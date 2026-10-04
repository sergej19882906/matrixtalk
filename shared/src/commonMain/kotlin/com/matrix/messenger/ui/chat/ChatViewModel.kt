package com.matrix.messenger.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.matrix.messenger.data.model.Message
import com.matrix.messenger.data.model.UiEvent
import com.matrix.messenger.data.model.UiState
import com.matrix.messenger.data.repository.MatrixRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

data class ChatUiState(
    val roomId: String = "",
    val roomName: String? = null,
    val messages: List<Message> = emptyList(),
    val messageInput: String = "",
    val isLoading: Boolean = true,
    val canLoadMore: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isTyping: Boolean = false,
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
            _uiState.value = _uiState.value.copy(roomName = roomInfo?.name)
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
            val message = _uiState.value.messageInput.trim()
            if (message.isBlank()) return@launch

            try {
                matrixRepository.sendTextMessage(
                    _uiState.value.roomId,
                    message
                )
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
                _events.send(UiEvent.ShowSnackbar("Ошибка отправки файла: ${e.message}"))
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

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch {
            matrixRepository.sendTypingNotification(_uiState.value.roomId, false)
        }
    }
}
