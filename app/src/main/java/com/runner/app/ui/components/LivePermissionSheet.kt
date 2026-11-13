package com.runner.app.ui.components

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.runner.app.service.RunnerAccessibilityService
import com.runner.app.ui.theme.StatusSuccess
import com.runner.app.ui.theme.StatusWarning

/**
 * Шторка настройки разрешений для Live-режима (автопилота):
 * специальные возможности для действий на экране и наложение поверх других окон.
 *
 * Все цвета берутся из темы: шторка должна выглядеть частью Material You,
 * а не отдельным тёмным островком со своей палитрой.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LivePermissionSheet(
    onDismiss: () -> Unit,
    onAllGranted: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasAccessibility by remember {
        mutableStateOf(RunnerAccessibilityService.isEnabled(context))
    }
    var hasOverlay by remember {
        mutableStateOf(Settings.canDrawOverlays(context))
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasAccessibility = RunnerAccessibilityService.isEnabled(context)
                hasOverlay = Settings.canDrawOverlays(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // Заголовок
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.32f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = RunnerIcons.TouchApp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Live-режим (Автопилот)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Выполнение действий на экране в реальном времени",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "В этом режиме Runner сворачивается и по вашей команде взаимодействует с экраном: нажимает кнопки, ищет элементы и вводит текст прямо перед вашими глазами. Для работы требуются два разрешения:",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Разрешение 1: Специальные возможности
            PermissionCard(
                title = "1. Специальные возможности",
                description = "Позволяет анализировать иерархию элементов на экране и отправлять касания.",
                isGranted = hasAccessibility,
                onGrantClick = {
                    try {
                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(intent)
                    } catch (_: Exception) {}
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Разрешение 2: Отображение поверх других приложений
            PermissionCard(
                title = "2. Отображение поверх других приложений",
                description = "Требуется для плавающего кружка управления со статусом и визуализации касаний.",
                isGranted = hasOverlay,
                onGrantClick = {
                    try {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        ).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(intent)
                    } catch (_: Exception) {}
                }
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Итоговая кнопка
            Button(
                onClick = {
                    if (hasAccessibility && hasOverlay) {
                        onAllGranted()
                    }
                    onDismiss()
                },
                enabled = hasAccessibility && hasOverlay,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
            ) {
                Text(
                    text = if (hasAccessibility && hasOverlay) "Включить Live-режим" else "Предоставьте оба разрешения",
                    style = MaterialTheme.typography.labelLarge
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PermissionCard(
    title: String,
    description: String,
    isGranted: Boolean,
    onGrantClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(
            0.8.dp,
            if (isGranted) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            }
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (isGranted) RunnerIcons.CheckCircle else RunnerIcons.Error,
                        contentDescription = null,
                        tint = if (isGranted) StatusSuccess else StatusWarning,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (isGranted) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Включено",
                        style = MaterialTheme.typography.labelMedium,
                        color = StatusSuccess,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (!isGranted) {
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onGrantClick,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Настроить в системе",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}
