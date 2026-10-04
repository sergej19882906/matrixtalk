package com.matrix.messenger.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.matrix.messenger.data.repository.SessionStore
import com.matrix.messenger.platform.createTrixnityRepositoriesModule
import io.ktor.http.Url
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import net.folivo.trixnity.client.MatrixClient
import net.folivo.trixnity.client.flattenValues
import net.folivo.trixnity.client.loginWith
import net.folivo.trixnity.client.media.InMemoryMediaStore
import net.folivo.trixnity.client.room
import net.folivo.trixnity.client.store.sender
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.m.room.Membership
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent
import java.util.concurrent.TimeUnit

private const val PREFS = "message_notifications"
private const val CHANNEL_MESSAGES = "message_channel"

class MessageNotificationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()) {
            return Result.success()
        }
        val prefs = applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val session = SessionStore.read() ?: return Result.success()
        val client = try {
            MatrixClient.loginWith(
                baseUrl = Url(session.homeServer),
                repositoriesModuleFactory = { createTrixnityRepositoriesModule() },
                mediaStoreFactory = { InMemoryMediaStore() },
                getLoginInfo = {
                    kotlin.Result.success(
                        MatrixClient.LoginInfo(
                            userId = UserId(session.userId),
                            deviceId = session.deviceId,
                            accessToken = session.accessToken
                        )
                    )
                },
            ).getOrThrow()
        } catch (e: Exception) {
            return if (runAttemptCount < 3) Result.retry() else Result.success()
        }
        try {
            client.syncOnce()
            val rooms = client.room.getAll().flattenValues().first()
            rooms
                .filter { it.membership == Membership.JOIN && it.unreadMessageCount > 0 }
                .forEach { room ->
                    val lastTimestamp =
                        room.lastRelevantEventTimestamp?.toEpochMilliseconds() ?: return@forEach
                    val roomKey = room.roomId.full
                    if (lastTimestamp <= prefs.getLong(roomKey, 0L)) return@forEach

                    val event = withTimeoutOrNull(EVENT_RESOLVE_TIMEOUT_MS) {
                        room.lastRelevantEventId?.let { eventId ->
                            client.room.getTimelineEvent(room.roomId, eventId).first()
                        }
                    } ?: return@forEach

                    if (event.sender == client.userId) {
                        prefs.edit().putLong(roomKey, lastTimestamp).apply()
                        return@forEach
                    }
                    val content = event.content?.getOrNull() as? RoomMessageEventContent
                    prefs.edit().putLong(roomKey, lastTimestamp).apply()
                    if (content != null) {
                        showNotification(
                            id = roomKey.hashCode(),
                            title = room.name?.explicitName ?: room.roomId.full,
                            body = content.body
                        )
                    }
                }
        } catch (e: Exception) {
            return if (runAttemptCount < 3) Result.retry() else Result.success()
        } finally {
            try {
                client.cancelSync()
            } catch (e: Exception) {
                // best effort
            }
        }
        return Result.success()
    }

    private fun showNotification(id: Int, title: String, body: String) {
        val launchIntent: Intent? =
            applicationContext.packageManager.getLaunchIntentForPackage(applicationContext.packageName)
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            id,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_MESSAGES)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        try {
            NotificationManagerCompat.from(applicationContext).notify(id, notification)
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS not granted
        }
    }

    private companion object {
        const val EVENT_RESOLVE_TIMEOUT_MS = 1500L
    }
}

fun enqueueMessageNotifications(context: Context) {
    val request = PeriodicWorkRequestBuilder<MessageNotificationWorker>(15, TimeUnit.MINUTES)
        .setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
        )
        .build()
    WorkManager.getInstance(context)
        .enqueueUniquePeriodicWork(
            "message_notifications",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
}
