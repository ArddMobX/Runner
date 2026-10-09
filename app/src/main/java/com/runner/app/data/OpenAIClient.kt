package com.runner.app.data

import com.runner.app.tools.ToolDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.ConnectionPool
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

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
                if (key.isNotEmpty()) {
                    result[key] = json.optString(key, "")
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

    private var reasoningStartedAt = 0L
    private var reasoningEndedAt = 0L
    private val reasoningBuffer = StringBuilder()

    private var firstTokenAt = 0L
    private var lastTokenAt = 0L

    fun noteReasoning(delta: String, now: Long) {
        if (reasoningStartedAt == 0L) reasoningStartedAt = now
        reasoningEndedAt = now
        reasoningBuffer.append(delta)
    }

    fun noteContent(now: Long) {
        if (firstTokenAt == 0L) firstTokenAt = now
        lastTokenAt = now
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

    /** Скорость генерации: выходные токены за время после первого токена. */
    val tokensPerSecond: Double?
        get() {
            val tokens = completionTokens ?: return null
            if (firstTokenAt == 0L || lastTokenAt <= firstTokenAt) return null
            val seconds = (lastTokenAt - firstTokenAt) / 1000.0
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
    }

    /**
     * Поддерживает ли эндпоинт stream_options.include_usage.
     *
     * У известных хостов это проверено один раз. У незнакомых (свои прокси и
     * шлюзы) выясняем опытом: пока попытки не исчерпаны, поле отправляется,
     * а после двух жалоб на неизвестный параметр больше не шлётся. Отдельный
     * «пробный» запрос для этого не нужен и ничего не доказывает: он ушёл бы
     * с несуществующей моделью и упал бы по другой причине.
     */
    private fun streamUsageSupported(endpoint: String): Boolean {
        if (endpoint.isBlank()) return false
        if (KNOWN_STREAM_USAGE_HOSTS.any { endpoint.contains(it) }) return true
        return (streamUsageFailures[endpoint] ?: 0) < STREAM_USAGE_PROBE_ATTEMPTS
    }

    /** Провайдер отказался от stream_options: считаем отказы по эндпоинту. */
    private fun noteStreamUsageRejected(endpoint: String) {
        if (endpoint.isNotBlank()) {
            streamUsageFailures[endpoint] = (streamUsageFailures[endpoint] ?: 0) + 1
        }
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

    @Volatile
    private var activeCall: Call? = null

    /** Сколько раз эндпоинт отказался от stream_options. Живёт в памяти. */
    private val streamUsageFailures = ConcurrentHashMap<String, Int>()

    /** Обрывает текущий запрос. Стриминг прекращается сразу. */
    fun cancelActive() {
        activeCall?.cancel()
        activeCall = null
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
        streaming: Boolean = true,
        listener: StreamListener? = null,
        metrics: GenerationMetrics? = null,
        /** false — планировочный запрос без тулов: модель отвечает только текстом. */
        withTools: Boolean = true,
        /**
         * Какие именно инструменты объявить в запросе. `null` — все.
         *
         * Схемы инструментов уходят в КАЖДОМ запросе и занимают заметную часть
         * лимита токенов, поэтому набор можно сузить: объявлять только нужное,
         * а остальное подключать инструментом `enable_tools`.
         */
        toolNames: Set<String>? = null,
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
        shouldStop: () -> Boolean = { false }
    ): AIResponseResult = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext AIResponseResult.Error(
                "API ключ не задан. Открой настройки и укажи ключ провайдера."
            )
        }

        val effectiveBaseUrl = baseUrl.ifBlank { "https://openrouter.ai/api/v1" }
        val effectiveModel = modelName.ifBlank { "openai/gpt-oss-120b" }
        val endpoint = resolveChatEndpoint(effectiveBaseUrl, reverseProxyUrl)

        val isGemini = providerKind == ProviderKind.GEMINI

        // Параметры, которые провайдер может не знать. При жалобе на неизвестное
        // поле убираем его и повторяем сразу, не тратя попытку ретрая.
        var allowStreamUsage = streamUsageSupported(endpoint)
        var allowTemperature = true
        var allowThinking = providerKind == ProviderKind.DEEPSEEK && officialDeepSeekHost
        var optionalStripped = 0

        var forceSkipValidator = false
        /**
         * Подставлять ли заглушку thought_signature, когда Google её не прислал.
         *
         * Раньше заглушка подставлялась всем Gemini-запросам заранее, то есть
         * недокументированная строка уходила в API всегда. Теперь только после
         * того, как Google сам пожаловался на отсутствующую подпись.
         */
        var signatureFallback = false
        var attempt = 0
        while (attempt <= MAX_RETRIES) {
            val sanitizedMessages = sanitizeMessages(messages, isGemini, forceSkipValidator)
            // Старые реплики отбрасываем до отправки: иначе длинная сессия
            // упирается в лимит модели и падает с 400 без пути восстановления.
            val contextMessages = trimMessagesToBudget(sanitizedMessages, contextBudgetChars)

            fun buildBody(): JSONObject = JSONObject().apply {
                put("model", effectiveModel)
                put("messages", contextMessages)
                if (withTools) {
                    val tools = ToolDispatcher.getToolsJson(toolNames)
                    // Пустой набор означает «инструменты не нужны». Пустой массив
                    // в поле tools часть провайдеров отвергает как ошибку схемы,
                    // поэтому поле просто не отправляем.
                    if (tools.length() > 0) {
                        put("tools", tools)
                        put("tool_choice", "auto")
                    }
                }
                if (allowTemperature) put("temperature", temperature)
                if (streaming) {
                    put("stream", true)
                    if (allowStreamUsage) {
                        put("stream_options", JSONObject().put("include_usage", true))
                    }
                }
                // У DeepSeek V4 thinking mode включён по умолчанию, для агентского
                // цикла это лишняя латентность. Но поле не из схемы OpenAI, поэтому
                // шлём его только на официальный хост, а не на любой похожий прокси.
                if (allowThinking) {
                    put("thinking", JSONObject().put("type", "disabled"))
                }
            }

            val requestBody = buildBody()

            val requestBuilder = Request.Builder()
                .url(endpoint)
                .addHeader("Authorization", "Bearer ${apiKey.trim()}")
                .addHeader("Content-Type", "application/json")
                .addHeader("HTTP-Referer", "https://github.com/ArddMobX/Runner")
                .addHeader("X-Title", "Runner Android Agent")

            parseCustomHeaders(customHeaders).forEach { (name, value) ->
                requestBuilder.header(name, value)
            }

            val request = requestBuilder
                .post(requestBody.toString().toRequestBody(jsonMediaType))
                .build()

            val call = clientWithTimeout(connectTimeoutSeconds, responseTimeoutSeconds).newCall(request)
            activeCall = call
            try {
                val result = if (streaming) {
                    // Заглушка thought_signature — недокументированная строка Google.
                    // На первой попытке её нет; включается после явной жалобы на
                    // подпись, а для Gemini ещё и на любом повторе: раньше она уходила
                    // всегда, и отказываться от проверенного поведения сразу нельзя.
                    val fallback = signatureFallback || (isGemini && attempt > 0)
                    executeStreaming(call, listener, fallback, metrics)
                } else {
                    val fallback = signatureFallback || (isGemini && attempt > 0)
                    executeBlocking(call, fallback, metrics)
                }

                if (result is AIResponseResult.Error && result.optionalField != null &&
                    optionalStripped < MAX_OPTIONAL_STRIPS
                ) {
                    // Провайдер не знает такое поле. Убираем его и повторяем сразу:
                    // это не ошибка пользователя и не повод расходовать попытку ретрая.
                    when (result.optionalField) {
                        "stream_options" -> {
                            allowStreamUsage = false
                            noteStreamUsageRejected(endpoint)
                        }
                        "temperature" -> allowTemperature = false
                        "thinking" -> allowThinking = false
                        else -> Unit
                    }
                    optionalStripped++
                    continue
                }

                if (result is AIResponseResult.Error && result.retryable && attempt < MAX_RETRIES) {
                    if (result.message.contains("thought_signature", ignoreCase = true)) {
                        forceSkipValidator = true
                        // Google требует подпись, а модель её не прислала: только
                        // теперь подставляем заглушку, а не заранее всем запросам.
                        signatureFallback = true
                    }
                    attempt++
                    val pause = backoffMillis(attempt)
                    // 429 (лимит запросов): отдельный индикатор ожидания снятия ограничения.
                    val retryText = if (result.message.contains("429")) {
                        "Лимит запросов, ожидаю снятия ограничения"
                    } else {
                        result.message
                    }
                    listener?.onRetry(retryText, pause)
                    // Стоп во время ожидания должен прервать цикл, а не запустить
                    // запрос заново уже после нажатия кнопки.
                    if (waitForRetry(pause, shouldStop)) return@withContext AIResponseResult.Cancelled
                    continue
                }
                return@withContext result
            } catch (e: IOException) {
                if (call.isCanceled()) return@withContext AIResponseResult.Cancelled
                if (shouldStop()) return@withContext AIResponseResult.Cancelled
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
                // Битый JSON от сервера — сырой текст исключения с куском
                // ответа в чат не тащим, только понятное сообщение.
                return@withContext AIResponseResult.Error(
                    "Ответ сервера не распознан (битый JSON). Повторите запрос."
                )
            } catch (e: Exception) {
                return@withContext AIResponseResult.Error(
                    "Ошибка обработки ответа: ${e.localizedMessage}"
                )
            } finally {
                activeCall = null
            }
        }

        AIResponseResult.Error("Превышено количество попыток запроса к API.")
    }

    /** Разбирает SSE-поток: content уходит в UI по мере поступления, tool_calls склеиваются по index/id. */
    private fun executeStreaming(
        call: Call,
        listener: StreamListener?,
        signatureFallback: Boolean,
        metrics: GenerationMetrics?
    ): AIResponseResult {
        val content = StringBuilder()
        val toolCallSlots = sortedMapOf<Int, ToolCallAccumulator>()
        var emitted = false

        call.execute().use { response ->
            if (!response.isSuccessful) {
                val errorBody = response.body?.string().orEmpty()
                return withEmitted(httpError(response.code, errorBody, call), emitted)
            }

            val responseBody = response.body
                ?: return AIResponseResult.Error("Пустой ответ от сервера.")

            val source = responseBody.source()
            while (true) {
                val line = source.readUtf8Line() ?: break
                if (line.isEmpty() || !line.startsWith("data:")) continue

                val payload = line.substring(5).trim()
                if (payload.isEmpty()) continue
                if (payload == DONE_SENTINEL) break

                val chunk = try {
                    JSONObject(payload)
                } catch (e: Exception) {
                    continue
                }

                // Чанк с usage приходит отдельно и с пустым choices — читаем до разбора choices
                val usage = chunk.optJSONObject("usage")
                if (usage != null && metrics != null) {
                    val prompt = usage.optInt("prompt_tokens", 0)
                    if (prompt > 0) metrics.promptTokens = prompt
                    val completion = usage.optInt("completion_tokens", 0)
                    if (completion > 0) metrics.completionTokens = completion
                }

                val choices = chunk.optJSONArray("choices") ?: continue
                val choice0 = choices.optJSONObject(0) ?: continue
                val delta = choice0.optJSONObject("delta") ?: continue

                val now = System.currentTimeMillis()

                // Размышления thinking-моделей: у OpenRouter это reasoning,
                // у DeepSeek — reasoning_content.
                val reasoningPiece =
                    (delta.opt("reasoning_content") as? String)?.takeIf { it.isNotEmpty() }
                        ?: (delta.opt("reasoning") as? String)?.takeIf { it.isNotEmpty() }
                if (reasoningPiece != null) {
                    metrics?.noteReasoning(reasoningPiece, now)
                }

                (delta.opt("content") as? String)?.takeIf { it.isNotEmpty() }?.let { piece ->
                    metrics?.noteContent(now)
                    content.append(piece)
                    emitted = true
                    listener?.onTextDelta(piece)
                }

                val deltaExtra = delta.optJSONObject("extra_content")
                    ?: choice0.optJSONObject("extra_content")
                    ?: chunk.optJSONObject("extra_content")

                val deltaSig = (delta.opt("thought_signature") as? String)?.takeIf { it.isNotBlank() }
                    ?: (delta.opt("thoughtSignature") as? String)?.takeIf { it.isNotBlank() }
                    ?: deltaExtra?.optJSONObject("google")?.optString("thought_signature")?.takeIf { it.isNotBlank() }
                    ?: deltaExtra?.optString("thought_signature")?.takeIf { it.isNotBlank() }

                val deltaToolCalls = delta.optJSONArray("tool_calls")
                if (deltaToolCalls != null) {
                    for (index in 0 until deltaToolCalls.length()) {
                        val item = deltaToolCalls.optJSONObject(index) ?: continue
                        val hasIndex = item.has("index")
                        val itemIndex = if (hasIndex) item.optInt("index", -1) else -1
                        val itemId = (item.opt("id") as? String)?.takeIf { it.isNotBlank() }

                        val accumulator = when {
                            itemIndex >= 0 -> toolCallSlots.getOrPut(itemIndex) { ToolCallAccumulator() }
                            itemId != null -> {
                                toolCallSlots.values.firstOrNull { it.id == itemId }
                                    ?: toolCallSlots.getOrPut(toolCallSlots.size) { ToolCallAccumulator() }
                            }
                            toolCallSlots.isNotEmpty() -> toolCallSlots.values.last()
                            else -> toolCallSlots.getOrPut(0) { ToolCallAccumulator() }
                        }

                        if (itemId != null && accumulator.id.isBlank()) {
                            accumulator.id = itemId
                        }

                        val function = item.optJSONObject("function")
                        if (function != null) {
                            (function.opt("name") as? String)?.takeIf { it.isNotBlank() }?.let { name ->
                                if (accumulator.name.isBlank()) {
                                    accumulator.name = name
                                    listener?.onToolCallStarted(name)
                                }
                            }
                            functionArgsToString(function).takeIf { it != "{}" }?.let {
                                accumulator.arguments.append(it)
                            }
                        }

                        val itemExtra = item.optJSONObject("extra_content") ?: deltaExtra
                        if (itemExtra != null) {
                            accumulator.extraContent = itemExtra
                            val sig = itemExtra.optJSONObject("google")?.optString("thought_signature")?.takeIf { it.isNotBlank() }
                                ?: itemExtra.optString("thought_signature")?.takeIf { it.isNotBlank() }
                            if (sig != null) {
                                accumulator.thoughtSignature = sig
                            }
                        }

                        val itemSig = (item.opt("thought_signature") as? String)?.takeIf { it.isNotBlank() }
                            ?: (item.opt("thoughtSignature") as? String)?.takeIf { it.isNotBlank() }
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
            }
        }

        metrics?.finish()

        if (toolCallSlots.isEmpty()) {
            val text = content.toString()
            return if (text.isBlank()) {
                AIResponseResult.Error("Модель вернула пустой ответ.", retryable = !emitted)
            } else {
                AIResponseResult.TextResult(text)
            }
        }
        return buildToolCallResult(content.toString(), toolCallSlots, signatureFallback)
    }

    private fun executeBlocking(
        call: Call,
        signatureFallback: Boolean,
        metrics: GenerationMetrics?
    ): AIResponseResult {
        call.execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                return httpError(response.code, responseBody, call)
            }

            val json = JSONObject(responseBody)
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
            val message = choice0.getJSONObject("message")

            // Размышления thinking-модели приходят в самом сообщении
            val reasoning = (message.opt("reasoning_content") as? String)?.takeIf { it.isNotBlank() }
                ?: (message.opt("reasoning") as? String)?.takeIf { it.isNotBlank() }
            if (reasoning != null && metrics != null) {
                val now = System.currentTimeMillis()
                metrics.noteReasoning(reasoning, now)
            }
            metrics?.finish()

            val toolCallsJson = message.optJSONArray("tool_calls")
            val messageExtra = message.optJSONObject("extra_content")
            val messageSig = messageExtra?.optJSONObject("google")?.optString("thought_signature")?.takeIf { it.isNotBlank() }
                ?: messageExtra?.optString("thought_signature")?.takeIf { it.isNotBlank() }
                ?: message.optString("thought_signature").takeIf { it.isNotBlank() }

            if (toolCallsJson != null && toolCallsJson.length() > 0) {
                val calls = mutableListOf<ToolCall>()
                var firstSig: String? = messageSig

                for (index in 0 until toolCallsJson.length()) {
                    val item = toolCallsJson.getJSONObject(index)
                    val function = item.getJSONObject("function")

                    var sig = item.optJSONObject("extra_content")?.optJSONObject("google")?.optString("thought_signature")?.takeIf { it.isNotBlank() }
                        ?: item.optJSONObject("extra_content")?.optString("thought_signature")?.takeIf { it.isNotBlank() }
                        ?: item.optString("thought_signature").takeIf { it.isNotBlank() }
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

                    // Нормализуем id и записываем обратно в echo: пустой id
                    // (Gemini такое присылает) роняет следующий запрос с
                    // 400 function_response.name — echo и tool-ответ обязаны
                    // нести один и тот же непустой id.
                    val callId = (item.opt("id") as? String).orEmpty()
                        .ifBlank { "call_${System.currentTimeMillis()}_$index" }
                    item.put("id", callId)

                    calls.add(
                        ToolCall(
                            id = callId,
                            name = function.getString("name"),
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

            return AIResponseResult.TextResult(message.optString("content", ""))
        }
    }

    /**
     * Аргументы вызова в строку: Gemini иногда отдаёт arguments объектом,
     * а не строкой — приводим к JSON-строке вместо потери.
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
        signatureFallback: Boolean
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

            if (isGemini && copy.optString("role") == "assistant") {
                val toolCalls = copy.optJSONArray("tool_calls")
                if (toolCalls != null && toolCalls.length() > 0) {
                    var defaultSig: String? = null
                    for (j in 0 until toolCalls.length()) {
                        val tc = toolCalls.optJSONObject(j) ?: continue

                        val sig = if (forceSkipValidator) {
                            GEMINI_THOUGHT_SIGNATURE_BYPASS
                        } else {
                            tc.optJSONObject("extra_content")?.optJSONObject("google")?.optString("thought_signature")?.takeIf { it.isNotBlank() }
                                ?: tc.optJSONObject("extra_content")?.optString("thought_signature")?.takeIf { it.isNotBlank() }
                                ?: tc.optString("thought_signature").takeIf { it.isNotBlank() }
                                ?: copy.optJSONObject("extra_content")?.optJSONObject("google")?.optString("thought_signature")?.takeIf { it.isNotBlank() }
                                ?: copy.optJSONObject("extra_content")?.optString("thought_signature")?.takeIf { it.isNotBlank() }
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

    private fun httpError(code: Int, body: String, call: Call): AIResponseResult {
        if (call.isCanceled()) return AIResponseResult.Cancelled

        // Сырой JSON/HTML в плашку чата не вываливаем — только понятный текст.
        val details = try {
            val json = JSONObject(body)
            json.optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() }
                ?: json.optString("message").takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            null
        } ?: body.take(300).trim().takeIf {
            it.isNotBlank() && !it.startsWith("{") && !it.startsWith("[") && !it.startsWith("<")
        }

        val isThoughtSignatureError = details?.contains("thought_signature", ignoreCase = true) == true

        // Провайдер назвал поле, которого не знает. Формат у Google свой
        // (error.details[].fieldViolations[].description), у остальных — текст.
        val unknownField = if (code == 400) extractUnknownField(body) else null

        return when {
            unknownField != null -> AIResponseResult.Error(
                "Провайдер не принял параметр «$unknownField». " +
                        "Запрос повторяется без него.",
                optionalField = unknownField,
                retryable = true
            )
            code == 400 && details != null ->
                AIResponseResult.Error("Ошибка запроса (400): $details")
            code == 400 ->
                AIResponseResult.Error("Ошибка запроса (400): сервер не принял параметры вызова. Повторите запрос.")
            code == 401 -> AIResponseResult.Error("Ошибка авторизации (401). Проверьте API-ключ провайдера.")
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
            isThoughtSignatureError -> AIResponseResult.Error("Ошибка API ($code): $details", retryable = true)
            details != null -> AIResponseResult.Error("Ошибка API ($code): $details")
            else -> AIResponseResult.Error("Ошибка сервера ($code): ответ не распознан, повторите запрос")
        }
    }

    /** Если часть ответа уже показана, повторять запрос нельзя — текст задвоится. */
    private fun withEmitted(result: AIResponseResult, emitted: Boolean): AIResponseResult =
        if (emitted && result is AIResponseResult.Error) result.copy(retryable = false) else result

    /**
     * Достаёт из тела ошибки имя поля, которое провайдер не знает.
     * Проверяются оба известных формата: Google (structured details) и текстовый.
     * Возвращается только имя из белого списка: убирать из запроса произвольное
     * поле, названное сервером, слишком рискованно.
     */
    private fun extractUnknownField(body: String): String? {
        val text = try {
            val json = JSONObject(body)
            val error = json.optJSONObject("error") ?: json
            val message = error.optString("message")
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
        } catch (e: Exception) {
            body
        }

        val lowered = text.lowercase()
        if (!lowered.contains("unknown name") &&
            !lowered.contains("unknown parameter") &&
            !lowered.contains("unrecognized") &&
            !lowered.contains("unexpected")
        ) {
            return null
        }

        return OPTIONAL_PARAM_NAMES.firstOrNull { lowered.contains(it) }
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

        val requestBuilder = try {
            Request.Builder().url(testEndpoint)
        } catch (e: Exception) {
            return@withContext ConnectionTestResult(
                isSuccess = false,
                isReachable = false,
                statusCode = null,
                latencyMs = 0L,
                message = "${providerPrefix}Некорректный адрес URL: ${e.localizedMessage ?: cleanUrl}",
                targetEndpoint = testEndpoint
            )
        }

        requestBuilder.get()
            .addHeader("Accept", "application/json")
            .addHeader("HTTP-Referer", "https://github.com/ArddMobX/Runner")
            .addHeader("X-Title", "Runner Android Agent")

        if (apiKey.isNotBlank()) {
            requestBuilder.addHeader("Authorization", "Bearer ${apiKey.trim()}")
        }

        parsedHeaders.forEach { (name, value) ->
            requestBuilder.header(name, value)
        }

        val start = System.currentTimeMillis()
        try {
            testClient.newCall(requestBuilder.build()).execute().use { response ->
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
                    code == 403 -> ConnectionTestResult(
                        isSuccess = false,
                        isReachable = true,
                        statusCode = code,
                        latencyMs = latencyMs,
                        message = "${providerPrefix}Сервер ответил ($latencyMs мс) · 403 Forbidden (доступ ограничен)",
                        targetEndpoint = testEndpoint
                    )
                    code == 404 -> ConnectionTestResult(
                        // Сервер ответил, значит он доступен. Но работоспособной
                        // связку назвать нельзя: эндпоинта нет и запросы упадут.
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
                    else -> ConnectionTestResult(
                        isSuccess = code < 500,
                        isReachable = true,
                        statusCode = code,
                        latencyMs = latencyMs,
                        message = "${providerPrefix}Сервер ответил · HTTP $code · $latencyMs мс",
                        targetEndpoint = testEndpoint
                    )
                }
            }
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
        }
    }

    private fun resolvePingUrl(target: String): String {
        val trimmed = target.trim().trimEnd('/')
        return when {
            trimmed.endsWith("/chat/completions") -> trimmed
            trimmed.endsWith("/models") -> trimmed
            trimmed.endsWith("/v1") -> "$trimmed/models"
            trimmed.endsWith("/openai") -> "$trimmed/models"
            !trimmed.contains("/models") && !trimmed.contains("/chat/completions") -> "$trimmed/models"
            else -> trimmed
        }
    }

    /**
     * Ожидание между ретраями с проверкой «пользователь нажал Стоп».
     * Обычный delay здесь не годится: Stop не отменяет корутину (иначе не
     * сохранить уже сгенерированный текст), значит проверять надо самим.
     */
    private suspend fun waitForRetry(totalMs: Long, shouldStop: () -> Boolean): Boolean {
        // true — ожидание прервано пользователем.
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
     * Отбрасывает старые сообщения, чтобы запрос влезал в бюджет символов.
     *
     * Три правила, без которых провайдер отвечает 400:
     * 1. system-сообщение остаётся всегда;
     * 2. хвост диалога (от последнего сообщения пользователя) сохраняется целиком —
     *    именно он нужен для продолжения работы;
     * 3. assistant с tool_calls и его ответы tool — неделимая группа: нельзя
     *    оставить tool-ответ без запроса или запрос без ответов.
     */
    private fun trimMessagesToBudget(messages: JSONArray, budgetChars: Int): JSONArray {
        if (budgetChars <= 0 || messages.length() <= 1) return messages

        fun sizeOf(picked: List<JSONObject>): Int {
            val array = JSONArray()
            picked.forEach { array.put(it) }
            return array.toString().toByteArray(Charsets.UTF_8).size
        }

        val all = (0 until messages.length()).mapNotNull { messages.optJSONObject(it) }
        if (all.size != messages.length()) return messages
        if (sizeOf(all) <= budgetChars) return messages

        // Последнее сообщение пользователя: всё от него и до конца — неприкосновенно.
        var tailStart = -1
        for (i in all.indices.reversed()) {
            if (all[i].optString("role") == "user") {
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
            val role = all[i].optString("role")
            if (role == "assistant" && (all[i].optJSONArray("tool_calls")?.length() ?: 0) > 0) {
                var j = i + 1
                while (j < all.size && all[j].optString("role") == "tool") j++
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
        var anyDropped = false
        i = 0
        while (i < all.size) {
            if (sizeOf((0 until all.size).filter { keep[it] }.map { all[it] }) <= budgetChars) break
            val protected = i == 0 || (tailStart >= 0 && i >= tailStart)
            if (protected) {
                i++
                continue
            }

            if (inGroup[i]) {
                val group = groups.firstOrNull { it.start == i }
                if (group != null) {
                    // Группа целиком уходит или остаётся: половина сломает протокол.
                    val canDrop = (group.start..group.endInclusive).none { it == 0 || (tailStart >= 0 && it >= tailStart) }
                    if (canDrop) {
                        for (k in group.start..group.endInclusive) keep[k] = false
                        anyDropped = true
                    }
                    i = group.endInclusive + 1
                    continue
                }
                // tool-ответ без своего assistant — битая история, убираем.
                keep[i] = false
                anyDropped = true
                i++
                continue
            }

            keep[i] = false
            anyDropped = true
            i++
        }

        if (!anyDropped) return messages

        val kept = (0 until all.size).filter { keep[it] }.map { all[it] }
        return JSONArray().apply { kept.forEach { put(it) } }
    }

    private class ToolCallAccumulator {
        var id: String = ""
        var name: String = ""
        val arguments = StringBuilder()
        var extraContent: JSONObject? = null
        var thoughtSignature: String? = null
    }

    private companion object {
        const val MAX_RETRIES = 3
        const val DONE_SENTINEL = "[DONE]"
        const val GEMINI_THOUGHT_SIGNATURE_BYPASS = "skip_thought_signature_validator"

        /** Сколько необязательных полей разрешено убрать в одном запросе. */
        const val MAX_OPTIONAL_STRIPS = 3

        /** Сколько раз пробуем stream_options у незнакомого провайдера. */
        const val STREAM_USAGE_PROBE_ATTEMPTS = 2

        /**
         * Поля, которые разрешено убирать по жалобе провайдера. Белый список:
         * удалять из запроса произвольное поле, названное сервером, нельзя.
         */
        val OPTIONAL_PARAM_NAMES = listOf("stream_options", "temperature", "thinking")

        /** Хосты, про которые точно известно, что stream_options они принимают. */
        val KNOWN_STREAM_USAGE_HOSTS = listOf(
            "openai.com", "groq.com", "openrouter.ai", "deepseek.com", "googleapis.com"
        )

        /**
         * Бюджет контекста в символах (не токенах: точного токенайзера здесь нет).
         * 600 КБ ≈ 150–200 тыс. токенов — с запасом влезает в окна современных
         * моделей (128k+), но не даёт длинной сессии вырасти безгранично и упасть
         * с 400 «context length exceeded».
         */
        const val DEFAULT_CONTEXT_BUDGET_CHARS = 600_000

        /** Шаг ожидания между ретраями: чаще — и Стоп реагирует быстрее. */
        const val RETRY_TICK_MS = 120L
    }

}
