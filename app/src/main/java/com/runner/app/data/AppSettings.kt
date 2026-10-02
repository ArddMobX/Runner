package com.runner.app.data

import android.content.Context

/**
 * Настройки агента и внешнего вида.
 * Секретные заголовки шифруются через Android Keystore (SecureStore).
 */
data class AppSettings(
    val systemPrompt: String = DEFAULT_SYSTEM_PROMPT,
    val temperature: Float = 0.2f,
    val maxSteps: Int = DEFAULT_MAX_STEPS,
    val reverseProxyUrl: String = "",
    val connectTimeoutSeconds: Int = DEFAULT_CONNECT_TIMEOUT,
    val responseTimeoutSeconds: Int = DEFAULT_RESPONSE_TIMEOUT,
    val customHeaders: String = "",
    val textScale: Float = 1f,
    val showToolDetails: Boolean = true,
    /** Показывать тайминги, токены и скорость генерации. */
    val showStats: Boolean = true,
    /** Планирование: модель сначала составляет план шагов, исполнение — после подтверждения. */
    val planningEnabled: Boolean = true
) {
    /** Для обратной совместимости старых вызовов. */
    val timeoutSeconds: Int get() = responseTimeoutSeconds

    companion object {
        const val DEFAULT_MAX_STEPS = 10
        const val DEFAULT_CONNECT_TIMEOUT = 15
        const val DEFAULT_RESPONSE_TIMEOUT = 60

        val TEMPERATURE_RANGE = 0f..1.5f
        val STEPS_RANGE = 1..20
        val TEXT_SCALES = listOf(0.9f, 1f, 1.15f)
        val CONNECT_TIMEOUT_PRESETS = listOf(10, 15, 30)
        val RESPONSE_TIMEOUT_PRESETS = listOf(60, 120, 180)

        @Deprecated("Используйте RESPONSE_TIMEOUT_PRESETS")
        val TIMEOUT_PRESETS = RESPONSE_TIMEOUT_PRESETS

        const val MANDATORY_PROMPT_SUFFIX = "Числа и единицы пиши обычным текстом: 51.6 GB, 89%. Не используй LaTeX, формулы и эмодзи. Не заканчивай ответ фразой 'При необходимости могу...'."

        val DEFAULT_SYSTEM_PROMPT = """
            Ты автономный мобильный агент Runner для Android. У тебя есть доступ к локальным системным инструментам:
            1. Аналитика: get_storage_summary, find_largest_files, find_junk_files, search_files.
            2. Файлы: list_dir, get_folder_summary, read_file, write_file, delete_file, create_dir, move_file, copy_file.
            3. Архивы: create_archive, extract_archive, organize_downloads.
            4. Система: clipboard_read, clipboard_write, run_shell_command.

            ПРАВИЛА:
            - Для анализа памяти и файлов используй агрегаторы (get_storage_summary, find_largest_files,
              find_junk_files). Не перечисляй сотни файлов поштучно.
            - Вывод инструментов ограничен (до 35 элементов / 3.5 КБ). Уточняй запрос при необходимости.
            - Всегда используй абсолютные пути из результатов list_dir. Корень: /storage/emulated/0. Перед записью проверь папку через list_dir.
            - Не конструируй относительные пути и не дописывай имя папки к пути из list_dir — это даёт Download/Download.
            - Деструктивные операции (удаление, перемещение, запись, сортировка, shell) требуют
              подтверждения пользователя. Если операцию отклонили — предложи альтернативу или остановись.
            - Оформляй ответы в Markdown.
            - Отвечай кратко и по делу, на русском языке.
            - $MANDATORY_PROMPT_SUFFIX
        """.trimIndent()
    }
}

class SettingsStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val legacyPrefs = context.applicationContext
        .getSharedPreferences("runner_prefs", Context.MODE_PRIVATE)

    fun load(): AppSettings {
        val migratedProxy = legacyPrefs.getString("api_reverse_proxy_url", null).orEmpty()

        // Миграция таймаутов
        val legacyTimeout = prefs.getInt(KEY_TIMEOUT, -1)
        val responseTimeout = if (prefs.contains(KEY_RESPONSE_TIMEOUT)) {
            prefs.getInt(KEY_RESPONSE_TIMEOUT, AppSettings.DEFAULT_RESPONSE_TIMEOUT)
        } else if (legacyTimeout > 0) {
            legacyTimeout
        } else {
            AppSettings.DEFAULT_RESPONSE_TIMEOUT
        }
        val connectTimeout = prefs.getInt(KEY_CONNECT_TIMEOUT, AppSettings.DEFAULT_CONNECT_TIMEOUT)

        // Миграция старого дефолта maxSteps (5 -> 10)
        val rawSteps = prefs.getInt(KEY_MAX_STEPS, AppSettings.DEFAULT_MAX_STEPS)
        val effectiveSteps = if (rawSteps == 5) AppSettings.DEFAULT_MAX_STEPS else rawSteps

        // Безопасная загрузка кастомных заголовков
        val encryptedHeaders = prefs.getString(KEY_CUSTOM_HEADERS_ENCRYPTED, null)
        val customHeaders = if (!encryptedHeaders.isNullOrBlank()) {
            SecureStore.decrypt(encryptedHeaders) ?: prefs.getString(KEY_CUSTOM_HEADERS, null).orEmpty()
        } else {
            // Миграция из plain text
            prefs.getString(KEY_CUSTOM_HEADERS, null).orEmpty()
        }

        return AppSettings(
            systemPrompt = prefs.getString(KEY_PROMPT, null) ?: AppSettings.DEFAULT_SYSTEM_PROMPT,
            temperature = prefs.getFloat(KEY_TEMPERATURE, 0.2f),
            maxSteps = effectiveSteps,
            reverseProxyUrl = prefs.getString(KEY_PROXY, null) ?: migratedProxy,
            connectTimeoutSeconds = connectTimeout,
            responseTimeoutSeconds = responseTimeout,
            customHeaders = customHeaders,
            textScale = prefs.getFloat(KEY_TEXT_SCALE, 1f),
            showToolDetails = prefs.getBoolean(KEY_SHOW_TOOL_DETAILS, true),
            showStats = prefs.getBoolean(KEY_SHOW_STATS, true),
            planningEnabled = prefs.getBoolean(KEY_PLANNING_ENABLED, true)
        )
    }

    fun save(settings: AppSettings) {
        val encryptedHeaders = SecureStore.encrypt(settings.customHeaders)
        val editor = prefs.edit()
            .putString(KEY_PROMPT, settings.systemPrompt)
            .putFloat(KEY_TEMPERATURE, settings.temperature)
            .putInt(KEY_MAX_STEPS, settings.maxSteps)
            .putString(KEY_PROXY, settings.reverseProxyUrl)
            .putInt(KEY_CONNECT_TIMEOUT, settings.connectTimeoutSeconds)
            .putInt(KEY_RESPONSE_TIMEOUT, settings.responseTimeoutSeconds)
            .remove(KEY_TIMEOUT)
            .putFloat(KEY_TEXT_SCALE, settings.textScale)
            .putBoolean(KEY_SHOW_TOOL_DETAILS, settings.showToolDetails)
            .putBoolean(KEY_SHOW_STATS, settings.showStats)
            .putBoolean(KEY_PLANNING_ENABLED, settings.planningEnabled)

        if (encryptedHeaders != null) {
            editor.putString(KEY_CUSTOM_HEADERS_ENCRYPTED, encryptedHeaders)
            editor.remove(KEY_CUSTOM_HEADERS) // Очищаем plain text
        } else {
            editor.putString(KEY_CUSTOM_HEADERS, settings.customHeaders)
        }

        editor.apply()
    }

    private companion object {
        const val PREFS_NAME = "runner_settings"
        const val KEY_PROMPT = "system_prompt"
        const val KEY_TEMPERATURE = "temperature"
        const val KEY_MAX_STEPS = "max_steps"
        const val KEY_PROXY = "reverse_proxy_url"
        const val KEY_TIMEOUT = "timeout_seconds" // legacy
        const val KEY_CONNECT_TIMEOUT = "connect_timeout_seconds"
        const val KEY_RESPONSE_TIMEOUT = "response_timeout_seconds"
        const val KEY_CUSTOM_HEADERS = "custom_headers" // legacy plain
        const val KEY_CUSTOM_HEADERS_ENCRYPTED = "custom_headers_encrypted"
        const val KEY_TEXT_SCALE = "text_scale"
        const val KEY_SHOW_TOOL_DETAILS = "show_tool_details"
        const val KEY_SHOW_STATS = "show_stats"
        const val KEY_PLANNING_ENABLED = "planning_enabled"
    }
}
