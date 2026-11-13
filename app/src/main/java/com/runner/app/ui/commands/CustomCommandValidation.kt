package com.runner.app.ui.commands

/**
 * Проверка и разворачивание своих команд.
 *
 * Имя живёт отдельно от промпта: по нему команда находится в палитре, а
 * шаблон может быть каким угодно длинным.
 */
object CustomCommandValidation {

    const val MAX_NAME_LENGTH = 24
    const val MAX_DESCRIPTION_LENGTH = 160
    const val MAX_ARGS_HINT_LENGTH = 60
    const val MAX_PROMPT_LENGTH = 8_000

    private val NAME_PATTERN = Regex("^[a-z][a-z0-9_-]{0,23}$")

    fun normalizeName(raw: String): String =
        raw.trim().removePrefix("/").lowercase()

    fun validateName(raw: String): String? {
        val name = normalizeName(raw)
        return when {
            name.isEmpty() -> "Введи имя команды"
            !NAME_PATTERN.matches(name) ->
                "Имя: латиница, цифры, _ и -, до $MAX_NAME_LENGTH символов, начинается с буквы."
            SlashCommands.find(name) != null -> "Имя /$name уже занято встроенной командой"
            else -> null
        }
    }

    fun validatePrompt(raw: String): String? = when {
        raw.isBlank() -> "Промпт не может быть пустым"
        raw.length > MAX_PROMPT_LENGTH -> "Промпт длиннее $MAX_PROMPT_LENGTH символов"
        else -> null
    }

    /**
     * Подставляет текст после команды.
     *
     * Если в шаблоне есть {{args}}, он заменяется. Если плейсхолдера нет,
     * аргументы добавляются в конец отдельным блоком, чтобы не потерялись.
     */
    fun expandPrompt(template: String, args: String): String {
        val cleanTemplate = template.trim()
        val cleanArgs = args.trim()
        return when {
            cleanTemplate.contains("{{args}}") ->
                cleanTemplate.replace("{{args}}", cleanArgs).trim()
            cleanArgs.isEmpty() -> cleanTemplate
            else -> cleanTemplate + "\n\n" + cleanArgs
        }
    }
}
