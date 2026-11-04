package com.matrix.messenger.ui.spaces

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.matrix.messenger.data.model.ChatRoom
import com.matrix.messenger.data.model.PublicRoomItem
import com.matrix.messenger.data.repository.MatrixRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.launch

/** Element X style spaces overview: all spaces with room counts. */
class SpacesViewModel(
    private val matrixRepository: MatrixRepository
) : ViewModel() {

    private val _spaces = MutableStateFlow<List<ChatRoom>>(emptyList())
    val spaces: StateFlow<List<ChatRoom>> = _spaces.asStateFlow()

    private val _childrenCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val childrenCounts: StateFlow<Map<String, Int>> = _childrenCounts.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        viewModelScope.launch {
            matrixRepository.getSpacesFlow()
                .flowOn(Dispatchers.Default)
                .collect { spaces ->
                    _spaces.value = spaces
                    _isLoading.value = false
                    refreshCounts(spaces)
                }
        }
    }

    private fun refreshCounts(spaces: List<ChatRoom>) {
        viewModelScope.launch {
            val counts = mutableMapOf<String, Int>()
            spaces.forEach { space ->
                runCatching {
                    counts[space.roomId] = matrixRepository.getSpaceChildren(space.roomId).size
                }
            }
            _childrenCounts.value = counts
        }
    }

    fun leaveSpace(spaceId: String, onLeft: () -> Unit) {
        viewModelScope.launch {
            runCatching { matrixRepository.leaveRoom(spaceId) }
            onLeft()
        }
    }
}

/** Single space screen: header, child rooms, nested spaces, actions. */
class SpaceDetailViewModel(
    private val matrixRepository: MatrixRepository,
    private val spaceId: String
) : ViewModel() {

    data class SpaceDetailUiState(
        val name: String? = null,
        val topic: String? = null,
        val avatarUrl: String? = null,
        val membersCount: Int = 0,
        val rooms: List<PublicRoomItem> = emptyList(),
        val childSpaces: List<PublicRoomItem> = emptyList(),
        val isLoading: Boolean = true
    )

    private val _uiState = MutableStateFlow(SpaceDetailUiState())
    val uiState: StateFlow<SpaceDetailUiState> = _uiState.asStateFlow()

    private val _shareLink = MutableStateFlow<String?>(null)
    val shareLink: StateFlow<String?> = _shareLink.asStateFlow()

    init {
        viewModelScope.launch {
            val info = matrixRepository.getRoomInfo(spaceId)
            val children = runCatching {
                matrixRepository.getSpaceChildren(spaceId)
            }.getOrDefault(emptyList())
            _uiState.value = SpaceDetailUiState(
                name = info?.name,
                topic = info?.topic,
                avatarUrl = info?.avatarUrl,
                membersCount = info?.membersCount ?: 0,
                rooms = children.filter { !it.isSpace },
                childSpaces = children.filter { it.isSpace },
                isLoading = false
            )
        }
        viewModelScope.launch {
            _shareLink.value = runCatching { matrixRepository.getRoomShareLink(spaceId) }.getOrNull()
        }
    }

    fun leaveSpace(onLeft: () -> Unit) {
        viewModelScope.launch {
            runCatching { matrixRepository.leaveRoom(spaceId) }
            onLeft()
        }
    }
}
