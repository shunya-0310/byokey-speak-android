package com.byokeylab.speak.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.byokeylab.speak.AppLinks
import com.byokeylab.speak.AppSettings
import com.byokeylab.speak.AppStorage
import com.byokeylab.speak.AppViewModel
import com.byokeylab.speak.EnglishLevel
import com.byokeylab.speak.Grimoire
import com.byokeylab.speak.LlmProvider
import com.byokeylab.speak.grimoireBorder
import kotlinx.coroutines.launch

/**
 * Settings (8.5): API設定、接続テスト(FR-002)、CEFR、
 * Coach Personalities & Skills(FR-006)、通知、データ削除(FR-010)、リンク類。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val settings = viewModel.settings
    val scope = rememberCoroutineScope()

    var provider by rememberSaveable(settings.provider) { mutableStateOf(settings.provider) }
    // APIキーはsavedInstanceStateへ残さない (NFR-001)
    var apiKey by androidx.compose.runtime.remember(settings.apiKey) { mutableStateOf(settings.apiKey) }
    var model by rememberSaveable(settings.model) { mutableStateOf(settings.model) }
    var level by rememberSaveable(settings.englishLevel) { mutableStateOf(settings.englishLevel) }
    var coachInstructions by rememberSaveable(settings.coachInstructions) { mutableStateOf(settings.coachInstructions) }
    var reminderEnabled by rememberSaveable(settings.dailyReminderEnabled) { mutableStateOf(settings.dailyReminderEnabled) }
    var hourText by rememberSaveable(settings.reminderHour) { mutableStateOf(settings.reminderHour.toString()) }
    var minuteText by rememberSaveable(settings.reminderMinute) { mutableStateOf(settings.reminderMinute.toString().padStart(2, '0')) }

    var isTesting by rememberSaveable { mutableStateOf(false) }
    var testResult by rememberSaveable { mutableStateOf<String?>(null) }
    var savedMessage by rememberSaveable { mutableStateOf(false) }
    var deleteConfirm by rememberSaveable { mutableStateOf<DeleteAction?>(null) }

    fun buildSettings(): AppSettings = AppSettings(
        provider = provider,
        apiKey = apiKey.trim(),
        model = model.trim().ifBlank { provider.defaultModel },
        dailyReminderEnabled = reminderEnabled,
        reminderHour = hourText.toIntOrNull()?.coerceIn(0, 23) ?: 7,
        reminderMinute = minuteText.toIntOrNull()?.coerceIn(0, 59) ?: 15,
        englishLevel = level,
        coachInstructions = coachInstructions.trim().ifBlank { AppStorage.DEFAULT_COACH_INSTRUCTIONS }
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "✦ Settings",
            style = MaterialTheme.typography.titleMedium,
            color = Grimoire.BurgundyDeep,
            modifier = Modifier.padding(top = 8.dp)
        )

        // --- API設定 (FR-002) ---
        SectionCard(title = "API設定") {
            Text(
                text = "LLM provider",
                style = MaterialTheme.typography.labelLarge,
                color = Grimoire.Ink
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                LlmProvider.entries.forEach { candidate ->
                    FilterChip(
                        selected = provider == candidate,
                        onClick = {
                            if (provider != candidate) {
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
                modifier = Modifier.fillMaxWidth(),
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
                text = "モデルIDはAPIリクエストに使うIDです。プロバイダー側の課金設定や利用可能モデルは各公式サイトで確認してください。",
                style = MaterialTheme.typography.bodySmall,
                color = Grimoire.InkFaded
            )
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
                        testResult = if (error == null) {
                            "接続に成功しました。"
                        } else {
                            error.kind.userMessage
                        }
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

        // --- 会話設定 (FR-006) ---
        SectionCard(title = "会話設定") {
            Text("CEFR level", style = MaterialTheme.typography.labelLarge, color = Grimoire.Ink)
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
                text = "日本語でも英語でもOK。コーチの性格・添削方針・解説の言語を自由に指定できます。",
                style = MaterialTheme.typography.bodySmall,
                color = Grimoire.InkFaded
            )
        }

        // --- 通知 ---
        SectionCard(title = "通知") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("朝のトピック通知", modifier = Modifier.weight(1f), color = Grimoire.Ink)
                Switch(checked = reminderEnabled, onCheckedChange = { reminderEnabled = it })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = hourText,
                    onValueChange = { hourText = it.filter { char -> char.isDigit() }.take(2) },
                    label = { Text("Hour") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    enabled = reminderEnabled
                )
                OutlinedTextField(
                    value = minuteText,
                    onValueChange = { minuteText = it.filter { char -> char.isDigit() }.take(2) },
                    label = { Text("Minute") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    enabled = reminderEnabled
                )
            }
        }

        Button(
            onClick = {
                viewModel.saveSettings(buildSettings())
                savedMessage = true
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Grimoire.Burgundy)
        ) {
            Text("設定を保存")
        }
        if (savedMessage) {
            Text(
                text = "保存しました。次の送信から反映されます。",
                style = MaterialTheme.typography.bodySmall,
                color = Grimoire.Moss
            )
        }

        // --- データ削除 (FR-010) ---
        SectionCard(title = "データ削除") {
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

        // --- About / リンク ---
        SectionCard(title = "About") {
            TextButton(onClick = { openUrl(context, AppLinks.PRIVACY_POLICY) }) {
                Text("プライバシーポリシー", color = Grimoire.GoldDark)
            }
            TextButton(onClick = { openUrl(context, AppLinks.SUPPORT) }) {
                Text("サポート", color = Grimoire.GoldDark)
            }
            TextButton(onClick = {
                val intent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("mailto:${AppLinks.CONTACT_EMAIL}")
                    putExtra(Intent.EXTRA_SUBJECT, "BYOKey Speak feedback")
                }
                runCatching { context.startActivity(intent) }
            }) {
                Text("お問い合わせ (${AppLinks.CONTACT_EMAIL})", color = Grimoire.GoldDark)
            }
            Text(
                text = "会話内容とコーチ設定は、選択したLLMプロバイダーにのみ送信されます。BYOKey Labが会話やAPIキーを収集することはありません。",
                style = MaterialTheme.typography.bodySmall,
                color = Grimoire.InkFaded
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
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
}

private enum class DeleteAction(val label: String, val description: String) {
    ApiKey("APIキーを削除", "保存されたAPIキーを端末から削除します。"),
    Conversations("会話履歴を削除", "CoachとChatsのすべての会話を削除します。"),
    Notes("学習メモを削除", "Reviewのすべての学習メモを削除します。"),
    Progress("進捗データを削除", "ストリークや学習統計を削除します。"),
    Everything("全ローカルデータを削除", "APIキー、会話、メモ、進捗、設定のすべてを削除して初期状態に戻します。")
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier
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
