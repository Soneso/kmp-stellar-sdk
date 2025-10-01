package com.stellar.sdk.horizon.responses

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents an account response from the Horizon API.
 *
 * @see <a href="https://developers.stellar.org/docs/fundamentals-and-concepts/stellar-data-structures/accounts">Account documentation</a>
 */
@Serializable
data class AccountResponse(
    @SerialName("id")
    val id: String,

    @SerialName("account_id")
    val accountId: String,

    @SerialName("sequence")
    val sequenceNumber: Long,

    @SerialName("sequence_ledger")
    val sequenceLedger: Long? = null,

    @SerialName("sequence_time")
    val sequenceTime: Long? = null,

    @SerialName("subentry_count")
    val subentryCount: Int,

    @SerialName("inflation_destination")
    val inflationDestination: String? = null,

    @SerialName("home_domain")
    val homeDomain: String? = null,

    @SerialName("last_modified_ledger")
    val lastModifiedLedger: Int,

    @SerialName("last_modified_time")
    val lastModifiedTime: String,

    @SerialName("thresholds")
    val thresholds: Thresholds,

    @SerialName("flags")
    val flags: Flags,

    @SerialName("balances")
    val balances: List<Balance>,

    @SerialName("signers")
    val signers: List<Signer>,

    @SerialName("data")
    val data: Map<String, String> = emptyMap(),

    @SerialName("num_sponsoring")
    val numSponsoring: Int? = null,

    @SerialName("num_sponsored")
    val numSponsored: Int? = null,

    @SerialName("sponsor")
    val sponsor: String? = null,

    @SerialName("paging_token")
    val pagingToken: String,

    @SerialName("_links")
    val links: Links
) : Response() {

    /**
     * Gets the incremented sequence number (current sequence + 1).
     * This is the sequence number that should be used for the next transaction.
     */
    fun getIncrementedSequenceNumber(): Long = sequenceNumber + 1

    /**
     * Represents account thresholds for different operation weights.
     */
    @Serializable
    data class Thresholds(
        @SerialName("low_threshold")
        val lowThreshold: Int,

        @SerialName("med_threshold")
        val medThreshold: Int,

        @SerialName("high_threshold")
        val highThreshold: Int
    )

    /**
     * Represents account authorization flags.
     */
    @Serializable
    data class Flags(
        @SerialName("auth_required")
        val authRequired: Boolean,

        @SerialName("auth_revocable")
        val authRevocable: Boolean,

        @SerialName("auth_immutable")
        val authImmutable: Boolean,

        @SerialName("auth_clawback_enabled")
        val authClawbackEnabled: Boolean
    )

    /**
     * Represents an account balance (native or trustline).
     */
    @Serializable
    data class Balance(
        @SerialName("asset_type")
        val assetType: String,

        @SerialName("asset_code")
        val assetCode: String? = null,

        @SerialName("asset_issuer")
        val assetIssuer: String? = null,

        @SerialName("liquidity_pool_id")
        val liquidityPoolId: String? = null,

        @SerialName("limit")
        val limit: String? = null,

        @SerialName("balance")
        val balance: String,

        @SerialName("buying_liabilities")
        val buyingLiabilities: String? = null,

        @SerialName("selling_liabilities")
        val sellingLiabilities: String? = null,

        @SerialName("is_authorized")
        val isAuthorized: Boolean? = null,

        @SerialName("is_authorized_to_maintain_liabilities")
        val isAuthorizedToMaintainLiabilities: Boolean? = null,

        @SerialName("is_clawback_enabled")
        val isClawbackEnabled: Boolean? = null,

        @SerialName("last_modified_ledger")
        val lastModifiedLedger: Int? = null,

        @SerialName("sponsor")
        val sponsor: String? = null
    )

    /**
     * Represents an account signer.
     */
    @Serializable
    data class Signer(
        @SerialName("key")
        val key: String,

        @SerialName("type")
        val type: String,

        @SerialName("weight")
        val weight: Int,

        @SerialName("sponsor")
        val sponsor: String? = null
    )

    /**
     * HAL links connected to the account.
     */
    @Serializable
    data class Links(
        @SerialName("self")
        val self: Link,

        @SerialName("transactions")
        val transactions: Link,

        @SerialName("operations")
        val operations: Link,

        @SerialName("payments")
        val payments: Link,

        @SerialName("effects")
        val effects: Link,

        @SerialName("offers")
        val offers: Link,

        @SerialName("trades")
        val trades: Link,

        @SerialName("data")
        val data: Link
    )
}
