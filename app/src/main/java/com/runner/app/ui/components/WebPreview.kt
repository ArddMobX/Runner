package com.runner.app.ui.components

import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.json.JSONObject

/**
 * Источник превью: либо разметка прямо из сообщения, либо файл на диске,
 * который агент только что создал тулом write_file.
 */
data class WebPreviewSource(
    val title: String,
    val html: String? = null,
    val filePath: String? = null
)

private val FENCED_BLOCK = Regex("```([A-Za-z0-9+#.-]*)[ \\t]*\\r?\\n([\\s\\S]*?)```")

private val WEB_EXTENSIONS = listOf(".html", ".htm", ".svg")

private fun isSvgMarkup(body: String): Boolean =
    body.trimStart().startsWith("<svg", ignoreCase = true)

private fun isHtmlMarkup(body: String): Boolean {
    val head = body.trimStart()
    return head.startsWith("<!doctype", ignoreCase = true) ||
            head.startsWith("<html", ignoreCase = true)
}

/**
 * Ищет в ответе модели блок с HTML или SVG. Отдельно ловит случай, когда модель
 * выдала разметку вообще без ограждения — тогда смотрим на начало текста.
 */
fun findWebPreviewInMarkdown(text: String): WebPreviewSource? {
    for (match in FENCED_BLOCK.findAll(text)) {
        val language = match.groupValues[1].lowercase()
        val body = match.groupValues[2].trim()
        if (body.isEmpty()) continue

        val svg = language == "svg" || isSvgMarkup(body)
        val html = language == "html" || language == "htm" || isHtmlMarkup(body)
        if (svg || html) {
            return WebPreviewSource(
                title = if (svg) "SVG" else "HTML",
                html = buildPreviewHtml(body, svg)
            )
        }
    }

    val trimmed = text.trimStart()
    if (trimmed.length > 120 && (isHtmlMarkup(trimmed) || isSvgMarkup(trimmed))) {
        val svg = isSvgMarkup(trimmed)
        return WebPreviewSource(
            title = if (svg) "SVG" else "HTML",
            html = buildPreviewHtml(trimmed, svg)
        )
    }
    return null
}

/** Превью для файла, который агент записал через write_file. */
fun findWebPreviewInToolCall(
    toolName: String,
    args: String?,
    output: String?
): WebPreviewSource? {
    if (toolName != "write_file") return null

    val declared = try {
        if (args.isNullOrBlank()) null else JSONObject(args).optString("path").takeIf { it.isNotBlank() }
    } catch (e: Exception) {
        null
    } ?: return null

    if (WEB_EXTENSIONS.none { declared.endsWith(it, ignoreCase = true) }) return null

    // Абсолютный путь надёжнее брать из resolved_path: в аргументах он может быть относительным
    val resolved = output
        ?.lineSequence()
        ?.map { it.trim() }
        ?.firstOrNull { it.startsWith("resolved_path") }
        ?.substringAfter(':', "")
        ?.trim()
        ?.takeIf { it.isNotBlank() }

    val absolute = resolved ?: declared
    if (!absolute.startsWith("/")) return null

    return WebPreviewSource(
        title = absolute.substringAfterLast('/'),
        filePath = absolute
    )
}

/**
 * Оборачивает разметку в каркас под мобильный экран: без viewport страница
 * рендерится в десктопной ширине и выглядит как уменьшенная простыня.
 */
private fun buildPreviewHtml(body: String, svg: Boolean): String {
    if (svg) {
        return """
            <!DOCTYPE html>
            <html><head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <style>
              html, body { margin: 0; padding: 0; height: 100%; background: #0A0A0C; }
              body { display: flex; align-items: center; justify-content: center; }
              svg { max-width: 94vw; max-height: 94vh; width: auto; height: auto; }
            </style>
            </head><body>$body</body></html>
        """.trimIndent()
    }

    if (isHtmlMarkup(body)) {
        // Своя разметка есть — добавляем только viewport, если его не поставили
        if (body.contains("name=\"viewport\"", ignoreCase = true)) return body
        val headOpen = Regex("<head[^>]*>", RegexOption.IGNORE_CASE).find(body)
        return if (headOpen != null) {
            body.substring(0, headOpen.range.last + 1) +
                    "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">" +
                    body.substring(headOpen.range.last + 1)
        } else {
            body
        }
    }

    return """
        <!DOCTYPE html>
        <html><head>
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <style>
          body { background: #0A0A0C; color: #EDEDEF; font-family: sans-serif;
                 margin: 12px; font-size: 15px; }
        </style>
        </head><body>$body</body></html>
    """.trimIndent()
}

/** Компактная кнопка «Открыть превью» под сообщением. */
@Composable
fun PreviewButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
            .border(
                BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                shape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Outlined.PlayCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(17.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            color = MaterialTheme.colorScheme.primary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Плавающее окно с живым превью. WebView создаётся один раз и уничтожается
 * при закрытии: иначе он держит ссылку на активити и течёт.
 */
@Composable
fun WebPreviewDialog(
    source: WebPreviewSource,
    onDismiss: () -> Unit
) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    var loading by remember { mutableStateOf(true) }
    var reloadToken by remember { mutableStateOf(0) }

    DisposableEffect(Unit) {
        onDispose {
            webView?.stopLoading()
            webView?.destroy()
            webView = null
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.88f)
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.PlayCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = source.title,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (source.filePath != null) "файл на устройстве" else "из сообщения",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }
                    IconButton(
                        onClick = { reloadToken++ },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = "Перезагрузить",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Закрыть",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    thickness = 0.5.dp
                )

                Box(modifier = Modifier.weight(1f)) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { context ->
                            WebView(context).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.useWideViewPort = true
                                settings.loadWithOverviewMode = true
                                settings.builtInZoomControls = true
                                settings.displayZoomControls = false
                                // Доступ к файлам нужен только когда грузим файл с диска;
                                // для разметки из сообщения он выключен
                                settings.allowFileAccess = source.filePath != null
                                settings.allowContentAccess = false
                                setBackgroundColor(0xFF0A0A0C.toInt())
                                webViewClient = object : WebViewClient() {
                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        loading = false
                                    }
                                }
                                webChromeClient = WebChromeClient()
                                load(source)
                                // 0 совпадает со стартовым reloadToken, чтобы первый
                                // update не перезагружал страницу второй раз
                                tag = 0
                                webView = this
                            }
                        },
                        update = { view ->
                            // Перезагрузка по кнопке: обычная перерисовка не должна
                            // заново дёргать страницу
                            if (view.tag != reloadToken) {
                                view.tag = reloadToken
                                loading = true
                                view.load(source)
                            }
                        }
                    )

                    if (loading) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(28.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

private fun WebView.load(source: WebPreviewSource) {
    val path = source.filePath
    if (path != null) {
        loadUrl("file://$path")
    } else {
        // null как baseUrl: страница получает изолированный origin и не тянет
        // относительные ресурсы с диска
        loadDataWithBaseURL(null, source.html.orEmpty(), "text/html", "UTF-8", null)
    }
}
