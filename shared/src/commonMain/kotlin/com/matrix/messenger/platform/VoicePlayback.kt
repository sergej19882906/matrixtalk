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
 * switching to another message. Desktop falls back to the system player
 * (state stays null there).
 */
expect object VoicePlayback {
    val state: StateFlow<VoicePlaybackState?>

    fun toggle(sourceId: String, source: String)
    fun seekTo(fraction: Float)
    fun stop()
}
