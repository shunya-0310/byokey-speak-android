package com.byokeylab.speak

object LearningNoteExtractor {
    fun fromCoachResponse(response: String, sourceMessage: String): LearningNote? {
        val coachNotes = extractSection(
            text = response,
            startLabels = listOf("Coach notes:", "Coach notes", "English coach notes:", "English coach notes", "2. English coach notes", "### 2. English coach notes", "Native note:"),
            endLabels = listOf("Japanese explanation:", "Japanese explanation", "3. Japanese explanation", "### 3. Japanese explanation", "Better options:", "Better options", "4. Better options", "### 4. Better options", "Natural reply:", "Natural reply")
        )
        val betterOptions = extractSection(
            text = response,
            startLabels = listOf("Better options:", "Better options", "4. Better options", "### 4. Better options"),
            endLabels = listOf("Natural reply:", "Natural reply", "Coach notes:", "Coach notes", "English coach notes:", "English coach notes", "Japanese explanation:", "Japanese explanation")
        )
        val japaneseNote = extractSection(
            text = response,
            startLabels = listOf("Japanese explanation:", "Japanese explanation", "3. Japanese explanation", "### 3. Japanese explanation"),
            endLabels = listOf("Better options:", "Better options", "4. Better options", "### 4. Better options", "Natural reply:", "Natural reply", "Coach notes:", "Coach notes")
        )

        if (coachNotes.isBlank() && betterOptions.isBlank()) return null

        return LearningNote(
            id = System.nanoTime(),
            sourceMessage = sourceMessage,
            coachNotes = coachNotes.ifBlank { "No coach notes captured." },
            betterOptions = betterOptions,
            japaneseNote = japaneseNote,
            timestampMillis = System.currentTimeMillis()
        )
    }

    private fun extractSection(
        text: String,
        startLabels: List<String>,
        endLabels: List<String>
    ): String {
        val lower = text.lowercase()
        val startMatch = startLabels
            .mapNotNull { label ->
                val index = lower.indexOf(label.lowercase())
                if (index >= 0) index to label.length else null
            }
            .minByOrNull { it.first }
            ?: return ""

        val contentStart = startMatch.first + startMatch.second
        val contentEnd = endLabels
            .mapNotNull { label ->
                val index = lower.indexOf(label.lowercase(), startIndex = contentStart)
                if (index > contentStart) index else null
            }
            .minOrNull()
            ?: text.length

        return text.substring(contentStart, contentEnd)
            .trim()
            .trim('-', '*', '#', ' ', '\n', '\r')
            .trim()
    }
}
