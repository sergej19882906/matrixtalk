package com.matrix.messenger.platform

import androidx.compose.runtime.Composable
import com.matrix.messenger.data.model.GeoLocation

/**
 * Desktop has no GPS: the caller falls back to manual coordinate entry.
 */
@Composable
actual fun rememberLocationRequester(
    onResult: (location: GeoLocation?) -> Unit
): () -> Unit = { onResult(null) }

// Desktop has no location provider: live location sharing is not broadcast here
// (viewing other participants' beacons still works).
actual suspend fun getCurrentLocation(): com.matrix.messenger.data.model.GeoLocation? = null

actual fun hasLocationProvider(): Boolean = false
