package com.matrix.messenger.data.push

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val Context.pushDataStore by preferencesDataStore(name = "matrix_push")

@Serializable
data class AndroidPushState(
    val generation: Long = 0,
    val status: PushRegistrationStatus = PushRegistrationStatus.UNAVAILABLE,
    val reason: String? = null,
    val endpoint: String? = null,
    val activeEndpoint: String? = null,
    val ownerUserId: String? = null,
    val removals: List<String> = emptyList(),
    val deferredRemovals: Map<String, List<String>> = emptyMap(),
    val delivered: List<String> = emptyList(),
)

/** One process-wide DataStore and lock; endpoint secrets are never diagnostic text. */
object AndroidPushStateStore {
    val mutex = Mutex()
    private val key = stringPreferencesKey("registration")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun decode(value: String?): AndroidPushState = value?.let {
        runCatching { json.decodeFromString<AndroidPushState>(it) }.getOrNull()
    } ?: AndroidPushState()

    suspend fun read(context: Context): AndroidPushState = decode(context.pushDataStore.data.first()[key])

    suspend fun save(context: Context, state: AndroidPushState) {
        context.pushDataStore.edit { it[key] = json.encodeToString(AndroidPushState.serializer(), state) }
    }

    fun observe(context: Context): Flow<PushRegistrationSnapshot> = context.pushDataStore.data.map {
        decode(it[key]).let { state -> PushRegistrationSnapshot(state.status, state.generation, state.reason) }
    }
}
