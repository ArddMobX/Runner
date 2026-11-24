package com.runner.app.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

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
        val normalized = extractHost(host)
        if (normalized.isBlank() || !info.hasAny) return
        val stamped = info.copy(updatedAt = System.currentTimeMillis())
        _byHost.update { current -> current + (normalized to stamped) }
    }

    fun forBaseUrl(baseUrl: String, reverseProxyUrl: String = ""): RateLimitInfo? {
        val map = _byHost.value
        val baseHost = extractHost(baseUrl)
        if (baseHost.isNotEmpty()) {
            map[baseHost]?.let { return it }
        }
        val proxyHost = extractHost(reverseProxyUrl)
        if (proxyHost.isNotEmpty()) {
            map[proxyHost]?.let { return it }
        }
        return null
    }
}
