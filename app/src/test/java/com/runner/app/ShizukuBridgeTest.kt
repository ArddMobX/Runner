package com.runner.app

import com.runner.app.tools.ShizukuBridge
import org.junit.Assert.assertEquals
import org.junit.Test

class ShizukuBridgeTest {

    @Test
    fun shellQuoteWrapsSimpleCommand() {
        assertEquals("'pkg update'", ShizukuBridge.shellQuote("pkg update"))
    }

    @Test
    fun shellQuoteEscapesSingleQuotes() {
        assertEquals(
            "'echo '\\''hi'\\'''",
            ShizukuBridge.shellQuote("echo 'hi'")
        )
    }
}
