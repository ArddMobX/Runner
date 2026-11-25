package com.runner.app.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
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
import com.runner.app.ui.theme.MotionSettings
import com.runner.app.ui.theme.MotionTokens
import com.runner.app.ui.theme.RunnerShapes
import com.runner.app.ui.theme.TypeScale
import com.runner.app.ui.theme.StatusSuccess
import com.runner.app.ui.components.RunnerIcons
import java.io.ByteArrayInputStream

/**
 * Сериализует строку в безопасный JSON/JS-литерал для вставки внутрь `<script>`:
 * экранирует `</script>`, кавычки, обратные слэши, управляющие символы и
 * юникодные разделители строк `\u2028`/`\u2029`.
 */
internal fun safeJsonStringForHtmlScript(value: String): String {
    val sb = StringBuilder(value.length + 16)
    sb.append('"')
    for (ch in value) {
        when (ch) {
            '\\' -> sb.append("\\\\")
            '"' -> sb.append("\\\"")
            '\n' -> sb.append("\\n")
            '\r' -> sb.append("\\r")
            '\t' -> sb.append("\\t")
            '\b' -> sb.append("\\b")
            '\u000C' -> sb.append("\\f")
            '<' -> sb.append("\\u003c")
            '>' -> sb.append("\\u003e")
            '&' -> sb.append("\\u0026")
            '\u2028' -> sb.append("\\u2028")
            '\u2029' -> sb.append("\\u2029")
            else -> {
                if (ch.code < 0x20) {
                    sb.append(String.format(java.util.Locale.US, "\\u%04x", ch.code))
                } else {
                    sb.append(ch)
                }
            }
        }
    }
    sb.append('"')
    return sb.toString()
}

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
    data class BulletItem(
        val text: String,
        val indentLevel: Int = 0,
        val checked: Boolean? = null
    ) : MarkdownElement()
    data class NumberedItem(
        val number: String,
        val text: String,
        val indentLevel: Int = 0
    ) : MarkdownElement()
    data class Blockquote(val text: String) : MarkdownElement()
    data object HorizontalRule : MarkdownElement()
    data class Paragraph(val text: String) : MarkdownElement()

    /**
     * Отдельная ссылка в тексте. Модель часто выдаёт голые URL строками:
     * длинная ссылка ломает вёрстку и не нажимается. Такая строка
     * превращается в скруглённую карточку и открывается в браузере.
     */
    data class LinkCard(
        val url: String,
        val title: String? = null,
        val isImage: Boolean = false
    ) : MarkdownElement()
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
    val alphaState: State<Float>? = if (MotionSettings.enabled) {
        val infiniteTransition = rememberInfiniteTransition(label = "streaming_cursor")
        infiniteTransition.animateFloat(
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
    } else {
        null
    }

    Box(
        modifier = modifier
            .padding(start = 2.dp, end = 1.dp)
            .width(width)
            .height(height)
            .graphicsLayer { this.alpha = (alphaState?.value ?: 1f) * exitAlpha() }
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
    val streamingCache = remember { StreamingMarkdownCache() }
    val elements = remember(text, isStreaming) {
        if (isStreaming) streamingCache.parse(text) else parseMarkdown(text)
    }

    var showCursor by remember { mutableStateOf(isStreaming) }
    val cursorExitAlpha = remember { Animatable(if (isStreaming) 1f else 0f) }

    LaunchedEffect(isStreaming) {
        if (isStreaming) {
            showCursor = true
            cursorExitAlpha.snapTo(1f)
        } else if (showCursor) {
            cursorExitAlpha.animateTo(
                targetValue = 0f,
                animationSpec = MotionTokens.fluidTween(MotionTokens.DurationFast)
            )
            showCursor = false
        }
    }

    val content: @Composable () -> Unit = {
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
                        is MarkdownElement.HorizontalRule -> HorizontalRuleElement()
                        is MarkdownElement.Paragraph -> ParagraphElement(element, isStreaming = elementStreaming, exitAlpha = exitAlpha)
                        is MarkdownElement.LinkCard -> LinkCardElement(element)
                    }
                }
            }
        }
    }

    // Пока текст течёт, выделение только мешает: SelectionContainer
    // пересобирает оверлеи на каждом обновлении. Включаем его после стрима.
    if (isStreaming) {
        content()
    } else {
        SelectionContainer { content() }
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
            fontSize = TypeScale.title,
            lineHeight = 23.sp
        ) to 8.dp

        2 -> MaterialTheme.typography.titleMedium.copy(
            fontWeight = FontWeight.SemiBold,
            fontSize = TypeScale.title,
            lineHeight = 22.sp
        ) to 6.dp

        else -> MaterialTheme.typography.titleMedium.copy(
            fontWeight = FontWeight.SemiBold,
            fontSize = TypeScale.bodyLarge,
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
    LinkAwareText(
        text = annotated,
        style = style,
        color = MaterialTheme.colorScheme.onSurface,
        inlineContent = if (isStreaming) cursorInlineContent(primaryColor, cursorHeight = 16.dp, exitAlpha = exitAlpha) else emptyMap(),
        modifier = Modifier.padding(top = topPadding)
    )
}

/**
 * Text с кликабельными аннотациями URL. Не перехватывает обычные нажатия:
 * выделение текста и скролл работают как раньше, открывается только ссылка.
 */
@Composable
private fun LinkAwareText(
    text: AnnotatedString,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    inlineContent: Map<String, InlineTextContent> = emptyMap()
) {
    val context = LocalContext.current
    // Держим результат раскладки в обычном массиве, а не в state: запись
    // в state из onTextLayout запускала бы лишний цикл рекомпозиции.
    val layoutHolder = remember { arrayOfNulls<TextLayoutResult>(1) }
    Text(
        text = text,
        style = style,
        color = color,
        inlineContent = inlineContent,
        onTextLayout = { layoutHolder[0] = it },
        modifier = modifier.pointerInput(text) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                val release = waitForUpOrCancellation() ?: return@awaitEachGesture
                val offset = layoutHolder[0]
                    ?.getOffsetForPosition(release.position)
                    ?: return@awaitEachGesture
                val url = text.getStringAnnotations("URL", offset, offset)
                    .firstOrNull()
                    ?.item
                    ?: return@awaitEachGesture
                release.consume()
                openLink(context, url)
            }
        }
    )
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
        shape = RunnerShapes.medium,
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
                        fontSize = TypeScale.caption,
                        fontFamily = RobotoMono,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Row(
                    modifier = Modifier
                        .clip(RunnerShapes.small)
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
                        fontSize = TypeScale.caption,
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
                    val isDarkSurface = MaterialTheme.colorScheme.surfaceContainerHigh.luminance() < 0.5f
                    val codeAnnotated = remember(element.code, element.language, isDarkSurface, isStreaming) {
                        val highlighted = highlightCodeSyntax(
                            code = element.code,
                            language = element.language,
                            isDarkTheme = isDarkSurface
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
                        fontSize = TypeScale.label,
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
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            kotlinx.coroutines.delay(2000)
            copied = false
        }
    }

    val fallbackMath = remember(element.latex) { formatMathFallback(element.latex) }
    var katexFailed by remember(element.latex) { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
        shape = RunnerShapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column {
            // Заголовок карточки формулы в едином стиле с CodeBlockElement
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
                        imageVector = RunnerIcons.Functions,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Формула",
                        fontSize = TypeScale.caption,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Row(
                    modifier = Modifier
                        .clip(RunnerShapes.small)
                        .background(
                            if (copied) StatusSuccess.copy(alpha = 0.15f)
                            else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f)
                        )
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("Formula", element.latex))
                            copied = true
                            Toast.makeText(context, "Формула скопирована", Toast.LENGTH_SHORT).show()
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
                        fontSize = TypeScale.caption,
                        fontWeight = FontWeight.Medium,
                        color = if (copied) StatusSuccess else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (isStreaming || katexFailed) {
                // Во время стрима не создаём WebView на каждый токен; в офлайне показываем
                // нативный выделяемый Unicode-рендер формулы.
                val primaryColor = MaterialTheme.colorScheme.primary
                val fallbackAnnotated = remember(fallbackMath, isStreaming) {
                    if (isStreaming) {
                        buildAnnotatedString {
                            append(fallbackMath)
                            appendInlineContent(CURSOR_INLINE_ID, "▋")
                        }
                    } else {
                        AnnotatedString(fallbackMath)
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = fallbackAnnotated,
                        fontFamily = RobotoMono,
                        fontSize = TypeScale.body,
                        lineHeight = 22.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        inlineContent = if (isStreaming) {
                            cursorInlineContent(
                                primaryColor,
                                cursorWidth = 5.5.dp,
                                cursorHeight = 14.dp,
                                exitAlpha = exitAlpha
                            )
                        } else {
                            emptyMap()
                        }
                    )
                }
            } else {
                // KaTeX WebView Renderer (строго изолированный, с автоматическим фолбэком при ошибке сети)
                val textColorHex = String.format(
                    java.util.Locale.US,
                    "#%06X",
                    0xFFFFFF and MaterialTheme.colorScheme.onSurface.toArgb()
                )
                var mathWebView by remember { mutableStateOf<WebView?>(null) }
                DisposableEffect(Unit) {
                    onDispose {
                        mathWebView?.stopLoading()
                        mathWebView?.loadUrl("about:blank")
                        mathWebView?.destroy()
                        mathWebView = null
                    }
                }
                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp, max = 220.dp),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = false
                            settings.allowFileAccess = false
                            settings.allowContentAccess = false
                            @Suppress("DEPRECATION")
                            settings.allowFileAccessFromFileURLs = false
                            @Suppress("DEPRECATION")
                            settings.allowUniversalAccessFromFileURLs = false
                            setBackgroundColor(0) // Transparent
                            isVerticalScrollBarEnabled = false
                            isHorizontalScrollBarEnabled = false
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): Boolean = true

                                override fun shouldInterceptRequest(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): WebResourceResponse? {
                                    val url = request?.url?.toString().orEmpty()
                                    val allowed = url.startsWith("https://cdn.jsdelivr.net/npm/katex@0.16.9/")
                                    return if (allowed) {
                                        null
                                    } else {
                                        WebResourceResponse(
                                            "text/plain",
                                            "UTF-8",
                                            ByteArrayInputStream(ByteArray(0))
                                        )
                                    }
                                }

                                override fun onReceivedError(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                    error: WebResourceError?
                                ) {
                                    val url = request?.url?.toString().orEmpty()
                                    if (url.contains("katex", ignoreCase = true) || request?.isForMainFrame == true) {
                                        view?.post { katexFailed = true }
                                    }
                                }

                                override fun onReceivedHttpError(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                    errorResponse: WebResourceResponse?
                                ) {
                                    val url = request?.url?.toString().orEmpty()
                                    val statusCode = errorResponse?.statusCode ?: 0
                                    if (statusCode >= 400 && (url.contains("katex", ignoreCase = true) || request?.isForMainFrame == true)) {
                                        view?.post { katexFailed = true }
                                    }
                                }
                            }
                            mathWebView = this
                        }
                    },
                    update = { webView ->
                        val renderKey = "$textColorHex|${element.latex}"
                        if (webView.tag != renderKey) {
                            webView.tag = renderKey

                            val safeLatexLiteral = safeJsonStringForHtmlScript(element.latex)
                            val safeFallbackLiteral = safeJsonStringForHtmlScript(fallbackMath)

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
                                    color: $textColorHex;
                                    margin: 0;
                                    padding: 10px 14px;
                                    display: flex;
                                    justify-content: center;
                                    font-size: 16px;
                                    overflow-x: auto;
                                  }
                                  #math {
                                    max-width: 100%;
                                    overflow-x: auto;
                                  }
                                  .katex { color: $textColorHex; }
                                </style>
                                </head>
                                <body>
                                <div id="math"></div>
                                <script>
                                  const latex = $safeLatexLiteral;
                                  const fallback = $safeFallbackLiteral;
                                  const target = document.getElementById("math");
                                  target.textContent = fallback;
                                  try {
                                    if (typeof katex !== "undefined") {
                                      katex.render(latex, target, {
                                        displayMode: true,
                                        throwOnError: false,
                                        trust: false
                                      });
                                    }
                                  } catch (e) {
                                    target.textContent = fallback;
                                  }
                                </script>
                                </body>
                                </html>
                            """.trimIndent()

                            webView.loadDataWithBaseURL("https://cdn.jsdelivr.net", html, "text/html", "UTF-8", null)
                        }
                    }
                )
            }
        }
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
    val indentPadding = (4 + element.indentLevel.coerceIn(0, 4) * 16).dp
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = indentPadding, top = 1.dp, bottom = 1.dp),
        verticalAlignment = Alignment.Top
    ) {
        val checked = element.checked
        if (checked != null) {
            Box(
                modifier = Modifier
                    .padding(top = 3.dp, end = 8.dp)
                    .size(16.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        if (checked) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                        else Color.Transparent
                    )
                    .border(
                        BorderStroke(
                            1.2.dp,
                            if (checked) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)
                        ),
                        RoundedCornerShape(4.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (checked) {
                    Icon(
                        imageVector = RunnerIcons.Check,
                        contentDescription = "Выполнено",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(11.dp)
                    )
                }
            }
        } else {
            val bulletGlyph = when {
                element.indentLevel <= 0 -> "•"
                element.indentLevel == 1 -> "◦"
                else -> "▪"
            }
            val bulletColor = if (element.indentLevel <= 0) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outline
            }
            Text(
                text = bulletGlyph,
                color = bulletColor,
                fontWeight = FontWeight.Bold,
                fontSize = if (element.indentLevel <= 0) TypeScale.headline else TypeScale.body,
                lineHeight = 22.sp,
                modifier = Modifier.padding(end = 8.dp)
            )
        }
        val textColor = if (checked == true) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.onSurface
        }
        MarkdownText(
            text = element.text,
            style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 22.sp),
            color = textColor,
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
    val indentPadding = (4 + element.indentLevel.coerceIn(0, 4) * 16).dp
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = indentPadding, top = 1.dp, bottom = 1.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "${element.number}.",
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            fontSize = TypeScale.body,
            lineHeight = 22.sp,
            modifier = Modifier
                .widthIn(min = 22.dp)
                .padding(end = 6.dp)
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
private fun HorizontalRuleElement() {
    HorizontalDivider(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        thickness = 0.8.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
    )
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
            .padding(vertical = 3.dp)
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(topEnd = 6.dp, bottomEnd = 6.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.30f))
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(2.dp))
                .background(primaryColor.copy(alpha = 0.65f))
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp, end = 10.dp, top = 7.dp, bottom = 7.dp)
        ) {
            LinkAwareText(
                text = annotated,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontStyle = FontStyle.Italic,
                    lineHeight = 21.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                inlineContent = if (isStreaming) cursorInlineContent(primaryColor, exitAlpha = exitAlpha) else emptyMap()
            )
        }
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

private val PLAIN_MARKDOWN_OR_LINK = Regex(
    """(\*\*|__|~~|\*[^*\s]|(?:^|[^A-Za-zА-Яа-яЁё0-9])_[^_\s]|!?\[[^\]]*\]\(|https?://|\\text\{|\$)""",
    RegexOption.IGNORE_CASE
)

/**
 * Чипсы уместны только в коротких однострочных перечислениях вида
 * «`Download` `DCIM` `Pictures`». В обычном абзаце с переносами строк
 * либо если в текстовой части есть Markdown-разметка или ссылки,
 * разбивать поток на чипсы нельзя — там остаётся цельный [LinkAwareText].
 */
private fun shouldUseChips(text: String, segments: List<InlineSegment>): Boolean {
    if (text.contains('\n') || text.contains("``")) return false
    if (segments.none { it is InlineSegment.Code }) return false
    if (segments.size > 9) return false
    return segments.filterIsInstance<InlineSegment.Plain>()
        .all { plain ->
            val trimmed = plain.value.trim()
            trimmed.length <= 28 && !PLAIN_MARKDOWN_OR_LINK.containsMatchIn(trimmed)
        }
}

internal fun shouldUseInlineChips(text: String): Boolean = shouldUseChips(text, splitInline(text))

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

    if (!shouldUseChips(text, segments)) {
        val annotated = remember(text, primaryColor, secondaryColor, isStreaming) {
            renderInlineMarkdown(
                text = text,
                primaryColor = primaryColor,
                secondaryColor = secondaryColor,
                showCursor = isStreaming
            )
        }
        LinkAwareText(
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
    val shape = RunnerShapes.tiny
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
            fontSize = TypeScale.label,
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

/**
 * Ссылка как отдельная плашка в стиле Material You: без голого длинного URL,
 * с доменом, путём и понятной зоной нажатия. Открывается системным браузером,
 * который выбран по умолчанию.
 */
@Composable
private fun LinkCardElement(element: MarkdownElement.LinkCard) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val primary = MaterialTheme.colorScheme.primary
    val host = remember(element.url) { linkHost(element.url) }
    val detail = remember(element.url) { linkDetail(element.url) }
    val title = element.title?.takeIf { it.isNotBlank() } ?: host.ifBlank { element.url }

    Surface(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            openLink(context, element.url)
        },
        shape = RunnerShapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(
            0.6.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RunnerShapes.medium)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (element.isImage) RunnerIcons.Image else RunnerIcons.Public,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(19.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = TypeScale.label,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                if (detail.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = detail,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = TypeScale.caption,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = RunnerIcons.OpenInNew,
                contentDescription = "Открыть в браузере",
                tint = primary.copy(alpha = 0.75f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

private fun openLink(context: Context, url: String) {
    if (!isWebUrl(url)) return
    try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (_: Exception) {
        Toast.makeText(context, "Не удалось открыть ссылку", Toast.LENGTH_SHORT).show()
    }
}

private fun isWebUrl(url: String): Boolean =
    url.startsWith("http://", ignoreCase = true) ||
            url.startsWith("https://", ignoreCase = true)

internal fun linkHost(url: String): String {
    val host = try {
        java.net.URI(url).host.orEmpty()
    } catch (_: Exception) {
        ""
    }.ifBlank {
        url.substringAfter("://", url).substringBefore('/').substringBefore(':')
    }
    return host.removePrefix("www.")
}

internal fun linkDetail(url: String): String {
    val uri = try {
        java.net.URI(url)
    } catch (_: Exception) {
        return ""
    }
    val path = uri.rawPath.orEmpty().trimEnd('/')
    val query = uri.rawQuery.orEmpty()
    val detail = buildString {
        if (path.isNotBlank()) append(path)
        if (query.isNotBlank()) {
            if (path.isNotBlank()) append('?') else append("/?")
            append(query)
        }
    }
    return if (detail.length <= 64) detail else detail.take(61).trimEnd('/', '?', '&') + "…"
}

/** Короткая подпись для ссылки внутри абзаца, чтобы не рвать строку URL-ом. */
internal fun prettyLinkLabel(url: String): String {
    val host = linkHost(url)
    if (host.isBlank()) return url
    val path = linkDetail(url)
    val label = if (path.isBlank()) host else "$host$path"
    return if (label.length <= 46) label else label.take(43).trimEnd('/', '?', '&', '.') + "…"
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
        shape = RunnerShapes.medium,
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
                        fontSize = TypeScale.caption,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "· ${element.rows.size} ${formatTableRowCount(element.rows.size)}",
                        fontSize = TypeScale.caption,
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
                            shape = RunnerShapes.small,
                            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
                            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                        ) {
                            Text(
                                text = "Сброс",
                                fontSize = TypeScale.caption,
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
                        shape = RunnerShapes.small,
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
                                fontSize = TypeScale.caption,
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
                                    val headerAnnotated = remember(header, primaryColor, secondaryColor) {
                                        renderInlineMarkdown(
                                            text = header,
                                            primaryColor = primaryColor,
                                            secondaryColor = secondaryColor
                                        )
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = headerAnnotated,
                                            fontSize = TypeScale.label,
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
                                        LinkAwareText(
                                            text = annotated,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontSize = TypeScale.label,
                                                lineHeight = 17.sp
                                            ),
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

private val THOUSANDS_COMMA_NUMBER = Regex("""^[+-]?\d{1,3}(,\d{3})+$""")

internal fun parseCellSortKey(raw: String): Pair<Double?, String> {
    val stripped = raw.replace(Regex("""[*_`$]"""), "").trim()
    val sizeMatch = Regex(
        """^([+-]?\d+(?:[.,]\d+)?)\s*(B|KB|MB|GB|TB|КБ|МБ|ГБ|ТБ|байт)$""",
        RegexOption.IGNORE_CASE
    ).find(stripped)
    if (sizeMatch != null) {
        val num = sizeMatch.groupValues[1].replace(',', '.').toDoubleOrNull() ?: 0.0
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
    var numStr = stripped.replace(Regex("""[%\$€₽\s\u00A0]"""), "")
    val hasComma = numStr.contains(',')
    val hasDot = numStr.contains('.')
    numStr = when {
        hasComma && hasDot -> {
            if (numStr.lastIndexOf(',') > numStr.lastIndexOf('.')) {
                // Европейский формат: 1.234,56 -> 1234.56
                numStr.replace(".", "").replace(',', '.')
            } else {
                // Английский формат: 1,234.56 -> 1234.56
                numStr.replace(",", "")
            }
        }
        hasComma -> {
            if (THOUSANDS_COMMA_NUMBER.matches(numStr)) {
                numStr.replace(",", "")
            } else if (numStr.count { it == ',' } == 1) {
                // Десятичная запятая: 3,14 -> 3.14
                numStr.replace(',', '.')
            } else {
                numStr
            }
        }
        else -> numStr
    }
    val numVal = numStr.toDoubleOrNull()
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
 *
 * Preserves verbatim regions untouched: fenced code blocks (```...```),
 * inline code spans (`...`), and block LaTeX math ($$...$$ and \[...\]).
 */
fun sanitizeMarkdown(text: String): String {
    if (text.isEmpty() || (!text.contains("\\text") && !text.contains("\\%") && !text.contains("$"))) {
        return text
    }

    val out = StringBuilder(text.length)
    var i = 0
    var plainStart = 0
    val len = text.length

    fun flushPlain(endExclusive: Int) {
        if (endExclusive > plainStart) {
            out.append(sanitizeTextChunk(text.substring(plainStart, endExclusive)))
        }
    }

    while (i < len) {
        // 1. Fenced code block: ``` / ```` / ~~~ (только в начале строки)
        if (i == 0 || text[i - 1] == '\n') {
            val lineEnd = text.indexOf('\n', i).let { if (it < 0) len else it }
            val fence = parseFenceOpen(text.substring(i, lineEnd).trim())
            if (fence != null) {
                flushPlain(i)
                var scan = if (lineEnd < len) lineEnd + 1 else len
                var blockEnd = len
                while (scan < len) {
                    val nextEnd = text.indexOf('\n', scan).let { if (it < 0) len else it }
                    if (isFenceClose(text.substring(scan, nextEnd).trim(), fence)) {
                        blockEnd = if (nextEnd < len) nextEnd + 1 else len
                        break
                    }
                    scan = if (nextEnd < len) nextEnd + 1 else len
                }
                out.append(text, i, blockEnd)
                i = blockEnd
                plainStart = blockEnd
                continue
            }
        }

        // 2. Inline code span: `...` (matched backtick run within paragraph)
        if (text[i] == '`') {
            var tickCount = 0
            while (i + tickCount < len && text[i + tickCount] == '`') {
                tickCount++
            }
            val delimiter = "`".repeat(tickCount)
            var searchPos = i + tickCount
            var matchedEnd = -1
            while (searchPos < len) {
                val candidate = text.indexOf(delimiter, searchPos)
                if (candidate < 0) break
                // Ensure exact backtick count (not a longer run)
                val beforeOk = candidate == 0 || text[candidate - 1] != '`'
                val afterOk = candidate + tickCount >= len || text[candidate + tickCount] != '`'
                if (beforeOk && afterOk) {
                    val inside = text.substring(i + tickCount, candidate)
                    if (!inside.contains("\n\n")) {
                        matchedEnd = candidate + tickCount
                    }
                    break
                }
                searchPos = candidate + 1
            }
            if (matchedEnd > 0) {
                flushPlain(i)
                out.append(text, i, matchedEnd)
                i = matchedEnd
                plainStart = matchedEnd
                continue
            }
            i += tickCount
            continue
        }

        // 3. Block LaTeX formula: $$...$$
        if (text.startsWith("$$", i)) {
            flushPlain(i)
            val closeIdx = text.indexOf("$$", i + 2)
            val end = if (closeIdx >= 0) closeIdx + 2 else len
            out.append(text, i, end)
            i = end
            plainStart = end
            continue
        }

        // 4. Block LaTeX formula: \[...\]
        if (text.startsWith("\\[", i)) {
            flushPlain(i)
            val closeIdx = text.indexOf("\\]", i + 2)
            val end = if (closeIdx >= 0) closeIdx + 2 else len
            out.append(text, i, end)
            i = end
            plainStart = end
            continue
        }

        i++
    }

    flushPlain(len)
    return out.toString()
}

private fun sanitizeTextChunk(chunk: String): String {
    // 1. \text{X} -> X
    var result = Regex("""\\text\{([^}]*)\}""").replace(chunk) { it.groupValues[1] }
    // 2. \% -> %
    result = result.replace("""\%""", "%")
    // 3. Remove $...$ around numbers and units (e.g. $51.6 GB$, $89%$, $100$),
    //    without touching prose currency pairs like "$5 и $10" where the second $ starts another number.
    result = Regex("""(?<!\$)\$\s*([~≈±+\-–—]?\s*\d[\d.,\s]*(?:[a-zA-Zа-яА-ЯёЁ%°²³/-]+(?:\s*[a-zA-Zа-яА-ЯёЁ%°²³/-]+)*)?)\s*\$(?!\$|\d)""").replace(result) {
        it.groupValues[1].trim()
    }
    // Also remove $...$ around standalone percentage e.g. $%$
    result = Regex("""(?<!\$)\$\s*([~≈±+\-–—]?\s*%)\s*\$(?!\$)""").replace(result) {
        it.groupValues[1].trim()
    }
    return result
}

private data class FenceSpec(
    val char: Char,
    val length: Int,
    val info: String
)

private fun parseFenceOpen(trimmed: String): FenceSpec? {
    if (trimmed.length < 3) return null
    val ch = trimmed[0]
    if (ch != '`' && ch != '~') return null
    var count = 0
    while (count < trimmed.length && trimmed[count] == ch) count++
    if (count < 3) return null
    val info = trimmed.substring(count).trim()
    // По CommonMark info-строка бэктик-фенса не может содержать бэктики
    // (иначе ```code``` в одну строку — это инлайн-код, а не блок).
    if (ch == '`' && info.contains('`')) return null
    return FenceSpec(char = ch, length = count, info = info)
}

private fun isFenceClose(trimmed: String, fence: FenceSpec): Boolean {
    if (trimmed.length < fence.length || trimmed[0] != fence.char) return false
    var count = 0
    while (count < trimmed.length && trimmed[count] == fence.char) count++
    return count >= fence.length && trimmed.substring(count).isBlank()
}

private val ATX_HEADER_REGEX = Regex("""^(#{1,6})\s+(.+?)\s*$""")
private val ATX_CLOSING_HASHES = Regex("""\s+#+$""")
private val HORIZONTAL_RULE_REGEX = Regex("""^(?:-\s*){3,}$|^(?:\*\s*){3,}$|^(?:_\s*){3,}$""")
private val TABLE_DELIMITER_REGEX = Regex("""^\s*\|?\s*:?-+:?\s*(\|\s*:?-+:?\s*)+\|?\s*$""")
private val NUMBERED_ITEM_REGEX = Regex("""^([0-9]+)\.\s+(.*)$""")
private val TASK_CHECKBOX_REGEX = Regex("""^\[([ xX])]\s+(.*)$""")

private fun leadingIndentWidth(line: String): Int {
    var width = 0
    for (ch in line) {
        when (ch) {
            ' ' -> width += 1
            '\t' -> width += 4
            else -> break
        }
    }
    return width
}

private fun isBulletMarker(trimmed: String): Boolean =
    trimmed.startsWith("* ") || trimmed.startsWith("- ") || trimmed.startsWith("+ ")

private fun isTableStart(lines: List<String>, index: Int): Boolean {
    if (index + 1 >= lines.size) return false
    val trimmed = lines[index].trim()
    return trimmed.contains("|") && TABLE_DELIMITER_REGEX.matches(lines[index + 1].trim())
}

private fun isBlockBoundary(lines: List<String>, index: Int): Boolean {
    val trimmed = lines[index].trim()
    if (trimmed.isEmpty()) return true
    if (parseFenceOpen(trimmed) != null) return true
    if (trimmed.startsWith("$$") || trimmed.startsWith("\\[")) return true
    if (ATX_HEADER_REGEX.find(trimmed) != null) return true
    if (HORIZONTAL_RULE_REGEX.matches(trimmed)) return true
    if (trimmed.startsWith(">")) return true
    if (isTableStart(lines, index)) return true
    if (isBulletMarker(trimmed) || NUMBERED_ITEM_REGEX.matches(trimmed)) return true
    if (parseStandaloneLinkCard(trimmed) != null) return true
    return false
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

        if (trimmed.isEmpty()) {
            i++
            continue
        }

        // 1. Code Block (```lang, ````markdown, ~~~python)
        val fence = parseFenceOpen(trimmed)
        if (fence != null) {
            val codeLines = mutableListOf<String>()
            i++
            while (i < lines.size && !isFenceClose(lines[i].trim(), fence)) {
                codeLines.add(lines[i])
                i++
            }
            if (i < lines.size && isFenceClose(lines[i].trim(), fence)) {
                i++ // consume closing fence
            }
            elements.add(MarkdownElement.CodeBlock(fence.info, codeLines.joinToString("\n")))
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

        // 3. Headers (# H1 .. ###### H6, requiring whitespace after # so #hashtag stays text)
        val headerMatch = ATX_HEADER_REGEX.find(trimmed)
        if (headerMatch != null) {
            val level = headerMatch.groupValues[1].length.coerceIn(1, 4)
            val text = headerMatch.groupValues[2]
                .replace(ATX_CLOSING_HASHES, "")
                .trim()
            if (text.isNotEmpty()) {
                elements.add(MarkdownElement.Header(level, text))
                i++
                continue
            }
        }

        // 4. Horizontal rule (---, ***, ___) — проверяем ДО списков, чтобы «- - -» и «* * *» не стали пунктами.
        if (HORIZONTAL_RULE_REGEX.matches(trimmed)) {
            elements.add(MarkdownElement.HorizontalRule)
            i++
            continue
        }

        // 5. Blockquotes: подряд идущие строки «>» объединяются в единую цитату.
        if (trimmed.startsWith(">")) {
            val quoteLines = mutableListOf<String>()
            while (i < lines.size && lines[i].trim().startsWith(">")) {
                val stripped = lines[i].trimStart().removePrefix(">").let {
                    if (it.startsWith(" ")) it.drop(1) else it
                }
                quoteLines.add(stripped)
                i++
            }
            val mergedQuote = quoteLines.joinToString("\n").trim()
            if (mergedQuote.isNotEmpty()) {
                elements.add(MarkdownElement.Blockquote(mergedQuote))
            }
            continue
        }

        // 6. Table (Markdown pipe table)
        if (isTableStart(lines, i)) {
            val nextTrimmed = lines[i + 1].trim()
            val headers = parseTableRow(line)
            val alignments = parseAlignments(nextTrimmed)
            val rows = mutableListOf<List<String>>()
            i += 2
            while (i < lines.size) {
                val rowLine = lines[i]
                val rowTrimmed = rowLine.trim()
                if (rowTrimmed.isEmpty() || !rowTrimmed.contains("|") || parseFenceOpen(rowTrimmed) != null) {
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

        // 7. Lists: маркеры (-, *, +), нумерация (1.), вложенность по отступу и чекбоксы (- [x] / - [ ]).
        if (isBulletMarker(trimmed) || NUMBERED_ITEM_REGEX.matches(trimmed)) {
            val listItems = mutableListOf<MarkdownElement>()
            val indentStack = mutableListOf<Int>()

            fun resolveIndentLevel(rawIndent: Int): Int {
                while (indentStack.isNotEmpty() && rawIndent < indentStack.last()) {
                    indentStack.removeAt(indentStack.lastIndex)
                }
                if (indentStack.isEmpty()) {
                    if (rawIndent > 0) {
                        indentStack.add(0)
                        indentStack.add(rawIndent)
                        return 1
                    }
                    indentStack.add(0)
                    return 0
                }
                if (rawIndent > indentStack.last()) {
                    indentStack.add(rawIndent)
                }
                return (indentStack.size - 1).coerceIn(0, 4)
            }

            while (i < lines.size) {
                val rawLine = lines[i]
                val currentTrimmed = rawLine.trim()
                if (currentTrimmed.isEmpty() || HORIZONTAL_RULE_REGEX.matches(currentTrimmed)) {
                    break
                }
                val rawIndent = leadingIndentWidth(rawLine)
                if (isBulletMarker(currentTrimmed)) {
                    val indentLevel = resolveIndentLevel(rawIndent)
                    val afterMarker = currentTrimmed.drop(2).trim()
                    val taskMatch = TASK_CHECKBOX_REGEX.matchEntire(afterMarker)
                    if (taskMatch != null) {
                        val checked = taskMatch.groupValues[1] != " "
                        val itemText = taskMatch.groupValues[2].trim()
                        listItems.add(MarkdownElement.BulletItem(itemText, indentLevel = indentLevel, checked = checked))
                    } else {
                        listItems.add(MarkdownElement.BulletItem(afterMarker, indentLevel = indentLevel))
                    }
                    i++
                } else {
                    val numMatch = NUMBERED_ITEM_REGEX.find(currentTrimmed)
                    if (numMatch != null) {
                        val indentLevel = resolveIndentLevel(rawIndent)
                        val number = numMatch.groupValues[1]
                        val itemText = numMatch.groupValues[2].trim()
                        listItems.add(MarkdownElement.NumberedItem(number, itemText, indentLevel = indentLevel))
                        i++
                    } else if (rawIndent >= 2 && listItems.isNotEmpty() && !isBlockBoundary(lines, i)) {
                        // Строка-продолжение с отступом относится к предыдущему пункту списка.
                        val prev = listItems.removeAt(listItems.lastIndex)
                        val updated = when (prev) {
                            is MarkdownElement.BulletItem -> prev.copy(text = prev.text + "\n" + currentTrimmed)
                            is MarkdownElement.NumberedItem -> prev.copy(text = prev.text + "\n" + currentTrimmed)
                            else -> prev
                        }
                        listItems.add(updated)
                        i++
                    } else {
                        break
                    }
                }
            }
            elements.add(MarkdownElement.ListBlock(listItems))
            continue
        }

        // 8. Отдельная ссылка или картинка: превращается в аккуратную карточку.
        val standaloneLink = parseStandaloneLinkCard(trimmed)
        if (standaloneLink != null) {
            elements.add(standaloneLink)
            i++
            continue
        }

        // 9. Обычный абзац: смежные строки без пустой строки объединяются в один блок
        // с сохранением переносов (без лишнего меж-абзацного зазора).
        val paragraphLines = mutableListOf<String>()
        while (i < lines.size) {
            if (paragraphLines.isNotEmpty() && isBlockBoundary(lines, i)) {
                break
            }
            val rawParaLine = lines[i]
            val cleanedLine = when {
                rawParaLine.endsWith("\\") && !rawParaLine.endsWith("\\\\") -> rawParaLine.dropLast(1).trimEnd()
                rawParaLine.endsWith("  ") -> rawParaLine.trimEnd()
                else -> rawParaLine
            }
            paragraphLines.add(cleanedLine)
            i++
        }
        val mergedParagraph = paragraphLines.joinToString("\n")
        if (mergedParagraph.isNotBlank()) {
            elements.add(MarkdownElement.Paragraph(mergedParagraph))
        }
    }

    return elements
}

/**
 * Блочный кэш для стрима. Готовые блоки (до последней пустой строки вне
 * code fence и LaTeX) разбираются один раз, на каждом обновлении заново
 * парсится только хвост. Это убирает пересборку всей простыни на каждом токене.
 */
internal class StreamingMarkdownCache {

    private var stablePrefix = ""
    private var stableElements: List<MarkdownElement> = emptyList()

    fun parse(rawText: String): List<MarkdownElement> {
        val text = rawText.replace("\r\n", "\n").replace('\r', '\n')
        if (!text.startsWith(stablePrefix)) {
            stablePrefix = ""
            stableElements = emptyList()
        }

        val boundary = findStableBoundary(text)
        if (boundary > stablePrefix.length) {
            val chunk = text.substring(stablePrefix.length, boundary)
            if (chunk.isNotBlank()) {
                stableElements = stableElements + parseMarkdown(chunk)
            }
            stablePrefix = text.substring(0, boundary)
        }

        val tail = text.substring(stablePrefix.length)
        return if (tail.isBlank()) stableElements else stableElements + parseMarkdown(tail)
    }

    /**
     * Индекс после последней пустой строки, которая находится вне code fence
     * и вне блочной LaTeX-формулы. Всё до него уже не изменится.
     */
    private fun findStableBoundary(text: String): Int {
        var activeFence: FenceSpec? = null
        var mathDelimiter: String? = null
        var lastBoundary = 0
        var lineStart = 0

        while (lineStart <= text.length) {
            val lineEnd = text.indexOf('\n', lineStart).let {
                if (it < 0) text.length else it
            }
            val trimmed = text.substring(lineStart, lineEnd).trim()

            when {
                activeFence != null -> {
                    if (isFenceClose(trimmed, activeFence)) activeFence = null
                }

                mathDelimiter != null -> {
                    if (trimmed.contains(mathDelimiter!!)) mathDelimiter = null
                }

                else -> {
                    val openedFence = parseFenceOpen(trimmed)
                    when {
                        openedFence != null -> activeFence = openedFence

                        trimmed.startsWith("$$") -> {
                            if (!trimmed.removePrefix("$$").contains("$$")) mathDelimiter = "$$"
                        }

                        trimmed.startsWith("\\[") -> {
                            if (!trimmed.contains("\\]")) mathDelimiter = "\\]"
                        }

                        trimmed.isEmpty() -> {
                            lastBoundary = (lineEnd + 1).coerceAtMost(text.length)
                        }
                    }
                }
            }

            if (lineEnd >= text.length) break
            lineStart = lineEnd + 1
        }
        return lastBoundary
    }
}

private val STANDALONE_MD_IMAGE = Regex("""^!\[([^\]]*)]\((https?://[^)\s]+)\)$""")
private val STANDALONE_MD_LINK = Regex("""^\[([^\]]+)]\((https?://[^)\s]+)\)$""")
private val STANDALONE_BARE_LINK = Regex("""^<?(https?://[^\s<>]+)>?$""")

/**
 * Строка целиком состоит из ссылки или изображения. Markdown-ссылка сохраняет свой текст
 * как заголовок, голая ссылка получает домен.
 */
private fun parseStandaloneLinkCard(line: String): MarkdownElement.LinkCard? {
    // Модель любит оборачивать ссылку в жирный или курсив. Снимаем
    // внешние маркеры, иначе строка не опознается как отдельная ссылка.
    val clean = line
        .removeSurrounding("**")
        .removeSurrounding("__")
        .removeSurrounding("*")
        .removeSurrounding("_")
        .trim()
    STANDALONE_MD_IMAGE.matchEntire(clean)?.let { match ->
        val url = match.groupValues[2].trim()
        if (url.isNotBlank()) {
            return MarkdownElement.LinkCard(
                url = url,
                title = match.groupValues[1].trim().takeIf { it.isNotBlank() },
                isImage = true
            )
        }
    }
    STANDALONE_MD_LINK.matchEntire(clean)?.let { match ->
        val url = match.groupValues[2].trim()
        if (url.isNotBlank()) {
            return MarkdownElement.LinkCard(
                url = url,
                title = match.groupValues[1].trim().takeIf { it.isNotBlank() }
            )
        }
    }
    STANDALONE_BARE_LINK.matchEntire(clean)?.let { match ->
        val url = trimUrlTail(match.groupValues[1])
        if (url.isNotBlank()) {
            return MarkdownElement.LinkCard(url = url)
        }
    }
    return null
}

/**
 * Убирает хвостовую пунктуацию вокруг ссылки, но не трогает закрывающие
 * скобки, которые принадлежат самому адресу (например, Wikipedia).
 */
internal fun trimUrlTail(raw: String): String {
    var end = raw.length
    while (end > 0) {
        val ch = raw[end - 1]
        when {
            ch == '.' || ch == ',' || ch == ';' || ch == ':' -> end--
            ch == ')' && raw.take(end).count { it == '(' } < raw.take(end).count { it == ')' } -> end--
            ch == ']' && raw.take(end).count { it == '[' } < raw.take(end).count { it == ']' } -> end--
            ch == '}' && raw.take(end).count { it == '{' } < raw.take(end).count { it == '}' } -> end--
            else -> break
        }
    }
    return raw.substring(0, end)
}

private const val MAX_INLINE_DEPTH = 6
private const val ESCAPABLE_MD_PUNCT = "\\`*_{}[]()#+-.!~$|"

private data class ParsedBracketLink(
    val label: String,
    val url: String,
    val endExclusive: Int
)

private fun findInlineCodeEnd(text: String, openIdx: Int, tickCount: Int): Int {
    val len = text.length
    var pos = openIdx + tickCount
    while (pos < len) {
        if (text[pos] == '`') {
            var run = 0
            while (pos + run < len && text[pos + run] == '`') run++
            if (run == tickCount) return pos
            pos += run
        } else {
            pos++
        }
    }
    return -1
}

private fun parseBracketLink(text: String, openBracketIdx: Int): ParsedBracketLink? {
    val len = text.length
    if (openBracketIdx >= len || text[openBracketIdx] != '[') return null
    var pos = openBracketIdx + 1
    var bracketDepth = 1
    while (pos < len && bracketDepth > 0) {
        val ch = text[pos]
        when {
            ch == '\\' && pos + 1 < len -> pos += 2
            ch == '`' -> {
                var ticks = 0
                while (pos + ticks < len && text[pos + ticks] == '`') ticks++
                val closeCode = findInlineCodeEnd(text, pos, ticks)
                pos = if (closeCode >= 0) closeCode + ticks else pos + ticks
            }
            ch == '[' -> {
                bracketDepth++
                pos++
            }
            ch == ']' -> {
                bracketDepth--
                if (bracketDepth == 0) break
                pos++
            }
            else -> pos++
        }
    }
    if (bracketDepth != 0 || pos >= len) return null
    val closeBracketIdx = pos
    if (closeBracketIdx + 1 >= len || text[closeBracketIdx + 1] != '(') return null

    var urlPos = closeBracketIdx + 2
    while (urlPos < len && text[urlPos].isWhitespace()) urlPos++
    val urlStart = urlPos
    var urlEnd = -1
    var parenDepth = 0
    var closeParenIdx = -1
    var inTitleQuote: Char? = null

    while (urlPos < len) {
        val ch = text[urlPos]
        if (inTitleQuote != null) {
            if (ch == '\\' && urlPos + 1 < len) {
                urlPos += 2
                continue
            }
            if (ch == inTitleQuote) inTitleQuote = null
            urlPos++
            continue
        }
        when {
            ch == '\\' && urlPos + 1 < len -> urlPos += 2
            ch == '(' -> {
                parenDepth++
                urlPos++
            }
            ch == ')' -> {
                if (parenDepth == 0) {
                    if (urlEnd < 0) urlEnd = urlPos
                    closeParenIdx = urlPos
                    break
                }
                parenDepth--
                urlPos++
            }
            ch.isWhitespace() && urlEnd < 0 -> {
                urlEnd = urlPos
                urlPos++
                while (urlPos < len && text[urlPos].isWhitespace()) urlPos++
                if (urlPos < len && (text[urlPos] == '"' || text[urlPos] == '\'')) {
                    inTitleQuote = text[urlPos]
                    urlPos++
                }
            }
            else -> urlPos++
        }
    }
    if (closeParenIdx < 0) return null
    val rawUrl = text.substring(urlStart, if (urlEnd >= urlStart) urlEnd else closeParenIdx)
        .trim()
        .removePrefix("<")
        .removeSuffix(">")
    if (rawUrl.isEmpty()) return null
    return ParsedBracketLink(
        label = text.substring(openBracketIdx + 1, closeBracketIdx),
        url = rawUrl,
        endExclusive = closeParenIdx + 1
    )
}

private fun findClosingDelimiter(
    text: String,
    openIdx: Int,
    delimiter: String,
    requireWordBoundary: Boolean
): Int {
    val len = text.length
    val dLen = delimiter.length
    val startIdx = openIdx + dLen
    if (startIdx >= len) return -1
    if (text[startIdx].isWhitespace()) return -1
    val dChar = delimiter[0]
    if (dLen == 1 && text[startIdx] == dChar) return -1
    if (requireWordBoundary && openIdx > 0 && text[openIdx - 1].isLetterOrDigit()) return -1

    var pos = startIdx
    while (pos <= len - dLen) {
        val ch = text[pos]
        if (ch == '\\' && pos + 1 < len) {
            pos += 2
            continue
        }
        if (ch == '`') {
            var ticks = 0
            while (pos + ticks < len && text[pos + ticks] == '`') ticks++
            val closeCode = findInlineCodeEnd(text, pos, ticks)
            pos = if (closeCode >= 0) closeCode + ticks else pos + ticks
            continue
        }
        if (ch == dChar && text.startsWith(delimiter, pos)) {
            var runEnd = pos
            while (runEnd < len && text[runEnd] == dChar) runEnd++
            val runLen = runEnd - pos
            // Для «**bold *italic***» внешний «**» закрывается на правом краю тройки «***»,
            // чтобы внутренний «*italic*» сохранил свою закрывающую звёздочку.
            val candidatePos = when {
                dLen == 1 && runLen > 1 -> -1
                dLen > 1 && runLen > dLen -> runEnd - dLen
                else -> pos
            }
            if (candidatePos > startIdx && !text[candidatePos - 1].isWhitespace()) {
                val afterIdx = candidatePos + dLen
                val boundaryOk = !requireWordBoundary || afterIdx >= len || !text[afterIdx].isLetterOrDigit()
                if (boundaryOk) return candidatePos
            }
            pos = runEnd
            continue
        }
        pos++
    }
    return -1
}

private fun AnnotatedString.Builder.appendInlineMarkdown(
    text: String,
    primaryColor: Color,
    secondaryColor: Color,
    depth: Int
) {
    val len = text.length
    var i = 0
    while (i < len) {
        val ch = text[i]

        // 1. Экранирование ASCII-пунктуации Markdown (\*, \_, \`, \[, \], \\ и т.д.),
        //    не трогает Windows-пути вроде C:\Users\name.
        if (ch == '\\' && i + 1 < len && text[i + 1] in ESCAPABLE_MD_PUNCT) {
            append(text[i + 1])
            i += 2
            continue
        }

        // 2. Инлайн-код (`code`, ``foo `bar` ``) — без искусственных пробелов по краям,
        //    чтобы не рвать пунктуацию и не портить копирование.
        if (ch == '`') {
            var ticks = 0
            while (i + ticks < len && text[i + ticks] == '`') ticks++
            val closeIdx = findInlineCodeEnd(text, i, ticks)
            if (closeIdx >= 0) {
                var code = text.substring(i + ticks, closeIdx)
                if (code.length >= 2 && code.startsWith(' ') && code.endsWith(' ') && code.any { it != ' ' }) {
                    code = code.substring(1, code.length - 1)
                }
                pushStyle(
                    SpanStyle(
                        fontFamily = RobotoMono,
                        background = primaryColor.copy(alpha = 0.13f),
                        color = primaryColor,
                        fontSize = TypeScale.label
                    )
                )
                append(code)
                pop()
                i = closeIdx + ticks
                continue
            }
            append(text, i, i + ticks)
            i += ticks
            continue
        }

        // 3. Встроенное изображение ![alt](url): без висящего «!» и без лишних сетевых загрузок.
        if (ch == '!' && i + 1 < len && text[i + 1] == '[') {
            val parsed = parseBracketLink(text, i + 1)
            if (parsed != null) {
                val label = parsed.label.trim().ifBlank { linkHost(parsed.url).ifBlank { "Изображение" } }
                if (isWebUrl(parsed.url)) {
                    val start = length
                    pushStyle(
                        SpanStyle(
                            color = primaryColor,
                            textDecoration = TextDecoration.Underline
                        )
                    )
                    append(label)
                    pop()
                    addStringAnnotation("URL", parsed.url, start, length)
                } else {
                    pushStyle(SpanStyle(color = secondaryColor, fontStyle = FontStyle.Italic))
                    append(label)
                    pop()
                }
                i = parsed.endExclusive
                continue
            }
        }

        // 4. Markdown-ссылка [label](url) с поддержкой вложенного форматирования внутри label.
        if (ch == '[') {
            val parsed = parseBracketLink(text, i)
            if (parsed != null) {
                if (isWebUrl(parsed.url)) {
                    val start = length
                    pushStyle(
                        SpanStyle(
                            color = primaryColor,
                            textDecoration = TextDecoration.Underline
                        )
                    )
                    if (depth < MAX_INLINE_DEPTH) {
                        appendInlineMarkdown(parsed.label, primaryColor, secondaryColor, depth + 1)
                    } else {
                        append(parsed.label)
                    }
                    pop()
                    addStringAnnotation("URL", parsed.url, start, length)
                } else {
                    // file://, javascript: и прочие схемы не должны становиться кликабельными.
                    if (depth < MAX_INLINE_DEPTH) {
                        appendInlineMarkdown(parsed.label, primaryColor, secondaryColor, depth + 1)
                    } else {
                        append(parsed.label)
                    }
                }
                i = parsed.endExclusive
                continue
            }
        }

        // 5. Автоссылка <https://...> или голый URL https://... / http://...
        if ((ch == '<' && (text.startsWith("<https://", i, ignoreCase = true) || text.startsWith("<http://", i, ignoreCase = true))) ||
            ((ch == 'h' || ch == 'H') &&
                (i == 0 || !text[i - 1].isLetterOrDigit()) &&
                (text.startsWith("https://", i, ignoreCase = true) || text.startsWith("http://", i, ignoreCase = true)))
        ) {
            val isAngle = ch == '<'
            var end = i + (if (isAngle) 1 else 0)
            while (end < len && !text[end].isWhitespace() && text[end] != '<' && text[end] != '>') {
                end++
            }
            val closedAngle = isAngle && end < len && text[end] == '>'
            val rawUrl = text.substring(i + (if (isAngle) 1 else 0), end)
            if (!isAngle || closedAngle) {
                val url = trimUrlTail(rawUrl)
                val tail = rawUrl.substring(url.length)
                if (isWebUrl(url)) {
                    val start = length
                    pushStyle(
                        SpanStyle(
                            color = primaryColor,
                            textDecoration = TextDecoration.Underline
                        )
                    )
                    append(prettyLinkLabel(url))
                    pop()
                    addStringAnnotation("URL", url, start, length)
                    append(tail)
                    i = if (closedAngle) end + 1 else end
                    continue
                }
            }
        }

        // 6. Вложенное форматирование: ***, ___, **, __, ~~, *, _
        if (depth < MAX_INLINE_DEPTH) {
            // Bold + Italic: ***...*** / ___...___
            if ((ch == '*' && text.startsWith("***", i)) || (ch == '_' && text.startsWith("___", i))) {
                val delim = if (ch == '*') "***" else "___"
                val closeIdx = findClosingDelimiter(text, i, delim, requireWordBoundary = ch == '_')
                if (closeIdx >= 0) {
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic))
                    appendInlineMarkdown(text.substring(i + 3, closeIdx), primaryColor, secondaryColor, depth + 1)
                    pop()
                    i = closeIdx + 3
                    continue
                }
            }

            // Bold: **...** / __...__ (подчёркивания — строго по границам слов, безопасно для snake_case)
            if ((ch == '*' && text.startsWith("**", i)) || (ch == '_' && text.startsWith("__", i))) {
                val delim = if (ch == '*') "**" else "__"
                val closeIdx = findClosingDelimiter(text, i, delim, requireWordBoundary = ch == '_')
                if (closeIdx >= 0) {
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                    appendInlineMarkdown(text.substring(i + 2, closeIdx), primaryColor, secondaryColor, depth + 1)
                    pop()
                    i = closeIdx + 2
                    continue
                }
            }

            // Strikethrough: ~~...~~
            if (ch == '~' && text.startsWith("~~", i)) {
                val closeIdx = findClosingDelimiter(text, i, "~~", requireWordBoundary = false)
                if (closeIdx >= 0) {
                    pushStyle(SpanStyle(textDecoration = TextDecoration.LineThrough))
                    appendInlineMarkdown(text.substring(i + 2, closeIdx), primaryColor, secondaryColor, depth + 1)
                    pop()
                    i = closeIdx + 2
                    continue
                }
            }

            // Italic: *...* / _..._ (безопасно для «2 * 3 * 4» и «full_unlock»)
            if (ch == '*' || ch == '_') {
                val delim = ch.toString()
                val closeIdx = findClosingDelimiter(text, i, delim, requireWordBoundary = ch == '_')
                if (closeIdx >= 0) {
                    pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                    appendInlineMarkdown(text.substring(i + 1, closeIdx), primaryColor, secondaryColor, depth + 1)
                    pop()
                    i = closeIdx + 1
                    continue
                }
            }
        }

        // 7. Inline LaTeX ($formula$) — защищён от цен вида «$5 и $10».
        if (ch == '$' && i + 1 < len && text[i + 1] != '$' && !text[i + 1].isWhitespace()) {
            var closeIdx = -1
            var scan = i + 1
            while (scan < len) {
                if (text[scan] == '\\' && scan + 1 < len) {
                    scan += 2
                    continue
                }
                if (text[scan] == '$') {
                    if (scan > i + 1 && !text[scan - 1].isWhitespace() &&
                        (scan + 1 >= len || (text[scan + 1] != '$' && !text[scan + 1].isDigit()))
                    ) {
                        closeIdx = scan
                        break
                    }
                }
                scan++
            }
            if (closeIdx >= 0) {
                val rawFormula = text.substring(i + 1, closeIdx)
                pushStyle(
                    SpanStyle(
                        fontFamily = RobotoMono,
                        color = secondaryColor,
                        fontWeight = FontWeight.Medium
                    )
                )
                append(formatMathFallback(rawFormula))
                pop()
                i = closeIdx + 1
                continue
            }
        }

        append(ch)
        i++
    }
}

/**
 * Formats inline Markdown with nesting support: ***bold italic***, **bold**, __bold__,
 * *italic*, _italic_ (safe for snake_case), `code` (including multi-backtick spans without
 * extra padding spaces), ~~strikethrough~~, escapes, links, images, and inline math ($x = y$).
 */
fun renderInlineMarkdown(
    text: String,
    primaryColor: Color = Color(0xFF90CAF9),
    secondaryColor: Color = Color(0xFF80CBC4),
    showCursor: Boolean = false
): AnnotatedString {
    val sanitized = sanitizeMarkdown(text)
    return buildAnnotatedString {
        appendInlineMarkdown(
            text = sanitized,
            primaryColor = primaryColor,
            secondaryColor = secondaryColor,
            depth = 0
        )
        if (showCursor) {
            appendInlineContent(CURSOR_INLINE_ID, "▋")
        }
    }
}

private val MATH_SYMBOL_MAP: Map<String, String> = mapOf(
    // Греческий алфавит (строчные)
    "alpha" to "α", "beta" to "β", "gamma" to "γ", "delta" to "δ",
    "epsilon" to "ε", "varepsilon" to "ε", "zeta" to "ζ", "eta" to "η",
    "theta" to "θ", "vartheta" to "ϑ", "iota" to "ι", "kappa" to "κ",
    "lambda" to "λ", "mu" to "μ", "nu" to "ν", "xi" to "ξ",
    "pi" to "π", "varpi" to "ϖ", "rho" to "ρ", "varrho" to "ϱ",
    "sigma" to "σ", "varsigma" to "ς", "tau" to "τ", "upsilon" to "υ",
    "phi" to "φ", "varphi" to "φ", "chi" to "χ", "psi" to "ψ", "omega" to "ω",
    // Греческий алфавит (заглавные)
    "Gamma" to "Γ", "Delta" to "Δ", "Theta" to "Θ", "Lambda" to "Λ",
    "Xi" to "Ξ", "Pi" to "Π", "Sigma" to "Σ", "Upsilon" to "Υ",
    "Phi" to "Φ", "Psi" to "Ψ", "Omega" to "Ω",
    // Операторы, отношения, стрелки, логика и множества
    "pm" to "±", "mp" to "∓", "times" to "×", "div" to "÷",
    "cdot" to "·", "ast" to "∗", "star" to "⋆", "circ" to "∘",
    "bullet" to "•", "oplus" to "⊕", "otimes" to "⊗",
    "le" to "≤", "leq" to "≤", "leqslant" to "≤",
    "ge" to "≥", "geq" to "≥", "geqslant" to "≥",
    "ne" to "≠", "neq" to "≠",
    "approx" to "≈", "equiv" to "≡", "sim" to "∼", "simeq" to "≃",
    "cong" to "≅", "propto" to "∝", "ll" to "≪", "gg" to "≫",
    "to" to "→", "rightarrow" to "→", "longrightarrow" to "→",
    "leftarrow" to "←", "longleftarrow" to "←", "leftrightarrow" to "↔",
    "mapsto" to "↦", "Rightarrow" to "⇒", "Longrightarrow" to "⇒",
    "Leftarrow" to "⇐", "Leftrightarrow" to "⇔", "iff" to "⇔", "implies" to "⇒",
    "forall" to "∀", "exists" to "∃", "nexists" to "∄",
    "in" to "∈", "notin" to "∉", "ni" to "∋",
    "emptyset" to "∅", "varnothing" to "∅",
    "subset" to "⊂", "subseteq" to "⊆", "supset" to "⊃", "supseteq" to "⊇",
    "cup" to "∪", "cap" to "∩", "setminus" to "∖",
    "land" to "∧", "wedge" to "∧", "lor" to "∨", "vee" to "∨",
    "neg" to "¬", "lnot" to "¬",
    "infty" to "∞", "partial" to "∂", "nabla" to "∇",
    "sum" to "∑", "prod" to "∏", "coprod" to "∐",
    "int" to "∫", "iint" to "∬", "iiint" to "∭", "oint" to "∮",
    "cdots" to "⋯", "ldots" to "…", "dots" to "…", "vdots" to "⋮", "ddots" to "⋱",
    "angle" to "∠", "perp" to "⊥", "parallel" to "∥", "triangle" to "△",
    "degree" to "°", "prime" to "′",
    "langle" to "⟨", "rangle" to "⟩",
    "lceil" to "⌈", "rceil" to "⌉", "lfloor" to "⌊", "rfloor" to "⌋",
    "quad" to "  ", "qquad" to "   "
)

private val MATH_OPERATOR_NAMES: Set<String> = setOf(
    "sin", "cos", "tan", "cot", "sec", "csc",
    "arcsin", "arccos", "arctan", "sinh", "cosh", "tanh",
    "log", "ln", "exp", "lim", "limsup", "liminf",
    "sup", "inf", "max", "min", "arg", "deg", "det",
    "dim", "gcd", "hom", "ker", "Pr", "mod"
)

private val MATH_NOOP_COMMANDS: Set<String> = setOf(
    "displaystyle", "textstyle", "scriptstyle", "scriptscriptstyle",
    "limits", "nolimits", "nonumber", "notag",
    "big", "Big", "bigg", "Bigg",
    "bigl", "bigr", "Bigl", "Bigr", "biggl", "biggr", "Biggl", "Biggr",
    "middle"
)

private val MATH_BLACKBOARD_MAP: Map<Char, String> = mapOf(
    'R' to "ℝ", 'N' to "ℕ", 'Z' to "ℤ", 'Q' to "ℚ",
    'C' to "ℂ", 'P' to "ℙ", 'E' to "𝔼", 'H' to "ℍ"
)

private val SUPERSCRIPT_MAP: Map<Char, Char> = mapOf(
    '0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴',
    '5' to '⁵', '6' to '⁶', '7' to '⁷', '8' to '⁸', '9' to '⁹',
    '+' to '⁺', '-' to '⁻', '=' to '⁼', '(' to '⁽', ')' to '⁾',
    'n' to 'ⁿ', 'i' to 'ⁱ'
)

private val SUBSCRIPT_MAP: Map<Char, Char> = mapOf(
    '0' to '₀', '1' to '₁', '2' to '₂', '3' to '₃', '4' to '₄',
    '5' to '₅', '6' to '₆', '7' to '₇', '8' to '₈', '9' to '₉',
    '+' to '₊', '-' to '₋', '=' to '₌', '(' to '₍', ')' to '₎',
    'a' to 'ₐ', 'e' to 'ₑ', 'i' to 'ᵢ', 'j' to 'ⱼ', 'k' to 'ₖ',
    'm' to 'ₘ', 'n' to 'ₙ', 'o' to 'ₒ', 'r' to 'ᵣ', 'x' to 'ₓ'
)

private val MATH_MULTI_SPACE_REGEX = Regex("[ \\t]{2,}")

/**
 * Преобразует LaTeX-формулу в читаемый Unicode-текст для inline-математики,
 * стриминга и офлайн-фолбэка (без WebView/KaTeX).
 */
internal fun formatMathFallback(latex: String): String {
    if (latex.isBlank()) return ""
    val raw = formatMathExpr(latex.trim(), depth = 0)
    return raw
        .split('\n')
        .joinToString("\n") { line ->
            MATH_MULTI_SPACE_REGEX.replace(line, " ").trim()
        }
        .trim()
}

private fun formatMathExpr(input: String, depth: Int): String {
    if (input.isEmpty() || depth > 12) return input
    val sb = StringBuilder(input.length)
    var i = 0
    val len = input.length

    while (i < len) {
        val ch = input[i]

        if (ch == '\\') {
            if (i + 1 >= len) {
                i++
                continue
            }
            val next = input[i + 1]
            if (!next.isLetter()) {
                when (next) {
                    '\\' -> sb.append('\n')
                    '{' -> sb.append('{')
                    '}' -> sb.append('}')
                    '|' -> sb.append('‖')
                    '%', '$', '_', '#', '&' -> sb.append(next)
                    ',', ';', ':', ' ' -> sb.append(' ')
                    '!' -> Unit
                    else -> sb.append(next)
                }
                i += 2
                continue
            }

            var cmdEnd = i + 1
            while (cmdEnd < len && input[cmdEnd].isLetter()) cmdEnd++
            val cmd = input.substring(i + 1, cmdEnd)
            i = cmdEnd

            when (cmd) {
                "frac", "dfrac", "tfrac", "cfrac" -> {
                    val numGroup = readMathBraceGroup(input, i)
                    if (numGroup != null) {
                        val denGroup = readMathBraceGroup(input, numGroup.second)
                        val num = formatMathExpr(numGroup.first, depth + 1).trim()
                        if (denGroup != null) {
                            val den = formatMathExpr(denGroup.first, depth + 1).trim()
                            val numFormatted = if (needsMathGroupParens(num, isDenominator = false)) "($num)" else num
                            val denFormatted = if (needsMathGroupParens(den, isDenominator = true)) "($den)" else den
                            sb.append(numFormatted).append('/').append(denFormatted)
                            i = denGroup.second
                        } else {
                            sb.append(num)
                            i = numGroup.second
                        }
                    }
                }

                "sqrt" -> {
                    var scan = skipHorizontalSpaces(input, i)
                    var degree: String? = null
                    if (scan < len && input[scan] == '[') {
                        val closeBracket = input.indexOf(']', scan + 1)
                        if (closeBracket > scan) {
                            degree = input.substring(scan + 1, closeBracket)
                            scan = closeBracket + 1
                        }
                    }
                    val rootPrefix = when (degree?.trim()) {
                        null, "", "2" -> "√"
                        "3" -> "∛"
                        "4" -> "∜"
                        else -> "${formatMathScript(degree, isSuper = true, depth = depth + 1)}√"
                    }
                    val argGroup = readMathBraceGroup(input, scan)
                    if (argGroup != null) {
                        val inner = formatMathExpr(argGroup.first, depth + 1).trim()
                        if (inner.isEmpty()) {
                            sb.append(rootPrefix)
                        } else if (inner.length == 1 || inner.all { it.isDigit() }) {
                            sb.append(rootPrefix).append(inner)
                        } else {
                            sb.append(rootPrefix).append('(').append(inner).append(')')
                        }
                        i = argGroup.second
                    } else {
                        sb.append(rootPrefix)
                        i = scan
                    }
                }

                "text", "textrm", "textbf", "textit", "mbox" -> {
                    val grp = readMathBraceGroup(input, i)
                    if (grp != null) {
                        sb.append(grp.first)
                        i = grp.second
                    }
                }

                "mathrm", "mathbf", "mathit", "mathsf", "mathtt", "operatorname", "mathcal" -> {
                    val grp = readMathBraceGroup(input, i)
                    if (grp != null) {
                        sb.append(formatMathExpr(grp.first, depth + 1))
                        i = grp.second
                    }
                }

                "mathbb" -> {
                    val grp = readMathBraceGroup(input, i)
                    if (grp != null) {
                        for (c in grp.first) {
                            sb.append(MATH_BLACKBOARD_MAP[c] ?: c.toString())
                        }
                        i = grp.second
                    }
                }

                "binom" -> {
                    val nGrp = readMathBraceGroup(input, i)
                    val kGrp = if (nGrp != null) readMathBraceGroup(input, nGrp.second) else null
                    if (nGrp != null && kGrp != null) {
                        val n = formatMathExpr(nGrp.first, depth + 1).trim()
                        val k = formatMathExpr(kGrp.first, depth + 1).trim()
                        sb.append("C(").append(n).append(", ").append(k).append(')')
                        i = kGrp.second
                    }
                }

                "overline", "bar", "hat", "widehat", "tilde", "widetilde", "vec" -> {
                    val grp = readMathBraceGroup(input, i)
                    if (grp != null) {
                        val inner = formatMathExpr(grp.first, depth + 1).trim()
                        if (inner.length == 1) {
                            val combining = when (cmd) {
                                "overline", "bar" -> '\u0304'
                                "hat", "widehat" -> '\u0302'
                                "tilde", "widetilde" -> '\u0303'
                                else -> '\u20D7'
                            }
                            sb.append(inner).append(combining)
                        } else {
                            val label = when (cmd) {
                                "overline", "bar" -> "bar"
                                "hat", "widehat" -> "hat"
                                "tilde", "widetilde" -> "tilde"
                                else -> "vec"
                            }
                            sb.append(label).append('(').append(inner).append(')')
                        }
                        i = grp.second
                    }
                }

                "left", "right" -> {
                    val scan = skipHorizontalSpaces(input, i)
                    if (scan < len && input[scan] == '.') {
                        i = scan + 1
                    }
                }

                "begin", "end", "color" -> {
                    val grp = readMathBraceGroup(input, i)
                    if (grp != null) {
                        i = grp.second
                    }
                }

                "bmod", "pmod" -> sb.append("mod")

                else -> {
                    val mapped = MATH_SYMBOL_MAP[cmd]
                    when {
                        mapped != null -> sb.append(mapped)
                        cmd in MATH_OPERATOR_NAMES -> sb.append(cmd)
                        cmd in MATH_NOOP_COMMANDS -> Unit
                        else -> sb.append('\\').append(cmd)
                    }
                }
            }
            continue
        }

        if (ch == '^' || ch == '_') {
            val isSuper = ch == '^'
            val arg = readMathScriptArg(input, i + 1)
            if (arg != null) {
                sb.append(formatMathScript(arg.first, isSuper = isSuper, depth = depth + 1))
                i = arg.second
            } else {
                sb.append(ch)
                i++
            }
            continue
        }

        if (ch == '{') {
            val grp = readMathBraceGroup(input, i)
            if (grp != null) {
                sb.append(formatMathExpr(grp.first, depth + 1))
                i = grp.second
                continue
            }
        }

        if (ch == '&') {
            sb.append(' ')
            i++
            continue
        }

        sb.append(ch)
        i++
    }

    return sb.toString()
}

private fun skipHorizontalSpaces(input: String, start: Int): Int {
    var pos = start
    while (pos < input.length && (input[pos] == ' ' || input[pos] == '\t')) {
        pos++
    }
    return pos
}

private fun readMathBraceGroup(input: String, start: Int): Pair<String, Int>? {
    val pos = skipHorizontalSpaces(input, start)
    if (pos >= input.length || input[pos] != '{') return null
    var depth = 0
    var idx = pos
    while (idx < input.length) {
        if (input[idx] == '\\' && idx + 1 < input.length) {
            idx += 2
            continue
        }
        when (input[idx]) {
            '{' -> depth++
            '}' -> {
                depth--
                if (depth == 0) {
                    return input.substring(pos + 1, idx) to (idx + 1)
                }
            }
        }
        idx++
    }
    // Незакрытая группа (например, во время стриминга) — отдаём хвост как есть
    return input.substring(pos + 1) to input.length
}

private fun readMathScriptArg(input: String, start: Int): Pair<String, Int>? {
    val pos = skipHorizontalSpaces(input, start)
    if (pos >= input.length) return null
    if (input[pos] == '{') {
        return readMathBraceGroup(input, pos)
    }
    if (input[pos] == '\\' && pos + 1 < input.length && input[pos + 1].isLetter()) {
        var end = pos + 1
        while (end < input.length && input[end].isLetter()) end++
        return input.substring(pos, end) to end
    }
    return input.substring(pos, pos + 1) to (pos + 1)
}

private fun formatMathScript(raw: String, isSuper: Boolean, depth: Int): String {
    val formatted = formatMathExpr(raw, depth).trim()
    if (formatted.isEmpty()) return ""
    val map = if (isSuper) SUPERSCRIPT_MAP else SUBSCRIPT_MAP
    if (formatted.all { it in map }) {
        val sb = StringBuilder(formatted.length)
        for (c in formatted) {
            sb.append(map[c])
        }
        return sb.toString()
    }
    val prefix = if (isSuper) "^" else "_"
    return if (formatted.length == 1) "$prefix$formatted" else "$prefix($formatted)"
}

private fun needsMathGroupParens(expr: String, isDenominator: Boolean): Boolean {
    val trimmed = expr.trim()
    if (trimmed.length <= 1) return false
    if (trimmed.startsWith('(') && trimmed.endsWith(')')) {
        var depth = 0
        var enclosesAll = true
        for (idx in trimmed.indices) {
            when (trimmed[idx]) {
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0 && idx < trimmed.lastIndex) {
                        enclosesAll = false
                        break
                    }
                }
            }
        }
        if (enclosesAll && depth == 0) return false
    }
    if (trimmed.any { ch ->
            ch == ' ' || ch == '+' || ch == '-' || ch == '−' || ch == '=' ||
                ch == '/' || ch == '×' || ch == '·' || ch == '±' || ch == ',' || ch == '\n'
        }
    ) {
        return true
    }
    if (isDenominator && trimmed[0].isDigit() && trimmed.any { !it.isDigit() && it != '.' }) {
        return true
    }
    return false
}
