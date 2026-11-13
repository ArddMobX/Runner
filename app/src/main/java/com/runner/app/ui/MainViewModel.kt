package com.runner.app.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.runner.app.service.AgentExecutionService
import com.runner.app.util.CrashReportManager
import com.runner.app.util.HapticManager
import com.runner.app.util.ImageUtils
import com.runner.app.util.TaskNotificationManager
import com.runner.app.util.VoiceManager
import com.runner.app.util.VoiceOption
import com.runner.app.data.AIResponseResult
import com.runner.app.data.AppSettings
import com.runner.app.data.AppThemeMode
import com.runner.app.data.InstructionProfile
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
import com.runner.app.tools.ShellLog
import com.runner.app.tools.ToolDispatcher
import com.runner.app.tools.TrashStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CancellationException
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
 * remember - пользователь попросил больше не спрашивать именно это действие.
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
    /** Файлы, которые можно снять галочкой. Пусто - дробить нечего. */
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
 * steps - распарсенные шаги, rawText - исходный текст модели.
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

/** Потолок шагов в плане (лимит исполнения всё равно задаёт maxSteps). */
private const val MAX_PLAN_STEPS = 30

/**
 * Сколько одинаковых вызовов ПОДРЯД терпим, прежде чем решить, что модель
 * зациклилась. Тексты «не повторяй операцию» в промпте - просьба к модели,
 * а не защита: без счётчика зациклившаяся модель просто выжигает maxSteps,
 * и пользователь видит «достигнут лимит» без причины.
 */
private const val LOOP_REPEAT_LIMIT = 3

/**
 * Предел памяти для набора «недоверенных» строк за сессию. Набор живёт весь
 * сеанс и без предела рос бы с каждым прочитанным файлом. Держим последние:
 * актуальная защита не должна незаметно съедать память.
 */
private const val UNTRUSTED_LINES_LIMIT = 2_000

/**
 * Пометка к операции, которую автономный режим выполнил без вопроса, хотя её
 * аргументы совпали с прочитанным текстом.
 *
 * Автономный режим в рабочей папке существует именно для того, чтобы не
 * спрашивать, и отменять это поведение нельзя - иначе ломается обычный
 * сценарий «прочитай файл и перепиши его содержимое». Но промолчать тоже
 * нельзя: файл в папке может содержать указание вроде «удали всё», и тогда
 * агент выполнит его молча. Компромисс - диалога нет, но факт остаётся
 * в истории: пометка видна в свёрнутой строке карточки и в подробностях.
 */
private const val UNTRUSTED_AUTONOMOUS_NOTE =
    "⚠ АРГУМЕНТЫ ИЗ ПРОЧИТАННОГО ТЕКСТА. Эта операция собрана из строк, которые агент " +
            "прочитал в файле или буфере обмена, и выполнена без вопроса, потому что включён " +
            "автономный режим в рабочей папке. Диалог в этом режиме не показывается намеренно. " +
            "Если такая операция не ожидалась, проверьте источник и отмените её."

/**
 * Минимальная длина значения, которое план может предодобрить подстрокой.
 * Короткие значения («all», имя приложения из трёх букв) слишком легко
 * случайно оказываются частью текста шага, а предодобрение - это молчаливое
 * выполнение операции. При сомнении честнее спросить.
 */
private const val PREAPPROVE_MIN_VALUE_LEN = 4

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
 * тяжелые файлы, чтение буфера) идут напрямую в агентский цикл - это сокращает время
 * ответа модели вдвое, устраняя избыточный сетевой запрос планирования.
 */
private fun shouldPlan(text: String): Boolean {
    val clean = text.trim().lowercase().replace(Regex("[!?.…]+$"), "").trim()
    if (clean.isEmpty()) return false
    if (clean in NO_PLAN_EXACT) return false
    if (clean.length <= 20 && NO_PLAN_CONTAINS.any { clean.contains(it) }) return false
    return PLAN_ACTION_HINTS.any { clean.contains(it) }
}

/**
 * В Live-режиме план нужен почти любой задаче, но приветствия и болтовню
 * планировщиком гонять бессмысленно.
 */
private fun shouldPlanLive(text: String): Boolean {
    val clean = text.trim().lowercase().replace(Regex("[!?.…]+$"), "").trim()
    if (clean.isEmpty()) return false
    if (clean in NO_PLAN_EXACT) return false
    if (clean.length <= 20 && NO_PLAN_CONTAINS.any { clean.contains(it) }) return false
    return true
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
 * подтверждение. Первый набор - просто список значений, второй - значения,
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
    "clipboard_write" to listOf("text"),
    // Звонок и действия на экране стали опасными операциями и попадают
    // в предодобрение плана. Без своих аргументов они получили бы пустой
    // список значений, а `values.all { ... }` на пустом списке истинно:
    // шаг плана, назвавший инструмент, заранее одобрял бы ЛЮБОЙ номер
    // и любое нажатие. Именно так уже было с clipboard_write.
    "call_phone" to listOf("phone_number", "contact_name", "target"),
    "ui_click" to listOf("label"),
    "ui_long_click" to listOf("label"),
    "ui_type" to listOf("text"),
    "ui_global_action" to listOf("action")
)

/**
 * Аргументы-«нагрузка»: не то, НАД чем выполняется операция, а то, ЧТО записывается.
 * Путь и команда привязывают одобрение к цели; здесь важно другое - не переносит ли
 * модель в файл или в буфер текст, прочитанный из недоверенного источника.
 */
private val PAYLOAD_ARGS: Map<String, List<String>> = mapOf(
    "write_file" to listOf("content"),
    "clipboard_write" to listOf("text"),
    // Текст, который агент печатает в чужое приложение, - такая же нагрузка:
    // он может быть прочитан из файла или буфера и содержать чужую ссылку,
    // сумму или адрес получателя.
    "ui_type" to listOf("text")
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
    "run_shell_command", "open_app", "open_url",
    // Live-инструменты: план Live-задачи тоже состоит из именованных шагов.
    "ui_inspect", "ui_click", "ui_long_click", "ui_type", "ui_scroll",
    "ui_global_action", "ui_wait", "ui_wait_for", "ui_target", "ui_finish"
)

/**
 * Шаг плана, у которого уже срезан номер: «tool_name - что сделать».
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
 * При любом сомнении возвращаем false - это отправит вызов на подтверждение.
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

    // У инструмента есть критичные аргументы, но разобрать их не удалось - не рискуем.
    val expectedKeys = CRITICAL_ARGS_BY_TOOL[cleanTool]
    if (!expectedKeys.isNullOrEmpty() && values.isEmpty()) return false

    return candidates.any { step ->
        val haystack = normalizeForMatch(step.rawText)
        values.all { value ->
            val needle = normalizeForMatch(value)
            // Короткие значения не предодобряем: совпадение подстроки длиной
            // 1–3 символа - почти наверняка случайность, а не совпадение шага.
            needle.length >= PREAPPROVE_MIN_VALUE_LEN && haystack.contains(needle)
        }
    }
}

/** Инструменты, чей вывод считается недоверенным содержимым. */
private val UNTRUSTED_OUTPUT_TOOLS: Set<String> = setOf("read_file", "list_dir", "clipboard_read")

/**
 * Команда из аргументов run_shell_command. Пустая строка, если JSON не
 * разобрался: тогда пред-проверка [ToolDispatcher.shellPreRefusal] пропускается,
 * а окончательное решение всё равно принимает исполнитель.
 */
private fun shellCommandOf(argsJson: String): String = try {
    if (argsJson.isBlank()) "" else JSONObject(argsJson).optString("command", "").trim()
} catch (e: Exception) {
    ""
}

/**
 * Подпись операции для списка «больше не спрашивать».
 *
 * Галочка в диалоге действует на КОНКРЕТНОЕ действие, а не на инструмент
 * целиком: «открой YouTube» перестанет спрашивать, а «удали папку» -
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
 * предваряют результат строкой `resolved_path:` - из-за этого ошибка
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

    // Аргументы места назначения - путь, команда, ссылка, имя приложения:
    // сверяем значение целиком.
    val targets = criticalArgValues(clean, argsJson)
    if (targets.any { matchesUntrustedLine(it, untrusted) }) return true

    // Нагрузку (что именно записываем) сверяем построчно. Целое значение здесь
    // не работает: модель почти всегда составляет текст из нескольких строк,
    // и совпадение целого значения не наступило бы никогда - то есть проверка
    // была бы мёртвой. Это и был разрыв: read_file отдавал недоверенный текст,
    // а содержимое write_file не проверялось вовсе, у clipboard_write - ничего.
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
 * Показывать сообщение целиком нельзя - в истории бывают ответы на тысячи
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
- Пути указывай абсолютные от корня /storage/emulated/0, а для run_shell_command - саму команду целиком.
- Никаких вступлений, пояснений и заключений.
- Если задача решается одним текстовым ответом без инструментов, выведи одну строку: БЕЗ_ИНСТРУМЕНТОВ.
"""

/**
 * План для Live-задачи. Отличается от обычного: перечисляет инструменты
 * экрана и требует критерий успеха, по которому потом проверяется финиш.
 */
private const val LIVE_PLAN_SYSTEM_PROMPT = """
Ты планировщик Live-задачи мобильного агента Runner. Агент управляет экраном Android через специальные возможности.
Доступные инструменты: ui_inspect, ui_click, ui_long_click, ui_type, ui_scroll, ui_global_action, ui_wait, ui_wait_for, ui_target, open_app, open_url, ui_finish.
Правила вывода:
- Выведи 3-5 шагов, по одному на строку: "1. имя_инструмента - что сделать".
- Не выдумывай элементы экрана: сначала ui_inspect, потом действие.
- Для поиска и открытия предпочитай open_app или open_url с готовой ссылкой.
- Последней строкой выведи: "Критерий успеха: ..." - как проверить, что задача выполнена.
- Никаких вступлений и пояснений.
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
    /** Размышления thinking-модели - показываются под катом. */
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
    val hapticManager = HapticManager(application)

    /** Идентификатор владельца задач: у чата и у плавающего ассистента он свой. */
    private val taskOwnerId = UUID.randomUUID().toString()

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
     * По одной строке на сессию - самое свежее совпадение.
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

    val availableVoices: StateFlow<List<VoiceOption>> = voiceManager.availableVoices

    fun previewTtsVoice(text: String = "Привет! Это проверка голоса в Runner.") {
        voiceManager.previewVoice(text)
    }

    fun testHaptic(intensity: Int) {
        hapticManager.testVibration(intensity)
    }

    fun testHaptic3Seconds(intensity: Int) {
        hapticManager.testVibration3Seconds(intensity)
    }

    fun stopHapticTest() {
        hapticManager.stopVibration()
    }

    fun stopSpeaking() {
        voiceManager.stopSpeaking()
    }

    /** Приложение находится на переднем плане. */
    @Volatile
    private var isAppInForeground: Boolean = true

    fun setAppInForeground(inForeground: Boolean) {
        isAppInForeground = inForeground
        if (inForeground) {
            TaskNotificationManager.cancelAll(getApplication())
        } else {
            if (_settings.value.notifyOnCompletion) {
                val pendingConf = _pendingConfirmation.value
                val pendingP = _pendingPlan.value
                val currentSession = _currentSessionId.value
                if (pendingConf != null) {
                    TaskNotificationManager.notifyConfirmationRequired(
                        getApplication(),
                        sessionId = currentSession,
                        actionTitle = pendingConf.title
                    )
                } else if (pendingP != null) {
                    TaskNotificationManager.notifyConfirmationRequired(
                        getApplication(),
                        sessionId = currentSession,
                        actionTitle = "Утверждение плана действий"
                    )
                }
            }
        }
    }

    private fun maybeNotifyTaskCompleted(sessionId: String) {
        if (isAppInForeground || !_settings.value.notifyOnCompletion || stopRequested) return

        val sessionTitle = _currentSessionTitle.value.ifBlank {
            sessions.value.firstOrNull { it.id == sessionId }?.title
        }
        val error = lastRunError
        if (error != null) {
            TaskNotificationManager.notifyTaskFailed(
                getApplication(),
                sessionId = sessionId,
                sessionTitle = sessionTitle,
                errorText = error
            )
            return
        }

        val lastAssistantMsg = _messages.value.lastOrNull { it.role == MessageRole.ASSISTANT }?.content
        TaskNotificationManager.notifyTaskCompleted(
            getApplication(),
            sessionId = sessionId,
            sessionTitle = sessionTitle,
            previewText = lastAssistantMsg
        )
    }

    private val _pendingConfirmation = MutableStateFlow<ConfirmationRequest?>(null)
    val pendingConfirmation: StateFlow<ConfirmationRequest?> = _pendingConfirmation.asStateFlow()

    /** План шагов, ожидающий решения пользователя (null - нет ожидания). */
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
     * описываются. Флаг живёт один ход и не сохраняется - после перезапуска
     * приложение не должно «застрять» в предпросмотре.
     */
    @Volatile
    private var dryRunActive = false

    /** Последняя задача прошла всухую, баннер уже показан. Живёт один ход. */
    private var dryRunBannerShown = false

    /** Инструменты, подключённые моделью в этом ходу через `enable_tools`. */
    private val enabledToolNames = mutableSetOf<String>()

    /**
     * Набор инструментов, объявляемый в запросе. `null` - все.
     *
     * Сужаем только когда план уже назвал нужные инструменты: тогда состав
     * известен, и урезание ничего не ломает. Без плана шлём всё - иначе модель
     * упрётся в отсутствие нужного тула на середине задачи.
     */
    private var activeToolScope: Set<String>? = null

    private val _hasStoragePermission = MutableStateFlow(false)
    val hasStoragePermission: StateFlow<Boolean> = _hasStoragePermission.asStateFlow()

    private val _hasContactsPermission = MutableStateFlow(false)
    val hasContactsPermission: StateFlow<Boolean> = _hasContactsPermission.asStateFlow()

    private val _hasCallPermission = MutableStateFlow(false)
    val hasCallPermission: StateFlow<Boolean> = _hasCallPermission.asStateFlow()

    private val _hasAudioPermission = MutableStateFlow(false)
    val hasAudioPermission: StateFlow<Boolean> = _hasAudioPermission.asStateFlow()

    /** Занято/всего для виджета хранилища на пустом экране (null - не удалось прочитать). */
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
        val deletions: List<TrashStore.TrashEntry>
            get() = entries.filter { it.kind == TrashStore.EntryKind.DELETE }

        /** Перемещения: объекты стоят на новых местах, место занимает не корзина. */
        val moves: List<TrashStore.TrashEntry> get() = entries.filter { it.isMove }

        /** Перезаписи: в корзине лежит прежняя версия файла. */
        val overwrites: List<TrashStore.TrashEntry> get() = entries.filter { it.isOverwrite }

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
                    "Не удалось откатить операцию: файл недоступен " +
                            "или его не удалось записать (занят другим приложением)."
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
     * Объекты перемещений не трогаются - они стоят на своих новых местах,
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

    /**
     * Текст последней ошибки хода. Нужен уведомлению в фоне: иначе на упавшую
     * задачу приходит «Задача выполнена» с последним ответом ассистента.
     */
    @Volatile
    private var lastRunError: String? = null

    /** First-run диалог «Выдать разрешения»: показывается один раз при входе без прав. */
    private val _showStoragePrompt = MutableStateFlow(false)
    val showStoragePrompt: StateFlow<Boolean> = _showStoragePrompt.asStateFlow()

    /**
     * Сериализует переключение сессий.
     *
     * Объявлен ДО init осознанно: корутина из init на Main.immediate
     * стартует сразу и обращается к этому полю. Если объявить его ниже,
     * конструктор ещё не успевает создать Mutex, и получается NPE.
     */
    private val sessionSwitchLock = Mutex()

    /** Сессия, которую попросили открыть явно: init не должен её перебить. */
    @Volatile
    private var explicitSessionRequest: String? = null

    /** Оверлей уже запросил изолированную сессию ассистента. */
    @Volatile
    private var assistantSessionRequested = false

    init {
        ToolDispatcher.defaultWorkingDir = _settings.value.defaultWorkingDir
        voiceManager.setTtsParameters(_settings.value.ttsSpeed, _settings.value.ttsPitch, _settings.value.ttsVoiceName)
        checkAllPermissions()
        maybeShowStoragePrompt()
        // Уборка корзины при запуске: срок хранения, выставленный в настройках,
        // иначе оставался бы только обещанием в интерфейсе.
        purgeTrashOnStart()
        // Старые скриншоты, фото камеры и картинки удалённых чатов.
        viewModelScope.launch(Dispatchers.IO) {
            ImageUtils.purgeCacheOlderThan(getApplication())
        }
        // Кнопка «Стоп» в уведомлении должна останавливать ЖИВУЮ ViewModel.
        // Обработчик регистрируется под своим идентификатором: у плавающего
        // ассистента своя ViewModel, и общая переменная перезаписывалась ею.
        AgentExecutionService.registerStopHandler(taskOwnerId) { stopGeneration() }
        viewModelScope.launch {
            _currentStatus.collect { status ->
                if (_isRunning.value && !status.isNullOrBlank()) {
                    AgentExecutionService.updateStatus(getApplication(), status)
                }
            }
        }
        viewModelScope.launch {
            sessionSwitchLock.withLock {
                // Пока читали БД, мог прийти явный запрос: deep link из
                // уведомления или старт плавающего ассистента. Он важнее
                // стартовой сессии — второй чат поверх него открывать нельзя.
                if (explicitSessionRequest != null || assistantSessionRequested) return@withLock
                val empty = repository.getRecentEmptySession()
                val session = empty ?: repository.observeSessions().first().firstOrNull() ?: repository.createSession()
                loadSession(session.id)
            }
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
        // Право выдали на системном экране - first-run диалог больше не нужен.
        if (granted) {
            _showStoragePrompt.value = false
            refreshStorageStats()
        }
    }

    fun checkAllPermissions() {
        checkStoragePermission()
        val app = getApplication<Application>()
        _hasContactsPermission.value = ContextCompat.checkSelfPermission(
            app,
            android.Manifest.permission.READ_CONTACTS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        _hasCallPermission.value = ContextCompat.checkSelfPermission(
            app,
            android.Manifest.permission.CALL_PHONE
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        _hasAudioPermission.value = ContextCompat.checkSelfPermission(
            app,
            android.Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
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
        explicitSessionRequest = sessionId
        viewModelScope.launch {
            sessionSwitchLock.withLock {
                loadSession(sessionId)
                if (explicitSessionRequest == sessionId) explicitSessionRequest = null
            }
        }
    }

    fun startNewChat() {
        if (_isRunning.value) return
        viewModelScope.launch {
            // Если в текущем чате ещё нет пользовательских сообщений - не создаём дубликат
            if (_messages.value.none { it.role != MessageRole.SYSTEM_INFO }) {
                return@launch
            }
            val emptySession = repository.getRecentEmptySession()
            val session = emptySession ?: repository.createSession()
            loadSession(session.id)
        }
    }

    /**
     * Запускает изолированную сессию для плавающего ассистента OverlayActivity.
     * Не трогает и не загрязняет текущий диалог пользователя в основном окне.
     */
    fun startAssistantSession() {
        if (_isRunning.value) return
        assistantSessionRequested = true
        viewModelScope.launch {
            sessionSwitchLock.withLock {
                val emptySession = repository.getRecentEmptySession()
                val session = emptySession ?: repository.createSession("Быстрый ассистент")
                loadSession(session.id)
            }
        }
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            repository.deleteSession(sessionId)
            if (_currentSessionId.value == sessionId) {
                sessionSwitchLock.withLock {
                    val remaining = repository.observeSessions().first()
                    val next = remaining.firstOrNull() ?: repository.createSession()
                    loadSession(next.id)
                }
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

    /** Выбор модели переключает и провайдера - одной операцией. */
    fun selectModel(providerId: String, model: String) {
        val provider = _providers.value.firstOrNull { it.id == providerId } ?: return
        val modelId = model.trim()
        if (modelId.isBlank()) return

        // ID, введённый вручную, дописываем в список - иначе он потеряется
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

    /** Добавить или удалить модель из списка избранных. */
    fun toggleFavoriteModel(providerId: String, modelId: String) {
        val key = "$providerId:$modelId"
        val current = _settings.value.favoriteModels.toMutableSet()
        if (current.contains(key) || current.contains(modelId)) {
            current.remove(key)
            current.remove(modelId)
        } else {
            current.add(key)
        }
        updateSettings(_settings.value.copy(favoriteModels = current))
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

    fun updateAppSettings(transform: AppSettings.() -> AppSettings) {
        updateSettings(_settings.value.transform())
    }

    fun updateSettings(newSettings: AppSettings) {
        // Автономный режим без рабочей папки означал бы «вся память без
        // подтверждений»: сбрасываем флаг вместе с последней папкой, иначе
        // крестик «Сбросить папку» тихо снимал бы защиту со всех операций.
        val safeSettings = if (newSettings.defaultWorkingDir.isBlank() &&
            newSettings.autoApproveInWorkingDir
        ) {
            newSettings.copy(autoApproveInWorkingDir = false)
        } else {
            newSettings
        }
        // Полный доступ и подтверждение каждого шага взаимоисключающие:
        // если настройки пришли в конфликте, побеждает более безопасный режим.
        val liveSettings = if (safeSettings.liveFullAccess && safeSettings.liveConfirmEveryStep) {
            safeSettings.copy(liveFullAccess = false)
        } else {
            safeSettings
        }
        val wasLive = _settings.value.liveModeEnabled
        _settings.value = liveSettings
        settingsStore.save(liveSettings)
        ToolDispatcher.defaultWorkingDir = liveSettings.defaultWorkingDir
        voiceManager.setTtsParameters(liveSettings.ttsSpeed, liveSettings.ttsPitch, liveSettings.ttsVoiceName)
        if (wasLive && !liveSettings.liveModeEnabled) {
            LiveAgentManager.reset()
        }
    }

    /** Выбрать активный профиль инструкций */
    fun selectInstructionProfile(profileId: String) {
        val current = _settings.value
        val targetProfile = current.instructionProfiles.firstOrNull { it.id == profileId }
        val newInstructions = targetProfile?.instructions ?: current.userInstructions
        val newSettings = current.copy(
            activeInstructionProfileId = profileId,
            userInstructions = newInstructions
        )
        updateSettings(newSettings)
    }

    /** Обновить текст инструкций текущего активного профиля */
    fun updateActiveProfileInstructions(instructions: String) {
        val current = _settings.value
        val updatedProfiles = current.instructionProfiles.map { profile ->
            if (profile.id == current.activeInstructionProfileId) {
                profile.copy(instructions = instructions)
            } else {
                profile
            }
        }
        val newSettings = current.copy(
            instructionProfiles = updatedProfiles,
            userInstructions = instructions
        )
        updateSettings(newSettings)
    }

    /** Создать новый пользовательский профиль инструкций */
    fun addInstructionProfile(title: String, instructions: String) {
        val current = _settings.value
        val id = "profile_" + System.currentTimeMillis()
        val newProfile = InstructionProfile(
            id = id,
            title = title.trim().ifBlank { "Мой профиль" },
            instructions = instructions.trim(),
            isBuiltIn = false
        )
        val updatedProfiles = current.instructionProfiles + newProfile
        val newSettings = current.copy(
            instructionProfiles = updatedProfiles,
            activeInstructionProfileId = id,
            userInstructions = newProfile.instructions
        )
        updateSettings(newSettings)
    }

    /** Удалить пользовательский профиль инструкций */
    fun deleteInstructionProfile(profileId: String) {
        val current = _settings.value
        val updatedProfiles = current.instructionProfiles.filterNot { it.id == profileId && !it.isBuiltIn }
        val newActiveId = if (current.activeInstructionProfileId == profileId) {
            AppSettings.DEFAULT_PROFILE_ID
        } else {
            current.activeInstructionProfileId
        }
        val newInstructions = updatedProfiles.firstOrNull { it.id == newActiveId }?.instructions.orEmpty()
        val newSettings = current.copy(
            instructionProfiles = updatedProfiles,
            activeInstructionProfileId = newActiveId,
            userInstructions = newInstructions
        )
        updateSettings(newSettings)
    }

    /**
     * Сбрасывает настройки приложения к заводским значениям,
     * сохраняя статус согласия с дисклеймером. API-ключи и чаты не затрагиваются.
     */
    fun resetSettingsToDefault() {
        val currentDisclaimer = _settings.value.disclaimerAccepted
        val defaultSettings = AppSettings(disclaimerAccepted = currentDisclaimer)
        updateSettings(defaultSettings)
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
        LiveAgentManager.stop("Остановлено пользователем")
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

        hapticManager.onSendMessage(_settings.value)

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
                try {
                    val session = repository.createSession()
                    loadSession(session.id)
                    sendMessage(prompt, images)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    CrashReportManager.setLastError("Не удалось создать чат: ${e.stackTraceToString()}")
                    appendSystemInfo(
                        "Не удалось открыть чат: ${e.message ?: e.javaClass.simpleName}.",
                        null
                    )
                }
            }
            return
        }

        stopRequested = false
        lastRunError = null
        approvedPlanSteps = null
        untrustedLines = emptySet()
        // Состояние сухого прогона и область инструментов - на один ход.
        enabledToolNames.clear()
        activeToolScope = null
        dryRunBannerShown = false
        val effectiveText = if (text.isBlank() && images.isNotEmpty()) "Что на этом изображении?" else text.trim()
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
        AgentExecutionService.start(getApplication(), "Runner выполняет задачу...")
        if (_settings.value.liveModeEnabled) {
            // Обработчик «Стоп» привязан к этой сессии Live-режима, а не
            // к глобальной переменной: иначе его перезапишет ViewModel
            // плавающего ассистента, и кнопка остановит не ту задачу.
            LiveAgentManager.start(getApplication(), onStop = { stopGeneration() }, sessionId = sessionId)
        }
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
                    val rawTitle = effectiveText.replace('\n', ' ').take(ChatRepository.MAX_TITLE_LENGTH)
                    val title = if (_settings.value.liveModeEnabled) "⚡ Live: $rawTitle" else rawTitle
                    repository.renameSession(sessionId, title)
                    _currentSessionTitle.value = title
                }
                repository.updateSessionModel(sessionId, model)

                // Системный промпт пересобираем перед каждым ходом: настройки
                // могли измениться посреди сессии, а контекст хранится в базе.
                refreshSystemPrompt()

                if (cachedImagePaths.isNotEmpty()) {
                    val contentArray = JSONArray().apply {
                        val promptText = effectiveText.ifBlank { "Что на этом изображении?" }
                        put(JSONObject().apply {
                            put("type", "text")
                            put("text", promptText)
                        })
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
                val shouldRequestPlan = if (_settings.value.liveModeEnabled) {
                    shouldPlanLive(effectiveText)
                } else {
                    shouldPlan(effectiveText)
                }
                val planNote = if (!dryRunActive &&
                    _settings.value.planningEnabled &&
                    shouldRequestPlan
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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Незакрытое исключение в корутине viewModelScope роняет всё
                // приложение. Превращаем его в понятное сообщение, а стек
                // оставляем в отчёте о сбое для разбора.
                CrashReportManager.setLastError("Сбой выполнения: ${e.stackTraceToString()}")
                hapticManager.onError(_settings.value)
                appendSystemInfo(
                    "Задача прервалась из-за ошибки: ${e.message ?: e.javaClass.simpleName}. " +
                            "Стек сохранён в отчёте о сбое (Настройки → Диагностика).",
                    null
                )
            } finally {
                finishDryRunIfNeeded()
                _isRunning.value = false
                _currentStatus.value = null
                _streamingText.value = ""
                apiClient.cancelActive()
                // Задача завершилась: служба гасится только если других задач
                // не осталось (в оверлее ассистента может идти своя).
                AgentExecutionService.release(getApplication())
                // Live-карточку завершаем только если задача действительно
                // ещё идёт: stop() и ui_finish() уже выставили собственный
                // статус (ошибка, остановка, итоговый отчёт), и перезаписывать
                // его зелёным «Готово» нельзя. Без Live-режима менеджер вообще
                // не стартовал.
                if (_settings.value.liveModeEnabled) {
                    LiveAgentManager.finishIfRunning("Завершено")
                }
                if (!stopRequested) {
                    hapticManager.onTaskComplete(_settings.value)
                }
                maybeNotifyTaskCompleted(sessionId)
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
        lastRunError = null
        approvedPlanSteps = null
        untrustedLines = emptySet()
        // Область инструментов - на один ход: при повторе план заново не
        // запрашивается, значит сужать набор нечем и незачем.
        enabledToolNames.clear()
        activeToolScope = null
        dryRunBannerShown = false
        _streamingText.value = ""
        _isRunning.value = true
        AgentExecutionService.start(getApplication(), "Повтор задачи...")
        if (_settings.value.liveModeEnabled) {
            // Повтор в Live-режиме тоже должен показывать плавающую карточку.
            LiveAgentManager.start(getApplication(), onStop = { stopGeneration() }, sessionId = sessionId)
        }

        viewModelScope.launch {
            try {
                repository.deleteMessagesAfter(sessionId, target.timestamp)
                trimContextToUser(target.content)
                persistContext(sessionId)
                repository.updateSessionModel(sessionId, model)
                runAgentLoop(sessionId, provider, model)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                CrashReportManager.setLastError("Сбой выполнения: ${e.stackTraceToString()}")
                hapticManager.onError(_settings.value)
                appendSystemInfo(
                    "Задача прервалась из-за ошибки: ${e.message ?: e.javaClass.simpleName}. " +
                            "Стек сохранён в отчёте о сбое (Настройки → Диагностика).",
                    null
                )
            } finally {
                finishDryRunIfNeeded()
                _isRunning.value = false
                _currentStatus.value = null
                _streamingText.value = ""
                apiClient.cancelActive()
                // Задача завершилась: служба гасится только если других задач
                // не осталось (в оверлее ассистента может идти своя).
                AgentExecutionService.release(getApplication())
                // Live-карточку завершаем только если задача действительно
                // ещё идёт: stop() и ui_finish() уже выставили собственный
                // статус (ошибка, остановка, итоговый отчёт), и перезаписывать
                // его зелёным «Готово» нельзя. Без Live-режима менеджер вообще
                // не стартовал.
                if (_settings.value.liveModeEnabled) {
                    LiveAgentManager.finishIfRunning("Завершено")
                }
                if (!stopRequested) {
                    hapticManager.onTaskComplete(_settings.value)
                }
                maybeNotifyTaskCompleted(sessionId)
            }
        }
    }

    /**
     * Обрезает контекст по последнему совпадению с текстом пользователя.
     * Ищем с конца: при повторе одного и того же сообщения (кнопка «Заново»)
     * поиск с начала обрезал бы историю не по тому месту и ломал диалог.
     */
    /**
     * Текст пользовательского сообщения независимо от формы content:
     * обычная строка или мультимодальный массив с текстовой частью.
     */
    private fun userMessageText(item: JSONObject): String {
        return when (val content = item.opt("content")) {
            is String -> content
            is JSONArray -> {
                for (i in 0 until content.length()) {
                    val part = content.optJSONObject(i) ?: continue
                    if (part.optString("type") == "text") {
                        return part.optString("text")
                    }
                }
                ""
            }
            else -> ""
        }
    }

    private fun trimContextToUser(text: String) {
        var lastMatch = -1
        for (i in 0 until conversationJson.length()) {
            val item = conversationJson.optJSONObject(i) ?: continue
            if (item.optString("role") != "user") continue
            // Сравниваем сам текст, а не сериализованный JSON: у сообщений
            // с картинками content — массив, а переводы строк и кавычки
            // в JSON экранированы, и поиск подстрокой промахивался.
            if (userMessageText(item) == text) {
                lastMatch = i
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
                    put(
                        "content",
                        if (_settings.value.liveModeEnabled) LIVE_PLAN_SYSTEM_PROMPT else PLAN_SYSTEM_PROMPT
                    )
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
            maxTokens = currentSettings.maxOutputTokens,
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
                // Без тулов этого быть не должно; fallback - свободный режим.
                return null
            }
        }

        if (planText.isBlank()) return null
        if (planText.contains(NO_TOOLS_MARKER)) return null

        // Критерий успеха нужен Live-задаче: по нему модель проверяет,
        // что результат действительно достигнут, прежде чем вызвать ui_finish.
        val successCriteria = planText.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.contains("Критерий успеха", ignoreCase = true) }
            ?.substringAfter(':', "")
            ?.trim()
            .orEmpty()

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
            if (successCriteria.isNotBlank()) {
                append("Критерий успеха: $successCriteria\n")
            }
        }

        // В автономном режиме (autoApproveInWorkingDir) сформированный план передаётся на исполнение сразу.
        // В Live-режиме тоже: приложение уже свёрнуто, карточку плана пользователь
        // не увидит, а задача встанет намертво. Защита там другая - подтверждения
        // действий на плавающей карточке.
        val needsApproval = !_settings.value.liveModeEnabled &&
                !_settings.value.autoApproveInWorkingDir &&
                planSteps.any { ToolDispatcher.isCriticalOperation(it.tool) }
        if (!needsApproval) {
            approvedPlanSteps = planSteps
            return planNoteText()
        }

        _currentStatus.value = "Жду подтверждения плана"
        hapticManager.onConfirmationRequired(_settings.value)
        val approved = suspendCancellableCoroutine { continuation ->
            _pendingPlan.value = PlanProposal(
                id = planMessageId,
                steps = stepLines,
                rawText = planText,
                onDecision = { decision ->
                    if (continuation.isActive) continuation.resume(decision)
                }
            )
            if (!isAppInForeground && _settings.value.notifyOnCompletion) {
                TaskNotificationManager.notifyConfirmationRequired(
                    getApplication(),
                    sessionId = sessionId,
                    actionTitle = "Утверждение плана действий"
                )
            }
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
                if (successCriteria.isNotBlank()) {
                    append("Критерий успеха: $successCriteria\n")
                }
            }
        } else {
            null
        }
    }

    /** Строки вида "1. ..." / "1) ..." - остальное игнорируем. */
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

        // Суммарные счётчики по всей задаче - уходят в плашку под финальным ответом
        var totalPromptTokens = 0
        var totalCompletionTokens = 0
        var step = 0

        // Ответ этого хода. Плашку статистики вешаем именно на него, а не на
        // последний ASSISTANT всей ленты: при ошибке или выводе только тулов
        // тайминги прилипали к чужому, более старому сообщению.
        var turnAssistantId: String? = null

        // Детектор зацикливания: считаем ПОДРЯД идущие одинаковые вызовы
        // в этом ходе. Счётчик переживает шаги: модель, повторяющая вызов
        // через шаг, для пользователя ничем не лучше повторяющей подряд.
        var lastCallSignature: String? = null
        var repeatedCalls = 0

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
                maxTokens = currentSettings.maxOutputTokens,
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
                // запросе и занимают заметную часть лимита токенов. null - все,
                // доступные в текущем режиме.
                toolNames = activeToolScope,
                // Управление экраном вне Live-режима не объявляем вообще.
                liveMode = currentSettings.liveModeEnabled,
                // Стоп должен работать и во время паузы между ретраями,
                // иначе кнопка «висит», а запрос всё равно уходит повторно.
                shouldStop = { stopRequested }
            )

            metrics.promptTokens?.let { totalPromptTokens += it }
            metrics.completionTokens?.let { totalCompletionTokens += it }

            val currentTaskTokens = totalPromptTokens + totalCompletionTokens
            if (currentSettings.maxTaskTokens > 0 && currentTaskTokens >= currentSettings.maxTaskTokens) {
                if (result is AIResponseResult.TextResult) {
                    val reply = result.text.ifBlank { "Готово." }
                    turnAssistantId = saveAssistantMessage(
                        sessionId = sessionId,
                        text = reply,
                        reasoningText = metrics.reasoningText.takeIf { it.isNotBlank() },
                        reasoningMs = metrics.reasoningMillis.takeIf { it > 0L },
                        completionTokens = metrics.completionTokens,
                        tokensPerSecond = metrics.tokensPerSecond
                    )
                    _streamingText.value = ""
                }
                hapticManager.onError(_settings.value)
                val budgetMessage = "Достигнут лимит токенов на задачу " +
                        "($currentTaskTokens из ${currentSettings.maxTaskTokens}). " +
                        "Выполнение приостановлено для экономии баланса."
                lastRunError = budgetMessage
                appendSystemInfo(budgetMessage, null)
                attachTurnStats(sessionId, turnStartedAt, totalPromptTokens, totalCompletionTokens, trackStats, turnAssistantId)
                return
            }

            when (result) {
                is AIResponseResult.Cancelled -> {
                    turnAssistantId = finalizeStreamedText(sessionId, stopped = true) ?: turnAssistantId
                    attachTurnStats(sessionId, turnStartedAt, totalPromptTokens, totalCompletionTokens, trackStats, turnAssistantId)
                    return
                }

                is AIResponseResult.Error -> {
                    turnAssistantId = finalizeStreamedText(sessionId, stopped = false) ?: turnAssistantId
                    hapticManager.onError(_settings.value)
                    lastRunError = result.message
                    val action = when {
                        result.message.contains("401") || result.message.contains("API-ключ") -> MessageAction.OPEN_SETTINGS
                        result.message.contains("400") || result.message.contains("404") || result.message.contains("модел") -> MessageAction.OPEN_MODELS
                        else -> null
                    }
                    appendSystemInfo(result.message, action)
                    attachTurnStats(sessionId, turnStartedAt, totalPromptTokens, totalCompletionTokens, trackStats, turnAssistantId)
                    return
                }

                is AIResponseResult.TextResult -> {
                    // Некоторые провайдеры возвращают в ответе кусок своей
                    // системной инструкции. В чат это попадать не должно.
                    val cleanedReply = stripLeakedSystemPrompt(result.text)
                    if (cleanedReply.isBlank() && result.text.isNotBlank()) {
                        _streamingText.value = ""
                        appendSystemInfo(
                            "Модель вернула служебную инструкцию вместо ответа. Повторите запрос.",
                            null
                        )
                        attachTurnStats(
                            sessionId, turnStartedAt, totalPromptTokens,
                            totalCompletionTokens, trackStats, turnAssistantId
                        )
                        return
                    }
                    val reply = cleanedReply.ifBlank { "Готово." }
                    turnAssistantId = saveAssistantMessage(
                        sessionId = sessionId,
                        text = reply,
                        reasoningText = metrics.reasoningText.takeIf { it.isNotBlank() },
                        reasoningMs = metrics.reasoningMillis.takeIf { it > 0L },
                        completionTokens = metrics.completionTokens,
                        tokensPerSecond = metrics.tokensPerSecond
                    )
                    _streamingText.value = ""
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
                    var loopDetected = false
                    var loopToolName = ""
                    result.toolCalls.forEachIndexed { index, call ->
                        if (stopRequested || loopDetected) return@forEachIndexed

                        // Подпись - имя плюс аргументы целиком: «list_dir Download»
                        // и «list_dir Documents» - разные вызовы, и повтором
                        // считается только полное совпадение.
                        val signature = call.name.substringAfterLast(":") + "\u0000" + call.arguments
                        if (signature == lastCallSignature) {
                            repeatedCalls++
                        } else {
                            lastCallSignature = signature
                            repeatedCalls = 1
                        }

                        // Лимит терпимости достигнут: вызов НЕ выполняем.
                        if (repeatedCalls >= LOOP_REPEAT_LIMIT) {
                            loopDetected = true
                            loopToolName = call.name.substringAfterLast(":")
                            return@forEachIndexed
                        }

                        executeToolCall(sessionId, call)
                        executed += index
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

                    if (loopDetected) {
                        val loopMessage = "Обнаружено зацикливание: модель $repeatedCalls-й раз подряд " +
                                "вызывает '$loopToolName' с теми же аргументами. " +
                                "Задача остановлена, чтобы не выжигать лимит запросов. " +
                                "Переформулируйте запрос или уточните, что именно нужно."
                        lastRunError = loopMessage
                        appendSystemInfo(loopMessage, null)
                        attachTurnStats(
                            sessionId, turnStartedAt, totalPromptTokens,
                            totalCompletionTokens, trackStats, turnAssistantId
                        )
                        return
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
     * из цикла раньше - из ветки TextResult, - и до кода после цикла дело не
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
     * с реальным прогоном. Режим прогона при повторе не включается - иначе
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
     * [assistantMessageId] - id ответа, созданного в текущем ходе. Если его нет
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
            _settings.value.shellOutsideStorage,
            _settings.value.allowRootShell,
            _settings.value.liveFullAccess,
            _settings.value.liveConfirmEveryStep
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

        // Подпись этого конкретного действия: «открой YouTube» и «удали папку» -
        // разные подписи, поэтому разрешение одного не открывает второе.
        val signature = confirmationSignature(call.name, call.arguments)
        val remembered = isDangerous && signature in _settings.value.approvedOperationSignatures

        // Запоминать имеет смысл только операцию с опознаваемыми аргументами.
        // Пустая подпись склеила бы в одно разрешение все вызовы инструмента:
        // именно так одно нажатие на записи в буфер разрешало все последующие.
        // Если модель аргументов не передала - предлагать галочку нечестно.
        val canRemember = isDangerous &&
                criticalArgValues(call.name, call.arguments).isNotEmpty()

        // Режим «только чтение» блокирует ровно те же операции, что считаются
        // опасными: отдельного списка «изменяющих» инструментов не заводим, иначе
        // списки разойдутся - на этом в проекте уже была ошибка.
        val blockedByReadOnly = _settings.value.readOnlyMode && isDangerous

        // Сухой прогон - тот же набор операций, но по другой причине: не «нельзя»,
        // а «покажи, что будет сделано». Тексты ответов поэтому разные.
        val blockedByDryRun = dryRunActive && isDangerous

        // Управление чужим экраном существует только в Live-режиме. Схемы этих
        // инструментов при выключенном режиме в запрос не уходят (см.
        // ToolDispatcher.getToolsJson), но модель могла узнать имя из истории
        // или из контекста - поэтому отказ нужен и здесь, в гейте исполнения.
        // Раньше такой проверки не было вовсе: обычный чат мог тапнуть по
        // экрану, а подтверждение в этом случае некуда было показать -
        // задача вставала намертво в ожидании ответа.
        val isLiveOnlyTool = call.name.substringAfterLast(":") in ToolDispatcher.LIVE_ONLY_TOOL_NAMES
        val blockedByLiveModeOff = isLiveOnlyTool && !_settings.value.liveModeEnabled

        // Абсолютные запреты shell (запрещённая команда, root без тумблера,
        // удаление данных приложений) отсекаются до подтверждения. Показывать
        // диалог для команды, которая всё равно не выполнится, нечестно, а
        // модель должна сразу узнать отказ. Песочница путей сюда не входит:
        // её отказ срабатывает при выполнении, после подтверждения - так
        // пользователь видит, что именно предлагалось выполнить.
        val isShellCall = call.name.substringAfterLast(":") == "run_shell_command"
        val commandLine = if (isShellCall) shellCommandOf(call.arguments) else ""
        val shellPreRefusal = if (isShellCall && commandLine.isNotEmpty()) {
            ToolDispatcher.shellPreRefusal(commandLine, _settings.value.allowRootShell)
        } else {
            null
        }

        // Подключение инструментов - не операция над файлами, а часть диалога:
        // ни подтверждений, ни режимов у неё быть не должно.
        val isEnableTools = call.name.substringAfterLast(":") == ToolDispatcher.ENABLE_TOOLS

        // Режим «не спрашивать вообще» снимает диалог для всех опасных действий.
        // Исключение одно: аргументы, пришедшие из недоверенного вывода (файл,
        // буфер обмена). Это защита от подсказок вида «удали всё» внутри
        // прочитанного текста, её отключать нельзя.
        val confirmDangerous = _settings.value.confirmDangerous
        val isInsideWorkingDir = if (_settings.value.autoApproveInWorkingDir) {
            if (_settings.value.defaultWorkingDir.isBlank()) {
                // Автономный режим без папки не имеет области: «вся память
                // без подтверждений» — это не автономия, а снятая защита.
                // updateSettings такой набор сбрасывает, здесь — страховка.
                false
            } else {
                val workingDirFile = try { java.io.File(_settings.value.defaultWorkingDir).canonicalFile } catch (_: Exception) { null }
                if (workingDirFile != null) {
                    if (isShellCall) {
                        // Shell-команда считается «внутри папки», только если
                        // она не поднимается по '..', не использует домашний
                        // каталог и подстановки и все абсолютные пути ведут
                        // в рабочую папку. Раньше проверялись лишь токены
                        // с ведущим '/', поэтому `cd ..` и пути /data при
                        // «Shell вне памяти» проходили без подтверждения.
                        val unsafeEscapes = commandLine.contains("..") ||
                                commandLine.contains('~') ||
                                commandLine.contains('$') ||
                                commandLine.contains('`')
                        if (unsafeEscapes) {
                            false
                        } else {
                            val pathTokens = Regex("""/[A-Za-z0-9_./*?@=+-]+""")
                                .findAll(commandLine)
                                .map { it.value }
                                .toList()
                            pathTokens.all { token ->
                                try {
                                    val canonical = java.io.File(token).canonicalPath
                                    canonical == workingDirFile.canonicalPath ||
                                            canonical.startsWith("${workingDirFile.canonicalPath}/")
                                } catch (_: Exception) {
                                    // Путь не разобрался — не рискуем, спросим.
                                    false
                                }
                            }
                        }
                    } else {
                        val argsObj = try { JSONObject(call.arguments) } catch (_: Exception) { null }
                        val targetPaths = mutableListOf<String>()
                        argsObj?.optString("path")?.takeIf { it.isNotBlank() }?.let { targetPaths.add(it) }
                        argsObj?.optString("src")?.takeIf { it.isNotBlank() }?.let { targetPaths.add(it) }
                        argsObj?.optString("dir")?.takeIf { it.isNotBlank() }?.let { targetPaths.add(it) }
                        argsObj?.optString("archive_path")?.takeIf { it.isNotBlank() }?.let { targetPaths.add(it) }
                        argsObj?.optString("destination")?.takeIf { it.isNotBlank() }?.let { targetPaths.add(it) }
                        argsObj?.optString("source_path")?.takeIf { it.isNotBlank() }?.let { targetPaths.add(it) }
                        argsObj?.optString("destination_path")?.takeIf { it.isNotBlank() }?.let { targetPaths.add(it) }
                        argsObj?.optString("target_dir")?.takeIf { it.isNotBlank() }?.let { targetPaths.add(it) }
                        argsObj?.optString("zip_path")?.takeIf { it.isNotBlank() }?.let { targetPaths.add(it) }
                        val sourcePathsArr = argsObj?.optJSONArray("source_paths")
                        if (sourcePathsArr != null) {
                            for (i in 0 until sourcePathsArr.length()) {
                                sourcePathsArr.optString(i)?.takeIf { it.isNotBlank() }?.let { targetPaths.add(it) }
                            }
                        }
                        if (targetPaths.isNotEmpty()) {
                            targetPaths.all { tp ->
                                val resolved = try {
                                    ToolDispatcher.resolvePath(tp).canonicalFile
                                } catch (_: Exception) {
                                    val f = java.io.File(tp)
                                    if (f.isAbsolute) f.canonicalFile else java.io.File(workingDirFile, tp).canonicalFile
                                }
                                resolved.canonicalPath == workingDirFile.canonicalPath || resolved.canonicalPath.startsWith("${workingDirFile.canonicalPath}/")
                            }
                        } else {
                            false
                        }
                    }
                } else false
            }
        } else false

        val needConfirm = when {
            isEnableTools -> false
            // Заблокированное не подтверждаем: спрашивать нечего, ответ уже дан
            // заранее - самим включением режима.
            blockedByDryRun -> false
            blockedByReadOnly -> false
            blockedByLiveModeOff -> false
            isInsideWorkingDir -> false
            // Аргументы из недоверенного вывода проверяем ДО Live-исключения
            // ниже: текст, прочитанный из файла или буфера, - единственная
            // защита, которую нельзя отключать режимами.
            fromUntrusted -> true
            // Галочка «Больше не спрашивать это действие» - осознанное решение
            // пользователя по конкретной операции: оно важнее общего режима
            // «спрашивать каждый шаг», иначе галочка выглядит сломанной.
            remembered -> false
            // Действия на экране в Live-режиме спрашивает плавающая карточка
            // (LiveAgentManager.requestConfirmation, с учётом liveFullAccess),
            // а не шторка в чате: шторка в этот момент перекрыта оверлеем и
            // превратила бы один вопрос в два. Вне Live-режима сюда не дойти -
            // такие вызовы отклоняет blockedByLiveModeOff.
            isLiveOnlyTool && _settings.value.liveModeEnabled -> false
            isDangerous -> confirmDangerous && !preApproved
            else -> _settings.value.confirmEveryStep
        }
        var output = if (isEnableTools) {
            enableTools(call.arguments)
        } else if (shellPreRefusal != null) {
            // Абсолютный запрет важнее режимов: «попроси выключить режим» не
            // помогло бы с командой, запрещённой навсегда. Попытку тоже пишем
            // в журнал - это факт, по которому потом разбирается инцидент.
            declined = true
            ShellLog.append(
                getApplication(),
                "ОТКАЗ (до подтверждения)",
                commandLine
            )
            shellPreRefusal
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
        } else if (blockedByLiveModeOff) {
            declined = true
            "Инструмент '${call.name.substringAfterLast(":")}' работает только в Live-режиме, " +
                    "а он выключен. Не пытайся управлять экраном: реши задачу иначе или " +
                    "попроси пользователя включить Live-режим кнопкой в шапке чата."
        } else if (needConfirm) {
            _currentStatus.value = "Жду подтверждения"
            hapticManager.onConfirmationRequired(_settings.value)
            val info = ToolDispatcher.describeCriticalAction(call.name, call.arguments)

            val decision = if (_settings.value.liveModeEnabled) {
                // Live-задача сразу свернула приложение: шторка подтверждения
                // в чате оказалась бы за пределами экрана, а задача - в вечном
                // ожидании. Спрашиваем там, где пользователь реально смотрит, -
                // на плавающей карточке.
                val approved = com.runner.app.ui.LiveAgentManager.requestConfirmation(
                    liveConfirmationPrompt(info)
                )
                ConfirmationDecision(approved = approved)
            } else {
                suspendCancellableCoroutine { continuation ->
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
                    if (!isAppInForeground && _settings.value.notifyOnCompletion) {
                        TaskNotificationManager.notifyConfirmationRequired(
                            getApplication(),
                            sessionId = sessionId,
                            actionTitle = info.title
                        )
                    }
                }
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
            // Настройки, от которых зависит поведение исполнителя, обязаны
            // доходить и по этому пути. Раньше здесь передавался только
            // shellOutsideStorage, поэтому тумблеры «Shell от root» и
            // «Полный доступ в Live» работали лишь тогда, когда операция шла
            // через подтверждение, а без подтверждений молча игнорировались.
            val result = ToolDispatcher.execute(
                call.name,
                call.arguments,
                getApplication(),
                _settings.value.shellOutsideStorage,
                _settings.value.allowRootShell,
                _settings.value.liveFullAccess,
                _settings.value.liveConfirmEveryStep
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
            if (lines.isNotEmpty()) {
                // Держим последние строки: набор нужен для сверки ближайших
                // вызовов, а память на длинной сессии расти не должна.
                untrustedLines = (untrustedLines + lines)
                    .toList()
                    .takeLast(UNTRUSTED_LINES_LIMIT)
                    .toSet()
            }
        }

        // Операция прошла без вопроса, хотя её аргументы взяты из прочитанного
        // текста. Так работает автономный режим в рабочей папке - спрашивать
        // он не должен. Но пометку ставим: сводку считаем по чистому выводу,
        // чтобы пояснение не съело её в свёрнутой строке, а в подробностях
        // добавляем сам текст предупреждения.
        val autoApprovedUntrusted = !declined && fromUntrusted && !needConfirm

        val isError = !declined && looksLikeToolError(output)
        val baseSummary = ToolDispatcher.summarizeResult(output)
        val summary = if (autoApprovedUntrusted && baseSummary.isNotBlank()) {
            "⚠ $baseSummary"
        } else {
            baseSummary
        }
        if (autoApprovedUntrusted) {
            output = output.trimEnd() + "\n\n" + UNTRUSTED_AUTONOMOUS_NOTE
        }

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

        // Появилась обратимая операция - обновляем состояние для кнопки
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

        if (partial.isBlank()) {
            _streamingText.value = ""
            if (stopped) appendSystemInfo("Генерация остановлена.", null)
            return null
        }

        val text = if (stopped) "$partial\n\n_Остановлено._" else partial
        val id = saveAssistantMessage(sessionId, text)
        _streamingText.value = ""
        conversationJson.put(
            JSONObject().apply {
                put("role", "assistant")
                put("content", partial)
            }
        )
        persistContext(sessionId)
        return id
    }

    /**
     * Убирает из ответа куски системных инструкций, которые некоторые
     * провайдеры возвращают в тексте. Это не контент ответа.
     */
    private fun stripLeakedSystemPrompt(text: String): String {
        val markers = listOf(
            "If no function call is necessary",
            "provide a direct prose answer",
            "Continue the conversation above and reply as the Assistant"
        )
        if (markers.none { text.contains(it, ignoreCase = true) }) return text
        return text.lineSequence()
            .filterNot { line -> markers.any { line.contains(it, ignoreCase = true) } }
            .joinToString("\n")
            .trim()
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
        repository.updateContext(sessionId, contextForStorage().toString())
    }

    /**
     * Копия контекста для записи в Room.
     *
     * data:image/...;base64,... — сотни килобайт на кадр. Несколько фото в
     * одном ходе превышают лимит CursorWindow (~2 МБ на строку), и чтение
     * sessions падает с SQLiteBlobTooBigException: список чатов и открытие
     * сессии перестают работать. В БД кладём текстовую пометку вместо
     * картинки; в памяти на время текущего хода base64 остаётся, поэтому
     * модель видит изображения в этом запросе.
     */
    private fun contextForStorage(): JSONArray {
        val out = JSONArray()
        for (i in 0 until conversationJson.length()) {
            val source = conversationJson.optJSONObject(i) ?: continue
            // Глубокая копия: JSONObject(String) заново разбирает вложенные массивы.
            val message = JSONObject(source.toString())
            val content = message.opt("content")
            if (content is JSONArray) {
                val rebuilt = JSONArray()
                for (j in 0 until content.length()) {
                    val raw = content.opt(j)
                    val part = raw as? JSONObject
                    if (part == null) {
                        rebuilt.put(raw)
                        continue
                    }
                    val url = part.optJSONObject("image_url")?.optString("url").orEmpty()
                    if (part.optString("type") == "image_url" && url.startsWith("data:")) {
                        rebuilt.put(JSONObject().apply {
                            put("type", "text")
                            put("text", "[Изображение было приложено в этом сообщении; в сохранённом контексте не передаётся]")
                        })
                    } else {
                        rebuilt.put(part)
                    }
                }
                message.put("content", rebuilt)
            }
            out.put(message)
        }
        return out
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
            _settings.value.effectiveInstructions,
            readOnly = _settings.value.readOnlyMode,
            dryRun = dryRunActive,
            workingDir = _settings.value.defaultWorkingDir,
            autoApproveInWorkingDir = _settings.value.autoApproveInWorkingDir,
            liveMode = _settings.value.liveModeEnabled
        )
        // Пояснение к урезанному набору. Без него модель просто не знает, что
        // инструмент существует, и отвечает «не могу» вместо подключения.
        activeToolScope?.let { scope ->
            content = content + "\n\n" + ToolDispatcher.toolScopeNote(scope, _settings.value.liveModeEnabled)
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
     * Без плана набор не трогаем - иначе на середине задачи модель обнаружит,
     * что нужного инструмента нет.
     */
    private fun updateToolScope() {
        if (_settings.value.liveModeEnabled) {
            val scope = mutableSetOf<String>()
            scope.addAll(ToolDispatcher.LIVE_TOOL_NAMES)
            scope.addAll(ToolDispatcher.CORE_TOOL_NAMES)
            activeToolScope = scope
            return
        }
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
        // Модели иногда присылают строку вместо массива - принимаем и её,
        // иначе подключение молча ничего не сделает.
        if (requested.isEmpty()) {
            args.optString("names", "")
                .split(',', ' ', ';')
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .forEach { requested += it }
        }

        // Вне Live-режима инструменты управления экраном недоступны, и обещать
        // модели обратное нельзя: раньше `enable_tools` отвечал «подключены»,
        // а область инструментов тут же перезаписывалась набором Live-режима -
        // то есть подключение было фиктивным.
        val liveMode = _settings.value.liveModeEnabled
        val known = ToolDispatcher.ALL_TOOL_NAMES
            .filter { liveMode || it !in ToolDispatcher.LIVE_ONLY_TOOL_NAMES }
            .toSet()
        val accepted = requested.filter { it in known }.distinct()
        val unknown = requested.filterNot { it in known }.distinct()
        val hiddenByMode = if (liveMode) {
            emptyList()
        } else {
            requested.filter { it in ToolDispatcher.LIVE_ONLY_TOOL_NAMES }.distinct()
        }

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
            if (hiddenByMode.isNotEmpty()) {
                append("\nНедоступны вне Live-режима: ${hiddenByMode.joinToString(", ")}. ")
                append("Управление экраном включается кнопкой Live в шапке чата.")
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
     * Короткий текст подтверждения для плавающей Live-карточки: заголовок
     * и одна-две строки деталей. Полную портянку в мини-карточку не влезет,
     * а пользователю нужно понять, что именно произойдёт.
     */
    private fun liveConfirmationPrompt(info: ToolDispatcher.CriticalActionInfo): String {
        val details = info.details
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .take(2)
            .joinToString(" · ")
        return buildString {
            append("Подтвердите действие: ")
            append(info.title)
            if (details.isNotBlank()) {
                append(". ")
                append(details)
            }
            if (info.items.isNotEmpty()) {
                append(". Объектов: ")
                append(info.items.size)
            }
        }.take(280)
    }

    /**
     * Анти-спам системных плашек: identical SYSTEM_INFO подряд не плодим
     * (например, «Ключ провайдера не задан» на каждый клик Send). Повтор просто
     * игнорируется - в ленте висит один актуальный баннер.
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
        // Снимаем обработчик «Стоп»: статическая карта не должна держать
        // уничтоженную ViewModel, иначе кнопка в уведомлении будет бить в неё.
        AgentExecutionService.unregisterStopHandler(taskOwnerId)
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
