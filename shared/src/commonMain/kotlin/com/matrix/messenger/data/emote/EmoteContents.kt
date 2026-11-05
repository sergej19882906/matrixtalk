package com.matrix.messenger.data.emote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import net.folivo.trixnity.core.model.events.GlobalAccountDataEventContent
import net.folivo.trixnity.core.model.events.StateEventContent
import net.folivo.trixnity.core.serialization.events.DefaultEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappingImpl
import net.folivo.trixnity.core.serialization.events.StateEventContentSerializerMapping

/**
 * MSC2545 image packs. One shortcode -> emote/sticker map entry; the wire format
 * moved from "emotes" to "images", so readers merge both keys (newer wins).
 */
@Serializable
data class EmotePackEntry(
    @SerialName("url") val url: String? = null,
    @SerialName("body") val body: String? = null,
    @SerialName("info") val info: JsonObject? = null,
    /** Sticker entries are usable as stickers; default usage is ["emoticon","sticker"]. */
    @SerialName("usage") val usage: List<String>? = null,
)

@Serializable
data class EmotePackMeta(
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("attribution") val attribution: String? = null,
)

/** Global user packs: account data event `im.ponies.user_emotes`. */
@Serializable
data class UserEmotesEventContent(
    @SerialName("pack") val pack: EmotePackMeta? = null,
    @SerialName("images") val images: Map<String, EmotePackEntry>? = null,
    @SerialName("emotes") val emotes: Map<String, EmotePackEntry>? = null,
    /** Rooms whose `im.ponies.room_emotes` packs the user subscribes to (original MSC layout). */
    @SerialName("packs") val packs: List<String>? = null,
) : GlobalAccountDataEventContent {
    val entries: Map<String, EmotePackEntry>
        get() = buildMap {
            emotes?.let { putAll(it) }
            images?.let { putAll(it) }
        }

    companion object {
        const val TYPE = "im.ponies.user_emotes"
    }
}

/** Global list of rooms providing packs: account data event `im.ponies.emote_rooms`. */
@Serializable
data class EmoteRoomsEventContent(
    /** roomId -> state key -> pack metadata ("" is the default pack). */
    @SerialName("rooms") val rooms: Map<String, Map<String, JsonObject>>? = null,
) : GlobalAccountDataEventContent {
    val roomIds: Set<String> get() = rooms?.keys ?: emptySet()

    companion object {
        const val TYPE = "im.ponies.emote_rooms"
    }
}

/** Room pack: state event `im.ponies.room_emotes`, state key is the pack id ("" default). */
@Serializable
data class RoomEmotesEventContent(
    @SerialName("pack") val pack: EmotePackMeta? = null,
    @SerialName("images") val images: Map<String, EmotePackEntry>? = null,
    @SerialName("emotes") val emotes: Map<String, EmotePackEntry>? = null,
    @SerialName("external_url") override val externalUrl: String? = null,
) : StateEventContent {
    val entries: Map<String, EmotePackEntry>
        get() = buildMap {
            emotes?.let { putAll(it) }
            images?.let { putAll(it) }
        }

    companion object {
        const val TYPE = "im.ponies.room_emotes"
    }
}

val emoteEventContentSerializerMappings: EventContentSerializerMappings =
    object : EventContentSerializerMappings by DefaultEventContentSerializerMappings {
        override val globalAccountData =
            DefaultEventContentSerializerMappings.globalAccountData + setOf(
                EventContentSerializerMappingImpl<GlobalAccountDataEventContent>(
                    UserEmotesEventContent.TYPE, UserEmotesEventContent::class, UserEmotesEventContent.serializer(),
                ),
                EventContentSerializerMappingImpl<GlobalAccountDataEventContent>(
                    EmoteRoomsEventContent.TYPE, EmoteRoomsEventContent::class, EmoteRoomsEventContent.serializer(),
                ),
            )
        override val state = DefaultEventContentSerializerMappings.state + setOf(
            StateEventContentSerializerMapping(
                RoomEmotesEventContent.TYPE, RoomEmotesEventContent::class, RoomEmotesEventContent.serializer(),
            ),
        )
    }
