package com.runner.app.data

/**
 * Бесплатный доступ к модели. Считаем бесплатным только то, что можно
 * проверить по правилам провайдера, а не по слову «free» в названии.
 *
 * - OpenRouter прямо помечает бесплатные модели суффиксом `:free` (плюс роутер `openrouter/free`).
 * - У Gemini и Groq есть бесплатный тариф с лимитами, но список моделей
 *   зависит от провайдера, поэтому держим небольшую таблицу известных семейств.
 * - Всё остальное честно считаем платным.
 */
object ModelAccess {

    /**
     * Семейства Gemini, доступные в бесплатном тарифе с лимитами.
     * В поколении Gemini 4 бесплатна только `gemini-4-hydrogen` (`neon`, `carbon`, `argon` — платные).
     */
    private val freeGeminiModels = listOf(
        "gemini-4-hydrogen",
        "gemini-3",
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
    private val nonChatMarkers = listOf(
        "image", "tts", "audio", "live", "guard", "safeguard",
        "whisper", "transcribe", "embed", "moderation", "orpheus"
    )

    fun isFreeWithLimits(providerId: String, modelId: String): Boolean {
        val model = modelId.removePrefix("models/").lowercase().trim()
        if (model.isBlank()) return false

        return when (providerId.lowercase()) {
            "openrouter" -> (model == "openrouter/free" || model.endsWith(":free")) &&
                    ModelCatalog.isChatAndToolModel(model)

            "gemini" -> {
                val isChat = nonChatMarkers.none { model.contains(it) } &&
                        ModelCatalog.isChatAndToolModel(model)
                isChat && freeGeminiModels.any { known ->
                    model == known || model.startsWith("$known-") || model.startsWith("$known.")
                }
            }

            "groq" -> {
                val isChat = nonChatMarkers.none { model.contains(it) } &&
                        ModelCatalog.isChatAndToolModel(model)
                isChat && freeGroqPrefixes.any { model.startsWith(it) }
            }

            else -> false
        }
    }
}
