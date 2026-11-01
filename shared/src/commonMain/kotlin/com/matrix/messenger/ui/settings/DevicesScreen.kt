package com.matrix.messenger.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GppMaybe
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.matrix.messenger.data.model.DeviceInfo
import com.matrix.messenger.data.model.VerificationUiState
import com.matrix.messenger.platform.readFileBytes
import com.matrix.messenger.platform.rememberFilePicker
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicesScreen(
    onNavigateBack: () -> Unit,
    viewModel: DevicesViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val verificationState by viewModel.verificationState.collectAsState()
    val showPasswordPrompt by viewModel.showPasswordPrompt.collectAsState()
    var passwordInput by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is com.matrix.messenger.data.model.UiEvent.ShowSnackbar ->
                    snackbarHostState.showSnackbar(event.message)
                else -> Unit
            }
        }
    }
    var renameTarget by remember { mutableStateOf<DeviceInfo?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<DeviceInfo?>(null) }
    var keyPassphrase by remember { mutableStateOf("") }
    val pickKeyFile = rememberFilePicker { file ->
        runCatching {
            val bytes = readFileBytes(file.path)
            viewModel.importKeys(bytes, keyPassphrase)
        }.onFailure {
            viewModel.onImportFailed(it)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Устройства") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Обновить")
                    }
                }
            )
        }
    ) { paddingValues ->
        when {
            uiState.isLoading -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
                ) { CircularProgressIndicator() }
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(paddingValues)
            ) {
                item(key = "backup") {
                    KeyBackupSection(
                        version = uiState.keyBackupVersion,
                        isBootstrapping = uiState.isBootstrapping,
                        onBootstrap = viewModel::bootstrapKeyBackup
                    )
                }
                item(key = "session") {
                    SessionTrustRow(verified = uiState.sessionVerified)
                }
                item(key = "keys") {
                    KeyExportSection(
                        isExporting = uiState.isExportingKeys,
                        isImporting = uiState.isImportingKeys,
                        passphrase = keyPassphrase,
                        onPassphraseChange = { keyPassphrase = it },
                        onExport = { viewModel.exportKeys(keyPassphrase) },
                        onImport = { pickKeyFile() }
                    )
                }
                uiState.error?.let { error ->
                    item(key = "error") {
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
                items(
                    items = uiState.devices,
                    key = { it.deviceId }
                ) { device ->
                    DeviceRow(
                        device = device,
                        isCurrent = device.deviceId == uiState.currentDeviceId,
                        onRename = {
                            renameTarget = device
                            renameText = device.displayName ?: ""
                        },
                        onDelete = { deleteTarget = device },
                        onVerify = { viewModel.verify(device.deviceId) }
                    )
                }
            }
        }
    }

    renameTarget?.let { device ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Переименовать устройство") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    label = { Text("Имя устройства") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.rename(device.deviceId, renameText.trim())
                    renameTarget = null
                }) { Text("Сохранить") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("Отмена") }
            }
        )
    }

    deleteTarget?.let { device ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Удалить устройство?") },
            text = { Text("Сессия «${device.displayName ?: device.deviceId}» будет завершена и её ключи шифрования недоступны.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(device.deviceId)
                    deleteTarget = null
                }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Отмена") }
            }
        )
    }

    // UIA password prompt (device deletion / backup bootstrap)
    if (showPasswordPrompt) {
        AlertDialog(
            onDismissRequest = { viewModel.submitPassword(null) },
            title = { Text("Подтверждение") },
            text = {
                OutlinedTextField(
                    value = passwordInput,
                    onValueChange = { passwordInput = it },
                    label = { Text("Пароль аккаунта") },
                    singleLine = true,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.submitPassword(passwordInput.takeIf { it.isNotBlank() })
                        passwordInput = ""
                    }
                ) { Text("Подтвердить") }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.submitPassword(null)
                    passwordInput = ""
                }) { Text("Отмена") }
            }
        )
    }

    // Recovery key — shown exactly once after bootstrap
    uiState.recoveryKey?.let { key ->
        AlertDialog(
            onDismissRequest = viewModel::dismissRecoveryKey,
            title = { Text("Резервное копирование включено") },
            text = {
                Column {
                    Text(
                        "Сохраните ключ восстановления — без него нельзя восстановить историю зашифрованных чатов на новом устройстве. Ключ больше не будет показан."
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = key,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::dismissRecoveryKey) { Text("Сохранил") }
            }
        )
    }

    // Interactive verification dialog
    when (val vs = verificationState) {
        is VerificationUiState.Waiting, is VerificationUiState.Compare -> {
            AlertDialog(
                // Outside clicks must NOT cancel the ongoing SAS flow.
                onDismissRequest = {},
                title = { Text("Верификация устройства") },
                text = {
                    when (vs) {
                        is VerificationUiState.Compare -> {
                            Column {
                                Text("Сравните эмодзи с другим устройством. Они должны совпадать:")
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = vs.emojis.joinToString("  ") { it.second },
                                    style = MaterialTheme.typography.headlineSmall
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Код: " + vs.decimal.joinToString(" "),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        else -> Text("Ожидание другого устройства… Оно должно подтвердить запрос на верификацию.")
                    }
                },
                confirmButton = {
                    if (vs is VerificationUiState.Compare) {
                        TextButton(onClick = { viewModel.confirmVerification(matches = true) }) {
                            Text("Совпадают")
                        }
                    }
                },
                dismissButton = {
                    Row {
                        if (vs is VerificationUiState.Compare) {
                            TextButton(onClick = { viewModel.confirmVerification(matches = false) }) {
                                Text("Не совпадают", color = MaterialTheme.colorScheme.error)
                            }
                        }
                        TextButton(onClick = viewModel::cancelVerification) { Text("Отмена") }
                    }
                }
            )
        }
        VerificationUiState.Done -> {
            AlertDialog(
                onDismissRequest = {},
                title = { Text("Устройство верифицировано") },
                text = { Text("Сессии подтверждены. Ключи зашифрованных чатов будут доступны обоим устройствам.") },
                confirmButton = {
                    TextButton(onClick = viewModel::cancelVerification) { Text("Готово") }
                }
            )
        }
        is VerificationUiState.Cancelled -> {
            AlertDialog(
                onDismissRequest = viewModel::cancelVerification,
                title = { Text("Верификация отменена") },
                text = { Text(vs.reason ?: "Верификация не была завершена") },
                confirmButton = {
                    TextButton(onClick = viewModel::cancelVerification) { Text("Закрыть") }
                }
            )
        }
        VerificationUiState.Idle -> Unit
    }
}

/** "This session" cross-signing trust indicator. */
@Composable
private fun SessionTrustRow(verified: Boolean?) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (verified == true) Icons.Default.VerifiedUser else Icons.Default.GppMaybe,
            contentDescription = null,
            tint = when (verified) {
                true -> MaterialTheme.colorScheme.primary
                false -> com.matrix.messenger.ui.theme.ElementAmber
                null -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(
                text = "Эта сессия",
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = when (verified) {
                    true -> "Верифицирована (кросс-подписана)"
                    false -> "Не верифицирована — подтвердите её из Element или ключом восстановления"
                    null -> "Статус доверия неизвестен"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    HorizontalDivider()
}

/** Key export/import (Element-compatible m.megolm_backup.v1.export files). */
@Composable
private fun KeyExportSection(
    isExporting: Boolean,
    isImporting: Boolean,
    passphrase: String,
    onPassphraseChange: (String) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(
            text = "Экспорт и импорт ключей",
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            text = "Файл с ключами расшифровки истории (формат Element). Храните в надёжном месте.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = passphrase,
            onValueChange = onPassphraseChange,
            label = { Text("Парольная фраза") },
            singleLine = true,
            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row {
            Button(onClick = onExport, enabled = !isExporting && passphrase.isNotBlank()) {
                if (isExporting) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Экспортировать")
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            OutlinedButton(onClick = onImport, enabled = !isImporting && passphrase.isNotBlank()) {
                if (isImporting) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Импортировать")
                }
            }
        }
    }
    HorizontalDivider()
}

@Composable
private fun KeyBackupSection(
    version: String?,
    isBootstrapping: Boolean,
    onBootstrap: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Резервное копирование ключей", style = MaterialTheme.typography.bodyLarge)
            Text(
                text = version?.let { "Активно (версия $it)" }
                    ?: "Не настроено — история чатов недоступна на новых устройствах",
                style = MaterialTheme.typography.bodySmall,
                color = if (version != null) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (version == null) {
            TextButton(onClick = onBootstrap, enabled = !isBootstrapping) {
                Text(if (isBootstrapping) "Настройка…" else "Включить")
            }
        }
    }
    HorizontalDivider()
}

@Composable
private fun DeviceRow(
    device: DeviceInfo,
    isCurrent: Boolean,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onVerify: () -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = device.displayName ?: device.deviceId,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    if (isCurrent) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                text = "это устройство",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(
                    text = device.deviceId,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val seen = device.lastSeenTs?.let(::formatSeen)
                if (seen != null) {
                    Text(
                        text = seen,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onRename) {
                Icon(Icons.Default.Edit, contentDescription = "Переименовать", modifier = Modifier.width(20.dp))
            }
            if (!isCurrent) {
                IconButton(onClick = onVerify) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Верифицировать",
                        modifier = Modifier.width(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Удалить",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.width(20.dp)
                    )
                }
            }
        }
    }
}

private fun formatSeen(ts: Long): String {
    val seen = Instant.fromEpochMilliseconds(ts)
    val now = Clock.System.now()
    val days = (now - seen).inWholeDays
    return when {
        days > 30 -> "неактивно более месяца"
        days > 0 -> "активно $days дн. назад"
        else -> {
            val local = seen.toLocalDateTime(TimeZone.currentSystemDefault())
            "активно сегодня, ${local.hour.toString().padStart(2, '0')}:${local.minute.toString().padStart(2, '0')}"
        }
    }
}
