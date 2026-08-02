package com.soneso.stellar.sdk.unitTests

import com.soneso.stellar.sdk.*
import com.soneso.stellar.sdk.xdr.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/**
 * Unit tests for LiquidityPool.
 */
class LiquidityPoolTest {

    companion object {
        const val ISSUER_A = "GADBBY4WFXKKFJ7CMTG3J5YAUXMQDBILRQ6W3U5IWN5TQFZU4MWZ5T4K"
        val ASSET_USD = AssetTypeCreditAlphaNum4("USD", ISSUER_A)
        val ASSET_EUR = AssetTypeCreditAlphaNum4("EUR", ISSUER_A)
    }

    @Test
    fun testLiquidityPoolConstruction() {
        // EUR < USD lexicographically
        val pool = LiquidityPool(ASSET_EUR, ASSET_USD)
        assertEquals(ASSET_EUR, pool.assetA)
        assertEquals(ASSET_USD, pool.assetB)
        assertEquals(LiquidityPool.FEE, pool.fee)
    }

    @Test
    fun testLiquidityPoolWithNativeAsset() {
        // Native < any credit asset
        val pool = LiquidityPool(AssetTypeNative, ASSET_USD)
        assertEquals(AssetTypeNative, pool.assetA)
        assertEquals(ASSET_USD, pool.assetB)
    }

    @Test
    fun testLiquidityPoolWrongOrderThrows() {
        assertFailsWith<IllegalArgumentException> {
            LiquidityPool(ASSET_USD, ASSET_EUR) // USD > EUR
        }
    }

    @Test
    fun testLiquidityPoolSameAssetThrows() {
        assertFailsWith<IllegalArgumentException> {
            LiquidityPool(ASSET_USD, ASSET_USD)
        }
    }

    @Test
    fun testLiquidityPoolDefaultFee() {
        assertEquals(30, LiquidityPool.FEE)
    }

    @Test
    fun testLiquidityPoolXdrRoundtrip() {
        val pool = LiquidityPool(ASSET_EUR, ASSET_USD)
        val xdr = pool.toXdr()
        assertTrue(xdr is LiquidityPoolParametersXdr.ConstantProduct)
        val restored = LiquidityPool.fromXdr(xdr)
        assertEquals(pool, restored)
    }

    @Test
    fun testLiquidityPoolGetId() = runTest {
        val pool = LiquidityPool(AssetTypeNative, ASSET_USD)
        val id = pool.getLiquidityPoolId()
        assertEquals(64, id.length)
        assertTrue(id.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun testLiquidityPoolEquality() {
        val pool1 = LiquidityPool(ASSET_EUR, ASSET_USD)
        val pool2 = LiquidityPool(ASSET_EUR, ASSET_USD)
        assertEquals(pool1, pool2)
        assertEquals(pool1.hashCode(), pool2.hashCode())
    }

    @Test
    fun testLiquidityPoolInequality() {
        val pool1 = LiquidityPool(AssetTypeNative, ASSET_EUR)
        val pool2 = LiquidityPool(AssetTypeNative, ASSET_USD)
        assertNotEquals(pool1, pool2)
    }
}
