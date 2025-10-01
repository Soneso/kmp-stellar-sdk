package com.stellar.sdk.horizon.responses.operations

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import com.stellar.sdk.horizon.responses.TransactionResponse

/**
 * Represents AccountMerge operation response.
 *
 * @see <a href="https://developers.stellar.org/docs/data/horizon/api-reference/resources/operations/object/account-merge">Operation documentation</a>
 */
@Serializable
@SerialName("account_merge")
data class AccountMergeOperationResponse(
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

    @SerialName("account")
    val account: String,

    @SerialName("account_muxed")
    val accountMuxed: String? = null,

    @SerialName("account_muxed_id")
    val accountMuxedId: String? = null,

    @SerialName("into")
    val into: String,

    @SerialName("into_muxed")
    val intoMuxed: String? = null,

    @SerialName("into_muxed_id")
    val intoMuxedId: String? = null
) : OperationResponse()
