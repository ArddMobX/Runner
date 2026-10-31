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

object TaskNotificationManager {

    const val CHANNEL_ID = "runner_task_notifications"
    private const val NOTIFICATION_ID_COMPLETED = 1001
    private const val NOTIFICATION_ID_CONFIRMATION = 1002

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

        val title = if (!sessionTitle.isNullOrBlank()) "Задача выполнена: $sessionTitle" else "Задача выполнена"
        val body = if (!previewText.isNullOrBlank()) previewText.take(150) else "Агент завершил выполнение шагов."

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(previewText ?: body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID_COMPLETED, notification)
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

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Требуется подтверждение")
            .setContentText(actionTitle.ifBlank { "Агент ожидает решения перед выполнением операции" })
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
    }
}
