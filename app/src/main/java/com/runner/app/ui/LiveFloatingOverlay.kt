package com.runner.app.ui

import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.runner.app.MainActivity
import com.runner.app.service.TouchVisualizerOverlay
import com.runner.app.ui.theme.GoogleSans
import com.runner.app.ui.theme.RunnerTheme
import com.runner.app.util.CrashReportManager
import com.runner.app.ui.components.RunnerIcons

/**
 * Плавающий оверлей для управления Live-режимом (автопилотом) агента:
 * компактный перетаскиваемый кружок у края экрана, разворачивающийся по тапу
 * в карточку статуса с кнопкой аварийной остановки "Стоп" и подтверждением рискованных шагов.
 */
object LiveFloatingOverlay {

    private var windowManager: WindowManager? = null
    private var composeView: ComposeView? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    private val isExpandedState = mutableStateOf(false)
    private val mainHandler = Handler(Looper.getMainLooper())

    fun show(context: Context) {
        mainHandler.post {
            if (composeView != null) return@post
            if (!Settings.canDrawOverlays(context)) return@post

            TouchVisualizerOverlay.initialize(context)

            val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return@post
            windowManager = wm

            val owner = OverlayLifecycleOwner()
            lifecycleOwner = owner

            val displayMetrics = context.resources.displayMetrics
            val density = displayMetrics.density
            val screenWidth = displayMetrics.widthPixels
            val screenHeight = displayMetrics.heightPixels

            val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = screenWidth - (68 * density).toInt()
                y = (screenHeight * 0.35f).toInt()
            }
            layoutParams = params

            val view = ComposeView(context).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                setViewTreeLifecycleOwner(owner)
                setViewTreeViewModelStoreOwner(owner)
                setViewTreeSavedStateRegistryOwner(owner)

                setContent {
                    RunnerTheme {
                        FloatingOverlayContent(
                            onToggleExpand = { toggleExpand() },
                            onPause = { LiveAgentManager.pause() },
                            onResume = { LiveAgentManager.resumeExecution() },
                            onStop = {
                                LiveAgentManager.stop("Остановлено пользователем")
                            },
                            onConfirmAction = {
                                LiveAgentManager.confirmPendingAction(true)
                            },
                            onCancelAction = {
                                LiveAgentManager.confirmPendingAction(false)
                            }
                        )
                    }
                }
            }

            var initialX = 0
            var initialY = 0
            var initialTouchX = 0f
            var initialTouchY = 0f
            var isDragging = false
            var isHeaderTouch = false
            val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

            view.setOnTouchListener { _, event ->
                val p = layoutParams ?: return@setOnTouchListener false
                val curWm = windowManager ?: return@setOnTouchListener false

                if (!isExpandedState.value) {
                    // Компактный плавающий кружок: перетаскивание и тап
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            initialX = p.x
                            initialY = p.y
                            initialTouchX = event.rawX
                            initialTouchY = event.rawY
                            isDragging = false
                            true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            val dx = event.rawX - initialTouchX
                            val dy = event.rawY - initialTouchY
                            if (!isDragging && (dx * dx + dy * dy > touchSlop * touchSlop)) {
                                isDragging = true
                            }
                            if (isDragging) {
                                val bubbleSizePx = (54 * density).toInt()
                                p.x = (initialX + dx).toInt().coerceIn(0, (screenWidth - bubbleSizePx).coerceAtLeast(0))
                                p.y = (initialY + dy).toInt().coerceIn(
                                    (24 * density).toInt(),
                                    (screenHeight - bubbleSizePx - (48 * density).toInt()).coerceAtLeast(0)
                                )
                                try {
                                    curWm.updateViewLayout(view, p)
                                } catch (_: Exception) {}
                            }
                            true
                        }
                        MotionEvent.ACTION_UP -> {
                            if (!isDragging) {
                                toggleExpand()
                            } else {
                                snapToEdge(view, p, screenWidth, density)
                            }
                            true
                        }
                        MotionEvent.ACTION_CANCEL -> {
                            isDragging = false
                            true
                        }
                        else -> false
                    }
                } else {
                    // Развернутая карточка: перетаскивание за верхнюю плашку (кроме кнопки закрытия X)
                    val headerHeightPx = (46 * density).toInt()
                    val closeButtonZonePx = (52 * density).toInt()

                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            if (event.y < headerHeightPx && event.x < (view.width - closeButtonZonePx)) {
                                isHeaderTouch = true
                                initialX = p.x
                                initialY = p.y
                                initialTouchX = event.rawX
                                initialTouchY = event.rawY
                                isDragging = false
                                true
                            } else {
                                isHeaderTouch = false
                                false
                            }
                        }
                        MotionEvent.ACTION_MOVE -> {
                            if (isHeaderTouch) {
                                val dx = event.rawX - initialTouchX
                                val dy = event.rawY - initialTouchY
                                if (!isDragging && (dx * dx + dy * dy > touchSlop * touchSlop)) {
                                    isDragging = true
                                }
                                if (isDragging) {
                                    val cardWidthPx = (320 * density).toInt()
                                    val cardHeightPx = (280 * density).toInt()
                                    p.x = (initialX + dx).toInt().coerceIn(0, (screenWidth - cardWidthPx).coerceAtLeast(0))
                                    p.y = (initialY + dy).toInt().coerceIn(
                                        (24 * density).toInt(),
                                        (screenHeight - cardHeightPx).coerceAtLeast(0)
                                    )
                                    try {
                                        curWm.updateViewLayout(view, p)
                                    } catch (_: Exception) {}
                                }
                                true
                            } else {
                                false
                            }
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            if (isHeaderTouch) {
                                isHeaderTouch = false
                                isDragging = false
                                true
                            } else {
                                false
                            }
                        }
                        else -> false
                    }
                }
            }

            composeView = view

            try {
                wm.addView(view, params)
            } catch (_: Exception) {
                composeView = null
                lifecycleOwner = null
            }
        }
    }

    private fun snapToEdge(view: ComposeView, params: WindowManager.LayoutParams, screenWidth: Int, density: Float) {
        val wm = windowManager ?: return
        val currentX = params.x
        val bubbleSizePx = (54 * density).toInt()
        val marginPx = (16 * density).toInt()
        val midX = screenWidth / 2
        val targetX = if (currentX + bubbleSizePx / 2 < midX) {
            marginPx
        } else {
            (screenWidth - bubbleSizePx - marginPx).coerceAtLeast(marginPx)
        }

        ValueAnimator.ofInt(currentX, targetX).apply {
            duration = 180
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                params.x = anim.animatedValue as Int
                try {
                    wm.updateViewLayout(view, params)
                } catch (_: Exception) {}
            }
            start()
        }
    }

    private fun toggleExpand() {
        val willExpand = !isExpandedState.value
        val p = layoutParams ?: return
        val wm = windowManager ?: return
        val view = composeView ?: return
        val context = view.context
        val metrics = context.resources.displayMetrics
        val screenWidth = metrics.widthPixels
        val screenHeight = metrics.heightPixels
        val density = metrics.density

        val marginPx = (16 * density).toInt()
        if (willExpand) {
            val cardWidthPx = (320 * density).toInt()
            val cardHeightPx = (280 * density).toInt()

            if (p.x + cardWidthPx > screenWidth - marginPx) {
                p.x = (screenWidth - cardWidthPx - marginPx).coerceAtLeast(marginPx)
            }
            if (p.y + cardHeightPx > screenHeight - marginPx) {
                p.y = (screenHeight - cardHeightPx - marginPx).coerceAtLeast(marginPx)
            }
            try {
                wm.updateViewLayout(view, p)
            } catch (_: Exception) {}
        } else {
            val bubbleSizePx = (54 * density).toInt()
            p.x = p.x.coerceIn(marginPx, (screenWidth - bubbleSizePx - marginPx).coerceAtLeast(marginPx))
            p.y = p.y.coerceIn(marginPx, (screenHeight - bubbleSizePx - marginPx).coerceAtLeast(marginPx))
            try {
                wm.updateViewLayout(view, p)
            } catch (_: Exception) {}
        }
        isExpandedState.value = willExpand
    }

    fun dismiss() {
        mainHandler.post {
            composeView?.let { view ->
                try {
                    windowManager?.removeView(view)
                } catch (_: Exception) {}
            }
            composeView = null
            lifecycleOwner?.destroy()
            lifecycleOwner = null
            windowManager = null
            isExpandedState.value = false
            TouchVisualizerOverlay.dismiss()
        }
    }

    private data class StatusVisuals(
        val color: Color,
        val icon: ImageVector,
        val title: String
    )

    @Composable
    private fun FloatingOverlayContent(
        onToggleExpand: () -> Unit,
        onPause: () -> Unit,
        onResume: () -> Unit,
        onStop: () -> Unit,
        onConfirmAction: () -> Unit,
        onCancelAction: () -> Unit
    ) {
        val isExpanded = isExpandedState.value
        val state by LiveAgentManager.stateFlow.collectAsState()
        val context = LocalContext.current

        // Вопрос не должен висеть в свёрнутом кружке: разворачиваем карточку
        // сами, иначе подтверждение выглядит как зависшая задача.
        LaunchedEffect(state.confirmationPrompt) {
            if (state.confirmationPrompt != null && !isExpandedState.value) {
                toggleExpand()
            }
        }

        val visuals = when {
            state.status == LiveAgentManager.StepStatus.SUCCESS -> StatusVisuals(
                color = Color(0xFF34D399),
                icon = RunnerIcons.Check,
                title = "Готово"
            )
            state.status == LiveAgentManager.StepStatus.STOPPED -> StatusVisuals(
                color = Color(0xFFFBBF24),
                icon = RunnerIcons.PauseCircle,
                title = "Остановлено"
            )
            state.status == LiveAgentManager.StepStatus.ERROR -> StatusVisuals(
                color = Color(0xFFF87171),
                icon = RunnerIcons.Error,
                title = "Ошибка"
            )
            state.isPaused -> StatusVisuals(
                color = Color(0xFFFBBF24),
                icon = RunnerIcons.PauseCircle,
                title = "Пауза"
            )
            else -> StatusVisuals(
                color = MaterialTheme.colorScheme.primary,
                icon = RunnerIcons.TouchApp,
                title = "Runner Live"
            )
        }

        val isFinished = state.status != LiveAgentManager.StepStatus.RUNNING

        Box(
            modifier = Modifier.padding(6.dp)
        ) {
            Crossfade(
                targetState = isExpanded,
                animationSpec = tween(150),
                label = "overlay_expand_fade"
            ) { expanded ->
                if (!expanded) {
                    // Компактный плавающий кружок
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF141416))
                            .border(1.5.dp, visuals.color, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = visuals.icon,
                            contentDescription = visuals.title,
                            tint = visuals.color,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                } else {
                    // Развернутая мини-карточка статуса
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFF161618),
                        shadowElevation = 8.dp,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            visuals.color.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.widthIn(min = 280.dp, max = 340.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp)
                        ) {
                            // Заголовок карточки со статусом и кнопкой сворачивания
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(visuals.color)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = visuals.title,
                                        fontFamily = GoogleSans,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = visuals.color
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF242428))
                                        .clickable {
                                            if (isFinished) {
                                                LiveAgentManager.reset()
                                            } else {
                                                onToggleExpand()
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = RunnerIcons.Close,
                                        contentDescription = if (isFinished) "Закрыть" else "Свернуть",
                                        tint = Color.White.copy(alpha = 0.85f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            if (state.expectedPackage.isNotBlank()) {
                                Text(
                                    text = "Приложение: ${state.expectedPackage}",
                                    fontFamily = GoogleSans,
                                    fontSize = 10.5.sp,
                                    color = Color.White.copy(alpha = 0.5f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                            }

                            if (!isFinished) {
                                Text(
                                    text = if (state.isPaused) "Пауза" else "Сейчас",
                                    fontFamily = GoogleSans,
                                    fontSize = 10.sp,
                                    color = Color.White.copy(alpha = 0.4f)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                            }

                            // Описание текущего действия / отчет
                            Text(
                                text = state.stepDescription.ifEmpty {
                                    if (isFinished) "Задача завершена" else "Выполнение задачи..."
                                },
                                fontFamily = GoogleSans,
                                fontSize = 15.sp,
                                fontWeight = if (isFinished) FontWeight.Normal else FontWeight.Medium,
                                color = Color.White,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis
                            )

                            if (!isFinished && state.stepIndex > 0) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    // «из N» показываем только когда N реально известно.
                                    text = if (state.totalSteps > 0) {
                                        "Шаг ${state.stepIndex} из ${state.totalSteps}"
                                    } else {
                                        "Шаг ${state.stepIndex}"
                                    },
                                    fontFamily = GoogleSans,
                                    fontSize = 12.sp,
                                    color = Color.White.copy(alpha = 0.6f)
                                )
                            }

                            // История предыдущих действий: по карточке видно,
                            // что агент уже сделал, а не только текущий шаг.
                            val previousSteps = state.recentSteps.dropLast(1)
                            if (!isFinished && previousSteps.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                previousSteps.forEach { recent ->
                                    Text(
                                        text = "· $recent",
                                        fontFamily = GoogleSans,
                                        fontSize = 11.sp,
                                        color = Color.White.copy(alpha = 0.45f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            // Блок подтверждения действия, если агент запросил подтверждение
                            state.confirmationPrompt?.let { prompt ->
                                Spacer(modifier = Modifier.height(10.dp))
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFF231F14))
                                        .border(1.dp, Color(0xFFF59E0B).copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                        .padding(10.dp)
                                ) {
                                    Text(
                                        text = prompt,
                                        fontFamily = GoogleSans,
                                        fontSize = 12.sp,
                                        color = Color(0xFFFDE68A)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = onCancelAction,
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.weight(1f).height(34.dp)
                                        ) {
                                            Text("Отмена", fontFamily = GoogleSans, fontSize = 11.sp)
                                        }
                                        ElevatedButton(
                                            onClick = onConfirmAction,
                                            colors = ButtonDefaults.elevatedButtonColors(
                                                containerColor = Color(0xFFF59E0B),
                                                contentColor = Color.Black
                                            ),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.weight(1f).height(34.dp)
                                        ) {
                                            Text(
                                                text = "Да",
                                                fontFamily = GoogleSans,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            if (isFinished) {
                                if (state.status == LiveAgentManager.StepStatus.ERROR || state.status == LiveAgentManager.StepStatus.STOPPED) {
                                    OutlinedButton(
                                        onClick = {
                                            CrashReportManager.copyReport(context, "Сбой в Live-режиме: ${state.stepDescription}")
                                        },
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = Color(0xFFFCA5A5)
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.45f)),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = RunnerIcons.ContentCopy,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Скопировать отчет о сбое",
                                            fontFamily = GoogleSans,
                                            fontSize = 12.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                }

                                // Действия по завершению задачи: Закрыть и Перейти в чат Runner
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { LiveAgentManager.reset() },
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(38.dp)
                                    ) {
                                        Text("Закрыть", fontFamily = GoogleSans, fontSize = 12.sp)
                                    }
                                    ElevatedButton(
                                        onClick = {
                                            try {
                                                val intent = Intent(context, MainActivity::class.java).apply {
                                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                                                    if (state.sessionId.isNotBlank()) {
                                                        putExtra("SESSION_ID", state.sessionId)
                                                    }
                                                }
                                                context.startActivity(intent)
                                            } catch (_: Exception) {}
                                            LiveAgentManager.reset()
                                        },
                                        colors = ButtonDefaults.elevatedButtonColors(
                                            containerColor = visuals.color,
                                            contentColor = Color.White
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .weight(1.3f)
                                            .height(38.dp)
                                    ) {
                                        Text(
                                            text = "В Runner",
                                            fontFamily = GoogleSans,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = if (state.isPaused) onResume else onPause,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(38.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (state.isPaused) RunnerIcons.PlayArrow else RunnerIcons.Pause,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (state.isPaused) "Продолжить" else "Пауза",
                                            fontFamily = GoogleSans,
                                            fontSize = 12.sp
                                        )
                                    }
                                    ElevatedButton(
                                        onClick = onStop,
                                        colors = ButtonDefaults.elevatedButtonColors(
                                            containerColor = Color(0xFF381A1A),
                                            contentColor = Color(0xFFFF6B6B)
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(38.dp)
                                    ) {
                                        Icon(
                                            imageVector = RunnerIcons.StopFilled,
                                            contentDescription = "Стоп",
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Стоп",
                                            fontFamily = GoogleSans,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                                if (!state.isPaused) {
                                    TextButton(
                                        onClick = {
                                            LiveAgentManager.stop("Управление передано пользователю")
                                        }
                                    ) {
                                        Text(
                                            text = "Забрать управление",
                                            fontFamily = GoogleSans,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private class OverlayLifecycleOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
        private val lifecycleRegistry = LifecycleRegistry(this)
        private val store = ViewModelStore()
        private val controller = SavedStateRegistryController.create(this)

        init {
            controller.performAttach()
            controller.performRestore(null)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }

        override val lifecycle: Lifecycle get() = lifecycleRegistry
        override val viewModelStore: ViewModelStore get() = store
        override val savedStateRegistry: SavedStateRegistry get() = controller.savedStateRegistry

        fun destroy() {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
            store.clear()
        }
    }
}
