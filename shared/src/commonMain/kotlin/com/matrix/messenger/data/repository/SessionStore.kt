package com.matrix.messenger.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.matrix.messenger.platform.sessionStorePath
import kotlinx.coroutines.flow.first
import okio.Path.Companion.toPath

private val sessionDataStore: DataStore<Preferences> by lazy {
    PreferenceDataStoreFactory.createWithPath(produceFile = { sessionStorePath().toPath() })
}

object SessionStore {

    data class Session(
        val homeServer: String,
        val userId: String,
        val deviceId: String,
        val accessToken: String
    )

    private val homeServerKey = stringPreferencesKey("home_server")
    private val userIdKey = stringPreferencesKey("user_id")
    private val deviceIdKey = stringPreferencesKey("device_id")
    private val accessTokenKey = stringPreferencesKey("access_token")
    private val themeModeKey = stringPreferencesKey("theme_mode")
    private val notificationsEnabledKey = booleanPreferencesKey("notifications_enabled")

    suspend fun readThemeMode(): String? = sessionDataStore.data.first()[themeModeKey]

    suspend fun saveThemeMode(mode: String) {
        sessionDataStore.edit { it[themeModeKey] = mode }
    }

    /** null = never configured (treated as enabled). */
    suspend fun readNotificationsEnabled(): Boolean? = sessionDataStore.data.first()[notificationsEnabledKey]

    suspend fun saveNotificationsEnabled(enabled: Boolean) {
        sessionDataStore.edit { it[notificationsEnabledKey] = enabled }
    }

    suspend fun save(session: Session) {
        sessionDataStore.edit { prefs ->
            prefs[homeServerKey] = session.homeServer
            prefs[userIdKey] = session.userId
            prefs[deviceIdKey] = session.deviceId
            prefs[accessTokenKey] = session.accessToken
        }
    }

    suspend fun read(): Session? {
        val prefs = sessionDataStore.data.first()
        val homeServer = prefs[homeServerKey] ?: return null
        val userId = prefs[userIdKey] ?: return null
        val deviceId = prefs[deviceIdKey] ?: return null
        val accessToken = prefs[accessTokenKey] ?: return null
        return Session(homeServer, userId, deviceId, accessToken)
    }

    suspend fun clear() {
        sessionDataStore.edit { it.clear() }
    }
}
