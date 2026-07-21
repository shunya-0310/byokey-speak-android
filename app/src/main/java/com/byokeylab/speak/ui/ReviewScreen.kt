package com.byokeylab.speak.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.byokeylab.speak.AppViewModel
import com.byokeylab.speak.Grimoire
import com.byokeylab.speak.LearningNote
import com.byokeylab.speak.grimoireBorder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Review (FR-007): 学習メモの復習。
 * 未復習/復習済みの切り替え、削除。
 */
@Composable
fun ReviewScreen(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val notes by viewModel.notes.collectAsState()
    var filter by rememberSaveable { mutableStateOf(ReviewFilter.All) }

    val filtered = when (filter) {
        ReviewFilter.All -> notes
        ReviewFilter.Unreviewed -> notes.filter { !it.reviewed }
        ReviewFilter.Reviewed -> notes.filter { it.reviewed }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "✦ Review",
                style = MaterialTheme.typography.titleMedium,
                color = Grimoire.BurgundyDeep,
                modifier = Modifier.weight(1f)
            )
            ReviewFilter.entries.forEach { candidate ->
                FilterChip(
                    selected = filter == candidate,
                    onClick = { filter = candidate },
                    label = { Text(candidate.label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Grimoire.Burgundy,
                        selectedLabelColor = Grimoire.ParchmentLight
                    )
                )
            }
        }

        if (filtered.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "このページはまだ空白です。\n会話するとコーチの指摘がここに保存されます。",
                    style = MaterialTheme.typography.bodyMedium,
                    fontStyle = FontStyle.Italic,
                    textAlign = TextAlign.Center,
                    color = Grimoire.InkFaded
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filtered, key = { it.id }) { note ->
                    ReviewNoteCard(
                        note = note,
                        onToggleReviewed = { viewModel.setNoteReviewed(note.id, !note.reviewed) },
                        onDelete = { viewModel.deleteNote(note.id) }
                    )
                }
                item { Spacer(modifier = Modifier.height(12.dp)) }
            }
        }
    }
}

private enum class ReviewFilter(val label: String) {
    All("All"),
    Unreviewed("未復習"),
    Reviewed("復習済み")
}

@Composable
private fun ReviewNoteCard(
    note: LearningNote,
    onToggleReviewed: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.grimoireBorder(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (note.reviewed) Grimoire.ParchmentDeep else Grimoire.ParchmentLight
        ),
        shape = RoundedCornerShape(10.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "✦ ${formatNoteDate(note.timestampMillis)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Grimoire.Gold,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onToggleReviewed) {
                    Text(
                        text = if (note.reviewed) "復習済み ✓" else "復習した",
                        color = if (note.reviewed) Grimoire.Moss else Grimoire.Burgundy
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete note", tint = Grimoire.GoldDark)
                }
            }
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
                    if (note.japaneseNote.isNotBlank()) {
                        Text(
                            text = "日本語メモ:\n${note.japaneseNote}",
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
