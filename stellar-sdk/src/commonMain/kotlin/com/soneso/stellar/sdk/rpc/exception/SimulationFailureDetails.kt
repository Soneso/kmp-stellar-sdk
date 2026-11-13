package com.soneso.stellar.sdk.rpc.exception

import com.soneso.stellar.sdk.rpc.responses.ReportedContractError

/**
 * Simulation data carried by an exception for a failed simulation.
 *
 * Implemented by `TransactionException.SimulationFailed`, `SimulationFailedException`,
 * [PrepareTransactionException], `ContractClientSimulationException` and
 * [FailedSimulationResponseException], so one handler can read the data whichever of them
 * was thrown:
 *
 * ```kotlin
 * catch (e: Exception) {
 *     val reported = (e as? SimulationFailureDetails)?.reportedContractError
 * }
 * ```
 *
 * An exception created without simulation data carries an empty list and null.
 *
 * @property diagnosticEventsXdr Immutable copy of the base64 DiagnosticEvent XDR strings of the
 *   failed simulation response
 * @property reportedContractError The contract error the failed simulation reports, or null;
 *   see [ReportedContractError] for how it is selected and when it is absent
 */
interface SimulationFailureDetails {
    val diagnosticEventsXdr: List<String>
    val reportedContractError: ReportedContractError?
}
