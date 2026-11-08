package com.matrix.messenger.platform

import android.media.MediaPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

actual object VoicePlayback {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow<VoicePlaybackState?>(null)
    actual val state: StateFlow<VoicePlaybackState?> = _state.asStateFlow()

    private var player: MediaPlayer? = null
    private var currentSourceId: String? = null
    private var generation = 0L
    private var prepared = false
    private var playWhenPrepared = true
    private var pollJob: Job? = null

    private fun isCurrent(p: MediaPlayer, token: Long): Boolean = player === p && generation == token

    private fun releasePlayer(p: MediaPlayer, token: Long) {
        if (isCurrent(p, token)) {
            generation++
            pollJob?.cancel()
            pollJob = null
            player = null
            currentSourceId = null
            prepared = false
            _state.value = null
        }
        // Stale callbacks may only release their own instance, never the replacement.
        runCatching { p.release() }
    }

    private fun stopNow() {
        generation++
        pollJob?.cancel()
        pollJob = null
        val old = player
        player = null
        currentSourceId = null
        prepared = false
        _state.value = null
        runCatching { old?.release() }
    }

    private fun publish(p: MediaPlayer, token: Long, sourceId: String) {
        if (!isCurrent(p, token) || !prepared) return
        _state.value = VoicePlaybackState(
            sourceId = sourceId,
            positionMs = p.currentPosition.toLong(),
            durationMs = p.duration.toLong(),
            playing = p.isPlaying
        )
    }

    private fun pollLoop(p: MediaPlayer, token: Long, sourceId: String) {
        pollJob?.cancel()
        pollJob = scope.launch {
            while (isCurrent(p, token) && prepared) {
                runCatching { publish(p, token, sourceId) }.onFailure { releasePlayer(p, token) }
                delay(200)
            }
        }
    }

    actual fun toggle(sourceId: String, source: String) {
        scope.launch {
            val existing = player
            if (existing != null && currentSourceId == sourceId) {
                val token = generation
                if (!prepared) {
                    playWhenPrepared = !playWhenPrepared
                    return@launch
                }
                runCatching {
                    if (existing.isPlaying) existing.pause() else existing.start()
                    publish(existing, token, sourceId)
                }.onFailure {
                    logError("VoicePlayback", "pause/start failed for $sourceId", it)
                    releasePlayer(existing, token)
                }
                return@launch
            }
            stopNow()
            val token = generation
            var created: MediaPlayer? = null
            runCatching {
                val p = MediaPlayer()
                created = p
                // Retain before setDataSource/prepareAsync, including the preparing phase.
                player = p
                currentSourceId = sourceId
                playWhenPrepared = true
                p.setDataSource(source)
                p.setOnPreparedListener { callback ->
                    scope.launch {
                        if (!isCurrent(callback, token)) {
                            runCatching { callback.release() }
                            return@launch
                        }
                        runCatching {
                            prepared = true
                            if (playWhenPrepared) callback.start()
                            publish(callback, token, sourceId)
                            pollLoop(callback, token, sourceId)
                        }.onFailure {
                            logError("VoicePlayback", "post-prepare failed for $sourceId", it)
                            releasePlayer(callback, token)
                        }
                    }
                }
                p.setOnCompletionListener { callback ->
                    scope.launch { releasePlayer(callback, token) }
                }
                p.setOnErrorListener { callback, what, extra ->
                    logError(
                        "VoicePlayback",
                        "MediaPlayer error what=$what extra=$extra source=$sourceId " +
                            "(file=${java.io.File(source).let { "exists=${it.isFile} size=${it.length()}" }})",
                        null
                    )
                    scope.launch { releasePlayer(callback, token) }
                    true
                }
                p.prepareAsync()
            }.onFailure {
                logError("VoicePlayback", "prepare failed for $sourceId", it)
                created?.let { releasePlayer(it, token) }
            }
        }
    }

    actual fun seekTo(fraction: Float) {
        if (!fraction.isFinite()) return
        scope.launch {
            val p = player ?: return@launch
            val token = generation
            if (!prepared) return@launch
            runCatching {
                val duration = p.duration
                if (duration > 0) p.seekTo((fraction.coerceIn(0f, 1f) * duration).toInt())
            }.onFailure { releasePlayer(p, token) }
        }
    }

    actual fun stop() {
        scope.launch { stopNow() }
    }
}
