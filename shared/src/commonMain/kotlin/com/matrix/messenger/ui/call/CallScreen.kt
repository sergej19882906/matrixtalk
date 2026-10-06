package com.matrix.messenger.ui.call

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.matrix.messenger.data.model.CallState
import org.koin.compose.viewmodel.koinViewModel

/**
 * Active/incoming call UI. Reflects [CallRepository] state via [CallViewModel]:
 * the call itself is started/accepted by the caller of this screen
 * (chat screen buttons, CallActivity, incoming-call observer).
 */
@Composable
fun CallScreen(
    onCallEnded: () -> Unit,
    viewModel: CallViewModel = koinViewModel()
) {
    val callState by viewModel.callState.collectAsState()
    val isMuted by viewModel.isMuted.collectAsState()
    val isVideoEnabled by viewModel.isVideoEnabled.collectAsState()
    val isSpeakerOn by viewModel.isSpeakerOn.collectAsState()
    val session by viewModel.session.collectAsState()
    val callDuration by viewModel.callDuration.collectAsState()

    val isVideo = session?.isVideo == true
    val peerName = when (val state = callState) {
        is CallState.Incoming -> state.callerName
        is CallState.Outgoing -> state.calleeName
        is CallState.Connected -> state.peerName
        else -> session?.peerDisplayName ?: "Собеседник"
    }

    // Close only after the call was actually active — a fresh screen starts in Idle
    // while the activity is still requesting permissions.
    var wasActive by remember { mutableStateOf(false) }
    LaunchedEffect(callState) {
        when (callState) {
            is CallState.Idle -> if (wasActive) onCallEnded()
            is CallState.Ended -> {
                kotlinx.coroutines.delay(600)
                onCallEnded()
            }
            else -> wasActive = true
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(
            Brush.verticalGradient(colors = listOf(Color(0xFF1A1C2E), Color(0xFF0F1419)))
        ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = peerName,
                color = Color.White,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = when (val state = callState) {
                    is CallState.Connected -> viewModel.formatDuration(callDuration)
                    is CallState.Incoming ->
                        if (isVideo) "Входящий видеозвонок" else "Входящий аудиозвонок"
                    is CallState.Outgoing -> "Вызов..."
                    is CallState.Ended -> state.reason
                    CallState.Idle -> ""
                },
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodyLarge
            )

            Spacer(modifier = Modifier.weight(1f).fillMaxWidth())

            if (callState is CallState.Incoming) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 48.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    CallButton(
                        icon = Icons.Default.CallEnd,
                        description = "Отклонить",
                        background = Color(0xFFEF4444),
                        size = 72,
                        onClick = { viewModel.rejectCall() }
                    )
                    CallButton(
                        icon = Icons.Default.Call,
                        description = "Принять",
                        background = Color(0xFF22C55E),
                        size = 72,
                        onClick = { viewModel.acceptCall() }
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 48.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    CallButton(
                        icon = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                        description = "Микрофон",
                        background = if (isMuted) Color(0xFFEF4444) else Color.White.copy(alpha = 0.1f),
                        onClick = { viewModel.toggleMute() }
                    )
                    if (isVideo) {
                        CallButton(
                            icon = if (isVideoEnabled) Icons.Default.Videocam else Icons.Default.VideocamOff,
                            description = "Камера",
                            background = if (isVideoEnabled) Color.White.copy(alpha = 0.1f) else Color(0xFFEF4444),
                            onClick = { viewModel.toggleVideo() }
                        )
                        CallButton(
                            icon = Icons.Default.Cameraswitch,
                            description = "Сменить камеру",
                            background = Color.White.copy(alpha = 0.1f),
                            onClick = { viewModel.switchCamera() }
                        )
                    }
                    CallButton(
                        icon = if (isSpeakerOn) Icons.Default.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                        description = "Динамик",
                        background = Color.White.copy(alpha = 0.1f),
                        onClick = { viewModel.toggleSpeaker() }
                    )
                    CallButton(
                        icon = Icons.Default.CallEnd,
                        description = "Завершить",
                        background = Color(0xFFEF4444),
                        size = 64,
                        iconSize = 32,
                        onClick = { viewModel.endCall() }
                    )
                }
            }
        }
    }
}

@Composable
private fun CallButton(
    icon: ImageVector,
    description: String,
    background: Color,
    size: Int = 56,
    iconSize: Int = 28,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(size.dp).clip(CircleShape).background(background)
    ) {
        Icon(
            icon,
            contentDescription = description,
            tint = Color.White,
            modifier = Modifier.size(iconSize.dp)
        )
    }
}
