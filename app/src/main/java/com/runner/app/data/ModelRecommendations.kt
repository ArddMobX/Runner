package com.runner.app.data

/**
 * Модели, которые стоит предлагать в мастере первого запуска.
 *
 * Список не является жёстким выбором: если модели нет в ответе провайдера,
 * берём первую модель с поддержкой инструментов. Идентификаторы меняются,
 * поэтому проверка идёт по фактическому списку [ModelInfo].
 */
object ModelRecommendations {

    fun preferred(providerId: String, models: List<ModelInfo>): String? {
        val preferred = when (providerId.lowercase()) {
            "openrouter" -> listOf(
                "qwen/qwen3.8-27b:free",
                "nvidia/nemotron-3-ultra-550b-a55b:free",
                "nvidia/nemotron-3.5-super-vl:free",
                "nvidia/nemotron-3.5-lightning:free",
                "google/gemma-4-26b-a4b-it:free",
                "openrouter/free",
                "deepseek/deepseek-v4.1-flash-1017",
                "deepseek/deepseek-v4.1-flash",
                "z-ai/glm-5.3-flash",
                "openai/gpt-6-luna"
            )
            "gemini" -> listOf(
                "gemini-4-hydrogen",
                "gemini-3.5-flash-lite",
                "gemini-3.8-flash",
                "gemini-4-neon",
                "gemini-4-carbon",
                "gemini-2.5-flash-lite",
                "gemini-2.5-flash"
            )
            "groq" -> listOf(
                "openai/gpt-oss-120b",
                "openai/gpt-oss-20b",
                "qwen/qwen3.8-27b",
                "llama-3.3-70b-versatile"
            )
            "deepseek" -> listOf(
                "deepseek-flash",
                "deepseek-v4-pro"
            )
            "openai" -> listOf(
                // Приоритет — актуальным моделям с поддержкой tool calling в /v1/chat/completions
                // (gpt-6.2-sol/astra и gpt-6.1-sol/terra требуют Responses API для инструментов).
                "gpt-5.6-luna",
                "gpt-5.6-terra",
                "gpt-5.6-sol",
                "gpt-6-luna",
                "gpt-6-sol",
                "gpt-5.4-mini",
                "gpt-5.5"
            )
            "together" -> listOf(
                "zai-org/GLM-5.3-Flash",
                "deepseek-ai/DeepSeek-V4.1-Flash-1017",
                "deepseek-ai/DeepSeek-V4.1-Flash",
                "Qwen/Qwen3.8-Flash",
                "Qwen/Qwen3.5-9B",
                "zai-org/GLM-5.3",
                "moonshotai/Kimi-K3.1-Pro",
                "openai/gpt-oss-120b"
            )
            else -> emptyList()
        }
        val toolModels = models.filter { ModelCatalog.isChatAndToolModel(it.id, it.name) }
        val match = preferred.firstNotNullOfOrNull { wanted ->
            toolModels.firstOrNull { it.id.removePrefix("models/").equals(wanted, ignoreCase = true) }
        }
        return match?.id
            ?: toolModels.firstOrNull()?.id
            ?: models.firstOrNull()?.id
    }
}
