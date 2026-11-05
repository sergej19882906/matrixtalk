package com.matrix.messenger.ui.settings

import com.matrix.messenger.data.repository.StoredPinState
import com.matrix.messenger.data.repository.TempStore
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class AppLockManagerTest {
    @Test
    fun saltedCredentialsVerifyAndRejectMalformedInput() {
        val first = PinHash.create("1234")
        val second = PinHash.create("1234")
        assertNotEquals(first, second)
        assertFalse(PinHash.isLegacy(first))
        assertTrue(PinHash.verify("1234", first))
        assertFalse(PinHash.verify("4321", first))
        assertFalse(PinHash.verify("1234", "pbkdf2-sha256\$1\$bad\$bad"))
        assertTrue(PinHash.verify("1234", PinHash.of("1234")))
    }

    @Test
    fun setupChangeAndDisableRequireCurrentPin() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val disk = TempStore(dispatcher)
        val store = disk.open()
        val lock = AppLockController(store, { 1_000L }, dispatcher)
        assertEquals(PinOperationResult.Success, lock.initialize())
        assertFalse(lock.state.value.enabled)
        assertEquals(PinOperationResult.Success, lock.setPin("1234"))
        assertTrue(lock.state.value.enabled)
        assertFalse(lock.state.value.locked)
        assertIs<PinOperationResult.Error>(lock.setPin("5678"))
        assertEquals(PinOperationResult.WrongPin, lock.changePin("9999", "5678"))
        assertEquals(PinOperationResult.Success, lock.changePin("1234", "5678"))
        assertEquals(PinOperationResult.WrongPin, lock.disablePin("1234"))
        assertTrue(lock.state.value.enabled)
        assertEquals(PinOperationResult.Success, lock.disablePin("5678"))
        assertFalse(lock.state.value.enabled)
        assertEquals(null, disk.open().readPinHash())
    }

    @Test
    fun legacyVerificationMigratesDurablyBeforeUnlock() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val disk = TempStore(dispatcher)
        val store = disk.open()
        val legacy = PinHash.of("1234")
        store.savePinHash(legacy)
        val lock = AppLockController(store, { 1_000L }, dispatcher)
        lock.initialize()
        assertTrue(lock.state.value.locked)
        disk.failWrites = true
        assertIs<PinOperationResult.Error>(lock.verifyPin("1234"))
        assertTrue(lock.state.value.locked)
        assertEquals(legacy, store.readPinHash())
        disk.failWrites = false
        assertEquals(PinOperationResult.Success, lock.verifyPin("1234"))
        assertFalse(lock.state.value.locked)
        val migrated = requireNotNull(disk.open().readPinHash())
        assertFalse(PinHash.isLegacy(migrated))
        assertTrue(PinHash.verify("1234", migrated))
    }

    @Test
    fun sharedPersistedThrottleSurvivesRecreationAndBlocksSettingsChanges() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val disk = TempStore(dispatcher)
        val store = disk.open()
        store.savePinHash(PinHash.of("1234"))
        var now = 1_000L
        val lock = AppLockController(store, { now }, dispatcher)
        lock.initialize()
        repeat(4) { assertEquals(PinOperationResult.WrongPin, lock.verifyPin("9999")) }
        assertIs<PinOperationResult.Throttled>(lock.verifyPin("9999"))
        assertEquals(31_000L, lock.state.value.retryAtEpochMillis)
        assertIs<PinOperationResult.Throttled>(lock.disablePin("1234"))
        val recreated = AppLockController(disk.open(), { now }, dispatcher)
        recreated.initialize()
        assertEquals(5, recreated.state.value.failedAttempts)
        assertIs<PinOperationResult.Throttled>(recreated.verifyPin("1234"))
        now = 31_000L
        assertEquals(PinOperationResult.Success, recreated.verifyPin("1234"))
        assertEquals(0, disk.open().readPinState().failedAttempts)
        assertEquals(0L, recreated.state.value.retryAtEpochMillis)
    }

    @Test
    fun backgroundDuringVerificationDoesNotReopenGate() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val disk = TempStore(dispatcher)
        val store = disk.open()
        store.savePinHash(PinHash.of("1234"))
        val lock = AppLockController(store, { 1_000L }, dispatcher)
        lock.initialize()
        disk.onWrite = { lock.onBackground() }
        assertEquals(PinOperationResult.Success, lock.verifyPin("1234"))
        disk.onWrite = null
        assertTrue(lock.state.value.locked)
        assertEquals(PinOperationResult.Success, lock.verifyPin("1234"))
        assertFalse(lock.state.value.locked)
        lock.resetAfterLogout()
        assertTrue(lock.state.value.locked)
    }

    @Test
    fun reactiveChangesNeverInterpretUnloadedOrFailedStorageAsDisabled() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val disk = TempStore(dispatcher)
        val store = disk.open()
        val lock = AppLockController(store, { 1_000L }, dispatcher)
        assertTrue(lock.state.value.locked)
        disk.failReads = true
        assertIs<PinOperationResult.Error>(lock.initialize())
        assertFalse(lock.state.value.initialized)
        assertTrue(lock.state.value.locked)
        disk.failReads = false
        lock.start(backgroundScope)
        runCurrent()
        store.savePinState(StoredPinState(PinHash.of("1234")))
        runCurrent()
        assertTrue(lock.state.value.enabled)
        assertTrue(lock.state.value.locked)
        assertEquals(PinOperationResult.Success, lock.verifyPin("1234"))
        runCurrent()
        assertFalse(lock.state.value.locked) // a queued old credential emission cannot relock
        store.savePinHash(null)
        runCurrent()
        assertFalse(lock.state.value.enabled)
    }
}
