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
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager
import com.runner.app.service.ScreenCaptureService
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

object ScreenCaptureManager {

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

        // Android 14+ разрешает getMediaProjection только при уже работающей
        // foreground-службе типа mediaProjection. Ждём её настоящей готовности,
        // а не фиксированную паузу: иначе на медленном старте проекция не
        // создаётся и захват молча возвращает null.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ScreenCaptureService.start(context)
            if (!ScreenCaptureService.awaitForeground()) {
                ScreenCaptureService.stop(context)
                return null
            }
        }

        return suspendCancellableCoroutine { continuation ->
            val finished = AtomicBoolean(false)
            var imageReader: ImageReader? = null
            var virtualDisplay: VirtualDisplay? = null
            var mediaProjection: MediaProjection? = null
            val mainHandler = Handler(Looper.getMainLooper())

            /** Единая точка выхода: освобождает всё и резюмирует ровно один раз. */
            fun finish(result: Uri?) {
                if (!finished.compareAndSet(false, true)) return
                try {
                    virtualDisplay?.release()
                } catch (_: Exception) {}
                try {
                    imageReader?.close()
                } catch (_: Exception) {}
                try {
                    mediaProjection?.stop()
                } catch (_: Exception) {}
                ScreenCaptureService.stop(context)
                if (continuation.isActive) continuation.resume(result)
            }

            continuation.invokeOnCancellation {
                // Активность закрыли или повернули экран: ImageReader,
                // VirtualDisplay и проекция обязаны быть освобождены, иначе
                // они висят до смерти процесса, а служба остаётся в шторке.
                // resume при этом уже не нужен — continuation отменён.
                finish(null)
            }

            mainHandler.post {
                try {
                    val mpManager =
                        context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                    mediaProjection = mpManager.getMediaProjection(resultCode, data)
                    if (mediaProjection == null) {
                        finish(null)
                        return@post
                    }

                    val windowManager = activity.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                    val (width, height, density) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        val bounds = windowManager.currentWindowMetrics.bounds
                        val densityDpi = context.resources.configuration.densityDpi
                        Triple(bounds.width(), bounds.height(), densityDpi)
                    } else {
                        val metrics = DisplayMetrics()
                        @Suppress("DEPRECATION")
                        windowManager.defaultDisplay.getRealMetrics(metrics)
                        Triple(metrics.widthPixels, metrics.heightPixels, metrics.densityDpi)
                    }

                    val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
                    imageReader = reader

                    reader.setOnImageAvailableListener({ source ->
                        var image: Image? = null
                        try {
                            image = source.acquireLatestImage() ?: return@setOnImageAvailableListener
                            val planes = image.planes
                            val buffer = planes[0].buffer
                            val pixelStride = planes[0].pixelStride
                            val rowStride = planes[0].rowStride
                            val rowPadding = rowStride - pixelStride * width

                            val bitmap = Bitmap.createBitmap(
                                width + rowPadding / pixelStride,
                                height,
                                Bitmap.Config.ARGB_8888
                            )
                            bitmap.copyPixelsFromBuffer(buffer)
                            val cropped = Bitmap.createBitmap(bitmap, 0, 0, width, height)
                            if (cropped != bitmap) bitmap.recycle()

                            val uri = saveBitmap(context, cropped)
                            cropped.recycle()

                            finish(uri)
                        } catch (e: Exception) {
                            finish(null)
                        } finally {
                            image?.close()
                        }
                    }, mainHandler)

                    virtualDisplay = mediaProjection?.createVirtualDisplay(
                        "runner_screen_capture",
                        width,
                        height,
                        density,
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                        reader.surface,
                        null,
                        null
                    )

                    // Страховка на случай, если кадр так и не придёт.
                    mainHandler.postDelayed({ finish(null) }, CAPTURE_TIMEOUT_MS)
                } catch (e: Exception) {
                    finish(null)
                }
            }
        }
    }
}
