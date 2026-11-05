package com.matrix.messenger.ui.call

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
actual fun ElementCallScreen(url: String, onBack: () -> Unit) {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<PermissionRequest?>(null) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    val allowedOrigin = Uri.parse(url)

    fun trusted(uri: Uri): Boolean =
        uri.scheme == "https" && uri.host == allowedOrigin.host && uri.port == allowedOrigin.port &&
            allowedOrigin.scheme == "https"

    fun allowedResources(request: PermissionRequest): Array<String> = request.resources.filter { resource ->
        when (resource) {
            PermissionRequest.RESOURCE_AUDIO_CAPTURE -> ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            PermissionRequest.RESOURCE_VIDEO_CAPTURE -> ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            else -> false
        }
    }.toTypedArray()

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        pending?.let { request ->
            val resources = allowedResources(request)
            if (trusted(request.origin) && resources.isNotEmpty()) request.grant(resources) else request.deny()
        }
        pending = null
    }

    DisposableEffect(url) {
        onDispose {
            pending?.deny()
            pending = null
            webView?.apply {
                stopLoading()
                webChromeClient = null
                destroy()
            }
            webView = null
        }
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Element Call — веб-клиент") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Назад") }
        })
    }) { padding ->
        AndroidView(
            modifier = Modifier.fillMaxSize().padding(padding),
            factory = { ctx ->
                WebView(ctx).apply {
                    webView = this
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = !trusted(request.url)
                    }
                    webChromeClient = object : WebChromeClient() {
                        override fun onPermissionRequest(request: PermissionRequest) {
                            if (!trusted(request.origin)) { request.deny(); return }
                            val permissions = request.resources.mapNotNull { resource ->
                                when (resource) {
                                    PermissionRequest.RESOURCE_AUDIO_CAPTURE -> Manifest.permission.RECORD_AUDIO
                                    PermissionRequest.RESOURCE_VIDEO_CAPTURE -> Manifest.permission.CAMERA
                                    else -> null
                                }
                            }.distinct()
                            if (permissions.isEmpty()) { request.deny(); return }
                            val missing = permissions.filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
                            if (missing.isEmpty()) request.grant(allowedResources(request)) else {
                                pending?.deny()
                                pending = request
                                permissionLauncher.launch(missing.toTypedArray())
                            }
                        }
                        override fun onPermissionRequestCanceled(request: PermissionRequest) {
                            if (pending === request) pending = null
                        }
                    }
                    if (trusted(allowedOrigin)) loadUrl(url)
                }
            }
        )
    }
}
