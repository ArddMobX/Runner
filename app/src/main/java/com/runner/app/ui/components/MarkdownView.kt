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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.runner.app.ui.theme.*

sealed class MarkdownElement {
    data class Header(val level: Int, val content: String) : MarkdownElement()
    data class CodeBlock(val language: String, val code: String) : MarkdownElement()
    data class MathBlock(val latex: String) : MarkdownElement()
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
        text = renderInlineMarkdown(element.content),
        style = style,
        color = TextPrimary,
        modifier = Modifier.padding(top = topPadding)
    )
}

@Composable
private fun CodeBlockElement(element: MarkdownElement.CodeBlock) {
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceContainerHigh),
        border = BorderStroke(1.dp, OutlineSubtle),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            // Header bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceContainerLowest.copy(alpha = 0.5f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = element.language.ifBlank { "code" }.lowercase(),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    color = AccentPrimary
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
                            tint = if (copied) StatusSuccess else TextSecondary,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }

            // Code content
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
                    color = TextPrimary
                )
            }
        }
    }
}

@Composable
private fun MathBlockElement(element: MarkdownElement.MathBlock) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceContainerHigh),
        border = BorderStroke(1.dp, OutlineSubtle),
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
                    tint = AccentSecondary,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "LaTeX Formula",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AccentSecondary
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
private fun BulletElement(element: MarkdownElement.BulletItem) {
    Row(
        modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Маркер меньше текста, иначе список спорит по весу с обычным абзацем.
        Text(
            text = "•",
            color = AccentPrimary,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 2.dp, end = 8.dp)
        )
        Text(
            text = renderInlineMarkdown(element.text),
            style = MaterialTheme.typography.bodyLarge,
            color = TextPrimary
        )
    }
}

@Composable
private fun NumberedElement(element: MarkdownElement.NumberedItem) {
    Row(
        modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Фиксированная ширина — чтобы двузначные номера не сдвигали текст.
        Text(
            text = "${element.number}.",
            color = AccentPrimary,
            fontWeight = FontWeight.Medium,
            fontSize = 12.5.sp,
            modifier = Modifier
                .width(20.dp)
                .padding(top = 3.dp)
        )
        Text(
            text = renderInlineMarkdown(element.text),
            style = MaterialTheme.typography.bodyLarge,
            color = TextPrimary
        )
    }
}

@Composable
private fun BlockquoteElement(element: MarkdownElement.Blockquote) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(SurfaceContainerHigh.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
            .border(
                BorderStroke(2.dp, AccentPrimary.copy(alpha = 0.6f)),
                RoundedCornerShape(4.dp)
            )
            .padding(10.dp)
    ) {
        Text(
            text = renderInlineMarkdown(element.text),
            fontStyle = FontStyle.Italic,
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary
        )
    }
}

@Composable
private fun ParagraphElement(element: MarkdownElement.Paragraph) {
    Text(
        text = renderInlineMarkdown(element.text),
        style = MaterialTheme.typography.bodyLarge,
        color = TextPrimary,
        lineHeight = 22.sp
    )
}

/**
 * Parses raw text into high-level Markdown elements.
 */
fun parseMarkdown(rawText: String): List<MarkdownElement> {
    val lines = rawText.lines()
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

        // 5. Bullet Lists (* item, - item)
        if (trimmed.startsWith("* ") || trimmed.startsWith("- ")) {
            val itemText = trimmed.drop(2).trim()
            elements.add(MarkdownElement.BulletItem(itemText))
            i++
            continue
        }

        // 6. Numbered Lists (1. item)
        val numMatch = Regex("^([0-9]+)\\.\\s+(.*)").find(trimmed)
        if (numMatch != null) {
            val number = numMatch.groupValues[1]
            val itemText = numMatch.groupValues[2]
            elements.add(MarkdownElement.NumberedItem(number, itemText))
            i++
            continue
        }

        // 7. Regular paragraph / empty line
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
fun renderInlineMarkdown(text: String): AnnotatedString {
    return buildAnnotatedString {
        var cursor = 0

        val regex = Regex("(\\*\\*.*?\\*\\*|\\*.*?\\*|`.*?`|~~.*?~~|\\$.*?\\$|\\[.*?\\]\\(.*?\\))")
        val matches = regex.findAll(text)

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
                            background = AccentPrimary.copy(alpha = 0.13f),
                            color = AccentPrimary,
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
                            color = AccentSecondary,
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
                            color = AccentPrimary,
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
