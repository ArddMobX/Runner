package com.runner.app

import com.runner.app.data.ModelAccess
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelAccessTest {

    @Test
    fun openRouterFreeOnlyWhenMarked() {
        assertTrue(ModelAccess.isFreeWithLimits("openrouter", "qwen/qwen3.8-27b:free"))
        assertFalse(ModelAccess.isFreeWithLimits("openrouter", "deepseek/deepseek-v4.1-flash"))
        assertFalse(ModelAccess.isFreeWithLimits("openrouter", "openai/gpt-5.2-free-trial"))
    }

    @Test
    fun geminiUsesKnownFreeFamilies() {
        assertTrue(ModelAccess.isFreeWithLimits("gemini", "gemini-2.5-flash"))
        assertTrue(ModelAccess.isFreeWithLimits("gemini", "gemini-2.5-flash-lite"))
        assertTrue(ModelAccess.isFreeWithLimits("gemini", "gemini-2.5-pro"))
        assertTrue(ModelAccess.isFreeWithLimits("gemini", "models/gemini-2.0-flash-001"))
        assertFalse(ModelAccess.isFreeWithLimits("gemini", "gemini-custom-paid"))
    }

    @Test
    fun geminiNonChatVariantsAreNotFree() {
        assertFalse(ModelAccess.isFreeWithLimits("gemini", "gemini-2.5-flash-image"))
    }

    @Test
    fun groqKnownFamiliesAreFreeWithLimits() {
        assertTrue(ModelAccess.isFreeWithLimits("groq", "openai/gpt-oss-120b"))
        assertTrue(ModelAccess.isFreeWithLimits("groq", "llama-3.3-70b-versatile"))
        assertTrue(ModelAccess.isFreeWithLimits("groq", "qwen/qwen3-32b"))
        assertFalse(ModelAccess.isFreeWithLimits("groq", "unknown-paid-model"))
    }

    @Test
    fun otherProvidersArePaid() {
        assertFalse(ModelAccess.isFreeWithLimits("deepseek", "deepseek-flash"))
        assertFalse(ModelAccess.isFreeWithLimits("custom", "gpt-4o:free"))
        assertFalse(ModelAccess.isFreeWithLimits("", "qwen/qwen3.8-27b:free"))
    }
}
