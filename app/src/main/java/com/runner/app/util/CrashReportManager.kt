package com.runner.app.util

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.runner.app.data.ProviderStore
import com.runner.app.service.RunnerAccessibilityService
import com.runner.app.ui.LiveAgentManager
import java.io.PrintWriter
import java.io.StringWriter
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Менеджер сбора и копирования отчетов о сбоях, ошибках агента и системных неполадках.
 * Сохраняет последние события в кольцевой буфер, перехватывает необработанные исключения
 * и формирует безопасный Markdown-отчет для передачи разработчикам.
 */
object CrashReportManager {
    private const val TAG = "CrashReportManager"
    private const val PREFS_NAME = "runner_crash_reports"
    private const val KEY_LAST_CRASH = "last_uncaught_crash"
    private const val KEY_LAST_CRASH_TIME = "last_uncaught_crash_time"
    private const val MAX_LOG_EVENTS = 50

    private val logEvents = ConcurrentLinkedQueue<String>()
    // DateTimeFormatter, в отличие от SimpleDateFormat, потокобезопасен:
    // log() зовут и UI, и фоновые потоки инструментов.
    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.getDefault())
    private val fullDateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    @Volatile
    private var lastActivePackage: String = ""

    @Volatile
    private var lastErrorDetail: String = ""

    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        initialized = true

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                recordCrash(context.applicationContext, thread, throwable)
            } catch (e: Exception) {
                Log.e(TAG, "не удалось записать краш-дамп", e)
            } finally {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    fun log(tag: String, message: String) {
        val sanitized = sanitize(message)
        val time = LocalTime.now().format(timeFormat)
        val entry = "[$time] $tag: $sanitized"
        logEvents.add(entry)
        while (logEvents.size > MAX_LOG_EVENTS) {
            logEvents.poll()
        }
    }

    fun setLastActivePackage(packageName: String) {
        if (packageName.isNotBlank()) {
            lastActivePackage = packageName
        }
    }

    fun setLastError(error: String) {
        lastErrorDetail = sanitize(error)
        log("Error", error)
    }

    private fun recordCrash(appContext: Context, thread: Thread, throwable: Throwable) {
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        val stackTrace = sw.toString()

        val report = buildReport(
            context = appContext,
            title = "Аварийное завершение приложения (Crash)",
            customContext = "Поток: ${thread.name}\nИсключение: ${throwable.javaClass.name}: ${throwable.message}",
            stackTrace = stackTrace
        )

        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_CRASH, report)
            .putLong(KEY_LAST_CRASH_TIME, System.currentTimeMillis())
            .commit()
    }

    fun hasPendingCrashReport(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return !prefs.getString(KEY_LAST_CRASH, null).isNullOrBlank()
    }

    fun getPendingCrashReport(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LAST_CRASH, null)
    }

    fun clearPendingCrashReport(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_LAST_CRASH)
            .remove(KEY_LAST_CRASH_TIME)
            .apply()
    }

    fun buildReport(
        context: Context,
        title: String = "Отчет о сбое и неполадках",
        customContext: String? = null,
        stackTrace: String? = null
    ): String {
        val packageInfo = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
        } catch (_: Exception) {
            null
        }

        val versionName = packageInfo?.versionName ?: "unknown"
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo?.longVersionCode?.toString() ?: "unknown"
        } else {
            @Suppress("DEPRECATION")
            packageInfo?.versionCode?.toString() ?: "unknown"
        }

        val hasAccessibility = RunnerAccessibilityService.isEnabled(context)
        val hasOverlay = Settings.canDrawOverlays(context)
        val hasStorage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
        val hasAudio = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

        // Провайдер и модель без утечки API-ключа
        val activeProvider = try {
            ProviderStore(context).activeProvider()
        } catch (_: Exception) {
            null
        }
        val providerInfo = if (activeProvider != null) {
            "${activeProvider.name} (${activeProvider.kind}) · Модель: ${activeProvider.activeModel}"
        } else {
            "Провайдер не выбран"
        }

        val liveState = LiveAgentManager.stateFlow.value
        val liveStatus = if (liveState.isExecuting) {
            "Выполняется (шаг ${liveState.stepIndex}/${liveState.totalSteps})"
        } else {
            "Статус: ${liveState.status}, описание: ${liveState.stepDescription.ifEmpty { "нет" }}"
        }

        val runtime = Runtime.getRuntime()
        val usedMemMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
        val maxMemMb = runtime.maxMemory() / (1024 * 1024)

        val sb = StringBuilder()
        sb.append("### ").append(title).append(" - Runner v").append(versionName).append(" (").append(versionCode).append(")\n")
        sb.append("- **Дата и время:** ").append(LocalDateTime.now().format(fullDateFormat)).append("\n")
        sb.append("- **Устройство:** ").append(Build.MANUFACTURER.replaceFirstChar { it.uppercase() }).append(" ").append(Build.MODEL)
            .append(" (").append(Build.DEVICE).append(", Android ").append(Build.VERSION.RELEASE)
            .append(", API ").append(Build.VERSION.SDK_INT).append(")\n")
        sb.append("- **Память:** ").append(usedMemMb).append(" МБ / ").append(maxMemMb).append(" МБ\n")
        sb.append("- **Провайдер:** ").append(providerInfo).append("\n")
        sb.append("- **Live-режим:** ").append(liveStatus).append("\n")
        if (lastActivePackage.isNotBlank()) {
            sb.append("- **Активное приложение на экране:** `").append(lastActivePackage).append("`\n")
        }
        sb.append("- **Разрешения:** ")
            .append("Accessibility: ").append(if (hasAccessibility) "ВКЛ" else "ВЫКЛ").append(", ")
            .append("Overlay: ").append(if (hasOverlay) "ВКЛ" else "ВЫКЛ").append(", ")
            .append("Storage: ").append(if (hasStorage) "ВКЛ" else "ВЫКЛ").append(", ")
            .append("Audio: ").append(if (hasAudio) "ВКЛ" else "ВЫКЛ").append("\n")

        val effectiveContext = customContext ?: lastErrorDetail
        if (effectiveContext.isNotBlank()) {
            sb.append("\n#### Контекст проблемы:\n")
            sb.append(sanitize(effectiveContext).trim()).append("\n")
        }

        if (!stackTrace.isNullOrBlank()) {
            sb.append("\n#### Стек ошибки:\n```\n")
            sb.append(sanitize(stackTrace).trim()).append("\n```\n")
        }

        val recentLogs = logEvents.toList()
        if (recentLogs.isNotEmpty()) {
            sb.append("\n#### Последние события (LogBuffer):\n```\n")
            for (log in recentLogs.takeLast(30)) {
                sb.append(log).append("\n")
            }
            sb.append("```\n")
        }

        return sb.toString()
    }

    fun copyReport(context: Context, customContext: String? = null, exception: Throwable? = null): Boolean {
        val stackTrace = exception?.let {
            val sw = StringWriter()
            it.printStackTrace(PrintWriter(sw))
            sw.toString()
        }
        val report = buildReport(context, customContext = customContext, stackTrace = stackTrace)

        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        if (clipboard != null) {
            val clip = ClipData.newPlainText("Отчет о сбое Runner", report)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "Отчет о сбое скопирован в буфер", Toast.LENGTH_SHORT).show()
            try {
                HapticManager(context).testVibration(2)
            } catch (_: Exception) {}
            return true
        }
        return false
    }

    private fun sanitize(input: String): String {
        if (input.isBlank()) return ""
        // Маскирование ключей: OpenAI/Anthropic (sk-), Groq (gsk_),
        // Google Gemini (AIza...). Без последних двух ключ уезжал в отчёт
        // как есть, а отчёт копируется в буфер и уходит разработчику.
        var result = input.replace(Regex("""sk-[a-zA-Z0-9_\-]{16,}"""), "sk-...***")
        result = result.replace(Regex("""gsk_[a-zA-Z0-9_\-]{16,}"""), "gsk_...***")
        result = result.replace(Regex("""AIza[0-9A-Za-z_\-]{20,}"""), "AIza...***")
        // Заголовок Gemini, если он попал в текст ошибки или журнал.
        result = result.replace(
            Regex("""x-goog-api-key["']?\s*[:=]\s*["']?[^\s"',}]+""", RegexOption.IGNORE_CASE),
            "x-goog-api-key: ***"
        )
        // Маскирование Bearer токенов
        result = result.replace(Regex("""Bearer\s+[a-zA-Z0-9_\-\.]{16,}""", RegexOption.IGNORE_CASE), "Bearer ...***")
        // Маскирование паролей
        result = result.replace(Regex("""password["':\s]+[^\s,"']+""", RegexOption.IGNORE_CASE), "password: ***")
        return result
    }
}
