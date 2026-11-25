package com.runner.app.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/**
 * Модель провайдера. Название и длина контекста есть не у всех:
 * OpenRouter отдаёт и то и другое, Groq и Gemini — только id.
 */
data class ModelInfo(
    val id: String,
    val name: String = "",
    val contextLength: Int? = null,
    /** Уровни reasoning effort, заявленные провайдером (в каноническом порядке). */
    val supportedReasoningEfforts: List<ReasoningEffort> = emptyList(),
    /** Уровень по умолчанию от провайдера (если отдал, например OpenRouter reasoning.default_effort). */
    val defaultReasoningEffort: ReasoningEffort? = null,
    /** Провайдер явно вернул метаданные параметров/reasoning (отличает «не умеет» от «не обновлялось»). */
    val reasoningMetadataKnown: Boolean = false
) {
    /** Что показывать в списке: человекочитаемое имя, если оно есть, без технического префикса models/. */
    val label: String get() = (name.ifBlank { id }).removePrefix("models/")

    /** Компактный бейдж контекста: «1M», «262K». */
    val contextBadge: String?
        get() = contextLength?.let { tokens ->
            when {
                tokens >= 1_000_000 -> "${tokens / 1_000_000}M"
                tokens >= 1_000 -> "${tokens / 1_000}K"
                else -> null
            }
        }
}

/**
 * Список моделей берётся у самого провайдера: GET {base}/models.
 * Руками ID вводить не нужно — они всё равно протухают.
 */
object ModelCatalog {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val tokenBoundaryNonChat = listOf(
        "aqa",
        "guard",
        "image",
        "audio",
        "live",
        "video",
        "speech",
        "rerank",
        "decision",
        "genie",
        "deep-research",
        "computer-use",
        "antigravity",
        "search-preview"
    )

    private val blockedKeywords = listOf(
        "embed",
        "moderation",
        "safeguard",
        "content-safety",
        "tts",
        "orpheus",
        "chirp",
        "lyria",
        "veo",
        "whisper",
        "transcribe",
        "transcription",
        "dall-e",
        "imagen",
        "nano-banana",
        "gemini-omni",
        "robotics",
        "realtime",
        "babbage",
        "davinci"
    )

    private fun hasTokenMatch(text: String, token: String): Boolean {
        if (text.isEmpty()) return false
        var start = text.indexOf(token)
        while (start >= 0) {
            val beforeOk = start == 0 || !text[start - 1].isLetterOrDigit()
            val end = start + token.length
            val afterOk = end == text.length || !text[end].isLetterOrDigit()
            if (beforeOk && afterOk) return true
            start = text.indexOf(token, start + 1)
        }
        return false
    }

    /**
     * Фильтрует не-чат модели:
     * - Оставляет только модели с поддержкой generateContent (если провайдер отдаёт supportedGenerationMethods).
     * - Скрывает aqa, guard, image, audio, live, deep-research, computer-use, antigravity,
     *   embedding, moderation, tts, whisper и прочие не-чат модели.
     */
    fun isChatAndToolModel(
        id: String,
        name: String = "",
        supportedMethods: List<String>? = null
    ): Boolean {
        // 1. Проверка методов генерации (Gemini API)
        if (!supportedMethods.isNullOrEmpty() && !supportedMethods.contains("generateContent")) {
            return false
        }

        val cleanId = id.removePrefix("models/").lowercase()
        val cleanName = name.removePrefix("models/").lowercase()

        // 1b. Batch-варианты OpenRouter (суффикс :batch) работают только с /v1/batches, не с чатом.
        if (cleanId.endsWith(":batch")) {
            return false
        }

        // 2. Токены, которые проверяем по границам слова/разделителя (чтобы не цеплять случайные подстроки)
        for (token in tokenBoundaryNonChat) {
            if (hasTokenMatch(cleanId, token) || hasTokenMatch(cleanName, token)) {
                return false
            }
        }

        // 3. Скрываем embedding и прочие не-чат сервисы
        for (kw in blockedKeywords) {
            if (cleanId.contains(kw) || cleanName.contains(kw)) {
                return false
            }
        }

        return true
    }

    internal data class ModelReasoningSpec(
        val supportedEfforts: List<ReasoningEffort>,
        val defaultEffort: ReasoningEffort?,
        val metadataKnown: Boolean
    )

    internal fun parseReasoningSpec(
        supportedParameters: Set<String>?,
        hasReasoningField: Boolean,
        hasReasoningObject: Boolean,
        reasoningMandatory: Boolean = false,
        reasoningDefaultEnabled: Boolean? = null,
        rawSupportedEfforts: List<String>? = null,
        rawDefaultEffort: String? = null
    ): ModelReasoningSpec {
        val metadataKnown = supportedParameters != null || hasReasoningField
        val normalizedParams = supportedParameters
            ?.map { it.trim().lowercase() }
            ?.toSet()
            .orEmpty()
        val supportsParam = "reasoning_effort" in normalizedParams || "reasoning" in normalizedParams

        if (hasReasoningObject) {
            val parsedEfforts = rawSupportedEfforts
                .orEmpty()
                .mapNotNull { ReasoningEffort.fromApiValue(it) }
                .distinct()
                .sortedBy { it.ordinal }
            val parsedDefault = ReasoningEffort.fromApiValue(rawDefaultEffort)

            if (parsedEfforts.isNotEmpty()) {
                val resolvedDefault = when {
                    parsedDefault != null && parsedDefault in parsedEfforts -> parsedDefault
                    reasoningDefaultEnabled == false && ReasoningEffort.NONE in parsedEfforts -> ReasoningEffort.NONE
                    reasoningMandatory -> parsedEfforts.firstOrNull { it == ReasoningEffort.MEDIUM }
                        ?: parsedEfforts.first()
                    else -> parsedDefault
                }
                return ModelReasoningSpec(
                    supportedEfforts = parsedEfforts,
                    defaultEffort = resolvedDefault,
                    metadataKnown = true
                )
            }

            if (supportsParam) {
                val fallbackEfforts = if (reasoningMandatory) {
                    listOf(ReasoningEffort.LOW, ReasoningEffort.MEDIUM, ReasoningEffort.HIGH)
                } else {
                    listOf(
                        ReasoningEffort.NONE,
                        ReasoningEffort.LOW,
                        ReasoningEffort.MEDIUM,
                        ReasoningEffort.HIGH
                    )
                }
                val resolvedDefault = when {
                    parsedDefault != null && parsedDefault in fallbackEfforts -> parsedDefault
                    reasoningDefaultEnabled == false && !reasoningMandatory -> ReasoningEffort.NONE
                    reasoningDefaultEnabled == true || reasoningMandatory -> ReasoningEffort.MEDIUM
                    else -> ReasoningEffort.NONE
                }
                return ModelReasoningSpec(
                    supportedEfforts = fallbackEfforts,
                    defaultEffort = resolvedDefault,
                    metadataKnown = true
                )
            }
        }

        if (supportsParam) {
            return ModelReasoningSpec(
                supportedEfforts = listOf(
                    ReasoningEffort.NONE,
                    ReasoningEffort.LOW,
                    ReasoningEffort.MEDIUM,
                    ReasoningEffort.HIGH
                ),
                defaultEffort = ReasoningEffort.NONE,
                metadataKnown = true
            )
        }

        return ModelReasoningSpec(
            supportedEfforts = emptyList(),
            defaultEffort = null,
            metadataKnown = metadataKnown
        )
    }

    internal fun resolveModelsEndpoint(target: String): String {
        val clean = target.trim().trimEnd('/')
        return when {
            clean.endsWith("/models") -> clean
            clean.endsWith("/chat/completions") -> clean.removeSuffix("/chat/completions") + "/models"
            else -> "$clean/models"
        }
    }

    suspend fun fetchModels(
        baseUrl: String,
        apiKey: String,
        reverseProxyUrl: String = "",
        timeoutSeconds: Int = 30,
        customHeaders: String = ""
    ): Result<List<ModelInfo>> = withContext(Dispatchers.IO) {
        val target = com.runner.app.util.UrlSanitizer.sanitizeBaseUrl(
            if (reverseProxyUrl.isNotBlank()) reverseProxyUrl else baseUrl
        )
        if (target.isBlank()) {
            return@withContext Result.failure(IOException("Base URL не задан"))
        }

        val endpoint = resolveModelsEndpoint(target)
        val request = try {
            val requestBuilder = Request.Builder()
                .url(endpoint)
                .get()
                .addHeader("Accept", "application/json")
                .apply {
                    if (apiKey.isNotBlank()) {
                        val cleanKey = apiKey.trim()
                        addHeader("Authorization", "Bearer $cleanKey")
                        // Нативный Gemini-эндпоинт Bearer не принимает — ключ едет в x-goog-api-key.
                        if (extractHost(endpoint) == "generativelanguage.googleapis.com") {
                            addHeader("x-goog-api-key", cleanKey)
                        }
                    }
                }

            parseCustomHeaders(customHeaders).forEach { (name, value) ->
                requestBuilder.header(name, value)
            }
            requestBuilder.build()
        } catch (e: IllegalArgumentException) {
            return@withContext Result.failure(
                IOException("Некорректный адрес URL, ключ или заголовки: ${e.localizedMessage ?: endpoint}")
            )
        }

        val requestClient = client.newBuilder()
            .connectTimeout(timeoutSeconds.coerceIn(5, 60).toLong(), TimeUnit.SECONDS)
            .readTimeout(timeoutSeconds.coerceIn(5, 120).toLong(), TimeUnit.SECONDS)
            .build()

        val call = requestClient.newCall(request)
        val cancelHandle = coroutineContext[Job]?.invokeOnCompletion { cause ->
            if (cause != null) call.cancel()
        }
        try {
            call.execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IOException(describeHttpError(response.code, body, endpoint))
                    )
                }

                val rootJson = JSONObject(body)
                val array = rootJson.optJSONArray("data")
                    ?: rootJson.optJSONArray("models")
                    ?: JSONArray()
                val models = LinkedHashMap<String, ModelInfo>()

                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val rawId = item.optNullableString("id")?.takeIf { it.isNotBlank() }
                        ?: item.optNullableString("name").orEmpty()
                    if (rawId.isBlank()) continue
                    val id = rawId.removePrefix("models/")

                    val rawDisplayName = item.optNullableString("displayName")?.takeIf { it.isNotBlank() }
                        ?: item.optNullableString("name").orEmpty()
                    val displayName = rawDisplayName.removePrefix("models/")

                    val methodsJson = item.optJSONArray("supportedGenerationMethods")
                    val supportedMethods: List<String>? = if (methodsJson != null) {
                        buildList<String> {
                            for (i in 0 until methodsJson.length()) {
                                (methodsJson.opt(i) as? String)?.takeIf { it.isNotBlank() }?.let { add(it) }
                            }
                        }
                    } else null

                    // OpenRouter отдаёт endpoints и architecture.output_modalities:
                    // отсекаем batch-only, audio/image-only и прочие не-чат записи автоматически.
                    val endpointsJson = item.optJSONArray("endpoints")
                    if (endpointsJson != null && endpointsJson.length() > 0) {
                        val hasChatEndpoint = (0 until endpointsJson.length()).any { i ->
                            (endpointsJson.opt(i) as? String)?.endsWith("/chat/completions") == true
                        }
                        if (!hasChatEndpoint) continue
                    }
                    val outputModalities = item.optJSONObject("architecture")?.optJSONArray("output_modalities")
                    if (outputModalities != null && outputModalities.length() > 0) {
                        val outputsText = (0 until outputModalities.length()).any { i ->
                            (outputModalities.opt(i) as? String).equals("text", ignoreCase = true)
                        }
                        if (!outputsText) continue
                    }

                    if (!isChatAndToolModel(id, displayName, supportedMethods)) {
                        continue
                    }

                    val contextLength = item.optInt("context_length", 0).takeIf { it > 0 }
                        ?: item.optInt("inputTokenLimit", 0).takeIf { it > 0 }
                        ?: item.optJSONObject("top_provider")
                            ?.optInt("context_length", 0)
                            ?.takeIf { it > 0 }

                    val supportedParamsJson = item.optJSONArray("supported_parameters")
                    val supportedParams: Set<String>? = if (supportedParamsJson != null) {
                        buildSet<String> {
                            for (i in 0 until supportedParamsJson.length()) {
                                (supportedParamsJson.opt(i) as? String)?.takeIf { it.isNotBlank() }?.let { add(it) }
                            }
                        }
                    } else null

                    val hasReasoningField = item.has("reasoning")
                    val reasoningObj = item.optJSONObject("reasoning")
                    val rawEfforts: List<String>? = reasoningObj?.optJSONArray("supported_efforts")?.let { arr ->
                        buildList<String> {
                            for (i in 0 until arr.length()) {
                                (arr.opt(i) as? String)?.takeIf { it.isNotBlank() }?.let { add(it) }
                            }
                        }
                    }
                    val reasoningSpec = parseReasoningSpec(
                        supportedParameters = supportedParams,
                        hasReasoningField = hasReasoningField,
                        hasReasoningObject = reasoningObj != null,
                        reasoningMandatory = reasoningObj?.optBoolean("mandatory", false) == true,
                        reasoningDefaultEnabled = if (reasoningObj != null && reasoningObj.has("default_enabled")) {
                            reasoningObj.optBoolean("default_enabled", true)
                        } else {
                            null
                        },
                        rawSupportedEfforts = rawEfforts,
                        rawDefaultEffort = reasoningObj?.optNullableString("default_effort")
                    )

                    val cleanName = displayName.takeIf { it.isNotBlank() && it != id }.orEmpty()

                    models[id] = ModelInfo(
                        id = id,
                        name = cleanName,
                        contextLength = contextLength,
                        supportedReasoningEfforts = reasoningSpec.supportedEfforts,
                        defaultReasoningEffort = reasoningSpec.defaultEffort,
                        reasoningMetadataKnown = reasoningSpec.metadataKnown
                    )
                }

                if (models.isEmpty()) {
                    Result.failure(IOException("Провайдер вернул пустой список моделей"))
                } else {
                    Result.success(models.values.sortedBy { it.label.lowercase() })
                }
            }
        } catch (e: CancellationException) {
            call.cancel()
            throw e
        } catch (e: IOException) {
            Result.failure(IOException("Сеть недоступна: ${e.localizedMessage ?: "не удалось подключиться"}"))
        } catch (e: Exception) {
            Result.failure(IOException("Не удалось разобрать ответ: ${e.localizedMessage ?: "некорректный формат"}"))
        } finally {
            cancelHandle?.dispose()
        }
    }

    private fun describeHttpError(code: Int, body: String, endpoint: String): String {
        val details = try {
            val json = JSONObject(body)
            json.optJSONObject("error")?.optNullableString("message")?.takeIf { it.isNotBlank() }
                ?: json.optNullableString("message")?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        } ?: body.take(200)

        return when (code) {
            401, 403 -> "Ключ не подошёл ($code)"
            404 -> "Эндпоинт не найден (404): $endpoint"
            429 -> "Слишком много запросов (429)"
            else -> "Ошибка $code: $details"
        }
    }
}
