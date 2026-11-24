package com.runner.app.ui.theme

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/**
 * Глобальный выключатель движения. Значение учитывает и настройку внутри Runner,
 * и системный флаг Android «Удалить анимации» (`ANIMATOR_DURATION_SCALE == 0`).
 * При `enabled == false` все пружины, переходы, скроллы и шторки работают мгновенно.
 */
object MotionSettings {
    var enabled by mutableStateOf(true)
}

/** Чистая функция расчёта итогового состояния анимаций (покрыта JVM-тестами). */
internal fun resolveEffectiveMotionEnabled(
    userMotionEnabled: Boolean,
    systemAnimatorScale: Float
): Boolean = userMotionEnabled && systemAnimatorScale > 0f

private fun readSystemAnimatorScale(context: Context): Float = try {
    Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f
    )
} catch (_: Throwable) {
    1f
}

/**
 * Синхронизирует глобальный выключатель с настройкой приложения и системной
 * шкалой анимаций Android (перечитывается при каждом возврате на экран).
 */
@Composable
fun ApplyMotionSetting(enabled: Boolean) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var systemScale by remember(context) {
        mutableFloatStateOf(readSystemAnimatorScale(context))
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                systemScale = readSystemAnimatorScale(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val effective = resolveEffectiveMotionEnabled(
        userMotionEnabled = enabled,
        systemAnimatorScale = systemScale
    )
    SideEffect { MotionSettings.enabled = effective }
}

/**
 * «Шёлковый Material»: физическая пружинная система в духе Material 3 Expressive,
 * настроенная на тягучее, маслянистое торможение без мультяшного отскока.
 *
 * - **Spatial** (движение, размер, scale, сдвиги экранов) — живая физическая инерция
 *   с тремя весовыми уровнями (`Fast`, `Default`, `Slow`).
 * - **Effects** (цвет, прозрачность) — критически затухающие пружины (`damping = 1.0`),
 *   которые никогда не перелетают и не мигают.
 * - При `MotionSettings.enabled == false` абсолютно все спеки возвращают `snap()`,
 *   а переходы — `EnterTransition.None` / `ExitTransition.None`.
 */
object MotionTokens {
    /** Материальный emphasized-decelerate: быстрый старт и мягкий хвост. */
    val FluidCurve = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    // --- Spatial springs (геометрия: позиция, размер, масштаб, повороты) ---
    /** Мелкие элементы: нажатия, чипы, шевроны, морфинг иконок. */
    const val SpatialFastDamping = 0.78f
    const val SpatialFastStiffness = 720f

    /** Основные контейнеры: карточки, раскрытия шагов, плашки, палитры, FAB. */
    const val SpatialDefaultDamping = 0.82f
    const val SpatialDefaultStiffness = 320f

    /** Крупные переходы: экраны (Shared Axis X), пустой чат ↔ лента. */
    const val SpatialSlowDamping = 0.86f
    const val SpatialSlowStiffness = 210f

    // --- Effects springs (цвет и прозрачность: строго без перелёта) ---
    const val EffectsFastDamping = Spring.DampingRatioNoBouncy
    const val EffectsFastStiffness = 900f

    const val EffectsDefaultDamping = Spring.DampingRatioNoBouncy
    const val EffectsDefaultStiffness = 450f

    const val EffectsSlowDamping = Spring.DampingRatioNoBouncy
    const val EffectsSlowStiffness = 260f

    // Совместимые псевдонимы прежних констант
    const val SpringStiffness = SpatialDefaultStiffness
    const val SpringDampingRatio = SpatialDefaultDamping

    const val DurationMicro = 120
    const val DurationFast = 180
    const val DurationNormal = 280
    const val DurationSlow = 360

    fun <T> spatialFast(): FiniteAnimationSpec<T> = if (MotionSettings.enabled) {
        spring(dampingRatio = SpatialFastDamping, stiffness = SpatialFastStiffness)
    } else {
        snap()
    }

    fun <T> spatialDefault(): FiniteAnimationSpec<T> = if (MotionSettings.enabled) {
        spring(dampingRatio = SpatialDefaultDamping, stiffness = SpatialDefaultStiffness)
    } else {
        snap()
    }

    fun <T> spatialSlow(): FiniteAnimationSpec<T> = if (MotionSettings.enabled) {
        spring(dampingRatio = SpatialSlowDamping, stiffness = SpatialSlowStiffness)
    } else {
        snap()
    }

    fun <T> effectsFast(): FiniteAnimationSpec<T> = if (MotionSettings.enabled) {
        spring(dampingRatio = EffectsFastDamping, stiffness = EffectsFastStiffness)
    } else {
        snap()
    }

    fun <T> effectsDefault(): FiniteAnimationSpec<T> = if (MotionSettings.enabled) {
        spring(dampingRatio = EffectsDefaultDamping, stiffness = EffectsDefaultStiffness)
    } else {
        snap()
    }

    fun <T> effectsSlow(): FiniteAnimationSpec<T> = if (MotionSettings.enabled) {
        spring(dampingRatio = EffectsSlowDamping, stiffness = EffectsSlowStiffness)
    } else {
        snap()
    }

    /** Основная пружина для карточек и раскрытий (делегирует в [spatialDefault]). */
    fun <T> fluidSpring(): FiniteAnimationSpec<T> = spatialDefault()

    fun <T> fluidTween(durationMillis: Int = DurationNormal): FiniteAnimationSpec<T> =
        if (MotionSettings.enabled) {
            tween(
                durationMillis = durationMillis,
                easing = FluidCurve
            )
        } else {
            snap()
        }

    /**
     * Переход между экранами по Material Shared Axis X на тягучей пружине [spatialSlow].
     * Входящий экран мягко «втекает» со сдвигом ~22% ширины, уходящий чуть уходит
     * в глубину (`scale 0.975`), а при возврате назад движение зеркалится.
     */
    fun sharedAxisXTransition(forward: Boolean): ContentTransform {
        if (!MotionSettings.enabled) {
            return EnterTransition.None togetherWith ExitTransition.None
        }
        val enter = if (forward) {
            slideInHorizontally(animationSpec = spatialSlow()) { width -> (width * 0.22f).toInt() } +
                fadeIn(animationSpec = effectsDefault())
        } else {
            slideInHorizontally(animationSpec = spatialSlow()) { width -> -(width * 0.12f).toInt() } +
                scaleIn(initialScale = 0.975f, animationSpec = spatialSlow()) +
                fadeIn(animationSpec = effectsDefault())
        }
        val exit = if (forward) {
            slideOutHorizontally(animationSpec = spatialSlow()) { width -> -(width * 0.12f).toInt() } +
                scaleOut(targetScale = 0.975f, animationSpec = spatialSlow()) +
                fadeOut(animationSpec = effectsFast())
        } else {
            slideOutHorizontally(animationSpec = spatialSlow()) { width -> (width * 0.22f).toInt() } +
                fadeOut(animationSpec = effectsFast())
        }
        return ContentTransform(
            targetContentEnter = enter,
            initialContentExit = exit,
            targetContentZIndex = if (forward) 1f else 0f
        )
    }

    /** Деликатный горизонтальный сдвиг заголовка в шапке синхронно с [sharedAxisXTransition]. */
    fun sharedAxisTitleTransition(forward: Boolean): ContentTransform {
        if (!MotionSettings.enabled) {
            return EnterTransition.None togetherWith ExitTransition.None
        }
        val enter = slideInHorizontally(animationSpec = spatialDefault()) { width ->
            if (forward) (width * 0.14f).toInt() else -(width * 0.14f).toInt()
        } + fadeIn(animationSpec = effectsDefault())
        val exit = slideOutHorizontally(animationSpec = spatialDefault()) { width ->
            if (forward) -(width * 0.10f).toInt() else (width * 0.10f).toInt()
        } + fadeOut(animationSpec = effectsFast())
        return enter togetherWith exit
    }

    /** Плавное раскрытие секций, плашек, палитры команд и вложений. */
    fun expandSectionEnter(expandFrom: Alignment.Vertical = Alignment.Top): EnterTransition =
        if (!MotionSettings.enabled) {
            EnterTransition.None
        } else {
            expandVertically(
                animationSpec = spatialDefault(),
                expandFrom = expandFrom
            ) + fadeIn(animationSpec = effectsDefault())
        }

    fun expandSectionExit(shrinkTowards: Alignment.Vertical = Alignment.Top): ExitTransition =
        if (!MotionSettings.enabled) {
            ExitTransition.None
        } else {
            shrinkVertically(
                animationSpec = spatialDefault(),
                shrinkTowards = shrinkTowards
            ) + fadeOut(animationSpec = effectsFast())
        }

    /** Плавное появление плавающих кнопок (например, «Вниз»). */
    fun floatingRevealEnter(): EnterTransition =
        if (!MotionSettings.enabled) {
            EnterTransition.None
        } else {
            scaleIn(initialScale = 0.84f, animationSpec = spatialFast()) +
                slideInVertically(animationSpec = spatialFast()) { it / 3 } +
                fadeIn(animationSpec = effectsFast())
        }

    fun floatingRevealExit(): ExitTransition =
        if (!MotionSettings.enabled) {
            ExitTransition.None
        } else {
            scaleOut(targetScale = 0.84f, animationSpec = spatialFast()) +
                slideOutVertically(animationSpec = spatialFast()) { it / 3 } +
                fadeOut(animationSpec = effectsFast())
        }

    /** Мягкий морфинг иконки внутри кнопки (Микрофон ↔ Отправить ↔ Стоп, Play ↔ Pause). */
    fun iconSwapTransition(): ContentTransform {
        if (!MotionSettings.enabled) {
            return EnterTransition.None togetherWith ExitTransition.None
        }
        return (scaleIn(initialScale = 0.72f, animationSpec = spatialFast()) +
            fadeIn(animationSpec = effectsFast())) togetherWith
            (scaleOut(targetScale = 0.72f, animationSpec = spatialFast()) +
                fadeOut(animationSpec = effectsFast()))
    }

    /** Плавная смена крупных состояний контента (пустой чат ↔ лента сообщений). */
    fun contentSwapTransition(): ContentTransform {
        if (!MotionSettings.enabled) {
            return EnterTransition.None togetherWith ExitTransition.None
        }
        return (fadeIn(animationSpec = effectsDefault()) +
            slideInVertically(animationSpec = spatialSlow()) { it / 24 }) togetherWith
            fadeOut(animationSpec = effectsFast())
    }
}

/** Прокрутка списка с честным соблюдением полного отключения анимаций. */
suspend fun LazyListState.scrollToItemRespectingMotion(index: Int, scrollOffset: Int = 0) {
    if (MotionSettings.enabled) {
        animateScrollToItem(index, scrollOffset)
    } else {
        scrollToItem(index, scrollOffset)
    }
}

/** Открытие боковой шторки с мгновенным переключением при отключённых анимациях. */
suspend fun DrawerState.openRespectingMotion() {
    if (MotionSettings.enabled) {
        open()
    } else {
        snapTo(DrawerValue.Open)
    }
}

/** Закрытие боковой шторки с мгновенным переключением при отключённых анимациях. */
suspend fun DrawerState.closeRespectingMotion() {
    if (MotionSettings.enabled) {
        close()
    } else {
        snapTo(DrawerValue.Closed)
    }
}

/**
 * Анимация лёгкого нажатия поверх существующего [InteractionSource]
 * (для `IconButton`, `Button`, `OutlinedButton` и других уже кликабельных компонентов),
 * без добавления второго обработчика нажатий.
 */
fun Modifier.bounceOnPress(
    interactionSource: InteractionSource,
    enabled: Boolean = true,
    scaleDown: Float = 0.965f
): Modifier = composed {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled && MotionSettings.enabled) {
            scaleDown
        } else {
            1f
        },
        animationSpec = MotionTokens.spatialFast(),
        label = "bounce_scale"
    )

    this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * Мягкое нажатие: лёгкое уменьшение и возврат пружиной.
 * Построено на [clickable] + [MutableInteractionSource], чтобы сохранять
 * доступность (TalkBack / Role.Button), фокус, отмену при уводе пальца и ripple.
 * При выключенных анимациях масштаб не меняется вообще.
 */
fun Modifier.bounceClick(
    enabled: Boolean = true,
    scaleDown: Float = 0.965f,
    onClick: () -> Unit
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    this
        .bounceOnPress(
            interactionSource = interactionSource,
            enabled = enabled,
            scaleDown = scaleDown
        )
        .clickable(
            interactionSource = interactionSource,
            indication = LocalIndication.current,
            enabled = enabled,
            role = Role.Button,
            onClick = onClick
        )
}
