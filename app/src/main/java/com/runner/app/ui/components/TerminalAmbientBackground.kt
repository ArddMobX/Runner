package com.runner.app.ui.components

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.sin
import kotlin.random.Random

/**
 * Мягкий терминальный ambient-фон.
 *
 * Невесомые символы кода и терминала плавно парят по всему экрану,
 * создавая живую атмосферу цифровой среды без визуального шума и градиентного нейрослопа.
 */
@Composable
fun TerminalAmbientBackground(
    enabled: Boolean,
    isLiveMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    if (!enabled) return

    val density = LocalDensity.current
    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface

    val activeGlyphs = if (isLiveMode) LIVE_GLYPHS else GLYPHS
    val particles = remember(isLiveMode) {
        val list = ArrayList<AmbientParticle>(PARTICLE_COUNT)
        val rnd = Random(if (isLiveMode) 77 else 42)
        repeat(PARTICLE_COUNT) {
            val glyph = activeGlyphs[it % activeGlyphs.size]
            val usePrimary = it % 3 == 0
            list.add(
                AmbientParticle(
                    glyph = glyph,
                    xNorm = rnd.nextFloat(),
                    yNorm = rnd.nextFloat(),
                    speed = 0.000012f + rnd.nextFloat() * 0.000018f,
                    swayFreq = 0.5f + rnd.nextFloat() * 0.8f,
                    swayAmp = 0.006f + rnd.nextFloat() * 0.008f,
                    sizePx = (11 + rnd.nextInt(3)) * density.density,
                    baseAlpha = 0.025f + rnd.nextFloat() * 0.035f,
                    isPrimary = usePrimary,
                    phase = rnd.nextFloat() * 6.28f
                )
            )
        }
        list
    }

    var animTick by remember { mutableLongStateOf(0L) }

    LaunchedEffect(enabled) {
        var prevTime = 0L
        while (true) {
            withFrameNanos { frameTime ->
                if (prevTime == 0L) {
                    prevTime = frameTime
                    animTick = frameTime
                    return@withFrameNanos
                }
                val deltaMs = (frameTime - prevTime) / 1_000_000f
                prevTime = frameTime

                for (i in particles.indices) {
                    val p = particles[i]
                    var newY = p.yNorm - p.speed * deltaMs
                    if (newY < -0.06f) {
                        newY = 1.06f
                    }
                    p.yNorm = newY
                    p.phase += 0.0005f * deltaMs
                }
                animTick = frameTime
            }
        }
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    val paint = remember(context) {
        val tf = try {
            androidx.core.content.res.ResourcesCompat.getFont(context, com.runner.app.R.font.google_sans_code_regular)
        } catch (_: Exception) {
            Typeface.MONOSPACE
        }
        Paint().apply {
            isAntiAlias = true
            typeface = tf ?: Typeface.MONOSPACE
        }
    }

    val primaryArgb = primaryColor.toArgb()
    val onSurfaceArgb = onSurfaceColor.toArgb()

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        if (width <= 0f || height <= 0f) return@Canvas

        // Подписка draw фазы Canvas на каждый кадр VSYNC
        val tick = animTick
        if (tick == 0L) return@Canvas

        val canvas = drawContext.canvas.nativeCanvas

        for (i in particles.indices) {
            val p = particles[i]
            val x = (p.xNorm + sin(p.phase * p.swayFreq) * p.swayAmp).coerceIn(0f, 1f) * width
            val y = p.yNorm * height

            val color = if (p.isPrimary) primaryArgb else onSurfaceArgb
            val alphaInt = (p.baseAlpha * 255).toInt().coerceIn(4, 28)
            val finalColor = (color and 0x00FFFFFF) or (alphaInt shl 24)

            paint.textSize = p.sizePx
            paint.color = finalColor

            canvas.drawText(p.glyph, x, y, paint)
        }
    }
}

private class AmbientParticle(
    val glyph: String,
    var xNorm: Float,
    var yNorm: Float,
    val speed: Float,
    val swayFreq: Float,
    val swayAmp: Float,
    val sizePx: Float,
    val baseAlpha: Float,
    val isPrimary: Boolean,
    var phase: Float
)

private const val PARTICLE_COUNT = 28

private val GLYPHS = listOf(
    ">_", "$", "0x", "~", "{ }", "[ ]", "::", "fn",
    "sh", "git", "cd", "200", "404", "1", "0", "&&",
    "||", "val", "=>", "/* */", "run", "echo", "grep",
    "true", "ls", "pwd", "exit", "null"
)

private val LIVE_GLYPHS = listOf(
    "tap", "swipe", "screen", "click", "inspect", "view",
    "ui", "touch", "auto", "live", "key", "press", "window",
    "node", "x, y", "focus", "bounds", "read"
)
