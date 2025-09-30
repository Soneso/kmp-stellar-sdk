package org.stellar.sdk.horizon.responses.operations

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.stellar.sdk.horizon.responses.TransactionResponse

/**
 * Represents ChangeTrust operation response.
 *
 * @see <a href="https://developers.stellar.org/docs/data/horizon/api-reference/resources/operations/object/change-trust">Operation documentation</a>
 */
@Serializable
@SerialName("change_trust")
data class ChangeTrustOperationResponse(
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

    @SerialName("trustor")
    val trustor: String,

    @SerialName("trustor_muxed")
    val trustorMuxed: String? = null,

    @SerialName("trustor_muxed_id")
    val trustorMuxedId: String? = null,

    @SerialName("trustee")
    val trustee: String? = null,

    @SerialName("asset_type")
    val assetType: String,

    @SerialName("asset_code")
    val assetCode: String? = null,

    @SerialName("asset_issuer")
    val assetIssuer: String? = null,

    @SerialName("limit")
    val limit: String,

    @SerialName("liquidity_pool_id")
    val liquidityPoolId: String? = null
) : OperationResponse()
