package com.runner.app

import androidx.compose.ui.graphics.Color
import com.runner.app.ui.components.MarkdownElement
import com.runner.app.ui.components.parseMarkdown
import com.runner.app.ui.components.prettyLinkLabel
import com.runner.app.ui.components.renderInlineMarkdown
import com.runner.app.ui.components.trimUrlTail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownLinkTest {

    @Test
    fun standaloneBareLinkBecomesCard() {
        val elements = parseMarkdown("https://discuss.ai.google.dev/t/example-thread/200320")

        val card = elements.single() as MarkdownElement.LinkCard
        assertEquals("https://discuss.ai.google.dev/t/example-thread/200320", card.url)
    }

    @Test
    fun standaloneMarkdownLinkKeepsTitle() {
        val elements = parseMarkdown("[Документация](https://example.com/docs)")

        val card = elements.single() as MarkdownElement.LinkCard
        assertEquals("https://example.com/docs", card.url)
        assertEquals("Документация", card.title)
    }

    @Test
    fun boldStandaloneLinkStillBecomesCard() {
        val elements = parseMarkdown("**[Google](https://google.com)**")

        val card = elements.single() as MarkdownElement.LinkCard
        assertEquals("https://google.com", card.url)
        assertEquals("Google", card.title)
    }

    @Test
    fun boldInlineLinkIsClickable() {
        val annotated = renderInlineMarkdown(
            text = "Смотри **[Google](https://google.com)**.",
            primaryColor = Color.Blue,
            secondaryColor = Color.Green
        )

        val links = annotated.getStringAnnotations("URL", 0, annotated.length)
        assertEquals("https://google.com", links.single().item)
    }

    @Test
    fun linkInsideSentenceStaysParagraph() {
        val elements = parseMarkdown("Подробности тут https://example.com/page и дальше текст")

        assertTrue(elements.single() is MarkdownElement.Paragraph)
    }

    @Test
    fun inlineLinkIsShortenedAndAnnotated() {
        val annotated = renderInlineMarkdown(
            text = "Смотри https://example.com/very/long/path.",
            primaryColor = Color.Blue,
            secondaryColor = Color.Green
        )

        assertTrue(annotated.text.contains("example.com/very/long/path"))
        assertTrue(!annotated.text.contains("https://"))
        val links = annotated.getStringAnnotations("URL", 0, annotated.length)
        assertEquals("https://example.com/very/long/path", links.single().item)
    }

    @Test
    fun trailingParenthesisIsNotPartOfLink() {
        assertEquals("https://example.com", trimUrlTail("https://example.com)"))
        assertEquals(
            "https://example.com/wiki_(test)",
            trimUrlTail("https://example.com/wiki_(test)")
        )
    }

    @Test
    fun prettyLabelKeepsHostAndPath() {
        assertEquals(
            "example.com/very/long/path",
            prettyLinkLabel("https://www.example.com/very/long/path")
        )
    }
}
