package com.runner.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.runner.app.tools.ToolDispatcher
import com.runner.app.ui.components.AudioPreviewPlayer
import com.runner.app.ui.components.ChatInputBar
import com.runner.app.ui.components.MarkdownView
import com.runner.app.ui.components.ModelPickerSheet
import com.runner.app.ui.components.PreviewButton
import com.runner.app.ui.components.ProviderLogos
import com.runner.app.ui.components.RunnerIcons
import com.runner.app.ui.components.ToolOutputView
import com.runner.app.ui.components.WebPreviewDialog
import com.runner.app.ui.components.WebPreviewSource
import com.runner.app.ui.components.findWebPreviewInMarkdown
import com.runner.app.ui.components.findWebPreviewInToolCall
import com.runner.app.ui.components.rememberChatInputState
import com.runner.app.ui.theme.MotionTokens
import com.runner.app.ui.theme.StatusSuccess
import com.runner.app.ui.theme.StatusWarning
import com.runner.app.ui.theme.bounceClick
import com.runner.app.util.PluralUtils

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(
    viewModel: MainViewModel,
    onOpenDrawer: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenStorageSettings: () -> Unit
) {
    val messages by viewModel.messages.collectAsState()
    val streamingText by viewModel.streamingText.collectAsState()
    val isRunning by viewModel.isRunning.collectAsState()
    val currentStatus by viewModel.currentStatus.collectAsState()
    val provider by viewModel.activeProvider.collectAsState()
    val providers by viewModel.providers.collectAsState()
    val activeProviderId by viewModel.activeProviderId.collectAsState()
    val modelsLoadingFor by viewModel.modelsLoadingFor.collectAsState()
    val pendingConfirmation by viewModel.pendingConfirmation.collectAsState()
    val pendingPlan by viewModel.pendingPlan.collectAsState()
    val hasStoragePermission by viewModel.hasStoragePermission.collectAsState()
    val appSettings by viewModel.settings.collectAsState()

    val inputState = rememberChatInputState()
    var showModelPicker by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // Живое превью HTML/SVG: источник появляется из ответа модели или из write_file
    var previewSource by remember { mutableStateOf<WebPreviewSource?>(null) }

    val isEmptyChat = messages.none { it.role != MessageRole.SYSTEM_INFO }
    val storageStats by viewModel.storageStats.collectAsState()

    LaunchedEffect(isEmptyChat) {
        if (isEmptyChat) viewModel.refreshStorageStats()
    }

    // Плеер живёт в синглтоне, поэтому его надо глушить при уходе с экрана:
    // иначе трек продолжит играть поверх настроек и в фоне
    DisposableEffect(Unit) {
        onDispose { AudioPreviewPlayer.stop() }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    var lastStreamingScrollPos by remember { mutableStateOf(0) }
    LaunchedEffect(streamingText) {
        if (streamingText.isNotEmpty()) {
            val delta = streamingText.length - lastStreamingScrollPos
            if (delta >= 40 || lastStreamingScrollPos == 0) {
                lastStreamingScrollPos = streamingText.length
                val target = messages.size
                if (target >= 0) listState.scrollToItem(target)
            }
        } else {
            lastStreamingScrollPos = 0
        }
    }

    if (showModelPicker && pendingConfirmation == null) {
        ModelPickerSheet(
            providers = providers,
            activeProviderId = activeProviderId,
            loadingProviderId = modelsLoadingFor,
            onSelectModel = { providerId, model ->
                viewModel.selectModel(providerId, model)
                showModelPicker = false
            },
            onRefresh = { providerId -> viewModel.refreshModels(providerId) },
            onOpenProviderSettings = {
                showModelPicker = false
                onOpenSettings()
            },
            onDismiss = { showModelPicker = false }
        )
    }

    pendingConfirmation?.let { request ->
        ConfirmationBottomSheet(
            request = request,
            onConfirm = { selected, remember ->
                viewModel.resolveConfirmation(true, selected, remember)
            },
            onReject = { viewModel.resolveConfirmation(false) }
        )
    }

    val showStoragePrompt by viewModel.showStoragePrompt.collectAsState()
    if (showStoragePrompt) {
        StoragePromptSheet(
            onGrant = {
                onOpenStorageSettings()
                viewModel.dismissStoragePrompt()
            },
            onLater = { viewModel.dismissStoragePrompt() }
        )
    }

    previewSource?.let { source ->
        WebPreviewDialog(
            source = source,
            onDismiss = { previewSource = null }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer, modifier = Modifier.bounceClick { onOpenDrawer() }) {
                        Icon(
                            imageVector = Icons.Outlined.Menu,
                            contentDescription = "Чаты",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                },
                title = {
                    // Название — как в списке провайдера: сначала ищем label
                    // выбранной модели, иначе форматируем raw ID. Логотип
                    // по-прежнему строится по raw ID внутри ModelChip.
                    val activeModelId = provider?.activeModel.orEmpty()
                    val activeModelLabel = provider?.models
                        ?.firstOrNull { it.id == activeModelId }
                        ?.label?.takeIf { it.isNotBlank() }
                    ModelChip(
                        providerId = provider?.id.orEmpty(),
                        providerName = provider?.name ?: "Провайдер",
                        modelName = activeModelId.ifBlank { "модель не выбрана" },
                        displayName = activeModelLabel.orEmpty(),
                        hasKey = provider?.apiKey?.isNotBlank() == true,
                        isLoading = modelsLoadingFor != null,
                        onClick = { showModelPicker = true }
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .navigationBarsPadding()
                .imePadding()
        ) {
            if (isEmptyChat) {
                EmptyChatState(
                    modifier = Modifier.weight(1f),
                    storageStats = storageStats,
                    onSuggestion = { inputState.setText(it) }
                )
            } else {
                Column(modifier = Modifier.weight(1f)) {
                    CompositionLocalProvider(
                        LocalDensity provides Density(
                            LocalDensity.current.density,
                            LocalDensity.current.fontScale * appSettings.textScale
                        )
                    ) {
                        // Серии одинаковых вызовов схлопываем в один аккордеон,
                        // чтобы «Выполняю команду...» не захламляли экран.
                        val chatItems = remember(messages) { groupChatItems(messages) }
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(
                                chatItems,
                                key = {
                                    when (it) {
                                        is ChatListItem.Single -> it.message.id
                                        is ChatListItem.ToolGroup -> "group_${it.messages.first().id}"
                                    }
                                }
                            ) { item ->
                                // animateItemPlacement вместо мгновенного скачка:
                                // когда выше появляется новая плашка, остальные
                                // разъезжаются, а не прыгают на новое место
                                Box(
                                    modifier = Modifier.animateItemPlacement(
                                        animationSpec = MotionTokens.fluidSpring()
                                    )
                                ) {
                                    when (item) {
                                        is ChatListItem.Single -> MessageItem(
                                            message = item.message,
                                            showToolDetails = appSettings.showToolDetails,
                                            showStats = appSettings.showStats,
                                            planAwaitingId = pendingPlan?.id,
                                            onApprovePlan = { viewModel.resolvePlan(true) },
                                            onRejectPlan = { viewModel.resolvePlan(false) },
                                            onEditMessage = { inputState.setText(it) },
                                            onRetryMessage = { viewModel.retryFromUserMessage(it) },
                                            onOpenPreview = { previewSource = it },
                                            onAction = { action ->
                                                when (action) {
                                                    MessageAction.OPEN_SETTINGS -> onOpenSettings()
                                                    MessageAction.OPEN_MODELS -> showModelPicker = true
                                                    MessageAction.GRANT_STORAGE -> onOpenStorageSettings()
                                                }
                                            }
                                        )

                                        is ChatListItem.ToolGroup -> ToolGroupCard(
                                            messages = item.messages,
                                            onOpenPreview = { previewSource = it }
                                        )
                                    }
                                }
                            }

                            if (streamingText.isNotBlank()) {
                                item(key = "streaming") {
                                    StreamingBubble(text = streamingText)
                                }
                            }
                        }
                    }
                }
            }

            if (!hasStoragePermission) {
                PermissionBanner(onOpenSettings = onOpenStorageSettings)
            }

            AnimatedVisibility(
                visible = isRunning,
                enter = expandVertically(animationSpec = MotionTokens.fluidSpring()) + fadeIn(),
                exit = shrinkVertically(animationSpec = MotionTokens.fluidSpring()) + fadeOut()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(11.dp),
                        strokeWidth = 1.6.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(9.dp))
                    Text(
                        text = currentStatus ?: "Работаю",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            ChatInputBar(
                inputState = inputState,
                isRunning = isRunning,
                onSend = { text -> viewModel.sendMessage(text) },
                onStop = { viewModel.stopGeneration() }
            )
        }
    }
}

/** Человекочитаемое имя модели: отсекает префиксы models/, вендоров и технические суффиксы. */
fun formatModelName(raw: String): String {
    if (raw.isBlank() || raw == "модель не выбрана") return "Выбрать модель"
    var name = raw.trim()
    if (name.startsWith("models/")) {
        name = name.removePrefix("models/")
    }
    if (name.contains('/')) {
        name = name.substringAfter('/')
    }
    name = name.removeSuffix(":free")
    name = name.removeSuffix("-instruct")
    name = name.removeSuffix("-versatile")
    name = name.removeSuffix("-preview")
    name = name.removeSuffix("-latest")

    val parts = name.split('-', '_', ' ').filter { it.isNotBlank() }
    val formatted = parts.map { part ->
        when (part.lowercase()) {
            "gpt" -> "GPT"
            "llama" -> "Llama"
            "gemini" -> "Gemini"
            "claude" -> "Claude"
            "deepseek" -> "DeepSeek"
            "qwen" -> "Qwen"
            "mistral" -> "Mistral"
            "flash" -> "Flash"
            "lite" -> "Lite"
            "pro" -> "Pro"
            "mini" -> "Mini"
            "oss" -> "OSS"
            else -> {
                if (part.matches(Regex("^[0-9]+[bB]$"))) {
                    part.uppercase()
                } else if (part.matches(Regex("^[0-9]+o$"))) {
                    part
                } else {
                    part.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                }
            }
        }
    }.joinToString(" ")

    return formatted.ifBlank { raw }
}

/** Компактный чип-селектор модели в шапке: фирменный логотип + форматированное имя + шеврон. */
@Composable
private fun ModelChip(
    providerId: String,
    providerName: String,
    modelName: String,
    hasKey: Boolean,
    isLoading: Boolean,
    onClick: () -> Unit,
    /** Готовое название из списка провайдера; пусто — форматируем modelName. */
    displayName: String = ""
) {
    val cleanModel = remember(displayName, modelName) {
        val raw = displayName.ifBlank { modelName }
        formatModelName(raw)
    }
    val brandLogo = remember(modelName, providerId, providerName) {
        ProviderLogos.forModelOrProvider(
            modelId = modelName,
            providerId = providerId,
            providerName = providerName
        )
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)), RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = brandLogo ?: Icons.Outlined.AutoAwesome,
            contentDescription = null,
            tint = when {
                !hasKey -> StatusWarning
                brandLogo != null -> MaterialTheme.colorScheme.onSurface
                else -> MaterialTheme.colorScheme.primary
            },
            modifier = Modifier.size(15.dp)
        )
        Spacer(modifier = Modifier.width(7.dp))
        Text(
            text = cleanModel,
            color = if (hasKey) MaterialTheme.colorScheme.onSurface else StatusWarning,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.width(4.dp))
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(12.dp),
                strokeWidth = 1.6.dp,
                color = MaterialTheme.colorScheme.primary
            )
        } else {
            Icon(
                imageVector = Icons.Outlined.KeyboardArrowDown,
                contentDescription = "Выбрать модель",
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}


/**
 * Экран пустого чата: блок прижат к низу (ближе к пальцу и полю ввода),
 * сверху — свободное пространство. Живой виджет хранилища задаёт контекст
 * системной утилиты, сетка 2×2 — короткие сценарии с описаниями.
 */
@Composable
private fun EmptyChatState(
    modifier: Modifier = Modifier,
    storageStats: ToolDispatcher.StorageStats?,
    onSuggestion: (String) -> Unit
) {
    val suggestions = listOf(
        Suggestion(
            Icons.Outlined.FolderOpen, "Разобрать Download",
            "Разбери папку Download: архивы и свежие файлы"
        ),
        Suggestion(
            Icons.Outlined.DeleteOutline, "Очистить мусор",
            "Найди мусор: кэш, пустые папки и тяжелые логи"
        ),
        Suggestion(
            Icons.Outlined.VideoLibrary, "Тяжёлые файлы",
            "Найди файлы тяжелее 50 МБ: видео и музыку"
        ),
        Suggestion(
            Icons.Outlined.ContentPaste, "Буфер обмена",
            "Прочитай буфер обмена"
        )
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Spacer(modifier = Modifier.weight(0.4f))

        Text(
            text = "Чем помочь?",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.5).sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Агент для работы с файлами, памятью и терминалом.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.5.sp,
            lineHeight = 18.sp
        )
        Spacer(modifier = Modifier.height(16.dp))

        StorageWidget(
            stats = storageStats,
            onClick = { onSuggestion("Сделай сводку по памяти устройства") }
        )
        if (storageStats?.totalBytes ?: 0L > 0L) {
            Spacer(modifier = Modifier.height(12.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SuggestionCard(
                modifier = Modifier.weight(1f),
                suggestion = suggestions[0],
                onClick = { onSuggestion(suggestions[0].prompt) }
            )
            SuggestionCard(
                modifier = Modifier.weight(1f),
                suggestion = suggestions[1],
                onClick = { onSuggestion(suggestions[1].prompt) }
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SuggestionCard(
                modifier = Modifier.weight(1f),
                suggestion = suggestions[2],
                onClick = { onSuggestion(suggestions[2].prompt) }
            )
            SuggestionCard(
                modifier = Modifier.weight(1f),
                suggestion = suggestions[3],
                onClick = { onSuggestion(suggestions[3].prompt) }
            )
        }

        Spacer(modifier = Modifier.weight(0.6f))
    }
}

private data class Suggestion(
    val icon: ImageVector,
    val title: String,
    val prompt: String
)

/**
 * Живой виджет хранилища: тонкий прогресс-бар и подпись
 * «Занято X из Y (N%)». Тап по карточке запускает анализ памяти.
 */
@Composable
private fun StorageWidget(
    stats: ToolDispatcher.StorageStats?,
    onClick: (() -> Unit)? = null
) {
    if (stats == null || stats.totalBytes <= 0L) return
    val ratio = (stats.usedBytes.toFloat() / stats.totalBytes).coerceIn(0f, 1f)

    // Полоса заливается плавно: при первом показе цифры прыгали с нуля
    val animatedRatio by animateFloatAsState(
        targetValue = ratio,
        animationSpec = MotionTokens.fluidTween(MotionTokens.DurationFast),
        label = "storage_ratio"
    )
    val percent = (animatedRatio * 100).toInt()
    val barColor = when {
        percent >= 90 -> MaterialTheme.colorScheme.error
        percent >= 75 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), RoundedCornerShape(12.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Хранилище устройства",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "$percent%",
                color = barColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace
            )
        }
        LinearProgressIndicator(
            progress = animatedRatio,
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = barColor,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
        )
        Text(
            text = "Занято ${ToolDispatcher.formatFileSize(stats.usedBytes)} " +
                    "из ${ToolDispatcher.formatFileSize(stats.totalBytes)} ($percent%)",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun SuggestionCard(
    modifier: Modifier = Modifier,
    suggestion: Suggestion,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)), RoundedCornerShape(12.dp))
            .bounceClick(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = suggestion.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(19.dp)
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = suggestion.title,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

/** Мелкая иконка действия под сообщением: копировать, изменить, заново. */
@Composable
private fun MessageActionIcon(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun MessageItem(
    message: ChatMessage,
    showToolDetails: Boolean,
    showStats: Boolean,
    planAwaitingId: String?,
    onApprovePlan: () -> Unit,
    onRejectPlan: () -> Unit,
    onEditMessage: (String) -> Unit,
    onRetryMessage: (String) -> Unit,
    onOpenPreview: (WebPreviewSource) -> Unit,
    onAction: (MessageAction) -> Unit
) {
    when (message.role) {
        MessageRole.USER -> {
            val context = LocalContext.current
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.End
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .clip(RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = message.content,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    MessageActionIcon(
                        icon = Icons.Outlined.ContentCopy,
                        label = "Копировать",
                        onClick = {
                            val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            manager.setPrimaryClip(ClipData.newPlainText("Сообщение", message.content))
                            Toast.makeText(context, "Скопировано", Toast.LENGTH_SHORT).show()
                        }
                    )
                    MessageActionIcon(
                        icon = Icons.Outlined.Edit,
                        label = "Изменить",
                        // Текст уходит в поле ввода, менять историю молча не станем
                        onClick = { onEditMessage(message.content) }
                    )
                    MessageActionIcon(
                        icon = Icons.Outlined.Refresh,
                        label = "Заново",
                        onClick = { onRetryMessage(message.id) }
                    )
                }
            }
        }

        MessageRole.ASSISTANT -> {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.96f)
                    .padding(vertical = 2.dp)
            ) {
                if (showStats) {
                    ReasoningBlock(
                        reasoning = message.reasoningText,
                        reasoningMs = message.reasoningMs
                    )
                }

                MarkdownView(text = message.content)

                // Если в ответе есть готовая HTML/SVG-разметка — показываем её живьём
                val preview = remember(message.content) {
                    findWebPreviewInMarkdown(message.content)
                }
                if (preview != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    PreviewButton(
                        label = "Открыть превью ${preview.title}",
                        onClick = { onOpenPreview(preview) }
                    )
                }

                if (showStats) {
                    ResponseStats(message)
                }
            }
        }

        MessageRole.TOOL_EXECUTION -> ToolCard(
            message = message,
            showDetails = showToolDetails,
            showStats = showStats,
            onOpenPreview = onOpenPreview
        )

        MessageRole.PLAN -> PlanCard(
            message = message,
            awaitingDecision = message.id == planAwaitingId,
            onApprove = onApprovePlan,
            onReject = onRejectPlan
        )

        MessageRole.SYSTEM_INFO -> NoticeBanner(
            text = message.content,
            isError = message.isError,
            action = message.action,
            onAction = onAction
        )
    }
}

/** Плашка под ответом: сколько занял весь пайплайн, токены и скорость генерации. */
@Composable
private fun ResponseStats(message: ChatMessage) {
    val parts = buildList {
        message.durationMs?.let { add("⏱ ${formatDuration(it)}") }
        val prompt = message.promptTokens
        val completion = message.completionTokens
        if (prompt != null || completion != null) {
            add("${prompt ?: 0}→${completion ?: 0} ток")
        }
        message.tokensPerSecond?.let { add("%.0f т/с".format(it)) }
    }

    if (parts.isEmpty()) return

    Row(
        modifier = Modifier.padding(top = 6.dp, start = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        parts.forEach { part ->
            Text(
                text = part,
                color = MaterialTheme.colorScheme.outline,
                fontSize = 10.5.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

/** Размышления thinking-модели: счётчик на виду, процесс — под катом. */
@Composable
private fun ReasoningBlock(reasoning: String?, reasoningMs: Long?) {
    if (reasoning.isNullOrBlank()) return

    var expanded by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = MotionTokens.fluidSpring(),
        label = "reasoning_chevron"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant), RoundedCornerShape(10.dp))
            .animateContentSize(animationSpec = MotionTokens.fluidSpring())
            .clickable { expanded = !expanded }
            .padding(horizontal = 12.dp, vertical = 9.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.Psychology,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (reasoningMs != null && reasoningMs > 0) {
                    "Думал ${formatDuration(reasoningMs)}"
                } else {
                    "Размышления"
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Outlined.KeyboardArrowDown,
                contentDescription = if (expanded) "Свернуть" else "Развернуть",
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier
                    .size(16.dp)
                    .graphicsLayer { rotationZ = rotation }
            )
        }

        if (expanded) {
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = reasoning,
                color = MaterialTheme.colorScheme.outline,
                fontSize = 11.5.sp,
                lineHeight = 17.sp
            )
        }
    }
}

/** Компактная длительность: 480 мс, 2.4 с, 1 мин 12 с. */
private fun formatDuration(millis: Long): String = when {
    millis < 1000L -> "$millis мс"
    millis < 60_000L -> "%.1f с".format(millis / 1000.0)
    else -> {
        val totalSeconds = millis / 1000
        "${totalSeconds / 60} мин ${totalSeconds % 60} с"
    }
}

@Composable
private fun StreamingBubble(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .padding(vertical = 2.dp)
        ) {
            Column {
                MarkdownView(text = text)
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .width(22.dp)
                        .height(2.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                )
            }
        }
    }
}

/** Абсолютные пути из результата тула (строки resolved_path[:_src|_dst]: ...). */
/**
 * Карточка плана шагов: нумерованные шаги + кнопки утверждения.
 * После решения кнопки гаснут (awaitingDecision=false), карточка остаётся историей.
 */
@Composable
private fun PlanCard(
    message: ChatMessage,
    awaitingDecision: Boolean,
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    val steps = remember(message.content) { parsePlanStepsUi(message.content) }

    // Пока план ждёт решения — раскрыт, иначе его нельзя осознанно утвердить.
    // После решения сворачивается в одну строку, чтобы не занимать пол-экрана.
    var expanded by remember(message.id) { mutableStateOf(awaitingDecision) }
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = MotionTokens.fluidSpring(),
        label = "plan_chevron"
    )
    LaunchedEffect(awaitingDecision) {
        if (!awaitingDecision) expanded = false
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = MotionTokens.fluidSpring())
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "План действий",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = PluralUtils.steps(steps.size),
                    color = MaterialTheme.colorScheme.outline,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Outlined.KeyboardArrowDown,
                    contentDescription = if (expanded) "Свернуть" else "Показать шаги",
                    tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
                    modifier = Modifier
                        .size(14.dp)
                        .graphicsLayer { rotationZ = rotation }
                )
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(8.dp))

                if (steps.isEmpty()) {
                    Text(
                        text = message.content,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.5.sp,
                        lineHeight = 18.sp
                    )
                } else {
                    steps.forEachIndexed { i, step ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = "${i + 1}.",
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.width(22.dp)
                            )
                            Text(
                                text = step,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 12.5.sp,
                                lineHeight = 18.sp,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            if (awaitingDecision) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = onApprove,
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Утвердить", fontSize = 13.sp)
                    }
                    OutlinedButton(
                        onClick = onReject,
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Без плана", fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

/** Те же правила, что у парсера модели: строки "N. ..." / "N) ...". */
private fun parsePlanStepsUi(text: String): List<String> {
    val stepRegex = Regex("""^\s*\d+[.)]\s*(.+?)\s*$""")
    return text.lines()
        .mapNotNull { line -> stepRegex.find(line)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() } }
}

/**
 * Элемент ленты чата: одиночное сообщение либо схлопнутая серия
 * одинаковых вызовов инструментов.
 */
private sealed interface ChatListItem {
    data class Single(val message: ChatMessage) : ChatListItem
    data class ToolGroup(val messages: List<ChatMessage>) : ChatListItem
}

/**
 * Схлопывает ЛЮБУЮ идущую подряд серию завершённых вызовов в одну карточку,
 * а не только одинаковые тулы: иначе после каждого шага агента в ленте
 * плодятся отдельные плашки и полезный ответ в них тонет.
 * Живые (isRunning) вызовы и одиночные не группируем.
 */
private fun groupChatItems(messages: List<ChatMessage>): List<ChatListItem> {
    val out = mutableListOf<ChatListItem>()
    var run = mutableListOf<ChatMessage>()
    fun flush() {
        if (run.size == 1) out += ChatListItem.Single(run[0])
        else if (run.isNotEmpty()) out += ChatListItem.ToolGroup(run.toList())
        run = mutableListOf()
    }
    for (m in messages) {
        val isFinishedTool = m.role == MessageRole.TOOL_EXECUTION && !m.isRunning && m.toolName != null
        if (isFinishedTool) {
            run += m
        } else {
            flush()
            out += ChatListItem.Single(m)
        }
    }
    flush()
    return out
}

/**
 * Аккордеон серии вызовов: «Выполнено N действий (нажми для деталей)».
 * Внутри — заголовок и вывод каждого вызова, стиль как у лёгкой тул-плашки.
 */
@Composable
private fun ToolGroupCard(
    messages: List<ChatMessage>,
    onOpenPreview: (WebPreviewSource) -> Unit
) {
    var expanded by remember(messages.first().id) { mutableStateOf(false) }
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = MotionTokens.fluidSpring(),
        label = "group_chevron_rotation"
    )
    val hasErrors = messages.any { it.isError }
    val compactShape = RoundedCornerShape(9.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(compactShape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.45f))
            .border(
                BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                compactShape
            )
            .animateContentSize(animationSpec = MotionTokens.fluidSpring())
            .clickable { expanded = !expanded }
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(if (hasErrors) MaterialTheme.colorScheme.error else StatusSuccess)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Выполнено ${PluralUtils.actions(messages.size)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Text(
                    text = "· ${messages.first().content}",
                    color = MaterialTheme.colorScheme.outline,
                    fontSize = 11.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 6.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Outlined.KeyboardArrowDown,
                    contentDescription = if (expanded) "Свернуть" else "Детали",
                    tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
                    modifier = Modifier
                        .size(14.dp)
                        .graphicsLayer { rotationZ = rotation }
                )
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(9.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(4.dp))
                messages.forEachIndexed { index, m ->
                    if (index > 0) {
                        Spacer(modifier = Modifier.height(4.dp))
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            thickness = 0.5.dp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    Text(
                        text = m.content,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!m.toolOutput.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(3.dp))
                        ToolOutputView(output = m.toolOutput)
                    }
                    val preview = remember(m.toolArgs, m.toolOutput) {
                        findWebPreviewInToolCall(
                            toolName = m.toolName.orEmpty(),
                            args = m.toolArgs,
                            output = m.toolOutput
                        )
                    }
                    if (preview != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        PreviewButton(
                            label = "Открыть превью ${preview.title}",
                            onClick = { onOpenPreview(preview) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Свёрнутая строка вызова инструмента: «Сканирую Download · 143 файла».
 * Технические детали — только по нажатию.
 */
@Composable
private fun ToolCard(
    message: ChatMessage,
    showDetails: Boolean,
    showStats: Boolean,
    onOpenPreview: (WebPreviewSource) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = MotionTokens.fluidSpring(),
        label = "chevron_rotation"
    )
    val hasDetails = showDetails && (
            !message.toolArgs.isNullOrBlank() || !message.toolOutput.isNullOrBlank()
            )

    // Лёгкая плашка-аккордеон: едва заметный фон, волосяная рамка,
    // свернутый текст 12.5sp. Раскрытие — по тапу на всю строку.
    val compactShape = RoundedCornerShape(9.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(compactShape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.45f))
            .border(
                BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                compactShape
            )
            .animateContentSize(animationSpec = MotionTokens.fluidSpring())
            .clickable(enabled = hasDetails && !message.isRunning) { expanded = !expanded }
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ToolStatusIcon(message)
                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = message.content,
                    color = if (message.isDeclined) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                if (!message.toolSummary.isNullOrBlank()) {
                    Text(
                        text = "· ${message.toolSummary}",
                        color = MaterialTheme.colorScheme.outline,
                        fontSize = 11.5.sp,
                        maxLines = 1,
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }

                // Время самой операции: сразу видно, что было узким местом — диск или сеть
                if (showStats && !message.isRunning && message.toolDurationMs != null) {
                    Text(
                        text = "· ${formatDuration(message.toolDurationMs)}",
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.8f),
                        fontSize = 10.5.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }

                // Раньше здесь стоял Spacer с weight(1f), а второй weight висел на заголовке —
                // ширина делилась пополам, и статус обрезался на пустом месте.
                Spacer(modifier = Modifier.width(6.dp))

                if (hasDetails && !message.isRunning) {
                    Icon(
                        imageVector = Icons.Outlined.KeyboardArrowDown,
                        contentDescription = if (expanded) "Свернуть" else "Развернуть",
                        tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
                        modifier = Modifier
                            .size(14.dp)
                            .graphicsLayer { rotationZ = rotation }
                    )
                }
            }

            if (expanded && hasDetails) {
                Spacer(modifier = Modifier.height(9.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(8.dp))

                if (!message.toolArgs.isNullOrBlank() && message.toolArgs != "{}") {
                    Text(
                        text = "Аргументы",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = message.toolArgs,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(9.dp))
                }

                if (!message.toolOutput.isNullOrBlank()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Результат",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                manager.setPrimaryClip(
                                    ClipData.newPlainText("Результат инструмента", message.toolOutput)
                                )
                                Toast.makeText(context, "Скопировано", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ContentCopy,
                                contentDescription = "Копировать",
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    ToolOutputView(output = message.toolOutput)
                }
            }

            // Превью показываем и в свёрнутой карточке: агент только что создал
            // страницу, и лишний тап для её открытия ни к чему
            val preview = remember(message.toolArgs, message.toolOutput) {
                findWebPreviewInToolCall(
                    toolName = message.toolName.orEmpty(),
                    args = message.toolArgs,
                    output = message.toolOutput
                )
            }
            if (preview != null) {
                Spacer(modifier = Modifier.height(8.dp))
                PreviewButton(
                    label = "Открыть превью ${preview.title}",
                    onClick = { onOpenPreview(preview) }
                )
            }
        }
    }
}

@Composable
private fun ToolStatusIcon(message: ChatMessage) {
    // Компактный статус: маленькая точка вместо иконки 15dp.
    // Зелёная — успех, красная — ошибка, серая — отклонено/ожидание.
    when {
        message.isRunning -> CircularProgressIndicator(
            modifier = Modifier.size(11.dp),
            strokeWidth = 1.6.dp,
            color = MaterialTheme.colorScheme.primary
        )

        else -> {
            val dotColor = when {
                message.isDeclined -> MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                message.isError -> MaterialTheme.colorScheme.error
                else -> StatusSuccess
            }
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
        }
    }
}

/** Баннер вместо текстовой ошибки: объясняет, что делать, и даёт кнопку. */
@Composable
private fun NoticeBanner(
    text: String,
    isError: Boolean,
    action: MessageAction?,
    onAction: (MessageAction) -> Unit
) {
    val accent = if (isError) MaterialTheme.colorScheme.error else StatusWarning

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(BorderStroke(1.dp, accent.copy(alpha = 0.35f)), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                imageVector = if (isError) Icons.Outlined.ErrorOutline else Icons.Outlined.Info,
                contentDescription = null,
                tint = accent,
                modifier = Modifier
                    .padding(top = 1.dp)
                    .size(15.dp)
            )
            Spacer(modifier = Modifier.width(9.dp))
            Text(
                text = text,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        }

        if (action != null) {
            val label = when (action) {
                MessageAction.OPEN_SETTINGS -> "Открыть настройки"
                MessageAction.OPEN_MODELS -> "Выбрать модель"
                MessageAction.GRANT_STORAGE -> "Разрешить доступ"
            }
            TextButton(
                onClick = { onAction(action) },
                modifier = Modifier.padding(start = 24.dp, top = 2.dp)
            ) {
                Text(label, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun PermissionBanner(onOpenSettings: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(BorderStroke(1.dp, StatusWarning.copy(alpha = 0.35f)), RoundedCornerShape(10.dp))
            .padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Outlined.WarningAmber,
            contentDescription = null,
            tint = StatusWarning,
            modifier = Modifier.size(15.dp)
        )
        Spacer(modifier = Modifier.width(9.dp))
        Text(
            text = "Нет доступа ко всем файлам",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 12.5.sp,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onOpenSettings) {
            Text("Выдать", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
        }
    }
}

/**
 * Подтверждение деструктивной операции с превью того, что именно будет затронуто.
 */
/**
 * First-run плашка разрешений: один раз при входе без прав предлагаем выдать
 * «Доступ ко всем файлам» (системный экран — тумблер в приложении право не даёт).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StoragePromptSheet(
    onGrant: () -> Unit,
    onLater: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onLater,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.65f),
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
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(StatusWarning.copy(alpha = 0.12f))
                        .border(
                            BorderStroke(1.dp, StatusWarning.copy(alpha = 0.30f)),
                            RoundedCornerShape(10.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.WarningAmber,
                        contentDescription = null,
                        tint = StatusWarning,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Доступ ко всем файлам",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Нужен, чтобы агент читал и раскладывал файлы",
                        color = MaterialTheme.colorScheme.outline,
                        fontSize = 12.5.sp
                    )
                }
            }

            Text(
                text = "Без него файловые инструменты не сработают. Право выдаётся только на системном экране, кнопка ниже его откроет.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                lineHeight = 19.sp
            )

            Button(
                onClick = onGrant,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
                shape = RoundedCornerShape(11.dp)
            ) {
                Text("Выдать доступ", fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }

            TextButton(
                onClick = onLater,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Позже", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.5.sp)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfirmationBottomSheet(
    request: ConfirmationRequest,
    onConfirm: (Set<String>?, Boolean) -> Unit,
    onReject: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Список объектов с галочками: по умолчанию отмечено всё
    var selectedIds by remember(request.id) {
        mutableStateOf(request.items.map { it.id }.toSet())
    }
    // «Больше не спрашивать» действует только на это конкретное действие:
    // разрешение открыть YouTube не отменяет вопрос при удалении папки.
    var remember by remember(request.id) { mutableStateOf(false) }
    val hasItems = request.items.isNotEmpty()
    val allSelected = !hasItems || selectedIds.size == request.items.size

    ModalBottomSheet(
        onDismissRequest = onReject,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.65f),
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
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column {
                Text(
                    text = request.title,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Подтверждение операции",
                    color = MaterialTheme.colorScheme.outline,
                    fontSize = 12.5.sp
                )
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = request.details,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(12.dp)
                )
            }

            if (request.preview.isNotBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = request.preview,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    imageVector = Icons.Outlined.WarningAmber,
                    contentDescription = null,
                    tint = StatusWarning,
                    modifier = Modifier
                        .padding(top = 1.dp)
                        .size(16.dp)
                )
                Spacer(modifier = Modifier.width(9.dp))
                Text(
                    text = request.warning,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp
                )
            }

            // Список с галочками: пользователь снимает то, что удалять не нужно
            if (hasItems) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Что удалить",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${selectedIds.size} из ${request.items.size}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.5.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .heightIn(max = 260.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        request.items.forEach { item ->
                            val checked = item.id in selectedIds
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedIds = if (checked) {
                                            selectedIds - item.id
                                        } else {
                                            selectedIds + item.id
                                        }
                                    }
                                    .padding(end = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = { isChecked ->
                                        selectedIds = if (isChecked) {
                                            selectedIds + item.id
                                        } else {
                                            selectedIds - item.id
                                        }
                                    },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = MaterialTheme.colorScheme.primary,
                                        checkmarkColor = MaterialTheme.colorScheme.onPrimary,
                                        uncheckedColor = MaterialTheme.colorScheme.outline
                                    )
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.label,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (item.detail.isNotBlank()) {
                                        Text(
                                            text = item.detail,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Запоминание конкретного действия. Показываем только когда его
            // есть смысл запоминать: удаление папки со списком файлов —
            // разовое действие, повторять его «молча» опасно.
            if (!hasItems) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { remember = !remember }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = remember,
                        onCheckedChange = { remember = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = MaterialTheme.colorScheme.primary,
                            checkmarkColor = MaterialTheme.colorScheme.onPrimary,
                            uncheckedColor = MaterialTheme.colorScheme.outline
                        )
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Больше не спрашивать это действие",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "Повтор этой же операции пойдёт без подтверждения. " +
                                    "Другие действия всё равно спросят.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.5.sp,
                            lineHeight = 15.sp
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onReject,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    shape = RoundedCornerShape(11.dp)
                ) {
                    Text("Отклонить", fontSize = 14.sp)
                }

                Button(
                    onClick = { onConfirm(if (hasItems) selectedIds else null, remember) },
                    enabled = !hasItems || selectedIds.isNotEmpty(),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        disabledContentColor = MaterialTheme.colorScheme.outline
                    ),
                    shape = RoundedCornerShape(11.dp)
                ) {
                    Text(
                        text = when {
                            !hasItems -> "Разрешить"
                            allSelected -> "Удалить все"
                            else -> "Удалить ${PluralUtils.files(selectedIds.size)}"
                        },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
