package com.matrix.messenger.data.repository

import com.matrix.messenger.data.call.CallSignalingCommand
import com.matrix.messenger.data.call.CallSignalingEvent
import com.matrix.messenger.data.model.ChatRoom
import com.matrix.messenger.data.model.ConnectionState
import com.matrix.messenger.data.model.DeviceInfo
import com.matrix.messenger.data.model.IceServer
import com.matrix.messenger.data.model.RoomMemberInfo
import com.matrix.messenger.data.model.KeyBackupStatus
import com.matrix.messenger.data.model.PresenceInfo
import com.matrix.messenger.data.model.ReadReceipt
import com.matrix.messenger.data.model.RoomNotificationLevel
import com.matrix.messenger.data.model.RoomSettingsInfo
import com.matrix.messenger.data.model.RoomTrust
import com.matrix.messenger.data.model.SelfVerificationInfo
import com.matrix.messenger.data.model.LoginResult
import com.matrix.messenger.data.model.MatrixUser
import com.matrix.messenger.data.model.Message
import com.matrix.messenger.data.model.PublicRoomItem
import com.matrix.messenger.data.model.SearchResultItem
import com.matrix.messenger.data.model.VerificationUiState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface MatrixRepository {

    val currentUser: Flow<MatrixUser?>

    val connectionState: Flow<ConnectionState>

    /** VoIP signaling events (m.call.*) from other users, live timeline only. */
    val callEvents: Flow<CallSignalingEvent>

    suspend fun sendCallEvent(roomId: String, command: CallSignalingCommand)

    /** TURN/STUN credentials from the homeserver (empty if unsupported or unreachable). */
    suspend fun getTurnServers(): List<IceServer>

    /** The other member's user id for a direct (1:1) room, null for group rooms. */
    suspend fun resolveDirectChatPeerId(roomId: String): String?

    /** Server-side full-text search over message bodies. */
    suspend fun searchMessages(query: String, limit: Int = 30): List<SearchResultItem>

    /** Public room directory of the homeserver. */
    suspend fun searchPublicRooms(query: String?, limit: Int = 30): List<PublicRoomItem>

    /** Joined spaces (rooms with m.space type). */
    fun getSpacesFlow(): Flow<List<ChatRoom>>

    /** Rooms of a space via the hierarchy endpoint. */
    suspend fun getSpaceChildren(spaceId: String): List<PublicRoomItem>

    /** Current user's tags of a room (m.favourite / m.lowpriority). */
    suspend fun getRoomTags(roomId: String): Set<String>

    /** Adds or removes the m.favourite tag. Returns the new favorite state. */
    suspend fun toggleFavorite(roomId: String): Boolean

    /** Invites a user to a room. */
    suspend fun inviteUserToRoom(roomId: String, userId: String)

    /** Creates an MSC3381 poll in the room. */
    suspend fun sendPoll(roomId: String, question: String, answers: List<String>)

    /** Votes in a poll (replaces the previous vote of this device/user). */
    suspend fun sendPollVote(roomId: String, pollEventId: String, answerIds: List<String>)
    /** Closes a poll the user created (m.poll.end). */
    suspend fun sendPollEnd(roomId: String, pollEventId: String)
    /** matrix.to link for sharing the room (canonical alias when present). */
    suspend fun getRoomShareLink(roomId: String): String?

    suspend fun initialize()

    suspend fun login(
        homeServer: String,
        username: String,
        password: String
    ): LoginResult

    suspend fun loginWithToken(
        homeServer: String,
        userId: String,
        accessToken: String
    ): LoginResult

    suspend fun logout()

    fun getRoomsFlow(): Flow<List<ChatRoom>>

    /** Rooms the user is invited to (membership=invite). */
    fun getInvitesFlow(): Flow<List<ChatRoom>>

    suspend fun searchUsers(query: String): List<MatrixUser>

    fun getMessagesFlow(roomId: String): Flow<List<Message>>

    suspend fun loadEarlierMessages(roomId: String)

    suspend fun canLoadMoreMessages(roomId: String): Boolean

    /** Downloads and decrypts an encrypted attachment to a local file; returns its path. */
    suspend fun resolveMediaFile(eventId: String, fileName: String): String?

    suspend fun sendTextMessage(roomId: String, text: String, formattedBody: String? = null)

    /** Sends an m.emote message ("/me" style action). */
    suspend fun sendEmoteMessage(roomId: String, text: String)

    /** Sends a reply: text with m.in_reply_to relation to [eventId];
     * keeps the thread when [threadRootId] is set. */
    suspend fun replyToMessage(roomId: String, eventId: String, text: String, threadRootId: String? = null)

    suspend fun sendFileMessage(
        roomId: String,
        filePath: String,
        mimeType: String,
        caption: String? = null
    )

    suspend fun getRoomInfo(roomId: String): ChatRoom?

    /** Room state for the room settings screen (reactive). */
    fun getRoomSettingsFlow(roomId: String): Flow<RoomSettingsInfo>

    /** Joined members of the room with power levels (reactive). */
    fun getRoomMembersFlow(roomId: String): Flow<List<RoomMemberInfo>>

    suspend fun setRoomName(roomId: String, name: String)
    suspend fun setRoomTopic(roomId: String, topic: String)
    suspend fun setRoomAvatar(roomId: String, filePath: String)
    suspend fun kickUser(roomId: String, userId: String)
    suspend fun banUser(roomId: String, userId: String, reason: String? = null)
    suspend fun unbanUser(roomId: String, userId: String)
    suspend fun setUserPowerLevel(roomId: String, userId: String, level: Long)
    suspend fun setRoomMuted(roomId: String, muted: Boolean)
    suspend fun isRoomMuted(roomId: String): Boolean

    /** Rooms with notifications disabled (reactive; server push rule + local cache). */
    fun mutedRoomsFlow(): StateFlow<Set<String>>

    /** Element style room notification levels (all / mentions only / mute). */
    suspend fun getRoomNotificationLevel(roomId: String): RoomNotificationLevel
    suspend fun setRoomNotificationLevel(roomId: String, level: RoomNotificationLevel)
    /** Default level applied to newly joined rooms (settings). */
    suspend fun setDefaultNotificationLevel(level: RoomNotificationLevel)

    /** Server-side key backup status; null when no backup exists. */
    suspend fun getKeyBackupStatus(): KeyBackupStatus?

    /** Presence snapshot of a user (online / offline / unavailable). */
    suspend fun getPresence(userId: String): PresenceInfo?

    /** Sets the current user's presence (online when entering a chat). */
    suspend fun setOwnPresence(online: Boolean)

    /** Sends an m.sticker image event (Element X style sticker). */
    suspend fun sendSticker(roomId: String, filePath: String, mimeType: String)

    /** Sends an m.location message with the given point. */
    suspend fun sendLocation(roomId: String, latitude: Double, longitude: Double, accuracyMeters: Double? = null)

    /** Sends a voice message (m.audio) with an MSC3245 waveform. */
    suspend fun sendVoiceMessage(roomId: String, filePath: String, durationMs: Long?, waveform: List<Int>)

    /** Read receipts of other members: eventId -> readers (reactive). */
    fun getReadReceiptsFlow(roomId: String): Flow<Map<String, List<ReadReceipt>>>

    /** Room encryption + member-trust summary for the shield indicator. */
    fun getRoomTrustFlow(roomId: String): Flow<RoomTrust>

    /** Whether THIS device session is verified (cross-signed); null = unknown. */
    fun getOwnSessionVerifiedFlow(): Flow<Boolean?>

    /** Self-verification state: is THIS session verified and how it can be fixed. */
    val selfVerification: Flow<SelfVerificationInfo>

    /** Verifies THIS session with the account recovery key (or passphrase). */
    suspend fun verifySelfWithSecret(secret: String): Result<Unit>

    /** Exports all known megolm room keys as an Element-compatible encrypted file. */
    suspend fun exportRoomKeys(passphrase: String): ByteArray

    /** Imports an Element-format key export; returns the number of imported sessions. */
    suspend fun importRoomKeys(bytes: ByteArray, passphrase: String): Int

    suspend fun createRoom(
        name: String?,
        topic: String?,
        isDirect: Boolean,
        userIds: List<String>,
        isEncrypted: Boolean = false
    ): String

    suspend fun joinRoom(aliasOrId: String): String

    suspend fun leaveRoom(roomId: String)

    /** Marks the room unread/read via the m.marked_unread account data (Element X). */
    suspend fun setRoomMarkedUnread(roomId: String, unread: Boolean)

    suspend fun markRoomAsRead(roomId: String)

    /** Pinned messages of the room (m.room.pinned_events). */
    fun getPinnedMessagesFlow(roomId: String): Flow<List<Message>>

    /** Pins or unpins a message. Requires sufficient power level in the room. */
    suspend fun setPinnedMessage(roomId: String, eventId: String, pinned: Boolean)

    suspend fun sendTypingNotification(roomId: String, isTyping: Boolean)

    /** Display names of users currently typing in the room (excluding self); auto-expires. */
    fun getTypingUsersFlow(roomId: String): Flow<List<String>>

    suspend fun sendReaction(eventId: String, reaction: String)
    /** Removes the current user's reaction (emoji) from a message. */
    suspend fun unreact(roomId: String, eventId: String, reaction: String)

    suspend fun editMessage(eventId: String, roomId: String, newText: String)

    suspend fun deleteMessage(eventId: String, roomId: String)

    suspend fun uploadAvatar(filePath: String, mimeType: String)

    suspend fun setDisplayName(name: String)

    // --- Devices / security ---

    /** All devices (sessions) of the account. */
    suspend fun getDevices(): List<DeviceInfo>

    suspend fun renameDevice(deviceId: String, newName: String)

    /**
     * Deletes a device. If the server requires interactive auth (UIA), [passwordProvider]
     * is invoked to ask the user for their password; returning null cancels the operation.
     */
    suspend fun deleteDevice(deviceId: String, passwordProvider: suspend () -> String? = { null })

    /** The device id of the current session, null when logged out. */
    suspend fun currentDeviceId(): String?

    /** The user id of the current session, null when logged out. */
    suspend fun currentUserId(): String?

    // --- Key backup / verification ---

    /** Current room-key backup version, null when backup is not set up. */
    fun getKeyBackupVersionFlow(): Flow<String?>

    /**
     * Bootstraps cross-signing and creates a room-key backup.
     * Returns the recovery key — shown to the user exactly once.
     * [passwordProvider] is used when the server requires UIA; null cancels.
     */
    suspend fun bootstrapKeyBackup(passwordProvider: suspend () -> String? = { null }): String

    /** Current state of an interactive device verification (own or incoming). */
    val verificationState: Flow<VerificationUiState>

    /** Starts an emoji-SAS verification with another device of the account. */
    suspend fun startDeviceVerification(deviceId: String)


    /** Confirms or rejects the currently shown emoji/decimal comparison. */
    suspend fun confirmVerification(matches: Boolean)

    suspend fun cancelVerification()
}
