package com.runner.app.ui

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import com.runner.app.ui.theme.RunnerTheme
import com.runner.app.ui.theme.SecondaryMint
import com.runner.app.ui.theme.bounceClick
import com.runner.app.util.ScreenCaptureManager
import kotlinx.coroutines.launch
import com.runner.app.ui.components.RunnerIcons

/**
 * Плавающее окно-ассистент поверх всех приложений (Quick Assist Overlay).
 * Открывается по тапу на плитку быстрых настроек или при вызове ассистента.
 */
class OverlayActivity : ComponentActivity() {

    companion object {
        const val EXTRA_AUTO_SCREENSHOT = "extra_auto_screenshot"
        const val EXTRA_SCREENSHOT_URI = "extra_screenshot_uri"
    }

    private val viewModel: MainViewModel by viewModels()
    private val pendingScreenshotUri = mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel.startAssistantSession()
        extractScreenshotUri(intent)
        val isAssist = intent?.action == Intent.ACTION_ASSIST
        val autoScreenshot = isAssist || (intent?.getBooleanExtra(EXTRA_AUTO_SCREENSHOT, false) ?: false)

        setContent {
            val themeConfig by viewModel.themeConfig.collectAsState()
            val screenshotUri by pendingScreenshotUri

            RunnerTheme(themeConfig = themeConfig) {
                OverlayScreen(
                    viewModel = viewModel,
                    initialAutoScreenshot = autoScreenshot && screenshotUri == null,
                    initialScreenshotUri = screenshotUri,
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
        extractScreenshotUri(intent)
        if (intent.action == Intent.ACTION_ASSIST) {
            viewModel.startAssistantSession()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        com.runner.app.service.RunnerVoiceInteractionSession.activeSession?.finish()
    }

    private fun extractScreenshotUri(intent: Intent?) {
        val uriStr = intent?.getStringExtra(EXTRA_SCREENSHOT_URI)
        if (!uriStr.isNullOrBlank()) {
            try {
                pendingScreenshotUri.value = Uri.parse(uriStr)
            } catch (_: Exception) {}
        }
    }
}

@Composable
private fun OverlayScreen(
    viewModel: MainViewModel,
    initialAutoScreenshot: Boolean,
    initialScreenshotUri: Uri?,
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

    val density = androidx.compose.ui.platform.LocalDensity.current
    val dismissThresholdPx = remember(density) { density.density * 120f }
    val offsetY = remember { Animatable(0f) }

    val dragModifier = Modifier.pointerInput(Unit) {
        detectVerticalDragGestures(
            onDragEnd = {
                scope.launch {
                    if (offsetY.value > dismissThresholdPx) {
                        offsetY.animateTo(
                            targetValue = 1200f,
                            animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)
                        )
                        onDismiss()
                    } else {
                        offsetY.animateTo(
                            targetValue = 0f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        )
                    }
                }
            },
            onDragCancel = {
                scope.launch {
                    offsetY.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
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
                        imageVector = RunnerIcons.AutoAwesome,
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
                        shape = RoundedCornerShape(4.dp),
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
                        shape = RoundedCornerShape(16.dp),
                        color = if (capturedScreenshotUri != null) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
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
                                viewModel.sendMessage(prompt, images)
                                inputState.clear()
                                capturedScreenshotUri = null
                            }
                        }
                    }
                }

                // Область ответов (если есть стрим или последнее сообщение)
                val lastAssistant = messages.lastOrNull { it.role == MessageRole.ASSISTANT }
                val hasResponseContent = streamingText.isNotBlank() || (lastAssistant != null)

                if (hasResponseContent) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .heightIn(max = 240.dp)
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

                            val displayText = if (streamingText.isNotBlank()) streamingText else lastAssistant?.content.orEmpty()
                            MarkdownView(text = displayText, isStreaming = streamingText.isNotBlank())

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
                        viewModel.sendMessage(text, images, files = files)
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
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    if (request.preview.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = request.preview,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = request.warning,
                        fontSize = 12.sp,
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
                            fontSize = 12.5.sp,
                            lineHeight = 18.sp
                        )
                    }
                    if (plan.steps.isEmpty()) {
                        Text(
                            text = plan.rawText,
                            fontSize = 12.5.sp,
                            lineHeight = 18.sp
                        )
                    }
                    if (criteria.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Критерий успеха: $criteria",
                            fontSize = 12.sp,
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
                    Text("Без плана")
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
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.bounceClick { onClick() }
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
        )
    }
}
