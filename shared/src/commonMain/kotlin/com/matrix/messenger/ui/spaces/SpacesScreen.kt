package com.matrix.messenger.ui.spaces

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.matrix.messenger.data.model.ChatRoom
import com.matrix.messenger.ui.components.MatrixAvatar
import org.koin.compose.viewmodel.koinViewModel

/**
 * Element X style spaces overview: every space with avatar, room count and
 * entry into the space detail.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpacesScreen(
    onNavigateBack: () -> Unit,
    onOpenSpace: (String, String?) -> Unit,
    viewModel: SpacesViewModel = koinViewModel()
) {
    val spaces by viewModel.spaces.collectAsState()
    val childrenCounts by viewModel.childrenCounts.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val snackbar = remember { androidx.compose.material3.SnackbarHostState() }
    androidx.compose.runtime.LaunchedEffect(error) { error?.let { snackbar.showSnackbar(it) } }
    var leaving by remember { mutableStateOf<ChatRoom?>(null) }

    Scaffold(
        snackbarHost = { androidx.compose.material3.SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Пространства") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (isLoading && spaces.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (error != null && spaces.isEmpty()) {
                Column(Modifier.align(Alignment.Center).padding(24.dp)) {
                    Text(error!!)
                    TextButton(onClick = viewModel::reload) { Text("Повторить") }
                }
            } else if (spaces.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
                ) {
                    Text("Пространств нет", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Пространства создаются в Element и появляются здесь автоматически.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(spaces, key = { it.roomId }) { space ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenSpace(space.roomId, space.name) }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            MatrixAvatar(
                                name = space.name ?: space.roomId,
                                avatarUrl = space.avatarUrl,
                                size = 48.dp,
                                shape = MaterialTheme.shapes.small
                            )
                            Spacer(Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = space.name ?: space.roomId,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val count = childrenCounts[space.roomId]
                                Text(
                                    text = when (count) {
                                        null -> "…"
                                        else -> "Загружено: $count"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(onClick = { leaving = space }) {
                                Text("Выйти")
                            }
                            Icon(
                                Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    leaving?.let { space ->
        AlertDialog(
            onDismissRequest = { leaving = null },
            title = { Text("Выйти из пространства") },
            text = { Text("Покинуть «${space.name ?: space.roomId}»? Комнаты пространства останутся в списке чатов.") },
            confirmButton = {
                TextButton(onClick = {
                    val id = space.roomId
                    leaving = null
                    viewModel.leaveSpace(id, onLeft = onNavigateBack)
                }) { Text("Выйти", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { leaving = null }) { Text("Отмена") }
            }
        )
    }
}
