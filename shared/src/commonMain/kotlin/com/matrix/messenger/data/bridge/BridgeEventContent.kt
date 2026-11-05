package com.matrix.messenger.data.bridge

import com.matrix.messenger.data.call.callEventContentSerializerMappings
import com.matrix.messenger.data.emote.emoteEventContentSerializerMappings
import com.matrix.messenger.data.location.PinLocationEventContent
import com.matrix.messenger.data.location.beaconEventContentSerializerMappings
import com.matrix.messenger.data.location.locationAssetEventContentSerializerMappings
import com.matrix.messenger.data.sticker.stickerEventContentSerializerMappings
import com.matrix.messenger.data.voice.voiceEventContentSerializerMappings
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.events.StateEventContent
import net.folivo.trixnity.core.serialization.events.DefaultEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings
import net.folivo.trixnity.core.serialization.events.StateEventContentSerializerMapping

/** MSC2346 bridge room state, published by appservice bridges. */
@Serializable
data class BridgeEventContent(
    @SerialName("bridgebot") val bridgeBot: String? = null,
    @SerialName("protocol") val protocol: BridgeProtocol? = null,
    @SerialName("channel") val channel: BridgeChannel? = null,
    @SerialName("network") val network: BridgeNetwork? = null,
    @SerialName("external_url") override val externalUrl: String? = null,
) : StateEventContent {
    @Serializable
    data class BridgeProtocol(
        @SerialName("id") val id: String? = null,
        @SerialName("displayname") val displayName: String? = null,
        @SerialName("homepage") val homepage: String? = null,
    )

    @Serializable
    data class BridgeChannel(
        @SerialName("id") val id: String? = null,
        @SerialName("network") val network: String? = null,
        @SerialName("displayname") val displayName: String? = null,
    )

    @Serializable
    data class BridgeNetwork(
        @SerialName("id") val id: String? = null,
        @SerialName("displayname") val displayName: String? = null,
    )

    companion object {
        const val TYPE = "m.bridge"
    }
}

/** Legacy bridge state has its own class, so serialization preserves its wire type. */
@Serializable
data class LegacyBridgeEventContent(
    @SerialName("bridgebot") val bridgeBot: String? = null,
    @SerialName("protocol") val protocol: BridgeEventContent.BridgeProtocol? = null,
    @SerialName("channel") val channel: BridgeEventContent.BridgeChannel? = null,
    @SerialName("network") val network: BridgeEventContent.BridgeNetwork? = null,
    @SerialName("external_url") override val externalUrl: String? = null,
) : StateEventContent {
    fun toBridgeEventContent(): BridgeEventContent =
        BridgeEventContent(bridgeBot, protocol, channel, network, externalUrl)

    companion object {
        const val TYPE = "uk.half-shot.bridge"
    }
}

/** Human-readable network name: "Telegram", "WhatsApp", etc. */
val BridgeEventContent.displayName: String?
    get() = protocol?.displayName ?: network?.displayName
        ?: channel?.displayName ?: protocol?.id

val LegacyBridgeEventContent.displayName: String?
    get() = toBridgeEventContent().displayName

/** m.bridge state mappings (stable + legacy wire types); merged into [customEventContentSerializerMappings]. */
val bridgeStateEventContentMappings: Set<StateEventContentSerializerMapping> = linkedSetOf(
    StateEventContentSerializerMapping(BridgeEventContent.TYPE, BridgeEventContent::class, BridgeEventContent.serializer()),
    StateEventContentSerializerMapping(LegacyBridgeEventContent.TYPE, LegacyBridgeEventContent::class, LegacyBridgeEventContent.serializer()),
)

/** Keep every SDK category and call mapping; replace only the stock message dispatcher. */
val customEventContentSerializerMappings: EventContentSerializerMappings =
    object : EventContentSerializerMappings by DefaultEventContentSerializerMappings {
        override val message = (
            DefaultEventContentSerializerMappings.message.filterNot { it.type == "m.room.message" } +
                callEventContentSerializerMappings.message.filterNot { it.type == "m.room.message" } +
                voiceEventContentSerializerMappings.message +
                stickerEventContentSerializerMappings.message.filter { it.type == "m.sticker" } +
                beaconEventContentSerializerMappings.message.filter { it.type == "m.beacon" } +
                locationAssetEventContentSerializerMappings.message.filter { it.kClass == PinLocationEventContent::class }
            ).toSet()
        override val state = DefaultEventContentSerializerMappings.state +
            bridgeStateEventContentMappings +
            emoteEventContentSerializerMappings.state.filter { it.type.startsWith("im.ponies.") } +
            beaconEventContentSerializerMappings.state.filter { it.type == "m.beacon_info" }
        override val globalAccountData = DefaultEventContentSerializerMappings.globalAccountData +
            emoteEventContentSerializerMappings.globalAccountData.filter { it.type.startsWith("im.ponies.") }
    }

/** One Koin binding registers all content mappings used by MatrixClient and its stores. */
val customEventContentSerializerMappingsModule: org.koin.core.module.Module = org.koin.dsl.module {
    single<EventContentSerializerMappings> { customEventContentSerializerMappings }
}
