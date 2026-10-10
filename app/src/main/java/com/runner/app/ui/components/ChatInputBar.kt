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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.runner.app.ui.theme.MotionTokens
import com.runner.app.ui.theme.bounceClick
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    onSend: (String, List<Uri>) -> Unit,
    onStop: () -> Unit,
    onStartListening: (() -> Unit)? = null,
    onStopListening: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val textValue = inputState.textFieldValue
    val rawText = textValue.text
    val selectedImages = inputState.selectedImages
    val hasContent = rawText.isNotBlank() || selectedImages.isNotEmpty()
    val canSend = hasContent && !isRunning
    var isFocused by remember { mutableStateOf(false) }
    val isActive = isFocused || hasContent || isListening

    // Лаунчер галереи (фото): по нажатию на «+» сразу открывается системная галерея
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            inputState.addImages(uris)
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
            else -> MaterialTheme.colorScheme.outlineVariant
        },
        animationSpec = MotionTokens.fluidTween(200),
        label = "input_border"
    )

    fun handleSend() {
        val trimmed = inputState.text.trim()
        val images = inputState.selectedImages
        if ((trimmed.isNotEmpty() || images.isNotEmpty()) && !isRunning) {
            inputState.clear()
            onSend(trimmed, images)
        }
    }

    fun handleMicClick() {
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

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .border(BorderStroke(1.dp, borderColor), RoundedCornerShape(26.dp))
                .padding(horizontal = 6.dp, vertical = 5.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            // Кнопка «+»: по тапу сразу открывает системную галерею для картинок.
            // Геометрически выровнена по размеру (38dp) и центру со строкой ввода и кнопкой микрофона.
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .bounceClick(enabled = !isRunning, scaleDown = 0.90f) {
                        imagePickerLauncher.launch("image/*")
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = "Прикрепить фото",
                    tint = if (!isRunning) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                    modifier = Modifier.size(22.dp)
                )
            }

            BasicTextField(
                value = textValue,
                onValueChange = { inputState.textFieldValue = it },
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)
                    .onFocusChanged { isFocused = it.isFocused },
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
                    imeAction = ImeAction.None
                ),
                keyboardActions = KeyboardActions.Default,
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (rawText.isEmpty()) {
                            Text(
                                text = if (isListening) "Слушаю речь..." else "Задать задачу",
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
                onStop = onStop,
                onMicClick = { handleMicClick() }
            )
        }
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
                imageVector = Icons.Outlined.Close,
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

    val background by animateColorAsState(
        targetValue = when {
            isRunning -> MaterialTheme.colorScheme.error.copy(alpha = 0.18f)
            canSend -> MaterialTheme.colorScheme.primary
            isListening -> MaterialTheme.colorScheme.error.copy(alpha = 0.25f)
            else -> MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f)
        },
        animationSpec = MotionTokens.fluidTween(220),
        label = "send_background"
    )

    val iconTint by animateColorAsState(
        targetValue = when {
            isRunning -> MaterialTheme.colorScheme.error
            canSend -> MaterialTheme.colorScheme.onPrimary
            isListening -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.outline
        },
        animationSpec = MotionTokens.fluidTween(220),
        label = "send_tint"
    )

    val baseScale = when {
        isListening -> 1f + (rmsLevel / 10f).coerceIn(0f, 1f) * 0.15f
        canSend || isRunning -> 1f
        else -> 0.94f
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
                when {
                    isRunning -> onStop()
                    canSend -> onSend()
                    else -> onMicClick()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        val icon = when {
            isRunning -> RunnerIcons.StopSquare
            canSend -> RunnerIcons.ArrowUp
            else -> Icons.Outlined.Mic
        }

        val desc = when {
            isRunning -> "Остановить"
            canSend -> "Отправить"
            isListening -> "Остановить запись"
            else -> "Голосовой ввод"
        }

        Icon(
            imageVector = icon,
            contentDescription = desc,
            tint = iconTint,
            modifier = Modifier.size(19.dp)
        )
    }
}
