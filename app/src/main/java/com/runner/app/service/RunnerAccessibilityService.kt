package com.runner.app.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.GestureResultCallback
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.text.InputType
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.runner.app.ui.LiveAgentManager
import com.runner.app.util.CrashReportManager
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Служба специальных возможностей (Accessibility Service) для Live-режима агента Runner.
 * Обеспечивает анализ иерархии экрана, поиск интерактивных элементов,
 * отправку жестов (тап, скролл, ввод текста) и аварийную остановку по кнопкам громкости.
 */
@Suppress("DEPRECATION")
class RunnerAccessibilityService : AccessibilityService() {

    private val cacheLock = Any()
    private val cachedNodes = mutableMapOf<Int, AccessibilityNodeInfo>()
    private val cachedBounds = mutableMapOf<Int, Rect>()
    private val cachedLabels = mutableMapOf<Int, String>()
    private var lastExtractedPackage: String = ""

    /** Пакет и окно, для которых построен кэш #id. */
    private var cachedPackage: String = ""
    private var cachedWindowId: Int = -1
    private var cachedAtMs: Long = 0L

    /** Когда последний раз приходило событие accessibility. Нужно для ui_wait. */
    @Volatile
    private var lastEventAtMs: Long = 0L

    @Volatile
    private var lastEventPackage: String = ""

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString().orEmpty()
        if (pkg.isNotBlank()) lastEventPackage = pkg
        lastEventAtMs = SystemClock.uptimeMillis()
    }

    override fun onInterrupt() {
        // Прерывание службы системой
    }

    override fun onDestroy() {
        if (instance == this) {
            instance = null
        }
        clearNodeCache()
        super.onDestroy()
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            val isVolumeKey = event.keyCode == KeyEvent.KEYCODE_VOLUME_UP ||
                    event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
            if (isVolumeKey && LiveAgentManager.isExecuting) {
                LiveAgentManager.stop("Остановлено физической кнопкой громкости")
                return true
            }
        }
        return super.onKeyEvent(event)
    }

    fun clearNodeCache() {
        synchronized(cacheLock) {
            cachedNodes.values.forEach { runCatching { it.recycle() } }
            cachedNodes.clear()
            cachedBounds.clear()
            cachedLabels.clear()
            cachedPackage = ""
            cachedWindowId = -1
            cachedAtMs = 0L
        }
    }

    /**
     * Реальная подпись кэшированного элемента из дерева экрана (text, contentDescription
     * и текст вложенных узлов контейнера). Нужна, чтобы модель не могла скрыть
     * кнопку вроде «Оплатить» под безобидным аргументом `label`.
     */
    fun cachedElementLabel(elementId: Int): String = synchronized(cacheLock) {
        val parts = linkedSetOf<String>()
        cachedLabels[elementId]?.takeIf { it.isNotBlank() }?.let { parts += it }
        val node = cachedNodes[elementId]
        if (node != null) {
            collectNodeLabels(node, parts, depth = 0, maxDepth = 3)
        }
        parts.joinToString(" ").trim()
    }

    private fun collectNodeLabels(
        node: AccessibilityNodeInfo,
        out: MutableSet<String>,
        depth: Int,
        maxDepth: Int
    ) {
        normalizeUiText(node.text?.toString()).takeIf { it.isNotBlank() }?.let { out += it }
        normalizeUiText(node.contentDescription?.toString()).takeIf { it.isNotBlank() }?.let { out += it }
        if (depth >= maxDepth) return
        val count = try {
            node.childCount
        } catch (_: Exception) {
            0
        }
        for (i in 0 until count) {
            val child = try {
                node.getChild(i)
            } catch (_: Exception) {
                null
            } ?: continue
            try {
                collectNodeLabels(child, out, depth + 1, maxDepth)
            } finally {
                runCatching { child.recycle() }
            }
        }
    }

    /**
     * Проверяет безопасность текущего экрана и извлекает компактную иерархию элементов.
     */
    fun dumpScreenHierarchy(): ScreenInspectionResult {
        return try {
            clearNodeCache()

            // Проверяем ВСЕ окна, а не только активное: банк может быть
            // открыт вторым окном в split-screen, а сверху висеть браузер.
            // Делаем это до получения rootInActiveWindow, чтобы recycle()
            // корней окон не задел тот же объект.
            val sensitivePackage = sensitiveWindowPackage()

            val root = rootInActiveWindow ?: return ScreenInspectionResult.Error("Экран недоступен или заблокирован")
            val pkg = root.packageName?.toString().orEmpty()
            lastExtractedPackage = pkg
            CrashReportManager.setLastActivePackage(pkg)

            // Проверка черного списка безопасности
            if (sensitivePackage != null || isSensitivePackage(pkg)) {
                root.recycle()
                return ScreenInspectionResult.SecurityBlocked(
                    "Блокировка безопасности: обнаружено чувствительное приложение " +
                            "(${sensitivePackage ?: pkg}). В целях защиты данных Live-режим остановлен."
                )
            }

            // Проверка наличия полей ввода пароля
            if (hasPasswordFields(root)) {
                root.recycle()
                return ScreenInspectionResult.SecurityBlocked(
                    "Блокировка безопасности: на экране обнаружено поле ввода пароля. " +
                            "В целях защиты конфиденциальности Live-режим остановлен."
                )
            }

            // С этого момента кэш #id привязан к конкретному окну. Перед
            // кликом проверим, что экран не сменился и кэш не устарел.
            synchronized(cacheLock) {
                cachedPackage = pkg
                cachedWindowId = root.windowId
                cachedAtMs = SystemClock.uptimeMillis()
            }

            val displayMetrics = resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val screenHeight = displayMetrics.heightPixels

            val visibleNodes = mutableListOf<NodeEntry>()
            val seenKeys = mutableSetOf<String>()
            var nextId = 1

            fun traverse(node: AccessibilityNodeInfo?) {
                if (node == null) return
                if (!node.isVisibleToUser) {
                    runCatching { node.recycle() }
                    return
                }

                val bounds = Rect()
                node.getBoundsInScreen(bounds)

                // Пропускаем узлы нулевого размера или полностью за пределами экрана
                if (bounds.width() <= 0 || bounds.height() <= 0 ||
                    bounds.bottom < 0 || bounds.right < 0 ||
                    bounds.left > screenWidth || bounds.top > screenHeight) {
                    runCatching { node.recycle() }
                    return
                }

                val text = normalizeUiText(node.text?.toString())
                val desc = normalizeUiText(node.contentDescription?.toString())
                val isClickable = node.isClickable
                val isEditable = node.isEditable
                val isScrollable = node.isScrollable
                val isCheckable = node.isCheckable
                val isActionable = isClickable || isEditable || isScrollable || isCheckable

                // Одиночные неинтерактивные символы вроде «•» только
                // раздувают дерево. Кнопки при этом остаются: у них есть флаг.
                val isMeaningful = isActionable || text.length >= 2 || desc.length >= 2
                var storedInCache = false

                if (isMeaningful) {
                    val typeLabel = simplifyClassName(node.className?.toString())
                    // Один и тот же текст часто дублируется в контейнере и
                    // вложенном TextView. Интерактивные элементы различаем по
                    // координатам, текстовый контекст дедуплицируем по смыслу.
                    val key = if (isActionable) {
                        "$typeLabel|$text|$desc|${bounds.left},${bounds.top},${bounds.right},${bounds.bottom}"
                    } else {
                        "$typeLabel|$text|$desc"
                    }
                    if (seenKeys.add(key)) {
                        val elementId = nextId++
                        synchronized(cacheLock) {
                            cachedNodes[elementId] = node
                            cachedBounds[elementId] = bounds
                            cachedLabels[elementId] = listOf(text, desc)
                                .filter { it.isNotBlank() }
                                .distinct()
                                .joinToString(" ")
                        }
                        storedInCache = true
                        visibleNodes.add(
                            NodeEntry(
                                id = elementId,
                                type = typeLabel,
                                text = text,
                                description = desc,
                                bounds = bounds,
                                isClickable = isClickable,
                                isEditable = isEditable,
                                isScrollable = isScrollable,
                                isChecked = if (isCheckable) node.isChecked else null
                            )
                        )
                    }
                }

                val childCount = node.childCount
                for (i in 0 until childCount) {
                    traverse(node.getChild(i))
                }
                // Все узлы, не попавшие в cachedNodes (неинтерактивные И дубликаты),
                // освобождаем сразу после обхода детей, чтобы не течь AccessibilityNodeInfo.
                if (!storedInCache) {
                    runCatching { node.recycle() }
                }
            }

            traverse(root)

            // Сначала интерактивные элементы: модель чаще ищет кнопку, а не
            // подпись. Текстовые узлы идут следом как контекст, но не больше
            // MAX_CONTEXT_ELEMENTS: длинные списки подписей только тормозят.
            fun isActionableEntry(entry: NodeEntry): Boolean =
                entry.isClickable || entry.isEditable || entry.isScrollable || entry.isChecked != null

            val actionable = visibleNodes.filter(::isActionableEntry)
            val context = visibleNodes.filterNot(::isActionableEntry).take(MAX_CONTEXT_ELEMENTS)
            val ordered = actionable + context
            val shown = ordered.take(MAX_UI_ELEMENTS)
            val expected = LiveAgentManager.stateFlow.value.expectedPackage

            val formattedTree = buildString {
                appendLine("Приложение: $pkg")
                appendLine("Экран: ${screenWidth}x$screenHeight")
                if (expected.isNotBlank()) {
                    if (expected != pkg && !isTargetLockExempt(pkg)) {
                        appendLine(
                            "ВНИМАНИЕ: ожидалось '$expected', на экране '$pkg'. " +
                                    "Действия заблокированы. Вернись назад, открой нужное " +
                                    "приложение или вызови ui_target('$pkg'), если это системный экран."
                        )
                    } else {
                        appendLine("Ожидаемое приложение: $expected")
                    }
                }
                if (visibleNodes.isEmpty()) {
                    appendLine("Элементы не найдены.")
                } else {
                    appendLine("Элементы: #id тип \"текст\" [флаги] (x,y)")
                    for (entry in shown) {
                        val flags = mutableListOf<String>()
                        if (entry.isClickable) flags += "tap"
                        if (entry.isEditable) flags += "input"
                        if (entry.isScrollable) flags += "scroll"
                        entry.isChecked?.let { flags += if (it) "checked" else "unchecked" }

                        append("#${entry.id} ${entry.type}")
                        if (entry.text.isNotEmpty()) append(" \"${entry.text.take(MAX_UI_TEXT)}\"")
                        if (entry.description.isNotEmpty() && entry.description != entry.text) {
                            append(" desc=\"${entry.description.take(MAX_UI_DESC)}\"")
                        }
                        if (flags.isNotEmpty()) append(" [${flags.joinToString(",")}]")
                        append(" (${entry.bounds.centerX()},${entry.bounds.centerY()})")
                        appendLine()
                    }
                    if (visibleNodes.size > shown.size) {
                        appendLine("[Показаны первые ${shown.size} из ${visibleNodes.size} элементов]")
                    }
                }
            }

            ScreenInspectionResult.Success(
                packageName = pkg,
                formattedHierarchy = formattedTree,
                nodes = visibleNodes
            )
        } catch (t: Throwable) {
            ScreenInspectionResult.Error("Ошибка анализа экрана: ${t.localizedMessage ?: t.javaClass.simpleName}")
        }
    }

    /**
     * Схлопывает переводы строк и повторные пробелы, убирает кавычки,
     * которые ломают формат `#id тип "текст"`.
     */
    private fun normalizeUiText(raw: String?): String =
        raw.orEmpty()
            .replace(UI_WHITESPACE, " ")
            .replace("\"", "'")
            .trim()

    private fun simplifyClassName(fullClass: String?): String {
        if (fullClass.isNullOrBlank()) return "View"
        return when {
            fullClass.contains("Button") -> "Button"
            fullClass.contains("EditText") || fullClass.contains("TextInput") -> "EditText"
            fullClass.contains("TextView") -> "TextView"
            fullClass.contains("ImageView") -> "Image"
            fullClass.contains("CheckBox") -> "CheckBox"
            fullClass.contains("RadioButton") -> "RadioButton"
            fullClass.contains("Switch") -> "Switch"
            fullClass.contains("ScrollView") || fullClass.contains("RecyclerView") || fullClass.contains("ListView") -> "List"
            fullClass.contains("ViewGroup") || fullClass.contains("Layout") -> "Container"
            else -> fullClass.substringAfterLast('.')
        }
    }

    private fun hasPasswordFields(root: AccessibilityNodeInfo): Boolean {
        if (!root.isVisibleToUser) return false

        val bounds = Rect()
        root.getBoundsInScreen(bounds)
        if (bounds.width() <= 0 || bounds.height() <= 0) return false

        if (root.isPassword) return true

        val inputType = root.inputType
        if (inputType != 0) {
            val inputClass = inputType and InputType.TYPE_MASK_CLASS
            val variation = inputType and InputType.TYPE_MASK_VARIATION
            val isPasswordType = when (inputClass) {
                InputType.TYPE_CLASS_TEXT -> {
                    variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                            variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
                            variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                }
                InputType.TYPE_CLASS_NUMBER -> {
                    variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
                }
                else -> false
            }
            if (isPasswordType) return true
        }

        val count = root.childCount
        for (i in 0 until count) {
            val child = root.getChild(i) ?: continue
            val hasPass = hasPasswordFields(child)
            runCatching { child.recycle() }
            if (hasPass) return true
        }
        return false
    }

    /**
     * Пакет любого видимого окна, который считается чувствительным, либо null.
     * Читаем только имя пакета корня окна, без обхода дерева: обход всех
     * окон на каждом ui_inspect был бы слишком дорогим.
     */
    private fun sensitiveWindowPackage(): String? {
        val windowList = try {
            windows
        } catch (_: Exception) {
            null
        } ?: return null

        for (window in windowList) {
            val windowRoot = try {
                window.root
            } catch (_: Exception) {
                null
            } ?: continue
            try {
                val name = windowRoot.packageName?.toString().orEmpty()
                if (isSensitivePackage(name)) return name
            } finally {
                runCatching { windowRoot.recycle() }
            }
        }
        return null
    }

    /**
     * Проверяет, нет ли на текущем экране чувствительного приложения (включая
     * соседние окна в split-screen) или видимого поля пароля. При срабатывании
     * останавливает Live-режим и возвращает текст отказа.
     */
    fun activeScreenSecurityBlockReason(stopLiveOnBlock: Boolean = true): String? {
        val sensitiveWindow = sensitiveWindowPackage()
        if (sensitiveWindow != null) {
            val reason = "Блокировка безопасности: обнаружено чувствительное приложение " +
                    "($sensitiveWindow). В целях защиты данных Live-режим остановлен."
            if (stopLiveOnBlock) LiveAgentManager.stop(reason, isError = true)
            return reason
        }
        val root = rootInActiveWindow ?: return null
        return try {
            val pkg = root.packageName?.toString().orEmpty()
            if (isSensitivePackage(pkg)) {
                val reason = "Блокировка безопасности: обнаружено чувствительное приложение " +
                        "($pkg). В целях защиты данных Live-режим остановлен."
                if (stopLiveOnBlock) LiveAgentManager.stop(reason, isError = true)
                return reason
            }
            if (hasPasswordFields(root)) {
                val reason = "Блокировка безопасности: на экране обнаружено поле ввода пароля. " +
                        "В целях защиты конфиденциальности Live-режим остановлен."
                if (stopLiveOnBlock) LiveAgentManager.stop(reason, isError = true)
                return reason
            }
            null
        } finally {
            runCatching { root.recycle() }
        }
    }

    /**
     * Пакет относится к чувствительному приложению?
     *
     * Сверяем по сегментам имени пакета: `ru.vtb24.mobilebanking` состоит из
     * сегментов `ru`, `vtb24`, `mobilebanking`, и запись `vtb` должна совпасть
     * с `vtb24`, но не с `com.example.vtbx.photo`. Сравнение подстрокой по
     * всему имени находило «vtb» где угодно; заодно убран allowlist браузеров,
     * который мог скрыть банк, открытый под именем браузера.
     */
    private fun isSensitivePackage(packageName: String): Boolean =
        isSensitivePackageName(packageName)

    /** Пакет активного окна. */
    fun currentPackageName(): String {
        val root = rootInActiveWindow ?: return lastEventPackage
        return try {
            root.packageName?.toString().orEmpty().ifBlank { lastEventPackage }
        } finally {
            runCatching { root.recycle() }
        }
    }

    fun msSinceLastAccessibilityEvent(): Long = SystemClock.uptimeMillis() - lastEventAtMs

    /** Экран давно не менялся: можно считать загрузку завершённой. */
    fun isScreenIdle(quietMs: Long = 400L): Boolean =
        msSinceLastAccessibilityEvent() >= quietMs

    /**
     * Live-задача ушла в другое приложение? Системные окна, клавиатура
     * и permission-диалоги не блокируются.
     */
    fun targetMismatchReason(): String? {
        val expected = LiveAgentManager.stateFlow.value.expectedPackage
        if (expected.isBlank()) return null
        val current = currentPackageName()
        if (current.isBlank()) return null
        if (current == expected || isTargetLockExempt(current)) return null
        return "Действие остановлено: ожидалось приложение '$expected', а на экране '$current'. " +
                "Вернись назад или открой нужное приложение. Если это системный экран, " +
                "вызови ui_target('$current')."
    }

    private fun isTargetLockExempt(packageName: String): Boolean =
        isTargetLockExemptPackage(packageName)

    /**
     * Проверяет, что кэш #id построен для текущего окна и не устарел.
     * Иначе старый номер может указывать на другой элемент.
     */
    private fun cacheFreshnessError(): String? {
        val snapshotAtMs: Long
        val snapshotPkg: String
        val snapshotWindowId: Int
        synchronized(cacheLock) {
            snapshotAtMs = cachedAtMs
            snapshotPkg = cachedPackage
            snapshotWindowId = cachedWindowId
        }
        if (snapshotAtMs == 0L) {
            return "Сначала вызови ui_inspect: список элементов ещё не построен."
        }
        val root = rootInActiveWindow ?: return "Экран недоступен. Повтори ui_inspect."
        return try {
            val pkg = root.packageName?.toString().orEmpty()
            val windowId = root.windowId
            when {
                pkg != snapshotPkg || windowId != snapshotWindowId ->
                    "Экран изменился после ui_inspect. Вызови ui_inspect заново: старые #id могли устареть."
                SystemClock.uptimeMillis() - snapshotAtMs > CACHE_TTL_MS ->
                    "Список элементов устарел. Вызови ui_inspect заново."
                else -> null
            }
        } finally {
            runCatching { root.recycle() }
        }
    }

    /** Есть ли на экране видимый текст/описание с этой подстрокой. */
    fun screenContainsText(query: String): Boolean {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return false
        if (activeScreenSecurityBlockReason(stopLiveOnBlock = true) != null) return false
        val root = rootInActiveWindow ?: return false
        return try {
            nodeContainsText(root, needle)
        } finally {
            runCatching { root.recycle() }
        }
    }

    private fun nodeContainsText(node: AccessibilityNodeInfo, needle: String): Boolean {
        if (node.isVisibleToUser) {
            val text = node.text?.toString().orEmpty()
            val desc = node.contentDescription?.toString().orEmpty()
            if (text.lowercase().contains(needle) || desc.lowercase().contains(needle)) {
                return true
            }
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = nodeContainsText(child, needle)
            runCatching { child.recycle() }
            if (found) return true
        }
        return false
    }

    /**
     * Выполняет клик по элементу с заданным ID.
     */
    suspend fun clickElement(elementId: Int): ActionResult {
        activeScreenSecurityBlockReason()?.let { return ActionResult.Error(it) }
        cacheFreshnessError()?.let { return ActionResult.Error(it) }
        targetMismatchReason()?.let { return ActionResult.Error(it) }

        val (targetBounds, immediateResult) = synchronized(cacheLock) {
            val bounds = cachedBounds[elementId]
            val node = cachedNodes[elementId]
            if (bounds == null && node == null) {
                null to ActionResult.Error("Элемент с ID #$elementId не найден на экране. Вызовите ui_inspect для обновления дерева.")
            } else {
                val rect = bounds?.let { Rect(it) } ?: Rect().also { node?.getBoundsInScreen(it) }
                val cx = rect.centerX().toFloat()
                val cy = rect.centerY().toFloat()
                TouchVisualizerOverlay.showTouchRipple(cx, cy, rect)

                // Сначала ACTION_CLICK: он не зависит от того, перекрыт ли центр
                // элемента другим view. Жест оставляем как резерв.
                val result = when {
                    node?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true ->
                        ActionResult.Success("Клик по элементу #$elementId выполнен через Accessibility ACTION_CLICK")
                    clickClickableParent(node) ->
                        ActionResult.Success("Клик по элементу #$elementId выполнен через родительский кликабельный элемент")
                    else -> null
                }
                rect to result
            }
        }
        if (immediateResult != null) return immediateResult
        val rect = targetBounds
            ?: return ActionResult.Error("Элемент с ID #$elementId не найден на экране. Вызовите ui_inspect для обновления дерева.")

        val cx = rect.centerX().toFloat()
        val cy = rect.centerY().toFloat()
        val gestureSuccess = tapAtCoordinates(cx, cy)
        return if (gestureSuccess) {
            ActionResult.Success("Успешный клик по элементу #$elementId в точке ($cx, $cy)")
        } else {
            ActionResult.Error("Не удалось выполнить клик по элементу #$elementId")
        }
    }

    private fun clickClickableParent(node: AccessibilityNodeInfo?): Boolean {
        var parent = node?.parent
        var depth = 0
        while (parent != null && depth < 4) {
            val current = parent
            if (current.isClickable || current.isCheckable) {
                val clicked = current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                runCatching { current.recycle() }
                return clicked
            }
            val next = current.parent
            runCatching { current.recycle() }
            parent = next
            depth++
        }
        runCatching { parent?.recycle() }
        return false
    }

    /**
     * Выполняет клик по прямым координатам на экране.
     */
    suspend fun clickCoordinates(x: Float, y: Float): ActionResult {
        activeScreenSecurityBlockReason()?.let { return ActionResult.Error(it) }
        targetMismatchReason()?.let { return ActionResult.Error(it) }
        TouchVisualizerOverlay.showTouchRipple(x, y, null)
        val success = tapAtCoordinates(x, y)
        return if (success) {
            ActionResult.Success("Клик по координатам ($x, $y) выполнен")
        } else {
            ActionResult.Error("Не удалось выполнить клик в координатах ($x, $y)")
        }
    }

    /**
     * Долгое нажатие по элементу. Нужно для контекстных меню, выделения
     * и перетаскивания, где обычный тап бесполезен.
     */
    suspend fun longClickElement(elementId: Int): ActionResult {
        activeScreenSecurityBlockReason()?.let { return ActionResult.Error(it) }
        cacheFreshnessError()?.let { return ActionResult.Error(it) }
        targetMismatchReason()?.let { return ActionResult.Error(it) }

        val (targetBounds, immediateResult) = synchronized(cacheLock) {
            val bounds = cachedBounds[elementId]
            val node = cachedNodes[elementId]
            if (bounds == null && node == null) {
                null to ActionResult.Error("Элемент с ID #$elementId не найден. Вызови ui_inspect.")
            } else {
                val rect = bounds?.let { Rect(it) } ?: Rect().also { node?.getBoundsInScreen(it) }
                val cx = rect.centerX().toFloat()
                val cy = rect.centerY().toFloat()
                TouchVisualizerOverlay.showTouchRipple(cx, cy, rect)

                val result = when {
                    node?.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK) == true ->
                        ActionResult.Success("Долгое нажатие на элемент #$elementId выполнено через Accessibility")
                    longClickClickableParent(node) ->
                        ActionResult.Success("Долгое нажатие на элемент #$elementId выполнено через родителя")
                    else -> null
                }
                rect to result
            }
        }
        if (immediateResult != null) return immediateResult
        val rect = targetBounds
            ?: return ActionResult.Error("Элемент с ID #$elementId не найден. Вызови ui_inspect.")

        val cx = rect.centerX().toFloat()
        val cy = rect.centerY().toFloat()
        return if (longPressAtCoordinates(cx, cy)) {
            ActionResult.Success("Долгое нажатие на элемент #$elementId выполнено в точке ($cx, $cy)")
        } else {
            ActionResult.Error("Не удалось выполнить долгое нажатие на #$elementId")
        }
    }

    private fun longClickClickableParent(node: AccessibilityNodeInfo?): Boolean {
        var parent = node?.parent
        var depth = 0
        while (parent != null && depth < 4) {
            val current = parent
            if (current.isClickable || current.isLongClickable || current.isCheckable) {
                val clicked = current.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
                runCatching { current.recycle() }
                return clicked
            }
            val next = current.parent
            runCatching { current.recycle() }
            parent = next
            depth++
        }
        runCatching { parent?.recycle() }
        return false
    }

    suspend fun longClickCoordinates(x: Float, y: Float): ActionResult {
        activeScreenSecurityBlockReason()?.let { return ActionResult.Error(it) }
        targetMismatchReason()?.let { return ActionResult.Error(it) }
        TouchVisualizerOverlay.showTouchRipple(x, y, null)
        return if (longPressAtCoordinates(x, y)) {
            ActionResult.Success("Долгое нажатие в точке ($x, $y) выполнено")
        } else {
            ActionResult.Error("Не удалось выполнить долгое нажатие в точке ($x, $y)")
        }
    }

    /**
     * Ввод текста в поле.
     */
    fun inputText(
        elementId: Int?,
        text: String,
        clearFirst: Boolean = false,
        submit: Boolean = false
    ): ActionResult {
        activeScreenSecurityBlockReason()?.let { return ActionResult.Error(it) }
        targetMismatchReason()?.let { return ActionResult.Error(it) }
        if (elementId != null) {
            cacheFreshnessError()?.let { return ActionResult.Error(it) }
        }

        return synchronized(cacheLock) {
            val cachedRef = if (elementId != null) cachedNodes[elementId] else null
            val rootNode = if (elementId == null) rootInActiveWindow else null
            val rawNode = if (elementId != null) {
                cachedRef
            } else {
                findFocusedEditText(rootNode)
            }
            if (rootNode != null && rootNode !== rawNode) {
                runCatching { rootNode.recycle() }
            }
            if (rawNode == null) {
                return@synchronized ActionResult.Error("Поле ввода не найдено. Укажи element_id или сфокусируй поле.")
            }

            // Иногда текст лежит не на самом EditText, а на контейнере или
            // вложенном узле. Ищем редактируемый узел вниз и вверх по дереву.
            val node = findEditableNode(rawNode)
            try {
                if (node == null) {
                    return@synchronized ActionResult.Error("Указанный элемент не является полем ввода.")
                }

                val bounds = Rect().also { node.getBoundsInScreen(it) }
                TouchVisualizerOverlay.highlightTarget(bounds)

                node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)

                if (clearFirst) {
                    val emptyBundle = Bundle().apply {
                        putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "")
                    }
                    node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, emptyBundle)
                }

                val textBundle = Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
                }
                val success = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, textBundle)
                if (!success) {
                    return@synchronized ActionResult.Error("Не удалось установить текст в поле через Accessibility")
                }

                runCatching { node.refresh() }
                val applied = node.text?.toString().orEmpty()
                if (applied.isNotEmpty() && applied != text && !applied.contains(text)) {
                    return@synchronized ActionResult.Error(
                        "Текст отправлен, но поле показывает другое значение " +
                                "('${applied.take(60)}'). Проверь, то ли это поле."
                    )
                }

                if (submit) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        val pressed = node.performAction(
                            AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id
                        )
                        return@synchronized if (pressed) {
                            ActionResult.Success("Текст \"$text\" введён, Enter/Search отправлен")
                        } else {
                            ActionResult.Success("Текст \"$text\" введён. Enter отправить не удалось, нажми кнопку поиска вручную")
                        }
                    }
                    return@synchronized ActionResult.Success("Текст \"$text\" введён. Enter не отправлен: нужен Android 11+")
                }

                ActionResult.Success("Текст \"$text\" успешно введен в поле")
            } finally {
                if (node != null && node !== rawNode && node !== cachedRef) {
                    runCatching { node.recycle() }
                }
                if (rawNode !== cachedRef) {
                    runCatching { rawNode.recycle() }
                }
            }
        }
    }

    /**
     * Ищет редактируемый узел: сам элемент, его потомка (строго вниз) или родителя (строго вверх).
     */
    private fun findEditableNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isEditable) return node

        findEditableDescendant(node)?.let { return it }

        var parent = node.parent
        var depth = 0
        while (parent != null && depth < 4) {
            if (parent.isEditable) return parent
            val next = parent.parent
            runCatching { parent.recycle() }
            parent = next
            depth++
        }
        runCatching { parent?.recycle() }
        return null
    }

    private fun findEditableDescendant(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (child.isEditable) return child
            val found = findEditableDescendant(child)
            runCatching { child.recycle() }
            if (found != null) return found
        }
        return null
    }

    private fun findFocusedEditText(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (root == null) return null
        if (root.isFocused && (root.isEditable || root.className?.contains("EditText") == true)) {
            return root
        }
        val count = root.childCount
        for (i in 0 until count) {
            val child = root.getChild(i) ?: continue
            val found = findFocusedEditText(child)
            if (found != null) {
                if (found !== child) runCatching { child.recycle() }
                return found
            }
            runCatching { child.recycle() }
        }
        return null
    }

    /**
     * Прокрутка экрана в заданном направлении ("down", "up", "left", "right").
     * Если известен scrollable-элемент, используется ACTION_SCROLL_*,
     * иначе жест внутри его bounds или по центру экрана.
     */
    suspend fun scrollScreen(direction: String, elementId: Int? = null): ActionResult {
        activeScreenSecurityBlockReason()?.let { return ActionResult.Error(it) }
        targetMismatchReason()?.let { return ActionResult.Error(it) }
        if (elementId != null) {
            cacheFreshnessError()?.let { return ActionResult.Error(it) }
        }

        val dir = direction.lowercase()
        val isVertical = dir in setOf("down", "вниз", "up", "вверх")
        val isHorizontal = dir in setOf("left", "влево", "right", "вправо")
        if (!isVertical && !isHorizontal) {
            return ActionResult.Error("Неизвестное направление скролла: $direction")
        }

        val (boundsSnapshot, scrolledViaA11y) = synchronized(cacheLock) {
            val cachedRef = if (elementId != null) cachedNodes[elementId] else null
            val rectSnapshot = if (elementId != null) cachedBounds[elementId]?.let { Rect(it) } else null
            val rootNode = if (elementId == null) rootInActiveWindow else null
            val scrollNode = if (elementId != null) {
                findScrollableNode(cachedRef)
            } else {
                findFirstScrollable(rootNode)
            }
            if (rootNode != null && rootNode !== scrollNode) {
                runCatching { rootNode.recycle() }
            }
            val scrolled = if (scrollNode != null) {
                val owned = scrollNode !== cachedRef
                try {
                    if (isVertical) {
                        val action = if (dir == "down" || dir == "вниз") {
                            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                        } else {
                            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                        }
                        scrollNode.performAction(action)
                    } else {
                        false
                    }
                } finally {
                    if (owned) runCatching { scrollNode.recycle() }
                }
            } else {
                false
            }
            rectSnapshot to scrolled
        }
        if (scrolledViaA11y) {
            return ActionResult.Success("Скролл $direction выполнен через Accessibility")
        }

        val displayMetrics = resources.displayMetrics
        val width = displayMetrics.widthPixels.toFloat()
        val height = displayMetrics.heightPixels.toFloat()
        val bounds = boundsSnapshot

        val startX: Float
        val startY: Float
        val endX: Float
        val endY: Float

        if (bounds != null && bounds.width() > 0 && bounds.height() > 0) {
            val centerX = bounds.exactCenterX()
            val centerY = bounds.exactCenterY()
            when (dir) {
                "down", "вниз" -> {
                    startX = centerX
                    startY = bounds.top + bounds.height() * 0.8f
                    endX = centerX
                    endY = bounds.top + bounds.height() * 0.2f
                }
                "up", "вверх" -> {
                    startX = centerX
                    startY = bounds.top + bounds.height() * 0.2f
                    endX = centerX
                    endY = bounds.top + bounds.height() * 0.8f
                }
                "right", "вправо" -> {
                    startX = bounds.left + bounds.width() * 0.8f
                    startY = centerY
                    endX = bounds.left + bounds.width() * 0.2f
                    endY = centerY
                }
                else -> {
                    startX = bounds.left + bounds.width() * 0.2f
                    startY = centerY
                    endX = bounds.left + bounds.width() * 0.8f
                    endY = centerY
                }
            }
        } else {
            when (dir) {
                "down", "вниз" -> {
                    startX = width / 2f
                    startY = height * 0.75f
                    endX = width / 2f
                    endY = height * 0.25f
                }
                "up", "вверх" -> {
                    startX = width / 2f
                    startY = height * 0.25f
                    endX = width / 2f
                    endY = height * 0.75f
                }
                "right", "вправо" -> {
                    startX = width * 0.8f
                    startY = height / 2f
                    endX = width * 0.2f
                    endY = height / 2f
                }
                else -> {
                    startX = width * 0.2f
                    startY = height / 2f
                    endX = width * 0.8f
                    endY = height / 2f
                }
            }
        }

        val success = dispatchSwipeGesture(startX, startY, endX, endY, 300)
        return if (success) {
            ActionResult.Success("Скролл $direction выполнен")
        } else {
            ActionResult.Error("Не удалось выполнить скролл $direction")
        }
    }

    private fun findScrollableNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isScrollable) return node

        findScrollableDescendant(node)?.let { return it }

        var parent = node.parent
        var depth = 0
        while (parent != null && depth < 4) {
            if (parent.isScrollable) return parent
            val next = parent.parent
            runCatching { parent.recycle() }
            parent = next
            depth++
        }
        runCatching { parent?.recycle() }
        return null
    }

    private fun findScrollableDescendant(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (child.isScrollable) return child
            val found = findScrollableDescendant(child)
            runCatching { child.recycle() }
            if (found != null) return found
        }
        return null
    }

    private fun findFirstScrollable(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isScrollable) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findFirstScrollable(child)
            if (found != null) {
                if (found !== child) runCatching { child.recycle() }
                return found
            }
            runCatching { child.recycle() }
        }
        return null
    }

    /**
     * Системные глобальные действия: back, home, recents, notifications.
     */
    fun performSystemAction(action: String): ActionResult {
        val globalAction = when (action.lowercase()) {
            "back", "назад" -> GLOBAL_ACTION_BACK
            "home", "домой" -> GLOBAL_ACTION_HOME
            "recents", "недавние" -> GLOBAL_ACTION_RECENTS
            "notifications", "уведомления" -> GLOBAL_ACTION_NOTIFICATIONS
            else -> return ActionResult.Error("Неизвестное системное действие: $action")
        }

        val success = performGlobalAction(globalAction)
        return if (success) {
            ActionResult.Success("Системное действие $action выполнено")
        } else {
            ActionResult.Error("Не удалось выполнить системное действие $action")
        }
    }

    private fun screenDimensions(): Pair<Float, Float> {
        val dm = resources.displayMetrics
        return (dm.widthPixels - 1).coerceAtLeast(1).toFloat() to
                (dm.heightPixels - 1).coerceAtLeast(1).toFloat()
    }

    private suspend fun tapAtCoordinates(x: Float, y: Float): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        val (maxX, maxY) = screenDimensions()
        val safeX = clampGestureCoordinate(x, maxX)
        val safeY = clampGestureCoordinate(y, maxY)
        val path = Path().apply {
            moveTo(safeX, safeY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, 60)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGestureSafely(gesture)
    }

    /** Долгое нажатие: тот же жест, но с удержанием 600 мс. */
    private suspend fun longPressAtCoordinates(x: Float, y: Float): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        val (maxX, maxY) = screenDimensions()
        val safeX = clampGestureCoordinate(x, maxX)
        val safeY = clampGestureCoordinate(y, maxY)
        val path = Path().apply {
            moveTo(safeX, safeY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, 600)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGestureSafely(gesture)
    }

    private suspend fun dispatchSwipeGesture(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        durationMs: Long
    ): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        val (maxX, maxY) = screenDimensions()
        val path = Path().apply {
            moveTo(clampGestureCoordinate(startX, maxX), clampGestureCoordinate(startY, maxY))
            lineTo(clampGestureCoordinate(endX, maxX), clampGestureCoordinate(endY, maxY))
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs.coerceAtLeast(1L))
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGestureSafely(gesture)
    }

    /**
     * Отправляет жест и ждёт результата ограниченное время.
     *
     * Колбэк может не прийти вовсе: службу отключили между проверкой instance
     * и вызовом, жест вытеснил другой жест, система не отдала событие. Без
     * предела корутина шага ждала бы вечно, а вместе с ней и вся задача.
     * По таймауту считаем, что жест не удался.
     */
    private suspend fun dispatchGestureSafely(gesture: GestureDescription): Boolean {
        val delivered = withTimeoutOrNull(GESTURE_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                val callback = object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        if (continuation.isActive) continuation.resume(true)
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        if (continuation.isActive) continuation.resume(false)
                    }
                }
                val dispatched = dispatchGesture(gesture, callback, null)
                if (!dispatched && continuation.isActive) {
                    continuation.resume(false)
                }
            }
        }
        return delivered ?: false
    }

    companion object {
        @Volatile
        var instance: RunnerAccessibilityService? = null

        /**
         * Предел ожидания результата жеста. Сам жест длится не больше 300 мс,
         * три секунды — щедрый запас; нужен, чтобы отсутствие колбэка не
         * превращалось в вечно висящий шаг задачи.
         */
        private const val GESTURE_TIMEOUT_MS = 3_000L

        /** Сколько живёт кэш #id, прежде чем попросить новый ui_inspect. */
        private const val CACHE_TTL_MS = 60_000L

        /**
         * Максимум элементов в одном ui_inspect. 90 вместо 150: меньше
         * токенов и быстрее ответ модели, при этом интерактивные элементы
         * всё равно идут первыми.
         */
        private const val MAX_UI_ELEMENTS = 90
        private const val MAX_CONTEXT_ELEMENTS = 40
        private const val MAX_UI_TEXT = 60
        private const val MAX_UI_DESC = 40
        private val UI_WHITESPACE = Regex("\\s+")

        /**
         * Окна, которые не считаются «чужим приложением»: системные диалоги,
         * клавиатура, permission-контроллер. Их можно нажимать без ui_target.
         */
        // `"android"` проверяется только точным совпадением выше, а
        // `com.google.android.gms` убран: под ним работают экраны Google Pay и аккаунтов.
        private val TARGET_LOCK_EXEMPT_EXACT = setOf(
            "android"
        )

        private val TARGET_LOCK_EXEMPT_PREFIXES = listOf(
            "com.android.systemui",
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller",
            "com.android.intentresolver",
            "com.google.android.intentresolver",
            "com.android.inputmethod",
            "com.google.android.inputmethod"
        )

        internal fun isTargetLockExemptPackage(packageName: String): Boolean {
            val pkg = packageName.trim().lowercase()
            if (pkg.isEmpty()) return false
            if (isSensitivePackageName(pkg)) return false
            if (pkg in TARGET_LOCK_EXEMPT_EXACT) return true
            if (TARGET_LOCK_EXEMPT_PREFIXES.any { prefix ->
                    pkg == prefix || pkg.startsWith("$prefix.")
                }
            ) {
                return true
            }
            val segments = pkg.split('.')
            return segments.any { it.contains("inputmethod") || it.contains("keyboard") }
        }

        fun isSensitivePackageName(packageName: String): Boolean {
            if (packageName.isBlank()) return false
            val segments = packageName.lowercase().split('.')
            return SENSITIVE_PACKAGES.any { entry ->
                segments.any { segment -> segment == entry || segment.startsWith(entry) }
            }
        }

        /**
         * Ограничивает координату жеста допустимым диапазоном экрана:
         * `GestureDescription.StrokeDescription` бросает IllegalArgumentException
         * на отрицательных или не-конечных (NaN/Infinity) координатах.
         */
        internal fun clampGestureCoordinate(value: Float, maxExtent: Float): Float {
            if (!value.isFinite()) return 0f
            val upper = maxExtent.coerceAtLeast(1f)
            return value.coerceIn(0f, upper)
        }

        fun isEnabled(context: Context): Boolean {
            if (instance != null) return true
            return try {
                val enabledServices = Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                ) ?: return false
                val expected = "${context.packageName}/${RunnerAccessibilityService::class.java.canonicalName}"
                val shortExpected = "${context.packageName}/.service.RunnerAccessibilityService"
                enabledServices.contains(expected) || enabledServices.contains(shortExpected)
            } catch (_: Exception) {
                false
            }
        }

        private val SENSITIVE_PACKAGES = setOf(
            "sberbank",
            "tinkoff",
            "idamob",
            "vtb",
            "alfabank",
            "raiffeisen",
            "mobicom",
            "rosbank",
            "gazprombank",
            "sovcomcard",
            "gosuslugi",
            "passwordmanager",
            "1password",
            "bitwarden",
            "keepass",
            "lastpass",
            "authenticator"
        )
    }

    sealed class ScreenInspectionResult {
        data class Success(
            val packageName: String,
            val formattedHierarchy: String,
            val nodes: List<NodeEntry>
        ) : ScreenInspectionResult()

        data class SecurityBlocked(val reason: String) : ScreenInspectionResult()
        data class Error(val message: String) : ScreenInspectionResult()
    }

    sealed class ActionResult {
        data class Success(val message: String) : ActionResult()
        data class Error(val message: String) : ActionResult()
    }

    data class NodeEntry(
        val id: Int,
        val type: String,
        val text: String,
        val description: String,
        val bounds: Rect,
        val isClickable: Boolean,
        val isEditable: Boolean,
        val isScrollable: Boolean,
        val isChecked: Boolean?
    )
}
