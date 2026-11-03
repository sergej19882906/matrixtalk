package com.matrix.messenger.ui.chat

/**
 * Minimal Markdown → org.matrix.custom.html converter for the message composer.
 * Supports **bold**, *italic*, `code`, ~~strike~~, bare http(s) links and
 * newlines. HTML in the input is escaped first, so user content can never
 * inject markup. Inline code spans are protected from further replacements.
 */
object MarkdownToHtml {

    private val CODE = Regex("`([^`\\n]+)`")
    private val BOLD = Regex("\\*\\*(.+?)\\*\\*")
    private val ITALIC = Regex("(?<![\\w*])\\*([^*\\n]+)\\*(?![\\w*])")
    private val STRIKE = Regex("~~(.+?)~~")
    private val URL = Regex("https?://[^\\s<>]+")
    private val PLACEHOLDER = Regex("\u0000(\\d+)\u0000")

    fun convert(markdown: String): String {
        // 1. Escape HTML specials before anything else.
        var s = markdown
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")

        // 2. Extract code spans into placeholders so their content stays verbatim.
        val codeTokens = mutableListOf<String>()
        s = CODE.replace(s) { match ->
            codeTokens += "<code>${match.groupValues[1]}</code>"
            "\u0000${codeTokens.size - 1}\u0000"
        }

        // 3. Inline formatting.
        s = BOLD.replace(s) { "<strong>${it.groupValues[1]}</strong>" }
        s = ITALIC.replace(s) { "<em>${it.groupValues[1]}</em>" }
        s = STRIKE.replace(s) { "<del>${it.groupValues[1]}</del>" }

        // 4. Bare URLs become links.
        s = URL.replace(s) { url ->
            "<a href=\"${url.value}\">${url.value}</a>"
        }

        // 5. Restore code spans and convert newlines.
        s = PLACEHOLDER.replace(s) { codeTokens[it.groupValues[1].toInt()] }
        return s.replace("\n", "<br />")
    }
}
