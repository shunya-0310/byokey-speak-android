package com.byokeylab.speak.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.byokeylab.speak.AppViewModel
import com.byokeylab.speak.ConversationAnalysisRecord
import com.byokeylab.speak.Grimoire
import com.byokeylab.speak.RecurringPattern
import com.byokeylab.speak.grimoireBorder
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Date
import java.util.Locale

/**
 * Progress (FR-008): 継続日数、累計、Vocabulary、会話分析の可視化。
 */
@Composable
fun ProgressScreen(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val stats by viewModel.dailyStats.collectAsState()
    val cards by viewModel.vocabCards.collectAsState()
    val analyses by viewModel.analyses.collectAsState()

    val streak = viewModel.streakOf(stats)
    val totalStudyDays = stats.count { it.turns > 0 }
    val totalTurns = stats.sumOf { it.turns }
    val totalCards = cards.size
    val favoriteCards = cards.count { it.favorite }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "✦ Progress",
            style = MaterialTheme.typography.titleMedium,
            color = Grimoire.BurgundyDeep,
            modifier = Modifier.padding(top = 8.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .grimoireBorder(10.dp),
            colors = CardDefaults.cardColors(containerColor = Grimoire.ParchmentDeep),
            shape = RoundedCornerShape(10.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (streak > 0) "✦ 連続 $streak 日 ✦" else "今日から始めよう",
                    style = MaterialTheme.typography.titleLarge,
                    color = Grimoire.Burgundy
                )
                Text(
                    text = "continuous study streak",
                    style = MaterialTheme.typography.labelSmall,
                    color = Grimoire.Gold
                )
            }
        }

        WeeklyActivityRow(studyDays = stats.filter { it.turns > 0 }.map { it.epochDay }.toSet())

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(label = "累計学習日", value = "$totalStudyDays 日", modifier = Modifier.weight(1f))
            StatCard(label = "累計ターン", value = "$totalTurns", modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(label = "単語帳のカード", value = "$totalCards", modifier = Modifier.weight(1f))
            StatCard(label = "お気に入り ★", value = "$favoriteCards", modifier = Modifier.weight(1f))
        }
        EnglishProfileSection(
            analyses = analyses,
            totalUserMessages = totalTurns,
            loading = viewModel.analysisState.loading,
            message = viewModel.analysisState.message,
            error = viewModel.analysisState.error,
            onAnalyze = viewModel::requestConversationAnalysis,
            onPractice = viewModel::startPracticeFromAnalysis
        )

        Spacer(modifier = Modifier.height(16.dp))
    }

    if (viewModel.showAnalysisConsent) {
        AlertDialog(
            onDismissRequest = viewModel::dismissConversationAnalysisConsent,
            containerColor = Grimoire.ParchmentLight,
            title = { Text("会話分析について", color = Grimoire.BurgundyDeep) },
            text = {
                Text(
                    text = "分析対象の会話テキストは、設定中のAIプロバイダーへ送信されます。BYOKey Labが会話や分析結果を受け取ることはありません。分析にはユーザー自身のAPI利用料が発生します。",
                    color = Grimoire.Ink
                )
            },
            confirmButton = {
                Button(
                    onClick = viewModel::confirmConversationAnalysisConsent,
                    colors = ButtonDefaults.buttonColors(containerColor = Grimoire.Burgundy)
                ) {
                    Text("分析する")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissConversationAnalysisConsent) {
                    Text("やめる", color = Grimoire.InkFaded)
                }
            }
        )
    }
}

@Composable
private fun EnglishProfileSection(
    analyses: List<ConversationAnalysisRecord>,
    totalUserMessages: Int,
    loading: Boolean,
    message: String?,
    error: String?,
    onAnalyze: () -> Unit,
    onPractice: (String) -> Unit
) {
    var selectedAnalysis by remember { mutableStateOf<ConversationAnalysisRecord?>(null) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .grimoireBorder(10.dp),
        colors = CardDefaults.cardColors(containerColor = Grimoire.ParchmentDeep),
        shape = RoundedCornerShape(10.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "✦ Your English Profile",
                    style = MaterialTheme.typography.titleMedium,
                    color = Grimoire.BurgundyDeep,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = onAnalyze,
                    enabled = !loading,
                    colors = ButtonDefaults.buttonColors(containerColor = Grimoire.Burgundy)
                ) {
                    if (loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Grimoire.ParchmentLight,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    } else {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(if (loading) "分析中" else "会話を分析")
                }
            }
            val remaining = (20 - totalUserMessages).coerceAtLeast(0)
            if (remaining > 0 && analyses.isEmpty()) {
                Text(
                    text = "分析にはあと${remaining}件の会話ターンが必要です。",
                    style = MaterialTheme.typography.bodySmall,
                    color = Grimoire.InkFaded
                )
            }
            message?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = Grimoire.GoldDark)
            }
            error?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = Grimoire.Burgundy)
            }
            if (analyses.isEmpty()) {
                Text(
                    text = "会話がたまると、話し方の癖、よくある間違い、次に練習するとよい表現を分析できます。",
                    style = MaterialTheme.typography.bodySmall,
                    color = Grimoire.InkFaded
                )
                return@Column
            }
            Text(
                text = "分析履歴",
                style = MaterialTheme.typography.labelLarge,
                color = Grimoire.Burgundy
            )
            analyses.take(8).forEach { analysis ->
                AnalysisHistoryCard(
                    analysis = analysis,
                    onClick = { selectedAnalysis = analysis }
                )
            }
        }
    }

    selectedAnalysis?.let { analysis ->
        AnalysisDetailDialog(
            analysis = analysis,
            onPractice = onPractice,
            onDismiss = { selectedAnalysis = null }
        )
    }
}

@Composable
private fun AnalysisHistoryCard(
    analysis: ConversationAnalysisRecord,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .grimoireBorder(8.dp),
        colors = CardDefaults.cardColors(containerColor = Grimoire.ParchmentLight),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatDateTime(analysis.createdAt),
                    style = MaterialTheme.typography.labelLarge,
                    color = Grimoire.Ink,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "CEFR ${analysis.result.estimatedCefr.ifBlank { "-" }}",
                    style = MaterialTheme.typography.labelLarge,
                    color = Grimoire.Burgundy
                )
            }
            Text(
                text = "${analysis.userMessageCount}発話 / ${analysis.model}",
                style = MaterialTheme.typography.labelSmall,
                color = Grimoire.GoldDark
            )
            Text(
                text = analysis.result.summary.ifBlank { "タップして分析結果を確認できます。" },
                style = MaterialTheme.typography.bodySmall,
                color = Grimoire.InkFaded,
                maxLines = 2
            )
        }
    }
}

@Composable
private fun AnalysisDetailDialog(
    analysis: ConversationAnalysisRecord,
    onPractice: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Grimoire.ParchmentLight,
        title = {
            Text(
                text = "分析結果 ${formatDateTime(analysis.createdAt)}",
                color = Grimoire.BurgundyDeep
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = analysis.result.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Grimoire.Ink
                )
                if (analysis.result.estimatedCefr.isNotBlank()) {
                    Text(
                        text = "推定CEFR: ${analysis.result.estimatedCefr}",
                        style = MaterialTheme.typography.titleMedium,
                        color = Grimoire.Burgundy
                    )
                }
                if (analysis.result.cefrRationale.isNotBlank()) {
                    Text(
                        text = analysis.result.cefrRationale,
                        style = MaterialTheme.typography.bodySmall,
                        color = Grimoire.Ink
                    )
                }
                ProfileBlock("強み", analysis.result.strengths.map { point ->
                    buildString {
                        append(point.title)
                        if (point.comment.isNotBlank()) append(": ${point.comment}")
                    }
                })
                PatternBlock(analysis.result.recurringPatterns)
                ProfileBlock("改善ポイント", analysis.result.improvements.map { it.title.ifBlank { it.comment } })
                ProfileBlock("次の練習項目", analysis.result.nextFocus)
                ProfileBlock("レベルアッププラン", analysis.result.levelUpPlan)
                analysis.result.practicePrompts.take(3).forEach { prompt ->
                    Button(
                        onClick = {
                            onDismiss()
                            onPractice(prompt)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Grimoire.GoldDark)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(prompt, maxLines = 2)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("閉じる", color = Grimoire.InkFaded)
            }
        }
    )
}

@Composable
private fun ProfileBlock(title: String, lines: List<String>) {
    val visible = lines.map { it.trim() }.filter { it.isNotBlank() }
    if (visible.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = Grimoire.Burgundy)
        visible.take(4).forEach {
            Text("・$it", style = MaterialTheme.typography.bodySmall, color = Grimoire.Ink)
        }
    }
}

@Composable
private fun PatternBlock(patterns: List<RecurringPattern>) {
    if (patterns.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("よくある傾向", style = MaterialTheme.typography.labelLarge, color = Grimoire.Burgundy)
        patterns.take(3).forEach { pattern ->
            Text(
                text = "${pattern.title} (${pattern.occurrences}回): ${pattern.nextAction}",
                style = MaterialTheme.typography.bodySmall,
                color = Grimoire.Ink
            )
            pattern.examples.firstOrNull()?.let { example ->
                Text(
                    text = "${example.original} → ${example.suggestion}",
                    style = MaterialTheme.typography.labelSmall,
                    fontStyle = FontStyle.Italic,
                    color = Grimoire.InkFaded
                )
            }
        }
    }
}

private fun formatDateTime(millis: Long): String {
    return SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(Date(millis))
}

@Composable
private fun WeeklyActivityRow(studyDays: Set<Long>) {
    val today = LocalDate.now()
    val days = (6 downTo 0L).map { offset -> today.minusDays(offset) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .grimoireBorder(10.dp),
        colors = CardDefaults.cardColors(containerColor = Grimoire.ParchmentLight),
        shape = RoundedCornerShape(10.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "この7日間",
                style = MaterialTheme.typography.labelLarge,
                color = Grimoire.Ink
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                days.forEach { day ->
                    val studied = day.toEpochDay() in studyDays
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(
                                    color = if (studied) Grimoire.Gold else Grimoire.ParchmentDeep,
                                    shape = RoundedCornerShape(6.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (studied) {
                                Text("✦", color = Grimoire.BurgundyDeep)
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = day.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                            style = MaterialTheme.typography.labelSmall,
                            color = Grimoire.InkFaded
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    caption: String? = null
) {
    Card(
        modifier = modifier.grimoireBorder(10.dp),
        colors = CardDefaults.cardColors(containerColor = Grimoire.ParchmentLight),
        shape = RoundedCornerShape(10.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                color = Grimoire.Burgundy
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = Grimoire.InkFaded,
                textAlign = TextAlign.Center
            )
            caption?.let {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic,
                    color = Grimoire.InkFaded,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
