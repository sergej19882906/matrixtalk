package com.matrix.messenger.platform

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.lang.reflect.Proxy
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import javax.sound.sampled.SourceDataLine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VoicePlaybackSessionTest {
    @Test
    fun progressUsesConsumedFramesAndCompletionWaitsForDrain() = runBlocking {
        val fake = FakeLine()
        val controller = controller { fake }
        try {
            controller.toggle("a", "wave")
            controller.awaitCommands()
            assertTrue(fake.draining.await(5, TimeUnit.SECONDS))
            assertEquals(0L, assertNotNull(controller.state.value).positionMs)
            fake.frames.set(24000)
            withTimeout(5000) {
                while (controller.state.value?.positionMs != 500L) delay(10)
            }
            assertNotNull(controller.state.value) // Queued all PCM, but not complete yet.
            fake.finishDrain.countDown()
            controller.awaitPlayback()
            assertNull(controller.state.value)
            assertTrue(fake.closed)
        } finally {
            controller.shutdown()
        }
    }

    @Test
    fun pauseSeekAndResumeReplaceSessionWithoutRevivingOldWriter() = runBlocking {
        val lines = CopyOnWriteArrayList<FakeLine>()
        val controller = controller { FakeLine().also { lines.add(it) } }
        try {
            controller.toggle("a", "wave")
            controller.awaitCommands()
            assertTrue(lines[0].draining.await(5, TimeUnit.SECONDS))
            controller.toggle("a", "wave")
            controller.awaitCommands()
            assertFalse(assertNotNull(controller.state.value).playing)
            assertTrue(lines[0].stops > 0)
            controller.seekTo(0.5f)
            controller.awaitCommands()
            assertTrue(lines[0].closed)
            assertTrue(lines[0].drainExited.await(5, TimeUnit.SECONDS))
            assertEquals(2, lines.size)
            assertFalse(assertNotNull(controller.state.value).playing)
            assertEquals(500L, assertNotNull(controller.state.value).positionMs)
            assertEquals(0L, lines[1].written.get())
            controller.toggle("a", "wave")
            controller.awaitCommands()
            assertTrue(lines[1].draining.await(5, TimeUnit.SECONDS))
            assertEquals(48000L, lines[1].written.get()) // Only the remaining half-second.
            assertTrue(assertNotNull(controller.state.value).playing)
            controller.stop()
            controller.awaitCommands()
            assertNull(controller.state.value)
            assertTrue(lines.all { it.closed })
        } finally {
            controller.shutdown()
        }
    }

    @Test
    fun unsupportedFallbackAndRapidSwitchJoinAndClosePriorLine() = runBlocking {
        val lines = CopyOnWriteArrayList<FakeLine>()
        var fallbackSource: String? = null
        val controller = DesktopVoicePlaybackController(
            decode = { if (it == "m4a") null else audio() },
            createLine = { FakeLine().also { lines.add(it) }.line },
            fallback = {
                assertTrue(lines.all { line -> line.closed })
                assertTrue(lines.all { line -> line.drainExited.count == 0L || line.draining.count != 0L })
                fallbackSource = it
            }
        )
        try {
            controller.toggle("a", "wave")
            controller.awaitCommands()
            assertTrue(lines[0].draining.await(5, TimeUnit.SECONDS))
            controller.toggle("b", "wave")
            controller.toggle("c", "m4a")
            controller.awaitCommands()
            assertEquals("m4a", fallbackSource)
            assertNull(controller.state.value)
            assertEquals(2, lines.size)
            assertTrue(lines.all { it.closed })
        } finally {
            controller.shutdown()
        }
    }

    @Test
    fun failedLineOpenClosesItsOwnLineBeforeFallback() = runBlocking {
        val fake = FakeLine(failOpen = true)
        var usedFallback = false
        val controller = DesktopVoicePlaybackController(
            decode = { audio() },
            createLine = { fake.line },
            fallback = {
                assertTrue(fake.closed)
                usedFallback = true
            }
        )
        try {
            controller.toggle("a", "wave")
            controller.awaitCommands()
            assertTrue(usedFallback)
            assertNull(controller.state.value)
        } finally {
            controller.shutdown()
        }
    }

    private fun audio() = DecodedAudio(ByteArray(96000), 48000, 1, 2)

    private fun controller(create: () -> FakeLine) = DesktopVoicePlaybackController(
        decode = { audio() },
        createLine = { create().line },
        fallback = { error("Unexpected fallback") }
    )

    private class FakeLine(private val failOpen: Boolean = false) {
        val frames = AtomicLong(0)
        val written = AtomicLong(0)
        val draining = CountDownLatch(1)
        val finishDrain = CountDownLatch(1)
        val drainExited = CountDownLatch(1)
        @Volatile var closed = false
        @Volatile var stops = 0
        val line = Proxy.newProxyInstance(
            SourceDataLine::class.java.classLoader,
            arrayOf(SourceDataLine::class.java)
        ) { _, method, args ->
            when (method.name) {
                "open" -> { check(!failOpen) { "Simulated unavailable audio device" }; Unit }
                "start", "flush" -> Unit
                "stop" -> { stops++; Unit }
                "close" -> { closed = true; finishDrain.countDown(); Unit }
                "write" -> {
                    val count = args!![2] as Int
                    written.addAndGet(count.toLong())
                    count
                }
                "getLongFramePosition" -> frames.get()
                "drain" -> {
                    draining.countDown()
                    try {
                        check(finishDrain.await(5, TimeUnit.SECONDS)) { "Test drain was not released" }
                    } finally {
                        drainExited.countDown()
                    }
                    Unit
                }
                else -> error("Unexpected audio line operation: ${method.name}")
            }
        } as SourceDataLine
    }
}
