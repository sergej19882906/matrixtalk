package com.matrix.messenger.ui.room

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.matrix.messenger.data.model.RoomMemberInfo
import com.matrix.messenger.data.model.RoomSettingsInfo
import com.matrix.messenger.data.repository.MatrixRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Element X style room settings: name / topic / avatar editing, notification
 * mute toggle, member list with power levels and moderation actions.
 */
class RoomSettingsViewModel(
    private val matrixRepository: MatrixRepository,
    private val roomId: String
) : ViewModel() {

    val settings: StateFlow<RoomSettingsInfo?> =
        matrixRepository.getRoomSettingsFlow(roomId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val members: StateFlow<List<RoomMemberInfo>> =
        matrixRepository.getRoomMembersFlow(roomId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        viewModelScope.launch {
            _isMuted.value = runCatching { matrixRepository.isRoomMuted(roomId) }.getOrDefault(false)
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    fun saveName(name: String) {
        if (name.isBlank()) return
        launchAction("Не удалось переименовать комнату") {
            matrixRepository.setRoomName(roomId, name.trim())
            _message.value = "Название сохранено"
        }
    }

    fun saveTopic(topic: String) {
        launchAction("Не удалось сохранить описание") {
            matrixRepository.setRoomTopic(roomId, topic.trim())
            _message.value = "Описание сохранено"
        }
    }

    fun uploadAvatar(filePath: String, mimeType: String) {
        launchAction("Не удалось загрузить аватар") {
            matrixRepository.setRoomAvatar(roomId, filePath)
            _message.value = "Аватар обновлён"
        }
    }

    fun toggleMuted(muted: Boolean) {
        _isMuted.value = muted
        launchAction("Не удалось изменить уведомления") {
            matrixRepository.setRoomMuted(roomId, muted)
        }
    }

    fun kickUser(userId: String) {
        launchAction("Не удалось исключить участника") {
            matrixRepository.kickUser(roomId, userId)
            _message.value = "Участник исключён"
        }
    }

    fun leaveRoom(onLeft: () -> Unit) {
        launchAction("Не удалось выйти из комнаты") {
            matrixRepository.leaveRoom(roomId)
            onLeft()
        }
    }

    private fun launchAction(errorText: String, block: suspend () -> Unit) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            runCatching { block() }.onFailure { _message.value = "$errorText: ${it.message ?: "ошибка"}" }
            _busy.value = false
        }
    }
}
