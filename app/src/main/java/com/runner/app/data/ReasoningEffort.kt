package com.runner.app.data

import kotlin.math.abs

/**
 * Уровень глубины размышлений (Reasoning Effort) для моделей и провайдеров,
 * поддерживающих управление бюджетом рассуждений (OpenRouter, OpenAI, Gemini и др.).
 */
enum class ReasoningEffort(
    val apiValue: String,
    val shortLabel: String,
    val title: String,
    val subtitle: String,
    val bars: Int
) {
    NONE("none", "", "Выкл", "Без размышлений, быстрый ответ", 0),
    MINIMAL("minimal", "Min", "Minimal", "Минимальная проверка ответа", 1),
    LOW("low", "Low", "Low", "Быстро и экономно по токенам", 1),
    MEDIUM("medium", "Med", "Medium", "Баланс скорости и глубины", 2),
    HIGH("high", "High", "High", "Глубокий анализ и сложный код", 3),
    XHIGH("xhigh", "XHigh", "Extra High", "Максимум глубины для сложных задач", 3),
    MAX("max", "Max", "Max", "Предельный бюджет размышлений", 3);

    /** true для любого активного уровня (всё, кроме NONE). */
    val isActive: Boolean get() = this != NONE

    /** true для максимальных уровней, где допустим мягкий цветовой акцент. */
    val isAccent: Boolean get() = this == XHIGH || this == MAX

    /** true для глубокого анализа (high / xhigh / max). */
    val isDeep: Boolean get() = this == HIGH || this == XHIGH || this == MAX

    companion object {
        fun fromApiValue(raw: String?): ReasoningEffort? {
            val key = raw?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
            return entries.firstOrNull { it.apiValue == key }
        }
    }
}

/**
 * Итоговое состояние Reasoning Effort для конкретной пары (провайдер, модель).
 */
data class ResolvedReasoningEffort(
    val supportedEfforts: List<ReasoningEffort>,
    val activeEffort: ReasoningEffort?,
    val defaultEffort: ReasoningEffort?,
    val isMandatory: Boolean
) {
    /** Модель поддерживает выбор уровня размышлений. */
    val isSupported: Boolean get() = supportedEfforts.isNotEmpty()

    /**
     * Показывать ли компактную плашку Effort рядом с именем модели.
     * Если уровень NONE или модель не поддерживает Effort — плашки нет.
     */
    val showPill: Boolean get() = activeEffort != null && activeEffort != ReasoningEffort.NONE

    /** Можно ли полностью отключить размышления (перевести в NONE). */
    val canDisable: Boolean get() = ReasoningEffort.NONE in supportedEfforts

    companion object {
        val Unsupported = ResolvedReasoningEffort(
            supportedEfforts = emptyList(),
            activeEffort = null,
            defaultEffort = null,
            isMandatory = false
        )
    }
}

/**
 * Единый источник истины: определяет, какие уровни Effort поддерживает активная
 * модель, и какой уровень реально действует с учётом выбора пользователя.
 */
object ReasoningEffortPolicy {

    private val OPENAI_O_MODEL_PREFIX = Regex("""^o[134](?:$|[-._])""")

    fun resolve(
        provider: Provider?,
        savedEffort: String = "",
        modelIdOverride: String? = null
    ): ResolvedReasoningEffort {
        if (provider == null) return ResolvedReasoningEffort.Unsupported
        val modelId = (modelIdOverride ?: provider.activeModel).removePrefix("models/").trim()
        if (modelId.isBlank()) return ResolvedReasoningEffort.Unsupported
        val modelInfo = provider.models.firstOrNull {
            it.id.removePrefix("models/").equals(modelId, ignoreCase = true)
        }
        return resolveForModel(
            modelId = modelId,
            modelInfo = modelInfo,
            baseUrl = provider.baseUrl,
            providerKind = provider.kind,
            savedEffort = savedEffort
        )
    }

    fun resolveForModel(
        modelId: String,
        modelInfo: ModelInfo?,
        baseUrl: String = "",
        providerKind: ProviderKind = ProviderKind.OPENAI_COMPATIBLE,
        savedEffort: String = ""
    ): ResolvedReasoningEffort {
        val cleanId = modelId.removePrefix("models/").trim()
        if (cleanId.isBlank()) return ResolvedReasoningEffort.Unsupported

        val (supported, defaultEffort) = resolveSupportedAndDefault(
            modelId = cleanId,
            modelInfo = modelInfo,
            baseUrl = baseUrl,
            providerKind = providerKind
        )
        if (supported.isEmpty()) return ResolvedReasoningEffort.Unsupported

        val isMandatory = ReasoningEffort.NONE !in supported
        val requested = ReasoningEffort.fromApiValue(savedEffort)
        val active = if (requested != null) {
            clampToSupported(requested, supported, defaultEffort)
        } else {
            defaultEffort?.takeIf { it in supported }
                ?: if (isMandatory) {
                    supported.firstOrNull { it == ReasoningEffort.MEDIUM } ?: supported.first()
                } else {
                    ReasoningEffort.NONE
                }
        }

        return ResolvedReasoningEffort(
            supportedEfforts = supported,
            activeEffort = active,
            defaultEffort = defaultEffort,
            isMandatory = isMandatory
        )
    }

    /**
     * Подбирает ближайший допустимый уровень, если пользователь переключился на модель
     * с другим набором уровней (например, с XHigh на модель, где максимум High).
     * Ненулевой выбор никогда не сбрасывается молча в NONE.
     */
    fun clampToSupported(
        requested: ReasoningEffort,
        supported: List<ReasoningEffort>,
        defaultEffort: ReasoningEffort? = null
    ): ReasoningEffort? {
        if (supported.isEmpty()) return null
        if (requested in supported) return requested
        if (requested == ReasoningEffort.NONE) {
            return defaultEffort?.takeIf { it in supported }
                ?: supported.firstOrNull { it == ReasoningEffort.MEDIUM }
                ?: supported.first()
        }
        val activeOnly = supported.filter { it != ReasoningEffort.NONE }
        if (activeOnly.isEmpty()) return supported.first()
        return activeOnly.minWith(
            compareBy<ReasoningEffort> { abs(it.ordinal - requested.ordinal) }
                .thenBy { if (it == defaultEffort) 0 else 1 }
                .thenByDescending { it.ordinal }
        )
    }

    /**
     * Какой уровень включить при нажатии «Думать», когда Effort сейчас выключен (NONE).
     */
    fun preferredEnableEffort(
        resolved: ResolvedReasoningEffort,
        lastNonNone: ReasoningEffort? = null
    ): ReasoningEffort? {
        val activeOnly = resolved.supportedEfforts.filter { it != ReasoningEffort.NONE }
        if (activeOnly.isEmpty()) return null
        if (lastNonNone != null && lastNonNone != ReasoningEffort.NONE) {
            clampToSupported(lastNonNone, activeOnly, resolved.defaultEffort)?.let { return it }
        }
        resolved.defaultEffort?.takeIf { it != ReasoningEffort.NONE && it in activeOnly }?.let {
            return it
        }
        return activeOnly.firstOrNull { it == ReasoningEffort.HIGH }
            ?: activeOnly.firstOrNull { it == ReasoningEffort.MEDIUM }
            ?: activeOnly.last()
    }

    private fun resolveSupportedAndDefault(
        modelId: String,
        modelInfo: ModelInfo?,
        baseUrl: String,
        providerKind: ProviderKind
    ): Pair<List<ReasoningEffort>, ReasoningEffort?> {
        // 1. Приоритет — явные метаданные, которые вернул провайдер (/models).
        if (modelInfo != null) {
            if (modelInfo.supportedReasoningEfforts.isNotEmpty()) {
                val ordered = modelInfo.supportedReasoningEfforts.distinct().sortedBy { it.ordinal }
                return ordered to modelInfo.defaultReasoningEffort?.takeIf { it in ordered }
            }
            if (modelInfo.reasoningMetadataKnown) {
                // Провайдер явно сообщил, что модель не поддерживает reasoning/effort.
                return emptyList<ReasoningEffort>() to null
            }
        }

        // 2. Fallback для прямых провайдеров, чей /models не отдаёт supported_parameters,
        //    или для кэша до первого обновления списка моделей.
        val host = extractHost(baseUrl)
        val shortName = modelId.substringAfterLast('/').lowercase()

        // Прямой api.deepseek.com поддерживает только бинарный thinking (enabled/disabled),
        // не уровни effort — не рисуем ему фиктивные Low/Med/High.
        if (providerKind == ProviderKind.DEEPSEEK || host == "api.deepseek.com") {
            return emptyList<ReasoningEffort>() to null
        }

        val isOpenRouter = host == "openrouter.ai" || host.endsWith(".openrouter.ai")
        if (isOpenRouter) {
            return if (OpenAIClient.supportsOpenRouterReasoning(modelId)) {
                listOf(
                    ReasoningEffort.NONE,
                    ReasoningEffort.LOW,
                    ReasoningEffort.MEDIUM,
                    ReasoningEffort.HIGH
                ) to ReasoningEffort.NONE
            } else {
                emptyList<ReasoningEffort>() to null
            }
        }

        val isGeminiDirect = providerKind == ProviderKind.GEMINI ||
            host == "generativelanguage.googleapis.com"
        if (isGeminiDirect) {
            return when {
                shortName.startsWith("gemini-2.5-flash") ||
                    (shortName.startsWith("gemini-3") && shortName.contains("flash")) ->
                    listOf(
                        ReasoningEffort.NONE,
                        ReasoningEffort.LOW,
                        ReasoningEffort.MEDIUM,
                        ReasoningEffort.HIGH
                    ) to ReasoningEffort.NONE
                shortName.startsWith("gemini-2.5-pro") ||
                    shortName.startsWith("gemini-3") ||
                    shortName.startsWith("gemini-4") ->
                    listOf(
                        ReasoningEffort.LOW,
                        ReasoningEffort.MEDIUM,
                        ReasoningEffort.HIGH
                    ) to ReasoningEffort.MEDIUM
                else -> emptyList<ReasoningEffort>() to null
            }
        }

        // OpenAI o1 / o3 / o4 / gpt-5 / gpt-6 (напрямую или через OpenAI-совместимые шлюзы).
        return when {
            OPENAI_O_MODEL_PREFIX.containsMatchIn(shortName) ->
                listOf(
                    ReasoningEffort.LOW,
                    ReasoningEffort.MEDIUM,
                    ReasoningEffort.HIGH
                ) to ReasoningEffort.MEDIUM
            shortName.startsWith("gpt-5") || shortName.startsWith("gpt-6") ->
                listOf(
                    ReasoningEffort.NONE,
                    ReasoningEffort.LOW,
                    ReasoningEffort.MEDIUM,
                    ReasoningEffort.HIGH
                ) to ReasoningEffort.NONE
            else -> emptyList<ReasoningEffort>() to null
        }
    }
}
