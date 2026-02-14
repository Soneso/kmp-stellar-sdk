package com.soneso.stellar.sdk.unitTests

import com.soneso.stellar.sdk.*
import kotlin.test.*

class AssetTest {

    private val testIssuer = "GDUKMGUGDZQK6YHYA5Z6AY2G4XDSZPSZ3SW5UN3ARVMO6QSRDWP5YLEX"
    private val testIssuer2 = "GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN"

    @Test
    fun testAssetTypeNative() {
        val asset = AssetTypeNative
        assertEquals("native", asset.toString())
        assertEquals(AssetTypeNative, asset)
    }

    @Test
    fun testAssetTypeNativeSingleton() {
        val asset1 = AssetTypeNative
        val asset2 = AssetTypeNative
        assertSame(asset1, asset2)
    }

    @Test
    fun testAssetTypeCreditAlphaNum4() {
        val asset = AssetTypeCreditAlphaNum4("USD", testIssuer)
        assertEquals("USD", asset.code)
        assertEquals(testIssuer, asset.issuer)
        assertEquals("USD:$testIssuer", asset.toString())
    }

    @Test
    fun testAssetTypeCreditAlphaNum4MinLength() {
        val asset = AssetTypeCreditAlphaNum4("A", testIssuer)
        assertEquals("A", asset.code)
    }

    @Test
    fun testAssetTypeCreditAlphaNum4MaxLength() {
        val asset = AssetTypeCreditAlphaNum4("ABCD", testIssuer)
        assertEquals("ABCD", asset.code)
    }

    @Test
    fun testAssetTypeCreditAlphaNum4InvalidLength() {
        assertFailsWith<IllegalArgumentException> {
            AssetTypeCreditAlphaNum4("", testIssuer)
        }
        assertFailsWith<IllegalArgumentException> {
            AssetTypeCreditAlphaNum4("ABCDE", testIssuer)
        }
    }

    @Test
    fun testAssetTypeCreditAlphaNum12() {
        val asset = AssetTypeCreditAlphaNum12("TESTASSET", testIssuer)
        assertEquals("TESTASSET", asset.code)
        assertEquals(testIssuer, asset.issuer)
        assertEquals("TESTASSET:$testIssuer", asset.toString())
    }

    @Test
    fun testAssetTypeCreditAlphaNum12MinLength() {
        val asset = AssetTypeCreditAlphaNum12("ABCDE", testIssuer)
        assertEquals("ABCDE", asset.code)
    }

    @Test
    fun testAssetTypeCreditAlphaNum12MaxLength() {
        val asset = AssetTypeCreditAlphaNum12("ABCDEFGHIJKL", testIssuer)
        assertEquals("ABCDEFGHIJKL", asset.code)
    }

    @Test
    fun testAssetTypeCreditAlphaNum12InvalidLength() {
        assertFailsWith<IllegalArgumentException> {
            AssetTypeCreditAlphaNum12("ABCD", testIssuer)
        }
        assertFailsWith<IllegalArgumentException> {
            AssetTypeCreditAlphaNum12("ABCDEFGHIJKLM", testIssuer)
        }
    }

    @Test
    fun testAssetCodeValidation() {
        // Valid codes with uppercase letters
        assertDoesNotThrow { AssetTypeCreditAlphaNum4("USD", testIssuer) }
        assertDoesNotThrow { AssetTypeCreditAlphaNum4("BTC", testIssuer) }

        // Valid codes with numbers
        assertDoesNotThrow { AssetTypeCreditAlphaNum4("A1B2", testIssuer) }
        assertDoesNotThrow { AssetTypeCreditAlphaNum12("ASSET123", testIssuer) }

        // Invalid codes with lowercase letters
        assertFailsWith<IllegalArgumentException> {
            AssetTypeCreditAlphaNum4("usd", testIssuer)
        }

        // Invalid codes with special characters
        assertFailsWith<IllegalArgumentException> {
            AssetTypeCreditAlphaNum4("US-D", testIssuer)
        }
        assertFailsWith<IllegalArgumentException> {
            AssetTypeCreditAlphaNum4("US_D", testIssuer)
        }
        assertFailsWith<IllegalArgumentException> {
            AssetTypeCreditAlphaNum4("US D", testIssuer)
        }
    }

    @Test
    fun testAssetIssuerValidation() {
        // Valid issuer
        assertDoesNotThrow {
            AssetTypeCreditAlphaNum4("USD", testIssuer)
        }

        // Invalid issuer (not a valid G... address)
        assertFailsWith<IllegalArgumentException> {
            AssetTypeCreditAlphaNum4("USD", "INVALID")
        }

        // Invalid issuer (empty)
        assertFailsWith<IllegalArgumentException> {
            AssetTypeCreditAlphaNum4("USD", "")
        }

        // Invalid issuer (wrong version byte - S for secret seed)
        assertFailsWith<IllegalArgumentException> {
            AssetTypeCreditAlphaNum4("USD", "SBGWSG6BTNCKCOB3DIFBGCVMUPQFYPA2G4O34RMTB343OYPXU5DJDVMN")
        }
    }

    @Test
    fun testCreateNonNativeAsset() {
        // Should create AlphaNum4 for codes 1-4 chars
        val asset1 = Asset.createNonNativeAsset("USD", testIssuer)
        assertTrue(asset1 is AssetTypeCreditAlphaNum4)
        assertEquals("USD", (asset1 as AssetTypeCreditAlphaNum4).code)

        val asset2 = Asset.createNonNativeAsset("A", testIssuer)
        assertTrue(asset2 is AssetTypeCreditAlphaNum4)

        val asset3 = Asset.createNonNativeAsset("ABCD", testIssuer)
        assertTrue(asset3 is AssetTypeCreditAlphaNum4)

        // Should create AlphaNum12 for codes 5-12 chars
        val asset4 = Asset.createNonNativeAsset("TESTASSET", testIssuer)
        assertTrue(asset4 is AssetTypeCreditAlphaNum12)
        assertEquals("TESTASSET", (asset4 as AssetTypeCreditAlphaNum12).code)

        val asset5 = Asset.createNonNativeAsset("ABCDE", testIssuer)
        assertTrue(asset5 is AssetTypeCreditAlphaNum12)

        val asset6 = Asset.createNonNativeAsset("ABCDEFGHIJKL", testIssuer)
        assertTrue(asset6 is AssetTypeCreditAlphaNum12)

        // Should fail for invalid lengths
        assertFailsWith<IllegalArgumentException> {
            Asset.createNonNativeAsset("", testIssuer)
        }
        assertFailsWith<IllegalArgumentException> {
            Asset.createNonNativeAsset("ABCDEFGHIJKLM", testIssuer)
        }
    }

    @Test
    fun testCreateNativeAsset() {
        val asset = Asset.createNativeAsset()
        assertTrue(asset is AssetTypeNative)
        assertSame(AssetTypeNative, asset)
    }

    @Test
    fun testCreateFromString() {
        // Native asset
        val native = Asset.create("native")
        assertTrue(native is AssetTypeNative)

        val nativeUpperCase = Asset.create("NATIVE")
        assertTrue(nativeUpperCase is AssetTypeNative)

        // AlphaNum4 asset
        val usd = Asset.create("USD:$testIssuer")
        assertTrue(usd is AssetTypeCreditAlphaNum4)
        assertEquals("USD", (usd as AssetTypeCreditAlphaNum4).code)
        assertEquals(testIssuer, usd.issuer)

        // AlphaNum12 asset
        val longAsset = Asset.create("TESTASSET:$testIssuer")
        assertTrue(longAsset is AssetTypeCreditAlphaNum12)
        assertEquals("TESTASSET", (longAsset as AssetTypeCreditAlphaNum12).code)
        assertEquals(testIssuer, longAsset.issuer)
    }

    @Test
    fun testCreateFromStringInvalid() {
        // Invalid format - missing colon
        assertFailsWith<IllegalArgumentException> {
            Asset.create("USD$testIssuer")
        }

        // Invalid format - too many parts
        assertFailsWith<IllegalArgumentException> {
            Asset.create("USD:ISSUER:EXTRA")
        }

        // Invalid format - blank
        assertFailsWith<IllegalArgumentException> {
            Asset.create("")
        }

        // Invalid format - only whitespace
        assertFailsWith<IllegalArgumentException> {
            Asset.create("   ")
        }
    }

    @Test
    fun testCreateWithType() {
        // Native
        val native1 = Asset.create("native", null, null)
        assertTrue(native1 is AssetTypeNative)

        val native2 = Asset.create("NATIVE", null, null)
        assertTrue(native2 is AssetTypeNative)

        // Non-native with null type (auto-detect)
        val usd = Asset.create(null, "USD", testIssuer)
        assertTrue(usd is AssetTypeCreditAlphaNum4)

        val longAsset = Asset.create(null, "TESTASSET", testIssuer)
        assertTrue(longAsset is AssetTypeCreditAlphaNum12)

        // Non-native with explicit type
        val usd2 = Asset.create("credit_alphanum4", "USD", testIssuer)
        assertTrue(usd2 is AssetTypeCreditAlphaNum4)
    }

    @Test
    fun testCreateWithTypeInvalid() {
        // Native requires no code/issuer, but we allow null
        val native = Asset.create("native", null, null)
        assertTrue(native is AssetTypeNative)

        // Non-native requires code and issuer
        assertFailsWith<IllegalArgumentException> {
            Asset.create(null, null, null)
        }
        assertFailsWith<IllegalArgumentException> {
            Asset.create(null, "USD", null)
        }
        assertFailsWith<IllegalArgumentException> {
            Asset.create(null, null, testIssuer)
        }
    }

    @Test
    fun testEquality() {
        // Native equality
        assertEquals(AssetTypeNative, AssetTypeNative)

        // AlphaNum4 equality
        val usd1 = AssetTypeCreditAlphaNum4("USD", testIssuer)
        val usd2 = AssetTypeCreditAlphaNum4("USD", testIssuer)
        assertEquals(usd1, usd2)

        // AlphaNum12 equality
        val long1 = AssetTypeCreditAlphaNum12("TESTASSET", testIssuer)
        val long2 = AssetTypeCreditAlphaNum12("TESTASSET", testIssuer)
        assertEquals(long1, long2)

        // Different codes
        val usd = AssetTypeCreditAlphaNum4("USD", testIssuer)
        val eur = AssetTypeCreditAlphaNum4("EUR", testIssuer)
        assertNotEquals<Asset>(usd, eur)

        // Different issuers
        val usd_issuer1 = AssetTypeCreditAlphaNum4("USD", testIssuer)
        val usd_issuer2 = AssetTypeCreditAlphaNum4("USD", testIssuer2)
        assertNotEquals<Asset>(usd_issuer1, usd_issuer2)

        // Different types
        val native: Asset = AssetTypeNative
        val usd3: Asset = AssetTypeCreditAlphaNum4("USD", testIssuer)
        assertNotEquals(native, usd3)
    }

    @Test
    fun testHashCode() {
        // Same assets should have same hash code
        val usd1 = AssetTypeCreditAlphaNum4("USD", testIssuer)
        val usd2 = AssetTypeCreditAlphaNum4("USD", testIssuer)
        assertEquals(usd1.hashCode(), usd2.hashCode())

        val long1 = AssetTypeCreditAlphaNum12("TESTASSET", testIssuer)
        val long2 = AssetTypeCreditAlphaNum12("TESTASSET", testIssuer)
        assertEquals(long1.hashCode(), long2.hashCode())
    }

    @Test
    fun testCompareTo() {
        val native = AssetTypeNative
        val usd = AssetTypeCreditAlphaNum4("USD", testIssuer)
        val eur = AssetTypeCreditAlphaNum4("EUR", testIssuer)
        val longAsset = AssetTypeCreditAlphaNum12("TESTASSET", testIssuer)

        // Native < AlphaNum4 < AlphaNum12
        assertTrue(native < usd)
        assertTrue(native < longAsset)
        assertTrue(usd < longAsset)

        // Same type comparison
        assertTrue(native.compareTo(native) == 0)
        assertTrue(eur < usd) // EUR comes before USD alphabetically

        // Same code, different issuers
        val usd_issuer1 = AssetTypeCreditAlphaNum4("USD", testIssuer)
        val usd_issuer2 = AssetTypeCreditAlphaNum4("USD", testIssuer2)
        // Compare based on issuer (testIssuer starts with 'G', testIssuer2 starts with 'G')
        // Full comparison depends on the actual issuer strings
        assertTrue(usd_issuer1.compareTo(usd_issuer2) != 0)
    }

    @Test
    fun testSorting() {
        val native = AssetTypeNative
        val eur = AssetTypeCreditAlphaNum4("EUR", testIssuer)
        val usd = AssetTypeCreditAlphaNum4("USD", testIssuer)
        val longAsset = AssetTypeCreditAlphaNum12("TESTASSET", testIssuer)

        val unsorted = listOf(longAsset, usd, native, eur)
        val sorted = unsorted.sorted()

        assertEquals(listOf(native, eur, usd, longAsset), sorted)
    }

    @Test
    fun testXdrRoundTripNative() {
        val original = AssetTypeNative
        val xdr = original.toXdr()
        val restored = Asset.fromXdr(xdr)

        assertEquals(original, restored)
        assertTrue(restored is AssetTypeNative)
    }

    @Test
    fun testXdrRoundTripAlphaNum4() {
        val original = AssetTypeCreditAlphaNum4("USD", testIssuer)
        val xdr = original.toXdr()
        val restored = Asset.fromXdr(xdr)

        assertEquals(original, restored)
        assertTrue(restored is AssetTypeCreditAlphaNum4)
        assertEquals("USD", (restored as AssetTypeCreditAlphaNum4).code)
        assertEquals(testIssuer, restored.issuer)
    }

    @Test
    fun testXdrRoundTripAlphaNum12() {
        val original = AssetTypeCreditAlphaNum12("TESTASSET", testIssuer)
        val xdr = original.toXdr()
        val restored = Asset.fromXdr(xdr)

        assertEquals(original, restored)
        assertTrue(restored is AssetTypeCreditAlphaNum12)
        assertEquals("TESTASSET", (restored as AssetTypeCreditAlphaNum12).code)
        assertEquals(testIssuer, restored.issuer)
    }

    @Test
    fun testXdrPadding() {
        // Test that short codes are properly padded in XDR
        val shortCode = AssetTypeCreditAlphaNum4("A", testIssuer)
        val xdr = shortCode.toXdr()
        val restored = Asset.fromXdr(xdr)

        assertEquals(shortCode, restored)
        assertEquals("A", (restored as AssetTypeCreditAlphaNum4).code)
    }

    @Test
    fun testToString() {
        assertEquals("native", AssetTypeNative.toString())
        assertEquals("USD:$testIssuer", AssetTypeCreditAlphaNum4("USD", testIssuer).toString())
        assertEquals(
            "TESTASSET:$testIssuer",
            AssetTypeCreditAlphaNum12("TESTASSET", testIssuer).toString()
        )
    }

    @Test
    fun testAssetWithNumbers() {
        val asset1 = AssetTypeCreditAlphaNum4("A1B2", testIssuer)
        assertEquals("A1B2", asset1.code)

        val asset2 = AssetTypeCreditAlphaNum12("ASSET123", testIssuer)
        assertEquals("ASSET123", asset2.code)

        // XDR round trip
        val xdr1 = asset1.toXdr()
        val restored1 = Asset.fromXdr(xdr1)
        assertEquals(asset1, restored1)

        val xdr2 = asset2.toXdr()
        val restored2 = Asset.fromXdr(xdr2)
        assertEquals(asset2, restored2)
    }

    @Test
    fun testRealWorldAssets() {
        // Common assets from Stellar network
        val xlm = AssetTypeNative
        val usdc = AssetTypeCreditAlphaNum4("USDC", "GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN")
        val yxlm = AssetTypeCreditAlphaNum4("YXLM", "GARDNV3Q7YGT4AKSDF25LT32YSCCW4EV22Y2TV3I2PU2MMXJTEDL5T55")

        assertNotNull(xlm)
        assertNotNull(usdc)
        assertNotNull(yxlm)

        // Test parsing from canonical form
        val usdcParsed = Asset.create("USDC:GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN")
        assertEquals(usdc, usdcParsed)
    }


    private inline fun assertDoesNotThrow(block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            fail("Expected no exception, but got: ${e.message}")
        }
    }
}
