package com.runner.app.util

/**
 * Единое вырезание секретов из текста, который уходит за пределы приложения:
 * отчётов о сбое, логов и экспортированных чатов.
 *
 * Это не замена шифрованию ключей. Это последний фильтр перед буфером обмена,
 * файлом или сообщением разработчику.
 */
object SecretRedactor {

    private val headerPattern = Regex(
        """\b(authorization|x-api-key|api[-_ ]?key|x-goog-api-key)\b\s*[:=]\s*(?:Bearer\s+)?["']?[^\s"',}]+""",
        RegexOption.IGNORE_CASE
    )
    private val bearerPattern = Regex("""Bearer\s+[A-Za-z0-9._\-]+""", RegexOption.IGNORE_CASE)
    private val openAiPattern = Regex("""\bsk-[A-Za-z0-9_\-]{12,}""")
    private val groqPattern = Regex("""\bgsk_[A-Za-z0-9_\-]{12,}""")
    private val geminiPattern = Regex("""\bAIza[0-9A-Za-z_\-]{20,}""")
    private val secretValuePattern = Regex(
        """\b(password|secret|token)\b\s*[:=]\s*["']?[^\s"',}]+""",
        RegexOption.IGNORE_CASE
    )

    fun redact(input: String): String {
        if (input.isBlank()) return ""
        var result = input
        result = headerPattern.replace(result) { match ->
            "${match.groupValues[1]}: ***"
        }
        result = bearerPattern.replace(result, "Bearer ...***")
        result = openAiPattern.replace(result, "sk-...***")
        result = groqPattern.replace(result, "gsk_...***")
        result = geminiPattern.replace(result, "AIza...***")
        result = secretValuePattern.replace(result) { match ->
            "${match.groupValues[1]}: ***"
        }
        return result
    }
}
