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
    val arguments: String
)

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

class OpenAIClient {

    /** Колбэки для живого вывода. Все методы необязательные. */
    interface StreamListener {
        fun onTextDelta(delta: String) {}
        fun onToolCallStarted(name: String) {}
        fun onRetry(message: String, delayMillis: Long) {}
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

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
        temperature: Double = 0.2,
        streaming: Boolean = true,
        listener: StreamListener? = null
    ): AIResponseResult = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext AIResponseResult.Error(
                "API ключ не задан. Открой настройки и укажи ключ провайдера."
            )
        }

        val effectiveBaseUrl = baseUrl.ifBlank { "https://openrouter.ai/api/v1" }
        val effectiveModel = modelName.ifBlank { "openai/gpt-oss-120b" }
        val endpoint = resolveChatEndpoint(effectiveBaseUrl, reverseProxyUrl)

        val requestBody = JSONObject().apply {
            put("model", effectiveModel)
            put("messages", messages)
            put("tools", ToolDispatcher.getToolsJson())
            put("tool_choice", "auto")
            put("temperature", temperature)
            if (streaming) put("stream", true)
            // У DeepSeek V4 thinking mode включён по умолчанию. Для агентского цикла это
            // лишняя латентность и расход токенов, поэтому явно выключаем.
            if (endpoint.contains("deepseek.com")) {
                put("thinking", JSONObject().put("type", "disabled"))
            }
        }

        val request = Request.Builder()
            .url(endpoint)
            .addHeader("Authorization", "Bearer ${apiKey.trim()}")
            .addHeader("Content-Type", "application/json")
            .addHeader("HTTP-Referer", "https://github.com/ArddMobX/Runner")
            .addHeader("X-Title", "Runner Android Agent")
            .post(requestBody.toString().toRequestBody(jsonMediaType))
            .build()

        var attempt = 0
        while (attempt <= MAX_RETRIES) {
            val call = client.newCall(request)
            activeCall = call
            try {
                val result = if (streaming) executeStreaming(call, listener) else executeBlocking(call)

                if (result is AIResponseResult.Error && result.retryable && attempt < MAX_RETRIES) {
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

    /** Разбирает SSE-поток: content уходит в UI по мере поступления, tool_calls склеиваются по index. */
    private fun executeStreaming(call: Call, listener: StreamListener?): AIResponseResult {
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

                val choices = chunk.optJSONArray("choices") ?: continue
                val delta = choices.optJSONObject(0)?.optJSONObject("delta") ?: continue

                (delta.opt("content") as? String)?.takeIf { it.isNotEmpty() }?.let { piece ->
                    content.append(piece)
                    emitted = true
                    listener?.onTextDelta(piece)
                }

                val deltaToolCalls = delta.optJSONArray("tool_calls") ?: continue
                for (index in 0 until deltaToolCalls.length()) {
                    val item = deltaToolCalls.optJSONObject(index) ?: continue
                    val slot = item.optInt("index", index)
                    val accumulator = toolCallSlots.getOrPut(slot) { ToolCallAccumulator() }

                    (item.opt("id") as? String)?.takeIf { it.isNotBlank() }?.let { accumulator.id = it }

                    val function = item.optJSONObject("function") ?: continue
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
            }
        }

        if (toolCallSlots.isEmpty()) {
            val text = content.toString()
            return if (text.isBlank()) {
                AIResponseResult.Error("Модель вернула пустой ответ.", retryable = !emitted)
            } else {
                AIResponseResult.TextResult(text)
            }
        }
        return buildToolCallResult(content.toString(), toolCallSlots)
    }

    private fun executeBlocking(call: Call): AIResponseResult {
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

            val message = choices.getJSONObject(0).getJSONObject("message")
            val toolCallsJson = message.optJSONArray("tool_calls")
            if (toolCallsJson != null && toolCallsJson.length() > 0) {
                val calls = mutableListOf<ToolCall>()
                for (index in 0 until toolCallsJson.length()) {
                    val item = toolCallsJson.getJSONObject(index)
                    val function = item.getJSONObject("function")
                    calls.add(
                        ToolCall(
                            id = item.optString("id", "call_${System.currentTimeMillis()}_$index"),
                            name = function.getString("name"),
                            arguments = function.optString("arguments", "{}")
                        )
                    )
                }
                return AIResponseResult.ToolCallsResult(message, calls)
            }

            return AIResponseResult.TextResult(message.optString("content", ""))
        }
    }

    private fun buildToolCallResult(
        text: String,
        slots: Map<Int, ToolCallAccumulator>
    ): AIResponseResult {
        val calls = mutableListOf<ToolCall>()
        val jsonCalls = JSONArray()

        slots.forEach { (slot, accumulator) ->
            val name = accumulator.name
            if (name.isBlank()) return@forEach
            val id = accumulator.id.ifBlank { "call_${System.currentTimeMillis()}_$slot" }
            val arguments = accumulator.arguments.toString().ifBlank { "{}" }

            calls.add(ToolCall(id = id, name = name, arguments = arguments))
            jsonCalls.put(
                JSONObject().apply {
                    put("id", id)
                    put("type", "function")
                    put(
                        "function",
                        JSONObject().apply {
                            put("name", name)
                            put("arguments", arguments)
                        }
                    )
                }
            )
        }

        if (calls.isEmpty()) {
            return AIResponseResult.Error("Модель запросила инструмент без имени.")
        }

        val assistantMessage = JSONObject().apply {
            put("role", "assistant")
            put("content", text)
            put("tool_calls", jsonCalls)
        }
        return AIResponseResult.ToolCallsResult(assistantMessage, calls)
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

        return when (code) {
            401 -> AIResponseResult.Error("Ошибка авторизации (401). Проверь API ключ провайдера.")
            403 -> AIResponseResult.Error("Доступ запрещён (403): $details")
            404 -> AIResponseResult.Error("Эндпоинт или модель не найдены (404): $details")
            429 -> AIResponseResult.Error("Лимит запросов (429): $details", retryable = true)
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

    private class ToolCallAccumulator {
        var id: String = ""
        var name: String = ""
        val arguments = StringBuilder()
    }

    private companion object {
        const val MAX_RETRIES = 3
        const val DONE_SENTINEL = "[DONE]"
    }
}
