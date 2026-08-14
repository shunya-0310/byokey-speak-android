package com.byokeylab.speak.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.byokeylab.speak.AppLinks
import com.byokeylab.speak.AppSettings
import com.byokeylab.speak.AppSound
import com.byokeylab.speak.AppStorage
import com.byokeylab.speak.AppTheme
import com.byokeylab.speak.AppTutorialKind
import com.byokeylab.speak.AppViewModel
import com.byokeylab.speak.EnglishLevel
import com.byokeylab.speak.Grimoire
import com.byokeylab.speak.LocalAppSoundEffects
import com.byokeylab.speak.Guides
import com.byokeylab.speak.LlmProvider
import com.byokeylab.speak.ModelOption
import com.byokeylab.speak.ReminderScheduler
import com.byokeylab.speak.TutorialTargets
import com.byokeylab.speak.VoiceGender
import com.byokeylab.speak.grimoireBorder
import com.byokeylab.speak.modelOptions
import com.byokeylab.speak.tutorialAnchor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Settings (8.5): API設定、接続テスト(FR-002)、CEFR、
 * Coach Personalities & Skills(FR-006)、通知、データ削除(FR-010)、リンク類。
 */
private enum class SettingsSection(val label: String) {
    System("システム設定"),
    Coach("レベル、コーチ設定"),
    Backup("バックアップ／復元"),
    Data("データ削除"),
    Help("ヘルプ"),
    About("About")
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun SettingsScreen(
    viewModel: AppViewModel,
    onReplayOnboarding: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val settings = viewModel.settings
    val scope = rememberCoroutineScope()
    val soundEffects = LocalAppSoundEffects.current

    var provider by rememberSaveable(settings.provider) { mutableStateOf(settings.provider) }
    // APIキーはsavedInstanceStateへ残さない (NFR-001)
    var apiKey by androidx.compose.runtime.remember(settings.apiKey) { mutableStateOf(settings.apiKey) }
    var model by rememberSaveable(settings.model) { mutableStateOf(settings.model) }
    var level by rememberSaveable(settings.englishLevel) { mutableStateOf(settings.englishLevel) }
    var coachInstructions by rememberSaveable(settings.coachInstructions) { mutableStateOf(settings.coachInstructions) }
    var reminderEnabled by rememberSaveable(settings.dailyReminderEnabled) { mutableStateOf(settings.dailyReminderEnabled) }
    var theme by rememberSaveable(settings.theme) { mutableStateOf(settings.theme) }
    var voiceGender by rememberSaveable(settings.voiceGender) { mutableStateOf(settings.voiceGender) }
    var soundEffectsEnabled by rememberSaveable(settings.soundEffectsEnabled) {
        mutableStateOf(settings.soundEffectsEnabled)
    }

    var isTesting by rememberSaveable { mutableStateOf(false) }
    var testResult by rememberSaveable { mutableStateOf<String?>(null) }
    var savedMessage by rememberSaveable { mutableStateOf(false) }
    var deleteConfirm by rememberSaveable { mutableStateOf<DeleteAction?>(null) }
    var selectedSection by rememberSaveable { mutableStateOf(SettingsSection.System) }
    var backupDialog by remember { mutableStateOf<BackupDialogMode?>(null) }
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }
    var pendingExportPassphrase by remember { mutableStateOf("") }
    val settingsScrollState = rememberScrollState()
    val coachSkillRequester = remember { BringIntoViewRequester() }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val passphrase = pendingExportPassphrase
        pendingExportPassphrase = ""
        if (uri != null && passphrase.isNotEmpty()) {
            viewModel.exportBackup(uri, passphrase.toCharArray())
        }
    }
    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            pendingRestoreUri = uri
            backupDialog = BackupDialogMode.Restore
        }
    }

    LaunchedEffect(viewModel.activeTutorial, viewModel.activeTutorialStep) {
        if (viewModel.activeTutorial != AppTutorialKind.Settings) return@LaunchedEffect
        selectedSection = if (viewModel.activeTutorialStep >= 3) {
            SettingsSection.Coach
        } else {
            SettingsSection.System
        }
        delay(120)
        if (viewModel.activeTutorialStep >= 4) {
            coachSkillRequester.bringIntoView()
        } else {
            settingsScrollState.animateScrollTo(0)
        }
    }

    fun buildSettings(): AppSettings = AppSettings(
        provider = provider,
        apiKey = apiKey.trim(),
        model = model.trim().ifBlank { provider.defaultModel },
        dailyReminderEnabled = reminderEnabled,
        reminderHour = ReminderScheduler.DEFAULT_NEWS_HOUR,
        reminderMinute = ReminderScheduler.DEFAULT_NEWS_MINUTE,
        englishLevel = level,
        coachInstructions = coachInstructions.trim().ifBlank { AppStorage.DEFAULT_COACH_INSTRUCTIONS },
        theme = theme,
        voiceGender = voiceGender,
        soundEffectsEnabled = soundEffectsEnabled
    )

    val hasUnsavedChanges = buildSettings() != settings
    val saveCompleted = savedMessage && !hasUnsavedChanges

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            text = "✦ Settings",
            style = MaterialTheme.typography.titleMedium,
            color = Grimoire.BurgundyDeep,
            modifier = Modifier.padding(top = 8.dp)
        )
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            SettingsSection.entries.forEach { section ->
                FilterChip(
                    selected = selectedSection == section,
                    modifier = when (section) {
                        SettingsSection.Help ->
                            Modifier.tutorialAnchor(TutorialTargets.SETTINGS_HELP)
                        SettingsSection.Coach ->
                            Modifier.tutorialAnchor(TutorialTargets.SETTINGS_COACH)
                        else -> Modifier
                    },
                    onClick = {
                        if (selectedSection != section) {
                            soundEffects?.play(AppSound.Select)
                            selectedSection = section
                        }
                    },
                    label = { Text(section.label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Grimoire.Burgundy,
                        selectedLabelColor = Grimoire.ParchmentLight
                    )
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(settingsScrollState),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            when (selectedSection) {
                SettingsSection.System -> {
                    SectionCard(title = "API") {
                        Text("LLM provider", style = MaterialTheme.typography.labelLarge, color = Grimoire.Ink)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            LlmProvider.entries.forEach { candidate ->
                                FilterChip(
                                    selected = provider == candidate,
                                    onClick = {
                                        if (provider != candidate) {
                                            soundEffects?.play(AppSound.Select)
                                            provider = candidate
                                            model = candidate.defaultModel
                                            apiKey = ""
                                            testResult = null
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
                        OutlinedTextField(
                            value = apiKey,
                            onValueChange = { apiKey = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .tutorialAnchor(TutorialTargets.SETTINGS_API_KEY),
                            label = { Text("${provider.label} API key") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation()
                        )
                        OutlinedTextField(
                            value = model,
                            onValueChange = { model = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Model ID") },
                            singleLine = true,
                            placeholder = { Text(provider.defaultModel) }
                        )
                        Text(
                            text = "主なモデル (タップで選択、右のアイコンでIDをコピー):",
                            style = MaterialTheme.typography.labelMedium,
                            color = Grimoire.Ink
                        )
                        provider.modelOptions.forEach { option ->
                            ModelOptionRow(
                                option = option,
                                selected = model.trim() == option.id,
                                onSelect = {
                                    if (model.trim() != option.id) {
                                        soundEffects?.play(AppSound.Select)
                                        model = option.id
                                    }
                                }
                            )
                        }
                        Text(
                            text = "上記は${provider.label}の代表的なモデルです。最新のモデルや料金、プロバイダー側の課金設定は各公式サイトで確認してください。他のモデルIDを直接入力することもできます。",
                            style = MaterialTheme.typography.bodySmall,
                            color = Grimoire.InkFaded
                        )
                        TextButton(onClick = { openUrl(context, AppLinks.PRICING_SIMULATOR) }) {
                            Text("料金シミュレーションを開く", color = Grimoire.GoldDark)
                        }
                        TextButton(onClick = { openUrl(context, AppLinks.API_GUIDE) }) {
                            Text("APIキーの取得・設定ガイドを開く", color = Grimoire.GoldDark)
                        }
                        Button(
                            onClick = {
                                isTesting = true
                                testResult = null
                                scope.launch {
                                    val error = viewModel.testConnection(
                                        provider,
                                        apiKey.trim(),
                                        model.trim().ifBlank { provider.defaultModel }
                                    )
                                    testResult = if (error == null) "接続に成功しました。" else error.kind.userMessage
                                    isTesting = false
                                }
                            },
                            enabled = apiKey.isNotBlank() && !isTesting,
                            colors = ButtonDefaults.buttonColors(containerColor = Grimoire.Burgundy)
                        ) {
                            if (isTesting) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(if (isTesting) "テスト中..." else "接続テスト")
                        }
                        testResult?.let { result ->
                            Text(
                                text = result,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (result.startsWith("接続に成功")) Grimoire.Moss else Grimoire.Burgundy
                            )
                        }
                        Text(
                            text = "APIキーはこの端末内で暗号化して保存され、BYOKey Labのサーバーには送信されません。",
                            style = MaterialTheme.typography.bodySmall,
                            color = Grimoire.InkFaded
                        )
                    }
                    SectionCard(title = "通知") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Daily News通知", modifier = Modifier.weight(1f), color = Grimoire.Ink)
                            Switch(
                                checked = reminderEnabled,
                                onCheckedChange = {
                                    soundEffects?.play(AppSound.Select)
                                    reminderEnabled = it
                                }
                            )
                        }
                        Text(
                            text = "オンにすると、毎朝6:30頃にDaily Newsの配信をお知らせします。",
                            style = MaterialTheme.typography.bodySmall,
                            color = Grimoire.InkFaded
                        )
                    }
                    SectionCard(title = "テーマ") {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            AppTheme.entries.forEach { candidate ->
                                FilterChip(
                                    selected = theme == candidate,
                                    onClick = {
                                        if (theme != candidate) {
                                            soundEffects?.play(AppSound.Select)
                                            theme = candidate
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
                    SectionCard(title = "効果音") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("効果音を鳴らす", modifier = Modifier.weight(1f), color = Grimoire.Ink)
                            Switch(
                                checked = soundEffectsEnabled,
                                onCheckedChange = {
                                    soundEffectsEnabled = it
                                    if (it) soundEffects?.play(AppSound.Select)
                                }
                            )
                        }
                        Text(
                            text = "タブ移動、選択、送受信などの短い効果音を切り替えます。",
                            style = MaterialTheme.typography.bodySmall,
                            color = Grimoire.InkFaded
                        )
                    }
                }

                SettingsSection.Coach -> {
                    SectionCard(
                        title = "CEFRレベル",
                        modifier = Modifier.tutorialAnchor(TutorialTargets.SETTINGS_CEFR)
                    ) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            EnglishLevel.entries.forEach { candidate ->
                                FilterChip(
                                    selected = level == candidate,
                                    onClick = {
                                        if (level != candidate) {
                                            soundEffects?.play(AppSound.Select)
                                            level = candidate
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
                        val guide = cefrGuide(level)
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = Grimoire.ParchmentDeep,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = guide.overview,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Grimoire.Ink
                                )
                                Text(
                                    text = "出力の目安：${guide.output}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Grimoire.InkFaded
                                )
                            }
                        }
                    }
                    SectionCard(
                        title = "コーチスキル",
                        modifier = Modifier
                            .bringIntoViewRequester(coachSkillRequester)
                            .tutorialAnchor(TutorialTargets.SETTINGS_COACH_SKILL)
                    ) {
                        OutlinedTextField(
                            value = coachInstructions,
                            onValueChange = { coachInstructions = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Coach Personalities & Skills") },
                            minLines = 3,
                            maxLines = 6,
                            placeholder = { Text(AppStorage.DEFAULT_COACH_INSTRUCTIONS) }
                        )
                        Text(
                            text = "コーチの性格・添削方針・解説の言語や詳しさを自由に指定できます。",
                            style = MaterialTheme.typography.bodySmall,
                            color = Grimoire.InkFaded
                        )
                    }
                    SectionCard(title = "読み上げの声") {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            VoiceGender.entries.forEach { candidate ->
                                FilterChip(
                                    selected = voiceGender == candidate,
                                    onClick = {
                                        if (voiceGender != candidate) {
                                            soundEffects?.play(AppSound.Select)
                                            voiceGender = candidate
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
                        Text(
                            text = "※端末に入っている音声によっては、声の違いが小さい場合があります。",
                            style = MaterialTheme.typography.bodySmall,
                            color = Grimoire.InkFaded
                        )
                    }
                }

                SettingsSection.Backup -> {
                    SectionCard(title = "バックアップ／復元") {
                        Text(
                            text = "機種変更や端末故障に備えて、会話履歴、Vocabulary List、進捗、会話分析、APIキー以外の設定を暗号化ファイルに保存できます。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Grimoire.Ink
                        )
                        Text(
                            text = "APIキーはバックアップに含まれません。新しい端末では、復元後にAPIキーを再入力してください。パスフレーズを忘れると復元できません。",
                            style = MaterialTheme.typography.bodySmall,
                            color = Grimoire.Burgundy
                        )
                        Button(
                            onClick = {
                                viewModel.clearBackupMessage()
                                backupDialog = BackupDialogMode.Create
                            },
                            enabled = !viewModel.backupState.working,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Grimoire.Burgundy)
                        ) {
                            Text("バックアップを作成")
                        }
                        OutlinedButton(
                            onClick = {
                                viewModel.clearBackupMessage()
                                restoreLauncher.launch(arrayOf("application/octet-stream", "application/json", "*/*"))
                            },
                            enabled = !viewModel.backupState.working,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("バックアップから復元", color = Grimoire.Burgundy)
                        }
                        when {
                            viewModel.backupState.working -> Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = Grimoire.GoldDark
                                )
                                Text("処理しています...", color = Grimoire.InkFaded)
                            }
                            viewModel.backupState.error != null -> Text(
                                viewModel.backupState.error.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = Grimoire.Burgundy
                            )
                            viewModel.backupState.message != null -> Text(
                                viewModel.backupState.message.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = Grimoire.Moss
                            )
                        }
                    }
                }

                SettingsSection.Data -> SectionCard(title = "データ削除") {
                    Text(
                        text = "削除したデータは復元できません。",
                        style = MaterialTheme.typography.bodySmall,
                        color = Grimoire.Burgundy
                    )
                    DeleteAction.entries.forEach { action ->
                        OutlinedButton(
                            onClick = { deleteConfirm = action },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(action.label, color = Grimoire.Burgundy)
                        }
                    }
                }

                SettingsSection.Help -> {
                    SectionCard(title = "チュートリアル") {
                        Text(
                            text = "初回案内と各画面の基本操作をもう一度確認できます。",
                            style = MaterialTheme.typography.bodySmall,
                            color = Grimoire.InkFaded
                        )
                        Button(
                            onClick = {
                                soundEffects?.play(AppSound.Select)
                                onReplayOnboarding()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Grimoire.Burgundy)
                        ) {
                            Text("初回案内を再表示")
                        }
                        Button(
                            onClick = {
                                soundEffects?.play(AppSound.Select)
                                viewModel.replayTutorials()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Grimoire.Burgundy)
                        ) {
                            Text("チュートリアルを再表示")
                        }
                    }
                    SectionCard(title = "ガイド") {
                        Text(
                            text = "BYOKの仕組みやAPIキーの設定方法は、いつでもここから確認できます。",
                            style = MaterialTheme.typography.bodySmall,
                            color = Grimoire.InkFaded
                        )
                        Guides.all.forEach { guide ->
                            TextButton(
                                onClick = { guide.assetPath?.let { Guides.open(context, it) } },
                                enabled = guide.assetPath != null
                            ) {
                                Text(
                                    text = guide.title,
                                    color = if (guide.assetPath != null) Grimoire.GoldDark else Grimoire.InkFaded
                                )
                            }
                            if (guide.platformLabel != null && guide.platformUrl != null) {
                                TextButton(
                                    onClick = { openUrl(context, guide.platformUrl) },
                                    modifier = Modifier.padding(start = 24.dp)
                                ) {
                                    Text(
                                        text = "↳ 公式: ${guide.platformLabel}を開く",
                                        color = Grimoire.GoldDark
                                    )
                                }
                            }
                        }
                    }
                }

                SettingsSection.About -> {
                    SectionCard(title = "About") {
                        TextButton(onClick = { openUrl(context, AppLinks.PRIVACY_POLICY) }) {
                            Text("プライバシーポリシー", color = Grimoire.GoldDark)
                        }
                        TextButton(onClick = { openUrl(context, AppLinks.TERMS_OF_USE) }) {
                            Text("利用規約", color = Grimoire.GoldDark)
                        }
                        TextButton(onClick = { openUrl(context, AppLinks.SUPPORT) }) {
                            Text("サポート", color = Grimoire.GoldDark)
                        }
                        TextButton(onClick = { openUrl(context, AppLinks.CONTACT_FORM) }) {
                            Text("お問い合わせ・フィードバック", color = Grimoire.GoldDark)
                        }
                        Text(
                            text = "会話内容とコーチ設定は、選択したLLMプロバイダーへ送信されます。BYOKey Labは通常の会話やAPIキーを収集しません。不適切なAI返信を報告した場合のみ、選択した返信、理由、任意で直前のユーザー発言が送信されます。",
                            style = MaterialTheme.typography.bodySmall,
                            color = Grimoire.InkFaded
                        )
                    }
                    SectionCard(title = "AI利用上の注意") {
                        Text(
                            text = "AIの回答には誤りや不適切な内容が含まれる場合があります。重要な判断は一次情報で確認してください。各AI返信の旗アイコンから内容を報告できます。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Grimoire.Ink
                        )
                    }
                    SectionCard(title = "返金対応について") {
                        Text(
                            text = "どうしてもAPIキーの設定が難しく会話することができない、アプリの不具合で使用できない、などの場合は、アプリ購入後2週間以内に限り返金対応いたします。お問い合わせフォームより注文番号と理由を添えてご連絡ください。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Grimoire.Ink
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        if (selectedSection == SettingsSection.System || selectedSection == SettingsSection.Coach) {
            Button(
                onClick = {
                    soundEffects?.play(AppSound.Decision)
                    viewModel.saveSettings(buildSettings())
                    savedMessage = true
                },
                enabled = hasUnsavedChanges,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2563EB),
                    contentColor = Color.White,
                    disabledContainerColor = if (saveCompleted) Grimoire.Moss else Color(0xFF8C8C8C),
                    disabledContentColor = Color.White
                )
            ) {
                Text(if (saveCompleted) "設定を保存しました" else "設定を保存")
            }
            Spacer(modifier = Modifier.height(8.dp))
        } else {
            Spacer(modifier = Modifier.height(8.dp))
        }
    }

    deleteConfirm?.let { action ->
        AlertDialog(
            onDismissRequest = { deleteConfirm = null },
            containerColor = Grimoire.ParchmentLight,
            title = { Text(action.label, color = Grimoire.BurgundyDeep) },
            text = { Text("${action.description}\n\n削除後は復元できません。実行しますか?", color = Grimoire.Ink) },
            confirmButton = {
                Button(
                    onClick = {
                        when (action) {
                            DeleteAction.ApiKey -> {
                                viewModel.deleteApiKey()
                                apiKey = ""
                            }
                            DeleteAction.Conversations -> viewModel.deleteConversations()
                            DeleteAction.Notes -> viewModel.deleteNotes()
                            DeleteAction.Progress -> viewModel.deleteProgress()
                            DeleteAction.Everything -> {
                                viewModel.deleteEverything()
                                apiKey = ""
                            }
                        }
                        deleteConfirm = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Grimoire.Burgundy)
                ) { Text("削除する") }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirm = null }) {
                    Text("キャンセル", color = Grimoire.InkFaded)
                }
            }
        )
    }

    backupDialog?.let { mode ->
        BackupPassphraseDialog(
            mode = mode,
            onDismiss = {
                backupDialog = null
                pendingRestoreUri = null
            },
            onConfirm = { passphrase ->
                backupDialog = null
                when (mode) {
                    BackupDialogMode.Create -> {
                        pendingExportPassphrase = passphrase
                        exportLauncher.launch("BYOKey-Speak-backup.byokbackup")
                    }
                    BackupDialogMode.Restore -> {
                        val uri = pendingRestoreUri
                        pendingRestoreUri = null
                        if (uri != null) viewModel.restoreBackup(uri, passphrase.toCharArray())
                    }
                }
            }
        )
    }
}

private enum class BackupDialogMode { Create, Restore }

@Composable
private fun BackupPassphraseDialog(
    mode: BackupDialogMode,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var passphrase by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    val isCreate = mode == BackupDialogMode.Create
    val valid = passphrase.length >= 8 && (!isCreate || passphrase == confirmation)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Grimoire.ParchmentLight,
        title = {
            Text(
                if (isCreate) "バックアップを暗号化" else "バックアップを復元",
                color = Grimoire.BurgundyDeep
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (isCreate) {
                        "8文字以上のパスフレーズを設定してください。このパスフレーズは端末にもBYOKey Labにも保存されません。"
                    } else {
                        "バックアップ作成時に設定したパスフレーズを入力してください。復元すると、現在の会話・学習データはバックアップ内容に置き換わります。"
                    },
                    color = Grimoire.Ink
                )
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    label = { Text("パスフレーズ") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true
                )
                if (isCreate) {
                    OutlinedTextField(
                        value = confirmation,
                        onValueChange = { confirmation = it },
                        label = { Text("パスフレーズ（確認）") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true
                    )
                }
                if (passphrase.isNotEmpty() && passphrase.length < 8) {
                    Text("8文字以上で入力してください。", color = Grimoire.Burgundy)
                } else if (isCreate && confirmation.isNotEmpty() && passphrase != confirmation) {
                    Text("確認用パスフレーズが一致しません。", color = Grimoire.Burgundy)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(passphrase) },
                enabled = valid,
                colors = ButtonDefaults.buttonColors(containerColor = Grimoire.Burgundy)
            ) {
                Text(if (isCreate) "保存先を選ぶ" else "復元する")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("キャンセル", color = Grimoire.InkFaded)
            }
        }
    )
}

@Composable
private fun ModelOptionRow(
    option: ModelOption,
    selected: Boolean,
    onSelect: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .grimoireBorder(8.dp)
            .clickable(onClick = onSelect),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) Grimoire.ParchmentDeep else Grimoire.ParchmentLight
        ),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = option.id,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Grimoire.Ink
                    )
                    if (option.recommended) {
                        Text(
                            text = " 【推奨】",
                            style = MaterialTheme.typography.labelMedium,
                            color = Grimoire.Burgundy
                        )
                    }
                    if (selected) {
                        Text(
                            text = " ✓",
                            style = MaterialTheme.typography.labelMedium,
                            color = Grimoire.Moss
                        )
                    }
                }
                Text(
                    text = option.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = Grimoire.InkFaded
                )
            }
            IconButton(onClick = {
                clipboard.setText(AnnotatedString(option.id))
            }) {
                Icon(
                    Icons.Default.ContentCopy,
                    contentDescription = "モデルIDをコピー",
                    tint = Grimoire.GoldDark
                )
            }
        }
    }
}

private enum class DeleteAction(val label: String, val description: String) {
    ApiKey("APIキーを削除", "保存されたAPIキーを端末から削除します。"),
    Conversations("会話履歴を削除", "CoachとChatsのすべての会話を削除します。"),
    Notes("単語帳を削除", "Reviewの単語帳カードをすべて削除します。"),
    Progress("進捗データを削除", "ストリークや学習統計を削除します。"),
    Everything("全ローカルデータを削除", "APIキー、会話、メモ、進捗、設定のすべてを削除して初期状態に戻します。")
}

private data class CefrGuide(
    val overview: String,
    val output: String
)

private fun cefrGuide(level: EnglishLevel): CefrGuide = when (level) {
    EnglishLevel.A1 -> CefrGuide(
        overview = "学習を始めた段階。身近な単語と短い定型表現を中心に会話します。",
        output = "1文8語以内を基本に、質問は1つ。難しい語には短い日本語ヒントを付けます。"
    )
    EnglishLevel.A2 -> CefrGuide(
        overview = "身近な話題なら、簡単な受け答えを続けられる段階です。",
        output = "1文12語以内を基本に、接続詞を使った短い文と新しい表現を1つ提示します。"
    )
    EnglishLevel.B1 -> CefrGuide(
        overview = "経験・理由・意見を、日常的な英語で説明できる段階です。",
        output = "1文15語程度で会話し、一般的なイディオムには短い説明を添えます。"
    )
    EnglishLevel.B2 -> CefrGuide(
        overview = "幅広い話題で、比較や議論を含む自然な会話ができる段階です。",
        output = "文法を過度に簡略化せず、ニュアンスや一般的なイディオムも使います。"
    )
    EnglishLevel.C1 -> CefrGuide(
        overview = "抽象的な話題でも、目的に合う表現を柔軟に使える段階です。",
        output = "正確で自然な英語を使い、語調・文体・細かなニュアンスも扱います。"
    )
    EnglishLevel.C2 -> CefrGuide(
        overview = "ほぼすべての話題を、細かな意味の違いまで表現できる段階です。",
        output = "簡略化せず、自然なリズム、含意、文体、イディオムまで含めて応答します。"
    )
}

@Composable
private fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .grimoireBorder(10.dp),
        colors = CardDefaults.cardColors(containerColor = Grimoire.ParchmentLight),
        shape = RoundedCornerShape(10.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "✦ $title",
                style = MaterialTheme.typography.labelLarge,
                color = Grimoire.Burgundy
            )
            content()
        }
    }
}

private fun openUrl(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}
