package com.matrix.messenger.platform

import android.content.Context
import android.content.pm.PackageManager
import com.matrix.messenger.data.push.AndroidPushState
import com.matrix.messenger.data.push.AndroidPushStateStore
import com.matrix.messenger.data.push.MATRIX_PUSH_APP_ID
import com.matrix.messenger.data.push.MATRIX_PUSH_INSTANCE
import com.matrix.messenger.data.push.NTFY_DISTRIBUTOR
import com.matrix.messenger.data.push.PushRegistrationSnapshot
import com.matrix.messenger.data.push.PushRegistrationStatus
import com.matrix.messenger.data.push.ntfyGateway
import com.matrix.messenger.data.repository.MatrixRepository
import com.matrix.messenger.data.repository.SessionStore
import com.matrix.messenger.service.REGISTER_PUSHER_WORK
import com.matrix.messenger.service.cancelMessageNotifications
import com.matrix.messenger.service.enqueueMessageNotifications
import com.matrix.messenger.service.enqueuePusherRegistration
import com.matrix.messenger.service.notificationsAllowed
import androidx.work.WorkManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.context.GlobalContext
import org.unifiedpush.android.connector.UnifiedPush

fun configuredNtfyBaseUrl(context: Context): String = runCatching {
    context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
        .metaData?.getString("com.matrix.messenger.NTFY_BASE_URL").orEmpty()
}.getOrDefault("")

fun validPushInstance(context: Context, instance: String): Boolean =
    instance == MATRIX_PUSH_INSTANCE && UnifiedPush.getSavedDistributor(context) == NTFY_DISTRIBUTOR &&
        NTFY_DISTRIBUTOR in UnifiedPush.getDistributors(context)

actual fun observePushRegistration(): Flow<PushRegistrationSnapshot> = AndroidPushStateStore.observe(requireNotNull(appContext))

actual suspend fun reconcilePushRegistration() {
    val context = appContext ?: return
    val session = SessionStore.read()
    if (session == null || !notificationsAllowed(context)) {
        stopPushNotifications()
        return
    }
    enqueueMessageNotifications(context)
    AndroidPushStateStore.mutex.withLock {
        var state = AndroidPushStateStore.read(context)
        if (state.ownerUserId != session.userId) {
            // Retain failed old-account removals, but never send them using the new account.
            val deferred = state.deferredRemovals.toMutableMap()
            state.ownerUserId?.let { owner ->
                val keys = (deferred[owner].orEmpty() + state.removals + listOfNotNull(state.activeEndpoint, state.endpoint)).distinct()
                if (keys.isNotEmpty()) deferred[owner] = keys
            }
            val removals = deferred.remove(session.userId).orEmpty()
            state = AndroidPushState(generation = state.generation + 1, ownerUserId = session.userId,
                removals = removals, deferredRemovals = deferred)
        }
        // Migrate the previous unconfirmed pusher key for deletion, never to ACTIVE.
        val legacyPrefs = context.getSharedPreferences("unified_push", Context.MODE_PRIVATE)
        legacyPrefs.getString("endpoint", null)?.let { legacyEndpoint ->
            state = state.copy(removals = (state.removals + legacyEndpoint).distinct())
            AndroidPushStateStore.save(context, state)
            legacyPrefs.edit().remove("endpoint").apply()
            runCatching { UnifiedPush.unregisterApp(context) } // Old SDK default instance.
        }
        val configured = configuredNtfyBaseUrl(context)
        val supported = ntfyGateway("${configured.trimEnd('/')}/upconfiguration?up=1", configured) != null &&
            NTFY_DISTRIBUTOR in UnifiedPush.getDistributors(context)
        if (!supported) {
            state = state.copy(generation = state.generation + 1, endpoint = null,
                removals = (state.removals + listOfNotNull(state.activeEndpoint, state.endpoint)).distinct(),
                status = PushRegistrationStatus.UNAVAILABLE, reason = "configure_ntfy_gateway_and_distributor")
            AndroidPushStateStore.save(context, state)
            enqueuePusherRegistration(context, state.generation)
            return@withLock
        }
        if (UnifiedPush.getSavedDistributor(context) != NTFY_DISTRIBUTOR) {
            UnifiedPush.saveDistributor(context, NTFY_DISTRIBUTOR)
        }
        state = state.copy(generation = state.generation + 1,
            status = PushRegistrationStatus.PENDING, reason = null)
        AndroidPushStateStore.save(context, state)
        if (state.endpoint != null || state.removals.isNotEmpty()) enqueuePusherRegistration(context, state.generation)
        try {
            UnifiedPush.registerApp(context, instance = MATRIX_PUSH_INSTANCE)
        } catch (_: Exception) {
            AndroidPushStateStore.save(context, state.copy(
                status = PushRegistrationStatus.FAILED, reason = "distributor_registration_failed"))
        }
    }
}

/** Also handles a distributor unregistration; keep keys until removal is confirmed. */
suspend fun invalidatePushRegistration(context: Context, status: PushRegistrationStatus, reason: String?) {
    AndroidPushStateStore.mutex.withLock {
        val state = AndroidPushStateStore.read(context)
        val updated = state.copy(generation = state.generation + 1, endpoint = null,
            removals = (state.removals + listOfNotNull(state.activeEndpoint, state.endpoint)).distinct(),
            status = status, reason = reason)
        AndroidPushStateStore.save(context, updated)
        WorkManager.getInstance(context).cancelUniqueWork(REGISTER_PUSHER_WORK)
        enqueuePusherRegistration(context, updated.generation)
    }
}

actual suspend fun stopPushNotifications() {
    val context = appContext ?: return
    cancelMessageNotifications(context)
    // Invalidate first; an old registration job can never subsequently mark ACTIVE.
    invalidatePushRegistration(context, PushRegistrationStatus.DISABLED, "notifications_disabled")
    UnifiedPush.unregisterApp(context, instance = MATRIX_PUSH_INSTANCE)
    AndroidPushStateStore.mutex.withLock {
        var state = AndroidPushStateStore.read(context)
        val session = SessionStore.read()
        if (session?.userId != state.ownerUserId || state.removals.isEmpty()) return@withLock
        try {
            val repository = GlobalContext.get().get<MatrixRepository>()
            val removed = withTimeoutOrNull(5000) {
                for (endpoint in state.removals) {
                    repository.removeHttpPusher(endpoint, MATRIX_PUSH_APP_ID).getOrThrow()
                    state = state.copy(removals = state.removals - endpoint,
                        activeEndpoint = state.activeEndpoint?.takeUnless { it == endpoint })
                    AndroidPushStateStore.save(context, state)
                }
                true
            } ?: false
            if (!removed) AndroidPushStateStore.save(context, state.copy(
                status = PushRegistrationStatus.FAILED, reason = "server_removal_pending"))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            AndroidPushStateStore.save(context, state.copy(
                status = PushRegistrationStatus.FAILED, reason = "server_removal_pending"))
            // Authenticated retries remain queued; no success status is fabricated.
        }
    }
}
