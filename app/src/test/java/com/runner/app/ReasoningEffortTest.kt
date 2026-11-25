package com.runner.app

import com.runner.app.data.ModelCatalog
import com.runner.app.data.ModelInfo
import com.runner.app.data.OpenAIClient
import com.runner.app.data.Provider
import com.runner.app.data.ProviderKind
import com.runner.app.data.ReasoningEffort
import com.runner.app.data.ReasoningEffortPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReasoningEffortTest {

    @Test
    fun parseReasoningSpec_handlesOpenRouterExplicitEffortsAndMandatoryFlag() {
        val deepSeekFlash = ModelCatalog.parseReasoningSpec(
            supportedParameters = setOf("reasoning", "reasoning_effort", "tools"),
            hasReasoningField = true,
            hasReasoningObject = true,
            reasoningMandatory = false,
            reasoningDefaultEnabled = true,
            rawSupportedEfforts = listOf("max", "high", "low", "none"),
            rawDefaultEffort = "high"
        )
        assertTrue(deepSeekFlash.metadataKnown)
        assertEquals(
            listOf(ReasoningEffort.NONE, ReasoningEffort.LOW, ReasoningEffort.HIGH, ReasoningEffort.MAX),
            deepSeekFlash.supportedEfforts
        )
        assertEquals(ReasoningEffort.HIGH, deepSeekFlash.defaultEffort)

        val claudeMandatory = ModelCatalog.parseReasoningSpec(
            supportedParameters = setOf("reasoning", "reasoning_effort"),
            hasReasoningField = true,
            hasReasoningObject = true,
            reasoningMandatory = true,
            reasoningDefaultEnabled = true,
            rawSupportedEfforts = listOf("max", "xhigh", "high", "medium", "low"),
            rawDefaultEffort = "high"
        )
        assertEquals(
            listOf(
                ReasoningEffort.LOW,
                ReasoningEffort.MEDIUM,
                ReasoningEffort.HIGH,
                ReasoningEffort.XHIGH,
                ReasoningEffort.MAX
            ),
            claudeMandatory.supportedEfforts
        )
        assertEquals(ReasoningEffort.HIGH, claudeMandatory.defaultEffort)

        val qwenWithMinimal = ModelCatalog.parseReasoningSpec(
            supportedParameters = setOf("reasoning", "reasoning_effort"),
            hasReasoningField = true,
            hasReasoningObject = true,
            reasoningMandatory = true,
            reasoningDefaultEnabled = true,
            rawSupportedEfforts = listOf("xhigh", "high", "medium", "low", "minimal"),
            rawDefaultEffort = "xhigh"
        )
        assertEquals(
            listOf(
                ReasoningEffort.MINIMAL,
                ReasoningEffort.LOW,
                ReasoningEffort.MEDIUM,
                ReasoningEffort.HIGH,
                ReasoningEffort.XHIGH
            ),
            qwenWithMinimal.supportedEfforts
        )
        assertEquals(ReasoningEffort.XHIGH, qwenWithMinimal.defaultEffort)
    }

    @Test
    fun parseReasoningSpec_handlesMissingEffortsArrayAndNonReasoningModels() {
        val o3Mandatory = ModelCatalog.parseReasoningSpec(
            supportedParameters = setOf("include_reasoning", "reasoning", "tools"),
            hasReasoningField = true,
            hasReasoningObject = true,
            reasoningMandatory = true,
            reasoningDefaultEnabled = null,
            rawSupportedEfforts = null,
            rawDefaultEffort = null
        )
        assertEquals(
            listOf(ReasoningEffort.LOW, ReasoningEffort.MEDIUM, ReasoningEffort.HIGH),
            o3Mandatory.supportedEfforts
        )
        assertEquals(ReasoningEffort.MEDIUM, o3Mandatory.defaultEffort)

        val llamaNonReasoning = ModelCatalog.parseReasoningSpec(
            supportedParameters = setOf("temperature", "tools", "top_p"),
            hasReasoningField = true,
            hasReasoningObject = false
        )
        assertTrue(llamaNonReasoning.metadataKnown)
        assertTrue(llamaNonReasoning.supportedEfforts.isEmpty())
        assertNull(llamaNonReasoning.defaultEffort)

        val directOpenAiNoMetadata = ModelCatalog.parseReasoningSpec(
            supportedParameters = null,
            hasReasoningField = false,
            hasReasoningObject = false
        )
        assertFalse(directOpenAiNoMetadata.metadataKnown)
        assertTrue(directOpenAiNoMetadata.supportedEfforts.isEmpty())
    }

    @Test
    fun policy_hidesPillWhenNoneOrUnsupportedAndShowsWhenActive() {
        val deepSeekModel = ModelInfo(
            id = "deepseek/deepseek-v4.1-flash-1017",
            name = "DeepSeek V4.1 Flash 1017",
            supportedReasoningEfforts = listOf(
                ReasoningEffort.NONE,
                ReasoningEffort.LOW,
                ReasoningEffort.HIGH,
                ReasoningEffort.MAX
            ),
            defaultReasoningEffort = ReasoningEffort.HIGH,
            reasoningMetadataKnown = true
        )
        val provider = Provider(
            id = "openrouter",
            name = "OpenRouter",
            baseUrl = "https://openrouter.ai/api/v1",
            apiKey = "sk-test",
            models = listOf(deepSeekModel),
            selectedModel = deepSeekModel.id
        )

        val autoResolved = ReasoningEffortPolicy.resolve(provider, savedEffort = "")
        assertTrue(autoResolved.isSupported)
        assertTrue(autoResolved.showPill)
        assertTrue(autoResolved.canDisable)
        assertEquals(ReasoningEffort.HIGH, autoResolved.activeEffort)

        val noneResolved = ReasoningEffortPolicy.resolve(provider, savedEffort = "none")
        assertTrue(noneResolved.isSupported)
        assertFalse("Effort pill must be hidden when effort is NONE", noneResolved.showPill)
        assertEquals(ReasoningEffort.NONE, noneResolved.activeEffort)

        val unsupportedModel = ModelInfo(
            id = "meta-llama/llama-3.3-70b-instruct",
            reasoningMetadataKnown = true
        )
        val unsupported = ReasoningEffortPolicy.resolveForModel(
            modelId = unsupportedModel.id,
            modelInfo = unsupportedModel,
            baseUrl = "https://openrouter.ai/api/v1",
            savedEffort = "high"
        )
        assertFalse(unsupported.isSupported)
        assertFalse(unsupported.showPill)
        assertNull(unsupported.activeEffort)
    }

    @Test
    fun policy_clampsGracefullyAcrossModelsAndRespectsMandatoryReasoning() {
        val mandatoryModel = ModelInfo(
            id = "anthropic/claude-fable-5.2",
            supportedReasoningEfforts = listOf(
                ReasoningEffort.LOW,
                ReasoningEffort.MEDIUM,
                ReasoningEffort.HIGH
            ),
            defaultReasoningEffort = ReasoningEffort.MEDIUM,
            reasoningMetadataKnown = true
        )

        val clampedFromXHigh = ReasoningEffortPolicy.resolveForModel(
            modelId = mandatoryModel.id,
            modelInfo = mandatoryModel,
            savedEffort = "xhigh"
        )
        assertEquals(ReasoningEffort.HIGH, clampedFromXHigh.activeEffort)
        assertTrue(clampedFromXHigh.isMandatory)
        assertFalse(clampedFromXHigh.canDisable)

        val clampedFromNone = ReasoningEffortPolicy.resolveForModel(
            modelId = mandatoryModel.id,
            modelInfo = mandatoryModel,
            savedEffort = "none"
        )
        assertEquals(ReasoningEffort.MEDIUM, clampedFromNone.activeEffort)
        assertTrue(clampedFromNone.showPill)

        val deepSeekNoMedium = ModelInfo(
            id = "deepseek/deepseek-v4.1-flash-1017",
            supportedReasoningEfforts = listOf(
                ReasoningEffort.NONE,
                ReasoningEffort.LOW,
                ReasoningEffort.HIGH,
                ReasoningEffort.MAX
            ),
            defaultReasoningEffort = ReasoningEffort.HIGH,
            reasoningMetadataKnown = true
        )
        val clampedMedium = ReasoningEffortPolicy.resolveForModel(
            modelId = deepSeekNoMedium.id,
            modelInfo = deepSeekNoMedium,
            savedEffort = "medium"
        )
        assertEquals(ReasoningEffort.HIGH, clampedMedium.activeEffort)
    }

    @Test
    fun policy_preferredEnableEffortRestoresLastNonNoneOrDefault() {
        val model = ModelInfo(
            id = "openai/gpt-5.6-luna",
            supportedReasoningEfforts = listOf(
                ReasoningEffort.NONE,
                ReasoningEffort.LOW,
                ReasoningEffort.MEDIUM,
                ReasoningEffort.HIGH,
                ReasoningEffort.XHIGH
            ),
            defaultReasoningEffort = ReasoningEffort.MEDIUM,
            reasoningMetadataKnown = true
        )
        val resolvedNone = ReasoningEffortPolicy.resolveForModel(
            modelId = model.id,
            modelInfo = model,
            savedEffort = "none"
        )
        assertEquals(
            ReasoningEffort.XHIGH,
            ReasoningEffortPolicy.preferredEnableEffort(resolvedNone, lastNonNone = ReasoningEffort.XHIGH)
        )
        assertEquals(
            ReasoningEffort.MEDIUM,
            ReasoningEffortPolicy.preferredEnableEffort(resolvedNone, lastNonNone = null)
        )
    }

    @Test
    fun policy_directProviderFallbacksAvoidFalsePositivesAndFakeDeepSeekTiers() {
        val directDeepSeek = ReasoningEffortPolicy.resolveForModel(
            modelId = "deepseek-reasoner",
            modelInfo = ModelInfo("deepseek-reasoner"),
            baseUrl = "https://api.deepseek.com/v1",
            providerKind = ProviderKind.DEEPSEEK
        )
        assertFalse(directDeepSeek.isSupported)

        val directOpenAiGpt5 = ReasoningEffortPolicy.resolveForModel(
            modelId = "gpt-5",
            modelInfo = ModelInfo("gpt-5"),
            baseUrl = "https://api.openai.com/v1"
        )
        assertTrue(directOpenAiGpt5.isSupported)
        assertEquals(ReasoningEffort.NONE, directOpenAiGpt5.activeEffort)
        assertFalse(directOpenAiGpt5.showPill)

        val directOpenAiO3 = ReasoningEffortPolicy.resolveForModel(
            modelId = "o3-mini",
            modelInfo = ModelInfo("o3-mini"),
            baseUrl = "https://api.openai.com/v1"
        )
        assertTrue(directOpenAiO3.isSupported)
        assertTrue(directOpenAiO3.isMandatory)
        assertEquals(ReasoningEffort.MEDIUM, directOpenAiO3.activeEffort)
        assertTrue(directOpenAiO3.showPill)

        val falsePositiveO10 = ReasoningEffortPolicy.resolveForModel(
            modelId = "o10-experimental",
            modelInfo = ModelInfo("o10-experimental"),
            baseUrl = "https://api.openai.com/v1"
        )
        assertFalse(falsePositiveO10.isSupported)

        val directGeminiFlash = ReasoningEffortPolicy.resolveForModel(
            modelId = "gemini-2.5-flash",
            modelInfo = ModelInfo("gemini-2.5-flash"),
            baseUrl = "https://generativelanguage.googleapis.com/v1beta/openai",
            providerKind = ProviderKind.GEMINI
        )
        assertTrue(directGeminiFlash.isSupported)
        assertEquals(ReasoningEffort.NONE, directGeminiFlash.activeEffort)
    }

    @Test
    fun openAiClient_resolveRequestReasoningMapsEffortPerProviderSafely() {
        val openRouterHigh = OpenAIClient.resolveRequestReasoning(
            isDirectOfficialDeepSeek = false,
            allowThinking = false,
            isOpenRouterHost = true,
            allowReasoning = true,
            allowReasoningEffort = true,
            endpointHost = "openrouter.ai",
            effectiveModel = "deepseek/deepseek-v4.1-flash-1017",
            allowTools = true,
            liveMode = false,
            forceThinking = false,
            reasoningEffort = ReasoningEffort.MAX
        )
        assertEquals("max", openRouterHigh.openRouterEffort)
        assertNull(openRouterHigh.reasoningEffort)
        assertNull(openRouterHigh.deepSeekThinkingEnabled)

        val openRouterNone = OpenAIClient.resolveRequestReasoning(
            isDirectOfficialDeepSeek = false,
            allowThinking = false,
            isOpenRouterHost = true,
            allowReasoning = true,
            allowReasoningEffort = true,
            endpointHost = "openrouter.ai",
            effectiveModel = "deepseek/deepseek-v4.1-flash-1017",
            allowTools = true,
            liveMode = false,
            forceThinking = false,
            reasoningEffort = ReasoningEffort.NONE
        )
        assertEquals("none", openRouterNone.openRouterEffort)

        val openAiO3 = OpenAIClient.resolveRequestReasoning(
            isDirectOfficialDeepSeek = false,
            allowThinking = false,
            isOpenRouterHost = false,
            allowReasoning = true,
            allowReasoningEffort = true,
            endpointHost = "api.openai.com",
            effectiveModel = "o3-mini",
            allowTools = true,
            liveMode = false,
            forceThinking = false,
            reasoningEffort = ReasoningEffort.HIGH
        )
        assertEquals("high", openAiO3.reasoningEffort)
        assertNull(openAiO3.openRouterEffort)

        val openAiGpt6ToolsGuard = OpenAIClient.resolveRequestReasoning(
            isDirectOfficialDeepSeek = false,
            allowThinking = false,
            isOpenRouterHost = false,
            allowReasoning = true,
            allowReasoningEffort = true,
            endpointHost = "api.openai.com",
            effectiveModel = "gpt-6-luna",
            allowTools = true,
            liveMode = false,
            forceThinking = false,
            reasoningEffort = ReasoningEffort.HIGH
        )
        assertEquals("none", openAiGpt6ToolsGuard.reasoningEffort)

        val directDeepSeek = OpenAIClient.resolveRequestReasoning(
            isDirectOfficialDeepSeek = true,
            allowThinking = true,
            isOpenRouterHost = false,
            allowReasoning = true,
            allowReasoningEffort = true,
            endpointHost = "api.deepseek.com",
            effectiveModel = "deepseek-chat",
            allowTools = true,
            liveMode = false,
            forceThinking = true,
            reasoningEffort = null
        )
        assertEquals(true, directDeepSeek.deepSeekThinkingEnabled)
        assertNull(directDeepSeek.reasoningEffort)
        assertNull(directDeepSeek.openRouterEffort)

        val nonReasoning = OpenAIClient.resolveRequestReasoning(
            isDirectOfficialDeepSeek = false,
            allowThinking = false,
            isOpenRouterHost = true,
            allowReasoning = true,
            allowReasoningEffort = true,
            endpointHost = "openrouter.ai",
            effectiveModel = "meta-llama/llama-3.3-70b-instruct",
            allowTools = true,
            liveMode = false,
            forceThinking = false,
            reasoningEffort = null
        )
        assertNull(nonReasoning.deepSeekThinkingEnabled)
        assertNull(nonReasoning.openRouterEffort)
        assertNull(nonReasoning.reasoningEffort)
    }
}
