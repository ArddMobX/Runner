package com.runner.app

import com.runner.app.ui.components.ChatInputState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatInputStateTest {

    @Test
    fun clearDoesNotResetSearchAndThinkModes() {
        val state = ChatInputState(initialText = "привет")
        state.webSearch = true
        state.think = true

        state.clear()

        assertEquals("", state.text)
        assertTrue(state.webSearch)
        assertTrue(state.think)
    }
}
