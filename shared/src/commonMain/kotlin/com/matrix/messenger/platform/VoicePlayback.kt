package com.matrix.messenger.platform

import kotlinx.coroutines.flow.StateFlow

/** Playback state of the single active voice message. */
data class VoicePlaybackState(
    val sourceId: String,
    val positionMs: Long,
    val durationMs: Long,
    val playing: Boolean
)

/**
 * Single-voice playback controller. Toggle switches between play / pause /
 * switching to another message. Commands are serialized by each platform and
 * asynchronous preparation/desktop IO never retains a retired playback session.
 * Position tracks consumed audio rather than bytes queued to the output device.
 * Desktop falls back to the system player for unsupported containers such as
 * M4A/AAC (state stays null there, after stopping the previous in-app session).
 */
expect object VoicePlayback {
    val state: StateFlow<VoicePlaybackState?>

    fun toggle(sourceId: String, source: String)
    fun seekTo(fraction: Float)
    fun stop()
}
