package com.matrix.messenger.ui.spaces

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.matrix.messenger.data.model.ChatRoom
import com.matrix.messenger.data.model.PublicRoomItem
import com.matrix.messenger.data.model.SpaceChildState
import com.matrix.messenger.data.repository.MatrixRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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
        /** m.space.child order/suggested per room id (tree editor). */
        val childStates: Map<String, SpaceChildState> = emptyMap(),
        val isLoading: Boolean = true,
        val isWorking: Boolean = false,
        val error: String? = null
    )
    private val _uiState = MutableStateFlow(SpaceDetailUiState())
    val uiState = _uiState.asStateFlow()
    private val _shareLink = MutableStateFlow<String?>(null)
    val shareLink = _shareLink.asStateFlow()

    /** Joined rooms that are not yet children of this space (add-room dialog). */
    private val _addCandidates = MutableStateFlow<List<ChatRoom>>(emptyList())
    val addCandidates = _addCandidates.asStateFlow()

    private var loadJob: Job? = null
    init { reload() }
    fun reload() {
        loadJob?.cancel()
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        loadJob = viewModelScope.launch {
            try {
                val info = matrixRepository.getRoomInfo(spaceId)
                val children = matrixRepository.getSpaceChildren(spaceId)
                val childStates = runCatching { matrixRepository.getSpaceChildStates(spaceId) }
                    .getOrDefault(emptyList())
                    .associateBy { it.roomId }
                _uiState.value = _uiState.value.copy(
                    name = info?.name, topic = info?.topic, avatarUrl = info?.avatarUrl,
                    membersCount = info?.membersCount ?: 0,
                    rooms = children.filter { !it.isSpace }, childSpaces = children.filter { it.isSpace },
                    childStates = childStates,
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

    // --- Tree editor (manage mode) ---

    /** Swaps a child with its neighbor in the current display list and persists the new order. */
    fun moveChild(childRoomId: String, up: Boolean) {
        val state = _uiState.value
        val inSpaces = state.childSpaces.any { it.roomId == childRoomId }
        val current = (if (inSpaces) state.childSpaces else state.rooms).map { it.roomId }
        val index = current.indexOf(childRoomId)
        if (index < 0) return
        val target = if (up) index - 1 else index + 1
        if (target < 0 || target >= current.size) return
        val reordered = current.toMutableList().apply { add(target, removeAt(index)) }
        // Persist the full display order: spaces section first, then rooms (as rendered).
        val fullOrder = if (inSpaces) {
            reordered + state.rooms.map { it.roomId }
        } else {
            state.childSpaces.map { it.roomId } + reordered
        }
        launchManaged("Не удалось изменить порядок") {
            matrixRepository.reorderSpaceChildren(spaceId, fullOrder)
            reload()
        }
    }

    /** Persists a drag-reordered display order (spaces section first, then rooms) — same persistence as moveChild. */
    fun persistChildOrder(fullOrder: List<String>) {
        launchManaged("Не удалось изменить порядок") {
            matrixRepository.reorderSpaceChildren(spaceId, fullOrder)
            reload()
        }
    }

    fun toggleChildSuggested(childRoomId: String) {
        val current = _uiState.value.childStates[childRoomId]
        launchManaged("Не удалось изменить статус") {
            matrixRepository.updateSpaceChild(
                spaceId,
                childRoomId,
                order = current?.order,
                suggested = !(current?.suggested ?: false)
            )
            reload()
        }
    }

    fun removeChild(childRoomId: String) {
        launchManaged("Не удалось удалить связь") {
            matrixRepository.removeSpaceChild(spaceId, childRoomId)
            reload()
        }
    }

    /** Fetches joined rooms not already children (the space itself is excluded too). */
    fun loadAddCandidates() {
        viewModelScope.launch {
            try {
                val children = _uiState.value.childSpaces.map { it.roomId } + _uiState.value.rooms.map { it.roomId }
                _addCandidates.value = matrixRepository.getRoomsFlow().first()
                    .filter { it.roomId != spaceId && it.roomId !in children }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _uiState.value = _uiState.value.copy(error = "Не удалось загрузить комнаты: ${e.message}") }
        }
    }

    fun addChild(childRoomId: String) {
        launchManaged("Не удалось добавить комнату") {
            matrixRepository.updateSpaceChild(spaceId, childRoomId, order = null, suggested = false)
            _addCandidates.value = _addCandidates.value.filter { it.roomId != childRoomId }
            reload()
        }
    }

    private fun launchManaged(errorText: String, block: suspend () -> Unit) {
        if (_uiState.value.isWorking) return
        _uiState.value = _uiState.value.copy(isWorking = true, error = null)
        viewModelScope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { _uiState.value = _uiState.value.copy(error = "$errorText: ${e.message}") }
            finally { _uiState.value = _uiState.value.copy(isWorking = false) }
        }
    }
}
