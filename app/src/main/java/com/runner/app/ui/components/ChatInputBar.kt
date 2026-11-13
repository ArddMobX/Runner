package com.runner.app.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.core.content.FileProvider
import java.io.File
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.runner.app.ui.commands.SlashCommand
import com.runner.app.ui.commands.SlashCommandParser
import com.runner.app.ui.theme.MotionTokens
import com.runner.app.ui.theme.bounceClick
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.runner.app.ui.components.RunnerIcons

/**
 * Изолированное состояние поля ввода.
 *
 * Хранит TextFieldValue и прикреплённые изображения локально, чтобы изменения текста
 * не вызывали рекомпозицию родительского экрана чата или списка сообщений при каждом нажатии клавиши.
 */
@Stable
class ChatInputState(initialText: String = "") {
    var textFieldValue by mutableStateOf(
        TextFieldValue(
            text = initialText,
            selection = TextRange(initialText.length)
        )
    )

    var selectedImages by mutableStateOf<List<Uri>>(emptyList())

    /** Принудительный веб-поиск перед ответом. */
    var webSearch by mutableStateOf(false)

    /** Принудительно включить размышления модели, если провайдер это умеет. */
    var think by mutableStateOf(false)

    val text: String
        get() = textFieldValue.text

    fun setText(newText: String) {
        textFieldValue = TextFieldValue(
            text = newText,
            selection = TextRange(newText.length)
        )
    }

    fun addImages(uris: List<Uri>) {
        selectedImages = (selectedImages + uris).distinct().take(10)
    }

    fun removeImage(uri: Uri) {
        selectedImages = selectedImages.filter { it != uri }
    }

    fun clear() {
        textFieldValue = TextFieldValue("")
        selectedImages = emptyList()
    }
}

@Composable
fun rememberChatInputState(initialText: String = ""): ChatInputState =
    remember { ChatInputState(initialText) }

/**
 * Полностью изолированный компонент панели ввода сообщений.
 *
 * 1. Локальный стейт ввода (не триггерит ререндер списка сообщений и родительского экрана).
 * 2. Кнопка «+»: быстрое прикрепление фото из галереи (Vision).
 * 3. Голосовой ввод речи через системный SpeechRecognizer (STT).
 * 4. Идеальное геометрическое выравнивание кнопки «+», поля ввода и микрофона.
 * 5. Многострочный ввод: Enter переносит строку (до 5 строк со скроллом),
 *    отправка — только кнопкой справа, imeAction = None.
 */
@Composable
fun ChatInputBar(
    inputState: ChatInputState,
    isRunning: Boolean,
    isListening: Boolean = false,
    rmsLevel: Float = 0f,
    sendOnEnter: Boolean = false,
    onSend: (String, List<Uri>) -> Unit,
    onStop: () -> Unit,
    /** Возвращает true, если команда выполнена и текст можно очистить. */
    onCommand: ((SlashCommand, String) -> Boolean)? = null,
    onStartListening: (() -> Unit)? = null,
    onStopListening: (() -> Unit)? = null,
    placeholder: String? = null,
    isLiveMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val textValue = inputState.textFieldValue
    val rawText = textValue.text
    val selectedImages = inputState.selectedImages
    val hasContent = rawText.isNotBlank() || selectedImages.isNotEmpty()
    val canSend = canSendMessage(hasContent = hasContent, isRunning = isRunning, isListening = isListening)
    var isFocused by remember { mutableStateOf(false) }
    val isActive = isFocused || hasContent || isListening
    val paletteCommands = if (onCommand == null) {
        emptyList()
    } else {
        SlashCommandParser.paletteMatches(rawText)
    }

    var showAttachMenu by remember { mutableStateOf(false) }
    var currentCameraUri by remember { mutableStateOf<Uri?>(null) }

    // Лаунчер галереи (фото): выбор существующих изображений
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            inputState.addImages(uris)
        }
    }

    // Лаунчер камеры: запускает системную камеру и сохраняет результат во временный файл
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            currentCameraUri?.let { uri ->
                inputState.addImages(listOf(uri))
            }
        }
        currentCameraUri = null
    }

    fun launchCamera() {
        try {
            val cameraDir = File(context.cacheDir, "camera").apply { mkdirs() }
            val photoFile = File(cameraDir, "photo_${System.currentTimeMillis()}.jpg")
            val authority = "${context.packageName}.fileprovider"
            val uri = FileProvider.getUriForFile(context, authority, photoFile)
            currentCameraUri = uri
            cameraLauncher.launch(uri)
        } catch (e: Exception) {
            Toast.makeText(context, "Не удалось открыть камеру: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            onStartListening?.invoke()
        } else {
            Toast.makeText(context, "Требуется разрешение на запись аудио", Toast.LENGTH_SHORT).show()
        }
    }

    val borderColor by animateColorAsState(
        targetValue = when {
            isRunning -> MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
            isListening -> MaterialTheme.colorScheme.primary
            isActive -> MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
            isLiveMode -> MaterialTheme.colorScheme.primary.copy(alpha = 0.38f)
            else -> MaterialTheme.colorScheme.outlineVariant
        },
        animationSpec = MotionTokens.fluidTween(200),
        label = "input_border"
    )

    /**
     * Выполняет команду через экран чата. Возвращает true, если текст можно
     * очистить. Изображения при этом остаются: команда не отправляет сообщение.
     */
    fun executeCommand(command: SlashCommand, args: String) {
        val handler = onCommand ?: return
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        if (handler(command, args)) {
            inputState.setText("")
            focusManager.clearFocus()
        }
    }

    fun pickPaletteCommand(command: SlashCommand) {
        if (isRunning && !command.availableWhileRunning) {
            executeCommand(command, "")
            return
        }
        if (command.requiresArgs) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            inputState.setText("${command.invocation} ")
            focusRequester.requestFocus()
        } else {
            executeCommand(command, "")
        }
    }

    fun handleSend() {
        val trimmed = inputState.text.trim()
        val images = inputState.selectedImages

        // Команда перехватывается до отправки. Неизвестный слэш остаётся
        // обычным текстом, чтобы пути вида /storage/... не ломались.
        if (onCommand != null && trimmed.startsWith("/")) {
            val parsed = SlashCommandParser.parse(trimmed)
            if (parsed != null) {
                executeCommand(parsed.command, parsed.args)
                return
            }

            // Неполное имя без пробелов: Enter не отправляет его модели.
            // Если подходит одна команда, выполняем её, иначе просим уточнить.
            if (trimmed.none { it.isWhitespace() }) {
                val partial = SlashCommandParser.paletteMatches(trimmed)
                if (partial.size == 1) {
                    executeCommand(partial.first(), "")
                    return
                }
                if (partial.size > 1) {
                    Toast.makeText(
                        context,
                        "Уточни команду или выбери её из списка",
                        Toast.LENGTH_SHORT
                    ).show()
                    return
                }
            }
        }

        if ((trimmed.isNotEmpty() || images.isNotEmpty()) && !isRunning) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            inputState.clear()
            onSend(trimmed, images)
        }
    }

    fun handleMicClick() {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        if (isListening) {
            onStopListening?.invoke()
        } else {
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED

            if (hasPermission) {
                onStartListening?.invoke()
            } else {
                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    fun handleStop() {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        onStop()
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 12.dp)
    ) {
        // Полоса выбранных изображений над полем ввода
        if (selectedImages.isNotEmpty()) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp, start = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(selectedImages) { uri ->
                    SelectedImageThumbnail(
                        uri = uri,
                        onRemove = { inputState.removeImage(uri) }
                    )
                }
            }
        }

        if (paletteCommands.isNotEmpty()) {
            SlashCommandPalette(
                commands = paletteCommands,
                isRunning = isRunning,
                onPick = { pickPaletteCommand(it) },
                modifier = Modifier.padding(bottom = 7.dp)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .border(BorderStroke(if (isLiveMode) 1.2.dp else 1.dp, borderColor), RoundedCornerShape(26.dp))
                .padding(horizontal = 6.dp, vertical = 5.dp)
        ) {
            InputModeChips(
                webSearch = inputState.webSearch,
                think = inputState.think,
                enabled = !isRunning,
                onToggleSearch = { inputState.webSearch = !inputState.webSearch },
                onToggleThink = { inputState.think = !inputState.think }
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = if (textValue.text.contains('\n')) Alignment.Bottom else Alignment.CenterVertically
            ) {
            // Кнопка «+»: быстрое меню прикрепления — камера или галерея.
            // Идеально сбалансирована по центру, размеру (38dp) и форме с кнопкой микрофона/отправки.
            Box(
                modifier = Modifier.size(38.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f))
                        .bounceClick(enabled = !isRunning, scaleDown = 0.90f) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            showAttachMenu = true
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = RunnerIcons.Add,
                        contentDescription = "Прикрепить фото или сделать снимок",
                        tint = if (!isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                        modifier = Modifier.size(20.dp)
                    )
                }

                DropdownMenu(
                    expanded = showAttachMenu,
                    onDismissRequest = { showAttachMenu = false },
                    modifier = Modifier.clip(RoundedCornerShape(18.dp))
                ) {
                    DropdownMenuItem(
                        text = { Text("Камера", fontSize = 14.sp) },
                        leadingIcon = {
                            Icon(
                                imageVector = RunnerIcons.CameraAlt,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            showAttachMenu = false
                            launchCamera()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Галерея", fontSize = 14.sp) },
                        leadingIcon = {
                            Icon(
                                imageVector = RunnerIcons.Image,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            showAttachMenu = false
                            imagePickerLauncher.launch("image/*")
                        }
                    )
                }
            }

            BasicTextField(
                value = textValue,
                onValueChange = { inputState.textFieldValue = it },
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)
                    .focusRequester(focusRequester)
                    .onFocusChanged { isFocused = it.isFocused }
                    .onKeyEvent { keyEvent ->
                        if (sendOnEnter && keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.Enter && !keyEvent.isShiftPressed) {
                            if (canSend) {
                                handleSend()
                            }
                            true
                        } else {
                            false
                        }
                    },
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 15.sp,
                    lineHeight = 22.sp
                ),
                maxLines = 5,
                minLines = 1,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    autoCorrect = true,
                    imeAction = if (sendOnEnter) ImeAction.Send else ImeAction.Default
                ),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (sendOnEnter && canSend) {
                            handleSend()
                        }
                    }
                ),
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (rawText.isEmpty()) {
                            Text(
                                text = if (isListening) "Слушаю речь..." else (placeholder ?: "Задать задачу или сказать голосом..."),
                                color = if (isListening) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                fontSize = 15.sp,
                                lineHeight = 22.sp
                            )
                        }
                        innerTextField()
                    }
                }
            )

            SendMicStopButton(
                isRunning = isRunning,
                canSend = canSend,
                isListening = isListening,
                rmsLevel = rmsLevel,
                onSend = { handleSend() },
                onStop = { handleStop() },
                onMicClick = { handleMicClick() }
            )
            }
        }
    }
}

/**
 * Два режима ввода: принудительный поиск и размышления.
 * Это не настройки навсегда, а переключатели на текущий диалог.
 */
@Composable
private fun InputModeChips(
    webSearch: Boolean,
    think: Boolean,
    enabled: Boolean,
    onToggleSearch: () -> Unit,
    onToggleThink: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 4.dp, top = 1.dp, bottom = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        InputModeChip(
            label = "Поиск",
            icon = RunnerIcons.Search,
            active = webSearch,
            enabled = enabled,
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onToggleSearch()
            }
        )
        InputModeChip(
            label = "Думать",
            icon = RunnerIcons.Psychology,
            active = think,
            enabled = enabled,
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onToggleThink()
            }
        )
    }
}

@Composable
private fun InputModeChip(
    label: String,
    icon: ImageVector,
    active: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(9.dp)
    val background by animateColorAsState(
        targetValue = when {
            !enabled -> MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.3f)
            active -> MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
            else -> MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f)
        },
        animationSpec = MotionTokens.fluidTween(180),
        label = "mode_chip_background"
    )
    val contentColor by animateColorAsState(
        targetValue = when {
            !enabled -> MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
            active -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = MotionTokens.fluidTween(180),
        label = "mode_chip_content"
    )
    Row(
        modifier = Modifier
            .clip(shape)
            .background(background)
            .border(
                BorderStroke(
                    1.dp,
                    if (active) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    } else {
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                    }
                ),
                shape
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = label,
            color = contentColor,
            fontSize = 11.5.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium
        )
    }
}

/**
 * Превью прикреплённого изображения с кнопкой удаления.
 */
@Composable
private fun SelectedImageThumbnail(
    uri: Uri,
    onRemove: () -> Unit
) {
    val context = LocalContext.current
    val bitmapState = produceState<ImageBitmap?>(initialValue = null, key1 = uri) {
        value = withContext(Dispatchers.IO) {
            try {
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, options)
                }
                var sampleSize = 1
                while (options.outWidth / sampleSize > 250 || options.outHeight / sampleSize > 250) {
                    sampleSize *= 2
                }
                options.inJustDecodeBounds = false
                options.inSampleSize = sampleSize
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, options)?.asImageBitmap()
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    Box(
        modifier = Modifier
            .size(58.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        val bitmap = bitmapState.value
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = "Прикрепленное фото",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(3.dp)
                .size(18.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.65f))
                .bounceClick(scaleDown = 0.85f) { onRemove() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = RunnerIcons.Close,
                contentDescription = "Удалить",
                tint = Color.White,
                modifier = Modifier.size(12.dp)
            )
        }
    }
}

/**
 * Многофункциональная кнопка действия:
 * - Выполняется задача: красный StopSquare.
 * - Есть текст или фото: акцентный синий ArrowUp (Отправить).
 * - Поле пустое и не выполняется: микрофон (Голосовой ввод), пульсирующий во время записи.
 */
internal enum class ActionButtonMode {
    STOP,
    RECORDING,
    SEND,
    MIC
}

internal fun canSendMessage(hasContent: Boolean, isRunning: Boolean, isListening: Boolean): Boolean =
    hasContent && !isRunning && !isListening

internal fun resolveActionButtonMode(
    isRunning: Boolean,
    isListening: Boolean,
    canSend: Boolean
): ActionButtonMode = when {
    isRunning -> ActionButtonMode.STOP
    isListening -> ActionButtonMode.RECORDING
    canSend -> ActionButtonMode.SEND
    else -> ActionButtonMode.MIC
}

@Composable
private fun SendMicStopButton(
    isRunning: Boolean,
    canSend: Boolean,
    isListening: Boolean,
    rmsLevel: Float,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onMicClick: () -> Unit
) {
    val enabled = true
    val mode = resolveActionButtonMode(isRunning = isRunning, isListening = isListening, canSend = canSend)

    val background by animateColorAsState(
        targetValue = when (mode) {
            ActionButtonMode.STOP -> MaterialTheme.colorScheme.error.copy(alpha = 0.18f)
            ActionButtonMode.RECORDING -> MaterialTheme.colorScheme.error.copy(alpha = 0.25f)
            ActionButtonMode.SEND -> MaterialTheme.colorScheme.primary
            ActionButtonMode.MIC -> MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f)
        },
        animationSpec = MotionTokens.fluidTween(220),
        label = "send_background"
    )

    val iconTint by animateColorAsState(
        targetValue = when (mode) {
            ActionButtonMode.STOP, ActionButtonMode.RECORDING -> MaterialTheme.colorScheme.error
            ActionButtonMode.SEND -> MaterialTheme.colorScheme.onPrimary
            ActionButtonMode.MIC -> MaterialTheme.colorScheme.primary
        },
        animationSpec = MotionTokens.fluidTween(220),
        label = "send_tint"
    )

    val baseScale = when (mode) {
        ActionButtonMode.RECORDING -> 1f + (rmsLevel / 10f).coerceIn(0f, 1f) * 0.15f
        ActionButtonMode.SEND, ActionButtonMode.STOP -> 1f
        ActionButtonMode.MIC -> 0.94f
    }

    val buttonScale by animateFloatAsState(
        targetValue = baseScale,
        animationSpec = MotionTokens.fluidSpring(),
        label = "send_scale"
    )

    Box(
        modifier = Modifier
            .size(38.dp)
            .graphicsLayer {
                scaleX = buttonScale
                scaleY = buttonScale
            }
            .clip(CircleShape)
            .background(background)
            .bounceClick(enabled = enabled, scaleDown = 0.90f) {
                when (mode) {
                    ActionButtonMode.STOP -> onStop()
                    ActionButtonMode.RECORDING, ActionButtonMode.MIC -> onMicClick()
                    ActionButtonMode.SEND -> onSend()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        val icon = when (mode) {
            ActionButtonMode.STOP -> RunnerIcons.StopFilled
            ActionButtonMode.RECORDING, ActionButtonMode.MIC -> RunnerIcons.Mic
            ActionButtonMode.SEND -> RunnerIcons.ArrowUpward
        }

        val desc = when (mode) {
            ActionButtonMode.STOP -> "Остановить"
            ActionButtonMode.RECORDING -> "Остановить запись"
            ActionButtonMode.SEND -> "Отправить"
            ActionButtonMode.MIC -> "Голосовой ввод"
        }

        Icon(
            imageVector = icon,
            contentDescription = desc,
            tint = iconTint,
            modifier = Modifier.size(19.dp)
        )
    }
}
