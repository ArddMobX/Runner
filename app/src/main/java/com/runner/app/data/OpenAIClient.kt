package com.runner.app.data

import com.runner.app.tools.ToolDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
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
    data class ToolCallsResult(val assistantMessageJson: JSONObject, val toolCalls: List<ToolCall>) : AIResponseResult()
    data class Error(val message: String) : AIResponseResult()
}

class OpenAIClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun sendChatCompletion(
        baseUrl: String,
        apiKey: String,
        modelName: String,
        messages: JSONArray,
        reverseProxyUrl: String = ""
    ): AIResponseResult = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext AIResponseResult.Error("API ключ не задан. Перейдите в настройки и укажите ключ.")
        }

        val effectiveBaseUrl = if (baseUrl.isBlank()) "https://openrouter.ai/api/v1" else baseUrl.trim()
        val effectiveModel = if (modelName.isBlank()) "meta-llama/llama-3.3-70b-instruct" else modelName.trim()

        val endpoint = resolveChatEndpoint(effectiveBaseUrl, reverseProxyUrl)

        val requestBodyJson = JSONObject().apply {
            put("model", effectiveModel)
            put("messages", messages)
            put("tools", ToolDispatcher.getToolsJson())
            put("tool_choice", "auto")
            put("temperature", 0.2)
        }

        val request = Request.Builder()
            .url(endpoint)
            .addHeader("Authorization", "Bearer ${apiKey.trim()}")
            .addHeader("Content-Type", "application/json")
            .addHeader("HTTP-Referer", "https://github.com/ArddMobX/Runner")
            .addHeader("X-Title", "Runner Android Agent")
            .post(requestBodyJson.toString().toRequestBody(jsonMediaType))
            .build()

        val maxRetries = 3
        var attempt = 0

        while (attempt <= maxRetries) {
            try {
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""

                if (!response.isSuccessful) {
                    if (response.code == 429) {
                        if (attempt < maxRetries) {
                            val retryAfterHeader = response.header("Retry-After")
                            val retryAfterSeconds = retryAfterHeader?.toLongOrNull()
                            val delayMs = if (retryAfterSeconds != null && retryAfterSeconds > 0) {
                                (retryAfterSeconds * 1000L).coerceIn(1000L, 30000L)
                            } else {
                                1000L * (1 shl attempt) // 1s, 2s, 4s
                            }
                            attempt++
                            delay(delayMs)
                            continue
                        } else {
                            return@withContext AIResponseResult.Error(
                                "Превышен лимит запросов (429 Rate Limit / Quota Exceeded) после $maxRetries повторов. Подождите или смените модель/провайдера."
                            )
                        }
                    }

                    val errorDetails = try {
                        val errJson = JSONObject(responseBody)
                        errJson.optJSONObject("error")?.optString("message")
                            ?: errJson.optString("message")
                    } catch (e: Exception) {
                        null
                    } ?: responseBody.take(300)

                    return@withContext when (response.code) {
                        401 -> AIResponseResult.Error("Ошибка авторизации (401 Unauthorized). Проверьте правильность API ключа.")
                        403 -> AIResponseResult.Error("Доступ запрещен (403 Forbidden): $errorDetails")
                        404 -> AIResponseResult.Error("Эндпоинт не найден (404 Not Found). Проверьте Base URL ($endpoint).")
                        else -> AIResponseResult.Error("Ошибка API (${response.code}): $errorDetails")
                    }
                }

                val json = JSONObject(responseBody)
                val choices = json.optJSONArray("choices")
                if (choices == null || choices.length() == 0) {
                    return@withContext AIResponseResult.Error("Получен пустой ответ от модели.")
                }

                val choice = choices.getJSONObject(0)
                val message = choice.getJSONObject("message")

                val toolCallsJson = message.optJSONArray("tool_calls")
                if (toolCallsJson != null && toolCallsJson.length() > 0) {
                    val toolCallsList = mutableListOf<ToolCall>()
                    for (i in 0 until toolCallsJson.length()) {
                        val call = toolCallsJson.getJSONObject(i)
                        val id = call.optString("id", "call_${System.currentTimeMillis()}_$i")
                        val function = call.getJSONObject("function")
                        val name = function.getString("name")
                        val arguments = function.optString("arguments", "{}")
                        toolCallsList.add(ToolCall(id = id, name = name, arguments = arguments))
                    }
                    return@withContext AIResponseResult.ToolCallsResult(
                        assistantMessageJson = message,
                        toolCalls = toolCallsList
                    )
                } else {
                    val content = message.optString("content", "")
                    return@withContext AIResponseResult.TextResult(content)
                }

            } catch (e: IOException) {
                if (attempt < maxRetries) {
                    attempt++
                    delay(1000L * attempt)
                    continue
                }
                return@withContext AIResponseResult.Error("Сетевая ошибка: ${e.localizedMessage ?: "Не удалось подключиться к серверу"}")
            } catch (e: Exception) {
                return@withContext AIResponseResult.Error("Ошибка обработки ответа: ${e.localizedMessage}")
            }
        }

        AIResponseResult.Error("Превышено количество попыток запроса к API.")
    }

    private fun resolveChatEndpoint(baseUrl: String, reverseProxyUrl: String): String {
        val target = if (reverseProxyUrl.isNotBlank()) reverseProxyUrl.trim() else baseUrl.trim()
        val trimmed = target.trimEnd('/')
        return when {
            trimmed.endsWith("/chat/completions") -> trimmed
            trimmed.endsWith("/v1") -> "$trimmed/chat/completions"
            else -> "$trimmed/chat/completions"
        }
    }
}
