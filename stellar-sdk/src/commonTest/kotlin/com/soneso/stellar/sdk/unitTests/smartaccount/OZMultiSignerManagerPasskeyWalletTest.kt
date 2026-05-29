//
//  OZMultiSignerManagerPasskeyWalletTest.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.unitTests.smartaccount

import com.soneso.stellar.sdk.KeyPair
import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.crypto.getSha256Crypto
import com.soneso.stellar.sdk.smartaccount.core.ValidationException
import com.soneso.stellar.sdk.smartaccount.oz.ConnectedWallet
import com.soneso.stellar.sdk.smartaccount.oz.ExternalWalletAdapter
import com.soneso.stellar.sdk.smartaccount.oz.OZSmartAccountConfig
import com.soneso.stellar.sdk.smartaccount.oz.OZSmartAccountKit
import com.soneso.stellar.sdk.smartaccount.oz.SelectedSigner
import com.soneso.stellar.sdk.smartaccount.oz.SignAuthEntryOptions
import com.soneso.stellar.sdk.smartaccount.oz.SignAuthEntryResult
import com.soneso.stellar.sdk.smartaccount.oz.WebAuthnAuthenticationResult
import com.soneso.stellar.sdk.xdr.SCValXdr
import com.soneso.stellar.sdk.xdr.Uint32Xdr
import com.soneso.stellar.sdk.xdr.toXdrBase64
import kotlinx.coroutines.test.runTest
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// ---------------------------------------------------------------------------
// Minimal DER-encoded secp256r1 signature used across happy-path passkey tests.
//
// This is the same known-good vector used in SmartAccountUtilsTest
// (testNormalizeSignature_alreadyLowS). It is a structurally valid DER
// signature whose r and s values are both below the curve half-order, so
// normalizeSignature() accepts it and returns a 64-byte compact signature.
//
// DER breakdown (70 bytes total):
//   30 44            — SEQUENCE, 68 bytes
//   02 20 0102...32  — INTEGER r, 32 bytes
//   02 20 0000...05  — INTEGER s, 32 bytes
// ---------------------------------------------------------------------------

private val VALID_DER_SIGNATURE: ByteArray = hexToByteArray(
    "3044022001020304050607080910111213141516171819202122232425262728293031320220" +
        "0000000000000000000000000000000000000000000000000000000000000005"
)

// ---------------------------------------------------------------------------
// Invalid "signature" that is not DER-encoded.
// normalizeSignature() will reject it with ValidationException.InvalidInput.
// ---------------------------------------------------------------------------
private val INVALID_DER_SIGNATURE: ByteArray = ByteArray(32) { (it + 1).toByte() }

// ---------------------------------------------------------------------------
// ExternalWalletAdapter backed by a real keypair.
//
// signAuthEntry decodes the base64 preimage, SHA-256-hashes it, signs the
// 32-byte hash with the keypair, and returns the 64-byte raw Ed25519
// signature base64-encoded. This matches the protocol expected by the
// Auth.authorizeInvocation lambda inside appendDelegatedSignerEntries.
// ---------------------------------------------------------------------------

@OptIn(ExperimentalEncodingApi::class)
private class KeypairWalletAdapter(
    private val keypair: KeyPair
) : ExternalWalletAdapter {

    val address: String = keypair.getAccountId()

    override suspend fun connect(): ConnectedWallet = ConnectedWallet(
        address = address,
        walletId = "test-wallet",
        walletName = "Test Wallet"
    )

    override suspend fun disconnect() { /* no-op */ }

    override fun canSignFor(address: String): Boolean = address == this.address

    override fun getConnectedWallets(): List<ConnectedWallet> = listOf(
        ConnectedWallet(address = address, walletId = "test-wallet", walletName = "Test Wallet")
    )

    override fun getWalletForAddress(address: String): ConnectedWallet? =
        if (address == this.address) {
            ConnectedWallet(address = address, walletId = "test-wallet", walletName = "Test Wallet")
        } else null

    @OptIn(ExperimentalEncodingApi::class)
    override suspend fun signAuthEntry(
        preimageXdr: String,
        options: SignAuthEntryOptions?
    ): SignAuthEntryResult {
        val preimageBytes = Base64.decode(preimageXdr)
        val hash = getSha256Crypto().hash(preimageBytes)
        val signature = keypair.sign(hash)
        return SignAuthEntryResult(
            signedAuthEntry = Base64.encode(signature),
            signerAddress = address
        )
    }
}

// ---------------------------------------------------------------------------
// Config helpers
// ---------------------------------------------------------------------------

private fun buildPasskeyConfig(
    deployer: KeyPair,
    webauthnProvider: MockWebAuthnProvider
): OZSmartAccountConfig = OZSmartAccountConfig(
    rpcUrl = "https://soroban-testnet.stellar.org",
    networkPassphrase = Network.TESTNET.networkPassphrase,
    accountWasmHash = "a" + "0".repeat(63),
    webauthnVerifierAddress = VERIFIER_A,
    deployerKeypair = deployer,
    webauthnProvider = webauthnProvider
)

private fun buildWalletConfig(
    deployer: KeyPair,
    walletAdapter: ExternalWalletAdapter
): OZSmartAccountConfig = OZSmartAccountConfig(
    rpcUrl = "https://soroban-testnet.stellar.org",
    networkPassphrase = Network.TESTNET.networkPassphrase,
    accountWasmHash = "a" + "0".repeat(63),
    webauthnVerifierAddress = VERIFIER_A,
    deployerKeypair = deployer,
    externalWallet = walletAdapter
)

private fun buildMixedConfig(
    deployer: KeyPair,
    webauthnProvider: MockWebAuthnProvider,
    walletAdapter: ExternalWalletAdapter
): OZSmartAccountConfig = OZSmartAccountConfig(
    rpcUrl = "https://soroban-testnet.stellar.org",
    networkPassphrase = Network.TESTNET.networkPassphrase,
    accountWasmHash = "a" + "0".repeat(63),
    webauthnVerifierAddress = VERIFIER_A,
    deployerKeypair = deployer,
    webauthnProvider = webauthnProvider,
    externalWallet = walletAdapter
)

// ---------------------------------------------------------------------------
// Passkey signer with keyData pointing at VERIFIER_A (65 bytes: 0x04 prefix +
// arbitrary X + Y). The keyData is not on-curve-validated by the signing loop —
// it is used only as the signer slot identifier.
// ---------------------------------------------------------------------------

private fun passkeySignerWithKeyData(
    credentialId: String = "test-credential-id",
    credentialIdBytes: ByteArray = ByteArray(16) { it.toByte() }
): SelectedSigner.Passkey {
    // keyData must be non-empty; the signing path in appendPasskeySignature passes it
    // directly to ExternalSigner(verifierAddress, keyData) which only validates that
    // keyData is non-empty. 68 bytes: 65-byte public key + 3-byte credential ID suffix.
    val keyData = ByteArray(68) { (it + 1).toByte() }
    return SelectedSigner.Passkey(
        credentialId = credentialId,
        credentialIdBytes = credentialIdBytes,
        keyData = keyData
    )
}

// ---------------------------------------------------------------------------
// Test class
// ---------------------------------------------------------------------------

/**
 * Unit tests for the passkey and delegated wallet signer paths through
 * [OZMultiSignerManager].
 *
 * Tests cover:
 * - appendPasskeySignature happy path (valid DER signature normalizes successfully)
 * - appendPasskeySignature failure path (invalid DER signature triggers exception)
 * - appendDelegatedSignerEntries + authSigner.sign happy path
 * - validateSelectedSigners wallet-absent failure path
 * - mapToSmartAccountSigners routing for mixed signer lists
 * - updatePasskeyLastUsed invoked when passkey participates
 */
class OZMultiSignerManagerPasskeyWalletTest {

    // ========================================================================
    // Passkey signer — happy path
    // ========================================================================

    @Test
    fun test_submitWithMultipleSigners_passkeyOnly_succeeds() = runTest {
        val deployer = KeyPair.random()
        val mockProvider = MockWebAuthnProvider()

        // Return a valid DER-encoded secp256r1 signature so that normalizeSignature
        // succeeds inside appendPasskeySignature.
        mockProvider.authenticationResult = WebAuthnAuthenticationResult(
            credentialId = ByteArray(16) { it.toByte() },
            authenticatorData = ByteArray(37) { it.toByte() },
            clientDataJSON = """{"type":"webauthn.get","challenge":"test"}""".encodeToByteArray(),
            signature = VALID_DER_SIGNATURE
        )

        val accountXdr = buildAccountEntryXdr(deployer).toXdrBase64()
        val authEntry = buildAuthEntry(VERIFIER_B)
        val authEntryXdr = authEntry.toXdrBase64()
        val sorobanDataXdr = buildMinimalSorobanData().toXdrBase64()
        val countZeroXdr = SCValXdr.U32(Uint32Xdr(0u)).toXdrBase64()
        val txHash = "b1721e2a61e9a6b3c6c2e5c0d4c0a5f3e2d1c0b9a8f7e6d5c4b3a2f1e0d9c8b7"

        val mockServer = buildSequentialMockServerWithSubmission(
            accountXdrBase64 = accountXdr,
            authEntryBase64 = authEntryXdr,
            sorobanDataBase64 = sorobanDataXdr,
            countXdrBase64 = countZeroXdr,
            txHash = txHash
        )

        val kit = OZSmartAccountKit.createWithServer(
            config = buildPasskeyConfig(deployer, mockProvider),
            sorobanServer = mockServer
        )
        kit.setConnectedState("test-credential-id", VERIFIER_B)

        val result = kit.multiSignerManager.submitWithMultipleSigners(
            hostFunction = stubHostFunction(VERIFIER_B),
            selectedSigners = listOf(passkeySignerWithKeyData()),
            resolveContextRuleIds = { _, _ -> emptyList() }
        )

        assertTrue(result.success, "Result must be successful; error=${result.error}")
        assertNotNull(result.hash, "Result must carry a transaction hash")
        assertEquals(txHash, result.hash, "Hash must match sendTransaction response")
        // authenticate was called once for the single passkey signer
        assertEquals(1, mockProvider.authenticateCallCount, "authenticate must be called once")
    }

    // ========================================================================
    // Passkey signer — invalid DER signature
    // ========================================================================

    @Test
    fun test_submitWithMultipleSigners_passkey_invalidDerSignature_throwsValidationException() = runTest {
        val deployer = KeyPair.random()
        val mockProvider = MockWebAuthnProvider()

        // Return bytes that are not valid DER — normalizeSignature rejects them with
        // ValidationException.InvalidInput which propagates uncaught from appendPasskeySignature.
        mockProvider.authenticationResult = WebAuthnAuthenticationResult(
            credentialId = ByteArray(16) { it.toByte() },
            authenticatorData = ByteArray(37) { it.toByte() },
            clientDataJSON = """{"type":"webauthn.get","challenge":"test"}""".encodeToByteArray(),
            signature = INVALID_DER_SIGNATURE
        )

        val accountXdr = buildAccountEntryXdr(deployer).toXdrBase64()
        val authEntry = buildAuthEntry(VERIFIER_B)
        val authEntryXdr = authEntry.toXdrBase64()
        val sorobanDataXdr = buildMinimalSorobanData().toXdrBase64()
        val countZeroXdr = SCValXdr.U32(Uint32Xdr(0u)).toXdrBase64()

        val mockServer = buildSequentialMockServer(
            accountXdrBase64 = accountXdr,
            authEntryBase64 = authEntryXdr,
            sorobanDataBase64 = sorobanDataXdr,
            countXdrBase64 = countZeroXdr
        )

        val kit = OZSmartAccountKit.createWithServer(
            config = buildPasskeyConfig(deployer, mockProvider),
            sorobanServer = mockServer
        )
        kit.setConnectedState("test-credential-id", VERIFIER_B)

        // normalizeSignature throws ValidationException.InvalidInput for non-DER bytes
        assertFailsWith<ValidationException.InvalidInput> {
            kit.multiSignerManager.submitWithMultipleSigners(
                hostFunction = stubHostFunction(VERIFIER_B),
                selectedSigners = listOf(passkeySignerWithKeyData()),
                resolveContextRuleIds = { _, _ -> emptyList() }
            )
        }
    }

    // ========================================================================
    // Passkey signer — WebAuthn authentication throws
    // ========================================================================

    @Test
    fun test_submitWithMultipleSigners_passkey_authenticationThrows_wrappedInWebAuthnException() = runTest {
        val deployer = KeyPair.random()
        val mockProvider = MockWebAuthnProvider()

        // Simulate the WebAuthn ceremony failing (e.g., user cancelled)
        mockProvider.authenticationException =
            com.soneso.stellar.sdk.smartaccount.core.WebAuthnException.authenticationFailed(
                "Simulated authentication failure"
            )

        val accountXdr = buildAccountEntryXdr(deployer).toXdrBase64()
        val authEntry = buildAuthEntry(VERIFIER_B)
        val authEntryXdr = authEntry.toXdrBase64()
        val sorobanDataXdr = buildMinimalSorobanData().toXdrBase64()
        val countZeroXdr = SCValXdr.U32(Uint32Xdr(0u)).toXdrBase64()

        val mockServer = buildSequentialMockServer(
            accountXdrBase64 = accountXdr,
            authEntryBase64 = authEntryXdr,
            sorobanDataBase64 = sorobanDataXdr,
            countXdrBase64 = countZeroXdr
        )

        val kit = OZSmartAccountKit.createWithServer(
            config = buildPasskeyConfig(deployer, mockProvider),
            sorobanServer = mockServer
        )
        kit.setConnectedState("test-credential-id", VERIFIER_B)

        // The exception from authenticate() is caught and re-wrapped as WebAuthnException
        assertFailsWith<com.soneso.stellar.sdk.smartaccount.core.WebAuthnException> {
            kit.multiSignerManager.submitWithMultipleSigners(
                hostFunction = stubHostFunction(VERIFIER_B),
                selectedSigners = listOf(passkeySignerWithKeyData()),
                resolveContextRuleIds = { _, _ -> emptyList() }
            )
        }
    }

    // ========================================================================
    // Passkey signer — no WebAuthn provider configured
    // ========================================================================

    @Test
    fun test_submitWithMultipleSigners_passkey_noProvider_throwsValidationInvalidInput() = runTest {
        val deployer = KeyPair.random()

        // Config with no webauthnProvider set
        val config = OZSmartAccountConfig(
            rpcUrl = "https://soroban-testnet.stellar.org",
            networkPassphrase = Network.TESTNET.networkPassphrase,
            accountWasmHash = "a" + "0".repeat(63),
            webauthnVerifierAddress = VERIFIER_A,
            deployerKeypair = deployer
        )

        val accountXdr = buildAccountEntryXdr(deployer).toXdrBase64()
        val authEntry = buildAuthEntry(VERIFIER_B)
        val authEntryXdr = authEntry.toXdrBase64()
        val sorobanDataXdr = buildMinimalSorobanData().toXdrBase64()
        val countZeroXdr = SCValXdr.U32(Uint32Xdr(0u)).toXdrBase64()

        val mockServer = buildSequentialMockServer(
            accountXdrBase64 = accountXdr,
            authEntryBase64 = authEntryXdr,
            sorobanDataBase64 = sorobanDataXdr,
            countXdrBase64 = countZeroXdr
        )

        val kit = OZSmartAccountKit.createWithServer(config = config, sorobanServer = mockServer)
        kit.setConnectedState("test-credential-id", VERIFIER_B)

        val ex = assertFailsWith<ValidationException.InvalidInput> {
            kit.multiSignerManager.submitWithMultipleSigners(
                hostFunction = stubHostFunction(VERIFIER_B),
                selectedSigners = listOf(passkeySignerWithKeyData()),
                resolveContextRuleIds = { _, _ -> emptyList() }
            )
        }
        assertTrue(
            ex.message.contains("webauthnProvider", ignoreCase = true),
            "Exception must reference webauthnProvider; got: ${ex.message}"
        )
    }

    // ========================================================================
    // Passkey signer — updatePasskeyLastUsed is reached when passkey participates
    //
    // The credentialId supplied via SelectedSigner.Passkey must be non-null for
    // updatePasskeyLastUsed to attempt the credential update. With a credentialId
    // set but no stored credential entry, updateLastUsed is best-effort and swallows
    // the resulting CredentialNotFoundException silently.
    // ========================================================================

    @Test
    fun test_submitWithMultipleSigners_passkeyWithCredentialId_updateLastUsedCalledSilently() = runTest {
        val deployer = KeyPair.random()
        val mockProvider = MockWebAuthnProvider()
        mockProvider.authenticationResult = WebAuthnAuthenticationResult(
            credentialId = ByteArray(16) { it.toByte() },
            authenticatorData = ByteArray(37) { it.toByte() },
            clientDataJSON = """{"type":"webauthn.get","challenge":"test"}""".encodeToByteArray(),
            signature = VALID_DER_SIGNATURE
        )

        val accountXdr = buildAccountEntryXdr(deployer).toXdrBase64()
        val authEntry = buildAuthEntry(VERIFIER_B)
        val authEntryXdr = authEntry.toXdrBase64()
        val sorobanDataXdr = buildMinimalSorobanData().toXdrBase64()
        val countZeroXdr = SCValXdr.U32(Uint32Xdr(0u)).toXdrBase64()
        val txHash = "c2831f3b72eaa7b4d7d3f6e1e5b1b6a4f3e2d1c0b9a8f7e6d5c4b3a2f1e0d9c8"

        val mockServer = buildSequentialMockServerWithSubmission(
            accountXdrBase64 = accountXdr,
            authEntryBase64 = authEntryXdr,
            sorobanDataBase64 = sorobanDataXdr,
            countXdrBase64 = countZeroXdr,
            txHash = txHash
        )

        val kit = OZSmartAccountKit.createWithServer(
            config = buildPasskeyConfig(deployer, mockProvider),
            sorobanServer = mockServer
        )
        kit.setConnectedState("test-credential-id", VERIFIER_B)

        // The passkey has a credentialId set; updatePasskeyLastUsed will attempt
        // credentialManager.updateLastUsed("named-cred-id") and swallow the miss silently.
        val signer = SelectedSigner.Passkey(
            credentialId = "named-cred-id",
            credentialIdBytes = ByteArray(16) { it.toByte() },
            keyData = ByteArray(68) { (it + 1).toByte() }
        )

        // A successful result confirms the signing loop completed and updatePasskeyLastUsed
        // did not propagate an exception.
        val result = kit.multiSignerManager.submitWithMultipleSigners(
            hostFunction = stubHostFunction(VERIFIER_B),
            selectedSigners = listOf(signer),
            resolveContextRuleIds = { _, _ -> emptyList() }
        )

        assertTrue(result.success, "Result must be successful; error=${result.error}")
    }

    // ========================================================================
    // Wallet signer — happy path
    // ========================================================================

    @Test
    fun test_submitWithMultipleSigners_walletOnly_succeeds() = runTest {
        val deployer = KeyPair.random()
        val walletKeypair = KeyPair.random()
        val walletAdapter = KeypairWalletAdapter(walletKeypair)

        val accountXdr = buildAccountEntryXdr(deployer).toXdrBase64()
        val authEntry = buildAuthEntry(VERIFIER_B)
        val authEntryXdr = authEntry.toXdrBase64()
        val sorobanDataXdr = buildMinimalSorobanData().toXdrBase64()
        val countZeroXdr = SCValXdr.U32(Uint32Xdr(0u)).toXdrBase64()
        val txHash = "d3941f4c83fbb8c5e8e4a7f2f6c2c7b5a4f3e2d1c0b9a8f7e6d5c4b3a2f1e0d9"

        val mockServer = buildSequentialMockServerWithSubmission(
            accountXdrBase64 = accountXdr,
            authEntryBase64 = authEntryXdr,
            sorobanDataBase64 = sorobanDataXdr,
            countXdrBase64 = countZeroXdr,
            txHash = txHash
        )

        val kit = OZSmartAccountKit.createWithServer(
            config = buildWalletConfig(deployer, walletAdapter),
            sorobanServer = mockServer
        )
        kit.setConnectedState("test-credential-id", VERIFIER_B)

        val result = kit.multiSignerManager.submitWithMultipleSigners(
            hostFunction = stubHostFunction(VERIFIER_B),
            selectedSigners = listOf(SelectedSigner.Wallet(walletAdapter.address)),
            resolveContextRuleIds = { _, _ -> emptyList() }
        )

        assertTrue(result.success, "Result must be successful; error=${result.error}")
        assertNotNull(result.hash, "Result must carry a transaction hash")
        assertEquals(txHash, result.hash, "Hash must match sendTransaction response")
    }

    // ========================================================================
    // Wallet signer — no signing source at all (no keypair, no adapter)
    // ========================================================================

    @Test
    fun test_submitWithMultipleSigners_wallet_noSigningSource_throwsValidationInvalidInput() = runTest {
        val deployer = KeyPair.random()

        // Config with no externalWallet adapter and no in-memory keypair registered.
        val config = OZSmartAccountConfig(
            rpcUrl = "https://soroban-testnet.stellar.org",
            networkPassphrase = Network.TESTNET.networkPassphrase,
            accountWasmHash = "a" + "0".repeat(63),
            webauthnVerifierAddress = VERIFIER_A,
            deployerKeypair = deployer
        )

        val kit = OZSmartAccountKit.create(config)
        kit.setConnectedState("test-credential-id", VERIFIER_B)

        val unregisteredAddress = "GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN"
        val ex = assertFailsWith<ValidationException.InvalidInput> {
            kit.multiSignerManager.submitWithMultipleSigners(
                hostFunction = stubHostFunction(VERIFIER_B),
                selectedSigners = listOf(SelectedSigner.Wallet(unregisteredAddress))
            )
        }
        // The wallet not-found message must name both runtime remedies.
        assertTrue(
            ex.message.contains(unregisteredAddress, ignoreCase = false),
            "Exception must identify the address with no signing source; got: ${ex.message}"
        )
        assertTrue(
            ex.message.contains("kit.externalSigners.addFromSecret", ignoreCase = false),
            "Exception must name kit.externalSigners.addFromSecret; got: ${ex.message}"
        )
        assertTrue(
            ex.message.contains("config.externalWallet", ignoreCase = false),
            "Exception must name config.externalWallet; got: ${ex.message}"
        )
    }

    // ========================================================================
    // Wallet signer — in-memory keypair registered, no adapter → validation passes
    // ========================================================================

    @Test
    fun test_submitWithMultipleSigners_wallet_inMemoryKeypairNoAdapter_passesValidation() = runTest {
        val deployer = KeyPair.random()
        val walletKeypair = KeyPair.random()

        // No externalWallet adapter — the in-memory keypair custody model is the only source.
        val config = OZSmartAccountConfig(
            rpcUrl = "https://soroban-testnet.stellar.org",
            networkPassphrase = Network.TESTNET.networkPassphrase,
            accountWasmHash = "a" + "0".repeat(63),
            webauthnVerifierAddress = VERIFIER_A,
            deployerKeypair = deployer
        )

        val kit = OZSmartAccountKit.create(config)
        val walletAddress = kit.externalSigners.addFromSecret(walletKeypair.getSecretSeed()!!.concatToString())
        kit.setConnectedState("test-credential-id", VERIFIER_B)

        // With an in-memory keypair registered, the per-address validation passes and the
        // call proceeds past validation to the simulation step. A network-related failure
        // (not InvalidInput) confirms validation did not reject the wallet signer.
        val ex = assertFailsWith<Exception> {
            kit.multiSignerManager.submitWithMultipleSigners(
                hostFunction = stubHostFunction(VERIFIER_B),
                selectedSigners = listOf(SelectedSigner.Wallet(walletAddress))
            )
        }
        assertFalse(
            ex is ValidationException.InvalidInput,
            "Wallet validation must pass with an in-memory keypair and no adapter; got: ${ex::class.simpleName}: ${ex.message}"
        )
    }

    // ========================================================================
    // Wallet signer — adapter cannot sign for address
    // ========================================================================

    @Test
    fun test_submitWithMultipleSigners_wallet_adapterCannotSign_throwsValidationInvalidInput() = runTest {
        val deployer = KeyPair.random()

        // Adapter that always returns false for canSignFor
        val noSignAdapter = object : ExternalWalletAdapter {
            override suspend fun connect(): ConnectedWallet? = null
            override suspend fun disconnect() { /* no-op */ }
            override fun canSignFor(address: String): Boolean = false
            override fun getConnectedWallets(): List<ConnectedWallet> = emptyList()
            override suspend fun signAuthEntry(
                preimageXdr: String,
                options: SignAuthEntryOptions?
            ): SignAuthEntryResult = throw UnsupportedOperationException("not reachable")
        }

        val config = OZSmartAccountConfig(
            rpcUrl = "https://soroban-testnet.stellar.org",
            networkPassphrase = Network.TESTNET.networkPassphrase,
            accountWasmHash = "a" + "0".repeat(63),
            webauthnVerifierAddress = VERIFIER_A,
            deployerKeypair = deployer,
            externalWallet = noSignAdapter
        )

        val kit = OZSmartAccountKit.create(config)
        kit.setConnectedState("test-credential-id", VERIFIER_B)

        val unregisteredAddress = "GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN"
        val ex = assertFailsWith<ValidationException.InvalidInput> {
            kit.multiSignerManager.submitWithMultipleSigners(
                hostFunction = stubHostFunction(VERIFIER_B),
                selectedSigners = listOf(SelectedSigner.Wallet(unregisteredAddress))
            )
        }
        assertTrue(
            ex.message.contains("selectedSigners", ignoreCase = true),
            "Exception must reference selectedSigners; got: ${ex.message}"
        )
    }

    // ========================================================================
    // Mixed passkey + wallet — both slots filled
    // ========================================================================

    @Test
    fun test_submitWithMultipleSigners_mixedPasskeyAndWallet_succeeds() = runTest {
        val deployer = KeyPair.random()
        val walletKeypair = KeyPair.random()
        val walletAdapter = KeypairWalletAdapter(walletKeypair)
        val mockProvider = MockWebAuthnProvider()

        mockProvider.authenticationResult = WebAuthnAuthenticationResult(
            credentialId = ByteArray(16) { it.toByte() },
            authenticatorData = ByteArray(37) { it.toByte() },
            clientDataJSON = """{"type":"webauthn.get","challenge":"test"}""".encodeToByteArray(),
            signature = VALID_DER_SIGNATURE
        )

        val accountXdr = buildAccountEntryXdr(deployer).toXdrBase64()
        val authEntry = buildAuthEntry(VERIFIER_B)
        val authEntryXdr = authEntry.toXdrBase64()
        val sorobanDataXdr = buildMinimalSorobanData().toXdrBase64()
        val countZeroXdr = SCValXdr.U32(Uint32Xdr(0u)).toXdrBase64()
        val txHash = "e4052a5d94acb9d6f9f5b8a3a7d3d8c6b5a4f3e2d1c0b9a8f7e6d5c4b3a2f1e0"

        val mockServer = buildSequentialMockServerWithSubmission(
            accountXdrBase64 = accountXdr,
            authEntryBase64 = authEntryXdr,
            sorobanDataBase64 = sorobanDataXdr,
            countXdrBase64 = countZeroXdr,
            txHash = txHash
        )

        val kit = OZSmartAccountKit.createWithServer(
            config = buildMixedConfig(deployer, mockProvider, walletAdapter),
            sorobanServer = mockServer
        )
        kit.setConnectedState("test-credential-id", VERIFIER_B)

        val selectedSigners = listOf(
            passkeySignerWithKeyData(),
            SelectedSigner.Wallet(walletAdapter.address)
        )

        val result = kit.multiSignerManager.submitWithMultipleSigners(
            hostFunction = stubHostFunction(VERIFIER_B),
            selectedSigners = selectedSigners,
            resolveContextRuleIds = { _, _ -> emptyList() }
        )

        assertTrue(result.success, "Result must be successful; error=${result.error}")
        assertNotNull(result.hash, "Result must carry a transaction hash")
        assertEquals(txHash, result.hash, "Hash must match sendTransaction response")
        // Passkey was included — authenticate must have been called once
        assertEquals(1, mockProvider.authenticateCallCount, "Passkey authenticate must be called once")
    }

    // ========================================================================
    // Wallet signer — in-memory keypair custody model (full submit round-trip)
    // ========================================================================

    @Test
    fun test_submitWithMultipleSigners_walletInMemoryKeypair_succeeds() = runTest {
        val deployer = KeyPair.random()
        val walletKeypair = KeyPair.random()

        // No externalWallet adapter is configured. The wallet signer is backed solely by an
        // in-memory keypair registered through kit.externalSigners.addFromSecret(...).
        val config = OZSmartAccountConfig(
            rpcUrl = "https://soroban-testnet.stellar.org",
            networkPassphrase = Network.TESTNET.networkPassphrase,
            accountWasmHash = "a" + "0".repeat(63),
            webauthnVerifierAddress = VERIFIER_A,
            deployerKeypair = deployer
        )

        val accountXdr = buildAccountEntryXdr(deployer).toXdrBase64()
        val authEntry = buildAuthEntry(VERIFIER_B)
        val authEntryXdr = authEntry.toXdrBase64()
        val sorobanDataXdr = buildMinimalSorobanData().toXdrBase64()
        val countZeroXdr = SCValXdr.U32(Uint32Xdr(0u)).toXdrBase64()
        val txHash = "f6052a5d94acb9d6f9f5b8a3a7d3d8c6b5a4f3e2d1c0b9a8f7e6d5c4b3a2f1e0"

        val mockServer = buildSequentialMockServerWithSubmission(
            accountXdrBase64 = accountXdr,
            authEntryBase64 = authEntryXdr,
            sorobanDataBase64 = sorobanDataXdr,
            countXdrBase64 = countZeroXdr,
            txHash = txHash
        )

        val kit = OZSmartAccountKit.createWithServer(config = config, sorobanServer = mockServer)
        val walletAddress = kit.externalSigners.addFromSecret(walletKeypair.getSecretSeed()!!.concatToString())
        kit.setConnectedState("test-credential-id", VERIFIER_B)

        val result = kit.multiSignerManager.submitWithMultipleSigners(
            hostFunction = stubHostFunction(VERIFIER_B),
            selectedSigners = listOf(SelectedSigner.Wallet(walletAddress)),
            resolveContextRuleIds = { _, _ -> emptyList() }
        )

        assertTrue(result.success, "Result must be successful; error=${result.error}")
        assertNotNull(result.hash, "Result must carry a transaction hash")
        assertEquals(txHash, result.hash, "Hash must match sendTransaction response")
    }

    // ========================================================================
    // signAuthEntry precedence — in-memory keypair wins over the wallet adapter
    // ========================================================================

    @OptIn(ExperimentalEncodingApi::class)
    @Test
    fun test_signAuthEntry_keypairAndAdapterForSameAddress_keypairWins() = runTest {
        // Register an address as BOTH an in-memory keypair AND a wallet adapter able to sign
        // for it. The manager must resolve the in-memory keypair first: the returned signature
        // verifies under the keypair public key, and the adapter is never invoked.
        val walletKeypair = KeyPair.random()
        val walletAddress = walletKeypair.getAccountId()

        val countingAdapter = object : ExternalWalletAdapter {
            var signCallCount = 0
                private set

            override suspend fun connect(): ConnectedWallet = ConnectedWallet(
                address = walletAddress,
                walletId = "test-wallet",
                walletName = "Test Wallet"
            )

            override suspend fun disconnect() { /* no-op */ }

            override fun canSignFor(address: String): Boolean = address == walletAddress

            override fun getConnectedWallets(): List<ConnectedWallet> = listOf(
                ConnectedWallet(address = walletAddress, walletId = "test-wallet", walletName = "Test Wallet")
            )

            override fun getWalletForAddress(address: String): ConnectedWallet? =
                if (address == walletAddress) {
                    ConnectedWallet(address = walletAddress, walletId = "test-wallet", walletName = "Test Wallet")
                } else null

            override suspend fun signAuthEntry(
                preimageXdr: String,
                options: SignAuthEntryOptions?
            ): SignAuthEntryResult {
                signCallCount++
                // Return a deliberately distinct (invalid) signature so a keypair-vs-adapter
                // mix-up would be detectable; this path must not be reached.
                return SignAuthEntryResult(
                    signedAuthEntry = Base64.encode(ByteArray(64)),
                    signerAddress = walletAddress
                )
            }
        }

        val config = OZSmartAccountConfig(
            rpcUrl = "https://soroban-testnet.stellar.org",
            networkPassphrase = Network.TESTNET.networkPassphrase,
            accountWasmHash = "a" + "0".repeat(63),
            webauthnVerifierAddress = VERIFIER_A,
            externalWallet = countingAdapter
        )

        val kit = OZSmartAccountKit.create(config)
        // Register the same address as an in-memory keypair on the kit-owned manager.
        kit.externalSigners.addFromSecret(walletKeypair.getSecretSeed()!!.concatToString())

        // Build a base64 preimage and sign it through the manager.
        val preimageBytes = ByteArray(48) { (it + 7).toByte() }
        val preimageBase64 = Base64.encode(preimageBytes)

        val result = kit.externalSigners.signAuthEntry(walletAddress, preimageBase64)

        // The keypair path hashes the preimage (SHA-256) and signs the hash. Recompute and verify.
        val expectedDigest = getSha256Crypto().hash(preimageBytes)
        val signatureBytes = Base64.decode(result.signedAuthEntry)
        assertTrue(
            walletKeypair.verify(expectedDigest, signatureBytes),
            "Signature must verify under the in-memory keypair public key (keypair-first precedence)"
        )
        assertEquals(
            0,
            countingAdapter.signCallCount,
            "The wallet adapter must NOT be invoked when an in-memory keypair is registered for the address"
        )
        assertEquals(walletAddress, result.signerAddress, "signerAddress must be the keypair G-address")
    }

    // ========================================================================
    // mapToSmartAccountSigners — missing keyData on passkey throws InvalidInput
    // ========================================================================

    @Test
    fun test_mapToSmartAccountSigners_passkeyMissingKeyData_throwsValidationInvalidInput() = runTest {
        val deployer = KeyPair.random()
        val mockProvider = MockWebAuthnProvider()
        mockProvider.authenticationResult = WebAuthnAuthenticationResult(
            credentialId = ByteArray(16) { it.toByte() },
            authenticatorData = ByteArray(37) { it.toByte() },
            clientDataJSON = """{"type":"webauthn.get","challenge":"test"}""".encodeToByteArray(),
            signature = VALID_DER_SIGNATURE
        )

        val accountXdr = buildAccountEntryXdr(deployer).toXdrBase64()
        val authEntry = buildAuthEntry(VERIFIER_B)
        val authEntryXdr = authEntry.toXdrBase64()
        val sorobanDataXdr = buildMinimalSorobanData().toXdrBase64()
        val countZeroXdr = SCValXdr.U32(Uint32Xdr(0u)).toXdrBase64()

        val mockServer = buildSequentialMockServer(
            accountXdrBase64 = accountXdr,
            authEntryBase64 = authEntryXdr,
            sorobanDataBase64 = sorobanDataXdr,
            countXdrBase64 = countZeroXdr
        )

        val kit = OZSmartAccountKit.createWithServer(
            config = buildPasskeyConfig(deployer, mockProvider),
            sorobanServer = mockServer
        )
        kit.setConnectedState("test-credential-id", VERIFIER_B)

        // Passkey with no keyData — mapToSmartAccountSigners throws immediately
        val signerNoKeyData = SelectedSigner.Passkey(
            credentialId = "cred-id",
            credentialIdBytes = ByteArray(16) { it.toByte() },
            keyData = null
        )

        val ex = assertFailsWith<ValidationException.InvalidInput> {
            kit.multiSignerManager.submitWithMultipleSigners(
                hostFunction = stubHostFunction(VERIFIER_B),
                selectedSigners = listOf(signerNoKeyData),
                resolveContextRuleIds = { _, _ -> emptyList() }
            )
        }
        assertTrue(
            ex.message.contains("selectedSigners", ignoreCase = true) ||
                ex.message.contains("keyData", ignoreCase = true),
            "Exception must reference selectedSigners or keyData; got: ${ex.message}"
        )
    }

    // ========================================================================
    // validateSelectedSigners — wallet signer routing when adapter present but
    // canSignFor returns false (reinforces the per-wallet loop branch)
    // ========================================================================

    @Test
    fun test_validateSelectedSigners_walletSignerAddressNotRegistered_throwsInvalidInput() = runTest {
        val deployer = KeyPair.random()
        val walletKeypair = KeyPair.random()
        val walletAdapter = KeypairWalletAdapter(walletKeypair)

        val config = OZSmartAccountConfig(
            rpcUrl = "https://soroban-testnet.stellar.org",
            networkPassphrase = Network.TESTNET.networkPassphrase,
            accountWasmHash = "a" + "0".repeat(63),
            webauthnVerifierAddress = VERIFIER_A,
            deployerKeypair = deployer,
            externalWallet = walletAdapter
        )

        val kit = OZSmartAccountKit.create(config)
        kit.setConnectedState("test-credential-id", VERIFIER_B)

        // Wallet address that is NOT the keypair address the adapter was built with
        val unregisteredAddress = "GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN"

        val ex = assertFailsWith<ValidationException.InvalidInput> {
            kit.multiSignerManager.submitWithMultipleSigners(
                hostFunction = stubHostFunction(VERIFIER_B),
                selectedSigners = listOf(SelectedSigner.Wallet(unregisteredAddress))
            )
        }
        assertTrue(
            ex.message.contains("selectedSigners", ignoreCase = true),
            "Exception must reference selectedSigners; got: ${ex.message}"
        )
        assertTrue(
            ex.message.contains(unregisteredAddress, ignoreCase = false),
            "Exception must identify the unregistered address; got: ${ex.message}"
        )
    }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

private fun hexToByteArray(hex: String): ByteArray {
    val len = hex.length
    require(len % 2 == 0) { "Hex string must have even length" }
    return ByteArray(len / 2) { i ->
        val index = i * 2
        hex.substring(index, index + 2).toInt(16).toByte()
    }
}
