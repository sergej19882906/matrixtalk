package com.matrix.messenger.platform

import java.awt.Desktop
import java.awt.FileDialog
import java.io.File
import javax.swing.JFrame
import javax.swing.JFileChooser

actual class PlatformContext

actual fun pickFile(allowedTypes: List<String>): FileResult? {
    val chooser = JFileChooser().apply {
        dialogTitle = "Выбрать файл"
        isMultiSelectionEnabled = false
    }

    val result = chooser.showOpenDialog(null)
    if (result != JFileChooser.APPROVE_OPTION) return null

    val file = chooser.selectedFile ?: return null
    val mimeType = when (file.extension.lowercase()) {
        "jpg", "jpeg", "png", "gif", "webp", "bmp" -> "image/${file.extension.lowercase()}"
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

actual fun openUrl(url: String) {
    if (Desktop.isDesktopSupported()) {
        Desktop.getDesktop().browse(java.net.URI(url))
    }
}

actual fun readFileBytes(path: String): ByteArray = File(path).readBytes()

actual fun sessionStorePath(): String {
    val home = System.getProperty("user.home") ?: error("user.home is not set")
    val dir = File(home, ".matrixtalk").apply { mkdirs() }
    return File(dir, "session.preferences_pb").absolutePath
}
