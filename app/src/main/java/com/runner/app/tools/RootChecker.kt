package com.runner.app.tools

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Проверка root без побочных эффектов.
 *
 * su может быть в PATH, в /system/bin, /system/xbin или в Magisk. Проверяем
 * `su -c id` с таймаутом: root-менеджер может показать запрос, а может
 * молча отказать. Результат кэшируется для карты возможностей.
 */
object RootChecker {

    @Volatile
    var lastStatus: String = "не проверялся"
        private set

    fun isRootOutput(output: String): Boolean =
        Regex("""\buid=0\b""").containsMatchIn(output)

    suspend fun check(): String = withContext(Dispatchers.IO) {
        val su = findSu() ?: return@withContext setStatus("su не найден")
        val output = try {
            val process = ProcessBuilder(su, "-c", "id")
                .redirectErrorStream(true)
                .start()
            val finished = process.waitFor(5, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                return@withContext setStatus("su не ответил за 5 сек")
            }
            process.inputStream.bufferedReader().use { it.readText() }.trim()
        } catch (e: Exception) {
            return@withContext setStatus("ошибка su: ${e.localizedMessage}")
        }

        val status = when {
            output.isBlank() -> "su не вернул вывод"
            isRootOutput(output) -> "root работает (uid=0)"
            else -> "su ответил, но uid не 0: ${output.take(80)}"
        }
        setStatus(status)
    }

    private fun findSu(): String? {
        val fromPath = try {
            val process = ProcessBuilder("/system/bin/sh", "-c", "command -v su")
                .redirectErrorStream(true)
                .start()
            val finished = process.waitFor(3, TimeUnit.SECONDS)
            val text = if (finished) {
                process.inputStream.bufferedReader().use { it.readText() }.trim()
            } else {
                process.destroyForcibly()
                ""
            }
            text.lineSequence().firstOrNull { it.startsWith("/") }
        } catch (_: Exception) {
            null
        }
        if (!fromPath.isNullOrBlank() && File(fromPath).canExecute()) return fromPath

        return listOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/su/bin/su"
        ).firstOrNull { File(it).canExecute() }
    }

    private fun setStatus(value: String): String {
        lastStatus = value
        return value
    }
}
