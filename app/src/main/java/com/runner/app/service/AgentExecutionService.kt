package com.runner.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.runner.app.MainActivity
import com.runner.app.R

/**
 * Фоновая служба переднего плана (Foreground Service) с типом dataSync.
 * Удерживает Partial WakeLock и WifiLock во время выполнения задач агента,
 * предотвращая засыпание процессора и обрыв сокетов OkHttp при сворачивании приложения.
 */
class AgentExecutionService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var currentStatusText: String = "Выполняется..."

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        when (action) {
            ACTION_STOP -> {
                // «Стоп» из уведомления останавливает ВСЕ задачи: у чата и у
                // плавающего ассистента свои ViewModel, и раньше здесь лежал
                // один колбэк, который перезаписывался при открытии оверлея —
                // кнопка попадала в уже уничтоженную ViewModel и не делала
                // ничего, а настоящая задача продолжала жечь лимит.
                fireStopHandlers()
                activeTasks.set(0)
                stopForegroundAndSelf()
                return START_NOT_STICKY
            }
            ACTION_RELEASE -> {
                // Задача завершилась сама. Гасим службу, но НЕ трогаем
                // обработчики «Стоп»: это обычное завершение, а не остановка
                // пользователем. Раньше здесь вызывался тот же колбэк, что и
                // у кнопки, и завершение задачи обрывало озвучку ответа,
                // которую только что запустил авто-speak.
                activeTasks.set(0)
                stopForegroundAndSelf()
                return START_NOT_STICKY
            }
            ACTION_UPDATE_STATUS -> {
                val status = intent.getStringExtra(EXTRA_STATUS)
                if (!status.isNullOrBlank()) {
                    currentStatusText = status
                    val manager = getSystemService(NotificationManager::class.java)
                    manager?.notify(NOTIFICATION_ID, createNotification(currentStatusText))
                }
            }
            ACTION_START -> {
                val status = intent.getStringExtra(EXTRA_STATUS)
                if (!status.isNullOrBlank()) {
                    currentStatusText = status
                }
                acquireLocks()
                val notification = createNotification(currentStatusText)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    } else {
                        0
                    }
                    startForeground(NOTIFICATION_ID, notification, type)
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            }
            else -> {
                if (wakeLock == null) {
                    acquireLocks()
                    val notification = createNotification(currentStatusText)
                    startForeground(NOTIFICATION_ID, notification)
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun acquireLocks() {
        try {
            if (wakeLock == null) {
                val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
                wakeLock = powerManager?.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "Runner:AgentExecutionWakeLock"
                )?.apply {
                    setReferenceCounted(false)
                    // Максимальное время удержания — 15 минут в целях безопасности
                    acquire(15 * 60 * 1000L)
                }
            }
        } catch (_: Exception) {}

        try {
            if (wifiLock == null) {
                val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                wifiLock = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    wifiManager?.createWifiLock(
                        WifiManager.WIFI_MODE_FULL_LOW_LATENCY,
                        "Runner:AgentExecutionWifiLock"
                    )
                } else {
                    @Suppress("DEPRECATION")
                    wifiManager?.createWifiLock(
                        WifiManager.WIFI_MODE_FULL,
                        "Runner:AgentExecutionWifiLock"
                    )
                }?.apply {
                    setReferenceCounted(false)
                    acquire()
                }
            }
        } catch (_: Exception) {}
    }

    private fun releaseLocks() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {}
        wakeLock = null

        try {
            if (wifiLock?.isHeld == true) {
                wifiLock?.release()
            }
        } catch (_: Exception) {}
        wifiLock = null
    }

    private fun stopForegroundAndSelf() {
        releaseLocks()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    override fun onDestroy() {
        releaseLocks()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Выполнение задач агента",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Отображает текущий статус работы Runner и предотвращает остановку сети"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(statusText: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingOpenApp = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, AgentExecutionService::class.java).apply {
            action = ACTION_STOP
        }
        val pendingStop = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Runner выполняет задачу...")
            .setContentText(statusText)
            .setSubText("Runner")
            .setSmallIcon(R.drawable.ic_qs_runner)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingOpenApp)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Остановить",
                pendingStop
            )
            .build()
    }

    companion object {
        const val CHANNEL_ID = "runner_execution_channel"
        const val NOTIFICATION_ID = 2001

        const val ACTION_START = "com.runner.app.action.START_EXECUTION"
        const val ACTION_STOP = "com.runner.app.action.STOP_EXECUTION"
        const val ACTION_RELEASE = "com.runner.app.action.RELEASE_EXECUTION"
        const val ACTION_UPDATE_STATUS = "com.runner.app.action.UPDATE_STATUS"
        const val EXTRA_STATUS = "extra_status"

        /**
         * Сколько задач сейчас выполняется.
         *
         * Служба одна на приложение, а задач может быть две: обычный чат и
         * плавающий ассистент (OverlayActivity со своей ViewModel). Раньше
         * каждый, кто заканчивал первым, гасил службу целиком — вторая задача
         * оставалась без foreground-режима и без wake-lock.
         */
        private val activeTasks = java.util.concurrent.atomic.AtomicInteger(0)

        /**
         * Обработчики «Стоп» по владельцу.
         *
         * Ключ — идентификатор ViewModel, а не одна переменная: у чата и
         * оверлея свои экземпляры, и общий колбэк перезаписывался тем, кто
         * создан позже. Владелец обязан снимать обработчик в onCleared,
         * иначе статическая карта держала бы уничтоженную ViewModel.
         */
        private val stopHandlers =
            java.util.concurrent.ConcurrentHashMap<String, () -> Unit>()

        fun registerStopHandler(ownerId: String, handler: () -> Unit) {
            stopHandlers[ownerId] = handler
        }

        fun unregisterStopHandler(ownerId: String) {
            stopHandlers.remove(ownerId)
        }

        /** Вызывается в службе на ACTION_STOP: останавливаем всех. */
        private fun fireStopHandlers() {
            // Копия: обработчик может снять себя прямо во время вызова.
            stopHandlers.values.toList().forEach { handler ->
                try {
                    handler()
                } catch (_: Exception) {}
            }
        }

        fun start(context: Context, status: String = "Выполняется...") {
            activeTasks.incrementAndGet()
            try {
                val intent = Intent(context, AgentExecutionService::class.java).apply {
                    action = ACTION_START
                    putExtra(EXTRA_STATUS, status)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (_: Exception) {
                activeTasks.decrementAndGet()
            }
        }

        fun updateStatus(context: Context, status: String) {
            // Нечего показывать, если задач нет: иначе после завершения
            // последней в уведомлении остаётся чужой статус.
            if (activeTasks.get() <= 0) return
            try {
                val intent = Intent(context, AgentExecutionService::class.java).apply {
                    action = ACTION_UPDATE_STATUS
                    putExtra(EXTRA_STATUS, status)
                }
                context.startService(intent)
            } catch (_: Exception) {}
        }

        /**
         * Одна задача завершилась. Служба гасится только когда не осталось
         * ни одной — иначе завершение задачи в оверлее обрывало бы основную.
         */
        fun release(context: Context) {
            val previous = activeTasks.getAndUpdate { current -> if (current > 0) current - 1 else 0 }
            // Только последняя задача гасит службу. Если задач не осталось
            // (принудительная остановка уже была) — новый интент не нужен:
            // служба создалась бы ради немедленной остановки.
            if (previous != 1) return
            try {
                val intent = Intent(context, AgentExecutionService::class.java).apply {
                    action = ACTION_RELEASE
                }
                context.startService(intent)
            } catch (_: Exception) {}
        }

        /** Принудительная остановка: все задачи считаются прерванными. */
        fun stop(context: Context) {
            activeTasks.set(0)
            try {
                val intent = Intent(context, AgentExecutionService::class.java).apply {
                    action = ACTION_STOP
                }
                context.startService(intent)
            } catch (_: Exception) {}
        }
    }
}
