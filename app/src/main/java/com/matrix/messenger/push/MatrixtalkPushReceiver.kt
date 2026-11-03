package com.matrix.messenger.push

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.matrix.messenger.service.MessageNotificationWorker
import com.matrix.messenger.service.RegisterPusherWorker
import org.unifiedpush.android.connector.MessagingReceiver

/**
 * UnifiedPush entry point: the distributor (e.g. ntfy) delivers Matrix push
 * payloads here the moment the homeserver sends them. Both handlers defer the
 * real work to WorkManager so nothing runs inside the broadcast lifetime.
 */
class MatrixtalkPushReceiver : MessagingReceiver() {

    override fun onMessage(context: Context, message: ByteArray, instance: String) {
        val request = OneTimeWorkRequestBuilder<MessageNotificationWorker>()
            .build()
        // APPEND_OR_REPLACE: consecutive pushes sync one after another, never in
        // parallel (two clients on the same Realm store would fight).
        WorkManager.getInstance(context).enqueueUniqueWork(
            PUSH_SYNC_WORK,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request
        )
    }

    override fun onNewEndpoint(context: Context, endpoint: String, instance: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ENDPOINT, endpoint)
            .apply()
        val request = OneTimeWorkRequestBuilder<RegisterPusherWorker>()
            .setInputData(androidx.work.Data.Builder().putString(KEY_ENDPOINT, endpoint).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            REGISTER_PUSHER_WORK,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    override fun onRegistrationFailed(context: Context, instance: String) {
        // The periodic 15-minute WorkManager sync stays as the fallback.
    }

    override fun onUnregistered(context: Context, instance: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_ENDPOINT)
            .apply()
    }

    companion object {
        const val PREFS = "unified_push"
        const val KEY_ENDPOINT = "endpoint"
        const val PUSH_SYNC_WORK = "push_background_sync"
        const val REGISTER_PUSHER_WORK = "register_pusher"

        fun savedEndpoint(context: Context): String? =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_ENDPOINT, null)
    }
}
