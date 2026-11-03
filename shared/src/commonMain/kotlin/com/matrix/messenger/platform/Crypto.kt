package com.matrix.messenger.platform

/** [size] cryptographically secure random bytes (keys, IVs). */
expect fun randomBytes(size: Int): ByteArray

/** URL-safe unpadded base64 (Matrix crypto encoding). */
expect fun encodeUnpaddedBase64Url(bytes: ByteArray): String
