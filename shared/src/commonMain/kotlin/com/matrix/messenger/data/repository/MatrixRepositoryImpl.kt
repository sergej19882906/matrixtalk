package com.matrix.messenger.data.repository

import com.matrix.messenger.data.call.CallInviteEventContent
import com.matrix.messenger.data.bridge.displayName
import com.matrix.messenger.data.call.CallSignalingCommand
import com.matrix.messenger.data.call.callEventContentSerializerMappingsModule
import com.matrix.messenger.data.crypto.ExportedSession
import com.matrix.messenger.data.crypto.decodeRoomKeysExport
import com.matrix.messenger.data.crypto.encodeRoomKeysExport
import com.matrix.messenger.data.call.CallSignalingEvent
import com.matrix.messenger.data.call.buildCallContent
import com.matrix.messenger.data.call.parseCallEvent
import com.matrix.messenger.data.model.ChatRoom
import com.matrix.messenger.data.model.RoomTrust
import com.matrix.messenger.data.model.SelfVerificationInfo
import com.matrix.messenger.data.model.DeviceInfo
import com.matrix.messenger.data.model.ConnectionState
import com.matrix.messenger.data.model.IceServer
import com.matrix.messenger.data.model.LastMessage
import com.matrix.messenger.data.model.LoginResult
import com.matrix.messenger.data.model.MatrixUser
import com.matrix.messenger.data.model.Message
import com.matrix.messenger.data.model.MessageType
import com.matrix.messenger.data.model.PublicRoomItem
import com.matrix.messenger.data.model.ReplyInfo
import com.matrix.messenger.data.model.SearchResultItem
import com.matrix.messenger.data.model.VerificationUiState
import com.matrix.messenger.media.MediaBytesProvider
import com.matrix.messenger.platform.attachmentsDir
import com.matrix.messenger.platform.createTrixnityRepositoriesModule
import com.matrix.messenger.platform.logError
import com.matrix.messenger.platform.readFileBytes
import com.matrix.messenger.platform.writeFileBytes
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Url
import io.ktor.http.contentType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.serialization.json.JsonPrimitive
import net.folivo.trixnity.core.model.events.m.MarkedUnreadEventContent
import net.folivo.trixnity.core.model.events.m.Presence
import net.folivo.trixnity.core.model.events.EventType
import net.folivo.trixnity.core.model.events.StateEventContent
import net.folivo.trixnity.core.model.push.PushAction
import com.matrix.messenger.data.model.KeyBackupStatus
import com.matrix.messenger.data.model.PresenceInfo
import com.matrix.messenger.data.model.ReadReceipt
import com.matrix.messenger.data.model.RoomNotificationLevel
import kotlin.reflect.KClass
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.MatrixClient
import net.folivo.trixnity.client.createDefaultTrixnityModules
import net.folivo.trixnity.client.flattenValues
import net.folivo.trixnity.client.fromStore
import net.folivo.trixnity.client.key.KeyBackupService
import net.folivo.trixnity.client.key.DeviceTrustLevel
import net.folivo.trixnity.crypto.olm.StoredInboundMegolmSession
import net.folivo.trixnity.olm.*
import net.folivo.trixnity.client.key.KeyService
import net.folivo.trixnity.client.verification.ActiveUserVerification
import net.folivo.trixnity.client.verification.SelfVerificationMethod
import net.folivo.trixnity.client.store.AccountStore
import net.folivo.trixnity.client.store.KeyStore
import net.folivo.trixnity.client.store.OlmCryptoStore
import net.folivo.trixnity.client.key.UserTrustLevel
import org.koin.core.qualifier.named
import net.folivo.trixnity.client.loginWith
import net.folivo.trixnity.client.loginWithPassword
import net.folivo.trixnity.client.media
import net.folivo.trixnity.client.media.InMemoryMediaStore
import net.folivo.trixnity.client.room
import net.folivo.trixnity.client.room.getState
import net.folivo.trixnity.core.model.events.ClientEvent
import net.folivo.trixnity.client.store.RoomUser
import net.folivo.trixnity.client.serverDiscovery
import net.folivo.trixnity.client.room.message.emote
import net.folivo.trixnity.client.room.message.react
import net.folivo.trixnity.client.room.message.replace
import net.folivo.trixnity.client.room.message.reply
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
import net.folivo.trixnity.client.user.UserService
import net.folivo.trixnity.client.verification.ActiveSasVerificationMethod
import net.folivo.trixnity.client.verification.ActiveSasVerificationState
import net.folivo.trixnity.client.verification.ActiveVerificationState
import net.folivo.trixnity.client.verification.VerificationService
import net.folivo.trixnity.clientserverapi.client.SyncState
import net.folivo.trixnity.clientserverapi.client.UIA
import net.folivo.trixnity.clientserverapi.model.authentication.IdentifierType
import net.folivo.trixnity.clientserverapi.model.rooms.GetPublicRoomsWithFilter
import net.folivo.trixnity.clientserverapi.model.uia.AuthenticationRequest
import net.folivo.trixnity.clientserverapi.model.uia.AuthenticationType
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomAliasId
import net.folivo.trixnity.core.model.keys.Key
import net.folivo.trixnity.core.model.keys.KeyAlgorithm
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.RedactedEventContent
import net.folivo.trixnity.core.model.events.MessageEventContent
import net.folivo.trixnity.core.model.events.RoomEventContent
import net.folivo.trixnity.core.model.events.UnknownEventContent
import net.folivo.trixnity.core.model.events.m.ReactionEventContent
import net.folivo.trixnity.core.model.events.m.RelatesTo
import net.folivo.trixnity.core.model.events.InitialStateEvent
import net.folivo.trixnity.core.model.events.m.room.AudioInfo
import net.folivo.trixnity.core.model.events.m.room.EncryptedFile
import net.folivo.trixnity.core.model.events.m.room.EncryptionEventContent
import net.folivo.trixnity.core.model.events.m.room.FileInfo
import net.folivo.trixnity.core.model.events.m.room.ImageInfo
import net.folivo.trixnity.core.model.events.m.room.Membership
import net.folivo.trixnity.core.model.events.m.room.PowerLevelsEventContent
import net.folivo.trixnity.core.model.events.m.room.PinnedEventsEventContent
import net.folivo.trixnity.core.model.events.m.room.NameEventContent
import net.folivo.trixnity.core.model.events.m.room.AvatarEventContent
import net.folivo.trixnity.clientserverapi.model.push.SetPushRule
import net.folivo.trixnity.core.model.push.PushRuleKind
import com.matrix.messenger.data.model.RoomMemberInfo
import com.matrix.messenger.data.model.RoomSettingsInfo
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent
import net.folivo.trixnity.core.model.events.m.room.TopicEventContent
import net.folivo.trixnity.core.model.events.m.room.VideoInfo
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationCancelEventContent
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationMethod
import net.folivo.trixnity.crypto.core.encryptAes256Ctr
import net.folivo.trixnity.crypto.core.sha256
import net.folivo.trixnity.utils.toByteArray
import net.folivo.trixnity.crypto.core.encryptAes256Ctr
import net.folivo.trixnity.crypto.core.sha256
import net.folivo.trixnity.utils.toByteArrayFlow

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class MatrixRepositoryImpl : MatrixRepository {

    private val apiJson = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _clientState = MutableStateFlow<MatrixClient?>(null)
    private var client: MatrixClient? = null
        set(value) {
            field = value
            _clientState.value = value
        }

    private var baseUrl: String = ""
    private val userNameCache = mutableMapOf<String, String>()
    private val userAvatarCache = mutableMapOf<String, String>()
    private val eventRoomMap = mutableMapOf<String, String>()
    private val pageSizes = mutableMapOf<String, MutableStateFlow<Int>>()
    private val encryptedFiles = mutableMapOf<String, net.folivo.trixnity.core.model.events.m.room.EncryptedFile>()
    private val mediaMxcCache = mutableMapOf<String, String>()

    private val _currentUser = MutableStateFlow<MatrixUser?>(null)
    override val currentUser: Flow<MatrixUser?> = _currentUser.asStateFlow()

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected("Not connected"))
    override val connectionState: Flow<ConnectionState> = _connectionState.asStateFlow()

    override val callEvents: Flow<CallSignalingEvent> = _clientState.flatMapLatest { c ->
        if (c == null) {
            flowOf()
        } else {
            c.room.getTimelineEventsFromNowOn().mapNotNull { timelineEvent ->
                if (client !== c) return@mapNotNull null
                if (timelineEvent.sender == c.userId) return@mapNotNull null
                val content = timelineEvent.content?.getOrNull() as? MessageEventContent
                    ?: return@mapNotNull null
                parseCallEvent(
                    content,
                    timelineEvent.roomId.full,
                    timelineEvent.sender.full,
                    ourPartyId = c.deviceId
                )?.also { signalEvent ->
                    logError("Call", "signaling received: ${signalEvent::class.simpleName} room=${timelineEvent.roomId.full} from=${timelineEvent.sender.full}", null)
                }
            }
        }
    }

    override suspend fun sendCallEvent(roomId: String, command: CallSignalingCommand) {
        val c = requireClient()
        logError("Call", "signaling send: ${command::class.simpleName} call=${command.callId} room=$roomId", null)
        // In encrypted rooms Element DROPS plaintext call signaling, so m.call.*
        // must go through the megolm pipeline (room.sendMessage). The typed
        // m.call.* serializer mappings (see CallEventContents) make the Realm
        // outbox able to persist them.
        c.room.sendMessage(RoomId(roomId)) {
            content(buildCallContent(command, partyId = c.deviceId))
        }
    }

    override suspend fun resolveDirectChatPeerId(roomId: String): String? {
        val c = client ?: return null
        val room = c.room.getById(RoomId(roomId)).firstOrNull() ?: return null
        return room.name?.heroes?.firstOrNull { it != c.userId }?.full
    }

    override suspend fun searchMessages(query: String, limit: Int): List<SearchResultItem> {
        val c = requireClient()
        val response = c.api.server.search(
            net.folivo.trixnity.clientserverapi.model.server.Search.Request(
                searchCategories = net.folivo.trixnity.clientserverapi.model.server.Search.Request.Categories(
                    roomEvents = net.folivo.trixnity.clientserverapi.model.server.Search.Request.Categories.RoomEventsCriteria(
                        searchTerm = query,
                        filter = net.folivo.trixnity.clientserverapi.model.users.Filters.RoomFilter.RoomEventFilter(
                            types = setOf("m.room.message"),
                            limit = limit.toLong()
                        ),
                        keys = setOf("content.body"),
                        orderBy = net.folivo.trixnity.clientserverapi.model.server.Search.Request.Categories.RoomEventsCriteria.Ordering.RECENT
                    )
                )
            )
        ).getOrThrow()
        val roomEvents = response.searchCategories.roomEvents ?: return emptyList()
        return roomEvents.results.orEmpty().mapNotNull { hit ->
            val event = hit.result as? net.folivo.trixnity.core.model.events.ClientEvent.RoomEvent.MessageEvent<*>
                ?: return@mapNotNull null
            val content = event.content as? RoomMessageEventContent ?: return@mapNotNull null
            val rId = RoomId(event.roomId.full)
            SearchResultItem(
                eventId = event.id.full,
                roomId = event.roomId.full,
                roomName = c.room.getById(rId).firstOrNull()?.let { room ->
                    room.name?.explicitName
                        ?: room.name?.heroes?.takeIf { it.isNotEmpty() }
                            ?.joinToString(", ") { it.full.substringBefore(":").removePrefix("@") }
                        ?: event.roomId.full
                },
                senderName = hit.context?.profileInfo?.get(event.sender)?.displayName
                    ?: c.resolveSenderName(rId, event.sender),
                body = content.body.stripReplyFallback().take(SEARCH_SNIPPET_MAX),
                timestamp = event.originTimestamp
            )
        }.take(limit)
    }

    override suspend fun searchPublicRooms(query: String?, limit: Int): List<PublicRoomItem> {
        val c = requireClient()
        val response = c.api.room.getPublicRooms(
            limit = limit.toLong(),
            filter = GetPublicRoomsWithFilter.Request.Filter(
                genericSearchTerm = query?.trim()?.takeIf { it.isNotEmpty() }
            )
        ).getOrThrow()
        return response.chunk.map { it.toPublicRoomItem() }
    }

    override fun getSpacesFlow(): Flow<List<ChatRoom>> = _clientState.flatMapLatest { c ->
        if (c == null) {
            flowOf(emptyList())
        } else {
            c.room.getAll()
                .flattenValues()
                .mapLatest { rooms ->
                    rooms
                        .filter {
                            it.membership == Membership.JOIN && !it.hasBeenReplaced &&
                                it.createEventContent?.type is net.folivo.trixnity.core.model.events.m.room.CreateEventContent.RoomType.Space
                        }
                        .map { room -> room.toChatRoom(c) }
                }
        }
    }

    override suspend fun getSpaceChildren(spaceId: String): List<PublicRoomItem> {
        val c = requireClient()
        val response = c.api.room.getHierarchy(
            roomId = RoomId(spaceId),
            from = "",
            limit = 100,
            maxDepth = 1
        ).getOrThrow()
        return response.rooms
            .filter { it.roomId.full != spaceId }
            .map { it.toPublicRoomItem() }
    }

    private fun net.folivo.trixnity.clientserverapi.model.rooms.GetHierarchy.Response.PublicRoomsChunk.toPublicRoomItem() =
        PublicRoomItem(
            roomId = roomId.full,
            name = name,
            alias = canonicalAlias?.full,
            topic = topic,
            avatarUrl = avatarUrl,
            joinedMembers = joinedMembersCount,
            joinRule = joinRule.toString()
        )

    private fun net.folivo.trixnity.clientserverapi.model.rooms.GetPublicRoomsResponse.PublicRoomsChunk.toPublicRoomItem() =
        PublicRoomItem(
            roomId = roomId.full,
            name = name,
            alias = canonicalAlias?.full,
            topic = topic,
            avatarUrl = avatarUrl,
            joinedMembers = joinedMembersCount,
            joinRule = joinRule.toString()
        )

    @Serializable
    private data class TurnServerResponse(
        val username: String? = null,
        val password: String? = null,
        val uris: List<String> = emptyList(),
        val ttl: Long = 0,
    )

    @Serializable
    private data class UserDirectorySearchRequest(
        val search_term: String,
        val limit: Int = 20
    )

    @Serializable
    private data class UserDirectorySearchResponse(
        val results: List<UserDirectoryEntry> = emptyList()
    )

    @Serializable
    private data class UserDirectoryEntry(
        val user_id: String,
        val display_name: String? = null,
        val avatar_url: String? = null
    )

    private var turnServersCache: Pair<Long, List<IceServer>>? = null

    override suspend fun getTurnServers(): List<IceServer> {
        val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        turnServersCache?.let { (expiresAt, servers) ->
            if (now < expiresAt) return servers
        }
        val c = client ?: return emptyList()
        return runCatching {
            // Trixnity 4.9.2 has no VoIP API client; baseClient already carries base URL and auth.
            val response: TurnServerResponse =
                c.api.httpClient.baseClient.get("/_matrix/client/v3/voip/turnServer").body()
            val servers = if (response.uris.isEmpty()) {
                emptyList()
            } else {
                listOf(IceServer(response.uris, response.username, response.password))
            }
            val ttlMs = (if (response.ttl > 0) response.ttl else DEFAULT_TURN_TTL_SECONDS) * 1000
            turnServersCache = (now + ttlMs) to servers
            servers
        }.getOrDefault(emptyList())
    }

    override suspend fun initialize() {
        if (client != null) return
        // With the persistent store a session survives restarts: restore the client
        // from the local database (fast incremental sync) without any network call.
        try {
            val proxy = runCatching { SessionStore.readProxySettings() }.getOrNull()
            val restored = MatrixClient.fromStore(
                repositoriesModule = createTrixnityRepositoriesModule(),
                mediaStore = InMemoryMediaStore(),
                configuration = {
                    httpClientFactory = createProxyHttpClientFactory(proxy)
                    modulesFactory = {
                        createDefaultTrixnityModules() + listOf(
                            com.matrix.messenger.data.bridge.customEventContentSerializerMappingsModule
                        )
                    }
                },
            ).getOrThrow()
            if (restored != null) {
                attachClient(restored, restored.baseUrl.toString().trimEnd('/'))
                return
            }
        } catch (t: Throwable) {
            // corrupted store or missing native library: fall through to token-based restore
            logError("MatrixRepository", "fromStore restore failed, falling back to token", t)
        }
        // No persisted store yet: fall back to the persisted session token.
        _mutedRooms.value = runCatching {
            val levels = SessionStore.readRoomNotificationLevels()
            if (levels.isEmpty()) {
                SessionStore.readMutedRooms().toSet()
            } else {
                levels.filterValues { it == RoomNotificationLevel.MUTE.name }.keys
            }
        }.getOrDefault(emptySet())
        val session = SessionStore.read()
        if (session == null) {
            _connectionState.value = ConnectionState.Disconnected("No active session")
            return
        }
        try {
            _connectionState.value = ConnectionState.Connecting
            val proxy = runCatching { SessionStore.readProxySettings() }.getOrNull()
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
                configuration = {
                    httpClientFactory = createProxyHttpClientFactory(proxy)
                    modulesFactory = {
                        createDefaultTrixnityModules() + listOf(
                            com.matrix.messenger.data.bridge.customEventContentSerializerMappingsModule
                        )
                    }
                },
            ).getOrThrow()
            attachClient(restored, session.homeServer)
        } catch (e: Exception) {
            logError("MatrixRepository", "Session restore failed", e)
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
            val proxy = runCatching { SessionStore.readProxySettings() }.getOrNull()
            val result = MatrixClient.loginWithPassword(
                baseUrl = Url(url),
                identifier = IdentifierType.User(username),
                password = password,
                initialDeviceDisplayName = "MatrixTalk",
                repositoriesModuleFactory = { createTrixnityRepositoriesModule() },
                mediaStoreFactory = { InMemoryMediaStore() },
                configuration = {
                    httpClientFactory = createProxyHttpClientFactory(proxy)
                    modulesFactory = {
                        createDefaultTrixnityModules() + listOf(
                            com.matrix.messenger.data.bridge.customEventContentSerializerMappingsModule
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
                    logError("MatrixRepository", "Password login failed", e)
                    _connectionState.value = ConnectionState.Disconnected(e.message ?: "Login failed")
                    LoginResult.Error(e.message ?: "Ошибка входа")
                }
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logError("MatrixRepository", "Password login crashed", e)
            _connectionState.value = ConnectionState.Disconnected(e.message ?: "Login failed")
            LoginResult.Error(e.message ?: "Ошибка входа")
        }
    }

    override suspend fun loginWithToken(homeServer: String, userId: String, accessToken: String): LoginResult {
        val url = normalizeHomeServer(homeServer)
        return try {
            _connectionState.value = ConnectionState.Connecting
            val proxy = runCatching { SessionStore.readProxySettings() }.getOrNull()
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
                configuration = {
                    httpClientFactory = createProxyHttpClientFactory(proxy)
                    modulesFactory = {
                        createDefaultTrixnityModules() + listOf(
                            com.matrix.messenger.data.bridge.customEventContentSerializerMappingsModule
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
                    logError("MatrixRepository", "Token login failed", e)
                    _connectionState.value = ConnectionState.Disconnected(e.message ?: "Login failed")
                    LoginResult.Error(e.message ?: "Ошибка входа по токену")
                }
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logError("MatrixRepository", "Token login crashed", e)
            _connectionState.value = ConnectionState.Disconnected(e.message ?: "Login failed")
            LoginResult.Error(e.message ?: "Ошибка входа по токену")
        }
    }

    private suspend fun forceLogout(reason: String) {
        logError("MatrixRepository", "Forcing logout: $reason")
        runCatching { client?.stopSync() }
        client = null
        MediaBytesProvider.resolver = null
        MediaBytesProvider.mxcResolver = null
        runCatching { SessionStore.clearSession() }
        TokenInvalidationDetector.reset()
        _clientState.value = null
        _connectionState.value = ConnectionState.Disconnected(reason)
    }

    override suspend fun logout() {
        val c = client
        client = null
        _currentUser.value = null
        userNameCache.clear()
        userAvatarCache.clear()
        mediaMxcCache.clear()
        MediaBytesProvider.resolver = null
        MediaBytesProvider.mxcResolver = null
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

    override fun getInvitesFlow(): Flow<List<ChatRoom>> = _clientState.flatMapLatest { c ->
        if (c == null) {
            flowOf(emptyList())
        } else {
            c.room.getAll()
                .flattenValues()
                .mapLatest { rooms ->
                    rooms
                        .filter { it.membership == Membership.INVITE && !it.hasBeenReplaced }
                        .map { room -> room.toChatRoom(c) }
                }
        }
    }

    override suspend fun searchUsers(query: String): List<MatrixUser> {
        val c = requireClient()
        val request = apiJson.encodeToString(UserDirectorySearchRequest(query.trim()))
        val response = c.api.httpClient.baseClient.post(
            "/_matrix/client/v3/user_directory/search"
        ) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.bodyAsText()
        return apiJson.decodeFromString<UserDirectorySearchResponse>(response).results.map { user ->
            MatrixUser(
                userId = user.user_id,
                displayName = user.display_name,
                // Coil can only load resolved https urls, not raw mxc://
                avatarUrl = user.avatar_url
            )
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
                                val polls = aggregatePolls(events, c.userId)
                                events.mapNotNull { it.toMessageOrNull(c) }
                                    .map { message ->
                                        val entry = reactions[message.eventId]
                                        val withReactions = if (entry.isNullOrEmpty()) message
                                        else message.copy(
                                            reactions = entry.mapValues { it.value.first },
                                            myReactions = entry.filterValues { it.second.contains(c.userId.full) }.keys
                                        )
                                        val pollSummary = polls[message.eventId]
                                        if (pollSummary != null) {
                                            withReactions.copy(
                                                pollVotes = pollSummary.votes,
                                                pollTotalVoters = pollSummary.totalVoters,
                                                pollMyAnswers = pollSummary.myAnswers
                                            )
                                        } else withReactions
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

    override suspend fun resolveMediaFile(eventId: String, fileName: String): String? {
        val c = client ?: return null
        val safeName = fileName.replace('/', '_').replace('\\', '_')
        val path = "${attachmentsDir()}/${eventId.removePrefix("$")}_$safeName"
        val bytes = encryptedFiles[eventId]?.let { file ->
            c.media.getEncryptedMedia(file).getOrThrow().toByteArray()
        } ?: mediaMxcCache[eventId]?.let { mxc ->
            c.media.getMedia(mxc).getOrThrow().toByteArray()
        } ?: return null
        writeFileBytes(path, bytes)
        return path
    }

    private fun pageSizeFor(roomId: String) =
        pageSizes.getOrPut(roomId) { MutableStateFlow(MESSAGE_PAGE_SIZE) }

    private fun aggregateReactions(events: List<TimelineEvent>): Map<String, Map<String, Pair<Int, Set<String>>>> {
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
            byKey.mapValues { (_, senders) -> senders.size to senders.toSet() }
        }
    }

    private data class PollSummary(
        val votes: Map<String, Int>,
        val totalVoters: Int,
        val myAnswers: List<String>
    )

    /** Aggregates MSC3381 poll responses: one vote per user per poll (latest wins). */
    private fun aggregatePolls(events: List<TimelineEvent>, myUserId: UserId): Map<String, PollSummary> {
        val votesByPoll = mutableMapOf<String, MutableMap<String, List<String>>>()
        events.forEach { timelineEvent ->
            val content = timelineEvent.content?.getOrNull() as? UnknownEventContent ?: return@forEach
            val response = com.matrix.messenger.data.poll.parsePollResponse(content.raw) ?: return@forEach
            val byUser = votesByPoll.getOrPut(response.pollEventId) { mutableMapOf() }
            byUser[timelineEvent.sender.full] = response.answers
        }
        return votesByPoll.mapValues { (_, byUser) ->
            val counts = mutableMapOf<String, Int>()
            byUser.values.forEach { answers -> answers.forEach { counts[it] = (counts[it] ?: 0) + 1 } }
            PollSummary(
                votes = counts,
                totalVoters = byUser.size,
                myAnswers = byUser[myUserId.full].orEmpty()
            )
        }
    }

    override suspend fun sendTextMessage(roomId: String, text: String, formattedBody: String?) {
        requireClient().room.sendMessage(RoomId(roomId)) {
            text(
                text,
                format = formattedBody?.let { "org.matrix.custom.html" },
                formattedBody = formattedBody
            )
        }
    }

    override suspend fun replyToMessage(roomId: String, eventId: String, text: String, threadRootId: String?) {
        requireClient().room.sendMessage(RoomId(roomId)) {
            val thread = threadRootId?.let {
                net.folivo.trixnity.core.model.events.m.RelatesTo.Thread(
                    eventId = EventId(it),
                    replyTo = net.folivo.trixnity.core.model.events.m.RelatesTo.ReplyTo(EventId(eventId)),
                    isFallingBack = false
                )
            }
            reply(EventId(eventId), thread)
            text(text)
        }
    }

    /**
     * Encrypts media bytes for an encrypted room (Matrix media encryption v2:
     * AES-CTR-256 + SHA-256, key/iv/hashes as EncryptedFile). The IV is
     * 8 random bytes followed by 8 zero bytes, as Element does.
     */
    private suspend fun encryptMediaBytes(bytes: ByteArray): Pair<EncryptedFile, ByteArray> {
        val key = com.matrix.messenger.platform.randomBytes(32)
        val iv = com.matrix.messenger.platform.randomBytes(8) + ByteArray(8)
        val hashedFlow = bytes.toByteArrayFlow()
            .encryptAes256Ctr(key, iv)
            .sha256()
        val encryptedBytes = hashedFlow.toByteArray()
        val sha256 = hashedFlow.hash.value ?: error("SHA-256 hash was not computed")
        val info = EncryptedFile(
            url = "",
            key = EncryptedFile.JWK(key = com.matrix.messenger.platform.encodeUnpaddedBase64Url(key)),
            initialisationVector = com.matrix.messenger.platform.encodeUnpaddedBase64Url(iv),
            hashes = mapOf("sha256" to sha256)
        )
        return info to encryptedBytes
    }

    override suspend fun sendEmoteMessage(roomId: String, text: String) {
        requireClient().room.sendMessage(RoomId(roomId)) { emote(text) }
    }

    override suspend fun sendFileMessage(roomId: String, filePath: String, mimeType: String, caption: String?) {
        val c = requireClient()
        val rId = RoomId(roomId)
        var bytes = readFileBytes(filePath)
        val isEncryptedRoom = runCatching {
            c.room.getById(rId).firstOrNull()?.encrypted == true
        }.getOrDefault(false)
        var fileInfo: EncryptedFile? = null
        if (isEncryptedRoom) {
            val (info, encryptedBytes) = encryptMediaBytes(bytes)
            bytes = encryptedBytes
            fileInfo = info
        }
        val mediaType = mimeType.ifBlank { "application/octet-stream" }
        val contentType = runCatching { ContentType.parse(mediaType) }
            .getOrElse { ContentType.Application.OctetStream }
        val mxc = uploadBytes(c, bytes, contentType)
        fileInfo = fileInfo?.copy(url = mxc)
        val fileName = caption ?: filePath.substringAfterLast('/').substringAfterLast('\\')
        val infoMime = mediaType
        val size = bytes.size.toLong()
        val messageContent: RoomMessageEventContent = when {
            mediaType.startsWith("image/") ->
                RoomMessageEventContent.FileBased.Image(
                    body = fileName,
                    info = ImageInfo(mimeType = infoMime, size = size),
                    url = fileInfo?.let { null } ?: mxc,
                    file = fileInfo
                )

            mediaType.startsWith("video/") ->
                RoomMessageEventContent.FileBased.Video(
                    body = fileName,
                    info = VideoInfo(mimeType = infoMime, size = size),
                    url = fileInfo?.let { null } ?: mxc,
                    file = fileInfo
                )

            mediaType.startsWith("audio/") ->
                RoomMessageEventContent.FileBased.Audio(
                    body = fileName,
                    info = AudioInfo(mimeType = infoMime, size = size),
                    url = fileInfo?.let { null } ?: mxc,
                    file = fileInfo
                )

            else ->
                RoomMessageEventContent.FileBased.File(
                    body = fileName,
                    info = FileInfo(mimeType = infoMime, size = size),
                    url = fileInfo?.let { null } ?: mxc,
                    file = fileInfo
                )
        }
        // Sent via the API directly, bypassing the outbox: Trixnity 4.9.2's outbox
        // media uploader unconditionally re-uploads the content url (even an already
        // valid mxc://) and crashes its retry loop on it, silently stalling every
        // later message in the queue. The media is already uploaded above, so a
        // straight send is both correct and observable (errors reach the snackbar).
        c.api.room.sendMessageEvent(RoomId(roomId), messageContent)
            .getOrThrow()
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

    override suspend fun createRoom(
        name: String?,
        topic: String?,
        isDirect: Boolean,
        userIds: List<String>,
        isEncrypted: Boolean
    ): String {
        // Encryption is applied at creation time via initial_state (m.room.encryption,
        // Megolm) — same as Element's "encryption enabled" room creation.
        val initialState = if (isEncrypted) {
            listOf(InitialStateEvent(content = EncryptionEventContent(), stateKey = ""))
        } else null
        return requireClient().api.room.createRoom(
            name = name,
            topic = topic,
            invite = userIds.map { UserId(it) }.toSet(),
            isDirect = isDirect,
            initialState = initialState
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

    override suspend fun setRoomMarkedUnread(roomId: String, unread: Boolean) {
        val c = requireClient()
        c.api.room.setAccountData(
            MarkedUnreadEventContent(unread = unread),
            roomId = RoomId(roomId),
            userId = c.userId
        ).getOrThrow()
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

    override fun getPinnedMessagesFlow(roomId: String): Flow<List<Message>> = _clientState.flatMapLatest { c ->
        if (c == null) {
            flowOf(emptyList())
        } else {
            val rId = RoomId(roomId)
            c.room.getState<PinnedEventsEventContent>(rId).mapLatest { stateEvent ->
                val pinnedIds = stateEvent?.content?.pinned ?: return@mapLatest emptyList()
                pinnedIds.mapNotNull { pinnedId ->
                    withTimeoutOrNull(PINNED_RESOLVE_TIMEOUT_MS) {
                        c.room.getTimelineEvent(rId, pinnedId).firstOrNull()
                    }?.toMessageOrNull(c)
                }
            }
        }
    }

    override fun getRoomSettingsFlow(roomId: String): Flow<RoomSettingsInfo> = _clientState.flatMapLatest { c ->
        if (c == null) {
            flowOf(RoomSettingsInfo(roomId, null, null, null, false, 0, canEdit = false))
        } else {
            val rId = RoomId(roomId)
            c.room.getById(rId).flatMapLatest { room ->
                if (room == null) {
                    flowOf(RoomSettingsInfo(roomId, null, null, null, false, 0, canEdit = false))
                } else {
                    val nameFlow = c.room.getState<NameEventContent>(rId).map { it?.content?.name }
                    val topicFlow = c.room.getState<TopicEventContent>(rId).map { it?.content?.topic }
                    val avatarFlow = c.room.getState<AvatarEventContent>(rId).map { it?.content?.url }
                    val plsFlow = c.room.getState<PowerLevelsEventContent>(rId)
                    combine(
                        nameFlow,
                        topicFlow,
                        avatarFlow,
                        plsFlow
                    ) { name: String?, topic: String?, avatarMxc: String?, pls: ClientEvent.StateBaseEvent<PowerLevelsEventContent>? ->
                        val content = pls?.content
                        val myPl = content?.users?.get(c.userId) ?: content?.usersDefault ?: 0
                        fun requiredFor(contentClass: KClass<out StateEventContent>, typeName: String): Long =
                            content?.events?.get(EventType(contentClass, typeName))
                                ?: content?.stateDefault ?: 50
                        val requiredEdit = maxOf(
                            requiredFor(NameEventContent::class, "m.room.name"),
                            requiredFor(TopicEventContent::class, "m.room.topic"),
                            requiredFor(AvatarEventContent::class, "m.room.avatar")
                        )
                        val requiredKick = content?.kick ?: 50
                        val requiredBan = content?.ban ?: 50
                        val requiredInvite = content?.invite ?: 0
                        val plKey = EventType(PowerLevelsEventContent::class, "m.room.power_levels")
                        val requiredPl = content?.events?.get(plKey) ?: content?.stateDefault ?: 50
                        RoomSettingsInfo(
                            roomId = roomId,
                            name = name ?: room.name?.explicitName ?: room.name?.heroes
                                ?.joinToString(", ") { it.full.substringBefore(":").removePrefix("@") }
                                ?: roomId,
                            topic = topic,
                            avatarUrl = avatarMxc,
                            isEncrypted = room.encrypted,
                            memberCount = (room.name?.heroes?.size ?: 0) + (room.name?.otherUsersCount ?: 0) + 1,
                            canEdit = myPl >= requiredEdit,
                            myPowerLevel = myPl,
                            canInvite = myPl >= requiredInvite,
                            canKick = myPl >= requiredKick,
                            canBan = myPl >= requiredBan,
                            canChangePowerLevels = myPl >= requiredPl
                        )
                    }
                }
            }
        }
    }

    override fun getRoomMembersFlow(roomId: String): Flow<List<RoomMemberInfo>> = _clientState.flatMapLatest { c ->
        if (c == null) {
            flowOf(emptyList())
        } else {
            val rId = RoomId(roomId)
            c.user.loadMembers(rId, wait = false)
            val powerLevelsFlow = c.room.getState<PowerLevelsEventContent>(rId)
            c.user.getAll(rId).flatMapLatest { members: Map<UserId, Flow<RoomUser?>> ->
                powerLevelsFlow.flatMapLatest { pls: ClientEvent.StateBaseEvent<PowerLevelsEventContent>? ->
                    val entries: List<Flow<RoomMemberInfo>> = members.entries.map { entry ->
                        val userId = entry.key
                        entry.value.map { user: RoomUser? ->
                            val member = user?.event?.content
                            RoomMemberInfo(
                                userId = userId.full,
                                name = user?.name,
                                avatarUrl = member?.avatarUrl,
                                powerLevel = pls?.content?.users?.get(userId) ?: pls?.content?.usersDefault ?: 0,
                                isSelf = userId == c.userId,
                                membership = member?.membership?.value ?: "join",
                                isBot = com.matrix.messenger.data.model.isBotUser(userId.full, user?.name)
                            )
                        }.filter { memberInfo: RoomMemberInfo -> memberInfo.name != null }
                    }
                    if (entries.isEmpty()) {
                        flowOf(emptyList<RoomMemberInfo>())
                    } else {
                        combine(entries) { list ->
                            list.sortedWith(compareByDescending<RoomMemberInfo> { it.powerLevel }.thenBy { it.name?.lowercase() })
                        }
                    }
                }
            }
        }
    }

    override suspend fun getKeyBackupStatus(): KeyBackupStatus? {
        val c = requireClient()
        return runCatching {
            val version = c.di.get<net.folivo.trixnity.client.key.KeyBackupService>().version.value
            version?.let { KeyBackupStatus(version = it.version, algorithm = it.algorithm::class.simpleName) }
        }.getOrNull()
    }

    override suspend fun getPresence(userId: String): PresenceInfo? {
        val content = requireClient().api.user.getPresence(UserId(userId)).getOrNull() ?: return null
        return PresenceInfo(
            presence = content.presence.value,
            lastActiveAgoMinutes = content.lastActiveAgo?.let { it / 60000 },
            statusMessage = content.statusMessage
        )
    }

    override suspend fun setOwnPresence(online: Boolean) {
        val c = requireClient()
        runCatching {
            c.api.user.setPresence(c.userId, if (online) Presence.ONLINE else Presence.OFFLINE)
        }
    }

    override suspend fun sendSticker(roomId: String, filePath: String, mimeType: String) {
        val c = requireClient()
        val rId = RoomId(roomId)
        var bytes = readFileBytes(filePath)
        require(bytes.isNotEmpty()) { "Выбранный файл пуст" }
        val contentType = runCatching { ContentType.parse(mimeType) }
            .getOrElse { ContentType.Application.OctetStream }
        val isEncryptedRoom = runCatching {
            c.room.getById(rId).firstOrNull()?.encrypted == true
        }.getOrDefault(false)
        var fileInfo: EncryptedFile? = null
        if (isEncryptedRoom) {
            val (info, encryptedBytes) = encryptMediaBytes(bytes)
            bytes = encryptedBytes
            fileInfo = info
        }
        val mxc = uploadBytes(c, bytes, contentType)
        fileInfo = fileInfo?.copy(url = mxc)
        val stickerJson = kotlinx.serialization.json.buildJsonObject {
            put("body", kotlinx.serialization.json.JsonPrimitive(filePath.substringAfterLast('/').substringAfterLast("\\")))
            if (fileInfo != null) {
                put("file", kotlinx.serialization.json.Json.encodeToJsonElement(EncryptedFile.serializer(), fileInfo))
            } else {
                put("url", kotlinx.serialization.json.JsonPrimitive(mxc))
            }
            put("info", kotlinx.serialization.json.buildJsonObject {
                put("mimetype", kotlinx.serialization.json.JsonPrimitive(mimeType))
                put("size", kotlinx.serialization.json.JsonPrimitive(bytes.size.toLong()))
            })
        }
        c.api.room.sendMessageEvent(rId, UnknownEventContent(stickerJson, "m.sticker")).getOrThrow()
    }

    override suspend fun sendLocation(roomId: String, latitude: Double, longitude: Double, accuracyMeters: Double?) {
        val c = requireClient()
        val location = com.matrix.messenger.data.model.GeoLocation(latitude, longitude, accuracyMeters)
        requireClient().api.room.sendMessageEvent(
            RoomId(roomId),
            RoomMessageEventContent.Location(
                body = com.matrix.messenger.data.model.GeoUri.format(location),
                geoUri = com.matrix.messenger.data.model.GeoUri.toGeoUri(location)
            )
        ).getOrThrow()
    }

    override fun getReadReceiptsFlow(roomId: String): Flow<Map<String, List<ReadReceipt>>> = _clientState.flatMapLatest { c ->
        if (c == null) {
            flowOf(emptyMap())
        } else {
            val rId = RoomId(roomId)
            c.user.loadMembers(rId, wait = false)
            c.user.getAllReceipts(rId).flatMapLatest { byUser ->
                val entries = byUser.filterKeys { it != c.userId }.map { (userId, receiptFlow) ->
                    receiptFlow.map { userReceipts ->
                        val eventId = userReceipts?.receipts
                            ?.get(net.folivo.trixnity.core.model.events.m.ReceiptType.Read)
                            ?.eventId?.full
                        userId to eventId
                    }
                }
                if (entries.isEmpty()) {
                    flowOf(emptyMap())
                } else {
                    combine(entries) { pairs: Array<Pair<UserId, String?>> ->
                        val byEvent = mutableMapOf<String, MutableList<UserId>>()
                        pairs.forEach { (userId, eventId) ->
                            if (eventId != null) byEvent.getOrPut(eventId) { mutableListOf() }.add(userId)
                        }
                        byEvent
                    }.flatMapLatest { byEvent ->
                        c.user.getAll(rId).flatMapLatest { members ->
                            flowOf(byEvent.mapValues { (_, userIds) ->
                                userIds.mapNotNull { userId ->
                                    val member = members[userId]?.firstOrNull()
                                    ReadReceipt(
                                        userId = userId.full,
                                        name = member?.name,
                                        avatarUrl = member?.event?.content?.avatarUrl
                                    )
                                }
                            })
                        }
                    }
                }
            }
        }
    }

    override suspend fun setRoomName(roomId: String, name: String) {
        requireClient().api.room.sendStateEvent(RoomId(roomId), NameEventContent(name = name))
    }

    override suspend fun setRoomTopic(roomId: String, topic: String) {
        requireClient().api.room.sendStateEvent(RoomId(roomId), TopicEventContent(topic = topic))
    }

    override suspend fun setRoomAvatar(roomId: String, filePath: String) {
        val c = requireClient()
        val bytes = readFileBytes(filePath)
        require(bytes.isNotEmpty()) { "Выбранный файл пуст" }
        val contentType = runCatching { ContentType.parse(mimeTypeForFile(filePath)) }
            .getOrElse { ContentType.Application.OctetStream }
        val mxc = uploadBytes(c, bytes, contentType)
        c.api.room.sendStateEvent(RoomId(roomId), AvatarEventContent(url = mxc))
    }

    override suspend fun kickUser(roomId: String, userId: String) {
        requireClient().api.room.kickUser(RoomId(roomId), UserId(userId)).getOrThrow()
    }

    override suspend fun banUser(roomId: String, userId: String, reason: String?) {
        requireClient().api.room.banUser(RoomId(roomId), UserId(userId), reason).getOrThrow()
    }

    override suspend fun unbanUser(roomId: String, userId: String) {
        requireClient().api.room.unbanUser(RoomId(roomId), UserId(userId)).getOrThrow()
    }

    override suspend fun setUserPowerLevel(roomId: String, userId: String, level: Long) {
        val c = requireClient()
        val rId = RoomId(roomId)
        val current = c.room.getState<PowerLevelsEventContent>(rId).firstOrNull()?.content
            ?: PowerLevelsEventContent()
        val updated = current.copy(users = current.users + (UserId(userId) to level))
        c.api.room.sendStateEvent(rId, updated)
    }

    private val _mutedRooms = MutableStateFlow<Set<String>>(emptySet())

    override fun mutedRoomsFlow(): StateFlow<Set<String>> = _mutedRooms.asStateFlow()

    override suspend fun isRoomMuted(roomId: String): Boolean = _mutedRooms.value.contains(roomId)

    override suspend fun setRoomMuted(roomId: String, muted: Boolean) {
        val c = requireClient()
        val ruleId = RoomId(roomId).full
        if (muted) {
            c.api.push.setPushRule(
                scope = "global",
                kind = PushRuleKind.ROOM,
                ruleId = ruleId,
                pushRule = SetPushRule.Request(actions = emptySet())
            ).getOrThrow()
        } else {
            c.api.push.deletePushRule(scope = "global", kind = PushRuleKind.ROOM, ruleId = ruleId).getOrThrow()
        }
        val mutedRooms = SessionStore.readMutedRooms().toMutableList()
        if (muted) mutedRooms.add(roomId) else mutedRooms.remove(roomId)
        SessionStore.saveMutedRooms(mutedRooms)
        _mutedRooms.value = mutedRooms.toSet()
        setRoomNotificationLevel(roomId, if (muted) RoomNotificationLevel.MUTE else RoomNotificationLevel.ALL_MESSAGES)
    }

    override suspend fun getRoomNotificationLevel(roomId: String): RoomNotificationLevel {
        // Server push rule wins; local cache is the offline fallback.
        val serverLevel = runCatching {
            val rule = requireClient().api.push.getPushRule(
                scope = "global",
                kind = PushRuleKind.ROOM,
                ruleId = RoomId(roomId).full
            ).getOrNull() ?: return RoomNotificationLevel.ALL_MESSAGES
            when {
                rule.actions.isEmpty() -> RoomNotificationLevel.MUTE
                rule.actions.any { it is PushAction.Notify || it is PushAction.SetHighlightTweak } ->
                    RoomNotificationLevel.ALL_MESSAGES
                else -> RoomNotificationLevel.MENTIONS_ONLY
            }
        }.getOrNull()
        if (serverLevel != null && serverLevel != RoomNotificationLevel.ALL_MESSAGES) {
            persistNotificationLevel(roomId, serverLevel)
            return serverLevel
        }
        if (serverLevel != null) {
            persistNotificationLevel(roomId, serverLevel)
            return serverLevel
        }
        return RoomNotificationLevel.fromStored(
            SessionStore.readRoomNotificationLevels()[roomId]
        )
    }

    private suspend fun persistNotificationLevel(roomId: String, level: RoomNotificationLevel) {
        val levels = SessionStore.readRoomNotificationLevels().toMutableMap()
        if (level == RoomNotificationLevel.ALL_MESSAGES) levels.remove(roomId) else levels[roomId] = level.name
        SessionStore.saveRoomNotificationLevels(levels)
    }

    override suspend fun setRoomNotificationLevel(roomId: String, level: RoomNotificationLevel) {
        val c = requireClient()
        val ruleId = RoomId(roomId).full
        when (level) {
            RoomNotificationLevel.ALL_MESSAGES -> {
                runCatching {
                    c.api.push.deletePushRule(scope = "global", kind = PushRuleKind.ROOM, ruleId = ruleId)
                }
            }
            RoomNotificationLevel.MENTIONS_ONLY -> {
                c.api.push.setPushRule(
                    scope = "global",
                    kind = PushRuleKind.ROOM,
                    ruleId = ruleId,
                    pushRule = SetPushRule.Request(
                        actions = setOf(PushAction.Unknown("dont_notify", JsonPrimitive("dont_notify")))
                    )
                ).getOrThrow()
            }
            RoomNotificationLevel.MUTE -> {
                c.api.push.setPushRule(
                    scope = "global",
                    kind = PushRuleKind.ROOM,
                    ruleId = ruleId,
                    pushRule = SetPushRule.Request(actions = emptySet())
                ).getOrThrow()
            }
        }
        persistNotificationLevel(roomId, level)
        _mutedRooms.value = SessionStore.readRoomNotificationLevels()
            .filterValues { it == RoomNotificationLevel.MUTE.name }.keys
    }

    override fun getRoomTrustFlow(roomId: String): Flow<RoomTrust> = _clientState.flatMapLatest { c ->
        if (c == null) {
            flowOf(RoomTrust(isEncrypted = false, verified = null))
        } else {
            val rId = RoomId(roomId)
            val keyService = c.di.get<KeyService>()
            val userService = c.di.get<UserService>()
            c.room.getById(rId).flatMapLatest { room ->
                if (room == null || !room.encrypted) {
                    flowOf(RoomTrust(isEncrypted = false, verified = null))
                } else {
                    // Fetch the full member list (store keeps updating it via sync).
                    c.user.loadMembers(rId, wait = false)
                    c.user.getAll(rId).flatMapLatest { members ->
                        val userIds = members.keys.toList()
                        if (userIds.isEmpty() || userIds.size > TRUST_MAX_MEMBERS) {
                            flowOf(RoomTrust(isEncrypted = true, verified = null))
                        } else {
                            combine(userIds.map { userId -> keyService.getTrustLevel(userId) }) { levels ->
                                RoomTrust(
                                    isEncrypted = true,
                                    // Element-style strictness: every member's identity must
                                    // be cross-signed AND verified; anything else = warning.
                                    verified = levels.all { it is UserTrustLevel.CrossSigned && it.verified }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun getOwnSessionVerifiedFlow(): Flow<Boolean?> = _clientState.flatMapLatest { c ->
        if (c == null) {
            flowOf(null)
        } else {
            c.di.get<KeyService>().getTrustLevel(c.userId, c.deviceId).map { level ->
                when (level) {
                    is DeviceTrustLevel.CrossSigned -> level.verified
                    is DeviceTrustLevel.Valid -> level.verified
                    else -> null
                }
            }
        }
    }

    override val selfVerification: Flow<SelfVerificationInfo> = _clientState.flatMapLatest { c ->
        if (c == null) {
            flowOf(SelfVerificationInfo(verified = null))
        } else {
            val service = c.di.get<VerificationService>()
            service.getSelfVerificationMethods().map { methods ->
                when (methods) {
                    is VerificationService.SelfVerificationMethods.AlreadyCrossSigned ->
                        SelfVerificationInfo(verified = true)
                    is VerificationService.SelfVerificationMethods.CrossSigningEnabled -> SelfVerificationInfo(
                        verified = false,
                        canVerifyWithRecoveryKey = methods.methods.any { it is SelfVerificationMethod.AesHmacSha2RecoveryKey },
                        canVerifyWithPassphrase = methods.methods.any { it is SelfVerificationMethod.AesHmacSha2RecoveryKeyWithPbkdf2Passphrase },
                    )
                    is VerificationService.SelfVerificationMethods.NoCrossSigningEnabled ->
                        SelfVerificationInfo(verified = false)
                    is VerificationService.SelfVerificationMethods.PreconditionsNotMet ->
                        SelfVerificationInfo(verified = null)
                }
            }
        }
    }

    override suspend fun verifySelfWithSecret(secret: String): Result<Unit> {
        val service = requireClient().di.get<VerificationService>()
        val methods = service.getSelfVerificationMethods().first()
        if (methods !is VerificationService.SelfVerificationMethods.CrossSigningEnabled) {
            return Result.failure(IllegalStateException("Кросс-подпись не настроена"))
        }
        val recoveryKeyMethod = methods.methods
            .filterIsInstance<SelfVerificationMethod.AesHmacSha2RecoveryKey>().firstOrNull()
        val passphraseMethod = methods.methods
            .filterIsInstance<SelfVerificationMethod.AesHmacSha2RecoveryKeyWithPbkdf2Passphrase>().firstOrNull()
        val result = recoveryKeyMethod?.verify(secret)
            ?: passphraseMethod?.verify(secret)
            ?: return Result.failure(IllegalStateException("Метод верификации недоступен"))
        return result
    }

    override suspend fun exportRoomKeys(passphrase: String): ByteArray {
        val c = requireClient()
        val pickleKey = checkNotNull(c.di.get<AccountStore>().getAccount()?.olmPickleKey) {
            "Ключи шифрования недоступны в этой сессии"
        }
        val olmCryptoStore = c.di.get<OlmCryptoStore>()
        val backupService = c.di.get<KeyBackupService>(named<KeyBackupService>())

        // All known sessions: server key backup + local sessions not backed up yet.
        val sessionIds = mutableSetOf<Pair<RoomId, String>>()
        val backupVersion = backupService.version.value
        if (backupVersion != null) {
            runCatching {
                c.api.keys.getRoomKeys(backupVersion.version).getOrThrow()
            }.getOrNull()?.rooms?.forEach { (roomId, roomBackup) ->
                roomBackup.sessions.keys.forEach { sessionId -> sessionIds += roomId to sessionId }
            }
        }
        val notBackedUp: Set<Pair<RoomId, String>> =
            olmCryptoStore.notBackedUpInboundMegolmSessions.value.keys.map { it.roomId to it.sessionId }.toSet()
        sessionIds.addAll(notBackedUp)
        // Pull sessions from the backup into the local store (decrypts on demand).
        sessionIds.forEach { (roomId, sessionId) ->
            runCatching { backupService.loadMegolmSession(roomId, sessionId) }
        }

        val exported = sessionIds.mapNotNull { (roomId, sessionId) ->
            val stored = olmCryptoStore.getInboundMegolmSession(sessionId, roomId).firstOrNull()
                ?: return@mapNotNull null
            val exportedSessionKey = freeAfter(OlmInboundGroupSession.unpickle(pickleKey, stored.pickled)) { session ->
                runCatching { session.export(stored.firstKnownIndex) }
                    .getOrElse { session.export(0) }
            }
            ExportedSession(
                roomId = roomId.full,
                sessionId = sessionId,
                sessionKey = exportedSessionKey,
                senderKey = stored.senderKey.value,
                senderClaimedKeys = mapOf(KeyAlgorithm.Ed25519.name to stored.senderSigningKey.value),
                forwardingCurve25519KeyChain = stored.forwardingCurve25519KeyChain.map { it.value }
            )
        }
        return encodeRoomKeysExport(exported, passphrase)
    }

    override suspend fun importRoomKeys(bytes: ByteArray, passphrase: String): Int {
        val c = requireClient()
        val pickleKey = checkNotNull(c.di.get<AccountStore>().getAccount()?.olmPickleKey) {
            "Ключи шифрования недоступны в этой сессии"
        }
        val olmCryptoStore = c.di.get<OlmCryptoStore>()
        val sessions = decodeRoomKeysExport(bytes, passphrase)
        var imported = 0
        sessions.forEach { session ->
            freeAfter(OlmInboundGroupSession.import(session.sessionKey)) { importedSession ->
                val firstKnownIndex = importedSession.firstKnownIndex
                val pickled = importedSession.pickle(pickleKey)
                olmCryptoStore.updateInboundMegolmSession(
                    sessionId = session.sessionId,
                    roomId = RoomId(session.roomId)
                ) { old ->
                    if (old != null && old.firstKnownIndex <= firstKnownIndex) {
                        old
                    } else {
                        StoredInboundMegolmSession(
                            senderKey = Key.Curve25519Key(null, session.senderKey),
                            sessionId = session.sessionId,
                            roomId = RoomId(session.roomId),
                            firstKnownIndex = firstKnownIndex,
                            isTrusted = false, // imported keys are only channel-trusted
                            hasBeenBackedUp = false,
                            senderSigningKey = Key.Ed25519Key(
                                null,
                                session.senderClaimedKeys[KeyAlgorithm.Ed25519.name] ?: ""
                            ),
                            forwardingCurve25519KeyChain =
                            session.forwardingCurve25519KeyChain.map { Key.Curve25519Key(null, it) },
                            pickled = pickled
                        )
                    }
                }
            }
            imported++
        }
        return imported
    }

    override suspend fun setPinnedMessage(roomId: String, eventId: String, pinned: Boolean) {
        val c = requireClient()
        val rId = RoomId(roomId)
        val current = c.room.getState<PinnedEventsEventContent>(rId).firstOrNull()?.content?.pinned
            ?: emptyList()
        val eId = EventId(eventId)
        val updated = if (pinned) (current + eId).distinct() else current - eId
        c.api.room.sendStateEvent(rId, PinnedEventsEventContent(updated), stateKey = "").getOrThrow()
    }

    override suspend fun sendTypingNotification(roomId: String, isTyping: Boolean) {
        val c = requireClient()
        c.api.room.setTyping(RoomId(roomId), c.userId, isTyping).getOrThrow()
    }

    override fun getTypingUsersFlow(roomId: String): Flow<List<String>> = _clientState.flatMapLatest { c ->
        if (c == null) {
            flowOf(emptyList())
        } else {
            c.room.usersTyping.transformLatest { typing ->
                val rId = RoomId(roomId)
                val users = typing[rId]?.users?.filter { it != c.userId }.orEmpty()
                if (users.isEmpty()) {
                    emit(emptyList())
                    return@transformLatest
                }
                emit(users.map { userId ->
                    c.resolveSenderName(rId, userId)
                        ?: userId.full.substringBefore(":").removePrefix("@")
                })
                // Fallback expiry: show the indicator for a bounded time after the last update.
                delay(TYPING_DISPLAY_TIMEOUT_MS)
                emit(emptyList())
            }
        }
    }

    override suspend fun unreact(roomId: String, eventId: String, reaction: String) {
        val c = requireClient()
        val rId = RoomId(roomId)
        val target = EventId(eventId)
        val events = c.room.getLastTimelineEvents(rId)
            .toFlowList(pageSizeFor(roomId)).firstOrNull().orEmpty()
            .mapNotNull { it.firstOrNull() }
        val mine = events.lastOrNull { timelineEvent ->
            val content = timelineEvent.content?.getOrNull() as? ReactionEventContent ?: return@lastOrNull false
            val annotation = content.relatesTo as? RelatesTo.Annotation ?: return@lastOrNull false
            annotation.eventId == target && annotation.key == reaction && timelineEvent.sender == c.userId
        }
        val reactionEventId = mine?.event?.id ?: return
        c.api.room.redactEvent(rId, reactionEventId).getOrThrow()
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

    override suspend fun uploadAvatar(filePath: String, mimeType: String) {
        val c = requireClient()
        val bytes = readFileBytes(filePath)
        require(bytes.isNotEmpty()) { "Выбранный файл пуст" }
        require(mimeType.startsWith("image/")) { "Выберите файл изображения" }
        val contentType = ContentType.parse(mimeType)
        val mxc = uploadBytes(c, bytes, contentType)
        c.setAvatarUrl(mxc).getOrThrow()
        _currentUser.value = _currentUser.value?.copy(avatarUrl = mxc)
    }

    override suspend fun setDisplayName(name: String) {
        requireClient().setDisplayName(name).getOrThrow()
        _currentUser.value = _currentUser.value?.copy(displayName = name)
    }

    override suspend fun getDevices(): List<DeviceInfo> =
        requireClient().api.device.getDevices().getOrThrow().map {
            DeviceInfo(
                deviceId = it.deviceId,
                displayName = it.displayName,
                lastSeenIp = it.lastSeenIp,
                lastSeenTs = it.lastSeenTs
            )
        }

    override suspend fun renameDevice(deviceId: String, newName: String) {
        requireClient().api.device.updateDevice(deviceId, displayName = newName).getOrThrow()
    }

    override suspend fun deleteDevice(deviceId: String, passwordProvider: suspend () -> String?) {
        val uia = requireClient().api.device.deleteDevice(deviceId).getOrThrow()
        completeUia(uia, passwordProvider)
    }

    override suspend fun currentDeviceId(): String? = client?.deviceId

    override suspend fun currentUserId(): String? = client?.userId?.full

    override fun getKeyBackupVersionFlow(): Flow<String?> = _clientState.flatMapLatest { c ->
        if (c == null) flowOf(null)
        // Trixnity binds KeyBackupService with a named qualifier: a plain get()
        // throws NoDefinitionFoundException (seen in error.log on the devices screen).
        else c.di.get<KeyBackupService>(named<KeyBackupService>()).version.map { it?.version }
    }

    override suspend fun bootstrapKeyBackup(passwordProvider: suspend () -> String?): String {
        val keyService = requireClient().di.get<KeyService>()
        val bootstrap = keyService.bootstrapCrossSigning()
        completeUia(bootstrap.result.getOrThrow(), passwordProvider)
        return bootstrap.recoveryKey
    }

    /**
     * Walks a UIA (user interactive auth) session to completion: if the server
     * asks for a password-only flow, [passwordProvider] is queried; null cancels.
     */
    private suspend fun <T> completeUia(initial: UIA<T>, passwordProvider: suspend () -> String?): T {
        var current = initial
        var attempts = 0
        while (true) {
            current = when (val uia = current) {
                is UIA.Success -> return uia.value
                is UIA.Step -> authenticateUia(uia, passwordProvider, ++attempts)
                is UIA.Error -> authenticateUia(uia, passwordProvider, ++attempts)
            }
        }
    }

    private suspend fun <T> authenticateUia(
        uia: UIA<T>,
        passwordProvider: suspend () -> String?,
        attempt: Int
    ): UIA<T> {
        if (attempt > 3) throw IllegalStateException("Слишком много попыток подтверждения")
        val state = when (uia) {
            is UIA.Step -> uia.state
            is UIA.Error -> uia.state
            else -> throw IllegalStateException("Unexpected UIA state")
        }
        val passwordOnlyFlow = state.flows.any { flow -> flow.stages.all { it == AuthenticationType.Password } }
        if (!passwordOnlyFlow) {
            throw IllegalStateException("Сервер требует неподдерживаемый тип подтверждения")
        }
        val password = passwordProvider() ?: throw kotlinx.coroutines.CancellationException("uia cancelled")
        val userId = client?.userId ?: throw IllegalStateException("Не выполнен вход")
        val localpart = userId.full.removePrefix("@").substringBefore(":")
        val request = AuthenticationRequest.Password(IdentifierType.User(localpart), password)
        val next = when (uia) {
            is UIA.Step -> uia.authenticate(request)
            is UIA.Error -> uia.authenticate(request)
            else -> error("unreachable")
        }
        return next.getOrElse { throw IllegalStateException("Неверный пароль или отказ сервера") }
    }

    // --- Interactive device verification (emoji SAS) ---

    private var lastActiveVerification: net.folivo.trixnity.client.verification.ActiveDeviceVerification? = null
    private var currentComparison: ActiveSasVerificationState.ComparisonByUser? = null
    private val verificationRefresh = MutableStateFlow(0)
    private var activeUserVerification: ActiveUserVerification? = null
    private val userVerificationState = MutableStateFlow<VerificationUiState>(VerificationUiState.Idle)

    override val verificationState: Flow<VerificationUiState> =
        userVerificationState.flatMapLatest { userState ->
            if (userState != VerificationUiState.Idle) {
                // an in-room verification started from the chat drives the UI
                flowOf(userState)
            } else {
                combine(_clientState, verificationRefresh) { c, _ -> c }
                    .flatMapLatest { c ->
                        val service = c?.di?.get<VerificationService>()
                            ?: return@flatMapLatest flowOf(VerificationUiState.Idle)
                        service.activeDeviceVerification.flatMapLatest { active ->
                            if (active == null) {
                                lastActiveVerification = null
                                currentComparison = null
                                flowOf(VerificationUiState.Idle)
                            } else {
                                lastActiveVerification = active
                                active.state.flatMapLatest { state -> handleVerificationState(state) }
                            }
                        }
                    }
            }
        }

    override suspend fun startDeviceVerification(deviceId: String) {
        val c = requireClient()
        val service = c.di.get<VerificationService>()
        // A stale active verification (e.g. a request nobody accepted within the
        // 10-minute window) makes trixnity auto-cancel every new request.
        runCatching { service.activeDeviceVerification.value?.cancel() }
        service.createDeviceVerificationRequest(c.userId, setOf(deviceId))
            .getOrThrow()
        verificationRefresh.value++
    }



    override suspend fun confirmVerification(matches: Boolean) {
        val comparison = currentComparison ?: return
        currentComparison = null
        if (matches) comparison.match() else comparison.noMatch()
    }

    override suspend fun cancelVerification() {
        activeUserVerification?.cancel("cancelled by user")
        userVerificationState.value = VerificationUiState.Idle
        lastActiveVerification?.cancel("cancelled by user")
        currentComparison = null
    }

    private fun handleVerificationState(state: ActiveVerificationState): Flow<VerificationUiState> =
        when (state) {
            is ActiveVerificationState.TheirRequest -> {
                repositoryScope.launch {
                    runCatching { state.ready() }
                        .onFailure { e -> logError("Verification", "sending ready failed", e) }
                        .onSuccess { logError("Verification", "ready sent", null) }
                }
                flowOf(VerificationUiState.Waiting)
            }
            is ActiveVerificationState.Ready -> {
                repositoryScope.launch {
                    runCatching {
                        if (state.methods.any { it is VerificationMethod.Sas }) {
                            state.start(VerificationMethod.Sas)
                        } else {
                            error("Element ready methods do not include SAS: ${state.methods}")
                        }
                    }
                        .onSuccess { logError("Verification", "SAS start sent", null) }
                        .onFailure { e -> logError("Verification", "sending SAS start failed", e) }
                }
                flowOf(VerificationUiState.Waiting)
            }
            is ActiveVerificationState.Start -> when (val method = state.method) {
                is ActiveSasVerificationMethod -> method.state.mapLatest { sas -> handleSasState(sas) }
                else -> flowOf(VerificationUiState.Waiting)
            }
            ActiveVerificationState.Done -> {
                currentComparison = null
                flowOf(VerificationUiState.Done)
            }
            is ActiveVerificationState.Cancel -> {
                currentComparison = null
                logError(
                    "Verification",
                    "Verification cancelled by peer: code=${state.content.code::class.simpleName} " +
                        "(${state.content.code.value}) reason=${state.content.reason}",
                    null
                )
                flowOf(VerificationUiState.Cancelled(state.content.reason))
            }
            else -> flowOf(VerificationUiState.Waiting)
        }

    private fun handleSasState(sas: ActiveSasVerificationState): VerificationUiState = when (sas) {
        is ActiveSasVerificationState.TheirSasStart -> {
            repositoryScope.launch {
                runCatching { sas.accept() }
                    .onFailure { e -> logError("Verification", "sending accept failed", e) }
                    .onSuccess { logError("Verification", "accept sent", null) }
            }
            VerificationUiState.Waiting
        }
        is ActiveSasVerificationState.ComparisonByUser -> {
            logError("Verification", "SAS comparison reached - emojis ready", null)
            currentComparison = sas
            VerificationUiState.Compare(
                theirUserId = lastActiveVerification?.theirUserId?.full ?: "",
                theirDeviceId = lastActiveVerification?.theirDeviceId,
                emojis = sas.emojis,
                decimal = sas.decimal
            )
        }
        else -> VerificationUiState.Waiting
    }

    private fun requireClient(): MatrixClient = client ?: error("Не выполнен вход")

    private fun mimeTypeForFile(filePath: String): String =
        when (filePath.substringAfterLast('.').lowercase()) {
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "svg" -> "image/svg+xml"
            else -> "application/octet-stream"
        }

    /**
     * Drops file/image/video/audio rows from the outbox. Trixnity 4.9.2's outbox
     * sender re-"uploads" the content url of ANY FileBased row unconditionally
     * (fileRoomMessageEventContentMediaUploader calls upload() even for a plain
     * mxc:// url), which throws and crashes its retry loop forever — silently
     * blocking ALL later messages (texts included) behind it. File messages are
     * therefore sent via the API directly (see sendFileMessage) and any file row
     * found in the persistent outbox is poison from an older run: remove it.
     */
    private fun purgeUnsendableOutbox(c: MatrixClient) {
        repositoryScope.launch {
            runCatching {
                val outbox = c.room.getOutbox().first()
                outbox.forEach { entryFlow ->
                    val message = runCatching { entryFlow.first() }.getOrNull() ?: return@forEach
                    if (message?.sentAt == null) {
                        runCatching {
                            c.room.cancelSendMessage(message.roomId, message.transactionId)
                        }.onSuccess {
                            logError(
                                "Chat",
                                "Purged stale outbox message ${message.transactionId}",
                                null
                            )
                        }
                    }
                }
            }.onFailure { t ->
                logError("Chat", "Outbox purge failed", t)
            }
        }
    }

    private suspend fun attachClient(c: MatrixClient, url: String) {
        client?.let { old ->
            try {
                old.stopSync()
            } catch (e: Exception) {
                // best effort
            }
        }
        userNameCache.clear()
        userAvatarCache.clear()
        baseUrl = url.trimEnd('/')
        client = c
        purgeUnsendableOutbox(c)
        MediaBytesProvider.resolver = { file ->
            c.media.getEncryptedMedia(file).getOrThrow().toByteArray()
        }
        // Plain mxc media downloads through the authenticated client (the legacy
        // unauthenticated endpoint 404s on enforcing homeservers like matrix.org).
        MediaBytesProvider.mxcResolver = { mxc ->
            c.media.getMedia(mxc).getOrThrow().toByteArray()
        }
        _currentUser.value = MatrixUser(
            userId = c.userId.full,
            displayName = c.displayName.value,
            avatarUrl = c.avatarUrl.value
        )
        repositoryScope.launch {
            combine(c.displayName, c.avatarUrl) { name, avatar -> name to avatar }
                .collect { (name, avatar) ->
                    _currentUser.value = _currentUser.value?.copy(
                        displayName = name,
                        avatarUrl = avatar
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
        repositoryScope.launch {
            TokenInvalidationDetector.triggered.collect { invalid ->
                if (invalid) {
                    forceLogout("Сессия истекла — сервер отклонил токен (M_UNKNOWN_TOKEN). Войдите заново.")
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

    private val roomTagsCache = mutableMapOf<String, Pair<Long, Set<String>>>()

    private suspend fun tagsFor(c: MatrixClient, roomId: String): Set<String> {
        val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        roomTagsCache[roomId]?.let { (ts, tags) ->
            if (now - ts < ROOM_TAGS_CACHE_MS) return tags
        }
        val tags = runCatching {
            c.api.room.getTags(c.userId, RoomId(roomId)).getOrNull()?.tags?.keys?.map { it.name }?.toSet()
        }.getOrNull() ?: emptySet()
        roomTagsCache[roomId] = now to tags
        return tags
    }

    override suspend fun getRoomTags(roomId: String): Set<String> {
        val c = client ?: return emptySet()
        return tagsFor(c, roomId)
    }

    override suspend fun toggleFavorite(roomId: String): Boolean {
        val c = requireClient()
        val rId = RoomId(roomId)
        val current = tagsFor(c, roomId)
        val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        return if (current.contains(FAVORITE_TAG)) {
            c.api.room.deleteTag(c.userId, rId, FAVORITE_TAG).getOrThrow()
            roomTagsCache[roomId] = now to (current - FAVORITE_TAG)
            false
        } else {
            c.api.room.setTag(
                c.userId, rId, FAVORITE_TAG,
                net.folivo.trixnity.core.model.events.m.TagEventContent.Tag(order = 0.0)
            ).getOrThrow()
            roomTagsCache[roomId] = now to (current + FAVORITE_TAG)
            true
        }
    }

    override suspend fun inviteUserToRoom(roomId: String, userId: String) {
        requireClient().api.room.inviteUser(RoomId(roomId), UserId(userId)).getOrThrow()
    }

    override suspend fun sendPoll(roomId: String, question: String, answers: List<String>) {
        if (answers.size < 2) error("Нужно минимум 2 варианта ответа")
        // m.poll.* is UnknownEventContent -> same outbox limitation as call signaling.
        requireClient().api.room.sendMessageEvent(
            RoomId(roomId),
            com.matrix.messenger.data.poll.buildPollStartContent(question, answers)
        ).getOrThrow()
    }

    override suspend fun sendPollVote(roomId: String, pollEventId: String, answerIds: List<String>) {
        requireClient().api.room.sendMessageEvent(
            RoomId(roomId),
            com.matrix.messenger.data.poll.buildPollResponseContent(pollEventId, answerIds)
        ).getOrThrow()
    }

    private suspend fun Room.toChatRoom(c: MatrixClient): ChatRoom {
        val lastMsg = lastRelevantEventId?.let { eventId ->
            withTimeoutOrNull(LAST_EVENT_TIMEOUT_MS) {
                c.room.getTimelineEvent(roomId, eventId).firstOrNull()
            }?.toLastMessage(c)
        }
        val tags = tagsFor(c, roomId.full)
        // DM rooms usually have no m.room.avatar: fall back to the peer's avatar.
        val roomAvatar = avatarUrl
            ?: if (isDirect) {
                name?.heroes?.firstOrNull { it != c.userId }?.let { hero ->
                    withTimeoutOrNull(RESOLVE_TIMEOUT_MS) {
                        c.user.getById(roomId, hero).filterNotNull().first()
                            .event.content.avatarUrl
                    }
                }
            } else null
        return ChatRoom(
            roomId = roomId.full,
            name = computeDisplayName(),
            topic = null,
            avatarUrl = roomAvatar,
            lastMessage = lastMsg,
            unreadCount = unreadMessageCount.toInt(),
            isDirect = isDirect,
            membersCount = (name?.heroes?.size ?: 0) + (name?.otherUsersCount ?: 0) + 1,
            isFavorite = FAVORITE_TAG in tags,
            isLowPriority = LOW_PRIORITY_TAG in tags,
            isEncrypted = encrypted,
            isMarkedUnread = markedUnread,
            bridge = runCatching {
                c.room.getAccountData(
                    roomId, com.matrix.messenger.data.bridge.BridgeEventContent::class
                ).firstOrNull()?.displayName
            }.getOrNull()
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
            is RoomMessageEventContent.VerificationRequest -> "Запрос верификации ключа"
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
        if (eventContent is CallInviteEventContent) {
            val isMine = sender == c.userId
            return Message(
                eventId = eventId.full,
                senderId = sender.full,
                senderName = c.resolveSenderName(roomId, sender),
                senderAvatarUrl = c.resolveSenderAvatar(roomId, sender),
                body = if (isMine) "Исходящий звонок" else "Входящий звонок",
                timestamp = originTimestamp,
                messageType = MessageType.CALL,
                isMine = isMine,
                isEdited = false,
                isDeleted = false
            )
        }
        if (eventContent is UnknownEventContent) {
            // Polls and call invites surface as dedicated message types;
            // other unknown events stay hidden.
            val poll = com.matrix.messenger.data.poll.parsePollStart(eventContent.raw)
            if (poll != null) {
                return Message(
                    eventId = eventId.full,
                    senderId = sender.full,
                    senderName = c.resolveSenderName(roomId, sender),
                    senderAvatarUrl = c.resolveSenderAvatar(roomId, sender),
                    body = poll.question,
                    timestamp = originTimestamp,
                    messageType = MessageType.POLL,
                    isMine = sender == c.userId,
                    isEdited = false,
                    isDeleted = false,
                    poll = poll
                )
            }
            if (eventContent.eventType == "m.sticker") {
                val url = (eventContent.raw["url"] as? kotlinx.serialization.json.JsonPrimitive)?.content
                if (url != null) {
                    return Message(
                        eventId = eventId.full,
                        senderId = sender.full,
                        senderName = c.resolveSenderName(roomId, sender),
                        senderAvatarUrl = c.resolveSenderAvatar(roomId, sender),
                        body = (eventContent.raw["body"] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: "Стикер",
                        timestamp = originTimestamp,
                        messageType = MessageType.STICKER,
                        isMine = sender == c.userId,
                        isEdited = false,
                        isDeleted = false,
                        mediaUrl = url,
                        encryptedFile = null
                    )
                }
                return null
            }
            if (eventContent.eventType == "m.call.invite") {
                val callEvent = com.matrix.messenger.data.call.parseCallEvent(
                    eventContent.eventType, eventContent.raw, roomId.full, sender.full
                ) as? com.matrix.messenger.data.call.CallSignalingEvent.Incoming
                val isVideo = callEvent?.isVideo == true
                val isMine = sender == c.userId
                return Message(
                    eventId = eventId.full,
                    senderId = sender.full,
                    senderName = c.resolveSenderName(roomId, sender),
                    senderAvatarUrl = c.resolveSenderAvatar(roomId, sender),
                    body = if (isMine) (if (isVideo) "Исходящий видеозвонок" else "Исходящий звонок")
                    else (if (isVideo) "Входящий видеозвонок" else "Входящий звонок"),
                    timestamp = originTimestamp,
                    messageType = MessageType.CALL,
                    isMine = isMine,
                    isEdited = false,
                    isDeleted = false
                )
            }
            return null
        }
        val isDeleted = eventContent is RedactedEventContent
        val messageContent = eventContent as? RoomMessageEventContent
        if (messageContent?.relatesTo is RelatesTo.Replace) return null
        // In-room key verification request (Element sends these into the DM):
        // render as an actionable verification bubble instead of the English fallback text.
        if (messageContent is RoomMessageEventContent.VerificationRequest && !isDeleted) {
            return Message(
                eventId = eventId.full,
                senderId = sender.full,
                senderName = c.resolveSenderName(roomId, sender),
                senderAvatarUrl = c.resolveSenderAvatar(roomId, sender),
                body = "Запрос верификации ключа",
                timestamp = originTimestamp,
                messageType = MessageType.VERIFICATION_REQUEST,
                isMine = sender == c.userId,
                isEdited = false,
                isDeleted = false,
                verificationDeviceId = messageContent.fromDevice
            )
        }
        val location = (messageContent as? RoomMessageEventContent.Location)
            ?.let { com.matrix.messenger.data.model.GeoUri.parse(it.geoUri) }
        val body = when {
            isDeleted -> ""
            messageContent is RoomMessageEventContent.Location ->
                messageContent.body

            messageContent is RoomMessageEventContent.FileBased ->
                messageContent.fileName ?: messageContent.body

            messageContent != null -> messageContent.body.stripReplyFallback()
            else -> return null
        }
        val replyTo = resolveReplyInfo(c, messageContent?.relatesTo)
        val mediaUrl = (messageContent as? RoomMessageEventContent.FileBased)
            ?.url?.takeIf { messageContent.file == null }
        val encryptedFile = (messageContent as? RoomMessageEventContent.FileBased)?.file
        if (encryptedFile != null) encryptedFiles[eventId.full] = encryptedFile
        if (mediaUrl != null) mediaMxcCache[eventId.full] = mediaUrl
        eventRoomMap[eventId.full] = roomId.full
        if (eventRoomMap.size > EVENT_ROOM_MAP_MAX) {
            eventRoomMap.entries.take(EVENT_ROOM_MAP_MAX / 2).forEach { eventRoomMap.remove(it.key) }
        }
        val senderName = c.resolveSenderName(roomId, sender)
        return Message(
            eventId = eventId.full,
            senderId = sender.full,
            senderName = senderName,
            senderAvatarUrl = c.resolveSenderAvatar(roomId, sender),
            isBot = com.matrix.messenger.data.model.isBotUser(sender.full, senderName),
            body = body,
            timestamp = originTimestamp,
            messageType = if (isDeleted) MessageType.TEXT else eventContent.toMessageType(),
            isMine = sender == c.userId,
            isEdited = !isDeleted && isReplaced,
            isDeleted = isDeleted,
            mediaUrl = mediaUrl,
            encryptedFile = encryptedFile,
            location = location,
            replyTo = replyTo,
            formattedBody = (messageContent as? RoomMessageEventContent.TextBased)?.formattedBody,
            threadRootId = (messageContent?.relatesTo as? RelatesTo.Thread)?.eventId?.full
        )
    }

    /** Resolves the quoted original of a reply from the local timeline (null if unavailable). */
    private suspend fun TimelineEvent.resolveReplyInfo(
        c: MatrixClient,
        relatesTo: RelatesTo?
    ): ReplyInfo? {
        val replyToEventId = (relatesTo as? RelatesTo.Reply)?.replyTo?.eventId ?: return null
        val original = withTimeoutOrNull(REPLY_RESOLVE_TIMEOUT_MS) {
            c.room.getTimelineEvent(roomId, replyToEventId).firstOrNull()
        } ?: return null
        val originalName = c.resolveSenderName(roomId, original.sender)
        val originalContent = original.content?.getOrNull() as? RoomMessageEventContent
        return if (originalContent != null) {
            ReplyInfo(
                eventId = replyToEventId.full,
                senderName = originalName,
                body = originalContent.body.stripReplyFallback().take(REPLY_PREVIEW_MAX),
                messageType = originalContent.toMessageType()
            )
        } else {
            ReplyInfo(replyToEventId.full, originalName, "Сообщение", MessageType.TEXT)
        }
    }

    /** Rich replies carry a "> <@user> quoted" fallback above the real body; keep only the real part. */
    private fun String.stripReplyFallback(): String =
        if (startsWith("> ")) substringAfter("\n\n", missingDelimiterValue = this) else this

    private fun RoomEventContent.toMessageType(): MessageType = when (this) {
        is RoomMessageEventContent.TextBased.Emote -> MessageType.EMOTE
        is RoomMessageEventContent.VerificationRequest -> MessageType.VERIFICATION_REQUEST
        is RoomMessageEventContent.FileBased.Image -> MessageType.IMAGE
        is RoomMessageEventContent.FileBased.Video -> MessageType.VIDEO
        is RoomMessageEventContent.FileBased.Audio -> MessageType.AUDIO
        is RoomMessageEventContent.FileBased.File -> MessageType.FILE
        is RoomMessageEventContent.Location -> MessageType.LOCATION
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

    private suspend fun MatrixClient.resolveSenderAvatar(roomId: RoomId, userId: UserId): String? {
        val key = "${roomId.full}|${userId.full}"
        userAvatarCache[key]?.let { return it }
        val mxc = withTimeoutOrNull(RESOLVE_TIMEOUT_MS) {
            user.getById(roomId, userId).filterNotNull().first().event.content.avatarUrl
        } ?: return null
        userAvatarCache[key] = mxc
        return mxc
    }

    /**
     * Resolves the homeserver base URL. A bare domain or user ID goes through
     * Matrix server discovery (.well-known) first; explicit http(s) URLs and
     * discovery failures fall back to https://<domain>.
     */
    private suspend fun normalizeHomeServer(homeServer: String): String {
        val input = homeServer.trim().trimEnd('/')
        if (!input.startsWith("http://") && !input.startsWith("https://")) {
            val domain = input.removePrefix("@").substringBefore(":")
            val discovered = runCatching {
                val proxy = runCatching { SessionStore.readProxySettings() }.getOrNull()
                domain.serverDiscovery(createProxyHttpClientFactory(proxy)).getOrNull()
            }.getOrNull()
            if (discovered != null) return discovered.toString().trimEnd('/')
        }
        var url = input
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
        const val DEFAULT_TURN_TTL_SECONDS = 300L
        const val REPLY_RESOLVE_TIMEOUT_MS = 300L
        const val REPLY_PREVIEW_MAX = 120
        const val TYPING_DISPLAY_TIMEOUT_MS = 6_000L
        const val PINNED_RESOLVE_TIMEOUT_MS = 400L
        const val SEARCH_SNIPPET_MAX = 200
        const val ROOM_TAGS_CACHE_MS = 120_000L
        const val FAVORITE_TAG = "m.favourite"
        /** Above this member count the trust shield shows "unknown" (cost guard). */
        const val TRUST_MAX_MEMBERS = 50
        const val LOW_PRIORITY_TAG = "m.lowpriority"
    }
}
