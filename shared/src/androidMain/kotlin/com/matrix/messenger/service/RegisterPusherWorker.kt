package com.matrix.messenger.service

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.matrix.messenger.data.repository.SessionStore
import com.matrix.messenger.platform.createTrixnityRepositoriesModule
import io.ktor.http.Url
import kotlinx.coroutines.flow.first
import net.folivo.trixnity.client.MatrixClient
import net.folivo.trixnity.client.loginWith
import net.folivo.trixnity.client.media.InMemoryMediaStore
import net.folivo.trixnity.clientserverapi.model.push.PusherData
import net.folivo.trixnity.clientserverapi.model.push.SetPushers
import net.folivo.trixnity.core.model.UserId

/**
 * Registers this device as an HTTP pusher on the homeserver with the
 * UnifiedPush endpoint as the gateway URL, so Synapse delivers message
 * pushes straight to the distributor (ntfy / NextPush).
 */
class RegisterPusherWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val session = SessionStore.read() ?: return Result.success()
        val endpoint = inputData.getString(KEY_ENDPOINT)
            ?: applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_ENDPOINT, null)
            ?: return Result.success()
        if (!NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()) {
            // notifications disabled: a pusher would only drain the battery
            return Result.success()
        }
        val proxy = runCatching { SessionStore.readProxySettings() }.getOrNull()
        val client = try {
            MatrixClient.loginWith(
                baseUrl = Url(session.homeServer),
                repositoriesModuleFactory = { createTrixnityRepositoriesModule() },
                mediaStoreFactory = { InMemoryMediaStore() },
                getLoginInfo = {
                    kotlin.Result.success(
                        MatrixClient.LoginInfo(
                            userId = UserId(session.userId),
                            deviceId = session.deviceId,
                            accessToken = session.accessToken
                        )
                    )
                },
                configuration = {
                    httpClientFactory = com.matrix.messenger.data.repository.createProxyHttpClientFactory(proxy)
                },
            ).getOrThrow()
        } catch (e: Exception) {
            return if (runAttemptCount < 3) Result.retry() else Result.success()
        }
        return try {
            client.api.push.setPushers(
                SetPushers.Request(
                    APP_ID,
                    endpoint,
                    true, // HTTP pusher
                    PusherData(url = endpoint, format = null),
                    APP_DISPLAY_NAME,
                    DEVICE_DISPLAY_NAME,
                    "ru",
                    PROFILE_TAG,
                    "false" // append=false: replace any previous pusher for this profile
                )
            ).getOrThrow()
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.success()
        } finally {
            try {
                client.cancelSync()
            } catch (e: Exception) {
            }
        }
    }

    companion object {
        const val KEY_ENDPOINT = "endpoint"
        const val PREFS = "unified_push"

        fun endpoint(context: Context): String? =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_ENDPOINT, null)
        const val APP_ID = "com.matrix.messenger"
        const val APP_DISPLAY_NAME = "MatrixTalk"
        const val DEVICE_DISPLAY_NAME = "MatrixTalk Android"
        const val PROFILE_TAG = "mtrxtlk_1"
    }
}
