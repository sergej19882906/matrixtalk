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
        runCatching { enqueueMessageNotifications(this) }
    }
}
