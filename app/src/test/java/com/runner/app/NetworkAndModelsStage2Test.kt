package com.runner.app

import com.runner.app.data.ConnectionDiagnosis
import com.runner.app.data.ConnectionTestResult
import com.runner.app.data.GenerationMetrics
import com.runner.app.data.ModelAccess
import com.runner.app.data.ModelCatalog
import com.runner.app.data.ModelInfo
import com.runner.app.data.ModelRecommendations
import com.runner.app.data.OpenAIClient
import com.runner.app.data.ProviderBalanceChecker
import com.runner.app.data.RateLimitInfo
import com.runner.app.data.RateLimitStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkAndModelsStage2Test {

    // --- OpenAIClient HTTP error & optional parameter detection ---

    @Test
    fun http400ThoughtSignatureIsRetryable() {
        val error = OpenAIClient.classifyHttpError(
            code = 400,
            details = "Function call is missing a thought_signature in functionCall parts",
            host = "generativelanguage.googleapis.com",
            unknownField = null
        )
        assertTrue(error.retryable)
        assertTrue(error.message.contains("thought_signature"))
    }

    @Test
    fun http5xxErrorsAreRetryable() {
        listOf(500, 502, 503, 504).forEach { code ->
            val err = OpenAIClient.classifyHttpError(code, "upstream timeout", "api.example.com", null)
            assertTrue("Expected HTTP $code to be retryable", err.retryable)
        }
    }

    @Test
    fun detectUnknownFieldMatchesOnlySentParamsAndOpenAiUnsupportedFormat() {
        val openAiMsg = "Unsupported parameter: 'max_tokens' is not supported with this model. " +
                "Use 'max_completion_tokens' instead."
        assertEquals(
            "max_tokens",
            OpenAIClient.detectUnknownFieldFromText(
                text = openAiMsg,
                sentOptionalParams = setOf("max_tokens", "temperature")
            )
        )

        val tempMsg = "Unsupported value: 'temperature' does not support 0.2 with this model."
        assertEquals(
            "temperature",
            OpenAIClient.detectUnknownFieldFromText(
                text = tempMsg,
                sentOptionalParams = setOf("temperature", "max_completion_tokens")
            )
        )

        // Если поле reasoning вообще не отправлялось, ошибка не должна запускать бесполезные ретраи.
        assertNull(
            OpenAIClient.detectUnknownFieldFromText(
                text = "Invalid reasoning budget",
                sentOptionalParams = setOf("temperature", "max_tokens")
            )
        )
    }

    @Test
    fun resolvePingAndModelsEndpointsStripChatCompletions() {
        assertEquals(
            "https://api.openai.com/v1/models",
            OpenAIClient.resolvePingUrl("https://api.openai.com/v1/chat/completions")
        )
        assertEquals(
            "https://api.openai.com/v1/models",
            ModelCatalog.resolveModelsEndpoint("https://api.openai.com/v1/chat/completions")
        )
        assertEquals(
            "https://api.groq.com/openai/v1/models",
            OpenAIClient.resolvePingUrl("https://api.groq.com/openai/v1")
        )
    }

    @Test
    fun streamUsageHostMatchesExactDomainOnly() {
        assertTrue(OpenAIClient.isKnownStreamUsageHost("https://api.openai.com/v1/chat/completions"))
        assertTrue(OpenAIClient.isKnownStreamUsageHost("https://generativelanguage.googleapis.com/v1beta/openai/chat/completions"))
        assertFalse(OpenAIClient.isKnownStreamUsageHost("https://evil.example/openai.com/v1/chat/completions"))
        assertFalse(OpenAIClient.isKnownStreamUsageHost("https://notopenai.com/v1/chat/completions"))
    }

    @Test
    fun nextToolCallSlotKeyAvoidsCollisionWithNonSequentialIndices() {
        assertEquals(0, OpenAIClient.nextToolCallSlotKey(emptyList()))
        assertEquals(2, OpenAIClient.nextToolCallSlotKey(listOf(1)))
        assertEquals(5, OpenAIClient.nextToolCallSlotKey(listOf(0, 4)))
    }

    @Test
    fun watchdogTimeoutRespectsHigherUserResponseTimeout() {
        assertEquals(90_000L, OpenAIClient.computeStreamingWatchdogTimeoutMs(60))
        assertEquals(180_000L, OpenAIClient.computeStreamingWatchdogTimeoutMs(180))
        assertEquals(300_000L, OpenAIClient.computeStreamingWatchdogTimeoutMs(300))
    }

    // --- GenerationMetrics ---

    @Test
    fun generationMetricsResetAndThinkingSpeedCalculation() {
        val metrics = GenerationMetrics()
        metrics.promptTokens = 100
        metrics.completionTokens = 200
        metrics.finishReason = "stop"
        metrics.noteReasoning("thinking...", now = 1_000L)
        metrics.noteContent(now = 10_800L)
        metrics.noteContent(now = 11_000L)
        metrics.finish()

        // 200 токенов за 10 с (включая reasoning), а не за 0.2 с текста (= 1000 tok/s).
        val tps = metrics.tokensPerSecond
        assertNotNull(tps)
        assertEquals(20.0, tps!!, 0.5)
        assertEquals(0L, metrics.reasoningMillis)
        assertEquals("thinking...", metrics.reasoningText)

        metrics.reset()
        assertNull(metrics.promptTokens)
        assertNull(metrics.completionTokens)
        assertNull(metrics.finishReason)
        assertNull(metrics.tokensPerSecond)
        assertEquals("", metrics.reasoningText)
        assertEquals(0L, metrics.reasoningMillis)
    }

    // --- ConnectionDiagnosis ---

    @Test
    fun connectionDiagnosisDoesNotMisclassify5xxByLatencyDigits() {
        val res503With404Ms = ConnectionTestResult(
            isSuccess = false,
            isReachable = true,
            statusCode = 503,
            latencyMs = 404L,
            message = "API: Сервер ответил ошибкой · HTTP 503 · 404 мс",
            targetEndpoint = "https://api.example.com/v1/models"
        )
        val diagnosis = ConnectionDiagnosis.of(res503With404Ms)
        assertEquals("Провайдер недоступен", diagnosis?.title)
    }

    @Test
    fun connectionDiagnosisHandles402And451AndInvalidUrl() {
        val res402 = ConnectionTestResult(false, true, 402, 100L, "HTTP 402", "https://openrouter.ai/api/v1/models")
        assertEquals("Недостаточно средств", ConnectionDiagnosis.of(res402)?.title)

        val res451 = ConnectionTestResult(false, true, 451, 100L, "HTTP 451", "https://api.example.com/v1/models")
        val diag451 = ConnectionDiagnosis.of(res451)
        assertEquals("Доступ ограничен по региону", diag451?.title)
        assertTrue(diag451?.vpnSuspected == true)

        val badUrl = ConnectionTestResult(
            isSuccess = false,
            isReachable = false,
            statusCode = null,
            latencyMs = 0L,
            message = "Некорректный адрес URL или заголовки: expected scheme",
            targetEndpoint = "api.openai.com/v1/models"
        )
        val diagUrl = ConnectionDiagnosis.of(badUrl)
        assertEquals("Неверный адрес или заголовки", diagUrl?.title)
        assertFalse(diagUrl?.vpnSuspected == true)
    }

    // --- ProviderBalanceChecker & RateLimitStore ---

    @Test
    fun balanceUrlsUseCreditsFirstAndRespectReverseProxy() {
        assertEquals(
            listOf("https://openrouter.ai/api/v1/credits", "https://openrouter.ai/api/v1/key"),
            ProviderBalanceChecker.resolveBalanceUrls("openrouter.ai", "")
        )
        assertEquals(
            listOf("https://my-proxy.example/v1/credits", "https://my-proxy.example/v1/key"),
            ProviderBalanceChecker.resolveBalanceUrls("openrouter.ai", "https://my-proxy.example/v1/")
        )
        assertEquals(
            listOf("https://my-proxy.example/user/balance"),
            ProviderBalanceChecker.resolveBalanceUrls("api.deepseek.com", "https://my-proxy.example/v1")
        )
    }

    @Test
    fun rateLimitStoreFindsLimitsViaReverseProxyHost() {
        val info = RateLimitInfo(remainingRequests = 42, limitRequests = 60)
        RateLimitStore.update("proxy.stage2-test.local:8443", info)
        val found = RateLimitStore.forBaseUrl(
            baseUrl = "https://unseen-upstream.stage2-test.local/v1",
            reverseProxyUrl = "https://proxy.stage2-test.local:8443/v1"
        )
        assertEquals(42, found?.remainingRequests)
    }

    // --- ModelCatalog, ModelAccess, ModelRecommendations ---

    @Test
    fun modelCatalogFiltersNonChatModelsWithoutFalsePositives() {
        assertFalse(ModelCatalog.isChatAndToolModel("meta-llama/llama-guard-4-12b"))
        assertFalse(ModelCatalog.isChatAndToolModel("openai/gpt-oss-safeguard-20b"))
        assertFalse(ModelCatalog.isChatAndToolModel("canopylabs/orpheus-v1-english"))
        assertFalse(ModelCatalog.isChatAndToolModel("nvidia/nemotron-3.5-content-safety:free"))
        assertFalse(ModelCatalog.isChatAndToolModel("deepseek/deepseek-v4.1-flash-1017:batch"))
        assertFalse(ModelCatalog.isChatAndToolModel("gemini-decision-1"))
        assertFalse(ModelCatalog.isChatAndToolModel("gemini-nano-banana-2.1"))
        assertFalse(ModelCatalog.isChatAndToolModel("gemini-omni-1.2-flash"))
        assertFalse(ModelCatalog.isChatAndToolModel("gemini-3.5-transcribe"))
        assertFalse(ModelCatalog.isChatAndToolModel("models/genie-4"))
        assertFalse(ModelCatalog.isChatAndToolModel("gemini-3.8-live"))
        assertFalse(ModelCatalog.isChatAndToolModel("models/aqa"))

        assertTrue(ModelCatalog.isChatAndToolModel("gemini-4-hydrogen"))
        assertTrue(ModelCatalog.isChatAndToolModel("deepseek-flash"))
        assertTrue(ModelCatalog.isChatAndToolModel("qwen/qwen3.8-27b"))
        assertTrue(ModelCatalog.isChatAndToolModel("nvidia/nemotron-3-nano-omni-30b-a3b-reasoning:free"))
        assertTrue(ModelCatalog.isChatAndToolModel("custom/deliver-chat-v1"))
    }

    @Test
    fun modelAccessHandlesCurrentFreeTiersAndBlocksNonChat() {
        assertFalse(ModelAccess.isFreeWithLimits("groq", "meta-llama/llama-guard-4-12b"))
        assertFalse(ModelAccess.isFreeWithLimits("groq", "openai/gpt-oss-safeguard-20b"))
        assertFalse(ModelAccess.isFreeWithLimits("groq", "canopylabs/orpheus-v1-english"))
        assertTrue(ModelAccess.isFreeWithLimits("groq", "qwen/qwen3.8-27b"))

        assertTrue(ModelAccess.isFreeWithLimits("openrouter", "openrouter/free"))
        assertTrue(ModelAccess.isFreeWithLimits("openrouter", "nvidia/nemotron-3-ultra-550b-a55b:free"))
        assertFalse(ModelAccess.isFreeWithLimits("openrouter", "nvidia/nemotron-3.5-content-safety:free"))
        assertFalse(ModelAccess.isFreeWithLimits("openrouter", "nvidia/nemotron-3.5-rerank-8b:free"))
        assertFalse(ModelAccess.isFreeWithLimits("openrouter", "vendor/model:free-preview"))

        assertTrue(ModelAccess.isFreeWithLimits("gemini", "gemini-4-hydrogen"))
        assertTrue(ModelAccess.isFreeWithLimits("gemini", "gemini-3.5-flash-lite"))
        assertTrue(ModelAccess.isFreeWithLimits("gemini", "gemini-3.8-flash"))
        assertFalse(ModelAccess.isFreeWithLimits("gemini", "gemini-4-neon"))
        assertFalse(ModelAccess.isFreeWithLimits("gemini", "gemini-4-carbon"))
        assertFalse(ModelAccess.isFreeWithLimits("gemini", "gemini-3.8-live"))
    }

    @Test
    fun modelRecommendationsMatchesCurrentApiIdsAndCaseInsensitiveProvider() {
        val deepSeekModels = listOf(
            ModelInfo("deepseek-v4-pro"),
            ModelInfo("deepseek-flash")
        )
        assertEquals("deepseek-flash", ModelRecommendations.preferred("DeepSeek", deepSeekModels))

        val groqModels = listOf(
            ModelInfo("qwen/qwen3.8-27b"),
            ModelInfo("openai/gpt-oss-120b")
        )
        assertEquals("openai/gpt-oss-120b", ModelRecommendations.preferred("groq", groqModels))

        val geminiModels = listOf(
            ModelInfo("models/gemini-2.5-flash"),
            ModelInfo("models/gemini-4-hydrogen"),
            ModelInfo("models/gemini-3.5-flash-lite")
        )
        assertEquals("models/gemini-4-hydrogen", ModelRecommendations.preferred("gemini", geminiModels))

        val openAiModels = listOf(
            ModelInfo("gpt-6-luna"),
            ModelInfo("gpt-5.6-luna"),
            ModelInfo("gpt-4o")
        )
        assertEquals("gpt-5.6-luna", ModelRecommendations.preferred("openai", openAiModels))

        val openRouterModels = listOf(
            ModelInfo("other/model"),
            ModelInfo("nvidia/nemotron-3-ultra-550b-a55b:free"),
            ModelInfo("deepseek/deepseek-v4.1-flash-1017")
        )
        assertEquals(
            "nvidia/nemotron-3-ultra-550b-a55b:free",
            ModelRecommendations.preferred("openrouter", openRouterModels)
        )

        val togetherModels = listOf(
            ModelInfo("Qwen/Qwen3.8-Flash"),
            ModelInfo("zai-org/GLM-5.3-Flash"),
            ModelInfo("deepseek-ai/DeepSeek-V4.1-Flash-1017")
        )
        assertEquals("zai-org/GLM-5.3-Flash", ModelRecommendations.preferred("together", togetherModels))
    }
}
