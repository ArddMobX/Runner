package com.runner.app.ui

import android.content.Context
import com.runner.app.util.CrashReportManager
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Координатор Live-режима (автопилота) агента.
 * Управляет состоянием выполнения, синхронизацией шагов с плавающим оверлеем,
 * запросом подтверждения рискованных действий и обработкой аварийной остановки.
 */
object LiveAgentManager {

    /**
     * Предел ожидания ответа на подтверждение в плавающей карточке.
     * Оверлей мог не показаться (нет разрешения на наложение) или быть
     * закрыт пользователем — задача не должна ждать ответа вечно.
     */
    private const val CONFIRM_TIMEOUT_MS = 120_000L

    enum class StepStatus {
        RUNNING,
        SUCCESS,
        ERROR,
        STOPPED
    }

    data class State(
        val isExecuting: Boolean = false,
        val status: StepStatus = StepStatus.RUNNING,
        val isFinished: Boolean = false,
        val stepDescription: String = "",
        val stepIndex: Int = 0,
        val totalSteps: Int = 0,
        val confirmationPrompt: String? = null,
        /** Последние действия — для истории в развёрнутой карточке. */
        val recentSteps: List<String> = emptyList(),
        /** Сессия, из которой запущена задача: нужна кнопке «В Runner». */
        val sessionId: String = "",
        /** Ожидаемое приложение Live-задачи. Пусто - без ограничения. */
        val expectedPackage: String = ""
    )

    private val _stateFlow = MutableStateFlow(State())
    val stateFlow: StateFlow<State> = _stateFlow.asStateFlow()

    val isExecuting: Boolean
        get() = _stateFlow.value.isExecuting

    private var pendingContinuation: CancellableContinuation<Boolean>? = null
    private var sessionStopHandler: (() -> Unit)? = null

    /**
     * Приложение, с которым должна работать Live-задача. Пока на экране
     * другое приложение, ui_click/ui_type/ui_scroll блокируются. Пусто -
     * без ограничения. open_app и open_url выставляют цель сами.
     */
    @Volatile
    var targetPackage: String = ""
        private set

    fun setTargetPackage(packageName: String) {
        targetPackage = packageName.trim()
        _stateFlow.value = _stateFlow.value.copy(expectedPackage = targetPackage)
    }

    @Volatile
    var onStopRequested: ((String) -> Unit)? = null

    fun start(
        context: Context,
        totalSteps: Int = 0,
        onStop: (() -> Unit)? = null,
        sessionId: String = ""
    ) {
        sessionStopHandler = onStop
        CrashReportManager.log("LiveAgent", "Старт задачи, всего шагов: $totalSteps")
        _stateFlow.value = State(
            isExecuting = true,
            status = StepStatus.RUNNING,
            isFinished = false,
            stepDescription = "Подготовка к выполнению...",
            stepIndex = 1,
            totalSteps = totalSteps,
            sessionId = sessionId
        )
        LiveFloatingOverlay.show(context)
    }

    fun updateStep(description: String, index: Int, total: Int = 0) {
        val current = _stateFlow.value
        CrashReportManager.log("LiveAgent", "Шаг $index: $description")
        _stateFlow.value = current.copy(
            isExecuting = true,
            status = StepStatus.RUNNING,
            stepDescription = description,
            stepIndex = index,
            totalSteps = if (total > 0) total else current.totalSteps,
            confirmationPrompt = null,
            recentSteps = (current.recentSteps + description).takeLast(3)
        )
    }

    /**
     * Приостанавливает выполнение и запрашивает подтверждение пользователя
     * на карточке плавающего оверлея.
     */
    suspend fun requestConfirmation(prompt: String): Boolean {
        _stateFlow.value = _stateFlow.value.copy(confirmationPrompt = prompt)
        val approved = withTimeoutOrNull(CONFIRM_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                pendingContinuation = continuation
                continuation.invokeOnCancellation {
                    if (pendingContinuation === continuation) pendingContinuation = null
                    _stateFlow.value = _stateFlow.value.copy(confirmationPrompt = null)
                }
            }
        }
        if (approved == null) {
            // Оверлея нет, разрешение на наложение отозвали или пользователь
            // закрыл карточку. Ждать вечно нельзя: задача залипла бы на «Стоп»
            // до перезапуска, а finally не выполнился бы. Безопасное
            // направление — считать действие отклонённым.
            pendingContinuation = null
            _stateFlow.value = _stateFlow.value.copy(confirmationPrompt = null)
            return false
        }
        return approved
    }

    fun confirmPendingAction(approved: Boolean) {
        val continuation = pendingContinuation
        pendingContinuation = null
        _stateFlow.value = _stateFlow.value.copy(confirmationPrompt = null)
        if (continuation != null && continuation.isActive) continuation.resume(approved)
    }

    fun stop(reason: String = "Остановлено пользователем", isError: Boolean = false) {
        // Снимаем висящее подтверждение ДО проверки isExecuting: ответ на
        // диалог могла ждать задача, для которой Live-режим не стартовал.
        confirmPendingAction(false)
        if (!isExecuting) return
        val lower = reason.lowercase()
        val isErr = isError ||
                lower.contains("ошибка") ||
                lower.contains("блокировк") ||
                lower.contains("security") ||
                lower.contains("сбой") ||
                lower.contains("недоступен")

        if (isErr) {
            CrashReportManager.setLastError("Live-режим остановлен с ошибкой: $reason")
        } else {
            CrashReportManager.log("LiveAgent", "Остановлено: $reason")
        }

        _stateFlow.value = _stateFlow.value.copy(
            isExecuting = false,
            status = if (isErr) StepStatus.ERROR else StepStatus.STOPPED,
            isFinished = false,
            stepDescription = reason,
            confirmationPrompt = null
        )
        sessionStopHandler?.invoke()
        sessionStopHandler = null
        onStopRequested?.invoke(reason)
        // Оверлей не закрываем: пользователь должен видеть причину остановки или ошибки,
        // с возможностью закрыть окно или перейти в Runner.
    }

    fun finish(summary: String) {
        confirmPendingAction(false)
        CrashReportManager.log("LiveAgent", "Успешно завершено: $summary")
        _stateFlow.value = _stateFlow.value.copy(
            isExecuting = false,
            status = StepStatus.SUCCESS,
            isFinished = true,
            stepDescription = summary,
            confirmationPrompt = null
        )
        sessionStopHandler = null
        // Не убираем оверлей автоматически: пользователь видит статус "Готово",
        // карточку результата и может перейти в Runner или закрыть оверлей.
    }

    /**
     * Завершает задачу только если она ещё выполняется.
     *
     * Вызывается из finally агентского цикла. stop() и ui_finish() уже
     * выставили собственный статус (ошибка, остановка, итоговый отчёт),
     * и перезаписывать его зелёным «Готово» нельзя: пользователь не увидит
     * причину сбоя, а кнопка отчёта о сбое исчезнет.
     */
    fun finishIfRunning(summary: String = "Завершено") {
        if (!_stateFlow.value.isExecuting) return
        finish(summary)
    }

    fun reset() {
        confirmPendingAction(false)
        sessionStopHandler = null
        targetPackage = ""
        _stateFlow.value = State()
        LiveFloatingOverlay.dismiss()
    }
}
