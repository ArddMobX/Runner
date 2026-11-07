package com.runner.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Фоновая служба переднего плана (Foreground Service) с типом mediaProjection,
 * обязательная в Android 14+ (API 34+) для использования MediaProjection API.
 */
class ScreenCaptureService : Service() {

    companion object {
        const val CHANNEL_ID = "runner_screen_capture"
        const val NOTIFICATION_ID = 1001

        /**
         * Сигнал «служба уже в foreground».
         *
         * Начиная с Android 14 проекцию нельзя запрашивать, пока foreground-служба
         * нужного типа не работает. Раньше здесь стояла пауза 150 мс «на глазок»:
         * на медленном старте проекция не создавалась, и захват молча возвращал
         * null. Теперь ждём настоящего события.
         */
        @Volatile
        private var readySignal = CompletableDeferred<Unit>()

        fun start(context: Context): CompletableDeferred<Unit> {
            val signal = CompletableDeferred<Unit>()
            readySignal = signal
            try {
                val intent = Intent(context, ScreenCaptureService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (_: Exception) {
                // Служба не поднялась: не держим вызывающего в ожидании,
                // проекция всё равно не создастся.
                signal.complete(Unit)
            }
            return signal
        }

        /** Ждёт, пока служба войдёт в foreground. false — не успела. */
        suspend fun awaitForeground(timeoutMs: Long = 3_000L): Boolean =
            withTimeoutOrNull(timeoutMs) {
                readySignal.await()
                true
            } ?: false

        fun stop(context: Context) {
            context.stopService(Intent(context, ScreenCaptureService::class.java))
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            } else {
                0
            }
            startForeground(NOTIFICATION_ID, notification, type)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        // Только после startForeground: до этого система ещё не считает
        // службу foreground, и проекция не создастся.
        readySignal.complete(Unit)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Захват экрана",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Используется для моментального снимка экрана"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Runner")
            .setContentText("Снимок экрана...")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
