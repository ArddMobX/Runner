package com.runner.app.tools

import android.content.Context
import com.runner.app.util.SecretRedactor
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Журнал shell-команд: что агент пытался выполнить и чем это закончилось.
 *
 * Появился после реального инцидента: агент с root выполнил `pm clear` и стёр
 * данные приложений, а разобраться по фактам было нечем — выполненная команда
 * нигде не сохранялась. Журнал пишет и отказы: попытка опасной команды — такой
 * же факт, как её выполнение, и именно отказы помогают понять, что модель
 * пыталась сделать.
 *
 * Файл лежит во внутреннем каталоге приложения и не покидает устройство:
 * команды могут содержать приватные данные. Хранятся последние [MAX_ENTRIES]
 * записей; каждая — одна строка формата «время · статус · команда».
 */
object ShellLog {

    private const val FILE_NAME = "shell_log.txt"
    private const val MAX_ENTRIES = 100
    private const val MAX_COMMAND_CHARS = 240

    // DateTimeFormatter потокобезопасен: append() зовут из фоновых потоков.
    private val timeFormat = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss", Locale.US)
    private val lock = Any()

    /**
     * Добавляет запись. Журнал диагностический: его сбой не должен ломать
     * выполнение команды, поэтому ошибки записи молча игнорируются.
     * Секреты (ключи, токены, пароли) маскируются до записи на диск.
     */
    fun append(context: Context, status: String, command: String) {
        val redactedCommand = SecretRedactor.redact(command)
            .replace('\n', ' ')
            .replace('\r', ' ')
            .trim()
            .take(MAX_COMMAND_CHARS)
        val line = buildString {
            append(LocalDateTime.now().format(timeFormat))
            append(" · ")
            append(SecretRedactor.redact(status))
            append(" · ")
            append(redactedCommand)
        }
        synchronized(lock) {
            try {
                val file = File(context.filesDir, FILE_NAME)
                val existing = if (file.exists()) file.readLines() else emptyList()
                val updated = (existing + line).takeLast(MAX_ENTRIES)
                file.writeText(updated.joinToString("\n") + "\n")
            } catch (_: Exception) {
            }
        }
    }

    /** Последние записи, новые сверху. */
    fun read(context: Context): List<String> = synchronized(lock) {
        try {
            val file = File(context.filesDir, FILE_NAME)
            if (!file.exists()) {
                emptyList()
            } else {
                file.readLines()
                    .filter { it.isNotBlank() }
                    .map { SecretRedactor.redact(it) }
                    .reversed()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun clear(context: Context) {
        synchronized(lock) {
            try {
                File(context.filesDir, FILE_NAME).delete()
            } catch (_: Exception) {
            }
        }
    }
}
