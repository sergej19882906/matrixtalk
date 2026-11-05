package com.matrix.messenger.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper

/**
 * Foreground service (type=location) that keeps the process alive — and location
 * access allowed — while an MSC3488 live location share is running in the background.
 * The beacons themselves are broadcast by [com.matrix.messenger.data.repository.MatrixRepositoryImpl];
 * this service only holds the foreground state and shows the persistent notification.
 */
class LiveLocationService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private val stopTask = Runnable { stopSelf() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            handler.removeCallbacks(stopTask)
            stopSelf()
            return START_NOT_STICKY
        }
        isRunning = true
        val durationMs = intent?.getLongExtra(EXTRA_DURATION_MS, DEFAULT_DURATION_MS) ?: DEFAULT_DURATION_MS
        val description = intent?.getStringExtra(EXTRA_DESCRIPTION)
        startInForeground(description)
        // Match the repository broadcaster deadline: stop the service when the share expires.
        handler.removeCallbacks(stopTask)
        handler.postDelayed(stopTask, durationMs)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(stopTask)
        isRunning = false
        super.onDestroy()
    }

    private fun startInForeground(description: String?) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Трансляция геолокации",
                NotificationManager.IMPORTANCE_LOW,
            ).apply { setShowBadge(false) }
        )
        val notification = buildNotification(description)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(description: String?): Notification {
        val contentText = description?.takeIf { it.isNotBlank() }
            ?: "Ваша геолокация передаётся участникам комнаты"
        val stopIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, LiveLocationService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("Live-геолокация активна")
            .setContentText(contentText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(
                Notification.Action.Builder(
                    null,
                    "Остановить",
                    stopIntent,
                ).build()
            )
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "live_location"
        private const val NOTIFICATION_ID = 4712
        private const val ACTION_STOP = "com.matrix.messenger.action.STOP_LIVE_LOCATION"
        private const val EXTRA_DURATION_MS = "durationMs"
        private const val EXTRA_DESCRIPTION = "description"
        private const val DEFAULT_DURATION_MS = 15 * 60_000L

        @Volatile
        private var isRunning = false

        fun start(context: Context, durationMs: Long, description: String?) {
            val intent = Intent(context, LiveLocationService::class.java)
                .putExtra(EXTRA_DURATION_MS, durationMs)
                .putExtra(EXTRA_DESCRIPTION, description)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            // Skip when nothing is running: startService would spawn-and-stop an idle service.
            if (!isRunning) return
            context.startService(
                Intent(context, LiveLocationService::class.java).setAction(ACTION_STOP)
            )
        }
    }
}
