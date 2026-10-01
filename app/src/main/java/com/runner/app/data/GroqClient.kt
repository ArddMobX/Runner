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

sealed class GroqResult {
    data class TextResult(val text: String) : GroqResult()
    data class ToolCallsResult(val assistantMessageJson: JSONObject, val toolCalls: List<ToolCall>) : GroqResult()
    data class Error(val message: String) : GroqResult()
}

class GroqClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun sendChatCompletion(
        apiKey: String,
        messages: JSONArray
    ): GroqResult = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext GroqResult.Error("API ключ Groq не задан. Перейдите в настройки и укажите ключ.")
        }

        val requestBodyJson = JSONObject().apply {
            put("model", "llama-3.3-70b-versatile")
            put("messages", messages)
            put("tools", ToolDispatcher.getToolsJson())
            put("tool_choice", "auto")
            put("temperature", 0.2)
        }

        val request = Request.Builder()
            .url("https://api.groq.com/openai/v1/chat/completions")
            .addHeader("Authorization", "Bearer ${apiKey.trim()}")
            .addHeader("Content-Type", "application/json")
            .post(requestBodyJson.toString().toRequestBody(jsonMediaType))
            .build()

        try {
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return@withContext when (response.code) {
                    401 -> GroqResult.Error("Неверный API ключ Groq (401 Unauthorized). Проверьте ключ в настройках.")
                    429 -> GroqResult.Error("Превышен лимит запросов Groq (429 Rate Limit). Подождите немного.")
                    else -> {
                        val errorMsg = try {
                            JSONObject(responseBody).optJSONObject("error")?.optString("message")
                        } catch (e: Exception) {
                            null
                        } ?: responseBody
                        GroqResult.Error("Ошибка Groq API (${response.code}): $errorMsg")
                    }
                }
            }

            val json = JSONObject(responseBody)
            val choices = json.optJSONArray("choices")
            if (choices == null || choices.length() == 0) {
                return@withContext GroqResult.Error("Пустой ответ от модели.")
            }

            val choice = choices.getJSONObject(0)
            val message = choice.getJSONObject("message")

            // Check if there are tool calls
            val toolCallsJson = message.optJSONArray("tool_calls")
            if (toolCallsJson != null && toolCallsJson.length() > 0) {
                val toolCallsList = mutableListOf<ToolCall>()
                for (i in 0 until toolCallsJson.length()) {
                    val call = toolCallsJson.getJSONObject(i)
                    val id = call.getString("id")
                    val function = call.getJSONObject("function")
                    val name = function.getString("name")
                    val arguments = function.optString("arguments", "{}")
                    toolCallsList.add(ToolCall(id = id, name = name, arguments = arguments))
                }
                GroqResult.ToolCallsResult(
                    assistantMessageJson = message,
                    toolCalls = toolCallsList
                )
            } else {
                val content = message.optString("content", "")
                GroqResult.TextResult(content)
            }

        } catch (e: IOException) {
            GroqResult.Error("Сетевая ошибка: ${e.localizedMessage ?: "Не удалось подключиться к Groq API"}")
        } catch (e: Exception) {
            GroqResult.Error("Ошибка обработки ответа: ${e.localizedMessage}")
        }
    }
}
