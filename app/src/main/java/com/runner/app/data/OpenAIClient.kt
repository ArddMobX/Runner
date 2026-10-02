package com.runner.app.data

import com.runner.app.tools.ToolDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
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
    data class Error(val message: String, val retryable: Boolean = false) : AIResponseResult()

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
     * usage в стриме приходит только по явному запросу, и поддерживают его
     * не все провайдеры. Шлём только тем, в ком уверены, — иначе есть риск
     * получить ошибку на неизвестном параметре.
     */
    private fun supportsStreamUsage(endpoint: String): Boolean {
        val host = endpoint.lowercase()
        return host.contains("openai.com") ||
                host.contains("groq.com") ||
                host.contains("openrouter.ai") ||
                host.contains("deepseek.com")
    }

    private val client = OkHttpClient.Builder()
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
        withTools: Boolean = true
    ): AIResponseResult = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext AIResponseResult.Error(
                "API ключ не задан. Открой настройки и укажи ключ провайдера."
            )
        }

        val effectiveBaseUrl = baseUrl.ifBlank { "https://openrouter.ai/api/v1" }
        val effectiveModel = modelName.ifBlank { "openai/gpt-oss-120b" }
        val endpoint = resolveChatEndpoint(effectiveBaseUrl, reverseProxyUrl)

        val isGemini = endpoint.contains("googleapis.com", ignoreCase = true) ||
                effectiveModel.contains("gemini", ignoreCase = true) ||
                effectiveBaseUrl.contains("googleapis.com", ignoreCase = true)

        var forceSkipValidator = false
        var attempt = 0
        while (attempt <= MAX_RETRIES) {
            val sanitizedMessages = sanitizeMessages(messages, isGemini, forceSkipValidator)
            val requestBody = JSONObject().apply {
                put("model", effectiveModel)
                put("messages", sanitizedMessages)
                if (withTools) {
                    put("tools", ToolDispatcher.getToolsJson())
                    put("tool_choice", "auto")
                }
                put("temperature", temperature)
                if (streaming) {
                    put("stream", true)
                    if (supportsStreamUsage(endpoint)) {
                        put("stream_options", JSONObject().put("include_usage", true))
                    }
                }
                // У DeepSeek V4 thinking mode включён по умолчанию. Для агентского цикла это
                // лишняя латентность и расход токенов, поэтому явно выключаем.
                if (endpoint.contains("deepseek.com")) {
                    put("thinking", JSONObject().put("type", "disabled"))
                }
            }

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
                    executeStreaming(call, listener, isGemini, metrics)
                } else {
                    executeBlocking(call, isGemini, metrics)
                }

                if (result is AIResponseResult.Error && result.retryable && attempt < MAX_RETRIES) {
                    if (result.message.contains("thought_signature", ignoreCase = true)) {
                        forceSkipValidator = true
                    }
                    attempt++
                    val pause = backoffMillis(attempt)
                    listener?.onRetry(result.message, pause)
                    delay(pause)
                    continue
                }
                return@withContext result
            } catch (e: IOException) {
                if (call.isCanceled()) return@withContext AIResponseResult.Cancelled
                if (attempt < MAX_RETRIES) {
                    attempt++
                    val pause = backoffMillis(attempt)
                    listener?.onRetry("Сеть недоступна, повтор через ${pause / 1000} с", pause)
                    delay(pause)
                    continue
                }
                return@withContext AIResponseResult.Error(
                    "Сетевая ошибка: ${e.localizedMessage ?: "не удалось подключиться к серверу"}"
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
        isGemini: Boolean,
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
                            (function.opt("arguments") as? String)?.takeIf { it.isNotEmpty() }?.let {
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
        return buildToolCallResult(content.toString(), toolCallSlots, isGemini)
    }

    private fun executeBlocking(
        call: Call,
        isGemini: Boolean,
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

                    if (sig.isNullOrBlank() && isGemini) {
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

                    calls.add(
                        ToolCall(
                            id = item.optString("id", "call_${System.currentTimeMillis()}_$index"),
                            name = function.getString("name"),
                            arguments = function.optString("arguments", "{}"),
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

    private fun buildToolCallResult(
        text: String,
        slots: Map<Int, ToolCallAccumulator>,
        isGemini: Boolean
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
            if (sig.isNullOrBlank() && isGemini) {
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

        val details = try {
            val json = JSONObject(body)
            json.optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() }
                ?: json.optString("message").takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            null
        } ?: body.take(300)

        val isThoughtSignatureError = details.contains("thought_signature", ignoreCase = true)

        return when {
            code == 401 -> AIResponseResult.Error("Ошибка авторизации (401). Проверь API ключ провайдера.")
            code == 403 -> AIResponseResult.Error("Доступ запрещён (403): $details")
            code == 404 -> AIResponseResult.Error("Эндпоинт или модель не найдены (404): $details")
            code == 429 -> AIResponseResult.Error("Лимит запросов (429): $details", retryable = true)
            isThoughtSignatureError -> AIResponseResult.Error("Ошибка API ($code): $details", retryable = true)
            else -> AIResponseResult.Error("Ошибка API ($code): $details")
        }
    }

    /** Если часть ответа уже показана, повторять запрос нельзя — текст задвоится. */
    private fun withEmitted(result: AIResponseResult, emitted: Boolean): AIResponseResult =
        if (emitted && result is AIResponseResult.Error) result.copy(retryable = false) else result

    private fun backoffMillis(attempt: Int): Long =
        (2000L * (1L shl (attempt - 1))).coerceAtMost(10_000L)

    private fun resolveChatEndpoint(baseUrl: String, reverseProxyUrl: String): String {
        val target = if (reverseProxyUrl.isNotBlank()) reverseProxyUrl.trim() else baseUrl.trim()
        val trimmed = target.trimEnd('/')
        return when {
            trimmed.endsWith("/chat/completions") -> trimmed
            trimmed.endsWith("/v1") -> "$trimmed/chat/completions"
            else -> "$trimmed/chat/completions"
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
        val cleanUrl = targetUrl.trim()
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
                        isSuccess = true,
                        isReachable = true,
                        statusCode = code,
                        latencyMs = latencyMs,
                        message = "${providerPrefix}Сервер ответил · 404 Not Found · $latencyMs мс",
                        targetEndpoint = testEndpoint
                    )
                    code == 405 -> ConnectionTestResult(
                        isSuccess = true,
                        isReachable = true,
                        statusCode = code,
                        latencyMs = latencyMs,
                        message = "${providerPrefix}Сервер доступен · 405 Method · $latencyMs мс",
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
                message = "${providerPrefix}Таймаут ($timeoutSeconds с) — сервер не ответил",
                targetEndpoint = testEndpoint
            )
        } catch (e: java.net.UnknownHostException) {
            ConnectionTestResult(
                isSuccess = false,
                isReachable = false,
                statusCode = null,
                latencyMs = 0L,
                message = "Ошибка DNS — хост не найден (${e.message ?: "недоступен"})",
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
    }

}
