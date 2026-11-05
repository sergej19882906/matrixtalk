package com.matrix.messenger.ui.settings

import com.matrix.messenger.data.repository.SessionPreferencesStore
import com.matrix.messenger.data.repository.SessionStore
import com.matrix.messenger.data.repository.StoredPinState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

/** Fail closed until initialization succeeds. UI protection only; local data is not encrypted. */
data class AppLockState(
    val initialized: Boolean = false,
    val enabled: Boolean = false,
    val locked: Boolean = true,
    val busy: Boolean = false,
    val error: String? = null,
    val failedAttempts: Int = 0,
    val retryAtEpochMillis: Long = 0
)

sealed interface PinOperationResult {
    data object Success : PinOperationResult
    data object WrongPin : PinOperationResult
    data class Throttled(val retryAtEpochMillis: Long) : PinOperationResult
    data class Error(val message: String) : PinOperationResult
}

/** A single shared instance must own verification, configuration and lifecycle locking. */
object AppLockManager : AppLockController(SessionStore)

open class AppLockController(
    private val store: SessionPreferencesStore,
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val cryptoDispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow(AppLockState())
    val state: StateFlow<AppLockState> = _state.asStateFlow()
    private var observedCredential: String? = null
    private val lifecycleGeneration = MutableStateFlow(0L)
    private var observation: Job? = null

    /** Start once in the app owner's scope, not a settings-screen scope. */
    fun start(scope: CoroutineScope): Job {
        observation?.takeIf { it.isActive }?.let { return it }
        return scope.launch {
            initialize()
            store.pinState.filterNotNull().collect {
                // A queued emission can predate another operation; reread the committed
                // snapshot after acquiring the controller mutex instead of publishing it.
                mutex.withLock { publish(store.readPinState()) }
            }
        }.also { observation = it }
    }

    suspend fun initialize(): PinOperationResult = operation {
        publish(store.readPinState())
        PinOperationResult.Success
    }

    fun lock() {
        lifecycleGeneration.update { it + 1 }
        _state.update { it.copy(locked = !it.initialized || it.enabled, error = null) }
    }

    fun onBackground() = lock()
    fun resetAfterLogout() = lock()
    fun clearError() { _state.update { it.copy(error = null) } }
    fun remainingLockoutMillis(): Long = (state.value.retryAtEpochMillis - nowMillis()).coerceAtLeast(0)

    suspend fun verifyPin(pin: String): PinOperationResult = operation {
        val generation = lifecycleGeneration.value
        val stored = store.readPinState().also(::publish)
        val credential = stored.credential
        if (credential == null) {
            _state.update { it.copy(locked = false) }
            return@operation PinOperationResult.Success
        }
        val result = authenticate(pin, stored)
        if (result != PinOperationResult.Success) return@operation result
        // Successful legacy verification must durably migrate before allowing entry.
        val updated = stored.copy(
            credential = if (PinHash.isLegacy(credential)) createCredential(pin) else credential,
            failedAttempts = 0, retryAtEpochMillis = 0
        )
        store.savePinState(updated)
        publish(updated)
        // Backgrounding during slow hashing or I/O must not reopen the UI.
        _state.update { if (generation == lifecycleGeneration.value) it.copy(locked = false) else it }
        PinOperationResult.Success
    }

    /** Initial setup needs no old PIN. Replacing any existing credential always does. */
    suspend fun setPin(pin: String, oldPin: String? = null): PinOperationResult = operation {
        if (!validPin(pin)) return@operation failure("PIN должен содержать 4 цифры")
        val generation = lifecycleGeneration.value
        val stored = store.readPinState().also(::publish)
        if (stored.credential != null) {
            if (oldPin == null) return@operation failure("Введите текущий PIN")
            val result = authenticate(oldPin, stored)
            if (result != PinOperationResult.Success) return@operation result
        }
        val updated = StoredPinState(createCredential(pin))
        store.savePinState(updated)
        publish(updated)
        _state.update { if (generation == lifecycleGeneration.value) it.copy(locked = false) else it }
        PinOperationResult.Success
    }

    suspend fun changePin(oldPin: String, newPin: String): PinOperationResult = setPin(newPin, oldPin)

    suspend fun disablePin(oldPin: String): PinOperationResult = operation {
        val stored = store.readPinState().also(::publish)
        if (stored.credential != null) {
            val result = authenticate(oldPin, stored)
            if (result != PinOperationResult.Success) return@operation result
        }
        val updated = StoredPinState()
        store.savePinState(updated)
        publish(updated)
        PinOperationResult.Success
    }

    private suspend fun authenticate(pin: String, stored: StoredPinState): PinOperationResult {
        if (stored.retryAtEpochMillis > nowMillis()) {
            _state.update { it.copy(error = "Слишком много попыток. Подождите перед повтором") }
            return PinOperationResult.Throttled(stored.retryAtEpochMillis)
        }
        val matches = validPin(pin) && withContext(cryptoDispatcher) {
            PinHash.verify(pin, requireNotNull(stored.credential))
        }
        if (matches) return PinOperationResult.Success
        val attempts = (stored.failedAttempts + 1).coerceAtMost(30)
        val delayMillis = if (attempts < 5) 0L else
            (30_000L * (1L shl (attempts - 5).coerceAtMost(4))).coerceAtMost(300_000L)
        val updated = stored.copy(failedAttempts = attempts,
            retryAtEpochMillis = if (delayMillis == 0L) 0 else nowMillis() + delayMillis)
        store.savePinState(updated)
        publish(updated)
        _state.update { it.copy(error = "Неверный PIN") }
        return if (delayMillis == 0L) PinOperationResult.WrongPin
        else PinOperationResult.Throttled(updated.retryAtEpochMillis)
    }

    private suspend fun createCredential(pin: String) = withContext(cryptoDispatcher) { PinHash.create(pin) }
    private fun validPin(pin: String) = pin.length == 4 && pin.all { it in '0'..'9' }

    private fun publish(pin: StoredPinState) {
        _state.update { current ->
            val enabled = pin.credential != null
            val needsLock = !current.initialized || (!current.enabled && enabled) ||
                (observedCredential != pin.credential && enabled)
            current.copy(initialized = true, enabled = enabled,
                locked = enabled && (current.locked || needsLock),
                failedAttempts = pin.failedAttempts, retryAtEpochMillis = pin.retryAtEpochMillis)
        }
        observedCredential = pin.credential
    }

    private fun failure(message: String): PinOperationResult.Error {
        _state.update { it.copy(error = message) }
        return PinOperationResult.Error(message)
    }

    private suspend fun operation(block: suspend () -> PinOperationResult): PinOperationResult = mutex.withLock {
        _state.update { it.copy(busy = true, error = null) }
        try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Never include a PIN, credential or storage contents in a user-facing error.
            failure("Не удалось прочитать или сохранить настройки блокировки. Повторите попытку")
        } finally {
            _state.update { it.copy(busy = false) }
        }
    }
}
