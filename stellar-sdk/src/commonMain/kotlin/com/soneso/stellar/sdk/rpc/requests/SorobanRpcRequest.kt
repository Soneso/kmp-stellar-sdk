package com.soneso.stellar.sdk.rpc.requests

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents a JSON-RPC 2.0 request sent to the Soroban RPC server.
 *
 * All Soroban RPC API calls use the JSON-RPC 2.0 protocol over HTTP. This class provides
 * a type-safe wrapper for constructing requests with proper serialization.
 *
 * ## JSON-RPC 2.0 Request Structure
 *
 * A valid JSON-RPC 2.0 request contains:
 * - `jsonrpc`: Protocol version (always "2.0")
 * - `id`: Unique identifier for matching requests to responses
 * - `method`: Name of the RPC method to invoke
 * - `params`: Method-specific parameters (optional, can be null)
 *
 * ## Example Usage
 *
 * ```kotlin
 * // Create a request to get health status
 * val request = SorobanRpcRequest(
 *     id = "1",
 *     method = "getHealth",
 *     params = null
 * )
 *
 * // Create a request with parameters
 * val ledgerRequest = SorobanRpcRequest(
 *     id = "2",
 *     method = "getLedgerEntries",
 *     params = GetLedgerEntriesRequest(keys = listOf("..."))
 * )
 * ```
 *
 * ## Serialization
 *
 * This class uses kotlinx.serialization for JSON encoding/decoding. The `jsonrpc` field
 * is serialized as "jsonrpc" (not "jsonRpc") to comply with the JSON-RPC specification.
 *
 * @param T The type of the request parameters (can be nullable for parameter-less methods)
 * @property jsonRpc The JSON-RPC protocol version, always "2.0"
 * @property id Unique identifier for this request (used to match responses)
 * @property method The RPC method name to invoke
 * @property params Method-specific parameters (null for parameter-less methods)
 *
 * @see <a href="https://www.jsonrpc.org/specification#request_object">JSON-RPC 2.0 Specification - Request object</a>
 * @see <a href="https://developers.stellar.org/docs/data/rpc/api-reference">Soroban RPC API Reference</a>
 */
@Serializable
data class SorobanRpcRequest<T>(
    /**
     * JSON-RPC protocol version.
     *
     * This field is always "2.0" per the JSON-RPC 2.0 specification.
     * Uses @SerialName to ensure correct JSON field name "jsonrpc".
     */
    @SerialName("jsonrpc")
    val jsonRpc: String = "2.0",

    /**
     * Unique identifier for this request.
     *
     * This ID is used to match responses to their corresponding requests.
     * Commonly a UUID string or incrementing number. The server MUST
     * include this ID in its response.
     */
    val id: String,

    /**
     * The RPC method name to invoke.
     *
     * Common Soroban RPC methods include:
     * - "getHealth"
     * - "getNetwork"
     * - "getLatestLedger"
     * - "getLedgerEntries"
     * - "getTransaction"
     * - "getEvents"
     * - "simulateTransaction"
     * - "sendTransaction"
     */
    val method: String,

    /**
     * Method-specific parameters.
     *
     * The type and structure of params depends on the method being invoked.
     * Some methods do not require parameters, in which case this should be null.
     */
    val params: T? = null
)
