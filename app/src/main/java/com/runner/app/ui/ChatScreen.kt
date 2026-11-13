package com.runner.app.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import kotlinx.coroutines.launch
import androidx.compose.foundation.Image
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.runner.app.data.AppSettings
import com.runner.app.data.FileChange
import com.runner.app.data.FileChangeKind
import com.runner.app.data.WebSource
import com.runner.app.tools.ToolDispatcher
import com.runner.app.tools.TrashStore
import com.runner.app.util.CrashReportManager
import com.runner.app.ui.components.AgentStatusIndicator
import com.runner.app.ui.components.AudioPreviewPlayer
import com.runner.app.ui.components.ChatInputBar
import com.runner.app.ui.components.MarkdownView
import com.runner.app.service.RunnerAccessibilityService
import com.runner.app.ui.components.LiveModeTopButton
import com.runner.app.ui.components.LivePermissionSheet
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import com.runner.app.ui.components.ModelPickerSheet
import com.runner.app.ui.components.PreviewButton
import com.runner.app.ui.components.ProviderLogos
import com.runner.app.ui.components.StatusPulsingDot
import com.runner.app.ui.components.ToolOutputView
import com.runner.app.ui.components.WebPreviewDialog
import com.runner.app.ui.components.WebPreviewSource
import com.runner.app.ui.components.findWebPreviewInMarkdown
import com.runner.app.ui.components.findWebPreviewInToolCall
import com.runner.app.ui.components.rememberChatInputState
import androidx.compose.ui.graphics.Brush
import com.runner.app.ui.components.TerminalAmbientBackground
import com.runner.app.ui.components.WorkspaceFolderBar
import com.runner.app.ui.components.WorkspaceFolderSheet
import com.runner.app.ui.theme.GoogleSans
import com.runner.app.ui.theme.GoogleSansDisplay
import com.runner.app.ui.theme.MotionTokens
import com.runner.app.ui.theme.SecondaryMint
import com.runner.app.ui.theme.StatusSuccess
import com.runner.app.ui.theme.StatusWarning
import com.runner.app.ui.theme.bounceClick
import com.runner.app.util.PluralUtils
import com.runner.app.ui.components.RunnerIcons

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
    val streamingReasoning by viewModel.streamingReasoning.collectAsState()
    val streamStartedAt by viewModel.streamStartedAt.collectAsState()
    val isRunning by viewModel.isRunning.collectAsState()
    val currentStatus by viewModel.currentStatus.collectAsState()
    val liveState by LiveAgentManager.stateFlow.collectAsState()
    val provider by viewModel.activeProvider.collectAsState()
    val providers by viewModel.providers.collectAsState()
    val activeProviderId by viewModel.activeProviderId.collectAsState()
    val modelsLoadingFor by viewModel.modelsLoadingFor.collectAsState()
    val pendingConfirmation by viewModel.pendingConfirmation.collectAsState()
    val pendingPlan by viewModel.pendingPlan.collectAsState()
    val hasStoragePermission by viewModel.hasStoragePermission.collectAsState()
    val appSettings by viewModel.settings.collectAsState()
    val currentSessionId by viewModel.currentSessionId.collectAsState()
    val isListening by viewModel.isListening.collectAsState()
    val rmsLevel by viewModel.rmsLevel.collectAsState()
    val isSpeaking by viewModel.isSpeaking.collectAsState()
    val activeUtteranceId by viewModel.activeUtteranceId.collectAsState()
    val context = LocalContext.current
    // Журнал нужен в шапке: кнопка «Отменить последнее» показывает самую свежую
    // обратимую операцию, и она же — самая заметная.
    val trashState by viewModel.trashState.collectAsState()
    val lastUndoable = trashState.entries.firstOrNull()
    var undoCandidate by remember { mutableStateOf<TrashStore.TrashEntry?>(null) }

    val inputState = rememberChatInputState()
    var showModelPicker by remember { mutableStateOf(false) }
    var showFolderSheet by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.checkAllPermissions()
            viewModel.startVoiceInput(
                onPartialResult = { partial -> inputState.setText(partial) },
                onFinalResult = { final -> inputState.setText(final) },
                onError = { error -> Toast.makeText(context, error, Toast.LENGTH_SHORT).show() }
            )
        } else {
            Toast.makeText(context, "Требуется разрешение на запись аудио для голосового ввода", Toast.LENGTH_SHORT).show()
        }
    }

    // Живое превью HTML/SVG: источник появляется из ответа модели или из write_file
    var previewSource by remember { mutableStateOf<WebPreviewSource?>(null) }
    // Шторка источников веб-поиска: null — закрыта.
    var sourcesSheet by remember { mutableStateOf<List<WebSource>?>(null) }
    // Шторка файловых изменений с диффами.
    var changesSheet by remember { mutableStateOf<List<FileChange>?>(null) }
    var showLivePermissionSheet by remember { mutableStateOf(false) }

    val isEmptyChat = messages.none { it.role != MessageRole.SYSTEM_INFO }
    // Лента строится заранее: по ней же считается автоскролл. Раньше скролл
    // использовал число сообщений, а рисуемых элементов после группировки
    // меньше, и индекс уезжал за пределы списка.
    val chatItems = remember(messages) { groupChatItems(messages) }
    val scope = rememberCoroutineScope()

    // Плеер и синтез речи глушим при уходе с экрана:
    // иначе они продолжат звучать поверх других экранов
    DisposableEffect(Unit) {
        onDispose {
            AudioPreviewPlayer.stop()
            viewModel.stopSpeaking()
            viewModel.stopVoiceInput()
        }
    }

    LaunchedEffect(messages.size) {
        if (chatItems.isNotEmpty()) {
            listState.animateScrollToItem(chatItems.size - 1)
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

    if (showLivePermissionSheet) {
        LivePermissionSheet(
            onDismiss = { showLivePermissionSheet = false },
            onAllGranted = {
                viewModel.updateAppSettings { copy(liveModeEnabled = true) }
            }
        )
    }

    if (showModelPicker && pendingConfirmation == null) {
        ModelPickerSheet(
            providers = providers,
            activeProviderId = activeProviderId,
            loadingProviderId = modelsLoadingFor,
            favoriteModels = appSettings.favoriteModels,
            onToggleFavorite = { providerId, model ->
                viewModel.toggleFavoriteModel(providerId, model)
            },
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

    if (showFolderSheet && pendingConfirmation == null) {
        WorkspaceFolderSheet(
            workingDir = appSettings.defaultWorkingDir,
            autoApprove = appSettings.autoApproveInWorkingDir,
            onWorkingDirChange = { viewModel.updateSettings(appSettings.copy(defaultWorkingDir = it)) },
            onAutoApproveChange = { viewModel.updateSettings(appSettings.copy(autoApproveInWorkingDir = it)) },
            onDismiss = { showFolderSheet = false }
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

    sourcesSheet?.let { sources ->
        WebSourcesSheet(
            sources = sources,
            onDismiss = { sourcesSheet = null },
            onOpenSource = { source ->
                sourcesSheet = null
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(source.url))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                } catch (_: Exception) {
                    Toast.makeText(context, "Не удалось открыть ссылку", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    changesSheet?.let { changes ->
        FileChangesSheet(
            changes = changes,
            onDismiss = { changesSheet = null }
        )
    }

    undoCandidate?.let { entry ->
        AlertDialog(
            onDismissRequest = { undoCandidate = null },
            title = {
                Text(
                    when {
                        entry.isMove -> "Отменить перемещение?"
                        entry.isOverwrite -> "Вернуть прежнюю версию?"
                        else -> "Вернуть из корзины?"
                    }
                )
            },
            text = {
                Text(
                    buildString {
                        append(
                            if (entry.items.size == 1) {
                                entry.items.first().name
                            } else {
                                PluralUtils.pluralize(
                                    entry.items.size, "объект", "объекта", "объектов"
                                )
                            }
                        )
                        append("\n\n")
                        when {
                            entry.isMove ->
                                append("Объекты вернутся туда, откуда их взяли. Если место занято, файл получит новое имя.")
                            entry.isOverwrite ->
                                append("Прежнее содержимое файла будет записано поверх текущего. Текущий текст будет потерян.")
                            else ->
                                append("Объекты вернутся на исходные места. Если место занято, файл получит новое имя.")
                        }
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val id = entry.id
                        undoCandidate = null
                        viewModel.restoreFromTrash(id)
                    }
                ) {
                    Text("Отменить")
                }
            },
            dismissButton = {
                TextButton(onClick = { undoCandidate = null }) {
                    Text("Оставить")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer, modifier = Modifier.bounceClick { onOpenDrawer() }) {
                        Icon(
                            imageVector = RunnerIcons.Menu,
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
                    // Индикатор режима: без него отклонённые операции выглядят
                    // как поломка агента, а не как включённая пользователем защита.
                    // Кнопка переключения Live-режима (автопилот по экрану)
                    LiveModeTopButton(
                        isActive = appSettings.liveModeEnabled,
                        onClick = {
                            if (appSettings.liveModeEnabled) {
                                viewModel.updateAppSettings { copy(liveModeEnabled = false) }
                                LiveAgentManager.reset()
                            } else {
                                val hasAccessibility = RunnerAccessibilityService.isEnabled(context)
                                val hasOverlay = android.provider.Settings.canDrawOverlays(context)
                                if (hasAccessibility && hasOverlay) {
                                    viewModel.updateAppSettings { copy(liveModeEnabled = true) }
                                } else {
                                    showLivePermissionSheet = true
                                }
                            }
                        }
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    if (appSettings.readOnlyMode) {
                        ReadOnlyBadge()
                    }
                    // Отмена последней обратимой операции. Операция уже была
                    // подтверждена один раз, поэтому кнопка одна и без лишних тапов —
                    // но диалог нужен: одна запись может вернуть десятки файлов.
                    lastUndoable?.let { entry ->
                        IconButton(
                            onClick = { undoCandidate = entry },
                            modifier = Modifier.bounceClick { undoCandidate = entry }
                        ) {
                            Icon(
                                imageVector = RunnerIcons.Undo,
                                contentDescription = "Отменить последнюю операцию",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
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
        Box(modifier = Modifier.fillMaxSize()) {
            if (appSettings.terminalAmbientEnabled && isEmptyChat && !appSettings.liveModeEnabled) {
                TerminalAmbientBackground(
                    enabled = true,
                    isLiveMode = false,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .navigationBarsPadding()
                    .imePadding()
            ) {
                AnimatedVisibility(
                    visible = appSettings.liveModeEnabled,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Live-режим: агент видит экран и выполняет жесты автономно",
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
                if (isEmptyChat) {
                    EmptyChatState(
                        settings = appSettings,
                        sessionId = currentSessionId,
                        onSuggestion = { inputState.setText(it) },
                        modifier = Modifier.weight(1f)
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
                        val lastUserMessageId = remember(messages) { findLastUserMessageId(messages) }
                        val lastAssistantMessageId = remember(messages) {
                            messages.lastOrNull { it.role == MessageRole.ASSISTANT && it.content.isNotBlank() }?.id
                        }
                        val canShowFollowUps = !isRunning && streamingText.isNullOrBlank() &&
                                pendingPlan == null && pendingConfirmation == null &&
                                appSettings.showFollowUpSuggestions
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
                                        is ChatListItem.TaskActivity -> "turn_${it.messages.first().id}"
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
                                            isLastUserMessage = item.message.id == lastUserMessageId,
                                            isLastAssistantMessage = (item.message.id == lastAssistantMessageId) && canShowFollowUps,
                                            onFollowUpClick = { prompt -> viewModel.sendMessage(prompt) },
                                            showToolDetails = appSettings.showToolDetails,
                                            showStats = appSettings.showStats,
                                            planAwaitingId = pendingPlan?.id,
                                            isSpeaking = isSpeaking,
                                            activeUtteranceId = activeUtteranceId,
                                            onToggleSpeak = { id, text -> viewModel.toggleSpeakMessage(id, text) },
                                            onApprovePlan = { viewModel.resolvePlan(true) },
                                            onRejectPlan = { viewModel.resolvePlan(false) },
                                            onEditMessage = { inputState.setText(it) },
                                            onRetryMessage = {
                                                viewModel.retryFromUserMessage(
                                                    it,
                                                    forceSearch = inputState.webSearch,
                                                    forceThinking = inputState.think
                                                )
                                            },
                                            onUndoTrash = { viewModel.restoreFromTrash(it) },
                                            onOpenPreview = { previewSource = it },
                                            onOpenSources = { sourcesSheet = it },
                                            onOpenChanges = { changesSheet = it },
                                            onAction = { action ->
                                                when (action) {
                                                    MessageAction.OPEN_SETTINGS -> onOpenSettings()
                                                    MessageAction.OPEN_MODELS -> showModelPicker = true
                                                    MessageAction.GRANT_STORAGE -> onOpenStorageSettings()
                                                    MessageAction.EXECUTE_DRY_RUN -> viewModel.confirmDryRun()
                                                }
                                            }
                                        )

                                        is ChatListItem.TaskActivity -> TaskActivityCard(
                                            messages = item.messages,
                                            showDetails = appSettings.showToolDetails,
                                            showStats = appSettings.showStats,
                                            planAwaitingId = pendingPlan?.id,
                                            isRunning = isRunning,
                                            isActiveTurn = isRunning && item === chatItems.lastOrNull(),
                                            onUndoTrash = { viewModel.restoreFromTrash(it) },
                                            onOpenPreview = { previewSource = it },
                                            onOpenSources = { sourcesSheet = it },
                                            onOpenChanges = { changesSheet = it },
                                            onApprovePlan = { viewModel.resolvePlan(true) },
                                            onRejectPlan = { viewModel.resolvePlan(false) }
                                        )
                                    }
                                }
                            }

                            if (streamingText.isNotBlank()) {
                                item(key = "streaming") {
                                    Box(
                                        modifier = Modifier.animateItemPlacement(
                                            animationSpec = MotionTokens.fluidSpring()
                                        )
                                    ) {
                                        StreamingBubble(text = streamingText)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            var pendingCrashReport by remember { mutableStateOf(CrashReportManager.hasPendingCrashReport(context)) }
            if (pendingCrashReport) {
                CrashReportBanner(
                    onCopy = {
                        val report = CrashReportManager.getPendingCrashReport(context)
                        if (report != null) {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            clipboard?.setPrimaryClip(ClipData.newPlainText("Отчет о сбое Runner", report))
                            Toast.makeText(context, "Отчет о сбое скопирован в буфер", Toast.LENGTH_SHORT).show()
                        }
                        CrashReportManager.clearPendingCrashReport(context)
                        pendingCrashReport = false
                    },
                    onDismiss = {
                        CrashReportManager.clearPendingCrashReport(context)
                        pendingCrashReport = false
                    }
                )
            }

            if (!hasStoragePermission) {
                PermissionBanner(onOpenSettings = onOpenStorageSettings)
            }

            AnimatedVisibility(
                visible = isRunning,
                enter = expandVertically(animationSpec = MotionTokens.fluidSpring()) + fadeIn(animationSpec = MotionTokens.fluidTween(MotionTokens.DurationFast)),
                exit = shrinkVertically(animationSpec = MotionTokens.fluidSpring()) + fadeOut(animationSpec = MotionTokens.fluidTween(MotionTokens.DurationFast))
            ) {
                Column {
                    AgentStatusIndicator(
                        status = currentStatus,
                        startedAt = if (liveState.isExecuting) null else streamStartedAt
                    )
                    if (streamingText.isBlank() && streamingReasoning.isNotBlank()) {
                        LiveReasoningBlock(reasoning = streamingReasoning)
                    }
                }
            }

            if (liveState.isExecuting) {
                LiveTaskStrip(
                    state = liveState,
                    startedAt = streamStartedAt,
                    onOpenActivity = {
                        if (chatItems.isNotEmpty()) {
                            scope.launch {
                                listState.animateScrollToItem(chatItems.lastIndex)
                            }
                        }
                    },
                    onStop = { viewModel.stopGeneration() }
                )
            }

            if (!appSettings.liveModeEnabled) {
                WorkspaceFolderBar(
                    workingDir = appSettings.defaultWorkingDir,
                    autoApprove = appSettings.autoApproveInWorkingDir,
                    onOpenFolderSheet = { showFolderSheet = true }
                )
            }

            ChatInputBar(
                inputState = inputState,
                isRunning = isRunning,
                isListening = isListening,
                rmsLevel = rmsLevel,
                sendOnEnter = appSettings.sendOnEnter,
                placeholder = if (appSettings.liveModeEnabled) "Опишите действие на экране..." else "Задать задачу или сказать голосом...",
                isLiveMode = appSettings.liveModeEnabled,
                onSend = { text, images ->
                    if (appSettings.liveModeEnabled) {
                        (context as? android.app.Activity)?.moveTaskToBack(true)
                    }
                    viewModel.sendMessage(
                        text,
                        images,
                        forceSearch = inputState.webSearch,
                        forceThinking = inputState.think
                    )
                },
                onStop = { viewModel.stopGeneration() },
                onStartListening = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        viewModel.startVoiceInput(
                            onPartialResult = { partial -> inputState.setText(partial) },
                            onFinalResult = { final -> inputState.setText(final) },
                            onError = { error -> Toast.makeText(context, error, Toast.LENGTH_SHORT).show() }
                        )
                    } else {
                        micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                onStopListening = { viewModel.stopVoiceInput() }
            )
        }
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
            imageVector = brandLogo ?: RunnerIcons.AutoAwesome,
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
                imageVector = RunnerIcons.KeyboardArrowDown,
                contentDescription = "Выбрать модель",
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}


/**
 * Главный экран пустого чата (Empty State):
 * Спокойная центрированная типографика в стиле Claude / ChatGPT с большим
 * количеством свободного пространства и горизонтальный скроллируемый ряд
 * лёгких капсул-подсказок (Pill Chips) над полем ввода.
 */
private val GREETING_TEMPLATES = listOf(
    "Чем заняться?",
    "Готов к работе",
    "Что делаем сегодня?",
    "Жду задачу",
    "Готов выполнять",
    "Какой план?",
    "В вашем распоряжении",
    "Система в строю",
    "На связи",
    "Слушаю вас",
    "Что оптимизируем?",
    "Разберёмся с файлами?",
    "Готов к запуску",
    "Терминал наготове",
    "Что запустим?"
)

private val LIVE_GREETING_TEMPLATES = listOf(
    "Что сделать на экране?",
    "Автопилот наготове",
    "Какое приложение открыть?",
    "Что найти или нажать?",
    "Жду задачу по экрану",
    "Готов к действиям в системе"
)

@Composable
private fun EmptyChatState(
    settings: AppSettings,
    sessionId: String?,
    onSuggestion: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isLive = settings.liveModeEnabled
    val greeting = remember(sessionId, isLive) {
        if (isLive) LIVE_GREETING_TEMPLATES.random() else GREETING_TEMPLATES.random()
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    var resumeTrigger by remember { mutableStateOf(0) }
    val activity = context as? androidx.activity.ComponentActivity
    DisposableEffect(activity) {
        if (activity == null) return@DisposableEffect onDispose {}
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_START) {
                resumeTrigger++
            }
        }
        activity.lifecycle.addObserver(observer)
        onDispose {
            activity.lifecycle.removeObserver(observer)
        }
    }

    val titleAlpha = remember { Animatable(0f) }
    val titleOffsetY = remember { Animatable(14f) }
    val titleScale = remember { Animatable(0.96f) }
    val greetingAlpha = remember { Animatable(0f) }
    val greetingOffsetY = remember { Animatable(10f) }

    LaunchedEffect(sessionId, resumeTrigger) {
        titleAlpha.snapTo(0f)
        titleOffsetY.snapTo(14f)
        titleScale.snapTo(0.96f)
        greetingAlpha.snapTo(0f)
        greetingOffsetY.snapTo(10f)
        launch {
            titleAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = 550,
                    easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
                )
            )
        }
        launch {
            titleOffsetY.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = 650,
                    easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
                )
            )
        }
        launch {
            titleScale.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = 650,
                    easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
                )
            )
        }
        launch {
            kotlinx.coroutines.delay(80L)
            greetingAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = 500,
                    easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
                )
            )
        }
        launch {
            kotlinx.coroutines.delay(80L)
            greetingOffsetY.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = 600,
                    easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
                )
            )
        }
    }

    val defaultSuggestions = listOf(
        EmptyStateSuggestion(
            icon = RunnerIcons.Delete,
            title = "Очистить кэш",
            prompt = "Найди мусор: кэш, пустые папки и временные файлы",
            isMint = true
        ),
        EmptyStateSuggestion(
            icon = RunnerIcons.VideoLibrary,
            title = "Тяжёлые файлы",
            prompt = "Найди файлы тяжелее 50 МБ: видео, архивы и загрузки",
            isMint = false
        ),
        EmptyStateSuggestion(
            icon = RunnerIcons.FolderOpen,
            title = "Разобрать Download",
            prompt = "Разбери папку Download: архивы и свежие файлы",
            isMint = true
        ),
        EmptyStateSuggestion(
            icon = RunnerIcons.ContentPaste,
            title = "Буфер обмена",
            prompt = "Прочитай буфер обмена",
            isMint = false
        )
    )

    val liveSuggestions = listOf(
        EmptyStateSuggestion(
            icon = RunnerIcons.Visibility,
            title = "Что на экране?",
            prompt = "Посмотри на экран и подробно опиши, что сейчас открыто",
            isMint = true
        ),
        EmptyStateSuggestion(
            icon = RunnerIcons.AutoAwesome,
            title = "Настройки системы",
            prompt = "Открой настройки телефона и проверь статус сети",
            isMint = false
        ),
        EmptyStateSuggestion(
            icon = RunnerIcons.Psychology,
            title = "Поиск в Telegram",
            prompt = "Открой Telegram и найди последний чат",
            isMint = true
        ),
        EmptyStateSuggestion(
            icon = RunnerIcons.Info,
            title = "Проверить заряд и сеть",
            prompt = "Проверь уровень заряда батареи и статус Wi-Fi",
            isMint = false
        ),
        EmptyStateSuggestion(
            icon = RunnerIcons.AutoAwesome,
            title = "Погода в браузере",
            prompt = "Открой браузер и найди прогноз погоды на сегодня",
            isMint = true
        )
    )

    val allSuggestions: List<EmptyStateSuggestion> = if (isLive) {
        liveSuggestions
    } else {
        val custom: List<EmptyStateSuggestion> = settings.customPromptSuggestions.mapIndexed { idx, promptText ->
            EmptyStateSuggestion(
                icon = RunnerIcons.AutoAwesome,
                title = promptText.take(24),
                prompt = promptText,
                isMint = (idx % 2 == 1)
            )
        }
        defaultSuggestions + custom
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.weight(1f))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isLive) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.graphicsLayer {
                        alpha = titleAlpha.value
                        translationY = titleOffsetY.value * density
                        scaleX = titleScale.value
                        scaleY = titleScale.value
                    }
                ) {
                    Text(
                        text = "RUNNER",
                        fontFamily = GoogleSans,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 6.sp,
                        style = TextStyle(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.92f),
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                                )
                            ),
                            textAlign = TextAlign.Center
                        )
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                        border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "LIVE",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }
            } else {
                Text(
                    text = "RUNNER",
                    fontFamily = GoogleSansDisplay,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 6.sp,
                    modifier = Modifier.graphicsLayer {
                        alpha = titleAlpha.value
                        translationY = titleOffsetY.value * density
                        scaleX = titleScale.value
                        scaleY = titleScale.value
                    },
                    style = TextStyle(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.92f),
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                            )
                        ),
                        textAlign = TextAlign.Center
                    )
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = greetingAlpha.value
                        translationY = greetingOffsetY.value * density
                    },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = greeting,
                    fontFamily = GoogleSansDisplay,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.4).sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (isLive) {
                        "Агент видит экран и автономно выполняет действия перед вашими глазами"
                    } else {
                        "Спросите о файлах, памяти или выберите сценарий"
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        if (settings.showPromptSuggestions && allSuggestions.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(allSuggestions, key = { it.title + "_" + it.prompt }) { suggestion ->
                    EmptyStateChip(
                        suggestion = suggestion,
                        onClick = { onSuggestion(suggestion.prompt) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

private data class EmptyStateSuggestion(
    val icon: ImageVector,
    val title: String,
    val prompt: String,
    val isMint: Boolean = false
)

/**
 * Лёгкая плавающая пилюля-подсказка (Pill Chip) со скруглением 20dp,
 * деликатным bounceClick и тонким фирменным акцентом Ice Blue / Mint.
 */
@Composable
private fun EmptyStateChip(
    suggestion: EmptyStateSuggestion,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val accent = if (suggestion.isMint) {
        SecondaryMint
    } else {
        MaterialTheme.colorScheme.primary
    }

    val handleClick = {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onClick()
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .bounceClick(scaleDown = 0.96f, onClick = handleClick)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                onClick {
                    handleClick()
                    true
                }
            }
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(
                BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                ),
                RoundedCornerShape(20.dp)
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = suggestion.icon,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(15.dp)
        )
        Spacer(modifier = Modifier.width(7.dp))
        Text(
            text = suggestion.title,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
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
    val haptic = LocalHapticFeedback.current
    val handleClick = {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onClick()
    }
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .bounceClick(scaleDown = 0.88f, onClick = handleClick)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                onClick {
                    handleClick()
                    true
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
            modifier = Modifier.size(17.dp)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageItem(
    message: ChatMessage,
    @Suppress("UNUSED_PARAMETER") isLastUserMessage: Boolean = false,
    isLastAssistantMessage: Boolean = false,
    onFollowUpClick: (String) -> Unit = {},
    showToolDetails: Boolean,
    showStats: Boolean,
    planAwaitingId: String?,
    isSpeaking: Boolean = false,
    activeUtteranceId: String? = null,
    onToggleSpeak: (String, String) -> Unit = { _, _ -> },
    onApprovePlan: () -> Unit,
    onRejectPlan: () -> Unit,
    onEditMessage: (String) -> Unit,
    onRetryMessage: (String) -> Unit,
    onUndoTrash: (String) -> Unit,
    onOpenPreview: (WebPreviewSource) -> Unit,
    onOpenSources: (List<WebSource>) -> Unit,
    onOpenChanges: (List<FileChange>) -> Unit,
    onAction: (MessageAction) -> Unit
) {
    when (message.role) {
        MessageRole.USER -> {
            val context = LocalContext.current
            val haptic = LocalHapticFeedback.current
            var showContextMenu by remember { mutableStateOf(false) }

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.End
            ) {
                val bubbleShape = RoundedCornerShape(
                    topStart = 18.dp,
                    topEnd = 18.dp,
                    bottomStart = 18.dp,
                    bottomEnd = 4.dp
                )
                val maxBubbleWidth = (LocalConfiguration.current.screenWidthDp * 0.82f).dp

                Box(
                    modifier = Modifier.widthIn(max = maxBubbleWidth)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(bubbleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .combinedClickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    showContextMenu = true
                                },
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    showContextMenu = true
                                },
                                onLongClickLabel = "Действия с сообщением"
                            )
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Column {
                            if (message.imageUris.isNotEmpty()) {
                                MessageImageGrid(message.imageUris)
                                if (message.content.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                }
                            }
                            if (message.content.isNotBlank()) {
                                Text(
                                    text = message.content,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }

                    DropdownMenu(
                        expanded = showContextMenu,
                        onDismissRequest = { showContextMenu = false },
                        modifier = Modifier.clip(RoundedCornerShape(18.dp))
                    ) {
                        if (message.content.isNotBlank()) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "Копировать",
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 14.sp
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = RunnerIcons.ContentCopy,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                onClick = {
                                    showContextMenu = false
                                    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    manager.setPrimaryClip(ClipData.newPlainText("Сообщение", message.content))
                                    Toast.makeText(context, "Скопировано", Toast.LENGTH_SHORT).show()
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "Изменить",
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 14.sp
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = RunnerIcons.Edit,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                onClick = {
                                    showContextMenu = false
                                    onEditMessage(message.content)
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "Заново",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 14.sp
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = RunnerIcons.Refresh,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            onClick = {
                                showContextMenu = false
                                onRetryMessage(message.id)
                            }
                        )
                    }
                }
            }
        }

        MessageRole.ASSISTANT -> {
            val context = LocalContext.current
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.96f)
                    .padding(vertical = 2.dp)
            ) {
                // Если модель вернула размышления, показываем их даже без
                // включённой статистики: режим «Думать» должен быть виден.
                if (showStats || !message.reasoningText.isNullOrBlank()) {
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

                val hasContent = message.content.isNotBlank()
                val hasStats = showStats && (message.durationMs != null || message.tokensPerSecond != null || message.promptTokens != null || message.completionTokens != null)

                if (hasContent || hasStats) {
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(
                        modifier = Modifier.padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (hasContent) {
                            val isCurrentSpeaking = isSpeaking && activeUtteranceId == message.id
                            @Suppress("DEPRECATION")
                            val volumeIcon = RunnerIcons.VolumeUp
                            MessageActionIcon(
                                icon = if (isCurrentSpeaking) RunnerIcons.Stop else volumeIcon,
                                label = if (isCurrentSpeaking) "Остановить" else "Озвучить",
                                onClick = { onToggleSpeak(message.id, message.content) }
                            )
                            MessageActionIcon(
                                icon = RunnerIcons.ContentCopy,
                                label = "Копировать",
                                onClick = {
                                    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    manager.setPrimaryClip(ClipData.newPlainText("Ответ", message.content))
                                    Toast.makeText(context, "Скопировано", Toast.LENGTH_SHORT).show()
                                }
                            )
                            if (message.isError) {
                                MessageActionIcon(
                                    icon = RunnerIcons.BugReport,
                                    label = "Отчет о сбое",
                                    onClick = { CrashReportManager.copyReport(context, "Ответ ассистента с ошибкой: ${message.content}") }
                                )
                            }
                        }

                        if (hasStats) {
                            ResponseStats(
                                message = message,
                                modifier = Modifier.padding(
                                    start = if (hasContent) 2.dp else 0.dp,
                                    top = if (hasContent) 6.dp else 0.dp
                                )
                            )
                        }
                    }
                }

                if (isLastAssistantMessage && hasContent) {
                    val followUps = remember(message.content) {
                        SmartFollowUpGenerator.generate(message.content)
                    }
                    if (followUps.isNotEmpty()) {
                        SmartFollowUpRow(
                            suggestions = followUps,
                            onSelect = onFollowUpClick
                        )
                    }
                }
            }
        }

        MessageRole.TOOL_EXECUTION -> ToolCard(
            message = message,
            showDetails = showToolDetails,
            showStats = showStats,
            onUndoTrash = onUndoTrash,
            onOpenPreview = onOpenPreview,
            onOpenSources = onOpenSources
        )

        MessageRole.NARRATION -> NarrationRow(text = message.content)

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

@Composable
private fun MessageImageGrid(imagePaths: List<String>) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(imagePaths) { path ->
            MessageImageItem(path = path)
        }
    }
}

@Composable
private fun MessageImageItem(path: String) {
    val bitmapState = produceState<ImageBitmap?>(initialValue = null, key1 = path) {
        value = withContext(Dispatchers.IO) {
            try {
                val file = File(path)
                if (!file.exists()) return@withContext null
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, options)
                var sampleSize = 1
                while (options.outWidth / sampleSize > 400 || options.outHeight / sampleSize > 400) {
                    sampleSize *= 2
                }
                options.inJustDecodeBounds = false
                options.inSampleSize = sampleSize
                BitmapFactory.decodeFile(file.absolutePath, options)?.asImageBitmap()
            } catch (e: Exception) {
                null
            }
        }
    }

    val bitmap = bitmapState.value
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = "Прикрепленное фото",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(100.dp)
                .clip(RoundedCornerShape(8.dp))
        )
    } else {
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        }
    }
}

/** Плашка под ответом: сколько занял весь пайплайн, токены и скорость генерации. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResponseStats(
    message: ChatMessage,
    modifier: Modifier = Modifier
) {
    val parts = buildList {
        message.durationMs?.let { add("⏱ ${formatDuration(it)}") }
        // Провайдеры иногда отдают usage, который не сходится с реальным
        // ответом: 17k completion-токенов на пару строк и 9000 т/с.
        // Такие цифры не показываем, иначе статистика выглядит как бред.
        val prompt = message.promptTokens?.takeIf { it in 1..2_000_000 }
        val completion = message.completionTokens?.takeIf { tokens ->
            tokens in 1..100_000 && tokens <= maxOf(512, message.content.length)
        }
        if (prompt != null && completion != null) {
            add("$prompt→$completion ток")
        }
        message.tokensPerSecond
            ?.takeIf { it in 0.1..300.0 }
            ?.let { add("%.0f т/с".format(it)) }
    }

    if (parts.isEmpty()) return

    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        parts.forEach { part ->
            Text(
                text = part,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.8f),
                fontSize = 10.5.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

/**
 * Закреплённая строка Live-задачи над полем ввода: шаг, время, «Лента», «Стоп».
 * Плавающая карточка остаётся для паузы и работы поверх других приложений.
 */
@Composable
private fun LiveTaskStrip(
    state: LiveAgentManager.State,
    startedAt: Long?,
    onOpenActivity: () -> Unit,
    onStop: () -> Unit
) {
    var elapsedSeconds by remember(state.isExecuting) { mutableIntStateOf(0) }
    LaunchedEffect(startedAt, state.isExecuting) {
        if (startedAt == null) {
            elapsedSeconds = 0
            return@LaunchedEffect
        }
        while (true) {
            elapsedSeconds = ((System.currentTimeMillis() - startedAt) / 1000)
                .toInt()
                .coerceAtLeast(0)
            kotlinx.coroutines.delay(1000)
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            0.7.dp,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusPulsingDot(dotSize = 5.dp)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Live",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = " · действие ${state.stepIndex.coerceAtLeast(1)}",
                color = MaterialTheme.colorScheme.outline,
                fontSize = 11.5.sp,
                maxLines = 1
            )
            if (state.isPaused) {
                Text(
                    text = " · пауза",
                    color = StatusWarning,
                    fontSize = 11.5.sp,
                    maxLines = 1
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = formatTimelineElapsed(elapsedSeconds * 1000L),
                color = MaterialTheme.colorScheme.outline,
                fontSize = 10.5.sp,
                fontFamily = FontFamily.Monospace
            )
            TextButton(
                onClick = onOpenActivity,
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text("Лента", fontSize = 11.5.sp)
            }
            Button(
                onClick = onStop,
                shape = RoundedCornerShape(9.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                modifier = Modifier.height(30.dp)
            ) {
                Text(
                    text = "Стоп",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/**
 * Живой хвост размышлений, пока модель думает.
 *
 * Не раскрывается и не копит историю: это индикатор прогресса, а не архив.
 * Показываем последние строки, чтобы было видно, что работа идёт.
 */
@Composable
private fun LiveReasoningBlock(reasoning: String) {
    val tail = remember(reasoning) { reasoning.takeLast(700).trim() }
    if (tail.isEmpty()) return
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 2.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = RunnerIcons.Psychology,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Размышления",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = tail,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                fontSize = 11.5.sp,
                lineHeight = 16.sp,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis
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
                imageVector = RunnerIcons.Psychology,
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
                imageVector = RunnerIcons.KeyboardArrowDown,
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
            MarkdownView(text = text, isStreaming = true)
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
    val haptic = LocalHapticFeedback.current
    val steps = remember(message.content) { parsePlanStepsUi(message.content) }
    val successCriteria = remember(message.content) {
        message.content.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.contains("Критерий успеха", ignoreCase = true) }
            ?.substringAfter(':', "")
            ?.trim()
            .orEmpty()
    }

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
                    imageVector = RunnerIcons.AutoAwesome,
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
                    imageVector = RunnerIcons.KeyboardArrowDown,
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

                if (successCriteria.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Критерий успеха: $successCriteria",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                }
            }

            if (awaitingDecision) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onApprove()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Утвердить", fontSize = 13.sp)
                    }
                    OutlinedButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onReject()
                        },
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
 * Элемент ленты чата: одиночное сообщение либо лента хода задачи
 * (план, реплики модели и вызовы инструментов).
 */
internal sealed interface ChatListItem {
    data class Single(val message: ChatMessage) : ChatListItem
    data class TaskActivity(val messages: List<ChatMessage>) : ChatListItem
}

/**
 * Находит ID последнего сообщения пользователя в диалоге,
 * чтобы показывать быстрые действия только для него.
 */
internal fun findLastUserMessageId(messages: List<ChatMessage>): String? =
    messages.findLast { it.role == MessageRole.USER }?.id

/**
 * Собирает ленту хода задачи: план, реплики модели и вызовы инструментов
 * идут одной карточкой, пока их не разделит ответ ассистента, системная
 * плашка или новое сообщение пользователя.
 *
 * Раньше группировались только завершённые тулы, и ход рассыпался на
 * «Выполнено 2 действия», «Выполнено 3 действия» с независимыми счётчиками.
 */
internal fun groupChatItems(messages: List<ChatMessage>): List<ChatListItem> {
    val out = mutableListOf<ChatListItem>()
    var run = mutableListOf<ChatMessage>()
    fun flush() {
        if (run.isNotEmpty()) out += ChatListItem.TaskActivity(run.toList())
        run = mutableListOf()
    }
    for (m in messages) {
        when (m.role) {
            MessageRole.PLAN,
            MessageRole.NARRATION,
            MessageRole.TOOL_EXECUTION -> run += m
            else -> {
                flush()
                out += ChatListItem.Single(m)
            }
        }
    }
    flush()
    return out
}

/** Подпись кнопки отмены: вид операции виден прямо на кнопке. */
internal fun undoLabel(ref: TrashStore.UndoRef): String = when (ref.kind) {
    TrashStore.EntryKind.MOVE -> "Отменить перемещение"
    TrashStore.EntryKind.OVERWRITE -> "Отменить перезапись"
    TrashStore.EntryKind.DELETE -> "Отменить удаление"
}

/**
 * Карточка одного хода задачи: реплики модели, план и действия идут
 * непрерывной лентой. Заголовок показывает общее число действий и время,
 * поэтому счётчики больше не скачут от группы к группе.
 */
@Composable
private fun TaskActivityCard(
    messages: List<ChatMessage>,
    showDetails: Boolean,
    showStats: Boolean,
    planAwaitingId: String?,
    isRunning: Boolean,
    isActiveTurn: Boolean,
    onUndoTrash: (String) -> Unit,
    onOpenPreview: (WebPreviewSource) -> Unit,
    onOpenSources: (List<WebSource>) -> Unit,
    onOpenChanges: (List<FileChange>) -> Unit,
    onApprovePlan: () -> Unit,
    onRejectPlan: () -> Unit
) {
    val toolMessages = remember(messages) { messages.filter { it.role == MessageRole.TOOL_EXECUTION } }
    val allChanges = remember(messages) { messages.flatMap { it.fileChanges } }
    val distinctSources = remember(toolMessages) {
        toolMessages.flatMap { it.sources }.distinctBy { it.url }.size
    }
    val failedSteps = remember(toolMessages) {
        toolMessages.filter { it.isError || it.isDeclined }
    }
    val actionCount = toolMessages.size
    val hasError = toolMessages.any { it.isError }
    val hasDeclined = toolMessages.any { it.isDeclined }
    val anyRunning = toolMessages.any { it.isRunning }
    val planAwaiting = messages.any { it.role == MessageRole.PLAN && it.id == planAwaitingId }
    val showTotals = actionCount > 0 || allChanges.isNotEmpty() ||
            failedSteps.isNotEmpty() || isActiveTurn
    var expanded by remember(messages.first().id) { mutableStateOf(anyRunning || planAwaiting) }
    LaunchedEffect(anyRunning) {
        if (anyRunning) expanded = true
    }
    LaunchedEffect(planAwaiting) {
        if (planAwaiting) expanded = true
    }

    val startedAt = remember(messages) { messages.firstOrNull()?.timestamp ?: 0L }
    var elapsedMs by remember(messages.first().id) {
        mutableStateOf(currentActivityElapsed(messages, startedAt, anyRunning && isRunning))
    }
    LaunchedEffect(messages, anyRunning, isRunning) {
        if (anyRunning && isRunning) {
            while (true) {
                elapsedMs = (System.currentTimeMillis() - startedAt).coerceAtLeast(0)
                kotlinx.coroutines.delay(1000)
            }
        } else {
            elapsedMs = currentActivityElapsed(messages, startedAt, false)
        }
    }

    val shape = RoundedCornerShape(12.dp)
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.55f),
        shape = shape,
        border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    // Пока план ждёт решения, прятать его нельзя: кнопки
                    // утверждения находятся внутри.
                    .clickable(enabled = !planAwaiting) { expanded = !expanded }
                    .padding(horizontal = 2.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                hasError -> MaterialTheme.colorScheme.error
                                planAwaiting -> StatusWarning
                                hasDeclined -> StatusWarning
                                anyRunning -> MaterialTheme.colorScheme.primary
                                else -> StatusSuccess
                            }
                        )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Ход задачи",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium
                )
                if (actionCount > 0) {
                    Text(
                        text = " · ${PluralUtils.actions(actionCount)}",
                        color = MaterialTheme.colorScheme.outline,
                        fontSize = 11.5.sp,
                        maxLines = 1
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = formatTimelineElapsed(elapsedMs),
                    color = MaterialTheme.colorScheme.outline,
                    fontSize = 10.5.sp,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = RunnerIcons.KeyboardArrowDown,
                    contentDescription = if (expanded) "Свернуть" else "Развернуть",
                    tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
                    modifier = Modifier
                        .size(14.dp)
                        .graphicsLayer { rotationZ = if (expanded) 180f else 0f }
                )
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(4.dp))
                messages.forEach { message ->
                    when (message.role) {
                        MessageRole.PLAN -> PlanCard(
                            message = message,
                            awaitingDecision = message.id == planAwaitingId,
                            onApprove = onApprovePlan,
                            onReject = onRejectPlan
                        )

                        MessageRole.NARRATION -> NarrationRow(text = message.content)

                        MessageRole.TOOL_EXECUTION -> TimelineStepRow(
                            message = message,
                            showDetails = showDetails,
                            showStats = showStats,
                            onUndoTrash = onUndoTrash,
                            onOpenPreview = onOpenPreview,
                            onOpenSources = onOpenSources,
                            onOpenChanges = onOpenChanges
                        )

                        else -> Unit
                    }
                }
            } else if (toolMessages.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                TimelineStepRow(
                    message = toolMessages.last(),
                    showDetails = showDetails,
                    showStats = showStats,
                    onUndoTrash = onUndoTrash,
                    onOpenPreview = onOpenPreview,
                    onOpenSources = onOpenSources,
                    onOpenChanges = onOpenChanges
                )
            }

            if (showTotals) {
                Spacer(modifier = Modifier.height(6.dp))
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                    thickness = 0.5.dp
                )
                Spacer(modifier = Modifier.height(4.dp))
                TaskTotalsRow(
                    changes = allChanges,
                    isActiveTurn = isActiveTurn,
                    actionCount = actionCount,
                    sourcesCount = distinctSources,
                    failedSteps = failedSteps,
                    onOpenChanges = { onOpenChanges(allChanges) }
                )
            }
        }
    }
}

/** Время хода: от первого сообщения до последнего завершённого действия. */
private fun currentActivityElapsed(
    messages: List<ChatMessage>,
    startedAt: Long,
    running: Boolean
): Long {
    if (startedAt <= 0L) return 0L
    val end = if (running) {
        System.currentTimeMillis()
    } else {
        val last = messages.lastOrNull() ?: return 0L
        last.timestamp + (last.toolDurationMs ?: 0L)
    }
    return (end - startedAt).coerceAtLeast(0L)
}

private fun formatTimelineElapsed(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

/** Короткая реплика модели внутри хода задачи. */
@Composable
private fun NarrationRow(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 2.dp, end = 4.dp, top = 6.dp, bottom = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = RunnerIcons.ChatBubble,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
            modifier = Modifier
                .padding(top = 1.dp)
                .size(13.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.5.sp,
            lineHeight = 17.sp,
            fontStyle = FontStyle.Italic,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Один шаг хода: действие, результат и раскрываемые детали. */
@Composable
private fun TimelineStepRow(
    message: ChatMessage,
    showDetails: Boolean,
    showStats: Boolean,
    onUndoTrash: (String) -> Unit,
    onOpenPreview: (WebPreviewSource) -> Unit,
    onOpenSources: (List<WebSource>) -> Unit,
    onOpenChanges: (List<FileChange>) -> Unit
) {
    val context = LocalContext.current
    var detailsExpanded by remember(message.id) { mutableStateOf(false) }
    val hasDetails = showDetails && (
            !message.toolArgs.isNullOrBlank() || !message.toolOutput.isNullOrBlank()
            )
    val undoRef = remember(message.toolOutput) {
        TrashStore.undoRefFromOutput(message.toolOutput)
    }
    val preview = remember(message.toolArgs, message.toolOutput) {
        findWebPreviewInToolCall(
            toolName = message.toolName.orEmpty(),
            args = message.toolArgs,
            output = message.toolOutput
        )
    }
    val shape = RoundedCornerShape(8.dp)
    val runningBackground = if (message.isRunning) {
        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f)
    } else {
        Color.Transparent
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(runningBackground)
            .padding(horizontal = 4.dp, vertical = 3.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .clickable(enabled = hasDetails && !message.isRunning) {
                    detailsExpanded = !detailsExpanded
                }
                .padding(vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ToolStatusIcon(message)
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = message.content,
                    color = if (message.isDeclined) {
                        MaterialTheme.colorScheme.outline
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontSize = 12.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val hasChanges = message.fileChanges.isNotEmpty()
                val detailLine = buildString {
                    if (!hasChanges && !message.toolSummary.isNullOrBlank()) append(message.toolSummary)
                    if (showStats && !message.isRunning && message.toolDurationMs != null) {
                        if (isNotEmpty()) append(" · ")
                        append(formatDuration(message.toolDurationMs))
                    }
                    if (message.isRunning) {
                        if (isNotEmpty()) append(" · ")
                        append("выполняется…")
                    }
                }
                if (detailLine.isNotBlank()) {
                    Text(
                        text = detailLine,
                        color = MaterialTheme.colorScheme.outline,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (hasDetails && !message.isRunning) {
                Icon(
                    imageVector = RunnerIcons.KeyboardArrowDown,
                    contentDescription = if (detailsExpanded) "Свернуть" else "Детали",
                    tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
                    modifier = Modifier
                        .size(14.dp)
                        .graphicsLayer { rotationZ = if (detailsExpanded) 180f else 0f }
                )
            }
        }

        if (message.fileChanges.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            message.fileChanges.forEach { change ->
                ChangeLine(
                    change = change,
                    onClick = if (change.diff.isNullOrBlank()) {
                        null
                    } else {
                        { onOpenChanges(message.fileChanges) }
                    }
                )
            }
        }

        if (message.sources.isNotEmpty()) {
            Spacer(modifier = Modifier.height(5.dp))
            WebSourcesRow(
                sources = message.sources,
                onClick = { onOpenSources(message.sources) }
            )
        }

        if (undoRef != null) {
            Spacer(modifier = Modifier.height(5.dp))
            UndoTrashButton(
                label = undoLabel(undoRef),
                onClick = { onUndoTrash(undoRef.entryId) }
            )
        }

        if (preview != null) {
            Spacer(modifier = Modifier.height(5.dp))
            PreviewButton(
                label = "Открыть превью ${preview.title}",
                onClick = { onOpenPreview(preview) }
            )
        }

        if (detailsExpanded && hasDetails) {
            Spacer(modifier = Modifier.height(7.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(7.dp))
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
                Spacer(modifier = Modifier.height(8.dp))
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
                            imageVector = RunnerIcons.ContentCopy,
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

/**
 * Свёрнутая строка вызова инструмента: «Сканирую Download · 143 файла».
 * Технические детали — только по нажатию.
 */
@Composable
private fun ToolCard(
    message: ChatMessage,
    showDetails: Boolean,
    showStats: Boolean,
    onUndoTrash: (String) -> Unit,
    onOpenPreview: (WebPreviewSource) -> Unit,
    onOpenSources: (List<WebSource>) -> Unit
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
                        imageVector = RunnerIcons.KeyboardArrowDown,
                        contentDescription = if (expanded) "Свернуть" else "Развернуть",
                        tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
                        modifier = Modifier
                            .size(14.dp)
                            .graphicsLayer { rotationZ = rotation }
                    )
                }
            }

            // Возврат из корзины виден прямо в свёрнутой карточке. Прятать
            // исправление ошибки за лишний тап — ровно то, чего не хватало
            // безвозвратному удалению.
            val undoRef = remember(message.toolOutput) {
                TrashStore.undoRefFromOutput(message.toolOutput)
            }
            if (undoRef != null) {
                Spacer(modifier = Modifier.height(7.dp))
                UndoTrashButton(
                    label = undoLabel(undoRef),
                    onClick = { onUndoTrash(undoRef.entryId) }
                )
            }

            // Источники веб-поиска: строка на виду, без раскрытия карточки.
            if (message.sources.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                WebSourcesRow(
                    sources = message.sources,
                    onClick = { onOpenSources(message.sources) }
                )
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
                                imageVector = RunnerIcons.ContentCopy,
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

/** Компактная строка изменения под шагом: «Создан script.py · +42 · 2.1 KB». */
@Composable
private fun ChangeLine(
    change: FileChange,
    onClick: (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 2.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = changeIcon(change.kind),
            contentDescription = null,
            tint = changeColor(change.kind),
            modifier = Modifier.size(13.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = changeLabel(change),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (onClick != null) {
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Дифф",
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/** Итог хода: «Итог · 7 действий · 16 источников», файлы и замечания. */
@Composable
private fun TaskTotalsRow(
    changes: List<FileChange>,
    isActiveTurn: Boolean,
    actionCount: Int,
    sourcesCount: Int,
    failedSteps: List<ChatMessage>,
    onOpenChanges: () -> Unit
) {
    val totals = remember(changes) { changeTotals(changes) }
    val hasFailed = failedSteps.isNotEmpty()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isActiveTurn) {
                StatusPulsingDot(dotSize = 5.dp)
            } else {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(if (hasFailed) StatusWarning else StatusSuccess)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = when {
                        isActiveTurn -> "В работе"
                        hasFailed -> "Итог с замечаниями"
                        else -> "Итог"
                    },
                    color = if (!isActiveTurn && hasFailed) {
                        StatusWarning
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium
                )
                val meta = buildList {
                    if (actionCount > 0) add(PluralUtils.actions(actionCount))
                    if (sourcesCount > 0) add(PluralUtils.sources(sourcesCount))
                }
                if (meta.isNotEmpty()) {
                    Text(
                        text = " · ${meta.joinToString(" · ")}",
                        color = MaterialTheme.colorScheme.outline,
                        fontSize = 11.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
            }
            if (changes.any { !it.diff.isNullOrBlank() }) {
                TextButton(
                    onClick = onOpenChanges,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "Дифф",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 11.5.sp
                    )
                }
            }
        }

        if (changes.isNotEmpty()) {
            Spacer(modifier = Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    imageVector = RunnerIcons.Edit,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(top = 1.dp)
                        .size(13.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Файлы: ${totals.body}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.5.sp,
                    lineHeight = 15.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (hasFailed) {
            Spacer(modifier = Modifier.height(3.dp))
            val errors = failedSteps.filter { it.isError }
            val declined = failedSteps.filter { it.isDeclined }
            val label = when {
                errors.isNotEmpty() && declined.isNotEmpty() -> "Замечания"
                errors.isNotEmpty() -> "Ошибки"
                else -> "Отклонено"
            }
            val names = failedSteps.take(3).joinToString("; ") { it.content }
            val suffix = if (failedSteps.size > 3) " и ещё ${failedSteps.size - 3}" else ""
            Text(
                text = "$label: $names$suffix",
                color = StatusWarning,
                fontSize = 11.5.sp,
                lineHeight = 15.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private data class ChangeTotals(
    val created: Int,
    val modified: Int,
    val deleted: Int,
    val moved: Int,
    val copied: Int,
    val added: Int,
    val removed: Int
) {
    val body: String
        get() = buildString {
            val parts = buildList {
                if (created > 0) add("создано $created")
                if (modified > 0) add("изменено $modified")
                if (deleted > 0) add("удалено $deleted")
                if (moved > 0) add("перемещено $moved")
                if (copied > 0) add("скопировано $copied")
            }
            if (parts.isEmpty()) append("без изменений") else append(parts.joinToString(" · "))
            if (added > 0 || removed > 0) {
                append(" · ")
                if (added > 0) append("+").append(added)
                if (added > 0 && removed > 0) append(" ")
                if (removed > 0) append("−").append(removed)
            }
        }
}

private fun changeTotals(changes: List<FileChange>): ChangeTotals {
    var created = 0
    var modified = 0
    var deleted = 0
    var moved = 0
    var copied = 0
    var added = 0
    var removed = 0
    changes.forEach { change ->
        when (change.kind) {
            FileChangeKind.CREATED, FileChangeKind.DIR_CREATED -> created += change.itemCount
            FileChangeKind.MODIFIED -> modified += change.itemCount
            FileChangeKind.DELETED -> deleted += change.itemCount
            FileChangeKind.MOVED -> moved += change.itemCount
            FileChangeKind.COPIED -> copied += change.itemCount
        }
        added += change.linesAdded
        removed += change.linesRemoved
    }
    return ChangeTotals(created, modified, deleted, moved, copied, added, removed)
}

@Composable
private fun changeIcon(kind: FileChangeKind): ImageVector = when (kind) {
    FileChangeKind.CREATED -> RunnerIcons.Add
    FileChangeKind.MODIFIED -> RunnerIcons.Edit
    FileChangeKind.DELETED -> RunnerIcons.Delete
    FileChangeKind.MOVED -> RunnerIcons.FolderOpen
    FileChangeKind.COPIED -> RunnerIcons.ContentCopy
    FileChangeKind.DIR_CREATED -> RunnerIcons.Folder
}

@Composable
private fun changeColor(kind: FileChangeKind): Color = when (kind) {
    FileChangeKind.CREATED, FileChangeKind.DIR_CREATED -> StatusSuccess
    FileChangeKind.MODIFIED -> MaterialTheme.colorScheme.primary
    FileChangeKind.DELETED -> StatusWarning
    FileChangeKind.MOVED, FileChangeKind.COPIED -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun changeLabel(change: FileChange): String {
    val name = change.displayPath
    val base = when (change.kind) {
        FileChangeKind.CREATED -> if (change.itemCount == 1) {
            "Создан $name"
        } else {
            "Создано ${PluralUtils.files(change.itemCount)}"
        }
        FileChangeKind.MODIFIED -> if (change.itemCount == 1) {
            "Изменён $name"
        } else {
            "Изменено ${PluralUtils.files(change.itemCount)}"
        }
        FileChangeKind.DELETED -> if (change.itemCount == 1) {
            "Удалён $name"
        } else {
            "Удалено ${PluralUtils.files(change.itemCount)}"
        }
        FileChangeKind.MOVED -> if (change.itemCount == 1) {
            "Перемещён $name"
        } else {
            "Перемещено ${PluralUtils.files(change.itemCount)}"
        }
        FileChangeKind.COPIED -> "Скопирован $name"
        FileChangeKind.DIR_CREATED -> "Создана папка $name"
    }
    val numbers = buildList {
        if (change.linesAdded > 0) add("+${change.linesAdded}")
        if (change.linesRemoved > 0) add("−${change.linesRemoved}")
    }
    val size = change.bytes
        ?.takeIf { it > 0 }
        ?.let { ToolDispatcher.formatFileSize(it) }
    return buildString {
        append(base)
        if (numbers.isNotEmpty()) {
            append(" · ")
            append(numbers.joinToString(" "))
        }
        if (size != null) {
            append(" · ")
            append(size)
        }
    }
}

/** Шторка изменений: по карточке на файл, внутри — построчный diff. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FileChangesSheet(
    changes: List<FileChange>,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val totalFiles = changes.sumOf { it.itemCount }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
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
                .padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Изменения",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = PluralUtils.files(totalFiles),
                    color = MaterialTheme.colorScheme.outline,
                    fontSize = 11.5.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Красным — удалённые строки, зелёным — добавленные.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(10.dp))
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 20.dp)
            ) {
                items(changes) { change ->
                    FileChangeCard(change = change)
                }
            }
        }
    }
}

@Composable
private fun FileChangeCard(change: FileChange) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(
            0.7.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    imageVector = changeIcon(change.kind),
                    contentDescription = null,
                    tint = changeColor(change.kind),
                    modifier = Modifier
                        .padding(top = 1.dp)
                        .size(15.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = changeLabel(change),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    if (change.itemCount > 1) {
                        Text(
                            text = change.displayPath,
                            color = MaterialTheme.colorScheme.outline,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            val diff = change.diff
            if (!diff.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(
                        0.5.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = diffAnnotated(diff),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            } else if (change.kind == FileChangeKind.MODIFIED) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Построчный diff недоступен: файл слишком большой или не читается.",
                    color = MaterialTheme.colorScheme.outline,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
        }
    }
}

@Composable
private fun diffAnnotated(diff: String): AnnotatedString {
    val colors = MaterialTheme.colorScheme
    return remember(diff, colors) {
        buildAnnotatedString {
            val lines = diff.lines()
            lines.forEachIndexed { index, line ->
                val color = when {
                    line.startsWith("+") -> StatusSuccess
                    line.startsWith("-") -> colors.error
                    line.startsWith("…") -> colors.outline
                    else -> colors.onSurfaceVariant
                }
                withStyle(SpanStyle(color = color)) { append(line) }
                if (index != lines.lastIndex) append("\n")
            }
        }
    }
}

/**
 * Строка источников под карточкой веб-поиска: «5 источников · lenta.ru · habr.com».
 * Тап открывает шторку. Домены важнее номеров: человек узнаёт сайт до клика.
 */
@Composable
private fun WebSourcesRow(
    sources: List<WebSource>,
    onClick: () -> Unit
) {
    val domains = remember(sources) {
        sources.map { it.domain }.filter { it.isNotBlank() }.distinct().take(3)
    }
    val shape = RoundedCornerShape(9.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f))
            .border(
                BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                shape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = RunnerIcons.Public,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = PluralUtils.sources(sources.size),
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Medium
        )
        if (domains.isNotEmpty()) {
            Text(
                text = " · ${domains.joinToString(" · ")}",
                color = MaterialTheme.colorScheme.outline,
                fontSize = 11.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.width(6.dp))
        Icon(
            imageVector = RunnerIcons.KeyboardArrowRight,
            contentDescription = "Открыть источники",
            tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.8f),
            modifier = Modifier.size(15.dp)
        )
    }
}

/** Шторка источников: карточки с доменом, заголовком и сниппетом. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WebSourcesSheet(
    sources: List<WebSource>,
    onDismiss: () -> Unit,
    onOpenSource: (WebSource) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
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
                .padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Источники",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = PluralUtils.sources(sources.size),
                    color = MaterialTheme.colorScheme.outline,
                    fontSize = 11.5.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Нажмите на карточку, чтобы открыть страницу в браузере.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(10.dp))
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 20.dp)
            ) {
                items(sources) { source ->
                    WebSourceCard(
                        source = source,
                        onClick = { onOpenSource(source) }
                    )
                }
            }
        }
    }
}

@Composable
private fun WebSourceCard(
    source: WebSource,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(
            0.7.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = source.domain.take(1).uppercase(),
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = source.title.ifBlank { source.domain },
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = source.domain,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
                if (source.snippet.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = source.snippet,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                imageVector = RunnerIcons.OpenInNew,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(15.dp)
            )
        }
    }
}

/**
 * Компактный индикатор режима «только чтение».
 *
 * Нужен именно в шапке: когда агент отказывается удалять или запускать,
 * без пометки это выглядит как поломка, а не как защита, которую включил сам
 * пользователь.
 */
@Composable
private fun ReadOnlyBadge() {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
        shape = RoundedCornerShape(7.dp),
        modifier = Modifier.padding(end = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = RunnerIcons.Visibility,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "чтение",
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * Возврат удалённого из корзины.
 *
 * Стоит прямо в карточке операции, свёрнутой по умолчанию: смысл корзины в том,
 * чтобы исправление ошибки было в один тап, а не в три.
 */
@Composable
private fun UndoTrashButton(label: String, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
            .border(
                BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.30f)),
                shape
            )
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = RunnerIcons.Undo,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(7.dp))
            Text(
                text = label,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium
            )
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
private fun getErrorHint(text: String): String? {
    val lower = text.lowercase()
    return when {
        lower.contains("(400)") || lower.contains("400") ->
            "Возможно, выбранная модель не поддерживается провайдером или в настройках указаны несовместимые параметры. Попробуйте сменить модель или проверить настройки."
        lower.contains("(401)") || lower.contains("401") || lower.contains("авторизац") ->
            "Ключ не подошёл выбранному провайдеру. Проверьте, что он от этого сервиса, а не от другого, и что Base URL совпадает. Если задан кастомный заголовок Authorization, он заменяет ключ провайдера."
        lower.contains("(403)") || lower.contains("403") || lower.contains("доступ запрещ") ->
            "Возможно, на балансе закончились средства, у ключа нет прав к этой модели или требуется включить VPN."
        lower.contains("(404)") || lower.contains("404") || lower.contains("не найден") ->
            "Возможно, опечатка в Base URL адреса или провайдер убрал эту модель. Проверьте адрес и список моделей."
        lower.contains("(429)") || lower.contains("429") || lower.contains("лимит") ->
            "Возможно, превышен лимит запросов в минуту (RPM) или исчерпан баланс. Подождите пару минут или смените модель."
        lower.contains("500") || lower.contains("502") || lower.contains("503") || lower.contains("504") || lower.contains("сервер") ->
            "Возможно, сервер провайдера временно перегружен или проводит техработы. Попробуйте повторить запрос позже."
        lower.contains("таймаут") || lower.contains("timeout") || lower.contains("сеть") || lower.contains("connection") ->
            "Возможно, нет подключения к интернету, сайт заблокирован провайдером или стоит настроить Reverse Proxy."
        else -> null
    }
}

@Composable
private fun NoticeBanner(
    text: String,
    isError: Boolean,
    action: MessageAction?,
    onAction: (MessageAction) -> Unit
) {
    val accent = if (isError) MaterialTheme.colorScheme.error else StatusWarning
    val hint = if (isError) remember(text) { getErrorHint(text) } else null

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
                imageVector = if (isError) RunnerIcons.Error else RunnerIcons.Info,
                contentDescription = null,
                tint = accent,
                modifier = Modifier
                    .padding(top = 1.dp)
                    .size(15.dp)
            )
            Spacer(modifier = Modifier.width(9.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = text,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
                if (hint != null) {
                    Spacer(modifier = Modifier.height(5.dp))
                    Text(
                        text = hint,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp
                    )
                }
            }
        }

        if (action != null || isError) {
            val context = LocalContext.current
            Row(
                modifier = Modifier.padding(start = 24.dp, top = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (action != null) {
                    val label = when (action) {
                        MessageAction.OPEN_SETTINGS -> "Открыть настройки"
                        MessageAction.OPEN_MODELS -> "Выбрать модель"
                        MessageAction.GRANT_STORAGE -> "Разрешить доступ"
                        MessageAction.EXECUTE_DRY_RUN -> "Выполнить"
                    }
                    TextButton(onClick = { onAction(action) }) {
                        Text(label, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }
                if (isError) {
                    TextButton(
                        onClick = { CrashReportManager.copyReport(context, "Системное уведомление: $text") }
                    ) {
                        Icon(
                            imageVector = RunnerIcons.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Скопировать отчет", color = MaterialTheme.colorScheme.error, fontSize = 12.5.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun CrashReportBanner(onCopy: () -> Unit, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f)), RoundedCornerShape(10.dp))
            .padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = RunnerIcons.BugReport,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(9.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Предыдущий запуск завершился сбоем",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "Скопируйте отчет для передачи разработчику",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )
        }
        TextButton(
            onClick = onCopy,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Icon(
                imageVector = RunnerIcons.ContentCopy,
                contentDescription = null,
                modifier = Modifier.size(13.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("Отчет", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
        }
        IconButton(
            onClick = onDismiss,
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                imageVector = RunnerIcons.Close,
                contentDescription = "Закрыть",
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.outline
            )
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
            imageVector = RunnerIcons.Warning,
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
                        imageVector = RunnerIcons.Warning,
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
                    imageVector = RunnerIcons.Warning,
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
            // разовое действие, повторять его «молча» опасно. И только если
            // операция вообще опознаваема: иначе подпись пустая и галочка
            // разрешила бы все вызовы инструмента разом.
            if (!hasItems && request.canRemember) {
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

/**
 * Умный генератор контекстных подсказок следующих шагов (Smart Follow-up Pills) под финальным ответом ассистента.
 * Анализирует структуру ответа (код, таблицы, пути, ошибки, сравнения) и выдает 2-3 релевантных действия.
 */
object SmartFollowUpGenerator {
    fun generate(content: String): List<String> {
        val trimmed = content.trim()
        if (trimmed.length < 15) return emptyList()

        val suggestions = mutableListOf<String>()

        val hasCode = trimmed.contains("```")
        val hasTable = trimmed.contains("|") && trimmed.contains("---")
        val hasPaths = trimmed.contains("/storage/") || trimmed.contains("/sdcard/") ||
            Regex("""\.(kt|py|sh|json|txt|md|zip|tar|apk|png|jpg)\b""").containsMatchIn(trimmed)
        val lastLines = trimmed.lines().takeLast(3).map { it.trim() }
        val endsWithQuestion = lastLines.any { it.endsWith("?") }
        val hasError = trimmed.contains("ошибк", ignoreCase = true) ||
            trimmed.contains("error", ignoreCase = true) ||
            trimmed.contains("exception", ignoreCase = true) ||
            trimmed.contains("failed", ignoreCase = true) ||
            trimmed.contains("исправ", ignoreCase = true)
        val isComparison = trimmed.contains("сравнен", ignoreCase = true) ||
            trimmed.contains("преимуществ", ignoreCase = true) ||
            trimmed.contains("отличи", ignoreCase = true) ||
            trimmed.contains("vs", ignoreCase = true)
        val isGuide = trimmed.contains("шаг 1", ignoreCase = true) ||
            trimmed.contains("инструкци", ignoreCase = true) ||
            Regex("""^\d+\.\s+""", RegexOption.MULTILINE).containsMatchIn(trimmed)

        when {
            hasCode -> {
                suggestions.add("Объяснить код пошагово")
                suggestions.add("Оптимизировать код")
                suggestions.add("Добавить обработку ошибок")
            }
            hasTable -> {
                suggestions.add("Сравнить подробнее")
                suggestions.add("Какой вариант лучше?")
                suggestions.add("Сохранить в файл")
            }
            hasError -> {
                suggestions.add("Как это исправить?")
                suggestions.add("Показать рабочий пример")
                suggestions.add("В чем причина ошибки?")
            }
            hasPaths -> {
                suggestions.add("Показать подробности о файлах")
                suggestions.add("Что ещё здесь можно сделать?")
                suggestions.add("Сохранить результат")
            }
            isComparison -> {
                suggestions.add("Какой вариант ты рекомендуешь?")
                suggestions.add("Сравнить по скорости и ресурсам")
                suggestions.add("Показать пример использования")
            }
            isGuide -> {
                suggestions.add("Что делать дальше?")
                suggestions.add("Показать пример реализации")
                suggestions.add("Какие могут быть сложности?")
            }
            endsWithQuestion -> {
                suggestions.add("Да, давай")
                suggestions.add("Расскажи подробнее")
                suggestions.add("Предложи другой вариант")
            }
        }

        return suggestions.distinct().take(3)
    }
}

@Composable
private fun SmartFollowUpRow(
    suggestions: List<String>,
    onSelect: (String) -> Unit
) {
    if (suggestions.isEmpty()) return

    val haptic = LocalHapticFeedback.current

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(suggestions) { suggestion ->
            Surface(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSelect(suggestion)
                },
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.35f),
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
            ) {
                Text(
                    text = suggestion,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}
