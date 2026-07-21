package com.byokeylab.speak

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.byokeylab.speak.data.AppDatabase
import com.byokeylab.speak.data.ChatMode
import com.byokeylab.speak.data.ChatRepository
import com.byokeylab.speak.data.ChatWithPreview
import com.byokeylab.speak.data.DailyStatEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class MainTab(val label: String) {
    Coach("Coach"),
    Chats("Chats"),
    Review("Review"),
    Progress("Progress"),
    Settings("Settings")
}

data class QuickAssistUiState(
    val visibleFor: Long? = null,
    val loading: Boolean = false,
    val suggestions: List<AssistSuggestion> = emptyList(),
    val error: String? = null,
    val rawResult: String? = null
)

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val storage = AppStorage(app)
    val repository = ChatRepository(AppDatabase.get(app))
    private val llm = LlmClient()

    var settings by mutableStateOf(storage.loadSettings())
        private set
    var onboardingDone by mutableStateOf(storage.onboardingDone)
        private set
    var currentTab by mutableStateOf(MainTab.Coach)
    var coachChatId by mutableStateOf<Long?>(null)
        private set

    /** Chatsタブで開いているチャット。nullなら一覧表示。 */
    var openQuickChatId by mutableStateOf<Long?>(null)
        private set

    var sendingChatId by mutableStateOf<Long?>(null)
        private set

    /** APIキー未設定のまま送信しようとしたときの誘導ダイアログ (FR-001) */
    var showSetupPrompt by mutableStateOf(false)

    var quickAssist by mutableStateOf(QuickAssistUiState())
        private set

    /**
     * チャットごとの送信前ドラフト (NFR-003: 応答待ちや失敗時も下書きを壊さない)。
     * Quick Assist・音声入力は appendToDraft 経由で必ず追記する (FR-005)。
     */
    private val drafts = androidx.compose.runtime.mutableStateMapOf<Long, String>()

    fun draftOf(chatId: Long): String = drafts[chatId] ?: ""

    fun setDraft(chatId: Long, text: String) {
        drafts[chatId] = text
    }

    fun appendToDraft(chatId: Long, addition: String) {
        drafts[chatId] = appendAssistText(draftOf(chatId), addition)
    }

    fun clearDraft(chatId: Long) {
        drafts.remove(chatId)
    }

    val quickChats: StateFlow<List<ChatWithPreview>> = repository.observeQuickChats()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val notes: StateFlow<List<LearningNote>> = repository.observeNotes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val dailyStats: StateFlow<List<DailyStatEntity>> = repository.observeDailyStats()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            if (!storage.legacyImported) {
                repository.importLegacy(storage.loadLegacyMessages(), storage.loadLegacyNotes())
                storage.clearLegacyConversationData()
                storage.legacyImported = true
            }
            coachChatId = repository.ensureCoachChat()
            val lastQuick = storage.lastOpenedQuickChatId
            if (lastQuick > 0 && repository.chatExists(lastQuick)) {
                openQuickChatId = lastQuick
            } else {
                openQuickChatId = repository.latestQuickChatId()
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
        storage.onboardingDone = true
        onboardingDone = true
        if (goToSettings) currentTab = MainTab.Settings
    }

    // --- 送信 (FR-003 / FR-004) ---

    fun send(chatId: Long, mode: ChatMode, text: String, onSent: () -> Unit) {
        val trimmed = text.trim()
        if (trimmed.isBlank() || sendingChatId != null) return
        if (!settings.isConfigured) {
            showSetupPrompt = true
            return
        }
        sendingChatId = chatId
        viewModelScope.launch {
            val context = repository.messagesOf(chatId)
            repository.appendMessage(chatId, MessageRole.User, trimmed)
            repository.recordTurn()
            onSent()
            val reply = runCatching {
                val prompt = when (mode) {
                    ChatMode.Coach -> PromptBuilder.buildPrompt(
                        context, trimmed, settings.englishLevel, settings.coachInstructions
                    )
                    ChatMode.Quick -> PromptBuilder.buildQuickChatPrompt(
                        context, trimmed, settings.englishLevel, settings.coachInstructions
                    )
                }
                llm.generate(settings.provider, settings.apiKey, settings.model, prompt)
            }.getOrElse { error ->
                val kind = (error as? LlmException)?.kind ?: LlmErrorKind.Unknown
                "Sorry, I couldn't reach the coach.\n\n日本語解説: ${kind.userMessage}"
            }
            repository.appendMessage(chatId, MessageRole.Coach, reply)
            LearningNoteExtractor.fromCoachResponse(reply, trimmed)?.let { note ->
                repository.saveNote(note, chatId)
            }
            sendingChatId = null
        }
    }

    fun startTopic(topic: String) {
        val chatId = coachChatId ?: return
        viewModelScope.launch {
            repository.appendMessage(chatId, MessageRole.System, "New topic: $topic")
        }
    }

    fun newCoachConversation() {
        viewModelScope.launch {
            coachChatId = repository.newCoachChat()
        }
    }

    // --- Chats (FR-004) ---

    fun openQuickChat(chatId: Long?) {
        openQuickChatId = chatId
        if (chatId != null) storage.lastOpenedQuickChatId = chatId
    }

    fun newQuickChat() {
        viewModelScope.launch {
            val id = repository.createQuickChat()
            openQuickChat(id)
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

    fun askQuickAssist(chatId: Long, currentDraft: String, stuckText: String) {
        if (stuckText.isBlank() || quickAssist.loading) return
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

    // --- Review (FR-007) ---

    fun setNoteReviewed(noteId: Long, reviewed: Boolean) {
        viewModelScope.launch { repository.setNoteReviewed(noteId, reviewed) }
    }

    fun deleteNote(noteId: Long) {
        viewModelScope.launch { repository.deleteNote(noteId) }
    }

    // --- Settings ---

    fun saveSettings(next: AppSettings) {
        settings = next
        storage.saveSettings(next)
        ReminderScheduler.apply(getApplication(), next)
    }

    suspend fun testConnection(provider: LlmProvider, apiKey: String, model: String): LlmException? {
        if (apiKey.isBlank()) {
            return LlmException(LlmErrorKind.MissingApiKey, LlmErrorKind.MissingApiKey.userMessage)
        }
        return llm.testConnection(provider, apiKey, model)
    }

    // --- データ削除 (FR-010) ---

    fun deleteApiKey() {
        storage.deleteApiKey()
        settings = settings.copy(apiKey = "")
    }

    fun deleteConversations() {
        viewModelScope.launch {
            repository.deleteAllConversations()
            coachChatId = repository.ensureCoachChat()
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
            settings = storage.loadSettings()
            onboardingDone = false
            coachChatId = repository.ensureCoachChat()
            openQuickChat(null)
            currentTab = MainTab.Coach
        }
    }
}
