package com.runner.app

import com.runner.app.ui.SmartFollowUpGenerator
import com.runner.app.ui.components.MarkdownElement
import com.runner.app.ui.components.TableAlignment
import com.runner.app.ui.components.parseMarkdown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownTableAndFollowUpTest {

    @Test
    fun testParseMarkdown_withTable() {
        val markdown = """
            # Заголовок
            
            | Файл | Размер | Описание |
            | :--- | :---: | ---: |
            | main.kt | 12 KB | Точка входа |
            | app.py | 5 MB | Скрипт |
            
            Текст после таблицы
        """.trimIndent()

        val elements = parseMarkdown(markdown)
        assertTrue(elements.any { it is MarkdownElement.Table })

        val table = elements.first { it is MarkdownElement.Table } as MarkdownElement.Table
        assertEquals(listOf("Файл", "Размер", "Описание"), table.headers)
        assertEquals(3, table.alignments.size)
        assertEquals(TableAlignment.START, table.alignments[0])
        assertEquals(TableAlignment.CENTER, table.alignments[1])
        assertEquals(TableAlignment.END, table.alignments[2])

        assertEquals(2, table.rows.size)
        assertEquals(listOf("main.kt", "12 KB", "Точка входа"), table.rows[0])
        assertEquals(listOf("app.py", "5 MB", "Скрипт"), table.rows[1])
    }

    @Test
    fun testParseMarkdown_tableWithEscapedPipes() {
        val markdown = """
            | Команда | Значение |
            | --- | --- |
            | echo \| grep | Фильтрация |
        """.trimIndent()

        val elements = parseMarkdown(markdown)
        val table = elements.first { it is MarkdownElement.Table } as MarkdownElement.Table
        assertEquals(listOf("Команда", "Значение"), table.headers)
        assertEquals(1, table.rows.size)
        assertEquals(listOf("echo | grep", "Фильтрация"), table.rows[0])
    }

    @Test
    fun testSmartFollowUp_codeContent() {
        val codeResponse = "Вот функция для сортировки:\n```kotlin\nfun sort(list: List<Int>) = list.sorted()\n```\nОна работает за O(N log N)."
        val followUps = SmartFollowUpGenerator.generate(codeResponse)
        assertTrue(followUps.isNotEmpty())
        assertTrue(followUps.contains("Объяснить код пошагово") || followUps.contains("Оптимизировать код"))
    }

    @Test
    fun testSmartFollowUp_tableContent() {
        val tableResponse = "Сравнение вариантов:\n| Модель | Скорость |\n|---|---|\n| Flash | 120 tps |\n| Pro | 40 tps |\nКакой вариант подходит?"
        val followUps = SmartFollowUpGenerator.generate(tableResponse)
        assertTrue(followUps.isNotEmpty())
        assertTrue(followUps.contains("Сравнить подробнее") || followUps.contains("Какой вариант лучше?") || followUps.contains("Да, давай"))
    }
}
