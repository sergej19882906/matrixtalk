package com.matrix.messenger.platform

import java.io.ByteArrayOutputStream
import java.io.File
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem

/** Fully-decoded signed PCM16 little-endian audio ready for in-app playback. */
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

/** Signature-based container decoding; AAC/M4A explicitly uses the system-player fallback. */
object AudioDecode {

    fun decode(path: String): DecodedAudio? = runCatching {
        val file = if (path.startsWith("file:")) File(java.net.URI(path)) else File(path)
        if (!file.isFile) return@runCatching null
        val header = file.inputStream().use { it.readNBytes(12) }
        when {
            header.matches(0, "RIFF") && header.matches(8, "WAVE") -> decodeWav(file)
            header.matches(0, "RIFX") && header.matches(8, "WAVE") -> decodeWav(file)
            header.matches(0, "OggS") -> decodeOggOpus(file)
            header.matches(4, "ftyp") -> null // ISO-BMFF (M4A/AAC) is not PCM or Ogg/Opus.
            // Recordings made by desktop builds before the 1.43 fix are raw int16
            // PCM without any container header (audit finding 11). The capture
            // parameters of that recorder were always 44100 Hz / mono / 16-bit,
            // so decode them directly instead of pushing to the external player.
            file.length() > 0 && file.length() % 2L == 0L -> decodeLegacyRawPcm(file)
            else -> null
        }
    }.getOrNull()

    private fun decodeLegacyRawPcm(file: File): DecodedAudio? = runCatching {
        val bytes = file.readBytes()
        DecodedAudio(
            pcm = bytes.copyOf(bytes.size - bytes.size % 2),
            sampleRate = 44100,
            channels = 1,
            frameBytes = 2,
        )
    }.getOrNull()

    private fun ByteArray.matches(offset: Int, signature: String): Boolean =
        size >= offset + signature.length && signature.indices.all { this[offset + it] == signature[it].code.toByte() }

    private fun decodeWav(file: File): DecodedAudio? = runCatching {
        AudioSystem.getAudioInputStream(file).use { stream ->
            val source = stream.format
            if (source.sampleRate <= 0 || source.channels <= 0) return@use null
            val target = AudioFormat(source.sampleRate, 16, source.channels, true, false)
            if (!AudioSystem.isConversionSupported(target, source)) return@use null
            AudioSystem.getAudioInputStream(target, stream).use { pcmStream ->
                val bytes = pcmStream.readAllBytes()
                DecodedAudio(
                    pcm = bytes.copyOf(bytes.size - bytes.size % target.frameSize),
                    sampleRate = target.sampleRate.toInt(),
                    channels = target.channels,
                    frameBytes = target.frameSize
                )
            }
        }
    }.getOrNull()

    private fun decodeOggOpus(file: File): DecodedAudio? = runCatching {
        // Own the input even if parsing the Opus headers throws during construction.
        file.inputStream().use { input ->
            org.gagravarr.ogg.OggFile(input).use { ogg ->
                org.gagravarr.opus.OpusFile(ogg).use { opusFile ->
                    val info = opusFile.info
                    val sampleRate = 48000
                    val channels = info.numChannels
                    // Mapping family 0 = mono/stereo without mapping; family 1 with
                    // 1-2 channels is still plain interleaved Opus for the decoder —
                    // some producers use it even for mono.
                    val mappingFamily = info.channelMappingFamily.toInt()
                    val supportedMapping = mappingFamily == 0 || (mappingFamily == 1 && channels in 1..2)
                    if (channels !in 1..2 || !supportedMapping) return@runCatching null
                    val decoder = io.github.jaredmdobson.concentus.OpusDecoder(sampleRate, channels)
                    decoder.gain = info.outputGain
                    val maxFrames = 5760 // Decoder frame_size is per channel, not the interleaved sample count.
                    val pcm = ShortArray(maxFrames * channels)
                    val out = ByteArrayOutputStream()
                    var finalGranule = -1L
                    var packet = opusFile.nextAudioPacket
                    while (packet != null) {
                        val data = packet.data
                        if (data.isNotEmpty()) {
                            val frames = decoder.decode(data, 0, data.size, pcm, 0, maxFrames, false)
                            val bytes = ByteArray(frames * channels * 2)
                            for (i in 0 until frames * channels) {
                                val sample = pcm[i].toInt()
                                bytes[i * 2] = (sample and 0xff).toByte()
                                bytes[i * 2 + 1] = (sample shr 8 and 0xff).toByte()
                            }
                            out.write(bytes)
                        }
                        if (packet.granulePosition >= 0) finalGranule = packet.granulePosition
                        packet = opusFile.nextAudioPacket
                    }
                    val bytes = out.toByteArray()
                    val frameBytes = channels * 2
                    val decodedFrames = bytes.size.toLong() / frameBytes
                    val endFrame = if (finalGranule >= 0) minOf(decodedFrames, finalGranule) else decodedFrames
                    val startFrame = info.preSkip.toLong().coerceIn(0, endFrame)
                    DecodedAudio(
                        pcm = bytes.copyOfRange((startFrame * frameBytes).toInt(), (endFrame * frameBytes).toInt()),
                        sampleRate = sampleRate,
                        channels = channels,
                        frameBytes = frameBytes
                    )
                }
            }
        }
    }.getOrNull()
}
