package com.matrix.messenger.platform

import java.awt.Desktop
import com.matrix.messenger.data.call.CallSignalingCommand
import com.matrix.messenger.data.call.CallInviteEventContent
import com.matrix.messenger.data.call.callEventContentSerializerMappings
import com.matrix.messenger.data.call.callEventContentSerializerMappingsModule
import net.folivo.trixnity.core.serialization.events.DefaultEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMapping
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

actual fun openLocalFile(path: String) {
    if (!Desktop.isDesktopSupported()) return
    val desktop = Desktop.getDesktop()
    val file = java.io.File(path)
    if (desktop.isSupported(Desktop.Action.OPEN)) {
        try {
            // The system file association — what "open attachment" means. Unlike
            // browse(URI), open() accepts plain paths with spaces/Cyrillic safely.
            desktop.open(file)
        } catch (t: Throwable) {
            // Rare association gaps: fall back to the shell handler.
            desktop.browse(file.toURI())
        }
    } else {
        desktop.browse(file.toURI())
    }
}

actual fun exportFile(fileName: String, bytes: ByteArray) {
    val chooser = JFileChooser().apply {
        dialogTitle = "Сохранить файл"
        selectedFile = File(fileName)
    }
    if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
        val file = chooser.selectedFile
        File(file.absolutePath.let { if (it.contains('.')) it else "$it.txt" }).writeBytes(bytes)
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
    // The typed m.call.* contents (see CallEventContents) must be present in the
    // client's EventContentSerializerMappings: the repositories module is loaded
    // before any other client module, so overriding the mapping here is safe
    // (unlike MatrixClientConfiguration.modules, whose late registration breaks
    // fromStore's Koin registry).
    val callMappings = com.matrix.messenger.data.call.callEventContentSerializerMappingsModule
    return try {
        val realmModule = net.folivo.trixnity.client.store.repository.realm.createRealmRepositoriesModule {
            directory(dir.absolutePath)
        }
        org.koin.dsl.module {
            includes(realmModule)
            single<EventContentSerializerMappings> {
                DefaultEventContentSerializerMappings + callEventContentSerializerMappings
            }
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

/**
 * Synthesized two-tone ringback (440/480 Hz, classic call tone) played in a loop —
 * desktop builds ship no audio assets.
 */
actual fun startIncomingCallRingtone(): () -> Unit {
    val sampleRate = 8000
    val toneSeconds = 2
    val samples = sampleRate * toneSeconds
    val pcm = ByteArray(samples * 2) // 16-bit mono
    for (i in 0 until samples) {
        val t = i.toDouble() / sampleRate
        val freq = if (i < samples / 2) 440.0 else 480.0
        val value = (kotlin.math.sin(2 * kotlin.math.PI * freq * t) * 12000).toInt()
        pcm[i * 2] = (value and 0xFF).toByte()
        pcm[i * 2 + 1] = ((value shr 8) and 0xFF).toByte()
    }
    val format = javax.sound.sampled.AudioFormat(
        sampleRate.toFloat(), 16, 1, true, false
    )
    val audioIn = javax.sound.sampled.AudioInputStream(
        java.io.ByteArrayInputStream(pcm), format, samples.toLong()
    )
    val clip = runCatching {
        javax.sound.sampled.AudioSystem.getClip().apply {
            open(audioIn)
            loop(javax.sound.sampled.Clip.LOOP_CONTINUOUSLY)
        }
    }.getOrNull() ?: return {}
    return {
        runCatching {
            clip.stop()
            clip.close()
        }
    }
}

actual fun appVersionName(): String = "1.14.6"

actual fun setMessageNotificationsEnabled(enabled: Boolean) = Unit

actual suspend fun listScreenSources(): List<ScreenSource> = runCatching {
    val capturer = dev.onvoid.webrtc.media.video.desktop.ScreenCapturer()
    try {
        capturer.getDesktopSources().map { ScreenSource(id = it.id, title = it.title) }
    } finally {
        runCatching { capturer.dispose() }
    }
}.getOrDefault(emptyList())
