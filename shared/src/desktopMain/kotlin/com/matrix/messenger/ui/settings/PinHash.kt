package com.matrix.messenger.ui.settings

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

actual object PinHash {
    private const val ITERATIONS = 210_000
    private const val PREFIX = "pbkdf2-sha256"

    actual fun of(pin: String): String = hex(
        MessageDigest.getInstance("SHA-256").digest("matrixtalk:$pin".toByteArray(Charsets.UTF_8))
    )

    actual fun create(pin: String): String {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        return "$PREFIX\$$ITERATIONS\$${hex(salt)}\$${hex(derive(pin, salt, ITERATIONS))}"
    }

    actual fun isLegacy(credential: String): Boolean =
        credential.length == 64 && credential.all { it.digitToIntOrNull(16) != null }

    actual fun verify(pin: String, credential: String): Boolean {
        if (isLegacy(credential)) {
            return MessageDigest.isEqual(unhex(of(pin)), unhex(credential))
        }
        val parts = credential.split('$')
        if (parts.size != 4 || parts[0] != PREFIX) return false
        val iterations = parts[1].toIntOrNull()?.takeIf { it in 100_000..1_000_000 } ?: return false
        if (parts[2].length != 32 || parts[3].length != 64 ||
            (parts[2] + parts[3]).any { it.digitToIntOrNull(16) == null }) return false
        return MessageDigest.isEqual(unhex(parts[3]), derive(pin, unhex(parts[2]), iterations))
    }

    private fun derive(pin: String, salt: ByteArray, iterations: Int): ByteArray {
        val chars = pin.toCharArray()
        val spec = PBEKeySpec(chars, salt, iterations, 256)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            chars.fill('\u0000')
            spec.clearPassword()
        }
    }

    private fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }
    private fun unhex(value: String): ByteArray = ByteArray(value.length / 2) {
        value.substring(it * 2, it * 2 + 2).toInt(16).toByte()
    }
}
