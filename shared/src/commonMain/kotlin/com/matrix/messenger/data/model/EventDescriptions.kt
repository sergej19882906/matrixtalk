package com.matrix.messenger.data.model

/**
 * Human-readable descriptions of room state events, rendered as centered
 * system rows in the timeline (Element style: "X joined the room").
 */
object EventDescriptions {

    fun member(membership: String, sender: String, subject: String, newDisplayName: String?): String =
        when (membership) {
            "join" ->
                if (sender == subject) {
                    val name = newDisplayName?.let { " (теперь: $it)" } ?: ""
                    "$sender присоединился к чату$name"
                } else {
                    "$sender добавил $subject в чат"
                }
            "invite" -> "$sender пригласил $subject"
            "ban" -> "$sender забанил $subject"
            "leave" ->
                if (sender == subject) "$sender покинул чат"
                else "$sender исключил $subject"
            "knock" -> "$subject просит войти в чат"
            else -> "$sender изменил статус участника $subject ($membership)"
        }

    fun topic(sender: String, topic: String?): String =
        if (topic.isNullOrBlank()) "$sender удалил описание комнаты"
        else "$sender изменил описание: $topic"

    fun roomName(sender: String, name: String?): String =
        if (name.isNullOrBlank()) "$sender удалил название комнаты"
        else "$sender изменил название на «$name»"

    fun avatarChanged(sender: String): String = "$sender изменил аватар комнаты"
}
