package com.runner.app.ui.commands

import com.runner.app.data.FileChangeKind
import com.runner.app.data.WebSource
import com.runner.app.ui.ChatMessage
import com.runner.app.ui.MessageRole

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

        return buildString {
            append("# ").append(title.ifBlank { "Чат Runner" }).append("\n\n")
            if (providerName.isNotBlank() || modelName.isNotBlank()) {
                append("Провайдер: ").append(providerName.ifBlank { "не выбран" }).append("  \n")
                append("Модель: ").append(modelName.ifBlank { "не выбрана" }).append("\n\n")
            }
            append("---\n")

            visible.forEach { message ->
                when (message.role) {
                    MessageRole.USER -> {
                        if (message.content.isNotBlank()) {
                            append("\n## Пользователь\n\n")
                            append(message.content.trim()).append("\n")
                        }
                        if (message.imageUris.isNotEmpty()) {
                            append("\nВложения: ${message.imageUris.size} изображений.\n")
                        }
                        if (message.attachments.isNotEmpty()) {
                            append("\nФайлы:\n")
                            message.attachments.forEach { attachment ->
                                append("- ").append(attachment.name).append('\n')
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
                            append("\n### Инструмент `").append(toolName).append("`\n\n")
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
                    append("- `").append(change.displayPath).append("`: ")
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
            append("- [")
            append(source.title.ifBlank { source.domain.ifBlank { source.url } })
            append("](")
            append(source.url)
            append(")\n")
        }
    }
}
