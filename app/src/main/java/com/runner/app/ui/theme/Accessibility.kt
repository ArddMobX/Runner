package com.runner.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Улучшенная доступность: крупные зоны нажатия и дополнительные
 * подсказки для TalkBack. Базовые подписи элементов работают всегда,
 * этот переключатель отвечает за усиленный режим.
 */
object AccessibilitySettings {
    var enhanced by mutableStateOf(false)
}

/** Синхронизирует усиленный режим с настройкой приложения. */
@Composable
fun ApplyAccessibilitySetting(enabled: Boolean) {
    SideEffect { AccessibilitySettings.enhanced = enabled }
}

/** Размер интерактивной зоны: не меньше 48 dp в усиленном режиме. */
fun accessibleTouchSize(normal: Dp): Dp =
    if (AccessibilitySettings.enhanced) maxOf(normal, 48.dp) else normal

/** Минимальная высота строки или чипа в усиленном режиме. */
fun accessibleMinHeight(): Dp = if (AccessibilitySettings.enhanced) 48.dp else 0.dp
