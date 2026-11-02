package com.matrix.messenger.platform

/** A single media device (microphone / camera) exposed for user selection. */
data class MediaDeviceInfo(
    /** Stable identifier to persist and look the device up by. */
    val id: String,
    /** Human-readable display name. */
    val name: String,
)

/** Lists available audio input (microphone) devices. Empty when not selectable on this platform. */
expect suspend fun listAudioInputDevices(): List<MediaDeviceInfo>

/** Lists available video input (camera) devices. Empty when not selectable on this platform. */
expect suspend fun listVideoInputDevices(): List<MediaDeviceInfo>
