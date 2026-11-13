package com.runner.app.data

import java.net.URI

/**
 * Источник из веб-поиска.
 *
 * Хранится отдельно от текста ответа, чтобы под карточкой инструмента можно
 * было показать аккуратную строку «N источников» и шторку с карточками,
 * а не простыню ссылок в выводе.
 */
data class WebSource(
    val url: String,
    val title: String,
    val domain: String,
    val snippet: String = ""
) {
    companion object {
        /** Домен без www: «lenta.ru». Пустая строка, если URL не разобрался. */
        fun domainOf(url: String): String = try {
            URI(url).host?.lowercase()?.removePrefix("www.").orEmpty()
        } catch (_: Exception) {
            ""
        }
    }
}
