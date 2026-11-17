package com.runner.app

import com.runner.app.util.SecretRedactor
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecretRedactorTest {

    @Test
    fun masksProviderKeys() {
        val text = "key=sk-or-v1-1234567890abcdef and groq=gsk_1234567890abcdefghi " +
                "gemini=AIzaSyA1234567890abcdefghijklmnop"
        val redacted = SecretRedactor.redact(text)
        assertFalse(redacted.contains("sk-or-v1-1234567890abcdef"))
        assertFalse(redacted.contains("gsk_1234567890abcdefghi"))
        assertFalse(redacted.contains("AIzaSyA1234567890abcdefghijklmnop"))
    }

    @Test
    fun masksAuthorizationHeaders() {
        val text = "Authorization: Bearer abcdefghijklmnopqrstuvwxyz\nX-Api-Key: secret123"
        val redacted = SecretRedactor.redact(text)
        assertFalse(redacted.contains("abcdefghijklmnopqrstuvwxyz"))
        assertFalse(redacted.contains("secret123"))
        assertTrue(redacted.contains("Authorization: ***"))
    }

    @Test
    fun keepsOrdinaryText() {
        val text = "Проверка соединения прошла за 120 мс."
        assertTrue(SecretRedactor.redact(text) == text)
    }
}
