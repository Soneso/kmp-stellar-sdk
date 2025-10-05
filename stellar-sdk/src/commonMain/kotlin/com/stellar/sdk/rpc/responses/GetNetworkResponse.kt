package com.stellar.sdk.rpc.responses

import kotlinx.serialization.Serializable

/**
 * Response for JSON-RPC method getNetwork.
 *
 * Returns general information about the Stellar network this RPC server is connected to.
 * This is useful for confirming network connectivity and obtaining network-specific configuration.
 *
 * @property friendbotUrl URL of the Friendbot service for this network, if available.
 *                        Friendbot is a testnet service that funds test accounts with test XLM.
 *                        This will be null on public networks (mainnet) where no friendbot exists.
 * @property passphrase The network passphrase. This is a unique string identifier for the network
 *                      (e.g., "Test SDF Network ; September 2015" for testnet or
 *                      "Public Global Stellar Network ; September 2015" for mainnet).
 *                      The passphrase is used in transaction signing to prevent replay attacks
 *                      across different networks.
 * @property protocolVersion The current Stellar protocol version supported by the network.
 *
 * @see [Stellar Soroban RPC getNetwork documentation](https://developers.stellar.org/docs/data/rpc/api-reference/methods/getNetwork)
 */
@Serializable
data class GetNetworkResponse(
    val friendbotUrl: String? = null,
    val passphrase: String,
    val protocolVersion: Int
)
