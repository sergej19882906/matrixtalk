package com.matrix.messenger.platform

import com.matrix.messenger.data.push.PushRegistrationSnapshot
import kotlinx.coroutines.flow.Flow

/** Reconcile after login, permission changes, and notification preference changes. */
expect suspend fun reconcilePushRegistration()
/** Call before logout discards the authenticated repository client. */
expect suspend fun stopPushNotifications()
expect fun observePushRegistration(): Flow<PushRegistrationSnapshot>
