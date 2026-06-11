//
//  TransactionOperationsValidationTest.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.unitTests.smartaccount

import com.soneso.stellar.sdk.Address
import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.smartaccount.core.SubmissionMethod
import com.soneso.stellar.sdk.smartaccount.core.ValidationException
import com.soneso.stellar.sdk.smartaccount.core.WalletException
import com.soneso.stellar.sdk.smartaccount.oz.OZSmartAccountConfig
import com.soneso.stellar.sdk.smartaccount.oz.OZSmartAccountKit
import com.soneso.stellar.sdk.smartaccount.oz.ResolveContextRuleIds
import com.soneso.stellar.sdk.smartaccount.oz.TransactionResult
import com.soneso.stellar.sdk.xdr.ContractExecutableXdr
import com.soneso.stellar.sdk.xdr.ContractIDPreimageFromAddressXdr
import com.soneso.stellar.sdk.xdr.ContractIDPreimageXdr
import com.soneso.stellar.sdk.xdr.CreateContractArgsXdr
import com.soneso.stellar.sdk.xdr.SorobanAuthorizationEntryXdr
import com.soneso.stellar.sdk.xdr.SorobanAuthorizedFunctionXdr
import com.soneso.stellar.sdk.xdr.SorobanAuthorizedInvocationXdr
import com.soneso.stellar.sdk.xdr.SorobanCredentialsXdr
import com.soneso.stellar.sdk.xdr.Uint256Xdr
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for validation and construction logic in [OZTransactionOperations].
 *
 * Tests cover:
 * - transfer() validation (not-connected guard, invalid recipient, self-transfer, invalid amount)
 * - contractCall() validation (not-connected guard, invalid target, empty function name)
 * - executeAndSubmit() validation (not-connected guard, invalid target, empty function name)
 * - TransactionResult data class construction and equality
 * - SubmissionMethod enum values
 * - ResolveContextRuleIds typealias usability
 *
 * All tests use InMemoryStorageAdapter and do not require network connectivity.
 * Methods that pass validation will fail at the network layer (simulation/RPC);
 * those failures are outside the scope of this test class.
 */
class TransactionOperationsValidationTest {

    // ========================================================================
    // Test Fixtures
    // ========================================================================

    private val validContractAddress =
        "CAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAD2KM"
    private val validContractAddress2 =
        "CDLZFC3SYJYDZT7K67VZ75HPJVIEUVNIXF47ZG2FB2RMQQVU2HHGCYSC"
    private val validAccountAddress =
        "GA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJVSGZ"

    private fun buildConfig(): OZSmartAccountConfig = OZSmartAccountConfig(
        rpcUrl = "https://soroban-testnet.stellar.org",
        networkPassphrase = Network.TESTNET.networkPassphrase,
        accountWasmHash = "a" + "0".repeat(63),
        webauthnVerifierAddress = validContractAddress
    )

    /** Creates a kit that is NOT connected (no wallet state set). */
    private fun createDisconnectedKit(): OZSmartAccountKit {
        return OZSmartAccountKit.create(buildConfig())
    }

    /** Creates a kit with connected state set to the given contractId. */
    private suspend fun createConnectedKit(
        contractId: String = validContractAddress2
    ): OZSmartAccountKit {
        val kit = OZSmartAccountKit.create(buildConfig())
        kit.setConnectedState("test-credential-id", contractId)
        return kit
    }

    // ========================================================================
    // transfer() - Not Connected Guard
    // ========================================================================

    @Test
    fun transfer_notConnected_throws() = runTest {
        val kit = createDisconnectedKit()
        val txOps = kit.transactionOperations

        val ex = assertFailsWith<WalletException.NotConnected> {
            txOps.transfer(
                tokenContract = validContractAddress,
                recipient = validAccountAddress,
                amount = "10"
            )
        }
        assertNotNull(ex.message)
        assertTrue(ex.message!!.contains("No wallet connected"))
    }

    // ========================================================================
    // transfer() - Invalid Recipient Address
    // ========================================================================

    @Test
    fun transfer_invalidRecipient_garbage_throws() = runTest {
        val kit = createConnectedKit()
        val txOps = kit.transactionOperations

        val ex = assertFailsWith<ValidationException.InvalidAddress> {
            txOps.transfer(
                tokenContract = validContractAddress,
                recipient = "not-a-stellar-address",
                amount = "10"
            )
        }
        assertNotNull(ex.message)
        assertTrue(ex.message!!.contains("recipient"))
    }

    @Test
    fun transfer_invalidRecipient_emptyString_throws() = runTest {
        val kit = createConnectedKit()
        val txOps = kit.transactionOperations

        assertFailsWith<ValidationException.InvalidAddress> {
            txOps.transfer(
                tokenContract = validContractAddress,
                recipient = "",
                amount = "10"
            )
        }
    }

    @Test
    fun transfer_invalidRecipient_muxedAddress_throws() = runTest {
        val kit = createConnectedKit()
        val txOps = kit.transactionOperations

        // M... addresses are not accepted by requireStellarAddress (only G... and C...)
        val muxedAddress =
            "MAQAA5L65LSYH7CQ3VTJ7F3HHLGCL3DSLAR2Y47263D56MNNGHSQSAAAAAAAAAAPZFBVAI"
        assertFailsWith<ValidationException.InvalidAddress> {
            txOps.transfer(
                tokenContract = validContractAddress,
                recipient = muxedAddress,
                amount = "10"
            )
        }
    }

    // ========================================================================
    // transfer() - Self-Transfer
    // ========================================================================

    @Test
    fun transfer_selfTransfer_throws() = runTest {
        // Connect the kit with contractId = validContractAddress2, then try to
        // transfer to that same address.
        val kit = createConnectedKit(contractId = validContractAddress2)
        val txOps = kit.transactionOperations

        val ex = assertFailsWith<ValidationException.InvalidInput> {
            txOps.transfer(
                tokenContract = validContractAddress,
                recipient = validContractAddress2,
                amount = "10"
            )
        }
        assertNotNull(ex.message)
        assertTrue(ex.message!!.contains("Cannot transfer to self"))
    }

    // ========================================================================
    // transfer() - Invalid Amount
    // ========================================================================

    @Test
    fun transfer_zeroAmount_throws() = runTest {
        val kit = createConnectedKit()
        val txOps = kit.transactionOperations

        // decimals is passed explicitly so the amount conversion runs without the
        // on-chain decimals() fetch (these tests are offline).
        assertFailsWith<ValidationException.InvalidAmount> {
            txOps.transfer(
                tokenContract = validContractAddress,
                recipient = validAccountAddress,
                amount = "0",
                decimals = 7
            )
        }
    }

    @Test
    fun transfer_negativeAmount_throws() = runTest {
        val kit = createConnectedKit()
        val txOps = kit.transactionOperations

        assertFailsWith<ValidationException.InvalidAmount> {
            txOps.transfer(
                tokenContract = validContractAddress,
                recipient = validAccountAddress,
                amount = "-5",
                decimals = 7
            )
        }
    }

    @Test
    fun transfer_nonNumericAmount_throws() = runTest {
        val kit = createConnectedKit()
        val txOps = kit.transactionOperations

        assertFailsWith<ValidationException.InvalidAmount> {
            txOps.transfer(
                tokenContract = validContractAddress,
                recipient = validAccountAddress,
                amount = "abc",
                decimals = 7
            )
        }
    }

    @Test
    fun transfer_emptyAmount_throws() = runTest {
        val kit = createConnectedKit()
        val txOps = kit.transactionOperations

        assertFailsWith<ValidationException.InvalidAmount> {
            txOps.transfer(
                tokenContract = validContractAddress,
                recipient = validAccountAddress,
                amount = "",
                decimals = 7
            )
        }
    }

    @Test
    fun transfer_scientificNotation_throws() = runTest {
        val kit = createConnectedKit()
        val txOps = kit.transactionOperations

        assertFailsWith<ValidationException.InvalidAmount> {
            txOps.transfer(
                tokenContract = validContractAddress,
                recipient = validAccountAddress,
                amount = "1e5",
                decimals = 7
            )
        }
    }

    @Test
    fun transfer_amountTooSmall_throws() = runTest {
        val kit = createConnectedKit()
        val txOps = kit.transactionOperations

        // 8 fractional digits exceed the 7-decimal token scale
        assertFailsWith<ValidationException.InvalidAmount> {
            txOps.transfer(
                tokenContract = validContractAddress,
                recipient = validAccountAddress,
                amount = "0.00000001",
                decimals = 7
            )
        }
    }

    // ========================================================================
    // transfer() - Invalid Token Contract
    // ========================================================================

    @Test
    fun transfer_invalidTokenContract_throws() = runTest {
        val kit = createConnectedKit()
        val txOps = kit.transactionOperations

        // With decimals unset, the token contract is validated by fetchTokenDecimals
        // (before any RPC call), tagged with the "tokenContract" field name.
        val ex = assertFailsWith<ValidationException.InvalidAddress> {
            txOps.transfer(
                tokenContract = "not-a-contract",
                recipient = validAccountAddress,
                amount = "10"
            )
        }
        assertNotNull(ex.message)
        assertTrue(ex.message!!.contains("tokenContract"))
    }

    @Test
    fun transfer_invalidTokenContract_explicitDecimals_throws() = runTest {
        val kit = createConnectedKit()
        val txOps = kit.transactionOperations

        // With decimals supplied, the decimals fetch is skipped and the token contract
        // is validated inside contractCall as the "target".
        val ex = assertFailsWith<ValidationException.InvalidAddress> {
            txOps.transfer(
                tokenContract = "not-a-contract",
                recipient = validAccountAddress,
                amount = "10",
                decimals = 7
            )
        }
        assertNotNull(ex.message)
        assertTrue(ex.message!!.contains("target"))
    }

    // ========================================================================
    // transfer() - Valid Recipient Types
    //
    // These tests verify that validation passes for both G-addresses and
    // C-addresses. They will fail at the network layer (simulation), not
    // at validation. We catch the network-level exception to confirm
    // validation passed.
    // ========================================================================

    @Test
    fun transfer_recipientGAddress_passesValidation() = runTest {
        val kit = createConnectedKit()
        val txOps = kit.transactionOperations

        // Validation passes; network call fails because we have no real connection.
        // Any exception other than ValidationException or WalletException means
        // validation succeeded.
        try {
            txOps.transfer(
                tokenContract = validContractAddress,
                recipient = validAccountAddress,
                amount = "10"
            )
        } catch (e: ValidationException) {
            throw AssertionError("Validation should pass for G-address recipient", e)
        } catch (e: WalletException) {
            throw AssertionError("Wallet exception unexpected after connected state set", e)
        } catch (_: Exception) {
            // Expected: network/simulation failure
        }
    }

    @Test
    fun transfer_recipientCAddress_passesValidation() = runTest {
        // Use a different contract address than the connected one to avoid self-transfer
        val kit = createConnectedKit(contractId = validContractAddress)
        val txOps = kit.transactionOperations

        try {
            txOps.transfer(
                tokenContract = validContractAddress,
                recipient = validContractAddress2,
                amount = "10"
            )
        } catch (e: ValidationException) {
            throw AssertionError("Validation should pass for C-address recipient", e)
        } catch (e: WalletException) {
            throw AssertionError("Wallet exception unexpected", e)
        } catch (_: Exception) {
            // Expected: network/simulation failure
        }
    }

    // ========================================================================
    // contractCall() - Not Connected Guard
    // ========================================================================

    @Test
    fun contractCall_notConnected_throws() = runTest {
        val kit = createDisconnectedKit()
        val txOps = kit.transactionOperations

        assertFailsWith<WalletException.NotConnected> {
            txOps.contractCall(
                target = validContractAddress,
                targetFn = "transfer"
            )
        }
    }

    // ========================================================================
    // contractCall() - Invalid Target Address
    // ========================================================================

    @Test
    fun contractCall_invalidTarget_garbage_throws() = runTest {
        val kit = createConnectedKit()
        val txOps = kit.transactionOperations

        val ex = assertFailsWith<ValidationException.InvalidAddress> {
            txOps.contractCall(
                target = "not-a-contract-address",
                targetFn = "my_function"
            )
        }
        assertNotNull(ex.message)
        assertTrue(ex.message!!.contains("target"))
    }

    @Test
    fun contractCall_invalidTarget_gAddress_throws() = runTest {
        val kit = createConnectedKit()
        val txOps = kit.transactionOperations

        // contractCall requires a C-address (contract), not a G-address (account)
        assertFailsWith<ValidationException.InvalidAddress> {
            txOps.contractCall(
                target = validAccountAddress,
                targetFn = "my_function"
            )
        }
    }

    @Test
    fun contractCall_invalidTarget_emptyString_throws() = runTest {
        val kit = createConnectedKit()
        val txOps = kit.transactionOperations

        assertFailsWith<ValidationException.InvalidAddress> {
            txOps.contractCall(
                target = "",
                targetFn = "my_function"
            )
        }
    }

    // ========================================================================
    // contractCall() - Empty Function Name
    // ========================================================================

    @Test
    fun contractCall_emptyFunctionName_throws() = runTest {
        val kit = createConnectedKit()
        val txOps = kit.transactionOperations

        val ex = assertFailsWith<ValidationException.InvalidInput> {
            txOps.contractCall(
                target = validContractAddress,
                targetFn = ""
            )
        }
        assertNotNull(ex.message)
        assertTrue(ex.message!!.contains("Function name cannot be empty"))
    }

    @Test
    fun contractCall_blankFunctionName_throws() = runTest {
        val kit = createConnectedKit()
        val txOps = kit.transactionOperations

        assertFailsWith<ValidationException.InvalidInput> {
            txOps.contractCall(
                target = validContractAddress,
                targetFn = "   "
            )
        }
    }

    // ========================================================================
    // contractCall() - Valid Inputs Pass Validation
    // ========================================================================

    @Test
    fun contractCall_validInputs_passesValidation() = runTest {
        val kit = createConnectedKit()
        val txOps = kit.transactionOperations

        try {
            txOps.contractCall(
                target = validContractAddress,
                targetFn = "my_function"
            )
        } catch (e: ValidationException) {
            throw AssertionError("Validation should pass for valid inputs", e)
        } catch (e: WalletException) {
            throw AssertionError("Wallet exception unexpected", e)
        } catch (_: Exception) {
            // Expected: network/simulation failure
        }
    }

    // ========================================================================
    // executeAndSubmit() - Not Connected Guard
    // ========================================================================

    @Test
    fun executeAndSubmit_notConnected_throws() = runTest {
        val kit = createDisconnectedKit()
        val txOps = kit.transactionOperations

        assertFailsWith<WalletException.NotConnected> {
            txOps.executeAndSubmit(
                target = validContractAddress,
                targetFn = "execute"
            )
        }
    }

    // ========================================================================
    // executeAndSubmit() - Invalid Target Address
    // ========================================================================

    @Test
    fun executeAndSubmit_invalidTarget_garbage_throws() = runTest {
        val kit = createConnectedKit()
        val txOps = kit.transactionOperations

        val ex = assertFailsWith<ValidationException.InvalidAddress> {
            txOps.executeAndSubmit(
                target = "bad-address",
                targetFn = "do_something"
            )
        }
        assertNotNull(ex.message)
        assertTrue(ex.message!!.contains("target"))
    }

    @Test
    fun executeAndSubmit_invalidTarget_gAddress_throws() = runTest {
        val kit = createConnectedKit()
        val txOps = kit.transactionOperations

        // executeAndSubmit requires a C-address (contract), not G-address
        assertFailsWith<ValidationException.InvalidAddress> {
            txOps.executeAndSubmit(
                target = validAccountAddress,
                targetFn = "do_something"
            )
        }
    }

    @Test
    fun executeAndSubmit_invalidTarget_emptyString_throws() = runTest {
        val kit = createConnectedKit()
        val txOps = kit.transactionOperations

        assertFailsWith<ValidationException.InvalidAddress> {
            txOps.executeAndSubmit(
                target = "",
                targetFn = "do_something"
            )
        }
    }

    // ========================================================================
    // executeAndSubmit() - Empty Function Name
    //
    // BUG: executeAndSubmit() does NOT validate targetFn. The function name
    // is passed directly to the smart account's execute() entry point without
    // a blank check. This is an inconsistency with contractCall(), which does
    // validate targetFn via: if (targetFn.isBlank()) throw ValidationException.
    // executeAndSubmit() should add the same check for consistency and to
    // catch invalid inputs early rather than at the contract execution level.
    // ========================================================================

    @Test
    fun executeAndSubmit_emptyFunctionName_throwsValidationException() = runTest {
        val kit = createConnectedKit()
        val txOps = kit.transactionOperations

        assertFailsWith<ValidationException.InvalidInput> {
            txOps.executeAndSubmit(
                target = validContractAddress,
                targetFn = ""
            )
        }
    }

    // ========================================================================
    // TransactionResult - Construction and Defaults
    // ========================================================================

    @Test
    fun transactionResult_allFields() {
        val result = TransactionResult(
            success = true,
            hash = "abc123",
            ledger = 42u,
            error = null
        )
        assertTrue(result.success)
        assertEquals("abc123", result.hash)
        assertEquals(42u, result.ledger)
        assertNull(result.error)
    }

    @Test
    fun transactionResult_defaults() {
        val result = TransactionResult(success = false)
        assertFalse(result.success)
        assertNull(result.hash)
        assertNull(result.ledger)
        assertNull(result.error)
    }

    @Test
    fun transactionResult_failureWithError() {
        val result = TransactionResult(
            success = false,
            hash = "def456",
            ledger = null,
            error = "Simulation failed"
        )
        assertFalse(result.success)
        assertEquals("def456", result.hash)
        assertNull(result.ledger)
        assertEquals("Simulation failed", result.error)
    }

    @Test
    fun transactionResult_successWithLedger() {
        val result = TransactionResult(
            success = true,
            hash = "txhash",
            ledger = 1000u
        )
        assertTrue(result.success)
        assertEquals(1000u, result.ledger)
        assertNull(result.error)
    }

    // ========================================================================
    // TransactionResult - Equality (data class)
    // ========================================================================

    @Test
    fun transactionResult_equalInstances() {
        val a = TransactionResult(success = true, hash = "h1", ledger = 10u, error = null)
        val b = TransactionResult(success = true, hash = "h1", ledger = 10u, error = null)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun transactionResult_unequalInstances() {
        val a = TransactionResult(success = true, hash = "h1", ledger = 10u)
        val b = TransactionResult(success = false, hash = "h1", ledger = 10u)
        assertNotEquals(a, b)
    }

    @Test
    fun transactionResult_copy() {
        val original = TransactionResult(success = true, hash = "orig")
        val modified = original.copy(success = false, error = "failed")
        assertTrue(original.success)
        assertFalse(modified.success)
        assertEquals("orig", modified.hash)
        assertEquals("failed", modified.error)
    }

    // ========================================================================
    // SubmissionMethod Enum
    // ========================================================================

    @Test
    fun submissionMethod_values() {
        val values = SubmissionMethod.entries
        assertEquals(2, values.size)
        assertTrue(values.contains(SubmissionMethod.RELAYER))
        assertTrue(values.contains(SubmissionMethod.RPC))
    }

    @Test
    fun submissionMethod_valueOf() {
        assertEquals(SubmissionMethod.RELAYER, SubmissionMethod.valueOf("RELAYER"))
        assertEquals(SubmissionMethod.RPC, SubmissionMethod.valueOf("RPC"))
    }

    @Test
    fun submissionMethod_invalidValue_throws() {
        assertFailsWith<IllegalArgumentException> {
            SubmissionMethod.valueOf("INVALID")
        }
    }

    // ========================================================================
    // ResolveContextRuleIds Typealias
    // ========================================================================

    @Test
    fun resolveContextRuleIds_lambdaUsable() = runTest {
        // Verify the typealias can be used as a suspend lambda
        val resolver: ResolveContextRuleIds = { _, index ->
            listOf(index.toUInt())
        }
        val result = resolver(createMinimalAuthEntry(), 3)
        assertEquals(listOf(3u), result)
    }

    @Test
    fun resolveContextRuleIds_emptyList() = runTest {
        val resolver: ResolveContextRuleIds = { _, _ -> emptyList() }
        val result = resolver(createMinimalAuthEntry(), 0)
        assertTrue(result.isEmpty())
    }

    @Test
    fun resolveContextRuleIds_multipleIds() = runTest {
        val resolver: ResolveContextRuleIds = { _, _ -> listOf(1u, 2u, 5u) }
        val result = resolver(createMinimalAuthEntry(), 0)
        assertEquals(listOf(1u, 2u, 5u), result)
    }

    // ========================================================================
    // transfer() - Validation Order
    //
    // Verifies that requireConnected() is checked before recipient validation
    // and before amount conversion.
    // ========================================================================

    @Test
    fun transfer_notConnected_beforeRecipientValidation() = runTest {
        val kit = createDisconnectedKit()
        val txOps = kit.transactionOperations

        // Even with an invalid recipient, the not-connected guard should fire first
        assertFailsWith<WalletException.NotConnected> {
            txOps.transfer(
                tokenContract = validContractAddress,
                recipient = "invalid",
                amount = "10"
            )
        }
    }

    @Test
    fun transfer_notConnected_beforeAmountValidation() = runTest {
        val kit = createDisconnectedKit()
        val txOps = kit.transactionOperations

        // Even with an invalid amount, the not-connected guard should fire first
        assertFailsWith<WalletException.NotConnected> {
            txOps.transfer(
                tokenContract = validContractAddress,
                recipient = validAccountAddress,
                amount = "not-a-number"
            )
        }
    }

    // ========================================================================
    // contractCall() - Validation Order
    // ========================================================================

    @Test
    fun contractCall_notConnected_beforeTargetValidation() = runTest {
        val kit = createDisconnectedKit()
        val txOps = kit.transactionOperations

        assertFailsWith<WalletException.NotConnected> {
            txOps.contractCall(
                target = "invalid-target",
                targetFn = "fn"
            )
        }
    }

    // ========================================================================
    // executeAndSubmit() - Validation Order
    // ========================================================================

    @Test
    fun executeAndSubmit_notConnected_beforeTargetValidation() = runTest {
        val kit = createDisconnectedKit()
        val txOps = kit.transactionOperations

        assertFailsWith<WalletException.NotConnected> {
            txOps.executeAndSubmit(
                target = "invalid-target",
                targetFn = "fn"
            )
        }
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    /**
     * Creates a minimal SorobanAuthorizationEntryXdr for lambda signature testing.
     * Uses Void (source_account) credentials and a minimal invocation tree.
     */
    private fun createMinimalAuthEntry(): SorobanAuthorizationEntryXdr {
        return SorobanAuthorizationEntryXdr(
            credentials = SorobanCredentialsXdr.Void,
            rootInvocation = SorobanAuthorizedInvocationXdr(
                function = SorobanAuthorizedFunctionXdr.CreateContractHostFn(
                    CreateContractArgsXdr(
                        contractIdPreimage = ContractIDPreimageXdr.FromAddress(
                            ContractIDPreimageFromAddressXdr(
                                address = Address(validContractAddress).toSCAddress(),
                                salt = Uint256Xdr(ByteArray(32))
                            )
                        ),
                        executable = ContractExecutableXdr.Void
                    )
                ),
                subInvocations = emptyList()
            )
        )
    }
}
