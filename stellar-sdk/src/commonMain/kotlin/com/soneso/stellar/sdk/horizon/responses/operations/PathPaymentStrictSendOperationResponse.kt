package com.soneso.stellar.sdk.horizon.responses.operations

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import com.soneso.stellar.sdk.horizon.responses.TransactionResponse

/**
 * Represents PathPaymentStrictSend operation response.
 *
 * @see <a href="https://developers.stellar.org/docs/data/horizon/api-reference/resources/operations/object/path-payment-strict-send">Operation documentation</a>
 */
@Serializable
@SerialName("path_payment_strict_send")
data class PathPaymentStrictSendOperationResponse(
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

    @SerialName("amount")
    override val amount: String,

    @SerialName("source_amount")
    override val sourceAmount: String,

    @SerialName("from")
    override val from: String,

    @SerialName("from_muxed")
    override val fromMuxed: String? = null,

    @SerialName("from_muxed_id")
    override val fromMuxedId: String? = null,

    @SerialName("to")
    override val to: String,

    @SerialName("to_muxed")
    override val toMuxed: String? = null,

    @SerialName("to_muxed_id")
    override val toMuxedId: String? = null,

    @SerialName("asset_type")
    override val assetType: String,

    @SerialName("asset_code")
    override val assetCode: String? = null,

    @SerialName("asset_issuer")
    override val assetIssuer: String? = null,

    @SerialName("source_asset_type")
    override val sourceAssetType: String,

    @SerialName("source_asset_code")
    override val sourceAssetCode: String? = null,

    @SerialName("source_asset_issuer")
    override val sourceAssetIssuer: String? = null,

    @SerialName("path")
    override val path: List<PathAsset>,

    @SerialName("destination_min")
    val destinationMin: String
) : PathPaymentBaseOperationResponse()
