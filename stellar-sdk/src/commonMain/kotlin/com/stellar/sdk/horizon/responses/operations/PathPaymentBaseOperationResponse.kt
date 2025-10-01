package com.stellar.sdk.horizon.responses.operations

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Base class for path payment operations.
 * This is an abstract intermediate class that is extended by PathPaymentStrictReceive and PathPaymentStrictSend.
 */
@Serializable
abstract class PathPaymentBaseOperationResponse : OperationResponse() {
    @SerialName("amount")
    abstract val amount: String

    @SerialName("source_amount")
    abstract val sourceAmount: String

    @SerialName("from")
    abstract val from: String

    @SerialName("from_muxed")
    abstract val fromMuxed: String?

    @SerialName("from_muxed_id")
    abstract val fromMuxedId: String?

    @SerialName("to")
    abstract val to: String

    @SerialName("to_muxed")
    abstract val toMuxed: String?

    @SerialName("to_muxed_id")
    abstract val toMuxedId: String?

    @SerialName("asset_type")
    abstract val assetType: String

    @SerialName("asset_code")
    abstract val assetCode: String?

    @SerialName("asset_issuer")
    abstract val assetIssuer: String?

    @SerialName("source_asset_type")
    abstract val sourceAssetType: String

    @SerialName("source_asset_code")
    abstract val sourceAssetCode: String?

    @SerialName("source_asset_issuer")
    abstract val sourceAssetIssuer: String?

    @SerialName("path")
    abstract val path: List<PathAsset>

    @Serializable
    data class PathAsset(
        @SerialName("asset_type")
        val assetType: String,

        @SerialName("asset_code")
        val assetCode: String? = null,

        @SerialName("asset_issuer")
        val assetIssuer: String? = null
    )
}
