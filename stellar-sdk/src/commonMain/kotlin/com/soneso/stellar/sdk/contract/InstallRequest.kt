package com.soneso.stellar.sdk.contract

import com.soneso.stellar.sdk.KeyPair
import com.soneso.stellar.sdk.Network

/**
 * Request to install (upload) a contract WASM to the Stellar network.
 *
 * @property wasmBytes The contract WASM bytecode to install
 * @property sourceAccountKeyPair The keypair that will send and sign the transaction (must contain private key)
 * @property network The Stellar network (TESTNET, PUBLIC, etc.)
 * @property rpcUrl The RPC server URL
 * @property enableSorobanServerLogging Enable server logging for debugging (default: false)
 */
data class InstallRequest(
    val wasmBytes: ByteArray,
    val sourceAccountKeyPair: KeyPair,
    val network: Network,
    val rpcUrl: String,
    val enableSorobanServerLogging: Boolean = false
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as InstallRequest
        return wasmBytes.contentEquals(other.wasmBytes) &&
            sourceAccountKeyPair == other.sourceAccountKeyPair &&
            network == other.network &&
            rpcUrl == other.rpcUrl &&
            enableSorobanServerLogging == other.enableSorobanServerLogging
    }

    override fun hashCode(): Int {
        var result = wasmBytes.contentHashCode()
        result = 31 * result + sourceAccountKeyPair.hashCode()
        result = 31 * result + network.hashCode()
        result = 31 * result + rpcUrl.hashCode()
        result = 31 * result + enableSorobanServerLogging.hashCode()
        return result
    }
}
