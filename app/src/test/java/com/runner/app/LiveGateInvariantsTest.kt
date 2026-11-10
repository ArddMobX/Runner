package com.runner.app

import com.runner.app.tools.ToolDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Инварианты гейта подтверждений.
 *
 * Тест сторожит три вещи, которые уже ломались и ломаются молча:
 *  - звонок и действия на экране считаются опасными, иначе режимы
 *    «только чтение» и «сухой прогон» их не блокируют;
 *  - инструменты управления экраном отделены от обычных: вне Live-режима
 *    их не должно быть видно модели;
 *  - второго списка опасных операций нет.
 *
 * Проверяются только наборы и функции без Android API, поэтому тест идёт
 * в обычном JVM-прогоне, без Robolectric.
 */
class LiveGateInvariantsTest {

    private val liveOnly = ToolDispatcher.LIVE_ONLY_TOOL_NAMES

    @Test
    fun liveOnlySetIsExactlyScreenControl() {
        assertEquals(
            setOf(
                "ui_inspect", "ui_click", "ui_long_click", "ui_type", "ui_scroll",
                "ui_global_action", "ui_wait", "ui_wait_for", "ui_target", "ui_finish"
            ),
            liveOnly
        )
    }

    @Test
    fun liveScopeAddsOnlyOpenApp() {
        // open_app работает и в обычном чате, поэтому в «только Live» его нет.
        assertEquals(liveOnly + "open_app", ToolDispatcher.LIVE_TOOL_NAMES)
        assertFalse("open_app не должен быть только-Live", "open_app" in liveOnly)
    }

    @Test
    fun screenActionsThatChangeStateAreDangerous() {
        assertTrue(ToolDispatcher.isCriticalOperation("ui_click"))
        assertTrue(ToolDispatcher.isCriticalOperation("ui_long_click"))
        assertTrue(ToolDispatcher.isCriticalOperation("ui_type"))
        assertTrue(ToolDispatcher.isCriticalOperation("ui_global_action"))
        assertTrue(ToolDispatcher.isCriticalOperation("call_phone"))
    }

    @Test
    fun readingAndNavigationAreNotDangerous() {
        // Иначе «только чтение» запрещало бы агенту смотреть на экран.
        assertFalse(ToolDispatcher.isCriticalOperation("ui_inspect"))
        assertFalse(ToolDispatcher.isCriticalOperation("ui_scroll"))
        assertFalse(ToolDispatcher.isCriticalOperation("ui_wait"))
        assertFalse(ToolDispatcher.isCriticalOperation("ui_wait_for"))
        assertFalse(ToolDispatcher.isCriticalOperation("ui_target"))
        assertFalse(ToolDispatcher.isCriticalOperation("ui_finish"))
        assertFalse(ToolDispatcher.isCriticalOperation("read_file"))
        assertFalse(ToolDispatcher.isCriticalOperation("list_dir"))
    }

    @Test
    fun dangerousSetCoversFilesAndDevice() {
        val dangerous = ToolDispatcher.DANGEROUS_OPERATIONS
        assertEquals(16, dangerous.size)
        // Файловые операции и shell — на месте: список расширяли, а не заменяли.
        assertTrue(dangerous.containsAll(setOf("delete_file", "write_file", "run_shell_command")))
        // Действия на экране, меняющие состояние, входят целиком.
        assertTrue(
            dangerous.containsAll(
                setOf("ui_click", "ui_long_click", "ui_type", "ui_global_action", "call_phone")
            )
        )
    }

    @Test
    fun toolNamesAreNormalizedByName() {
        // Модель иногда присылает имя с префиксом: "namespace:tool".
        assertTrue(ToolDispatcher.isCriticalOperation("functions:ui_click"))
        assertEquals(10, liveOnly.size)
    }
}
