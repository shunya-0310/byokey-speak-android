package com.byokeylab.speak

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class AppStorage(context: Context) {
    private val prefs = context.getSharedPreferences("byokey_speak", Context.MODE_PRIVATE)
    private val secureApiKeyStore = SecureApiKeyStore(context)

    fun loadSettings(): AppSettings {
        return AppSettings(
            provider = runCatching {
                LlmProvider.valueOf(prefs.getString(KEY_PROVIDER, LlmProvider.Gemini.name) ?: LlmProvider.Gemini.name)
            }.getOrDefault(LlmProvider.Gemini),
            apiKey = secureApiKeyStore.load(),
            model = prefs.getString(KEY_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL,
            dailyReminderEnabled = prefs.getBoolean(KEY_REMINDER_ENABLED, true),
            reminderHour = prefs.getInt(KEY_REMINDER_HOUR, 7),
            reminderMinute = prefs.getInt(KEY_REMINDER_MINUTE, 15),
            englishLevel = runCatching {
                EnglishLevel.valueOf(prefs.getString(KEY_LEVEL, EnglishLevel.A1.name) ?: EnglishLevel.A1.name)
            }.getOrDefault(EnglishLevel.A1),
            coachInstructions = prefs.getString(KEY_COACH_INSTRUCTIONS, DEFAULT_COACH_INSTRUCTIONS)
                ?.ifBlank { DEFAULT_COACH_INSTRUCTIONS }
                ?: DEFAULT_COACH_INSTRUCTIONS
        )
    }

    fun saveSettings(settings: AppSettings) {
        secureApiKeyStore.save(settings.apiKey)
        prefs.edit()
            .putString(KEY_PROVIDER, settings.provider.name)
            .putString(KEY_MODEL, settings.model.trim().ifBlank { settings.provider.defaultModel })
            .putBoolean(KEY_REMINDER_ENABLED, settings.dailyReminderEnabled)
            .putInt(KEY_REMINDER_HOUR, settings.reminderHour.coerceIn(0, 23))
            .putInt(KEY_REMINDER_MINUTE, settings.reminderMinute.coerceIn(0, 59))
            .putString(KEY_LEVEL, settings.englishLevel.name)
            .putString(KEY_COACH_INSTRUCTIONS, settings.coachInstructions.trim().ifBlank { DEFAULT_COACH_INSTRUCTIONS })
            .apply()
    }

    fun loadMessages(): List<ChatMessage> {
        val raw = prefs.getString(KEY_MESSAGES, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    add(
                        ChatMessage(
                            id = item.optLong("id"),
                            role = MessageRole.valueOf(item.optString("role", MessageRole.System.name)),
                            text = item.optString("text"),
                            timestampMillis = item.optLong("timestampMillis")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun saveMessages(messages: List<ChatMessage>) {
        val array = JSONArray()
        messages.takeLast(MAX_SAVED_MESSAGES).forEach { message ->
            array.put(
                JSONObject()
                    .put("id", message.id)
                    .put("role", message.role.name)
                    .put("text", message.text)
                    .put("timestampMillis", message.timestampMillis)
            )
        }
        prefs.edit().putString(KEY_MESSAGES, array.toString()).apply()
    }

    fun clearMessages() {
        prefs.edit().remove(KEY_MESSAGES).apply()
    }


    fun loadNotes(): List<LearningNote> {
        val raw = prefs.getString(KEY_NOTES, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    add(
                        LearningNote(
                            id = item.optLong("id"),
                            sourceMessage = item.optString("sourceMessage"),
                            coachNotes = item.optString("coachNotes"),
                            betterOptions = item.optString("betterOptions"),
                            timestampMillis = item.optLong("timestampMillis")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun saveNotes(notes: List<LearningNote>) {
        val array = JSONArray()
        notes.take(MAX_SAVED_NOTES).forEach { note ->
            array.put(
                JSONObject()
                    .put("id", note.id)
                    .put("sourceMessage", note.sourceMessage)
                    .put("coachNotes", note.coachNotes)
                    .put("betterOptions", note.betterOptions)
                    .put("timestampMillis", note.timestampMillis)
            )
        }
        prefs.edit().putString(KEY_NOTES, array.toString()).apply()
    }

    fun clearNotes() {
        prefs.edit().remove(KEY_NOTES).apply()
    }

    /** 学習ストリーク（今日を含む直近の連続学習日数）を返す。途切れていたら0。 */
    fun loadStreak(): Int {
        val today = java.time.LocalDate.now().toEpochDay()
        val last = prefs.getLong(KEY_LAST_STUDY_DAY, -1L)
        val count = prefs.getInt(KEY_STREAK_COUNT, 0)
        return when {
            last < 0 -> 0
            today - last <= 1 -> count
            else -> 0
        }
    }

    /** 今日の学習を記録し、更新後のストリークを返す。 */
    fun recordStudySession(): Int {
        val today = java.time.LocalDate.now().toEpochDay()
        val last = prefs.getLong(KEY_LAST_STUDY_DAY, -1L)
        val count = prefs.getInt(KEY_STREAK_COUNT, 0)
        val next = when {
            last == today -> count.coerceAtLeast(1)
            last == today - 1 -> count + 1
            else -> 1
        }
        prefs.edit()
            .putLong(KEY_LAST_STUDY_DAY, today)
            .putInt(KEY_STREAK_COUNT, next)
            .apply()
        return next
    }

    companion object {
        val DEFAULT_MODEL: String = LlmProvider.Gemini.defaultModel
        const val DEFAULT_COACH_INSTRUCTIONS = "Be a calm, friendly coach. Keep the conversation moving and correct only the most useful point at a time."
        private const val MAX_SAVED_MESSAGES = 120
        private const val MAX_SAVED_NOTES = 200
        private const val KEY_PROVIDER = "provider"
        private const val KEY_MODEL = "model"
        private const val KEY_REMINDER_ENABLED = "reminder_enabled"
        private const val KEY_REMINDER_HOUR = "reminder_hour"
        private const val KEY_REMINDER_MINUTE = "reminder_minute"
        private const val KEY_MESSAGES = "messages"
        private const val KEY_NOTES = "notes"
        private const val KEY_LEVEL = "english_level"
        private const val KEY_COACH_INSTRUCTIONS = "coach_instructions"
        private const val KEY_STREAK_COUNT = "streak_count"
        private const val KEY_LAST_STUDY_DAY = "last_study_day"
    }
}
