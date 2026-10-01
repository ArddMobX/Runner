package com.runner.app.ui

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.runner.app.data.AIResponseResult
import com.runner.app.data.OpenAIClient
import com.runner.app.tools.ToolDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class MessageRole {
    USER,
    ASSISTANT,
    TOOL_EXECUTION,
    SYSTEM_INFO
}

data class ConfirmationRequest(
    val id: String = UUID.randomUUID().toString(),
    val toolName: String,
    val title: String,
    val details: String,
    val warning: String,
    val onDecision: (Boolean) -> Unit
)

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: MessageRole,
    val content: String,
    val toolName: String? = null,
    val toolArgs: String? = null,
    val toolOutput: String? = null,
    val isRunning: Boolean = false,
    val isError: Boolean = false,
    val isDeclined: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("runner_prefs", Context.MODE_PRIVATE)
    private val apiClient = OpenAIClient()

    // Default provider without IP discrimination (OpenRouter)
    private val defaultBaseUrl = "https://openrouter.ai/api/v1"
    private val defaultModel = "meta-llama/llama-3.3-70b-instruct"

    private val _baseUrl = MutableStateFlow(
        prefs.getString("api_base_url", defaultBaseUrl) ?: defaultBaseUrl
    )
    val baseUrl: StateFlow<String> = _baseUrl.asStateFlow()

    private val _apiKey = MutableStateFlow(
        prefs.getString("api_key", null)
            ?: prefs.getString("groq_api_key", "")
            ?: ""
    )
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    private val _modelName = MutableStateFlow(
        prefs.getString("api_model_name", defaultModel) ?: defaultModel
    )
    val modelName: StateFlow<String> = _modelName.asStateFlow()

    private val _reverseProxyUrl = MutableStateFlow(
        prefs.getString("api_reverse_proxy_url", "") ?: ""
    )
    val reverseProxyUrl: StateFlow<String> = _reverseProxyUrl.asStateFlow()

    private val _pendingConfirmation = MutableStateFlow<ConfirmationRequest?>(null)
    val pendingConfirmation: StateFlow<ConfirmationRequest?> = _pendingConfirmation.asStateFlow()

    private val _hasStoragePermission = MutableStateFlow(false)
    val hasStoragePermission: StateFlow<Boolean> = _hasStoragePermission.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _currentStatus = MutableStateFlow<String?>(null)
    val currentStatus: StateFlow<String?> = _currentStatus.asStateFlow()

    private val conversationJson = JSONArray()

    init {
        checkStoragePermission()
        resetConversationContext()

        _messages.value = listOf(
            ChatMessage(
                role = MessageRole.ASSISTANT,
                content = "Привет! Я Runner — мобильный агент для Android. Могу исследовать файлы, распаковывать архивы, читать и создавать документы или выполнять команды терминала. Чем помочь?"
            )
        )
    }

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

    fun saveSettings(
        newBaseUrl: String,
        newApiKey: String,
        newModelName: String,
        newReverseProxyUrl: String = ""
    ) {
        val bUrl = newBaseUrl.trim()
        val key = newApiKey.trim()
        val model = newModelName.trim()
        val proxy = newReverseProxyUrl.trim()

        prefs.edit()
            .putString("api_base_url", bUrl)
            .putString("api_key", key)
            .putString("api_model_name", model)
            .putString("api_reverse_proxy_url", proxy)
            .apply()

        _baseUrl.value = bUrl
        _apiKey.value = key
        _modelName.value = model
        _reverseProxyUrl.value = proxy
    }

    fun resolveConfirmation(confirmed: Boolean) {
        val current = _pendingConfirmation.value
        _pendingConfirmation.value = null
        current?.onDecision?.invoke(confirmed)
    }

    private fun resetConversationContext() {
        while (conversationJson.length() > 0) {
            conversationJson.remove(0)
        }
        val systemPrompt = JSONObject().apply {
            put("role", "system")
            put(
                "content",
                """
                Ты автономный мобильный агент Runner для Android. У тебя есть доступ к локальным системным инструментам для работы с устройством:
                1. Аналитика памяти и поиск: get_storage_summary, find_largest_files, find_junk_files, search_files.
                2. Работа с файлами: get_folder_summary, read_file, write_file, delete_file, create_dir, move_file, copy_file.
                3. Архивы: create_archive, extract_archive, organize_downloads.
                4. Буфер обмена и система: clipboard_read, clipboard_write, run_shell_command.

                ПРАВИЛА И ПРИОРИТЕТЫ:
                - Всегда используй специализированные локальные агрегаторы (get_storage_summary, find_largest_files, find_junk_files) для анализа файлов и памяти устройства. Никогда не пытайся перечислять сотни файлов поштучно.
                - Вывод инструментов защищен лимитом (до 35 элементов / 3.5 КБ). Уточняй запросы при необходимости.
                - Деструктивные операции (удаление, перемещение, запись, сортировка, shell) запрашивают подтверждение пользователя (Human-in-the-Loop). Если пользователь отклонил операцию — прими это вежливо и предложи альтернативу или остановись.
                - Оформляй ответы в Markdown (жирный текст, списки, блоки кода ``` с указанием языка).
                - Если используешь математические формулы, оформляй их в синтаксисе LaTeX: в блоках '$$ ... $$' или внутри строки '$ ... $'.
                - Отвечай кратко, чётко и вежливо на русском языке.
                """.trimIndent()
            )
        }
        conversationJson.put(systemPrompt)
    }

    fun sendMessage(prompt: String) {
        val userText = prompt.trim()
        if (userText.isEmpty()) return

        if (_apiKey.value.isBlank()) {
            _messages.value = _messages.value + ChatMessage(
                role = MessageRole.SYSTEM_INFO,
                content = "API ключ не задан. Перейдите во вкладку «Настройки» и укажите ключ провайдера.",
                isError = true
            )
            return
        }

        checkStoragePermission()
        if (!_hasStoragePermission.value) {
            _messages.value = _messages.value + ChatMessage(
                role = MessageRole.SYSTEM_INFO,
                content = "Нет доступа к управлению файлами (MANAGE_EXTERNAL_STORAGE). Предоставьте разрешение в «Настройках».",
                isError = true
            )
            return
        }

        _messages.value = _messages.value + ChatMessage(role = MessageRole.USER, content = userText)
        conversationJson.put(JSONObject().apply {
            put("role", "user")
            put("content", userText)
        })

        viewModelScope.launch {
            _isLoading.value = true
            val activeModel = _modelName.value.ifBlank { "моделью" }
            _currentStatus.value = "Запрос к $activeModel..."

            val maxSteps = 5
            var step = 0

            while (step < maxSteps) {
                step++
                if (step > 1) {
                    // Inter-step throttling delay (750ms) to avoid rate limits
                    delay(750)
                }

                val result = apiClient.sendChatCompletion(
                    baseUrl = _baseUrl.value,
                    apiKey = _apiKey.value,
                    modelName = _modelName.value,
                    messages = conversationJson,
                    reverseProxyUrl = _reverseProxyUrl.value
                )

                when (result) {
                    is AIResponseResult.Error -> {
                        _messages.value = _messages.value + ChatMessage(
                            role = MessageRole.SYSTEM_INFO,
                            content = result.message,
                            isError = true
                        )
                        break
                    }
                    is AIResponseResult.TextResult -> {
                        val assistantReply = if (result.text.isBlank()) "Готово." else result.text
                        _messages.value = _messages.value + ChatMessage(
                            role = MessageRole.ASSISTANT,
                            content = assistantReply
                        )
                        conversationJson.put(JSONObject().apply {
                            put("role", "assistant")
                            put("content", assistantReply)
                        })
                        break
                    }
                    is AIResponseResult.ToolCallsResult -> {
                        conversationJson.put(result.assistantMessageJson)

                        for (call in result.toolCalls) {
                            val actionTitle = formatToolActionTitle(call.name, call.arguments)
                            _currentStatus.value = actionTitle

                            val msgId = UUID.randomUUID().toString()
                            _messages.value = _messages.value + ChatMessage(
                                id = msgId,
                                role = MessageRole.TOOL_EXECUTION,
                                content = actionTitle,
                                toolName = call.name,
                                toolArgs = call.arguments,
                                isRunning = true
                            )

                            val isCritical = ToolDispatcher.isCriticalOperation(call.name)
                            var wasDeclined = false

                            val toolOutput = if (isCritical) {
                                _currentStatus.value = "Ожидание подтверждения..."
                                val criticalInfo = ToolDispatcher.describeCriticalAction(call.name, call.arguments)
                                val confirmed = suspendCancellableCoroutine<Boolean> { cont ->
                                    _pendingConfirmation.value = ConfirmationRequest(
                                        toolName = call.name,
                                        title = criticalInfo.title,
                                        details = criticalInfo.details,
                                        warning = criticalInfo.warning,
                                        onDecision = { decision ->
                                            if (cont.isActive) {
                                                cont.resume(decision)
                                            }
                                        }
                                    )
                                }

                                if (confirmed) {
                                    _currentStatus.value = actionTitle
                                    ToolDispatcher.execute(call.name, call.arguments, getApplication())
                                } else {
                                    wasDeclined = true
                                    "Пользователь отклонил операцию. Предложи альтернативный вариант или остановись."
                                }
                            } else {
                                ToolDispatcher.execute(call.name, call.arguments, getApplication())
                            }

                            val isErr = if (wasDeclined) false else (toolOutput.startsWith("Ошибка") || toolOutput.startsWith("Не удалось") || toolOutput.contains("Exception", ignoreCase = true))

                            _messages.value = _messages.value.map { msg ->
                                if (msg.id == msgId) {
                                    msg.copy(
                                        content = if (wasDeclined) "$actionTitle (Отклонено)" else actionTitle,
                                        toolOutput = toolOutput,
                                        isRunning = false,
                                        isError = isErr,
                                        isDeclined = wasDeclined
                                    )
                                } else msg
                            }

                            conversationJson.put(JSONObject().apply {
                                put("role", "tool")
                                put("tool_call_id", call.id)
                                put("name", call.name)
                                put("content", toolOutput)
                            })
                        }

                        _currentStatus.value = "Обработка результата..."
                    }
                }
            }

            _isLoading.value = false
            _currentStatus.value = null
        }
    }

    fun clearChat() {
        resetConversationContext()
        _messages.value = listOf(
            ChatMessage(
                role = MessageRole.ASSISTANT,
                content = "История диалога очищена. Готов к новым задачам."
            )
        )
    }

    private fun formatToolActionTitle(toolName: String, argsJson: String): String {
        val args = try {
            if (argsJson.isBlank()) JSONObject() else JSONObject(argsJson)
        } catch (e: Exception) {
            JSONObject()
        }

        return when (toolName) {
            "get_storage_summary" -> "Анализ хранилища устройства"
            "find_largest_files" -> {
                val lim = args.optInt("limit", 10)
                "Поиск топ-$lim тяжелых файлов"
            }
            "find_junk_files" -> "Поиск мусора и временных файлов"
            "get_folder_summary" -> {
                val p = args.optString("path", "").ifBlank { "Download" }
                "Анализ папки '$p'"
            }
            "search_files" -> {
                val q = args.optString("query", "").trim()
                val ext = args.optString("extension", "").trim()
                when {
                    q.isNotBlank() && ext.isNotBlank() -> "Поиск '$q' (*.$ext)"
                    q.isNotBlank() -> "Поиск '$q'"
                    ext.isNotBlank() -> "Поиск *.$ext файлов"
                    else -> "Поиск файлов"
                }
            }
            "read_file" -> {
                val p = args.optString("path", "").substringAfterLast('/')
                "Чтение файла '$p'"
            }
            "write_file" -> {
                val p = args.optString("path", "").substringAfterLast('/')
                "Запись в '$p'"
            }
            "delete_file" -> {
                val p = args.optString("path", "").substringAfterLast('/')
                "Удаление '$p'"
            }
            "create_dir" -> {
                val p = args.optString("path", "").substringAfterLast('/')
                "Создание папки '$p'"
            }
            "move_file" -> {
                val s = args.optString("source_path", "").substringAfterLast('/')
                "Перемещение '$s'"
            }
            "copy_file" -> {
                val s = args.optString("source_path", "").substringAfterLast('/')
                "Копирование '$s'"
            }
            "create_archive" -> "Создание ZIP-архива"
            "extract_archive" -> {
                val p = args.optString("zip_path", "").substringAfterLast('/')
                "Распаковка архива '$p'"
            }
            "organize_downloads" -> {
                val cat = args.optString("category", "all")
                "Сортировка '$cat' в Download"
            }
            "clipboard_read" -> "Чтение буфера обмена"
            "clipboard_write" -> "Копирование в буфер обмена"
            "run_shell_command" -> {
                val cmd = args.optString("command", "").take(28)
                "Команда '$cmd...'"
            }
            else -> toolName
        }
    }
}
