package com.stellar.sdk.horizon.responses.effects

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Abstract base class for trustline Create/Update/Delete effect responses.
 *
 * This class provides common properties for trustline_created, trustline_updated,
 * and trustline_removed effects.
 *
 * @see [Effect Documentation](https://developers.stellar.org/docs/data/horizon/api-reference/resources/effects)
 */
@Serializable
abstract class TrustlineCUDResponse : EffectResponse() {
    /**
     * The limit for the trustline
     */
    @SerialName("limit")
    abstract val limit: String

    /**
     * The asset type (native, credit_alphanum4, credit_alphanum12, or liquidity_pool_shares)
     */
    @SerialName("asset_type")
    abstract val assetType: String

    /**
     * The asset code (for credit assets)
     */
    @SerialName("asset_code")
    abstract val assetCode: String?

    /**
     * The asset issuer (for credit assets)
     */
    @SerialName("asset_issuer")
    abstract val assetIssuer: String?

    /**
     * The liquidity pool ID (for liquidity pool shares)
     */
    @SerialName("liquidity_pool_id")
    abstract val liquidityPoolId: String?
}
