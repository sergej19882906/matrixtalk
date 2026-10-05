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
    ) : CallSignalingCommand

    data class Answer(val callId: String, val answerSdp: String) : CallSignalingCommand
    data class Candidates(val callId: String, val candidates: List<CallCandidate>) : CallSignalingCommand
    data class Hangup(val callId: String) : CallSignalingCommand
    data class Reject(val callId: String) : CallSignalingCommand
}

fun parseCallEvent(eventType: String, raw: JsonObject, roomId: String, fromUserId: String): CallSignalingEvent? {
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

fun buildCallContent(command: CallSignalingCommand): UnknownEventContent = when (command) {
    is CallSignalingCommand.Invite -> UnknownEventContent(
        eventType = "m.call.invite",
        raw = buildJsonObject {
            put("version", CALL_VERSION)
            put("call_id", command.callId)
            put("lifetime", CALL_LIFETIME_MS)
            putJsonObject("offer") {
                put("type", "offer")
                put("sdp", command.offerSdp)
            }
        },
    )

    is CallSignalingCommand.Answer -> UnknownEventContent(
        eventType = "m.call.answer",
        raw = buildJsonObject {
            put("version", CALL_VERSION)
            put("call_id", command.callId)
            putJsonObject("answer") {
                put("type", "answer")
                put("sdp", command.answerSdp)
            }
        },
    )

    is CallSignalingCommand.Candidates -> UnknownEventContent(
        eventType = "m.call.candidates",
        raw = buildJsonObject {
            put("version", CALL_VERSION)
            put("call_id", command.callId)
            putJsonArray("candidates") {
                command.candidates.forEach { candidate ->
                    add(buildJsonObject {
                        put("candidate", candidate.candidate)
                        candidate.sdpMid?.let { put("sdpMid", it) }
                        candidate.sdpMLineIndex?.let { put("sdpMLineIndex", it) }
                    })
                }
            }
        },
    )

    is CallSignalingCommand.Hangup -> UnknownEventContent(
        eventType = "m.call.hangup",
        raw = buildJsonObject {
            put("version", CALL_VERSION)
            put("call_id", command.callId)
        },
    )

    is CallSignalingCommand.Reject -> UnknownEventContent(
        eventType = "m.call.reject",
        raw = buildJsonObject {
            put("version", CALL_VERSION)
            put("call_id", command.callId)
        },
    )
}
