package com.matrix.messenger.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState

@Composable
actual fun rememberCallPermissionRequester(
    onResult: (granted: Boolean) -> Unit
): (isVideo: Boolean) -> Unit {
    val currentOnResult by rememberUpdatedState(onResult)
    return { currentOnResult(true) }
}
