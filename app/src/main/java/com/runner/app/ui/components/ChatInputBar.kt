package com.runner.app.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.runner.app.data.SelectedFile
import com.runner.app.ui.commands.SlashCommand
import com.runner.app.ui.commands.SlashCommandParser
import com.runner.app.ui.commands.SlashCommands
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import com.runner.app.ui.theme.MotionTokens
import com.runner.app.ui.theme.RunnerShapes
import com.runner.app.ui.theme.Spacing
import com.runner.app.ui.theme.TypeScale
import com.runner.app.ui.theme.accessibleMinHeight
import com.runner.app.ui.theme.accessibleTouchSize
import com.runner.app.ui.theme.bounceClick
import com.runner.app.util.DocumentExtractor
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

    /** Документы: PDF, DOCX, таблицы, текст и код. */
    var selectedFiles by mutableStateOf<List<SelectedFile>>(emptyList())

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

    fun addFiles(files: List<SelectedFile>) {
        selectedFiles = (selectedFiles + files).distinctBy { it.uri }.take(5)
    }

    fun removeFile(file: SelectedFile) {
        selectedFiles = selectedFiles.filter { it.uri != file.uri }
    }

    fun clear() {
        textFieldValue = TextFieldValue("")
        selectedImages = emptyList()
        selectedFiles = emptyList()
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
    onSend: (String, List<Uri>, List<SelectedFile>) -> Unit,
    onStop: () -> Unit,
    /** Встроенные и свои команды. Свои приходят из Room и меняются на лету. */
    commands: List<SlashCommand> = SlashCommands.all,
    /** Возвращает true, если команда выполнена и текст можно очистить. */
    onCommand: ((SlashCommand, String) -> Boolean)? = null,
    onStartListening: (() -> Unit)? = null,
    onStopListening: (() -> Unit)? = null,
    placeholder: String? = null,
    isLiveMode: Boolean = false,
    /** Внешнее состояние кнопки «Думать» (при синхронизации с Reasoning Effort модели). */
    thinkActiveOverride: Boolean? = null,
    onToggleThinkOverride: (() -> Unit)? = null,
    onLongClickThink: (() -> Unit)? = null,
    thinkMenu: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val textValue = inputState.textFieldValue
    val rawText = textValue.text
    val selectedImages = inputState.selectedImages
    val selectedFiles = inputState.selectedFiles
    val hasContent = rawText.isNotBlank() ||
            selectedImages.isNotEmpty() ||
            selectedFiles.isNotEmpty()
    val canSend = canSendMessage(hasContent = hasContent, isRunning = isRunning, isListening = isListening)
    val canSubmit = canSubmitOnEnter(
        rawText = rawText,
        hasContent = hasContent,
        isRunning = isRunning,
        isListening = isListening,
        hasCommandHandler = onCommand != null,
        commands = commands
    )
    var isFocused by remember { mutableStateOf(false) }
    val isActive = isFocused || hasContent || isListening
    val paletteCommands = if (onCommand == null) {
        emptyList()
    } else {
        SlashCommandParser.paletteMatches(rawText, commands)
    }

    var showAttachMenu by remember { mutableStateOf(false) }
    var currentCameraUriString by rememberSaveable { mutableStateOf<String?>(null) }
    var currentCameraPath by rememberSaveable { mutableStateOf<String?>(null) }
    val attachAction = {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        showAttachMenu = true
    }

    // Лаунчер галереи (фото): выбор существующих изображений
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            inputState.addImages(uris)
        }
    }

    // Документы: системный пикер отдаёт content:// URI. Сразу проверяем
    // формат и размер, чтобы не тащить в чат заведомо нечитаемый файл.
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        val accepted = mutableListOf<SelectedFile>()
        var rejected = 0
        uris.forEach { uri ->
            val file = DocumentExtractor.readMeta(context, uri) ?: return@forEach
            val tooBig = file.sizeBytes > DocumentExtractor.MAX_FILE_BYTES
            if (tooBig || !DocumentExtractor.isSupported(file.name, file.mime)) {
                rejected++
            } else {
                accepted += file
            }
        }
        if (accepted.isNotEmpty()) {
            inputState.addFiles(accepted)
        }
        if (rejected > 0) {
            Toast.makeText(
                context,
                "Пропущено файлов: $rejected. Поддерживаются PDF, DOCX, XLSX, PPTX, текст и код до 25 МБ.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // Лаунчер камеры: запускает системную камеру и сохраняет результат во временный файл
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val savedUri = currentCameraUriString?.let { runCatching { Uri.parse(it) }.getOrNull() }
        val savedFile = currentCameraPath?.let { File(it) }
        if (success && savedUri != null) {
            if (savedFile == null || (savedFile.exists() && savedFile.length() > 0L)) {
                inputState.addImages(listOf(savedUri))
            } else {
                savedFile.delete()
            }
        } else {
            savedFile?.delete()
        }
        currentCameraUriString = null
        currentCameraPath = null
    }

    fun launchCamera() {
        var photoFile: File? = null
        try {
            val cameraDir = File(context.cacheDir, "camera").apply { mkdirs() }
            photoFile = File(cameraDir, "photo_${System.currentTimeMillis()}.jpg")
            val authority = "${context.packageName}.fileprovider"
            val uri = FileProvider.getUriForFile(context, authority, photoFile)
            currentCameraUriString = uri.toString()
            currentCameraPath = photoFile.absolutePath
            cameraLauncher.launch(uri)
        } catch (e: Exception) {
            photoFile?.delete()
            currentCameraUriString = null
            currentCameraPath = null
            Toast.makeText(context, "Не удалось открыть камеру: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            onStartListening?.invoke()
        } else {
            Toast.makeText(context, "Для голосового ввода нужен доступ к микрофону", Toast.LENGTH_SHORT).show()
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
        animationSpec = MotionTokens.effectsFast(),
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
        if (command.takesArgs) {
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
        val files = inputState.selectedFiles

        // Команда перехватывается до отправки. Неизвестный слэш остаётся
        // обычным текстом, чтобы пути вида /storage/... не ломались.
        if (onCommand != null && trimmed.startsWith("/")) {
            val parsed = SlashCommandParser.parse(trimmed, commands)
            if (parsed != null) {
                executeCommand(parsed.command, parsed.args)
                return
            }

            // Неполное имя без пробелов: Enter не отправляет его модели.
            // Если подходит одна команда, выполняем её, иначе просим уточнить.
            if (trimmed.none { it.isWhitespace() }) {
                val partial = SlashCommandParser.paletteMatches(trimmed, commands)
                if (partial.size == 1) {
                    executeCommand(partial.first(), "")
                    return
                }
                if (partial.size > 1) {
                    Toast.makeText(
                        context,
                        "Уточните команду или выберите её из списка",
                        Toast.LENGTH_SHORT
                    ).show()
                    return
                }
            }
        }

        if ((trimmed.isNotEmpty() || images.isNotEmpty() || files.isNotEmpty()) && !isRunning) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            inputState.clear()
            onSend(trimmed, images, files)
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
        AnimatedVisibility(
            visible = selectedImages.isNotEmpty(),
            enter = MotionTokens.expandSectionEnter(expandFrom = Alignment.Bottom),
            exit = MotionTokens.expandSectionExit(shrinkTowards = Alignment.Bottom)
        ) {
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

        AnimatedVisibility(
            visible = selectedFiles.isNotEmpty(),
            enter = MotionTokens.expandSectionEnter(expandFrom = Alignment.Bottom),
            exit = MotionTokens.expandSectionExit(shrinkTowards = Alignment.Bottom)
        ) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp, start = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(selectedFiles, key = { it.uri.toString() }) { file ->
                    SelectedFileChip(
                        file = file,
                        onRemove = { inputState.removeFile(file) }
                    )
                }
            }
        }

        var lastPaletteCommands by remember { mutableStateOf(paletteCommands) }
        if (paletteCommands.isNotEmpty()) {
            lastPaletteCommands = paletteCommands
        }
        AnimatedVisibility(
            visible = paletteCommands.isNotEmpty(),
            enter = MotionTokens.expandSectionEnter(expandFrom = Alignment.Bottom),
            exit = MotionTokens.expandSectionExit(shrinkTowards = Alignment.Bottom)
        ) {
            SlashCommandPalette(
                commands = lastPaletteCommands,
                isRunning = isRunning,
                onPick = { pickPaletteCommand(it) },
                modifier = Modifier.padding(bottom = 7.dp)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RunnerShapes.extraLarge)
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .border(
                    BorderStroke(if (isLiveMode) 1.dp else 0.8.dp, borderColor),
                    RunnerShapes.extraLarge
                )
                .padding(horizontal = 6.dp, vertical = 5.dp)
        ) {
            InputModeChips(
                webSearch = inputState.webSearch,
                think = thinkActiveOverride ?: inputState.think,
                enabled = !isRunning,
                onToggleSearch = { inputState.webSearch = !inputState.webSearch },
                onToggleThink = {
                    if (onToggleThinkOverride != null) {
                        onToggleThinkOverride()
                    } else {
                        inputState.think = !inputState.think
                    }
                },
                onLongClickThink = onLongClickThink,
                thinkMenu = thinkMenu
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = if (textValue.text.contains('\n')) Alignment.Bottom else Alignment.CenterVertically
            ) {
            // Кнопка «+»: файл, камера или галерея.
            // Идеально сбалансирована по центру, размеру (38dp) и форме с кнопкой микрофона/отправки.
            Box(
                modifier = Modifier.size(accessibleTouchSize(38.dp)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(accessibleTouchSize(38.dp))
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f))
                        .semantics(mergeDescendants = true) {
                            role = Role.Button
                            onClick(label = "Прикрепить файл") {
                                attachAction()
                                true
                            }
                        }
                        .bounceClick(enabled = !isRunning, scaleDown = 0.90f) {
                            attachAction()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = RunnerIcons.Add,
                        contentDescription = "Прикрепить файл, фото или сделать снимок",
                        tint = if (!isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                        modifier = Modifier.size(20.dp)
                    )
                }

                DropdownMenu(
                    expanded = showAttachMenu,
                    onDismissRequest = { showAttachMenu = false },
                    modifier = Modifier.clip(RunnerShapes.large)
                ) {
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text("Файл", fontSize = TypeScale.body)
                                Text(
                                    text = "PDF, DOCX, XLSX, PPTX, текст и код",
                                    fontSize = TypeScale.caption,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = RunnerIcons.Article,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            showAttachMenu = false
                            filePickerLauncher.launch(arrayOf("*/*"))
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Камера", fontSize = TypeScale.body) },
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
                        text = { Text("Галерея", fontSize = TypeScale.body) },
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
                            if (canSubmit) {
                                handleSend()
                            }
                            true
                        } else {
                            false
                        }
                    },
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = TypeScale.bodyLarge,
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
                        if (sendOnEnter && canSubmit) {
                            handleSend()
                        }
                    }
                ),
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (rawText.isEmpty()) {
                            Text(
                                text = if (isListening) "Слушаю речь…" else (placeholder ?: "Задать задачу или сказать голосом…"),
                                color = if (isListening) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                fontSize = TypeScale.bodyLarge,
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
    onToggleThink: () -> Unit,
    onLongClickThink: (() -> Unit)? = null,
    thinkMenu: (@Composable () -> Unit)? = null
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
        Box {
            InputModeChip(
                label = "Думать",
                icon = RunnerIcons.Lightbulb,
                active = think,
                enabled = enabled,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onToggleThink()
                },
                onLongClick = onLongClickThink?.let { handler ->
                    {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        handler()
                    }
                }
            )
            thinkMenu?.invoke()
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun InputModeChip(
    label: String,
    icon: ImageVector,
    active: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    val shape = RunnerShapes.medium
    val background by animateColorAsState(
        targetValue = when {
            !enabled -> MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.3f)
            active -> MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
            else -> MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f)
        },
        animationSpec = MotionTokens.effectsFast(),
        label = "mode_chip_background"
    )
    val contentColor by animateColorAsState(
        targetValue = when {
            !enabled -> MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
            active -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = MotionTokens.effectsFast(),
        label = "mode_chip_content"
    )
    Row(
        modifier = Modifier
            .heightIn(min = accessibleMinHeight())
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
            .semantics(mergeDescendants = true) { role = Role.Button }
            .then(
                if (onLongClick != null) {
                    Modifier.combinedClickable(
                        enabled = enabled,
                        onClick = onClick,
                        onLongClick = onLongClick
                    )
                } else {
                    Modifier.clickable(enabled = enabled, onClick = onClick)
                }
            )
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
            fontSize = TypeScale.caption,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium
        )
    }
}

/** Компактная плашка выбранного документа с крестиком. */
@Composable
private fun SelectedFileChip(
    file: SelectedFile,
    onRemove: () -> Unit
) {
    val extension = file.name.substringAfterLast('.', "").lowercase()
    Row(
        modifier = Modifier
            .clip(RunnerShapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(
                BorderStroke(
                    0.6.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                ),
                RunnerShapes.medium
            )
            .padding(start = 7.dp, end = 3.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AttachmentBadge(name = file.name, size = 32.dp)
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = file.name,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = TypeScale.label,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 150.dp)
            )
            Text(
                text = attachmentTypeName(extension) + " · " +
                        DocumentExtractor.formatSize(file.sizeBytes),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = TypeScale.micro,
                maxLines = 1
            )
        }
        Spacer(modifier = Modifier.width(3.dp))
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .semantics(mergeDescendants = true) { role = Role.Button }
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = RunnerIcons.Close,
                contentDescription = "Убрать файл",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

private sealed interface SelectedImageLoadState {
    data object Loading : SelectedImageLoadState
    data class Ready(val bitmap: ImageBitmap) : SelectedImageLoadState
    data object Failed : SelectedImageLoadState
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
    val imageState = produceState<SelectedImageLoadState>(
        initialValue = SelectedImageLoadState.Loading,
        key1 = uri
    ) {
        value = withContext(Dispatchers.IO) {
            try {
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, options)
                }
                if (options.outWidth <= 0 || options.outHeight <= 0) {
                    return@withContext SelectedImageLoadState.Failed
                }
                var sampleSize = 1
                while (options.outWidth / sampleSize > 250 || options.outHeight / sampleSize > 250) {
                    sampleSize *= 2
                }
                options.inJustDecodeBounds = false
                options.inSampleSize = sampleSize
                val decoded = context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, options)?.asImageBitmap()
                }
                if (decoded != null) {
                    SelectedImageLoadState.Ready(decoded)
                } else {
                    SelectedImageLoadState.Failed
                }
            } catch (_: Exception) {
                SelectedImageLoadState.Failed
            }
        }
    }

    Box(
        modifier = Modifier
            .size(58.dp)
            .clip(RunnerShapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        when (val state = imageState.value) {
            is SelectedImageLoadState.Ready -> {
                Image(
                    bitmap = state.bitmap,
                    contentDescription = "Прикрепленное фото",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            SelectedImageLoadState.Loading -> {
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
            SelectedImageLoadState.Failed -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = RunnerIcons.BrokenImage,
                        contentDescription = "Изображение недоступно",
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(3.dp)
                .size(18.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.65f))
                .semantics(mergeDescendants = true) {
                    role = Role.Button
                    onClick(label = "Удалить изображение") {
                        onRemove()
                        true
                    }
                }
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

/**
 * Определяет, можно ли выполнить действие по нажатию Enter / IME Send.
 * Во время работы агента разрешаем slash-команды с [SlashCommand.availableWhileRunning]
 * (например, `/stop` и `/help`), не разблокируя отправку обычных сообщений.
 */
internal fun canSubmitOnEnter(
    rawText: String,
    hasContent: Boolean,
    isRunning: Boolean,
    isListening: Boolean,
    hasCommandHandler: Boolean,
    commands: List<SlashCommand> = SlashCommands.all
): Boolean {
    if (canSendMessage(hasContent = hasContent, isRunning = isRunning, isListening = isListening)) {
        return true
    }
    if (!isRunning || isListening || !hasCommandHandler) return false
    val trimmed = rawText.trim()
    if (!trimmed.startsWith("/")) return false
    val parsed = SlashCommandParser.parse(trimmed, commands)
    if (parsed != null) return parsed.command.availableWhileRunning
    if (trimmed.none { it.isWhitespace() }) {
        val matches = SlashCommandParser.paletteMatches(trimmed, commands)
        return matches.size == 1 && matches.first().availableWhileRunning
    }
    return false
}

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
        animationSpec = MotionTokens.effectsFast(),
        label = "send_background"
    )

    val iconTint by animateColorAsState(
        targetValue = when (mode) {
            ActionButtonMode.STOP, ActionButtonMode.RECORDING -> MaterialTheme.colorScheme.error
            ActionButtonMode.SEND -> MaterialTheme.colorScheme.onPrimary
            ActionButtonMode.MIC -> MaterialTheme.colorScheme.primary
        },
        animationSpec = MotionTokens.effectsFast(),
        label = "send_tint"
    )

    val baseScale = when (mode) {
        ActionButtonMode.RECORDING -> 1f + (rmsLevel / 10f).coerceIn(0f, 1f) * 0.15f
        ActionButtonMode.SEND, ActionButtonMode.STOP -> 1f
        ActionButtonMode.MIC -> 0.94f
    }

    val buttonScale by animateFloatAsState(
        targetValue = baseScale,
        animationSpec = MotionTokens.spatialFast(),
        label = "send_scale"
    )

    val actionLabel = when (mode) {
        ActionButtonMode.STOP -> "Остановить"
        ActionButtonMode.RECORDING -> "Остановить запись"
        ActionButtonMode.SEND -> "Отправить"
        ActionButtonMode.MIC -> "Голосовой ввод"
    }
    fun activate() {
        when (mode) {
            ActionButtonMode.STOP -> onStop()
            ActionButtonMode.RECORDING, ActionButtonMode.MIC -> onMicClick()
            ActionButtonMode.SEND -> onSend()
        }
    }

    Box(
        modifier = Modifier
            .size(accessibleTouchSize(38.dp))
            .graphicsLayer {
                scaleX = buttonScale
                scaleY = buttonScale
            }
            .semantics(mergeDescendants = true) {
                role = Role.Button
                onClick(label = actionLabel) {
                    activate()
                    true
                }
            }
            .clip(CircleShape)
            .background(background)
            .bounceClick(enabled = enabled, scaleDown = 0.90f) {
                activate()
            },
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = mode,
            transitionSpec = { MotionTokens.iconSwapTransition() },
            label = "send_action_icon"
        ) { targetMode ->
            val icon = when (targetMode) {
                ActionButtonMode.STOP -> RunnerIcons.StopFilled
                ActionButtonMode.RECORDING, ActionButtonMode.MIC -> RunnerIcons.Mic
                ActionButtonMode.SEND -> RunnerIcons.ArrowUpward
            }
            Icon(
                imageVector = icon,
                contentDescription = actionLabel,
                tint = iconTint,
                modifier = Modifier.size(19.dp)
            )
        }
    }
}
