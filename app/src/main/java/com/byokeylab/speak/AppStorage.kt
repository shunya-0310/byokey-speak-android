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

    init {
        migrateToGeminiOnlyIfNeeded()
    }

    /**
     * 旧版は一つの暗号化領域へ全providerのキーを保存していた。
     * OpenAI/ClaudeキーをGemini APIへ誤送信しないよう、enumを読む前に生の旧値で判定する。
     */
    private fun migrateToGeminiOnlyIfNeeded() {
        if (prefs.getInt(KEY_GEMINI_ONLY_MIGRATION_VERSION, 0) >= GEMINI_ONLY_MIGRATION_VERSION) return
        val legacyProvider = prefs.getString(KEY_PROVIDER, LlmProvider.Gemini.name)
        val removedNonGeminiKey = legacyProvider != null && legacyProvider != LlmProvider.Gemini.name
        if (removedNonGeminiKey) secureApiKeyStore.clear()
        val storedModel = prefs.getString(KEY_MODEL, DEFAULT_MODEL).orEmpty()
        prefs.edit()
            .putString(KEY_PROVIDER, LlmProvider.Gemini.name)
            .putString(KEY_MODEL, normalizedGeminiModel(storedModel))
            .putBoolean(KEY_GEMINI_ONLY_KEY_REMOVED_NOTICE, removedNonGeminiKey)
            .putInt(KEY_GEMINI_ONLY_MIGRATION_VERSION, GEMINI_ONLY_MIGRATION_VERSION)
            .apply()
    }

    fun loadSettings(): AppSettings {
        return AppSettings(
            provider = LlmProvider.Gemini,
            apiKey = secureApiKeyStore.load(),
            model = normalizedGeminiModel(prefs.getString(KEY_MODEL, DEFAULT_MODEL).orEmpty()),
            dailyReminderEnabled = prefs.getBoolean(KEY_REMINDER_ENABLED, true),
            reminderHour = prefs.getInt(KEY_REMINDER_HOUR, ReminderScheduler.DEFAULT_NEWS_HOUR),
            reminderMinute = prefs.getInt(KEY_REMINDER_MINUTE, ReminderScheduler.DEFAULT_NEWS_MINUTE),
            englishLevel = runCatching {
                EnglishLevel.valueOf(prefs.getString(KEY_LEVEL, EnglishLevel.A1.name) ?: EnglishLevel.A1.name)
            }.getOrDefault(EnglishLevel.A1),
            coachInstructions = migrateCoachInstructions(
                prefs.getString(KEY_COACH_INSTRUCTIONS, DEFAULT_COACH_INSTRUCTIONS)
                    ?.ifBlank { DEFAULT_COACH_INSTRUCTIONS }
                    ?: DEFAULT_COACH_INSTRUCTIONS
            ),
            theme = runCatching {
                AppTheme.valueOf(prefs.getString(KEY_THEME, AppTheme.Light.name) ?: AppTheme.Light.name)
            }.getOrDefault(AppTheme.Light),
            voiceGender = runCatching {
                VoiceGender.valueOf(prefs.getString(KEY_VOICE, VoiceGender.Female.name) ?: VoiceGender.Female.name)
            }.getOrDefault(VoiceGender.Female),
            speechOutputProvider = runCatching {
                SpeechOutputProvider.valueOf(prefs.getString(KEY_SPEECH_OUTPUT, SpeechOutputProvider.Device.name) ?: SpeechOutputProvider.Device.name)
            }.getOrDefault(SpeechOutputProvider.Device),
            geminiTtsVoice = runCatching {
                GeminiTtsVoice.valueOf(prefs.getString(KEY_GEMINI_TTS_VOICE, GeminiTtsVoice.Kore.name) ?: GeminiTtsVoice.Kore.name)
            }.getOrDefault(GeminiTtsVoice.Kore),
            soundEffectsEnabled = prefs.getBoolean(KEY_SOUND_EFFECTS_ENABLED, true)
        )
    }

    /** 旧バージョンのデフォルト文が保存されている場合は、新しいmd形式デフォルトへ置き換える。 */
    private fun migrateCoachInstructions(stored: String): String {
        return if (stored.trim() in LEGACY_COACH_DEFAULTS) DEFAULT_COACH_INSTRUCTIONS else stored
    }

    fun saveSettings(settings: AppSettings) {
        secureApiKeyStore.save(settings.apiKey)
        prefs.edit()
            .putString(KEY_PROVIDER, LlmProvider.Gemini.name)
            .putString(KEY_MODEL, normalizedGeminiModel(settings.model))
            .putBoolean(KEY_REMINDER_ENABLED, settings.dailyReminderEnabled)
            .putInt(KEY_REMINDER_HOUR, ReminderScheduler.DEFAULT_NEWS_HOUR)
            .putInt(KEY_REMINDER_MINUTE, ReminderScheduler.DEFAULT_NEWS_MINUTE)
            .putString(KEY_LEVEL, settings.englishLevel.name)
            .putString(KEY_COACH_INSTRUCTIONS, settings.coachInstructions.trim().ifBlank { DEFAULT_COACH_INSTRUCTIONS })
            .putString(KEY_THEME, settings.theme.name)
            .putString(KEY_VOICE, settings.voiceGender.name)
            .putString(KEY_SPEECH_OUTPUT, settings.speechOutputProvider.name)
            .putString(KEY_GEMINI_TTS_VOICE, settings.geminiTtsVoice.name)
            .putBoolean(KEY_SOUND_EFFECTS_ENABLED, settings.soundEffectsEnabled)
            .apply()
    }

    fun deleteApiKey() {
        secureApiKeyStore.clear()
    }

    fun exportPortableSettings(): PortableSettings {
        val current = loadSettings()
        return PortableSettings(
            model = current.model,
            dailyReminderEnabled = current.dailyReminderEnabled,
            reminderHour = ReminderScheduler.DEFAULT_NEWS_HOUR,
            reminderMinute = ReminderScheduler.DEFAULT_NEWS_MINUTE,
            englishLevel = current.englishLevel.name,
            coachInstructions = current.coachInstructions,
            theme = current.theme.name,
            voiceGender = current.voiceGender.name,
            speechOutputProvider = current.speechOutputProvider.name,
            geminiTtsVoice = current.geminiTtsVoice.name,
            soundEffectsEnabled = current.soundEffectsEnabled
        )
    }

    fun validatePortableSettings(restored: PortableSettings): AppSettings {
        val currentApiKey = secureApiKeyStore.load()
        val level = runCatching { EnglishLevel.valueOf(restored.englishLevel) }
            .getOrElse { throw IllegalArgumentException("CEFR設定が不正です。") }
        val theme = runCatching { AppTheme.valueOf(restored.theme) }
            .getOrElse { throw IllegalArgumentException("テーマ設定が不正です。") }
        val voice = runCatching { VoiceGender.valueOf(restored.voiceGender) }
            .getOrElse { throw IllegalArgumentException("音声設定が不正です。") }
        val speechOutputProvider = runCatching { SpeechOutputProvider.valueOf(restored.speechOutputProvider) }
            .getOrDefault(SpeechOutputProvider.Device)
        val geminiTtsVoice = runCatching { GeminiTtsVoice.valueOf(restored.geminiTtsVoice) }
            .getOrDefault(GeminiTtsVoice.Kore)
        require(restored.reminderHour in 0..23 && restored.reminderMinute in 0..59) {
            "通知時刻の設定が不正です。"
        }
        return AppSettings(
            provider = LlmProvider.Gemini,
            apiKey = currentApiKey,
            model = normalizedGeminiModel(restored.model),
            dailyReminderEnabled = restored.dailyReminderEnabled,
            reminderHour = ReminderScheduler.DEFAULT_NEWS_HOUR,
            reminderMinute = ReminderScheduler.DEFAULT_NEWS_MINUTE,
            englishLevel = level,
            coachInstructions = restored.coachInstructions.trim()
                .ifBlank { DEFAULT_COACH_INSTRUCTIONS },
            theme = theme,
            voiceGender = voice,
            speechOutputProvider = speechOutputProvider,
            geminiTtsVoice = geminiTtsVoice,
            soundEffectsEnabled = restored.soundEffectsEnabled
        )
    }

    fun consumeGeminiOnlyKeyRemovedNotice(): Boolean {
        if (!prefs.getBoolean(KEY_GEMINI_ONLY_KEY_REMOVED_NOTICE, false)) return false
        prefs.edit().putBoolean(KEY_GEMINI_ONLY_KEY_REMOVED_NOTICE, false).apply()
        return true
    }

    private fun normalizedGeminiModel(value: String): String =
        LlmProvider.Gemini.modelOptions.firstOrNull { it.id == value.trim() }?.id ?: DEFAULT_MODEL

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

    var analysisConsentDone: Boolean
        get() = prefs.getBoolean(KEY_ANALYSIS_CONSENT_DONE, false)
        set(value) = prefs.edit().putBoolean(KEY_ANALYSIS_CONSENT_DONE, value).apply()

    var feedbackPromptShown: Boolean
        get() = prefs.getBoolean(KEY_FEEDBACK_PROMPT_SHOWN, false)
        set(value) = prefs.edit().putBoolean(KEY_FEEDBACK_PROMPT_SHOWN, value).apply()

    val hasCurrentExternalDataConsent: Boolean
        get() = prefs.getInt(KEY_EXTERNAL_DATA_CONSENT_VERSION, 0) >= EXTERNAL_DATA_CONSENT_VERSION

    fun recordExternalDataConsent() {
        prefs.edit()
            .putInt(KEY_EXTERNAL_DATA_CONSENT_VERSION, EXTERNAL_DATA_CONSENT_VERSION)
            .putLong(KEY_EXTERNAL_DATA_CONSENT_AT, System.currentTimeMillis())
            .apply()
    }

    fun isTutorialSeen(kind: AppTutorialKind): Boolean {
        return prefs.getBoolean(tutorialKey(kind), false)
    }

    fun markTutorialSeen(kind: AppTutorialKind) {
        prefs.edit().putBoolean(tutorialKey(kind), true).apply()
    }

    fun resetTutorials() {
        prefs.edit()
            .remove(KEY_TUTORIAL_CHATS_SEEN)
            .remove(KEY_TUTORIAL_CHAT_CONTROLS_SEEN)
            .remove(KEY_TUTORIAL_VOCABULARY_SEEN)
            .remove(KEY_TUTORIAL_PROGRESS_SEEN)
            .remove(KEY_TUTORIAL_SETTINGS_SEEN)
            .apply()
    }

    private fun tutorialKey(kind: AppTutorialKind): String = when (kind) {
        AppTutorialKind.Chats -> KEY_TUTORIAL_CHATS_SEEN
        AppTutorialKind.ChatControls -> KEY_TUTORIAL_CHAT_CONTROLS_SEEN
        AppTutorialKind.Vocabulary -> KEY_TUTORIAL_VOCABULARY_SEEN
        AppTutorialKind.Progress -> KEY_TUTORIAL_PROGRESS_SEEN
        AppTutorialKind.Settings -> KEY_TUTORIAL_SETTINGS_SEEN
    }

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

        /** 過去バージョンで保存された可能性のあるデフォルト文(md移行判定用)。 */
        private val LEGACY_COACH_DEFAULTS = setOf(
            "Be a calm, friendly coach. Keep the conversation moving and correct only the most useful point at a time.",
            "ビジネスではなく日常会話を強化したいので、かしこまった話し方ではなくフランクに話してほしい。" +
                "通じはするけど、ネイティブでは使わない表現など、細かなところも「ネイティブではこうだよ」と指摘してほしい。" +
                "スラングや気の利いた言い回しを良く使う性格。解説部分だけは英語だけでなく日本語も併記して。"
        )
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
        private const val KEY_THEME = "app_theme"
        private const val KEY_VOICE = "voice_gender"
        private const val KEY_SOUND_EFFECTS_ENABLED = "sound_effects_enabled"
        private const val KEY_SPEECH_OUTPUT = "speech_output_provider"
        private const val KEY_GEMINI_TTS_VOICE = "gemini_tts_voice"
        private const val KEY_GEMINI_ONLY_MIGRATION_VERSION = "gemini_only_migration_version"
        private const val KEY_GEMINI_ONLY_KEY_REMOVED_NOTICE = "gemini_only_key_removed_notice"
        private const val GEMINI_ONLY_MIGRATION_VERSION = 1
        private const val KEY_ONBOARDING_DONE = "onboarding_done"
        private const val KEY_LAST_QUICK_CHAT = "last_quick_chat_id"
        private const val KEY_LEGACY_IMPORTED = "legacy_imported"
        private const val KEY_ANALYSIS_CONSENT_DONE = "analysis_consent_done"
        private const val KEY_FEEDBACK_PROMPT_SHOWN = "analysis_feedback_prompt_shown"
        private const val KEY_EXTERNAL_DATA_CONSENT_VERSION = "external_data_consent_version"
        private const val KEY_EXTERNAL_DATA_CONSENT_AT = "external_data_consent_at"
        private const val EXTERNAL_DATA_CONSENT_VERSION = 2
        private const val KEY_TUTORIAL_CHATS_SEEN = "tutorial_chats_seen"
        private const val KEY_TUTORIAL_CHAT_CONTROLS_SEEN = "tutorial_chat_controls_seen"
        private const val KEY_TUTORIAL_VOCABULARY_SEEN = "tutorial_vocabulary_seen"
        private const val KEY_TUTORIAL_PROGRESS_SEEN = "tutorial_progress_seen"
        private const val KEY_TUTORIAL_SETTINGS_SEEN = "tutorial_settings_seen"
    }
}
