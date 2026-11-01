package com.matrix.messenger.ui.components

import com.matrix.messenger.data.model.Message

/**
 * Flat message list projected into chat-timeline items: a date header before
 * each calendar day, plus sender grouping info (consecutive messages of the
 * same sender within 5 minutes are grouped Element-X style: the sender name
 * and avatar are shown only on the first bubble, corners are tightened
 * between neighbors).
 */
sealed interface TimelineItem {
    /** Calendar-day separator; [epochDay] is the stable LazyColumn key suffix. */
    data class Header(val epochDay: Long, val label: String) : TimelineItem

    data class Msg(
        val message: Message,
        /** First bubble of a sender group -> show sender name (and avatar). */
        val isGroupStart: Boolean,
        /** Consecutive bubble of the same sender -> tightened top corners. */
        val groupedWithPrevious: Boolean,
        /** Next message continues the group -> tightened bottom corners. */
        val groupedWithNext: Boolean
    ) : TimelineItem

    val key: String
        get() = when (this) {
            is Header -> "date_$epochDay"
            is Msg -> message.eventId
        }
}

private const val GROUP_WINDOW_MS = 5 * 60 * 1000L

fun buildTimeline(messages: List<Message>): List<TimelineItem> {
    val items = mutableListOf<TimelineItem>()
    var lastEpochDay = Long.MIN_VALUE
    for (index in messages.indices) {
        val message = messages[index]
        val epochDay = TimeFormat.epochDay(message.timestamp)
        if (epochDay != lastEpochDay) {
            items += TimelineItem.Header(epochDay, TimeFormat.separatorLabel(message.timestamp))
            lastEpochDay = epochDay
        }
        val previous = messages.getOrNull(index - 1)
        val next = messages.getOrNull(index + 1)
        val groupsWithPrevious = previous != null && groupsTogether(previous, message)
        val groupsWithNext = next != null && groupsTogether(message, next)
        items += TimelineItem.Msg(
            message = message,
            isGroupStart = !groupsWithPrevious,
            groupedWithPrevious = groupsWithPrevious,
            groupedWithNext = groupsWithNext
        )
    }
    return items
}

private fun groupsTogether(first: Message, second: Message): Boolean =
    first.senderId == second.senderId &&
        second.timestamp - first.timestamp < GROUP_WINDOW_MS
