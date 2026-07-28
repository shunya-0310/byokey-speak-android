package com.byokeylab.speak.data

import com.byokeylab.speak.ChatMessage
import com.byokeylab.speak.ConversationAnalysis
import com.byokeylab.speak.ConversationAnalysisRecord
import com.byokeylab.speak.ConversationOrigin
import com.byokeylab.speak.LearningNote
import com.byokeylab.speak.MessageRole
import com.byokeylab.speak.MessageInputSource
import com.byokeylab.speak.StudyStats
import com.byokeylab.speak.VocabCard
import com.byokeylab.speak.VocabSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import androidx.room.withTransaction
import java.time.LocalDate
import org.json.JSONObject

enum class ChatMode { Coach, Quick }

class ChatRepository(private val db: AppDatabase) {

    suspend fun createBackupSnapshot(): UserDataSnapshot = db.withTransaction {
        UserDataSnapshot(
            chats = db.chatDao().allForBackup(),
            messages = db.messageDao().allForBackup(),
            notes = db.learningNoteDao().allForBackup(),
            vocabCards = db.vocabCardDao().allForBackup(),
            dailyStats = db.dailyStatDao().allForBackup(),
            analyses = db.conversationAnalysisDao().allForBackup()
        )
    }

    suspend fun restoreBackupSnapshot(snapshot: UserDataSnapshot) {
        db.withTransaction {
            db.messageDao().deleteAll()
            db.chatDao().deleteAll()
            db.learningNoteDao().deleteAll()
            db.vocabCardDao().deleteAll()
            db.dailyStatDao().deleteAll()
            db.conversationAnalysisDao().deleteAll()

            db.chatDao().restoreAll(snapshot.chats)
            db.messageDao().restoreAll(snapshot.messages)
            db.learningNoteDao().restoreAll(snapshot.notes)
            db.vocabCardDao().restoreAll(snapshot.vocabCards)
            db.dailyStatDao().restoreAll(snapshot.dailyStats)
            db.conversationAnalysisDao().restoreAll(snapshot.analyses)
        }
    }

    fun observeQuickChats(): Flow<List<ChatWithPreview>> = db.chatDao().observeAllChats()

    fun observeCoachChats(): Flow<List<ChatWithPreview>> = db.chatDao().observeChats(ChatMode.Coach.name)

    fun observeMessages(chatId: Long): Flow<List<ChatMessage>> =
        db.messageDao().observeMessages(chatId).map { list -> list.map { it.toChatMessage() } }

    fun observeAllUserMessageTexts(): Flow<List<String>> =
        db.messageDao().observeAllUserMessageTexts()

    fun observeNotes(): Flow<List<LearningNote>> =
        db.learningNoteDao().observeNotes().map { list -> list.map { it.toLearningNote() } }

    fun observeDailyStats(): Flow<List<DailyStatEntity>> = db.dailyStatDao().observeAll()

    fun observeAnalyses(): Flow<List<ConversationAnalysisRecord>> =
        db.conversationAnalysisDao().observeAll().map { list -> list.mapNotNull { it.toAnalysisRecord() } }

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
            ChatEntity(
                title = "New chat",
                mode = ChatMode.Quick.name,
                origin = ConversationOrigin.FREE_CHAT.name,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    suspend fun createTopicChat(topic: String): Long {
        val now = System.currentTimeMillis()
        val chatId = db.chatDao().insert(
            ChatEntity(
                title = StudyStats.deriveChatTitle(topic),
                mode = ChatMode.Quick.name,
                origin = ConversationOrigin.TOPIC.name,
                createdAt = now,
                updatedAt = now
            )
        )
        appendMessage(chatId, MessageRole.System, "New topic: $topic")
        return chatId
    }

    suspend fun createNewsChat(title: String, hiddenContext: String, coachOpener: String): Long {
        val now = System.currentTimeMillis()
        val chatId = db.chatDao().insert(
            ChatEntity(
                title = StudyStats.deriveChatTitle(title),
                mode = ChatMode.Quick.name,
                origin = ConversationOrigin.DAILY_NEWS.name,
                createdAt = now,
                updatedAt = now
            )
        )
        appendMessage(chatId, MessageRole.System, hiddenContext)
        appendMessage(chatId, MessageRole.Coach, coachOpener)
        return chatId
    }

    suspend fun latestQuickChatId(): Long? = db.chatDao().latestChat(ChatMode.Quick.name)?.id

    suspend fun chatExists(chatId: Long): Boolean = db.chatDao().chatById(chatId) != null

    suspend fun appendMessage(
        chatId: Long,
        role: MessageRole,
        text: String,
        inputSource: MessageInputSource = if (role == MessageRole.User) MessageInputSource.TYPED else MessageInputSource.NONE,
        usedQuickAssist: Boolean = false
    ): Long {
        val now = System.currentTimeMillis()
        val id = db.messageDao().insert(
            MessageEntity(
                chatId = chatId,
                role = role.name,
                text = text,
                inputSource = if (role == MessageRole.User) inputSource.name else MessageInputSource.NONE.name,
                usedQuickAssist = role == MessageRole.User && usedQuickAssist,
                createdAt = now
            )
        )
        db.chatDao().touch(chatId, now)
        if (role == MessageRole.User) {
            maybeAutoTitle(chatId, text)
        }
        return id
    }

    private suspend fun maybeAutoTitle(chatId: Long, firstMessage: String) {
        val chat = db.chatDao().chatById(chatId) ?: return
        // 未編集の初期タイトルのままなら、最初のユーザー発話から自動命名する (Coach/Quick共通)
        if (chat.title != "New chat" && chat.title != "Coach") return
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

    // --- 単語帳 (Review) ---

    fun observeVocabCards(): Flow<List<VocabCard>> =
        db.vocabCardDao().observeAll().map { list -> list.map { it.toVocabCard() } }

    suspend fun saveVocabCard(expression: String, meaning: String, source: VocabSource, chatId: Long?) {
        val trimmed = expression.trim()
        if (trimmed.isBlank()) return
        db.vocabCardDao().insert(
            VocabCardEntity(
                expression = trimmed,
                meaning = meaning.trim(),
                source = source.name,
                chatId = chatId,
                createdAt = System.currentTimeMillis()
            )
        )
        bumpToday { it.copy(notesSaved = it.notesSaved + 1) }
    }

    suspend fun setVocabFavorite(cardId: Long, favorite: Boolean) =
        db.vocabCardDao().setFavorite(cardId, favorite)

    suspend fun deleteVocabCard(cardId: Long) = db.vocabCardDao().delete(cardId)

    suspend fun recordTurn() = bumpToday { it.copy(turns = it.turns + 1) }

    suspend fun recordAssistUse() = bumpToday { it.copy(assistUses = it.assistUses + 1) }

    suspend fun analysisInput(now: Long = System.currentTimeMillis()): ConversationAnalysisInput {
        val start = now - ANALYSIS_PERIOD_MS
        val messages = db.messageDao().messagesSince(start)
        val userMessages = messages
            .filter { it.role == MessageRole.User.name }
            .takeLast(100)
        return ConversationAnalysisInput(
            periodStart = userMessages.firstOrNull()?.createdAt ?: start,
            periodEnd = userMessages.lastOrNull()?.createdAt ?: now,
            messages = userMessages.map { it.toChatMessage() },
            context = pairUserMessagesWithCoach(messages, userMessages)
        )
    }

    suspend fun latestUserMessageAt(): Long? = db.messageDao().latestUserMessage()?.createdAt

    suspend fun latestAnalysis(): ConversationAnalysisRecord? =
        db.conversationAnalysisDao().latest()?.toAnalysisRecord()

    suspend fun saveAnalysis(
        periodStart: Long,
        periodEnd: Long,
        userMessageCount: Int,
        provider: String,
        model: String,
        resultJson: String
    ) {
        db.conversationAnalysisDao().insert(
            ConversationAnalysisEntity(
                createdAt = System.currentTimeMillis(),
                periodStart = periodStart,
                periodEnd = periodEnd,
                userMessageCount = userMessageCount,
                provider = provider,
                model = model,
                resultJson = resultJson
            )
        )
    }

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

    suspend fun deleteAllNotes() {
        db.learningNoteDao().deleteAll()
        db.vocabCardDao().deleteAll()
        db.conversationAnalysisDao().deleteAll()
    }

    suspend fun deleteAllProgress() {
        db.dailyStatDao().deleteAll()
        db.conversationAnalysisDao().deleteAll()
    }

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
                    origin = ConversationOrigin.MIGRATED_COACH.name,
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
                        inputSource = if (message.role == MessageRole.User) {
                            MessageInputSource.UNKNOWN.name
                        } else {
                            MessageInputSource.NONE.name
                        },
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

data class ConversationAnalysisInput(
    val periodStart: Long,
    val periodEnd: Long,
    val messages: List<ChatMessage>,
    val context: List<AnalysisMessageContext>
)

data class AnalysisMessageContext(
    val userText: String,
    val coachText: String?,
    val createdAt: Long,
    val inputSource: MessageInputSource,
    val usedQuickAssist: Boolean
)

private fun pairUserMessagesWithCoach(
    allMessages: List<MessageEntity>,
    userMessages: List<MessageEntity>
): List<AnalysisMessageContext> {
    return userMessages.map { user ->
        val coach = allMessages
            .asSequence()
            .filter { it.chatId == user.chatId && it.id > user.id && it.role == MessageRole.Coach.name }
            .minByOrNull { it.id }
        AnalysisMessageContext(
            userText = user.text,
            coachText = coach?.text,
            createdAt = user.createdAt,
            inputSource = runCatching {
                MessageInputSource.valueOf(user.inputSource)
            }.getOrDefault(MessageInputSource.UNKNOWN),
            usedQuickAssist = user.usedQuickAssist
        )
    }
}

private fun MessageEntity.toChatMessage() = ChatMessage(
    id = id,
    role = runCatching { MessageRole.valueOf(role) }.getOrDefault(MessageRole.System),
    text = text,
    inputSource = runCatching { MessageInputSource.valueOf(inputSource) }.getOrDefault(MessageInputSource.UNKNOWN),
    usedQuickAssist = usedQuickAssist,
    timestampMillis = createdAt
)

private fun VocabCardEntity.toVocabCard() = VocabCard(
    id = id,
    expression = expression,
    meaning = meaning,
    source = runCatching { VocabSource.valueOf(source) }.getOrDefault(VocabSource.Coach),
    favorite = favorite,
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

private fun ConversationAnalysisEntity.toAnalysisRecord(): ConversationAnalysisRecord? {
    val parsed = ConversationAnalysisParser.parse(resultJson).getOrNull() ?: return null
    return ConversationAnalysisRecord(
        id = id,
        createdAt = createdAt,
        periodStart = periodStart,
        periodEnd = periodEnd,
        userMessageCount = userMessageCount,
        provider = provider,
        model = model,
        result = parsed
    )
}

object ConversationAnalysisParser {
    fun parse(raw: String): Result<ConversationAnalysis> {
        return runCatching {
            val json = JSONObject(extractJson(raw))
            ConversationAnalysis(
                summary = json.optString("summary").trim(),
                strengths = json.optJSONArray("strengths").toAnalysisPoints(),
                recurringPatterns = json.optJSONArray("recurringPatterns").toRecurringPatterns(),
                improvements = json.optJSONArray("improvements").toAnalysisPoints(),
                nextFocus = json.optJSONArray("nextFocus").toStringList(),
                practicePrompts = json.optJSONArray("practicePrompts").toStringList(),
                estimatedCefr = json.optString("estimatedCefr").trim(),
                cefrRationale = json.optString("cefrRationale").trim(),
                levelUpPlan = json.optJSONArray("levelUpPlan").toStringList()
            )
        }
    }

    private fun extractJson(raw: String): String {
        val trimmed = raw.trim()
        val start = trimmed.indexOf('{')
        val end = trimmed.lastIndexOf('}')
        return if (start >= 0 && end > start) trimmed.substring(start, end + 1) else trimmed
    }
}

private fun org.json.JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    return buildList {
        for (index in 0 until length()) {
            val value = optString(index).trim()
            if (value.isNotBlank()) add(value)
        }
    }
}

private fun org.json.JSONArray?.toAnalysisPoints(): List<com.byokeylab.speak.AnalysisPoint> {
    if (this == null) return emptyList()
    return buildList {
        for (index in 0 until length()) {
            val item = optJSONObject(index) ?: continue
            add(
                com.byokeylab.speak.AnalysisPoint(
                    title = item.optString("title").trim(),
                    evidence = item.optJSONArray("evidence").toStringList(),
                    comment = item.optString("comment").trim()
                )
            )
        }
    }
}

private fun org.json.JSONArray?.toRecurringPatterns(): List<com.byokeylab.speak.RecurringPattern> {
    if (this == null) return emptyList()
    return buildList {
        for (index in 0 until length()) {
            val item = optJSONObject(index) ?: continue
            add(
                com.byokeylab.speak.RecurringPattern(
                    category = item.optString("category").trim(),
                    title = item.optString("title").trim(),
                    occurrences = item.optInt("occurrences"),
                    examples = item.optJSONArray("examples").toAnalysisExamples(),
                    nextAction = item.optString("nextAction").trim()
                )
            )
        }
    }
}

private fun org.json.JSONArray?.toAnalysisExamples(): List<com.byokeylab.speak.AnalysisExample> {
    if (this == null) return emptyList()
    return buildList {
        for (index in 0 until length()) {
            val item = optJSONObject(index) ?: continue
            add(
                com.byokeylab.speak.AnalysisExample(
                    original = item.optString("original").trim(),
                    suggestion = item.optString("suggestion").trim(),
                    explanationJa = item.optString("explanationJa").trim()
                )
            )
        }
    }
}

private const val ANALYSIS_PERIOD_MS = 30L * 24L * 60L * 60L * 1000L
