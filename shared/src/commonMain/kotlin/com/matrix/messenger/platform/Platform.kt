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

expect fun readFileBytes(path: String): ByteArray

expect fun writeFileBytes(path: String, bytes: ByteArray)

expect fun attachmentsDir(): String

expect fun sessionStorePath(): String

expect fun createTrixnityRepositoriesModule(): org.koin.core.module.Module

/** Logs an error with optional stack trace (logcat on Android, file + stderr on Desktop). */
expect fun logError(tag: String, message: String, throwable: Throwable? = null)
