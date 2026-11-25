package com.runner.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import com.runner.app.ui.components.MarkdownElement
import com.runner.app.ui.components.StreamingMarkdownCache
import com.runner.app.ui.components.parseMarkdown
import com.runner.app.ui.components.renderInlineMarkdown
import com.runner.app.ui.components.sanitizeMarkdown
import com.runner.app.ui.components.shouldUseInlineChips
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownStage3Test {

    @Test
    fun inlineCodeHasNoArtificialPaddingSpacesAndSupportsDoubleBackticks() {
        val annotated = renderInlineMarkdown(
            text = "Строка `full_unlock` по адресу (`0x3dc2340`).",
            primaryColor = Color.Blue,
            secondaryColor = Color.Green
        )
        assertEquals("Строка full_unlock по адресу (0x3dc2340).", annotated.text)
        assertTrue(annotated.spanStyles.any { it.item.color == Color.Blue })

        val doubleTicks = renderInlineMarkdown(
            text = "Используй ``foo `bar` baz`` и `` `x` ``.",
            primaryColor = Color.Blue,
            secondaryColor = Color.Green
        )
        assertEquals("Используй foo `bar` baz и `x`.", doubleTicks.text)
    }

    @Test
    fun horizontalRulesParseBeforeLists() {
        val md = """
            Начало

            ---

            ***

            ___

            - - -

            * * *

            Конец
        """.trimIndent()
        val elements = parseMarkdown(md)
        val rules = elements.filterIsInstance<MarkdownElement.HorizontalRule>()
        assertEquals(5, rules.size)
        assertTrue(elements.first() is MarkdownElement.Paragraph)
        assertTrue(elements.last() is MarkdownElement.Paragraph)
    }

    @Test
    fun nestedListsAndTaskCheckboxesParseIndentLevelsAndCheckedState() {
        val md = """
            - [x] Сделано
              - [ ] Подзадача
                + Глубокий пункт
            - [X] Тоже сделано
            - Обычный пункт
              продолжение строки
            1. Первый шаг
               1. Вложенный шаг
        """.trimIndent()

        val listBlock = parseMarkdown(md).single() as MarkdownElement.ListBlock
        assertEquals(7, listBlock.items.size)

        val item0 = listBlock.items[0] as MarkdownElement.BulletItem
        assertEquals("Сделано", item0.text)
        assertEquals(0, item0.indentLevel)
        assertEquals(true, item0.checked)

        val item1 = listBlock.items[1] as MarkdownElement.BulletItem
        assertEquals("Подзадача", item1.text)
        assertEquals(1, item1.indentLevel)
        assertEquals(false, item1.checked)

        val item2 = listBlock.items[2] as MarkdownElement.BulletItem
        assertEquals("Глубокий пункт", item2.text)
        assertEquals(2, item2.indentLevel)
        assertNull(item2.checked)

        val item3 = listBlock.items[3] as MarkdownElement.BulletItem
        assertEquals(0, item3.indentLevel)
        assertEquals(true, item3.checked)

        val item4 = listBlock.items[4] as MarkdownElement.BulletItem
        assertEquals("Обычный пункт\nпродолжение строки", item4.text)
        assertEquals(0, item4.indentLevel)
        assertNull(item4.checked)

        val item5 = listBlock.items[5] as MarkdownElement.NumberedItem
        assertEquals("1", item5.number)
        assertEquals(0, item5.indentLevel)

        val item6 = listBlock.items[6] as MarkdownElement.NumberedItem
        assertEquals("1", item6.number)
        assertEquals(1, item6.indentLevel)
    }

    @Test
    fun multilineBlockquotesMergeIntoSingleBlockquote() {
        val md = """
            > Первая строка цитаты
            > вторая строка
            >
            > Новый абзац внутри цитаты
        """.trimIndent()
        val elements = parseMarkdown(md)
        val quote = elements.single() as MarkdownElement.Blockquote
        assertEquals(
            "Первая строка цитаты\nвторая строка\n\nНовый абзац внутри цитаты",
            quote.text
        )
    }

    @Test
    fun longBacktickAndTildeFencesPreserveNestedCodeBlocksAndStreamingCache() {
        val fourTicks = """
            ````markdown
            Пример:
            ```kotlin
            val price = "${'$'}100"
            ```
            ````
        """.trimIndent()

        val parsedFour = parseMarkdown(fourTicks)
        val codeFour = parsedFour.single() as MarkdownElement.CodeBlock
        assertEquals("markdown", codeFour.language)
        assertTrue(codeFour.code.contains("```kotlin"))
        assertTrue(codeFour.code.contains("${'$'}100"))

        val tilde = """
            ~~~python
            cost = "${'$'}50 GB${'$'}"

            print(cost)
            ~~~

            После блока
        """.trimIndent()
        assertEquals(tilde, sanitizeMarkdown(tilde))

        val cache = StreamingMarkdownCache()
        val streamed = cache.parse(tilde)
        assertEquals(2, streamed.size)
        val tildeBlock = streamed[0] as MarkdownElement.CodeBlock
        assertEquals("python", tildeBlock.language)
        assertTrue(tildeBlock.code.contains("\n\nprint(cost)"))
        assertEquals("После блока", (streamed[1] as MarkdownElement.Paragraph).text)
    }

    @Test
    fun paragraphGroupsSoftLineBreaksWithoutSwallowingTablesOrHeaders() {
        val md = """
            Имя: Runner
            Версия: 1.0
            | Ключ | Значение |
            | --- | --- |
            | a | 1 |
        """.trimIndent()

        val elements = parseMarkdown(md)
        assertEquals(2, elements.size)
        assertEquals("Имя: Runner\nВерсия: 1.0", (elements[0] as MarkdownElement.Paragraph).text)
        assertTrue(elements[1] is MarkdownElement.Table)
    }

    @Test
    fun nestedInlineFormattingAndUnderscoreSnakeCaseSafety() {
        val nested = renderInlineMarkdown(
            text = "***жирный курсив***, **жирный с `кодом` и *курсивом***, ~~зачёркнутый **текст**~~",
            primaryColor = Color.Blue,
            secondaryColor = Color.Green
        )
        assertEquals("жирный курсив, жирный с кодом и курсивом, зачёркнутый текст", nested.text)
        assertTrue(nested.spanStyles.any {
            it.item.fontWeight == FontWeight.Bold && it.item.fontStyle == FontStyle.Italic
        })
        assertTrue(nested.spanStyles.any {
            it.item.textDecoration == TextDecoration.LineThrough
        })

        val snake = renderInlineMarkdown(
            text = "Флаги full_unlock, is_locked и my_var_name — это _важно_ и (__точно__), а 2 * 3 * 4 = 24",
            primaryColor = Color.Blue,
            secondaryColor = Color.Green
        )
        assertEquals(
            "Флаги full_unlock, is_locked и my_var_name — это важно и (точно), а 2 * 3 * 4 = 24",
            snake.text
        )
        val italicStart = snake.text.indexOf("важно")
        assertTrue(snake.spanStyles.any {
            it.item.fontStyle == FontStyle.Italic && it.start == italicStart && it.end == italicStart + 5
        })
        val boldStart = snake.text.indexOf("точно")
        assertTrue(snake.spanStyles.any {
            it.item.fontWeight == FontWeight.Bold && it.start == boldStart && it.end == boldStart + 5
        })
    }

    @Test
    fun escapesCurrencyAndBalancedLinksAndImagesRenderCleanly() {
        val escaped = renderInlineMarkdown(
            text = """\*не курсив\*, \_не курсив\_, \`не код\`, путь C:\Users\name, цена ${'$'}5 и ${'$'}10, формула ${'$'}x^2${'$'}""",
            primaryColor = Color.Blue,
            secondaryColor = Color.Green
        )
        assertEquals(
            """*не курсив*, _не курсив_, `не код`, путь C:\Users\name, цена ${'$'}5 и ${'$'}10, формула x²""",
            escaped.text
        )

        val wikiLink = renderInlineMarkdown(
            text = "Читай [**Rust** [lang]](https://en.wikipedia.org/wiki/Rust_(programming_language)) и ![График](https://example.com/chart.png)!",
            primaryColor = Color.Blue,
            secondaryColor = Color.Green
        )
        assertEquals("Читай Rust [lang] и График!", wikiLink.text)
        val urls = wikiLink.getStringAnnotations("URL", 0, wikiLink.length).map { it.item }
        assertEquals(
            listOf(
                "https://en.wikipedia.org/wiki/Rust_(programming_language)",
                "https://example.com/chart.png"
            ),
            urls
        )

        val standaloneImage = parseMarkdown("![Схема](https://example.com/scheme.png)").single() as MarkdownElement.LinkCard
        assertTrue(standaloneImage.isImage)
        assertEquals("Схема", standaloneImage.title)
        assertEquals("https://example.com/scheme.png", standaloneImage.url)

        assertFalse(shouldUseInlineChips("Строка с _курсивом_ и `code`"))
        assertFalse(shouldUseInlineChips("Строка 1\n`code`"))
    }
}
