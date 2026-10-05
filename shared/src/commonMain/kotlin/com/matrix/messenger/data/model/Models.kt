package com.matrix.messenger.data.model

import net.folivo.trixnity.core.model.events.m.room.EncryptedFile

data class MatrixUser(
    val userId: String,
    val displayName: String?,
    val avatarUrl: String?
) {
    val shortUserId: String = userId.substringBefore(":")
}

data class ChatRoom(
    val roomId: String,
    val name: String?,
    val topic: String?,
    val avatarUrl: String?,
    val lastMessage: LastMessage?,
    val unreadCount: Int,
    val isDirect: Boolean,
    val membersCount: Int
)

data class LastMessage(
    val senderId: String,
    val senderName: String?,
    val body: String,
    val timestamp: Long,
    val messageType: MessageType
)

enum class MessageType {
    TEXT,
    IMAGE,
    VIDEO,
    AUDIO,
    FILE,
    EMOTE,
    UNKNOWN
}

data class Message(
    val eventId: String,
    val senderId: String,
    val senderName: String?,
    val body: String,
    val timestamp: Long,
    val messageType: MessageType,
    val isMine: Boolean,
    val isEdited: Boolean,
    val isDeleted: Boolean,
    val reactions: Map<String, Int> = emptyMap(),
    val mediaUrl: String? = null,
    val encryptedFile: EncryptedFile? = null
)

sealed class LoginResult {
    data class Success(val userId: String) : LoginResult()
    data class Error(val message: String) : LoginResult()
}

sealed class ConnectionState {
    object Connected : ConnectionState()
    object Connecting : ConnectionState()
    data class Disconnected(val reason: String) : ConnectionState()
}
