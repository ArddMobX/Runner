package com.runner.app.service

import android.content.Intent
import android.speech.RecognitionService

/**
 * Системная служба распознавания речи Android.
 * Обязательный компонент для квалификации приложения в роли системного ассистента (RoleManager.ROLE_ASSISTANT).
 */
class RunnerRecognitionService : RecognitionService() {

    override fun onStartListening(recognizerIntent: Intent?, listener: Callback?) {
        // Runner использует встроенный распознаватель через SpeechRecognizer
    }

    override fun onCancel(listener: Callback?) {
        // Отмена текущего сеанса распознавания
    }

    override fun onStopListening(listener: Callback?) {
        // Остановка прослушивания
    }
}
