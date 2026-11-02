package com.matrix.messenger.platform

import android.content.Context
import org.webrtc.Camera2Enumerator

private fun appContext(): Context =
    com.matrix.messenger.platform.appContext ?: error("Application context not initialized")

actual suspend fun listAudioInputDevices(): List<MediaDeviceInfo> =
    emptyList() // mic routing on Android is handled by the system

actual suspend fun listVideoInputDevices(): List<MediaDeviceInfo> {
    val enumerator = Camera2Enumerator(appContext())
    return enumerator.deviceNames.map { name ->
        val facing = when {
            enumerator.isFrontFacing(name) -> " (фронтальная)"
            enumerator.isBackFacing(name) -> " (основная)"
            else -> ""
        }
        MediaDeviceInfo(id = name, name = name + facing)
    }
}
