package com.soneso.stellar.sdk.contract

import com.soneso.stellar.sdk.KeyPair
import com.soneso.stellar.sdk.Network

/**
 * Configuration options for ContractClient initialization and contract invocation.
 *
 * This class consolidates both client setup and invocation behavior options:
 * - Client setup: sourceAccountKeyPair, contractId, network, rpcUrl, logging
 * - Invocation behavior: baseFee, timeouts, simulation, restoration, auto-submit
 *
 * ## Client Setup Options
 *
 * @property sourceAccountKeyPair The keypair of the source account that will send transactions.
 *                                If [restore] is needed in method options, must contain private key.
 * @property contractId The contract ID (C... address) to interact with
 * @property network The Stellar network (TESTNET, PUBLIC, etc.)
 * @property rpcUrl The RPC server URL (e.g., "https://soroban-testnet.stellar.org:443")
 * @property enableServerLogging Enable server logging for debugging (default: false)
 *
 * ## Invocation Behavior Options
 *
 * These options control transaction building, simulation, and submission behavior
 * for contract method invocations via [ContractClient].
 *
 * @property baseFee The base fee for the transaction in stroops (default: 100)
 * @property transactionTimeout Transaction validity timeout in seconds (default: 300 = 5 minutes)
 * @property submitTimeout Polling timeout when submitting transactions in seconds (default: 30)
 * @property simulate Whether to simulate the transaction before submission (default: true)
 * @property restore Whether to auto-restore contract state if expired (default: true)
 * @property autoSubmit Whether to auto-submit write calls (default: true). When true,
 *           write calls are automatically signed and submitted. When false, only simulation
 *           is performed and you can inspect the transaction before submitting manually.
 */
data class ClientOptions(
    val sourceAccountKeyPair: KeyPair,
    val contractId: String,
    val network: Network,
    val rpcUrl: String,
    val enableServerLogging: Boolean = false,
    val baseFee: Int = 100,
    val transactionTimeout: Long = 300,
    val submitTimeout: Int = 30,
    val simulate: Boolean = true,
    val restore: Boolean = true,
    val autoSubmit: Boolean = true
)
