package com.runner.app

import androidx.compose.ui.graphics.Color
import com.runner.app.ui.components.CURSOR_INLINE_ID
import com.runner.app.ui.components.MarkdownElement
import com.runner.app.ui.components.parseMarkdown
import com.runner.app.ui.components.renderInlineMarkdown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownCursorTest {

    @Test
    fun testRenderInlineMarkdown_withoutCursor() {
        val annotated = renderInlineMarkdown(
            text = "Hello world",
            primaryColor = Color.Blue,
            secondaryColor = Color.Green,
            showCursor = false
        )
        assertEquals("Hello world", annotated.text)
        val inlineAnnotations = annotated.getStringAnnotations("androidx.compose.foundation.text.inlineContent", 0, annotated.length)
        assertTrue(inlineAnnotations.isEmpty())
    }

    @Test
    fun testRenderInlineMarkdown_withCursor() {
        val annotated = renderInlineMarkdown(
            text = "Generating response...",
            primaryColor = Color.Blue,
            secondaryColor = Color.Green,
            showCursor = true
        )
        assertTrue(annotated.text.endsWith("▋"))
        val inlineAnnotations = annotated.getStringAnnotations("androidx.compose.foundation.text.inlineContent", 0, annotated.length)
        assertEquals(1, inlineAnnotations.size)
        assertEquals(CURSOR_INLINE_ID, inlineAnnotations.first().item)
    }

    @Test
    fun testRenderInlineMarkdown_emptyTextWithCursor() {
        val annotated = renderInlineMarkdown(
            text = "",
            primaryColor = Color.Blue,
            secondaryColor = Color.Green,
            showCursor = true
        )
        assertEquals("▋", annotated.text)
        val inlineAnnotations = annotated.getStringAnnotations("androidx.compose.foundation.text.inlineContent", 0, annotated.length)
        assertEquals(1, inlineAnnotations.size)
        assertEquals(CURSOR_INLINE_ID, inlineAnnotations.first().item)
    }

    @Test
    fun testRenderInlineMarkdown_formattingPreservedWithCursor() {
        val annotated = renderInlineMarkdown(
            text = "Here is **bold** and `code` result",
            primaryColor = Color.Blue,
            secondaryColor = Color.Green,
            showCursor = true
        )
        assertTrue(annotated.text.endsWith("▋"))
        val inlineAnnotations = annotated.getStringAnnotations("androidx.compose.foundation.text.inlineContent", 0, annotated.length)
        assertEquals(1, inlineAnnotations.size)
        assertEquals(CURSOR_INLINE_ID, inlineAnnotations.first().item)
    }

    @Test
    fun testRenderInlineMarkdown_sanitizedLaTeXPreserved() {
        val annotatedWithoutCursor = renderInlineMarkdown(
            text = "Свободно $12 GB$ из $64 GB$ на устройстве",
            primaryColor = Color.Blue,
            secondaryColor = Color.Green,
            showCursor = false
        )
        assertEquals("Свободно 12 GB из 64 GB на устройстве", annotatedWithoutCursor.text)

        val annotatedWithCursor = renderInlineMarkdown(
            text = "Свободно $12 GB$ из $64 GB$ на устройстве",
            primaryColor = Color.Blue,
            secondaryColor = Color.Green,
            showCursor = true
        )
        assertEquals("Свободно 12 GB из 64 GB на устройстве▋", annotatedWithCursor.text)
        val inlineAnnotations = annotatedWithCursor.getStringAnnotations("androidx.compose.foundation.text.inlineContent", 0, annotatedWithCursor.length)
        assertEquals(1, inlineAnnotations.size)
    }

    @Test
    fun testRenderInlineMarkdown_sanitizedWithSubsequentMarkdownAndCursor() {
        val annotated = renderInlineMarkdown(
            text = "Память: \\text{всего} $64 GB$, свободно **12 GB** в хранилище",
            primaryColor = Color.Blue,
            secondaryColor = Color.Green,
            showCursor = true
        )
        assertEquals("Память: всего 64 GB, свободно 12 GB в хранилище▋", annotated.text)
        val inlineAnnotations = annotated.getStringAnnotations("androidx.compose.foundation.text.inlineContent", 0, annotated.length)
        assertEquals(1, inlineAnnotations.size)
        assertEquals(CURSOR_INLINE_ID, inlineAnnotations.first().item)
    }

    @Test
    fun testParseMarkdown_emptyAndWhitespace() {
        val emptyElements = parseMarkdown("")
        assertTrue(emptyElements.isEmpty())

        val whitespaceElements = parseMarkdown("   \n\n  ")
        assertTrue(whitespaceElements.isEmpty())
    }

    @Test
    fun testParseMarkdown_unclosedCodeBlockWhileStreaming() {
        val elements = parseMarkdown("```kotlin\nfun test() = true")
        assertEquals(1, elements.size)
        assertTrue(elements.first() is MarkdownElement.CodeBlock)
        val codeBlock = elements.first() as MarkdownElement.CodeBlock
        assertEquals("kotlin", codeBlock.language)
        assertEquals("fun test() = true", codeBlock.code)
    }

    @Test
    fun testParseMarkdown_listStreaming() {
        val elements = parseMarkdown("* item 1\n* item 2")
        assertEquals(1, elements.size)
        assertTrue(elements.first() is MarkdownElement.ListBlock)
        val listBlock = elements.first() as MarkdownElement.ListBlock
        assertEquals(2, listBlock.items.size)
    }
}
