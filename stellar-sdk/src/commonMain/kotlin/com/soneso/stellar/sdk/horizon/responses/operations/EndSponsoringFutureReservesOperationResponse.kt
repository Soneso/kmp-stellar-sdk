package com.soneso.stellar.sdk.horizon.responses.operations

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import com.soneso.stellar.sdk.horizon.responses.TransactionResponse

/**
 * Represents EndSponsoringFutureReserves operation response.
 *
 * @see <a href="https://developers.stellar.org/docs/data/horizon/api-reference/resources/operations/object/end-sponsoring-future-reserves">Operation documentation</a>
 */
@Serializable
@SerialName("end_sponsoring_future_reserves")
data class EndSponsoringFutureReservesOperationResponse(
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

    @SerialName("begin_sponsor")
    val beginSponsor: String,

    @SerialName("begin_sponsor_muxed")
    val beginSponsorMuxed: String? = null,

    @SerialName("begin_sponsor_muxed_id")
    val beginSponsorMuxedId: String? = null
) : OperationResponse()
