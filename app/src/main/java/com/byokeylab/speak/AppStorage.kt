package com.byokeylab.speak

import android.content.Context
import org.json.JSONArray

/**
 * 設定・フラグ類のSharedPreferences保存。
 * 会話・メモ・進捗はRoom(AppDatabase)へ移行済み。
 * loadLegacy*() は旧バージョンからの一度きりの移行専用。
 */
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

    fun deleteApiKey() {
        secureApiKeyStore.clear()
    }

    // --- Onboarding / ナビゲーション状態 ---

    var onboardingDone: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_DONE, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDING_DONE, value).apply()

    var lastOpenedQuickChatId: Long
        get() = prefs.getLong(KEY_LAST_QUICK_CHAT, -1L)
        set(value) = prefs.edit().putLong(KEY_LAST_QUICK_CHAT, value).apply()

    var legacyImported: Boolean
        get() = prefs.getBoolean(KEY_LEGACY_IMPORTED, false)
        set(value) = prefs.edit().putBoolean(KEY_LEGACY_IMPORTED, value).apply()

    // --- 旧バージョンデータ(移行専用) ---

    fun loadLegacyMessages(): List<ChatMessage> {
        val raw = prefs.getString(KEY_MESSAGES, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    add(
                        ChatMessage(
                            id = item.optLong("id"),
                            role = runCatching {
                                MessageRole.valueOf(item.optString("role", MessageRole.System.name))
                            }.getOrDefault(MessageRole.System),
                            text = item.optString("text"),
                            timestampMillis = item.optLong("timestampMillis")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun loadLegacyNotes(): List<LearningNote> {
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

    fun clearLegacyConversationData() {
        prefs.edit()
            .remove(KEY_MESSAGES)
            .remove(KEY_NOTES)
            .remove(KEY_STREAK_COUNT)
            .remove(KEY_LAST_STUDY_DAY)
            .apply()
    }

    /** 全ローカルデータ削除の一部: 設定・フラグをリセットする(FR-010)。 */
    fun clearAllPreferences() {
        secureApiKeyStore.clear()
        prefs.edit().clear().apply()
    }

    companion object {
        val DEFAULT_MODEL: String = LlmProvider.Gemini.defaultModel
        val DEFAULT_COACH_INSTRUCTIONS = """
            |# Coach Skills
            |
            |## 目的
            |- ビジネスではなく日常会話を強化したい。
            |
            |## 性格・口調
            |- かしこまった話し方ではなく、フランクに話す。
            |- スラングや気の利いた言い回しをよく使う。
            |
            |## 添削方針
            |- 通じるけどネイティブは使わない表現も、「ネイティブではこうだよ」と細かく指摘する。
            |
            |## 解説の言語
            |- 解説部分だけは英語だけでなく日本語も併記する。
        """.trimMargin()
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
        private const val KEY_ONBOARDING_DONE = "onboarding_done"
        private const val KEY_LAST_QUICK_CHAT = "last_quick_chat_id"
        private const val KEY_LEGACY_IMPORTED = "legacy_imported"
    }
}
