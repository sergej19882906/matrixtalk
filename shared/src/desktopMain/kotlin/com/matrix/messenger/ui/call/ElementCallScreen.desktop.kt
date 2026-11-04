package com.matrix.messenger.ui.call

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.matrix.messenger.platform.openUrl

/**
 * Desktop: Element Call runs in the system browser (no embedded webview in
 * Compose Desktop). The user logs into call.element.io once; subsequent
 * joins open the room directly.
 */
@Composable
actual fun ElementCallScreen(
    url: String,
    onBack: () -> Unit
) {
    LaunchedEffect(url) {
        openUrl(url)
        onBack()
    }
}
