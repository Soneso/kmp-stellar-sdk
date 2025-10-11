package com.soneso.stellar.sdk.horizon.responses

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents a liquidity pool response from the Horizon API.
 *
 * Liquidity pools enable automated market makers (AMMs) on the Stellar network,
 * allowing users to deposit assets and earn fees from trades.
 *
 * @property id Unique identifier for this liquidity pool
 * @property pagingToken A cursor value for use in pagination
 * @property feeBp Fee in basis points charged for swaps (typically 30, which is 0.3%)
 * @property type The type of liquidity pool (currently only "constant_product" is supported)
 * @property totalTrustlines The number of trustlines to this liquidity pool
 * @property totalShares The total number of pool shares issued
 * @property reserves The list of reserves held by this pool
 * @property lastModifiedLedger The sequence number of the last ledger in which this pool was modified
 * @property lastModifiedTime An ISO 8601 formatted string of the last time this pool was modified
 * @property links HAL links related to this liquidity pool
 *
 * @see <a href="https://developers.stellar.org/api/resources/liquiditypools/">Liquidity Pool documentation</a>
 */
@Serializable
data class LiquidityPoolResponse(
    @SerialName("id")
    val id: String,

    @SerialName("paging_token")
    override val pagingToken: String,

    @SerialName("fee_bp")
    val feeBp: Int? = null,

    @SerialName("type")
    val type: String,

    @SerialName("total_trustlines")
    val totalTrustlines: Long? = null,

    @SerialName("total_shares")
    val totalShares: String,

    @SerialName("reserves")
    val reserves: List<Reserve> = emptyList(),

    @SerialName("last_modified_ledger")
    val lastModifiedLedger: Long? = null,

    @SerialName("last_modified_time")
    val lastModifiedTime: String? = null,

    @SerialName("_links")
    val links: Links
) : Response(), Pageable {

    /**
     * Represents liquidity pool reserves.
     *
     * Each reserve holds one of the assets in the liquidity pool.
     *
     * @property amount The amount of the asset in the reserve
     * @property asset The asset in canonical string form (e.g., "native" or "USD:GCDNJUBQSX7...")
     */
    @Serializable
    data class Reserve(
        @SerialName("amount")
        val amount: String,

        @SerialName("asset")
        val asset: String
    )

    /**
     * HAL links connected to this liquidity pool.
     *
     * @property self Link to this liquidity pool
     * @property operations Link to operations related to this liquidity pool
     * @property transactions Link to transactions related to this liquidity pool
     */
    @Serializable
    data class Links(
        @SerialName("self")
        val self: Link,

        @SerialName("operations")
        val operations: Link? = null,

        @SerialName("transactions")
        val transactions: Link? = null
    )
}
