package com.matrix.messenger.ui.chat

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle

/**
 * Minimal, safe HTML renderer for Matrix `org.matrix.custom.html` message bodies.
 * Supports: b/strong, i/em, u/ins, s/del/strike, code, pre, a[href], br, p, li,
 * blockquote, font color, span/div (ignored), mx-spoiler (rendered with a
 * distinct background). Everything unknown is stripped to its text content.
 * No scripting constructs are interpreted — output is plain styled text.
 */
object RichTextParser {

    private val tagRegex = Regex("<[^>]+>")

    const val LINK_TAG = "mx-url"

    fun parse(html: String, linkColor: Color, codeBackground: Color, spoilerBackground: Color): AnnotatedString {
        val root = Node(ElementType.ROOT)
        val tokenizer = Tokenizer(html)
        parseChildren(tokenizer, root, null)
        return render(root, linkColor, codeBackground, spoilerBackground)
    }

    // --- parsing ---

    private enum class ElementType {
        ROOT, B, I, U, S, CODE, PRE, A, P, LI, BLOCKQUOTE, SPOILER, FONT, BR, UNKNOWN
    }

    private class Node(val type: ElementType, val href: String? = null, val color: Color? = null) {
        val children = mutableListOf<Any>() // Node or String
    }

    private class Tokenizer(private val html: String) {
        var pos = 0
        fun next(): Token? {
            if (pos >= html.length) return null
            val lt = html.indexOf('<', pos)
            return if (lt == pos) {
                val gt = html.indexOf('>', lt)
                if (gt == -1) {
                    val text = html.substring(pos)
                    pos = html.length
                    Token.Text(text)
                } else {
                    val raw = html.substring(lt, gt + 1)
                    pos = gt + 1
                    Token.Tag(raw)
                }
            } else {
                val end = if (lt == -1) html.length else lt
                val text = decodeEntities(html.substring(pos, end))
                pos = end
                Token.Text(text)
            }
        }
    }

    private sealed interface Token {
        data class Text(val value: String) : Token
        data class Tag(val raw: String) : Token {
            val isClosing get() = raw.startsWith("</")
            val name: String
                get() {
                    val body = raw.removePrefix("</").removePrefix("<").removeSuffix("/>").removeSuffix(">")
                    return body.takeWhile { it.isLetterOrDigit() }.lowercase()
                }
            fun attr(name: String): String? =
                Regex("$name\\s*=\\s*\"([^\"]*)\"").find(raw)?.groupValues?.get(1)
        }
    }

    private fun parseChildren(tokenizer: Tokenizer, parent: Node, until: String?) {
        while (true) {
            val token = tokenizer.next() ?: return
            when (token) {
                is Token.Text -> parent.children.add(token.value)
                is Token.Tag -> {
                    if (token.isClosing) {
                        if (until != null && token.name == until) return
                        // stray closing tag: ignore
                    } else {
                        val type = elementType(token)
                        val node = when (type) {
                            ElementType.A -> Node(type, href = token.attr("href"))
                            ElementType.FONT -> Node(type, color = token.attr("color")?.let(::parseColor))
                            else -> Node(type)
                        }
                        // Unknown tags are stripped (their text content stays at the parent level);
                        // known void elements (br) never have children.
                        val selfClosing = type == ElementType.UNKNOWN || type == ElementType.BR ||
                            token.raw.removeSuffix(">").endsWith("/")
                        if (!selfClosing) parseChildren(tokenizer, node, token.name)
                        parent.children.add(node)
                    }
                }
            }
        }
    }

    private fun elementType(tag: Token.Tag): ElementType = when (tag.name) {
        "b", "strong" -> ElementType.B
        "i", "em" -> ElementType.I
        "u", "ins" -> ElementType.U
        "s", "del", "strike" -> ElementType.S
        "code" -> ElementType.CODE
        "pre" -> ElementType.PRE
        "a" -> ElementType.A
        "p", "div" -> ElementType.P
        "li" -> ElementType.LI
        "blockquote" -> ElementType.BLOCKQUOTE
        "font" -> ElementType.FONT
        "span" -> if (tag.attr("data-mx-spoiler") != null) ElementType.SPOILER else ElementType.UNKNOWN
        "br" -> ElementType.BR
        else -> ElementType.UNKNOWN
    }

    // --- rendering ---

    private data class Style(
        val bold: Boolean = false,
        val italic: Boolean = false,
        val underline: Boolean = false,
        val strike: Boolean = false,
        val code: Boolean = false,
        val pre: Boolean = false,
        val spoiler: Boolean = false,
        val link: String? = null,
        val color: Color? = null,
        val quoteDepth: Int = 0
    )

    private fun render(
        root: Node,
        linkColor: Color,
        codeBackground: Color,
        spoilerBackground: Color
    ): AnnotatedString {
        val ctx = RenderCtx(linkColor, codeBackground, spoilerBackground)
        return buildAnnotatedString {
            renderNode(root, Style(), ctx)
        }
    }

    private class RenderCtx(
        val linkColor: Color,
        val codeBackground: Color,
        val spoilerBackground: Color,
        var lastWasNewline: Boolean = true
    )

    private fun AnnotatedString.Builder.renderNode(
        node: Node,
        style: Style,
        ctx: RenderCtx
    ) {
        val newStyle = when (node.type) {
            ElementType.B -> style.copy(bold = true)
            ElementType.I -> style.copy(italic = true)
            ElementType.U -> style.copy(underline = true)
            ElementType.S -> style.copy(strike = true)
            ElementType.CODE -> style.copy(code = true)
            ElementType.PRE -> style.copy(pre = true, code = true)
            ElementType.A -> style.copy(link = node.href)
            ElementType.BLOCKQUOTE -> style.copy(quoteDepth = style.quoteDepth + 1)
            ElementType.SPOILER -> style.copy(spoiler = true)
            ElementType.FONT -> node.color?.let { style.copy(color = it) } ?: style
            else -> style
        }
        val isBlock = node.type in setOf(ElementType.P, ElementType.LI, ElementType.PRE, ElementType.BLOCKQUOTE)
        if (node.type == ElementType.BR) {
            append("\n")
            ctx.lastWasNewline = true
            return
        }
        if (isBlock && length > 0 && !ctx.lastWasNewline) {
            append("\n")
            ctx.lastWasNewline = true
        }
        if (node.type == ElementType.LI) append("• ")
        if (node.type == ElementType.BLOCKQUOTE && length > 0) append("▍ ")

        withStyle(spanStyleOf(newStyle, ctx.linkColor, ctx.codeBackground, ctx.spoilerBackground)) {
            val link = node.takeIf { it.type == ElementType.A }?.href
            if (link != null) {
                val start = length
                appendChildren(node, newStyle, ctx)
                addStringAnnotation(tag = LINK_TAG, annotation = link, start = start, end = length)
            } else {
                appendChildren(node, newStyle, ctx)
            }
        }
        ctx.lastWasNewline = false
        if (isBlock && node.type != ElementType.LI) {
            append("\n")
            ctx.lastWasNewline = true
        }
    }

    private fun AnnotatedString.Builder.appendChildren(node: Node, style: Style, ctx: RenderCtx) {
        for (child in node.children) {
            when (child) {
                is String -> {
                    append(child)
                    ctx.lastWasNewline = child.isEmpty() || child.endsWith("\n")
                }
                is Node -> renderNode(child, style, ctx)
            }
        }
    }

    private fun spanStyleOf(
        s: Style,
        linkColor: Color,
        codeBackground: Color,
        spoilerBackground: Color
    ): SpanStyle {
        var style = SpanStyle(
            fontWeight = if (s.bold) FontWeight.Bold else null,
            fontStyle = if (s.italic) FontStyle.Italic else null,
            textDecoration = buildList {
                if (s.underline) add(TextDecoration.Underline)
                if (s.strike) add(TextDecoration.LineThrough)
            }.takeIf { it.isNotEmpty() }?.let { TextDecoration.combine(it) },
            color = s.color ?: if (s.link != null) linkColor else Color.Unspecified,
            fontFamily = if (s.code) FontFamily.Monospace else null,
            background = when {
                s.spoiler -> spoilerBackground
                s.code || s.pre -> codeBackground
                else -> Color.Unspecified
            }
        )
        return style
    }

    // --- helpers ---

    private fun decodeEntities(text: String): String = text
        .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
        .replace("&apos;", "'").replace("&#39;", "'").replace("&amp;", "&")
        .replace(Regex("&#[0-9]+;")) { m ->
            m.value.removePrefix("&#").removeSuffix(";").toIntOrNull()?.let { String(Character.toChars(it)) } ?: m.value
        }

    private fun parseColor(value: String): Color? = runCatching {
        when {
            value.startsWith("#") && value.length == 7 -> Color(
                value.substring(1).toInt(16) or 0xFF000000.toInt()
            )
            else -> null
        }
    }.getOrNull()
}
