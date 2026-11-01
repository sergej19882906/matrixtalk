package com.matrix.messenger.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.matrix.messenger.data.model.ChatRoom
import com.matrix.messenger.data.model.MatrixUser
import com.matrix.messenger.data.model.UiEvent
import com.matrix.messenger.data.model.UiState
import com.matrix.messenger.data.repository.MatrixRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

data class HomeUiState(
    val currentUser: MatrixUser? = null,
    val rooms: List<ChatRoom> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val searchQuery: String = "",
    val contactSearchQuery: String = "",
    val contacts: List<MatrixUser> = emptyList(),
    val isSearchingContacts: Boolean = false,
    val contactSearchError: String? = null,
    val invites: List<ChatRoom> = emptyList(),
    val spaces: List<ChatRoom> = emptyList()
)

class HomeViewModel(
    private val matrixRepository: MatrixRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _uiStateSealed = MutableStateFlow<UiState<List<ChatRoom>>>(UiState.Loading)
    val uiStateSealed: StateFlow<UiState<List<ChatRoom>>> = _uiStateSealed.asStateFlow()

    private val _events = Channel<UiEvent>()
    val events = _events.receiveAsFlow()
    private var contactSearchJob: Job? = null

    init {
        observeUser()
        observeRooms()
        viewModelScope.launch {
            matrixRepository.getInvitesFlow().collect { invites ->
                _uiState.value = _uiState.value.copy(invites = invites)
            }
        }
        viewModelScope.launch {
            matrixRepository.getSpacesFlow().collect { spaces ->
                _uiState.value = _uiState.value.copy(spaces = spaces)
            }
        }
    }

    fun acceptInvite(roomId: String) {
        viewModelScope.launch {
            try {
                matrixRepository.joinRoom(roomId)
            } catch (e: Exception) {
                _events.send(UiEvent.ShowSnackbar("Не удалось принять приглашение: ${e.message}"))
            }
        }
    }

    fun declineInvite(roomId: String) {
        viewModelScope.launch {
            try {
                matrixRepository.leaveRoom(roomId)
            } catch (e: Exception) {
                _events.send(UiEvent.ShowSnackbar("Не удалось отклонить приглашение: ${e.message}"))
            }
        }
    }

    private fun observeUser() {
        viewModelScope.launch {
            matrixRepository.currentUser.collect { user ->
                _uiState.value = _uiState.value.copy(currentUser = user)
            }
        }
    }

    private fun observeRooms() {
        viewModelScope.launch {
            matrixRepository.getRoomsFlow().collect { rooms ->
                val filteredRooms = filterRooms(rooms, _uiState.value.searchQuery)
                _uiState.value = _uiState.value.copy(
                    rooms = filteredRooms,
                    isLoading = false
                )
                _uiStateSealed.value = UiState.Success(filteredRooms)
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        viewModelScope.launch {
            val rooms = try {
                matrixRepository.getRoomsFlow().first()
            } catch (e: Exception) {
                emptyList()
            }
            val filteredRooms = filterRooms(rooms, query)
            _uiState.value = _uiState.value.copy(rooms = filteredRooms)
        }
    }

    fun onContactSearchQueryChange(query: String) {
        _uiState.value = _uiState.value.copy(
            contactSearchQuery = query,
            contacts = emptyList(),
            isSearchingContacts = query.trim().length >= 2,
            contactSearchError = null
        )
        contactSearchJob?.cancel()
        if (query.trim().length < 2) {
            _uiState.value = _uiState.value.copy(isSearchingContacts = false)
            return
        }
        contactSearchJob = viewModelScope.launch {
            delay(350)
            try {
                val contacts = matrixRepository.searchUsers(query)
                currentCoroutineContext().ensureActive()
                _uiState.value = _uiState.value.copy(
                    contacts = contacts,
                    isSearchingContacts = false
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSearchingContacts = false,
                    contactSearchError = e.message ?: "Не удалось выполнить поиск контактов"
                )
            }
        }
    }

    fun onContactClick(userId: String) {
        createDirectChat(userId)
    }

    fun onRoomClick(roomId: String) {
        viewModelScope.launch {
            matrixRepository.markRoomAsRead(roomId)
            _events.send(UiEvent.NavigateToChat(roomId))
        }
    }

    fun createGroupChat(name: String, userIds: List<String>, isEncrypted: Boolean) {
        viewModelScope.launch {
            try {
                val roomId = matrixRepository.createRoom(
                    name = name.takeIf { it.isNotBlank() },
                    topic = null,
                    isDirect = false,
                    userIds = userIds,
                    isEncrypted = isEncrypted
                )
                _events.send(UiEvent.NavigateToChat(roomId))
            } catch (e: Exception) {
                _events.send(UiEvent.ShowSnackbar("Ошибка создания чата: ${e.message}"))
            }
        }
    }

    fun createDirectChat(userId: String) {
        viewModelScope.launch {
            try {
                val existingRoom = matrixRepository.getRoomsFlow().first()
                    .firstOrNull { room ->
                        room.isDirect && matrixRepository.resolveDirectChatPeerId(room.roomId) == userId
                    }
                // Element-style: direct chats are always encrypted
                val roomId = existingRoom?.roomId ?: matrixRepository.createRoom(
                    name = null,
                    topic = null,
                    isDirect = true,
                    userIds = listOf(userId),
                    isEncrypted = true
                )
                _events.send(UiEvent.NavigateToChat(roomId))
            } catch (e: Exception) {
                _events.send(UiEvent.ShowSnackbar("Ошибка создания чата: ${e.message}"))
            }
        }
    }

    fun joinRoom(aliasOrId: String) {
        viewModelScope.launch {
            try {
                val roomId = matrixRepository.joinRoom(aliasOrId)
                _events.send(UiEvent.NavigateToChat(roomId))
            } catch (e: Exception) {
                _events.send(UiEvent.ShowSnackbar("Ошибка входа в комнату: ${e.message}"))
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            matrixRepository.logout()
            _events.send(UiEvent.NavigateToLogin)
        }
    }

    fun setDisplayName(name: String) {
        viewModelScope.launch {
            try {
                matrixRepository.setDisplayName(name)
                _events.send(UiEvent.ShowSnackbar("Имя профиля обновлено"))
            } catch (e: Exception) {
                _events.send(UiEvent.ShowSnackbar("Ошибка изменения имени: ${e.message}"))
            }
        }
    }

    fun uploadAvatar(filePath: String, mimeType: String) {
        viewModelScope.launch {
            try {
                matrixRepository.uploadAvatar(filePath, mimeType)
                _events.send(UiEvent.ShowSnackbar("Аватар обновлён"))
            } catch (e: Exception) {
                _events.send(UiEvent.ShowSnackbar("Ошибка загрузки аватара: ${e.message}"))
            }
        }
    }

    fun leaveRoom(roomId: String) {
        viewModelScope.launch {
            try {
                matrixRepository.leaveRoom(roomId)
                _events.send(UiEvent.ShowSnackbar("Чат удалён из списка"))
            } catch (e: Exception) {
                _events.send(UiEvent.ShowSnackbar("Ошибка удаления чата: ${e.message}"))
            }
        }
    }

    private fun filterRooms(rooms: List<ChatRoom>, query: String): List<ChatRoom> {
        val sorted = rooms.sortedWith(
            compareByDescending<ChatRoom> { it.isFavorite }
                .thenByDescending { it.lastMessage?.timestamp ?: 0 }
        )
        if (query.isBlank()) return sorted
        return sorted.filter {
            it.name?.contains(query, ignoreCase = true) == true ||
            it.topic?.contains(query, ignoreCase = true) == true
        }
    }

    fun toggleFavorite(roomId: String) {
        viewModelScope.launch {
            try {
                matrixRepository.toggleFavorite(roomId)
            } catch (e: Exception) {
                _events.send(UiEvent.ShowSnackbar("Не удалось изменить избранное: ${e.message}"))
            }
        }
    }
}
