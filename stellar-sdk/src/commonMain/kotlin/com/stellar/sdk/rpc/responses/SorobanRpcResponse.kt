package com.stellar.sdk.rpc.responses

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents a JSON-RPC 2.0 response returned by the Soroban RPC server.
 *
 * All Soroban RPC API responses follow the JSON-RPC 2.0 protocol. This class provides
 * a type-safe wrapper for parsing responses and detecting errors.
 *
 * ## JSON-RPC 2.0 Response Structure
 *
 * A valid JSON-RPC 2.0 response contains:
 * - `jsonrpc`: Protocol version (always "2.0")
 * - `id`: Identifier matching the request ID
 * - Either:
 *   - `result`: The successful result data (when no error occurred)
 *   - `error`: Error information (when the request failed)
 *
 * A response MUST contain either `result` or `error`, but never both.
 *
 * ## Example Usage
 *
 * ```kotlin
 * val response: SorobanRpcResponse<GetHealthResponse> = parseResponse(json)
 *
 * if (response.isSuccess()) {
 *     val health = response.result!!
 *     println("Server status: ${health.status}")
 * } else if (response.isError()) {
 *     val error = response.error!!
 *     println("Error ${error.code}: ${error.message}")
 * }
 * ```
 *
 * @param T The type of the successful result data
 * @property jsonRpc The JSON-RPC protocol version (always "2.0")
 * @property id The request identifier this response corresponds to
 * @property result The successful result data (null if an error occurred)
 * @property error Error information (null if the request succeeded)
 *
 * @see <a href="https://www.jsonrpc.org/specification#response_object">JSON-RPC 2.0 Specification - Response object</a>
 * @see <a href="https://developers.stellar.org/docs/data/rpc/api-reference">Soroban RPC API Reference</a>
 */
@Serializable
data class SorobanRpcResponse<T>(
    /**
     * JSON-RPC protocol version.
     *
     * This field is always "2.0" per the JSON-RPC 2.0 specification.
     * Uses @SerialName to ensure correct JSON field name "jsonrpc".
     */
    @SerialName("jsonrpc")
    val jsonRpc: String,

    /**
     * Request identifier matching the original request.
     *
     * This ID matches the ID from the corresponding SorobanRpcRequest.
     * Used to correlate responses with requests in async environments.
     */
    val id: String,

    /**
     * Successful result data.
     *
     * Contains the method-specific result data when the request succeeded.
     * This field is null when an error occurred (error field will be populated instead).
     */
    val result: T? = null,

    /**
     * Error information.
     *
     * Contains error details when the request failed.
     * This field is null when the request succeeded (result field will be populated instead).
     */
    val error: Error? = null
) {
    /**
     * Represents a JSON-RPC 2.0 error object.
     *
     * ## Error Code Ranges
     *
     * Standard JSON-RPC 2.0 error codes:
     * - `-32700`: Parse error
     * - `-32600`: Invalid Request
     * - `-32601`: Method not found
     * - `-32602`: Invalid params
     * - `-32603`: Internal error
     * - `-32000 to -32099`: Server error (implementation-defined)
     *
     * @property code Numeric error code identifying the error type
     * @property message Human-readable error message
     * @property data Optional additional error data (implementation-specific)
     *
     * @see <a href="https://www.jsonrpc.org/specification#error_object">JSON-RPC 2.0 Specification - Error object</a>
     */
    @Serializable
    data class Error(
        /**
         * Numeric error code.
         *
         * See JSON-RPC 2.0 specification for standard error code ranges.
         */
        val code: Int,

        /**
         * Human-readable error message.
         *
         * A short description of the error. Should be limited to a concise single sentence.
         */
        val message: String,

        /**
         * Additional error data.
         *
         * Optional field containing additional information about the error.
         * The structure is implementation-specific and may contain debugging details.
         */
        val data: String? = null
    )

    /**
     * Checks if this response represents a successful result.
     *
     * A response is considered successful when the error field is null,
     * indicating that the RPC method executed without errors.
     *
     * @return true if the response contains a successful result, false otherwise
     */
    fun isSuccess(): Boolean = error == null

    /**
     * Checks if this response represents an error.
     *
     * A response is considered an error when the error field is not null,
     * indicating that the RPC method execution failed.
     *
     * @return true if the response contains an error, false otherwise
     */
    fun isError(): Boolean = error != null

    /**
     * Returns a string representation of this response.
     *
     * Includes the ID and either the result or error information.
     */
    override fun toString(): String {
        return buildString {
            append("SorobanRpcResponse(jsonRpc='$jsonRpc', id='$id'")
            if (isSuccess()) {
                append(", result=$result")
            } else {
                append(", error=$error")
            }
            append(")")
        }
    }
}
