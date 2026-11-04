package com.matrix.messenger.platform

import java.io.File
import javax.sound.sampled.AudioFileFormat
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.TargetDataLine

actual fun createVoiceRecorder(): VoiceRecorder = DesktopVoiceRecorder()

private class DesktopVoiceRecorder : VoiceRecorder {

    private var line: TargetDataLine? = null
    private var thread: Thread? = null
    @Volatile
    private var outputPath: String? = null
    private val amplitudes = mutableListOf<Int>()

    override val waveform: List<Int>
        get() = amplitudes.toList()

    @Volatile
    override var isRecording: Boolean = false
        private set

    override fun start(): Boolean {
        if (isRecording) return false
        return runCatching {
            val format = AudioFormat(44100f, 16, 1, true, false)
            val info = javax.sound.sampled.DataLine.Info(TargetDataLine::class.java, format)
            val targetLine = AudioSystem.getLine(info) as TargetDataLine
            val path = File(attachmentsDir(), "voice_${System.currentTimeMillis()}.wav").absolutePath
            targetLine.open(format)
            targetLine.start()
            isRecording = true
            outputPath = path
            amplitudes.clear()
            var samplesUntilAmplitude = 0
            thread = Thread {
                val bytes = ByteArray(4096)
                val out = File(path).outputStream().buffered()
                try {
                    while (isRecording) {
                        val count = targetLine.read(bytes, 0, bytes.size)
                        if (count > 0) {
                            out.write(bytes, 0, count)
                            // 16-bit LE mono: peak per ~100ms chunk (4410 samples)
                            samplesUntilAmplitude += count / 2
                            if (samplesUntilAmplitude >= 4410) {
                                samplesUntilAmplitude = 0
                                var peak = 0
                                var i = 0
                                while (i + 1 < count) {
                                    val sample = ((bytes[i + 1].toInt() shl 8) or (bytes[i].toInt() and 0xFF)).toShort().toInt()
                                    val abs = if (sample < 0) -sample else sample
                                    if (abs > peak) peak = abs
                                    i += 2
                                }
                                amplitudes.add((peak * 1024 / 32767).coerceIn(8, 1024))
                            }
                        }
                    }
                } catch (_: Exception) {
                    // recording interrupted
                } finally {
                    out.close()
                }
            }.apply { isDaemon = true; start() }
            line = targetLine
            true
        }.getOrElse {
            isRecording = false
            outputPath = null
            false
        }
    }

    override fun stop(): String? {
        if (!isRecording) return null
        isRecording = false
        runCatching {
            line?.stop()
            line?.close()
        }
        line = null
        thread?.join(1000)
        thread = null
        val path = outputPath
        outputPath = null
        return path?.takeIf { File(it).exists() && File(it).length() > 1000 }
    }

    override fun cancel() {
        val path = stop()
        path?.let { runCatching { File(it).delete() } }
    }
}

actual fun playAudio(source: String) {
    runCatching {
        if (source.startsWith("http://") || source.startsWith("https://")) {
            java.awt.Desktop.getDesktop().browse(java.net.URI(source))
        } else {
            java.awt.Desktop.getDesktop().open(File(source.removePrefix("file://")))
        }
    }
}
