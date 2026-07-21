package com.byokeylab.speak

enum class MessageRole {
    User,
    Coach,
    System
}

data class ChatMessage(
    val id: Long,
    val role: MessageRole,
    val text: String,
    val timestampMillis: Long
)

data class LearningNote(
    val id: Long,
    val sourceMessage: String,
    val coachNotes: String,
    val betterOptions: String,
    val japaneseNote: String = "",
    val reviewed: Boolean = false,
    val timestampMillis: Long
)

enum class LlmProvider(val label: String, val defaultModel: String) {
    Gemini("Gemini", "gemini-3.5-flash"),
    OpenAI("OpenAI", "gpt-5-mini"),
    Anthropic("Claude", "claude-haiku-4-5-20251001")
}

/** FR-002: 接続テスト・API呼び出しのエラー分類 */
enum class LlmErrorKind(val userMessage: String) {
    MissingApiKey("APIキーが未入力です。設定画面で入力してください。"),
    InvalidApiKey("APIキーが無効です。プロバイダーの管理画面で確認してください。"),
    InvalidModel("モデルIDが無効か、このAPIキーでは利用できません。"),
    Billing("残高不足または課金設定が未完了の可能性があります。プロバイダーの課金設定を確認してください。"),
    RateLimit("レート制限に達しました。しばらく待ってから再試行してください。"),
    Network("ネットワークに接続できません。通信状態を確認してください。"),
    Provider("プロバイダー側でエラーが発生しました。時間をおいて再試行してください。"),
    Unknown("エラーが発生しました。設定と通信状態を確認してください。")
}

class LlmException(
    val kind: LlmErrorKind,
    message: String
) : Exception(message)

enum class EnglishLevel(val label: String, val promptInstruction: String) {
    A1(
        "A1",
        "Use very common words and short sentences. Ask one simple question at a time and explain unfamiliar expressions in Japanese."
    ),
    A2(
        "A2",
        "Use common everyday English and short connected sentences. Introduce useful expressions with brief Japanese explanations."
    ),
    B1(
        "B1",
        "Use natural everyday English. Encourage the learner to explain reasons, experiences, and opinions in connected sentences."
    ),
    B2(
        "B2",
        "Use fluent conversational English with some nuance and idioms. Help the learner make arguments and respond spontaneously."
    ),
    C1(
        "C1",
        "Use flexible, precise, native-level English. Discuss abstract topics and explain subtle register or nuance when useful."
    ),
    C2(
        "C2",
        "Use highly natural and precise English without simplifying. Focus feedback on subtle tone, style, and idiomatic choice."
    )
}

data class AppSettings(
    val provider: LlmProvider = LlmProvider.Gemini,
    val apiKey: String,
    val model: String,
    val dailyReminderEnabled: Boolean,
    val reminderHour: Int,
    val reminderMinute: Int,
    val englishLevel: EnglishLevel = EnglishLevel.A1,
    val coachInstructions: String = AppStorage.DEFAULT_COACH_INSTRUCTIONS
) {
    val isConfigured: Boolean get() = apiKey.isNotBlank()
}

enum class TopicCategory(val label: String) {
    Politics("Politics"),
    Economy("Economy"),
    GeneralKnowledge("General knowledge"),
    LiberalArts("Liberal arts")
}

/** Quick Assist候補 (FR-005) */
data class AssistSuggestion(val english: String, val note: String)

/** 公式サイト等へのリンク集約 */
object AppLinks {
    const val SITE = "https://byokey-lab.com/speak/english/"
    const val PRIVACY_POLICY = "https://byokey-lab.com/privacy"
    const val SUPPORT = "https://byokey-lab.com/support"
    const val API_GUIDE = "https://byokey-lab.com/speak/english/"
    const val CONTACT_EMAIL = "byokey-lab@gmail.com"
}
