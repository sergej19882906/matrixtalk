package com.matrix.messenger.call

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Renders a platform video track (org.webrtc.VideoTrack on Android).
 * [eglContext] is the platform EGL context from [WebRtcEngine.eglContext].
 * No-op on platforms without a WebRTC engine.
 */
@Composable
expect fun VideoTrackView(
    track: Any?,
    eglContext: Any?,
    modifier: Modifier = Modifier,
    mirror: Boolean = false
)
