package com.runner.app.tools

import com.runner.app.data.WebSource
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.URI
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Веб-поиск и чтение страниц для агента.
 *
 * По умолчанию поиск идёт через HTML-выдачу DuckDuckGo без ключа. Если в
 * настройках задан ключ Tavily, используется он: результаты чище, а текст
 * страниц уже приходит в LLM-ready виде.
 *
 * Чтение страниц: только http/https, только внешние адреса, лимит размера,
 * ручные редиректы с повторной проверкой. Всё, что вернулось, считается
 * недоверенным текстом и оборачивается вызывающим кодом.
 */
object WebTools {

    private const val MAX_RESULTS = 8
    private const val MAX_PAGE_BYTES = 3_000_000L
    private const val MAX_TEXT_CHARS = 9_000
    private const val SEARCH_TTL_MS = 5 * 60 * 1000L
    private const val FETCH_TTL_MS = 10 * 60 * 1000L
    private const val MAX_REDIRECTS = 4

    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private class CacheEntry<T>(val storedAt: Long, val value: T)

    private val searchCache = ConcurrentHashMap<String, CacheEntry<List<WebSource>>>()
    private val fetchCache = ConcurrentHashMap<String, CacheEntry<WebToolResult>>()

    data class WebToolResult(
        val text: String,
        val sources: List<WebSource> = emptyList()
    )

    /**
     * Ищет в интернете. Возвращает текст для модели и список источников для UI.
     */
    fun search(query: String, limit: Int, apiKey: String): WebToolResult {
        val cleanQuery = query.trim()
        if (cleanQuery.isEmpty()) {
            return WebToolResult("Пустой поисковый запрос. Уточни, что именно нужно найти.")
        }
        if (cleanQuery.length > 300) {
            return WebToolResult("Слишком длинный поисковый запрос (${cleanQuery.length} символов). Сократи его.")
        }

        val count = limit.coerceIn(1, MAX_RESULTS)
        val provider = if (apiKey.isNotBlank()) "tavily" else "ddg"
        val cacheKey = "$provider|$count|${cleanQuery.lowercase(Locale.ROOT)}"
        searchCache[cacheKey]
            ?.takeIf { System.currentTimeMillis() - it.storedAt < SEARCH_TTL_MS }
            ?.let { return formatSearch(cleanQuery, it.value) }

        val results = try {
            if (apiKey.isNotBlank()) {
                // Ключ мог протухнуть или упереться в лимит: тогда не оставляем
                // пользователя без поиска, а тихо откатываемся на DuckDuckGo.
                try {
                    searchTavily(cleanQuery, count, apiKey)
                } catch (tavilyError: Exception) {
                    try {
                        searchDuckDuckGo(cleanQuery, count)
                    } catch (_: Exception) {
                        throw tavilyError
                    }
                }
            } else {
                searchDuckDuckGo(cleanQuery, count)
            }
        } catch (e: Exception) {
            return WebToolResult(
                "Не удалось выполнить поиск: ${e.message ?: e.javaClass.simpleName}. " +
                        "Попробуй ещё раз или открой страницу напрямую через web_fetch."
            )
        }

        if (results.isEmpty()) {
            return WebToolResult(
                "Поиск не дал результатов. DuckDuckGo мог заблокировать запрос: " +
                        "в этом случае добавь ключ Tavily в Настройки → Веб-поиск " +
                        "или открой известный адрес напрямую через web_fetch."
            )
        }

        searchCache[cacheKey] = CacheEntry(System.currentTimeMillis(), results)
        return formatSearch(cleanQuery, results)
    }

    /**
     * Скачивает страницу и вытаскивает основной текст.
     */
    fun fetch(rawUrl: String): WebToolResult {
        val url = try {
            WebPageParser.validateFetchUrl(rawUrl)
        } catch (e: IllegalArgumentException) {
            return WebToolResult("Не удалось открыть страницу: ${e.message}.")
        }

        fetchCache[url]
            ?.takeIf { System.currentTimeMillis() - it.storedAt < FETCH_TTL_MS }
            ?.let { return it.value }

        val page = try {
            httpGet(url)
        } catch (e: Exception) {
            return WebToolResult(
                "Не удалось открыть $url: ${e.message ?: e.javaClass.simpleName}."
            )
        }

        if (!isTextual(page.contentType)) {
            return WebToolResult(
                "По адресу $url не текстовая страница (${page.contentType ?: "неизвестный тип"}). " +
                        "Скачивать файлы этим инструментом нельзя."
            )
        }

        val isJson = page.contentType.substringBefore(';').trim()
            .equals("application/json", ignoreCase = true)
        val extracted = if (isJson) {
            WebPageParser.ExtractedPage(
                title = "",
                text = String(page.bytes, Charsets.UTF_8)
            )
        } else {
            WebPageParser.extractReadableText(page.bytes, page.finalUrl)
        }
        val title = extracted.title.ifBlank { WebSource.domainOf(page.finalUrl) }
        val source = WebSource(
            url = page.finalUrl,
            title = title,
            domain = WebSource.domainOf(page.finalUrl),
            snippet = extracted.text.take(180)
        )

        if (extracted.text.length < 120) {
            return WebToolResult(
                "Страница $url открылась, но читаемого текста почти нет. " +
                        "Вероятно, контент грузится JavaScript или сайт блокирует чтение.",
                listOf(source)
            )
        }

        val clipped = extracted.text.take(MAX_TEXT_CHARS)
        val output = buildString {
            append("Источник: ").append(title).append('\n')
            append("URL: ").append(page.finalUrl).append('\n')
            append("Текст страницы:\n\n")
            append(clipped)
            if (extracted.text.length > MAX_TEXT_CHARS) {
                append("\n\n[Текст сокращён: показаны первые ")
                append(MAX_TEXT_CHARS)
                append(" символов из ")
                append(extracted.text.length)
                append("]")
            }
        }

        return WebToolResult(output, listOf(source)).also {
            fetchCache[url] = CacheEntry(System.currentTimeMillis(), it)
        }
    }

    private fun formatSearch(query: String, results: List<WebSource>): WebToolResult {
        val text = buildString {
            append("Найдено ")
            append(results.size)
            append(" источников по запросу «")
            append(query)
            append("»:\n\n")
            results.forEachIndexed { index, source ->
                append(index + 1).append(". ").append(source.title).append('\n')
                append("   ").append(source.url).append('\n')
                if (source.snippet.isNotBlank()) {
                    append("   ").append(source.snippet.take(300)).append('\n')
                }
                append('\n')
            }
            append("Чтобы прочитать страницу, вызови web_fetch с нужным URL. ")
            append("В итоговом ответе опирайся на эти источники и называй сайты.")
        }
        return WebToolResult(text, results)
    }

    private fun searchDuckDuckGo(query: String, limit: Int): List<WebSource> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        // Через общий httpGet: он аккуратно проходит редиректы и не тянет
        // мегабайты, если вместо выдачи прилетит заглушка.
        val page = httpGet("https://html.duckduckgo.com/html/?q=$encoded")
        val html = String(page.bytes, Charsets.UTF_8)
        return WebPageParser.parseDuckDuckGoHtml(html, limit)
    }

    private fun searchTavily(query: String, limit: Int, apiKey: String): List<WebSource> {
        val key = apiKey.trim()
        val body = JSONObject().apply {
            put("api_key", key)
            put("query", query)
            put("max_results", limit)
            put("search_depth", "basic")
            put("include_answer", false)
            put("include_raw_content", false)
        }.toString().toRequestBody(jsonMediaType)

        val request = Request.Builder()
            .url("https://api.tavily.com/search")
            .header("Authorization", "Bearer $key")
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            val payload = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val reason = when (response.code) {
                    401, 403 -> "ключ Tavily не подошёл (HTTP ${response.code})"
                    429 -> "лимит запросов Tavily исчерпан (HTTP 429)"
                    else -> "Tavily ответил HTTP ${response.code}"
                }
                throw IllegalStateException(reason)
            }
            val root = JSONObject(payload)
            val array = root.optJSONArray("results") ?: JSONArray()
            val out = mutableListOf<WebSource>()
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val url = item.optString("url").trim()
                if (url.isEmpty()) continue
                val title = item.optString("title").trim().ifBlank { WebSource.domainOf(url) }
                val domain = WebSource.domainOf(url)
                if (domain.isBlank()) continue
                out.add(
                    WebSource(
                        url = url,
                        title = title,
                        domain = domain,
                        snippet = item.optString("content").trim()
                    )
                )
                if (out.size >= limit) break
            }
            return out
        }
    }

    private class HttpPage(
        val finalUrl: String,
        val contentType: String,
        val bytes: ByteArray
    )

    private fun httpGet(initialUrl: String): HttpPage {
        var currentUrl = initialUrl
        var redirects = 0

        while (true) {
            val request = Request.Builder()
                .url(currentUrl)
                .header("User-Agent", USER_AGENT)
                .header(
                    "Accept",
                    "text/html,application/xhtml+xml,application/xml;q=0.9,text/plain;q=0.8,application/json;q=0.7,*/*;q=0.5"
                )
                .header("Accept-Language", "ru,en;q=0.8")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (response.code in 300..399) {
                    val location = response.header("Location")
                        ?: return HttpPage(currentUrl, "", ByteArray(0))
                    redirects++
                    if (redirects > MAX_REDIRECTS) {
                        throw IllegalStateException("слишком много перенаправлений")
                    }
                    currentUrl = resolveRedirect(currentUrl, location)
                    // Каждый редирект снова проверяем: публичный сайт мог
                    // перенаправить на localhost или в домашнюю сеть.
                    currentUrl = WebPageParser.validateFetchUrl(currentUrl)
                    return@use
                }

                if (!response.isSuccessful) {
                    throw IllegalStateException("HTTP ${response.code}")
                }

                val bytes = readLimited(response.body?.byteStream(), MAX_PAGE_BYTES)
                return HttpPage(
                    finalUrl = response.request.url.toString(),
                    contentType = response.header("Content-Type").orEmpty(),
                    bytes = bytes
                )
            }
        }
    }

    private fun resolveRedirect(baseUrl: String, location: String): String {
        return try {
            URI(baseUrl).resolve(location.trim()).toString()
        } catch (_: Exception) {
            location.trim()
        }
    }

    private fun readLimited(stream: InputStream?, limit: Long): ByteArray {
        if (stream == null) return ByteArray(0)
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (total < limit) {
            val read = stream.read(buffer)
            if (read < 0) break
            val remaining = (limit - total).toInt()
            val toWrite = minOf(read, remaining)
            out.write(buffer, 0, toWrite)
            total += toWrite
            if (toWrite < read) break
        }
        return out.toByteArray()
    }

    private fun isTextual(contentType: String): Boolean {
        val type = contentType.substringBefore(';').trim().lowercase(Locale.ROOT)
        if (type.isEmpty()) return true
        return type.startsWith("text/") ||
                type == "application/xhtml+xml" ||
                type == "application/xml" ||
                type == "application/json"
    }
}
