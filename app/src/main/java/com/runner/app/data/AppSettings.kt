package com.runner.app.data

import android.content.Context

/**
 * Настройки агента и внешнего вида. Секретов здесь нет — обычные SharedPreferences.
 */
data class AppSettings(
    val systemPrompt: String = DEFAULT_SYSTEM_PROMPT,
    val temperature: Float = 0.2f,
    val maxSteps: Int = 5,
    val reverseProxyUrl: String = "",
    val timeoutSeconds: Int = 60,
    val customHeaders: String = "",
    val textScale: Float = 1f,
    val showToolDetails: Boolean = true,
    /** Показывать тайминги, токены и скорость генерации. */
    val showStats: Boolean = true
) {
    companion object {
        val TEMPERATURE_RANGE = 0f..1.5f
        val STEPS_RANGE = 1..12
        val TEXT_SCALES = listOf(0.9f, 1f, 1.15f)
        val TIMEOUT_PRESETS = listOf(15, 30, 60, 120)

        val DEFAULT_SYSTEM_PROMPT = """
            Ты автономный мобильный агент Runner для Android. У тебя есть доступ к локальным системным инструментам:
            1. Аналитика: get_storage_summary, find_largest_files, find_junk_files, search_files.
            2. Файлы: get_folder_summary, read_file, write_file, delete_file, create_dir, move_file, copy_file.
            3. Архивы: create_archive, extract_archive, organize_downloads.
            4. Система: clipboard_read, clipboard_write, run_shell_command.

            ПРАВИЛА:
            - Для анализа памяти и файлов используй агрегаторы (get_storage_summary, find_largest_files,
              find_junk_files). Не перечисляй сотни файлов поштучно.
            - Вывод инструментов ограничен (до 35 элементов / 3.5 КБ). Уточняй запрос при необходимости.
            - Деструктивные операции (удаление, перемещение, запись, сортировка, shell) требуют
              подтверждения пользователя. Если операцию отклонили — предложи альтернативу или остановись.
            - Оформляй ответы в Markdown. Формулы — в LaTeX: блочные в '$$ ... $$', строчные в '$ ... $'.
            - Отвечай кратко и по делу, на русском языке.
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
        return AppSettings(
            systemPrompt = prefs.getString(KEY_PROMPT, null) ?: AppSettings.DEFAULT_SYSTEM_PROMPT,
            temperature = prefs.getFloat(KEY_TEMPERATURE, 0.2f),
            maxSteps = prefs.getInt(KEY_MAX_STEPS, 5),
            reverseProxyUrl = prefs.getString(KEY_PROXY, null) ?: migratedProxy,
            timeoutSeconds = prefs.getInt(KEY_TIMEOUT, 60),
            customHeaders = prefs.getString(KEY_CUSTOM_HEADERS, null).orEmpty(),
            textScale = prefs.getFloat(KEY_TEXT_SCALE, 1f),
            showToolDetails = prefs.getBoolean(KEY_SHOW_TOOL_DETAILS, true),
            showStats = prefs.getBoolean(KEY_SHOW_STATS, true)
        )
    }

    fun save(settings: AppSettings) {
        prefs.edit()
            .putString(KEY_PROMPT, settings.systemPrompt)
            .putFloat(KEY_TEMPERATURE, settings.temperature)
            .putInt(KEY_MAX_STEPS, settings.maxSteps)
            .putString(KEY_PROXY, settings.reverseProxyUrl)
            .putInt(KEY_TIMEOUT, settings.timeoutSeconds)
            .putString(KEY_CUSTOM_HEADERS, settings.customHeaders)
            .putFloat(KEY_TEXT_SCALE, settings.textScale)
            .putBoolean(KEY_SHOW_TOOL_DETAILS, settings.showToolDetails)
            .putBoolean(KEY_SHOW_STATS, settings.showStats)
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "runner_settings"
        const val KEY_PROMPT = "system_prompt"
        const val KEY_TEMPERATURE = "temperature"
        const val KEY_MAX_STEPS = "max_steps"
        const val KEY_PROXY = "reverse_proxy_url"
        const val KEY_TIMEOUT = "timeout_seconds"
        const val KEY_CUSTOM_HEADERS = "custom_headers"
        const val KEY_TEXT_SCALE = "text_scale"
        const val KEY_SHOW_TOOL_DETAILS = "show_tool_details"
        const val KEY_SHOW_STATS = "show_stats"
    }
}
