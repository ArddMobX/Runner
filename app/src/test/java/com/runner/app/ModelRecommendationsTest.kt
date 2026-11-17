package com.runner.app

import com.runner.app.data.ModelInfo
import com.runner.app.data.ModelRecommendations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ModelRecommendationsTest {

    @Test
    fun openRouterPrefersKnownFreeModel() {
        val models = listOf(
            ModelInfo("other/model"),
            ModelInfo("qwen/qwen3.8-27b:free"),
            ModelInfo("deepseek/deepseek-v4.1-flash")
        )
        assertEquals(
            "qwen/qwen3.8-27b:free",
            ModelRecommendations.preferred("openrouter", models)
        )
    }

    @Test
    fun unknownProviderFallsBackToToolModel() {
        val models = listOf(
            ModelInfo("text-embedding-3-small"),
            ModelInfo("gpt-4o")
        )
        assertEquals("gpt-4o", ModelRecommendations.preferred("custom", models))
    }

    @Test
    fun emptyListReturnsNull() {
        assertNull(ModelRecommendations.preferred("openrouter", emptyList()))
    }
}
