package com.runner.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Единая сетка отступов. В интерфейсе используем только эти значения,
 * чтобы экраны не разъезжались на 13, 14 и 15 dp в соседних блоках.
 */
object Spacing {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}

/**
 * Шкала форм. Три рабочих радиуса плюс шторка и пилюля.
 * Всё остальное в UI должно ссылаться сюда, а не на случайные 9/11/13/18.
 */
object RunnerShapes {
    val tiny = RoundedCornerShape(6.dp)
    val small = RoundedCornerShape(8.dp)
    val medium = RoundedCornerShape(12.dp)
    val large = RoundedCornerShape(16.dp)
    val extraLarge = RoundedCornerShape(24.dp)

    /** Нижняя шторка: скругляем только верх. */
    val sheet = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)

    /** Пузырь пользователя: три угла большие, нижний правый почти прямой. */
    val userBubble = RoundedCornerShape(
        topStart = 16.dp,
        topEnd = 16.dp,
        bottomStart = 16.dp,
        bottomEnd = 4.dp
    )

    /** Круглые кнопки и пилюли. */
    val pill = RoundedCornerShape(50)
}

/**
 * Рабочая типографическая шкала. Display-размеры остаются в Typography,
 * а здесь только то, что реально встречается в карточках и настройках.
 */
object TypeScale {
    val micro = 11.sp
    val caption = 12.sp
    val label = 13.sp
    val body = 14.sp
    val bodyLarge = 15.sp
    val title = 16.sp
    val headline = 20.sp
}
