package com.soneso.stellar.sdk.horizon.responses.operations

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import com.soneso.stellar.sdk.horizon.responses.Price
import com.soneso.stellar.sdk.horizon.responses.TransactionResponse

/**
 * Represents CreatePassiveSellOffer operation response.
 *
 * @see <a href="https://developers.stellar.org/docs/data/horizon/api-reference/resources/operations/object/passive-sell-offer">Operation documentation</a>
 */
@Serializable
@SerialName("create_passive_sell_offer")
data class CreatePassiveSellOfferOperationResponse(
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

    @SerialName("offer_id")
    val offerId: Long,

    @SerialName("amount")
    val amount: String,

    @SerialName("price")
    val price: String,

    @SerialName("price_r")
    val priceR: Price,

    @SerialName("buying_asset_type")
    val buyingAssetType: String,

    @SerialName("buying_asset_code")
    val buyingAssetCode: String? = null,

    @SerialName("buying_asset_issuer")
    val buyingAssetIssuer: String? = null,

    @SerialName("selling_asset_type")
    val sellingAssetType: String,

    @SerialName("selling_asset_code")
    val sellingAssetCode: String? = null,

    @SerialName("selling_asset_issuer")
    val sellingAssetIssuer: String? = null
) : OperationResponse()
