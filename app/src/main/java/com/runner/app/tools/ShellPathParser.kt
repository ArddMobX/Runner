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

    private const val MAX_NESTED_DEPTH = 4

    fun pathTokens(command: String): List<String> = pathTokens(command, depth = 0)

    private fun pathTokens(command: String, depth: Int): List<String> {
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
                token.startsWith("-") && '/' in token && !token.any { it.isWhitespace() } ->
                    token.substring(token.indexOf('/'))
                else -> null
            }
            if (!candidate.isNullOrBlank()) {
                result += candidate
            } else if (
                depth < MAX_NESTED_DEPTH &&
                token != command &&
                '/' in token &&
                token.any { it.isWhitespace() || it in ";|&<>(){}`" }
            ) {
                // Вложенная команда в кавычках (например, `sh -c "rm -rf /etc/hosts"`):
                // одиночные пути с пробелами выше уже обработаны через `startsWith("/")`,
                // а составные команды разбираем рекурсивно.
                result += pathTokens(token, depth + 1)
            }
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
                ch == '(' || ch == ')' || ch == '{' || ch == '}' || ch == '`' -> flush()
                else -> current.append(ch)
            }
        }
        flush()
        return result.distinct()
    }
}
