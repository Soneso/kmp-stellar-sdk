package com.soneso.stellar.sdk.unitTests

import com.soneso.stellar.sdk.*
import com.soneso.stellar.sdk.xdr.*
import kotlin.test.*

/**
 * Unit tests for ChangeTrustAsset.
 */
class ChangeTrustAssetTest {

    companion object {
        const val ISSUER_A = "GADBBY4WFXKKFJ7CMTG3J5YAUXMQDBILRQ6W3U5IWN5TQFZU4MWZ5T4K"
        val ASSET_USD = AssetTypeCreditAlphaNum4("USD", ISSUER_A)
        val ASSET_EUR = AssetTypeCreditAlphaNum4("EUR", ISSUER_A)
    }

    @Test
    fun testChangeTrustAssetFromRegularAsset() {
        val cta = ChangeTrustAsset(ASSET_USD)
        assertTrue(cta is ChangeTrustAsset.Wrapper)
        assertEquals(ASSET_USD, (cta as ChangeTrustAsset.Wrapper).asset)
        assertEquals(AssetTypeXdr.ASSET_TYPE_CREDIT_ALPHANUM4, cta.assetType)
    }

    @Test
    fun testChangeTrustAssetFromNativeAsset() {
        val cta = ChangeTrustAsset(AssetTypeNative)
        assertTrue(cta is ChangeTrustAsset.Wrapper)
        assertEquals(AssetTypeXdr.ASSET_TYPE_NATIVE, cta.assetType)
    }

    @Test
    fun testChangeTrustAssetFromLiquidityPool() {
        val pool = LiquidityPool(ASSET_EUR, ASSET_USD)
        val cta = ChangeTrustAsset(pool)
        assertTrue(cta is ChangeTrustAsset.LiquidityPoolShare)
        assertEquals(AssetTypeXdr.ASSET_TYPE_POOL_SHARE, cta.assetType)
        assertEquals(pool, (cta as ChangeTrustAsset.LiquidityPoolShare).liquidityPool)
    }

    @Test
    fun testChangeTrustAssetWrapperXdrRoundtrip() {
        val cta = ChangeTrustAsset(ASSET_USD)
        val xdr = cta.toXdr()
        val restored = ChangeTrustAsset.fromXdr(xdr)
        assertTrue(restored is ChangeTrustAsset.Wrapper)
        assertEquals(ASSET_USD, (restored as ChangeTrustAsset.Wrapper).asset)
    }

    @Test
    fun testChangeTrustAssetAlpha12XdrRoundtrip() {
        val asset = AssetTypeCreditAlphaNum12("LONGASSET", ISSUER_A)
        val cta = ChangeTrustAsset(asset)
        val xdr = cta.toXdr()
        val restored = ChangeTrustAsset.fromXdr(xdr)
        assertTrue(restored is ChangeTrustAsset.Wrapper)
        assertEquals(asset, (restored as ChangeTrustAsset.Wrapper).asset)
    }

    @Test
    fun testChangeTrustAssetNativeXdrRoundtrip() {
        val cta = ChangeTrustAsset(AssetTypeNative)
        val xdr = cta.toXdr()
        val restored = ChangeTrustAsset.fromXdr(xdr)
        assertTrue(restored is ChangeTrustAsset.Wrapper)
        assertEquals(AssetTypeNative, (restored as ChangeTrustAsset.Wrapper).asset)
    }

    @Test
    fun testChangeTrustAssetLiquidityPoolXdrRoundtrip() {
        val pool = LiquidityPool(ASSET_EUR, ASSET_USD)
        val cta = ChangeTrustAsset(pool)
        val xdr = cta.toXdr()
        val restored = ChangeTrustAsset.fromXdr(xdr)
        assertTrue(restored is ChangeTrustAsset.LiquidityPoolShare)
        assertEquals(pool, (restored as ChangeTrustAsset.LiquidityPoolShare).liquidityPool)
    }

    @Test
    fun testChangeTrustAssetWrapperEquality() {
        val cta1 = ChangeTrustAsset(ASSET_USD)
        val cta2 = ChangeTrustAsset(ASSET_USD)
        val cta3 = ChangeTrustAsset(ASSET_EUR)
        assertEquals(cta1, cta2)
        assertEquals(cta1.hashCode(), cta2.hashCode())
        assertNotEquals(cta1, cta3)
    }

    @Test
    fun testChangeTrustAssetPoolShareEquality() {
        val pool1 = LiquidityPool(ASSET_EUR, ASSET_USD)
        val pool2 = LiquidityPool(ASSET_EUR, ASSET_USD)
        val cta1 = ChangeTrustAsset(pool1)
        val cta2 = ChangeTrustAsset(pool2)
        assertEquals(cta1, cta2)
        assertEquals(cta1.hashCode(), cta2.hashCode())
    }

    @Test
    fun testChangeTrustAssetDifferentTypes() {
        val wrapper = ChangeTrustAsset(ASSET_USD)
        val pool = LiquidityPool(ASSET_EUR, ASSET_USD)
        val poolShare = ChangeTrustAsset(pool)
        assertNotEquals(wrapper, poolShare)
    }

    @Test
    fun testChangeTrustAssetWrapperEqualsReflexiveAndNull() {
        val cta = ChangeTrustAsset(ASSET_USD)
        assertEquals(cta, cta)
        assertFalse(cta.equals(null))
    }

    @Test
    fun testChangeTrustAssetPoolShareEqualsReflexiveAndNull() {
        val pool = LiquidityPool(ASSET_EUR, ASSET_USD)
        val cta = ChangeTrustAsset(pool)
        assertEquals(cta, cta)
        assertFalse(cta.equals(null))
    }
}
