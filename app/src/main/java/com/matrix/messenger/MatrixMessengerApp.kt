package com.matrix.messenger

import android.app.Application
import androidx.work.Configuration
import com.matrix.messenger.di.sharedModule
import com.matrix.messenger.media.setupImageLoader
import com.matrix.messenger.platform.appContext
import com.matrix.messenger.receiver.NotificationChannels
import com.matrix.messenger.service.enqueueMessageNotifications
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

// Configuration.Provider is required because the manifest disables the default
// WorkManagerInitializer: without it WorkManager.getInstance() crashes at startup.
class MatrixMessengerApp : Application(), Configuration.Provider {

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()

    override fun onCreate() {
        super.onCreate()
        appContext = this
        setupImageLoader()
        startKoin {
            androidContext(this@MatrixMessengerApp)
            modules(sharedModule)
        }
        NotificationChannels.createAll(this)
        kotlinx.coroutines.runBlocking {
            val enabled = com.matrix.messenger.data.repository.SessionStore.readNotificationsEnabled() ?: true
            if (enabled) {
                runCatching { enqueueMessageNotifications(this@MatrixMessengerApp) }
                runCatching { registerUnifiedPush(this@MatrixMessengerApp) }
            }
        }
    }

    /**
     * Registers with an installed UnifiedPush distributor (ntfy, NextPush, …).
     * The new endpoint lands in MatrixtalkPushService.onNewEndpoint, which
     * registers the device as an HTTP pusher on the homeserver. Without a
     * distributor the periodic WorkManager sync stays the fallback.
     */
    private fun registerUnifiedPush(context: android.content.Context) {
        val distributors = org.unifiedpush.android.connector.UnifiedPush.getDistributors(context)
        if (distributors.isEmpty()) return
        val current = org.unifiedpush.android.connector.UnifiedPush.getSavedDistributor(context)
        if (current.isNullOrEmpty()) {
            org.unifiedpush.android.connector.UnifiedPush.saveDistributor(context, distributors.first())
        }
        org.unifiedpush.android.connector.UnifiedPush.registerApp(context)
    }
}
