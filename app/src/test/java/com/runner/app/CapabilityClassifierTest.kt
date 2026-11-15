package com.runner.app

import com.runner.app.ui.looksActionable
import com.runner.app.ui.looksLikeCapabilityRefusal
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CapabilityClassifierTest {

    @Test
    fun actionableRequestsAreRecognized() {
        listOf(
            "поставь ffmpeg в термуксе",
            "найди последний чат в телеге",
            "проверь папку AI Motion",
            "собери ролик на 2 минуты",
            "открой настройки",
            "почини скрипт"
        ).forEach { prompt ->
            assertTrue("Должно быть действием: $prompt", looksActionable(prompt))
        }
    }

    @Test
    fun plainQuestionsAreNotActions() {
        listOf(
            "что такое kotlin",
            "как дела",
            "объясни разницу между tcp и udp",
            "почему небо синее"
        ).forEach { prompt ->
            assertFalse("Это не действие: $prompt", looksActionable(prompt))
        }
    }

    @Test
    fun refusalIsRecognized() {
        listOf(
            "Не могу: нет доступа к Termux",
            "У меня нет прав на этот путь",
            "Это не поддерживается на Android",
            "Access denied"
        ).forEach { text ->
            assertTrue("Должен быть отказ: $text", looksLikeCapabilityRefusal(text))
        }
    }

    @Test
    fun normalAnswerIsNotRefusal() {
        listOf(
            "Готово, файл записан",
            "Вот список файлов в папке Download",
            "Ролик собран, длительность 2 минуты"
        ).forEach { text ->
            assertFalse("Это не отказ: $text", looksLikeCapabilityRefusal(text))
        }
    }
}
