package com.soneso.stellar.sdk.rpc.responses

import kotlinx.serialization.Serializable

/**
 * Response for JSON-RPC method getVersionInfo.
 *
 * Returns version information about the Soroban RPC server and its components.
 * This is useful for debugging, verifying server compatibility, and tracking deployment versions.
 *
 * @property version The version string of the Soroban RPC server (e.g., "21.0.0").
 * @property commitHash Git commit hash of the build, providing exact source code traceability.
 * @property buildTimestamp ISO 8601 timestamp of when the server binary was built.
 * @property captiveCoreVersion Version of the embedded Stellar Core (captive core) used by this RPC server.
 *                              Captive core is the underlying component that processes ledgers.
 * @property protocolVersion The Stellar protocol version supported by this server.
 *
 * @see [Stellar Soroban RPC getVersionInfo documentation](https://developers.stellar.org/docs/data/rpc/api-reference/methods/getVersionInfo)
 */
@Serializable
data class GetVersionInfoResponse(
    val version: String,
    val commitHash: String,
    val buildTimestamp: String,
    val captiveCoreVersion: String,
    val protocolVersion: Int
)
