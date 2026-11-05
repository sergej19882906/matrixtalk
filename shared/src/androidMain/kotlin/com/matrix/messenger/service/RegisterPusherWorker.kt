package com.matrix.messenger.service

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.matrix.messenger.data.push.AndroidPushStateStore
import com.matrix.messenger.data.push.MATRIX_PUSH_APP_ID
import com.matrix.messenger.data.push.PushRegistrationStatus
import com.matrix.messenger.data.push.ntfyGateway
import com.matrix.messenger.data.repository.MatrixRepository
import com.matrix.messenger.data.repository.SessionStore
import com.matrix.messenger.platform.configuredNtfyBaseUrl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.withLock
import org.koin.core.context.GlobalContext

const val REGISTER_PUSHER_WORK = "register_pusher"

/** Uses the Koin repository's sole Matrix client; stale generations cannot re-enable push. */
class RegisterPusherWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = AndroidPushStateStore.mutex.withLock {
        var state = AndroidPushStateStore.read(applicationContext)
        if (state.generation != inputData.getLong(KEY_GENERATION, -1)) return@withLock Result.success()
        val session = SessionStore.read() ?: return@withLock Result.success()
        if (state.ownerUserId != session.userId) return@withLock Result.success()
        try {
            val repository = GlobalContext.get().get<MatrixRepository>()
            for (endpoint in state.removals) {
                repository.removeHttpPusher(endpoint, MATRIX_PUSH_APP_ID).getOrThrow()
                state = state.copy(removals = state.removals - endpoint,
                    activeEndpoint = state.activeEndpoint?.takeUnless { it == endpoint })
                AndroidPushStateStore.save(applicationContext, state)
            }
            val endpoint = state.endpoint
            val gateway = endpoint?.let { ntfyGateway(it, configuredNtfyBaseUrl(applicationContext)) }
            val allowed = notificationsAllowed(applicationContext)
            if (!allowed || gateway == null) {
                state.activeEndpoint?.let { active ->
                    state = state.copy(removals = (state.removals + active).distinct())
                    AndroidPushStateStore.save(applicationContext, state)
                    repository.removeHttpPusher(active, MATRIX_PUSH_APP_ID).getOrThrow()
                    state = state.copy(activeEndpoint = null, removals = state.removals - active)
                }
                AndroidPushStateStore.save(applicationContext, state.copy(
                    status = if (!allowed) PushRegistrationStatus.DISABLED
                    else if (state.status == PushRegistrationStatus.PENDING || state.status == PushRegistrationStatus.ACTIVE)
                        PushRegistrationStatus.UNAVAILABLE else state.status))
                return@withLock Result.success()
            }
            repository.setHttpPusher(endpoint, gateway, MATRIX_PUSH_APP_ID, DEVICE_DISPLAY_NAME).getOrThrow()
            AndroidPushStateStore.save(applicationContext, state.copy(
                activeEndpoint = endpoint, status = PushRegistrationStatus.ACTIVE, reason = null))
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            AndroidPushStateStore.save(applicationContext, state.copy(
                status = PushRegistrationStatus.FAILED, reason = "server_registration_failed"))
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val KEY_GENERATION = "generation"
        const val DEVICE_DISPLAY_NAME = "MatrixTalk Android"
        // Compatibility for callers that previously read an unconfirmed endpoint.
        const val PREFS = "unified_push"
        const val KEY_ENDPOINT = "endpoint"
        fun endpoint(context: Context): String? = null
    }
}

fun enqueuePusherRegistration(context: Context, generation: Long) {
    val request = OneTimeWorkRequestBuilder<RegisterPusherWorker>()
        .setInputData(Data.Builder().putLong(RegisterPusherWorker.KEY_GENERATION, generation).build())
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
    WorkManager.getInstance(context).enqueueUniqueWork(REGISTER_PUSHER_WORK, ExistingWorkPolicy.REPLACE, request)
}
