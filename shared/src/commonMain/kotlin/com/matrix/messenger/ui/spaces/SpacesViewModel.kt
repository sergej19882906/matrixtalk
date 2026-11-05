package com.matrix.messenger.ui.spaces

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.matrix.messenger.data.model.ChatRoom
import com.matrix.messenger.data.model.PublicRoomItem
import com.matrix.messenger.data.repository.MatrixRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SpacesViewModel(private val matrixRepository: MatrixRepository) : ViewModel() {
    private val _spaces = MutableStateFlow<List<ChatRoom>>(emptyList())
    val spaces = _spaces.asStateFlow()
    private val _childrenCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val childrenCounts = _childrenCounts.asStateFlow()
    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    private var countsJob: Job? = null
    private var observeJob: Job? = null
    init { reload() }
    fun reload() {
        observeJob?.cancel()
        _isLoading.value = true
        _error.value = null
        observeJob = viewModelScope.launch {
            try {
                matrixRepository.getSpacesFlow().collect { spaces ->
                    _spaces.value = spaces
                    _isLoading.value = false
                    countsJob?.cancel()
                    countsJob = viewModelScope.launch {
                        val counts = mutableMapOf<String, Int>()
                        spaces.forEach { space ->
                            try { counts[space.roomId] = matrixRepository.getSpaceChildren(space.roomId).size }
                            catch (e: CancellationException) { throw e }
                            catch (e: Exception) { _error.value = "Не удалось загрузить комнаты: ${e.message}" }
                        }
                        _childrenCounts.value = counts
                    }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _error.value = "Не удалось загрузить пространства: ${e.message}"; _isLoading.value = false }
        }
    }
    fun leaveSpace(spaceId: String, onLeft: () -> Unit) {
        viewModelScope.launch {
            try { matrixRepository.leaveRoom(spaceId); onLeft() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { _error.value = "Не удалось выйти: ${e.message}" }
        }
    }
}

class SpaceDetailViewModel(private val matrixRepository: MatrixRepository, private val spaceId: String) : ViewModel() {
    data class SpaceDetailUiState(
        val name: String? = null,
        val topic: String? = null,
        val avatarUrl: String? = null,
        val membersCount: Int = 0,
        val rooms: List<PublicRoomItem> = emptyList(),
        val childSpaces: List<PublicRoomItem> = emptyList(),
        val isLoading: Boolean = true,
        val isWorking: Boolean = false,
        val error: String? = null
    )
    private val _uiState = MutableStateFlow(SpaceDetailUiState())
    val uiState = _uiState.asStateFlow()
    private val _shareLink = MutableStateFlow<String?>(null)
    val shareLink = _shareLink.asStateFlow()
    private var loadJob: Job? = null
    init { reload() }
    fun reload() {
        loadJob?.cancel()
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        loadJob = viewModelScope.launch {
            try {
                val info = matrixRepository.getRoomInfo(spaceId)
                val children = matrixRepository.getSpaceChildren(spaceId)
                _uiState.value = _uiState.value.copy(
                    name = info?.name, topic = info?.topic, avatarUrl = info?.avatarUrl,
                    membersCount = info?.membersCount ?: 0,
                    rooms = children.filter { !it.isSpace }, childSpaces = children.filter { it.isSpace },
                    isLoading = false
                )
                _shareLink.value = matrixRepository.getRoomShareLink(spaceId)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _uiState.value = _uiState.value.copy(isLoading = false, error = "Не удалось загрузить пространство: ${e.message}") }
        }
    }
    fun joinChild(child: PublicRoomItem, onJoined: (String) -> Unit) {
        if (_uiState.value.isWorking) return
        _uiState.value = _uiState.value.copy(isWorking = true, error = null)
        viewModelScope.launch {
            try { onJoined(matrixRepository.joinRoom(child.roomId)) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { _uiState.value = _uiState.value.copy(error = "Не удалось присоединиться: ${e.message}") }
            finally { _uiState.value = _uiState.value.copy(isWorking = false) }
        }
    }
    fun leaveSpace(onLeft: () -> Unit) {
        if (_uiState.value.isWorking) return
        _uiState.value = _uiState.value.copy(isWorking = true, error = null)
        viewModelScope.launch {
            try { matrixRepository.leaveRoom(spaceId); onLeft() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { _uiState.value = _uiState.value.copy(error = "Не удалось выйти: ${e.message}") }
            finally { _uiState.value = _uiState.value.copy(isWorking = false) }
        }
    }
}
