package com.matrix.messenger.platform

import androidx.compose.runtime.Composable

/**
 * Desktop has no camera pipeline: video "messages" are attached as files.
 */
@Composable
actual fun rememberVideoRecorder(onDone: (path: String?) -> Unit): () -> Unit {
    val pick = rememberFilePicker(listOf("video/*")) { file ->
        onDone(file.path)
    }
    return { pick() }
}
