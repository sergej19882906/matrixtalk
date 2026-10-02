package com.matrix.messenger.ui.bridges

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private data class BridgeInfo(
    val name: String,
    val protocol: String,
    val description: String,
    val setup: String
)

private val bridges = listOf(
    BridgeInfo(
        "Telegram",
        "mautrix-telegram",
        "Личные чаты, группы и каналы Telegram через Matrix.",
        "Запустите mautrix-telegram на homeserver и откройте ссылку авторизации в его bridge-комнате."
    ),
    BridgeInfo(
        "WhatsApp",
        "mautrix-whatsapp",
        "Синхронизация WhatsApp с комнатами Matrix.",
        "Запустите mautrix-whatsapp, затем отсканируйте QR-код в bridge-комнате."
    ),
    BridgeInfo(
        "Signal",
        "mautrix-signal",
        "Подключение Signal через отдельный bridge-сервис.",
        "Запустите mautrix-signal и завершите привязку устройства по инструкции bridge-сервиса."
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BridgesScreen(onNavigateBack: () -> Unit) {
    var selectedBridge by remember { mutableStateOf<BridgeInfo?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Мосты") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Подключение внешних мессенджеров",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    "Мосты запускаются на вашем Matrix homeserver. Приложение показывает инструкции и статус настройки.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
            items(bridges) { bridge ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Default.Link,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(bridge.name, style = MaterialTheme.typography.titleMedium)
                            Text(bridge.protocol, style = MaterialTheme.typography.labelMedium)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(bridge.description, style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(onClick = { selectedBridge = bridge }) {
                                Text("Настроить")
                            }
                        }
                    }
                }
            }
        }
    }

    selectedBridge?.let { bridge ->
        AlertDialog(
            onDismissRequest = { selectedBridge = null },
            title = { Text("Настройка ${bridge.name}") },
            text = { Text(bridge.setup) },
            confirmButton = {
                TextButton(onClick = { selectedBridge = null }) {
                    Text("Понятно")
                }
            }
        )
    }
}
