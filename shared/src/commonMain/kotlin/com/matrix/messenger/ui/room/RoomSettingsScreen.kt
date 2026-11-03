package com.matrix.messenger.ui.room

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.matrix.messenger.data.model.RoomMemberInfo
import com.matrix.messenger.platform.rememberFilePicker
import com.matrix.messenger.ui.components.MatrixAvatar
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Element X style room settings: avatar, name, topic, notification mute,
 * encryption status and the member list with power-level badges and kick.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomSettingsScreen(
    roomId: String,
    onNavigateBack: () -> Unit,
    onLeftRoom: () -> Unit = {},
    viewModel: RoomSettingsViewModel = koinViewModel { parametersOf(roomId) }
) {
    val settings by viewModel.settings.collectAsState()
    val members by viewModel.members.collectAsState()
    val isMuted by viewModel.isMuted.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val message by viewModel.message.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val pickAvatar = rememberFilePicker(listOf("image/*")) { file ->
        viewModel.uploadAvatar(file.path, file.mimeType)
    }

    var editingName by remember { mutableStateOf(false) }
    var editingTopic by remember { mutableStateOf(false) }
    var confirmKick by remember { mutableStateOf<RoomMemberInfo?>(null) }
    var confirmLeave by remember { mutableStateOf(false) }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки комнаты") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        val info = settings
        if (info == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            // Avatar + name header
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(contentAlignment = Alignment.BottomEnd) {
                    MatrixAvatar(
                        name = info.name,
                        avatarUrl = info.avatarUrl,
                        size = 96.dp
                    )
                    IconButton(
                        onClick = { pickAvatar() },
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(percent = 50))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Icon(
                            Icons.Default.PhotoCamera,
                            contentDescription = "Изменить аватар",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = info.name ?: roomId,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (info.isEncrypted) Icons.Default.Shield else Icons.Default.LockOpen,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = buildString {
                            append(if (info.isEncrypted) "Шифрование включено" else "Без шифрования")
                            append(" · ${info.memberCount} участн.")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // Name
            SettingFieldRow(
                label = "Название",
                value = info.name,
                onEdit = { editingName = true }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

            // Topic
            SettingFieldRow(
                label = "Описание",
                value = info.topic,
                emptyHint = "Добавить описание",
                onEdit = { editingTopic = true }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

            // Notifications toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isMuted) Icons.Default.NotificationsOff else Icons.Default.Notifications,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Уведомления", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = if (isMuted) "Отключены" else "Включены",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = !isMuted,
                    onCheckedChange = { enabled -> viewModel.toggleMuted(!enabled) },
                    enabled = !busy
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

            Spacer(Modifier.height(20.dp))
            Text(
                text = "Участники (${members.size})",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))

            members.take(50).forEach { member ->
                MemberRow(
                    member = member,
                    canKick = member.isSelf.not(),
                    onKick = { confirmKick = member }
                )
            }
            if (members.size > 50) {
                Text(
                    text = "…и ещё ${members.size - 50}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { confirmLeave = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.error
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Выйти из комнаты")
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    // Edit name dialog
    if (editingName) {
        EditValueDialog(
            title = "Название комнаты",
            initial = settings?.name.orEmpty(),
            onDismiss = { editingName = false },
            onConfirm = { value ->
                editingName = false
                viewModel.saveName(value)
            }
        )
    }

    // Edit topic dialog
    if (editingTopic) {
        EditValueDialog(
            title = "Описание",
            initial = settings?.topic.orEmpty(),
            multiline = true,
            onDismiss = { editingTopic = false },
            onConfirm = { value ->
                editingTopic = false
                viewModel.saveTopic(value)
            }
        )
    }

    // Kick confirmation
    confirmKick?.let { member ->
        AlertDialog(
            onDismissRequest = { confirmKick = null },
            title = { Text("Исключить участника") },
            text = { Text("Исключить ${member.name ?: member.userId} из комнаты?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.kickUser(member.userId)
                    confirmKick = null
                }) {
                    Text("Исключить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmKick = null }) { Text("Отмена") }
            }
        )
    }

    // Leave confirmation
    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("Выйти из комнаты") },
            text = { Text("Вы покинете комнату и удалите её из списка чатов.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmLeave = false
                    viewModel.leaveRoom(onLeft = onLeftRoom)
                }) {
                    Text("Выйти", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmLeave = false }) { Text("Отмена") }
            }
        )
    }
}

@Composable
private fun SettingFieldRow(
    label: String,
    value: String?,
    emptyHint: String? = null,
    onEdit: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = value?.takeIf { it.isNotBlank() } ?: emptyHint ?: "—",
                style = MaterialTheme.typography.bodyLarge,
                color = if (value.isNullOrBlank() && emptyHint != null) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
        }
        Icon(
            Icons.Default.Edit,
            contentDescription = "Изменить $label",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun MemberRow(member: RoomMemberInfo, canKick: Boolean, onKick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MatrixAvatar(name = member.name ?: member.userId, avatarUrl = member.avatarUrl, size = 36.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = member.name ?: member.userId,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1
            )
            if (member.isSelf) {
                Text(
                    text = "Вы",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        val badge = when {
            member.powerLevel >= 100 -> "Админ"
            member.powerLevel >= 50 -> "Модератор"
            else -> null
        }
        if (badge != null) {
            Text(
                text = badge,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(percent = 50))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            )
            Spacer(Modifier.width(8.dp))
        }
        if (canKick) {
            IconButton(onClick = onKick, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.PersonRemove,
                    contentDescription = "Исключить ${member.name ?: member.userId}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun EditValueDialog(
    title: String,
    initial: String,
    multiline: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = if (multiline) 3 else 1,
                singleLine = !multiline
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(value) },
                enabled = value.isNotBlank()
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}
