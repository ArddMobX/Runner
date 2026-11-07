package com.runner.app.ui

import android.content.Context
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Сколько ждём ответа на подтверждение, прежде чем считать действие отклонённым.
 *
 * Пять минут — заведомо больше, чем нужно человеку, чтобы прочитать карточку
 * и нажать кнопку. Смысл не в строгости, а в том, чтобы задача не могла
 * остаться в вечном ожидании: подтверждение, которое некуда показать, — это
 * не «ждём пользователя», а зависший чат.
 */
private const val CONFIRM_TIMEOUT_MS = 5 * 60 * 1000L

/**
 * Координатор Live-режима (автопилота) агента.
 * Управляет состоянием выполнения, синхронизацией шагов с плавающим оверлеем,
 * запросом подтверждения рискованных действий и обработкой аварийной остановки.
 */
object LiveAgentManager {

    data class State(
        val isExecuting: Boolean = false,
        val stepDescription: String = "",
        val stepIndex: Int = 0,
        val totalSteps: Int = 0,
        val confirmationPrompt: String? = null
    )

    private val _stateFlow = MutableStateFlow(State())
    val stateFlow: StateFlow<State> = _stateFlow.asStateFlow()

    val isExecuting: Boolean
        get() = _stateFlow.value.isExecuting

    private var pendingContinuation: CancellableContinuation<Boolean>? = null

    /**
     * Что делать по «Стоп» в плавающей карточке.
     *
     * Хранится на сессию, а не в глобальной переменной: Live-режим может
     * запустить и обычный чат, и плавающий ассистент со своей ViewModel.
     * Глобальный колбэк перезаписывался тем, кто стартовал позже, и «Стоп»
     * попадал в чужой (часто уже уничтоженный) экземпляр.
     */
    private var sessionStopHandler: (() -> Unit)? = null

    fun start(context: Context, totalSteps: Int = 5, onStop: (() -> Unit)? = null) {
        sessionStopHandler = onStop
        _stateFlow.value = State(
            isExecuting = true,
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
     *
     * У ожидания есть предел: если оверлей так и не показался (нет разрешения
     * на наложение, пользователь свернул приложение), подтверждение не придёт
     * никогда, а задача встанет намертво — корутина шага ждёт continuation,
     * `finally` не выполняется, `_isRunning` остаётся true, и чат залипает
     * на кнопке «Стоп» до перезапуска приложения. По истечении таймаута
     * считаем действие отклонённым — это безопасное направление.
     */
    suspend fun requestConfirmation(
        prompt: String,
        timeoutMs: Long = CONFIRM_TIMEOUT_MS
    ): Boolean {
        _stateFlow.value = _stateFlow.value.copy(confirmationPrompt = prompt)
        return withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { continuation ->
                pendingContinuation = continuation
                continuation.invokeOnCancellation {
                    // Таймаут или отмена корутины шага: ссылку надо снять,
                    // иначе следующий запрос перезапишет её и потеряет.
                    if (pendingContinuation === continuation) pendingContinuation = null
                    _stateFlow.value = _stateFlow.value.copy(confirmationPrompt = null)
                }
            }
        } ?: false
    }

    fun confirmPendingAction(approved: Boolean) {
        val continuation = pendingContinuation
        pendingContinuation = null
        _stateFlow.value = _stateFlow.value.copy(confirmationPrompt = null)
        if (continuation != null && continuation.isActive) continuation.resume(approved)
    }

    /**
     * Снимает висящее подтверждение, не трогая состояние выполнения.
     *
     * Нужен ровно в одном месте — в «Стоп»: ответ на диалог может ждать
     * задача, которая Live-режим не запускала (оверлея нет, а подтверждение
     * требуется), и тогда [stop] выходил по early return, оставляя корутину
     * шага ждать вечно.
     */
    fun cancelPending() {
        confirmPendingAction(false)
    }

    fun stop(reason: String = "Остановлено пользователем") {
        // Снимаем ожидание ДО проверки isExecuting: подтверждение могло быть
        // запрошено задачей, для которой Live-режим даже не стартовал.
        confirmPendingAction(false)
        if (!isExecuting) return

        _stateFlow.value = _stateFlow.value.copy(
            isExecuting = false,
            stepDescription = reason,
            confirmationPrompt = null
        )
        val handler = sessionStopHandler
        sessionStopHandler = null
        handler?.invoke()
        LiveFloatingOverlay.dismiss()
    }

    fun finish(summary: String) {
        confirmPendingAction(false)
        // Обработчик снимаем: сессия закончилась, держать ссылку на ViewModel
        // незачем — она может быть уже уничтожена.
        sessionStopHandler = null
        _stateFlow.value = _stateFlow.value.copy(
            isExecuting = false,
            stepDescription = summary,
            confirmationPrompt = null
        )
        LiveFloatingOverlay.dismiss()
    }
}
