package com.runner.app.ui.commands

import com.runner.app.data.FileChangeKind
import com.runner.app.data.WebSource
import com.runner.app.ui.ChatMessage
import com.runner.app.ui.MessageRole
import com.runner.app.util.SecretRedactor

/**
 * Собирает Markdown для /export.
 *
 * В экспорт идут реплики пользователя, ответы модели, планы и сводки
 * инструментов. Служебные баннеры и полные выводы инструментов не тянем:
 * файл должен читаться, а не весить мегабайты.
 */
object ChatMarkdownExporter {

    fun build(
        title: String,
        providerName: String,
        modelName: String,
        messages: List<ChatMessage>
    ): String {
        val visible = messages.filter { it.role != MessageRole.SYSTEM_INFO }
        if (visible.isEmpty()) return ""

        val markdown = buildString {
            append("# ").append(escapeHeadingLine(title.ifBlank { "Чат Runner" })).append("\n\n")
            if (providerName.isNotBlank() || modelName.isNotBlank()) {
                append("Провайдер: ").append(escapeMarkdownText(providerName.ifBlank { "не выбран" })).append("  \n")
                append("Модель: ").append(escapeMarkdownText(modelName.ifBlank { "не выбрана" })).append("\n\n")
            }
            append("---\n")

            visible.forEach { message ->
                when (message.role) {
                    MessageRole.USER -> {
                        val hasUserContent = message.content.isNotBlank() ||
                            message.imageUris.isNotEmpty() ||
                            message.attachments.isNotEmpty()
                        if (hasUserContent) {
                            append("\n## Пользователь\n")
                            if (message.content.isNotBlank()) {
                                append("\n").append(message.content.trim()).append("\n")
                            }
                            if (message.imageUris.isNotEmpty()) {
                                append("\nВложения: ${message.imageUris.size} изображений.\n")
                            }
                            if (message.attachments.isNotEmpty()) {
                                append("\nФайлы:\n")
                                message.attachments.forEach { attachment ->
                                    append("- ").append(escapeMarkdownText(attachment.name)).append('\n')
                                }
                            }
                        }
                    }

                    MessageRole.ASSISTANT -> {
                        if (message.content.isNotBlank()) {
                            append("\n## Ассистент\n\n")
                            append(message.content.trim()).append("\n")
                        }
                    }

                    MessageRole.NARRATION -> {
                        if (message.content.isNotBlank()) {
                            append("\n> ")
                            append(message.content.trim().lineSequence().joinToString("\n> "))
                            append("\n")
                        }
                    }

                    MessageRole.PLAN -> {
                        if (message.content.isNotBlank()) {
                            append("\n## План\n\n")
                            append(message.content.trim()).append("\n")
                        }
                    }

                    MessageRole.TOOL_EXECUTION -> {
                        val toolName = message.toolName
                            ?.substringAfterLast(':')
                            ?.takeIf { it.isNotBlank() }
                        if (toolName != null) {
                            append("\n### Инструмент ").append(inlineCodeSpan(toolName)).append("\n\n")
                            val body = message.toolSummary
                                ?.takeIf { it.isNotBlank() }
                                ?: message.content.takeIf { it.isNotBlank() }
                            if (body != null) {
                                append(body.trim()).append("\n")
                            }
                            appendSources(message.sources)
                        }
                    }

                    MessageRole.SYSTEM_INFO -> Unit
                }
            }

            val changes = messages.flatMap { it.fileChanges }
            if (changes.isNotEmpty()) {
                append("\n## Файлы\n\n")
                changes.forEach { change ->
                    append("- ").append(inlineCodeSpan(change.displayPath)).append(": ")
                    append(changeAction(change.kind))
                    if (change.itemCount > 1) {
                        append(" (").append(change.itemCount).append(" файлов)")
                    }
                    if (change.linesAdded > 0 || change.linesRemoved > 0) {
                        append(" (+").append(change.linesAdded)
                        append("/-").append(change.linesRemoved).append(")")
                    }
                    append("\n")
                }
            }
        }.trimEnd() + "\n"
        return SecretRedactor.redact(markdown)
    }

    private fun changeAction(kind: FileChangeKind): String = when (kind) {
        FileChangeKind.CREATED -> "создан"
        FileChangeKind.MODIFIED -> "изменён"
        FileChangeKind.DELETED -> "удалён"
        FileChangeKind.MOVED -> "перемещён"
        FileChangeKind.COPIED -> "скопирован"
        FileChangeKind.DIR_CREATED -> "создана папка"
    }

    private fun StringBuilder.appendSources(sources: List<WebSource>) {
        if (sources.isEmpty()) return
        append("\nИсточники:\n")
        sources.forEach { source ->
            val label = source.title.ifBlank { source.domain.ifBlank { source.url } }
            append("- [")
            append(escapeMarkdownLinkLabel(label))
            append("](")
            append(escapeMarkdownLinkUrl(source.url))
            append(")\n")
        }
    }

    private fun escapeHeadingLine(raw: String): String =
        escapeMarkdownText(raw.replace(Regex("[\r\n]+"), " ").trim())

    private fun escapeMarkdownText(raw: String): String {
        val singleLine = raw.replace(Regex("[\r\n]+"), " ")
        val sb = StringBuilder(singleLine.length + 8)
        for (ch in singleLine) {
            when (ch) {
                '\\', '*', '_', '`', '[', ']', '<', '>' -> {
                    sb.append('\\').append(ch)
                }
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun escapeMarkdownLinkLabel(raw: String): String =
        escapeMarkdownText(raw)

    private fun escapeMarkdownLinkUrl(raw: String): String =
        raw.trim()
            .replace(" ", "%20")
            .replace("\r", "")
            .replace("\n", "")
            .replace("(", "%28")
            .replace(")", "%29")

    private fun inlineCodeSpan(raw: String): String {
        val singleLine = raw.replace(Regex("[\r\n]+"), " ")
        var maxRun = 0
        var run = 0
        for (ch in singleLine) {
            if (ch == '`') {
                run++
                if (run > maxRun) maxRun = run
            } else {
                run = 0
            }
        }
        val fence = "`".repeat(maxRun + 1)
        val needsPadding = singleLine.startsWith('`') || singleLine.endsWith('`') ||
            (singleLine.startsWith(' ') && singleLine.endsWith(' ') && singleLine.isNotBlank())
        return if (needsPadding) "$fence $singleLine $fence" else "$fence$singleLine$fence"
    }
}
