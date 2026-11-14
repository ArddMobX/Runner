package com.runner.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Общая визуальная метка файла: цвет и короткая подпись зависят от
 * расширения. Одинаково используется в поле ввода и в истории чата.
 */
@Composable
fun AttachmentBadge(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 34.dp
) {
    val extension = name.substringAfterLast('.', "").lowercase()
    val (background, content) = attachmentColors(extension)
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size / 3))
            .background(background),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = attachmentShortLabel(extension),
            color = content,
            fontSize = if (size <= 32.dp) 8.sp else 9.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

@Composable
fun attachmentColors(extension: String): Pair<Color, Color> = when (extension.lowercase()) {
    "pdf" -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer

    "docx", "odt", "doc" ->
        MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer

    "xlsx", "csv", "tsv" ->
        MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer

    "pptx" ->
        MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer

    else ->
        MaterialTheme.colorScheme.surfaceContainerHighest to MaterialTheme.colorScheme.onSurfaceVariant
}

fun attachmentShortLabel(extension: String): String = when (extension.lowercase()) {
    "pdf" -> "PDF"
    "docx", "doc" -> "DOCX"
    "odt" -> "ODT"
    "xlsx" -> "XLSX"
    "csv" -> "CSV"
    "pptx" -> "PPTX"
    "json" -> "JSON"
    "html", "htm" -> "HTML"
    "md", "markdown" -> "MD"
    "kotlin", "kt", "kts" -> "KT"
    "python", "py" -> "PY"
    "javascript", "js", "jsx" -> "JS"
    "typescript", "ts", "tsx" -> "TS"
    "plain" -> "TXT"
    "" -> "FILE"
    else -> extension.uppercase().take(4)
}

/** Человеческое название типа для строки под именем файла. */
fun attachmentTypeName(extension: String): String = when (extension.lowercase()) {
    "pdf" -> "PDF-документ"
    "docx", "doc" -> "Word"
    "odt" -> "OpenDocument"
    "xlsx" -> "Excel"
    "csv", "tsv" -> "Таблица"
    "pptx" -> "PowerPoint"
    "json" -> "JSON"
    "md", "markdown" -> "Markdown"
    "html", "htm" -> "HTML"
    "kt", "kts" -> "Kotlin"
    "java" -> "Java"
    "py" -> "Python"
    "js", "jsx" -> "JavaScript"
    "ts", "tsx" -> "TypeScript"
    "" -> "Файл"
    else -> extension.uppercase()
}
