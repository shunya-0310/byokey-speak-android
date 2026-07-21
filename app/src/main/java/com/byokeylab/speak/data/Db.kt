package com.byokeylab.speak.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "chats")
data class ChatEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    /** ChatMode.name: Coach or Quick */
    val mode: String,
    val pinned: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "messages",
    indices = [Index("chatId")],
    foreignKeys = [
        ForeignKey(
            entity = ChatEntity::class,
            parentColumns = ["id"],
            childColumns = ["chatId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val chatId: Long,
    /** MessageRole.name */
    val role: String,
    val text: String,
    val createdAt: Long
)

@Entity(tableName = "learning_notes")
data class LearningNoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 元会話への緩い参照。チャット削除後もメモは残す。 */
    val chatId: Long? = null,
    val sourceMessage: String,
    val coachNotes: String,
    val betterOptions: String,
    val japaneseNote: String = "",
    val reviewed: Boolean = false,
    val createdAt: Long
)

@Entity(tableName = "daily_stats")
data class DailyStatEntity(
    @PrimaryKey val epochDay: Long,
    val turns: Int = 0,
    val assistUses: Int = 0,
    val notesSaved: Int = 0,
    val reviewsDone: Int = 0
)

data class ChatWithPreview(
    val id: Long,
    val title: String,
    val mode: String,
    val pinned: Boolean,
    val updatedAt: Long,
    val lastMessage: String?
)

@Dao
interface ChatDao {
    @Query(
        """
        SELECT c.id, c.title, c.mode, c.pinned, c.updatedAt,
               (SELECT m.text FROM messages m WHERE m.chatId = c.id ORDER BY m.id DESC LIMIT 1) AS lastMessage
        FROM chats c
        WHERE c.mode = :mode
        ORDER BY c.pinned DESC, c.updatedAt DESC
        """
    )
    fun observeChats(mode: String): Flow<List<ChatWithPreview>>

    @Query("SELECT * FROM chats WHERE id = :id")
    suspend fun chatById(id: Long): ChatEntity?

    @Query("SELECT * FROM chats WHERE mode = :mode ORDER BY updatedAt DESC LIMIT 1")
    suspend fun latestChat(mode: String): ChatEntity?

    @Insert
    suspend fun insert(chat: ChatEntity): Long

    @Query("UPDATE chats SET title = :title WHERE id = :id")
    suspend fun rename(id: Long, title: String)

    @Query("UPDATE chats SET pinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean)

    @Query("UPDATE chats SET updatedAt = :updatedAt WHERE id = :id")
    suspend fun touch(id: Long, updatedAt: Long)

    @Query("DELETE FROM chats WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM chats")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM chats")
    suspend fun count(): Int
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY id ASC")
    fun observeMessages(chatId: Long): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY id ASC")
    suspend fun messagesOf(chatId: Long): List<MessageEntity>

    @Query("SELECT COUNT(*) FROM messages WHERE chatId = :chatId AND role = 'User'")
    suspend fun userMessageCount(chatId: Long): Int

    @Insert
    suspend fun insert(message: MessageEntity): Long

    @Query("DELETE FROM messages WHERE chatId = :chatId")
    suspend fun deleteByChat(chatId: Long)

    @Query("DELETE FROM messages")
    suspend fun deleteAll()
}

@Dao
interface LearningNoteDao {
    @Query("SELECT * FROM learning_notes ORDER BY id DESC")
    fun observeNotes(): Flow<List<LearningNoteEntity>>

    @Query("SELECT COUNT(*) FROM learning_notes")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM learning_notes WHERE reviewed = 1")
    fun observeReviewedCount(): Flow<Int>

    @Insert
    suspend fun insert(note: LearningNoteEntity): Long

    @Query("UPDATE learning_notes SET reviewed = :reviewed WHERE id = :id")
    suspend fun setReviewed(id: Long, reviewed: Boolean)

    @Query("DELETE FROM learning_notes WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM learning_notes")
    suspend fun deleteAll()
}

@Dao
interface DailyStatDao {
    @Query("SELECT * FROM daily_stats ORDER BY epochDay DESC")
    fun observeAll(): Flow<List<DailyStatEntity>>

    @Query("SELECT * FROM daily_stats WHERE epochDay = :epochDay")
    suspend fun byDay(epochDay: Long): DailyStatEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(stat: DailyStatEntity)

    @Query("DELETE FROM daily_stats")
    suspend fun deleteAll()
}

@Database(
    entities = [
        ChatEntity::class,
        MessageEntity::class,
        LearningNoteEntity::class,
        DailyStatEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun messageDao(): MessageDao
    abstract fun learningNoteDao(): LearningNoteDao
    abstract fun dailyStatDao(): DailyStatDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "byokey_speak.db"
                ).build().also { instance = it }
            }
        }
    }
}
