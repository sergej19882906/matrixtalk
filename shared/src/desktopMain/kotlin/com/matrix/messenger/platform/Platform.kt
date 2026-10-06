package com.matrix.messenger.platform

import java.awt.Desktop
import java.awt.FileDialog
import java.io.File
import javax.swing.filechooser.FileNameExtensionFilter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import javax.swing.JFrame
import javax.swing.JFileChooser

actual class PlatformContext

private fun pickFile(allowedTypes: List<String>): FileResult? {
    val chooser = JFileChooser().apply {
        dialogTitle = "Выбрать файл"
        isMultiSelectionEnabled = false
        val extensions = allowedTypes.flatMap { type ->
            when (type) {
                "image/*" -> listOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "tif", "tiff")
                "video/*" -> listOf("mp4", "avi", "mkv", "webm")
                "audio/*" -> listOf("mp3", "ogg", "wav", "flac", "aac")
                else -> emptyList()
            }
        }.distinct()
        if (extensions.isNotEmpty()) {
            fileFilter = FileNameExtensionFilter("Допустимые файлы", *extensions.toTypedArray())
        }
    }

    val result = chooser.showOpenDialog(null)
    if (result != JFileChooser.APPROVE_OPTION) return null

    val file = chooser.selectedFile ?: return null
    val mimeType = when (file.extension.lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "png", "gif", "webp", "bmp", "tif", "tiff" -> "image/${file.extension.lowercase()}"
        "mp4", "avi", "mkv", "webm" -> "video/${file.extension.lowercase()}"
        "mp3", "ogg", "wav", "flac", "aac" -> "audio/${file.extension.lowercase()}"
        else -> "application/octet-stream"
    }

    return FileResult(
        path = file.absolutePath,
        name = file.name,
        size = file.length(),
        mimeType = mimeType
    )
}

@Composable
actual fun rememberFilePicker(
    allowedTypes: List<String>,
    onFilePicked: (FileResult) -> Unit
): () -> Unit = remember(allowedTypes, onFilePicked) {
    {
        pickFile(allowedTypes)?.let(onFilePicked)
    }
}

actual fun openUrl(url: String) {
    if (Desktop.isDesktopSupported()) {
        Desktop.getDesktop().browse(java.net.URI(url))
    }
}

actual fun readFileBytes(path: String): ByteArray = File(path).readBytes()

actual fun writeFileBytes(path: String, bytes: ByteArray) {
    File(path).writeBytes(bytes)
}

actual fun attachmentsDir(): String {
    val home = System.getProperty("user.home") ?: error("user.home is not set")
    return File(home, ".matrixtalk/attachments").apply { mkdirs() }.absolutePath
}

actual fun sessionStorePath(): String {
    val home = System.getProperty("user.home") ?: error("user.home is not set")
    val dir = File(home, ".matrixtalk").apply { mkdirs() }
    return File(dir, "session.preferences_pb").absolutePath
}

actual fun createTrixnityRepositoriesModule(): org.koin.core.module.Module {
    val dir = File(
        System.getProperty("user.home") ?: error("user.home is not set"),
        ".matrixtalk/realm"
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

actual fun logError(tag: String, message: String, throwable: Throwable?) {
    val logDir = File(
        System.getProperty("user.home") ?: error("user.home is not set"),
        ".matrixtalk/logs"
    ).apply { mkdirs() }
    val details = throwable?.let { "\n${it.stackTraceToString()}" }.orEmpty()
    val entry = "${java.time.LocalDateTime.now()} [$tag] $message$details\n"
    logDir.resolve("error.log").appendText(entry)
    System.err.print(entry)
}
