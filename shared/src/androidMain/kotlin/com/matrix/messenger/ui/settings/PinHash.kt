package com.matrix.messenger.ui.settings

import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

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
            try {
                SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            } catch (_: NoSuchAlgorithmException) {
                // Android API 24/25 lacks this SecretKeyFactory, but supports HmacSHA256.
                // Implement the same single-block RFC 8018 derivation, not a weaker fallback.
                deriveWithHmac(pin, salt, iterations)
            }
        } finally {
            chars.fill('\u0000')
            spec.clearPassword()
        }
    }

    private fun deriveWithHmac(pin: String, salt: ByteArray, iterations: Int): ByteArray {
        val password = pin.toByteArray(Charsets.UTF_8)
        return try {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(password, "HmacSHA256"))
            var u = mac.doFinal(salt + byteArrayOf(0, 0, 0, 1))
            val result = u.copyOf()
            repeat(iterations - 1) {
                val next = mac.doFinal(u)
                u.fill(0)
                u = next
                for (i in result.indices) result[i] = (result[i].toInt() xor u[i].toInt()).toByte()
            }
            u.fill(0)
            result
        } finally {
            password.fill(0)
        }
    }

    private fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }
    private fun unhex(value: String): ByteArray = ByteArray(value.length / 2) {
        value.substring(it * 2, it * 2 + 2).toInt(16).toByte()
    }
}
