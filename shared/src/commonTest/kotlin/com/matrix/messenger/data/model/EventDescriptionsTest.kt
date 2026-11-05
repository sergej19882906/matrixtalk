package com.matrix.messenger.data.model

import kotlin.test.Test
import kotlin.test.assertEquals

class EventDescriptionsTest {

    @Test
    fun selfJoin() {
        assertEquals(
            "Аня присоединился к чату",
            EventDescriptions.member("join", "Аня", "Аня", null, previousMembership = "invite")
        )
    }

    @Test
    fun selfJoinWithNewName() {
        assertEquals(
            "Аня изменил отображаемое имя на «Анна»",
            EventDescriptions.member("join", "Аня", "Аня", "Анна", "join", "Аня")
        )
    }

    @Test
    fun unknownPreviousMembershipDoesNotInventAJoin() {
        assertEquals("Аня обновил сведения об участнике Аня", EventDescriptions.member("join", "Аня", "Аня", "Анна"))
        assertEquals("Аня обновил профиль участника", EventDescriptions.member("join", "Аня", "Аня", "Аня", "join", "Аня"))
        assertEquals("Аня удалил отображаемое имя", EventDescriptions.member("join", "Аня", "Аня", null, "join", "Аня"))
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
