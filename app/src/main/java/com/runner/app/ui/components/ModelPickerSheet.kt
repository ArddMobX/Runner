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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.runner.app.data.ModelCatalog
import com.runner.app.data.ModelInfo
import com.runner.app.data.Provider
import com.runner.app.ui.theme.StatusSuccess

const val ALL_PROVIDERS_ID = "__ALL__"
const val FAVORITES_PROVIDER_ID = "__FAVORITES__"

private data class DisplayModel(
    val model: ModelInfo,
    val providerId: String,
    val providerName: String
)

/**
 * Выбор модели: вкладка "Все" и отдельные провайдеры сверху, поиск, список
 * моделей с бейджем контекста, провайдера и копированием ID. Плюс режим
 * ручного ввода для кастомных моделей.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelPickerSheet(
    providers: List<Provider>,
    activeProviderId: String,
    loadingProviderId: String?,
    favoriteModels: Set<String> = emptySet(),
    onToggleFavorite: (providerId: String, model: String) -> Unit = { _, _ -> },
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

    val isFavoritesSelected = selectedProviderId == FAVORITES_PROVIDER_ID
    val isAllSelected = selectedProviderId == ALL_PROVIDERS_ID
    val activeProvider = providers.firstOrNull { it.id == activeProviderId } ?: providers.firstOrNull()
    val currentProvider = if (isAllSelected || isFavoritesSelected) null else (providers.firstOrNull { it.id == selectedProviderId } ?: providers.firstOrNull())
    val isLoading = if (isAllSelected || isFavoritesSelected) {
        loadingProviderId != null
    } else {
        currentProvider != null && loadingProviderId == currentProvider.id
    }

    val displayModels = remember(providers, selectedProviderId, query, favoriteModels) {
        if (isFavoritesSelected) {
            providers.flatMap { prov ->
                prov.models
                    .filter { model ->
                        favoriteModels.contains("${prov.id}:${model.id}") || favoriteModels.contains(model.id)
                    }
                    .map { DisplayModel(model = it, providerId = prov.id, providerName = prov.name) }
            }.filter { item ->
                if (query.isBlank()) true else {
                    val q = query.trim()
                    item.model.id.contains(q, ignoreCase = true) ||
                            item.model.label.contains(q, ignoreCase = true) ||
                            item.model.name.contains(q, ignoreCase = true) ||
                            item.providerName.contains(q, ignoreCase = true)
                }
            }
        } else if (isAllSelected) {
            val sourceProviders = providers.filter { it.apiKey.isNotBlank() }.ifEmpty { providers }
            sourceProviders.flatMap { prov ->
                prov.models
                    .filter { ModelCatalog.isChatAndToolModel(it.id, it.name) }
                    .map { DisplayModel(model = it, providerId = prov.id, providerName = prov.name) }
            }.filter { item ->
                if (query.isBlank()) true else {
                    val q = query.trim()
                    item.model.id.contains(q, ignoreCase = true) ||
                            item.model.label.contains(q, ignoreCase = true) ||
                            item.model.name.contains(q, ignoreCase = true) ||
                            item.providerName.contains(q, ignoreCase = true)
                }
            }
        } else if (currentProvider != null) {
            val provider = currentProvider
            provider.models
                .filter { ModelCatalog.isChatAndToolModel(it.id, it.name) }
                .filter { model ->
                    if (query.isBlank()) true else {
                        val q = query.trim()
                        model.id.contains(q, ignoreCase = true) ||
                                model.label.contains(q, ignoreCase = true) ||
                                model.name.contains(q, ignoreCase = true)
                    }
                }
                .map { DisplayModel(model = it, providerId = provider.id, providerName = provider.name) }
        } else {
            emptyList()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        scrimColor = Color.Black.copy(alpha = 0.65f),
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
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                if (manualMode) {
                    TextButton(onClick = { manualMode = false }) {
                        Text("К списку", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
                    }
                } else if (isAllSelected || currentProvider != null) {
                    IconButton(
                        onClick = {
                            if (isAllSelected) {
                                val refreshTargets = providers.filter { it.apiKey.isNotBlank() }.ifEmpty { providers }
                                refreshTargets.forEach { onRefresh(it.id) }
                            } else if (currentProvider != null) {
                                onRefresh(currentProvider.id)
                            }
                        },
                        enabled = !isLoading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 1.8.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.Refresh,
                                contentDescription = "Обновить список",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            if (manualMode) {
                val targetProvider = currentProvider ?: activeProvider
                ManualIdEntry(
                    value = manualId,
                    onValueChange = { manualId = it },
                    onApply = {
                        val id = manualId.trim()
                        if (id.isNotBlank() && targetProvider != null) {
                            onSelectModel(targetProvider.id, id)
                        }
                    },
                    enabled = targetProvider != null
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
                // Вкладка "Избранное"
                val favoritesSelected = isFavoritesSelected
                val favoriteCount = remember(favoriteModels, providers) {
                    providers.sumOf { prov ->
                        prov.models.count { favoriteModels.contains("${prov.id}:${it.id}") || favoriteModels.contains(it.id) }
                    }
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (favoritesSelected) MaterialTheme.colorScheme.surfaceContainerHigh
                            else MaterialTheme.colorScheme.surfaceContainer
                        )
                        .border(
                            BorderStroke(
                                1.dp,
                                if (favoritesSelected) Color(0xFFFFB800).copy(alpha = 0.65f)
                                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                            ),
                            RoundedCornerShape(8.dp)
                        )
                        .clickable {
                            selectedProviderId = FAVORITES_PROVIDER_ID
                            query = ""
                        }
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = null,
                            tint = if (favoritesSelected) Color(0xFFFFB800) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (favoriteCount > 0) "Избранное ($favoriteCount)" else "Избранное",
                            color = if (favoritesSelected) Color(0xFFFFB800) else MaterialTheme.colorScheme.onSurface,
                            fontSize = 12.sp,
                            fontWeight = if (favoritesSelected) FontWeight.SemiBold else FontWeight.Medium
                        )
                    }
                }

                // Вкладка "Все"
                val allSelected = isAllSelected
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (allSelected) MaterialTheme.colorScheme.surfaceContainerHigh
                            else MaterialTheme.colorScheme.surfaceContainer
                        )
                        .border(
                            BorderStroke(
                                1.dp,
                                if (allSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                            ),
                            RoundedCornerShape(8.dp)
                        )
                        .clickable {
                            selectedProviderId = ALL_PROVIDERS_ID
                            query = ""
                        }
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Layers,
                            contentDescription = null,
                            tint = if (allSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Все",
                            color = if (allSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            fontSize = 12.sp,
                            fontWeight = if (allSelected) FontWeight.SemiBold else FontWeight.Medium
                        )
                    }
                }

                providers.forEach { item ->
                    val isSelected = !isAllSelected && item.id == currentProvider?.id
                    val providerLogo = remember(item.id, item.name) {
                        ProviderLogos.forProvider(item.id, item.name)
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.surfaceContainerHigh
                                else MaterialTheme.colorScheme.surfaceContainer
                            )
                            .border(
                                BorderStroke(
                                    1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
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
                            if (providerLogo != null) {
                                Icon(
                                    imageVector = providerLogo,
                                    contentDescription = null,
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                            } else if (item.apiKey.isBlank()) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(MaterialTheme.colorScheme.outline)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                            } else if (isSelected) {
                                Icon(
                                    imageVector = Icons.Outlined.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                            }
                            Text(
                                text = item.name,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
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
                    .padding(horizontal = 16.dp)
                    .border(
                        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                        RoundedCornerShape(10.dp)
                    ),
                placeholder = {
                    Text(
                        text = if (isAllSelected) "Поиск по всем моделям и провайдерам" else "Поиск модели",
                        color = MaterialTheme.colorScheme.outline,
                        fontSize = 13.sp
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
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
                        manualId = (currentProvider ?: activeProvider)?.selectedModel.orEmpty()
                        manualMode = true
                    }
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(text = "Ввести ID вручную", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), thickness = 0.5.dp)

            when {
                providers.isEmpty() -> SheetMessage("Нет ни одного провайдера")

                isFavoritesSelected && displayModels.isEmpty() -> SheetMessage(
                    if (query.isBlank()) "В избранном пока нет моделей.\nНажмите на звёздочку ★ рядом с любой моделью, чтобы добавить её сюда."
                    else "Ничего не найдено в избранном"
                )

                !isAllSelected && !isFavoritesSelected && currentProvider == null -> SheetMessage("Провайдер не найден")

                !isAllSelected && !isFavoritesSelected && currentProvider?.apiKey?.isBlank() == true -> Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Text(
                        text = "У провайдера ${currentProvider.name} не задан ключ, список моделей недоступен.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    TextButton(onClick = onOpenProviderSettings) {
                        Text("Открыть провайдеры", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
                    }
                }

                displayModels.isEmpty() -> SheetMessage(
                    if (query.isBlank()) "Список пуст. Нажмите обновление сверху." else "Ничего не найдено"
                )

                else -> LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(
                        items = displayModels,
                        key = { "${it.providerId}:${it.model.id}" }
                    ) { item ->
                        val isActive = item.providerId == activeProviderId && item.model.id == activeProvider?.selectedModel
                        val isFavorite = favoriteModels.contains("${item.providerId}:${item.model.id}") ||
                                favoriteModels.contains(item.model.id)
                        ModelRow(
                            model = item.model,
                            providerId = item.providerId,
                            providerName = item.providerName,
                            isActive = isActive,
                            isFavorite = isFavorite,
                            showProviderBadge = isAllSelected || isFavoritesSelected,
                            onSelect = { onSelectModel(item.providerId, item.model.id) },
                            onToggleFavorite = { onToggleFavorite(item.providerId, item.model.id) },
                            onCopy = {
                                copyToClipboard(context, item.model.id)
                                Toast.makeText(context, "ID скопирован в буфер", Toast.LENGTH_SHORT).show()
                            }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), thickness = 0.5.dp)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ModelRow(
    model: ModelInfo,
    providerId: String,
    providerName: String,
    isActive: Boolean,
    isFavorite: Boolean = false,
    showProviderBadge: Boolean = false,
    onSelect: () -> Unit,
    onToggleFavorite: () -> Unit,
    onCopy: () -> Unit
) {
    val hasReadableName = model.name.isNotBlank() || model.label != model.id
    val modelLogo = remember(model.id, providerId, providerName) {
        ProviderLogos.forModelOrProvider(
            modelId = model.id,
            providerId = providerId,
            providerName = providerName
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onSelect,
                onLongClick = onToggleFavorite
            )
            .padding(start = 16.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
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
                    RoundedCornerShape(8.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = modelLogo ?: Icons.Outlined.AutoAwesome,
                contentDescription = null,
                tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = model.label,
                color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                fontSize = 13.5.sp,
                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (showProviderBadge || hasReadableName) {
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (showProviderBadge) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = providerName,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    if (showProviderBadge && hasReadableName) {
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    if (hasReadableName) {
                        Text(
                            text = model.id,
                            color = MaterialTheme.colorScheme.outline,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                }
            }
        }

        model.contextBadge?.let { badge ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(5.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
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
            onClick = onToggleFavorite,
            modifier = Modifier.size(34.dp)
        ) {
            Icon(
                imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                contentDescription = if (isFavorite) "Удалить из избранного" else "В избранное",
                tint = if (isFavorite) Color(0xFFFFB800) else MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(17.dp)
            )
        }

        IconButton(
            onClick = onCopy,
            modifier = Modifier.size(34.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.ContentCopy,
                contentDescription = "Скопировать ID",
                tint = MaterialTheme.colorScheme.outline,
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
            color = MaterialTheme.colorScheme.outline,
            fontSize = 11.5.sp,
            lineHeight = 16.sp
        )
        Spacer(modifier = Modifier.height(12.dp))
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                    RoundedCornerShape(10.dp)
                ),
            placeholder = {
                Text("meta-llama/llama-3.3-70b-instruct", color = MaterialTheme.colorScheme.outline, fontSize = 13.sp)
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
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                disabledContentColor = MaterialTheme.colorScheme.outline
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
        Text(text = text, color = MaterialTheme.colorScheme.outline, fontSize = 13.sp)
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    manager.setPrimaryClip(ClipData.newPlainText("Model ID", text))
}

@Composable
private fun sheetFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    cursorColor = MaterialTheme.colorScheme.primary,
    focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface
)
