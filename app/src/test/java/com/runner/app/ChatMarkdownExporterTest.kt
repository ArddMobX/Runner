package com.runner.app

import com.runner.app.data.FileChange
import com.runner.app.data.FileChangeKind
import com.runner.app.data.WebSource
import com.runner.app.ui.ChatMessage
import com.runner.app.ui.MessageRole
import com.runner.app.ui.commands.ChatMarkdownExporter
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatMarkdownExporterTest {

    @Test
    fun exportsDialogAndToolSummaryWithoutServiceBanners() {
        val messages = listOf(
            ChatMessage(role = MessageRole.USER, content = "Привет"),
            ChatMessage(role = MessageRole.ASSISTANT, content = "Здравствуйте"),
            ChatMessage(
                role = MessageRole.TOOL_EXECUTION,
                content = "Ищу в сети",
                toolName = "web_search",
                toolSummary = "3 результата",
                sources = listOf(
                    WebSource(
                        url = "https://example.com/page",
                        title = "Пример",
                        domain = "example.com"
                    )
                ),
                fileChanges = listOf(
                    FileChange(
                        kind = FileChangeKind.MODIFIED,
                        path = "/storage/emulated/0/Download/test.kt",
                        linesAdded = 3,
                        linesRemoved = 1
                    )
                )
            ),
            ChatMessage(role = MessageRole.SYSTEM_INFO, content = "Служебная ошибка")
        )

        val markdown = ChatMarkdownExporter.build(
            title = "Тестовый чат",
            providerName = "OpenAI",
            modelName = "gpt-5",
            messages = messages
        )

        assertTrue(markdown.contains("# Тестовый чат"))
        assertTrue(markdown.contains("Пользователь"))
        assertTrue(markdown.contains("Привет"))
        assertTrue(markdown.contains("Здравствуйте"))
        assertTrue(markdown.contains("web_search"))
        assertTrue(markdown.contains("3 результата"))
        assertTrue(markdown.contains("https://example.com/page"))
        assertTrue(markdown.contains("Download/test.kt"))
        assertTrue(markdown.contains("изменён"))
        assertFalse(markdown.contains("Служебная ошибка"))
    }

    @Test
    fun emptyWhenThereIsNothingToExport() {
        val markdown = ChatMarkdownExporter.build(
            title = "Пусто",
            providerName = "",
            modelName = "",
            messages = listOf(
                ChatMessage(role = MessageRole.SYSTEM_INFO, content = "Ошибка")
            )
        )

        assertTrue(markdown.isBlank())
    }
}
