package com.matrix.messenger.telecom

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telecom.Connection
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import com.matrix.messenger.data.model.CallState
import com.matrix.messenger.data.repository.CallRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Bridges [CallRepository] state into the system Telecom UI on Android.
 * Installed from MainActivity once the user is authenticated.
 */
object CallTelecomIntegration {

    const val EXTRA_PEER_NAME = "com.matrix.messenger.telecom.PEER_NAME"
    const val EXTRA_IS_VIDEO = "com.matrix.messenger.telecom.IS_VIDEO"

    private var telecomManager: TelecomManager? = null
    private var phoneAccountHandle: PhoneAccountHandle? = null
    private var repository: CallRepository? = null
    private var scope: CoroutineScope? = null
    private var installed = false

    // Callbacks consumed by MatrixCallConnection
    internal var onAnswer: ((isVideo: Boolean) -> Unit)? = null
    internal var onReject: (() -> Unit)? = null
    internal var onDisconnect: (() -> Unit)? = null

    private var connection: Connection? = null
    private var lastState: CallState = CallState.Idle

    fun install(context: Context, callRepository: CallRepository, coroutineScope: CoroutineScope) {
        if (installed || Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        installed = true
        repository = callRepository
        scope = coroutineScope
        telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
        phoneAccountHandle = PhoneAccountHandle(
            ComponentName(context, MatrixCallConnectionService::class.java),
            "matrixtalk"
        )
        runCatching { registerPhoneAccount(context) }

        coroutineScope.launch {
            callRepository.callState.collect { state -> handleState(context, state) }
        }
    }

    private fun registerPhoneAccount(context: Context) {
        val handle = phoneAccountHandle ?: return
        val account = PhoneAccount.builder(handle, "MatrixTalk")
            .setCapabilities(PhoneAccount.CAPABILITY_SELF_MANAGED)
            .build()
        telecomManager?.registerPhoneAccount(account)
    }

    internal fun bindConnection(conn: Connection) {
        connection = conn
        when (lastState) {
            is CallState.Connected -> conn.setActive()
            is CallState.Idle, is CallState.Ended -> {
                conn.setDisconnected(android.telecom.DisconnectCause(android.telecom.DisconnectCause.LOCAL))
                conn.destroy()
                connection = null
            }
            else -> Unit
        }
    }

    internal fun clearConnection() {
        connection = null
    }

    private fun handleState(context: Context, state: CallState) {
        if (state == lastState) return
        val previous = lastState
        lastState = state

        when (state) {
            is CallState.Incoming -> {
                if (previous !is CallState.Incoming) showIncomingCall(state.callerName, false)
            }
            is CallState.Connected -> connection?.setActive()
            is CallState.Idle, is CallState.Ended -> {
                connection?.let { conn ->
                    conn.setDisconnected(android.telecom.DisconnectCause(android.telecom.DisconnectCause.LOCAL))
                    conn.destroy()
                }
                connection = null
            }
            else -> Unit
        }
    }

    private fun showIncomingCall(peerName: String, isVideo: Boolean) {
        val manager = telecomManager ?: return
        val handle = phoneAccountHandle ?: return
        onAnswer = {
            scope?.launch { repository?.acceptCall() }
        }
        onReject = {
            scope?.launch { repository?.rejectCall() }
        }
        onDisconnect = {
            scope?.launch { repository?.endCall() }
        }
        runCatching {
            val extras = Bundle().apply {
                putString(EXTRA_PEER_NAME, peerName)
                putBoolean(EXTRA_IS_VIDEO, isVideo)
            }
            manager.addNewIncomingCall(handle, extras)
        }
    }
}
