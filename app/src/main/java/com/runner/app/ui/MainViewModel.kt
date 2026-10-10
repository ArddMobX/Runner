package com.runner.app.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.runner.app.util.ImageUtils
import com.runner.app.util.VoiceManager
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
import com.runner.app.data.db.MessageMatch
import com.runner.app.data.db.SessionEntity
import com.runner.app.tools.ToolDispatcher
import com.runner.app.tools.TrashStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
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
    GRANT_STORAGE,
    /** Сухой прогон закончился: запустить ту же задачу по-настоящему. */
    EXECUTE_DRY_RUN
}

/**
 * Решение пользователя по опасной операции.
 * selectedIds заполняется только для операций со списком объектов:
 * null означает «подтверждено целиком».
 * remember — пользователь попросил больше не спрашивать именно это действие.
 */
data class ConfirmationDecision(
    val approved: Boolean,
    val selectedIds: Set<String>? = null,
    val remember: Boolean = false
)

data class ConfirmationRequest(
    val id: String = UUID.randomUUID().toString(),
    val toolName: String,
    val title: String,
    val details: String,
    val warning: String,
    val preview: String = "",
    /** Файлы, которые можно снять галочкой. Пусто — дробить нечего. */
    val items: List<ToolDispatcher.ActionItem> = emptyList(),
    /**
     * Есть ли смысл предлагать «больше не спрашивать».
     *
     * Ложь, когда аргументы операции не опознать: пустая подпись склеила бы
     * в одно разрешение все вызовы инструмента, и галочка стала бы обещанием,
     * которого приложение не держит.
     */
    val canRemember: Boolean = true,
    val onDecision: (ConfirmationDecision) -> Unit
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

/** Сколько последних реплик диалога отдаём планировщику для контекста уточнений. */
private const val PLAN_HISTORY_TAIL = 10

/** Точные короткие реплики, на которые планировщик не триггерится. */
private val NO_PLAN_EXACT = setOf(
    "привет", "здравствуй", "здравствуйте", "добрый день", "добрый вечер",
    "доброе утро", "спасибо", "пожалуйста", "пока", "до свидания",
    "как дела", "как ты", "кто ты", "что ты умеешь", "что умеешь",
    "помощь", "help", "hi", "hello", "hey", "thanks", "thank you", "bye",
    "ок", "ok", "ага", "понятно", "ясно", "хорошо", "давай", "продолжай",
    "ага", "угу"
)

/** Короткие приветственные маркеры внутри очень коротких сообщений. */
private val NO_PLAN_CONTAINS = listOf(
    "привет", "здравствуй", "здравствуйте", "добрый день", "добрый вечер",
    "доброе утро", "спасибо", "благодарю", "hello", "hi there"
)

/**
 * Гейт планировщика: предварительный план составляется только для деструктивных
 * или модифицирующих операций (удаление, перемещение, запись, сортировка, shell),
 * либо когда пользователь явно просит составить план («составь план», «по шагам»).
 * Безопасные запросы на чтение, аналитику и поиск (сводка памяти, поиск мусора,
 * тяжелые файлы, чтение буфера) идут напрямую в агентский цикл — это сокращает время
 * ответа модели вдвое, устраняя избыточный сетевой запрос планирования.
 */
private fun shouldPlan(text: String): Boolean {
    val clean = text.trim().lowercase().replace(Regex("[!?.…]+$"), "").trim()
    if (clean.isEmpty()) return false
    if (clean in NO_PLAN_EXACT) return false
    if (clean.length <= 20 && NO_PLAN_CONTAINS.any { clean.contains(it) }) return false
    return PLAN_ACTION_HINTS.any { clean.contains(it) }
}

/** Действия, требующие предварительного планирования и подтверждения. */
private val PLAN_ACTION_HINTS = listOf(
    "план", "спланируй", "пошагово", "по шагам",
    "удали", "удален", "стереть", "очист",
    "перемест", "переимен", "скопир", "создай", "разбери", "сортиру",
    "запиши", "перезапиши", "допиши",
    "упаку", "распаку",
    "shell", "терминал", "команд", "выполни", "запусти", "скрипт"
)

/**
 * Шаг утверждённого плана: имя инструмента и исходный текст шага.
 * Текст нужен, чтобы привязать одобрение к аргументам, а не только к имени тула.
 */
private data class PlanStep(
    val tool: String,
    val rawText: String
)

/**
 * Аргументы-пути каждого инструмента. Предодобрение плана сверяется именно
 * с ними: вызов с именем из плана, но с другим путём, снова уходит на
 * подтверждение. Первый набор — просто список значений, второй — значения,
 * внутри которых лежит массив путей.
 */
private val PATH_ARGS: Map<String, List<String>> = mapOf(
    "read_file" to listOf("path"),
    "write_file" to listOf("path"),
    "delete_file" to listOf("path"),
    "create_dir" to listOf("path"),
    "list_dir" to listOf("path"),
    "get_folder_summary" to listOf("path"),
    "search_files" to listOf("path"),
    "move_file" to listOf("source_path", "destination_path"),
    "copy_file" to listOf("source_path", "destination_path"),
    "extract_archive" to listOf("zip_path", "target_dir"),
    "create_archive" to listOf("zip_path", "source_paths")
)

/**
 * Не-путевые аргументы, которые тоже привязывают одобрение к конкретному действию.
 *
 * У `clipboard_write` и `organize_downloads` критичных аргументов не было вовсе,
 * и это давало сразу две дыры. Подпись «больше не спрашивать» у них получалась
 * пустой и одинаковой для всех вызовов, то есть одно нажатие разрешало вообще все
 * записи в буфер. А в предодобрении плана пустой список значений проходил проверку
 * `values.all { ... }` вырожденно истинным: шаг плана, назвавший инструмент,
 * заранее одобрял ЛЮБЫЕ его аргументы. Теперь оба перечисляют свои.
 */
private val EXTRA_CRITICAL_ARGS: Map<String, List<String>> = mapOf(
    "run_shell_command" to listOf("command"),
    "open_url" to listOf("url"),
    "open_app" to listOf("app"),
    "organize_downloads" to listOf("category"),
    "clipboard_write" to listOf("text")
)

/**
 * Аргументы-«нагрузка»: не то, НАД чем выполняется операция, а то, ЧТО записывается.
 * Путь и команда привязывают одобрение к цели; здесь важно другое — не переносит ли
 * модель в файл или в буфер текст, прочитанный из недоверенного источника.
 */
private val PAYLOAD_ARGS: Map<String, List<String>> = mapOf(
    "write_file" to listOf("content"),
    "clipboard_write" to listOf("text")
)

/** Все аргументы, которые участвуют в сверке с текстом плана. */
private val CRITICAL_ARGS_BY_TOOL: Map<String, List<String>> =
    PATH_ARGS + EXTRA_CRITICAL_ARGS

/** Инструменты, которые вообще могут попасть в предодобрение плана. */
private val KNOWN_PLAN_TOOLS: Set<String> = setOf(
    "list_dir", "get_folder_summary", "read_file", "write_file", "delete_file",
    "create_dir", "move_file", "copy_file", "search_files", "create_archive",
    "extract_archive", "organize_downloads", "get_storage_summary",
    "find_largest_files", "find_junk_files", "clipboard_read", "clipboard_write",
    "run_shell_command", "open_app", "open_url"
)

/**
 * Шаг плана, у которого уже срезан номер: «tool_name — что сделать».
 * Номера снимает extractPlanStepLines, поэтому цифры здесь не обязательны.
 */
private val PLAN_STEP_LINE = Regex("""^\s*([A-Za-z_][A-Za-z0-9_]*)\b(.*)$""")

/**
 * Мягкие переносы модели вставляют в длинные пути. Их надо именно удалять:
 * замена на пробел рвёт путь («Do cuments» вместо «Documents») и ломает сверку.
 * Неразрывный пробел, наоборот, приводим к обычному.
 */
private fun normalizeForMatch(value: String): String = value
    .replace("\u00AD", "")
    .replace('\u2011', '-')
    .replace('\u00A0', ' ')
    .replace('\uFF0F', '/')
    .replace('\\', '/')
    .replace(Regex("/+"), "/")
    .trim()
    .trimEnd('/')
    .lowercase()

/**
 * Разбирает шаги плана в пары «инструмент + текст шага».
 * Возвращает пустой список, если план не распознан: пустой список означает
 * «ничего не предодобрено», то есть все опасные вызовы пойдут на подтверждение.
 */
private fun parsePlanSteps(steps: List<String>): List<PlanStep> =
    steps.mapNotNull { line ->
        val match = PLAN_STEP_LINE.find(line.trim()) ?: return@mapNotNull null
        val tool = match.groupValues[1]
        if (tool !in KNOWN_PLAN_TOOLS) return@mapNotNull null
        PlanStep(tool = tool, rawText = line.trim())
    }

/** Значения только путевых аргументов вызова. */
private fun pathArgValues(toolName: String, argsJson: String): List<String> {
    val keys = PATH_ARGS[toolName] ?: return emptyList()
    return argValues(argsJson, keys)
}

/** Значения перечисленных ключей: строки и элементы массивов. */
private fun argValues(argsJson: String, keys: List<String>): List<String> {
    val args = try {
        if (argsJson.isBlank()) JSONObject() else JSONObject(argsJson)
    } catch (e: Exception) {
        return emptyList()
    }
    return keys.flatMap { key ->
        when (val value = args.opt(key)) {
            is String -> listOf(value)
            is JSONArray -> (0 until value.length()).mapNotNull { value.optString(it, "").takeIf(String::isNotBlank) }
            else -> emptyList()
        }
    }.filter { it.isNotBlank() }
}

/** Значения критичных аргументов вызова: строки и элементы массивов. */
private fun criticalArgValues(toolName: String, argsJson: String): List<String> {
    // Блочное тело, а не выражение: в функции с expression body `return` запрещён.
    val keys = CRITICAL_ARGS_BY_TOOL[toolName] ?: return emptyList()
    return argValues(argsJson, keys)
}

/** Инструменты, у которых путь обязателен: без пути в плане не предодобряем. */
private val PATH_REQUIRED_TOOLS: Set<String> = setOf(
    "read_file", "write_file", "delete_file", "create_dir", "list_dir",
    "get_folder_summary", "search_files", "move_file", "copy_file",
    "extract_archive", "create_archive"
)

/**
 * Вызов покрыт утверждённым планом?
 *
 * Мало совпадения по имени: аргументы должны быть теми же, что были в шаге
 * плана, который пользователь видел на экране. Иначе модель может один раз
 * показать невинный шаг и выполнить совсем другое действие молча.
 * При любом сомнении возвращаем false — это отправит вызов на подтверждение.
 */
private fun isPreApproved(planSteps: List<PlanStep>?, toolName: String, argsJson: String): Boolean {
    if (planSteps.isNullOrEmpty()) return false

    val cleanTool = toolName.substringAfterLast(":")
    val candidates = planSteps.filter { it.tool == cleanTool }
    if (candidates.isEmpty()) return false

    // Файловая операция без абсолютного пути в аргументах не покрывается планом:
    // иначе модель могла бы назвать в плане один файл, а тронуть другой.
    if (cleanTool in PATH_REQUIRED_TOOLS) {
        val paths = pathArgValues(cleanTool, argsJson)
        if (paths.isEmpty() || paths.none { it.contains('/') }) return false
    }

    val values = criticalArgValues(cleanTool, argsJson)

    // У инструмента есть критичные аргументы, но разобрать их не удалось — не рискуем.
    val expectedKeys = CRITICAL_ARGS_BY_TOOL[cleanTool]
    if (!expectedKeys.isNullOrEmpty() && values.isEmpty()) return false

    return candidates.any { step ->
        val haystack = normalizeForMatch(step.rawText)
        values.all { value -> haystack.contains(normalizeForMatch(value)) }
    }
}

/** Инструменты, чей вывод считается недоверенным содержимым. */
private val UNTRUSTED_OUTPUT_TOOLS: Set<String> = setOf("read_file", "list_dir", "clipboard_read")

/**
 * Подпись операции для списка «больше не спрашивать».
 *
 * Галочка в диалоге действует на КОНКРЕТНОЕ действие, а не на инструмент
 * целиком: «открой YouTube» перестанет спрашивать, а «удали папку» —
 * нет. Поэтому в подпись входят критичные аргументы, нормализованные так же,
 * как при сверке с планом.
 */
private fun confirmationSignature(toolName: String, argsJson: String): String {
    val clean = toolName.substringAfterLast(":")
    val values = criticalArgValues(clean, argsJson)
        .map { signatureValue(it) }
        .sorted()
    return "$clean|${values.joinToString("|")}"
}

/** Значение длиннее этого сворачивается в хеш. */
private const val SIGNATURE_VALUE_LIMIT = 120

/**
 * Значение для подписи операции.
 *
 * Длинные тексты сворачиваются в SHA-256. Подписи лежат в SharedPreferences,
 * и запись в буфер на несколько килобайт раздула бы настройки, а заодно
 * сохранила бы содержимое пользователя на диск в открытом виде.
 *
 * Именно хеш, а не `hashCode()`: 32 бита дают шанс коллизии, а коллизия здесь
 * означает молчаливое разрешение операции, которую пользователь не одобрял.
 */
private fun signatureValue(value: String): String {
    val normalized = normalizeForMatch(value)
    if (normalized.length <= SIGNATURE_VALUE_LIMIT) return normalized
    return "#" + sha256Hex(normalized).take(16)
}

private fun sha256Hex(value: String): String = try {
    MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 0xFF) }
} catch (e: Exception) {
    // SHA-256 есть в любой JVM. Если его вдруг не окажется, не разрешаем
    // запоминать наугад: значение не совпадёт ни с чем, и вопрос задастся снова.
    "#unavailable"
}

/** Признаки ошибки в выводе инструмента, проверяются по началу любой строки. */
private val TOOL_ERROR_PREFIXES = listOf("Ошибка", "Не удалось", "Не могу", "Запрещено")

/**
 * Ошибка ли это в выводе инструмента.
 *
 * Раньше проверялось начало всего текста, а большинство инструментов
 * предваряют результат строкой `resolved_path:` — из-за этого ошибка
 * («Ошибка: родительской папки нет») не распознавалась и карточка выглядела
 * успешной. Теперь смотрим начало каждой строки.
 */
private fun looksLikeToolError(output: String): Boolean {
    val lines = output.lineSequence()
    for (raw in lines) {
        val line = raw.trimStart()
        if (line.isEmpty()) continue
        if (TOOL_ERROR_PREFIXES.any { line.startsWith(it) }) return true
        if (line.contains("Exception", ignoreCase = true)) return true
    }
    return false
}

/**
 * Аргумент похож на строку из недоверенного вывода?
 *
 * Сравнение по целой строке, а не подстрокой: подстрочное давало бы ложные
 * срабатывания на коротких значениях вроде имени файла.
 */
private fun isDerivedFromUntrusted(untrusted: Set<String>, toolName: String, argsJson: String): Boolean {
    if (untrusted.isEmpty()) return false
    val clean = toolName.substringAfterLast(":")

    // Аргументы места назначения — путь, команда, ссылка, имя приложения:
    // сверяем значение целиком.
    val targets = criticalArgValues(clean, argsJson)
    if (targets.any { matchesUntrustedLine(it, untrusted) }) return true

    // Нагрузку (что именно записываем) сверяем построчно. Целое значение здесь
    // не работает: модель почти всегда составляет текст из нескольких строк,
    // и совпадение целого значения не наступило бы никогда — то есть проверка
    // была бы мёртвой. Это и был разрыв: read_file отдавал недоверенный текст,
    // а содержимое write_file не проверялось вовсе, у clipboard_write — ничего.
    val payloads = argValues(argsJson, PAYLOAD_ARGS[clean] ?: emptyList())
    return payloads.any { payload ->
        payload.lineSequence().any { matchesUntrustedLine(it, untrusted) }
    }
}

/** Строка достаточно длинная и в точности совпадает с недоверенной. */
private fun matchesUntrustedLine(value: String, untrusted: Set<String>): Boolean {
    val needle = value.trim()
    return needle.length >= 4 && untrusted.any { it.trim() == needle }
}

/** Радиус контекста вокруг найденной подстроки, символов с каждой стороны. */
private const val SNIPPET_RADIUS = 48

private val WHITESPACE_RUN = Regex("\\s+")

/**
 * Сниппет совпадения: окно вокруг найденного текста.
 *
 * Показывать сообщение целиком нельзя — в истории бывают ответы на тысячи
 * символов, и список чатов превратился бы в простыню. Но и голое «нашлось»
 * бесполезно: непонятно, почему чат попал в выдачу.
 *
 * Переносы строк схлопываются: сниппет рисуется в одну-две строки.
 */
private fun buildSnippet(content: String, query: String): String? {
    if (query.isEmpty()) return null
    val flat = WHITESPACE_RUN.replace(content, " ").trim()
    if (flat.isEmpty()) return null

    val index = flat.indexOf(query, ignoreCase = true)
    if (index < 0) return null

    val start = (index - SNIPPET_RADIUS).coerceAtLeast(0)
    val end = (index + query.length + SNIPPET_RADIUS).coerceAtMost(flat.length)

    return buildString {
        if (start > 0) append('…')
        append(flat.substring(start, end).trim())
        if (end < flat.length) append('…')
    }
}

/**
 * По одному сниппету на сессию. Совпадения приходят новыми сверху, поэтому
 * первое встреченное и есть самое свежее.
 */
private fun buildSnippets(matches: List<MessageMatch>, query: String): Map<String, String> {
    val out = LinkedHashMap<String, String>()
    matches.forEach { match ->
        if (out.containsKey(match.sessionId)) return@forEach
        buildSnippet(match.content, query)?.let { out[match.sessionId] = it }
    }
    return out
}

/**
 * Системный промпт фазы планирования. Формат строгий: слабые модели
 * (Flash Lite и подобные) надёжно держат нумерованный список, а свободный
 * текст потом невозможно привязать к шагам исполнения.
 */
private const val PLAN_SYSTEM_PROMPT = """
Ты планировщик мобильного агента Runner для Android. Разложи задачу пользователя на пошаговый план работы с инструментами.
Доступные инструменты: list_dir, get_folder_summary, read_file, write_file, delete_file, create_dir, move_file, copy_file, search_files, create_archive, extract_archive, organize_downloads, get_storage_summary, find_largest_files, find_junk_files, clipboard_read, clipboard_write, run_shell_command, open_app, open_url.
Правила вывода:
- Выведи ТОЛЬКО нумерованный список шагов, по одному на строку: "1. имя_инструмента - что сделать".
- Используй только инструменты из списка выше.
- Пути указывай абсолютные от корня /storage/emulated/0, а для run_shell_command — саму команду целиком.
- Никаких вступлений, пояснений и заключений.
- Если задача решается одним текстовым ответом без инструментов, выведи одну строку: БЕЗ_ИНСТРУМЕНТОВ.
"""

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: MessageRole,
    val content: String,
    val imageUris: List<String> = emptyList(),
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
    val voiceManager = VoiceManager(application)

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

    /**
     * Сниппеты совпадений для списка чатов: поиск идёт и по содержимому
     * сообщений, поэтому без сниппета непонятно, почему чат попал в выдачу.
     * По одной строке на сессию — самое свежее совпадение.
     */
    val searchSnippets: StateFlow<Map<String, String>> = _sessionsQuery
        .flatMapLatest { query ->
            val trimmed = query.trim()
            if (trimmed.isEmpty()) {
                flowOf(emptyMap())
            } else {
                flow {
                    val matches = repository.findMessageMatches(trimmed)
                    emit(buildSnippets(matches, trimmed))
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

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

    // --- Голос и синтез речи (STT / TTS) ---
    val isListening: StateFlow<Boolean> = voiceManager.isListening
    val rmsLevel: StateFlow<Float> = voiceManager.rmsLevel
    val isSpeaking: StateFlow<Boolean> = voiceManager.isSpeaking
    val activeUtteranceId: StateFlow<String?> = voiceManager.activeUtteranceId

    fun isVoiceRecognitionAvailable(): Boolean = voiceManager.isRecognitionAvailable()

    fun startVoiceInput(
        onPartialResult: (String) -> Unit = {},
        onFinalResult: (String) -> Unit,
        onError: (String) -> Unit = {}
    ) {
        voiceManager.startListening(onPartialResult, onFinalResult, onError)
    }

    fun stopVoiceInput() {
        voiceManager.stopListening()
    }

    fun toggleSpeakMessage(messageId: String, text: String) {
        if (voiceManager.activeUtteranceId.value == messageId && voiceManager.isSpeaking.value) {
            voiceManager.stopSpeaking()
        } else {
            voiceManager.speak(text, messageId)
        }
    }

    fun stopSpeaking() {
        voiceManager.stopSpeaking()
    }

    private val _pendingConfirmation = MutableStateFlow<ConfirmationRequest?>(null)
    val pendingConfirmation: StateFlow<ConfirmationRequest?> = _pendingConfirmation.asStateFlow()

    /** План шагов, ожидающий решения пользователя (null — нет ожидания). */
    private val _pendingPlan = MutableStateFlow<PlanProposal?>(null)
    val pendingPlan: StateFlow<PlanProposal?> = _pendingPlan.asStateFlow()

    /**
     * Шаги утверждённого плана. После кнопки «Утвердить» (и для безопасного
     * плана, исполняемого молча) шторка не показывается только тем вызовам,
     * чьи аргументы совпадают с шагом, который пользователь видел на экране.
     * Сбрасывается при каждом новом сообщении и при перезапуске хода.
     */
    private var approvedPlanSteps: List<PlanStep>? = null

    /**
     * Строки из последнего недоверенного вывода (read_file, list_dir,
     * clipboard_read). Если аргумент следующего опасного вызова целиком
     * совпадает с такой строкой, значит значение пришло из файла или буфера,
     * а не от пользователя: такую операцию подтверждаем руками, даже если
     * она попала в утверждённый план. Это барьер против prompt injection.
     */
    private var untrustedLines: Set<String> = emptySet()

    // --- Сухой прогон и область инструментов ---

    /**
     * Идёт сухой прогон: операции, меняющие состояние, не выполняются, а
     * описываются. Флаг живёт один ход и не сохраняется — после перезапуска
     * приложение не должно «застрять» в предпросмотре.
     */
    @Volatile
    private var dryRunActive = false

    /** Последняя задача прошла всухую, баннер уже показан. Живёт один ход. */
    private var dryRunBannerShown = false

    /** Инструменты, подключённые моделью в этом ходу через `enable_tools`. */
    private val enabledToolNames = mutableSetOf<String>()

    /**
     * Набор инструментов, объявляемый в запросе. `null` — все.
     *
     * Сужаем только когда план уже назвал нужные инструменты: тогда состав
     * известен, и урезание ничего не ломает. Без плана шлём всё — иначе модель
     * упрётся в отсутствие нужного тула на середине задачи.
     */
    private var activeToolScope: Set<String>? = null

    private val _hasStoragePermission = MutableStateFlow(false)
    val hasStoragePermission: StateFlow<Boolean> = _hasStoragePermission.asStateFlow()

    /** Занято/всего для виджета хранилища на пустом экране (null — не удалось прочитать). */
    private val _storageStats = MutableStateFlow<ToolDispatcher.StorageStats?>(null)
    val storageStats: StateFlow<ToolDispatcher.StorageStats?> = _storageStats.asStateFlow()

    /** Версия приложения для нижней панели шторки. */
    val appVersion: String = try {
        val pm = getApplication<Application>().packageManager
        val pkg = getApplication<Application>().packageName
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(pkg, android.content.pm.PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(pkg, 0)
        }
        info.versionName ?: ""
    } catch (e: Exception) {
        ""
    }

    fun refreshStorageStats() {
        viewModelScope.launch {
            _storageStats.value = ToolDispatcher.getStorageStats()
        }
    }

    // --- Корзина и журнал перемещений ---

    /**
     * Состояние журнала для настроек. Пересчитывается по требованию, а не
     * наблюдается: содержимое меняется только действиями пользователя.
     */
    data class TrashState(
        val entries: List<TrashStore.TrashEntry> = emptyList(),
        val loading: Boolean = false
    ) {
        val count: Int get() = entries.size

        /** Удаления: объекты лежат в корзине и занимают её место. */
        val deletions: List<TrashStore.TrashEntry> get() = entries.filter { !it.isMove }

        /** Перемещения: объекты стоят на новых местах, место занимает не корзина. */
        val moves: List<TrashStore.TrashEntry> get() = entries.filter { it.isMove }

        val totalBytes: Long get() = entries.sumOf { it.totalBytes }
        val deletedFileCount: Int get() = deletions.sumOf { it.fileCount }
    }

    private val _trashState = MutableStateFlow(TrashState())
    val trashState: StateFlow<TrashState> = _trashState.asStateFlow()

    /**
     * Перечитывает корзину. Вызывается при открытии настроек: держать это
     * в наблюдателе незачем, содержимое меняется только по действию.
     */
    fun refreshTrash() {
        viewModelScope.launch {
            _trashState.value = _trashState.value.copy(loading = true)
            val entries = withContext(Dispatchers.IO) { TrashStore.list() }
            _trashState.value = TrashState(entries = entries, loading = false)
        }
    }

    /**
     * Уборка при старте: чистит просроченное и потерянные объекты.
     * Без неё корзина росла бы бесконечно, а срок хранения оставался бы
     * обещанием в настройках.
     */
    fun purgeTrashOnStart() {
        viewModelScope.launch {
            val days = _settings.value.trashRetentionDays
            withContext(Dispatchers.IO) { TrashStore.purgeExpired(days) }
            refreshTrash()
        }
    }

    /**
     * Возвращает объект из корзины на исходное место.
     *
     * Сообщение об удалении помечается как отменённое: иначе кнопка «Отменить»
     * осталась бы на экране и после успешного возврата предлагала бы повторить
     * уже выполненное действие.
     */
    fun restoreFromTrash(entryId: String) {
        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) { TrashStore.restore(entryId) }
            val stillInTrash = withContext(Dispatchers.IO) {
                TrashStore.list().any { it.id == entryId }
            }

            val summary = when {
                outcome.restored == 0 && outcome.failed > 0 ->
                    "Не удалось откатить операцию: объект занят другим приложением, " +
                            "перемещён или путь недоступен."
                outcome.renamedTo.isNotEmpty() ->
                    "Возвращено объектов: ${outcome.restored}. " +
                            "Исходное место было занято, поэтому файл получил новое имя: " +
                            outcome.renamedTo.joinToString(", ")
                else -> "Возвращено объектов: ${outcome.restored}."
            }
            appendSystemInfo(summary, null)

            // Пока в записи что-то осталось, кнопка должна работать: остаток
            // пользователь вернёт вторым нажатием. Гасим её только когда
            // восстанавливать больше нечего.
            if (!stillInTrash) markTrashMessage(entryId)
            refreshTrash()
        }
    }

    /** Откатывает весь журнал: и удаления, и перемещения. */
    fun restoreAllFromTrash() {
        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) { TrashStore.restoreAll() }

            // Помечаем все сообщения, чьи записи ушли из журнала.
            val remaining = withContext(Dispatchers.IO) { TrashStore.list().map { it.id }.toSet() }
            _messages.value
                .mapNotNull { message ->
                    TrashStore.trashIdFromOutput(message.toolOutput)
                        ?.takeIf { it !in remaining }
                }
                .forEach { markTrashMessage(it) }

            val renamedNote = if (outcome.renamedTo.isNotEmpty()) {
                " Часть файлов получила новое имя: исходные места были заняты."
            } else {
                ""
            }
            appendSystemInfo("Возвращено объектов: ${outcome.restored}.$renamedNote", null)
            refreshTrash()
        }
    }

    /**
     * Удаляет удалённое безвозвратно и стирает журнал.
     *
     * Объекты перемещений не трогаются — они стоят на своих новых местах,
     * теряется только возможность откатить эти перемещения. Формулировка
     * сообщения это проговаривает: иначе легко решить, что «очистить» вернёт
     * или удалит и перемещённое тоже.
     */
    fun emptyTrash() {
        viewModelScope.launch {
            val freed = withContext(Dispatchers.IO) { TrashStore.empty() }
            appendSystemInfo(
                "Корзина очищена безвозвратно, освобождено ${ToolDispatcher.formatFileSize(freed)}. " +
                        "Перемещённые файлы остались на своих местах, но откатить их больше нельзя.",
                null
            )
            refreshTrash()
        }
    }

    /** Переписывает вывод инструмента, чтобы кнопка отмены погасла. */
    private suspend fun markTrashMessage(entryId: String) {
        val target = _messages.value.firstOrNull { message ->
            TrashStore.trashIdFromOutput(message.toolOutput) == entryId
        } ?: return

        val updated = target.copy(
            toolOutput = TrashStore.markRestored(target.toolOutput.orEmpty(), entryId)
        )
        _messages.value = _messages.value.map { if (it.id == updated.id) updated else it }
        _currentSessionId.value?.let { sessionId ->
            repository.saveMessage(updated.toEntity(sessionId))
        }
    }

    private var conversationJson = JSONArray()

    @Volatile
    private var stopRequested = false

    /** First-run диалог «Выдать разрешения»: показывается один раз при входе без прав. */
    private val _showStoragePrompt = MutableStateFlow(false)
    val showStoragePrompt: StateFlow<Boolean> = _showStoragePrompt.asStateFlow()

    init {
        checkStoragePermission()
        maybeShowStoragePrompt()
        // Уборка корзины при запуске: срок хранения, выставленный в настройках,
        // иначе оставался бы только обещанием в интерфейсе.
        purgeTrashOnStart()
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
        if (granted) {
            _showStoragePrompt.value = false
            refreshStorageStats()
        }
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

    fun togglePinSession(sessionId: String, pinned: Boolean) {
        viewModelScope.launch {
            repository.setSessionPinned(sessionId, pinned)
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

    fun resolveConfirmation(
        confirmed: Boolean,
        selectedIds: Set<String>? = null,
        remember: Boolean = false
    ) {
        val current = _pendingConfirmation.value
        _pendingConfirmation.value = null
        current?.onDecision?.invoke(ConfirmationDecision(confirmed, selectedIds, remember))
    }

    /** Решение пользователя по плану шагов. */
    fun resolvePlan(approved: Boolean) {
        val current = _pendingPlan.value
        _pendingPlan.value = null
        current?.onDecision?.invoke(approved)
    }

    fun stopGeneration() {
        stopRequested = true
        voiceManager.stopSpeaking()
        // Обрыв текущего HTTP-запроса.
        apiClient.cancelActive()
        // Корутину хода намеренно НЕ отменяем. Отмена может прервать запись
        // ответа инструмента в историю, а assistant с tool_calls без ответа
        // провайдер отклоняет. Вместо этого флаг stopRequested проверяется в
        // цикле агента, а паузы между ретраями опрашиваются через shouldStop.
        // Разблокировать ожидание решения по плану: дальше проверка stopRequested остановит цикл.
        resolvePlan(false)
        _currentStatus.value = "Останавливаю"
    }

    fun sendMessage(prompt: String, images: List<Uri> = emptyList()) {
        val text = prompt.trim()
        if (text.isEmpty() && images.isEmpty()) return
        if (_isRunning.value) return

        val provider = activeProvider.value
        if (provider == null || provider.apiKey.isBlank()) {
            appendSystemInfo(
                "Ключ провайдера не задан, модель не ответит. Необходимо указать API-ключ в настройках.",
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
                sendMessage(prompt, images)
            }
            return
        }

        stopRequested = false
        approvedPlanSteps = null
        untrustedLines = emptySet()
        // Состояние сухого прогона и область инструментов — на один ход.
        enabledToolNames.clear()
        activeToolScope = null
        dryRunBannerShown = false
        val effectiveText = if (text.isEmpty() && images.isNotEmpty()) "Что на этом изображении?" else text
        // Прогон всухую включаем только для задач, которые выглядят как
        // изменяющие данные: он удваивает расход запросов, и тратить его
        // на чтение бессмысленно. Эвристика та же, что решает про план.
        dryRunActive = _settings.value.dryRunFirst && shouldPlan(effectiveText)
        val userMessageId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val isFirstUserMessage = _messages.value.none { it.role == MessageRole.USER }

        val cachedImagePaths = images.mapNotNull { uri ->
            ImageUtils.copyToInternalCache(getApplication(), uri)
        }

        _messages.value = _messages.value + ChatMessage(
            id = userMessageId,
            role = MessageRole.USER,
            content = effectiveText,
            imageUris = cachedImagePaths,
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
                        content = effectiveText,
                        imageUris = if (cachedImagePaths.isEmpty()) null else cachedImagePaths.joinToString(","),
                        createdAt = now
                    )
                )
                if (isFirstUserMessage) {
                    val title = effectiveText.replace('\n', ' ').take(ChatRepository.MAX_TITLE_LENGTH)
                    repository.renameSession(sessionId, title)
                    _currentSessionTitle.value = title
                }

                // Системный промпт пересобираем перед каждым ходом: настройки
                // могли измениться посреди сессии, а контекст хранится в базе.
                refreshSystemPrompt()

                if (cachedImagePaths.isNotEmpty()) {
                    val contentArray = JSONArray().apply {
                        if (effectiveText.isNotBlank()) {
                            put(JSONObject().apply {
                                put("type", "text")
                                put("text", effectiveText)
                            })
                        }
                        for (path in cachedImagePaths) {
                            val base64Data = ImageUtils.compressAndEncodeImage(getApplication(), path)
                            if (base64Data != null) {
                                put(JSONObject().apply {
                                    put("type", "image_url")
                                    put("image_url", JSONObject().apply {
                                        put("url", base64Data)
                                    })
                                })
                            }
                        }
                    }
                    conversationJson.put(
                        JSONObject().apply {
                            put("role", "user")
                            put("content", contentArray)
                        }
                    )
                } else {
                    conversationJson.put(
                        JSONObject().apply {
                            put("role", "user")
                            put("content", effectiveText)
                        }
                    )
                }
                persistContext(sessionId)

                // Планирование: сначала план шагов + подтверждение, потом исполнение.
                // Слабым моделям (Flash Lite и подобные) это сильно поднимает надёжность.
                // На приветствия и смолл-ток планировщик не триггерим вообще.
                // Во время сухого прогона план не нужен: сам прогон и есть предпросмотр,
                // а лишний запрос расходует тот же лимит.
                val planNote = if (!dryRunActive &&
                    _settings.value.planningEnabled &&
                    shouldPlan(effectiveText)
                ) {
                    requestPlanApproval(sessionId, provider, model)
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

                // Состав инструментов известен только после утверждения плана.
                updateToolScope()

                runAgentLoop(sessionId, provider, model)
            } finally {
                finishDryRunIfNeeded()
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
    /**
     * «Заново»: убирает ответ на указанное сообщение пользователя и запускает
     * генерацию ещё раз. Контекст диалога обрезается до того же места, иначе
     * модель получила бы историю, которой в ленте уже нет.
     */
    fun retryFromUserMessage(messageId: String) {
        if (_isRunning.value) return

        val target = _messages.value.firstOrNull { it.id == messageId } ?: return
        if (target.role != MessageRole.USER) return

        val provider = activeProvider.value
        if (provider == null || provider.apiKey.isBlank()) {
            appendSystemInfo(
                "Ключ провайдера не задан, модель не ответит. Необходимо указать API-ключ в настройках.",
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

        val sessionId = _currentSessionId.value ?: return
        val index = _messages.value.indexOfFirst { it.id == messageId }
        if (index < 0) return

        _messages.value = _messages.value.take(index + 1)
        stopRequested = false
        approvedPlanSteps = null
        untrustedLines = emptySet()
        // Область инструментов — на один ход: при повторе план заново не
        // запрашивается, значит сужать набор нечем и незачем.
        enabledToolNames.clear()
        activeToolScope = null
        dryRunBannerShown = false
        _streamingText.value = ""
        _isRunning.value = true

        viewModelScope.launch {
            try {
                repository.deleteMessagesAfter(sessionId, target.timestamp)
                trimContextToUser(target.content)
                persistContext(sessionId)
                runAgentLoop(sessionId, provider, model)
            } finally {
                finishDryRunIfNeeded()
                _isRunning.value = false
                _currentStatus.value = null
                _streamingText.value = ""
                apiClient.cancelActive()
            }
        }
    }

    /**
     * Обрезает контекст по последнему совпадению с текстом пользователя.
     * Ищем с конца: при повторе одного и того же сообщения (кнопка «Заново»)
     * поиск с начала обрезал бы историю не по тому месту и ломал диалог.
     */
    private fun trimContextToUser(text: String) {
        var lastMatch = -1
        for (i in 0 until conversationJson.length()) {
            val item = conversationJson.optJSONObject(i) ?: continue
            if (item.optString("role") == "user") {
                val strContent = item.optString("content")
                if (strContent == text || (item.has("content") && item.get("content").toString().contains(text))) {
                    lastMatch = i
                }
            }
        }
        if (lastMatch < 0) return

        val trimmed = JSONArray()
        for (i in 0..lastMatch) {
            conversationJson.optJSONObject(i)?.let { trimmed.put(it) }
        }
        conversationJson = trimmed
    }

    private suspend fun requestPlanApproval(
        sessionId: String,
        provider: Provider,
        model: String
    ): String? {
        val currentSettings = _settings.value
        _currentStatus.value = "Составляю план"

        // Короткие уточнения («самая жирная по весу?») планируем в контексте:
        // отдаём хвост диалога (user/assistant/tool), иначе план слепой
        // и уводит исполнение в анализ несоответствующих файлов.
        // conversationJson уже заканчивается текущим сообщением пользователя.
        val planningMessages = JSONArray().apply {
            put(
                JSONObject().apply {
                    put("role", "system")
                    put("content", PLAN_SYSTEM_PROMPT)
                }
            )
            val start = maxOf(0, conversationJson.length() - PLAN_HISTORY_TAIL)
            for (i in start until conversationJson.length()) {
                conversationJson.optJSONObject(i)?.let { put(JSONObject(it.toString())) }
            }
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
            withTools = false,
            providerKind = provider.kind,
            officialDeepSeekHost = provider.isOfficialDeepSeekHost
        )

        val planText = when (result) {
            is AIResponseResult.TextResult -> result.text.trim()
            is AIResponseResult.Cancelled -> return null
            is AIResponseResult.Error -> {
                // Ошибка планировщика в чат не выводится: молча переходим к прямому выполнению.
                return null
            }
            is AIResponseResult.ToolCallsResult -> {
                // Без тулов этого быть не должно; fallback — свободный режим.
                return null
            }
        }

        if (planText.isBlank()) return null
        if (planText.contains(NO_TOOLS_MARKER)) return null

        val stepLines = extractPlanStepLines(planText)
        // План не распознан: в чат ничего не выводим, исполняемся напрямую.
        if (stepLines.isEmpty()) {
            return null
        }
        val planSteps = parsePlanSteps(stepLines)

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

        fun planNoteText(): String = buildString {
            append("План по шагам (не пропускай шаги, не выдумывай свои инструменты):\n")
            stepLines.forEachIndexed { i, step -> append("${i + 1}. $step\n") }
        }

        // Диалог нужен, если в плане есть хоть одна опасная операция.
        // Список опасных — один на весь проект (ToolDispatcher.DANGEROUS_OPERATIONS),
        // иначе планировщик и гейт подтверждения разъезжаются, как было раньше:
        // run_shell_command и move_file в плане диалог не открывали вообще.
        val needsApproval = planSteps.any { ToolDispatcher.isCriticalOperation(it.tool) }
        if (!needsApproval) {
            approvedPlanSteps = planSteps
            return planNoteText()
        }

        _currentStatus.value = "Жду подтверждения плана"
        val approved = suspendCancellableCoroutine { continuation ->
            _pendingPlan.value = PlanProposal(
                id = planMessageId,
                steps = stepLines,
                rawText = planText,
                onDecision = { decision ->
                    if (continuation.isActive) continuation.resume(decision)
                }
            )
        }
        _pendingPlan.value = null

        return if (approved && !stopRequested) {
            // Одобрены ровно те шаги, которые пользователь видел на экране.
            // Вызов с тем же именем, но другими аргументами, снова спросит.
            approvedPlanSteps = planSteps
            buildString {
                append("Утверждённый пользователем план, строго следуй ему по шагам ")
                append("(не пропускай шаги, не выдумывай свои инструменты):\n")
                stepLines.forEachIndexed { i, step -> append("${i + 1}. $step\n") }
            }
        } else {
            null
        }
    }

    /** Строки вида "1. ..." / "1) ..." — остальное игнорируем. */
    private fun extractPlanStepLines(text: String): List<String> {
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

        // Ответ этого хода. Плашку статистики вешаем именно на него, а не на
        // последний ASSISTANT всей ленты: при ошибке или выводе только тулов
        // тайминги прилипали к чужому, более старому сообщению.
        var turnAssistantId: String? = null

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
                metrics = metrics,
                contextBudgetChars = currentSettings.contextBudgetChars,
                // Тип провайдера и признак официального хоста DeepSeek: от них
                // зависит, какие необязательные поля запроса безопасно слать.
                providerKind = provider.kind,
                officialDeepSeekHost = provider.isOfficialDeepSeekHost,
                // Объявляем только нужные инструменты: их схемы уходят в каждом
                // запросе и занимают заметную часть лимита токенов. null — все.
                toolNames = activeToolScope,
                // Стоп должен работать и во время паузы между ретраями,
                // иначе кнопка «висит», а запрос всё равно уходит повторно.
                shouldStop = { stopRequested }
            )

            metrics.promptTokens?.let { totalPromptTokens += it }
            metrics.completionTokens?.let { totalCompletionTokens += it }

            when (result) {
                is AIResponseResult.Cancelled -> {
                    turnAssistantId = finalizeStreamedText(sessionId, stopped = true) ?: turnAssistantId
                    attachTurnStats(sessionId, turnStartedAt, totalPromptTokens, totalCompletionTokens, trackStats, turnAssistantId)
                    return
                }

                is AIResponseResult.Error -> {
                    turnAssistantId = finalizeStreamedText(sessionId, stopped = false) ?: turnAssistantId
                    appendSystemInfo(result.message, null)
                    attachTurnStats(sessionId, turnStartedAt, totalPromptTokens, totalCompletionTokens, trackStats, turnAssistantId)
                    return
                }

                is AIResponseResult.TextResult -> {
                    val reply = result.text.ifBlank { "Готово." }
                    _streamingText.value = ""
                    turnAssistantId = saveAssistantMessage(
                        sessionId = sessionId,
                        text = reply,
                        reasoningText = metrics.reasoningText.takeIf { it.isNotBlank() },
                        reasoningMs = metrics.reasoningMillis.takeIf { it > 0L },
                        completionTokens = metrics.completionTokens,
                        tokensPerSecond = metrics.tokensPerSecond
                    )
                    if (_settings.value.autoSpeakResponses) {
                        voiceManager.speak(reply, turnAssistantId)
                    }
                    conversationJson.put(
                        JSONObject().apply {
                            put("role", "assistant")
                            put("content", reply)
                        }
                    )
                    persistContext(sessionId)
                    attachTurnStats(sessionId, turnStartedAt, totalPromptTokens, totalCompletionTokens, trackStats, turnAssistantId)
                    return
                }

                is AIResponseResult.ToolCallsResult -> {
                    val preText = result.assistantMessageJson.optString("content")
                    // Текст-вывод модели в ленту не публикуем до исполнения тулов:
                    // итоговый пузырь появляется строго после результата инструментов.
                    if (preText.isNotBlank()) _streamingText.value = ""
                    conversationJson.put(result.assistantMessageJson)
                    persistContext(sessionId)

                    val executed = mutableSetOf<Int>()
                    result.toolCalls.forEachIndexed { index, call ->
                        if (!stopRequested) {
                            executeToolCall(sessionId, call)
                            executed += index
                        }
                    }

                    // Стоп посреди пачки тулов оставлял assistant с tool_calls без
                    // ответов tool. Следующий запрос за такую историю провайдер
                    // отклоняет (400), поэтому досылаем заглушки для невыполненных.
                    closeUnansweredToolCalls(sessionId, result, executed)

                    if (preText.isNotBlank()) {
                        turnAssistantId = saveAssistantMessage(
                            sessionId = sessionId,
                            text = preText,
                            reasoningText = metrics.reasoningText.takeIf { it.isNotBlank() },
                            reasoningMs = metrics.reasoningMillis.takeIf { it > 0L }
                        )
                    }
                }
            }
        }

        if (stopRequested) {
            turnAssistantId = finalizeStreamedText(sessionId, stopped = true) ?: turnAssistantId
        } else {
            appendSystemInfo(
                "Достигнут лимит шагов (${currentSettings.maxSteps}). Увеличьте лимит в настройках агента.",
                null
            )
        }
        attachTurnStats(sessionId, turnStartedAt, totalPromptTokens, totalCompletionTokens, trackStats, turnAssistantId)
    }

    /**
     * Баннер после сухого прогона.
     *
     * Вызывается из `finally`, а не в конце [runAgentLoop]: обычный ответ выходит
     * из цикла раньше — из ветки TextResult, — и до кода после цикла дело не
     * доходит. Именно так прогон и заканчивается в большинстве случаев.
     */
    private fun finishDryRunIfNeeded() {
        if (!dryRunActive || stopRequested) return
        if (dryRunBannerShown) return
        dryRunBannerShown = true
        appendSystemInfo(
            "Это был сухой прогон: файлы не тронуты. Проверьте, что агент собирается " +
                    "сделать, и запустите выполнение.",
            MessageAction.EXECUTE_DRY_RUN
        )
    }

    /**
     * Запускает по-настоящему то, что только что прошло всухую.
     *
     * Повторяет ту же ветку, что кнопка «Заново»: контекст и лента обрезаются до
     * последнего сообщения пользователя, поэтому предпросмотр не дублируется
     * с реальным прогоном. Режим прогона при повторе не включается — иначе
     * кнопка зациклилась бы.
     */
    fun confirmDryRun() {
        val lastUser = _messages.value.lastOrNull { it.role == MessageRole.USER } ?: return
        dryRunBannerShown = false
        dryRunActive = false
        retryFromUserMessage(lastUser.id)
    }

    /**
     * Дописывает в ответ этого хода общее время пайплайна и суммарные токены.
     * [assistantMessageId] — id ответа, созданного в текущем ходе. Если его нет
     * (ход закончился только выводом инструментов), плашку не вешаем вовсе:
     * раньше статистика прилипала к последнему ASSISTANT во всей ленте, то есть
     * к ответу предыдущего хода.
     */
    private suspend fun attachTurnStats(
        sessionId: String,
        turnStartedAt: Long,
        promptTokens: Int,
        completionTokens: Int,
        enabled: Boolean,
        assistantMessageId: String? = null
    ) {
        if (!enabled) return

        val target = assistantMessageId?.let { id -> _messages.value.firstOrNull { it.id == id } }
            ?: return
        val updated = target.copy(
            durationMs = System.currentTimeMillis() - turnStartedAt,
            promptTokens = promptTokens.takeIf { it > 0 },
            completionTokens = completionTokens.takeIf { it > 0 }
        )

        _messages.value = _messages.value.map { if (it.id == updated.id) updated else it }
        repository.saveMessage(updated.toEntity(sessionId))
    }

    /**
     * Досылает заглушки tool-ответов для вызовов, которые не успели выполниться.
     *
     * Без этого остановка посреди пачки инструментов оставляла в истории
     * assistant с tool_calls и без ответов на часть из них. Провайдер такой
     * диалог отклоняет, и следующий запрос падал с 400.
     */
    private suspend fun closeUnansweredToolCalls(
        sessionId: String,
        result: AIResponseResult.ToolCallsResult,
        executedIndexes: Set<Int>
    ) {
        // Индексы, а не id: у части провайдеров id вызова пустой, и два вызова
        // одного инструмента дали бы одинаковый ключ.
        var added = false
        result.toolCalls.forEachIndexed { index, call ->
            if (index in executedIndexes) return@forEachIndexed
            conversationJson.put(
                JSONObject().apply {
                    put("role", "tool")
                    put("tool_call_id", call.id)
                    put("name", call.name)
                    put("content", "Операция не выполнена: пользователь остановил генерацию.")
                }
            )
            added = true
        }
        if (added) persistContext(sessionId)
    }

    /**
     * Выполняет подтверждённую операцию. Если это удаление папки и пользователь
     * снял часть галочек, удаляем только выбранные файлы, а саму папку не трогаем.
     */
    private suspend fun runConfirmedOperation(
        call: ToolCall,
        info: ToolDispatcher.CriticalActionInfo,
        decision: ConfirmationDecision
    ): String {
        val cleanName = call.name.substringAfterLast(":")
        val allIds = info.items.map { it.id }
        val selected = decision.selectedIds

        if (cleanName == "delete_file" &&
            allIds.isNotEmpty() &&
            selected != null &&
            selected.size < allIds.size
        ) {
            return ToolDispatcher.deleteSelectedFiles(allIds.filter { it in selected })
        }
        return ToolDispatcher.execute(
            call.name,
            call.arguments,
            getApplication(),
            _settings.value.shellOutsideStorage
        )
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

        // Проверка опасности идёт здесь, в момент исполнения, и не зависит от
        // того, был ли план. Предодобрение плана снимает шторку только для тех
        // вызовов, чьи аргументы совпадают с шагом, который пользователь видел:
        // одного совпадения по имени тула недостаточно.
        val isDangerous = ToolDispatcher.isCriticalOperation(call.name)
        val preApproved = isDangerous && isPreApproved(approvedPlanSteps, call.name, call.arguments)
        val fromUntrusted = isDangerous &&
                isDerivedFromUntrusted(untrustedLines, call.name, call.arguments)

        // Подпись этого конкретного действия: «открой YouTube» и «удали папку» —
        // разные подписи, поэтому разрешение одного не открывает второе.
        val signature = confirmationSignature(call.name, call.arguments)
        val remembered = isDangerous && signature in _settings.value.approvedOperationSignatures

        // Запоминать имеет смысл только операцию с опознаваемыми аргументами.
        // Пустая подпись склеила бы в одно разрешение все вызовы инструмента:
        // именно так одно нажатие на записи в буфер разрешало все последующие.
        // Если модель аргументов не передала — предлагать галочку нечестно.
        val canRemember = isDangerous &&
                criticalArgValues(call.name, call.arguments).isNotEmpty()

        // Режим «только чтение» блокирует ровно те же операции, что считаются
        // опасными: отдельного списка «изменяющих» инструментов не заводим, иначе
        // списки разойдутся — на этом в проекте уже была ошибка.
        val blockedByReadOnly = _settings.value.readOnlyMode && isDangerous

        // Сухой прогон — тот же набор операций, но по другой причине: не «нельзя»,
        // а «покажи, что будет сделано». Тексты ответов поэтому разные.
        val blockedByDryRun = dryRunActive && isDangerous

        // Подключение инструментов — не операция над файлами, а часть диалога:
        // ни подтверждений, ни режимов у неё быть не должно.
        val isEnableTools = call.name.substringAfterLast(":") == ToolDispatcher.ENABLE_TOOLS

        // Режим «не спрашивать вообще» снимает диалог для всех опасных действий.
        // Исключение одно: аргументы, пришедшие из недоверенного вывода (файл,
        // буфер обмена). Это защита от подсказок вида «удали всё» внутри
        // прочитанного текста, её отключать нельзя.
        val confirmDangerous = _settings.value.confirmDangerous
        val needConfirm = when {
            isEnableTools -> false
            // Заблокированное не подтверждаем: спрашивать нечего, ответ уже дан
            // заранее — самим включением режима.
            blockedByDryRun -> false
            blockedByReadOnly -> false
            fromUntrusted -> true
            remembered -> _settings.value.confirmEveryStep
            isDangerous -> confirmDangerous && !preApproved
            else -> _settings.value.confirmEveryStep
        }
        val output = if (isEnableTools) {
            enableTools(call.arguments)
        } else if (blockedByReadOnly) {
            // «Только чтение» проверяем раньше сухого прогона: она запрещает
            // операцию совсем, поэтому её сообщение правдивее. Сообщение прогона
            // («будет сделано вот это») обещало бы то, что всё равно недоступно.
            declined = true
            "Режим «только чтение»: операция '${call.name.substringAfterLast(":")}' запрещена, " +
                    "данные менять нельзя. Если задача без этого не решается, скажи об этом " +
                    "пользователю и попроси выключить режим в настройках агента."
        } else if (blockedByDryRun) {
            declined = true
            dryRunRefusal(call)
        } else if (needConfirm) {
            _currentStatus.value = "Жду подтверждения"
            val info = ToolDispatcher.describeCriticalAction(call.name, call.arguments)

            val decision = suspendCancellableCoroutine { continuation ->
                _pendingConfirmation.value = ConfirmationRequest(
                    toolName = call.name,
                    title = info.title,
                    details = info.details,
                    warning = info.warning,
                    preview = info.preview,
                    items = info.items,
                    canRemember = canRemember,
                    onDecision = { userDecision ->
                        if (continuation.isActive) continuation.resume(userDecision)
                    }
                )
            }
            _pendingConfirmation.value = null

            if (!decision.approved || stopRequested) {
                declined = true
                "Пользователь отклонил операцию. Предложи альтернативу или остановись."
            } else if (info.items.isNotEmpty() && decision.selectedIds?.isEmpty() == true) {
                declined = true
                "Пользователь снял все галочки, удалять нечего. Не повторяй операцию без новой просьбы."
            } else {
                // «Больше не спрашивать» запоминается только для этого действия
                // и только когда действие вообще опознаваемо.
                if (decision.remember && canRemember) {
                    updateSettings(
                        _settings.value.copy(
                            approvedOperationSignatures =
                            _settings.value.approvedOperationSignatures + signature
                        )
                    )
                }
                _currentStatus.value = title
                val startedAt = System.currentTimeMillis()
                val result = runConfirmedOperation(call, info, decision)
                toolDurationMs = System.currentTimeMillis() - startedAt
                result
            }
        } else {
            val startedAt = System.currentTimeMillis()
            val result = ToolDispatcher.execute(
                call.name,
                call.arguments,
                getApplication(),
                _settings.value.shellOutsideStorage
            )
            toolDurationMs = System.currentTimeMillis() - startedAt
            result
        }

        // Содержимое read_file / list_dir / clipboard_read запоминаем: если
        // следующий опасный вызов возьмёт аргумент прямо оттуда, он снова
        // уйдёт на подтверждение, даже если попал в утверждённый план.
        if (!declined && call.name.substringAfterLast(":") in UNTRUSTED_OUTPUT_TOOLS) {
            val lines = output.lineSequence()
                .map { it.trim() }
                .filter { it.length in 4..300 && it.none { ch -> ch == '\u0000' } }
                .toSet()
            if (lines.isNotEmpty()) untrustedLines = untrustedLines + lines
        }

        val isError = !declined && looksLikeToolError(output)
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

        // Появилась обратимая операция — обновляем состояние для кнопки
        // «Отменить последнее» в шапке. Читаем журнал только когда он реально
        // изменился, а не на каждом вызове инструмента.
        if (TrashStore.undoRefFromOutput(output) != null) {
            refreshTrash()
        }
    }

    /**
     * Сохраняет текст, который успел накопиться в стриме, если ответ оборвался.
     * Возвращает id созданного сообщения (null, если текста не было).
     */
    private suspend fun finalizeStreamedText(sessionId: String, stopped: Boolean): String? {
        val partial = _streamingText.value.trim()
        _streamingText.value = ""

        if (partial.isBlank()) {
            if (stopped) appendSystemInfo("Генерация остановлена.", null)
            return null
        }

        val text = if (stopped) "$partial\n\n_Остановлено._" else partial
        val id = saveAssistantMessage(sessionId, text)
        conversationJson.put(
            JSONObject().apply {
                put("role", "assistant")
                put("content", partial)
            }
        )
        persistContext(sessionId)
        return id
    }

    private suspend fun saveAssistantMessage(
        sessionId: String,
        text: String,
        reasoningText: String? = null,
        reasoningMs: Long? = null,
        completionTokens: Int? = null,
        tokensPerSecond: Double? = null
    ): String {
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
        return id
    }

    private suspend fun persistContext(sessionId: String) {
        repository.updateContext(sessionId, conversationJson.toString())
    }

    private fun freshContext(): JSONArray = JSONArray().apply {
        // База зашита в код + доп. инструкции пользователя (если заданы).
        put(systemMessage())
    }

    /**
     * Системное сообщение по текущим настройкам.
     *
     * Собирается заново, а не хранится однажды собранным: режим «только чтение»
     * и доп. инструкции могут измениться посреди сессии, а контекст лежит в базе
     * как есть и переживает перезапуск.
     */
    private fun systemMessage(): JSONObject {
        var content = AppSettings.buildFinalSystemPrompt(
            _settings.value.userInstructions,
            readOnly = _settings.value.readOnlyMode,
            dryRun = dryRunActive
        )
        // Пояснение к урезанному набору. Без него модель просто не знает, что
        // инструмент существует, и отвечает «не могу» вместо подключения.
        activeToolScope?.let { scope ->
            content = content + "\n\n" + ToolDispatcher.toolScopeNote(scope)
        }
        return JSONObject().apply {
            put("role", "system")
            put("content", content)
        }
    }

    /**
     * Пересобирает набор инструментов для запроса.
     *
     * Сужаем только когда план уже назвал нужные: тогда состав известен.
     * Без плана набор не трогаем — иначе на середине задачи модель обнаружит,
     * что нужного инструмента нет.
     */
    private fun updateToolScope() {
        val planTools = approvedPlanSteps?.map { it.tool }.orEmpty().toSet()
        if (!_settings.value.pruneTools || planTools.isEmpty()) {
            activeToolScope = null
            return
        }
        val scope = mutableSetOf<String>()
        scope.addAll(ToolDispatcher.CORE_TOOL_NAMES)
        scope.addAll(planTools)
        scope.addAll(enabledToolNames)
        scope.add(ToolDispatcher.ENABLE_TOOLS)
        activeToolScope = scope
    }

    /**
     * Ответ модели на операцию, которую сухой прогон не выполняет.
     *
     * Отдаём настоящие параметры операции, а не сухое «нельзя»: смысл прогона
     * в том, чтобы модель описала пользователю, что именно будет сделано,
     * опираясь на реальные данные, а не на догадки.
     */
    private fun dryRunRefusal(call: ToolCall): String {
        val info = ToolDispatcher.describeCriticalAction(call.name, call.arguments)
        return buildString {
            append("СУХОЙ ПРОГОН: операция НЕ выполнена, ничего не изменено.\n")
            append("Инструмент: ${call.name.substringAfterLast(":")}\n")
            append("Что было бы сделано: ${info.title}\n")
            append(info.details)
            if (info.preview.isNotBlank()) {
                append('\n').append(info.preview)
            }
            append("\nЭто предпросмотр. Продолжай: собери оставшиеся данные и опиши ")
            append("пользователю, что будет сделано. Не утверждай, что уже сделано.")
        }
    }

    /**
     * Обработка `enable_tools`: это не файловая операция, а часть диалога,
     * поэтому живёт здесь, а не в ToolDispatcher.
     */
    private fun enableTools(argsJson: String): String {
        val args = try {
            if (argsJson.isBlank()) JSONObject() else JSONObject(argsJson)
        } catch (e: Exception) {
            JSONObject()
        }

        val requested = mutableListOf<String>()
        val array = args.optJSONArray("names")
        if (array != null) {
            for (index in 0 until array.length()) {
                array.optString(index, "").trim()
                    .takeIf { it.isNotBlank() }
                    ?.let { requested += it }
            }
        }
        // Модели иногда присылают строку вместо массива — принимаем и её,
        // иначе подключение молча ничего не сделает.
        if (requested.isEmpty()) {
            args.optString("names", "")
                .split(',', ' ', ';')
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .forEach { requested += it }
        }

        val known = ToolDispatcher.ALL_TOOL_NAMES.toSet()
        val accepted = requested.filter { it in known }.distinct()
        val unknown = requested.filterNot { it in known }.distinct()

        enabledToolNames.addAll(accepted)
        updateToolScope()
        // Промпт пересобираем сразу: список доступного в нём изменился.
        refreshSystemPrompt()

        return buildString {
            if (accepted.isEmpty()) {
                append("Ни один инструмент не подключён.")
            } else {
                append("Подключены инструменты: ${accepted.joinToString(", ")}. ")
                append("Они доступны со следующего шага.")
            }
            if (unknown.isNotEmpty()) {
                append("\nНеизвестные имена: ${unknown.joinToString(", ")}.")
            }
        }
    }

    /** Обновляет системное сообщение в текущем контексте, если оно там есть. */
    private fun refreshSystemPrompt() {
        if (conversationJson.length() == 0) return
        val first = conversationJson.optJSONObject(0) ?: return
        if (first.optString("role") != "system") return
        conversationJson.put(0, systemMessage())
    }

    /**
     * Анти-спам системных плашек: identical SYSTEM_INFO подряд не плодим
     * (например, «Ключ провайдера не задан» на каждый клик Send). Повтор просто
     * игнорируется — в ленте висит один актуальный баннер.
     */
    private fun appendSystemInfo(text: String, action: MessageAction?) {
        val last = _messages.value.lastOrNull()
        if (last != null && last.role == MessageRole.SYSTEM_INFO &&
            last.content == text && last.action == action
        ) {
            return
        }
        _messages.value = _messages.value + ChatMessage(
            role = MessageRole.SYSTEM_INFO,
            content = text,
            isError = action == MessageAction.OPEN_SETTINGS,
            action = action
        )
    }

    override fun onCleared() {
        super.onCleared()
        voiceManager.destroy()
        apiClient.cancelActive()
    }

    private fun ChatMessage.toEntity(sessionId: String): MessageEntity = MessageEntity(
        id = id,
        sessionId = sessionId,
        role = role.name,
        content = content,
        imageUris = if (imageUris.isEmpty()) null else imageUris.joinToString(","),
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
        imageUris = imageUris?.split(",")?.filter { it.isNotBlank() } ?: emptyList(),
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
