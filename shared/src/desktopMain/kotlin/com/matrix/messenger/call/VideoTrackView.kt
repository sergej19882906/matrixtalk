package com.matrix.messenger.call

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
actual fun VideoTrackView(
    track: Any?,
    eglContext: Any?,
    modifier: Modifier,
    mirror: Boolean
) {
    Box(modifier.background(Color.Black))
}
