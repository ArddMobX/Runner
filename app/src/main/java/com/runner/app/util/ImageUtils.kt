package com.runner.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.UUID

object ImageUtils {

    /**
     * Сжимает изображение, выравнивает ориентацию по EXIF и кодирует в Base64
     * формата data:image/jpeg;base64,... для отправки в OpenAI Vision API.
     */
    fun compressAndEncodeImage(
        context: Context,
        uriString: String,
        maxDimension: Int = 1280,
        quality: Int = 80
    ): String? {
        val uri = if (uriString.startsWith("content://") || uriString.startsWith("file://")) {
            Uri.parse(uriString)
        } else {
            Uri.fromFile(File(uriString))
        }

        return try {
            val resolver = context.contentResolver

            // 1. Читаем границы изображения
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, options)
            } ?: return null

            val origWidth = options.outWidth
            val origHeight = options.outHeight
            if (origWidth <= 0 || origHeight <= 0) return null

            // 2. Рассчитываем inSampleSize для экономии оперативной памяти
            var sampleSize = 1
            while ((origWidth / sampleSize) > maxDimension * 2 || (origHeight / sampleSize) > maxDimension * 2) {
                sampleSize *= 2
            }

            options.inJustDecodeBounds = false
            options.inSampleSize = sampleSize
            options.inPreferredConfig = Bitmap.Config.ARGB_8888

            val sampledBitmap = resolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, options)
            } ?: return null

            // 3. Учитываем поворот EXIF
            val rotation = getExifRotation(context, uri)
            val rotatedBitmap = if (rotation != 0) {
                val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
                Bitmap.createBitmap(sampledBitmap, 0, 0, sampledBitmap.width, sampledBitmap.height, matrix, true).also {
                    if (it != sampledBitmap) sampledBitmap.recycle()
                }
            } else {
                sampledBitmap
            }

            // 4. Финальное пропорциональное масштабирование до maxDimension
            val finalBitmap = if (rotatedBitmap.width > maxDimension || rotatedBitmap.height > maxDimension) {
                val ratio = minOf(
                    maxDimension.toFloat() / rotatedBitmap.width,
                    maxDimension.toFloat() / rotatedBitmap.height
                )
                val targetW = (rotatedBitmap.width * ratio).toInt().coerceAtLeast(1)
                val targetH = (rotatedBitmap.height * ratio).toInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(rotatedBitmap, targetW, targetH, true).also {
                    if (it != rotatedBitmap) rotatedBitmap.recycle()
                }
            } else {
                rotatedBitmap
            }

            // 5. Сжатие в JPEG и base64
            val outputStream = ByteArrayOutputStream()
            finalBitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
            val bytes = outputStream.toByteArray()
            finalBitmap.recycle()

            "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Копирует изображение во внутренний кэш приложения, чтобы в истории чатов
     * оставалась стабильная локальная копия.
     */
    fun copyToInternalCache(context: Context, sourceUri: Uri): String? {
        return try {
            val dir = File(context.cacheDir, "chat_images").apply { mkdirs() }
            val fileName = "img_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.jpg"
            val targetFile = File(dir, fileName)

            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            targetFile.absolutePath
        } catch (e: Exception) {
            sourceUri.toString()
        }
    }

    private fun getExifRotation(context: Context, uri: Uri): Int {
        var input: InputStream? = null
        return try {
            input = context.contentResolver.openInputStream(uri) ?: return 0
            val exif = ExifInterface(input)
            when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } catch (e: Exception) {
            0
        } finally {
            try { input?.close() } catch (_: Exception) {}
        }
    }
}
