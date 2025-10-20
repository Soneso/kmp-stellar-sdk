package com.soneso.stellar.sdk.rpc.exception

/**
 * Exception thrown when preparing a Soroban transaction fails.
 *
 * This exception is raised during the transaction preparation flow, which involves simulating
 * the transaction, calculating resource requirements, and populating authorization entries.
 * When preparation fails, this exception provides context about what went wrong.
 *
 * ## Common Failure Scenarios
 *
 * Transaction preparation can fail for several reasons:
 * - Contract invocation error during simulation
 * - Insufficient resources (CPU instructions, memory, etc.)
 * - Invalid contract or function parameters
 * - Missing authorization entries
 * - Network connectivity issues
 * - State changes that invalidate the transaction
 *
 * The [simulationError] property provides detailed information about simulation failures,
 * which is crucial for debugging contract invocation issues.
 *
 * ## Example Usage
 *
 * ```kotlin
 * try {
 *     val preparedTx = sorobanServer.prepareTransaction(transaction)
 * } catch (e: PrepareTransactionException) {
 *     println("Transaction preparation failed: ${e.message}")
 *     e.simulationError?.let { error ->
 *         println("Simulation error: $error")
 *     }
 * }
 * ```
 *
 * @property message A descriptive error message explaining why preparation failed
 * @property simulationError Optional detailed error from the simulation response,
 *           providing contract-specific failure information
 *
 * @see <a href="https://developers.stellar.org/docs/smart-contracts/guides/transactions/submit-transaction">Submit a Transaction</a>
 */
class PrepareTransactionException(
    override val message: String,
    val simulationError: String? = null
) : Exception(message)
