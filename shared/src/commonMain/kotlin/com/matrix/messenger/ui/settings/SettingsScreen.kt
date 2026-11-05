package com.matrix.messenger.ui.settings

import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhonelinkLock
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.matrix.messenger.platform.MediaDeviceInfo
import com.matrix.messenger.platform.appVersionName
import com.matrix.messenger.platform.rememberFilePicker
import com.matrix.messenger.ui.components.MatrixAvatar
import com.matrix.messenger.ui.theme.ThemeMode
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onOpenDevices: () -> Unit,
    onLogout: () -> Unit = {},
    viewModel: SettingsViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val pickAvatar = rememberFilePicker(listOf("image/*")) { file ->
        viewModel.uploadAvatar(file.path, file.mimeType)
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.proxyMessage) {
        uiState.proxyMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeProxyMessage()
        }
    }

    var pinDialog by remember { mutableStateOf(false) }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                SettingsEvent.LoggedOut -> onLogout()
                SettingsEvent.PinSaved -> pinDialog = false
            }
        }
    }
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeError()
        }
    }
    if (pinDialog) {
        PinSetupDialog(
            pinEnabled = uiState.pinEnabled,
            busy = uiState.isSavingPin,
            error = uiState.errorMessage,
            onDismiss = { if (!uiState.isSavingPin) pinDialog = false },
            onSet = { pin, oldPin -> viewModel.setPin(pin, oldPin) },
            onClear = { oldPin -> viewModel.clearPin(oldPin) }
        )
    }

    // Account deactivation: step 1 explains irreversibility, step 2 asks for the password.
    var showDeactivateConfirm by remember { mutableStateOf(false) }
    var showDeactivatePassword by remember { mutableStateOf(false) }
    var deactivateErase by remember { mutableStateOf(false) }
    var deactivatePassword by remember { mutableStateOf("") }
    if (showDeactivateConfirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDeactivateConfirm = false },
            title = { Text("Деактивировать аккаунт?") },
            text = {
                Column {
                    Text(
                        "Аккаунт будет удалён навсегда. Вы потеряете доступ ко всем комнатам и не сможете войти под этим же пользователем. Это действие необратимо."
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = deactivateErase,
                            onCheckedChange = { deactivateErase = it }
                        )
                        Text(
                            text = "Также стереть мои сообщения",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeactivateConfirm = false
                    showDeactivatePassword = true
                }) { Text("Продолжить") }
            },
            dismissButton = {
                TextButton(onClick = { showDeactivateConfirm = false }) { Text("Отмена") }
            }
        )
    }
    if (showDeactivatePassword) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { if (!uiState.isDeactivating) showDeactivatePassword = false },
            title = { Text("Подтвердите паролем") },
            text = {
                Column {
                    Text("Для удаления аккаунта введите пароль.")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = deactivatePassword,
                        onValueChange = { deactivatePassword = it },
                        label = { Text("Пароль") },
                        singleLine = true,
                        enabled = !uiState.isDeactivating,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeactivatePassword = false
                        viewModel.deactivateAccount(deactivateErase, deactivatePassword)
                        deactivatePassword = ""
                    },
                    enabled = !uiState.isDeactivating && deactivatePassword.isNotBlank()
                ) { Text("Деактивировать") }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeactivatePassword = false },
                    enabled = !uiState.isDeactivating
                ) { Text("Отмена") }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Настройки") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            // Профиль
            SettingsSectionHeader("Профиль")
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MatrixAvatar(
                    name = uiState.user?.displayName ?: uiState.user?.userId,
                    avatarUrl = uiState.user?.avatarUrl,
                    size = 56.dp,
                    shape = CircleShape
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = uiState.user?.displayName ?: "",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = uiState.user?.userId ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { pickAvatar() }) {
                    Icon(Icons.Default.PhotoCamera, contentDescription = "Сменить аватар")
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = uiState.displayNameInput,
                    onValueChange = viewModel::onDisplayNameChange,
                    label = { Text("Отображаемое имя") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.extraLarge,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = viewModel::saveDisplayName,
                    enabled = !uiState.isSavingName &&
                        uiState.displayNameInput.trim() != uiState.user?.displayName
                ) {
                    Text("Сохранить")
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            // Внешний вид
            SettingsSectionHeader("Внешний вид")
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Palette, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Тема оформления", style = MaterialTheme.typography.bodyLarge)
            }
            ThemeMode.entries.forEach { mode ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.setThemeMode(mode) }
                        .padding(horizontal = 24.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = uiState.themeMode == mode,
                        onClick = { viewModel.setThemeMode(mode) }
                    )
                    Text(
                        text = when (mode) {
                            ThemeMode.SYSTEM -> "Как в системе"
                            ThemeMode.LIGHT -> "Светлая"
                            ThemeMode.DARK -> "Тёмная"
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            // Шифрование и ключи
            SettingsSectionHeader("Уведомления")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.setNotificationsEnabled(!uiState.notificationsEnabled) }
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Notifications, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Уведомления о сообщениях",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = uiState.notificationsEnabled,
                    enabled = !uiState.isSavingNotifications,
                    onCheckedChange = viewModel::setNotificationsEnabled
                )
            }
            Text(
                text = uiState.pushStatus ?: "Фоновая проверка новых сообщений каждые 15 минут (Android)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Для новых комнат:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                listOf(
                    com.matrix.messenger.data.model.RoomNotificationLevel.ALL_MESSAGES to "Все",
                    com.matrix.messenger.data.model.RoomNotificationLevel.MENTIONS_ONLY to "Упоминания"
                ).forEach { (level, label) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable { viewModel.setDefaultNotificationLevel(level) }
                            .padding(end = 16.dp)
                    ) {
                        RadioButton(
                            selected = uiState.defaultNotificationLevel == level,
                            onClick = { viewModel.setDefaultNotificationLevel(level) }
                        )
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
            PushRulesSection(
                uiState = uiState,
                onSetMode = viewModel::setAccountMode,
                onAddKeyword = viewModel::addKeyword,
                onRemoveKeyword = viewModel::removeKeyword,
                onToggleRule = viewModel::toggleRule,
                onToggleExpanded = viewModel::togglePushRulesExpanded
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            // О приложении
            SettingsSectionHeader("Безопасность и сессии")
            Row(
                modifier = Modifier.fillMaxWidth().clickable(enabled = !uiState.isSavingPin) { pinDialog = true }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.PhonelinkLock, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("Блокировка приложения PIN", style = MaterialTheme.typography.bodyLarge)
                    Text(if (uiState.pinEnabled) "Включена · изменить или отключить" else "Выключена · задать PIN",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenDevices)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.PhonelinkLock,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text("Сессии и ключи", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = "Верификация, выход с устройств, экспорт и импорт ключей",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Shield,
                    contentDescription = null,
                    tint = if (uiState.keyBackupVersion != null) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (uiState.keyBackupVersion != null) {
                            "Резервное копирование ключей включено"
                        } else {
                            "Резервное копирование ключей не активно"
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = if (uiState.keyBackupVersion != null) {
                            "Версия: ${uiState.keyBackupVersion}. История расшифровывается автоматически."
                        } else {
                            "Включите резервное копирование в Element, чтобы переносить историю."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            // Звук и видео
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            // Сеть: прокси
            SettingsSectionHeader("Звук и видео")
            if (uiState.audioInputDevices.isNotEmpty()) {
                MediaDeviceSelector(
                    label = "Микрофон",
                    devices = uiState.audioInputDevices,
                    selectedId = uiState.selectedAudioInput,
                    onSelect = viewModel::selectAudioInputDevice
                )
            }
            if (uiState.videoDevices.isNotEmpty()) {
                MediaDeviceSelector(
                    label = "Камера",
                    devices = uiState.videoDevices,
                    selectedId = uiState.selectedVideoDevice,
                    onSelect = viewModel::selectVideoDevice
                )
            }
            if (uiState.audioInputDevices.isEmpty() && uiState.videoDevices.isEmpty()) {
                Text(
                    text = "Устройства не найдены",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            // Устройства и безопасность
            SettingsSectionHeader("Прокси")
            var proxyType by remember(uiState.proxy) {
                mutableStateOf(uiState.proxy?.type ?: com.matrix.messenger.data.model.ProxyType.NONE)
            }
            var proxyHost by remember(uiState.proxy) {
                mutableStateOf(uiState.proxy?.host.orEmpty())
            }
            var proxyPort by remember(uiState.proxy) {
                mutableStateOf(uiState.proxy?.port?.toString().orEmpty())
            }
            var proxyUser by remember(uiState.proxy) {
                mutableStateOf(uiState.proxy?.username.orEmpty())
            }
            var proxyPass by remember(uiState.proxy) {
                mutableStateOf(uiState.proxy?.password.orEmpty())
            }
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                listOf(
                    com.matrix.messenger.data.model.ProxyType.NONE to "Нет",
                    com.matrix.messenger.data.model.ProxyType.HTTP to "HTTP",
                    com.matrix.messenger.data.model.ProxyType.SOCKS to "SOCKS5"
                ).forEach { (type, label) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable { proxyType = type }
                            .padding(end = 12.dp)
                    ) {
                        RadioButton(
                            selected = proxyType == type,
                            onClick = { proxyType = type }
                        )
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            if (proxyType != com.matrix.messenger.data.model.ProxyType.NONE) {
                OutlinedTextField(
                    value = proxyHost,
                    onValueChange = { proxyHost = it },
                    label = { Text("Хост") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = proxyPort,
                    onValueChange = { proxyPort = it.filter { ch -> ch.isDigit() }.take(5) },
                    label = { Text("Порт") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = proxyUser,
                    onValueChange = { proxyUser = it },
                    label = { Text("Логин (опционально)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = proxyPass,
                    onValueChange = { proxyPass = it },
                    label = { Text("Пароль (опционально)") },
                    singleLine = true,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                )
            }
            val proxyValid = proxyType == com.matrix.messenger.data.model.ProxyType.NONE ||
                (proxyHost.isNotBlank() && proxyPort.toIntOrNull() in 1..65535)
            Button(
                onClick = {
                    viewModel.updateProxy(
                        if (proxyType == com.matrix.messenger.data.model.ProxyType.NONE) {
                            null
                        } else {
                            com.matrix.messenger.data.model.ProxySettings(
                                type = proxyType,
                                host = proxyHost.trim(),
                                port = proxyPort.toInt(),
                                username = proxyUser.trim().ifBlank { null },
                                password = proxyPass.ifBlank { null }
                            )
                        }
                    )
                    viewModel.saveProxy()
                },
                enabled = proxyValid && !uiState.isSavingProxy,
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Text(if (uiState.proxyDirty) "Сохранить" else "Сохранить (без изменений)")
            }
            Text(
                text = "Весь трафик Matrix (включая фоновую синхронизацию) пойдёт через прокси. Применяется после перезапуска приложения.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            // Уведомления
            SettingsSectionHeader("О приложении")
            Text(
                text = "MatrixTalk ${appVersionName()}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Text(
                text = "Клиент Matrix на Kotlin Multiplatform",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            SettingsSectionHeader("Аккаунт")
            Button(
                onClick = viewModel::logout,
                enabled = !uiState.isLoggingOut,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.error
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            ) {
                Icon(Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Выйти из аккаунта")
            }

            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = {
                    deactivateErase = false
                    deactivatePassword = ""
                    showDeactivateConfirm = true
                },
                enabled = !uiState.isDeactivating,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            ) {
                Text("Деактивировать аккаунт")
            }
            Text(
                text = "Безвозвратно удаляет аккаунт на сервере.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MediaDeviceSelector(
    label: String,
    devices: List<MediaDeviceInfo>,
    selectedId: String?,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = devices.firstOrNull { it.id == selectedId }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 2.dp)
        )
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it }
        ) {
            OutlinedTextField(
                value = selected?.name ?: "По умолчанию",
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                label = { Text("Устройство") },
                trailingIcon = {
                    Icon(
                        if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null
                    )
                },
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                devices.forEach { device ->
                    DropdownMenuItem(
                        text = { Text(device.name) },
                        onClick = {
                            onSelect(device.id)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun PinSetupDialog(
    pinEnabled: Boolean,
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSet: (String, String?) -> Unit,
    onClear: (String) -> Unit
) {
    var oldPin by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (pinEnabled) "Изменить PIN" else "Задать PIN") },
        text = {
            Column {
                if (pinEnabled) {
                    OutlinedTextField(
                        value = oldPin,
                        onValueChange = { oldPin = it.filter { ch -> ch in '0'..'9' }.take(4) },
                        label = { Text("Текущий PIN") },
                        enabled = !busy,
                        singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
                    )
                    Spacer(Modifier.height(8.dp))
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                androidx.compose.material3.OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.filter { ch -> ch.isDigit() }.take(4) },
                    label = { Text("PIN (4 цифры)") },
                    singleLine = true,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
                )
                Spacer(modifier = Modifier.height(8.dp))
                androidx.compose.material3.OutlinedTextField(
                    value = confirm,
                    onValueChange = { confirm = it.filter { ch -> ch.isDigit() }.take(4) },
                    label = { Text("Повторите PIN") },
                    singleLine = true,
                    isError = confirm.length == 4 && confirm != pin,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSet(pin, oldPin.takeIf { pinEnabled }) },
                enabled = !busy && pin.length == 4 && pin == confirm && (!pinEnabled || oldPin.length == 4)
            ) { Text(if (pinEnabled) "Изменить" else "Включить") }
        },
        dismissButton = {
            if (pinEnabled) {
                TextButton(onClick = { onClear(oldPin) }, enabled = !busy && oldPin.length == 4) { Text("Отключить") }
            } else {
                TextButton(onClick = onDismiss) { Text("Отмена") }
            }
        }
    )
}

@Composable
private fun PushRulesSection(
    uiState: SettingsUiState,
    onSetMode: (com.matrix.messenger.data.repository.AccountNotificationMode) -> Unit,
    onAddKeyword: (String) -> Unit,
    onRemoveKeyword: (String) -> Unit,
    onToggleRule: (net.folivo.trixnity.core.model.push.PushRuleKind, String, Boolean) -> Unit,
    onToggleExpanded: () -> Unit
) {
    var newKeyword by remember { mutableStateOf("") }

    Text(
        text = "Режим аккаунта (сервер)",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp)
    )
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        listOf(
            com.matrix.messenger.data.repository.AccountNotificationMode.ALL_MESSAGES to "Все",
            com.matrix.messenger.data.repository.AccountNotificationMode.MENTIONS_ONLY to "Упоминания",
            com.matrix.messenger.data.repository.AccountNotificationMode.OFF to "Выкл."
        ).forEach { (mode, label) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable { onSetMode(mode) }
                    .padding(end = 12.dp)
            ) {
                RadioButton(
                    selected = uiState.accountMode == mode,
                    onClick = { onSetMode(mode) }
                )
                Text(label, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }

    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = "Ключевые слова",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp)
    )
    uiState.keywords.forEach { rule ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "«${rule.pattern}»",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { onRemoveKeyword(rule.ruleId) }, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Удалить ключевое слово", modifier = Modifier.size(16.dp))
            }
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = newKeyword,
            onValueChange = { newKeyword = it },
            placeholder = { Text("Новое ключевое слово") },
            singleLine = true,
            shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Button(
            onClick = {
                onAddKeyword(newKeyword)
                newKeyword = ""
            },
            enabled = newKeyword.isNotBlank()
        ) { Text("Добавить") }
    }
    Text(
        text = "Сообщения с ключевым словом уведомляют даже в комнатах без звука.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp)
    )

    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

    // --- Debug: pushers + server rules ---
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggleExpanded() }
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Правила уведомлений (сервер)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = if (uiState.pushRulesExpanded) "скрыть" else "показать",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )
    }
    if (uiState.pushRulesExpanded) {
        Text(
            text = "Пушеры (куда доставляются уведомления):",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        uiState.pushers.forEach { pusher ->
            Text(
                text = "• ${pusher.appDisplayName} — ${pusher.deviceDisplayName}",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Правила (тумблер = включено):",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        uiState.pushRules.forEach { rule ->
            val kindLabel = when (rule.kind) {
                net.folivo.trixnity.core.model.push.PushRuleKind.OVERRIDE -> "override"
                net.folivo.trixnity.core.model.push.PushRuleKind.CONTENT -> "content"
                net.folivo.trixnity.core.model.push.PushRuleKind.ROOM -> "room"
                net.folivo.trixnity.core.model.push.PushRuleKind.SENDER -> "sender"
                net.folivo.trixnity.core.model.push.PushRuleKind.UNDERRIDE -> "underride"
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 1.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "[$kindLabel] ${rule.ruleId}" +
                        if (rule.notify) "" else " (без уведомления)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Switch(
                    checked = rule.enabled,
                    onCheckedChange = { onToggleRule(rule.kind, rule.ruleId, it) },
                    modifier = Modifier.height(28.dp)
                )
            }
        }
    }
}
