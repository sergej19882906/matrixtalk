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
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

@Composable
actual fun rememberCallPermissionRequester(
    onResult: (granted: Boolean) -> Unit
): (isVideo: Boolean) -> Unit {
    val context = LocalContext.current
    val currentOnResult by rememberUpdatedState(onResult)
    var pendingRequired by remember {
        mutableStateOf<List<String>?>(null)
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        val required = pendingRequired
        pendingRequired = null
        // Callback maps contain only requested permissions, not pregranted ones.
        currentOnResult(required != null && required.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        })
    }
    return { isVideo ->
        val required = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (isVideo) add(Manifest.permission.CAMERA)
        }
        val missing = required.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (pendingRequired == null) {
            if (missing.isEmpty()) {
                currentOnResult(true)
            } else {
                pendingRequired = required
                runCatching { launcher.launch(missing.toTypedArray()) }.onFailure {
                    pendingRequired = null
                    currentOnResult(false)
                }
            }
        }
    }
}
