package com.matrix.messenger.telecom

import android.net.Uri
import android.os.Build
import android.telecom.Connection
import android.telecom.ConnectionRequest
import android.telecom.ConnectionService
import android.telecom.DisconnectCause
import android.telecom.PhoneAccountHandle
import androidx.annotation.RequiresApi

/**
 * Self-managed ConnectionService: surfaces Matrix calls in the system Telecom UI
 * (incoming-call screen, ongoing call notification, answer/reject from system).
 */
@RequiresApi(Build.VERSION_CODES.M)
class MatrixCallConnectionService : ConnectionService() {

    override fun onCreateIncomingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ): Connection {
        val extras = request?.extras
        val name = extras?.getString(CallTelecomIntegration.EXTRA_PEER_NAME) ?: "Входящий звонок"
        val isVideo = extras?.getBoolean(CallTelecomIntegration.EXTRA_IS_VIDEO, false) ?: false

        val connection = MatrixCallConnection()
        connection.setAddress(request?.address, android.telecom.TelecomManager.PRESENTATION_ALLOWED)
        connection.setCallerDisplayName(name, android.telecom.TelecomManager.PRESENTATION_ALLOWED)
        connection.setConnectionProperties(Connection.PROPERTY_SELF_MANAGED)
        connection.setConnectionCapabilities(
            Connection.CAPABILITY_SUPPORT_HOLD or Connection.CAPABILITY_HOLD
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            connection.setVideoState(
                if (isVideo) android.telecom.VideoProfile.STATE_BIDIRECTIONAL
                else android.telecom.VideoProfile.STATE_AUDIO_ONLY
            )
        }
        connection.setRinging()
        CallTelecomIntegration.bindConnection(connection)
        return connection
    }

    override fun onCreateOutgoingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ): Connection {
        val connection = MatrixCallConnection()
        connection.setConnectionProperties(Connection.PROPERTY_SELF_MANAGED)
        connection.setDialing()
        CallTelecomIntegration.bindConnection(connection)
        return connection
    }

    override fun onCreateIncomingConnectionFailed(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ) {
        CallTelecomIntegration.clearConnection()
    }

    override fun onCreateOutgoingConnectionFailed(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ) {
        CallTelecomIntegration.clearConnection()
    }
}

@RequiresApi(Build.VERSION_CODES.M)
class MatrixCallConnection : Connection() {

    override fun onAnswer() {
        CallTelecomIntegration.onAnswer?.invoke(false)
        setActive()
    }

    override fun onAnswer(videoState: Int) {
        CallTelecomIntegration.onAnswer?.invoke(
            videoState == android.telecom.VideoProfile.STATE_BIDIRECTIONAL
        )
        setActive()
    }

    override fun onReject() {
        CallTelecomIntegration.onReject?.invoke()
        setDisconnected(DisconnectCause(DisconnectCause.REJECTED))
        destroy()
    }

    override fun onDisconnect() {
        CallTelecomIntegration.onDisconnect?.invoke()
        setDisconnected(DisconnectCause(DisconnectCause.LOCAL))
        destroy()
    }
}
