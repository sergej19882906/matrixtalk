package com.matrix.messenger.ui.call

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.matrix.messenger.service.CallService
import com.matrix.messenger.ui.theme.MatrixMessengerTheme

class IncomingCallActivity : ComponentActivity() {

    companion object {
        const val EXTRA_CALL_ID = "extra_call_id"
        const val EXTRA_ROOM_ID = "extra_room_id"
        const val EXTRA_PEER_USER_ID = "extra_peer_user_id"
        const val EXTRA_PEER_NAME = "extra_peer_name"
        const val EXTRA_IS_VIDEO = "extra_is_video"

        fun newIntent(
            context: Context,
            callId: String,
            roomId: String,
            peerUserId: String,
            peerName: String,
            isVideo: Boolean
        ): Intent {
            return Intent(context, IncomingCallActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(EXTRA_CALL_ID, callId)
                putExtra(EXTRA_ROOM_ID, roomId)
                putExtra(EXTRA_PEER_USER_ID, peerUserId)
                putExtra(EXTRA_PEER_NAME, peerName)
                putExtra(EXTRA_IS_VIDEO, isVideo)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupWindowForCall()

        val callId = intent.getStringExtra(EXTRA_CALL_ID) ?: ""
        val roomId = intent.getStringExtra(EXTRA_ROOM_ID) ?: ""
        val peerUserId = intent.getStringExtra(EXTRA_PEER_USER_ID) ?: ""
        val peerName = intent.getStringExtra(EXTRA_PEER_NAME) ?: "Неизвестный"
        val isVideo = intent.getBooleanExtra(EXTRA_IS_VIDEO, false)

        enableEdgeToEdge()

        setContent {
            MatrixMessengerTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    IncomingCallScreen(
                        peerName = peerName,
                        isVideo = isVideo,
                        onAccept = {
                            val callIntent = CallActivity.newIntent(
                                context = this@IncomingCallActivity,
                                callId = callId,
                                roomId = roomId,
                                peerUserId = peerUserId,
                                peerName = peerName,
                                peerAvatarUrl = null,
                                isVideo = isVideo,
                                isIncoming = true
                            )
                            startActivity(callIntent)
                            finish()
                        },
                        onReject = {
                            CallService.endCall(this@IncomingCallActivity)
                            finish()
                        }
                    )
                }
            }
        }
    }

    private fun setupWindowForCall() {
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                if (!packageManager.hasSystemFeature("android.software.telecom")) {
                    return
                }
            } catch (_: Exception) {
            }
        }
    }
}

@Composable
private fun IncomingCallScreen(
    peerName: String,
    isVideo: Boolean,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(colors = listOf(Color(0xFF1A1C2E), Color(0xFF0F1419))))
            .systemBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (isVideo) "Входящий видеозвонок" else "Входящий аудиозвонок",
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = peerName,
                color = Color.White,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(64.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = onReject,
                        modifier = Modifier.size(72.dp).clip(CircleShape).background(Color(0xFFEF4444))
                    ) {
                        Icon(Icons.Default.CallEnd, contentDescription = "Отклонить", tint = Color.White, modifier = Modifier.size(36.dp))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Отклонить", color = Color.White, style = MaterialTheme.typography.bodyMedium)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = onAccept,
                        modifier = Modifier.size(72.dp).clip(CircleShape).background(Color(0xFF22C55E))
                    ) {
                        Icon(Icons.Default.Call, contentDescription = "Принять", tint = Color.White, modifier = Modifier.size(36.dp))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Принять", color = Color.White, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
