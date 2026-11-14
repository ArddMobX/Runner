package com.runner.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.Base64
import com.runner.app.data.ChatAttachment
import com.runner.app.data.SelectedFile
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.jsoup.Jsoup
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.Collections
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipFile

/**
 * Читает прикреплённые документы и превращает их в текст для модели.
 *
 * Поддерживаются PDF, DOCX, XLSX, PPTX, ODT, HTML и обычный текст с кодом.
 * Для PDF сначала пробуем текстовый слой, а если его нет (скан), отдаём
 * первые страницы картинками: модель увидит документ через Vision.
 */
object DocumentExtractor {

    const val MAX_FILE_BYTES: Long = 25L * 1024 * 1024
    const val MAX_TEXT_CHARS: Int = 40_000

    private const val MAX_TEXT_READ_BYTES = 4 * 1024 * 1024
    private const val MAX_PDF_PAGES = 80
    private const val MAX_PDF_IMAGE_PAGES = 6
    private const val MAX_CACHED_FILE_BYTES: Long = 30L * 1024 * 1024

    private val TEXT_EXTENSIONS = setOf(
        "txt", "text", "md", "markdown", "csv", "tsv", "json", "jsonl",
        "xml", "yaml", "yml", "html", "htm", "css", "js", "jsx", "ts", "tsx",
        "kt", "kts", "java", "py", "rb", "php", "c", "h", "cpp", "hpp",
        "cs", "go", "rs", "swift", "sh", "bash", "zsh", "bat", "ps1",
        "sql", "gradle", "properties", "ini", "cfg", "conf", "log", "toml"
    )

    private val OFFICE_EXTENSIONS = setOf("pdf", "docx", "xlsx", "pptx", "odt")

    val SUPPORTED_EXTENSIONS: Set<String> = TEXT_EXTENSIONS + OFFICE_EXTENSIONS

    private val MIME_EXTENSIONS = mapOf(
        "application/pdf" to "pdf",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document" to "docx",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" to "xlsx",
        "application/vnd.openxmlformats-officedocument.presentationml.presentation" to "pptx",
        "application/vnd.oasis.opendocument.text" to "odt",
        "application/json" to "json",
        "application/xml" to "xml",
        "text/html" to "html",
        "text/markdown" to "md",
        "text/csv" to "csv"
    )

    @Volatile
    private var pdfBoxReady = false

    data class Extraction(
        val text: String,
        val pageImages: List<String> = emptyList(),
        val note: String? = null
    )

    data class PreparedAttachment(
        val attachment: ChatAttachment,
        val text: String = "",
        val pageImages: List<String> = emptyList(),
        val note: String? = null
    )

    fun extensionOf(name: String, mime: String = ""): String {
        val fromName = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        if (fromName.isNotBlank()) return fromName
        return MIME_EXTENSIONS[mime.lowercase(Locale.ROOT)].orEmpty()
    }

    fun isSupported(name: String, mime: String): Boolean {
        val ext = extensionOf(name, mime)
        return ext in SUPPORTED_EXTENSIONS || mime.startsWith("text/")
    }

    fun readMeta(context: Context, uri: Uri): SelectedFile? {
        var name = ""
        var size = -1L
        try {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIndex >= 0) name = cursor.getString(nameIndex).orEmpty()
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
                }
            }
        } catch (_: Exception) {
            // Ниже попробуем хотя бы имя из URI.
        }
        if (name.isBlank()) {
            name = uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: "файл"
        }
        val mime = try {
            context.contentResolver.getType(uri).orEmpty()
        } catch (_: Exception) {
            ""
        }
        return SelectedFile(uri = uri, name = name, mime = mime, sizeBytes = size.coerceAtLeast(0L))
    }

    fun prepare(context: Context, selected: SelectedFile): PreparedAttachment {
        val mime = selected.mime.ifBlank { mimeFromExtension(selected.name) }
        if (selected.sizeBytes > MAX_FILE_BYTES) {
            return PreparedAttachment(
                attachment = ChatAttachment(
                    path = selected.uri.toString(),
                    name = selected.name,
                    mime = mime,
                    sizeBytes = selected.sizeBytes
                ),
                note = "Файл больше 25 МБ, поэтому содержимое не читалось."
            )
        }
        if (!isSupported(selected.name, mime)) {
            return PreparedAttachment(
                attachment = ChatAttachment(
                    path = selected.uri.toString(),
                    name = selected.name,
                    mime = mime,
                    sizeBytes = selected.sizeBytes
                ),
                note = "Формат .${extensionOf(selected.name, mime)} не поддерживается."
            )
        }

        val cachedPath = copyToCache(context, selected.uri, selected.name)
            ?: return PreparedAttachment(
                attachment = ChatAttachment(
                    path = selected.uri.toString(),
                    name = selected.name,
                    mime = mime,
                    sizeBytes = selected.sizeBytes
                ),
                note = "Не удалось скопировать файл."
            )

        val cachedFile = File(cachedPath)
        val cachedLength = cachedFile.length()
        if (cachedLength > MAX_FILE_BYTES) {
            runCatching { cachedFile.delete() }
            return PreparedAttachment(
                attachment = ChatAttachment(
                    path = selected.uri.toString(),
                    name = selected.name,
                    mime = mime,
                    sizeBytes = cachedLength
                ),
                note = "Файл больше 25 МБ, поэтому содержимое не читалось."
            )
        }
        val attachment = ChatAttachment(
            path = cachedPath,
            name = selected.name,
            mime = mime,
            sizeBytes = cachedFile.length().takeIf { it > 0 } ?: selected.sizeBytes
        )
        val extraction = extractFile(context, cachedFile, selected.name, mime)
        return PreparedAttachment(
            attachment = attachment,
            text = extraction.text,
            pageImages = extraction.pageImages,
            note = extraction.note
        )
    }

    fun prepareStored(context: Context, attachment: ChatAttachment): PreparedAttachment {
        if (attachment.sizeBytes > MAX_FILE_BYTES) {
            return PreparedAttachment(
                attachment = attachment,
                note = "Файл больше 25 МБ, поэтому содержимое не читалось."
            )
        }
        if (!isSupported(attachment.name, attachment.mime)) {
            return PreparedAttachment(
                attachment = attachment,
                note = "Формат .${extensionOf(attachment.name, attachment.mime)} не поддерживается."
            )
        }
        val file = File(attachment.path)
        if (!file.isFile) {
            return PreparedAttachment(
                attachment = attachment,
                note = "Файл вложения больше недоступен."
            )
        }
        val extraction = extractFile(context, file, attachment.name, attachment.mime)
        return PreparedAttachment(
            attachment = attachment,
            text = extraction.text,
            pageImages = extraction.pageImages,
            note = extraction.note
        )
    }

    fun copyToCache(context: Context, uri: Uri, name: String): String? {
        return try {
            val dir = File(context.cacheDir, "chat_files").apply { mkdirs() }
            val safeName = sanitizeFileName(name).ifBlank { "file" }
            val target = File(dir, "${UUID.randomUUID().toString().take(8)}_$safeName")
            var tooBig = false
            val copied = context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(16 * 1024)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        total += read
                        if (total > MAX_CACHED_FILE_BYTES) {
                            tooBig = true
                            break
                        }
                        output.write(buffer, 0, read)
                    }
                    total
                }
            }
            if (!tooBig && copied != null && target.isFile && target.length() in 1..MAX_CACHED_FILE_BYTES) {
                target.absolutePath
            } else {
                runCatching { target.delete() }
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun formatSize(bytes: Long): String {
        if (bytes <= 0L) return "размер неизвестен"
        return when {
            bytes >= 1024L * 1024L -> String.format(Locale.ROOT, "%.1f МБ", bytes / (1024.0 * 1024.0))
            bytes >= 1024L -> String.format(Locale.ROOT, "%.0f КБ", bytes / 1024.0)
            else -> "$bytes Б"
        }
    }

    private fun extractFile(
        context: Context,
        file: File,
        name: String,
        mime: String
    ): Extraction {
        val ext = extensionOf(name, mime)
        return try {
            when {
                ext == "pdf" -> extractPdf(context, file)
                ext == "docx" -> fromText(extractDocx(file))
                ext == "xlsx" -> fromText(extractXlsx(file))
                ext == "pptx" -> fromText(extractPptx(file))
                ext == "odt" -> fromText(extractOdt(file))
                ext == "html" || ext == "htm" -> fromText(extractHtml(file))
                ext in TEXT_EXTENSIONS || mime.startsWith("text/") -> fromText(readTextFile(file))
                else -> Extraction(
                    text = "",
                    note = "Формат .$ext не поддерживается."
                )
            }
        } catch (e: Exception) {
            Extraction(
                text = "",
                pageImages = emptyList(),
                note = "Не удалось прочитать файл: ${e.message ?: e.javaClass.simpleName}"
            )
        }
    }

    private fun fromText(raw: String): Extraction {
        val normalized = normalizeText(raw)
        if (normalized.isBlank()) {
            return Extraction(text = "", note = "В файле не нашлось текста.")
        }
        val clipped = normalized.take(MAX_TEXT_CHARS)
        val note = if (normalized.length > MAX_TEXT_CHARS) {
            "Показаны первые ${MAX_TEXT_CHARS} символов из ${normalized.length}."
        } else {
            null
        }
        return Extraction(text = clipped, note = note)
    }

    private fun readTextFile(file: File): String {
        val bytes = readBytesCapped(file, MAX_TEXT_READ_BYTES)
        if (bytes.isEmpty()) return ""
        val text = decodeText(bytes)
        return text.removePrefix("\uFEFF")
    }

    private fun readBytesCapped(file: File, maxBytes: Int): ByteArray {
        val out = ByteArrayOutputStream()
        file.inputStream().use { input ->
            val buffer = ByteArray(16 * 1024)
            var total = 0
            while (total < maxBytes) {
                val read = input.read(buffer)
                if (read <= 0) break
                val allowed = minOf(read, maxBytes - total)
                out.write(buffer, 0, allowed)
                total += allowed
            }
        }
        return out.toByteArray()
    }

    private fun decodeText(bytes: ByteArray): String {
        return try {
            val decoder = Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            decoder.decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: Exception) {
            String(bytes, charset("windows-1251"))
        }
    }

    private fun extractPdf(context: Context, file: File): Extraction {
        ensurePdfBox(context)
        val text = try {
            PDDocument.load(file).use { document ->
                val stripper = PDFTextStripper().apply {
                    sortByPosition = true
                    startPage = 1
                    endPage = minOf(document.numberOfPages, MAX_PDF_PAGES)
                }
                normalizeText(stripper.getText(document))
            }
        } catch (_: Exception) {
            ""
        }
        if (text.length >= 40) {
            val clipped = text.take(MAX_TEXT_CHARS)
            val note = buildString {
                if (text.length > MAX_TEXT_CHARS) {
                    append("Показаны первые $MAX_TEXT_CHARS символов. ")
                }
                append("PDF прочитан как текст.")
            }
            return Extraction(text = clipped, note = note)
        }

        val pages = renderPdfPages(file)
        return if (pages.isNotEmpty()) {
            Extraction(
                text = "",
                pageImages = pages,
                note = "PDF без текстового слоя: первые ${pages.size} страниц приложены как изображения."
            )
        } else {
            Extraction(text = "", note = "PDF пустой или защищён паролем.")
        }
    }

    private fun ensurePdfBox(context: Context) {
        if (pdfBoxReady) return
        synchronized(this) {
            if (pdfBoxReady) return
            PDFBoxResourceLoader.init(context.applicationContext)
            pdfBoxReady = true
        }
    }

    private fun renderPdfPages(file: File): List<String> {
        val result = mutableListOf<String>()
        var descriptor: ParcelFileDescriptor? = null
        try {
            descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            PdfRenderer(descriptor).use { renderer ->
                val count = minOf(renderer.pageCount, MAX_PDF_IMAGE_PAGES)
                for (index in 0 until count) {
                    val page = renderer.openPage(index)
                    try {
                        val scale = minOf(
                            1.6f,
                            1400f / maxOf(page.width, page.height).coerceAtLeast(1)
                        ).coerceAtLeast(0.6f)
                        val width = (page.width * scale).toInt().coerceAtLeast(1)
                        val height = (page.height * scale).toInt().coerceAtLeast(1)
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        val stream = ByteArrayOutputStream()
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 72, stream)
                        bitmap.recycle()
                        result += "data:image/jpeg;base64," +
                                Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
                    } finally {
                        page.close()
                    }
                }
            }
        } catch (_: Exception) {
            // Вернём то, что успели отрисовать.
        } finally {
            runCatching { descriptor?.close() }
        }
        return result
    }

    internal fun extractDocx(file: File): String {
        ZipFile(file).use { zip ->
            val entry = zip.getEntry("word/document.xml") ?: return ""
            val xml = zip.getInputStream(entry).bufferedReader().use { it.readText() }
            return xml
                .replace(Regex("</w:p>"), "\n")
                .replace(Regex("<w:tab[^>]*/>"), "\t")
                .replace(Regex("<w:br[^>]*/>"), "\n")
                .replace(Regex("<[^>]+>"), "")
                .let(::unescapeXml)
        }
    }

    internal fun extractOdt(file: File): String {
        ZipFile(file).use { zip ->
            val entry = zip.getEntry("content.xml") ?: return ""
            val xml = zip.getInputStream(entry).bufferedReader().use { it.readText() }
            return xml
                .replace(Regex("</text:p>|</text:h>"), "\n")
                .replace(Regex("<text:tab[^>]*/>"), "\t")
                .replace(Regex("<[^>]+>"), "")
                .let(::unescapeXml)
        }
    }

    internal fun extractPptx(file: File): String {
        ZipFile(file).use { zip ->
            val slides = Collections.list(zip.entries())
                .filter { it.name.matches(Regex("""ppt/slides/slide\d+\.xml""")) }
                .sortedBy { it.name.filter { ch -> ch.isDigit() }.toIntOrNull() ?: 0 }
                .take(40)
            if (slides.isEmpty()) return ""
            return buildString {
                slides.forEachIndexed { index, entry ->
                    val xml = zip.getInputStream(entry).bufferedReader().use { it.readText() }
                    val texts = Regex("""<a:t>([\s\S]*?)</a:t>""").findAll(xml)
                        .map { unescapeXml(it.groupValues[1]).trim() }
                        .filter { it.isNotEmpty() }
                        .toList()
                    if (texts.isNotEmpty()) {
                        append("\nСлайд ${index + 1}\n")
                        append(texts.joinToString(" "))
                        append('\n')
                    }
                }
            }
        }
    }

    internal fun extractXlsx(file: File): String {
        ZipFile(file).use { zip ->
            val shared = mutableListOf<String>()
            zip.getEntry("xl/sharedStrings.xml")?.let { entry ->
                val xml = zip.getInputStream(entry).bufferedReader().use { it.readText() }
                Regex("""<si>([\s\S]*?)</si>""").findAll(xml).forEach { match ->
                    val text = Regex("""<t[^>]*>([\s\S]*?)</t>""")
                        .findAll(match.groupValues[1])
                        .joinToString("") { unescapeXml(it.groupValues[1]) }
                    shared += text
                }
            }

            val sheets = Collections.list(zip.entries())
                .filter { it.name.matches(Regex("""xl/worksheets/sheet\d+\.xml""")) }
                .sortedBy { it.name.filter { ch -> ch.isDigit() }.toIntOrNull() ?: 0 }
                .take(3)
            if (sheets.isEmpty()) return ""

            return buildString {
                sheets.forEachIndexed { sheetIndex, entry ->
                    val xml = zip.getInputStream(entry).bufferedReader().use { it.readText() }
                    append("\nЛист ${sheetIndex + 1}\n")
                    var rowCount = 0
                    for (row in Regex("""<row[^>]*>([\s\S]*?)</row>""").findAll(xml)) {
                        if (rowCount >= 200) break
                        val cells = mutableListOf<String>()
                        for (cell in Regex("""<c([^>]*)>([\s\S]*?)</c>""").findAll(row.groupValues[1])) {
                            if (cells.size >= 30) break
                            val attrs = cell.groupValues[1]
                            val body = cell.groupValues[2]
                            val type = Regex("t=\"([^\"]+)\"").find(attrs)?.groupValues?.get(1)
                            val raw = when (type) {
                                "s" -> Regex("""<v>([\s\S]*?)</v>""")
                                    .find(body)?.groupValues?.get(1)
                                    ?.toIntOrNull()
                                    ?.let(shared::getOrNull)
                                    .orEmpty()

                                "inlineStr" -> Regex("""<t[^>]*>([\s\S]*?)</t>""")
                                    .find(body)?.groupValues?.get(1)
                                    .orEmpty()

                                else -> Regex("""<v>([\s\S]*?)</v>""")
                                    .find(body)?.groupValues?.get(1)
                                    .orEmpty()
                            }
                            cells += unescapeXml(raw).trim()
                        }
                        if (cells.any { it.isNotBlank() }) {
                            append(cells.joinToString(" | "))
                            append('\n')
                            rowCount++
                        }
                    }
                }
            }
        }
    }

    private fun extractHtml(file: File): String {
        val document = Jsoup.parse(file, "UTF-8")
        val blocks = document.body().select("h1, h2, h3, h4, h5, h6, p, li, tr")
        return if (blocks.isNotEmpty()) {
            blocks.joinToString("\n") { it.text().trim() }.trim()
        } else {
            document.body().text()
        }
    }

    private fun normalizeText(raw: String): String = raw
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .replace(Regex("[ \\t]+\\n"), "\n")
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()

    private fun unescapeXml(raw: String): String {
        var value = raw
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&amp;", "&")
        value = Regex("""&#x([0-9a-fA-F]+);""").replace(value) { match ->
            val code = match.groupValues[1].toIntOrNull(16)
            if (code != null) String(Character.toChars(code)) else match.value
        }
        value = Regex("""&#(\d+);""").replace(value) { match ->
            val code = match.groupValues[1].toIntOrNull()
            if (code != null) String(Character.toChars(code)) else match.value
        }
        return value
    }

    private fun sanitizeFileName(name: String): String = name
        .replace(Regex("""[\\/:*?"<>|]"""), "_")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(80)

    private fun mimeFromExtension(name: String): String = when (extensionOf(name)) {
        "pdf" -> "application/pdf"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        "odt" -> "application/vnd.oasis.opendocument.text"
        "json" -> "application/json"
        "xml" -> "application/xml"
        "html", "htm" -> "text/html"
        "md", "markdown" -> "text/markdown"
        "csv" -> "text/csv"
        else -> "text/plain"
    }
}
