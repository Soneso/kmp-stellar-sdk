//
//  ManagerSelectedSignersTest.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.unitTests.smartaccount

import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.scval.Scv
import com.soneso.stellar.sdk.smartaccount.core.DelegatedSigner
import com.soneso.stellar.sdk.smartaccount.core.SubmissionMethod
import com.soneso.stellar.sdk.smartaccount.core.ValidationException
import com.soneso.stellar.sdk.smartaccount.core.WalletException
import com.soneso.stellar.sdk.smartaccount.oz.ContextRuleType
import com.soneso.stellar.sdk.smartaccount.oz.OZSmartAccountConfig
import com.soneso.stellar.sdk.smartaccount.oz.OZSmartAccountKit
import com.soneso.stellar.sdk.smartaccount.oz.SelectedSigner
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * Unit tests verifying that the `selectedSigners` and `forceMethod` parameters on
 * [OZSignerManager], [OZPolicyManager], and [OZContextRuleManager] methods are
 * correctly accepted and routed.
 *
 * These tests exercise input validation paths that do not require a live Soroban
 * server. Network-dependent behavior (actual multi-signer signing) is covered by
 * integration tests.
 *
 * Each method is tested in two scenarios:
 * - Not connected: throws [WalletException.NotConnected] regardless of selectedSigners
 * - Connected with invalid input: throws the appropriate validation error, confirming
 *   the method accepted the selectedSigners parameter and reached the next validation
 */
class ManagerSelectedSignersTest {

    // ========================================================================
    // Fixtures
    // ========================================================================

    private val validContractId = "CAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAD2KM"
    private val validAccountAddress = "GA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJVSGZ"

    private fun buildConfig(): OZSmartAccountConfig = OZSmartAccountConfig(
        rpcUrl = "https://soroban-testnet.stellar.org",
        networkPassphrase = Network.TESTNET.networkPassphrase,
        accountWasmHash = "a" + "0".repeat(63),
        webauthnVerifierAddress = "CAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAD2KM"
    )

    private suspend fun createConnectedKit(): OZSmartAccountKit {
        val kit = OZSmartAccountKit.create(buildConfig())
        kit.setConnectedState("test-credential-id", validContractId)
        return kit
    }

    private fun passkeySignerStub() = SelectedSigner.Passkey(
        credentialId = null,
        credentialIdBytes = null,
        keyData = null
    )

    private fun walletSignerStub() = SelectedSigner.Wallet(address = validAccountAddress)

    private val multiSigners = listOf(passkeySignerStub(), walletSignerStub())

    // ========================================================================
    // OZSignerManager.addDelegated — selectedSigners
    // ========================================================================

    @Test
    fun testAddDelegated_notConnected_withSelectedSigners_throwsNotConnected() = runTest {
        val kit = OZSmartAccountKit.create(buildConfig())
        assertFailsWith<WalletException.NotConnected> {
            kit.signerManager.addDelegated(
                contextRuleId = 0u,
                address = validAccountAddress,
                selectedSigners = multiSigners
            )
        }
    }

    @Test
    fun testAddDelegated_connected_withSelectedSigners_reachesAddressValidation() = runTest {
        val kit = createConnectedKit()
        // Invalid address should trigger validation after the not-connected check passes.
        assertFailsWith<ValidationException.InvalidAddress> {
            kit.signerManager.addDelegated(
                contextRuleId = 0u,
                address = "INVALID",
                selectedSigners = multiSigners
            )
        }
    }

    // ========================================================================
    // OZSignerManager.addEd25519 — selectedSigners
    // ========================================================================

    @Test
    fun testAddEd25519_notConnected_withSelectedSigners_throwsNotConnected() = runTest {
        val kit = OZSmartAccountKit.create(buildConfig())
        assertFailsWith<WalletException.NotConnected> {
            kit.signerManager.addEd25519(
                contextRuleId = 0u,
                verifierAddress = validContractId,
                publicKey = ByteArray(32),
                selectedSigners = multiSigners
            )
        }
    }

    @Test
    fun testAddEd25519_connected_withSelectedSigners_reachesKeyValidation() = runTest {
        val kit = createConnectedKit()
        // Wrong key size should trigger validation after the not-connected check passes.
        assertFailsWith<ValidationException.InvalidInput> {
            kit.signerManager.addEd25519(
                contextRuleId = 0u,
                verifierAddress = validContractId,
                publicKey = ByteArray(10), // Invalid size
                selectedSigners = multiSigners
            )
        }
    }

    // ========================================================================
    // OZSignerManager.addPasskey — selectedSigners
    // ========================================================================

    @Test
    fun testAddPasskey_notConnected_withSelectedSigners_throwsNotConnected() = runTest {
        val kit = OZSmartAccountKit.create(buildConfig())
        assertFailsWith<WalletException.NotConnected> {
            kit.signerManager.addPasskey(
                contextRuleId = 0u,
                publicKey = ByteArray(65),
                credentialId = ByteArray(16),
                selectedSigners = multiSigners
            )
        }
    }

    @Test
    fun testAddPasskey_connected_withSelectedSigners_reachesKeyValidation() = runTest {
        val kit = createConnectedKit()
        // Wrong public key size should trigger validation.
        assertFailsWith<ValidationException.InvalidInput> {
            kit.signerManager.addPasskey(
                contextRuleId = 0u,
                publicKey = ByteArray(10), // Invalid size (should be 65)
                credentialId = ByteArray(16),
                selectedSigners = multiSigners
            )
        }
    }

    // ========================================================================
    // OZSignerManager.removeSigner — selectedSigners
    // ========================================================================

    @Test
    fun testRemoveSigner_notConnected_withSelectedSigners_throwsNotConnected() = runTest {
        val kit = OZSmartAccountKit.create(buildConfig())
        assertFailsWith<WalletException.NotConnected> {
            kit.signerManager.removeSigner(
                contextRuleId = 0u,
                signerId = 1u,
                selectedSigners = multiSigners
            )
        }
    }

    @Test
    fun testRemoveSignerBySigner_notConnected_throwsNotConnected() = runTest {
        val kit = OZSmartAccountKit.create(buildConfig())
        assertFailsWith<WalletException.NotConnected> {
            kit.signerManager.removeSigner(
                contextRuleId = 0u,
                signer = DelegatedSigner(address = "GA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJVSGZ"),
                selectedSigners = multiSigners
            )
        }
    }

    // ========================================================================
    // OZPolicyManager.addPolicy — selectedSigners
    // ========================================================================

    @Test
    fun testAddPolicy_notConnected_withSelectedSigners_throwsNotConnected() = runTest {
        val kit = OZSmartAccountKit.create(buildConfig())
        assertFailsWith<WalletException.NotConnected> {
            kit.policyManager.addPolicy(
                contextRuleId = 0u,
                policyAddress = validContractId,
                installParams = Scv.toVoid(),
                selectedSigners = multiSigners
            )
        }
    }

    @Test
    fun testAddPolicy_connected_withSelectedSigners_reachesAddressValidation() = runTest {
        val kit = createConnectedKit()
        assertFailsWith<ValidationException.InvalidAddress> {
            kit.policyManager.addPolicy(
                contextRuleId = 0u,
                policyAddress = "INVALID",
                installParams = Scv.toVoid(),
                selectedSigners = multiSigners
            )
        }
    }

    // ========================================================================
    // OZPolicyManager.removePolicy — selectedSigners
    // ========================================================================

    @Test
    fun testRemovePolicy_notConnected_withSelectedSigners_throwsNotConnected() = runTest {
        val kit = OZSmartAccountKit.create(buildConfig())
        assertFailsWith<WalletException.NotConnected> {
            kit.policyManager.removePolicy(
                contextRuleId = 0u,
                policyId = 1u,
                selectedSigners = multiSigners
            )
        }
    }

    @Test
    fun testRemovePolicyByAddress_notConnected_throwsNotConnected() = runTest {
        val kit = OZSmartAccountKit.create(buildConfig())
        assertFailsWith<WalletException.NotConnected> {
            kit.policyManager.removePolicy(
                contextRuleId = 0u,
                policyAddress = "CAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAD2KM",
                selectedSigners = multiSigners
            )
        }
    }

    // ========================================================================
    // OZContextRuleManager.updateName — selectedSigners
    // ========================================================================

    @Test
    fun testUpdateName_notConnected_withSelectedSigners_throwsNotConnected() = runTest {
        val kit = OZSmartAccountKit.create(buildConfig())
        assertFailsWith<WalletException.NotConnected> {
            kit.contextRuleManager.updateName(
                id = 0u,
                name = "New Name",
                selectedSigners = multiSigners
            )
        }
    }

    @Test
    fun testUpdateName_connected_withSelectedSigners_reachesInputValidation() = runTest {
        val kit = createConnectedKit()
        assertFailsWith<ValidationException.InvalidInput> {
            kit.contextRuleManager.updateName(
                id = 0u,
                name = "", // Empty name is invalid
                selectedSigners = multiSigners
            )
        }
    }

    // ========================================================================
    // OZContextRuleManager.updateValidUntil — selectedSigners
    // ========================================================================

    @Test
    fun testUpdateValidUntil_notConnected_withSelectedSigners_throwsNotConnected() = runTest {
        val kit = OZSmartAccountKit.create(buildConfig())
        assertFailsWith<WalletException.NotConnected> {
            kit.contextRuleManager.updateValidUntil(
                id = 0u,
                validUntil = 100u,
                selectedSigners = multiSigners
            )
        }
    }

    // ========================================================================
    // OZSignerManager.addNewPasskeySigner — selectedSigners
    // ========================================================================

    @Test
    fun testAddNewPasskeySigner_notConnected_withSelectedSigners_throwsNotConnected() = runTest {
        val kit = OZSmartAccountKit.create(buildConfig())
        assertFailsWith<WalletException.NotConnected> {
            kit.signerManager.addNewPasskeySigner(
                contextRuleId = 0u,
                userName = "test",
                selectedSigners = multiSigners
            )
        }
    }

    // ========================================================================
    // Default parameter value — methods callable without selectedSigners
    // ========================================================================

    @Test
    fun testAddDelegated_defaultSelectedSigners_isEmptyList() = runTest {
        val kit = OZSmartAccountKit.create(buildConfig())
        // Calling without selectedSigners should compile and use the default empty list.
        // It will throw NotConnected before reaching the signers parameter logic.
        assertFailsWith<WalletException.NotConnected> {
            kit.signerManager.addDelegated(
                contextRuleId = 0u,
                address = validAccountAddress
            )
        }
    }

    @Test
    fun testRemoveSigner_defaultSelectedSigners_isEmptyList() = runTest {
        val kit = OZSmartAccountKit.create(buildConfig())
        assertFailsWith<WalletException.NotConnected> {
            kit.signerManager.removeSigner(
                contextRuleId = 0u,
                signerId = 1u
            )
        }
    }

    @Test
    fun testAddPolicy_defaultSelectedSigners_isEmptyList() = runTest {
        val kit = OZSmartAccountKit.create(buildConfig())
        assertFailsWith<WalletException.NotConnected> {
            kit.policyManager.addPolicy(
                contextRuleId = 0u,
                policyAddress = validContractId,
                installParams = Scv.toVoid()
            )
        }
    }

    @Test
    fun testRemovePolicy_defaultSelectedSigners_isEmptyList() = runTest {
        val kit = OZSmartAccountKit.create(buildConfig())
        assertFailsWith<WalletException.NotConnected> {
            kit.policyManager.removePolicy(
                contextRuleId = 0u,
                policyId = 1u
            )
        }
    }

    @Test
    fun testUpdateName_defaultSelectedSigners_isEmptyList() = runTest {
        val kit = OZSmartAccountKit.create(buildConfig())
        assertFailsWith<WalletException.NotConnected> {
            kit.contextRuleManager.updateName(
                id = 0u,
                name = "Test"
            )
        }
    }

    @Test
    fun testUpdateValidUntil_defaultSelectedSigners_isEmptyList() = runTest {
        val kit = OZSmartAccountKit.create(buildConfig())
        assertFailsWith<WalletException.NotConnected> {
            kit.contextRuleManager.updateValidUntil(
                id = 0u,
                validUntil = 100u
            )
        }
    }

    // ========================================================================
    // OZContextRuleManager.addContextRule — selectedSigners
    // ========================================================================

    @Test
    fun testAddContextRule_withSelectedSigners_notConnected() = runTest {
        val kit = OZSmartAccountKit.create(buildConfig())
        assertFailsWith<WalletException.NotConnected> {
            kit.contextRuleManager.addContextRule(
                contextType = ContextRuleType.Default,
                name = "TestRule",
                signers = emptyList(),
                selectedSigners = multiSigners
            )
        }
    }

    @Test
    fun testAddContextRule_defaultSelectedSigners() = runTest {
        val kit = OZSmartAccountKit.create(buildConfig())
        // Calling without selectedSigners should compile and use the default empty list.
        assertFailsWith<WalletException.NotConnected> {
            kit.contextRuleManager.addContextRule(
                contextType = ContextRuleType.Default,
                name = "TestRule",
                signers = emptyList()
            )
        }
    }

    // ========================================================================
    // OZContextRuleManager.removeContextRule — selectedSigners
    // ========================================================================

    @Test
    fun testRemoveContextRule_withSelectedSigners_notConnected() = runTest {
        val kit = OZSmartAccountKit.create(buildConfig())
        assertFailsWith<WalletException.NotConnected> {
            kit.contextRuleManager.removeContextRule(
                id = 0u,
                selectedSigners = multiSigners
            )
        }
    }

    @Test
    fun testRemoveContextRule_defaultSelectedSigners() = runTest {
        val kit = OZSmartAccountKit.create(buildConfig())
        // Calling without selectedSigners should compile and use the default empty list.
        assertFailsWith<WalletException.NotConnected> {
            kit.contextRuleManager.removeContextRule(
                id = 0u
            )
        }
    }

    // ========================================================================
    // forceMethod parameter — accepted by manager methods
    // ========================================================================

    @Test
    fun testAddDelegated_withForceMethod_notConnected() = runTest {
        val kit = OZSmartAccountKit.create(buildConfig())
        assertFailsWith<WalletException.NotConnected> {
            kit.signerManager.addDelegated(
                contextRuleId = 0u,
                address = validAccountAddress,
                forceMethod = SubmissionMethod.RPC
            )
        }
    }

    @Test
    fun testRemovePolicy_withForceMethod_notConnected() = runTest {
        val kit = OZSmartAccountKit.create(buildConfig())
        assertFailsWith<WalletException.NotConnected> {
            kit.policyManager.removePolicy(
                contextRuleId = 0u,
                policyId = 1u,
                forceMethod = SubmissionMethod.RPC
            )
        }
    }

    @Test
    fun testUpdateName_defaultForceMethod() = runTest {
        val kit = OZSmartAccountKit.create(buildConfig())
        // Calling without forceMethod should compile and use the default null.
        assertFailsWith<WalletException.NotConnected> {
            kit.contextRuleManager.updateName(
                id = 0u,
                name = "Test"
            )
        }
    }

    @Test
    fun testAddContextRule_withForceMethod_notConnected() = runTest {
        val kit = OZSmartAccountKit.create(buildConfig())
        assertFailsWith<WalletException.NotConnected> {
            kit.contextRuleManager.addContextRule(
                contextType = ContextRuleType.Default,
                name = "TestRule",
                signers = emptyList(),
                forceMethod = SubmissionMethod.RELAYER
            )
        }
    }

    @Test
    fun testRemoveContextRule_withForceMethod_notConnected() = runTest {
        val kit = OZSmartAccountKit.create(buildConfig())
        assertFailsWith<WalletException.NotConnected> {
            kit.contextRuleManager.removeContextRule(
                id = 0u,
                forceMethod = SubmissionMethod.RPC
            )
        }
    }

    @Test
    fun testRemoveSigner_withForceMethod_notConnected() = runTest {
        val kit = OZSmartAccountKit.create(buildConfig())
        assertFailsWith<WalletException.NotConnected> {
            kit.signerManager.removeSigner(
                contextRuleId = 0u,
                signerId = 1u,
                forceMethod = SubmissionMethod.RPC
            )
        }
    }

    @Test
    fun testUpdateValidUntil_withForceMethod_notConnected() = runTest {
        val kit = OZSmartAccountKit.create(buildConfig())
        assertFailsWith<WalletException.NotConnected> {
            kit.contextRuleManager.updateValidUntil(
                id = 0u,
                validUntil = 100u,
                forceMethod = SubmissionMethod.RELAYER
            )
        }
    }

    @Test
    fun testAddSpendingLimit_withForceMethod_notConnected() = runTest {
        val kit = OZSmartAccountKit.create(buildConfig())
        assertFailsWith<WalletException.NotConnected> {
            kit.policyManager.addSpendingLimit(
                contextRuleId = 0u,
                policyAddress = validContractId,
                spendingLimit = "100",
                periodLedgers = 17280u,
                forceMethod = SubmissionMethod.RPC
            )
        }
    }
}
