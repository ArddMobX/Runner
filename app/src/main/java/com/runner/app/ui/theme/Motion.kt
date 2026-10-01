package com.runner.app.ui.theme

import androidx.compose.animation.core.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput

object MotionTokens {
    // Apple-inspired fluid easing curve
    val FluidCurve = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

    // Spring physics: moderate stiffness (~220) and high damping (~28 / 0.85 ratio)
    const val SpringStiffness = 220f
    const val SpringDampingRatio = 0.86f

    const val DurationNormal = 400
    const val DurationFast = 250

    fun <T> fluidSpring(): SpringSpec<T> = spring(
        dampingRatio = SpringDampingRatio,
        stiffness = SpringStiffness
    )

    fun <T> fluidTween(durationMillis: Int = DurationNormal): TweenSpec<T> = tween(
        durationMillis = durationMillis,
        easing = FluidCurve
    )
}

/**
 * Adds an iOS-style physical press bounce effect (scales down to 0.965 on press, spring returns on release).
 * Uses rememberUpdatedState to ensure the latest onClick and enabled values are always invoked.
 */
fun Modifier.bounceClick(
    enabled: Boolean = true,
    scaleDown: Float = 0.965f,
    onClick: () -> Unit
): Modifier = composed {
    val currentOnClick by rememberUpdatedState(onClick)
    val currentEnabled by rememberUpdatedState(enabled)
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed && currentEnabled) scaleDown else 1f,
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

