package com.soneso.stellar.sdk.contract.exception

import com.soneso.stellar.sdk.contract.AssembledTransaction
import com.soneso.stellar.sdk.rpc.exception.SimulationFailureDetails
import com.soneso.stellar.sdk.rpc.responses.EventSnapshot
import com.soneso.stellar.sdk.rpc.responses.ReportedContractError

/**
 * Exception thrown when transaction simulation fails.
 *
 * This typically indicates an error in the contract invocation parameters,
 * such as invalid arguments, insufficient authorization, or contract logic errors.
 *
 * Check [assembledTransaction].[AssembledTransaction.simulation].error for details. When the
 * failed simulation reports a contract error, [reportedContractError] holds it; when it was
 * found in the diagnostic events, the message also ends with
 * ` (contract error in diagnostic events: Error(Contract, #N), contract C...)`.
 *
 * @param diagnosticEventsXdr The base64 DiagnosticEvent XDR strings of the failed simulation
 * @property message The error message, used as given
 * @property assembledTransaction The AssembledTransaction whose simulation failed
 * @property diagnosticEventsXdr Immutable copy of the base64 DiagnosticEvent XDR strings
 * @property reportedContractError The contract error the failed simulation reports, or null.
 *   The selection from the diagnostic events is a heuristic, and without RPC debug events a code
 *   is available only from an `Error(Contract, #N)` header; see [ReportedContractError].
 */
class SimulationFailedException(
    message: String,
    assembledTransaction: AssembledTransaction<*>,
    diagnosticEventsXdr: List<String>,
    override val reportedContractError: ReportedContractError?
) : ContractException(message, assembledTransaction), SimulationFailureDetails {

    override val diagnosticEventsXdr: List<String> = EventSnapshot(diagnosticEventsXdr)

    /**
     * Creates the exception without simulation data: [diagnosticEventsXdr] is empty and
     * [reportedContractError] is null.
     */
    constructor(message: String, assembledTransaction: AssembledTransaction<*>) :
        this(message, assembledTransaction, emptyList(), null)
}
