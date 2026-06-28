package com.soneso.smartdemo.util

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.encodeURLPathPart
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * A coordination-server request record (the inbox side of the agent-signer flow).
 *
 * Mirrors the canonical request object served by the coordination server and the
 * reference agent's own client, so the two stay byte-for-byte consistent. All
 * fields are present in a server response; nullable fields are `null` until the
 * request is resolved. The [args] entries are base64-encoded `SCValXdr` strings,
 * opaque to the server and stored verbatim so the inbox can rebuild the original
 * call exactly. The [amount] is display-only — the inbox decodes the authoritative
 * amount from [args], never from this field.
 */
@Serializable
data class CoordinationRequest(
    val id: String,
    val smartAccount: String,
    val target: String,
    val targetFn: String,
    val args: List<String>,
    val amount: String = "",
    val reason: Int,
    val status: String,
    val createdAt: Long,
    val resolvedAt: Long? = null,
    val resultHash: String? = null,
    val note: String? = null,
) {
    /** Whether the request has reached a terminal state. */
    val isResolved: Boolean
        get() = status == STATUS_APPROVED || status == STATUS_REJECTED

    companion object {
        const val STATUS_PENDING = "pending"
        const val STATUS_APPROVED = "approved"
        const val STATUS_REJECTED = "rejected"
    }
}

/** Body of `POST /requests/{id}/approve`. */
@Serializable
private data class ApproveBody(val resultHash: String)

/** Body of `POST /requests/{id}/reject`. The note is omitted from the wire when null. */
@Serializable
private data class RejectBody(val note: String? = null)

/** Wrapper for the `GET /requests` listing, which nests the array under `requests`. */
@Serializable
private data class RequestListResponse(val requests: List<CoordinationRequest> = emptyList())

/** Thrown when a coordination-server call fails or returns an error response. */
class CoordinationException(
    override val message: String,
    val statusCode: Int? = null,
) : Exception(message)

/**
 * Inbox-facing subset of the coordination server's REST contract.
 *
 * Behind an interface so the approval-inbox flow and the pending-count poller can
 * be unit-tested against a fake or a Ktor `MockEngine`, without a live server or
 * network access. The agent side (`POST /requests`) lives in the reference agent.
 */
interface CoordinationClient {
    /** Lists every pending request via `GET /requests?status=pending`, newest first. */
    suspend fun listPending(): List<CoordinationRequest>

    /** Fetches one request via `GET /requests/{id}`. */
    suspend fun getRequest(id: String): CoordinationRequest

    /**
     * Approves a pending request via `POST /requests/{id}/approve` with
     * `{ "resultHash": <hash> }`. Returns the updated record.
     */
    suspend fun approve(id: String, resultHash: String): CoordinationRequest

    /**
     * Rejects a pending request via `POST /requests/{id}/reject` with an optional
     * `{ "note": <text> }` body. Returns the updated record.
     */
    suspend fun reject(id: String, note: String? = null): CoordinationRequest

    /** Releases any held resources (the HTTP client). */
    fun close()
}

/**
 * [CoordinationClient] backed by the coordination server's HTTP API.
 *
 * Sends `Authorization: Bearer <token>` on every `/requests*` call and maps non-2xx
 * responses (JSON `{ "error": "..." }`) to [CoordinationException]. The [HttpClient]
 * is injected so tests can drive it with a Ktor `MockEngine`; the default client is
 * created here and closed by [close].
 */
class HttpCoordinationClient(
    baseUrl: String,
    private val token: String,
    private val client: HttpClient = HttpClient(),
    private val ownsClient: Boolean = true,
) : CoordinationClient {

    private val base = baseUrl.trimEnd('/')

    override suspend fun listPending(): List<CoordinationRequest> {
        val response = try {
            client.get("$base/requests") {
                header(HttpHeaders.Authorization, "Bearer $token")
                parameter("status", CoordinationRequest.STATUS_PENDING)
            }
        } catch (e: Exception) {
            throw CoordinationException("GET /requests failed: ${e.message}")
        }
        val text = ensureStatus(response, expectedStatus = 200, context = "list")
        return try {
            decodeJson.decodeFromString(RequestListResponse.serializer(), text).requests
        } catch (e: Exception) {
            throw CoordinationException("list returned malformed JSON: ${e.message}")
        }
    }

    override suspend fun getRequest(id: String): CoordinationRequest {
        val response = try {
            client.get("$base/requests/${id.encodeURLPathPart()}") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
        } catch (e: Exception) {
            throw CoordinationException("GET /requests/$id failed: ${e.message}")
        }
        return parse(response, expectedStatus = 200, context = "get")
    }

    override suspend fun approve(id: String, resultHash: String): CoordinationRequest {
        val body = encodeJson.encodeToString(ApproveBody.serializer(), ApproveBody(resultHash))
        val response = try {
            client.post("$base/requests/${id.encodeURLPathPart()}/approve") {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(body)
            }
        } catch (e: Exception) {
            throw CoordinationException("POST /requests/$id/approve failed: ${e.message}")
        }
        return parse(response, expectedStatus = 200, context = "approve")
    }

    override suspend fun reject(id: String, note: String?): CoordinationRequest {
        val body = encodeJson.encodeToString(RejectBody.serializer(), RejectBody(note))
        val response = try {
            client.post("$base/requests/${id.encodeURLPathPart()}/reject") {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(body)
            }
        } catch (e: Exception) {
            throw CoordinationException("POST /requests/$id/reject failed: ${e.message}")
        }
        return parse(response, expectedStatus = 200, context = "reject")
    }

    override fun close() {
        if (ownsClient) client.close()
    }

    private suspend fun parse(
        response: HttpResponse,
        expectedStatus: Int,
        context: String,
    ): CoordinationRequest {
        val text = ensureStatus(response, expectedStatus, context)
        return try {
            decodeJson.decodeFromString(CoordinationRequest.serializer(), text)
        } catch (e: Exception) {
            throw CoordinationException("$context returned malformed JSON: ${e.message}")
        }
    }

    private suspend fun ensureStatus(
        response: HttpResponse,
        expectedStatus: Int,
        context: String,
    ): String {
        val text = response.bodyAsText()
        if (response.status.value != expectedStatus) {
            throw CoordinationException(
                "$context returned ${response.status.value}: ${errorBody(text)}",
                statusCode = response.status.value,
            )
        }
        return text
    }

    private fun errorBody(text: String): String =
        try {
            decodeJson.parseToJsonElement(text).jsonObject["error"]?.jsonPrimitive?.content
                ?: text.ifEmpty { "(empty body)" }
        } catch (_: Exception) {
            text.ifEmpty { "(empty body)" }
        }

    private companion object {
        val encodeJson = Json { explicitNulls = false; encodeDefaults = true }
        val decodeJson = Json { ignoreUnknownKeys = true }
    }
}
