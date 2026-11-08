package com.matrix.messenger.ui.room

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.matrix.messenger.data.model.RoomMemberInfo
import com.matrix.messenger.data.model.RoomNotificationLevel
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
 * levels, member list with power levels and moderation actions (kick / ban /
 * unban / power level change / invite).
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

    private val _notificationLevel =
        MutableStateFlow(RoomNotificationLevel.ALL_MESSAGES)
    val notificationLevel: StateFlow<RoomNotificationLevel> = _notificationLevel.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        viewModelScope.launch {
            _notificationLevel.value = runCatching {
                matrixRepository.getRoomNotificationLevel(roomId)
            }.getOrDefault(RoomNotificationLevel.ALL_MESSAGES)
        }
        viewModelScope.launch {
            _shareLink.value = runCatching { matrixRepository.getRoomShareLink(roomId) }.getOrNull()
        }
    }

    private val _shareLink = MutableStateFlow<String?>(null)
    val shareLink: StateFlow<String?> = _shareLink.asStateFlow()

    /** Server-side ignored senders (account data "m.ignored_user_list"). */
    val ignoredUsers: StateFlow<Set<String>> =
        matrixRepository.getIgnoredUsersFlow()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    fun ignoreUser(userId: String) {
        launchAction("Не удалось заблокировать пользователя") {
            matrixRepository.ignoreUser(userId)
            _message.value = "Пользователь заблокирован"
        }
    }

    fun unignoreUser(userId: String) {
        launchAction("Не удалось разблокировать пользователя") {
            matrixRepository.unignoreUser(userId)
            _message.value = "Разблокирован"
        }
    }

    fun reportUser(userId: String, reason: String?) {
        launchAction("Не удалось отправить жалобу") {
            matrixRepository.reportUser(userId, reason?.trim()?.takeIf { it.isNotBlank() })
            _message.value = "Жалоба отправлена"
        }
    }

    fun endPoll(pollEventId: String) {
        launchAction("Не удалось завершить опрос") {
            matrixRepository.sendPollEnd(roomId, pollEventId)
            _message.value = "Опрос завершён"
        }
    }

    fun showCopied() {
        _message.value = "Ссылка скопирована"
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

    fun setNotificationLevel(level: RoomNotificationLevel) {
        _notificationLevel.value = level
        launchAction("Не удалось изменить уведомления") {
            matrixRepository.setRoomNotificationLevel(roomId, level)
        }
    }

    fun inviteUser(userId: String) {
        launchAction("Не удалось пригласить пользователя") {
            matrixRepository.inviteUserToRoom(roomId, userId)
            _message.value = "Приглашение отправлено"
        }
    }

    fun kickUser(userId: String) {
        launchAction("Не удалось исключить участника") {
            matrixRepository.kickUser(roomId, userId)
            _message.value = "Участник исключён"
        }
    }

    fun banUser(userId: String) {
        launchAction("Не удалось забанить участника") {
            matrixRepository.banUser(roomId, userId)
            _message.value = "Участник забанен"
        }
    }

    fun unbanUser(userId: String) {
        launchAction("Не удалось разбанить участника") {
            matrixRepository.unbanUser(roomId, userId)
            _message.value = "Участник разбанен"
        }
    }

    fun setUserPowerLevel(userId: String, level: Long) {
        launchAction("Не удалось изменить права") {
            matrixRepository.setUserPowerLevel(roomId, userId, level)
            _message.value = "Права обновлены"
        }
    }

    fun leaveRoom(onLeft: () -> Unit) {
        launchAction("Не удалось выйти из комнаты") {
            matrixRepository.leaveRoom(roomId)
            onLeft()
        }
    }

    /** Dumps the full available room history to a file via the platform save dialog. */
    fun exportHistory(html: Boolean) {
        launchAction("Не удалось экспортировать переписку") {
            val bytes = matrixRepository.exportRoomHistory(roomId, html)
            com.matrix.messenger.platform.exportFile(
                fileName = "matrixtalk-export-" +
                    roomId.removePrefix("!").substringBefore(":") +
                    if (html) ".html" else ".json",
                bytes = bytes
            )
            _message.value = "Экспорт готов"
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
