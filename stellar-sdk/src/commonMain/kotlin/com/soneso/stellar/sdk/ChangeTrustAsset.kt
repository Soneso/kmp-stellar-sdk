package com.soneso.stellar.sdk

import com.soneso.stellar.sdk.xdr.*

/**
 * Represents an asset in a change trust operation on the Stellar network.
 *
 * This class can represent both regular assets (native, credit_alphanum4, credit_alphanum12)
 * and liquidity pool shares for change trustline operations.
 *
 * ## Usage
 *
 * ### For Regular Assets
 * ```kotlin
 * val asset = AssetTypeCreditAlphaNum4("USD", issuer)
 * val changeTrustAsset = ChangeTrustAsset(asset)
 * ```
 *
 * ### For Liquidity Pool Shares
 * ```kotlin
 * val assetA = AssetTypeCreditAlphaNum4("USD", issuer)
 * val assetB = AssetTypeCreditAlphaNum4("EUR", issuer)
 * val pool = LiquidityPool(assetA, assetB)
 * val changeTrustAsset = ChangeTrustAsset(pool)
 * ```
 *
 * @property assetType The type of the asset
 * @property asset The asset for regular trustlines (null for liquidity pool shares)
 * @property liquidityPool The liquidity pool for pool share trustlines (null for regular assets)
 *
 * @see ChangeTrustOperation
 * @see LiquidityPool
 */
sealed class ChangeTrustAsset {
    abstract val assetType: AssetTypeXdr

    /**
     * Converts this ChangeTrustAsset to its XDR representation.
     *
     * @return The XDR representation of this ChangeTrustAsset
     */
    abstract fun toXdr(): ChangeTrustAssetXdr

    /**
     * ChangeTrustAsset for a regular asset (native, alphanum4, or alphanum12).
     *
     * @property asset The asset for which trust is being changed
     */
    data class Wrapper(val asset: Asset) : ChangeTrustAsset() {
        override val assetType: AssetTypeXdr
            get() = asset.type

        override fun toXdr(): ChangeTrustAssetXdr {
            return when (val assetXdr = asset.toXdr()) {
                is AssetXdr.Void -> ChangeTrustAssetXdr.Void
                is AssetXdr.AlphaNum4 -> ChangeTrustAssetXdr.AlphaNum4(assetXdr.value)
                is AssetXdr.AlphaNum12 -> ChangeTrustAssetXdr.AlphaNum12(assetXdr.value)
            }
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false
            other as Wrapper
            return asset == other.asset
        }

        override fun hashCode(): Int = asset.hashCode()
    }

    /**
     * ChangeTrustAsset for a liquidity pool share.
     *
     * @property liquidityPool The liquidity pool for which trust is being changed
     */
    data class LiquidityPoolShare(val liquidityPool: LiquidityPool) : ChangeTrustAsset() {
        override val assetType: AssetTypeXdr
            get() = AssetTypeXdr.ASSET_TYPE_POOL_SHARE

        override fun toXdr(): ChangeTrustAssetXdr {
            return ChangeTrustAssetXdr.LiquidityPool(liquidityPool.toXdr())
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false
            other as LiquidityPoolShare
            return liquidityPool == other.liquidityPool
        }

        override fun hashCode(): Int = liquidityPool.hashCode()
    }

    companion object {
        /**
         * Factory method to create a ChangeTrustAsset from a regular asset.
         *
         * @param asset The asset for which trust is being changed
         * @return A ChangeTrustAsset.Wrapper instance
         */
        operator fun invoke(asset: Asset): ChangeTrustAsset = Wrapper(asset)

        /**
         * Factory method to create a ChangeTrustAsset from a liquidity pool.
         *
         * @param liquidityPool The liquidity pool for which trust is being changed
         * @return A ChangeTrustAsset.LiquidityPoolShare instance
         */
        operator fun invoke(liquidityPool: LiquidityPool): ChangeTrustAsset = LiquidityPoolShare(liquidityPool)

        /**
         * Creates a ChangeTrustAsset from its XDR representation.
         *
         * @param xdr The XDR representation of the ChangeTrustAsset
         * @return A new ChangeTrustAsset instance
         * @throws IllegalArgumentException if the asset type is unknown
         */
        fun fromXdr(xdr: ChangeTrustAssetXdr): ChangeTrustAsset {
            return when (xdr) {
                is ChangeTrustAssetXdr.Void -> Wrapper(AssetTypeNative)
                is ChangeTrustAssetXdr.AlphaNum4 -> Wrapper(AssetTypeCreditAlphaNum4.fromXdr(xdr.value))
                is ChangeTrustAssetXdr.AlphaNum12 -> Wrapper(AssetTypeCreditAlphaNum12.fromXdr(xdr.value))
                is ChangeTrustAssetXdr.LiquidityPool -> LiquidityPoolShare(LiquidityPool.fromXdr(xdr.value))
            }
        }
    }
}
