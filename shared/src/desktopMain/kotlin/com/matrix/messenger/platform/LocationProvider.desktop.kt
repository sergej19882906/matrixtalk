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
