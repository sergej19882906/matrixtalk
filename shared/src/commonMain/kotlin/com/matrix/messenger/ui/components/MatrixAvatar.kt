package com.matrix.messenger.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.matrix.messenger.ui.theme.AvatarColors
import kotlin.math.abs

/**
 * Shared Element-X style avatar: photo when a URL is available, otherwise a
 * letter fallback on a deterministic color from the Element avatar palette.
 * Default shape is a rounded square (Element X uses squircle-like room/user
 * avatars); pass [CircleShape] for the small in-chat sender avatars.
 */
@Composable
fun MatrixAvatar(
    name: String?,
    avatarUrl: String?,
    size: Dp,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(percent = 40)
) {
    val displayName = name?.takeIf { it.isNotBlank() } ?: "?"
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(avatarColor(displayName)),
        contentAlignment = Alignment.Center
    ) {
        if (avatarUrl != null) {
            AsyncImage(
                // mxc:// avatars load through the authenticated media fetcher
                model = com.matrix.messenger.media.MatrixMedia(avatarUrl, null),
                contentDescription = "Аватар $displayName",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(
                text = displayName.trim().take(1).uppercase(),
                color = Color.White,
                fontSize = (size.value * 0.42f).sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

private fun avatarColor(name: String): Color =
    AvatarColors[abs(name.hashCode()) % AvatarColors.size]
