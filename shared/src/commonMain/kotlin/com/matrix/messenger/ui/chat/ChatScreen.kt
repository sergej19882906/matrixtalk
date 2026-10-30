package com.matrix.messenger.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PresentToAll
import androidx.compose.material.icons.filled.PushPin
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.matrix.messenger.data.model.Message
import com.matrix.messenger.data.model.MessageType
import com.matrix.messenger.data.model.UiEvent
import com.matrix.messenger.data.repository.CallRepository
import com.matrix.messenger.data.repository.MatrixRepository
import com.matrix.messenger.media.MatrixMedia
import com.matrix.messenger.platform.ScreenSource
import com.matrix.messenger.platform.listScreenSources
import com.matrix.messenger.platform.rememberCallPermissionRequester
import com.matrix.messenger.platform.rememberFilePicker
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    roomId: String,
    onNavigateBack: () -> Unit,
    onStartCall: () -> Unit = {},
    viewModel: ChatViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    var editingMessage by remember { mutableStateOf<Message?>(null) }
    var editText by remember { mutableStateOf("") }
    var showPinned by remember { mutableStateOf(false) }
    var showGallery by remember { mutableStateOf(false) }
    var messageToDelete by remember { mutableStateOf<Message?>(null) }
    var roomMenuExpanded by remember { mutableStateOf(false) }
    var confirmLeaveRoom by remember { mutableStateOf(false) }
    val pickAttachment = rememberFilePicker { file ->
        viewModel.sendFile(file.path, file.mimeType)
    }

    val callRepository: CallRepository = koinInject()
    val matrixRepository: MatrixRepository = koinInject()
    val coroutineScope = rememberCoroutineScope()
    val currentUser by matrixRepository.currentUser.collectAsState(initial = null)
    var pendingVideoCall by remember { mutableStateOf(false) }
    var pendingScreenShareId by remember { mutableStateOf<Long?>(null) }
    var screenSources by remember { mutableStateOf<List<ScreenSource>?>(null) }
    val requestCallPermissions = rememberCallPermissionRequester { granted ->
        if (!granted) {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Нет доступа к микрофону — звонок невозможен")
            }
        } else {
            coroutineScope.launch {
                runCatching {
                    callRepository.startCall(
                        roomId = roomId,
                        peerUserId = matrixRepository.resolveDirectChatPeerId(roomId) ?: "",
                        peerName = uiState.roomName ?: "Собеседник",
                        isVideo = pendingVideoCall,
                        screenShareSourceId = pendingScreenShareId
                    )
                }.onSuccess { onStartCall() }
                    .onFailure {
                        snackbarHostState.showSnackbar("Не удалось начать звонок: ${it.message}")
                    }
            }
        }
    }

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
                    Column {
                        Text(
                            text = uiState.roomName ?: "Чат",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val typingUsers = uiState.typingUsers
                        if (typingUsers.isNotEmpty()) {
                            Text(
                                text = when (typingUsers.size) {
                                    1 -> "печатает…"
                                    else -> "печатают…"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    if (uiState.pinnedMessages.isNotEmpty()) {
                        IconButton(onClick = { showPinned = !showPinned }) {
                            Icon(
                                Icons.Default.PushPin,
                                contentDescription = "Закреплённые",
                                tint = if (showPinned) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    IconButton(onClick = { showGallery = !showGallery }) {
                        Icon(
                            Icons.Default.PhotoLibrary,
                            contentDescription = "Вложения",
                            tint = if (showGallery) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = {
                        pendingVideoCall = false
                        pendingScreenShareId = null
                        requestCallPermissions(false)
                    }) {
                        Icon(Icons.Default.Call, contentDescription = "Аудио звонок")
                    }
                    IconButton(onClick = {
                        pendingVideoCall = true
                        pendingScreenShareId = null
                        requestCallPermissions(true)
                    }) {
                        Icon(Icons.Default.Videocam, contentDescription = "Видео звонок")
                    }
                    IconButton(onClick = {
                        coroutineScope.launch {
                            val sources = listScreenSources()
                            if (sources.isEmpty()) {
                                snackbarHostState.showSnackbar("Демонстрация экрана недоступна на этом устройстве")
                            } else {
                                screenSources = sources
                            }
                        }
                    }) {
                        Icon(Icons.Default.PresentToAll, contentDescription = "Демонстрация экрана")
                    }
                    Box {
                        IconButton(onClick = { roomMenuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Ещё")
                        }
                        DropdownMenu(
                            expanded = roomMenuExpanded,
                            onDismissRequest = { roomMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Выйти и удалить чат") },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                                onClick = {
                                    roomMenuExpanded = false
                                    confirmLeaveRoom = true
                                }
                            )
                        }
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
                onFileSelected = pickAttachment,
                replyingTo = uiState.replyingTo,
                onCancelReply = viewModel::cancelReply,
                isRecordingVoice = uiState.isRecordingVoice,
                recordingSeconds = uiState.recordingSeconds,
                onStartVoice = viewModel::startVoiceRecording,
                onStopVoice = { send -> viewModel.stopVoiceRecording(send) }
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
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (showPinned && uiState.pinnedMessages.isNotEmpty()) {
                            PinnedBanner(
                                pinned = uiState.pinnedMessages,
                                onClose = { showPinned = false }
                            )
                        }
                        if (showGallery) {
                            GalleryPanel(
                                messages = uiState.messages,
                                onOpen = { viewModel.openAttachment(it) },
                                onClose = { showGallery = false }
                            )
                        }
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
                                ownShortId = currentUser?.shortUserId,
                                ownDisplayName = currentUser?.displayName,
                                onReaction = { viewModel.sendReaction(message.eventId, "👍") },
                                onEdit = {
                                    editText = message.body
                                    editingMessage = message
                                },
                                onDelete = { messageToDelete = message },
                                onOpenAttachment = { viewModel.openAttachment(message) },
                                onReply = { viewModel.replyToMessage(message) },
                                onPlayAudio = { viewModel.playAudioMessage(message) },
                                isPinned = uiState.pinnedMessages.any { it.eventId == message.eventId },
                                onTogglePin = { viewModel.togglePin(message) }
                            )
                        }
                    }
                    }
                }
            }
        }
    }

    screenSources?.let { sources ->
        AlertDialog(
            onDismissRequest = { screenSources = null },
            title = { Text("Что показать?") },
            text = {
                Column {
                    sources.forEach { source ->
                        TextButton(
                            onClick = {
                                screenSources = null
                                pendingVideoCall = true
                                pendingScreenShareId = source.id
                                requestCallPermissions(true)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(source.title.ifBlank { "Экран ${source.id}" }, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { screenSources = null }) { Text("Отмена") }
            }
        )
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

    messageToDelete?.let { message ->
        AlertDialog(
            onDismissRequest = { messageToDelete = null },
            title = { Text("Удалить сообщение?") },
            text = { Text("Это удалит сообщение для участников комнаты, если сервер разрешает удаление.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteMessage(message.eventId)
                        messageToDelete = null
                    }
                ) {
                    Text("Удалить")
                }
            },
            dismissButton = {
                TextButton(onClick = { messageToDelete = null }) {
                    Text("Отмена")
                }
            }
        )
    }

    if (confirmLeaveRoom) {
        AlertDialog(
            onDismissRequest = { confirmLeaveRoom = false },
            title = { Text("Удалить чат?") },
            text = { Text("Вы выйдете из комнаты. Она исчезнет из списка чатов.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmLeaveRoom = false
                        viewModel.leaveRoom()
                    }
                ) {
                    Text("Выйти и удалить")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmLeaveRoom = false }) {
                    Text("Отмена")
                }
            }
        )
    }
}

@Composable
private fun MessageItem(
    message: Message,
    ownShortId: String?,
    ownDisplayName: String?,
    isPinned: Boolean,
    onReaction: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onOpenAttachment: () -> Unit,
    onReply: () -> Unit,
    onTogglePin: () -> Unit,
    onPlayAudio: () -> Unit
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
                            val replyTo = message.replyTo
                            if (replyTo != null && !message.isDeleted) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                                    modifier = Modifier.padding(bottom = 6.dp)
                                ) {
                                    Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                                        Text(
                                            text = replyTo.senderName?.substringBefore(":") ?: "Unknown",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = replyTo.body,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                            if (message.isDeleted) {
                                Text(
                                    text = "Сообщение удалено",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                when {
                                    message.messageType == MessageType.IMAGE &&
                                        (message.mediaUrl != null || message.encryptedFile != null) -> {
                                        AsyncImage(
                                            model = MatrixMedia(message.mediaUrl, message.encryptedFile),
                                            contentDescription = message.body,
                                            modifier = Modifier
                                                .widthIn(max = 280.dp)
                                                .clip(RoundedCornerShape(8.dp)),
                                            contentScale = ContentScale.Fit
                                        )
                                    }

                                    message.mediaUrl != null || message.encryptedFile != null -> {
                                        if (message.messageType == MessageType.AUDIO) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.clickable { onPlayAudio() }
                                            ) {
                                                Icon(
                                                    Icons.Default.PlayArrow,
                                                    contentDescription = "Воспроизвести",
                                                    modifier = Modifier.size(24.dp),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "Голосовое сообщение",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        } else {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.clickable { onOpenAttachment() }
                                            ) {
                                                Icon(
                                                    Icons.Default.AttachFile,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(20.dp),
                                                    tint = MaterialTheme.colorScheme.onSurface
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = message.body,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                    }

                                    else -> {
                                        val formatted = message.formattedBody
                                        if (formatted != null) {
                                            RichMessageText(html = formatted)
                                        } else {
                                            Text(
                                                text = buildMentionAnnotatedString(
                                                    body = message.body,
                                                    ownShortId = ownShortId,
                                                    ownDisplayName = ownDisplayName,
                                                    mentionColor = MaterialTheme.colorScheme.primary
                                                ),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
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
                        if (!message.isDeleted) {
                            DropdownMenuItem(
                                text = { Text("Ответить") },
                                onClick = {
                                    menuExpanded = false
                                    onReply()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(if (isPinned) "Открепить" else "Закрепить") },
                                onClick = {
                                    menuExpanded = false
                                    onTogglePin()
                                }
                            )
                        }
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
private fun GalleryPanel(
    messages: List<Message>,
    onOpen: (Message) -> Unit,
    onClose: () -> Unit
) {
    val media = messages.filter {
        it.messageType == MessageType.IMAGE && !it.isDeleted && (it.mediaUrl != null || it.encryptedFile != null)
    }.asReversed()
    val files = messages.filter {
        it.messageType in setOf(MessageType.FILE, MessageType.VIDEO, MessageType.AUDIO) &&
            !it.isDeleted && (it.mediaUrl != null || it.encryptedFile != null)
    }.asReversed()

    Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Вложения",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onClose, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Закрыть", modifier = Modifier.size(16.dp))
                }
            }
            if (media.isEmpty() && files.isEmpty()) {
                Text(
                    text = "В загруженной истории нет вложений",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (media.isNotEmpty()) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier.fillMaxWidth().height(180.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    gridItems(
                        items = media,
                        key = { "img_${it.eventId}" }
                    ) { msg ->
                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { onOpen(msg) }
                        ) {
                            AsyncImage(
                                model = MatrixMedia(msg.mediaUrl, msg.encryptedFile),
                                contentDescription = msg.body,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }
            }
            files.take(5).forEach { msg ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { onOpen(msg) }.padding(vertical = 4.dp)
                ) {
                    Icon(
                        Icons.Default.AttachFile,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = msg.body,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun PinnedBanner(
    pinned: List<Message>,
    onClose: () -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.PushPin,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Закреплённые: ${pinned.size}",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onClose, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Закрыть", modifier = Modifier.size(16.dp))
                }
            }
            pinned.take(3).forEach { msg ->
                Text(
                    text = "${msg.senderName?.substringBefore(":") ?: "?"}: ${msg.body}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
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
    onCancelReply: () -> Unit,
    isRecordingVoice: Boolean,
    recordingSeconds: Int,
    onStartVoice: () -> Unit,
    onStopVoice: (send: Boolean) -> Unit
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
                if (isRecordingVoice) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "● ${recordingSeconds / 60}:${(recordingSeconds % 60).toString().padStart(2, '0')}",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        IconButton(onClick = { onStopVoice(false) }) {
                            Icon(Icons.Default.Close, contentDescription = "Отменить запись")
                        }
                        IconButton(onClick = { onStopVoice(true) }) {
                            Icon(
                                Icons.Default.Send,
                                contentDescription = "Отправить голосовое",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                } else {
                    IconButton(
                        onClick = onStartVoice,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.Mic, contentDescription = "Голосовое сообщение")
                    }
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
