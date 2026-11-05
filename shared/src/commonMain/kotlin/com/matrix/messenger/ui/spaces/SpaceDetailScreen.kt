package com.matrix.messenger.ui.spaces

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.matrix.messenger.data.model.PublicRoomItem
import com.matrix.messenger.ui.components.MatrixAvatar
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Element X style space detail: header (avatar/name/topic/members), child
 * rooms, nested spaces, share link and leave.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpaceDetailScreen(
    spaceId: String,
    onNavigateBack: () -> Unit,
    onOpenRoom: (String) -> Unit,
    onOpenNestedSpace: (String, String?) -> Unit,
    viewModel: SpaceDetailViewModel = koinViewModel { parametersOf(spaceId) }
) {
    val uiState by viewModel.uiState.collectAsState()
    val shareLink by viewModel.shareLink.collectAsState()
    val addCandidates by viewModel.addCandidates.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var leaving by remember { mutableStateOf(false) }
    var manageMode by remember(spaceId) { mutableStateOf(false) }
    var showAddChildDialog by remember { mutableStateOf(false) }

    var preview by remember(spaceId) { mutableStateOf<PublicRoomItem?>(null) }
    val openChild: (PublicRoomItem) -> Unit = { child ->
        if (child.isJoined) {
            if (child.isSpace) onOpenNestedSpace(child.roomId, child.name) else onOpenRoom(child.roomId)
        } else preview = child
    }
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    LaunchedEffect(uiState.error) {
        uiState.error?.let { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        uiState.name ?: "Пространство",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    if (shareLink != null) {
                        IconButton(onClick = {
                            clipboard.setText(androidx.compose.ui.text.AnnotatedString(shareLink!!))
                        }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Скопировать ссылку")
                        }
                    }
                    // Tree editor toggle (always available for joined spaces).
                    TextButton(onClick = { manageMode = !manageMode }) {
                        Text(if (manageMode) "Готово" else "Управление")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 12.dp)
        ) {
            if (uiState.error != null) {
                item(key = "load_error") {
                    Text(uiState.error!!, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = viewModel::reload) { Text("Повторить загрузку") }
                }
            }
            item(key = "header") {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    MatrixAvatar(
                        name = uiState.name ?: spaceId,
                        avatarUrl = uiState.avatarUrl,
                        size = 84.dp,
                        shape = MaterialTheme.shapes.small
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = uiState.name ?: spaceId,
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center
                    )
                    if (!uiState.topic.isNullOrBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = uiState.topic!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                    Text(
                        text = "${uiState.membersCount} участн. · загружено комнат: ${uiState.rooms.size}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(16.dp))
            }

            if (uiState.childSpaces.isNotEmpty()) {
                item(key = "child_spaces_header") {
                    Text(
                        text = "Подпространства (${uiState.childSpaces.size})",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                }
                items(uiState.childSpaces, key = { "space_${it.roomId}" }) { child ->
                    val index = uiState.childSpaces.indexOfFirst { it.roomId == child.roomId }
                    SpaceChildRow(
                        child = child,
                        onOpen = { openChild(child) },
                        manageMode = manageMode,
                        suggested = uiState.childStates[child.roomId]?.suggested ?: false,
                        canMoveUp = index > 0,
                        canMoveDown = index < uiState.childSpaces.lastIndex,
                        onMoveUp = { viewModel.moveChild(child.roomId, up = true) },
                        onMoveDown = { viewModel.moveChild(child.roomId, up = false) },
                        onToggleSuggested = { viewModel.toggleChildSuggested(child.roomId) },
                        onRemove = { viewModel.removeChild(child.roomId) }
                    )
                }
                item(key = "rooms_header_gap") { Spacer(Modifier.height(12.dp)) }
            }

            item(key = "rooms_header") {
                Text(
                    text = "Комнаты (${uiState.rooms.size})",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
            }
            items(uiState.rooms, key = { it.roomId }) { child ->
                val index = uiState.rooms.indexOfFirst { it.roomId == child.roomId }
                SpaceChildRow(
                    child = child,
                    onOpen = { openChild(child) },
                    manageMode = manageMode,
                    suggested = uiState.childStates[child.roomId]?.suggested ?: false,
                    canMoveUp = index > 0,
                    canMoveDown = index < uiState.rooms.lastIndex,
                    onMoveUp = { viewModel.moveChild(child.roomId, up = true) },
                    onMoveDown = { viewModel.moveChild(child.roomId, up = false) },
                    onToggleSuggested = { viewModel.toggleChildSuggested(child.roomId) },
                    onRemove = { viewModel.removeChild(child.roomId) }
                )
            }
            if (uiState.rooms.isEmpty()) {
                item(key = "rooms_empty") {
                    Text(
                        text = "В этом пространстве пока нет комнат",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }
            if (manageMode) {
                item(key = "add_child") {
                    TextButton(onClick = {
                        viewModel.loadAddCandidates()
                        showAddChildDialog = true
                    }) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Добавить комнату")
                    }
                }
            }

            item(key = "leave") {
                Spacer(Modifier.height(24.dp))
                TextButton(
                    onClick = { leaving = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.ExitToApp, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Выйти из пространства")
                }
                if (shareLink == null) {
                    Text(
                        text = "Ссылка недоступна: у пространства нет публичного адреса.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        textAlign = TextAlign.Center
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Link,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "Ссылка скопирована в буфер при нажатии иконки выше",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    preview?.let { child ->
        AlertDialog(
            onDismissRequest = { if (!uiState.isWorking) preview = null },
            title = { Text(child.name ?: child.roomId) },
            text = {
                Column {
                    Text(child.topic ?: "Описание отсутствует")
                    Text("${child.joinedMembers} участн. · ${child.joinRule}")
                    if (!child.roomTypeKnown) Text("Тип комнаты не определён сервером.")
                    uiState.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(enabled = !uiState.isWorking, onClick = {
                    viewModel.joinChild(child) { joinedId ->
                        preview = null
                        if (child.isSpace) onOpenNestedSpace(joinedId, child.name) else onOpenRoom(joinedId)
                    }
                }) { Text(if (uiState.isWorking) "Присоединение…" else "Присоединиться") }
            },
            dismissButton = { TextButton(enabled = !uiState.isWorking, onClick = { preview = null }) { Text("Отмена") } }
        )
    }

    if (leaving) {
        AlertDialog(
            onDismissRequest = { leaving = false },
            title = { Text("Выйти из пространства") },
            text = { Text("Покинуть «${uiState.name ?: spaceId}»?") },
            confirmButton = {
                TextButton(onClick = {
                    leaving = false
                    viewModel.leaveSpace(onLeft = onNavigateBack)
                }) { Text("Выйти", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { leaving = false }) { Text("Отмена") }
            }
        )
    }

    // Manage mode: add an existing joined room as a space child.
    if (showAddChildDialog) {
        AlertDialog(
            onDismissRequest = { showAddChildDialog = false },
            title = { Text("Добавить комнату") },
            text = {
                if (addCandidates.isEmpty()) {
                    Text(
                        text = "Нет подходящих комнат — все уже добавлены",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp)
                    ) {
                        items(
                            items = addCandidates,
                            key = { it.roomId }
                        ) { room ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showAddChildDialog = false
                                        viewModel.addChild(room.roomId)
                                    }
                                    .padding(vertical = 8.dp)
                            ) {
                                MatrixAvatar(
                                    name = room.name ?: room.roomId,
                                    avatarUrl = room.avatarUrl,
                                    size = 32.dp,
                                    shape = MaterialTheme.shapes.small
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = room.name ?: room.roomId,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAddChildDialog = false }) { Text("Отмена") }
            }
        )
    }
}

@Composable
private fun SpaceChildRow(
    child: PublicRoomItem,
    onOpen: () -> Unit,
    manageMode: Boolean = false,
    suggested: Boolean = false,
    canMoveUp: Boolean = false,
    canMoveDown: Boolean = false,
    onMoveUp: () -> Unit = {},
    onMoveDown: () -> Unit = {},
    onToggleSuggested: () -> Unit = {},
    onRemove: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MatrixAvatar(
            name = child.name ?: child.roomId,
            avatarUrl = child.avatarUrl,
            size = 40.dp,
            shape = MaterialTheme.shapes.small
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = child.name ?: child.roomId,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!child.topic.isNullOrBlank()) {
                Text(
                    text = child.topic,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = "${child.joinedMembers} участн. · ${child.joinRule}" +
                    if (manageMode && suggested) " · рекомендуется" else "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (manageMode) {
            IconButton(onClick = onMoveUp, enabled = canMoveUp, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.KeyboardArrowUp,
                    contentDescription = "Выше",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
            IconButton(onClick = onMoveDown, enabled = canMoveDown, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = "Ниже",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
            IconButton(onClick = onToggleSuggested, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.School,
                    contentDescription = if (suggested) "Убрать рекомендацию" else "Рекомендовать",
                    tint = if (suggested) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
            IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Удалить связь",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
