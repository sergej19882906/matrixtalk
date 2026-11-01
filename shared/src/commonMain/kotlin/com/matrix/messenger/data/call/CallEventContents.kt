package com.matrix.messenger.data.call

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import net.folivo.trixnity.core.model.events.MessageEventContent
import net.folivo.trixnity.core.model.events.m.Mentions
import net.folivo.trixnity.core.model.events.m.RelatesTo
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMapping
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings
import net.folivo.trixnity.core.serialization.events.MessageEventContentSerializerMapping
import net.folivo.trixnity.core.serialization.events.StateEventContentSerializerMapping
import net.folivo.trixnity.core.serialization.events.DefaultEventContentSerializerMappings
import net.folivo.trixnity.core.model.events.EphemeralEventContent
import net.folivo.trixnity.core.model.events.EphemeralDataUnitContent
import net.folivo.trixnity.core.model.events.GlobalAccountDataEventContent
import net.folivo.trixnity.core.model.events.RoomAccountDataEventContent
import net.folivo.trixnity.core.model.events.StateEventContent
import net.folivo.trixnity.core.model.events.ToDeviceEventContent
import org.koin.core.module.Module

/**
 * Typed m.call.* event contents (Matrix VoIP v0).
 *
 * Registering them in the event content serializer mappings does two things:
 * 1. megolm-encrypted m.call.* events from Element decrypt into typed contents
 *    (in encrypted rooms Element DROPS plaintext call signaling, so calls there
 *    must go through the encrypted pipeline);
 * 2. the Realm outbox can persist call signaling, so it can be sent through
 *    room.sendMessage (outbox retry included).
 */
@Serializable
data class CallSessionDescription(
    @SerialName("type") val type: String,
    @SerialName("sdp") val sdp: String,
)

@Serializable
data class CallInviteEventContent(
    @SerialName("call_id") val callId: String,
    @SerialName("offer") val offer: CallSessionDescription,
    @SerialName("lifetime") val lifetime: Long = CALL_LIFETIME_MS.toLong(),
    @SerialName("version") val version: String = CALL_VERSION,
    @SerialName("party_id") val partyId: String? = null,
    @SerialName("invitee") val invitee: String? = null,
    @SerialName("m.relates_to") override val relatesTo: RelatesTo? = null,
    @SerialName("m.mentions") override val mentions: Mentions? = null,
    override val externalUrl: String? = null,
) : MessageEventContent

@Serializable
data class CallAnswerEventContent(
    @SerialName("call_id") val callId: String,
    @SerialName("answer") val answer: CallSessionDescription,
    @SerialName("version") val version: String = CALL_VERSION,
    @SerialName("party_id") val partyId: String? = null,
    @SerialName("m.relates_to") override val relatesTo: RelatesTo? = null,
    @SerialName("m.mentions") override val mentions: Mentions? = null,
    override val externalUrl: String? = null,
) : MessageEventContent

@Serializable
data class CallCandidatePayload(
    @SerialName("candidate") val candidate: String,
    @SerialName("sdpMid") val sdpMid: String? = null,
    @SerialName("sdpMLineIndex") val sdpMLineIndex: Int? = null,
)

@Serializable
data class CallCandidatesEventContent(
    @SerialName("call_id") val callId: String,
    @SerialName("candidates") val candidates: List<CallCandidatePayload>,
    @SerialName("version") val version: String = CALL_VERSION,
    @SerialName("party_id") val partyId: String? = null,
    @SerialName("m.relates_to") override val relatesTo: RelatesTo? = null,
    @SerialName("m.mentions") override val mentions: Mentions? = null,
    override val externalUrl: String? = null,
) : MessageEventContent

@Serializable
data class CallHangupEventContent(
    @SerialName("call_id") val callId: String,
    @SerialName("version") val version: String = CALL_VERSION,
    @SerialName("party_id") val partyId: String? = null,
    @SerialName("m.relates_to") override val relatesTo: RelatesTo? = null,
    @SerialName("m.mentions") override val mentions: Mentions? = null,
    override val externalUrl: String? = null,
) : MessageEventContent

@Serializable
data class CallRejectEventContent(
    @SerialName("call_id") val callId: String,
    @SerialName("version") val version: String = CALL_VERSION,
    @SerialName("party_id") val partyId: String? = null,
    @SerialName("m.relates_to") override val relatesTo: RelatesTo? = null,
    @SerialName("m.mentions") override val mentions: Mentions? = null,
    override val externalUrl: String? = null,
) : MessageEventContent

@OptIn(kotlinx.serialization.InternalSerializationApi::class)
private inline fun <reified T : MessageEventContent> callMapping(type: String): MessageEventContentSerializerMapping =
    MessageEventContentSerializerMapping(type, T::class, T::class.serializer())

private val callMessageMappings: Set<MessageEventContentSerializerMapping> = setOf(
    callMapping<CallInviteEventContent>("m.call.invite"),
    callMapping<CallAnswerEventContent>("m.call.answer"),
    callMapping<CallCandidatesEventContent>("m.call.candidates"),
    callMapping<CallHangupEventContent>("m.call.hangup"),
    callMapping<CallRejectEventContent>("m.call.reject"),
)

private val callEventContentSerializerMappings = object : EventContentSerializerMappings {
    override val message = callMessageMappings
    override val state = emptySet<StateEventContentSerializerMapping>()
    override val ephemeral = emptySet<EventContentSerializerMapping<EphemeralEventContent>>()
    override val ephemeralDataUnit = emptySet<EventContentSerializerMapping<EphemeralDataUnitContent>>()
    override val toDevice = emptySet<EventContentSerializerMapping<ToDeviceEventContent>>()
    override val globalAccountData = emptySet<EventContentSerializerMapping<GlobalAccountDataEventContent>>()
    override val roomAccountData = emptySet<EventContentSerializerMapping<RoomAccountDataEventContent>>()
}

/** Injected via MatrixClientConfiguration.modules: adds typed m.call.* mappings. */
val callEventContentSerializerMappingsModule: Module = org.koin.dsl.module {
    single<EventContentSerializerMappings> {
        DefaultEventContentSerializerMappings + callEventContentSerializerMappings
    }
}
