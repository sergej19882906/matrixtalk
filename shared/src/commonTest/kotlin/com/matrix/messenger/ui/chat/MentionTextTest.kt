package com.matrix.messenger.ui.chat

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MentionTextTest {

    private val color = Color(0xFF00FF00)

    @Test
    fun plainTextHasNoSpans() {
        val text = buildMentionAnnotatedString("просто текст", "me", "Me", color)
        assertEquals("просто текст", text.text)
        assertTrue(text.spanStyles.isEmpty())
    }

    @Test
    fun mentionTokenIsColored() {
        val text = buildMentionAnnotatedString("привет @alice как дела", "bob", "Bob", color)
        assertEquals("привет @alice как дела", text.text)
        assertEquals(1, text.spanStyles.size)
        val span = text.spanStyles[0]
        assertEquals("@alice", text.text.substring(span.start, span.end))
        assertEquals(color, span.item.color)
        assertNull(span.item.fontWeight)
    }

    @Test
    fun ownMentionByShortIdIsBold() {
        val text = buildMentionAnnotatedString("@bob:server, посмотри", "bob", "Bob Ross", color)
        val span = text.spanStyles.single()
        assertEquals("@bob:server", text.text.substring(span.start, span.end))
        assertEquals(FontWeight.Bold, span.item.fontWeight)
    }

    @Test
    fun ownMentionByDisplayNameIsBold() {
        val text = buildMentionAnnotatedString("спасибо, @Bob Ross!", "bobby", "Bob Ross", color)
        val span = text.spanStyles.single()
        assertEquals(FontWeight.Bold, span.item.fontWeight)
    }

    @Test
    fun multipleMentionsAllHighlighted() {
        val text = buildMentionAnnotatedString("@a и @b и @c", null, null, color)
        assertEquals(3, text.spanStyles.size)
    }
}
