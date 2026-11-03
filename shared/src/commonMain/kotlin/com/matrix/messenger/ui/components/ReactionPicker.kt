package com.matrix.messenger.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup

/** Emoji palette offered by the reaction picker, grouped like Element X. */
private val PickerEmojis = listOf(
    // Smileys
    "😀", "😁", "😂", "🤣", "😊", "😍", "🤔", "😎", "😢", "😭", "😡", "🥳",
    "😴", "🤯", "🥺", "😱", "🤗", "🤫", "🙄", "😤", "😷", "🤒", "😇", "🤠",
    // Gestures
    "👍", "👎", "👏", "🙌", "🤝", "🙏", "✌️", "🤞", "👌", "👋", "💪", "🫡",
    // Hearts & symbols
    "❤️", "🧡", "💚", "💜", "🔥", "⭐", "✨", "🎉", "💯", "✅", "❌", "❓",
)

/**
 * Popup grid of emoji reactions, anchored to a message bubble (Element X style).
 */
@Composable
fun ReactionPicker(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    if (!expanded) return
    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onDismiss
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 4.dp,
            shadowElevation = 8.dp
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(6),
                    modifier = Modifier.width(240.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    items(PickerEmojis) { emoji ->
                        Box(
                            modifier = Modifier
                                .clickable {
                                    onDismiss()
                                    onSelect(emoji)
                                }
                                .padding(6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = emoji, style = MaterialTheme.typography.titleLarge)
                        }
                    }
                }
            }
        }
    }
}
