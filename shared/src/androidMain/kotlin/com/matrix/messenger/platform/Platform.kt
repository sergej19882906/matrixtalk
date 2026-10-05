package com.matrix.messenger.platform

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns

actual class PlatformContext(val context: Context)

actual fun pickFile(allowedTypes: List<String>): FileResult? {
    // File picking on Android requires Activity Result API,
    // which is handled at the Activity level. This returns null
    // as a fallback; the Activity will handle file selection.
    return null
}

actual fun openUrl(url: String) {
    // Requires Android Context — handled via PlatformContext in Android module
}

var appContext: Context? = null

actual fun readFileBytes(path: String): ByteArray {
    val context = appContext ?: error("Application context not initialized")
    return if (path.startsWith("content://")) {
        context.contentResolver.openInputStream(Uri.parse(path))?.use { it.readBytes() }
            ?: error("Не удалось прочитать файл")
    } else {
        java.io.File(path.removePrefix("file://")).readBytes()
    }
}

actual fun writeFileBytes(path: String, bytes: ByteArray) {
    java.io.File(path).writeBytes(bytes)
}

actual fun attachmentsDir(): String {
    val context = appContext ?: error("Application context not initialized")
    return java.io.File(context.cacheDir, "attachments").apply { mkdirs() }.absolutePath
}

actual fun sessionStorePath(): String {
    val context = appContext ?: error("Application context not initialized")
    return java.io.File(context.filesDir, "session.preferences_pb").absolutePath
}

actual fun createTrixnityRepositoriesModule(): org.koin.core.module.Module {
    val dir = java.io.File(
        appContext?.filesDir ?: error("Application context not initialized"),
        "realm"
    ).apply { mkdirs() }
    return try {
        net.folivo.trixnity.client.store.repository.realm.createRealmRepositoriesModule {
            directory(dir.absolutePath)
        }
    } catch (t: Throwable) {
        // e.g. missing native Realm library on this platform: stay functional without persistence
        net.folivo.trixnity.client.store.repository.createInMemoryRepositoriesModule()
    }
}

fun openUrlWith(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(intent)
}

fun resolveFileInfo(context: Context, uri: Uri): FileResult? {
    var name = uri.lastPathSegment?.substringAfterLast('/').orEmpty()
    var size = -1L
    var mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"

    if (uri.scheme == "content") {
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null, null, null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIndex >= 0) name = cursor.getString(nameIndex)
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
            }
        }
    } else {
        val file = java.io.File(uri.path ?: return null)
        if (!file.exists()) return null
        name = file.name
        size = file.length()
    }

    return FileResult(path = uri.toString(), name = name, size = size, mimeType = mimeType)
}
