//
//  RelayerIndexerClientTest.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.unitTests.smartaccount

import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.smartaccount.core.*
import com.soneso.stellar.sdk.smartaccount.oz.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Isolated construction and validation tests for [OZRelayerClient] and [OZIndexerClient].
 *
 * These tests verify URL validation logic, client version header constants, and the
 * companion-object helpers of [OZIndexerClient]. No real HTTP connections are made.
 */
class RelayerIndexerClientTest {

    // MARK: - OZRelayerClient URL Validation

    @Test
    fun testRelayerClient_blankUrlThrows() {
        assertFailsWith<ConfigurationException.InvalidConfig> {
            OZRelayerClient(relayerUrl = "")
        }
    }

    @Test
    fun testRelayerClient_whitespaceUrlThrows() {
        assertFailsWith<ConfigurationException.InvalidConfig> {
            OZRelayerClient(relayerUrl = "   ")
        }
    }

    @Test
    fun testRelayerClient_httpUrlThrows() {
        assertFailsWith<ConfigurationException.InvalidConfig> {
            OZRelayerClient(relayerUrl = "http://relayer.example.com")
        }
    }

    @Test
    fun testRelayerClient_httpsUrlSucceeds() {
        val client = OZRelayerClient(relayerUrl = "https://relayer.example.com")
        assertTrue(client.isConfigured, "Client with HTTPS URL must be configured")
    }

    @Test
    fun testRelayerClient_localhostHttpUrlSucceeds() {
        val client = OZRelayerClient(relayerUrl = "http://localhost:3000")
        assertTrue(client.isConfigured, "Client with http://localhost URL must be configured for development")
    }

    @Test
    fun testRelayerClient_localhostWithoutPortSucceeds() {
        val client = OZRelayerClient(relayerUrl = "http://localhost")
        assertTrue(client.isConfigured)
    }

    @Test
    fun testRelayerClient_trailingSlashIsNormalized() {
        // A client created with trailing slash should still be configured
        val client = OZRelayerClient(relayerUrl = "https://relayer.example.com/")
        assertTrue(client.isConfigured)
    }

    @Test
    fun testRelayerClient_multipleTrailingSlashesAreNormalized() {
        val client = OZRelayerClient(relayerUrl = "https://relayer.example.com///")
        assertTrue(client.isConfigured)
    }

    @Test
    fun testRelayerClient_ftpSchemeThrows() {
        assertFailsWith<ConfigurationException.InvalidConfig> {
            OZRelayerClient(relayerUrl = "ftp://relayer.example.com")
        }
    }

    @Test
    fun testRelayerClient_noSchemeThrows() {
        assertFailsWith<ConfigurationException.InvalidConfig> {
            OZRelayerClient(relayerUrl = "relayer.example.com")
        }
    }

    @Test
    fun testRelayerClient_customTimeoutIsAccepted() {
        val client = OZRelayerClient(
            relayerUrl = "https://relayer.example.com",
            timeoutMs = 10_000L
        )
        assertTrue(client.isConfigured)
    }

    // MARK: - OZIndexerClient URL Validation

    @Test
    fun testIndexerClient_blankUrlThrows() {
        assertFailsWith<ConfigurationException.InvalidConfig> {
            OZIndexerClient(indexerUrl = "")
        }
    }

    @Test
    fun testIndexerClient_whitespaceUrlThrows() {
        assertFailsWith<ConfigurationException.InvalidConfig> {
            OZIndexerClient(indexerUrl = "   ")
        }
    }

    @Test
    fun testIndexerClient_httpUrlThrows() {
        assertFailsWith<ConfigurationException.InvalidConfig> {
            OZIndexerClient(indexerUrl = "http://indexer.example.com")
        }
    }

    @Test
    fun testIndexerClient_httpsUrlSucceeds() {
        // Constructor must not throw; close() frees resources
        val client = OZIndexerClient(indexerUrl = "https://indexer.example.com")
        client.close()
    }

    @Test
    fun testIndexerClient_localhostHttpUrlSucceeds() {
        val client = OZIndexerClient(indexerUrl = "http://localhost:8080")
        client.close()
    }

    @Test
    fun testIndexerClient_ftpSchemeThrows() {
        assertFailsWith<ConfigurationException.InvalidConfig> {
            OZIndexerClient(indexerUrl = "ftp://indexer.example.com")
        }
    }

    @Test
    fun testIndexerClient_noSchemeThrows() {
        assertFailsWith<ConfigurationException.InvalidConfig> {
            OZIndexerClient(indexerUrl = "indexer.example.com")
        }
    }

    // MARK: - OZIndexerClient.DEFAULT_INDEXER_URLS

    @Test
    fun testIndexerClient_testnetHasDefaultUrl() {
        val url = OZIndexerClient.DEFAULT_INDEXER_URLS[Network.TESTNET.networkPassphrase]
        assertNotNull(url, "Testnet should have a default indexer URL configured")
        assertTrue(url.startsWith("https://"), "Default indexer URL must use HTTPS")
    }

    @Test
    fun testIndexerClient_unknownNetworkReturnsNull() {
        val url = OZIndexerClient.DEFAULT_INDEXER_URLS["Custom Network ; 2026"]
        assertNull(url, "Unknown network must not have a default indexer URL")
    }

    // MARK: - OZIndexerClient.getDefaultUrl

    @Test
    fun testGetDefaultUrl_testnetReturnsUrl() {
        val url = OZIndexerClient.getDefaultUrl(Network.TESTNET.networkPassphrase)
        assertNotNull(url)
        assertTrue(url.startsWith("https://"))
    }

    @Test
    fun testGetDefaultUrl_unknownNetworkReturnsNull() {
        val url = OZIndexerClient.getDefaultUrl("Unknown Network ; January 2099")
        assertNull(url)
    }

    @Test
    fun testGetDefaultUrl_mainnetReturnsNullOrUrl() {
        // Mainnet may or may not have a default URL depending on configuration
        // Just verify the method does not throw
        val url = OZIndexerClient.getDefaultUrl(Network.PUBLIC.networkPassphrase)
        // url can be null (mainnet not configured) or non-null (mainnet configured)
        if (url != null) {
            assertTrue(url.startsWith("https://"), "If mainnet URL is set, it must use HTTPS")
        }
    }

    // MARK: - OZIndexerClient.forNetwork

    @Test
    fun testForNetwork_testnetReturnsClient() {
        val client = OZIndexerClient.forNetwork(Network.TESTNET.networkPassphrase)
        assertNotNull(client, "forNetwork must return a client for testnet")
        client.close()
    }

    @Test
    fun testForNetwork_unknownNetworkReturnsNull() {
        val client = OZIndexerClient.forNetwork("Unknown Network ; 2099")
        assertNull(client, "forNetwork must return null for unknown networks")
    }

    // MARK: - SmartAccountVersion constants

    @Test
    fun testSmartAccountVersion_nameIsNotBlank() {
        assertTrue(SmartAccountVersion.NAME.isNotBlank(), "Client name must not be blank")
    }

    @Test
    fun testSmartAccountVersion_versionIsNotBlank() {
        assertTrue(SmartAccountVersion.VERSION.isNotBlank(), "Client version must not be blank")
    }

    @Test
    fun testSmartAccountVersion_nameMatchesExpected() {
        assertEquals("kmp-smart-account-kit", SmartAccountVersion.NAME)
    }

    @Test
    fun testSmartAccountVersion_versionHasSemverFormat() {
        val parts = SmartAccountVersion.VERSION.split(".")
        assertEquals(3, parts.size, "Version must follow semver (MAJOR.MINOR.PATCH)")
        parts.forEach { part ->
            assertTrue(part.toIntOrNull() != null, "Each semver part must be numeric, got: '$part'")
        }
    }

    // MARK: - RelayerErrorCodes constants

    @Test
    fun testRelayerErrorCodes_allCodesAreNonBlank() {
        val codes = listOf(
            RelayerErrorCodes.INVALID_PARAMS,
            RelayerErrorCodes.INVALID_XDR,
            RelayerErrorCodes.POOL_CAPACITY,
            RelayerErrorCodes.SIMULATION_FAILED,
            RelayerErrorCodes.ONCHAIN_FAILED,
            RelayerErrorCodes.INVALID_TIME_BOUNDS,
            RelayerErrorCodes.FEE_LIMIT_EXCEEDED,
            RelayerErrorCodes.UNAUTHORIZED,
            RelayerErrorCodes.TIMEOUT
        )
        codes.forEach { code ->
            assertTrue(code.isNotBlank(), "Error code must not be blank: '$code'")
        }
    }

    @Test
    fun testRelayerErrorCodes_timeoutCodeValue() {
        assertEquals("TIMEOUT", RelayerErrorCodes.TIMEOUT)
    }
}
