package com.matrix.messenger.ui.settings

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Shared, throttled UI gate. The PIN does not encrypt the local database/session. */
@Composable
fun PinLockScreen(
    onUnlock: () -> Unit,
    appLock: AppLockController = AppLockManager
) {
    val lockState by appLock.state.collectAsState()
    val scope = rememberCoroutineScope()
    var entry by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var remainingMillis by remember { mutableStateOf(0L) }
    LaunchedEffect(appLock) { appLock.initialize() }
    LaunchedEffect(lockState.retryAtEpochMillis) {
        do {
            remainingMillis = appLock.remainingLockoutMillis()
            if (remainingMillis > 0) delay(250)
        } while (remainingMillis > 0)
    }
    val enabled = lockState.initialized && !lockState.busy && !submitting && remainingMillis == 0L
    val error = lockState.error
    val message = when {
        !lockState.initialized -> error ?: "Загрузка настроек блокировки…"
        remainingMillis > 0 -> "Повторите через ${(remainingMillis + 999) / 1000} с"
        error != null -> error
        lockState.busy || submitting -> "Проверка PIN…"
        else -> "Введите PIN"
    }

    fun onDigit(d: Char) {
        if (!enabled || entry.length >= 4) return
        appLock.clearError()
        val next = entry + d
        entry = next
        if (next.length == 4) {
            submitting = true
            scope.launch {
                try {
                    if (appLock.verifyPin(next) == PinOperationResult.Success && !appLock.state.value.locked) {
                        onUnlock()
                    }
                } finally {
                    entry = ""
                    submitting = false
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(40.dp)
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.titleMedium,
            color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        if (!lockState.initialized && error != null && !lockState.busy) {
            TextButton(onClick = { scope.launch { appLock.initialize() } }) {
                Text("Повторить")
            }
        }
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(4) { i ->
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .background(
                            color = if (i < entry.length) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant,
                            shape = CircleShape
                        )
                )
            }
        }
        Spacer(Modifier.height(32.dp))
        for (row in listOf(listOf('1', '2', '3'), listOf('4', '5', '6'), listOf('7', '8', '9'))) {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                row.forEach { digit ->
                    KeypadButton(label = digit.toString(), enabled = enabled, onClick = { onDigit(digit) })
                }
                if (row.first() == '7') {
                    KeypadButton(label = "", onClick = {})
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.padding(top = 16.dp)) {
            KeypadButton(label = "", onClick = {})
            KeypadButton(label = "0", enabled = enabled, onClick = { onDigit('0') })
            KeypadButton(
                label = "",
                enabled = enabled,
                icon = { Icon(Icons.Default.Backspace, contentDescription = "Стереть") },
                onClick = { entry = entry.dropLast(1) }
            )
        }
    }
}

@Composable
private fun KeypadButton(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    icon: (@Composable () -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)
            .clickable(
            enabled = enabled,
            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
            indication = null,
            onClick = onClick
        ),
        contentAlignment = Alignment.Center
    ) {
        if (icon != null) {
            icon()
        } else {
            Text(
                text = label,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
