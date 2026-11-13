package com.runner.app.tools

import com.runner.app.data.FileChange
import com.runner.app.data.FileChangeKind
import org.json.JSONObject
import java.io.File

/**
 * Считает, что именно изменила файловая операция агента.
 *
 * Для write_file старый текст читается ДО записи: иначе diff не с чем строить.
 * Для удаления и перемещения точные числа берутся из записи корзины, которая
 * создаётся самим инструментом. Всё это только для карточки; на безопасность
 * и на выполнение операции трекер не влияет.
 */
object FileChangeTracker {

    private const val MAX_DIFF_BYTES = 200_000L
    private const val MAX_DIFF_LINES = 400

    private val TRACKED_TOOLS = setOf(
        "write_file",
        "delete_file",
        "move_file",
        "copy_file",
        "create_dir",
        "create_archive",
        "organize_downloads"
    )

    class Probe internal constructor(
        val tool: String,
        val path: String,
        val existed: Boolean,
        val oldText: String?,
        val oldBytes: Long?,
        val newText: String?,
        val append: Boolean
    )

    fun prepare(toolName: String, argsJson: String): Probe? {
        val clean = toolName.substringAfterLast(':')
        if (clean !in TRACKED_TOOLS) return null
        val args = try {
            if (argsJson.isBlank()) JSONObject() else JSONObject(argsJson)
        } catch (_: Exception) {
            JSONObject()
        }
        return try {
            when (clean) {
                "write_file" -> prepareWrite(args)
                "delete_file" -> Probe(
                    tool = clean,
                    path = args.optString("path").trim(),
                    existed = true,
                    oldText = null,
                    oldBytes = statSize(args.optString("path")),
                    newText = null,
                    append = false
                )

                "move_file", "copy_file" -> {
                    val src = args.optString("source_path").trim()
                    val dest = args.optString("destination_path").trim()
                    val target = if (clean == "move_file") src else dest
                    Probe(
                        tool = clean,
                        path = resolveAbsolute(target),
                        existed = clean == "copy_file" && File(resolveAbsolute(dest)).exists(),
                        oldText = null,
                        oldBytes = statSize(src),
                        newText = null,
                        append = false
                    )
                }

                "create_dir" -> Probe(
                    tool = clean,
                    path = resolveAbsolute(args.optString("path").trim()),
                    existed = false,
                    oldText = null,
                    oldBytes = null,
                    newText = null,
                    append = false
                )

                "create_archive" -> {
                    val archivePath = resolveAbsolute(args.optString("zip_path").trim())
                    Probe(
                        tool = clean,
                        path = archivePath,
                        existed = File(archivePath).exists(),
                        oldText = null,
                        oldBytes = statSize(archivePath),
                        newText = null,
                        append = false
                    )
                }

                "organize_downloads" -> Probe(
                    tool = clean,
                    path = "Download",
                    existed = false,
                    oldText = null,
                    oldBytes = null,
                    newText = null,
                    append = false
                )

                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun finish(probe: Probe?, output: String): FileChange? {
        if (probe == null) return null
        return try {
            when (probe.tool) {
                "write_file" -> finishWrite(probe)
                "delete_file" -> {
                    val entry = entryFromOutput(output)
                    FileChange(
                        kind = FileChangeKind.DELETED,
                        path = probe.path,
                        itemCount = entry?.fileCount ?: 1,
                        bytes = entry?.totalBytes ?: probe.oldBytes
                    )
                }

                "move_file" -> {
                    val entry = entryFromOutput(output)
                    FileChange(
                        kind = FileChangeKind.MOVED,
                        path = probe.path,
                        itemCount = entry?.fileCount ?: 1,
                        bytes = probe.oldBytes
                    )
                }

                "copy_file" -> FileChange(
                    kind = FileChangeKind.COPIED,
                    path = probe.path,
                    itemCount = 1,
                    bytes = statSize(probe.path)
                )

                "create_dir" -> FileChange(
                    kind = FileChangeKind.DIR_CREATED,
                    path = probe.path,
                    itemCount = 1
                )

                "create_archive" -> FileChange(
                    kind = if (probe.existed) FileChangeKind.MODIFIED else FileChangeKind.CREATED,
                    path = probe.path,
                    itemCount = 1,
                    bytes = statSize(probe.path)
                )

                "organize_downloads" -> {
                    val entry = entryFromOutput(output)
                    if (entry != null && entry.fileCount > 0) {
                        FileChange(
                            kind = FileChangeKind.MOVED,
                            path = probe.path,
                            itemCount = entry.fileCount
                        )
                    } else {
                        null
                    }
                }

                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun prepareWrite(args: JSONObject): Probe {
        val rawPath = args.optString("path").trim()
        val file = try {
            ToolDispatcher.resolvePath(rawPath)
        } catch (_: Exception) {
            File(rawPath)
        }
        val exists = file.exists() && file.isFile
        val oldBytes = if (exists) file.length() else null
        val oldText = if (exists && file.length() in 1..MAX_DIFF_BYTES) {
            try {
                file.readText()
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }
        return Probe(
            tool = "write_file",
            path = file.absolutePath,
            existed = exists,
            oldText = oldText,
            oldBytes = oldBytes,
            newText = args.optString("content", ""),
            append = args.optBoolean("append", false)
        )
    }

    private fun finishWrite(probe: Probe): FileChange? {
        val newText = probe.newText ?: return null
        val newBytes = newText.toByteArray(Charsets.UTF_8).size.toLong()

        if (!probe.existed) {
            val lines = newText.lines().size
            return FileChange(
                kind = FileChangeKind.CREATED,
                path = probe.path,
                itemCount = 1,
                bytes = newBytes,
                linesAdded = lines,
                linesRemoved = 0,
                diff = if (newText.isNotBlank()) {
                    LineDiff.compare("", newText).preview
                } else {
                    null
                }
            )
        }

        if (probe.oldText != null) {
            val before = probe.oldText
            val after = if (probe.append) probe.oldText + newText else newText
            val diff = LineDiff.compare(before, after)
            return FileChange(
                kind = FileChangeKind.MODIFIED,
                path = probe.path,
                itemCount = 1,
                bytes = newBytes,
                linesAdded = diff.added,
                linesRemoved = diff.removed,
                diff = diff.preview
            )
        }

        // Старый файл слишком большой или не прочитался: честно показываем
        // только объём записи, без выдуманных +/−.
        return FileChange(
            kind = FileChangeKind.MODIFIED,
            path = probe.path,
            itemCount = 1,
            bytes = newBytes,
            linesAdded = newText.lines().size,
            linesRemoved = 0
        )
    }

    private fun resolveAbsolute(raw: String): String = try {
        ToolDispatcher.resolvePath(raw).absolutePath
    } catch (_: Exception) {
        raw
    }

    private fun statSize(raw: String): Long? = try {
        val file = ToolDispatcher.resolvePath(raw)
        if (file.isFile) file.length() else null
    } catch (_: Exception) {
        null
    }

    private fun entryFromOutput(output: String): TrashStore.TrashEntry? {
        val ref = TrashStore.undoRefFromOutput(output) ?: return null
        return TrashStore.list().firstOrNull { it.id == ref.entryId }
    }

    /** Построчный diff с ограничением размера: для карточки и шторки. */
    internal object LineDiff {

        data class Result(val added: Int, val removed: Int, val preview: String?)

        fun compare(oldText: String, newText: String): Result {
            val oldLines = if (oldText.isEmpty()) {
                emptyList()
            } else {
                oldText.lines().take(MAX_DIFF_LINES)
            }
            val newLines = if (newText.isEmpty()) {
                emptyList()
            } else {
                newText.lines().take(MAX_DIFF_LINES)
            }
            val n = oldLines.size
            val m = newLines.size
            if (n == 0 && m == 0) return Result(0, 0, null)

            val dp = Array(n + 1) { IntArray(m + 1) }
            for (i in n - 1 downTo 0) {
                for (j in m - 1 downTo 0) {
                    dp[i][j] = if (oldLines[i] == newLines[j]) {
                        dp[i + 1][j + 1] + 1
                    } else {
                        maxOf(dp[i + 1][j], dp[i][j + 1])
                    }
                }
            }

            val ops = ArrayList<Pair<Char, String>>(n + m)
            var i = 0
            var j = 0
            while (i < n && j < m) {
                when {
                    oldLines[i] == newLines[j] -> {
                        ops.add(' ' to oldLines[i])
                        i++
                        j++
                    }

                    dp[i + 1][j] >= dp[i][j + 1] -> {
                        ops.add('-' to oldLines[i])
                        i++
                    }

                    else -> {
                        ops.add('+' to newLines[j])
                        j++
                    }
                }
            }
            while (i < n) {
                ops.add('-' to oldLines[i])
                i++
            }
            while (j < m) {
                ops.add('+' to newLines[j])
                j++
            }

            val added = ops.count { it.first == '+' }
            val removed = ops.count { it.first == '-' }
            if (added == 0 && removed == 0) return Result(0, 0, null)

            val changed = ops.indices.filter { ops[it].first != ' ' }
            val keep = BooleanArray(ops.size)
            changed.forEach { index ->
                for (k in (index - 2).coerceAtLeast(0)..(index + 2).coerceAtMost(ops.lastIndex)) {
                    keep[k] = true
                }
            }
            val previewLines = ArrayList<String>()
            var skipped = false
            for (index in ops.indices) {
                if (keep[index]) {
                    if (skipped) previewLines.add("…")
                    skipped = false
                    val (prefix, line) = ops[index]
                    previewLines.add("$prefix $line")
                } else {
                    skipped = true
                }
            }
            val preview = buildString {
                append(previewLines.take(120).joinToString("\n"))
                if (previewLines.size > 120) append("\n…")
                if (n == MAX_DIFF_LINES || m == MAX_DIFF_LINES) {
                    append("\n(сравнение ограничено первыми ")
                    append(MAX_DIFF_LINES)
                    append(" строками)")
                }
            }
            return Result(added, removed, preview)
        }
    }
}
