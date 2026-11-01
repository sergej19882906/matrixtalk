package com.matrix.messenger.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.GppMaybe
import androidx.compose.material.icons.filled.HowToVote
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PresentToAll
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import com.matrix.messenger.data.model.VerificationUiState
import com.matrix.messenger.data.repository.CallRepository
import com.matrix.messenger.data.repository.MatrixRepository
import com.matrix.messenger.media.MatrixMedia
import com.matrix.messenger.platform.ScreenSource
import com.matrix.messenger.platform.listScreenSources
import com.matrix.messenger.platform.rememberCallPermissionRequester
import com.matrix.messenger.platform.rememberFilePicker
import com.matrix.messenger.ui.components.DateSeparator
import com.matrix.messenger.ui.theme.ElementAmber
import com.matrix.messenger.ui.components.MatrixAvatar
import com.matrix.messenger.ui.components.TimeFormat
import com.matrix.messenger.ui.components.TimelineItem
import com.matrix.messenger.ui.components.buildTimeline
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/** Quick reactions shown at the top of the message context menu (Element X). */
private val QuickReactions = listOf("👍", "❤️", "😂", "😮", "😢", "🙏")

/** Compact emoji palette for the composer popup. */
private val EmojiPalette = listOf(
    "😀", "😃", "😄", "😁", "😆", "😅", "😂", "🙂",
    "😉", "😊", "😍", "🥰", "😘", "😜", "🤪", "🤗",
    "🤔", "🤨", "😐", "😴", "😢", "😭", "😤", "😡",
    "🥳", "🎉", "❤️", "🧡", "💛", "💚", "💙", "💜",
    "👍", "👎", "👏", "🙏", "💪", "🔥", "⭐", "✨",
    "🚀", "💯", "✅", "❌", "❓", "🤝", "👀", "🍀"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    roomId: String,
    onNavigateBack: () -> Unit,
    onStartCall: () -> Unit = {},
    viewModel: ChatViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val verificationState by viewModel.verificationState.collectAsState()
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    var editingMessage by remember { mutableStateOf<Message?>(null) }
    var editText by remember { mutableStateOf("") }
    var showPinned by remember { mutableStateOf(false) }
    var showGallery by remember { mutableStateOf(false) }
    var showInviteDialog by remember { mutableStateOf(false) }
    var inviteInput by remember { mutableStateOf("") }
    var showPollDialog by remember { mutableStateOf(false) }
    var pollQuestion by remember { mutableStateOf("") }
    var pollOptions by remember { mutableStateOf(listOf("", "")) }
    val coroutineScope = rememberCoroutineScope()
    val requestMicPermission = rememberCallPermissionRequester { granted ->
        if (granted) {
            viewModel.startVoiceRecording()
        } else {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Нет доступа к микрофону — запись невозможна")
            }
        }
    }
    var messageToDelete by remember { mutableStateOf<Message?>(null) }
    var roomMenuExpanded by remember { mutableStateOf(false) }
    var confirmLeaveRoom by remember { mutableStateOf(false) }
    val pickAttachment = rememberFilePicker { file ->
        viewModel.sendFile(file.path, file.mimeType)
    }

    val callRepository: CallRepository = koinInject()
    val matrixRepository: MatrixRepository = koinInject()
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

    // Date headers + sender grouping projected once per message-list change.
    val timeline = remember(uiState.messages) { buildTimeline(uiState.messages) }

    LaunchedEffect(uiState.messages.lastOrNull()?.eventId, uiState.messages.lastOrNull()?.isMine) {
        // Auto-scroll only for newly sent own messages or incoming messages while near the bottom;
        // loading earlier history must not jump to the bottom.
        val last = uiState.messages.lastOrNull() ?: return@LaunchedEffect
        if (last.isMine) {
            listState.animateScrollToItem(timeline.size - 1)
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
                        MatrixAvatar(
                            name = uiState.roomName ?: roomId,
                            avatarUrl = uiState.roomAvatarUrl,
                            size = 36.dp,
                            shape = CircleShape
                        )
                        val trust = uiState.roomTrust
                        if (trust?.isEncrypted == true) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = if (trust.verified == true) {
                                    Icons.Filled.VerifiedUser
                                } else {
                                    Icons.Filled.GppMaybe
                                },
                                contentDescription = when (trust.verified) {
                                    true -> "Все участники верифицированы"
                                    false -> "Есть неверифицированные участники"
                                    null -> "Шифрование включено"
                                },
                                tint = if (trust.verified == true) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    ElementAmber
                                },
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
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
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
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
                    Box {
                        IconButton(onClick = { roomMenuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Ещё")
                        }
                        DropdownMenu(
                            expanded = roomMenuExpanded,
                            onDismissRequest = { roomMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Вложения") },
                                leadingIcon = { Icon(Icons.Default.PhotoLibrary, contentDescription = null) },
                                onClick = {
                                    roomMenuExpanded = false
                                    showGallery = !showGallery
                                }
                            )
                            if (uiState.pinnedMessages.isNotEmpty()) {
                                DropdownMenuItem(
                                    text = { Text(if (showPinned) "Скрыть закреплённые" else "Закреплённые") },
                                    leadingIcon = { Icon(Icons.Default.PushPin, contentDescription = null) },
                                    onClick = {
                                        roomMenuExpanded = false
                                        showPinned = !showPinned
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Демонстрация экрана") },
                                leadingIcon = { Icon(Icons.Default.PresentToAll, contentDescription = null) },
                                onClick = {
                                    roomMenuExpanded = false
                                    coroutineScope.launch {
                                        val sources = listScreenSources()
                                        if (sources.isEmpty()) {
                                            snackbarHostState.showSnackbar("Демонстрация экрана недоступна на этом устройстве")
                                        } else {
                                            screenSources = sources
                                        }
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Пригласить пользователя") },
                                leadingIcon = { Icon(Icons.Default.PersonAdd, contentDescription = null) },
                                onClick = {
                                    roomMenuExpanded = false
                                    showInviteDialog = true
                                }
                            )
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
                onStartVoice = { requestMicPermission(false) },
                onStopVoice = { send -> viewModel.stopVoiceRecording(send) },
                onCreatePoll = { showPollDialog = true }
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
                                items = timeline,
                                key = { it.key },
                                contentType = { it::class }
                            ) { item ->
                                when (item) {
                                    is TimelineItem.Header -> DateSeparator(label = item.label)
                                    is TimelineItem.Msg -> MessageItem(
                                        message = item.message,
                                        ownShortId = currentUser?.shortUserId,
                                        ownDisplayName = currentUser?.displayName,
                                        isGroupStart = item.isGroupStart,
                                        groupedWithPrevious = item.groupedWithPrevious,
                                        groupedWithNext = item.groupedWithNext,
                                        onReaction = { emoji ->
                                            viewModel.sendReaction(item.message.eventId, emoji)
                                        },
                                        onAcceptVerification = {
                                            viewModel.acceptVerificationRequest(item.message.senderId)
                                        },
                                        onEdit = {
                                            editText = item.message.body
                                            editingMessage = item.message
                                        },
                                        onDelete = { messageToDelete = item.message },
                                        onOpenAttachment = { viewModel.openAttachment(item.message) },
                                        onReply = { viewModel.replyToMessage(item.message) },
                                        onPlayAudio = { viewModel.playAudioMessage(item.message) },
                                        onVotePoll = { ids -> viewModel.votePoll(item.message, ids) },
                                        isPinned = uiState.pinnedMessages.any { it.eventId == item.message.eventId },
                                        onTogglePin = { viewModel.togglePin(item.message) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showInviteDialog) {
        AlertDialog(
            onDismissRequest = { showInviteDialog = false },
            title = { Text("Пригласить пользователя") },
            text = {
                OutlinedTextField(
                    value = inviteInput,
                    onValueChange = { inviteInput = it },
                    label = { Text("User ID") },
                    placeholder = { Text("@user:matrix.org") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showInviteDialog = false
                    viewModel.inviteUser(inviteInput)
                    inviteInput = ""
                }) { Text("Пригласить") }
            },
            dismissButton = {
                TextButton(onClick = { showInviteDialog = false }) { Text("Отмена") }
            }
        )
    }

    if (showPollDialog) {
        AlertDialog(
            onDismissRequest = { showPollDialog = false },
            title = { Text("Новый опрос") },
            text = {
                Column {
                    OutlinedTextField(
                        value = pollQuestion,
                        onValueChange = { pollQuestion = it },
                        label = { Text("Вопрос") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    pollOptions.forEachIndexed { index, option ->
                        OutlinedTextField(
                            value = option,
                            onValueChange = { value ->
                                pollOptions = pollOptions.toMutableList().also { it[index] = value }
                            },
                            label = { Text("Вариант ${index + 1}") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    if (pollOptions.size < 5) {
                        TextButton(onClick = { pollOptions = pollOptions + "" }) {
                            Text("Добавить вариант")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val answers = pollOptions.map { it.trim() }.filter { it.isNotEmpty() }
                    if (pollQuestion.isBlank() || answers.size < 2) {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Нужен вопрос и минимум 2 варианта")
                        }
                    } else {
                        showPollDialog = false
                        viewModel.createPoll(pollQuestion, answers)
                        pollQuestion = ""
                        pollOptions = listOf("", "")
                    }
                }) { Text("Создать") }
            },
            dismissButton = {
                TextButton(onClick = { showPollDialog = false }) { Text("Отмена") }
            }
        )
    }

    screenSources?.let { sources ->        AlertDialog(
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

    // In-chat SAS verification (triggered by "Верифицировать" on a key verification request)
    when (val vs = verificationState) {
        is com.matrix.messenger.data.model.VerificationUiState.Waiting,
        is com.matrix.messenger.data.model.VerificationUiState.Compare -> {
            AlertDialog(
                // Outside clicks must NOT cancel: the SAS flow continues while the
                // peer decides; only the explicit buttons confirm or cancel.
                onDismissRequest = {},
                title = { Text("Верификация устройства") },
                text = {
                    if (vs is com.matrix.messenger.data.model.VerificationUiState.Compare) {
                        Column {
                            Text("Сравните эмодзи с другим устройством. Они должны совпадать:")
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = vs.emojis.joinToString("  ") { it.second },
                                style = MaterialTheme.typography.headlineSmall
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Код: " + vs.decimal.joinToString(" "),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Text("Ожидание другого устройства… Оно должно подтвердить запрос на верификацию.")
                    }
                },
                confirmButton = {
                    if (vs is com.matrix.messenger.data.model.VerificationUiState.Compare) {
                        TextButton(onClick = { viewModel.confirmVerification(true) }) {
                            Text("Совпадают")
                        }
                    }
                },
                dismissButton = {
                    Row {
                        if (vs is com.matrix.messenger.data.model.VerificationUiState.Compare) {
                            TextButton(onClick = { viewModel.confirmVerification(false) }) {
                                Text("Не совпадают", color = MaterialTheme.colorScheme.error)
                            }
                        }
                        TextButton(onClick = { viewModel.cancelVerification() }) { Text("Отмена") }
                    }
                }
            )
        }
        com.matrix.messenger.data.model.VerificationUiState.Done -> {
            AlertDialog(
                onDismissRequest = { viewModel.cancelVerification() },
                title = { Text("Устройство верифицировано") },
                text = { Text("Сессии подтверждены. Ключи зашифрованных чатов будут доступны обоим устройствам.") },
                confirmButton = {
                    TextButton(onClick = { viewModel.cancelVerification() }) { Text("Готово") }
                }
            )
        }
        else -> Unit
    }
}

@Composable
private fun MessageItem(
    message: Message,
    ownShortId: String?,
    ownDisplayName: String?,
    isGroupStart: Boolean,
    groupedWithPrevious: Boolean,
    groupedWithNext: Boolean,
    isPinned: Boolean,
    onReaction: (String) -> Unit,
    onAcceptVerification: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onOpenAttachment: () -> Unit,
    onReply: () -> Unit,
    onTogglePin: () -> Unit,
    onPlayAudio: () -> Unit,
    onVotePoll: (List<String>) -> Unit
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
                if (isGroupStart) {
                    MatrixAvatar(
                        name = message.senderName ?: message.senderId,
                        avatarUrl = message.senderAvatarUrl,
                        size = 36.dp,
                        shape = CircleShape
                    )
                } else {
                    Spacer(modifier = Modifier.width(36.dp))
                }
            }
            Column {
                if (!message.isMine && isGroupStart) {
                    Text(
                        text = message.senderName?.substringBefore(":") ?: "Unknown",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Box {
                    Surface(
                        shape = RoundedCornerShape(
                            topStart = if (!message.isMine && groupedWithPrevious) 4.dp else 16.dp,
                            topEnd = if (message.isMine && groupedWithPrevious) 4.dp else 16.dp,
                            bottomStart = if (message.isMine && groupedWithNext) 4.dp else 16.dp,
                            bottomEnd = if (!message.isMine && groupedWithNext) 4.dp else 16.dp
                        ),
                        color = if (message.isMine) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        },
                        modifier = Modifier.clickable { menuExpanded = true }
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
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
                                val poll = message.poll
                                when {
                                    poll != null -> PollContent(
                                        message = message,
                                        onVote = onVotePoll
                                    )

                                    message.messageType == MessageType.VERIFICATION_REQUEST -> {
                                        Column {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    Icons.Default.VerifiedUser,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "Запрос верификации ключа",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                            if (!message.isMine) {
                                                if (message.verificationDeviceId != null) {
                                                    Text(
                                                        text = "Устройство ${message.verificationDeviceId}",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                Spacer(modifier = Modifier.height(6.dp))
                                                Button(onClick = onAcceptVerification) {
                                                    Text("Верифицировать")
                                                }
                                            } else {
                                                Text(
                                                    text = "Запрос отправлен",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }

                                    message.messageType == MessageType.CALL -> {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Default.Call,
                                                contentDescription = null,
                                                modifier = Modifier.size(20.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = message.body,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }

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
                                    text = TimeFormat.timeOnly(message.timestamp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (message.isEdited) {
                                    Text(
                                        text = "(ред.)",
                                        style = MaterialTheme.typography.labelSmall,
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
                                            shape = MaterialTheme.shapes.extraLarge,
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
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            QuickReactions.forEach { emoji ->
                                Text(
                                    text = emoji,
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier
                                        .clip(MaterialTheme.shapes.small)
                                        .clickable {
                                            menuExpanded = false
                                            onReaction(emoji)
                                        }
                                        .padding(4.dp)
                                )
                            }
                        }
                        HorizontalDivider()
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
                                leadingIcon = { Icon(Icons.Default.PushPin, contentDescription = null) },
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
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
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
                Spacer(modifier = Modifier.width(36.dp))
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

    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
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
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
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
    onStopVoice: (send: Boolean) -> Unit,
    onCreatePoll: () -> Unit
) {
    var emojiVisible by remember { mutableStateOf(false) }
    var attachMenuVisible by remember { mutableStateOf(false) }
    val hasText = value.isNotBlank()

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
            if (replyingTo != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest
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
                if (isRecordingVoice) {
                    OutlinedTextField(
                        value = "● Голосовое ${recordingSeconds / 60}:${(recordingSeconds % 60).toString().padStart(2, '0')}",
                        onValueChange = { },
                        readOnly = true,
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.extraLarge,
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.width(8.dp))
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
                } else {
                    Box {
                        IconButton(onClick = { emojiVisible = !emojiVisible }) {
                            Icon(
                                Icons.Default.EmojiEmotions,
                                contentDescription = "Эмодзи",
                                tint = if (emojiVisible) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        EmojiPickerMenu(
                            expanded = emojiVisible,
                            onDismiss = { emojiVisible = false },
                            onPick = { emoji ->
                                onValueChange(value + emoji)
                            }
                        )
                    }
                    OutlinedTextField(
                        value = value,
                        onValueChange = onValueChange,
                        placeholder = { Text("Сообщение...") },
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.extraLarge,
                        maxLines = 4
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box {
                        IconButton(onClick = { attachMenuVisible = !attachMenuVisible }) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = "Прикрепить",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        DropdownMenu(
                            expanded = attachMenuVisible,
                            onDismissRequest = { attachMenuVisible = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Файл") },
                                leadingIcon = { Icon(Icons.Default.AttachFile, contentDescription = null) },
                                onClick = {
                                    attachMenuVisible = false
                                    onFileSelected()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Опрос") },
                                leadingIcon = { Icon(Icons.Default.HowToVote, contentDescription = null) },
                                onClick = {
                                    attachMenuVisible = false
                                    onCreatePoll()
                                }
                            )
                        }
                    }
                    if (hasText) {
                        // Element X: the mic becomes a filled circular send button once there is text
                        Box(
                            modifier = Modifier
                                .padding(start = 4.dp)
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                                .clickable(onClick = onSend),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Send,
                                contentDescription = "Отправить",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    } else {
                        IconButton(
                            onClick = onStartVoice,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Default.Mic, contentDescription = "Голосовое сообщение")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmojiPickerMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(8),
            modifier = Modifier.width(288.dp).height(240.dp).padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            gridItems(
                items = EmojiPalette,
                key = { it }
            ) { emoji ->
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(MaterialTheme.shapes.small)
                        .clickable {
                            onPick(emoji)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = emoji, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}
