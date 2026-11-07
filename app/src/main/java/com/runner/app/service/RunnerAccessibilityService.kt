package com.runner.app.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.GestureResultCallback
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.runner.app.ui.LiveAgentManager
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

    private val cachedNodes = mutableMapOf<Int, AccessibilityNodeInfo>()
    private val cachedBounds = mutableMapOf<Int, Rect>()
    private var lastExtractedPackage: String = ""

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // События отслеживаются при необходимости
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
        cachedNodes.values.forEach { runCatching { it.recycle() } }
        cachedNodes.clear()
        cachedBounds.clear()
    }

    /**
     * Проверяет безопасность текущего экрана и извлекает компактную иерархию элементов.
     */
    fun dumpScreenHierarchy(): ScreenInspectionResult {
        clearNodeCache()

        val root = rootInActiveWindow ?: return ScreenInspectionResult.Error("Экран недоступен или заблокирован")
        val pkg = root.packageName?.toString().orEmpty()
        lastExtractedPackage = pkg

        // Проверка черного списка безопасности. Смотрим ВСЕ окна, а не только
        // активное: банк может быть открыт вторым окном в split-screen, а сверху
        // висеть браузер — и тогда проверка одного активного окна пропускала
        // управление банковским приложением.
        val sensitivePackage = sensitiveWindowPackage()
        if (sensitivePackage != null) {
            root.recycle()
            return ScreenInspectionResult.SecurityBlocked(
                "Блокировка безопасности: обнаружено чувствительное приложение " +
                        "($sensitivePackage). В целях защиты данных Live-режим остановлен."
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

        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        val visibleNodes = mutableListOf<NodeEntry>()
        var nextId = 1

        fun traverse(node: AccessibilityNodeInfo?) {
            if (node == null) return
            if (!node.isVisibleToUser) {
                node.recycle()
                return
            }

            val bounds = Rect()
            node.getBoundsInScreen(bounds)

            // Пропускаем узлы нулевого размера или полностью за пределами экрана
            if (bounds.width() <= 0 || bounds.height() <= 0 ||
                bounds.bottom < 0 || bounds.right < 0 ||
                bounds.left > screenWidth || bounds.top > screenHeight) {
                node.recycle()
                return
            }

            val text = node.text?.toString()?.trim().orEmpty()
            val desc = node.contentDescription?.toString()?.trim().orEmpty()
            val isClickable = node.isClickable
            val isEditable = node.isEditable
            val isScrollable = node.isScrollable
            val isCheckable = node.isCheckable

            val isMeaningful = text.isNotEmpty() || desc.isNotEmpty() ||
                    isClickable || isEditable || isScrollable || isCheckable

            if (isMeaningful) {
                val elementId = nextId++
                cachedNodes[elementId] = node
                cachedBounds[elementId] = bounds

                val typeLabel = simplifyClassName(node.className?.toString())
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
            } else {
                // Если узел сам по себе не интерактивен и не несет текста,
                // проверяем детей, а сам узел утилизируем
                val childCount = node.childCount
                for (i in 0 until childCount) {
                    traverse(node.getChild(i))
                }
                node.recycle()
                return
            }

            val childCount = node.childCount
            for (i in 0 until childCount) {
                traverse(node.getChild(i))
            }
        }

        traverse(root)

        val formattedTree = buildString {
            appendLine("Приложение: $pkg (размер экрана: ${screenWidth}x$screenHeight)")
            if (visibleNodes.isEmpty()) {
                appendLine("Интерактивные элементы не найдены.")
            } else {
                appendLine("Видимые элементы:")
                for (entry in visibleNodes) {
                    append("[#${entry.id}] ${entry.type}")
                    if (entry.text.isNotEmpty()) append(" \"${entry.text}\"")
                    if (entry.description.isNotEmpty() && entry.description != entry.text) {
                        append(" (описание: \"${entry.description}\")")
                    }
                    if (entry.isChecked != null) {
                        append(" [выбрано=${entry.isChecked}]")
                    }
                    val cx = entry.bounds.centerX()
                    val cy = entry.bounds.centerY()
                    append(" -> центр: ($cx, $cy)")
                    appendLine(" границы: [${entry.bounds.left}, ${entry.bounds.top}, ${entry.bounds.right}, ${entry.bounds.bottom}]")
                }
            }
        }

        return ScreenInspectionResult.Success(
            packageName = pkg,
            formattedHierarchy = formattedTree,
            nodes = visibleNodes
        )
    }

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
        if (root.isPassword) return true
        val inputType = root.inputType
        val isPasswordType = (inputType and InputType.TYPE_TEXT_VARIATION_PASSWORD) != 0 ||
                (inputType and InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD) != 0 ||
                (inputType and InputType.TYPE_NUMBER_VARIATION_PASSWORD) != 0
        if (isPasswordType) return true

        val count = root.childCount
        for (i in 0 until count) {
            val child = root.getChild(i) ?: continue
            if (hasPasswordFields(child)) {
                child.recycle()
                return true
            }
            child.recycle()
        }
        return false
    }

    /**
     * Пакет любого видимого окна, который считается чувствительным, либо null.
     *
     * Дешёвая проверка: читаем только имя пакета корня окна, без обхода дерева
     * (обход всех окон на каждом `ui_inspect` был бы слишком дорогим).
     */
    private fun sensitiveWindowPackage(): String? {
        val windowList = try {
            windows
        } catch (e: Exception) {
            null
        } ?: return null

        for (window in windowList) {
            val windowRoot = try {
                window.root
            } catch (e: Exception) {
                null
            } ?: continue
            try {
                val name = windowRoot.packageName?.toString().orEmpty()
                if (isSensitivePackage(name)) return name
            } finally {
                windowRoot.recycle()
            }
        }
        return null
    }

    /**
     * Пакет относится к чувствительному приложению?
     *
     * Сверяем по сегментам имени пакета: `ru.vtb24.mobilebanking` состоит из
     * сегментов `ru`, `vtb24`, `mobilebanking`, и запись `vtb` должна совпасть
     * с `vtb24`, но не с `com.example.vtbx.photo`. Раньше сравнение было
     * подстрокой по всему имени: «vtb» находилось в любом пакете, где эти три
     * буквы стоят рядом, а «sberbank» — в имени чужой игры.
     *
     * Совпадение по префиксу сегмента оставляет ложные срабатывания (пакет
     * `vtbx` заблокируется), но это безопасное направление: лишний отказ
     * лучше, чем управление банковским приложением.
     */
    private fun isSensitivePackage(packageName: String): Boolean {
        if (packageName.isBlank()) return false
        val segments = packageName.lowercase().split('.')
        return SENSITIVE_PACKAGES.any { entry ->
            segments.any { segment -> segment == entry || segment.startsWith(entry) }
        }
    }

    /**
     * Выполняет клик по элементу с заданным ID.
     */
    suspend fun clickElement(elementId: Int): ActionResult {
        val bounds = cachedBounds[elementId]
        val node = cachedNodes[elementId]

        if (bounds == null && node == null) {
            return ActionResult.Error("Элемент с ID #$elementId не найден на экране. Вызовите ui_inspect для обновления дерева.")
        }

        val targetBounds = bounds ?: Rect().also { node?.getBoundsInScreen(it) }
        val cx = targetBounds.centerX().toFloat()
        val cy = targetBounds.centerY().toFloat()

        // Визуализируем тач и подсветку элемента
        TouchVisualizerOverlay.showTouchRipple(cx, cy, targetBounds)

        // Предпочитаем жест тапа по координатам центра, так как он работает универсально
        val gestureSuccess = tapAtCoordinates(cx, cy)
        if (gestureSuccess) {
            return ActionResult.Success("Успешный клик по элементу #$elementId в точке ($cx, $cy)")
        }

        // Резервный вариант через действие доступности
        val actionSuccess = node?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
        return if (actionSuccess) {
            ActionResult.Success("Клик по элементу #$elementId выполнен через Accessibility ACTION_CLICK")
        } else {
            ActionResult.Error("Не удалось выполнить клик по элементу #$elementId")
        }
    }

    /**
     * Выполняет клик по прямым координатам на экране.
     */
    suspend fun clickCoordinates(x: Float, y: Float): ActionResult {
        TouchVisualizerOverlay.showTouchRipple(x, y, null)
        val success = tapAtCoordinates(x, y)
        return if (success) {
            ActionResult.Success("Клик по координатам ($x, $y) выполнен")
        } else {
            ActionResult.Error("Не удалось выполнить клик в координатах ($x, $y)")
        }
    }

    /**
     * Ввод текста в поле.
     */
    fun inputText(elementId: Int?, text: String, clearFirst: Boolean = false): ActionResult {
        val node = if (elementId != null) {
            cachedNodes[elementId]
        } else {
            findFocusedEditText(rootInActiveWindow)
        }

        if (node == null) {
            return ActionResult.Error("Поле ввода не найдено. Укажите element_id или убедитесь, что поле в фокусе.")
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

        return if (success) {
            ActionResult.Success("Текст \"$text\" успешно введен в поле")
        } else {
            ActionResult.Error("Не удалось установить текст в поле через Accessibility")
        }
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
            if (found != null) return found
            child.recycle()
        }
        return null
    }

    /**
     * Прокрутка экрана в заданном направлении ("down", "up", "left", "right").
     */
    suspend fun scrollScreen(direction: String): ActionResult {
        val displayMetrics = resources.displayMetrics
        val width = displayMetrics.widthPixels.toFloat()
        val height = displayMetrics.heightPixels.toFloat()

        val startX: Float
        val startY: Float
        val endX: Float
        val endY: Float

        when (direction.lowercase()) {
            "down", "вниз" -> {
                // Прокрутка содержимого вниз (движение пальца снизу вверх)
                startX = width / 2f
                startY = height * 0.75f
                endX = width / 2f
                endY = height * 0.25f
            }
            "up", "вверх" -> {
                // Прокрутка содержимого вверх (движение пальца сверху вниз)
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
            "left", "влево" -> {
                startX = width * 0.2f
                startY = height / 2f
                endX = width * 0.8f
                endY = height / 2f
            }
            else -> return ActionResult.Error("Неизвестное направление скролла: $direction")
        }

        val success = dispatchSwipeGesture(startX, startY, endX, endY, 300)
        return if (success) {
            ActionResult.Success("Скролл $direction выполнен")
        } else {
            ActionResult.Error("Не удалось выполнить скролл $direction")
        }
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

    private suspend fun tapAtCoordinates(x: Float, y: Float): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        val path = Path().apply {
            moveTo(x, y)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, 60)
        return dispatchGestureSafely(GestureDescription.Builder().addStroke(stroke).build())
    }

    private suspend fun dispatchSwipeGesture(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        durationMs: Long
    ): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        return dispatchGestureSafely(GestureDescription.Builder().addStroke(stroke).build())
    }

    /**
     * Отправляет жест и ждёт результата ограниченное время.
     *
     * Колбэк может не прийти вовсе: службу отключили между проверкой `instance`
     * и вызовом, жест вытеснил другой жест, система не отдала событие. Без
     * предела корутина шага ждала бы вечно, а вместе с ней и вся задача —
     * именно так Live-режим и зависал. По таймауту считаем, что жест не удался.
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
         * Предел ожидания результата жеста.
         *
         * Сам жест длится не больше 300 мс, поэтому три секунды — заведомо
         * щедрый запас. Нужен он не для скорости, а чтобы отсутствие колбэка
         * не превращалось в вечно висящий шаг задачи.
         */
        private const val GESTURE_TIMEOUT_MS = 3_000L

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

        /**
         * Банки, госуслуги и хранилища паролей, которыми агент управлять не должен.
         *
         * Список — эвристика, а не гарантия: полноту даёт только белый список
         * приложений. Сверяется по сегментам имени пакета (см.
         * [isSensitivePackage]) и по ВСЕМ окнам (см. [sensitiveWindowPackage]).
         */
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
            "passwords",
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
