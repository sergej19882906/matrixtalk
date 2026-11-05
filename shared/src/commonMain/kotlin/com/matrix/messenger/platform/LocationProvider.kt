package com.matrix.messenger.platform

import androidx.compose.runtime.Composable
import com.matrix.messenger.data.model.GeoLocation

/**
 * Returns a launcher that resolves the device's current location and invokes
 * [onResult] with it (null when unavailable — no GPS, permission denied, or
 * desktop where coordinates must be entered manually).
 *
 * On Android the runtime location permission dialog is shown when needed.
 */
@Composable
expect fun rememberLocationRequester(
    onResult: (location: GeoLocation?) -> Unit
): () -> Unit

/**
 * One-shot current location for non-composable callers (live location beacon
 * broadcaster). Never shows a permission dialog — the UI flow must request the
 * permission first. Returns null when unavailable.
 */
expect suspend fun getCurrentLocation(): GeoLocation?

/** Whether the platform has any location provider at all (desktop has none). */
expect fun hasLocationProvider(): Boolean
