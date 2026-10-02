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

    /**
     * Фильтрует не-чат модели:
     * - Оставляет только модели с поддержкой generateContent (если провайдер отдаёт supportedGenerationMethods).
     * - Скрывает aqa, deep-research*, computer-use*, antigravity*, embedding, moderation, tts, whisper, etc.
     */
    fun isChatAndToolModel(
        id: String,
        name: String = "",
        supportedMethods: List<String>? = null
    ): Boolean {
        // 1. Проверка методов генерации (Gemini API)
        if (supportedMethods != null && supportedMethods.isNotEmpty()) {
            if (!supportedMethods.contains("generateContent")) {
                return false
            }
        }

        val cleanId = id.removePrefix("models/").lowercase()
        val cleanName = name.removePrefix("models/").lowercase()

        // 2. Скрываем aqa, deep-research*, computer-use*, antigravity*
        val blockedExactOrPrefix = listOf(
            "aqa",
            "deep-research",
            "computer-use",
            "antigravity"
        )
        for (pattern in blockedExactOrPrefix) {
            if (cleanId == pattern ||
                cleanId.startsWith("$pattern-") ||
                cleanId.startsWith("${pattern}_") ||
                cleanId.contains(pattern)
            ) {
                return false
            }
            if (cleanName.contains(pattern)) {
                return false
            }
        }

        // 3. Скрываем embedding и прочие не-чат сервисы
        val blockedKeywords = listOf(
            "embed",
            "moderation",
            "tts",
            "whisper",
            "dall-e",
            "imagen",
            "realtime",
            "transcription",
            "babbage",
            "davinci"
        )
        for (kw in blockedKeywords) {
            if (cleanId.contains(kw) || cleanName.contains(kw)) {
                return false
            }
        }

        return true
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

        val endpoint = if (target.endsWith("/models")) target else "$target/models"
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

                val rootJson = JSONObject(body)
                val array = rootJson.optJSONArray("data")
                    ?: rootJson.optJSONArray("models")
                    ?: JSONArray()
                val models = LinkedHashMap<String, ModelInfo>()

                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val id = item.optString("id").ifBlank { item.optString("name") }
                    if (id.isBlank()) continue

                    val displayName = item.optString("displayName").ifBlank { item.optString("name") }

                    val methodsJson = item.optJSONArray("supportedGenerationMethods")
                    val supportedMethods = if (methodsJson != null) {
                        List(methodsJson.length()) { methodsJson.optString(it) }
                    } else null

                    if (!isChatAndToolModel(id, displayName, supportedMethods)) {
                        continue
                    }

                    val contextLength = item.optInt("context_length", 0).takeIf { it > 0 }
                        ?: item.optInt("inputTokenLimit", 0).takeIf { it > 0 }
                        ?: item.optJSONObject("top_provider")
                            ?.optInt("context_length", 0)
                            ?.takeIf { it > 0 }

                    val cleanName = displayName.takeIf { it.isNotBlank() && it != id }.orEmpty()

                    models[id] = ModelInfo(
                        id = id,
                        name = cleanName,
                        contextLength = contextLength
                    )
                }

                if (models.isEmpty()) {
                    Result.failure(IOException("Провайдер вернул пустой список моделей"))
                } else {
                    Result.success(models.values.sortedBy { it.label.lowercase() })
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
