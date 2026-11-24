package com.runner.app.data

import com.runner.app.data.db.ChatDao
import com.runner.app.data.db.CustomCommandEntity
import com.runner.app.data.db.MessageEntity
import com.runner.app.data.db.MessageMatch
import com.runner.app.data.db.SessionEntity
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.util.UUID

/**
 * Репозиторий истории чатов. Всё, что знает ViewModel о хранении, — этот класс.
 */
class ChatRepository(private val dao: ChatDao) {

    fun observeSessions(query: String = ""): Flow<List<SessionEntity>> =
        if (query.isBlank()) dao.observeSessions() else dao.searchSessionsFullText(escapeLike(query.trim()))

    /** Совпадения внутри сообщений — для сниппетов в списке чатов. */
    suspend fun findMessageMatches(query: String): List<MessageMatch> =
        if (query.isBlank()) emptyList() else dao.findMessageMatches(escapeLike(query.trim()))

    suspend fun loadMessages(sessionId: String): List<MessageEntity> = dao.loadMessages(sessionId)

    suspend fun getSession(sessionId: String): SessionEntity? = dao.getSession(sessionId)

    suspend fun updateContext(sessionId: String, contextJson: String) {
        dao.updateContext(sessionId, contextJson)
    }

    suspend fun getRecentEmptySession(): SessionEntity? = dao.getRecentEmptySession()

    suspend fun createSession(title: String = NEW_CHAT_TITLE): SessionEntity {
        val now = System.currentTimeMillis()
        val session = SessionEntity(
            id = UUID.randomUUID().toString(),
            title = title,
            createdAt = now,
            updatedAt = now
        )
        dao.insertSession(session)
        return session
    }

    suspend fun saveMessage(message: MessageEntity) {
        dao.saveMessageAndTouch(message, System.currentTimeMillis())
    }

    suspend fun renameSession(sessionId: String, title: String) {
        dao.updateSessionTitle(sessionId, title.take(MAX_TITLE_LENGTH), System.currentTimeMillis())
    }

    suspend fun setSessionPinned(sessionId: String, pinned: Boolean) {
        dao.setSessionPinned(sessionId, pinned)
    }

    suspend fun updateSessionModel(sessionId: String, model: String) {
        dao.updateLastModel(sessionId, model)
    }

    suspend fun updateCompactSummary(sessionId: String, summary: String?) {
        dao.updateCompactSummary(sessionId, summary)
    }

    suspend fun deleteSession(sessionId: String) {
        // Сначала атомарно удаляем сессию и сообщения в БД, потом чистим файлы кэша:
        // если транзакция не прошла, живые файлы не пострадают.
        val removed = dao.deleteSessionReturningMessages(sessionId)
        if (removed.isNotEmpty()) {
            cleanupMessageFiles(removed)
        }
    }

    /** Убирает всё, что появилось в сессии после указанного момента, и чистит кэш удалённых вложений. */
    suspend fun deleteMessagesAfter(sessionId: String, timestamp: Long) {
        val removed = dao.deleteMessagesAfterReturning(sessionId, timestamp)
        if (removed.isNotEmpty()) {
            cleanupMessageFiles(removed)
        }
    }

    /**
     * Все пути в `chat_images` и `chat_files`, на которые ссылается хотя бы одно
     * сохранённое сообщение в БД. Используется при автоочистке кэша.
     */
    suspend fun loadReferencedCachePaths(): Set<String> {
        val paths = LinkedHashSet<String>()
        dao.loadAllImageUris().forEach { raw ->
            decodeImageUris(raw)
                .filter { it.contains("/chat_images/") }
                .forEach { paths.add(it) }
        }
        dao.loadAllAttachmentsJson().forEach { raw ->
            parseAttachmentPaths(raw)
                .filter { it.contains("/chat_files/") }
                .forEach { paths.add(it) }
        }
        return paths
    }

    // --- Свои команды ---

    fun observeCustomCommands(): Flow<List<CustomCommandEntity>> =
        dao.observeCustomCommands()

    suspend fun getCustomCommand(id: String): CustomCommandEntity? =
        dao.getCustomCommand(id)

    suspend fun customCommandNameTaken(name: String, excludeId: String? = null): Boolean =
        dao.countCustomCommands(name, excludeId.orEmpty()) > 0

    suspend fun saveCustomCommand(command: CustomCommandEntity) {
        dao.upsertCustomCommand(command)
    }

    suspend fun deleteCustomCommand(id: String) {
        dao.deleteCustomCommand(id)
    }

    /**
     * Копия чата для /fork: сообщения, контекст и модель.
     *
     * Картинки из chat_images и вложения из chat_files копируются на диск:
     * иначе удаление одной ветки снесло бы файлы, на которые ссылается вторая.
     * Запись в БД выполняется одной транзакцией; при ошибке или отмене корутины
     * уже скопированные файлы гарантированно удаляются под [NonCancellable].
     */
    suspend fun forkSession(sessionId: String): SessionEntity? {
        val original = dao.getSession(sessionId) ?: return null
        val originalMessages = dao.loadMessages(sessionId)
        val now = System.currentTimeMillis()
        val prefix = "Ветка: "
        val originalTitle = original.title.ifBlank { NEW_CHAT_TITLE }
        val title = prefix + originalTitle.take(
            (MAX_TITLE_LENGTH - prefix.length).coerceAtLeast(1)
        )
        val forked = SessionEntity(
            id = UUID.randomUUID().toString(),
            title = title,
            createdAt = now,
            updatedAt = now,
            isPinned = false,
            contextJson = original.contextJson,
            lastModel = original.lastModel,
            compactSummary = original.compactSummary
        )
        val copiedFiles = mutableListOf<File>()
        try {
            val forkedMessages = originalMessages.map { message ->
                message.copy(
                    id = UUID.randomUUID().toString(),
                    sessionId = forked.id,
                    imageUris = forkImageUris(message.imageUris, copiedFiles),
                    attachmentsJson = forkAttachmentsJson(message.attachmentsJson, copiedFiles)
                )
            }
            dao.insertSessionWithMessages(forked, forkedMessages)
        } catch (e: Throwable) {
            withContext(NonCancellable) {
                copiedFiles.forEach { file -> runCatching { file.delete() } }
            }
            throw e
        }
        return forked
    }

    private suspend fun cleanupMessageFiles(messages: List<MessageEntity>) {
        withContext(NonCancellable) {
            val candidatePaths = LinkedHashSet<String>()
            messages.forEach { message ->
                decodeImageUris(message.imageUris)
                    .filter { it.contains("/chat_images/") }
                    .forEach { candidatePaths.add(it) }
                parseAttachmentPaths(message.attachmentsJson)
                    .filter { it.contains("/chat_files/") }
                    .forEach { candidatePaths.add(it) }
            }
            if (candidatePaths.isEmpty()) return@withContext
            val stillReferenced = runCatching { loadReferencedCachePaths() }.getOrNull() ?: return@withContext
            val referencedCanonical = stillReferenced.mapTo(HashSet(stillReferenced.size)) { canonicalOrSelf(it) }
            candidatePaths.forEach { path ->
                if (path !in stillReferenced && canonicalOrSelf(path) !in referencedCanonical) {
                    runCatching { File(path).delete() }
                }
            }
        }
    }

    private fun forkImageUris(raw: String?, copiedFiles: MutableList<File>): String? {
        val paths = decodeImageUris(raw)
        if (paths.isEmpty()) return null
        val forked = paths.map { path -> forkFile(path, "/chat_images/", copiedFiles) ?: path }
        return encodeImageUris(forked)
    }

    /**
     * Вложения лежат JSON-массивом. Разбираем через [JSONArray] (а не regex),
     * чтобы корректно декодировать экранированные `\/` из Android org.json и
     * безопасно сериализовать обновлённые пути обратно в JSON.
     */
    private fun forkAttachmentsJson(raw: String?, copiedFiles: MutableList<File>): String? {
        if (raw.isNullOrBlank()) return null
        return try {
            val array = JSONArray(raw)
            var changed = false
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val path = (item.opt("path") as? String)?.trim().orEmpty()
                if (path.isEmpty()) continue
                val copied = forkFile(path, "/chat_files/", copiedFiles)
                if (copied != null) {
                    item.put("path", copied)
                    changed = true
                }
            }
            if (changed) array.toString() else raw
        } catch (_: Exception) {
            raw
        }
    }

    private fun forkFile(path: String, marker: String, copiedFiles: MutableList<File>): String? {
        if (!path.contains(marker)) return null
        val source = File(path)
        if (!source.isFile) return null
        return try {
            val parent = source.parentFile ?: return null
            val extension = source.extension
                .takeIf { it.isNotBlank() }
                ?.let { ".$it" }
                .orEmpty()
            val copy = File(
                parent,
                "${source.nameWithoutExtension}_fork_${UUID.randomUUID().toString().take(8)}$extension"
            )
            try {
                source.copyTo(copy, overwrite = false)
            } catch (e: Exception) {
                runCatching { copy.delete() }
                throw e
            }
            copiedFiles.add(copy)
            copy.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    private fun canonicalOrSelf(path: String): String =
        runCatching { File(path).canonicalPath }.getOrDefault(path)

    companion object {
        const val NEW_CHAT_TITLE = "Новый чат"
        const val MAX_TITLE_LENGTH = 60

        /**
         * Извлекает пути из JSON-массива вложений.
         * Использует [JSONArray], чтобы корректно снимать экранирование `\/` и `\"`.
         */
        fun parseAttachmentPaths(raw: String?): List<String> {
            if (raw.isNullOrBlank()) return emptyList()
            return try {
                val array = JSONArray(raw)
                buildList {
                    for (index in 0 until array.length()) {
                        val item = array.optJSONObject(index) ?: continue
                        val path = (item.opt("path") as? String)?.trim().orEmpty()
                        if (path.isNotEmpty()) add(path)
                    }
                }
            } catch (_: Exception) {
                emptyList()
            }
        }

        /**
         * Кодирует список путей изображений в JSON-массив строк (например `["/a,b.jpg"]`).
         * Сохраняет пути с запятыми и спецсимволами без порчи разделителем.
         */
        fun encodeImageUris(paths: List<String>): String? {
            val clean = paths.map { it.trim() }.filter { it.isNotEmpty() }
            if (clean.isEmpty()) return null
            return buildString {
                append('[')
                clean.forEachIndexed { index, path ->
                    if (index > 0) append(',')
                    append('"')
                    appendJsonEscaped(path)
                    append('"')
                }
                append(']')
            }
        }

        /**
         * Декодирует список путей изображений из БД.
         * Поддерживает JSON-массив строк и обратную совместимость с legacy-форматом через запятую.
         */
        fun decodeImageUris(raw: String?): List<String> {
            if (raw.isNullOrBlank()) return emptyList()
            val trimmed = raw.trim()
            if (trimmed.startsWith('[')) {
                val parsed = parseJsonStringArray(trimmed)
                if (parsed != null) return parsed
            }
            return trimmed.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        }

        private fun StringBuilder.appendJsonEscaped(value: String) {
            for (ch in value) {
                when (ch) {
                    '"' -> append("\\\"")
                    '\\' -> append("\\\\")
                    '\b' -> append("\\b")
                    '\u000C' -> append("\\f")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> if (ch.code < 0x20) {
                        append("\\u")
                        append(ch.code.toString(16).padStart(4, '0'))
                    } else {
                        append(ch)
                    }
                }
            }
        }

        private fun parseJsonStringArray(json: String): List<String>? {
            var pos = 0
            val len = json.length
            fun skipWs() {
                while (pos < len && json[pos].isWhitespace()) pos++
            }
            skipWs()
            if (pos >= len || json[pos] != '[') return null
            pos++
            skipWs()
            if (pos < len && json[pos] == ']') {
                pos++
                skipWs()
                return if (pos == len) emptyList() else null
            }
            val result = mutableListOf<String>()
            while (pos < len) {
                skipWs()
                if (pos >= len || json[pos] != '"') return null
                pos++
                val sb = StringBuilder()
                var closed = false
                while (pos < len) {
                    val ch = json[pos++]
                    when (ch) {
                        '"' -> {
                            closed = true
                            break
                        }
                        '\\' -> {
                            if (pos >= len) return null
                            when (val esc = json[pos++]) {
                                '"', '\\', '/' -> sb.append(esc)
                                'b' -> sb.append('\b')
                                'f' -> sb.append('\u000C')
                                'n' -> sb.append('\n')
                                'r' -> sb.append('\r')
                                't' -> sb.append('\t')
                                'u' -> {
                                    if (pos + 4 > len) return null
                                    val hex = json.substring(pos, pos + 4)
                                    val code = hex.toIntOrNull(16) ?: return null
                                    sb.append(Char(code))
                                    pos += 4
                                }
                                else -> return null
                            }
                        }
                        else -> sb.append(ch)
                    }
                }
                if (!closed) return null
                val value = sb.toString().trim()
                if (value.isNotEmpty()) result.add(value)
                skipWs()
                if (pos >= len) return null
                when (json[pos]) {
                    ',' -> pos++
                    ']' -> {
                        pos++
                        skipWs()
                        return if (pos == len) result else null
                    }
                    else -> return null
                }
            }
            return null
        }

        /**
         * Экранирует спецсимволы LIKE. Запросы в ChatDao объявлены с
         * `ESCAPE '\'`, поэтому символы `%` и `_` из поисковой строки должны
         * приходить уже с обратным слешем. Порядок замен важен: сначала
         * экранируется сам обратный слеш, иначе он заэкранирует экранирующие.
         */
        fun escapeLike(raw: String): String = raw
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")
    }
}
