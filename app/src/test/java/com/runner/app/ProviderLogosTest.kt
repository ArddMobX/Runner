package com.runner.app

import androidx.compose.ui.graphics.vector.VectorPath
import com.runner.app.ui.components.ProviderLogos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderLogosTest {

    @Test
    fun allBrandVectorsBuildAndParseNonEmptyPaths() {
        val allLogos = listOf(
            ProviderLogos.OpenAI,
            ProviderLogos.Gemini,
            ProviderLogos.Claude,
            ProviderLogos.DeepSeek,
            ProviderLogos.Groq,
            ProviderLogos.Meta,
            ProviderLogos.Mistral,
            ProviderLogos.OpenRouter,
            ProviderLogos.Qwen,
            ProviderLogos.Perplexity,
            ProviderLogos.TokenHarbor,
            ProviderLogos.CleanApi,
            ProviderLogos.AbniBots,
            ProviderLogos.ProxyApi,
            ProviderLogos.VseGpt,
            ProviderLogos.Together,
            ProviderLogos.Grok,
            ProviderLogos.Nvidia,
            ProviderLogos.Zai,
            ProviderLogos.Kimi,
            ProviderLogos.MiniMax,
            ProviderLogos.Cohere,
            ProviderLogos.Microsoft,
            ProviderLogos.Amazon,
            ProviderLogos.ByteDance,
            ProviderLogos.Xiaomi,
            ProviderLogos.StepFun,
            ProviderLogos.IBM,
            ProviderLogos.Ollama,
            ProviderLogos.Fireworks,
            ProviderLogos.Cerebras,
            ProviderLogos.DeepInfra,
            ProviderLogos.HuggingFace
        )

        allLogos.forEach { logo ->
            assertTrue("Logo name should not be blank", logo.name.isNotBlank())
            assertTrue("Logo ${logo.name} should have at least one path", logo.root.size > 0)
            for (i in 0 until logo.root.size) {
                val node = logo.root[i]
                assertTrue("Logo ${logo.name}[$i] should be VectorPath", node is VectorPath)
                assertTrue(
                    "Logo ${logo.name}[$i] pathData should not be empty",
                    (node as VectorPath).pathData.isNotEmpty()
                )
            }
        }
    }

    @Test
    fun forModelOrProvider_mapsNewAndCompositeModelIds() {
        assertSame(ProviderLogos.Grok, ProviderLogos.forModelOrProvider("x-ai/grok-4"))
        assertSame(ProviderLogos.Grok, ProviderLogos.forModelOrProvider("grok-code-fast-1"))
        assertSame(ProviderLogos.Groq, ProviderLogos.forModelOrProvider("groq/compound"))
        assertSame(ProviderLogos.Nvidia, ProviderLogos.forModelOrProvider("nvidia/llama-3.1-nemotron-70b-instruct"))
        assertSame(ProviderLogos.Zai, ProviderLogos.forModelOrProvider("zai-org/GLM-4.7"))
        assertSame(ProviderLogos.Zai, ProviderLogos.forModelOrProvider("z-ai/glm-4.5-air:free"))
        assertSame(ProviderLogos.Kimi, ProviderLogos.forModelOrProvider("moonshotai/kimi-k2-thinking"))
        assertSame(ProviderLogos.MiniMax, ProviderLogos.forModelOrProvider("minimax/minimax-m1"))
        assertSame(ProviderLogos.Cohere, ProviderLogos.forModelOrProvider("cohere/command-a"))
        assertSame(ProviderLogos.Microsoft, ProviderLogos.forModelOrProvider("microsoft/phi-4-reasoning"))
        assertSame(ProviderLogos.Microsoft, ProviderLogos.forModelOrProvider("phi-3.5-mini-instruct"))
        assertSame(ProviderLogos.Amazon, ProviderLogos.forModelOrProvider("amazon/nova-pro-v1"))
        assertSame(ProviderLogos.ByteDance, ProviderLogos.forModelOrProvider("bytedance/seed-oss-36b"))
        assertSame(ProviderLogos.Xiaomi, ProviderLogos.forModelOrProvider("xiaomi/mimo-v2-flash"))
        assertSame(ProviderLogos.StepFun, ProviderLogos.forModelOrProvider("stepfun/step-3.5-flash"))
        assertSame(ProviderLogos.IBM, ProviderLogos.forModelOrProvider("ibm-granite/granite-4.0-h-small"))
        assertSame(ProviderLogos.DeepSeek, ProviderLogos.forModelOrProvider("deepseek/deepseek-r1-distill-qwen-32b"))
        assertSame(ProviderLogos.Mistral, ProviderLogos.forModelOrProvider("mistralai/devstral-small"))
        assertSame(ProviderLogos.OpenRouter, ProviderLogos.forModelOrProvider("openrouter/auto"))
        assertSame(ProviderLogos.OpenAI, ProviderLogos.forModelOrProvider("openai/o1"))
        assertSame(ProviderLogos.OpenAI, ProviderLogos.forModelOrProvider("o3-mini"))
        assertSame(ProviderLogos.OpenAI, ProviderLogos.forModelOrProvider("o4-mini-high"))
    }

    @Test
    fun forModelOrProvider_doesNotFalsePositiveShortModelTokens() {
        assertNull(ProviderLogos.forModelOrProvider("vendor/demo1-chat"))
        assertNull(ProviderLogos.forModelOrProvider("vendor/pro3-instruct"))
        assertNull(ProviderLogos.forModelOrProvider("vendor/hero4-preview"))
        assertNull(ProviderLogos.forModelOrProvider("vendor/o10-large"))
        assertNull(ProviderLogos.forModelOrProvider("cognitivecomputations/dolphin-2.9"))
        assertNull(ProviderLogos.forModelOrProvider("novita/custom-supernova"))
        assertNull(ProviderLogos.forModelOrProvider("arcee-ai/trinity-large-preview:free"))
    }

    @Test
    fun forProvider_detectsBrandFromBaseUrlEvenWhenRenamed() {
        assertSame(
            ProviderLogos.Grok,
            ProviderLogos.forProvider("custom_1", "Мой провайдер", "https://api.x.ai/v1")
        )
        assertSame(
            ProviderLogos.Ollama,
            ProviderLogos.forProvider("custom_2", "Домашний ПК", "http://192.168.1.10:11434/v1")
        )
        assertSame(
            ProviderLogos.Ollama,
            ProviderLogos.forProvider("ollama", "Ollama Local", "http://localhost:8080/v1")
        )
        assertSame(
            ProviderLogos.Fireworks,
            ProviderLogos.forProvider("custom_3", "Быстрый API", "https://api.fireworks.ai/inference/v1")
        )
        assertSame(
            ProviderLogos.Cerebras,
            ProviderLogos.forProvider("custom_4", "Fast", "https://api.cerebras.ai/v1")
        )
        assertSame(
            ProviderLogos.DeepInfra,
            ProviderLogos.forProvider("custom_5", "Cloud", "https://api.deepinfra.com/v1/openai")
        )
        assertSame(
            ProviderLogos.HuggingFace,
            ProviderLogos.forProvider("custom_6", "HF", "https://router.huggingface.co/v1")
        )
        assertSame(
            ProviderLogos.Nvidia,
            ProviderLogos.forProvider("custom_7", "NIM", "https://integrate.api.nvidia.com/v1")
        )
        assertSame(
            ProviderLogos.Zai,
            ProviderLogos.forProvider("custom_8", "GLM", "https://open.bigmodel.cn/api/paas/v4")
        )
        assertNull(
            ProviderLogos.forProvider("custom_9", "Мой прокси", "https://llm.example.com/v1")
        )
    }

    @Test
    fun forModelOrProvider_fallsBackToProviderBaseUrlForUnknownModel() {
        val logo = ProviderLogos.forModelOrProvider(
            modelId = "my-local-gguf:latest",
            providerId = "custom_home",
            providerName = "Домашний сервер",
            baseUrl = "http://192.168.0.5:11434/v1"
        )
        assertNotNull(logo)
        assertSame(ProviderLogos.Ollama, logo)
    }

    @Test
    fun monogramFor_extractsCleanVendorAndProviderMonograms() {
        assertEquals("AR", ProviderLogos.monogramFor("arcee-ai/trinity-large-preview:free"))
        assertEquals("NO", ProviderLogos.monogramFor("nousresearch/hermes-3-llama-3.1-405b"))
        assertEquals("AI", ProviderLogos.monogramFor("ai21/jamba-1.5-large"))
        assertEquals("01", ProviderLogos.monogramFor("01-ai/yi-large"))
        assertEquals("LI", ProviderLogos.monogramFor("liquid/lfm-40b"))
        assertEquals("МП", ProviderLogos.monogramFor(providerName = "Мой прокси"))
        assertEquals("NO", ProviderLogos.monogramFor(baseUrl = "https://api.novita.ai/v3/openai"))
        assertEquals("AI", ProviderLogos.monogramFor())
    }
}
