package com.matrix.messenger.platform

import java.security.SecureRandom
import java.util.Base64

private val secureRandom = SecureRandom()

actual fun randomBytes(size: Int): ByteArray =
    ByteArray(size).also { secureRandom.nextBytes(it) }

actual fun encodeUnpaddedBase64Url(bytes: ByteArray): String =
    Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
