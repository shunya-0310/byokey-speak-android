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
    val inputSource: MessageInputSource = MessageInputSource.UNKNOWN,
    val usedQuickAssist: Boolean = false,
    val timestampMillis: Long
)

enum class MessageInputSource {
    NONE,
    UNKNOWN,
    TYPED,
    VOICE,
    QUICK_ASSIST,
    MIXED
}

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
    Gemini("Gemini", "gemini-3.1-flash-lite"),
    OpenAI("OpenAI", "gpt-5.4-mini"),
    Anthropic("Claude", "claude-haiku-4-5-20251001")
}

/** 設定画面に表示するモデル候補。IDはタップで選択、コピーも可能。 */
data class ModelOption(
    val id: String,
    val note: String,
    val recommended: Boolean = false
)

val LlmProvider.modelOptions: List<ModelOption>
    get() = when (this) {
        LlmProvider.Gemini -> listOf(
            ModelOption("gemini-3.1-flash-lite", "コスパ重視。日常会話に十分", recommended = true),
            ModelOption("gemini-3.5-flash", "よりインテリジェント。3.1-flash-liteの約6倍の費用")
        )
        LlmProvider.OpenAI -> listOf(
            ModelOption("gpt-5.4-mini", "コスパと会話品質のバランス", recommended = true),
            ModelOption("gpt-5.4-nano", "最安・高速。シンプルな会話向け"),
            ModelOption("gpt-5.5", "フラッグシップ。高品質だが高価格")
        )
        LlmProvider.Anthropic -> listOf(
            ModelOption("claude-haiku-4-5-20251001", "高速・低価格。会話向け", recommended = true),
            ModelOption("claude-sonnet-5", "バランス型の高性能。費用は高め")
        )
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
        "STRICT LEVEL RULES (A1): Use only the ~1000 most common English words. " +
            "Keep every sentence under 8 words. One idea per sentence. Ask exactly one very simple question per reply. " +
            "No idioms, no slang, no phrasal verbs beyond the most basic (get up, go out). " +
            "Add a short Japanese hint for any word the learner might not know."
    ),
    A2(
        "A2",
        "STRICT LEVEL RULES (A2): Use only common everyday vocabulary (~2000 words). " +
            "Keep sentences under 12 words. Connect ideas with and/but/because only. " +
            "Introduce at most one new useful expression per reply, with a brief Japanese note. Avoid idioms and slang."
    ),
    B1(
        "B1",
        "STRICT LEVEL RULES (B1): Use natural everyday English but avoid rare words and complex clause chains. " +
            "Sentences up to ~15 words. Common idioms are OK if you briefly explain them. " +
            "Ask questions that invite reasons, experiences, and opinions in connected sentences."
    ),
    B2(
        "B2",
        "STRICT LEVEL RULES (B2): Use fluent conversational English with nuance and common idioms. " +
            "Do not simplify grammar, but avoid literary or highly technical vocabulary. " +
            "Challenge the learner to make arguments, compare options, and respond spontaneously."
    ),
    C1(
        "C1",
        "STRICT LEVEL RULES (C1): Use flexible, precise, native-level English including less common vocabulary. " +
            "Discuss abstract topics. Point out register (formal/casual) and subtle nuance when useful."
    ),
    C2(
        "C2",
        "STRICT LEVEL RULES (C2): Use highly natural, precise English with no simplification at all. " +
            "Focus feedback on subtle tone, style, rhythm, and idiomatic choice at a native level."
    )
}

enum class AppTheme(val label: String) {
    Dark("ダーク"),
    Light("ライト")
}

enum class VoiceGender(val label: String) {
    Female("女性"),
    Male("男性")
}

enum class ConversationAutoMode {
    Off,
    ManualSend,
    FullAuto;

    fun next(): ConversationAutoMode = when (this) {
        Off -> ManualSend
        ManualSend -> FullAuto
        FullAuto -> Off
    }

    val startsMicAfterReading: Boolean
        get() = this != Off
}

data class AppSettings(
    val provider: LlmProvider = LlmProvider.Gemini,
    val apiKey: String,
    val model: String,
    val dailyReminderEnabled: Boolean,
    val reminderHour: Int,
    val reminderMinute: Int,
    val englishLevel: EnglishLevel = EnglishLevel.A1,
    val coachInstructions: String = AppStorage.DEFAULT_COACH_INSTRUCTIONS,
    val theme: AppTheme = AppTheme.Dark,
    val voiceGender: VoiceGender = VoiceGender.Female,
    val soundEffectsEnabled: Boolean = true
) {
    val isConfigured: Boolean get() = apiKey.isNotBlank()
}

/** 単語帳の出所 */
enum class VocabSource(val label: String) {
    Coach("Coach"),
    Chats("Chat"),
    Manual("Manual"),
    QuickAssist("Assist")
}

enum class AppTutorialKind {
    Chats,
    ChatControls,
    Vocabulary,
    Progress,
    Settings
}

enum class ConversationOrigin(val label: String) {
    FREE_CHAT("Free chat"),
    TOPIC("Topic"),
    DAILY_NEWS("Daily News"),
    MIGRATED_COACH("Coach history")
}

/** 単語帳カード (Review画面) */
data class VocabCard(
    val id: Long,
    val expression: String,
    val meaning: String,
    val source: VocabSource,
    val favorite: Boolean,
    val timestampMillis: Long
)

data class AnalysisPoint(
    val title: String,
    val evidence: List<String> = emptyList(),
    val comment: String = ""
)

data class AnalysisExample(
    val original: String,
    val suggestion: String,
    val explanationJa: String
)

data class RecurringPattern(
    val category: String,
    val title: String,
    val occurrences: Int,
    val examples: List<AnalysisExample>,
    val nextAction: String
)

data class ConversationAnalysis(
    val summary: String,
    val strengths: List<AnalysisPoint>,
    val recurringPatterns: List<RecurringPattern>,
    val improvements: List<AnalysisPoint>,
    val nextFocus: List<String>,
    val practicePrompts: List<String>,
    val estimatedCefr: String = "",
    val cefrRationale: String = "",
    val levelUpPlan: List<String> = emptyList()
)

data class ConversationAnalysisRecord(
    val id: Long,
    val createdAt: Long,
    val periodStart: Long,
    val periodEnd: Long,
    val userMessageCount: Int,
    val provider: String,
    val model: String,
    val result: ConversationAnalysis
)

enum class TopicCategory(val label: String) {
    DailyLife("Daily life"),
    Business("Business"),
    Travel("Travel"),
    ScienceTech("Science & Tech"),
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
    const val TERMS_OF_USE = "https://byokey-lab.com/terms/"
    const val SUPPORT = "https://byokey-lab.com/support"
    const val API_GUIDE = "https://byokey-lab.com/guide/api/"

    /** 料金シミュレーションつきのアプリ公式ページ */
    const val PRICING_SIMULATOR = "https://byokey-lab.com/speak/english/"
    const val CONTACT_FORM =
        "https://docs.google.com/forms/d/e/1FAIpQLSf14ucq_SU36hxEQSsw0W5eBJ1WVp7PYjCaaEHu9GKRWyWQVw/viewform?usp=publish-editor"
}
