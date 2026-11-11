package com.runner.app.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.webkit.WebView
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.runner.app.ui.theme.RobotoMono
import com.runner.app.ui.theme.MotionTokens
import com.runner.app.ui.theme.StatusSuccess
import com.runner.app.ui.components.RunnerIcons

sealed class MarkdownElement {
    data class Header(val level: Int, val content: String) : MarkdownElement()
    data class CodeBlock(val language: String, val code: String) : MarkdownElement()
    data class MathBlock(val latex: String) : MarkdownElement()
    data class Table(
        val headers: List<String>,
        val rows: List<List<String>>,
        val alignments: List<TableAlignment> = emptyList()
    ) : MarkdownElement()
    data class ListBlock(val items: List<MarkdownElement>) : MarkdownElement()
    data class BulletItem(val text: String) : MarkdownElement()
    data class NumberedItem(val number: String, val text: String) : MarkdownElement()
    data class Blockquote(val text: String) : MarkdownElement()
    data class Paragraph(val text: String) : MarkdownElement()
}

enum class TableAlignment {
    START, CENTER, END
}

const val CURSOR_INLINE_ID = "streaming_cursor"

/**
 * Деликатный пульсирующий курсор генерации (streaming cursor).
 * Выполняется аппаратно через graphicsLayer (нулевой оверхед на рекомпозицию).
 */
@Composable
fun StreamingCursor(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    width: Dp = 6.5.dp,
    height: Dp = 15.dp,
    exitAlpha: () -> Float = { 1f }
) {
    val infiniteTransition = rememberInfiniteTransition(label = "streaming_cursor")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 550,
                easing = MotionTokens.FluidCurve
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cursor_alpha"
    )

    Box(
        modifier = modifier
            .padding(start = 2.dp, end = 1.dp)
            .width(width)
            .height(height)
            .graphicsLayer { this.alpha = alpha * exitAlpha() }
            .clip(RoundedCornerShape(1.5.dp))
            .background(color)
    )
}

fun cursorInlineContent(
    color: Color,
    cursorWidth: Dp = 6.5.dp,
    cursorHeight: Dp = 15.dp,
    exitAlpha: () -> Float = { 1f }
): Map<String, InlineTextContent> = mapOf(
    CURSOR_INLINE_ID to InlineTextContent(
        Placeholder(
            width = 9.sp,
            height = 16.sp,
            placeholderVerticalAlign = PlaceholderVerticalAlign.Center
        )
    ) {
        StreamingCursor(
            color = color,
            width = cursorWidth,
            height = cursorHeight,
            exitAlpha = exitAlpha
        )
    }
)

@Composable
fun MarkdownView(
    text: String,
    modifier: Modifier = Modifier,
    isStreaming: Boolean = false
) {
    val elements = remember(text) { parseMarkdown(text) }

    var showCursor by remember { mutableStateOf(isStreaming) }
    val cursorExitAlpha = remember { Animatable(if (isStreaming) 1f else 0f) }

    LaunchedEffect(isStreaming) {
        if (isStreaming) {
            showCursor = true
            cursorExitAlpha.snapTo(1f)
        } else if (showCursor) {
            cursorExitAlpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = MotionTokens.DurationFast,
                    easing = MotionTokens.FluidCurve
                )
            )
            showCursor = false
        }
    }

    SelectionContainer {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (elements.isEmpty() && showCursor) {
                StreamingCursor(exitAlpha = { cursorExitAlpha.value })
            } else {
                elements.forEachIndexed { index, element ->
                    val isLast = index == elements.lastIndex
                    val elementStreaming = showCursor && isLast
                    val exitAlpha = { cursorExitAlpha.value }
                    when (element) {
                        is MarkdownElement.Header -> HeaderElement(element, isStreaming = elementStreaming, exitAlpha = exitAlpha)
                        is MarkdownElement.CodeBlock -> CodeBlockElement(element, isStreaming = elementStreaming, exitAlpha = exitAlpha)
                        is MarkdownElement.MathBlock -> MathBlockElement(element, isStreaming = elementStreaming, exitAlpha = exitAlpha)
                        is MarkdownElement.ListBlock -> ListBlockElement(element, isStreaming = elementStreaming, exitAlpha = exitAlpha)
                        is MarkdownElement.BulletItem -> BulletElement(element, isStreaming = elementStreaming, exitAlpha = exitAlpha)
                        is MarkdownElement.NumberedItem -> NumberedElement(element, isStreaming = elementStreaming, exitAlpha = exitAlpha)
                        is MarkdownElement.Table -> TableElement(element, isStreaming = elementStreaming, exitAlpha = exitAlpha)
                        is MarkdownElement.Blockquote -> BlockquoteElement(element, isStreaming = elementStreaming, exitAlpha = exitAlpha)
                        is MarkdownElement.Paragraph -> ParagraphElement(element, isStreaming = elementStreaming, exitAlpha = exitAlpha)
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderElement(
    element: MarkdownElement.Header,
    isStreaming: Boolean = false,
    exitAlpha: () -> Float = { 1f }
) {
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
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val annotated = remember(element.content, primaryColor, secondaryColor, isStreaming) {
        renderInlineMarkdown(
            element.content,
            primaryColor = primaryColor,
            secondaryColor = secondaryColor,
            showCursor = isStreaming
        )
    }
    Text(
        text = annotated,
        style = style,
        color = MaterialTheme.colorScheme.onSurface,
        inlineContent = if (isStreaming) cursorInlineContent(primaryColor, cursorHeight = 16.dp, exitAlpha = exitAlpha) else emptyMap(),
        modifier = Modifier.padding(top = topPadding)
    )
}

private val SYNTAX_TOKEN_REGEX = Regex(
    // 1: Comments (// ... or # ... or /* ... */)
    "((?://|#)[^\\n]*|/\\*[\\s\\S]*?\\*/)" +
    // 2: Strings ("..." or '...' or """...""")
    "|(\"\"\"[\\s\\S]*?\"\"\"|\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*')" +
    // 3: Numbers (0x..., 123, 3.14, 100L, 10f)
    "|(\\b(?:0x[0-9a-fA-F]+|\\d+(?:\\.\\d+)?(?:[fFLuU]|px|dp|sp)?)\\b)" +
    // 4: Keywords
    "|(\\b(?:val|var|fun|class|interface|object|import|package|return|if|else|when|for|while|do|try|catch|finally|throw|new|public|private|protected|internal|override|suspend|data|sealed|enum|null|true|false|this|super|def|elif|except|raise|with|yield|lambda|async|await|None|True|False|in|is|not|and|or|pass|break|continue|function|const|let|export|default|switch|case|undefined|typeof|instanceof|echo|sudo|chmod|chown|SELECT|FROM|WHERE|INSERT|UPDATE|DELETE|JOIN|TABLE|CREATE|DROP|ALTER|GROUP|ORDER|BY|LIMIT|HAVING)\\b)" +
    // 5: Function calls
    "|(\\b[a-zA-Z_][a-zA-Z0-9_]*(?=\\s*\\())"
)

/**
 * Быстрая и плавная подсветка синтаксиса для блоков кода.
 * Раскрашивает ключевые слова, строки, числа, комментарии и вызовы функций.
 */
internal fun highlightCodeSyntax(
    code: String,
    keywordColor: Color = Color(0xFFCF85DE),  // Soft Magenta / Purple
    stringColor: Color = Color(0xFFA8DF8E),   // Soft Mint Green
    commentColor: Color = Color(0xFF8E92A4),  // Cool Muted Slate Gray
    numberColor: Color = Color(0xFFFFB74D),   // Warm Amber Orange
    functionColor: Color = Color(0xFF64B5F6)  // Sky Blue
): AnnotatedString {
    return buildAnnotatedString {
        append(code)
        SYNTAX_TOKEN_REGEX.findAll(code).forEach { match ->
            val range = match.range
            when {
                match.groups[1] != null -> addStyle(
                    SpanStyle(color = commentColor, fontStyle = FontStyle.Italic),
                    range.first,
                    range.last + 1
                )
                match.groups[2] != null -> addStyle(
                    SpanStyle(color = stringColor),
                    range.first,
                    range.last + 1
                )
                match.groups[3] != null -> addStyle(
                    SpanStyle(color = numberColor),
                    range.first,
                    range.last + 1
                )
                match.groups[4] != null -> addStyle(
                    SpanStyle(color = keywordColor, fontWeight = FontWeight.SemiBold),
                    range.first,
                    range.last + 1
                )
                match.groups[5] != null -> addStyle(
                    SpanStyle(color = functionColor),
                    range.first,
                    range.last + 1
                )
            }
        }
    }
}

@Composable
private fun CodeBlockElement(
    element: MarkdownElement.CodeBlock,
    isStreaming: Boolean = false,
    exitAlpha: () -> Float = { 1f }
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            kotlinx.coroutines.delay(2000)
            copied = false
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column {
            // Header bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.7f))
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = RunnerIcons.Code,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = element.language.ifBlank { "код" }.lowercase(),
                        fontSize = 12.sp,
                        fontFamily = RobotoMono,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (copied) StatusSuccess.copy(alpha = 0.15f)
                            else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f)
                        )
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("Code", element.code))
                            copied = true
                            Toast.makeText(context, "Код скопирован", Toast.LENGTH_SHORT).show()
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (copied) RunnerIcons.Check else RunnerIcons.ContentCopy,
                        contentDescription = null,
                        tint = if (copied) StatusSuccess else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = if (copied) "Скопировано" else "Копировать",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (copied) StatusSuccess else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Список файлов рендерим строками, а не блоком кода с горизонтальной
            // прокруткой: в прокрутке длинный путь обрезается по правому краю,
            // и в списке остаются одинаковые строки без имён файлов.
            val isFileList = remember(element.code) { looksLikeFileList(element.code) }

            if (isFileList) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    ToolOutputView(output = element.code)
                    if (isStreaming) {
                        Spacer(modifier = Modifier.height(6.dp))
                        StreamingCursor(exitAlpha = exitAlpha)
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    val primaryColor = MaterialTheme.colorScheme.primary
                    val codeAnnotated = remember(element.code, isStreaming) {
                        val highlighted = highlightCodeSyntax(
                            code = element.code
                        )
                        if (isStreaming) {
                            buildAnnotatedString {
                                append(highlighted)
                                appendInlineContent(CURSOR_INLINE_ID, "▋")
                            }
                        } else {
                            highlighted
                        }
                    }
                    Text(
                        text = codeAnnotated,
                        fontFamily = RobotoMono,
                        fontSize = 12.5.sp,
                        lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        inlineContent = if (isStreaming) cursorInlineContent(primaryColor, cursorWidth = 5.5.dp, cursorHeight = 13.dp, exitAlpha = exitAlpha) else emptyMap()
                    )
                }
            }
        }
    }
}

@Composable
private fun MathBlockElement(
    element: MarkdownElement.MathBlock,
    isStreaming: Boolean = false,
    exitAlpha: () -> Float = { 1f }
) {
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
                    imageVector = RunnerIcons.Functions,
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
    if (isStreaming) {
        Spacer(modifier = Modifier.height(4.dp))
        StreamingCursor(exitAlpha = exitAlpha)
    }
}

@Composable
private fun ListBlockElement(
    element: MarkdownElement.ListBlock,
    isStreaming: Boolean = false,
    exitAlpha: () -> Float = { 1f }
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        element.items.forEachIndexed { idx, item ->
            val isLast = idx == element.items.lastIndex
            when (item) {
                is MarkdownElement.BulletItem -> BulletElement(item, isStreaming = isStreaming && isLast, exitAlpha = exitAlpha)
                is MarkdownElement.NumberedItem -> NumberedElement(item, isStreaming = isStreaming && isLast, exitAlpha = exitAlpha)
                else -> {}
            }
        }
    }
}

@Composable
private fun BulletElement(
    element: MarkdownElement.BulletItem,
    isStreaming: Boolean = false,
    exitAlpha: () -> Float = { 1f }
) {
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
            style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 22.sp),
            modifier = Modifier.weight(1f),
            isStreaming = isStreaming,
            exitAlpha = exitAlpha
        )
    }
}

@Composable
private fun NumberedElement(
    element: MarkdownElement.NumberedItem,
    isStreaming: Boolean = false,
    exitAlpha: () -> Float = { 1f }
) {
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
            style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 22.sp),
            modifier = Modifier.weight(1f),
            isStreaming = isStreaming,
            exitAlpha = exitAlpha
        )
    }
}

@Composable
private fun BlockquoteElement(
    element: MarkdownElement.Blockquote,
    isStreaming: Boolean = false,
    exitAlpha: () -> Float = { 1f }
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val annotated = remember(element.text, primaryColor, secondaryColor, isStreaming) {
        renderInlineMarkdown(
            element.text,
            primaryColor = primaryColor,
            secondaryColor = secondaryColor,
            showCursor = isStreaming
        )
    }
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
            text = annotated,
            fontStyle = FontStyle.Italic,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            inlineContent = if (isStreaming) cursorInlineContent(primaryColor, exitAlpha = exitAlpha) else emptyMap()
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
    modifier: Modifier = Modifier,
    isStreaming: Boolean = false,
    exitAlpha: () -> Float = { 1f }
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val segments = remember(text) { splitInline(text) }

    if (!shouldUseChips(segments)) {
        val annotated = remember(text, primaryColor, secondaryColor, isStreaming) {
            renderInlineMarkdown(
                text = text,
                primaryColor = primaryColor,
                secondaryColor = secondaryColor,
                showCursor = isStreaming
            )
        }
        Text(
            text = annotated,
            style = style,
            color = color,
            inlineContent = if (isStreaming) cursorInlineContent(primaryColor, exitAlpha = exitAlpha) else emptyMap(),
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
        if (isStreaming) {
            StreamingCursor(
                color = primaryColor,
                exitAlpha = exitAlpha,
                modifier = Modifier.align(Alignment.CenterVertically)
            )
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
            fontFamily = RobotoMono,
            maxLines = 1
        )
    }
}

@Composable
private fun ParagraphElement(
    element: MarkdownElement.Paragraph,
    isStreaming: Boolean = false,
    exitAlpha: () -> Float = { 1f }
) {
    MarkdownText(
        text = element.text,
        style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 22.sp),
        isStreaming = isStreaming,
        exitAlpha = exitAlpha
    )
}


@Composable
private fun TableElement(
    element: MarkdownElement.Table,
    isStreaming: Boolean = false,
    exitAlpha: () -> Float = { 1f }
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    var sortColumnIndex by remember { mutableStateOf<Int?>(null) }
    var sortAscending by remember { mutableStateOf(true) }

    val sortedRows = remember(element.rows, sortColumnIndex, sortAscending) {
        val col = sortColumnIndex
        if (col == null || col >= element.headers.size) {
            element.rows
        } else {
            element.rows.sortedWith { r1, r2 ->
                val v1 = r1.getOrElse(col) { "" }
                val v2 = r2.getOrElse(col) { "" }
                val comp = compareTableCells(v1, v2)
                if (sortAscending) comp else -comp
            }
        }
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header bar: Table icon, title, row count and Copy CSV button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.5f))
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = RunnerIcons.Table,
                        contentDescription = null,
                        tint = primaryColor,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "Таблица",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "· ${element.rows.size} ${formatTableRowCount(element.rows.size)}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (sortColumnIndex != null) {
                        Surface(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                sortColumnIndex = null
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
                            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                        ) {
                            Text(
                                text = "Сброс",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Surface(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            val csv = exportTableToCsv(element.headers, sortedRows)
                            val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            manager.setPrimaryClip(ClipData.newPlainText("Таблица CSV", csv))
                            Toast.makeText(context, "Таблица скопирована как CSV", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.5.dp)
                        ) {
                            Icon(
                                imageVector = RunnerIcons.ContentCopy,
                                contentDescription = "Скопировать как CSV",
                                tint = primaryColor,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "CSV",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = primaryColor
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), thickness = 0.5.dp)

            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val availableWidth = maxWidth
                val colCount = element.headers.size

                val colWidths = remember(element.headers, sortedRows, availableWidth) {
                    if (colCount == 0) emptyList()
                    else {
                        val estimated = element.headers.indices.map { col ->
                            val headerChars = element.headers[col].length
                            val maxRowChars = sortedRows.maxOfOrNull { it.getOrElse(col) { "" }.length } ?: 0
                            val maxChars = maxOf(headerChars, maxRowChars)
                            (maxChars * 8.5f + 36f).dp.coerceIn(90.dp, 260.dp)
                        }
                        val totalEstimated = estimated.fold(0.dp) { acc, w -> acc + w }
                        if (totalEstimated < availableWidth && totalEstimated > 0.dp) {
                            val ratio = availableWidth.value / totalEstimated.value
                            estimated.map { (it.value * ratio).dp }
                        } else {
                            estimated
                        }
                    }
                }

                val totalContentWidth = remember(colWidths) {
                    colWidths.fold(0.dp) { acc, w -> acc + w }
                }
                val tableWidth = if (totalContentWidth > availableWidth) totalContentWidth else availableWidth

                // Scrollable table area
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    Column(modifier = Modifier.width(tableWidth)) {
                        // Header Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.45f)),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            element.headers.forEachIndexed { colIndex, header ->
                                val width = colWidths.getOrElse(colIndex) { 90.dp }
                                val isSorted = sortColumnIndex == colIndex
                                val align = element.alignments.getOrElse(colIndex) { TableAlignment.START }

                                Box(
                                    modifier = Modifier
                                        .width(width)
                                        .clickable {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            if (sortColumnIndex != colIndex) {
                                                sortColumnIndex = colIndex
                                                sortAscending = true
                                            } else if (sortAscending) {
                                                sortAscending = false
                                            } else {
                                                sortColumnIndex = null
                                            }
                                        }
                                        .padding(horizontal = 10.dp, vertical = 9.dp),
                                    contentAlignment = when (align) {
                                        TableAlignment.CENTER -> Alignment.Center
                                        TableAlignment.END -> Alignment.CenterEnd
                                        TableAlignment.START -> Alignment.CenterStart
                                    }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = header,
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (isSorted) primaryColor else MaterialTheme.colorScheme.onSurface
                                        )
                                        if (isSorted) {
                                            Icon(
                                                imageVector = if (sortAscending) RunnerIcons.ArrowUpward else RunnerIcons.ArrowDownward,
                                                contentDescription = if (sortAscending) "По возрастанию" else "По убыванию",
                                                tint = primaryColor,
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }
                                    }
                                }

                                if (colIndex < element.headers.lastIndex) {
                                    Box(
                                        modifier = Modifier
                                            .width(0.5.dp)
                                            .height(20.dp)
                                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), thickness = 0.5.dp)

                        // Data Rows
                        sortedRows.forEachIndexed { rowIndex, row ->
                            val isLastRow = rowIndex == sortedRows.lastIndex
                            val isStreamingRow = isStreaming && isLastRow
                            val rowBg = if (rowIndex % 2 == 1) {
                                MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.22f)
                            } else {
                                Color.Transparent
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(rowBg),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                element.headers.indices.forEach { colIndex ->
                                    val width = colWidths.getOrElse(colIndex) { 90.dp }
                                    val cellText = row.getOrElse(colIndex) { "" }
                                    val align = element.alignments.getOrElse(colIndex) { TableAlignment.START }
                                    val isLastCell = colIndex == element.headers.lastIndex
                                    val showStreamingCursor = isStreamingRow && isLastCell

                                    val annotated = remember(cellText, primaryColor, secondaryColor, showStreamingCursor) {
                                        renderInlineMarkdown(
                                            text = cellText,
                                            primaryColor = primaryColor,
                                            secondaryColor = secondaryColor,
                                            showCursor = showStreamingCursor
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .width(width)
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        contentAlignment = when (align) {
                                            TableAlignment.CENTER -> Alignment.Center
                                            TableAlignment.END -> Alignment.CenterEnd
                                            TableAlignment.START -> Alignment.CenterStart
                                        }
                                    ) {
                                        Text(
                                            text = annotated,
                                            fontSize = 12.5.sp,
                                            lineHeight = 17.sp,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            inlineContent = if (showStreamingCursor) {
                                                cursorInlineContent(primaryColor, cursorHeight = 14.dp, exitAlpha = exitAlpha)
                                            } else {
                                                emptyMap()
                                            }
                                        )
                                    }

                                    if (colIndex < element.headers.lastIndex) {
                                        Box(
                                            modifier = Modifier
                                                .width(0.5.dp)
                                                .height(20.dp)
                                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f))
                                        )
                                    }
                                }
                            }

                            if (rowIndex < sortedRows.lastIndex) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f),
                                    thickness = 0.5.dp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatTableRowCount(count: Int): String {
    val mod10 = count % 10
    val mod100 = count % 100
    return when {
        mod100 in 11..14 -> "строк"
        mod10 == 1 -> "строка"
        mod10 in 2..4 -> "строки"
        else -> "строк"
    }
}

private fun compareTableCells(v1: String, v2: String): Int {
    val (num1, str1) = parseCellSortKey(v1)
    val (num2, str2) = parseCellSortKey(v2)
    return when {
        num1 != null && num2 != null -> num1.compareTo(num2)
        num1 != null -> -1
        num2 != null -> 1
        else -> str1.compareTo(str2, ignoreCase = true)
    }
}

private fun parseCellSortKey(raw: String): Pair<Double?, String> {
    val stripped = raw.replace(Regex("""[*_`$]"""), "").trim()
    val sizeMatch = Regex("""^([+-]?\d+(?:\.\d+)?)\s*(B|KB|MB|GB|TB|КБ|МБ|ГБ|ТБ|байт)$""", RegexOption.IGNORE_CASE).find(stripped)
    if (sizeMatch != null) {
        val num = sizeMatch.groupValues[1].toDoubleOrNull() ?: 0.0
        val unit = sizeMatch.groupValues[2].uppercase()
        val multiplier = when {
            unit.startsWith("K") || unit.startsWith("К") -> 1024.0
            unit.startsWith("M") || unit.startsWith("М") -> 1024.0 * 1024.0
            unit.startsWith("G") || unit.startsWith("Г") -> 1024.0 * 1024.0 * 1024.0
            unit.startsWith("T") || unit.startsWith("Т") -> 1024.0 * 1024.0 * 1024.0 * 1024.0
            else -> 1.0
        }
        return (num * multiplier) to stripped.lowercase()
    }
    val cleanNum = stripped.replace(Regex("""[%\$€₽,\s]"""), "").replace(',', '.')
    val numVal = cleanNum.toDoubleOrNull()
    if (numVal != null) {
        return numVal to stripped.lowercase()
    }
    return null to stripped.lowercase()
}

private fun exportTableToCsv(headers: List<String>, rows: List<List<String>>): String {
    fun escapeCsv(cell: String): String {
        val clean = cell.replace(Regex("""[*_`$]"""), "").trim()
        return if (clean.contains(",") || clean.contains("\"") || clean.contains("\n") || clean.contains(";")) {
            "\"" + clean.replace("\"", "\"\"") + "\""
        } else {
            clean
        }
    }
    val sb = StringBuilder()
    if (headers.isNotEmpty()) {
        sb.append(headers.joinToString(",") { escapeCsv(it) }).append("\n")
    }
    rows.forEach { row ->
        sb.append(row.joinToString(",") { escapeCsv(it) }).append("\n")
    }
    return sb.toString().trimEnd()
}

private fun parseTableRow(line: String): List<String> {
    val escaped = line.replace("""\|""", "\u0000PIPE\u0000").trim()
    val clean = if (escaped.startsWith("|") && escaped.endsWith("|") && escaped.length >= 2) {
        escaped.substring(1, escaped.length - 1)
    } else {
        escaped.removePrefix("|").removeSuffix("|")
    }
    return clean.split("|").map { cell ->
        cell.replace("\u0000PIPE\u0000", "|").trim()
    }
}

private fun parseAlignments(delimiterLine: String): List<TableAlignment> {
    val cells = parseTableRow(delimiterLine)
    return cells.map { cell ->
        val trimmed = cell.trim()
        when {
            trimmed.startsWith(":") && trimmed.endsWith(":") -> TableAlignment.CENTER
            trimmed.endsWith(":") -> TableAlignment.END
            else -> TableAlignment.START
        }
    }
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

        // 5. Table (Markdown pipe table)
        if (trimmed.contains("|") && i + 1 < lines.size) {
            val nextTrimmed = lines[i + 1].trim()
            val tableDelimiterRegex = Regex("""^\s*\|?\s*:?-+:?\s*(\|\s*:?-+:?\s*)+\|?\s*$""")
            if (tableDelimiterRegex.matches(nextTrimmed)) {
                val headers = parseTableRow(line)
                val alignments = parseAlignments(nextTrimmed)
                val rows = mutableListOf<List<String>>()
                i += 2
                while (i < lines.size) {
                    val rowLine = lines[i]
                    val rowTrimmed = rowLine.trim()
                    if (rowTrimmed.isEmpty() || !rowTrimmed.contains("|") || rowTrimmed.startsWith("```")) {
                        break
                    }
                    val rowCells = parseTableRow(rowLine)
                    val paddedCells = if (rowCells.size < headers.size) {
                        rowCells + List(headers.size - rowCells.size) { "" }
                    } else {
                        rowCells.take(headers.size)
                    }
                    rows.add(paddedCells)
                    i++
                }
                elements.add(MarkdownElement.Table(headers, rows, alignments))
                continue
            }
        }

        // 6. Lists (Bullet Lists: * item, - item; Numbered Lists: 1. item)
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
    secondaryColor: Color = Color(0xFF80CBC4),
    showCursor: Boolean = false
): AnnotatedString {
    val sanitized = sanitizeMarkdown(text)
    return buildAnnotatedString {
        var cursor = 0

        val regex = Regex("(\\*\\*.*?\\*\\*|\\*.*?\\*|`.*?`|~~.*?~~|\\$.*?\\$|\\[.*?\\]\\(.*?\\))")
        val matches = regex.findAll(sanitized)

        for (match in matches) {
            if (match.range.first > cursor) {
                append(sanitized.substring(cursor, match.range.first))
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
                            fontFamily = RobotoMono,
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
                            fontFamily = RobotoMono,
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

        if (cursor < sanitized.length) {
            append(sanitized.substring(cursor))
        }

        if (showCursor) {
            appendInlineContent(CURSOR_INLINE_ID, "▋")
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
