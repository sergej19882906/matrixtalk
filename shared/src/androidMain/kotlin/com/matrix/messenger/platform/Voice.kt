package com.matrix.messenger.platform

import android.media.MediaRecorder
import android.os.Build

actual fun createVoiceRecorder(): VoiceRecorder = AndroidVoiceRecorder()

private class AndroidVoiceRecorder : VoiceRecorder {

    private var recorder: MediaRecorder? = null
    private var outputPath: String? = null

    override var isRecording: Boolean = false
        private set

    override fun start(): Boolean {
        if (isRecording) return false
        val context = appContext ?: return false
        val path = "${context.cacheDir.absolutePath}/voice_${System.currentTimeMillis()}.m4a"
        return runCatching {
            val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioEncodingBitRate(64_000)
            r.setAudioSamplingRate(44_100)
            r.setOutputFile(path)
            r.prepare()
            r.start()
            recorder = r
            outputPath = path
            isRecording = true
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
