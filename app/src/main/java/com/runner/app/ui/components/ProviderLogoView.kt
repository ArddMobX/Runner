package com.runner.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.runner.app.ui.theme.RobotoMono

/**
 * Отображает фирменный векторный логотип бренда, а для редких/кастомных вендоров —
 * аккуратную типографическую монограмму (1–2 символа) вместо одинакового робота.
 */
@Composable
fun BrandLogoOrMonogram(
    logo: ImageVector?,
    monogram: String,
    tint: Color,
    iconSize: Dp = 16.dp,
    fontSize: TextUnit = 10.sp,
    modifier: Modifier = Modifier
) {
    if (logo != null) {
        Icon(
            imageVector = logo,
            contentDescription = null,
            tint = tint,
            modifier = modifier.size(iconSize)
        )
    } else {
        Box(
            modifier = modifier
                .height(iconSize)
                .widthIn(min = iconSize),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = monogram.ifBlank { "AI" },
                color = tint,
                fontSize = fontSize,
                lineHeight = fontSize,
                letterSpacing = (-0.3).sp,
                fontFamily = RobotoMono,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

/**
 * Минималистичный индикатор уровня размышлений (Effort) из 3 вертикальных полосок в стиле Codex.
 *
 * @param activeBars сколько полосок заполнено (0..3; для xhigh/max все 3 полные с акцентом).
 */
@Composable
fun EffortLevelBars(
    activeBars: Int,
    modifier: Modifier = Modifier,
    activeColor: Color = MaterialTheme.colorScheme.onSurface,
    inactiveColor: Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
) {
    val heights = listOf(4.dp, 7.dp, 10.dp)
    val filled = activeBars.coerceIn(0, heights.size)
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(1.5.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        heights.forEachIndexed { index, barHeight ->
            Box(
                modifier = Modifier
                    .width(2.2.dp)
                    .height(barHeight)
                    .clip(RoundedCornerShape(1.dp))
                    .background(if (index < filled) activeColor else inactiveColor)
            )
        }
    }
}
