package com.matrix.messenger.platform

import androidx.compose.runtime.Composable

/**
 * Returns a launcher `(isVideo) -> Unit` that ensures microphone (and camera for video
 * calls) permission, then invokes [onResult]. On Android it triggers the runtime
 * permission dialog when needed; on Desktop it invokes `onResult(true)` immediately.
 */
@Composable
expect fun rememberCallPermissionRequester(
    onResult: (granted: Boolean) -> Unit
): (isVideo: Boolean) -> Unit
