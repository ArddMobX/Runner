package com.runner.app.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Иконки в стиле Lucide: тонкий штрих 2.2, скруглённые концы.
 *
 * Нарисованы вручную, а не взяты из material-icons: во-первых, там нет
 * один-в-один Lucide ArrowUp, во-вторых, свои пути не зависят от версии
 * библиотеки иконок. Цвет задаётся через tint у Icon.
 */
object RunnerIcons {

    /** Lucide arrow-up: вертикальная линия и шеврон сверху. */
    val ArrowUp: ImageVector by lazy {
        ImageVector.Builder(
            name = "ArrowUp",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2.2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 19f)
                lineTo(12f, 5f)
                moveTo(5f, 12f)
                lineTo(12f, 5f)
                lineTo(19f, 12f)
            }
        }.build()
    }

    /** Скруглённый квадрат — прерывание генерации. */
    val StopSquare: ImageVector by lazy {
        ImageVector.Builder(
            name = "StopSquare",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(9.2f, 7f)
                lineTo(14.8f, 7f)
                quadTo(17f, 7f, 17f, 9.2f)
                lineTo(17f, 14.8f)
                quadTo(17f, 17f, 14.8f, 17f)
                lineTo(9.2f, 17f)
                quadTo(7f, 17f, 7f, 14.8f)
                lineTo(7f, 9.2f)
                quadTo(7f, 7f, 9.2f, 7f)
                close()
            }
        }.build()
    }

    /** Lucide activity / pulse line — пинг и проверка соединения. */
    val Activity: ImageVector by lazy {
        ImageVector.Builder(
            name = "Activity",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2.2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(22f, 12f)
                lineTo(18f, 12f)
                lineTo(15f, 21f)
                lineTo(9f, 3f)
                lineTo(6f, 12f)
                lineTo(2f, 12f)
            }
        }.build()
    }
}
