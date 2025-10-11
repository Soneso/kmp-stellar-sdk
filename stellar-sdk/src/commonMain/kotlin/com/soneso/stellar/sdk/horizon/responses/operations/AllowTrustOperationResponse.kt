package com.soneso.stellar.sdk.horizon.responses.operations

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import com.soneso.stellar.sdk.horizon.responses.TransactionResponse

/**
 * Represents AllowTrust operation response.
 *
 * @deprecated As of release 0.24.0, replaced by SetTrustLineFlagsOperationResponse
 * @see <a href="https://developers.stellar.org/docs/data/horizon/api-reference/resources/operations/object/allow-trust">Operation documentation</a>
 */
@Serializable
@SerialName("allow_trust")
@Deprecated("Replaced by SetTrustLineFlagsOperationResponse")
data class AllowTrustOperationResponse(
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

    @SerialName("trustee")
    val trustee: String,

    @SerialName("trustee_muxed")
    val trusteeMuxed: String? = null,

    @SerialName("trustee_muxed_id")
    val trusteeMuxedId: String? = null,

    @SerialName("asset_type")
    val assetType: String,

    @SerialName("asset_code")
    val assetCode: String? = null,

    @SerialName("asset_issuer")
    val assetIssuer: String? = null,

    @SerialName("authorize")
    val authorize: Boolean? = null,

    @SerialName("authorize_to_maintain_liabilities")
    val authorizeToMaintainLiabilities: Boolean? = null
) : OperationResponse()
