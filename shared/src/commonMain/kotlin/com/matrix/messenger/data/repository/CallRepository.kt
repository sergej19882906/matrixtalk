package com.matrix.messenger.data.repository

import com.matrix.messenger.call.WebRtcEvent
import com.matrix.messenger.call.createWebRtcEngine
import com.matrix.messenger.data.call.CallSignalingCommand
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
    private val engine = createWebRtcEngine()

    private val _callState = MutableStateFlow<CallState>(CallState.Idle)
    val callState: StateFlow<CallState> = _callState.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _isVideoOff = MutableStateFlow(false)
    val isVideoOff: StateFlow<Boolean> = _isVideoOff.asStateFlow()

    private val _isSpeakerOn = MutableStateFlow(true)
    val isSpeakerOn: StateFlow<Boolean> = _isSpeakerOn.asStateFlow()

    private var currentCall: CallSession? = null

    init {
        scope.launch {
            matrixRepository.callEvents.collect { event ->
                when (event) {
                    is CallSignalingEvent.Incoming -> onIncomingCall(event)
                    is CallSignalingEvent.Answer ->
                        engine.setRemoteAnswer(event.callId, event.answerSdp)

                    is CallSignalingEvent.Candidates ->
                        engine.addCandidates(event.callId, event.candidates)

                    is CallSignalingEvent.Hangup,
                    is CallSignalingEvent.Reject -> {
                        engine.hangup(event.callId)
                        reset()
                    }
                }
            }
        }
        scope.launch {
            engine.events.collect { event ->
                val call = currentCall ?: return@collect
                when (event) {
                    is WebRtcEvent.Offer ->
                        matrixRepository.sendCallEvent(
                            call.roomId,
                            CallSignalingCommand.Invite(event.callId, event.sdp, call.isVideo)
                        )

                    is WebRtcEvent.Answer ->
                        matrixRepository.sendCallEvent(
                            call.roomId,
                            CallSignalingCommand.Answer(event.callId, event.sdp)
                        )

                    is WebRtcEvent.IceCandidates ->
                        matrixRepository.sendCallEvent(
                            call.roomId,
                            CallSignalingCommand.Candidates(event.callId, event.candidates)
                        )

                    is WebRtcEvent.ConnectionEstablished ->
                        _callState.value = CallState.Connected(call.peerUserId, call.peerDisplayName)

                    is WebRtcEvent.ConnectionFailed -> {
                        runCatching {
                            matrixRepository.sendCallEvent(call.roomId, CallSignalingCommand.Hangup(event.callId))
                        }
                        engine.hangup(event.callId)
                        _callState.value = CallState.Ended("Соединение не удалось")
                        currentCall = null
                    }

                    is WebRtcEvent.Error -> Unit // surfaced in Phase 1.4 via UI events
                }
            }
        }
    }

    private suspend fun onIncomingCall(event: CallSignalingEvent.Incoming) {
        if (_callState.value != CallState.Idle) {
            // busy: reject the second call
            runCatching {
                matrixRepository.sendCallEvent(event.roomId, CallSignalingCommand.Reject(event.callId))
            }
            return
        }
        currentCall = CallSession(
            callId = event.callId,
            roomId = event.roomId,
            peerUserId = event.fromUserId,
            peerDisplayName = event.fromUserId,
            isVideo = event.isVideo,
            startTime = kotlinx.datetime.Clock.System.now().toEpochMilliseconds(),
            offerSdp = event.offerSdp
        )
        _callState.value = CallState.Incoming(event.fromUserId, event.fromUserId)
    }

    suspend fun startCall(roomId: String, peerUserId: String, peerName: String, isVideo: Boolean) {
        if (_callState.value != CallState.Idle) return
        engine.initialize()
        prepareIceServers()
        val callId = "call_${kotlinx.datetime.Clock.System.now().toEpochMilliseconds()}"
        currentCall = CallSession(
            callId = callId,
            roomId = roomId,
            peerUserId = peerUserId,
            peerDisplayName = peerName,
            isVideo = isVideo,
            startTime = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        )
        _callState.value = CallState.Outgoing(peerUserId, peerName)
        engine.startCall(callId, isVideo)
    }

    suspend fun acceptCall(callId: String? = null) {
        val call = currentCall ?: return
        if (callId != null && call.callId != callId) return
        val offerSdp = call.offerSdp ?: return
        engine.initialize()
        prepareIceServers()
        engine.acceptCall(call.callId, offerSdp, call.isVideo)
    }

    private suspend fun prepareIceServers() {
        val servers = mutableListOf<Triple<String, String?, String?>>(
            Triple("stun:stun.l.google.com:19302", null, null)
        )
        matrixRepository.getTurnServers().forEach { server ->
            server.urls.forEach { url ->
                servers.add(Triple(url, server.username, server.credential))
            }
        }
        engine.setIceServers(servers)
    }

    suspend fun rejectCall(callId: String? = null) {
        val call = currentCall ?: return reset()
        runCatching {
            matrixRepository.sendCallEvent(call.roomId, CallSignalingCommand.Reject(call.callId))
        }
        engine.hangup(call.callId)
        reset()
    }

    suspend fun endCall(callId: String? = null) {
        val call = currentCall ?: return
        runCatching {
            matrixRepository.sendCallEvent(call.roomId, CallSignalingCommand.Hangup(call.callId))
        }
        engine.hangup(call.callId)
        reset()
    }

    private fun reset() {
        _callState.value = CallState.Idle
        currentCall = null
        _isMuted.value = false
        _isVideoOff.value = false
    }

    fun toggleMute(isMuted: Boolean) {
        _isMuted.value = isMuted
        engine.setMuted(isMuted)
    }

    fun toggleVideo(isEnabled: Boolean) {
        _isVideoOff.value = !isEnabled
        engine.setVideoEnabled(isEnabled)
    }

    fun toggleSpeaker(speakerOn: Boolean) {
        _isSpeakerOn.value = speakerOn
        engine.setSpeakerOn(speakerOn)
    }

    fun switchCamera() {
        engine.switchCamera()
    }

    fun release() {
        engine.release()
    }

    fun getCurrentCallId(): String? = currentCall?.callId
}
