package com.matrix.messenger.platform

import android.media.MediaPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

actual object VoicePlayback {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _state = MutableStateFlow<VoicePlaybackState?>(null)
    actual val state: StateFlow<VoicePlaybackState?> = _state.asStateFlow()

    private var player: MediaPlayer? = null
    private var currentSourceId: String? = null
    private var currentSource: String? = null

    private fun pollLoop() {
        scope.launch {
            while (player != null) {
                val p = player
                if (p != null && p.isPlaying) {
                    _state.value = VoicePlaybackState(
                        sourceId = currentSourceId ?: return@launch,
                        positionMs = p.currentPosition.toLong(),
                        durationMs = p.duration.toLong(),
                        playing = true
                    )
                }
                delay(200)
            }
        }
    }

    actual fun toggle(sourceId: String, source: String) {
        val current = _state.value
        if (current?.sourceId == sourceId) {
            val p = player ?: return
            if (p.isPlaying) {
                p.pause()
                _state.value = current.copy(playing = false)
            } else {
                p.start()
                _state.value = current.copy(playing = true)
            }
            return
        }
        stop()
        runCatching {
            val p = MediaPlayer()
            p.setDataSource(source)
            p.setOnPreparedListener {
                it.start()
                currentSourceId = sourceId
                currentSource = source
                player = it
                _state.value = VoicePlaybackState(
                    sourceId = sourceId,
                    positionMs = 0,
                    durationMs = it.duration.toLong(),
                    playing = true
                )
                pollLoop()
            }
            p.setOnCompletionListener {
                _state.value = null
                releasePlayer()
            }
            p.setOnErrorListener { mp, _, _ ->
                releasePlayer()
                _state.value = null
                true
            }
            p.prepareAsync()
        }
    }

    actual fun seekTo(fraction: Float) {
        val p = player ?: return
        val duration = p.duration
        if (duration > 0) {
            p.seekTo((fraction.coerceIn(0f, 1f) * duration).toInt())
        }
    }

    actual fun stop() {
        releasePlayer()
        _state.value = null
    }

    private fun releasePlayer() {
        runCatching { player?.release() }
        player = null
        currentSourceId = null
        currentSource = null
    }
}
