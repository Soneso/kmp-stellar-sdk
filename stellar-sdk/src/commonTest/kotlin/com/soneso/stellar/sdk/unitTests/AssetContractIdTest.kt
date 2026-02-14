package com.soneso.stellar.sdk.unitTests

import com.soneso.stellar.sdk.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/**
 * Tests for Asset.getContractId() method (Stellar Asset Contract support).
 */
class AssetContractIdTest {

    private val testIssuer = "GDUKMGUGDZQK6YHYA5Z6AY2G4XDSZPSZ3SW5UN3ARVMO6QSRDWP5YLEX"
    private val testIssuer2 = "GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN"

    @Test
    fun testGetContractIdNativeAsset() = runTest {
        // Test native asset contract ID on public network
        val native = AssetTypeNative
        val contractId = native.getContractId(Network.PUBLIC)

        // Contract ID should be a valid C... address
        assertTrue(contractId.startsWith("C"), "Contract ID should start with 'C', got: $contractId")
        assertTrue(StrKey.isValidContract(contractId), "Contract ID should be valid")

        // Native asset should have consistent contract ID
        val contractId2 = native.getContractId(Network.PUBLIC)
        assertEquals(contractId, contractId2, "Native asset should have deterministic contract ID")
    }

    @Test
    fun testGetContractIdIssuedAssetAlphaNum4() = runTest {
        // Test issued asset contract ID with known test vector from Java SDK
        // USDC:GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN on PUBLIC network
        val usdc = AssetTypeCreditAlphaNum4("USDC", "GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN")
        val contractId = usdc.getContractId(Network.PUBLIC)

        // Known contract ID from Java SDK test
        assertEquals(
            "CCW67TSZV3SSS2HXMBQ5JFGCKJNXKZM7UQUWUZPUTHXSTZLEO7SJMI75",
            contractId,
            "Contract ID should match Java SDK test vector"
        )

        // Contract ID should be a valid C... address
        assertTrue(contractId.startsWith("C"), "Contract ID should start with 'C'")
        assertTrue(StrKey.isValidContract(contractId), "Contract ID should be valid")
    }

    @Test
    fun testGetContractIdIssuedAssetAlphaNum12() = runTest {
        // Test issued asset with AlphaNum12 code
        val longAsset = AssetTypeCreditAlphaNum12("TESTASSET", testIssuer)
        val contractId = longAsset.getContractId(Network.PUBLIC)

        // Contract ID should be a valid C... address
        assertTrue(contractId.startsWith("C"), "Contract ID should start with 'C'")
        assertTrue(StrKey.isValidContract(contractId), "Contract ID should be valid")

        // Should be consistent
        val contractId2 = longAsset.getContractId(Network.PUBLIC)
        assertEquals(contractId, contractId2, "Contract ID should be deterministic")
    }

    @Test
    fun testGetContractIdDifferentNetworks() = runTest {
        // Same asset on different networks should have different contract IDs
        val usd = AssetTypeCreditAlphaNum4("USD", testIssuer)

        val publicContractId = usd.getContractId(Network.PUBLIC)
        val testnetContractId = usd.getContractId(Network.TESTNET)
        val futurenetContractId = usd.getContractId(Network.FUTURENET)

        // All should be valid contract addresses
        assertTrue(StrKey.isValidContract(publicContractId))
        assertTrue(StrKey.isValidContract(testnetContractId))
        assertTrue(StrKey.isValidContract(futurenetContractId))

        // All should be different
        assertNotEquals(publicContractId, testnetContractId, "PUBLIC and TESTNET should have different contract IDs")
        assertNotEquals(publicContractId, futurenetContractId, "PUBLIC and FUTURENET should have different contract IDs")
        assertNotEquals(testnetContractId, futurenetContractId, "TESTNET and FUTURENET should have different contract IDs")
    }

    @Test
    fun testGetContractIdDifferentAssets() = runTest {
        // Different assets should have different contract IDs
        val usd = AssetTypeCreditAlphaNum4("USD", testIssuer)
        val eur = AssetTypeCreditAlphaNum4("EUR", testIssuer)
        val usd2 = AssetTypeCreditAlphaNum4("USD", testIssuer2)

        val usdContractId = usd.getContractId(Network.PUBLIC)
        val eurContractId = eur.getContractId(Network.PUBLIC)
        val usd2ContractId = usd2.getContractId(Network.PUBLIC)

        // All should be valid
        assertTrue(StrKey.isValidContract(usdContractId))
        assertTrue(StrKey.isValidContract(eurContractId))
        assertTrue(StrKey.isValidContract(usd2ContractId))

        // All should be different
        assertNotEquals(usdContractId, eurContractId, "Different codes should have different contract IDs")
        assertNotEquals(usdContractId, usd2ContractId, "Different issuers should have different contract IDs")
        assertNotEquals(eurContractId, usd2ContractId)
    }

    @Test
    fun testGetContractIdNativeVsIssued() = runTest {
        // Native and issued assets should have different contract IDs
        val native = AssetTypeNative
        val usd = AssetTypeCreditAlphaNum4("USD", testIssuer)

        val nativeContractId = native.getContractId(Network.PUBLIC)
        val usdContractId = usd.getContractId(Network.PUBLIC)

        assertNotEquals(nativeContractId, usdContractId, "Native and issued assets should have different contract IDs")
    }

    @Test
    fun testGetContractIdCustomNetwork() = runTest {
        // Test with custom network passphrase
        val customNetwork = Network("Custom Test Network ; 2025")
        val usd = AssetTypeCreditAlphaNum4("USD", testIssuer)

        val contractId = usd.getContractId(customNetwork)

        // Should be valid
        assertTrue(StrKey.isValidContract(contractId), "Custom network contract ID should be valid")

        // Should be different from public network
        val publicContractId = usd.getContractId(Network.PUBLIC)
        assertNotEquals(contractId, publicContractId, "Custom network should have different contract ID than PUBLIC")
    }

    @Test
    fun testGetContractIdRoundTrip() = runTest {
        // Test that contract IDs can be decoded and are 32 bytes
        val usd = AssetTypeCreditAlphaNum4("USD", testIssuer)
        val contractId = usd.getContractId(Network.PUBLIC)

        // Decode contract ID
        val decoded = StrKey.decodeContract(contractId)

        // Should be 32 bytes (SHA-256 hash)
        assertEquals(32, decoded.size, "Contract ID should be 32 bytes")

        // Re-encode should give same result
        val reEncoded = StrKey.encodeContract(decoded)
        assertEquals(contractId, reEncoded, "Re-encoded contract ID should match original")
    }

    @Test
    fun testGetContractIdTestnetNetwork() = runTest {
        // Test with TESTNET to ensure it produces valid contract IDs
        val asset = AssetTypeCreditAlphaNum4("TEST", testIssuer)
        val contractId = asset.getContractId(Network.TESTNET)

        assertTrue(StrKey.isValidContract(contractId))
        assertTrue(contractId.startsWith("C"))
    }

    @Test
    fun testGetContractIdWithShortCode() = runTest {
        // Test with single-character asset code
        val asset = AssetTypeCreditAlphaNum4("X", testIssuer)
        val contractId = asset.getContractId(Network.PUBLIC)

        assertTrue(StrKey.isValidContract(contractId))
        assertTrue(contractId.startsWith("C"))
    }

    @Test
    fun testGetContractIdWithLongCode() = runTest {
        // Test with maximum-length AlphaNum12 code
        val asset = AssetTypeCreditAlphaNum12("ABCDEFGHIJKL", testIssuer)
        val contractId = asset.getContractId(Network.PUBLIC)

        assertTrue(StrKey.isValidContract(contractId))
        assertTrue(contractId.startsWith("C"))
    }
}
