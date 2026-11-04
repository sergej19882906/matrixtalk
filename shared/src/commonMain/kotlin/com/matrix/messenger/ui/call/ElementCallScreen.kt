package com.matrix.messenger.ui.call

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

/**
 * Element Call group-call surface.
 * Android: loads call.element.io in a WebView with camera/mic granted.
 * Desktop: opens the URL in the system browser and returns.
 */
@Composable
expect fun ElementCallScreen(
    url: String,
    onBack: () -> Unit
)

/** Builds the Element Call URL for a room (legacy embed route). */
fun elementCallUrl(roomId: String, homeServer: String): String {
    val server = homeServer.removePrefix("https://").removePrefix("http://").trimEnd('/')
    val via = "via=" + server.substringBefore(":")
    return "https://call.element.io/#/room/$roomId?$via"
}
