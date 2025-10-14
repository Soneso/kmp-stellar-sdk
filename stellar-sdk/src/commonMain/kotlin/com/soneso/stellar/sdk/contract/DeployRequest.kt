package com.soneso.stellar.sdk.contract

import com.soneso.stellar.sdk.KeyPair
import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.xdr.SCValXdr
import com.soneso.stellar.sdk.xdr.Uint256Xdr

/**
 * Request to deploy a contract with an optional constructor.
 *
 * The contract WASM must already be installed (via [SorobanClient.install]) before deployment.
 *
 * @property sourceAccountKeyPair The keypair that will send and sign the transaction (must contain private key)
 * @property network The Stellar network (TESTNET, PUBLIC, etc.)
 * @property rpcUrl The RPC server URL
 * @property wasmHash The hash of the installed WASM (hex string)
 * @property constructorArgs Constructor arguments for the __constructor method (default: empty)
 * @property salt Salt used to generate the contract ID (default: null for random)
 * @property methodOptions Method options for the deployment transaction (default: MethodOptions())
 * @property enableSorobanServerLogging Enable server logging for debugging (default: false)
 */
data class DeployRequest(
    val sourceAccountKeyPair: KeyPair,
    val network: Network,
    val rpcUrl: String,
    val wasmHash: String,
    val constructorArgs: List<SCValXdr>? = null,
    val salt: Uint256Xdr? = null,
    val methodOptions: MethodOptions = MethodOptions(),
    val enableSorobanServerLogging: Boolean = false
)
