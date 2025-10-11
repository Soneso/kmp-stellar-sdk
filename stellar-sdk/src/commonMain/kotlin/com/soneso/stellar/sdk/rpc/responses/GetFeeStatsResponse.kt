package com.soneso.stellar.sdk.rpc.responses

import kotlinx.serialization.Serializable

/**
 * Response for JSON-RPC method getFeeStats.
 *
 * Returns statistics about charged inclusion fees for transactions. This endpoint is useful for
 * estimating appropriate fees for future transactions based on recent network activity.
 *
 * @property sorobanInclusionFee Statistics for Soroban smart contract transaction inclusion fees.
 *                                These fees are charged for including Soroban transactions in ledgers.
 * @property inclusionFee Statistics for standard (non-Soroban) transaction inclusion fees.
 * @property latestLedger The latest ledger sequence number used for calculating these statistics.
 *
 * @see [Stellar Soroban RPC getFeeStats documentation](https://developers.stellar.org/docs/data/rpc/api-reference/methods/getFeeStats)
 */
@Serializable
data class GetFeeStatsResponse(
    val sorobanInclusionFee: FeeDistribution,
    val inclusionFee: FeeDistribution,
    val latestLedger: Long
) {
    /**
     * Statistical distribution of fee charges across multiple percentiles.
     *
     * All fee values are in stroops (1 stroop = 0.0000001 XLM). The distribution provides
     * insight into fee market conditions, helping developers choose appropriate fees for
     * their transactions based on desired confirmation priority.
     *
     * @property max Maximum fee charged in the sample set.
     * @property min Minimum fee charged in the sample set.
     * @property mode Most frequently occurring fee value (mode of the distribution).
     * @property p10 10th percentile - 10% of transactions paid this fee or less.
     * @property p20 20th percentile - 20% of transactions paid this fee or less.
     * @property p30 30th percentile - 30% of transactions paid this fee or less.
     * @property p40 40th percentile - 40% of transactions paid this fee or less.
     * @property p50 50th percentile (median) - half of transactions paid this fee or less.
     * @property p60 60th percentile - 60% of transactions paid this fee or less.
     * @property p70 70th percentile - 70% of transactions paid this fee or less.
     * @property p80 80th percentile - 80% of transactions paid this fee or less.
     * @property p90 90th percentile - 90% of transactions paid this fee or less.
     * @property p95 95th percentile - 95% of transactions paid this fee or less.
     * @property p99 99th percentile - 99% of transactions paid this fee or less.
     * @property transactionCount Number of transactions included in this statistical sample.
     * @property ledgerCount Number of ledgers analyzed to produce this distribution.
     */
    @Serializable
    data class FeeDistribution(
        val max: Long,
        val min: Long,
        val mode: Long,
        val p10: Long,
        val p20: Long,
        val p30: Long,
        val p40: Long,
        val p50: Long,
        val p60: Long,
        val p70: Long,
        val p80: Long,
        val p90: Long,
        val p95: Long,
        val p99: Long,
        val transactionCount: Long,
        val ledgerCount: Long
    )
}
