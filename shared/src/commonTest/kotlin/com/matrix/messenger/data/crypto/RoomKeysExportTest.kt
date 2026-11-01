package com.matrix.messenger.data.crypto

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RoomKeysExportTest {

    private val sessions = listOf(
        ExportedSession(
            roomId = "!abc:matrix.org",
            sessionId = "session1",
            sessionKey = "F0zRqW5vUkVQbGFjZWhvbGRlclNlc3Npb25LZXk",
            senderKey = "curve25519senderkeyvalue",
            senderClaimedKeys = mapOf("ed25519" to "ed25519claimed"),
            forwardingCurve25519KeyChain = listOf("fwd1", "fwd2")
        ),
        ExportedSession(
            roomId = "!def:matrix.org",
            sessionId = "session2",
            sessionKey = "QW5vdGhlclNlc3Npb25LZXlGb3JUZXN0aW5n",
            senderKey = "anothercurve25519",
            senderClaimedKeys = mapOf("ed25519" to "ed25519claimed2")
        )
    )

    @Test
    fun `roundtrip encrypt-decrypt restores all sessions`() = runTest {
        val bytes = encodeRoomKeysExport(sessions, "pass-phrase", iterations = 1_000)
        val decoded = decodeRoomKeysExport(bytes, "pass-phrase")
        assertEquals(sessions, decoded)
    }

    @Test
    fun `envelope contains Element format markers`() = runTest {
        val text = encodeRoomKeysExport(sessions, "pass", iterations = 1_000).decodeToString()
        assertTrue(text.contains("\"format\":\"m.megolm_backup.v1.export\""), text)
        assertTrue(text.contains("\"kdf_type\":\"pbkdf2\""), text)
        assertTrue(text.contains("\"kdf_bits\":512"), text)
        assertTrue(text.contains("\"kdf_iterations\":1000"), text)
    }

    @Test
    fun `wrong passphrase fails with readable error`() = runTest {
        val bytes = encodeRoomKeysExport(sessions, "correct", iterations = 1_000)
        val error = assertFailsWith<IllegalArgumentException> {
            decodeRoomKeysExport(bytes, "wrong")
        }
        assertTrue(error.message!!.contains("парольн"))
    }
}
