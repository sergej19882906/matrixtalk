package com.matrix.messenger.data.poll

import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PollProtocolTest {

    @Test
    fun parsesUnstablePollStart() {
        val content = buildPollStartContent("Лучший цвет?", listOf("Красный", "Синий"))
        assertEquals(POLL_START_UNSTABLE, content.eventType)
        val poll = parsePollStart(content.raw)
        assertNotNull(poll)
        assertEquals("Лучший цвет?", poll.question)
        assertEquals(2, poll.answers.size)
        assertEquals("Красный", poll.answers[0].text)
        assertTrue(poll.disclosed)
        assertEquals(1, poll.maxSelections)
    }

    @Test
    fun parsesStableNamespacedPollStart() {
        val raw = kotlinx.serialization.json.buildJsonObject {
            put("m.poll.start", buildPollStartContent("Q?", listOf("A", "B")).raw[POLL_START_UNSTABLE]!!)
        }
        val poll = parsePollStart(raw)
        assertNotNull(poll)
        assertEquals("Q?", poll.question)
    }

    @Test
    fun parsesPollResponse() {
        val content = buildPollResponseContent("\$poll123", listOf("a1", "a2"))
        assertEquals(POLL_RESPONSE_UNSTABLE, content.eventType)
        val response = parsePollResponse(content.raw)
        assertNotNull(response)
        assertEquals("\$poll123", response.pollEventId)
        assertEquals(listOf("a1", "a2"), response.answers)
    }

    @Test
    fun ignoresNonPollContent() {
        assertNull(parsePollStart(kotlinx.serialization.json.buildJsonObject { put("body", kotlinx.serialization.json.JsonPrimitive("hi")) }))
        assertNull(parsePollResponse(kotlinx.serialization.json.buildJsonObject { put("body", kotlinx.serialization.json.JsonPrimitive("hi")) }))
    }

    @Test
    fun fallbackBodyPresentForLegacyClients() {
        val content = buildPollStartContent("Вопрос", listOf("Один", "Два"))
        val body = content.raw["body"]?.jsonPrimitive?.content
        assertNotNull(body)
        assertTrue(body!!.startsWith("[FALLBACK]"))
        assertTrue(body.contains("1. Один"))
    }
}
