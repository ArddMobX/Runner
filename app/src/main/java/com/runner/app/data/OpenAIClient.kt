package com.runner.app.data

import com.runner.app.tools.ToolDispatcher
import com.runner.app.util.CrashReportManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.ConnectionPool
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.coroutineContext
import kotlin.math.roundToLong

data class ToolCall(
    val id: String,
    val name: String,
    val arguments: String,
    val thoughtSignature: String? = null,
    val extraContent: JSONObject? = null
)

data class ConnectionTestResult(
    val isSuccess: Boolean,
    val isReachable: Boolean,
    val statusCode: Int?,
    val latencyMs: Long,
    val message: String,
    val targetEndpoint: String
)

/** Безопасное чтение строкового поля из org.json (JSON null -> null, а не строка "null"). */
internal fun JSONObject.optNullableString(key: String): String? {
    val raw = opt(key)
    return if (raw is String) raw else null
}

/**
 * Парсит строку кастомных заголовков.
 * Поддерживает два формата:
 * 1. JSON: { "Header-Name": "Value", ... }
 * 2. Построчный: Header-Name: Value
 */
fun parseCustomHeaders(raw: String): Map<String, String> {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return emptyMap()

    // 1. Попытка разобрать как JSON
    if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
        try {
            val json = JSONObject(trimmed)
            val result = LinkedHashMap<String, String>()
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next().trim()
                val rawVal = json.opt(key)
                if (key.isNotEmpty() && rawVal != null && rawVal != JSONObject.NULL) {
                    result[key] = rawVal.toString()
                }
            }
            if (result.isNotEmpty()) return result
        } catch (_: Exception) {
            // Игнорируем ошибку и пробуем построчный разбор
        }
    }

    // 2. Построчный формат: "Header-Name: Value"
    val result = LinkedHashMap<String, String>()
    trimmed.lineSequence().forEach { line ->
        val lineTrimmed = line.trim()
        if (lineTrimmed.isEmpty() || lineTrimmed.startsWith("#") || lineTrimmed.startsWith("//")) {
            return@forEach
        }
        val colonIndex = lineTrimmed.indexOf(':')
        if (colonIndex > 0) {
            val name = lineTrimmed.substring(0, colonIndex).trim()
            val value = lineTrimmed.substring(colonIndex + 1).trim()
            if (name.isNotEmpty()) {
                result[name] = value
            }
        }
    }
    return result
}

sealed class AIResponseResult {
    data class TextResult(val text: String) : AIResponseResult()
    data class ToolCallsResult(
        val assistantMessageJson: JSONObject,
        val toolCalls: List<ToolCall>
    ) : AIResponseResult()

    /** retryable = true, если запрос можно безопасно повторить (ничего не успели показать). */
    /**
     * [optionalField] заполняется, когда провайдер пожаловался на неизвестное поле
     * запроса. Цикл отправки убирает это поле и повторяет запрос без него.
     */
    data class Error(
        val message: String,
        val retryable: Boolean = false,
        val optionalField: String? = null
    ) : AIResponseResult()

    /** Пользователь нажал «Стоп». Это не ошибка, показывать красным не нужно. */
    object Cancelled : AIResponseResult()
}

/**
 * Метрики одного обращения к модели. Живёт от начала запроса до его конца,
 * читается вызывающей стороной после возврата.
 */
class GenerationMetrics {

    @Volatile
    var promptTokens: Int? = null

    @Volatile
    var completionTokens: Int? = null

    @Volatile
    var reasoningText: String = ""

    @Volatile
    var reasoningMillis: Long = 0L

    @Volatile
    var finishReason: String? = null

    private var reasoningStartedAt = 0L
    private var reasoningEndedAt = 0L
    private val reasoningBuffer = StringBuilder()

    private var firstOutputAt = 0L
    private var lastOutputAt = 0L

    /** Сбрасывает накопленное состояние перед новой попыткой (retry / optional strip). */
    fun reset() {
        promptTokens = null
        completionTokens = null
        reasoningText = ""
        reasoningMillis = 0L
        finishReason = null
        reasoningStartedAt = 0L
        reasoningEndedAt = 0L
        reasoningBuffer.setLength(0)
        firstOutputAt = 0L
        lastOutputAt = 0L
    }

    fun noteReasoning(delta: String, now: Long) {
        if (reasoningStartedAt == 0L) reasoningStartedAt = now
        reasoningEndedAt = now
        if (firstOutputAt == 0L) firstOutputAt = now
        if (now > lastOutputAt) lastOutputAt = now
        reasoningBuffer.append(delta)
    }

    fun noteContent(now: Long) {
        if (firstOutputAt == 0L) firstOutputAt = now
        if (now > lastOutputAt) lastOutputAt = now
    }

    /** Вызывается по завершении запроса — фиксирует размышления и их длительность. */
    fun finish() {
        reasoningText = reasoningBuffer.toString()
        reasoningMillis = if (reasoningStartedAt > 0L && reasoningEndedAt >= reasoningStartedAt) {
            reasoningEndedAt - reasoningStartedAt
        } else {
            0L
        }
    }

    /**
     * Скорость генерации: выходные токены (включая reasoning-токены) за время
     * от первого полученного токена (размышлений или текста) до последнего.
     */
    val tokensPerSecond: Double?
        get() {
            val tokens = completionTokens ?: return null
            if (firstOutputAt == 0L || lastOutputAt <= firstOutputAt) return null
            val seconds = (lastOutputAt - firstOutputAt) / 1000.0
            if (seconds < 0.15) return null
            return tokens / seconds
        }
}

class OpenAIClient {

    /** Колбэки для живого вывода. Все методы необязательные. */
    interface StreamListener {
        fun onTextDelta(delta: String) {}
        fun onToolCallStarted(name: String) {}
        fun onRetry(message: String, delayMillis: Long) {}

        /**
         * Кусок размышлений thinking-модели. UI показывает его живьём,
         * чтобы долгое «думание» не выглядело как зависание.
         */
        fun onReasoningDelta(delta: String) {}
    }

    /**
     * Поддерживает ли эндпоинт stream_options.include_usage.
     *
     * У известных хостов это проверено один раз. У незнакомых (свои прокси и
     * шлюзы) выясняем опытом: пока попытки не исчерпаны, поле отправляется,
     * а после двух жалоб на неизвестный параметр больше не шлётся.
     */
    private fun streamUsageSupported(endpoint: String): Boolean {
        if (endpoint.isBlank()) return false
        if (isKnownStreamUsageHost(endpoint)) return true
        return (streamUsageFailures[endpoint] ?: 0) < STREAM_USAGE_PROBE_ATTEMPTS
    }

    /** Провайдер отказался от stream_options: считаем отказы по эндпоинту. */
    private fun noteStreamUsageRejected(endpoint: String) {
        if (endpoint.isNotBlank()) {
            streamUsageFailures[endpoint] = (streamUsageFailures[endpoint] ?: 0) + 1
        }
    }

    private fun supportsReasoningEffort(model: String): Boolean =
        OpenAIClient.supportsReasoningEffort(model)

    private fun supportsOpenRouterReasoning(model: String): Boolean =
        OpenAIClient.supportsOpenRouterReasoning(model)

    /**
     * Достаёт текст размышлений в разных форматах:
     * reasoning_content у DeepSeek, reasoning у OpenRouter, reasoning_details
     * у прокси, которые отдают структурированные блоки.
     */
    private fun extractReasoningText(source: JSONObject): String? {
        source.optNullableString("reasoning_content")
            ?.takeIf { it.isNotEmpty() }
            ?.let { return it }
        source.optNullableString("reasoning")
            ?.takeIf { it.isNotEmpty() }
            ?.let { return it }

        val details = source.optJSONArray("reasoning_details") ?: return null
        val parts = mutableListOf<String>()
        for (index in 0 until details.length()) {
            val item = details.optJSONObject(index) ?: continue
            item.optNullableString("text")
                ?.takeIf { it.isNotEmpty() }
                ?.let { parts += it }
            item.optNullableString("summary")
                ?.takeIf { it.isNotEmpty() }
                ?.let { parts += it }
            val content = item.optJSONArray("content") ?: continue
            for (partIndex in 0 until content.length()) {
                val part = content.optJSONObject(partIndex) ?: continue
                part.optNullableString("text")
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { parts += it }
            }
        }
        return parts.joinToString("").takeIf { it.isNotEmpty() }
    }

    private val client = OkHttpClient.Builder()
        .connectionPool(ConnectionPool(8, 5, TimeUnit.MINUTES))
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private fun clientWithTimeout(
        connectTimeoutSeconds: Int = 15,
        responseTimeoutSeconds: Int = 60
    ): OkHttpClient {
        val connectSec = connectTimeoutSeconds.coerceIn(5, 60).toLong()
        val readSec = responseTimeoutSeconds.coerceIn(10, 300).toLong()
        return client.newBuilder()
            .connectTimeout(connectSec, TimeUnit.SECONDS)
            .readTimeout(readSec, TimeUnit.SECONDS)
            .writeTimeout(connectSec.coerceAtLeast(30), TimeUnit.SECONDS)
            .build()
    }

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val activeCalls = ConcurrentHashMap.newKeySet<Call>()

    /** Сколько раз эндпоинт отказался от stream_options. Живёт в памяти. */
    private val streamUsageFailures = ConcurrentHashMap<String, Int>()

    /** Обрывает все текущие запросы. Стриминг прекращается сразу. */
    fun cancelActive() {
        val snapshot = activeCalls.toList()
        activeCalls.clear()
        snapshot.forEach { it.cancel() }
    }

    suspend fun sendChatCompletion(
        baseUrl: String,
        apiKey: String,
        modelName: String,
        messages: JSONArray,
        reverseProxyUrl: String = "",
        timeoutSeconds: Int = 60,
        connectTimeoutSeconds: Int = 15,
        responseTimeoutSeconds: Int = timeoutSeconds,
        customHeaders: String = "",
        temperature: Double = 0.2,
        maxTokens: Int = 0,
        streaming: Boolean = true,
        listener: StreamListener? = null,
        metrics: GenerationMetrics? = null,
        /** false — планировочный запрос без тулов: модель отвечает только текстом. */
        withTools: Boolean = true,
        /**
         * Какие именно инструменты объявить в запросе. `null` — все, доступные
         * в текущем режиме.
         */
        toolNames: Set<String>? = null,
        /**
         * Включён ли Live-режим. Инструменты управления экраном (`ui_*`)
         * объявляются только при нём.
         */
        liveMode: Boolean = false,
        /** Бюджет контекста в символах: старые реплики отбрасываются, чтобы не упереться в лимит. */
        contextBudgetChars: Int = DEFAULT_CONTEXT_BUDGET_CHARS,
        /**
         * Тип провайдера. Решает, какие необязательные поля запроса ему можно
         * слать, вместо угадывания по подстроке в URL.
         */
        providerKind: ProviderKind = ProviderKind.OPENAI_COMPATIBLE,
        /**
         * Base URL ведёт на официальный api.deepseek.com. Только там можно
         * выключить thinking: у сторонних прокси это поле не из OpenAI-схемы
         * и строгий сервер ответит 400.
         */
        officialDeepSeekHost: Boolean = false,
        /**
         * Проверка «пользователь нажал Стоп». Нужна внутри пауз между ретраями:
         * без неё кнопка не реагирует до конца ожидания, а запрос всё равно уходит.
         */
        shouldStop: () -> Boolean = { false },
        /**
         * Режим «Думать»: включить thinking у DeepSeek и reasoning_effort
         * у моделей OpenAI, если они это поддерживают.
         */
        forceThinking: Boolean = false,
        /**
         * Явный уровень Reasoning Effort для моделей с его поддержкой.
         * null — использовать прежнее поведение (forceThinking / liveMode / дефолт провайдера).
         */
        reasoningEffort: ReasoningEffort? = null
    ): AIResponseResult = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext AIResponseResult.Error(
                "API ключ не задан. Открой настройки и укажи ключ провайдера."
            )
        }

        val effectiveBaseUrl = baseUrl.ifBlank { "https://openrouter.ai/api/v1" }
        val endpoint = resolveChatEndpoint(effectiveBaseUrl, reverseProxyUrl)
        val endpointHost = extractHost(endpoint)
        val baseHost = extractHost(effectiveBaseUrl)
        val hasReverseProxy = reverseProxyUrl.isNotBlank()

        // Тип провайдера определяется по ProviderKind и точному хосту, но НЕ по имени модели:
        // иначе при вызове Gemini через OpenRouter/шлюзы ключ утекал в x-goog-api-key,
        // а в запросы подмешивался Google-специфичный thought_signature.
        val isGemini = providerKind == ProviderKind.GEMINI ||
                endpointHost == "generativelanguage.googleapis.com" ||
                baseHost == "generativelanguage.googleapis.com"

        val effectiveModel = modelName.removePrefix("models/").trim().ifBlank {
            if (isGemini) "gemini-4-hydrogen" else "openai/gpt-oss-120b"
        }

        val isOpenRouterHost = !hasReverseProxy &&
                (endpointHost == "openrouter.ai" || endpointHost.endsWith(".openrouter.ai"))
        val isDirectOfficialDeepSeek = !hasReverseProxy &&
                providerKind == ProviderKind.DEEPSEEK &&
                (officialDeepSeekHost || endpointHost == "api.deepseek.com")

        // Параметры, которые провайдер может не знать. При жалобе на неизвестное
        // поле убираем его и повторяем сразу, не тратя попытку ретрая.
        var allowStreamUsage = streamUsageSupported(endpoint)
        var allowTemperature = true
        // Не отправляем max_tokens и max_completion_tokens одновременно:
        // OpenAI o1/o3/o4/gpt-5 отклоняют max_tokens, старые шлюзы — max_completion_tokens.
        var maxTokensParam: String? = when {
            maxTokens <= 0 -> null
            supportsReasoningEffort(effectiveModel) -> "max_completion_tokens"
            else -> "max_tokens"
        }
        var triedMaxTokensFallback = false
        var allowThinking = isDirectOfficialDeepSeek
        var allowReasoning = true
        var allowReasoningEffort = true
        var allowTools = withTools
        var optionalStripped = 0

        var forceSkipValidator = false
        var signatureFallback = false
        var attempt = 0
        val stallTimeoutMs = computeStreamingWatchdogTimeoutMs(responseTimeoutSeconds)
        val rateLimitBaseHost = baseHost.takeIf { it.isNotBlank() }

        while (attempt <= MAX_RETRIES) {
            if (shouldStop()) return@withContext AIResponseResult.Cancelled

            val sanitizedMessages = sanitizeMessages(messages, isGemini, forceSkipValidator)
            // Старые реплики отбрасываем до отправки: иначе длинная сессия
            // упирается в лимит модели и падает с 400 без пути восстановления.
            val contextMessages = trimMessagesToBudget(sanitizedMessages, contextBudgetChars)
            val sentOptionalParams = LinkedHashSet<String>()

            fun buildBody(): JSONObject = JSONObject().apply {
                put("model", effectiveModel.removePrefix("models/"))
                put("messages", contextMessages)
                if (allowTools) {
                    val tools = ToolDispatcher.getToolsJson(toolNames, liveMode)
                    if (tools.length() > 0) {
                        put("tools", tools)
                        put("tool_choice", "auto")
                        sentOptionalParams += "tools"
                        sentOptionalParams += "tool_choice"
                    }
                }
                if (allowTemperature) {
                    val cleanTemp = (temperature * 100.0).roundToLong() / 100.0
                    put("temperature", cleanTemp)
                    sentOptionalParams += "temperature"
                }
                val activeMaxTokensKey = maxTokensParam
                if (activeMaxTokensKey != null && maxTokens > 0) {
                    put(activeMaxTokensKey, maxTokens)
                    sentOptionalParams += activeMaxTokensKey
                }
                if (streaming) {
                    put("stream", true)
                    if (allowStreamUsage) {
                        put("stream_options", JSONObject().put("include_usage", true))
                        sentOptionalParams += "stream_options"
                    }
                }
                val reasoningDecision = resolveRequestReasoning(
                    isDirectOfficialDeepSeek = isDirectOfficialDeepSeek,
                    allowThinking = allowThinking,
                    isOpenRouterHost = isOpenRouterHost,
                    allowReasoning = allowReasoning,
                    allowReasoningEffort = allowReasoningEffort,
                    endpointHost = endpointHost,
                    effectiveModel = effectiveModel,
                    allowTools = allowTools,
                    liveMode = liveMode,
                    forceThinking = forceThinking,
                    reasoningEffort = reasoningEffort
                )
                reasoningDecision.deepSeekThinkingEnabled?.let { enabled ->
                    put("thinking", JSONObject().put("type", if (enabled) "enabled" else "disabled"))
                    sentOptionalParams += "thinking"
                }
                reasoningDecision.openRouterEffort?.let { effort ->
                    put("reasoning", JSONObject().put("effort", effort))
                    sentOptionalParams += "reasoning"
                }
                reasoningDecision.reasoningEffort?.let { effort ->
                    put("reasoning_effort", effort)
                    sentOptionalParams += "reasoning_effort"
                }
            }

            val requestBody = buildBody()
            val request = try {
                val requestBuilder = Request.Builder()
                    .url(endpoint)
                    .addHeader("Authorization", "Bearer ${apiKey.trim()}")
                    .addHeader("Content-Type", "application/json")
                    .addHeader("HTTP-Referer", "https://github.com/ArddMobX/Runner")
                    .addHeader("X-Title", "Runner Android Agent")

                if (isGemini) {
                    requestBuilder.addHeader("x-goog-api-key", apiKey.trim())
                }

                parseCustomHeaders(customHeaders).forEach { (name, value) ->
                    requestBuilder.header(name, value)
                }

                requestBuilder
                    .post(requestBody.toString().toRequestBody(jsonMediaType))
                    .build()
            } catch (e: IllegalArgumentException) {
                return@withContext AIResponseResult.Error(
                    "Некорректный URL, API-ключ или заголовки запроса: ${e.localizedMessage ?: endpoint}"
                )
            }

            if (shouldStop()) return@withContext AIResponseResult.Cancelled

            val call = clientWithTimeout(connectTimeoutSeconds, responseTimeoutSeconds).newCall(request)
            activeCalls.add(call)
            val cancelHandle = coroutineContext[Job]?.invokeOnCompletion { cause ->
                if (cause != null) call.cancel()
            }
            metrics?.reset()
            CrashReportManager.log("API", "Запрос $effectiveModel, попытка ${attempt + 1}")
            try {
                val fallback = signatureFallback || (isGemini && attempt > 0)
                val result = if (streaming) {
                    executeStreaming(
                        call = call,
                        listener = listener,
                        signatureFallback = fallback,
                        metrics = metrics,
                        stallTimeoutMs = stallTimeoutMs,
                        sentOptionalParams = sentOptionalParams,
                        rateLimitBaseHost = rateLimitBaseHost
                    )
                } else {
                    executeBlocking(
                        call = call,
                        signatureFallback = fallback,
                        metrics = metrics,
                        sentOptionalParams = sentOptionalParams,
                        rateLimitBaseHost = rateLimitBaseHost
                    )
                }

                if (result is AIResponseResult.Error && result.optionalField != null &&
                    optionalStripped < MAX_OPTIONAL_STRIPS
                ) {
                    var changed = false
                    when (result.optionalField) {
                        "stream_options" -> if (allowStreamUsage) {
                            allowStreamUsage = false
                            noteStreamUsageRejected(endpoint)
                            changed = true
                        }
                        "temperature" -> if (allowTemperature) {
                            allowTemperature = false
                            changed = true
                        }
                        "max_tokens" -> if (maxTokensParam == "max_tokens") {
                            maxTokensParam = if (!triedMaxTokensFallback) {
                                triedMaxTokensFallback = true
                                "max_completion_tokens"
                            } else {
                                null
                            }
                            changed = true
                        }
                        "max_completion_tokens" -> if (maxTokensParam == "max_completion_tokens") {
                            maxTokensParam = if (!triedMaxTokensFallback) {
                                triedMaxTokensFallback = true
                                "max_tokens"
                            } else {
                                null
                            }
                            changed = true
                        }
                        "thinking" -> if (allowThinking) {
                            allowThinking = false
                            changed = true
                        }
                        "reasoning" -> if (allowReasoning) {
                            allowReasoning = false
                            changed = true
                        }
                        "reasoning_effort" -> if (allowReasoningEffort) {
                            allowReasoningEffort = false
                            changed = true
                        }
                        "tools", "tool_choice" -> if (allowTools) {
                            allowTools = false
                            changed = true
                        }
                    }
                    if (changed) {
                        optionalStripped++
                        continue
                    }
                }

                if (result is AIResponseResult.Error && result.retryable && attempt < MAX_RETRIES) {
                    if (result.message.contains("thought_signature", ignoreCase = true)) {
                        forceSkipValidator = true
                        signatureFallback = true
                    }
                    attempt++
                    val pause = backoffMillis(attempt)
                    val retryText = if (result.message.contains("429")) {
                        "Лимит запросов, ожидаю снятия ограничения"
                    } else {
                        result.message
                    }
                    listener?.onRetry(retryText, pause)
                    if (waitForRetry(pause, shouldStop)) return@withContext AIResponseResult.Cancelled
                    continue
                }
                return@withContext result
            } catch (e: CancellationException) {
                call.cancel()
                throw e
            } catch (e: IOException) {
                if (call.isCanceled() || shouldStop()) return@withContext AIResponseResult.Cancelled
                if (attempt < MAX_RETRIES) {
                    attempt++
                    val pause = backoffMillis(attempt)
                    listener?.onRetry("Сеть недоступна, повтор через ${pause / 1000} с", pause)
                    if (waitForRetry(pause, shouldStop)) return@withContext AIResponseResult.Cancelled
                    continue
                }
                return@withContext AIResponseResult.Error(
                    "Сетевая ошибка: ${e.localizedMessage ?: "не удалось подключиться к серверу"}"
                )
            } catch (e: JSONException) {
                return@withContext AIResponseResult.Error(
                    "Ответ сервера не распознан (битый JSON). Повторите запрос."
                )
            } catch (e: Exception) {
                return@withContext AIResponseResult.Error(
                    "Ошибка обработки ответа: ${e.localizedMessage ?: "неизвестный сбой"}"
                )
            } finally {
                cancelHandle?.dispose()
                activeCalls.remove(call)
            }
        }

        AIResponseResult.Error("Превышено количество попыток запроса к API.")
    }

    /**
     * Обёртка над разбором SSE: заводит сторожа потока и превращает зависание
     * в обычную retryable-ошибку (если текст ещё не выведен), а не в вечное ожидание.
     */
    private fun executeStreaming(
        call: Call,
        listener: StreamListener?,
        signatureFallback: Boolean,
        metrics: GenerationMetrics?,
        stallTimeoutMs: Long,
        sentOptionalParams: Set<String>,
        rateLimitBaseHost: String?
    ): AIResponseResult {
        val watchdog = StreamWatchdog(stallTimeoutMs)
        val emitted = AtomicBoolean(false)
        watchdog.start(call)
        CrashReportManager.log("API", "Стрим начат")
        try {
            val result = executeStreamingInner(
                call = call,
                listener = listener,
                signatureFallback = signatureFallback,
                metrics = metrics,
                watchdog = watchdog,
                emitted = emitted,
                sentOptionalParams = sentOptionalParams,
                rateLimitBaseHost = rateLimitBaseHost
            )
            if (watchdog.stalled) {
                CrashReportManager.log(
                    "API",
                    "Поток завис: ${stallTimeoutMs / 1000} с без полезных данных"
                )
                return AIResponseResult.Error(
                    "Модель не отвечает уже ${stallTimeoutMs / 1000} с.",
                    retryable = !emitted.get()
                )
            }
            CrashReportManager.log("API", "Стрим завершён: ${result::class.simpleName}")
            return result
        } catch (e: IOException) {
            if (watchdog.stalled) {
                CrashReportManager.log(
                    "API",
                    "Поток завис: ${stallTimeoutMs / 1000} с без полезных данных"
                )
                return AIResponseResult.Error(
                    "Модель не отвечает уже ${stallTimeoutMs / 1000} с.",
                    retryable = !emitted.get()
                )
            }
            throw e
        } finally {
            watchdog.stop()
        }
    }

    /** Разбирает SSE-поток: content уходит в UI по мере поступления, tool_calls склеиваются по index/id. */
    private fun executeStreamingInner(
        call: Call,
        listener: StreamListener?,
        signatureFallback: Boolean,
        metrics: GenerationMetrics?,
        watchdog: StreamWatchdog,
        emitted: AtomicBoolean,
        sentOptionalParams: Set<String>,
        rateLimitBaseHost: String?
    ): AIResponseResult {
        val content = StringBuilder()
        val rawReasoningContent = StringBuilder()
        val toolCallSlots = sortedMapOf<Int, ToolCallAccumulator>()

        call.execute().use { response ->
            captureRateLimits(response, rateLimitBaseHost)
            if (!response.isSuccessful) {
                val errorBody = response.body?.string().orEmpty()
                return withEmitted(
                    httpError(response.code, errorBody, call, sentOptionalParams),
                    emitted.get()
                )
            }

            val responseBody = response.body
                ?: return AIResponseResult.Error("Пустой ответ от сервера.")

            // Если прокси/провайдер проигнорировал stream=true и вернул обычный JSON (HTTP 200),
            // разбираем его напрямую, вместо того чтобы молча получить «пустой ответ».
            val contentType = response.header("Content-Type").orEmpty().lowercase()
            if (contentType.contains("json") && !contentType.contains("text/event-stream")) {
                val jsonText = responseBody.string()
                return parseBlockingJson(
                    responseBody = jsonText,
                    signatureFallback = signatureFallback,
                    metrics = metrics,
                    listener = listener,
                    emitted = emitted
                )
            }

            val source = responseBody.source()
            val eventData = StringBuilder()
            val nonSseBuffer = StringBuilder()
            var sawSseData = false

            fun processChunk(chunk: JSONObject): AIResponseResult? {
                // OpenRouter и часть прокси присылают ошибку отдельным SSE-чанком без choices.
                val streamErrorObj = chunk.optJSONObject("error")
                val streamErrorText = when {
                    streamErrorObj != null -> streamErrorObj.optNullableString("message")
                        ?.takeIf { it.isNotBlank() }
                        ?: streamErrorObj.toString()
                    else -> chunk.optNullableString("error")?.takeIf { it.isNotBlank() }
                }
                if (!streamErrorText.isNullOrBlank()) {
                    val code = streamErrorObj?.optInt("code", 0) ?: 0
                    val prefix = if (code > 0) "Ошибка провайдера ($code)" else "Ошибка провайдера"
                    return AIResponseResult.Error("$prefix: $streamErrorText")
                }

                // Чанк с usage приходит отдельно и с пустым choices — читаем до разбора choices
                val usage = chunk.optJSONObject("usage")
                if (usage != null && metrics != null) {
                    val prompt = usage.optInt("prompt_tokens", 0)
                    if (prompt > 0) metrics.promptTokens = prompt
                    val completion = usage.optInt("completion_tokens", 0)
                    if (completion > 0) {
                        metrics.completionTokens = completion
                    }
                }
                if (usage != null) watchdog.touch()

                val choices = chunk.optJSONArray("choices") ?: return null
                val choice0 = choices.optJSONObject(0) ?: return null
                val choiceFinishReason = choice0.optNullableString("finish_reason")?.takeIf { it.isNotBlank() }
                if (choiceFinishReason != null) {
                    metrics?.finishReason = choiceFinishReason
                }

                val delta = choice0.optJSONObject("delta") ?: return null
                val now = System.currentTimeMillis()

                // Сохраняем сырой reasoning_content (нужен DeepSeek Thinking Mode для echo в tool_calls)
                delta.optNullableString("reasoning_content")?.takeIf { it.isNotEmpty() }?.let {
                    rawReasoningContent.append(it)
                }

                val reasoningPiece = extractReasoningText(delta)
                if (reasoningPiece != null) {
                    metrics?.noteReasoning(reasoningPiece, now)
                    listener?.onReasoningDelta(reasoningPiece)
                    watchdog.touch()
                }

                delta.optNullableString("content")?.takeIf { it.isNotEmpty() }?.let { piece ->
                    metrics?.noteContent(now)
                    content.append(piece)
                    emitted.set(true)
                    listener?.onTextDelta(piece)
                    watchdog.touch()
                }

                val deltaExtra = delta.optJSONObject("extra_content")
                    ?: choice0.optJSONObject("extra_content")
                    ?: chunk.optJSONObject("extra_content")

                val deltaSig = delta.optNullableString("thought_signature")?.takeIf { it.isNotBlank() }
                    ?: delta.optNullableString("thoughtSignature")?.takeIf { it.isNotBlank() }
                    ?: deltaExtra?.optJSONObject("google")?.optNullableString("thought_signature")?.takeIf { it.isNotBlank() }
                    ?: deltaExtra?.optNullableString("thought_signature")?.takeIf { it.isNotBlank() }

                val deltaToolCalls = delta.optJSONArray("tool_calls")
                if (deltaToolCalls != null) {
                    watchdog.touch()
                    for (index in 0 until deltaToolCalls.length()) {
                        val item = deltaToolCalls.optJSONObject(index) ?: continue
                        val hasIndex = item.has("index") && !item.isNull("index")
                        val itemIndex = if (hasIndex) item.optInt("index", -1) else -1
                        val itemId = item.optNullableString("id")?.takeIf { it.isNotBlank() }

                        val accumulator = when {
                            itemIndex >= 0 -> toolCallSlots.getOrPut(itemIndex) { ToolCallAccumulator() }
                            itemId != null -> {
                                toolCallSlots.values.firstOrNull { it.id == itemId }
                                    ?: toolCallSlots.getOrPut(nextToolCallSlotKey(toolCallSlots.keys)) {
                                        ToolCallAccumulator()
                                    }
                            }
                            toolCallSlots.isNotEmpty() -> toolCallSlots.values.last()
                            else -> toolCallSlots.getOrPut(0) { ToolCallAccumulator() }
                        }

                        if (itemId != null && accumulator.id.isBlank()) {
                            accumulator.id = itemId
                        }

                        val function = item.optJSONObject("function")
                        if (function != null) {
                            function.optNullableString("name")?.takeIf { it.isNotBlank() }?.let { name ->
                                if (accumulator.name.isBlank()) {
                                    accumulator.name = name
                                    listener?.onToolCallStarted(name)
                                }
                            }
                            extractStreamingArgsDelta(function)?.let { argsDelta ->
                                accumulator.arguments.append(argsDelta)
                            }
                        }

                        val itemExtra = item.optJSONObject("extra_content") ?: deltaExtra
                        if (itemExtra != null) {
                            accumulator.extraContent = itemExtra
                            val sig = itemExtra.optJSONObject("google")?.optNullableString("thought_signature")?.takeIf { it.isNotBlank() }
                                ?: itemExtra.optNullableString("thought_signature")?.takeIf { it.isNotBlank() }
                            if (sig != null) {
                                accumulator.thoughtSignature = sig
                            }
                        }

                        val itemSig = item.optNullableString("thought_signature")?.takeIf { it.isNotBlank() }
                            ?: item.optNullableString("thoughtSignature")?.takeIf { it.isNotBlank() }
                            ?: deltaSig
                        if (itemSig != null) {
                            accumulator.thoughtSignature = itemSig
                        }
                    }
                } else if (deltaSig != null && toolCallSlots.isNotEmpty()) {
                    toolCallSlots.values.last().thoughtSignature = deltaSig
                    if (deltaExtra != null) {
                        toolCallSlots.values.last().extraContent = deltaExtra
                    }
                }
                return null
            }

            fun flushEventData(): AIResponseResult? {
                if (eventData.isEmpty()) return null
                val payload = eventData.toString().trim()
                eventData.setLength(0)
                if (payload.isEmpty() || payload == DONE_SENTINEL) return null
                val chunk = try {
                    JSONObject(payload)
                } catch (_: Exception) {
                    return null
                }
                return processChunk(chunk)
            }

            while (true) {
                val line = source.readUtf8Line() ?: break
                if (line.isEmpty()) {
                    flushEventData()?.let { return it }
                    continue
                }
                if (line.startsWith(":")) continue

                if (line.startsWith("data:")) {
                    sawSseData = true
                    val piece = line.substring(5).removePrefix(" ")
                    if (piece.trim() == DONE_SENTINEL) {
                        flushEventData()?.let { return it }
                        break
                    }
                    if (eventData.isNotEmpty()) eventData.append('\n')
                    eventData.append(piece)

                    // Поддерживаем и стандартный многострочный SSE (с пустой строкой-разделителем),
                    // и прокси, шлющие `data: {...}` подряд без пустых строк.
                    val candidate = eventData.toString().trim()
                    if (candidate.startsWith("{") && candidate.endsWith("}")) {
                        val parsed = try {
                            JSONObject(candidate)
                        } catch (_: Exception) {
                            null
                        }
                        if (parsed != null) {
                            eventData.setLength(0)
                            processChunk(parsed)?.let { return it }
                        }
                    }
                } else if (!sawSseData && nonSseBuffer.length < NON_SSE_FALLBACK_MAX_CHARS) {
                    nonSseBuffer.append(line).append('\n')
                }
            }

            flushEventData()?.let { return it }

            // Если сервер вернул JSON без Content-Type: application/json и без SSE `data:`
            if (!sawSseData && nonSseBuffer.isNotBlank()) {
                val raw = nonSseBuffer.toString().trim()
                if (raw.startsWith("{")) {
                    return parseBlockingJson(
                        responseBody = raw,
                        signatureFallback = signatureFallback,
                        metrics = metrics,
                        listener = listener,
                        emitted = emitted
                    )
                }
            }
        }

        metrics?.finish()

        if (toolCallSlots.isEmpty()) {
            val text = content.toString()
            return if (text.isBlank()) {
                if (metrics?.finishReason == "length") {
                    AIResponseResult.Error(
                        "Модель израсходовала лимит вывода на размышления и не оставила текста. " +
                                "Увеличь лимит вывода или выключи «Думать».",
                        retryable = false
                    )
                } else {
                    AIResponseResult.Error("Модель вернула пустой ответ.", retryable = !emitted.get())
                }
            } else {
                AIResponseResult.TextResult(text)
            }
        }
        return buildToolCallResult(
            text = content.toString(),
            slots = toolCallSlots,
            signatureFallback = signatureFallback,
            reasoningContent = rawReasoningContent.toString().takeIf { it.isNotEmpty() }
        )
    }

    private fun executeBlocking(
        call: Call,
        signatureFallback: Boolean,
        metrics: GenerationMetrics?,
        sentOptionalParams: Set<String>,
        rateLimitBaseHost: String?
    ): AIResponseResult {
        call.execute().use { response ->
            captureRateLimits(response, rateLimitBaseHost)
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                return httpError(response.code, responseBody, call, sentOptionalParams)
            }
            return parseBlockingJson(
                responseBody = responseBody,
                signatureFallback = signatureFallback,
                metrics = metrics,
                listener = null,
                emitted = null
            )
        }
    }

    private fun parseBlockingJson(
        responseBody: String,
        signatureFallback: Boolean,
        metrics: GenerationMetrics?,
        listener: StreamListener?,
        emitted: AtomicBoolean?
    ): AIResponseResult {
        val json = JSONObject(responseBody)

        val errObj = json.optJSONObject("error")
        val errText = when {
            errObj != null -> errObj.optNullableString("message")?.takeIf { it.isNotBlank() } ?: errObj.toString()
            else -> json.optNullableString("error")?.takeIf { it.isNotBlank() }
        }
        if (!errText.isNullOrBlank()) {
            val code = errObj?.optInt("code", 0) ?: 0
            val prefix = if (code > 0) "Ошибка провайдера ($code)" else "Ошибка провайдера"
            return AIResponseResult.Error("$prefix: $errText")
        }

        val choices = json.optJSONArray("choices")
        if (choices == null || choices.length() == 0) {
            return AIResponseResult.Error("Получен пустой ответ от модели.")
        }

        json.optJSONObject("usage")?.let { usage ->
            if (metrics != null) {
                val prompt = usage.optInt("prompt_tokens", 0)
                if (prompt > 0) metrics.promptTokens = prompt
                val completion = usage.optInt("completion_tokens", 0)
                if (completion > 0) metrics.completionTokens = completion
            }
        }

        val choice0 = choices.getJSONObject(0)
        choice0.optNullableString("finish_reason")?.takeIf { it.isNotBlank() }?.let {
            metrics?.finishReason = it
        }
        val message = choice0.getJSONObject("message")

        val reasoning = extractReasoningText(message)
        if (reasoning != null) {
            val now = System.currentTimeMillis()
            metrics?.noteReasoning(reasoning, now)
            listener?.onReasoningDelta(reasoning)
        }
        metrics?.finish()

        val toolCallsJson = message.optJSONArray("tool_calls")
        val messageExtra = message.optJSONObject("extra_content")
        val messageSig = messageExtra?.optJSONObject("google")?.optNullableString("thought_signature")?.takeIf { it.isNotBlank() }
            ?: messageExtra?.optNullableString("thought_signature")?.takeIf { it.isNotBlank() }
            ?: message.optNullableString("thought_signature")?.takeIf { it.isNotBlank() }

        if (toolCallsJson != null && toolCallsJson.length() > 0) {
            val calls = mutableListOf<ToolCall>()
            var firstSig: String? = messageSig

            for (index in 0 until toolCallsJson.length()) {
                val item = toolCallsJson.getJSONObject(index)
                val function = item.getJSONObject("function")

                var sig = item.optJSONObject("extra_content")?.optJSONObject("google")?.optNullableString("thought_signature")?.takeIf { it.isNotBlank() }
                    ?: item.optJSONObject("extra_content")?.optNullableString("thought_signature")?.takeIf { it.isNotBlank() }
                    ?: item.optNullableString("thought_signature")?.takeIf { it.isNotBlank() }
                    ?: messageSig

                if (sig.isNullOrBlank() && signatureFallback) {
                    sig = GEMINI_THOUGHT_SIGNATURE_BYPASS
                }
                if (firstSig == null && !sig.isNullOrBlank()) {
                    firstSig = sig
                }

                if (!sig.isNullOrBlank()) {
                    val extra = item.optJSONObject("extra_content") ?: JSONObject()
                    val google = extra.optJSONObject("google") ?: JSONObject()
                    google.put("thought_signature", sig)
                    extra.put("google", google)
                    item.put("extra_content", extra)
                    item.put("thought_signature", sig)
                }

                val callId = item.optNullableString("id").orEmpty()
                    .ifBlank { "call_${System.currentTimeMillis()}_$index" }
                item.put("id", callId)

                val toolName = function.getString("name")
                listener?.onToolCallStarted(toolName)

                calls.add(
                    ToolCall(
                        id = callId,
                        name = toolName,
                        arguments = functionArgsToString(function),
                        thoughtSignature = sig,
                        extraContent = item.optJSONObject("extra_content")
                    )
                )
            }

            if (firstSig != null && !message.has("extra_content")) {
                message.put(
                    "extra_content",
                    JSONObject().put("google", JSONObject().put("thought_signature", firstSig))
                )
            }

            return AIResponseResult.ToolCallsResult(message, calls)
        }

        val text = message.optNullableString("content").orEmpty()
        if (text.isBlank()) {
            return if (metrics?.finishReason == "length") {
                AIResponseResult.Error(
                    "Модель израсходовала лимит вывода на размышления и не оставила текста. " +
                            "Увеличь лимит вывода или выключи «Думать».",
                    retryable = false
                )
            } else {
                AIResponseResult.Error("Модель вернула пустой ответ.", retryable = true)
            }
        }
        if (listener != null) {
            emitted?.set(true)
            listener.onTextDelta(text)
        }
        return AIResponseResult.TextResult(text)
    }

    /**
     * Извлекает дельту аргументов из SSE-чанка без порчи пробельных токенов
     * (`" "`, `"\n"`, `"    "`) и буквального токена `"{}"`.
     */
    private fun extractStreamingArgsDelta(function: JSONObject): String? {
        return when (val args = function.opt("arguments")) {
            is String -> args.takeIf { it.isNotEmpty() }
            is JSONObject, is JSONArray -> args.toString()
            else -> null
        }
    }

    /**
     * Аргументы полного (нестримингового) вызова в строку: Gemini иногда отдаёт
     * arguments объектом, а не строкой — приводим к JSON-строке вместо потери.
     */
    private fun functionArgsToString(function: JSONObject): String {
        return when (val args = function.opt("arguments")) {
            is String -> args.ifBlank { "{}" }
            is JSONObject, is JSONArray -> args.toString()
            else -> "{}"
        }
    }

    private fun buildToolCallResult(
        text: String,
        slots: Map<Int, ToolCallAccumulator>,
        signatureFallback: Boolean,
        reasoningContent: String? = null
    ): AIResponseResult {
        val calls = mutableListOf<ToolCall>()
        val jsonCalls = JSONArray()
        var firstSig: String? = null

        slots.forEach { (slot, accumulator) ->
            val name = accumulator.name
            if (name.isBlank()) return@forEach
            val id = accumulator.id.ifBlank { "call_${System.currentTimeMillis()}_$slot" }
            val arguments = accumulator.arguments.toString().ifBlank { "{}" }

            var sig = accumulator.thoughtSignature
            if (sig.isNullOrBlank() && signatureFallback) {
                sig = GEMINI_THOUGHT_SIGNATURE_BYPASS
            }
            if (firstSig == null && !sig.isNullOrBlank()) {
                firstSig = sig
            }

            val extra = accumulator.extraContent ?: if (!sig.isNullOrBlank()) {
                JSONObject().put("google", JSONObject().put("thought_signature", sig))
            } else null

            calls.add(
                ToolCall(
                    id = id,
                    name = name,
                    arguments = arguments,
                    thoughtSignature = sig,
                    extraContent = extra
                )
            )

            val callJson = JSONObject().apply {
                put("id", id)
                put("type", "function")
                put(
                    "function",
                    JSONObject().apply {
                        put("name", name)
                        put("arguments", arguments)
                    }
                )
                if (extra != null) {
                    put("extra_content", extra)
                }
                if (!sig.isNullOrBlank()) {
                    put("thought_signature", sig)
                }
            }
            jsonCalls.put(callJson)
        }

        if (calls.isEmpty()) {
            return AIResponseResult.Error("Модель запросила инструмент без имени.")
        }

        val assistantMessage = JSONObject().apply {
            put("role", "assistant")
            put("content", text)
            put("tool_calls", jsonCalls)
            if (!reasoningContent.isNullOrEmpty()) {
                put("reasoning_content", reasoningContent)
            }
            if (firstSig != null) {
                put(
                    "extra_content",
                    JSONObject().put("google", JSONObject().put("thought_signature", firstSig))
                )
            }
        }
        return AIResponseResult.ToolCallsResult(assistantMessage, calls)
    }

    /**
     * Проверяет историю сообщений перед отправкой.
     * Для Gemini гарантирует наличие thought_signature у всех tool_calls в роли assistant.
     */
    private fun sanitizeMessages(
        messages: JSONArray,
        isGemini: Boolean,
        forceSkipValidator: Boolean
    ): JSONArray {
        val output = JSONArray()
        for (i in 0 until messages.length()) {
            val msg = messages.optJSONObject(i) ?: continue
            val copy = JSONObject(msg.toString())

            if (isGemini && copy.optNullableString("role") == "assistant") {
                val toolCalls = copy.optJSONArray("tool_calls")
                if (toolCalls != null && toolCalls.length() > 0) {
                    var defaultSig: String? = null
                    for (j in 0 until toolCalls.length()) {
                        val tc = toolCalls.optJSONObject(j) ?: continue

                        val sig = if (forceSkipValidator) {
                            GEMINI_THOUGHT_SIGNATURE_BYPASS
                        } else {
                            tc.optJSONObject("extra_content")?.optJSONObject("google")?.optNullableString("thought_signature")?.takeIf { it.isNotBlank() }
                                ?: tc.optJSONObject("extra_content")?.optNullableString("thought_signature")?.takeIf { it.isNotBlank() }
                                ?: tc.optNullableString("thought_signature")?.takeIf { it.isNotBlank() }
                                ?: copy.optJSONObject("extra_content")?.optJSONObject("google")?.optNullableString("thought_signature")?.takeIf { it.isNotBlank() }
                                ?: copy.optJSONObject("extra_content")?.optNullableString("thought_signature")?.takeIf { it.isNotBlank() }
                                ?: GEMINI_THOUGHT_SIGNATURE_BYPASS
                        }

                        if (defaultSig == null) defaultSig = sig

                        val extra = tc.optJSONObject("extra_content") ?: JSONObject()
                        val google = extra.optJSONObject("google") ?: JSONObject()
                        google.put("thought_signature", sig)
                        extra.put("google", google)
                        tc.put("extra_content", extra)
                        tc.put("thought_signature", sig)
                    }

                    if (!copy.has("extra_content") && defaultSig != null) {
                        copy.put(
                            "extra_content",
                            JSONObject().put("google", JSONObject().put("thought_signature", defaultSig))
                        )
                    }
                }
            }

            output.put(copy)
        }
        return output
    }

    private fun httpError(
        code: Int,
        body: String,
        call: Call,
        sentOptionalParams: Set<String>
    ): AIResponseResult {
        if (call.isCanceled()) return AIResponseResult.Cancelled

        // Сырой JSON/HTML в плашку чата не вываливаем — только понятный текст.
        val details = try {
            val json = JSONObject(body)
            val errorObj = json.optJSONObject("error")
            val mainMsg = errorObj?.optNullableString("message")?.takeIf { it.isNotBlank() }
                ?: json.optNullableString("message")?.takeIf { it.isNotBlank() }
                ?: json.optNullableString("error")?.takeIf { it.isNotBlank() }
                ?: errorObj?.optNullableString("error")?.takeIf { it.isNotBlank() }
                ?: json.optNullableString("detail")?.takeIf { it.isNotBlank() }
                ?: errorObj?.optNullableString("detail")?.takeIf { it.isNotBlank() }
                ?: json.optJSONArray("detail")?.let { arr ->
                    val list = mutableListOf<String>()
                    for (i in 0 until arr.length()) {
                        val d = arr.optJSONObject(i)
                        val msg = d?.optNullableString("msg") ?: (arr.opt(i) as? String)
                        if (!msg.isNullOrBlank()) list.add(msg)
                    }
                    if (list.isNotEmpty()) list.joinToString("; ") else null
                }
                ?: json.optNullableString("error_description")?.takeIf { it.isNotBlank() }
                ?: json.optNullableString("msg")?.takeIf { it.isNotBlank() }

            val violations = errorObj?.optJSONArray("details")?.let { arr ->
                val list = mutableListOf<String>()
                for (i in 0 until arr.length()) {
                    val d = arr.optJSONObject(i) ?: continue
                    val fv = d.optJSONArray("fieldViolations") ?: continue
                    for (j in 0 until fv.length()) {
                        val v = fv.optJSONObject(j) ?: continue
                        val desc = v.optNullableString("description")?.takeIf { it.isNotBlank() }
                            ?: v.optNullableString("field")
                        if (!desc.isNullOrBlank()) list.add(desc)
                    }
                }
                if (list.isNotEmpty()) list.joinToString("; ") else null
            }

            when {
                mainMsg != null && violations != null && !mainMsg.contains(violations) -> "$mainMsg ($violations)"
                mainMsg != null -> mainMsg
                violations != null -> violations
                else -> null
            }
        } catch (_: Exception) {
            null
        } ?: body.take(300).trim().takeIf {
            it.isNotBlank() && !it.startsWith("<")
        }?.let { raw ->
            raw.removePrefix("{").removeSuffix("}").trim()
        }

        val unknownField = if (code == 400) extractUnknownField(body, sentOptionalParams) else null
        return classifyHttpError(
            code = code,
            details = details,
            host = call.request().url.host,
            unknownField = unknownField
        )
    }

    /** Если часть ответа уже показана, повторять запрос нельзя — текст задвоится. */
    private fun withEmitted(result: AIResponseResult, emitted: Boolean): AIResponseResult =
        if (emitted && result is AIResponseResult.Error) result.copy(retryable = false) else result

    /**
     * Достаёт из тела ошибки имя поля, которое провайдер не знает.
     * Возвращается только имя из белого списка, которое реально отправлялось в запросе.
     */
    private fun extractUnknownField(body: String, sentOptionalParams: Set<String>): String? {
        val text = try {
            val json = JSONObject(body)
            val error = json.optJSONObject("error") ?: json
            val message = error.optNullableString("message").orEmpty()
            val details = error.optJSONArray("details")
            buildString {
                append(message)
                if (details != null) {
                    for (i in 0 until details.length()) {
                        append(' ')
                        append(details.optJSONObject(i)?.toString().orEmpty())
                    }
                }
            }
        } catch (_: Exception) {
            body
        }
        return detectUnknownFieldFromText(text, sentOptionalParams)
    }

    /** Экспоненциальная пауза 3-6-10 с: лимит успевает отпустить, ожидание терпимое. */
    private fun backoffMillis(attempt: Int): Long =
        (3000L * (1L shl (attempt - 1))).coerceAtMost(10_000L)

    private fun resolveChatEndpoint(baseUrl: String, reverseProxyUrl: String): String {
        val target = com.runner.app.util.UrlSanitizer.sanitizeBaseUrl(
            if (reverseProxyUrl.isNotBlank()) reverseProxyUrl else baseUrl
        )
        return when {
            target.endsWith("/chat/completions") -> target
            target.endsWith("/v1") -> "$target/chat/completions"
            else -> "$target/chat/completions"
        }
    }

    /**
     * Проверка связи с прокси или официальным API.
     * Замеряет пинг (latency), проверяет доступность хоста и валидность ключа/заголовков.
     */
    suspend fun testConnection(
        targetUrl: String,
        apiKey: String = "",
        timeoutSeconds: Int = 15,
        customHeaders: String = "",
        providerName: String = ""
    ): ConnectionTestResult = withContext(Dispatchers.IO) {
        val cleanUrl = com.runner.app.util.UrlSanitizer.sanitizeBaseUrl(targetUrl)
        val providerPrefix = if (providerName.isNotBlank()) "$providerName: " else ""
        if (cleanUrl.isBlank()) {
            return@withContext ConnectionTestResult(
                isSuccess = false,
                isReachable = false,
                statusCode = null,
                latencyMs = 0L,
                message = "${providerPrefix}Адрес не указан",
                targetEndpoint = ""
            )
        }

        val testEndpoint = resolvePingUrl(cleanUrl)
        val parsedHeaders = parseCustomHeaders(customHeaders)
        val timeoutSec = timeoutSeconds.coerceIn(5, 120).toLong()

        val testClient = client.newBuilder()
            .connectTimeout(timeoutSec.coerceAtMost(30), TimeUnit.SECONDS)
            .readTimeout(timeoutSec, TimeUnit.SECONDS)
            .writeTimeout(timeoutSec.coerceAtMost(30), TimeUnit.SECONDS)
            .callTimeout(timeoutSec + 2, TimeUnit.SECONDS)
            .build()

        val request = try {
            val requestBuilder = Request.Builder()
                .url(testEndpoint)
                .get()
                .addHeader("Accept", "application/json")
                .addHeader("HTTP-Referer", "https://github.com/ArddMobX/Runner")
                .addHeader("X-Title", "Runner Android Agent")

            if (apiKey.isNotBlank()) {
                val cleanKey = apiKey.trim()
                requestBuilder.addHeader("Authorization", "Bearer $cleanKey")
                if (extractHost(testEndpoint) == "generativelanguage.googleapis.com") {
                    requestBuilder.addHeader("x-goog-api-key", cleanKey)
                }
            }

            parsedHeaders.forEach { (name, value) ->
                requestBuilder.header(name, value)
            }
            requestBuilder.build()
        } catch (e: Exception) {
            return@withContext ConnectionTestResult(
                isSuccess = false,
                isReachable = false,
                statusCode = null,
                latencyMs = 0L,
                message = "${providerPrefix}Некорректный адрес URL или заголовки: ${e.localizedMessage ?: cleanUrl}",
                targetEndpoint = testEndpoint
            )
        }

        val call = testClient.newCall(request)
        val cancelHandle = coroutineContext[Job]?.invokeOnCompletion { cause ->
            if (cause != null) call.cancel()
        }
        val start = System.currentTimeMillis()
        try {
            call.execute().use { response ->
                val latencyMs = System.currentTimeMillis() - start
                val code = response.code

                when {
                    code in 200..299 -> ConnectionTestResult(
                        isSuccess = true,
                        isReachable = true,
                        statusCode = code,
                        latencyMs = latencyMs,
                        message = "${providerPrefix}Подключено · $code OK · $latencyMs мс",
                        targetEndpoint = testEndpoint
                    )
                    code == 401 -> ConnectionTestResult(
                        isSuccess = false,
                        isReachable = true,
                        statusCode = code,
                        latencyMs = latencyMs,
                        message = "${providerPrefix}Сервер ответил ($latencyMs мс) · 401 Unauthorized (проверьте ключ)",
                        targetEndpoint = testEndpoint
                    )
                    code == 402 -> ConnectionTestResult(
                        isSuccess = false,
                        isReachable = true,
                        statusCode = code,
                        latencyMs = latencyMs,
                        message = "${providerPrefix}Сервер ответил ($latencyMs мс) · 402 Недостаточно средств на балансе",
                        targetEndpoint = testEndpoint
                    )
                    code == 403 -> ConnectionTestResult(
                        isSuccess = false,
                        isReachable = true,
                        statusCode = code,
                        latencyMs = latencyMs,
                        message = "${providerPrefix}Сервер ответил ($latencyMs мс) · 403 Forbidden (доступ ограничен)",
                        targetEndpoint = testEndpoint
                    )
                    code == 404 -> ConnectionTestResult(
                        isSuccess = false,
                        isReachable = true,
                        statusCode = code,
                        latencyMs = latencyMs,
                        message = "${providerPrefix}Сервер отвечает, но эндпоинт не найден (404). " +
                                "Проверьте Base URL: возможно, он указан без /v1 или с лишним путём.",
                        targetEndpoint = testEndpoint
                    )
                    code == 405 -> ConnectionTestResult(
                        isSuccess = false,
                        isReachable = true,
                        statusCode = code,
                        latencyMs = latencyMs,
                        message = "${providerPrefix}Сервер доступен, но метод не поддерживается (405). " +
                                "Проверьте адрес: он должен указывать на OpenAI-совместимый эндпоинт.",
                        targetEndpoint = testEndpoint
                    )
                    code == 429 -> ConnectionTestResult(
                        isSuccess = false,
                        isReachable = true,
                        statusCode = code,
                        latencyMs = latencyMs,
                        message = "${providerPrefix}Сервер ответил ($latencyMs мс) · 429 Превышен лимит запросов",
                        targetEndpoint = testEndpoint
                    )
                    code == 451 -> ConnectionTestResult(
                        isSuccess = false,
                        isReachable = true,
                        statusCode = code,
                        latencyMs = latencyMs,
                        message = "${providerPrefix}Сервер ответил ($latencyMs мс) · 451 Доступ ограничен по региону",
                        targetEndpoint = testEndpoint
                    )
                    else -> ConnectionTestResult(
                        isSuccess = false,
                        isReachable = true,
                        statusCode = code,
                        latencyMs = latencyMs,
                        message = "${providerPrefix}Сервер ответил ошибкой · HTTP $code · $latencyMs мс",
                        targetEndpoint = testEndpoint
                    )
                }
            }
        } catch (e: CancellationException) {
            call.cancel()
            throw e
        } catch (e: java.net.SocketTimeoutException) {
            val latencyMs = System.currentTimeMillis() - start
            ConnectionTestResult(
                isSuccess = false,
                isReachable = false,
                statusCode = null,
                latencyMs = latencyMs,
                message = "${providerPrefix}Таймаут ($timeoutSeconds с), сервер не ответил",
                targetEndpoint = testEndpoint
            )
        } catch (e: java.net.UnknownHostException) {
            ConnectionTestResult(
                isSuccess = false,
                isReachable = false,
                statusCode = null,
                latencyMs = 0L,
                message = "Ошибка DNS: хост не найден (${e.message ?: "недоступен"})",
                targetEndpoint = testEndpoint
            )
        } catch (e: java.net.ConnectException) {
            ConnectionTestResult(
                isSuccess = false,
                isReachable = false,
                statusCode = null,
                latencyMs = 0L,
                message = "Соединение отклонено сервером",
                targetEndpoint = testEndpoint
            )
        } catch (e: javax.net.ssl.SSLException) {
            ConnectionTestResult(
                isSuccess = false,
                isReachable = false,
                statusCode = null,
                latencyMs = 0L,
                message = "Ошибка SSL/TLS: ${e.localizedMessage ?: "не доверенный сертификат"}",
                targetEndpoint = testEndpoint
            )
        } catch (e: IOException) {
            val latencyMs = System.currentTimeMillis() - start
            ConnectionTestResult(
                isSuccess = false,
                isReachable = false,
                statusCode = null,
                latencyMs = latencyMs,
                message = "Сетевая ошибка: ${e.localizedMessage ?: "сбой соединения"}",
                targetEndpoint = testEndpoint
            )
        } catch (e: Exception) {
            ConnectionTestResult(
                isSuccess = false,
                isReachable = false,
                statusCode = null,
                latencyMs = 0L,
                message = "Ошибка: ${e.localizedMessage ?: "неизвестный сбой"}",
                targetEndpoint = testEndpoint
            )
        } finally {
            cancelHandle?.dispose()
        }
    }

    private fun captureRateLimits(response: Response, providerBaseHost: String?) {
        val headers = response.headers
        fun intHeader(name: String): Int? =
            headers[name]?.trim()?.toIntOrNull()

        val info = RateLimitInfo(
            remainingRequests = intHeader("x-ratelimit-remaining-requests"),
            limitRequests = intHeader("x-ratelimit-limit-requests"),
            resetRequests = headers["x-ratelimit-reset-requests"]?.trim(),
            remainingTokens = intHeader("x-ratelimit-remaining-tokens"),
            limitTokens = intHeader("x-ratelimit-limit-tokens"),
            resetTokens = headers["x-ratelimit-reset-tokens"]?.trim()
        )
        if (info.hasAny) {
            val requestHost = response.request.url.host
            RateLimitStore.update(requestHost, info)
            if (!providerBaseHost.isNullOrBlank() && !providerBaseHost.equals(requestHost, ignoreCase = true)) {
                RateLimitStore.update(providerBaseHost, info)
            }
        }
    }

    /**
     * Ожидание между ретраями с проверкой «пользователь нажал Стоп».
     * Обычный delay здесь не годится: Stop не отменяет корутину (иначе не
     * сохранить уже сгенерированный текст), значит проверять надо самим.
     */
    private suspend fun waitForRetry(totalMs: Long, shouldStop: () -> Boolean): Boolean {
        var waited = 0L
        while (waited < totalMs) {
            if (shouldStop()) return true
            val tick = minOf(RETRY_TICK_MS, totalMs - waited)
            delay(tick)
            waited += tick
        }
        return shouldStop()
    }

    /**
     * Отбрасывает старые сообщения за O(N), чтобы запрос влезал в бюджет символов.
     *
     * Три правила, без которых провайдер отвечает 400:
     * 1. system-сообщение остаётся всегда;
     * 2. хвост диалога (от последнего сообщения пользователя) сохраняется целиком;
     * 3. assistant с tool_calls и его ответы tool — неделимая группа.
     */
    private fun trimMessagesToBudget(messages: JSONArray, budgetChars: Int): JSONArray {
        if (budgetChars <= 0 || messages.length() <= 1) return messages

        val all = (0 until messages.length()).mapNotNull { messages.optJSONObject(it) }
        if (all.size != messages.length()) return messages

        // Предвычисляем длину каждого элемента один раз: избегаем O(N^2) пересериализации
        // и считаем именно символы (budgetChars), а не UTF-8 байты (не режем кириллицу вдвое).
        val itemChars = IntArray(all.size) { all[it].toString().length }
        var totalChars = 2 + itemChars.sum() + (all.size - 1).coerceAtLeast(0)
        if (totalChars <= budgetChars) return messages

        // Последнее сообщение пользователя: всё от него и до конца — неприкосновенно.
        var tailStart = -1
        for (i in all.indices.reversed()) {
            if (all[i].optNullableString("role") == "user") {
                tailStart = i
                break
            }
        }

        // Границы групп tool_calls: ведущий assistant и следующие за ним tool-ответы.
        data class ToolGroup(val start: Int, val endInclusive: Int)

        val groups = mutableListOf<ToolGroup>()
        val inGroup = BooleanArray(all.size)
        var i = 0
        while (i < all.size) {
            val role = all[i].optNullableString("role")
            if (role == "assistant" && (all[i].optJSONArray("tool_calls")?.length() ?: 0) > 0) {
                var j = i + 1
                while (j < all.size && all[j].optNullableString("role") == "tool") j++
                if (j > i + 1) {
                    groups += ToolGroup(i, j - 1)
                    for (k in i until j) inGroup[k] = true
                }
                i = j
            } else {
                i++
            }
        }

        val keep = BooleanArray(all.size) { true }
        var keptCount = all.size
        var anyDropped = false

        fun dropIndex(idx: Int) {
            if (!keep[idx]) return
            keep[idx] = false
            totalChars -= itemChars[idx] + if (keptCount > 1) 1 else 0
            keptCount--
            anyDropped = true
        }

        i = 0
        while (i < all.size && totalChars > budgetChars) {
            val protected = i == 0 || (tailStart >= 0 && i >= tailStart)
            if (protected) {
                i++
                continue
            }

            if (inGroup[i]) {
                val group = groups.firstOrNull { it.start == i }
                if (group != null) {
                    val canDrop = (group.start..group.endInclusive).none {
                        it == 0 || (tailStart >= 0 && it >= tailStart)
                    }
                    if (canDrop) {
                        for (k in group.start..group.endInclusive) dropIndex(k)
                    }
                    i = group.endInclusive + 1
                    continue
                }
                // tool-ответ без своего assistant — битая история, убираем.
                dropIndex(i)
                i++
                continue
            }

            dropIndex(i)
            i++
        }

        if (!anyDropped) return messages

        return JSONArray().apply {
            for (idx in all.indices) {
                if (keep[idx]) put(all[idx])
            }
        }
    }

    private class ToolCallAccumulator {
        var id: String = ""
        var name: String = ""
        val arguments = StringBuilder()
        var extraContent: JSONObject? = null
        var thoughtSignature: String? = null
    }

    internal companion object {
        const val MAX_RETRIES = 3
        const val DONE_SENTINEL = "[DONE]"
        const val GEMINI_THOUGHT_SIGNATURE_BYPASS = "skip_thought_signature_validator"

        /** Сколько необязательных полей разрешено убрать в одном запросе. */
        const val MAX_OPTIONAL_STRIPS = 4

        /** Сколько раз пробуем stream_options у незнакомого провайдера. */
        const val STREAM_USAGE_PROBE_ATTEMPTS = 2

        /** Лимит буфера для fallback-разбора non-SSE JSON-ответа. */
        const val NON_SSE_FALLBACK_MAX_CHARS = 512_000

        /**
         * Поля, которые разрешено убирать по жалобе провайдера. Белый список:
         * более специфичные имена стоят первыми (max_completion_tokens до max_tokens,
         * reasoning_effort до reasoning).
         */
        val OPTIONAL_PARAM_NAMES = listOf(
            "stream_options",
            "max_completion_tokens",
            "max_tokens",
            "reasoning_effort",
            "reasoning",
            "temperature",
            "thinking",
            "tool_choice",
            "tools"
        )

        /** Хосты, про которые точно известно, что stream_options они принимают. */
        val KNOWN_STREAM_USAGE_HOSTS = listOf(
            "openai.com", "groq.com", "openrouter.ai", "deepseek.com", "googleapis.com"
        )

        const val DEFAULT_CONTEXT_BUDGET_CHARS = 600_000
        const val RETRY_TICK_MS = 120L

        internal fun isKnownStreamUsageHost(endpoint: String): Boolean {
            val host = extractHost(endpoint)
            if (host.isEmpty()) return false
            return KNOWN_STREAM_USAGE_HOSTS.any { known ->
                host == known || host.endsWith(".$known")
            }
        }

        internal fun nextToolCallSlotKey(existingKeys: Collection<Int>): Int =
            (existingKeys.maxOrNull() ?: -1) + 1

        internal fun computeStreamingWatchdogTimeoutMs(responseTimeoutSeconds: Int): Long {
            val userMs = responseTimeoutSeconds.coerceIn(10, 300).toLong() * 1000L
            return maxOf(DEFAULT_STREAM_STALL_TIMEOUT_MS, userMs)
        }

        internal fun resolvePingUrl(target: String): String {
            val trimmed = target.trim().trimEnd('/')
            return when {
                trimmed.endsWith("/chat/completions") ->
                    trimmed.removeSuffix("/chat/completions") + "/models"
                trimmed.endsWith("/models") -> trimmed
                trimmed.endsWith("/v1") -> "$trimmed/models"
                trimmed.endsWith("/openai") -> "$trimmed/models"
                !trimmed.contains("/models") -> "$trimmed/models"
                else -> trimmed
            }
        }

        internal fun detectUnknownFieldFromText(
            text: String,
            sentOptionalParams: Set<String>
        ): String? {
            if (sentOptionalParams.isEmpty()) return null
            val lowered = text.lowercase()

            if ("tools" in sentOptionalParams && (
                        lowered.contains("tools are not supported") ||
                                lowered.contains("tool calling is not supported") ||
                                lowered.contains("function calling is not supported") ||
                                (lowered.contains("tools") && lowered.contains("multimodal"))
                        )
            ) {
                return "tools"
            }

            if ("reasoning_effort" in sentOptionalParams && lowered.contains("reasoning_effort") &&
                (lowered.contains("not supported") || lowered.contains("unsupported") ||
                        lowered.contains("invalid") || lowered.contains("unknown"))
            ) {
                return "reasoning_effort"
            }
            if ("reasoning" in sentOptionalParams && lowered.contains("reasoning") &&
                (lowered.contains("not supported") || lowered.contains("unsupported") ||
                        lowered.contains("invalid") || lowered.contains("unknown"))
            ) {
                return "reasoning"
            }

            val isParamError = lowered.contains("unknown name") ||
                    lowered.contains("unknown parameter") ||
                    lowered.contains("unsupported parameter") ||
                    lowered.contains("unsupported value") ||
                    lowered.contains("invalid parameter") ||
                    lowered.contains("is not supported") ||
                    lowered.contains("unrecognized") ||
                    lowered.contains("unexpected")

            if (!isParamError) return null

            return OPTIONAL_PARAM_NAMES.firstOrNull { param ->
                param in sentOptionalParams && lowered.contains(param)
            }
        }

        internal data class RequestReasoningDecision(
            val deepSeekThinkingEnabled: Boolean? = null,
            val openRouterEffort: String? = null,
            val reasoningEffort: String? = null
        )

        private val OPENAI_O_SERIES_PREFIX = Regex("""^o[134](?:$|[-._])""")

        /**
         * Модель понимает reasoning_effort. Проверка по имени, а не по провайдеру:
         * один и тот же GPT приходит и с OpenAI, и через прокси/агрегаторы.
         */
        internal fun supportsReasoningEffort(model: String): Boolean {
            val name = model.substringAfterLast('/').lowercase()
            return name.startsWith("gpt-5") ||
                name.startsWith("gpt-6") ||
                OPENAI_O_SERIES_PREFIX.containsMatchIn(name)
        }

        /**
         * Модель через OpenRouter умеет reasoning. Список по именам, а не по
         * провайдеру: OpenRouter отдаёт десятки чужих моделей.
         */
        internal fun supportsOpenRouterReasoning(model: String): Boolean {
            val name = model.substringAfterLast('/').lowercase()
            return name.startsWith("deepseek") ||
                name.startsWith("gpt-5") ||
                name.startsWith("gpt-6") ||
                OPENAI_O_SERIES_PREFIX.containsMatchIn(name) ||
                name.startsWith("gemini-2.5") ||
                name.startsWith("gemini-3") ||
                name.startsWith("gemini-4") ||
                name.startsWith("claude") ||
                name.startsWith("grok") ||
                name.startsWith("qwen3") ||
                name.startsWith("qwen4") ||
                name.startsWith("glm-4.5") ||
                name.startsWith("glm-4.6") ||
                name.startsWith("glm-4.7") ||
                name.startsWith("glm-5") ||
                name.startsWith("nemotron-3") ||
                name.startsWith("kimi-k2") ||
                name.startsWith("kimi-k3") ||
                name.startsWith("minimax-m")
        }

        /**
         * Чистая функция расчёта полей reasoning / reasoning_effort / thinking для тела запроса.
         */
        internal fun resolveRequestReasoning(
            isDirectOfficialDeepSeek: Boolean,
            allowThinking: Boolean,
            isOpenRouterHost: Boolean,
            allowReasoning: Boolean,
            allowReasoningEffort: Boolean,
            endpointHost: String,
            effectiveModel: String,
            allowTools: Boolean,
            liveMode: Boolean,
            forceThinking: Boolean,
            reasoningEffort: ReasoningEffort?
        ): RequestReasoningDecision {
            val deepSeekThinking = if (isDirectOfficialDeepSeek && allowThinking) {
                forceThinking || (reasoningEffort != null && reasoningEffort != ReasoningEffort.NONE)
            } else {
                null
            }

            val normalized = effectiveModel.substringAfterLast('/').lowercase()
            val isOpenAiGpt6WithTools = endpointHost == "api.openai.com" &&
                allowTools &&
                (normalized == "gpt-6-luna" || normalized == "gpt-6-sol")

            if (reasoningEffort != null) {
                return when {
                    isOpenRouterHost -> {
                        if (allowReasoning) {
                            RequestReasoningDecision(
                                deepSeekThinkingEnabled = deepSeekThinking,
                                openRouterEffort = reasoningEffort.apiValue
                            )
                        } else {
                            RequestReasoningDecision(deepSeekThinkingEnabled = deepSeekThinking)
                        }
                    }
                    allowReasoningEffort && !isDirectOfficialDeepSeek -> {
                        val effectiveApiValue = if (isOpenAiGpt6WithTools) {
                            ReasoningEffort.NONE.apiValue
                        } else {
                            reasoningEffort.apiValue
                        }
                        RequestReasoningDecision(
                            deepSeekThinkingEnabled = deepSeekThinking,
                            reasoningEffort = effectiveApiValue
                        )
                    }
                    else -> RequestReasoningDecision(deepSeekThinkingEnabled = deepSeekThinking)
                }
            }

            return when {
                forceThinking -> when {
                    isOpenRouterHost -> if (allowReasoning && supportsOpenRouterReasoning(effectiveModel)) {
                        RequestReasoningDecision(
                            deepSeekThinkingEnabled = deepSeekThinking,
                            openRouterEffort = "high"
                        )
                    } else {
                        RequestReasoningDecision(deepSeekThinkingEnabled = deepSeekThinking)
                    }
                    allowReasoningEffort && supportsReasoningEffort(effectiveModel) -> {
                        val effort = if (isOpenAiGpt6WithTools) "none" else "high"
                        RequestReasoningDecision(
                            deepSeekThinkingEnabled = deepSeekThinking,
                            reasoningEffort = effort
                        )
                    }
                    else -> RequestReasoningDecision(deepSeekThinkingEnabled = deepSeekThinking)
                }
                liveMode -> when {
                    isOpenRouterHost -> if (allowReasoning && supportsOpenRouterReasoning(effectiveModel)) {
                        RequestReasoningDecision(
                            deepSeekThinkingEnabled = deepSeekThinking,
                            openRouterEffort = "low"
                        )
                    } else {
                        RequestReasoningDecision(deepSeekThinkingEnabled = deepSeekThinking)
                    }
                    allowReasoningEffort && supportsReasoningEffort(effectiveModel) -> {
                        val effort = if (isOpenAiGpt6WithTools) "none" else "low"
                        RequestReasoningDecision(
                            deepSeekThinkingEnabled = deepSeekThinking,
                            reasoningEffort = effort
                        )
                    }
                    else -> RequestReasoningDecision(deepSeekThinkingEnabled = deepSeekThinking)
                }
                isOpenAiGpt6WithTools && allowReasoningEffort -> RequestReasoningDecision(
                    deepSeekThinkingEnabled = deepSeekThinking,
                    reasoningEffort = "none"
                )
                else -> RequestReasoningDecision(deepSeekThinkingEnabled = deepSeekThinking)
            }
        }

        internal fun classifyHttpError(
            code: Int,
            details: String?,
            host: String,
            unknownField: String?
        ): AIResponseResult.Error {
            val isThoughtSignatureError =
                details?.contains("thought_signature", ignoreCase = true) == true

            return when {
                unknownField != null -> AIResponseResult.Error(
                    "Провайдер не принял параметр «$unknownField». " +
                            "Запрос повторяется без него.",
                    optionalField = unknownField,
                    retryable = true
                )
                // Gemini возвращает HTTP 400 при ошибке thought_signature — проверяем ДО общей ветки 400!
                isThoughtSignatureError -> AIResponseResult.Error(
                    "Ошибка API ($code): ${details ?: "требуется thought_signature"}",
                    retryable = true
                )
                code == 400 && details != null ->
                    AIResponseResult.Error("Ошибка запроса (400): $details")
                code == 400 ->
                    AIResponseResult.Error("Ошибка запроса (400): сервер не принял параметры вызова. Проверьте модель и параметры в настройках.")
                code == 401 -> AIResponseResult.Error(
                    "Ошибка авторизации (401) на $host. Ключ не подошёл этому провайдеру: " +
                            "проверьте, что он от этого сервиса, а не от другого, и что Base URL совпадает."
                )
                code == 402 -> AIResponseResult.Error(
                    if (details != null) "Недостаточно средств (402): $details"
                    else "Недостаточно средств на балансе провайдера (402)."
                )
                code == 403 -> AIResponseResult.Error(
                    if (details != null) "Доступ запрещён (403): $details"
                    else "Доступ запрещён (403). Проверьте ключ и доступ модели."
                )
                code == 404 -> AIResponseResult.Error(
                    if (details != null) "Эндпоинт или модель не найдены (404): $details"
                    else "Эндпоинт или модель не найдены (404). Проверьте Base URL и модель."
                )
                code == 429 -> AIResponseResult.Error(
                    if (details != null) "Лимит запросов (429): $details"
                    else "Лимит запросов исчерпан (429). Повторите запрос через несколько секунд.",
                    retryable = true
                )
                code in 500..599 -> AIResponseResult.Error(
                    if (details != null) "Ошибка сервера ($code): $details"
                    else "Ошибка сервера ($code): временный сбой провайдера, повторите запрос",
                    retryable = true
                )
                details != null -> AIResponseResult.Error("Ошибка API ($code): $details")
                else -> AIResponseResult.Error("Ошибка сервера ($code): ответ не распознан, повторите запрос")
            }
        }
    }
}

/**
 * Сторож SSE-потока.
 *
 * OkHttp-таймаут следит за байтами, а не за смыслом: провайдер может слать
 * пустые keepalive-строки, и соединение будет жить вечно, пока модель молчит.
 * Использует монотонный `System.nanoTime()` и уважает пользовательский `responseTimeoutSeconds`.
 */
private class StreamWatchdog(private val stallTimeoutMs: Long) {

    @Volatile
    private var lastEventNanos: Long = System.nanoTime()

    @Volatile
    var stalled: Boolean = false
        private set

    private var future: ScheduledFuture<*>? = null

    fun start(call: Call) {
        val timeoutNanos = TimeUnit.MILLISECONDS.toNanos(stallTimeoutMs)
        future = streamWatchdogExecutor.scheduleWithFixedDelay(
            {
                if (System.nanoTime() - lastEventNanos > timeoutNanos) {
                    stalled = true
                    call.cancel()
                }
            },
            STREAM_WATCHDOG_TICK_MS,
            STREAM_WATCHDOG_TICK_MS,
            TimeUnit.MILLISECONDS
        )
    }

    /** Отметить полезное событие: поток жив, даже если это только reasoning. */
    fun touch() {
        lastEventNanos = System.nanoTime()
    }

    fun stop() {
        future?.cancel(false)
        future = null
    }
}

private const val DEFAULT_STREAM_STALL_TIMEOUT_MS = 90_000L
private const val STREAM_WATCHDOG_TICK_MS = 5_000L

private val streamWatchdogExecutor = Executors.newSingleThreadScheduledExecutor { runnable ->
    Thread(runnable, "runner-stream-watchdog").apply { isDaemon = true }
}
