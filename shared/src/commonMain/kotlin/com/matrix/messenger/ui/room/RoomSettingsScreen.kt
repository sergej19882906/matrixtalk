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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.matrix.messenger.data.model.RoomMemberInfo
import com.matrix.messenger.data.model.RoomNotificationLevel
import com.matrix.messenger.data.model.RoomSettingsInfo
import com.matrix.messenger.platform.rememberFilePicker
import com.matrix.messenger.ui.components.MatrixAvatar
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Element X style room settings: avatar, name, topic, notification levels,
 * encryption status, member list with search, member card and moderation
 * actions (kick / ban / unban / power level change / invite).
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
    val notificationLevel by viewModel.notificationLevel.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val message by viewModel.message.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val pickAvatar = rememberFilePicker(listOf("image/*")) { file ->
        viewModel.uploadAvatar(file.path, file.mimeType)
    }

    var editingName by remember { mutableStateOf(false) }
    var editingTopic by remember { mutableStateOf(false) }
    var inviting by remember { mutableStateOf(false) }
    var selectedMember by remember { mutableStateOf<RoomMemberInfo?>(null) }
    var confirmLeave by remember { mutableStateOf(false) }
    var memberSearch by remember { mutableStateOf("") }

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
                    if (info.canEdit) {
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
                onEdit = { editingName = true },
                editable = info.canEdit
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

            // Topic
            SettingFieldRow(
                label = "Описание",
                value = info.topic,
                emptyHint = "Добавить описание",
                onEdit = { editingTopic = true },
                editable = info.canEdit
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

            // Notifications: three levels like Element
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (notificationLevel == RoomNotificationLevel.MUTE) {
                        Icons.Default.NotificationsOff
                    } else {
                        Icons.Default.Notifications
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text("Уведомления", style = MaterialTheme.typography.bodyLarge)
            }
            RoomNotificationLevel.entries.forEach { level ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.setNotificationLevel(level) }
                        .padding(start = 34.dp, top = 2.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = notificationLevel == level,
                        onClick = { viewModel.setNotificationLevel(level) }
                    )
                    Text(
                        text = when (level) {
                            RoomNotificationLevel.ALL_MESSAGES -> "Все сообщения"
                            RoomNotificationLevel.MENTIONS_ONLY -> "Только упоминания"
                            RoomNotificationLevel.MUTE -> "Без звука"
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Участники (${members.size})",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                if (info.canInvite) {
                    TextButton(onClick = { inviting = true }) {
                        Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Пригласить")
                    }
                }
            }
            Spacer(Modifier.height(4.dp))

            OutlinedTextField(
                value = memberSearch,
                onValueChange = { memberSearch = it },
                placeholder = { Text("Поиск участников") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(4.dp))

            val query = memberSearch.trim().lowercase()
            val visibleMembers = if (query.isEmpty()) {
                members
            } else {
                members.filter {
                    it.name?.lowercase()?.contains(query) == true || it.userId.lowercase().contains(query)
                }
            }
            visibleMembers.forEach { member ->
                MemberRow(
                    member = member,
                    canModerate = info.canKick && (member.isSelf || member.powerLevel < info.myPowerLevel),
                    onClick = { selectedMember = member }
                )
            }
            if (visibleMembers.isEmpty() && query.isNotEmpty()) {
                Text(
                    text = "Никого не найдено",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp)
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

    // Invite dialog
    if (inviting) {
        var inviteUserId by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { inviting = false },
            title = { Text("Пригласить пользователя") },
            text = {
                OutlinedTextField(
                    value = inviteUserId,
                    onValueChange = { inviteUserId = it },
                    placeholder = { Text("@user:server") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        inviting = false
                        viewModel.inviteUser(inviteUserId.trim())
                    },
                    enabled = inviteUserId.trim().startsWith("@") && inviteUserId.trim().contains(":")
                ) { Text("Пригласить") }
            },
            dismissButton = {
                TextButton(onClick = { inviting = false }) { Text("Отмена") }
            }
        )
    }

    // Member card (Element X style bottom sheet)
    selectedMember?.let { member ->
        settings?.let { info ->
        MemberCardSheet(
            member = member,
            info = info,
            busy = busy,
            onDismiss = { selectedMember = null },
            onKick = {
                viewModel.kickUser(member.userId)
                selectedMember = null
            },
            onBan = {
                viewModel.banUser(member.userId)
                selectedMember = null
            },
            onUnban = {
                viewModel.unbanUser(member.userId)
                selectedMember = null
            },
            onPowerLevel = { level ->
                viewModel.setUserPowerLevel(member.userId, level)
                selectedMember = null
            }
        )
        }
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
    editable: Boolean,
    onEdit: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = editable, onClick = onEdit)
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
                color = if (value.isNullOrBlank() && emptyHint != null && editable) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
        }
        if (editable) {
            Icon(
                Icons.Default.Edit,
                contentDescription = "Изменить $label",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun MemberRow(member: RoomMemberInfo, canModerate: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MatrixAvatar(name = member.name ?: member.userId, avatarUrl = member.avatarUrl, size = 36.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = member.name ?: member.userId,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (member.isBot) {
                    Spacer(modifier = Modifier.width(6.dp))
                    com.matrix.messenger.ui.chat.BotBadge()
                }
            }
            Text(
                text = if (member.isSelf) "Вы · ${member.userId}" else member.userId,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
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
        if (canModerate) {
            Icon(
                Icons.Default.PersonRemove,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
        }
    }
}

/** Bottom sheet with a member's profile and allowed moderation actions. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MemberCardSheet(
    member: RoomMemberInfo,
    info: RoomSettingsInfo,
    busy: Boolean,
    onDismiss: () -> Unit,
    onKick: () -> Unit,
    onBan: () -> Unit,
    onUnban: () -> Unit,
    onPowerLevel: (Long) -> Unit
) {
    val canModerateTarget = !member.isSelf && member.powerLevel < info.myPowerLevel
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            MatrixAvatar(name = member.name ?: member.userId, avatarUrl = member.avatarUrl, size = 72.dp)
            Spacer(Modifier.height(8.dp))
            Text(
                text = member.name ?: member.userId,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = member.userId,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Уровень прав: ${member.powerLevel}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))

            if (member.membership == "ban") {
                if (info.canBan) {
                    SheetAction(
                        text = "Разбанить",
                        icon = Icons.Default.VerticalAlignTop,
                        enabled = !busy,
                        onClick = onUnban
                    )
                }
            } else if (info.canBan && canModerateTarget) {
                SheetAction(
                    text = "Забанить",
                    icon = Icons.Default.Block,
                    enabled = !busy,
                    onClick = onBan,
                    destructive = true
                )
            }
            if (info.canKick && canModerateTarget) {
                SheetAction(
                    text = "Исключить из комнаты",
                    icon = Icons.Default.PersonRemove,
                    enabled = !busy,
                    onClick = onKick,
                    destructive = true
                )
            }
            if (info.canChangePowerLevels && !member.isSelf) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Изменить уровень прав",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    listOf(0L to "Участник", 50L to "Модератор", 100L to "Админ").forEach { (level, label) ->
                        TextButton(
                            onClick = { onPowerLevel(level) },
                            enabled = !busy && level < info.myPowerLevel
                        ) { Text(label) }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SheetAction(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
    destructive: Boolean = false
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text,
            color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        )
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
