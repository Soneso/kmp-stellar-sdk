package org.stellar.sdk.horizon.responses.operations

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.stellar.sdk.horizon.responses.AssetAmount
import org.stellar.sdk.horizon.responses.Price
import org.stellar.sdk.horizon.responses.TransactionResponse

/**
 * Represents LiquidityPoolDeposit operation response.
 *
 * @see <a href="https://developers.stellar.org/docs/data/horizon/api-reference/resources/operations/object/liquidity-pool-deposit">Operation documentation</a>
 */
@Serializable
@SerialName("liquidity_pool_deposit")
data class LiquidityPoolDepositOperationResponse(
    @SerialName("id")
    override val id: String,

    @SerialName("source_account")
    override val sourceAccount: String,

    @SerialName("source_account_muxed")
    override val sourceAccountMuxed: String? = null,

    @SerialName("source_account_muxed_id")
    override val sourceAccountMuxedId: String? = null,

    @SerialName("paging_token")
    override val pagingToken: String,

    @SerialName("created_at")
    override val createdAt: String,

    @SerialName("transaction_hash")
    override val transactionHash: String,

    @SerialName("transaction_successful")
    override val transactionSuccessful: Boolean,

    @SerialName("type")
    override val type: String,

    @SerialName("_links")
    override val links: Links,

    @SerialName("transaction")
    override val transaction: TransactionResponse? = null,

    @SerialName("liquidity_pool_id")
    val liquidityPoolId: String,

    @SerialName("reserves_max")
    val reservesMax: List<AssetAmount>,

    @SerialName("min_price")
    val minPrice: String,

    @SerialName("min_price_r")
    val minPriceR: Price,

    @SerialName("max_price")
    val maxPrice: String,

    @SerialName("max_price_r")
    val maxPriceR: Price,

    @SerialName("reserves_deposited")
    val reservesDeposited: List<AssetAmount>,

    @SerialName("shares_received")
    val sharesReceived: String
) : OperationResponse()
