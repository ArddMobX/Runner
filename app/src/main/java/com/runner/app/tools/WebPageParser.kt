package com.runner.app.tools

import com.runner.app.data.WebSource
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.io.ByteArrayInputStream
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI
import java.net.URLDecoder
import java.util.Locale

/**
 * Разбор веб-страниц без Android-зависимостей: всё здесь проверяется
 * юнит-тестами на JVM.
 */
internal object WebPageParser {

    private const val MIN_READABLE_CHARS = 200
    private const val MIN_PARAGRAPH_CHARS = 40

    /**
     * Приводит адрес к виду https://… и проверяет, что он ведёт во внешний
     * интернет, а не на localhost, домашний роутер или файл на устройстве.
     *
     * Возвращает нормализованный URL или бросает IllegalArgumentException
     * с человеческим текстом.
     */
    fun validateFetchUrl(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) throw IllegalArgumentException("пустой адрес")

        val withScheme = when {
            trimmed.startsWith("http://", ignoreCase = true) ||
                    trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            trimmed.contains("://") -> throw IllegalArgumentException("поддерживаются только http и https")
            else -> "https://$trimmed"
        }

        val uri = try {
            URI(withScheme)
        } catch (_: Exception) {
            throw IllegalArgumentException("адрес не разобрался")
        }

        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        if (scheme != "http" && scheme != "https") {
            throw IllegalArgumentException("поддерживаются только http и https")
        }
        if (uri.userInfo != null) {
            throw IllegalArgumentException("адреса с логином и паролем не открываются")
        }

        val host = uri.host ?: throw IllegalArgumentException("в адресе нет домена")
        if (host.isBlank()) throw IllegalArgumentException("в адресе нет домена")

        val port = uri.port
        if (port != -1 && port != 80 && port != 443) {
            throw IllegalArgumentException("порт $port не разрешён")
        }

        val lowerHost = host.lowercase(Locale.ROOT)
            .removePrefix("[")
            .removeSuffix("]")
        if (lowerHost == "localhost" ||
            lowerHost.endsWith(".local") ||
            lowerHost.endsWith(".internal") ||
            lowerHost.endsWith(".home")
        ) {
            throw IllegalArgumentException("локальные адреса не открываются")
        }

        val looksLikeIpv6 = lowerHost.contains(':')
        val looksLikeIpv4 = lowerHost.matches(Regex("""\d{1,3}(\.\d{1,3}){3}"""))
        if (!looksLikeIpv4 && !looksLikeIpv6 && !lowerHost.contains('.')) {
            throw IllegalArgumentException("это похоже на адрес внутри сети")
        }

        // DNS-резолв может занять время и упасть; оба случая одинаково
        // означают «страницу сейчас не открыть».
        val addresses = try {
            InetAddress.getAllByName(lowerHost)
        } catch (_: Exception) {
            throw IllegalArgumentException("не удалось найти сайт $lowerHost")
        }
        if (addresses.isEmpty()) throw IllegalArgumentException("не удалось найти сайт $lowerHost")

        for (address in addresses) {
            if (isPrivateAddress(address)) {
                throw IllegalArgumentException("адрес ведёт во внутреннюю сеть")
            }
        }

        return uri.toString()
    }

    private fun isPrivateAddress(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress ||
            address.isLoopbackAddress ||
            address.isLinkLocalAddress ||
            address.isSiteLocalAddress ||
            address.isMulticastAddress
        ) {
            return true
        }
        // IPv6 unique local (fc00::/7) не всегда помечен как site-local.
        if (address is Inet6Address) {
            val first = address.address.firstOrNull()?.toInt()?.and(0xFF) ?: return false
            if (first and 0xFE == 0xFC) return true
        }
        return false
    }

    /**
     * Ссылки DuckDuckGo часто завёрнуты в редирект вида
     * `//duckduckgo.com/l/?uddg=https%3A%2F%2Fexample.com`. Достаём настоящий адрес.
     */
    fun resolveDuckDuckGoHref(href: String): String? {
        var value = href.trim()
        if (value.isEmpty()) return null
        if (value.startsWith("//")) value = "https:$value"
        if (value.startsWith("/l/")) value = "https://duckduckgo.com$value"

        if (value.contains("duckduckgo.com/l/")) {
            val query = value.substringAfter('?', "")
            val encoded = query.split('&')
                .firstOrNull { it.startsWith("uddg=") }
                ?.substringAfter('=')
                ?: return null
            return try {
                URLDecoder.decode(encoded, "UTF-8")
            } catch (_: Exception) {
                null
            }
        }

        return if (value.startsWith("http://") || value.startsWith("https://")) value else null
    }

    /** Разбор выдачи html.duckduckgo.com. */
    fun parseDuckDuckGoHtml(html: String, limit: Int): List<WebSource> {
        if (html.isBlank()) return emptyList()
        val doc = Jsoup.parse(html)
        val found = LinkedHashMap<String, WebSource>()

        doc.select("div.result, div.web-result").forEach { result ->
            if (result.hasClass("result--ad")) return@forEach
            val link = result.selectFirst("a.result__a") ?: return@forEach
            val url = resolveDuckDuckGoHref(link.attr("href")) ?: return@forEach
            val title = link.text().trim()
            if (title.isBlank()) return@forEach
            val domain = WebSource.domainOf(url)
            if (domain.isBlank()) return@forEach
            val snippet = result
                .selectFirst(".result__snippet, a.result__snippet")
                ?.text()
                ?.trim()
                .orEmpty()
            if (found.size < limit && url !in found) {
                found[url] = WebSource(
                    url = url,
                    title = title,
                    domain = domain,
                    snippet = snippet
                )
            }
        }
        return found.values.toList()
    }

    /**
     * Разбор RSS-выдачи Bing. Нужен как запасной движок: HTML-выдача
     * DuckDuckGo в некоторых сетях рвётся по connection reset, а лёгкий
     * RSS-ответ Bing проходит чаще и не требует ключа.
     */
    fun parseBingRss(xml: String, limit: Int): List<WebSource> {
        if (xml.isBlank()) return emptyList()
        val doc = Jsoup.parse(xml, "", Parser.xmlParser())
        val found = LinkedHashMap<String, WebSource>()

        doc.select("item").forEach { item ->
            val url = item.selectFirst("link")?.text()?.trim().orEmpty()
            if (!url.startsWith("http://") && !url.startsWith("https://")) return@forEach
            val title = item.selectFirst("title")?.text()?.trim().orEmpty()
            if (title.isBlank()) return@forEach
            val domain = WebSource.domainOf(url)
            if (domain.isBlank()) return@forEach
            val snippet = item.selectFirst("description")?.text()?.trim().orEmpty()
            if (found.size < limit && url !in found) {
                found[url] = WebSource(
                    url = url,
                    title = title,
                    domain = domain,
                    snippet = snippet.take(300)
                )
            }
        }
        return found.values.toList()
    }

    /**
     * Достаёт основной текст статьи.
     *
     * Сначала пробуем семантические контейнеры (article/main), затем выбираем
     * родителя с наибольшей плотностью абзацев. Навигация, меню, скрипты и
     * баннеры выкидываются: модели нужен текст, а не «принять cookie».
     */
    fun extractReadableText(bytes: ByteArray, baseUrl: String): ExtractedPage {
        val doc = try {
            Jsoup.parse(ByteArrayInputStream(bytes), null, baseUrl)
        } catch (_: Exception) {
            return ExtractedPage("", "")
        }

        val title = doc.title().trim()
        doc.select(
            "script,style,noscript,svg,iframe,canvas,form,button,input,select,textarea," +
                    "nav,footer,header,aside,figure figcaption"
        ).remove()
        doc.select(
            "[class*=cookie],[id*=cookie],[class*=consent],[class*=subscribe]," +
                    "[class*=newsletter],[class*=banner],[class*=advert],[class*=social]"
        ).remove()

        val candidates = doc.select(
            "article, main, [role=main], [itemprop=articleBody], " +
                    ".post-content, .article-content, .entry-content, .article-body, " +
                    "#content, .content"
        )
        val semantic = candidates.maxByOrNull { it.text().length }
        val root = if (semantic != null && semantic.text().length >= MIN_READABLE_CHARS) {
            semantic
        } else {
            bestParagraphContainer(doc) ?: semantic ?: doc.body()
        }

        val text = root?.let { readableBlocks(it) }.orEmpty()
            .ifBlank { root?.text().orEmpty() }
            .replace(Regex("[ \\t]+"), " ")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()

        return ExtractedPage(title, text)
    }

    fun extractReadableText(html: String, baseUrl: String): ExtractedPage =
        extractReadableText(html.toByteArray(Charsets.UTF_8), baseUrl)

    private fun readableBlocks(root: Element): String {
        val blocks = root.select("h1,h2,h3,h4,p,li,blockquote,pre")
        if (blocks.isEmpty()) return root.text().trim()

        val out = StringBuilder()
        for (element in blocks) {
            val text = element.text().trim()
            if (text.length < 2) continue
            if (element.tagName() == "pre") {
                out.append("\n```\n").append(text).append("\n```\n\n")
            } else {
                out.append(text).append("\n\n")
            }
        }
        return out.toString().trim()
    }

    private fun bestParagraphContainer(doc: Document): Element? {
        val scores = LinkedHashMap<Element, Int>()
        doc.select("p, pre, blockquote").forEach { paragraph ->
            val length = paragraph.text().trim().length
            if (length < MIN_PARAGRAPH_CHARS) return@forEach
            var parent: Element? = paragraph.parent()
            var depth = 0
            while (parent != null && parent.tagName() != "body" && depth < 3) {
                scores[parent] = (scores[parent] ?: 0) + (length / 50).coerceAtLeast(1)
                parent = parent.parent()
                depth++
            }
        }
        return scores.maxByOrNull { it.value }?.key
    }

    data class ExtractedPage(val title: String, val text: String)
}
