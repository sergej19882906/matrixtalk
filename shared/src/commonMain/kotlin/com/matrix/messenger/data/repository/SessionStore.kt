package com.matrix.messenger.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
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
