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
        reverseProxyUrl: String = ""
    ): Result<List<String>> = withContext(Dispatchers.IO) {
        val target = if (reverseProxyUrl.isNotBlank()) reverseProxyUrl.trim() else baseUrl.trim()
        if (target.isBlank()) {
            return@withContext Result.failure(IOException("Base URL не задан"))
        }

        val endpoint = target.trimEnd('/') + "/models"
        val request = Request.Builder()
            .url(endpoint)
            .get()
            .addHeader("Accept", "application/json")
            .apply {
                if (apiKey.isNotBlank()) {
                    addHeader("Authorization", "Bearer ${apiKey.trim()}")
                }
            }
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IOException(describeHttpError(response.code, body, endpoint))
                    )
                }

                val array = JSONObject(body).optJSONArray("data") ?: JSONArray()
                val ids = LinkedHashSet<String>()
                for (index in 0 until array.length()) {
                    val id = array.optJSONObject(index)?.optString("id").orEmpty()
                    if (id.isNotBlank()) ids.add(id)
                }

                if (ids.isEmpty()) {
                    Result.failure(IOException("Провайдер вернул пустой список моделей"))
                } else {
                    Result.success(ids.sorted())
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
            401, 403 -> "Ключ не подошёл ($code). Проверь API Key провайдера."
            404 -> "Эндпоинт не найден (404): $endpoint"
            429 -> "Слишком много запросов (429). Подожди немного."
            else -> "Ошибка $code: $details"
        }
    }
}
