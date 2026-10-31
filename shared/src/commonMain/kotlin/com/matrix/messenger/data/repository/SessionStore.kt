package com.matrix.messenger.data.repository

import com.matrix.messenger.platform.readFileBytes
import com.matrix.messenger.platform.sessionStorePath
import com.matrix.messenger.platform.writeFileBytes
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * App settings storage: session credentials, theme mode, notification toggle.
 *
 * Plain JSON file instead of DataStore: the protobuf layer inside DataStore
 * hard-references sun.misc.Unsafe, which the jlink runtime of desktop
 * installers does not ship (NoClassDefFoundError at runtime). A tiny prefs
 * file needs none of that.
 */
object SessionStore {

    data class Session(
        val homeServer: String,
        val userId: String,
        val deviceId: String,
        val accessToken: String
    )

    @Serializable
    private data class StoredPrefs(
        val homeServer: String? = null,
        val userId: String? = null,
        val deviceId: String? = null,
        val accessToken: String? = null,
        val themeMode: String? = null,
        val notificationsEnabled: Boolean? = null
    )

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val mutex = Mutex()
    private var cache: StoredPrefs? = null

    private fun filePath(): String =
        sessionStorePath().removeSuffix("session.preferences_pb") + "settings.json"

    private suspend fun load(): StoredPrefs {
        cache?.let { return it }
        val prefs = runCatching {
            json.decodeFromString<StoredPrefs>(readFileBytes(filePath()).decodeToString())
        }.getOrNull() ?: readLegacy() ?: StoredPrefs()
        cache = prefs
        return prefs
    }

    /** Best-effort migration from the old DataStore file; any failure (including
     *  LinkageError from the missing Unsafe class) means "no legacy session". */
    private suspend fun readLegacy(): StoredPrefs? = runCatching {
        val legacy = LegacyDataStore.readSession()
        legacy?.let {
            StoredPrefs(
                homeServer = it.homeServer,
                userId = it.userId,
                deviceId = it.deviceId,
                accessToken = it.accessToken
            )
        }
    }.getOrNull()

    private suspend fun save(prefs: StoredPrefs) {
        cache = prefs
        runCatching {
            writeFileBytes(filePath(), json.encodeToString(StoredPrefs.serializer(), prefs).encodeToByteArray())
        }
    }

    suspend fun save(session: Session) = mutex.withLock {
        save(load().copy(homeServer = session.homeServer, userId = session.userId, deviceId = session.deviceId, accessToken = session.accessToken))
    }

    suspend fun read(): Session? = mutex.withLock {
        load().let { p ->
            if (p.homeServer != null && p.userId != null && p.deviceId != null && p.accessToken != null) {
                Session(p.homeServer, p.userId, p.deviceId, p.accessToken)
            } else null
        }
    }

    suspend fun clear() = mutex.withLock {
        save(StoredPrefs())
    }

    suspend fun readThemeMode(): String? = mutex.withLock { load().themeMode }

    suspend fun saveThemeMode(mode: String) = mutex.withLock {
        save(load().copy(themeMode = mode))
    }

    /** null = never configured (treated as enabled). */
    suspend fun readNotificationsEnabled(): Boolean? = mutex.withLock { load().notificationsEnabled }

    suspend fun saveNotificationsEnabled(enabled: Boolean) = mutex.withLock {
        save(load().copy(notificationsEnabled = enabled))
    }
}
