package com.runner.app.ui.components
import com.runner.app.ui.theme.RunnerShapes
import com.runner.app.ui.theme.TypeScale

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.runner.app.ui.commands.SlashCommand

/**
 * Палитра команд над полем ввода.
 *
 * Показывается, пока пользователь набирает первое слово после слэша.
 * Во время работы агента недоступные команды остаются в списке, но гаснут:
 * так видно, что команда есть, и понятно, почему она не сработает.
 */
@Composable
fun SlashCommandPalette(
    commands: List<SlashCommand>,
    isRunning: Boolean,
    onPick: (SlashCommand) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RunnerShapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(
            0.7.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
        ),
        tonalElevation = 2.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, top = 9.dp, bottom = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "КОМАНДЫ",
                    color = MaterialTheme.colorScheme.outline,
                    fontSize = TypeScale.micro,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.9.sp,
                    modifier = Modifier.weight(1f)
                )
                if (isRunning) {
                    Text(
                        text = "сейчас только /stop и /help",
                        color = MaterialTheme.colorScheme.outline,
                        fontSize = TypeScale.micro
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 280.dp),
                contentPadding = PaddingValues(bottom = 6.dp)
            ) {
                items(commands, key = { it.name }) { command ->
                    val enabled = !isRunning || command.availableWhileRunning
                    CommandPaletteRow(
                        command = command,
                        enabled = enabled,
                        onClick = { onPick(command) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CommandPaletteRow(
    command: SlashCommand,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.42f)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(RunnerShapes.small)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.13f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "/",
                color = MaterialTheme.colorScheme.primary,
                fontSize = TypeScale.label,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = command.invocation,
                    color = if (enabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontSize = TypeScale.label,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace
                )
                if (!command.argsHint.isNullOrBlank()) {
                    Text(
                        text = " ${command.argsHint}",
                        color = MaterialTheme.colorScheme.outline,
                        fontSize = TypeScale.caption,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
            Text(
                text = command.description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = TypeScale.caption,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
