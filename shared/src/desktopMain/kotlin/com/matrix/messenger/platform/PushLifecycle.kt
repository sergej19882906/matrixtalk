package com.matrix.messenger.platform

import com.matrix.messenger.data.push.PushRegistrationSnapshot
import com.matrix.messenger.data.push.PushRegistrationStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

actual suspend fun reconcilePushRegistration() = Unit
actual suspend fun stopPushNotifications() = Unit
actual fun observePushRegistration(): Flow<PushRegistrationSnapshot> = flowOf(
    PushRegistrationSnapshot(PushRegistrationStatus.UNAVAILABLE, reason = "android_ntfy_only"))
