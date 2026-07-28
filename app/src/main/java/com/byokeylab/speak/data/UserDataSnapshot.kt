package com.byokeylab.speak.data

data class UserDataSnapshot(
    val chats: List<ChatEntity>,
    val messages: List<MessageEntity>,
    val notes: List<LearningNoteEntity>,
    val vocabCards: List<VocabCardEntity>,
    val dailyStats: List<DailyStatEntity>,
    val analyses: List<ConversationAnalysisEntity>
) {
    val recordCount: Int
        get() = chats.size + messages.size + notes.size + vocabCards.size +
            dailyStats.size + analyses.size

    fun validate() {
        require(recordCount <= MAX_RECORDS) { "バックアップのデータ件数が上限を超えています。" }
        requireUniquePositiveIds("会話", chats.map { it.id })
        requireUniquePositiveIds("メッセージ", messages.map { it.id })
        requireUniquePositiveIds("学習メモ", notes.map { it.id })
        requireUniquePositiveIds("Vocabulary", vocabCards.map { it.id })
        requireUniquePositiveIds("分析", analyses.map { it.id })
        require(dailyStats.map { it.epochDay }.distinct().size == dailyStats.size) {
            "学習統計の日付が重複しています。"
        }

        val chatIds = chats.mapTo(mutableSetOf()) { it.id }
        require(messages.all { it.chatId in chatIds }) {
            "会話に紐づかないメッセージが含まれています。"
        }
        require(chats.all { it.createdAt >= 0 && it.updatedAt >= 0 }) {
            "会話の日時が不正です。"
        }
        require(messages.all { it.createdAt >= 0 }) { "メッセージの日時が不正です。" }
    }

    private fun requireUniquePositiveIds(label: String, ids: List<Long>) {
        require(ids.all { it > 0 }) { "$label IDが不正です。" }
        require(ids.distinct().size == ids.size) { "$label IDが重複しています。" }
    }

    private companion object {
        const val MAX_RECORDS = 200_000
    }
}
