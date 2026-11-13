package com.runner.app.ui.commands

import com.runner.app.tools.ToolDispatcher
import java.util.Locale

/** Снимок состояния для /status. */
data class CommandStatusInfo(
    val sessionTitle: String,
    val providerName: String,
    val modelName: String,
    val modes: String,
    val workingDir: String,
    val contextChars: Int,
    val contextBudgetChars: Int,
    val activeTools: Int,
    val totalTools: Int,
    val promptTokens: Int?,
    val completionTokens: Int?
)

/** Содержимое /tools: что реально доступно агенту сейчас. */
data class CommandToolsInfo(
    val tools: List<ToolDispatcher.ToolSummary>,
    val total: Int
) {
    val hiddenCount: Int get() = (total - tools.size).coerceAtLeast(0)
}

/** Числа с пробелами между разрядами: 12480 -> 12 480. */
fun formatCommandCount(value: Int): String =
    String.format(Locale.US, "%,d", value).replace(',', ' ')
