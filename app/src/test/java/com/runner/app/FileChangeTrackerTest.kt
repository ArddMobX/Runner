package com.runner.app

import com.runner.app.tools.FileChangeTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FileChangeTrackerTest {

    @Test
    fun lineDiff_countsAddedAndRemoved() {
        val result = FileChangeTracker.LineDiff.compare(
            "один\nдва\nтри",
            "один\nДВА\nтри"
        )
        assertEquals(1, result.added)
        assertEquals(1, result.removed)
        assertTrue(result.preview.orEmpty().contains("- два"))
        assertTrue(result.preview.orEmpty().contains("+ ДВА"))
    }

    @Test
    fun lineDiff_newFileIsAllAdditions() {
        val result = FileChangeTracker.LineDiff.compare("", "строка 1\nстрока 2")
        assertEquals(2, result.added)
        assertEquals(0, result.removed)
    }

    @Test
    fun lineDiff_identicalTextHasNoChanges() {
        val result = FileChangeTracker.LineDiff.compare("a\nb", "a\nb")
        assertEquals(0, result.added)
        assertEquals(0, result.removed)
        assertNull(result.preview)
    }

    @Test
    fun lineDiff_appendShowsOnlyAdditions() {
        val result = FileChangeTracker.LineDiff.compare("старое", "старое\nновое")
        assertEquals(1, result.added)
        assertEquals(0, result.removed)
        assertTrue(result.preview.orEmpty().contains("+ новое"))
    }
}
