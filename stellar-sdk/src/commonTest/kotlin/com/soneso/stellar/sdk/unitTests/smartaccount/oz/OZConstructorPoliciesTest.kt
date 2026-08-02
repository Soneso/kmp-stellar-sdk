//
//  OZConstructorPoliciesTest.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.unitTests.smartaccount.oz

import com.soneso.stellar.sdk.unitTests.smartaccount.MockWebAuthnProvider
import com.soneso.stellar.sdk.unitTests.smartaccount.buildNoRpcMockServer
import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.StrKey
import com.soneso.stellar.sdk.Util
import com.soneso.stellar.sdk.smartaccount.core.TransactionException
import com.soneso.stellar.sdk.smartaccount.core.ValidationException
import com.soneso.stellar.sdk.smartaccount.oz.OZSmartAccountConfig
import com.soneso.stellar.sdk.smartaccount.oz.OZSmartAccountKit
import com.soneso.stellar.sdk.smartaccount.oz.PolicyInstallParams
import com.soneso.stellar.sdk.smartaccount.oz.WebAuthnRegistrationResult
import com.soneso.stellar.sdk.smartaccount.oz.requireValidPolicies
import com.soneso.stellar.sdk.xdr.SCValXdr
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Constructor-time policy installation: the config-level [OZSmartAccountConfig.defaultPolicies]
 * default and the per-call `policies` override on [OZWalletOperations.createWallet] and
 * [OZWalletOperations.deployPendingCredential], plus the [requireValidPolicies] guard that runs
 * before the passkey ceremony.
 */
class OZConstructorPoliciesTest {

    private val verifier = "CB26VN37RCVNTHJZDEPK6IRO2MMTS3Z2IEO5JD5BINY2OOJ5KKJG7NKY"
    private val wasmHash = "a".repeat(64)
    private val rpcUrl = "https://soroban-testnet.stellar.org"

    private fun policyAddress(seed: Int): String = StrKey.encodeContract(ByteArray(32) { (it + seed).toByte() })
    private fun installParam(): SCValXdr = PolicyInstallParams.SimpleThreshold(threshold = 1u).toScVal()
    private fun policies(n: Int): Map<String, SCValXdr> = (0 until n).associate { policyAddress(it) to installParam() }

    private fun hexToBytes(hex: String): ByteArray =
        ByteArray(hex.length / 2) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    // Uncompressed secp256r1 public key formed from the curve's generator point: a valid
    // on-curve point that registration public-key extraction accepts.
    private fun onCurvePublicKey(): ByteArray =
        byteArrayOf(0x04) +
            hexToBytes("6b17d1f2e12c4247f8bce6e563a440f277037d812deb33a0f4a13945d898c296") +
            hexToBytes("4fe342e2fe1a7f9b8ee7eb4a7c0f9e162bce33576b315ececbb6406837bf51f5")

    private fun config(
        provider: MockWebAuthnProvider? = null,
        defaultPolicies: Map<String, SCValXdr> = emptyMap()
    ) = OZSmartAccountConfig(
        rpcUrl = rpcUrl,
        networkPassphrase = Network.TESTNET.networkPassphrase,
        accountWasmHash = wasmHash,
        webauthnVerifierAddress = verifier,
        webauthnProvider = provider,
        defaultPolicies = defaultPolicies
    )

    // MARK: - requireValidPolicies

    @Test
    fun testRequireValidPolicies_emptyAndAtMax_ok() {
        requireValidPolicies(emptyMap())
        requireValidPolicies(policies(5)) // MAX_POLICIES
    }

    @Test
    fun testRequireValidPolicies_tooMany_throws() {
        assertFailsWith<ValidationException.InvalidInput> {
            requireValidPolicies(policies(6))
        }
    }

    @Test
    fun testRequireValidPolicies_invalidAddress_throws() {
        assertFailsWith<ValidationException.InvalidAddress> {
            requireValidPolicies(mapOf("not-a-contract-address" to installParam()))
        }
    }

    // MARK: - Config defaultPolicies

    @Test
    fun testConfig_defaultPolicies_defaultsEmpty() {
        assertEquals(emptyMap<String, SCValXdr>(), config().defaultPolicies)
    }

    @Test
    fun testConfig_defaultPolicies_viaConstructorAndBuilder() {
        val p = policies(2)
        assertEquals(p, config(defaultPolicies = p).defaultPolicies)

        val built = OZSmartAccountConfig.Builder(
            rpcUrl = rpcUrl,
            networkPassphrase = Network.TESTNET.networkPassphrase,
            accountWasmHash = wasmHash,
            webauthnVerifierAddress = verifier
        ).defaultPolicies(p).build()
        assertEquals(p, built.defaultPolicies)
    }

    // MARK: - createWallet validation-before-ceremony and precedence

    @Test
    fun testCreateWallet_invalidPerCallPolicies_throwsBeforeCeremony() = runTest {
        val provider = MockWebAuthnProvider()
        val kit = OZSmartAccountKit.create(config(provider))
        assertFailsWith<ValidationException.InvalidAddress> {
            kit.walletOperations.createWallet(policies = mapOf("not-a-contract" to installParam()))
        }
        assertEquals(0, provider.registerCallCount, "policy validation must run before the passkey ceremony")
    }

    @Test
    fun testCreateWallet_tooManyPerCallPolicies_throwsBeforeCeremony() = runTest {
        val provider = MockWebAuthnProvider()
        val kit = OZSmartAccountKit.create(config(provider))
        assertFailsWith<ValidationException.InvalidInput> {
            kit.walletOperations.createWallet(policies = policies(6))
        }
        assertEquals(0, provider.registerCallCount)
    }

    @Test
    fun testCreateWallet_usesConfigDefaultWhenNoOverride() = runTest {
        // No per-call policies -> the invalid config default is used -> throws before the ceremony.
        val provider = MockWebAuthnProvider()
        val kit = OZSmartAccountKit.create(config(provider, defaultPolicies = policies(6)))
        assertFailsWith<ValidationException.InvalidInput> {
            kit.walletOperations.createWallet()
        }
        assertEquals(0, provider.registerCallCount)
    }

    @Test
    fun testDeployPendingCredential_invalidPolicies_throwsBeforeCredentialLookup() = runTest {
        // Policy validation runs before the stored-credential lookup: an unknown credential
        // with an invalid policies map fails with the validation error, not NotFound.
        val kit = OZSmartAccountKit.create(config())
        assertFailsWith<ValidationException.InvalidInput> {
            kit.walletOperations.deployPendingCredential(
                credentialId = "unknown-credential",
                policies = policies(6)
            )
        }
    }

    @Test
    fun testCreateWallet_validPolicies_encodedIntoDeployBuildThenRpcFails() = runTest {
        // A valid per-call policies map passes validation, the passkey ceremony runs, and the
        // deploy transaction is built (encoding the constructor policies) up to the deployer
        // account fetch. The RPC-less mock server fails that fetch, which surfaces as a
        // TransactionException — proving the build reaches the network step rather than
        // short-circuiting earlier.
        val provider = MockWebAuthnProvider().apply {
            registrationResult = WebAuthnRegistrationResult(
                credentialId = ByteArray(16) { it.toByte() },
                publicKey = onCurvePublicKey(),
                attestationObject = ByteArray(0),
                transports = listOf("internal"),
                deviceType = "singleDevice",
                backedUp = false
            )
        }
        val kit = OZSmartAccountKit.createWithServer(config(provider), buildNoRpcMockServer())
        assertFailsWith<TransactionException> {
            kit.walletOperations.createWallet(policies = policies(2), autoSubmit = false)
        }
        assertEquals(1, provider.registerCallCount)
    }

    @Test
    fun testDeployPendingCredential_validPolicies_encodedIntoRetryDeployBuild() = runTest {
        // A createWallet whose deploy build fails at the RPC leaves the credential stored as
        // pending. deployPendingCredential re-encodes the constructor policies and rebuilds the
        // deploy up to the same deployer-account fetch, which the RPC-less mock again fails.
        val credentialIdBytes = ByteArray(16) { it.toByte() }
        val provider = MockWebAuthnProvider().apply {
            registrationResult = WebAuthnRegistrationResult(
                credentialId = credentialIdBytes,
                publicKey = onCurvePublicKey(),
                attestationObject = ByteArray(0),
                transports = listOf("internal"),
                deviceType = "singleDevice",
                backedUp = false
            )
        }
        val kit = OZSmartAccountKit.createWithServer(config(provider), buildNoRpcMockServer())
        assertFailsWith<TransactionException> {
            kit.walletOperations.createWallet(autoSubmit = false)
        }

        val credentialId = Util.base64urlEncode(credentialIdBytes)
        assertFailsWith<TransactionException> {
            kit.walletOperations.deployPendingCredential(credentialId = credentialId, policies = policies(2))
        }
    }

    @Test
    fun testCreateWallet_perCallOverridesInvalidConfigDefault() = runTest {
        // A valid per-call override supersedes an invalid config default: validation passes and
        // the ceremony proceeds (a later network step then fails, which is irrelevant here).
        val provider = MockWebAuthnProvider()
        val kit = OZSmartAccountKit.create(config(provider, defaultPolicies = policies(6)))
        try {
            kit.walletOperations.createWallet(policies = emptyMap())
        } catch (_: Throwable) {
            // Expected: a post-ceremony step fails without network access.
        }
        assertEquals(1, provider.registerCallCount, "a valid per-call override must supersede the invalid config default")
    }
}
