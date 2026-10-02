package com.runner.app.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/** Сессия чата — одна строка в боковом меню. */
@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    /** Закреплённые висят в отдельной секции сверху, вне хронологии. */
    val isPinned: Boolean = false,
    /**
     * Полный контекст диалога (JSON-массив сообщений OpenAI).
     * Хранится как есть, иначе после перезапуска нельзя восстановить
     * структуру tool_calls и агент теряет связь между шагами.
     */
    val contextJson: String = ""
)

/** Сообщение сессии. role — имя MessageRole, чтобы слой БД не зависел от UI. */
@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sessionId")]
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val role: String,
    val content: String,
    val toolName: String? = null,
    val toolArgs: String? = null,
    val toolOutput: String? = null,
    val isError: Boolean = false,
    val isDeclined: Boolean = false,
    val createdAt: Long,
    /** Полное время ответа агента в миллисекундах. */
    val durationMs: Long? = null,
    /** Время работы конкретного инструмента. */
    val toolDurationMs: Long? = null,
    /** Текст размышлений thinking-модели, показывается под катом. */
    val reasoningText: String? = null,
    val reasoningMs: Long? = null,
    val promptTokens: Int? = null,
    val completionTokens: Int? = null,
    val tokensPerSecond: Double? = null
)

@Dao
interface ChatDao {

    @Query("""
        SELECT s.* FROM sessions s
        WHERE (SELECT COUNT(*) FROM messages m WHERE m.sessionId = s.id) > 0
        ORDER BY s.isPinned DESC, s.updatedAt DESC
    """)
    fun observeSessions(): Flow<List<SessionEntity>>

    @Query("""
        SELECT s.* FROM sessions s
        WHERE (SELECT COUNT(*) FROM messages m WHERE m.sessionId = s.id) > 0
          AND s.title LIKE '%' || :query || '%'
        ORDER BY s.isPinned DESC, s.updatedAt DESC
    """)
    fun searchSessions(query: String): Flow<List<SessionEntity>>

    @Query("""
        SELECT s.* FROM sessions s
        WHERE (SELECT COUNT(*) FROM messages m WHERE m.sessionId = s.id) = 0
        ORDER BY s.updatedAt DESC LIMIT 1
    """)
    suspend fun getRecentEmptySession(): SessionEntity?

    @Query("SELECT * FROM messages WHERE sessionId = :sessionId ORDER BY createdAt ASC")
    suspend fun loadMessages(sessionId: String): List<MessageEntity>

    @Query("SELECT * FROM sessions WHERE id = :id LIMIT 1")
    suspend fun getSession(id: String): SessionEntity?

    @Query("UPDATE sessions SET contextJson = :context WHERE id = :id")
    suspend fun updateContext(id: String, context: String)

    // IGNORE, а не REPLACE: REPLACE удаляет и вставляет строку заново, а это
    // каскадом снесло бы все сообщения сессии.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSession(session: SessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Query("UPDATE sessions SET title = :title, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateSessionTitle(id: String, title: String, updatedAt: Long)

    @Query("UPDATE sessions SET isPinned = :pinned WHERE id = :id")
    suspend fun setSessionPinned(id: String, pinned: Boolean)

    @Query("UPDATE sessions SET updatedAt = :updatedAt WHERE id = :id")
    suspend fun touchSession(id: String, updatedAt: Long)

    @Query("DELETE FROM messages WHERE sessionId = :sessionId")
    suspend fun deleteMessagesOf(sessionId: String)

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun deleteSession(id: String)

    @Transaction
    suspend fun deleteSessionCascade(id: String) {
        deleteMessagesOf(id)
        deleteSession(id)
    }
}

@Database(
    entities = [SessionEntity::class, MessageEntity::class],
    version = 3,
    exportSchema = false
)
abstract class ChatDatabase : RoomDatabase() {

    abstract fun chatDao(): ChatDao

    companion object {
        @Volatile
        private var instance: ChatDatabase? = null

        /**
         * v1 → v2: добавлены тайминги, размышления и счётчики токенов.
         * Все колонки nullable, поэтому ALTER TABLE без DEFAULT — существующая
         * история чатов остаётся на месте.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN durationMs INTEGER")
                db.execSQL("ALTER TABLE messages ADD COLUMN toolDurationMs INTEGER")
                db.execSQL("ALTER TABLE messages ADD COLUMN reasoningText TEXT")
                db.execSQL("ALTER TABLE messages ADD COLUMN reasoningMs INTEGER")
                db.execSQL("ALTER TABLE messages ADD COLUMN promptTokens INTEGER")
                db.execSQL("ALTER TABLE messages ADD COLUMN completionTokens INTEGER")
                db.execSQL("ALTER TABLE messages ADD COLUMN tokensPerSecond REAL")
            }
        }

        /**
         * v2 → v3: закрепление чатов. Колонка NOT NULL с дефолтом —
         * существующие сессии становятся незакреплёнными.
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sessions ADD COLUMN isPinned INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun get(context: Context): ChatDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ChatDatabase::class.java,
                    "runner_chat.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { instance = it }
            }
    }
}
