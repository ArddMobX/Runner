package com.runner.app

import com.runner.app.data.Provider
import com.runner.app.data.ProviderBalanceChecker
import com.runner.app.data.RateLimitInfo
import com.runner.app.data.UsageEvent
import com.runner.app.data.totals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageTotalsTest {

    @Test
    fun totalsSumRequestsAndTokens() {
        val events = listOf(
            UsageEvent(0L, "openrouter", "OpenRouter", "a", "s1", 10, 5),
            UsageEvent(0L, "openrouter", "OpenRouter", "b", "s1", 20, 7)
        )
        val totals = events.totals()
        assertEquals(2, totals.requests)
        assertEquals(30L, totals.promptTokens)
        assertEquals(12L, totals.completionTokens)
        assertEquals(42L, totals.totalTokens)
    }

    @Test
    fun emptyUsageHasZero() {
        val totals = emptyList<UsageEvent>().totals()
        assertEquals(0, totals.requests)
        assertEquals(0L, totals.totalTokens)
    }

    @Test
    fun rateLimitKnowsWhenItHasData() {
        assertFalse(RateLimitInfo().hasAny)
        assertTrue(RateLimitInfo(remainingRequests = 9).hasAny)
        assertTrue(RateLimitInfo(remainingTokens = 1000).hasAny)
    }

    @Test
    fun balanceSupportedOnlyForOfficialHosts() {
        assertTrue(
            ProviderBalanceChecker.supports(
                Provider("openrouter", "OpenRouter", "https://openrouter.ai/api/v1")
            )
        )
        assertTrue(
            ProviderBalanceChecker.supports(
                Provider("deepseek", "DeepSeek", "https://api.deepseek.com")
            )
        )
        assertFalse(
            ProviderBalanceChecker.supports(
                Provider("proxy", "Proxy", "https://my-proxy.workers.dev/v1")
            )
        )
    }
}
