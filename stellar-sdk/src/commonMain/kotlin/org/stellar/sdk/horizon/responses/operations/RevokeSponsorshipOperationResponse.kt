package org.stellar.sdk.horizon.responses.operations

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.stellar.sdk.horizon.responses.TransactionResponse

/**
 * Represents RevokeSponsorship operation response.
 *
 * @see <a href="https://developers.stellar.org/docs/data/horizon/api-reference/resources/operations/object/revoke-sponsorship">Operation documentation</a>
 */
@Serializable
@SerialName("revoke_sponsorship")
data class RevokeSponsorshipOperationResponse(
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

    @SerialName("account_id")
    val accountId: String? = null,

    @SerialName("claimable_balance_id")
    val claimableBalanceId: String? = null,

    @SerialName("data_account_id")
    val dataAccountId: String? = null,

    @SerialName("data_name")
    val dataName: String? = null,

    @SerialName("offer_id")
    val offerId: Long? = null,

    @SerialName("trustline_account_id")
    val trustlineAccountId: String? = null,

    @SerialName("trustline_asset")
    val trustlineAsset: String? = null,

    @SerialName("signer_account_id")
    val signerAccountId: String? = null,

    @SerialName("signer_key")
    val signerKey: String? = null
) : OperationResponse()
