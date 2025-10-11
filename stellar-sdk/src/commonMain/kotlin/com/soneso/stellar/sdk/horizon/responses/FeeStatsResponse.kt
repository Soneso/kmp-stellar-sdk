package com.soneso.stellar.sdk.horizon.responses

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents fee statistics response from the Horizon API.
 *
 * Fee stats provide information about the transaction fees network validators are accepting.
 * This data helps users set appropriate transaction fees to ensure their transactions are
 * accepted by the network in a timely manner.
 *
 * The response includes:
 * - Information about the last closed ledger
 * - Fee charged statistics (actual fees that were charged)
 * - Max fee statistics (maximum fees users were willing to pay)
 * - Percentile distributions for both fee types
 *
 * @property lastLedger The sequence number of the last ledger
 * @property lastLedgerBaseFee The base fee in the last ledger (in stroops)
 * @property ledgerCapacityUsage The capacity usage of the last ledger (as a decimal string, e.g., "0.97" for 97%)
 * @property feeCharged Distribution of fees that were actually charged
 * @property maxFee Distribution of maximum fees that users were willing to pay
 *
 * @see <a href="https://developers.stellar.org/api/aggregations/fee-stats/">Fee Stats documentation</a>
 */
@Serializable
data class FeeStatsResponse(
    @SerialName("last_ledger")
    val lastLedger: Long,

    @SerialName("last_ledger_base_fee")
    val lastLedgerBaseFee: Long,

    @SerialName("ledger_capacity_usage")
    val ledgerCapacityUsage: String,

    @SerialName("fee_charged")
    val feeCharged: FeeDistribution,

    @SerialName("max_fee")
    val maxFee: FeeDistribution
) : Response() {

    /**
     * Represents the statistical distribution of transaction fees.
     *
     * This provides various percentiles and aggregates to help users understand
     * the fee landscape and choose an appropriate fee for their transactions.
     *
     * All values are in stroops (1 XLM = 10,000,000 stroops).
     *
     * @property min The minimum fee in the distribution
     * @property max The maximum fee in the distribution
     * @property mode The most common fee (mode of the distribution)
     * @property p10 10th percentile - 10% of transactions had fees at or below this value
     * @property p20 20th percentile
     * @property p30 30th percentile
     * @property p40 40th percentile
     * @property p50 50th percentile (median)
     * @property p60 60th percentile
     * @property p70 70th percentile
     * @property p80 80th percentile
     * @property p90 90th percentile - 90% of transactions had fees at or below this value
     * @property p95 95th percentile
     * @property p99 99th percentile
     */
    @Serializable
    data class FeeDistribution(
        @SerialName("min")
        val min: Long,

        @SerialName("max")
        val max: Long,

        @SerialName("mode")
        val mode: Long,

        @SerialName("p10")
        val p10: Long,

        @SerialName("p20")
        val p20: Long,

        @SerialName("p30")
        val p30: Long,

        @SerialName("p40")
        val p40: Long,

        @SerialName("p50")
        val p50: Long,

        @SerialName("p60")
        val p60: Long,

        @SerialName("p70")
        val p70: Long,

        @SerialName("p80")
        val p80: Long,

        @SerialName("p90")
        val p90: Long,

        @SerialName("p95")
        val p95: Long,

        @SerialName("p99")
        val p99: Long
    )
}
