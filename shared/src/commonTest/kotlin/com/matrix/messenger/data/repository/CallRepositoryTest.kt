package com.matrix.messenger.data.repository

import com.matrix.messenger.call.WebRtcEngine
import com.matrix.messenger.call.WebRtcEvent
import com.matrix.messenger.data.call.CallCandidate
import com.matrix.messenger.data.call.CallSignalingCommand
import com.matrix.messenger.data.call.CallSignalingEvent
import com.matrix.messenger.data.model.CallState
import com.matrix.messenger.data.model.ChatRoom
import com.matrix.messenger.data.model.ConnectionState
import com.matrix.messenger.data.model.DeviceInfo
import com.matrix.messenger.data.model.VerificationUiState
import com.matrix.messenger.data.model.IceServer
import com.matrix.messenger.data.model.LoginResult
import com.matrix.messenger.data.model.MatrixUser
import com.matrix.messenger.data.model.SearchResultItem
import com.matrix.messenger.data.model.Message
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val testSdp = "v=0\r\nm=audio 9 UDP/TLS/RTP/SAVPF 111\r\n"

class CallRepositoryTest {

    private class FakeMatrixRepository : MatrixRepository {
        val callEventsFlow = MutableSharedFlow<CallSignalingEvent>(replay = 16)
        val sentCommands = mutableListOf<Pair<String, CallSignalingCommand>>()

        override val callEvents: Flow<CallSignalingEvent> = callEventsFlow

        override suspend fun sendCallEvent(roomId: String, command: CallSignalingCommand) {
            sentCommands.add(roomId to command)
        }

        override suspend fun getTurnServers(): List<IceServer> = emptyList()
        override suspend fun resolveDirectChatPeerId(roomId: String): String? = null
        override suspend fun searchMessages(query: String, limit: Int): List<SearchResultItem> = emptyList()
        override val currentUser: Flow<MatrixUser?> = MutableStateFlow(null)
        override val connectionState: Flow<ConnectionState> = MutableStateFlow(ConnectionState.Connected)
        override suspend fun initialize() = Unit
        override suspend fun login(homeServer: String, username: String, password: String) = LoginResult.Error("test")
        override suspend fun loginWithToken(homeServer: String, userId: String, accessToken: String) = LoginResult.Error("test")
        override suspend fun logout() = Unit
        override fun getRoomsFlow(): Flow<List<ChatRoom>> = MutableStateFlow(emptyList())
        override fun getMessagesFlow(roomId: String): Flow<List<Message>> = MutableStateFlow(emptyList())
        override suspend fun loadEarlierMessages(roomId: String) = Unit
        override suspend fun canLoadMoreMessages(roomId: String) = true
        override suspend fun resolveMediaFile(eventId: String, fileName: String): String? = null
        override suspend fun searchUsers(query: String): List<MatrixUser> = emptyList()
        override suspend fun sendTextMessage(roomId: String, text: String) = Unit
        override suspend fun replyToMessage(roomId: String, eventId: String, text: String) = Unit
        override suspend fun sendFileMessage(roomId: String, filePath: String, mimeType: String, caption: String?) = Unit
        override suspend fun getRoomInfo(roomId: String): ChatRoom? = null
        override suspend fun createRoom(name: String?, topic: String?, isDirect: Boolean, userIds: List<String>): String = "!room:test"
        override suspend fun joinRoom(aliasOrId: String): String = aliasOrId
        override suspend fun leaveRoom(roomId: String) = Unit
        override suspend fun markRoomAsRead(roomId: String) = Unit
        override fun getPinnedMessagesFlow(roomId: String): Flow<List<Message>> = MutableStateFlow(emptyList())
        override suspend fun setPinnedMessage(roomId: String, eventId: String, pinned: Boolean) = Unit
        override suspend fun sendTypingNotification(roomId: String, isTyping: Boolean) = Unit
        override fun getTypingUsersFlow(roomId: String): Flow<List<String>> = MutableStateFlow(emptyList())
        override suspend fun sendReaction(eventId: String, reaction: String) = Unit
        override suspend fun editMessage(eventId: String, roomId: String, newText: String) = Unit
        override suspend fun deleteMessage(eventId: String, roomId: String) = Unit
        override suspend fun uploadAvatar(filePath: String, mimeType: String) = Unit
        override suspend fun setDisplayName(name: String) = Unit
        override suspend fun getDevices(): List<DeviceInfo> = emptyList()
        override suspend fun renameDevice(deviceId: String, newName: String) = Unit
        override suspend fun deleteDevice(deviceId: String, passwordProvider: suspend () -> String?) = Unit
        override suspend fun currentDeviceId(): String? = null
        override suspend fun currentUserId(): String? = null
        override fun getKeyBackupVersionFlow(): Flow<String?> = MutableStateFlow(null)
        override suspend fun bootstrapKeyBackup(passwordProvider: suspend () -> String?): String = ""
        override val verificationState: Flow<VerificationUiState> = MutableStateFlow(VerificationUiState.Idle)
        override suspend fun startDeviceVerification(deviceId: String) = Unit
        override suspend fun confirmVerification(matches: Boolean) = Unit
        override suspend fun cancelVerification() = Unit
    }

    private class FakeWebRtcEngine : WebRtcEngine {
        val eventsFlow = MutableSharedFlow<WebRtcEvent>(replay = 16)
        val accepted = mutableListOf<Triple<String, String, Boolean>>()
        val hangups = mutableListOf<String>()
        val mutedStates = mutableListOf<Boolean>()

        override val events: Flow<WebRtcEvent> = eventsFlow
        override val remoteVideoTrack: Flow<Any?> = MutableStateFlow(null)
        override val localVideoTrack: Flow<Any?> = MutableStateFlow(null)
        override val eglContext: Any? = null

        override fun initialize() = Unit
        override suspend fun startCall(callId: String, isVideo: Boolean, screenShareSourceId: Long?) {
            eventsFlow.tryEmit(WebRtcEvent.Offer(callId, testSdp))
        }
        override suspend fun acceptCall(callId: String, offerSdp: String, isVideo: Boolean) {
            accepted.add(Triple(callId, offerSdp, isVideo))
            eventsFlow.tryEmit(WebRtcEvent.Answer(callId, testSdp))
        }
        override suspend fun setRemoteAnswer(callId: String, answerSdp: String) = Unit
        override suspend fun addCandidates(callId: String, candidates: List<CallCandidate>) = Unit
        override suspend fun hangup(callId: String) { hangups.add(callId) }
        override fun setIceServers(servers: List<Triple<String, String?, String?>>) = Unit
        override fun setMuted(muted: Boolean) { mutedStates.add(muted) }
        override fun setVideoEnabled(enabled: Boolean) = Unit
        override fun switchCamera() = Unit
        override fun setSpeakerOn(speakerOn: Boolean) = Unit
        override fun release() = Unit
    }

    private fun waitFor(timeoutMs: Long = 2000, condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return true
            Thread.sleep(20)
        }
        return condition()
    }

    @Test
    fun incomingCallAcceptFlow() = runBlocking {
        val matrix = FakeMatrixRepository()
        val engine = FakeWebRtcEngine()
        val repository = CallRepository(matrix, engine)

        matrix.callEventsFlow.emit(
            CallSignalingEvent.Incoming("!room:test", "call1", "@caller:test", testSdp, isVideo = false)
        )
        assertTrue(waitFor { repository.callState.value is CallState.Incoming })

        repository.acceptCall()
        assertTrue(waitFor { engine.accepted.isNotEmpty() })
        assertEquals("call1", engine.accepted[0].first)
        assertEquals(testSdp, engine.accepted[0].second)

        // Engine answer -> Answer command sent to the room
        assertTrue(waitFor { matrix.sentCommands.any { it.second is CallSignalingCommand.Answer } })
    }

    @Test
    fun outgoingCallSendsInvite() = runBlocking {
        val matrix = FakeMatrixRepository()
        val engine = FakeWebRtcEngine()
        val repository = CallRepository(matrix, engine)

        repository.startCall("!room:test", "@peer:test", "Peer", isVideo = false)
        assertTrue(waitFor { repository.callState.value is CallState.Outgoing })
        assertTrue(waitFor { matrix.sentCommands.any { it.second is CallSignalingCommand.Invite } })
        val invite = matrix.sentCommands.map { it.second }.filterIsInstance<CallSignalingCommand.Invite>().first()
        assertEquals(testSdp, invite.offerSdp)
    }

    @Test
    fun secondIncomingCallWhileBusyIsRejected() = runBlocking {
        val matrix = FakeMatrixRepository()
        val engine = FakeWebRtcEngine()
        val repository = CallRepository(matrix, engine)

        repository.startCall("!room:test", "@peer:test", "Peer", isVideo = false)
        assertTrue(waitFor { repository.callState.value is CallState.Outgoing })

        matrix.callEventsFlow.emit(
            CallSignalingEvent.Incoming("!room:test", "call2", "@other:test", testSdp, isVideo = false)
        )
        assertTrue(waitFor { matrix.sentCommands.any { it.second is CallSignalingCommand.Reject } })
        // The ongoing call is untouched.
        assertTrue(repository.callState.value is CallState.Outgoing)
    }

    @Test
    fun peerHangupResetsToIdle() = runBlocking {
        val matrix = FakeMatrixRepository()
        val engine = FakeWebRtcEngine()
        val repository = CallRepository(matrix, engine)

        matrix.callEventsFlow.emit(
            CallSignalingEvent.Incoming("!room:test", "call1", "@caller:test", testSdp, isVideo = false)
        )
        assertTrue(waitFor { repository.callState.value is CallState.Incoming })

        matrix.callEventsFlow.emit(CallSignalingEvent.Hangup("!room:test", "call1"))
        assertTrue(waitFor { repository.callState.value is CallState.Idle })
        assertTrue(engine.hangups.contains("call1"))
    }

    @Test
    fun connectionEstablishedMovesToConnected() = runBlocking {
        val matrix = FakeMatrixRepository()
        val engine = FakeWebRtcEngine()
        val repository = CallRepository(matrix, engine)

        matrix.callEventsFlow.emit(
            CallSignalingEvent.Incoming("!room:test", "call1", "@caller:test", testSdp, isVideo = false)
        )
        assertTrue(waitFor { repository.callState.value is CallState.Incoming })
        repository.acceptCall()
        engine.eventsFlow.emit(WebRtcEvent.ConnectionEstablished)
        assertTrue(waitFor { repository.callState.value is CallState.Connected })
    }

    @Test
    fun engineErrorEndsCallWithMessage() = runBlocking {
        val matrix = FakeMatrixRepository()
        val engine = FakeWebRtcEngine()
        val repository = CallRepository(matrix, engine)

        matrix.callEventsFlow.emit(
            CallSignalingEvent.Incoming("!room:test", "call1", "@caller:test", testSdp, isVideo = false)
        )
        assertTrue(waitFor { repository.callState.value is CallState.Incoming })

        engine.eventsFlow.emit(WebRtcEvent.Error("Не удалось инициализировать аудио"))
        assertTrue(waitFor { repository.callState.value is CallState.Ended })
        val ended = repository.callState.value as CallState.Ended
        assertEquals("Не удалось инициализировать аудио", ended.reason)
    }

    @Test
    fun connectionFailedSendsHangupAndEnds() = runBlocking {
        val matrix = FakeMatrixRepository()
        val engine = FakeWebRtcEngine()
        val repository = CallRepository(matrix, engine)

        repository.startCall("!room:test", "@peer:test", "Peer", isVideo = false)
        assertTrue(waitFor { repository.callState.value is CallState.Outgoing })

        val callId = repository.getCurrentCallId()
        assertTrue(callId != null)
        engine.eventsFlow.emit(WebRtcEvent.ConnectionFailed(callId!!))
        assertTrue(waitFor { repository.callState.value is CallState.Ended })
        assertTrue(matrix.sentCommands.any { it.second is CallSignalingCommand.Hangup })
    }

    @Test
    fun muteTogglePropagatesToEngine() {
        val engine = FakeWebRtcEngine()
        val repository = CallRepository(FakeMatrixRepository(), engine)
        repository.toggleMute(true)
        repository.toggleMute(false)
        assertEquals(listOf(true, false), engine.mutedStates)
    }
}
