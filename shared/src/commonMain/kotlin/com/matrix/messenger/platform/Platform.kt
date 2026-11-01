package com.matrix.messenger.platform

import androidx.compose.runtime.Composable

data class FileResult(
    val path: String,
    val name: String,
    val size: Long,
    val mimeType: String
)

expect class PlatformContext

@Composable
expect fun rememberFilePicker(
    allowedTypes: List<String> = emptyList(),
    onFilePicked: (FileResult) -> Unit
): () -> Unit

expect fun openUrl(url: String)

/** Opens a downloaded attachment with the OS default application for its type. */
expect fun openLocalFile(path: String)

/** Saves [bytes] as [fileName] via the platform save dialog / share sheet. */
expect fun exportFile(fileName: String, bytes: ByteArray)

expect fun readFileBytes(path: String): ByteArray

expect fun writeFileBytes(path: String, bytes: ByteArray)

expect fun attachmentsDir(): String

expect fun sessionStorePath(): String

expect fun createTrixnityRepositoriesModule(): org.koin.core.module.Module

/** Logs an error with optional stack trace (logcat on Android, file + stderr on Desktop). */
expect fun logError(tag: String, message: String, throwable: Throwable? = null)

/** Starts the incoming-call ringtone; returns a stop handle. No-op on platforms without audio. */
expect fun startIncomingCallRingtone(): () -> Unit

/** A shareable screen/window source (Desktop only; empty on Android). */
data class ScreenSource(val id: Long, val title: String)

/** Lists shareable screen/window sources. Empty on platforms without desktop capture. */
expect suspend fun listScreenSources(): List<ScreenSource>

/** Human-readable app version for the About section. */
expect fun appVersionName(): String

/** Enables/disables periodic background message notifications. No-op where unsupported. */
expect fun setMessageNotificationsEnabled(enabled: Boolean)
