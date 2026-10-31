package com.runner.app.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.runner.app.data.AppSettings

/**
 * Менеджер тактильного отклика (вибрации) устройства с учётом настроек пользователя.
 */
class HapticManager(private val context: Context) {

    private val vibrator: Vibrator? by lazy {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator ?: (context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Выполняет вибрацию заданной длительности и амплитуды.
     * intensity: 1 (слабая), 2 (средняя), 3 (сильная).
     */
    private fun vibrate(durationMs: Long, intensity: Int) {
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return

        try {
            val amplitude = when (intensity) {
                1 -> 60
                3 -> 255
                else -> 140
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = VibrationEffect.createOneShot(durationMs, amplitude.coerceIn(1, 255))
                vib.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(durationMs)
            }
        } catch (_: Exception) {}
    }

    private fun vibratePattern(timings: LongArray, amplitudes: IntArray) {
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = VibrationEffect.createWaveform(timings, amplitudes, -1)
                vib.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(timings, -1)
            }
        } catch (_: Exception) {}
    }

    /** Вибрация при отправке сообщения пользователем */
    fun onSendMessage(settings: AppSettings) {
        if (!settings.hapticEnabled || !settings.hapticOnSend) return
        vibrate(35L, settings.hapticIntensity)
    }

    /** Вибрация при успешном завершении задачи агентом */
    fun onTaskComplete(settings: AppSettings) {
        if (!settings.hapticEnabled || !settings.hapticOnComplete) return
        val amp = when (settings.hapticIntensity) {
            1 -> 70
            3 -> 255
            else -> 150
        }
        // Двойной мягкий импульс завершения: тик, пауза, тик
        vibratePattern(longArrayOf(0, 45, 60, 55), intArrayOf(0, amp, 0, amp))
    }

    /** Вибрация при запросе подтверждения (план или опасное действие) */
    fun onConfirmationRequired(settings: AppSettings) {
        if (!settings.hapticEnabled || !settings.hapticOnConfirm) return
        vibrate(70L, settings.hapticIntensity)
    }

    /** Вибрация при ошибке выполнения */
    fun onError(settings: AppSettings) {
        if (!settings.hapticEnabled || !settings.hapticOnError) return
        val amp = when (settings.hapticIntensity) {
            1 -> 100
            3 -> 255
            else -> 200
        }
        // Тройной короткий тревожный импульс
        vibratePattern(
            longArrayOf(0, 40, 40, 40, 40, 40),
            intArrayOf(0, amp, 0, amp, 0, amp)
        )
    }

    /** Тестовая вибрация для предпросмотра силы в настройках */
    fun testVibration(intensity: Int) {
        vibrate(45L, intensity)
    }
}
