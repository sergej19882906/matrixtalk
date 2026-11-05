package com.matrix.messenger.data.push

import com.matrix.messenger.data.model.RoomNotificationLevel
import io.ktor.http.Url
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.folivo.trixnity.clientserverapi.model.push.PusherData
import net.folivo.trixnity.clientserverapi.model.push.SetPushers

/** Only decrypted, server-notifiable room messages belong in this projection. */
data class BackgroundNotification(
    val roomId: String,
    val eventId: String,
    val senderId: String,
    val title: String,
    val body: String,
    val isRead: Boolean,
    val isHighlight: Boolean,
)

data class PushTarget(val roomId: String, val eventId: String)

@Serializable
enum class PushRegistrationStatus { UNAVAILABLE, DISABLED, PENDING, FAILED, ACTIVE }

data class PushRegistrationSnapshot(
    val status: PushRegistrationStatus = PushRegistrationStatus.UNAVAILABLE,
    val generation: Long = 0,
    /** Safe diagnostic code, never an endpoint, token, or exception message. */
    val reason: String? = null,
)

const val MATRIX_PUSH_APP_ID = "com.matrix.messenger"
const val MATRIX_PUSH_INSTANCE = "matrixtalk"
const val NTFY_DISTRIBUTOR = "io.heckel.ntfy"

/** No generic UnifiedPush gateway promise: this implementation only supports configured ntfy. */
fun ntfyGateway(endpoint: String, configuredBaseUrl: String): String? = runCatching {
    val base = Url(configuredBaseUrl)
    val target = Url(endpoint)
    require(base.protocol.name == "https" && target.protocol.name == "https")
    require(base.user.isNullOrEmpty() && base.password.isNullOrEmpty() && target.user.isNullOrEmpty() && target.password.isNullOrEmpty())
    require(base.encodedPath in listOf("", "/") && base.parameters.isEmpty() && base.fragment.isEmpty())
    require(base.host.isNotBlank() && base.host == target.host && base.port == target.port)
    require(target.fragment.isEmpty())
    require(Regex("/up[A-Za-z0-9_-]{8,200}").matches(target.encodedPath))
    require(target.parameters.names() == setOf("up") && target.parameters.getAll("up") == listOf("1"))
    base.toString().trimEnd('/') + "/_matrix/push/v1/notify"
}.getOrNull()

/** Matrix request field names are intentional; positional arguments caused a wrong-kind pusher. */
fun httpPusherRequest(
    pushKey: String,
    gatewayUrl: String,
    appId: String,
    deviceDisplayName: String,
    remove: Boolean = false,
): SetPushers.Request = SetPushers.Request(
    appDisplayName = "MatrixTalk",
    appId = appId,
    append = false,
    data = PusherData(url = gatewayUrl, format = "event_id_only"),
    deviceDisplayName = deviceDisplayName,
    kind = if (remove) null else "http",
    lang = "en",
    profileTag = "matrixtalk",
    pushkey = pushKey,
)

/** The body is a wake-up hint, never notification text. Malformed/oversized hints are ignored. */
fun parseMatrixPush(message: ByteArray, endpoint: String): PushTarget? = runCatching {
    require(message.size in 1..8192)
    val notification = Json.parseToJsonElement(message.decodeToString(throwOnInvalidSequence = true))
        .jsonObject.getValue("notification").jsonObject
    val devices = notification.getValue("devices").jsonArray
    require(devices.size in 1..16)
    require(devices.any {
        val device = it.jsonObject
        device["app_id"]?.jsonPrimitive?.content == MATRIX_PUSH_APP_ID &&
            device["pushkey"]?.jsonPrimitive?.content == endpoint
    })
    val roomId = notification.getValue("room_id").jsonPrimitive.content
    val eventId = notification.getValue("event_id").jsonPrimitive.content
    require(roomId.length in 2..512 && roomId.startsWith("!") && roomId.contains(':'))
    require(eventId.length in 2..512 && eventId.startsWith("$"))
    require((roomId + eventId).none { it.isWhitespace() || it.code < 32 })
    PushTarget(roomId, eventId)
}.getOrNull()

fun shouldNotify(
    event: BackgroundNotification,
    userId: String,
    enabled: Boolean,
    level: RoomNotificationLevel,
    muted: Boolean,
    target: PushTarget? = null,
): Boolean = enabled && !muted && !event.isRead && event.senderId != userId &&
    event.body.isNotBlank() &&
    (target == null || (target.roomId == event.roomId && target.eventId == event.eventId)) &&
    when (level) {
        RoomNotificationLevel.MUTE -> false
        RoomNotificationLevel.MENTIONS_ONLY -> event.isHighlight
        RoomNotificationLevel.ALL_MESSAGES -> true
    }
