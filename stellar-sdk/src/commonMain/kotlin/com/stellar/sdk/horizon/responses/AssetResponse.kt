package com.stellar.sdk.horizon.responses

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents an asset response from the Horizon API.
 *
 * Assets are representations of value issued on the Stellar network. This response provides
 * detailed information about an asset including its supply, number of accounts holding it,
 * and various flags set by the issuer.
 *
 * @property assetType The type of asset (credit_alphanum4, credit_alphanum12)
 * @property assetCode The asset code
 * @property assetIssuer The account ID of the asset issuer
 * @property pagingToken A cursor value for use in pagination
 * @property contractId The Stellar Asset Contract ID for this asset in hex format (Soroban)
 * @property numClaimableBalances The number of claimable balances holding this asset
 * @property numLiquidityPools The number of liquidity pools that include this asset
 * @property numContracts The number of contracts holding this asset
 * @property accounts Statistics about the number of accounts holding this asset
 * @property claimableBalancesAmount The total amount held in claimable balances
 * @property liquidityPoolsAmount The total amount held in liquidity pools
 * @property contractsAmount The total amount held in contracts
 * @property balances Statistics about the balances of this asset
 * @property flags Flags set on the asset issuer account
 * @property links HAL links related to this asset
 *
 * @see <a href="https://developers.stellar.org/api/resources/assets/">Asset documentation</a>
 */
@Serializable
data class AssetResponse(
    @SerialName("asset_type")
    val assetType: String,

    @SerialName("asset_code")
    val assetCode: String,

    @SerialName("asset_issuer")
    val assetIssuer: String,

    @SerialName("paging_token")
    val pagingToken: String,

    @SerialName("contract_id")
    val contractId: String? = null,

    @SerialName("num_claimable_balances")
    val numClaimableBalances: Int? = null,

    @SerialName("num_liquidity_pools")
    val numLiquidityPools: Int? = null,

    @SerialName("num_contracts")
    val numContracts: Int? = null,

    @SerialName("accounts")
    val accounts: Accounts,

    @SerialName("claimable_balances_amount")
    val claimableBalancesAmount: String? = null,

    @SerialName("liquidity_pools_amount")
    val liquidityPoolsAmount: String? = null,

    @SerialName("contracts_amount")
    val contractsAmount: String? = null,

    @SerialName("balances")
    val balances: Balances,

    @SerialName("flags")
    val flags: Flags,

    @SerialName("_links")
    val links: Links
) : Response() {

    /**
     * Statistics about accounts holding this asset.
     *
     * @property authorized The number of accounts authorized to hold this asset
     * @property authorizedToMaintainLiabilities The number of accounts authorized to maintain liabilities for this asset
     * @property unauthorized The number of unauthorized accounts holding this asset
     */
    @Serializable
    data class Accounts(
        @SerialName("authorized")
        val authorized: Int,

        @SerialName("authorized_to_maintain_liabilities")
        val authorizedToMaintainLiabilities: Int,

        @SerialName("unauthorized")
        val unauthorized: Int
    )

    /**
     * Statistics about balances of this asset.
     *
     * @property authorized The total amount held by authorized accounts
     * @property authorizedToMaintainLiabilities The total amount held by accounts authorized to maintain liabilities
     * @property unauthorized The total amount held by unauthorized accounts
     */
    @Serializable
    data class Balances(
        @SerialName("authorized")
        val authorized: String,

        @SerialName("authorized_to_maintain_liabilities")
        val authorizedToMaintainLiabilities: String,

        @SerialName("unauthorized")
        val unauthorized: String
    )

    /**
     * Flags set on the asset issuer account.
     *
     * @property authRequired If true, the asset issuer has AUTH_REQUIRED flag set
     * @property authRevocable If true, the asset issuer has AUTH_REVOCABLE flag set
     * @property authImmutable If true, the asset issuer has AUTH_IMMUTABLE flag set
     * @property authClawbackEnabled If true, the asset issuer has AUTH_CLAWBACK_ENABLED flag set
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
     * HAL links connected to this asset.
     *
     * @property toml Link to the stellar.toml file for this asset
     */
    @Serializable
    data class Links(
        @SerialName("toml")
        val toml: Link
    )
}
