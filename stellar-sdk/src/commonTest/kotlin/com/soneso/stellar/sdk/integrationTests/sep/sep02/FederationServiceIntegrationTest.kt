package com.soneso.stellar.sdk.integrationTests.sep.sep02

import com.soneso.stellar.sdk.sep.sep02.FederationService
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.seconds

/**
 * Integration tests for SEP-02 (Federation Protocol).
 *
 * These tests verify the SDK's federation service functionality against a live federation server.
 * The federation protocol allows for human-readable addresses (like email addresses) to be mapped
 * to Stellar account IDs.
 *
 * Tests verify:
 * - Resolving stellar addresses (name*domain format) to account IDs
 * - Static resolution using FederationService.resolveStellarAddress()
 * - Instance-based resolution using FederationService.fromDomain()
 * - Reverse resolution of account IDs to stellar addresses
 *
 * All tests use the live federation server at soneso.com.
 *
 * Ported from: /Users/chris/projects/Stellar/stellar_flutter_sdk/test/integration/sep0002_test.dart
 *
 * @see com.soneso.stellar.sdk.sep.sep02.FederationService
 * @see <a href="https://developers.stellar.org/docs/protocols/sep-2">SEP-02 Federation Protocol</a>
 */
class FederationServiceIntegrationTest {

    /**
     * Test static resolution of stellar address.
     *
     * This test verifies that:
     * 1. The static resolveStellarAddress method works correctly
     * 2. The federation server at soneso.com returns the expected data
     * 3. All response fields (accountId, memoType, memo, stellarAddress) are correctly parsed
     */
    @Test
    fun testStaticResolveStellarAddress() = runTest(timeout = 60.seconds) {
        val response = FederationService.resolveStellarAddress("bob*soneso.com")

        assertEquals("GBVPKXWMAB3FIUJB6T7LF66DABKKA2ZHRHDOQZ25GBAEFZVHTBPJNOJI", response.accountId)
        assertEquals("text", response.memoType)
        assertEquals("hello memo text", response.memo)
        assertEquals("bob*soneso.com", response.stellarAddress)
    }

    /**
     * Test instance-based resolution of stellar address.
     *
     * This test verifies that:
     * 1. FederationService.fromDomain() correctly loads the federation server URL from stellar.toml
     * 2. The instance-based resolveStellarAddress method works correctly
     * 3. All response fields are correctly parsed
     */
    @Test
    fun testInstanceResolveStellarAddress() = runTest(timeout = 60.seconds) {
        val service = FederationService.fromDomain("soneso.com")
        assertNotNull(service.federationServerUrl, "Federation server URL should be loaded from stellar.toml")

        val response = service.resolveStellarAddress("bob*soneso.com")

        assertEquals("GBVPKXWMAB3FIUJB6T7LF66DABKKA2ZHRHDOQZ25GBAEFZVHTBPJNOJI", response.accountId)
        assertEquals("text", response.memoType)
        assertEquals("hello memo text", response.memo)
        assertEquals("bob*soneso.com", response.stellarAddress)
    }

    /**
     * Test reverse resolution of account ID to stellar address.
     *
     * This test verifies that:
     * 1. The resolveAccountId method works correctly
     * 2. The federation server can reverse-lookup account IDs
     * 3. The response contains both the stellar address and account ID
     */
    @Test
    fun testResolveAccountId() = runTest(timeout = 60.seconds) {
        val service = FederationService.fromDomain("soneso.com")

        val response = service.resolveAccountId("GBVPKXWMAB3FIUJB6T7LF66DABKKA2ZHRHDOQZ25GBAEFZVHTBPJNOJI")

        assertEquals("bob*soneso.com", response.stellarAddress)
        assertEquals("GBVPKXWMAB3FIUJB6T7LF66DABKKA2ZHRHDOQZ25GBAEFZVHTBPJNOJI", response.accountId)
    }
}
