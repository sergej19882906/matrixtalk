package com.matrix.messenger.data.repository

import io.ktor.resources.Resource
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType
import net.folivo.trixnity.core.MatrixEndpoint

/**
 * POST /_matrix/client/v3/users/{userId}/report — user reports are not part of
 * trixnity 4.9.2, so this endpoint is declared locally (same shape as spec).
 */
@Serializable
@Resource("/_matrix/client/v3/users/{userId}/report")
@HttpMethod(HttpMethodType.POST)
class ReportUserEndpoint(val userId: String) : MatrixEndpoint<ReportUserEndpoint.Request, Unit> {
    @Serializable
    data class Request(
        @SerialName("score") val score: Int? = null,
        @SerialName("reason") val reason: String? = null,
    )
}
