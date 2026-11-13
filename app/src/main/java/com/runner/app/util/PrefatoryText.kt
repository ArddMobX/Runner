package com.runner.app.util

import java.util.Locale

/**
 * Определяет короткие служебные обещания вроде «Сейчас посмотрю»,
 * «Открываю приложение» или «Let me check».
 *
 * Такая реплика не является финальным ответом: у неё не должно быть
 * кнопок копирования и озвучки, а агент должен продолжить работу
 * вызовом инструмента.
 */
object PrefatoryText {

    private const val MAX_CHARS = 160
    private const val MAX_FIRST_LINE_CHARS = 120

    private val RU_PATTERN = Regex(
        """(?iu)^(сейчас|щас|теперь|сначала|далее|затем|потом|уже)?\s*""" +
                """(посмотрю|посмотрим|проверю|проверяю|открою|открываю|""" +
                """запущу|запускаю|начну|начинаю|приступаю|сделаю|делаю|""" +
                """попробую|пробую|найду|ищу|перейду|перехожу|нажму|нажимаю|""" +
                """скачаю|скачиваю|подожду|жду|создам|создаю|обновлю|обновляю|""" +
                """установлю|устанавливаю|включу|включаю|выключу|выключаю|""" +
                """вернусь|возвращаюсь|покажу|показываю|гляну|загляну|введу|""" +
                """напишу|изменю|удалю|перемещу|скопирую)\b"""
    )

    private val EN_PATTERN = Regex(
        """(?i)^(now|first|next|then|let me|i'll|i will|i'm going to|i am going to)\s*""" +
                """(check|checking|look|looking|open|opening|launch|launching|""" +
                """start|starting|try|trying|find|finding|search|searching|""" +
                """click|clicking|tap|tapping|wait|waiting|create|creating|""" +
                """update|updating|install|installing|enable|enabling|""" +
                """disable|disabling|continue|continuing|proceed|proceeding)\b"""
    )

    /**
     * Слова, которые означают уже полученный результат. Если они есть,
     * это не чистое обещание, а содержательный комментарий или ответ.
     */
    private val RESULT_HINTS = listOf(
        "вижу",
        "видно",
        "показывает",
        "показывают",
        "нашёл",
        "нашел",
        "нашла",
        "готово",
        "получилось",
        "открылся",
        "открылась",
        "содержит",
        "лежит",
        "удалось",
        "создан",
        "изменён",
        "изменен",
        "удалён",
        "удален",
        "ошибка",
        "не удалось"
    )

    fun isPrefatory(raw: String): Boolean {
        val text = raw.trim()
        if (text.isEmpty() || text.length > MAX_CHARS) return false
        if (text.contains('?') || text.contains("```") || text.contains('|')) return false

        val firstLine = text.lineSequence().firstOrNull()?.trim().orEmpty()
        if (firstLine.isEmpty() || firstLine.length > MAX_FIRST_LINE_CHARS) return false

        val lower = text.lowercase(Locale.ROOT)
        val hasResult = RESULT_HINTS.any { lower.contains(it) }
        val firstLinePromise = RU_PATTERN.containsMatchIn(firstLine) ||
                EN_PATTERN.containsMatchIn(firstLine)
        if (firstLinePromise && !hasResult) return true

        // Фраза может начинаться с результата, а заканчиваться обещанием:
        // «Экран показывает ... Проверяю». Это тоже не финал. Но одну
        // длинную фразу с результатом («Проверяю экран, вижу кнопку»)
        // обещанием не считаем.
        if (hasResult && !text.contains(". ")) return false
        val lastSentence = text
            .substringAfterLast(". ")
            .trim()
            .trimEnd('.', '…', '!')
        if (lastSentence.length in 1..60 && RU_PATTERN.containsMatchIn(lastSentence)) {
            return true
        }

        return false
    }
}
