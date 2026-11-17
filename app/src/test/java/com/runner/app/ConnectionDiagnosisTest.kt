package com.runner.app

import com.runner.app.data.ConnectionDiagnosis
import com.runner.app.data.ConnectionTestResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionDiagnosisTest {

    private fun result(
        code: Int? = null,
        reachable: Boolean = true,
        message: String = ""
    ) = ConnectionTestResult(
        isSuccess = false,
        isReachable = reachable,
        statusCode = code,
        latencyMs = 12L,
        message = message,
        targetEndpoint = "https://example.com/models"
    )

    @Test
    fun authErrorPointsToKey() {
        val diagnosis = ConnectionDiagnosis.of(result(code = 401))
        assertEquals("Ключ не подошёл", diagnosis?.title)
    }

    @Test
    fun forbiddenMentionsVpn() {
        val diagnosis = ConnectionDiagnosis.of(result(code = 403))
        assertEquals("Доступ ограничен", diagnosis?.title)
        assertTrue(diagnosis?.vpnSuspected == true)
    }

    @Test
    fun rateLimitIsSeparateFromBalance() {
        val diagnosis = ConnectionDiagnosis.of(result(code = 429))
        assertEquals("Лимит запросов", diagnosis?.title)
    }

    @Test
    fun timeoutMentionsVpn() {
        val diagnosis = ConnectionDiagnosis.of(
            result(reachable = false, message = "Таймаут (15 с), сервер не ответил")
        )
        assertEquals("Нет ответа", diagnosis?.title)
        assertTrue(diagnosis?.vpnSuspected == true)
    }

    @Test
    fun successHasNoDiagnosis() {
        val success = result().copy(isSuccess = true)
        assertNull(ConnectionDiagnosis.of(success))
    }
}
