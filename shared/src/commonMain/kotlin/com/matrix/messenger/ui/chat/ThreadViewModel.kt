package com.matrix.messenger.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.matrix.messenger.data.model.Message
import com.matrix.messenger.data.repository.MatrixRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

/**
 * Element X style thread view: the root message plus all replies belonging to
 * the m.thread relation, with the composer sending into the thread.
 */
class ThreadViewModel(
    private val matrixRepository: MatrixRepository,
    private val roomId: String,
    private val rootId: String
) : ViewModel() {

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _root = MutableStateFlow<Message?>(null)
    val root: StateFlow<Message?> = _root.asStateFlow()

    private val _messageInput = MutableStateFlow("")
    val messageInput: StateFlow<String> = _messageInput.asStateFlow()

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    init {
        viewModelScope.launch {
            matrixRepository.getMessagesFlow(roomId).flowOn(Dispatchers.Default).collect { all ->
                val inThread = all.filter { it.threadRootId == rootId && !it.isDeleted }.sortedBy { it.timestamp }
                _messages.value = inThread
                _root.value = all.firstOrNull { it.eventId == rootId }
            }
        }
    }

    fun setMessageInput(value: String) {
        _messageInput.value = value
    }

    fun sendReply() {
        val text = _messageInput.value.trim()
        if (text.isEmpty() || _isSending.value) return
        viewModelScope.launch {
            _isSending.value = true
            try {
                matrixRepository.replyToMessage(
                    roomId = roomId,
                    eventId = rootId,
                    text = text,
                    threadRootId = rootId
                )
                _messageInput.value = ""
            } finally {
                _isSending.value = false
            }
        }
    }
}
