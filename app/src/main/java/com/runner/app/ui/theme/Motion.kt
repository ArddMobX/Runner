package com.runner.app.ui.theme

import androidx.compose.animation.core.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Глобальный выключатель движения. Значение приходит из настроек и
 * применяется ко всем экранам, включая оверлеи.
 */
object MotionSettings {
    var enabled by mutableStateOf(true)
}

/** Синхронизирует глобальный выключатель с настройкой приложения. */
@Composable
fun ApplyMotionSetting(enabled: Boolean) {
    SideEffect { MotionSettings.enabled = enabled }
}

object MotionTokens {
    /** Материальный emphasized-decelerate: быстрый старт и мягкий хвост. */
    val FluidCurve = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    // Пружина без заметного перелёта. Карточки и шторки не «пружинят».
    const val SpringStiffness = 300f
    const val SpringDampingRatio = 0.9f

    const val DurationMicro = 120
    const val DurationFast = 180
    const val DurationNormal = 280
    const val DurationSlow = 360

    fun <T> fluidSpring(): FiniteAnimationSpec<T> = if (MotionSettings.enabled) {
        spring(
            dampingRatio = SpringDampingRatio,
            stiffness = SpringStiffness
        )
    } else {
        snap()
    }

    fun <T> fluidTween(durationMillis: Int = DurationNormal): FiniteAnimationSpec<T> =
        if (MotionSettings.enabled) {
            tween(
                durationMillis = durationMillis,
                easing = FluidCurve
            )
        } else {
            snap()
        }
}

/**
 * Мягкое нажатие: лёгкое уменьшение и возврат пружиной.
 * При выключенных анимациях масштаб не меняется вообще.
 */
fun Modifier.bounceClick(
    enabled: Boolean = true,
    scaleDown: Float = 0.975f,
    onClick: () -> Unit
): Modifier = composed {
    val currentOnClick by rememberUpdatedState(onClick)
    val currentEnabled by rememberUpdatedState(enabled)
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed && currentEnabled && MotionSettings.enabled) {
            scaleDown
        } else {
            1f
        },
        animationSpec = MotionTokens.fluidSpring(),
        label = "bounce_scale"
    )

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .pointerInput(currentEnabled) {
            if (!currentEnabled) return@pointerInput
            detectTapGestures(
                onPress = {
                    isPressed = true
                    tryAwaitRelease()
                    isPressed = false
                },
                onTap = {
                    if (currentEnabled) {
                        currentOnClick()
                    }
                }
            )
        }
}
