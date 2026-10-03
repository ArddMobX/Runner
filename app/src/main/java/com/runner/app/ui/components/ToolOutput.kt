package com.runner.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.MaterialTheme

/** Файл, вытащенный из вывода инструмента. */
data class FileEntry(
    val name: String,
    val folder: String?,
    val size: String?
)

private const val SIZE_PATTERN = """[\d.,]+\s?(?:TB|GB|MB|KB|B|ТБ|ГБ|МБ|КБ)"""

/** «1. DCIM/Camera/lv_0_2026.mp4 — 2.4 GB» */
private val NUMBERED_ENTRY = Regex("""^\d+\.\s+(.+?)\s+[—–-]\s+($SIZE_PATTERN)$""")

/** «• DCIM/Camera/lv_0_2026.mp4 (2.4 GB)» */
private val BULLETED_ENTRY = Regex("""^[•\-*]\s+(.+?)\s+\(($SIZE_PATTERN)\)$""")

/** Хвостовой размер в скобках у строки-пути. */
private val TRAILING_SIZE = Regex("""\s*\(($SIZE_PATTERN)\)$""")

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

    val name = path.substringAfterLast('/')
    val folder = path.substringBeforeLast('/', missingDelimiterValue = "")
        .trim('/')
        .takeIf { it.isNotBlank() }

    return FileEntry(name = name, folder = folder, size = size)
}

/**
 * Строка — просто путь без разделителя с размером (например, resolved_path
 * из вывода тула). Отсекаем обычные фразы вроде «и/или что-то ещё».
 */
private fun parseBarePath(rawLine: String): FileEntry? {
    var line = rawLine.trim()
        .removePrefix("resolved_path:")
        .trim()
        .trimEnd('/')
    if (line.length < 4 || line.endsWith(":") || line.contains(": ")) return null

    // Отделяем хвостовой размер: «/path/file.apk (12.4 MB)»
    var size: String? = null
    TRAILING_SIZE.find(line)?.let { match ->
        size = match.groupValues[1].trim()
        line = line.substring(0, match.range.first).trim()
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

    val folder = line.substringBeforeLast('/', "")
        .trim('/')
        .takeIf { it.isNotBlank() }

    return FileEntry(name = name, folder = folder, size = size)
}

private sealed interface OutputLine {
    data class File(val entry: FileEntry) : OutputLine
    data class Plain(val value: String, val isHeading: Boolean) : OutputLine
}

private fun parseOutput(output: String): List<OutputLine> =
    output.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { line ->
            val entry = parseFileEntry(line) ?: parseBarePath(line)
            if (entry != null) {
                OutputLine.File(entry)
            } else {
                OutputLine.Plain(line, line.endsWith(":"))
            }
        }
        .toList()

/**
 * Вывод инструмента в читаемом виде: списки файлов превращаются в строки
 * «имя (размер)» с путём мелким шрифтом, остальной текст идёт обычным шрифтом.
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
                is OutputLine.File -> FileRow(line.entry)

                is OutputLine.Plain -> Text(
                    text = line.value,
                    color = if (line.isHeading) {
                        MaterialTheme.colorScheme.outline
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
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
 * Имя файла обычным текстом с размером в скобках рядом, путь — мелким ниже.
 * Раньше размер висел справа по центру двух строк и читался как отдельная
 * третья строка, а имя терялось за длинным путём.
 */
@Composable
private fun FileRow(entry: FileEntry) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp)
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = entry.name,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 18.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            entry.size?.let { size ->
                Text(
                    text = " ($size)",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.5.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1
                )
            }
        }

        entry.folder?.let { folder ->
            Text(
                text = folder,
                color = MaterialTheme.colorScheme.outline,
                fontSize = 10.5.sp,
                lineHeight = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
