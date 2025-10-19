package com.soneso.demo.stellar

import com.soneso.stellar.sdk.contract.SorobanContractInfo
import com.soneso.stellar.sdk.rpc.SorobanServer

/**
 * Result type for contract details fetching operations.
 */
sealed class ContractDetailsResult {
    /**
     * Successful contract fetch with parsed details.
     *
     * @property contractInfo The parsed contract information including metadata and spec entries
     */
    data class Success(
        val contractInfo: SorobanContractInfo
    ) : ContractDetailsResult()

    /**
     * Failed contract fetch with error details.
     *
     * @property message Human-readable error message
     * @property exception The underlying exception, if any
     */
    data class Error(
        val message: String,
        val exception: Throwable? = null
    ) : ContractDetailsResult()
}

/**
 * Fetches and parses smart contract details from the Stellar network using Soroban RPC.
 *
 * This function retrieves the contract's WASM bytecode from the network and parses it to extract:
 * - Environment interface version (protocol version)
 * - Contract specification entries (functions, structs, unions, enums, events)
 * - Contract metadata (key-value pairs for application/tooling use)
 *
 * The parsing is performed using the SDK's [com.soneso.stellar.sdk.contract.SorobanContractParser]
 * which implements the Soroban contract specification.
 *
 * ## Usage
 *
 * ```kotlin
 * val result = fetchContractDetails("CBNCMQU5VCEVFASCPT4CCQX2LGYJK6YZ7LOIZLRXDEVJYQB7K6UTQNWW")
 * when (result) {
 *     is ContractDetailsResult.Success -> {
 *         val info = result.contractInfo
 *         println("Protocol version: ${info.envInterfaceVersion}")
 *         println("Spec entries: ${info.specEntries.size}")
 *     }
 *     is ContractDetailsResult.Error -> {
 *         println("Failed to fetch contract: ${result.message}")
 *     }
 * }
 * ```
 *
 * @param contractId The Stellar contract ID to fetch (must start with 'C')
 * @param useTestnet If true, connects to testnet; otherwise connects to mainnet (default: true)
 * @return ContractDetailsResult.Success with parsed contract info if fetch succeeded, ContractDetailsResult.Error if it failed
 *
 * @see <a href="https://developers.stellar.org/docs/tools/sdks/build-your-own">Soroban Contract Specification</a>
 * @see <a href="https://developers.stellar.org/docs/data/rpc/api-reference">Soroban RPC API Reference</a>
 */
suspend fun fetchContractDetails(
    contractId: String,
    useTestnet: Boolean = true
): ContractDetailsResult {
    return try {
        // Validate contract ID format
        if (contractId.isBlank()) {
            return ContractDetailsResult.Error(
                message = "Contract ID cannot be empty"
            )
        }

        if (!contractId.startsWith('C')) {
            return ContractDetailsResult.Error(
                message = "Contract ID must start with 'C' (got: ${contractId.take(1)})"
            )
        }

        if (contractId.length != 56) {
            return ContractDetailsResult.Error(
                message = "Contract ID must be exactly 56 characters long (got: ${contractId.length})"
            )
        }

        // Connect to Soroban RPC server
        val rpcUrl = if (useTestnet) {
            "https://soroban-testnet.stellar.org:443"
        } else {
            "https://soroban-mainnet.stellar.org:443"
        }

        val server = SorobanServer(rpcUrl)

        try {
            // Fetch and parse contract details from the network
            val contractInfo = server.loadContractInfoForContractId(contractId)

            if (contractInfo == null) {
                return ContractDetailsResult.Error(
                    message = "Contract not found. The contract may not exist or hasn't been deployed yet."
                )
            }

            ContractDetailsResult.Success(
                contractInfo = contractInfo
            )
        } finally {
            // Clean up HTTP client resources
            server.close()
        }
    } catch (e: com.soneso.stellar.sdk.contract.SorobanContractParserException) {
        // Contract parsing errors
        ContractDetailsResult.Error(
            message = "Failed to parse contract: ${e.message ?: "Invalid contract bytecode"}",
            exception = e
        )
    } catch (e: com.soneso.stellar.sdk.rpc.exception.SorobanRpcException) {
        // RPC-level errors
        ContractDetailsResult.Error(
            message = "RPC error: ${e.message ?: "Failed to communicate with Soroban RPC"}",
            exception = e
        )
    } catch (e: IllegalArgumentException) {
        // Invalid contract ID format
        ContractDetailsResult.Error(
            message = "Invalid contract ID format: ${e.message}",
            exception = e
        )
    } catch (e: Exception) {
        // Unexpected errors
        ContractDetailsResult.Error(
            message = "Unexpected error: ${e.message ?: "Unknown error occurred"}",
            exception = e
        )
    }
}
