package com.runner.app.service

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import com.runner.app.ui.OverlayActivity
import com.runner.app.util.ScreenCaptureManager

/**
 * Сессия системного ассистента Runner.
 * Запускается ОС при вызове цифрового помощника (удержание кнопки питания, свайп из угла).
 */
class RunnerVoiceInteractionSession(context: Context) : VoiceInteractionSession(context) {

    override fun onPrepareShow(args: Bundle?, showFlags: Int) {
        super.onPrepareShow(args, showFlags)
        // Отключаем встроенное окно сессии, так как используем плавающую активность ассистента
        setUiEnabled(false)
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
        try {
            startAssistantActivity(intent)
        } catch (_: Exception) {
            try {
                context.startActivity(intent)
            } catch (_: Exception) {}
        }
    }
}
