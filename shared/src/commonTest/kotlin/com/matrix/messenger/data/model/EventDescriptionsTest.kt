package com.matrix.messenger.data.model

import kotlin.test.Test
import kotlin.test.assertEquals

class EventDescriptionsTest {

    @Test
    fun selfJoin() {
        assertEquals(
            "Аня присоединился к чату",
            EventDescriptions.member("join", "Аня", "Аня", null)
        )
    }

    @Test
    fun selfJoinWithNewName() {
        assertEquals(
            "Аня присоединился к чату (теперь: Анна)",
            EventDescriptions.member("join", "Аня", "Аня", "Анна")
        )
    }

    @Test
    fun invite() {
        assertEquals("Борис пригласил Аня", EventDescriptions.member("invite", "Борис", "Аня", null))
    }

    @Test
    fun kickVsLeave() {
        assertEquals(
            "Борис исключил Аня",
            EventDescriptions.member("leave", "Борис", "Аня", null)
        )
        assertEquals(
            "Аня покинул чат",
            EventDescriptions.member("leave", "Аня", "Аня", null)
        )
    }

    @Test
    fun ban() {
        assertEquals("Борис забанил Аня", EventDescriptions.member("ban", "Борис", "Аня", null))
    }

    @Test
    fun topicAndName() {
        assertEquals("Аня изменил описание: обсудили план", EventDescriptions.topic("Аня", "обсудили план"))
        assertEquals("Аня удалил описание комнаты", EventDescriptions.topic("Аня", null))
        assertEquals("Аня изменил название на «Чат»", EventDescriptions.roomName("Аня", "Чат"))
    }
}
