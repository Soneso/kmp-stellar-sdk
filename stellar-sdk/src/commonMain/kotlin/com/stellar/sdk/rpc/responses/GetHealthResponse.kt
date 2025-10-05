package com.stellar.sdk.rpc.responses

import kotlinx.serialization.Serializable

/**
 * Response for JSON-RPC method getHealth.
 *
 * General node health check endpoint. The RPC server will return a 200 OK response
 * if the node is healthy, with additional metadata about the ledger state.
 *
 * @property status Health status. Expected to be "healthy" when the node is operational.
 * @property latestLedger The latest ledger known to the node. May be null if the node hasn't synced any ledgers yet.
 * @property oldestLedger The oldest ledger stored by the node. May be null if the node hasn't synced any ledgers yet.
 * @property ledgerRetentionWindow The ledger retention window configured for the node. Represents how many ledgers
 *                                  the node keeps in storage. May be null if not configured.
 *
 * @see [Stellar Soroban RPC getHealth documentation](https://developers.stellar.org/docs/data/rpc/api-reference/methods/getHealth)
 */
@Serializable
data class GetHealthResponse(
    val status: String,
    val latestLedger: Long? = null,
    val oldestLedger: Long? = null,
    val ledgerRetentionWindow: Long? = null
)
