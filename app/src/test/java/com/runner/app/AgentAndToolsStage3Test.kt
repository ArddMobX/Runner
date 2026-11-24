package com.runner.app

import com.runner.app.tools.ToolDispatcher
import com.runner.app.tools.TrashStore
import com.runner.app.ui.looksLikeToolError
import com.runner.app.util.DocumentExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Регрессионные pure-JVM тесты для исправлений этапа 3 (ядро агента и файловые инструменты).
 */
class AgentAndToolsStage3Test {

    @get:Rule
    val tempFolder = TemporaryFolder()

    // --- MainViewModel.looksLikeToolError ---

    @Test
    fun looksLikeToolError_ignoresExceptionAndErrorInsideUntrustedEnvelope() {
        val output = buildString {
            appendLine("resolved_path: /storage/emulated/0/src/MyException.kt")
            appendLine(ToolDispatcher.UNTRUSTED_OPEN)
            appendLine("Содержимое файла 'MyException.kt':")
            appendLine("```")
            appendLine("class MyException : RuntimeException(\"Ошибка: тестовая\")")
            appendLine("java.io.IOException: пример из комментария")
            appendLine("```")
            appendLine(ToolDispatcher.UNTRUSTED_CLOSE)
        }
        assertFalse(looksLikeToolError(output))
    }

    @Test
    fun looksLikeToolError_ignoresErrorWordsInsideSuccessfulShellCodeBlock() {
        val output = buildString {
            appendLine("Выполнено (код 0):")
            appendLine("```")
            appendLine("Ошибка: текст внутри stdout успешной команды")
            appendLine("IllegalStateException: строка в grep-выводе")
            appendLine("```")
        }
        assertFalse(looksLikeToolError(output))
    }

    @Test
    fun looksLikeToolError_detectsRealToolErrorsAndNonZeroShellExit() {
        assertTrue(looksLikeToolError("resolved_path: /sdcard/a\nОшибка: файла нет."))
        assertTrue(looksLikeToolError("Не удалось открыть файл"))
        assertTrue(looksLikeToolError("Выполнено (код 127):\n```\nsh: foo: not found\n```"))
        assertTrue(looksLikeToolError("java.io.IOException: Permission denied"))
        assertTrue(looksLikeToolError("SecurityException: Доступ запрещён"))
    }

    @Test
    fun looksLikeToolError_doesNotFlagFileNamesContainingExceptionWord() {
        val output = "Найдено файлов: 2\n  • /sdcard/app/ExceptionHandler.kt\n  • /sdcard/app/CustomException.kt"
        assertFalse(looksLikeToolError(output))
    }

    // --- ToolDispatcher.checkCopyOrMovePathConflict ---

    @Test
    fun checkCopyOrMovePathConflict_rejectsSamePathAndSelfNesting() {
        assertNotNull(
            ToolDispatcher.checkCopyOrMovePathConflict(
                srcCanonical = "/storage/emulated/0/Docs/a.txt",
                destCanonical = "/storage/emulated/0/Docs/a.txt",
                srcIsDirectory = false
            )
        )
        assertNotNull(
            ToolDispatcher.checkCopyOrMovePathConflict(
                srcCanonical = "/storage/emulated/0/Docs",
                destCanonical = "/storage/emulated/0/Docs/sub/copy",
                srcIsDirectory = true
            )
        )
    }

    @Test
    fun checkCopyOrMovePathConflict_allowsSiblingPrefixAndDifferentPaths() {
        assertNull(
            ToolDispatcher.checkCopyOrMovePathConflict(
                srcCanonical = "/storage/emulated/0/Docs",
                destCanonical = "/storage/emulated/0/Docs2/sub",
                srcIsDirectory = true
            )
        )
        assertNull(
            ToolDispatcher.checkCopyOrMovePathConflict(
                srcCanonical = "/storage/emulated/0/Docs/a.txt",
                destCanonical = "/storage/emulated/0/Docs/b.txt",
                srcIsDirectory = false
            )
        )
    }

    // --- ToolDispatcher.readFileLinesCapped ---

    @Test
    fun readFileLinesCapped_capsHugeSingleLineWithoutOom() {
        val file = tempFolder.newFile("minified.json")
        file.writeText("x".repeat(5000))

        val (lines, truncated) = ToolDispatcher.readFileLinesCapped(
            file = file,
            maxLines = 200,
            maxChars = 100
        )

        assertTrue(truncated)
        assertEquals(1, lines.size)
        assertEquals(100, lines.first().length)
    }

    @Test
    fun readFileLinesCapped_respectsLineLimitAndTrailingCrlf() {
        val exactCrlf = tempFolder.newFile("exact_crlf.txt")
        exactCrlf.writeText("one\r\ntwo\r\n")

        val (exactLines, exactTruncated) = ToolDispatcher.readFileLinesCapped(
            file = exactCrlf,
            maxLines = 2,
            maxChars = 1024
        )
        assertEquals(listOf("one", "two"), exactLines)
        assertFalse(exactTruncated)

        val extraCrlf = tempFolder.newFile("extra_crlf.txt")
        extraCrlf.writeText("one\r\ntwo\r\nthree\r\n")

        val (extraLines, extraTruncated) = ToolDispatcher.readFileLinesCapped(
            file = extraCrlf,
            maxLines = 2,
            maxChars = 1024
        )
        assertEquals(listOf("one", "two"), extraLines)
        assertTrue(extraTruncated)
    }

    // --- DocumentExtractor ---

    @Test
    fun documentExtractor_renderPdfTargetSize_capsGiantBlueprintToMaxDim() {
        val (w, h) = DocumentExtractor.renderPdfTargetSize(14400, 14400)
        assertTrue("width=$w must be <= 1400", w <= 1400)
        assertTrue("height=$h must be <= 1400", h <= 1400)
        assertEquals(1400, w)
        assertEquals(1400, h)

        val (wideW, wideH) = DocumentExtractor.renderPdfTargetSize(10000, 2000)
        assertEquals(1400, wideW)
        assertTrue(wideH in 279..281)
    }

    @Test
    fun documentExtractor_extractXlsx_handlesSelfClosingEmptyCells() {
        val xlsx = buildZip(
            "self_closing.xlsx",
            mapOf(
                "xl/sharedStrings.xml" to """
                    <sst><si><t>ЗначениеB1</t></si><si><t>ЗначениеD1</t></si></sst>
                """.trimIndent(),
                "xl/worksheets/sheet1.xml" to """
                    <worksheet><sheetData>
                      <row r="1">
                        <c r="A1" s="2"/>
                        <c r="B1" t="s"><v>0</v></c>
                        <c r="C1" s="3"/>
                        <c r="D1" t="s"><v>1</v></c>
                      </row>
                    </sheetData></worksheet>
                """.trimIndent()
            )
        )

        val text = DocumentExtractor.extractXlsx(xlsx)
        assertTrue("Expected B1 cell value in: $text", text.contains("ЗначениеB1"))
        assertTrue("Expected D1 cell value in: $text", text.contains("ЗначениеD1"))
    }

    @Test
    fun documentExtractor_extractDocx_capsUncompressedZipPayload() {
        val payload = "<w:document><w:body><w:p><w:t>${"АБВГ".repeat(2000)}</w:t></w:p></w:body></w:document>"
        val docx = buildZip("capped.docx", mapOf("word/document.xml" to payload))

        val text = DocumentExtractor.extractDocx(docx, maxEntryBytes = 512)
        assertTrue(text.length < 600)
    }

    @Test
    fun documentExtractor_unescapeXml_rejectsInvalidCodePointsAndAvoidsDoubleUnescape() {
        // Невалидный code point и суррогат не должны ронять Character.toChars
        assertEquals("&#99999999;", DocumentExtractor.unescapeXml("&#99999999;"))
        assertEquals("&#xD800;", DocumentExtractor.unescapeXml("&#xD800;"))
        // Двойное раскодирование &amp;#65; должно дать буквальное &#65;, а не 'A'
        assertEquals("&#65;", DocumentExtractor.unescapeXml("&amp;#65;"))
        // Валидные сущности и суррогатные пары (эмодзи) работают штатно
        assertEquals("A & <B> 😀", DocumentExtractor.unescapeXml("&#65; &amp; &lt;B&gt; &#x1F600;"))
    }

    @Test
    fun documentExtractor_readTextFile_keepsValidUtf8WhenTruncatedMidCyrillicChar() {
        val textFile = tempFolder.newFile("cyrillic.txt")
        // Каждая кириллическая буква в UTF-8 занимает 2 байта: "Привет" = 12 байт.
        // Лимит 5 байт обрывает третью букву 'и' (2 байта) ровно посередине.
        textFile.writeText("Привет мир", Charsets.UTF_8)

        val decoded = DocumentExtractor.readTextFile(textFile, maxBytes = 5)
        assertEquals("Пр", decoded)
    }

    // --- TrashStore ---

    @Test
    fun trashStore_isTrashPath_matchesRootAndChildrenOnly() {
        val fakeTrash = tempFolder.newFolder(".runner-trash")
        val inside = File(fakeTrash, "items/entry1/secret.txt")
        val sibling = File(tempFolder.root, ".runner-trash-other/file.txt")

        TrashStore.rootOverrideForTest = fakeTrash
        try {
            assertTrue(TrashStore.isTrashPath(fakeTrash))
            assertTrue(TrashStore.isTrashPath(inside))
            assertFalse(TrashStore.isTrashPath(sibling))
        } finally {
            TrashStore.rootOverrideForTest = null
        }
    }

    @Test
    fun trashStore_markerLine_andMarkRestored_roundTrip() {
        val marker = TrashStore.markerLine("20260101-120000-abcdef", TrashStore.EntryKind.OVERWRITE)
        val output = "Файл обновлён.\n$marker"

        val ref = TrashStore.undoRefFromOutput(output)
        assertNotNull(ref)
        assertEquals("20260101-120000-abcdef", ref?.entryId)
        assertEquals(TrashStore.EntryKind.OVERWRITE, ref?.kind)

        val restored = TrashStore.markRestored(output, "20260101-120000-abcdef")
        assertNull(TrashStore.undoRefFromOutput(restored))
    }

    private fun buildZip(name: String, entries: Map<String, String>): File {
        val file = tempFolder.newFile(name)
        ZipOutputStream(FileOutputStream(file)).use { zip ->
            entries.forEach { (entryName, content) ->
                zip.putNextEntry(ZipEntry(entryName))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return file
    }
}
