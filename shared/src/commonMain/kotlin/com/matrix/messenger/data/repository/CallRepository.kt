package com.matrix.messenger.data.repository

import com.matrix.messenger.call.WebRtcEngine
import com.matrix.messenger.call.WebRtcEvent
import com.matrix.messenger.call.createWebRtcEngine
import com.matrix.messenger.data.call.CallSignalingCommand
import com.matrix.messenger.data.call.CallSignalingEvent
import com.matrix.messenger.data.model.CallSession
import com.matrix.messenger.data.model.CallState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.folivo.trixnity.crypto.core.SecureRandom

class CallRepository(
    private val matrixRepository: MatrixRepository,
    private val engine: WebRtcEngine = createWebRtcEngine(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val mediaPreferences: suspend () -> Pair<String?, String?> = {
        SessionStore.readVideoDevice() to SessionStore.readAudioInputDevice()
    },
) {
    private val callMutex = Mutex()
    private var released = false

    val remoteVideoTrack = engine.remoteVideoTrack
    val localVideoTrack = engine.localVideoTrack
    val eglContext: Any? get() = engine.eglContext

    private val _callState = MutableStateFlow<CallState>(CallState.Idle)
    val callState: StateFlow<CallState> = _callState.asStateFlow()
    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()
    private val _isVideoOff = MutableStateFlow(false)
    val isVideoOff: StateFlow<Boolean> = _isVideoOff.asStateFlow()
    private val _isSpeakerOn = MutableStateFlow(true)
    val isSpeakerOn: StateFlow<Boolean> = _isSpeakerOn.asStateFlow()
    private var currentCall: CallSession? = null
    private val _currentCall = MutableStateFlow<CallSession?>(null)
    val currentCallSession: StateFlow<CallSession?> = _currentCall.asStateFlow()

    init {
        scope.launch {
            matrixRepository.callEvents.collect { event ->
                callMutex.withLock {
                    if (released) return@withLock
                    if (event is CallSignalingEvent.Incoming) {
                        onIncomingCall(event)
                    } else {
                        val call = currentCall ?: return@withLock
                        if (!matches(call, event)) return@withLock
                        when (event) {
                            is CallSignalingEvent.Answer -> engine.setRemoteAnswer(event.callId, event.answerSdp)
                            is CallSignalingEvent.Candidates -> engine.addCandidates(event.callId, event.candidates)
                            is CallSignalingEvent.Hangup, is CallSignalingEvent.Reject -> {
                                engine.hangup(call.callId)
                                reset()
                            }
                            is CallSignalingEvent.Incoming -> Unit
                        }
                    }
                }
            }
        }
        scope.launch {
            engine.events.collect { event ->
                callMutex.withLock {
                    if (released) return@withLock
                    val call = currentCall ?: return@withLock
                    val eventCallId = when (event) {
                        is WebRtcEvent.Offer -> event.callId
                        is WebRtcEvent.Answer -> event.callId
                        is WebRtcEvent.IceCandidates -> event.callId
                        is WebRtcEvent.ConnectionEstablished -> event.callId
                        is WebRtcEvent.ConnectionFailed -> event.callId
                        is WebRtcEvent.Error -> event.callId
                    }
                    if (eventCallId != null && eventCallId != call.callId) return@withLock
                    when (event) {
                        is WebRtcEvent.Offer -> matrixRepository.sendCallEvent(
                            call.roomId,
                            CallSignalingCommand.Invite(event.callId, event.sdp, call.isVideo,
                                invitee = call.peerUserId.ifBlank { null })
                        )
                        is WebRtcEvent.Answer -> matrixRepository.sendCallEvent(
                            call.roomId, CallSignalingCommand.Answer(event.callId, event.sdp)
                        )
                        is WebRtcEvent.IceCandidates -> matrixRepository.sendCallEvent(
                            call.roomId, CallSignalingCommand.Candidates(event.callId, event.candidates)
                        )
                        is WebRtcEvent.ConnectionEstablished ->
                            _callState.value = CallState.Connected(call.peerUserId, call.peerDisplayName)
                        is WebRtcEvent.ConnectionFailed -> {
                            runCatching {
                                matrixRepository.sendCallEvent(call.roomId, CallSignalingCommand.Hangup(call.callId))
                            }
                            engine.hangup(call.callId)
                            reset()
                            _callState.value = CallState.Ended("Соединение не удалось")
                        }
                        is WebRtcEvent.Error -> {
                            engine.hangup(call.callId)
                            reset()
                            _callState.value = CallState.Ended(event.message)
                        }
                    }
                }
            }
        }
    }

    // Null senders are supported for legacy callers; parsed network events always carry a sender.
    private fun matches(call: CallSession, event: CallSignalingEvent): Boolean =
        call.roomId == event.roomId && call.callId == event.callId &&
            (event.fromUserId == null || call.peerUserId == event.fromUserId)

    private suspend fun onIncomingCall(event: CallSignalingEvent.Incoming) {
        val active = currentCall
        // A repeated invite for the same session must not reject that session.
        if (active != null && matches(active, event)) return
        if (active != null || matrixRepository.isRoomMuted(event.roomId)) {
            runCatching {
                matrixRepository.sendCallEvent(event.roomId, CallSignalingCommand.Reject(event.callId))
            }
            return
        }
        currentCall = CallSession(
            callId = event.callId, roomId = event.roomId,
            peerUserId = event.fromUserId, peerDisplayName = event.fromUserId,
            isVideo = event.isVideo,
            startTime = kotlinx.datetime.Clock.System.now().toEpochMilliseconds(),
            offerSdp = event.offerSdp
        )
        _currentCall.value = currentCall
        _callState.value = CallState.Incoming(event.fromUserId, event.fromUserId)
    }

    suspend fun startCall(
        roomId: String, peerUserId: String, peerName: String, isVideo: Boolean,
        screenShareSourceId: Long? = null
    ) = callMutex.withLock {
        if (released || currentCall != null) return@withLock
        val callId = "call_" + SecureRandom.nextBytes(16).joinToString("") {
            (it.toInt() and 0xff).toString(16).padStart(2, '0')
        }
        currentCall = CallSession(
            callId = callId, roomId = roomId, peerUserId = peerUserId,
            peerDisplayName = peerName, isVideo = isVideo,
            startTime = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        )
        _currentCall.value = currentCall
        _callState.value = CallState.Outgoing(peerUserId, peerName)
        engine.initialize()
        prepareIceServers()
        applyMediaPreferences()
        engine.startCall(callId, isVideo, screenShareSourceId)
    }

    suspend fun acceptCall(callId: String? = null) = callMutex.withLock {
        if (released) return@withLock
        val call = currentCall ?: return@withLock
        if (callId != null && call.callId != callId) return@withLock
        if (_callState.value !is CallState.Incoming) return@withLock
        val offerSdp = call.offerSdp ?: return@withLock
        engine.initialize()
        prepareIceServers()
        applyMediaPreferences()
        engine.acceptCall(call.callId, offerSdp, call.isVideo)
    }

    private suspend fun applyMediaPreferences() {
        val (videoDevice, audioInputDevice) = mediaPreferences()
        engine.preferredVideoDevice = videoDevice
        engine.preferredAudioInputDevice = audioInputDevice
    }

    private suspend fun prepareIceServers() {
        val servers = mutableListOf<Triple<String, String?, String?>>(
            Triple("stun:stun.l.google.com:19302", null, null)
        )
        matrixRepository.getTurnServers().forEach { server ->
            server.urls.forEach { url -> servers.add(Triple(url, server.username, server.credential)) }
        }
        engine.setIceServers(servers)
    }

    suspend fun rejectCall(callId: String? = null) = callMutex.withLock {
        if (released) return@withLock
        val call = currentCall ?: return@withLock
        if (callId != null && call.callId != callId) return@withLock
        runCatching { matrixRepository.sendCallEvent(call.roomId, CallSignalingCommand.Reject(call.callId)) }
        engine.hangup(call.callId)
        reset()
    }

    suspend fun endCall(callId: String? = null) = callMutex.withLock {
        if (released) return@withLock
        val call = currentCall
        if (call == null) {
            if (callId == null) reset()
            return@withLock
        }
        if (callId != null && call.callId != callId) return@withLock
        runCatching { matrixRepository.sendCallEvent(call.roomId, CallSignalingCommand.Hangup(call.callId)) }
        engine.hangup(call.callId)
        reset()
    }

    private fun reset() {
        currentCall = null
        _currentCall.value = null
        _callState.value = CallState.Idle
        _isMuted.value = false
        _isVideoOff.value = false
        _isSpeakerOn.value = true
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
    fun switchCamera() = engine.switchCamera()

    fun release() {
        released = true
        scope.cancel()
        engine.release()
        reset()
    }

    fun getCurrentCallId(): String? = currentCall?.callId
}
