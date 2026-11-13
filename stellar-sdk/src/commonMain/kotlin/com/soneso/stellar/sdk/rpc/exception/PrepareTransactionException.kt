package com.soneso.stellar.sdk.rpc.exception

import com.soneso.stellar.sdk.rpc.responses.EventSnapshot
import com.soneso.stellar.sdk.rpc.responses.ReportedContractError

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
 * When the failed simulation reports a contract error, [reportedContractError] holds it; when it
 * was found in the diagnostic events, the message also ends with
 * ` (contract error in diagnostic events: Error(Contract, #N), contract C...)`, while
 * [simulationError] stays the raw error text of the response.
 *
 * @param diagnosticEventsXdr The base64 DiagnosticEvent XDR strings of the failed simulation
 * @property message A descriptive error message explaining why preparation failed
 * @property simulationError Optional detailed error from the simulation response,
 *           providing contract-specific failure information
 * @property diagnosticEventsXdr Immutable copy of the base64 DiagnosticEvent XDR strings
 * @property reportedContractError The contract error the failed simulation reports, or null.
 *   The selection from the diagnostic events is a heuristic, and without RPC debug events a code
 *   is available only from an `Error(Contract, #N)` header; see [ReportedContractError].
 *
 * @see <a href="https://developers.stellar.org/docs/smart-contracts/guides/transactions/submit-transaction">Submit a Transaction</a>
 */
class PrepareTransactionException(
    override val message: String,
    val simulationError: String?,
    diagnosticEventsXdr: List<String>,
    override val reportedContractError: ReportedContractError?
) : Exception(message), SimulationFailureDetails {

    override val diagnosticEventsXdr: List<String> = EventSnapshot(diagnosticEventsXdr)

    /**
     * Creates the exception without simulation data: [diagnosticEventsXdr] is empty and
     * [reportedContractError] is null.
     */
    constructor(message: String, simulationError: String? = null) :
        this(message, simulationError, emptyList(), null)
}
