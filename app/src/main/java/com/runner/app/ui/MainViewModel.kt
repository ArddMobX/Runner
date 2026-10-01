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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class MessageRole {
    USER,
    ASSISTANT,
    TOOL_CALL,
    TOOL_RESULT,
    SYSTEM_INFO
}

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: MessageRole,
    val content: String,
    val toolName: String? = null,
    val toolArgs: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("runner_prefs", Context.MODE_PRIVATE)
    private val apiClient = OpenAIClient()

    // Preferences with fallback/migration
    private val defaultBaseUrl = "https://api.groq.com/openai/v1"
    private val defaultModel = "llama-3.3-70b-versatile"

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
                content = "Привет! Я твой автономный агент Runner. Могу просканировать папку Download, распаковать любой ZIP или навести порядок в файлах по типам.\n\nПоддерживаю любого OpenAI-совместимого провайдера (Groq, OpenRouter, DeepSeek, OpenAI). Чем помочь?"
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

    fun saveSettings(newBaseUrl: String, newApiKey: String, newModelName: String) {
        val bUrl = newBaseUrl.trim()
        val key = newApiKey.trim()
        val model = newModelName.trim()

        prefs.edit()
            .putString("api_base_url", bUrl)
            .putString("api_key", key)
            .putString("api_model_name", model)
            .apply()

        _baseUrl.value = bUrl
        _apiKey.value = key
        _modelName.value = model
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
                Ты автономный мобильный агент Runner для Android. У тебя есть доступ к локальным системным инструментам для работы с файлами на устройстве.
                Инструменты:
                1. get_folder_summary: вызывай, когда пользователь просит проанализировать папку, узнать, что там лежит, показать тяжелые файлы.
                2. extract_archive: вызывай, когда нужно распаковать zip-архив.
                3. organize_downloads: вызывай, когда пользователь просит рассортировать, перенести или навести порядок в Downloads по категориям (documents, apks, archives, images, all).

                Всегда используй доступные инструменты, когда задача требует взаимодействия с файловой системой.
                Отвечай кратко, чётко и вежливо на русском языке.
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
                content = "⚠️ API ключ не задан. Перейдите во вкладку 'Настройки' в правом верхнем углу и укажите ключ провайдера."
            )
            return
        }

        checkStoragePermission()
        if (!_hasStoragePermission.value) {
            _messages.value = _messages.value + ChatMessage(
                role = MessageRole.SYSTEM_INFO,
                content = "⚠️ Нет доступа ко всем файлам (MANAGE_EXTERNAL_STORAGE). Предоставьте разрешение в 'Настройках'."
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
            _currentStatus.value = "Связываюсь с $activeModel..."

            val maxSteps = 5
            var step = 0

            while (step < maxSteps) {
                step++
                val result = apiClient.sendChatCompletion(
                    baseUrl = _baseUrl.value,
                    apiKey = _apiKey.value,
                    modelName = _modelName.value,
                    messages = conversationJson
                )

                when (result) {
                    is AIResponseResult.Error -> {
                        _messages.value = _messages.value + ChatMessage(
                            role = MessageRole.SYSTEM_INFO,
                            content = "❌ ${result.message}"
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
                            _currentStatus.value = "Выполняю: ${call.name}..."

                            _messages.value = _messages.value + ChatMessage(
                                role = MessageRole.TOOL_CALL,
                                content = "Запуск инструмента: ${call.name}",
                                toolName = call.name,
                                toolArgs = call.arguments
                            )

                            val toolOutput = ToolDispatcher.execute(call.name, call.arguments)

                            _messages.value = _messages.value + ChatMessage(
                                role = MessageRole.TOOL_RESULT,
                                content = toolOutput,
                                toolName = call.name
                            )

                            conversationJson.put(JSONObject().apply {
                                put("role", "tool")
                                put("tool_call_id", call.id)
                                put("name", call.name)
                                put("content", toolOutput)
                            })
                        }

                        _currentStatus.value = "Обрабатываю результат выполнения..."
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
                content = "Чат очищен. Готов к новым задачам!"
            )
        )
    }
}
