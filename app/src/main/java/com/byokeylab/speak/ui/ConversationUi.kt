package com.byokeylab.speak.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.byokeylab.speak.AppViewModel
import com.byokeylab.speak.ChatMessage
import com.byokeylab.speak.Grimoire
import com.byokeylab.speak.MessageRole
import com.byokeylab.speak.OrnamentDivider
import com.byokeylab.speak.StudyStats
import com.byokeylab.speak.data.ChatMode
import com.byokeylab.speak.grimoireBorder

/**
 * CoachとChatsで共有する会話ペイン。
 * Quick Assist・音声入力・送信・読み上げを常備する (FR-003/FR-004/FR-005)。
 */
@Composable
fun ConversationPane(
    chatId: Long,
    mode: ChatMode,
    viewModel: AppViewModel,
    onMic: (Long) -> Unit,
    onAssistMic: () -> Unit,
    onSpeak: (String) -> Unit,
    modifier: Modifier = Modifier,
    emptyHint: String = "Say hello to start the conversation.",
    header: (@Composable () -> Unit)? = null
) {
    val messages by remember(chatId) { viewModel.messagesFlow(chatId) }
        .collectAsState(initial = emptyList())
    val isSending = viewModel.sendingChatId == chatId
    val input = viewModel.draftOf(chatId)
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size, isSending) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Column(modifier = modifier) {
        header?.invoke()
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (messages.isEmpty() && !isSending) {
                item {
                    Text(
                        text = emptyHint,
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = FontStyle.Italic,
                        color = Grimoire.InkFaded,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp)
                    )
                }
            }
            items(messages, key = { it.id }) { message ->
                MessageBubble(
                    message = message,
                    onSpeak = { onSpeak(StudyStats.naturalReplyOf(message.text)) }
                )
            }
            if (isSending) {
                item { SendingIndicator() }
            }
            item { Spacer(modifier = Modifier.height(10.dp)) }
        }
        ConversationComposer(
            input = input,
            isSending = isSending,
            onInputChange = { viewModel.setDraft(chatId, it) },
            onSend = {
                viewModel.send(chatId, mode, input) { viewModel.clearDraft(chatId) }
            },
            onMic = { onMic(chatId) },
            onQuickAssist = { viewModel.openQuickAssist(chatId) }
        )
    }

    if (viewModel.quickAssist.visibleFor == chatId) {
        QuickAssistSheet(chatId = chatId, viewModel = viewModel, onMic = onAssistMic)
    }
}

@Composable
fun MessageBubble(
    message: ChatMessage,
    onSpeak: () -> Unit
) {
    val isUser = message.role == MessageRole.User
    val background = when (message.role) {
        MessageRole.User -> Color(0xFFEADBB4)
        MessageRole.Coach -> Grimoire.ParchmentLight
        MessageRole.System -> Color(0xFFE3CF9C)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(if (isUser) 0.86f else 0.96f)
                .grimoireBorder(10.dp),
            color = background,
            shape = RoundedCornerShape(10.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = when (message.role) {
                            MessageRole.User -> "YOU"
                            MessageRole.Coach -> "✦ COACH"
                            MessageRole.System -> "✦ TOPIC ✦"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isUser) Grimoire.InkFaded else Grimoire.Burgundy,
                        modifier = Modifier.weight(1f)
                    )
                    if (message.role == MessageRole.Coach) {
                        IconButton(onClick = onSpeak, modifier = Modifier.size(28.dp)) {
                            Icon(
                                Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = "Read aloud",
                                tint = Grimoire.GoldDark,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                SelectionContainer {
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Grimoire.Ink
                    )
                }
            }
        }
    }
}

@Composable
fun SendingIndicator(label: String = "The quill is writing...") {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            color = Grimoire.Gold,
            strokeWidth = 2.dp
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontStyle = FontStyle.Italic,
            color = Grimoire.InkFaded
        )
    }
}

@Composable
fun ConversationComposer(
    input: String,
    isSending: Boolean,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onMic: () -> Unit,
    onQuickAssist: () -> Unit
) {
    Column {
        OrnamentDivider(modifier = Modifier.fillMaxWidth())
        Surface(color = Color.Transparent) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(12.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                IconButton(onClick = onQuickAssist) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = "Quick Assist",
                        tint = Grimoire.Burgundy
                    )
                }
                IconButton(onClick = onMic) {
                    Icon(Icons.Default.Mic, contentDescription = "Voice input", tint = Grimoire.Burgundy)
                }
                OutlinedTextField(
                    value = input,
                    onValueChange = onInputChange,
                    modifier = Modifier.weight(1f),
                    minLines = 1,
                    maxLines = 5,
                    placeholder = {
                        Text(
                            "English / 日本語 どちらでもOK",
                            fontStyle = FontStyle.Italic,
                            color = Grimoire.InkFaded
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Grimoire.Gold,
                        unfocusedBorderColor = Grimoire.GoldDark.copy(alpha = 0.6f),
                        focusedContainerColor = Grimoire.ParchmentLight,
                        unfocusedContainerColor = Grimoire.ParchmentLight
                    )
                )
                Spacer(modifier = Modifier.width(6.dp))
                IconButton(
                    onClick = onSend,
                    enabled = input.isNotBlank() && !isSending
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Grimoire.Burgundy)
                }
            }
        }
    }
}

/**
 * Quick Assist (FR-005):
 * 詰まった内容を書く → AIに聞く → 候補1〜3 → 挿入(追記) → 編集して送信。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAssistSheet(
    chatId: Long,
    viewModel: AppViewModel,
    onMic: () -> Unit
) {
    val state = viewModel.quickAssist

    ModalBottomSheet(
        onDismissRequest = { viewModel.closeQuickAssist() },
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
                text = "✦ Quick Assist",
                style = MaterialTheme.typography.titleMedium,
                color = Grimoire.BurgundyDeep
            )
            Text(
                text = "言いたいことが英語で出てこないときは、日本語のままでOK。自然な言い方を提案します。",
                style = MaterialTheme.typography.bodySmall,
                color = Grimoire.InkFaded
            )
            Row(verticalAlignment = Alignment.Bottom) {
                OutlinedTextField(
                    value = state.question,
                    onValueChange = { viewModel.updateAssistQuestion(it) },
                    modifier = Modifier.weight(1f),
                    minLines = 2,
                    maxLines = 4,
                    placeholder = { Text("例: 最近値上がりしてて外食を控えてる、って言いたい") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Grimoire.Gold,
                        unfocusedBorderColor = Grimoire.GoldDark.copy(alpha = 0.6f)
                    ),
                    enabled = !state.loading
                )
                IconButton(onClick = onMic, enabled = !state.loading) {
                    Icon(Icons.Default.Mic, contentDescription = "日本語で音声入力", tint = Grimoire.Burgundy)
                }
            }
            Button(
                onClick = {
                    viewModel.askQuickAssist(chatId, viewModel.draftOf(chatId), state.question)
                },
                enabled = state.question.isNotBlank() && !state.loading,
                colors = ButtonDefaults.buttonColors(containerColor = Grimoire.Burgundy)
            ) {
                Text(if (state.loading) "考え中..." else "AIに聞く")
            }
            if (state.loading) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = Grimoire.Gold,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "候補を探しています...",
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = FontStyle.Italic,
                        color = Grimoire.InkFaded
                    )
                }
            }
            state.error?.let { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = Grimoire.Burgundy
                )
            }
            if (state.suggestions.isNotEmpty()) {
                Text(
                    text = "「追記」は入力欄の続きに足します。「差し替え」は入力欄を丸ごと置き換えます。送信前に自由に編集できます。",
                    style = MaterialTheme.typography.labelSmall,
                    color = Grimoire.Gold
                )
                state.suggestions.forEach { suggestion ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .grimoireBorder(8.dp),
                        colors = CardDefaults.cardColors(containerColor = Grimoire.ParchmentDeep),
                        shape = RoundedCornerShape(8.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            SelectionContainer {
                                Text(
                                    text = suggestion.english,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Grimoire.Ink
                                )
                            }
                            if (suggestion.note.isNotBlank()) {
                                Text(
                                    text = suggestion.note,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Grimoire.InkFaded
                                )
                            }
                            Row {
                                TextButton(onClick = {
                                    viewModel.appendToDraft(chatId, suggestion.english)
                                    viewModel.closeQuickAssist()
                                }) {
                                    Text("追記", color = Grimoire.Burgundy)
                                }
                                TextButton(onClick = {
                                    viewModel.replaceDraft(chatId, suggestion.english)
                                    viewModel.closeQuickAssist()
                                }) {
                                    Text("差し替え", color = Grimoire.GoldDark)
                                }
                            }
                        }
                    }
                }
            } else if (!state.rawResult.isNullOrBlank() && !state.loading) {
                SelectionContainer {
                    Text(
                        text = state.rawResult,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Grimoire.Ink
                    )
                }
            }
            TextButton(onClick = { viewModel.closeQuickAssist() }) {
                Text("閉じる", color = Grimoire.InkFaded)
            }
        }
    }
}
