package com.runner.app

import com.runner.app.util.PrefatoryText
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrefatoryTextTest {

    @Test
    fun recognizesRussianActionPromises() {
        listOf(
            "Сейчас посмотрю.",
            "Открываю vpnify.",
            "Начинаю с открытия vpnify.",
            "Проверяю экран.",
            "Проверю, что там.",
            "Сейчас покажу результат.",
            "Сейчас гляну.",
            "Щас посмотрю.",
            "Экран показывает подключение. Проверяю."
        ).forEach { text ->
            assertTrue("Должно считаться обещанием: $text", PrefatoryText.isPrefatory(text))
        }
    }

    @Test
    fun recognizesEnglishActionPromises() {
        listOf(
            "Let me check.",
            "I'll open the app.",
            "I will check the screen.",
            "Now opening the settings."
        ).forEach { text ->
            assertTrue("Должно считаться обещанием: $text", PrefatoryText.isPrefatory(text))
        }
    }

    @Test
    fun doesNotTreatFinishedResultAsPromise() {
        listOf(
            "Готово: файл создан.",
            "Нашёл 43 файла.",
            "Файл создан, 12 МБ.",
            "Проверяю экран, вижу кнопку.",
            "Экран показывает «Соединение...».",
            "Done. The file was created."
        ).forEach { text ->
            assertFalse("Это не обещание: $text", PrefatoryText.isPrefatory(text))
        }
    }

    @Test
    fun doesNotTreatQuestionAsPromise() {
        assertFalse(PrefatoryText.isPrefatory("Что открыть?"))
        assertFalse(PrefatoryText.isPrefatory("Should I open it?"))
    }

    @Test
    fun doesNotTreatLongTextAsPromise() {
        val long = "Сейчас посмотрю. " + "Подробности ".repeat(30)
        assertFalse(PrefatoryText.isPrefatory(long))
    }

    @Test
    fun doesNotTreatCodeAsPromise() {
        assertFalse(PrefatoryText.isPrefatory("Сейчас посмотрю.\n```kotlin\nval x = 1\n```"))
    }
}
