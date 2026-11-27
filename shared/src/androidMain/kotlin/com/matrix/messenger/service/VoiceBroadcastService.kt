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
import android.os.IBinder

/**
 * Foreground service (type=microphone) that keeps the MSC3401 voice broadcast
 * recording allowed while the app is backgrounded. The chunk recorder/sender
 * lives in MatrixRepositoryImpl; this service only holds the foreground state.
 */
class VoiceBroadcastService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        isRunning = true
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Голосовая трансляция", NotificationManager.IMPORTANCE_LOW)
                .apply { setShowBadge(false) }
        )
        val stopIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, VoiceBroadcastService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Голосовая трансляция идёт")
            .setContentText("Ваша трансляция записывается и отправляется участникам")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(Notification.Action.Builder(null, "Остановить", stopIntent).build())
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "voice_broadcast"
        private const val NOTIFICATION_ID = 4713
        private const val ACTION_STOP = "com.matrix.messenger.action.STOP_VOICE_BROADCAST"

        @Volatile
        private var isRunning = false

        fun start(context: Context, description: String?) {
            val intent = Intent(context, VoiceBroadcastService::class.java)
                .putExtra("description", description)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            if (!isRunning) return
            context.startService(
                Intent(context, VoiceBroadcastService::class.java).setAction(ACTION_STOP)
            )
        }
    }
}
