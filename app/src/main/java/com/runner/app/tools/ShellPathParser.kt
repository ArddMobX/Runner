package com.runner.app.tools

/**
 * Достаёт абсолютные пути из shell-команды с учётом кавычек и экранирования.
 *
 * Раньше рабочая папка проверялась регуляркой, которая обрывалась на пробеле:
 * `"/storage/emulated/0/AI Motion/make_video.sh"` превращался в
 * `/storage/emulated/0/AI`, автономный режим не срабатывал, и агент просил
 * подтверждение на команду внутри собственной папки.
 */
object ShellPathParser {

    fun pathTokens(command: String): List<String> {
        if (command.isBlank()) return emptyList()

        val result = mutableListOf<String>()
        val current = StringBuilder()
        var quote: Char? = null
        var escaped = false

        fun flush() {
            if (current.isEmpty()) return
            val token = current.toString()
            current.setLength(0)
            val candidate = when {
                token.startsWith("/") -> token
                '=' in token -> token.substringAfter('=').takeIf { it.startsWith("/") }
                else -> null
            }
            if (!candidate.isNullOrBlank()) result += candidate
        }

        command.forEach { ch ->
            if (escaped) {
                current.append(ch)
                escaped = false
                return@forEach
            }
            if (ch == '\\' && quote != '\'') {
                escaped = true
                return@forEach
            }
            if (quote != null) {
                if (ch == quote) {
                    quote = null
                } else {
                    current.append(ch)
                }
                return@forEach
            }
            when {
                ch == '\'' || ch == '"' -> quote = ch
                ch.isWhitespace() -> flush()
                ch == ';' || ch == '|' || ch == '&' -> flush()
                ch == '>' || ch == '<' -> flush()
                else -> current.append(ch)
            }
        }
        flush()
        return result.distinct()
    }
}
