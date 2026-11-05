package com.matrix.messenger.data.repository

import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.MessageEventContent
import net.folivo.trixnity.core.model.events.m.room.EncryptedMessageEventContent

/** Direct upload must never bypass encryption of the room event carrying its key. */
internal suspend fun sendUploadedRoomEvent(
    roomId: RoomId,
    content: MessageEventContent,
    encryptedRoom: Boolean,
    encrypt: suspend (MessageEventContent, RoomId) -> Result<MessageEventContent>?,
    send: suspend (RoomId, MessageEventContent) -> Unit
) {
    val wireContent = if (encryptedRoom) {
        val encrypted = encrypt(content, roomId)?.getOrThrow()
            ?: error("No supported encryption service for this room")
        check(encrypted is EncryptedMessageEventContent) {
            "Encrypted room sender refused a plaintext event"
        }
        encrypted
    } else content
    send(roomId, wireContent)
}
