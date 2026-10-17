package com.runner.app.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.runner.app.ui.theme.MotionTokens
import com.runner.app.ui.theme.SecondaryMint

/**
 * Деликатная пульсирующая точка статуса (status pulsing dot) с фирменным акцентом Runner (Mint / Ice Blue).
 * Выполняется аппаратно через graphicsLayer (нулевой оверхед на рекомпозицию).
 */
@Composable
fun StatusPulsingDot(
    modifier: Modifier = Modifier,
    color: Color = SecondaryMint,
    dotSize: Dp = 6.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "status_dot_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 2.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_scale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_alpha"
    )

    Box(
        modifier = modifier.size(16.dp),
        contentAlignment = Alignment.Center
    ) {
        // Внешняя расходящаяся волна
        Box(
            modifier = Modifier
                .size(dotSize)
                .graphicsLayer {
                    scaleX = pulseScale
                    scaleY = pulseScale
                    alpha = pulseAlpha
                }
                .background(color, CircleShape)
        )
        // Центральное ядро индикатора
        Box(
            modifier = Modifier
                .size(dotSize)
                .background(color, CircleShape)
        )
    }
}

/**
 * Живая плашка статуса агента с фиксированной высотой и плавным crossfade текста.
 */
@Composable
fun AgentStatusIndicator(
    status: String?,
    modifier: Modifier = Modifier,
    dotColor: Color = SecondaryMint,
    dotSize: Dp = 6.dp,
    height: Dp = 28.dp,
    horizontalPadding: Dp = 20.dp
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .padding(horizontal = horizontalPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatusPulsingDot(color = dotColor, dotSize = dotSize)
        Spacer(modifier = Modifier.width(8.dp))
        Crossfade(
            targetState = if (status.isNullOrBlank()) "Работаю" else status,
            animationSpec = tween(
                durationMillis = MotionTokens.DurationFast,
                easing = MotionTokens.FluidCurve
            ),
            label = "status_text_crossfade",
            modifier = Modifier.weight(1f)
        ) { text ->
            Text(
                text = text,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
