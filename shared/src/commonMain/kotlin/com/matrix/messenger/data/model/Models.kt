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
    val membersCount: Int,
    val isFavorite: Boolean = false,
    val isLowPriority: Boolean = false
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
    POLL,
    CALL,
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
    /** Resolved https avatar of the sender (room member), null when unavailable. */
    val senderAvatarUrl: String? = null,
    val reactions: Map<String, Int> = emptyMap(),
    val mediaUrl: String? = null,
    val encryptedFile: EncryptedFile? = null,
    val replyTo: ReplyInfo? = null,
    /** HTML body (org.matrix.custom.html) when the sender used rich formatting. */
    val formattedBody: String? = null,
    /** Root event id when the message belongs to a thread (m.thread). */
    val threadRootId: String? = null,
    /** MSC3381 poll payload when this message is a poll. */
    val poll: com.matrix.messenger.data.poll.PollInfo? = null,
    /** Aggregated poll votes: answerId -> number of voters. */
    val pollVotes: Map<String, Int> = emptyMap(),
    val pollTotalVoters: Int = 0,
    val pollMyAnswers: List<String> = emptyList()
)

/** Quoted original of a reply (m.in_reply_to). */
data class ReplyInfo(
    val eventId: String,
    val senderName: String?,
    val body: String,
    val messageType: MessageType
)

/** A session/device of the user's account (GET /devices). */
data class DeviceInfo(
    val deviceId: String,
    val displayName: String?,
    val lastSeenIp: String?,
    val lastSeenTs: Long?
)

/** UI states of an interactive device verification (emoji SAS). */
sealed interface VerificationUiState {
    data object Idle : VerificationUiState
    data object Waiting : VerificationUiState
    data class Compare(
        val theirUserId: String,
        val theirDeviceId: String?,
        val emojis: List<Pair<Int, String>>,
        val decimal: List<Int>
    ) : VerificationUiState
    data object Done : VerificationUiState
    data class Cancelled(val reason: String?) : VerificationUiState
}

/** A single hit from server-side message search. */
data class SearchResultItem(
    val eventId: String,
    val roomId: String,
    val roomName: String?,
    val senderName: String?,
    val body: String,
    val timestamp: Long
)

/** A public room from the server directory. */
data class PublicRoomItem(
    val roomId: String,
    val name: String?,
    val alias: String?,
    val topic: String?,
    val avatarUrl: String?,
    val joinedMembers: Long,
    val joinRule: String
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
