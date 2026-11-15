package com.runner.app

import com.runner.app.ui.ChatMessage
import com.runner.app.ui.MessageRole
import com.runner.app.ui.PlanStepState
import com.runner.app.ui.computePlanStepStates
import com.runner.app.ui.parsePlanStepTools
import org.junit.Assert.assertEquals
import org.junit.Test

class PlanProgressTest {

    @Test
    fun toolNamesAreParsedFromPlanLines() {
        val plan = """
            1. list_dir - посмотреть папку
            2. write_file - записать скрипт
            3. run_shell_command - проверить синтаксис
        """.trimIndent()

        assertEquals(
            listOf("list_dir", "write_file", "run_shell_command"),
            parsePlanStepTools(plan)
        )
    }

    @Test
    fun stepsFollowExecutedTools() {
        val tools = listOf("list_dir", "write_file", "run_shell_command")
        val messages = listOf(
            tool("list_dir"),
            tool("write_file", running = true)
        )

        assertEquals(
            listOf(PlanStepState.DONE, PlanStepState.RUNNING, PlanStepState.PENDING),
            computePlanStepStates(tools, messages)
        )
    }

    @Test
    fun failedStepIsMarked() {
        val tools = listOf("write_file")
        val messages = listOf(tool("write_file", failed = true))

        assertEquals(listOf(PlanStepState.FAILED), computePlanStepStates(tools, messages))
    }

    private fun tool(
        name: String,
        running: Boolean = false,
        failed: Boolean = false
    ): ChatMessage = ChatMessage(
        role = MessageRole.TOOL_EXECUTION,
        content = name,
        toolName = name,
        isRunning = running,
        isError = failed,
        isDeclined = false
    )
}
