package com.soneso.stellar.sdk

import com.soneso.stellar.sdk.xdr.*

/**
 * Represents a Liquidity Pool Parameters object on the Stellar network.
 *
 * Liquidity pools allow for automated market making through constant product pools.
 * Each pool contains two assets and a fee parameter.
 *
 * ## Usage
 * ```kotlin
 * val assetA = AssetTypeCreditAlphaNum4("USD", issuer)
 * val assetB = AssetTypeCreditAlphaNum4("EUR", issuer)
 * val pool = LiquidityPool(assetA, assetB)
 * ```
 *
 * ## Asset Ordering
 * Assets must be in lexicographic order (assetA < assetB). The constructor
 * will automatically validate this ordering.
 *
 * @property assetA The first asset in the pool (must be less than assetB)
 * @property assetB The second asset in the pool (must be greater than assetA)
 * @property fee The liquidity pool fee in basis points (default 30 = 0.3%)
 *
 * @see <a href="https://developers.stellar.org/docs/learn/encyclopedia/sdex/liquidity-on-stellar-sdex-liquidity-pools">Liquidity Pools</a>
 */
data class LiquidityPool(
    val assetA: Asset,
    val assetB: Asset,
    val fee: Int = FEE
) {
    init {
        require(assetA.compareTo(assetB) < 0) {
            "Assets are not in lexicographic order. assetA must be < assetB. " +
            "Current order: $assetA vs $assetB"
        }
    }

    /**
     * Converts this LiquidityPool to its XDR representation.
     *
     * @return The XDR object representing this LiquidityPool
     */
    fun toXdr(): LiquidityPoolParametersXdr {
        val params = LiquidityPoolConstantProductParametersXdr(
            assetA = assetA.toXdr(),
            assetB = assetB.toXdr(),
            fee = Int32Xdr(fee)
        )
        return LiquidityPoolParametersXdr.ConstantProduct(params)
    }

    /**
     * Generates the LiquidityPoolID for this LiquidityPool.
     *
     * The pool ID is computed as the SHA-256 hash of the XDR-encoded
     * liquidity pool parameters.
     *
     * @return The liquidity pool ID as a hex string (lowercase)
     */
    fun getLiquidityPoolId(): String {
        val writer = XdrWriter()
        toXdr().encode(writer)
        val xdrBytes = writer.toByteArray()
        val poolId = Util.hash(xdrBytes)
        return Util.bytesToHex(poolId).lowercase()
    }

    companion object {
        /**
         * The standard liquidity pool fee in basis points.
         * 30 basis points = 0.30% fee.
         */
        const val FEE = 30

        /**
         * Creates a LiquidityPool from its XDR representation.
         *
         * @param xdr The XDR object to convert from
         * @return A new LiquidityPool object
         * @throws IllegalArgumentException if the XDR discriminant is not LIQUIDITY_POOL_CONSTANT_PRODUCT
         */
        fun fromXdr(xdr: LiquidityPoolParametersXdr): LiquidityPool {
            return when (xdr) {
                is LiquidityPoolParametersXdr.ConstantProduct -> {
                    val params = xdr.value
                    LiquidityPool(
                        assetA = Asset.fromXdr(params.assetA),
                        assetB = Asset.fromXdr(params.assetB),
                        fee = params.fee.value
                    )
                }
            }
        }
    }
}
