package com.matrix.messenger.platform

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine
import kotlin.coroutines.coroutineContext

/** Commands and blocking audio IO run off the UI thread; each writer owns its own line. */
actual object VoicePlayback {
    private val controller = DesktopVoicePlaybackController()
    actual val state: StateFlow<VoicePlaybackState?> = controller.state
    actual fun toggle(sourceId: String, source: String) = controller.toggle(sourceId, source)
    actual fun seekTo(fraction: Float) = controller.seekTo(fraction)
    actual fun stop() = controller.stop()
}

/** Injectable audio boundary for device-free session/command tests. */
internal class DesktopVoicePlaybackController(
    private val decode: (String) -> DecodedAudio? = AudioDecode::decode,
    private val createLine: (AudioFormat) -> SourceDataLine = AudioSystem::getSourceDataLine,
    private val fallback: (String) -> Unit = { source ->
        runCatching {
            if (source.startsWith("http://") || source.startsWith("https://")) {
                java.awt.Desktop.getDesktop().browse(java.net.URI(source))
            } else {
                val file = if (source.startsWith("file:")) File(java.net.URI(source)) else File(source)
                java.awt.Desktop.getDesktop().open(file)
            }
        }
    }
) {
    private sealed interface Command {
        data class Toggle(val sourceId: String, val source: String) : Command
        data class Seek(val fraction: Float) : Command
        data object Stop : Command
        data class Barrier(val done: CompletableDeferred<Unit>) : Command
    }

    private class Session(
        val sourceId: String,
        val audio: DecodedAudio,
        val line: SourceDataLine,
        val startFrame: Long,
        val lineStartFrame: Long,
        @Volatile var paused: Boolean
    ) {
        lateinit var job: Job
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val commands = Channel<Command>(Channel.UNLIMITED)
    private val lock = Any()
    private var session: Session? = null
    private val _state = MutableStateFlow<VoicePlaybackState?>(null)
    val state: StateFlow<VoicePlaybackState?> = _state.asStateFlow()

    init {
        scope.launch {
            for (command in commands) {
                when (command) {
                    is Command.Toggle -> toggleNow(command)
                    is Command.Seek -> seekNow(command.fraction)
                    Command.Stop -> stopNow()
                    is Command.Barrier -> command.done.complete(Unit)
                }
            }
        }
    }

    fun toggle(sourceId: String, source: String) {
        commands.trySend(Command.Toggle(sourceId, source))
    }

    fun seekTo(fraction: Float) {
        if (fraction.isFinite()) commands.trySend(Command.Seek(fraction.coerceIn(0f, 1f)))
    }

    fun stop() {
        commands.trySend(Command.Stop)
    }

    internal suspend fun awaitCommands() {
        val done = CompletableDeferred<Unit>()
        commands.send(Command.Barrier(done))
        done.await()
    }

    internal suspend fun awaitPlayback() {
        awaitCommands()
        synchronized(lock) { session }?.job?.join()
    }

    internal suspend fun shutdown() {
        stop()
        awaitCommands()
        commands.close()
        scope.cancel()
    }

    private suspend fun toggleNow(command: Command.Toggle) {
        val current = synchronized(lock) { session }
        if (current?.sourceId == command.sourceId) {
            runCatching {
                synchronized(lock) {
                    if (session === current) {
                        current.paused = !current.paused
                        if (current.paused) current.line.stop() else current.line.start()
                        publish(current)
                    }
                }
            }.onFailure { stopNow() }
            return
        }
        // An unsupported source must also replace/stop the previous controllable session.
        stopNow()
        val audio = runCatching { decode(command.source) }
            .onFailure {
                // Why did the external player take over? This line is the answer.
                logError("VoicePlayback", "decode failed for $command.source", it)
            }
            .getOrNull()
        if (audio == null || audio.pcm.isEmpty()) {
            val file = if (command.source.startsWith("file:")) {
                runCatching { File(java.net.URI(command.source)) }.getOrNull()
            } else File(command.source)
            logError(
                "VoicePlayback",
                "built-in playback unavailable: decoded=${audio != null}, " +
                    "pcmBytes=${audio?.pcm?.size ?: 0}, file=${file?.absolutePath}, " +
                    "exists=${file?.isFile}, size=${file?.length()}",
                null
            )
            runCatching { fallback(command.source) }
            return
        }
        if (!startSession(command.sourceId, audio, 0, false)) {
            runCatching { fallback(command.source) }
        }
    }

    private suspend fun seekNow(fraction: Float) {
        val current = synchronized(lock) { session } ?: return
        val target = (fraction.toDouble() * (current.audio.pcm.size / current.audio.frameBytes)).toLong()
        val paused = current.paused
        stopNow()
        startSession(current.sourceId, current.audio, target, paused)
    }

    private suspend fun stopNow() {
        val old = synchronized(lock) {
            val old = session
            session = null
            _state.value = null
            old
        } ?: return
        // Unblock write/drain before joining. Never close a line belonging to a new session.
        old.job.cancel()
        runCatching { old.line.stop() }
        runCatching { old.line.flush() }
        runCatching { old.line.close() }
        old.job.cancelAndJoin()
    }

    private fun startSession(sourceId: String, audio: DecodedAudio, startFrame: Long, paused: Boolean): Boolean {
        require(audio.sampleRate > 0 && audio.channels > 0 && audio.frameBytes == audio.channels * 2)
        val format = AudioFormat(audio.sampleRate.toFloat(), 16, audio.channels, true, false)
        val target: SourceDataLine = try {
            createLine(format)
        } catch (t: Throwable) {
            logError(
                "VoicePlayback",
                "line acquire failed (${audio.sampleRate}Hz ${audio.channels}ch): " +
                    "${t.javaClass.simpleName}: ${t.message}",
                t
            )
            return false
        }
        try {
            target.open(format, 8192 * audio.frameBytes)
        } catch (t: Throwable) {
            // A line that failed to open must be released before the fallback takes over.
            runCatching { target.close() }
            logError(
                "VoicePlayback",
                "line open failed (${audio.sampleRate}Hz ${audio.channels}ch): " +
                    "${t.javaClass.simpleName}: ${t.message}",
                t
            )
            return false
        }
        return runCatching {
            val current = Session(sourceId, audio, target, startFrame, target.longFramePosition, paused)
            current.job = scope.launch(start = kotlinx.coroutines.CoroutineStart.LAZY) {
                try {
                    coroutineScope {
                        val poll = launch {
                            while (true) {
                                synchronized(lock) { if (session === current) publish(current) }
                                delay(50)
                            }
                        }
                        try {
                            var written = (startFrame * audio.frameBytes).toInt()
                            while (written < audio.pcm.size) {
                                coroutineContext.ensureActive()
                                if (current.paused) {
                                    delay(25)
                                    continue
                                }
                                val count = minOf(2048 * audio.frameBytes, audio.pcm.size - written)
                                val accepted = target.write(audio.pcm, written, count)
                                coroutineContext.ensureActive()
                                check(accepted > 0) { "Audio line stopped accepting PCM" }
                                written += accepted
                            }
                            // Completion is based on hardware consumption, not queued bytes.
                            while (current.paused) delay(25)
                            target.drain()
                            coroutineContext.ensureActive()
                        } finally {
                            poll.cancelAndJoin()
                        }
                    }
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // Device loss ends only this session; its finally block clears its state.
                } finally {
                    runCatching { target.stop() }
                    runCatching { target.close() }
                    synchronized(lock) {
                        if (session === current) {
                            session = null
                            _state.value = null
                        }
                    }
                }
            }
            synchronized(lock) {
                session = current
                publish(current)
            }
            if (!paused) target.start()
            current.job.start()
            true
        }.getOrElse {
            val failed = synchronized(lock) {
                val failed = session?.takeIf { it.line === target }
                if (failed != null) {
                    session = null
                    _state.value = null
                }
                failed
            }
            failed?.job?.cancel()
            runCatching { target.stop() }
            runCatching { target.close() }
            false
        }
    }

    /** Caller holds lock, so a retired session can never overwrite a replacement's state. */
    private fun publish(current: Session) {
        val audio = current.audio
        val consumed = (current.line.longFramePosition - current.lineStartFrame).coerceAtLeast(0)
        val frame = (current.startFrame + consumed).coerceAtMost(audio.pcm.size.toLong() / audio.frameBytes)
        _state.value = VoicePlaybackState(
            sourceId = current.sourceId,
            positionMs = frame * 1000 / audio.sampleRate,
            durationMs = audio.durationMs,
            playing = !current.paused
        )
    }
}
