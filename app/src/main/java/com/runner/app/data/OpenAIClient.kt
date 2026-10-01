package com.runner.app.data

import com.runner.app.tools.ToolDispatcher
import kotlinx.coroutines.Dispatchers
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
        messages: JSONArray
    ): AIResponseResult = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext AIResponseResult.Error("API ключ не задан. Перейдите в настройки и укажите ключ.")
        }

        val effectiveBaseUrl = if (baseUrl.isBlank()) "https://api.groq.com/openai/v1" else baseUrl.trim()
        val effectiveModel = if (modelName.isBlank()) "llama-3.3-70b-versatile" else modelName.trim()

        val endpoint = resolveChatEndpoint(effectiveBaseUrl)

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

        try {
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
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
                    429 -> AIResponseResult.Error("Превышен лимит запросов (429 Rate Limit / Quota Exceeded).")
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
                AIResponseResult.ToolCallsResult(
                    assistantMessageJson = message,
                    toolCalls = toolCallsList
                )
            } else {
                val content = message.optString("content", "")
                AIResponseResult.TextResult(content)
            }

        } catch (e: IOException) {
            AIResponseResult.Error("Сетевая ошибка: ${e.localizedMessage ?: "Не удалось подключиться к серверу"}")
        } catch (e: Exception) {
            AIResponseResult.Error("Ошибка обработки ответа: ${e.localizedMessage}")
        }
    }

    private fun resolveChatEndpoint(baseUrl: String): String {
        val trimmed = baseUrl.trim().trimEnd('/')
        return when {
            trimmed.endsWith("/chat/completions") -> trimmed
            trimmed.endsWith("/v1") -> "$trimmed/chat/completions"
            else -> "$trimmed/chat/completions"
        }
    }
}
