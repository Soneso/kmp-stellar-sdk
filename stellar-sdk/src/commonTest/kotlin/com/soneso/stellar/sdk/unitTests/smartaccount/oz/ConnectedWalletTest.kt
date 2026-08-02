//
//  ConnectedWalletTest.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.unitTests.smartaccount.oz

import com.soneso.stellar.sdk.smartaccount.core.SmartAccountBuilders
import com.soneso.stellar.sdk.smartaccount.oz.ConnectedWallet
import com.soneso.stellar.sdk.smartaccount.oz.InMemoryStorageAdapter
import com.soneso.stellar.sdk.smartaccount.oz.OZConstants
import com.soneso.stellar.sdk.smartaccount.oz.OZSmartAccountConfig
import com.soneso.stellar.sdk.smartaccount.oz.SignAuthEntryOptions
import com.soneso.stellar.sdk.smartaccount.oz.SignAuthEntryResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for [ConnectedWallet], [SignAuthEntryOptions], [SignAuthEntryResult],
 * [InMemoryStorageAdapter] equality/hashCode, [OZSmartAccountConfig.maxContextRuleScanId],
 * and [SmartAccountBuilders] threshold param validation.
 */
class ConnectedWalletTest {

    private val validRpcUrl = "https://soroban-testnet.stellar.org"
    private val validPassphrase = "Test SDF Network ; September 2015"
    private val validWasmHash = "a" + "0".repeat(63)
    private val validVerifier = "CAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAD2KM"
    private val validAccountAddress = "GA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJVSGZ"
    private val validAccountAddress2 = "GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7"

    // ========================================================================
    // ConnectedWallet
    // ========================================================================

    @Test
    fun testConnectedWallet_fieldAccessReturnsConstructedValues() {
        val wallet = ConnectedWallet(
            address = validAccountAddress,
            walletId = "freighter",
            walletName = "Freighter"
        )
        assertEquals(validAccountAddress, wallet.address)
        assertEquals("freighter", wallet.walletId)
        assertEquals("Freighter", wallet.walletName)
    }

    @Test
    fun testConnectedWallet_dataClassEquality() {
        val a = ConnectedWallet(address = validAccountAddress, walletId = "lobstr", walletName = "LOBSTR")
        val b = ConnectedWallet(address = validAccountAddress, walletId = "lobstr", walletName = "LOBSTR")
        assertEquals(a, b, "ConnectedWallet with same fields must be equal")
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun testConnectedWallet_differentAddress_notEqual() {
        val a = ConnectedWallet(address = validAccountAddress, walletId = "freighter", walletName = "Freighter")
        val b = ConnectedWallet(address = validAccountAddress2, walletId = "freighter", walletName = "Freighter")
        assertTrue(a != b)
    }

    @Test
    fun testConnectedWallet_differentWalletId_notEqual() {
        val a = ConnectedWallet(address = validAccountAddress, walletId = "freighter", walletName = "Freighter")
        val b = ConnectedWallet(address = validAccountAddress, walletId = "albedo", walletName = "Freighter")
        assertTrue(a != b)
    }

    @Test
    fun testConnectedWallet_copy_modifiesField() {
        val original = ConnectedWallet(address = validAccountAddress, walletId = "freighter", walletName = "Freighter")
        val modified = original.copy(walletName = "Freighter v3")
        assertEquals("Freighter v3", modified.walletName)
        assertEquals(original.address, modified.address)
        assertEquals(original.walletId, modified.walletId)
    }

    // ========================================================================
    // SignAuthEntryOptions
    // ========================================================================

    @Test
    fun testSignAuthEntryOptions_defaultValuesAreNull() {
        val options = SignAuthEntryOptions()
        assertNull(options.networkPassphrase)
        assertNull(options.address)
    }

    @Test
    fun testSignAuthEntryOptions_fieldAccessReturnsConstructedValues() {
        val options = SignAuthEntryOptions(
            networkPassphrase = validPassphrase,
            address = validAccountAddress
        )
        assertEquals(validPassphrase, options.networkPassphrase)
        assertEquals(validAccountAddress, options.address)
    }

    @Test
    fun testSignAuthEntryOptions_equality() {
        val a = SignAuthEntryOptions(networkPassphrase = validPassphrase, address = validAccountAddress)
        val b = SignAuthEntryOptions(networkPassphrase = validPassphrase, address = validAccountAddress)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun testSignAuthEntryOptions_nullAddress() {
        val options = SignAuthEntryOptions(networkPassphrase = validPassphrase, address = null)
        assertEquals(validPassphrase, options.networkPassphrase)
        assertNull(options.address)
    }

    @Test
    fun testSignAuthEntryOptions_nullNetworkPassphrase() {
        val options = SignAuthEntryOptions(networkPassphrase = null, address = validAccountAddress)
        assertNull(options.networkPassphrase)
        assertEquals(validAccountAddress, options.address)
    }

    // ========================================================================
    // SignAuthEntryResult
    // ========================================================================

    @Test
    fun testSignAuthEntryResult_fieldAccessReturnsConstructedValues() {
        val result = SignAuthEntryResult(
            signedAuthEntry = "base64encodedSignature==",
            signerAddress = validAccountAddress
        )
        assertEquals("base64encodedSignature==", result.signedAuthEntry)
        assertEquals(validAccountAddress, result.signerAddress)
    }

    @Test
    fun testSignAuthEntryResult_signerAddressDefaultsToNull() {
        val result = SignAuthEntryResult(signedAuthEntry = "aGVsbG8=")
        assertNull(result.signerAddress)
    }

    @Test
    fun testSignAuthEntryResult_equality() {
        val a = SignAuthEntryResult(signedAuthEntry = "abc==", signerAddress = validAccountAddress)
        val b = SignAuthEntryResult(signedAuthEntry = "abc==", signerAddress = validAccountAddress)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun testSignAuthEntryResult_differentSignedEntry_notEqual() {
        val a = SignAuthEntryResult(signedAuthEntry = "aaaa==")
        val b = SignAuthEntryResult(signedAuthEntry = "bbbb==")
        assertTrue(a != b)
    }

    @Test
    fun testSignAuthEntryResult_copy_modifiesField() {
        val original = SignAuthEntryResult(signedAuthEntry = "sig==", signerAddress = validAccountAddress)
        val modified = original.copy(signerAddress = validAccountAddress2)
        assertEquals(validAccountAddress2, modified.signerAddress)
        assertEquals(original.signedAuthEntry, modified.signedAuthEntry)
    }

    // ========================================================================
    // InMemoryStorageAdapter equality and hashCode
    // ========================================================================

    @Test
    fun testInMemoryStorageAdapter_twoInstances_areEqual() {
        val a = InMemoryStorageAdapter()
        val b = InMemoryStorageAdapter()
        assertEquals(a, b, "Two fresh InMemoryStorageAdapter instances must be equal")
    }

    @Test
    fun testInMemoryStorageAdapter_sameInstance_equalsItself() {
        val adapter = InMemoryStorageAdapter()
        assertEquals(adapter, adapter, "InMemoryStorageAdapter must equal itself")
    }

    @Test
    fun testInMemoryStorageAdapter_hashCodeIsConsistent() {
        val adapter = InMemoryStorageAdapter()
        val h1 = adapter.hashCode()
        val h2 = adapter.hashCode()
        assertEquals(h1, h2, "InMemoryStorageAdapter hashCode must be consistent across calls")
    }

    @Test
    fun testInMemoryStorageAdapter_twoInstances_haveSameHashCode() {
        val a = InMemoryStorageAdapter()
        val b = InMemoryStorageAdapter()
        assertEquals(a.hashCode(), b.hashCode(), "Equal InMemoryStorageAdapters must have same hashCode")
    }

    @Test
    fun testInMemoryStorageAdapter_notEqualToNonAdapterObject() {
        val adapter = InMemoryStorageAdapter()
        val other: Any = "not an adapter"
        assertTrue(adapter != other, "InMemoryStorageAdapter must not equal a String")
    }

    // ========================================================================
    // OZSmartAccountConfig.maxContextRuleScanId
    // ========================================================================

    @Test
    fun testMaxContextRuleScanId_defaultValueIs50() {
        val config = OZSmartAccountConfig(
            rpcUrl = validRpcUrl,
            networkPassphrase = validPassphrase,
            accountWasmHash = validWasmHash,
            webauthnVerifierAddress = validVerifier
        )
        assertEquals(50u, config.maxContextRuleScanId, "Default maxContextRuleScanId must be 50")
    }

    @Test
    fun testMaxContextRuleScanId_customValuePersists() {
        val config = OZSmartAccountConfig(
            rpcUrl = validRpcUrl,
            networkPassphrase = validPassphrase,
            accountWasmHash = validWasmHash,
            webauthnVerifierAddress = validVerifier,
            maxContextRuleScanId = 200u
        )
        assertEquals(200u, config.maxContextRuleScanId)
    }

    @Test
    fun testMaxContextRuleScanId_zeroValueAllowed() {
        val config = OZSmartAccountConfig(
            rpcUrl = validRpcUrl,
            networkPassphrase = validPassphrase,
            accountWasmHash = validWasmHash,
            webauthnVerifierAddress = validVerifier,
            maxContextRuleScanId = 0u
        )
        assertEquals(0u, config.maxContextRuleScanId)
    }

    @Test
    fun testMaxContextRuleScanId_builderDefaultValue() {
        val config = OZSmartAccountConfig.builder(
            rpcUrl = validRpcUrl,
            networkPassphrase = validPassphrase,
            accountWasmHash = validWasmHash,
            webauthnVerifierAddress = validVerifier
        ).build()
        assertEquals(50u, config.maxContextRuleScanId, "Builder default maxContextRuleScanId must be 50")
    }

    @Test
    fun testMaxContextRuleScanId_builderCustomValue() {
        val config = OZSmartAccountConfig.builder(
            rpcUrl = validRpcUrl,
            networkPassphrase = validPassphrase,
            accountWasmHash = validWasmHash,
            webauthnVerifierAddress = validVerifier
        ).maxContextRuleScanId(200u).build()
        assertEquals(200u, config.maxContextRuleScanId)
    }

    // ========================================================================
    // OZConstants values
    // ========================================================================

    @Test
    fun testOZConstants_maxSignersIs15() {
        assertEquals(15, OZConstants.MAX_SIGNERS)
    }

    @Test
    fun testOZConstants_maxPoliciesIs5() {
        assertEquals(5, OZConstants.MAX_POLICIES)
    }

}
