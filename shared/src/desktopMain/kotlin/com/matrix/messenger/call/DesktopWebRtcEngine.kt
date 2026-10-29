package com.matrix.messenger.call

import com.matrix.messenger.data.call.CallCandidate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import dev.onvoid.webrtc.CreateSessionDescriptionObserver
import dev.onvoid.webrtc.PeerConnectionFactory
import dev.onvoid.webrtc.PeerConnectionObserver
import dev.onvoid.webrtc.RTCAnswerOptions
import dev.onvoid.webrtc.RTCConfiguration
import dev.onvoid.webrtc.RTCIceCandidate
import dev.onvoid.webrtc.RTCIceConnectionState
import dev.onvoid.webrtc.RTCIceServer
import dev.onvoid.webrtc.RTCOfferOptions
import dev.onvoid.webrtc.RTCPeerConnection
import dev.onvoid.webrtc.RTCPeerConnectionState
import dev.onvoid.webrtc.RTCSdpType
import dev.onvoid.webrtc.RTCSessionDescription
import dev.onvoid.webrtc.RTCRtpTransceiverDirection
import dev.onvoid.webrtc.RTCRtpTransceiverInit
import dev.onvoid.webrtc.SetSessionDescriptionObserver
import dev.onvoid.webrtc.media.MediaStream
import dev.onvoid.webrtc.media.MediaStreamTrack
import dev.onvoid.webrtc.media.audio.AudioDeviceModule
import dev.onvoid.webrtc.media.audio.AudioOptions
import dev.onvoid.webrtc.media.audio.AudioTrack
import dev.onvoid.webrtc.media.audio.AudioTrackSource
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

actual fun createWebRtcEngine(): WebRtcEngine = DesktopWebRtcEngine()

/**
 * Desktop WebRTC engine backed by webrtc-java (JNI bindings to Google WebRTC).
 * Mirrors AndroidWebRtcEngine logic: Unified Plan transceivers, ICE candidate
 * buffering until the remote description is set, suspend SDP helpers.
 */
private class DesktopWebRtcEngine : WebRtcEngine {

    private val _events = MutableSharedFlow<WebRtcEvent>(extraBufferCapacity = 64)
    override val events: SharedFlow<WebRtcEvent> = _events

    private val _remoteVideoTrack = MutableStateFlow<Any?>(null)
    override val remoteVideoTrack: StateFlow<Any?> = _remoteVideoTrack

    private val _localVideoTrack = MutableStateFlow<Any?>(null)
    override val localVideoTrack: StateFlow<Any?> = _localVideoTrack

    override val eglContext: Any? = null

    private var audioDeviceModule: AudioDeviceModule? = null
    private var factory: PeerConnectionFactory? = null
    private var peerConnection: RTCPeerConnection? = null
    private var audioTrackSource: AudioTrackSource? = null
    private var localAudioTrack: AudioTrack? = null
    private var currentCallId: String? = null
    private var remoteDescriptionSet = false
    private var pendingRemoteCandidates = mutableListOf<RTCIceCandidate>()

    private var iceServers: List<RTCIceServer> = listOf(
        RTCIceServer().apply { urls = listOf("stun:stun.l.google.com:19302") }
    )

    override fun initialize() {
        if (factory != null) return
        try {
            val adm = AudioDeviceModule()
            audioDeviceModule = adm
            factory = PeerConnectionFactory(adm)
        } catch (t: Throwable) {
            _events.tryEmit(WebRtcEvent.Error("Не удалось инициализировать аудио: ${t.message ?: t::class.simpleName}"))
        }
    }

    override suspend fun startCall(callId: String, isVideo: Boolean) {
        withContext(Dispatchers.Default) {
            runCatching {
                initialize()
                prepareConnection(callId)
                val pc = peerConnection ?: error("PeerConnection not created")
                val offer = pc.awaitCreateOffer()
                pc.awaitSetLocalDescription(offer)
                _events.emit(WebRtcEvent.Offer(callId, offer.sdp))
            }.onFailure { _events.emit(WebRtcEvent.Error(it.message ?: "Failed to start call")) }
        }
    }

    override suspend fun acceptCall(callId: String, offerSdp: String, isVideo: Boolean) {
        withContext(Dispatchers.Default) {
            runCatching {
                initialize()
                prepareConnection(callId)
                val pc = peerConnection ?: error("PeerConnection not created")
                pc.awaitSetRemoteDescription(RTCSessionDescription(RTCSdpType.OFFER, offerSdp))
                remoteDescriptionSet = true
                flushPendingCandidates()
                val answer = pc.awaitCreateAnswer()
                pc.awaitSetLocalDescription(answer)
                _events.emit(WebRtcEvent.Answer(callId, answer.sdp))
            }.onFailure { _events.emit(WebRtcEvent.Error(it.message ?: "Failed to accept call")) }
        }
    }

    override suspend fun setRemoteAnswer(callId: String, answerSdp: String) =
        withContext(Dispatchers.Default) {
            runCatching {
                val pc = peerConnection ?: return@withContext
                pc.awaitSetRemoteDescription(RTCSessionDescription(RTCSdpType.ANSWER, answerSdp))
                remoteDescriptionSet = true
                flushPendingCandidates()
            }.onFailure { _events.emit(WebRtcEvent.Error(it.message ?: "Failed to set remote answer")) }
        }

    override suspend fun addCandidates(callId: String, candidates: List<CallCandidate>) =
        withContext(Dispatchers.Default) {
            val pc = peerConnection ?: return@withContext
            candidates.forEach { candidate ->
                val iceCandidate = RTCIceCandidate(candidate.sdpMid, candidate.sdpMLineIndex ?: 0, candidate.candidate)
                if (remoteDescriptionSet) {
                    pc.addIceCandidate(iceCandidate)
                } else {
                    synchronized(pendingRemoteCandidates) { pendingRemoteCandidates.add(iceCandidate) }
                }
            }
        }

    override suspend fun hangup(callId: String) = withContext(Dispatchers.Default) {
        cleanupCall()
    }

    override fun setIceServers(servers: List<Triple<String, String?, String?>>) {
        iceServers = servers.map { (url, username, password) ->
            RTCIceServer().apply {
                urls = listOf(url)
                if (username != null) this.username = username
                if (password != null) this.password = password
            }
        }
    }

    override fun setMuted(muted: Boolean) {
        runCatching { localAudioTrack?.setEnabled(!muted) }
    }

    override fun setVideoEnabled(enabled: Boolean) {
        // Local video capture arrives in Phase 2.4; remote rendering is controlled separately.
    }

    override fun switchCamera() = Unit

    override fun setSpeakerOn(speakerOn: Boolean) {
        // webrtc-java uses the default output device; per-device selection is a Phase 2.2/2.5 item.
    }

    override fun release() {
        runCatching { audioDeviceModule?.dispose() }
        audioDeviceModule = null
        factory = null
    }

    private fun prepareConnection(callId: String) {
        cleanupCall()
        currentCallId = callId
        remoteDescriptionSet = false
        synchronized(pendingRemoteCandidates) { pendingRemoteCandidates.clear() }

        val factory = factory ?: error("PeerConnectionFactory not initialized")
        val config = RTCConfiguration().apply { iceServers = this@DesktopWebRtcEngine.iceServers }
        peerConnection = factory.createPeerConnection(config, object : PeerConnectionObserver {
            override fun onIceCandidate(candidate: RTCIceCandidate) {
                currentCallId?.let { callId ->
                    _events.tryEmit(
                        WebRtcEvent.IceCandidates(callId, listOf(candidate.toCallCandidate()))
                    )
                }
            }

            override fun onConnectionChange(state: RTCPeerConnectionState) {
                when (state) {
                    RTCPeerConnectionState.CONNECTED -> _events.tryEmit(WebRtcEvent.ConnectionEstablished)
                    RTCPeerConnectionState.FAILED -> currentCallId?.let {
                        _events.tryEmit(WebRtcEvent.ConnectionFailed(it))
                    }
                    else -> Unit
                }
            }

            override fun onIceConnectionChange(state: RTCIceConnectionState) {
                // Fallback for stacks not reporting the standardized state.
                when (state) {
                    RTCIceConnectionState.CONNECTED, RTCIceConnectionState.COMPLETED ->
                        _events.tryEmit(WebRtcEvent.ConnectionEstablished)

                    RTCIceConnectionState.FAILED ->
                        currentCallId?.let { _events.tryEmit(WebRtcEvent.ConnectionFailed(it)) }

                    else -> Unit
                }
            }

            override fun onAddStream(stream: MediaStream) {
                val track = stream.videoTracks.firstOrNull()
                if (track != null) _remoteVideoTrack.value = track
            }

            override fun onTrack(transceiver: dev.onvoid.webrtc.RTCRtpTransceiver) {
                val track = transceiver.receiver?.track
                if (track != null && track.kind == MediaStreamTrack.VIDEO_TRACK_KIND) {
                    _remoteVideoTrack.value = track
                }
            }
        })

        addLocalMedia()
    }

    private fun addLocalMedia() {
        val factory = factory ?: return
        val pc = peerConnection ?: return

        val source = factory.createAudioSource(AudioOptions().apply {
            echoCancellation = true
            noiseSuppression = true
            autoGainControl = true
        })
        audioTrackSource = source
        val audioTrack = factory.createAudioTrack("matrixtalk-audio", source)
        localAudioTrack = audioTrack
        pc.addTransceiver(
            audioTrack,
            RTCRtpTransceiverInit().apply { direction = RTCRtpTransceiverDirection.SEND_RECV }
        )
    }

    private fun flushPendingCandidates() {
        val pc = peerConnection ?: return
        synchronized(pendingRemoteCandidates) {
            pendingRemoteCandidates.forEach { pc.addIceCandidate(it) }
            pendingRemoteCandidates.clear()
        }
    }

    private fun cleanupCall() {
        runCatching { peerConnection?.setAudioRecording(false) }
        runCatching { peerConnection?.setAudioPlayout(false) }
        runCatching { localAudioTrack?.dispose() }
        localAudioTrack = null
        runCatching { audioTrackSource?.dispose() }
        audioTrackSource = null
        runCatching { peerConnection?.close() }
        peerConnection = null
        _remoteVideoTrack.value = null
        currentCallId = null
        remoteDescriptionSet = false
        synchronized(pendingRemoteCandidates) { pendingRemoteCandidates.clear() }
    }

    private fun RTCIceCandidate.toCallCandidate() = CallCandidate(
        candidate = sdp,
        sdpMid = sdpMid,
        sdpMLineIndex = sdpMLineIndex
    )

    private suspend fun RTCPeerConnection.awaitCreateOffer(): RTCSessionDescription =
        awaitSdpCreate { observer -> createOffer(RTCOfferOptions(), observer) }

    private suspend fun RTCPeerConnection.awaitCreateAnswer(): RTCSessionDescription =
        awaitSdpCreate { observer -> createAnswer(RTCAnswerOptions(), observer) }

    private suspend fun RTCPeerConnection.awaitSetLocalDescription(sdp: RTCSessionDescription): Unit =
        awaitSdpSet { observer -> setLocalDescription(sdp, observer) }

    private suspend fun RTCPeerConnection.awaitSetRemoteDescription(sdp: RTCSessionDescription): Unit =
        awaitSdpSet { observer -> setRemoteDescription(sdp, observer) }

    private suspend fun awaitSdpCreate(
        action: (CreateSessionDescriptionObserver) -> Unit
    ): RTCSessionDescription = suspendCancellableCoroutine { cont ->
        action(object : CreateSessionDescriptionObserver {
            override fun onSuccess(description: RTCSessionDescription) {
                if (cont.isActive) cont.resume(description)
            }

            override fun onFailure(error: String) {
                if (cont.isActive) cont.resumeWithException(RuntimeException(error))
            }
        })
    }

    private suspend fun awaitSdpSet(
        action: (SetSessionDescriptionObserver) -> Unit
    ): Unit = suspendCancellableCoroutine { cont ->
        action(object : SetSessionDescriptionObserver {
            override fun onSuccess() {
                if (cont.isActive) cont.resume(Unit)
            }

            override fun onFailure(error: String) {
                if (cont.isActive) cont.resumeWithException(RuntimeException(error))
            }
        })
    }
}
