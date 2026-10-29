package com.matrix.messenger

import com.matrix.messenger.call.createWebRtcEngine
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/** TEMPORARY smoke test: initialize the desktop WebRTC engine and start a call locally. */
fun main(): Unit = runBlocking {
    val engine = createWebRtcEngine()
    try {
        coroutineScope {
            val collector = async {
                engine.events.collect { event ->
                    println("[event] $event")
                }
            }
            delay(300)
            engine.setIceServers(listOf(Triple("stun:stun.l.google.com:19302", null, null)))
            engine.startCall("smoke_call", isVideo = false)
            // ICE gathering + offer emission should complete well within 10s.
            delay(10_000)
            collector.cancel()

            // Second pass: video call — checks camera enumeration/capture if a device exists.
            println("[video] local track after audio call: ${withTimeoutOrNull(500) { engine.localVideoTrack.first() }}")
            engine.hangup("smoke_call")
            val collector2 = async {
                engine.events.collect { event -> println("[event-video] $event") }
            }
            delay(300)
            engine.startCall("smoke_call_video", isVideo = true)
            delay(10_000)
            println("[video] local track after video call: ${withTimeoutOrNull(500) { engine.localVideoTrack.first() }}")
            collector2.cancel()
        }
        println("=== engine smoke test OK ===")
    } catch (t: Throwable) {
        println("=== engine smoke test FAILED ===")
        t.printStackTrace()
        kotlin.system.exitProcess(1)
    }
    kotlin.system.exitProcess(0)
}
