package com.matrix.messenger.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.matrix.messenger.data.model.Message
import com.matrix.messenger.data.model.UiEvent
import com.matrix.messenger.platform.pickFile
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    roomId: String,
    onNavigateBack: () -> Unit,
    viewModel: ChatViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    var editingMessage by remember { mutableStateOf<Message?>(null) }
    var editText by remember { mutableStateOf("") }

    LaunchedEffect(roomId) {
        viewModel.setRoomId(roomId)
    }

    LaunchedEffect(uiState.messages.lastOrNull()?.eventId, uiState.messages.lastOrNull()?.isMine) {
        // Auto-scroll only for newly sent own messages or incoming messages while near the bottom;
        // loading earlier history must not jump to the bottom.
        val last = uiState.messages.lastOrNull() ?: return@LaunchedEffect
        if (last.isMine) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is UiEvent.NavigateBack -> onNavigateBack()
                is UiEvent.ShowSnackbar -> snackbarHostState.showSnackbar(event.message)
                else -> {}
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = uiState.roomName ?: "Чат",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = { /* TODO: Start audio call via platform */ }) {
                        Icon(Icons.Default.Call, contentDescription = "Аудио звонок")
                    }
                    IconButton(onClick = { /* TODO: Start video call via platform */ }) {
                        Icon(Icons.Default.Videocam, contentDescription = "Видео звонок")
                    }
                    IconButton(onClick = { viewModel.leaveRoom() }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Ещё")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            MessageInputBar(
                value = uiState.messageInput,
                onValueChange = viewModel::onMessageInputChange,
                onSend = viewModel::sendMessage,
                onFileSelected = {
                    val result = pickFile()
                    result?.let { viewModel.sendFile(it.path, it.mimeType) }
                },
                replyingTo = uiState.replyingTo,
                onCancelReply = viewModel::cancelReply
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                uiState.isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (uiState.canLoadMore) {
                            item(key = "load_earlier") {
                                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                    if (uiState.isLoadingMore) {
                                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                    } else {
                                        TextButton(onClick = { viewModel.loadEarlierMessages() }) {
                                            Text("Загрузить ранее")
                                        }
                                    }
                                }
                            }
                        }
                        items(
                            items = uiState.messages,
                            key = { it.eventId }
                        ) { message ->
                            MessageItem(
                                message = message,
                                onReaction = { viewModel.sendReaction(message.eventId, "👍") },
                                onEdit = {
                                    editText = message.body
                                    editingMessage = message
                                },
                                onDelete = { viewModel.deleteMessage(message.eventId) }
                            )
                        }
                    }
                }
            }
        }
    }

    editingMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { editingMessage = null },
            title = { Text("Редактировать сообщение") },
            text = {
                OutlinedTextField(
                    value = editText,
                    onValueChange = { editText = it },
                    singleLine = false,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.editMessage(message.eventId, editText)
                        editingMessage = null
                    },
                    enabled = editText.isNotBlank()
                ) {
                    Text("Сохранить")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingMessage = null }) {
                    Text("Отмена")
                }
            }
        )
    }
}

@Composable
private fun MessageItem(
    message: Message,
    onReaction: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (message.isMine) Alignment.End else Alignment.Start
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!message.isMine) {
                Surface(
                    modifier = Modifier.size(32.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = (message.senderName?.take(1)?.uppercase() ?: "?"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
            Column {
                if (!message.isMine) {
                    Text(
                        text = message.senderName?.substringBefore(":") ?: "Unknown",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Box {
                    Surface(
                        shape = RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = if (message.isMine) 16.dp else 4.dp,
                            bottomEnd = if (message.isMine) 4.dp else 16.dp
                        ),
                        color = if (message.isMine) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            if (message.isDeleted) {
                                Text(
                                    text = "Сообщение удалено",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                Text(
                                    text = message.body,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = formatTime(message.timestamp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (message.isEdited) {
                                    Text(
                                        text = "(ред.)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (message.reactions.isNotEmpty()) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.padding(top = 4.dp)
                                ) {
                                    message.reactions.forEach { (emoji, count) ->
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.surface
                                        ) {
                                            Text(
                                                text = "$emoji $count",
                                                style = MaterialTheme.typography.bodySmall,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.align(Alignment.TopEnd).size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "Действия с сообщением",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("👍 Реакция") },
                            onClick = {
                                menuExpanded = false
                                onReaction()
                            }
                        )
                        if (message.isMine && !message.isDeleted) {
                            DropdownMenuItem(
                                text = { Text("Редактировать") },
                                onClick = {
                                    menuExpanded = false
                                    onEdit()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Удалить") },
                                onClick = {
                                    menuExpanded = false
                                    onDelete()
                                }
                            )
                        }
                    }
                }
            }
            if (message.isMine) {
                Spacer(modifier = Modifier.width(32.dp))
            }
        }
    }
}

@Composable
private fun MessageInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onFileSelected: () -> Unit,
    replyingTo: Message?,
    onCancelReply: () -> Unit
) {
    Surface(
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
            if (replyingTo != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Ответ: ${replyingTo.senderName?.substringBefore(":")}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = replyingTo.body,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = onCancelReply) {
                            Icon(Icons.Default.Close, contentDescription = "Отмена")
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom
            ) {
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    placeholder = { Text("Сообщение...") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    maxLines = 4
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = onFileSelected,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.AttachFile, contentDescription = "Прикрепить файл")
                }
                IconButton(
                    onClick = onSend,
                    enabled = value.isNotBlank(),
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Отправить",
                        tint = if (value.isNotBlank()) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }
    }
}

private fun formatTime(timestamp: Long): String {
    val totalSeconds = timestamp / 1000
    val hours = ((totalSeconds % 86400) / 3600).toInt()
    val minutes = ((totalSeconds % 3600) / 60).toInt()
    return hours.toString().padStart(2, '0') + ":" + minutes.toString().padStart(2, '0')
}
