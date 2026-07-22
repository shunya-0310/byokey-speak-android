package com.byokeylab.speak.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChip
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.byokeylab.speak.AppViewModel
import com.byokeylab.speak.Grimoire
import com.byokeylab.speak.TopicCategory
import com.byokeylab.speak.TopicRepository
import com.byokeylab.speak.data.ChatMode
import com.byokeylab.speak.grimoireBorder

/**
 * Main Coach (FR-003)。
 * 話題なしでもすぐ話せる。必要ならトピック候補から開始できる。
 */
@Composable
fun CoachScreen(
    viewModel: AppViewModel,
    onMic: (Long) -> Unit,
    onAssistMic: () -> Unit,
    onSpeak: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val chatId = viewModel.coachChatId ?: return
    var showHistory by rememberSaveable { mutableStateOf(false) }
    var topicPanelVisible by rememberSaveable { mutableStateOf(false) }
    var selectedCategory by rememberSaveable { mutableStateOf(TopicCategory.DailyLife) }
    var suggestedTopic by rememberSaveable { mutableStateOf(TopicRepository.topicForToday()) }

    if (showHistory) {
        CoachHistoryList(
            viewModel = viewModel,
            onOpen = { id ->
                viewModel.openCoachChat(id)
                showHistory = false
            },
            onBack = { showHistory = false },
            modifier = modifier
        )
        return
    }

    ConversationPane(
        chatId = chatId,
        mode = ChatMode.Coach,
        viewModel = viewModel,
        onMic = onMic,
        onAssistMic = onAssistMic,
        onSpeak = onSpeak,
        modifier = modifier,
        emptyHint = "そのまま話しかけてOK。英語でも日本語でも、コーチが会話を続けます。",
        header = {
            if (topicPanelVisible) {
                TopicPanel(
                    topic = suggestedTopic,
                    onSpeakTopic = { onSpeak(suggestedTopic) },
                    selectedCategory = selectedCategory,
                    onCategoryChange = { category ->
                        selectedCategory = category
                        suggestedTopic = TopicRepository.randomTopic(category)
                    },
                    onRefreshTopic = {
                        suggestedTopic = TopicRepository.randomTopic(selectedCategory)
                    },
                    onStartTopic = {
                        viewModel.startTopic(suggestedTopic)
                        topicPanelVisible = false
                    },
                    onCloseTopic = { topicPanelVisible = false }
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "✦ Main Coach",
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = FontStyle.Italic,
                        color = Grimoire.Burgundy
                    )
                    TextButton(onClick = { topicPanelVisible = true }) {
                        Text("Topics", color = Grimoire.GoldDark)
                    }
                    TextButton(onClick = { showHistory = true }) {
                        Text("履歴", color = Grimoire.GoldDark)
                    }
                    IconButton(onClick = { viewModel.newCoachConversation() }) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "New coach conversation",
                            tint = Grimoire.GoldDark
                        )
                    }
                }
            }
        }
    )
}

/** Coach会話の履歴一覧。Chatsと同じカードで開く/ピン留め/名前変更/削除ができる。 */
@Composable
private fun CoachHistoryList(
    viewModel: AppViewModel,
    onOpen: (Long) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val chats by viewModel.coachChats.collectAsState()
    var renameTarget by rememberSaveable { mutableStateOf<Long?>(null) }
    var deleteTarget by rememberSaveable { mutableStateOf<Long?>(null) }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back to coach",
                    tint = Grimoire.GoldDark
                )
            }
            Text(
                text = "✦ Coach履歴",
                style = MaterialTheme.typography.titleSmall,
                color = Grimoire.Burgundy,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = {
                viewModel.newCoachConversation()
                onBack()
            }) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "New coach conversation",
                    tint = Grimoire.GoldDark
                )
            }
        }

        if (chats.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "まだ会話がありません。",
                    style = MaterialTheme.typography.bodyMedium,
                    fontStyle = FontStyle.Italic,
                    color = Grimoire.InkFaded
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(chats, key = { it.id }) { chat ->
                    ChatListItem(
                        chat = chat,
                        onOpen = { onOpen(chat.id) },
                        onPinToggle = { viewModel.setPinned(chat.id, !chat.pinned) },
                        onRename = { renameTarget = chat.id },
                        onDelete = { deleteTarget = chat.id }
                    )
                }
                item { Spacer(modifier = Modifier.height(12.dp)) }
            }
        }
    }

    renameTarget?.let { id ->
        RenameDialog(
            currentTitle = chats.firstOrNull { it.id == id }?.title.orEmpty(),
            onConfirm = { newTitle ->
                viewModel.renameChat(id, newTitle)
                renameTarget = null
            },
            onDismiss = { renameTarget = null }
        )
    }
    deleteTarget?.let { id ->
        DeleteChatDialog(
            onConfirm = {
                viewModel.deleteChat(id)
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TopicPanel(
    topic: String,
    onSpeakTopic: () -> Unit,
    selectedCategory: TopicCategory,
    onCategoryChange: (TopicCategory) -> Unit,
    onRefreshTopic: () -> Unit,
    onStartTopic: () -> Unit,
    onCloseTopic: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "✦ Topic ideas",
                style = MaterialTheme.typography.labelLarge,
                color = Grimoire.Burgundy,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onRefreshTopic) {
                Icon(Icons.Default.Refresh, contentDescription = "New topic", tint = Grimoire.GoldDark)
            }
            IconButton(onClick = onCloseTopic) {
                Icon(Icons.Default.Close, contentDescription = "Hide topics", tint = Grimoire.GoldDark)
            }
        }
        Card(
            modifier = Modifier.grimoireBorder(10.dp),
            colors = CardDefaults.cardColors(containerColor = Grimoire.ParchmentDeep),
            shape = RoundedCornerShape(10.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = topic,
                        style = MaterialTheme.typography.titleMedium,
                        color = Grimoire.Ink,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onSpeakTopic) {
                        Icon(
                            Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = "Listen to topic",
                            tint = Grimoire.GoldDark
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(
                        onClick = onStartTopic,
                        label = { Text("この話題で始める", color = Grimoire.Burgundy) }
                    )
                    Text(
                        text = "話題は選ばなくてもOK。そのまま入力しても会話できます。",
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = FontStyle.Italic,
                        color = Grimoire.InkFaded
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        TopicCategory.entries.forEach { category ->
                            FilterChip(
                                selected = selectedCategory == category,
                                onClick = { onCategoryChange(category) },
                                label = { Text(category.label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Grimoire.Burgundy,
                                    selectedLabelColor = Grimoire.ParchmentLight
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}
