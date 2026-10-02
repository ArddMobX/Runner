package com.runner.app.ui

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.runner.app.data.AIResponseResult
import com.runner.app.data.AppSettings
import com.runner.app.data.AppThemeMode
import com.runner.app.data.ChatRepository
import com.runner.app.data.ColorSource
import com.runner.app.data.ConnectionTestResult
import com.runner.app.data.GenerationMetrics
import com.runner.app.data.ModelCatalog
import com.runner.app.data.ModelInfo
import com.runner.app.data.OpenAIClient
import com.runner.app.data.Provider
import com.runner.app.data.ProviderStore
import com.runner.app.data.SettingsStore
import com.runner.app.data.ThemeConfig
import com.runner.app.data.ThemeStore
import com.runner.app.data.ToolCall
import com.runner.app.data.db.ChatDatabase
import com.runner.app.data.db.MessageEntity
import com.runner.app.data.db.SessionEntity
import com.runner.app.tools.ToolDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlin.coroutines.resume

enum class MessageRole {
    USER,
    ASSISTANT,
    TOOL_EXECUTION,
    SYSTEM_INFO,
    /** Карточка плана шагов: составляется до исполнения, ждёт подтверждения. */
    PLAN
}

/** Что предложить пользователю прямо в баннере. */
enum class MessageAction {
    OPEN_SETTINGS,
    OPEN_MODELS,
    GRANT_STORAGE
}

data class ConfirmationRequest(
    val id: String = UUID.randomUUID().toString(),
    val toolName: String,
    val title: String,
    val details: String,
    val warning: String,
    val preview: String = "",
    val onDecision: (Boolean) -> Unit
)

data class ConnectionTestState(
    val isTesting: Boolean = false,
    val result: ConnectionTestResult? = null
)

/**
 * План шагов на подтверждении у пользователя.
 * steps — распарсенные шаги, rawText — исходный текст модели.
 */
data class PlanProposal(
    val id: String = UUID.randomUUID().toString(),
    val steps: List<String>,
    val rawText: String,
    val onDecision: (Boolean) -> Unit
)

/** SharedPreferences для одноразовых first-run флагов (общий с настройками). */
private const val STORAGE_PREFS = "runner_settings"

/** First-run диалог разрешений уже показывали. */
private const val KEY_STORAGE_PROMPT_SHOWN = "storage_prompt_shown"

/** Маркер «инструменты не нужны» в ответе планировщика. */
private const val NO_TOOLS_MARKER = "БЕЗ_ИНСТРУМЕНТОВ"

/** Жёсткий потолок шагов в плане (лимит исполнения всё равно задаёт maxSteps). */
private const val MAX_PLAN_STEPS = 12

/**
 * Системный промпт фазы планирования. Формат строгий: слабые модели
 * (Flash Lite и подобные) надёжно держат нумерованный список, а свободный
 * текст потом невозможно привязать к шагам исполнения.
 */
private const val PLAN_SYSTEM_PROMPT = """
Ты планировщик мобильного агента Runner для Android. Разложи задачу пользователя на пошаговый план работы с инструментами.
Доступные инструменты: list_dir, get_folder_summary, read_file, write_file, delete_file, create_dir, move_file, copy_file, search_files, create_archive, extract_archive, organize_downloads, get_storage_summary, find_largest_files, find_junk_files, clipboard_read, clipboard_write, run_shell_command.
Правила вывода:
- Выведи ТОЛЬКО нумерованный список шагов, по одному на строку: "1. имя_инструмента — что сделать".
- Используй только инструменты из списка выше.
- Пути указывай абсолютные от корня /storage/emulated/0.
- Никаких вступлений, пояснений и заключений.
- Если задача решается одним текстовым ответом без инструментов, выведи одну строку: БЕЗ_ИНСТРУМЕНТОВ.
"""

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: MessageRole,
    val content: String,
    val toolName: String? = null,
    val toolArgs: String? = null,
    val toolOutput: String? = null,
    /** Короткая сводка результата: «143 файла», «2.4 ГБ». */
    val toolSummary: String? = null,
    val isRunning: Boolean = false,
    val isError: Boolean = false,
    val isDeclined: Boolean = false,
    val action: MessageAction? = null,
    val timestamp: Long = System.currentTimeMillis(),
    /** Полное время ответа агента на задачу. */
    val durationMs: Long? = null,
    /** Время работы конкретного инструмента. */
    val toolDurationMs: Long? = null,
    /** Размышления thinking-модели — показываются под катом. */
    val reasoningText: String? = null,
    val reasoningMs: Long? = null,
    val promptTokens: Int? = null,
    val completionTokens: Int? = null,
    val tokensPerSecond: Double? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val providerStore = ProviderStore(application)
    private val settingsStore = SettingsStore(application)
    private val repository = ChatRepository(ChatDatabase.get(application).chatDao())
    private val apiClient = OpenAIClient()

    // --- Провайдеры ---

    private val _providers = MutableStateFlow(providerStore.loadProviders())
    val providers: StateFlow<List<Provider>> = _providers.asStateFlow()

    private val _activeProviderId = MutableStateFlow(providerStore.activeProviderId())
    val activeProviderId: StateFlow<String> = _activeProviderId.asStateFlow()

    val activeProvider: StateFlow<Provider?> =
        combine(_providers, _activeProviderId) { list, id ->
            list.firstOrNull { it.id == id } ?: list.firstOrNull()
        }.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            _providers.value.firstOrNull { it.id == _activeProviderId.value }
                ?: _providers.value.firstOrNull()
        )

    /** id провайдера, для которого сейчас тянется список моделей. */
    private val _modelsLoadingFor = MutableStateFlow<String?>(null)
    val modelsLoadingFor: StateFlow<String?> = _modelsLoadingFor.asStateFlow()

    // --- Настройки ---

    private val _settings = MutableStateFlow(settingsStore.load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _connectionTestState = MutableStateFlow(ConnectionTestState())
    val connectionTestState: StateFlow<ConnectionTestState> = _connectionTestState.asStateFlow()

    // --- Тема (Material You / DataStore) ---

    private val themeStore = ThemeStore(application)
    val themeConfig: StateFlow<ThemeConfig> = themeStore.themeFlow.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        ThemeConfig()
    )

    // --- История чатов ---

    private val _sessionsQuery = MutableStateFlow("")
    val sessionsQuery: StateFlow<String> = _sessionsQuery.asStateFlow()

    val sessions: StateFlow<List<SessionEntity>> = _sessionsQuery
        .flatMapLatest { query -> repository.observeSessions(query) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _currentSessionId = MutableStateFlow<String?>(null)
    val currentSessionId: StateFlow<String?> = _currentSessionId.asStateFlow()

    private val _currentSessionTitle = MutableStateFlow("")
    val currentSessionTitle: StateFlow<String> = _currentSessionTitle.asStateFlow()

    // --- Диалог ---

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    /** Текст, который модель печатает прямо сейчас. */
    private val _streamingText = MutableStateFlow("")
    val streamingText: StateFlow<String> = _streamingText.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _currentStatus = MutableStateFlow<String?>(null)
    val currentStatus: StateFlow<String?> = _currentStatus.asStateFlow()

    private val _pendingConfirmation = MutableStateFlow<ConfirmationRequest?>(null)
    val pendingConfirmation: StateFlow<ConfirmationRequest?> = _pendingConfirmation.asStateFlow()

    /** План шагов, ожидающий решения пользователя (null — нет ожидания). */
    private val _pendingPlan = MutableStateFlow<PlanProposal?>(null)
    val pendingPlan: StateFlow<PlanProposal?> = _pendingPlan.asStateFlow()

    private val _hasStoragePermission = MutableStateFlow(false)
    val hasStoragePermission: StateFlow<Boolean> = _hasStoragePermission.asStateFlow()

    private var conversationJson = JSONArray()

    @Volatile
    private var stopRequested = false

    /** First-run диалог «Выдать разрешения»: показывается один раз при входе без прав. */
    private val _showStoragePrompt = MutableStateFlow(false)
    val showStoragePrompt: StateFlow<Boolean> = _showStoragePrompt.asStateFlow()

    init {
        checkStoragePermission()
        maybeShowStoragePrompt()
        viewModelScope.launch {
            val empty = repository.getRecentEmptySession()
            val session = empty ?: repository.observeSessions().first().firstOrNull() ?: repository.createSession()
            loadSession(session.id)
        }
    }

    // --- Доступы ---

    fun checkStoragePermission() {
        val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                getApplication(),
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        _hasStoragePermission.value = granted
        // Право выдали на системном экране — first-run диалог больше не нужен.
        if (granted) _showStoragePrompt.value = false
    }

    /** Закрыть first-run диалог (в т.ч. кнопкой «Позже»). Больше не показываем. */
    fun dismissStoragePrompt() {
        _showStoragePrompt.value = false
        getApplication<Application>().getSharedPreferences(STORAGE_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_STORAGE_PROMPT_SHOWN, true)
            .apply()
    }

    private fun maybeShowStoragePrompt() {
        val shown = getApplication<Application>().getSharedPreferences(STORAGE_PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_STORAGE_PROMPT_SHOWN, false)
        if (!_hasStoragePermission.value && !shown) {
            _showStoragePrompt.value = true
        }
    }

    // --- Сессии ---

    fun setSessionsQuery(query: String) {
        _sessionsQuery.value = query
    }

    fun openSession(sessionId: String) {
        if (_isRunning.value) return
        viewModelScope.launch { loadSession(sessionId) }
    }

    fun startNewChat() {
        if (_isRunning.value) return
        viewModelScope.launch {
            // Если в текущем чате ещё нет пользовательских сообщений — не создаём дубликат
            if (_messages.value.none { it.role != MessageRole.SYSTEM_INFO }) {
                return@launch
            }
            val emptySession = repository.getRecentEmptySession()
            val session = emptySession ?: repository.createSession()
            loadSession(session.id)
        }
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            repository.deleteSession(sessionId)
            if (_currentSessionId.value == sessionId) {
                val remaining = repository.observeSessions().first()
                val next = remaining.firstOrNull() ?: repository.createSession()
                loadSession(next.id)
            }
        }
    }

    fun renameSession(sessionId: String, title: String) {
        val clean = title.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            repository.renameSession(sessionId, clean)
            if (_currentSessionId.value == sessionId) {
                _currentSessionTitle.value = clean
            }
        }
    }

    private suspend fun loadSession(sessionId: String) {
        val session = repository.getSession(sessionId) ?: return
        _currentSessionId.value = session.id
        _currentSessionTitle.value = session.title
        _streamingText.value = ""
        _currentStatus.value = null

        conversationJson = if (session.contextJson.isBlank()) {
            freshContext()
        } else {
            try {
                JSONArray(session.contextJson)
            } catch (e: Exception) {
                freshContext()
            }
        }

        _messages.value = repository.loadMessages(session.id).map { it.toChatMessage() }
    }

    // --- Провайдеры и модели ---

    fun selectProvider(providerId: String) {
        providerStore.setActiveProviderId(providerId)
        _activeProviderId.value = providerId
    }

    /** Выбор модели переключает и провайдера — одной операцией. */
    fun selectModel(providerId: String, model: String) {
        val provider = _providers.value.firstOrNull { it.id == providerId } ?: return
        val modelId = model.trim()
        if (modelId.isBlank()) return

        // ID, введённый вручную, дописываем в список — иначе он потеряется
        // при следующем обновлении списка.
        val models = if (provider.models.any { it.id == modelId }) {
            provider.models
        } else {
            provider.models + ModelInfo(id = modelId)
        }

        _providers.value = providerStore.upsert(
            provider.copy(models = models, selectedModel = modelId)
        )
        providerStore.setActiveProviderId(providerId)
        _activeProviderId.value = providerId
    }

    /** Обновление списка по сохранённым ключу и Base URL провайдера. */
    fun refreshModels(providerId: String, onResult: (String) -> Unit = {}) {
        val provider = _providers.value.firstOrNull { it.id == providerId } ?: return
        fetchModels(providerId, provider.baseUrl, provider.apiKey, onResult)
    }

    /**
     * Загрузка списка моделей с явными baseUrl и ключом.
     * Нужна для авто-подгрузки: пользователь ещё не нажал «Сохранить»,
     * поэтому проверяем именно тот ключ, который он ввёл.
     */
    fun fetchModels(
        providerId: String,
        baseUrl: String,
        apiKey: String,
        onResult: (String) -> Unit = {}
    ) {
        if (baseUrl.isBlank() || apiKey.isBlank()) {
            onResult("Нужны Base URL и API Key")
            return
        }

        _modelsLoadingFor.value = providerId
        viewModelScope.launch {
            val currentSettings = _settings.value
            val result = ModelCatalog.fetchModels(
                baseUrl = baseUrl,
                apiKey = apiKey,
                reverseProxyUrl = currentSettings.reverseProxyUrl,
                timeoutSeconds = currentSettings.connectTimeoutSeconds,
                customHeaders = currentSettings.customHeaders
            )

            result.onSuccess { models ->
                val provider = _providers.value.firstOrNull { it.id == providerId }
                if (provider != null) {
                    val selected = provider.selectedModel
                        .takeIf { current -> models.any { it.id == current } }
                        ?: models.first().id
                    _providers.value = providerStore.upsert(
                        provider.copy(models = models, selectedModel = selected)
                    )
                }
                onResult("Моделей: ${models.size}")
            }.onFailure { error ->
                onResult("Не удалось загрузить модели: ${error.localizedMessage}")
            }

            _modelsLoadingFor.value = null
        }
    }

    fun saveProvider(provider: Provider) {
        _providers.value = providerStore.upsert(provider)
    }

    fun deleteProvider(providerId: String) {
        _providers.value = providerStore.remove(providerId)
        _activeProviderId.value = providerStore.activeProviderId()
    }

    // --- Настройки ---

    fun updateSettings(newSettings: AppSettings) {
        _settings.value = newSettings
        settingsStore.save(newSettings)
    }

    fun resetSystemPrompt() {
        updateSettings(_settings.value.copy(systemPrompt = AppSettings.DEFAULT_SYSTEM_PROMPT))
    }

    fun setThemeMode(mode: AppThemeMode) {
        viewModelScope.launch { themeStore.updateThemeMode(mode) }
    }

    fun setColorSource(source: ColorSource) {
        viewModelScope.launch { themeStore.updateColorSource(source) }
    }

    fun setCustomSeedColor(color: Int) {
        viewModelScope.launch { themeStore.updateCustomSeedColor(color) }
    }

    fun setAmoled(isAmoled: Boolean) {
        viewModelScope.launch { themeStore.updateAmoled(isAmoled) }
    }

    /**
     * Проверка связи: пингует заданный reverse proxy или официальный baseUrl активного провайдера
     * с учётом настроенного таймаута и кастомных заголовков.
     */
    fun testConnection() {
        if (_connectionTestState.value.isTesting) return
        val currentSettings = _settings.value
        val provider = activeProvider.value
        val providerName = provider?.name.orEmpty().ifBlank { "API" }
        val targetUrl = currentSettings.reverseProxyUrl.ifBlank {
            provider?.baseUrl.orEmpty()
        }
        if (targetUrl.isBlank()) {
            _connectionTestState.value = ConnectionTestState(
                isTesting = false,
                result = ConnectionTestResult(
                    isSuccess = false,
                    isReachable = false,
                    statusCode = null,
                    latencyMs = 0L,
                    message = "Укажите адрес прокси или настройте провайдера $providerName",
                    targetEndpoint = ""
                )
            )
            return
        }

        val apiKey = provider?.apiKey.orEmpty()
        val timeout = currentSettings.connectTimeoutSeconds
        val customHeaders = currentSettings.customHeaders

        _connectionTestState.value = ConnectionTestState(isTesting = true)
        viewModelScope.launch {
            val result = apiClient.testConnection(
                targetUrl = targetUrl,
                apiKey = apiKey,
                timeoutSeconds = timeout,
                customHeaders = customHeaders,
                providerName = providerName
            )
            _connectionTestState.value = ConnectionTestState(isTesting = false, result = result)
        }
    }

    fun clearConnectionTestResult() {
        _connectionTestState.value = ConnectionTestState()
    }

    // --- Агент ---

    fun resolveConfirmation(confirmed: Boolean) {
        val current = _pendingConfirmation.value
        _pendingConfirmation.value = null
        current?.onDecision?.invoke(confirmed)
    }

    /** Решение пользователя по плану шагов. */
    fun resolvePlan(approved: Boolean) {
        val current = _pendingPlan.value
        _pendingPlan.value = null
        current?.onDecision?.invoke(approved)
    }

    fun stopGeneration() {
        stopRequested = true
        apiClient.cancelActive()
        // Разблокировать ожидание решения по плану: дальше проверка stopRequested остановит цикл.
        resolvePlan(false)
        _currentStatus.value = "Останавливаю"
    }

    fun sendMessage(prompt: String) {
        val text = prompt.trim()
        if (text.isEmpty() || _isRunning.value) return

        val provider = activeProvider.value
        if (provider == null || provider.apiKey.isBlank()) {
            appendSystemInfo(
                "Ключ провайдера не задан — модель не ответит. Укажи API Key в настройках.",
                MessageAction.OPEN_SETTINGS
            )
            return
        }

        val model = provider.activeModel
        if (model.isBlank()) {
            appendSystemInfo(
                "У провайдера ${provider.name} не выбрана модель.",
                MessageAction.OPEN_MODELS
            )
            return
        }

        checkStoragePermission()
        if (!_hasStoragePermission.value) {
            appendSystemInfo(
                "Нет доступа ко всем файлам: инструменты работы с файлами не сработают.",
                MessageAction.GRANT_STORAGE
            )
        }

        val sessionId = _currentSessionId.value
        if (sessionId == null) {
            viewModelScope.launch {
                val session = repository.createSession()
                loadSession(session.id)
                sendMessage(text)
            }
            return
        }

        stopRequested = false
        val userMessageId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val isFirstUserMessage = _messages.value.none { it.role == MessageRole.USER }

        _messages.value = _messages.value + ChatMessage(
            id = userMessageId,
            role = MessageRole.USER,
            content = text,
            timestamp = now
        )
        _isRunning.value = true
        _streamingText.value = ""

        viewModelScope.launch {
            try {
                repository.saveMessage(
                    MessageEntity(
                        id = userMessageId,
                        sessionId = sessionId,
                        role = MessageRole.USER.name,
                        content = text,
                        createdAt = now
                    )
                )
                if (isFirstUserMessage) {
                    val title = text.replace('\n', ' ').take(ChatRepository.MAX_TITLE_LENGTH)
                    repository.renameSession(sessionId, title)
                    _currentSessionTitle.value = title
                }

                conversationJson.put(
                    JSONObject().apply {
                        put("role", "user")
                        put("content", text)
                    }
                )
                persistContext(sessionId)

                // Планирование: сначала план шагов + подтверждение, потом исполнение.
                // Слабым моделям (Flash Lite и подобные) это сильно поднимает надёжность.
                val planNote = if (_settings.value.planningEnabled) {
                    requestPlanApproval(sessionId, provider, model, text)
                } else {
                    null
                }
                if (stopRequested) {
                    finalizeStreamedText(sessionId, stopped = true)
                    return@launch
                }
                if (planNote != null) {
                    conversationJson.put(
                        JSONObject().apply {
                            put("role", "user")
                            put("content", planNote)
                        }
                    )
                    persistContext(sessionId)
                }

                runAgentLoop(sessionId, provider, model)
            } finally {
                _isRunning.value = false
                _currentStatus.value = null
                _streamingText.value = ""
                apiClient.cancelActive()
            }
        }
    }

    /**
     * Фаза планирования: одним дешёвым нетуловым запросом просим модель разложить
     * задачу на шаги, показываем план карточкой и ждём решения пользователя.
     *
     * Возвращает текст для подстановки в контекст (утверждённый план) или null:
     * null = исполнять цикл как обычно без плана (план выключен, не нужен,
     * не распознан, отклонён или остановка).
     */
    private suspend fun requestPlanApproval(
        sessionId: String,
        provider: Provider,
        model: String,
        userText: String
    ): String? {
        val currentSettings = _settings.value
        _currentStatus.value = "Составляю план"

        val planningMessages = JSONArray().apply {
            put(
                JSONObject().apply {
                    put("role", "system")
                    put("content", PLAN_SYSTEM_PROMPT)
                }
            )
            put(
                JSONObject().apply {
                    put("role", "user")
                    put("content", userText)
                }
            )
        }

        val metrics = GenerationMetrics()
        val result = apiClient.sendChatCompletion(
            baseUrl = provider.baseUrl,
            apiKey = provider.apiKey,
            modelName = model,
            messages = planningMessages,
            reverseProxyUrl = currentSettings.reverseProxyUrl,
            connectTimeoutSeconds = currentSettings.connectTimeoutSeconds,
            responseTimeoutSeconds = currentSettings.responseTimeoutSeconds,
            customHeaders = currentSettings.customHeaders,
            // Детерминированный план: строгий формат важнее креативности.
            temperature = 0.1,
            streaming = false,
            metrics = metrics,
            withTools = false
        )

        val planText = when (result) {
            is AIResponseResult.TextResult -> result.text.trim()
            is AIResponseResult.Cancelled -> return null
            is AIResponseResult.Error -> {
                appendSystemInfo("План не составлен (${result.message}). Работаю без плана.", null)
                return null
            }
            is AIResponseResult.ToolCallsResult -> {
                // Без тулов этого быть не должно; fallback — свободный режим.
                return null
            }
        }

        if (planText.isBlank()) return null
        if (planText.contains(NO_TOOLS_MARKER)) return null

        val steps = parsePlanSteps(planText)
        if (steps.isEmpty()) {
            appendSystemInfo("План не распознан. Работаю без плана.", null)
            return null
        }

        val planMessageId = UUID.randomUUID().toString()
        val planMessage = ChatMessage(
            id = planMessageId,
            role = MessageRole.PLAN,
            content = planText
        )
        _messages.value = _messages.value + planMessage
        repository.saveMessage(
            MessageEntity(
                id = planMessageId,
                sessionId = sessionId,
                role = MessageRole.PLAN.name,
                content = planText,
                createdAt = System.currentTimeMillis()
            )
        )

        _currentStatus.value = "Жду подтверждения плана"
        val approved = suspendCancellableCoroutine { continuation ->
            _pendingPlan.value = PlanProposal(
                id = planMessageId,
                steps = steps,
                rawText = planText,
                onDecision = { decision ->
                    if (continuation.isActive) continuation.resume(decision)
                }
            )
        }
        _pendingPlan.value = null

        return if (approved && !stopRequested) {
            buildString {
                append("Утверждённый пользователем план, строго следуй ему по шагам ")
                append("(не пропускай шаги, не выдумывай свои инструменты):\n")
                steps.forEachIndexed { i, step -> append("${i + 1}. $step\n") }
            }
        } else {
            appendSystemInfo("План отклонён. Работаю без плана.", null)
            null
        }
    }

    /** Строки вида "1. ..." / "1) ..." — остальное игнорируем. */
    private fun parsePlanSteps(text: String): List<String> {
        val stepRegex = Regex("""^\s*\d+[.)]\s*(.+?)\s*$""")
        return text.lines()
            .mapNotNull { line -> stepRegex.find(line)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() } }
            .take(MAX_PLAN_STEPS)
    }

    private suspend fun runAgentLoop(sessionId: String, provider: Provider, model: String) {
        val currentSettings = _settings.value
        val trackStats = currentSettings.showStats
        val turnStartedAt = System.currentTimeMillis()

        // Суммарные счётчики по всей задаче — уходят в плашку под финальным ответом
        var totalPromptTokens = 0
        var totalCompletionTokens = 0
        var step = 0

        while (step < currentSettings.maxSteps && !stopRequested) {
            step++
            _currentStatus.value = "Шаг $step · $model"

            val metrics = GenerationMetrics()

            val result = apiClient.sendChatCompletion(
                baseUrl = provider.baseUrl,
                apiKey = provider.apiKey,
                modelName = model,
                messages = conversationJson,
                reverseProxyUrl = currentSettings.reverseProxyUrl,
                connectTimeoutSeconds = currentSettings.connectTimeoutSeconds,
                responseTimeoutSeconds = currentSettings.responseTimeoutSeconds,
                customHeaders = currentSettings.customHeaders,
                temperature = currentSettings.temperature.toDouble(),
                streaming = true,
                listener = object : OpenAIClient.StreamListener {
                    override fun onTextDelta(delta: String) {
                        _streamingText.value = _streamingText.value + delta
                    }

                    override fun onToolCallStarted(name: String) {
                        _currentStatus.value = ToolDispatcher.actionTitle(name, "{}")
                    }

                    override fun onRetry(message: String, delayMillis: Long) {
                        _currentStatus.value = "Повтор через ${delayMillis / 1000} с · $message"
                    }
                },
                metrics = metrics
            )

            metrics.promptTokens?.let { totalPromptTokens += it }
            metrics.completionTokens?.let { totalCompletionTokens += it }

            when (result) {
                is AIResponseResult.Cancelled -> {
                    finalizeStreamedText(sessionId, stopped = true)
                    attachTurnStats(sessionId, turnStartedAt, totalPromptTokens, totalCompletionTokens, trackStats)
                    return
                }

                is AIResponseResult.Error -> {
                    finalizeStreamedText(sessionId, stopped = false)
                    appendSystemInfo(result.message, null)
                    attachTurnStats(sessionId, turnStartedAt, totalPromptTokens, totalCompletionTokens, trackStats)
                    return
                }

                is AIResponseResult.TextResult -> {
                    val reply = result.text.ifBlank { "Готово." }
                    _streamingText.value = ""
                    saveAssistantMessage(
                        sessionId = sessionId,
                        text = reply,
                        reasoningText = metrics.reasoningText.takeIf { it.isNotBlank() },
                        reasoningMs = metrics.reasoningMillis.takeIf { it > 0L },
                        completionTokens = metrics.completionTokens,
                        tokensPerSecond = metrics.tokensPerSecond
                    )
                    conversationJson.put(
                        JSONObject().apply {
                            put("role", "assistant")
                            put("content", reply)
                        }
                    )
                    persistContext(sessionId)
                    attachTurnStats(sessionId, turnStartedAt, totalPromptTokens, totalCompletionTokens, trackStats)
                    return
                }

                is AIResponseResult.ToolCallsResult -> {
                    val preText = result.assistantMessageJson.optString("content")
                    if (preText.isNotBlank()) {
                        _streamingText.value = ""
                        saveAssistantMessage(
                            sessionId = sessionId,
                            text = preText,
                            reasoningText = metrics.reasoningText.takeIf { it.isNotBlank() },
                            reasoningMs = metrics.reasoningMillis.takeIf { it > 0L }
                        )
                    }
                    conversationJson.put(result.assistantMessageJson)
                    persistContext(sessionId)

                    for (call in result.toolCalls) {
                        if (stopRequested) break
                        executeToolCall(sessionId, call)
                    }
                }
            }
        }

        if (stopRequested) {
            finalizeStreamedText(sessionId, stopped = true)
        } else {
            appendSystemInfo(
                "Достигнут лимит шагов (${currentSettings.maxSteps}). Увеличь его в настройках агента.",
                null
            )
        }
        attachTurnStats(sessionId, turnStartedAt, totalPromptTokens, totalCompletionTokens, trackStats)
    }

    /**
     * Дописывает в последний ответ общее время пайплайна и суммарные токены.
     * Плашка «сколько заняла вся задача» вешается именно на финальный ответ.
     */
    private suspend fun attachTurnStats(
        sessionId: String,
        turnStartedAt: Long,
        promptTokens: Int,
        completionTokens: Int,
        enabled: Boolean
    ) {
        if (!enabled) return

        val lastAssistant = _messages.value.lastOrNull { it.role == MessageRole.ASSISTANT } ?: return
        val updated = lastAssistant.copy(
            durationMs = System.currentTimeMillis() - turnStartedAt,
            promptTokens = promptTokens.takeIf { it > 0 },
            completionTokens = completionTokens.takeIf { it > 0 }
        )

        _messages.value = _messages.value.map { if (it.id == updated.id) updated else it }
        repository.saveMessage(updated.toEntity(sessionId))
    }

    private suspend fun executeToolCall(sessionId: String, call: ToolCall) {
        val title = ToolDispatcher.actionTitle(call.name, call.arguments)
        _currentStatus.value = title

        val messageId = UUID.randomUUID().toString()
        _messages.value = _messages.value + ChatMessage(
            id = messageId,
            role = MessageRole.TOOL_EXECUTION,
            content = title,
            toolName = call.name,
            toolArgs = call.arguments,
            isRunning = true
        )

        var declined = false
        // Замеряем только саму операцию: время ожидания подтверждения пользователем
        // в это число не входит, иначе тайминг теряет смысл.
        var toolDurationMs: Long? = null

        val output = if (ToolDispatcher.isCriticalOperation(call.name)) {
            _currentStatus.value = "Жду подтверждения"
            val info = ToolDispatcher.describeCriticalAction(call.name, call.arguments)

            val approved = suspendCancellableCoroutine { continuation ->
                _pendingConfirmation.value = ConfirmationRequest(
                    toolName = call.name,
                    title = info.title,
                    details = info.details,
                    warning = info.warning,
                    preview = info.preview,
                    onDecision = { decision ->
                        if (continuation.isActive) continuation.resume(decision)
                    }
                )
            }
            _pendingConfirmation.value = null

            if (approved && !stopRequested) {
                _currentStatus.value = title
                val startedAt = System.currentTimeMillis()
                val result = ToolDispatcher.execute(call.name, call.arguments, getApplication())
                toolDurationMs = System.currentTimeMillis() - startedAt
                result
            } else {
                declined = true
                "Пользователь отклонил операцию. Предложи альтернативу или остановись."
            }
        } else {
            val startedAt = System.currentTimeMillis()
            val result = ToolDispatcher.execute(call.name, call.arguments, getApplication())
            toolDurationMs = System.currentTimeMillis() - startedAt
            result
        }

        val isError = !declined && (
                output.startsWith("Ошибка") ||
                        output.startsWith("Не удалось") ||
                        output.contains("Exception", ignoreCase = true)
                )
        val summary = ToolDispatcher.summarizeResult(output)

        _messages.value = _messages.value.map { message ->
            if (message.id == messageId) {
                message.copy(
                    toolOutput = output,
                    toolSummary = summary,
                    toolDurationMs = toolDurationMs,
                    isRunning = false,
                    isError = isError,
                    isDeclined = declined
                )
            } else {
                message
            }
        }

        repository.saveMessage(
            MessageEntity(
                id = messageId,
                sessionId = sessionId,
                role = MessageRole.TOOL_EXECUTION.name,
                content = title,
                toolName = call.name,
                toolArgs = call.arguments,
                toolOutput = output,
                isError = isError,
                isDeclined = declined,
                createdAt = System.currentTimeMillis(),
                toolDurationMs = toolDurationMs
            )
        )

        conversationJson.put(
            JSONObject().apply {
                put("role", "tool")
                put("tool_call_id", call.id)
                put("name", call.name)
                put("content", output)
            }
        )
        persistContext(sessionId)
    }

    /** Сохраняет текст, который успел накопиться в стриме, если ответ оборвался. */
    private suspend fun finalizeStreamedText(sessionId: String, stopped: Boolean) {
        val partial = _streamingText.value.trim()
        _streamingText.value = ""

        if (partial.isBlank()) {
            if (stopped) appendSystemInfo("Генерация остановлена.", null)
            return
        }

        val text = if (stopped) "$partial\n\n_Остановлено._" else partial
        saveAssistantMessage(sessionId, text)
        conversationJson.put(
            JSONObject().apply {
                put("role", "assistant")
                put("content", partial)
            }
        )
        persistContext(sessionId)
    }

    private suspend fun saveAssistantMessage(
        sessionId: String,
        text: String,
        reasoningText: String? = null,
        reasoningMs: Long? = null,
        completionTokens: Int? = null,
        tokensPerSecond: Double? = null
    ) {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val message = ChatMessage(
            id = id,
            role = MessageRole.ASSISTANT,
            content = text,
            timestamp = now,
            reasoningText = reasoningText,
            reasoningMs = reasoningMs,
            completionTokens = completionTokens,
            tokensPerSecond = tokensPerSecond
        )
        _messages.value = _messages.value + message
        repository.saveMessage(message.toEntity(sessionId))
    }

    private suspend fun persistContext(sessionId: String) {
        repository.updateContext(sessionId, conversationJson.toString())
    }

    private fun freshContext(): JSONArray = JSONArray().apply {
        val basePrompt = _settings.value.systemPrompt
        val effectivePrompt = if (basePrompt.contains(AppSettings.MANDATORY_PROMPT_SUFFIX)) {
            basePrompt
        } else {
            basePrompt.trimEnd() + "\n- " + AppSettings.MANDATORY_PROMPT_SUFFIX
        }
        put(
            JSONObject().apply {
                put("role", "system")
                put("content", effectivePrompt)
            }
        )
    }

    private fun appendSystemInfo(text: String, action: MessageAction?) {
        _messages.value = _messages.value + ChatMessage(
            role = MessageRole.SYSTEM_INFO,
            content = text,
            isError = action == MessageAction.OPEN_SETTINGS,
            action = action
        )
    }

    private fun ChatMessage.toEntity(sessionId: String): MessageEntity = MessageEntity(
        id = id,
        sessionId = sessionId,
        role = role.name,
        content = content,
        toolName = toolName,
        toolArgs = toolArgs,
        toolOutput = toolOutput,
        isError = isError,
        isDeclined = isDeclined,
        createdAt = timestamp,
        durationMs = durationMs,
        toolDurationMs = toolDurationMs,
        reasoningText = reasoningText,
        reasoningMs = reasoningMs,
        promptTokens = promptTokens,
        completionTokens = completionTokens,
        tokensPerSecond = tokensPerSecond
    )

    private fun MessageEntity.toChatMessage(): ChatMessage = ChatMessage(
        id = id,
        role = try {
            MessageRole.valueOf(role)
        } catch (e: Exception) {
            MessageRole.SYSTEM_INFO
        },
        content = content,
        toolName = toolName,
        toolArgs = toolArgs,
        toolOutput = toolOutput,
        isError = isError,
        isDeclined = isDeclined,
        timestamp = createdAt,
        durationMs = durationMs,
        toolDurationMs = toolDurationMs,
        reasoningText = reasoningText,
        reasoningMs = reasoningMs,
        promptTokens = promptTokens,
        completionTokens = completionTokens,
        tokensPerSecond = tokensPerSecond
    )
}
