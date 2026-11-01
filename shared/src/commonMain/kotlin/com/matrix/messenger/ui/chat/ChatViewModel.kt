package com.matrix.messenger.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.matrix.messenger.data.model.Message
import com.matrix.messenger.data.model.RoomTrust
import com.matrix.messenger.data.model.UiEvent
import com.matrix.messenger.data.model.UiState
import com.matrix.messenger.data.model.VerificationUiState
import com.matrix.messenger.data.repository.MatrixRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
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
    val replyingTo: Message? = null
)

class ChatViewModel(
    private val matrixRepository: MatrixRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val _uiStateSealed = MutableStateFlow<UiState<List<Message>>>(UiState.Loading)
    val uiStateSealed: StateFlow<UiState<List<Message>>> = _uiStateSealed.asStateFlow()

    private val _events = Channel<UiEvent>()
    val events = _events.receiveAsFlow()

    /** SAS verification state (in-chat verification of the requesting device). */
    val verificationState: StateFlow<VerificationUiState> = matrixRepository.verificationState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VerificationUiState.Idle)

    fun acceptVerificationRequest(deviceId: String) {
        viewModelScope.launch {
            try {
                matrixRepository.startDeviceVerification(deviceId)
            } catch (e: Exception) {
                _events.send(UiEvent.ShowSnackbar("Не удалось начать верификацию: ${e.message}"))
            }
        }
    }

    fun confirmVerification(matches: Boolean) {
        viewModelScope.launch { matrixRepository.confirmVerification(matches) }
    }

    fun cancelVerification() {
        viewModelScope.launch { matrixRepository.cancelVerification() }
    }

    fun setRoomId(roomId: String) {
        if (_uiState.value.roomId != roomId) {
            _uiState.value = _uiState.value.copy(roomId = roomId)
            observeMessages(roomId)
        }
    }

    private fun observeMessages(roomId: String) {
        viewModelScope.launch {
            matrixRepository.getMessagesFlow(roomId).collect { messages ->
                _uiState.value = _uiState.value.copy(
                    messages = messages.sortedBy { it.timestamp },
                    isLoading = false,
                    isLoadingMore = false,
                    canLoadMore = matrixRepository.canLoadMoreMessages(roomId)
                )
                _uiStateSealed.value = UiState.Success(messages)
            }
        }

        viewModelScope.launch {
            val roomInfo = matrixRepository.getRoomInfo(roomId)
            _uiState.value = _uiState.value.copy(
                roomName = roomInfo?.name,
                roomAvatarUrl = roomInfo?.avatarUrl
            )
        }

        viewModelScope.launch {
            matrixRepository.getRoomTrustFlow(roomId).collect { trust ->
                _uiState.value = _uiState.value.copy(roomTrust = trust)
            }
        }

        viewModelScope.launch {
            matrixRepository.getTypingUsersFlow(roomId).collect { users ->
                _uiState.value = _uiState.value.copy(typingUsers = users)
            }
        }

        viewModelScope.launch {
            matrixRepository.getPinnedMessagesFlow(roomId).collect { pinned ->
                _uiState.value = _uiState.value.copy(pinnedMessages = pinned)
            }
        }
    }

    fun togglePin(message: Message) {
        val roomId = _uiState.value.roomId
        val pinned = _uiState.value.pinnedMessages.any { it.eventId == message.eventId }
        viewModelScope.launch {
            try {
                matrixRepository.setPinnedMessage(roomId, message.eventId, pinned = !pinned)
            } catch (e: Exception) {
                _events.send(UiEvent.ShowSnackbar("Не удалось изменить закреп: ${e.message}"))
            }
        }
    }

    fun loadEarlierMessages() {
        val roomId = _uiState.value.roomId
        if (roomId.isBlank() || _uiState.value.isLoadingMore) return
        _uiState.value = _uiState.value.copy(isLoadingMore = true)
        viewModelScope.launch {
            try {
                matrixRepository.loadEarlierMessages(roomId)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoadingMore = false)
                _events.send(UiEvent.ShowSnackbar("Ошибка загрузки: ${e.message}"))
            }
        }
    }

    fun onMessageInputChange(input: String) {
        _uiState.value = _uiState.value.copy(
            messageInput = input,
            isTyping = input.isNotBlank()
        )

        viewModelScope.launch {
            matrixRepository.sendTypingNotification(
                _uiState.value.roomId,
                input.isNotBlank()
            )
        }
    }

    fun sendMessage() {
        viewModelScope.launch {
            val state = _uiState.value
            val message = state.messageInput.trim()
            if (message.isBlank()) return@launch

            try {
                val replyingTo = state.replyingTo
                if (replyingTo != null) {
                    matrixRepository.replyToMessage(
                        roomId = state.roomId,
                        eventId = replyingTo.eventId,
                        text = message,
                        threadRootId = replyingTo.threadRootId
                    )
                } else {
                    matrixRepository.sendTextMessage(
                        state.roomId,
                        message
                    )
                }
                _uiState.value = _uiState.value.copy(
                    messageInput = "",
                    isTyping = false,
                    replyingTo = null
                )
                matrixRepository.sendTypingNotification(_uiState.value.roomId, false)
            } catch (e: Exception) {
                _events.send(UiEvent.ShowSnackbar("Ошибка отправки: ${e.message}"))
            }
        }
    }

    fun sendReaction(eventId: String, reaction: String) {
        viewModelScope.launch {
            try {
                matrixRepository.sendReaction(eventId, reaction)
            } catch (e: Exception) {
                _events.send(UiEvent.ShowSnackbar("Ошибка отправки реакции: ${e.message}"))
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
        viewModelScope.launch {
            try {
                matrixRepository.editMessage(
                    eventId = eventId,
                    roomId = _uiState.value.roomId,
                    newText = newText
                )
            } catch (e: Exception) {
                _events.send(UiEvent.ShowSnackbar("Ошибка редактирования: ${e.message}"))
            }
        }
    }

    fun deleteMessage(eventId: String) {
        viewModelScope.launch {
            try {
                matrixRepository.deleteMessage(
                    eventId = eventId,
                    roomId = _uiState.value.roomId
                )
            } catch (e: Exception) {
                _events.send(UiEvent.ShowSnackbar("Ошибка удаления: ${e.message}"))
            }
        }
    }

    fun sendFile(filePath: String, mimeType: String?) {
        viewModelScope.launch {
            try {
                matrixRepository.sendFileMessage(
                    roomId = _uiState.value.roomId,
                    filePath = filePath,
                    mimeType = mimeType.orEmpty()
                )
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
            viewModelScope.launch {
                _events.send(UiEvent.ShowSnackbar("Не удалось начать запись — проверьте микрофон"))
            }
            return
        }
        voiceRecorder = recorder
        _uiState.value = _uiState.value.copy(isRecordingVoice = true, recordingSeconds = 0)
        recordingTimerJob = viewModelScope.launch {
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
        viewModelScope.launch {
            val path = recorder.stop()
            if (path == null) {
                _events.send(UiEvent.ShowSnackbar("Запись не сохранилась"))
                return@launch
            }
            try {
                val mime = if (path.endsWith(".wav")) "audio/wav" else "audio/mp4"
                matrixRepository.sendFileMessage(_uiState.value.roomId, path, mime)
            } catch (e: Exception) {
                _events.send(UiEvent.ShowSnackbar("Ошибка отправки голосового: ${e.message}"))
            }
        }
    }

    fun playAudioMessage(message: Message) {
        viewModelScope.launch {
            try {
                // Both encrypted and plain media resolve to a local file through
                // the authenticated media client.
                val source = matrixRepository.resolveMediaFile(message.eventId, message.body)
                if (source != null) {
                    com.matrix.messenger.platform.playAudio(source)
                } else {
                    _events.send(UiEvent.ShowSnackbar("Не удалось открыть аудио"))
                }
            } catch (e: Exception) {
                _events.send(UiEvent.ShowSnackbar("Ошибка воспроизведения: ${e.message}"))
            }
        }
    }

    fun openAttachment(message: Message) {
        viewModelScope.launch {
            try {
                val path = matrixRepository.resolveMediaFile(message.eventId, message.body)
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
        viewModelScope.launch {
            try {
                matrixRepository.leaveRoom(_uiState.value.roomId)
                _events.send(UiEvent.NavigateBack)
            } catch (e: Exception) {
                _events.send(UiEvent.ShowSnackbar("Ошибка выхода из комнаты: ${e.message}"))
            }
        }
    }

    fun inviteUser(userId: String) {
        val target = userId.trim()
        if (target.isBlank() || !target.startsWith("@")) {
            viewModelScope.launch {
                _events.send(UiEvent.ShowSnackbar("Введите user ID вида @user:server"))
            }
            return
        }
        viewModelScope.launch {
            try {
                matrixRepository.inviteUserToRoom(_uiState.value.roomId, target)
                _events.send(UiEvent.ShowSnackbar("Приглашение отправлено"))
            } catch (e: Exception) {
                _events.send(UiEvent.ShowSnackbar("Не удалось пригласить: ${e.message}"))
            }
        }
    }

    fun createPoll(question: String, answers: List<String>) {
        viewModelScope.launch {
            try {
                matrixRepository.sendPoll(_uiState.value.roomId, question.trim(), answers.map { it.trim() })
            } catch (e: Exception) {
                _events.send(UiEvent.ShowSnackbar("Не удалось создать опрос: ${e.message}"))
            }
        }
    }

    fun votePoll(message: Message, answerIds: List<String>) {
        viewModelScope.launch {
            try {
                matrixRepository.sendPollVote(_uiState.value.roomId, message.eventId, answerIds)
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
        super.onCleared()
        viewModelScope.launch {
            matrixRepository.sendTypingNotification(_uiState.value.roomId, false)
        }
    }
}
