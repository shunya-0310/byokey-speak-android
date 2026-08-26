package com.byokeylab.speak

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Insights
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.viewmodel.compose.viewModel
import com.byokeylab.speak.ui.ChatsScreen
import com.byokeylab.speak.ui.OnboardingScreen
import com.byokeylab.speak.ui.ProgressScreen
import com.byokeylab.speak.data.ChatMode
import com.byokeylab.speak.ui.ReviewScreen
import com.byokeylab.speak.ui.SettingsScreen
import com.byokeylab.speak.ui.SplashScreen
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.roundToInt

private const val AUTO_MIC_START_DELAY_MS = 350L

class MainActivity : ComponentActivity() {
    private lateinit var appUpdateManager: AppUpdateManager
    private lateinit var updateFlowLauncher: ActivityResultLauncher<IntentSenderRequest>
    private var updateFlowStarted = false
    private var updateReadyToInstall by mutableStateOf(false)
    private val updateStateListener = InstallStateUpdatedListener { state ->
        if (state.installStatus() == InstallStatus.DOWNLOADED) {
            runOnUiThread { updateReadyToInstall = true }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        appUpdateManager = AppUpdateManagerFactory.create(this)
        appUpdateManager.registerListener(updateStateListener)
        updateFlowLauncher = registerForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult()
        ) {
            // キャンセルまたは失敗した場合も、この起動中には再表示しない。
            // 次回起動時にPlayの状態をあらためて確認する。
        }

        val notificationPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) {}

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            val context = LocalContext.current
            val soundEffects = remember(context) { AppSoundEffects(context) }
            DisposableEffect(soundEffects) {
                onDispose { soundEffects.release() }
            }
            // スプラッシュ (1秒): プロセス生成後の初回のみ表示。回転等の再生成では出さない
            var showSplash by rememberSaveable { mutableStateOf(!splashShown) }
            CompositionLocalProvider(LocalAppSoundEffects provides soundEffects) {
                if (showSplash) {
                    SplashScreen(onFinished = {
                        splashShown = true
                        showSplash = false
                    })
                } else {
                    BYOKeySpeakApp(
                        updateReadyToInstall = updateReadyToInstall,
                        onInstallDownloadedUpdate = ::installDownloadedUpdate,
                        onDismissDownloadedUpdate = { updateReadyToInstall = false }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkForFlexibleUpdate()
    }

    override fun onDestroy() {
        if (::appUpdateManager.isInitialized) {
            appUpdateManager.unregisterListener(updateStateListener)
        }
        super.onDestroy()
    }

    private fun checkForFlexibleUpdate() {
        if (updateFlowStarted) return

        appUpdateManager.appUpdateInfo.addOnSuccessListener { info ->
            when {
                info.installStatus() == InstallStatus.DOWNLOADED -> {
                    updateReadyToInstall = true
                }

                info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                    info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE) -> {
                    updateFlowStarted = true
                    appUpdateManager.startUpdateFlowForResult(
                        info,
                        updateFlowLauncher,
                        AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build()
                    )
                }
            }
        }
    }

    private fun installDownloadedUpdate() {
        updateReadyToInstall = false
        appUpdateManager.completeUpdate().addOnFailureListener {
            updateReadyToInstall = true
        }
    }

    companion object {
        /** プロセス内で一度スプラッシュを見せたかどうか */
        private var splashShown = false
    }
}

@Composable
private fun BYOKeySpeakApp(
    updateReadyToInstall: Boolean,
    onInstallDownloadedUpdate: () -> Unit,
    onDismissDownloadedUpdate: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: AppViewModel = viewModel()
    val soundEffects = LocalAppSoundEffects.current
    val userMessageTexts by viewModel.userMessageTexts.collectAsState()
    var showPostOnboardingSplash by rememberSaveable { mutableStateOf(false) }
    var finishOnboardingToSettings by rememberSaveable { mutableStateOf(false) }
    var showOnboardingReplay by rememberSaveable { mutableStateOf(false) }
    var onboardingReplayStartPage by rememberSaveable { mutableStateOf(0) }
    val tutorialAnchors = remember { TutorialAnchorRegistry() }

    DisposableEffect(viewModel, soundEffects) {
        viewModel.messageReceivedHook = {
            soundEffects?.play(AppSound.MessageReceive)
        }
        onDispose { viewModel.messageReceivedHook = null }
    }
    androidx.compose.runtime.SideEffect {
        soundEffects?.enabled = viewModel.settings.soundEffectsEnabled
    }

    LaunchedEffect(viewModel.onboardingDone, viewModel.currentTab) {
        if (!viewModel.onboardingDone) return@LaunchedEffect
        when (viewModel.currentTab) {
            MainTab.Chats -> viewModel.maybeShowTutorial(AppTutorialKind.Chats)
            MainTab.Review -> viewModel.maybeShowTutorial(AppTutorialKind.Vocabulary)
            MainTab.Settings -> viewModel.maybeShowTutorial(AppTutorialKind.Settings)
            MainTab.Progress -> viewModel.maybeShowProgressTutorial(userMessageTexts.size)
        }
    }

    LaunchedEffect(viewModel.onboardingDone, userMessageTexts.size) {
        if (viewModel.onboardingDone) {
            delay(2_000)
            viewModel.maybeShowProgressTutorial(userMessageTexts.size)
        }
    }

    // TTS (端末音声またはGemini音声。読み上げ&マイクオート時は完了後に英語マイクを開く)
    val ttsRef = remember { mutableStateOf<TextToSpeech?>(null) }
    val geminiTts = remember(context) { GeminiTtsPlayer(context) }
    var speakingMessageId by rememberSaveable { mutableStateOf<Long?>(null) }
    var pendingAutoMicChatId by rememberSaveable { mutableStateOf<Long?>(null) }
    var autoMicLaunchGeneration by remember { mutableLongStateOf(0L) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val startMicRef = remember { mutableStateOf<((Long) -> Unit)?>(null) }
    val scheduleEnglishMicAfterReading: (Long?) -> Unit = { chatId ->
        if (chatId != null && pendingAutoMicChatId == chatId && viewModel.autoMode.startsMicAfterReading) {
            val generation = autoMicLaunchGeneration + 1
            autoMicLaunchGeneration = generation
            mainHandler.postDelayed({
                if (
                    autoMicLaunchGeneration == generation &&
                    pendingAutoMicChatId == chatId &&
                    viewModel.autoMode.startsMicAfterReading
                ) {
                    pendingAutoMicChatId = null
                    startMicRef.value?.invoke(chatId)
                }
            }, AUTO_MIC_START_DELAY_MS)
        }
    }
    DisposableEffect(geminiTts) {
        onDispose { geminiTts.close() }
    }
    DisposableEffect(Unit) {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                engine?.language = Locale.US
                engine?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        val messageId = utteranceId
                            ?.removePrefix("msg_")
                            ?.takeIf { it != utteranceId }
                            ?.toLongOrNull()
                        mainHandler.post { speakingMessageId = messageId }
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        mainHandler.post { speakingMessageId = null }
                    }
                    override fun onDone(utteranceId: String?) {
                        mainHandler.post { speakingMessageId = null }
                        val chatId = utteranceId
                            ?.removePrefix("msg_")
                            ?.takeIf { it != utteranceId }
                            ?.toLongOrNull()
                        // 読み上げ&マイクオート: Android TTSの完了通知後に少しだけ待ってから起動する。
                        mainHandler.post { scheduleEnglishMicAfterReading(chatId) }
                    }

                    override fun onStop(utteranceId: String?, interrupted: Boolean) {
                        mainHandler.post { speakingMessageId = null }
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

    val speak: (Long?, String) -> Unit = { messageId, text ->
        if (viewModel.settings.speechOutputProvider == SpeechOutputProvider.Gemini) {
            geminiTts.play(
                apiKey = viewModel.settings.apiKey,
                text = text,
                voice = viewModel.settings.geminiTtsVoice,
                callback = object : GeminiTtsPlayer.Callback {
                    override fun onStart() {
                        speakingMessageId = messageId
                    }

                    override fun onComplete() {
                        speakingMessageId = null
                        scheduleEnglishMicAfterReading(messageId)
                    }

                    override fun onError(message: String) {
                        speakingMessageId = null
                        pendingAutoMicChatId = null
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    }
                }
            )
        } else {
            ttsRef.value?.let { engine ->
                applyVoiceStyle(engine)
                speakingMessageId = messageId
                val params = Bundle().apply {
                    putString(TextToSpeech.Engine.KEY_PARAM_VOLUME, "1.0")
                }
                engine.speak(text, TextToSpeech.QUEUE_FLUSH, params, messageId?.let { "msg_$it" } ?: "topic")
            }
        }
    }
    val pauseSpeaking: () -> Unit = {
        ttsRef.value?.stop()
        geminiTts.stop()
        autoMicLaunchGeneration += 1
        pendingAutoMicChatId = null
        speakingMessageId = null
    }

    // コーチ返信到着時の自動読み上げ(読み上げ完了でマイク起動につながる)
    androidx.compose.runtime.SideEffect {
        viewModel.coachReplyHook = { chatId, naturalReply ->
            autoMicLaunchGeneration += 1
            pendingAutoMicChatId = if (viewModel.autoMode.startsMicAfterReading) chatId else null
            speak(chatId, naturalReply)
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
    val startEnglishMic: (Long) -> Unit = { chatId ->
        speechTargetChatId = chatId
        launchSpeech("en-US", "Speak in English")
    }
    val startJapaneseMic: (Long) -> Unit = { chatId ->
        speechTargetChatId = chatId
        launchSpeech("ja-JP", "日本語で話してください")
    }
    startMicRef.value = startEnglishMic
    val startAssistMic: () -> Unit = {
        speechTargetChatId = null
        launchSpeech("ja-JP", "日本語で話してください")
    }

    GrimoireTheme(darkTheme = viewModel.settings.theme == AppTheme.Dark) {
        CompositionLocalProvider(LocalTutorialAnchors provides tutorialAnchors) {
        if (showPostOnboardingSplash) {
            SplashScreen(onFinished = {
                showPostOnboardingSplash = false
                viewModel.completeOnboarding(finishOnboardingToSettings)
            })
        } else if (!viewModel.onboardingDone || showOnboardingReplay) {
            OnboardingScreen(
                initialPage = if (showOnboardingReplay) onboardingReplayStartPage else 0,
                requireExternalDataConsent = !viewModel.hasExternalDataConsent,
                onFinish = { goToSettings, externalDataConsented ->
                if (showOnboardingReplay) {
                    showOnboardingReplay = false
                    onboardingReplayStartPage = 0
                    if (externalDataConsented) viewModel.confirmExternalDataConsent()
                    if (goToSettings) viewModel.currentTab = MainTab.Settings
                } else {
                    finishOnboardingToSettings = goToSettings
                    showPostOnboardingSplash = true
                }
            })
        } else {
            MainScaffold(
                viewModel = viewModel,
                onEnglishMic = startEnglishMic,
                onJapaneseMic = startJapaneseMic,
                onAssistMic = startAssistMic,
                onSpeak = speak,
                speakingMessageId = speakingMessageId,
                onPauseSpeaking = pauseSpeaking,
                onReplayOnboarding = {
                    onboardingReplayStartPage = 0
                    showOnboardingReplay = true
                },
                onOpenConsentOnboarding = {
                    onboardingReplayStartPage = 4
                    showOnboardingReplay = true
                }
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
                        "会話を始めるにはGemini APIキーを設定してください。設定するまで会話は送信されません。",
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

        if (viewModel.geminiOnlyMigrationNotice) {
            AlertDialog(
                onDismissRequest = viewModel::dismissGeminiOnlyMigrationNotice,
                containerColor = Grimoire.ParchmentLight,
                title = { Text("Gemini専用版へ更新しました", color = Grimoire.BurgundyDeep) },
                text = {
                    Text(
                        "この版ではGeminiのみを利用できます。安全のため、旧版でGemini以外を選択していたAPIキーは端末から削除しました。Geminiを使う場合は設定画面でGemini APIキーを入力してください。会話履歴などの学習データはそのまま残っています。",
                        color = Grimoire.Ink
                    )
                },
                confirmButton = {
                    Button(
                        onClick = viewModel::dismissGeminiOnlyMigrationNotice,
                        colors = ButtonDefaults.buttonColors(containerColor = Grimoire.Burgundy)
                    ) { Text("確認しました") }
                }
            )
        }

        viewModel.activeTutorial?.let { tutorial ->
            TutorialOverlay(
                kind = tutorial,
                onStepChanged = viewModel::updateTutorialStep,
                onDismiss = viewModel::dismissTutorial
            )
        }
        AnimatedVisibility(
            visible = viewModel.showFeedbackPrompt,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            FeedbackPrompt(
                onDismiss = viewModel::dismissFeedbackPrompt
            )
        }
        if (updateReadyToInstall) {
            AlertDialog(
                onDismissRequest = onDismissDownloadedUpdate,
                containerColor = Grimoire.ParchmentLight,
                title = { Text("更新をインストールできます", color = Grimoire.BurgundyDeep) },
                text = {
                    Text(
                        "更新のダウンロードが完了しました。アプリを再起動して更新を適用しますか？",
                        color = Grimoire.Ink
                    )
                },
                confirmButton = {
                    Button(
                        onClick = onInstallDownloadedUpdate,
                        colors = ButtonDefaults.buttonColors(containerColor = Grimoire.Burgundy)
                    ) { Text("再起動して適用") }
                },
                dismissButton = {
                    TextButton(onClick = onDismissDownloadedUpdate) {
                        Text("あとで", color = Grimoire.InkFaded)
                    }
                }
            )
        }
        }
    }
}

@Composable
private fun FeedbackPrompt(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.46f))
            .padding(18.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 18.dp,
                    shape = RoundedCornerShape(12.dp),
                    ambientColor = Grimoire.Gold,
                    spotColor = Grimoire.Gold
                )
                .grimoireBorder(12.dp),
            color = Grimoire.ParchmentLight,
            shape = RoundedCornerShape(12.dp),
            shadowElevation = 12.dp
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "使い心地はいかがですか？",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold
                    ),
                    color = Grimoire.BurgundyDeep
                )
                Text(
                    text = "会話分析はProgress画面からいつでも確認できます。BYOKey Speakをより良くするため、ご意見や評価をお聞かせください。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Grimoire.Ink
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            onDismiss()
                            openFeedbackForm(context)
                        }
                    ) {
                        Text("意見を送る", color = Grimoire.GoldDark)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onDismiss()
                            openPlayStoreRating(context)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Grimoire.Burgundy)
                    ) {
                        Text("評価する")
                    }
                }
            }
        }
    }
}

private fun openFeedbackForm(context: android.content.Context) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(AppLinks.CONTACT_FORM))
    runCatching { context.startActivity(intent) }
}

private fun openPlayStoreRating(context: android.content.Context) {
    val packageName = context.packageName.removeSuffix(".debug")
    val marketIntent = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("market://details?id=$packageName")
    )
    runCatching {
        context.startActivity(marketIntent)
    }.getOrElse {
        val webIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
        )
        runCatching { context.startActivity(webIntent) }
    }
}

@Composable
private fun TutorialOverlay(
    kind: AppTutorialKind,
    onStepChanged: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val content = tutorialContent(kind)
    var stepIndex by rememberSaveable(kind) { mutableStateOf(0) }
    val step = content.steps[stepIndex]
    val isLastStep = stepIndex == content.steps.lastIndex
    val anchorRegistry = LocalTutorialAnchors.current
    val targetBounds = step.targetId?.let(anchorRegistry.bounds::get)
    val density = LocalDensity.current
    var calloutSize by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(kind, stepIndex) {
        onStepChanged(stepIndex)
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val screenHeight = constraints.maxHeight
        val edgePadding = with(density) { 16.dp.roundToPx() }
        val targetGap = with(density) { 14.dp.roundToPx() }
        val calloutY = when {
            calloutSize.height == 0 -> edgePadding
            targetBounds == null -> ((screenHeight - calloutSize.height) / 2)
                .coerceAtLeast(edgePadding)
            targetBounds.bottom.roundToInt() + targetGap + calloutSize.height <=
                screenHeight - edgePadding -> targetBounds.bottom.roundToInt() + targetGap
            else -> (targetBounds.top.roundToInt() - targetGap - calloutSize.height)
                .coerceAtLeast(edgePadding)
        }

        TutorialScrim(targetBounds = targetBounds)
        targetBounds?.let {
            TutorialTargetFrame(
                bounds = it,
                screenWidthPx = constraints.maxWidth,
                screenHeightPx = constraints.maxHeight
            )
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .onSizeChanged { calloutSize = it }
                .offset { IntOffset(0, calloutY) }
                .shadow(
                    elevation = 14.dp,
                    shape = RoundedCornerShape(10.dp),
                    ambientColor = Grimoire.Gold,
                    spotColor = Grimoire.Gold
                )
                .grimoireBorder(10.dp),
            color = Grimoire.ParchmentLight,
            shape = RoundedCornerShape(10.dp),
            shadowElevation = 10.dp
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = step.target,
                    style = MaterialTheme.typography.titleMedium,
                    color = Grimoire.BurgundyDeep,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = step.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Grimoire.Ink
                )
                Text(
                    text = "${stepIndex + 1} / ${content.steps.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Grimoire.InkFaded,
                    modifier = Modifier.padding(top = 2.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("閉じる", color = Grimoire.InkFaded)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (isLastStep) {
                                onDismiss()
                            } else {
                                stepIndex += 1
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Grimoire.Burgundy)
                    ) {
                        Text(if (isLastStep) "OK" else "次へ")
                    }
                }
            }
        }
    }
}

@Composable
private fun TutorialScrim(targetBounds: Rect?) {
    val density = LocalDensity.current
    val spotlightPadding = with(density) { 8.dp.toPx() }
    val cornerRadius = with(density) { 12.dp.toPx() }
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                compositingStrategy = CompositingStrategy.Offscreen
            }
    ) {
        drawRect(Color.Black.copy(alpha = 0.58f))
        targetBounds?.let { bounds ->
            val left = (bounds.left - spotlightPadding).coerceIn(0f, size.width)
            val top = (bounds.top - spotlightPadding).coerceIn(0f, size.height)
            val right = (bounds.right + spotlightPadding).coerceIn(left, size.width)
            val bottom = (bounds.bottom + spotlightPadding).coerceIn(top, size.height)
            drawRoundRect(
                color = Color.Transparent,
                topLeft = Offset(left, top),
                size = Size(right - left, bottom - top),
                cornerRadius = CornerRadius(cornerRadius),
                blendMode = BlendMode.Clear
            )
        }
    }
}

@Composable
private fun TutorialTargetFrame(
    bounds: Rect,
    screenWidthPx: Int,
    screenHeightPx: Int
) {
    val density = LocalDensity.current
    val paddingPx = with(density) { 8.dp.toPx() }
    val edgePx = with(density) { 3.dp.toPx() }
    val leftPx = (bounds.left - paddingPx).coerceIn(edgePx, screenWidthPx - edgePx)
    val topPx = (bounds.top - paddingPx).coerceIn(edgePx, screenHeightPx - edgePx)
    val rightPx = (bounds.right + paddingPx).coerceIn(leftPx, screenWidthPx - edgePx)
    val bottomPx = (bounds.bottom + paddingPx).coerceIn(topPx, screenHeightPx - edgePx)
    val left = leftPx.roundToInt()
    val top = topPx.roundToInt()
    val width = with(density) { (rightPx - leftPx).toDp() }
    val height = with(density) { (bottomPx - topPx).toDp() }
    val shape = RoundedCornerShape(12.dp)

    Box(
        modifier = Modifier
            .offset { IntOffset(left, top) }
            .width(width)
            .height(height)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .blur(7.dp)
                .border(5.dp, Grimoire.Gold.copy(alpha = 0.72f), shape)
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .shadow(
                    elevation = 18.dp,
                    shape = shape,
                    clip = false,
                    ambientColor = Grimoire.Gold,
                    spotColor = Grimoire.Gold
                )
                .border(2.dp, Grimoire.Gold, shape)
        )
    }
}

private data class TutorialContent(
    val steps: List<TutorialStep>
)

private data class TutorialStep(
    val target: String,
    val message: String,
    val targetId: String? = null
)

private fun tutorialContent(kind: AppTutorialKind): TutorialContent = when (kind) {
    AppTutorialKind.Chats -> TutorialContent(
        steps = listOf(
            TutorialStep(
                "TODAY'S WORLD",
                "毎朝世界のトップニュースが配信されます。気になる記事の Talk から、その話題でChatを立ち上げられます。",
                TutorialTargets.CHATS_DAILY_NEWS
            ),
            TutorialStep(
                "+ New Chat",
                "ニュース以外の話題で話したいときは、新しい会話をここから始めます。",
                TutorialTargets.CHATS_NEW_CHAT
            )
        )
    )
    AppTutorialKind.ChatControls -> TutorialContent(
        steps = listOf(
            TutorialStep(
                "ヘッドフォン",
                "通常と「読み上げ&マイクオート」を切り替えます。オンにすると、コーチの返信を読み上げたあとに英語マイクが自動で起動します。音声入力の送信は手動です。",
                TutorialTargets.CHAT_AUTO_MODE
            ),
            TutorialStep(
                "キラキラ",
                "Quick Assistです。言葉に詰まったとき、日本語で聞くと短い英語候補を出します。",
                TutorialTargets.CHAT_QUICK_ASSIST
            ),
            TutorialStep(
                "地球マーク",
                "最新の話題など、検索を伴うチャットをしたいときにオンにします。",
                TutorialTargets.CHAT_WEB_SEARCH
            ),
            TutorialStep(
                "マイク",
                "「英」は英語認識、「日」は日本語認識の音声入力です。話す言語に合わせて選べます。",
                TutorialTargets.CHAT_MIC
            )
        )
    )
    AppTutorialKind.Vocabulary -> TutorialContent(
        steps = listOf(
            TutorialStep("Vocabulary List", "Chatで登場した単語やイディオムは自動で登録されます。"),
            TutorialStep("Card color", "頻出する用語は、会話で出るほど色が濃くなっていきます。"),
            TutorialStep("＋", "必要な単語は＋ボタンから手動でも登録できます。")
        )
    )
    AppTutorialKind.Progress -> TutorialContent(
        steps = listOf(
            TutorialStep("Conversation Analysis", "会話の履歴が溜まると、Progress画面から、自分の話し方の特徴や多い失敗を分析できます。"),
            TutorialStep("Practice", "分析結果はカードで保存されます。詳細から、苦手な表現を練習する会話も始められます。")
        )
    )
    AppTutorialKind.Settings -> TutorialContent(
        steps = listOf(
            TutorialStep(
                "API Key",
                "ここにAPIキーを入力し、利用するモデルを選びます。変更後は画面下の「設定を保存」を押してください。",
                TutorialTargets.SETTINGS_API_KEY
            ),
            TutorialStep(
                "ヘルプ",
                "APIキーの取得方法がわからない場合は、ここをタップして設定ガイドを確認できます。",
                TutorialTargets.SETTINGS_HELP
            ),
            TutorialStep(
                "レベル、コーチ設定",
                "会話レベルやコーチの話し方は「レベル、コーチ設定」で変更できます。次へ進むと、この画面へ切り替わります。",
                TutorialTargets.SETTINGS_COACH
            ),
            TutorialStep(
                "CEFRレベル",
                "CEFRレベルを選ぶと、会話の語彙・文法・返答の難しさを調整できます。",
                TutorialTargets.SETTINGS_CEFR
            ),
            TutorialStep(
                "Coach Personalities & Skills",
                "性格、話し方、添削方法、解説の詳しさなどを自由に指定できます。",
                TutorialTargets.SETTINGS_COACH_SKILL
            )
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScaffold(
    viewModel: AppViewModel,
    onEnglishMic: (Long) -> Unit,
    onJapaneseMic: (Long) -> Unit,
    onAssistMic: () -> Unit,
    onSpeak: (Long?, String) -> Unit,
    speakingMessageId: Long?,
    onPauseSpeaking: () -> Unit,
    onReplayOnboarding: () -> Unit,
    onOpenConsentOnboarding: () -> Unit
) {
    val density = LocalDensity.current
    val isKeyboardVisible = WindowInsets.ime.getBottom(density) > 0
    val soundEffects = LocalAppSoundEffects.current

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val compactChrome = maxHeight < 700.dp

        Scaffold(
            topBar = {
                if (compactChrome) {
                    CompactAppHeader()
                } else {
                    Column {
                        CenterAlignedTopAppBar(
                            title = {
                                Text(
                                    text = "BYOKey Speak",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = Grimoire.BurgundyDeep,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                                containerColor = Color.Transparent
                            )
                        )
                        OrnamentDivider(modifier = Modifier.fillMaxWidth())
                    }
                }
            },
            bottomBar = {
                if (!isKeyboardVisible) {
                    NavigationBar(
                        modifier = if (compactChrome) Modifier.height(76.dp) else Modifier,
                        containerColor = Grimoire.ParchmentDeep
                    ) {
                        MainTab.entries.forEach { tab ->
                            NavigationBarItem(
                                selected = viewModel.currentTab == tab,
                                onClick = {
                                    if (viewModel.currentTab != tab) {
                                        soundEffects?.play(AppSound.Pages)
                                        viewModel.currentTab = tab
                                    }
                                    if (tab == MainTab.Chats) viewModel.openQuickChat(null)
                                },
                                icon = {
                                    Icon(
                                        imageVector = tab.icon(),
                                        contentDescription = tab.label,
                                        modifier = if (compactChrome) Modifier.size(20.dp) else Modifier
                                    )
                                },
                                label = {
                                    Text(
                                        text = tab.label,
                                        style = if (compactChrome) {
                                            MaterialTheme.typography.labelSmall
                                        } else {
                                            MaterialTheme.typography.labelMedium
                                        },
                                        maxLines = 1
                                    )
                                },
                                alwaysShowLabel = !compactChrome,
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
                }
            },
            containerColor = Color.Transparent
        ) { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
            ) {
                if (viewModel.showExternalDataConsent) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .grimoireBorder(10.dp),
                        color = Grimoire.ParchmentLight,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "利用前に案内文の同意を確認してください。",
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall,
                                color = Grimoire.Ink
                            )
                            TextButton(onClick = onOpenConsentOnboarding) {
                                Text("案内文を開く", color = Grimoire.Burgundy)
                            }
                        }
                    }
                }
                when (viewModel.currentTab) {
                    MainTab.Chats -> ChatsScreen(
                        viewModel = viewModel,
                        onEnglishMic = onEnglishMic,
                        onJapaneseMic = onJapaneseMic,
                        onAssistMic = onAssistMic,
                        onSpeak = onSpeak,
                        speakingMessageId = speakingMessageId,
                        onPauseSpeaking = onPauseSpeaking,
                        compactLayout = compactChrome,
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
                        onReplayOnboarding = onReplayOnboarding,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactAppHeader() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(WindowInsets.statusBars.asPaddingValues())
            .padding(top = 4.dp, bottom = 4.dp)
    ) {
        Text(
            text = "BYOKey Speak",
            modifier = Modifier.align(Alignment.CenterHorizontally),
            style = MaterialTheme.typography.titleMedium,
            color = Grimoire.BurgundyDeep,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        OrnamentDivider(modifier = Modifier.fillMaxWidth())
    }
}

private fun MainTab.icon(): ImageVector = when (this) {
    MainTab.Chats -> Icons.Default.ChatBubbleOutline
    MainTab.Review -> Icons.AutoMirrored.Filled.MenuBook
    MainTab.Progress -> Icons.Default.Insights
    MainTab.Settings -> Icons.Default.Settings
}
