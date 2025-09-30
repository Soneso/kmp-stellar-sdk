package org.stellar.sdk.horizon.responses.operations

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.stellar.sdk.horizon.responses.TransactionResponse

/**
 * Represents SetOptions operation response.
 *
 * @see <a href="https://developers.stellar.org/docs/data/horizon/api-reference/resources/operations/object/set-options">Operation documentation</a>
 */
@Serializable
@SerialName("set_options")
data class SetOptionsOperationResponse(
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

    @SerialName("low_threshold")
    val lowThreshold: Int? = null,

    @SerialName("med_threshold")
    val medThreshold: Int? = null,

    @SerialName("high_threshold")
    val highThreshold: Int? = null,

    @SerialName("inflation_dest")
    val inflationDestination: String? = null,

    @SerialName("home_domain")
    val homeDomain: String? = null,

    @SerialName("signer_key")
    val signerKey: String? = null,

    @SerialName("signer_weight")
    val signerWeight: Int? = null,

    @SerialName("master_key_weight")
    val masterKeyWeight: Int? = null,

    @SerialName("clear_flags")
    val clearFlags: List<Int>? = null,

    @SerialName("clear_flags_s")
    val clearFlagStrings: List<String>? = null,

    @SerialName("set_flags")
    val setFlags: List<Int>? = null,

    @SerialName("set_flags_s")
    val setFlagStrings: List<String>? = null
) : OperationResponse()
