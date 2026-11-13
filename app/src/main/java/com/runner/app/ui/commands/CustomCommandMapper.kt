package com.runner.app.ui.commands

import com.runner.app.data.db.CustomCommandEntity

/**
 * Превращает сохранённую команду в элемент палитры.
 *
 * Своя команда всегда принимает аргументы: тап по ней оставляет поле ввода
 * открытым, а Enter с пустым хвостом всё равно выполнит шаблон.
 */
fun CustomCommandEntity.toSlashCommand(): SlashCommand = SlashCommand(
    name = name,
    title = description.ifBlank { name },
    description = description.ifBlank { "Своя команда" },
    group = SlashCommandGroup.CUSTOM,
    argsHint = argsHint.takeIf { it.isNotBlank() },
    isCustom = true,
    promptTemplate = prompt
)
