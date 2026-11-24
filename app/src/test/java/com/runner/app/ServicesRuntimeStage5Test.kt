package com.runner.app

import com.runner.app.service.AgentExecutionService
import com.runner.app.service.RunnerAccessibilityService
import com.runner.app.service.RunnerRecognitionService
import com.runner.app.tools.TermuxBridge
import com.runner.app.ui.LiveAgentManager
import com.runner.app.util.VoiceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Регрессионные тесты Android-сервисов и runtime (этап 5):
 * счётчик активных задач AgentExecutionService, TermuxBridge (версии и коды ошибок),
 * выбор внешней службы распознавания речи, разбивка длинного текста для TTS,
 * конечный автомат LiveAgentManager и ограничение координат жестов Accessibility.
 */
class ServicesRuntimeStage5Test {

    // --- AgentExecutionService: защита от гонки activeTasks ---

    @Test
    fun agentExecutionService_releaseRaceDoesNotKillConcurrentlyStartedTask() {
        AgentExecutionService.resetActiveTasksForTest()
        try {
            // Задача A стартовала (0 -> 1)
            assertEquals(1, AgentExecutionService.markTaskStarted())
            assertFalse(AgentExecutionService.shouldStopServiceOnRelease())

            // Задача A завершилась (1 -> 0) и поставила ACTION_RELEASE в очередь
            assertTrue(AgentExecutionService.markTaskReleased())

            // Пока ACTION_RELEASE в очереди, задача B стартовала (0 -> 1)
            assertEquals(1, AgentExecutionService.markTaskStarted())

            // Доставка отложенного ACTION_RELEASE НЕ должна гасить службу или обнулять счётчик
            assertFalse(AgentExecutionService.shouldStopServiceOnRelease())
            assertEquals(1, AgentExecutionService.activeTaskCountForTest())

            // Задача B завершилась (1 -> 0) -> теперь службу можно гасить
            assertTrue(AgentExecutionService.markTaskReleased())
            assertTrue(AgentExecutionService.shouldStopServiceOnRelease())
            assertEquals(0, AgentExecutionService.activeTaskCountForTest())

            // Лишний release не уводит счётчик в минус
            assertFalse(AgentExecutionService.markTaskReleased())
            assertEquals(0, AgentExecutionService.activeTaskCountForTest())
        } finally {
            AgentExecutionService.resetActiveTasksForTest()
        }
    }

    // --- TermuxBridge: версии с суффиксами и коды ошибок ---

    @Test
    fun termuxBridge_supportsResultCallback_handlesSuffixVersions() {
        assertTrue(TermuxBridge.supportsResultCallback(null))
        assertTrue(TermuxBridge.supportsResultCallback(""))
        assertTrue(TermuxBridge.supportsResultCallback("0.109"))
        assertTrue(TermuxBridge.supportsResultCallback("0.118.1"))
        assertTrue(TermuxBridge.supportsResultCallback("0.118-beta1"))
        assertTrue(TermuxBridge.supportsResultCallback("0.118+github"))
        assertTrue(TermuxBridge.supportsResultCallback("v0.119.0-rc2"))
        assertTrue(TermuxBridge.supportsResultCallback("1.0-rc1"))

        assertFalse(TermuxBridge.supportsResultCallback("0.108"))
        assertFalse(TermuxBridge.supportsResultCallback("0.108-beta2"))
        assertFalse(TermuxBridge.supportsResultCallback("0.95"))
    }

    @Test
    fun termuxBridge_formatResult_distinguishesSuccessFromMissingBundleAndCancelled() {
        val ok = TermuxBridge.formatResult(
            TermuxBridge.TermuxResult(
                stdout = "hello",
                stderr = "",
                exitCode = 0,
                err = TermuxBridge.ERRNO_SUCCESS,
                errMsg = ""
            ),
            label = "Termux"
        )
        assertTrue(ok.startsWith("Выполнено (код 0) [Termux]:"))
        assertTrue(ok.contains("hello"))

        // Отсутствующий result bundle (ERRNO_FAILED) обязан считаться ошибкой Termux
        val missingBundle = TermuxBridge.formatResult(
            TermuxBridge.TermuxResult(
                stdout = "",
                stderr = "Termux не вернул result bundle",
                exitCode = -1,
                err = TermuxBridge.ERRNO_FAILED,
                errMsg = "Termux не вернул result bundle"
            ),
            label = "Termux"
        )
        assertTrue(missingBundle.startsWith("Ошибка Termux:"))
        assertFalse(missingBundle.startsWith("Выполнено"))

        // err == 0 в Termux — это ERRNO_CANCELLED, а не успех
        val cancelled = TermuxBridge.formatResult(
            TermuxBridge.TermuxResult(
                stdout = "",
                stderr = "",
                exitCode = -1,
                err = 0,
                errMsg = ""
            ),
            label = "Termux"
        )
        assertTrue(cancelled.startsWith("Ошибка Termux:"))
    }

    // --- RunnerRecognitionService: выбор внешней службы без самовызова ---

    @Test
    fun recognitionServiceSelector_excludesSelfAndPrefersSystemEngines() {
        val own = "com.runner.app"

        // Только собственный сервис -> null (исключаем бесконечную рекурсию)
        assertNull(
            RunnerRecognitionService.pickExternalRecognitionComponent(
                candidates = listOf(own to "com.runner.app.service.RunnerRecognitionService"),
                ownPackage = own
            )
        )

        // Предпочитает Google Speech Services среди нескольких сторонних
        val preferred = RunnerRecognitionService.pickExternalRecognitionComponent(
            candidates = listOf(
                own to "com.runner.app.service.RunnerRecognitionService",
                "com.thirdparty.stt" to "com.thirdparty.stt.RecogService",
                "com.google.android.tts" to "com.google.android.apps.speech.tts.googletts.service.GoogleTTSRecognitionService"
            ),
            ownPackage = own
        )
        assertEquals("com.google.android.tts", preferred?.first)

        // Если системных движков нет — берёт первый доступный сторонний
        val fallback = RunnerRecognitionService.pickExternalRecognitionComponent(
            candidates = listOf(
                own to "com.runner.app.service.RunnerRecognitionService",
                "com.thirdparty.stt" to "com.thirdparty.stt.RecogService"
            ),
            ownPackage = own
        )
        assertEquals("com.thirdparty.stt", fallback?.first)
    }

    // --- VoiceManager: разбивка длинных текстов для TTS ---

    @Test
    fun voiceManager_splitTextForTts_keepsAllChunksWithinLimit() {
        assertTrue(VoiceManager.splitTextForTts("   ", 100).isEmpty())
        assertEquals(listOf("Короткая фраза."), VoiceManager.splitTextForTts("Короткая фраза.", 100))

        // Длинный текст с предложениями > 3800 символов
        val sentence = "Это тестовое предложение для проверки корректного разбиения длинного ответа. "
        val longText = sentence.repeat(120) // ~9240 символов
        val chunks = VoiceManager.splitTextForTts(longText, maxChunkLength = 500)

        assertTrue("Ожидалось несколько фрагментов", chunks.size > 10)
        chunks.forEach { chunk ->
            assertTrue("Фрагмент не должен быть пустым", chunk.isNotEmpty())
            assertTrue("Фрагмент превысил лимит: ${chunk.length}", chunk.length <= 500)
        }
        assertEquals(
            longText.trim().replace(Regex("\\s+"), " "),
            chunks.joinToString(" ").replace(Regex("\\s+"), " ")
        )

        // Сплошная строка без пробелов не зацикливается
        val unbroken = "a".repeat(350)
        val hardChunks = VoiceManager.splitTextForTts(unbroken, maxChunkLength = 100)
        assertEquals(4, hardChunks.size)
        assertEquals(unbroken, hardChunks.joinToString(""))
    }

    // --- LiveAgentManager: защита статусов, targetPackage и подтверждений ---

    @Test
    fun liveAgentManager_preventsResurrectionResetsTargetAndHandlesOverlayUnavailable() = runBlocking {
        val originalOverlayCheck = LiveAgentManager.overlayAvailableCheck
        val originalDismissHook = LiveAgentManager.dismissOverlayHook
        LiveAgentManager.dismissOverlayHook = {}
        try {
            // 1. startForTest сбрасывает targetPackage прошлой задачи
            LiveAgentManager.setTargetPackage("com.old.app")
            LiveAgentManager.startForTest(totalSteps = 5, sessionId = "s1")
            assertEquals("", LiveAgentManager.targetPackage)
            assertEquals("", LiveAgentManager.stateFlow.value.expectedPackage)

            // 2. Нет оверлея -> requestConfirmation не зависает на 120с, сразу возвращает false
            LiveAgentManager.overlayAvailableCheck = { false }
            val rejectedWithoutOverlay = LiveAgentManager.requestConfirmation("Нажать «Оплатить»?")
            assertFalse(rejectedWithoutOverlay)

            // 3. Оверлей доступен -> подтверждение проходит, повторный вызов confirmPendingAction безопасен
            LiveAgentManager.overlayAvailableCheck = { true }
            val pending = async(Dispatchers.Default) {
                LiveAgentManager.requestConfirmation("Нажать «Отправить»?")
            }
            val deadline = System.currentTimeMillis() + 5_000L
            while (!LiveAgentManager.hasPendingConfirmationForTest() &&
                System.currentTimeMillis() < deadline
            ) {
                delay(5)
            }
            assertTrue(LiveAgentManager.hasPendingConfirmationForTest())
            LiveAgentManager.confirmPendingAction(true)
            LiveAgentManager.confirmPendingAction(false) // не должно падать с IllegalStateException
            assertTrue(pending.await())

            // 4. stop(..., isError = true) не «воскрешается» через updateStep() и не затирается finishIfRunning()
            LiveAgentManager.stop("Сбой сети", isError = true)
            assertEquals(LiveAgentManager.StepStatus.ERROR, LiveAgentManager.stateFlow.value.status)
            assertFalse(LiveAgentManager.isExecuting)

            LiveAgentManager.updateStep("Запоздалый шаг", index = 2)
            assertEquals(LiveAgentManager.StepStatus.ERROR, LiveAgentManager.stateFlow.value.status)
            assertFalse(LiveAgentManager.isExecuting)

            LiveAgentManager.finishIfRunning("Завершено")
            assertEquals(LiveAgentManager.StepStatus.ERROR, LiveAgentManager.stateFlow.value.status)
        } finally {
            LiveAgentManager.reset()
            LiveAgentManager.overlayAvailableCheck = originalOverlayCheck
            LiveAgentManager.dismissOverlayHook = originalDismissHook
        }
    }

    // --- RunnerAccessibilityService: защита координат жестов ---

    @Test
    fun accessibilityGestureCoordinate_clampsNegativeOverflowAndNonFiniteValues() {
        assertEquals(0f, RunnerAccessibilityService.clampGestureCoordinate(-42f, 1079f), 0.001f)
        assertEquals(1079f, RunnerAccessibilityService.clampGestureCoordinate(5000f, 1079f), 0.001f)
        assertEquals(540f, RunnerAccessibilityService.clampGestureCoordinate(540f, 1079f), 0.001f)
        assertEquals(0f, RunnerAccessibilityService.clampGestureCoordinate(Float.NaN, 1079f), 0.001f)
        assertEquals(0f, RunnerAccessibilityService.clampGestureCoordinate(Float.POSITIVE_INFINITY, 1079f), 0.001f)
        assertEquals(0f, RunnerAccessibilityService.clampGestureCoordinate(Float.NEGATIVE_INFINITY, 1079f), 0.001f)
    }
}
