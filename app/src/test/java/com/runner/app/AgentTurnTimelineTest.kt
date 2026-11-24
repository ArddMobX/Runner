package com.runner.app

import com.runner.app.data.FileChange
import com.runner.app.data.FileChangeKind
import com.runner.app.ui.ChatListItem
import com.runner.app.ui.ChatMessage
import com.runner.app.ui.MessageRole
import com.runner.app.ui.TurnSegment
import com.runner.app.ui.buildTurnSegments
import com.runner.app.ui.extractJsonStringField
import com.runner.app.ui.extractToolErrorPreview
import com.runner.app.ui.formatActionRunHeadline
import com.runner.app.ui.formatActionRunHeadlineParts
import com.runner.app.ui.formatToolStepDisplay
import com.runner.app.ui.groupChatItems
import com.runner.app.ui.shouldShowBottomStatusIndicator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentTurnTimelineTest {

    // --- buildTurnSegments ---

    @Test
    fun buildTurnSegments_emptyListReturnsEmpty() {
        assertTrue(buildTurnSegments(emptyList()).isEmpty())
    }

    @Test
    fun buildTurnSegments_interleavesPlainCommentaryBetweenActionRuns() {
        val t1 = ChatMessage(id = "t1", role = MessageRole.TOOL_EXECUTION, content = "", toolName = "read_file", toolArgs = """{"path":"a.kt"}""")
        val t2 = ChatMessage(id = "t2", role = MessageRole.TOOL_EXECUTION, content = "", toolName = "read_file", toolArgs = """{"path":"b.kt"}""")
        val n1 = ChatMessage(id = "n1", role = MessageRole.NARRATION, content = "Нашёл проблему в настройках, сейчас поправлю.")
        val t3 = ChatMessage(id = "t3", role = MessageRole.TOOL_EXECUTION, content = "", toolName = "write_file", toolArgs = """{"path":"a.kt"}""")
        val a1 = ChatMessage(id = "a1", role = MessageRole.ASSISTANT, content = "Правка внесена, прогоню проверку.")
        val t4 = ChatMessage(id = "t4", role = MessageRole.TOOL_EXECUTION, content = "", toolName = "run_shell_command", toolArgs = """{"command":"git diff"}""")

        val segments = buildTurnSegments(listOf(t1, t2, n1, t3, a1, t4))

        assertEquals(5, segments.size)
        val run1 = segments[0] as TurnSegment.ActionRun
        assertEquals(listOf("t1", "t2"), run1.tools.map { it.id })
        assertEquals("run_t1", run1.id)

        val commentary1 = segments[1] as TurnSegment.Commentary
        assertEquals("n1", commentary1.message.id)

        val run2 = segments[2] as TurnSegment.ActionRun
        assertEquals(listOf("t3"), run2.tools.map { it.id })

        val commentary2 = segments[3] as TurnSegment.Commentary
        assertEquals("a1", commentary2.message.id)

        val run3 = segments[4] as TurnSegment.ActionRun
        assertEquals(listOf("t4"), run3.tools.map { it.id })
    }

    @Test
    fun buildTurnSegments_attachesReasoningChronologicallyToActionRunOrCommentary() {
        val thoughtBeforeRun = ChatMessage(
            id = "th1",
            role = MessageRole.ASSISTANT,
            content = "",
            reasoningText = "Сначала посмотрю SettingsScreen.kt",
            reasoningMs = 1_500L
        )
        val t1 = ChatMessage(id = "t1", role = MessageRole.TOOL_EXECUTION, content = "", toolName = "read_file")
        val thoughtBeforeText = ChatMessage(
            id = "th2",
            role = MessageRole.ASSISTANT,
            content = "",
            reasoningText = "Вижу дубль.",
            reasoningMs = 800L
        )
        val commentary = ChatMessage(
            id = "a1",
            role = MessageRole.ASSISTANT,
            content = "Убрал дублирующий блок.",
            reasoningText = "Проверяю отступы.",
            reasoningMs = 600L
        )
        val trailingThought = ChatMessage(
            id = "th3",
            role = MessageRole.ASSISTANT,
            content = "",
            reasoningText = "Финальная мысль",
            reasoningMs = 2_000L
        )

        val segments = buildTurnSegments(
            listOf(thoughtBeforeRun, t1, thoughtBeforeText, commentary, trailingThought)
        )

        assertEquals(3, segments.size)
        val run = segments[0] as TurnSegment.ActionRun
        assertEquals("Сначала посмотрю SettingsScreen.kt", run.reasoningText)
        assertEquals(1_500L, run.reasoningMs)

        val comm = segments[1] as TurnSegment.Commentary
        assertEquals("Вижу дубль.\n\nПроверяю отступы.", comm.reasoningText)
        assertEquals(1_400L, comm.reasoningMs)

        val thought = segments[2] as TurnSegment.Thought
        assertEquals("thought_th3", thought.id)
        assertEquals("Финальная мысль", thought.text)
        assertEquals(2_000L, thought.durationMs)
    }

    @Test
    fun buildTurnSegments_planFlushesPrecedingToolsAndThought() {
        val t1 = ChatMessage(id = "t1", role = MessageRole.TOOL_EXECUTION, content = "", toolName = "list_dir")
        val thought = ChatMessage(id = "th1", role = MessageRole.ASSISTANT, content = "", reasoningText = "Составляю план", reasoningMs = 1_200L)
        val plan = ChatMessage(id = "p1", role = MessageRole.PLAN, content = "1. read_file — прочитать файл")
        val t2 = ChatMessage(id = "t2", role = MessageRole.TOOL_EXECUTION, content = "", toolName = "read_file")

        val segments = buildTurnSegments(listOf(t1, thought, plan, t2))

        assertEquals(4, segments.size)
        assertTrue(segments[0] is TurnSegment.ActionRun)
        assertTrue(segments[1] is TurnSegment.Thought)
        assertTrue(segments[2] is TurnSegment.Plan)
        assertTrue(segments[3] is TurnSegment.ActionRun)
    }

    @Test
    fun groupChatItems_preservesReasoningOnlyAssistantInCompletedTurn() {
        val u1 = ChatMessage(id = "u1", role = MessageRole.USER, content = "Проверь файлы")
        val thought = ChatMessage(
            id = "th1",
            role = MessageRole.ASSISTANT,
            content = "",
            reasoningText = "Нужно прочитать конфиг перед ответом",
            reasoningMs = 2_100L
        )
        val t1 = ChatMessage(id = "t1", role = MessageRole.TOOL_EXECUTION, content = "", toolName = "read_file")
        val finalMsg = ChatMessage(id = "a_final", role = MessageRole.ASSISTANT, content = "Готово, конфиг проверен.")

        val items = groupChatItems(listOf(u1, thought, t1, finalMsg), activeTurn = false)

        assertEquals(3, items.size)
        val activity = items[1] as ChatListItem.TaskActivity
        assertEquals(listOf("th1", "t1"), activity.messages.map { it.id })
        val segments = buildTurnSegments(activity.messages)
        assertEquals(1, segments.size)
        val run = segments[0] as TurnSegment.ActionRun
        assertEquals("Нужно прочитать конфиг перед ответом", run.reasoningText)
    }

    // --- shouldShowBottomStatusIndicator ---

    @Test
    fun shouldShowBottomStatusIndicator_hidesDuplicateWhenToolActiveOrStreaming() {
        assertFalse(shouldShowBottomStatusIndicator(isRunning = false, hasActiveToolInFeed = false, streamingText = ""))
        assertFalse(shouldShowBottomStatusIndicator(isRunning = true, hasActiveToolInFeed = true, streamingText = ""))
        assertFalse(shouldShowBottomStatusIndicator(isRunning = true, hasActiveToolInFeed = false, streamingText = "Ответ…"))
        assertTrue(shouldShowBottomStatusIndicator(isRunning = true, hasActiveToolInFeed = false, streamingText = ""))
        assertTrue(shouldShowBottomStatusIndicator(isRunning = true, hasActiveToolInFeed = false, streamingText = "   "))
    }

    // --- extractJsonStringField ---

    @Test
    fun extractJsonStringField_handlesPathsEscapesAndUnicode() {
        val json = """{"path":"/storage/emulated/0/Download/report \"2026\".txt","command":"echo \"hi\"\nls","unicode":"\u0041\u0042"}"""
        assertEquals("/storage/emulated/0/Download/report \"2026\".txt", extractJsonStringField(json, "path"))
        assertEquals("echo \"hi\"\nls", extractJsonStringField(json, "command"))
        assertEquals("AB", extractJsonStringField(json, "unicode"))
        assertEquals("", extractJsonStringField(json, "missing"))
        assertEquals("", extractJsonStringField(null, "path"))
        assertEquals("", extractJsonStringField("not-json", "path"))
    }

    // --- extractToolErrorPreview ---

    @Test
    fun extractToolErrorPreview_stripsUntrustedEnvelopesAndPicksFirstMeaningfulLine() {
        val okMsg = ChatMessage(role = MessageRole.TOOL_EXECUTION, content = "", isError = false, toolOutput = "fail")
        assertNull(extractToolErrorPreview(okMsg))

        val withSummary = ChatMessage(
            role = MessageRole.TOOL_EXECUTION,
            content = "",
            isError = true,
            toolSummary = "Exit code 1: Permission denied"
        )
        assertEquals("Exit code 1: Permission denied", extractToolErrorPreview(withSummary))

        val withEnvelope = ChatMessage(
            role = MessageRole.TOOL_EXECUTION,
            content = "",
            isError = true,
            toolSummary = "OK",
            toolOutput = """
                ```НЕДОВЕРЕННЫЕ-ДАННЫЕ
                resolved_path: /storage/emulated/0/Download/secret.txt
                FileNotFoundException: secret.txt (No such file or directory)
                ```КОНЕЦ-НЕДОВЕРЕННЫХ-ДАННЫХ
            """.trimIndent()
        )
        assertEquals(
            "FileNotFoundException: secret.txt (No such file or directory)",
            extractToolErrorPreview(withEnvelope)
        )
    }

    // --- formatToolStepDisplay ---

    @Test
    fun formatToolStepDisplay_distinguishesRunningPastCreatedModifiedDeclinedAndError() {
        val reading = ChatMessage(
            role = MessageRole.TOOL_EXECUTION,
            content = "",
            toolName = "builtin:read_file",
            toolArgs = """{"path":"/storage/emulated/0/app/SettingsScreen.kt"}""",
            isRunning = true
        )
        val readingDisplay = formatToolStepDisplay(reading)
        assertEquals("Читает", readingDisplay.verb)
        assertEquals("SettingsScreen.kt", readingDisplay.target)
        assertTrue(readingDisplay.isCodeTarget)
        assertEquals("Читает SettingsScreen.kt", readingDisplay.plainText())

        val readDone = reading.copy(isRunning = false)
        assertEquals("Прочитал SettingsScreen.kt", formatToolStepDisplay(readDone).plainText())

        val created = ChatMessage(
            role = MessageRole.TOOL_EXECUTION,
            content = "",
            toolName = "write_file",
            toolArgs = """{"path":"notes/todo.md"}""",
            fileChanges = listOf(FileChange(FileChangeKind.CREATED, "/sdcard/notes/todo.md", linesAdded = 12))
        )
        assertEquals("Создал todo.md", formatToolStepDisplay(created).plainText())

        val modified = created.copy(
            fileChanges = listOf(FileChange(FileChangeKind.MODIFIED, "/sdcard/notes/todo.md", linesAdded = 4, linesRemoved = 2))
        )
        assertEquals("Изменил todo.md", formatToolStepDisplay(modified).plainText())

        val shell = ChatMessage(
            role = MessageRole.TOOL_EXECUTION,
            content = "",
            toolName = "run_shell_command",
            toolArgs = """{"command":"  git   status  \n echo done"}""",
            isError = true,
            toolOutput = "fatal: not a git repository"
        )
        val shellDisplay = formatToolStepDisplay(shell)
        assertEquals("Выполнил", shellDisplay.verb)
        assertEquals("git status", shellDisplay.target)
        assertTrue(shellDisplay.isCodeTarget)
        assertEquals("fatal: not a git repository", shellDisplay.errorPreview)

        val declined = ChatMessage(
            role = MessageRole.TOOL_EXECUTION,
            content = "Удаление отменено",
            toolName = "delete_file",
            toolArgs = """{"path":"/sdcard/Download/archive.zip"}""",
            isDeclined = true
        )
        val declinedDisplay = formatToolStepDisplay(declined)
        assertEquals("Отклонено:", declinedDisplay.verb)
        assertEquals("archive.zip", declinedDisplay.target)
        assertTrue(declinedDisplay.isCodeTarget)

        val webSearch = ChatMessage(
            role = MessageRole.TOOL_EXECUTION,
            content = "",
            toolName = "web_search",
            toolArgs = """{"query":"kotlin compose 1.6"}"""
        )
        val webDisplay = formatToolStepDisplay(webSearch)
        assertEquals("Искал в сети", webDisplay.verb)
        assertEquals("«kotlin compose 1.6»", webDisplay.target)
        assertFalse(webDisplay.isCodeTarget)

        val webFetch = ChatMessage(
            role = MessageRole.TOOL_EXECUTION,
            content = "",
            toolName = "web_fetch",
            toolArgs = """{"url":"https://www.developer.android.com/jetpack/compose"}"""
        )
        val fetchDisplay = formatToolStepDisplay(webFetch)
        assertEquals("Загрузил", fetchDisplay.verb)
        assertEquals("developer.android.com", fetchDisplay.target)
        assertTrue(fetchDisplay.isCodeTarget)
    }

    // --- formatActionRunHeadline & formatActionRunHeadlineParts ---

    @Test
    fun formatActionRunHeadline_liveAndSingleStep() {
        assertEquals("Действия", formatActionRunHeadline(emptyList()))

        val t1 = ChatMessage(
            role = MessageRole.TOOL_EXECUTION,
            content = "",
            toolName = "read_file",
            toolArgs = """{"path":"a.kt"}"""
        )
        val t2 = ChatMessage(
            role = MessageRole.TOOL_EXECUTION,
            content = "",
            toolName = "run_shell_command",
            toolArgs = """{"command":"git status"}""",
            isRunning = true
        )
        assertEquals("Выполняет git status… · шаг 2", formatActionRunHeadline(listOf(t1, t2)))
        val liveParts = formatActionRunHeadlineParts(listOf(t1, t2))
        assertTrue(liveParts.any { it.text == "git status" && it.isCode })

        val failedSingle = t2.copy(isRunning = false, isError = true)
        assertEquals("Выполнил git status · ошибка", formatActionRunHeadline(listOf(failedSingle)))
        val failedParts = formatActionRunHeadlineParts(listOf(failedSingle))
        assertTrue(failedParts.any { it.text == "git status" && it.isCode })
        assertTrue(failedParts.any { it.text == " · ошибка" && it.isError })
    }

    @Test
    fun formatActionRunHeadline_multiStepDevConsoleSummaries() {
        val r1 = ChatMessage(role = MessageRole.TOOL_EXECUTION, content = "", toolName = "read_file", toolArgs = """{"path":"SettingsScreen.kt"}""")
        val r2 = ChatMessage(role = MessageRole.TOOL_EXECUTION, content = "", toolName = "read_file", toolArgs = """{"path":"Motion.kt"}""")
        val sh = ChatMessage(role = MessageRole.TOOL_EXECUTION, content = "", toolName = "run_shell_command", toolArgs = """{"command":"git status"}""")

        assertEquals(
            "Прочитал SettingsScreen.kt, Motion.kt, выполнил git status",
            formatActionRunHeadline(listOf(r1, r2, sh))
        )
        val parts = formatActionRunHeadlineParts(listOf(r1, r2, sh))
        assertEquals(
            listOf("SettingsScreen.kt", "Motion.kt", "git status"),
            parts.filter { it.isCode }.map { it.text }
        )

        val dir = ChatMessage(role = MessageRole.TOOL_EXECUTION, content = "", toolName = "list_dir", toolArgs = """{"path":"/sdcard/Download"}""")
        assertEquals(
            "Прочитал SettingsScreen.kt, просмотрел Download/",
            formatActionRunHeadline(listOf(r1, dir))
        )

        val manyReads = (1..4).map { idx ->
            ChatMessage(role = MessageRole.TOOL_EXECUTION, content = "", toolName = "read_file", toolArgs = """{"path":"file$idx.kt"}""")
        }
        val twoEdits = (1..2).map { idx ->
            ChatMessage(role = MessageRole.TOOL_EXECUTION, content = "", toolName = "write_file", toolArgs = """{"path":"edit$idx.kt"}""")
        }
        val twoCmds = listOf(
            ChatMessage(role = MessageRole.TOOL_EXECUTION, content = "", toolName = "run_shell_command", toolArgs = """{"command":"cmd1"}"""),
            ChatMessage(role = MessageRole.TOOL_EXECUTION, content = "", toolName = "run_shell_command", toolArgs = """{"command":"cmd2"}""", isError = true)
        )
        assertEquals(
            "Прочитал 4 файла, изменил 2 файла, выполнил 2 команды · 1 ошибка",
            formatActionRunHeadline(manyReads + twoEdits + twoCmds)
        )

        val declinedRun = listOf(
            r1,
            ChatMessage(role = MessageRole.TOOL_EXECUTION, content = "", toolName = "delete_file", toolArgs = """{"path":"old.log"}""", isDeclined = true)
        )
        assertEquals(
            "Прочитал SettingsScreen.kt · отклонено (1)",
            formatActionRunHeadline(declinedRun)
        )

        val allDeclined = listOf(
            ChatMessage(role = MessageRole.TOOL_EXECUTION, content = "", toolName = "delete_file", toolArgs = """{"path":"a.log"}""", isDeclined = true),
            ChatMessage(role = MessageRole.TOOL_EXECUTION, content = "", toolName = "delete_file", toolArgs = """{"path":"b.log"}""", isDeclined = true)
        )
        assertEquals("Отклонено 2 действия", formatActionRunHeadline(allDeclined))
    }
}
