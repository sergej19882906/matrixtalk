package com.matrix.messenger.data.push

import com.matrix.messenger.data.model.RoomNotificationLevel
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.folivo.trixnity.clientserverapi.model.push.SetPushers

class PushProtocolTest {
    private val base = "https://ntfy.example.test"
    private val endpoint = "$base/upmockdevice123?up=1"

    @Test fun configuredNtfyOnly() {
        assertEquals("$base/_matrix/push/v1/notify", ntfyGateway(endpoint, base))
        assertNull(ntfyGateway(endpoint, ""))
        assertNull(ntfyGateway(endpoint, "https://another.example.test"))
        assertNull(ntfyGateway("http://ntfy.example.test/upmockdevice123?up=1", base))
        assertNull(ntfyGateway("$base/arbitrarytopic", base))
        assertNull(ntfyGateway("$endpoint&up=1", base))
        assertNull(ntfyGateway("$endpoint#fragment", base))
        assertNull(ntfyGateway("https://user:pass@ntfy.example.test/upmockdevice123?up=1", base))
        assertNull(ntfyGateway(endpoint, "$base/subpath"))
    }

    @Test fun requestHasHttpKindGatewayAndEventIdOnly() {
        val request = httpPusherRequest(endpoint, "$base/_matrix/push/v1/notify", MATRIX_PUSH_APP_ID, "Mock Android")
        assertEquals("http", request.kind)
        assertEquals(endpoint, request.pushkey)
        assertEquals("$base/_matrix/push/v1/notify", request.data.url)
        assertEquals("event_id_only", request.data.format)
        assertEquals(false, request.append)
        val roundTrip = Json.decodeFromString(SetPushers.Request.serializer(),
            Json.encodeToString(SetPushers.Request.serializer(), request))
        assertEquals(request, roundTrip)
        assertNull(httpPusherRequest(endpoint, "$base/_matrix/push/v1/notify", MATRIX_PUSH_APP_ID, "Mock", remove = true).kind)
    }

    private fun payload(pushkey: String = endpoint): ByteArray = """
        {"notification":{"room_id":"!room:example.test","event_id":"${'$'}event",
        "devices":[{"app_id":"com.matrix.messenger","pushkey":"$pushkey"}],
        "content":{"body":"untrusted text"}}}
    """.trimIndent().encodeToByteArray()

    @Test fun parsesTargetButDoesNotTrustBody() {
        assertEquals(PushTarget("!room:example.test", "${'$'}event"), parseMatrixPush(payload(), endpoint))
        assertNull(parseMatrixPush(payload("$base/upotherdevice123?up=1"), endpoint))
        assertNull(parseMatrixPush("{}".encodeToByteArray(), endpoint))
        assertNull(parseMatrixPush(ByteArray(8193), endpoint))
        assertNull(parseMatrixPush(byteArrayOf(0xff.toByte()), endpoint))
    }

    @Test fun togglesReadOwnMutedMentionAndTargetAreEnforced() {
        val event = BackgroundNotification("!room:example.test", "${'$'}event", "@other:example.test", "Room", "Decrypted", false, false)
        fun allowed(candidate: BackgroundNotification = event, enabled: Boolean = true,
                    level: RoomNotificationLevel = RoomNotificationLevel.ALL_MESSAGES,
                    muted: Boolean = false, target: PushTarget? = null) =
            shouldNotify(candidate, "@me:example.test", enabled, level, muted, target)
        assertTrue(allowed())
        assertFalse(allowed(enabled = false))
        assertFalse(allowed(muted = true))
        assertFalse(allowed(level = RoomNotificationLevel.MUTE))
        assertFalse(allowed(level = RoomNotificationLevel.MENTIONS_ONLY))
        assertTrue(allowed(event.copy(isHighlight = true), level = RoomNotificationLevel.MENTIONS_ONLY))
        assertFalse(allowed(event.copy(isRead = true)))
        assertFalse(allowed(event.copy(senderId = "@me:example.test")))
        assertFalse(allowed(event.copy(body = "")))
        assertFalse(allowed(target = PushTarget("!other:example.test", event.eventId)))
        assertFalse(allowed(target = PushTarget(event.roomId, "${'$'}different")))
        assertTrue(allowed(target = PushTarget(event.roomId, event.eventId)))
    }
}
