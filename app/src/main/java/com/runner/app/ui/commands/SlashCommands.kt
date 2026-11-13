package com.runner.app.ui.commands

/** Группа команд для палитры и шторки помощи. */
enum class SlashCommandGroup(val label: String) {
    TASK("Задача"),
    CHAT("Чат"),
    MODES("Режимы"),
    CUSTOM("Свои"),
    HELP("Справка")
}

/**
 * Команда поля ввода.
 *
 * Это действие приложения, а не промпт: команда выполняется до отправки
 * сообщения и не попадает в контекст модели. Поэтому локальные команды
 * не засоряют историю и не стоят токенов.
 */
data class SlashCommand(
    val name: String,
    val title: String,
    val description: String,
    val group: SlashCommandGroup,
    val argsHint: String? = null,
    val aliases: List<String> = emptyList(),
    /** Команда с обязательным аргументом не выполнится без него. */
    val requiresArgs: Boolean = false,
    /** Разрешена ли команда, пока агент работает. */
    val availableWhileRunning: Boolean = false,
    /** Своя команда из настроек, а не встроенная. */
    val isCustom: Boolean = false,
    /** Шаблон промпта для своей команды. */
    val promptTemplate: String? = null
) {
    val invocation: String get() = "/$name"

    /**
     * Нужно ли после выбора команды оставить поле открытым для аргументов.
     * Свои команды всегда дают дописать аргументы, даже если они не обязательны.
     */
    val takesArgs: Boolean
        get() = requiresArgs || !argsHint.isNullOrBlank() || isCustom
}

/** Разобранная команда: имя уже найдено в реестре, аргументы очищены. */
data class ParsedSlashCommand(
    val command: SlashCommand,
    val args: String
)

object SlashCommands {

    /**
     * Порядок здесь же порядок в палитре. Первыми идут команды, которые
     * чаще всего нужны с пустого поля ввода.
     */
    val all: List<SlashCommand> = listOf(
        SlashCommand(
            name = "help",
            title = "Помощь",
            description = "Показать все команды",
            group = SlashCommandGroup.HELP,
            aliases = listOf("?"),
            availableWhileRunning = true
        ),
        SlashCommand(
            name = "new",
            title = "Новый чат",
            description = "Начать разговор с чистого листа",
            group = SlashCommandGroup.CHAT
        ),
        SlashCommand(
            name = "plan",
            title = "План",
            description = "Составить план перед выполнением",
            group = SlashCommandGroup.TASK,
            argsHint = "задача",
            requiresArgs = true
        ),
        SlashCommand(
            name = "search",
            title = "Поиск",
            description = "Найти в интернете и сразу ответить",
            group = SlashCommandGroup.TASK,
            argsHint = "запрос",
            requiresArgs = true
        ),
        SlashCommand(
            name = "model",
            title = "Модель",
            description = "Выбрать провайдера и модель",
            group = SlashCommandGroup.MODES
        ),
        SlashCommand(
            name = "live",
            title = "Live",
            description = "Включить или выключить Live-режим",
            group = SlashCommandGroup.MODES
        ),
        SlashCommand(
            name = "stop",
            title = "Стоп",
            description = "Остановить текущую задачу",
            group = SlashCommandGroup.TASK,
            availableWhileRunning = true
        ),
        SlashCommand(
            name = "status",
            title = "Статус",
            description = "Провайдер, модель, контекст и режимы",
            group = SlashCommandGroup.HELP
        ),
        SlashCommand(
            name = "tools",
            title = "Инструменты",
            description = "Что доступно агенту сейчас",
            group = SlashCommandGroup.HELP
        ),
        SlashCommand(
            name = "undo",
            title = "Отменить",
            description = "Отменить последнюю обратимую операцию",
            group = SlashCommandGroup.TASK
        ),
        SlashCommand(
            name = "rename",
            title = "Переименовать",
            description = "Задать текущему чату новое имя",
            group = SlashCommandGroup.CHAT,
            argsHint = "новое имя",
            requiresArgs = true
        ),
        SlashCommand(
            name = "sessions",
            title = "Чаты",
            description = "Открыть список сохранённых чатов",
            group = SlashCommandGroup.CHAT
        ),
        SlashCommand(
            name = "compact",
            title = "Сжать контекст",
            description = "Заменить историю короткой сводкой",
            group = SlashCommandGroup.CHAT,
            argsHint = "что сохранить"
        ),
        SlashCommand(
            name = "export",
            title = "Экспорт",
            description = "Сохранить чат в Markdown",
            group = SlashCommandGroup.CHAT
        ),
        SlashCommand(
            name = "diff",
            title = "Изменения",
            description = "Показать изменения файлов за сессию",
            group = SlashCommandGroup.CHAT
        ),
        SlashCommand(
            name = "fork",
            title = "Ветка",
            description = "Создать копию чата и продолжить в ней",
            group = SlashCommandGroup.CHAT
        ),
        SlashCommand(
            name = "init",
            title = "Инструкции проекта",
            description = "Создать RUNNER.md в рабочей папке",
            group = SlashCommandGroup.CHAT
        ),
        SlashCommand(
            name = "readonly",
            title = "Только чтение",
            description = "Запретить изменения файлов и shell",
            group = SlashCommandGroup.MODES
        ),
        SlashCommand(
            name = "dryrun",
            title = "Сухой прогон",
            description = "Сначала план, без изменений",
            group = SlashCommandGroup.MODES
        ),
        SlashCommand(
            name = "think",
            title = "Думать",
            description = "Включить размышления модели",
            group = SlashCommandGroup.MODES
        )
    )

    /** Поиск по имени и алиасам. Регистр не важен, слэш можно не убирать. */
    fun find(nameOrAlias: String): SlashCommand? = findIn(all, nameOrAlias)

    fun findIn(commands: List<SlashCommand>, nameOrAlias: String): SlashCommand? {
        val clean = nameOrAlias.trim().removePrefix("/").lowercase()
        if (clean.isEmpty()) return null
        return commands.firstOrNull { command ->
            command.name == clean || command.aliases.any { it.lowercase() == clean }
        }
    }
}

/**
 * Разбор команд и подсказки для палитры.
 *
 * Правила намеренно строгие: команда должна начинаться со слэша и занимать
 * первое слово целиком. Путь вида /storage/emulated/0 не считается командой
 * и уходит модели как обычный текст.
 */
object SlashCommandParser {

    private val COMMAND_PATTERN = Regex("^/([^\\s/]+)(?:\\s+([\\s\\S]*))?$")

    fun parse(
        text: String,
        commands: List<SlashCommand> = SlashCommands.all
    ): ParsedSlashCommand? {
        val trimmed = text.trim()
        if (!trimmed.startsWith("/")) return null

        val match = COMMAND_PATTERN.matchEntire(trimmed) ?: return null
        val command = SlashCommands.findIn(commands, match.groupValues[1]) ?: return null
        return ParsedSlashCommand(command, match.groupValues[2].trim())
    }

    /**
     * Команды для палитры. Показываем только пока вводится первое слово:
     * после пробела начинаются аргументы, и палитра больше не нужна.
     */
    fun paletteMatches(
        text: String,
        commands: List<SlashCommand> = SlashCommands.all
    ): List<SlashCommand> {
        if (!text.startsWith("/")) return emptyList()
        if (text.any { it.isWhitespace() }) return emptyList()

        val query = text.removePrefix("/").lowercase()
        if (query.isEmpty()) return commands

        return commands.filter { command ->
            command.name.startsWith(query) ||
                    command.aliases.any { alias -> alias.lowercase().startsWith(query) }
        }
    }
}

/**
 * Запрос агенту, собранный из команды.
 *
 * В промпт уходит только текст после команды. Сама строка /plan или /search
 * в контекст модели не попадает: она управляет флагами хода, а не является
 * сообщением пользователя.
 */
data class SlashCommandAgentRequest(
    val prompt: String,
    val forceSearch: Boolean = false,
    val forcePlan: Boolean = false
)

object SlashCommandRequests {

    fun from(command: SlashCommand, args: String): SlashCommandAgentRequest? {
        command.promptTemplate?.let { template ->
            val prompt = CustomCommandValidation.expandPrompt(template, args)
            return prompt.takeIf { it.isNotBlank() }?.let { SlashCommandAgentRequest(it) }
        }

        val prompt = args.trim()
        if (prompt.isEmpty()) return null
        return when (command.name) {
            "plan" -> SlashCommandAgentRequest(prompt, forcePlan = true)
            "search" -> SlashCommandAgentRequest(prompt, forceSearch = true)
            else -> null
        }
    }
}
