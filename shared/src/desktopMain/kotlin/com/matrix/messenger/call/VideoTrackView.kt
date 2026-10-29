package com.matrix.messenger.call

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.graphics.Color
import dev.onvoid.webrtc.media.FourCC
import dev.onvoid.webrtc.media.video.VideoBufferConverter
import dev.onvoid.webrtc.media.video.VideoFrame
import dev.onvoid.webrtc.media.video.VideoTrack
import dev.onvoid.webrtc.media.video.VideoTrackSink
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.image.BufferedImage
import javax.swing.JPanel
import javax.swing.SwingUtilities

@Composable
actual fun VideoTrackView(
    track: Any?,
    eglContext: Any?,
    modifier: Modifier,
    mirror: Boolean
) {
    val videoTrack = track as? VideoTrack
    if (videoTrack == null) {
        Box(modifier.background(Color.Black))
        return
    }
    key(videoTrack) {
        val panel = remember(videoTrack) { VideoFramePanel(videoTrack, mirror) }
        DisposableEffect(panel) {
            onDispose { panel.dispose() }
        }
        SwingPanel(
            modifier = modifier,
            factory = { panel }
        )
    }
}

/** Swing surface rendering VideoTrack frames via a VideoTrackSink (I420 -> ARGB conversion). */
private class VideoFramePanel(
    private val track: VideoTrack,
    private val mirror: Boolean
) : JPanel(), VideoTrackSink {

    @Volatile
    private var image: BufferedImage? = null

    init {
        track.addSink(this)
    }

    fun dispose() {
        runCatching { track.removeSink(this) }
        image = null
    }

    override fun onVideoFrame(frame: VideoFrame) {
        try {
            val buffer = frame.buffer
            val width = buffer.width
            val height = buffer.height
            val argb = ByteArray(width * height * 4)
            VideoBufferConverter.convertFromI420(buffer, argb, FourCC.ARGB)

            val pixels = IntArray(width * height)
            for (i in 0 until width * height) {
                val a = argb[i * 4].toInt() and 0xFF
                val r = argb[i * 4 + 1].toInt() and 0xFF
                val g = argb[i * 4 + 2].toInt() and 0xFF
                val b = argb[i * 4 + 3].toInt() and 0xFF
                pixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
            }
            val converted = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
            converted.raster.setDataElements(0, 0, width, height, pixels)
            image = converted
            SwingUtilities.invokeLater { repaint() }
        } catch (_: Throwable) {
            // Drop frames we cannot convert; rendering must never crash the call.
        }
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val img = image ?: return
        val cw = width.toDouble()
        val ch = height.toDouble()
        if (cw <= 0 || ch <= 0) return

        // Aspect-fill scaling, centered.
        val scale = maxOf(cw / img.width, ch / img.height)
        val dw = img.width * scale
        val dh = img.height * scale
        val dx = (cw - dw) / 2
        val dy = (ch - dh) / 2

        val g2 = g.create() as Graphics2D
        if (mirror) {
            g2.translate(dx + dw, dy)
            g2.scale(-scale, scale)
        } else {
            g2.translate(dx, dy)
            g2.scale(scale, scale)
        }
        g2.drawImage(img, 0, 0, null)
        g2.dispose()
    }
}
