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
    fun ampersandEscapedInUrl() {
        assertEquals(
            "<a href=\"https://x.org?a=1&amp;b=2\">https://x.org?a=1&amp;b=2</a>",
            MarkdownToHtml.convert("https://x.org?a=1&b=2")
        )
    }
}
