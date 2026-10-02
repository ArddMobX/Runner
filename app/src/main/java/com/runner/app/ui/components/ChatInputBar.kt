package com.runner.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.MaterialTheme
import com.runner.app.ui.theme.MotionTokens
import com.runner.app.ui.theme.bounceClick

/**
 * Изолированное состояние поля ввода.
 *
 * Хранит TextFieldValue локально, чтобы изменения текста не вызывали
 * рекомпозицию родительского экрана чата или списка сообщений при каждом нажатии клавиши.
 */
@Stable
class ChatInputState(initialText: String = "") {
    var textFieldValue by mutableStateOf(
        TextFieldValue(
            text = initialText,
            selection = TextRange(initialText.length)
        )
    )

    val text: String
        get() = textFieldValue.text

    fun setText(newText: String) {
        textFieldValue = TextFieldValue(
            text = newText,
            selection = TextRange(newText.length)
        )
    }

    fun clear() {
        textFieldValue = TextFieldValue("")
    }
}

@Composable
fun rememberChatInputState(initialText: String = ""): ChatInputState =
    remember { ChatInputState(initialText) }

/**
 * Полностью изолированный компонент панели ввода сообщений.
 *
 * 1. Локальный стейт ввода (не триггерит ререндер списка сообщений и родительского экрана).
 * 2. Многострочный ввод: Enter переносит строку (до 5 строк со скроллом),
 *    отправка — только кнопкой-стрелкой справа, imeAction = None.
 * 3. Мгновенная синхронная обработка символов без дебаунсов, замеров и блокировок UI.
 */
@Composable
fun ChatInputBar(
    inputState: ChatInputState,
    isRunning: Boolean,
    onSend: (String) -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val textValue = inputState.textFieldValue
    val rawText = textValue.text
    val canSend = rawText.isNotBlank() && !isRunning
    var isFocused by remember { mutableStateOf(false) }
    val isActive = isFocused || rawText.isNotBlank()

    val borderColor by animateColorAsState(
        targetValue = when {
            isRunning -> MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
            isActive -> MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
            else -> MaterialTheme.colorScheme.outlineVariant
        },
        animationSpec = MotionTokens.fluidTween(200),
        label = "input_border"
    )

    fun handleSend() {
        val trimmed = inputState.text.trim()
        if (trimmed.isNotEmpty() && !isRunning) {
            inputState.clear()
            onSend(trimmed)
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 12.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(BorderStroke(1.dp, borderColor), RoundedCornerShape(24.dp))
            .padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        BasicTextField(
            value = textValue,
            onValueChange = { inputState.textFieldValue = it },
            modifier = Modifier
                .weight(1f)
                .padding(start = 14.dp, end = 6.dp, top = 11.dp, bottom = 11.dp)
                .onFocusChanged { isFocused = it.isFocused },
            textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp, lineHeight = 21.sp),
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
                Box {
                    if (rawText.isEmpty()) {
                        Text(
                            text = "Задать задачу",
                            color = MaterialTheme.colorScheme.outline,
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
            onSend = { handleSend() },
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
            isRunning -> MaterialTheme.colorScheme.error.copy(alpha = 0.18f)
            canSend -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f)
        },
        animationSpec = MotionTokens.fluidTween(220),
        label = "send_background"
    )

    val iconTint by animateColorAsState(
        targetValue = when {
            isRunning -> MaterialTheme.colorScheme.error
            canSend -> MaterialTheme.colorScheme.onPrimary
            else -> MaterialTheme.colorScheme.outline
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
