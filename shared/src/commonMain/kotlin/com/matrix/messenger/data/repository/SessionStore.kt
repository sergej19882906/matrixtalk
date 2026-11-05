package com.matrix.messenger.data.repository

import com.matrix.messenger.data.model.ProxySettings
import com.matrix.messenger.platform.readFileBytesIfExists
import com.matrix.messenger.platform.sessionStorePath
import com.matrix.messenger.platform.writeFileBytesAtomic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Production preferences. PIN locking protects the UI, not this unencrypted JSON file. */
object SessionStore : SessionPreferencesStore(
    path = { sessionStorePath().removeSuffix("session.preferences_pb") + "settings.json" },
    reader = ::readFileBytesIfExists,
    atomicWriter = ::writeFileBytesAtomic,
    legacyReader = {
        try {
            LegacyDataStore.readSession()?.let {
                Session(it.homeServer, it.userId, it.deviceId, it.accessToken)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            // Read-only, best-effort migration also tolerates missing desktop Unsafe.
            null
        }
    }
) {
    data class Session(
        val homeServer: String,
        val userId: String,
        val deviceId: String,
        val accessToken: String
    )
}

@Serializable
data class StoredPinState(
    val credential: String? = null,
    val failedAttempts: Int = 0,
    val retryAtEpochMillis: Long = 0
)

/** Injectable storage for tests; all reads and read-modify-writes share one mutex. */
open class SessionPreferencesStore(
    private val path: () -> String,
    private val reader: (String) -> ByteArray?,
    private val atomicWriter: (String, ByteArray) -> Unit,
    private val legacyReader: suspend () -> SessionStore.Session? = { null },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    @Serializable
    private data class StoredPrefs(
        val homeServer: String? = null,
        val userId: String? = null,
        val deviceId: String? = null,
        val accessToken: String? = null,
        val themeMode: String? = null,
        val notificationsEnabled: Boolean? = null,
        val videoDevice: String? = null,
        val audioInputDevice: String? = null,
        val mutedRooms: List<String>? = null,
        val roomNotificationLevels: Map<String, String>? = null,
        val recentReactions: List<String>? = null,
        // Keep the legacy field name so existing SHA-256 credentials still load.
        val pinHash: String? = null,
        val pinFailedAttempts: Int = 0,
        val pinRetryAtEpochMillis: Long = 0,
        val defaultNotificationLevel: String? = null,
        val proxyType: String? = null,
        val proxyHost: String? = null,
        val proxyPort: Int? = null,
        val proxyUsername: String? = null,
        val proxyPassword: String? = null
    ) {
        fun pinState() = StoredPinState(pinHash, pinFailedAttempts, pinRetryAtEpochMillis)
    }

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val mutex = Mutex()
    private var cache: StoredPrefs? = null
    private val _pinState = MutableStateFlow<StoredPinState?>(null)
    /** null means not loaded yet, not "PIN disabled". Published only after successful I/O. */
    val pinState: StateFlow<StoredPinState?> = _pinState.asStateFlow()

    // These helpers are only called with mutex held. A corrupt/unreadable current file
    // must never silently become empty preferences and overwrite the saved session/PIN.
    private suspend fun load(): StoredPrefs {
        cache?.let { return it }
        val bytes = withContext(ioDispatcher) { reader(path()) }
        val prefs = if (bytes != null) {
            json.decodeFromString<StoredPrefs>(bytes.decodeToString())
        } else {
            legacyReader()?.let {
                StoredPrefs(it.homeServer, it.userId, it.deviceId, it.accessToken)
            } ?: StoredPrefs()
        }
        cache = prefs
        _pinState.value = prefs.pinState()
        return prefs
    }

    private suspend fun save(prefs: StoredPrefs) {
        val bytes = json.encodeToString(StoredPrefs.serializer(), prefs).encodeToByteArray()
        // The writer commits by atomic replacement or throws, leaving the old file intact.
        // Once replacement begins, finish the commit and cache publication even if
        // the caller is cancelled; otherwise disk and the in-process snapshot diverge.
        withContext(ioDispatcher + NonCancellable) {
            atomicWriter(path(), bytes)
            cache = prefs
            _pinState.value = prefs.pinState()
        }
    }

    suspend fun save(session: SessionStore.Session) = mutex.withLock {
        save(load().copy(homeServer = session.homeServer, userId = session.userId,
            deviceId = session.deviceId, accessToken = session.accessToken))
    }

    suspend fun read(): SessionStore.Session? = mutex.withLock {
        load().let { p ->
            if (p.homeServer != null && p.userId != null && p.deviceId != null && p.accessToken != null) {
                SessionStore.Session(p.homeServer, p.userId, p.deviceId, p.accessToken)
            } else null
        }
    }

    /** Drops credentials while keeping preferences and app-lock configuration. */
    suspend fun clearSession() = mutex.withLock {
        save(load().copy(accessToken = null, deviceId = null, userId = null))
    }

    suspend fun clear() = mutex.withLock { save(StoredPrefs()) }
    suspend fun readThemeMode(): String? = mutex.withLock { load().themeMode }
    suspend fun saveThemeMode(mode: String) = mutex.withLock { save(load().copy(themeMode = mode)) }
    /** null = never configured (treated as enabled). */
    suspend fun readNotificationsEnabled(): Boolean? = mutex.withLock { load().notificationsEnabled }
    suspend fun saveNotificationsEnabled(enabled: Boolean) = mutex.withLock {
        save(load().copy(notificationsEnabled = enabled))
    }
    suspend fun readVideoDevice(): String? = mutex.withLock { load().videoDevice }
    suspend fun saveVideoDevice(device: String) = mutex.withLock { save(load().copy(videoDevice = device)) }
    suspend fun readAudioInputDevice(): String? = mutex.withLock { load().audioInputDevice }
    suspend fun saveAudioInputDevice(device: String) = mutex.withLock {
        save(load().copy(audioInputDevice = device))
    }
    suspend fun readMutedRooms(): List<String> = mutex.withLock { load().mutedRooms.orEmpty() }
    suspend fun saveMutedRooms(rooms: List<String>) = mutex.withLock { save(load().copy(mutedRooms = rooms)) }
    suspend fun readRoomNotificationLevels(): Map<String, String> =
        mutex.withLock { load().roomNotificationLevels.orEmpty() }
    suspend fun saveRoomNotificationLevels(levels: Map<String, String>) = mutex.withLock {
        save(load().copy(roomNotificationLevels = levels))
    }
    suspend fun readPinHash(): String? = mutex.withLock { load().pinHash }
    suspend fun readPinState(): StoredPinState = mutex.withLock { load().pinState() }
    suspend fun savePinHash(hash: String?) = mutex.withLock {
        save(load().copy(pinHash = hash, pinFailedAttempts = 0, pinRetryAtEpochMillis = 0))
    }
    suspend fun savePinState(state: StoredPinState) = mutex.withLock {
        require(state.failedAttempts >= 0 && state.retryAtEpochMillis >= 0)
        save(load().copy(pinHash = state.credential, pinFailedAttempts = state.failedAttempts,
            pinRetryAtEpochMillis = state.retryAtEpochMillis))
    }
    suspend fun readDefaultNotificationLevel(): String? = mutex.withLock { load().defaultNotificationLevel }
    suspend fun saveDefaultNotificationLevel(level: String?) = mutex.withLock {
        save(load().copy(defaultNotificationLevel = level))
    }
    suspend fun readProxySettings(): ProxySettings? = mutex.withLock {
        val prefs = load()
        val type = prefs.proxyType?.let { t ->
            com.matrix.messenger.data.model.ProxyType.entries.firstOrNull { it.name == t }
        }
        if (type == null || prefs.proxyHost == null || prefs.proxyPort == null) null
        else ProxySettings(type, prefs.proxyHost, prefs.proxyPort, prefs.proxyUsername, prefs.proxyPassword)
    }
    suspend fun saveProxySettings(settings: ProxySettings?) = mutex.withLock {
        save(load().copy(proxyType = settings?.type?.name, proxyHost = settings?.host,
            proxyPort = settings?.port, proxyUsername = settings?.username, proxyPassword = settings?.password))
    }
    suspend fun readRecentReactions(): List<String> = mutex.withLock { load().recentReactions.orEmpty() }
    suspend fun saveRecentReactions(reactions: List<String>) = mutex.withLock {
        save(load().copy(recentReactions = reactions))
    }
}
