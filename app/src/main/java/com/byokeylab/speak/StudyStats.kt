package com.byokeylab.speak

/** 純粋関数群。ユニットテスト対象。 */
object StudyStats {

    /** 学習した日の集合から、today を含む(または昨日まで続く)連続日数を返す。 */
    fun streak(studyDays: Set<Long>, today: Long): Int {
        var anchor = when {
            today in studyDays -> today
            (today - 1) in studyDays -> today - 1
            else -> return 0
        }
        var count = 0
        while (anchor in studyDays) {
            count++
            anchor--
        }
        return count
    }

    /** 最初のユーザー発話からチャットタイトルを作る。 */
    fun deriveChatTitle(firstUserMessage: String, maxLength: Int = 24): String {
        val singleLine = firstUserMessage
            .replace('\n', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
        if (singleLine.isEmpty()) return "New chat"
        return if (singleLine.length <= maxLength) singleLine
        else singleLine.take(maxLength).trimEnd() + "…"
    }

    /** コーチ返信から Natural reply 部分だけを取り出す(TTS用)。 */
    fun naturalReplyOf(text: String): String {
        val lower = text.lowercase()
        val startLabel = "natural reply:"
        val startIndex = lower.indexOf(startLabel)
        val from = if (startIndex >= 0) startIndex + startLabel.length else 0
        val end = listOf("coach notes:", "japanese explanation:", "better options:", "native note:")
            .mapNotNull { label -> lower.indexOf(label, from).takeIf { it > from } }
            .minOrNull() ?: text.length
        val extracted = text.substring(from, end).trim()
        return extracted.ifBlank { text }
    }
}
