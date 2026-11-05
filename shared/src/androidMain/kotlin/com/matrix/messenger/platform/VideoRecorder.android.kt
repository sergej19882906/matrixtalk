package com.matrix.messenger.platform

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File

@Composable
actual fun rememberVideoRecorder(
    onError: (String) -> Unit,
    onDone: (path: String?) -> Unit
): () -> Unit {
    val context = androidx.compose.ui.platform.LocalContext.current
    val currentOnDone by rememberUpdatedState(onDone)
    val currentOnError by rememberUpdatedState(onError)
    var pendingFile by remember { mutableStateOf<File?>(null) }
    var requestingPermission by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.CaptureVideo()
    ) { recorded: Boolean ->
        val file = pendingFile
        pendingFile = null
        when {
            !recorded -> {
                file?.delete()
                currentOnDone(null)
            }
            file != null && file.isFile && file.length() > 0 -> currentOnDone(file.absolutePath)
            else -> {
                file?.delete()
                currentOnError("Camera did not produce a video file")
            }
        }
    }

    val launchCapture = {
        runCatching {
            val dir = File(context.cacheDir, "video")
            check(dir.isDirectory || dir.mkdirs()) { "Cannot create video output directory" }
            val file = File.createTempFile("video_", ".mp4", dir)
            pendingFile = file
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            launcher.launch(uri)
        }.onFailure {
            pendingFile?.delete()
            pendingFile = null
            currentOnError("Unable to open video camera: ${it.message ?: "capture unavailable"}")
        }
        Unit
    }
    val currentLaunchCapture by rememberUpdatedState(launchCapture)
    val requestCamera = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        requestingPermission = false
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            currentLaunchCapture()
        } else {
            currentOnError("Camera permission is required to record video")
        }
    }

    return {
        if (!requestingPermission && pendingFile == null) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                launchCapture()
            } else {
                requestingPermission = true
                runCatching { requestCamera.launch(Manifest.permission.CAMERA) }.onFailure {
                    requestingPermission = false
                    currentOnError("Unable to request camera permission")
                }
            }
        }
    }
}
