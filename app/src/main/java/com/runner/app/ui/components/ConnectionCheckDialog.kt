package com.runner.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.net.ConnectivityManager
import android.net.Network
import com.runner.app.data.ConnectionDiagnosis
import com.runner.app.ui.ConnectionTestState
import com.runner.app.ui.theme.RunnerShapes
import com.runner.app.ui.theme.StatusSuccess
import com.runner.app.ui.theme.TypeScale

/**
 * Результат проверки соединения в чате. Показывает не сырой HTTP-код,
 * а короткий вывод и следующее действие.
 */
@Composable
fun ConnectionCheckDialog(
    providerName: String,
    state: ConnectionTestState,
    onCheck: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    val result = state.result
    val diagnosis = ConnectionDiagnosis.of(result)
    val context = LocalContext.current
    val currentState by rememberUpdatedState(state)
    val currentOnCheck by rememberUpdatedState(onCheck)

    // Если пользователь включает VPN, пока диалог открыт, перепроверяем сами.
    DisposableEffect(context) {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        if (manager == null) {
            onDispose { }
        } else {
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    if (currentState.result?.isSuccess != true) {
                        currentOnCheck()
                    }
                }
            }
            try {
                manager.registerDefaultNetworkCallback(callback)
            } catch (_: Exception) {
            }
            onDispose {
                try {
                    manager.unregisterNetworkCallback(callback)
                } catch (_: Exception) {
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RunnerShapes.large,
        title = {
            Text(
                text = "Проверка соединения",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = TypeScale.title,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
            )
        },
        text = {
            Column {
                Text(
                    text = providerName,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = TypeScale.label
                )
                Spacer(modifier = Modifier.height(10.dp))

                when {
                    state.isTesting -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Проверяю…",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = TypeScale.label
                        )
                    }

                    result == null -> Text(
                        text = "Проверим, отвечает ли провайдер и принимает ли ключ.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = TypeScale.label,
                        lineHeight = 18.sp
                    )

                    result.isSuccess -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = RunnerIcons.CheckCircle,
                            contentDescription = null,
                            tint = StatusSuccess,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = result.message,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = TypeScale.label,
                            lineHeight = 18.sp
                        )
                    }

                    else -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = RunnerIcons.Error,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = diagnosis?.title ?: "Ошибка соединения",
                                color = MaterialTheme.colorScheme.error,
                                fontSize = TypeScale.label,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = diagnosis?.detail ?: result.message,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = TypeScale.label,
                            lineHeight = 18.sp
                        )
                        if (result.targetEndpoint.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = result.targetEndpoint,
                                color = MaterialTheme.colorScheme.outline,
                                fontSize = TypeScale.micro,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onCheck,
                enabled = !state.isTesting
            ) {
                Text(if (result == null) "Проверить" else "Проверить снова")
            }
        },
        dismissButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onOpenSettings) {
                    Text("Настройки", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = onDismiss) {
                    Text("Закрыть", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    )
}
