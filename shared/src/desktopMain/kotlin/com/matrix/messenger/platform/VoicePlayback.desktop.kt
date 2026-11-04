package com.matrix.messenger.platform

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine

/**
 * Desktop voice playback: fully decoded to PCM (WAV / Ogg-Opus via
 * vorbis-java + Concentus) and played through SourceDataLine with
 * pause / resume / position tracking / seek. Undecodable formats fall back
 * to the system player (state stays null).
 */
actual object VoicePlayback {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<VoicePlaybackState?>(null)
    actual val state: StateFlow<VoicePlaybackState?> = _state.asStateFlow()

    private var line: SourceDataLine? = null
    private var playbackThread: Thread? = null
    private var currentSourceId: String? = null
    private var audio: DecodedAudio? = null
    private var framePosition: Long = 0
    private var paused = false
    private var stopped = false

    actual fun toggle(sourceId: String, source: String) {
        val current = _state.value
        if (current?.sourceId == sourceId) {
            if (paused) {
                paused = false
                _state.value = current.copy(playing = true)
            } else {
                paused = true
                _state.value = current.copy(playing = false)
            }
            return
        }
        val decoded = AudioDecode.decode(source)
        if (decoded == null || decoded.pcm.isEmpty()) {
            // Fallback: system player (m4a/AAC and exotic formats)
            runCatching {
                if (source.startsWith("http")) {
                    java.awt.Desktop.getDesktop().browse(java.net.URI(source))
                } else {
                    java.awt.Desktop.getDesktop().open(File(source.removePrefix("file://")))
                }
            }
            return
        }
        stop()
        currentSourceId = sourceId
        audio = decoded
        framePosition = 0
        paused = false
        stopped = false

        val format = AudioFormat(
            decoded.sampleRate.toFloat(),
            16,
            decoded.channels,
            true,
            false
        )
        val targetLine = runCatching {
            AudioSystem.getSourceDataLine(format).apply {
                open(format, 8192 * decoded.frameBytes)
                start()
            }
        }.getOrElse {
            runCatching { java.awt.Desktop.getDesktop().open(File(source)) }
            return
        }
        line = targetLine

        _state.value = VoicePlaybackState(
            sourceId = sourceId,
            positionMs = 0,
            durationMs = decoded.durationMs,
            playing = true
        )

        playbackThread = Thread {
            val pcm = decoded.pcm
            val frameBytes = decoded.frameBytes
            var written = 0L
            while (!stopped && written < pcm.size.toLong()) {
                if (paused) {
                    Thread.sleep(50)
                    continue
                }
                val chunk = minOf(8192 * frameBytes, (pcm.size - written).toInt())
                if (chunk <= 0) break
                val played = targetLine.write(pcm, written.toInt(), chunk)
                if (played <= 0) break
                written += played
                framePosition = written / frameBytes
                _state.value = VoicePlaybackState(
                    sourceId = currentSourceId ?: return@Thread,
                    positionMs = framePosition * 1000 / decoded.sampleRate,
                    durationMs = decoded.durationMs,
                    playing = true
                )
            }
            if (!stopped) {
                _state.value = null
                releaseLine()
                currentSourceId = null
            }
        }.apply { isDaemon = true; start() }
    }

    actual fun seekTo(fraction: Float) {
        val decoded = audio ?: return
        val targetFrame = (fraction.coerceIn(0f, 1f) * decoded.pcm.size / decoded.frameBytes).toLong()
        // Line buffers may hold stale frames: restart the write loop at the new position.
        val wasPaused = paused
        stopLineKeepState()
        framePosition = targetFrame
        paused = wasPaused
        startWriteThread()
    }

    actual fun stop() {
        stopped = true
        releaseLine()
        playbackThread = null
        currentSourceId = null
        audio = null
        _state.value = null
    }

    private fun stopLineKeepState() {
        stopped = true
        playbackThread?.join(1000)
        playbackThread = null
        runCatching { line?.close() }
        line = null
        stopped = false
    }

    private fun startWriteThread() {
        val decoded = audio ?: return
        val format = AudioFormat(decoded.sampleRate.toFloat(), 16, decoded.channels, true, false)
        val targetLine = runCatching {
            AudioSystem.getSourceDataLine(format).apply {
                open(format, 8192 * decoded.frameBytes)
                start()
            }
        }.getOrElse { return }
        line = targetLine
        playbackThread = Thread {
            val pcm = decoded.pcm
            val frameBytes = decoded.frameBytes
            var written = framePosition * frameBytes
            while (!stopped && written < pcm.size.toLong()) {
                if (paused) {
                    Thread.sleep(50)
                    continue
                }
                val chunk = minOf(8192 * frameBytes, (pcm.size - written).toInt())
                if (chunk <= 0) break
                val played = targetLine.write(pcm, written.toInt(), chunk)
                if (played <= 0) break
                written += played
                framePosition = written / frameBytes
                _state.value = VoicePlaybackState(
                    sourceId = currentSourceId ?: return@Thread,
                    positionMs = framePosition * 1000 / decoded.sampleRate,
                    durationMs = decoded.durationMs,
                    playing = true
                )
            }
            if (!stopped) {
                _state.value = null
                releaseLine()
                currentSourceId = null
            }
        }.apply { isDaemon = true; start() }
    }

    private fun releaseLine() {
        runCatching { line?.close() }
        line = null
    }
}
