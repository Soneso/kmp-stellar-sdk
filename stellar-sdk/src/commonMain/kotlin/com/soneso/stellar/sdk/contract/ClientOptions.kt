package com.soneso.stellar.sdk.contract

import com.soneso.stellar.sdk.KeyPair
import com.soneso.stellar.sdk.Network

/**
 * Configuration options for the SorobanClient.
 *
 * @property sourceAccountKeyPair The keypair of the source account that will send transactions.
 *                                If [restore] is needed in method options, must contain private key.
 * @property contractId The contract ID (C... address) to interact with
 * @property network The Stellar network (TESTNET, PUBLIC, etc.)
 * @property rpcUrl The RPC server URL (e.g., "https://soroban-testnet.stellar.org:443")
 * @property enableServerLogging Enable server logging for debugging (default: false)
 */
data class ClientOptions(
    val sourceAccountKeyPair: KeyPair,
    val contractId: String,
    val network: Network,
    val rpcUrl: String,
    val enableServerLogging: Boolean = false
)
