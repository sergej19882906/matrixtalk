package com.matrix.messenger.data.sticker

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.events.MessageEventContent
import net.folivo.trixnity.core.model.events.m.Mentions
import net.folivo.trixnity.core.model.events.m.RelatesTo
import net.folivo.trixnity.core.model.events.m.room.EncryptedFile
import net.folivo.trixnity.core.model.events.m.room.ImageInfo
import net.folivo.trixnity.core.serialization.events.DefaultEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings
import net.folivo.trixnity.core.serialization.events.MessageEventContentSerializerMapping

/**
 * Typed `m.sticker` room message (a room message type, not a msgtype). Kept custom so
 * incoming stickers deserialize predictably on both platforms instead of UnknownEventContent.
 */
@Serializable
data class StickerEventContent(
    @SerialName("body") val body: String,
    @SerialName("url") val url: String? = null,
    @SerialName("file") val file: EncryptedFile? = null,
    @SerialName("info") val info: ImageInfo? = null,
    @SerialName("m.relates_to") override val relatesTo: RelatesTo? = null,
    @SerialName("m.mentions") override val mentions: Mentions? = null,
    @SerialName("external_url") override val externalUrl: String? = null,
) : MessageEventContent {
    companion object {
        const val TYPE = "m.sticker"
    }
}

val stickerEventContentSerializerMappings: EventContentSerializerMappings =
    object : EventContentSerializerMappings by DefaultEventContentSerializerMappings {
        override val message = DefaultEventContentSerializerMappings.message + setOf(
            MessageEventContentSerializerMapping(
                StickerEventContent.TYPE, StickerEventContent::class, StickerEventContent.serializer(),
            ),
        )
    }
