package com.runner.app.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognitionService
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

/**
 * Системная служба распознавания речи Android.
 *
 * Обязательный компонент для квалификации приложения в роли системного ассистента
 * (RoleManager.ROLE_ASSISTANT): VoiceInteractionServiceInfo отклоняет метаданные
 * без атрибута android:recognitionService.
 *
 * На Android 12+ (res/xml-v31/recognition_service.xml) помечена как
 * selectableAsDefault="false", чтобы не перехватывать системный голосовой ввод.
 * На Android 8–11 (или при явной привязке) делегирует распознавание внешней
 * установленной системной службе (Google Speech Services и др.) вместо пустой
 * заглушки; если внешней службы нет — сразу возвращает ошибку ERROR_CLIENT.
 */
class RunnerRecognitionService : RecognitionService() {

    private var delegateRecognizer: SpeechRecognizer? = null

    override fun onStartListening(recognizerIntent: Intent?, listener: Callback?) {
        if (listener == null) return
        destroyDelegate()

        val externalTarget = findExternalRecognitionService(this)
        if (externalTarget == null) {
            Log.w(TAG, "внешняя служба распознавания речи не найдена")
            runCatching { listener.error(SpeechRecognizer.ERROR_CLIENT) }
            return
        }

        try {
            val recognizer = SpeechRecognizer.createSpeechRecognizer(this, externalTarget)
            delegateRecognizer = recognizer
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    if (delegateRecognizer !== recognizer) return
                    runCatching { listener.readyForSpeech(params ?: Bundle()) }
                }

                override fun onBeginningOfSpeech() {
                    if (delegateRecognizer !== recognizer) return
                    runCatching { listener.beginningOfSpeech() }
                }

                override fun onRmsChanged(rmsdB: Float) {
                    if (delegateRecognizer !== recognizer) return
                    runCatching { listener.rmsChanged(rmsdB) }
                }

                override fun onBufferReceived(buffer: ByteArray?) {
                    if (delegateRecognizer !== recognizer || buffer == null) return
                    runCatching { listener.bufferReceived(buffer) }
                }

                override fun onEndOfSpeech() {
                    if (delegateRecognizer !== recognizer) return
                    runCatching { listener.endOfSpeech() }
                }

                override fun onError(error: Int) {
                    if (delegateRecognizer !== recognizer) return
                    clearDelegate(recognizer)
                    runCatching { listener.error(error) }
                }

                override fun onResults(results: Bundle?) {
                    if (delegateRecognizer !== recognizer) return
                    clearDelegate(recognizer)
                    runCatching { listener.results(results ?: Bundle()) }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    if (delegateRecognizer !== recognizer) return
                    runCatching { listener.partialResults(partialResults ?: Bundle()) }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            val forwardIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                recognizerIntent?.extras?.let { putExtras(it) }
            }
            recognizer.startListening(forwardIntent)
        } catch (e: Exception) {
            Log.w(TAG, "не удалось делегировать распознавание речи", e)
            destroyDelegate()
            runCatching { listener.error(SpeechRecognizer.ERROR_CLIENT) }
        }
    }

    override fun onStopListening(listener: Callback?) {
        runCatching { delegateRecognizer?.stopListening() }
    }

    override fun onCancel(listener: Callback?) {
        destroyDelegate()
    }

    override fun onDestroy() {
        destroyDelegate()
        super.onDestroy()
    }

    private fun clearDelegate(target: SpeechRecognizer) {
        if (delegateRecognizer === target) {
            delegateRecognizer = null
        }
        runCatching { target.destroy() }
    }

    private fun destroyDelegate() {
        val current = delegateRecognizer
        delegateRecognizer = null
        if (current != null) {
            runCatching { current.cancel() }
            runCatching { current.destroy() }
        }
    }

    companion object {
        private const val TAG = "RunnerRecogService"

        /**
         * Предпочтительный порядок системных движков распознавания речи:
         * Google Speech Recognition & Synthesis, Android System Intelligence,
         * классическое приложение Google, Samsung, затем любой другой не-свой движок.
         */
        private val PREFERRED_RECOGNITION_PACKAGES = listOf(
            "com.google.android.tts",
            "com.google.android.as",
            "com.google.android.googlequicksearchbox",
            "com.samsung.android.bixby.agent",
            "com.samsung.android.svoiceime"
        )

        internal fun pickExternalRecognitionComponent(
            candidates: List<Pair<String, String>>,
            ownPackage: String
        ): Pair<String, String>? {
            val external = candidates.filter { (pkg, cls) ->
                pkg.isNotBlank() && cls.isNotBlank() && pkg != ownPackage
            }
            if (external.isEmpty()) return null
            for (preferredPkg in PREFERRED_RECOGNITION_PACKAGES) {
                external.firstOrNull { it.first == preferredPkg }?.let { return it }
            }
            return external.first()
        }

        fun findExternalRecognitionService(context: Context): ComponentName? {
            return try {
                val queryIntent = Intent(SERVICE_INTERFACE)
                val services = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.packageManager.queryIntentServices(
                        queryIntent,
                        PackageManager.ResolveInfoFlags.of(0)
                    )
                } else {
                    @Suppress("DEPRECATION")
                    context.packageManager.queryIntentServices(queryIntent, 0)
                }
                val pairs = services.mapNotNull { info ->
                    val si = info.serviceInfo ?: return@mapNotNull null
                    si.packageName to si.name
                }
                val chosen = pickExternalRecognitionComponent(pairs, context.packageName) ?: return null
                ComponentName(chosen.first, chosen.second)
            } catch (e: Exception) {
                Log.w(TAG, "ошибка поиска внешней службы распознавания", e)
                null
            }
        }
    }
}
