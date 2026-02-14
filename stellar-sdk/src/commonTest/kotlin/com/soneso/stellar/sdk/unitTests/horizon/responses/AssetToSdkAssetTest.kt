package com.soneso.stellar.sdk.unitTests.horizon.responses

import com.soneso.stellar.sdk.AssetTypeNative
import com.soneso.stellar.sdk.AssetTypeCreditAlphaNum4
import com.soneso.stellar.sdk.AssetTypeCreditAlphaNum12
import com.soneso.stellar.sdk.xdr.AssetTypeXdr
import com.soneso.stellar.sdk.horizon.responses.Asset as HorizonAsset
import com.soneso.stellar.sdk.horizon.responses.toSdkAsset
import kotlinx.serialization.json.Json
import kotlin.test.*

class AssetToSdkAssetTest {

    private val json = Json { ignoreUnknownKeys = true }
    // Valid Stellar test public key
    private val validIssuer = "GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7"

    @Test
    fun testNativeAssetToSdkAsset() {
        val horizonAsset = HorizonAsset("native")
        val sdkAsset = horizonAsset.toSdkAsset()
        assertSame(AssetTypeNative, sdkAsset)
        assertEquals(AssetTypeXdr.ASSET_TYPE_NATIVE, sdkAsset.type)
    }

    @Test
    fun testNativeAssetFromJsonToSdkAsset() {
        val nativeJson = """{"asset_type": "native"}"""
        val horizonAsset = json.decodeFromString<HorizonAsset>(nativeJson)
        val sdkAsset = horizonAsset.toSdkAsset()
        assertSame(AssetTypeNative, sdkAsset)
        assertEquals(AssetTypeXdr.ASSET_TYPE_NATIVE, sdkAsset.type)
    }

    @Test
    fun testCreditAlphanum4ToSdkAsset() {
        val horizonAsset = HorizonAsset("credit_alphanum4", "USD", validIssuer)
        val sdkAsset = horizonAsset.toSdkAsset()
        assertTrue(sdkAsset is AssetTypeCreditAlphaNum4)
        val credit4Asset = sdkAsset as AssetTypeCreditAlphaNum4
        assertEquals("USD", credit4Asset.code)
        assertEquals(validIssuer, credit4Asset.issuer)
    }

    @Test
    fun testCreditAlphanum4FromJsonToSdkAsset() {
        val creditJson = """{"asset_type": "credit_alphanum4", "asset_code": "EUR", "asset_issuer": "$validIssuer"}"""
        val horizonAsset = json.decodeFromString<HorizonAsset>(creditJson)
        val sdkAsset = horizonAsset.toSdkAsset()
        assertTrue(sdkAsset is AssetTypeCreditAlphaNum4)
        val credit4Asset = sdkAsset as AssetTypeCreditAlphaNum4
        assertEquals("EUR", credit4Asset.code)
        assertEquals(validIssuer, credit4Asset.issuer)
    }

    @Test
    fun testCreditAlphanum12ToSdkAsset() {
        val horizonAsset = HorizonAsset("credit_alphanum12", "LONGASSET", validIssuer)
        val sdkAsset = horizonAsset.toSdkAsset()
        assertTrue(sdkAsset is AssetTypeCreditAlphaNum12)
        val credit12Asset = sdkAsset as AssetTypeCreditAlphaNum12
        assertEquals("LONGASSET", credit12Asset.code)
        assertEquals(validIssuer, credit12Asset.issuer)
    }

    @Test
    fun testUnknownAssetTypeThrowsException() {
        val horizonAsset = HorizonAsset("unknown_type", "CODE", validIssuer)
        val exception = assertFailsWith<IllegalArgumentException> {
            horizonAsset.toSdkAsset()
        }
        assertEquals("Unknown asset type: unknown_type", exception.message)
    }

    @Test
    fun testCredit4AssetNullCodeThrowsException() {
        val horizonAsset = HorizonAsset("credit_alphanum4", null, validIssuer)
        assertFailsWith<IllegalStateException> {
            horizonAsset.toSdkAsset()
        }
    }

    @Test
    fun testCredit4AssetNullIssuerThrowsException() {
        val horizonAsset = HorizonAsset("credit_alphanum4", "USD", null)
        assertFailsWith<IllegalStateException> {
            horizonAsset.toSdkAsset()
        }
    }

    @Test
    fun testCredit12AssetNullCodeThrowsException() {
        val horizonAsset = HorizonAsset("credit_alphanum12", null, validIssuer)
        assertFailsWith<IllegalStateException> {
            horizonAsset.toSdkAsset()
        }
    }

    @Test
    fun testCredit12AssetNullIssuerThrowsException() {
        val horizonAsset = HorizonAsset("credit_alphanum12", "LONGASSET", null)
        assertFailsWith<IllegalStateException> {
            horizonAsset.toSdkAsset()
        }
    }

    @Test
    fun testAssetTypeComparison() {
        val nativeSdk = HorizonAsset("native").toSdkAsset()
        val credit4Sdk = HorizonAsset("credit_alphanum4", "USD", validIssuer).toSdkAsset()
        val credit12Sdk = HorizonAsset("credit_alphanum12", "LONGASSET", validIssuer).toSdkAsset()

        assertEquals(AssetTypeXdr.ASSET_TYPE_NATIVE, nativeSdk.type)
        assertEquals(AssetTypeXdr.ASSET_TYPE_CREDIT_ALPHANUM4, credit4Sdk.type)
        assertEquals(AssetTypeXdr.ASSET_TYPE_CREDIT_ALPHANUM12, credit12Sdk.type)

        assertTrue(nativeSdk is AssetTypeNative)
        assertTrue(credit4Sdk is AssetTypeCreditAlphaNum4)
        assertTrue(credit12Sdk is AssetTypeCreditAlphaNum12)
    }

    @Test
    fun testAssetEquality() {
        val sdkAsset1 = HorizonAsset("credit_alphanum4", "USD", validIssuer).toSdkAsset() as AssetTypeCreditAlphaNum4
        val sdkAsset2 = HorizonAsset("credit_alphanum4", "USD", validIssuer).toSdkAsset() as AssetTypeCreditAlphaNum4
        assertEquals(sdkAsset1.code, sdkAsset2.code)
        assertEquals(sdkAsset1.issuer, sdkAsset2.issuer)
        assertEquals(sdkAsset1.type, sdkAsset2.type)
    }

    @Test
    fun testSingleCharacterAssetCode() {
        val sdkAsset = HorizonAsset("credit_alphanum4", "X", validIssuer).toSdkAsset()
        assertTrue(sdkAsset is AssetTypeCreditAlphaNum4)
        assertEquals("X", (sdkAsset as AssetTypeCreditAlphaNum4).code)
    }

    @Test
    fun testFourCharacterAssetCode() {
        val sdkAsset = HorizonAsset("credit_alphanum4", "ABCD", validIssuer).toSdkAsset()
        assertTrue(sdkAsset is AssetTypeCreditAlphaNum4)
        assertEquals("ABCD", (sdkAsset as AssetTypeCreditAlphaNum4).code)
    }

    @Test
    fun testTwelveCharacterAssetCode() {
        val sdkAsset = HorizonAsset("credit_alphanum12", "ABCDEFGHIJKL", validIssuer).toSdkAsset()
        assertTrue(sdkAsset is AssetTypeCreditAlphaNum12)
        assertEquals("ABCDEFGHIJKL", (sdkAsset as AssetTypeCreditAlphaNum12).code)
    }
}
