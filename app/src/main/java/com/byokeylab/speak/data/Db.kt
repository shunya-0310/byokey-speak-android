package com.byokeylab.speak.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.ColumnInfo
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
    /** ChatMode.name. Existing Coach rows are migrated to Quick from DB v3. */
    val mode: String,
    /** ConversationOrigin.name */
    @ColumnInfo(defaultValue = "FREE_CHAT") val origin: String = "FREE_CHAT",
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
    /** MessageInputSource.name */
    @ColumnInfo(defaultValue = "UNKNOWN") val inputSource: String = "UNKNOWN",
    @ColumnInfo(defaultValue = "0") val usedQuickAssist: Boolean = false,
    val createdAt: Long
)

@Entity(tableName = "conversation_analyses")
data class ConversationAnalysisEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAt: Long,
    val periodStart: Long,
    val periodEnd: Long,
    val userMessageCount: Int,
    val provider: String,
    val model: String,
    val resultJson: String
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

@Entity(tableName = "vocab_cards")
data class VocabCardEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val expression: String,
    val meaning: String,
    /** VocabSource.name */
    val source: String,
    val chatId: Long? = null,
    val favorite: Boolean = false,
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
    @Query("SELECT * FROM chats ORDER BY id ASC")
    suspend fun allForBackup(): List<ChatEntity>

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

    @Query(
        """
        SELECT c.id, c.title, c.mode, c.pinned, c.updatedAt,
               (SELECT m.text FROM messages m WHERE m.chatId = c.id ORDER BY m.id DESC LIMIT 1) AS lastMessage
        FROM chats c
        ORDER BY c.pinned DESC, c.updatedAt DESC
        """
    )
    fun observeAllChats(): Flow<List<ChatWithPreview>>

    @Query("SELECT * FROM chats WHERE id = :id")
    suspend fun chatById(id: Long): ChatEntity?

    @Query("SELECT * FROM chats WHERE mode = :mode ORDER BY updatedAt DESC LIMIT 1")
    suspend fun latestChat(mode: String): ChatEntity?

    @Insert
    suspend fun insert(chat: ChatEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreAll(chats: List<ChatEntity>)

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
    @Query("SELECT * FROM messages ORDER BY id ASC")
    suspend fun allForBackup(): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY id ASC")
    fun observeMessages(chatId: Long): Flow<List<MessageEntity>>

    @Query("SELECT text FROM messages WHERE role = 'User' ORDER BY id ASC")
    fun observeAllUserMessageTexts(): Flow<List<String>>

    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY id ASC")
    suspend fun messagesOf(chatId: Long): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE createdAt >= :createdAt ORDER BY createdAt ASC, id ASC")
    suspend fun messagesSince(createdAt: Long): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE role = 'User' ORDER BY createdAt DESC, id DESC LIMIT 1")
    suspend fun latestUserMessage(): MessageEntity?

    @Query("SELECT COUNT(*) FROM messages WHERE chatId = :chatId AND role = 'User'")
    suspend fun userMessageCount(chatId: Long): Int

    @Insert
    suspend fun insert(message: MessageEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreAll(messages: List<MessageEntity>)

    @Query("DELETE FROM messages WHERE chatId = :chatId")
    suspend fun deleteByChat(chatId: Long)

    @Query("DELETE FROM messages")
    suspend fun deleteAll()
}

@Dao
interface LearningNoteDao {
    @Query("SELECT * FROM learning_notes ORDER BY id ASC")
    suspend fun allForBackup(): List<LearningNoteEntity>

    @Query("SELECT * FROM learning_notes ORDER BY id DESC")
    fun observeNotes(): Flow<List<LearningNoteEntity>>

    @Query("SELECT COUNT(*) FROM learning_notes")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM learning_notes WHERE reviewed = 1")
    fun observeReviewedCount(): Flow<Int>

    @Insert
    suspend fun insert(note: LearningNoteEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreAll(notes: List<LearningNoteEntity>)

    @Query("UPDATE learning_notes SET reviewed = :reviewed WHERE id = :id")
    suspend fun setReviewed(id: Long, reviewed: Boolean)

    @Query("DELETE FROM learning_notes WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM learning_notes")
    suspend fun deleteAll()
}

@Dao
interface VocabCardDao {
    @Query("SELECT * FROM vocab_cards ORDER BY id ASC")
    suspend fun allForBackup(): List<VocabCardEntity>

    @Query("SELECT * FROM vocab_cards ORDER BY id DESC")
    fun observeAll(): Flow<List<VocabCardEntity>>

    @Insert
    suspend fun insert(card: VocabCardEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreAll(cards: List<VocabCardEntity>)

    @Query("UPDATE vocab_cards SET favorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)

    @Query("DELETE FROM vocab_cards WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM vocab_cards")
    suspend fun deleteAll()
}

@Dao
interface DailyStatDao {
    @Query("SELECT * FROM daily_stats ORDER BY epochDay ASC")
    suspend fun allForBackup(): List<DailyStatEntity>

    @Query("SELECT * FROM daily_stats ORDER BY epochDay DESC")
    fun observeAll(): Flow<List<DailyStatEntity>>

    @Query("SELECT * FROM daily_stats WHERE epochDay = :epochDay")
    suspend fun byDay(epochDay: Long): DailyStatEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(stat: DailyStatEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreAll(stats: List<DailyStatEntity>)

    @Query("DELETE FROM daily_stats")
    suspend fun deleteAll()
}

@Dao
interface ConversationAnalysisDao {
    @Query("SELECT * FROM conversation_analyses ORDER BY id ASC")
    suspend fun allForBackup(): List<ConversationAnalysisEntity>

    @Query("SELECT * FROM conversation_analyses ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ConversationAnalysisEntity>>

    @Query("SELECT * FROM conversation_analyses ORDER BY createdAt DESC LIMIT 1")
    suspend fun latest(): ConversationAnalysisEntity?

    @Insert
    suspend fun insert(analysis: ConversationAnalysisEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreAll(analyses: List<ConversationAnalysisEntity>)

    @Query("DELETE FROM conversation_analyses")
    suspend fun deleteAll()
}

@Database(
    entities = [
        ChatEntity::class,
        MessageEntity::class,
        LearningNoteEntity::class,
        VocabCardEntity::class,
        DailyStatEntity::class,
        ConversationAnalysisEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun messageDao(): MessageDao
    abstract fun learningNoteDao(): LearningNoteDao
    abstract fun vocabCardDao(): VocabCardDao
    abstract fun dailyStatDao(): DailyStatDao
    abstract fun conversationAnalysisDao(): ConversationAnalysisDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        private val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `vocab_cards` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`expression` TEXT NOT NULL, " +
                        "`meaning` TEXT NOT NULL, " +
                        "`source` TEXT NOT NULL, " +
                        "`chatId` INTEGER, " +
                        "`favorite` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL)"
                )
            }
        }

        private val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `chats` ADD COLUMN `origin` TEXT NOT NULL DEFAULT 'FREE_CHAT'")
                db.execSQL("UPDATE `chats` SET `origin` = 'MIGRATED_COACH', `mode` = 'Quick' WHERE `mode` = 'Coach'")
                db.execSQL("ALTER TABLE `messages` ADD COLUMN `inputSource` TEXT NOT NULL DEFAULT 'UNKNOWN'")
                db.execSQL("ALTER TABLE `messages` ADD COLUMN `usedQuickAssist` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE `messages` SET `inputSource` = 'NONE' WHERE `role` != 'User'")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `conversation_analyses` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "`periodStart` INTEGER NOT NULL, " +
                        "`periodEnd` INTEGER NOT NULL, " +
                        "`userMessageCount` INTEGER NOT NULL, " +
                        "`provider` TEXT NOT NULL, " +
                        "`model` TEXT NOT NULL, " +
                        "`resultJson` TEXT NOT NULL)"
                )
            }
        }

        fun get(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "byokey_speak.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
