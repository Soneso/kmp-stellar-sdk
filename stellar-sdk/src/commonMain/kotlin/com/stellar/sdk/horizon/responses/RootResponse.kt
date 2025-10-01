package com.stellar.sdk.horizon.responses

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents root endpoint response.
 * This endpoint provides information about the Horizon server and the Stellar network it's connected to.
 *
 * @property horizonVersion The version of Horizon server
 * @property stellarCoreVersion The version of Stellar Core this Horizon instance is connected to
 * @property ingestLatestLedger The latest ledger ingested by Horizon
 * @property historyLatestLedger The latest ledger stored in Horizon's history database
 * @property historyLatestLedgerClosedAt The timestamp when the history latest ledger was closed
 * @property historyElderLedger The oldest ledger stored in Horizon's history database
 * @property coreLatestLedger The latest ledger known by the connected Stellar Core
 * @property networkPassphrase The network passphrase for the network this Horizon instance is connected to
 * @property currentProtocolVersion The protocol version that the network currently supports
 * @property supportedProtocolVersion The protocol version supported by this Horizon instance
 * @property coreSupportedProtocolVersion The protocol version supported by the connected Stellar Core
 * @property links HAL links to other endpoints
 *
 * @see com.stellar.sdk.horizon.HorizonServer.root
 */
@Serializable
data class RootResponse(
    @SerialName("horizon_version")
    val horizonVersion: String,

    @SerialName("core_version")
    val stellarCoreVersion: String,

    @SerialName("ingest_latest_ledger")
    val ingestLatestLedger: Long? = null,

    @SerialName("history_latest_ledger")
    val historyLatestLedger: Long,

    @SerialName("history_latest_ledger_closed_at")
    val historyLatestLedgerClosedAt: String,

    @SerialName("history_elder_ledger")
    val historyElderLedger: Long,

    @SerialName("core_latest_ledger")
    val coreLatestLedger: Long,

    @SerialName("network_passphrase")
    val networkPassphrase: String,

    @SerialName("current_protocol_version")
    val currentProtocolVersion: Int,

    @SerialName("supported_protocol_version")
    val supportedProtocolVersion: Int,

    @SerialName("core_supported_protocol_version")
    val coreSupportedProtocolVersion: Int,

    @SerialName("_links")
    val links: Links
) : Response() {

    /**
     * Links to related endpoints.
     */
    @Serializable
    data class Links(
        @SerialName("account")
        val account: Link? = null,

        @SerialName("accounts")
        val accounts: Link? = null,

        @SerialName("account_transactions")
        val accountTransactions: Link? = null,

        @SerialName("claimable_balances")
        val claimableBalances: Link? = null,

        @SerialName("assets")
        val assets: Link? = null,

        @SerialName("effects")
        val effects: Link? = null,

        @SerialName("fee_stats")
        val feeStats: Link? = null,

        @SerialName("friendbot")
        val friendbot: Link? = null,

        @SerialName("ledger")
        val ledger: Link? = null,

        @SerialName("ledgers")
        val ledgers: Link? = null,

        @SerialName("liquidity_pools")
        val liquidityPools: Link? = null,

        @SerialName("offer")
        val offer: Link? = null,

        @SerialName("offers")
        val offers: Link? = null,

        @SerialName("operation")
        val operation: Link? = null,

        @SerialName("operations")
        val operations: Link? = null,

        @SerialName("order_book")
        val orderBook: Link? = null,

        @SerialName("payments")
        val payments: Link? = null,

        @SerialName("self")
        val self: Link? = null,

        @SerialName("strict_receive_paths")
        val strictReceivePaths: Link? = null,

        @SerialName("strict_send_paths")
        val strictSendPaths: Link? = null,

        @SerialName("trade_aggregations")
        val tradeAggregations: Link? = null,

        @SerialName("trades")
        val trades: Link? = null,

        @SerialName("transaction")
        val transaction: Link? = null,

        @SerialName("transactions")
        val transactions: Link? = null
    )
}
