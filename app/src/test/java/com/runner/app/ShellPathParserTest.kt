package com.runner.app

import com.runner.app.tools.ShellPathParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShellPathParserTest {

    @Test
    fun quotedPathWithSpaceIsOneToken() {
        val paths = ShellPathParser.pathTokens(
            """bash -n "/storage/emulated/0/AI Motion/make_video.sh" && echo OK"""
        )

        assertEquals(listOf("/storage/emulated/0/AI Motion/make_video.sh"), paths)
    }

    @Test
    fun singleQuotesAndEscapesWork() {
        val paths = ShellPathParser.pathTokens(
            """cd '/storage/emulated/0/AI Motion' && cat /storage/emulated/0/My\ File.txt"""
        )

        assertTrue("/storage/emulated/0/AI Motion" in paths)
        assertTrue("/storage/emulated/0/My File.txt" in paths)
    }

    @Test
    fun redirectsAndAssignmentsAreFound() {
        val paths = ShellPathParser.pathTokens(
            """OUT=/storage/emulated/0/out.txt sh run.sh > /storage/emulated/0/log.txt 2>/dev/null"""
        )

        assertTrue("/storage/emulated/0/out.txt" in paths)
        assertTrue("/storage/emulated/0/log.txt" in paths)
        assertTrue("/dev/null" in paths)
    }

    @Test
    fun wordsThatAreNotPathsAreIgnored() {
        assertTrue(ShellPathParser.pathTokens("""echo "не путь" && ls -la""").isEmpty())
    }
}
