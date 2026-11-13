package com.runner.app

import com.runner.app.ui.commands.CustomCommandValidation
import com.runner.app.ui.commands.SlashCommandGroup
import com.runner.app.ui.commands.SlashCommandParser
import com.runner.app.ui.commands.SlashCommandRequests
import com.runner.app.ui.commands.SlashCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomCommandValidationTest {

    @Test
    fun normalizesLeadingSlashAndCase() {
        assertEquals("review", CustomCommandValidation.normalizeName(" /Review "))
    }

    @Test
    fun acceptsValidName() {
        assertNull(CustomCommandValidation.validateName("review_2"))
    }

    @Test
    fun rejectsBuiltInName() {
        assertNotNull(CustomCommandValidation.validateName("help"))
        assertNotNull(CustomCommandValidation.validateName("/plan"))
    }

    @Test
    fun rejectsAlias() {
        assertNotNull(CustomCommandValidation.validateName("?"))
    }

    @Test
    fun rejectsInvalidCharacters() {
        assertNotNull(CustomCommandValidation.validateName("про"))
        assertNotNull(CustomCommandValidation.validateName("two words"))
        assertNotNull(CustomCommandValidation.validateName("-start"))
    }

    @Test
    fun rejectsTooLongName() {
        assertNotNull(CustomCommandValidation.validateName("a".repeat(25)))
    }

    @Test
    fun rejectsEmptyPrompt() {
        assertNotNull(CustomCommandValidation.validatePrompt("   "))
    }

    @Test
    fun expandsPlaceholder() {
        assertEquals(
            "Проверь файл Download/test.txt",
            CustomCommandValidation.expandPrompt(
                "Проверь файл {{args}}",
                " Download/test.txt "
            )
        )
    }

    @Test
    fun appendsArgumentsWithoutPlaceholder() {
        assertEquals(
            "Сделай ревью\n\nDownload/test.txt",
            CustomCommandValidation.expandPrompt("Сделай ревью", "Download/test.txt")
        )
    }

    @Test
    fun keepsTemplateWithoutArguments() {
        assertEquals(
            "Просто ответь",
            CustomCommandValidation.expandPrompt("Просто ответь", "  ")
        )
    }

    @Test
    fun customCommandBuildsAgentRequest() {
        val command = SlashCommand(
            name = "review",
            title = "Ревью",
            description = "Проверить код",
            group = SlashCommandGroup.CUSTOM,
            argsHint = "файл",
            isCustom = true,
            promptTemplate = "Проверь {{args}}"
        )

        val request = SlashCommandRequests.from(command, "Download/test.kt")

        assertNotNull(request)
        assertEquals("Проверь Download/test.kt", request?.prompt)
        assertEquals(false, request?.forceSearch)
        assertEquals(false, request?.forcePlan)
        assertTrue(command.takesArgs)
    }

    @Test
    fun customCommandIsFoundByDynamicParser() {
        val command = SlashCommand(
            name = "review",
            title = "Ревью",
            description = "Проверить код",
            group = SlashCommandGroup.CUSTOM,
            isCustom = true,
            promptTemplate = "Проверь {{args}}"
        )

        val parsed = SlashCommandParser.parse("/review Download/test.kt", listOf(command))

        assertEquals("review", parsed?.command?.name)
        assertEquals("Download/test.kt", parsed?.args)
    }

    @Test
    fun customCommandIsHiddenFromStaticParser() {
        val parsed = SlashCommandParser.parse("/review Download/test.kt")

        assertNull(parsed)
    }

    @Test
    fun builtInCommandDoesNotBecomeCustomRequest() {
        val parsed = SlashCommandParser.parse("/help")

        assertNull(parsed?.let { SlashCommandRequests.from(it.command, it.args) })
        assertFalse(parsed?.command?.isCustom == true)
    }
}
