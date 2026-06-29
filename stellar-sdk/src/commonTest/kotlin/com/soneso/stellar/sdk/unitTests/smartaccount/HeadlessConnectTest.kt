//
//  HeadlessConnectTest.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.unitTests.smartaccount

import com.soneso.stellar.sdk.KeyPair
import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.smartaccount.core.ValidationException
import com.soneso.stellar.sdk.smartaccount.core.WalletException
import com.soneso.stellar.sdk.smartaccount.oz.OZSmartAccountConfig
import com.soneso.stellar.sdk.smartaccount.oz.OZSmartAccountKit
import com.soneso.stellar.sdk.smartaccount.oz.SelectedSigner
import com.soneso.stellar.sdk.smartaccount.oz.SmartAccountEvent
import com.soneso.stellar.sdk.smartaccount.oz.StoredSession
import com.soneso.stellar.sdk.rpc.SorobanServer
import com.soneso.stellar.sdk.xdr.SCValXdr
import com.soneso.stellar.sdk.xdr.Uint32Xdr
import com.soneso.stellar.sdk.xdr.toXdrBase64
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for the headless connect path [OZWalletOperations.connectToContract].
 *
 * Headless connect attaches the kit to a deployed smart account by contract address alone —
 * no passkey credential, no WebAuthn ceremony, no session. It is usable only by the
 * multi-signer / external-signer pipeline (non-empty selectedSigners). The single-passkey
 * submit path is guarded and throws [WalletException.HeadlessConnection].
 *
 * All tests are hermetic: a Ktor [io.ktor.client.engine.mock.MockEngine] backs the
 * [SorobanServer], and tests that must prove a short-circuit before the network use a server
 * that fails on the unexpected request.
 */
class HeadlessConnectTest {

    // The connected smart account contract address.
    private val contractId = VERIFIER_B

    // A valid recipient G-address for the transfer() guard sub-case. Distinct from the connected
    // contract so the self-transfer check does not fire before the guard.
    private val transferRecipient = "GA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJVSGZ"

    private fun buildConfig(deployer: KeyPair? = null): OZSmartAccountConfig = OZSmartAccountConfig(
        rpcUrl = "https://soroban-testnet.stellar.org",
        networkPassphrase = Network.TESTNET.networkPassphrase,
        accountWasmHash = "a" + "0".repeat(63),
        webauthnVerifierAddress = VERIFIER_A,
        deployerKeypair = deployer
    )

    private fun buildKit(server: SorobanServer, deployer: KeyPair? = null): OZSmartAccountKit =
        OZSmartAccountKit.createWithServer(config = buildConfig(deployer), sorobanServer = server)

    // ========================================================================
    // Test 1 — invalid address rejected before any network call
    // ========================================================================

    @Test
    fun test_connectToContract_invalidAddress_throwsValidationException() = runTest {
        // A MockEngine that fails on any request proves validation short-circuits before RPC.
        val kit = buildKit(buildNoRpcMockServer())

        assertFailsWith<ValidationException.InvalidAddress> {
            kit.walletOperations.connectToContract("G" + "A".repeat(55)) // G-address, not a contract
        }
        assertFalse(kit.isConnected, "Kit must not be connected after a rejected G-address")

        assertFailsWith<ValidationException.InvalidAddress> {
            kit.walletOperations.connectToContract("not-an-address")
        }
        assertFalse(kit.isConnected, "Kit must not be connected after a rejected malformed address")
    }

    // ========================================================================
    // Test 2 — non-existent contract fails with a clear, address-naming NotFound
    // ========================================================================

    @Test
    fun test_connectToContract_nonExistentContract_throwsWalletNotFound() = runTest {
        // Empty entries -> getContractData returns null -> WalletException.NotFound.
        val kit = buildKit(buildConstantResponseMockServer(emptyLedgerEntriesResponseJson()))

        val ex = assertFailsWith<WalletException.NotFound> {
            kit.walletOperations.connectToContract(contractId)
        }
        assertTrue(
            ex.message.contains(contractId),
            "NotFound message must name the contract address; got: ${ex.message}"
        )
        assertFalse(kit.isConnected, "Kit must not be connected when the contract does not exist")
    }

    // ========================================================================
    // Test 3 — existing contract sets a headless connected state (null credential)
    // ========================================================================

    @Test
    fun test_connectToContract_existingContract_setsConnectedState() = runTest {
        val kit = buildKit(buildConstantResponseMockServer(contractInstanceEntriesResponseJson(contractId)))

        val returned = kit.walletOperations.connectToContract(contractId)

        assertEquals(contractId, returned, "Return value must equal the connected contract address")
        assertTrue(kit.isConnected, "Kit must be connected after a successful headless connect")
        assertTrue(kit.isHeadless, "A contract-only connect must be marked headless")
        assertEquals(contractId, kit.contractId, "kit.contractId must equal the connected address")
        assertNull(kit.credentialId, "A headless connection must expose a null credentialId")
    }

    // ========================================================================
    // Test 4 — emits the dedicated HeadlessConnected event, never WalletConnected
    // ========================================================================

    @Test
    fun test_connectToContract_emitsHeadlessConnectedEvent() = runTest {
        val kit = buildKit(buildConstantResponseMockServer(contractInstanceEntriesResponseJson(contractId)))

        val headless = mutableListOf<SmartAccountEvent.HeadlessConnected>()
        var walletConnectedCount = 0
        kit.events.on<SmartAccountEvent.HeadlessConnected> { headless.add(it) }
        kit.events.on<SmartAccountEvent.WalletConnected> { walletConnectedCount++ }

        kit.walletOperations.connectToContract(contractId)

        assertEquals(1, headless.size, "Exactly one HeadlessConnected event must be emitted")
        assertEquals(contractId, headless.single().contractId, "Event must carry the contract address")
        assertEquals(
            0,
            walletConnectedCount,
            "WalletConnected must not fire: a headless connection has no credential to carry on the event"
        )
    }

    // ========================================================================
    // Test 5 — clears any previously saved session
    // ========================================================================

    @Test
    fun test_connectToContract_clearsExistingSession() = runTest {
        val kit = buildKit(buildConstantResponseMockServer(contractInstanceEntriesResponseJson(contractId)))

        // Pre-seed a stale passkey session.
        kit.getStorage().saveSession(
            StoredSession(
                credentialId = "stale-passkey-credential",
                contractId = contractId,
                connectedAt = 0L,
                expiresAt = Long.MAX_VALUE
            )
        )
        assertNotNull(kit.getStorage().getSession(), "Pre-seeded session must be present before connect")

        kit.walletOperations.connectToContract(contractId)

        assertNull(
            kit.getStorage().getSession(),
            "Headless connect must clear the saved session so a later silent restore cannot resurrect it"
        )
    }

    // ========================================================================
    // Test 6 — writes no session of its own
    // ========================================================================

    @Test
    fun test_connectToContract_doesNotSaveSession() = runTest {
        val kit = buildKit(buildConstantResponseMockServer(contractInstanceEntriesResponseJson(contractId)))

        kit.walletOperations.connectToContract(contractId)

        assertNull(
            kit.getStorage().getSession(),
            "Headless connect must not persist a session (it writes no credential and no session to storage)"
        )
    }

    // ========================================================================
    // Test 7 — does not touch the credential manager
    // ========================================================================

    @Test
    fun test_connectToContract_doesNotTouchCredentialManager() = runTest {
        val kit = buildKit(buildConstantResponseMockServer(contractInstanceEntriesResponseJson(contractId)))

        kit.walletOperations.connectToContract(contractId)

        assertNull(
            kit.credentialManager.getCredential(""),
            "Headless connect must not write a credential under any id"
        )
        assertTrue(
            kit.credentialManager.getAllCredentials().isEmpty(),
            "Headless connect must not create, read for deletion, or delete any credential"
        )
    }

    // ========================================================================
    // Test 8 — the guard: single-passkey paths fail loudly after a headless connect
    // ========================================================================

    @Test
    fun test_submitAfterHeadlessConnect_throwsHeadlessConnection() = runTest {
        // The server answers the connect existence check (index 0) and fails on any later
        // request, proving the guard fires before STEP 2 (any decode / simulation / WebAuthn).
        val kit = buildKit(buildContractInstanceThenNoRpcMockServer(contractInstanceEntriesResponseJson(contractId)))
        kit.walletOperations.connectToContract(contractId)

        // Direct submit() — the single-passkey path.
        val submitEx = assertFailsWith<WalletException.HeadlessConnection> {
            kit.transactionOperations.submit(stubHostFunction(contractId), emptyList())
        }
        assertTrue(
            submitEx.message.contains("multi-signer pipeline with explicit selectedSigners"),
            "Guard message must direct the caller to the multi-signer pipeline; got: ${submitEx.message}"
        )

        // executeAndSubmit() delegates to submit() — same guard fires.
        assertFailsWith<WalletException.HeadlessConnection> {
            kit.transactionOperations.executeAndSubmit(VERIFIER_A, "noop")
        }

        // contractCall() delegates to submit() — same guard fires.
        val contractCallEx = assertFailsWith<WalletException.HeadlessConnection> {
            kit.transactionOperations.contractCall(target = VERIFIER_A, targetFn = "noop")
        }
        assertTrue(
            contractCallEx.message.contains("multi-signer pipeline with explicit selectedSigners"),
            "Guard message must direct the caller to the multi-signer pipeline; got: ${contractCallEx.message}"
        )

        // transfer() with EXPLICIT decimals (so no on-chain decimals lookup runs) and a valid
        // recipient distinct from the connected contract reaches submit() via contractCall — the
        // same guard fires with no network round-trip before it.
        val transferEx = assertFailsWith<WalletException.HeadlessConnection> {
            kit.transactionOperations.transfer(
                tokenContract = VERIFIER_A,
                recipient = transferRecipient,
                amount = "1",
                decimals = 7
            )
        }
        assertTrue(
            transferEx.message.contains("multi-signer pipeline with explicit selectedSigners"),
            "Guard message must direct the caller to the multi-signer pipeline; got: ${transferEx.message}"
        )

        // A manager operation left at the default empty selectedSigners routes through submit().
        // removeSigner runs requireConnected, builds the host function, then dispatches into
        // submit() with no network round-trip before the guard.
        assertFailsWith<WalletException.HeadlessConnection> {
            kit.signerManager.removeSigner(contextRuleId = 0u, signerId = 0u)
        }
    }

    // ========================================================================
    // Test 9 — headline acceptance: multi-signer pipeline succeeds after headless connect
    // ========================================================================

    @Test
    fun test_connectToContract_thenMultiSignerSucceeds() = runTest {
        val deployer = KeyPair.random()

        val accountXdr = buildAccountEntryXdr(deployer).toXdrBase64()
        val authEntryXdr = buildAuthEntry(contractId).toXdrBase64()
        val sorobanDataXdr = buildMinimalSorobanData().toXdrBase64()
        val countZeroXdr = SCValXdr.U32(Uint32Xdr(0u)).toXdrBase64()
        val txHash = "a4721e2a61e9a6b3c6c2e5c0d4c0a5f3e2d1c0b9a8f7e6d5c4b3a2f1e0d9c8b7"

        val server = buildHeadlessConnectThenSubmissionMockServer(
            contractInstanceJson = contractInstanceEntriesResponseJson(contractId),
            accountXdrBase64 = accountXdr,
            authEntryBase64 = authEntryXdr,
            sorobanDataBase64 = sorobanDataXdr,
            countXdrBase64 = countZeroXdr,
            txHash = txHash
        )

        val kit = buildKit(server, deployer = deployer)

        // Register an in-process Ed25519 signer, then attach headlessly by contract address.
        val rawSeed = ByteArray(32) { (it + 77).toByte() }
        val publicKey = kit.externalSigners.addEd25519FromRawKey(rawSeed, VERIFIER_A)
        kit.walletOperations.connectToContract(contractId)

        // Operate via the multi-signer pipeline with explicit non-empty selectedSigners.
        val result = kit.multiSignerManager.multiSignerExecuteAndSubmit(
            target = VERIFIER_A,
            targetFn = "noop",
            targetArgs = emptyList(),
            selectedSigners = listOf(
                SelectedSigner.Ed25519(verifierAddress = VERIFIER_A, publicKey = publicKey)
            ),
            resolveContextRuleIds = { _, _ -> emptyList() }
        )

        assertTrue(result.success, "Multi-signer submit must succeed after a headless connect; error=${result.error}")
        assertNotNull(result.hash, "Result must carry the transaction hash")
        assertEquals(txHash, result.hash, "Transaction hash must match the mock response")
    }

    // ========================================================================
    // Test 10 — returns the connected address
    // ========================================================================

    @Test
    fun test_connectToContract_returnsConnectedAddress() = runTest {
        val kit = buildKit(buildConstantResponseMockServer(contractInstanceEntriesResponseJson(contractId)))

        val returned = kit.walletOperations.connectToContract(contractId)

        assertEquals(contractId, returned, "Returned address must equal the input contractId")
        assertEquals(kit.contractId, returned, "Returned address must equal kit.contractId")
    }
}
