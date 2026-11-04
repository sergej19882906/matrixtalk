package com.matrix.messenger.platform

/** Platform voice recorder; produces an audio file on stop(). */
interface VoiceRecorder {
    val isRecording: Boolean
    /** Peak amplitudes collected during the current/last recording (MSC3245 scale 0..1024). */
    val waveform: List<Int>
    fun start(): Boolean
    /** Stops and returns the recorded file path, or null on failure. */
    fun stop(): String?
    fun cancel()
}

expect fun createVoiceRecorder(): VoiceRecorder

/** Plays an audio source (http(s) URL or local file path). Fire-and-forget. */
expect fun playAudio(source: String)
