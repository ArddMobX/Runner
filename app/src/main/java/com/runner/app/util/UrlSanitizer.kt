package com.runner.app.util

object UrlSanitizer {

    /**
     * Очищает и нормализует Base URL:
     * - trim пробелов
     * - заменяет случайные обратные слэши на прямые
     * - удаляет висящие концевые слэши и обратные слэши (например "googleapis.com/\" -> "googleapis.com")
     */
    fun sanitizeBaseUrl(raw: String): String {
        var url = raw.trim()
        if (url.isBlank()) return ""

        // Нормализация схемы: если ввели "http:\" или "https:\"
        url = url.replace("http:\\", "http://", ignoreCase = true)
        url = url.replace("https:\\", "https://", ignoreCase = true)
        url = url.replace("http:/", "http://", ignoreCase = true)
        url = url.replace("https:/", "https://", ignoreCase = true)

        // Замена оставшихся обратных слэшей на прямые
        url = url.replace('\\', '/')

        // Удаление лишних повторяющихся слэшей в пути (сохраняя http://)
        val schemePrefix = when {
            url.startsWith("https://", ignoreCase = true) -> "https://"
            url.startsWith("http://", ignoreCase = true) -> "http://"
            else -> ""
        }
        val remainder = if (schemePrefix.isNotEmpty()) url.substring(schemePrefix.length) else url
        val cleanedRemainder = remainder.split('/').filter { it.isNotEmpty() }.joinToString("/")

        val result = if (schemePrefix.isNotEmpty()) {
            "$schemePrefix$cleanedRemainder"
        } else {
            cleanedRemainder
        }

        return result.trimEnd('/')
    }
}
