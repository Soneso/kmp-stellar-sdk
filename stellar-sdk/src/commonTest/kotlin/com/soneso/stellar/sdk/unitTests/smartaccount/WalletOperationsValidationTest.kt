//
//  WalletOperationsValidationTest.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.unitTests.smartaccount

import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.smartaccount.core.*
import com.soneso.stellar.sdk.smartaccount.oz.*
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
 * Unit tests for [OZWalletOperations] validation logic, data classes, and kit connection state.
 *
 * Tests cover:
 * - [OZWalletOperations.createWallet] pre-network validation (WebAuthn provider check)
 * - [OZWalletOperations.authenticatePasskey] pre-network validation
 * - [OZWalletOperations.connectWallet] session-only paths and validation
 * - [OZWalletOperations.ConnectWalletOptions] data class construction and equality
 * - [CreateWalletResult] data class construction, equality, and hashCode
 * - [ConnectWalletResult] data class construction and equality
 * - [DeployPendingResult] data class construction and equality
 * - [AuthenticatePasskeyResult] data class equality (ByteArray field)
 * - [OZSmartAccountKit.disconnect] state transitions and events
 * - [OZSmartAccountKit.isConnected], [OZSmartAccountKit.credentialId], [OZSmartAccountKit.contractId]
 * - [OZSmartAccountKit.requireConnected] when not connected
 * - [OZWalletOperations.deployPendingCredential] input validation
 *
 * Methods that cannot be tested without network or WebAuthn:
 * - connectWallet(ConnectWalletOptions(credentialId=..., contractId=...)): Always calls
 *   verifyContractExists() which queries Soroban RPC.
 * - createWallet() beyond the initial validation: Requires a real WebAuthnProvider.
 * - authenticatePasskey() beyond the initial validation: Requires a real WebAuthnProvider.
 * - deployPendingCredential() with a valid credential and autoSubmit=false: Calls
 *   buildDeployTransaction() which requires sorobanServer.getAccount() and simulateTransaction().
 * - connectWallet(ConnectWalletOptions(prompt = true)): Requires WebAuthnProvider.
 * - connectWallet(ConnectWalletOptions(fresh = true)): Requires WebAuthnProvider.
 */
class WalletOperationsValidationTest {

    // MARK: - Test Fixtures

    private val validContractAddress = "CAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAD2KM"

    private fun createKit(): OZSmartAccountKit {
        val config = OZSmartAccountConfig(
            rpcUrl = "https://soroban-testnet.stellar.org",
            networkPassphrase = Network.TESTNET.networkPassphrase,
            accountWasmHash = "a" + "0".repeat(63),
            webauthnVerifierAddress = validContractAddress
        )
        return OZSmartAccountKit.create(config)
    }

    private fun testPublicKey(fill: Byte = 0x42): ByteArray {
        val key = ByteArray(65)
        key[0] = SmartAccountConstants.UNCOMPRESSED_PUBKEY_PREFIX
        for (i in 1 until 65) key[i] = fill
        return key
    }

    // ========================================================================
    // createWallet() Pre-Network Validation
    // ========================================================================

    @Test
    fun testCreateWallet_noWebAuthnProvider_throwsNotSupported() = runTest {
        val kit = createKit()
        val exception = assertFailsWith<WebAuthnException.NotSupported> {
            kit.walletOperations.createWallet()
        }
        assertTrue(
            exception.message!!.contains("No WebAuthnProvider configured"),
            "Exception message should mention missing provider"
        )
    }

    @Test
    fun testCreateWallet_noWebAuthnProvider_withCustomUserName() = runTest {
        val kit = createKit()
        val exception = assertFailsWith<WebAuthnException.NotSupported> {
            kit.walletOperations.createWallet(userName = "Alice")
        }
        assertNotNull(exception.message)
    }

    @Test
    fun testCreateWallet_noWebAuthnProvider_withAutoSubmit() = runTest {
        // WebAuthn check happens before autoSubmit logic
        val kit = createKit()
        assertFailsWith<WebAuthnException.NotSupported> {
            kit.walletOperations.createWallet(autoSubmit = true)
        }
    }

    @Test
    fun testCreateWallet_noWebAuthnProvider_withAutoFundAndToken() = runTest {
        // WebAuthn check happens before autoFund validation
        val kit = createKit()
        assertFailsWith<WebAuthnException.NotSupported> {
            kit.walletOperations.createWallet(
                autoFund = true,
                nativeTokenContract = validContractAddress
            )
        }
    }

    // ========================================================================
    // authenticatePasskey() Pre-Network Validation
    // ========================================================================

    @Test
    fun testAuthenticatePasskey_noWebAuthnProvider_throwsNotSupported() = runTest {
        val kit = createKit()
        val exception = assertFailsWith<WebAuthnException.NotSupported> {
            kit.walletOperations.authenticatePasskey()
        }
        assertTrue(
            exception.message!!.contains("No WebAuthnProvider configured"),
            "Exception message should mention missing provider"
        )
    }

    @Test
    fun testAuthenticatePasskey_noWebAuthnProvider_withChallenge() = runTest {
        val kit = createKit()
        assertFailsWith<WebAuthnException.NotSupported> {
            kit.walletOperations.authenticatePasskey(challenge = ByteArray(32))
        }
    }

    @Test
    fun testAuthenticatePasskey_noWebAuthnProvider_withCredentialIds() = runTest {
        val kit = createKit()
        assertFailsWith<WebAuthnException.NotSupported> {
            kit.walletOperations.authenticatePasskey(
                credentialIds = listOf("cred-1", "cred-2")
            )
        }
    }

    // ========================================================================
    // connectWallet() Default Options (No Session)
    // ========================================================================

    @Test
    fun testConnectWallet_defaultOptions_noSession_returnsNull() = runTest {
        val kit = createKit()
        val result = kit.walletOperations.connectWallet()
        assertNull(result, "connectWallet with default options and no session should return null")
    }

    @Test
    fun testConnectWallet_promptFalse_noSession_returnsNull() = runTest {
        val kit = createKit()
        val result = kit.walletOperations.connectWallet(
            OZWalletOperations.ConnectWalletOptions(prompt = false)
        )
        assertNull(result)
    }

    @Test
    fun testConnectWallet_freshTrue_noWebAuthnProvider_throwsNotSupported() = runTest {
        val kit = createKit()
        assertFailsWith<WebAuthnException.NotSupported> {
            kit.walletOperations.connectWallet(
                OZWalletOperations.ConnectWalletOptions(fresh = true)
            )
        }
    }

    @Test
    fun testConnectWallet_promptTrue_noSession_noWebAuthnProvider_throwsNotSupported() = runTest {
        val kit = createKit()
        assertFailsWith<WebAuthnException.NotSupported> {
            kit.walletOperations.connectWallet(
                OZWalletOperations.ConnectWalletOptions(prompt = true)
            )
        }
    }

    @Test
    fun testConnectWallet_contractIdWithoutCredentialId_throwsValidation() = runTest {
        val kit = createKit()
        assertFailsWith<ValidationException.InvalidInput> {
            kit.walletOperations.connectWallet(
                OZWalletOperations.ConnectWalletOptions(
                    contractId = validContractAddress
                )
            )
        }
    }

    // ========================================================================
    // ConnectWalletOptions Data Class
    // ========================================================================

    @Test
    fun testConnectWalletOptions_defaultValues() {
        val options = OZWalletOperations.ConnectWalletOptions()
        assertNull(options.credentialId)
        assertNull(options.contractId)
        assertFalse(options.fresh)
        assertFalse(options.prompt)
    }

    @Test
    fun testConnectWalletOptions_withPrompt() {
        val options = OZWalletOperations.ConnectWalletOptions(prompt = true)
        assertNull(options.credentialId)
        assertNull(options.contractId)
        assertFalse(options.fresh)
        assertTrue(options.prompt)
    }

    @Test
    fun testConnectWalletOptions_withFresh() {
        val options = OZWalletOperations.ConnectWalletOptions(fresh = true)
        assertNull(options.credentialId)
        assertNull(options.contractId)
        assertTrue(options.fresh)
        assertFalse(options.prompt)
    }

    @Test
    fun testConnectWalletOptions_withCredentialIdAndContractId() {
        val options = OZWalletOperations.ConnectWalletOptions(
            credentialId = "cred-abc",
            contractId = validContractAddress
        )
        assertEquals("cred-abc", options.credentialId)
        assertEquals(validContractAddress, options.contractId)
        assertFalse(options.fresh)
        assertFalse(options.prompt)
    }

    @Test
    fun testConnectWalletOptions_withAllFields() {
        val options = OZWalletOperations.ConnectWalletOptions(
            credentialId = "cred-abc",
            contractId = validContractAddress,
            fresh = true,
            prompt = true
        )
        assertEquals("cred-abc", options.credentialId)
        assertEquals(validContractAddress, options.contractId)
        assertTrue(options.fresh)
        assertTrue(options.prompt)
    }

    @Test
    fun testConnectWalletOptions_equality() {
        val a = OZWalletOperations.ConnectWalletOptions(credentialId = "x", prompt = true)
        val b = OZWalletOperations.ConnectWalletOptions(credentialId = "x", prompt = true)
        val c = OZWalletOperations.ConnectWalletOptions(credentialId = "y", prompt = true)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a, c)
    }

    @Test
    fun testConnectWalletOptions_copy() {
        val original = OZWalletOperations.ConnectWalletOptions(fresh = true)
        val copied = original.copy(prompt = true)
        assertTrue(copied.fresh)
        assertTrue(copied.prompt)
        assertNull(copied.credentialId)
    }

    // ========================================================================
    // CreateWalletResult Data Class
    // ========================================================================

    @Test
    fun testCreateWalletResult_construction_defaultOptionalFields() {
        val pk = testPublicKey()
        val result = CreateWalletResult(
            credentialId = "cred-1",
            contractId = validContractAddress,
            publicKey = pk,
            signedTransactionXdr = "AAAA..."
        )
        assertEquals("cred-1", result.credentialId)
        assertEquals(validContractAddress, result.contractId)
        assertTrue(pk.contentEquals(result.publicKey))
        assertEquals("AAAA...", result.signedTransactionXdr)
        assertNull(result.transactionHash)
        assertNull(result.nickname)
    }

    @Test
    fun testCreateWalletResult_constructionWithAllFields() {
        val pk = testPublicKey()
        val result = CreateWalletResult(
            credentialId = "cred-2",
            contractId = validContractAddress,
            publicKey = pk,
            signedTransactionXdr = "BBBB...",
            transactionHash = "hash-abc",
            nickname = "Alice"
        )
        assertEquals("cred-2", result.credentialId)
        assertEquals("hash-abc", result.transactionHash)
        assertEquals("Alice", result.nickname)
    }

    @Test
    fun testCreateWalletResult_equality_sameData() {
        val pk = testPublicKey()
        val a = CreateWalletResult(
            credentialId = "cred-1",
            contractId = validContractAddress,
            publicKey = pk.copyOf(),
            signedTransactionXdr = "XDR"
        )
        val b = CreateWalletResult(
            credentialId = "cred-1",
            contractId = validContractAddress,
            publicKey = pk.copyOf(),
            signedTransactionXdr = "XDR"
        )
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun testCreateWalletResult_equality_differentPublicKey() {
        val a = CreateWalletResult(
            credentialId = "cred-1",
            contractId = validContractAddress,
            publicKey = testPublicKey(fill = 0x01),
            signedTransactionXdr = "XDR"
        )
        val b = CreateWalletResult(
            credentialId = "cred-1",
            contractId = validContractAddress,
            publicKey = testPublicKey(fill = 0x02),
            signedTransactionXdr = "XDR"
        )
        assertNotEquals(a, b)
    }

    @Test
    fun testCreateWalletResult_equality_differentCredentialId() {
        val pk = testPublicKey()
        val a = CreateWalletResult("a", validContractAddress, pk.copyOf(), "XDR")
        val b = CreateWalletResult("b", validContractAddress, pk.copyOf(), "XDR")
        assertNotEquals(a, b)
    }

    @Test
    fun testCreateWalletResult_equality_differentTransactionHash() {
        val pk = testPublicKey()
        val a = CreateWalletResult("c", validContractAddress, pk.copyOf(), "XDR", transactionHash = "h1")
        val b = CreateWalletResult("c", validContractAddress, pk.copyOf(), "XDR", transactionHash = "h2")
        assertNotEquals(a, b)
    }

    @Test
    fun testCreateWalletResult_equality_differentNickname() {
        val pk = testPublicKey()
        val a = CreateWalletResult("c", validContractAddress, pk.copyOf(), "XDR", nickname = "Alice")
        val b = CreateWalletResult("c", validContractAddress, pk.copyOf(), "XDR", nickname = "Bob")
        assertNotEquals(a, b)
    }

    @Test
    fun testCreateWalletResult_copy() {
        val pk = testPublicKey()
        val original = CreateWalletResult(
            credentialId = "cred-1",
            contractId = validContractAddress,
            publicKey = pk,
            signedTransactionXdr = "XDR"
        )
        val copied = original.copy(transactionHash = "new-hash", nickname = "Bob")
        assertEquals("new-hash", copied.transactionHash)
        assertEquals("Bob", copied.nickname)
        assertEquals("cred-1", copied.credentialId)
    }

    @Test
    fun testCreateWalletResult_equality_notEqualToNull() {
        val result = CreateWalletResult("c", validContractAddress, testPublicKey(), "XDR")
        assertFalse(result.equals(null))
    }

    @Test
    fun testCreateWalletResult_equality_notEqualToOtherType() {
        val result = CreateWalletResult("c", validContractAddress, testPublicKey(), "XDR")
        assertFalse(result.equals("not a result"))
    }

    @Test
    fun testCreateWalletResult_equality_sameInstance() {
        val result = CreateWalletResult("c", validContractAddress, testPublicKey(), "XDR")
        assertTrue(result.equals(result))
    }

    // ========================================================================
    // ConnectWalletResult Sealed Type
    // ========================================================================

    @Test
    fun testConnectWalletResult_connected_construction() {
        val result = ConnectWalletResult.Connected(
            credentialId = "cred-abc",
            contractId = validContractAddress,
            restoredFromSession = false
        )
        assertEquals("cred-abc", result.credentialId)
        assertEquals(validContractAddress, result.contractId)
        assertFalse(result.restoredFromSession)
    }

    @Test
    fun testConnectWalletResult_connected_restoredFromSession() {
        val result = ConnectWalletResult.Connected(
            credentialId = "cred-abc",
            contractId = validContractAddress,
            restoredFromSession = true
        )
        assertTrue(result.restoredFromSession)
    }

    @Test
    fun testConnectWalletResult_connected_equality() {
        val a = ConnectWalletResult.Connected("c", validContractAddress, false)
        val b = ConnectWalletResult.Connected("c", validContractAddress, false)
        val c = ConnectWalletResult.Connected("c", validContractAddress, true)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a, c)
    }

    @Test
    fun testConnectWalletResult_connected_copy() {
        val original = ConnectWalletResult.Connected("c", validContractAddress, false)
        val copied = original.copy(restoredFromSession = true)
        assertTrue(copied.restoredFromSession)
        assertEquals("c", copied.credentialId)
    }

    @Test
    fun testConnectWalletResult_ambiguous_construction() {
        val candidates = listOf(
            "CDLZFC3SYJYDZT7K67VZ75HPJVIEUVNIXF47ZG2FB2RMQQVU2HHGCYSC",
            "CCMK6CYUEFEWKCPP6JL4EYYWTQGVPLG4F2KHE2H6DQOMXKBTHSDIH3JB"
        )
        val result = ConnectWalletResult.Ambiguous(
            credentialId = "cred-abc",
            candidates = candidates
        )
        assertEquals("cred-abc", result.credentialId)
        assertEquals(candidates, result.candidates)
        assertEquals(2, result.candidates.size)
    }

    @Test
    fun testConnectWalletResult_sealed_when_exhaustive() {
        // Compile-time exhaustiveness check: when on the sealed type must
        // cover both arms. If a future change adds a new arm, this test
        // fails to compile until the arm is handled here, surfacing the
        // need to update every consumer.
        val results: List<ConnectWalletResult> = listOf(
            ConnectWalletResult.Connected("c", validContractAddress, false),
            ConnectWalletResult.Ambiguous("c", listOf(validContractAddress))
        )
        for (r in results) {
            val handled: String = when (r) {
                is ConnectWalletResult.Connected -> "connected:${r.contractId}"
                is ConnectWalletResult.Ambiguous -> "ambiguous:${r.candidates.size}"
            }
            assertTrue(handled.isNotEmpty())
        }
    }

    // ========================================================================
    // DeployPendingResult Data Class
    // ========================================================================

    @Test
    fun testDeployPendingResult_construction_defaultOptionalFields() {
        val result = DeployPendingResult(
            contractId = validContractAddress,
            signedTransactionXdr = "signed-xdr"
        )
        assertEquals(validContractAddress, result.contractId)
        assertEquals("signed-xdr", result.signedTransactionXdr)
        assertNull(result.transactionHash)
    }

    @Test
    fun testDeployPendingResult_withTransactionHash() {
        val result = DeployPendingResult(
            contractId = validContractAddress,
            signedTransactionXdr = "signed-xdr",
            transactionHash = "hash-123"
        )
        assertEquals("hash-123", result.transactionHash)
    }

    @Test
    fun testDeployPendingResult_equality() {
        val a = DeployPendingResult(validContractAddress, "xdr-1")
        val b = DeployPendingResult(validContractAddress, "xdr-1")
        val c = DeployPendingResult(validContractAddress, "xdr-2")
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a, c)
    }

    @Test
    fun testDeployPendingResult_copy() {
        val original = DeployPendingResult(validContractAddress, "xdr")
        val copied = original.copy(transactionHash = "h")
        assertEquals("h", copied.transactionHash)
        assertEquals("xdr", copied.signedTransactionXdr)
    }

    // ========================================================================
    // AuthenticatePasskeyResult Data Class
    // ========================================================================

    @Test
    fun testAuthenticatePasskeyResult_equality_sameData() {
        val sig = WebAuthnSignature(
            authenticatorData = ByteArray(37) { 0x01 },
            clientData = ByteArray(10) { 0x02 },
            signature = ByteArray(64) { 0x03 }
        )
        val pk = testPublicKey()
        val a = AuthenticatePasskeyResult("cred", sig, pk.copyOf())
        val b = AuthenticatePasskeyResult("cred", sig, pk.copyOf())
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun testAuthenticatePasskeyResult_equality_differentPublicKey() {
        val sig = WebAuthnSignature(
            authenticatorData = ByteArray(37) { 0x01 },
            clientData = ByteArray(10) { 0x02 },
            signature = ByteArray(64) { 0x03 }
        )
        val a = AuthenticatePasskeyResult("cred", sig, testPublicKey(fill = 0x01))
        val b = AuthenticatePasskeyResult("cred", sig, testPublicKey(fill = 0x02))
        assertNotEquals(a, b)
    }

    @Test
    fun testAuthenticatePasskeyResult_equality_differentCredentialId() {
        val sig = WebAuthnSignature(
            authenticatorData = ByteArray(37) { 0x01 },
            clientData = ByteArray(10) { 0x02 },
            signature = ByteArray(64) { 0x03 }
        )
        val pk = testPublicKey()
        val a = AuthenticatePasskeyResult("cred-1", sig, pk.copyOf())
        val b = AuthenticatePasskeyResult("cred-2", sig, pk.copyOf())
        assertNotEquals(a, b)
    }

    @Test
    fun testAuthenticatePasskeyResult_equality_notEqualToOtherType() {
        val sig = WebAuthnSignature(
            authenticatorData = ByteArray(37),
            clientData = ByteArray(10),
            signature = ByteArray(64)
        )
        val result = AuthenticatePasskeyResult("cred", sig, testPublicKey())
        assertFalse(result.equals("not a result"))
    }

    @Test
    fun testAuthenticatePasskeyResult_equality_notEqualToNull() {
        val sig = WebAuthnSignature(
            authenticatorData = ByteArray(37),
            clientData = ByteArray(10),
            signature = ByteArray(64)
        )
        val result = AuthenticatePasskeyResult("cred", sig, testPublicKey())
        assertFalse(result.equals(null))
    }

    @Test
    fun testAuthenticatePasskeyResult_fieldAccess() {
        val authData = ByteArray(37) { 0x0A }
        val clientData = ByteArray(10) { 0x0B }
        val sigBytes = ByteArray(64) { 0x0C }
        val sig = WebAuthnSignature(
            authenticatorData = authData,
            clientData = clientData,
            signature = sigBytes
        )
        val pk = testPublicKey(fill = 0x77)
        val result = AuthenticatePasskeyResult("my-cred", sig, pk)
        assertEquals("my-cred", result.credentialId)
        assertEquals(sig, result.signature)
        assertTrue(pk.contentEquals(result.publicKey))
    }

    // ========================================================================
    // Kit Connection State: isConnected / credentialId / contractId
    // ========================================================================

    @Test
    fun testKit_initialState_notConnected() {
        val kit = createKit()
        assertFalse(kit.isConnected)
        assertNull(kit.credentialId)
        assertNull(kit.contractId)
    }

    @Test
    fun testKit_afterSetConnectedState() = runTest {
        val kit = createKit()
        kit.setConnectedState("my-credential", validContractAddress)
        assertTrue(kit.isConnected)
        assertEquals("my-credential", kit.credentialId)
        assertEquals(validContractAddress, kit.contractId)
    }

    @Test
    fun testKit_setConnectedState_overwritesPrevious() = runTest {
        val kit = createKit()
        kit.setConnectedState("cred-1", validContractAddress)
        assertTrue(kit.isConnected)
        assertEquals("cred-1", kit.credentialId)

        val otherContract = "CDLZFC3SYJYDZT7K67VZ75HPJVIEUVNIXF47ZG2FB2RMQQVU2HHGCYSC"
        kit.setConnectedState("cred-2", otherContract)
        assertTrue(kit.isConnected)
        assertEquals("cred-2", kit.credentialId)
        assertEquals(otherContract, kit.contractId)
    }

    // ========================================================================
    // disconnect()
    // ========================================================================

    @Test
    fun testDisconnect_afterConnectedState_clearsState() = runTest {
        val kit = createKit()
        kit.setConnectedState("cred-x", validContractAddress)
        assertTrue(kit.isConnected)

        kit.disconnect()
        assertFalse(kit.isConnected)
        assertNull(kit.credentialId)
        assertNull(kit.contractId)
    }

    @Test
    fun testDisconnect_whenNotConnected_doesNotThrow() = runTest {
        val kit = createKit()
        assertFalse(kit.isConnected)
        kit.disconnect()
        assertFalse(kit.isConnected)
    }

    @Test
    fun testDisconnect_doubleDisconnect_doesNotThrow() = runTest {
        val kit = createKit()
        kit.setConnectedState("cred-x", validContractAddress)
        kit.disconnect()
        kit.disconnect()
        assertFalse(kit.isConnected)
    }

    @Test
    fun testDisconnect_emitsEvent_whenConnected() = runTest {
        val kit = createKit()
        kit.setConnectedState("cred-x", validContractAddress)

        var emittedEvent: SmartAccountEvent.WalletDisconnected? = null
        kit.events.on<SmartAccountEvent.WalletDisconnected> { event ->
            emittedEvent = event
        }

        kit.disconnect()
        assertNotNull(emittedEvent)
        assertEquals(validContractAddress, emittedEvent!!.contractId)
    }

    @Test
    fun testDisconnect_doesNotEmitEvent_whenNotConnected() = runTest {
        val kit = createKit()
        var emittedEvent: SmartAccountEvent.WalletDisconnected? = null
        kit.events.on<SmartAccountEvent.WalletDisconnected> { event ->
            emittedEvent = event
        }

        kit.disconnect()
        assertNull(emittedEvent, "Should not emit WalletDisconnected when not connected")
    }

    @Test
    fun testDisconnect_clearsSession() = runTest {
        val kit = createKit()
        kit.setConnectedState("cred-x", validContractAddress)
        kit.disconnect()
        // After disconnect, a subsequent connectWallet (default) should return null
        // because the session was cleared
        val result = kit.walletOperations.connectWallet()
        assertNull(result)
    }

    // ========================================================================
    // requireConnected()
    // ========================================================================

    @Test
    fun testRequireConnected_whenNotConnected_throwsNotConnected() = runTest {
        val kit = createKit()
        val exception = assertFailsWith<WalletException.NotConnected> {
            kit.requireConnected()
        }
        assertTrue(exception.message!!.contains("No wallet connected"))
    }

    @Test
    fun testRequireConnected_whenConnected_returnsPair() = runTest {
        val kit = createKit()
        kit.setConnectedState("cred-abc", validContractAddress)
        val (credId, ctId) = kit.requireConnected()
        assertEquals("cred-abc", credId)
        assertEquals(validContractAddress, ctId)
    }

    @Test
    fun testRequireConnected_afterDisconnect_throwsNotConnected() = runTest {
        val kit = createKit()
        kit.setConnectedState("cred-abc", validContractAddress)
        kit.disconnect()
        assertFailsWith<WalletException.NotConnected> {
            kit.requireConnected()
        }
    }

    // ========================================================================
    // deployPendingCredential() Validation
    // ========================================================================

    @Test
    fun testDeployPendingCredential_autoFundWithoutToken_throwsValidation() = runTest {
        val kit = createKit()
        assertFailsWith<ValidationException.InvalidInput> {
            kit.walletOperations.deployPendingCredential(
                credentialId = "cred-abc",
                autoFund = true,
                nativeTokenContract = null
            )
        }
    }

    @Test
    fun testDeployPendingCredential_credentialNotFound_throwsCredentialException() = runTest {
        val kit = createKit()
        assertFailsWith<CredentialException.NotFound> {
            kit.walletOperations.deployPendingCredential(
                credentialId = "nonexistent-cred",
                autoSubmit = false
            )
        }
    }

    @Test
    fun testDeployPendingCredential_credentialMissingPublicKey_throwsInvalid() = runTest {
        val kit = createKit()
        // Bypass createPendingCredential validation by saving directly to storage
        kit.getStorage().save(
            StoredCredential(
                credentialId = "cred-empty-pk",
                publicKey = ByteArray(0),
                contractId = validContractAddress
            )
        )
        assertFailsWith<CredentialException.Invalid> {
            kit.walletOperations.deployPendingCredential(
                credentialId = "cred-empty-pk",
                autoSubmit = false
            )
        }
    }

    @Test
    fun testDeployPendingCredential_credentialMissingContractId_throwsInvalid() = runTest {
        val kit = createKit()
        // Bypass createPendingCredential validation by saving directly to storage
        kit.getStorage().save(
            StoredCredential(
                credentialId = "cred-no-contract",
                publicKey = testPublicKey(),
                contractId = null
            )
        )
        assertFailsWith<CredentialException.Invalid> {
            kit.walletOperations.deployPendingCredential(
                credentialId = "cred-no-contract",
                autoSubmit = false
            )
        }
    }

    @Test
    fun testDeployPendingCredential_credentialEmptyContractId_throwsInvalid() = runTest {
        val kit = createKit()
        // Bypass createPendingCredential validation by saving directly to storage
        kit.getStorage().save(
            StoredCredential(
                credentialId = "cred-empty-contract",
                publicKey = testPublicKey(),
                contractId = ""
            )
        )
        assertFailsWith<CredentialException.Invalid> {
            kit.walletOperations.deployPendingCredential(
                credentialId = "cred-empty-contract",
                autoSubmit = false
            )
        }
    }

    @Test
    fun testDeployPendingCredential_autoFundValidation_beforeCredentialLookup() = runTest {
        // autoFund validation happens before credential lookup
        val kit = createKit()
        assertFailsWith<ValidationException.InvalidInput> {
            kit.walletOperations.deployPendingCredential(
                credentialId = "any-cred",
                autoFund = true,
                nativeTokenContract = null
            )
        }
    }
}
