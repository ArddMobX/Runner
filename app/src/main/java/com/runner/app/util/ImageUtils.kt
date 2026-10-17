package com.runner.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.UUID

object ImageUtils {

    private const val TAG = "ImageUtils"

    /**
     * Надёжно открывает InputStream для пути к файлу или content:// URI.
     * Для локальных файлов в cacheDir читает напрямую через File.inputStream(),
     * избегая проблем ContentResolver с file:// URI на современных версиях Android.
     */
    private fun openStream(context: Context, uriString: String): InputStream? {
        return try {
            if (uriString.startsWith("content://")) {
                context.contentResolver.openInputStream(Uri.parse(uriString))
            } else {
                val filePath = if (uriString.startsWith("file://")) {
                    Uri.parse(uriString).path.orEmpty()
                } else {
                    uriString
                }
                val file = File(filePath)
                if (file.exists() && file.length() > 0) {
                    file.inputStream()
                } else {
                    context.contentResolver.openInputStream(Uri.parse(uriString))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Не удалось открыть поток для $uriString", e)
            null
        }
    }

    /**
     * Сжимает изображение, выравнивает ориентацию по EXIF и кодирует в Base64
     * формата data:image/jpeg;base64,... для отправки в OpenAI/Gemini Vision API.
     */
    fun compressAndEncodeImage(
        context: Context,
        uriString: String,
        maxDimension: Int = 1280,
        quality: Int = 80
    ): String? {
        return try {
            // 1. Читаем границы изображения
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            openStream(context, uriString)?.use { input ->
                BitmapFactory.decodeStream(input, null, options)
            } ?: run {
                Log.e(TAG, "Не удалось прочитать границы изображения: $uriString")
                return null
            }

            val origWidth = options.outWidth
            val origHeight = options.outHeight
            if (origWidth <= 0 || origHeight <= 0) {
                Log.e(TAG, "Некорректный размер изображения ($origWidth x $origHeight): $uriString")
                return null
            }

            // 2. Рассчитываем inSampleSize для экономии оперативной памяти
            var sampleSize = 1
            while ((origWidth / sampleSize) > maxDimension * 2 || (origHeight / sampleSize) > maxDimension * 2) {
                sampleSize *= 2
            }

            options.inJustDecodeBounds = false
            options.inSampleSize = sampleSize
            options.inPreferredConfig = Bitmap.Config.ARGB_8888

            val sampledBitmap = openStream(context, uriString)?.use { input ->
                BitmapFactory.decodeStream(input, null, options)
            } ?: run {
                Log.e(TAG, "Не удалось декодировать сэмпл изображения: $uriString")
                return null
            }

            // 3. Учитываем поворот EXIF
            val rotation = getExifRotation(context, uriString)
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

            val base64 = "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
            Log.i(TAG, "Изображение успешно закодировано в base64 (${bytes.size} байт, ${finalBitmap.width}x${finalBitmap.height}): $uriString")
            base64
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при обработке изображения $uriString", e)
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

            val bytesCopied = context.contentResolver.openInputStream(sourceUri)?.use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: 0L

            if (bytesCopied > 0L && targetFile.exists() && targetFile.length() > 0L) {
                targetFile.absolutePath
            } else {
                sourceUri.toString()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка копирования в кэш $sourceUri", e)
            sourceUri.toString()
        }
    }

    private fun getExifRotation(context: Context, uriString: String): Int {
        var input: InputStream? = null
        return try {
            if (!uriString.startsWith("content://")) {
                val filePath = if (uriString.startsWith("file://")) Uri.parse(uriString).path.orEmpty() else uriString
                val file = File(filePath)
                if (file.exists()) {
                    val exif = ExifInterface(file.absolutePath)
                    return when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> 90
                        ExifInterface.ORIENTATION_ROTATE_180 -> 180
                        ExifInterface.ORIENTATION_ROTATE_270 -> 270
                        else -> 0
                    }
                }
            }
            input = context.contentResolver.openInputStream(Uri.parse(uriString)) ?: return 0
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
