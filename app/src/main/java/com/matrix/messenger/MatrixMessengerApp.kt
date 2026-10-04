package com.matrix.messenger

import android.app.Application
import com.matrix.messenger.di.sharedModule
import com.matrix.messenger.platform.appContext
import com.matrix.messenger.receiver.NotificationChannels
import com.matrix.messenger.service.enqueueMessageNotifications
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class MatrixMessengerApp : Application() {

    override fun onCreate() {
        super.onCreate()
        appContext = this
        startKoin {
            androidContext(this@MatrixMessengerApp)
            modules(sharedModule)
        }
        NotificationChannels.createAll(this)
        enqueueMessageNotifications(this)
    }
}
