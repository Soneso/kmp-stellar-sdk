package com.soneso.stellar.sdk.rpc.requests

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Request for JSON-RPC method simulateTransaction.
 *
 * Simulates a transaction to preview its effects without submitting it to the network.
 * This is essential for Soroban transactions to calculate resource requirements and auth entries.
 *
 * @property transaction Base64-encoded XDR TransactionEnvelope to simulate.
 * @property resourceConfig Optional configuration for resource estimation.
 * @property authMode Authorization mode for simulation. Controls how auth entries are handled.
 *
 * @see <a href="https://developers.stellar.org/docs/data/rpc/api-reference/methods/simulateTransaction">simulateTransaction documentation</a>
 */
@Serializable
data class SimulateTransactionRequest(
    val transaction: String,
    val resourceConfig: ResourceConfig? = null,
    val authMode: AuthMode? = null
) {
    init {
        require(transaction.isNotBlank()) { "transaction must not be blank" }
        resourceConfig?.instructionLeeway?.let {
            require(it >= 0) { "resourceConfig.instructionLeeway must be non-negative" }
        }
    }

    /**
     * Resource configuration for simulation.
     *
     * @property instructionLeeway Additional CPU instructions to reserve beyond the minimum required.
     *                             This provides a buffer for transaction execution variability.
     *                             If not specified, the server uses a default leeway value.
     */
    @Serializable
    data class ResourceConfig(
        val instructionLeeway: Long? = null
    )

    /**
     * Authorization mode for simulation.
     *
     * Controls how authorization entries are handled during simulation:
     *
     * - **ENFORCE**: Requires existing auth entries to be valid. Use for transactions with pre-populated auth.
     * - **RECORD**: Records new auth entries. Fails if any auth already exists. Use for initial transaction preparation.
     * - **RECORD_ALLOW_NONROOT**: Like RECORD, but allows non-root authorization. Use for complex auth scenarios.
     *
     * If unset, defaults to ENFORCE if auth entries are present, RECORD otherwise.
     *
     * @see <a href="https://developers.stellar.org/docs/learn/smart-contract-internals/authorization">Authorization documentation</a>
     */
    @Serializable
    enum class AuthMode {
        /**
         * Always enforce mode, even with an empty list.
         * Auth entries must be valid and present.
         */
        @SerialName("enforce")
        ENFORCE,

        /**
         * Always recording mode, failing if any auth exists.
         * Records new auth entries for the simulation.
         */
        @SerialName("record")
        RECORD,

        /**
         * Like RECORD, but allowing non-root authorization.
         * Useful for testing complex authorization scenarios.
         */
        @SerialName("record_allow_nonroot")
        RECORD_ALLOW_NONROOT
    }
}
