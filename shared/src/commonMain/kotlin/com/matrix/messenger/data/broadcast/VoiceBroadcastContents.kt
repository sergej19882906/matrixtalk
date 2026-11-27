package com.matrix.messenger.data.broadcast

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.events.StateEventContent
import net.folivo.trixnity.core.serialization.events.DefaultEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings
import net.folivo.trixnity.core.serialization.events.StateEventContentSerializerMapping

/**
 * MSC3401 voice broadcast (Element-compatible layout): state event with the
 * sender id as state key; audio chunks are voice messages with an
 * m.relates_to m.reference pointing at this event.
 */
@Serializable
data class VoiceBroadcastInfoEventContent(
    @SerialName("device_id") val deviceId: String,
    @SerialName("live") val live: Boolean,
    /** Broadcast duration cap in milliseconds. */
    @SerialName("timeout") val timeout: Long = 4 * 3600_000L,
    @SerialName("last_chunk_ts") val lastChunkTs: Long? = null,
    @SerialName("external_url") override val externalUrl: String? = null,
) : StateEventContent {
    companion object {
        const val TYPE = "m.voice_broadcast_info"
    }
}

val voiceBroadcastEventContentSerializerMappings: EventContentSerializerMappings =
    object : EventContentSerializerMappings by DefaultEventContentSerializerMappings {
        override val state = DefaultEventContentSerializerMappings.state + setOf(
            StateEventContentSerializerMapping(
                VoiceBroadcastInfoEventContent.TYPE, VoiceBroadcastInfoEventContent::class, VoiceBroadcastInfoEventContent.serializer(),
            ),
        )
    }
