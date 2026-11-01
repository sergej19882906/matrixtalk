package com.matrix.messenger.data.repository

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.events.MessageEventContent
import net.folivo.trixnity.core.model.events.UnknownEventContent
import net.folivo.trixnity.core.model.events.m.ReactionEventContent
import net.folivo.trixnity.core.model.events.m.RelatesTo
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent
import net.folivo.trixnity.core.serialization.events.DefaultEventContentSerializerMappings
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Realm outbox repository (trixnity-client-repository-realm) resolves the
 * serializer for an outgoing message via
 * `mappings.message.find { it.kClass.isInstance(content) }` and hard-fails with
 * `IllegalArgumentException: Required value was null.` when no mapping exists
 * (it does NOT use the polymorphic UnknownEventContent fallback of the core
 * serializer). These tests document which content types our send paths put
 * into the outbox and whether they survive that lookup.
 */
class OutboxMappingTest {

    private fun mapped(content: MessageEventContent) =
        DefaultEventContentSerializerMappings.message.find { it.kClass.isInstance(content) } != null

    @Test
    fun `text message is mapped`() {
        assertTrue(mapped(RoomMessageEventContent.TextBased.Text(body = "hello")))
    }

    @Test
    fun `file message is mapped`() {
        assertTrue(
            mapped(
                RoomMessageEventContent.FileBased.File(
                    body = "report.pdf",
                    url = "mxc://server/test"
                )
            )
        )
    }

    @Test
    fun `image message is mapped`() {
        assertTrue(
            mapped(
                RoomMessageEventContent.FileBased.Image(
                    body = "photo.png",
                    url = "mxc://server/test"
                )
            )
        )
    }

    @Test
    fun `reaction is mapped`() {
        val reaction = ReactionEventContent(
            relatesTo = RelatesTo.Annotation(
                EventId("\$evt"),
                key = "👍"
            )
        )
        assertTrue(mapped(reaction))
    }

    @Test
    fun `unknown event content has NO mapping - outbox save crashes`() {
        // m.call.* signaling and m.poll.* are sent as UnknownEventContent
        val call = UnknownEventContent(
            buildJsonObject {
                put("call_id", "c1")
                put("version", "0")
            },
            "m.call.invite"
        )
        assertNull(
            DefaultEventContentSerializerMappings.message.find { it.kClass.isInstance(call) },
            "UnknownEventContent must be unresolvable via kClass lookup - this is the outbox crash"
        )
        val poll = UnknownEventContent(
            buildJsonObject {
                put("m.relates_to", buildJsonObject { put("event_id", "\$poll") })
            },
            "m.poll.response"
        )
        assertNull(
            DefaultEventContentSerializerMappings.message.find { it.kClass.isInstance(poll) }
        )
    }

    @Test
    fun `edit replacement is mapped`() {
        val edit = RoomMessageEventContent.TextBased.Text(
            body = "* new",
            relatesTo = RelatesTo.Replace(EventId("\$evt"))
        )
        assertTrue(mapped(edit))
    }
}
