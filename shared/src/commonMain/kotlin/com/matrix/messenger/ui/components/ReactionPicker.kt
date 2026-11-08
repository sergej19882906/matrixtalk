package com.matrix.messenger.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import coil3.compose.AsyncImage
import com.matrix.messenger.data.model.StickerItem
import com.matrix.messenger.media.MatrixMedia

/** Emoji palette offered by the reaction picker, grouped like Element X. */
data class PickerEmoji(val emoji: String, val keywords: List<String>)

private val PickerEmojis = listOf(
    // Smileys
    PickerEmoji("😀", listOf("улыбка", "смайл", "радость", "smile", "happy")),
    PickerEmoji("😂", listOf("смех", "слёзы", " laugh", "joy")),
    PickerEmoji("🤣", listOf("ржач", "rofl", "laughing")),
    PickerEmoji("😊", listOf("милота", "улыбка", "blush", "smiling")),
    PickerEmoji("😍", listOf("любовь", "влюблён", "heart", "love")),
    PickerEmoji("🤔", listOf("думает", "вопрос", "thinking", "hmm")),
    PickerEmoji("😎", listOf("круто", "очки", "cool", "sunglasses")),
    PickerEmoji("😢", listOf("плачет", "грусть", "cry", "sad")),
    PickerEmoji("😭", listOf("рыдает", "реву", "sob", "crying")),
    PickerEmoji("😡", listOf("злость", "злой", "angry", "rage")),
    PickerEmoji("🥳", listOf("праздник", "торт", "party", "celebrate")),
    PickerEmoji("😴", listOf("спит", "сон", "sleep", "zzz")),
    PickerEmoji("🤯", listOf("шок", "взрыв мозга", "mind blown", "shock")),
    PickerEmoji("🥺", listOf("умоляет", "жалко", "pleading", "please")),
    PickerEmoji("😱", listOf("страх", "ужас", "scream", "fear")),
    PickerEmoji("🤗", listOf("обнимает", "hug", "embrace")),
    PickerEmoji("🙄", listOf("закатил глаза", "eye roll", "annoyed")),
    PickerEmoji("😤", listOf("упрямство", "триумф", "triumph", "huff")),
    PickerEmoji("😷", listOf("маска", "болею", "mask", "sick")),
    PickerEmoji("😇", listOf("ангел", "нимб", "angel", "innocent")),
    PickerEmoji("🤠", listOf("ковбой", "cowboy", "hat")),
    PickerEmoji("🤫", listOf("тихо", "тьфу", "shush", "quiet")),
    // Gestures
    PickerEmoji("👍", listOf("лайк", "нравится", "ок", "thumbs up", "like")),
    PickerEmoji("👎", listOf("дизлайк", "плохо", "thumbs down", "dislike")),
    PickerEmoji("👏", listOf("аплодисменты", "молодец", "clap", "bravo")),
    PickerEmoji("🙌", listOf("ура", "руки", "raised hands", "hooray")),
    PickerEmoji("🤝", listOf("рукопожатие", "договорились", "handshake", "deal")),
    PickerEmoji("🙏", listOf("спасибо", "спасибо", "please", "thanks", "pray")),
    PickerEmoji("✌️", listOf("победа", "мир", "victory", "peace")),
    PickerEmoji("🤞", listOf("удачи", "скрещённые", "fingers crossed", "luck")),
    PickerEmoji("👌", listOf("отлично", "ок", "ok", "perfect")),
    PickerEmoji("👋", listOf("привет", "пока", "wave", "hello", "bye")),
    PickerEmoji("💪", listOf("сила", "качает", "muscle", "strong")),
    PickerEmoji("🫡", listOf("салют", "смирно", "salute")),
    // Hearts & symbols
    PickerEmoji("❤️", listOf("сердце", "люблю", "heart", "love", "red")),
    PickerEmoji("🧡", listOf("сердце", "оранжевое", "orange heart")),
    PickerEmoji("💚", listOf("сердце", "зелёное", "green heart")),
    PickerEmoji("💜", listOf("сердце", "фиолетовое", "purple heart")),
    PickerEmoji("🔥", listOf("огонь", "жарко", "fire", "hot", "lit")),
    PickerEmoji("⭐", listOf("звезда", "star", "favourite")),
    PickerEmoji("✨", listOf("блеск", "magic", "sparkles", "shiny")),
    PickerEmoji("🎉", listOf("праздник", "конфетти", "party", "tada", "congrats")),
    PickerEmoji("💯", listOf("сто", "сотка", "hundred", "perfect", "100")),
    PickerEmoji("✅", listOf("галочка", "готово", "check", "done", "yes")),
    PickerEmoji("❌", listOf("крест", "нет", "wrong", "cross", "no")),
    PickerEmoji("❓", listOf("вопрос", "question", "unknown")),
)

/**
 * Popup reaction picker (Element X style): frequently used row, keyword search
 * over the palette, custom emotes of the room (MSC2545) and highlight of the
 * reactions already set by the user.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReactionPicker(
    expanded: Boolean,
    myReactions: Set<String>,
    recentReactions: List<String> = emptyList(),
    customEmotes: List<StickerItem> = emptyList(),
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    if (!expanded) return
    var search by remember { mutableStateOf("") }

    val query = search.trim().lowercase()
    val filtered = if (query.isEmpty()) {
        PickerEmojis
    } else {
        PickerEmojis.filter { candidate ->
            candidate.emoji.contains(query) ||
                candidate.keywords.any { it.contains(query) }
        }
    }
    val emotes = if (query.isEmpty()) {
        customEmotes
    } else {
        customEmotes.filter { it.shortcode.lowercase().contains(query) }
    }
    val recent = recentReactions.mapNotNull { recent ->
        PickerEmojis.firstOrNull { it.emoji == recent }?.emoji
    }

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
            Column(modifier = Modifier.padding(8.dp).width(260.dp)) {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = { Text("Поиск реакции") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall,
                    colors = OutlinedTextFieldDefaults.colors()
                )
                if (recent.isNotEmpty() && query.isEmpty()) {
                    Text(
                        text = "Часто используемые",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        recent.take(6).forEach { emoji ->
                            EmojiCell(
                                emoji = emoji,
                                isMine = emoji in myReactions,
                                onSelect = onSelect
                            )
                        }
                    }
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(6),
                    modifier = Modifier.padding(top = 6.dp).height(192.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    items(filtered) { candidate ->
                        EmojiCell(
                            emoji = candidate.emoji,
                            isMine = candidate.emoji in myReactions,
                            onSelect = onSelect
                        )
                    }
                }
                // Room custom emoji (MSC2545 packs, usage "emoticon") — sent as ":shortcode:".
                if (emotes.isNotEmpty()) {
                    Text(
                        text = "Кастомные эмодзи",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                    )
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 120.dp)
                            .verticalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        emotes.forEach { emote ->
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onSelect(":${emote.shortcode}:") },
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model = MatrixMedia(emote.url, null),
                                    contentDescription = emote.shortcode,
                                    modifier = Modifier.size(32.dp),
                                    contentScale = ContentScale.Fit
                                )
                            }
                        }
                    }
                }
                if (filtered.isEmpty() && emotes.isEmpty()) {
                    Text(
                        text = "Ничего не найдено",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun EmojiCell(emoji: String, isMine: Boolean, onSelect: (String) -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .background(
                color = if (isMine) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent,
                shape = CircleShape
            )
            .clickable { onSelect(emoji) },
        contentAlignment = Alignment.Center
    ) {
        Text(text = emoji, style = MaterialTheme.typography.titleMedium)
    }
}
