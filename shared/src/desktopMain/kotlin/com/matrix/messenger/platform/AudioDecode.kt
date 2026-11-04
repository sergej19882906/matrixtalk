package com.matrix.messenger.platform

import java.io.File
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem

/** Fully-decoded PCM audio ready for in-app playback. */
data class DecodedAudio(
    val pcm: ByteArray,
    val sampleRate: Int,
    val channels: Int,
    /** Bytes per frame (channels * 2 for 16-bit). */
    val frameBytes: Int
) {
    val durationMs: Long
        get() = if (sampleRate <= 0 || frameBytes <= 0) 0 else (pcm.size.toLong() * 1000 / (sampleRate.toLong() * frameBytes))
}

/**
 * Decodes voice audio to 16-bit PCM for the controllable desktop player.
 * Supports WAV (PCM, incl. our desktop recordings) and Ogg/Opus (Element
 * voice messages, Android Opus recordings) via vorbis-java demux + Concentus
 * decoder. Anything else returns null → caller falls back to the system player.
 */
object AudioDecode {

    fun decode(path: String): DecodedAudio? {
        val file = File(path)
        if (!file.exists()) return null
        return when {
            path.endsWith(".wav", ignoreCase = true) -> decodeWav(file)
            path.endsWith(".ogg", ignoreCase = true) || path.endsWith(".opus", ignoreCase = true) ->
                decodeOggOpus(file)
            else -> null
        }
    }

    private fun decodeWav(file: File): DecodedAudio? = runCatching {
        AudioSystem.getAudioInputStream(file).use { stream ->
            val format = stream.format
            if (format.encoding != AudioFormat.Encoding.PCM_SIGNED &&
                format.encoding != AudioFormat.Encoding.PCM_UNSIGNED
            ) {
                return@use null
            }
            DecodedAudio(
                pcm = stream.readAllBytes(),
                sampleRate = format.frameRate.toInt(),
                channels = format.channels,
                frameBytes = format.frameSize.coerceAtLeast(1)
            )
        }
    }.getOrNull()

    private fun decodeOggOpus(file: File): DecodedAudio? = runCatching {
        val opusFile = org.gagravarr.opus.OpusFile(file)
        val info = opusFile.info
        val sampleRate = 48000
        val channels = info.numChannels.coerceIn(1, 2)
        val preSkip = info.preSkip.toLong() * channels * 2

        val decoder = io.github.jaredmdobson.concentus.OpusDecoder(sampleRate, channels)
        val maxFramePcm = 5760 * channels
        val out = java.io.ByteArrayOutputStream()
        var packet = opusFile.nextAudioPacket
        while (packet != null) {
            val data = packet.data
            if (data.isNotEmpty()) {
                val pcm = ShortArray(maxFramePcm)
                val decoded = decoder.decode(data, 0, data.size, pcm, 0, maxFramePcm, false)
                if (decoded > 0) {
                    val byteCount = decoded * channels * 2
                    val byteBuf = ByteArray(byteCount)
                    var bi = 0
                    for (i in 0 until decoded * channels) {
                        val sample = pcm[i].toInt()
                        byteBuf[bi++] = (sample and 0xFF).toByte()
                        byteBuf[bi++] = ((sample shr 8) and 0xFF).toByte()
                    }
                    out.write(byteBuf)
                }
            }
            packet = opusFile.nextAudioPacket
        }
        var pcmBytes = out.toByteArray()
        // Drop Opus pre-skip samples from the very start.
        if (pcmBytes.size > preSkip) {
            pcmBytes = pcmBytes.copyOfRange(preSkip.toInt(), pcmBytes.size)
        }
        DecodedAudio(
            pcm = pcmBytes,
            sampleRate = sampleRate,
            channels = channels,
            frameBytes = channels * 2
        )
    }.getOrNull()
}
