package com.soneso.stellar.sdk.unitTests.horizon.responses

import com.soneso.stellar.sdk.horizon.responses.Asset
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HorizonAssetResponseTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun testNativeAssetDeserialization() {
        val nativeJson = """{"asset_type": "native"}"""
        val asset = json.decodeFromString<Asset>(nativeJson)
        assertEquals("native", asset.assetType)
        assertNull(asset.assetCode)
        assertNull(asset.assetIssuer)
        assertTrue(asset.isNative())
    }

    @Test
    fun testCreditAlphanum4Deserialization() {
        val creditJson = """{"asset_type": "credit_alphanum4", "asset_code": "USD", "asset_issuer": "GTEST"}"""
        val asset = json.decodeFromString<Asset>(creditJson)
        assertEquals("credit_alphanum4", asset.assetType)
        assertEquals("USD", asset.assetCode)
        assertEquals("GTEST", asset.assetIssuer)
        assertFalse(asset.isNative())
    }

    @Test
    fun testCreditAlphanum12Deserialization() {
        val creditJson = """{"asset_type": "credit_alphanum12", "asset_code": "LONGASSET", "asset_issuer": "GTEST"}"""
        val asset = json.decodeFromString<Asset>(creditJson)
        assertEquals("credit_alphanum12", asset.assetType)
        assertEquals("LONGASSET", asset.assetCode)
        assertFalse(asset.isNative())
    }

    @Test
    fun testToCanonicalFormNative() {
        val asset = Asset("native")
        assertEquals("native", asset.toCanonicalForm())
    }

    @Test
    fun testToCanonicalFormCredit() {
        val asset = Asset("credit_alphanum4", "USD", "GTEST")
        assertEquals("USD:GTEST", asset.toCanonicalForm())
    }

    @Test
    fun testToCanonicalFormInvalid() {
        val asset = Asset("credit_alphanum4", null, null)
        assertFailsWith<IllegalStateException> {
            asset.toCanonicalForm()
        }
    }

    @Test
    fun testFromCanonicalFormNative() {
        val asset = Asset.fromCanonicalForm("native")
        assertEquals("native", asset.assetType)
        assertTrue(asset.isNative())
    }

    @Test
    fun testFromCanonicalFormCredit4() {
        val asset = Asset.fromCanonicalForm("USD:GTEST")
        assertEquals("credit_alphanum4", asset.assetType)
        assertEquals("USD", asset.assetCode)
        assertEquals("GTEST", asset.assetIssuer)
    }

    @Test
    fun testFromCanonicalFormCredit12() {
        val asset = Asset.fromCanonicalForm("LONGASSET:GTEST")
        assertEquals("credit_alphanum12", asset.assetType)
        assertEquals("LONGASSET", asset.assetCode)
        assertEquals("GTEST", asset.assetIssuer)
    }

    @Test
    fun testFromCanonicalFormInvalid() {
        assertFailsWith<IllegalArgumentException> {
            Asset.fromCanonicalForm("invalid")
        }
    }

    @Test
    fun testFromCanonicalFormInvalidTooManyColons() {
        assertFailsWith<IllegalArgumentException> {
            Asset.fromCanonicalForm("a:b:c")
        }
    }

    @Test
    fun testFromCanonicalFormInvalidCodeLength() {
        assertFailsWith<IllegalArgumentException> {
            Asset.fromCanonicalForm("TOOLONGASSETCODE:GTEST")
        }
    }

    @Test
    fun testNativeFactory() {
        val asset = Asset.native()
        assertEquals("native", asset.assetType)
        assertNull(asset.assetCode)
        assertNull(asset.assetIssuer)
        assertTrue(asset.isNative())
    }

    @Test
    fun testCreateFactory() {
        val asset = Asset.create("USD", "GTEST")
        assertEquals("credit_alphanum4", asset.assetType)
        assertEquals("USD", asset.assetCode)
        assertEquals("GTEST", asset.assetIssuer)
    }

    @Test
    fun testCreateFactoryAlphanum12() {
        val asset = Asset.create("LONGASSET", "GTEST")
        assertEquals("credit_alphanum12", asset.assetType)
    }

    @Test
    fun testCreateFactoryInvalidCodeLength() {
        assertFailsWith<IllegalArgumentException> {
            Asset.create("TOOLONGASSETCODE", "GTEST")
        }
    }

    @Test
    fun testDataClassEquality() {
        val a1 = Asset("native")
        val a2 = Asset("native")
        assertEquals(a1, a2)
        assertEquals(a1.hashCode(), a2.hashCode())
    }
}
