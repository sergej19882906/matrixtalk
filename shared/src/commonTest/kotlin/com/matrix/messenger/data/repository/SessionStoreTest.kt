package com.matrix.messenger.data.repository

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SessionStoreTest {

    @Test
    fun sessionRoundtrip() = runTest {
        SessionStore.clear()
        assertNull(SessionStore.read())

        SessionStore.save(
            SessionStore.Session(
                homeServer = "https://example.org",
                userId = "@user:example.org",
                deviceId = "DEVICE",
                accessToken = "TOKEN"
            )
        )
        val session = SessionStore.read()
        assertEquals("https://example.org", session?.homeServer)
        assertEquals("@user:example.org", session?.userId)
        assertEquals("DEVICE", session?.deviceId)
        assertEquals("TOKEN", session?.accessToken)

        SessionStore.clear()
        assertNull(SessionStore.read())
    }

    @Test
    fun prefsRoundtrip() = runTest {
        SessionStore.saveThemeMode("dark")
        assertEquals("dark", SessionStore.readThemeMode())

        SessionStore.saveNotificationsEnabled(false)
        assertEquals(false, SessionStore.readNotificationsEnabled())

        SessionStore.clear()
        assertNull(SessionStore.readThemeMode())
        assertNull(SessionStore.readNotificationsEnabled())
    }
}
