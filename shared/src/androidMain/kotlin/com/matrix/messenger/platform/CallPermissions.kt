package com.matrix.messenger.platform

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

@Composable
actual fun rememberCallPermissionRequester(
    onResult: (granted: Boolean) -> Unit
): (isVideo: Boolean) -> Unit {
    val context = LocalContext.current
    val currentOnResult by rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        currentOnResult(grants[Manifest.permission.RECORD_AUDIO] == true)
    }
    return { isVideo ->
        val required = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (isVideo) add(Manifest.permission.CAMERA)
        }
        val missing = required.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            currentOnResult(true)
        } else {
            launcher.launch(missing.toTypedArray())
        }
    }
}
