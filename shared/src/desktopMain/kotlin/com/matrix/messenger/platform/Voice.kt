package com.matrix.messenger.platform

import java.io.File
import java.io.RandomAccessFile
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.TargetDataLine

actual fun createVoiceRecorder(): VoiceRecorder = DesktopVoiceRecorder()

/** Finalizes the RIFF sizes only after all PCM writes have finished. */
internal class WaveFileWriter(file: File, private val sampleRate: Int, private val channels: Int) : AutoCloseable {
    private val out = RandomAccessFile(file, "rw")
    private var dataBytes = 0L

    init {
        try {
            out.setLength(0)
            out.writeBytes("RIFF")
            writeInt(0)
            out.writeBytes("WAVEfmt ")
            writeInt(16)
            writeShort(1)
            writeShort(channels)
            writeInt(sampleRate)
            writeInt(sampleRate * channels * 2)
            writeShort(channels * 2)
            writeShort(16)
            out.writeBytes("data")
            writeInt(0)
        } catch (failure: Exception) {
            runCatching { out.close() }
            throw failure
        }
    }

    fun write(bytes: ByteArray, count: Int) {
        require(count % (channels * 2) == 0)
        require(dataBytes + count <= 0xffffffffL - 36)
        out.write(bytes, 0, count)
        dataBytes += count
    }

    override fun close() {
        try {
            out.seek(4)
            writeInt((36 + dataBytes).toInt())
            out.seek(40)
            writeInt(dataBytes.toInt())
        } finally {
            out.close()
        }
    }

    private fun writeInt(value: Int) {
        repeat(4) { out.write(value ushr (it * 8) and 0xff) }
    }

    private fun writeShort(value: Int) {
        repeat(2) { out.write(value ushr (it * 8) and 0xff) }
    }
}

private class DesktopVoiceRecorder : VoiceRecorder {

    private class Recording(val line: TargetDataLine, val file: File) {
        @Volatile var active = true
        @Volatile var failed = false
        lateinit var thread: Thread
    }

    @Volatile private var recording: Recording? = null
    private val amplitudes = mutableListOf<Int>()

    override val waveform: List<Int>
        get() = synchronized(amplitudes) { amplitudes.toList() }

    override val isRecording: Boolean
        get() = recording?.active == true

    @Synchronized
    override fun start(): Boolean {
        if (recording != null) return false
        var targetLine: TargetDataLine? = null
        var writer: WaveFileWriter? = null
        var file: File? = null
        return runCatching {
            val format = AudioFormat(44100f, 16, 1, true, false)
            val target = AudioSystem.getTargetDataLine(format)
            targetLine = target
            target.open(format)
            file = File.createTempFile("voice_", ".wav", File(attachmentsDir()))
            val out = WaveFileWriter(file!!, 44100, 1)
            writer = out
            val session = Recording(target, file!!)
            synchronized(amplitudes) { amplitudes.clear() }
            target.start()
            session.thread = Thread {
                val bytes = ByteArray(4096)
                var sampleCount = 0
                var peak = 0
                try {
                    while (session.active) {
                        val count = target.read(bytes, 0, bytes.size)
                        if (count <= 0) break
                        out.write(bytes, count)
                        var i = 0
                        while (i + 1 < count) {
                            val sample = ((bytes[i + 1].toInt() shl 8) or (bytes[i].toInt() and 0xff)).toShort().toInt()
                            peak = maxOf(peak, kotlin.math.abs(sample))
                            sampleCount++
                            if (sampleCount == 4410) {
                                synchronized(amplitudes) {
                                    amplitudes.add((peak * 1024 / 32767).coerceIn(8, 1024))
                                }
                                sampleCount = 0
                                peak = 0
                            }
                            i += 2
                        }
                    }
                } catch (_: Exception) {
                    if (session.active) session.failed = true
                } finally {
                    runCatching { out.close() }.onFailure { session.failed = true }
                    session.active = false
                    runCatching { target.stop() }
                    runCatching { target.close() }
                }
            }.apply { isDaemon = true }
            recording = session
            session.thread.start()
            true
        }.getOrElse {
            recording = null
            runCatching { targetLine?.stop() }
            runCatching { targetLine?.close() }
            runCatching { writer?.close() }
            file?.delete()
            false
        }
    }

    @Synchronized
    override fun stop(): String? {
        val session = recording ?: return null
        session.active = false
        // Close the capture line first to unblock read, then wait for RIFF finalization.
        runCatching { session.line.stop() }
        runCatching { session.line.close() }
        var interrupted = false
        while (session.thread.isAlive) {
            try {
                session.thread.join()
            } catch (_: InterruptedException) {
                interrupted = true
            }
        }
        if (interrupted) Thread.currentThread().interrupt()
        recording = null
        return if (!session.failed && session.file.length() > 44) {
            session.file.absolutePath
        } else {
            session.file.delete()
            null
        }
    }

    @Synchronized
    override fun cancel() {
        val file = recording?.file
        stop()
        file?.delete()
        synchronized(amplitudes) { amplitudes.clear() }
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
