package com.stellar.sdk.rpc.responses

import com.stellar.sdk.xdr.ContractEventXdr
import com.stellar.sdk.xdr.DiagnosticEventXdr
import com.stellar.sdk.xdr.fromXdrBase64
import kotlinx.serialization.Serializable

/**
 * Container for Soroban events emitted during transaction execution or simulation.
 *
 * Events provide insight into smart contract execution, including diagnostic information,
 * transaction-level events, and contract-specific events. Events are returned as base64-encoded
 * XDR strings and can be parsed into typed XDR objects for detailed analysis.
 *
 * This class is used by various response types including SimulateTransactionResponse,
 * GetTransactionResponse, and GetEventsResponse to encapsulate event data.
 *
 * @property diagnosticEventsXdr List of diagnostic events in base64-encoded XDR format.
 *                               Diagnostic events are emitted by the runtime for debugging purposes
 *                               and include detailed information about contract execution.
 *                               Can be parsed using [parseDiagnosticEventsXdr].
 * @property transactionEventsXdr List of transaction-level events in base64-encoded XDR format.
 *                                These events are associated with the transaction as a whole.
 *                                Can be parsed using [parseTransactionEventsXdr].
 * @property contractEventsXdr Nested list of contract events in base64-encoded XDR format.
 *                             The outer list corresponds to operations in the transaction,
 *                             and the inner lists contain events emitted by each operation.
 *                             Contract events are explicitly emitted by smart contracts using
 *                             the events SDK functions. Can be parsed using [parseContractEventsXdr].
 *
 * @see [Stellar Events documentation](https://developers.stellar.org/docs/smart-contracts/guides/events)
 */
@Serializable
data class Events(
    val diagnosticEventsXdr: List<String>? = null,
    val transactionEventsXdr: List<String>? = null,
    val contractEventsXdr: List<List<String>>? = null
) {
    /**
     * Parses the [diagnosticEventsXdr] field from a list of base64-encoded XDR strings
     * to a list of typed DiagnosticEvent objects.
     *
     * Diagnostic events contain detailed runtime information useful for debugging contract
     * execution, including:
     * - Contract function calls and returns
     * - Authorization checks
     * - Resource consumption metrics
     * - Error conditions
     *
     * @return A list of parsed DiagnosticEvent XDR objects, or null if no diagnostic events exist.
     * @throws IllegalArgumentException if any XDR string is malformed or cannot be decoded.
     */
    fun parseDiagnosticEventsXdr(): List<DiagnosticEventXdr>? {
        return diagnosticEventsXdr?.map { xdr ->
            DiagnosticEventXdr.fromXdrBase64(xdr)
        }
    }

    /**
     * Parses the [transactionEventsXdr] field from a list of base64-encoded XDR strings
     * to a list of typed ContractEvent objects.
     *
     * Transaction events are associated with the transaction as a whole rather than
     * individual contract invocations.
     *
     * @return A list of parsed ContractEvent XDR objects, or null if no transaction events exist.
     * @throws IllegalArgumentException if any XDR string is malformed or cannot be decoded.
     */
    fun parseTransactionEventsXdr(): List<ContractEventXdr>? {
        return transactionEventsXdr?.map { xdr ->
            ContractEventXdr.fromXdrBase64(xdr)
        }
    }

    /**
     * Parses the [contractEventsXdr] field from a nested list of base64-encoded XDR strings
     * to a nested list of typed ContractEvent objects.
     *
     * Contract events are explicitly emitted by smart contracts and contain application-specific
     * data. The outer list corresponds to operations in the transaction, and the inner lists
     * contain all events emitted by each operation's contract invocation.
     *
     * Common uses for contract events:
     * - State change notifications (e.g., token transfers, balance updates)
     * - Cross-contract communication
     * - Logging and auditing
     * - Off-chain indexing
     *
     * @return A nested list of parsed ContractEvent XDR objects, or null if no contract events exist.
     * @throws IllegalArgumentException if any XDR string is malformed or cannot be decoded.
     */
    fun parseContractEventsXdr(): List<List<ContractEventXdr>>? {
        return contractEventsXdr?.map { operationEvents ->
            operationEvents.map { xdr ->
                ContractEventXdr.fromXdrBase64(xdr)
            }
        }
    }
}
