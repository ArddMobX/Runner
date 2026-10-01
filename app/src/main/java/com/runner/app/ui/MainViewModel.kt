package com.runner.app.ui

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.runner.app.data.GroqClient
import com.runner.app.data.GroqResult
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
    private val groqClient = GroqClient()

    private val _apiKey = MutableStateFlow(prefs.getString("groq_api_key", "") ?: "")
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

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

        // Welcome message
        _messages.value = listOf(
            ChatMessage(
                role = MessageRole.ASSISTANT,
                content = "Привет! Я твой автономный агент Runner. Могу просканировать папку Download, распаковать любой ZIP или навести порядок в файлах по типам.\n\nЧем помочь?"
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

    fun saveApiKey(newKey: String) {
        val trimmed = newKey.trim()
        prefs.edit().putString("groq_api_key", trimmed).apply()
        _apiKey.value = trimmed
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
                Ты автономный мобильный агент Runner для Android. У тебя есть доступ к локальным системным инструментам для работы с файлами.
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
                content = "⚠️ Groq API ключ не задан. Перейдите во вкладку 'Настройки' в правом верхнем углу и укажите ваш ключ."
            )
            return
        }

        checkStoragePermission()
        if (!_hasStoragePermission.value) {
            _messages.value = _messages.value + ChatMessage(
                role = MessageRole.SYSTEM_INFO,
                content = "⚠️ Нет доступа ко всем файлам (MANAGE_EXTERNAL_STORAGE). Предоставьте разрешение в 'Настройках', чтобы я мог работать с файлами."
            )
            return
        }

        // Add user message
        val userMsg = ChatMessage(role = MessageRole.USER, content = userText)
        _messages.value = _messages.value + userMsg

        val userJson = JSONObject().apply {
            put("role", "user")
            put("content", userText)
        }
        conversationJson.put(userJson)

        viewModelScope.launch {
            _isLoading.value = true
            _currentStatus.value = "Связываюсь с Groq Llama 3.3..."

            val maxSteps = 5
            var step = 0

            while (step < maxSteps) {
                step++
                when (val result = groqClient.sendChatCompletion(_apiKey.value, conversationJson)) {
                    is GroqResult.Error -> {
                        _messages.value = _messages.value + ChatMessage(
                            role = MessageRole.SYSTEM_INFO,
                            content = "❌ ${result.message}"
                        )
                        break
                    }
                    is GroqResult.TextResult -> {
                        val assistantReply = if (result.text.isBlank()) "Готово." else result.text
                        _messages.value = _messages.value + ChatMessage(
                            role = MessageRole.ASSISTANT,
                            content = assistantReply
                        )
                        val assistantJson = JSONObject().apply {
                            put("role", "assistant")
                            put("content", assistantReply)
                        }
                        conversationJson.put(assistantJson)
                        break
                    }
                    is GroqResult.ToolCallsResult -> {
                        // Append assistant tool-calls message to history
                        conversationJson.put(result.assistantMessageJson)

                        // Execute each tool sequentially
                        for (call in result.toolCalls) {
                            _currentStatus.value = "Выполняю: ${call.name}..."

                            // Show in UI that tool is triggered
                            _messages.value = _messages.value + ChatMessage(
                                role = MessageRole.TOOL_CALL,
                                content = "Запуск инструмента: ${call.name}",
                                toolName = call.name,
                                toolArgs = call.arguments
                            )

                            // Execute native code
                            val toolOutput = ToolDispatcher.execute(call.name, call.arguments)

                            // Show tool result in UI
                            _messages.value = _messages.value + ChatMessage(
                                role = MessageRole.TOOL_RESULT,
                                content = toolOutput,
                                toolName = call.name
                            )

                            // Append tool response to model context
                            val toolResponseJson = JSONObject().apply {
                                put("role", "tool")
                                put("tool_call_id", call.id)
                                put("name", call.name)
                                put("content", toolOutput)
                            }
                            conversationJson.put(toolResponseJson)
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
