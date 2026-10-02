package com.runner.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Модель провайдера. Название и длина контекста есть не у всех:
 * OpenRouter отдаёт и то и другое, Groq и Gemini — только id.
 */
data class ModelInfo(
    val id: String,
    val name: String = "",
    val contextLength: Int? = null
) {
    /** Что показывать в списке: человекочитаемое имя, если оно есть. */
    val label: String get() = name.ifBlank { id }

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

    suspend fun fetchModels(
        baseUrl: String,
        apiKey: String,
        reverseProxyUrl: String = "",
        timeoutSeconds: Int = 30,
        customHeaders: String = ""
    ): Result<List<ModelInfo>> = withContext(Dispatchers.IO) {
        val target = if (reverseProxyUrl.isNotBlank()) reverseProxyUrl.trim() else baseUrl.trim()
        if (target.isBlank()) {
            return@withContext Result.failure(IOException("Base URL не задан"))
        }

        val endpoint = target.trimEnd('/') + "/models"
        val requestBuilder = Request.Builder()
            .url(endpoint)
            .get()
            .addHeader("Accept", "application/json")
            .apply {
                if (apiKey.isNotBlank()) {
                    addHeader("Authorization", "Bearer ${apiKey.trim()}")
                }
            }

        parseCustomHeaders(customHeaders).forEach { (name, value) ->
            requestBuilder.header(name, value)
        }

        val request = requestBuilder.build()
        val requestClient = client.newBuilder()
            .connectTimeout(timeoutSeconds.coerceIn(5, 60).toLong().coerceAtMost(20), TimeUnit.SECONDS)
            .readTimeout(timeoutSeconds.coerceIn(5, 120).toLong(), TimeUnit.SECONDS)
            .build()

        try {
            requestClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IOException(describeHttpError(response.code, body, endpoint))
                    )
                }

                val array = JSONObject(body).optJSONArray("data") ?: JSONArray()
                val models = LinkedHashMap<String, ModelInfo>()

                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val id = item.optString("id")
                    if (id.isBlank()) continue

                    val name = item.optString("name")
                    val contextLength = item.optInt("context_length", 0).takeIf { it > 0 }
                        ?: item.optJSONObject("top_provider")
                            ?.optInt("context_length", 0)
                            ?.takeIf { it > 0 }

                    models[id] = ModelInfo(
                        id = id,
                        name = name.takeIf { it.isNotBlank() && it != id }.orEmpty(),
                        contextLength = contextLength
                    )
                }

                if (models.isEmpty()) {
                    Result.failure(IOException("Провайдер вернул пустой список моделей"))
                } else {
                    Result.success(models.values.sortedBy { it.id })
                }
            }
        } catch (e: IOException) {
            Result.failure(IOException("Сеть недоступна: ${e.localizedMessage ?: "не удалось подключиться"}"))
        } catch (e: Exception) {
            Result.failure(IOException("Не удалось разобрать ответ: ${e.localizedMessage}"))
        }
    }

    private fun describeHttpError(code: Int, body: String, endpoint: String): String {
        val details = try {
            val json = JSONObject(body)
            json.optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() }
                ?: json.optString("message").takeIf { it.isNotBlank() }
        } catch (e: Exception) {
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
