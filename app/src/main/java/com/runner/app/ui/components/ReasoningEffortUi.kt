package com.runner.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.runner.app.data.ReasoningEffort
import com.runner.app.data.ResolvedReasoningEffort
import com.runner.app.ui.theme.RunnerShapes
import com.runner.app.ui.theme.TypeScale

/**
 * Сдержанный цвет индикатора Effort в стиле Codex:
 * - Minimal / Low / Medium — спокойный вторичный тон;
 * - High — основной тон текста;
 * - Extra High / Max — деликатный акцент темы.
 */
@Composable
fun effortTintColor(effort: ReasoningEffort): Color = when {
    effort.isAccent -> MaterialTheme.colorScheme.primary
    effort == ReasoningEffort.HIGH -> MaterialTheme.colorScheme.onSurface
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

/**
 * Компактное выпадающее меню выбора уровня размышлений в стиле Codex.
 * Показывает только реально поддерживаемые текущей моделью уровни.
 */
@Composable
fun ReasoningEffortMenu(
    expanded: Boolean,
    supportedEfforts: List<ReasoningEffort>,
    activeEffort: ReasoningEffort?,
    isMandatory: Boolean,
    onSelect: (ReasoningEffort) -> Unit,
    onDismiss: () -> Unit
) {
    if (supportedEfforts.isEmpty()) return
    val haptic = LocalHapticFeedback.current

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier
            .widthIn(min = 216.dp)
            .clip(RunnerShapes.large)
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
            Text(
                text = "Уровень размышлений",
                color = MaterialTheme.colorScheme.outline,
                fontSize = TypeScale.micro,
                fontWeight = FontWeight.Medium
            )
            if (isMandatory) {
                Text(
                    text = "Модель всегда использует размышления",
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.78f),
                    fontSize = TypeScale.micro
                )
            }
        }
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
            thickness = 0.5.dp
        )
        supportedEfforts.forEach { effort ->
            val isSelected = effort == activeEffort
            val itemTint = if (isSelected) MaterialTheme.colorScheme.primary else effortTintColor(effort)
            DropdownMenuItem(
                leadingIcon = {
                    Box(
                        modifier = Modifier.size(18.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (effort == ReasoningEffort.NONE) {
                            Box(
                                modifier = Modifier
                                    .width(10.dp)
                                    .height(1.8.dp)
                                    .clip(RoundedCornerShape(1.dp))
                                    .background(
                                        if (isSelected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
                                        }
                                    )
                            )
                        } else {
                            EffortLevelBars(
                                activeBars = effort.bars,
                                activeColor = itemTint,
                                inactiveColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                            )
                        }
                    }
                },
                text = {
                    Column {
                        Text(
                            text = effort.title,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            fontSize = TypeScale.label,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                        )
                        Text(
                            text = effort.subtitle,
                            color = MaterialTheme.colorScheme.outline,
                            fontSize = TypeScale.micro,
                            maxLines = 1
                        )
                    }
                },
                trailingIcon = if (isSelected) {
                    {
                        Icon(
                            imageVector = RunnerIcons.Check,
                            contentDescription = "Выбран",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                } else {
                    null
                },
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSelect(effort)
                }
            )
        }
    }
}

/**
 * Компактная строка выбора Effort внутри шторки моделей (появляется только для
 * моделей с поддержкой Effort — так уровень можно включить обратно даже из NONE).
 */
@Composable
fun ReasoningEffortStrip(
    resolved: ResolvedReasoningEffort,
    onSelect: (ReasoningEffort) -> Unit,
    modifier: Modifier = Modifier
) {
    if (!resolved.isSupported) return
    val haptic = LocalHapticFeedback.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Уровень размышлений",
                color = MaterialTheme.colorScheme.outline,
                fontSize = TypeScale.micro,
                fontWeight = FontWeight.Medium
            )
            if (resolved.isMandatory) {
                Text(
                    text = "всегда вкл.",
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.72f),
                    fontSize = TypeScale.micro
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            resolved.supportedEfforts.forEach { effort ->
                val selected = effort == resolved.activeEffort
                val tint = when {
                    selected -> MaterialTheme.colorScheme.primary
                    effort == ReasoningEffort.NONE -> MaterialTheme.colorScheme.onSurfaceVariant
                    else -> effortTintColor(effort)
                }
                Row(
                    modifier = Modifier
                        .clip(RunnerShapes.small)
                        .background(
                            if (selected) {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            } else {
                                MaterialTheme.colorScheme.surfaceContainer
                            }
                        )
                        .border(
                            BorderStroke(
                                1.dp,
                                if (selected) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                                }
                            ),
                            RunnerShapes.small
                        )
                        .semantics(mergeDescendants = true) { role = Role.Button }
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelect(effort)
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (effort != ReasoningEffort.NONE) {
                        EffortLevelBars(
                            activeBars = effort.bars,
                            activeColor = tint,
                            inactiveColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                    }
                    Text(
                        text = if (effort == ReasoningEffort.NONE) "Выкл" else effort.shortLabel,
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        fontSize = TypeScale.caption,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}
