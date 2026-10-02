package com.matrix.messenger.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

object NotificationChannels {

    const val CALLS = "call_channel"
    const val MESSAGES = "message_channel"
    const val SYNC = "sync_channel"

    fun createAll(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val callChannel = NotificationChannel(
            CALLS,
            "Звонки",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Уведомления о входящих звонках"
            enableVibration(true)
            setShowBadge(true)
        }

        val messageChannel = NotificationChannel(
            MESSAGES,
            "Сообщения",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Уведомления о новых сообщениях"
            enableVibration(true)
            setShowBadge(true)
        }

        val syncChannel = NotificationChannel(
            SYNC,
            "Синхронизация",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Фоновая синхронизация Matrix"
            setShowBadge(false)
        }

        manager.createNotificationChannels(listOf(callChannel, messageChannel, syncChannel))
    }
}
