package com.byokeylab.speak.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.byokeylab.speak.AppViewModel
import com.byokeylab.speak.AppSound
import com.byokeylab.speak.Grimoire
import com.byokeylab.speak.LocalAppSoundEffects
import com.byokeylab.speak.VocabCard
import com.byokeylab.speak.VocabSource
import com.byokeylab.speak.grimoireBorder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

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
    val soundEffects = LocalAppSoundEffects.current
    val cards by viewModel.vocabCards.collectAsState()
    val userMessageTexts by viewModel.userMessageTexts.collectAsState()
    var sort by rememberSaveable { mutableStateOf(VocabSort.Date) }
    var collection by rememberSaveable { mutableStateOf(ReviewCollection.Vocabulary) }
    var requestedLetter by remember { mutableStateOf<Char?>(null) }
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val collectionCards = cards.filter { card ->
        when (collection) {
            ReviewCollection.Vocabulary -> card.source != VocabSource.QuickAssist
            ReviewCollection.QuickAssist -> card.source == VocabSource.QuickAssist
        }
    }
    val sorted = when (sort) {
        VocabSort.Date -> collectionCards.sortedByDescending { it.timestampMillis }
        VocabSort.Alphabet -> collectionCards.sortedBy { it.expression.lowercase() }
        VocabSort.Favorite -> collectionCards.filter { it.favorite }.sortedBy { it.expression.lowercase() }
    }
    val availableLetters = sorted.mapNotNull { alphabetKeyOf(it.expression) }.toSet()
    val usageCounts = remember(collectionCards, userMessageTexts) {
        collectionCards.associate { card ->
            card.id to countExpressionOccurrences(card.expression, userMessageTexts)
        }
    }

    LaunchedEffect(collection) {
        requestedLetter = null
        if (sorted.isNotEmpty()) listState.scrollToItem(0)
    }
    LaunchedEffect(requestedLetter, sorted, sort) {
        val letter = requestedLetter ?: return@LaunchedEffect
        val exact = sorted.indexOfFirst { alphabetKeyOf(it.expression) == letter }
        val target = if (exact >= 0) {
            exact
        } else {
            sorted.indexOfFirst { (alphabetKeyOf(it.expression) ?: 'Z') >= letter }
                .takeIf { it >= 0 } ?: sorted.lastIndex
        }
        if (target >= 0) listState.scrollToItem(target)
    }

    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = if (collection == ReviewCollection.Vocabulary) {
                    "✦ Vocabulary List"
                } else {
                    "✦ Quick Assist"
                },
                style = MaterialTheme.typography.titleMedium,
                color = Grimoire.BurgundyDeep
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ReviewCollection.entries.forEach { candidate ->
                    FilterChip(
                        selected = collection == candidate,
                        onClick = {
                            if (collection != candidate) {
                                soundEffects?.play(AppSound.Select)
                                collection = candidate
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "並び順",
                    style = MaterialTheme.typography.labelSmall,
                    color = Grimoire.InkFaded
                )
                Spacer(modifier = Modifier.weight(1f))
                VocabSort.entries.forEach { candidate ->
                    FilterChip(
                        selected = sort == candidate,
                        onClick = {
                            if (sort != candidate) {
                                soundEffects?.play(AppSound.Select)
                                sort = candidate
                            }
                        },
                        label = { Text(candidate.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Grimoire.Burgundy,
                            selectedLabelColor = Grimoire.ParchmentLight
                        )
                    )
                }
                if (collection == ReviewCollection.Vocabulary) {
                    IconButton(
                        onClick = {
                            soundEffects?.play(AppSound.Select)
                            showAddDialog = true
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "カードを追加",
                            tint = Grimoire.Burgundy
                        )
                    }
                }
            }
        }

        if (showAddDialog) {
            ManualVocabDialog(
                onDismiss = { showAddDialog = false },
                onSave = { expression, meaning ->
                    viewModel.saveManualVocabCard(expression, meaning)
                    showAddDialog = false
                }
            )
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
                    text = if (sort == VocabSort.Favorite) {
                        "お気に入りはまだありません。"
                    } else if (collection == ReviewCollection.QuickAssist) {
                        "Quick Assistで選んだ表現はまだありません。"
                    } else {
                        "まだカードがありません。\n会話すると、覚えたい表現がここに貯まっていきます。"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontStyle = FontStyle.Italic,
                    textAlign = TextAlign.Center,
                    color = Grimoire.InkFaded
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 6.dp)
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(sorted, key = { it.id }) { card ->
                        VocabCardItem(
                            card = card,
                            quickAssist = collection == ReviewCollection.QuickAssist,
                            usageCount = if (collection == ReviewCollection.Vocabulary) {
                                usageCounts[card.id] ?: 0
                            } else {
                                0
                            },
                            onToggleFavorite = {
                                soundEffects?.play(AppSound.Select)
                                viewModel.setVocabFavorite(card.id, !card.favorite)
                            },
                            onDelete = { viewModel.deleteVocabCard(card.id) }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(12.dp)) }
                }
                if (collection == ReviewCollection.Vocabulary) {
                    AlphabetRail(
                        availableLetters = availableLetters,
                        onLetterSelected = { letter ->
                            sort = VocabSort.Alphabet
                            requestedLetter = letter
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun AlphabetRail(
    availableLetters: Set<Char>,
    onLetterSelected: (Char) -> Unit
) {
    var railHeightPx by remember { mutableFloatStateOf(1f) }
    var activeIndex by remember { mutableIntStateOf(-1) }
    var activeY by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val bubbleSize = 32.dp
    val bubbleSizePx = with(density) { bubbleSize.toPx() }

    fun selectAt(position: Offset) {
        val y = position.y.coerceIn(0f, railHeightPx)
        val index = ((y / railHeightPx) * 26f).toInt().coerceIn(0, 25)
        activeY = y
        if (activeIndex != index) {
            activeIndex = index
            onLetterSelected(('A'.code + index).toChar())
        }
    }

    Box(
        modifier = Modifier
            .width(58.dp)
            .fillMaxHeight()
            .onSizeChanged { railHeightPx = it.height.toFloat().coerceAtLeast(1f) }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { selectAt(it) },
                    onVerticalDrag = { change, _ ->
                        change.consume()
                        selectAt(change.position)
                    },
                    onDragEnd = { activeIndex = -1 },
                    onDragCancel = { activeIndex = -1 }
                )
            }
    ) {
        if (activeIndex >= 0) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset {
                        IntOffset(
                            x = 0,
                            y = (activeY - bubbleSizePx / 2f)
                                .coerceIn(0f, (railHeightPx - bubbleSizePx).coerceAtLeast(0f))
                                .roundToInt()
                        )
                    }
                    .size(bubbleSize),
                color = Grimoire.Burgundy,
                shape = CircleShape
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = ('A'.code + activeIndex).toChar().toString(),
                        style = MaterialTheme.typography.labelLarge,
                        color = Grimoire.ParchmentLight
                    )
                }
            }
        }
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(24.dp)
                .padding(bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            ('A'..'Z').forEach { letter ->
                val enabled = letter in availableLetters
                Text(
                    text = letter.toString(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            activeIndex = -1
                            onLetterSelected(letter)
                        },
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (enabled) {
                        Grimoire.Burgundy
                    } else {
                        Grimoire.InkFaded.copy(alpha = 0.35f)
                    }
                )
            }
        }
    }
}

private fun alphabetKeyOf(text: String): Char? {
    val letter = text.firstOrNull { it.isLetter() }?.uppercaseChar() ?: return null
    return letter.takeIf { it in 'A'..'Z' }
}

private enum class VocabSort(val label: String) {
    Date("日付"),
    Alphabet("ABC"),
    Favorite("★")
}

private enum class ReviewCollection(val label: String) {
    Vocabulary("Vocabulary"),
    QuickAssist("Quick Assist")
}

@Composable
private fun ManualVocabDialog(
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var expression by rememberSaveable { mutableStateOf("") }
    var meaning by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Grimoire.ParchmentLight,
        title = { Text("Vocabularyを追加", color = Grimoire.BurgundyDeep) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = expression,
                    onValueChange = { expression = it },
                    label = { Text("Expression") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = meaning,
                    onValueChange = { meaning = it },
                    label = { Text("Meaning / note") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(expression, meaning) },
                enabled = expression.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Grimoire.Burgundy)
            ) {
                Text("追加")
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
private fun VocabCardItem(
    card: VocabCard,
    quickAssist: Boolean,
    usageCount: Int,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit
) {
    val cardColor = vocabUsageCardColor(usageCount)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .grimoireBorder(8.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                SelectionContainer {
                    Text(
                        text = card.expression,
                        style = MaterialTheme.typography.titleSmall,
                        color = Grimoire.Ink,
                        maxLines = 1,
                        softWrap = false
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (card.meaning.isNotBlank()) {
                        SelectionContainer {
                            Text(
                                text = card.meaning,
                                style = MaterialTheme.typography.bodySmall,
                                color = Grimoire.InkFaded,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = formatCardDate(card.timestampMillis),
                        style = MaterialTheme.typography.labelSmall,
                        color = Grimoire.Gold,
                        maxLines = 1
                    )
                }
            }
            Row {
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = if (card.favorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        contentDescription = "お気に入り",
                        tint = if (card.favorite) Grimoire.Gold else Grimoire.InkFaded,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(34.dp)
                )
                {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "カードを削除",
                        tint = Grimoire.GoldDark,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun vocabUsageCardColor(usageCount: Int): Color {
    if (!Grimoire.isDark) {
        return when {
            usageCount >= 15 -> Color(0xFFA65E58)
            usageCount >= 9 -> Color(0xFFC98E73)
            usageCount >= 5 -> Color(0xFFD9BA88)
            usageCount >= 2 -> Color(0xFFEBDCB6)
            else -> Color(0xFFF8EFDA)
        }
    }
    return when {
        usageCount >= 15 -> Color(0xFFD08A76)
        usageCount >= 9 -> Color(0xFF8D5E58)
        usageCount >= 5 -> Color(0xFF4E4551)
        usageCount >= 2 -> Color(0xFF263A52)
        else -> Color(0xFF1A2840)
    }
}

private fun formatCardDate(timestampMillis: Long): String {
    return SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(Date(timestampMillis))
}

internal fun countExpressionOccurrences(expression: String, messages: List<String>): Int {
    val target = expression.trim()
    if (target.isEmpty()) return 0
    val wordCharacter = "[\\p{L}\\p{N}']"
    val pattern = Regex(
        pattern = "(?<!$wordCharacter)${Regex.escape(target)}(?!$wordCharacter)",
        option = RegexOption.IGNORE_CASE
    )
    return messages.sumOf { message -> pattern.findAll(message).count() }
}
