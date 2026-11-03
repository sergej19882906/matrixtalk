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
    val isLowPriority: Boolean = false,
    val isEncrypted: Boolean = false,
    /** Room manually marked unread via account data (m.marked_unread). */
    val isMarkedUnread: Boolean = false
)

/**
 * Trust summary of a chat room for the shield indicator.
 * [verified] = null when unknown (members not loaded yet or room too large).
 */
/**
 * Self-verification state of THIS session (cross-signing trust).
 */
data class SelfVerificationInfo(
    /** true = session cross-signed and verified; false = needs verification; null = unknown. */
    val verified: Boolean?,
    val canVerifyWithRecoveryKey: Boolean = false,
    val canVerifyWithPassphrase: Boolean = false,
)

data class RoomTrust(
    val isEncrypted: Boolean,
    val verified: Boolean?
)

/** Editable room state for the room settings screen. */
data class RoomSettingsInfo(
    val roomId: String,
    val name: String?,
    val topic: String?,
    val avatarUrl: String?,
    val isEncrypted: Boolean,
    val memberCount: Int,
    val canEdit: Boolean,
    /** Power level of the current user in this room (Element X style permissions). */
    val myPowerLevel: Long = 0,
    val canInvite: Boolean = false,
    val canKick: Boolean = false,
    val canBan: Boolean = false,
    val canChangePowerLevels: Boolean = false
)

/** Status of the server-side key backup (megolm). */
data class KeyBackupStatus(
    val version: String,
    val algorithm: String?
)

/** Presence snapshot of a user (Element X header style). */
data class PresenceInfo(
    val presence: String,
    /** Minutes since the user was last active, null when unknown. */
    val lastActiveAgoMinutes: Long?,
    val statusMessage: String?
)

/** A read receipt resolved to a member for the chat timeline. */
data class ReadReceipt(
    val userId: String,
    val name: String?,
    val avatarUrl: String?
)

/** Room notification level, mirroring Element (all / mentions only / mute). */
enum class RoomNotificationLevel {
    ALL_MESSAGES,
    MENTIONS_ONLY,
    MUTE;

    companion object {
        fun fromStored(value: String?): RoomNotificationLevel =
            entries.firstOrNull { it.name == value } ?: ALL_MESSAGES
    }
}

/** A room member for the participant list. */
data class RoomMemberInfo(
    val userId: String,
    val name: String?,
    val avatarUrl: String?,
    val powerLevel: Long,
    val isSelf: Boolean,
    /** Raw m.room.member membership value: join / ban / leave / invite. */
    val membership: String = "join"
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
    STICKER,
    LOCATION,
    VIDEO,
    AUDIO,
    FILE,
    EMOTE,
    POLL,
    CALL,
    VERIFICATION_REQUEST,
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
    /** Requesting device id for in-room key verification requests (m.key.verification.request). */
    val verificationDeviceId: String? = null,
    val reactions: Map<String, Int> = emptyMap(),
    /** Reaction keys the current user has added to this message (toggle support). */
    val myReactions: Set<String> = emptySet(),
    /** Parsed geo point for m.location messages. */
    val location: GeoLocation? = null,
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
