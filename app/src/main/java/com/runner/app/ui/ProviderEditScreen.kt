package com.runner.app.ui
import com.runner.app.ui.theme.RunnerShapes
import com.runner.app.ui.theme.TypeScale

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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.runner.app.data.ProviderStore
import com.runner.app.ui.components.BrandLogoOrMonogram
import com.runner.app.ui.components.ProviderLogos
import com.runner.app.ui.theme.StatusSuccess
import com.runner.app.util.PluralUtils
import com.runner.app.util.UrlSanitizer
import com.runner.app.ui.components.RunnerIcons

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

    val isActive = provider.id == activeProviderId
    val isDirty = name != provider.name || baseUrl != provider.baseUrl || apiKey != provider.apiKey
    val isLoadingModels = loadingFor == provider.id

    fun persistDraft(): Pair<String, String> {
        val cleanUrl = UrlSanitizer.sanitizeBaseUrl(baseUrl)
        val cleanKey = apiKey.trim()
        val cleanName = name.trim().ifBlank { "Без названия" }
        viewModel.saveProvider(
            provider.copy(
                name = cleanName,
                baseUrl = cleanUrl,
                apiKey = cleanKey
            )
        )
        name = cleanName
        baseUrl = cleanUrl
        apiKey = cleanKey
        return cleanUrl to cleanKey
    }

    val providerLogo = remember(provider.id, name, baseUrl) {
        ProviderLogos.forProvider(provider.id, name, baseUrl)
    }
    val providerMonogram = remember(name, baseUrl) {
        ProviderLogos.monogramFor(providerName = name, baseUrl = baseUrl)
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
                            .clip(RunnerShapes.small)
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
                                RunnerShapes.small
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        BrandLogoOrMonogram(
                            logo = providerLogo,
                            monogram = providerMonogram,
                            tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            iconSize = 19.dp,
                            fontSize = 11.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = name.ifBlank { "Провайдер" },
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = TypeScale.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (isActive) "Активный провайдер" else "Настройка провайдера",
                            color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            fontSize = TypeScale.caption
                        )
                    }

                    if (isActive) {
                        Box(
                            modifier = Modifier
                                .clip(RunnerShapes.tiny)
                                .background(StatusSuccess.copy(alpha = 0.12f))
                                .border(BorderStroke(1.dp, StatusSuccess.copy(alpha = 0.25f)), RunnerShapes.tiny)
                                .padding(horizontal = 7.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "Активен",
                                color = StatusSuccess,
                                fontSize = TypeScale.caption,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Готовые шаблоны",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = TypeScale.caption,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ProviderStore.PRESETS.forEach { preset ->
                        Box(
                            modifier = Modifier
                                .clip(RunnerShapes.small)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .border(
                                    BorderStroke(
                                        1.dp,
                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                                    ),
                                    RunnerShapes.small
                                )
                                .clickable {
                                    name = preset.name
                                    baseUrl = preset.baseUrl
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = preset.name,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = TypeScale.caption,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                FieldLabel("Название")
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RunnerShapes.small,
                    colors = providerFieldColors()
                )

                Spacer(modifier = Modifier.height(14.dp))
                FieldLabel("Base URL (адрес API)")
                TextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text("https://api.groq.com/openai/v1", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = TypeScale.label)
                    },
                    singleLine = true,
                    shape = RunnerShapes.small,
                    colors = providerFieldColors()
                )

                Spacer(modifier = Modifier.height(14.dp))
                FieldLabel("API-ключ")
                TextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        val isGemini = provider.id.contains("gemini", ignoreCase = true) || name.contains("gemini", ignoreCase = true)
                        val keyPlaceholder = when {
                            isGemini -> "AIzaSy…"
                            provider.id.contains("anthropic", ignoreCase = true) -> "sk-ant-…"
                            provider.id.contains("openrouter", ignoreCase = true) -> "sk-or-…"
                            provider.id.contains("groq", ignoreCase = true) -> "gsk_…"
                            provider.id.contains("openai", ignoreCase = true) -> "sk-…"
                            else -> "Введите API-ключ…"
                        }
                        Text(keyPlaceholder, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = TypeScale.label)
                    },
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
                                    RunnerIcons.VisibilityOff
                                } else {
                                    RunnerIcons.Visibility
                                },
                                contentDescription = if (keyVisible) "Скрыть ключ" else "Показать ключ",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    },
                    shape = RunnerShapes.small,
                    colors = providerFieldColors()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Ключ хранится в зашифрованном виде через Android Keystore и не покидает устройство.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = TypeScale.caption,
                    lineHeight = 15.sp
                )

                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            val (cleanUrl, cleanKey) = persistDraft()
                            status.value = "Сохранено"
                            // Подгружаем список моделей только после явного сохранения пары URL+ключ,
                            // чтобы при вводе нового ключа он не уходил на старый Base URL.
                            if (cleanUrl.isNotBlank() && cleanKey.isNotBlank()) {
                                viewModel.fetchModels(provider.id, cleanUrl, cleanKey) { status.value = it }
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 44.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                        enabled = isDirty,
                        shape = RunnerShapes.small,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            disabledContentColor = MaterialTheme.colorScheme.outline
                        )
                    ) {
                        Text("Сохранить", fontSize = TypeScale.label, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
                    }

                    if (!isActive) {
                        OutlinedButton(
                            onClick = {
                                val wasDirty = isDirty
                                val savedCreds = if (wasDirty) persistDraft() else null
                                viewModel.selectProvider(provider.id)
                                status.value = if (wasDirty) {
                                    "Сохранено и выбрано активным"
                                } else {
                                    "Провайдер стал активным"
                                }
                                if (savedCreds != null) {
                                    val (cleanUrl, cleanKey) = savedCreds
                                    if (cleanUrl.isNotBlank() && cleanKey.isNotBlank() && provider.models.isEmpty()) {
                                        viewModel.fetchModels(provider.id, cleanUrl, cleanKey) { status.value = it }
                                    }
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 44.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                            shape = RunnerShapes.small,
                            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            )
                        ) {
                            Text(
                                text = "Сделать активным",
                                fontSize = TypeScale.label,
                                lineHeight = 15.sp,
                                maxLines = 2,
                                softWrap = true,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                status.value?.let { message ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = message, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = TypeScale.caption)
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
                            fontSize = TypeScale.body,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "${PluralUtils.models(provider.models.size)} в списке",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = TypeScale.caption
                        )
                    }
                    IconButton(
                        onClick = {
                            val cleanUrl = UrlSanitizer.sanitizeBaseUrl(baseUrl)
                            val cleanKey = apiKey.trim()
                            viewModel.fetchModels(provider.id, cleanUrl, cleanKey) { status.value = it }
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
                                imageVector = RunnerIcons.Refresh,
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
                            "Список пуст. Нажмите «Сохранить» или кнопку обновления, " +
                                    "чтобы загрузить список моделей."
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = TypeScale.caption,
                        lineHeight = 17.sp
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp)
                            .background(MaterialTheme.colorScheme.surfaceContainerLowest, RunnerShapes.small)
                    ) {
                        LazyColumn {
                            items(provider.models, key = { it.id }) { model ->
                                val isSelected = model.id == provider.selectedModel
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (isDirty) persistDraft()
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
                                            fontSize = TypeScale.caption,
                                            fontFamily = FontFamily.Monospace,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (model.name.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(1.dp))
                                            Text(
                                                text = model.id,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = TypeScale.micro,
                                                fontFamily = FontFamily.Monospace,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    model.contextBadge?.let { badge ->
                                        Box(
                                            modifier = Modifier
                                                .clip(RunnerShapes.tiny)
                                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                                .padding(horizontal = 5.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = badge,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = TypeScale.micro,
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
                                            imageVector = RunnerIcons.ContentCopy,
                                            contentDescription = "Скопировать ID",
                                            tint = MaterialTheme.colorScheme.outline,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }

                                    if (isSelected) {
                                        Icon(
                                            imageVector = RunnerIcons.Check,
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
                Text("Удалить провайдера", color = MaterialTheme.colorScheme.error, fontSize = TypeScale.label)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text("Удалить провайдера?", color = MaterialTheme.colorScheme.onSurface, fontSize = TypeScale.title) },
            text = {
                Text(
                    text = "«${provider.name}» и его ключ будут удалены с устройства.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = TypeScale.label
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.deleteProvider(provider.id)
                    onDeleted()
                }) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error, fontSize = TypeScale.body)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text("Отмена", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = TypeScale.body)
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
        fontSize = TypeScale.caption,
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
        shape = RunnerShapes.medium,
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
