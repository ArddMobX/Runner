package com.runner.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class ProviderBalanceResult(
    val success: Boolean,
    val title: String,
    val detail: String
)

/**
 * Проверка баланса только там, где провайдер реально отдаёт такой эндпоинт.
 *
 * OpenRouter: GET /api/v1/key
 * DeepSeek: GET /user/balance
 *
 * Для остальных провайдеров кнопка не показывается, чтобы не выдумывать
 * «баланс» из заголовков лимитов.
 */
object ProviderBalanceChecker {

    fun supports(provider: Provider): Boolean = when (hostOf(provider.baseUrl)) {
        "openrouter.ai", "api.deepseek.com" -> true
        else -> false
    }

    suspend fun check(
        provider: Provider,
        timeoutSeconds: Int,
        customHeaders: String
    ): ProviderBalanceResult = withContext(Dispatchers.IO) {
        val host = hostOf(provider.baseUrl)
        val url = when (host) {
            "openrouter.ai" -> "https://openrouter.ai/api/v1/key"
            "api.deepseek.com" -> "https://api.deepseek.com/user/balance"
            else -> return@withContext ProviderBalanceResult(
                success = false,
                title = "Проверка недоступна",
                detail = "Этот провайдер не отдаёт баланс через API."
            )
        }
        if (provider.apiKey.isBlank()) {
            return@withContext ProviderBalanceResult(
                success = false,
                title = "Нет ключа",
                detail = "Сначала добавьте API-ключ провайдера."
            )
        }

        val timeout = timeoutSeconds.coerceIn(5, 60).toLong()
        val client = OkHttpClient.Builder()
            .connectTimeout(timeout, TimeUnit.SECONDS)
            .readTimeout(timeout, TimeUnit.SECONDS)
            .callTimeout(timeout + 2, TimeUnit.SECONDS)
            .build()

        val requestbuilder = Request.Builder()
            .url(url)
            .get()
            .addHeader("Accept", "application/json")
            .addHeader("Authorization", "Bearer ${provider.apiKey.trim()}")
            .addHeader("HTTP-Referer", "https://github.com/ArddMobX/Runner")
            .addHeader("X-Title", "Runner Android Agent")

        parseCustomHeaders(customHeaders).forEach { (name, value) ->
            requestbuilder.header(name, value)
        }

        try {
            client.newCall(requestbuilder.build()).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@use when (response.code) {
                        401 -> ProviderBalanceResult(
                            success = false,
                            title = "Ключ не подошёл",
                            detail = "Проверьте, что ключ от этого провайдера."
                        )
                        403 -> ProviderBalanceResult(
                            success = false,
                            title = "Доступ ограничен",
                            detail = "Проверьте права ключа и регион."
                        )
                        404 -> ProviderBalanceResult(
                            success = false,
                            title = "Проверка недоступна",
                            detail = "Провайдер не нашёл эндпоинт баланса."
                        )
                        else -> ProviderBalanceResult(
                            success = false,
                            title = "Ошибка ${response.code}",
                            detail = "Провайдер не отдал баланс."
                        )
                    }
                }

                when (host) {
                    "openrouter.ai" -> parseOpenRouter(body)
                    "api.deepseek.com" -> parseDeepSeek(body)
                    else -> ProviderBalanceResult(
                        success = false,
                        title = "Проверка недоступна",
                        detail = "Этот провайдер не отдаёт баланс через API."
                    )
                }
            }
        } catch (e: Exception) {
            ProviderBalanceResult(
                success = false,
                title = "Нет ответа",
                detail = "Не удалось проверить баланс: ${e.localizedMessage ?: "проверьте сеть"}"
            )
        }
    }

    private fun parseOpenRouter(body: String): ProviderBalanceResult {
        return try {
            val data = JSONObject(body).optJSONObject("data")
                ?: return ProviderBalanceResult(false, "Нет данных", "OpenRouter не вернул баланс.")
            val usage = data.optDouble("usage", Double.NaN).takeUnless { it.isNaN() }
            val limit = data.optDouble("limit", Double.NaN).takeUnless { it.isNaN() }
            val remaining = data.optDouble("limit_remaining", Double.NaN).takeUnless { it.isNaN() }
            val detail = when {
                limit != null && remaining != null ->
                    "Осталось ${money(remaining)} из ${money(limit)}. Использовано ${money(usage ?: 0.0)}."
                limit != null && usage != null ->
                    "Использовано ${money(usage)} из ${money(limit)}."
                usage != null ->
                    "Ключ активен. Лимит не задан, использовано ${money(usage)}."
                else ->
                    "Ключ активен. OpenRouter не сообщил баланс."
            }
            ProviderBalanceResult(true, "OpenRouter", detail)
        } catch (_: Exception) {
            ProviderBalanceResult(false, "Нет данных", "OpenRouter вернул неожиданный ответ.")
        }
    }

    private fun parseDeepSeek(body: String): ProviderBalanceResult {
        return try {
            val root = JSONObject(body)
            val available = root.optBoolean("is_available", true)
            val balances = root.optJSONArray("balance_infos")
            val parts = buildList {
                if (balances != null) {
                    for (index in 0 until balances.length()) {
                        val item = balances.optJSONObject(index) ?: continue
                        val currency = item.optString("currency").ifBlank { "?" }
                        val total = item.optString("total_balance").ifBlank { "0" }
                        add("$currency $total")
                    }
                }
            }
            ProviderBalanceResult(
                success = available,
                title = if (available) "DeepSeek" else "Баланс недостаточен",
                detail = if (parts.isNotEmpty()) {
                    "Доступно: ${parts.joinToString(", ")}"
                } else {
                    "DeepSeek не сообщил баланс."
                }
            )
        } catch (_: Exception) {
            ProviderBalanceResult(false, "Нет данных", "DeepSeek вернул неожиданный ответ.")
        }
    }

    private fun money(value: Double): String = String.format(java.util.Locale.US, "\$%.2f", value)

    private fun hostOf(url: String): String? = try {
        java.net.URI(url.trim()).host?.lowercase()
    } catch (_: Exception) {
        null
    }
}
