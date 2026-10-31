package com.matrix.messenger.data.poll

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import net.folivo.trixnity.core.model.events.UnknownEventContent

/** MSC3381 poll support (manual parsing — no models in Trixnity 4.9.2). */

const val POLL_START_UNSTABLE = "org.matrix.msc3381.poll.start"
const val POLL_RESPONSE_UNSTABLE = "org.matrix.msc3381.poll.response"
const val POLL_START_STABLE = "m.poll.start"
const val POLL_RESPONSE_STABLE = "m.poll.response"

data class PollAnswer(val id: String, val text: String)

data class PollInfo(
    val question: String,
    val answers: List<PollAnswer>,
    val disclosed: Boolean,
    val maxSelections: Int
)

/** A vote: [answers] chosen by a user for poll [pollEventId]. */
data class PollResponse(val pollEventId: String, val answers: List<String>)

private fun JsonObject.textValue(): String? =
    (this["org.matrix.msc3381.text"] ?: this["text"])?.jsonPrimitive?.content

/** Parses a poll start payload from an m.poll.start event content. */
fun parsePollStart(raw: JsonObject): PollInfo? {
    val start = raw[POLL_START_STABLE]?.jsonObject
        ?: raw[POLL_START_UNSTABLE]?.jsonObject
        ?: return null
    val question = (start["question"]?.jsonObject)?.textValue() ?: return null
    val answers = (start["answers"]?.jsonArray)?.mapNotNull { element ->
        val obj = element as? JsonObject ?: return@mapNotNull null
        val id = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
        val text = obj.textValue() ?: return@mapNotNull null
        PollAnswer(id, text)
    }.orEmpty()
    if (answers.isEmpty()) return null
    val kind = (start["kind"]?.jsonPrimitive?.content) ?: ""
    val maxSelections = start["max_selections"]?.jsonPrimitive?.content?.toIntOrNull() ?: 1
    return PollInfo(
        question = question,
        answers = answers,
        disclosed = kind.contains("disclosed"),
        maxSelections = maxSelections.coerceAtLeast(1)
    )
}

/** Parses a poll response (vote) from an m.poll.response event content. */
fun parsePollResponse(raw: JsonObject): PollResponse? {
    val response = raw[POLL_RESPONSE_STABLE]?.jsonObject
        ?: raw[POLL_RESPONSE_UNSTABLE]?.jsonObject
        ?: return null
    val answers = (response["answers"]?.jsonArray)?.mapNotNull {
        (it as? JsonObject)?.get("id")?.jsonPrimitive?.content ?: it.jsonPrimitive.content
    }.orEmpty()
    if (answers.isEmpty()) return null
    val relatesTo = (raw["m.relates_to"] ?: raw["relates_to"])?.jsonObject ?: return null
    val target = relatesTo["event_id"]?.jsonPrimitive?.content ?: return null
    return PollResponse(pollEventId = target, answers = answers)
}

fun buildPollStartContent(
    question: String,
    answers: List<String>,
    disclosed: Boolean = true
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
                put("max_selections", 1)
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
