package com.matrix.messenger.platform

import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardCopyOption

actual fun readFileBytesIfExists(path: String): ByteArray? = try {
    Files.readAllBytes(Path.of(path))
} catch (_: NoSuchFileException) {
    null
}

actual fun writeFileBytesAtomic(path: String, bytes: ByteArray) {
    val target = Path.of(path).toAbsolutePath()
    val directory = target.parent
    Files.createDirectories(directory)
    val temporary = Files.createTempFile(directory, ".settings-", ".tmp")
    try {
        FileOutputStream(temporary.toFile()).use {
            it.write(bytes)
            it.fd.sync()
        }
        // Do not silently fall back to non-atomic copy/truncate on unsupported filesystems.
        Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    } finally {
        Files.deleteIfExists(temporary)
    }
}

actual fun deleteFileIfExists(path: String): Boolean {
    val file = java.io.File(path)
    return if (file.exists()) file.delete() else false
}
