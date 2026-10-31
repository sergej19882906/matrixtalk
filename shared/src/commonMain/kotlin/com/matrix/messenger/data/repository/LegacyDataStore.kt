package com.matrix.messenger.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.matrix.messenger.platform.sessionStorePath
import kotlinx.coroutines.flow.first
import okio.Path.Companion.toPath

/**
 * Read-only access to the pre-v1.14.4 DataStore-backed session file, kept
 * solely for migration. Never write through it. Runs in runCatching(Throwable)
 * at the call site: on runtimes without sun.misc.Unsafe this fails fast and
 * the user simply logs in again.
 */
internal object LegacyDataStore {

    data class LegacySession(
        val homeServer: String,
        val userId: String,
        val deviceId: String,
        val accessToken: String
    )

    private val dataStore: DataStore<Preferences> by lazy {
        PreferenceDataStoreFactory.createWithPath(produceFile = { sessionStorePath().toPath() })
    }

    private val homeServerKey = stringPreferencesKey("home_server")
    private val userIdKey = stringPreferencesKey("user_id")
    private val deviceIdKey = stringPreferencesKey("device_id")
    private val accessTokenKey = stringPreferencesKey("access_token")

    suspend fun readSession(): LegacySession? {
        val prefs = dataStore.data.first()
        val homeServer = prefs[homeServerKey] ?: return null
        val userId = prefs[userIdKey] ?: return null
        val deviceId = prefs[deviceIdKey] ?: return null
        val accessToken = prefs[accessTokenKey] ?: return null
        return LegacySession(homeServer, userId, deviceId, accessToken)
    }
}
