package com.runner.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.outlined.Check
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.runner.app.ui.theme.AccentPrimary
import com.runner.app.ui.theme.OutlineSubtle
import com.runner.app.ui.theme.StatusError
import com.runner.app.ui.theme.StatusSuccess
import com.runner.app.ui.theme.SurfaceContainer
import com.runner.app.ui.theme.SurfaceContainerHigh
import com.runner.app.ui.theme.SurfaceContainerLow
import com.runner.app.ui.theme.SurfaceContainerLowest
import com.runner.app.ui.theme.SurfaceDark
import com.runner.app.ui.theme.TextPrimary
import com.runner.app.ui.theme.TextSecondary
import com.runner.app.ui.theme.TextTertiary

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

    var name by remember(providerId) { mutableStateOf(provider.name) }
    var baseUrl by remember(providerId) { mutableStateOf(provider.baseUrl) }
    var apiKey by remember(providerId) { mutableStateOf(provider.apiKey) }
    var keyVisible by remember { mutableStateOf(false) }
    val status = remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    val isActive = provider.id == activeProviderId
    val isDirty = name != provider.name || baseUrl != provider.baseUrl || apiKey != provider.apiKey

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SettingsCard {
            Column(modifier = Modifier.padding(14.dp)) {
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
                    onValueChange = { baseUrl = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text("https://api.groq.com/openai/v1", color = TextTertiary, fontSize = 13.sp)
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    colors = providerFieldColors()
                )

                Spacer(modifier = Modifier.height(14.dp))
                FieldLabel("API Key")
                TextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("sk-...", color = TextTertiary, fontSize = 13.sp) },
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
                                tint = TextSecondary,
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
                    color = TextTertiary,
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
                            containerColor = AccentPrimary,
                            contentColor = SurfaceDark,
                            disabledContainerColor = SurfaceContainerHigh,
                            disabledContentColor = TextTertiary
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
                            border = BorderStroke(0.5.dp, OutlineSubtle),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = SurfaceContainer,
                                contentColor = TextPrimary
                            )
                        ) {
                            Text("Сделать активным", fontSize = 13.sp)
                        }
                    }
                }

                status.value?.let { message ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = message, color = TextSecondary, fontSize = 12.sp)
                }
            }
        }

        SettingsCard {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Модели",
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "${provider.models.size} в списке",
                            color = TextTertiary,
                            fontSize = 11.5.sp
                        )
                    }
                    IconButton(
                        onClick = { viewModel.refreshModels(provider.id) { status.value = it } },
                        enabled = loadingFor != provider.id
                    ) {
                        if (loadingFor == provider.id) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(17.dp),
                                strokeWidth = 1.8.dp,
                                color = AccentPrimary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.Refresh,
                                contentDescription = "Загрузить список",
                                tint = AccentPrimary,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                if (provider.models.isEmpty()) {
                    Text(
                        text = "Список пуст. Нажми обновление — модели придут из GET /models.",
                        color = TextTertiary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp)
                            .background(SurfaceContainerLowest, RoundedCornerShape(10.dp))
                    ) {
                        LazyColumn {
                            items(provider.models, key = { it }) { model ->
                                val isSelected = model == provider.selectedModel
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.selectModel(provider.id, model)
                                            status.value = "Модель: $model"
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = model,
                                        color = if (isSelected) AccentPrimary else TextPrimary,
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Outlined.Check,
                                            contentDescription = "Выбрана",
                                            tint = StatusSuccess,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                HorizontalDivider(color = OutlineSubtle, thickness = 0.5.dp)
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
                Text("Удалить провайдера", color = StatusError, fontSize = 13.sp)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = SurfaceContainerHigh,
            title = { Text("Удалить провайдера?", color = TextPrimary, fontSize = 16.sp) },
            text = {
                Text(
                    text = "«${provider.name}» и его ключ будут удалены с устройства.",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.deleteProvider(provider.id)
                    onDeleted()
                }) {
                    Text("Удалить", color = StatusError, fontSize = 14.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text("Отмена", color = TextSecondary, fontSize = 14.sp)
                }
            }
        )
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        color = TextSecondary,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(bottom = 6.dp)
    )
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow),
        border = BorderStroke(0.5.dp, OutlineSubtle),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        content()
    }
}

@Composable
private fun providerFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = SurfaceContainer,
    unfocusedContainerColor = SurfaceContainer,
    focusedIndicatorColor = OutlineSubtle,
    unfocusedIndicatorColor = OutlineSubtle,
    cursorColor = AccentPrimary,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary
)
