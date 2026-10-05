package com.matrix.messenger.data.model

sealed class CallState {
    object Idle : CallState()
    data class Incoming(val callerId: String, val callerName: String) : CallState()
    data class Outgoing(val calleeId: String, val calleeName: String) : CallState()
    data class Connected(val peerId: String, val peerName: String) : CallState()
    data class Ended(val reason: String) : CallState()
}

data class CallSession(
    val callId: String,
    val roomId: String,
    val peerUserId: String,
    val peerDisplayName: String,
    val isVideo: Boolean,
    val startTime: Long,
    val endTime: Long? = null,
    val offerSdp: String? = null
)

/** TURN/STUN server entry as reported by GET /_matrix/client/v3/voip/turnServer. */
data class IceServer(
    val urls: List<String>,
    val username: String? = null,
    val credential: String? = null
)
