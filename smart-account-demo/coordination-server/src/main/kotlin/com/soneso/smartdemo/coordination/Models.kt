package com.soneso.smartdemo.coordination

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Lifecycle state of a coordination request.
 *
 * A request is created as [PENDING] and transitions exactly once to either
 * [APPROVED] or [REJECTED]. No other transition is permitted.
 */
@Serializable
enum class RequestStatus(
    /** The string used on the wire and in persisted JSON. */
    val wireName: String,
) {
    @SerialName("pending")
    PENDING("pending"),

    @SerialName("approved")
    APPROVED("approved"),

    @SerialName("rejected")
    REJECTED("rejected");

    companion object {
        /**
         * Parses a wire value into a [RequestStatus].
         *
         * Throws [ValidationException] when [value] is not a known status.
         */
        fun fromWire(value: String): RequestStatus =
            entries.firstOrNull { it.wireName == value }
                ?: throw ValidationException(
                    "status must be one of ${entries.joinToString(", ") { "'${it.wireName}'" }}"
                )
    }
}

/**
 * A policy-rejected smart-account call awaiting human approval.
 *
 * The [args] list holds base64-encoded `SCValXdr` entries that are opaque to
 * the server and are stored and returned verbatim so the inbox can rebuild the
 * original call exactly. The nullable fields are always present on the wire,
 * carrying JSON `null` while unset, so the emitted object matches the locked
 * wire contract field-for-field.
 */
@Serializable
data class SmartAccountRequest(
    val id: String,
    val smartAccount: String,
    val target: String,
    val targetFn: String,
    val args: List<String>,
    val amount: String,
    val reason: Int,
    val status: RequestStatus,
    val createdAt: Long,
    val resolvedAt: Long? = null,
    val resultHash: String? = null,
    val note: String? = null,
) {
    /** Whether this request has already transitioned out of [RequestStatus.PENDING]. */
    val isResolved: Boolean
        get() = status != RequestStatus.PENDING

    /**
     * Returns a resolved copy carrying the new terminal [status], the [resolvedAt]
     * timestamp, and any approval [resultHash] or rejection [note].
     */
    fun resolving(
        status: RequestStatus,
        resolvedAt: Long,
        resultHash: String? = null,
        note: String? = null,
    ): SmartAccountRequest = copy(
        status = status,
        resolvedAt = resolvedAt,
        resultHash = resultHash,
        note = note,
    )
}

/**
 * Validated input for `POST /requests`.
 *
 * The client supplies only the agent-controlled fields; the server assigns
 * `id`, `status`, and `createdAt`. Any server-assigned fields present in the
 * body are ignored on decode. [amount] is optional and defaults to the empty
 * string.
 */
@Serializable
data class CreateRequestInput(
    val smartAccount: String,
    val target: String,
    val targetFn: String,
    val args: List<String>,
    val amount: String = "",
    val reason: Int,
) {
    /**
     * Validates that the required string fields are non-empty.
     *
     * The serializer already enforces presence and type; this adds the
     * non-empty constraints the wire contract requires.
     */
    fun validated(): CreateRequestInput {
        requireNonEmpty(smartAccount, "smartAccount")
        requireNonEmpty(target, "target")
        requireNonEmpty(targetFn, "targetFn")
        return this
    }

    private fun requireNonEmpty(value: String, field: String) {
        if (value.isEmpty()) {
            throw ValidationException("field '$field' must not be empty")
        }
    }
}

/** Body of `POST /requests/{id}/approve`. */
@Serializable
data class ApproveBody(val resultHash: String? = null)

/** Body of `POST /requests/{id}/reject`. The note is optional. */
@Serializable
data class RejectBody(val note: String? = null)

/** Wire shape for the `/health` response. */
@Serializable
data class HealthResponse(val status: String)

/** Wire shape for the `GET /requests` list response. */
@Serializable
data class RequestListResponse(val requests: List<SmartAccountRequest>)

/** Wire shape for an error response: `{ "error": "..." }`. */
@Serializable
data class ErrorResponse(val error: String)
