package com.byokeylab.speak.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.byokeylab.speak.AppViewModel
import com.byokeylab.speak.AppSound
import com.byokeylab.speak.DailyNewsItem
import com.byokeylab.speak.DailyNewsUiState
import com.byokeylab.speak.Grimoire
import com.byokeylab.speak.LocalAppSoundEffects
import com.byokeylab.speak.NewsCategory
import com.byokeylab.speak.TutorialTargets
import com.byokeylab.speak.data.ChatMode
import com.byokeylab.speak.data.ChatWithPreview
import com.byokeylab.speak.grimoireBorder
import com.byokeylab.speak.tutorialAnchor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.net.URL

/**
 * Chats (FR-004): LINEのような複数チャット。
 * New Chatワンタップ、ピン留め、削除、名前変更、最終メッセージプレビュー。
 */
@Composable
fun ChatsScreen(
    viewModel: AppViewModel,
    onEnglishMic: (Long) -> Unit,
    onJapaneseMic: (Long) -> Unit,
    onAssistMic: () -> Unit,
    onSpeak: (Long?, String) -> Unit,
    speakingMessageId: Long?,
    onPauseSpeaking: () -> Unit,
    compactLayout: Boolean = false,
    modifier: Modifier = Modifier
) {
    val openChatId = viewModel.openQuickChatId
    if (openChatId != null) {
        QuickChatDetail(
            chatId = openChatId,
            viewModel = viewModel,
            onEnglishMic = onEnglishMic,
            onJapaneseMic = onJapaneseMic,
            onAssistMic = onAssistMic,
            onSpeak = onSpeak,
            speakingMessageId = speakingMessageId,
            onPauseSpeaking = onPauseSpeaking,
            modifier = modifier
        )
    } else {
        ChatList(viewModel = viewModel, compactLayout = compactLayout, modifier = modifier)
    }
}

@Composable
private fun QuickChatDetail(
    chatId: Long,
    viewModel: AppViewModel,
    onEnglishMic: (Long) -> Unit,
    onJapaneseMic: (Long) -> Unit,
    onAssistMic: () -> Unit,
    onSpeak: (Long?, String) -> Unit,
    speakingMessageId: Long?,
    onPauseSpeaking: () -> Unit,
    modifier: Modifier = Modifier
) {
    val chats by viewModel.quickChats.collectAsState()
    val title = chats.firstOrNull { it.id == chatId }?.title ?: "Chat"

    ConversationPane(
        chatId = chatId,
        mode = ChatMode.Quick,
        viewModel = viewModel,
        onEnglishMic = onEnglishMic,
        onJapaneseMic = onJapaneseMic,
        onAssistMic = onAssistMic,
        onSpeak = onSpeak,
        speakingMessageId = speakingMessageId,
        onPauseSpeaking = onPauseSpeaking,
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
    compactLayout: Boolean,
    modifier: Modifier = Modifier
) {
    val chats by viewModel.quickChats.collectAsState()
    var renameTarget by rememberSaveable { mutableStateOf<Long?>(null) }
    var deleteTarget by rememberSaveable { mutableStateOf<Long?>(null) }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = if (compactLayout) 4.dp else 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "✦ Chats",
                style = if (compactLayout) {
                    MaterialTheme.typography.titleSmall
                } else {
                    MaterialTheme.typography.titleMedium
                },
                color = Grimoire.BurgundyDeep,
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = { viewModel.newQuickChat() },
                modifier = Modifier.tutorialAnchor(TutorialTargets.CHATS_NEW_CHAT),
                colors = ButtonDefaults.buttonColors(containerColor = Grimoire.Burgundy)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.padding(2.dp))
                Text("New Chat")
            }
        }

        DailyNewsSection(
            state = viewModel.dailyNews,
            onRefresh = { viewModel.refreshDailyNews(manual = true) },
            onStartChat = viewModel::startNewsChat,
            compactLayout = compactLayout,
            modifier = Modifier.tutorialAnchor(TutorialTargets.CHATS_DAILY_NEWS)
        )
        HorizontalDivider(
            modifier = Modifier.padding(
                start = 16.dp,
                end = 16.dp,
                bottom = if (compactLayout) 6.dp else 10.dp
            ),
            thickness = 1.dp,
            color = Grimoire.GoldDark.copy(alpha = 0.55f)
        )
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

@Composable
private fun DailyNewsSection(
    state: DailyNewsUiState,
    onRefresh: () -> Unit,
    onStartChat: (DailyNewsItem) -> Unit,
    compactLayout: Boolean,
    modifier: Modifier = Modifier
) {
    val soundEffects = LocalAppSoundEffects.current
    val feed = state.feed
    val items = feed?.items.orEmpty()
    val pagerState = rememberPagerState(pageCount = { items.size })
    val categoryListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val selectedCategory = items.getOrNull(pagerState.currentPage)?.category

    LaunchedEffect(selectedCategory) {
        val categoryIndex = NewsCategory.entries.indexOf(selectedCategory)
        if (categoryIndex >= 0) {
            categoryListState.animateScrollToItem(categoryIndex)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = if (compactLayout) 6.dp else 10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "TODAY'S WORLD",
                    style = if (compactLayout) {
                        MaterialTheme.typography.labelMedium
                    } else {
                        MaterialTheme.typography.labelLarge
                    },
                    color = Grimoire.Burgundy,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = feed?.date ?: "最新ニュースを準備中",
                    style = MaterialTheme.typography.labelSmall,
                    color = Grimoire.InkFaded
                )
            }
            if (state.loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = Grimoire.Gold,
                    strokeWidth = 2.dp
                )
            } else {
                TextButton(onClick = onRefresh) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = null,
                        tint = Grimoire.GoldDark,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Reload", color = Grimoire.GoldDark)
                }
            }
        }

        LazyRow(
            state = categoryListState,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = if (compactLayout) 2.dp else 4.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(NewsCategory.entries, key = { it.id }) { category ->
                FilterChip(
                    selected = selectedCategory == category,
                    onClick = {
                        val targetPage = items.indexOfFirst { it.category == category }
                        if (targetPage >= 0 && targetPage != pagerState.currentPage) {
                            soundEffects?.play(AppSound.Select)
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(targetPage)
                            }
                        }
                    },
                    enabled = items.any { it.category == category },
                    label = { Text(category.label, maxLines = 1) }
                )
            }
        }

        listOfNotNull(state.notice, state.error).distinct().forEach { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.labelSmall,
                color = Grimoire.InkFaded,
                modifier = Modifier.padding(
                    horizontal = 16.dp,
                    vertical = if (compactLayout) 2.dp else 4.dp
                )
            )
        }

        if (items.isEmpty()) {
            if (!state.loading) {
                Text(
                    text = "ニュース記事はまだありません。",
                    style = MaterialTheme.typography.bodySmall,
                    color = Grimoire.InkFaded,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
                )
            }
        } else {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                pageSpacing = 10.dp,
                key = { page -> items[page].id }
            ) { page ->
                val item = items[page]
                DailyNewsCard(
                    item = item,
                    onStartChat = { onStartChat(item) },
                    compactLayout = compactLayout,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (items.size > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = if (compactLayout) 4.dp else 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items.indices.forEach { page ->
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 3.dp)
                                .size(if (page == pagerState.currentPage) 8.dp else 6.dp)
                                .clip(CircleShape)
                                .background(
                                    if (page == pagerState.currentPage) {
                                        Grimoire.Burgundy
                                    } else {
                                        Grimoire.GoldDark.copy(alpha = 0.4f)
                                    }
                                )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DailyNewsCard(
    item: DailyNewsItem,
    onStartChat: () -> Unit,
    compactLayout: Boolean,
    modifier: Modifier = Modifier
) {
    val uriHandler = LocalUriHandler.current
    val primarySource = item.sources.firstOrNull()
    val opensSourceList = !item.canStartConversation
    Card(
        modifier = modifier
            .height(if (compactLayout) 142.dp else 214.dp)
            .grimoireBorder(8.dp),
        colors = CardDefaults.cardColors(containerColor = Grimoire.ParchmentLight),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (compactLayout) 8.dp else 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SourceIcon(
                    source = item.sources.firstOrNull(),
                    modifier = Modifier.size(if (compactLayout) 28.dp else 34.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.category.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = Grimoire.GoldDark,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = item.headline,
                        style = MaterialTheme.typography.titleSmall,
                        color = Grimoire.Ink,
                        maxLines = if (compactLayout) 1 else 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            Text(
                text = item.summary,
                style = MaterialTheme.typography.bodySmall,
                color = Grimoire.InkFaded,
                maxLines = if (compactLayout) 2 else 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(top = if (compactLayout) 3.dp else 6.dp)
                    .weight(1f)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                OutlinedButton(
                    onClick = {
                        primarySource?.let { source ->
                            runCatching { uriHandler.openUri(source.url) }
                        }
                    },
                    enabled = item.sources.isNotEmpty()
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Source")
                }
                Button(
                    onClick = {
                        if (opensSourceList) {
                            primarySource?.let { source ->
                                runCatching { uriHandler.openUri(source.url) }
                            }
                        } else {
                            onStartChat()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Grimoire.Burgundy)
                ) {
                    Icon(
                        if (opensSourceList) {
                            Icons.AutoMirrored.Filled.OpenInNew
                        } else {
                            Icons.Default.ChatBubbleOutline
                        },
                        contentDescription = null,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(if (opensSourceList) "Headlines" else "Talk")
                }
            }
        }
    }
}

@Composable
private fun SourceIcon(
    source: com.byokeylab.speak.NewsSource?,
    modifier: Modifier = Modifier
) {
    val iconUrl = source?.iconUrl.orEmpty()
    val bitmap by produceState<ImageBitmap?>(initialValue = null, iconUrl) {
        value = if (iconUrl.isBlank()) {
            null
        } else {
            withContext(Dispatchers.IO) {
                runCatching {
                    URL(iconUrl).openStream().use { stream ->
                        BitmapFactory.decodeStream(stream)?.asImageBitmap()
                    }
                }.getOrNull()
            }
        }
    }
    Box(
        modifier = modifier
            .clip(CircleShape)
            .grimoireBorder(18.dp),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!,
                contentDescription = source?.title ?: "News source",
                modifier = Modifier.fillMaxSize().padding(6.dp)
            )
        } else {
            Text(
                text = source?.host?.firstOrNull()?.uppercaseChar()?.toString() ?: "N",
                style = MaterialTheme.typography.labelLarge,
                color = Grimoire.GoldDark,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
