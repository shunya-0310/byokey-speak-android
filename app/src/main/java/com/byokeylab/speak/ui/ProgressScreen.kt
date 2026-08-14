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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import java.time.YearMonth
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import com.byokeylab.speak.VocabCard
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import androidx.compose.material.icons.filled.OpenInFull

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
    val studyDays = stats
        .filter { it.turns > 0 }
        .map { it.epochDay }
        .toSet()

    val totalStudyDays = studyDays.size
    val totalTurns = stats.sumOf { it.turns }
    val totalVocabulary = cards.size
    val favoriteCards = cards.count { it.favorite }

    var showStudyCalendar by remember { mutableStateOf(false) }
    var showVocabularyGraph by remember { mutableStateOf(false) }

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

        WeeklyActivityRow(studyDays = studyDays)

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(
                label = "累計学習日",
                value = "$totalStudyDays 日",
                modifier = Modifier.weight(1f),
                onClick = { showStudyCalendar = true }
            )
            StatCard(
                label = "累計ターン",
                value = "$totalTurns",
                modifier = Modifier.weight(1f)
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(
            label = "ボキャブラリー",
            value = "$totalVocabulary",
            modifier = Modifier.weight(1f),
            onClick = {
                showVocabularyGraph = true
            }
        )
            StatCard(
                label = "お気に入り ★",
                value = "$favoriteCards",
                modifier = Modifier.weight(1f)
            )
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

    if (showStudyCalendar) {
    StudyCalendarBottomSheet(
        studyDays = studyDays,
        onDismiss = { showStudyCalendar = false }
        )
    }

    if (showVocabularyGraph) {
        VocabularyHistoryBottomSheet(
            cards = cards,
            onDismiss = {
                showVocabularyGraph = false
            }
        )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StudyCalendarBottomSheet(
    studyDays: Set<Long>,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )

    val currentMonth = YearMonth.now()

    var displayedMonth by remember {
        mutableStateOf(currentMonth)
    }

    val firstDay = displayedMonth.atDay(1)
    val leadingBlankCount = firstDay.dayOfWeek.value % 7

    val leadingBlanks = List<LocalDate?>(leadingBlankCount) {
        null
    }

    val monthDays = (1..displayedMonth.lengthOfMonth()).map { day ->
        displayedMonth.atDay(day)
    }

    val calendarDays: List<LocalDate?> =
        leadingBlanks + monthDays

    val trailingBlankCount =
        (7 - calendarDays.size % 7) % 7

    val paddedDays =
        calendarDays +
            List<LocalDate?>(trailingBlankCount) { null }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Grimoire.ParchmentLight
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "✦ 学習カレンダー",
                style = MaterialTheme.typography.titleMedium,
                color = Grimoire.BurgundyDeep
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = {
                        displayedMonth =
                            displayedMonth.minusMonths(1)
                    }
                ) {
                    Text("‹")
                }

                Text(
                    text = "${displayedMonth.year}年${displayedMonth.monthValue}月",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    color = Grimoire.Burgundy,
                    textAlign = TextAlign.Center
                )

                TextButton(
                    onClick = {
                        displayedMonth =
                            displayedMonth.plusMonths(1)
                    },
                    enabled = displayedMonth < currentMonth
                ) {
                    Text("›")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth()
            ) {
                listOf(
                    "日", "月", "火", "水", "木", "金", "土"
                ).forEach { weekday ->
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = weekday,
                            style = MaterialTheme.typography.labelMedium,
                            color = Grimoire.InkFaded
                        )
                    }
                }
            }

            paddedDays.chunked(7).forEach { week ->
                Row(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    week.forEach { day ->
                        val studied =
                            day != null &&
                                day.toEpochDay() in studyDays

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            if (day != null) {
                                Column(
                                    horizontalAlignment =
                                        Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = day.dayOfMonth.toString(),
                                        style =
                                            MaterialTheme.typography.bodyMedium,
                                        color =
                                            if (day == LocalDate.now()) {
                                                Grimoire.Burgundy
                                            } else {
                                                Grimoire.Ink
                                            }
                                    )

                                    if (studied) {
                                        Text(
                                            text = "✦",
                                            style =
                                                MaterialTheme.typography.labelLarge,
                                            color = Grimoire.GoldDark
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Text(
                text = "✦ 学習した日",
                style = MaterialTheme.typography.labelSmall,
                color = Grimoire.GoldDark,
                modifier = Modifier.align(Alignment.End)
            )
        }
    }
}

@Composable
private fun StatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    caption: String? = null,
    onClick: (() -> Unit)? = null
) {
    val cardModifier = if (onClick != null) {
        modifier.clickable(onClick = onClick)
    } else {
        modifier
    }

    Card(
        modifier = cardModifier.grimoireBorder(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = Grimoire.ParchmentLight
        ),
        shape = RoundedCornerShape(10.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth()
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

            if (onClick != null) {
                Icon(
                    imageVector = Icons.Default.OpenInFull,
                    contentDescription = "$label の詳細を開く",
                    tint = Grimoire.GoldDark,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(15.dp)
                )
            }
        }
    }
}

private data class VocabHistoryPoint(
    val date: LocalDate,
    val count: Int
)

private fun buildVocabularyHistory(
    cards: List<VocabCard>
): List<VocabHistoryPoint> {
    if (cards.isEmpty()) return emptyList()

    val zoneId = ZoneId.systemDefault()

    val additionsByDate = cards
        .groupingBy { card ->
            Instant
                .ofEpochMilli(card.timestampMillis)
                .atZone(zoneId)
                .toLocalDate()
        }
        .eachCount()

    val firstDate =
        additionsByDate.keys.minOrNull()
            ?: return emptyList()

    val today = LocalDate.now()

    var cumulative = 0
    val result = mutableListOf<VocabHistoryPoint>()

    var date = firstDate

    while (!date.isAfter(today)) {
        cumulative += additionsByDate[date] ?: 0

        result += VocabHistoryPoint(
            date = date,
            count = cumulative
        )

        date = date.plusDays(1)
    }

    return result
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VocabularyHistoryBottomSheet(
    cards: List<VocabCard>,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )

    val history = remember(cards) {
        buildVocabularyHistory(cards)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Grimoire.ParchmentLight
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "✦ ボキャブラリーの推移",
                style = MaterialTheme.typography.titleMedium,
                color = Grimoire.BurgundyDeep
            )

            Text(
                text = "現在 ${cards.size} 語",
                style = MaterialTheme.typography.bodyMedium,
                color = Grimoire.InkFaded
            )

            if (history.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "ボキャブラリーを追加すると\nここに推移が表示されます",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Grimoire.InkFaded,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Grimoire.ParchmentDeep
                    ),
                    shape = RoundedCornerShape(10.dp),
                    elevation = CardDefaults.cardElevation(
                        defaultElevation = 0.dp
                    )
                ) {
                    VocabularyLineChart(
                        points = history,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun VocabularyLineChart(
    points: List<VocabHistoryPoint>,
    modifier: Modifier = Modifier
) {
    if (points.isEmpty()) return

    // Grimoire は @Composable なので、
    // Canvas の外側で色を取得しておく
    val palette = Grimoire
    val gridColor = palette.InkFaded.copy(alpha = 0.18f)
    val lineColor = palette.Burgundy
    val pointColor = palette.GoldDark

    val maxCount = points.maxOf { it.count }
    val yMax = niceVocabularyAxisMax(maxCount)

    val yLabels = listOf(
        yMax,
        yMax * 3 / 4,
        yMax / 2,
        yMax / 4,
        0
    )

    val lastIndex = points.lastIndex

    val xIndexes =
        if (points.size <= 5) {
            points.indices.toList()
        } else {
            listOf(
                0,
                (lastIndex * 0.25f).toInt(),
                (lastIndex * 0.50f).toInt(),
                (lastIndex * 0.75f).toInt(),
                lastIndex
            ).distinct()
        }

    // LocalDate.until(...).days ではなく、
    // 実際の総日数を Long で取得
    val rangeDays = ChronoUnit.DAYS.between(
        points.first().date,
        points.last().date
    )

    val crossesYear =
        points.first().date.year != points.last().date.year

    Row(
        modifier = modifier
    ) {
        // Y軸
        Column(
            modifier = Modifier
                .width(36.dp)
                .height(200.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.End
        ) {
            yLabels.forEach { value ->
                Text(
                    text = value.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.InkFaded
                )
            }
        }

        Spacer(
            modifier = Modifier.width(8.dp)
        )

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            ) {
                val leftPadding = 4.dp.toPx()
                val rightPadding = 4.dp.toPx()
                val topPadding = 6.dp.toPx()
                val bottomPadding = 6.dp.toPx()

                val graphWidth =
                    size.width -
                        leftPadding -
                        rightPadding

                val graphHeight =
                    size.height -
                        topPadding -
                        bottomPadding

                // 横グリッド
                repeat(5) { index ->
                    val y =
                        topPadding +
                            graphHeight *
                            index / 4f

                    drawLine(
                        color = gridColor,
                        start = Offset(
                            leftPadding,
                            y
                        ),
                        end = Offset(
                            size.width - rightPadding,
                            y
                        ),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                val path = Path()

                points.forEachIndexed { index, point ->
                    val x =
                        if (points.size == 1) {
                            leftPadding +
                                graphWidth / 2f
                        } else {
                            leftPadding +
                                graphWidth *
                                index /
                                points.lastIndex
                        }

                    val ratio =
                        point.count.toFloat() /
                            yMax.toFloat()

                    val y =
                        topPadding +
                            graphHeight *
                            (1f - ratio)

                    if (index == 0) {
                        path.moveTo(x, y)
                    } else {
                        path.lineTo(x, y)
                    }
                }

                drawPath(
                    path = path,
                    color = lineColor,
                    style = Stroke(
                        width = 2.5.dp.toPx()
                    )
                )

                // 最新値にだけ丸を表示
                val latest = points.last()

                val latestX =
                    if (points.size == 1) {
                        leftPadding +
                            graphWidth / 2f
                    } else {
                        size.width -
                            rightPadding
                    }

                val latestRatio =
                    latest.count.toFloat() /
                        yMax.toFloat()

                val latestY =
                    topPadding +
                        graphHeight *
                        (1f - latestRatio)

                drawCircle(
                    color = pointColor,
                    radius = 4.dp.toPx(),
                    center = Offset(
                        latestX,
                        latestY
                    )
                )
            }

            Spacer(
                modifier = Modifier.height(6.dp)
            )

            if (xIndexes.size == 1) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = formatGraphDate(
                            points[xIndexes.first()].date,
                            rangeDays,
                            crossesYear
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.InkFaded
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    xIndexes.forEach { index ->
                        Text(
                            text = formatGraphDate(
                                points[index].date,
                                rangeDays,
                                crossesYear
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.InkFaded
                        )
                    }
                }
            }
        }
    }
}

private fun niceVocabularyAxisMax(
    maxCount: Int
): Int {
    if (maxCount <= 0) return 4

    val unit = when {
        maxCount <= 20 -> 4
        maxCount <= 100 -> 20
        maxCount <= 250 -> 40
        maxCount <= 500 -> 100
        else -> 200
    }

    return maxOf(
        unit,
        ((maxCount + unit - 1) / unit) * unit
    )
}

private fun formatGraphDate(
    date: LocalDate,
    rangeDays: Long,
    crossesYear: Boolean
): String {
    return when {
        crossesYear -> {
            "%02d/%02d".format(
                date.year % 100,
                date.monthValue
            )
        }

        rangeDays > 120 -> {
            "${date.monthValue}月"
        }

        else -> {
            "${date.monthValue}/${date.dayOfMonth}"
        }
    }
}
