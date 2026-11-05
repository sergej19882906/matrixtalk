package com.matrix.messenger.ui.room

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import com.matrix.messenger.data.model.Message
import com.matrix.messenger.data.model.MessageType
import com.matrix.messenger.data.model.RoomMediaItem
import com.matrix.messenger.data.repository.MatrixRepository
import com.matrix.messenger.media.MatrixMedia
import com.matrix.messenger.ui.components.ImageViewer
import com.matrix.messenger.ui.components.TimeFormat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Element X style «Файлы и медиа» panel: media grid (images/videos) and file
 * list, paginated newest-first from the room's media history.
 */
class RoomMediaViewModel(
    private val matrixRepository: MatrixRepository,
    private val roomId: String
) : ViewModel() {

    data class RoomMediaUiState(
        val mediaItems: List<RoomMediaItem> = emptyList(),
        val fileItems: List<RoomMediaItem> = emptyList(),
        val isLoading: Boolean = true,
        val isLoadingMore: Boolean = false,
        val canLoadMore: Boolean = false,
        val error: String? = null
    )

    private val _uiState = MutableStateFlow(RoomMediaUiState())
    val uiState = _uiState.asStateFlow()

    private var nextFrom: String? = null

    init {
        loadFirstPage()
    }

    fun loadFirstPage() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true, error = null)
                val page = matrixRepository.getRoomMediaPage(roomId, null)
                nextFrom = page.nextFrom
                _uiState.value = RoomMediaUiState(
                    mediaItems = page.items.filter { isMedia(it) },
                    fileItems = page.items.filter { !isMedia(it) },
                    isLoading = false,
                    canLoadMore = page.nextFrom != null
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Не удалось загрузить вложения: ${e.message ?: "неизвестная ошибка"}"
                )
            }
        }
    }

    fun loadMore() {
        val from = nextFrom ?: return
        val state = _uiState.value
        if (state.isLoading || state.isLoadingMore) return
        viewModelScope.launch {
            try {
                _uiState.value = state.copy(isLoadingMore = true)
                val page = matrixRepository.getRoomMediaPage(roomId, from)
                nextFrom = page.nextFrom
                _uiState.value = _uiState.value.copy(
                    mediaItems = _uiState.value.mediaItems + page.items.filter { isMedia(it) },
                    fileItems = _uiState.value.fileItems + page.items.filter { !isMedia(it) },
                    isLoadingMore = false,
                    canLoadMore = page.nextFrom != null
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoadingMore = false,
                    error = "Не удалось загрузить ещё вложения: ${e.message ?: "неизвестная ошибка"}"
                )
            }
        }
    }

    /** Images and videos go to the grid, everything else to the file list. */
    private fun isMedia(item: RoomMediaItem): Boolean =
        item.mimeType?.startsWith("image/") == true || item.mimeType?.startsWith("video/") == true
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomMediaScreen(
    roomId: String,
    onNavigateBack: () -> Unit,
    viewModel: RoomMediaViewModel = koinViewModel { parametersOf(roomId) }
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    var currentTab by remember { mutableStateOf(0) } // 0 = Медиа, 1 = Файлы
    var viewerStartId by remember { mutableStateOf<String?>(null) }

    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()

    // Infinite scroll: load the next page when the end of the list approaches.
    LaunchedEffect(currentTab) {
        snapshotFlow {
            if (currentTab == 0) {
                (gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1) to
                    gridState.layoutInfo.totalItemsCount
            } else {
                (listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1) to
                    listState.layoutInfo.totalItemsCount
            }
        }.collect { (lastVisible, total) ->
            val state = viewModel.uiState.value
            if (total > 0 && lastVisible >= total - 4 && state.canLoadMore && !state.isLoadingMore) {
                viewModel.loadMore()
            }
        }
    }

    // Images are viewable through the shared fullscreen viewer (Message-backed).
    val viewerImages = remember(uiState.mediaItems) {
        uiState.mediaItems
            .filter { it.mimeType?.startsWith("image/") == true }
            .map { it.toViewerMessage() }
    }
    viewerStartId?.let { startId ->
        if (viewerImages.isNotEmpty()) {
            ImageViewer(
                images = viewerImages,
                startMessageId = startId,
                onDismiss = { viewerStartId = null },
                onDownload = { _ ->
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("Скачивание файлов в панели — скоро")
                    }
                }
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Файлы и медиа") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = currentTab == 0,
                    onClick = { currentTab = 0 },
                    label = { Text("Медиа") }
                )
                FilterChip(
                    selected = currentTab == 1,
                    onClick = { currentTab = 1 },
                    label = { Text("Файлы") }
                )
            }

            if (uiState.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@Column
            }

            uiState.error?.let { error ->
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(error, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = viewModel::loadFirstPage) { Text("Повторить") }
                }
            }

            when (currentTab) {
                0 -> {
                    if (uiState.mediaItems.isEmpty()) {
                        EmptyMediaHint()
                    } else {
                        LazyVerticalGrid(
                            state = gridState,
                            columns = GridCells.Fixed(3),
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)
                        ) {
                            gridItems(
                                items = uiState.mediaItems,
                                key = { it.eventId }
                            ) { item ->
                                if (item.mimeType?.startsWith("video/") == true) {
                                    Box(
                                        modifier = Modifier
                                            .aspectRatio(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                            .clickable {
                                                coroutineScope.launch {
                                                    snackbarHostState.showSnackbar("Открытие видео в панели — скоро")
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.Videocam,
                                            contentDescription = item.body,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }
                                } else {
                                    AsyncImage(
                                        model = MatrixMedia(item.url, item.encryptedFile),
                                        contentDescription = item.body,
                                        modifier = Modifier
                                            .aspectRatio(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { viewerStartId = item.eventId },
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            }
                            if (uiState.isLoadingMore) {
                                items(
                                    count = 1,
                                    key = { "loading_more" }
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                    }
                                }
                            }
                        }
                    }
                }
                else -> {
                    if (uiState.fileItems.isEmpty()) {
                        EmptyMediaHint()
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                horizontal = 16.dp, vertical = 8.dp
                            )
                        ) {
                            items(
                                items = uiState.fileItems,
                                key = { it.eventId }
                            ) { item ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar("Открытие файлов в панели — скоро")
                                            }
                                        }
                                        .padding(vertical = 8.dp)
                                ) {
                                    Icon(
                                        if (item.isVoice) Icons.Default.Mic else Icons.Default.AttachFile,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.body.ifBlank { "Файл" },
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = (item.senderName?.substringBefore(":") ?: item.senderId) +
                                                " · " + TimeFormat.listLabel(item.timestamp),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                            if (uiState.isLoadingMore) {
                                item(key = "loading_more") {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyMediaHint() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "Пока нет вложений",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Adapts a panel entry to the shared fullscreen image viewer. */
private fun RoomMediaItem.toViewerMessage(): Message = Message(
    eventId = eventId,
    senderId = senderId,
    senderName = senderName,
    body = body,
    timestamp = timestamp,
    messageType = MessageType.IMAGE,
    isMine = false,
    isEdited = false,
    isDeleted = false,
    mediaUrl = url,
    encryptedFile = encryptedFile
)
