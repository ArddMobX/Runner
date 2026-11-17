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
        val preferred = when (providerId) {
            "openrouter" -> listOf(
                "qwen/qwen3.8-27b:free",
                "deepseek/deepseek-v4.1-flash",
                "nvidia/nemotron-3.5-lightning:free"
            )
            "gemini" -> listOf("gemini-2.5-flash", "gemini-2.5-flash-lite", "gemini-2.5-pro")
            "groq" -> listOf("openai/gpt-oss-120b", "openai/gpt-oss-20b", "qwen/qwen3.8-27b")
            "deepseek" -> listOf("deepseek-flash", "deepseek-v4-pro")
            else -> emptyList()
        }
        val toolModels = models.filter { ModelCatalog.isChatAndToolModel(it.id, it.name) }
        return preferred.firstOrNull { wanted -> models.any { it.id == wanted } }
            ?: toolModels.firstOrNull()?.id
            ?: models.firstOrNull()?.id
    }
}
