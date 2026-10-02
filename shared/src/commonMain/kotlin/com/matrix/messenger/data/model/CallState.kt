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
    val endTime: Long? = null
)
