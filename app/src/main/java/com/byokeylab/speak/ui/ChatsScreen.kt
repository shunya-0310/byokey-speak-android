package com.byokeylab.speak.ui

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.byokeylab.speak.AppViewModel
import com.byokeylab.speak.Grimoire
import com.byokeylab.speak.data.ChatMode
import com.byokeylab.speak.data.ChatWithPreview
import com.byokeylab.speak.grimoireBorder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Chats (FR-004): LINEのような複数チャット。
 * New Chatワンタップ、ピン留め、削除、名前変更、最終メッセージプレビュー。
 */
@Composable
fun ChatsScreen(
    viewModel: AppViewModel,
    onMic: (Long) -> Unit,
    onAssistMic: () -> Unit,
    onSpeak: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val openChatId = viewModel.openQuickChatId
    if (openChatId != null) {
        QuickChatDetail(
            chatId = openChatId,
            viewModel = viewModel,
            onMic = onMic,
            onAssistMic = onAssistMic,
            onSpeak = onSpeak,
            modifier = modifier
        )
    } else {
        ChatList(viewModel = viewModel, modifier = modifier)
    }
}

@Composable
private fun QuickChatDetail(
    chatId: Long,
    viewModel: AppViewModel,
    onMic: (Long) -> Unit,
    onAssistMic: () -> Unit,
    onSpeak: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val chats by viewModel.quickChats.collectAsState()
    val title = chats.firstOrNull { it.id == chatId }?.title ?: "Chat"

    ConversationPane(
        chatId = chatId,
        mode = ChatMode.Quick,
        viewModel = viewModel,
        onMic = onMic,
        onAssistMic = onAssistMic,
        onSpeak = onSpeak,
        modifier = modifier,
        emptyHint = "気軽にどうぞ。今日あったことを一言でもOK。",
        header = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.openQuickChat(null) }) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to chats",
                        tint = Grimoire.GoldDark
                    )
                }
                Text(
                    text = title,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall,
                    color = Grimoire.Burgundy
                )
            }
        }
    )
}

@Composable
private fun ChatList(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val chats by viewModel.quickChats.collectAsState()
    var renameTarget by rememberSaveable { mutableStateOf<Long?>(null) }
    var deleteTarget by rememberSaveable { mutableStateOf<Long?>(null) }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "✦ Chats",
                style = MaterialTheme.typography.titleMedium,
                color = Grimoire.BurgundyDeep,
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = { viewModel.newQuickChat() },
                colors = ButtonDefaults.buttonColors(containerColor = Grimoire.Burgundy)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.padding(2.dp))
                Text("New Chat")
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
                    text = "まだチャットがありません。\nNew Chatを押して、今日のことを一言話してみましょう。",
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
                        onOpen = { viewModel.openQuickChat(chat.id) },
                        onPinToggle = { viewModel.setPinned(chat.id, !chat.pinned) },
                        onRename = { renameTarget = chat.id },
                        onDelete = { deleteTarget = chat.id }
                    )
                }
                item { Spacer(modifier = Modifier.height(12.dp)) }
            }
        }
    }

    renameTarget?.let { chatId ->
        val current = chats.firstOrNull { it.id == chatId }?.title.orEmpty()
        RenameDialog(
            currentTitle = current,
            onConfirm = { newTitle ->
                viewModel.renameChat(chatId, newTitle)
                renameTarget = null
            },
            onDismiss = { renameTarget = null }
        )
    }

    deleteTarget?.let { chatId ->
        DeleteChatDialog(
            onConfirm = {
                viewModel.deleteChat(chatId)
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null }
        )
    }
}
