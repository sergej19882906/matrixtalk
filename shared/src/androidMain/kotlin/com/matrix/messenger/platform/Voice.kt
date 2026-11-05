package com.matrix.messenger.platform

import android.media.MediaRecorder
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

actual fun createVoiceRecorder(): VoiceRecorder = AndroidVoiceRecorder()

private class AndroidVoiceRecorder : VoiceRecorder {

    private var recorder: MediaRecorder? = null
    private var outputPath: String? = null
    private val amplitudes = mutableListOf<Int>()
    private var amplitudeJob: Job? = null

    override val waveform: List<Int>
        get() = synchronized(this) { amplitudes.toList() }

    @Volatile
    override var isRecording: Boolean = false
        private set

    @Synchronized
    override fun start(): Boolean {
        if (recorder != null) return false
        val context = appContext ?: return false
        val useOpus = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        val extension = if (useOpus) "ogg" else "m4a"
        val path = "${context.cacheDir.absolutePath}/voice_${System.currentTimeMillis()}.$extension"
        return runCatching {
            val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            // Retain before configuration/prepare so every failure can release it.
            recorder = r
            outputPath = path
            r.setOnErrorListener { failed, _, _ ->
                synchronized(this@AndroidVoiceRecorder) {
                    if (recorder === failed) cancel()
                }
            }
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            if (useOpus) {
                r.setOutputFormat(MediaRecorder.OutputFormat.OGG)
                r.setAudioEncoder(MediaRecorder.AudioEncoder.OPUS)
            } else {
                r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            }
            r.setAudioEncodingBitRate(64_000)
            r.setAudioSamplingRate(48_000)
            r.setOutputFile(path)
            r.prepare()
            r.start()
            isRecording = true
            amplitudes.clear()
            amplitudeJob = CoroutineScope(Dispatchers.Default).launch {
                while (true) {
                    val active = synchronized(this@AndroidVoiceRecorder) {
                        if (recorder !== r || !isRecording) {
                            false
                        } else {
                            runCatching { r.maxAmplitude }.getOrNull()?.takeIf { it > 0 }?.let { peak ->
                                // Normalize to MSC3245 scale 0..1024.
                                amplitudes.add((peak * 1024 / 32767).coerceIn(8, 1024))
                            }
                            true
                        }
                    }
                    if (!active) break
                    delay(100)
                }
            }
            true
        }.getOrElse {
            isRecording = false
            amplitudeJob?.cancel()
            amplitudeJob = null
            runCatching { recorder?.release() }
            recorder = null
            outputPath = null
            File(path).delete()
            false
        }
    }

    @Synchronized
    override fun stop(): String? {
        isRecording = false
        amplitudeJob?.cancel()
        amplitudeJob = null
        val r = recorder
        val path = outputPath
        recorder = null
        outputPath = null
        var stopped = false
        try {
            if (r != null) {
                r.stop()
                stopped = true
            }
        } catch (_: Exception) {
            // Short or interrupted recordings may not contain a valid container.
        } finally {
            runCatching { r?.release() }
        }
        val file = path?.let(::File)
        return if (stopped && file != null && file.length() > 0) {
            file.absolutePath
        } else {
            file?.delete()
            null
        }
    }

    @Synchronized
    override fun cancel() {
        val path = outputPath
        stop()
        path?.let { File(it).delete() }
        amplitudes.clear()
    }
}

actual fun playAudio(source: String) {
    var player: android.media.MediaPlayer? = null
    runCatching {
        val p = android.media.MediaPlayer()
        player = p
        p.setDataSource(source)
        p.setOnPreparedListener { prepared ->
            runCatching { prepared.start() }.onFailure { runCatching { prepared.release() } }
        }
        p.setOnCompletionListener { runCatching { it.release() } }
        p.setOnErrorListener { mp, _, _ -> runCatching { mp.release() }; true }
        p.prepareAsync()
    }.onFailure {
        runCatching { player?.release() }
    }
}
