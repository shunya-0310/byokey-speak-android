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
    onSpeak: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val openChatId = viewModel.openQuickChatId
    if (openChatId != null) {
        QuickChatDetail(
            chatId = openChatId,
            viewModel = viewModel,
            onMic = onMic,
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
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            containerColor = Grimoire.ParchmentLight,
            title = { Text("チャットを削除しますか?", color = Grimoire.BurgundyDeep) },
            text = { Text("このチャットの会話履歴は端末から削除され、復元できません。", color = Grimoire.Ink) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteChat(chatId)
                        deleteTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Grimoire.Burgundy)
                ) { Text("削除") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("キャンセル", color = Grimoire.InkFaded)
                }
            }
        )
    }
}

@Composable
private fun ChatListItem(
    chat: ChatWithPreview,
    onOpen: () -> Unit,
    onPinToggle: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .grimoireBorder(10.dp)
            .clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(containerColor = Grimoire.ParchmentLight),
        shape = RoundedCornerShape(10.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (chat.pinned) {
                        Icon(
                            Icons.Default.PushPin,
                            contentDescription = "Pinned",
                            tint = Grimoire.Gold,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                    }
                    Text(
                        text = chat.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = Grimoire.Ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = chat.lastMessage ?: "(まだメッセージがありません)",
                    style = MaterialTheme.typography.bodySmall,
                    color = Grimoire.InkFaded,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = formatChatDate(chat.updatedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = Grimoire.Gold
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Chat menu", tint = Grimoire.GoldDark)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(if (chat.pinned) "ピン留めを外す" else "ピン留め") },
                        onClick = {
                            menuOpen = false
                            onPinToggle()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("名前を変更") },
                        onClick = {
                            menuOpen = false
                            onRename()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("削除") },
                        onClick = {
                            menuOpen = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun RenameDialog(
    currentTitle: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var title by rememberSaveable { mutableStateOf(currentTitle) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Grimoire.ParchmentLight,
        title = { Text("チャット名を変更", color = Grimoire.BurgundyDeep) },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                singleLine = true
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(title) },
                enabled = title.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Grimoire.Burgundy)
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("キャンセル", color = Grimoire.InkFaded)
            }
        }
    )
}

private fun formatChatDate(timestampMillis: Long): String {
    return SimpleDateFormat("M/d HH:mm", Locale.getDefault()).format(Date(timestampMillis))
}
