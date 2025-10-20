package com.soneso.stellar.sdk.rpc.exception

import com.soneso.stellar.sdk.horizon.exceptions.NetworkException

/**
 * Exception thrown when Soroban RPC instance responds with an error.
 *
 * This exception represents errors returned by the Soroban RPC server according to the
 * JSON-RPC 2.0 specification. The error object contains a code, message, and optional data field.
 *
 * ## Common JSON-RPC Error Codes
 *
 * Standard JSON-RPC 2.0 error codes:
 * - `-32700` - Parse error: Invalid JSON was received by the server
 * - `-32600` - Invalid Request: The JSON sent is not a valid Request object
 * - `-32601` - Method not found: The method does not exist / is not available
 * - `-32602` - Invalid params: Invalid method parameter(s)
 * - `-32603` - Internal error: Internal JSON-RPC error
 * - `-32000 to -32099` - Server error: Reserved for implementation-defined server errors
 *
 * Soroban-specific error codes:
 * - `-32001` - Transaction submission failed
 * - `-32002` - Transaction simulation failed
 * - `-32003` - Ledger entry not found
 * - `-32004` - Contract invocation failed
 *
 * @property errorCode The JSON-RPC error code
 * @property data Optional additional error data provided by the server
 *
 * @see <a href="https://www.jsonrpc.org/specification#error_object">JSON-RPC 2.0 Specification - Error object</a>
 * @see <a href="https://developers.stellar.org/docs/data/rpc/api-reference">Soroban RPC API Reference</a>
 */
class SorobanRpcException(
    val errorCode: Int,
    override val message: String,
    val data: String? = null
) : NetworkException(
    message = "Soroban RPC error ($errorCode): $message",
    cause = null,
    code = errorCode,
    body = data
)
