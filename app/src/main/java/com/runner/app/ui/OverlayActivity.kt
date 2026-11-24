package com.runner.app.ui
import com.runner.app.ui.theme.MotionSettings
import com.runner.app.ui.theme.MotionTokens
import com.runner.app.ui.theme.RunnerShapes
import com.runner.app.ui.theme.TypeScale

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.runner.app.MainActivity
import com.runner.app.ui.components.ChatInputBar
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import com.runner.app.ui.components.MarkdownView
import com.runner.app.ui.components.AgentStatusIndicator
import com.runner.app.ui.components.rememberChatInputState
import com.runner.app.ui.theme.ApplyAccessibilitySetting
import com.runner.app.ui.theme.ApplyMotionSetting
import com.runner.app.ui.theme.RunnerTheme
import com.runner.app.ui.theme.SecondaryMint
import com.runner.app.ui.theme.bounceClick
import com.runner.app.util.ScreenCaptureManager
import com.runner.app.ui.components.RunnerIcons

/**
 * Плавающее окно-ассистент поверх всех приложений (Quick Assist Overlay).
 * Открывается по тапу на плитку быстрых настроек или при вызове ассистента.
 */
class OverlayActivity : ComponentActivity() {

    companion object {
        const val EXTRA_AUTO_SCREENSHOT = "extra_auto_screenshot"
        const val EXTRA_SCREENSHOT_URI = "extra_screenshot_uri"
        private const val EXTRA_INTERNAL_TOKEN = "extra_internal_token"

        // Случайный токен процесса: Activity экспортируется для ACTION_ASSIST,
        // поэтому внутренние extras принимаются только от своих компонентов.
        private val INTERNAL_TOKEN: String = java.util.UUID.randomUUID().toString()

        fun markInternalIntent(intent: Intent): Intent =
            intent.putExtra(EXTRA_INTERNAL_TOKEN, INTERNAL_TOKEN)

        internal fun isTrustedInternalIntent(intent: Intent?): Boolean {
            val token = intent?.getStringExtra(EXTRA_INTERNAL_TOKEN) ?: return false
            return token == INTERNAL_TOKEN
        }

        internal fun isAllowedInternalCachePath(
            filePath: String?,
            cacheDirCanonicalPath: String
        ): Boolean {
            if (filePath.isNullOrBlank() || cacheDirCanonicalPath.isBlank()) return false
            return try {
                val cacheRoot = java.io.File(cacheDirCanonicalPath).canonicalPath
                    .replace('\\', '/')
                    .trimEnd('/')
                if (cacheRoot.isBlank() || cacheRoot == "/") return false
                val target = java.io.File(filePath).canonicalPath
                    .replace('\\', '/')
                    .trimEnd('/')
                target.startsWith("$cacheRoot/")
            } catch (_: Exception) {
                false
            }
        }
    }

    private val viewModel: MainViewModel by viewModels()
    private val pendingScreenshotUri = mutableStateOf<Uri?>(null)
    private val pendingAutoVoice = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }

        viewModel.startAssistantSession()
        val trustedInternal = isTrustedInternalIntent(intent)
        if (trustedInternal) {
            extractScreenshotUri(intent)
        }
        // Автоматический диалог MediaProjection запускаем только по явному
        // EXTRA_AUTO_SCREENSHOT=true (плитка быстрых настроек). Системный
        // ассистент (RunnerVoiceInteractionSession) передаёт EXTRA_AUTO_SCREENSHOT=false
        // и присылает бесплатный снимок через onHandleScreenshot -> onNewIntent.
        val autoScreenshot = trustedInternal &&
                (intent?.getBooleanExtra(EXTRA_AUTO_SCREENSHOT, false) ?: false)
        pendingAutoVoice.value = intent?.action == Intent.ACTION_VOICE_COMMAND

        setContent {
            val themeConfig by viewModel.themeConfig.collectAsState()
            val appSettings by viewModel.settings.collectAsState()
            val screenshotUri by pendingScreenshotUri
            val autoVoice by pendingAutoVoice

            ApplyMotionSetting(appSettings.motionEnabled)
            ApplyAccessibilitySetting(appSettings.accessibilityEnhanced)
            DisposableEffect(appSettings.secureScreenMode) {
                if (appSettings.secureScreenMode) {
                    window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
                onDispose {
                    window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }
            RunnerTheme(themeConfig = themeConfig) {
                OverlayScreen(
                    viewModel = viewModel,
                    initialAutoScreenshot = autoScreenshot && screenshotUri == null,
                    initialScreenshotUri = screenshotUri,
                    initialAutoVoice = autoVoice,
                    onAutoVoiceConsumed = { pendingAutoVoice.value = false },
                    onOpenInFullChat = { sessionId ->
                        val fullIntent = Intent(this, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                            putExtra("SESSION_ID", sessionId)
                        }
                        startActivity(fullIntent)
                        finish()
                    },
                    onDismiss = { finish() }
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Оверлей — тоже передний план: иначе подтверждение и завершение
        // задачи в нём не дают системного уведомления, а isAppInForeground
        // остаётся true после сворачивания.
        viewModel.setAppInForeground(true)
    }

    override fun onStop() {
        super.onStop()
        viewModel.setAppInForeground(false)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val trusted = isTrustedInternalIntent(intent)
        if (trusted) {
            extractScreenshotUri(intent)
        }
        if (intent.action == Intent.ACTION_VOICE_COMMAND) {
            pendingAutoVoice.value = true
        }
        // Не сбрасываем сессию, когда RunnerVoiceInteractionSession просто
        // прислала отложенный скриншот для уже открытого окна.
        val isScreenshotFollowUp = trusted && !intent.getStringExtra(EXTRA_SCREENSHOT_URI).isNullOrBlank()
        if ((intent.action == Intent.ACTION_ASSIST || intent.action == Intent.ACTION_VOICE_COMMAND) &&
            !isScreenshotFollowUp
        ) {
            viewModel.startAssistantSession()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        com.runner.app.service.RunnerVoiceInteractionSession.activeSession?.finish()
    }

    private fun extractScreenshotUri(intent: Intent?) {
        if (!isTrustedInternalIntent(intent)) return
        val uriStr = intent?.getStringExtra(EXTRA_SCREENSHOT_URI)
        if (!uriStr.isNullOrBlank()) {
            try {
                val parsed = Uri.parse(uriStr)
                val scheme = parsed.scheme?.lowercase()
                val allowed = when (scheme) {
                    "file" -> isAllowedInternalCachePath(parsed.path, cacheDir.canonicalPath)
                    "content" -> parsed.authority == "$packageName.fileprovider"
                    else -> false
                }
                if (allowed) {
                    pendingScreenshotUri.value = parsed
                }
            } catch (_: Exception) {}
        }
    }
}

@Composable
private fun OverlayScreen(
    viewModel: MainViewModel,
    initialAutoScreenshot: Boolean,
    initialScreenshotUri: Uri?,
    initialAutoVoice: Boolean,
    onAutoVoiceConsumed: () -> Unit,
    onOpenInFullChat: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val inputState = rememberChatInputState()

    val currentSessionId by viewModel.currentSessionId.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val streamingText by viewModel.streamingText.collectAsState()
    val streamStartedAt by viewModel.streamStartedAt.collectAsState()
    val isRunning by viewModel.isRunning.collectAsState()
    val currentStatus by viewModel.currentStatus.collectAsState()
    val isListening by viewModel.isListening.collectAsState()
    val rmsLevel by viewModel.rmsLevel.collectAsState()
    val isSpeaking by viewModel.isSpeaking.collectAsState()
    val activeUtteranceId by viewModel.activeUtteranceId.collectAsState()
    val activeProvider by viewModel.activeProvider.collectAsState()

    var isCapturingScreen by remember { mutableStateOf(false) }
    var capturedScreenshotUri by remember { mutableStateOf<Uri?>(initialScreenshotUri) }

    LaunchedEffect(initialScreenshotUri) {
        if (initialScreenshotUri != null) {
            capturedScreenshotUri = initialScreenshotUri
            inputState.addImages(listOf(initialScreenshotUri))
        }
    }

    val micLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.checkAllPermissions()
            viewModel.startVoiceInput(
                onPartialResult = { inputState.setText(it) },
                onFinalResult = { inputState.setText(it) },
                onError = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
            )
        } else {
            Toast.makeText(context, "Требуется разрешение на запись аудио для голосового ввода", Toast.LENGTH_SHORT).show()
        }
    }

    val screenCaptureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            isCapturingScreen = true
            scope.launch {
                val act = context as? Activity ?: return@launch
                val uri = ScreenCaptureManager.captureScreen(
                    act,
                    result.resultCode,
                    result.data!!
                )
                isCapturingScreen = false
                if (uri != null) {
                    capturedScreenshotUri = uri
                    inputState.addImages(listOf(uri))
                } else {
                    Toast.makeText(context, "Не удалось сохранить снимок", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            isCapturingScreen = false
        }
    }

    fun triggerScreenCapture() {
        try {
            val captureIntent = ScreenCaptureManager.createScreenCaptureIntent(context)
            screenCaptureLauncher.launch(captureIntent)
        } catch (e: Exception) {
            Toast.makeText(context, "Захват экрана недоступен: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(initialAutoScreenshot) {
        if (initialAutoScreenshot && capturedScreenshotUri == null) {
            triggerScreenCapture()
        }
    }

    LaunchedEffect(initialAutoVoice) {
        if (initialAutoVoice) {
            onAutoVoiceConsumed()
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                viewModel.startVoiceInput(
                    onPartialResult = { inputState.setText(it) },
                    onFinalResult = { inputState.setText(it) },
                    onError = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
                )
            } else {
                micLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    val density = androidx.compose.ui.platform.LocalDensity.current
    val dismissThresholdPx = remember(density) { density.density * 120f }
    val offsetY = remember { Animatable(0f) }

    val dragModifier = Modifier.pointerInput(Unit) {
        detectVerticalDragGestures(
            onDragEnd = {
                scope.launch {
                    if (offsetY.value > dismissThresholdPx) {
                        if (MotionSettings.enabled) {
                            offsetY.animateTo(
                                targetValue = 1200f,
                                animationSpec = MotionTokens.spatialFast()
                            )
                        } else {
                            offsetY.snapTo(1200f)
                        }
                        onDismiss()
                    } else {
                        if (MotionSettings.enabled) {
                            offsetY.animateTo(
                                targetValue = 0f,
                                animationSpec = MotionTokens.spatialDefault()
                            )
                        } else {
                            offsetY.snapTo(0f)
                        }
                    }
                }
            },
            onDragCancel = {
                scope.launch {
                    if (MotionSettings.enabled) {
                        offsetY.animateTo(0f, MotionTokens.spatialDefault())
                    } else {
                        offsetY.snapTo(0f)
                    }
                }
            },
            onVerticalDrag = { change, dragAmount ->
                change.consume()
                scope.launch {
                    val newOffset = (offsetY.value + dragAmount).coerceAtLeast(0f)
                    offsetY.snapTo(newOffset)
                }
            }
        )
    }

    val backgroundAlpha = (1f - (offsetY.value / 600f)).coerceIn(0f, 1f) * 0.55f

    // Тёмный полупрозрачный фон с закрытием при тапе мимо карточки
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = backgroundAlpha))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onDismiss() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(0, offsetY.value.roundToInt().coerceAtLeast(0)) }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {} // Блокируем закрытие при кликах внутри панели
                .navigationBarsPadding()
                .imePadding(),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                // Индикатор смахивания (drag handle) с увеличенной зоной касания и обработкой свайпа
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                        .then(dragModifier),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 38.dp, height = 4.5.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    )
                }

                // Шапка с поддержкой свайпа вниз
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(dragModifier)
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = RunnerIcons.SmartToy,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Runner",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RunnerShapes.tiny,
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = "БЕТА",
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(horizontal = 4.5.dp, vertical = 1.dp)
                        )
                    }

                    activeProvider?.let { p ->
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "· ${formatModelName(p.activeModel)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    } ?: Spacer(modifier = Modifier.weight(1f))

                    // Кнопка перехода в полный чат
                    IconButton(
                        onClick = { onOpenInFullChat(currentSessionId) },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = RunnerIcons.OpenInNew,
                            contentDescription = "Открыть в чате",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Кнопка закрытия
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = RunnerIcons.Close,
                            contentDescription = "Закрыть",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Быстрые подсказки / снимок экрана
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Кнопка быстрого снимка экрана
                    Surface(
                        shape = RunnerShapes.large,
                        color = if (capturedScreenshotUri != null) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .semantics(mergeDescendants = true) {
                                role = Role.Button
                                onClick(label = "Снимок экрана") {
                                    triggerScreenCapture()
                                    true
                                }
                            }
                            .bounceClick { triggerScreenCapture() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isCapturingScreen) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Icon(
                                    imageVector = RunnerIcons.CameraAlt,
                                    contentDescription = null,
                                    tint = if (capturedScreenshotUri != null) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (capturedScreenshotUri != null) "Снимок прикреплён" else "Снимок экрана",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (capturedScreenshotUri != null) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Быстрые шаблоны вопросов
                    val quickPrompts = if (capturedScreenshotUri != null) {
                        listOf("Что на экране?", "Переведи", "Объясни", "Выдели главное")
                    } else {
                        listOf("Объясни", "Переведи", "Помоги с кодом", "Сделай выжимку")
                    }

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(quickPrompts) { prompt ->
                            QuickPromptChip(label = prompt) {
                                val images = inputState.selectedImages
                                val files = inputState.selectedFiles
                                val forceSearch = inputState.webSearch
                                val forceThinking = inputState.think
                                viewModel.sendMessage(
                                    prompt = prompt,
                                    images = images,
                                    forceSearch = forceSearch,
                                    forceThinking = forceThinking,
                                    files = files
                                )
                                inputState.clear()
                                capturedScreenshotUri = null
                            }
                        }
                    }
                }

                // Область ответов: показываем стрим, ответ ассистента, шаги инструментов
                // и системные сообщения (ошибки ключа, 401, сети и т.д.).
                val lastUserIdx = messages.indexOfLast { it.role == MessageRole.USER }
                val currentTurnMessages = if (lastUserIdx >= 0) messages.drop(lastUserIdx + 1) else messages
                val lastAssistant = currentTurnMessages.lastOrNull { it.role == MessageRole.ASSISTANT }
                    ?: messages.lastOrNull { it.role == MessageRole.ASSISTANT }
                val latestSystemInfo = currentTurnMessages.lastOrNull { it.role == MessageRole.SYSTEM_INFO }
                val recentTools = currentTurnMessages
                    .filter { it.role == MessageRole.TOOL_EXECUTION }
                    .takeLast(3)
                val hasResponseContent = isRunning ||
                        streamingText.isNotBlank() ||
                        lastAssistant != null ||
                        latestSystemInfo != null ||
                        recentTools.isNotEmpty()

                if (hasResponseContent) {
                    Surface(
                        shape = RunnerShapes.large,
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .heightIn(max = 260.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(12.dp)
                        ) {
                            if (isRunning) {
                                AgentStatusIndicator(
                                    status = currentStatus,
                                    dotColor = SecondaryMint,
                                    dotSize = 5.dp,
                                    height = 24.dp,
                                    horizontalPadding = 0.dp,
                                    startedAt = streamStartedAt
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                            }

                            if (recentTools.isNotEmpty()) {
                                recentTools.forEach { toolMsg ->
                                    val label = toolMsg.content.ifBlank { toolMsg.toolName.orEmpty() }
                                    val summary = toolMsg.toolSummary?.takeIf { it.isNotBlank() }
                                    val line = if (summary != null) "$label · $summary" else label
                                    if (line.isNotBlank()) {
                                        Text(
                                            text = line,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (toolMsg.isError || toolMsg.isDeclined) {
                                                MaterialTheme.colorScheme.error
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(bottom = 4.dp)
                                        )
                                    }
                                }
                            }

                            val displayText = if (streamingText.isNotBlank()) {
                                streamingText
                            } else {
                                lastAssistant?.content.orEmpty()
                            }
                            if (displayText.isNotBlank()) {
                                MarkdownView(text = displayText, isStreaming = streamingText.isNotBlank())
                            }

                            if (latestSystemInfo != null && latestSystemInfo.content.isNotBlank()) {
                                if (displayText.isNotBlank() || recentTools.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                }
                                Surface(
                                    shape = RunnerShapes.small,
                                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = latestSystemInfo.content,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }

                            if (!isRunning && lastAssistant != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val isCurSpeaking = isSpeaking && activeUtteranceId == lastAssistant.id
                                    IconButton(
                                        onClick = { viewModel.toggleSpeakMessage(lastAssistant.id, lastAssistant.content) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        @Suppress("DEPRECATION")
                                        val volIcon = if (isCurSpeaking) RunnerIcons.Stop else RunnerIcons.VolumeUp
                                        Icon(
                                            imageVector = volIcon,
                                            contentDescription = "Озвучить",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            val mgr = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            mgr.setPrimaryClip(ClipData.newPlainText("Ответ", lastAssistant.content))
                                            Toast.makeText(context, "Скопировано", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = RunnerIcons.ContentCopy,
                                            contentDescription = "Копировать",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Панель ввода
                ChatInputBar(
                    inputState = inputState,
                    isRunning = isRunning,
                    isListening = isListening,
                    rmsLevel = rmsLevel,
                    onSend = { text, images, files ->
                        viewModel.sendMessage(
                            prompt = text,
                            images = images,
                            forceSearch = inputState.webSearch,
                            forceThinking = inputState.think,
                            files = files
                        )
                        capturedScreenshotUri = null
                    },
                    onStop = { viewModel.stopGeneration() },
                    onStartListening = {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                            viewModel.startVoiceInput(
                                onPartialResult = { inputState.setText(it) },
                                onFinalResult = { inputState.setText(it) },
                                onError = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
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

    // План и подтверждения должны быть доступны и из плавающего окна:
    // без них задача с опасным шагом ждала ответа, которого некуда прийти,
    // и висела до таймаута.
    OverlayTaskApprovalDialogs(viewModel)
}

@Composable
private fun OverlayTaskApprovalDialogs(viewModel: MainViewModel) {
    val pendingConfirmation by viewModel.pendingConfirmation.collectAsState()
    val pendingPlan by viewModel.pendingPlan.collectAsState()

    pendingConfirmation?.let { request ->
        AlertDialog(
            onDismissRequest = { viewModel.resolveConfirmation(false) },
            title = { Text(request.title) },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = request.details,
                        fontSize = TypeScale.caption,
                        fontFamily = FontFamily.Monospace
                    )
                    if (request.preview.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = request.preview,
                            fontSize = TypeScale.caption,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = request.warning,
                        fontSize = TypeScale.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.resolveConfirmation(true) }) {
                    Text("Разрешить")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.resolveConfirmation(false) }) {
                    Text("Отклонить")
                }
            }
        )
    }

    pendingPlan?.let { plan ->
        val criteria = remember(plan.rawText) {
            plan.rawText.lineSequence()
                .map { it.trim() }
                .firstOrNull { it.contains("Критерий успеха", ignoreCase = true) }
                ?.substringAfter(':', "")
                ?.trim()
                .orEmpty()
        }
        AlertDialog(
            onDismissRequest = { viewModel.resolvePlan(false) },
            title = { Text("План действий") },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    plan.steps.forEachIndexed { index, step ->
                        Text(
                            text = "${index + 1}. $step",
                            fontSize = TypeScale.label,
                            lineHeight = 18.sp
                        )
                    }
                    if (plan.steps.isEmpty()) {
                        Text(
                            text = plan.rawText,
                            fontSize = TypeScale.label,
                            lineHeight = 18.sp
                        )
                    }
                    if (criteria.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Критерий успеха: $criteria",
                            fontSize = TypeScale.caption,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.resolvePlan(true) }) {
                    Text("Утвердить")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.resolvePlan(false) }) {
                    Text("Отклонить")
                }
            }
        )
    }
}

@Composable
private fun QuickPromptChip(
    label: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RunnerShapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .semantics(mergeDescendants = true) {
                role = Role.Button
                onClick(label = label) {
                    onClick()
                    true
                }
            }
            .bounceClick { onClick() }
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
        )
    }
}
