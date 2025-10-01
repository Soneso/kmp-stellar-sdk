package com.stellar.sdk.horizon.responses.effects

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Abstract base class for trustline authorization effect responses.
 *
 * This class provides common properties for trustline_authorized,
 * trustline_deauthorized, and trustline_authorized_to_maintain_liabilities effects.
 *
 * @see [Effect Documentation](https://developers.stellar.org/docs/data/horizon/api-reference/resources/effects)
 */
@Serializable
abstract class TrustlineAuthorizationResponse : EffectResponse() {
    /**
     * The trustor account
     */
    @SerialName("trustor")
    abstract val trustor: String

    /**
     * The asset type (native, credit_alphanum4, or credit_alphanum12)
     */
    @SerialName("asset_type")
    abstract val assetType: String

    /**
     * The asset code
     */
    @SerialName("asset_code")
    abstract val assetCode: String?
}
