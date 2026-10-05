package com.matrix.messenger.data.repository

import com.matrix.messenger.data.call.CallSignalingEvent
import com.matrix.messenger.data.model.CallSession
import com.matrix.messenger.data.model.CallState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CallRepository(
    private val matrixRepository: MatrixRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _callState = MutableStateFlow<CallState>(CallState.Idle)
    val callState: StateFlow<CallState> = _callState.asStateFlow()

    private var currentCall: CallSession? = null
    private var currentCallId: String? = null

    init {
        scope.launch {
            matrixRepository.callEvents.collect { event ->
                when (event) {
                    is CallSignalingEvent.Incoming -> {
                        currentCallId = event.callId
                        currentCall = CallSession(
                            callId = event.callId,
                            roomId = event.roomId,
                            peerUserId = event.fromUserId,
                            peerDisplayName = event.fromUserId,
                            isVideo = event.isVideo,
                            startTime = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
                        )
                        _callState.value = CallState.Incoming(event.fromUserId, event.fromUserId)
                    }

                    is CallSignalingEvent.Answer,
                    is CallSignalingEvent.Candidates -> Unit // consumed by the WebRTC engine (Phase 1)

                    is CallSignalingEvent.Hangup,
                    is CallSignalingEvent.Reject -> endCall()
                }
            }
        }
    }

    fun getCurrentCallId(): String? = currentCallId

    suspend fun sendReject() {
        val roomId = currentCall?.roomId
        val callId = currentCallId
        if (roomId != null && callId != null) {
            runCatching { matrixRepository.sendCallEvent(roomId, com.matrix.messenger.data.call.CallSignalingCommand.Reject(callId)) }
        }
        endCall()
    }

    fun endCall() {
        _callState.value = CallState.Idle
        currentCall = null
        currentCallId = null
    }

    // Phase 1 will drive these from the WebRTC engine.
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

    suspend fun rejectCall(callId: String? = null) = sendReject()

    fun toggleMute(isMuted: Boolean) { /* Phase 1 */ }

    fun toggleVideo(isEnabled: Boolean) { /* Phase 1 */ }

    fun switchCamera() { /* Phase 1 */ }
}
