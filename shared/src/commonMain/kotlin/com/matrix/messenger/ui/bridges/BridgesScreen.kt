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
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.matrix.messenger.data.repository.MatrixRepository
import org.koin.compose.koinInject

private data class BridgeInfo(
    val name: String,
    val protocol: String,
    val category: String,
    val description: String,
    val setup: String,
    /** Our GHCR image slug (matrixtalk-<image>), null when none is published. */
    val image: String? = null
)

private const val CATEGORY_MESSENGERS = "Мессенджеры"
private const val CATEGORY_NETWORKS = "Сети и протоколы"
private const val CATEGORY_INTEGRATIONS = "Интеграции и автоматизация"

private val bridges = listOf(
    // -- Мессенджеры --
    BridgeInfo(
        "Telegram",
        "mautrix-telegram",
        CATEGORY_MESSENGERS,
        "Личные чаты, группы и каналы Telegram через Matrix.",
        "Запустите mautrix-telegram на homeserver и откройте ссылку авторизации в его bridge-комнате.",
        image = "mautrix-telegram"
    ),
    BridgeInfo(
        "WhatsApp",
        "mautrix-whatsapp",
        CATEGORY_MESSENGERS,
        "Синхронизация WhatsApp с комнатами Matrix.",
        "Запустите mautrix-whatsapp, затем отсканируйте QR-код в bridge-комнате.",
        image = "mautrix-whatsapp"
    ),
    BridgeInfo(
        "Signal",
        "mautrix-signal",
        CATEGORY_MESSENGERS,
        "Подключение Signal как linked-устройства.",
        "Запустите mautrix-signal и завершите привязку устройства по инструкции bridge-сервиса.",
        image = "mautrix-signal"
    ),
    BridgeInfo(
        "Meta (Facebook, Instagram)",
        "mautrix-meta",
        CATEGORY_MESSENGERS,
        "Facebook Messenger и Instagram Direct в Matrix.",
        "Запустите mautrix-meta и авторизуйтесь в аккаунт Meta через bridge-комнату.",
        image = "mautrix-meta"
    ),
    BridgeInfo(
        "Google Messages",
        "mautrix-gmessages",
        CATEGORY_MESSENGERS,
        "SMS/RCS с Android-устройства как второе устройство.",
        "Запустите mautrix-gmessages и свяжите телефон по QR-коду из bridge-комнаты.",
        image = "mautrix-gmessages"
    ),
    BridgeInfo(
        "Discord",
        "mautrix-discord",
        CATEGORY_MESSENGERS,
        "Каналы и серверы Discord: двусторонние сообщения.",
        "Запустите mautrix-discord, добавьте бота на сервер Discord и привяжите каналы в комнатах.",
        image = "mautrix-discord"
    ),
    BridgeInfo(
        "Slack",
        "mautrix-slack",
        CATEGORY_MESSENGERS,
        "Рабочие каналы и личные сообщения Slack.",
        "Запустите mautrix-slack и пройдите OAuth-вход в bridge-комнате."
    ),
    // -- Сети и протоколы --
    BridgeInfo(
        "IRC",
        "heisenbridge",
        CATEGORY_NETWORKS,
        "IRC-каналы и приваты: каждый канал становится комнатой.",
        "Запустите heisenbridge, затем напишите его боту команду для подключения к IRC-сети.",
        image = "heisenbridge"
    ),
    BridgeInfo(
        "XMPP",
        "biboumi / matrix-xmpp",
        CATEGORY_NETWORKS,
        "Jabber/XMPP-конференции и контакты.",
        "Запустите biboumi (или matrix-xmpp-bridge) на homeserver и добавьте JID в bridge-комнату."
    ),
    BridgeInfo(
        "Email",
        "mautrix-email",
        CATEGORY_NETWORKS,
        "Переписка по email как чаты Matrix.",
        "Запустите mautrix-email и укажите почтовый аккаунт в bridge-комнате."
    ),
    // -- Интеграции --
    BridgeInfo(
        "GitHub / GitLab / Jira",
        "matrix-hookshot",
        CATEGORY_INTEGRATIONS,
        "Уведомления о коммитах, issues, PR и webhooks в комнатах.",
        "Запустите matrix-hookshot, подключите репозитории через его команды в комнате (help — !hookshot).",
        image = "hookshot"
    ),
    BridgeInfo(
        "RSS / Atom",
        "mautrix-rss",
        CATEGORY_INTEGRATIONS,
        "Новости из фидов приходят сообщениями в комнату.",
        "Запустите mautrix-rss и добавьте URL фида командой боту в комнате."
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BridgesScreen(onNavigateBack: () -> Unit) {
    var selectedBridge by remember { mutableStateOf<BridgeInfo?>(null) }

    val categories = bridges.groupBy { it.category }

    // Rooms bridged right now, grouped by network name.
    val matrixRepository: MatrixRepository = koinInject()
    val bridgedRooms by matrixRepository.getRoomsFlow().collectAsState(initial = emptyList())
    val runningBridges = remember(bridgedRooms) {
        bridgedRooms
            .filter { it.bridge != null }
            .groupBy { it.bridge!! }
            .map { (network, rooms) -> network to rooms.size }
            .sortedBy { it.first.lowercase() }
    }

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
            item(key = "running_header") {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Запущенные мосты",
                    style = MaterialTheme.typography.titleMedium
                )
                if (runningBridges.isEmpty()) {
                    Text(
                        "Мостов в вашем аккаунте не найдено. Настройте их ниже — комнаты появятся здесь автоматически.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Spacer(modifier = Modifier.height(8.dp))
                    runningBridges.forEach { (network, count) ->
                        val known = bridges.firstOrNull {
                            it.name.contains(network, ignoreCase = true) ||
                                it.protocol.contains(network, ignoreCase = true)
                        }
                        Text(
                            text = "• $network — $count комн.${if (known != null) "  ✓ настроен" else ""}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))
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
            categories.forEach { (category, list) ->
                item(key = "category_$category") {
                    Text(
                        category,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                items(list, key = { "${it.protocol}_${it.name}" }) { bridge ->
                    val isRunning = runningBridges.any { (network, _) ->
                        bridge.name.contains(network, ignoreCase = true) ||
                            bridge.protocol.contains(network, ignoreCase = true)
                    }
                    BridgeCard(bridge = bridge, isRunning = isRunning, onSetup = { selectedBridge = bridge })
                }
            }
            item { Spacer(modifier = Modifier.height(8.dp)) }
        }
    }

    selectedBridge?.let { bridge ->
        AlertDialog(
            onDismissRequest = { selectedBridge = null },
            title = { Text("Настройка ${bridge.name}") },
            text = {
                Column {
                    Text(bridge.setup)
                    if (bridge.image != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Готовый образ: ghcr.io/sergej19882906/matrixtalk-${bridge.image}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedBridge = null }) {
                    Text("Понятно")
                }
            }
        )
    }
}

@Composable
private fun BridgeCard(bridge: BridgeInfo, isRunning: Boolean, onSetup: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
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
                Text(
                    text = if (isRunning) "${bridge.name}  ✓" else bridge.name,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(bridge.protocol, style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Text(bridge.description, style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = onSetup) {
                    Text("Настроить")
                }
            }
        }
    }
}
