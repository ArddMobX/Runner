package com.runner.app.tools

import android.content.Context

/**
 * Лёгкий Shizuku-мост через `rish` в Termux.
 *
 * Это не полный Shizuku SDK с binder-сервисом, а практичный путь для ADB-команд:
 * pm, am, settings, cmd. Пользователь ставит Shizuku, копирует rish в Termux
 * и запускает `rish` оттуда. Runner отправляет команду через TermuxBridge.
 */
object ShizukuBridge {

    const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"

    fun isInstalled(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
        true
    } catch (_: Exception) {
        false
    }

    suspend fun run(
        context: Context,
        command: String,
        timeoutSeconds: Int,
        termuxEnabled: Boolean,
        shouldStop: () -> Boolean = { false }
    ): String {
        if (command.isBlank()) return "Ошибка: команда не задана."
        if (!termuxEnabled) {
            return "Shizuku-мост работает через Termux и rish. Сначала включите Termux-мост " +
                    "в настройках агента."
        }
        if (!isInstalled(context)) {
            return "Shizuku не установлен. Установите Shizuku с shizuku.rikka.app и запустите " +
                    "его через ADB или root."
        }

        // rish может быть в PATH или лежать в домашней папке Termux.
        val wrapped = buildString {
            append("if command -v rish >/dev/null 2>&1; then RISH=rish; ")
            append("elif [ -x \"\$HOME/rish\" ]; then RISH=\"\$HOME/rish\"; ")
            append("else echo RISH_NOT_FOUND; exit 127; fi\n")
            append("RISH_APPLICATION_ID=com.termux \"\$RISH\" -c ")
            append(shellQuote(command))
        }
        val output = TermuxBridge.run(
            context,
            wrapped,
            timeoutSeconds,
            label = "Shizuku",
            shouldStop = shouldStop
        )
        if (output.contains("RISH_NOT_FOUND")) {
            return "Shizuku установлен, но rish не настроен в Termux. Скопируйте rish и " +
                    "rish_shizuku.dex в домашнюю папку Termux, выдайте rish права на запуск " +
                    "и повторите."
        }
        return output
    }

    /** Оборачивает произвольную строку в безопасные одинарные кавычки shell. */
    internal fun shellQuote(raw: String): String =
        "'" + raw.replace("'", "'\\''") + "'"
}
