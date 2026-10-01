package com.runner.app.tools

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Environment
import android.os.StatFs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.lingala.zip4j.ZipFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.text.DecimalFormat
import java.util.concurrent.TimeUnit

object ToolDispatcher {

    private val imageExtensions = setOf("jpg", "jpeg", "png", "webp", "gif", "heic", "bmp")
    private val docExtensions = setOf("pdf", "doc", "docx", "txt", "rtf", "xls", "xlsx", "ppt", "pptx", "epub", "csv", "json", "md")
    private val archiveExtensions = setOf("zip", "rar", "7z", "tar", "gz", "bz2")
    private val apkExtensions = setOf("apk", "xapk", "apks")
    private val videoExtensions = setOf("mp4", "mkv", "mov", "avi", "3gp", "webm", "flv", "ts")
    private val junkExtensions = setOf("tmp", "temp", "log", "crdownload", "part", "bak")

    /**
     * JSON Schema description of all tools for OpenAI-compatible Tool Calling.
     */
    fun getToolsJson(): JSONArray {
        val tools = JSONArray()

        // 1. get_storage_summary
        tools.put(createToolFunction(
            name = "get_storage_summary",
            description = "Возвращает сводку по памяти устройства: общий объем диска, занято, свободно, а также агрегированный вес основных категорий (видео, фото, документы, архивы, кэш и временные файлы). Использовать вместо выкачивания всего списка файлов.",
            properties = JSONObject()
        ))

        // 2. find_largest_files
        tools.put(createToolFunction(
            name = "find_largest_files",
            description = "Находит самые тяжелые файлы на устройстве. Возвращает топ-10 (или N) файлов с их путями и размерами без перегрузки контекста модели.",
            properties = JSONObject().apply {
                put("limit", JSONObject().apply {
                    put("type", "integer")
                    put("description", "Количество файлов в топе (по умолчанию 10, максимум 30).")
                })
                put("min_size_mb", JSONObject().apply {
                    put("type", "integer")
                    put("description", "Минимальный размер файла в мегабайтах (по умолчанию 50 МБ).")
                })
            }
        ))

        // 3. find_junk_files
        tools.put(createToolFunction(
            name = "find_junk_files",
            description = "Ищет временные файлы (*.tmp, *.log, *.crdownload, остатки кэша) и пустые папки на устройстве. Возвращает суммарный объем мусора и список для очистки.",
            properties = JSONObject()
        ))

        // 4. get_folder_summary
        tools.put(createToolFunction(
            name = "get_folder_summary",
            description = "Быстро сканирует директорию на устройстве и возвращает сводку: общее количество файлов, сколько картинок, документов, архивов, APK и топ-5 самых тяжелых файлов.",
            properties = JSONObject().apply {
                put("path", JSONObject().apply {
                    put("type", "string")
                    put("description", "Путь к папке (например, 'Download', 'Documents' или полный путь). По умолчанию 'Download'.")
                })
            }
        ))

        // 2. read_file
        tools.put(createToolFunction(
            name = "read_file",
            description = "Читает текстовое содержимое файла (UTF-8). Ограничивает объем чтения, чтобы не перегружать контекст.",
            properties = JSONObject().apply {
                put("path", JSONObject().apply {
                    put("type", "string")
                    put("description", "Путь к файлу для чтения (например, 'Download/notes.txt' или полный путь).")
                })
                put("max_lines", JSONObject().apply {
                    put("type", "integer")
                    put("description", "Максимальное количество строк для чтения (по умолчанию 300).")
                })
            },
            required = listOf("path")
        ))

        // 3. write_file
        tools.put(createToolFunction(
            name = "write_file",
            description = "Создает или перезаписывает текстовый файл, либо дописывает в него текст (append). Автоматически создает недостающие папки.",
            properties = JSONObject().apply {
                put("path", JSONObject().apply {
                    put("type", "string")
                    put("description", "Путь к целевому файлу (например, 'Documents/todo.txt').")
                })
                put("content", JSONObject().apply {
                    put("type", "string")
                    put("description", "Текст для записи в файл.")
                })
                put("append", JSONObject().apply {
                    put("type", "boolean")
                    put("description", "Если true, дописывает в конец файла. Если false, перезаписывает (по умолчанию false).")
                })
            },
            required = listOf("path", "content")
        ))

        // 4. delete_file
        tools.put(createToolFunction(
            name = "delete_file",
            description = "Удаляет файл или директорию на устройстве.",
            properties = JSONObject().apply {
                put("path", JSONObject().apply {
                    put("type", "string")
                    put("description", "Путь к файлу или папке для удаления.")
                })
                put("recursive", JSONObject().apply {
                    put("type", "boolean")
                    put("description", "Если удаляется непустая папка, необходимо передать true для рекурсивного удаления (по умолчанию false).")
                })
            },
            required = listOf("path")
        ))

        // 5. create_dir
        tools.put(createToolFunction(
            name = "create_dir",
            description = "Создает новую папку и все промежуточные родительские директории при необходимости.",
            properties = JSONObject().apply {
                put("path", JSONObject().apply {
                    put("type", "string")
                    put("description", "Путь к создаваемой папке (например, 'Documents/Projects/2026').")
                })
            },
            required = listOf("path")
        ))

        // 6. move_file
        tools.put(createToolFunction(
            name = "move_file",
            description = "Перемещает или переименовывает файл или папку в новое местоположение.",
            properties = JSONObject().apply {
                put("source_path", JSONObject().apply {
                    put("type", "string")
                    put("description", "Путь к исходному файлу или папке.")
                })
                put("destination_path", JSONObject().apply {
                    put("type", "string")
                    put("description", "Путь к новому файлу или целевой папке назначения.")
                })
            },
            required = listOf("source_path", "destination_path")
        ))

        // 7. copy_file
        tools.put(createToolFunction(
            name = "copy_file",
            description = "Копирует файл или директорию в указанное местоположение.",
            properties = JSONObject().apply {
                put("source_path", JSONObject().apply {
                    put("type", "string")
                    put("description", "Путь к копируемому файлу или папке.")
                })
                put("destination_path", JSONObject().apply {
                    put("type", "string")
                    put("description", "Путь к файлу-копии или папке назначения.")
                })
            },
            required = listOf("source_path", "destination_path")
        ))

        // 8. search_files
        tools.put(createToolFunction(
            name = "search_files",
            description = "Ищет файлы по названию и/или расширению в указанной папке (по умолчанию Download). Возвращает список совпадений с путями и размерами.",
            properties = JSONObject().apply {
                put("query", JSONObject().apply {
                    put("type", "string")
                    put("description", "Часть имени файла для поиска (например, 'договор' или 'resume').")
                })
                put("path", JSONObject().apply {
                    put("type", "string")
                    put("description", "Папка для поиска (по умолчанию 'Download').")
                })
                put("extension", JSONObject().apply {
                    put("type", "string")
                    put("description", "Фильтр по расширению без точки (например, 'pdf', 'apk', 'zip').")
                })
            }
        ))

        // 9. create_archive
        tools.put(createToolFunction(
            name = "create_archive",
            description = "Создает ZIP-архив из одного или нескольких указанных файлов или папок через Zip4j.",
            properties = JSONObject().apply {
                put("source_paths", JSONObject().apply {
                    put("type", "array")
                    put("items", JSONObject().apply { put("type", "string") })
                    put("description", "Список путей к файлам или папкам для архивации.")
                })
                put("zip_path", JSONObject().apply {
                    put("type", "string")
                    put("description", "Путь к создаваемому zip-архиву (например, 'Download/my_archive.zip').")
                })
            },
            required = listOf("source_paths", "zip_path")
        ))

        // 10. extract_archive
        tools.put(createToolFunction(
            name = "extract_archive",
            description = "Распаковывает ZIP-архив в указанную директорию через Zip4j.",
            properties = JSONObject().apply {
                put("zip_path", JSONObject().apply {
                    put("type", "string")
                    put("description", "Путь к zip-файлу или имя архива в папке Download (например, 'documents.zip').")
                })
                put("target_dir", JSONObject().apply {
                    put("type", "string")
                    put("description", "Папка для распаковки. Если не указана, распаковывает в одноименную подпапку рядом с архивом.")
                })
            },
            required = listOf("zip_path")
        ))

        // 11. organize_downloads
        tools.put(createToolFunction(
            name = "organize_downloads",
            description = "Перемещает файлы из папки Download по категориям ('documents' -> Documents/Runner, 'apks' -> Backups/APKs, 'archives', 'images', 'all'). Защищает от перезаписи.",
            properties = JSONObject().apply {
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
            },
            required = listOf("category")
        ))

        // 12. clipboard_read
        tools.put(createToolFunction(
            name = "clipboard_read",
            description = "Читает текущий текст из системного буфера обмена Android.",
            properties = JSONObject()
        ))

        // 13. clipboard_write
        tools.put(createToolFunction(
            name = "clipboard_write",
            description = "Записывает указанный текст в системный буфер обмена Android.",
            properties = JSONObject().apply {
                put("text", JSONObject().apply {
                    put("type", "string")
                    put("description", "Текст для сохранения в буфер обмена.")
                })
            },
            required = listOf("text")
        ))

        // 14. run_shell_command
        tools.put(createToolFunction(
            name = "run_shell_command",
            description = "Выполняет команду оболочки (sh) на устройстве Android и возвращает вывод stdout, stderr и код завершения.",
            properties = JSONObject().apply {
                put("command", JSONObject().apply {
                    put("type", "string")
                    put("description", "Команда шелла для выполнения (например, 'df -h', 'ls -la /sdcard/Download', 'uname -a').")
                })
                put("timeout_seconds", JSONObject().apply {
                    put("type", "integer")
                    put("description", "Таймаут выполнения команды в секундах (по умолчанию 10, максимум 30).")
                })
            },
            required = listOf("command")
        ))

        return tools
    }

    private fun createToolFunction(
        name: String,
        description: String,
        properties: JSONObject,
        required: List<String> = emptyList()
    ): JSONObject {
        return JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", name)
                put("description", description)
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", properties)
                    if (required.isNotEmpty()) {
                        put("required", JSONArray().apply { required.forEach { put(it) } })
                    }
                })
            })
        }
    }

    /**
     * Dispatch and execute a tool by name with JSON arguments and Android Context.
     */
    suspend fun execute(
        toolName: String,
        argumentsJson: String,
        context: Context
    ): String = withContext(Dispatchers.IO) {
        val args = try {
            if (argumentsJson.isBlank()) JSONObject() else JSONObject(argumentsJson)
        } catch (e: Exception) {
            JSONObject()
        }

        val rawOutput = try {
            when (toolName) {
                // Storage aggregators
                "get_storage_summary" -> getStorageSummary()
                "find_largest_files" -> {
                    val limit = args.optInt("limit", 10).coerceIn(1, 30)
                    val minSizeMb = args.optLong("min_size_mb", 50L)
                    findLargestFiles(limit, minSizeMb)
                }
                "find_junk_files" -> findJunkFiles()

                // Folder summary
                "get_folder_summary" -> {
                    val path = args.optString("path", "").trim()
                    getFolderSummary(path)
                }

                // File content operations
                "read_file" -> {
                    val path = args.optString("path", "").trim()
                    val maxLines = args.optInt("max_lines", 300)
                    readFile(path, maxLines)
                }

                "write_file" -> {
                    val path = args.optString("path", "").trim()
                    val content = args.optString("content", "")
                    val append = args.optBoolean("append", false)
                    writeFile(path, content, append)
                }

                // File management operations
                "delete_file" -> {
                    val path = args.optString("path", "").trim()
                    val recursive = args.optBoolean("recursive", false)
                    deleteFile(path, recursive)
                }

                "create_dir" -> {
                    val path = args.optString("path", "").trim()
                    createDir(path)
                }

                "move_file" -> {
                    val sourcePath = args.optString("source_path", "").trim()
                    val destinationPath = args.optString("destination_path", "").trim()
                    moveFile(sourcePath, destinationPath)
                }

                "copy_file" -> {
                    val sourcePath = args.optString("source_path", "").trim()
                    val destinationPath = args.optString("destination_path", "").trim()
                    copyFile(sourcePath, destinationPath)
                }

                // Search
                "search_files" -> {
                    val query = args.optString("query", "").trim()
                    val path = args.optString("path", "").trim()
                    val extension = args.optString("extension", "").trim()
                    searchFiles(query, path, extension)
                }

                // Archives
                "create_archive" -> {
                    val sourcesJson = args.optJSONArray("source_paths")
                    val sourcePaths = mutableListOf<String>()
                    if (sourcesJson != null) {
                        for (i in 0 until sourcesJson.length()) {
                            sourcePaths.add(sourcesJson.getString(i))
                        }
                    }
                    val zipPath = args.optString("zip_path", "").trim()
                    createArchive(sourcePaths, zipPath)
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

                // Clipboard
                "clipboard_read" -> {
                    clipboardRead(context)
                }

                "clipboard_write" -> {
                    val text = args.optString("text", "")
                    clipboardWrite(text, context)
                }

                // Shell
                "run_shell_command" -> {
                    val command = args.optString("command", "").trim()
                    val timeout = args.optInt("timeout_seconds", 10).coerceIn(1, 30)
                    runShellCommand(command, timeout)
                }

                else -> "Неизвестный инструмент: $toolName"
            }
        } catch (e: Exception) {
            "Ошибка при выполнении $toolName: ${e.message}"
        }

        truncateOutput(rawOutput)
    }

    // --- File read/write ---

    fun readFile(rawPath: String, maxLines: Int): String {
        if (rawPath.isBlank()) return "Ошибка: путь к файлу не указан."
        val file = resolveFile(rawPath)
        if (!file.exists()) return "Файл '${file.absolutePath}' не существует."
        if (file.isDirectory) return "Ошибка: '${file.absolutePath}' является папкой, а не файлом."

        val lines = mutableListOf<String>()
        var totalLinesCount = 0
        var truncated = false

        file.bufferedReader(Charsets.UTF_8).use { reader ->
            var line = reader.readLine()
            while (line != null) {
                totalLinesCount++
                if (lines.size < maxLines) {
                    lines.add(line)
                } else {
                    truncated = true
                }
                line = reader.readLine()
            }
        }

        return buildString {
            append("Содержимое файла '${file.name}' (${formatFileSize(file.length())}):\n")
            append("```\n")
            append(lines.joinToString("\n"))
            append("\n```")
            if (truncated) {
                append("\n[Показано $maxLines из $totalLinesCount строк]")
            }
        }
    }

    fun writeFile(rawPath: String, content: String, append: Boolean): String {
        if (rawPath.isBlank()) return "Ошибка: путь к файлу не указан."
        val file = resolveFile(rawPath)

        file.parentFile?.let { parent ->
            if (!parent.exists()) parent.mkdirs()
        }

        return try {
            if (append && file.exists()) {
                file.appendText(content, Charsets.UTF_8)
                "Текст успешно добавлен в конец файла: '${file.absolutePath}'. Новый размер: ${formatFileSize(file.length())}."
            } else {
                file.writeText(content, Charsets.UTF_8)
                "Файл успешно записан: '${file.absolutePath}' (${formatFileSize(file.length())})."
            }
        } catch (e: Exception) {
            "Ошибка записи в файл: ${e.localizedMessage}"
        }
    }

    fun deleteFile(rawPath: String, recursive: Boolean): String {
        if (rawPath.isBlank()) return "Ошибка: путь для удаления не указан."
        val file = resolveFile(rawPath)
        if (!file.exists()) return "Файл или папка '${file.absolutePath}' не найден(а)."

        // Safety check: protect root and primary storage dirs
        val extRoot = Environment.getExternalStorageDirectory().canonicalPath
        val canonical = file.canonicalPath
        if (canonical == extRoot || canonical == "/" || canonical == "/storage/emulated/0") {
            return "Защита безопасности: удаление корневой директории запрещено!"
        }

        return if (file.isDirectory) {
            val count = file.walkTopDown().count()
            if (recursive) {
                if (file.deleteRecursively()) {
                    "Папка '${file.name}' и все вложенные элементы ($count) успешно удалены."
                } else {
                    "Не удалось полностью удалить папку '${file.absolutePath}'."
                }
            } else {
                val children = file.listFiles()
                if (children.isNullOrEmpty()) {
                    if (file.delete()) "Пустая папка '${file.name}' успешно удалена."
                    else "Не удалось удалить папку."
                } else {
                    "Папка '${file.name}' содержит элементы (${children.size}). Укажите recursive=true для подтверждения удаления всей папки."
                }
            }
        } else {
            val name = file.name
            if (file.delete()) {
                "Файл '$name' успешно удален."
            } else {
                "Не удалось удалить файл '${file.absolutePath}'."
            }
        }
    }

    fun createDir(rawPath: String): String {
        if (rawPath.isBlank()) return "Ошибка: путь к папке не указан."
        val dir = resolveFolder(rawPath)
        return if (dir.exists()) {
            "Папка уже существует: '${dir.absolutePath}'."
        } else {
            if (dir.mkdirs()) {
                "Папка успешно создана: '${dir.absolutePath}'."
            } else {
                "Не удалось создать папку '${dir.absolutePath}'."
            }
        }
    }

    fun moveFile(rawSource: String, rawDest: String): String {
        if (rawSource.isBlank() || rawDest.isBlank()) return "Ошибка: укажите исходный и целевой пути."
        val src = resolveFile(rawSource)
        if (!src.exists()) return "Исходный файл '${src.absolutePath}' не существует."

        var dest = resolveFile(rawDest)
        if (dest.isDirectory) {
            dest = File(dest, src.name)
        } else {
            dest.parentFile?.let { if (!it.exists()) it.mkdirs() }
        }

        return try {
            if (src.renameTo(dest)) {
                "Перемещено: '${src.name}' -> '${dest.absolutePath}'."
            } else {
                // Cross-device fallback
                src.copyTo(dest, overwrite = true)
                src.delete()
                "Перемещено (через копирование): '${src.name}' -> '${dest.absolutePath}'."
            }
        } catch (e: Exception) {
            "Ошибка при перемещении: ${e.localizedMessage}"
        }
    }

    fun copyFile(rawSource: String, rawDest: String): String {
        if (rawSource.isBlank() || rawDest.isBlank()) return "Ошибка: укажите исходный и целевой пути."
        val src = resolveFile(rawSource)
        if (!src.exists()) return "Исходный файл '${src.absolutePath}' не существует."

        var dest = resolveFile(rawDest)
        if (dest.isDirectory) {
            dest = File(dest, src.name)
        } else {
            dest.parentFile?.let { if (!it.exists()) it.mkdirs() }
        }

        return try {
            if (src.isDirectory) {
                src.copyRecursively(dest, overwrite = true)
                "Папка '${src.name}' успешно скопирована в '${dest.absolutePath}'."
            } else {
                src.copyTo(dest, overwrite = true)
                "Файл '${src.name}' успешно скопирован в '${dest.absolutePath}' (${formatFileSize(dest.length())})."
            }
        } catch (e: Exception) {
            "Ошибка при копировании: ${e.localizedMessage}"
        }
    }

    // --- Search ---

    fun searchFiles(query: String, rawPath: String, extension: String): String {
        val rootDir = resolveFolder(rawPath)
        if (!rootDir.exists() || !rootDir.isDirectory) {
            return "Папка для поиска '${rootDir.absolutePath}' не найдена."
        }

        val normQuery = query.lowercase().trim()
        val normExt = extension.lowercase().removePrefix(".").trim()

        val results = mutableListOf<File>()
        val maxResults = 30

        try {
            rootDir.walkTopDown()
                .maxDepth(5)
                .filter { it.isFile }
                .filter { file ->
                    val matchesQuery = normQuery.isEmpty() || file.name.lowercase().contains(normQuery)
                    val matchesExt = normExt.isEmpty() || file.extension.lowercase() == normExt
                    matchesQuery && matchesExt
                }
                .take(maxResults + 1)
                .forEach { results.add(it) }
        } catch (e: Exception) {
            return "Ошибка при поиске: ${e.localizedMessage}"
        }

        if (results.isEmpty()) {
            return "Файлов по запросу (query='$query', ext='$extension') в '${rootDir.name}' не найдено."
        }

        val isTruncated = results.size > maxResults
        val displayList = results.take(maxResults)

        return buildString {
            append("Найдено совпадений: ${displayList.size}${if (isTruncated) " (показаны первые $maxResults)" else ""}:\n")
            displayList.forEach { f ->
                val rel = f.relativeToOrSelf(rootDir).path
                append("  • $rel (${formatFileSize(f.length())})\n")
            }
        }
    }

    // --- Archive operations ---

    fun createArchive(rawSourcePaths: List<String>, rawZipPath: String): String {
        if (rawSourcePaths.isEmpty()) return "Ошибка: список файлов для архивации пуст."
        if (rawZipPath.isBlank()) return "Ошибка: путь к целевому zip-файлу не указан."

        var targetZip = resolveFile(rawZipPath)
        if (!targetZip.name.endsWith(".zip", ignoreCase = true)) {
            targetZip = File(targetZip.parentFile ?: Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "${targetZip.name}.zip")
        }

        targetZip.parentFile?.let { if (!it.exists()) it.mkdirs() }

        val sources = rawSourcePaths.map { resolveFile(it) }.filter { it.exists() }
        if (sources.isEmpty()) {
            return "Ни один из указанных исходных файлов не найден."
        }

        return try {
            val zip = ZipFile(targetZip)
            for (item in sources) {
                if (item.isDirectory) {
                    zip.addFolder(item)
                } else if (item.isFile) {
                    zip.addFile(item)
                }
            }
            "ZIP-архив успешно создан: '${targetZip.absolutePath}' (${formatFileSize(targetZip.length())}). Добавлено элементов: ${sources.size}."
        } catch (e: Exception) {
            "Ошибка создания архива: ${e.localizedMessage}"
        }
    }

    fun extractArchive(rawZipPath: String, rawTargetDir: String): String {
        val zipFile = resolveFile(rawZipPath)
        if (!zipFile.exists() || !zipFile.isFile) return "Архив '${zipFile.absolutePath}' не найден."

        val targetDir = if (rawTargetDir.isBlank()) {
            val baseName = zipFile.nameWithoutExtension
            File(zipFile.parentFile ?: Environment.getExternalStorageDirectory(), baseName)
        } else {
            resolveFolder(rawTargetDir)
        }

        if (!targetDir.exists()) targetDir.mkdirs()

        return try {
            val zip = ZipFile(zipFile)
            if (!zip.isValidZipFile) return "Файл '${zipFile.name}' поврежден или не является валидным ZIP-архивом."
            zip.extractAll(targetDir.absolutePath)
            val extractedCount = targetDir.walkTopDown().filter { it.isFile }.count()
            "Архив '${zipFile.name}' успешно распакован в '${targetDir.absolutePath}'. Извлечено файлов: $extractedCount."
        } catch (e: Exception) {
            "Ошибка при распаковке архива: ${e.localizedMessage}"
        }
    }

    // --- Clipboard ---

    suspend fun clipboardRead(context: Context): String = withContext(Dispatchers.Main) {
        try {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = cm.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val text = clip.getItemAt(0).coerceToText(context).toString()
                if (text.isNotBlank()) {
                    "Текст из буфера обмена:\n\"$text\""
                } else {
                    "Буфер обмена пуст."
                }
            } else {
                "Буфер обмена пуст."
            }
        } catch (e: Exception) {
            "Не удалось прочитать буфер обмена: ${e.localizedMessage}"
        }
    }

    suspend fun clipboardWrite(text: String, context: Context): String = withContext(Dispatchers.Main) {
        try {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Runner Agent", text)
            cm.setPrimaryClip(clip)
            "Текст (${text.length} симв.) успешно скопирован в буфер обмена."
        } catch (e: Exception) {
            "Не удалось записать в буфер обмена: ${e.localizedMessage}"
        }
    }

    // --- Shell execution ---

    fun runShellCommand(command: String, timeoutSeconds: Int): String {
        if (command.isBlank()) return "Ошибка: команда не задана."

        return try {
            val process = ProcessBuilder("sh", "-c", command)
                .redirectErrorStream(true)
                .start()

            val output = StringBuilder()
            val reader = BufferedReader(InputStreamReader(process.inputStream))

            val completed = process.waitFor(timeoutSeconds.toLong(), TimeUnit.SECONDS)
            if (!completed) {
                process.destroy()
                return "Команда '$command' превысила таймаут в $timeoutSeconds сек. и была принудительно остановлена."
            }

            var line = reader.readLine()
            var linesCount = 0
            while (line != null && linesCount < 200) {
                output.append(line).append("\n")
                linesCount++
                line = reader.readLine()
            }

            val exitCode = process.exitValue()
            val resultText = output.toString().trim()

            buildString {
                append("Выполнено (код $exitCode):\n")
                if (resultText.isNotBlank()) {
                    append("```\n")
                    append(resultText)
                    append("\n```")
                } else {
                    append("[Вывод пуст]")
                }
            }
        } catch (e: Exception) {
            "Ошибка при выполнении shell-команды: ${e.localizedMessage}"
        }
    }

    // --- Existing Utilities ---

    fun getFolderSummary(rawPath: String): String {
        val dir = resolveFolder(rawPath)
        if (!dir.exists()) return "Папка '${dir.absolutePath}' не найдена."
        if (!dir.isDirectory) return "'${dir.absolutePath}' не является папкой."

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

        val topHeaviest = fileList
            .sortedByDescending { it.length() }
            .take(5)
            .joinToString("\n") { "  • ${it.name} (${formatFileSize(it.length())})" }

        val heaviestSection = if (topHeaviest.isNotEmpty()) "\nТоп-5 самых тяжелых файлов:\n$topHeaviest" else ""

        return buildString {
            append("Сводка по папке '${dir.name}' (${dir.absolutePath}):\n")
            append("Всего файлов: $totalFiles, подпапок: $totalDirs.\n")
            append("Категории: Документы: $docsCount, Картинки: $imagesCount, Архивы: $archivesCount, APK: $apksCount, Другое: $othersCount.")
            append(heaviestSection)
        }
    }

    fun organizeDownloads(category: String): String {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!downloadsDir.exists() || !downloadsDir.isDirectory) return "Папка Download не найдена."

        val allFiles = downloadsDir.listFiles()?.filter { it.isFile } ?: emptyList()
        if (allFiles.isEmpty()) return "В папке Download нет файлов для перемещения."

        val normCategory = category.lowercase().trim()
        val movedFiles = mutableListOf<String>()

        fun moveMatching(files: List<File>, extensions: Set<String>, targetFolder: File) {
            if (!targetFolder.exists()) targetFolder.mkdirs()
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
            "documents" -> moveMatching(allFiles, docExtensions, docsDir)
            "apks" -> moveMatching(allFiles, apkExtensions, apksDir)
            "archives" -> moveMatching(allFiles, archiveExtensions, archivesDir)
            "images" -> moveMatching(allFiles, imageExtensions, picturesDir)
            "all" -> {
                moveMatching(allFiles, docExtensions, docsDir)
                moveMatching(allFiles, apkExtensions, apksDir)
                moveMatching(allFiles, archiveExtensions, archivesDir)
                moveMatching(allFiles, imageExtensions, picturesDir)
            }
            else -> return "Неизвестная категория: '$category'. Допустимые: 'documents', 'apks', 'archives', 'images', 'all'."
        }

        return if (movedFiles.isEmpty()) {
            "В папке Download не найдено файлов для категории '$category'."
        } else {
            buildString {
                append("Успешно перемещено файлов (${movedFiles.size}) для категории '$category':\n")
                movedFiles.take(10).forEach { append("  • $it\n") }
                if (movedFiles.size > 10) append("  ...и еще ${movedFiles.size - 10} файлов.")
            }
        }
    }

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
        if (trimmed.equals("documents", ignoreCase = true)) return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        if (trimmed.equals("pictures", ignoreCase = true)) return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        if (trimmed.equals("dcim", ignoreCase = true)) return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)

        val directFile = File(trimmed)
        if (directFile.isAbsolute) return directFile

        val inDownloads = File(defaultDownloads, trimmed)
        if (inDownloads.exists()) return inDownloads

        return File(Environment.getExternalStorageDirectory(), trimmed)
    }

    private fun resolveFile(path: String): File {
        val trimmed = path.trim()
        val directFile = File(trimmed)
        if (directFile.isAbsolute) return directFile

        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val inDownloads = File(downloads, trimmed)
        if (inDownloads.exists()) return inDownloads

        val inRoot = File(Environment.getExternalStorageDirectory(), trimmed)
        if (inRoot.exists()) return inRoot

        return inDownloads
    }

    private fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        val df = DecimalFormat("#,##0.#")
        return "${df.format(bytes / Math.pow(1024.0, digitGroups.toDouble()))} ${units[digitGroups]}"
    }

    // --- Storage aggregators ---

    fun getStorageSummary(): String {
        return try {
            val extDir = Environment.getExternalStorageDirectory()
            val stat = StatFs(extDir.path)
            val blockSize = stat.blockSizeLong
            val totalBytes = stat.blockCountLong * blockSize
            val availableBytes = stat.availableBlocksLong * blockSize
            val usedBytes = (totalBytes - availableBytes).coerceAtLeast(0L)

            var videoBytes = 0L
            var photoBytes = 0L
            var docsBytes = 0L
            var archiveBytes = 0L
            var junkBytes = 0L

            fun scanCategoryDir(dir: File, maxDepth: Int = 4) {
                if (!dir.exists() || !dir.isDirectory) return
                try {
                    dir.walkTopDown().maxDepth(maxDepth).forEach { file ->
                        if (file.isFile) {
                            val len = file.length()
                            val ext = file.extension.lowercase()
                            when {
                                ext in videoExtensions -> videoBytes += len
                                ext in imageExtensions -> photoBytes += len
                                ext in docExtensions -> docsBytes += len
                                ext in archiveExtensions -> archiveBytes += len
                                ext in junkExtensions -> junkBytes += len
                            }
                        }
                    }
                } catch (ignored: Exception) {
                }
            }

            scanCategoryDir(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS))
            scanCategoryDir(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM))
            scanCategoryDir(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES))
            scanCategoryDir(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES))
            scanCategoryDir(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS))

            val usedPercent = if (totalBytes > 0) ((usedBytes.toDouble() / totalBytes) * 100).toInt() else 0

            buildString {
                append("Сводка по хранилищу устройства:\n")
                append("• Всего памяти: ${formatFileSize(totalBytes)}\n")
                append("• Занято: ${formatFileSize(usedBytes)} ($usedPercent%)\n")
                append("• Свободно: ${formatFileSize(availableBytes)}\n\n")
                append("Вес основных категорий в медиа-папках:\n")
                append("• Фото и изображения: ${formatFileSize(photoBytes)}\n")
                append("• Видеозаписи: ${formatFileSize(videoBytes)}\n")
                append("• Документы: ${formatFileSize(docsBytes)}\n")
                append("• Архивы: ${formatFileSize(archiveBytes)}\n")
                append("• Временные файлы и кэш: ${formatFileSize(junkBytes)}")
            }
        } catch (e: Exception) {
            "Ошибка при получении сводки хранилища: ${e.localizedMessage}"
        }
    }

    fun findLargestFiles(limit: Int = 10, minSizeMb: Long = 50L): String {
        val root = Environment.getExternalStorageDirectory()
        val minSizeBytes = minSizeMb * 1024L * 1024L
        val largeFiles = mutableListOf<File>()

        try {
            root.walkTopDown()
                .maxDepth(6)
                .onEnter { dir ->
                    val name = dir.name
                    !name.equals("Android", ignoreCase = true) || dir.parentFile == root
                }
                .filter { it.isFile && it.length() >= minSizeBytes }
                .take(150)
                .forEach { largeFiles.add(it) }
        } catch (ignored: Exception) {
        }

        if (largeFiles.isEmpty()) {
            return "Файлов размером более $minSizeMb МБ не обнаружено."
        }

        val sorted = largeFiles.sortedByDescending { it.length() }
        val displayList = sorted.take(limit.coerceIn(1, 30))

        return buildString {
            append("Топ самых тяжелых файлов (>${minSizeMb} МБ):\n")
            displayList.forEachIndexed { i, file ->
                val rel = file.relativeToOrSelf(root).path
                append("${i + 1}. $rel — ${formatFileSize(file.length())}\n")
            }
            if (sorted.size > displayList.size) {
                append("\n[Найдено еще ${sorted.size - displayList.size} тяжелых файлов. Увеличьте порог min_size_mb для точной выборки]")
            }
        }
    }

    fun findJunkFiles(): String {
        val root = Environment.getExternalStorageDirectory()
        val junkFiles = mutableListOf<File>()
        val emptyDirs = mutableListOf<File>()
        var totalJunkBytes = 0L

        val scanFolders = listOf(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            File(root, "Telegram"),
            File(root, "WhatsApp")
        ).filter { it.exists() && it.isDirectory }

        for (dir in scanFolders) {
            try {
                dir.walkTopDown().maxDepth(4).forEach { f ->
                    if (f.isFile) {
                        val ext = f.extension.lowercase()
                        if (ext in junkExtensions || f.name.startsWith(".tmp") || f.name.endsWith(".tmp")) {
                            junkFiles.add(f)
                            totalJunkBytes += f.length()
                        }
                    } else if (f.isDirectory && f != dir) {
                        val contents = f.list()
                        if (contents != null && contents.isEmpty()) {
                            emptyDirs.add(f)
                        }
                    }
                }
            } catch (ignored: Exception) {
            }
        }

        if (junkFiles.isEmpty() && emptyDirs.isEmpty()) {
            return "Мусорные файлы (.tmp, .log, .crdownload) и пустые папки не обнаружены. Система чиста."
        }

        return buildString {
            append("Обнаружено мусорных данных:\n")
            append("• Временных файлов: ${junkFiles.size} (${formatFileSize(totalJunkBytes)})\n")
            append("• Пустых папок: ${emptyDirs.size}\n\n")
            if (junkFiles.isNotEmpty()) {
                append("Файлы для возможной очистки (первые 20):\n")
                junkFiles.take(20).forEach {
                    append("  • ${it.name} (${formatFileSize(it.length())})\n")
                }
            }
            if (emptyDirs.isNotEmpty()) {
                append("\nПустые папки:\n")
                emptyDirs.take(10).forEach {
                    append("  • ${it.name}/\n")
                }
            }
        }
    }

    // --- Safety Truncation ---

    fun truncateOutput(text: String, maxBytes: Int = 3500, maxLines: Int = 35): String {
        val bytes = text.toByteArray(Charsets.UTF_8)
        if (bytes.size <= maxBytes) {
            val lines = text.lines()
            if (lines.size <= maxLines) {
                return text
            }
            val truncatedLines = lines.take(maxLines).joinToString("\n")
            return "$truncatedLines\n\n[Вывод сокращен: показано $maxLines из ${lines.size} строк. Для детального просмотра уточни запрос]"
        }

        var cutIndex = 0
        var byteCount = 0
        for (i in text.indices) {
            val charBytes = text[i].toString().toByteArray(Charsets.UTF_8).size
            if (byteCount + charBytes > maxBytes - 120) break
            byteCount += charBytes
            cutIndex = i + 1
        }

        val truncatedText = text.substring(0, cutIndex)
        val lastNewline = truncatedText.lastIndexOf('\n')
        val cleanCut = if (lastNewline > cutIndex / 2) truncatedText.substring(0, lastNewline) else truncatedText

        return "$cleanCut\n\n[Вывод сокращен: размер превысил лимит ${formatFileSize(maxBytes.toLong())}. Для детального просмотра уточни запрос]"
    }

    // --- HITL Metadata ---

    data class CriticalActionInfo(
        val title: String,
        val details: String,
        val warning: String
    )

    fun isCriticalOperation(toolName: String): Boolean {
        return toolName in setOf(
            "delete_file",
            "move_file",
            "organize_downloads",
            "run_shell_command",
            "write_file"
        )
    }

    fun describeCriticalAction(toolName: String, argsJson: String): CriticalActionInfo {
        val args = try {
            if (argsJson.isBlank()) JSONObject() else JSONObject(argsJson)
        } catch (e: Exception) {
            JSONObject()
        }

        return when (toolName) {
            "delete_file" -> {
                val path = args.optString("path", "").trim()
                val recursive = args.optBoolean("recursive", false)
                CriticalActionInfo(
                    title = "Удаление данных",
                    details = "Объект: $path${if (recursive) " (рекурсивно, включая вложенные файлы)" else ""}",
                    warning = "Удаленные файлы невозможно восстановить через корзину."
                )
            }
            "write_file" -> {
                val path = args.optString("path", "").trim()
                val append = args.optBoolean("append", false)
                CriticalActionInfo(
                    title = if (append) "Дозапись в файл" else "Перезапись файла",
                    details = "Файл: $path",
                    warning = if (append) "Текст будет добавлен в конец существующего файла." else "Содержимое файла будет полностью перезаписано."
                )
            }
            "move_file" -> {
                val src = args.optString("source_path", "").trim()
                val dest = args.optString("destination_path", "").trim()
                CriticalActionInfo(
                    title = "Перемещение файла",
                    details = "Из: $src\nВ: $dest",
                    warning = "Файл будет перемещен в новое местоположение."
                )
            }
            "organize_downloads" -> {
                val cat = args.optString("category", "all")
                CriticalActionInfo(
                    title = "Сортировка загрузок",
                    details = "Категория: $cat (папка Download)",
                    warning = "Файлы из Download будут перемещены в Documents/Runner, Backups/APKs или Pictures/Runner."
                )
            }
            "run_shell_command" -> {
                val cmd = args.optString("command", "").trim()
                CriticalActionInfo(
                    title = "Выполнение shell-команды",
                    details = "Команда: $cmd",
                    warning = "Команда будет запущена в оболочке sh с правами приложения."
                )
            }
            else -> CriticalActionInfo(
                title = "Выполнение операции",
                details = "Инструмент: $toolName",
                warning = "Операция может изменить локальные файлы."
            )
        }
    }
}
