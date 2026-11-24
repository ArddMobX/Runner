package com.runner.app.data

import com.runner.app.util.UrlSanitizer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

data class ProviderBalanceResult(
    val success: Boolean,
    val title: String,
    val detail: String
)

/**
 * Проверка баланса только там, где провайдер реально отдаёт такой эндпоинт.
 *
 * OpenRouter: GET /api/v1/credits (с fallback на GET /api/v1/key)
 * DeepSeek: GET /user/balance
 *
 * Для остальных провайдеров кнопка не показывается, чтобы не выдумывать
 * «баланс» из заголовков лимитов.
 */
object ProviderBalanceChecker {

    private val baseClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    fun supports(provider: Provider): Boolean = when (extractHost(provider.baseUrl)) {
        "openrouter.ai", "api.deepseek.com" -> true
        else -> false
    }

    suspend fun check(
        provider: Provider,
        timeoutSeconds: Int,
        customHeaders: String,
        reverseProxyUrl: String = ""
    ): ProviderBalanceResult = withContext(Dispatchers.IO) {
        val host = extractHost(provider.baseUrl)
        val urls = resolveBalanceUrls(host, reverseProxyUrl)
        if (urls.isEmpty()) {
            return@withContext ProviderBalanceResult(
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
        val client = baseClient.newBuilder()
            .connectTimeout(timeout, TimeUnit.SECONDS)
            .readTimeout(timeout, TimeUnit.SECONDS)
            .callTimeout(timeout + 2, TimeUnit.SECONDS)
            .build()

        var lastResult: ProviderBalanceResult? = null
        for ((index, url) in urls.withIndex()) {
            val hasFallback = index < urls.lastIndex
            val request = try {
                val requestBuilder = Request.Builder()
                    .url(url)
                    .get()
                    .addHeader("Accept", "application/json")
                    .addHeader("Authorization", "Bearer ${provider.apiKey.trim()}")
                    .addHeader("HTTP-Referer", "https://github.com/ArddMobX/Runner")
                    .addHeader("X-Title", "Runner Android Agent")

                parseCustomHeaders(customHeaders).forEach { (name, value) ->
                    requestBuilder.header(name, value)
                }
                requestBuilder.build()
            } catch (e: IllegalArgumentException) {
                return@withContext ProviderBalanceResult(
                    success = false,
                    title = "Некорректный адрес",
                    detail = "Проверьте URL прокси, API-ключ и кастомные заголовки: ${e.localizedMessage ?: url}"
                )
            }

            val call = client.newCall(request)
            val cancelHandle = coroutineContext[Job]?.invokeOnCompletion { cause ->
                if (cause != null) call.cancel()
            }
            try {
                val attemptResult = call.execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        // Если /api/v1/credits недоступен (403/404), пробуем fallback /api/v1/key.
                        if (hasFallback && (response.code == 403 || response.code == 404)) {
                            return@use null
                        }
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
                if (attemptResult != null) {
                    if (attemptResult.success || !hasFallback) {
                        return@withContext attemptResult
                    }
                    lastResult = attemptResult
                }
            } catch (e: CancellationException) {
                call.cancel()
                throw e
            } catch (e: Exception) {
                return@withContext ProviderBalanceResult(
                    success = false,
                    title = "Нет ответа",
                    detail = "Не удалось проверить баланс: ${e.localizedMessage ?: "проверьте сеть"}"
                )
            } finally {
                cancelHandle?.dispose()
            }
        }

        lastResult ?: ProviderBalanceResult(
            success = false,
            title = "Нет данных",
            detail = "Провайдер не отдал баланс."
        )
    }

    internal fun resolveBalanceUrls(host: String, reverseProxyUrl: String): List<String> {
        val proxy = if (reverseProxyUrl.isNotBlank()) {
            UrlSanitizer.sanitizeBaseUrl(reverseProxyUrl)
                .removeSuffix("/chat/completions")
                .removeSuffix("/models")
                .trimEnd('/')
        } else {
            ""
        }
        return when (host) {
            "openrouter.ai" -> if (proxy.isNotBlank()) {
                val base = if (proxy.endsWith("/api/v1") || proxy.endsWith("/v1")) proxy else "$proxy/api/v1"
                listOf("$base/credits", "$base/key")
            } else {
                listOf("https://openrouter.ai/api/v1/credits", "https://openrouter.ai/api/v1/key")
            }
            "api.deepseek.com" -> if (proxy.isNotBlank()) {
                val root = proxy.removeSuffix("/v1").trimEnd('/')
                listOf("$root/user/balance")
            } else {
                listOf("https://api.deepseek.com/user/balance")
            }
            else -> emptyList()
        }
    }

    private fun parseOpenRouter(body: String): ProviderBalanceResult {
        return try {
            val data = JSONObject(body).optJSONObject("data")
                ?: return ProviderBalanceResult(false, "Нет данных", "OpenRouter не вернул баланс.")
            val totalCredits = data.optDouble("total_credits", Double.NaN).takeUnless { it.isNaN() }
            val totalUsage = data.optDouble("total_usage", Double.NaN).takeUnless { it.isNaN() }
            if (totalCredits != null && totalUsage != null) {
                val remaining = (totalCredits - totalUsage).coerceAtLeast(0.0)
                return ProviderBalanceResult(
                    success = true,
                    title = "OpenRouter",
                    detail = "Осталось ${money(remaining)} из ${money(totalCredits)}. Использовано ${money(totalUsage)}."
                )
            }

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
                    return ProviderBalanceResult(false, "Нет данных", "OpenRouter не сообщил баланс.")
            }
            ProviderBalanceResult(true, "OpenRouter", detail)
        } catch (_: Exception) {
            ProviderBalanceResult(false, "Нет данных", "OpenRouter вернул неожиданный ответ.")
        }
    }

    private fun parseDeepSeek(body: String): ProviderBalanceResult {
        return try {
            val root = JSONObject(body)
            val hasAvailable = root.has("is_available") && !root.isNull("is_available")
            val balances = root.optJSONArray("balance_infos")
            val parts = buildList {
                if (balances != null) {
                    for (index in 0 until balances.length()) {
                        val item = balances.optJSONObject(index) ?: continue
                        val currency = item.optNullableString("currency")?.ifBlank { "?" } ?: "?"
                        val total = item.optNullableString("total_balance")?.ifBlank { "0" } ?: "0"
                        add("$currency $total")
                    }
                }
            }
            if (!hasAvailable && parts.isEmpty()) {
                return ProviderBalanceResult(false, "Нет данных", "DeepSeek не вернул данные о балансе.")
            }
            val available = root.optBoolean("is_available", parts.isNotEmpty())
            ProviderBalanceResult(
                success = available,
                title = if (available) "DeepSeek" else "Баланс недостаточен",
                detail = if (parts.isNotEmpty()) {
                    "Доступно: ${parts.joinToString(", ")}"
                } else {
                    "DeepSeek не сообщил сумму баланса."
                }
            )
        } catch (_: Exception) {
            ProviderBalanceResult(false, "Нет данных", "DeepSeek вернул неожиданный ответ.")
        }
    }

    private fun money(value: Double): String = String.format(Locale.US, "\$%.2f", value)
}
