package com.matrix.messenger.platform

import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import java.io.File
import java.io.FileOutputStream

actual fun readFileBytesIfExists(path: String): ByteArray? {
    try {
        Os.stat(path)
    } catch (error: ErrnoException) {
        if (error.errno == OsConstants.ENOENT) return null
        throw error
    }
    return File(path).readBytes()
}

actual fun writeFileBytesAtomic(path: String, bytes: ByteArray) {
    val target = File(path).absoluteFile
    val directory = requireNotNull(target.parentFile)
    check(directory.isDirectory || directory.mkdirs()) { "Could not create settings directory" }
    val temporary = File.createTempFile(".settings-", ".tmp", directory)
    try {
        FileOutputStream(temporary).use {
            it.write(bytes)
            it.fd.sync()
        }
        // POSIX rename replaces atomically; a same-directory temporary avoids cross-device moves.
        Os.rename(temporary.absolutePath, target.absolutePath)
    } finally {
        temporary.delete()
    }
}
