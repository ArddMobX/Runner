package com.runner.app.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Остатки лимитов, которые провайдер прислал в заголовках последнего ответа.
 *
 * Это не баланс аккаунта. Заголовки обычно описывают текущее окно запросов
 * или токенов, поэтому в интерфейсе их нельзя называть «балансом».
 */
data class RateLimitInfo(
    val remainingRequests: Int? = null,
    val limitRequests: Int? = null,
    val resetRequests: String? = null,
    val remainingTokens: Int? = null,
    val limitTokens: Int? = null,
    val resetTokens: String? = null,
    val updatedAt: Long = 0L
) {
    val hasAny: Boolean
        get() = remainingRequests != null || remainingTokens != null ||
                limitRequests != null || limitTokens != null
}

object RateLimitStore {
    private val _byHost = MutableStateFlow<Map<String, RateLimitInfo>>(emptyMap())
    val byHost: StateFlow<Map<String, RateLimitInfo>> = _byHost.asStateFlow()

    fun update(host: String, info: RateLimitInfo) {
        if (host.isBlank() || !info.hasAny) return
        _byHost.value = _byHost.value + (host.lowercase() to info.copy(updatedAt = System.currentTimeMillis()))
    }

    fun forBaseUrl(baseUrl: String): RateLimitInfo? {
        val host = try {
            java.net.URI(baseUrl.trim()).host?.lowercase()
        } catch (_: Exception) {
            null
        } ?: return null
        return _byHost.value[host]
    }
}
