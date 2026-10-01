package com.runner.app.ui

import android.app.Application
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.runner.app.data.AIResponseResult
import com.runner.app.data.AppSettings
import com.runner.app.data.ChatRepository
import com.runner.app.data.ModelCatalog
import com.runner.app.data.ModelInfo
import com.runner.app.data.OpenAIClient
import com.runner.app.data.Provider
import com.runner.app.data.ProviderStore
import com.runner.app.data.SettingsStore
import com.runner.app.data.ToolCall
import com.runner.app.data.db.ChatDatabase
import com.runner.app.data.db.MessageEntity
import com.runner.app.data.db.SessionEntity
import com.runner.app.tools.ToolDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
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
    SYSTEM_INFO
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
    val timestamp: Long = System.currentTimeMillis()
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

    private val _hasStoragePermission = MutableStateFlow(false)
    val hasStoragePermission: StateFlow<Boolean> = _hasStoragePermission.asStateFlow()

    private var conversationJson = JSONArray()

    @Volatile
    private var stopRequested = false

    init {
        checkStoragePermission()
        viewModelScope.launch {
            val existing = repository.observeSessions().first()
            val session = existing.firstOrNull() ?: repository.createSession()
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
            val session = repository.createSession()
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
            val result = ModelCatalog.fetchModels(
                baseUrl = baseUrl,
                apiKey = apiKey,
                reverseProxyUrl = _settings.value.reverseProxyUrl
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

    // --- Агент ---

    fun resolveConfirmation(confirmed: Boolean) {
        val current = _pendingConfirmation.value
        _pendingConfirmation.value = null
        current?.onDecision?.invoke(confirmed)
    }

    fun stopGeneration() {
        stopRequested = true
        apiClient.cancelActive()
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

                runAgentLoop(sessionId, provider, model)
            } finally {
                _isRunning.value = false
                _currentStatus.value = null
                _streamingText.value = ""
                apiClient.cancelActive()
            }
        }
    }

    private suspend fun runAgentLoop(sessionId: String, provider: Provider, model: String) {
        val currentSettings = _settings.value
        var step = 0

        while (step < currentSettings.maxSteps && !stopRequested) {
            step++
            _currentStatus.value = "Шаг $step · $model"

            val result = apiClient.sendChatCompletion(
                baseUrl = provider.baseUrl,
                apiKey = provider.apiKey,
                modelName = model,
                messages = conversationJson,
                reverseProxyUrl = currentSettings.reverseProxyUrl,
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
                }
            )

            when (result) {
                is AIResponseResult.Cancelled -> {
                    finalizeStreamedText(sessionId, stopped = true)
                    return
                }

                is AIResponseResult.Error -> {
                    finalizeStreamedText(sessionId, stopped = false)
                    appendSystemInfo(result.message, null)
                    return
                }

                is AIResponseResult.TextResult -> {
                    val reply = result.text.ifBlank { "Готово." }
                    _streamingText.value = ""
                    saveAssistantMessage(sessionId, reply)
                    conversationJson.put(
                        JSONObject().apply {
                            put("role", "assistant")
                            put("content", reply)
                        }
                    )
                    persistContext(sessionId)
                    return
                }

                is AIResponseResult.ToolCallsResult -> {
                    val preText = result.assistantMessageJson.optString("content")
                    if (preText.isNotBlank()) {
                        _streamingText.value = ""
                        saveAssistantMessage(sessionId, preText)
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
                ToolDispatcher.execute(call.name, call.arguments, getApplication())
            } else {
                declined = true
                "Пользователь отклонил операцию. Предложи альтернативу или остановись."
            }
        } else {
            ToolDispatcher.execute(call.name, call.arguments, getApplication())
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
                createdAt = System.currentTimeMillis()
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

    private suspend fun saveAssistantMessage(sessionId: String, text: String) {
        val id = UUID.randomUUID().toString()
        _messages.value = _messages.value + ChatMessage(
            id = id,
            role = MessageRole.ASSISTANT,
            content = text
        )
        repository.saveMessage(
            MessageEntity(
                id = id,
                sessionId = sessionId,
                role = MessageRole.ASSISTANT.name,
                content = text,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    private suspend fun persistContext(sessionId: String) {
        repository.updateContext(sessionId, conversationJson.toString())
    }

    private fun freshContext(): JSONArray = JSONArray().apply {
        put(
            JSONObject().apply {
                put("role", "system")
                put("content", _settings.value.systemPrompt)
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
        timestamp = createdAt
    )
}
