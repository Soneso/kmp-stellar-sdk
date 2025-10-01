package com.stellar.sdk.horizon.responses

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents a claimable balance response from the Horizon API.
 *
 * Claimable balances are used for trustless, non-interactive asset transfers.
 * They can be claimed by the intended destination account(s) when certain conditions are met.
 *
 * @property id Unique identifier for this claimable balance
 * @property assetString The asset available to be claimed (in canonical form)
 * @property amount The amount of the asset that can be claimed
 * @property sponsor The account ID of the sponsor who is paying the reserves for this claimable balance
 * @property lastModifiedLedger The sequence number of the last ledger in which this claimable balance was modified
 * @property lastModifiedTime An ISO 8601 formatted string of the last time this claimable balance was modified
 * @property claimants The list of entities who can claim the balance
 * @property flags Flags set on this claimable balance
 * @property pagingToken A cursor value for use in pagination
 * @property links HAL links related to this claimable balance
 *
 * @see <a href="https://developers.stellar.org/api/resources/claimablebalances/">Claimable Balance documentation</a>
 */
@Serializable
data class ClaimableBalanceResponse(
    @SerialName("id")
    val id: String,

    @SerialName("asset")
    val assetString: String,

    @SerialName("amount")
    val amount: String,

    @SerialName("sponsor")
    val sponsor: String? = null,

    @SerialName("last_modified_ledger")
    val lastModifiedLedger: Long? = null,

    @SerialName("last_modified_time")
    val lastModifiedTime: String? = null,

    @SerialName("claimants")
    val claimants: List<Claimant> = emptyList(),

    @SerialName("flags")
    val flags: Flags? = null,

    @SerialName("paging_token")
    val pagingToken: String,

    @SerialName("_links")
    val links: Links
) : Response() {

    /**
     * Flags set on this claimable balance.
     *
     * @property clawbackEnabled Whether the claimable balance can be clawed back by the asset issuer
     */
    @Serializable
    data class Flags(
        @SerialName("clawback_enabled")
        val clawbackEnabled: Boolean? = null
    )

    /**
     * HAL links connected to this claimable balance.
     *
     * @property self Link to this claimable balance
     * @property transactions Link to transactions related to this claimable balance
     * @property operations Link to operations related to this claimable balance
     */
    @Serializable
    data class Links(
        @SerialName("self")
        val self: Link,

        @SerialName("transactions")
        val transactions: Link? = null,

        @SerialName("operations")
        val operations: Link? = null
    )
}
