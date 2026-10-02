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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.runner.app.ui.components.ChatInputBar
import com.runner.app.ui.components.MarkdownView
import com.runner.app.ui.components.ModelPickerSheet
import com.runner.app.ui.components.ProviderLogos
import com.runner.app.ui.components.RunnerIcons
import com.runner.app.ui.components.ToolOutputView
import com.runner.app.ui.components.rememberChatInputState
import com.runner.app.ui.theme.MotionTokens
import com.runner.app.ui.theme.StatusSuccess
import com.runner.app.ui.theme.StatusWarning
import com.runner.app.ui.theme.bounceClick
import com.runner.app.util.PluralUtils

@OptIn(ExperimentalMaterial3Api::class)
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

    val isEmptyChat = messages.none { it.role != MessageRole.SYSTEM_INFO }

    LaunchedEffect(messages.size, streamingText, currentStatus) {
        val target = messages.size + if (streamingText.isNotBlank()) 1 else 0
        if (target > 0) listState.animateScrollToItem(target - 1)
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
            onConfirm = { viewModel.resolveConfirmation(true) },
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
                actions = {
                    IconButton(
                        onClick = { viewModel.startNewChat() },
                        modifier = Modifier.bounceClick { viewModel.startNewChat() }
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Add,
                            contentDescription = "Новый чат",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
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
                                when (item) {
                                    is ChatListItem.Single -> MessageItem(
                                        message = item.message,
                                        showToolDetails = appSettings.showToolDetails,
                                        showStats = appSettings.showStats,
                                        planAwaitingId = pendingPlan?.id,
                                        onApprovePlan = { viewModel.resolvePlan(true) },
                                        onRejectPlan = { viewModel.resolvePlan(false) },
                                        onAction = { action ->
                                            when (action) {
                                                MessageAction.OPEN_SETTINGS -> onOpenSettings()
                                                MessageAction.OPEN_MODELS -> showModelPicker = true
                                                MessageAction.GRANT_STORAGE -> onOpenStorageSettings()
                                            }
                                        }
                                    )

                                    is ChatListItem.ToolGroup -> ToolGroupCard(messages = item.messages)
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

    val parts = name.split('-', '_')
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
        displayName.ifBlank { formatModelName(modelName) }
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
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), RoundedCornerShape(20.dp))
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
 * Компактный экран пустого чата с аккуратной сеткой 2×2 быстрых действий.
 */
@Composable
private fun EmptyChatState(
    modifier: Modifier = Modifier,
    onSuggestion: (String) -> Unit
) {
    val suggestions = listOf(
        Triple(Icons.Outlined.Storage, "Сводка памяти", "Сделай сводку по памяти устройства"),
        Triple(Icons.Outlined.FolderOpen, "Папка Download", "Покажи сводку по папке Download"),
        Triple(Icons.Outlined.WarningAmber, "Найти мусор", "Найди временные и мусорные файлы"),
        Triple(Icons.Outlined.Info, "Свободное место", "Сколько свободного место на устройстве?")
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Runner",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.5).sp
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Агент для работы с файлами, памятью и терминалом прямо на телефоне.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.5.sp,
            lineHeight = 20.sp
        )
        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SuggestionCard(
                modifier = Modifier.weight(1f),
                icon = suggestions[0].first,
                title = suggestions[0].second,
                onClick = { onSuggestion(suggestions[0].third) }
            )
            SuggestionCard(
                modifier = Modifier.weight(1f),
                icon = suggestions[1].first,
                title = suggestions[1].second,
                onClick = { onSuggestion(suggestions[1].third) }
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SuggestionCard(
                modifier = Modifier.weight(1f),
                icon = suggestions[2].first,
                title = suggestions[2].second,
                onClick = { onSuggestion(suggestions[2].third) }
            )
            SuggestionCard(
                modifier = Modifier.weight(1f),
                icon = suggestions[3].first,
                title = suggestions[3].second,
                onClick = { onSuggestion(suggestions[3].third) }
            )
        }
    }
}

@Composable
private fun SuggestionCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), RoundedCornerShape(12.dp))
            .bounceClick(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            lineHeight = 18.sp,
            maxLines = 2
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
    onAction: (MessageAction) -> Unit
) {
    when (message.role) {
        MessageRole.USER -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
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

                if (showStats) {
                    ResponseStats(message)
                }
            }
        }

        MessageRole.TOOL_EXECUTION -> ToolCard(
            message = message,
            showDetails = showToolDetails,
            showStats = showStats
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
private fun extractResolvedPaths(toolOutput: String): List<String> =
    toolOutput.lineSequence()
        .map { it.trim() }
        .filter { it.startsWith("resolved_path") }
        .mapNotNull { line ->
            val value = line.substringAfter(':', missingDelimiterValue = "").trim()
            value.takeIf { it.isNotBlank() }
        }
        .distinct()
        .toList()

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

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
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
            }

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
 * Схлопывает идущие подряд завершённые вызовы одного и того же тула
 * («Выполняю команду...» × N) в единую группу. Живые (isRunning) и
 * одиночные вызовы не трогаем.
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
        val continues = m.role == MessageRole.TOOL_EXECUTION && !m.isRunning &&
                m.toolName != null && (run.isEmpty() || run.last().toolName == m.toolName)
        if (continues) {
            run += m
        } else {
            flush()
            if (m.role == MessageRole.TOOL_EXECUTION && !m.isRunning && m.toolName != null) {
                run += m
            } else {
                out += ChatListItem.Single(m)
            }
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
private fun ToolGroupCard(messages: List<ChatMessage>) {
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
private fun ToolCard(message: ChatMessage, showDetails: Boolean, showStats: Boolean) {
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

                val resolvedPaths = remember(message.toolOutput) { extractResolvedPaths(message.toolOutput.orEmpty()) }
                if (resolvedPaths.isNotEmpty()) {
                    Text(
                        text = "Путь",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    resolvedPaths.forEach { path ->
                        Text(
                            text = path,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
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
    onConfirm: () -> Unit,
    onReject: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
                    onClick = onConfirm,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(11.dp)
                ) {
                    Text("Разрешить", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}
