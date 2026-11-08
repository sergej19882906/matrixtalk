package com.matrix.messenger.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Forward
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.GppMaybe
import androidx.compose.material.icons.filled.HowToVote
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TextFormat
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PresentToAll
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.ShareLocation
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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.font.FontWeight
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.matrix.messenger.data.model.ChatRoom
import com.matrix.messenger.data.model.GeoLocation
import com.matrix.messenger.data.model.GeoUri
import com.matrix.messenger.data.model.LiveLocationBeacon
import com.matrix.messenger.data.model.ReadReceipt
import com.matrix.messenger.data.model.Message
import com.matrix.messenger.data.model.MessageType
import com.matrix.messenger.data.model.StickerItem
import com.matrix.messenger.data.model.UiEvent
import com.matrix.messenger.data.model.VerificationUiState
import com.matrix.messenger.data.repository.CallRepository
import com.matrix.messenger.data.repository.MatrixRepository
import com.matrix.messenger.media.MatrixMedia
import com.matrix.messenger.platform.ScreenSource
import com.matrix.messenger.platform.listScreenSources
import com.matrix.messenger.platform.rememberCallPermissionRequester
import com.matrix.messenger.platform.openUrl
import com.matrix.messenger.platform.rememberLocationRequester
import com.matrix.messenger.platform.rememberVideoRecorder
import com.matrix.messenger.platform.rememberFilePicker
import com.matrix.messenger.ui.components.DateSeparator
import com.matrix.messenger.ui.theme.ElementAmber
import com.matrix.messenger.ui.components.MatrixAvatar
import com.matrix.messenger.ui.components.TimeFormat
import com.matrix.messenger.ui.components.ImageViewer
import com.matrix.messenger.ui.components.ReactionPicker
import com.matrix.messenger.ui.components.TimelineItem
import com.matrix.messenger.ui.components.buildTimeline
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

internal fun unreadMessageIndex(messages: List<Message>, eventId: String?): Int =
    if (eventId == null) -1 else messages.indexOfFirst { it.eventId == eventId }

internal fun timelineListIndex(timelineIndex: Int, leadingRows: Int): Int =
    if (timelineIndex < 0) -1 else timelineIndex + leadingRows

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
    onOpenRoomSettings: () -> Unit = {},
    onStartGroupCall: () -> Unit = {},
    onOpenThread: (String) -> Unit = {},
    viewModel: ChatViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val verificationState by viewModel.verificationState.collectAsState()
    val recentReactions by viewModel.recentReactions.collectAsState()
    val readReceipts by viewModel.readReceipts.collectAsState()
    val voicePlayback by viewModel.voicePlayback.collectAsState()
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

    val pickSticker = rememberFilePicker(listOf("image/*")) { file ->
        viewModel.sendSticker(file.path, file.mimeType)
    }
    var showStickerSheet by remember { mutableStateOf(false) }
    var showAddPackDialog by remember { mutableStateOf(false) }
    var addPackInput by remember { mutableStateOf("") }
    var showManagePacksDialog by remember { mutableStateOf(false) }
    var reportingMessage by remember { mutableStateOf<Message?>(null) }
    var reportReason by remember { mutableStateOf("") }
    var showForwardDialog by remember { mutableStateOf(false) }
    /** Message being forwarded directly from its context menu (Element «Переслать»). */
    var singleForwardMessage by remember { mutableStateOf<Message?>(null) }
    var confirmDeleteSelected by remember { mutableStateOf(false) }
    var showLiveLocationDialog by remember { mutableStateOf(false) }
    // MSC3488: beacons still broadcasting in this room, latest per sender.
    val activeBeacons = viewModel.activeLocationBeacons()
    var showActiveBeaconsDialog by remember { mutableStateOf(false) }
    val chatListPreview by viewModel.chatListPreview.collectAsState()
    val selectionMode = uiState.selectedEventIds.isNotEmpty()
    var locationDraft by remember { mutableStateOf<GeoLocation?>(null) }
    var showLocationDialog by remember { mutableStateOf(false) }
    val requestLocation = rememberLocationRequester { point ->
        locationDraft = point
        showLocationDialog = true
    }
    val requestLiveLocation = rememberLocationRequester { point ->
        if (point == null) {
            coroutineScope.launch {
                snackbarHostState.showSnackbar(
                    if (com.matrix.messenger.platform.hasLocationProvider()) {
                        "Не удалось определить местоположение"
                    } else {
                        "Live-геолокация недоступна на этом устройстве"
                    }
                )
            }
        } else {
            showLiveLocationDialog = true
        }
    }
    var videoDraft by remember(roomId) { mutableStateOf<String?>(null) }
    val captureVideo = rememberVideoRecorder(onError = { error ->
        coroutineScope.launch { snackbarHostState.showSnackbar(error) }
    }) { path ->
        videoDraft = path
    }
    videoDraft?.let { path ->
        AlertDialog(
            onDismissRequest = { videoDraft = null },
            title = { Text("Отправить видео?") },
            text = {
                Column {
                    Text("Видео записано. Просмотрите его перед отправкой.")
                    TextButton(onClick = { com.matrix.messenger.platform.openLocalFile(path) }) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Text("Просмотреть")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    videoDraft = null
                    viewModel.sendVideoMessage(path)
                }) { Text("Отправить") }
            },
            dismissButton = { TextButton(onClick = { videoDraft = null }) { Text("Отмена") } }
        )
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

    // Thread summary: reply counts per root message (Element X chip).
    val threadReplyCounts = remember(uiState.messages) {
        uiState.messages
            .filter { it.threadRootId != null && !it.isDeleted }
            .groupBy { it.threadRootId!! }
            .mapValues { (_, replies) -> replies.size }
    }

    // In-chat search state.
    var chatSearchActive by remember(roomId) { mutableStateOf(false) }
    var chatSearchQuery by remember(roomId) { mutableStateOf("") }

    // Date headers + sender grouping projected once per message-list change.
    // The unread divider is inserted before the first unread message snapshot.
    val firstUnreadMessageIndex = if (!chatSearchActive) {
        unreadMessageIndex(uiState.messages, uiState.initialUnreadEventId)
    } else -1
    val searchQuery = chatSearchQuery.trim()
    val searchMatches = remember(uiState.messages, searchQuery) {
        if (searchQuery.isEmpty()) null
        else uiState.messages.count {
            !it.isDeleted && it.body.contains(searchQuery, ignoreCase = true)
        }
    }
    val searchFilteredMessages = if (searchQuery.isEmpty()) {
        uiState.messages
    } else {
        uiState.messages.filter {
            !it.isDeleted && it.body.contains(searchQuery, ignoreCase = true)
        }
    }
    val timeline = remember(searchFilteredMessages, firstUnreadMessageIndex) {
        buildTimeline(searchFilteredMessages, firstUnreadMessageIndex)
    }
    val historyRowCount = if (!chatSearchActive && uiState.canLoadMore) 1 else 0
    var scrolledToUnread by remember(roomId) { mutableStateOf(false) }
    LaunchedEffect(timeline.size, uiState.initialUnreadEventId, chatSearchActive, historyRowCount) {
        if (!chatSearchActive && !scrolledToUnread && firstUnreadMessageIndex >= 0) {
            val dividerIndex = timeline.indexOfFirst { it is TimelineItem.UnreadDivider }
            if (dividerIndex >= 0) {
                listState.scrollToItem(timelineListIndex(dividerIndex, historyRowCount))
                scrolledToUnread = true
            }
        }
    }

    var previousLastEventId by remember(roomId) { mutableStateOf<String?>(null) }
    LaunchedEffect(uiState.messages.lastOrNull()?.eventId, chatSearchActive) {
        val last = uiState.messages.lastOrNull() ?: return@LaunchedEffect
        val isNew = previousLastEventId != null && previousLastEventId != last.eventId
        previousLastEventId = last.eventId
        if (!chatSearchActive && isNew && last.isMine && timeline.isNotEmpty()) {
            listState.animateScrollToItem(timelineListIndex(timeline.lastIndex, historyRowCount))
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

    val viewerMessageId = uiState.imageViewerMessageId
    if (viewerMessageId != null) {
        val viewerImages = uiState.messages.filter {
            it.messageType == MessageType.IMAGE && !it.isDeleted && (it.mediaUrl != null || it.encryptedFile != null)
        }.asReversed()
        if (viewerImages.isNotEmpty()) {
            ImageViewer(
                images = viewerImages,
                startMessageId = viewerMessageId,
                onDismiss = { viewModel.closeImageViewer() },
                onDownload = { msg -> viewModel.openAttachment(msg) }
            )
        }
    }

    if (showLocationDialog) {
        LocationSendDialog(
            initial = locationDraft,
            onDismiss = { showLocationDialog = false },
            onSend = { lat, lon ->
                showLocationDialog = false
                viewModel.sendLocation(lat, lon)
            }
        )
    }

    Scaffold(
        topBar = {
            if (selectionMode) {
                SelectionModeBar(
                    selectedCount = uiState.selectedEventIds.size,
                    onCancel = viewModel::clearSelection,
                    onForward = {
                        showForwardDialog = true
                        viewModel.loadChatListPreview()
                    },
                    onDelete = { confirmDeleteSelected = true }
                )
            } else {
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
                            } else {
                                val presence = uiState.peerPresence
                                if (presence != null) Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .background(
                                                color = if (presence.presence == "online") {
                                                    MaterialTheme.colorScheme.primary
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                },
                                                shape = CircleShape
                                            )
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = buildString {
                                            append(
                                                when {
                                                    presence.presence == "online" -> "в сети"
                                                    presence.lastActiveAgoMinutes != null && presence.lastActiveAgoMinutes < 1 ->
                                                        "был(а) только что"
                                                    presence.lastActiveAgoMinutes != null && presence.lastActiveAgoMinutes < 60 ->
                                                        "был(а) ${presence.lastActiveAgoMinutes} мин назад"
                                                    presence.lastActiveAgoMinutes != null && presence.lastActiveAgoMinutes < 1440 ->
                                                        "был(а) ${presence.lastActiveAgoMinutes / 60} ч назад"
                                                    else -> "не в сети"
                                                }
                                            )
                                            presence.statusMessage?.takeIf { it.isNotBlank() }?.let {
                                                append(" · ")
                                                append(it)
                                            }
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
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
                                text = { Text("Групповой звонок (Element Call)") },
                                leadingIcon = { Icon(Icons.Default.Groups, contentDescription = null) },
                                onClick = {
                                    roomMenuExpanded = false
                                    onStartGroupCall()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Поиск в чате") },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                onClick = {
                                    roomMenuExpanded = false
                                    chatSearchActive = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Настройки комнаты") },
                                leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                onClick = {
                                    roomMenuExpanded = false
                                    onOpenRoomSettings()
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
            }
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
                onCreatePoll = { showPollDialog = true },
                markdownEnabled = uiState.markdownEnabled,
                onToggleMarkdown = { viewModel.toggleMarkdown() },
                onPickSticker = {
                    showStickerSheet = true
                    viewModel.loadStickerPacks()
                },
                onPickLocation = { requestLocation() },
                onPickLiveLocation = { requestLiveLocation() },
                onPickVideo = { captureVideo() },
                activeBeaconCount = activeBeacons.size,
                onShowActiveBeacons = { showActiveBeaconsDialog = true }
            )
        }
    ) { paddingValues ->
        Column(Modifier.fillMaxSize().padding(paddingValues)) {
        if (chatSearchActive) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = chatSearchQuery,
                    onValueChange = { chatSearchQuery = it },
                    placeholder = { Text("Поиск в чате") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.extraLarge,
                    modifier = Modifier.weight(1f)
                )
                if (searchMatches != null) {
                    Text(
                        text = "$searchMatches",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
                IconButton(onClick = {
                    chatSearchActive = false
                    chatSearchQuery = ""
                }) {
                    Icon(Icons.Default.Close, contentDescription = "Закрыть поиск")
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
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
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (!chatSearchActive && uiState.canLoadMore) {
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
                                    is TimelineItem.UnreadDivider -> UnreadDividerRow()
                                    is TimelineItem.Msg -> MessageItem(
                                        message = item.message,
                                        ownShortId = currentUser?.shortUserId,
                                        ownDisplayName = currentUser?.displayName,
                                        isGroupStart = item.isGroupStart,
                                        groupedWithPrevious = item.groupedWithPrevious,
                                        groupedWithNext = item.groupedWithNext,
                                        onReaction = { emoji ->
                                            viewModel.toggleReaction(item.message, emoji)
                                        },
                                        recentReactions = recentReactions,
                                        customEmotes = uiState.reactionEmotes,
                                        readers = readReceipts[item.message.eventId].orEmpty(),
                                        threadReplyCount = threadReplyCounts[item.message.eventId] ?: 0,
                                        voicePlayback = voicePlayback.takeIf { it?.sourceId == item.message.eventId },
                                        onSeekVoice = { fraction -> viewModel.seekVoice(fraction) },
                                        onEdit = {
                                            editText = item.message.body
                                            editingMessage = item.message
                                        },
                                        onDelete = { messageToDelete = item.message },
                                        onOpenAttachment = { viewModel.openAttachment(item.message) },
                                        onOpenThread = {
                                            onOpenThread(item.message.threadRootId ?: item.message.eventId)
                                        },
                                        onOpenImage = { viewModel.openImageViewer(item.message) },
                                        onReply = { viewModel.replyToMessage(item.message) },
                                        onPlayAudio = { viewModel.playAudioMessage(item.message) },
                                        onVotePoll = { ids -> viewModel.votePoll(item.message, ids) },
                                        onEndPoll = { viewModel.endPoll(item.message.eventId) },
                                        isPinned = uiState.pinnedMessages.any { it.eventId == item.message.eventId },
                                        onTogglePin = { viewModel.togglePin(item.message) },
                                        onStopLiveLocation = { viewModel.stopLiveLocationShare() },
                                        onReport = { reportingMessage = item.message },
                                        isSelected = item.message.eventId in uiState.selectedEventIds,
                                        selectionMode = selectionMode,
                                        onSelect = { viewModel.toggleSelection(item.message.eventId) },
                                        onForward = {
                                            singleForwardMessage = item.message
                                            viewModel.loadChatListPreview()
                                        }
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

    // MSC2545 sticker pack picker (Element X style sheet).
    if (showStickerSheet) {
        ModalBottomSheet(onDismissRequest = { showStickerSheet = false }) {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                if (uiState.stickerPacks.isNotEmpty()) {
                    var selectedPackIndex by remember { mutableStateOf(0) }
                    ScrollableTabRow(selectedTabIndex = selectedPackIndex.coerceIn(0, uiState.stickerPacks.lastIndex)) {
                        uiState.stickerPacks.forEachIndexed { index, pack ->
                            Tab(
                                selected = selectedPackIndex == index,
                                onClick = { selectedPackIndex = index },
                                text = { Text(pack.name, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            )
                        }
                    }
                    val pack = uiState.stickerPacks[selectedPackIndex.coerceIn(0, uiState.stickerPacks.lastIndex)]
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        modifier = Modifier.fillMaxWidth().height(280.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(12.dp)
                    ) {
                        gridItems(items = pack.stickers) { sticker ->
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        showStickerSheet = false
                                        viewModel.sendStickerUrl(sticker.url, sticker.body)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model = MatrixMedia(sticker.url, null),
                                    contentDescription = sticker.shortcode,
                                    modifier = Modifier.size(72.dp),
                                    contentScale = ContentScale.Fit
                                )
                            }
                        }
                    }
                } else if (uiState.stickerPacksLoading) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(160.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    Text(
                        text = "Стикер-паки не найдены. Паки берутся из комнат с im.ponies.room_emotes (MSC2545).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(onClick = { showAddPackDialog = true }) {
                        Text("Добавить пак…")
                    }
                    TextButton(onClick = {
                        showManagePacksDialog = true
                        viewModel.loadStickerPackRooms()
                    }) {
                        Text("Управление паками")
                    }
                }
                HorizontalDivider()
                TextButton(
                    onClick = {
                        showStickerSheet = false
                        pickSticker()
                    },
                    modifier = Modifier.padding(horizontal = 12.dp)
                ) {
                    Text("Отправить файл как стикер")
                }
            }
        }
    }

    // MSC2545: subscribe to a pack room by matrix.to link / alias / roomId.
    if (showAddPackDialog) {
        AlertDialog(
            onDismissRequest = { showAddPackDialog = false },
            title = { Text("Добавить пак") },
            text = {
                OutlinedTextField(
                    value = addPackInput,
                    onValueChange = { addPackInput = it },
                    label = { Text("Ссылка matrix.to, #алиас:сервер или !roomId:сервер") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showAddPackDialog = false
                        viewModel.addStickerPackByReference(addPackInput)
                        addPackInput = ""
                    },
                    enabled = addPackInput.isNotBlank()
                ) { Text("Добавить") }
            },
            dismissButton = {
                TextButton(onClick = { showAddPackDialog = false }) { Text("Отмена") }
            }
        )
    }

    // MSC2545: manage subscribed pack rooms (unsubscribe per room).
    if (showManagePacksDialog) {
        AlertDialog(
            onDismissRequest = { showManagePacksDialog = false },
            title = { Text("Управление паками") },
            text = {
                if (uiState.packRooms.isEmpty()) {
                    Text(
                        text = "Вы не подписаны на паки комнат",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp)
                    ) {
                        items(
                            items = uiState.packRooms,
                            key = { it }
                        ) { packRoomId ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                            ) {
                                Text(
                                    text = packRoomId.substringBefore(":"),
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = { viewModel.removeStickerPackRoom(packRoomId) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Удалить пак",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showManagePacksDialog = false }) { Text("Закрыть") }
            }
        )
    }

    // Report a message to the homeserver administrators.
    reportingMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { reportingMessage = null },
            title = { Text("Пожаловаться на сообщение") },
            text = {
                OutlinedTextField(
                    value = reportReason,
                    onValueChange = { reportReason = it },
                    label = { Text("Причина (необязательно)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.reportMessage(message.eventId, reportReason)
                    reportingMessage = null
                    reportReason = ""
                }) { Text("Отправить") }
            },
            dismissButton = {
                TextButton(onClick = { reportingMessage = null }) { Text("Отмена") }
            }
        )
    }

    // Multi-select: pick a target room for forwarding.
    if (showForwardDialog) {
        ForwardRoomPickerDialog(
            title = "Переслать в…",
            rooms = chatListPreview,
            onPick = { roomId ->
                showForwardDialog = false
                viewModel.forwardSelected(roomId)
            },
            onDismiss = { showForwardDialog = false }
        )
    }

    // Direct forward from a message's context menu.
    singleForwardMessage?.let { message ->
        ForwardRoomPickerDialog(
            title = "Переслать в…",
            rooms = chatListPreview,
            onPick = { roomId ->
                singleForwardMessage = null
                viewModel.clearSelection()
                viewModel.forwardMessage(message, roomId)
            },
            onDismiss = { singleForwardMessage = null }
        )
    }

    // Multi-select delete confirmation (same style as the single-message one).
    if (confirmDeleteSelected) {
        AlertDialog(
            onDismissRequest = { confirmDeleteSelected = false },
            title = { Text("Удалить сообщения?") },
            text = { Text("Будет удалено сообщений: ${uiState.selectedEventIds.size}. Это удалит их для участников комнаты, если сервер разрешает удаление.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDeleteSelected = false
                        viewModel.deleteSelected()
                    }
                ) {
                    Text("Удалить")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteSelected = false }) {
                    Text("Отмена")
                }
            }
        )
    }

    // MSC3488 live location: duration picker (shown after location access was granted).
    if (showLiveLocationDialog) {
        AlertDialog(
            onDismissRequest = { showLiveLocationDialog = false },
            title = { Text("Live-геолокация") },
            text = {
                Column {
                    Text("Ваша геолокация будет транслироваться в комнате в реальном времени. Выберите длительность:")
                    Spacer(modifier = Modifier.height(8.dp))
                    listOf(
                        "15 минут" to 15 * 60_000L,
                        "1 час" to 3_600_000L,
                        "8 часов" to 8 * 3_600_000L
                    ).forEach { (label, durationMs) ->
                        TextButton(
                            onClick = {
                                showLiveLocationDialog = false
                                viewModel.startLiveLocationShare(durationMs)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(label, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLiveLocationDialog = false }) { Text("Отмена") }
            }
        )
    }

    // MSC3488: who is broadcasting their location right now (attach menu summary).
    if (showActiveBeaconsDialog) {
        ActiveBeaconsDialog(
            beacons = activeBeacons,
            onDismiss = { showActiveBeaconsDialog = false }
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MessageItem(
    message: Message,
    ownShortId: String?,
    ownDisplayName: String?,
    isGroupStart: Boolean,
    groupedWithPrevious: Boolean,
    groupedWithNext: Boolean,
    isPinned: Boolean,
    recentReactions: List<String> = emptyList(),
    /** Room custom emoji (MSC2545) for reaction picker and ":shortcode:" reaction chips. */
    customEmotes: List<StickerItem> = emptyList(),
    readers: List<ReadReceipt> = emptyList(),
    threadReplyCount: Int = 0,
    voicePlayback: com.matrix.messenger.platform.VoicePlaybackState? = null,
    onSeekVoice: ((Float) -> Unit)? = null,
    onReaction: ((String) -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onOpenAttachment: (() -> Unit)? = null,
    onOpenImage: (() -> Unit)? = null,
    onOpenThread: (() -> Unit)? = null,
    onReply: (() -> Unit)? = null,
    onTogglePin: (() -> Unit)? = null,
    onPlayAudio: (() -> Unit)? = null,
    onVotePoll: ((List<String>) -> Unit)? = null,
    onEndPoll: (() -> Unit)? = null,
    onStopLiveLocation: (() -> Unit)? = null,
    onReport: (() -> Unit)? = null,
    isSelected: Boolean = false,
    selectionMode: Boolean = false,
    onSelect: (() -> Unit)? = null,
    onForward: (() -> Unit)? = null
) {
    if (message.messageType == MessageType.SYSTEM) {
        Text(
            text = message.body,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        return
    }
    val hasActions = !message.isDeleted && listOf(onReaction, onReply, onOpenThread, onTogglePin, onEdit, onDelete, onEndPoll).any { it != null }
    var menuExpanded by remember(message.eventId) { mutableStateOf(false) }
    var showReactionPicker by remember(message.eventId) { mutableStateOf(false) }
    // In selection mode a tap toggles the tick instead of opening the context menu.
    val itemClick: () -> Unit = {
        if (selectionMode) {
            onSelect?.invoke()
        } else {
            menuExpanded = true
        }
    }
    val itemClickEnabled = selectionMode || hasActions
    BoxWithConstraints(Modifier.fillMaxWidth()) {
    val messageWidth = (maxWidth - 44.dp).coerceAtLeast(1.dp).coerceAtMost(560.dp)
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
            Column(Modifier.widthIn(max = messageWidth)) {
                if (!message.isMine && isGroupStart) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = message.senderName?.substringBefore(":") ?: message.senderId,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = senderAccentColor(message.senderName, message.isMine)
                        )
                        if (message.isBot) {
                            Spacer(modifier = Modifier.width(6.dp))
                            BotBadge()
                        }
                    }
                }
                Box {
                Column(Modifier.widthIn(max = messageWidth)) {
                    // Element X: text-like messages live in a bubble, media is borderless.
                    val isMediaOnly = message.messageType in setOf(
                        MessageType.IMAGE, MessageType.STICKER, MessageType.LOCATION
                    ) && !message.isDeleted

                    if (isMediaOnly) {
                        Column(
                            modifier = Modifier.clickable(enabled = itemClickEnabled) { itemClick() },
                            horizontalAlignment = if (message.isMine) Alignment.End else Alignment.Start
                        ) {
                            when {
                                message.messageType == MessageType.LOCATION &&
                                    (message.location != null || message.liveBeacon != null) -> {
                                    val cardLocation = message.location ?: message.liveBeacon?.location
                                    if (cardLocation != null) {
                                        LocationCard(
                                            location = cardLocation,
                                            liveBeacon = message.liveBeacon,
                                            isMine = message.isMine,
                                            onOpenMap = { openUrl(GeoUri.toOsmUrl(cardLocation)) },
                                            onStopLive = onStopLiveLocation
                                        )
                                    }
                                }

                                else -> {
                                    val mediaModifier = when (message.messageType) {
                                        MessageType.STICKER -> Modifier
                                            .widthIn(max = 160.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable(enabled = onOpenImage != null) { onOpenImage?.invoke() }
                                        else -> Modifier
                                            .widthIn(max = 280.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable(enabled = onOpenImage != null) { onOpenImage?.invoke() }
                                    }
                                    AsyncImage(
                                        model = MatrixMedia(message.mediaUrl, message.encryptedFile),
                                        contentDescription = message.body,
                                        modifier = mediaModifier,
                                        contentScale = ContentScale.Fit
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier.align(Alignment.End).padding(top = 2.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (message.isEdited) {
                                    Text(
                                        text = "(ред.)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = TimeFormat.timeOnly(message.timestamp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        // Element X bubble: outgoing = primary with onPrimary content,
                        // incoming = surfaceContainerHigh. The 4.dp "tail" corner sits at
                        // the bottom on each side and stays flattened where messages group.
                        val bubbleColor = if (message.isMine) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        }
                        val bubbleContentColor = if (message.isMine) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                        val bubbleSecondaryColor = if (message.isMine) {
                            MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        val bubbleAccentColor = if (message.isMine) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                        Surface(
                            shape = RoundedCornerShape(
                                topStart = if (!message.isMine && groupedWithPrevious) 4.dp else 16.dp,
                                topEnd = if (message.isMine && groupedWithPrevious) 4.dp else 16.dp,
                                bottomStart = if (message.isMine) {
                                    if (groupedWithNext) 4.dp else 16.dp
                                } else {
                                    4.dp
                                },
                                bottomEnd = if (message.isMine) {
                                    4.dp
                                } else {
                                    if (groupedWithNext) 4.dp else 16.dp
                                }
                            ),
                            color = bubbleColor,
                            contentColor = bubbleContentColor,
                            modifier = Modifier.clickable(enabled = itemClickEnabled) { itemClick() }
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                val replyTo = message.replyTo
                                if (replyTo != null && !message.isDeleted) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (message.isMine) {
                                            MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.15f)
                                        } else {
                                            MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                                        },
                                        contentColor = bubbleContentColor,
                                        modifier = Modifier.padding(bottom = 6.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                                            Text(
                                                text = replyTo.senderName?.substringBefore(":") ?: "Unknown",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (message.isMine) {
                                                    bubbleContentColor
                                                } else {
                                                    senderAccentColor(replyTo.senderName, message.isMine)
                                                }
                                            )
                                            Text(
                                                text = replyTo.body,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (message.isMine) {
                                                    bubbleContentColor
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                },
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
                                        color = bubbleSecondaryColor
                                    )
                                } else {
                                    val poll = message.poll
                                    when {
                                        poll != null && onVotePoll == null -> Column {
                                            Text(poll.question)
                                            poll.answers.forEach { Text(it.text) }
                                            Text("Голосование недоступно", style = MaterialTheme.typography.labelSmall)
                                        }
                                        poll != null -> PollContent(
                                            message = message,
                                            onVote = { onVotePoll?.invoke(it) },
                                            ended = message.pollEnded,
                                            isMine = message.isMine
                                        )

                                        message.messageType == MessageType.VERIFICATION_REQUEST -> {
                                            Column {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        Icons.Default.VerifiedUser,
                                                        contentDescription = null,
                                                        tint = bubbleAccentColor,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = "Запрос верификации ключа",
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = bubbleContentColor
                                                    )
                                                }
                                                if (!message.isMine) {
                                                    Text(
                                                        text = "Устройство ${message.verificationDeviceId ?: "неизвестно"}",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = bubbleSecondaryColor
                                                    )
                                                }
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = "Element отменяет запросы верификации из чата — верифицируйте эту сессию через Настройки → Сессии в Element",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = bubbleSecondaryColor
                                                )
                                            }
                                        }

                                        message.messageType == MessageType.CALL -> {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    if (message.callIsVideo == true) Icons.Default.Videocam else Icons.Default.Call,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(20.dp),
                                                    tint = bubbleAccentColor
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = message.body,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = bubbleContentColor
                                                )
                                            }
                                        }

                                        message.messageType == MessageType.VIDEO &&
                                            (message.mediaUrl != null || message.encryptedFile != null) -> {
                                            val hasThumb = message.videoThumbnailUrl != null || message.videoThumbnailFile != null
                                            Box(
                                                modifier = Modifier.clickable(enabled = onOpenAttachment != null) { onOpenAttachment?.invoke() }
                                            ) {
                                                if (hasThumb) {
                                                    AsyncImage(
                                                        model = MatrixMedia(message.videoThumbnailUrl, message.videoThumbnailFile),
                                                        contentDescription = message.body,
                                                        modifier = Modifier
                                                            .widthIn(max = 280.dp)
                                                            .clip(RoundedCornerShape(12.dp)),
                                                        contentScale = ContentScale.Crop
                                                    )
                                                } else {
                                                    Box(
                                                        modifier = Modifier
                                                            .widthIn(max = 280.dp)
                                                            .height(140.dp)
                                                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                                            .clip(RoundedCornerShape(12.dp)),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            Icons.Default.Videocam,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            modifier = Modifier.size(36.dp)
                                                        )
                                                    }
                                                }
                                                // Play button over the preview
                                                Surface(
                                                    shape = CircleShape,
                                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
                                                    modifier = Modifier
                                                        .align(Alignment.Center)
                                                        .size(44.dp)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Icon(
                                                            Icons.Default.PlayArrow,
                                                            contentDescription = "Воспроизвести видео",
                                                            tint = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.size(28.dp)
                                                        )
                                                    }
                                                }
                                                // Duration badge
                                                message.mediaDurationMs?.let { ms ->
                                                    Surface(
                                                        shape = RoundedCornerShape(6.dp),
                                                        color = Color.Black.copy(alpha = 0.6f),
                                                        modifier = Modifier
                                                            .align(Alignment.BottomEnd)
                                                            .padding(6.dp)
                                                    ) {
                                                        Text(
                                                            text = com.matrix.messenger.data.model.MediaFormat.duration(ms),
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = Color.White,
                                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        message.mediaUrl != null || message.encryptedFile != null -> {
                                            if (message.messageType == MessageType.AUDIO && onPlayAudio == null) {
                                                Text("Аудио: ${message.body} · воспроизведение недоступно")
                                            } else if (message.messageType == MessageType.AUDIO) {
                                                VoiceMessageRow(
                                                    message = message,
                                                    playback = voicePlayback,
                                                    isMine = message.isMine,
                                                    onToggle = { onPlayAudio?.invoke() },
                                                    onSeek = { onSeekVoice?.invoke(it) }
                                                )
                                            } else {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.clickable(enabled = onOpenAttachment != null) { onOpenAttachment?.invoke() }
                                                ) {
                                                    Icon(
                                                        Icons.Default.AttachFile,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(20.dp),
                                                        tint = bubbleContentColor
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = message.body,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = bubbleContentColor
                                                    )
                                                }
                                            }
                                        }

                                        else -> {
                                            val formatted = message.formattedBody
                                            if (formatted != null) {
                                                RichMessageText(
                                                    html = formatted,
                                                    linkColor = bubbleAccentColor,
                                                    codeBackground = if (message.isMine) {
                                                        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.15f)
                                                    } else {
                                                        MaterialTheme.colorScheme.surfaceVariant
                                                    }
                                                )
                                            } else {
                                                Text(
                                                    text = buildMentionAnnotatedString(
                                                        body = message.body,
                                                        ownShortId = ownShortId,
                                                        ownDisplayName = ownDisplayName,
                                                        mentionColor = bubbleAccentColor
                                                    ),
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = bubbleContentColor
                                                )
                                            }
                                        }
                                    }
                                }
                                // Timestamp pinned to the bubble's bottom-right (Element X).
                                Row(
                                    modifier = Modifier.align(Alignment.End).padding(top = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (message.isEdited) {
                                        Text(
                                            text = "(ред.)",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = bubbleSecondaryColor
                                        )
                                    }
                                    Text(
                                        text = TimeFormat.timeOnly(message.timestamp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = bubbleSecondaryColor
                                    )
                                }
                            }
                        }
                    }

                    // Element X: thread summary chip under the root message.
                    if (message.threadRootId == null && threadReplyCount > 0 && onOpenThread != null) {
                        Surface(
                            shape = MaterialTheme.shapes.extraLarge,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .clickable { onOpenThread?.invoke() }
                        ) {
                            Text(
                                text = "💬 В ветке: $threadReplyCount",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    // Reactions and read receipts live OUTSIDE the bubble (Element X).
                    if (message.isMine && readers.isNotEmpty()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            readers.take(3).forEach { reader ->
                                MatrixAvatar(
                                    name = reader.name ?: reader.userId,
                                    avatarUrl = reader.avatarUrl,
                                    size = 18.dp,
                                    shape = CircleShape
                                )
                                Spacer(Modifier.width(3.dp))
                            }
                            if (readers.size > 3) {
                                Text(
                                    text = "+${readers.size - 3}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = "Прочитано",
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    if (message.reactions.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            message.reactions.forEach { (emoji, count) ->
                                val isMine = emoji in message.myReactions
                                // ":shortcode:" keys resolve to a room custom emote image when possible.
                                val emoteUrl = emoji.takeIf { it.length > 2 && it.startsWith(":") && it.endsWith(":") }
                                    ?.let { key ->
                                        customEmotes.firstOrNull { emote ->
                                            emote.shortcode == key.substring(1, key.length - 1)
                                        }?.url
                                    }
                                Surface(
                                    shape = MaterialTheme.shapes.extraLarge,
                                    color = if (isMine) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceContainerHigh,
                                    modifier = Modifier.clickable(enabled = onReaction != null) { onReaction?.invoke(emoji) }
                                ) {
                                    if (emoteUrl != null) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(start = 5.dp, end = 7.dp, top = 2.dp, bottom = 2.dp)
                                        ) {
                                            AsyncImage(
                                                model = MatrixMedia(emoteUrl, null),
                                                contentDescription = emoji,
                                                modifier = Modifier.size(16.dp),
                                                contentScale = ContentScale.Fit
                                            )
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text(
                                                text = "$count",
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                    } else {
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
                        if (onReaction != null) FlowRow(
                            modifier = Modifier.widthIn(max = 280.dp).padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            QuickReactions.forEach { emoji ->
                                Text(
                                    text = emoji,
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier
                                        .clip(MaterialTheme.shapes.small)
                                        .clickable {
                                            menuExpanded = false
                                            onReaction?.invoke(emoji)
                                        }
                                        .padding(4.dp)
                                )
                            }
                            Text(
                                text = "+",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier
                                    .clip(MaterialTheme.shapes.small)
                                    .clickable {
                                        menuExpanded = false
                                        showReactionPicker = true
                                    }
                                    .padding(4.dp)
                            )
                        }
                        if (onReaction != null) HorizontalDivider()
                        if (onSelect != null) DropdownMenuItem(
                            text = { Text("Выбрать") },
                            leadingIcon = { Icon(Icons.Default.CheckCircle, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onSelect?.invoke()
                            }
                        )
                        if (onSelect != null) DropdownMenuItem(
                            text = { Text("Переслать") },
                            leadingIcon = { Icon(Icons.Default.Forward, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onForward?.invoke()
                            }
                        )
                        if (!message.isDeleted) {
                            if (onReply != null) DropdownMenuItem(
                                text = { Text("Ответить") },
                                onClick = {
                                    menuExpanded = false
                                    onReply?.invoke()
                                }
                            )
                            if (onOpenThread != null) DropdownMenuItem(
                                text = { Text(if (message.threadRootId != null) "Открыть ветку" else "Ответить в ветке") },
                                leadingIcon = { Icon(Icons.Default.Forum, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onOpenThread?.invoke()
                                }
                            )
                            if (onTogglePin != null) DropdownMenuItem(
                                text = { Text(if (isPinned) "Открепить" else "Закрепить") },
                                leadingIcon = { Icon(Icons.Default.PushPin, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onTogglePin?.invoke()
                                }
                            )
                        }
                        if (message.isMine && !message.isDeleted) {
                            if (onEdit != null && message.messageType in setOf(MessageType.TEXT, MessageType.EMOTE) && message.poll == null) DropdownMenuItem(
                                text = { Text("Редактировать") },
                                onClick = {
                                    menuExpanded = false
                                    onEdit?.invoke()
                                }
                            )
                            if (onDelete != null) DropdownMenuItem(
                                text = { Text("Удалить") },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onDelete?.invoke()
                                }
                            )
                            if (message.poll != null && message.isMine && !message.pollEnded && onEndPoll != null) {
                                DropdownMenuItem(
                                    text = { Text("Завершить опрос") },
                                    leadingIcon = { Icon(Icons.Default.HowToVote, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        onEndPoll?.invoke()
                                    }
                                )
                            }
                        }
                        if (!message.isMine && !message.isDeleted && onReport != null) DropdownMenuItem(
                            text = { Text("Пожаловаться") },
                            leadingIcon = { Icon(Icons.Default.Flag, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onReport?.invoke()
                            }
                        )
                    }
                    if (onReaction != null) ReactionPicker(
                        expanded = showReactionPicker,
                        myReactions = message.myReactions,
                        recentReactions = recentReactions,
                        customEmotes = customEmotes,
                        onDismiss = { showReactionPicker = false },
                        onSelect = onReaction
                    )
                }
            }
            // Multi-select tick next to the bubble (Element X style).
            if (selectionMode) {
                Icon(
                    imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = if (isSelected) "Выбрано" else "Не выбрано",
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
            }
            if (message.isMine) {
                Spacer(modifier = Modifier.width(36.dp))
            }
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

/** Selection-mode top bar replacing the room header while messages are ticked. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionModeBar(
    selectedCount: Int,
    onCancel: () -> Unit,
    onForward: () -> Unit,
    onDelete: () -> Unit
) {
    TopAppBar(
        title = { Text("Выбрано: $selectedCount") },
        navigationIcon = {
            IconButton(onClick = onCancel) {
                Icon(Icons.Default.Close, contentDescription = "Отмена")
            }
        },
        actions = {
            TextButton(onClick = onForward) { Text("Переслать") }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Удалить")
            }
        }
    )
}

/** Shared target-room picker for forwarding (multi-select bar and the message menu). */
@Composable
private fun ForwardRoomPickerDialog(
    title: String,
    rooms: List<ChatRoom>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            if (rooms.isEmpty()) {
                Text(
                    text = "Список чатов загружается…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp)
                ) {
                    items(
                        items = rooms,
                        key = { it.roomId }
                    ) { room ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(room.roomId) }
                                .padding(vertical = 8.dp)
                        ) {
                            MatrixAvatar(
                                name = room.name ?: room.roomId,
                                avatarUrl = room.avatarUrl,
                                size = 32.dp,
                                shape = CircleShape
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = room.name ?: room.roomId,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
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
    onCreatePoll: () -> Unit,
    markdownEnabled: Boolean = false,
    onToggleMarkdown: () -> Unit = {},
    onPickSticker: () -> Unit = {},
    onPickLocation: () -> Unit = {},
    onPickLiveLocation: () -> Unit = {},
    onPickVideo: () -> Unit = {},
    /** Number of live location beacons currently broadcasting (0 hides the summary item). */
    activeBeaconCount: Int = 0,
    onShowActiveBeacons: () -> Unit = {}
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
                    IconButton(onClick = onToggleMarkdown) {
                        Icon(
                            Icons.Default.TextFormat,
                            contentDescription = "Markdown",
                            tint = if (markdownEnabled) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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
                                text = { Text("Геолокация") },
                                leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null) },
                                onClick = {
                                    attachMenuVisible = false
                                    onPickLocation()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Live-геолокация") },
                                leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null) },
                                onClick = {
                                    attachMenuVisible = false
                                    onPickLiveLocation()
                                }
                            )
                            if (activeBeaconCount > 0) {
                                DropdownMenuItem(
                                    text = { Text("Активные трансляции") },
                                    leadingIcon = { Icon(Icons.Default.ShareLocation, contentDescription = null) },
                                    onClick = {
                                        attachMenuVisible = false
                                        onShowActiveBeacons()
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Видео") },
                                leadingIcon = { Icon(Icons.Default.Videocam, contentDescription = null) },
                                onClick = {
                                    attachMenuVisible = false
                                    onPickVideo()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Стикер") },
                                leadingIcon = { Icon(Icons.Default.EmojiEmotions, contentDescription = null) },
                                onClick = {
                                    attachMenuVisible = false
                                    onPickSticker()
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

@Composable
private fun UnreadDividerRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.primary)
        Text(
            text = "Новые сообщения",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun LocationCard(
    location: GeoLocation,
    liveBeacon: LiveLocationBeacon? = null,
    isMine: Boolean = false,
    onOpenMap: () -> Unit,
    onStopLive: (() -> Unit)? = null
) {
    // Ticking clock for the live beacon countdown (updates once a second).
    var nowMs by remember(liveBeacon?.infoEventId) {
        mutableStateOf(kotlinx.datetime.Clock.System.now().toEpochMilliseconds())
    }
    LaunchedEffect(liveBeacon?.infoEventId) {
        if (liveBeacon == null) return@LaunchedEffect
        while (true) {
            kotlinx.coroutines.delay(1000)
            nowMs = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        }
    }
    val beaconEnded = liveBeacon != null &&
        liveBeacon.endTimestamp != null &&
        liveBeacon.endTimestamp < nowMs
    val remainingMs = liveBeacon?.endTimestamp?.let { (it - nowMs).coerceAtLeast(0L) }
    val liveActive = liveBeacon != null && liveBeacon.live && !beaconEnded

    Column {
        Box(
            modifier = Modifier
                .widthIn(max = 240.dp)
                .height(140.dp)
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onOpenMap)
        ) {
            AsyncImage(
                model = GeoUri.toTileUrl(location, zoom = 15),
                contentDescription = "Карта",
                modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .align(Alignment.Center)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
            if (liveBeacon != null) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color.Black.copy(alpha = 0.6f),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        if (liveActive) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(MaterialTheme.colorScheme.error, CircleShape)
                            )
                            Text(
                                text = "LIVE" + (remainingMs?.let {
                                    " · " + com.matrix.messenger.data.model.MediaFormat.duration(it)
                                } ?: ""),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White
                            )
                        } else {
                            Text(
                                text = if (liveBeacon.live) "Завершена" else "Трансляция остановлена",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White
                            )
                        }
                    }
                }
            }
            Text(
                text = "© OpenStreetMap",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                    .padding(horizontal = 4.dp)
            )
        }
        if (liveBeacon != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Text(
                    text = when {
                        !liveBeacon.live -> "Трансляция остановлена"
                        beaconEnded -> "Завершена"
                        remainingMs != null -> "Осталось: " + com.matrix.messenger.data.model.MediaFormat.duration(remainingMs)
                        else -> "Трансляция активна"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (isMine && liveActive && onStopLive != null) {
                    TextButton(onClick = onStopLive) {
                        Text("Остановить")
                    }
                }
            }
        }
        Text(
            text = GeoUri.format(location),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun LocationSendDialog(
    initial: GeoLocation?,
    onDismiss: () -> Unit,
    onSend: (Double, Double) -> Unit
) {
    var latText by remember {
        mutableStateOf(initial?.latitude?.toString() ?: "")
    }
    var lonText by remember {
        mutableStateOf(initial?.longitude?.toString() ?: "")
    }
    val lat = latText.trim().replace(',', '.').toDoubleOrNull()
    val lon = lonText.trim().replace(',', '.').toDoubleOrNull()
    val valid = lat != null && lat in -90.0..90.0 && lon != null && lon in -180.0..180.0
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Отправить геолокацию") },
        text = {
            Column {
                if (initial == null) {
                    Text(
                        text = "GPS недоступен — введите координаты вручную",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                } else {
                    Text(
                        text = GeoUri.format(initial) +
                            (initial.accuracyMeters?.let { " (±${it.toInt()} м)" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedTextField(
                    value = latText,
                    onValueChange = { latText = it },
                    label = { Text("Широта (-90…90)") },
                    singleLine = true,
                    isError = latText.isNotBlank() && (lat == null || lat !in -90.0..90.0)
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = lonText,
                    onValueChange = { lonText = it },
                    label = { Text("Долгота (-180…180)") },
                    singleLine = true,
                    isError = lonText.isNotBlank() && (lon == null || lon !in -180.0..180.0)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (valid) onSend(lat!!, lon!!) },
                enabled = valid
            ) { Text("Отправить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

/** Summary of the room's currently-broadcasting live location beacons (MSC3488). */
@Composable
private fun ActiveBeaconsDialog(
    beacons: List<ActiveBeaconInfo>,
    onDismiss: () -> Unit
) {
    // Ticking clock so the "осталось …" labels stay fresh.
    var nowMs by remember { mutableStateOf(kotlinx.datetime.Clock.System.now().toEpochMilliseconds()) }
    LaunchedEffect(beacons) {
        while (true) {
            kotlinx.coroutines.delay(1000)
            nowMs = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Кто сейчас транслирует") },
        text = {
            if (beacons.isEmpty()) {
                Text(
                    text = "Нет активных трансляций",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column {
                    beacons.forEach { beacon ->
                        ActiveBeaconRow(beacon = beacon, nowMs = nowMs)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Закрыть") }
        }
    )
}

@Composable
private fun ActiveBeaconRow(beacon: ActiveBeaconInfo, nowMs: Long) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { openUrl(GeoUri.toOsmUrl(beacon.location)) }
            .padding(vertical = 6.dp)
    ) {
        val tileUrl = remember(beacon.location) { GeoUri.toTileUrl(beacon.location, zoom = 15) }
        if (tileUrl != null) {
            AsyncImage(
                model = tileUrl,
                contentDescription = "Карта",
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = beacon.senderName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            beacon.description?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = beacon.endTimestamp
                    ?.let { end -> formatBeaconRemaining(end - nowMs) }
                    ?: "трансляция активна",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/** Human-readable countdown for a live beacon, e.g. "осталось 7 мин". */
private fun formatBeaconRemaining(msRemaining: Long): String {
    val totalMinutes = (msRemaining / 60_000).coerceAtLeast(0)
    return when {
        totalMinutes >= 60 -> "осталось ${totalMinutes / 60} ч ${totalMinutes % 60} мин"
        totalMinutes >= 1 -> "осталось $totalMinutes мин"
        else -> "осталось меньше минуты"
    }
}

/** Element X palette: deterministic per-sender accent for names and replies. */
private val SenderAccents = listOf(
    Color(0xFF368BD6), Color(0xFFAC3BA8), Color(0xFF03B381), Color(0xFFE64F7A),
    Color(0xFFFF812D), Color(0xFF2DC2C5), Color(0xFF5C56F5), Color(0xFF74D12C)
)

private fun senderAccentColor(senderName: String?, isMine: Boolean): Color {
    if (isMine) return Color.Unspecified
    val key = (senderName ?: "?").trim().lowercase()
    val hash = key.fold(0) { acc, ch -> (acc * 31 + ch.code) and Int.MAX_VALUE }
    return SenderAccents[hash % SenderAccents.size]
}

/** Small "БОТ" chip next to bot names (Element X style). */
@Composable
fun BotBadge() {
    Text(
        text = "БОТ",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 5.dp, vertical = 1.dp)
    )
}

/** Element X style voice message: play/pause, waveform, progress, tap-to-seek. */
@Composable
private fun VoiceMessageRow(
    message: Message,
    playback: com.matrix.messenger.platform.VoicePlaybackState?,
    isMine: Boolean,
    onToggle: () -> Unit,
    onSeek: (Float) -> Unit
) {
    // Inside an outgoing primary bubble the controls invert to onPrimary for contrast.
    val playCircleColor = if (isMine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
    val playIconColor = if (isMine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimary
    val playedBarColor = if (isMine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
    val idleBarColor = if (isMine) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.4f)
    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
    val active = playback?.sourceId == message.eventId
    val playing = active && playback?.playing == true
    val durationMs = playback?.takeIf { active }?.durationMs
        ?: message.mediaDurationMs
    val progress = if (active && playback != null && playback.durationMs > 0) {
        (playback.positionMs.toFloat() / playback.durationMs).coerceIn(0f, 1f)
    } else 0f
    val bars = remember(message.voiceWaveform) {
        com.matrix.messenger.data.model.MediaFormat.normalizeWaveform(
            message.voiceWaveform ?: List(28) { 300 }
        )
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clickable { onToggle() }
    ) {
        Surface(
            shape = CircleShape,
            color = playCircleColor,
            modifier = Modifier.size(32.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (playing) "Пауза" else "Воспроизвести",
                    tint = playIconColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier
                    .widthIn(max = 180.dp)
                    .pointerInput(message.eventId) {
                        detectTapGestures { offset ->
                            val width = size.width.toFloat()
                            if (width > 0) onSeek((offset.x / width).coerceIn(0f, 1f))
                        }
                    }
            ) {
                bars.forEachIndexed { i, amplitude ->
                    val played = (i + 0.5f) / bars.size <= progress
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height((4 + (amplitude / 1024f) * 22).dp)
                            .background(
                                color = if (played) playedBarColor else idleBarColor,
                                shape = RoundedCornerShape(2.dp)
                            )
                    )
                }
            }
            Text(
                text = buildString {
                    if (active && playback != null) {
                        append(com.matrix.messenger.data.model.MediaFormat.duration(playback.positionMs))
                        append(" / ")
                        append(com.matrix.messenger.data.model.MediaFormat.duration(playback.durationMs))
                    } else {
                        append("Голосовое сообщение")
                        message.mediaDurationMs?.let {
                            append(" · ")
                            append(com.matrix.messenger.data.model.MediaFormat.duration(it))
                        }
                    }
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (isMine) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f)
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
