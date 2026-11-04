package com.matrix.messenger.platform

import androidx.compose.runtime.Composable

/**
 * Launches video capture for a "video message". On Android the system camera
 * records an mp4; on desktop a video file picker opens instead. [onDone]
 * receives the recorded file path (null when cancelled).
 */
@Composable
expect fun rememberVideoRecorder(onDone: (path: String?) -> Unit): () -> Unit
