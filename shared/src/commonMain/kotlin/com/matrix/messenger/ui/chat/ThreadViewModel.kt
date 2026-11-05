package com.matrix.messenger.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.matrix.messenger.data.model.Message
import com.matrix.messenger.data.model.ReadReceipt
import com.matrix.messenger.data.model.UiEvent
import com.matrix.messenger.data.repository.MatrixRepository
import com.matrix.messenger.platform.VoicePlayback
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** Thread replies currently loaded in the room timeline; not a relation-history paginator. */
class ThreadViewModel(
    private val matrixRepository: MatrixRepository,
    private val roomId: String,
    private val rootId: String
) : ViewModel() {
    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()
    private val _root = MutableStateFlow<Message?>(null)
    val root: StateFlow<Message?> = _root.asStateFlow()
    private val _rootLoading = MutableStateFlow(true)
    val rootLoading = _rootLoading.asStateFlow()
    private val _messageInput = MutableStateFlow("")
    val messageInput = _messageInput.asStateFlow()
    private val _isSending = MutableStateFlow(false)
    val isSending = _isSending.asStateFlow()
    private val _pinned = MutableStateFlow<List<Message>>(emptyList())
    val pinned = _pinned.asStateFlow()
    private val _receipts = MutableStateFlow<Map<String, List<ReadReceipt>>>(emptyMap())
    val receipts = _receipts.asStateFlow()
    private val _events = Channel<UiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()
    val currentUser = matrixRepository.currentUser
    val voicePlayback = VoicePlayback.state

    init {
        action("Не удалось загрузить ответы") {
            matrixRepository.getMessagesFlow(roomId).flowOn(Dispatchers.Default).collect { all ->
                _messages.value = all.filter { it.threadRootId == rootId && it.eventId != rootId && !it.isDeleted }
                    .sortedBy { it.timestamp }
                all.firstOrNull { it.eventId == rootId }?.let { _root.value = it }
            }
        }
        loadRoot()
        action("Не удалось загрузить закреплённые сообщения") {
            matrixRepository.getPinnedMessagesFlow(roomId).collect { _pinned.value = it }
        }
        action("Не удалось загрузить отметки прочтения") {
            matrixRepository.getReadReceiptsFlow(roomId).collect { _receipts.value = it }
        }
    }

    fun loadRoot() {
        _rootLoading.value = true
        action("Не удалось загрузить исходное сообщение") {
            try { matrixRepository.getMessage(roomId, rootId)?.let { _root.value = it } }
            finally { _rootLoading.value = false }
        }
    }

    private fun action(error: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { _events.send(UiEvent.ShowSnackbar("$error: ${e.message ?: "неизвестная ошибка"}")) }
        }
    }

    fun setMessageInput(value: String) { _messageInput.value = value }
    fun sendReply() {
        val input = _messageInput.value
        val text = input.trim()
        if (text.isEmpty() || _isSending.value) return
        _isSending.value = true
        action("Не удалось отправить ответ") {
            try {
                matrixRepository.replyToMessage(roomId, rootId, text, threadRootId = rootId)
                if (_messageInput.value == input) _messageInput.value = ""
            } finally { _isSending.value = false }
        }
    }

    fun toggleReaction(message: Message, emoji: String) = action("Ошибка реакции") {
        if (emoji in message.myReactions) matrixRepository.unreact(roomId, message.eventId, emoji)
        else matrixRepository.sendReaction(message.eventId, emoji)
    }
    fun togglePin(message: Message) = action("Не удалось изменить закреп") {
        matrixRepository.setPinnedMessage(roomId, message.eventId, !_pinned.value.any { it.eventId == message.eventId })
    }
    fun edit(message: Message, text: String) = action("Ошибка редактирования") {
        matrixRepository.editMessage(message.eventId, roomId, text)
    }
    fun delete(message: Message) = action("Ошибка удаления") {
        matrixRepository.deleteMessage(message.eventId, roomId)
    }
    fun vote(message: Message, answers: List<String>) = action("Ошибка голосования") {
        matrixRepository.sendPollVote(roomId, message.eventId, answers)
    }
    fun endPoll(message: Message) = action("Не удалось завершить опрос") {
        matrixRepository.sendPollEnd(roomId, message.eventId)
    }
    fun openAttachment(message: Message) = action("Не удалось открыть вложение") {
        val path = matrixRepository.resolveMediaFile(message.eventId, message.body, roomId)
            ?: error("Файл недоступен")
        com.matrix.messenger.platform.openLocalFile(path)
    }
    fun playAudio(message: Message) = action("Не удалось воспроизвести аудио") {
        val path = matrixRepository.resolveMediaFile(message.eventId, message.body, roomId)
            ?: error("Файл недоступен")
        VoicePlayback.toggle(message.eventId, path)
    }
    fun seekVoice(fraction: Float) { VoicePlayback.seekTo(fraction) }
}
