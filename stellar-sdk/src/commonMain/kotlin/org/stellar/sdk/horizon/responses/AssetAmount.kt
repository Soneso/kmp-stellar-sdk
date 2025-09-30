package org.stellar.sdk.horizon.responses

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents an amount of an asset.
 *
 * @property asset The asset type (uses a simplified JSON representation for now)
 * @property amount The amount of the asset as a string (to preserve precision)
 */
@Serializable
data class AssetAmount(
    @SerialName("asset")
    val asset: String,

    @SerialName("amount")
    val amount: String
)
