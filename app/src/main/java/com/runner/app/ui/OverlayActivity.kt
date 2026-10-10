package com.runner.app.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.runner.app.MainActivity
import com.runner.app.ui.components.ChatInputBar
import com.runner.app.ui.components.MarkdownView
import com.runner.app.ui.components.rememberChatInputState
import com.runner.app.ui.theme.RunnerTheme
import com.runner.app.ui.theme.bounceClick
import com.runner.app.util.ScreenCaptureManager
import kotlinx.coroutines.launch

/**
 * Плавающее окно-ассистент поверх всех приложений (Quick Assist Overlay).
 * Открывается по тапу на плитку быстрых настроек или при вызове ассистента.
 */
class OverlayActivity : ComponentActivity() {

    companion object {
        const val EXTRA_AUTO_SCREENSHOT = "extra_auto_screenshot"
    }

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val autoScreenshot = intent?.getBooleanExtra(EXTRA_AUTO_SCREENSHOT, false) ?: false

        setContent {
            val themeConfig by viewModel.themeConfig.collectAsState()
            RunnerTheme(themeConfig = themeConfig) {
                OverlayScreen(
                    viewModel = viewModel,
                    initialAutoScreenshot = autoScreenshot,
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
}

@Composable
private fun OverlayScreen(
    viewModel: MainViewModel,
    initialAutoScreenshot: Boolean,
    onOpenInFullChat: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val inputState = rememberChatInputState()

    val currentSessionId by viewModel.currentSessionId.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val streamingText by viewModel.streamingText.collectAsState()
    val isRunning by viewModel.isRunning.collectAsState()
    val currentStatus by viewModel.currentStatus.collectAsState()
    val isListening by viewModel.isListening.collectAsState()
    val rmsLevel by viewModel.rmsLevel.collectAsState()
    val isSpeaking by viewModel.isSpeaking.collectAsState()
    val activeUtteranceId by viewModel.activeUtteranceId.collectAsState()
    val activeProvider by viewModel.activeProvider.collectAsState()

    var isCapturingScreen by remember { mutableStateOf(false) }
    var capturedScreenshotUri by remember { mutableStateOf<Uri?>(null) }

    val screenCaptureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            isCapturingScreen = true
            scope.launch {
                val uri = ScreenCaptureManager.captureScreen(
                    context as Activity,
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

    LaunchedEffect(Unit) {
        if (initialAutoScreenshot) {
            triggerScreenCapture()
        }
    }

    // Тёмный полупрозрачный фон с закрытием при тапе мимо карточки
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onDismiss() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
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
                    .padding(top = 10.dp)
            ) {
                // Индикатор смахивания (drag handle)
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(width = 36.dp, height = 4.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )

                // Шапка
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AutoAwesome,
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
                            imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
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
                            imageVector = Icons.Outlined.Close,
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
                                    imageVector = Icons.Outlined.CameraAlt,
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
                            if (isRunning && currentStatus != null) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(12.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = currentStatus.orEmpty(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                            }

                            val displayText = if (streamingText.isNotBlank()) streamingText else lastAssistant?.content.orEmpty()
                            MarkdownView(text = displayText)

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
                                        val volIcon = if (isCurSpeaking) Icons.Outlined.Stop else Icons.Outlined.VolumeUp
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
                                            imageVector = Icons.Outlined.ContentCopy,
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
                    onSend = { text, images ->
                        viewModel.sendMessage(text, images)
                        capturedScreenshotUri = null
                    },
                    onStop = { viewModel.stopGeneration() },
                    onStartListening = {
                        viewModel.startVoiceInput(
                            onPartialResult = { inputState.setText(it) },
                            onFinalResult = { inputState.setText(it) },
                            onError = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
                        )
                    },
                    onStopListening = { viewModel.stopVoiceInput() }
                )
            }
        }
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
