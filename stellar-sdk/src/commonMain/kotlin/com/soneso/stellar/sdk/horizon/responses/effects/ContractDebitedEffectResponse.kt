package com.soneso.stellar.sdk.horizon.responses.effects

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents contract_debited effect response.
 *
 * This effect occurs when a contract is debited with an asset.
 *
 * @see [Effect Documentation](https://developers.stellar.org/docs/data/horizon/api-reference/resources/effects)
 */
@Serializable
@SerialName("contract_debited")
data class ContractDebitedEffectResponse(
    override val id: String,
    override val account: String?,
    @SerialName("account_muxed")
    override val accountMuxed: String? = null,
    @SerialName("account_muxed_id")
    override val accountMuxedId: String? = null,
    override val type: String,
    @SerialName("created_at")
    override val createdAt: String,
    @SerialName("paging_token")
    override val pagingToken: String,
    @SerialName("_links")
    override val links: EffectLinks,

    /**
     * The asset type
     */
    @SerialName("asset_type")
    val assetType: String,

    /**
     * The asset code
     */
    @SerialName("asset_code")
    val assetCode: String? = null,

    /**
     * The asset issuer
     */
    @SerialName("asset_issuer")
    val assetIssuer: String? = null,

    /**
     * The contract ID
     */
    val contract: String,

    /**
     * The amount debited
     */
    val amount: String
) : EffectResponse()
