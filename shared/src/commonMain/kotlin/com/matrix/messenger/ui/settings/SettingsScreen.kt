package com.matrix.messenger.ui.settings

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
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhonelinkLock
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
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
            SettingsSectionHeader("Шифрование и ключи")
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
                            "Версия: \${uiState.keyBackupVersion}. История расшифровывается автоматически."
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
            SettingsSectionHeader("Безопасность")
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
                    Text("Устройства", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = "Сессии аккаунта и ключи шифрования",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            // Сеть: прокси
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
                enabled = proxyValid,
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
                    onCheckedChange = viewModel::setNotificationsEnabled
                )
            }
            Text(
                text = uiState.pushStatus ?: "Фоновая проверка новых сообщений каждые 15 минут (Android)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            // О приложении
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
