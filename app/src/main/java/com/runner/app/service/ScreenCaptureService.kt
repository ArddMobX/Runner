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
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Фоновая служба переднего плана (Foreground Service) с типом mediaProjection,
 * обязательная в Android 10+ (API 29+) и особенно строго проверяемая в Android 14–15
 * для использования MediaProjection API.
 */
class ScreenCaptureService : Service() {

    companion object {
        private const val TAG = "RunnerScreenCapSvc"
        const val CHANNEL_ID = "runner_screen_capture"
        const val NOTIFICATION_ID = 1001

        /**
         * Сигнал «служба успешно вошла в foreground (true) или запуск сорвался (false)».
         *
         * Начиная с Android 10–14 проекцию нельзя запрашивать, пока foreground-служба
         * типа mediaProjection не активна. Раньше здесь стояла пауза 150 мс «на глазок»,
         * а затем сигнал завершался успехом даже в ветке catch.
         */
        @Volatile
        private var readySignal = CompletableDeferred<Boolean>()

        fun start(context: Context): CompletableDeferred<Boolean> {
            val signal = CompletableDeferred<Boolean>()
            readySignal = signal
            try {
                val intent = Intent(context, ScreenCaptureService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.w(TAG, "не удалось запустить foreground-службу захвата экрана", e)
                signal.complete(false)
            }
            return signal
        }

        /** Ждёт, пока служба войдёт в foreground. false — запуск сорвался или не успел. */
        suspend fun awaitForeground(timeoutMs: Long = 3_000L): Boolean =
            withTimeoutOrNull(timeoutMs) {
                readySignal.await()
            } ?: false

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, ScreenCaptureService::class.java))
            } catch (_: Exception) {}
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        if (!promoteToForeground()) {
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Повторный запуск уже созданной службы не вызывает onCreate():
        // подтверждаем foreground-статус и здесь, чтобы readySignal не зависал.
        if (!promoteToForeground()) {
            stopSelf()
        }
        // START_NOT_STICKY обязателен: перезапуск mediaProjection-службы системой
        // после убийства процесса без нового согласия пользователя роняет API 34+.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        readySignal.complete(false)
        super.onDestroy()
    }

    private fun promoteToForeground(): Boolean {
        val notification = createNotification()
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // На всех Android 10–15 (API 29–35) обязателен тип FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION:
                // передача 0 на API 29–33 вызывает SecurityException при getMediaProjection().
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            readySignal.complete(true)
            true
        } catch (e: Exception) {
            Log.w(TAG, "startForeground для захвата экрана отклонён системой", e)
            readySignal.complete(false)
            false
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Захват экрана",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Используется для снимка экрана"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Runner")
            .setContentText("Снимок экрана…")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
