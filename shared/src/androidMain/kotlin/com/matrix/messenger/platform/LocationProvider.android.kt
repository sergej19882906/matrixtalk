package com.matrix.messenger.platform

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.matrix.messenger.data.model.GeoLocation

@Composable
actual fun rememberLocationRequester(
    onResult: (location: GeoLocation?) -> Unit
): () -> Unit {
    val context = LocalContext.current
    val currentOnResult by rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        currentOnResult(if (granted) fetchLastKnownLocation(context) else null)
    }
    return {
        val required = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        val missing = required.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            currentOnResult(fetchLastKnownLocation(context))
        } else {
            launcher.launch(missing.toTypedArray())
        }
    }
}

/** Most recent/accurate cached fix across enabled providers (fast path). */
private fun fetchLastKnownLocation(context: Context): GeoLocation? {
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        ?: return null
    var best: Location? = null
    for (provider in manager.getProviders(true)) {
        val fix = runCatching { manager.getLastKnownLocation(provider) }.getOrNull() ?: continue
        if (best == null || fix.time > best.time) best = fix
    }
    return best?.let { GeoLocation(it.latitude, it.longitude, it.accuracy.toDouble()) }
}
