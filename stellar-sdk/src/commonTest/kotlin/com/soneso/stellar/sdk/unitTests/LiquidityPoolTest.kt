package com.soneso.stellar.sdk.unitTests

import com.soneso.stellar.sdk.*
import com.soneso.stellar.sdk.xdr.*
import kotlinx.coroutines.test.runTest
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
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

    // ========== Same-code assets: issuer raw key order ==========
    // Raw keys X 0x6801..1f < Y 0x7401..1f, while the strkey texts sort Y before X.

    private val issuerX = "GBUACAQDAQCQMBYIBEFAWDANBYHRAEISCMKBKFQXDAMRUGY4DUPB6CLH"
    private val issuerY = "GB2ACAQDAQCQMBYIBEFAWDANBYHRAEISCMKBKFQXDAMRUGY4DUPB7BZ4"
    private val usdcX = AssetTypeCreditAlphaNum4("USDC", issuerX)
    private val usdcY = AssetTypeCreditAlphaNum4("USDC", issuerY)
    private val poolParamsBase64 =
        "AAAAAAAAAAFVU0RDAAAAAGgBAgMEBQYHCAkKCwwNDg8QERITFBUWFxgZGhscHR4fAAAAAVVTREMAAAAAdAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8AAAAe"

    private fun encode(write: (XdrWriter) -> Unit): ByteArray = XdrWriter().also(write).toByteArray()

    @Test
    fun testSameCodeAssetsOrderByIssuerKeyBytes() {
        assertTrue(issuerY < issuerX, "precondition: strkey text order puts Y first")
        assertTrue(usdcX < usdcY)
        assertTrue(usdcY > usdcX)
        assertEquals(usdcX, LiquidityPool(usdcX, usdcY).assetA)
        assertFailsWith<IllegalArgumentException> { LiquidityPool(usdcY, usdcX) }
    }

    @OptIn(ExperimentalEncodingApi::class)
    @Test
    fun testSameCodePoolIdMatchesVector() = runTest {
        val pool = LiquidityPool(usdcX, usdcY)
        assertEquals(poolParamsBase64, Base64.encode(encode(pool.toXdr()::encode)))
        val id = pool.getLiquidityPoolId()
        assertEquals("2c325546b1bf03f8d1b9c0b74974cdef7609c60202d72a30e34f393ccf5eed1c", id)
        assertEquals("LAWDEVKGWG7QH6GRXHALOSLUZXXXMCOGAIBNOKRQ4NHTSPGPL3WRYKDA", StrKey.encodeLiquidityPool(Util.hexToBytes(id)))
    }

    @OptIn(ExperimentalEncodingApi::class)
    @Test
    fun testChangeTrustWithSameCodePoolDecodesAndReencodes() {
        val params = LiquidityPoolParametersXdr.decode(XdrReader(Base64.decode(poolParamsBase64)))
        val op = OperationXdr(
            sourceAccount = null,
            body = OperationBodyXdr.ChangeTrustOp(
                ChangeTrustOpXdr(ChangeTrustAssetXdr.LiquidityPool(params), Int64Xdr(Long.MAX_VALUE))
            )
        )
        val wire = encode(op::encode)
        val decoded = Operation.fromXdr(OperationXdr.decode(XdrReader(wire)))
        assertContentEquals(wire, encode(decoded.toXdr()::encode))
    }
}
