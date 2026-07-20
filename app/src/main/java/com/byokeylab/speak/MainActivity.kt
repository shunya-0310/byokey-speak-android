package com.byokeylab.speak

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val initialTopic = intent.getStringExtra(EXTRA_TOPIC)

        val notificationPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) {}

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            BYOKeySpeakRoot(initialTopic = initialTopic)
        }
    }

    companion object {
        const val EXTRA_TOPIC = "morning_topic"
    }
}

@Composable
private fun BYOKeySpeakRoot(initialTopic: String?) {
    val context = LocalContext.current
    val storage = remember { AppStorage(context) }
    val llmClient = remember { LlmClient() }
    val scope = rememberCoroutineScope()
    val messages = remember { mutableStateListOf<ChatMessage>() }
    val notes = remember { mutableStateListOf<LearningNote>() }
    val quickMessages = remember { mutableStateListOf<ChatMessage>() }

    var settings by remember { mutableStateOf(storage.loadSettings()) }
    var currentTopic by rememberSaveable {
        mutableStateOf(initialTopic ?: TopicRepository.topicForToday())
    }
    var selectedPracticeTab by rememberSaveable { mutableStateOf(PracticeTab.Chat) }
    var selectedCategory by rememberSaveable { mutableStateOf(TopicCategory.Economy) }
    var input by rememberSaveable { mutableStateOf("") }
    var quickStarterIndex by rememberSaveable { mutableStateOf(0) }
    var quickInput by rememberSaveable { mutableStateOf("") }
    var isQuickSending by rememberSaveable { mutableStateOf(false) }
    var isSending by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(settings.apiKey.isBlank()) }
    var topicPanelVisible by rememberSaveable { mutableStateOf(true) }
    var composerFocused by rememberSaveable { mutableStateOf(false) }
    var showNotes by rememberSaveable { mutableStateOf(false) }
    var streak by rememberSaveable { mutableStateOf(storage.loadStreak()) }

    // 日本語ヘルプ
    var showJapaneseHelp by rememberSaveable { mutableStateOf(false) }
    var assistTarget by rememberSaveable { mutableStateOf(InputTarget.MainChat) }
    var speechTarget by rememberSaveable { mutableStateOf(InputTarget.MainChat) }
    var jpHelpResult by rememberSaveable { mutableStateOf<String?>(null) }
    var jpHelpLoading by rememberSaveable { mutableStateOf(false) }

    // 読み上げ（TTS）
    val ttsRef = remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(Unit) {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                engine?.language = Locale.US
                ttsRef.value = engine
            }
        }
        onDispose {
            engine?.stop()
            engine?.shutdown()
            ttsRef.value = null
        }
    }

    fun speak(text: String) {
        ttsRef.value?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "coach_reply")
    }

    LaunchedEffect(Unit) {
        val saved = storage.loadMessages()
        messages.clear()
        messages.addAll(saved)
        notes.clear()
        notes.addAll(storage.loadNotes())
        if (quickMessages.isEmpty()) {
            quickMessages.add(coachMessage(quickStarterLines[quickStarterIndex]))
        }
        if (saved.isEmpty()) {
            messages.add(systemMessage("Today's topic: $currentTopic"))
            storage.saveMessages(messages)
        } else if (!initialTopic.isNullOrBlank()) {
            messages.add(systemMessage("Today's topic: $initialTopic"))
            storage.saveMessages(messages)
        }
        ReminderScheduler.apply(context, settings)
    }

    fun saveMessages() {
        storage.saveMessages(messages)
    }

    fun saveNotes() {
        storage.saveNotes(notes)
    }

    fun changeTopic(category: TopicCategory = selectedCategory) {
        selectedCategory = category
        currentTopic = TopicRepository.randomTopic(category)
        topicPanelVisible = true
        messages.add(systemMessage("New ${category.label.lowercase()} topic: $currentTopic"))
        saveMessages()
    }

    fun sendMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank() || isSending) return
        if (settings.apiKey.isBlank()) {
            showSettings = true
            return
        }

        val promptContext = messages.toList()
        messages.add(userMessage(trimmed))
        input = ""
        isSending = true
        saveMessages()
        streak = storage.recordStudySession()

        scope.launch {
            val coachText = runCatching {
                val prompt = PromptBuilder.buildPrompt(
                    promptContext,
                    trimmed,
                    settings.englishLevel,
                    settings.coachInstructions
                )
                llmClient.generate(settings.provider, settings.apiKey, settings.model, prompt)
            }.getOrElse { error ->
                "API request failed.\n\n日本語解説: ${error.message ?: "原因不明のエラーです。APIキー、通信状態、モデル名を確認してください。"}"
            }
            messages.add(coachMessage(coachText))
            LearningNoteExtractor.fromCoachResponse(coachText, trimmed)?.let { note ->
                notes.add(0, note)
                saveNotes()
            }
            isSending = false
            saveMessages()
        }
    }

    fun askJapaneseHelp(japaneseText: String) {
        if (japaneseText.isBlank() || jpHelpLoading) return
        if (settings.apiKey.isBlank()) {
            showSettings = true
            return
        }
        jpHelpLoading = true
        jpHelpResult = null
        scope.launch {
            jpHelpResult = runCatching {
                val prompt = PromptBuilder.buildJapaneseHelpPrompt(
                    japaneseText,
                    settings.englishLevel,
                    settings.coachInstructions
                )
                llmClient.generate(settings.provider, settings.apiKey, settings.model, prompt)
            }.getOrElse { error ->
                "取得に失敗しました: ${error.message ?: "APIキーや通信状態を確認してください。"}"
            }
            jpHelpLoading = false
        }
    }

    fun startNewQuickChat() {
        quickStarterIndex = (quickStarterIndex + 1) % quickStarterLines.size
        quickMessages.clear()
        quickMessages.add(coachMessage(quickStarterLines[quickStarterIndex]))
        quickInput = ""
    }

    fun sendQuickReply(reply: String) {
        val trimmed = reply.trim()
        if (trimmed.isBlank() || isQuickSending) return
        if (settings.apiKey.isBlank()) {
            showSettings = true
            return
        }

        val promptContext = quickMessages.toList()
        quickMessages.add(userMessage(trimmed))
        quickInput = ""
        isQuickSending = true
        streak = storage.recordStudySession()

        scope.launch {
            val coachText = runCatching {
                val prompt = PromptBuilder.buildQuickChatPrompt(
                    promptContext,
                    trimmed,
                    settings.englishLevel,
                    settings.coachInstructions
                )
                llmClient.generate(settings.provider, settings.apiKey, settings.model, prompt)
            }.getOrElse { error ->
                "I couldn't reach the coach just now. Try again in a moment.\nNative note: ${error.message ?: "Check your API key, model name, or network connection."}"
            }
            quickMessages.add(coachMessage(coachText))
            isQuickSending = false
        }
    }

    val speechLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                .orEmpty()
            if (spoken.isNotBlank()) {
                when (speechTarget) {
                    InputTarget.MainChat -> input = appendAssistText(input, spoken)
                    InputTarget.QuickChat -> quickInput = appendAssistText(quickInput, spoken)
                }
            }
        }
    }

    GrimoireTheme {
        EnglishCoachScreen(
            settings = settings,
            messages = messages,
            notes = notes,
            quickMessages = quickMessages,
            input = input,
            quickInput = quickInput,
            isQuickSending = isQuickSending,
            isSending = isSending,
            currentTopic = currentTopic,
            selectedPracticeTab = selectedPracticeTab,
            selectedCategory = selectedCategory,
            showSettings = showSettings,
            showNotes = showNotes,
            topicPanelVisible = topicPanelVisible,
            composerFocused = composerFocused,
            streak = streak,
            onPracticeTabChange = { selectedPracticeTab = it },
            onInputChange = { input = it },
            onQuickInputChange = { quickInput = it },
            onSend = { sendMessage(input) },
            onQuickReply = { reply -> sendQuickReply(reply) },
            onQuickRefresh = { startNewQuickChat() },
            onClearQuick = {
                quickMessages.clear()
                quickMessages.add(coachMessage(quickStarterLines[quickStarterIndex]))
                quickInput = ""
                isQuickSending = false
            },
            onStartTopic = {
                input = "I think "
                topicPanelVisible = false
            },
            onRefreshTopic = { changeTopic() },
            onCategoryChange = { changeTopic(it) },
            onMic = {
                speechTarget = InputTarget.MainChat
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(
                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                    )
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US.toLanguageTag())
                    putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak in English")
                }
                speechLauncher.launch(intent)
            },
            onQuickMic = {
                speechTarget = InputTarget.QuickChat
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(
                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                    )
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                }
                speechLauncher.launch(intent)
            },
            onSpeak = { message -> speak(naturalReplyOf(message.text)) },
            onOpenJapaneseHelp = {
                assistTarget = InputTarget.MainChat
                jpHelpResult = null
                showJapaneseHelp = true
            },
            onOpenQuickJapaneseHelp = {
                assistTarget = InputTarget.QuickChat
                jpHelpResult = null
                showJapaneseHelp = true
            },
            onOpenSettings = { showSettings = true },
            onOpenNotes = { showNotes = true },
            onCloseNotes = { showNotes = false },
            onCloseTopic = { topicPanelVisible = false },
            onShowTopic = { topicPanelVisible = true },
            onComposerFocusChange = { composerFocused = it },
            onDismissSettings = { showSettings = false },
            onSaveSettings = { next ->
                settings = next
                storage.saveSettings(next)
                ReminderScheduler.apply(context, next)
                showSettings = false
            },
            onClearChat = {
                messages.clear()
                messages.add(systemMessage("Today's topic: $currentTopic"))
                storage.clearMessages()
                saveMessages()
            }
        )

        if (showJapaneseHelp) {
            JapaneseHelpDialog(
                isLoading = jpHelpLoading,
                result = jpHelpResult,
                onAsk = { askJapaneseHelp(it) },
                onUseOption = { option ->
                    when (assistTarget) {
                        InputTarget.MainChat -> input = appendAssistText(input, option)
                        InputTarget.QuickChat -> quickInput = appendAssistText(quickInput, option)
                    }
                    showJapaneseHelp = false
                },
                onDismiss = { showJapaneseHelp = false }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EnglishCoachScreen(
    settings: AppSettings,
    messages: List<ChatMessage>,
    notes: List<LearningNote>,
    quickMessages: List<ChatMessage>,
    input: String,
    quickInput: String,
    isQuickSending: Boolean,
    isSending: Boolean,
    currentTopic: String,
    selectedPracticeTab: PracticeTab,
    selectedCategory: TopicCategory,
    showSettings: Boolean,
    showNotes: Boolean,
    topicPanelVisible: Boolean,
    composerFocused: Boolean,
    streak: Int,
    onPracticeTabChange: (PracticeTab) -> Unit,
    onInputChange: (String) -> Unit,
    onQuickInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onQuickReply: (String) -> Unit,
    onQuickRefresh: () -> Unit,
    onClearQuick: () -> Unit,
    onStartTopic: () -> Unit,
    onRefreshTopic: () -> Unit,
    onCategoryChange: (TopicCategory) -> Unit,
    onMic: () -> Unit,
    onQuickMic: () -> Unit,
    onSpeak: (ChatMessage) -> Unit,
    onOpenJapaneseHelp: () -> Unit,
    onOpenQuickJapaneseHelp: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenNotes: () -> Unit,
    onCloseNotes: () -> Unit,
    onCloseTopic: () -> Unit,
    onShowTopic: () -> Unit,
    onComposerFocusChange: (Boolean) -> Unit,
    onDismissSettings: () -> Unit,
    onSaveSettings: (AppSettings) -> Unit,
    onClearChat: () -> Unit
) {
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Scaffold(
        topBar = {
            Column {
                CenterAlignedTopAppBar(
                    title = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = if (showNotes) "Learning Notes" else "BYOKey Speak",
                                style = MaterialTheme.typography.titleLarge,
                                color = Grimoire.BurgundyDeep,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (!showNotes && streak > 0) {
                                Text(
                                    text = "✦ 連続 $streak 日目の修行 ✦",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Grimoire.Gold
                                )
                            }
                        }
                    },
                    actions = {
                        if (showNotes) {
                            IconButton(onClick = onCloseNotes) {
                                Icon(Icons.Default.Close, contentDescription = "Close notes", tint = Grimoire.GoldDark)
                            }
                        } else {
                            TextButton(onClick = onOpenNotes) {
                                Text("Notes", color = Grimoire.Burgundy)
                            }
                            IconButton(onClick = onClearChat) {
                                Icon(Icons.Default.Delete, contentDescription = "Clear chat", tint = Grimoire.GoldDark)
                            }
                            IconButton(onClick = onOpenSettings) {
                                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Grimoire.GoldDark)
                            }
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
                OrnamentDivider(modifier = Modifier.fillMaxWidth())
            }
        },
        containerColor = Color.Transparent
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            if (showNotes) {
                NotesScreen(
                    notes = notes,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )
            } else {
                PracticeTabs(
                    selectedPracticeTab = selectedPracticeTab,
                    onPracticeTabChange = onPracticeTabChange
                )
                when (selectedPracticeTab) {
                    PracticeTab.Chat -> {
                        if (topicPanelVisible && !composerFocused) {
                            TopicPanel(
                                topic = currentTopic,
                                selectedCategory = selectedCategory,
                                onCategoryChange = onCategoryChange,
                                onRefreshTopic = onRefreshTopic,
                                onStartTopic = onStartTopic,
                                onCloseTopic = onCloseTopic
                            )
                        } else if (!composerFocused) {
                            TopicCollapsedBar(topic = currentTopic, onShowTopic = onShowTopic)
                        }
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(messages, key = { it.id }) { message ->
                                MessageBubble(message = message, onSpeak = onSpeak)
                            }
                            if (isSending) {
                                item {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            color = Grimoire.Gold,
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = "The quill is writing...",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontStyle = FontStyle.Italic,
                                            color = Grimoire.InkFaded
                                        )
                                    }
                                }
                            }
                            item { Spacer(modifier = Modifier.height(10.dp)) }
                        }
                        Composer(
                            input = input,
                            isSending = isSending,
                            onInputChange = onInputChange,
                            onSend = onSend,
                            onMic = onMic,
                            onJapaneseHelp = onOpenJapaneseHelp,
                            onFocusChange = onComposerFocusChange
                        )
                    }

                    PracticeTab.Quick -> {
                        QuickChatScreen(
                            messages = quickMessages,
                            input = quickInput,
                            isSending = isQuickSending,
                            onInputChange = onQuickInputChange,
                            onReply = onQuickReply,
                            onRefreshPrompt = onQuickRefresh,
                            onClearQuick = onClearQuick,
                            onMic = onQuickMic,
                            onJapaneseHelp = onOpenQuickJapaneseHelp,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        )
                    }
                }
            }
        }
    }

    if (showSettings) {
        SettingsDialog(
            settings = settings,
            onDismiss = onDismissSettings,
            onSave = onSaveSettings
        )
    }
}

@Composable
private fun PracticeTabs(
    selectedPracticeTab: PracticeTab,
    onPracticeTabChange: (PracticeTab) -> Unit
) {
    TabRow(
        selectedTabIndex = selectedPracticeTab.ordinal,
        containerColor = Color.Transparent,
        contentColor = Grimoire.Burgundy
    ) {
        PracticeTab.entries.forEach { tab ->
            Tab(
                selected = selectedPracticeTab == tab,
                onClick = { onPracticeTabChange(tab) },
                text = { Text(tab.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                selectedContentColor = Grimoire.Burgundy,
                unselectedContentColor = Grimoire.InkFaded
            )
        }
    }
}

@Composable
private fun QuickChatScreen(
    messages: List<ChatMessage>,
    input: String,
    isSending: Boolean,
    onInputChange: (String) -> Unit,
    onReply: (String) -> Unit,
    onRefreshPrompt: () -> Unit,
    onClearQuick: () -> Unit,
    onMic: () -> Unit,
    onJapaneseHelp: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Quick Chat",
                style = MaterialTheme.typography.labelLarge,
                color = Grimoire.Burgundy,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onRefreshPrompt, enabled = !isSending) {
                Icon(Icons.Default.Refresh, contentDescription = "New quick chat", tint = Grimoire.GoldDark)
            }
            IconButton(onClick = onClearQuick, enabled = messages.isNotEmpty() && !isSending) {
                Icon(Icons.Default.Delete, contentDescription = "Clear quick chat", tint = Grimoire.GoldDark)
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(messages, key = { it.id }) { message ->
                QuickMessageBubble(message = message)
            }
            if (isSending) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Grimoire.Gold,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "The coach is replying...",
                            style = MaterialTheme.typography.bodySmall,
                            fontStyle = FontStyle.Italic,
                            color = Grimoire.InkFaded
                        )
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(8.dp)) }
        }

        QuickComposer(
            input = input,
            isSending = isSending,
            onInputChange = onInputChange,
            onSend = { onReply(input) },
            onMic = onMic,
            onJapaneseHelp = onJapaneseHelp
        )
    }
}

@Composable
private fun QuickMessageBubble(message: ChatMessage) {
    val isUser = message.role == MessageRole.User
    val background = if (isUser) Color(0xFFEADBB4) else Grimoire.ParchmentLight

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(if (isUser) 0.86f else 0.96f)
                .grimoireBorder(10.dp),
            color = background,
            shape = RoundedCornerShape(10.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = if (isUser) "YOU" else "COACH",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isUser) Grimoire.InkFaded else Grimoire.Burgundy
                )
                Spacer(modifier = Modifier.height(4.dp))
                SelectionContainer {
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Grimoire.Ink
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickComposer(
    input: String,
    isSending: Boolean,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onMic: () -> Unit,
    onJapaneseHelp: () -> Unit
) {
    Column {
        OrnamentDivider(modifier = Modifier.fillMaxWidth())
        Surface(color = Color.Transparent) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(12.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = onInputChange,
                    modifier = Modifier.weight(1f),
                    minLines = 1,
                    maxLines = 3,
                    placeholder = {
                        Text(
                            "Reply briefly in English",
                            fontStyle = FontStyle.Italic,
                            color = Grimoire.InkFaded
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Grimoire.Gold,
                        unfocusedBorderColor = Grimoire.GoldDark.copy(alpha = 0.6f),
                        focusedContainerColor = Grimoire.ParchmentLight,
                        unfocusedContainerColor = Grimoire.ParchmentLight
                    ),
                    enabled = !isSending
                )
                Spacer(modifier = Modifier.width(6.dp))
                IconButton(onClick = onJapaneseHelp, enabled = !isSending) {
                    Icon(Icons.Default.Translate, contentDescription = "Quick Assist", tint = Grimoire.GoldDark)
                }
                IconButton(onClick = onMic, enabled = !isSending) {
                    Icon(Icons.Default.Mic, contentDescription = "Voice input", tint = Grimoire.GoldDark)
                }
                IconButton(
                    onClick = onSend,
                    enabled = input.isNotBlank() && !isSending
                ) {
                    Icon(Icons.Default.Send, contentDescription = "Send quick chat", tint = Grimoire.Burgundy)
                }
            }
        }
    }
}

@Composable
private fun NotesScreen(
    notes: List<LearningNote>,
    modifier: Modifier = Modifier
) {
    if (notes.isEmpty()) {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "This page of the grimoire is still blank.\nCoach notes will appear here after your next conversation.",
                style = MaterialTheme.typography.bodyMedium,
                fontStyle = FontStyle.Italic,
                textAlign = TextAlign.Center,
                color = Grimoire.InkFaded
            )
        }
        return
    }

    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(notes, key = { it.id }) { note ->
            NoteCard(note = note)
        }
        item { Spacer(modifier = Modifier.height(12.dp)) }
    }
}

@Composable
private fun NoteCard(note: LearningNote) {
    Card(
        modifier = Modifier.grimoireBorder(10.dp),
        colors = CardDefaults.cardColors(containerColor = Grimoire.ParchmentLight),
        shape = RoundedCornerShape(10.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "✦ ${formatNoteDate(note.timestampMillis)}",
                style = MaterialTheme.typography.labelSmall,
                color = Grimoire.Gold
            )
            SelectionContainer {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Your sentence:\n${note.sourceMessage}",
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = FontStyle.Italic,
                        color = Grimoire.InkFaded
                    )
                    Text(
                        text = "Coach notes:\n${note.coachNotes}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Grimoire.Ink
                    )
                    if (note.betterOptions.isNotBlank()) {
                        Text(
                            text = "Better options:\n${note.betterOptions}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Grimoire.Ink
                        )
                    }
                }
            }
        }
    }
}

private fun formatNoteDate(timestampMillis: Long): String {
    return SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(Date(timestampMillis))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TopicPanel(
    topic: String,
    selectedCategory: TopicCategory,
    onCategoryChange: (TopicCategory) -> Unit,
    onRefreshTopic: () -> Unit,
    onStartTopic: () -> Unit,
    onCloseTopic: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "✦ Today's Chapter",
                style = MaterialTheme.typography.labelLarge,
                color = Grimoire.Burgundy,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onRefreshTopic) {
                Icon(Icons.Default.Refresh, contentDescription = "New topic", tint = Grimoire.GoldDark)
            }
            IconButton(onClick = onCloseTopic) {
                Icon(Icons.Default.Close, contentDescription = "Hide topic", tint = Grimoire.GoldDark)
            }
        }
        Card(
            modifier = Modifier.grimoireBorder(10.dp),
            colors = CardDefaults.cardColors(containerColor = Grimoire.ParchmentDeep),
            shape = RoundedCornerShape(10.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = topic,
                    style = MaterialTheme.typography.titleMedium,
                    color = Grimoire.Ink
                )
                Spacer(modifier = Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(
                        onClick = onStartTopic,
                        label = { Text("Reply", color = Grimoire.Burgundy) }
                    )
                    Text(
                        text = "Choose a category to get a new topic.",
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = FontStyle.Italic,
                        color = Grimoire.InkFaded
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        TopicCategory.entries.forEach { category ->
                            FilterChip(
                                selected = selectedCategory == category,
                                onClick = { onCategoryChange(category) },
                                label = { Text(category.label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Grimoire.Burgundy,
                                    selectedLabelColor = Grimoire.ParchmentLight
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopicCollapsedBar(
    topic: String,
    onShowTopic: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "✦ $topic",
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall,
            fontStyle = FontStyle.Italic,
            color = Grimoire.Burgundy
        )
        TextButton(onClick = onShowTopic) {
            Text("Topic", color = Grimoire.GoldDark)
        }
    }
}

@Composable
private fun MessageBubble(
    message: ChatMessage,
    onSpeak: (ChatMessage) -> Unit
) {
    val isUser = message.role == MessageRole.User
    val background = when (message.role) {
        MessageRole.User -> Color(0xFFEADBB4)
        MessageRole.Coach -> Grimoire.ParchmentLight
        MessageRole.System -> Color(0xFFE3CF9C)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(if (isUser) 0.86f else 0.96f)
                .grimoireBorder(10.dp),
            color = background,
            shape = RoundedCornerShape(10.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = when (message.role) {
                            MessageRole.User -> "YOU"
                            MessageRole.Coach -> "✦ COACH"
                            MessageRole.System -> "✦ TOPIC ✦"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isUser) Grimoire.InkFaded else Grimoire.Burgundy,
                        modifier = Modifier.weight(1f)
                    )
                    if (message.role == MessageRole.Coach) {
                        IconButton(
                            onClick = { onSpeak(message) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Default.VolumeUp,
                                contentDescription = "Read aloud",
                                tint = Grimoire.GoldDark,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                SelectionContainer {
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Grimoire.Ink
                    )
                }
            }
        }
    }
}

@Composable
private fun Composer(
    input: String,
    isSending: Boolean,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onMic: () -> Unit,
    onJapaneseHelp: () -> Unit,
    onFocusChange: (Boolean) -> Unit
) {
    Column {
        OrnamentDivider(modifier = Modifier.fillMaxWidth())
        Surface(color = Color.Transparent) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(12.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                IconButton(onClick = onMic, enabled = !isSending) {
                    Icon(Icons.Default.Mic, contentDescription = "Voice input", tint = Grimoire.Burgundy)
                }
                IconButton(onClick = onJapaneseHelp, enabled = !isSending) {
                    Icon(Icons.Default.Translate, contentDescription = "日本語ヘルプ", tint = Grimoire.Burgundy)
                }
                OutlinedTextField(
                    value = input,
                    onValueChange = onInputChange,
                    modifier = Modifier
                        .weight(1f)
                        .onFocusChanged { onFocusChange(it.isFocused) },
                    minLines = 1,
                    maxLines = 5,
                    placeholder = {
                        Text(
                            "Write your reply in English",
                            fontStyle = FontStyle.Italic,
                            color = Grimoire.InkFaded
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Grimoire.Gold,
                        unfocusedBorderColor = Grimoire.GoldDark.copy(alpha = 0.6f),
                        focusedContainerColor = Grimoire.ParchmentLight,
                        unfocusedContainerColor = Grimoire.ParchmentLight
                    ),
                    enabled = !isSending
                )
                Spacer(modifier = Modifier.width(6.dp))
                IconButton(
                    onClick = onSend,
                    enabled = input.isNotBlank() && !isSending
                ) {
                    Icon(Icons.Default.Send, contentDescription = "Send", tint = Grimoire.Burgundy)
                }
            }
        }
    }
}

@Composable
private fun JapaneseHelpDialog(
    isLoading: Boolean,
    result: String?,
    onAsk: (String) -> Unit,
    onUseOption: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var japaneseInput by rememberSaveable { mutableStateOf("") }
    val options = remember(result) { extractHelpOptions(result) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Grimoire.ParchmentLight,
        title = {
            Text(
                "✦ 翻訳の魔法 ✦",
                style = MaterialTheme.typography.titleMedium,
                color = Grimoire.BurgundyDeep
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "言いたいことを日本語で書くと、英語での言い方を3パターン教えます。",
                    style = MaterialTheme.typography.bodySmall,
                    color = Grimoire.InkFaded
                )
                OutlinedTextField(
                    value = japaneseInput,
                    onValueChange = { japaneseInput = it },
                    minLines = 2,
                    maxLines = 4,
                    placeholder = { Text("例: 最近は物価が上がって大変です") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Grimoire.Gold,
                        unfocusedBorderColor = Grimoire.GoldDark.copy(alpha = 0.6f)
                    ),
                    enabled = !isLoading
                )
                if (isLoading) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Grimoire.Gold,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "詠唱中...",
                            style = MaterialTheme.typography.bodySmall,
                            fontStyle = FontStyle.Italic,
                            color = Grimoire.InkFaded
                        )
                    }
                }
                if (!result.isNullOrBlank() && !isLoading) {
                    if (options.isNotEmpty()) {
                        Text(
                            text = "タップすると入力欄にセットされます。",
                            style = MaterialTheme.typography.labelSmall,
                            color = Grimoire.Gold
                        )
                        options.forEach { option ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .grimoireBorder(8.dp)
                                    .clickable { onUseOption(option.english) },
                                colors = CardDefaults.cardColors(containerColor = Grimoire.ParchmentDeep),
                                shape = RoundedCornerShape(8.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = option.english,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Grimoire.Ink
                                    )
                                    if (option.note.isNotBlank()) {
                                        Text(
                                            text = option.note,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Grimoire.InkFaded
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        SelectionContainer {
                            Text(
                                text = result,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Grimoire.Ink
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onAsk(japaneseInput) },
                enabled = japaneseInput.isNotBlank() && !isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = Grimoire.Burgundy)
            ) {
                Text("英語での言い方を聞く")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("閉じる", color = Grimoire.InkFaded)
            }
        }
    )
}

private enum class PracticeTab(val label: String) {
    Chat("Chat"),
    Quick("Quick")
}

private enum class InputTarget {
    MainChat,
    QuickChat
}

private val quickStarterLines = listOf(
    "I finally tried that new cafe near the station.",
    "My morning was busier than I expected.",
    "I found a small app update that actually helped today.",
    "I slept better than usual last night.",
    "I want to keep today pretty simple."
)

private data class HelpOption(val english: String, val note: String)

/** "Option n:" 形式の回答から選択肢を取り出す。英文1行目＋残りを注釈として扱う。 */
private fun extractHelpOptions(result: String?): List<HelpOption> {
    if (result.isNullOrBlank()) return emptyList()
    val regex = Regex("(?i)option\\s*([123])\\s*:")
    val matches = regex.findAll(result).toList()
    if (matches.isEmpty()) return emptyList()
    return matches.mapIndexedNotNull { index, match ->
        val start = match.range.last + 1
        val end = if (index + 1 < matches.size) matches[index + 1].range.first else result.length
        val block = result.substring(start, end).trim()
        if (block.isBlank()) return@mapIndexedNotNull null
        val lines = block.lines().map { it.trim() }.filter { it.isNotBlank() }
        val english = lines.firstOrNull() ?: return@mapIndexedNotNull null
        val note = lines.drop(1).joinToString(" ")
        HelpOption(english = english, note = note)
    }
}

/** コーチ返信から Natural reply の部分だけを取り出す（TTS用） */
private fun naturalReplyOf(text: String): String {
    val lower = text.lowercase()
    val startLabel = "natural reply:"
    val startIndex = lower.indexOf(startLabel)
    val from = if (startIndex >= 0) startIndex + startLabel.length else 0
    val end = listOf("coach notes:", "japanese explanation:", "better options:")
        .mapNotNull { label -> lower.indexOf(label, from).takeIf { it > from } }
        .minOrNull() ?: text.length
    val extracted = text.substring(from, end).trim()
    return extracted.ifBlank { text }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsDialog(
    settings: AppSettings,
    onDismiss: () -> Unit,
    onSave: (AppSettings) -> Unit
) {
    var apiKey by rememberSaveable { mutableStateOf(settings.apiKey) }
    var provider by rememberSaveable { mutableStateOf(settings.provider) }
    var model by rememberSaveable { mutableStateOf(settings.model) }
    var reminderEnabled by rememberSaveable { mutableStateOf(settings.dailyReminderEnabled) }
    var hourText by rememberSaveable { mutableStateOf(settings.reminderHour.toString()) }
    var minuteText by rememberSaveable { mutableStateOf(settings.reminderMinute.toString().padStart(2, '0')) }
    var level by rememberSaveable { mutableStateOf(settings.englishLevel) }
    var coachInstructions by rememberSaveable { mutableStateOf(settings.coachInstructions) }
    var isTesting by rememberSaveable { mutableStateOf(false) }
    var testResult by rememberSaveable { mutableStateOf<String?>(null) }
    val testScope = rememberCoroutineScope()
    val testClient = remember { LlmClient() }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Grimoire.ParchmentLight,
        title = {
            Text(
                "Settings",
                style = MaterialTheme.typography.titleMedium,
                color = Grimoire.BurgundyDeep
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column {
                    Text("LLM provider", style = MaterialTheme.typography.labelLarge, color = Grimoire.Ink)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        LlmProvider.entries.forEach { candidate ->
                            FilterChip(
                                selected = provider == candidate,
                                onClick = {
                                    if (provider != candidate) {
                                        provider = candidate
                                        model = candidate.defaultModel
                                        apiKey = ""
                                    }
                                },
                                label = { Text(candidate.label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Grimoire.Burgundy,
                                    selectedLabelColor = Grimoire.ParchmentLight
                                )
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("${provider.label} API key") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation()
                )
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text("Model") },
                    singleLine = true,
                    placeholder = { Text(provider.defaultModel) }
                )
                OutlinedTextField(
                    value = coachInstructions,
                    onValueChange = { coachInstructions = it },
                    label = { Text("Coach Personalities & Skills") },
                    minLines = 3,
                    maxLines = 5,
                    placeholder = { Text(AppStorage.DEFAULT_COACH_INSTRUCTIONS) }
                )
                Column {
                    Text("CEFR level", style = MaterialTheme.typography.labelLarge, color = Grimoire.Ink)
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        EnglishLevel.entries.forEach { candidate ->
                            FilterChip(
                                selected = level == candidate,
                                onClick = { level = candidate },
                                label = { Text(candidate.label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Grimoire.Burgundy,
                                    selectedLabelColor = Grimoire.ParchmentLight
                                )
                            )
                        }
                    }
                }
                TextButton(
                    onClick = {
                        isTesting = true
                        testResult = null
                        testScope.launch {
                            testResult = runCatching {
                                testClient.generate(
                                    provider,
                                    apiKey.trim(),
                                    model.ifBlank { provider.defaultModel },
                                    "Reply with exactly: OK"
                                )
                            }.fold(
                                onSuccess = { "Connection successful" },
                                onFailure = { it.message ?: "Connection failed" }
                            )
                            isTesting = false
                        }
                    },
                    enabled = apiKey.isNotBlank() && model.isNotBlank() && !isTesting
                ) {
                    if (isTesting) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(if (isTesting) "Testing..." else "Test connection")
                }
                testResult?.let { result ->
                    Text(result, style = MaterialTheme.typography.bodySmall, color = Grimoire.InkFaded)
                }
                Text(
                    "API key is encrypted on this device and is never sent to BYOKey Lab.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Grimoire.InkFaded
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Daily morning topic", modifier = Modifier.weight(1f))
                    Switch(
                        checked = reminderEnabled,
                        onCheckedChange = { reminderEnabled = it }
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = hourText,
                        onValueChange = { hourText = it.filter { char -> char.isDigit() }.take(2) },
                        label = { Text("Hour") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = minuteText,
                        onValueChange = { minuteText = it.filter { char -> char.isDigit() }.take(2) },
                        label = { Text("Minute") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        AppSettings(
                            provider = provider,
                            apiKey = apiKey,
                            model = model.ifBlank { provider.defaultModel },
                            dailyReminderEnabled = reminderEnabled,
                            reminderHour = hourText.toIntOrNull()?.coerceIn(0, 23) ?: 7,
                            reminderMinute = minuteText.toIntOrNull()?.coerceIn(0, 59) ?: 15,
                            englishLevel = level,
                            coachInstructions = coachInstructions.ifBlank { AppStorage.DEFAULT_COACH_INSTRUCTIONS }
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = Grimoire.Burgundy)
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Grimoire.InkFaded)
            }
        }
    )
}

private fun userMessage(text: String) = ChatMessage(
    id = System.nanoTime(),
    role = MessageRole.User,
    text = text,
    timestampMillis = System.currentTimeMillis()
)

private fun coachMessage(text: String) = ChatMessage(
    id = System.nanoTime(),
    role = MessageRole.Coach,
    text = text,
    timestampMillis = System.currentTimeMillis()
)

private fun systemMessage(text: String) = ChatMessage(
    id = System.nanoTime(),
    role = MessageRole.System,
    text = text,
    timestampMillis = System.currentTimeMillis()
)
