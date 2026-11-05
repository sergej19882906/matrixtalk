package com.matrix.messenger.data.repository

import com.matrix.messenger.data.model.ProxySettings
import com.matrix.messenger.data.model.ProxyType
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SessionStoreTest {
    private val session = SessionStore.Session("https://example.org", "@user:example.org", "DEVICE", "TOKEN")

    @Test
    fun sessionRoundtripAndLogoutPreservePreferences() = runTest {
        val disk = TempStore(StandardTestDispatcher(testScheduler))
        val store = disk.open()
        assertNull(store.read())
        store.save(session)
        store.saveThemeMode("dark")
        store.savePinHash("legacy-hash")
        assertEquals(session, disk.open().read())
        store.clearSession()
        val reloaded = disk.open()
        assertNull(reloaded.read())
        assertEquals("dark", reloaded.readThemeMode())
        assertEquals("legacy-hash", reloaded.readPinHash())
    }

    @Test
    fun allConcurrentMutatorsPreserveOtherFields() = runTest {
        val disk = TempStore(StandardTestDispatcher(testScheduler))
        val store = disk.open()
        val proxy = ProxySettings(ProxyType.HTTP, "localhost", 8080, null, null)
        store.save(session)
        val jobs = listOf(
            launch { store.clearSession() },
            launch { store.saveThemeMode("dark") },
            launch { store.saveNotificationsEnabled(false) },
            launch { store.saveVideoDevice("camera") },
            launch { store.saveAudioInputDevice("microphone") },
            launch { store.saveMutedRooms(listOf("!muted")) },
            launch { store.saveRoomNotificationLevels(mapOf("!room" to "mute")) },
            launch { store.saveRecentReactions(listOf("reaction")) },
            launch { store.savePinHash("credential") },
            launch { store.saveDefaultNotificationLevel("mentions") },
            launch { store.saveProxySettings(proxy) }
        )
        jobs.forEach { it.join() }
        val reloaded = disk.open()
        assertNull(reloaded.read())
        assertEquals("dark", reloaded.readThemeMode())
        assertEquals(false, reloaded.readNotificationsEnabled())
        assertEquals("camera", reloaded.readVideoDevice())
        assertEquals("microphone", reloaded.readAudioInputDevice())
        assertEquals(listOf("!muted"), reloaded.readMutedRooms())
        assertEquals(mapOf("!room" to "mute"), reloaded.readRoomNotificationLevels())
        assertEquals(listOf("reaction"), reloaded.readRecentReactions())
        assertEquals("credential", reloaded.readPinHash())
        assertEquals("mentions", reloaded.readDefaultNotificationLevel())
        assertEquals(proxy, reloaded.readProxySettings())
    }

    @Test
    fun failedReplacementPreservesCacheFlowAndReload() = runTest {
        val disk = TempStore(StandardTestDispatcher(testScheduler))
        val store = disk.open()
        store.save(session)
        store.savePinHash("old")
        val before = disk.committed?.decodeToString()
        disk.failWrites = true
        assertFailsWith<IllegalStateException> { store.savePinHash("new") }
        assertFailsWith<IllegalStateException> { store.clearSession() }
        assertFailsWith<IllegalStateException> { store.saveNotificationsEnabled(false) }
        assertEquals(before, disk.committed?.decodeToString())
        assertEquals(session, store.read())
        assertEquals("old", store.pinState.value?.credential)
        assertEquals("old", disk.open().readPinHash())
        disk.failWrites = false
        store.saveThemeMode("light")
        assertEquals(session, disk.open().read())
    }

    @Test
    fun legacyOnlyWhenCurrentFileAbsent() = runTest {
        val disk = TempStore(StandardTestDispatcher(testScheduler))
        disk.legacy = session
        val store = disk.open()
        assertEquals(session, store.read())
        store.saveThemeMode("light")
        store.clearSession()
        assertNull(disk.open().read()) // persisted logout must not resurrect legacy credentials
        disk.committed = "not-json".encodeToByteArray()
        val corrupted = disk.open()
        assertFailsWith<Exception> { corrupted.read() }
        assertFailsWith<Exception> { corrupted.saveThemeMode("dark") }
        assertNull(corrupted.pinState.value)
        assertEquals("not-json", disk.committed?.decodeToString())
    }

    @Test
    fun readFailureIsNotCachedAsEmptyPreferences() = runTest {
        val disk = TempStore(StandardTestDispatcher(testScheduler))
        disk.open().save(session)
        disk.failReads = true
        val store = disk.open()
        assertFailsWith<IllegalStateException> { store.read() }
        assertNull(store.pinState.value)
        disk.failReads = false
        assertEquals(session, store.read())
        assertNotNull(store.pinState.value)
    }
}
