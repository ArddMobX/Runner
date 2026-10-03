package com.runner.app.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.webkit.WebView
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Functions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.runner.app.ui.theme.StatusSuccess
import com.runner.app.ui.theme.bounceClick

sealed class MarkdownElement {
    data class Header(val level: Int, val content: String) : MarkdownElement()
    data class CodeBlock(val language: String, val code: String) : MarkdownElement()
    data class MathBlock(val latex: String) : MarkdownElement()
    data class ListBlock(val items: List<MarkdownElement>) : MarkdownElement()
    data class BulletItem(val text: String) : MarkdownElement()
    data class NumberedItem(val number: String, val text: String) : MarkdownElement()
    data class Blockquote(val text: String) : MarkdownElement()
    data class Paragraph(val text: String) : MarkdownElement()
}

@Composable
fun MarkdownView(
    text: String,
    modifier: Modifier = Modifier
) {
    val elements = remember(text) { parseMarkdown(text) }

    SelectionContainer {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            elements.forEach { element ->
                when (element) {
                    is MarkdownElement.Header -> HeaderElement(element)
                    is MarkdownElement.CodeBlock -> CodeBlockElement(element)
                    is MarkdownElement.MathBlock -> MathBlockElement(element)
                    is MarkdownElement.ListBlock -> ListBlockElement(element)
                    is MarkdownElement.BulletItem -> BulletElement(element)
                    is MarkdownElement.NumberedItem -> NumberedElement(element)
                    is MarkdownElement.Blockquote -> BlockquoteElement(element)
                    is MarkdownElement.Paragraph -> ParagraphElement(element)
                }
            }
        }
    }
}

@Composable
private fun HeaderElement(element: MarkdownElement.Header) {
    // Шкала сжата: заголовки не должны перекрикивать основной текст (15sp).
    val (style, topPadding) = when (element.level) {
        1 -> MaterialTheme.typography.titleLarge.copy(
            fontWeight = FontWeight.SemiBold,
            fontSize = 17.sp,
            lineHeight = 23.sp
        ) to 8.dp

        2 -> MaterialTheme.typography.titleMedium.copy(
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            lineHeight = 22.sp
        ) to 6.dp

        else -> MaterialTheme.typography.titleMedium.copy(
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            lineHeight = 21.sp
        ) to 5.dp
    }
    Text(
        text = renderInlineMarkdown(
            element.content,
            primaryColor = MaterialTheme.colorScheme.primary,
            secondaryColor = MaterialTheme.colorScheme.secondary
        ),
        style = style,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = topPadding)
    )
}

@Composable
private fun CodeBlockElement(element: MarkdownElement.CodeBlock) {
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            // Header bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.5f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = element.language.ifBlank { "code" }.lowercase(),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp))
                ) {
                    IconButton(
                        onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("Code", element.code))
                            copied = true
                            Toast.makeText(context, "Код скопирован", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .size(26.dp)
                            .bounceClick {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("Code", element.code))
                                copied = true
                                Toast.makeText(context, "Код скопирован", Toast.LENGTH_SHORT).show()
                            }
                    ) {
                        Icon(
                            imageVector = if (copied) Icons.Outlined.Check else Icons.Outlined.ContentCopy,
                            contentDescription = "Копировать",
                            tint = if (copied) StatusSuccess else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }

            // Список файлов рендерим строками, а не блоком кода с горизонтальной
            // прокруткой: в прокрутке длинный путь обрезается по правому краю,
            // и в списке остаются одинаковые строки без имён файлов.
            val isFileList = remember(element.code) { looksLikeFileList(element.code) }

            if (isFileList) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    ToolOutputView(output = element.code)
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(12.dp)
                ) {
                    Text(
                        text = element.code,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun MathBlockElement(element: MarkdownElement.MathBlock) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Functions,
                    contentDescription = "LaTeX Formula",
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "LaTeX Formula",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // KaTeX WebView Renderer
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 40.dp, max = 200.dp),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        setBackgroundColor(0) // Transparent
                        isVerticalScrollBarEnabled = false
                        isHorizontalScrollBarEnabled = false
                    }
                },
                update = { webView ->
                    val cleanLatex = element.latex
                        .replace("\\", "\\\\")
                        .replace("\"", "\\\"")
                        .replace("\n", " ")

                    val html = """
                        <!DOCTYPE html>
                        <html>
                        <head>
                        <meta name="viewport" content="width=device-width, initial-scale=1.0">
                        <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/katex@0.16.9/dist/katex.min.css">
                        <script src="https://cdn.jsdelivr.net/npm/katex@0.16.9/dist/katex.min.js"></script>
                        <style>
                          body {
                            background-color: transparent;
                            color: #EDEDEF;
                            margin: 0;
                            padding: 6px;
                            display: flex;
                            justify-content: center;
                            font-size: 16px;
                          }
                          .katex { color: #EDEDEF; }
                        </style>
                        </head>
                        <body>
                        <div id="math"></div>
                        <script>
                          try {
                            katex.render("$cleanLatex", document.getElementById("math"), {
                              displayMode: true,
                              throwOnError: false
                            });
                          } catch (e) {
                            document.getElementById("math").innerText = "$cleanLatex";
                          }
                        </script>
                        </body>
                        </html>
                    """.trimIndent()

                    webView.loadDataWithBaseURL("https://cdn.jsdelivr.net", html, "text/html", "UTF-8", null)
                }
            )
        }
    }
}

@Composable
private fun ListBlockElement(element: MarkdownElement.ListBlock) {
    Column(
        verticalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        element.items.forEach { item ->
            when (item) {
                is MarkdownElement.BulletItem -> BulletElement(item)
                is MarkdownElement.NumberedItem -> NumberedElement(item)
                else -> {}
            }
        }
    }
}

@Composable
private fun BulletElement(element: MarkdownElement.BulletItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, top = 1.dp, bottom = 1.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Увеличенный маркер с выравниванием по строке текста
        Text(
            text = "•",
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            lineHeight = 22.sp,
            modifier = Modifier.padding(end = 8.dp)
        )
        MarkdownText(
            text = element.text,
            style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 22.sp)
        )
    }
}

@Composable
private fun NumberedElement(element: MarkdownElement.NumberedItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, top = 1.dp, bottom = 1.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "${element.number}.",
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            lineHeight = 22.sp,
            modifier = Modifier
                .width(22.dp)
                .padding(end = 4.dp)
        )
        MarkdownText(
            text = element.text,
            style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 22.sp)
        )
    }
}

@Composable
private fun BlockquoteElement(element: MarkdownElement.Blockquote) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
            .border(
                BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
                RoundedCornerShape(4.dp)
            )
            .padding(10.dp)
    ) {
        Text(
            text = renderInlineMarkdown(
                element.text,
                primaryColor = MaterialTheme.colorScheme.primary,
                secondaryColor = MaterialTheme.colorScheme.secondary
            ),
            fontStyle = FontStyle.Italic,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private sealed interface InlineSegment {
    data class Plain(val value: String) : InlineSegment
    data class Code(val value: String) : InlineSegment
}

private val INLINE_CODE = Regex("`([^`]+)`")

private fun splitInline(text: String): List<InlineSegment> {
    val segments = mutableListOf<InlineSegment>()
    var cursor = 0
    INLINE_CODE.findAll(text).forEach { match ->
        if (match.range.first > cursor) {
            segments.add(InlineSegment.Plain(text.substring(cursor, match.range.first)))
        }
        segments.add(InlineSegment.Code(match.groupValues[1]))
        cursor = match.range.last + 1
    }
    if (cursor < text.length) {
        segments.add(InlineSegment.Plain(text.substring(cursor)))
    }
    return segments
}

/**
 * Чипсы уместны только в коротких строках-перечислениях вида
 * «`Download` `DCIM` `Pictures`». В обычном абзаце с одним словом в коде
 * разбивать поток на блоки нельзя — там остаётся цельный текст.
 */
private fun shouldUseChips(segments: List<InlineSegment>): Boolean {
    if (segments.none { it is InlineSegment.Code }) return false
    if (segments.size > 9) return false
    return segments.filterIsInstance<InlineSegment.Plain>()
        .all { it.value.trim().length <= 28 }
}

/**
 * Текст с инлайн-кодом. Если код есть и строка короткая — код рендерится
 * скруглёнными чипсами, иначе всё идёт единым потоком с акцентной подсветкой.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MarkdownText(
    text: String,
    style: TextStyle,
    color: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val segments = remember(text) { splitInline(text) }

    if (!shouldUseChips(segments)) {
        Text(
            text = renderInlineMarkdown(text, primaryColor, secondaryColor),
            style = style,
            color = color,
            modifier = modifier
        )
        return
    }

    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        segments.forEach { segment ->
            when (segment) {
                is InlineSegment.Plain -> {
                    val chunk = segment.value.trim()
                    if (chunk.isNotEmpty()) {
                        Text(text = chunk, style = style, color = color)
                    }
                }

                is InlineSegment.Code -> InlineChip(text = segment.value)
            }
        }
    }
}

/** Скруглённый чип для слова в бэктиках. */
@Composable
private fun InlineChip(text: String) {
    val shape = RoundedCornerShape(7.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            // Нейтральная подложка вместо тонированной акцентом: при тёплом seed-цвете
            // акцентная заливка давала коричневую плашку с бледным текстом, и пути
            // читались плохо. Контраст здесь важнее цветности.
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .border(
                BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                shape
            )
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 13.5.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 1
        )
    }
}

@Composable
private fun ParagraphElement(element: MarkdownElement.Paragraph) {
    MarkdownText(
        text = element.text,
        style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 22.sp)
    )
}

/**
 * Sanitizes LaTeX numbers, units, and escapes from LLM responses:
 * - \text{X} -> X
 * - \% -> %
 * - Strips $...$ around numbers, units, and percentages (e.g. $51.6 GB$ -> 51.6 GB, $89%$ -> 89%)
 */
fun sanitizeMarkdown(text: String): String {
    if (text.isEmpty() || (!text.contains("\\text") && !text.contains("\\%") && !text.contains("$"))) {
        return text
    }

    val parts = text.split("```")
    if (parts.size > 1) {
        return parts.mapIndexed { index, part ->
            if (index % 2 == 1) {
                // Inside code block: preserve exactly
                part
            } else {
                sanitizeTextChunk(part)
            }
        }.joinToString("```")
    }

    return sanitizeTextChunk(text)
}

private fun sanitizeTextChunk(chunk: String): String {
    // 1. \text{X} -> X
    var result = Regex("""\\text\{([^}]*)\}""").replace(chunk) { it.groupValues[1] }
    // 2. \% -> %
    result = result.replace("""\%""", "%")
    // 3. Remove $...$ around numbers and units (e.g. $51.6 GB$, $89%$, $100$)
    result = Regex("""(?<!\$)\$\s*([~≈±+\-–—]?\s*\d[\d.,\s]*(?:[a-zA-Zа-яА-ЯёЁ%°²³/-]+(?:\s*[a-zA-Zа-яА-ЯёЁ%°²³/-]+)*)?)\s*\$(?!\$)""").replace(result) {
        it.groupValues[1].trim()
    }
    // Also remove $...$ around standalone percentage e.g. $%$
    result = Regex("""(?<!\$)\$\s*([~≈±+\-–—]?\s*%)\s*\$(?!\$)""").replace(result) {
        it.groupValues[1].trim()
    }
    return result
}

/**
 * Parses raw text into high-level Markdown elements.
 */
fun parseMarkdown(rawText: String): List<MarkdownElement> {
    val sanitized = sanitizeMarkdown(rawText)
    val lines = sanitized.lines()
    val elements = mutableListOf<MarkdownElement>()
    var i = 0

    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trim()

        // 1. Code Block (```lang)
        if (trimmed.startsWith("```")) {
            val language = trimmed.removePrefix("```").trim()
            val codeLines = mutableListOf<String>()
            i++
            while (i < lines.size && !lines[i].trim().startsWith("```")) {
                codeLines.add(lines[i])
                i++
            }
            if (i < lines.size && lines[i].trim().startsWith("```")) {
                i++ // consume closing ```
            }
            elements.add(MarkdownElement.CodeBlock(language, codeLines.joinToString("\n")))
            continue
        }

        // 2. Block LaTeX Formula ($$ ... $$ or \[ ... \])
        if (trimmed.startsWith("$$") || trimmed.startsWith("\\[")) {
            val isDoubleDollar = trimmed.startsWith("$$")
            val endDelimiter = if (isDoubleDollar) "$$" else "\\]"
            val contentStart = if (isDoubleDollar) trimmed.removePrefix("$$") else trimmed.removePrefix("\\[")

            if (contentStart.contains(endDelimiter)) {
                val formula = contentStart.substringBefore(endDelimiter).trim()
                elements.add(MarkdownElement.MathBlock(formula))
                i++
                continue
            } else {
                val mathLines = mutableListOf<String>()
                if (contentStart.isNotBlank()) mathLines.add(contentStart)
                i++
                while (i < lines.size && !lines[i].contains(endDelimiter)) {
                    mathLines.add(lines[i])
                    i++
                }
                if (i < lines.size) {
                    val lastLine = lines[i].substringBefore(endDelimiter).trim()
                    if (lastLine.isNotBlank()) mathLines.add(lastLine)
                    i++
                }
                elements.add(MarkdownElement.MathBlock(mathLines.joinToString(" ").trim()))
                continue
            }
        }

        // 3. Headers (# H1, ## H2, ### H3)
        if (trimmed.startsWith("#")) {
            val level = trimmed.takeWhile { it == '#' }.length.coerceIn(1, 4)
            val text = trimmed.drop(level).trim()
            elements.add(MarkdownElement.Header(level, text))
            i++
            continue
        }

        // 4. Blockquotes (> quote)
        if (trimmed.startsWith(">")) {
            val quoteText = trimmed.removePrefix(">").trim()
            elements.add(MarkdownElement.Blockquote(quoteText))
            i++
            continue
        }

        // 5. Lists (Bullet Lists: * item, - item; Numbered Lists: 1. item)
        val isBullet = trimmed.startsWith("* ") || trimmed.startsWith("- ")
        val isNumbered = Regex("^([0-9]+)\\.\\s+(.*)").matches(trimmed)
        if (isBullet || isNumbered) {
            val listItems = mutableListOf<MarkdownElement>()
            while (i < lines.size) {
                val currentTrimmed = lines[i].trim()
                if (currentTrimmed.startsWith("* ") || currentTrimmed.startsWith("- ")) {
                    val itemText = currentTrimmed.drop(2).trim()
                    listItems.add(MarkdownElement.BulletItem(itemText))
                    i++
                } else {
                    val numMatch = Regex("^([0-9]+)\\.\\s+(.*)").find(currentTrimmed)
                    if (numMatch != null) {
                        val number = numMatch.groupValues[1]
                        val itemText = numMatch.groupValues[2]
                        listItems.add(MarkdownElement.NumberedItem(number, itemText))
                        i++
                    } else {
                        break
                    }
                }
            }
            elements.add(MarkdownElement.ListBlock(listItems))
            continue
        }

        // 6. Regular paragraph / empty line
        if (trimmed.isNotEmpty()) {
            elements.add(MarkdownElement.Paragraph(line))
        }

        i++
    }

    return elements
}

/**
 * Formats inline Markdown: bold (**text**), italic (*text*), code (`code`), strikethrough (~~text~~),
 * and inline math ($x = y$).
 */
fun renderInlineMarkdown(
    text: String,
    primaryColor: Color = Color(0xFF90CAF9),
    secondaryColor: Color = Color(0xFF80CBC4)
): AnnotatedString {
    val sanitized = sanitizeMarkdown(text)
    return buildAnnotatedString {
        var cursor = 0

        val regex = Regex("(\\*\\*.*?\\*\\*|\\*.*?\\*|`.*?`|~~.*?~~|\\$.*?\\$|\\[.*?\\]\\(.*?\\))")
        val matches = regex.findAll(sanitized)

        for (match in matches) {
            if (match.range.first > cursor) {
                append(text.substring(cursor, match.range.first))
            }

            val token = match.value

            when {
                // Bold (**text**)
                token.startsWith("**") && token.endsWith("**") && token.length >= 4 -> {
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                    append(token.substring(2, token.length - 2))
                    pop()
                }

                // Italic (*text*)
                token.startsWith("*") && token.endsWith("*") && token.length >= 2 -> {
                    pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                    append(token.substring(1, token.length - 1))
                    pop()
                }

                // Inline code (`code`) — мягкая акцентная подсветка вместо серого кирпича.
                // Скруглить углы у SpanStyle нельзя, поэтому берём светлую заливку
                // и воздух по бокам через пробелы.
                token.startsWith("`") && token.endsWith("`") && token.length >= 2 -> {
                    pushStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = primaryColor.copy(alpha = 0.13f),
                            color = primaryColor,
                            fontSize = 13.sp
                        )
                    )
                    append(" ${token.substring(1, token.length - 1)} ")
                    pop()
                }

                // Strikethrough (~~text~~)
                token.startsWith("~~") && token.endsWith("~~") && token.length >= 4 -> {
                    pushStyle(SpanStyle(textDecoration = TextDecoration.LineThrough))
                    append(token.substring(2, token.length - 2))
                    pop()
                }

                // Inline LaTeX ($formula$)
                token.startsWith("$") && token.endsWith("$") && token.length >= 2 -> {
                    val rawFormula = token.substring(1, token.length - 1)
                    pushStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            color = secondaryColor,
                            fontWeight = FontWeight.Medium
                        )
                    )
                    append(formatInlineMath(rawFormula))
                    pop()
                }

                // Links ([text](url))
                token.startsWith("[") && token.contains("](") && token.endsWith(")") -> {
                    val linkText = token.substringAfter("[").substringBefore("]")
                    pushStyle(
                        SpanStyle(
                            color = primaryColor,
                            textDecoration = TextDecoration.Underline
                        )
                    )
                    append(linkText)
                    pop()
                }

                else -> append(token)
            }

            cursor = match.range.last + 1
        }

        if (cursor < text.length) {
            append(text.substring(cursor))
        }
    }
}

private fun formatInlineMath(latex: String): String {
    return latex
        .replace("\\alpha", "α")
        .replace("\\beta", "β")
        .replace("\\gamma", "γ")
        .replace("\\delta", "δ")
        .replace("\\epsilon", "ε")
        .replace("\\theta", "θ")
        .replace("\\lambda", "λ")
        .replace("\\mu", "μ")
        .replace("\\pi", "π")
        .replace("\\sigma", "σ")
        .replace("\\phi", "φ")
        .replace("\\omega", "ω")
        .replace("\\pm", "±")
        .replace("\\times", "×")
        .replace("\\div", "÷")
        .replace("\\le", "≤")
        .replace("\\ge", "≥")
        .replace("\\neq", "≠")
        .replace("\\approx", "≈")
        .replace("\\infty", "∞")
        .replace("\\sum", "∑")
        .replace("\\prod", "∏")
        .replace("\\int", "∫")
        .replace("\\sqrt", "√")
        .replace("^2", "²")
        .replace("^3", "³")
        .replace("_0", "₀")
        .replace("_1", "₁")
        .replace("_2", "₂")
        .replace("_i", "ᵢ")
        .replace("_n", "ₙ")
}
