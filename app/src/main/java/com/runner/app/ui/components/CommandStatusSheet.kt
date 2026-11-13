package com.runner.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.runner.app.ui.commands.CommandStatusInfo
import com.runner.app.ui.commands.formatCommandCount

/**
 * Снимок /status: чат, провайдер, модель, режимы, контекст и инструменты.
 * Это только чтение, в контекст модели снимок не попадает.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommandStatusSheet(
    info: CommandStatusInfo,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.65f),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(32.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = "Статус",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Снимок текущего чата. В контекст модели не уходит.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp),
                contentPadding = PaddingValues(bottom = 20.dp)
            ) {
                item { StatusSection("Сессия") }
                item { StatusRow("Чат", info.sessionTitle) }
                item { StatusRow("Провайдер", info.providerName) }
                item { StatusRow("Модель", info.modelName, mono = true) }

                item { StatusSection("Режим") }
                item { StatusRow("Сейчас", info.modes) }
                item { StatusRow("Папка", info.workingDir, mono = true) }

                item { StatusSection("Контекст") }
                item {
                    StatusRow(
                        "Занято",
                        "${formatCommandCount(info.contextChars)} / " +
                                "${formatCommandCount(info.contextBudgetChars)} символов"
                    )
                }
                item {
                    StatusRow(
                        "Инструменты",
                        if (info.activeTools == info.totalTools) {
                            "${info.totalTools} доступны"
                        } else {
                            "${info.activeTools} активны из ${info.totalTools}"
                        }
                    )
                }
                val tokens = buildTokenLine(info.promptTokens, info.completionTokens)
                if (tokens != null) {
                    item { StatusSection("Последний ход") }
                    item { StatusRow("Токены", tokens) }
                }
            }
        }
    }
}

private fun buildTokenLine(prompt: Int?, completion: Int?): String? {
    val parts = buildList {
        prompt?.takeIf { it > 0 }?.let { add("${formatCommandCount(it)} вход") }
        completion?.takeIf { it > 0 }?.let { add("${formatCommandCount(it)} выход") }
    }
    return if (parts.isEmpty()) null else parts.joinToString(" / ")
}

@Composable
private fun StatusSection(title: String) {
    Text(
        text = title.uppercase(),
        color = MaterialTheme.colorScheme.outline,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.9.sp,
        modifier = Modifier.padding(top = 12.dp, bottom = 3.dp)
    )
}

@Composable
private fun StatusRow(
    label: String,
    value: String,
    mono: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.outline,
            fontSize = 11.5.sp,
            modifier = Modifier.width(108.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = value,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 12.5.sp,
            lineHeight = 17.sp,
            fontFamily = if (mono) FontFamily.Monospace else null,
            modifier = Modifier.weight(1f)
        )
    }
}
