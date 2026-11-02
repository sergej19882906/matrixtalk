package com.matrix.messenger.platform

import dev.onvoid.webrtc.media.MediaDevices

actual suspend fun listAudioInputDevices(): List<MediaDeviceInfo> =
    MediaDevices.getAudioCaptureDevices().map {
        MediaDeviceInfo(id = it.descriptor, name = it.name)
    }

actual suspend fun listVideoInputDevices(): List<MediaDeviceInfo> =
    MediaDevices.getVideoCaptureDevices().map {
        MediaDeviceInfo(id = it.descriptor, name = it.name)
    }
