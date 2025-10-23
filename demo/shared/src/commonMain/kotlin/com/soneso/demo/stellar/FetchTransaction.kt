package com.soneso.demo.stellar

import com.soneso.stellar.sdk.horizon.HorizonServer
import com.soneso.stellar.sdk.horizon.responses.TransactionResponse
import com.soneso.stellar.sdk.horizon.responses.operations.OperationResponse
import com.soneso.stellar.sdk.rpc.SorobanServer
import com.soneso.stellar.sdk.rpc.responses.GetTransactionResponse
import com.soneso.stellar.sdk.rpc.responses.GetTransactionStatus
import com.soneso.demo.util.StellarValidation

/**
 * Result type for transaction fetch operations.
 */
sealed class FetchTransactionResult {
    /**
     * Successful fetch from Horizon API.
     *
     * @property transaction The full transaction response from Horizon
     * @property operations The list of operations in this transaction
     * @property message Success message
     */
    data class HorizonSuccess(
        val transaction: TransactionResponse,
        val operations: List<OperationResponse>,
        val message: String = "Transaction fetched successfully from Horizon"
    ) : FetchTransactionResult()

    /**
     * Successful fetch from Soroban RPC.
     *
     * @property transaction The full transaction response from RPC
     * @property message Success message
     */
    data class RpcSuccess(
        val transaction: GetTransactionResponse,
        val message: String = "Transaction fetched successfully from Soroban RPC"
    ) : FetchTransactionResult()

    /**
     * Failed fetch operation with error details.
     *
     * @property message Human-readable error message
     * @property exception The underlying exception, if any
     */
    data class Error(
        val message: String,
        val exception: Throwable? = null
    ) : FetchTransactionResult()
}

/**
 * Fetches transaction details from Horizon testnet API.
 *
 * Horizon provides comprehensive transaction information including:
 * - Transaction metadata (hash, ledger, timestamps)
 * - Source account and fee details
 * - Operation count and signatures
 * - XDR envelopes and results
 * - Memo information
 * - Success/failure status
 * - Complete list of operations with their details
 *
 * ## What is Horizon?
 *
 * Horizon is Stellar's REST API for accessing blockchain data. It provides:
 * - Complete transaction history
 * - Account and operation details
 * - Human-readable responses
 * - Extensive filtering and pagination
 *
 * ## Usage
 *
 * ```kotlin
 * val result = fetchTransactionFromHorizon(
 *     transactionHash = "abc123..."
 * )
 *
 * when (result) {
 *     is FetchTransactionResult.HorizonSuccess -> {
 *         val tx = result.transaction
 *         println("Hash: ${tx.hash}")
 *         println("Source: ${tx.sourceAccount}")
 *         println("Ledger: ${tx.ledger}")
 *         println("Fee: ${tx.feeCharged} stroops")
 *         println("Operations: ${tx.operationCount}")
 *         println("Success: ${tx.successful}")
 *
 *         // Access operations
 *         result.operations.forEach { operation ->
 *             println("Operation type: ${operation.type}")
 *         }
 *     }
 *     is FetchTransactionResult.Error -> {
 *         println("Error: ${result.message}")
 *     }
 * }
 * ```
 *
 * ## Transaction Hash Format
 *
 * Transaction hashes are 64-character hexadecimal strings representing the
 * SHA-256 hash of the transaction envelope XDR.
 *
 * @param transactionHash The transaction hash (64-character hex string)
 * @return FetchTransactionResult.HorizonSuccess if found, FetchTransactionResult.Error if not found or failed
 *
 * @see <a href="https://developers.stellar.org/api/resources/transactions/">Horizon Transactions API</a>
 * @see <a href="https://developers.stellar.org/docs/learn/fundamentals/transactions/">Stellar Transactions</a>
 */
suspend fun fetchTransactionFromHorizon(
    transactionHash: String
): FetchTransactionResult {
    return try {
        // Validate transaction hash
        StellarValidation.validateTransactionHash(transactionHash)?.let { error ->
            return FetchTransactionResult.Error(message = error)
        }

        // Connect to Horizon testnet server
        val horizonUrl = "https://horizon-testnet.stellar.org"

        val server = HorizonServer(horizonUrl)

        try {
            // Fetch transaction by hash using the SDK's TransactionsRequestBuilder
            val transaction = try {
                server.transactions().transaction(transactionHash)
            } catch (e: com.soneso.stellar.sdk.horizon.exceptions.BadRequestException) {
                if (e.code == 404) {
                    return FetchTransactionResult.Error(
                        message = "Transaction not found. The transaction may not exist on testnet, or the hash may be incorrect.",
                        exception = e
                    )
                } else {
                    return FetchTransactionResult.Error(
                        message = "Failed to fetch transaction: ${e.message}",
                        exception = e
                    )
                }
            } catch (e: com.soneso.stellar.sdk.horizon.exceptions.NetworkException) {
                return FetchTransactionResult.Error(
                    message = "Network error while fetching transaction: ${e.message ?: "Failed to connect to Horizon"}",
                    exception = e
                )
            } catch (e: Exception) {
                return FetchTransactionResult.Error(
                    message = "Failed to fetch transaction: ${e.message}",
                    exception = e
                )
            }

            // Fetch operations for this transaction
            val operations = try {
                server.operations()
                    .forTransaction(transactionHash)
                    .execute()
                    .records
            } catch (e: Exception) {
                // If we can't fetch operations, return empty list rather than failing
                // The transaction data is still valuable
                emptyList()
            }

            // Return success with full transaction response and operations
            FetchTransactionResult.HorizonSuccess(
                transaction = transaction,
                operations = operations,
                message = "Successfully fetched transaction ${transaction.hash.take(8)}... from ledger ${transaction.ledger} with ${operations.size} operation(s)"
            )
        } finally {
            // Clean up HTTP client resources
            server.close()
        }
    } catch (e: Exception) {
        // Catch any unexpected errors
        FetchTransactionResult.Error(
            message = "Unexpected error: ${e.message ?: "Unknown error occurred"}",
            exception = e
        )
    }
}

/**
 * Fetches transaction details from Soroban testnet RPC.
 *
 * Soroban RPC provides transaction information for smart contract operations:
 * - Transaction status (SUCCESS, FAILED, NOT_FOUND)
 * - Ledger information and timestamps
 * - XDR envelopes, results, and metadata
 * - Contract invocation return values
 * - Event data from contract execution
 *
 * ## What is Soroban RPC?
 *
 * Soroban RPC is Stellar's smart contract platform RPC server. It provides:
 * - Real-time transaction status monitoring
 * - Contract execution results and events
 * - Lower-level XDR access
 * - Optimized for contract operations
 *
 * ## Usage
 *
 * ```kotlin
 * val result = fetchTransactionFromRpc(
 *     transactionHash = "abc123..."
 * )
 *
 * when (result) {
 *     is FetchTransactionResult.RpcSuccess -> {
 *         val tx = result.transaction
 *         println("Status: ${tx.status}")
 *         println("Ledger: ${tx.ledger}")
 *         println("Created: ${tx.createdAt}")
 *
 *         // Access contract return value if available
 *         tx.getResultValue()?.let { scVal ->
 *             println("Return value: $scVal")
 *         }
 *
 *         // Access events
 *         tx.events?.let { events ->
 *             println("Events: ${events.events.size}")
 *         }
 *     }
 *     is FetchTransactionResult.Error -> {
 *         println("Error: ${result.message}")
 *     }
 * }
 * ```
 *
 * ## Transaction Status
 *
 * RPC returns one of three statuses:
 * - SUCCESS: Transaction was included in a ledger and executed successfully
 * - FAILED: Transaction was included but execution failed
 * - NOT_FOUND: Transaction not found or still pending
 *
 * @param transactionHash The transaction hash (64-character hex string)
 * @return FetchTransactionResult.RpcSuccess if found, FetchTransactionResult.Error if not found or failed
 *
 * @see <a href="https://developers.stellar.org/docs/data/rpc/api-reference/methods/getTransaction">Soroban RPC getTransaction</a>
 * @see <a href="https://developers.stellar.org/docs/smart-contracts">Stellar Smart Contracts</a>
 */
suspend fun fetchTransactionFromRpc(
    transactionHash: String
): FetchTransactionResult {
    return try {
        // Validate transaction hash
        StellarValidation.validateTransactionHash(transactionHash)?.let { error ->
            return FetchTransactionResult.Error(message = error)
        }

        // Connect to Soroban RPC testnet server
        val rpcUrl = "https://soroban-testnet.stellar.org:443"

        val server = SorobanServer(rpcUrl)

        try {
            // Fetch transaction using the SDK's getTransaction method
            val transaction = try {
                server.getTransaction(transactionHash)
            } catch (e: com.soneso.stellar.sdk.rpc.exception.SorobanRpcException) {
                return FetchTransactionResult.Error(
                    message = "RPC error: ${e.message}",
                    exception = e
                )
            } catch (e: Exception) {
                return FetchTransactionResult.Error(
                    message = "Failed to fetch transaction: ${e.message}",
                    exception = e
                )
            }

            // Check transaction status
            when (transaction.status) {
                GetTransactionStatus.NOT_FOUND -> {
                    return FetchTransactionResult.Error(
                        message = "Transaction not found. The transaction may not exist on testnet, may still be pending, or may be outside the retention window (ledgers ${transaction.oldestLedger} to ${transaction.latestLedger})."
                    )
                }
                GetTransactionStatus.SUCCESS,
                GetTransactionStatus.FAILED -> {
                    // Transaction found (either succeeded or failed)
                    val statusText = if (transaction.status == GetTransactionStatus.SUCCESS) "successful" else "failed"
                    FetchTransactionResult.RpcSuccess(
                        transaction = transaction,
                        message = "Successfully fetched $statusText transaction from ledger ${transaction.ledger}"
                    )
                }
            }
        } finally {
            // Clean up HTTP client resources
            server.close()
        }
    } catch (e: Exception) {
        // Catch any unexpected errors
        FetchTransactionResult.Error(
            message = "Unexpected error: ${e.message ?: "Unknown error occurred"}",
            exception = e
        )
    }
}

/**
 * Shortens a transaction hash for display purposes.
 * Shows first 8 and last 8 characters with "..." in between.
 *
 * @param hash The full transaction hash
 * @return Shortened hash (e.g., "abc12345...xyz67890")
 */
fun shortenHash(hash: String): String {
    return if (hash.length > 20) {
        "${hash.take(8)}...${hash.takeLast(8)}"
    } else {
        hash
    }
}
