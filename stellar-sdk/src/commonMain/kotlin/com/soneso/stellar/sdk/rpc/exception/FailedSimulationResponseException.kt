package com.soneso.stellar.sdk.rpc.exception

import com.soneso.stellar.sdk.rpc.responses.EventSnapshot
import com.soneso.stellar.sdk.rpc.responses.ReportedContractError

/**
 * Exception thrown by [com.soneso.stellar.sdk.rpc.assembleTransaction] for a simulation
 * response that reports an error.
 *
 * It is an [IllegalArgumentException], so a handler for that type catches it. When the
 * failed simulation reports a contract error, [reportedContractError] holds it; when it was found
 * in the diagnostic events, the message also ends with
 * ` (contract error in diagnostic events: Error(Contract, #N), contract C...)`.
 *
 * @param message The error message, used as given
 * @param diagnosticEventsXdr The base64 DiagnosticEvent XDR strings of the failed simulation
 * @property simulationError The raw error text of the simulation response
 * @property diagnosticEventsXdr Immutable copy of the base64 DiagnosticEvent XDR strings
 * @property reportedContractError The contract error the failed simulation reports, or null.
 *   The selection from the diagnostic events is a heuristic, and without RPC debug events a code
 *   is available only from an `Error(Contract, #N)` header; see [ReportedContractError].
 */
class FailedSimulationResponseException(
    message: String,
    val simulationError: String,
    diagnosticEventsXdr: List<String>,
    override val reportedContractError: ReportedContractError?
) : IllegalArgumentException(message), SimulationFailureDetails {

    override val diagnosticEventsXdr: List<String> = EventSnapshot(diagnosticEventsXdr)
}
