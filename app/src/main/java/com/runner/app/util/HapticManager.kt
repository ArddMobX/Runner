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
 * Менеджер тактильного отклика.
 *
 * Две вещи, которые уже ломались и должны остаться сломанными только в памяти:
 *
 * 1. Вибратор ищется по строковому ключу `VIBRATOR_SERVICE`. Типизированный
 *    `getSystemService(Vibrator::class.java)` возвращает null на части прошивок,
 *    потому что класс не зарегистрирован как ключ службы. Из-за этого отклик
 *    молча пропадал целиком, включая кнопку проверки в настройках.
 * 2. Сила вибрации задаётся амплитудой только там, где мотор это умеет.
 *    Если `hasAmplitudeControl()` == false, все три уровня звучали одинаково,
 *    поэтому там интенсивность меняет ещё и длительность импульса.
 */
class HapticManager(private val context: Context) {

    private val vibrator: Vibrator? by lazy { resolveVibrator() }

    private fun resolveVibrator(): Vibrator? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                manager?.defaultVibrator?.let { return it }
            } catch (e: Exception) {
                Log.w(TAG, "VibratorManager недоступен", e)
            }
        }
        try {
            @Suppress("DEPRECATION")
            val legacy = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (legacy != null) return legacy
        } catch (e: Exception) {
            Log.w(TAG, "вибратор недоступен", e)
        }
        Log.w(TAG, "вибратор не найден на устройстве")
        return null
    }

    /**
     * Приводит длительность к силе, когда амплитуду мотор не поддерживает.
     * Иначе «слабая» и «сильная» ощущаются одинаково.
     */
    private fun scaledDuration(durationMs: Long, intensity: Int): Long = when (intensity) {
        1 -> (durationMs * 0.7f).toLong()
        3 -> (durationMs * 1.8f).toLong()
        else -> durationMs
    }

    private fun scaledTimings(timings: LongArray, intensity: Int): LongArray {
        if (intensity == 2) return timings
        // Паузы (нулевая амплитуда) не трогаем, длится должны только импульсы.
        return LongArray(timings.size) { index ->
            if (index == 0) timings[index] else scaledDuration(timings[index], intensity)
        }
    }

    /**
     * @param usage канал вибрации; null означает «без атрибутов», как в старых
     * версиях Android. Так делаются отклик на касание и тест из настроек:
     * они не должны зависеть от правил уведомлений.
     */
    private fun performVibration(effect: VibrationEffect, usage: Int?) {
        val vib = vibrator
        if (vib == null) {
            Log.w(TAG, "вибрация пропущена: вибратор недоступен")
            return
        }
        if (!vib.hasVibrator()) {
            Log.w(TAG, "вибрация пропущена: у устройства нет вибромотора")
            return
        }

        try {
            if (usage != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                vib.vibrate(effect, VibrationAttributes.createForUsage(usage))
            } else {
                vib.vibrate(effect)
            }
        } catch (e: Exception) {
            Log.w(TAG, "вибрация с атрибутами не прошла, повтор без них", e)
            try {
                vib.vibrate(effect)
            } catch (e2: Exception) {
                Log.w(TAG, "вибрация не выполнена", e2)
            }
        }
    }

    /** Короткий одиночный импульс. */
    private fun vibrate(durationMs: Long, intensity: Int, usage: Int?) {
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return

        val safeDuration = durationMs.coerceAtLeast(35L)
        val effectiveDuration = if (vib.hasAmplitudeControl()) safeDuration else scaledDuration(safeDuration, intensity)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val amplitude = when (intensity) {
                    1 -> 90
                    3 -> 255
                    else -> 180
                }
                val effectiveAmp = if (vib.hasAmplitudeControl()) amplitude else VibrationEffect.DEFAULT_AMPLITUDE
                performVibration(VibrationEffect.createOneShot(effectiveDuration, effectiveAmp), usage)
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(effectiveDuration)
            }
        } catch (e: Exception) {
            Log.w(TAG, "одиночная вибрация не выполнена", e)
        }
    }

    /** Узор из импульсов. */
    private fun vibratePattern(timings: LongArray, amplitudes: IntArray, intensity: Int, usage: Int?) {
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val hasAmplitude = vib.hasAmplitudeControl()
                val effectiveAmps = if (hasAmplitude) {
                    amplitudes
                } else {
                    amplitudes.map { if (it > 0) VibrationEffect.DEFAULT_AMPLITUDE else 0 }.toIntArray()
                }
                val effectiveTimings = if (hasAmplitude) timings else scaledTimings(timings, intensity)
                val effect = VibrationEffect.createWaveform(effectiveTimings, effectiveAmps, -1)
                performVibration(effect, usage)
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(if (vib.hasAmplitudeControl()) timings else scaledTimings(timings, intensity), -1)
            }
        } catch (e: Exception) {
            Log.w(TAG, "вибрация узором не выполнена", e)
        }
    }

    /** Вибрация при отправке сообщения. */
    fun onSendMessage(settings: AppSettings) {
        if (!settings.hapticEnabled || !settings.hapticOnSend) return
        // Без атрибутов: это отклик на касание, а не уведомление.
        vibrate(40L, settings.hapticIntensity, usage = null)
    }

    /** Готово: два отчётливых импульса. */
    fun onTaskComplete(settings: AppSettings) {
        if (!settings.hapticEnabled || !settings.hapticOnComplete) return
        val amp = when (settings.hapticIntensity) {
            1 -> 110
            3 -> 255
            else -> 190
        }
        vibratePattern(
            longArrayOf(0, 60, 70, 75),
            intArrayOf(0, amp, 0, amp),
            settings.hapticIntensity,
            usage = VibrationAttributes.USAGE_NOTIFICATION
        )
    }

    /** Нужно подтверждение. */
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
            settings.hapticIntensity,
            usage = VibrationAttributes.USAGE_NOTIFICATION
        )
    }

    /** Ошибка. */
    fun onError(settings: AppSettings) {
        if (!settings.hapticEnabled || !settings.hapticOnError) return
        val amp = when (settings.hapticIntensity) {
            1 -> 120
            3 -> 255
            else -> 210
        }
        vibratePattern(
            longArrayOf(0, 65, 50, 65, 50, 65),
            intArrayOf(0, amp, 0, amp, 0, amp),
            settings.hapticIntensity,
            usage = VibrationAttributes.USAGE_NOTIFICATION
        )
    }

    /**
     * Короткий тест из настроек.
     *
     * Идёт без атрибутов: пользователь нажал кнопку и ждёт, что почувствует.
     * Правила уведомлений здесь только мешают, из-за них кнопка «молчит».
     */
    fun testVibration(intensity: Int) {
        vibrate(60L, intensity, usage = null)
    }

    /** Тест на 3 секунды: пять импульсов по 450 мс. */
    fun testVibration3Seconds(intensity: Int) {
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return

        val amp = when (intensity) {
            1 -> 110
            3 -> 255
            else -> 190
        }
        val timings = longArrayOf(0, 450, 80, 450, 80, 450, 80, 450, 80, 450)
        val amplitudes = intArrayOf(0, amp, 0, amp, 0, amp, 0, amp, 0, amp)
        vibratePattern(timings, amplitudes, intensity, usage = null)
    }

    /** Остановка активной вибрации. */
    fun stopVibration() {
        try {
            vibrator?.cancel()
        } catch (_: Exception) {
            // Уборка: отменять нечего, если мотор уже отпустил эффект.
        }
    }

    private companion object {
        const val TAG = "RunnerHaptics"
    }
}
