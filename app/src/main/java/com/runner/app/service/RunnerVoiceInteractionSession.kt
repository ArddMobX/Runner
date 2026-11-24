package com.runner.app.service

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import com.runner.app.ui.OverlayActivity
import com.runner.app.util.ScreenCaptureManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Сессия системного ассистента Runner.
 * Запускается ОС при вызове цифрового помощника (удержание кнопки питания, свайп из угла).
 */
class RunnerVoiceInteractionSession(context: Context) : VoiceInteractionSession(context) {

    companion object {
        @Volatile
        var activeSession: RunnerVoiceInteractionSession? = null
    }

    private val sessionScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        activeSession = this
    }

    override fun onDestroy() {
        sessionScope.cancel()
        if (activeSession == this) {
            activeSession = null
        }
        super.onDestroy()
    }

    override fun onPrepareShow(args: Bundle?, showFlags: Int) {
        super.onPrepareShow(args, showFlags)
        // Отключаем встроенное окно VoiceInteractionSession: весь UI делегирован в OverlayActivity.
        // Без этого система показывает невидимое окно с TOUCHABLE_INSETS_FRAME на весь экран,
        // которое перехватывает все клики и делает панель неинтерактивной картинкой.
        setUiEnabled(false)
    }

    override fun onComputeInsets(outInsets: Insets) {
        super.onComputeInsets(outInsets)
        // Защита: гарантируем, что пустое окно сессии не перехватывает тачи
        outInsets.contentInsets.setEmpty()
        outInsets.touchableInsets = Insets.TOUCHABLE_INSETS_CONTENT
        outInsets.touchableRegion.setEmpty()
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        // Сразу открываем оверлей для мгновенного отклика на жест пользователя.
        // EXTRA_AUTO_SCREENSHOT = false: не открываем лишний системный диалог
        // MediaProjection, так как ОС сама передаст бесплатный снимок в onHandleScreenshot.
        launchOverlay(null)
    }

    override fun onHandleScreenshot(screenshot: Bitmap?) {
        super.onHandleScreenshot(screenshot)
        if (screenshot == null || screenshot.isRecycled) return
        // Копируем Bitmap до возврата из колбэка (система может освободить оригинал)
        // и сохраняем JPEG на Dispatchers.IO, не блокируя Main-поток.
        val bitmapCopy = try {
            screenshot.copy(screenshot.config ?: Bitmap.Config.ARGB_8888, false)
        } catch (_: Exception) {
            null
        } ?: return

        sessionScope.launch {
            val uri = try {
                withContext(Dispatchers.IO) {
                    try {
                        ScreenCaptureManager.saveBitmap(context, bitmapCopy)
                    } finally {
                        if (!bitmapCopy.isRecycled) bitmapCopy.recycle()
                    }
                }
            } catch (_: Exception) {
                if (!bitmapCopy.isRecycled) bitmapCopy.recycle()
                null
            }
            if (uri != null) {
                launchOverlay(uri.toString())
            }
        }
    }

    private fun launchOverlay(screenshotUriString: String?) {
        val intent = Intent(context, OverlayActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            action = Intent.ACTION_ASSIST
            putExtra(OverlayActivity.EXTRA_AUTO_SCREENSHOT, false)
            if (!screenshotUriString.isNullOrBlank()) {
                putExtra(OverlayActivity.EXTRA_SCREENSHOT_URI, screenshotUriString)
            }
        }
        OverlayActivity.markInternalIntent(intent)

        val optionsBundle = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ActivityOptions.makeBasic().apply {
                setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
            }.toBundle()
        } else {
            null
        }

        try {
            startAssistantActivity(intent)
        } catch (_: Exception) {
            try {
                if (optionsBundle != null) {
                    context.startActivity(intent, optionsBundle)
                } else {
                    context.startActivity(intent)
                }
            } catch (_: Exception) {
                try {
                    context.startActivity(intent)
                } catch (_: Exception) {}
            }
        }
    }
}
