package com.runner.app

import com.runner.app.ui.shouldPlanLive
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveTaskClassifierTest {

    @Test
    fun simpleOneShotTasksSkipPlanner() {
        listOf(
            "Открой Telegram",
            "Включи VPN",
            "Нажми кнопку",
            "Покажи последние новости США",
            "Открой настройки Wi-Fi",
            "Запусти приложение"
        ).forEach { text ->
            assertFalse("Не нужен план: $text", shouldPlanLive(text))
        }
    }

    @Test
    fun multiStepTasksStillPlan() {
        listOf(
            "Открой Telegram и найди последний чат",
            "Включи VPN, открой Gemini",
            "Найди последний чат в Telegram",
            "Удали файл",
            "Запусти скрипт",
            "Составь план"
        ).forEach { text ->
            assertTrue("Нужен план: $text", shouldPlanLive(text))
        }
    }

    @Test
    fun greetingsAndSmallTalkSkipPlanner() {
        listOf("Привет", "Спасибо", "Как дела", "Продолжай").forEach { text ->
            assertFalse("Не нужен план: $text", shouldPlanLive(text))
        }
    }
}
