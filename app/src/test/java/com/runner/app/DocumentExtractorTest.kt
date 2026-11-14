package com.runner.app

import com.runner.app.util.DocumentExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DocumentExtractorTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun zipWith(entries: Map<String, String>): File {
        val file = temp.newFile()
        ZipOutputStream(file.outputStream()).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return file
    }

    @Test
    fun docxTextKeepsParagraphs() {
        val file = zipWith(
            mapOf(
                "word/document.xml" to """
                    <w:document><w:body>
                    <w:p><w:r><w:t>Привет</w:t></w:r><w:r><w:t> мир</w:t></w:r></w:p>
                    <w:p><w:r><w:t>Второй абзац</w:t></w:r></w:p>
                    </w:body></w:document>
                """.trimIndent()
            )
        )

        val text = DocumentExtractor.extractDocx(file)
        assertTrue(text.contains("Привет мир"))
        assertTrue(text.contains("Второй абзац"))
    }

    @Test
    fun xlsxSharedStringsBecomeRows() {
        val file = zipWith(
            mapOf(
                "xl/sharedStrings.xml" to """
                    <sst><si><t>Имя</t></si><si><t>Значение</t></si></sst>
                """.trimIndent(),
                "xl/worksheets/sheet1.xml" to """
                    <worksheet><sheetData>
                    <row><c t="s"><v>0</v></c><c t="s"><v>1</v></c></row>
                    <row><c t="inlineStr"><is><t>Кот</t></is></c><c><v>42</v></c></row>
                    </sheetData></worksheet>
                """.trimIndent()
            )
        )

        val text = DocumentExtractor.extractXlsx(file)
        assertTrue(text.contains("Имя | Значение"))
        assertTrue(text.contains("Кот | 42"))
    }

    @Test
    fun pptxTextIsGroupedBySlide() {
        val file = zipWith(
            mapOf(
                "ppt/slides/slide1.xml" to "<p:sld><a:t>Первый</a:t><a:t>слайд</a:t></p:sld>",
                "ppt/slides/slide2.xml" to "<p:sld><a:t>Второй</a:t></p:sld>"
            )
        )

        val text = DocumentExtractor.extractPptx(file)
        assertTrue(text.contains("Слайд 1"))
        assertTrue(text.contains("Первый слайд"))
        assertTrue(text.contains("Слайд 2"))
    }

    @Test
    fun odtParagraphsBecomeLines() {
        val file = zipWith(
            mapOf(
                "content.xml" to "<office:text><text:p>Первый</text:p><text:p>Второй</text:p></office:text>"
            )
        )

        val text = DocumentExtractor.extractOdt(file)
        assertTrue(text.contains("Первый"))
        assertTrue(text.contains("Второй"))
    }

    @Test
    fun extensionIsTakenFromNameCaseInsensitive() {
        assertEquals("pdf", DocumentExtractor.extensionOf("Report.PDF"))
        assertEquals("kt", DocumentExtractor.extensionOf("Main.kt"))
    }

    @Test
    fun extensionFallsBackToMime() {
        assertEquals(
            "docx",
            DocumentExtractor.extensionOf(
                "document",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            )
        )
    }

    @Test
    fun supportedFormatsAreRecognized() {
        listOf("report.pdf", "notes.docx", "table.xlsx", "slides.pptx", "Main.kt", "data.json")
            .forEach { assertTrue(it, DocumentExtractor.isSupported(it, "")) }
    }

    @Test
    fun archivesAndBinariesAreRejected() {
        listOf("backup.zip", "app.apk", "photo.jpg", "video.mp4")
            .forEach { assertFalse(it, DocumentExtractor.isSupported(it, "")) }
    }

    @Test
    fun sizeIsHumanReadable() {
        assertEquals("512 Б", DocumentExtractor.formatSize(512))
        assertEquals("2 КБ", DocumentExtractor.formatSize(2048))
        assertEquals("1.5 МБ", DocumentExtractor.formatSize(1024L * 1024L + 512L * 1024L))
    }
}
