package com.runner.app.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import com.runner.app.service.ScreenCaptureService
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

object ScreenCaptureManager {

    private const val TAG = "RunnerScreenCapture"

    /** Сколько всего ждём кадр, прежде чем сдаться и вернуть null. */
    private const val CAPTURE_TIMEOUT_MS = 3_000L

    fun createScreenCaptureIntent(context: Context): Intent {
        val manager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        return manager.createScreenCaptureIntent()
    }

    /**
     * Сохраняет Bitmap в кэш-директорию скриншотов и возвращает Uri.
     */
    fun saveBitmap(context: Context, bitmap: Bitmap): Uri {
        val file = File(context.cacheDir, "screenshots").apply { mkdirs() }
        val outFile = File(file, "screen_${System.currentTimeMillis()}.jpg")
        FileOutputStream(outFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
        }
        return Uri.fromFile(outFile)
    }

    suspend fun captureScreen(
        activity: Activity,
        resultCode: Int,
        data: Intent
    ): Uri? {
        val context = activity.applicationContext

        // Android 10–15 разрешает getMediaProjection только при уже работающей
        // foreground-службе типа mediaProjection. Ждём настоящего подтверждения
        // startForeground(), а не фиксированной паузы.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ScreenCaptureService.start(context)
            if (!ScreenCaptureService.awaitForeground()) {
                ScreenCaptureService.stop(context)
                return null
            }
        }

        // Размеры окна снимаем заранее на вызывающем потоке до перехода на фоновый HandlerThread.
        val (width, height, density) = try {
            val windowManager = activity.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val bounds = windowManager.currentWindowMetrics.bounds
                val densityDpi = context.resources.configuration.densityDpi
                Triple(bounds.width(), bounds.height(), densityDpi)
            } else {
                val metrics = DisplayMetrics()
                @Suppress("DEPRECATION")
                windowManager.defaultDisplay.getRealMetrics(metrics)
                Triple(metrics.widthPixels, metrics.heightPixels, metrics.densityDpi)
            }
        } catch (e: Exception) {
            Log.w(TAG, "не удалось получить размеры экрана", e)
            ScreenCaptureService.stop(context)
            return null
        }

        if (width <= 0 || height <= 0 || density <= 0) {
            ScreenCaptureService.stop(context)
            return null
        }

        return suspendCancellableCoroutine { continuation ->
            val finished = AtomicBoolean(false)
            val cleanupLock = Any()
            var imageReader: ImageReader? = null
            var virtualDisplay: VirtualDisplay? = null
            var mediaProjection: MediaProjection? = null
            var projectionCallback: MediaProjection.Callback? = null

            // Обработка кадра, копирование пикселей и сжатие JPEG выполняются на
            // выделенном фоновом потоке, чтобы не блокировать Main-поток UI.
            val captureThread = HandlerThread("RunnerScreenCapture").apply { start() }
            val captureHandler = Handler(captureThread.looper)

            lateinit var timeoutRunnable: Runnable

            /** Единая точка выхода: освобождает все ресурсы и резюмирует ровно один раз. */
            fun finish(result: Uri?) {
                if (!finished.compareAndSet(false, true)) return
                captureHandler.removeCallbacks(timeoutRunnable)
                val vd: VirtualDisplay?
                val reader: ImageReader?
                val mp: MediaProjection?
                val cb: MediaProjection.Callback?
                synchronized(cleanupLock) {
                    vd = virtualDisplay
                    virtualDisplay = null
                    reader = imageReader
                    imageReader = null
                    mp = mediaProjection
                    mediaProjection = null
                    cb = projectionCallback
                    projectionCallback = null
                }
                runCatching { vd?.release() }
                runCatching { reader?.close() }
                runCatching { if (cb != null) mp?.unregisterCallback(cb) }
                runCatching { mp?.stop() }
                runCatching { captureThread.quitSafely() }
                ScreenCaptureService.stop(context)
                if (continuation.isActive) continuation.resume(result)
            }

            timeoutRunnable = Runnable { finish(null) }

            continuation.invokeOnCancellation {
                // Активность закрыли или повернули экран: ImageReader,
                // VirtualDisplay, поток и проекция обязаны быть освобождены.
                finish(null)
            }

            captureHandler.post {
                if (finished.get()) return@post
                try {
                    val mpManager =
                        context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                    val projection = mpManager.getMediaProjection(resultCode, data)
                    if (projection == null) {
                        finish(null)
                        return@post
                    }

                    // На Android 14–15 (API 34–35) регистрация MediaProjection.Callback
                    // ДО createVirtualDisplay() обязательна — без неё система безусловно
                    // бросает IllegalStateException.
                    val callback = object : MediaProjection.Callback() {
                        override fun onStop() {
                            finish(null)
                        }
                    }
                    projection.registerCallback(callback, captureHandler)

                    val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)

                    val abortedEarly = synchronized(cleanupLock) {
                        if (finished.get()) {
                            true
                        } else {
                            mediaProjection = projection
                            projectionCallback = callback
                            imageReader = reader
                            false
                        }
                    }
                    if (abortedEarly) {
                        runCatching { reader.close() }
                        runCatching { projection.unregisterCallback(callback) }
                        runCatching { projection.stop() }
                        return@post
                    }

                    reader.setOnImageAvailableListener({ source ->
                        if (finished.get()) return@setOnImageAvailableListener
                        var image: Image? = null
                        try {
                            image = source.acquireLatestImage() ?: return@setOnImageAvailableListener
                            val planes = image.planes
                            if (planes.isEmpty()) {
                                finish(null)
                                return@setOnImageAvailableListener
                            }
                            val buffer = planes[0].buffer
                            val pixelStride = planes[0].pixelStride
                            val rowStride = planes[0].rowStride
                            val imgWidth = image.width
                            val imgHeight = image.height
                            if (pixelStride <= 0 || imgWidth <= 0 || imgHeight <= 0) {
                                finish(null)
                                return@setOnImageAvailableListener
                            }
                            val rowPadding = (rowStride - pixelStride * imgWidth).coerceAtLeast(0)

                            val bitmap = Bitmap.createBitmap(
                                imgWidth + rowPadding / pixelStride,
                                imgHeight,
                                Bitmap.Config.ARGB_8888
                            )
                            bitmap.copyPixelsFromBuffer(buffer)
                            val cropped = Bitmap.createBitmap(bitmap, 0, 0, imgWidth, imgHeight)
                            if (cropped !== bitmap) bitmap.recycle()

                            val uri = saveBitmap(context, cropped)
                            cropped.recycle()

                            finish(uri)
                        } catch (e: Exception) {
                            Log.w(TAG, "кадр не удалось разобрать", e)
                            finish(null)
                        } finally {
                            runCatching { image?.close() }
                        }
                    }, captureHandler)

                    val display = projection.createVirtualDisplay(
                        "runner_screen_capture",
                        width,
                        height,
                        density,
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                        reader.surface,
                        null,
                        captureHandler
                    )

                    val cancelledDuringCreate = synchronized(cleanupLock) {
                        if (finished.get()) {
                            true
                        } else {
                            virtualDisplay = display
                            false
                        }
                    }
                    if (cancelledDuringCreate) {
                        runCatching { display.release() }
                        return@post
                    }

                    // Страховка на случай, если кадр так и не придёт.
                    captureHandler.postDelayed(timeoutRunnable, CAPTURE_TIMEOUT_MS)
                } catch (e: Exception) {
                    Log.w(TAG, "не удалось создать проекцию экрана", e)
                    finish(null)
                }
            }
        }
    }
}
