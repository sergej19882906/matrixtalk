package com.matrix.messenger.data.repository

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import net.folivo.trixnity.core.model.events.RoomEventContent
import net.folivo.trixnity.core.model.events.UnknownEventContent
import net.folivo.trixnity.core.model.events.m.room.EncryptedFile
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent
import com.matrix.messenger.data.sticker.StickerEventContent
import com.matrix.messenger.data.voice.VoiceAudioContent

internal data class MediaReference(val url: String?, val file: EncryptedFile?, val mimeType: String?)

internal fun RoomEventContent.mediaReference(): MediaReference? {
    when (this) {
        is RoomMessageEventContent.FileBased ->
            return MediaReference(if (file == null) url else null, file, info?.mimeType)
        is VoiceAudioContent -> stockAudio.let {
            return MediaReference(if (it.file == null) it.url else null, it.file, it.info?.mimeType)
        }
        is StickerEventContent ->
            return MediaReference(if (file == null) url else null, file, info?.mimeType)
        else -> {}
    }
    if (this !is UnknownEventContent || eventType !in setOf("m.audio", "m.sticker", "m.room.message")) return null
    val file = (raw["file"] as? JsonObject)?.let { Json.decodeFromJsonElement(EncryptedFile.serializer(), it) }
    val url = if (file == null) (raw["url"] as? JsonPrimitive)?.content else null
    val mime = ((raw["info"] as? JsonObject)?.get("mimetype") as? JsonPrimitive)?.content
    return MediaReference(url, file, mime)
}

internal fun mediaSuffix(mime: String?): String? = when (mime?.substringBefore(';')) {
    "audio/ogg", "audio/opus" -> "ogg"
    "audio/wav", "audio/x-wav" -> "wav"
    "audio/mp4", "audio/aac" -> "m4a"
    "video/mp4" -> "mp4"
    "image/png" -> "png"
    "image/jpeg" -> "jpg"
    "image/webp" -> "webp"
    else -> null
}
