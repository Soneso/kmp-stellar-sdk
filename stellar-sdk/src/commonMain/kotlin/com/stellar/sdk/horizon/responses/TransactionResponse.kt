package com.stellar.sdk.horizon.responses

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents a transaction response from the Horizon API.
 *
 * @see <a href="https://developers.stellar.org/api/resources/transactions/">Transaction documentation</a>
 */
@Serializable
data class TransactionResponse(
    @SerialName("id")
    val id: String,

    @SerialName("paging_token")
    override val pagingToken: String,

    @SerialName("successful")
    val successful: Boolean,

    @SerialName("hash")
    val hash: String,

    @SerialName("ledger")
    val ledger: Long,

    @SerialName("created_at")
    val createdAt: String,

    @SerialName("source_account")
    val sourceAccount: String,

    @SerialName("account_muxed")
    val accountMuxed: String? = null,

    @SerialName("account_muxed_id")
    val accountMuxedId: String? = null,

    @SerialName("source_account_sequence")
    val sourceAccountSequence: Long,

    @SerialName("fee_account")
    val feeAccount: String,

    @SerialName("fee_account_muxed")
    val feeAccountMuxed: String? = null,

    @SerialName("fee_account_muxed_id")
    val feeAccountMuxedId: String? = null,

    @SerialName("fee_charged")
    val feeCharged: Long,

    @SerialName("max_fee")
    val maxFee: Long,

    @SerialName("operation_count")
    val operationCount: Int,

    @SerialName("envelope_xdr")
    val envelopeXdr: String? = null,

    @SerialName("result_xdr")
    val resultXdr: String? = null,

    @SerialName("result_meta_xdr")
    val resultMetaXdr: String? = null,

    @SerialName("fee_meta_xdr")
    val feeMetaXdr: String? = null,

    @SerialName("signatures")
    val signatures: List<String>,

    @SerialName("preconditions")
    val preconditions: Preconditions? = null,

    @SerialName("fee_bump_transaction")
    val feeBumpTransaction: FeeBumpTransaction? = null,

    @SerialName("inner_transaction")
    val innerTransaction: InnerTransaction? = null,

    @SerialName("memo_type")
    val memoType: String,

    @SerialName("memo_bytes")
    val memoBytes: String? = null,

    @SerialName("memo")
    val memoValue: String? = null,

    @SerialName("_links")
    val links: Links
) : Response(), Pageable {

    /**
     * Preconditions of a transaction per CAP-21.
     *
     * @see <a href="https://github.com/stellar/stellar-protocol/blob/master/core/cap-0021.md#specification">CAP-21</a>
     */
    @Serializable
    data class Preconditions(
        @SerialName("timebounds")
        val timeBounds: TimeBounds? = null,

        @SerialName("ledgerbounds")
        val ledgerBounds: LedgerBounds? = null,

        @SerialName("min_account_sequence")
        val minAccountSequence: Long? = null,

        @SerialName("min_account_sequence_age")
        val minAccountSequenceAge: Long? = null,

        @SerialName("min_account_sequence_ledger_gap")
        val minAccountSequenceLedgerGap: Long? = null,

        @SerialName("extra_signers")
        val extraSigners: List<String>? = null
    ) {
        @Serializable
        data class TimeBounds(
            @SerialName("min_time")
            val minTime: String? = null,

            @SerialName("max_time")
            val maxTime: String? = null
        )

        @Serializable
        data class LedgerBounds(
            @SerialName("min_ledger")
            val minLedger: Long,

            @SerialName("max_ledger")
            val maxLedger: Long
        )
    }

    /**
     * FeeBumpTransaction is only present in a TransactionResponse if the transaction
     * is a fee bump transaction or is wrapped by a fee bump transaction.
     */
    @Serializable
    data class FeeBumpTransaction(
        @SerialName("hash")
        val hash: String,

        @SerialName("signatures")
        val signatures: List<String>
    )

    /**
     * InnerTransaction is only present in a TransactionResponse if the transaction
     * is a fee bump transaction or is wrapped by a fee bump transaction.
     */
    @Serializable
    data class InnerTransaction(
        @SerialName("hash")
        val hash: String,

        @SerialName("signatures")
        val signatures: List<String>,

        @SerialName("max_fee")
        val maxFee: Long
    )

    /**
     * HAL links connected to the transaction.
     */
    @Serializable
    data class Links(
        @SerialName("self")
        val self: Link,

        @SerialName("account")
        val account: Link,

        @SerialName("ledger")
        val ledger: Link,

        @SerialName("operations")
        val operations: Link,

        @SerialName("effects")
        val effects: Link,

        @SerialName("precedes")
        val precedes: Link,

        @SerialName("succeeds")
        val succeeds: Link
    )
}
