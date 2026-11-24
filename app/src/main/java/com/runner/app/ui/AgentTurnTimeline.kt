package com.runner.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.runner.app.data.FileChange
import com.runner.app.data.FileChangeKind
import com.runner.app.data.WebSource
import com.runner.app.tools.TrashStore
import com.runner.app.ui.components.MarkdownView
import com.runner.app.ui.components.PreviewButton
import com.runner.app.ui.components.RunnerIcons
import com.runner.app.ui.components.StatusPulsingDot
import com.runner.app.ui.components.ToolOutputView
import com.runner.app.ui.components.WebPreviewSource
import com.runner.app.ui.components.findWebPreviewInToolCall
import com.runner.app.ui.theme.MotionTokens
import com.runner.app.ui.theme.RobotoMono
import com.runner.app.ui.theme.RunnerShapes
import com.runner.app.ui.theme.StatusSuccess
import com.runner.app.ui.theme.StatusWarning
import com.runner.app.ui.theme.TypeScale
import com.runner.app.util.PluralUtils

/**
 * Хронологический сегмент внутри хода агента.
 *
 * Позволяет рендерить промежуточные пояснения модели обычным текстом
 * между компактными безрамными группами действий (в стиле Antigravity / Codex / Claude Code),
 * сохраняя единый стабильный ключ хода в LazyColumn.
 */
internal sealed interface TurnSegment {
    data class Plan(val message: ChatMessage) : TurnSegment

    data class Thought(
        val id: String,
        val text: String,
        val durationMs: Long?
    ) : TurnSegment

    data class Commentary(
        val message: ChatMessage,
        val reasoningText: String? = null,
        val reasoningMs: Long? = null
    ) : TurnSegment

    data class ActionRun(
        val id: String,
        val tools: List<ChatMessage>,
        val reasoningText: String? = null,
        val reasoningMs: Long? = null
    ) : TurnSegment
}

/** Представление одного шага инструмента в dev/консольном стиле. */
internal data class ToolStepDisplay(
    val verb: String,
    val target: String? = null,
    val isCodeTarget: Boolean = false,
    val errorPreview: String? = null
) {
    fun plainText(): String = if (target.isNullOrBlank()) verb else "$verb $target"
}

/**
 * Разбивает сообщения одного хода на хронологические сегменты:
 * план, обычный текст пояснений, размышления по месту и компактные группы инструментов.
 */
internal fun buildTurnSegments(messages: List<ChatMessage>): List<TurnSegment> {
    if (messages.isEmpty()) return emptyList()
    val out = mutableListOf<TurnSegment>()
    val pendingTools = mutableListOf<ChatMessage>()
    var pendingRunReasoning: String? = null
    var pendingRunReasoningMs: Long? = null
    var pendingStandaloneReasoning: String? = null
    var pendingStandaloneReasoningMs: Long? = null
    var pendingStandaloneReasoningId: String = "thought"

    fun flushTools() {
        if (pendingTools.isNotEmpty()) {
            out += TurnSegment.ActionRun(
                id = "run_${pendingTools.first().id}",
                tools = pendingTools.toList(),
                reasoningText = pendingRunReasoning,
                reasoningMs = pendingRunReasoningMs
            )
            pendingTools.clear()
            pendingRunReasoning = null
            pendingRunReasoningMs = null
        }
    }

    fun mergeReasoning(a: String?, b: String?): String? = listOfNotNull(
        a?.takeIf { it.isNotBlank() },
        b?.takeIf { it.isNotBlank() }
    ).joinToString("\n\n").takeIf { it.isNotBlank() }

    fun sumMs(a: Long?, b: Long?): Long? {
        val total = (a ?: 0L) + (b ?: 0L)
        return total.takeIf { it > 0L }
    }

    for (message in messages) {
        when (message.role) {
            MessageRole.PLAN -> {
                flushTools()
                if (!pendingStandaloneReasoning.isNullOrBlank()) {
                    out += TurnSegment.Thought(
                        id = "thought_$pendingStandaloneReasoningId",
                        text = pendingStandaloneReasoning!!,
                        durationMs = pendingStandaloneReasoningMs
                    )
                    pendingStandaloneReasoning = null
                    pendingStandaloneReasoningMs = null
                }
                out += TurnSegment.Plan(message)
            }

            MessageRole.NARRATION -> {
                flushTools()
                if (message.content.isNotBlank()) {
                    out += TurnSegment.Commentary(
                        message = message,
                        reasoningText = pendingStandaloneReasoning,
                        reasoningMs = pendingStandaloneReasoningMs
                    )
                    pendingStandaloneReasoning = null
                    pendingStandaloneReasoningMs = null
                }
            }

            MessageRole.ASSISTANT -> {
                if (message.content.isBlank()) {
                    val r = message.reasoningText?.takeIf { it.isNotBlank() }
                    if (r != null) {
                        if (pendingStandaloneReasoning == null) {
                            pendingStandaloneReasoningId = message.id
                        }
                        pendingStandaloneReasoning = mergeReasoning(pendingStandaloneReasoning, r)
                        pendingStandaloneReasoningMs = sumMs(pendingStandaloneReasoningMs, message.reasoningMs)
                    }
                } else {
                    flushTools()
                    out += TurnSegment.Commentary(
                        message = message,
                        reasoningText = mergeReasoning(pendingStandaloneReasoning, message.reasoningText),
                        reasoningMs = sumMs(pendingStandaloneReasoningMs, message.reasoningMs)
                    )
                    pendingStandaloneReasoning = null
                    pendingStandaloneReasoningMs = null
                }
            }

            MessageRole.TOOL_EXECUTION -> {
                if (pendingTools.isEmpty() && !pendingStandaloneReasoning.isNullOrBlank()) {
                    pendingRunReasoning = pendingStandaloneReasoning
                    pendingRunReasoningMs = pendingStandaloneReasoningMs
                    pendingStandaloneReasoning = null
                    pendingStandaloneReasoningMs = null
                }
                pendingTools += message
            }

            else -> Unit
        }
    }

    flushTools()
    if (!pendingStandaloneReasoning.isNullOrBlank()) {
        out += TurnSegment.Thought(
            id = "thought_$pendingStandaloneReasoningId",
            text = pendingStandaloneReasoning!!,
            durationMs = pendingStandaloneReasoningMs
        )
    }

    return out
}

/**
 * Показываем ли нижнюю полосу `AgentStatusIndicator` над полем ввода.
 * Если в ленте уже тикает активный шаг инструмента, нижний дубль скрываем,
 * чтобы на экране не было двух пульсирующих точек и двух таймеров.
 */
internal fun shouldShowBottomStatusIndicator(
    isRunning: Boolean,
    hasActiveToolInFeed: Boolean,
    streamingText: String
): Boolean = isRunning && !hasActiveToolInFeed && streamingText.isBlank()

/**
 * Безопасное извлечение строкового поля из JSON-аргументов инструмента
 * без зависимости от Android `org.json.JSONObject` (работает и в JVM-тестах).
 */
internal fun extractJsonStringField(json: String?, key: String): String {
    if (json.isNullOrBlank() || key.isBlank()) return ""
    val pattern = Regex(""""${Regex.escape(key)}"\s*:\s*"((?:[^"\\]|\\.)*)"""")
    val raw = pattern.find(json)?.groupValues?.getOrNull(1) ?: return ""
    return unescapeJsonString(raw).trim()
}

private fun unescapeJsonString(raw: String): String {
    if (!raw.contains('\\')) return raw
    val sb = StringBuilder(raw.length)
    var i = 0
    while (i < raw.length) {
        val ch = raw[i]
        if (ch == '\\' && i + 1 < raw.length) {
            when (val next = raw[i + 1]) {
                '"' -> sb.append('"')
                '\\' -> sb.append('\\')
                '/' -> sb.append('/')
                'n' -> sb.append('\n')
                'r' -> sb.append('\r')
                't' -> sb.append('\t')
                'b' -> sb.append('\b')
                'f' -> sb.append('\u000C')
                'u' -> {
                    if (i + 5 < raw.length) {
                        val hex = raw.substring(i + 2, i + 6).toIntOrNull(16)
                        if (hex != null) {
                            sb.append(hex.toChar())
                            i += 6
                            continue
                        }
                    }
                    sb.append(next)
                }
                else -> sb.append(next)
            }
            i += 2
        } else {
            sb.append(ch)
            i++
        }
    }
    return sb.toString()
}

private fun shortPathName(raw: String): String {
    val clean = raw.trim().trimEnd('/')
    if (clean.isEmpty()) return ""
    return clean.substringAfterLast('/')
}

private fun compactCommandPreview(raw: String, limit: Int = 52): String {
    val oneLine = raw.lineSequence()
        .map { it.trim() }
        .firstOrNull { it.isNotEmpty() }
        .orEmpty()
        .replace(Regex("\\s+"), " ")
    return if (oneLine.length > limit) oneLine.take(limit - 1).trimEnd() + "…" else oneLine
}

private fun extractDomainName(url: String): String {
    val trimmed = url.trim()
    if (trimmed.isEmpty()) return ""
    val noScheme = trimmed.substringAfter("://", trimmed)
    return noScheme.substringBefore('/').substringBefore('?').removePrefix("www.")
}

internal fun extractToolErrorPreview(message: ChatMessage): String? {
    if (!message.isError) return null
    val summary = message.toolSummary?.trim()
    if (!summary.isNullOrBlank() &&
        !summary.startsWith("Выполнено", ignoreCase = true) &&
        !summary.equals("OK", ignoreCase = true)
    ) {
        return summary.take(110)
    }
    val output = message.toolOutput?.trim().orEmpty()
    if (output.isBlank()) return "Ошибка выполнения"
    val meaningfulLine = output.lineSequence()
        .map { it.trim() }
        .firstOrNull { line ->
            line.isNotEmpty() &&
                !line.startsWith("```") &&
                !line.startsWith("НЕДОВЕРЕННЫЕ-ДАННЫЕ") &&
                !line.startsWith("КОНЕЦ-НЕДОВЕРЕННЫХ-ДАННЫХ") &&
                !line.startsWith("resolved_path")
        }
    return meaningfulLine?.let {
        if (it.length > 110) it.take(109).trimEnd() + "…" else it
    } ?: "Ошибка выполнения"
}

/**
 * Форматирует один шаг инструмента в dev/консольном стиле:
 * - пока работает (`isRunning`): настоящее время («Читает SettingsScreen.kt»);
 * - после завершения: прошедшее время («Прочитал SettingsScreen.kt», «Выполнил git status»);
 * - при отказе: «Отклонено»;
 * - при ошибке: заполняет `errorPreview`.
 */
internal fun formatToolStepDisplay(message: ChatMessage): ToolStepDisplay {
    val cleanTool = message.toolName?.substringAfterLast(':')?.trim().orEmpty()
    val args = message.toolArgs
    val running = message.isRunning
    val errorPreview = extractToolErrorPreview(message)

    if (message.isDeclined) {
        val target = extractPrimaryTarget(cleanTool, args)
        return ToolStepDisplay(
            verb = "Отклонено:",
            target = target ?: message.content.ifBlank { cleanTool.ifBlank { "действие" } },
            isCodeTarget = target != null
        )
    }

    val pathName = shortPathName(extractJsonStringField(args, "path"))
    val srcName = shortPathName(extractJsonStringField(args, "source_path"))
    val zipName = shortPathName(extractJsonStringField(args, "zip_path"))
    val cmd = compactCommandPreview(extractJsonStringField(args, "command"))
    val query = extractJsonStringField(args, "query")
    val ext = extractJsonStringField(args, "extension")
    val urlDomain = extractDomainName(extractJsonStringField(args, "url"))
    val app = extractJsonStringField(args, "app")
    val uiLabel = extractJsonStringField(args, "label")
    val uiText = extractJsonStringField(args, "text")
    val uiPkg = extractJsonStringField(args, "package")

    val createdFile = message.fileChanges.any {
        it.kind == FileChangeKind.CREATED || it.kind == FileChangeKind.DIR_CREATED
    }

    return when (cleanTool) {
        "read_file" -> ToolStepDisplay(
            verb = if (running) "Читает" else "Прочитал",
            target = pathName.ifBlank { "файл" },
            isCodeTarget = pathName.isNotBlank(),
            errorPreview = errorPreview
        )
        "write_file" -> ToolStepDisplay(
            verb = when {
                running -> "Записывает"
                createdFile -> "Создал"
                else -> "Изменил"
            },
            target = pathName.ifBlank { "файл" },
            isCodeTarget = pathName.isNotBlank(),
            errorPreview = errorPreview
        )
        "delete_file" -> ToolStepDisplay(
            verb = if (running) "Удаляет" else "Удалил",
            target = pathName.ifBlank { "файл" },
            isCodeTarget = pathName.isNotBlank(),
            errorPreview = errorPreview
        )
        "create_dir" -> ToolStepDisplay(
            verb = if (running) "Создаёт папку" else "Создал папку",
            target = pathName.ifBlank { null },
            isCodeTarget = pathName.isNotBlank(),
            errorPreview = errorPreview
        )
        "move_file" -> ToolStepDisplay(
            verb = if (running) "Перемещает" else "Переместил",
            target = srcName.ifBlank { "файл" },
            isCodeTarget = srcName.isNotBlank(),
            errorPreview = errorPreview
        )
        "copy_file" -> ToolStepDisplay(
            verb = if (running) "Копирует" else "Скопировал",
            target = srcName.ifBlank { "файл" },
            isCodeTarget = srcName.isNotBlank(),
            errorPreview = errorPreview
        )
        "list_dir" -> ToolStepDisplay(
            verb = if (running) "Открывает" else "Просмотрел",
            target = pathName.ifBlank { "Download" }.let { "$it/" },
            isCodeTarget = true,
            errorPreview = errorPreview
        )
        "get_folder_summary" -> ToolStepDisplay(
            verb = if (running) "Сканирует" else "Просканировал",
            target = pathName.ifBlank { "Download" }.let { "$it/" },
            isCodeTarget = true,
            errorPreview = errorPreview
        )
        "get_storage_summary" -> ToolStepDisplay(
            verb = if (running) "Проверяет память устройства" else "Проверил память устройства",
            errorPreview = errorPreview
        )
        "find_largest_files" -> ToolStepDisplay(
            verb = if (running) "Ищет крупные файлы" else "Нашёл крупные файлы",
            errorPreview = errorPreview
        )
        "find_junk_files" -> ToolStepDisplay(
            verb = if (running) "Ищет кэш и мусор" else "Проверил кэш и мусор",
            errorPreview = errorPreview
        )
        "search_files" -> {
            val target = when {
                query.isNotBlank() -> query.take(40)
                ext.isNotBlank() -> "*.$ext"
                else -> null
            }
            ToolStepDisplay(
                verb = if (running) "Ищет" else "Искал",
                target = target ?: "файлы",
                isCodeTarget = target != null,
                errorPreview = errorPreview
            )
        }
        "create_archive" -> ToolStepDisplay(
            verb = if (running) "Упаковывает ZIP" else "Упаковал ZIP",
            target = zipName.ifBlank { null },
            isCodeTarget = zipName.isNotBlank(),
            errorPreview = errorPreview
        )
        "extract_archive" -> ToolStepDisplay(
            verb = if (running) "Распаковывает" else "Распаковал",
            target = zipName.ifBlank { "архив" },
            isCodeTarget = zipName.isNotBlank(),
            errorPreview = errorPreview
        )
        "organize_downloads" -> ToolStepDisplay(
            verb = if (running) "Разбирает" else "Разобрал",
            target = "Download/",
            isCodeTarget = true,
            errorPreview = errorPreview
        )
        "run_shell_command" -> ToolStepDisplay(
            verb = if (running) "Выполняет" else "Выполнил",
            target = cmd.ifBlank { "shell" },
            isCodeTarget = true,
            errorPreview = errorPreview
        )
        "termux_run" -> ToolStepDisplay(
            verb = "Termux:",
            target = cmd.ifBlank { "команда" },
            isCodeTarget = true,
            errorPreview = errorPreview
        )
        "shizuku_run" -> ToolStepDisplay(
            verb = "Shizuku:",
            target = cmd.ifBlank { "команда" },
            isCodeTarget = true,
            errorPreview = errorPreview
        )
        "web_search" -> ToolStepDisplay(
            verb = if (running) "Ищет в сети" else "Искал в сети",
            target = query.takeIf { it.isNotBlank() }?.let { "«${it.take(42)}${if (it.length > 42) "…" else ""}»" },
            isCodeTarget = false,
            errorPreview = errorPreview
        )
        "web_fetch" -> ToolStepDisplay(
            verb = if (running) "Открывает" else "Загрузил",
            target = urlDomain.ifBlank { "страницу" },
            isCodeTarget = urlDomain.isNotBlank(),
            errorPreview = errorPreview
        )
        "clipboard_read" -> ToolStepDisplay(
            verb = if (running) "Читает буфер обмена" else "Прочитал буфер обмена",
            errorPreview = errorPreview
        )
        "clipboard_write" -> ToolStepDisplay(
            verb = if (running) "Записывает в буфер" else "Записал в буфер",
            errorPreview = errorPreview
        )
        "open_app" -> ToolStepDisplay(
            verb = if (running) "Открывает" else "Открыл",
            target = app.ifBlank { "приложение" },
            isCodeTarget = app.isNotBlank(),
            errorPreview = errorPreview
        )
        "open_url" -> ToolStepDisplay(
            verb = if (running) "Открывает" else "Открыл",
            target = urlDomain.ifBlank { "ссылку" },
            isCodeTarget = urlDomain.isNotBlank(),
            errorPreview = errorPreview
        )
        "call_phone" -> {
            val who = extractJsonStringField(args, "contact_name")
                .ifBlank { extractJsonStringField(args, "target") }
                .ifBlank { extractJsonStringField(args, "phone_number") }
            ToolStepDisplay(
                verb = if (running) "Звонит" else "Вызов",
                target = who.ifBlank { null },
                isCodeTarget = false,
                errorPreview = errorPreview
            )
        }
        "search_contacts" -> ToolStepDisplay(
            verb = if (running) "Ищет контакт" else "Искал контакт",
            target = query.takeIf { it.isNotBlank() }?.let { "«$it»" },
            isCodeTarget = false,
            errorPreview = errorPreview
        )
        "ui_inspect" -> ToolStepDisplay(
            verb = if (running) "Анализирует экран" else "Проверил экран",
            errorPreview = errorPreview
        )
        "ui_click" -> ToolStepDisplay(
            verb = if (running) "Нажимает" else "Нажал",
            target = uiLabel.takeIf { it.isNotBlank() }?.let { "«$it»" },
            isCodeTarget = false,
            errorPreview = errorPreview
        )
        "ui_long_click" -> ToolStepDisplay(
            verb = if (running) "Удерживает" else "Удержал",
            target = uiLabel.takeIf { it.isNotBlank() }?.let { "«$it»" },
            isCodeTarget = false,
            errorPreview = errorPreview
        )
        "ui_type" -> ToolStepDisplay(
            verb = if (running) "Вводит" else "Ввёл",
            target = uiText.takeIf { it.isNotBlank() }?.let { "«${it.take(36)}${if (it.length > 36) "…" else ""}»" },
            isCodeTarget = false,
            errorPreview = errorPreview
        )
        "ui_scroll" -> ToolStepDisplay(
            verb = if (running) "Прокручивает экран" else "Прокрутил экран",
            errorPreview = errorPreview
        )
        "ui_global_action" -> ToolStepDisplay(
            verb = if (running) "Системное действие" else "Выполнил системное действие",
            errorPreview = errorPreview
        )
        "ui_wait", "ui_wait_for" -> ToolStepDisplay(
            verb = if (running) "Ждёт" else "Дождался",
            target = uiText.ifBlank { uiPkg }.ifBlank { "экран" },
            isCodeTarget = uiPkg.isNotBlank() && uiText.isBlank(),
            errorPreview = errorPreview
        )
        "ui_target" -> ToolStepDisplay(
            verb = "Цель:",
            target = uiPkg.ifBlank { "сброшена" },
            isCodeTarget = uiPkg.isNotBlank(),
            errorPreview = errorPreview
        )
        "ui_finish" -> ToolStepDisplay(
            verb = "Завершил Live-задачу",
            errorPreview = errorPreview
        )
        else -> ToolStepDisplay(
            verb = message.content.ifBlank { cleanTool.ifBlank { "Действие" } },
            isCodeTarget = false,
            errorPreview = errorPreview
        )
    }
}

private fun extractPrimaryTarget(cleanTool: String, args: String?): String? = when (cleanTool) {
    "read_file", "write_file", "delete_file", "create_dir", "list_dir", "get_folder_summary" ->
        shortPathName(extractJsonStringField(args, "path")).takeIf { it.isNotBlank() }
    "move_file", "copy_file" ->
        shortPathName(extractJsonStringField(args, "source_path")).takeIf { it.isNotBlank() }
    "run_shell_command", "termux_run", "shizuku_run" ->
        compactCommandPreview(extractJsonStringField(args, "command")).takeIf { it.isNotBlank() }
    "web_fetch", "open_url" ->
        extractDomainName(extractJsonStringField(args, "url")).takeIf { it.isNotBlank() }
    else -> null
}

/** Фрагмент 1-строчной сводки: обычный текст, моноширинный код или пометка ошибки. */
internal data class HeadlinePart(
    val text: String,
    val isCode: Boolean = false,
    val isError: Boolean = false
)

/**
 * Смысловая 1-строчная сводка группы действий в стиле Codex / Antigravity / Dev:
 * - пока выполняется: «Выполняет git status… · шаг 3»
 * - после 1 шага: «Прочитал SettingsScreen.kt»
 * - после серии шагов: «Прочитал SettingsScreen.kt, Motion.kt, выполнил git status»
 *   или «Прочитал 4 файла, изменил 2 файла, выполнил 2 команды · 1 ошибка»
 */
internal fun formatActionRunHeadline(tools: List<ChatMessage>): String =
    formatActionRunHeadlineParts(tools).joinToString("") { it.text }

internal fun formatActionRunHeadlineParts(tools: List<ChatMessage>): List<HeadlinePart> {
    if (tools.isEmpty()) return listOf(HeadlinePart("Действия"))

    val runningTool = tools.lastOrNull { it.isRunning }
    if (runningTool != null) {
        val display = formatToolStepDisplay(runningTool)
        return buildList {
            add(HeadlinePart(display.verb))
            if (!display.target.isNullOrBlank()) {
                add(HeadlinePart(" "))
                add(HeadlinePart(display.target, isCode = display.isCodeTarget))
            }
            add(HeadlinePart(if (tools.size > 1) "… · шаг ${tools.size}" else "…"))
        }
    }

    if (tools.size == 1) {
        val single = tools.first()
        val display = formatToolStepDisplay(single)
        return buildList {
            add(HeadlinePart(display.verb))
            if (!display.target.isNullOrBlank()) {
                add(HeadlinePart(" "))
                add(HeadlinePart(display.target, isCode = display.isCodeTarget))
            }
            if (single.isError) {
                add(HeadlinePart(" · ошибка", isError = true))
            }
        }
    }

    val readFiles = mutableListOf<String>()
    var fileReadCount = 0
    val listedDirs = mutableListOf<String>()
    var dirReadCount = 0
    var storageCheckCount = 0
    val editedFiles = mutableListOf<String>()
    var editCount = 0
    val shellCmds = mutableListOf<String>()
    var shellCount = 0
    var webCount = 0
    var searchCount = 0
    var uiCount = 0
    var otherCount = 0

    for (msg in tools) {
        if (msg.isDeclined) continue
        val name = msg.toolName?.substringAfterLast(':')?.trim().orEmpty()
        val args = msg.toolArgs
        when (name) {
            "read_file" -> {
                fileReadCount++
                shortPathName(extractJsonStringField(args, "path"))
                    .takeIf { it.isNotBlank() }
                    ?.let { readFiles += it }
            }
            "list_dir", "get_folder_summary" -> {
                dirReadCount++
                val dir = shortPathName(extractJsonStringField(args, "path"))
                if (dir.isNotBlank()) listedDirs += "$dir/"
            }
            "get_storage_summary" -> {
                storageCheckCount++
            }
            "search_files", "find_largest_files", "find_junk_files", "search_contacts" -> {
                searchCount++
            }
            "web_search", "web_fetch" -> {
                webCount++
            }
            "write_file", "delete_file", "move_file", "copy_file", "create_dir",
            "create_archive", "extract_archive", "organize_downloads" -> {
                editCount++
                val target = shortPathName(extractJsonStringField(args, "path"))
                    .ifBlank { shortPathName(extractJsonStringField(args, "source_path")) }
                    .ifBlank { shortPathName(extractJsonStringField(args, "zip_path")) }
                if (target.isNotBlank()) editedFiles += target
            }
            "run_shell_command", "termux_run", "shizuku_run" -> {
                shellCount++
                compactCommandPreview(extractJsonStringField(args, "command"), limit = 34)
                    .takeIf { it.isNotBlank() }
                    ?.let { shellCmds += it }
            }
            "ui_inspect", "ui_click", "ui_long_click", "ui_type", "ui_scroll",
            "ui_global_action", "ui_wait", "ui_wait_for", "ui_target", "ui_finish", "open_app" -> {
                uiCount++
            }
            else -> otherCount++
        }
    }

    val clauses = mutableListOf<List<HeadlinePart>>()

    if (fileReadCount > 0) {
        val uniqueFiles = readFiles.distinct()
        clauses += if (uniqueFiles.size in 1..2 && fileReadCount == readFiles.size) {
            buildList {
                add(HeadlinePart("прочитал "))
                uniqueFiles.forEachIndexed { idx, file ->
                    if (idx > 0) add(HeadlinePart(", "))
                    add(HeadlinePart(file, isCode = true))
                }
            }
        } else {
            listOf(HeadlinePart("прочитал ${PluralUtils.files(fileReadCount)}"))
        }
    }

    if (dirReadCount > 0) {
        val uniqueDirs = listedDirs.distinct()
        clauses += if (uniqueDirs.size == 1 && dirReadCount == 1) {
            listOf(HeadlinePart("просмотрел "), HeadlinePart(uniqueDirs.first(), isCode = true))
        } else {
            listOf(HeadlinePart("просмотрел ${PluralUtils.pluralize(dirReadCount, "папку", "папки", "папок")}"))
        }
    }

    if (storageCheckCount > 0) {
        clauses += listOf(HeadlinePart("проверил память"))
    }

    if (searchCount > 0) {
        clauses += listOf(
            HeadlinePart(
                if (searchCount == 1) {
                    "выполнил поиск"
                } else {
                    "выполнил ${PluralUtils.pluralize(searchCount, "поиск", "поиска", "поисков")}"
                }
            )
        )
    }

    if (webCount > 0) {
        clauses += listOf(
            HeadlinePart(if (webCount == 1) "проверил сеть" else "веб-запросов: $webCount")
        )
    }

    if (editCount > 0) {
        val uniqueEdits = editedFiles.distinct()
        clauses += if (uniqueEdits.size == 1 && editCount == 1) {
            listOf(HeadlinePart("изменил "), HeadlinePart(uniqueEdits.first(), isCode = true))
        } else {
            listOf(HeadlinePart("изменил ${PluralUtils.files(editCount)}"))
        }
    }

    if (shellCount > 0) {
        clauses += if (shellCount == 1 && shellCmds.size == 1) {
            listOf(HeadlinePart("выполнил "), HeadlinePart(shellCmds.first(), isCode = true))
        } else {
            listOf(HeadlinePart("выполнил ${PluralUtils.pluralize(shellCount, "команду", "команды", "команд")}"))
        }
    }

    if (uiCount > 0) {
        clauses += listOf(HeadlinePart("действий на экране: $uiCount"))
    }

    if (otherCount > 0 && clauses.isEmpty()) {
        clauses += listOf(HeadlinePart("выполнил ${PluralUtils.actions(tools.size)}"))
    }

    val errors = tools.count { it.isError }
    val declined = tools.count { it.isDeclined }
    val selected = clauses.take(3)
    val parts = mutableListOf<HeadlinePart>()
    val allDeclinedOnly = selected.isEmpty() && declined > 0 && errors == 0
    if (allDeclinedOnly) {
        parts += HeadlinePart("Отклонено ${PluralUtils.actions(declined)}")
    } else if (selected.isEmpty()) {
        parts += HeadlinePart("Выполнено ${PluralUtils.actions(tools.size)}")
    } else {
        selected.forEachIndexed { idx, clause ->
            if (idx > 0) parts += HeadlinePart(", ")
            parts += clause
        }
        val first = parts.first()
        if (!first.isCode && first.text.isNotEmpty()) {
            parts[0] = first.copy(text = first.text.replaceFirstChar { it.uppercaseChar() })
        }
    }

    when {
        errors > 0 -> parts += HeadlinePart(
            text = " · ${PluralUtils.pluralize(errors, "ошибка", "ошибки", "ошибок")}",
            isError = true
        )
        declined > 0 && !allDeclinedOnly -> parts += HeadlinePart(text = " · отклонено ($declined)")
    }
    return parts
}

/**
 * Современный вид хода агента (Вариант A + dev/консольный стиль):
 * - никаких «матрёшек» из коробок и заголовков «Ход задачи» / «Итог»;
 * - промежуточные реплики модели идут обычным читаемым текстом;
 * - серии вызовов инструментов схлопываются в 1 спокойную смысловую строку с таймером;
 * - изменения файлов (+N −M), ошибки, Undo, источники и превью всегда доступны в 1 тап.
 */
@Composable
internal fun AgentTurnView(
    messages: List<ChatMessage>,
    showDetails: Boolean,
    showStats: Boolean,
    planAwaitingId: String?,
    isRunning: Boolean,
    isActiveTurn: Boolean,
    isStreamingAnswer: Boolean = false,
    onUndoTrash: (String) -> Unit,
    onOpenPreview: (WebPreviewSource) -> Unit,
    onOpenSources: (List<WebSource>) -> Unit,
    onOpenChanges: (List<FileChange>) -> Unit,
    onApprovePlan: () -> Unit,
    onRejectPlan: () -> Unit
) {
    val segments = remember(messages) { buildTurnSegments(messages) }
    val toolMessages = remember(messages) { messages.filter { it.role == MessageRole.TOOL_EXECUTION } }
    val planMessage = remember(messages) { messages.firstOrNull { it.role == MessageRole.PLAN } }
    val planTools = remember(planMessage?.content) {
        planMessage?.let { parsePlanStepTools(it.content) }.orEmpty()
    }
    val stepStates = remember(planTools, toolMessages) {
        computePlanStepStates(planTools, toolMessages)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        segments.forEachIndexed { index, segment ->
            when (segment) {
                is TurnSegment.Plan -> {
                    PlanCard(
                        message = segment.message,
                        awaitingDecision = segment.message.id == planAwaitingId,
                        stepStates = stepStates,
                        onApprove = onApprovePlan,
                        onReject = onRejectPlan
                    )
                }

                is TurnSegment.Thought -> {
                    val showThought = showStats ||
                        (segment.durationMs ?: 0L) >= MIN_VISIBLE_REASONING_MS ||
                        segment.text.length > 200
                    if (showThought) {
                        InlineThoughtDisclosure(
                            reasoning = segment.text,
                            reasoningMs = segment.durationMs
                        )
                    }
                }

                is TurnSegment.Commentary -> {
                    val showThought = !segment.reasoningText.isNullOrBlank() && (
                        showStats ||
                            (segment.reasoningMs ?: 0L) >= MIN_VISIBLE_REASONING_MS ||
                            segment.reasoningText.length > 200
                        )
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (showThought) {
                            InlineThoughtDisclosure(
                                reasoning = segment.reasoningText!!,
                                reasoningMs = segment.reasoningMs
                            )
                        }
                        MarkdownView(text = segment.message.content)
                    }
                }

                is TurnSegment.ActionRun -> {
                    val isLastSegment = index == segments.lastIndex
                    val isRunActive = isActiveTurn && isRunning && (
                        segment.tools.any { it.isRunning } || (isLastSegment && !isStreamingAnswer)
                        )
                    ActionRunBlock(
                        segment = segment,
                        isRunActive = isRunActive,
                        showDetails = showDetails,
                        showStats = showStats,
                        onUndoTrash = onUndoTrash,
                        onOpenPreview = onOpenPreview,
                        onOpenSources = onOpenSources,
                        onOpenChanges = onOpenChanges
                    )
                }
            }
        }
    }
}

/** Лёгкая однострочная раскрывашка размышлений без коробки («Думал 2.4 с ▸»). */
@Composable
internal fun InlineThoughtDisclosure(
    reasoning: String,
    reasoningMs: Long?,
    modifier: Modifier = Modifier
) {
    if (reasoning.isBlank()) return
    var expanded by remember(reasoning) { mutableStateOf(false) }
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = MotionTokens.spatialFast(),
        label = "inline_thought_chevron"
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .clip(RunnerShapes.small)
                .semantics(mergeDescendants = true) {
                    role = Role.Button
                    stateDescription = if (expanded) "Развёрнуто" else "Свёрнуто"
                }
                .clickable { expanded = !expanded }
                .padding(vertical = 3.dp, horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = RunnerIcons.Psychology,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (reasoningMs != null && reasoningMs > 0L) {
                    "Думал ${formatDuration(reasoningMs)}"
                } else {
                    "Размышления"
                },
                color = MaterialTheme.colorScheme.outline,
                fontSize = TypeScale.caption,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.width(3.dp))
            Icon(
                imageVector = RunnerIcons.KeyboardArrowRight,
                contentDescription = if (expanded) "Свернуть" else "Развернуть",
                tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
                modifier = Modifier
                    .size(14.dp)
                    .graphicsLayer { rotationZ = rotation }
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = MotionTokens.expandSectionEnter(),
            exit = MotionTokens.expandSectionExit()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .padding(start = 6.dp, top = 4.dp, bottom = 2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = reasoning.trim(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                    fontSize = TypeScale.caption,
                    lineHeight = 17.sp,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * Компактная безрамная группа инструментов в стиле Antigravity + Codex + Dev.
 *
 * - Пока выполняется: показывает живой заголовок + тонкую рельсу активных шагов.
 * - После завершения: сворачивается в 1 смысловую строку (`✓ Прочитал 3 файла · 1.4 с ▸`).
 * - Ошибки, изменённые файлы (+N −M), Undo, веб-источники и HTML-превью остаются
 *   на поверхности сразу под строкой, без лишних кликов.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActionRunBlock(
    segment: TurnSegment.ActionRun,
    isRunActive: Boolean,
    showDetails: Boolean,
    showStats: Boolean,
    onUndoTrash: (String) -> Unit,
    onOpenPreview: (WebPreviewSource) -> Unit,
    onOpenSources: (List<WebSource>) -> Unit,
    onOpenChanges: (List<FileChange>) -> Unit
) {
    val tools = segment.tools
    val anyToolRunning = tools.any { it.isRunning }
    val hasError = tools.any { it.isError }
    val hasDeclined = tools.any { it.isDeclined }
    val failedTools = remember(tools) { tools.filter { it.isError } }
    val allChanges = remember(tools) { tools.flatMap { it.fileChanges } }
    val allSources = remember(tools) { tools.flatMap { it.sources }.distinctBy { it.url } }
    val undoRefs = remember(tools) {
        tools.mapNotNull { TrashStore.undoRefFromOutput(it.toolOutput) }.distinctBy { it.entryId }
    }
    val previews = remember(tools) {
        tools.mapNotNull { msg ->
            findWebPreviewInToolCall(
                toolName = msg.toolName?.substringAfterLast(':')?.trim().orEmpty(),
                args = msg.toolArgs,
                output = msg.toolOutput
            )
        }.distinctBy { it.title }
    }
    val headlineParts = remember(tools) { formatActionRunHeadlineParts(tools) }

    // Пока серия активна — раскрыто без мигания между шагами; как только
    // начинается пояснение/стриминг ответа или ход завершён — схлопывается в 1 строку,
    // если пользователь сам не раскрыл блок вручную.
    var userToggled by remember(segment.id) { mutableStateOf(false) }
    var expanded by remember(segment.id) { mutableStateOf(anyToolRunning || isRunActive) }
    LaunchedEffect(anyToolRunning, isRunActive) {
        if (!userToggled) {
            expanded = anyToolRunning || isRunActive
        }
    }

    val startedAt = remember(tools) { tools.firstOrNull()?.timestamp ?: 0L }
    var elapsedMs by remember(segment.id) {
        mutableStateOf(computeActionRunElapsedMs(tools, startedAt, anyToolRunning))
    }
    LaunchedEffect(tools, anyToolRunning) {
        if (anyToolRunning && startedAt > 0L) {
            while (true) {
                elapsedMs = (System.currentTimeMillis() - startedAt).coerceAtLeast(0L)
                kotlinx.coroutines.delay(1000L)
            }
        } else {
            elapsedMs = computeActionRunElapsedMs(tools, startedAt, false)
        }
    }

    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = MotionTokens.spatialFast(),
        label = "action_run_chevron"
    )

    val showThought = !segment.reasoningText.isNullOrBlank() && (
        showStats ||
            (segment.reasoningMs ?: 0L) >= MIN_VISIBLE_REASONING_MS ||
            segment.reasoningText.length > 200
        )

    Column(modifier = Modifier.fillMaxWidth()) {
        // 1. Главная безрамная строка сводки
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 30.dp)
                .clip(RunnerShapes.small)
                .semantics(mergeDescendants = true) {
                    role = Role.Button
                    stateDescription = if (expanded) "Развёрнуто" else "Свёрнуто"
                }
                .clickable {
                    userToggled = true
                    expanded = !expanded
                }
                .padding(horizontal = 2.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (anyToolRunning) {
                StatusPulsingDot(dotSize = 5.dp)
            } else {
                val dotColor = when {
                    hasError -> MaterialTheme.colorScheme.error
                    hasDeclined -> StatusWarning
                    else -> StatusSuccess
                }
                Box(
                    modifier = Modifier.size(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.5.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                }
            }
            Spacer(modifier = Modifier.width(6.dp))
            val baseColor = when {
                hasError -> MaterialTheme.colorScheme.error
                anyToolRunning -> MaterialTheme.colorScheme.onSurface
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            val codeColor = if (hasError) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurface
            }
            val errorColor = MaterialTheme.colorScheme.error
            Text(
                text = buildAnnotatedString {
                    headlineParts.forEach { part ->
                        when {
                            part.isError -> withStyle(
                                SpanStyle(
                                    color = errorColor,
                                    fontWeight = FontWeight.Medium
                                )
                            ) {
                                append(part.text)
                            }
                            part.isCode -> withStyle(
                                SpanStyle(
                                    color = codeColor,
                                    fontFamily = RobotoMono,
                                    fontWeight = FontWeight.Medium
                                )
                            ) {
                                append(part.text)
                            }
                            else -> withStyle(SpanStyle(color = baseColor)) {
                                append(part.text)
                            }
                        }
                    }
                },
                fontSize = TypeScale.label,
                fontWeight = if (anyToolRunning) FontWeight.Medium else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (elapsedMs >= 1_000L || anyToolRunning || (showStats && elapsedMs > 0L)) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (elapsedMs >= 60_000L) formatTimelineElapsed(elapsedMs) else formatDuration(elapsedMs),
                    color = MaterialTheme.colorScheme.outline,
                    fontSize = TypeScale.micro,
                    fontFamily = RobotoMono,
                    maxLines = 1
                )
            }
            Spacer(modifier = Modifier.width(3.dp))
            Icon(
                imageVector = RunnerIcons.KeyboardArrowRight,
                contentDescription = if (expanded) "Свернуть" else "Развернуть",
                tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
                modifier = Modifier
                    .size(14.dp)
                    .graphicsLayer { rotationZ = chevronRotation }
            )
        }

        // 2. Раскрытая вертикальная рельса шагов (без вложенных коробок)
        AnimatedVisibility(
            visible = expanded,
            enter = MotionTokens.expandSectionEnter(),
            exit = MotionTokens.expandSectionExit()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .padding(start = 7.dp, top = 2.dp, bottom = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    if (showThought) {
                        InlineThoughtDisclosure(
                            reasoning = segment.reasoningText!!,
                            reasoningMs = segment.reasoningMs
                        )
                    }
                    tools.forEach { toolMsg ->
                        DevTimelineStepRow(
                            message = toolMsg,
                            showDetails = showDetails,
                            showStats = showStats,
                            onOpenChanges = onOpenChanges
                        )
                    }
                }
            }
        }
        AnimatedVisibility(
            visible = !expanded && failedTools.isNotEmpty(),
            enter = MotionTokens.expandSectionEnter(),
            exit = MotionTokens.expandSectionExit()
        ) {
            // При свёрнутом блоке причина ошибки всё равно видна сразу (1 строка), без лишнего клика
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 22.dp, top = 1.dp, bottom = 2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                failedTools.take(2).forEach { failed ->
                    val display = formatToolStepDisplay(failed)
                    val preview = display.errorPreview
                    if (!preview.isNullOrBlank()) {
                        Text(
                            text = if (display.target != null) {
                                "${display.target}: $preview"
                            } else {
                                preview
                            },
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.9f),
                            fontSize = TypeScale.caption,
                            fontFamily = RobotoMono,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .clip(RunnerShapes.tiny)
                                .clickable {
                                    userToggled = true
                                    expanded = true
                                }
                        )
                    }
                }
            }
        }

        // 3. Поверхностные артефакты (чипы файлов с +N −M, Undo, источники, превью) — всегда в 1 тап
        if (allChanges.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 22.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                allChanges.forEach { change ->
                    DevFileChangePill(
                        change = change,
                        onClick = if (change.diff.isNullOrBlank()) {
                            null
                        } else {
                            { onOpenChanges(allChanges) }
                        }
                    )
                }
            }
        }

        if (allSources.isNotEmpty()) {
            Spacer(modifier = Modifier.height(6.dp))
            Box(modifier = Modifier.padding(start = 22.dp)) {
                WebSourcesRow(
                    sources = allSources,
                    onClick = { onOpenSources(allSources) }
                )
            }
        }

        undoRefs.forEach { ref ->
            Spacer(modifier = Modifier.height(6.dp))
            Box(modifier = Modifier.padding(start = 22.dp)) {
                UndoTrashButton(
                    label = undoLabel(ref),
                    onClick = { onUndoTrash(ref.entryId) }
                )
            }
        }

        previews.forEach { preview ->
            Spacer(modifier = Modifier.height(6.dp))
            Box(modifier = Modifier.padding(start = 22.dp)) {
                PreviewButton(
                    label = "Открыть превью ${preview.title}",
                    onClick = { onOpenPreview(preview) }
                )
            }
        }
    }
}

private fun computeActionRunElapsedMs(
    tools: List<ChatMessage>,
    startedAt: Long,
    anyRunning: Boolean
): Long {
    val summed = tools.mapNotNull { it.toolDurationMs }.sum()
    if (!anyRunning && summed > 0L) return summed
    if (startedAt <= 0L) return summed
    val end = if (anyRunning) {
        System.currentTimeMillis()
    } else {
        val last = tools.lastOrNull() ?: return summed
        last.timestamp + (last.toolDurationMs ?: 0L)
    }
    return (end - startedAt).coerceAtLeast(summed)
}

/** Компактный dev-чип изменённого файла: `[✎ SettingsScreen.kt +18 −4]`. */
@Composable
private fun DevFileChangePill(
    change: FileChange,
    onClick: (() -> Unit)?
) {
    val shape = RunnerShapes.small
    val displayName = remember(change.displayPath, change.itemCount) {
        val base = change.displayPath.trimEnd('/').substringAfterLast('/').ifBlank { change.displayPath }
        if (change.itemCount > 1) "$base (+${change.itemCount - 1})" else base
    }
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier
            .clip(shape)
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
            )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = changeIcon(change.kind),
                contentDescription = null,
                tint = changeColor(change.kind),
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = displayName,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = TypeScale.caption,
                fontFamily = RobotoMono,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (change.linesAdded > 0 || change.linesRemoved > 0) {
                Text(
                    text = buildAnnotatedString {
                        if (change.linesAdded > 0) {
                            withStyle(SpanStyle(color = StatusSuccess, fontWeight = FontWeight.Medium)) {
                                append("+${change.linesAdded}")
                            }
                        }
                        if (change.linesAdded > 0 && change.linesRemoved > 0) append(" ")
                        if (change.linesRemoved > 0) {
                            withStyle(SpanStyle(color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Medium)) {
                                append("−${change.linesRemoved}")
                            }
                        }
                    },
                    fontSize = TypeScale.micro,
                    fontFamily = RobotoMono
                )
            }
        }
    }
}

/** Один плоский шаг внутри раскрытой рельсы. */
@Composable
private fun DevTimelineStepRow(
    message: ChatMessage,
    showDetails: Boolean,
    showStats: Boolean,
    onOpenChanges: (List<FileChange>) -> Unit
) {
    val context = LocalContext.current
    val display = remember(message) { formatToolStepDisplay(message) }
    var detailsExpanded by remember(message.id) { mutableStateOf(false) }
    val hasDetails = showDetails && (
        !message.toolArgs.isNullOrBlank() || !message.toolOutput.isNullOrBlank()
        )
    val stepChevronRotation by animateFloatAsState(
        targetValue = if (detailsExpanded) 90f else 0f,
        animationSpec = MotionTokens.spatialFast(),
        label = "step_details_chevron"
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RunnerShapes.tiny)
                .clickable(enabled = hasDetails && !message.isRunning) {
                    detailsExpanded = !detailsExpanded
                }
                .padding(vertical = 3.dp, horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ToolStatusIcon(message)
            Spacer(modifier = Modifier.width(8.dp))
            val onSurface = MaterialTheme.colorScheme.onSurface
            val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
            val outlineColor = MaterialTheme.colorScheme.outline
            val errorColor = MaterialTheme.colorScheme.error

            Text(
                text = buildAnnotatedString {
                    withStyle(
                        SpanStyle(
                            color = when {
                                message.isDeclined -> outlineColor
                                message.isError -> errorColor
                                else -> onSurfaceVariant
                            }
                        )
                    ) {
                        append(display.verb)
                    }
                    if (!display.target.isNullOrBlank()) {
                        append(" ")
                        withStyle(
                            SpanStyle(
                                color = if (message.isDeclined) outlineColor else onSurface,
                                fontFamily = if (display.isCodeTarget) RobotoMono else null,
                                fontWeight = if (display.isCodeTarget) FontWeight.Medium else FontWeight.Normal
                            )
                        ) {
                            append(display.target)
                        }
                    }
                },
                fontSize = TypeScale.caption,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            val meta = buildString {
                if (!message.isRunning && !message.isError &&
                    message.fileChanges.isEmpty() && !message.toolSummary.isNullOrBlank()
                ) {
                    append(message.toolSummary)
                }
                if ((showStats || (message.toolDurationMs ?: 0L) >= 1_000L) &&
                    !message.isRunning && message.toolDurationMs != null
                ) {
                    if (isNotEmpty()) append(" · ")
                    append(formatDuration(message.toolDurationMs))
                }
            }
            if (meta.isNotBlank()) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = meta,
                    color = MaterialTheme.colorScheme.outline,
                    fontSize = TypeScale.micro,
                    fontFamily = RobotoMono,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (hasDetails && !message.isRunning) {
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = RunnerIcons.KeyboardArrowRight,
                    contentDescription = if (detailsExpanded) "Свернуть" else "Детали",
                    tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.65f),
                    modifier = Modifier
                        .size(13.dp)
                        .graphicsLayer { rotationZ = stepChevronRotation }
                )
            }
        }

        // Причина ошибки видна прямо под упавшим шагом без раскрытия
        AnimatedVisibility(
            visible = !display.errorPreview.isNullOrBlank() && !detailsExpanded,
            enter = MotionTokens.expandSectionEnter(),
            exit = MotionTokens.expandSectionExit()
        ) {
            Text(
                text = display.errorPreview.orEmpty(),
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.9f),
                fontSize = TypeScale.micro,
                fontFamily = RobotoMono,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 16.dp, bottom = 2.dp)
            )
        }

        AnimatedVisibility(
            visible = detailsExpanded && hasDetails,
            enter = MotionTokens.expandSectionEnter(),
            exit = MotionTokens.expandSectionExit()
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                shape = RunnerShapes.small,
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 15.dp, top = 4.dp, bottom = 4.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                    if (!message.toolArgs.isNullOrBlank() && message.toolArgs != "{}") {
                        Text(
                            text = "Аргументы",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = TypeScale.micro,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = message.toolArgs,
                            fontFamily = RobotoMono,
                            fontSize = TypeScale.micro,
                            lineHeight = 15.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                        if (!message.toolOutput.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                thickness = 0.5.dp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                    }
                    if (!message.toolOutput.isNullOrBlank()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Вывод",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = TypeScale.micro,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = {
                                    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    manager?.setPrimaryClip(
                                        ClipData.newPlainText("Результат инструмента", message.toolOutput)
                                    )
                                    Toast.makeText(context, "Скопировано", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(22.dp)
                            ) {
                                Icon(
                                    imageVector = RunnerIcons.ContentCopy,
                                    contentDescription = "Копировать",
                                    tint = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        ToolOutputView(output = message.toolOutput)
                    }
                    if (message.fileChanges.any { !it.diff.isNullOrBlank() }) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Открыть diff →",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = TypeScale.micro,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .clip(RunnerShapes.tiny)
                                .clickable { onOpenChanges(message.fileChanges) }
                                .padding(vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}
