package com.matrix.messenger.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.matrix.messenger.data.model.ChatRoom
import com.matrix.messenger.data.model.GeoLocation
import com.matrix.messenger.data.model.PresenceInfo
import com.matrix.messenger.data.model.ReadReceipt
import com.matrix.messenger.data.model.Message
import com.matrix.messenger.data.model.RoomTrust
import com.matrix.messenger.data.model.StickerItem
import com.matrix.messenger.data.model.StickerPack
import com.matrix.messenger.data.model.UiEvent
import com.matrix.messenger.data.model.UiState
import com.matrix.messenger.data.model.VerificationUiState
import com.matrix.messenger.data.repository.MatrixRepository
import com.matrix.messenger.data.repository.SessionStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

data class ChatUiState(
    val roomId: String = "",
    val roomName: String? = null,
    val roomAvatarUrl: String? = null,
    val roomTrust: RoomTrust? = null,
    val messages: List<Message> = emptyList(),
    val messageInput: String = "",
    val isLoading: Boolean = true,
    val canLoadMore: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isTyping: Boolean = false,
    val typingUsers: List<String> = emptyList(),
    val pinnedMessages: List<Message> = emptyList(),
    val isRecordingVoice: Boolean = false,
    val recordingSeconds: Int = 0,
    val error: String? = null,
    val replyingTo: Message? = null,
    /** Composer markdown mode: send text with org.matrix.custom.html formatting. */
    val markdownEnabled: Boolean = false,
    /** Unread messages at the moment the room was opened (for jump-to-unread). */
    val initialUnreadCount: Int = 0,
    val unreadSnapshotLoaded: Boolean = false,
    val initialUnreadEventId: String? = null,
    /** Event id of the image currently opened in the fullscreen viewer, null = closed. */
    val imageViewerMessageId: String? = null,
    /** Presence of the direct-chat peer, polled while the chat is open. */
    val peerPresence: PresenceInfo? = null,
    /** MSC2545 sticker/emote packs available in this room (loaded lazily for the picker). */
    val stickerPacks: List<StickerPack> = emptyList(),
    val stickerPacksLoading: Boolean = false,
    /** Room ids whose packs the user subscribed to (sticker pack management). */
    val packRooms: List<String> = emptyList(),
    /** Multi-select mode: event ids currently ticked for delete/forward. */
    val selectedEventIds: Set<String> = emptySet()
) {
    /** Custom emoji (MSC2545 usage "emoticon") usable in reactions, deduped by shortcode. */
    val reactionEmotes: List<StickerItem>
        get() = stickerPacks
            .flatMap { pack -> pack.stickers.filter { it.isEmoticon } }
            .distinctBy { it.shortcode }
}

/** One currently-broadcasting live location beacon (MSC3488), latest per sender. */
data class ActiveBeaconInfo(
    val senderId: String,
    val senderName: String,
    val location: GeoLocation,
    /** Beacon broadcast end (epoch ms), null when the server gave no timeout. */
    val endTimestamp: Long?,
    val description: String?
)

class ChatViewModel(
    private val matrixRepository: MatrixRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val _uiStateSealed = MutableStateFlow<UiState<List<Message>>>(UiState.Loading)
    val uiStateSealed: StateFlow<UiState<List<Message>>> = _uiStateSealed.asStateFlow()

    private val _events = Channel<UiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    /** SAS verification state (in-chat verification of the requesting device). */
    val verificationState: StateFlow<VerificationUiState> = matrixRepository.verificationState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VerificationUiState.Idle)

    /** Read receipts of other members keyed by the message event id. */
    private val _readReceipts = MutableStateFlow<Map<String, List<ReadReceipt>>>(emptyMap())
    val readReceipts: StateFlow<Map<String, List<ReadReceipt>>> = _readReceipts.asStateFlow()

    private fun launchRoomAction(error: String, block: suspend () -> Unit): Job? = roomScope?.launch {
        try { block() }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { _events.send(UiEvent.ShowSnackbar("$error: ${e.message ?: "неизвестная ошибка"}")) }
    }

    fun confirmVerification(matches: Boolean) {
        launchRoomAction("Ошибка верификации") { matrixRepository.confirmVerification(matches) }
    }

    fun cancelVerification() {
        launchRoomAction("Ошибка отмены верификации") { matrixRepository.cancelVerification() }
    }

    private var roomScope: CoroutineScope? = null

    fun setRoomId(roomId: String) {
        if (_uiState.value.roomId != roomId) {
            roomScope?.cancel()
            recordingTimerJob?.cancel()
            voiceRecorder?.cancel()
            voiceRecorder = null
            _uiState.value = ChatUiState(roomId = roomId)
            _uiStateSealed.value = UiState.Loading
            _readReceipts.value = emptyMap()
            _chatListPreview.value = emptyList()
            chatListPreviewLoaded = false
            stickerPacksLoadedForRoom = null
            roomScope = CoroutineScope(viewModelScope.coroutineContext + SupervisorJob(viewModelScope.coroutineContext[Job]))
            observeMessages(roomId)
            observePeerPresence(roomId)
            if (!presencePublished) {
                presencePublished = true
                roomScope?.launch { runCatching { matrixRepository.setOwnPresence(true) } }
            }
        }
    }

    private fun unreadAnchor(state: ChatUiState): String? = state.initialUnreadEventId
        ?: state.messages.takeIf { state.unreadSnapshotLoaded && state.initialUnreadCount > 0 && it.isNotEmpty() }
            ?.let { it[(it.size - state.initialUnreadCount).coerceAtLeast(0)].eventId }

    private fun observeMessages(roomId: String) {
        roomScope?.launch {
            try {
                matrixRepository.getMessagesFlow(roomId).flowOn(Dispatchers.Default).collect { messages ->
                    val state = _uiState.value.copy(
                        messages = messages.sortedBy { it.timestamp },
                        isLoading = false,
                        canLoadMore = matrixRepository.canLoadMoreMessages(roomId)
                    )
                    _uiState.value = state.copy(initialUnreadEventId = unreadAnchor(state))
                    _uiStateSealed.value = UiState.Success(messages)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message ?: "Не удалось загрузить сообщения")
                _events.send(UiEvent.ShowSnackbar("Не удалось загрузить сообщения: ${e.message}"))
            }
        }

        roomScope?.launch {
            try {
                val roomInfo = matrixRepository.getRoomInfo(roomId)
                val state = _uiState.value.copy(
                    roomName = roomInfo?.name,
                    roomAvatarUrl = roomInfo?.avatarUrl,
                    initialUnreadCount = roomInfo?.unreadCount ?: 0,
                    unreadSnapshotLoaded = true
                )
                _uiState.value = state.copy(initialUnreadEventId = unreadAnchor(state))
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _events.send(UiEvent.ShowSnackbar("Не удалось загрузить сведения комнаты: ${e.message}")) }
        }

        launchRoomAction("Не удалось загрузить доверие комнаты") {
            matrixRepository.getRoomTrustFlow(roomId).flowOn(Dispatchers.Default).collect { trust ->
                _uiState.value = _uiState.value.copy(roomTrust = trust)
            }
        }

        launchRoomAction("Не удалось загрузить отметки прочтения") {
            matrixRepository.getReadReceiptsFlow(roomId).flowOn(Dispatchers.Default).collect { receipts ->
                _readReceipts.value = receipts
            }
        }

        launchRoomAction("Не удалось загрузить статус набора") {
            matrixRepository.getTypingUsersFlow(roomId).flowOn(Dispatchers.Default).collect { users ->
                _uiState.value = _uiState.value.copy(typingUsers = users)
            }
        }

        launchRoomAction("Не удалось загрузить закреплённые сообщения") {
            matrixRepository.getPinnedMessagesFlow(roomId).flowOn(Dispatchers.Default).collect { pinned ->
                _uiState.value = _uiState.value.copy(pinnedMessages = pinned)
            }
        }
    }

    fun togglePin(message: Message) {
        val roomId = _uiState.value.roomId
        val pinned = _uiState.value.pinnedMessages.any { it.eventId == message.eventId }
        roomScope?.launch {
            try {
                matrixRepository.setPinnedMessage(roomId, message.eventId, pinned = !pinned)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.send(UiEvent.ShowSnackbar("Не удалось изменить закреп: ${e.message}"))
            }
        }
    }

    fun loadEarlierMessages() {
        val roomId = _uiState.value.roomId
        if (roomId.isBlank() || _uiState.value.isLoadingMore) return
        _uiState.value = _uiState.value.copy(isLoadingMore = true)
        roomScope?.launch {
            try {
                matrixRepository.loadEarlierMessages(roomId)
                _uiState.value = _uiState.value.copy(canLoadMore = matrixRepository.canLoadMoreMessages(roomId))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoadingMore = false)
                _events.send(UiEvent.ShowSnackbar("Ошибка загрузки: ${e.message}"))
            } finally {
                _uiState.value = _uiState.value.copy(isLoadingMore = false)
            }
        }
    }

    fun onMessageInputChange(input: String) {
        _uiState.value = _uiState.value.copy(
            messageInput = input,
            isTyping = input.isNotBlank()
        )

        launchRoomAction("Не удалось отправить статус набора") {
            matrixRepository.sendTypingNotification(
                _uiState.value.roomId,
                input.isNotBlank()
            )
        }
    }

    /**
     * Element-style slash commands. Returns true when the input was a command
     * and must not be sent as a message.
     */
    private suspend fun handleSlashCommand(raw: String): Boolean {
        if (!raw.startsWith("/")) return false
        val parts = raw.substring(1).split(' ').filter { it.isNotBlank() }
        val command = parts.firstOrNull()?.lowercase() ?: return false
        val args = parts.drop(1)
        val roomId = _uiState.value.roomId

        suspend fun requireUser(): String =
            args.firstOrNull()?.takeIf { it.startsWith("@") && it.contains(":") }
                ?: throw IllegalArgumentException("Укажите @user:server")

        when (command) {
            "me" -> matrixRepository.sendEmoteMessage(roomId, args.joinToString(" "))
            "join" -> {
                val target = args.firstOrNull()
                    ?: throw IllegalArgumentException("Укажите #alias:server или roomId")
                matrixRepository.joinRoom(target)
                _events.send(UiEvent.ShowSnackbar("Присоединился к $target"))
            }
            "leave" -> {
                matrixRepository.leaveRoom(roomId)
                _events.send(UiEvent.ShowSnackbar("Вы вышли из комнаты"))
            }
            "invite" -> {
                matrixRepository.inviteUserToRoom(roomId, requireUser())
                _events.send(UiEvent.ShowSnackbar("Приглашение отправлено"))
            }
            "kick" -> {
                matrixRepository.kickUser(roomId, requireUser())
                _events.send(UiEvent.ShowSnackbar("Участник исключён"))
            }
            "ban" -> {
                matrixRepository.banUser(roomId, requireUser(), args.drop(1).joinToString(" ").ifBlank { null })
                _events.send(UiEvent.ShowSnackbar("Участник забанен"))
            }
            "unban" -> {
                matrixRepository.unbanUser(roomId, requireUser())
                _events.send(UiEvent.ShowSnackbar("Участник разбанен"))
            }
            "help" -> _events.send(
                UiEvent.ShowSnackbar("/me /join #alias /leave /invite @user /kick @user /ban @user [причина] /unban @user")
            )
            else -> throw IllegalArgumentException("Неизвестная команда: /$command. /help — список")
        }
        return true
    }

    fun sendMessage() {
        roomScope?.launch {
            val state = _uiState.value
            val message = state.messageInput.trim()
            if (message.isBlank()) return@launch

            try {
                if (handleSlashCommand(message)) {
                    if (_uiState.value.messageInput == state.messageInput) {
                        _uiState.value = _uiState.value.copy(messageInput = "", isTyping = false)
                    }
                    return@launch
                }
                val replyingTo = state.replyingTo
                if (replyingTo != null) {
                    matrixRepository.replyToMessage(
                        roomId = state.roomId,
                        eventId = replyingTo.eventId,
                        text = message,
                        threadRootId = replyingTo.threadRootId
                    )
                } else if (state.markdownEnabled) {
                    matrixRepository.sendTextMessage(
                        state.roomId,
                        message,
                        formattedBody = MarkdownToHtml.convert(message)
                    )
                } else {
                    matrixRepository.sendTextMessage(
                        state.roomId,
                        message
                    )
                }
                if (_uiState.value.messageInput == state.messageInput) {
                    _uiState.value = _uiState.value.copy(
                        messageInput = "",
                        isTyping = false,
                        replyingTo = null
                    )
                }
                matrixRepository.sendTypingNotification(_uiState.value.roomId, false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.send(UiEvent.ShowSnackbar("Ошибка отправки: ${e.message}"))
            }
        }
    }

    private var presencePublished = false

    private fun observePeerPresence(roomId: String) {
        roomScope?.launch {
            val peer = runCatching { matrixRepository.resolveDirectChatPeerId(roomId) }.getOrNull()
                ?: return@launch
            while (_uiState.value.roomId == roomId) {
                val presence = runCatching { matrixRepository.getPresence(peer) }.getOrNull()
                currentCoroutineContext().ensureActive()
                if (_uiState.value.roomId != roomId) return@launch
                _uiState.value = _uiState.value.copy(peerPresence = presence)
                kotlinx.coroutines.delay(60_000)
            }
        }
    }

    fun seekVoice(fraction: Float) {
        com.matrix.messenger.platform.VoicePlayback.seekTo(fraction)
    }

    fun sendVideoMessage(path: String) {
        roomScope?.launch {
            try {
                matrixRepository.sendFileMessage(_uiState.value.roomId, path, "video/mp4")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.send(UiEvent.ShowSnackbar("Ошибка отправки видео: ${e.message}"))
            }
        }
    }

    fun sendSticker(filePath: String, mimeType: String) {
        roomScope?.launch {
            try {
                matrixRepository.sendSticker(_uiState.value.roomId, filePath, mimeType)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.send(UiEvent.ShowSnackbar("Ошибка отправки стикера: ${e.message}"))
            }
        }
    }

    // --- Sticker packs (MSC2545) ---

    private var stickerPacksLoadedForRoom: String? = null

    /** Lazily loads the sticker packs for the current room (guard against double-load). */
    fun loadStickerPacks() {
        val roomId = _uiState.value.roomId
        if (roomId.isBlank() || _uiState.value.stickerPacksLoading) return
        if (stickerPacksLoadedForRoom == roomId) return
        stickerPacksLoadedForRoom = roomId
        _uiState.value = _uiState.value.copy(stickerPacksLoading = true)
        roomScope?.launch {
            try {
                val packs = matrixRepository.getStickerPacks(roomId)
                _uiState.value = _uiState.value.copy(stickerPacks = packs, stickerPacksLoading = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Allow a retry on the next sheet open.
                stickerPacksLoadedForRoom = null
                _uiState.value = _uiState.value.copy(stickerPacksLoading = false)
                _events.send(UiEvent.ShowSnackbar("Не удалось загрузить стикер-паки: ${e.message ?: "неизвестная ошибка"}"))
            }
        }
    }

    /** Sends an m.sticker from an already-uploaded pack URL (sticker picker tap). */
    fun sendStickerUrl(mxcUrl: String, body: String) {
        roomScope?.launch {
            try {
                matrixRepository.sendStickerUrl(_uiState.value.roomId, mxcUrl, body, null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.send(UiEvent.ShowSnackbar("Ошибка отправки стикера: ${e.message}"))
            }
        }
    }

    /** Room ids the user subscribed to for their packs (loaded when the manage dialog opens). */
    fun loadStickerPackRooms() {
        roomScope?.launch {
            try {
                val rooms = matrixRepository.getStickerPackRooms()
                _uiState.value = _uiState.value.copy(packRooms = rooms)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.send(UiEvent.ShowSnackbar("Не удалось загрузить паки: ${e.message ?: "неизвестная ошибка"}"))
            }
        }
    }

    /** Adds a pack by matrix.to link / alias / roomId, then force-reloads the picker. */
    fun addStickerPackByReference(reference: String) {
        val trimmed = reference.trim()
        if (trimmed.isEmpty()) return
        launchRoomAction("Не удалось добавить пак") {
            matrixRepository.addStickerPackByReference(trimmed)
            _events.send(UiEvent.ShowSnackbar("Пак добавлен"))
            reloadStickerPacks()
        }
    }

    /** Unsubscribes a room's packs and force-reloads the picker. */
    fun removeStickerPackRoom(roomIdToRemove: String) {
        launchRoomAction("Не удалось удалить пак") {
            matrixRepository.removeStickerPackRoom(roomIdToRemove)
            _uiState.value = _uiState.value.copy(packRooms = _uiState.value.packRooms - roomIdToRemove)
            _events.send(UiEvent.ShowSnackbar("Пак удалён"))
            reloadStickerPacks()
        }
    }

    /** Forces a fresh pack fetch on the next load (bypasses the double-load guard). */
    private fun reloadStickerPacks() {
        stickerPacksLoadedForRoom = null
        loadStickerPacks()
    }

    // --- Message reporting ---

    fun reportMessage(eventId: String, reason: String?) {
        val roomId = _uiState.value.roomId
        launchRoomAction("Не удалось отправить жалобу") {
            matrixRepository.reportMessage(roomId, eventId, reason?.trim()?.takeIf { it.isNotBlank() }, null)
            _events.send(UiEvent.ShowSnackbar("Жалоба отправлена"))
        }
    }

    // --- Multi-select (delete + forward) ---

    fun toggleSelection(eventId: String) {
        val current = _uiState.value.selectedEventIds
        _uiState.value = _uiState.value.copy(
            selectedEventIds = if (eventId in current) current - eventId else current + eventId
        )
    }

    fun clearSelection() {
        _uiState.value = _uiState.value.copy(selectedEventIds = emptySet())
    }

    fun deleteSelected() {
        val roomId = _uiState.value.roomId
        val eventIds = _uiState.value.selectedEventIds.toList()
        if (eventIds.isEmpty()) return
        roomScope?.launch {
            try {
                matrixRepository.deleteMessages(roomId, eventIds)
                _uiState.value = _uiState.value.copy(selectedEventIds = emptySet())
                _events.send(UiEvent.ShowSnackbar("Удалено: ${eventIds.size}"))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.send(UiEvent.ShowSnackbar("Ошибка удаления: ${e.message}"))
            }
        }
    }

    fun forwardSelected(targetRoomId: String) {
        val state = _uiState.value
        val selectedIds = state.selectedEventIds
        if (selectedIds.isEmpty()) return
        val messages = state.messages.filter { it.eventId in selectedIds }
        roomScope?.launch {
            try {
                val count = matrixRepository.forwardMessages(targetRoomId, messages)
                _uiState.value = _uiState.value.copy(selectedEventIds = emptySet())
                _events.send(UiEvent.ShowSnackbar("Переслано: $count"))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.send(UiEvent.ShowSnackbar("Ошибка пересылки: ${e.message}"))
            }
        }
    }

    /** Room list snapshot for the forward dialog, fetched on demand. */
    private val _chatListPreview = MutableStateFlow<List<ChatRoom>>(emptyList())
    val chatListPreview: StateFlow<List<ChatRoom>> = _chatListPreview.asStateFlow()

    private var chatListPreviewLoaded = false

    fun loadChatListPreview() {
        if (chatListPreviewLoaded) return
        chatListPreviewLoaded = true
        roomScope?.launch {
            try {
                _chatListPreview.value = matrixRepository.getRoomsFlow().first()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                chatListPreviewLoaded = false
                _events.send(UiEvent.ShowSnackbar("Не удалось загрузить список чатов: ${e.message}"))
            }
        }
    }

    // --- Live location (MSC3488) ---

    fun startLiveLocationShare(durationMs: Long) {
        launchRoomAction("Не удалось запустить трансляцию геолокации") {
            matrixRepository.startLiveLocationShare(_uiState.value.roomId, durationMs)
            _events.send(UiEvent.ShowSnackbar("Трансляция геолокации запущена"))
        }
    }

    /**
     * Live location beacons still broadcasting in this room, newest message per
     * sender (for the «Активные трансляции» attach-menu summary).
     */
    fun activeLocationBeacons(): List<ActiveBeaconInfo> {
        val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        return _uiState.value.messages
            .asReversed()
            .filter { message ->
                val beacon = message.liveBeacon
                !message.isDeleted && beacon != null && beacon.live &&
                    (beacon.endTimestamp == null || beacon.endTimestamp > now)
            }
            .distinctBy { it.senderId }
            .map { message ->
                val beacon = message.liveBeacon!!
                ActiveBeaconInfo(
                    senderId = message.senderId,
                    senderName = message.senderName?.substringBefore(":") ?: message.senderId,
                    location = beacon.location,
                    endTimestamp = beacon.endTimestamp,
                    description = beacon.description
                )
            }
    }

    fun stopLiveLocationShare() {
        launchRoomAction("Не удалось остановить трансляцию геолокации") {
            matrixRepository.stopLiveLocationShare(_uiState.value.roomId)
        }
    }

    fun sendLocation(latitude: Double, longitude: Double, accuracyMeters: Double? = null) {
        roomScope?.launch {
            try {
                matrixRepository.sendLocation(_uiState.value.roomId, latitude, longitude, accuracyMeters)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.send(UiEvent.ShowSnackbar("Ошибка отправки геолокации: ${e.message}"))
            }
        }
    }

    fun toggleMarkdown() {
        _uiState.value = _uiState.value.copy(markdownEnabled = !_uiState.value.markdownEnabled)
    }

    fun openImageViewer(message: Message) {
        _uiState.value = _uiState.value.copy(imageViewerMessageId = message.eventId)
    }

    fun closeImageViewer() {
        _uiState.value = _uiState.value.copy(imageViewerMessageId = null)
    }

    private val _recentReactions = MutableStateFlow<List<String>>(emptyList())
    val recentReactions: StateFlow<List<String>> = _recentReactions.asStateFlow()

    init {
        viewModelScope.launch {
            _recentReactions.value = runCatching { SessionStore.readRecentReactions() }.getOrDefault(emptyList())
        }
    }

    private suspend fun rememberReaction(reaction: String) {
        val updated = (listOf(reaction) + _recentReactions.value.filter { it != reaction }).take(8)
        _recentReactions.value = updated
        runCatching { SessionStore.saveRecentReactions(updated) }
    }

    fun sendReaction(eventId: String, reaction: String) {
        roomScope?.launch {
            try {
                matrixRepository.sendReaction(eventId, reaction)
                rememberReaction(reaction)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.send(UiEvent.ShowSnackbar("Ошибка отправки реакции: ${e.message}"))
            }
        }
    }

    /** Toggles a reaction: adds it, or removes it when the user already reacted with it. */
    fun toggleReaction(message: Message, reaction: String) {
        roomScope?.launch {
            try {
                if (reaction in message.myReactions) {
                    matrixRepository.unreact(_uiState.value.roomId, message.eventId, reaction)
                } else {
                    matrixRepository.sendReaction(message.eventId, reaction)
                    rememberReaction(reaction)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.send(UiEvent.ShowSnackbar("Ошибка реакции: ${e.message}"))
            }
        }
    }

    fun replyToMessage(message: Message) {
        _uiState.value = _uiState.value.copy(replyingTo = message)
    }

    fun cancelReply() {
        _uiState.value = _uiState.value.copy(replyingTo = null)
    }

    fun editMessage(eventId: String, newText: String) {
        roomScope?.launch {
            try {
                matrixRepository.editMessage(
                    eventId = eventId,
                    roomId = _uiState.value.roomId,
                    newText = newText
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.send(UiEvent.ShowSnackbar("Ошибка редактирования: ${e.message}"))
            }
        }
    }

    fun deleteMessage(eventId: String) {
        roomScope?.launch {
            try {
                matrixRepository.deleteMessage(
                    eventId = eventId,
                    roomId = _uiState.value.roomId
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.send(UiEvent.ShowSnackbar("Ошибка удаления: ${e.message}"))
            }
        }
    }

    fun sendFile(filePath: String, mimeType: String?) {
        roomScope?.launch {
            try {
                matrixRepository.sendFileMessage(
                    roomId = _uiState.value.roomId,
                    filePath = filePath,
                    mimeType = mimeType.orEmpty()
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                com.matrix.messenger.platform.logError("Chat", "File send failed ($filePath)", e)
                _events.send(UiEvent.ShowSnackbar("Ошибка отправки файла: ${e.message}"))
            }
        }
    }

    // --- Voice messages ---

    private var voiceRecorder: com.matrix.messenger.platform.VoiceRecorder? = null
    private var recordingTimerJob: Job? = null

    fun startVoiceRecording() {
        if (_uiState.value.isRecordingVoice) return
        val recorder = com.matrix.messenger.platform.createVoiceRecorder()
        if (!recorder.start()) {
            roomScope?.launch {
                _events.send(UiEvent.ShowSnackbar("Не удалось начать запись — проверьте микрофон"))
            }
            return
        }
        voiceRecorder = recorder
        _uiState.value = _uiState.value.copy(isRecordingVoice = true, recordingSeconds = 0)
        recordingTimerJob = roomScope?.launch {
            while (true) {
                kotlinx.coroutines.delay(1000)
                _uiState.value = _uiState.value.copy(recordingSeconds = _uiState.value.recordingSeconds + 1)
            }
        }
    }

    fun stopVoiceRecording(send: Boolean) {
        val recorder = voiceRecorder ?: return
        voiceRecorder = null
        recordingTimerJob?.cancel()
        recordingTimerJob = null
        _uiState.value = _uiState.value.copy(isRecordingVoice = false, recordingSeconds = 0)
        if (!send) {
            recorder.cancel()
            return
        }
        roomScope?.launch {
            val path = recorder.stop()
            if (path == null) {
                _events.send(UiEvent.ShowSnackbar("Запись не сохранилась"))
                return@launch
            }
            try {
                matrixRepository.sendVoiceMessage(
                    _uiState.value.roomId,
                    path,
                    durationMs = null,
                    waveform = recorder.waveform
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.send(UiEvent.ShowSnackbar("Ошибка отправки голосового: ${e.message}"))
            }
        }
    }

    val voicePlayback = com.matrix.messenger.platform.VoicePlayback.state

    fun playAudioMessage(message: Message) {
        roomScope?.launch {
            try {
                // Both encrypted and plain media resolve to a local file through
                // the authenticated media client.
                val source = matrixRepository.resolveMediaFile(message.eventId, message.body, _uiState.value.roomId)
                if (source != null) {
                    com.matrix.messenger.platform.VoicePlayback.toggle(message.eventId, source)
                } else {
                    _events.send(UiEvent.ShowSnackbar("Не удалось открыть аудио"))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.send(UiEvent.ShowSnackbar("Ошибка воспроизведения: ${e.message}"))
            }
        }
    }

    fun openAttachment(message: Message) {
        roomScope?.launch {
            try {
                val path = matrixRepository.resolveMediaFile(message.eventId, message.body, _uiState.value.roomId)
                if (path != null) {
                    com.matrix.messenger.platform.openLocalFile(path)
                } else {
                    // No EncryptedFile was captured for this event (e.g. message
                    // loaded before the mapping existed) — log for diagnostics.
                    com.matrix.messenger.platform.logError(
                        "Chat",
                        "Attachment ${message.eventId} has no media descriptor",
                        null
                    )
                    _events.send(UiEvent.ShowSnackbar("Не удалось открыть файл"))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                com.matrix.messenger.platform.logError(
                    "Chat",
                    "Attachment open failed: ${message.eventId} (${message.body})",
                    e
                )
                _events.send(UiEvent.ShowSnackbar("Ошибка открытия файла: ${e.message}"))
            }
        }
    }

    fun leaveRoom() {
        roomScope?.launch {
            try {
                matrixRepository.leaveRoom(_uiState.value.roomId)
                _events.send(UiEvent.NavigateBack)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.send(UiEvent.ShowSnackbar("Ошибка выхода из комнаты: ${e.message}"))
            }
        }
    }

    fun inviteUser(userId: String) {
        val target = userId.trim()
        if (target.isBlank() || !target.startsWith("@")) {
            roomScope?.launch {
                _events.send(UiEvent.ShowSnackbar("Введите user ID вида @user:server"))
            }
            return
        }
        roomScope?.launch {
            try {
                matrixRepository.inviteUserToRoom(_uiState.value.roomId, target)
                _events.send(UiEvent.ShowSnackbar("Приглашение отправлено"))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.send(UiEvent.ShowSnackbar("Не удалось пригласить: ${e.message}"))
            }
        }
    }

    fun createPoll(question: String, answers: List<String>) {
        roomScope?.launch {
            try {
                matrixRepository.sendPoll(_uiState.value.roomId, question.trim(), answers.map { it.trim() })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.send(UiEvent.ShowSnackbar("Не удалось создать опрос: ${e.message}"))
            }
        }
    }

    fun endPoll(pollEventId: String) {
        roomScope?.launch {
            try {
                matrixRepository.sendPollEnd(_uiState.value.roomId, pollEventId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.send(UiEvent.ShowSnackbar("Не удалось завершить опрос: ${e.message}"))
            }
        }
    }

    fun votePoll(message: Message, answerIds: List<String>) {
        roomScope?.launch {
            try {
                matrixRepository.sendPollVote(_uiState.value.roomId, message.eventId, answerIds)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.send(UiEvent.ShowSnackbar("Не удалось проголосовать: ${e.message}"))
            }
        }
    }

    override fun onCleared() {
        // Never leave a running recorder behind when the chat is closed.
        recordingTimerJob?.cancel()
        recordingTimerJob = null
        voiceRecorder?.cancel()
        voiceRecorder = null
        roomScope?.cancel()
        super.onCleared()
    }
}
