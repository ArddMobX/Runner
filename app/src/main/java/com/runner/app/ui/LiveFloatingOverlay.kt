package com.runner.app.ui

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.PauseCircleOutline
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
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
import kotlin.math.abs

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

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                },
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
                            onMove = { dx, dy -> moveOverlay(dx, dy) },
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

            composeView = view

            try {
                wm.addView(view, params)
            } catch (_: Exception) {
                composeView = null
                lifecycleOwner = null
            }
        }
    }

    private fun moveOverlay(dx: Float, dy: Float) {
        val p = layoutParams ?: return
        val wm = windowManager ?: return
        val view = composeView ?: return
        val context = view.context
        val metrics = context.resources.displayMetrics
        val screenWidth = metrics.widthPixels
        val screenHeight = metrics.heightPixels
        val density = metrics.density

        val currentWidth = if (isExpandedState.value) (320 * density).toInt() else (54 * density).toInt()
        val currentHeight = if (isExpandedState.value) (260 * density).toInt() else (54 * density).toInt()

        p.x = (p.x + dx.toInt()).coerceIn(0, (screenWidth - currentWidth).coerceAtLeast(0))
        p.y = (p.y + dy.toInt()).coerceIn(
            (24 * density).toInt(),
            (screenHeight - currentHeight - (48 * density).toInt()).coerceAtLeast(0)
        )
        try {
            wm.updateViewLayout(view, p)
        } catch (_: Exception) {}
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

        if (willExpand) {
            val cardWidthPx = (320 * density).toInt()
            val cardHeightPx = (280 * density).toInt()
            val marginPx = (16 * density).toInt()

            if (p.x + cardWidthPx > screenWidth - marginPx) {
                p.x = (screenWidth - cardWidthPx - marginPx).coerceAtLeast(marginPx)
            }
            if (p.y + cardHeightPx > screenHeight - marginPx) {
                p.y = (screenHeight - cardHeightPx - marginPx).coerceAtLeast(marginPx)
            }
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
        onMove: (Float, Float) -> Unit,
        onStop: () -> Unit,
        onConfirmAction: () -> Unit,
        onCancelAction: () -> Unit
    ) {
        val isExpanded = isExpandedState.value
        val state by LiveAgentManager.stateFlow.collectAsState()
        val context = LocalContext.current

        val visuals = when (state.status) {
            LiveAgentManager.StepStatus.SUCCESS -> StatusVisuals(
                color = Color(0xFF4CAF50),
                icon = Icons.Outlined.Check,
                title = "Runner Live: Готово"
            )
            LiveAgentManager.StepStatus.ERROR -> StatusVisuals(
                color = Color(0xFFEF5350),
                icon = Icons.Outlined.ErrorOutline,
                title = "Runner Live: Ошибка"
            )
            LiveAgentManager.StepStatus.STOPPED -> StatusVisuals(
                color = Color(0xFFFFA726),
                icon = Icons.Outlined.PauseCircleOutline,
                title = "Runner Live: Остановлено"
            )
            LiveAgentManager.StepStatus.RUNNING -> StatusVisuals(
                color = MaterialTheme.colorScheme.primary,
                icon = Icons.Outlined.TouchApp,
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
                    // Компактный плавающий кружок с плавным перетаскиванием
                    var hasDragged by remember { mutableStateOf(false) }

                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { hasDragged = false },
                                    onDragEnd = {},
                                    onDragCancel = {},
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        if (abs(dragAmount.x) > 0.5f || abs(dragAmount.y) > 0.5f) {
                                            hasDragged = true
                                            onMove(dragAmount.x, dragAmount.y)
                                        }
                                    }
                                )
                            }
                            .clip(CircleShape)
                            .background(Color(0xFF141416))
                            .border(1.5.dp, visuals.color, CircleShape)
                            .clickable {
                                if (!hasDragged) {
                                    onToggleExpand()
                                }
                            },
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
                            // Верхняя строка: заголовок со статусом и кнопка свернуть
                            // Также поддерживает перетаскивание карточки за заголовок
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .pointerInput(Unit) {
                                        detectDragGestures { change, dragAmount ->
                                            change.consume()
                                            onMove(dragAmount.x, dragAmount.y)
                                        }
                                    }
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
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
                                        .size(24.dp)
                                        .clip(CircleShape)
                                    .background(Color(0xFF242428))
                                    .clickable { onToggleExpand() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Свернуть",
                                    tint = Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Описание текущего действия / отчет
                        Text(
                            text = state.stepDescription.ifEmpty {
                                if (isFinished) "Задача завершена" else "Выполнение задачи..."
                            },
                            fontFamily = GoogleSans,
                            fontSize = 14.sp,
                            color = Color.White,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (!isFinished && state.stepIndex > 0) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Шаг ${state.stepIndex} из ${state.totalSteps.coerceAtLeast(state.stepIndex)}",
                                fontFamily = GoogleSans,
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.5f)
                            )
                        }

                        // Если требуется подтверждение рискованного действия
                        if (state.confirmationPrompt != null) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF2A2016))
                                    .border(1.dp, Color(0xFFE5A030).copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                    .padding(10.dp)
                            ) {
                                Column {
                                    Text(
                                        text = state.confirmationPrompt.orEmpty(),
                                        fontFamily = GoogleSans,
                                        fontSize = 12.sp,
                                        color = Color(0xFFF3C77D)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        horizontalArrangement = Arrangement.End,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        OutlinedButton(
                                            onClick = onCancelAction,
                                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                            modifier = Modifier.height(32.dp)
                                        ) {
                                            Text("Отмена", fontSize = 11.sp)
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        ElevatedButton(
                                            onClick = onConfirmAction,
                                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                            colors = ButtonDefaults.elevatedButtonColors(
                                                containerColor = MaterialTheme.colorScheme.primary,
                                                contentColor = MaterialTheme.colorScheme.onPrimary
                                            ),
                                            modifier = Modifier.height(32.dp)
                                        ) {
                                            Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Подтвердить", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        if (isFinished) {
                            // Действия по завершению задачи: Закрыть и Перейти в чат Runner
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { dismiss() },
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
                                            }
                                            context.startActivity(intent)
                                        } catch (_: Exception) {}
                                        dismiss()
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
                            // Кнопка Стоп (аварийная остановка)
                            ElevatedButton(
                                onClick = onStop,
                                colors = ButtonDefaults.elevatedButtonColors(
                                    containerColor = Color(0xFF381A1A),
                                    contentColor = Color(0xFFFF6B6B)
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(38.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Stop,
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
