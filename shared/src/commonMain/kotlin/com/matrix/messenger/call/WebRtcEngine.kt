package com.matrix.messenger.call

import com.matrix.messenger.data.call.CallCandidate
import kotlinx.coroutines.flow.Flow

sealed interface WebRtcEvent {
    data class Offer(val callId: String, val sdp: String) : WebRtcEvent
    data class Answer(val callId: String, val sdp: String) : WebRtcEvent
    data class IceCandidates(val callId: String, val candidates: List<CallCandidate>) : WebRtcEvent
    data object ConnectionEstablished : WebRtcEvent
    data class ConnectionFailed(val callId: String) : WebRtcEvent
    data class Error(val message: String) : WebRtcEvent
}

/**
 * Platform WebRTC engine. Video tracks are exposed as platform objects (Any)
 * to keep the interface common; the Android UI casts them to [org.webrtc.VideoTrack].
 */
interface WebRtcEngine {
    val events: Flow<WebRtcEvent>

    /** Current remote video track (org.webrtc.VideoTrack on Android), if any. */
    val remoteVideoTrack: Flow<Any?>

    /** Current local video track (org.webrtc.VideoTrack on Android), if any. */
    val localVideoTrack: Flow<Any?>

    /** EGL context for video rendering (org.webrtc.EglBase.Context on Android), null otherwise. */
    val eglContext: Any?

    fun initialize()

    /** Creates a PeerConnection with local media and emits [WebRtcEvent.Offer]. */
    suspend fun startCall(callId: String, isVideo: Boolean)

    /** Creates a PeerConnection with local media, applies the offer and emits [WebRtcEvent.Answer]. */
    suspend fun acceptCall(callId: String, offerSdp: String, isVideo: Boolean)

    /** Caller side: applies the remote answer SDP. */
    suspend fun setRemoteAnswer(callId: String, answerSdp: String)

    suspend fun addCandidates(callId: String, candidates: List<CallCandidate>)

    suspend fun hangup(callId: String)

    /** Replaces the ICE servers used for the next PeerConnection (TURN/STUN). */
    fun setIceServers(servers: List<Triple<String, String?, String?>>)

    fun setMuted(muted: Boolean)

    fun setVideoEnabled(enabled: Boolean)

    fun switchCamera()

    fun setSpeakerOn(speakerOn: Boolean)

    fun release()
}

expect fun createWebRtcEngine(): WebRtcEngine
