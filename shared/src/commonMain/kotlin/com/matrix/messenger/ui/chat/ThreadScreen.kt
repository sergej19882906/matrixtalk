package com.matrix.messenger.ui.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.matrix.messenger.ui.components.TimelineItem
import com.matrix.messenger.ui.components.buildTimeline
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Element X style thread view: root message pinned on top, replies below,
 * composer sending into the m.thread relation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadScreen(
    roomId: String,
    rootId: String,
    onNavigateBack: () -> Unit,
    viewModel: ThreadViewModel = koinViewModel { parametersOf(roomId, rootId) }
) {
    val messages by viewModel.messages.collectAsState()
    val root by viewModel.root.collectAsState()
    val messageInput by viewModel.messageInput.collectAsState()
    val isSending by viewModel.isSending.collectAsState()

    val listState = rememberLazyListState()
    val timeline = remember(messages) { buildTimeline(messages) }

    // Follow new replies like the main chat does for own messages.
    LaunchedEffect(messages.lastOrNull()?.eventId) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(timeline.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ветка") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = messageInput,
                        onValueChange = viewModel::setMessageInput,
                        placeholder = { Text("Ответить в ветке...") },
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.extraLarge,
                        maxLines = 4
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = { viewModel.sendReply() },
                        enabled = messageInput.isNotBlank() && !isSending
                    ) {
                        Icon(
                            Icons.Default.Send,
                            contentDescription = "Отправить",
                            tint = if (messageInput.isNotBlank()) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp)
            ) {
                root?.let { rootMessage ->
                    item(key = "thread_root") {
                        Column {
                            Text(
                                text = "Сообщение ветки",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            MessageItem(
                                message = rootMessage,
                                ownShortId = null,
                                ownDisplayName = null,
                                isGroupStart = true,
                                groupedWithPrevious = false,
                                groupedWithNext = false,
                                isPinned = false
                            )
                            Text(
                                text = "Ответов: ${messages.size}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    }
                }
                items(timeline, key = { it.key }) { item ->
                    when (item) {
                        is TimelineItem.Header -> com.matrix.messenger.ui.components.DateSeparator(label = item.label)
                        is TimelineItem.Msg -> MessageItem(
                            message = item.message,
                            ownShortId = null,
                            ownDisplayName = null,
                            isGroupStart = item.isGroupStart,
                            groupedWithPrevious = item.groupedWithPrevious,
                            groupedWithNext = item.groupedWithNext,
                            isPinned = false
                        )
                        is TimelineItem.UnreadDivider -> Unit
                    }
                }
            }
        }
    }
}
