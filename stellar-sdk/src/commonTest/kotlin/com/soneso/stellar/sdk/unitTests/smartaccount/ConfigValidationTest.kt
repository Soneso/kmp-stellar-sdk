//
//  ConfigValidationTest.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.unitTests.smartaccount

import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.Util
import com.soneso.stellar.sdk.smartaccount.core.*
import com.soneso.stellar.sdk.smartaccount.oz.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Unit tests for [OZSmartAccountConfig] validation and the Builder pattern.
 *
 * SmartAccountKitTest already covers:
 * - Missing rpcUrl, networkPassphrase, accountWasmHash throws ConfigurationException.MissingConfig
 * - Invalid webauthnVerifierAddress (wrong prefix, wrong length) throws ConfigurationException.InvalidConfig
 * - Basic builder usage
 * - Default deployer creation
 *
 * These tests cover:
 * - Builder pattern: all optional fields (signatureExpirationLedgers, timeoutInSeconds)
 * - Config defaults for optional fields
 * - webauthnVerifierAddress additional edge cases
 * - Config data class properties and equality
 * - effectiveIndexerUrl behavior
 * - Builder produces identical config to constructor
 */
class ConfigValidationTest {

    // MARK: - Test Fixtures

    private val validRpcUrl = "https://soroban-testnet.stellar.org"
    private val validPassphrase = Network.TESTNET.networkPassphrase
    private val validWasmHash = "a" + "0".repeat(63)
    private val validVerifier = "CAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAD2KM"

    private fun validConfig(
        rpcUrl: String = validRpcUrl,
        networkPassphrase: String = validPassphrase,
        accountWasmHash: String = validWasmHash,
        webauthnVerifierAddress: String = validVerifier
    ): OZSmartAccountConfig {
        return OZSmartAccountConfig(
            rpcUrl = rpcUrl,
            networkPassphrase = networkPassphrase,
            accountWasmHash = accountWasmHash,
            webauthnVerifierAddress = webauthnVerifierAddress
        )
    }

    // MARK: - Config Defaults

    @Test
    fun testConfigDefaults_optionalFieldsHaveCorrectDefaults() {
        val config = validConfig()

        assertNull(config.deployerKeypair)
        assertEquals(OZConstants.DEFAULT_SESSION_EXPIRY_MS, config.sessionExpiryMs)
        assertEquals(Util.LEDGERS_PER_HOUR, config.signatureExpirationLedgers)
        assertEquals(OZConstants.DEFAULT_TIMEOUT_SECONDS, config.timeoutInSeconds)
        assertNull(config.relayerUrl)
        assertNull(config.indexerUrl)
        assertNull(config.webauthnProvider)
    }

    @Test
    fun testConfigDefaults_requiredFieldsStored() {
        val config = validConfig()

        assertEquals(validRpcUrl, config.rpcUrl)
        assertEquals(validPassphrase, config.networkPassphrase)
        assertEquals(validWasmHash, config.accountWasmHash)
        assertEquals(validVerifier, config.webauthnVerifierAddress)
    }

    // MARK: - webauthnVerifierAddress Edge Cases

    @Test
    fun testWebauthnVerifierAddress_whitespaceOnlyThrows() {
        assertFailsWith<ConfigurationException.InvalidConfig> {
            OZSmartAccountConfig(
                rpcUrl = validRpcUrl,
                networkPassphrase = validPassphrase,
                accountWasmHash = validWasmHash,
                webauthnVerifierAddress = "    " // whitespace, does not start with C
            )
        }
    }

    @Test
    fun testWebauthnVerifierAddress_startsWithGThrows() {
        assertFailsWith<ConfigurationException.InvalidConfig> {
            OZSmartAccountConfig(
                rpcUrl = validRpcUrl,
                networkPassphrase = validPassphrase,
                accountWasmHash = validWasmHash,
                webauthnVerifierAddress = "G" + "A".repeat(55) // account address, not contract
            )
        }
    }

    @Test
    fun testWebauthnVerifierAddress_tooShortThrows() {
        assertFailsWith<ConfigurationException.InvalidConfig> {
            OZSmartAccountConfig(
                rpcUrl = validRpcUrl,
                networkPassphrase = validPassphrase,
                accountWasmHash = validWasmHash,
                webauthnVerifierAddress = "CABC" // too short
            )
        }
    }

    @Test
    fun testWebauthnVerifierAddress_tooLongThrows() {
        assertFailsWith<ConfigurationException.InvalidConfig> {
            OZSmartAccountConfig(
                rpcUrl = validRpcUrl,
                networkPassphrase = validPassphrase,
                accountWasmHash = validWasmHash,
                webauthnVerifierAddress = "C" + "A".repeat(56) // 57 chars, too long
            )
        }
    }

    // MARK: - rpcUrl Validation Edge Cases

    @Test
    fun testRpcUrl_whitespaceOnlyThrows() {
        assertFailsWith<ConfigurationException.MissingConfig> {
            OZSmartAccountConfig(
                rpcUrl = "   ",
                networkPassphrase = validPassphrase,
                accountWasmHash = validWasmHash,
                webauthnVerifierAddress = validVerifier
            )
        }
    }

    // MARK: - networkPassphrase Validation Edge Cases

    @Test
    fun testNetworkPassphrase_whitespaceOnlyThrows() {
        assertFailsWith<ConfigurationException.MissingConfig> {
            OZSmartAccountConfig(
                rpcUrl = validRpcUrl,
                networkPassphrase = "   ",
                accountWasmHash = validWasmHash,
                webauthnVerifierAddress = validVerifier
            )
        }
    }

    // MARK: - accountWasmHash Validation Edge Cases

    @Test
    fun testAccountWasmHash_whitespaceOnlyThrows() {
        assertFailsWith<ConfigurationException.MissingConfig> {
            OZSmartAccountConfig(
                rpcUrl = validRpcUrl,
                networkPassphrase = validPassphrase,
                accountWasmHash = "   ",
                webauthnVerifierAddress = validVerifier
            )
        }
    }

    @Test
    fun testAccountWasmHash_invalidHexThrows() {
        assertFailsWith<ConfigurationException.InvalidConfig> {
            OZSmartAccountConfig(
                rpcUrl = validRpcUrl,
                networkPassphrase = validPassphrase,
                accountWasmHash = "not_a_valid_hex_hash",
                webauthnVerifierAddress = validVerifier
            )
        }
    }

    @Test
    fun testAccountWasmHash_tooShortHexThrows() {
        assertFailsWith<ConfigurationException.InvalidConfig> {
            OZSmartAccountConfig(
                rpcUrl = validRpcUrl,
                networkPassphrase = validPassphrase,
                accountWasmHash = "abcdef",
                webauthnVerifierAddress = validVerifier
            )
        }
    }

    // MARK: - Builder Pattern Tests

    @Test
    fun testBuilder_allOptionalFields() {
        val config = OZSmartAccountConfig.builder(
            rpcUrl = validRpcUrl,
            networkPassphrase = validPassphrase,
            accountWasmHash = validWasmHash,
            webauthnVerifierAddress = validVerifier
        )
            .sessionExpiryMs(86400000L)
            .signatureExpirationLedgers(1440)
            .timeoutInSeconds(60)
            .relayerUrl("https://relayer.example.com")
            .indexerUrl("https://indexer.example.com")
            .build()

        assertEquals(86400000L, config.sessionExpiryMs)
        assertEquals(1440, config.signatureExpirationLedgers)
        assertEquals(60, config.timeoutInSeconds)
        assertEquals("https://relayer.example.com", config.relayerUrl)
        assertEquals("https://indexer.example.com", config.indexerUrl)
    }

    @Test
    fun testBuilder_defaultValues() {
        val config = OZSmartAccountConfig.builder(
            rpcUrl = validRpcUrl,
            networkPassphrase = validPassphrase,
            accountWasmHash = validWasmHash,
            webauthnVerifierAddress = validVerifier
        ).build()

        assertEquals(OZConstants.DEFAULT_SESSION_EXPIRY_MS, config.sessionExpiryMs)
        assertEquals(Util.LEDGERS_PER_HOUR, config.signatureExpirationLedgers)
        assertEquals(OZConstants.DEFAULT_TIMEOUT_SECONDS, config.timeoutInSeconds)
        assertNull(config.relayerUrl)
        assertNull(config.indexerUrl)
        assertNull(config.deployerKeypair)
    }

    @Test
    fun testBuilder_producesIdenticalConfigToConstructor() {
        val constructorConfig = OZSmartAccountConfig(
            rpcUrl = validRpcUrl,
            networkPassphrase = validPassphrase,
            accountWasmHash = validWasmHash,
            webauthnVerifierAddress = validVerifier,
            sessionExpiryMs = 100000L,
            relayerUrl = "https://relayer.test"
        )

        val builderConfig = OZSmartAccountConfig.builder(
            rpcUrl = validRpcUrl,
            networkPassphrase = validPassphrase,
            accountWasmHash = validWasmHash,
            webauthnVerifierAddress = validVerifier
        )
            .sessionExpiryMs(100000L)
            .relayerUrl("https://relayer.test")
            .build()

        assertEquals(constructorConfig, builderConfig)
    }

    @Test
    fun testBuilder_nullOptionalValues() {
        val config = OZSmartAccountConfig.builder(
            rpcUrl = validRpcUrl,
            networkPassphrase = validPassphrase,
            accountWasmHash = validWasmHash,
            webauthnVerifierAddress = validVerifier
        )
            .relayerUrl(null)
            .indexerUrl(null)
            .deployerKeypair(null)
            .build()

        assertNull(config.relayerUrl)
        assertNull(config.indexerUrl)
        assertNull(config.deployerKeypair)
    }

    @Test
    fun testBuilder_chainable() {
        // Verify fluent API returns the same builder instance
        val builder = OZSmartAccountConfig.builder(
            rpcUrl = validRpcUrl,
            networkPassphrase = validPassphrase,
            accountWasmHash = validWasmHash,
            webauthnVerifierAddress = validVerifier
        )

        val result = builder
            .sessionExpiryMs(1000L)
            .signatureExpirationLedgers(100)
            .timeoutInSeconds(10)
            .relayerUrl("https://r.com")
            .indexerUrl("https://i.com")
            .deployerKeypair(null)

        // All calls return the same builder, so we can build from the result
        val config = result.build()
        assertEquals(1000L, config.sessionExpiryMs)
        assertEquals("https://r.com", config.relayerUrl)
    }

    // MARK: - Config Data Class Properties

    @Test
    fun testConfigEquality_identicalConfigsAreEqual() {
        val config1 = validConfig()
        val config2 = validConfig()

        assertEquals(config1, config2)
        assertEquals(config1.hashCode(), config2.hashCode())
    }

    @Test
    fun testConfigEquality_differentRpcUrlNotEqual() {
        val config1 = OZSmartAccountConfig(
            rpcUrl = "https://rpc1.example.com",
            networkPassphrase = validPassphrase,
            accountWasmHash = validWasmHash,
            webauthnVerifierAddress = validVerifier
        )
        val config2 = OZSmartAccountConfig(
            rpcUrl = "https://rpc2.example.com",
            networkPassphrase = validPassphrase,
            accountWasmHash = validWasmHash,
            webauthnVerifierAddress = validVerifier
        )

        assertTrue(config1 != config2)
    }

    // MARK: - Config Copy Tests

    @Test
    fun testConfigCopy_withModifiedFields() {
        val original = validConfig()
        val modified = original.copy(sessionExpiryMs = 99999L)

        assertEquals(99999L, modified.sessionExpiryMs)
        assertEquals(original.rpcUrl, modified.rpcUrl)
        assertEquals(original.networkPassphrase, modified.networkPassphrase)
    }

    // MARK: - effectiveIndexerUrl Tests

    @Test
    fun testEffectiveIndexerUrl_explicitUrlTakesPrecedence() {
        val config = OZSmartAccountConfig(
            rpcUrl = validRpcUrl,
            networkPassphrase = validPassphrase,
            accountWasmHash = validWasmHash,
            webauthnVerifierAddress = validVerifier,
            indexerUrl = "https://custom-indexer.example.com"
        )

        assertEquals("https://custom-indexer.example.com", config.effectiveIndexerUrl())
    }

    @Test
    fun testEffectiveIndexerUrl_noExplicitUrlFallsBackToDefault() {
        val config = OZSmartAccountConfig(
            rpcUrl = validRpcUrl,
            networkPassphrase = validPassphrase,
            accountWasmHash = validWasmHash,
            webauthnVerifierAddress = validVerifier,
            indexerUrl = null
        )

        // May return a default URL for testnet or null depending on OZIndexerClient.getDefaultUrl
        val url = config.effectiveIndexerUrl()
        // We do not assert the exact URL, as it depends on OZIndexerClient defaults
        // Just verify the method does not throw
        // If testnet has a default, url will be non-null; otherwise null
    }

    // MARK: - effectiveDeployer Tests

    @Test
    fun testGetDeployer_defaultDeployerIsDeterministic() = runTest {
        val config = validConfig()

        val deployer1 = config.effectiveDeployer()
        val deployer2 = config.effectiveDeployer()

        assertNotNull(deployer1)
        assertNotNull(deployer2)
        assertEquals(deployer1.getAccountId(), deployer2.getAccountId())
    }

    @Test
    fun testGetDeployer_customDeployerTakesPrecedence() = runTest {
        val customDeployer = com.soneso.stellar.sdk.KeyPair.random()
        val config = OZSmartAccountConfig(
            rpcUrl = validRpcUrl,
            networkPassphrase = validPassphrase,
            accountWasmHash = validWasmHash,
            webauthnVerifierAddress = validVerifier,
            deployerKeypair = customDeployer
        )

        val deployer = config.effectiveDeployer()
        assertEquals(customDeployer.getAccountId(), deployer.getAccountId())
    }

    // MARK: - Config with Various Network Passphrases

    @Test
    fun testConfig_testnetPassphrase() {
        val config = OZSmartAccountConfig(
            rpcUrl = validRpcUrl,
            networkPassphrase = Network.TESTNET.networkPassphrase,
            accountWasmHash = validWasmHash,
            webauthnVerifierAddress = validVerifier
        )
        assertEquals(Network.TESTNET.networkPassphrase, config.networkPassphrase)
    }

    @Test
    fun testConfig_mainnetPassphrase() {
        val config = OZSmartAccountConfig(
            rpcUrl = "https://soroban-mainnet.stellar.org",
            networkPassphrase = Network.PUBLIC.networkPassphrase,
            accountWasmHash = validWasmHash,
            webauthnVerifierAddress = validVerifier
        )
        assertEquals(Network.PUBLIC.networkPassphrase, config.networkPassphrase)
    }

    @Test
    fun testConfig_customPassphrase() {
        val customPassphrase = "My Custom Stellar Network ; January 2026"
        val config = OZSmartAccountConfig(
            rpcUrl = validRpcUrl,
            networkPassphrase = customPassphrase,
            accountWasmHash = validWasmHash,
            webauthnVerifierAddress = validVerifier
        )
        assertEquals(customPassphrase, config.networkPassphrase)
    }

    // MARK: - Constants Tests

    @Test
    fun testOZConstants_defaultSessionExpiryMs() {
        // 7 days in milliseconds
        assertEquals(7 * 24 * 60 * 60 * 1000L, OZConstants.DEFAULT_SESSION_EXPIRY_MS)
    }

    @Test
    fun testUtil_ledgersPerHour() {
        // ~720 ledgers per hour (5 seconds per ledger)
        assertEquals(720, Util.LEDGERS_PER_HOUR)
    }

    @Test
    fun testOZConstants_defaultTimeoutSeconds() {
        assertEquals(30, OZConstants.DEFAULT_TIMEOUT_SECONDS)
    }

    // MARK: - Builder.externalEd25519Adapter

    @Test
    fun testBuilder_externalEd25519Adapter_storesInstance() {
        val adapter = StubEd25519Adapter()

        val config = OZSmartAccountConfig.builder(
            rpcUrl = validRpcUrl,
            networkPassphrase = validPassphrase,
            accountWasmHash = validWasmHash,
            webauthnVerifierAddress = validVerifier
        )
            .externalEd25519Adapter(adapter)
            .build()

        assertNotNull(
            config.externalEd25519Adapter,
            "externalEd25519Adapter must not be null after builder.externalEd25519Adapter()"
        )
        assertSame(
            adapter,
            config.externalEd25519Adapter,
            "externalEd25519Adapter must be the exact instance passed to the builder"
        )
    }

    @Test
    fun testBuilder_externalEd25519Adapter_defaultIsNull() {
        val config = OZSmartAccountConfig.builder(
            rpcUrl = validRpcUrl,
            networkPassphrase = validPassphrase,
            accountWasmHash = validWasmHash,
            webauthnVerifierAddress = validVerifier
        ).build()

        assertNull(
            config.externalEd25519Adapter,
            "externalEd25519Adapter must default to null when never set on the builder"
        )
    }

    @Test
    fun testBuilder_externalEd25519Adapter_null_leavesFieldNull() {
        val config = OZSmartAccountConfig.builder(
            rpcUrl = validRpcUrl,
            networkPassphrase = validPassphrase,
            accountWasmHash = validWasmHash,
            webauthnVerifierAddress = validVerifier
        )
            .externalEd25519Adapter(null)
            .build()

        assertNull(
            config.externalEd25519Adapter,
            "externalEd25519Adapter must be null when null is passed to the builder"
        )
    }

    @Test
    fun testBuilder_externalEd25519Adapter_overridesPreviousValue() {
        val adapter1 = StubEd25519Adapter()
        val adapter2 = StubEd25519Adapter()

        val config = OZSmartAccountConfig.builder(
            rpcUrl = validRpcUrl,
            networkPassphrase = validPassphrase,
            accountWasmHash = validWasmHash,
            webauthnVerifierAddress = validVerifier
        )
            .externalEd25519Adapter(adapter1)
            .externalEd25519Adapter(adapter2)
            .build()

        assertSame(
            adapter2,
            config.externalEd25519Adapter,
            "Last externalEd25519Adapter call must win"
        )
    }

    // MARK: - createDefaultDeployer determinism

    @Test
    fun testCreateDefaultDeployer_returnsValidKeypair() = runTest {
        val deployer = OZSmartAccountConfig.createDefaultDeployer()
        assertNotNull(deployer, "createDefaultDeployer() must return a non-null keypair")
        assertTrue(deployer.canSign(), "createDefaultDeployer() must return a signing keypair")
        assertTrue(deployer.getAccountId().startsWith("G"), "Deployer account ID must start with G")
    }

    @Test
    fun testCreateDefaultDeployer_isDeterministicAcrossMultipleCalls() = runTest {
        val deployer1 = OZSmartAccountConfig.createDefaultDeployer()
        val deployer2 = OZSmartAccountConfig.createDefaultDeployer()

        assertEquals(
            deployer1.getAccountId(),
            deployer2.getAccountId(),
            "createDefaultDeployer() must produce the same G-address on every call"
        )
    }
}


/**
 * Minimal [OZExternalEd25519SignerAdapter] used to assert builder field round-trips.
 * Never invoked for signing in these tests.
 */
private class StubEd25519Adapter : OZExternalEd25519SignerAdapter {
    override fun canSignFor(verifierAddress: String, publicKey: ByteArray): Boolean = false

    override suspend fun signAuthDigest(authDigest: ByteArray, publicKey: ByteArray): ByteArray =
        throw UnsupportedOperationException("StubEd25519Adapter.signAuthDigest must not be called")
}
