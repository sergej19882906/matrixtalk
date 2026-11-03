package com.matrix.messenger.data.bridge

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.events.EphemeralDataUnitContent
import net.folivo.trixnity.core.model.events.EphemeralEventContent
import net.folivo.trixnity.core.model.events.GlobalAccountDataEventContent
import net.folivo.trixnity.core.model.events.RoomAccountDataEventContent
import net.folivo.trixnity.core.model.events.ToDeviceEventContent
import net.folivo.trixnity.core.serialization.events.DefaultEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMapping
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings
import net.folivo.trixnity.core.serialization.events.MessageEventContentSerializerMapping
import net.folivo.trixnity.core.serialization.events.StateEventContentSerializerMapping
import kotlin.reflect.KClass

/**
 * MSC2346 room account data (m.bridge): published by appservice bridges on
 * every bridged room. Lets clients show "this is a Telegram bridge" badges.
 */
@Serializable
data class BridgeEventContent(
    @SerialName("bridgebot") val bridgeBot: String? = null,
    @SerialName("protocol") val protocol: BridgeProtocol? = null,
    @SerialName("channel") val channel: BridgeChannel? = null,
    @SerialName("network") val network: BridgeNetwork? = null,
) : RoomAccountDataEventContent {

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

/** Human-readable network name: "Telegram", "WhatsApp", … */
val BridgeEventContent.displayName: String?
    get() = protocol?.displayName ?: network?.displayName
        ?: channel?.displayName ?: protocol?.id

@Suppress("UNCHECKED_CAST")
private val bridgeRoomAccountDataMapping: EventContentSerializerMapping<RoomAccountDataEventContent> =
    object : EventContentSerializerMapping<RoomAccountDataEventContent> {
        override val type: String = BridgeEventContent.TYPE
        override val kClass: KClass<out RoomAccountDataEventContent> = BridgeEventContent::class
        override val serializer: KSerializer<RoomAccountDataEventContent> =
            BridgeEventContent.serializer() as KSerializer<RoomAccountDataEventContent>
    }

val bridgeEventContentSerializerMappings = object : EventContentSerializerMappings {
    override val message = emptySet<MessageEventContentSerializerMapping>()
    override val state = emptySet<StateEventContentSerializerMapping>()
    override val ephemeral = emptySet<EventContentSerializerMapping<EphemeralEventContent>>()
    override val ephemeralDataUnit = emptySet<EventContentSerializerMapping<EphemeralDataUnitContent>>()
    override val toDevice = emptySet<EventContentSerializerMapping<ToDeviceEventContent>>()
    override val globalAccountData = emptySet<EventContentSerializerMapping<GlobalAccountDataEventContent>>()
    override val roomAccountData = setOf(bridgeRoomAccountDataMapping)
}

/**
 * All custom content mappings of the app (call signaling, bridge info) merged
 * with the trixnity defaults. Registered as a single Koin binding so the
 * MatrixClient picks them up.
 */
val customEventContentSerializerMappingsModule: org.koin.core.module.Module = org.koin.dsl.module {
    single<EventContentSerializerMappings> {
        DefaultEventContentSerializerMappings +
            com.matrix.messenger.data.call.callEventContentSerializerMappings +
            bridgeEventContentSerializerMappings
    }
}
