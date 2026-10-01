package com.runner.app.tools

import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.lingala.zip4j.ZipFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.DecimalFormat

object ToolDispatcher {

    private val imageExtensions = setOf("jpg", "jpeg", "png", "webp", "gif", "heic", "bmp")
    private val docExtensions = setOf("pdf", "doc", "docx", "txt", "rtf", "xls", "xlsx", "ppt", "pptx", "epub", "csv")
    private val archiveExtensions = setOf("zip", "rar", "7z", "tar", "gz", "bz2")
    private val apkExtensions = setOf("apk", "xapk", "apks")

    /**
     * JSON Schema description of tools for OpenAI-compatible Tool Calling (Groq API).
     */
    fun getToolsJson(): JSONArray {
        val tools = JSONArray()

        // 1. get_folder_summary
        val summaryTool = JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "get_folder_summary")
                put("description", "Быстро сканирует директорию на устройстве и возвращает сводку: общее количество файлов, сколько картинок, документов, архивов, APK и топ-5 самых тяжелых файлов.")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("path", JSONObject().apply {
                            put("type", "string")
                            put("description", "Путь к папке (например, 'Download', 'Documents' или полный путь). По умолчанию используется 'Download'.")
                        })
                    })
                })
            })
        }

        // 2. extract_archive
        val extractTool = JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "extract_archive")
                put("description", "Распаковывает ZIP-архив в указанную директорию через Zip4j.")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("zip_path", JSONObject().apply {
                            put("type", "string")
                            put("description", "Путь к zip-файлу или имя архива в папке Download (например, 'documents.zip').")
                        })
                        put("target_dir", JSONObject().apply {
                            put("type", "string")
                            put("description", "Папка для распаковки. Если не указана, распаковывает в одноименную подпапку рядом с архивом.")
                        })
                    })
                    put("required", JSONArray().apply { put("zip_path") })
                })
            })
        }

        // 3. organize_downloads
        val organizeTool = JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "organize_downloads")
                put("description", "Перемещает файлы из папки Download по категориям (например, 'documents' -> Documents/Runner, 'apks' -> Backups/APKs, 'archives', 'images', 'all').")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("category", JSONObject().apply {
                            put("type", "string")
                            put("enum", JSONArray().apply {
                                put("documents")
                                put("apks")
                                put("archives")
                                put("images")
                                put("all")
                            })
                            put("description", "Категория для перемещения: 'documents', 'apks', 'archives', 'images' или 'all'.")
                        })
                    })
                    put("required", JSONArray().apply { put("category") })
                })
            })
        }

        tools.put(summaryTool)
        tools.put(extractTool)
        tools.put(organizeTool)

        return tools
    }

    /**
     * Dispatch and execute a tool by name with JSON arguments.
     */
    suspend fun execute(toolName: String, argumentsJson: String): String = withContext(Dispatchers.IO) {
        val args = try {
            if (argumentsJson.isBlank()) JSONObject() else JSONObject(argumentsJson)
        } catch (e: Exception) {
            JSONObject()
        }

        try {
            when (toolName) {
                "get_folder_summary" -> {
                    val path = args.optString("path", "").trim()
                    getFolderSummary(path)
                }
                "extract_archive" -> {
                    val zipPath = args.optString("zip_path", "").trim()
                    val targetDir = args.optString("target_dir", "").trim()
                    extractArchive(zipPath, targetDir)
                }
                "organize_downloads" -> {
                    val category = args.optString("category", "documents").trim()
                    organizeDownloads(category)
                }
                else -> "Неизвестный инструмент: $toolName"
            }
        } catch (e: Exception) {
            "Ошибка при выполнении $toolName: ${e.message}"
        }
    }

    /**
     * Tool 1: Fast folder scanning & summary.
     */
    fun getFolderSummary(rawPath: String): String {
        val dir = resolveFolder(rawPath)
        if (!dir.exists()) {
            return "Папка '${dir.absolutePath}' не найдена."
        }
        if (!dir.isDirectory) {
            return "'${dir.absolutePath}' не является папкой."
        }

        val allFiles = dir.listFiles() ?: return "Не удалось получить список файлов (нет доступа)."

        var totalFiles = 0
        var totalDirs = 0
        var imagesCount = 0
        var docsCount = 0
        var archivesCount = 0
        var apksCount = 0
        var othersCount = 0

        val fileList = mutableListOf<File>()

        for (item in allFiles) {
            if (item.isDirectory) {
                totalDirs++
            } else if (item.isFile) {
                totalFiles++
                fileList.add(item)
                val ext = item.extension.lowercase()
                when {
                    ext in imageExtensions -> imagesCount++
                    ext in docExtensions -> docsCount++
                    ext in archiveExtensions -> archivesCount++
                    ext in apkExtensions -> apksCount++
                    else -> othersCount++
                }
            }
        }

        // Top 5 heaviest files
        val topHeaviest = fileList
            .sortedByDescending { it.length() }
            .take(5)
            .joinToString("\n") { "  • ${it.name} (${formatFileSize(it.length())})" }

        val heaviestSection = if (topHeaviest.isNotEmpty()) {
            "\nТоп-5 самых тяжелых файлов:\n$topHeaviest"
        } else {
            ""
        }

        return buildString {
            append("Сводка по папке '${dir.name}' (${dir.absolutePath}):\n")
            append("Всего файлов: $totalFiles, подпапок: $totalDirs.\n")
            append("Категории: Документы: $docsCount, Картинки: $imagesCount, Архивы: $archivesCount, APK: $apksCount, Другое: $othersCount.")
            append(heaviestSection)
        }
    }

    /**
     * Tool 2: Native ZIP extraction via Zip4j.
     */
    fun extractArchive(rawZipPath: String, rawTargetDir: String): String {
        val zipFile = resolveFile(rawZipPath)
        if (!zipFile.exists() || !zipFile.isFile) {
            return "Архив '${zipFile.absolutePath}' не найден."
        }

        val targetDir = if (rawTargetDir.isBlank()) {
            // Default target: folder named after zip in the same directory
            val baseName = zipFile.nameWithoutExtension
            File(zipFile.parentFile ?: Environment.getExternalStorageDirectory(), baseName)
        } else {
            resolveFolder(rawTargetDir)
        }

        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }

        return try {
            val zip = ZipFile(zipFile)
            if (!zip.isValidZipFile) {
                return "Файл '${zipFile.name}' поврежден или не является валидным ZIP-архивом."
            }
            zip.extractAll(targetDir.absolutePath)
            val extractedCount = targetDir.walkTopDown().filter { it.isFile }.count()
            "Архив '${zipFile.name}' успешно распакован в '${targetDir.absolutePath}'. Извлечено файлов: $extractedCount."
        } catch (e: Exception) {
            "Ошибка при распаковке архива: ${e.localizedMessage}"
        }
    }

    /**
     * Tool 3: Organize files from Downloads folder into categorized directories.
     */
    fun organizeDownloads(category: String): String {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!downloadsDir.exists() || !downloadsDir.isDirectory) {
            return "Папка Download не найдена."
        }

        val allFiles = downloadsDir.listFiles()?.filter { it.isFile } ?: emptyList()
        if (allFiles.isEmpty()) {
            return "В папке Download нет файлов для перемещения."
        }

        val normCategory = category.lowercase().trim()
        val movedFiles = mutableListOf<String>()

        fun moveMatching(files: List<File>, extensions: Set<String>, targetFolder: File, catName: String) {
            if (!targetFolder.exists()) {
                targetFolder.mkdirs()
            }
            for (file in files) {
                if (file.extension.lowercase() in extensions) {
                    val dest = getNonConflictingDestination(targetFolder, file.name)
                    if (file.renameTo(dest)) {
                        movedFiles.add("${file.name} -> ${targetFolder.name}/${dest.name}")
                    }
                }
            }
        }

        val docsDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "Runner")
        val apksDir = File(Environment.getExternalStorageDirectory(), "Backups/APKs")
        val archivesDir = File(docsDir, "Archives")
        val picturesDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Runner")

        when (normCategory) {
            "documents" -> {
                moveMatching(allFiles, docExtensions, docsDir, "documents")
            }
            "apks" -> {
                moveMatching(allFiles, apkExtensions, apksDir, "apks")
            }
            "archives" -> {
                moveMatching(allFiles, archiveExtensions, archivesDir, "archives")
            }
            "images" -> {
                moveMatching(allFiles, imageExtensions, picturesDir, "images")
            }
            "all" -> {
                moveMatching(allFiles, docExtensions, docsDir, "documents")
                moveMatching(allFiles, apkExtensions, apksDir, "apks")
                moveMatching(allFiles, archiveExtensions, archivesDir, "archives")
                moveMatching(allFiles, imageExtensions, picturesDir, "images")
            }
            else -> {
                return "Неизвестная категория: '$category'. Допустимые: 'documents', 'apks', 'archives', 'images', 'all'."
            }
        }

        return if (movedFiles.isEmpty()) {
            "В папке Download не найдено файлов, соответствующих категории '$category'."
        } else {
            buildString {
                append("Успешно перемещено файлов (${movedFiles.size}) для категории '$category':\n")
                movedFiles.take(10).forEach { append("  • $it\n") }
                if (movedFiles.size > 10) {
                    append("  ...и еще ${movedFiles.size - 10} файлов.")
                }
            }
        }
    }

    /**
     * Resolves collisions: document.pdf -> document_1.pdf -> document_2.pdf
     */
    private fun getNonConflictingDestination(targetDir: File, originalName: String): File {
        var dest = File(targetDir, originalName)
        if (!dest.exists()) return dest

        val dotIndex = originalName.lastIndexOf('.')
        val baseName = if (dotIndex > 0) originalName.substring(0, dotIndex) else originalName
        val extension = if (dotIndex > 0) originalName.substring(dotIndex) else ""

        var counter = 1
        while (dest.exists()) {
            dest = File(targetDir, "${baseName}_$counter$extension")
            counter++
        }
        return dest
    }

    private fun resolveFolder(path: String): File {
        val trimmed = path.trim()
        val defaultDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)

        if (trimmed.isEmpty() || trimmed.equals("download", ignoreCase = true) || trimmed.equals("downloads", ignoreCase = true)) {
            return defaultDownloads
        }
        if (trimmed.equals("documents", ignoreCase = true)) {
            return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        }
        if (trimmed.equals("pictures", ignoreCase = true)) {
            return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        }
        if (trimmed.equals("dcim", ignoreCase = true)) {
            return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
        }

        val directFile = File(trimmed)
        if (directFile.isAbsolute) return directFile

        // Relative to external storage or Downloads
        val inDownloads = File(defaultDownloads, trimmed)
        if (inDownloads.exists()) return inDownloads

        return File(Environment.getExternalStorageDirectory(), trimmed)
    }

    private fun resolveFile(path: String): File {
        val trimmed = path.trim()
        val directFile = File(trimmed)
        if (directFile.isAbsolute && directFile.exists()) return directFile

        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val inDownloads = File(downloads, trimmed)
        if (inDownloads.exists()) return inDownloads

        val inRoot = File(Environment.getExternalStorageDirectory(), trimmed)
        if (inRoot.exists()) return inRoot

        return if (directFile.isAbsolute) directFile else inDownloads
    }

    private fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        val df = DecimalFormat("#,##0.#")
        return "${df.format(bytes / Math.pow(1024.0, digitGroups.toDouble()))} ${units[digitGroups]}"
    }
}
