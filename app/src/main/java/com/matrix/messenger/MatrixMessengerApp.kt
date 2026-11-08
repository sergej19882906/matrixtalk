package com.matrix.messenger

import android.app.Application
import androidx.work.Configuration
import com.matrix.messenger.di.sharedModule
import com.matrix.messenger.media.setupImageLoader
import com.matrix.messenger.platform.appContext
import com.matrix.messenger.receiver.NotificationChannels
import com.matrix.messenger.platform.reconcilePushRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
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
        // Uncaught crashes land in files/logs/crash.log so they can be shared
        // from Settings -> "Поделиться журналами".
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val dir = java.io.File(filesDir, "logs").apply { mkdirs() }
                val stamp = java.time.LocalDateTime.now().toString()
                val header = stamp + " [" + thread.name + "]" + "\n"
                val body = android.util.Log.getStackTraceString(throwable) + "\n\n"
                dir.resolve("crash.log").appendText(header + body)
            }
            previous?.uncaughtException(thread, throwable)
        }
        setupImageLoader()
        startKoin {
            androidContext(this@MatrixMessengerApp)
            modules(sharedModule)
        }
        NotificationChannels.createAll(this)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            reconcilePushRegistration()
        }
    }
}
