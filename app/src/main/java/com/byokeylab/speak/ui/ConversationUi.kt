package com.byokeylab.speak.ui

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.byokeylab.speak.AppViewModel
import com.byokeylab.speak.AiReportEndpointUnavailableException
import com.byokeylab.speak.AiReportReason
import com.byokeylab.speak.AppLinks
import com.byokeylab.speak.AppSound
import com.byokeylab.speak.ChatMessage
import com.byokeylab.speak.CoachTranslationUiState
import com.byokeylab.speak.ConversationAutoMode
import com.byokeylab.speak.Grimoire
import com.byokeylab.speak.MessageRole
import com.byokeylab.speak.LocalAppSoundEffects
import com.byokeylab.speak.NewsConversation
import com.byokeylab.speak.OrnamentDivider
import com.byokeylab.speak.StudyStats
import com.byokeylab.speak.TutorialTargets
import com.byokeylab.speak.data.ChatMode
import com.byokeylab.speak.grimoireBorder
import com.byokeylab.speak.tutorialAnchor
import kotlinx.coroutines.delay

/**
 * CoachとChatsで共有する会話ペイン。
 * Quick Assist・音声入力・送信・読み上げを常備する (FR-003/FR-004/FR-005)。
 */
@Composable
fun ConversationPane(
    chatId: Long,
    mode: ChatMode,
    viewModel: AppViewModel,
    onEnglishMic: (Long) -> Unit,
    onJapaneseMic: (Long) -> Unit,
    onAssistMic: () -> Unit,
    onSpeak: (Long?, String) -> Unit,
    speakingMessageId: Long?,
    onPauseSpeaking: () -> Unit,
    modifier: Modifier = Modifier,
    emptyHint: String = "Say hello to start the conversation.",
    header: (@Composable () -> Unit)? = null
) {
    val messages by remember(chatId) { viewModel.messagesFlow(chatId) }
        .collectAsState(initial = emptyList())
    val visibleMessages = messages.filterNot(NewsConversation::isHidden)
    val isSending = viewModel.sendingChatId == chatId
    val input = viewModel.draftOf(chatId)
    val forceWebSearch = viewModel.isWebSearchForced(chatId)
    val listState = rememberLazyListState()
    val soundEffects = LocalAppSoundEffects.current
    var reportMessage by remember { mutableStateOf<ChatMessage?>(null) }

    LaunchedEffect(visibleMessages.size, isSending) {
        if (visibleMessages.isNotEmpty()) {
            listState.animateScrollToItem(visibleMessages.lastIndex)
        }
    }

    Column(modifier = modifier.imePadding()) {
        header?.invoke()
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (visibleMessages.isEmpty() && !isSending) {
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
            items(visibleMessages, key = { it.id }) { message ->
                MessageBubble(
                    message = message,
                    translation = viewModel.coachTranslationOf(message.id),
                    isSpeaking = speakingMessageId == message.id,
                    onToggleTranslation = {
                        soundEffects?.play(AppSound.Select)
                        viewModel.toggleCoachTranslation(message)
                    },
                    onPauseSpeaking = onPauseSpeaking,
                    onReport = { reportMessage = message },
                    onSpeak = {
                        onSpeak(
                            message.id,
                            StudyStats.naturalReplyOf(conversationDisplayText(message.text))
                                .removePrefix("New topic:")
                                .trim()
                        )
                    }
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
            autoMode = viewModel.autoMode,
            forceWebSearch = forceWebSearch,
            canUndo = viewModel.canUndoDraft(chatId),
            onInputChange = { viewModel.setDraft(chatId, it) },
            onUndo = { viewModel.undoDraft(chatId) },
            onSend = {
                viewModel.send(chatId, mode, input) {
                    soundEffects?.play(AppSound.MessageSend)
                    viewModel.clearDraft(chatId)
                }
            },
            onEnglishMic = { onEnglishMic(chatId) },
            onJapaneseMic = { onJapaneseMic(chatId) },
            onQuickAssist = { viewModel.openQuickAssist(chatId) },
            onToggleWebSearch = {
                soundEffects?.play(AppSound.Select)
                viewModel.toggleWebSearch(chatId)
            },
            onToggleAuto = {
                soundEffects?.play(AppSound.Select)
                val nextMode = viewModel.cycleAutoMode()
                if (nextMode.startsMicAfterReading) {
                    // 自動モードへ切り替えた瞬間、直近のコーチ発話からループを開始する。
                    messages.lastOrNull { it.role == MessageRole.Coach }?.let { last ->
                        viewModel.coachReplyHook?.invoke(
                            chatId,
                            StudyStats.naturalReplyOf(conversationDisplayText(last.text))
                        )
                    }
                }
            }
        )
    }

    if (viewModel.quickAssist.visibleFor == chatId) {
        QuickAssistSheet(chatId = chatId, viewModel = viewModel, onMic = onAssistMic)
    }

    reportMessage?.let { selected ->
        val previousUserMessage = visibleMessages
            .takeWhile { it.id != selected.id }
            .lastOrNull { it.role == MessageRole.User }
            ?.text
        AiContentReportDialog(
            coachMessage = conversationDisplayText(selected.text),
            previousUserMessage = previousUserMessage,
            onDismiss = { reportMessage = null },
            onSubmit = { reason, comment, includeUser, onResult ->
                viewModel.submitAiContentReport(
                    coachMessage = conversationDisplayText(selected.text),
                    precedingUserMessage = previousUserMessage,
                    reason = reason,
                    comment = comment,
                    includeUserMessage = includeUser,
                    onResult = onResult
                )
            }
        )
    }
}

@Composable
fun MessageBubble(
    message: ChatMessage,
    translation: CoachTranslationUiState,
    isSpeaking: Boolean,
    onToggleTranslation: () -> Unit,
    onPauseSpeaking: () -> Unit,
    onReport: () -> Unit,
    onSpeak: () -> Unit
) {
    val isUser = message.role == MessageRole.User
    val displayText = conversationDisplayText(message.text)
    val background = when (message.role) {
        MessageRole.User -> Grimoire.BubbleUser
        MessageRole.Coach -> Grimoire.ParchmentLight
        MessageRole.System -> Grimoire.BubbleSystem
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
                        IconButton(onClick = onToggleTranslation, modifier = Modifier.size(28.dp)) {
                            Icon(
                                Icons.Default.Translate,
                                contentDescription = "日本語訳を表示",
                                tint = if (translation.visible) Grimoire.Burgundy else Grimoire.GoldDark,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(onClick = onReport, modifier = Modifier.size(28.dp)) {
                            Icon(
                                Icons.Default.Flag,
                                contentDescription = "このAI返信を報告",
                                tint = Grimoire.GoldDark,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    if (message.role == MessageRole.Coach || message.role == MessageRole.System) {
                        IconButton(
                            onClick = if (isSpeaking) onPauseSpeaking else onSpeak,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                if (isSpeaking) Icons.Default.Pause else Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = if (isSpeaking) "読み上げを一時停止" else "Read aloud",
                                tint = Grimoire.GoldDark,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                LinkifiedMessageText(displayText)
                if (message.role == MessageRole.Coach && translation.visible) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = Grimoire.ParchmentDeep,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "日本語訳",
                                style = MaterialTheme.typography.labelSmall,
                                color = Grimoire.Burgundy
                            )
                            when {
                                translation.loading -> {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            color = Grimoire.Gold,
                                            strokeWidth = 2.dp
                                        )
                                        Text(
                                            text = "翻訳しています...",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Grimoire.InkFaded
                                        )
                                    }
                                }
                                translation.error != null -> {
                                    Text(
                                        text = translation.error,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Grimoire.Burgundy
                                    )
                                }
                                else -> {
                                    SelectionContainer {
                                        Text(
                                            text = translation.text,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Grimoire.Ink
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AiContentReportDialog(
    coachMessage: String,
    previousUserMessage: String?,
    onDismiss: () -> Unit,
    onSubmit: (
        AiReportReason,
        String,
        Boolean,
        (Result<Unit>) -> Unit
    ) -> Unit
) {
    var reason by remember { mutableStateOf(AiReportReason.Inappropriate) }
    var comment by remember { mutableStateOf("") }
    var includeUserMessage by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val clipboard = LocalClipboardManager.current
    val uriHandler = LocalUriHandler.current

    AlertDialog(
        onDismissRequest = { if (!sending) onDismiss() },
        containerColor = Grimoire.ParchmentLight,
        title = { Text("このAI返信を報告", color = Grimoire.BurgundyDeep) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "理由を選んでください。APIキー、メールアドレス、端末IDは送信しません。",
                    style = MaterialTheme.typography.bodySmall,
                    color = Grimoire.Ink
                )
                AiReportReason.entries.forEach { candidate ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { reason = candidate },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = reason == candidate,
                            onClick = { reason = candidate }
                        )
                        Text(candidate.label, color = Grimoire.Ink)
                    }
                }
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it.take(1_000) },
                    label = { Text("補足（任意）") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
                if (previousUserMessage != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = includeUserMessage,
                            onCheckedChange = { includeUserMessage = it }
                        )
                        Text(
                            "直前の自分の発言も送る",
                            style = MaterialTheme.typography.bodySmall,
                            color = Grimoire.Ink
                        )
                    }
                }
                error?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = Grimoire.Burgundy)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    sending = true
                    error = null
                    onSubmit(reason, comment, includeUserMessage) { result ->
                        sending = false
                        result.fold(
                            onSuccess = { onDismiss() },
                            onFailure = { failure ->
                                if (failure is AiReportEndpointUnavailableException) {
                                    val reportText = buildString {
                                        appendLine("AI返信の報告: ${reason.label}")
                                        appendLine("AI返信:")
                                        appendLine(coachMessage)
                                        if (includeUserMessage && previousUserMessage != null) {
                                            appendLine("直前のユーザー発言:")
                                            appendLine(previousUserMessage)
                                        }
                                        if (comment.isNotBlank()) {
                                            appendLine("補足:")
                                            appendLine(comment)
                                        }
                                    }
                                    clipboard.setText(AnnotatedString(reportText))
                                    uriHandler.openUri(AppLinks.CONTACT_FORM)
                                    onDismiss()
                                } else {
                                    error = failure.message ?: "通報を送信できませんでした。"
                                }
                            }
                        )
                    }
                },
                enabled = !sending,
                colors = ButtonDefaults.buttonColors(containerColor = Grimoire.Burgundy)
            ) {
                if (sending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                } else {
                    Text("報告する")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !sending) {
                Text("キャンセル", color = Grimoire.InkFaded)
            }
        }
    )
}

private fun conversationDisplayText(text: String): String {
    return text
        .removeSuffix("\n\n※今回はWeb検索を利用できなかったため、最新情報ではない可能性があります。")
        .removeSuffix("\n\n※Web検索の出典を取得できなかったため、最新情報ではない可能性があります。")
        .trimEnd()
}

@Suppress("DEPRECATION")
@Composable
private fun LinkifiedMessageText(text: String) {
    val uriHandler = LocalUriHandler.current
    val urlRegex = remember { Regex("https://[^\\s]+") }
    val matches = remember(text) { urlRegex.findAll(text).toList() }
    if (matches.isEmpty()) {
        SelectionContainer {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = Grimoire.Ink
            )
        }
        return
    }

    val linkColor = Grimoire.Burgundy
    val annotated = remember(text, linkColor) {
        buildAnnotatedString {
            var previousEnd = 0
            matches.forEach { match ->
                val url = match.value.trimEnd('.', ',', ')', ']', '。', '、')
                append(text.substring(previousEnd, match.range.first))
                val start = length
                append(compactUrlLabel(url))
                val end = length
                addStringAnnotation("URL", url, start, end)
                addStyle(
                    SpanStyle(
                        color = linkColor,
                        textDecoration = TextDecoration.Underline
                    ),
                    start,
                    end
                )
                previousEnd = match.range.last + 1
            }
            append(text.substring(previousEnd))
        }
    }
    ClickableText(
        text = annotated,
        style = MaterialTheme.typography.bodyMedium.copy(color = Grimoire.Ink),
        onClick = { offset ->
            annotated.getStringAnnotations("URL", offset, offset)
                .firstOrNull()
                ?.let { annotation -> runCatching { uriHandler.openUri(annotation.item) } }
        }
    )
}

private fun compactUrlLabel(url: String): String {
    val host = runCatching { Uri.parse(url).host.orEmpty().removePrefix("www.") }.getOrDefault("")
    return if (host.isBlank()) "リンク ↗" else "$host ↗"
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
    autoMode: ConversationAutoMode,
    forceWebSearch: Boolean,
    canUndo: Boolean,
    onInputChange: (String) -> Unit,
    onUndo: () -> Unit,
    onSend: () -> Unit,
    onEnglishMic: () -> Unit,
    onJapaneseMic: () -> Unit,
    onQuickAssist: () -> Unit,
    onToggleWebSearch: () -> Unit,
    onToggleAuto: () -> Unit
) {
    var webSearchNoticeToken by remember { mutableIntStateOf(0) }
    var webSearchNoticeVisible by remember { mutableStateOf(false) }

    LaunchedEffect(webSearchNoticeToken) {
        if (webSearchNoticeToken == 0) return@LaunchedEffect
        webSearchNoticeVisible = true
        delay(2_000)
        webSearchNoticeVisible = false
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        Column {
            OrnamentDivider(modifier = Modifier.fillMaxWidth())
            if (autoMode != ConversationAutoMode.Off) {
                Text(
                    text = when (autoMode) {
                        ConversationAutoMode.ReadAndMicAuto ->
                            "✦ 読み上げ&マイクオート: 返信の読み上げ後に英語マイクが起動します"
                        ConversationAutoMode.Off -> ""
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = Grimoire.Gold,
                    modifier = Modifier.padding(start = 16.dp, top = 4.dp)
                )
            }
            Surface(
                color = Color.Transparent
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .tutorialAnchor(TutorialTargets.CHAT_AUTO_MODE)
                        ) {
                            IconButton(onClick = onToggleAuto) {
                                Icon(
                                    Icons.Default.Headset,
                                    contentDescription = when (autoMode) {
                                        ConversationAutoMode.Off -> "通常モード"
                                        ConversationAutoMode.ReadAndMicAuto -> "読み上げ&マイクオート"
                                    },
                                    tint = if (autoMode != ConversationAutoMode.Off) {
                                        Grimoire.Gold
                                    } else {
                                        Grimoire.InkFaded
                                    }
                                )
                            }
                            if (autoMode != ConversationAutoMode.Off) {
                                Text(
                                    text = "A",
                                    modifier = Modifier.align(Alignment.BottomEnd),
                                    color = Grimoire.Burgundy,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        IconButton(
                            onClick = onQuickAssist,
                            modifier = Modifier.tutorialAnchor(TutorialTargets.CHAT_QUICK_ASSIST)
                        ) {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = "Quick Assist",
                                tint = Grimoire.Burgundy
                            )
                        }
                        IconButton(
                            onClick = {
                                val turningOn = !forceWebSearch
                                onToggleWebSearch()
                                if (turningOn) webSearchNoticeToken += 1
                            },
                            modifier = Modifier.tutorialAnchor(TutorialTargets.CHAT_WEB_SEARCH),
                            enabled = !isSending
                        ) {
                            Icon(
                                Icons.Default.Public,
                                contentDescription = if (forceWebSearch) {
                                    "次の送信でWeb検索を使う: ON"
                                } else {
                                    "次の送信でWeb検索を使う"
                                },
                                tint = if (forceWebSearch) Grimoire.Gold else Grimoire.InkFaded
                            )
                        }
                        IconButton(
                            onClick = onUndo,
                            enabled = canUndo && !isSending
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Undo,
                                contentDescription = "入力をひとつ元に戻す",
                                tint = if (canUndo && !isSending) {
                                    Grimoire.GoldDark
                                } else {
                                    Grimoire.InkFaded.copy(alpha = 0.35f)
                                }
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        OutlinedTextField(
                            value = input,
                            onValueChange = onInputChange,
                            modifier = Modifier.weight(1f),
                            minLines = 1,
                            maxLines = 5,
                            placeholder = {
                                Text(
                                    "Let's talk!",
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
                        Spacer(modifier = Modifier.width(4.dp))
                        Row(
                            modifier = Modifier.tutorialAnchor(TutorialTargets.CHAT_MIC),
                            horizontalArrangement = Arrangement.spacedBy(0.dp)
                        ) {
                            LanguageMicButton(
                                language = "英",
                                contentDescription = "英語音声入力",
                                onClick = onEnglishMic
                            )
                            LanguageMicButton(
                                language = "日",
                                contentDescription = "日本語音声入力",
                                onClick = onJapaneseMic
                            )
                        }
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

        AnimatedVisibility(
            visible = webSearchNoticeVisible,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-48).dp)
                .padding(horizontal = 20.dp),
            enter = fadeIn(animationSpec = tween(120)),
            exit = fadeOut(animationSpec = tween(280))
        ) {
            Surface(
                color = Grimoire.Ink,
                shape = RoundedCornerShape(6.dp),
                shadowElevation = 6.dp
            ) {
                Text(
                    text = "次のメッセージでは、Web検索で最新情報を確認しながら回答します。",
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = Grimoire.ParchmentLight
                )
            }
        }
    }
}

@Composable
private fun LanguageMicButton(
    language: String,
    contentDescription: String,
    onClick: () -> Unit
) {
    Box(modifier = Modifier.size(42.dp)) {
        IconButton(onClick = onClick, modifier = Modifier.size(42.dp)) {
            Icon(
                Icons.Default.Mic,
                contentDescription = contentDescription,
                tint = Grimoire.Burgundy
            )
        }
        Text(
            text = language,
            modifier = Modifier.align(Alignment.BottomEnd),
            color = Grimoire.GoldDark,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
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
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "✦ Quick Assist",
                    style = MaterialTheme.typography.titleMedium,
                    color = Grimoire.BurgundyDeep,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { viewModel.startNewQuickAssist(chatId) },
                    enabled = !state.loading
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "新しいQuick Assist相談",
                        tint = Grimoire.Burgundy
                    )
                }
            }
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
                                    viewModel.adoptAssistSuggestion(chatId, suggestion, replace = false)
                                }) {
                                    Text("追記", color = Grimoire.Burgundy)
                                }
                                TextButton(onClick = {
                                    viewModel.adoptAssistSuggestion(chatId, suggestion, replace = true)
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
