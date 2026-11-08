package com.runner.app.ui

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume

/**
 * Координатор Live-режима (автопилота) агента.
 * Управляет состоянием выполнения, синхронизацией шагов с плавающим оверлеем,
 * запросом подтверждения рискованных действий и обработкой аварийной остановки.
 */
object LiveAgentManager {

    data class State(
        val isExecuting: Boolean = false,
        val isFinished: Boolean = false,
        val stepDescription: String = "",
        val stepIndex: Int = 0,
        val totalSteps: Int = 0,
        val confirmationPrompt: String? = null
    )

    private val _stateFlow = MutableStateFlow(State())
    val stateFlow: StateFlow<State> = _stateFlow.asStateFlow()

    val isExecuting: Boolean
        get() = _stateFlow.value.isExecuting

    private var pendingContinuation: Continuation<Boolean>? = null
    private var sessionStopHandler: (() -> Unit)? = null

    @Volatile
    var onStopRequested: ((String) -> Unit)? = null

    fun start(context: Context, totalSteps: Int = 5, onStop: (() -> Unit)? = null) {
        sessionStopHandler = onStop
        _stateFlow.value = State(
            isExecuting = true,
            isFinished = false,
            stepDescription = "Подготовка к выполнению...",
            stepIndex = 1,
            totalSteps = totalSteps
        )
        LiveFloatingOverlay.show(context)
    }

    fun updateStep(description: String, index: Int, total: Int = 0) {
        val current = _stateFlow.value
        _stateFlow.value = current.copy(
            stepDescription = description,
            stepIndex = index,
            totalSteps = if (total > 0) total else current.totalSteps,
            confirmationPrompt = null
        )
    }

    /**
     * Приостанавливает выполнение и запрашивает подтверждение пользователя
     * на карточке плавающего оверлея.
     */
    suspend fun requestConfirmation(prompt: String): Boolean {
        _stateFlow.value = _stateFlow.value.copy(confirmationPrompt = prompt)
        return suspendCancellableCoroutine { continuation ->
            pendingContinuation = continuation
            continuation.invokeOnCancellation {
                pendingContinuation = null
            }
        }
    }

    fun confirmPendingAction(approved: Boolean) {
        val continuation = pendingContinuation
        pendingContinuation = null
        _stateFlow.value = _stateFlow.value.copy(confirmationPrompt = null)
        continuation?.resume(approved)
    }

    fun stop(reason: String = "Остановлено пользователем") {
        if (!isExecuting) return

        confirmPendingAction(false)
        _stateFlow.value = _stateFlow.value.copy(
            isExecuting = false,
            isFinished = false,
            stepDescription = reason,
            confirmationPrompt = null
        )
        sessionStopHandler?.invoke()
        sessionStopHandler = null
        onStopRequested?.invoke(reason)
        LiveFloatingOverlay.dismiss()
    }

    fun finish(summary: String) {
        confirmPendingAction(false)
        _stateFlow.value = _stateFlow.value.copy(
            isExecuting = false,
            isFinished = true,
            stepDescription = summary,
            confirmationPrompt = null
        )
        sessionStopHandler = null
        // Не убираем оверлей автоматически: пользователь видит статус "Готово",
        // карточку результата и может перейти в Runner или закрыть оверлей.
    }

    fun reset() {
        confirmPendingAction(false)
        sessionStopHandler = null
        _stateFlow.value = State()
        LiveFloatingOverlay.dismiss()
    }
}
