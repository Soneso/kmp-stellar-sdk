package com.stellar.sdk.horizon.responses

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents a ledger response from the Horizon API.
 *
 * A ledger represents the state of the Stellar network at a particular point in time.
 * Ledgers are created approximately every 5 seconds and contain all the changes (transactions)
 * that have occurred in the network since the previous ledger.
 *
 * @property id Unique identifier for this ledger
 * @property pagingToken A cursor value for use in pagination
 * @property hash A hex-encoded SHA-256 hash of the ledger's XDR-encoded form
 * @property prevHash The hash of the ledger that immediately precedes this ledger
 * @property sequence The sequence number of this ledger
 * @property successfulTransactionCount The number of successful transactions in this ledger
 * @property failedTransactionCount The number of failed transactions in this ledger
 * @property operationCount The number of operations applied in this ledger
 * @property txSetOperationCount The number of operations in the transaction set for this ledger
 * @property closedAt An ISO 8601 formatted string of when this ledger was closed
 * @property totalCoins Total number of lumens in circulation
 * @property feePool The sum of all transaction fees (in stroops) since the network started
 * @property baseFeeInStroops The fee (in stroops) the network charges per operation in a transaction
 * @property baseReserveInStroops The reserve (in stroops) the network requires an account to retain as a minimum balance
 * @property maxTxSetSize The maximum number of operations validators have agreed to process in a given ledger
 * @property protocolVersion The protocol version that the Stellar network was running when this ledger was committed
 * @property headerXdr A base64 encoded string of the raw LedgerHeader xdr struct for this ledger
 * @property links HAL links related to this ledger
 *
 * @see <a href="https://developers.stellar.org/docs/fundamentals-and-concepts/stellar-data-structures/ledgers">Ledger documentation</a>
 * @see <a href="https://developers.stellar.org/api/resources/ledgers/">Ledger API documentation</a>
 */
@Serializable
data class LedgerResponse(
    @SerialName("id")
    val id: String,

    @SerialName("paging_token")
    val pagingToken: String,

    @SerialName("hash")
    val hash: String,

    @SerialName("prev_hash")
    val prevHash: String? = null,

    @SerialName("sequence")
    val sequence: Long,

    @SerialName("successful_transaction_count")
    val successfulTransactionCount: Int? = null,

    @SerialName("failed_transaction_count")
    val failedTransactionCount: Int? = null,

    @SerialName("operation_count")
    val operationCount: Int? = null,

    @SerialName("tx_set_operation_count")
    val txSetOperationCount: Int? = null,

    @SerialName("closed_at")
    val closedAt: String,

    @SerialName("total_coins")
    val totalCoins: String,

    @SerialName("fee_pool")
    val feePool: String,

    @SerialName("base_fee_in_stroops")
    val baseFeeInStroops: String,

    @SerialName("base_reserve_in_stroops")
    val baseReserveInStroops: String,

    @SerialName("max_tx_set_size")
    val maxTxSetSize: Int? = null,

    @SerialName("protocol_version")
    val protocolVersion: Int? = null,

    @SerialName("header_xdr")
    val headerXdr: String? = null,

    @SerialName("_links")
    val links: Links
) : Response() {

    /**
     * HAL links connected to this ledger.
     *
     * @property self Link to this ledger
     * @property transactions Link to the transactions in this ledger
     * @property operations Link to the operations in this ledger
     * @property payments Link to the payment operations in this ledger
     * @property effects Link to the effects in this ledger
     */
    @Serializable
    data class Links(
        @SerialName("self")
        val self: Link,

        @SerialName("transactions")
        val transactions: Link,

        @SerialName("operations")
        val operations: Link,

        @SerialName("payments")
        val payments: Link,

        @SerialName("effects")
        val effects: Link
    )
}
