package com.runner.app.util

/**
 * Единое вырезание секретов из текста, который уходит за пределы приложения:
 * отчётов о сбое, логов и экспортированных чатов.
 *
 * Это не замена шифрованию ключей. Это последний фильтр перед буфером обмена,
 * файлом или сообщением разработчику.
 */
object SecretRedactor {

    // JSON-поля вида "api_key": "secret", "password": "...", "authorization": "Bearer ..."
    private val jsonSecretPattern = Regex(
        """("(?:authorization|x-api-key|x-goog-api-key|api[_-]?key|apiKey|password|passwd|secret|access[_-]?token|refresh[_-]?token|token)"\s*:\s*")([^"\\]*(?:\\.[^"\\]*)*)(")""",
        RegexOption.IGNORE_CASE
    )

    // Заголовки и пары ключ=значение (включая Bearer/Basic с Base64-символами + / =)
    private val headerPattern = Regex(
        """\b(authorization|x-api-key|api[-_ ]?key|x-goog-api-key)\b\s*[:=]\s*(?:(?:Bearer|Basic)\s+)?["']?[^\s"',}\]]+["']?""",
        RegexOption.IGNORE_CASE
    )
    private val bearerOrBasicPattern = Regex(
        """\b(Bearer|Basic)\s+[A-Za-z0-9._\-+/=]{6,}""",
        RegexOption.IGNORE_CASE
    )
    private val openAiPattern = Regex("""\bsk-[A-Za-z0-9_\-]{12,}""")
    private val groqPattern = Regex("""\bgsk_[A-Za-z0-9_\-]{12,}""")
    private val geminiPattern = Regex("""\bAIza[0-9A-Za-z_\-]{20,}""")
    private val tavilyPattern = Regex("""\btvly-[A-Za-z0-9_\-]{12,}""")
    private val githubPattern = Regex("""\b(?:gh[pous]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,})""")
    private val jwtPattern = Regex("""\beyJ[A-Za-z0-9_\-]{10,}\.eyJ[A-Za-z0-9_\-]{10,}\.[A-Za-z0-9_\-]{10,}""")
    private val secretValuePattern = Regex(
        """\b(password|passwd|secret|access[_-]?token|refresh[_-]?token|token)\b\s*[:=]\s*["']?[^\s"',}\]]+["']?""",
        RegexOption.IGNORE_CASE
    )

    fun redact(input: String): String {
        if (input.isBlank()) return ""
        var result = input
        result = jsonSecretPattern.replace(result) { match ->
            "${match.groupValues[1]}***${match.groupValues[3]}"
        }
        result = headerPattern.replace(result) { match ->
            "${match.groupValues[1]}: ***"
        }
        result = bearerOrBasicPattern.replace(result) { match ->
            "${match.groupValues[1]} ...***"
        }
        result = openAiPattern.replace(result, "sk-...***")
        result = groqPattern.replace(result, "gsk_...***")
        result = geminiPattern.replace(result, "AIza...***")
        result = tavilyPattern.replace(result, "tvly-...***")
        result = githubPattern.replace(result, "gh...***")
        result = jwtPattern.replace(result, "eyJ...***")
        result = secretValuePattern.replace(result) { match ->
            "${match.groupValues[1]}: ***"
        }
        return result
    }
}
