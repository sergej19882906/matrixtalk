package com.matrix.messenger.platform

import android.media.MediaRecorder
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import android.os.Build

actual fun createVoiceRecorder(): VoiceRecorder = AndroidVoiceRecorder()

private class AndroidVoiceRecorder : VoiceRecorder {

    private var recorder: MediaRecorder? = null
    private var outputPath: String? = null
    private val amplitudes = mutableListOf<Int>()
    private var amplitudeJob: kotlinx.coroutines.Job? = null

    override val waveform: List<Int>
        get() = amplitudes.toList()

    override var isRecording: Boolean = false
        private set

    override fun start(): Boolean {
        if (isRecording) return false
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
            recorder = r
            outputPath = path
            isRecording = true
            amplitudes.clear()
            amplitudeJob = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch {
                while (isRecording) {
                    recorder?.maxAmplitude?.takeIf { it > 0 }?.let { peak ->
                        // normalize to MSC3245 scale 0..1024
                        amplitudes.add((peak * 1024 / 32767).coerceIn(8, 1024))
                    }
                    kotlinx.coroutines.delay(100)
                }
            }
            true
        }.getOrElse {
            runCatching { recorder?.release() }
            recorder = null
            outputPath = null
            false
        }
    }

    override fun stop(): String? {
        val r = recorder ?: return null
        val path = outputPath
        isRecording = false
        amplitudeJob?.cancel()
        amplitudeJob = null
        recorder = null
        outputPath = null
        return runCatching {
            r.stop()
            r.release()
            path
        }.getOrElse {
            runCatching { r.release() }
            null
        }
    }

    override fun cancel() {
        val r = recorder ?: return
        isRecording = false
        amplitudeJob?.cancel()
        amplitudeJob = null
        amplitudes.clear()
        recorder = null
        outputPath = null
        runCatching {
            r.stop()
            r.release()
        }
    }
}

actual fun playAudio(source: String) {
    runCatching {
        android.media.MediaPlayer().apply {
            setDataSource(source)
            setOnPreparedListener { it.start() }
            setOnCompletionListener { it.release() }
            setOnErrorListener { mp, _, _ -> mp.release(); true }
            prepareAsync()
        }
    }
}
