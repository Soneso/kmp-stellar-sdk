package com.soneso.stellar.sdk.horizon.responses.operations

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import com.soneso.stellar.sdk.horizon.responses.TransactionResponse

/**
 * Represents InvokeHostFunction operation response.
 *
 * @see <a href="https://developers.stellar.org/docs/data/horizon/api-reference/resources/operations/object/invoke-host-function">Operation documentation</a>
 */
@Serializable
@SerialName("invoke_host_function")
data class InvokeHostFunctionOperationResponse(
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

    @SerialName("function")
    val function: String,

    @SerialName("parameters")
    val parameters: List<HostFunctionParameter>? = null,

    @SerialName("address")
    val address: String? = null,

    @SerialName("salt")
    val salt: String? = null,

    @SerialName("asset_balance_changes")
    val assetBalanceChanges: List<AssetContractBalanceChange>? = null
) : OperationResponse() {

    @Serializable
    data class HostFunctionParameter(
        @SerialName("type")
        val type: String,

        @SerialName("value")
        val value: String
    )

    @Serializable
    data class AssetContractBalanceChange(
        @SerialName("asset_type")
        val assetType: String,

        @SerialName("asset_code")
        val assetCode: String? = null,

        @SerialName("asset_issuer")
        val assetIssuer: String? = null,

        @SerialName("type")
        val type: String,

        @SerialName("from")
        val from: String,

        @SerialName("to")
        val to: String,

        @SerialName("amount")
        val amount: String,

        @SerialName("destination_muxed_id")
        val destinationMuxedId: String? = null
    )
}
