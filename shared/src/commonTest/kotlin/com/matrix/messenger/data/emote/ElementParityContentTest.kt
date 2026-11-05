package com.matrix.messenger.data.emote

import com.matrix.messenger.data.bridge.customEventContentSerializerMappings
import com.matrix.messenger.data.location.BeaconEventContent
import com.matrix.messenger.data.location.BeaconInfoEventContent
import com.matrix.messenger.data.sticker.StickerEventContent
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import net.folivo.trixnity.core.model.events.MessageEventContent
import net.folivo.trixnity.core.model.events.StateEventContent
import net.folivo.trixnity.core.model.events.UnknownEventContent
import net.folivo.trixnity.core.model.events.m.RelatesTo
import net.folivo.trixnity.core.model.events.m.room.EncryptedFile
import net.folivo.trixnity.core.model.events.m.room.ImageInfo
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import kotlinx.serialization.ExperimentalSerializationApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalSerializationApi::class)
class ElementParityContentTest {
    private val json = createMatrixEventJson(customEventContentSerializerMappings)
    // Account data has no content-level contextual serializer: dispatch happens on the
    // event wrapper (type field at event level), unlike message/state content.
    private val globalEvent =
        requireNotNull(json.serializersModule.getContextual(net.folivo.trixnity.core.model.events.ClientEvent.GlobalAccountDataEvent::class))
    private val state = requireNotNull(json.serializersModule.getContextual(StateEventContent::class))
    private val message = requireNotNull(json.serializersModule.getContextual(MessageEventContent::class))

    private fun withType(raw: JsonObject, type: String) = JsonObject(raw + ("type" to JsonPrimitive(type)))

    @Test
    fun userEmotesGlobalAccountDataRoundTripsAndMergesImagesWithLegacyEmotes() {
        val content = UserEmotesEventContent(
            pack = EmotePackMeta(displayName = "My Pack", avatarUrl = "mxc://example.org/pack"),
            images = mapOf("new" to EmotePackEntry(url = "mxc://example.org/new", usage = listOf("sticker"))),
            emotes = mapOf("old" to EmotePackEntry(url = "mxc://example.org/old")),
            packs = listOf("!room1:example.org"),
        )
        val eventJson = withType(
            JsonObject(mapOf("content" to json.encodeToJsonElement(UserEmotesEventContent.serializer(), content))),
            UserEmotesEventContent.TYPE,
        )
        val decodedEvent = json.decodeFromJsonElement(globalEvent, eventJson)
        val decoded = assertIs<UserEmotesEventContent>(decodedEvent.content)
        assertEquals("My Pack", decoded.pack?.displayName)
        assertEquals(listOf("!room1:example.org"), decoded.packs)
        // New "images" entries win over legacy "emotes" of the same shortcode.
        assertEquals(setOf("new", "old"), decoded.entries.keys)
        assertEquals(listOf("sticker"), decoded.entries["new"]?.usage)
    }

    @Test
    fun roomEmotesStateRoundTripsPerStateKey() {
        val content = RoomEmotesEventContent(
            pack = EmotePackMeta(displayName = "Room Pack"),
            images = mapOf("smile" to EmotePackEntry(url = "mxc://example.org/smile", body = "Smile")),
        )
        val raw = json.encodeToJsonElement(state, content).jsonObject
        assertEquals("im.ponies.room_emotes", customEventContentSerializerMappings.state.first { it.kClass.isInstance(content) }.type)
        val decoded = assertIs<RoomEmotesEventContent>(json.decodeFromJsonElement(state, withType(raw, RoomEmotesEventContent.TYPE)))
        assertEquals("mxc://example.org/smile", decoded.entries["smile"]?.url)
        assertEquals("Smile", decoded.entries["smile"]?.body)
    }

    @Test
    fun stickerMessageRoundTripsWithEncryptedFile() {
        val content: MessageEventContent = StickerEventContent(
            body = "cat.png",
            file = EncryptedFile(
                url = "mxc://example.org/enc", initialisationVector = "iv", hashes = mapOf("sha256" to "h"),
                key = EncryptedFile.JWK(key = "secret"),
            ),
            info = ImageInfo(mimeType = "image/png", width = 64, height = 64, size = 128),
        )
        val raw = json.encodeToJsonElement(message, content).jsonObject
        assertEquals(JsonPrimitive("mxc://example.org/enc"), raw["file"]?.jsonObject?.get("url"))
        val decoded = assertIs<StickerEventContent>(json.decodeFromJsonElement(message, withType(raw, StickerEventContent.TYPE)))
        assertEquals("mxc://example.org/enc", decoded.file?.url)
        assertEquals("image/png", decoded.info?.mimeType)
        // Plaintext variant keeps the plain mxc url.
        val plain = StickerEventContent(body = "dog.png", url = "mxc://example.org/dog")
        val plainRaw = json.encodeToJsonElement(message, plain).jsonObject
        assertEquals(JsonPrimitive("mxc://example.org/dog"), plainRaw["url"])
        assertEquals(null, plainRaw["file"])
    }

    @Test
    fun beaconInfoStateAndBeaconMessageRoundTrip() {
        val info = BeaconInfoEventContent(timeout = 3600_000L, live = true, ts = 1000L, description = "Иду домой")
        val infoRaw = json.encodeToJsonElement(state, info).jsonObject
        val decodedInfo = assertIs<BeaconInfoEventContent>(json.decodeFromJsonElement(state, withType(infoRaw, BeaconInfoEventContent.TYPE)))
        assertEquals(3600_000L, decodedInfo.timeout)
        assertTrue(decodedInfo.live)

        val beacon: MessageEventContent = BeaconEventContent(
            location = BeaconEventContent.BeaconLocation("geo:55.7,37.6;u=5"),
            ts = 2000L,
            relatesTo = RelatesTo.Reference(net.folivo.trixnity.core.model.EventId("\$info1:example.org")),
        )
        val beaconRaw = json.encodeToJsonElement(message, beacon).jsonObject
        assertEquals(JsonPrimitive("geo:55.7,37.6;u=5"), beaconRaw["m.location"]?.jsonObject?.get("geo_uri"))
        val decodedBeacon = assertIs<BeaconEventContent>(json.decodeFromJsonElement(message, withType(beaconRaw, BeaconEventContent.TYPE)))
        val reference = assertIs<RelatesTo.Reference>(decodedBeacon.relatesTo)
        assertEquals("\$info1:example.org", reference.eventId.full)
    }

    @Test
    fun unknownEmoteTypesStillFallBackToUnknownContentForPollCompat() {
        // Polls rely on UnknownEventContent parsing: custom packs must not break that.
        val poll = buildJsonObject {
            put("type", "m.poll.start")
            put("m.poll", buildJsonObject { put("question", JsonObject(emptyMap())) })
        }
        assertIs<UnknownEventContent>(json.decodeFromJsonElement(message, poll))
    }
}
