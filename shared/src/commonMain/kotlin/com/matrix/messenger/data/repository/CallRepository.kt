package com.matrix.messenger.data.repository

import com.matrix.messenger.data.model.CallSession
import com.matrix.messenger.data.model.CallState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class CallRepository(
    private val matrixRepository: MatrixRepository
) {
    private val _callState = MutableStateFlow<CallState>(CallState.Idle)
    val callState: StateFlow<CallState> = _callState.asStateFlow()

    private var currentCall: CallSession? = null
    private var currentCallId: String? = null

    suspend fun startCall(roomId: String, peerUserId: String, peerName: String, isVideo: Boolean) {
        currentCallId = "call_${kotlinx.datetime.Clock.System.now().toEpochMilliseconds()}"
        currentCall = CallSession(
            callId = currentCallId!!,
            roomId = roomId,
            peerUserId = peerUserId,
            peerDisplayName = peerName,
            isVideo = isVideo,
            startTime = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        )
        _callState.value = CallState.Outgoing(peerUserId, peerName)
    }

    suspend fun acceptCall(callId: String? = null) {
        currentCall?.let {
            _callState.value = CallState.Connected(it.peerUserId, it.peerDisplayName)
        }
    }

    suspend fun rejectCall(callId: String? = null) {
        _callState.value = CallState.Idle
        currentCall = null
        currentCallId = null
    }

    suspend fun endCall(callId: String? = null) {
        _callState.value = CallState.Ended("Завершено")
        currentCall = null
        currentCallId = null
    }

    fun toggleMute(isMuted: Boolean) { /* Stub */ }
    fun toggleVideo(isEnabled: Boolean) { /* Stub */ }
    fun switchCamera() { /* Stub */ }
    fun getCurrentCallId(): String? = currentCallId
}
