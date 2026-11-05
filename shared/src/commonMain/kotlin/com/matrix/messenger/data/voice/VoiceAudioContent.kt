package com.matrix.messenger.data.voice

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import net.folivo.trixnity.core.model.events.EphemeralDataUnitContent
import net.folivo.trixnity.core.model.events.EphemeralEventContent
import net.folivo.trixnity.core.model.events.GlobalAccountDataEventContent
import net.folivo.trixnity.core.model.events.MessageEventContent
import net.folivo.trixnity.core.model.events.RoomAccountDataEventContent
import net.folivo.trixnity.core.model.events.ToDeviceEventContent
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContentSerializer
import net.folivo.trixnity.core.serialization.events.DefaultEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings
import net.folivo.trixnity.core.serialization.events.MessageEventContentSerializerMapping

const val VOICE_MESSAGE_KEY = "org.matrix.msc3245.voice"
const val VOICE_AUDIO_KEY = "org.matrix.msc1767.audio"

/** Voice metadata around the stock audio model, without extending its sealed hierarchy. */
@Serializable(with = VoiceAudioContentSerializer::class)
data class VoiceAudioContent(
    val stockAudio: RoomMessageEventContent.FileBased.Audio,
    val duration: Long? = stockAudio.info?.duration,
    val waveform: List<Int>? = null,
    internal val audioMetadata: JsonObject? = null,
    internal val voiceMetadata: JsonObject = JsonObject(emptyMap()),
) : MessageEventContent by stockAudio {
    companion object {
        fun fromAudio(
            stockAudio: RoomMessageEventContent.FileBased.Audio,
            duration: Long? = stockAudio.info?.duration,
            waveform: List<Int>? = null,
        ): VoiceAudioContent = VoiceAudioContent(stockAudio, duration, waveform)
    }
}

/** One dispatcher must serve BOTH classes registered for the m.room.message wire type. */
object VoiceRoomMessageEventContentSerializer : KSerializer<MessageEventContent> {
    override val descriptor = RoomMessageEventContentSerializer.descriptor

    override fun deserialize(decoder: Decoder): MessageEventContent {
        val jsonDecoder = decoder as? JsonDecoder
            ?: throw SerializationException("Room message content requires JSON")
        val raw = jsonDecoder.decodeJsonElement() as? JsonObject
            ?: throw SerializationException("Room message content must be an object")
        val stockRaw = JsonObject(raw.filterKeys { it != VOICE_MESSAGE_KEY && it != VOICE_AUDIO_KEY })
        val stock = jsonDecoder.json.decodeFromJsonElement(RoomMessageEventContentSerializer, stockRaw)
        val voice = raw[VOICE_MESSAGE_KEY] as? JsonObject
        if (stock !is RoomMessageEventContent.FileBased.Audio || voice == null) return stock
        val audio = raw[VOICE_AUDIO_KEY] as? JsonObject
        val waveform = (audio?.get("waveform") as? JsonArray)?.let { values ->
            val samples = values.mapNotNull { (it as? JsonPrimitive)?.intOrNull }
            samples.takeIf { it.size == values.size }
        }
        return VoiceAudioContent(
            stockAudio = stock,
            duration = (audio?.get("duration") as? JsonPrimitive)?.longOrNull ?: stock.info?.duration,
            waveform = waveform,
            audioMetadata = audio,
            voiceMetadata = voice,
        )
    }

    override fun serialize(encoder: Encoder, value: MessageEventContent) {
        val jsonEncoder = encoder as? JsonEncoder
            ?: throw SerializationException("Room message content requires JSON")
        val stock = when (value) {
            is VoiceAudioContent -> value.stockAudio
            is RoomMessageEventContent -> value
            else -> throw SerializationException("Not an m.room.message content: ${value::class}")
        }
        val stockRaw = jsonEncoder.json.encodeToJsonElement(RoomMessageEventContentSerializer, stock) as JsonObject
        if (value !is VoiceAudioContent) {
            jsonEncoder.encodeJsonElement(stockRaw)
            return
        }
        val audio = buildJsonObject {
            value.audioMetadata?.forEach { (key, element) -> put(key, element) }
            value.duration?.let { put("duration", it) }
            value.waveform?.let { samples -> put("waveform", JsonArray(samples.map(::JsonPrimitive))) }
        }
        jsonEncoder.encodeJsonElement(JsonObject(stockRaw + mapOf(
            VOICE_MESSAGE_KEY to value.voiceMetadata,
            VOICE_AUDIO_KEY to audio,
        )))
    }
}

object VoiceAudioContentSerializer : KSerializer<VoiceAudioContent> {
    override val descriptor = VoiceRoomMessageEventContentSerializer.descriptor

    override fun deserialize(decoder: Decoder): VoiceAudioContent =
        VoiceRoomMessageEventContentSerializer.deserialize(decoder) as? VoiceAudioContent
            ?: throw SerializationException("Expected an m.audio voice message")

    override fun serialize(encoder: Encoder, value: VoiceAudioContent) =
        VoiceRoomMessageEventContentSerializer.serialize(encoder, value)
}

val voiceEventContentSerializerMappings: EventContentSerializerMappings =
    object : EventContentSerializerMappings by (DefaultEventContentSerializerMappings - DefaultEventContentSerializerMappings) {
        // Trixnity looks up the first mapping by class, but associates the last by wire type.
        // Both entries deliberately share this dispatcher; stock text/files retain SDK typing.
        override val message = linkedSetOf(
            MessageEventContentSerializerMapping(
                "m.room.message", RoomMessageEventContent::class, VoiceRoomMessageEventContentSerializer,
            ),
            MessageEventContentSerializerMapping(
                "m.room.message", VoiceAudioContent::class, VoiceRoomMessageEventContentSerializer,
            ),
        )
    }
