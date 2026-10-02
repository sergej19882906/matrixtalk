package com.matrix.messenger.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.matrix.messenger.data.model.ChatRoom
import com.matrix.messenger.data.model.MatrixUser
import com.matrix.messenger.data.model.UiEvent
import com.matrix.messenger.data.model.UiState
import com.matrix.messenger.data.repository.MatrixRepository
import kotlinx.coroutines.channels.Channel
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
    val searchQuery: String = ""
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

    init {
        observeUser()
        observeRooms()
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

    fun onRoomClick(roomId: String) {
        viewModelScope.launch {
            matrixRepository.markRoomAsRead(roomId)
            _events.send(UiEvent.NavigateToChat(roomId))
        }
    }

    fun createDirectChat(userId: String) {
        viewModelScope.launch {
            try {
                val roomId = matrixRepository.createRoom(
                    name = null,
                    topic = null,
                    isDirect = true,
                    userIds = listOf(userId)
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

    fun uploadAvatar(filePath: String) {
        viewModelScope.launch {
            try {
                matrixRepository.uploadAvatar(filePath)
                _events.send(UiEvent.ShowSnackbar("Аватар обновлён"))
            } catch (e: Exception) {
                _events.send(UiEvent.ShowSnackbar("Ошибка загрузки аватара: ${e.message}"))
            }
        }
    }

    private fun filterRooms(rooms: List<ChatRoom>, query: String): List<ChatRoom> {
        if (query.isBlank()) return rooms.sortedByDescending { it.lastMessage?.timestamp ?: 0 }
        return rooms.filter {
            it.name?.contains(query, ignoreCase = true) == true ||
            it.topic?.contains(query, ignoreCase = true) == true
        }.sortedByDescending { it.lastMessage?.timestamp ?: 0 }
    }
}
