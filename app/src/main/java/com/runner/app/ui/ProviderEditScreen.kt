package com.runner.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.runner.app.ui.components.ProviderLogos
import com.runner.app.ui.theme.StatusSuccess
import kotlinx.coroutines.delay

/**
 * Редактирование одного провайдера: имя, Base URL, ключ и список моделей,
 * полученный из GET /models.
 */
@Composable
fun ProviderEditScreen(
    viewModel: MainViewModel,
    providerId: String,
    onDeleted: () -> Unit
) {
    val providers by viewModel.providers.collectAsState()
    val loadingFor by viewModel.modelsLoadingFor.collectAsState()
    val activeProviderId by viewModel.activeProviderId.collectAsState()

    val provider = providers.firstOrNull { it.id == providerId } ?: return
    val context = LocalContext.current

    var name by remember(providerId) { mutableStateOf(provider.name) }
    var baseUrl by remember(providerId) { mutableStateOf(provider.baseUrl) }
    var apiKey by remember(providerId) { mutableStateOf(provider.apiKey) }
    var keyVisible by remember { mutableStateOf(false) }
    val status = remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    // Авто-подгрузка моделей: как только пользователь закончил вводить ключ
    // или Base URL, через 600 мс сами идём в GET /models. Ключ при этом
    // не сохраняем — только список моделей, ключ уйдёт по кнопке «Сохранить».
    var autoFetchEnabled by remember(providerId) { mutableStateOf(false) }

    LaunchedEffect(apiKey, baseUrl, autoFetchEnabled) {
        if (!autoFetchEnabled) return@LaunchedEffect
        if (apiKey.isBlank() || baseUrl.isBlank()) return@LaunchedEffect
        delay(600)
        viewModel.fetchModels(provider.id, baseUrl, apiKey) { status.value = it }
    }

    val isActive = provider.id == activeProviderId
    val isDirty = name != provider.name || baseUrl != provider.baseUrl || apiKey != provider.apiKey
    val isLoadingModels = loadingFor == provider.id

    val providerLogo = remember(provider.id, name) {
        ProviderLogos.forProvider(provider.id, name)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SettingsCard {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(
                                if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                else MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                            .border(
                                BorderStroke(
                                    1.dp,
                                    if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                                ),
                                RoundedCornerShape(9.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = providerLogo ?: Icons.Outlined.AutoAwesome,
                            contentDescription = null,
                            tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = name.ifBlank { "Провайдер" },
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (isActive) "Активный провайдер" else "Настройка провайдера",
                            color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            fontSize = 11.5.sp
                        )
                    }

                    if (isActive) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(StatusSuccess.copy(alpha = 0.12f))
                                .border(BorderStroke(1.dp, StatusSuccess.copy(alpha = 0.25f)), RoundedCornerShape(6.dp))
                                .padding(horizontal = 7.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "Активен",
                                color = StatusSuccess,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(14.dp))

                FieldLabel("Название")
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    colors = providerFieldColors()
                )

                Spacer(modifier = Modifier.height(14.dp))
                FieldLabel("Base URL")
                TextField(
                    value = baseUrl,
                    onValueChange = {
                        baseUrl = it
                        autoFetchEnabled = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text("https://api.groq.com/openai/v1", color = MaterialTheme.colorScheme.outline, fontSize = 13.sp)
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    colors = providerFieldColors()
                )

                Spacer(modifier = Modifier.height(14.dp))
                FieldLabel("API Key")
                TextField(
                    value = apiKey,
                    onValueChange = {
                        apiKey = it
                        autoFetchEnabled = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("sk-...", color = MaterialTheme.colorScheme.outline, fontSize = 13.sp) },
                    singleLine = true,
                    visualTransformation = if (keyVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        IconButton(onClick = { keyVisible = !keyVisible }) {
                            Icon(
                                imageVector = if (keyVisible) {
                                    Icons.Outlined.VisibilityOff
                                } else {
                                    Icons.Outlined.Visibility
                                },
                                contentDescription = "Показать ключ",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = providerFieldColors()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Ключ шифруется ключом Android Keystore и наружу не уходит.",
                    color = MaterialTheme.colorScheme.outline,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )

                Spacer(modifier = Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            viewModel.saveProvider(
                                provider.copy(
                                    name = name.trim().ifBlank { "Без названия" },
                                    baseUrl = baseUrl.trim(),
                                    apiKey = apiKey.trim()
                                )
                            )
                            status.value = "Сохранено"
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        enabled = isDirty,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            disabledContentColor = MaterialTheme.colorScheme.outline
                        )
                    ) {
                        Text("Сохранить", fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
                    }

                    if (!isActive) {
                        OutlinedButton(
                            onClick = { viewModel.selectProvider(provider.id) },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            )
                        ) {
                            Text("Сделать активным", fontSize = 13.sp)
                        }
                    }
                }

                status.value?.let { message ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = message, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
            }
        }

        SettingsCard {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Модели",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "${provider.models.size} в списке",
                            color = MaterialTheme.colorScheme.outline,
                            fontSize = 11.5.sp
                        )
                    }
                    IconButton(
                        onClick = {
                            autoFetchEnabled = false
                            viewModel.fetchModels(provider.id, baseUrl, apiKey) { status.value = it }
                        },
                        enabled = !isLoadingModels
                    ) {
                        if (isLoadingModels) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(17.dp),
                                strokeWidth = 1.8.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.Refresh,
                                contentDescription = "Загрузить список",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                if (provider.models.isEmpty()) {
                    Text(
                        text = if (isLoadingModels) {
                            "Загружаю список моделей…"
                        } else {
                            "Список пуст. Модели подтянутся сами после ввода ключа, " +
                                    "либо нажми обновление."
                        },
                        color = MaterialTheme.colorScheme.outline,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp)
                            .background(MaterialTheme.colorScheme.surfaceContainerLowest, RoundedCornerShape(10.dp))
                    ) {
                        LazyColumn {
                            items(provider.models, key = { it.id }) { model ->
                                val isSelected = model.id == provider.selectedModel
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.selectModel(provider.id, model.id)
                                            status.value = "Модель: ${model.id}"
                                        }
                                        .padding(start = 12.dp, end = 4.dp, top = 9.dp, bottom = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = model.label,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                            fontSize = 12.sp,
                                            fontFamily = FontFamily.Monospace,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (model.name.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(1.dp))
                                            Text(
                                                text = model.id,
                                                color = MaterialTheme.colorScheme.outline,
                                                fontSize = 10.5.sp,
                                                fontFamily = FontFamily.Monospace,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    model.contextBadge?.let { badge ->
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(5.dp))
                                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                                .padding(horizontal = 5.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = badge,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 10.sp,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                    }

                                    IconButton(
                                        onClick = {
                                            copyModelId(context, model.id)
                                            Toast.makeText(
                                                context,
                                                "ID скопирован в буфер",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.ContentCopy,
                                            contentDescription = "Скопировать ID",
                                            tint = MaterialTheme.colorScheme.outline,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }

                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Outlined.Check,
                                            contentDescription = "Выбрана",
                                            tint = StatusSuccess,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                    thickness = 0.5.dp
                                )
                            }
                        }
                    }
                }
            }
        }

        if (providers.size > 1) {
            TextButton(
                onClick = { confirmDelete = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Удалить провайдера", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text("Удалить провайдера?", color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp) },
            text = {
                Text(
                    text = "«${provider.name}» и его ключ будут удалены с устройства.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.deleteProvider(provider.id)
                    onDeleted()
                }) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text("Отмена", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                }
            }
        )
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(bottom = 6.dp)
    )
}

private fun copyModelId(context: Context, modelId: String) {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    manager.setPrimaryClip(ClipData.newPlainText("Model ID", modelId))
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        content()
    }
}

@Composable
private fun providerFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
    focusedIndicatorColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
    unfocusedIndicatorColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
    cursorColor = MaterialTheme.colorScheme.primary,
    focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface
)
