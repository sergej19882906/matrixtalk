package com.matrix.messenger.ui.call

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.matrix.messenger.data.model.CallSession
import com.matrix.messenger.data.model.CallState
import com.matrix.messenger.data.repository.CallRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CallViewModel(
    private val callRepository: CallRepository
) : ViewModel() {

    private val _callState = MutableStateFlow<CallState>(CallState.Idle)
    val callState: StateFlow<CallState> = _callState.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _isVideoEnabled = MutableStateFlow(true)
    val isVideoEnabled: StateFlow<Boolean> = _isVideoEnabled.asStateFlow()

    val session: StateFlow<CallSession?> = callRepository.currentCallSession
    val isSpeakerOn: StateFlow<Boolean> = callRepository.isSpeakerOn

    val remoteVideoTrack = callRepository.remoteVideoTrack
    val localVideoTrack = callRepository.localVideoTrack
    val eglContext: Any? get() = callRepository.eglContext

    init {
        viewModelScope.launch {
            callRepository.isMuted.collect { _isMuted.value = it }
        }
        viewModelScope.launch {
            callRepository.isVideoOff.collect { _isVideoEnabled.value = !it }
        }
    }

    private val _callDuration = MutableStateFlow(0L)
    val callDuration: StateFlow<Long> = _callDuration.asStateFlow()

    private var callTimerJob: Job? = null
    private var callStartTime: Long = 0L

    init {
        viewModelScope.launch {
            callRepository.callState.collect { state ->
                _callState.value = state
                when (state) {
                    is CallState.Connected -> startCallTimer()
                    is CallState.Ended, CallState.Idle -> stopCallTimer()
                    else -> { /* ignore */ }
                }
            }
        }
    }

    fun startCall(roomId: String, peerUserId: String, peerName: String, isVideo: Boolean) {
        viewModelScope.launch {
            try {
                callRepository.startCall(roomId, peerUserId, peerName, isVideo)
            } catch (e: Exception) {
                _callState.value = CallState.Ended("Ошибка: ${e.message}")
            }
        }
    }

    fun acceptCall(callId: String? = null) {
        viewModelScope.launch {
            try {
                callRepository.acceptCall(callId)
            } catch (e: Exception) {
                _callState.value = CallState.Ended("Ошибка: ${e.message}")
            }
        }
    }

    fun rejectCall() {
        viewModelScope.launch {
            callRepository.rejectCall()
        }
    }

    fun endCall() {
        viewModelScope.launch {
            callRepository.endCall()
        }
    }

    fun toggleMute() {
        callRepository.toggleMute(!_isMuted.value)
    }

    fun toggleVideo() {
        callRepository.toggleVideo(!_isVideoEnabled.value)
    }

    fun toggleSpeaker() {
        callRepository.toggleSpeaker(!isSpeakerOn.value)
    }

    fun switchCamera() {
        callRepository.switchCamera()
    }

    private fun startCallTimer() {
        callStartTime = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        callTimerJob?.cancel()
        callTimerJob = viewModelScope.launch {
            while (true) {
                _callDuration.value = (kotlinx.datetime.Clock.System.now().toEpochMilliseconds() - callStartTime) / 1000
                delay(1000)
            }
        }
    }

    private fun stopCallTimer() {
        callTimerJob?.cancel()
        callTimerJob = null
        _callDuration.value = 0L
    }

    fun formatDuration(durationSeconds: Long): String {
        val minutes = durationSeconds / 60
        val seconds = durationSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    override fun onCleared() {
        super.onCleared()
        callTimerJob?.cancel()
    }
}
