package com.runner.app

import com.runner.app.ui.components.MarkdownElement
import com.runner.app.ui.components.StreamingMarkdownCache
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamingMarkdownCacheTest {

    @Test
    fun growingTextKeepsAllBlocks() {
        val cache = StreamingMarkdownCache()

        val one = cache.parse("Первый абзац")
        assertEquals(1, one.count { it is MarkdownElement.Paragraph })

        val two = cache.parse("Первый абзац\n\nВторой абзац")
        assertEquals(2, two.count { it is MarkdownElement.Paragraph })

        val three = cache.parse("Первый абзац\n\nВторой абзац\n\nТретий")
        assertEquals(3, three.count { it is MarkdownElement.Paragraph })
    }

    @Test
    fun blankLineInsideCodeFenceIsNotBoundary() {
        val cache = StreamingMarkdownCache()
        val elements = cache.parse(
            "```kotlin\nval a = 1\n\nval b = 2\n```\n\nДальше текст"
        )

        assertTrue(elements.any { it is MarkdownElement.CodeBlock })
        assertTrue(elements.any { it is MarkdownElement.Paragraph })
    }

    @Test
    fun changedBeginningResetsCache() {
        val cache = StreamingMarkdownCache()
        cache.parse("Старый текст\n\nЕщё старый")

        val result = cache.parse("Новый текст")

        assertEquals(1, result.size)
        assertEquals("Новый текст", (result.single() as MarkdownElement.Paragraph).text)
    }

    @Test
    fun tableStaysWholeBeforeBoundary() {
        val cache = StreamingMarkdownCache()
        val result = cache.parse(
            "| A | B |\n|---|---|\n| 1 | 2 |\n\nПосле таблицы"
        )

        assertTrue(result.any { it is MarkdownElement.Table })
        assertTrue(result.any { it is MarkdownElement.Paragraph })
    }
}
