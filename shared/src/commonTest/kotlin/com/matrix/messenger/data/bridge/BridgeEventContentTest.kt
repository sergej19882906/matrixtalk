package com.matrix.messenger.data.bridge

import com.matrix.messenger.data.call.callEventContentSerializerMappings
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import net.folivo.trixnity.core.model.events.StateEventContent
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.core.serialization.events.DefaultEventContentSerializerMappings
import kotlinx.serialization.ExperimentalSerializationApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalSerializationApi::class)
class BridgeEventContentTest {
    private val mappings = customEventContentSerializerMappings
    private val json = createMatrixEventJson(mappings)

    @Test
    fun stableAndLegacyBridgeTypesAreIndependentRoomStateMappings() {
        val protocol = BridgeEventContent.BridgeProtocol(id = "telegram", displayName = "Telegram")
        val contents: List<StateEventContent> = listOf(
            BridgeEventContent(bridgeBot = "@bridge:example.org", protocol = protocol),
            LegacyBridgeEventContent(bridgeBot = "@bridge:example.org", protocol = protocol),
        )
        assertEquals(BridgeEventContent.TYPE, mappings.state.first { it.kClass.isInstance(contents[0]) }.type)
        assertEquals(LegacyBridgeEventContent.TYPE, mappings.state.first { it.kClass.isInstance(contents[1]) }.type)
        val serializer = requireNotNull(json.serializersModule.getContextual(StateEventContent::class))
        contents.forEach { content ->
            val raw = json.encodeToJsonElement(serializer, content).jsonObject
            assertEquals(JsonPrimitive("Telegram"), raw["protocol"]?.jsonObject?.get("displayname"))
            val typed = kotlinx.serialization.json.JsonObject(raw + ("type" to JsonPrimitive(mappings.state.first { it.kClass.isInstance(content) }.type)))
            assertEquals(content, json.decodeFromJsonElement(serializer, typed))
        }
        assertFalse(mappings.roomAccountData.any { it.type == BridgeEventContent.TYPE || it.type == LegacyBridgeEventContent.TYPE })
    }

    @Test
    fun combinedMappingsRetainSdkCategoriesCallsAndFiles() {
        val defaults = DefaultEventContentSerializerMappings
        assertTrue(mappings.state.containsAll(defaults.state))
        assertTrue(mappings.ephemeral.containsAll(defaults.ephemeral))
        assertTrue(mappings.ephemeralDataUnit.containsAll(defaults.ephemeralDataUnit))
        assertTrue(mappings.toDevice.containsAll(defaults.toDevice))
        assertTrue(mappings.globalAccountData.containsAll(defaults.globalAccountData))
        assertTrue(mappings.roomAccountData.containsAll(defaults.roomAccountData))
        assertTrue(mappings.message.containsAll(defaults.message.filterNot { it.type == "m.room.message" }))
        assertTrue(mappings.message.containsAll(callEventContentSerializerMappings.message))
    }
}
