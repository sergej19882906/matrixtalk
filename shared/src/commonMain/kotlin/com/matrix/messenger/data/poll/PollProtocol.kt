package com.matrix.messenger.data.poll

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import net.folivo.trixnity.core.model.events.UnknownEventContent

/** MSC3381 poll support (manual parsing — no models in Trixnity 4.9.2). */

const val POLL_START_UNSTABLE = "org.matrix.msc3381.poll.start"
const val POLL_RESPONSE_UNSTABLE = "org.matrix.msc3381.poll.response"
const val POLL_START_STABLE = "m.poll.start"
const val POLL_RESPONSE_STABLE = "m.poll.response"
const val POLL_END_STABLE = "m.poll.end"
const val POLL_END_UNSTABLE = "org.matrix.msc3381.poll.end"

data class PollAnswer(val id: String, val text: String)

data class PollInfo(
    val question: String,
    val answers: List<PollAnswer>,
    val disclosed: Boolean,
    val maxSelections: Int
)

/** A vote: [answers] chosen by a user for poll [pollEventId]. Empty answers withdraw a vote. */
data class PollResponse(val pollEventId: String, val answers: List<String>)

data class PollEnd(val pollEventId: String)

private fun JsonObject.string(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

private fun JsonObject.textValue(): String? = string("org.matrix.msc3381.text")
    ?: string("m.text") ?: string("text")

/** Parses stable and unstable poll starts, safely ignoring malformed JSON shapes. */
fun parsePollStart(raw: JsonObject): PollInfo? {
    val start = raw[POLL_START_STABLE] as? JsonObject
        ?: raw[POLL_START_UNSTABLE] as? JsonObject
        ?: return null
    val question = (start["question"] as? JsonObject)?.textValue() ?: return null
    val answers = (start["answers"] as? JsonArray)?.mapNotNull { element ->
        val obj = element as? JsonObject ?: return@mapNotNull null
        val id = obj.string("id")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val text = obj.textValue() ?: return@mapNotNull null
        PollAnswer(id, text)
    }.orEmpty()
    if (answers.isEmpty() || answers.map { it.id }.distinct().size != answers.size) return null
    val kind = start.string("kind")
    val maxSelections = (start["max_selections"] as? JsonPrimitive)?.intOrNull ?: 1
    return PollInfo(
        question = question,
        answers = answers,
        disclosed = kind == "m.disclosed" || kind == "org.matrix.msc3381.disclosed",
        maxSelections = maxSelections.coerceIn(1, answers.size),
    )
}

private fun JsonObject.referenceTarget(): String? {
    val relation = this["m.relates_to"] as? JsonObject
        ?: this["relates_to"] as? JsonObject
        ?: return null
    if (relation.string("rel_type") != "m.reference") return null
    return relation.string("event_id")?.takeIf { it.isNotBlank() }
}

/** Parses a response including empty answers (withdrawal); malformed entries never throw. */
fun parsePollResponse(raw: JsonObject): PollResponse? {
    val response = raw[POLL_RESPONSE_STABLE] as? JsonObject
        ?: raw[POLL_RESPONSE_UNSTABLE] as? JsonObject
        ?: return null
    val elements = response["answers"] as? JsonArray ?: return null
    val answers = elements.map { element ->
        when (element) {
            is JsonObject -> element.string("id")
            is JsonPrimitive -> element.takeIf { it.isString }?.contentOrNull
            else -> null
        } ?: return null
    }
    val target = raw.referenceTarget() ?: return null
    return PollResponse(pollEventId = target, answers = answers)
}

/** Only actual end payloads with a reference relation can close a poll. */
fun parsePollEnd(raw: JsonObject): PollEnd? {
    if (raw[POLL_END_STABLE] !is JsonObject && raw[POLL_END_UNSTABLE] !is JsonObject) return null
    return PollEnd(raw.referenceTarget() ?: return null)
}

fun buildPollStartContent(
    question: String,
    answers: List<String>,
    disclosed: Boolean = true,
    maxSelections: Int = 1
): UnknownEventContent {
    val ids = answers.map { kotlin.random.Random.nextLong().toString(36).take(8) }
    val fallbackBody = buildString {
        append("[FALLBACK] ").append(question)
        answers.forEachIndexed { index, text -> append("\n").append(index + 1).append(". ").append(text) }
    }
    return UnknownEventContent(
        eventType = POLL_START_UNSTABLE,
        raw = buildJsonObject {
            putJsonObject(POLL_START_UNSTABLE) {
                putJsonObject("question") {
                    put("org.matrix.msc3381.text", question)
                    put("text", question)
                }
                put("kind", if (disclosed) "org.matrix.msc3381.disclosed" else "org.matrix.msc3381.undisclosed")
                put("max_selections", maxSelections.coerceAtLeast(1))
                putJsonArray("answers") {
                    answers.forEachIndexed { index, text ->
                        add(buildJsonObject {
                            put("id", ids[index])
                            put("org.matrix.msc3381.text", text)
                            put("text", text)
                        })
                    }
                }
            }
            put("msgtype", "m.text")
            put("body", fallbackBody)
        }
    )
}

fun buildPollResponseContent(pollEventId: String, answerIds: List<String>): UnknownEventContent =
    UnknownEventContent(
        eventType = POLL_RESPONSE_UNSTABLE,
        raw = buildJsonObject {
            putJsonObject(POLL_RESPONSE_UNSTABLE) {
                putJsonArray("answers") {
                    answerIds.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) }
                }
            }
            putJsonObject("m.relates_to") {
                put("rel_type", "m.reference")
                put("event_id", pollEventId)
            }
            put("msgtype", "m.text")
            put("body", "[FALLBACK] Проголосовал в опросе")
        }
    )

/** Builds a stable or unstable poll end with its required nested object and reference. */
fun buildPollEndContent(pollEventId: String, unstable: Boolean = false): UnknownEventContent {
    val type = if (unstable) POLL_END_UNSTABLE else POLL_END_STABLE
    val raw = buildJsonObject {
        putJsonObject(type) {}
        putJsonObject("m.relates_to") {
            put("rel_type", "m.reference")
            put("event_id", pollEventId)
        }
        put("m.text", "Опрос завершён")
        put("org.matrix.msc1767.text", "Опрос завершён")
        put("body", "[FALLBACK] Опрос завершён")
    }
    return UnknownEventContent(raw, type)
}
