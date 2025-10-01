package com.stellar.sdk.horizon.responses.operations

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import com.stellar.sdk.horizon.responses.TransactionResponse

/**
 * Represents Payment operation response.
 *
 * @see <a href="https://developers.stellar.org/docs/data/horizon/api-reference/resources/operations/object/payment">Operation documentation</a>
 */
@Serializable
@SerialName("payment")
data class PaymentOperationResponse(
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
    val amount: String,

    @SerialName("asset_type")
    val assetType: String,

    @SerialName("asset_code")
    val assetCode: String? = null,

    @SerialName("asset_issuer")
    val assetIssuer: String? = null,

    @SerialName("from")
    val from: String,

    @SerialName("from_muxed")
    val fromMuxed: String? = null,

    @SerialName("from_muxed_id")
    val fromMuxedId: String? = null,

    @SerialName("to")
    val to: String,

    @SerialName("to_muxed")
    val toMuxed: String? = null,

    @SerialName("to_muxed_id")
    val toMuxedId: String? = null
) : OperationResponse()
