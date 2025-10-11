package com.soneso.stellar.sdk.rpc.responses

import kotlinx.serialization.Serializable

/**
 * Response for JSON-RPC method getLatestLedger.
 *
 * Returns metadata about the latest (most recent) ledger known to the Soroban RPC server.
 * This is commonly used to check the current state of the network and ensure the server
 * is properly synced.
 *
 * @property id Hex-encoded hash identifier of the latest ledger. This is the unique cryptographic
 *              hash of the ledger header.
 * @property protocolVersion The Stellar protocol version in effect for this ledger.
 * @property sequence The ledger sequence number. Ledgers are numbered sequentially starting from 1
 *                    at network genesis. This number increases by 1 with each new ledger (approximately
 *                    every 5 seconds on the Stellar network).
 *
 * @see [Stellar Soroban RPC getLatestLedger documentation](https://developers.stellar.org/docs/data/rpc/api-reference/methods/getLatestLedger)
 */
@Serializable
data class GetLatestLedgerResponse(
    val id: String,
    val protocolVersion: Int,
    val sequence: Long
)
