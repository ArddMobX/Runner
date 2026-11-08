package com.runner.app.util

import android.content.Context
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.runner.app.data.AppSettings

/**
 * Менеджер тактильного отклика (вибрации) устройства с учётом настроек пользователя.
 *
 * Канал вибрации выбирается осознанно:
 * - Обычный тап интерфейса идет как USAGE_TOUCH.
 * - Завершение задачи, подтверждение и ошибка - как USAGE_NOTIFICATION.
 * - Явные тесты вибрации в настройках используют USAGE_ALARM, чтобы мотор гарантированно
 *   срабатывал даже если в системе Android отключен «Тактильный отклик» или включен беззвучный режим.
 */
class HapticManager(private val context: Context) {

    private val vibrator: Vibrator? by lazy {
        try {
            var vib: Vibrator? = null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                val defVib = vm?.defaultVibrator
                if (defVib != null && defVib.hasVibrator()) {
                    vib = defVib
                }
            }
            if (vib == null || !vib.hasVibrator()) {
                val sysVib = context.getSystemService(Vibrator::class.java)
                if (sysVib != null && (vib == null || sysVib.hasVibrator())) {
                    vib = sysVib
                }
            }
            if (vib == null) {
                @Suppress("DEPRECATION")
                vib = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            vib
        } catch (e: Exception) {
            Log.w(TAG, "вибратор недоступен", e)
            try {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            } catch (_: Exception) { null }
        }
    }

    /** Проверяет физическое наличие вибромотора на устройстве */
    fun hasVibrator(): Boolean {
        return try {
            vibrator?.hasVibrator() == true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Выполняет вибрацию с заданным эффектом и атрибутами использования.
     */
    private fun performVibration(
        effect: VibrationEffect,
        usage: Int = VibrationAttributes.USAGE_NOTIFICATION
    ) {
        val vib = vibrator ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val attrs = VibrationAttributes.createForUsage(usage)
                vib.vibrate(effect, attrs)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val audioUsage = when (usage) {
                    VibrationAttributes.USAGE_ALARM -> android.media.AudioAttributes.USAGE_ALARM
                    VibrationAttributes.USAGE_TOUCH -> android.media.AudioAttributes.USAGE_ASSISTANCE_SONIFICATION
                    else -> android.media.AudioAttributes.USAGE_NOTIFICATION
                }
                val attrs = android.media.AudioAttributes.Builder()
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(audioUsage)
                    .build()
                vib.vibrate(effect, attrs)
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(200L)
            }
        } catch (e: Exception) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vib.vibrate(effect)
                } else {
                    @Suppress("DEPRECATION")
                    vib.vibrate(200L)
                }
            } catch (e2: Exception) {
                Log.w(TAG, "вибрация не выполнена", e2)
            }
        }
    }

    /**
     * Выполняет вибрацию заданной длительности и амплитуды.
     * intensity: 1 (слабая), 2 (средняя), 3 (сильная).
     */
    private fun vibrate(durationMs: Long, intensity: Int, asNotification: Boolean = true) {
        val vib = vibrator ?: return

        try {
            val safeDuration = durationMs.coerceAtLeast(35L)
            val usage = if (asNotification) {
                VibrationAttributes.USAGE_NOTIFICATION
            } else {
                VibrationAttributes.USAGE_TOUCH
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val amplitude = when (intensity) {
                    1 -> 90
                    3 -> 255
                    else -> 180
                }
                val effect = if (vib.hasAmplitudeControl()) {
                    VibrationEffect.createOneShot(safeDuration, amplitude.coerceIn(1, 255))
                } else {
                    VibrationEffect.createOneShot(safeDuration, VibrationEffect.DEFAULT_AMPLITUDE)
                }
                performVibration(effect, usage)
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(safeDuration)
            }
        } catch (_: Exception) {}
    }

    private fun vibratePattern(timings: LongArray, amplitudes: IntArray, asNotification: Boolean = true) {
        val vib = vibrator ?: return

        try {
            val usage = if (asNotification) {
                VibrationAttributes.USAGE_NOTIFICATION
            } else {
                VibrationAttributes.USAGE_TOUCH
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = if (vib.hasAmplitudeControl()) {
                    VibrationEffect.createWaveform(timings, amplitudes, -1)
                } else {
                    VibrationEffect.createWaveform(timings, -1)
                }
                performVibration(effect, usage)
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(timings, -1)
            }
        } catch (_: Exception) {}
    }

    /** Вибрация при отправке сообщения пользователем */
    fun onSendMessage(settings: AppSettings) {
        if (!settings.hapticEnabled || !settings.hapticOnSend) return
        vibrate(40L, settings.hapticIntensity, asNotification = false)
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
            asNotification = true
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
            asNotification = true
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
            asNotification = true
        )
    }

    /** Тестовая короткая вибрация для предпросмотра силы в настройках */
    fun testVibration(intensity: Int) {
        val vib = vibrator ?: return
        val amp = when (intensity) {
            1 -> 130
            3 -> 255
            else -> 190
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = if (vib.hasAmplitudeControl()) {
                    VibrationEffect.createOneShot(120L, amp)
                } else {
                    VibrationEffect.createOneShot(120L, VibrationEffect.DEFAULT_AMPLITUDE)
                }
                performVibration(effect, usage = VibrationAttributes.USAGE_ALARM)
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(120L)
            }
        } catch (e: Exception) {
            try {
                @Suppress("DEPRECATION")
                vib.vibrate(120L)
            } catch (_: Exception) {}
        }
    }

    /** Непрерывный/пульсирующий тест вибрации на 3 секунды */
    fun testVibration3Seconds(intensity: Int) {
        val vib = vibrator ?: return

        val amp = when (intensity) {
            1 -> 140
            3 -> 255
            else -> 200
        }

        val timings = longArrayOf(0, 450, 80, 450, 80, 450, 80, 450, 80, 450)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = if (vib.hasAmplitudeControl()) {
                    val amplitudes = intArrayOf(0, amp, 0, amp, 0, amp, 0, amp, 0, amp)
                    VibrationEffect.createWaveform(timings, amplitudes, -1)
                } else {
                    VibrationEffect.createWaveform(timings, -1)
                }
                performVibration(effect, usage = VibrationAttributes.USAGE_ALARM)
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(timings, -1)
            }
        } catch (e: Exception) {
            try {
                @Suppress("DEPRECATION")
                vib.vibrate(timings, -1)
            } catch (e2: Exception) {
                try {
                    @Suppress("DEPRECATION")
                    vib.vibrate(3000L)
                } catch (e3: Exception) {
                    Log.w(TAG, "тестовая вибрация не выполнена", e3)
                }
            }
        }
    }

    /** Остановка любой активной тестовой вибрации */
    fun stopVibration() {
        try {
            vibrator?.cancel()
        } catch (_: Exception) {
            // Отмена - уборка: если мотор уже отпустил эффект, падать некуда.
        }
    }

    private companion object {
        const val TAG = "RunnerHaptics"
    }
}
