package com.runner.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/**
 * Кнопка переключения Live-режима (автопилот по экрану) в верхнем правом углу.
 * Активное состояние показывается цветом и границей, без бесконечной
 * пульсации: движение в интерфейсе должно быть осмысленным.
 */
@Composable
fun LiveModeTopButton(
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor by animateColorAsState(
        targetValue = if (isActive) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        },
        label = "live_border"
    )

    val backgroundColor by animateColorAsState(
        targetValue = if (isActive) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.35f)
        },
        label = "live_bg"
    )

    val iconColor by animateColorAsState(
        targetValue = if (isActive) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        },
        label = "live_icon"
    )

    Box(
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(backgroundColor)
            .border(if (isActive) 1.2.dp else 0.8.dp, borderColor, CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = RunnerIcons.TouchApp,
            contentDescription = if (isActive) "Live-режим активен (автопилот)" else "Включить Live-режим",
            tint = iconColor,
            modifier = Modifier.size(19.dp)
        )
    }
}
