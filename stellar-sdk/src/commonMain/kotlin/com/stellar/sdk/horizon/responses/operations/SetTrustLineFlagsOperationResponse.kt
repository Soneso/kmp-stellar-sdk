package com.stellar.sdk.horizon.responses.operations

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import com.stellar.sdk.horizon.responses.TransactionResponse

/**
 * Represents SetTrustLineFlags operation response.
 *
 * @see <a href="https://developers.stellar.org/docs/data/horizon/api-reference/resources/operations/object/set-trustline-flags">Operation documentation</a>
 */
@Serializable
@SerialName("set_trust_line_flags")
data class SetTrustLineFlagsOperationResponse(
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

    @SerialName("clear_flags")
    val clearFlags: List<Int>? = null,

    @SerialName("clear_flags_s")
    val clearFlagStrings: List<String>? = null,

    @SerialName("set_flags")
    val setFlags: List<Int>? = null,

    @SerialName("set_flags_s")
    val setFlagStrings: List<String>? = null,

    @SerialName("trustor")
    val trustor: String
) : OperationResponse()
