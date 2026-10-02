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
import com.runner.app.ui.components.MarkdownView
import com.runner.app.ui.components.ModelPickerSheet
import com.runner.app.ui.components.RunnerIcons
import com.runner.app.ui.components.ToolOutputView
import com.runner.app.ui.theme.AccentPrimary
import com.runner.app.ui.theme.MotionTokens
import com.runner.app.ui.theme.OutlineSubtle
import com.runner.app.ui.theme.StatusError
import com.runner.app.ui.theme.StatusSuccess
import com.runner.app.ui.theme.StatusWarning
import com.runner.app.ui.theme.SurfaceContainer
import com.runner.app.ui.theme.SurfaceContainerHigh
import com.runner.app.ui.theme.SurfaceContainerLow
import com.runner.app.ui.theme.SurfaceContainerLowest
import com.runner.app.ui.theme.SurfaceDark
import com.runner.app.ui.theme.TextPrimary
import com.runner.app.ui.theme.TextSecondary
import com.runner.app.ui.theme.TextTertiary
import com.runner.app.ui.theme.bounceClick

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
    val hasStoragePermission by viewModel.hasStoragePermission.collectAsState()
    val appSettings by viewModel.settings.collectAsState()

    var inputText by remember { mutableStateOf("") }
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

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer, modifier = Modifier.bounceClick { onOpenDrawer() }) {
                        Icon(
                            imageVector = Icons.Outlined.Menu,
                            contentDescription = "Чаты",
                            tint = TextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                },
                title = {
                    ModelChip(
                        providerName = provider?.name ?: "Провайдер",
                        modelName = provider?.activeModel.orEmpty().ifBlank { "модель не выбрана" },
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
                            tint = TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SurfaceDark,
                    titleContentColor = TextPrimary
                )
            )
        },
        containerColor = SurfaceDark
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
                    onSuggestion = { inputText = it }
                )
            } else {
                Column(modifier = Modifier.weight(1f)) {
                    CompositionLocalProvider(
                        LocalDensity provides Density(
                            LocalDensity.current.density,
                            LocalDensity.current.fontScale * appSettings.textScale
                        )
                    ) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(messages, key = { it.id }) { message ->
                                MessageItem(
                                    message = message,
                                    showToolDetails = appSettings.showToolDetails,
                                    onAction = { action ->
                                        when (action) {
                                            MessageAction.OPEN_SETTINGS -> onOpenSettings()
                                            MessageAction.OPEN_MODELS -> showModelPicker = true
                                            MessageAction.GRANT_STORAGE -> onOpenStorageSettings()
                                        }
                                    }
                                )
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
                        color = AccentPrimary
                    )
                    Spacer(modifier = Modifier.width(9.dp))
                    Text(
                        text = currentStatus ?: "Работаю",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            InputBar(
                value = inputText,
                onValueChange = { inputText = it },
                isRunning = isRunning,
                onSend = {
                    val text = inputText
                    inputText = ""
                    viewModel.sendMessage(text)
                },
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

/** Компактный чип-селектор модели в шапке: иконка + форматированное имя + шеврон. */
@Composable
private fun ModelChip(
    providerName: String,
    modelName: String,
    hasKey: Boolean,
    isLoading: Boolean,
    onClick: () -> Unit
) {
    val cleanModel = remember(modelName) { formatModelName(modelName) }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(SurfaceContainerLow)
            .border(BorderStroke(1.dp, OutlineSubtle), RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Outlined.AutoAwesome,
            contentDescription = null,
            tint = if (hasKey) AccentPrimary else StatusWarning,
            modifier = Modifier.size(15.dp)
        )
        Spacer(modifier = Modifier.width(7.dp))
        Text(
            text = cleanModel,
            color = if (hasKey) TextPrimary else StatusWarning,
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
                color = AccentPrimary
            )
        } else {
            Icon(
                imageVector = Icons.Outlined.KeyboardArrowDown,
                contentDescription = "Выбрать модель",
                tint = TextTertiary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/**
 * Нижняя панель: динамическая подсветка контура при фокусе и вводе,
 * поддержка ImeAction.Send с клавиатуры и гарантированная обработка отправки.
 */
@Composable
private fun InputBar(
    value: String,
    onValueChange: (String) -> Unit,
    isRunning: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val canSend = value.isNotBlank() && !isRunning
    val isActive = isFocused || value.isNotBlank()

    val borderColor by animateColorAsState(
        targetValue = when {
            isRunning -> StatusError.copy(alpha = 0.4f)
            isActive -> AccentPrimary.copy(alpha = 0.45f)
            else -> OutlineSubtle
        },
        animationSpec = MotionTokens.fluidTween(200),
        label = "input_border"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Нижний отступ больше верхнего: панель не должна лежать на полоске навигации
            .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 12.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(SurfaceContainer)
            .border(BorderStroke(1.dp, borderColor), RoundedCornerShape(24.dp))
            .padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .weight(1f)
                .padding(start = 14.dp, end = 6.dp, top = 11.dp, bottom = 11.dp)
                .onFocusChanged { isFocused = it.isFocused },
            textStyle = TextStyle(color = TextPrimary, fontSize = 15.sp, lineHeight = 21.sp),
            maxLines = 5,
            minLines = 1,
            cursorBrush = SolidColor(AccentPrimary),
            keyboardOptions = KeyboardOptions(
                imeAction = if (canSend) ImeAction.Send else ImeAction.Default
            ),
            keyboardActions = KeyboardActions(
                onSend = {
                    if (canSend) onSend()
                }
            ),
            decorationBox = { innerTextField ->
                Box {
                    if (value.isEmpty()) {
                        Text(
                            text = "Задать задачу",
                            color = TextTertiary,
                            fontSize = 15.sp,
                            lineHeight = 21.sp
                        )
                    }
                    innerTextField()
                }
            }
        )

        SendStopButton(
            isRunning = isRunning,
            canSend = canSend,
            onSend = onSend,
            onStop = onStop
        )
    }
}

/**
 * Круглая кнопка: плавно загорается акцентным синим при вводе текста,
 * пульсирует в стоп во время выполнения, корректно вызывает onSend/onStop.
 */
@Composable
private fun SendStopButton(
    isRunning: Boolean,
    canSend: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit
) {
    val enabled = isRunning || canSend

    val background by animateColorAsState(
        targetValue = when {
            isRunning -> StatusError.copy(alpha = 0.18f)
            canSend -> AccentPrimary
            else -> SurfaceContainerHigh.copy(alpha = 0.6f)
        },
        animationSpec = MotionTokens.fluidTween(220),
        label = "send_background"
    )

    val iconTint by animateColorAsState(
        targetValue = when {
            isRunning -> StatusError
            canSend -> SurfaceDark
            else -> TextTertiary
        },
        animationSpec = MotionTokens.fluidTween(220),
        label = "send_tint"
    )

    val buttonScale by animateFloatAsState(
        targetValue = if (canSend || isRunning) 1f else 0.92f,
        animationSpec = MotionTokens.fluidSpring(),
        label = "send_scale"
    )

    Box(
        modifier = Modifier
            .padding(bottom = 3.dp, end = 3.dp)
            .size(38.dp)
            .graphicsLayer {
                scaleX = buttonScale
                scaleY = buttonScale
            }
            .clip(CircleShape)
            .background(background)
            .bounceClick(enabled = enabled, scaleDown = 0.90f) {
                if (isRunning) {
                    onStop()
                } else if (canSend) {
                    onSend()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isRunning) RunnerIcons.StopSquare else RunnerIcons.ArrowUp,
            contentDescription = if (isRunning) "Остановить" else "Отправить",
            tint = iconTint,
            modifier = Modifier.size(18.dp)
        )
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
        Triple(Icons.Outlined.Info, "Свободное место", "Сколько свободного места на устройстве?")
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Runner",
            color = TextPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.5).sp
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Агент для работы с файлами, памятью и терминалом прямо на телефоне.",
            color = TextSecondary,
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
            .background(SurfaceContainerLow)
            .border(BorderStroke(1.dp, OutlineSubtle), RoundedCornerShape(12.dp))
            .bounceClick(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = AccentPrimary,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = title,
            color = TextPrimary,
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
                        .background(SurfaceContainerHigh)
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = message.content,
                        color = TextPrimary,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }

        MessageRole.ASSISTANT -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.96f)
                        .clip(RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp))
                        .padding(vertical = 2.dp)
                ) {
                    MarkdownView(text = message.content)
                }
            }
        }

        MessageRole.TOOL_EXECUTION -> ToolCard(message = message, showDetails = showToolDetails)

        MessageRole.SYSTEM_INFO -> NoticeBanner(
            text = message.content,
            isError = message.isError,
            action = message.action,
            onAction = onAction
        )
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
                        .background(AccentPrimary.copy(alpha = 0.7f))
                )
            }
        }
    }
}

/**
 * Свёрнутая строка вызова инструмента: «Сканирую Download · 143 файла».
 * Технические детали — только по нажатию.
 */
@Composable
private fun ToolCard(message: ChatMessage, showDetails: Boolean) {
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

    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow),
        border = BorderStroke(0.5.dp, OutlineSubtle),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = MotionTokens.fluidSpring())
            .clickable(enabled = hasDetails && !message.isRunning) { expanded = !expanded }
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ToolStatusIcon(message)
                Spacer(modifier = Modifier.width(9.dp))

                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (message.isDeclined) TextSecondary else TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                if (!message.toolSummary.isNullOrBlank()) {
                    Text(
                        text = "· ${message.toolSummary}",
                        color = TextTertiary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }

                // Раньше здесь стоял Spacer с weight(1f), а второй weight висел на заголовке —
                // ширина делилась пополам, и статус обрезался на пустом месте.
                Spacer(modifier = Modifier.width(8.dp))

                if (hasDetails && !message.isRunning) {
                    Icon(
                        imageVector = Icons.Outlined.KeyboardArrowDown,
                        contentDescription = if (expanded) "Свернуть" else "Развернуть",
                        tint = TextTertiary,
                        modifier = Modifier
                            .size(17.dp)
                            .graphicsLayer { rotationZ = rotation }
                    )
                }
            }

            if (expanded && hasDetails) {
                Spacer(modifier = Modifier.height(9.dp))
                HorizontalDivider(color = OutlineSubtle, thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(8.dp))

                if (!message.toolArgs.isNullOrBlank() && message.toolArgs != "{}") {
                    Text(
                        text = "Аргументы",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = message.toolArgs,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        color = TextTertiary
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
                            color = TextSecondary,
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
                                tint = TextTertiary,
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
    when {
        message.isRunning -> CircularProgressIndicator(
            modifier = Modifier.size(13.dp),
            strokeWidth = 1.8.dp,
            color = AccentPrimary
        )

        message.isDeclined -> Icon(
            imageVector = Icons.Outlined.Block,
            contentDescription = null,
            tint = TextTertiary,
            modifier = Modifier.size(15.dp)
        )

        message.isError -> Icon(
            imageVector = Icons.Outlined.ErrorOutline,
            contentDescription = null,
            tint = StatusError,
            modifier = Modifier.size(15.dp)
        )

        else -> Icon(
            imageVector = Icons.Outlined.CheckCircle,
            contentDescription = null,
            tint = StatusSuccess,
            modifier = Modifier.size(15.dp)
        )
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
    val accent = if (isError) StatusError else StatusWarning

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(SurfaceContainerLow)
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
                color = TextPrimary,
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
                Text(label, color = AccentPrimary, fontSize = 13.sp)
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
            .background(SurfaceContainerLow)
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
            color = TextPrimary,
            fontSize = 12.5.sp,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onOpenSettings) {
            Text("Выдать", color = AccentPrimary, fontSize = 13.sp)
        }
    }
}

/**
 * Подтверждение деструктивной операции с превью того, что именно будет затронуто.
 */
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
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column {
                Text(
                    text = request.title,
                    color = TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Подтверди операцию",
                    color = TextTertiary,
                    fontSize = 12.5.sp
                )
            }

            Surface(
                color = SurfaceContainerLowest,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, OutlineSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = request.details,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = TextPrimary,
                    modifier = Modifier.padding(12.dp)
                )
            }

            if (request.preview.isNotBlank()) {
                Surface(
                    color = SurfaceContainerHigh.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, OutlineSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = request.preview,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = TextSecondary,
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
                    color = TextSecondary,
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
                        containerColor = SurfaceContainer,
                        contentColor = TextPrimary
                    ),
                    border = BorderStroke(1.dp, OutlineSubtle),
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
                        containerColor = AccentPrimary,
                        contentColor = SurfaceDark
                    ),
                    shape = RoundedCornerShape(11.dp)
                ) {
                    Text("Разрешить", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}
