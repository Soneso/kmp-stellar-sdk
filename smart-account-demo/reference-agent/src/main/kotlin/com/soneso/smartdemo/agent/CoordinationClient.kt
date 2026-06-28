package com.soneso.smartdemo.agent

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
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
 * A coordination-server request record.
 *
 * Mirrors the canonical request object served by the coordination server. All
 * fields are present in a server response; optional fields are `null` until the
 * request is resolved. The amount defaults to the empty string when absent.
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

/** Body of `POST /requests`. The amount is omitted from the wire when null. */
@Serializable
private data class CreateRequestBody(
    val smartAccount: String,
    val target: String,
    val targetFn: String,
    val args: List<String>,
    val amount: String? = null,
    val reason: Int,
)

/** Thrown when a coordination-server call fails or returns an error response. */
class CoordinationException(
    override val message: String,
    val statusCode: Int? = null,
) : Exception(message)

/**
 * Abstraction over the coordination server's REST contract.
 *
 * Behind an interface so the agent runner can be unit-tested with a fake that
 * returns canned responses, without a live server or network access.
 */
interface CoordinationClient {
    /**
     * Posts a rejected call to `POST /requests`.
     *
     * [args] is the list of base64-encoded `SCValXdr` strings — the exact call
     * arguments, so the inbox can rebuild the call verbatim. [reason] is the
     * integer contract error code. Returns the created record with a
     * server-assigned id and `pending` status.
     */
    suspend fun createRequest(
        smartAccount: String,
        target: String,
        targetFn: String,
        args: List<String>,
        amount: String?,
        reason: Int,
    ): CoordinationRequest

    /** Fetches one request from `GET /requests/{id}` to poll its status. */
    suspend fun getRequest(id: String): CoordinationRequest
}

/**
 * [CoordinationClient] backed by the coordination server's HTTP API.
 *
 * Sends `Authorization: Bearer <token>` on every `/requests*` call and maps
 * non-2xx responses (JSON `{ "error": "..." }`) to [CoordinationException]. The
 * [HttpClient] is injected so tests can drive it with a mock engine.
 */
class HttpCoordinationClient(
    baseUrl: String,
    private val token: String,
    private val client: HttpClient,
) : CoordinationClient {

    private val base = baseUrl.trimEnd('/')

    override suspend fun createRequest(
        smartAccount: String,
        target: String,
        targetFn: String,
        args: List<String>,
        amount: String?,
        reason: Int,
    ): CoordinationRequest {
        val body = encodeJson.encodeToString(
            CreateRequestBody.serializer(),
            CreateRequestBody(smartAccount, target, targetFn, args, amount, reason),
        )
        val response = try {
            client.post("$base/requests") {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(body)
            }
        } catch (e: Exception) {
            throw CoordinationException("POST /requests failed: ${e.message}")
        }
        return parse(response, expectedStatus = 201, context = "create")
    }

    override suspend fun getRequest(id: String): CoordinationRequest {
        val response = try {
            client.get("$base/requests/${id.encodeURLPathPart()}") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
        } catch (e: Exception) {
            throw CoordinationException("GET /requests/$id failed: ${e.message}")
        }
        return parse(response, expectedStatus = 200, context = "poll")
    }

    private suspend fun parse(
        response: HttpResponse,
        expectedStatus: Int,
        context: String,
    ): CoordinationRequest {
        val text = response.bodyAsText()
        if (response.status.value != expectedStatus) {
            throw CoordinationException(
                "$context returned ${response.status.value}: ${errorBody(text)}",
                statusCode = response.status.value,
            )
        }
        return try {
            decodeJson.decodeFromString(CoordinationRequest.serializer(), text)
        } catch (e: Exception) {
            throw CoordinationException("$context returned malformed JSON: ${e.message}")
        }
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
