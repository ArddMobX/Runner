package com.runner.app.data

import com.runner.app.data.db.ChatDao
import com.runner.app.data.db.MessageEntity
import com.runner.app.data.db.SessionEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * Репозиторий истории чатов. Всё, что знает ViewModel о хранении, — этот класс.
 */
class ChatRepository(private val dao: ChatDao) {

    fun observeSessions(query: String = ""): Flow<List<SessionEntity>> =
        if (query.isBlank()) dao.observeSessions() else dao.searchSessions(query.trim())

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

    suspend fun deleteSession(sessionId: String) {
        dao.deleteSessionCascade(sessionId)
    }

    /** Убирает всё, что появилось в сессии после указанного момента. */
    suspend fun deleteMessagesAfter(sessionId: String, timestamp: Long) {
        dao.deleteMessagesAfter(sessionId, timestamp)
    }

    companion object {
        const val NEW_CHAT_TITLE = "Новый чат"
        const val MAX_TITLE_LENGTH = 60
    }
}
