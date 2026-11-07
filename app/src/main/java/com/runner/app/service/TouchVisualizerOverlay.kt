package com.runner.app.service

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator

/**
 * Полноэкранный прозрачный оверлей для визуализации действий агента в Live-режиме:
 * анимированное кольцо касания в точке тапа и мягкая неоновая рамка вокруг целевого элемента.
 * Окно полностью прозрачно для тачей (FLAG_NOT_TOUCHABLE).
 */
object TouchVisualizerOverlay {

    private var windowManager: WindowManager? = null
    private var visualizerView: VisualizerView? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    fun initialize(context: Context) {
        mainHandler.post {
            if (visualizerView != null) return@post
            if (!Settings.canDrawOverlays(context)) return@post

            val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return@post
            windowManager = wm

            val view = VisualizerView(context.applicationContext)
            visualizerView = view

            val layoutParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                },
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
            }

            try {
                wm.addView(view, layoutParams)
            } catch (_: Exception) {
                visualizerView = null
            }
        }
    }

    fun showTouchRipple(x: Float, y: Float, bounds: Rect? = null) {
        mainHandler.post {
            visualizerView?.startTouchAnimation(x, y, bounds)
        }
    }

    fun highlightTarget(bounds: Rect) {
        mainHandler.post {
            visualizerView?.startHighlightAnimation(bounds)
        }
    }

    fun dismiss() {
        mainHandler.post {
            visualizerView?.let { view ->
                try {
                    windowManager?.removeView(view)
                } catch (_: Exception) {}
            }
            visualizerView = null
            windowManager = null
        }
    }

    @SuppressLint("ViewConstructor")
    private class VisualizerView(context: Context) : View(context) {

        private var touchX: Float = 0f
        private var touchY: Float = 0f
        private var rippleProgress: Float = 1f
        private var highlightBounds: RectF? = null
        private var highlightAlpha: Float = 0f

        private val density = context.resources.displayMetrics.density
        private val minRadius = 8f * density
        private val maxRadius = 42f * density

        private val rippleStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.5f * density
            color = Color.parseColor("#4DE1C1") // Неоновый мятный акцент по умолчанию
        }

        private val rippleFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#4DE1C1")
        }

        private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f * density
            color = Color.parseColor("#4DE1C1")
        }

        private var rippleAnimator: ValueAnimator? = null
        private var highlightAnimator: ValueAnimator? = null

        fun startTouchAnimation(x: Float, y: Float, bounds: Rect?) {
            touchX = x
            touchY = y
            if (bounds != null) {
                highlightBounds = RectF(
                    bounds.left.toFloat(),
                    bounds.top.toFloat(),
                    bounds.right.toFloat(),
                    bounds.bottom.toFloat()
                )
            } else {
                highlightBounds = null
            }

            rippleAnimator?.cancel()
            rippleAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 320
                interpolator = DecelerateInterpolator()
                addUpdateListener { anim ->
                    rippleProgress = anim.animatedValue as Float
                    invalidate()
                }
                start()
            }

            if (bounds != null) {
                startHighlightAnimation(bounds)
            }
        }

        fun startHighlightAnimation(bounds: Rect) {
            highlightBounds = RectF(
                bounds.left.toFloat(),
                bounds.top.toFloat(),
                bounds.right.toFloat(),
                bounds.bottom.toFloat()
            )

            highlightAnimator?.cancel()
            highlightAnimator = ValueAnimator.ofFloat(1f, 0f).apply {
                duration = 450
                interpolator = DecelerateInterpolator()
                addUpdateListener { anim ->
                    highlightAlpha = anim.animatedValue as Float
                    invalidate()
                }
                start()
            }
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            // Отрисовка подсветки элемента
            val bounds = highlightBounds
            if (bounds != null && highlightAlpha > 0.01f) {
                val alphaInt = (highlightAlpha * 180).toInt().coerceIn(0, 255)
                highlightPaint.alpha = alphaInt
                val cornerRadius = 10f * density
                canvas.drawRoundRect(bounds, cornerRadius, cornerRadius, highlightPaint)
            }

            // Отрисовка круга тача
            if (rippleProgress < 1f) {
                val currentRadius = minRadius + (maxRadius - minRadius) * rippleProgress
                val alpha = (1f - rippleProgress).coerceIn(0f, 1f)

                val strokeAlphaInt = (alpha * 220).toInt().coerceIn(0, 255)
                rippleStrokePaint.alpha = strokeAlphaInt
                canvas.drawCircle(touchX, touchY, currentRadius, rippleStrokePaint)

                val fillAlphaInt = (alpha * 60).toInt().coerceIn(0, 255)
                rippleFillPaint.alpha = fillAlphaInt
                canvas.drawCircle(touchX, touchY, currentRadius * 0.75f, rippleFillPaint)
            }
        }
    }
}
