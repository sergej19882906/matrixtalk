package com.matrix.messenger.platform

import androidx.compose.runtime.Composable

/**
 * Launches video capture. Android requests CAMERA before launching the system
 * camera; desktop opens a video file picker. [onDone] receives a non-empty file
 * path for preview/send, or null on cancellation. Permission denial, launch
 * failure and a successful capture with missing/empty output call [onError]
 * instead; no successful [onDone] is emitted on those paths. The caller owns
 * preview, sending and deletion of successfully returned files.
 */
@Composable
expect fun rememberVideoRecorder(
    onError: (String) -> Unit = {},
    onDone: (path: String?) -> Unit
): () -> Unit
