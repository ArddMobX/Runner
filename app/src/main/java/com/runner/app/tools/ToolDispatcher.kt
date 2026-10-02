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
            description = "Быстро сканирует директорию на устройстве и возвращает сводку: общее количество файлов, сколько картинок, документов, архивов, APK и топ-5 самых тяжелых файлов (абсолютные пути).",
            properties = JSONObject().apply {
                put("path", JSONObject().apply {
                    put("type", "string")
                    put("description", "Путь к папке (например, 'Download', 'Documents' или полный путь). По умолчанию 'Download'.")
                })
            }
        ))

        // 4b. list_dir
        tools.put(createToolFunction(
            name = "list_dir",
            description = "Возвращает плоский список содержимого папки только абсолютными путями (корень /storage/emulated/0). Используй перед записью, чтобы проверить папку и не плодить Download/Download.",
            properties = JSONObject().apply {
                put("path", JSONObject().apply {
                    put("type", "string")
                    put("description", "Путь к папке (например, '/storage/emulated/0/Download'). По умолчанию 'Download'.")
                })
                put("limit", JSONObject().apply {
                    put("type", "integer")
                    put("description", "Максимум элементов (по умолчанию 50, максимум 100).")
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
            description = "Создает или перезаписывает текстовый файл, либо дописывает в него текст (append). Если родительской папки нет — возвращает ошибку и ничего не создаёт; папку создай заранее тулом create_dir (перед записью проверь папку через list_dir).",
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
            description = "Выполняет команду оболочки (sh, Android Toybox — только короткие флаги, длинных --флагов нет) и возвращает stdout, stderr и код завершения. Размер папок меряй тулом get_folder_summary, а не du.",
            properties = JSONObject().apply {
                put("command", JSONObject().apply {
                    put("type", "string")
                    put("description", "Команда шелла (например, 'df -h', 'du -d 1 /sdcard/Download', 'ls /sdcard'). GNU-флаги вроде --max-depth запрещены.")
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
        val cleanToolName = toolName.substringAfterLast(":")
        val args = try {
            if (argumentsJson.isBlank()) JSONObject() else JSONObject(argumentsJson)
        } catch (e: Exception) {
            JSONObject()
        }

        val rawOutput = try {
            when (cleanToolName) {
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

                "list_dir" -> {
                    val path = args.optString("path", "").trim()
                    val limit = args.optInt("limit", 50)
                    listDir(path, limit)
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

                else -> "Неизвестный инструмент: $cleanToolName"
            }
        } catch (e: Exception) {
            "Ошибка при выполнении $cleanToolName: ${e.message}"
        }

        truncateOutput(rawOutput)
    }

    // --- File read/write ---

    fun readFile(rawPath: String, maxLines: Int): String {
        if (rawPath.isBlank()) return "Ошибка: путь к файлу не указан."
        val file = try {
            resolveFile(rawPath)
        } catch (e: SecurityException) {
            return "Ошибка: ${e.message}"
        }
        if (!file.exists()) return "resolved_path: ${file.absolutePath}\nФайл '${file.absolutePath}' не существует."
        if (file.isDirectory) return "resolved_path: ${file.absolutePath}\nОшибка: '${file.absolutePath}' является папкой, а не файлом."

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
            append("resolved_path: ${file.absolutePath}\n")
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
        val file = try {
            resolveFile(rawPath)
        } catch (e: SecurityException) {
            return "Ошибка: ${e.message}"
        }

        // Папки молча не создаём: их создаёт только явный тул create_dir.
        val parent = file.parentFile
        if (parent != null && !parent.exists()) {
            return "resolved_path: ${file.absolutePath}\nОшибка: родительской папки нет: '${parent.absolutePath}'. Сначала проверь папку через list_dir и создай её тулом create_dir."
        }

        return try {
            if (append && file.exists()) {
                file.appendText(content, Charsets.UTF_8)
                "resolved_path: ${file.absolutePath}\nТекст успешно добавлен в конец файла: '${file.absolutePath}'. Новый размер: ${formatFileSize(file.length())}."
            } else {
                file.writeText(content, Charsets.UTF_8)
                "resolved_path: ${file.absolutePath}\nФайл успешно записан: '${file.absolutePath}' (${formatFileSize(file.length())})."
            }
        } catch (e: Exception) {
            "Ошибка записи в файл: ${e.localizedMessage}"
        }
    }

    fun deleteFile(rawPath: String, recursive: Boolean): String {
        if (rawPath.isBlank()) return "Ошибка: путь для удаления не указан."
        val file = try {
            resolveFile(rawPath)
        } catch (e: SecurityException) {
            return "Ошибка: ${e.message}"
        }
        if (!file.exists()) return "resolved_path: ${file.absolutePath}\nФайл или папка '${file.absolutePath}' не найден(а)."

        // Safety check: protect root and primary storage dirs
        val extRoot = Environment.getExternalStorageDirectory().canonicalPath
        val canonical = file.canonicalPath
        if (canonical == extRoot || canonical == "/" || canonical == "/storage/emulated/0") {
            return "resolved_path: ${file.absolutePath}\nЗащита безопасности: удаление корневой директории запрещено!"
        }

        return if (file.isDirectory) {
            val count = file.walkTopDown().count()
            if (recursive) {
                if (file.deleteRecursively()) {
                    "resolved_path: ${file.absolutePath}\nПапка '${file.name}' и все вложенные элементы ($count) успешно удалены."
                } else {
                    "resolved_path: ${file.absolutePath}\nНе удалось полностью удалить папку '${file.absolutePath}'."
                }
            } else {
                val children = file.listFiles()
                if (children.isNullOrEmpty()) {
                    if (file.delete()) "resolved_path: ${file.absolutePath}\nПустая папка '${file.name}' успешно удалена."
                    else "resolved_path: ${file.absolutePath}\nНе удалось удалить папку."
                } else {
                    "resolved_path: ${file.absolutePath}\nПапка '${file.name}' содержит элементы (${children.size}). Укажите recursive=true для подтверждения удаления всей папки."
                }
            }
        } else {
            val name = file.name
            if (file.delete()) {
                "resolved_path: ${file.absolutePath}\nФайл '$name' успешно удален."
            } else {
                "resolved_path: ${file.absolutePath}\nНе удалось удалить файл '${file.absolutePath}'."
            }
        }
    }

    fun createDir(rawPath: String): String {
        if (rawPath.isBlank()) return "Ошибка: путь к папке не указан."
        val dir = try {
            resolveFolder(rawPath)
        } catch (e: SecurityException) {
            return "Ошибка: ${e.message}"
        }
        // create_dir — единственный тул, которому разрешено создавать папки.
        return if (dir.exists()) {
            "resolved_path: ${dir.absolutePath}\nПапка уже существует: '${dir.absolutePath}'."
        } else {
            if (dir.mkdirs()) {
                "resolved_path: ${dir.absolutePath}\nПапка успешно создана: '${dir.absolutePath}'."
            } else {
                "resolved_path: ${dir.absolutePath}\nНе удалось создать папку '${dir.absolutePath}'."
            }
        }
    }

    fun moveFile(rawSource: String, rawDest: String): String {
        if (rawSource.isBlank() || rawDest.isBlank()) return "Ошибка: укажите исходный и целевой пути."
        val src = try {
            resolveFile(rawSource)
        } catch (e: SecurityException) {
            return "Ошибка: ${e.message}"
        }
        if (!src.exists()) return "resolved_path_src: ${src.absolutePath}\nИсходный файл '${src.absolutePath}' не существует."

        var dest = try {
            resolveFile(rawDest)
        } catch (e: SecurityException) {
            return "Ошибка: ${e.message}"
        }
        if (dest.isDirectory) {
            dest = File(dest, src.name)
        } else {
            val parent = dest.parentFile
            if (parent != null && !parent.exists()) {
                return "resolved_path_src: ${src.absolutePath}\nresolved_path_dst: ${dest.absolutePath}\nОшибка: родительской папки нет: '${parent.absolutePath}'. Сначала создай её тулом create_dir."
            }
        }

        return try {
            if (src.renameTo(dest)) {
                "resolved_path_src: ${src.absolutePath}\nresolved_path_dst: ${dest.absolutePath}\nresolved_path: ${dest.absolutePath}\nПеремещено: '${src.name}' -> '${dest.absolutePath}'."
            } else {
                // Cross-device fallback
                src.copyTo(dest, overwrite = true)
                src.delete()
                "resolved_path_src: ${src.absolutePath}\nresolved_path_dst: ${dest.absolutePath}\nresolved_path: ${dest.absolutePath}\nПеремещено (через копирование): '${src.name}' -> '${dest.absolutePath}'."
            }
        } catch (e: Exception) {
            "resolved_path_src: ${src.absolutePath}\nresolved_path_dst: ${dest.absolutePath}\nОшибка при перемещении: ${e.localizedMessage}"
        }
    }

    fun copyFile(rawSource: String, rawDest: String): String {
        if (rawSource.isBlank() || rawDest.isBlank()) return "Ошибка: укажите исходный и целевой пути."
        val src = try {
            resolveFile(rawSource)
        } catch (e: SecurityException) {
            return "Ошибка: ${e.message}"
        }
        if (!src.exists()) return "resolved_path_src: ${src.absolutePath}\nИсходный файл '${src.absolutePath}' не существует."

        var dest = try {
            resolveFile(rawDest)
        } catch (e: SecurityException) {
            return "Ошибка: ${e.message}"
        }
        if (dest.isDirectory) {
            dest = File(dest, src.name)
        } else {
            val parent = dest.parentFile
            if (parent != null && !parent.exists()) {
                return "resolved_path_src: ${src.absolutePath}\nresolved_path_dst: ${dest.absolutePath}\nОшибка: родительской папки нет: '${parent.absolutePath}'. Сначала создай её тулом create_dir."
            }
        }

        return try {
            if (src.isDirectory) {
                src.copyRecursively(dest, overwrite = true)
                "resolved_path_src: ${src.absolutePath}\nresolved_path_dst: ${dest.absolutePath}\nresolved_path: ${dest.absolutePath}\nПапка '${src.name}' успешно скопирована в '${dest.absolutePath}'."
            } else {
                src.copyTo(dest, overwrite = true)
                "resolved_path_src: ${src.absolutePath}\nresolved_path_dst: ${dest.absolutePath}\nresolved_path: ${dest.absolutePath}\nФайл '${src.name}' успешно скопирован в '${dest.absolutePath}' (${formatFileSize(dest.length())})."
            }
        } catch (e: Exception) {
            "resolved_path_src: ${src.absolutePath}\nresolved_path_dst: ${dest.absolutePath}\nОшибка при копировании: ${e.localizedMessage}"
        }
    }

    // --- Search ---

    fun searchFiles(query: String, rawPath: String, extension: String): String {
        val rootDir = try {
            resolveFolder(rawPath.ifBlank { "Download" })
        } catch (e: SecurityException) {
            return "Ошибка: ${e.message}"
        }
        if (!rootDir.exists() || !rootDir.isDirectory) {
            return "resolved_path: ${rootDir.absolutePath}\nПапка для поиска '${rootDir.absolutePath}' не найдена."
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
            return "resolved_path: ${rootDir.absolutePath}\nФайлов по запросу (query='$query', ext='$extension') в '${rootDir.absolutePath}' не найдено."
        }

        val isTruncated = results.size > maxResults
        val displayList = results.take(maxResults)

        return buildString {
            append("resolved_path: ${rootDir.absolutePath}\n")
            append("Найдено совпадений: ${displayList.size}${if (isTruncated) " (показаны первые $maxResults)" else ""}:\n")
            displayList.forEach { f ->
                append("  • ${f.absolutePath} (${formatFileSize(f.length())})\n")
            }
        }
    }

    /**
     * list_dir: плоский листинг папки. Возвращает только абсолютные пути,
     * чтобы модель не конструировала относительные и не плодила Download/Download.
     */
    fun listDir(rawPath: String, limit: Int = 50): String {
        val dir = try {
            resolveFolder(rawPath.ifBlank { "Download" })
        } catch (e: SecurityException) {
            return "Ошибка: ${e.message}"
        }
        if (!dir.exists()) return "resolved_path: ${dir.absolutePath}\nПапка '${dir.absolutePath}' не найдена."
        if (!dir.isDirectory) return "resolved_path: ${dir.absolutePath}\n'${dir.absolutePath}' не является папкой."

        val safeLimit = limit.coerceIn(1, 100)
        val children = try {
            dir.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })) ?: emptyList()
        } catch (e: Exception) {
            return "resolved_path: ${dir.absolutePath}\nНе удалось получить список файлов: ${e.localizedMessage}"
        }

        return buildString {
            append("resolved_path: ${dir.absolutePath}\n")
            append("Содержимое '${dir.absolutePath}' (папок: ${children.count { it.isDirectory }}, файлов: ${children.count { it.isFile }}):\n")
            children.take(safeLimit + 1).take(safeLimit).forEach { f ->
                if (f.isDirectory) {
                    append("  • ${f.absolutePath}/\n")
                } else {
                    append("  • ${f.absolutePath} (${formatFileSize(f.length())})\n")
                }
            }
            if (children.size > safeLimit) {
                append("[Показаны первые $safeLimit из ${children.size}]")
            }
        }
    }

    // --- Archive operations ---

    fun createArchive(rawSourcePaths: List<String>, rawZipPath: String): String {
        if (rawSourcePaths.isEmpty()) return "Ошибка: список файлов для архивации пуст."
        if (rawZipPath.isBlank()) return "Ошибка: путь к целевому zip-файлу не указан."

        var targetZip = try {
            resolveFile(rawZipPath)
        } catch (e: SecurityException) {
            return "Ошибка: ${e.message}"
        }
        if (!targetZip.name.endsWith(".zip", ignoreCase = true)) {
            targetZip = File(targetZip.parentFile ?: Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "${targetZip.name}.zip")
        }

        val zipParent = targetZip.parentFile
        if (zipParent != null && !zipParent.exists()) {
            return "resolved_path: ${targetZip.absolutePath}\nОшибка: родительской папки нет: '${zipParent.absolutePath}'. Сначала создай её тулом create_dir."
        }

        val sources = rawSourcePaths.mapNotNull {
            try {
                resolveFile(it)
            } catch (e: SecurityException) {
                null
            }
        }.filter { it.exists() }
        if (sources.isEmpty()) {
            return "resolved_path: ${targetZip.absolutePath}\nНи один из указанных исходных файлов не найден."
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
            "resolved_path: ${targetZip.absolutePath}\nZIP-архив успешно создан: '${targetZip.absolutePath}' (${formatFileSize(targetZip.length())}). Добавлено элементов: ${sources.size}."
        } catch (e: Exception) {
            "resolved_path: ${targetZip.absolutePath}\nОшибка создания архива: ${e.localizedMessage}"
        }
    }

    fun extractArchive(rawZipPath: String, rawTargetDir: String): String {
        val zipFile = try {
            resolveFile(rawZipPath)
        } catch (e: SecurityException) {
            return "Ошибка: ${e.message}"
        }
        if (!zipFile.exists() || !zipFile.isFile) return "resolved_path: ${zipFile.absolutePath}\nАрхив '${zipFile.absolutePath}' не найден."

        val targetDir = try {
            if (rawTargetDir.isBlank()) {
                val baseName = zipFile.nameWithoutExtension
                val auto = File(zipFile.parentFile ?: Environment.getExternalStorageDirectory(), baseName)
                // Анти-дубль: архив Download/x.zip + target_dir "x" не должны дать x/x.
                resolvePath(auto.absolutePath, base = zipFile.parentFile)
            } else {
                resolvePath(rawTargetDir, base = zipFile.parentFile)
            }
        } catch (e: SecurityException) {
            return "Ошибка: ${e.message}"
        }

        // Распаковке разрешено создать только саму целевую папку; её родитель обязан существовать.
        val targetParent = targetDir.parentFile
        if (targetParent != null && !targetParent.exists()) {
            return "resolved_path: ${targetDir.absolutePath}\nОшибка: родительской папки нет: '${targetParent.absolutePath}'. Сначала создай её тулом create_dir."
        }
        if (!targetDir.exists()) targetDir.mkdirs()

        return try {
            val zip = ZipFile(zipFile)
            if (!zip.isValidZipFile) return "resolved_path: ${targetDir.absolutePath}\nФайл '${zipFile.name}' поврежден или не является валидным ZIP-архивом."
            zip.extractAll(targetDir.absolutePath)
            val extractedCount = targetDir.walkTopDown().filter { it.isFile }.count()
            "resolved_path: ${targetDir.absolutePath}\nАрхив '${zipFile.name}' успешно распакован в '${targetDir.absolutePath}'. Извлечено файлов: $extractedCount."
        } catch (e: Exception) {
            "resolved_path: ${targetDir.absolutePath}\nОшибка при распаковке архива: ${e.localizedMessage}"
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
        val dir = try {
            resolveFolder(rawPath.ifBlank { "Download" })
        } catch (e: SecurityException) {
            return "Ошибка: ${e.message}"
        }
        if (!dir.exists()) return "resolved_path: ${dir.absolutePath}\nПапка '${dir.absolutePath}' не найдена."
        if (!dir.isDirectory) return "resolved_path: ${dir.absolutePath}\n'${dir.absolutePath}' не является папкой."

        val allFiles = dir.listFiles() ?: return "resolved_path: ${dir.absolutePath}\nНе удалось получить список файлов (нет доступа)."

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
            .joinToString("\n") { "  • ${it.absolutePath} (${formatFileSize(it.length())})" }

        val heaviestSection = if (topHeaviest.isNotEmpty()) "\nТоп-5 самых тяжелых файлов:\n$topHeaviest" else ""

        return buildString {
            append("resolved_path: ${dir.absolutePath}\n")
            append("Сводка по папке '${dir.absolutePath}':\n")
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

    /**
     * Единая точка резолва всех файловых путей. Всегда возвращает абсолютный
     * канонический путь внутри корня /storage/emulated/0.
     *
     * Правила:
     * - Пустой ввод -> корень (вызывающие тулы проверяют пустоту отдельно).
     * - Абсолютный путь внутри корня -> как есть (после canonical-проверки).
     * - Относительный путь -> всегда от корня, НЕ от "текущей" папки и НЕ от Download.
     * - Анти-дубль: если задан base и первый сегмент ввода совпадает с именем base
     *   (например base=.../Download, ввод="Download/x"), а вложенного пути
     *   base/ввод не существует — резолвим от родителя base (не создаём Download/Download).
     * - Нормализация: обратные слэши, двойные слэши, хвостовые слэши, префикс file://.
     * - Безопасность: через canonicalPath проверяем, что результат внутри корня,
     *   иначе бросаем SecurityException (вызывающий тул превращает её в "Ошибка: ...").
     */
    fun resolvePath(input: String, base: File? = null): File {
        val root = Environment.getExternalStorageDirectory().canonicalFile
        val rootPath = root.canonicalPath

        var p = input.trim().replace('\\', '/')
        if (p.startsWith("file://")) p = p.removePrefix("file://")
        p = p.replace(Regex("/+"), "/")
        if (p.length > 1) p = p.trimEnd('/')
        if (p.isEmpty()) return root

        // Короткие алиасы системных папок резолвятся в те же точки внутри корня.
        val lower = p.lowercase()
        if (lower == "download" || lower == "downloads") {
            return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).canonicalFile
        }

        val candidate: File = if (p.startsWith("/")) {
            File(p)
        } else {
            val b = base?.takeIf { it.isAbsolute }?.canonicalFile ?: root
            val firstSeg = p.substringBefore('/')
            val nested = File(b, p)
            if (b.canonicalPath != rootPath && firstSeg.equals(b.name, ignoreCase = true) && !nested.exists()) {
                File(b.parentFile?.canonicalFile ?: root, p)
            } else {
                File(root, p)
            }
        }

        val canonical = try {
            candidate.canonicalPath
        } catch (e: Exception) {
            throw SecurityException("Некорректный путь: $input")
        }
        if (canonical != rootPath && !canonical.startsWith("$rootPath/")) {
            throw SecurityException("Путь вне корня /storage/emulated/0: $input")
        }
        return File(canonical)
    }

    /** Папка для файловых тулов. Историческое имя, внутри — resolvePath. */
    private fun resolveFolder(path: String): File {
        val trimmed = path.trim()
        if (trimmed.isEmpty()) {
            return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).canonicalFile
        }
        return resolvePath(trimmed)
    }

    /** Файл для файловых тулов. Историческое имя, внутри — resolvePath. */
    private fun resolveFile(path: String): File {
        return resolvePath(path.trim())
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
                append("${i + 1}. ${file.absolutePath} — ${formatFileSize(file.length())}\n")
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

        // Только отчёт: дубли вида Download/Download, Documents/Documents и т.п.
        // Ничего не удаляем и не перемещаем — решение за пользователем.
        val duplicates = findDuplicateDirs(scanFolders)

        if (junkFiles.isEmpty() && emptyDirs.isEmpty() && duplicates.isEmpty()) {
            return "Мусорные файлы (.tmp, .log, .crdownload), пустые папки и дубли вида Download/Download не обнаружены. Система чиста."
        }

        return buildString {
            append("Обнаружено мусорных данных:\n")
            append("• Временных файлов: ${junkFiles.size} (${formatFileSize(totalJunkBytes)})\n")
            append("• Пустых папок: ${emptyDirs.size}\n")
            append("• Подозрений на дубли (X/X): ${duplicates.size}\n\n")
            if (junkFiles.isNotEmpty()) {
                append("Файлы для возможной очистки (первые 20):\n")
                junkFiles.take(20).forEach {
                    append("  • ${it.absolutePath} (${formatFileSize(it.length())})\n")
                }
            }
            if (emptyDirs.isNotEmpty()) {
                append("\nПустые папки:\n")
                emptyDirs.take(10).forEach {
                    append("  • ${it.absolutePath}/\n")
                }
            }
            if (duplicates.isNotEmpty()) {
                append("\nВозможные дубли папок (только для просмотра, ничего не удалено):\n")
                duplicates.take(10).forEach {
                    append("  • ${it.absolutePath}/\n")
                }
            }
        }
    }

    /**
     * Read-only поиск дублей вида X/X (Download/Download, Documents/Documents):
     * папка, внутри которой есть подпапка с тем же именем (регистр игнорируется).
     * Ничего не трогает, только возвращает кандидатов абсолютными путями.
     */
    fun findDuplicateDirs(scanRoots: List<File>? = null): List<File> {
        val root = Environment.getExternalStorageDirectory()
        val roots = scanRoots ?: listOf(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
            root
        ).filter { it.exists() && it.isDirectory }

        val result = mutableListOf<File>()
        for (base in roots) {
            try {
                base.walkTopDown().maxDepth(3).forEach { dir ->
                    if (!dir.isDirectory) return@forEach
                    val child = File(dir, dir.name)
                    val childCi = dir.listFiles()?.firstOrNull {
                        it.isDirectory && it.name.equals(dir.name, ignoreCase = true)
                    }
                    if (childCi != null && (child.exists() || childCi.exists())) {
                        result.add(childCi.canonicalFile)
                    }
                }
            } catch (ignored: Exception) {
            }
        }
        return result.distinctBy { it.canonicalPath }
    }

    // --- Safety Truncation ---

    fun truncateOutput(text: String, maxBytes: Int = 3500, maxLines: Int = 35): String {
        // resolved_path — первая строка результата; при обрезке не должна потеряться,
        // иначе UI не покажет путь, а модель потеряет абсолютный путь.
        val resolvedLines = text.lines().filter { it.startsWith("resolved_path") }
        fun withPaths(body: String): String {
            if (resolvedLines.isEmpty()) return body
            val missing = resolvedLines.filter { !body.contains(it) }
            return if (missing.isEmpty()) body else (missing + body).joinToString("\n")
        }

        val bytes = text.toByteArray(Charsets.UTF_8)
        if (bytes.size <= maxBytes) {
            val lines = text.lines()
            if (lines.size <= maxLines) {
                return text
            }
            val truncatedLines = lines.take(maxLines).joinToString("\n")
            return withPaths("$truncatedLines\n\n[Вывод сокращен: показано $maxLines из ${lines.size} строк. Для детального просмотра уточни запрос]")
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

        return withPaths("$cleanCut\n\n[Вывод сокращен: размер превысил лимит ${formatFileSize(maxBytes.toLong())}. Для детального просмотра уточни запрос]")
    }

    // --- HITL Metadata ---

    data class CriticalActionInfo(
        val title: String,
        val details: String,
        val warning: String,
        /** Факты о цели операции: размер, количество файлов. Может быть пустым. */
        val preview: String = ""
    )

    /** Операции, которые меняют данные на устройстве. Всегда требуют подтверждения. */
    fun isCriticalOperation(toolName: String): Boolean {
        val cleanToolName = toolName.substringAfterLast(":")
        return cleanToolName in setOf(
            "delete_file",
            "move_file",
            "organize_downloads",
            "run_shell_command",
            "write_file"
        )
    }

    fun describeCriticalAction(toolName: String, argsJson: String): CriticalActionInfo {
        val cleanToolName = toolName.substringAfterLast(":")
        val args = try {
            if (argsJson.isBlank()) JSONObject() else JSONObject(argsJson)
        } catch (e: Exception) {
            JSONObject()
        }

        return when (cleanToolName) {
            "delete_file" -> {
                val path = args.optString("path", "").trim()
                val recursive = args.optBoolean("recursive", false)
                CriticalActionInfo(
                    title = "Удаление данных",
                    details = "Объект: $path${if (recursive) " (рекурсивно, включая вложенные файлы)" else ""}",
                    warning = "Удалённые файлы не попадают в корзину — восстановить не получится.",
                    preview = previewForPath(path)
                )
            }
            "write_file" -> {
                val path = args.optString("path", "").trim()
                val append = args.optBoolean("append", false)
                val contentLength = args.optString("content", "").length
                CriticalActionInfo(
                    title = if (append) "Дозапись в файл" else "Перезапись файла",
                    details = "Файл: $path",
                    warning = if (append) {
                        "Текст будет добавлен в конец существующего файла."
                    } else {
                        "Содержимое файла будет полностью перезаписано."
                    },
                    preview = buildString {
                        append("Объём записи: $contentLength символов")
                        val existing = existingFileInfo(path)
                        if (existing != null) append("\nФайл уже существует: $existing")
                    }
                )
            }
            "move_file" -> {
                val src = args.optString("source_path", "").trim()
                val dest = args.optString("destination_path", "").trim()
                CriticalActionInfo(
                    title = "Перемещение",
                    details = "Из: $src\nВ: $dest",
                    warning = "Исходный путь перестанет существовать.",
                    preview = previewForPath(src)
                )
            }
            "organize_downloads" -> {
                val category = args.optString("category", "all")
                CriticalActionInfo(
                    title = "Сортировка загрузок",
                    details = "Категория: $category (папка Download)",
                    warning = "Файлы будут перемещены в Documents/Runner, Backups/APKs или Pictures/Runner.",
                    preview = previewForDownloads(category)
                )
            }
            "run_shell_command" -> {
                val command = args.optString("command", "").trim()
                CriticalActionInfo(
                    title = "Выполнение shell-команды",
                    details = command,
                    warning = "Команда запускается в sh с правами приложения. Проверь её перед запуском.",
                    preview = "Длина команды: ${command.length} символов"
                )
            }
            else -> CriticalActionInfo(
                title = "Выполнение операции",
                details = "Инструмент: $cleanToolName",
                warning = "Операция может изменить локальные файлы."
            )
        }
    }

    /** Человеческое название действия в настоящем времени: «Сканирую Download». */
    fun actionTitle(toolName: String, argsJson: String): String {
        val cleanToolName = toolName.substringAfterLast(":")
        val args = try {
            if (argsJson.isBlank()) JSONObject() else JSONObject(argsJson)
        } catch (e: Exception) {
            JSONObject()
        }
        val shortName = { key: String ->
            args.optString(key, "").trim().trimEnd('/').substringAfterLast('/')
        }

        return when (cleanToolName) {
            "get_storage_summary" -> "Сканирую память устройства"
            "find_largest_files" -> "Ищу тяжёлые файлы"
            "find_junk_files" -> "Ищу мусор и временные файлы"
            "get_folder_summary" -> "Сканирую ${shortName("path").ifBlank { "Download" }}"
            "list_dir" -> "Открываю ${shortName("path").ifBlank { "Download" }}"
            "search_files" -> {
                val query = args.optString("query", "").trim()
                val extension = args.optString("extension", "").trim()
                when {
                    query.isNotBlank() -> "Ищу «$query»"
                    extension.isNotBlank() -> "Ищу *.$extension"
                    else -> "Ищу файлы"
                }
            }
            "read_file" -> "Читаю ${shortName("path").ifBlank { "файл" }}"
            "write_file" -> "Пишу ${shortName("path").ifBlank { "файл" }}"
            "delete_file" -> "Удаляю ${shortName("path").ifBlank { "объект" }}"
            "create_dir" -> "Создаю папку ${shortName("path").ifBlank { "" }}".trim()
            "move_file" -> "Перемещаю ${shortName("source_path").ifBlank { "объект" }}"
            "copy_file" -> "Копирую ${shortName("source_path").ifBlank { "объект" }}"
            "create_archive" -> "Упаковываю в ZIP"
            "extract_archive" -> "Распаковываю ${shortName("zip_path").ifBlank { "архив" }}"
            "organize_downloads" -> "Раскладываю Download по папкам"
            "clipboard_read" -> "Читаю буфер обмена"
            "clipboard_write" -> "Пишу в буфер обмена"
            "run_shell_command" -> "Выполняю команду"
            else -> cleanToolName
        }
    }

    /**
     * Короткая сводка результата для свёрнутой карточки: «143 файла», «свободно 24.3 GB».
     * Пустая строка, если ничего осмысленного вытащить не удалось.
     */
    fun summarizeResult(output: String): String {
        if (output.isBlank()) return ""
        val lines = output.lineSequence().take(10).toList()
        val flat = lines.joinToString(" ").take(600)

        // Нумерованный список: «1. Download/film.mkv — 2.4 GB»
        TOP_ENTRY.find(flat)?.let { return "макс. ${it.groupValues[1]}" }

        // «Всего файлов: 143», «Найдено совпадений: 7», «Пустых папок: 3»
        COUNT_AFTER_LABEL.find(flat)?.let { match ->
            val noun = match.groupValues[1].lowercase()
            val value = match.groupValues[2].toIntOrNull() ?: return@let
            val word = when {
                noun.startsWith("совпад") -> plural(value, "совпадение", "совпадения", "совпадений")
                noun.startsWith("подпап") || noun.startsWith("пап") ->
                    plural(value, "папка", "папки", "папок")
                noun.startsWith("элемент") -> plural(value, "элемент", "элемента", "элементов")
                noun.startsWith("запис") -> plural(value, "запись", "записи", "записей")
                noun.startsWith("модел") -> plural(value, "модель", "модели", "моделей")
                else -> plural(value, "файл", "файла", "файлов")
            }
            return "$value $word"
        }

        // «143 файла» — число перед словом
        COUNT_BEFORE_LABEL.find(flat)?.let { return "${it.groupValues[1]} ${it.groupValues[2]}" }

        // «• Свободно: 24.3 GB»
        FREE_SPACE.find(flat)?.let { return "свободно ${it.groupValues[1]}" }

        // Последний шанс — просто размер, но не из строки-заголовка
        val body = lines.drop(1).joinToString(" ").take(400)
        SIZE.find(body)?.let { return it.value }

        return ""
    }

    /** Русские падежи: 1 файл, 2 файла, 143 файла, 11 файлов. */
    private fun plural(count: Int, one: String, few: String, many: String): String {
        val mod100 = count % 100
        val mod10 = count % 10
        return when {
            mod100 in 11..14 -> many
            mod10 == 1 -> one
            mod10 in 2..4 -> few
            else -> many
        }
    }

    private fun previewForPath(rawPath: String): String = try {
        val file = resolvePath(rawPath)
        if (!file.exists()) {
            "Объект не найден: ${file.absolutePath}"
        } else if (file.isFile) {
            "Файл · ${formatFileSize(file.length())}\n${file.absolutePath}"
        } else {
            val stats = collectStats(file)
            buildString {
                append("Папка · ${stats.fileCount} файлов · ${formatFileSize(stats.totalBytes)}")
                if (stats.truncated) append(" (подсчёт ограничен)")
                append("\n${file.absolutePath}")
            }
        }
    } catch (e: Exception) {
        "Не удалось прочитать цель: ${e.localizedMessage}"
    }

    private fun existingFileInfo(rawPath: String): String? = try {
        val file = resolvePath(rawPath)
        if (file.isFile) formatFileSize(file.length()) else null
    } catch (e: Exception) {
        null
    }

    private fun previewForDownloads(category: String): String {
        return try {
            val downloads = resolveFolder("Download")
            if (!downloads.exists()) return "Папка Download не найдена"

            val extensions = when (category.lowercase()) {
                "documents" -> docExtensions
                "apks" -> apkExtensions
                "archives" -> archiveExtensions
                "images" -> imageExtensions
                else -> docExtensions + apkExtensions + archiveExtensions + imageExtensions
            }
            val files = downloads.listFiles()
                ?.filter { it.isFile && it.extension.lowercase() in extensions }
                .orEmpty()
            val bytes = files.sumOf { it.length() }
            "К перемещению: ${files.size} файлов · ${formatFileSize(bytes)}"
        } catch (e: Exception) {
            "Не удалось посчитать файлы: ${e.localizedMessage}"
        }
    }

    private data class PathStats(val fileCount: Int, val totalBytes: Long, val truncated: Boolean)

    /** Обход дерева с ограничением, чтобы превью не подвисало на гигантских папках. */
    private fun collectStats(root: File, maxEntries: Int = 4000): PathStats {
        var count = 0
        var bytes = 0L
        var truncated = false
        val stack = ArrayDeque<File>()
        stack.addLast(root)

        while (stack.isNotEmpty()) {
            val directory = stack.removeLast()
            val children = directory.listFiles() ?: continue
            for (child in children) {
                if (child.isDirectory) {
                    stack.addLast(child)
                } else {
                    count++
                    bytes += child.length()
                    if (count >= maxEntries) {
                        truncated = true
                        break
                    }
                }
            }
            if (truncated) break
        }
        return PathStats(count, bytes, truncated)
    }

    private val TOP_ENTRY = Regex(
        """\d+\.\s+\S.*?[—–-]\s*([\d.,]+\s?(?:TB|GB|MB|KB|ТБ|ГБ|МБ|КБ|B))"""
    )

    private val COUNT_AFTER_LABEL = Regex(
        """(подпапок|папок|папки|папка|совпадений|файлов|файла|файл|элементов|записей|моделей)\s*[:—-]?\s*(\d+)""",
        RegexOption.IGNORE_CASE
    )

    private val COUNT_BEFORE_LABEL = Regex(
        """(\d[\d\s\u00A0]*)\s*(файлов|файла|файл|папок|папки|записей|элементов|моделей|совпадений)""",
        RegexOption.IGNORE_CASE
    )

    private val FREE_SPACE = Regex(
        """Свободно\s*[:—-]?\s*([\d.,]+\s?(?:TB|GB|MB|KB|ТБ|ГБ|МБ|КБ|B))""",
        RegexOption.IGNORE_CASE
    )

    private val SIZE = Regex("""[\d.,]+\s?(?:TB|GB|MB|KB|ТБ|ГБ|МБ|КБ|B)""")
}
