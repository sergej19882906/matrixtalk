package com.matrix.messenger.call

import com.matrix.messenger.data.call.CallCandidate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

actual fun createWebRtcEngine(): WebRtcEngine = DesktopWebRtcEngine()

private class DesktopWebRtcEngine : WebRtcEngine {

    private val _events = MutableSharedFlow<WebRtcEvent>(extraBufferCapacity = 16)
    override val events: Flow<WebRtcEvent> = _events

    private val _remoteVideoTrack = MutableStateFlow<Any?>(null)
    override val remoteVideoTrack: Flow<Any?> = _remoteVideoTrack

    private val _localVideoTrack = MutableStateFlow<Any?>(null)
    override val localVideoTrack: Flow<Any?> = _localVideoTrack

    override val eglContext: Any? = null

    override fun initialize() = Unit

    override suspend fun startCall(callId: String, isVideo: Boolean) {
        _events.emit(WebRtcEvent.Error("Звонки на Desktop появятся в ближайших версиях"))
    }

    override suspend fun acceptCall(callId: String, offerSdp: String, isVideo: Boolean) {
        _events.emit(WebRtcEvent.Error("Звонки на Desktop появятся в ближайших версиях"))
    }

    override suspend fun setRemoteAnswer(callId: String, answerSdp: String) = Unit

    override suspend fun addCandidates(callId: String, candidates: List<CallCandidate>) = Unit

    override suspend fun hangup(callId: String) = Unit

    override fun setIceServers(servers: List<Triple<String, String?, String?>>) = Unit

    override fun setMuted(muted: Boolean) = Unit

    override fun setVideoEnabled(enabled: Boolean) = Unit

    override fun switchCamera() = Unit

    override fun setSpeakerOn(speakerOn: Boolean) = Unit

    override fun release() = Unit
}
