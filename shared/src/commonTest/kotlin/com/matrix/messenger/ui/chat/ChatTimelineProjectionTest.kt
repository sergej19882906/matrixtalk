package com.matrix.messenger.ui.chat

import com.matrix.messenger.data.model.Message
import com.matrix.messenger.data.model.MessageType
import com.matrix.messenger.ui.components.TimelineItem
import com.matrix.messenger.ui.components.buildTimeline
import kotlin.test.Test
import kotlin.test.assertEquals

class ChatTimelineProjectionTest {
    private fun message(id: String, timestamp: Long = 1_700_000_000_000) = Message(
        eventId = id, senderId = "@user:example.org", senderName = "User", body = id,
        timestamp = timestamp, messageType = MessageType.TEXT,
        isMine = false, isEdited = false, isDeleted = false
    )

    @Test
    fun unreadAnchorSurvivesEarlierHistoryAndNewMessages() {
        val opened = listOf(message("read"), message("firstUnread"), message("secondUnread"))
        assertEquals(1, unreadMessageIndex(opened, "firstUnread"))
        val updated = listOf(message("earlier")) + opened + message("new")
        assertEquals(2, unreadMessageIndex(updated, "firstUnread"))
        val timeline = buildTimeline(updated, unreadMessageIndex(updated, "firstUnread"))
        val divider = timeline.indexOfFirst { it is TimelineItem.UnreadDivider }
        assertEquals("firstUnread", (timeline[divider + 1] as TimelineItem.Msg).message.eventId)
    }

    @Test
    fun lazyIndicesIncludeLeadingHistoryOrRootRows() {
        assertEquals(4, timelineListIndex(3, 1))
        assertEquals(3, timelineListIndex(3, 0))
        assertEquals(-1, timelineListIndex(-1, 1))
    }

    @Test
    fun missingAnchorNeverCreatesFalseUnreadDivider() {
        assertEquals(-1, unreadMessageIndex(listOf(message("match")), "unmatched"))
        assertEquals(-1, unreadMessageIndex(listOf(message("match")), null))
    }
}
