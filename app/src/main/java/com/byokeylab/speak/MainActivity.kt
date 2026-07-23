package com.byokeylab.speak

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.viewmodel.compose.viewModel
import com.byokeylab.speak.ui.ChatsScreen
import com.byokeylab.speak.ui.CoachScreen
import com.byokeylab.speak.ui.OnboardingScreen
import com.byokeylab.speak.ui.ProgressScreen
import com.byokeylab.speak.ui.ReviewScreen
import com.byokeylab.speak.ui.SettingsScreen
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
            BYOKeySpeakApp(initialTopic = initialTopic)
        }
    }

    companion object {
        const val EXTRA_TOPIC = "morning_topic"
    }
}

@Composable
private fun BYOKeySpeakApp(initialTopic: String?) {
    val context = LocalContext.current
    val viewModel: AppViewModel = viewModel()

    // 通知からのトピック起動 (コーチ会話の準備完了後に一度だけ)
    var topicConsumed by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(viewModel.coachChatId) {
        if (!topicConsumed && !initialTopic.isNullOrBlank() && viewModel.coachChatId != null) {
            viewModel.startTopic(initialTopic)
            viewModel.currentTab = MainTab.Coach
            topicConsumed = true
        }
    }

    // TTS (コーチ返信の読み上げ + ハンズフリー自動ループ)
    val ttsRef = remember { mutableStateOf<TextToSpeech?>(null) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val startMicRef = remember { mutableStateOf<((Long) -> Unit)?>(null) }
    DisposableEffect(Unit) {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                engine?.language = Locale.US
                engine?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        // 自動モード: 読み上げが終わったらマイクを起動する
                        if (utteranceId != null && utteranceId.startsWith("auto_")) {
                            val chatId = utteranceId.removePrefix("auto_").toLongOrNull() ?: return
                            mainHandler.post {
                                if (viewModel.autoMode) startMicRef.value?.invoke(chatId)
                            }
                        }
                    }
                })
                ttsRef.value = engine
            }
        }
        onDispose {
            engine?.stop()
            engine?.shutdown()
            ttsRef.value = null
        }
    }

    /** 設定に応じて声(男性/女性)を切り替える。該当ボイスがない端末ではピッチで代用。 */
    fun applyVoiceStyle(engine: TextToSpeech) {
        val male = viewModel.settings.voiceGender == VoiceGender.Male
        val voice = runCatching {
            engine.voices
                ?.filter { it.locale.language == "en" }
                ?.firstOrNull { candidate ->
                    val name = candidate.name.lowercase(Locale.ROOT)
                    if (male) "male" in name && "female" !in name else "female" in name
                }
        }.getOrNull()
        if (voice != null) {
            runCatching { engine.voice = voice }
            engine.setPitch(1.0f)
        } else {
            engine.setPitch(if (male) 0.7f else 1.05f)
        }
    }

    val speak: (String) -> Unit = { text ->
        ttsRef.value?.let { engine ->
            applyVoiceStyle(engine)
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "coach_reply")
        }
    }

    // コーチ返信到着時の自動読み上げ(読み上げ完了でマイク起動につながる)
    androidx.compose.runtime.SideEffect {
        viewModel.coachReplyHook = { chatId, naturalReply ->
            ttsRef.value?.let { engine ->
                applyVoiceStyle(engine)
                engine.speak(naturalReply, TextToSpeech.QUEUE_FLUSH, null, "auto_$chatId")
            }
        }
    }

    // 音声入力:
    // - 会話入力欄 → 英語認識 (en-US) で下書きへ追記
    // - Quick Assist → 日本語認識 (ja-JP) で質問欄へ追記
    var speechTargetChatId by rememberSaveable { mutableStateOf<Long?>(null) }
    val speechLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                .orEmpty()
            if (spoken.isNotBlank()) {
                val target = speechTargetChatId
                if (target != null) {
                    viewModel.appendToDraft(target, spoken)
                } else {
                    viewModel.appendToAssistQuestion(spoken)
                }
            }
        }
    }
    fun launchSpeech(languageTag: String, prompt: String) {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageTag)
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, true)
            putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
        }
        runCatching { speechLauncher.launch(intent) }
    }
    val startMic: (Long) -> Unit = { chatId ->
        speechTargetChatId = chatId
        launchSpeech("en-US", "Speak in English")
    }
    startMicRef.value = startMic
    val startAssistMic: () -> Unit = {
        speechTargetChatId = null
        launchSpeech("ja-JP", "日本語で話してください")
    }

    GrimoireTheme(darkTheme = viewModel.settings.theme == AppTheme.Dark) {
        if (!viewModel.onboardingDone) {
            OnboardingScreen(onFinish = { goToSettings ->
                viewModel.completeOnboarding(goToSettings)
            })
        } else {
            MainScaffold(
                viewModel = viewModel,
                onMic = startMic,
                onAssistMic = startAssistMic,
                onSpeak = speak
            )
        }

        // 設定未完了で送信しようとした場合の誘導 (FR-001)
        if (viewModel.showSetupPrompt) {
            AlertDialog(
                onDismissRequest = { viewModel.showSetupPrompt = false },
                containerColor = Grimoire.ParchmentLight,
                title = { Text("APIキーの設定が必要です", color = Grimoire.BurgundyDeep) },
                text = {
                    Text(
                        "会話を始めるには、お使いのLLMプロバイダーのAPIキーを設定してください。設定するまで会話は送信されません。",
                        color = Grimoire.Ink
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.showSetupPrompt = false
                            viewModel.currentTab = MainTab.Settings
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Grimoire.Burgundy)
                    ) { Text("設定画面へ") }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.showSetupPrompt = false }) {
                        Text("あとで", color = Grimoire.InkFaded)
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScaffold(
    viewModel: AppViewModel,
    onMic: (Long) -> Unit,
    onAssistMic: () -> Unit,
    onSpeak: (String) -> Unit
) {
    val stats by viewModel.dailyStats.collectAsState()
    val streak = viewModel.streakOf(stats)

    Scaffold(
        topBar = {
            Column {
                CenterAlignedTopAppBar(
                    title = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "BYOKey Speak",
                                style = MaterialTheme.typography.titleLarge,
                                color = Grimoire.BurgundyDeep,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (streak > 0) {
                                Text(
                                    text = "✦ 連続 $streak 日目の修行 ✦",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Grimoire.Gold
                                )
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
        bottomBar = {
            NavigationBar(containerColor = Grimoire.ParchmentDeep) {
                MainTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = viewModel.currentTab == tab,
                        onClick = { viewModel.currentTab = tab },
                        icon = {
                            Icon(
                                imageVector = tab.icon(),
                                contentDescription = tab.label
                            )
                        },
                        label = { Text(tab.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Grimoire.Burgundy,
                            selectedTextColor = Grimoire.Burgundy,
                            unselectedIconColor = Grimoire.InkFaded,
                            unselectedTextColor = Grimoire.InkFaded,
                            indicatorColor = Grimoire.Gold.copy(alpha = 0.35f)
                        )
                    )
                }
            }
        },
        containerColor = Color.Transparent
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            when (viewModel.currentTab) {
                MainTab.Coach -> CoachScreen(
                    viewModel = viewModel,
                    onMic = onMic,
                    onAssistMic = onAssistMic,
                    onSpeak = onSpeak,
                    modifier = Modifier.fillMaxSize()
                )
                MainTab.Chats -> ChatsScreen(
                    viewModel = viewModel,
                    onMic = onMic,
                    onAssistMic = onAssistMic,
                    onSpeak = onSpeak,
                    modifier = Modifier.fillMaxSize()
                )
                MainTab.Review -> ReviewScreen(
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxSize()
                )
                MainTab.Progress -> ProgressScreen(
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxSize()
                )
                MainTab.Settings -> SettingsScreen(
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

private fun MainTab.icon(): ImageVector = when (this) {
    MainTab.Coach -> Icons.Default.School
    MainTab.Chats -> Icons.Default.ChatBubbleOutline
    MainTab.Review -> Icons.AutoMirrored.Filled.MenuBook
    MainTab.Progress -> Icons.Default.Insights
    MainTab.Settings -> Icons.Default.Settings
}
