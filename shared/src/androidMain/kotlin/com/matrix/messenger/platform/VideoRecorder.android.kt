package com.matrix.messenger.platform

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import java.io.File

@Composable
actual fun rememberVideoRecorder(onDone: (path: String?) -> Unit): () -> Unit {
    val context = androidx.compose.ui.platform.LocalContext.current
    val currentOnDone by rememberUpdatedState(onDone)
    var pendingFile by remember { mutableStateOf<File?>(null) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.CaptureVideo()
    ) { recorded: Boolean ->
        val file = pendingFile
        pendingFile = null
        if (recorded && file != null && file.length() > 0) {
            currentOnDone(file.absolutePath)
        } else {
            file?.delete()
            currentOnDone(null)
        }
    }

    return {
        val dir = File(context.cacheDir, "video").apply { mkdirs() }
        val file = File(dir, "video_${System.currentTimeMillis()}.mp4")
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        pendingFile = file
        launcher.launch(uri)
    }
}
