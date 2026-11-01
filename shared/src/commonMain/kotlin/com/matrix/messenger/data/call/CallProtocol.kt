package com.matrix.messenger.data.call

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import net.folivo.trixnity.core.model.events.MessageEventContent
import net.folivo.trixnity.core.model.events.UnknownEventContent

/** Matrix VoIP v0 protocol: https://spec.matrix.org/v1.11/client-server-api/#voice-over-ip */

const val CALL_VERSION = "0"
const val CALL_LIFETIME_MS = 60_000

data class CallCandidate(
    val candidate: String,
    val sdpMid: String? = null,
    val sdpMLineIndex: Int? = null,
)

sealed interface CallSignalingEvent {
    val roomId: String
    val callId: String

    data class Incoming(
        override val roomId: String,
        override val callId: String,
        val fromUserId: String,
        val offerSdp: String,
        val isVideo: Boolean,
    ) : CallSignalingEvent

    data class Answer(
        override val roomId: String,
        override val callId: String,
        val answerSdp: String,
    ) : CallSignalingEvent

    data class Candidates(
        override val roomId: String,
        override val callId: String,
        val candidates: List<CallCandidate>,
    ) : CallSignalingEvent

    data class Hangup(
        override val roomId: String,
        override val callId: String,
    ) : CallSignalingEvent

    data class Reject(
        override val roomId: String,
        override val callId: String,
    ) : CallSignalingEvent
}

sealed interface CallSignalingCommand {
    data class Invite(
        val callId: String,
        val offerSdp: String,
        val isVideo: Boolean,
        val invitee: String? = null,
    ) : CallSignalingCommand

    data class Answer(val callId: String, val answerSdp: String) : CallSignalingCommand
    data class Candidates(val callId: String, val candidates: List<CallCandidate>) : CallSignalingCommand
    data class Hangup(val callId: String) : CallSignalingCommand
    data class Reject(val callId: String) : CallSignalingCommand
}

/**
 * @param ourPartyId our device id; events carrying a `dest_party_id` for another
 * device (VoIP v1) are ignored.
 */
fun parseCallEvent(
    eventType: String,
    raw: JsonObject,
    roomId: String,
    fromUserId: String,
    ourPartyId: String? = null,
): CallSignalingEvent? {
    val destPartyId = raw["dest_party_id"]?.jsonPrimitive?.content
    if (destPartyId != null && ourPartyId != null && destPartyId != ourPartyId) return null
    val callId = raw["call_id"]?.jsonPrimitive?.content ?: return null
    return when (eventType) {
        "m.call.invite" -> {
            val offerSdp = raw["offer"]?.jsonObject?.get("sdp")?.jsonPrimitive?.content ?: return null
            CallSignalingEvent.Incoming(
                roomId = roomId,
                callId = callId,
                fromUserId = fromUserId,
                offerSdp = offerSdp,
                isVideo = offerSdp.lines().any { it.startsWith("m=video") },
            )
        }

        "m.call.answer" -> {
            val answerSdp = raw["answer"]?.jsonObject?.get("sdp")?.jsonPrimitive?.content ?: return null
            CallSignalingEvent.Answer(roomId, callId, answerSdp)
        }

        "m.call.candidates" -> {
            val candidates = raw["candidates"]?.jsonArray?.mapNotNull { element ->
                val obj = element as? JsonObject ?: return@mapNotNull null
                val candidate = obj["candidate"]?.jsonPrimitive?.content ?: return@mapNotNull null
                CallCandidate(
                    candidate = candidate,
                    sdpMid = obj["sdpMid"]?.jsonPrimitive?.content,
                    sdpMLineIndex = obj["sdpMLineIndex"]?.jsonPrimitive?.intOrNull,
                )
            } ?: return null
            CallSignalingEvent.Candidates(roomId, callId, candidates)
        }

        "m.call.hangup" -> CallSignalingEvent.Hangup(roomId, callId)
        "m.call.reject" -> CallSignalingEvent.Reject(roomId, callId)
        else -> null
    }
}

fun buildCallContent(command: CallSignalingCommand, partyId: String? = null): MessageEventContent = when (command) {
    is CallSignalingCommand.Invite -> CallInviteEventContent(
        callId = command.callId,
        offer = CallSessionDescription(type = "offer", sdp = command.offerSdp),
        lifetime = CALL_LIFETIME_MS.toLong(),
        partyId = partyId,
        invitee = command.invitee,
    )

    is CallSignalingCommand.Answer -> CallAnswerEventContent(
        callId = command.callId,
        answer = CallSessionDescription(type = "answer", sdp = command.answerSdp),
        partyId = partyId,
    )

    is CallSignalingCommand.Candidates -> CallCandidatesEventContent(
        callId = command.callId,
        candidates = command.candidates.map {
            CallCandidatePayload(candidate = it.candidate, sdpMid = it.sdpMid, sdpMLineIndex = it.sdpMLineIndex)
        },
        partyId = partyId,
    )

    is CallSignalingCommand.Hangup -> CallHangupEventContent(callId = command.callId, partyId = partyId)

    is CallSignalingCommand.Reject -> CallRejectEventContent(callId = command.callId, partyId = partyId)
}

/**
 * Typed parse for events decoded through the serializer mappings (new events
 * and all events after the call mappings registration).
 */
fun parseCallEvent(
    content: MessageEventContent,
    roomId: String,
    fromUserId: String,
    ourPartyId: String? = null,
): CallSignalingEvent? {
    if (content !is CallInviteEventContent &&
        content !is CallAnswerEventContent &&
        content !is CallCandidatesEventContent &&
        content !is CallHangupEventContent &&
        content !is CallRejectEventContent
    ) return null
    val callId = when (content) {
        is CallInviteEventContent -> content.callId
        is CallAnswerEventContent -> content.callId
        is CallCandidatesEventContent -> content.callId
        is CallHangupEventContent -> content.callId
        is CallRejectEventContent -> content.callId
        else -> return null
    }
    val isVideo = (content as? CallInviteEventContent)
        ?.offer?.sdp?.lines()?.any { it.startsWith("m=video") } == true
    return when (content) {
        is CallInviteEventContent -> CallSignalingEvent.Incoming(
            roomId = roomId,
            callId = callId,
            fromUserId = fromUserId,
            offerSdp = content.offer.sdp,
            isVideo = isVideo,
        )

        is CallAnswerEventContent -> CallSignalingEvent.Answer(roomId, callId, content.answer.sdp)

        is CallCandidatesEventContent -> CallSignalingEvent.Candidates(
            roomId = roomId,
            callId = callId,
            candidates = content.candidates.map {
                CallCandidate(candidate = it.candidate, sdpMid = it.sdpMid, sdpMLineIndex = it.sdpMLineIndex)
            },
        )

        is CallHangupEventContent -> CallSignalingEvent.Hangup(roomId, callId)
        is CallRejectEventContent -> CallSignalingEvent.Reject(roomId, callId)
        else -> null
    }
}
