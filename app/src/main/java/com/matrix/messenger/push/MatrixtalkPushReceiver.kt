package com.matrix.messenger.push

import android.content.Context
import android.content.Intent
import com.matrix.messenger.data.push.AndroidPushStateStore
import com.matrix.messenger.data.push.PushRegistrationStatus
import com.matrix.messenger.data.push.ntfyGateway
import com.matrix.messenger.data.push.parseMatrixPush
import com.matrix.messenger.platform.configuredNtfyBaseUrl
import com.matrix.messenger.platform.invalidatePushRegistration
import com.matrix.messenger.platform.validPushInstance
import com.matrix.messenger.service.enqueueMessageSync
import com.matrix.messenger.service.enqueuePusherRegistration
import com.matrix.messenger.service.notificationsAllowed
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import org.unifiedpush.android.connector.MessagingReceiver

/** Exported for the distributor; SDK onReceive checks its secret registration token.
 * Do not bypass super.onReceive or trust a caller-supplied instance/package extra.
 */
class MatrixtalkPushReceiver : MessagingReceiver() {
    private val scope = CoroutineScope(Dispatchers.IO)
    private var pendingResult: android.content.BroadcastReceiver.PendingResult? = null

    override fun onReceive(context: Context, intent: Intent) {
        // Keep token -> SDK instance validation, and reject callbacks from removed distributors.
        pendingResult = goAsync()
        try {
            super.onReceive(context, intent)
        } finally {
            pendingResult?.finish()
            pendingResult = null
        }
    }

    private fun defer(block: suspend () -> Unit) {
        val pending = pendingResult
        pendingResult = null
        scope.launch {
            try { block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { /* Worker/lifecycle retry can reconcile persisted state. */ }
            finally { pending?.finish() }
        }
    }

    override fun onMessage(context: Context, message: ByteArray, instance: String) {
        if (!validPushInstance(context, instance)) return
        defer {
            if (!notificationsAllowed(context)) return@defer
            val state = AndroidPushStateStore.read(context)
            if (state.status != PushRegistrationStatus.ACTIVE) return@defer
            val endpoint = state.activeEndpoint ?: return@defer
            val target = parseMatrixPush(message, endpoint) ?: return@defer
            enqueueMessageSync(context, target)
        }
    }

    override fun onNewEndpoint(context: Context, endpoint: String, instance: String) {
        if (!validPushInstance(context, instance)) return
        defer {
            if (!notificationsAllowed(context)) {
                invalidatePushRegistration(context, PushRegistrationStatus.DISABLED, "notifications_disabled")
                return@defer
            }
            if (ntfyGateway(endpoint, configuredNtfyBaseUrl(context)) == null) {
                invalidatePushRegistration(context, PushRegistrationStatus.UNAVAILABLE, "unsupported_endpoint")
                return@defer
            }
            AndroidPushStateStore.mutex.withLock {
                val state = AndroidPushStateStore.read(context)
                val updated = state.copy(generation = state.generation + 1, endpoint = endpoint,
                    removals = (state.removals + listOfNotNull(state.activeEndpoint?.takeUnless { it == endpoint },
                        state.endpoint?.takeUnless { it == endpoint })).distinct(),
                    status = PushRegistrationStatus.PENDING, reason = null)
                AndroidPushStateStore.save(context, updated)
                enqueuePusherRegistration(context, updated.generation)
            }
        }
    }

    override fun onRegistrationFailed(context: Context, instance: String) {
        if (!validPushInstance(context, instance)) return
        defer { invalidatePushRegistration(context, PushRegistrationStatus.FAILED, "distributor_registration_failed") }
    }

    override fun onUnregistered(context: Context, instance: String) {
        if (!validPushInstance(context, instance)) return
        defer { invalidatePushRegistration(context, PushRegistrationStatus.DISABLED, "distributor_unregistered") }
    }
}
