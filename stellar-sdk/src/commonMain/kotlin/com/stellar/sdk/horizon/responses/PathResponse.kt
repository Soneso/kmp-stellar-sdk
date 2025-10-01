package com.stellar.sdk.horizon.responses

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents a path response from the Horizon API.
 *
 * Paths represent possible payment routes between assets on the Stellar network.
 * The Horizon API provides two pathfinding endpoints:
 * - Strict Send: Find paths given a source asset and amount
 * - Strict Receive: Find paths given a destination asset and amount
 *
 * Each path contains the source and destination assets, amounts, and a list of intermediate
 * assets that the payment would pass through.
 *
 * @property destinationAmount The amount that would be received at the destination
 * @property destinationAssetType The destination asset type
 * @property destinationAssetCode The destination asset code (null for native)
 * @property destinationAssetIssuer The destination asset issuer (null for native)
 * @property sourceAmount The amount that would be sent from the source
 * @property sourceAssetType The source asset type
 * @property sourceAssetCode The source asset code (null for native)
 * @property sourceAssetIssuer The source asset issuer (null for native)
 * @property path List of intermediate assets in the payment path
 *
 * @see <a href="https://developers.stellar.org/api/aggregations/paths/">Path Finding documentation</a>
 */
@Serializable
data class PathResponse(
    @SerialName("destination_amount")
    val destinationAmount: String,

    @SerialName("destination_asset_type")
    val destinationAssetType: String,

    @SerialName("destination_asset_code")
    val destinationAssetCode: String? = null,

    @SerialName("destination_asset_issuer")
    val destinationAssetIssuer: String? = null,

    @SerialName("source_amount")
    val sourceAmount: String,

    @SerialName("source_asset_type")
    val sourceAssetType: String,

    @SerialName("source_asset_code")
    val sourceAssetCode: String? = null,

    @SerialName("source_asset_issuer")
    val sourceAssetIssuer: String? = null,

    @SerialName("path")
    val path: List<Asset>
) : Response() {

    /**
     * Returns the destination asset as an Asset object.
     */
    fun getDestinationAsset(): Asset {
        return Asset(
            assetType = destinationAssetType,
            assetCode = destinationAssetCode,
            assetIssuer = destinationAssetIssuer
        )
    }

    /**
     * Returns the source asset as an Asset object.
     */
    fun getSourceAsset(): Asset {
        return Asset(
            assetType = sourceAssetType,
            assetCode = sourceAssetCode,
            assetIssuer = sourceAssetIssuer
        )
    }
}
