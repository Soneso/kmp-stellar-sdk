//
//  OZConstructorPoliciesIntegrationTest.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.integrationTests

import com.soneso.stellar.sdk.FriendBot
import com.soneso.stellar.sdk.KeyPair
import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.smartaccount.core.ContractErrorCodes
import com.soneso.stellar.sdk.smartaccount.core.TransactionException
import com.soneso.stellar.sdk.smartaccount.oz.OZSmartAccountConfig
import com.soneso.stellar.sdk.smartaccount.oz.OZSmartAccountKit
import com.soneso.stellar.sdk.smartaccount.oz.PolicyInstallParams
import com.soneso.stellar.sdk.smartaccount.oz.WebAuthnRegistrationResult
import com.soneso.stellar.sdk.unitTests.smartaccount.MockWebAuthnProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Testnet integration tests for constructor-time policy installation: a wallet deployed with
 * `policies` installs them on the Default context rule via the contract constructor.
 *
 * The deploy transaction is signed by the deployer, not the passkey, so the passkey side is a
 * [MockWebAuthnProvider] carrying a valid secp256r1 point (the curve's generator) as the public
 * key: it passes client-side curve validation and the on-chain verifier's key canonicalization,
 * and is never used for signing here. A random credential ID gives every run a fresh derived
 * contract address.
 */
class OZConstructorPoliciesIntegrationTest {

    private val rpcUrl = "https://soroban-testnet.stellar.org"
    private val accountWasmHash = "86b49fe03f7df0ad1c2a28bd8361b923ab57096e09f397f92f0c00ae3bd06d28"
    private val webauthnVerifier = "CB26VN37RCVNTHJZDEPK6IRO2MMTS3Z2IEO5JD5BINY2OOJ5KKJG7NKY"

    /** SimpleThreshold policy contract deployed on testnet. */
    private val thresholdPolicy = "CAZJ3UVRY3R3S5C5BH32GMYBRSN23N75ZEEXEOLXOUUAHDFIMVP4AXUC"

    /** Uncompressed secp256r1 generator point: a valid on-curve public key. */
    private val p256GeneratorPubKey: ByteArray = (
        "04" +
            "6B17D1F2E12C4247F8BCE6E563A440F277037D812DEB33A0F4A13945D898C296" +
            "4FE342E2FE1A7F9B8EE7EB4A7C0F9E162BCE33576B315ECECBB6406837BF51F5"
        ).chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private suspend fun createKit(): OZSmartAccountKit {
        val deployer = KeyPair.random()
        FriendBot.fundTestnetAccount(deployer.getAccountId())

        val provider = MockWebAuthnProvider()
        provider.registrationResult = WebAuthnRegistrationResult(
            credentialId = Random.nextBytes(16),
            publicKey = p256GeneratorPubKey,
            attestationObject = MockWebAuthnProvider.testAttestationObject(),
            transports = listOf("internal"),
            deviceType = "multiDevice",
            backedUp = true
        )

        return OZSmartAccountKit.create(
            OZSmartAccountConfig(
                rpcUrl = rpcUrl,
                networkPassphrase = Network.TESTNET.networkPassphrase,
                accountWasmHash = accountWasmHash,
                webauthnVerifierAddress = webauthnVerifier,
                webauthnProvider = provider,
                deployerKeypair = deployer
            )
        )
    }

    @Test
    fun testDeployWithThresholdOnePolicy_installsOnDefaultRule() = runTest(timeout = 300.seconds) {
        // Run off the test dispatcher so the deploy-confirmation polling delays are
        // wall-clock, not virtual time.
        withContext(Dispatchers.Default) {
            val kit = createKit()
            try {
                val result = kit.walletOperations.createWallet(
                    userName = "Constructor Policy Test",
                    autoSubmit = true,
                    policies = mapOf(
                        thresholdPolicy to PolicyInstallParams.SimpleThreshold(threshold = 1u).toScVal()
                    )
                )
                assertNotNull(result.contractId, "deploy must yield a contract address")

                // Read back the Default rule and verify the policy was installed at construction.
                val rules = kit.contextRuleManager.listContextRules()
                val defaultRule = rules.first { it.id == 0u }
                assertTrue(
                    defaultRule.policies.contains(thresholdPolicy),
                    "Default rule must carry the constructor-installed threshold policy, got: ${defaultRule.policies}"
                )
            } finally {
                kit.close()
            }
        }
    }

    @Test
    fun testDeployWithTwoOfOneThreshold_failsAtSimulationWithInvalidThreshold() = runTest(timeout = 300.seconds) {
        val kit = createKit()
        try {
            // A threshold of 2 exceeds the Default rule's single initial signer; the policy
            // contract rejects the install during deploy simulation.
            val exception = assertFailsWith<TransactionException> {
                kit.walletOperations.createWallet(
                    userName = "Constructor Policy Negative Test",
                    autoSubmit = false,
                    policies = mapOf(
                        thresholdPolicy to PolicyInstallParams.SimpleThreshold(threshold = 2u).toScVal()
                    )
                )
            }
            val decoded = ContractErrorCodes.decodeFromMessage(exception.message)
            assertNotNull(decoded, "simulation error must carry a decodable contract error, got: ${exception.message}")
            assertEquals(3201, decoded.code)
            assertEquals("InvalidThreshold", decoded.name)
            assertEquals("SimpleThresholdError", decoded.contract)
        } finally {
            kit.close()
        }
    }
}
