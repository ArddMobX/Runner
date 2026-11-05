package com.runner.app.util

import android.content.Context
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.runner.app.data.AppSettings

/**
 * Менеджер тактильного отклика (вибрации) устройства с учётом настроек пользователя.
 * Оптимизирован для Android 13-15 и вибромоторов X-axis (включая устройства iQOO/Vivo/Xiaomi/Samsung).
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

    private fun performVibration(effect: VibrationEffect, isNotification: Boolean = true) {
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // USAGE_NOTIFICATION и USAGE_ALARM гарантируют отклик даже при отключённом в системе "тактильном отклике клавиатуры"
                val usage = if (isNotification) {
                    VibrationAttributes.USAGE_NOTIFICATION
                } else {
                    VibrationAttributes.USAGE_ALARM
                }
                val attrs = VibrationAttributes.createForUsage(usage)
                vib.vibrate(effect, attrs)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vib.vibrate(effect)
            }
        } catch (_: Exception) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vib.vibrate(effect)
                }
            } catch (_: Exception) {}
        }
    }

    /**
     * Выполняет вибрацию заданной длительности и амплитуды.
     * intensity: 1 (слабая), 2 (средняя), 3 (сильная).
     */
    private fun vibrate(durationMs: Long, intensity: Int, isNotification: Boolean = true) {
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return

        try {
            val safeDuration = durationMs.coerceAtLeast(35L)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val amplitude = when (intensity) {
                    1 -> 90
                    3 -> 255
                    else -> 180
                }
                val effectiveAmp = if (vib.hasAmplitudeControl()) amplitude.coerceIn(1, 255) else VibrationEffect.DEFAULT_AMPLITUDE
                val effect = VibrationEffect.createOneShot(safeDuration, effectiveAmp)
                performVibration(effect, isNotification)
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(safeDuration)
            }
        } catch (_: Exception) {}
    }

    private fun vibratePattern(timings: LongArray, amplitudes: IntArray, isNotification: Boolean = true) {
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effectiveAmps = if (vib.hasAmplitudeControl()) {
                    amplitudes
                } else {
                    amplitudes.map { if (it > 0) VibrationEffect.DEFAULT_AMPLITUDE else 0 }.toIntArray()
                }
                val effect = VibrationEffect.createWaveform(timings, effectiveAmps, -1)
                performVibration(effect, isNotification)
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(timings, -1)
            }
        } catch (_: Exception) {}
    }

    /** Вибрация при отправке сообщения пользователем */
    fun onSendMessage(settings: AppSettings) {
        if (!settings.hapticEnabled || !settings.hapticOnSend) return
        vibrate(40L, settings.hapticIntensity, isNotification = false)
    }

    /** Вибрация при успешном завершении задачи агентом */
    fun onTaskComplete(settings: AppSettings) {
        if (!settings.hapticEnabled || !settings.hapticOnComplete) return
        val amp = when (settings.hapticIntensity) {
            1 -> 110
            3 -> 255
            else -> 190
        }
        // Двойной отчётливый импульс: 60 мс, пауза 70 мс, 75 мс
        vibratePattern(
            longArrayOf(0, 60, 70, 75),
            intArrayOf(0, amp, 0, amp),
            isNotification = true
        )
    }

    /** Вибрация при запросе подтверждения (план или опасное действие) */
    fun onConfirmationRequired(settings: AppSettings) {
        if (!settings.hapticEnabled || !settings.hapticOnConfirm) return
        val amp = when (settings.hapticIntensity) {
            1 -> 130
            3 -> 255
            else -> 200
        }
        vibratePattern(
            longArrayOf(0, 80, 50, 80),
            intArrayOf(0, amp, 0, amp),
            isNotification = true
        )
    }

    /** Вибрация при ошибке выполнения */
    fun onError(settings: AppSettings) {
        if (!settings.hapticEnabled || !settings.hapticOnError) return
        val amp = when (settings.hapticIntensity) {
            1 -> 120
            3 -> 255
            else -> 210
        }
        // Тройной тревожный импульс по 65 мс
        vibratePattern(
            longArrayOf(0, 65, 50, 65, 50, 65),
            intArrayOf(0, amp, 0, amp, 0, amp),
            isNotification = true
        )
    }

    /** Тестовая короткая вибрация для предпросмотра силы в настройках */
    fun testVibration(intensity: Int) {
        vibrate(60L, intensity, isNotification = true)
    }

    /** Непрерывный/пульсирующий тест вибрации на 3 секунды */
    fun testVibration3Seconds(intensity: Int) {
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return

        val amp = when (intensity) {
            1 -> 110
            3 -> 255
            else -> 190
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // 3 секунды: 5 активных импульсов по 450мс с короткими паузами 80мс
                val timings = longArrayOf(0, 450, 80, 450, 80, 450, 80, 450, 80, 450)
                val amplitudes = intArrayOf(0, amp, 0, amp, 0, amp, 0, amp, 0, amp)
                val effect = VibrationEffect.createWaveform(timings, amplitudes, -1)
                performVibration(effect, isNotification = true)
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(3000L)
            }
        } catch (_: Exception) {
            try {
                @Suppress("DEPRECATION")
                vib.vibrate(3000L)
            } catch (_: Exception) {}
        }
    }

    /** Остановка любой активной тестовой вибрации */
    fun stopVibration() {
        try {
            vibrator?.cancel()
        } catch (_: Exception) {}
    }
}
