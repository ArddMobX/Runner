package com.runner.app.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Описание системного голоса для озвучки.
 */
data class VoiceOption(
    val name: String,
    val displayName: String,
    val locale: String
)

/**
 * Менеджер голосовых функций: распознавание речи (STT) и синтез речи (TTS)
 * через стандартные системные службы Android.
 */
class VoiceManager(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())

    // --- Speech To Text (STT) ---

    private var speechRecognizer: SpeechRecognizer? = null

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _rmsLevel = MutableStateFlow(0f)
    val rmsLevel: StateFlow<Float> = _rmsLevel.asStateFlow()

    fun isRecognitionAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun startListening(
        onPartialResult: (String) -> Unit = {},
        onFinalResult: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        mainHandler.post {
            if (!isRecognitionAvailable()) {
                onError("Голосовой ввод недоступен на этом устройстве")
                return@post
            }

            stopListeningInternal()

            try {
                val recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        _isListening.value = true
                    }

                    override fun onBeginningOfSpeech() {}

                    override fun onRmsChanged(rmsdB: Float) {
                        _rmsLevel.value = rmsdB.coerceIn(0f, 10f)
                    }

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        _isListening.value = false
                    }

                    override fun onError(error: Int) {
                        _isListening.value = false
                        val message = when (error) {
                            SpeechRecognizer.ERROR_AUDIO -> "Ошибка аудио"
                            SpeechRecognizer.ERROR_CLIENT -> "Ошибка клиента"
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Требуется разрешение на запись аудио"
                            SpeechRecognizer.ERROR_NETWORK -> "Ошибка сети"
                            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Таймаут сети"
                            SpeechRecognizer.ERROR_NO_MATCH -> "Речь не распознана"
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Служба распознавания занята"
                            SpeechRecognizer.ERROR_SERVER -> "Ошибка сервера"
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Время ожидания речи истекло"
                            else -> "Ошибка распознавания ($error)"
                        }
                        if (error != SpeechRecognizer.ERROR_NO_MATCH && error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                            onError(message)
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        _isListening.value = false
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull()?.trim().orEmpty()
                        if (text.isNotBlank()) {
                            onFinalResult(text)
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull()?.trim().orEmpty()
                        if (text.isNotBlank()) {
                            onPartialResult(text)
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }

            speechRecognizer = recognizer
            recognizer.startListening(intent)
            _isListening.value = true
        } catch (e: Exception) {
            _isListening.value = false
            onError("Не удалось запустить распознавание: ${e.message}")
        }
    }
}

    fun stopListening() {
        mainHandler.post {
            stopListeningInternal()
        }
    }

    private fun stopListeningInternal() {
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
        } catch (_: Exception) {}
        speechRecognizer = null
        _isListening.value = false
        _rmsLevel.value = 0f
    }

    // --- Text To Speech (TTS) ---

    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _activeUtteranceId = MutableStateFlow<String?>(null)
    val activeUtteranceId: StateFlow<String?> = _activeUtteranceId.asStateFlow()

    private val _availableVoices = MutableStateFlow<List<VoiceOption>>(emptyList())
    val availableVoices: StateFlow<List<VoiceOption>> = _availableVoices.asStateFlow()

    private var currentSpeed: Float = 1.0f
    private var currentPitch: Float = 1.0f
    private var currentVoiceName: String = ""

    private var onSpeechDoneCallback: (() -> Unit)? = null

    /**
     * Фраза, которую попросили произнести до готовности движка.
     *
     * Раньше `speak()` в этом состоянии вызывал `initTts()` и выходил, а текст
     * выбрасывался: первый ответ после запуска приложения молча не озвучивался.
     * Инициализация TTS асинхронная и занимает секунды, так что это не редкий
     * случай, а обычный первый запуск.
     */
    private class PendingSpeech(
        val text: String,
        val utteranceId: String,
        val onDone: () -> Unit
    )

    private var pendingSpeech: PendingSpeech? = null
    private var isTtsInitializing = false

    init {
        mainHandler.post {
            initTts()
        }
    }

    fun setTtsParameters(speed: Float, pitch: Float, voiceName: String) {
        currentSpeed = speed
        currentPitch = pitch
        currentVoiceName = voiceName
        mainHandler.post {
            applyTtsParameters()
        }
    }

    private fun applyTtsParameters() {
        val engine = tts ?: return
        if (!isTtsReady) return
        try {
            engine.setSpeechRate(currentSpeed.coerceIn(0.5f, 2.5f))
            engine.setPitch(currentPitch.coerceIn(0.5f, 2.0f))
            if (currentVoiceName.isNotBlank()) {
                val matched = engine.voices?.firstOrNull { it.name == currentVoiceName }
                if (matched != null) {
                    engine.voice = matched
                }
            } else {
                try {
                    engine.defaultVoice?.let { engine.voice = it }
                    val locResult = engine.setLanguage(Locale.getDefault())
                    if (locResult == TextToSpeech.LANG_MISSING_DATA || locResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                        engine.language = Locale.US
                    }
                } catch (e: Exception) {
                    // Настройки голоса не применились: озвучка будет звучать
                    // не так, как выбрал пользователь, и это стоит видеть в логе.
                    Log.w(TAG, "не удалось применить параметры TTS", e)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "движок TTS не принял параметры", e)
        }
    }

    private fun isGoogleTtsInstalled(): Boolean {
        return try {
            val pm = context.packageManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(GOOGLE_TTS_PACKAGE, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(GOOGLE_TTS_PACKAGE, 0)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun loadAvailableVoices() {
        val engine = tts ?: return
        try {
            val voices = engine.voices ?: return
            val currentLoc = Locale.getDefault()
            val currentLang = currentLoc.language

            val filtered = voices.filter { v ->
                val l = v.locale.language
                l == currentLang || l == "ru" || l == "en"
            }

            val byLang = filtered.groupBy { v ->
                v.locale.getDisplayLanguage(Locale("ru")).replaceFirstChar { it.uppercase() }
            }

            val resultList = mutableListOf<VoiceOption>()

            byLang.forEach { (langName, vList) ->
                // Делим на категории: Студийный, Качественный, Стандартный
                // Отдаём приоритет голосам с v.quality >= Voice.QUALITY_HIGH
                val studioList = vList.filter { v ->
                    val lower = v.name.lowercase()
                    v.quality >= Voice.QUALITY_VERY_HIGH ||
                            lower.contains("studio") ||
                            lower.contains("wavenet") ||
                            (v.quality >= Voice.QUALITY_HIGH && lower.contains("network"))
                }.sortedWith(compareByDescending<Voice> { it.quality }.thenBy { it.name })

                val studioNames = studioList.map { it.name }.toSet()

                val highList = vList.filter { v ->
                    v.name !in studioNames && (
                        v.quality >= Voice.QUALITY_HIGH ||
                        !v.name.lowercase().contains("low")
                    )
                }.sortedWith(compareByDescending<Voice> { it.quality }.thenBy { it.name })

                val highNames = highList.map { it.name }.toSet()

                val standardList = vList.filter { v ->
                    v.name !in studioNames && v.name !in highNames
                }.sortedWith(compareByDescending<Voice> { it.quality }.thenBy { it.name })

                fun addTierVoices(tierList: List<Voice>, tierName: String) {
                    val count = tierList.size
                    tierList.forEachIndexed { index, voice ->
                        val suffix = if (count > 1) " ${index + 1}" else ""
                        val displayName = "$langName ($tierName$suffix)"
                        resultList.add(
                            VoiceOption(
                                name = voice.name,
                                displayName = displayName,
                                locale = voice.locale.displayName
                            )
                        )
                    }
                }

                addTierVoices(studioList, "Студийный")
                addTierVoices(highList, "Качественный")
                addTierVoices(standardList, "Стандартный")
            }

            _availableVoices.value = resultList.sortedWith(
                compareBy<VoiceOption> { option ->
                    val isCur = option.locale.startsWith(currentLoc.displayName) || option.displayName.startsWith(currentLoc.displayLanguage)
                    val isRu = option.displayName.startsWith("Русский")
                    when {
                        isCur -> 0
                        isRu -> 1
                        else -> 2
                    }
                }.thenBy { option ->
                    when {
                        option.displayName.contains("Студийный") -> 0
                        option.displayName.contains("Качественный") -> 1
                        else -> 2
                    }
                }.thenBy { it.displayName }
            )
        } catch (_: Exception) {}
    }

    private fun initTts() {
        // Повторный вход запрещён: инициализация асинхронная, а каждый новый
        // TextToSpeech — это ещё одна привязка к службе синтеза.
        if (isTtsInitializing) return
        isTtsInitializing = true

        // Старый движок гасим обязательно. Без shutdown объекты копились при
        // каждом повторе инициализации и держали службу синтеза живой.
        try {
            tts?.shutdown()
        } catch (_: Exception) {}
        tts = null
        isTtsReady = false

        val onInit = TextToSpeech.OnInitListener { status ->
            isTtsInitializing = false
            if (status == TextToSpeech.SUCCESS) {
                isTtsReady = true
                val result = tts?.setLanguage(Locale.getDefault())
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.language = Locale.US
                }
                applyTtsParameters()
                setupUtteranceListener()
                loadAvailableVoices()
                // Текст, который просили озвучить до готовности движка,
                // произносим сейчас — с уже применёнными скоростью и голосом.
                flushPendingSpeech()
            } else {
                isTtsReady = false
                // Движка нет: держать текст незачем, иначе он всплывёт
                // неожиданно позже, поверх другого ответа.
                pendingSpeech = null
            }
        }

        tts = if (isGoogleTtsInstalled()) {
            TextToSpeech(context.applicationContext, onInit, GOOGLE_TTS_PACKAGE)
        } else {
            TextToSpeech(context.applicationContext, onInit)
        }
    }

    private fun setupUtteranceListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _isSpeaking.value = true
                _activeUtteranceId.value = utteranceId
            }

            override fun onDone(utteranceId: String?) {
                _isSpeaking.value = false
                _activeUtteranceId.value = null
                onSpeechDoneCallback?.invoke()
                onSpeechDoneCallback = null
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                _isSpeaking.value = false
                _activeUtteranceId.value = null
                onSpeechDoneCallback = null
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                _isSpeaking.value = false
                _activeUtteranceId.value = null
                onSpeechDoneCallback = null
            }
        })
    }

    fun speak(text: String, utteranceId: String, onDone: () -> Unit = {}) {
        mainHandler.post {
            if (!isTtsReady || tts == null) {
                // Движок ещё поднимается: запоминаем фразу и произнесём её
                // сразу после OnInit. Выбрасывать нельзя — это первый ответ.
                pendingSpeech = PendingSpeech(text, utteranceId, onDone)
                initTts()
                return@post
            }
            speakInternal(text, utteranceId, onDone)
        }
    }

    private fun speakInternal(text: String, utteranceId: String, onDone: () -> Unit) {
        // stopSpeakingInternal сбрасывает onSpeechDoneCallback, поэтому порядок
        // важен: сначала останавливаем прошлую фразу, потом ставим новый колбэк.
        stopSpeakingInternal()

        val clean = cleanTextForSpeech(text)
        if (clean.isBlank()) return

        onSpeechDoneCallback = onDone
        _activeUtteranceId.value = utteranceId
        _isSpeaking.value = true

        tts?.speak(clean, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    /** Произносит фразу, отложенную до готовности движка. */
    private fun flushPendingSpeech() {
        val pending = pendingSpeech ?: return
        pendingSpeech = null
        speakInternal(pending.text, pending.utteranceId, pending.onDone)
    }

    /**
     * Воспроизводит тестовую фразу для предпросмотра выбранного голоса и параметров.
     */
    fun previewVoice(text: String = "Привет! Это проверка голоса в Runner.") {
        speak(text, "preview_${System.currentTimeMillis()}")
    }

    fun stopSpeaking() {
        mainHandler.post {
            stopSpeakingInternal()
        }
    }

    private fun stopSpeakingInternal() {
        try {
            tts?.stop()
        } catch (_: Exception) {}
        _isSpeaking.value = false
        _activeUtteranceId.value = null
        onSpeechDoneCallback = null
    }

    fun destroy() {
        mainHandler.post {
            stopListeningInternal()
            stopSpeakingInternal()
            pendingSpeech = null
            try {
                tts?.shutdown()
            } catch (_: Exception) {}
            tts = null
            isTtsReady = false
            isTtsInitializing = false
        }
    }

    companion object {
        const val GOOGLE_TTS_PACKAGE = "com.google.android.tts"

        /** Метка для logcat: без неё молчаливые отказы движка не найти. */
        const val TAG = "RunnerVoice"

        /**
         * Очищает текст от Markdown-разметки, блоков кода и LaTeX формул,
         * чтобы синтезатор речи не читал служебные символы.
         */
        fun cleanTextForSpeech(raw: String): String {
            return raw
                // Блоки кода заменяем на короткую реплику
                .replace(Regex("""```[\s\S]*?```"""), " Блок кода. ")
                // Формулы LaTeX в блоках и строках
                .replace(Regex("""\$\$[\s\S]*?\$\$"""), " Математическая формула. ")
                .replace(Regex("""\$[^\$\n]+\$"""), " Формула. ")
                // Ссылки: [текст](ссылка) -> текст
                .replace(Regex("""\[([^\]]+)\]\([^)]+\)"""), "$1")
                // Инлайн-код `code` -> code
                .replace(Regex("""`([^`]+)`"""), "$1")
                // Заголовки, жирный, курсив, зачеркнутый
                .replace(Regex("""[#*_~>`]+"""), "")
                // Лишние пробелы и пустые строки
                .replace(Regex("""\n+"""), ". ")
                .replace(Regex("""\s+"""), " ")
                .trim()
        }
    }
}
