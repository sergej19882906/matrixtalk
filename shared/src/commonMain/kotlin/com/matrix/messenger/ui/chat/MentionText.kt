package com.matrix.messenger.ui.chat

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight

private val mentionRegex = Regex("@[\\p{L}\\p{N}_.:\\-]+")

/**
 * Highlights @mention tokens in a message body. A mention of the current user
 * matches [ownShortId] (with or without the server part) or [ownDisplayName]
 * (possibly multi-word, directly after '@') and is additionally bolded.
 */
fun buildMentionAnnotatedString(
    body: String,
    ownShortId: String?,
    ownDisplayName: String?,
    mentionColor: Color
): AnnotatedString = buildAnnotatedString {
    var index = 0
    for (match in mentionRegex.findAll(body)) {
        append(body.substring(index, match.range.first))
        val afterAt = match.range.first + 1

        val displayNameLength = ownDisplayName
            ?.takeIf { it.isNotBlank() && afterAt + it.length <= body.length }
            ?.takeIf { body.regionMatches(afterAt, it, 0, it.length, ignoreCase = true) }
            ?.length

        val token = match.value.removePrefix("@")
        val tokenBase = token.substringBefore(":")
        val isOwn = displayNameLength != null ||
            (ownShortId != null &&
                (token.equals(ownShortId, ignoreCase = true) || tokenBase.equals(ownShortId, ignoreCase = true)))

        val end = if (displayNameLength != null) afterAt + displayNameLength else match.range.last + 1
        pushStyle(
            SpanStyle(
                color = mentionColor,
                fontWeight = if (isOwn) FontWeight.Bold else null
            )
        )
        append(body.substring(match.range.first, end))
        pop()
        index = end
    }
    append(body.substring(index))
}
