package com.byokeylab.speak.data

import com.byokeylab.speak.ChatMessage
import com.byokeylab.speak.LearningNote
import com.byokeylab.speak.MessageRole
import com.byokeylab.speak.StudyStats
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

enum class ChatMode { Coach, Quick }

class ChatRepository(private val db: AppDatabase) {

    fun observeQuickChats(): Flow<List<ChatWithPreview>> = db.chatDao().observeChats(ChatMode.Quick.name)

    fun observeMessages(chatId: Long): Flow<List<ChatMessage>> =
        db.messageDao().observeMessages(chatId).map { list -> list.map { it.toChatMessage() } }

    fun observeNotes(): Flow<List<LearningNote>> =
        db.learningNoteDao().observeNotes().map { list -> list.map { it.toLearningNote() } }

    fun observeDailyStats(): Flow<List<DailyStatEntity>> = db.dailyStatDao().observeAll()

    /** Coachモードの会話を1つ確保して返す(直近の会話へすぐ戻れる)。 */
    suspend fun ensureCoachChat(): Long {
        db.chatDao().latestChat(ChatMode.Coach.name)?.let { return it.id }
        val now = System.currentTimeMillis()
        return db.chatDao().insert(
            ChatEntity(title = "Coach", mode = ChatMode.Coach.name, createdAt = now, updatedAt = now)
        )
    }

    suspend fun newCoachChat(): Long {
        val now = System.currentTimeMillis()
        return db.chatDao().insert(
            ChatEntity(title = "Coach", mode = ChatMode.Coach.name, createdAt = now, updatedAt = now)
        )
    }

    suspend fun createQuickChat(): Long {
        val now = System.currentTimeMillis()
        return db.chatDao().insert(
            ChatEntity(title = "New chat", mode = ChatMode.Quick.name, createdAt = now, updatedAt = now)
        )
    }

    suspend fun latestQuickChatId(): Long? = db.chatDao().latestChat(ChatMode.Quick.name)?.id

    suspend fun chatExists(chatId: Long): Boolean = db.chatDao().chatById(chatId) != null

    suspend fun appendMessage(chatId: Long, role: MessageRole, text: String): Long {
        val now = System.currentTimeMillis()
        val id = db.messageDao().insert(
            MessageEntity(chatId = chatId, role = role.name, text = text, createdAt = now)
        )
        db.chatDao().touch(chatId, now)
        if (role == MessageRole.User) {
            maybeAutoTitle(chatId, text)
        }
        return id
    }

    private suspend fun maybeAutoTitle(chatId: Long, firstMessage: String) {
        val chat = db.chatDao().chatById(chatId) ?: return
        if (chat.mode != ChatMode.Quick.name) return
        if (chat.title != "New chat") return
        if (db.messageDao().userMessageCount(chatId) != 1) return
        db.chatDao().rename(chatId, StudyStats.deriveChatTitle(firstMessage))
    }

    suspend fun messagesOf(chatId: Long): List<ChatMessage> =
        db.messageDao().messagesOf(chatId).map { it.toChatMessage() }

    suspend fun renameChat(chatId: Long, title: String) =
        db.chatDao().rename(chatId, title.trim().ifBlank { "New chat" })

    suspend fun setPinned(chatId: Long, pinned: Boolean) = db.chatDao().setPinned(chatId, pinned)

    suspend fun deleteChat(chatId: Long) = db.chatDao().delete(chatId)

    suspend fun clearChatMessages(chatId: Long) = db.messageDao().deleteByChat(chatId)

    suspend fun saveNote(note: LearningNote, chatId: Long?) {
        db.learningNoteDao().insert(
            LearningNoteEntity(
                chatId = chatId,
                sourceMessage = note.sourceMessage,
                coachNotes = note.coachNotes,
                betterOptions = note.betterOptions,
                japaneseNote = note.japaneseNote,
                createdAt = note.timestampMillis
            )
        )
        bumpToday { it.copy(notesSaved = it.notesSaved + 1) }
    }

    suspend fun setNoteReviewed(noteId: Long, reviewed: Boolean) {
        db.learningNoteDao().setReviewed(noteId, reviewed)
        if (reviewed) bumpToday { it.copy(reviewsDone = it.reviewsDone + 1) }
    }

    suspend fun deleteNote(noteId: Long) = db.learningNoteDao().delete(noteId)

    suspend fun recordTurn() = bumpToday { it.copy(turns = it.turns + 1) }

    suspend fun recordAssistUse() = bumpToday { it.copy(assistUses = it.assistUses + 1) }

    private suspend fun bumpToday(update: (DailyStatEntity) -> DailyStatEntity) {
        val today = LocalDate.now().toEpochDay()
        val current = db.dailyStatDao().byDay(today) ?: DailyStatEntity(epochDay = today)
        db.dailyStatDao().upsert(update(current))
    }

    // --- データ削除 (FR-010) ---

    suspend fun deleteAllConversations() {
        db.messageDao().deleteAll()
        db.chatDao().deleteAll()
    }

    suspend fun deleteAllNotes() = db.learningNoteDao().deleteAll()

    suspend fun deleteAllProgress() = db.dailyStatDao().deleteAll()

    suspend fun deleteEverything() {
        deleteAllConversations()
        deleteAllNotes()
        deleteAllProgress()
    }

    /** 旧SharedPreferences保存の会話・メモをRoomへ一度だけ取り込む。 */
    suspend fun importLegacy(messages: List<ChatMessage>, notes: List<LearningNote>) {
        if (messages.isNotEmpty()) {
            val now = System.currentTimeMillis()
            val chatId = db.chatDao().insert(
                ChatEntity(
                    title = "Imported conversation",
                    mode = ChatMode.Coach.name,
                    createdAt = now,
                    updatedAt = now
                )
            )
            messages.forEach { message ->
                db.messageDao().insert(
                    MessageEntity(
                        chatId = chatId,
                        role = message.role.name,
                        text = message.text,
                        createdAt = message.timestampMillis
                    )
                )
            }
        }
        notes.forEach { note ->
            db.learningNoteDao().insert(
                LearningNoteEntity(
                    chatId = null,
                    sourceMessage = note.sourceMessage,
                    coachNotes = note.coachNotes,
                    betterOptions = note.betterOptions,
                    japaneseNote = note.japaneseNote,
                    createdAt = note.timestampMillis
                )
            )
        }
    }
}

private fun MessageEntity.toChatMessage() = ChatMessage(
    id = id,
    role = runCatching { MessageRole.valueOf(role) }.getOrDefault(MessageRole.System),
    text = text,
    timestampMillis = createdAt
)

private fun LearningNoteEntity.toLearningNote() = LearningNote(
    id = id,
    sourceMessage = sourceMessage,
    coachNotes = coachNotes,
    betterOptions = betterOptions,
    japaneseNote = japaneseNote,
    reviewed = reviewed,
    timestampMillis = createdAt
)
