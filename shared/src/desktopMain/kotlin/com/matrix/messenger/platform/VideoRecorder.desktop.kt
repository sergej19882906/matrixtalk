package com.matrix.messenger.platform

import androidx.compose.runtime.Composable

/** Desktop has no camera pipeline: video messages are attached as files. */
@Composable
actual fun rememberVideoRecorder(
    onError: (String) -> Unit,
    onDone: (path: String?) -> Unit
): () -> Unit {
    var picked = false
    val pick = rememberFilePicker(listOf("video/*")) { file ->
        picked = true
        if (java.io.File(file.path).isFile && java.io.File(file.path).length() > 0) {
            onDone(file.path)
        } else {
            onError("Selected video file is missing or empty")
        }
    }
    return {
        picked = false
        runCatching {
            pick()
            if (!picked) onDone(null)
        }.onFailure {
            onError("Unable to open video picker: ${it.message ?: "picker unavailable"}")
        }
    }
}
