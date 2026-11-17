package com.runner.app.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.runner.app.MainActivity
import com.runner.app.R
import com.runner.app.data.SettingsStore

object TaskNotificationManager {

    const val CHANNEL_ID = "runner_task_notifications"

    // 3001+, чтобы не пересекаться с foreground-уведомлениями служб:
    // ScreenCaptureService занимает 1001, AgentExecutionService — 2001.
    private const val NOTIFICATION_ID_COMPLETED = 3001
    private const val NOTIFICATION_ID_CONFIRMATION = 3002
    private const val NOTIFICATION_ID_FAILED = 3003

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Уведомления о задачах"
            val descriptionText = "Уведомления о завершении работы агента и запросах подтверждения"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableVibration(true)
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun canPostNotifications(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun hidePreviews(context: Context): Boolean = try {
        SettingsStore(context).load().hideNotificationPreviews
    } catch (_: Exception) {
        false
    }

    fun notifyTaskCompleted(
        context: Context,
        sessionId: String? = null,
        sessionTitle: String? = null,
        previewText: String? = null
    ) {
        if (!canPostNotifications(context)) return
        createNotificationChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (sessionId != null) {
                putExtra("SESSION_ID", sessionId)
            }
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID_COMPLETED,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val privateMode = hidePreviews(context)
        val title = if (privateMode) {
            "Задача выполнена"
        } else if (!sessionTitle.isNullOrBlank()) {
            "Задача выполнена: $sessionTitle"
        } else {
            "Задача выполнена"
        }
        val body = if (privateMode) {
            "Откройте Runner, чтобы посмотреть результат."
        } else if (!previewText.isNullOrBlank()) {
            previewText.take(150)
        } else {
            "Откройте Runner, чтобы посмотреть результат."
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_qs_runner)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    if (privateMode) body else previewText ?: body
                )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID_COMPLETED, notification)
    }

    /**
     * Задача завершилась ошибкой. Отдельный заголовок и ID: иначе в фоне
     * приходило «Задача выполнена» с последним текстом ассистента, хотя
     * запрос упал с 401/429/500.
     */
    fun notifyTaskFailed(
        context: Context,
        sessionId: String? = null,
        sessionTitle: String? = null,
        errorText: String? = null
    ) {
        if (!canPostNotifications(context)) return
        createNotificationChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (sessionId != null) {
                putExtra("SESSION_ID", sessionId)
            }
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID_FAILED,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val privateMode = hidePreviews(context)
        val title = if (privateMode) {
            "Задача завершилась с ошибкой"
        } else if (!sessionTitle.isNullOrBlank()) {
            "Ошибка: $sessionTitle"
        } else {
            "Задача завершилась с ошибкой"
        }
        val details = if (privateMode) {
            "Откройте Runner и посмотрите историю."
        } else {
            errorText?.take(300)?.takeIf { it.isNotBlank() }
                ?: "Агент не смог завершить задачу. Откройте Runner и посмотрите историю."
        }
        val body = if (privateMode) {
            "Откройте Runner и посмотрите историю."
        } else {
            errorText?.take(150)?.takeIf { it.isNotBlank() }
                ?: "Агент не смог завершить задачу."
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_qs_runner)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(details))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID_FAILED, notification)
    }

    fun notifyConfirmationRequired(
        context: Context,
        sessionId: String? = null,
        actionTitle: String
    ) {
        if (!canPostNotifications(context)) return
        createNotificationChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (sessionId != null) {
                putExtra("SESSION_ID", sessionId)
            }
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID_CONFIRMATION,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val privateMode = hidePreviews(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_qs_runner)
            .setContentTitle("Требуется подтверждение")
            .setContentText(
                if (privateMode) {
                    "Агент ждёт подтверждения операции"
                } else {
                    actionTitle.ifBlank { "Агент ждёт подтверждения операции" }
                }
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID_CONFIRMATION, notification)
    }

    fun cancelAll(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID_COMPLETED)
        notificationManager.cancel(NOTIFICATION_ID_CONFIRMATION)
        notificationManager.cancel(NOTIFICATION_ID_FAILED)
    }
}
