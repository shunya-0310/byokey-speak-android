package com.byokeylab.speak

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.byokeylab.speak.data.AppDatabase
import com.byokeylab.speak.data.ChatMode
import com.byokeylab.speak.data.ChatRepository
import com.byokeylab.speak.data.ChatWithPreview
import com.byokeylab.speak.data.ConversationAnalysisParser
import com.byokeylab.speak.data.DailyStatEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class MainTab(val label: String) {
    Chats("Chats"),
    Review("Review"),
    Progress("Progress"),
    Settings("Settings")
}

data class QuickAssistUiState(
    val visibleFor: Long? = null,
    val question: String = "",
    val loading: Boolean = false,
    val suggestions: List<AssistSuggestion> = emptyList(),
    val error: String? = null,
    val rawResult: String? = null
)

data class CoachTranslationUiState(
    val visible: Boolean = false,
    val loading: Boolean = false,
    val text: String = "",
    val error: String? = null
)

data class ConversationAnalysisUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val message: String? = null
)

data class BackupUiState(
    val working: Boolean = false,
    val message: String? = null,
    val error: String? = null
)

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val storage = AppStorage(app)
    val repository = ChatRepository(AppDatabase.get(app))
    private val llm = LlmClient()
    private val dailyNewsClient = DailyNewsClient(app)
    private val backupManager = UserDataBackupManager(app, repository, storage)
    private val aiContentReportClient = AiContentReportClient()

    var settings by mutableStateOf(storage.loadSettings())
        private set
    var onboardingDone by mutableStateOf(storage.onboardingDone)
        private set
    var currentTab by mutableStateOf(MainTab.Chats)
    var coachChatId by mutableStateOf<Long?>(null)
        private set

    /** Chatsタブで開いているチャット。nullなら一覧表示。 */
    var openQuickChatId by mutableStateOf<Long?>(null)
        private set

    var sendingChatId by mutableStateOf<Long?>(null)
        private set

    var dailyNews by mutableStateOf(DailyNewsUiState())
        private set

    /** APIキー未設定のまま送信しようとしたときの誘導ダイアログ (FR-001) */
    var showSetupPrompt by mutableStateOf(false)

    /** 旧版でGemini以外を選んでいた場合の、一度だけ表示する安全な移行通知。 */
    var geminiOnlyMigrationNotice by mutableStateOf(storage.consumeGeminiOnlyKeyRemovedNotice())
        private set

    var showExternalDataConsent by mutableStateOf(
        onboardingDone && !storage.hasCurrentExternalDataConsent
    )
        private set

    val hasExternalDataConsent: Boolean
        get() = storage.hasCurrentExternalDataConsent

    var activeTutorial by mutableStateOf<AppTutorialKind?>(null)
        private set

    var activeTutorialStep by mutableIntStateOf(0)
        private set

    var showFeedbackPrompt by mutableStateOf(false)
        private set

    private var tutorialReplayQueue: List<AppTutorialKind> = emptyList()
    private var pendingTutorialOpen: AppTutorialKind? = null

    /** 読み上げ後に英語マイクを起動する任意の補助モード。 */
    var autoMode by mutableStateOf(ConversationAutoMode.Off)

    fun cycleAutoMode(): ConversationAutoMode {
        autoMode = autoMode.next()
        return autoMode
    }

    /** コーチ返信到着時にMainActivityが読み上げ+マイク起動を行うためのフック */
    var coachReplyHook: ((chatId: Long, naturalReply: String) -> Unit)? = null

    /** 返信の保存完了時にアプリ全体で受信音を鳴らすためのフック */
    var messageReceivedHook: (() -> Unit)? = null

    var quickAssist by mutableStateOf(QuickAssistUiState())
        private set

    private val coachTranslations = androidx.compose.runtime.mutableStateMapOf<Long, CoachTranslationUiState>()
    private val topicTranslations = androidx.compose.runtime.mutableStateMapOf<String, CoachTranslationUiState>()

    /**
     * チャットごとの送信前ドラフト (NFR-003: 応答待ちや失敗時も下書きを壊さない)。
     * Quick Assist・音声入力は appendToDraft 経由で必ず追記する (FR-005)。
     */
    private val drafts = androidx.compose.runtime.mutableStateMapOf<Long, String>()
    private val previousDrafts = androidx.compose.runtime.mutableStateMapOf<Long, String>()
    private val draftInputSources = androidx.compose.runtime.mutableStateMapOf<Long, MessageInputSource>()
    private val draftUsedQuickAssist = androidx.compose.runtime.mutableStateMapOf<Long, Boolean>()
    private val forcedWebSearch = androidx.compose.runtime.mutableStateMapOf<Long, Boolean>()

    fun draftOf(chatId: Long): String = drafts[chatId] ?: ""

    fun canUndoDraft(chatId: Long): Boolean = previousDrafts.containsKey(chatId)

    fun setDraft(chatId: Long, text: String) {
        updateDraft(chatId, text, MessageInputSource.TYPED)
    }

    fun appendToDraft(chatId: Long, addition: String) {
        updateDraft(chatId, appendAssistText(draftOf(chatId), addition), MessageInputSource.VOICE)
    }

    /** Quick Assist候補で下書きを全文差し替える(ユーザーが明示選択した場合のみ)。 */
    fun replaceDraft(chatId: Long, text: String) {
        updateDraft(chatId, text, MessageInputSource.QUICK_ASSIST, usedQuickAssist = true)
    }

    fun undoDraft(chatId: Long) {
        val previous = previousDrafts.remove(chatId) ?: return
        if (previous.isEmpty()) {
            drafts.remove(chatId)
            draftInputSources.remove(chatId)
            draftUsedQuickAssist.remove(chatId)
        } else {
            drafts[chatId] = previous
        }
    }

    fun clearDraft(chatId: Long) {
        drafts.remove(chatId)
        previousDrafts.remove(chatId)
        draftInputSources.remove(chatId)
        draftUsedQuickAssist.remove(chatId)
    }

    private fun updateDraft(
        chatId: Long,
        text: String,
        source: MessageInputSource,
        usedQuickAssist: Boolean = false
    ) {
        val current = draftOf(chatId)
        if (text == current) return
        previousDrafts[chatId] = current
        if (text.isEmpty()) {
            drafts.remove(chatId)
            draftInputSources.remove(chatId)
            draftUsedQuickAssist.remove(chatId)
        } else {
            drafts[chatId] = text
            draftInputSources[chatId] = mergeInputSource(draftInputSources[chatId], source)
            if (usedQuickAssist) draftUsedQuickAssist[chatId] = true
        }
    }

    fun isWebSearchForced(chatId: Long): Boolean = forcedWebSearch[chatId] == true

    fun toggleWebSearch(chatId: Long) {
        forcedWebSearch[chatId] = !isWebSearchForced(chatId)
    }

    val quickChats: StateFlow<List<ChatWithPreview>> = repository.observeQuickChats()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val coachChats: StateFlow<List<ChatWithPreview>> = repository.observeCoachChats()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val vocabCards: StateFlow<List<VocabCard>> = repository.observeVocabCards()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val userMessageTexts: StateFlow<List<String>> = repository.observeAllUserMessageTexts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val dailyStats: StateFlow<List<DailyStatEntity>> = repository.observeDailyStats()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val analyses: StateFlow<List<ConversationAnalysisRecord>> = repository.observeAnalyses()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var analysisState by mutableStateOf(ConversationAnalysisUiState())
        private set

    var backupState by mutableStateOf(BackupUiState())
        private set

    var showAnalysisConsent by mutableStateOf(false)

    init {
        ReminderScheduler.apply(getApplication(), settings)
        if (onboardingDone) refreshDailyNews()
        viewModelScope.launch {
            if (!storage.legacyImported) {
                repository.importLegacy(storage.loadLegacyMessages(), storage.loadLegacyNotes())
                storage.clearLegacyConversationData()
                storage.legacyImported = true
            }
            if (storage.isTutorialSeen(AppTutorialKind.Chats)) {
                val lastQuick = storage.lastOpenedQuickChatId
                if (lastQuick > 0 && repository.chatExists(lastQuick)) {
                    openQuickChatId = lastQuick
                } else {
                    openQuickChatId = repository.latestQuickChatId()
                }
            }
        }
    }

    fun messagesFlow(chatId: Long): Flow<List<ChatMessage>> = repository.observeMessages(chatId)

    fun streakOf(stats: List<DailyStatEntity>): Int {
        val days = stats.filter { it.turns > 0 }.map { it.epochDay }.toSet()
        return StudyStats.streak(days, LocalDate.now().toEpochDay())
    }

    // --- Onboarding ---

    fun completeOnboarding(goToSettings: Boolean) {
        storage.recordExternalDataConsent()
        storage.onboardingDone = true
        onboardingDone = true
        showExternalDataConsent = false
        refreshDailyNews()
        if (goToSettings) currentTab = MainTab.Settings
    }

    // --- 送信 (FR-003 / FR-004) ---

    fun send(chatId: Long, mode: ChatMode, text: String, onSent: () -> Unit) {
        val trimmed = text.trim()
        if (trimmed.isBlank() || sendingChatId != null) return
        if (!ensureExternalDataConsent()) return
        if (!settings.isConfigured) {
            showSetupPrompt = true
            return
        }
        sendingChatId = chatId
        viewModelScope.launch {
            val context = repository.messagesOf(chatId)
            val webSearchEnabled =
                isWebSearchForced(chatId) || WebSearchPolicy.shouldSearch(context, trimmed)
            val inputSource = draftInputSources[chatId] ?: MessageInputSource.TYPED
            val usedQuickAssist = draftUsedQuickAssist[chatId] == true
            repository.appendMessage(
                chatId,
                MessageRole.User,
                trimmed,
                inputSource = inputSource,
                usedQuickAssist = usedQuickAssist
            )
            repository.recordTurn()
            forcedWebSearch.remove(chatId)
            onSent()
            val generated = runCatching {
                val prompt = PromptBuilder.buildConversationPrompt(
                    context,
                    trimmed,
                    settings.englishLevel,
                    settings.coachInstructions,
                    webSearchEnabled
                )
                val result = runCatching {
                    llm.generateDetailed(
                        settings.provider,
                        settings.apiKey,
                        settings.model,
                        prompt,
                        enableWebSearch = webSearchEnabled
                    )
                }.getOrElse { searchError ->
                    if (!webSearchEnabled) throw searchError
                    val fallbackPrompt = PromptBuilder.buildConversationPrompt(
                        context,
                        trimmed,
                        settings.englishLevel,
                        settings.coachInstructions,
                        webSearchEnabled = false
                    )
                    val fallback = llm.generateDetailed(
                        settings.provider,
                        settings.apiKey,
                        settings.model,
                        fallbackPrompt
                    )
                    fallback.copy(
                        searchAttempted = true
                    )
                }
                result
            }.getOrElse { error ->
                val kind = (error as? LlmException)?.kind ?: LlmErrorKind.Unknown
                LlmResult("Sorry, I couldn't reach the coach.\n\n日本語解説: ${kind.userMessage}")
            }
            // 単語帳行(Vocab:)を抽出し、表示テキストからは取り除く
            val extracted = VocabExtractor.extract(generated.text)
            val cleanText = extracted.cleanText.ifBlank { generated.text }
            val displayText = generated.copy(text = cleanText).textWithSources()
            repository.appendMessage(chatId, MessageRole.Coach, displayText)
            messageReceivedHook?.invoke()
            val source = VocabSource.Chats
            extracted.vocab.forEach { draft ->
                repository.saveVocabCard(draft.expression, draft.meaning, source, chatId)
            }
            sendingChatId = null
            if (autoMode.startsMicAfterReading) {
                coachReplyHook?.invoke(chatId, StudyStats.naturalReplyOf(displayText))
            }
        }
    }

    fun startTopic(topic: String) {
        viewModelScope.launch {
            val id = repository.createTopicChat(topic)
            openQuickChat(id)
            currentTab = MainTab.Chats
        }
    }

    fun newCoachConversation() {
        viewModelScope.launch {
            coachChatId = repository.newCoachChat()
        }
    }

    /** Coach履歴カードから過去の会話を開く。 */
    fun openCoachChat(chatId: Long) {
        coachChatId = chatId
    }

    // --- Chats (FR-004) ---

    fun refreshDailyNews(manual: Boolean = false) {
        dailyNews = dailyNews.copy(loading = true, error = null, notice = null)
        viewModelScope.launch {
            val loaded = dailyNewsClient.load()
            dailyNews = if (manual) {
                loaded.copy(notice = DailyNewsClient.DAILY_NEWS_SCHEDULE_NOTICE)
            } else {
                loaded
            }
        }
    }

    fun startNewsChat(item: DailyNewsItem) {
        viewModelScope.launch {
            val id = repository.createNewsChat(
                title = item.headline,
                hiddenContext = NewsConversation.hiddenContext(item),
                coachOpener = NewsConversation.visibleOpener(item, settings.coachInstructions)
            )
            openQuickChat(id)
            maybeShowTutorial(AppTutorialKind.ChatControls)
        }
    }

    fun openQuickChat(chatId: Long?) {
        openQuickChatId = chatId
        if (chatId != null) storage.lastOpenedQuickChatId = chatId
    }

    fun newQuickChat() {
        viewModelScope.launch {
            val id = repository.createQuickChat()
            openQuickChat(id)
            maybeShowTutorial(AppTutorialKind.ChatControls)
        }
    }

    fun renameChat(chatId: Long, title: String) {
        viewModelScope.launch { repository.renameChat(chatId, title) }
    }

    fun setPinned(chatId: Long, pinned: Boolean) {
        viewModelScope.launch { repository.setPinned(chatId, pinned) }
    }

    fun deleteChat(chatId: Long) {
        viewModelScope.launch {
            repository.deleteChat(chatId)
            if (openQuickChatId == chatId) openQuickChat(null)
        }
    }

    // --- Quick Assist (FR-005) ---

    fun openQuickAssist(chatId: Long) {
        quickAssist = QuickAssistUiState(visibleFor = chatId)
    }

    fun closeQuickAssist() {
        quickAssist = QuickAssistUiState()
    }

    fun updateAssistQuestion(text: String) {
        quickAssist = quickAssist.copy(question = text)
    }

    /** 音声入力はQuick Assistの質問欄にも追記できる。 */
    fun appendToAssistQuestion(addition: String) {
        quickAssist = quickAssist.copy(question = appendAssistText(quickAssist.question, addition))
    }

    fun askQuickAssist(chatId: Long, currentDraft: String, stuckText: String) {
        if (stuckText.isBlank() || quickAssist.loading) return
        if (!ensureExternalDataConsent()) return
        if (!settings.isConfigured) {
            showSetupPrompt = true
            return
        }
        quickAssist = quickAssist.copy(loading = true, error = null, suggestions = emptyList(), rawResult = null)
        viewModelScope.launch {
            repository.recordAssistUse()
            val context = repository.messagesOf(chatId)
            runCatching {
                val prompt = PromptBuilder.buildQuickAssistPrompt(
                    context, currentDraft, stuckText, settings.englishLevel, settings.coachInstructions
                )
                llm.generate(settings.provider, settings.apiKey, settings.model, prompt)
            }.fold(
                onSuccess = { result ->
                    val parsed = AssistSuggestionParser.parse(result)
                    quickAssist = quickAssist.copy(
                        loading = false,
                        suggestions = parsed,
                        rawResult = if (parsed.isEmpty()) result else null
                    )
                },
                onFailure = { error ->
                    val kind = (error as? LlmException)?.kind ?: LlmErrorKind.Unknown
                    quickAssist = quickAssist.copy(loading = false, error = kind.userMessage)
                }
            )
        }
    }

    /** Quick Assist候補を採用したとき、その表現を単語帳にも保存する。 */
    fun adoptAssistSuggestion(chatId: Long, suggestion: AssistSuggestion, replace: Boolean) {
        if (replace) {
            replaceDraft(chatId, suggestion.english)
        } else {
            updateDraft(
                chatId,
                appendAssistText(draftOf(chatId), suggestion.english),
                MessageInputSource.QUICK_ASSIST,
                usedQuickAssist = true
            )
        }
        viewModelScope.launch {
            repository.saveVocabCard(suggestion.english, suggestion.note, VocabSource.QuickAssist, chatId)
        }
        closeQuickAssist()
    }

    // --- Coach返信の日本語訳 ---

    fun coachTranslationOf(messageId: Long): CoachTranslationUiState {
        return coachTranslations[messageId] ?: CoachTranslationUiState()
    }

    fun toggleCoachTranslation(message: ChatMessage) {
        if (message.role != MessageRole.Coach) return
        val current = coachTranslationOf(message.id)
        if (current.text.isNotBlank() || current.error != null) {
            coachTranslations[message.id] = current.copy(visible = !current.visible)
            return
        }
        if (current.loading) return
        if (!ensureExternalDataConsent()) return
        if (!settings.isConfigured) {
            showSetupPrompt = true
            return
        }
        coachTranslations[message.id] = current.copy(visible = true, loading = true, error = null)
        viewModelScope.launch {
            runCatching {
                val prompt = PromptBuilder.buildCoachTranslationPrompt(message.text)
                llm.generate(settings.provider, settings.apiKey, settings.model, prompt)
            }.fold(
                onSuccess = { result ->
                    coachTranslations[message.id] = CoachTranslationUiState(
                        visible = true,
                        loading = false,
                        text = result.trim()
                    )
                },
                onFailure = { error ->
                    val kind = (error as? LlmException)?.kind ?: LlmErrorKind.Unknown
                    coachTranslations[message.id] = CoachTranslationUiState(
                        visible = true,
                        loading = false,
                        error = kind.userMessage
                    )
                }
            )
        }
    }

    fun topicTranslationOf(topic: String): CoachTranslationUiState {
        return topicTranslations[topic] ?: CoachTranslationUiState()
    }

    fun toggleTopicTranslation(topic: String) {
        val current = topicTranslationOf(topic)
        if (current.text.isNotBlank() || current.error != null) {
            topicTranslations[topic] = current.copy(visible = !current.visible)
            return
        }
        if (current.loading) return
        if (!ensureExternalDataConsent()) return
        if (!settings.isConfigured) {
            showSetupPrompt = true
            return
        }
        topicTranslations[topic] = current.copy(visible = true, loading = true, error = null)
        viewModelScope.launch {
            runCatching {
                val prompt = PromptBuilder.buildTopicTranslationPrompt(topic)
                llm.generate(settings.provider, settings.apiKey, settings.model, prompt)
            }.fold(
                onSuccess = { result ->
                    topicTranslations[topic] = CoachTranslationUiState(
                        visible = true,
                        text = result.trim()
                    )
                },
                onFailure = { error ->
                    val kind = (error as? LlmException)?.kind ?: LlmErrorKind.Unknown
                    topicTranslations[topic] = CoachTranslationUiState(
                        visible = true,
                        error = kind.userMessage
                    )
                }
            )
        }
    }

    fun submitAiContentReport(
        coachMessage: String,
        precedingUserMessage: String?,
        reason: AiReportReason,
        comment: String,
        includeUserMessage: Boolean,
        onResult: (Result<Unit>) -> Unit
    ) {
        viewModelScope.launch {
            val result = runCatching {
                aiContentReportClient.submit(
                    coachMessage = coachMessage,
                    reason = reason,
                    comment = comment,
                    precedingUserMessage = precedingUserMessage.takeIf { includeUserMessage },
                    provider = settings.provider.name,
                    model = settings.model
                )
            }
            onResult(result)
        }
    }

    // --- Review: 単語帳 (FR-007) ---

    fun setVocabFavorite(cardId: Long, favorite: Boolean) {
        viewModelScope.launch { repository.setVocabFavorite(cardId, favorite) }
    }

    fun saveManualVocabCard(expression: String, meaning: String) {
        val normalizedExpression = expression.trim()
        if (normalizedExpression.isBlank()) return
        viewModelScope.launch {
            repository.saveVocabCard(
                expression = normalizedExpression,
                meaning = meaning.trim(),
                source = VocabSource.Manual,
                chatId = null
            )
        }
    }

    fun maybeShowTutorial(kind: AppTutorialKind) {
        if (activeTutorial != null || pendingTutorialOpen != null || !onboardingDone || storage.isTutorialSeen(kind)) return
        activeTutorialStep = 0
        when (kind) {
            AppTutorialKind.Chats -> {
                currentTab = MainTab.Chats
                openQuickChat(null)
            }
            AppTutorialKind.ChatControls -> {
                currentTab = MainTab.Chats
                if (openQuickChatId == null) {
                    pendingTutorialOpen = kind
                    viewModelScope.launch {
                        val chatId = repository.latestQuickChatId() ?: repository.createQuickChat()
                        openQuickChat(chatId)
                        pendingTutorialOpen = null
                        activeTutorial = kind
                    }
                    return
                }
            }
            AppTutorialKind.Vocabulary -> currentTab = MainTab.Review
            AppTutorialKind.Progress -> currentTab = MainTab.Progress
            AppTutorialKind.Settings -> currentTab = MainTab.Settings
        }
        activeTutorial = kind
    }

    fun updateTutorialStep(step: Int) {
        activeTutorialStep = step
    }

    fun maybeShowProgressTutorial(userMessageCount: Int) {
        if (userMessageCount < 20) return
        maybeShowTutorial(AppTutorialKind.Progress)
    }

    fun dismissTutorial() {
        val dismissed = activeTutorial
        dismissed?.let(storage::markTutorialSeen)
        activeTutorial = null
        activeTutorialStep = 0
        if (tutorialReplayQueue.isNotEmpty()) {
            val next = tutorialReplayQueue.first()
            tutorialReplayQueue = tutorialReplayQueue.drop(1)
            maybeShowTutorial(next)
        }
    }

    fun dismissFeedbackPrompt() {
        showFeedbackPrompt = false
    }

    private fun showFeedbackPromptAfterAnalysis() {
        if (storage.feedbackPromptShown) return
        storage.feedbackPromptShown = true
        viewModelScope.launch {
            delay(2_000)
            if (activeTutorial == null) {
                showFeedbackPrompt = true
            }
        }
    }

    fun replayTutorials() {
        storage.resetTutorials()
        tutorialReplayQueue = listOf(
            AppTutorialKind.ChatControls,
            AppTutorialKind.Vocabulary,
            AppTutorialKind.Progress,
            AppTutorialKind.Settings
        )
        maybeShowTutorial(AppTutorialKind.Chats)
    }

    // --- Conversation Analysis ---

    fun requestConversationAnalysis() {
        if (!ensureExternalDataConsent()) return
        if (!settings.isConfigured) {
            showSetupPrompt = true
            return
        }
        if (!storage.analysisConsentDone) {
            showAnalysisConsent = true
            return
        }
        runConversationAnalysis()
    }

    fun confirmConversationAnalysisConsent() {
        storage.analysisConsentDone = true
        showAnalysisConsent = false
        runConversationAnalysis()
    }

    fun dismissConversationAnalysisConsent() {
        showAnalysisConsent = false
    }

    fun startPracticeFromAnalysis(prompt: String) {
        if (prompt.isBlank()) return
        viewModelScope.launch {
            val id = repository.createTopicChat(prompt)
            openQuickChat(id)
            currentTab = MainTab.Chats
        }
    }

    private fun runConversationAnalysis() {
        if (analysisState.loading) return
        analysisState = ConversationAnalysisUiState(loading = true)
        viewModelScope.launch {
            val input = repository.analysisInput()
            if (input.messages.size < 20) {
                analysisState = ConversationAnalysisUiState(
                    message = "分析にはあと${20 - input.messages.size}件の発話が必要です。"
                )
                return@launch
            }
            val latestAnalysis = repository.latestAnalysis()
            val latestUserAt = repository.latestUserMessageAt()
            if (latestAnalysis != null && latestUserAt != null && latestUserAt <= latestAnalysis.createdAt) {
                analysisState = ConversationAnalysisUiState(message = "前回の分析以降、十分な新しい発話がありません。")
                return@launch
            }
            runCatching {
                val prompt = PromptBuilder.buildConversationAnalysisPrompt(
                    input.context,
                    settings.englishLevel,
                    settings.coachInstructions
                )
                val raw = llm.generate(settings.provider, settings.apiKey, settings.model, prompt)
                val normalized = ConversationAnalysisParser.parse(raw)
                    .map { raw }
                    .getOrElse {
                        val repairPrompt = PromptBuilder.buildAnalysisRepairPrompt(raw)
                        val repaired = llm.generate(settings.provider, settings.apiKey, settings.model, repairPrompt)
                        ConversationAnalysisParser.parse(repaired).getOrThrow()
                        repaired
                    }
                repository.saveAnalysis(
                    periodStart = input.periodStart,
                    periodEnd = input.periodEnd,
                    userMessageCount = input.messages.size,
                    provider = settings.provider.name,
                    model = settings.model,
                    resultJson = normalized
                )
            }.fold(
                onSuccess = {
                    analysisState = ConversationAnalysisUiState(message = "会話分析を保存しました。")
                    showFeedbackPromptAfterAnalysis()
                },
                onFailure = { error ->
                    val kind = (error as? LlmException)?.kind ?: LlmErrorKind.Unknown
                    analysisState = ConversationAnalysisUiState(error = kind.userMessage)
                }
            )
        }
    }

    fun deleteVocabCard(cardId: Long) {
        viewModelScope.launch { repository.deleteVocabCard(cardId) }
    }

    // --- Settings ---

    fun saveSettings(next: AppSettings) {
        settings = next
        storage.saveSettings(next)
        ReminderScheduler.apply(getApplication(), next)
    }

    fun exportBackup(uri: Uri, passphrase: CharArray) {
        if (backupState.working) return
        backupState = BackupUiState(working = true)
        viewModelScope.launch {
            runCatching { backupManager.exportTo(uri, passphrase) }.fold(
                onSuccess = { result ->
                    backupState = BackupUiState(
                        message = "${result.recordCount}件のデータをバックアップしました。"
                    )
                },
                onFailure = { error ->
                    backupState = BackupUiState(
                        error = error.message ?: "バックアップを作成できませんでした。"
                    )
                }
            )
        }
    }

    fun restoreBackup(uri: Uri, passphrase: CharArray) {
        if (backupState.working) return
        backupState = BackupUiState(working = true)
        viewModelScope.launch {
            runCatching { backupManager.restoreFrom(uri, passphrase) }.fold(
                onSuccess = { result ->
                    settings = storage.loadSettings()
                    ReminderScheduler.apply(getApplication(), settings)
                    openQuickChat(null)
                    backupState = BackupUiState(
                        message = "${result.recordCount}件のデータを復元しました。APIキーはバックアップ対象外です。新しい端末では再入力してください。"
                    )
                },
                onFailure = { error ->
                    backupState = BackupUiState(
                        error = error.message ?: "バックアップを復元できませんでした。"
                    )
                }
            )
        }
    }

    fun clearBackupMessage() {
        if (!backupState.working) backupState = BackupUiState()
    }

    suspend fun testConnection(provider: LlmProvider, apiKey: String, model: String): LlmException? {
        if (!storage.hasCurrentExternalDataConsent) {
            showExternalDataConsent = true
            return LlmException(LlmErrorKind.Unknown, "外部AIへのデータ送信に同意してください。")
        }
        if (apiKey.isBlank()) {
            return LlmException(LlmErrorKind.MissingApiKey, LlmErrorKind.MissingApiKey.userMessage)
        }
        return llm.testConnection(provider, apiKey, model)
    }

    fun confirmExternalDataConsent() {
        storage.recordExternalDataConsent()
        showExternalDataConsent = false
    }

    fun dismissExternalDataConsent() {
        showExternalDataConsent = false
    }

    fun dismissGeminiOnlyMigrationNotice() {
        geminiOnlyMigrationNotice = false
    }

    private fun ensureExternalDataConsent(): Boolean {
        if (storage.hasCurrentExternalDataConsent) return true
        showExternalDataConsent = true
        return false
    }

    // --- データ削除 (FR-010) ---

    fun deleteApiKey() {
        storage.deleteApiKey()
        settings = settings.copy(apiKey = "")
    }

    fun deleteConversations() {
        viewModelScope.launch {
            repository.deleteAllConversations()
            openQuickChat(null)
        }
    }

    fun deleteNotes() {
        viewModelScope.launch { repository.deleteAllNotes() }
    }

    fun deleteProgress() {
        viewModelScope.launch { repository.deleteAllProgress() }
    }

    fun deleteEverything() {
        viewModelScope.launch {
            repository.deleteEverything()
            storage.clearAllPreferences()
            GeminiTtsPlayer.clearCache(getApplication())
            settings = storage.loadSettings()
            onboardingDone = false
            openQuickChat(null)
            currentTab = MainTab.Chats
        }
    }
}

private fun mergeInputSource(current: MessageInputSource?, next: MessageInputSource): MessageInputSource {
    if (current == null || current == next) return next
    if (current == MessageInputSource.UNKNOWN) return next
    if (next == MessageInputSource.UNKNOWN) return current
    return MessageInputSource.MIXED
}
