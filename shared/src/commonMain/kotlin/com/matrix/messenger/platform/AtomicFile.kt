package com.matrix.messenger.platform

/** Returns null only when the file is absent; read/permission errors propagate. */
expect fun readFileBytesIfExists(path: String): ByteArray?

/** Commit a complete file by atomic replacement; on failure preserve the old file. */
expect fun writeFileBytesAtomic(path: String, bytes: ByteArray)
