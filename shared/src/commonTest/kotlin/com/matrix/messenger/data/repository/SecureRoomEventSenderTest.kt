package com.matrix.messenger.data.repository

import kotlinx.coroutines.test.runTest
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.MessageEventContent
import net.folivo.trixnity.core.model.events.m.room.EncryptedFile
import net.folivo.trixnity.core.model.events.m.room.EncryptedMessageEventContent
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull

class SecureRoomEventSenderTest {
    private val room = RoomId("!private:example.org")
    private val plaintext = RoomMessageEventContent.FileBased.Image(
        body = "private caption",
        file = EncryptedFile("mxc://example.org/file", EncryptedFile.JWK("secret-key"), "secret-iv", mapOf("sha256" to "hash"))
    )

    @Test
    fun encryptedRoomSendsOnlyEncryptedEnvelope() = runTest {
        var sent: MessageEventContent? = null
        val envelope = EncryptedMessageEventContent.MegolmEncryptedMessageEventContent("opaque ciphertext", sessionId = "session")
        sendUploadedRoomEvent(room, plaintext, true,
            encrypt = { content, id -> assertEquals(plaintext, content); assertEquals(room, id); Result.success(envelope) },
            send = { _, content -> sent = content })
        assertIs<EncryptedMessageEventContent>(sent)
        assertEquals(envelope, sent)
    }

    @Test
    fun unavailableEncryptionNeverCallsSender() = runTest {
        var sent: MessageEventContent? = null
        assertFailsWith<IllegalStateException> {
            sendUploadedRoomEvent(room, plaintext, true, { _, _ -> null }, { _, body -> sent = body })
        }
        assertNull(sent)
    }

    @Test
    fun failedEncryptionNeverCallsSender() = runTest {
        var sent: MessageEventContent? = null
        assertFailsWith<IllegalStateException> {
            sendUploadedRoomEvent(room, plaintext, true, { _, _ -> Result.failure(IllegalStateException("key unavailable")) }, { _, body -> sent = body })
        }
        assertNull(sent)
    }

    @Test
    fun plaintextReturnedByEncryptionServiceIsRejected() = runTest {
        var sent: MessageEventContent? = null
        assertFailsWith<IllegalStateException> {
            sendUploadedRoomEvent(room, plaintext, true, { body, _ -> Result.success(body) }, { _, body -> sent = body })
        }
        assertNull(sent)
    }

    @Test
    fun unencryptedRoomKeepsStandardMessage() = runTest {
        var sent: MessageEventContent? = null
        sendUploadedRoomEvent(room, plaintext, false, { _, _ -> error("Encryption not expected") }, { _, body -> sent = body })
        assertEquals(plaintext, sent)
    }
}
