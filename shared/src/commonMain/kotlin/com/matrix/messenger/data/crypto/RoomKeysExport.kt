package com.matrix.messenger.data.crypto

import io.ktor.util.encodeBase64
import io.ktor.util.decodeBase64Bytes
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.folivo.trixnity.crypto.core.SecureRandom
import net.folivo.trixnity.crypto.core.decryptAes256Ctr
import net.folivo.trixnity.crypto.core.encryptAes256Ctr
import net.folivo.trixnity.crypto.core.generatePbkdf2Sha512
import net.folivo.trixnity.crypto.core.hmacSha256
import net.folivo.trixnity.utils.toByteArray
import net.folivo.trixnity.utils.toByteArrayFlow

/**
 * Element-compatible megolm room key export ("m.megolm_backup.v1.export").
 *
 * File format (see Matrix spec, "Key export format"): a JSON envelope with a
 * passphrase-encrypted payload. KDF = PBKDF2-HMAC-SHA512 (64-byte key: first
 * 32 bytes = AES key, last 32 = HMAC key), encryption = AES-CTR-256 with a
 * zero IV, authentication = first 8 bytes of HMAC-SHA256 over the ciphertext.
 */
const val KEY_EXPORT_FORMAT = "m.megolm_backup.v1.export"
private const val KEY_EXPORT_ITERATIONS = 500_000

@Serializable
data class ExportedSession(
    @SerialName("algorithm") val algorithm: String = "m.megolm.v1.aes-sha2",
    @SerialName("room_id") val roomId: String,
    @SerialName("session_id") val sessionId: String,
    @SerialName("session_key") val sessionKey: String,
    @SerialName("sender_key") val senderKey: String,
    @SerialName("sender_claimed_keys") val senderClaimedKeys: Map<String, String>,
    @SerialName("forwarding_curve25519_key_chain") val forwardingCurve25519KeyChain: List<String> = emptyList()
)

@Serializable
private data class ExportEnvelope(
    @SerialName("format") val format: String,
    @SerialName("encrypted") val encrypted: EncryptedBlock
)

@Serializable
private data class EncryptedBlock(
    @SerialName("iv") val iv: String,
    @SerialName("ciphertext") val ciphertext: String,
    @SerialName("mac") val mac: String,
    @SerialName("kdf_salt") val kdfSalt: String,
    @SerialName("kdf_type") val kdfType: String,
    @SerialName("kdf_bits") val kdfBits: Int,
    @SerialName("kdf_iterations") val kdfIterations: Int
)

private val exportJson = Json { ignoreUnknownKeys = true }

/** Encrypts the JSON array of [sessions] with the passphrase, Element-style. */
suspend fun encodeRoomKeysExport(
    sessions: List<ExportedSession>,
    passphrase: String,
    iterations: Int = KEY_EXPORT_ITERATIONS
): ByteArray {
    require(passphrase.isNotBlank()) { "Passphrase must not be blank" }
    val payload = exportJson.encodeToString(sessions).encodeToByteArray()
    val salt = SecureRandom.nextBytes(16)
    val derived = generatePbkdf2Sha512(passphrase, salt, iterations, 512)
    val aesKey = derived.copyOfRange(0, 32)
    val hmacKey = derived.copyOfRange(32, 64)
    val iv = ByteArray(16)
    val ciphertext = payload.toByteArrayFlow()
        .encryptAes256Ctr(key = aesKey, initialisationVector = iv)
        .toByteArray()
    val mac = hmacSha256(hmacKey, ciphertext).copyOfRange(0, 8)
    val envelope = ExportEnvelope(
        format = KEY_EXPORT_FORMAT,
        encrypted = EncryptedBlock(
            iv = iv.encodeBase64(),
            ciphertext = ciphertext.encodeBase64(),
            mac = mac.encodeBase64(),
            kdfSalt = salt.encodeBase64(),
            kdfType = "pbkdf2",
            kdfBits = 512,
            kdfIterations = iterations
        )
    )
    return exportJson.encodeToString(envelope).encodeToByteArray()
}

/** Decrypts an Element-format export; throws on a wrong passphrase or a malformed file. */
suspend fun decodeRoomKeysExport(
    bytes: ByteArray,
    passphrase: String
): List<ExportedSession> {
    val envelope = exportJson.decodeFromString<ExportEnvelope>(bytes.decodeToString())
    require(envelope.format == KEY_EXPORT_FORMAT) { "Unsupported key export format: ${envelope.format}" }
    val salt = envelope.encrypted.kdfSalt.decodeBase64Bytes()
    val derived = generatePbkdf2Sha512(passphrase, salt, envelope.encrypted.kdfIterations, 512)
    val aesKey = derived.copyOfRange(0, 32)
    val hmacKey = derived.copyOfRange(32, 64)
    val ciphertext = envelope.encrypted.ciphertext.decodeBase64Bytes()
    val expectedMac = hmacSha256(hmacKey, ciphertext).copyOfRange(0, 8)
    if (!expectedMac.contentEquals(envelope.encrypted.mac.decodeBase64Bytes())) {
        throw IllegalArgumentException("Неверная парольная фраза или повреждённый файл")
    }
    val iv = envelope.encrypted.iv.decodeBase64Bytes()
    val plaintext = ciphertext.toByteArrayFlow()
        .decryptAes256Ctr(key = aesKey, initialisationVector = iv)
        .toByteArray()
    return exportJson.decodeFromString(plaintext.decodeToString())
}
