package com.matrix.messenger.data.location

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.events.MessageEventContent
import net.folivo.trixnity.core.model.events.StateEventContent
import net.folivo.trixnity.core.model.events.m.Mentions
import net.folivo.trixnity.core.model.events.m.RelatesTo
import net.folivo.trixnity.core.serialization.events.DefaultEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings
import net.folivo.trixnity.core.serialization.events.MessageEventContentSerializerMapping
import net.folivo.trixnity.core.serialization.events.StateEventContentSerializerMapping

/**
 * MSC3488 live location. `m.beacon_info` is room state with the sender id as state key;
 * `m.beacon` messages reference their beacon_info event via m.relates_to m.reference.
 */
@Serializable
data class BeaconInfoEventContent(
    @SerialName("description") val description: String? = null,
    /** Broadcast duration in milliseconds from [ts]. */
    @SerialName("timeout") val timeout: Long,
    @SerialName("live") val live: Boolean,
    @SerialName("ts") val ts: Long? = null,
    @SerialName("asset") val asset: BeaconAsset? = null,
    @SerialName("external_url") override val externalUrl: String? = null,
) : StateEventContent {
    @Serializable
    data class BeaconAsset(@SerialName("type") val type: String = "m.self")

    companion object {
        const val TYPE = "m.beacon_info"
    }
}

/** `m.beacon` message: an m.location payload bound to its beacon_info state event. */
@Serializable
data class BeaconEventContent(
    @SerialName("m.location") val location: BeaconLocation,
    @SerialName("m.ts") val ts: Long? = null,
    @SerialName("m.relates_to") override val relatesTo: RelatesTo? = null,
    @SerialName("m.mentions") override val mentions: Mentions? = null,
    @SerialName("external_url") override val externalUrl: String? = null,
) : MessageEventContent {
    @Serializable
    data class BeaconLocation(@SerialName("geo_uri") val geoUri: String)

    companion object {
        const val TYPE = "m.beacon"
    }
}

val beaconEventContentSerializerMappings: EventContentSerializerMappings =
    object : EventContentSerializerMappings by DefaultEventContentSerializerMappings {
        override val state = DefaultEventContentSerializerMappings.state + setOf(
            StateEventContentSerializerMapping(
                BeaconInfoEventContent.TYPE, BeaconInfoEventContent::class, BeaconInfoEventContent.serializer(),
            ),
        )
        override val message = (
            DefaultEventContentSerializerMappings.message.filterNot { it.type == BeaconEventContent.TYPE } +
                setOf(
                    MessageEventContentSerializerMapping(
                        BeaconEventContent.TYPE, BeaconEventContent::class, BeaconEventContent.serializer(),
                    ),
                )
            ).toSet()
    }
