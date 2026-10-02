package com.matrix.messenger.service

import android.Manifest
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.matrix.messenger.R
import com.matrix.messenger.receiver.NotificationChannels

class CallService : Service() {

    companion object {
        private const val TAG = "CallService"
        const val NOTIFICATION_ID = 1001

        fun startOutgoingCall(context: Context, callId: String, roomId: String, peerName: String, isVideo: Boolean) {
            val intent = Intent(context, CallService::class.java).apply {
                putExtra("peerName", peerName)
                putExtra("isVideo", isVideo)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun endCall(context: Context) {
            context.stopService(Intent(context, CallService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val peerName = intent?.getStringExtra("peerName") ?: "Звонок"
        val isVideo = intent?.getBooleanExtra("isVideo", false) ?: false

        val notification = NotificationCompat.Builder(this, NotificationChannels.CALLS)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentTitle(if (isVideo) "Видеозвонок" else "Аудиозвонок")
            .setContentText(peerName)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .build()

        val foregroundServiceType = computeForegroundServiceType()
        try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                foregroundServiceType
            )
        } catch (e: SecurityException) {
            Log.e(TAG, "Failed to start foreground service", e)
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun computeForegroundServiceType(): Int {
        var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED
            ) {
                type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED
            ) {
                type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            }
        } else {
            type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        }
        return type
    }
}
