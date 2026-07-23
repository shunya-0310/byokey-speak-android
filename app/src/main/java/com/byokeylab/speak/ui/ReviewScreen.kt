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
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.byokeylab.speak.VocabCard
import com.byokeylab.speak.grimoireBorder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Review (FR-007): 単語帳。
 * 会話から抽出した表現・イディオムをカードでめくる。
 * お気に入り(★)、出所(Coach/Chat/Assist)表示、ソート(日付/ABC/お気に入り)。
 */
@Composable
fun ReviewScreen(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val cards by viewModel.vocabCards.collectAsState()
    var sort by rememberSaveable { mutableStateOf(VocabSort.Date) }

    val sorted = when (sort) {
        VocabSort.Date -> cards.sortedByDescending { it.timestampMillis }
        VocabSort.Alphabet -> cards.sortedBy { it.expression.lowercase() }
        VocabSort.Favorite -> cards.sortedWith(
            compareByDescending<VocabCard> { it.favorite }.thenByDescending { it.timestampMillis }
        )
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
                text = "✦ 単語帳",
                style = MaterialTheme.typography.titleMedium,
                color = Grimoire.BurgundyDeep,
                modifier = Modifier.weight(1f)
            )
            VocabSort.entries.forEach { candidate ->
                FilterChip(
                    selected = sort == candidate,
                    onClick = { sort = candidate },
                    label = { Text(candidate.label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Grimoire.Burgundy,
                        selectedLabelColor = Grimoire.ParchmentLight
                    )
                )
            }
        }

        if (sorted.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "まだカードがありません。\n会話すると、覚えたい表現がここに貯まっていきます。",
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
                items(sorted, key = { it.id }) { card ->
                    VocabCardItem(
                        card = card,
                        onToggleFavorite = { viewModel.setVocabFavorite(card.id, !card.favorite) },
                        onDelete = { viewModel.deleteVocabCard(card.id) }
                    )
                }
                item { Spacer(modifier = Modifier.height(12.dp)) }
            }
        }
    }
}

private enum class VocabSort(val label: String) {
    Date("日付"),
    Alphabet("ABC"),
    Favorite("★")
}

@Composable
private fun VocabCardItem(
    card: VocabCard,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .grimoireBorder(12.dp),
        colors = CardDefaults.cardColors(containerColor = Grimoire.ParchmentLight),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.Top) {
                SelectionContainer(modifier = Modifier.weight(1f)) {
                    Text(
                        text = card.expression,
                        style = MaterialTheme.typography.titleMedium,
                        color = Grimoire.Ink
                    )
                }
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        imageVector = if (card.favorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        contentDescription = "お気に入り",
                        tint = if (card.favorite) Grimoire.Gold else Grimoire.InkFaded
                    )
                }
            }
            if (card.meaning.isNotBlank()) {
                SelectionContainer {
                    Text(
                        text = card.meaning,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Grimoire.InkFaded
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = Grimoire.ParchmentDeep,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = card.source.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = Grimoire.Burgundy,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.padding(4.dp))
                Text(
                    text = formatCardDate(card.timestampMillis),
                    style = MaterialTheme.typography.labelSmall,
                    color = Grimoire.Gold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "カードを削除",
                        tint = Grimoire.GoldDark
                    )
                }
            }
        }
    }
}

private fun formatCardDate(timestampMillis: Long): String {
    return SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(Date(timestampMillis))
}
