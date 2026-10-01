package com.runner.app.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.runner.app.data.ModelInfo
import com.runner.app.data.Provider
import com.runner.app.ui.theme.AccentPrimary
import com.runner.app.ui.theme.OutlineSubtle
import com.runner.app.ui.theme.StatusSuccess
import com.runner.app.ui.theme.SurfaceContainer
import com.runner.app.ui.theme.SurfaceContainerHigh
import com.runner.app.ui.theme.SurfaceContainerLow
import com.runner.app.ui.theme.SurfaceDark
import com.runner.app.ui.theme.TextPrimary
import com.runner.app.ui.theme.TextSecondary
import com.runner.app.ui.theme.TextTertiary

/**
 * Выбор модели: провайдер сверху, поиск, список моделей с бейджем контекста
 * и копированием ID. Плюс режим ручного ввода для кастомных моделей.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelPickerSheet(
    providers: List<Provider>,
    activeProviderId: String,
    loadingProviderId: String?,
    onSelectModel: (providerId: String, model: String) -> Unit,
    onRefresh: (providerId: String) -> Unit,
    onOpenProviderSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var selectedProviderId by remember(activeProviderId) { mutableStateOf(activeProviderId) }
    var query by remember { mutableStateOf("") }
    var manualMode by remember { mutableStateOf(false) }
    var manualId by remember { mutableStateOf("") }

    val provider = providers.firstOrNull { it.id == selectedProviderId } ?: providers.firstOrNull()
    val isLoading = provider != null && loadingProviderId == provider.id

    val models = provider?.models.orEmpty().filter { model ->
        query.isBlank() ||
                model.id.contains(query.trim(), ignoreCase = true) ||
                model.name.contains(query.trim(), ignoreCase = true)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SurfaceContainerLow,
        scrimColor = Color.Black.copy(alpha = 0.65f),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(32.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(TextTertiary.copy(alpha = 0.4f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (manualMode) "Свой ID модели" else "Модель",
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                if (manualMode) {
                    TextButton(onClick = { manualMode = false }) {
                        Text("К списку", color = AccentPrimary, fontSize = 13.sp)
                    }
                } else if (provider != null) {
                    IconButton(
                        onClick = { onRefresh(provider.id) },
                        enabled = !isLoading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 1.8.dp,
                                color = AccentPrimary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.Refresh,
                                contentDescription = "Обновить список",
                                tint = TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            if (manualMode) {
                ManualIdEntry(
                    value = manualId,
                    onValueChange = { manualId = it },
                    onApply = {
                        val id = manualId.trim()
                        if (id.isNotBlank() && provider != null) {
                            onSelectModel(provider.id, id)
                        }
                    },
                    enabled = provider != null
                )
                return@Column
            }

            // Провайдеры
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                providers.forEach { item ->
                    val isSelected = item.id == provider?.id
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) SurfaceContainerHigh else SurfaceContainer)
                            .border(
                                BorderStroke(
                                    1.dp,
                                    if (isSelected) AccentPrimary.copy(alpha = 0.5f) else OutlineSubtle
                                ),
                                RoundedCornerShape(8.dp)
                            )
                            .clickable {
                                selectedProviderId = item.id
                                query = ""
                            }
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (item.apiKey.isBlank()) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(TextTertiary)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                            } else if (isSelected) {
                                Icon(
                                    imageVector = Icons.Outlined.Check,
                                    contentDescription = null,
                                    tint = AccentPrimary,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                            }
                            Text(
                                text = item.name,
                                color = if (isSelected) AccentPrimary else TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            TextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                placeholder = { Text("Поиск модели", color = TextTertiary, fontSize = 13.sp) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = null,
                        tint = TextTertiary,
                        modifier = Modifier.size(17.dp)
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                shape = RoundedCornerShape(10.dp),
                colors = sheetFieldColors()
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        manualId = provider?.selectedModel.orEmpty()
                        manualMode = true
                    }
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = null,
                    tint = AccentPrimary,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(text = "Ввести ID вручную", color = AccentPrimary, fontSize = 13.sp)
            }

            HorizontalDivider(color = OutlineSubtle, thickness = 0.5.dp)

            when {
                provider == null -> SheetMessage("Нет ни одного провайдера")

                provider.apiKey.isBlank() -> Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Text(
                        text = "У провайдера ${provider.name} не задан ключ — список моделей не запросить.",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    TextButton(onClick = onOpenProviderSettings) {
                        Text("Открыть провайдеры", color = AccentPrimary, fontSize = 13.sp)
                    }
                }

                models.isEmpty() -> SheetMessage(
                    if (query.isBlank()) "Список пуст. Нажми обновление сверху." else "Ничего не найдено"
                )

                else -> LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(models, key = { it.id }) { model ->
                        ModelRow(
                            model = model,
                            isActive = provider.id == activeProviderId && model.id == provider.selectedModel,
                            onSelect = { onSelectModel(provider.id, model.id) },
                            onCopy = {
                                copyToClipboard(context, model.id)
                                Toast.makeText(context, "ID скопирован в буфер", Toast.LENGTH_SHORT).show()
                            }
                        )
                        HorizontalDivider(color = OutlineSubtle, thickness = 0.5.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelRow(
    model: ModelInfo,
    isActive: Boolean,
    onSelect: () -> Unit,
    onCopy: () -> Unit
) {
    val hasReadableName = model.name.isNotBlank()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() }
            .padding(start = 20.dp, end = 6.dp, top = 11.dp, bottom = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = model.label,
                color = if (isActive) AccentPrimary else TextPrimary,
                fontSize = 13.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (hasReadableName) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = model.id,
                    color = TextTertiary,
                    fontSize = 11.sp,
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
                    .background(SurfaceContainerHigh)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = badge,
                    color = TextSecondary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
        }

        if (isActive) {
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = "Выбрана",
                tint = StatusSuccess,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
        }

        IconButton(
            onClick = onCopy,
            modifier = Modifier.size(34.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.ContentCopy,
                contentDescription = "Скопировать ID",
                tint = TextTertiary,
                modifier = Modifier.size(15.dp)
            )
        }
    }
}

@Composable
private fun ManualIdEntry(
    value: String,
    onValueChange: (String) -> Unit,
    onApply: () -> Unit,
    enabled: Boolean
) {
    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        Text(
            text = "Провайдер должен поддерживать tool calling, иначе агент не сможет вызывать инструменты.",
            color = TextTertiary,
            fontSize = 11.5.sp,
            lineHeight = 16.sp
        )
        Spacer(modifier = Modifier.height(12.dp))
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text("meta-llama/llama-3.3-70b-instruct", color = TextTertiary, fontSize = 13.sp)
            },
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
            colors = sheetFieldColors()
        )
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = onApply,
            enabled = enabled && value.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            shape = RoundedCornerShape(11.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = AccentPrimary,
                contentColor = SurfaceDark,
                disabledContainerColor = SurfaceContainerHigh,
                disabledContentColor = TextTertiary
            )
        ) {
            Text("Использовать этот ID", fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
        }
        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
private fun SheetMessage(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = TextTertiary, fontSize = 13.sp)
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    manager.setPrimaryClip(ClipData.newPlainText("Model ID", text))
}

@Composable
private fun sheetFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = SurfaceContainer,
    unfocusedContainerColor = SurfaceContainer,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    cursorColor = AccentPrimary,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary
)
