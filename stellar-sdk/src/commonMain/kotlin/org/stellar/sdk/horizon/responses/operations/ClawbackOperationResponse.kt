package org.stellar.sdk.horizon.responses.operations

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.stellar.sdk.horizon.responses.TransactionResponse

/**
 * Represents Clawback operation response.
 *
 * @see <a href="https://developers.stellar.org/docs/data/horizon/api-reference/resources/operations/object/clawback">Operation documentation</a>
 */
@Serializable
@SerialName("clawback")
data class ClawbackOperationResponse(
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

    @SerialName("asset_type")
    val assetType: String,

    @SerialName("asset_code")
    val assetCode: String? = null,

    @SerialName("asset_issuer")
    val assetIssuer: String? = null,

    @SerialName("amount")
    val amount: String,

    @SerialName("from")
    val from: String,

    @SerialName("from_muxed")
    val fromMuxed: String? = null,

    @SerialName("from_muxed_id")
    val fromMuxedId: String? = null
) : OperationResponse()
