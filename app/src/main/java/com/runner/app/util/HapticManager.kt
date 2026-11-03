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
 * Оптимизирован для Android 13-15 и вибромоторов X-axis (включая устройства iQOO/Vivo).
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

    private fun performVibration(effect: VibrationEffect, isNotification: Boolean) {
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val usage = if (isNotification) {
                    VibrationAttributes.USAGE_NOTIFICATION
                } else {
                    VibrationAttributes.USAGE_TOUCH
                }
                val attrs = VibrationAttributes.createForUsage(usage)
                vib.vibrate(effect, attrs)
            } else {
                vib.vibrate(effect)
            }
        } catch (_: Exception) {}
    }

    /**
     * Выполняет вибрацию заданной длительности и амплитуды.
     * intensity: 1 (слабая), 2 (средняя), 3 (сильная).
     */
    private fun vibrate(durationMs: Long, intensity: Int, isNotification: Boolean = false) {
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return

        try {
            val safeDuration = durationMs.coerceAtLeast(60L)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val amplitude = when (intensity) {
                    1 -> 70
                    3 -> 255
                    else -> 160
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

    private fun vibratePattern(timings: LongArray, amplitudes: IntArray, isNotification: Boolean = false) {
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
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val effect = when (settings.hapticIntensity) {
                3 -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
                else -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
            }
            performVibration(effect, isNotification = false)
        } else {
            vibrate(60L, settings.hapticIntensity, isNotification = false)
        }
    }

    /** Вибрация при успешном завершении задачи агентом */
    fun onTaskComplete(settings: AppSettings) {
        if (!settings.hapticEnabled || !settings.hapticOnComplete) return
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val effect = VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK)
            performVibration(effect, isNotification = true)
        } else {
            val amp = when (settings.hapticIntensity) {
                1 -> 90
                3 -> 255
                else -> 170
            }
            // Двойной импульс завершения: 65 мс, пауза 70 мс, 65 мс
            vibratePattern(
                longArrayOf(0, 65, 70, 65),
                intArrayOf(0, amp, 0, amp),
                isNotification = true
            )
        }
    }

    /** Вибрация при запросе подтверждения (план или опасное действие) */
    fun onConfirmationRequired(settings: AppSettings) {
        if (!settings.hapticEnabled || !settings.hapticOnConfirm) return
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val effect = VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
            performVibration(effect, isNotification = false)
        } else {
            vibrate(75L, settings.hapticIntensity, isNotification = false)
        }
    }

    /** Вибрация при ошибке выполнения */
    fun onError(settings: AppSettings) {
        if (!settings.hapticEnabled || !settings.hapticOnError) return
        val amp = when (settings.hapticIntensity) {
            1 -> 110
            3 -> 255
            else -> 200
        }
        // Тройной тревожный импульс по 65 мс (минимальная длина 50-80 мс)
        vibratePattern(
            longArrayOf(0, 65, 50, 65, 50, 65),
            intArrayOf(0, amp, 0, amp, 0, amp),
            isNotification = true
        )
    }

    /** Тестовая вибрация для предпросмотра силы в настройках */
    fun testVibration(intensity: Int) {
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val effect = if (intensity == 3) {
                VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
            } else {
                VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
            }
            performVibration(effect, isNotification = false)
        } else {
            vibrate(60L, intensity, isNotification = false)
        }
    }
}
