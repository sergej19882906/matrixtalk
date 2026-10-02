package com.matrix.messenger.ui.call

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.matrix.messenger.service.CallService
import com.matrix.messenger.ui.theme.MatrixMessengerTheme

class CallActivity : ComponentActivity() {

    companion object {
        const val EXTRA_CALL_ID = "extra_call_id"
        const val EXTRA_ROOM_ID = "extra_room_id"
        const val EXTRA_PEER_USER_ID = "extra_peer_user_id"
        const val EXTRA_PEER_NAME = "extra_peer_name"
        const val EXTRA_PEER_AVATAR_URL = "extra_peer_avatar_url"
        const val EXTRA_IS_VIDEO = "extra_is_video"
        const val EXTRA_IS_INCOMING = "extra_is_incoming"

        fun newIntent(
            context: Context,
            callId: String,
            roomId: String,
            peerUserId: String,
            peerName: String,
            peerAvatarUrl: String?,
            isVideo: Boolean,
            isIncoming: Boolean
        ): Intent {
            return Intent(context, CallActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                putExtra(EXTRA_CALL_ID, callId)
                putExtra(EXTRA_ROOM_ID, roomId)
                putExtra(EXTRA_PEER_USER_ID, peerUserId)
                putExtra(EXTRA_PEER_NAME, peerName)
                putExtra(EXTRA_PEER_AVATAR_URL, peerAvatarUrl)
                putExtra(EXTRA_IS_VIDEO, isVideo)
                putExtra(EXTRA_IS_INCOMING, isIncoming)
            }
        }
    }

    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupWindowForCall()

        val callId = intent.getStringExtra(EXTRA_CALL_ID) ?: ""
        val roomId = intent.getStringExtra(EXTRA_ROOM_ID) ?: ""
        val peerUserId = intent.getStringExtra(EXTRA_PEER_USER_ID) ?: ""
        val peerName = intent.getStringExtra(EXTRA_PEER_NAME) ?: "Неизвестный"
        val peerAvatarUrl = intent.getStringExtra(EXTRA_PEER_AVATAR_URL)
        val isVideo = intent.getBooleanExtra(EXTRA_IS_VIDEO, false)
        val isIncoming = intent.getBooleanExtra(EXTRA_IS_INCOMING, false)

        enableEdgeToEdge()

        setContent {
            MatrixMessengerTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val permissionsState = rememberMultiplePermissionsState(
                        permissions = listOf(
                            android.Manifest.permission.RECORD_AUDIO,
                            android.Manifest.permission.CAMERA
                        )
                    )

                    val allPermissionsGranted = permissionsState.permissions.all { it.status.isGranted }

                    LaunchedEffect(Unit) {
                        if (!allPermissionsGranted) {
                            permissionsState.launchMultiplePermissionRequest()
                        }
                    }

                    if (allPermissionsGranted) {
                        CallScreen(
                            roomId = roomId,
                            peerUserId = peerUserId,
                            peerName = peerName,
                            isVideo = isVideo,
                            isOutgoing = !isIncoming,
                            onCallEnded = { finish() }
                        )
                    } else {
                        PermissionRequestScreen(
                            onGrantPermissions = { permissionsState.launchMultiplePermissionRequest() },
                            onDeny = { finish() }
                        )
                    }
                }

                BackHandler {
                    CallService.endCall(this@CallActivity)
                    finish()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        recreate()
    }

    override fun onDestroy() {
        super.onDestroy()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun setupWindowForCall() {
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
    }
}

@Composable
private fun PermissionRequestScreen(
    onGrantPermissions: () -> Unit,
    onDeny: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1C2E))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Mic,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(64.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Требуются разрешения",
            color = Color.White,
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Для совершения звонков необходим доступ к микрофону и камере",
            color = Color.White.copy(alpha = 0.7f),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedButton(
                onClick = onDeny,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
            ) {
                Text("Отмена")
            }

            Button(
                onClick = onGrantPermissions,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1))
            ) {
                Text("Разрешить")
            }
        }
    }
}
