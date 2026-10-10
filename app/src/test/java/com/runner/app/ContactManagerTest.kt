package com.runner.app

import com.runner.app.util.ContactManager
import org.junit.Assert.assertEquals
import org.junit.Test

class ContactManagerTest {

    @Test
    fun testNormalizePhoneNumber_russianFormats() {
        // Проверяем приведение российских номеров с 89... к +79...
        assertEquals("+79991234567", ContactManager.normalizePhoneNumber("8 (999) 123-45-67"))
        assertEquals("+79991234567", ContactManager.normalizePhoneNumber("+7 (999) 123-45-67"))
        assertEquals("+79991234567", ContactManager.normalizePhoneNumber("89991234567"))
        assertEquals("+79991234567", ContactManager.normalizePhoneNumber("+79991234567"))
    }

    @Test
    fun testNormalizePhoneNumber_stripsFormattingCharacters() {
        assertEquals("+79161112233", ContactManager.normalizePhoneNumber("+7 916 111 22 33"))
        assertEquals("+12125550199", ContactManager.normalizePhoneNumber("+1 (212) 555-0199"))
        assertEquals("*100#", ContactManager.normalizePhoneNumber("*100#"))
    }

    @Test
    fun testNormalizePhoneNumber_emptyAndSpecial() {
        assertEquals("", ContactManager.normalizePhoneNumber(""))
        assertEquals("", ContactManager.normalizePhoneNumber("   "))
        assertEquals("", ContactManager.normalizePhoneNumber("abc-def"))
    }
}
