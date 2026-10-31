package com.matrix.messenger.data.repository

import com.matrix.messenger.data.call.CallSignalingCommand
import com.matrix.messenger.data.call.CallSignalingEvent
import com.matrix.messenger.data.model.ChatRoom
import com.matrix.messenger.data.model.ConnectionState
import com.matrix.messenger.data.model.DeviceInfo
import com.matrix.messenger.data.model.IceServer
import com.matrix.messenger.data.model.LoginResult
import com.matrix.messenger.data.model.MatrixUser
import com.matrix.messenger.data.model.Message
import com.matrix.messenger.data.model.PublicRoomItem
import com.matrix.messenger.data.model.SearchResultItem
import com.matrix.messenger.data.model.VerificationUiState
import kotlinx.coroutines.flow.Flow

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

    suspend fun sendTextMessage(roomId: String, text: String)

    /** Sends a reply: text with m.in_reply_to relation to [eventId]. */
    suspend fun replyToMessage(roomId: String, eventId: String, text: String)

    suspend fun sendFileMessage(
        roomId: String,
        filePath: String,
        mimeType: String,
        caption: String? = null
    )

    suspend fun getRoomInfo(roomId: String): ChatRoom?

    suspend fun createRoom(
        name: String?,
        topic: String?,
        isDirect: Boolean,
        userIds: List<String>
    ): String

    suspend fun joinRoom(aliasOrId: String): String

    suspend fun leaveRoom(roomId: String)

    suspend fun markRoomAsRead(roomId: String)

    /** Pinned messages of the room (m.room.pinned_events). */
    fun getPinnedMessagesFlow(roomId: String): Flow<List<Message>>

    /** Pins or unpins a message. Requires sufficient power level in the room. */
    suspend fun setPinnedMessage(roomId: String, eventId: String, pinned: Boolean)

    suspend fun sendTypingNotification(roomId: String, isTyping: Boolean)

    /** Display names of users currently typing in the room (excluding self); auto-expires. */
    fun getTypingUsersFlow(roomId: String): Flow<List<String>>

    suspend fun sendReaction(eventId: String, reaction: String)

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
