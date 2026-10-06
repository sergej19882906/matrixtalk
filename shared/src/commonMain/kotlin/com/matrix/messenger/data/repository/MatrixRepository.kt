package com.matrix.messenger.data.repository

import com.matrix.messenger.data.call.CallSignalingCommand
import com.matrix.messenger.data.call.CallSignalingEvent
import com.matrix.messenger.data.model.ChatRoom
import com.matrix.messenger.data.model.ConnectionState
import com.matrix.messenger.data.model.IceServer
import com.matrix.messenger.data.model.LoginResult
import com.matrix.messenger.data.model.MatrixUser
import com.matrix.messenger.data.model.Message
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

    fun getMessagesFlow(roomId: String): Flow<List<Message>>

    suspend fun loadEarlierMessages(roomId: String)

    suspend fun canLoadMoreMessages(roomId: String): Boolean

    /** Downloads and decrypts an encrypted attachment to a local file; returns its path. */
    suspend fun resolveMediaFile(eventId: String, fileName: String): String?

    suspend fun sendTextMessage(roomId: String, text: String)

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

    suspend fun sendTypingNotification(roomId: String, isTyping: Boolean)

    suspend fun sendReaction(eventId: String, reaction: String)

    suspend fun editMessage(eventId: String, roomId: String, newText: String)

    suspend fun deleteMessage(eventId: String, roomId: String)

    suspend fun uploadAvatar(filePath: String)

    suspend fun setDisplayName(name: String)
}
