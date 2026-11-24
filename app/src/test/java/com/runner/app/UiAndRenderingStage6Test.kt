package com.runner.app

import com.runner.app.data.ChatAttachment
import com.runner.app.data.FileChange
import com.runner.app.data.FileChangeKind
import com.runner.app.data.WebSource
import com.runner.app.ui.ChatMessage
import com.runner.app.ui.MessageRole
import com.runner.app.ui.PlanStepState
import com.runner.app.ui.commands.ChatMarkdownExporter
import com.runner.app.ui.components.FileEntry
import com.runner.app.ui.components.MarkdownElement
import com.runner.app.ui.components.absolutePath
import com.runner.app.ui.components.canSubmitOnEnter
import com.runner.app.ui.components.computeDownsampleSize
import com.runner.app.ui.components.parseCellSortKey
import com.runner.app.ui.components.parseMarkdown
import com.runner.app.ui.components.resolveFileEntryAbsolutePath
import com.runner.app.ui.components.sanitizeMarkdown
import com.runner.app.ui.components.shouldUseInlineChips
import com.runner.app.ui.deduplicateCustomPrompts
import com.runner.app.ui.formatPlanHeaderSubtitle
import com.runner.app.ui.isPersistableReverseProxyUrl
import com.runner.app.ui.mergeUniqueSuggestion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UiAndRenderingStage6Test {

    // --- SettingsScreen helpers ---

    @Test
    fun reverseProxyUrlOnlyPersistsEmptyOrValidHttpUrl() {
        assertTrue(isPersistableReverseProxyUrl(""))
        assertTrue(isPersistableReverseProxyUrl("   "))
        assertTrue(isPersistableReverseProxyUrl("https://my-proxy.workers.dev/v1"))
        assertTrue(isPersistableReverseProxyUrl("http://127.0.0.1:8080/v1"))

        assertFalse(isPersistableReverseProxyUrl("h"))
        assertFalse(isPersistableReverseProxyUrl("https://"))
        assertFalse(isPersistableReverseProxyUrl("my-proxy.workers.dev"))
        assertFalse(isPersistableReverseProxyUrl("ftp://proxy.example.com/v1"))
    }

    @Test
    fun mergeUniqueSuggestionDeduplicatesTrimmedAndCaseInsensitive() {
        val initial = listOf("Проверить заряд")
        assertEquals(initial, mergeUniqueSuggestion(initial, "   "))
        assertEquals(initial, mergeUniqueSuggestion(initial, "проверить заряд"))
        assertEquals(initial, mergeUniqueSuggestion(initial, "  Проверить заряд  "))
        assertEquals(
            listOf("Проверить заряд", "Найти дубликаты"),
            mergeUniqueSuggestion(initial, "  Найти дубликаты ")
        )
    }

    // --- ChatScreen helpers ---

    @Test
    fun deduplicateCustomPromptsDropsDuplicatesAgainstDefaultsAndSelf() {
        val defaults = listOf("Прочитай буфер обмена", "Найди мусор")
        val custom = listOf(
            "  ",
            "прочитай буфер обмена",
            "Проверить батарею",
            "Проверить БАТАРЕЮ ",
            "Сделай бэкап"
        )
        assertEquals(
            listOf("Проверить батарею", "Сделай бэкап"),
            deduplicateCustomPrompts(defaults, custom)
        )
    }

    @Test
    fun formatPlanHeaderSubtitleNeverReportsDoneBeforeApprovalOrWithoutTools() {
        assertNull(
            formatPlanHeaderSubtitle(
                planStepsCount = 0,
                stepStates = emptyList(),
                planAwaiting = false,
                isActiveTurn = false
            )
        )

        val awaiting = formatPlanHeaderSubtitle(
            planStepsCount = 3,
            stepStates = emptyList(),
            planAwaiting = true,
            isActiveTurn = true
        )
        assertNotNull(awaiting)
        assertTrue(awaiting!!.startsWith("Ожидает утверждения"))
        assertFalse(awaiting.contains("План выполнен"))

        val runningNoTools = formatPlanHeaderSubtitle(
            planStepsCount = 2,
            stepStates = emptyList(),
            planAwaiting = false,
            isActiveTurn = true
        )
        assertNotNull(runningNoTools)
        assertTrue(runningNoTools!!.startsWith("Выполняется план"))
        assertFalse(runningNoTools.contains("План выполнен"))

        val idleNoTools = formatPlanHeaderSubtitle(
            planStepsCount = 2,
            stepStates = emptyList(),
            planAwaiting = false,
            isActiveTurn = false
        )
        assertNotNull(idleNoTools)
        assertTrue(idleNoTools!!.startsWith("План ·"))
        assertFalse(idleNoTools.contains("План выполнен"))

        assertEquals(
            "Шаг 2 из 3",
            formatPlanHeaderSubtitle(
                planStepsCount = 3,
                stepStates = listOf(PlanStepState.DONE, PlanStepState.RUNNING, PlanStepState.PENDING),
                planAwaiting = false,
                isActiveTurn = true
            )
        )

        assertEquals(
            "План выполнен",
            formatPlanHeaderSubtitle(
                planStepsCount = 2,
                stepStates = listOf(PlanStepState.DONE, PlanStepState.DONE),
                planAwaiting = false,
                isActiveTurn = false
            )
        )
    }

    // --- ChatInputBar slash commands while running ---

    @Test
    fun canSubmitOnEnterAllowsHelpAndStopWhileRunning() {
        assertTrue(
            canSubmitOnEnter(
                rawText = "/stop",
                hasContent = true,
                isRunning = true,
                isListening = false,
                hasCommandHandler = true
            )
        )
        assertTrue(
            canSubmitOnEnter(
                rawText = "/help",
                hasContent = true,
                isRunning = true,
                isListening = false,
                hasCommandHandler = true
            )
        )
        assertTrue(
            canSubmitOnEnter(
                rawText = "/?",
                hasContent = true,
                isRunning = true,
                isListening = false,
                hasCommandHandler = true
            )
        )
        assertTrue(
            canSubmitOnEnter(
                rawText = "/hel",
                hasContent = true,
                isRunning = true,
                isListening = false,
                hasCommandHandler = true
            )
        )

        // Regular messages and non-running commands stay blocked while running
        assertFalse(
            canSubmitOnEnter(
                rawText = "привет",
                hasContent = true,
                isRunning = true,
                isListening = false,
                hasCommandHandler = true
            )
        )
        assertFalse(
            canSubmitOnEnter(
                rawText = "/new",
                hasContent = true,
                isRunning = true,
                isListening = false,
                hasCommandHandler = true
            )
        )
        assertFalse(
            canSubmitOnEnter(
                rawText = "/stop",
                hasContent = true,
                isRunning = true,
                isListening = false,
                hasCommandHandler = false
            )
        )
        assertFalse(
            canSubmitOnEnter(
                rawText = "/stop",
                hasContent = true,
                isRunning = true,
                isListening = true,
                hasCommandHandler = true
            )
        )
    }

    // --- MediaPreview helpers ---

    @Test
    fun fileEntryAbsolutePathResolvesRelativeAndRootedFolders() {
        assertEquals(
            "/storage/emulated/0/DCIM/Camera/photo.jpg",
            FileEntry(name = "photo.jpg", folder = "DCIM/Camera", size = "2 MB").absolutePath
        )
        assertEquals(
            "/storage/emulated/0/Download/doc.pdf",
            resolveFileEntryAbsolutePath("storage/emulated/0/Download", "doc.pdf")
        )
        assertEquals(
            "/sdcard/Music/song.mp3",
            resolveFileEntryAbsolutePath("sdcard/Music", "song.mp3")
        )
        assertEquals(
            "/data/local/tmp/out.png",
            resolveFileEntryAbsolutePath(null, "/data/local/tmp/out.png")
        )
        assertNull(resolveFileEntryAbsolutePath(null, "relative.png"))
    }

    @Test
    fun computeDownsampleSizePreventsOomOnLongScreenshots() {
        assertEquals(16, computeDownsampleSize(width = 1080, height = 15000, targetSize = 1440))
        assertEquals(16, computeDownsampleSize(width = 15000, height = 1080, targetSize = 1440))
        assertEquals(1, computeDownsampleSize(width = 800, height = 600, targetSize = 1440))
        assertEquals(1, computeDownsampleSize(width = 0, height = 100, targetSize = 160))
    }

    // --- MarkdownView helpers ---

    @Test
    fun shouldUseInlineChipsRejectsRawMarkdownAndLinks() {
        assertTrue(shouldUseInlineChips("Папки: `Download` `DCIM` `Pictures`"))
        assertFalse(shouldUseInlineChips("**Файл:** `main.kt`"))
        assertFalse(shouldUseInlineChips("Смотри [доки](https://example.com) и `cmd`"))
        assertFalse(shouldUseInlineChips("URL https://example.com `cmd`"))
        assertFalse(shouldUseInlineChips("*курсив* `foo`"))
    }

    @Test
    fun parseCellSortKeyHandlesDecimalCommaAndGroupedNumbers() {
        assertEquals(3.14, parseCellSortKey("3,14").first!!, 0.0001)
        assertEquals(1.5 * 1024.0 * 1024.0, parseCellSortKey("1,5 МБ").first!!, 0.01)
        assertEquals(1234.56, parseCellSortKey("1.234,56").first!!, 0.0001)
        assertEquals(1234.56, parseCellSortKey("1,234.56").first!!, 0.0001)
        assertEquals(1000.0, parseCellSortKey("1,000").first!!, 0.0001)
        assertEquals(1234.5, parseCellSortKey("1 234,5").first!!, 0.0001)
        assertEquals(85.0, parseCellSortKey("85%").first!!, 0.0001)
        assertNull(parseCellSortKey("текст"))
    }

    @Test
    fun sanitizeMarkdownPreservesInlineCodeAndBlockMath() {
        assertEquals(
            "Используй `\\text{foo}`, `50\\%` и `\$100\$`, а в тексте 50% и 12 GB",
            sanitizeMarkdown("Используй `\\text{foo}`, `50\\%` и `\$100\$`, а в тексте 50\\% и \$12 GB\$")
        )

        val blockMath = "Формула:\n\$\$\n\\text{Total} = 50\\%\n\$\$\nИтог: \$50%\$"
        assertEquals(
            "Формула:\n\$\$\n\\text{Total} = 50\\%\n\$\$\nИтог: 50%",
            sanitizeMarkdown(blockMath)
        )

        val bracketMath = "\\[\\text{Share} = 75\\%\\] и \\text{данные}"
        assertEquals(
            "\\[\\text{Share} = 75\\%\\] и данные",
            sanitizeMarkdown(bracketMath)
        )
    }

    @Test
    fun parseMarkdownHeadersRequireWhitespaceAndStripH5H6Hashes() {
        val hashtag = parseMarkdown("#hashtag").single()
        assertTrue(hashtag is MarkdownElement.Paragraph)
        assertEquals("#hashtag", (hashtag as MarkdownElement.Paragraph).text)

        val include = parseMarkdown("#include <stdio.h>").single()
        assertTrue(include is MarkdownElement.Paragraph)

        val h5 = parseMarkdown("##### Пятый уровень").single() as MarkdownElement.Header
        assertEquals(4, h5.level)
        assertEquals("Пятый уровень", h5.content)

        val h6 = parseMarkdown("###### Шестой уровень").single() as MarkdownElement.Header
        assertEquals(4, h6.level)
        assertEquals("Шестой уровень", h6.content)

        val csharp = parseMarkdown("## C#").single() as MarkdownElement.Header
        assertEquals(2, csharp.level)
        assertEquals("C#", csharp.content)

        val closed = parseMarkdown("## Раздел ##").single() as MarkdownElement.Header
        assertEquals(2, closed.level)
        assertEquals("Раздел", closed.content)
    }

    // --- ChatMarkdownExporter ---

    @Test
    fun exporterKeepsUserHeadingForAttachmentOnlyMessagesAndEscapesMarkdown() {
        val messages = listOf(
            ChatMessage(
                role = MessageRole.USER,
                content = "",
                imageUris = listOf("/cache/img1.jpg"),
                attachments = listOf(
                    ChatAttachment(
                        path = "/cache/report.pdf",
                        name = "report_[v1]*final*.pdf"
                    )
                )
            ),
            ChatMessage(
                role = MessageRole.TOOL_EXECUTION,
                content = "Готово",
                toolName = "web_search",
                sources = listOf(
                    WebSource(
                        url = "https://en.wikipedia.org/wiki/Rust_(programming_language)",
                        title = "Rust [lang]",
                        domain = "en.wikipedia.org"
                    )
                ),
                fileChanges = listOf(
                    FileChange(
                        kind = FileChangeKind.CREATED,
                        path = "/storage/emulated/0/file`1.txt"
                    )
                )
            )
        )

        val md = ChatMarkdownExporter.build(
            title = "Чат\n## Инъекция",
            providerName = "OpenRouter",
            modelName = "model_1",
            messages = messages
        )

        assertTrue(md.startsWith("# Чат ## Инъекция\n"))
        assertTrue(md.contains("## Пользователь"))
        assertTrue(md.contains("Вложения: 1 изображений."))
        assertTrue(md.contains("report\\_\\[v1\\]\\*final\\*.pdf"))
        assertTrue(md.contains("[Rust \\[lang\\]](https://en.wikipedia.org/wiki/Rust_%28programming_language%29)"))
        assertTrue(md.contains("``file`1.txt``"))
    }
}
