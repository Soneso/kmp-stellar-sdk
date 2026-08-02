//
//  OZValidationLimitsTest.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.unitTests.smartaccount.oz

import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.StrKey
import com.soneso.stellar.sdk.smartaccount.core.DelegatedSigner
import com.soneso.stellar.sdk.smartaccount.core.ExternalSigner
import com.soneso.stellar.sdk.smartaccount.core.ValidationException
import com.soneso.stellar.sdk.smartaccount.oz.ContextRuleType
import com.soneso.stellar.sdk.smartaccount.oz.OZConstants
import com.soneso.stellar.sdk.smartaccount.oz.OZSmartAccountConfig
import com.soneso.stellar.sdk.smartaccount.oz.OZSmartAccountKit
import com.soneso.stellar.sdk.smartaccount.oz.PolicyInstallParams
import com.soneso.stellar.sdk.smartaccount.oz.requireValidContextRuleName
import com.soneso.stellar.sdk.smartaccount.oz.requireValidSigners
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Client-side validation of the OZ smart-account contract limits for context rule names
 * and external signer key data. These reject oversized inputs before submission instead of
 * surfacing an opaque on-chain failure.
 */
class OZValidationLimitsTest {

    // A real testnet WebAuthn verifier contract address (valid C-address).
    private val verifier = "CB26VN37RCVNTHJZDEPK6IRO2MMTS3Z2IEO5JD5BINY2OOJ5KKJG7NKY"

    // A valid G-address for delegated signers.
    private val account = "GA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJVSGZ"

    // MARK: - Constants

    @Test
    fun testConstants_matchContractLimits() {
        assertEquals(20, OZConstants.MAX_NAME_SIZE)
        assertEquals(256, OZConstants.MAX_EXTERNAL_KEY_SIZE)
    }

    // MARK: - Context rule name

    @Test
    fun testContextRuleName_empty_throws() {
        assertFailsWith<ValidationException.InvalidInput> {
            requireValidContextRuleName("")
        }
    }

    @Test
    fun testContextRuleName_at20Bytes_ok() {
        // 20 ASCII characters == 20 UTF-8 bytes: at the limit, accepted.
        requireValidContextRuleName("a".repeat(20))
    }

    @Test
    fun testContextRuleName_21Bytes_throws() {
        assertFailsWith<ValidationException.InvalidInput> {
            requireValidContextRuleName("a".repeat(21))
        }
    }

    @Test
    fun testContextRuleName_multiByteUtf8_measuredInBytesNotChars() {
        // "ä" is 2 UTF-8 bytes. 10 chars == 20 bytes: at the limit, accepted.
        requireValidContextRuleName("ä".repeat(10))
        // 11 chars == 22 bytes: over the byte limit even though the character count is <= 20.
        assertFailsWith<ValidationException.InvalidInput> {
            requireValidContextRuleName("ä".repeat(11))
        }
    }

    // MARK: - External signer key size

    @Test
    fun testExternalSigner_at256Bytes_ok() {
        val signer = ExternalSigner(verifier, ByteArray(OZConstants.MAX_EXTERNAL_KEY_SIZE) { 0x01 })
        requireValidSigners(listOf(signer))
    }

    @Test
    fun testExternalSigner_257Bytes_throws() {
        val signer = ExternalSigner(verifier, ByteArray(OZConstants.MAX_EXTERNAL_KEY_SIZE + 1) { 0x01 })
        assertFailsWith<ValidationException.InvalidInput> {
            requireValidSigners(listOf(signer))
        }
    }

    @Test
    fun testDelegatedSigner_hasNoKeyData_skipped() {
        // Delegated signers carry no key data and must never trip the external-key check.
        requireValidSigners(listOf(DelegatedSigner(account)))
    }

    @Test
    fun testMixedSigners_oversizedExternal_throws() {
        val delegated = DelegatedSigner(account)
        val oversized = ExternalSigner(verifier, ByteArray(OZConstants.MAX_EXTERNAL_KEY_SIZE + 1) { 0x02 })
        assertFailsWith<ValidationException.InvalidInput> {
            requireValidSigners(listOf(delegated, oversized))
        }
    }

    // MARK: - Wiring through the manager entry points
    //
    // The failure paths below fire during pre-network validation, so a connected kit with no
    // network access exercises the wiring: removing a requireValid* call from the manager
    // would turn these tests red.

    private val contractId = "CAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAD2KM"

    private suspend fun connectedKit(): OZSmartAccountKit {
        val kit = OZSmartAccountKit.create(
            OZSmartAccountConfig(
                rpcUrl = "https://soroban-testnet.stellar.org",
                networkPassphrase = Network.TESTNET.networkPassphrase,
                accountWasmHash = "a" + "0".repeat(63),
                webauthnVerifierAddress = verifier
            )
        )
        kit.setConnectedState("test-credential-id", contractId)
        return kit
    }

    private fun policyAddress(seed: Int): String = StrKey.encodeContract(ByteArray(32) { (it + seed).toByte() })

    @Test
    fun testAddContextRule_nameOverByteLimit_throws() = runTest {
        val kit = connectedKit()
        assertFailsWith<ValidationException.InvalidInput> {
            kit.contextRuleManager.addContextRule(
                contextType = ContextRuleType.Default,
                name = "a".repeat(OZConstants.MAX_NAME_SIZE + 1),
                signers = listOf(DelegatedSigner(account))
            )
        }
    }

    @Test
    fun testAddContextRule_oversizedExternalSigner_throws() = runTest {
        val kit = connectedKit()
        assertFailsWith<ValidationException.InvalidInput> {
            kit.contextRuleManager.addContextRule(
                contextType = ContextRuleType.Default,
                name = "rule",
                signers = listOf(ExternalSigner(verifier, ByteArray(OZConstants.MAX_EXTERNAL_KEY_SIZE + 1) { 0x01 }))
            )
        }
    }

    @Test
    fun testAddContextRule_tooManyPolicies_throws() = runTest {
        val kit = connectedKit()
        val installParam = PolicyInstallParams.SimpleThreshold(threshold = 1u).toScVal()
        val policies = (0 until OZConstants.MAX_POLICIES + 1).associate { policyAddress(it) to installParam }
        assertFailsWith<ValidationException.InvalidInput> {
            kit.contextRuleManager.addContextRule(
                contextType = ContextRuleType.Default,
                name = "rule",
                signers = listOf(DelegatedSigner(account)),
                policies = policies
            )
        }
    }

    @Test
    fun testUpdateName_nameOverByteLimit_throws() = runTest {
        val kit = connectedKit()
        assertFailsWith<ValidationException.InvalidInput> {
            kit.contextRuleManager.updateName(
                id = 0u,
                name = "ä".repeat(11) // 22 UTF-8 bytes with a character count below the limit
            )
        }
    }

    @Test
    fun testAddPasskey_oversizedCredentialId_throws() = runTest {
        val kit = connectedKit()
        val publicKey = ByteArray(65) { if (it == 0) 0x04 else 0x01 }
        // keyData = publicKey (65) + credentialId (192) = 257 bytes, one over the limit.
        val credentialId = ByteArray(OZConstants.MAX_EXTERNAL_KEY_SIZE + 1 - 65) { 0x02 }
        assertFailsWith<ValidationException.InvalidInput> {
            kit.signerManager.addPasskey(
                contextRuleId = 0u,
                publicKey = publicKey,
                credentialId = credentialId
            )
        }
    }
}
