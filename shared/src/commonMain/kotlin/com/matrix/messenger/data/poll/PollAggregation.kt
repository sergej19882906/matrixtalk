package com.matrix.messenger.data.poll

/** Parsed poll events, independent of timeline/storage/UI models. */
data class PollResponseEvent(
    val eventId: String,
    val sender: String,
    val timestamp: Long,
    val response: PollResponse,
)

data class PollEndEvent(
    val eventId: String,
    val sender: String,
    val timestamp: Long,
    val end: PollEnd,
)

data class PollAggregation(
    val votes: Map<String, Int>,
    val myVotes: Set<String>,
    val ended: Boolean,
    val endTimestamp: Long?,
    val totalVoters: Int,
)

/**
 * Selects the earliest creator-issued end and each sender's timestamp-latest ballot strictly
 * before that cutoff. Event IDs break timestamp ties deterministically, not arrival order.
 * A latest empty or spoiled ballot withdraws the previous vote. Disclosure is a UI concern.
 */
fun aggregatePoll(
    pollEventId: String,
    creator: String,
    poll: PollInfo,
    responses: List<PollResponseEvent>,
    ends: List<PollEndEvent>,
    currentUserId: String? = null,
): PollAggregation {
    val end = ends.asSequence()
        .filter { it.sender == creator && it.end.pollEventId == pollEventId }
        .minWithOrNull(compareBy<PollEndEvent> { it.timestamp }.thenBy { it.eventId })
    val latest = responses.asSequence()
        .filter { it.response.pollEventId == pollEventId && (end == null || it.timestamp < end.timestamp) }
        .groupBy { it.sender }
        .mapValues { (_, events) ->
            events.maxWithOrNull(compareBy<PollResponseEvent> { it.timestamp }.thenBy { it.eventId })!!
        }
    val allowed = poll.answers.map { it.id }.toSet()
    val ballots = latest.mapValues { (_, event) ->
        val answers = event.response.answers.toSet()
        answers.takeIf { it.size <= poll.maxSelections && it.all(allowed::contains) }.orEmpty()
    }
    val votes = poll.answers.associate { answer ->
        answer.id to ballots.values.count { answer.id in it }
    }
    return PollAggregation(
        votes = votes,
        myVotes = currentUserId?.let { ballots[it] }.orEmpty(),
        ended = end != null,
        endTimestamp = end?.timestamp,
        totalVoters = ballots.values.count { it.isNotEmpty() },
    )
}
