package com.matrix.messenger.ui.chat

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RichTextParserTest {

    private val link = Color.Blue
    private val codeBg = Color.Gray
    private val spoilerBg = Color.DarkGray

    private fun parse(html: String) = RichTextParser.parse(html, link, codeBg, spoilerBg)

    /** The innermost span covering exactly [word] (later entries are more nested). */
    private fun spanFor(text: androidx.compose.ui.text.AnnotatedString, word: String) =
        text.spanStyles.filter { text.text.substring(it.start, it.end) == word }
            .last()

    @Test
    fun plainTextPassesThrough() {
        val text = parse("hello world")
        assertEquals("hello world", text.text)
    }

    @Test
    fun entitiesAreDecoded() {
        assertEquals("a < b & c", parse("a &lt; b &amp; c").text)
    }

    @Test
    fun boldItalicStrikethroughStylesApplied() {
        val text = parse("<b>bold</b> <i>it</i> <del>gone</del>")
        assertEquals("bold it gone", text.text)
        assertEquals(FontWeight.Bold, spanFor(text, "bold").item.fontWeight)
        assertEquals(FontStyle.Italic, spanFor(text, "it").item.fontStyle)
    }

    @Test
    fun codeUsesMonospaceAndBackground() {
        val text = parse("use <code>val x = 1</code> here")
        val span = spanFor(text, "val x = 1")
        assertEquals(FontFamily.Monospace, span.item.fontFamily)
        assertEquals(codeBg, span.item.background)
    }

    @Test
    fun linkGetsUrlAnnotationAndColor() {
        val text = parse("see <a href=\"https://matrix.org\">matrix</a> now")
        assertEquals("see matrix now", text.text)
        assertEquals(link, spanFor(text, "matrix").item.color)
        val url = text.getStringAnnotations(RichTextParser.LINK_TAG, 0, text.text.length).firstOrNull()
        assertNotNull(url)
        assertEquals("https://matrix.org", url!!.item)
    }

    @Test
    fun unknownTagsAreStrippedToContent() {
        assertEquals("content", parse("<marquee><xyz>content</xyz></marquee>").text)
    }

    @Test
    fun lineBreaksAndParagraphs() {
        assertEquals("a\nb", parse("a<br>b").text)
        assertEquals("one\ntwo", parse("<p>one</p><p>two</p>").text.trimEnd())
    }

    @Test
    fun listItemsGetBullets() {
        val text = parse("<ul><li>first</li><li>second</li></ul>")
        assertTrue(text.text.contains("• first"))
        assertTrue(text.text.contains("• second"))
    }

    @Test
    fun spoilerGetsBackground() {
        val text = parse("<span data-mx-spoiler=\"reason\">secret</span>")
        assertEquals(spoilerBg, spanFor(text, "secret").item.background)
    }
}
