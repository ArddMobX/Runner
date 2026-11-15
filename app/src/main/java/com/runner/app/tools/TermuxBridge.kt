package com.runner.app.tools

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Мост в Termux через RUN_COMMAND.
 *
 * Обычный run_shell_command работает в песочнице Android и не видит pkg, apt,
 * python и ffmpeg из Termux. Здесь команда уходит в Termux, выполняется в его
 * окружении, а stdout/stderr/код возвращаются через PendingIntent.
 */
object TermuxBridge {

    const val TERMUX_PACKAGE = "com.termux"
    const val PERMISSION_RUN_COMMAND = "com.termux.permission.RUN_COMMAND"
    const val ACTION_RESULT = "com.runner.app.TERMUX_RESULT"
    const val EXTRA_EXECUTION_ID = "execution_id"
    const val EXTRA_RESULT_BUNDLE = "result"

    private const val TERMUX_SERVICE = "com.termux.app.RunCommandService"
    private const val ACTION_RUN_COMMAND = "com.termux.RUN_COMMAND"
    private const val EXTRA_COMMAND_PATH = "com.termux.RUN_COMMAND_PATH"
    private const val EXTRA_ARGUMENTS = "com.termux.RUN_COMMAND_ARGUMENTS"
    private const val EXTRA_WORKDIR = "com.termux.RUN_COMMAND_WORKDIR"
    private const val EXTRA_BACKGROUND = "com.termux.RUN_COMMAND_BACKGROUND"
    private const val EXTRA_SESSION_ACTION = "com.termux.RUN_COMMAND_SESSION_ACTION"
    private const val EXTRA_COMMAND_LABEL = "com.termux.RUN_COMMAND_COMMAND_LABEL"
    private const val EXTRA_COMMAND_DESCRIPTION = "com.termux.RUN_COMMAND_COMMAND_DESCRIPTION"
    private const val EXTRA_PENDING_INTENT = "com.termux.RUN_COMMAND_PENDING_INTENT"

    const val RESULT_STDOUT = "stdout"
    const val RESULT_STDERR = "stderr"
    const val RESULT_EXIT_CODE = "exitCode"
    const val RESULT_ERR = "err"
    const val RESULT_ERRMSG = "errmsg"

    private const val TERMUX_PREFIX = "/data/data/com.termux/files/usr"
    private const val TERMUX_HOME = "/data/data/com.termux/files/home"

    private const val MAX_SHOWN_OUTPUT = 8_000

    private val ANSI_ESCAPES = Regex("\u001B\\[[;\\d]*[ -/]*[@-~]")

    private val nextExecutionId = AtomicInteger(1000)
    private val pending = ConcurrentHashMap<Int, CompletableDeferred<TermuxResult>>()

    data class TermuxResult(
        val stdout: String,
        val stderr: String,
        val exitCode: Int,
        val err: Int,
        val errMsg: String
    )

    fun isInstalled(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(TERMUX_PACKAGE, 0)
        true
    } catch (_: Exception) {
        false
    }

    fun hasRunCommandPermission(context: Context): Boolean =
        context.checkSelfPermission(PERMISSION_RUN_COMMAND) == PackageManager.PERMISSION_GRANTED

    fun versionName(context: Context): String? = try {
        context.packageManager.getPackageInfo(TERMUX_PACKAGE, 0).versionName
    } catch (_: Exception) {
        null
    }

    fun status(context: Context): String {
        if (!isInstalled(context)) return "Termux не установлен"
        val version = versionName(context)?.let { " v$it" }.orEmpty()
        val permission = if (hasRunCommandPermission(context)) {
            "разрешение выдано"
        } else {
            "разрешение не выдано"
        }
        return "Termux$version, $permission"
    }

    suspend fun run(
        context: Context,
        command: String,
        timeoutSeconds: Int,
        label: String = "Termux",
        shouldStop: () -> Boolean = { false }
    ): String {
        if (command.isBlank()) return "Ошибка: команда не задана."
        if (!isInstalled(context)) {
            return "Termux не установлен. Установите Termux с F-Droid или GitHub, " +
                    "не из Play Store: старый bootstrap ломает pkg."
        }
        val version = versionName(context)
        if (!supportsResultCallback(version)) {
            return "Termux слишком старый ($version). Нужна версия 0.109 или новее, " +
                    "иначе RUN_COMMAND не возвращает результат."
        }
        if (!hasRunCommandPermission(context)) {
            return "Нет разрешения com.termux.permission.RUN_COMMAND. " +
                    "Откройте настройки Android → Приложения → Runner → Дополнительные " +
                    "разрешения → «Run commands in Termux environment»."
        }

        val executionId = nextExecutionId.incrementAndGet()
        val deferred = CompletableDeferred<TermuxResult>()
        pending[executionId] = deferred

        val resultIntent = Intent(context, com.runner.app.service.TermuxResultReceiver::class.java).apply {
            action = ACTION_RESULT
            putExtra(EXTRA_EXECUTION_ID, executionId)
        }
        val pendingFlags = PendingIntent.FLAG_ONE_SHOT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    PendingIntent.FLAG_MUTABLE
                } else {
                    0
                }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            executionId,
            resultIntent,
            pendingFlags
        )

        val intent = Intent().apply {
            setClassName(TERMUX_PACKAGE, TERMUX_SERVICE)
            action = ACTION_RUN_COMMAND
            putExtra(EXTRA_COMMAND_PATH, "$TERMUX_PREFIX/bin/bash")
            putExtra(EXTRA_ARGUMENTS, arrayOf("-lc", command))
            putExtra(EXTRA_WORKDIR, TERMUX_HOME)
            putExtra(EXTRA_BACKGROUND, true)
            putExtra(EXTRA_SESSION_ACTION, "0")
            putExtra(EXTRA_COMMAND_LABEL, "Runner: ${compactCommand(command)}")
            putExtra(EXTRA_COMMAND_DESCRIPTION, "Команда из Runner")
            putExtra(EXTRA_PENDING_INTENT, pendingIntent)
        }

        try {
            context.startService(intent)
        } catch (e: Exception) {
            pending.remove(executionId)
            return "Не удалось запустить Termux: ${e.localizedMessage}. " +
                    "Откройте Runner на экране и повторите команду."
        }

        return try {
            val deadline = System.currentTimeMillis() +
                    (timeoutSeconds + 5).coerceAtLeast(10) * 1000L
            while (System.currentTimeMillis() < deadline) {
                if (shouldStop()) {
                    return "Остановлено пользователем. Команда могла продолжить " +
                            "выполняться внутри Termux."
                }
                val result = withTimeoutOrNull(250L) { deferred.await() }
                if (result != null) return formatResult(result, label)
            }
            "Ошибка: Termux не вернул результат за $timeoutSeconds сек. " +
                    "Проверьте, что в Termux включён allow-external-apps=true, " +
                    "Termux не убит системой, а команда не ждёт ввода. " +
                    "Команда могла продолжить выполняться внутри Termux."
        } finally {
            pending.remove(executionId)
        }
    }

    private fun supportsResultCallback(version: String?): Boolean {
        if (version.isNullOrBlank()) return true
        val parts = version.split('.').mapNotNull { it.toIntOrNull() }
        val major = parts.getOrNull(0) ?: 0
        val minor = parts.getOrNull(1) ?: 0
        return major > 0 || minor >= 109
    }

    internal fun onResult(executionId: Int, result: TermuxResult) {
        pending.remove(executionId)?.complete(result)
    }

    private fun formatResult(result: TermuxResult, label: String): String {
        val stdout = ANSI_ESCAPES.replace(result.stdout, "").trim()
        val stderr = ANSI_ESCAPES.replace(result.stderr, "").trim()
        val shownStdout = stdout.take(MAX_SHOWN_OUTPUT)
        val shownStderr = stderr.take(MAX_SHOWN_OUTPUT)
        val internalError = result.err != 0 && result.err != Activity.RESULT_OK
        return buildString {
            if (internalError) {
                append("Ошибка Termux: ")
                append(result.errMsg.ifBlank { "внутренний код ${result.err}" })
                append("\nКод выполнения: ${result.exitCode}")
            } else {
                append("Выполнено (код ${result.exitCode}) [$label]:")
            }
            if (shownStdout.isNotBlank()) {
                append("\nSTDOUT:\n```\n").append(shownStdout).append("\n```")
            }
            if (shownStderr.isNotBlank()) {
                append("\nSTDERR:\n```\n").append(shownStderr).append("\n```")
            }
            if (shownStdout.isBlank() && shownStderr.isBlank()) {
                append("\n[Вывод пуст]")
            }
            if (stdout.length > shownStdout.length) {
                append("\n[STDOUT обрезан: ${stdout.length} символов]")
            }
            if (stderr.length > shownStderr.length) {
                append("\n[STDERR обрезан: ${stderr.length} символов]")
            }
        }
    }

    private fun compactCommand(command: String): String {
        val flat = command.replace(Regex("\\s+"), " ").trim()
        return if (flat.length <= 60) flat else flat.take(59).trimEnd() + "…"
    }
}
