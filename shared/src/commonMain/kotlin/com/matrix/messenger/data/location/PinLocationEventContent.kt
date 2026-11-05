package com.matrix.messenger.data.location

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.events.MessageEventContent
import net.folivo.trixnity.core.model.events.m.Mentions
import net.folivo.trixnity.core.model.events.m.RelatesTo
import net.folivo.trixnity.core.serialization.events.DefaultEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings
import net.folivo.trixnity.core.serialization.events.MessageEventContentSerializerMapping

/**
 * m.location room message with MSC3488 `m.asset` (Element compatibility: a static
 * share is a pin, "share my location" is m.self). Decoders without the field simply
 * ignore m.asset, so this stays wire-compatible with plain m.location.
 */
@Serializable
data class PinLocationEventContent(
    @SerialName("body") val body: String,
    @SerialName("geo_uri") val geoUri: String,
    @SerialName("m.asset") val asset: Asset = Asset("m.pin"),
    @SerialName("m.relates_to") override val relatesTo: RelatesTo? = null,
    @SerialName("m.mentions") override val mentions: Mentions? = null,
    @SerialName("external_url") override val externalUrl: String? = null,
) : MessageEventContent {
    @Serializable
    data class Asset(@SerialName("type") val type: String)

    companion object {
        const val MSGTYPE = "m.location"
    }
}

val locationAssetEventContentSerializerMappings: EventContentSerializerMappings =
    object : EventContentSerializerMappings by DefaultEventContentSerializerMappings {
        override val message = DefaultEventContentSerializerMappings.message + setOf(
            MessageEventContentSerializerMapping(
                "m.room.message", PinLocationEventContent::class, PinLocationEventContent.serializer(),
            ),
        )
    }
