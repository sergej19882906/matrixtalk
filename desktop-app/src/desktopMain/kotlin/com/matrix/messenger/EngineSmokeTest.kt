package com.matrix.messenger

import com.matrix.messenger.call.createWebRtcEngine
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking

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
        }
        println("=== engine smoke test OK ===")
    } catch (t: Throwable) {
        println("=== engine smoke test FAILED ===")
        t.printStackTrace()
        kotlin.system.exitProcess(1)
    }
    kotlin.system.exitProcess(0)
}
