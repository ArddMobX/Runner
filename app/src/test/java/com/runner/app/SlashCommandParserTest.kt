package com.runner.app

import com.runner.app.ui.commands.SlashCommandParser
import com.runner.app.ui.commands.SlashCommands
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SlashCommandParserTest {

    @Test
    fun parsesCommandWithoutArguments() {
        val parsed = SlashCommandParser.parse("/help")

        assertNotNull(parsed)
        assertEquals("help", parsed?.command?.name)
        assertEquals("", parsed?.args)
    }

    @Test
    fun parsesCommandWithArguments() {
        val parsed = SlashCommandParser.parse("/rename Новый чат")

        assertNotNull(parsed)
        assertEquals("rename", parsed?.command?.name)
        assertEquals("Новый чат", parsed?.args)
    }

    @Test
    fun commandNamesAreCaseInsensitive() {
        val parsed = SlashCommandParser.parse("/HeLp")

        assertEquals("help", parsed?.command?.name)
    }

    @Test
    fun aliasWorks() {
        val parsed = SlashCommandParser.parse("/?")

        assertEquals("help", parsed?.command?.name)
    }

    @Test
    fun absolutePathIsNotACommand() {
        assertNull(SlashCommandParser.parse("/storage/emulated/0/Download"))
    }

    @Test
    fun unknownCommandIsNotACommand() {
        assertNull(SlashCommandParser.parse("/unknown"))
    }

    @Test
    fun plainTextIsNotACommand() {
        assertNull(SlashCommandParser.parse("help me"))
    }

    @Test
    fun loneSlashIsNotACommand() {
        assertNull(SlashCommandParser.parse("/"))
    }

    @Test
    fun paletteShowsAllCommandsForLoneSlash() {
        val matches = SlashCommandParser.paletteMatches("/")

        assertEquals(SlashCommands.all.size, matches.size)
    }

    @Test
    fun paletteFiltersByPrefix() {
        val matches = SlashCommandParser.paletteMatches("/ren")

        assertEquals(listOf("rename"), matches.map { it.name })
    }

    @Test
    fun paletteHidesAfterWhitespace() {
        assertTrue(SlashCommandParser.paletteMatches("/rename ").isEmpty())
    }

    @Test
    fun paletteHidesForUnknownPrefix() {
        assertTrue(SlashCommandParser.paletteMatches("/zzz").isEmpty())
    }

    @Test
    fun commandNamesAndAliasesAreUnique() {
        val names = SlashCommands.all.flatMap { command ->
            listOf(command.name) + command.aliases
        }

        assertEquals(names.size, names.distinct().size)
    }
}
