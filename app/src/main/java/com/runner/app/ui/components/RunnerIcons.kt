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
            }
        }.build()
    }

    /** Lucide code < > — окно исходного кода. */
    val Code: ImageVector by lazy {
        ImageVector.Builder(
            name = "Code",
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
                moveTo(16f, 18f)
                lineTo(22f, 12f)
                lineTo(16f, 6f)
                moveTo(8f, 6f)
                lineTo(2f, 12f)
                lineTo(8f, 18f)
            }
        }.build()
    }

    /** TON (The Open Network) crystal logo. */
    val Ton: ImageVector by lazy {
        ImageVector.Builder(
            name = "Ton",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(12.0f, 3.0f)
                lineTo(3.8f, 7.8f)
                lineTo(12.0f, 21.0f)
                lineTo(20.2f, 7.8f)
                close()
                moveTo(12.0f, 5.2f)
                lineTo(18.0f, 8.7f)
                lineTo(12.0f, 12.2f)
                lineTo(6.0f, 8.7f)
                close()
                moveTo(5.3f, 10.2f)
                lineTo(11.2f, 13.6f)
                lineTo(11.2f, 19.0f)
                close()
                moveTo(12.8f, 19.0f)
                lineTo(12.8f, 13.6f)
                lineTo(18.7f, 10.2f)
                close()
            }
        }.build()
    }

    /** Telegram paper plane logo. */
    val Telegram: ImageVector by lazy {
        ImageVector.Builder(
            name = "Telegram",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(21.4f, 3.4f)
                curveTo(21.1f, 3.1f, 20.6f, 3.0f, 20.1f, 3.2f)
                lineTo(2.7f, 10.0f)
                curveTo(2.1f, 10.2f, 1.8f, 10.7f, 1.8f, 11.3f)
                curveTo(1.8f, 11.9f, 2.3f, 12.4f, 2.9f, 12.6f)
                lineTo(7.6f, 14.1f)
                lineTo(9.5f, 19.8f)
                curveTo(9.7f, 20.3f, 10.2f, 20.6f, 10.7f, 20.6f)
                curveTo(11.1f, 20.6f, 11.4f, 20.4f, 11.7f, 20.1f)
                lineTo(14.2f, 17.6f)
                lineTo(18.4f, 20.7f)
                curveTo(18.7f, 20.9f, 19.1f, 21.0f, 19.5f, 21.0f)
                curveTo(20.1f, 21.0f, 20.6f, 20.6f, 20.8f, 20.0f)
                lineTo(22.2f, 4.7f)
                curveTo(22.3f, 4.2f, 22.0f, 3.7f, 21.4f, 3.4f)
                close()
                moveTo(18.1f, 19.0f)
                lineTo(13.2f, 15.4f)
                lineTo(17.8f, 7.8f)
                lineTo(9.2f, 13.6f)
                lineTo(4.4f, 12.1f)
                lineTo(19.9f, 6.0f)
                close()
            }
        }.build()
    }
    /** Lucide arrow-down: вертикальная стрелка вниз для индикатора сортировки. */
    val ArrowDown: ImageVector by lazy {
        ImageVector.Builder(
            name = "ArrowDown",
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
                moveTo(12f, 5f)
                lineTo(12f, 19f)
                moveTo(5f, 12f)
                lineTo(12f, 19f)
                lineTo(19f, 12f)
            }
        }.build()
    }

    /** Иконка таблицы со столбцами и строками. */
    val Table: ImageVector by lazy {
        ImageVector.Builder(
            name = "Table",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(4f, 5f)
                lineTo(20f, 5f)
                lineTo(20f, 19f)
                lineTo(4f, 19f)
                close()
                moveTo(4f, 10f)
                lineTo(20f, 10f)
                moveTo(11f, 10f)
                lineTo(11f, 19f)
            }
        }.build()
    }
}
