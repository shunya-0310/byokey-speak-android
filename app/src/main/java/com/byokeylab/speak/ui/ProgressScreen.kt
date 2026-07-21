package com.byokeylab.speak.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.byokeylab.speak.AppViewModel
import com.byokeylab.speak.Grimoire
import com.byokeylab.speak.grimoireBorder
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * Progress (FR-008): 継続日数、累計、Quick Assist利用などの可視化。
 * Quick Assist利用数は「助けを使えた成果」としてポジティブに表示する。
 */
@Composable
fun ProgressScreen(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val stats by viewModel.dailyStats.collectAsState()
    val notes by viewModel.notes.collectAsState()

    val streak = viewModel.streakOf(stats)
    val totalStudyDays = stats.count { it.turns > 0 }
    val totalTurns = stats.sumOf { it.turns }
    val totalAssist = stats.sumOf { it.assistUses }
    val totalNotes = notes.size
    val reviewedNotes = notes.count { it.reviewed }

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
            StatCard(label = "保存した表現", value = "$totalNotes", modifier = Modifier.weight(1f))
            StatCard(label = "復習完了", value = "$reviewedNotes", modifier = Modifier.weight(1f))
        }
        StatCard(
            label = "Quick Assistで乗り切った回数",
            value = "$totalAssist",
            caption = "詰まっても会話を止めなかった回数。立派な成果です。",
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
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
