package com.runner.app.util

import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract

object StoragePathUtils {

    /**
     * Преобразует DocumentTree Uri из системного SAF-пикера в абсолютный путь файловой системы.
     */
    fun uriToStoragePath(uri: Uri): String? {
        return try {
            val treeDocId = DocumentsContract.getTreeDocumentId(uri)
            if (treeDocId.startsWith("primary:")) {
                val subPath = treeDocId.removePrefix("primary:").trimStart('/')
                val root = Environment.getExternalStorageDirectory().canonicalPath
                if (subPath.isEmpty()) root else "$root/$subPath"
            } else if (treeDocId.contains(":")) {
                val parts = treeDocId.split(":", limit = 2)
                val subPath = parts.getOrNull(1)?.trimStart('/').orEmpty()
                val storageId = parts[0]
                val basePath = "/storage/$storageId"
                if (subPath.isEmpty()) basePath else "$basePath/$subPath"
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Возвращает короткое имя папки для компактного отображения в UI без полного длинного пути.
     */
    fun getFolderDisplayName(path: String): String {
        if (path.isBlank()) return "Вся память"
        val trimmed = path.trimEnd('/', '\\')
        val name = trimmed.substringAfterLast('/').substringAfterLast('\\')
        return if (name.isNotBlank()) name else trimmed
    }
}
