package com.matrix.messenger.call

import android.content.Context
import android.media.AudioManager
import com.matrix.messenger.data.call.CallCandidate
import com.matrix.messenger.platform.appContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoCapturer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

actual fun createWebRtcEngine(): WebRtcEngine = AndroidWebRtcEngine()

private class AndroidWebRtcEngine : WebRtcEngine {

    private val _events = MutableSharedFlow<WebRtcEvent>(extraBufferCapacity = 64)
    override val events: SharedFlow<WebRtcEvent> = _events

    private val _remoteVideoTrack = MutableStateFlow<Any?>(null)
    override val remoteVideoTrack: StateFlow<Any?> = _remoteVideoTrack

    private val _localVideoTrack = MutableStateFlow<Any?>(null)
    override val localVideoTrack: StateFlow<Any?> = _localVideoTrack

    override val eglContext: Any?
        get() = eglBase?.eglBaseContext

    private var factory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var eglBase: EglBase? = null
    private var audioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null
    private var videoSource: VideoSource? = null
    private var localVideoTrackInternal: VideoTrack? = null
    private var videoCapturer: VideoCapturer? = null
    private var currentCallId: String? = null
    private var remoteDescriptionSet = false
    private val pendingRemoteCandidates = mutableListOf<IceCandidate>()

    private var iceServers: List<PeerConnection.IceServer> = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
    )

    override fun initialize() {
        if (factory != null) return
        val context = appContext ?: run {
            _events.tryEmit(WebRtcEvent.Error("Application context not initialized"))
            return
        }
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context).createInitializationOptions()
        )
        val egl = EglBase.create()
        eglBase = egl
        factory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(egl.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(egl.eglBaseContext))
            .createPeerConnectionFactory()
    }

    override suspend fun startCall(callId: String, isVideo: Boolean) {
        withContext(Dispatchers.Main) {
            runCatching {
                initialize()
                prepareConnection(callId, isVideo)
                val pc = peerConnection ?: error("PeerConnection not created")
                val offer = pc.awaitCreateOffer()
                pc.awaitSetLocalDescription(offer)
                _events.emit(WebRtcEvent.Offer(callId, offer.description))
            }.onFailure { _events.emit(WebRtcEvent.Error(it.message ?: "Failed to start call")) }
        }
    }

    override suspend fun acceptCall(callId: String, offerSdp: String, isVideo: Boolean) {
        withContext(Dispatchers.Main) {
            runCatching {
                initialize()
                prepareConnection(callId, isVideo)
                val pc = peerConnection ?: error("PeerConnection not created")
                val offer = SessionDescription(SessionDescription.Type.OFFER, offerSdp)
                pc.awaitSetRemoteDescription(offer)
                remoteDescriptionSet = true
                flushPendingCandidates()
                val answer = pc.awaitCreateAnswer()
                pc.awaitSetLocalDescription(answer)
                _events.emit(WebRtcEvent.Answer(callId, answer.description))
            }.onFailure { _events.emit(WebRtcEvent.Error(it.message ?: "Failed to accept call")) }
        }
    }

    override suspend fun setRemoteAnswer(callId: String, answerSdp: String) =
        withContext(Dispatchers.Main) {
            runCatching {
                val pc = peerConnection ?: return@withContext
                pc.awaitSetRemoteDescription(
                    SessionDescription(SessionDescription.Type.ANSWER, answerSdp)
                )
                remoteDescriptionSet = true
                flushPendingCandidates()
            }.onFailure { _events.emit(WebRtcEvent.Error(it.message ?: "Failed to set remote answer")) }
        }

    override suspend fun addCandidates(callId: String, candidates: List<CallCandidate>) =
        withContext(Dispatchers.Main) {
            val pc = peerConnection ?: return@withContext
            candidates.forEach { candidate ->
                val iceCandidate = IceCandidate(candidate.sdpMid, candidate.sdpMLineIndex ?: 0, candidate.candidate)
                if (remoteDescriptionSet) {
                    pc.addIceCandidate(iceCandidate)
                } else {
                    pendingRemoteCandidates.add(iceCandidate)
                }
            }
        }

    override suspend fun hangup(callId: String) = withContext(Dispatchers.Main) {
        cleanupCall()
    }

    override fun setIceServers(servers: List<Triple<String, String?, String?>>) {
        iceServers = servers.map { (url, username, password) ->
            val builder = PeerConnection.IceServer.builder(url)
            if (username != null && password != null) {
                builder.setUsername(username).setPassword(password)
            }
            builder.createIceServer()
        }
    }

    override fun setMuted(muted: Boolean) {
        localAudioTrack?.setEnabled(!muted)
    }

    override fun setVideoEnabled(enabled: Boolean) {
        localVideoTrackInternal?.setEnabled(enabled)
    }

    override fun switchCamera() {
        (videoCapturer as? CameraVideoCapturer)?.switchCamera(null)
    }

    override fun setSpeakerOn(speakerOn: Boolean) {
        val context = appContext ?: return
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager.isSpeakerphoneOn = speakerOn
    }

    override fun release() {
        val context = appContext ?: return
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.mode = AudioManager.MODE_NORMAL
        audioManager.isSpeakerphoneOn = false
    }

    private fun prepareConnection(callId: String, isVideo: Boolean) {
        cleanupCall()
        currentCallId = callId
        remoteDescriptionSet = false
        pendingRemoteCandidates.clear()

        val factory = factory ?: error("PeerConnectionFactory not initialized")
        peerConnection = factory.createPeerConnection(iceServers, object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState?) = Unit

            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) = Unit

            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                when (state) {
                    PeerConnection.IceConnectionState.CONNECTED,
                    PeerConnection.IceConnectionState.COMPLETED -> _events.tryEmit(WebRtcEvent.ConnectionEstablished)

                    PeerConnection.IceConnectionState.FAILED ->
                        currentCallId?.let { _events.tryEmit(WebRtcEvent.ConnectionFailed(it)) }

                    else -> Unit
                }
            }

            override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit

            override fun onIceCandidate(candidate: IceCandidate) {
                currentCallId?.let { callId ->
                    _events.tryEmit(
                        WebRtcEvent.IceCandidates(callId, listOf(candidate.toCallCandidate()))
                    )
                }
            }

            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) = Unit

            override fun onAddStream(stream: MediaStream) {
                val track = stream.videoTracks.firstOrNull()
                if (track != null) _remoteVideoTrack.value = track
            }

            override fun onRemoveStream(stream: MediaStream) {
                _remoteVideoTrack.value = null
            }

            override fun onDataChannel(channel: org.webrtc.DataChannel?) = Unit

            override fun onRenegotiationNeeded() = Unit

            override fun onTrack(transceiver: RtpTransceiver) {
                val track = transceiver.receiver?.track()
                if (track is VideoTrack) _remoteVideoTrack.value = track
            }
        })

        addLocalMedia(isVideo)
    }

    private fun addLocalMedia(withVideo: Boolean) {
        val factory = factory ?: return
        val pc = peerConnection ?: return

        val source = factory.createAudioSource(MediaConstraints())
        audioSource = source
        val audioTrack = factory.createAudioTrack("ARDAMSa0", source)
        localAudioTrack = audioTrack
        pc.addTransceiver(
            audioTrack,
            RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.SEND_RECV)
        )

        if (withVideo) {
            val capturer = createCameraCapturer() ?: return
            val eglContext = eglBase?.eglBaseContext ?: return
            val surfaceHelper = SurfaceTextureHelper.create("CaptureThread", eglContext)
            val source = factory.createVideoSource(false)
            capturer.initialize(surfaceHelper, appContext, source.capturerObserver)
            capturer.startCapture(1280, 720, 30)
            val videoTrack = factory.createVideoTrack("ARDAMSv0", source)
            videoSource = source
            videoCapturer = capturer
            localVideoTrackInternal = videoTrack
            _localVideoTrack.value = videoTrack
            pc.addTransceiver(
                videoTrack,
                RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.SEND_RECV)
            )
        }
    }

    private fun createCameraCapturer(): VideoCapturer? {
        val context = appContext ?: return null
        val enumerator = Camera2Enumerator(context)
        val deviceNames = enumerator.deviceNames
        val front = deviceNames.firstOrNull { enumerator.isFrontFacing(it) }
        val back = deviceNames.firstOrNull { enumerator.isBackFacing(it) }
        val selected = front ?: back ?: return null
        return enumerator.createCapturer(selected, null)
    }

    private fun flushPendingCandidates() {
        val pc = peerConnection ?: return
        pendingRemoteCandidates.forEach { pc.addIceCandidate(it) }
        pendingRemoteCandidates.clear()
    }

    private fun cleanupCall() {
        runCatching { videoCapturer?.stopCapture() }
        runCatching { videoCapturer?.dispose() }
        videoCapturer = null
        runCatching { localVideoTrackInternal?.dispose() }
        localVideoTrackInternal = null
        _localVideoTrack.value = null
        runCatching { videoSource?.dispose() }
        videoSource = null
        runCatching { localAudioTrack?.dispose() }
        localAudioTrack = null
        runCatching { audioSource?.dispose() }
        audioSource = null
        runCatching { peerConnection?.close() }
        peerConnection = null
        _remoteVideoTrack.value = null
        currentCallId = null
        remoteDescriptionSet = false
        pendingRemoteCandidates.clear()
    }

    private fun IceCandidate.toCallCandidate() = CallCandidate(
        candidate = sdp,
        sdpMid = sdpMid,
        sdpMLineIndex = sdpMLineIndex
    )

    private suspend fun PeerConnection.awaitCreateOffer(): SessionDescription =
        awaitSdp { observer -> createOffer(observer, MediaConstraints()) }

    private suspend fun PeerConnection.awaitCreateAnswer(): SessionDescription =
        awaitSdp { observer -> createAnswer(observer, MediaConstraints()) }

    private suspend fun PeerConnection.awaitSetLocalDescription(sdp: SessionDescription): Unit =
        awaitSdpSet { observer -> setLocalDescription(observer, sdp) }

    private suspend fun PeerConnection.awaitSetRemoteDescription(sdp: SessionDescription): Unit =
        awaitSdpSet { observer -> setRemoteDescription(observer, sdp) }

    private suspend fun PeerConnection.awaitSdp(
        action: PeerConnection.(SdpObserver) -> Unit
    ): SessionDescription = suspendCancellableCoroutine { cont ->
        action(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription) {
                if (cont.isActive) cont.resume(sdp)
            }

            override fun onSetSuccess() = Unit

            override fun onCreateFailure(error: String) {
                if (cont.isActive) cont.resumeWithException(RuntimeException(error))
            }

            override fun onSetFailure(error: String) {
                if (cont.isActive) cont.resumeWithException(RuntimeException(error))
            }
        })
    }

    private suspend fun PeerConnection.awaitSdpSet(
        action: PeerConnection.(SdpObserver) -> Unit
    ): Unit = suspendCancellableCoroutine { cont ->
        action(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription) = Unit

            override fun onSetSuccess() {
                if (cont.isActive) cont.resume(Unit)
            }

            override fun onCreateFailure(error: String) = Unit

            override fun onSetFailure(error: String) {
                if (cont.isActive) cont.resumeWithException(RuntimeException(error))
            }
        })
    }
}
