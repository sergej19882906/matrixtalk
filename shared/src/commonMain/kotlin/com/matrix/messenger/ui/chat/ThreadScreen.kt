package com.matrix.messenger.ui.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import com.matrix.messenger.data.model.Message
import com.matrix.messenger.data.model.UiEvent
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.matrix.messenger.ui.components.TimelineItem
import com.matrix.messenger.ui.components.buildTimeline
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Element X style thread view: root message pinned on top, replies below,
 * composer sending into the m.thread relation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadScreen(
    roomId: String,
    rootId: String,
    onNavigateBack: () -> Unit,
    viewModel: ThreadViewModel = koinViewModel { parametersOf(roomId, rootId) }
) {
    val messages by viewModel.messages.collectAsState()
    val root by viewModel.root.collectAsState()
    val messageInput by viewModel.messageInput.collectAsState()
    val isSending by viewModel.isSending.collectAsState()

    val rootLoading by viewModel.rootLoading.collectAsState()
    val pinned by viewModel.pinned.collectAsState()
    val receipts by viewModel.receipts.collectAsState()
    val user by viewModel.currentUser.collectAsState(initial = null)
    val playback by viewModel.voicePlayback.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var editing by remember { mutableStateOf<Message?>(null) }
    var editText by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf<Message?>(null) }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            if (event is UiEvent.ShowSnackbar) snackbar.showSnackbar(event.message)
        }
    }
    val renderMessage: @Composable (TimelineItem.Msg) -> Unit = { item ->
        val message = item.message
        MessageItem(
            message = message,
            ownShortId = user?.shortUserId,
            ownDisplayName = user?.displayName,
            isGroupStart = item.isGroupStart,
            groupedWithPrevious = item.groupedWithPrevious,
            groupedWithNext = item.groupedWithNext,
            isPinned = pinned.any { it.eventId == message.eventId },
            readers = receipts[message.eventId].orEmpty(),
            voicePlayback = playback?.takeIf { it.sourceId == message.eventId },
            onReaction = { viewModel.toggleReaction(message, it) },
            onTogglePin = { viewModel.togglePin(message) },
            onEdit = { editing = message; editText = message.body },
            onDelete = { deleting = message },
            onOpenAttachment = { viewModel.openAttachment(message) },
            onOpenImage = { viewModel.openAttachment(message) },
            onPlayAudio = { viewModel.playAudio(message) },
            onSeekVoice = viewModel::seekVoice,
            onVotePoll = { viewModel.vote(message, it) },
            onEndPoll = { viewModel.endPoll(message) }
        )
    }
    val listState = rememberLazyListState()
    val timeline = remember(messages) { buildTimeline(messages) }

    var previousLastId by remember(rootId) { mutableStateOf<String?>(null) }
    LaunchedEffect(messages.lastOrNull()?.eventId) {
        val last = messages.lastOrNull()
        val newOwnReply = previousLastId != null && previousLastId != last?.eventId && last?.isMine == true
        previousLastId = last?.eventId
        if (newOwnReply && timeline.isNotEmpty()) {
            listState.animateScrollToItem(timelineListIndex(timeline.lastIndex, 1))
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Ветка") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = messageInput,
                        onValueChange = viewModel::setMessageInput,
                        placeholder = { Text("Ответить в ветке...") },
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.extraLarge,
                        maxLines = 4
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = { viewModel.sendReply() },
                        enabled = messageInput.isNotBlank() && !isSending
                    ) {
                        Icon(
                            Icons.Default.Send,
                            contentDescription = "Отправить",
                            tint = if (messageInput.isNotBlank()) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp)
            ) {
                item(key = "thread_root") {
                    Column {
                        Text("Исходное сообщение", style = MaterialTheme.typography.labelSmall)
                        val rootMessage = root
                        if (rootMessage != null) {
                            renderMessage(TimelineItem.Msg(rootMessage, true, false, false))
                        } else {
                            Text(if (rootLoading) "Загрузка исходного сообщения…" else "Исходное сообщение недоступно")
                            if (!rootLoading) TextButton(onClick = viewModel::loadRoot) { Text("Повторить") }
                        }
                        Text(
                            "Загружено ответов: ${messages.size}. Более ранние ответы могут отсутствовать.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }
                items(timeline, key = { it.key }) { item ->
                    when (item) {
                        is TimelineItem.Header -> com.matrix.messenger.ui.components.DateSeparator(label = item.label)
                        is TimelineItem.Msg -> renderMessage(item)
                        is TimelineItem.UnreadDivider -> Unit
                    }
                }
            }
        }
    }
    editing?.let { message ->
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Редактировать сообщение") },
            text = { OutlinedTextField(value = editText, onValueChange = { editText = it }) },
            confirmButton = { TextButton(onClick = { viewModel.edit(message, editText); editing = null }, enabled = editText.isNotBlank()) { Text("Сохранить") } },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Отмена") } }
        )
    }
    deleting?.let { message ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Удалить сообщение?") },
            text = { Text("Сообщение будет удалено для участников комнаты, если сервер разрешит.") },
            confirmButton = { TextButton(onClick = { viewModel.delete(message); deleting = null }) { Text("Удалить") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Отмена") } }
        )
    }
}
