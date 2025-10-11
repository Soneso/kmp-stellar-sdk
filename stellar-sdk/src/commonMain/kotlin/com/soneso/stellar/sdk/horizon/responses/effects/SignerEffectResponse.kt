package com.soneso.stellar.sdk.horizon.responses.effects

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Abstract base class for signer effect responses.
 *
 * This class provides common properties for signer_created, signer_updated,
 * and signer_removed effects.
 *
 * @see [Effect Documentation](https://developers.stellar.org/docs/data/horizon/api-reference/resources/effects)
 */
@Serializable
abstract class SignerEffectResponse : EffectResponse() {
    /**
     * The weight of the signer
     */
    @SerialName("weight")
    abstract val weight: Int

    /**
     * The public key of the signer
     */
    @SerialName("public_key")
    abstract val publicKey: String
}
