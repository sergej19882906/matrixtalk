package com.matrix.messenger.service

import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.matrix.messenger.data.model.RoomNotificationLevel
import com.matrix.messenger.data.push.AndroidPushStateStore
import com.matrix.messenger.data.push.PushTarget
import com.matrix.messenger.data.push.shouldNotify
import com.matrix.messenger.data.repository.MatrixRepository
import com.matrix.messenger.data.repository.SessionStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.withLock
import org.koin.core.context.GlobalContext
import java.util.concurrent.TimeUnit

private const val CHANNEL_MESSAGES = "message_channel"
const val MESSAGE_SYNC_WORK = "push_background_sync"
private const val PERIODIC_WORK = "message_notifications"

class MessageNotificationWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        // Periodic work only feeds the same unique chain as UnifiedPush; it never opens a client.
        if (inputData.getBoolean(KEY_TICK, false)) {
            enqueueMessageSync(applicationContext)
            return Result.success()
        }
        try {
            if (!notificationsAllowed(applicationContext)) return Result.success()
            val session = SessionStore.read() ?: return Result.success()
            val roomId = inputData.getString(KEY_ROOM)
            val eventId = inputData.getString(KEY_EVENT)
            val target = if (roomId != null && eventId != null) PushTarget(roomId, eventId) else null
            val repository = GlobalContext.get().get<MatrixRepository>()
            repository.backgroundSync().getOrThrow()
            val events = repository.getBackgroundNotifications(roomId, eventId).getOrThrow()
            val muted = SessionStore.readMutedRooms().toSet()
            val levels = SessionStore.readRoomNotificationLevels()
            val defaultLevel = SessionStore.readDefaultNotificationLevel()
            for (event in events) {
                val level = RoomNotificationLevel.fromStored(levels[event.roomId] ?: defaultLevel)
                if (!shouldNotify(event, session.userId, notificationsAllowed(applicationContext), level,
                        event.roomId in muted, target)) continue
                AndroidPushStateStore.mutex.withLock {
                    if (SessionStore.read()?.userId != session.userId || !notificationsAllowed(applicationContext)) return@withLock
                    val currentLevel = RoomNotificationLevel.fromStored(
                        SessionStore.readRoomNotificationLevels()[event.roomId] ?: SessionStore.readDefaultNotificationLevel())
                    if (!shouldNotify(event, session.userId, true, currentLevel,
                            event.roomId in SessionStore.readMutedRooms(), target)) return@withLock
                    val state = AndroidPushStateStore.read(applicationContext)
                    val dedupKey = "${session.userId}|${event.roomId}|${event.eventId}"
                    if (dedupKey in state.delivered) return@withLock
                    val id = event.roomId.hashCode()
                    val launchIntent = applicationContext.packageManager
                        .getLaunchIntentForPackage(applicationContext.packageName) ?: return@withLock
                    launchIntent.putExtra(KEY_ROOM, event.roomId)
                    val pendingIntent = PendingIntent.getActivity(applicationContext, id, launchIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                    val notification = NotificationCompat.Builder(applicationContext, CHANNEL_MESSAGES)
                        .setSmallIcon(android.R.drawable.ic_dialog_email)
                        .setContentTitle(event.title).setContentText(event.body)
                        .setContentIntent(pendingIntent).setAutoCancel(true)
                        .setPriority(NotificationCompat.PRIORITY_HIGH).build()
                    // Do not advance dedup on decryption/fetch/permission/notify failure.
                    NotificationManagerCompat.from(applicationContext).notify(event.roomId, id, notification)
                    AndroidPushStateStore.save(applicationContext, state.copy(
                        delivered = (state.delivered + dedupKey).takeLast(512)))
                }
            }
            return Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: SecurityException) {
            return Result.success()
        } catch (_: Exception) {
            return if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val KEY_ROOM = "room_id"
        const val KEY_EVENT = "event_id"
        private const val KEY_TICK = "periodic_tick"
        fun tickData(): Data = Data.Builder().putBoolean(KEY_TICK, true).build()
    }
}

suspend fun notificationsAllowed(context: Context): Boolean =
    (SessionStore.readNotificationsEnabled() ?: true) &&
        NotificationManagerCompat.from(context).areNotificationsEnabled()

fun enqueueMessageSync(context: Context, target: PushTarget? = null) {
    val request = OneTimeWorkRequestBuilder<MessageNotificationWorker>()
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        .setInputData(Data.Builder().putString(MessageNotificationWorker.KEY_ROOM, target?.roomId)
            .putString(MessageNotificationWorker.KEY_EVENT, target?.eventId).build()).build()
    WorkManager.getInstance(context).enqueueUniqueWork(MESSAGE_SYNC_WORK, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
}

fun enqueueMessageNotifications(context: Context) {
    val request = PeriodicWorkRequestBuilder<MessageNotificationWorker>(15, TimeUnit.MINUTES)
        .setInputData(MessageNotificationWorker.tickData())
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
    WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC_WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
}

fun cancelMessageNotifications(context: Context) {
    WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK)
    WorkManager.getInstance(context).cancelUniqueWork(MESSAGE_SYNC_WORK)
    NotificationManagerCompat.from(context).cancelAll()
}
