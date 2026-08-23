//
//  TransactionOperationsValidationTest.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.unitTests.smartaccount.oz

import com.soneso.stellar.sdk.unitTests.smartaccount.MockWebAuthnProvider
import com.soneso.stellar.sdk.unitTests.smartaccount.buildAccountEntryXdr
import com.soneso.stellar.sdk.unitTests.smartaccount.buildAuthEntry
import com.soneso.stellar.sdk.unitTests.smartaccount.buildConfig
import com.soneso.stellar.sdk.unitTests.smartaccount.buildMinimalSorobanData
import com.soneso.stellar.sdk.unitTests.smartaccount.buildNoRpcMockServer
import com.soneso.stellar.sdk.unitTests.smartaccount.latestLedgerResponseJson
import com.soneso.stellar.sdk.unitTests.smartaccount.ledgerEntriesResponseJson
import com.soneso.stellar.sdk.unitTests.smartaccount.relayerSuccessWithoutHashJson
import com.soneso.stellar.sdk.unitTests.smartaccount.simulateErrorResponseJson
import com.soneso.stellar.sdk.AbstractTransaction
import com.soneso.stellar.sdk.Address
import com.soneso.stellar.sdk.FriendBot
import com.soneso.stellar.sdk.InvokeHostFunctionOperation
import com.soneso.stellar.sdk.KeyPair
import com.soneso.stellar.sdk.MemoNone
import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.Transaction
import com.soneso.stellar.sdk.TransactionBuilder
import com.soneso.stellar.sdk.Util
import com.soneso.stellar.sdk.crypto.getSha256Crypto
import com.soneso.stellar.sdk.rpc.SorobanServer
import com.soneso.stellar.sdk.rpc.responses.SimulateTransactionResponse
import com.soneso.stellar.sdk.scval.Scv
import com.soneso.stellar.sdk.smartaccount.core.CredentialException
import com.soneso.stellar.sdk.smartaccount.core.SmartAccountAuth
import com.soneso.stellar.sdk.smartaccount.core.SmartAccountConstants
import com.soneso.stellar.sdk.smartaccount.core.SubmissionMethod
import com.soneso.stellar.sdk.smartaccount.core.TransactionException
import com.soneso.stellar.sdk.smartaccount.core.ValidationException
import com.soneso.stellar.sdk.smartaccount.core.WalletException
import com.soneso.stellar.sdk.smartaccount.oz.InMemoryStorageAdapter
import com.soneso.stellar.sdk.smartaccount.oz.OZConstants
import com.soneso.stellar.sdk.smartaccount.oz.OZSmartAccountConfig
import com.soneso.stellar.sdk.smartaccount.oz.OZRelayerClient
import com.soneso.stellar.sdk.smartaccount.oz.OZSmartAccountKit
import com.soneso.stellar.sdk.smartaccount.oz.RelayerErrorCodes
import com.soneso.stellar.sdk.smartaccount.oz.ResolveContextRuleIds
import com.soneso.stellar.sdk.smartaccount.oz.SmartAccountEvent
import com.soneso.stellar.sdk.smartaccount.oz.StorageAdapter
import com.soneso.stellar.sdk.smartaccount.oz.StoredCredential
import com.soneso.stellar.sdk.smartaccount.oz.TransactionResult
import com.soneso.stellar.sdk.smartaccount.oz.WebAuthnAuthenticationResult
import com.soneso.stellar.sdk.smartaccount.oz.WebAuthnProvider
import com.soneso.stellar.sdk.xdr.ContractExecutableXdr
import com.soneso.stellar.sdk.xdr.ContractIDPreimageFromAddressXdr
import com.soneso.stellar.sdk.xdr.ContractIDPreimageXdr
import com.soneso.stellar.sdk.xdr.CreateContractArgsXdr
import com.soneso.stellar.sdk.xdr.HashIDPreimageSorobanAuthorizationWithAddressXdr
import com.soneso.stellar.sdk.xdr.HashIDPreimageXdr
import com.soneso.stellar.sdk.xdr.HashXdr
import com.soneso.stellar.sdk.xdr.HostFunctionXdr
import com.soneso.stellar.sdk.xdr.InvokeContractArgsXdr
import com.soneso.stellar.sdk.xdr.SCMapEntryXdr
import com.soneso.stellar.sdk.xdr.SCMapXdr
import com.soneso.stellar.sdk.xdr.SCSymbolXdr
import com.soneso.stellar.sdk.xdr.SCValTypeXdr
import com.soneso.stellar.sdk.xdr.SCValXdr
import com.soneso.stellar.sdk.xdr.SorobanAuthorizationEntryXdr
import com.soneso.stellar.sdk.xdr.SorobanAuthorizedFunctionXdr
import com.soneso.stellar.sdk.xdr.SorobanAuthorizedInvocationXdr
import com.soneso.stellar.sdk.xdr.SorobanCredentialsXdr
import com.soneso.stellar.sdk.xdr.Uint256Xdr
import com.soneso.stellar.sdk.xdr.Uint32Xdr
import com.soneso.stellar.sdk.xdr.XdrWriter
import com.soneso.stellar.sdk.xdr.fromXdrBase64
import com.soneso.stellar.sdk.xdr.toXdrBase64
import com.ionspin.kotlin.bignum.integer.BigInteger
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for [OZTransactionOperations]: input validation, the single-passkey signing
 * and submission lifecycle, and the outcome mapping of a submitted transaction.
 *
 * Validation:
 * - transfer() (not-connected guard, invalid recipient, self-transfer, invalid amount,
 *   invalid token contract with and without an explicit decimals scale)
 * - contractCall() and executeAndSubmit() (not-connected guard, invalid target,
 *   empty function name, guard ordering)
 * - TransactionResult construction, defaults and equality; SubmissionMethod entries;
 *   the ResolveContextRuleIds typealias
 *
 * Signing lifecycle (submit(), driven through contractCall / executeAndSubmit / transfer):
 * - an auth entry addressed to the connected smart account is signed with the passkey; the
 *   WebAuthn challenge is the auth digest binding the resolved rule IDs to the entry payload,
 *   and the allowCredentials constraint carries the stored transport hints
 * - key data resolution: storage hit, storage miss falling back to the on-chain context-rule
 *   scan, and the not-found failure when neither holds the credential
 * - context rule IDs from an explicit resolver callback and from automatic on-chain resolution,
 *   plus the failure when no rule covers the invocation context
 * - entries that are not signed: source_account (Void) credentials pass through unchanged, and
 *   a simulation with no auth entries (results absent, empty, or without an auth member) emits
 *   a signed event without a credential
 * - failures: missing WebAuthn provider, an undecodable credential ID, a failing re-simulation,
 *   and forcing relayer submission with no relayer configured
 *
 * Submission outcomes:
 * - sendTransaction ERROR and TRY_AGAIN_LATER, a pending submission with no hash
 * - on-chain confirmation SUCCESS (with and without a ledger) and FAILED (with and without a
 *   result XDR)
 * - submitMultiSignerTransaction() routing to RPC by default and to the relayer when one is
 *   configured
 * - relayer submission: the host-function mode used when no source_account credentials remain,
 *   a rejected submission, and a submission accepted without a hash
 * - simulateAndExtractResult() failures surfaced through fetchTokenDecimals()
 *
 * fundWallet():
 * - not-connected and invalid-contract-address guards fire before any HTTP call, including the
 *   Friendbot request
 * - Friendbot returning a non-2xx status
 * - the temp account balance query returning a non-i128 value, and the balance being at or below
 *   the Friendbot reserve (both strictly below and exactly at, since the check is "<=")
 * - the funding-transfer simulation failing
 * - the full happy path, asserting the exact funded XLM amount returned
 * - auth-entry preparation for both credential shapes: source_account entries converted to fresh
 *   ADDRESS_V2 credentials signed over the address-bound preimage, and existing Address
 *   credentials re-signed with their arm, address and nonce preserved
 * - relayed funding, including a relayer rejection
 *
 * All Soroban RPC traffic is served by a scripted Ktor MockEngine and all storage is in-memory;
 * the relayer is likewise an [OZRelayerClient] over a MockEngine, so no test contacts the
 * network. fundWallet() additionally routes Friendbot through [FriendBot.httpClientOverride],
 * reset after every test since FriendBot is a process-wide singleton shared with the integration
 * test suite.
 *
 * Out of reach for a hermetic unit test: the NOT_FOUND arm of the post-submission confirmation
 * poll. [com.soneso.stellar.sdk.rpc.SorobanServer.pollTransaction] sleeps on real wall-clock time
 * between attempts, so exhausting its 30 attempts at the configured 3-second interval would take
 * about 90 seconds.
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

    /** [validContractAddress] with its trailing character changed, breaking the CRC16 checksum. */
    private val malformedContractAddress =
        validContractAddress.dropLast(1) + "X"

    /**
     * [FriendBot] is a process-wide singleton shared with the integration test suite, so every
     * fundWallet() test below installs [FriendBot.httpClientOverride] before calling fundWallet()
     * and this teardown clears it again afterward, regardless of whether the test body succeeded
     * or threw.
     */
    @AfterTest
    fun resetFriendBotOverride() {
        FriendBot.httpClientOverride = null
    }

    /**
     * Creates a kit that is NOT connected (no wallet state set).
     *
     * The injected Soroban server fails on any request, so a validation guard that leaks
     * past its check surfaces as a failed RPC rather than silently reaching the network.
     */
    private fun createDisconnectedKit(): OZSmartAccountKit {
        return OZSmartAccountKit.createWithServer(buildConfig(), buildNoRpcMockServer())
    }

    /** Creates a kit with connected state set to the given contractId and a no-RPC server. */
    private suspend fun createConnectedKit(
        contractId: String = validContractAddress2
    ): OZSmartAccountKit {
        val kit = OZSmartAccountKit.createWithServer(buildConfig(), buildNoRpcMockServer())
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
    // C-addresses. The call then fails at the RPC layer, which the injected
    // no-RPC server rejects. Catching that failure — and nothing from the
    // validation families — confirms validation let the call through.
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
    // A blank target function is rejected before the invocation is built, the
    // same guard contractCall() applies, so the error names the input rather
    // than surfacing from contract execution.
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

    // ========================================================================
    // Signing and Submission Fixtures
    //
    // The tests below drive the full submit() lifecycle against a scripted
    // Soroban RPC mock: simulate, auth-entry signing via WebAuthn, re-simulate,
    // submit and poll. No test touches the network.
    // ========================================================================

    /** Raw credential ID bytes of the connected passkey. */
    private val credentialIdBytes = ByteArray(16) { it.toByte() }

    /** Base64URL form of [credentialIdBytes]; the value held as the kit's connected credential. */
    private val credentialId = Util.base64urlEncode(credentialIdBytes)

    /** Uncompressed secp256r1 public key stored alongside the credential. */
    private val storedPublicKey = ByteArray(SmartAccountConstants.SECP256R1_PUBLIC_KEY_SIZE) { i ->
        if (i == 0) 0x04.toByte() else ((i * 3) % 256).toByte()
    }

    /**
     * DER-encoded secp256r1 signature whose r and s are both below the curve half-order,
     * so `SmartAccountUtils.normalizeSignature` accepts it and yields 64 compact bytes.
     */
    private val validDerSignature = hexToBytes(
        "3044022001020304050607080910111213141516171819202122232425262728293031320220" +
            "0000000000000000000000000000000000000000000000000000000000000005"
    )

    /** Ledger sequence reported by [latestLedgerResponseJson]. */
    private val latestLedgerSequence = 1000

    /**
     * Signature expiration ledger applied by submit(): the latest ledger plus the
     * configured `signatureExpirationLedgers` (720 by default).
     */
    private val expirationLedger = (latestLedgerSequence + Util.LEDGERS_PER_HOUR).toUInt()

    private fun hexToBytes(hex: String): ByteArray = ByteArray(hex.length / 2) { i ->
        hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
    }

    private fun buildMockHttpClient(engine: MockEngine): HttpClient = HttpClient(engine) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
                encodeDefaults = false
            })
        }
    }

    /**
     * Creates a [SorobanServer] that answers requests with [responses] in order.
     *
     * Requests past the end of [responses] are answered with [trailingResponse] when supplied
     * (used for the repeated getTransaction polling calls) and otherwise fail the test, so an
     * unexpected extra round-trip is never silently absorbed.
     *
     * [onRequest] receives the request body of each call, keyed by its index.
     */
    private fun buildScriptedMockServer(
        responses: List<String>,
        trailingResponse: String? = null,
        onRequest: ((index: Int, body: String) -> Unit)? = null
    ): SorobanServer {
        var requestIndex = 0
        val mockEngine = MockEngine { request ->
            val index = requestIndex++
            onRequest?.invoke(index, request.body.toByteArray().decodeToString())
            val body = responses.getOrNull(index)
                ?: trailingResponse
                ?: error("Unexpected RPC request at index $index")
            respond(
                content = ByteReadChannel(body),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        return SorobanServer(
            "https://soroban-testnet.stellar.org",
            buildMockHttpClient(mockEngine)
        )
    }

    private fun buildSigningConfig(
        deployer: KeyPair,
        webauthnProvider: WebAuthnProvider?,
        storage: StorageAdapter
    ): OZSmartAccountConfig = OZSmartAccountConfig(
        rpcUrl = "https://soroban-testnet.stellar.org",
        networkPassphrase = Network.TESTNET.networkPassphrase,
        accountWasmHash = "a" + "0".repeat(63),
        webauthnVerifierAddress = validContractAddress,
        deployerKeypair = deployer,
        webauthnProvider = webauthnProvider,
        storage = storage
    )

    /**
     * Builds a kit connected to [contractId] whose RPC is scripted by [responses].
     *
     * When [withStoredCredential] is true the connected credential is present in storage with
     * `internal` transports, so the signing loop takes the storage-hit branch; otherwise the
     * loop falls through to the on-chain context-rule lookup.
     *
     * Passing [relayerClient] makes the kit prefer relayer submission, which is the routing the
     * kit auto-detects whenever a relayer is configured.
     */
    private suspend fun createSigningKit(
        deployer: KeyPair,
        responses: List<String>,
        trailingResponse: String? = null,
        webauthnProvider: WebAuthnProvider? = null,
        withStoredCredential: Boolean = false,
        connectedCredentialId: String? = credentialId,
        contractId: String = validContractAddress2,
        relayerClient: OZRelayerClient? = null,
        onRequest: ((index: Int, body: String) -> Unit)? = null
    ): OZSmartAccountKit {
        val storage = InMemoryStorageAdapter()
        if (withStoredCredential && connectedCredentialId != null) {
            storage.save(
                StoredCredential(
                    credentialId = connectedCredentialId,
                    publicKey = storedPublicKey,
                    contractId = contractId,
                    transports = listOf("internal")
                )
            )
        }
        val kit = OZSmartAccountKit.createWithServer(
            config = buildSigningConfig(deployer, webauthnProvider, storage),
            sorobanServer = buildScriptedMockServer(responses, trailingResponse, onRequest),
            relayerClient = relayerClient
        )
        kit.setConnectedState(connectedCredentialId, contractId)
        return kit
    }

    /**
     * An [OZRelayerClient] over a Ktor [MockEngine] that answers every submission with
     * [responseBody]. Request payloads are recorded so tests can assert which relayer mode the
     * submission used and what it carried.
     */
    private class MockRelayer(responseBody: String) {
        val requestBodies = mutableListOf<String>()

        val client: OZRelayerClient

        init {
            val engine = MockEngine { request ->
                requestBodies.add(request.body.toByteArray().decodeToString())
                respond(
                    content = ByteReadChannel(responseBody),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
            client = OZRelayerClient(
                relayerUrl = "https://relayer.example.com",
                injectedClient = HttpClient(engine) {
                    install(ContentNegotiation) {
                        json(Json { ignoreUnknownKeys = true; isLenient = true })
                    }
                }
            )
        }

        /** The single recorded payload, parsed as a JSON object. */
        fun singleRequestJson() = Json.parseToJsonElement(requestBodies.single()).jsonObject
    }

    private fun relayerSuccessJson(hash: String): String =
        """{"success":true,"data":{"transactionId":"relayer-tx-1","hash":"$hash","status":"PENDING"}}"""

    private fun relayerErrorJson(error: String, code: String): String =
        """{"success":false,"error":"$error","code":"$code"}"""

    /** Reads the `public_key` and `signature` fields out of an Ed25519 auth-entry signature vector. */
    private fun ed25519SignatureFields(signature: SCValXdr): Pair<ByteArray, ByteArray> {
        val entries = Scv.fromVec(signature)
        assertEquals(1, entries.size, "An Ed25519 auth signature carries exactly one signer map")
        val fields = Scv.fromMap(entries.single())
            .map { (key, value) -> Scv.fromSymbol(key) to value }
            .toMap()
        return Scv.fromBytes(fields.getValue("public_key")) to Scv.fromBytes(fields.getValue("signature"))
    }

    private fun passkeyProvider(
        signature: ByteArray = validDerSignature
    ): MockWebAuthnProvider = MockWebAuthnProvider().apply {
        authenticationResult = WebAuthnAuthenticationResult(
            credentialId = credentialIdBytes,
            authenticatorData = ByteArray(37) { it.toByte() },
            clientDataJSON = """{"type":"webauthn.get","challenge":"test"}""".encodeToByteArray(),
            signature = signature
        )
    }

    // ---------------------------------------------------------------------
    // Scripted RPC payloads
    // ---------------------------------------------------------------------

    private fun simulateAuthResponseJson(
        authEntriesBase64: List<String>,
        sorobanDataBase64: String
    ): String {
        val entries = authEntriesBase64.joinToString(",") { "\"$it\"" }
        return """
        {
          "jsonrpc": "2.0",
          "id": "test-id",
          "result": {
            "transactionData": "$sorobanDataBase64",
            "minResourceFee": 100,
            "results": [ { "auth": [$entries], "xdr": null } ],
            "latestLedger": 100
          }
        }
        """.trimIndent()
    }

    private fun simulateValueResponseJson(
        valueXdrBase64: String,
        sorobanDataBase64: String
    ): String = """
    {
      "jsonrpc": "2.0",
      "id": "test-id",
      "result": {
        "transactionData": "$sorobanDataBase64",
        "minResourceFee": 100,
        "results": [ { "auth": [], "xdr": "$valueXdrBase64" } ],
        "latestLedger": 100
      }
    }
    """.trimIndent()

    /** A simulation response carrying no `results` member at all. */
    private fun simulateWithoutResultsJson(sorobanDataBase64: String): String = """
    {
      "jsonrpc": "2.0",
      "id": "test-id",
      "result": {
        "transactionData": "$sorobanDataBase64",
        "minResourceFee": 100,
        "latestLedger": 100
      }
    }
    """.trimIndent()

    /** A simulation response whose `results` array is empty. */
    private fun simulateEmptyResultsJson(sorobanDataBase64: String): String = """
    {
      "jsonrpc": "2.0",
      "id": "test-id",
      "result": {
        "transactionData": "$sorobanDataBase64",
        "minResourceFee": 100,
        "results": [],
        "latestLedger": 100
      }
    }
    """.trimIndent()

    /** A simulation result that omits both `auth` and `xdr`. */
    private fun simulateResultWithoutAuthJson(sorobanDataBase64: String): String = """
    {
      "jsonrpc": "2.0",
      "id": "test-id",
      "result": {
        "transactionData": "$sorobanDataBase64",
        "minResourceFee": 100,
        "results": [ {} ],
        "latestLedger": 100
      }
    }
    """.trimIndent()

    private fun sendTransactionResponseJson(
        status: String,
        hash: String? = null,
        errorResultXdr: String? = null
    ): String {
        val hashField = hash?.let { ",\n    \"hash\": \"$it\"" } ?: ""
        val errorField = errorResultXdr?.let { ",\n    \"errorResultXdr\": \"$it\"" } ?: ""
        return """
        {
          "jsonrpc": "2.0",
          "id": "test-id",
          "result": {
            "status": "$status",
            "latestLedger": 1001,
            "latestLedgerCloseTime": 1700000000$hashField$errorField
          }
        }
        """.trimIndent()
    }

    private fun getTransactionResponseJson(
        status: String,
        ledger: Long? = null,
        resultXdr: String? = null
    ): String {
        val ledgerField = ledger?.let { ",\n    \"ledger\": $it" } ?: ""
        val resultField = resultXdr?.let { ",\n    \"resultXdr\": \"$it\"" } ?: ""
        return """
        {
          "jsonrpc": "2.0",
          "id": "test-id",
          "result": {
            "status": "$status",
            "latestLedger": 1002,
            "latestLedgerCloseTime": 1700000010,
            "oldestLedger": 900,
            "oldestLedgerCloseTime": 1699990000$ledgerField$resultField
          }
        }
        """.trimIndent()
    }

    // ---------------------------------------------------------------------
    // Context rule ScVal fixtures
    // ---------------------------------------------------------------------

    private fun externalSignerScVal(verifierAddress: String, keyData: ByteArray): SCValXdr =
        Scv.toVec(
            listOf(
                Scv.toSymbol("External"),
                Scv.toAddress(Address(verifierAddress).toSCAddress()),
                Scv.toBytes(keyData)
            )
        )

    /** A complete on-chain context rule with a `Default` context type, matching every invocation. */
    private fun defaultContextRuleScVal(
        id: UInt,
        signers: List<SCValXdr> = emptyList()
    ): SCValXdr {
        val fields = listOf(
            "context_type" to Scv.toVec(listOf(Scv.toSymbol("Default"))),
            "id" to Scv.toUint32(id),
            "name" to Scv.toString("Default rule"),
            "policies" to Scv.toVec(emptyList()),
            "policy_ids" to Scv.toVec(emptyList()),
            "signer_ids" to Scv.toVec(emptyList()),
            "signers" to Scv.toVec(signers),
            "valid_until" to Scv.toVoid()
        )
        return SCValXdr.Map(
            SCMapXdr(fields.map { (key, value) -> SCMapEntryXdr(key = Scv.toSymbol(key), `val` = value) })
        )
    }

    /** Auth entry with source_account (Void) credentials, which submit() passes through unsigned. */
    private fun sourceAccountAuthEntry(): SorobanAuthorizationEntryXdr =
        SorobanAuthorizationEntryXdr(
            credentials = SorobanCredentialsXdr.Void,
            rootInvocation = SorobanAuthorizedInvocationXdr(
                function = SorobanAuthorizedFunctionXdr.ContractFn(
                    InvokeContractArgsXdr(
                        contractAddress = Address(validContractAddress).toSCAddress(),
                        functionName = SCSymbolXdr("noop"),
                        args = emptyList()
                    )
                ),
                subInvocations = emptyList()
            )
        )

    private fun submittedHostFunction(sendBody: String): HostFunctionXdr =
        submittedInvokeOperation(sendBody).hostFunction

    /** The authorization entries carried by the operation of a submitted `sendTransaction` body. */
    private fun submittedAuthEntries(sendBody: String): List<SorobanAuthorizationEntryXdr> =
        submittedInvokeOperation(sendBody).auth

    private fun submittedInvokeOperation(sendBody: String): InvokeHostFunctionOperation {
        val root = Json.parseToJsonElement(sendBody).jsonObject
        val envelope = root["params"]!!.jsonObject["transaction"]!!.jsonPrimitive.content
        val transaction = Transaction.fromEnvelopeXdr(envelope, Network.TESTNET)
        return transaction.operations
            .first { it is InvokeHostFunctionOperation } as InvokeHostFunctionOperation
    }

    /** The JSON-RPC method name of a request body, for asserting which round-trips a flow made. */
    private fun rpcMethodOf(body: String): String =
        Json.parseToJsonElement(body).jsonObject["method"]?.jsonPrimitive?.content ?: ""

    // ========================================================================
    // submit() - Passkey Signing of a Matching Auth Entry
    // ========================================================================

    @Test
    fun contractCall_authEntryMatchesConnectedContract_signsWithPasskeyAndSubmits() = runTest {
        val deployer = KeyPair.random()
        val accountJson = ledgerEntriesResponseJson(buildAccountEntryXdr(deployer).toXdrBase64())
        val sorobanData = buildMinimalSorobanData().toXdrBase64()
        val authEntry = buildAuthEntry(validContractAddress2)
        val simulateAuth = simulateAuthResponseJson(listOf(authEntry.toXdrBase64()), sorobanData)
        val countZero = simulateValueResponseJson(SCValXdr.U32(Uint32Xdr(0u)).toXdrBase64(), sorobanData)
        val txHash = "a1b2c3d4e5f60718293a4b5c6d7e8f900112233445566778899aabbccddeeff0"

        val provider = passkeyProvider()
        val kit = createSigningKit(
            deployer = deployer,
            responses = listOf(
                accountJson,
                simulateAuth,
                latestLedgerResponseJson(latestLedgerSequence),
                accountJson,
                countZero,
                accountJson,
                simulateAuth,
                sendTransactionResponseJson("PENDING", hash = txHash)
            ),
            trailingResponse = getTransactionResponseJson("SUCCESS", ledger = 1001L),
            webauthnProvider = provider,
            withStoredCredential = true
        )

        val observed = mutableListOf<SmartAccountEvent>()
        kit.events.addListener { observed.add(it) }

        val result = kit.transactionOperations.contractCall(
            target = validContractAddress,
            targetFn = "increment",
            resolveContextRuleIds = { _, _ -> listOf(0u) }
        )

        assertTrue(result.success, "Submission must succeed; error=${result.error}")
        assertEquals(txHash, result.hash)
        assertEquals(1001u, result.ledger)
        assertNull(result.error)

        assertEquals(1, provider.authenticateCallCount, "The matching entry must be signed exactly once")

        // The WebAuthn challenge is the auth digest binding the resolved rule IDs to the
        // payload hash built from the entry, the network and the expiration ledger.
        val expectedDigest = SmartAccountAuth.buildAuthDigest(
            SmartAccountAuth.buildAuthPayloadHash(
                entry = authEntry,
                expirationLedger = expirationLedger,
                networkPassphrase = Network.TESTNET.networkPassphrase
            ),
            listOf(0u)
        )
        assertContentEquals(expectedDigest, provider.lastAuthenticateChallenge)

        val allowCredentials = assertNotNull(provider.lastAuthenticateAllowCredentials)
        assertEquals(1, allowCredentials.size)
        assertContentEquals(credentialIdBytes, allowCredentials[0].id)
        assertEquals(
            listOf("internal"),
            allowCredentials[0].transports,
            "Transport hints must come from the stored credential"
        )

        val signedEvent = observed.filterIsInstance<SmartAccountEvent.TransactionSigned>().single()
        assertEquals(validContractAddress2, signedEvent.contractId)
        assertEquals(credentialId, signedEvent.credentialId)

        val submittedEvent = observed.filterIsInstance<SmartAccountEvent.TransactionSubmitted>().single()
        assertEquals(txHash, submittedEvent.hash)
        assertTrue(submittedEvent.success)
    }

    @Test
    fun contractCall_matchingAuthEntryWithoutWebAuthnProvider_throwsInvalidInput() = runTest {
        val deployer = KeyPair.random()
        val accountJson = ledgerEntriesResponseJson(buildAccountEntryXdr(deployer).toXdrBase64())
        val sorobanData = buildMinimalSorobanData().toXdrBase64()
        val simulateAuth = simulateAuthResponseJson(
            listOf(buildAuthEntry(validContractAddress2).toXdrBase64()),
            sorobanData
        )
        val countZero = simulateValueResponseJson(SCValXdr.U32(Uint32Xdr(0u)).toXdrBase64(), sorobanData)

        val kit = createSigningKit(
            deployer = deployer,
            responses = listOf(
                accountJson,
                simulateAuth,
                latestLedgerResponseJson(latestLedgerSequence),
                accountJson,
                countZero
            ),
            webauthnProvider = null,
            withStoredCredential = true
        )

        val ex = assertFailsWith<ValidationException.InvalidInput> {
            kit.transactionOperations.contractCall(
                target = validContractAddress,
                targetFn = "increment",
                resolveContextRuleIds = { _, _ -> listOf(0u) }
            )
        }
        assertTrue(
            ex.message.contains("webauthnProvider"),
            "Exception must name the missing provider; got: ${ex.message}"
        )
    }

    @Test
    fun contractCall_credentialIdNotBase64Url_throwsCredentialInvalid() = runTest {
        val deployer = KeyPair.random()
        val accountJson = ledgerEntriesResponseJson(buildAccountEntryXdr(deployer).toXdrBase64())
        val sorobanData = buildMinimalSorobanData().toXdrBase64()
        val simulateAuth = simulateAuthResponseJson(
            listOf(buildAuthEntry(validContractAddress2).toXdrBase64()),
            sorobanData
        )
        val countZero = simulateValueResponseJson(SCValXdr.U32(Uint32Xdr(0u)).toXdrBase64(), sorobanData)
        val malformedCredentialId = "not a base64url id!"

        val kit = createSigningKit(
            deployer = deployer,
            responses = listOf(
                accountJson,
                simulateAuth,
                latestLedgerResponseJson(latestLedgerSequence),
                accountJson,
                countZero
            ),
            webauthnProvider = passkeyProvider(),
            connectedCredentialId = malformedCredentialId
        )

        val ex = assertFailsWith<CredentialException.Invalid> {
            kit.transactionOperations.contractCall(
                target = validContractAddress,
                targetFn = "increment",
                resolveContextRuleIds = { _, _ -> listOf(0u) }
            )
        }
        assertTrue(
            ex.message.contains(malformedCredentialId),
            "Exception must quote the undecodable credential ID; got: ${ex.message}"
        )
    }

    @Test
    fun contractCall_credentialMissingFromStorage_resolvesKeyDataFromContextRules() = runTest {
        val deployer = KeyPair.random()
        val accountJson = ledgerEntriesResponseJson(buildAccountEntryXdr(deployer).toXdrBase64())
        val sorobanData = buildMinimalSorobanData().toXdrBase64()
        val simulateAuth = simulateAuthResponseJson(
            listOf(buildAuthEntry(validContractAddress2).toXdrBase64()),
            sorobanData
        )
        val countZero = simulateValueResponseJson(SCValXdr.U32(Uint32Xdr(0u)).toXdrBase64(), sorobanData)
        val countOne = simulateValueResponseJson(SCValXdr.U32(Uint32Xdr(1u)).toXdrBase64(), sorobanData)

        // The on-chain rule holds the connected passkey as an External signer whose key data
        // is the public key followed by the credential ID.
        val onChainKeyData = storedPublicKey + credentialIdBytes
        val ruleJson = simulateValueResponseJson(
            defaultContextRuleScVal(
                id = 0u,
                signers = listOf(externalSignerScVal(validContractAddress, onChainKeyData))
            ).toXdrBase64(),
            sorobanData
        )
        val txHash = "b1b2c3d4e5f60718293a4b5c6d7e8f900112233445566778899aabbccddeeff0"

        val provider = passkeyProvider()
        val kit = createSigningKit(
            deployer = deployer,
            responses = listOf(
                accountJson,
                simulateAuth,
                latestLedgerResponseJson(latestLedgerSequence),
                accountJson,
                countZero,
                accountJson,
                countOne,
                accountJson,
                ruleJson,
                accountJson,
                simulateAuth,
                sendTransactionResponseJson("PENDING", hash = txHash)
            ),
            trailingResponse = getTransactionResponseJson("SUCCESS", ledger = 1001L),
            webauthnProvider = provider,
            withStoredCredential = false
        )

        val result = kit.transactionOperations.contractCall(
            target = validContractAddress,
            targetFn = "increment",
            resolveContextRuleIds = { _, _ -> listOf(0u) }
        )

        assertTrue(result.success, "Submission must succeed; error=${result.error}")
        assertEquals(txHash, result.hash)
        assertEquals(1, provider.authenticateCallCount)

        val allowCredentials = assertNotNull(provider.lastAuthenticateAllowCredentials)
        assertContentEquals(credentialIdBytes, allowCredentials[0].id)
        assertNull(
            allowCredentials[0].transports,
            "Without a stored credential there are no transport hints to pass"
        )
    }

    @Test
    fun contractCall_credentialAbsentFromStorageAndChain_throwsCredentialNotFound() = runTest {
        val deployer = KeyPair.random()
        val accountJson = ledgerEntriesResponseJson(buildAccountEntryXdr(deployer).toXdrBase64())
        val sorobanData = buildMinimalSorobanData().toXdrBase64()
        val simulateAuth = simulateAuthResponseJson(
            listOf(buildAuthEntry(validContractAddress2).toXdrBase64()),
            sorobanData
        )
        val countZero = simulateValueResponseJson(SCValXdr.U32(Uint32Xdr(0u)).toXdrBase64(), sorobanData)
        val countOne = simulateValueResponseJson(SCValXdr.U32(Uint32Xdr(1u)).toXdrBase64(), sorobanData)

        // The only rule on-chain holds an External signer for a different credential, so the
        // key-data scan runs to completion without a match.
        val otherKeyData = storedPublicKey + ByteArray(16) { (it + 100).toByte() }
        val ruleJson = simulateValueResponseJson(
            defaultContextRuleScVal(
                id = 0u,
                signers = listOf(externalSignerScVal(validContractAddress, otherKeyData))
            ).toXdrBase64(),
            sorobanData
        )

        val kit = createSigningKit(
            deployer = deployer,
            responses = listOf(
                accountJson,
                simulateAuth,
                latestLedgerResponseJson(latestLedgerSequence),
                accountJson,
                countZero,
                accountJson,
                countOne,
                accountJson,
                ruleJson
            ),
            webauthnProvider = passkeyProvider(),
            withStoredCredential = false
        )

        val ex = assertFailsWith<CredentialException.NotFound> {
            kit.transactionOperations.contractCall(
                target = validContractAddress,
                targetFn = "increment",
                resolveContextRuleIds = { _, _ -> listOf(0u) }
            )
        }
        assertTrue(
            ex.message.contains(credentialId),
            "Exception must identify the credential that could not be resolved; got: ${ex.message}"
        )
    }

    @Test
    fun contractCall_withoutResolver_derivesContextRuleIdsFromChain() = runTest {
        val deployer = KeyPair.random()
        val accountJson = ledgerEntriesResponseJson(buildAccountEntryXdr(deployer).toXdrBase64())
        val sorobanData = buildMinimalSorobanData().toXdrBase64()
        val authEntry = buildAuthEntry(validContractAddress2)
        val simulateAuth = simulateAuthResponseJson(listOf(authEntry.toXdrBase64()), sorobanData)
        val countOne = simulateValueResponseJson(SCValXdr.U32(Uint32Xdr(1u)).toXdrBase64(), sorobanData)
        val ruleId = 7u
        val ruleJson = simulateValueResponseJson(
            defaultContextRuleScVal(id = ruleId).toXdrBase64(),
            sorobanData
        )
        val txHash = "c1b2c3d4e5f60718293a4b5c6d7e8f900112233445566778899aabbccddeeff0"

        val provider = passkeyProvider()
        val kit = createSigningKit(
            deployer = deployer,
            responses = listOf(
                accountJson,
                simulateAuth,
                latestLedgerResponseJson(latestLedgerSequence),
                accountJson,
                countOne,
                accountJson,
                ruleJson,
                accountJson,
                simulateAuth,
                sendTransactionResponseJson("PENDING", hash = txHash)
            ),
            trailingResponse = getTransactionResponseJson("SUCCESS", ledger = 1001L),
            webauthnProvider = provider,
            withStoredCredential = true
        )

        // No resolveContextRuleIds callback: the rule ID is resolved from the single
        // on-chain Default rule, which matches every invocation context.
        val result = kit.transactionOperations.contractCall(
            target = validContractAddress,
            targetFn = "increment"
        )

        assertTrue(result.success, "Submission must succeed; error=${result.error}")
        assertEquals(txHash, result.hash)

        val expectedDigest = SmartAccountAuth.buildAuthDigest(
            SmartAccountAuth.buildAuthPayloadHash(
                entry = authEntry,
                expirationLedger = expirationLedger,
                networkPassphrase = Network.TESTNET.networkPassphrase
            ),
            listOf(ruleId)
        )
        assertContentEquals(
            expectedDigest,
            provider.lastAuthenticateChallenge,
            "Challenge must bind the auto-resolved rule ID $ruleId"
        )
    }

    @Test
    fun contractCall_withoutResolverAndNoMatchingRule_throwsInvalidInput() = runTest {
        val deployer = KeyPair.random()
        val accountJson = ledgerEntriesResponseJson(buildAccountEntryXdr(deployer).toXdrBase64())
        val sorobanData = buildMinimalSorobanData().toXdrBase64()
        val simulateAuth = simulateAuthResponseJson(
            listOf(buildAuthEntry(validContractAddress2).toXdrBase64()),
            sorobanData
        )
        val countZero = simulateValueResponseJson(SCValXdr.U32(Uint32Xdr(0u)).toXdrBase64(), sorobanData)

        val kit = createSigningKit(
            deployer = deployer,
            responses = listOf(
                accountJson,
                simulateAuth,
                latestLedgerResponseJson(latestLedgerSequence),
                accountJson,
                countZero
            ),
            webauthnProvider = passkeyProvider(),
            withStoredCredential = true
        )

        val ex = assertFailsWith<ValidationException.InvalidInput> {
            kit.transactionOperations.contractCall(
                target = validContractAddress,
                targetFn = "increment"
            )
        }
        assertTrue(
            ex.message.contains("No context rule matches"),
            "Exception must explain that no rule covers the invocation; got: ${ex.message}"
        )
    }

    // ========================================================================
    // submit() - Entries That Are Not Signed by the Connected Passkey
    // ========================================================================

    @Test
    fun contractCall_sourceAccountAuthEntry_passesEntryThroughUnsigned() = runTest {
        val deployer = KeyPair.random()
        val accountJson = ledgerEntriesResponseJson(buildAccountEntryXdr(deployer).toXdrBase64())
        val sorobanData = buildMinimalSorobanData().toXdrBase64()
        val simulateAuth = simulateAuthResponseJson(
            listOf(sourceAccountAuthEntry().toXdrBase64()),
            sorobanData
        )
        val countZero = simulateValueResponseJson(SCValXdr.U32(Uint32Xdr(0u)).toXdrBase64(), sorobanData)
        val txHash = "d1b2c3d4e5f60718293a4b5c6d7e8f900112233445566778899aabbccddeeff0"

        var sendBody: String? = null
        val provider = passkeyProvider()
        val kit = createSigningKit(
            deployer = deployer,
            responses = listOf(
                accountJson,
                simulateAuth,
                latestLedgerResponseJson(latestLedgerSequence),
                accountJson,
                countZero,
                accountJson,
                simulateAuth,
                sendTransactionResponseJson("PENDING", hash = txHash)
            ),
            trailingResponse = getTransactionResponseJson("SUCCESS", ledger = 1001L),
            webauthnProvider = provider,
            withStoredCredential = true,
            onRequest = { index, body -> if (index == 7) sendBody = body }
        )

        val observed = mutableListOf<SmartAccountEvent>()
        kit.events.addListener { observed.add(it) }

        val result = kit.transactionOperations.contractCall(
            target = validContractAddress,
            targetFn = "increment",
            resolveContextRuleIds = { _, _ -> listOf(0u) }
        )

        assertTrue(result.success, "Submission must succeed; error=${result.error}")
        assertEquals(
            0,
            provider.authenticateCallCount,
            "A source_account entry carries no smart-account address and must not be signed by the passkey"
        )
        val signedEvent = observed.filterIsInstance<SmartAccountEvent.TransactionSigned>().single()
        assertNull(
            signedEvent.credentialId,
            "The entry is passed through unsigned, so no credential signed this transaction"
        )
        // Source_account auth forces the signed-envelope path, so the submitted transaction
        // carries the deployer's signature.
        val envelope = Json.parseToJsonElement(assertNotNull(sendBody)).jsonObject["params"]!!
            .jsonObject["transaction"]!!.jsonPrimitive.content
        val submitted = Transaction.fromEnvelopeXdr(envelope, Network.TESTNET)
        assertEquals(1, submitted.signatures.size, "The envelope must be signed for RPC submission")
    }

    @Test
    fun contractCall_simulationReturnsNoAuthEntries_emitsSignedEventWithoutCredential() = runTest {
        val deployer = KeyPair.random()
        val accountJson = ledgerEntriesResponseJson(buildAccountEntryXdr(deployer).toXdrBase64())
        val sorobanData = buildMinimalSorobanData().toXdrBase64()
        val simulateNoAuth = simulateAuthResponseJson(emptyList(), sorobanData)
        val txHash = "e1b2c3d4e5f60718293a4b5c6d7e8f900112233445566778899aabbccddeeff0"

        val provider = passkeyProvider()
        val kit = createSigningKit(
            deployer = deployer,
            responses = listOf(
                accountJson,
                simulateNoAuth,
                accountJson,
                simulateNoAuth,
                sendTransactionResponseJson("PENDING", hash = txHash)
            ),
            trailingResponse = getTransactionResponseJson("SUCCESS", ledger = 1001L),
            webauthnProvider = provider,
            withStoredCredential = true
        )

        val observed = mutableListOf<SmartAccountEvent>()
        kit.events.addListener { observed.add(it) }

        val result = kit.transactionOperations.contractCall(
            target = validContractAddress,
            targetFn = "increment"
        )

        assertTrue(result.success, "Submission must succeed; error=${result.error}")
        assertEquals(0, provider.authenticateCallCount, "Nothing to sign without auth entries")

        val signedEvent = observed.filterIsInstance<SmartAccountEvent.TransactionSigned>().single()
        assertNull(
            signedEvent.credentialId,
            "No credential participated, so the signed event must not name one"
        )
        assertEquals(validContractAddress2, signedEvent.contractId)
    }

    @Test
    fun contractCall_simulationOmitsResults_treatedAsNoAuthEntries() = runTest {
        val deployer = KeyPair.random()
        val accountJson = ledgerEntriesResponseJson(buildAccountEntryXdr(deployer).toXdrBase64())
        val sorobanData = buildMinimalSorobanData().toXdrBase64()
        val txHash = "f1b2c3d4e5f60718293a4b5c6d7e8f900112233445566778899aabbccddeeff0"

        val provider = passkeyProvider()
        val kit = createSigningKit(
            deployer = deployer,
            responses = listOf(
                accountJson,
                simulateWithoutResultsJson(sorobanData),
                accountJson,
                simulateAuthResponseJson(emptyList(), sorobanData),
                sendTransactionResponseJson("PENDING", hash = txHash)
            ),
            trailingResponse = getTransactionResponseJson("SUCCESS", ledger = 1001L),
            webauthnProvider = provider,
            withStoredCredential = true
        )

        val result = kit.transactionOperations.contractCall(
            target = validContractAddress,
            targetFn = "increment"
        )

        assertTrue(result.success, "A simulation without results yields no auth entries to sign")
        assertEquals(txHash, result.hash)
        assertEquals(0, provider.authenticateCallCount)
    }

    @Test
    fun contractCall_simulationResultsEmpty_treatedAsNoAuthEntries() = runTest {
        val deployer = KeyPair.random()
        val accountJson = ledgerEntriesResponseJson(buildAccountEntryXdr(deployer).toXdrBase64())
        val sorobanData = buildMinimalSorobanData().toXdrBase64()
        val txHash = "01b2c3d4e5f60718293a4b5c6d7e8f900112233445566778899aabbccddeeff0"

        val provider = passkeyProvider()
        val kit = createSigningKit(
            deployer = deployer,
            responses = listOf(
                accountJson,
                simulateEmptyResultsJson(sorobanData),
                accountJson,
                simulateAuthResponseJson(emptyList(), sorobanData),
                sendTransactionResponseJson("PENDING", hash = txHash)
            ),
            trailingResponse = getTransactionResponseJson("SUCCESS", ledger = 1001L),
            webauthnProvider = provider,
            withStoredCredential = true
        )

        val result = kit.transactionOperations.contractCall(
            target = validContractAddress,
            targetFn = "increment"
        )

        assertTrue(result.success, "An empty results array yields no auth entries to sign")
        assertEquals(0, provider.authenticateCallCount)
    }

    @Test
    fun contractCall_simulationResultOmitsAuth_treatedAsNoAuthEntries() = runTest {
        val deployer = KeyPair.random()
        val accountJson = ledgerEntriesResponseJson(buildAccountEntryXdr(deployer).toXdrBase64())
        val sorobanData = buildMinimalSorobanData().toXdrBase64()
        val txHash = "11b2c3d4e5f60718293a4b5c6d7e8f900112233445566778899aabbccddeeff0"

        val provider = passkeyProvider()
        val kit = createSigningKit(
            deployer = deployer,
            responses = listOf(
                accountJson,
                simulateResultWithoutAuthJson(sorobanData),
                accountJson,
                simulateAuthResponseJson(emptyList(), sorobanData),
                sendTransactionResponseJson("PENDING", hash = txHash)
            ),
            trailingResponse = getTransactionResponseJson("SUCCESS", ledger = 1001L),
            webauthnProvider = provider,
            withStoredCredential = true
        )

        val result = kit.transactionOperations.contractCall(
            target = validContractAddress,
            targetFn = "increment"
        )

        assertTrue(result.success, "A result without an auth member yields no auth entries to sign")
        assertEquals(0, provider.authenticateCallCount)
    }

    // ========================================================================
    // submit() - Re-Simulation and Submission Method
    // ========================================================================

    @Test
    fun contractCall_initialSimulationFails_throwsSimulationFailedBeforeSigning() = runTest {
        // The first simulation is what produces the auth entries, so its failure aborts the call
        // before any passkey prompt.
        val deployer = KeyPair.random()
        val accountJson = ledgerEntriesResponseJson(buildAccountEntryXdr(deployer).toXdrBase64())

        val provider = passkeyProvider()
        val kit = createSigningKit(
            deployer = deployer,
            responses = listOf(
                accountJson,
                simulateErrorResponseJson("contract not found")
            ),
            webauthnProvider = provider,
            withStoredCredential = true
        )

        val ex = assertFailsWith<TransactionException.SimulationFailed> {
            kit.transactionOperations.contractCall(
                target = validContractAddress,
                targetFn = "increment"
            )
        }
        assertTrue(
            ex.message.contains("Simulation error") && ex.message.contains("contract not found"),
            "Failure must carry the RPC error detail; got: ${ex.message}"
        )
        assertFalse(
            ex.message.contains("Re-simulation"),
            "The failure belongs to the first simulation, not the re-simulation; got: ${ex.message}"
        )
        assertEquals(0, provider.authenticateCallCount, "No passkey prompt without auth entries to sign")
    }

    @Test
    fun contractCall_reSimulationFails_throwsSimulationFailed() = runTest {
        val deployer = KeyPair.random()
        val accountJson = ledgerEntriesResponseJson(buildAccountEntryXdr(deployer).toXdrBase64())
        val sorobanData = buildMinimalSorobanData().toXdrBase64()

        val kit = createSigningKit(
            deployer = deployer,
            responses = listOf(
                accountJson,
                simulateAuthResponseJson(emptyList(), sorobanData),
                accountJson,
                simulateErrorResponseJson("host invocation trapped")
            ),
            webauthnProvider = passkeyProvider(),
            withStoredCredential = true
        )

        val ex = assertFailsWith<TransactionException.SimulationFailed> {
            kit.transactionOperations.contractCall(
                target = validContractAddress,
                targetFn = "increment"
            )
        }
        assertTrue(
            ex.message.contains("Re-simulation error"),
            "Failure must be attributed to the re-simulation; got: ${ex.message}"
        )
        assertTrue(
            ex.message.contains("host invocation trapped"),
            "Failure must carry the RPC error detail; got: ${ex.message}"
        )
    }

    @Test
    fun contractCall_forcedRelayerWithoutRelayerConfigured_throwsSubmissionFailed() = runTest {
        val deployer = KeyPair.random()
        val accountJson = ledgerEntriesResponseJson(buildAccountEntryXdr(deployer).toXdrBase64())
        val sorobanData = buildMinimalSorobanData().toXdrBase64()
        val simulateNoAuth = simulateAuthResponseJson(emptyList(), sorobanData)

        val kit = createSigningKit(
            deployer = deployer,
            responses = listOf(accountJson, simulateNoAuth, accountJson, simulateNoAuth),
            webauthnProvider = passkeyProvider(),
            withStoredCredential = true
        )

        val ex = assertFailsWith<TransactionException.SubmissionFailed> {
            kit.transactionOperations.contractCall(
                target = validContractAddress,
                targetFn = "increment",
                forceMethod = SubmissionMethod.RELAYER
            )
        }
        assertTrue(
            ex.message.contains("Relayer is not configured"),
            "Forcing the relayer without one configured must say so; got: ${ex.message}"
        )
    }

    // ========================================================================
    // RPC Submission Outcomes
    //
    // These drive executeAndSubmit() through a simulation that produces no auth
    // entries, so the run reaches sendTransaction in four RPC round-trips and the
    // submission outcome is the only variable.
    // ========================================================================

    /**
     * Builds a kit whose first four RPC calls carry executeAndSubmit() to the submission
     * step, then answers sendTransaction with [sendResponse] and every later call with
     * [pollResponse].
     */
    private suspend fun createSubmissionKit(
        deployer: KeyPair,
        sendResponse: String,
        pollResponse: String? = null
    ): OZSmartAccountKit {
        val accountJson = ledgerEntriesResponseJson(buildAccountEntryXdr(deployer).toXdrBase64())
        val simulateNoAuth = simulateAuthResponseJson(emptyList(), buildMinimalSorobanData().toXdrBase64())
        return createSigningKit(
            deployer = deployer,
            responses = listOf(accountJson, simulateNoAuth, accountJson, simulateNoAuth, sendResponse),
            trailingResponse = pollResponse,
            webauthnProvider = passkeyProvider(),
            withStoredCredential = true
        )
    }

    @Test
    fun executeAndSubmit_networkRejectsTransaction_returnsFailureWithErrorResult() = runTest {
        val errorResultXdr = "AAAAAAAAAGT////9AAAAAA=="
        val txHash = "21b2c3d4e5f60718293a4b5c6d7e8f900112233445566778899aabbccddeeff0"
        val kit = createSubmissionKit(
            deployer = KeyPair.random(),
            sendResponse = sendTransactionResponseJson(
                status = "ERROR",
                hash = txHash,
                errorResultXdr = errorResultXdr
            )
        )

        val result = kit.transactionOperations.executeAndSubmit(
            target = validContractAddress,
            targetFn = "increment"
        )

        assertFalse(result.success)
        assertEquals(txHash, result.hash)
        assertEquals(errorResultXdr, result.error, "The rejection XDR must be surfaced verbatim")
        assertNull(result.ledger)
    }

    @Test
    fun executeAndSubmit_networkRejectsWithoutDetail_returnsDefaultRejectionMessage() = runTest {
        val kit = createSubmissionKit(
            deployer = KeyPair.random(),
            sendResponse = sendTransactionResponseJson(status = "ERROR")
        )

        val result = kit.transactionOperations.executeAndSubmit(
            target = validContractAddress,
            targetFn = "increment"
        )

        assertFalse(result.success)
        assertEquals("", result.hash, "A rejection without a hash reports an empty hash")
        assertEquals("Transaction rejected by network", result.error)
    }

    @Test
    fun executeAndSubmit_networkCongested_returnsRetryLaterFailure() = runTest {
        val txHash = "31b2c3d4e5f60718293a4b5c6d7e8f900112233445566778899aabbccddeeff0"
        val kit = createSubmissionKit(
            deployer = KeyPair.random(),
            sendResponse = sendTransactionResponseJson(status = "TRY_AGAIN_LATER", hash = txHash)
        )

        val result = kit.transactionOperations.executeAndSubmit(
            target = validContractAddress,
            targetFn = "increment"
        )

        assertFalse(result.success)
        assertEquals(txHash, result.hash)
        assertEquals("Network is congested. Try again later.", result.error)
    }

    @Test
    fun executeAndSubmit_pendingSubmissionWithoutHash_throwsSubmissionFailed() = runTest {
        val kit = createSubmissionKit(
            deployer = KeyPair.random(),
            sendResponse = sendTransactionResponseJson(status = "PENDING")
        )

        val ex = assertFailsWith<TransactionException.SubmissionFailed> {
            kit.transactionOperations.executeAndSubmit(
                target = validContractAddress,
                targetFn = "increment"
            )
        }
        assertTrue(
            ex.message.contains("No transaction hash returned from send result"),
            "A pending submission with no hash cannot be polled; got: ${ex.message}"
        )
    }

    @Test
    fun executeAndSubmit_transactionFailsOnChain_returnsFailureWithResultXdr() = runTest {
        val txHash = "41b2c3d4e5f60718293a4b5c6d7e8f900112233445566778899aabbccddeeff0"
        val resultXdr = "AAAAAAAAAGT////7AAAAAA=="
        val kit = createSubmissionKit(
            deployer = KeyPair.random(),
            sendResponse = sendTransactionResponseJson(status = "PENDING", hash = txHash),
            pollResponse = getTransactionResponseJson("FAILED", ledger = 1005L, resultXdr = resultXdr)
        )

        val result = kit.transactionOperations.executeAndSubmit(
            target = validContractAddress,
            targetFn = "increment"
        )

        assertFalse(result.success)
        assertEquals(txHash, result.hash)
        assertEquals(1005u, result.ledger, "A failed transaction still reports its ledger")
        assertEquals(resultXdr, result.error)
    }

    @Test
    fun executeAndSubmit_transactionFailsOnChainWithoutResultXdr_returnsDefaultFailureMessage() = runTest {
        val txHash = "51b2c3d4e5f60718293a4b5c6d7e8f900112233445566778899aabbccddeeff0"
        val kit = createSubmissionKit(
            deployer = KeyPair.random(),
            sendResponse = sendTransactionResponseJson(status = "PENDING", hash = txHash),
            pollResponse = getTransactionResponseJson("FAILED", ledger = 1005L)
        )

        val result = kit.transactionOperations.executeAndSubmit(
            target = validContractAddress,
            targetFn = "increment"
        )

        assertFalse(result.success)
        assertEquals("Transaction failed on-chain", result.error)
    }

    @Test
    fun executeAndSubmit_confirmationWithoutLedger_returnsSuccessWithNullLedger() = runTest {
        val txHash = "61b2c3d4e5f60718293a4b5c6d7e8f900112233445566778899aabbccddeeff0"
        val kit = createSubmissionKit(
            deployer = KeyPair.random(),
            sendResponse = sendTransactionResponseJson(status = "PENDING", hash = txHash),
            pollResponse = getTransactionResponseJson("SUCCESS")
        )

        val result = kit.transactionOperations.executeAndSubmit(
            target = validContractAddress,
            targetFn = "increment"
        )

        assertTrue(result.success, "A SUCCESS status without a ledger is still a success")
        assertEquals(txHash, result.hash)
        assertNull(result.ledger)
        assertNull(result.error)
    }

    // ========================================================================
    // submitMultiSignerTransaction()
    // ========================================================================

    @Test
    fun submitMultiSignerTransaction_withoutForcedMethod_submitsViaRpcAndConfirms() = runTest {
        val deployer = KeyPair.random()
        val accountJson = ledgerEntriesResponseJson(buildAccountEntryXdr(deployer).toXdrBase64())
        val sorobanData = buildMinimalSorobanData().toXdrBase64()
        val txHash = "71b2c3d4e5f60718293a4b5c6d7e8f900112233445566778899aabbccddeeff0"

        val kit = createSigningKit(
            deployer = deployer,
            responses = listOf(
                accountJson,
                sendTransactionResponseJson(status = "PENDING", hash = txHash)
            ),
            trailingResponse = getTransactionResponseJson("SUCCESS", ledger = 1001L),
            webauthnProvider = passkeyProvider(),
            withStoredCredential = true
        )

        val hostFunction = HostFunctionXdr.InvokeContract(
            InvokeContractArgsXdr(
                contractAddress = Address(validContractAddress).toSCAddress(),
                functionName = SCSymbolXdr("increment"),
                args = emptyList()
            )
        )
        val account = kit.sorobanServer.getAccount(deployer.getAccountId())
        val signedTransaction = TransactionBuilder(account, Network.TESTNET)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .addOperation(InvokeHostFunctionOperation(hostFunction, emptyList()))
            .addMemo(MemoNone)
            .setTimeout(30L)
            .build()
        val simulation = SimulateTransactionResponse(
            transactionData = sorobanData,
            minResourceFee = 100L,
            results = listOf(
                SimulateTransactionResponse.SimulateHostFunctionResult(auth = emptyList(), xdr = null)
            ),
            latestLedger = 100L
        )

        val observed = mutableListOf<SmartAccountEvent>()
        kit.events.addListener { observed.add(it) }

        // No relayer is configured and no method is forced, so the default routing is RPC.
        val result = kit.transactionOperations.submitMultiSignerTransaction(
            hostFunction = hostFunction,
            signedAuthEntries = emptyList(),
            signedTransaction = signedTransaction,
            simulation = simulation
        )

        assertTrue(result.success, "Submission must succeed; error=${result.error}")
        assertEquals(txHash, result.hash)
        assertEquals(1001u, result.ledger)

        val submittedEvent = observed.filterIsInstance<SmartAccountEvent.TransactionSubmitted>().single()
        assertEquals(txHash, submittedEvent.hash)
        assertTrue(submittedEvent.success)
    }

    // ========================================================================
    // simulateAndExtractResult() - Missing Return Values
    // ========================================================================

    @Test
    fun fetchTokenDecimals_simulationOmitsResults_throwsSimulationFailed() = runTest {
        val deployer = KeyPair.random()
        val accountJson = ledgerEntriesResponseJson(buildAccountEntryXdr(deployer).toXdrBase64())
        val sorobanData = buildMinimalSorobanData().toXdrBase64()

        val kit = createSigningKit(
            deployer = deployer,
            responses = listOf(accountJson, simulateWithoutResultsJson(sorobanData))
        )

        val ex = assertFailsWith<TransactionException.SimulationFailed> {
            kit.transactionOperations.fetchTokenDecimals(validContractAddress)
        }
        assertTrue(
            ex.message.contains("No results returned from simulation"),
            "Missing results must be reported as such; got: ${ex.message}"
        )
    }

    @Test
    fun fetchTokenDecimals_simulationResultsEmpty_throwsSimulationFailed() = runTest {
        val deployer = KeyPair.random()
        val accountJson = ledgerEntriesResponseJson(buildAccountEntryXdr(deployer).toXdrBase64())
        val sorobanData = buildMinimalSorobanData().toXdrBase64()

        val kit = createSigningKit(
            deployer = deployer,
            responses = listOf(accountJson, simulateEmptyResultsJson(sorobanData))
        )

        val ex = assertFailsWith<TransactionException.SimulationFailed> {
            kit.transactionOperations.fetchTokenDecimals(validContractAddress)
        }
        assertTrue(
            ex.message.contains("No results returned from simulation"),
            "An empty results array is indistinguishable from none; got: ${ex.message}"
        )
    }

    @Test
    fun fetchTokenDecimals_simulationResultWithoutReturnValue_throwsSimulationFailed() = runTest {
        val deployer = KeyPair.random()
        val accountJson = ledgerEntriesResponseJson(buildAccountEntryXdr(deployer).toXdrBase64())
        val sorobanData = buildMinimalSorobanData().toXdrBase64()

        val kit = createSigningKit(
            deployer = deployer,
            responses = listOf(accountJson, simulateResultWithoutAuthJson(sorobanData))
        )

        val ex = assertFailsWith<TransactionException.SimulationFailed> {
            kit.transactionOperations.fetchTokenDecimals(validContractAddress)
        }
        assertTrue(
            ex.message.contains("No return value in simulation result"),
            "A result without an xdr member carries no return value; got: ${ex.message}"
        )
    }

    // ========================================================================
    // transfer() - End-to-End Base-Unit Scaling
    // ========================================================================

    @Test
    fun transfer_resolvesDecimalsOnChain_submitsScaledTransferInvocation() = runTest {
        val deployer = KeyPair.random()
        val accountJson = ledgerEntriesResponseJson(buildAccountEntryXdr(deployer).toXdrBase64())
        val sorobanData = buildMinimalSorobanData().toXdrBase64()
        val authEntry = buildAuthEntry(validContractAddress2)
        val simulateAuth = simulateAuthResponseJson(listOf(authEntry.toXdrBase64()), sorobanData)
        val decimalsJson = simulateValueResponseJson(
            SCValXdr.U32(Uint32Xdr(7u)).toXdrBase64(),
            sorobanData
        )
        val countOne = simulateValueResponseJson(SCValXdr.U32(Uint32Xdr(1u)).toXdrBase64(), sorobanData)
        val ruleJson = simulateValueResponseJson(defaultContextRuleScVal(id = 3u).toXdrBase64(), sorobanData)
        val txHash = "81b2c3d4e5f60718293a4b5c6d7e8f900112233445566778899aabbccddeeff0"

        var sendBody: String? = null
        val kit = createSigningKit(
            deployer = deployer,
            responses = listOf(
                accountJson,
                decimalsJson,
                accountJson,
                simulateAuth,
                latestLedgerResponseJson(latestLedgerSequence),
                accountJson,
                countOne,
                accountJson,
                ruleJson,
                accountJson,
                simulateAuth,
                sendTransactionResponseJson("PENDING", hash = txHash)
            ),
            trailingResponse = getTransactionResponseJson("SUCCESS", ledger = 1001L),
            webauthnProvider = passkeyProvider(),
            withStoredCredential = true,
            onRequest = { index, body -> if (index == 11) sendBody = body }
        )

        val result = kit.transactionOperations.transfer(
            tokenContract = validContractAddress,
            recipient = validAccountAddress,
            amount = "10.5"
        )

        assertTrue(result.success, "Transfer must succeed; error=${result.error}")
        assertEquals(txHash, result.hash)

        val hostFunction = submittedHostFunction(assertNotNull(sendBody))
        val invocation = (hostFunction as? HostFunctionXdr.InvokeContract)?.value
            ?: throw AssertionError("Submitted host function must invoke a contract, got: $hostFunction")
        assertEquals("transfer", invocation.functionName.value)
        assertEquals(3, invocation.args.size)
        assertEquals(
            validContractAddress2,
            Address.fromSCAddress(Scv.fromAddress(invocation.args[0])).toString(),
            "The smart account is the sender"
        )
        assertEquals(
            validAccountAddress,
            Address.fromSCAddress(Scv.fromAddress(invocation.args[1])).toString()
        )
        assertEquals(
            "105000000",
            Scv.fromInt128(invocation.args[2]).toString(),
            "10.5 at the on-chain scale of 7 decimals is 105000000 base units"
        )
    }

    // ========================================================================
    // fundWallet() - Friendbot Mock Fixtures
    //
    // fundWallet() talks to two independent HTTP endpoints: Friendbot (a plain
    // GET, mocked via FriendBot.httpClientOverride) and the Soroban RPC (mocked
    // via the scripted SorobanServer already used above). Only the reserve
    // amount for the temp keypair's balance changes per test; the funding
    // account's own keypair is never asserted on, since SorobanServer.getAccount
    // trusts the requested address and reads only the sequence number out of the
    // returned ledger entry XDR.
    // ========================================================================

    private fun buildFriendbotClient(engine: MockEngine): HttpClient = HttpClient(engine)

    private fun buildFriendbotSuccessClient(): HttpClient =
        buildFriendbotClient(MockEngine { _ ->
            respond(
                content = ByteReadChannel("""{"successful":true}"""),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        })

    private fun buildFriendbotFailureClient(): HttpClient =
        buildFriendbotClient(MockEngine { _ ->
            respond(
                content = ByteReadChannel("""{"detail":"createAccountAlreadyExist"}"""),
                status = HttpStatusCode.BadRequest,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        })

    /** Fails the test if Friendbot is ever called, for guards that must fire before any HTTP. */
    private fun buildFriendbotFailingIfCalledClient(): HttpClient =
        buildFriendbotClient(MockEngine { _ -> error("no Friendbot request expected") })

    private val friendbotReserveStroops = OZConstants.FRIENDBOT_RESERVE_XLM.toLong() * Util.STROOPS_PER_XLM

    /**
     * Scripted [SorobanServer] whose [MockEngine] runs its request handler on
     * [kotlinx.coroutines.Dispatchers.Unconfined] instead of the engine's default IO dispatcher.
     *
     * fundWallet() waits for RPC visibility via `withTimeoutOrNull` + `delay()`
     * ([pollUntilVisibleToRpc]). Under `runTest`'s virtual clock, a request handler dispatched
     * on a real (non-test) dispatcher -- [buildScriptedMockServer]'s default -- races the
     * "auto-advance to the next scheduled deadline" behavior: the virtual clock can jump straight
     * to the 45s timeout before the real dispatch ever reaches the handler, since the scheduler
     * has no visibility into work happening off its own dispatcher. Running the handler unconfined
     * keeps the whole request on the coroutine already suspended under the test scheduler,
     * eliminating the race. Scoped to fundWallet() tests only, since no other test in this file
     * combines a scripted RPC mock with a `delay()`-based poll.
     */
    private fun buildFundWalletMockServer(
        responses: List<String>,
        trailingResponse: String? = null,
        onRequest: ((index: Int, body: String) -> Unit)? = null
    ): SorobanServer {
        var requestIndex = 0
        val engine = MockEngine(MockEngineConfig().apply {
            dispatcher = Dispatchers.Unconfined
            addHandler { request ->
                val index = requestIndex++
                onRequest?.invoke(index, request.body.toByteArray().decodeToString())
                val body = responses.getOrNull(index)
                    ?: trailingResponse
                    ?: error("Unexpected RPC request at index $index")
                respond(
                    content = ByteReadChannel(body),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        })
        return SorobanServer("https://soroban-testnet.stellar.org", buildMockHttpClient(engine))
    }

    /** Builds a kit connected to [contractId], scripted by [responses] via [buildFundWalletMockServer]. */
    private suspend fun createFundWalletKit(
        deployer: KeyPair,
        responses: List<String>,
        trailingResponse: String? = null,
        contractId: String = validContractAddress2,
        relayerClient: OZRelayerClient? = null,
        onRequest: ((index: Int, body: String) -> Unit)? = null
    ): OZSmartAccountKit {
        val kit = OZSmartAccountKit.createWithServer(
            config = buildSigningConfig(deployer, webauthnProvider = null, storage = InMemoryStorageAdapter()),
            sorobanServer = buildFundWalletMockServer(responses, trailingResponse, onRequest),
            relayerClient = relayerClient
        )
        kit.setConnectedState(credentialId, contractId)
        return kit
    }

    // ========================================================================
    // fundWallet() - Not Connected / Invalid Address Guards
    //
    // Both guards must fire before any HTTP call (Friendbot or RPC), so both the
    // RPC server and the Friendbot client fail the test if invoked.
    // ========================================================================

    @Test
    fun fundWallet_notConnected_throwsWithoutAnyHttpCall() = runTest {
        FriendBot.httpClientOverride = buildFriendbotFailingIfCalledClient()
        val kit = createDisconnectedKit()

        val ex = assertFailsWith<WalletException.NotConnected> {
            kit.transactionOperations.fundWallet(nativeTokenContract = validContractAddress)
        }
        assertTrue(ex.message.contains("No wallet connected"))
    }

    @Test
    fun fundWallet_invalidNativeTokenContract_gAddress_throwsBeforeAnyHttpCall() = runTest {
        FriendBot.httpClientOverride = buildFriendbotFailingIfCalledClient()
        val kit = createConnectedKit()

        val ex = assertFailsWith<ValidationException.InvalidAddress> {
            kit.transactionOperations.fundWallet(nativeTokenContract = validAccountAddress)
        }
        assertTrue(
            ex.message.contains("nativeTokenContract"),
            "Exception must name the invalid field; got: ${ex.message}"
        )
    }

    @Test
    fun fundWallet_invalidNativeTokenContract_malformedChecksum_throwsBeforeAnyHttpCall() = runTest {
        FriendBot.httpClientOverride = buildFriendbotFailingIfCalledClient()
        val kit = createConnectedKit()

        val ex = assertFailsWith<ValidationException.InvalidAddress> {
            kit.transactionOperations.fundWallet(nativeTokenContract = malformedContractAddress)
        }
        assertTrue(
            ex.message.contains("nativeTokenContract"),
            "Exception must name the invalid field; got: ${ex.message}"
        )
    }

    // ========================================================================
    // fundWallet() - Friendbot Funding Failure
    // ========================================================================

    @Test
    fun fundWallet_friendbotReturnsNonSuccessStatus_throwsSubmissionFailed() = runTest {
        FriendBot.httpClientOverride = buildFriendbotFailureClient()
        val kit = createConnectedKit()

        val ex = assertFailsWith<TransactionException.SubmissionFailed> {
            kit.transactionOperations.fundWallet(nativeTokenContract = validContractAddress)
        }
        assertTrue(ex.message.contains("Friendbot funding failed"))
    }

    // ========================================================================
    // fundWallet() - Temp Account Balance Query and Reserve Check
    //
    // Each of these reaches: Friendbot success, the RPC-visibility probe, the
    // temp account fetch, and the deployer account fetch used by the balance
    // simulation -- then fails on the balance step itself.
    // ========================================================================

    @Test
    fun fundWallet_balanceSimulationReturnsNonI128Value_throwsSubmissionFailed() = runTest {
        FriendBot.httpClientOverride = buildFriendbotSuccessClient()
        val deployer = KeyPair.random()
        val tempAccountJson = ledgerEntriesResponseJson(buildAccountEntryXdr(KeyPair.random()).toXdrBase64())
        val deployerAccountJson = ledgerEntriesResponseJson(buildAccountEntryXdr(deployer).toXdrBase64())
        val sorobanData = buildMinimalSorobanData().toXdrBase64()
        val nonI128Balance = simulateValueResponseJson(SCValXdr.U32(Uint32Xdr(5u)).toXdrBase64(), sorobanData)

        val kit = createFundWalletKit(
            deployer = deployer,
            responses = listOf(
                tempAccountJson,
                tempAccountJson,
                deployerAccountJson,
                nonI128Balance
            )
        )

        val ex = assertFailsWith<TransactionException.SubmissionFailed> {
            kit.transactionOperations.fundWallet(nativeTokenContract = validContractAddress)
        }
        assertTrue(ex.message.contains("Failed to query temp account balance"))
    }

    @Test
    fun fundWallet_balanceBelowReserve_throwsSubmissionFailed() = runTest {
        FriendBot.httpClientOverride = buildFriendbotSuccessClient()
        val deployer = KeyPair.random()
        val tempAccountJson = ledgerEntriesResponseJson(buildAccountEntryXdr(KeyPair.random()).toXdrBase64())
        val deployerAccountJson = ledgerEntriesResponseJson(buildAccountEntryXdr(deployer).toXdrBase64())
        val sorobanData = buildMinimalSorobanData().toXdrBase64()
        // 1 XLM funded, well below the 5 XLM reserve.
        val belowReserveBalance = simulateValueResponseJson(
            Scv.toInt128(BigInteger.fromLong(Util.STROOPS_PER_XLM)).toXdrBase64(),
            sorobanData
        )

        val kit = createFundWalletKit(
            deployer = deployer,
            responses = listOf(
                tempAccountJson,
                tempAccountJson,
                deployerAccountJson,
                belowReserveBalance
            )
        )

        val ex = assertFailsWith<TransactionException.SubmissionFailed> {
            kit.transactionOperations.fundWallet(nativeTokenContract = validContractAddress)
        }
        assertTrue(ex.message.contains("Insufficient balance after Friendbot funding"))
    }

    @Test
    fun fundWallet_balanceExactlyAtReserve_throwsSubmissionFailed() = runTest {
        FriendBot.httpClientOverride = buildFriendbotSuccessClient()
        val deployer = KeyPair.random()
        val tempAccountJson = ledgerEntriesResponseJson(buildAccountEntryXdr(KeyPair.random()).toXdrBase64())
        val deployerAccountJson = ledgerEntriesResponseJson(buildAccountEntryXdr(deployer).toXdrBase64())
        val sorobanData = buildMinimalSorobanData().toXdrBase64()
        // The check is strictly "<=", so a balance exactly at the reserve must still fail:
        // funding the account and immediately draining it back to the reserve is not funding.
        val atReserveBalance = simulateValueResponseJson(
            Scv.toInt128(BigInteger.fromLong(friendbotReserveStroops)).toXdrBase64(),
            sorobanData
        )

        val kit = createFundWalletKit(
            deployer = deployer,
            responses = listOf(
                tempAccountJson,
                tempAccountJson,
                deployerAccountJson,
                atReserveBalance
            )
        )

        val ex = assertFailsWith<TransactionException.SubmissionFailed> {
            kit.transactionOperations.fundWallet(nativeTokenContract = validContractAddress)
        }
        assertTrue(ex.message.contains("Insufficient balance after Friendbot funding"))
    }

    // ========================================================================
    // fundWallet() - Funding Transfer Simulation Failure
    // ========================================================================

    @Test
    fun fundWallet_fundingTransferSimulationFails_throwsSimulationFailed() = runTest {
        FriendBot.httpClientOverride = buildFriendbotSuccessClient()
        val deployer = KeyPair.random()
        val tempAccountJson = ledgerEntriesResponseJson(buildAccountEntryXdr(KeyPair.random()).toXdrBase64())
        val deployerAccountJson = ledgerEntriesResponseJson(buildAccountEntryXdr(deployer).toXdrBase64())
        val sorobanData = buildMinimalSorobanData().toXdrBase64()
        val sufficientBalance = simulateValueResponseJson(
            Scv.toInt128(BigInteger.fromLong(1_000L * Util.STROOPS_PER_XLM)).toXdrBase64(),
            sorobanData
        )

        val kit = createFundWalletKit(
            deployer = deployer,
            responses = listOf(
                tempAccountJson,
                tempAccountJson,
                deployerAccountJson,
                sufficientBalance,
                simulateErrorResponseJson("transfer trapped")
            )
        )

        val ex = assertFailsWith<TransactionException.SimulationFailed> {
            kit.transactionOperations.fundWallet(nativeTokenContract = validContractAddress)
        }
        assertTrue(ex.message.contains("Failed to simulate funding transfer"))
        assertTrue(ex.message.contains("transfer trapped"))
    }

    @Test
    fun fundWallet_reSimulationWithSignedAuthEntriesFails_throwsSimulationFailed() = runTest {
        // The re-simulation runs after the auth entries are signed and is what fixes the resource
        // fees, so its failure is reported separately from the first funding-transfer simulation.
        FriendBot.httpClientOverride = buildFriendbotSuccessClient()
        val deployer = KeyPair.random()
        val tempAccountJson = ledgerEntriesResponseJson(buildAccountEntryXdr(KeyPair.random()).toXdrBase64())
        val deployerAccountJson = ledgerEntriesResponseJson(buildAccountEntryXdr(deployer).toXdrBase64())
        val sorobanData = buildMinimalSorobanData().toXdrBase64()
        val sufficientBalance = simulateValueResponseJson(
            Scv.toInt128(BigInteger.fromLong(1_000L * Util.STROOPS_PER_XLM)).toXdrBase64(),
            sorobanData
        )
        val transferSimulateJson = simulateAuthResponseJson(
            listOf(sourceAccountAuthEntry().toXdrBase64()),
            sorobanData
        )

        val kit = createFundWalletKit(
            deployer = deployer,
            responses = listOf(
                tempAccountJson,
                tempAccountJson,
                deployerAccountJson,
                sufficientBalance,
                transferSimulateJson,
                latestLedgerResponseJson(latestLedgerSequence),
                tempAccountJson,
                simulateErrorResponseJson("resource limit exceeded")
            )
        )

        val ex = assertFailsWith<TransactionException.SimulationFailed> {
            kit.transactionOperations.fundWallet(nativeTokenContract = validContractAddress)
        }
        assertTrue(
            ex.message.contains("Re-simulation error") && ex.message.contains("resource limit exceeded"),
            "got: ${ex.message}"
        )
    }

    @Test
    fun fundWallet_fractionalTransferAmount_returnsTrimmedXlmString() = runTest {
        // 1006.25 XLM funded minus the 5 XLM reserve leaves 1001.25 XLM, whose stroop remainder
        // must be rendered without the trailing zeros of the 7-decimal scale.
        FriendBot.httpClientOverride = buildFriendbotSuccessClient()
        val deployer = KeyPair.random()
        val tempAccountJson = ledgerEntriesResponseJson(buildAccountEntryXdr(KeyPair.random()).toXdrBase64())
        val deployerAccountJson = ledgerEntriesResponseJson(buildAccountEntryXdr(deployer).toXdrBase64())
        val sorobanData = buildMinimalSorobanData().toXdrBase64()

        val fundedBalanceStroops = 1_006L * Util.STROOPS_PER_XLM + 2_500_000L
        val balanceJson = simulateValueResponseJson(
            Scv.toInt128(BigInteger.fromLong(fundedBalanceStroops)).toXdrBase64(),
            sorobanData
        )
        val transferSimulateJson = simulateAuthResponseJson(
            listOf(sourceAccountAuthEntry().toXdrBase64()),
            sorobanData
        )
        val reSimulateJson = simulateAuthResponseJson(emptyList(), sorobanData)
        val txHash = "61b2c3d4e5f60718293a4b5c6d7e8f900112233445566778899aabbccddeeff0"

        val kit = createFundWalletKit(
            deployer = deployer,
            responses = listOf(
                tempAccountJson,
                tempAccountJson,
                deployerAccountJson,
                balanceJson,
                transferSimulateJson,
                latestLedgerResponseJson(latestLedgerSequence),
                tempAccountJson,
                reSimulateJson,
                sendTransactionResponseJson("PENDING", hash = txHash)
            ),
            trailingResponse = getTransactionResponseJson("SUCCESS", ledger = 1001L)
        )

        assertEquals(
            "1001.25",
            kit.transactionOperations.fundWallet(nativeTokenContract = validContractAddress)
        )
    }

    // ========================================================================
    // fundWallet() - Happy Path
    // ========================================================================

    @Test
    fun fundWallet_happyPath_returnsFundedXlmAmount() = runTest {
        FriendBot.httpClientOverride = buildFriendbotSuccessClient()
        val deployer = KeyPair.random()
        val tempAccountJson = ledgerEntriesResponseJson(buildAccountEntryXdr(KeyPair.random()).toXdrBase64())
        val deployerAccountJson = ledgerEntriesResponseJson(buildAccountEntryXdr(deployer).toXdrBase64())
        val sorobanData = buildMinimalSorobanData().toXdrBase64()

        // 1005 XLM funded, 5 XLM held back as reserve -> exactly 1000 XLM transferred.
        val fundedBalanceStroops = 1_005L * Util.STROOPS_PER_XLM
        val balanceJson = simulateValueResponseJson(
            Scv.toInt128(BigInteger.fromLong(fundedBalanceStroops)).toXdrBase64(),
            sorobanData
        )
        // The temp account is a classic keypair, not the smart account, so the auth entry
        // simulation reports source_account (Void) credentials -- the same shape
        // convertAndSignAuthEntries() converts to a signed ADDRESS_V2 credential.
        val transferSimulateJson = simulateAuthResponseJson(
            listOf(sourceAccountAuthEntry().toXdrBase64()),
            sorobanData
        )
        val reSimulateJson = simulateAuthResponseJson(emptyList(), sorobanData)
        val txHash = "91b2c3d4e5f60718293a4b5c6d7e8f900112233445566778899aabbccddeeff0"

        val kit = createFundWalletKit(
            deployer = deployer,
            responses = listOf(
                tempAccountJson,
                tempAccountJson,
                deployerAccountJson,
                balanceJson,
                transferSimulateJson,
                latestLedgerResponseJson(latestLedgerSequence),
                tempAccountJson,
                reSimulateJson,
                sendTransactionResponseJson("PENDING", hash = txHash)
            ),
            trailingResponse = getTransactionResponseJson("SUCCESS", ledger = 1001L)
        )

        val fundedAmount = kit.transactionOperations.fundWallet(nativeTokenContract = validContractAddress)

        assertEquals("1000", fundedAmount)
    }

    // ========================================================================
    // fundWallet() - Auth-Entry Conversion and Re-Signing
    //
    // The funding simulation normally reports source_account (Void) credentials, which are
    // converted to fresh ADDRESS_V2 credentials carrying the temp account address and signed
    // over the address-bound WITH_ADDRESS preimage. An entry that already carries address
    // credentials takes the other branch: the credential arm, address and nonce are preserved
    // and only the expiration and signature are replaced.
    // ========================================================================

    @Test
    fun fundWallet_sourceAccountAuthEntry_convertsToSignedAddressV2Credential() = runTest {
        FriendBot.httpClientOverride = buildFriendbotSuccessClient()
        val deployer = KeyPair.random()
        val tempAccountJson = ledgerEntriesResponseJson(buildAccountEntryXdr(KeyPair.random()).toXdrBase64())
        val deployerAccountJson = ledgerEntriesResponseJson(buildAccountEntryXdr(deployer).toXdrBase64())
        val sorobanData = buildMinimalSorobanData().toXdrBase64()

        val balanceJson = simulateValueResponseJson(
            Scv.toInt128(BigInteger.fromLong(1_005L * Util.STROOPS_PER_XLM)).toXdrBase64(),
            sorobanData
        )
        val sourceEntry = sourceAccountAuthEntry()
        val transferSimulateJson = simulateAuthResponseJson(
            listOf(sourceEntry.toXdrBase64()),
            sorobanData
        )
        val reSimulateJson = simulateAuthResponseJson(emptyList(), sorobanData)
        val txHash = "41b2c3d4e5f60718293a4b5c6d7e8f900112233445566778899aabbccddeeff0"

        var sendBody: String? = null
        val kit = createFundWalletKit(
            deployer = deployer,
            responses = listOf(
                tempAccountJson,
                tempAccountJson,
                deployerAccountJson,
                balanceJson,
                transferSimulateJson,
                latestLedgerResponseJson(latestLedgerSequence),
                tempAccountJson,
                reSimulateJson,
                sendTransactionResponseJson("PENDING", hash = txHash)
            ),
            trailingResponse = getTransactionResponseJson("SUCCESS", ledger = 1001L),
            onRequest = { index, body -> if (index == 8) sendBody = body }
        )

        assertEquals("1000", kit.transactionOperations.fundWallet(nativeTokenContract = validContractAddress))

        val submitted = submittedAuthEntries(assertNotNull(sendBody)).single()
        val credentials = assertIs<SorobanCredentialsXdr.AddressV2>(
            submitted.credentials,
            "the converted entry must carry ADDRESS_V2 credentials"
        ).value
        assertEquals(
            expirationLedger,
            credentials.signatureExpirationLedger.value,
            "the signature expiration is the current ledger plus one hour"
        )

        val (publicKey, signature) = ed25519SignatureFields(credentials.signature)
        assertEquals(
            KeyPair.fromPublicKey(publicKey).getAccountId(),
            Address.fromSCAddress(credentials.address).toString(),
            "the credential address must be the temp account that signed the entry"
        )

        // Ground-truth address-bound preimage, constructed independently of the
        // SDK's hash helper: networkID, nonce, signatureExpirationLedger, address,
        // invocation under ENVELOPE_TYPE_SOROBAN_AUTHORIZATION_WITH_ADDRESS.
        val networkId = getSha256Crypto().hash(Network.TESTNET.networkPassphrase.encodeToByteArray())
        val preimage = HashIDPreimageXdr.SorobanAuthorizationWithAddress(
            HashIDPreimageSorobanAuthorizationWithAddressXdr(
                networkId = HashXdr(networkId),
                nonce = credentials.nonce,
                signatureExpirationLedger = credentials.signatureExpirationLedger,
                address = credentials.address,
                invocation = sourceEntry.rootInvocation
            )
        )
        val writer = XdrWriter()
        preimage.encode(writer)
        val payloadHash = getSha256Crypto().hash(writer.toByteArray())
        assertTrue(
            KeyPair.fromPublicKey(publicKey).verify(payloadHash, signature),
            "the signature must verify over the WITH_ADDRESS preimage carrying the temp account address"
        )
    }

    @Test
    fun fundWallet_addressCredentialAuthEntry_reSignsPreservingAddressAndNonce() = runTest {
        FriendBot.httpClientOverride = buildFriendbotSuccessClient()
        val deployer = KeyPair.random()
        val tempAccountJson = ledgerEntriesResponseJson(buildAccountEntryXdr(KeyPair.random()).toXdrBase64())
        val deployerAccountJson = ledgerEntriesResponseJson(buildAccountEntryXdr(deployer).toXdrBase64())
        val sorobanData = buildMinimalSorobanData().toXdrBase64()

        val balanceJson = simulateValueResponseJson(
            Scv.toInt128(BigInteger.fromLong(1_005L * Util.STROOPS_PER_XLM)).toXdrBase64(),
            sorobanData
        )
        // buildAuthEntry carries Address credentials with nonce 12345 and an unsigned (Void)
        // signature, which is the shape the re-signing branch has to update in place.
        val addressAuthEntry = buildAuthEntry(validContractAddress2)
        val transferSimulateJson = simulateAuthResponseJson(
            listOf(addressAuthEntry.toXdrBase64()),
            sorobanData
        )
        val reSimulateJson = simulateAuthResponseJson(emptyList(), sorobanData)
        val txHash = "51b2c3d4e5f60718293a4b5c6d7e8f900112233445566778899aabbccddeeff0"

        var sendBody: String? = null
        val kit = createFundWalletKit(
            deployer = deployer,
            responses = listOf(
                tempAccountJson,
                tempAccountJson,
                deployerAccountJson,
                balanceJson,
                transferSimulateJson,
                latestLedgerResponseJson(latestLedgerSequence),
                tempAccountJson,
                reSimulateJson,
                sendTransactionResponseJson("PENDING", hash = txHash)
            ),
            trailingResponse = getTransactionResponseJson("SUCCESS", ledger = 1001L),
            onRequest = { index, body -> if (index == 8) sendBody = body }
        )

        assertEquals("1000", kit.transactionOperations.fundWallet(nativeTokenContract = validContractAddress))

        val submitted = submittedAuthEntries(assertNotNull(sendBody)).single()
        val credentials = assertIs<SorobanCredentialsXdr.Address>(submitted.credentials).value
        assertEquals(
            validContractAddress2,
            Address.fromSCAddress(credentials.address).toString(),
            "the existing credential address is preserved rather than replaced by the temp account"
        )
        assertEquals(12345L, credentials.nonce.value, "the existing nonce is preserved")
        assertEquals(
            expirationLedger,
            credentials.signatureExpirationLedger.value,
            "the signature expiration is refreshed to the current ledger plus one hour"
        )

        val (publicKey, signature) = ed25519SignatureFields(credentials.signature)
        val payloadHash = SmartAccountAuth.buildAuthPayloadHash(
            entry = addressAuthEntry,
            expirationLedger = expirationLedger,
            networkPassphrase = Network.TESTNET.networkPassphrase
        )
        assertTrue(
            KeyPair.fromPublicKey(publicKey).verify(payloadHash, signature),
            "the entry must be signed by the advertised public key over the refreshed payload hash"
        )
    }

    // ========================================================================
    // Relayer Submission
    //
    // A configured relayer is auto-detected. With no source_account credentials left on the auth
    // entries, the relayer receives the host function and the signed entries and builds the
    // envelope itself.
    // ========================================================================

    @Test
    fun contractCall_relayerConfigured_submitsHostFunctionAndAuthEntries() = runTest {
        val deployer = KeyPair.random()
        val accountJson = ledgerEntriesResponseJson(buildAccountEntryXdr(deployer).toXdrBase64())
        val sorobanData = buildMinimalSorobanData().toXdrBase64()
        val authEntry = buildAuthEntry(validContractAddress2)
        val simulateAuth = simulateAuthResponseJson(listOf(authEntry.toXdrBase64()), sorobanData)
        val countZero = simulateValueResponseJson(SCValXdr.U32(Uint32Xdr(0u)).toXdrBase64(), sorobanData)
        val txHash = "c1b2c3d4e5f60718293a4b5c6d7e8f900112233445566778899aabbccddeeff0"

        val relayer = MockRelayer(relayerSuccessJson(txHash))
        val rpcMethods = mutableListOf<String>()
        val kit = createSigningKit(
            deployer = deployer,
            responses = listOf(
                accountJson,
                simulateAuth,
                latestLedgerResponseJson(latestLedgerSequence),
                accountJson,
                countZero,
                accountJson,
                simulateAuth
            ),
            trailingResponse = getTransactionResponseJson("SUCCESS", ledger = 1001L),
            webauthnProvider = passkeyProvider(),
            withStoredCredential = true,
            relayerClient = relayer.client,
            onRequest = { _, body -> rpcMethods.add(rpcMethodOf(body)) }
        )

        val observed = mutableListOf<SmartAccountEvent>()
        kit.events.addListener { observed.add(it) }

        val result = kit.transactionOperations.contractCall(
            target = validContractAddress,
            targetFn = "increment",
            resolveContextRuleIds = { _, _ -> listOf(0u) }
        )

        assertTrue(result.success, "Submission must succeed; error=${result.error}")
        assertEquals(txHash, result.hash, "the hash reported by the relayer is polled and returned")
        assertEquals(1001u, result.ledger)
        assertFalse(
            rpcMethods.contains("sendTransaction"),
            "a configured relayer replaces the RPC submit endpoint; saw $rpcMethods"
        )

        val payload = relayer.singleRequestJson()
        assertNull(payload["xdr"], "without source_account credentials the relayer builds the envelope")
        val submittedHostFunction = HostFunctionXdr.fromXdrBase64(
            assertNotNull(payload["func"]).jsonPrimitive.content
        )
        val invocation = assertIs<HostFunctionXdr.InvokeContract>(submittedHostFunction).value
        assertEquals("increment", invocation.functionName.value)
        assertEquals(
            validContractAddress,
            Address.fromSCAddress(invocation.contractAddress).toString()
        )

        val relayedAuth = assertNotNull(payload["auth"]).jsonArray
        assertEquals(1, relayedAuth.size, "the relayer receives the signed auth entries")
        val relayedEntry = SorobanAuthorizationEntryXdr.fromXdrBase64(
            relayedAuth.single().jsonPrimitive.content
        )
        val relayedCredentials = assertIs<SorobanCredentialsXdr.Address>(relayedEntry.credentials).value
        assertEquals(
            expirationLedger,
            relayedCredentials.signatureExpirationLedger.value,
            "the relayed entry carries the passkey signature expiration set during signing"
        )
        assertNotEquals(
            SCValXdr.Void(SCValTypeXdr.SCV_VOID),
            relayedCredentials.signature,
            "the relayed entry must be signed, not the unsigned entry returned by simulation"
        )

        val submittedEvent = observed.filterIsInstance<SmartAccountEvent.TransactionSubmitted>().single()
        assertEquals(txHash, submittedEvent.hash)
        assertTrue(submittedEvent.success)
    }

    @Test
    fun contractCall_relayerRejectsSubmission_returnsFailedResultWithoutPolling() = runTest {
        val deployer = KeyPair.random()
        val accountJson = ledgerEntriesResponseJson(buildAccountEntryXdr(deployer).toXdrBase64())
        val sorobanData = buildMinimalSorobanData().toXdrBase64()
        val simulateAuth = simulateAuthResponseJson(
            listOf(buildAuthEntry(validContractAddress2).toXdrBase64()),
            sorobanData
        )
        val countZero = simulateValueResponseJson(SCValXdr.U32(Uint32Xdr(0u)).toXdrBase64(), sorobanData)

        val relayer = MockRelayer(
            relayerErrorJson("simulation failed at the relayer", RelayerErrorCodes.SIMULATION_FAILED)
        )
        val kit = createSigningKit(
            deployer = deployer,
            responses = listOf(
                accountJson,
                simulateAuth,
                latestLedgerResponseJson(latestLedgerSequence),
                accountJson,
                countZero,
                accountJson,
                simulateAuth
            ),
            webauthnProvider = passkeyProvider(),
            withStoredCredential = true,
            relayerClient = relayer.client
        )

        val observed = mutableListOf<SmartAccountEvent>()
        kit.events.addListener { observed.add(it) }

        val result = kit.transactionOperations.contractCall(
            target = validContractAddress,
            targetFn = "increment",
            resolveContextRuleIds = { _, _ -> listOf(0u) }
        )

        assertFalse(result.success)
        assertEquals(
            "simulation failed at the relayer",
            result.error,
            "the relayer's own error text is surfaced verbatim"
        )
        assertNull(result.hash)
        assertTrue(
            observed.filterIsInstance<SmartAccountEvent.TransactionSubmitted>().isEmpty(),
            "a rejection without a hash emits no submission event"
        )
    }

    @Test
    fun contractCall_relayerAcceptsWithoutHash_returnsFailedResult() = runTest {
        // A relayer that acknowledges the submission but withholds the hash leaves nothing to
        // poll, so the call reports failure rather than a success it cannot confirm.
        val deployer = KeyPair.random()
        val accountJson = ledgerEntriesResponseJson(buildAccountEntryXdr(deployer).toXdrBase64())
        val sorobanData = buildMinimalSorobanData().toXdrBase64()
        val simulateAuth = simulateAuthResponseJson(
            listOf(buildAuthEntry(validContractAddress2).toXdrBase64()),
            sorobanData
        )
        val countZero = simulateValueResponseJson(SCValXdr.U32(Uint32Xdr(0u)).toXdrBase64(), sorobanData)

        val relayer = MockRelayer(relayerSuccessWithoutHashJson())
        val kit = createSigningKit(
            deployer = deployer,
            responses = listOf(
                accountJson,
                simulateAuth,
                latestLedgerResponseJson(latestLedgerSequence),
                accountJson,
                countZero,
                accountJson,
                simulateAuth
            ),
            webauthnProvider = passkeyProvider(),
            withStoredCredential = true,
            relayerClient = relayer.client
        )

        val result = kit.transactionOperations.contractCall(
            target = validContractAddress,
            targetFn = "increment",
            resolveContextRuleIds = { _, _ -> listOf(0u) }
        )

        assertFalse(result.success)
        assertEquals("Relayer submission failed", result.error)
        assertNull(result.hash)
    }

    @Test
    fun submitMultiSignerTransaction_relayerConfigured_routesThroughRelayer() = runTest {
        val deployer = KeyPair.random()
        val accountJson = ledgerEntriesResponseJson(buildAccountEntryXdr(deployer).toXdrBase64())
        val sorobanData = buildMinimalSorobanData().toXdrBase64()
        val txHash = "d1b2c3d4e5f60718293a4b5c6d7e8f900112233445566778899aabbccddeeff0"

        val relayer = MockRelayer(relayerSuccessJson(txHash))
        val rpcMethods = mutableListOf<String>()
        val kit = createSigningKit(
            deployer = deployer,
            responses = listOf(accountJson),
            trailingResponse = getTransactionResponseJson("SUCCESS", ledger = 1001L),
            webauthnProvider = passkeyProvider(),
            withStoredCredential = true,
            relayerClient = relayer.client,
            onRequest = { _, body -> rpcMethods.add(rpcMethodOf(body)) }
        )

        val hostFunction = HostFunctionXdr.InvokeContract(
            InvokeContractArgsXdr(
                contractAddress = Address(validContractAddress).toSCAddress(),
                functionName = SCSymbolXdr("increment"),
                args = emptyList()
            )
        )
        val account = kit.sorobanServer.getAccount(deployer.getAccountId())
        val signedTransaction = TransactionBuilder(account, Network.TESTNET)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .addOperation(InvokeHostFunctionOperation(hostFunction, emptyList()))
            .addMemo(MemoNone)
            .setTimeout(30L)
            .build()
        val simulation = SimulateTransactionResponse(
            transactionData = sorobanData,
            minResourceFee = 100L,
            results = listOf(
                SimulateTransactionResponse.SimulateHostFunctionResult(auth = emptyList(), xdr = null)
            ),
            latestLedger = 100L
        )

        val result = kit.transactionOperations.submitMultiSignerTransaction(
            hostFunction = hostFunction,
            signedAuthEntries = emptyList(),
            signedTransaction = signedTransaction,
            simulation = simulation
        )

        assertTrue(result.success, "Submission must succeed; error=${result.error}")
        assertEquals(txHash, result.hash)
        assertFalse(
            rpcMethods.contains("sendTransaction"),
            "the multi-signer path shares the relayer routing of submit(); saw $rpcMethods"
        )
        val payload = relayer.singleRequestJson()
        assertNull(payload["xdr"], "no source_account credentials means the host-function relayer mode")
        assertEquals(
            0,
            assertNotNull(payload["auth"]).jsonArray.size,
            "the collected auth entries are forwarded as given"
        )
    }

    @Test
    fun fundWallet_relayerConfigured_submitsConvertedAuthEntriesThroughRelayer() = runTest {
        // The funding entries start as source_account credentials and are converted to
        // ADDRESS_V2 credentials, so no source_account auth is left and the host-function
        // relayer mode applies rather than the signed-envelope mode.
        FriendBot.httpClientOverride = buildFriendbotSuccessClient()
        val deployer = KeyPair.random()
        val tempAccountJson = ledgerEntriesResponseJson(buildAccountEntryXdr(KeyPair.random()).toXdrBase64())
        val deployerAccountJson = ledgerEntriesResponseJson(buildAccountEntryXdr(deployer).toXdrBase64())
        val sorobanData = buildMinimalSorobanData().toXdrBase64()

        val balanceJson = simulateValueResponseJson(
            Scv.toInt128(BigInteger.fromLong(1_005L * Util.STROOPS_PER_XLM)).toXdrBase64(),
            sorobanData
        )
        val transferSimulateJson = simulateAuthResponseJson(
            listOf(sourceAccountAuthEntry().toXdrBase64()),
            sorobanData
        )
        val reSimulateJson = simulateAuthResponseJson(emptyList(), sorobanData)
        val txHash = "e1b2c3d4e5f60718293a4b5c6d7e8f900112233445566778899aabbccddeeff0"

        val relayer = MockRelayer(relayerSuccessJson(txHash))
        val rpcMethods = mutableListOf<String>()
        val observed = mutableListOf<SmartAccountEvent>()
        val kit = createFundWalletKit(
            deployer = deployer,
            responses = listOf(
                tempAccountJson,
                tempAccountJson,
                deployerAccountJson,
                balanceJson,
                transferSimulateJson,
                latestLedgerResponseJson(latestLedgerSequence),
                tempAccountJson,
                reSimulateJson
            ),
            trailingResponse = getTransactionResponseJson("SUCCESS", ledger = 1001L),
            relayerClient = relayer.client,
            onRequest = { _, body -> rpcMethods.add(rpcMethodOf(body)) }
        )
        kit.events.addListener { observed.add(it) }

        val fundedAmount = kit.transactionOperations.fundWallet(nativeTokenContract = validContractAddress)

        assertEquals("1000", fundedAmount)
        assertFalse(
            rpcMethods.contains("sendTransaction"),
            "funding is relayed rather than submitted to the RPC; saw $rpcMethods"
        )

        val payload = relayer.singleRequestJson()
        assertNull(payload["xdr"], "the converted entries leave no source_account auth behind")
        val relayedAuth = assertNotNull(payload["auth"]).jsonArray
        assertEquals(1, relayedAuth.size)
        val relayedEntry = SorobanAuthorizationEntryXdr.fromXdrBase64(
            relayedAuth.single().jsonPrimitive.content
        )
        assertIs<SorobanCredentialsXdr.AddressV2>(
            relayedEntry.credentials,
            "the source_account credential is relayed as a signed ADDRESS_V2 credential"
        )

        assertTrue(
            observed.filterIsInstance<SmartAccountEvent.TransactionSubmitted>().isEmpty(),
            "funding is an internal step and does not emit submission events"
        )
    }

    @Test
    fun fundWallet_relayerRejectsSubmission_throwsSubmissionFailed() = runTest {
        FriendBot.httpClientOverride = buildFriendbotSuccessClient()
        val deployer = KeyPair.random()
        val tempAccountJson = ledgerEntriesResponseJson(buildAccountEntryXdr(KeyPair.random()).toXdrBase64())
        val deployerAccountJson = ledgerEntriesResponseJson(buildAccountEntryXdr(deployer).toXdrBase64())
        val sorobanData = buildMinimalSorobanData().toXdrBase64()

        val balanceJson = simulateValueResponseJson(
            Scv.toInt128(BigInteger.fromLong(1_005L * Util.STROOPS_PER_XLM)).toXdrBase64(),
            sorobanData
        )
        val transferSimulateJson = simulateAuthResponseJson(
            listOf(sourceAccountAuthEntry().toXdrBase64()),
            sorobanData
        )
        val reSimulateJson = simulateAuthResponseJson(emptyList(), sorobanData)

        val relayer = MockRelayer(
            relayerErrorJson("fee limit exceeded", RelayerErrorCodes.FEE_LIMIT_EXCEEDED)
        )
        val kit = createFundWalletKit(
            deployer = deployer,
            responses = listOf(
                tempAccountJson,
                tempAccountJson,
                deployerAccountJson,
                balanceJson,
                transferSimulateJson,
                latestLedgerResponseJson(latestLedgerSequence),
                tempAccountJson,
                reSimulateJson
            ),
            relayerClient = relayer.client
        )

        val ex = assertFailsWith<TransactionException.SubmissionFailed> {
            kit.transactionOperations.fundWallet(nativeTokenContract = validContractAddress)
        }
        assertTrue(
            ex.message.contains("Funding transaction failed") && ex.message.contains("fee limit exceeded"),
            "a relayed funding failure must name the relayer's reason; got: ${ex.message}"
        )
    }
}
