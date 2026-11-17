package com.runner.app.data

/**
 * Бесплатный доступ к модели. Считаем бесплатным только то, что можно
 * проверить по правилам провайдера, а не по слову «free» в названии.
 *
 * - OpenRouter прямо помечает бесплатные модели суффиксом `:free`.
 * - У Gemini и Groq есть бесплатный тариф с лимитами, но список моделей
 *   зависит от провайдера, поэтому держим небольшую таблицу известных семейств.
 * - Всё остальное честно считаем платным.
 */
object ModelAccess {

    /** Семейства Gemini, доступные в бесплатном тарифе с лимитами. */
    private val freeGeminiModels = listOf(
        "gemini-2.5-pro",
        "gemini-2.5-flash",
        "gemini-2.5-flash-lite",
        "gemini-2.0-flash",
        "gemini-2.0-flash-lite",
        "gemini-1.5-pro",
        "gemini-1.5-flash",
        "gemini-1.5-flash-8b"
    )

    /** Семейства Groq, которые отдаются на бесплатном тарифе с rate limit. */
    private val freeGroqPrefixes = listOf(
        "llama",
        "meta-llama/",
        "openai/gpt-oss",
        "qwen/",
        "deepseek",
        "gemma",
        "mistral",
        "moonshotai/",
        "kimi"
    )

    /** Не-чат варианты, которые не должны попадать в бесплатную группу. */
    private val nonChatMarkers = listOf("image", "tts", "audio", "live")

    fun isFreeWithLimits(providerId: String, modelId: String): Boolean {
        val model = modelId.removePrefix("models/").lowercase()
        if (model.isBlank()) return false

        return when (providerId.lowercase()) {
            "openrouter" -> model.contains(":free")

            "gemini" -> {
                val isChat = nonChatMarkers.none { model.contains(it) }
                isChat && freeGeminiModels.any { known ->
                    model == known || model.startsWith("$known-")
                }
            }

            "groq" -> freeGroqPrefixes.any { model.startsWith(it) }

            else -> false
        }
    }
}
