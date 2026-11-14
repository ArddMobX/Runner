package com.runner.app.data

import com.runner.app.data.db.ChatDao
import com.runner.app.data.db.CustomCommandEntity
import com.runner.app.data.db.MessageEntity
import com.runner.app.data.db.MessageMatch
import com.runner.app.data.db.SessionEntity
import kotlinx.coroutines.flow.Flow
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
        dao.insertMessage(message)
        dao.touchSession(message.sessionId, System.currentTimeMillis())
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
        // Вместе с чатом убираем его картинки из кэша: иначе они остаются
        // навсегда, хотя в истории на них уже никто не ссылается. Удаляем
        // только файлы из собственного каталога chat_images.
        dao.loadMessages(sessionId).forEach { message ->
            message.imageUris
                ?.split(',')
                ?.map { it.trim() }
                ?.filter { it.contains("/chat_images/") }
                ?.forEach { path -> runCatching { File(path).delete() } }
            attachmentPaths(message.attachmentsJson)
                .filter { it.contains("/chat_files/") }
                .forEach { path -> runCatching { File(path).delete() } }
        }
        dao.deleteSessionCascade(sessionId)
    }

    /** Убирает всё, что появилось в сессии после указанного момента. */
    suspend fun deleteMessagesAfter(sessionId: String, timestamp: Long) {
        dao.deleteMessagesAfter(sessionId, timestamp)
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
     * Картинки из chat_images копируются на диск: иначе удаление одной ветки
     * снесло бы файлы, на которые ссылается вторая.
     */
    suspend fun forkSession(sessionId: String): SessionEntity? {
        val original = dao.getSession(sessionId) ?: return null
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
        dao.insertSession(forked)
        try {
            dao.loadMessages(sessionId).forEach { message ->
                dao.insertMessage(
                    message.copy(
                        id = UUID.randomUUID().toString(),
                        sessionId = forked.id,
                        imageUris = forkImageUris(message.imageUris),
                        attachmentsJson = forkAttachmentsJson(message.attachmentsJson)
                    )
                )
            }
        } catch (e: Exception) {
            // Не оставляем в списке чатов пустую или наполовину скопированную ветку.
            dao.deleteSessionCascade(forked.id)
            throw e
        }
        return forked
    }

    private fun forkImageUris(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val paths = raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (paths.isEmpty()) return null
        return paths.map { path -> forkFile(path, "/chat_images/") ?: path }.joinToString(",")
    }

    /**
     * Вложения лежат JSON-массивом. Копируем сами файлы и подменяем пути,
     * иначе удаление одной ветки снесло бы файлы второй.
     */
    private fun forkAttachmentsJson(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        return ATTACHMENT_PATH_REGEX.replace(raw) { match ->
            val path = match.groupValues[1]
            val copied = forkFile(path, "/chat_files/") ?: return@replace match.value
            "\"path\":\"$copied\""
        }
    }

    private fun attachmentPaths(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return ATTACHMENT_PATH_REGEX.findAll(raw).map { it.groupValues[1] }.toList()
    }

    private fun forkFile(path: String, marker: String): String? {
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
            source.copyTo(copy, overwrite = false)
            copy.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        const val NEW_CHAT_TITLE = "Новый чат"
        const val MAX_TITLE_LENGTH = 60

        /** Путь внутри JSON-вложения. Формат контролируем сами. */
        private val ATTACHMENT_PATH_REGEX = Regex("\"path\"\\s*:\\s*\"([^\"]+)\"")

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
