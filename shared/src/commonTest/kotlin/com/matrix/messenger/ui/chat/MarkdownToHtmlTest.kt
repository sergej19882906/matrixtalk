package com.matrix.messenger.ui.chat

import kotlin.test.Test
import kotlin.test.assertEquals

class MarkdownToHtmlTest {

    @Test
    fun plainTextUnchanged() {
        assertEquals("привет мир", MarkdownToHtml.convert("привет мир"))
    }

    @Test
    fun bold() {
        assertEquals("<strong>жирный</strong>", MarkdownToHtml.convert("**жирный**"))
    }

    @Test
    fun italic() {
        assertEquals("<em>курсив</em>", MarkdownToHtml.convert("*курсив*"))
    }

    @Test
    fun strike() {
        assertEquals("<del>зачёркнутый</del>", MarkdownToHtml.convert("~~зачёркнутый~~"))
    }

    @Test
    fun codeIsProtectedFromOtherRules() {
        assertEquals(
            "<code>**не жирный** https://example.com</code>",
            MarkdownToHtml.convert("`**не жирный** https://example.com`")
        )
    }

    @Test
    fun combinedFormatting() {
        assertEquals(
            "<strong>жирный</strong> и <em>курсив</em>",
            MarkdownToHtml.convert("**жирный** и *курсив*")
        )
    }

    @Test
    fun boldInsideItalicStarsNotConfused() {
        // A lone * pair must not swallow the ** markers; surrounding space is kept.
        assertEquals(
            "<strong>a</strong> <em>b</em>",
            MarkdownToHtml.convert("**a** *b*")
        )
    }

    @Test
    fun htmlIsEscaped() {
        assertEquals(
            "&lt;script&gt;alert(1)&lt;/script&gt;",
            MarkdownToHtml.convert("<script>alert(1)</script>")
        )
    }

    @Test
    fun urlBecomesLink() {
        assertEquals(
            "см. <a href=\"https://matrix.org\">https://matrix.org</a>",
            MarkdownToHtml.convert("см. https://matrix.org")
        )
    }

    @Test
    fun urlInsideFormatting() {
        assertEquals(
            "<strong><a href=\"https://x.org\">https://x.org</a></strong>",
            MarkdownToHtml.convert("**https://x.org**")
        )
    }

    @Test
    fun newlinesBecomeBr() {
        assertEquals("строка1<br />строка2", MarkdownToHtml.convert("строка1\nстрока2"))
    }

    @Test
    fun labeledLink() {
        assertEquals(
            "<a href=\"https://matrix.org\">Matrix</a>",
            MarkdownToHtml.convert("[Matrix](https://matrix.org)")
        )
    }

    @Test
    fun labeledLinkWithFormatting() {
        assertEquals(
            "<a href=\"https://x.org\"><strong>жирная ссылка</strong></a>",
            MarkdownToHtml.convert("[**жирная ссылка**](https://x.org)")
        )
    }

    @Test
    fun unorderedList() {
        assertEquals(
            "<ul><li>первый</li><li>второй</li></ul>",
            MarkdownToHtml.convert("- первый\n- второй")
        )
    }

    @Test
    fun orderedList() {
        assertEquals(
            "<ol><li>раз</li><li>два</li></ol>",
            MarkdownToHtml.convert("1. раз\n2. два")
        )
    }

    @Test
    fun blockquote() {
        assertEquals(
            "<blockquote>цитата<br />ещё строка</blockquote>",
            MarkdownToHtml.convert("> цитата\n> ещё строка")
        )
    }

    @Test
    fun quoteFollowedByParagraph() {
        assertEquals(
            "<blockquote>цитата</blockquote><br />обычный текст",
            MarkdownToHtml.convert("> цитата\nобычный текст")
        )
    }

    @Test
    fun listWithBoldItem() {
        assertEquals(
            "<ul><li><strong>жирный</strong> пункт</li></ul>",
            MarkdownToHtml.convert("- **жирный** пункт")
        )
    }

    @Test
    fun boldAroundUrlStillWorks() {
        assertEquals(
            "<strong><a href=\"https://x.org\">https://x.org</a></strong>",
            MarkdownToHtml.convert("**https://x.org**")
        )
    }

    @Test
    fun htmlInLabeledLinkLabelEscaped() {
        assertEquals(
            "<a href=\"https://x.org\">&lt;b&gt;не жирный&lt;/b&gt;</a>",
            MarkdownToHtml.convert("[<b>не жирный</b>](https://x.org)")
        )
    }

    @Test
    fun ampersandEscapedInUrl() {
        assertEquals(
            "<a href=\"https://x.org?a=1&amp;b=2\">https://x.org?a=1&amp;b=2</a>",
            MarkdownToHtml.convert("https://x.org?a=1&b=2")
        )
    }
}
