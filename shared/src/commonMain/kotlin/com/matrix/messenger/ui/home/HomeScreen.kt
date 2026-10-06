package com.matrix.messenger.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.CircleShape
import com.matrix.messenger.data.model.ChatRoom
import com.matrix.messenger.data.model.MatrixUser
import com.matrix.messenger.data.model.UiEvent
import com.matrix.messenger.platform.rememberFilePicker
import coil3.compose.AsyncImage
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onRoomClick: (String) -> Unit,
    onLogout: () -> Unit,
    onCreateChat: () -> Unit,
    onBridges: () -> Unit,
    viewModel: HomeViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var profileDialogVisible by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(0) }
    var roomToLeave by remember { mutableStateOf<ChatRoom?>(null) }
    var displayName by remember(uiState.currentUser?.displayName) {
        mutableStateOf(uiState.currentUser?.displayName.orEmpty())
    }
    val pickAvatar = rememberFilePicker(listOf("image/*")) { file ->
        viewModel.uploadAvatar(file.path, file.mimeType)
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is UiEvent.NavigateToChat -> onRoomClick(event.roomId)
                is UiEvent.NavigateToLogin -> onLogout()
                is UiEvent.ShowSnackbar -> snackbarHostState.showSnackbar(event.message)
                else -> {}
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (selectedTab == 0) "Чаты" else "Контакты") },
                actions = {
                    IconButton(onClick = { profileDialogVisible = true }) {
                        Icon(Icons.Default.AccountCircle, contentDescription = "Профиль")
                    }
                    IconButton(onClick = onCreateChat) {
                        Icon(Icons.Default.Add, contentDescription = "Создать чат")
                    }
                    IconButton(onClick = onBridges) {
                        Icon(Icons.Default.Link, contentDescription = "Мосты")
                    }
                    IconButton(onClick = viewModel::logout) {
                        Icon(Icons.Default.Logout, contentDescription = "Выйти")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.ChatBubbleOutline, contentDescription = null) },
                    label = { Text("Чаты") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.People, contentDescription = null) },
                    label = { Text("Контакты") }
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            when (selectedTab) {
                0 -> {
                    OutlinedTextField(
                        value = uiState.searchQuery,
                        onValueChange = viewModel::onSearchQueryChange,
                        placeholder = { Text("Поиск чатов...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        singleLine = true
                    )
                    when {
                        uiState.isLoading -> {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                        uiState.rooms.isEmpty() -> {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Default.ChatBubbleOutline,
                                        contentDescription = null,
                                        modifier = Modifier.size(64.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = if (uiState.searchQuery.isBlank()) "Нет чатов" else "Чаты не найдены",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        else -> {
                            LazyColumn {
                                items(uiState.rooms, key = { it.roomId }) { room ->
                                    RoomItem(
                                        room = room,
                                        onClick = { viewModel.onRoomClick(room.roomId) },
                                        onLeave = { roomToLeave = room }
                                    )
                                }
                            }
                        }
                    }
                }
                else -> {
                    OutlinedTextField(
                        value = uiState.contactSearchQuery,
                        onValueChange = viewModel::onContactSearchQueryChange,
                        placeholder = { Text("Имя или Matrix ID") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        singleLine = true
                    )
                    when {
                        uiState.contactSearchQuery.trim().length < 2 -> ContactSearchMessage(
                            text = "Введите не менее 2 символов для поиска"
                        )
                        uiState.isSearchingContacts -> {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                        uiState.contactSearchError != null -> ContactSearchMessage(
                            text = uiState.contactSearchError ?: "Не удалось выполнить поиск"
                        )
                        uiState.contacts.isEmpty() -> ContactSearchMessage(text = "Контакты не найдены")
                        else -> LazyColumn {
                            items(uiState.contacts, key = { it.userId }) { user ->
                                ContactItem(
                                    user = user,
                                    onClick = { viewModel.onContactClick(user.userId) }
                                )
                            }
                        }
                    }
                }
            }

        }
    }

    if (profileDialogVisible) {
        AlertDialog(
            onDismissRequest = { profileDialogVisible = false },
            title = { Text("Профиль") },
            text = {
                Column {
                    uiState.currentUser?.avatarUrl?.let { avatarUrl ->
                        AsyncImage(
                            model = avatarUrl,
                            contentDescription = "Аватар профиля",
                            modifier = Modifier.size(72.dp).clip(CircleShape)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    Text(
                        text = uiState.currentUser?.userId.orEmpty(),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = displayName,
                        onValueChange = { displayName = it },
                        label = { Text("Display name") },
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = pickAvatar
                    ) {
                        Icon(Icons.Default.AccountCircle, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Загрузить аватар")
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.setDisplayName(displayName)
                        profileDialogVisible = false
                    },
                    enabled = displayName.isNotBlank()
                ) {
                    Text("Сохранить")
                }
            },
            dismissButton = {
                TextButton(onClick = { profileDialogVisible = false }) {
                    Text("Отмена")
                }
            }
        )
    }

    roomToLeave?.let { room ->
        AlertDialog(
            onDismissRequest = { roomToLeave = null },
            title = { Text("Удалить чат?") },
            text = { Text("Вы выйдете из комнаты «${room.name ?: "Без названия"}». Она исчезнет из списка чатов.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.leaveRoom(room.roomId)
                        roomToLeave = null
                    }
                ) {
                    Text("Выйти и удалить")
                }
            },
            dismissButton = {
                TextButton(onClick = { roomToLeave = null }) {
                    Text("Отмена")
                }
            }
        )
    }
}

@Composable
private fun ContactSearchMessage(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            modifier = Modifier.padding(24.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ContactItem(user: MatrixUser, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                if (user.avatarUrl != null) {
                    AsyncImage(
                        model = user.avatarUrl,
                        contentDescription = "Аватар ${user.displayName ?: user.userId}",
                        modifier = Modifier.fillMaxSize().clip(MaterialTheme.shapes.medium)
                    )
                } else {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = (user.displayName ?: user.userId).take(1).uppercase(),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = user.displayName ?: user.userId,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (user.displayName != null) {
                    Text(
                        text = user.userId,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun RoomItem(
    room: ChatRoom,
    onClick: () -> Unit,
    onLeave: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar placeholder — Coil 3 async image will be added per platform
            Surface(
                modifier = Modifier.size(56.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                if (room.avatarUrl != null) {
                    AsyncImage(
                        model = room.avatarUrl,
                        contentDescription = "Аватар ${room.name ?: "чата"}",
                        modifier = Modifier.fillMaxSize().clip(MaterialTheme.shapes.medium)
                    )
                } else {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = (room.name?.take(1)?.uppercase() ?: "?"),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = room.name ?: "Без названия",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    room.lastMessage?.timestamp?.let { ts ->
                        Text(
                            text = formatTime(ts),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = room.lastMessage?.let { msg ->
                            "${msg.senderName?.substringBefore(":") ?: "Unknown"}: ${msg.body}"
                        } ?: "Нет сообщений",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (room.unreadCount > 0) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Text(
                                text = room.unreadCount.toString(),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Действия с чатом")
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Выйти и удалить чат") },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onLeave()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatTime(timestamp: Long): String {
    val totalSeconds = timestamp / 1000
    val hours = ((totalSeconds % 86400) / 3600).toInt()
    val minutes = ((totalSeconds % 3600) / 60).toInt()
    return hours.toString().padStart(2, '0') + ":" + minutes.toString().padStart(2, '0')
}
