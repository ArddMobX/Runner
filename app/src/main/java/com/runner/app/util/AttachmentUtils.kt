package com.runner.app.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.Locale

/**
 * Описание прикреплённого пользователем файла (документ, код, лог, таблица и т.д.).
 */
data class AttachedFile(
    val uri: Uri,
    val name: String,
    val size: Long,
    val mimeType: String?,
    val localPath: String? = null
)

object AttachmentUtils {

    private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp")

    private val TEXT_EXTENSIONS = setOf(
        "txt", "md", "markdown", "log", "json", "xml", "yaml", "yml", "csv", "tsv",
        "kt", "kts", "java", "py", "js", "ts", "jsx", "tsx", "html", "htm", "css", "scss",
        "c", "cpp", "h", "hpp", "cs", "go", "rs", "rb", "php", "sh", "bash", "zsh",
        "sql", "gradle", "properties", "conf", "ini", "toml", "env", "diff", "patch"
    )

    private val TEXT_MIME_TYPES = setOf(
        "application/json",
        "application/xml",
        "application/javascript",
        "application/x-yaml",
        "application/sql"
    )

    /**
     * Создаёт временный файл для камеры и возвращает (файл, content Uri через FileProvider).
     */
    fun createCameraImageUri(context: Context): Pair<File, Uri> {
        val cameraDir = File(context.cacheDir, "camera").apply { mkdirs() }
        val photoFile = File(cameraDir, "photo_${System.currentTimeMillis()}.jpg")
        val authority = "${context.packageName}.fileprovider"
        val uri = FileProvider.getUriForFile(context, authority, photoFile)
        return Pair(photoFile, uri)
    }

    /**
     * Извлекает имя и размер файла из content/file Uri.
     */
    fun getFileMetadata(context: Context, uri: Uri): Pair<String, Long> {
        var name = "file_${System.currentTimeMillis()}"
        var size = 0L

        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx >= 0) {
                        val n = cursor.getString(nameIdx)
                        if (!n.isNullOrBlank()) name = n
                    }
                    if (sizeIdx >= 0) {
                        size = cursor.getLong(sizeIdx)
                    }
                }
            }
        } catch (_: Exception) {}

        if (size <= 0L && uri.scheme == "file") {
            uri.path?.let { p ->
                val f = File(p)
                if (f.exists()) {
                    if (name.startsWith("file_")) name = f.name
                    size = f.length()
                }
            }
        }

        return Pair(name, size)
    }

    /**
     * Копирует файл из внешнего content Uri во внутренний каталог attachments кэша приложения.
     */
    fun copyToAttachmentsCache(context: Context, uri: Uri, displayName: String): File? {
        return try {
            val attachmentsDir = File(context.cacheDir, "attachments").apply { mkdirs() }
            val safeName = displayName.replace(Regex("[^a-zA-Z0-9._\\-\\p{IsCyrillic}]"), "_")
            val targetFile = File(attachmentsDir, "${System.currentTimeMillis()}_$safeName")
            context.contentResolver.openInputStream(uri)?.use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            targetFile
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Проверяет, является ли файл изображением.
     */
    fun isImageFile(fileName: String, mimeType: String?): Boolean {
        if (mimeType?.startsWith("image/") == true) return true
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return ext in IMAGE_EXTENSIONS
    }

    /**
     * Проверяет, является ли файл читаемым текстовым файлом (код, конфиг, лог, документ).
     */
    fun isTextFile(fileName: String, mimeType: String?): Boolean {
        if (mimeType?.startsWith("text/") == true || mimeType in TEXT_MIME_TYPES) return true
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return ext in TEXT_EXTENSIONS
    }

    /**
     * Безопасно считывает текст из файла (с ограничением по размеру).
     */
    fun readTextPreview(file: File, maxBytes: Long = 128 * 1024): String? {
        return try {
            if (!file.exists() || !file.canRead()) return null
            val bytesToRead = file.length().coerceAtMost(maxBytes).toInt()
            val buffer = ByteArray(bytesToRead)
            file.inputStream().use { it.read(buffer) }
            String(buffer, StandardCharsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Форматирует размер файла в удобочитаемый вид.
     */
    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 Б"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format(Locale.US, "%.1f ГБ", gb)
            mb >= 1.0 -> String.format(Locale.US, "%.1f МБ", mb)
            kb >= 1.0 -> String.format(Locale.US, "%.1f КБ", kb)
            else -> "$bytes Б"
        }
    }
}
