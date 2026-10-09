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
import kotlin.coroutines.resume

object ScreenCaptureManager {

    fun createScreenCaptureIntent(context: Context): Intent {
        val manager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        return manager.createScreenCaptureIntent()
    }

    suspend fun captureScreen(
        activity: Activity,
        resultCode: Int,
        data: Intent
    ): Uri? = suspendCancellableCoroutine { continuation ->
        val context = activity.applicationContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ScreenCaptureService.start(context)
        }

        Handler(Looper.getMainLooper()).postDelayed({
            try {
                val mpManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                val mediaProjection = mpManager.getMediaProjection(resultCode, data)

                if (mediaProjection == null) {
                    ScreenCaptureService.stop(context)
                    continuation.resume(null)
                    return@postDelayed
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

                val imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
                var virtualDisplay: VirtualDisplay? = null
                var isResumed = false

                fun cleanup() {
                    try { virtualDisplay?.release() } catch (_: Exception) {}
                    try { imageReader.close() } catch (_: Exception) {}
                    try { mediaProjection.stop() } catch (_: Exception) {}
                    ScreenCaptureService.stop(context)
                }

                imageReader.setOnImageAvailableListener({ reader ->
                    if (isResumed) return@setOnImageAvailableListener
                    var image: Image? = null
                    try {
                        image = reader.acquireLatestImage()
                        if (image != null) {
                            isResumed = true
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

                            val file = File(context.cacheDir, "screenshots").apply { mkdirs() }
                            val outFile = File(file, "screen_${System.currentTimeMillis()}.jpg")
                            FileOutputStream(outFile).use { out ->
                                cropped.compress(Bitmap.CompressFormat.JPEG, 85, out)
                            }
                            cropped.recycle()

                            cleanup()
                            continuation.resume(Uri.fromFile(outFile))
                        }
                    } catch (e: Exception) {
                        if (!isResumed) {
                            isResumed = true
                            cleanup()
                            continuation.resume(null)
                        }
                    } finally {
                        image?.close()
                    }
                }, Handler(Looper.getMainLooper()))

                virtualDisplay = mediaProjection.createVirtualDisplay(
                    "runner_screen_capture",
                    width,
                    height,
                    density,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    imageReader.surface,
                    null,
                    null
                )

                // Таймаут на случай непредвиденных задержек отображения
                Handler(Looper.getMainLooper()).postDelayed({
                    if (!isResumed) {
                        isResumed = true
                        cleanup()
                        continuation.resume(null)
                    }
                }, 3000)

            } catch (e: Exception) {
                ScreenCaptureService.stop(context)
                continuation.resume(null)
            }
        }, 150)
    }
}
