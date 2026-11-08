package com.matrix.messenger.platform

import io.github.jaredmdobson.concentus.OpusApplication
import io.github.jaredmdobson.concentus.OpusEncoder
import org.gagravarr.opus.OpusAudioData
import org.gagravarr.opus.OpusFile
import org.gagravarr.opus.OpusInfo
import org.gagravarr.opus.OpusTags
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.sound.sampled.AudioFileFormat
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AudioDecodeTest {
    @Test
    fun legacyRawPcmFromPre143RecordingsDecodesAt44100Mono() = withFile { file ->
        // Real-world bytes from a pre-1.43 recording: quiet noise, no container header.
        val raw = byteArrayOf(
            0, 0, 0, 0, -1, -1, 0, 0, 0, 0, 0, 0, 0, 0, -1, -1,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 1, 0, 2, 0, -2, -1,
        )
        file.writeBytes(raw)
        val audio = assertNotNull(AudioDecode.decode(file.absolutePath))
        assertEquals(44100, audio.sampleRate)
        assertEquals(1, audio.channels)
        assertEquals(2, audio.frameBytes)
        assertContentEquals(raw, audio.pcm)
    }

    @Test
    fun extensionlessWaveHasValidRiffHeaderAndPcm16() = withFile { file ->
        val pcm = byteArrayOf(0, 0, -1, 127, 0, -128, -1, -1)
        WaveFileWriter(file, 48000, 1).use { it.write(pcm, pcm.size) }
        val raw = file.readBytes()
        assertEquals("RIFF", String(raw, 0, 4, Charsets.US_ASCII))
        assertEquals(raw.size - 8, ByteBuffer.wrap(raw, 4, 4).order(ByteOrder.LITTLE_ENDIAN).int)
        assertEquals("WAVE", String(raw, 8, 4, Charsets.US_ASCII))
        assertEquals(pcm.size, ByteBuffer.wrap(raw, 40, 4).order(ByteOrder.LITTLE_ENDIAN).int)
        val audio = assertNotNull(AudioDecode.decode(file.absolutePath))
        assertContentEquals(pcm, audio.pcm)
        assertEquals(2, audio.frameBytes)
        assertEquals(48000, audio.sampleRate)
        assertContentEquals(pcm, assertNotNull(AudioDecode.decode(file.toURI().toString())).pcm)
    }

    @Test
    fun unsignedEightBitWaveIsNormalizedToSignedPcm16LittleEndian() = withFile { file ->
        val bytes = byteArrayOf(0, -128, -1)
        val format = AudioFormat(8000f, 8, 1, false, false)
        AudioInputStream(ByteArrayInputStream(bytes), format, bytes.size.toLong()).use {
            AudioSystem.write(it, AudioFileFormat.Type.WAVE, file)
        }
        val audio = assertNotNull(AudioDecode.decode(file.absolutePath))
        assertContentEquals(byteArrayOf(0, -128, 0, 0, -1, 127), audio.pcm)
        assertEquals(2, audio.frameBytes)
        assertEquals(8000, audio.sampleRate)
    }

    @Test
    fun extensionlessOpusTrimsPreskipAndFinalGranuleInFramesForMonoAndStereo() {
        for (channels in 1..2) withFile { file ->
            writeOpus(file, channels, preSkip = 312, finalGranule = 1680)
            val audio = assertNotNull(AudioDecode.decode(file.absolutePath))
            assertEquals(channels, audio.channels)
            assertEquals(channels * 2, audio.frameBytes)
            assertEquals((1680 - 312) * channels * 2, audio.pcm.size)
            assertEquals(28L, audio.durationMs)
        }
    }

    @Test
    fun preskipCoveringOutputProducesEmptyPcm() = withFile { file ->
        writeOpus(file, 1, preSkip = 2000, finalGranule = 1920)
        assertTrue(assertNotNull(AudioDecode.decode(file.absolutePath)).pcm.isEmpty())
    }

    @Test
    fun m4aAndMalformedOggReturnExplicitFallback() = withFile { file ->
        file.writeBytes(byteArrayOf(0, 0, 0, 24) + "ftypM4A ".toByteArray())
        assertNull(AudioDecode.decode(file.absolutePath))
        file.writeBytes("OggSinvalid".toByteArray())
        assertNull(AudioDecode.decode(file.absolutePath))
        // Decoders must close streams even when header construction fails (Windows deletion).
        assertTrue(file.delete())
    }

    private fun writeOpus(file: File, channels: Int, preSkip: Int, finalGranule: Long) {
        val encoder = OpusEncoder(48000, channels, OpusApplication.OPUS_APPLICATION_AUDIO)
        val info = OpusInfo().apply {
            numChannels = channels
            setSampleRate(48000L)
            this.preSkip = preSkip
        }
        file.outputStream().use { output ->
            OpusFile(output, info, OpusTags()).use { writer ->
                repeat(2) { index ->
                    val pcm = ShortArray(960 * channels) { ((it * 71 + index * 17) % 2000).toShort() }
                    val packet = ByteArray(4000)
                    val size = encoder.encode(pcm, 0, 960, packet, 0, packet.size)
                    writer.writeAudioData(OpusAudioData(packet.copyOf(size)).apply {
                        granulePosition = if (index == 1) finalGranule else 960L
                    })
                }
            }
        }
    }

    private fun withFile(block: (File) -> Unit) {
        val file = File.createTempFile("audio_fixture_", "")
        try {
            block(file)
        } finally {
            file.delete()
        }
    }
}
