package com.matrix.messenger.data.call

import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CallProtocolTest {

    private val fakeSdp = "v=0\r\no=- 1 1 IN IP4 127.0.0.1\r\nm=audio 9 UDP/TLS/RTP/SAVPF 111\r\n"

    @Test
    fun inviteBuildsCorrectEvent() {
        val content = buildCallContent(
            CallSignalingCommand.Invite("call1", fakeSdp, isVideo = false, invitee = "@peer:server"),
            partyId = "DEVICE1"
        )
        assertEquals("m.call.invite", content.eventType)
        val raw = content.raw
        assertEquals("0", raw["version"]?.jsonPrimitive?.content)
        assertEquals("call1", raw["call_id"]?.jsonPrimitive?.content)
        assertEquals("DEVICE1", raw["party_id"]?.jsonPrimitive?.content)
        assertEquals("@peer:server", raw["invitee"]?.jsonPrimitive?.content)
        assertEquals(fakeSdp, raw["offer"]?.jsonObject?.get("sdp")?.jsonPrimitive?.content)
    }

    @Test
    fun inviteWithoutOptionalFieldsOmitsThem() {
        val raw = buildCallContent(CallSignalingCommand.Invite("c", fakeSdp, isVideo = false)).raw
        assertNull(raw["party_id"])
        assertNull(raw["invitee"])
    }

    @Test
    fun answerBuildsCorrectEvent() {
        val content = buildCallContent(CallSignalingCommand.Answer("call1", fakeSdp), partyId = "D")
        assertEquals("m.call.answer", content.eventType)
        assertEquals(fakeSdp, content.raw["answer"]?.jsonObject?.get("sdp")?.jsonPrimitive?.content)
    }

    @Test
    fun candidatesBuildsCorrectEvent() {
        val content = buildCallContent(
            CallSignalingCommand.Candidates(
                "call1",
                listOf(CallCandidate("candidate:1 1 udp 1 1.2.3.4 5000 typ host", "0", 0))
            )
        )
        assertEquals("m.call.candidates", content.eventType)
    }

    @Test
    fun hangupAndRejectBuildCorrectEvents() {
        assertEquals("m.call.hangup", buildCallContent(CallSignalingCommand.Hangup("c1")).eventType)
        assertEquals("m.call.reject", buildCallContent(CallSignalingCommand.Reject("c1")).eventType)
    }

    @Test
    fun parsesIncomingInvite() {
        val content = buildCallContent(
            CallSignalingCommand.Invite("call9", fakeSdp, isVideo = true),
            partyId = "OTHER"
        )
        val event = parseCallEvent(content.eventType, content.raw, "!room:server", "@caller:server")
        val incoming = event as? CallSignalingEvent.Incoming
        assertEquals("call9", incoming?.callId)
        assertEquals("@caller:server", incoming?.fromUserId)
        assertEquals("!room:server", incoming?.roomId)
        // m=audio only in fake sdp -> parsed as audio
        assertEquals(false, incoming?.isVideo)
    }

    @Test
    fun parsesVideoInvite() {
        val videoSdp = "m=video 9 UDP/TLS/RTP/SAVPF 96\r\n"
        val content = buildCallContent(CallSignalingCommand.Invite("c", videoSdp, isVideo = true))
        val incoming = parseCallEvent(content.eventType, content.raw, "!r:s", "@u:s") as CallSignalingEvent.Incoming
        assertEquals(true, incoming.isVideo)
    }

    @Test
    fun parsesAnswerCandidatesHangupReject() {
        val answer = buildCallContent(CallSignalingCommand.Answer("c", fakeSdp), partyId = "P")
        assertEquals("c", (parseCallEvent(answer.eventType, answer.raw, "!r:s", "@u:s") as CallSignalingEvent.Answer).callId)

        val candidates = buildCallContent(
            CallSignalingCommand.Candidates("c", listOf(CallCandidate("candidate:x", "1", 1)))
        )
        val parsed = parseCallEvent(candidates.eventType, candidates.raw, "!r:s", "@u:s") as CallSignalingEvent.Candidates
        assertEquals(1, parsed.candidates.size)
        assertEquals("candidate:x", parsed.candidates[0].candidate)

        assertEquals("c", (parseCallEvent("m.call.hangup", buildJson { put("call_id", "c") }, "!r:s", "@u:s") as CallSignalingEvent.Hangup).callId)
        assertEquals("c", (parseCallEvent("m.call.reject", buildJson { put("call_id", "c") }, "!r:s", "@u:s") as CallSignalingEvent.Reject).callId)
    }

    @Test
    fun ignoresCandidatesForOtherParty() {
        val foreign = buildJson {
            put("call_id", "c")
            put("dest_party_id", "OTHER_DEVICE")
        }
        assertNull(parseCallEvent("m.call.hangup", foreign, "!r:s", "@u:s", ourPartyId = "MY_DEVICE"))
    }

    @Test
    fun acceptsCandidatesForOwnParty() {
        val own = buildJson {
            put("call_id", "c")
            put("dest_party_id", "MY_DEVICE")
        }
        assertEquals("c", (parseCallEvent("m.call.hangup", own, "!r:s", "@u:s", ourPartyId = "MY_DEVICE") as CallSignalingEvent.Hangup).callId)
    }

    @Test
    fun ignoresUnknownEventTypes() {
        assertNull(parseCallEvent("m.room.message", buildJson { put("call_id", "c") }, "!r:s", "@u:s"))
    }

    private fun buildJson(builder: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit) =
        kotlinx.serialization.json.buildJsonObject(builder)
}
