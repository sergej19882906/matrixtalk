package com.matrix.messenger.ui.chat

/**
 * Minimal Markdown → org.matrix.custom.html converter for the message composer.
 *
 * Block level: unordered lists (`- item`), ordered lists (`1. item`),
 * blockquotes (`> text`). Inline level: **bold**, *italic*, `code`,
 * ~~strike~~, links (`[text](url)` and bare http(s) URLs), newlines.
 *
 * HTML in the input is escaped first, so user content can never inject
 * markup. Inline code spans are protected from further replacements.
 */
object MarkdownToHtml {

    private val CODE = Regex("`([^`\\n]+)`")
    private val BOLD = Regex("\\*\\*(.+?)\\*\\*")
    private val ITALIC = Regex("(?<![\\w*])\\*([^*\\n]+)\\*(?![\\w*])")
    private val STRIKE = Regex("~~(.+?)~~")
    private val LABELED_LINK = Regex("\\[([^\\]\\n]+)]\\(([^)\\s]+)\\)")
    private val URL = Regex("https?://[^\\s<>\"\\u0000]+")
    private val PLACEHOLDER = Regex("\u0000(\\d+)\u0000")

    private val UNORDERED = Regex("^[-*] (.*)$")
    private val ORDERED = Regex("^(\\d+)[.)] (.*)$")
    private val QUOTE = Regex("^> ?(.*)$")

    fun convert(markdown: String): String {
        val lines = markdown.lines()
        val out = StringBuilder()
        var index = 0
        var firstBlock = true

        fun separate() {
            if (!firstBlock) out.append("<br />")
            firstBlock = false
        }

        while (index < lines.size) {
            val line = lines[index]

            when {
                QUOTE.containsMatchIn(line) -> {
                    separate()
                    val quoted = mutableListOf<String>()
                    while (index < lines.size && QUOTE.containsMatchIn(lines[index])) {
                        quoted += QUOTE.matchEntire(lines[index])!!.groupValues[1]
                        index++
                    }
                    out.append("<blockquote>")
                        .append(quoted.joinToString("<br />") { inline(it) })
                        .append("</blockquote>")
                }

                UNORDERED.containsMatchIn(line) -> {
                    separate()
                    out.append("<ul>")
                    while (index < lines.size && UNORDERED.containsMatchIn(lines[index])) {
                        val item = UNORDERED.matchEntire(lines[index])!!.groupValues[1]
                        out.append("<li>").append(inline(item)).append("</li>")
                        index++
                    }
                    out.append("</ul>")
                }

                ORDERED.containsMatchIn(line) -> {
                    separate()
                    out.append("<ol>")
                    while (index < lines.size && ORDERED.containsMatchIn(lines[index])) {
                        val item = ORDERED.matchEntire(lines[index])!!.groupValues[2]
                        out.append("<li>").append(inline(item)).append("</li>")
                        index++
                    }
                    out.append("</ol>")
                }

                else -> {
                    separate()
                    out.append(inline(line))
                    index++
                }
            }
        }
        return out.toString()
    }

    /** Inline formatting for a single logical line (already block-processed). */
    private fun inline(text: String): String {
        // 1. Escape HTML specials before anything else.
        var s = text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")

        // 2. Extract code spans into placeholders so their content stays verbatim.
        val tokens = mutableListOf<String>()
        s = CODE.replace(s) { match ->
            tokens += "<code>${match.groupValues[1]}</code>"
            "\u0000${tokens.size - 1}\u0000"
        }

        // 3. Emphasis first: labels inside [text](url) must be formatted,
        //    and bare URLs must not swallow the emphasis markers.
        s = BOLD.replace(s) { "<strong>${it.groupValues[1]}</strong>" }
        s = ITALIC.replace(s) { "<em>${it.groupValues[1]}</em>" }
        s = STRIKE.replace(s) { "<del>${it.groupValues[1]}</del>" }

        // 4. Links: [text](url) extracted into placeholders so the bare-URL
        //    pass cannot re-link the URL inside the produced href.
        s = LABELED_LINK.replace(s) { match ->
            tokens += "<a href=\"${match.groupValues[2]}\">${match.groupValues[1]}</a>"
            "\u0000${tokens.size - 1}\u0000"
        }

        // 5. Bare URLs.
        s = URL.replace(s) { url ->
            "<a href=\"${url.value}\">${url.value}</a>"
        }

        // 6. Restore placeholders.
        return PLACEHOLDER.replace(s) { tokens[it.groupValues[1].toInt()] }
    }
}
