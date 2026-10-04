package com.matrix.messenger.data.repository

import com.matrix.messenger.data.model.ChatRoom
import com.matrix.messenger.data.model.ConnectionState
import com.matrix.messenger.data.model.LoginResult
import com.matrix.messenger.data.model.MatrixUser
import com.matrix.messenger.data.model.Message
import kotlinx.coroutines.flow.Flow

interface MatrixRepository {

    val currentUser: Flow<MatrixUser?>

    val connectionState: Flow<ConnectionState>

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
