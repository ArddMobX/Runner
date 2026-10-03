package com.runner.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Файл, вытащенный из вывода инструмента. */
data class FileEntry(
    val name: String,
    val folder: String?,
    val size: String?
)

/**
 * Размер бывает «2.4 GB», «228 KB» и «1 234,5 КБ» — formatFileSize использует
 * DecimalFormat, а он в русской локали ставит пробел между разрядами.
 */
private const val SIZE_PATTERN =
    """[\d.,\u00A0 ]*\d[\d.,\u00A0 ]*\s?(?:TB|GB|MB|KB|B|ТБ|ГБ|МБ|КБ)"""

/** «1. DCIM/Camera/lv_0_2026.mp4 — 2.4 GB» и вариант с обычным дефисом. */
private val NUMBERED_ENTRY = Regex("""^\d+\.\s+(.+?)\s+[—–-]\s+($SIZE_PATTERN)$""")

/** «• DCIM/Camera/lv_0_2026.mp4 (2.4 GB)» */
private val BULLETED_ENTRY = Regex("""^[•\-*]\s+(.+?)\s+\(($SIZE_PATTERN)\)$""")

/** Хвостовой размер у строки-пути, со скобками или без. */
private val TRAILING_SIZE = Regex("""\s*\(?($SIZE_PATTERN)\)?$""")

/** Строка, состоящая ТОЛЬКО из размера: модель часто выносит его на отдельную строку. */
private val LONE_SIZE = Regex("""^\(?($SIZE_PATTERN)\)?$""")

/**
 * Похоже ли это на путь к файлу, а не на строку статистики.
 * Отсекает «Временных файлов: 7», у которого тоже есть размер в скобках.
 */
private fun looksLikeFile(path: String): Boolean {
    if (path.isBlank() || path.contains('\n')) return false
    if (path.contains('/')) return true
    if (path.endsWith(":")) return false
    val extension = path.substringAfterLast('.', "")
    return extension.length in 1..5 && extension.all { it.isLetterOrDigit() }
}

/**
 * Разбирает строку вида «путь — размер» в файл.
 * Возвращает null, если строка не похожа на файл.
 */
fun parseFileEntry(rawLine: String): FileEntry? {
    val line = rawLine.trim()
    val match = NUMBERED_ENTRY.find(line) ?: BULLETED_ENTRY.find(line) ?: return null

    val path = match.groupValues[1].trim().trimEnd('/')
    val size = match.groupValues[2].trim()
    if (!looksLikeFile(path)) return null

    return FileEntry(
        name = path.substringAfterLast('/'),
        folder = path.substringBeforeLast('/', missingDelimiterValue = "")
            .trim('/')
            .takeIf { it.isNotBlank() },
        size = size
    )
}

/**
 * Строка — просто путь без разделителя с размером (например, resolved_path
 * из вывода тула). Отсекаем обычные фразы вроде «и/или что-то ещё».
 */
fun parseBarePath(rawLine: String): FileEntry? {
    var line = rawLine.trim()
        .removePrefix("resolved_path:")
        .trim()
        .trimEnd('/')
    if (line.length < 4 || line.endsWith(":") || line.contains(": ")) return null

    // Отделяем хвостовой размер: «/path/file.apk (12.4 MB)»
    var size: String? = null
    TRAILING_SIZE.find(line)?.let { match ->
        // range.first > 0 — иначе размер «съел» бы само имя файла
        if (match.range.first > 0) {
            size = match.groupValues[1].trim()
            line = line.substring(0, match.range.first).trim()
        }
    }

    val slashes = line.count { it == '/' }
    val lastSegment = line.substringAfterLast('/')
    val hasExtension = lastSegment.substringAfterLast('.', "").let { it.length in 1..5 }

    val looksLikePath = line.startsWith("/") ||
            line.startsWith("storage/") ||
            line.startsWith("sdcard/") ||
            slashes >= 2 ||
            (slashes == 1 && hasExtension)
    if (!looksLikePath) return null

    val name = line.substringAfterLast('/')
    if (name.isBlank() || name.length > 120) return null

    return FileEntry(
        name = name,
        folder = line.substringBeforeLast('/', "")
            .trim('/')
            .takeIf { it.isNotBlank() },
        size = size
    )
}

private sealed interface RawLine {
    data class File(val entry: FileEntry) : RawLine
    data class Text(val value: String) : RawLine
}

private sealed interface OutputLine {
    data class FolderCaption(val folder: String) : OutputLine
    data class File(val entry: FileEntry, val showFolder: Boolean) : OutputLine
    data class Plain(val value: String, val isHeading: Boolean) : OutputLine
}

/** Строка целиком состоит из размера — «228 KB» или «(2,4 MB)». */
private fun parseLoneSize(line: String): String? =
    LONE_SIZE.find(line)?.groupValues?.get(1)?.trim()

/**
 * Разбирает вывод в элементы и решает две проблемы разом:
 * 1. Размер, вынесенный на отдельную строку, приклеивается к файлу выше —
 *    иначе он висел сиротливой строкой под списком.
 * 2. Одинаковая папка у серии файлов выводится один раз заголовком, а не
 *    повторяется под каждым именем. Иначе список выглядел как набор
 *    одинаковых строк «/storage/emulated/0/Download/» с размерами снизу.
 */
private fun parseOutput(output: String): List<OutputLine> {
    val raw = mutableListOf<RawLine>()

    output.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .forEach { line ->
            val entry = parseFileEntry(line) ?: parseBarePath(line)
            if (entry != null) {
                raw += RawLine.File(entry)
                return@forEach
            }

            val loneSize = parseLoneSize(line)
            val previous = raw.lastOrNull()
            if (loneSize != null && previous is RawLine.File && previous.entry.size == null) {
                raw[raw.lastIndex] = RawLine.File(previous.entry.copy(size = loneSize))
                return@forEach
            }

            raw += RawLine.Text(line)
        }

    val result = mutableListOf<OutputLine>()
    var index = 0
    while (index < raw.size) {
        val item = raw[index]
        if (item is RawLine.Text) {
            result += OutputLine.Plain(item.value, item.value.endsWith(":"))
            index++
            continue
        }

        val file = item as RawLine.File
        val folder = file.entry.folder

        var runEnd = index + 1
        if (folder != null) {
            while (runEnd < raw.size) {
                val candidate = raw[runEnd]
                if (candidate is RawLine.File && candidate.entry.folder == folder) runEnd++ else break
            }
        }

        val runLength = runEnd - index
        if (folder != null && runLength > 1) {
            result += OutputLine.FolderCaption(folder)
            for (i in index until runEnd) {
                result += OutputLine.File((raw[i] as RawLine.File).entry, showFolder = false)
            }
        } else {
            for (i in index until runEnd) {
                result += OutputLine.File((raw[i] as RawLine.File).entry, showFolder = true)
            }
        }
        index = runEnd
    }
    return result
}

/**
 * Похож ли текст на список файлов. Нужно MarkdownView: модель часто заворачивает
 * перечисление путей в блок кода, а блок кода прокручивается по горизонтали —
 * из-за этого хвост с именем файла уезжал за правый край экрана.
 */
fun looksLikeFileList(text: String): Boolean {
    val lines = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
    if (lines.size < 2) return false
    val hits = lines.count { parseFileEntry(it) != null || parseBarePath(it) != null }
    return hits >= 2 && hits * 2 >= lines.size
}

/**
 * Вывод инструмента в читаемом виде: список файлов превращается в компактные
 * строки «имя (размер)», общая папка выводится один раз заголовком.
 */
@Composable
fun ToolOutputView(
    output: String,
    modifier: Modifier = Modifier
) {
    val lines = remember(output) { parseOutput(output) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        lines.forEach { line ->
            when (line) {
                is OutputLine.FolderCaption -> Text(
                    text = line.folder,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.5.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp)
                )

                is OutputLine.File -> FileRow(line.entry, line.showFolder)

                is OutputLine.Plain -> Text(
                    text = line.value,
                    color = if (line.isHeading) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    fontSize = if (line.isHeading) 11.sp else 12.5.sp,
                    fontWeight = if (line.isHeading) FontWeight.Medium else FontWeight.Normal,
                    lineHeight = 17.sp
                )
            }
        }
    }
}

/**
 * Имя файла крупным текстом, размер рядом бледнее, путь — мелким подстрочником
 * и только если папка не показана общим заголовком.
 */
@Composable
private fun FileRow(entry: FileEntry, showFolder: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp)
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = entry.name,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 19.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            entry.size?.let { size ->
                Text(
                    text = "  $size",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.5.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1
                )
            }
        }

        if (showFolder) {
            entry.folder?.let { folder ->
                Text(
                    text = folder,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    fontSize = 10.5.sp,
                    lineHeight = 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
