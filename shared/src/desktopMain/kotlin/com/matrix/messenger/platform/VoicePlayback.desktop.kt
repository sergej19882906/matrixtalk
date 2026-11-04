package com.matrix.messenger.platform

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.awt.Desktop
import java.io.File

/**
 * Desktop: no controllable JVM audio pipeline for m4a/ogg — playback opens
 * the system player, so no live state is exposed.
 */
actual object VoicePlayback {

    private val _state = MutableStateFlow<VoicePlaybackState?>(null)
    actual val state: StateFlow<VoicePlaybackState?> = _state.asStateFlow()

    actual fun toggle(sourceId: String, source: String) {
        runCatching {
            if (source.startsWith("http")) {
                Desktop.getDesktop().browse(java.net.URI(source))
            } else {
                Desktop.getDesktop().open(File(source.removePrefix("file://")))
            }
        }
    }

    actual fun seekTo(fraction: Float) {
        // no-op: system player handles position
    }

    actual fun stop() {
        // nothing to stop
    }
}
