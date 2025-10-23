package com.soneso.demo.stellar

import com.soneso.stellar.sdk.KeyPair
import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.contract.ContractClient
import com.soneso.demo.util.StellarValidation

/**
 * Contract metadata for the deployment UI.
 *
 * Contains all information needed to display and deploy a demo contract.
 *
 * @property id Unique identifier for the contract (matches WASM filename without extension)
 * @property name Human-readable contract name for display
 * @property description Brief description of what the contract does
 * @property wasmFilename The WASM file name in resources/wasm/ directory
 * @property hasConstructor Whether this contract requires constructor arguments
 * @property constructorParams Constructor parameter definitions (empty if no constructor)
 */
data class ContractMetadata(
    val id: String,
    val name: String,
    val description: String,
    val wasmFilename: String,
    val hasConstructor: Boolean,
    val constructorParams: List<ConstructorParam> = emptyList()
)

/**
 * Constructor parameter definition.
 *
 * @property name Parameter name (must match contract spec)
 * @property type Parameter type for display and validation
 * @property description Brief description for the user
 * @property placeholder Example value to show in input field
 */
data class ConstructorParam(
    val name: String,
    val type: ConstructorParamType,
    val description: String,
    val placeholder: String
)

/**
 * Supported constructor parameter types.
 */
enum class ConstructorParamType {
    /**
     * Stellar address (G... format)
     */
    ADDRESS,

    /**
     * String value
     */
    STRING,

    /**
     * Unsigned 32-bit integer
     */
    U32
}

/**
 * Available contracts for deployment.
 *
 * This list defines all contracts that can be deployed through the demo app.
 * Each contract includes metadata about its purpose, constructor requirements,
 * and parameter specifications.
 */
val AVAILABLE_CONTRACTS = listOf(
    ContractMetadata(
        id = "hello_world",
        name = "Hello World",
        description = "Simple greeting contract that returns a hello message with a provided name",
        wasmFilename = "soroban_hello_world_contract.wasm",
        hasConstructor = false
    ),

    ContractMetadata(
        id = "auth",
        name = "Auth Contract",
        description = "Shows authorization patterns with signature verification and access control",
        wasmFilename = "soroban_auth_contract.wasm",
        hasConstructor = false
    ),
    ContractMetadata(
        id = "token",
        name = "Token Contract",
        description = "Full-featured Stellar Asset Contract (SAC) compatible token with mint, transfer, and balance functions",
        wasmFilename = "soroban_token_contract.wasm",
        hasConstructor = true,
        constructorParams = listOf(
            ConstructorParam(
                name = "admin",
                type = ConstructorParamType.ADDRESS,
                description = "Administrator address (G...)",
                placeholder = "G..."
            ),
            ConstructorParam(
                name = "decimal",
                type = ConstructorParamType.U32,
                description = "Number of decimal places",
                placeholder = "7"
            ),
            ConstructorParam(
                name = "name",
                type = ConstructorParamType.STRING,
                description = "Token name",
                placeholder = "My Token"
            ),
            ConstructorParam(
                name = "symbol",
                type = ConstructorParamType.STRING,
                description = "Token symbol",
                placeholder = "MYTKN"
            )
        )
    ),
    ContractMetadata(
        id = "atomic_swap",
        name = "Atomic Swap",
        description = "Multi-party atomic token swap contract for trustless exchange between two parties",
        wasmFilename = "soroban_atomic_swap_contract.wasm",
        hasConstructor = false
    )
)

/**
 * Result type for contract deployment operations.
 *
 * This sealed class represents the outcome of a contract deployment attempt,
 * providing either a successful contract ID or detailed error information.
 */
sealed class DeployContractResult {
    /**
     * Successful deployment with the deployed contract's ID.
     *
     * @property contractId The deployed contract ID (C... format, 56 characters)
     * @property wasmId The WASM ID if two-step deployment was used (optional)
     */
    data class Success(
        val contractId: String,
        val wasmId: String? = null
    ) : DeployContractResult()

    /**
     * Failed deployment with error details.
     *
     * @property message Human-readable error message
     * @property exception The underlying exception, if any
     */
    data class Error(
        val message: String,
        val exception: Throwable? = null
    ) : DeployContractResult()
}

/**
 * Loads WASM bytecode from the demo app's resources.
 *
 * This function uses platform-specific implementations to load WASM files
 * from the resources/wasm/ directory. The actual loading is handled by
 * expect/actual declarations in platform-specific source sets.
 *
 * For JavaScript/browser environments, this uses the fetch() API to load
 * WASM files served via HTTP, which requires async/await pattern.
 *
 * @param wasmFilename The WASM file name (e.g., "soroban_token_contract.wasm")
 * @return The WASM bytecode as ByteArray
 * @throws IllegalArgumentException if the file cannot be found or read
 */
expect suspend fun loadWasmResource(wasmFilename: String): ByteArray

/**
 * Deploys a Soroban smart contract to the Stellar testnet using the SDK's ContractClient.
 *
 * This function demonstrates the high-level contract deployment API from the Stellar SDK.
 * It supports both contracts with and without constructors, automatically handling:
 * - WASM bytecode loading from resources
 * - Constructor argument conversion from Map<String, Any?> to XDR types
 * - Transaction building, simulation, and submission
 * - Network fee estimation
 *
 * ## One-Step Deployment
 *
 * For most use cases, this one-step deployment is recommended. It:
 * 1. Uploads the WASM bytecode to the network
 * 2. Deploys a new contract instance from that WASM
 * 3. Optionally calls the contract's constructor
 *
 * ## Usage Examples
 *
 * **Contract without constructor:**
 * ```kotlin
 * val result = deployContract(
 *     contractMetadata = helloWorldMetadata,
 *     constructorArgs = emptyMap(),
 *     sourceAccountId = "GXYZ...",
 *     secretKey = "SXYZ..."
 * )
 * ```
 *
 * **Contract with constructor (token):**
 * ```kotlin
 * val result = deployContract(
 *     contractMetadata = tokenMetadata,
 *     constructorArgs = mapOf(
 *         "admin" to "GABC...",
 *         "decimal" to 7,
 *         "name" to "My Token",
 *         "symbol" to "MYTKN"
 *     ),
 *     sourceAccountId = "GXYZ...",
 *     secretKey = "SXYZ..."
 * )
 * ```
 *
 * ## Constructor Argument Type Conversion
 *
 * The SDK's ContractClient.deploy() automatically converts Kotlin types to Soroban XDR types:
 * - `String` (G... format) → `Address` (SCAddressXdr)
 * - `String` (regular) → `String` (SCValXdr.String)
 * - `Int` or `UInt` → `u32` (SCValXdr.U32)
 * - `Long` or `ULong` → `u64` or `i64`
 * - `Boolean` → `bool` (SCValXdr.Bool)
 * - And more (see SDK documentation)
 *
 * @param contractMetadata Contract metadata including WASM filename and constructor info
 * @param constructorArgs Constructor arguments as a Map (key = param name, value = param value)
 * @param sourceAccountId The source account ID (G... format) that will pay for deployment
 * @param secretKey The source account's secret key (S... format) for signing
 * @return DeployContractResult.Success with contract ID if successful, DeployContractResult.Error if failed
 *
 * @see ContractClient.deploy
 * @see <a href="https://developers.stellar.org/docs/smart-contracts/getting-started/deploy-to-testnet">Deploying to Testnet</a>
 */
suspend fun deployContract(
    contractMetadata: ContractMetadata,
    constructorArgs: Map<String, Any?>,
    sourceAccountId: String,
    secretKey: String
): DeployContractResult {
    return try {
        // Step 1: Validate inputs
        StellarValidation.validateAccountId(sourceAccountId)?.let { error ->
            return DeployContractResult.Error(message = error.replace("Account ID", "Source account ID"))
        }

        StellarValidation.validateSecretSeed(secretKey)?.let { error ->
            return DeployContractResult.Error(message = error.replace("Secret seed", "Secret key"))
        }

        // Step 2: Validate constructor arguments
        if (contractMetadata.hasConstructor) {
            val requiredParams = contractMetadata.constructorParams.map { it.name }.toSet()
            val providedParams = constructorArgs.keys

            val missingParams = requiredParams - providedParams
            if (missingParams.isNotEmpty()) {
                return DeployContractResult.Error(
                    message = "Missing constructor parameters: ${missingParams.joinToString(", ")}"
                )
            }

            // Validate types
            for (param in contractMetadata.constructorParams) {
                val value = constructorArgs[param.name]
                if (value == null) {
                    return DeployContractResult.Error(
                        message = "Constructor parameter '${param.name}' cannot be null"
                    )
                }

                // Type validation
                when (param.type) {
                    ConstructorParamType.ADDRESS -> {
                        if (value !is String || !value.startsWith('G') || value.length != 56) {
                            return DeployContractResult.Error(
                                message = "Parameter '${param.name}' must be a valid Stellar address (G...)"
                            )
                        }
                    }
                    ConstructorParamType.STRING -> {
                        if (value !is String) {
                            return DeployContractResult.Error(
                                message = "Parameter '${param.name}' must be a string"
                            )
                        }
                    }
                    ConstructorParamType.U32 -> {
                        if (value !is Int && value !is UInt) {
                            return DeployContractResult.Error(
                                message = "Parameter '${param.name}' must be an integer (u32)"
                            )
                        }
                    }
                }
            }
        }

        // Step 3: Load WASM bytecode from resources
        val wasmBytes = try {
            loadWasmResource(contractMetadata.wasmFilename)
        } catch (e: Exception) {
            return DeployContractResult.Error(
                message = "Failed to load WASM file '${contractMetadata.wasmFilename}': ${e.message}",
                exception = e
            )
        }

        if (wasmBytes.isEmpty()) {
            return DeployContractResult.Error(
                message = "WASM file '${contractMetadata.wasmFilename}' is empty"
            )
        }

        // Step 4: Create KeyPair from secret seed
        val sourceKeyPair = try {
            KeyPair.fromSecretSeed(secretKey)
        } catch (e: Exception) {
            return DeployContractResult.Error(
                message = "Invalid secret key: ${e.message}",
                exception = e
            )
        }

        // Step 5: Determine network and RPC URL for testnet
        val network = Network.TESTNET
        val rpcUrl = "https://soroban-testnet.stellar.org:443"

        // Step 6: Deploy contract using SDK's high-level API
        // This is the key demonstration of ContractClient.deploy()
        // The SDK handles all the complexity:
        // - WASM upload
        // - Contract deployment
        // - Constructor invocation (if needed)
        // - Transaction simulation
        // - Resource estimation
        // - Transaction signing and submission
        val client = ContractClient.deploy(
            wasmBytes = wasmBytes,
            constructorArgs = constructorArgs,  // SDK converts Map<String, Any?> to XDR automatically
            source = sourceAccountId,
            signer = sourceKeyPair,
            network = network,
            rpcUrl = rpcUrl
        )

        // Step 7: Return success with deployed contract ID
        DeployContractResult.Success(
            contractId = client.contractId
        )

    } catch (e: com.soneso.stellar.sdk.contract.exception.ContractException) {
        // Soroban contract-specific errors
        DeployContractResult.Error(
            message = "Contract deployment failed: ${e.message ?: "Unknown contract error"}",
            exception = e
        )
    } catch (e: com.soneso.stellar.sdk.rpc.exception.SorobanRpcException) {
        // RPC communication errors
        DeployContractResult.Error(
            message = "RPC error: ${e.message ?: "Failed to communicate with Soroban RPC"}",
            exception = e
        )
    } catch (e: IllegalArgumentException) {
        // Validation errors
        DeployContractResult.Error(
            message = "Invalid input: ${e.message}",
            exception = e
        )
    } catch (e: Exception) {
        // Unexpected errors
        DeployContractResult.Error(
            message = "Unexpected error: ${e.message ?: "Unknown error occurred"}",
            exception = e
        )
    }
}
