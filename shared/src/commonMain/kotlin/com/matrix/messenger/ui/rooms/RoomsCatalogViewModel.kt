package com.matrix.messenger.ui.rooms

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.matrix.messenger.data.model.PublicRoomItem
import com.matrix.messenger.data.repository.MatrixRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RoomsCatalogUiState(
    val query: String = "",
    val rooms: List<PublicRoomItem> = emptyList(),
    val isLoading: Boolean = false,
    val searched: Boolean = false,
    val error: String? = null,
    val joinedRoomId: String? = null
)

class RoomsCatalogViewModel(
    private val matrixRepository: MatrixRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RoomsCatalogUiState())
    val uiState: StateFlow<RoomsCatalogUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        search()
    }

    fun onQueryChange(query: String) {
        _uiState.value = _uiState.value.copy(query = query)
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(350)
            search()
        }
    }

    fun search() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val rooms = matrixRepository.searchPublicRooms(
                    query = _uiState.value.query.trim().takeIf { it.isNotEmpty() }
                )
                _uiState.value = _uiState.value.copy(
                    rooms = rooms,
                    isLoading = false,
                    searched = true
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    searched = true,
                    error = e.message ?: "Не удалось загрузить каталог"
                )
            }
        }
    }

    fun join(room: PublicRoomItem) {
        viewModelScope.launch {
            try {
                val joinedId = matrixRepository.joinRoom(room.roomId)
                _uiState.value = _uiState.value.copy(joinedRoomId = joinedId)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Не удалось вступить: ${e.message}"
                )
            }
        }
    }

    fun consumeJoined() {
        _uiState.value = _uiState.value.copy(joinedRoomId = null)
    }
}
