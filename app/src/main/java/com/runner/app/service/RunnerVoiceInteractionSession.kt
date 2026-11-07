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

/**
 * Сессия системного ассистента Runner.
 * Запускается ОС при вызове цифрового помощника (удержание кнопки питания, свайп из угла).
 */
class RunnerVoiceInteractionSession(context: Context) : VoiceInteractionSession(context) {

    companion object {
        @Volatile
        var activeSession: RunnerVoiceInteractionSession? = null
    }

    override fun onCreate() {
        super.onCreate()
        activeSession = this
    }

    override fun onDestroy() {
        super.onDestroy()
        if (activeSession == this) {
            activeSession = null
        }
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
        // Сразу открываем оверлей для мгновенного отклика на жест пользователя
        launchOverlay(null)
    }

    override fun onHandleScreenshot(screenshot: Bitmap?) {
        super.onHandleScreenshot(screenshot)
        if (screenshot != null) {
            val uri = try {
                ScreenCaptureManager.saveBitmap(context, screenshot)
            } catch (_: Exception) {
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
            if (!screenshotUriString.isNullOrBlank()) {
                putExtra(OverlayActivity.EXTRA_SCREENSHOT_URI, screenshotUriString)
            }
        }

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
