package org.stellar.sdk.horizon.responses.operations

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.stellar.sdk.horizon.responses.TransactionResponse

/**
 * Represents BeginSponsoringFutureReserves operation response.
 *
 * @see <a href="https://developers.stellar.org/docs/data/horizon/api-reference/resources/operations/object/begin-sponsoring-future-reserves">Operation documentation</a>
 */
@Serializable
@SerialName("begin_sponsoring_future_reserves")
data class BeginSponsoringFutureReservesOperationResponse(
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

    @SerialName("sponsored_id")
    val sponsoredId: String
) : OperationResponse()
