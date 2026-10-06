package com.matrix.messenger.call

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import org.webrtc.EglBase
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

@Composable
actual fun VideoTrackView(
    track: Any?,
    eglContext: Any?,
    modifier: Modifier,
    mirror: Boolean
) {
    val videoTrack = track as? VideoTrack
    val egl = eglContext as? EglBase.Context
    if (videoTrack == null || egl == null) {
        Box(modifier.background(Color.Black))
        return
    }
    key(videoTrack) {
        AndroidView(
            modifier = modifier,
            factory = { context ->
                SurfaceViewRenderer(context).apply {
                    init(egl, null)
                    setMirror(mirror)
                    setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                    videoTrack.addSink(this)
                }
            },
            onRelease = { renderer ->
                videoTrack.removeSink(renderer)
                renderer.release()
            }
        )
    }
}
