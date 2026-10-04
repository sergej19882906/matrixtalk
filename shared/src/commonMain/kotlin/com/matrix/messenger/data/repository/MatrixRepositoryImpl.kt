package com.matrix.messenger.data.repository

import com.matrix.messenger.data.model.ChatRoom
import com.matrix.messenger.data.model.ConnectionState
import com.matrix.messenger.data.model.LastMessage
import com.matrix.messenger.data.model.LoginResult
import com.matrix.messenger.data.model.MatrixUser
import com.matrix.messenger.data.model.Message
import com.matrix.messenger.data.model.MessageType
import com.matrix.messenger.platform.createTrixnityRepositoriesModule
import com.matrix.messenger.platform.readFileBytes
import io.ktor.http.ContentType
import io.ktor.http.Url
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import net.folivo.trixnity.client.MatrixClient
import net.folivo.trixnity.client.flattenValues
import net.folivo.trixnity.client.fromStore
import net.folivo.trixnity.client.loginWith
import net.folivo.trixnity.client.loginWithPassword
import net.folivo.trixnity.client.media
import net.folivo.trixnity.client.media.InMemoryMediaStore
import net.folivo.trixnity.client.room
import net.folivo.trixnity.client.room.getState
import net.folivo.trixnity.client.room.message.react
import net.folivo.trixnity.client.room.message.replace
import net.folivo.trixnity.client.room.message.text
import net.folivo.trixnity.client.room.toFlowList
import net.folivo.trixnity.client.store.Room
import net.folivo.trixnity.client.store.TimelineEvent
import net.folivo.trixnity.client.store.eventId
import net.folivo.trixnity.client.store.hasBeenReplaced
import net.folivo.trixnity.client.store.isReplaced
import net.folivo.trixnity.client.store.originTimestamp
import net.folivo.trixnity.client.store.roomId
import net.folivo.trixnity.client.store.sender
import net.folivo.trixnity.client.user
import net.folivo.trixnity.clientserverapi.client.SyncState
import net.folivo.trixnity.clientserverapi.model.authentication.IdentifierType
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomAliasId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.RedactedEventContent
import net.folivo.trixnity.core.model.events.RoomEventContent
import net.folivo.trixnity.core.model.events.m.ReactionEventContent
import net.folivo.trixnity.core.model.events.m.RelatesTo
import net.folivo.trixnity.core.model.events.m.room.AudioInfo
import net.folivo.trixnity.core.model.events.m.room.FileInfo
import net.folivo.trixnity.core.model.events.m.room.ImageInfo
import net.folivo.trixnity.core.model.events.m.room.Membership
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent
import net.folivo.trixnity.core.model.events.m.room.TopicEventContent
import net.folivo.trixnity.core.model.events.m.room.VideoInfo
import net.folivo.trixnity.utils.toByteArrayFlow

class MatrixRepositoryImpl : MatrixRepository {

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _clientState = MutableStateFlow<MatrixClient?>(null)
    private var client: MatrixClient? = null
        set(value) {
            field = value
            _clientState.value = value
        }

    private var baseUrl: String = ""
    private val userNameCache = mutableMapOf<String, String>()
    private val eventRoomMap = mutableMapOf<String, String>()
    private val pageSizes = mutableMapOf<String, MutableStateFlow<Int>>()

    private val _currentUser = MutableStateFlow<MatrixUser?>(null)
    override val currentUser: Flow<MatrixUser?> = _currentUser.asStateFlow()

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected("Not connected"))
    override val connectionState: Flow<ConnectionState> = _connectionState.asStateFlow()

    override suspend fun initialize() {
        if (client != null) return
        // With the persistent store a session survives restarts: restore the client
        // from the local database (fast incremental sync) without any network call.
        try {
            val restored = MatrixClient.fromStore(
                repositoriesModule = createTrixnityRepositoriesModule(),
                mediaStore = InMemoryMediaStore(),
            ).getOrThrow()
            if (restored != null) {
                attachClient(restored, restored.baseUrl.toString().trimEnd('/'))
                return
            }
        } catch (e: Exception) {
            // corrupted or incompatible store: fall through to token-based restore
        }
        // No persisted store yet: fall back to the persisted session token.
        val session = SessionStore.read()
        if (session == null) {
            _connectionState.value = ConnectionState.Disconnected("No active session")
            return
        }
        try {
            _connectionState.value = ConnectionState.Connecting
            val restored = MatrixClient.loginWith(
                baseUrl = Url(session.homeServer),
                repositoriesModuleFactory = { createTrixnityRepositoriesModule() },
                mediaStoreFactory = { InMemoryMediaStore() },
                getLoginInfo = {
                    Result.success(
                        MatrixClient.LoginInfo(
                            userId = UserId(session.userId),
                            deviceId = session.deviceId,
                            accessToken = session.accessToken
                        )
                    )
                },
            ).getOrThrow()
            attachClient(restored, session.homeServer)
        } catch (e: Exception) {
            // Keep the session: next app start will retry (e.g. when back online).
            _connectionState.value = ConnectionState.Disconnected(
                "Не удалось восстановить сессию: ${e.message ?: "unknown error"}"
            )
        }
    }

    override suspend fun login(homeServer: String, username: String, password: String): LoginResult {
        val url = normalizeHomeServer(homeServer)
        return try {
            _connectionState.value = ConnectionState.Connecting
            val result = MatrixClient.loginWithPassword(
                baseUrl = Url(url),
                identifier = IdentifierType.User(username),
                password = password,
                initialDeviceDisplayName = "MatrixTalk",
                repositoriesModuleFactory = { createTrixnityRepositoriesModule() },
                mediaStoreFactory = { InMemoryMediaStore() },
            )
            result.fold(
                onSuccess = { c ->
                    attachClient(c, url)
                    persistSession(c, url)
                    LoginResult.Success(c.userId.full)
                },
                onFailure = { e ->
                    _connectionState.value = ConnectionState.Disconnected(e.message ?: "Login failed")
                    LoginResult.Error(e.message ?: "Ошибка входа")
                }
            )
        } catch (e: Exception) {
            _connectionState.value = ConnectionState.Disconnected(e.message ?: "Login failed")
            LoginResult.Error(e.message ?: "Ошибка входа")
        }
    }

    override suspend fun loginWithToken(homeServer: String, userId: String, accessToken: String): LoginResult {
        val url = normalizeHomeServer(homeServer)
        return try {
            _connectionState.value = ConnectionState.Connecting
            val result = MatrixClient.loginWith(
                baseUrl = Url(url),
                repositoriesModuleFactory = { createTrixnityRepositoriesModule() },
                mediaStoreFactory = { InMemoryMediaStore() },
                getLoginInfo = { api ->
                    api.accessToken.value = accessToken
                    api.authentication.whoAmI().map { whoAmI ->
                        MatrixClient.LoginInfo(
                            userId = whoAmI.userId,
                            deviceId = whoAmI.deviceId ?: "unknown",
                            accessToken = accessToken
                        )
                    }
                },
            )
            result.fold(
                onSuccess = { c ->
                    attachClient(c, url)
                    persistSession(c, url)
                    LoginResult.Success(c.userId.full)
                },
                onFailure = { e ->
                    _connectionState.value = ConnectionState.Disconnected(e.message ?: "Login failed")
                    LoginResult.Error(e.message ?: "Ошибка входа по токену")
                }
            )
        } catch (e: Exception) {
            _connectionState.value = ConnectionState.Disconnected(e.message ?: "Login failed")
            LoginResult.Error(e.message ?: "Ошибка входа по токену")
        }
    }

    override suspend fun logout() {
        val c = client
        client = null
        _currentUser.value = null
        userNameCache.clear()
        SessionStore.clear()
        if (c != null) {
            try {
                c.logout()
            } catch (e: Exception) {
                // best effort
            }
        }
        _connectionState.value = ConnectionState.Disconnected("Logged out")
    }

    override fun getRoomsFlow(): Flow<List<ChatRoom>> = _clientState.flatMapLatest { c ->
        if (c == null) {
            flowOf(emptyList())
        } else {
            c.room.getAll()
                .flattenValues()
                .mapLatest { rooms ->
                    rooms
                        .filter { it.membership == Membership.JOIN && !it.hasBeenReplaced }
                        .map { room -> room.toChatRoom(c) }
                }
        }
    }

    override fun getMessagesFlow(roomId: String): Flow<List<Message>> = _clientState.flatMapLatest { c ->
        if (c == null) {
            flowOf(emptyList())
        } else {
            val rId = RoomId(roomId)
            c.room.getLastTimelineEvents(rId)
                .toFlowList(pageSizeFor(roomId))
                .mapLatest { eventFlows ->
                    if (eventFlows.isEmpty()) {
                        flowOf(emptyList())
                    } else {
                        combine(eventFlows) { events -> events.toList() }
                            .mapLatest { events ->
                                val reactions = aggregateReactions(events)
                                events.mapNotNull { it.toMessageOrNull(c) }
                                    .map { message ->
                                        val counts = reactions[message.eventId]
                                        if (counts.isNullOrEmpty()) message
                                        else message.copy(reactions = counts)
                                    }
                            }
                    }
                }
                .flatMapLatest { it }
        }
    }

    override suspend fun loadEarlierMessages(roomId: String) {
        pageSizeFor(roomId).value += MESSAGE_PAGE_SIZE
    }

    override suspend fun canLoadMoreMessages(roomId: String): Boolean = true

    private fun pageSizeFor(roomId: String) =
        pageSizes.getOrPut(roomId) { MutableStateFlow(MESSAGE_PAGE_SIZE) }

    private fun aggregateReactions(events: List<TimelineEvent>): Map<String, Map<String, Int>> {
        val aggregated = mutableMapOf<String, MutableMap<String, MutableSet<String>>>()
        events.forEach { timelineEvent ->
            val content = timelineEvent.content?.getOrNull() as? ReactionEventContent ?: return@forEach
            val annotation = content.relatesTo as? RelatesTo.Annotation ?: return@forEach
            val key = annotation.key ?: return@forEach
            aggregated.getOrPut(annotation.eventId.full) { mutableMapOf() }
                .getOrPut(key) { mutableSetOf() }
                .add(timelineEvent.sender.full)
        }
        return aggregated.mapValues { (_, byKey) ->
            byKey.mapValues { (_, senders) -> senders.size }
        }
    }

    override suspend fun sendTextMessage(roomId: String, text: String) {
        requireClient().room.sendMessage(RoomId(roomId)) { text(text) }
    }

    override suspend fun sendFileMessage(roomId: String, filePath: String, mimeType: String, caption: String?) {
        val c = requireClient()
        val bytes = readFileBytes(filePath)
        val mediaType = mimeType.ifBlank { "application/octet-stream" }
        val contentType = runCatching { ContentType.parse(mediaType) }
            .getOrElse { ContentType.Application.OctetStream }
        val mxc = uploadBytes(c, bytes, contentType)
        val fileName = caption ?: filePath.substringAfterLast('/').substringAfterLast('\\')
        val infoMime = mediaType
        val size = bytes.size.toLong()
        val messageContent: RoomMessageEventContent = when {
            mediaType.startsWith("image/") ->
                RoomMessageEventContent.FileBased.Image(
                    body = fileName,
                    info = ImageInfo(mimeType = infoMime, size = size),
                    url = mxc
                )

            mediaType.startsWith("video/") ->
                RoomMessageEventContent.FileBased.Video(
                    body = fileName,
                    info = VideoInfo(mimeType = infoMime, size = size),
                    url = mxc
                )

            mediaType.startsWith("audio/") ->
                RoomMessageEventContent.FileBased.Audio(
                    body = fileName,
                    info = AudioInfo(mimeType = infoMime, size = size),
                    url = mxc
                )

            else ->
                RoomMessageEventContent.FileBased.File(
                    body = fileName,
                    info = FileInfo(mimeType = infoMime, size = size),
                    url = mxc
                )
        }
        c.room.sendMessage(RoomId(roomId)) { content(messageContent) }
    }

    override suspend fun getRoomInfo(roomId: String): ChatRoom? {
        val c = client ?: return null
        val rId = RoomId(roomId)
        val room = c.room.getById(rId).firstOrNull() ?: return null
        if (room.membership != Membership.JOIN) return null
        val topic = withTimeoutOrNull(STATE_FETCH_TIMEOUT_MS) {
            c.room.getState<TopicEventContent>(rId).firstOrNull()?.content?.topic
        }
        return room.toChatRoom(c).copy(topic = topic)
    }

    override suspend fun createRoom(name: String?, topic: String?, isDirect: Boolean, userIds: List<String>): String {
        return requireClient().api.room.createRoom(
            name = name,
            topic = topic,
            invite = userIds.map { UserId(it) }.toSet(),
            isDirect = isDirect
        ).getOrThrow().full
    }

    override suspend fun joinRoom(aliasOrId: String): String {
        val c = requireClient()
        val trimmed = aliasOrId.trim()
        return if (trimmed.startsWith("#")) {
            c.api.room.joinRoom(RoomAliasId(trimmed)).getOrThrow().full
        } else {
            c.api.room.joinRoom(RoomId(trimmed)).getOrThrow().full
        }
    }

    override suspend fun leaveRoom(roomId: String) {
        requireClient().api.room.leaveRoom(RoomId(roomId)).getOrThrow()
    }

    override suspend fun markRoomAsRead(roomId: String) {
        val c = requireClient()
        val rId = RoomId(roomId)
        val lastEventId = c.room.getById(rId).firstOrNull()?.lastRelevantEventId ?: return
        c.api.room.setReadMarkers(rId, fullyRead = lastEventId, read = lastEventId).getOrThrow()
    }

    override suspend fun sendTypingNotification(roomId: String, isTyping: Boolean) {
        val c = requireClient()
        c.api.room.setTyping(RoomId(roomId), c.userId, isTyping).getOrThrow()
    }

    override suspend fun sendReaction(eventId: String, reaction: String) {
        val c = requireClient()
        val roomId = eventRoomMap[eventId] ?: error("Комната для события не найдена")
        c.room.sendMessage(RoomId(roomId)) { react(EventId(eventId), reaction) }
    }

    override suspend fun editMessage(eventId: String, roomId: String, newText: String) {
        requireClient().room.sendMessage(RoomId(roomId)) {
            replace(EventId(eventId))
            text(newText)
        }
    }

    override suspend fun deleteMessage(eventId: String, roomId: String) {
        requireClient().api.room.redactEvent(RoomId(roomId), EventId(eventId), reason = "Удалено").getOrThrow()
    }

    override suspend fun uploadAvatar(filePath: String) {
        val c = requireClient()
        val bytes = readFileBytes(filePath)
        val mxc = uploadBytes(c, bytes, ContentType.Image.Any)
        c.setAvatarUrl(mxc).getOrThrow()
        _currentUser.value = _currentUser.value?.copy(avatarUrl = mxcToHttp(mxc))
    }

    override suspend fun setDisplayName(name: String) {
        requireClient().setDisplayName(name).getOrThrow()
        _currentUser.value = _currentUser.value?.copy(displayName = name)
    }

    private fun requireClient(): MatrixClient = client ?: error("Не выполнен вход")

    private suspend fun attachClient(c: MatrixClient, url: String) {
        client?.let { old ->
            try {
                old.stopSync()
            } catch (e: Exception) {
                // best effort
            }
        }
        userNameCache.clear()
        baseUrl = url.trimEnd('/')
        client = c
        _currentUser.value = MatrixUser(
            userId = c.userId.full,
            displayName = c.displayName.value,
            avatarUrl = c.avatarUrl.value?.let(::mxcToHttp)
        )
        repositoryScope.launch {
            combine(c.displayName, c.avatarUrl) { name, avatar -> name to avatar }
                .collect { (name, avatar) ->
                    _currentUser.value = _currentUser.value?.copy(
                        displayName = name,
                        avatarUrl = avatar?.let(::mxcToHttp)
                    )
                }
        }
        repositoryScope.launch {
            c.syncState.collect { state ->
                _connectionState.value = when (state) {
                    SyncState.INITIAL_SYNC, SyncState.STARTED -> ConnectionState.Connecting
                    SyncState.RUNNING -> ConnectionState.Connected
                    SyncState.ERROR -> ConnectionState.Disconnected("Ошибка синхронизации")
                    SyncState.TIMEOUT -> ConnectionState.Connected
                    SyncState.STOPPED -> ConnectionState.Disconnected("Синхронизация остановлена")
                }
            }
        }
        c.startSync()
    }

    private suspend fun persistSession(c: MatrixClient, url: String) {
        val token = c.api.accessToken.value ?: return
        SessionStore.save(
            SessionStore.Session(
                homeServer = url,
                userId = c.userId.full,
                deviceId = c.deviceId,
                accessToken = token
            )
        )
    }

    private suspend fun uploadBytes(c: MatrixClient, bytes: ByteArray, contentType: ContentType): String {
        val cacheUri = c.media.prepareUploadMedia(bytes.toByteArrayFlow(), contentType)
        return c.media.uploadMedia(cacheUri).getOrThrow()
    }

    private suspend fun Room.toChatRoom(c: MatrixClient): ChatRoom {
        val lastMsg = lastRelevantEventId?.let { eventId ->
            withTimeoutOrNull(LAST_EVENT_TIMEOUT_MS) {
                c.room.getTimelineEvent(roomId, eventId).firstOrNull()
            }?.toLastMessage(c)
        }
        return ChatRoom(
            roomId = roomId.full,
            name = computeDisplayName(),
            topic = null,
            avatarUrl = avatarUrl?.let(::mxcToHttp),
            lastMessage = lastMsg,
            unreadCount = unreadMessageCount.toInt(),
            isDirect = isDirect,
            membersCount = (name?.heroes?.size ?: 0) + (name?.otherUsersCount ?: 0) + 1
        )
    }

    private fun Room.computeDisplayName(): String {
        val holder = name
        val base = holder?.explicitName
            ?: holder?.heroes?.takeIf { it.isNotEmpty() }
                ?.joinToString(", ") { it.full.substringBefore(":").removePrefix("@") }
            ?: roomId.full
        val others = holder?.otherUsersCount ?: 0
        return if (others > 0) "$base (+$others)" else base
    }

    private suspend fun TimelineEvent.toLastMessage(c: MatrixClient): LastMessage? {
        val eventContent = content?.getOrNull() ?: return null
        val body = when (eventContent) {
            is RedactedEventContent -> "Сообщение удалено"
            is RoomMessageEventContent -> eventContent.body
            else -> return null
        }
        return LastMessage(
            senderId = sender.full,
            senderName = c.resolveSenderName(roomId, sender),
            body = body,
            timestamp = originTimestamp,
            messageType = eventContent.toMessageType()
        )
    }

    private suspend fun TimelineEvent.toMessageOrNull(c: MatrixClient): Message? {
        val rawContent = content ?: return null
        val eventContent = rawContent.getOrNull() ?: return null
        val isDeleted = eventContent is RedactedEventContent
        val messageContent = eventContent as? RoomMessageEventContent
        if (messageContent?.relatesTo is RelatesTo.Replace) return null
        val body = when {
            isDeleted -> ""
            messageContent is RoomMessageEventContent.FileBased ->
                messageContent.fileName ?: messageContent.body

            messageContent != null -> messageContent.body
            else -> return null
        }
        eventRoomMap[eventId.full] = roomId.full
        if (eventRoomMap.size > EVENT_ROOM_MAP_MAX) {
            eventRoomMap.entries.take(EVENT_ROOM_MAP_MAX / 2).forEach { eventRoomMap.remove(it.key) }
        }
        return Message(
            eventId = eventId.full,
            senderId = sender.full,
            senderName = c.resolveSenderName(roomId, sender),
            body = body,
            timestamp = originTimestamp,
            messageType = if (isDeleted) MessageType.TEXT else eventContent.toMessageType(),
            isMine = sender == c.userId,
            isEdited = !isDeleted && isReplaced,
            isDeleted = isDeleted
        )
    }

    private fun RoomEventContent.toMessageType(): MessageType = when (this) {
        is RoomMessageEventContent.TextBased.Emote -> MessageType.EMOTE
        is RoomMessageEventContent.FileBased.Image -> MessageType.IMAGE
        is RoomMessageEventContent.FileBased.Video -> MessageType.VIDEO
        is RoomMessageEventContent.FileBased.Audio -> MessageType.AUDIO
        is RoomMessageEventContent.FileBased.File -> MessageType.FILE
        is RoomMessageEventContent -> MessageType.TEXT
        else -> MessageType.UNKNOWN
    }

    private suspend fun MatrixClient.resolveSenderName(roomId: RoomId, userId: UserId): String? {
        val key = "${roomId.full}|${userId.full}"
        userNameCache[key]?.let { return it }
        val resolved = withTimeoutOrNull(RESOLVE_TIMEOUT_MS) {
            user.getById(roomId, userId).filterNotNull().first().name
        } ?: return null
        userNameCache[key] = resolved
        return resolved
    }

    private fun mxcToHttp(mxc: String): String {
        if (!mxc.startsWith("mxc://")) return mxc
        val withoutScheme = mxc.removePrefix("mxc://")
        val serverName = withoutScheme.substringBefore('/')
        val mediaId = withoutScheme.substringAfter('/', missingDelimiterValue = "")
        if (mediaId.isEmpty()) return mxc
        return "$baseUrl/_matrix/media/v3/download/$serverName/$mediaId"
    }

    private fun normalizeHomeServer(homeServer: String): String {
        var url = homeServer.trim().trimEnd('/')
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://$url"
        }
        return url
    }

    private companion object {
        const val MESSAGE_PAGE_SIZE = 50
        const val LAST_EVENT_TIMEOUT_MS = 400L
        const val RESOLVE_TIMEOUT_MS = 300L
        const val STATE_FETCH_TIMEOUT_MS = 500L
        const val EVENT_ROOM_MAP_MAX = 1000
    }
}
