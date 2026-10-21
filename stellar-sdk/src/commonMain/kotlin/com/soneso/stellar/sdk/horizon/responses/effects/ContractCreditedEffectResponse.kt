package com.soneso.stellar.sdk.horizon.responses.effects

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents contract_credited effect response.
 *
 * This effect occurs when a contract is credited with an asset.
 *
 * @property contractMuxed The muxed address of [contract] as a W... strkey, set by Horizon
 * for a transfer or mint to a muxed contract address. Null when no muxed destination is reported.
 * @property contractMuxedId The muxed id as the decimal string Horizon sends for a transfer or
 * mint to a muxed contract address. Null when no muxed destination is reported.
 *
 * @see [Effect Documentation](https://developers.stellar.org/docs/data/horizon/api-reference/resources/effects)
 */
@Serializable
@SerialName("contract_credited")
data class ContractCreditedEffectResponse(
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
     * The amount credited
     */
    val amount: String,

    @SerialName("contract_muxed")
    val contractMuxed: String? = null,
    @SerialName("contract_muxed_id")
    val contractMuxedId: String? = null
) : EffectResponse()
