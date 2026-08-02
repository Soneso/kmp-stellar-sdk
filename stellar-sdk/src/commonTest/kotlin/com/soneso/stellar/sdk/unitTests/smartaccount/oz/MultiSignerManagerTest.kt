//
//  MultiSignerManagerTest.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.unitTests.smartaccount.oz

import com.soneso.stellar.sdk.unitTests.smartaccount.buildConfig
import com.soneso.stellar.sdk.smartaccount.core.*
import com.soneso.stellar.sdk.smartaccount.oz.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for [OZMultiSignerManager].
 *
 * Tests cover input validation for [OZMultiSignerManager.multiSignerExecuteAndSubmit] and
 * [OZMultiSignerManager.multiSignerTransfer] that can be exercised without a live Soroban
 * server. The full signing pipeline (simulation, auth-entry signing, submission) requires
 * testnet connectivity and is covered by the integration test suite.
 *
 * Validation order in both methods (reflected in test structure):
 * 1. [OZSmartAccountKit.requireConnected] — throws [WalletException.NotConnected] when no wallet is connected.
 * 2. [requireContractAddress] / [requireStellarAddress] — address format validation.
 * 3. Blank function name check (execute-and-submit only).
 * 4. Empty signers check.
 *
 * Because [requireConnected] runs first, tests that target later validations must first
 * simulate a connected state via [OZSmartAccountKit.setConnectedState].
 */
class MultiSignerManagerTest {

    // ========================================================================
    // Test Fixtures
    // ========================================================================

    /** A well-formed Stellar contract address used as a dummy smart-account contractId. */
    private val validContractId = "CAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAD2KM"

    /** A second well-formed contract address used as a target for execute calls. */
    private val validTargetContract = "CDLZFC3SYJYDZT7K67VZ75HPJVIEUVNIXF47ZG2FB2RMQQVU2HHGCYSC"

    /** A well-formed Stellar G-address used as a recipient / delegated signer. */
    private val validAccountAddress = "GA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJVSGZ"

    /** Creates a kit and connects it in-memory so later validations can be reached. */
    private suspend fun createConnectedKit(): OZSmartAccountKit {
        val kit = OZSmartAccountKit.create(buildConfig())
        kit.setConnectedState("test-credential-id", validContractId)
        return kit
    }

    /** Minimal passkey signer with no credential data (sufficient for list-length validation). */
    private fun passkeySignerStub() = SelectedSigner.Passkey(
        credentialId = null,
        credentialIdBytes = null,
        keyData = null
    )

    // ========================================================================
    // multiSignerExecuteAndSubmit — not-connected guard
    // ========================================================================

    @Test
    fun testMultiSignerExecuteAndSubmit_notConnected_throwsWalletNotConnected() = runTest {
        val kit = OZSmartAccountKit.create(buildConfig())
        val manager = kit.multiSignerManager

        assertFailsWith<WalletException.NotConnected> {
            manager.multiSignerExecuteAndSubmit(
                target = validTargetContract,
                targetFn = "vote",
                selectedSigners = listOf(passkeySignerStub())
            )
        }
    }

    // ========================================================================
    // multiSignerExecuteAndSubmit — target address validation
    // ========================================================================

    @Test
    fun testMultiSignerExecuteAndSubmit_targetIsGAddress_throwsInvalidAddress() = runTest {
        val kit = createConnectedKit()
        val manager = kit.multiSignerManager

        assertFailsWith<ValidationException.InvalidAddress> {
            manager.multiSignerExecuteAndSubmit(
                target = validAccountAddress,   // G-address, not a contract
                targetFn = "vote",
                selectedSigners = listOf(passkeySignerStub())
            )
        }
    }

    @Test
    fun testMultiSignerExecuteAndSubmit_targetTooShort_throwsInvalidAddress() = runTest {
        val kit = createConnectedKit()
        val manager = kit.multiSignerManager

        assertFailsWith<ValidationException.InvalidAddress> {
            manager.multiSignerExecuteAndSubmit(
                target = "CABC",                // valid prefix but far too short
                targetFn = "vote",
                selectedSigners = listOf(passkeySignerStub())
            )
        }
    }

    @Test
    fun testMultiSignerExecuteAndSubmit_targetIsBlank_throwsInvalidAddress() = runTest {
        val kit = createConnectedKit()
        val manager = kit.multiSignerManager

        assertFailsWith<ValidationException.InvalidAddress> {
            manager.multiSignerExecuteAndSubmit(
                target = "",
                targetFn = "vote",
                selectedSigners = listOf(passkeySignerStub())
            )
        }
    }

    // ========================================================================
    // multiSignerExecuteAndSubmit — function name validation
    // ========================================================================

    @Test
    fun testMultiSignerExecuteAndSubmit_targetFnIsBlank_throwsInvalidInput() = runTest {
        val kit = createConnectedKit()
        val manager = kit.multiSignerManager

        val ex = assertFailsWith<ValidationException.InvalidInput> {
            manager.multiSignerExecuteAndSubmit(
                target = validTargetContract,
                targetFn = "",
                selectedSigners = listOf(passkeySignerStub())
            )
        }
        assertTrue(
            ex.message.contains("Function name"),
            "Exception message must reference the 'Function name' field, got: ${ex.message}"
        )
    }

    @Test
    fun testMultiSignerExecuteAndSubmit_targetFnIsWhitespaceOnly_throwsInvalidInput() = runTest {
        val kit = createConnectedKit()
        val manager = kit.multiSignerManager

        assertFailsWith<ValidationException.InvalidInput> {
            manager.multiSignerExecuteAndSubmit(
                target = validTargetContract,
                targetFn = "   ",
                selectedSigners = listOf(passkeySignerStub())
            )
        }
    }

    // ========================================================================
    // multiSignerExecuteAndSubmit — selectedSigners validation
    // ========================================================================

    @Test
    fun testMultiSignerExecuteAndSubmit_emptySigners_throwsInvalidInput() = runTest {
        val kit = createConnectedKit()
        val manager = kit.multiSignerManager

        val ex = assertFailsWith<ValidationException.InvalidInput> {
            manager.multiSignerExecuteAndSubmit(
                target = validTargetContract,
                targetFn = "vote",
                selectedSigners = emptyList()
            )
        }
        assertTrue(
            ex.message.contains("signer"),
            "Exception message must reference signers, got: ${ex.message}"
        )
    }

    // ========================================================================
    // multiSignerTransfer — not-connected guard
    // ========================================================================

    @Test
    fun testMultiSignerTransfer_notConnected_throwsWalletNotConnected() = runTest {
        val kit = OZSmartAccountKit.create(buildConfig())
        val manager = kit.multiSignerManager

        assertFailsWith<WalletException.NotConnected> {
            manager.multiSignerTransfer(
                tokenContract = validTargetContract,
                recipient = validAccountAddress,
                amount = "10",
                selectedSigners = listOf(passkeySignerStub())
            )
        }
    }

    // ========================================================================
    // multiSignerTransfer — tokenContract validation
    // ========================================================================

    @Test
    fun testMultiSignerTransfer_tokenContractIsGAddress_throwsInvalidAddress() = runTest {
        val kit = createConnectedKit()
        val manager = kit.multiSignerManager

        assertFailsWith<ValidationException.InvalidAddress> {
            manager.multiSignerTransfer(
                tokenContract = validAccountAddress,   // G-address, not a contract
                recipient = validAccountAddress,
                amount = "10",
                selectedSigners = listOf(passkeySignerStub())
            )
        }
    }

    @Test
    fun testMultiSignerTransfer_tokenContractTooShort_throwsInvalidAddress() = runTest {
        val kit = createConnectedKit()
        val manager = kit.multiSignerManager

        assertFailsWith<ValidationException.InvalidAddress> {
            manager.multiSignerTransfer(
                tokenContract = "CABC",
                recipient = validAccountAddress,
                amount = "10",
                selectedSigners = listOf(passkeySignerStub())
            )
        }
    }

    // ========================================================================
    // multiSignerTransfer — recipient validation
    // ========================================================================

    @Test
    fun testMultiSignerTransfer_recipientInvalid_throwsInvalidAddress() = runTest {
        val kit = createConnectedKit()
        val manager = kit.multiSignerManager

        assertFailsWith<ValidationException.InvalidAddress> {
            manager.multiSignerTransfer(
                tokenContract = validTargetContract,
                recipient = "NOTAVALIDADDRESS",
                amount = "10",
                selectedSigners = listOf(passkeySignerStub())
            )
        }
    }

    // ========================================================================
    // multiSignerTransfer — self-transfer guard
    // ========================================================================

    @Test
    fun testMultiSignerTransfer_recipientIsSelf_throwsInvalidInput() = runTest {
        val kit = createConnectedKit()
        val manager = kit.multiSignerManager

        // The connected contractId is validContractId; transferring to self must fail.
        val ex = assertFailsWith<ValidationException.InvalidInput> {
            manager.multiSignerTransfer(
                tokenContract = validTargetContract,
                recipient = validContractId,       // == connected contractId
                amount = "10",
                selectedSigners = listOf(passkeySignerStub())
            )
        }
        assertTrue(
            ex.message.contains("self", ignoreCase = true),
            "Exception message must reference self-transfer, got: ${ex.message}"
        )
    }

    // ========================================================================
    // multiSignerTransfer — selectedSigners validation
    // ========================================================================

    @Test
    fun testMultiSignerTransfer_emptySigners_throwsInvalidInput() = runTest {
        val kit = createConnectedKit()
        val manager = kit.multiSignerManager

        val ex = assertFailsWith<ValidationException.InvalidInput> {
            manager.multiSignerTransfer(
                tokenContract = validTargetContract,
                recipient = validAccountAddress,
                amount = "10",
                selectedSigners = emptyList()
            )
        }
        assertTrue(
            ex.message.contains("signer"),
            "Exception message must reference signers, got: ${ex.message}"
        )
    }

    // ========================================================================
    // multiSignerTransfer — forceMethod parameter acceptance
    // ========================================================================

    @Test
    fun testMultiSignerTransfer_forceMethodRpc_signatureCompiles() = runTest {
        // Verify that the method accepts SubmissionMethod.RPC without compile errors.
        // The call will throw NotConnected before reaching the submission step.
        val kit = OZSmartAccountKit.create(buildConfig())
        val manager = kit.multiSignerManager

        assertFailsWith<WalletException.NotConnected> {
            manager.multiSignerTransfer(
                tokenContract = validTargetContract,
                recipient = validAccountAddress,
                amount = "10",
                selectedSigners = listOf(passkeySignerStub()),
                forceMethod = SubmissionMethod.RPC
            )
        }
    }

    @Test
    fun testMultiSignerTransfer_forceMethodRelayer_signatureCompiles() = runTest {
        // Verify that the method accepts SubmissionMethod.RELAYER without compile errors.
        val kit = OZSmartAccountKit.create(buildConfig())
        val manager = kit.multiSignerManager

        assertFailsWith<WalletException.NotConnected> {
            manager.multiSignerTransfer(
                tokenContract = validTargetContract,
                recipient = validAccountAddress,
                amount = "10",
                selectedSigners = listOf(passkeySignerStub()),
                forceMethod = SubmissionMethod.RELAYER
            )
        }
    }

    // ========================================================================
    // multiSignerExecuteAndSubmit — forceMethod parameter acceptance
    // ========================================================================

    @Test
    fun testMultiSignerExecuteAndSubmit_forceMethodNullDefault_signatureCompiles() = runTest {
        val kit = OZSmartAccountKit.create(buildConfig())
        val manager = kit.multiSignerManager

        assertFailsWith<WalletException.NotConnected> {
            manager.multiSignerExecuteAndSubmit(
                target = validTargetContract,
                targetFn = "vote",
                selectedSigners = listOf(passkeySignerStub())
                // forceMethod defaults to null
            )
        }
    }

    @Test
    fun testMultiSignerExecuteAndSubmit_forceMethodRpc_signatureCompiles() = runTest {
        val kit = OZSmartAccountKit.create(buildConfig())
        val manager = kit.multiSignerManager

        assertFailsWith<WalletException.NotConnected> {
            manager.multiSignerExecuteAndSubmit(
                target = validTargetContract,
                targetFn = "vote",
                selectedSigners = listOf(passkeySignerStub()),
                forceMethod = SubmissionMethod.RPC
            )
        }
    }

    // ========================================================================
    // SelectedSigner sealed class construction
    // ========================================================================

    @Test
    fun testSelectedSigner_passkey_defaultFieldsAreNull() {
        val signer = SelectedSigner.Passkey()
        assertTrue(signer.credentialId == null)
        assertTrue(signer.credentialIdBytes == null)
        assertTrue(signer.keyData == null)
    }

    @Test
    fun testSelectedSigner_passkey_fieldsAreSetCorrectly() {
        val credId = "abc123"
        val credBytes = byteArrayOf(1, 2, 3)
        val keyData = ByteArray(97) { it.toByte() }
        val signer = SelectedSigner.Passkey(
            credentialId = credId,
            credentialIdBytes = credBytes,
            keyData = keyData
        )
        assertNotNull(signer.credentialId)
        assertTrue(signer.credentialId == credId)
        assertNotNull(signer.credentialIdBytes)
        assertTrue(signer.credentialIdBytes!!.contentEquals(credBytes))
        assertNotNull(signer.keyData)
        assertTrue(signer.keyData!!.contentEquals(keyData))
    }

    @Test
    fun testSelectedSigner_wallet_holdsAddress() {
        val signer = SelectedSigner.Wallet(address = validAccountAddress)
        assertTrue(signer.address == validAccountAddress)
    }

    @Test
    fun testSelectedSigner_wallet_dataClassEquality() {
        val a = SelectedSigner.Wallet(validAccountAddress)
        val b = SelectedSigner.Wallet(validAccountAddress)
        assertTrue(a == b)
        assertTrue(a.hashCode() == b.hashCode())
    }

    @Test
    fun testSelectedSigner_passkey_dataClassEquality_withNullFields() {
        val a = SelectedSigner.Passkey()
        val b = SelectedSigner.Passkey()
        assertTrue(a == b)
    }

    // ========================================================================
    // multiSignerManager lazy accessor
    // ========================================================================

    @Test
    fun testKit_multiSignerManager_isNotNull() {
        val kit = OZSmartAccountKit.create(buildConfig())
        assertNotNull(kit.multiSignerManager)
    }

    @Test
    fun testKit_multiSignerManager_returnsSameInstance() {
        val kit = OZSmartAccountKit.create(buildConfig())
        val first = kit.multiSignerManager
        val second = kit.multiSignerManager
        assertTrue(first === second, "multiSignerManager must return the same lazy instance")
    }
}
