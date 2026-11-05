package com.matrix.messenger.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.matrix.messenger.platform.reconcilePushRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                Log.d(TAG, "Received: ${intent.action}")
                NotificationChannels.createAll(context)
                val pending = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try { reconcilePushRegistration() } finally { pending.finish() }
                }
            }
        }
    }
}
