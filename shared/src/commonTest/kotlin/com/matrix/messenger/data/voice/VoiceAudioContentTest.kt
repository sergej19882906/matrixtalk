package com.matrix.messenger.data.voice

import com.matrix.messenger.data.bridge.customEventContentSerializerMappings
import com.matrix.messenger.data.call.CallHangupEventContent
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import net.folivo.trixnity.core.model.events.MessageEventContent
import net.folivo.trixnity.core.model.events.m.room.AudioInfo
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import kotlinx.serialization.ExperimentalSerializationApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalSerializationApi::class)
class VoiceAudioContentTest {
    private val mappings = customEventContentSerializerMappings
    private val json = createMatrixEventJson(mappings)
    private val contextual = requireNotNull(json.serializersModule.getContextual(MessageEventContent::class))

    private fun contextualRoundTrip(content: MessageEventContent): MessageEventContent {
        val raw = json.encodeToJsonElement(contextual, content).jsonObject
        val type = mappings.message.first { it.kClass.isInstance(content) }.type
        return json.decodeFromJsonElement(contextual, JsonObject(raw + ("type" to JsonPrimitive(type))))
    }

    @Test
    fun factoryWritesVoiceMarkerAndAudioMetadataOnStandardRoomMessage() {
        val stock = RoomMessageEventContent.FileBased.Audio(
            body = "Voice message", url = "mxc://example.org/voice",
            info = AudioInfo(duration = 1500, mimeType = "audio/ogg", size = 99),
        )
        val voice = VoiceAudioContent.fromAudio(stock, waveform = listOf(0, 300, 1024))
        val raw = json.encodeToJsonElement(VoiceRoomMessageEventContentSerializer, voice).jsonObject
        assertEquals(JsonObject(emptyMap()), raw[VOICE_MESSAGE_KEY])
        assertEquals(JsonPrimitive(1500), raw[VOICE_AUDIO_KEY]?.jsonObject?.get("duration"))
        assertEquals(JsonPrimitive("m.audio"), raw["msgtype"])
        assertEquals(JsonPrimitive("mxc://example.org/voice"), raw["url"])
        assertEquals("m.room.message", mappings.message.first { it.kClass.isInstance(voice) }.type)

        val restored = assertIs<VoiceAudioContent>(
            json.decodeFromJsonElement(VoiceRoomMessageEventContentSerializer, raw),
        )
        assertEquals(stock, restored.stockAudio)
        assertEquals(1500L, restored.duration)
        assertEquals(listOf(0, 300, 1024), restored.waveform)
    }

    @Test
    fun encryptedFileAndStockRelationFieldsSurviveIncomingVoiceRoundTrip() {
        val raw = json.parseToJsonElement("""
            {
              "msgtype":"m.audio", "body":"Voice message", "filename":"voice.ogg",
              "info":{"duration":2000,"mimetype":"audio/ogg","size":128},
              "file":{
                "url":"mxc://example.org/encrypted",
                "key":{"kty":"oct","key_ops":["encrypt","decrypt"],"alg":"A256CTR","k":"secret","ext":true},
                "iv":"iv", "hashes":{"sha256":"hash"}, "v":"v2"
              },
              "m.relates_to":{"m.in_reply_to":{"event_id":"reply"}},
              "m.mentions":{"user_ids":["@alice:example.org"]},
              "external_url":"https://example.org/voice",
              "org.matrix.msc3245.voice":{},
              "org.matrix.msc1767.audio":{"duration":1900,"waveform":[0,512,1024],"future":true}
            }
        """).jsonObject
        val voice = assertIs<VoiceAudioContent>(
            json.decodeFromJsonElement(VoiceRoomMessageEventContentSerializer, raw),
        )
        assertNotNull(voice.stockAudio.file)
        assertNotNull(voice.relatesTo)
        assertNotNull(voice.mentions)
        assertEquals("https://example.org/voice", voice.externalUrl)
        assertEquals(1900L, voice.duration)
        val encoded = json.encodeToJsonElement(VoiceRoomMessageEventContentSerializer, voice).jsonObject
        listOf("file", "info", "m.relates_to", "m.mentions", "external_url", "filename", VOICE_AUDIO_KEY, VOICE_MESSAGE_KEY)
            .forEach { key -> assertEquals(raw[key], encoded[key], key) }
    }

    @Test
    fun ordinaryMessagesKeepStockTypedSerialization() {
        val messages: List<MessageEventContent> = listOf(
            RoomMessageEventContent.TextBased.Text(body = "Hello"),
            RoomMessageEventContent.TextBased.Notice(body = "Notice"),
            RoomMessageEventContent.FileBased.Audio(body = "Music", url = "mxc://example.org/music"),
            RoomMessageEventContent.FileBased.File(body = "File", url = "mxc://example.org/file"),
            RoomMessageEventContent.FileBased.Image(body = "Image", url = "mxc://example.org/image"),
            RoomMessageEventContent.FileBased.Video(body = "Video", url = "mxc://example.org/video"),
        )
        messages.forEach { message ->
            val raw = json.encodeToJsonElement(VoiceRoomMessageEventContentSerializer, message).jsonObject
            assertFalse(VOICE_MESSAGE_KEY in raw)
            assertEquals(message, json.decodeFromJsonElement(VoiceRoomMessageEventContentSerializer, raw))
            assertEquals(message, contextualRoundTrip(message))
        }
    }

    @Test
    fun duplicateWireMappingsShareDispatcherAndContextualOutboxRoundTrips() {
        val entries = mappings.message.filter { it.type == "m.room.message" }
        assertEquals(3, entries.size)
        val voice = VoiceAudioContent.fromAudio(
            RoomMessageEventContent.FileBased.Audio(body = "Voice", url = "mxc://example.org/voice"),
            duration = 1200, waveform = listOf(1, 2),
        )
        // Voice-capable wire mappings wrap the voice dispatcher: encoding the voice
        // content through them must emit the MSC voice marker (a stock audio or
        // pin-location serializer would not accept it at all).
        entries.forEach { entry ->
            if (entry.kClass.isInstance(voice)) {
                assertTrue(VOICE_MESSAGE_KEY in json.encodeToJsonElement(entry.serializer, voice).jsonObject)
            }
        }
        val restored = assertIs<VoiceAudioContent>(contextualRoundTrip(voice))
        assertEquals(voice.stockAudio, restored.stockAudio)
        assertEquals(voice.waveform, restored.waveform)
        assertEquals(voice.duration, restored.duration)
        val voiceEntry = mappings.message.first { it.kClass.isInstance(voice) }
        assertEquals("m.room.message", voiceEntry.type)
        assertTrue(VOICE_MESSAGE_KEY in json.encodeToJsonElement(voiceEntry.serializer, voice).jsonObject)
        val call = CallHangupEventContent(callId = "call")
        assertEquals(call, contextualRoundTrip(call))
    }

    @Test
    fun markerOnTextAndAudioMetadataWithoutMarkerAreNotVoice() {
        val text = buildJsonObject {
            put("msgtype", "m.text")
            put("body", "Hello")
            put(VOICE_MESSAGE_KEY, JsonObject(emptyMap()))
        }
        assertIs<RoomMessageEventContent.TextBased.Text>(
            json.decodeFromJsonElement(VoiceRoomMessageEventContentSerializer, text),
        )
        val audio = buildJsonObject {
            put("msgtype", "m.audio")
            put("body", "Music")
            put(VOICE_AUDIO_KEY, JsonObject(emptyMap()))
        }
        assertIs<RoomMessageEventContent.FileBased.Audio>(
            json.decodeFromJsonElement(VoiceRoomMessageEventContentSerializer, audio),
        )
    }
}
