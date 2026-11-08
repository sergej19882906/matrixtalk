package com.matrix.messenger.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HowToVote
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.matrix.messenger.data.model.Message

/** Renders an MSC3381 poll bubble: question, options with results, vote on tap. */
@Composable
fun PollContent(
    message: Message,
    onVote: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
    ended: Boolean = false,
    /** Own polls render inside the primary bubble and switch to onPrimary for contrast. */
    isMine: Boolean = false
) {
    val pollEnded = ended || message.pollEnded
    val poll = message.poll ?: return
    val total = message.pollTotalVoters.coerceAtLeast(1)
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.HowToVote,
                contentDescription = null,
                modifier = Modifier.width(18.dp).height(18.dp),
                tint = if (isMine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = poll.question,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = if (isMine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            if (pollEnded) {
                Text(
                    text = "завершён",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        poll.answers.forEach { answer ->
            val votes = message.pollVotes[answer.id] ?: 0
            val fraction = votes.toFloat() / total
            val mine = message.pollMyAnswers.contains(answer.id)
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (mine) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
                    .clickable(enabled = !pollEnded) { onVote(listOf(answer.id)) }
            ) {
                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = answer.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        if (votes > 0) {
                            Text(
                                text = "$votes",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isMine) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (message.pollTotalVoters > 0) {
                        LinearProgressIndicator(
                            progress = { fraction },
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        )
                    }
                }
            }
        }
        Text(
            text = "Голосов: ${message.pollTotalVoters}" +
                if (poll.disclosed) "" else " · результаты скрыты до завершения",
            style = MaterialTheme.typography.labelSmall,
            color = if (isMine) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}
