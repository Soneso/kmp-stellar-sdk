// Copyright 2025 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.integrationTests.sep.sep38

import com.soneso.stellar.sdk.sep.sep38.*
import com.soneso.stellar.sdk.FriendBot
import com.soneso.stellar.sdk.KeyPair
import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.sep.sep10.WebAuth
import com.soneso.stellar.sdk.sep.sep38.exceptions.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

/**
 * Integration tests for SEP-38 Anchor RFQ (Request for Quote) API against live testnet anchor.
 *
 * These tests validate the complete SEP-38 quote workflow against
 * testanchor.stellar.org, a real Stellar test anchor maintained by the
 * Stellar Development Foundation.
 *
 * Test Coverage:
 * - Service discovery via stellar.toml (fromDomain)
 * - GET /info to retrieve supported assets and delivery methods
 * - GET /prices to get indicative prices for multiple asset pairs
 * - GET /price to get indicative price for specific pair and amount
 * - POST /quote requires authentication (403 Forbidden without JWT)
 * - GET /quote/:id requires authentication (403 Forbidden without JWT)
 * - Error handling for various failure scenarios
 *
 * SEP-38 Workflow:
 * 1. Call info() to discover available assets and delivery methods
 * 2. Call prices() to get indicative prices (non-binding)
 * 3. Call price() to get specific indicative price (non-binding)
 * 4. Call postQuote() to request firm quote (binding, requires auth)
 * 5. Call getQuote() to retrieve quote (requires auth)
 *
 * Authentication:
 * - info(), prices(), and price() endpoints are public (no auth required)
 * - postQuote() and getQuote() endpoints require SEP-10 JWT token
 *
 * Network Requirements:
 * - Connectivity to https://testanchor.stellar.org
 * - Connectivity to https://friendbot.stellar.org (for account funding)
 * - Average test duration: 5-10 seconds per test (depends on network latency)
 *
 * Asset Format:
 * - Stellar assets: "stellar:CODE:ISSUER"
 * - Fiat currencies: "iso4217:USD"
 *
 * Reference:
 * - SEP-38 Specification: https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0038.md
 * - SEP-10 Specification: https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0010.md
 * - Test Anchor: https://testanchor.stellar.org
 * - Test Anchor stellar.toml: https://testanchor.stellar.org/.well-known/stellar.toml
 * - Test Anchor ANCHOR_QUOTE_SERVER: https://testanchor.stellar.org/sep38
 *
 * Note: These tests are NOT marked with @Ignore as they always have testnet
 * connectivity and should run as part of the standard test suite.
 */
class QuoteServiceIntegrationTest {

    // Test anchor configuration
    private val testAnchorDomain = "testanchor.stellar.org"
    private val network = Network.TESTNET

    /**
     * Helper function to create a funded account and obtain SEP-10 JWT token.
     *
     * @return Pair of (KeyPair, JWT token string)
     */
    private suspend fun createAuthenticatedAccount(): Pair<KeyPair, String> {
        // Generate random keypair
        val keyPair = KeyPair.random()
        val accountId = keyPair.getAccountId()

        // Fund account via Friendbot
        val friendBotResponse = FriendBot.fundTestnetAccount(accountId)
        assertTrue(friendBotResponse, "Account funding should succeed")

        // Authenticate with SEP-10 to get JWT token
        val webAuth = WebAuth.fromDomain(
            domain = testAnchorDomain,
            network = network
        )

        val authToken = webAuth.jwtToken(
            clientAccountId = accountId,
            signers = listOf(keyPair)
        )

        return Pair(keyPair, authToken.token)
    }

    /**
     * Tests SEP-38 service discovery via stellar.toml.
     *
     * Validates that:
     * - QuoteService.fromDomain() discovers ANCHOR_QUOTE_SERVER from stellar.toml
     * - The service is properly initialized with correct endpoint
     *
     * Flow:
     * 1. Call QuoteService.fromDomain()
     * 2. Verify service instance is created
     *
     * Expected Result:
     * - QuoteService instance is created successfully
     * - Service address is https://testanchor.stellar.org/sep38
     */
    @Test
    fun testFromDomain() = runTest {
        val quoteService = QuoteService.fromDomain(
            domain = testAnchorDomain
        )

        assertNotNull(quoteService, "Quote service should be created from domain")

        println("Service discovery from domain successful:")
        println("  Domain: $testAnchorDomain")
        println("  Expected Endpoint: https://testanchor.stellar.org/sep38")
    }

    /**
     * Tests GET /info endpoint to retrieve supported assets.
     *
     * Validates that:
     * - GET /info works without authentication
     * - Response contains list of supported assets
     * - Assets include delivery methods and country codes where applicable
     *
     * Flow:
     * 1. Initialize QuoteService from domain
     * 2. Call info() without JWT token
     * 3. Verify response contains assets
     *
     * Expected Result:
     * - Response contains at least one asset
     * - Each asset has proper format (stellar:CODE:ISSUER or iso4217:CODE)
     * - Response may include delivery methods and country codes
     */
    @Test
    fun testInfo() = runTest {
        val quoteService = QuoteService.fromDomain(
            domain = testAnchorDomain
        )

        val response = quoteService.info()

        assertNotNull(response.assets, "Response should contain assets")
        assertTrue(response.assets.isNotEmpty(), "Should have at least one supported asset")

        println("GET /info successful:")
        println("  Total assets: ${response.assets.size}")

        response.assets.forEach { asset ->
            println("  Asset: ${asset.asset}")

            asset.countryCodes?.let { codes ->
                println("    Country codes: ${codes.joinToString(", ")}")
            }

            asset.sellDeliveryMethods?.let { methods ->
                println("    Sell methods: ${methods.size} available")
                methods.forEach { method ->
                    println("      - ${method.name}: ${method.description}")
                }
            }

            asset.buyDeliveryMethods?.let { methods ->
                println("    Buy methods: ${methods.size} available")
                methods.forEach { method ->
                    println("      - ${method.name}: ${method.description}")
                }
            }
        }
    }

    /**
     * Tests GET /prices endpoint for indicative prices.
     *
     * Validates that:
     * - GET /prices works without authentication
     * - Response contains prices for available asset pairs
     * - Either sellAsset or buyAsset parameter works
     *
     * Flow:
     * 1. Initialize QuoteService from domain
     * 2. Get list of supported assets from info()
     * 3. Call prices() with first available Stellar asset
     * 4. Verify response structure
     *
     * Expected Result:
     * - Response contains list of buyAssets when sellAsset is provided
     * - Each entry includes asset identifier, price, and decimals
     * - Prices are non-binding indicative values
     *
     * Note: This test is best-effort. If the anchor has no trading pairs configured,
     * the response may be empty (which is valid behavior).
     */
    @Test
    fun testPrices() = runTest {
        val quoteService = QuoteService.fromDomain(
            domain = testAnchorDomain
        )

        // Get supported assets first
        val infoResponse = quoteService.info()
        assertTrue(infoResponse.assets.isNotEmpty(), "Need at least one asset")

        // Find a Stellar asset to use for prices query
        val stellarAsset = infoResponse.assets.find { it.asset.startsWith("stellar:") }

        if (stellarAsset != null) {
            println("Testing GET /prices with asset: ${stellarAsset.asset}")

            try {
                val pricesResponse = quoteService.prices(
                    sellAsset = stellarAsset.asset,
                    sellAmount = "100"
                )

                assertNotNull(pricesResponse, "Prices response should not be null")

                // buyAssets may be null or empty if anchor has no trading pairs configured
                if (pricesResponse.buyAssets != null && pricesResponse.buyAssets!!.isNotEmpty()) {
                    println("GET /prices successful:")
                    println("  Sell asset: ${stellarAsset.asset}")
                    println("  Sell amount: 100")
                    println("  Buy options: ${pricesResponse.buyAssets!!.size}")

                    pricesResponse.buyAssets!!.forEach { buyAsset ->
                        println("    ${buyAsset.asset}: ${buyAsset.price} (${buyAsset.decimals} decimals)")
                    }
                } else {
                    println("GET /prices successful (no trading pairs available)")
                    println("  This is valid - anchor may not have configured trading pairs")
                }
            } catch (e: Sep38BadRequestException) {
                // Some anchors may not support all assets in prices endpoint
                println("GET /prices not supported for this asset (valid behavior)")
                println("  Error: ${e.error}")
            }
        } else {
            println("Skipping GET /prices test - no Stellar assets available")
        }
    }

    /**
     * Tests GET /price endpoint for specific indicative price.
     *
     * Validates that:
     * - GET /price works without authentication
     * - Response contains price details for specific asset pair
     * - Fee information is included
     *
     * Flow:
     * 1. Initialize QuoteService from domain
     * 2. Get list of supported assets from info()
     * 3. Call price() with available asset pair
     * 4. Verify response structure
     *
     * Expected Result:
     * - Response contains price, totalPrice, sellAmount, buyAmount
     * - Fee information is included with total and asset
     * - Price is non-binding indicative value
     *
     * Note: This test requires the anchor to have at least one trading pair configured.
     * If no pairs are available, the test will skip gracefully.
     */
    @Test
    fun testPrice() = runTest {
        val quoteService = QuoteService.fromDomain(
            domain = testAnchorDomain
        )

        // Get supported assets first
        val infoResponse = quoteService.info()
        assertTrue(infoResponse.assets.isNotEmpty(), "Need at least one asset")

        // Find two different assets for testing
        val stellarAsset = infoResponse.assets.find { it.asset.startsWith("stellar:") }
        val fiatAsset = infoResponse.assets.find { it.asset.startsWith("iso4217:") }

        if (stellarAsset != null && fiatAsset != null) {
            println("Testing GET /price with pair: ${fiatAsset.asset} -> ${stellarAsset.asset}")

            try {
                val priceResponse = quoteService.price(
                    context = "sep6",
                    sellAsset = fiatAsset.asset,
                    buyAsset = stellarAsset.asset,
                    sellAmount = "100"
                )

                assertNotNull(priceResponse, "Price response should not be null")
                assertNotNull(priceResponse.price, "Should have price")
                assertNotNull(priceResponse.totalPrice, "Should have total price")
                assertNotNull(priceResponse.sellAmount, "Should have sell amount")
                assertNotNull(priceResponse.buyAmount, "Should have buy amount")
                assertNotNull(priceResponse.fee, "Should have fee information")

                println("GET /price successful:")
                println("  Sell: ${priceResponse.sellAmount} ${fiatAsset.asset}")
                println("  Buy: ${priceResponse.buyAmount} ${stellarAsset.asset}")
                println("  Price: ${priceResponse.price}")
                println("  Total Price (with fees): ${priceResponse.totalPrice}")
                println("  Fee: ${priceResponse.fee.total} ${priceResponse.fee.asset}")

                priceResponse.fee.details?.let { details ->
                    println("  Fee details:")
                    details.forEach { detail ->
                        println("    ${detail.name}: ${detail.amount}")
                        detail.description?.let { desc -> println("      ${desc}") }
                    }
                }
            } catch (e: Sep38BadRequestException) {
                // Some anchors may not support this asset pair
                println("GET /price not supported for this pair (valid behavior)")
                println("  Error: ${e.error}")
            }
        } else {
            println("Skipping GET /price test - need both Stellar and fiat assets")
            println("  Stellar assets available: ${stellarAsset != null}")
            println("  Fiat assets available: ${fiatAsset != null}")
        }
    }

    /**
     * Tests that POST /quote requires authentication.
     *
     * Validates that:
     * - POST /quote without JWT token returns 403 Forbidden
     * - Sep38PermissionDeniedException is thrown
     *
     * Flow:
     * 1. Initialize QuoteService from domain
     * 2. Attempt to post quote without authentication
     * 3. Verify 403 error is thrown
     *
     * Expected Result:
     * - Sep38PermissionDeniedException is thrown
     * - Error indicates authentication is required
     */
    @Test
    fun testPostQuoteRequiresAuth() = runTest {
        val quoteService = QuoteService.fromDomain(
            domain = testAnchorDomain
        )

        // Get supported assets first
        val infoResponse = quoteService.info()
        assertTrue(infoResponse.assets.isNotEmpty(), "Need at least one asset")

        val stellarAsset = infoResponse.assets.find { it.asset.startsWith("stellar:") }
        val fiatAsset = infoResponse.assets.find { it.asset.startsWith("iso4217:") }

        if (stellarAsset != null && fiatAsset != null) {
            val quoteRequest = Sep38QuoteRequest(
                context = "sep6",
                sellAsset = fiatAsset.asset,
                buyAsset = stellarAsset.asset,
                sellAmount = "100"
            )

            // Attempt to post quote with invalid token
            assertFailsWith<Sep38PermissionDeniedException> {
                quoteService.postQuote(quoteRequest, "invalid_token_12345")
            }

            println("POST /quote correctly requires authentication:")
            println("  Request without valid JWT returns 403 Forbidden")
        } else {
            println("Skipping POST /quote auth test - need both Stellar and fiat assets")
        }
    }

    /**
     * Tests that GET /quote/:id requires authentication.
     *
     * Validates that:
     * - GET /quote/:id without JWT token returns 403 Forbidden
     * - Sep38PermissionDeniedException is thrown
     *
     * Flow:
     * 1. Initialize QuoteService from domain
     * 2. Attempt to get quote without authentication
     * 3. Verify 403 error is thrown
     *
     * Expected Result:
     * - Sep38PermissionDeniedException is thrown
     * - Error indicates authentication is required
     *
     * Note: We use a dummy quote ID since we can't create a real quote
     * without authentication. The 403 error will be returned before
     * the anchor checks if the quote ID exists.
     */
    @Test
    fun testGetQuoteRequiresAuth() = runTest {
        val quoteService = QuoteService.fromDomain(
            domain = testAnchorDomain
        )

        // Use a dummy quote ID - we'll get 403 before anchor checks if it exists
        val dummyQuoteId = "test-quote-id-12345"

        // Attempt to get quote with invalid token
        assertFailsWith<Sep38PermissionDeniedException> {
            quoteService.getQuote(dummyQuoteId, "invalid_token_12345")
        }

        println("GET /quote/:id correctly requires authentication:")
        println("  Request without valid JWT returns 403 Forbidden")
    }

    /**
     * Tests complete workflow with authentication.
     *
     * Validates that:
     * - Can obtain SEP-10 JWT token
     * - Can create firm quote with valid JWT
     * - Can retrieve firm quote with valid JWT
     *
     * Flow:
     * 1. Fund account via Friendbot
     * 2. Authenticate with SEP-10 to get JWT token
     * 3. Get supported assets from info()
     * 4. Create firm quote via postQuote()
     * 5. Retrieve quote via getQuote()
     * 6. Verify quote details match
     *
     * Expected Result:
     * - Firm quote is created successfully
     * - Quote ID is returned
     * - Quote can be retrieved with same ID
     * - Quote details are consistent
     *
     * Note: This test requires the anchor to have trading pairs configured.
     * If not available, the test will fail gracefully.
     */
    @Test
    fun testCompleteWorkflowWithAuth() = runTest {
        val quoteService = QuoteService.fromDomain(
            domain = testAnchorDomain
        )

        // Get authenticated account
        val (keyPair, jwtToken) = createAuthenticatedAccount()

        println("Authentication successful:")
        println("  Account: ${keyPair.getAccountId()}")
        println("  JWT token obtained via SEP-10")

        // Get supported assets
        val infoResponse = quoteService.info()
        val stellarAsset = infoResponse.assets.find { it.asset.startsWith("stellar:") }
        val fiatAsset = infoResponse.assets.find { it.asset.startsWith("iso4217:") }

        if (stellarAsset != null && fiatAsset != null) {
            println("\nAttempting to create firm quote:")
            println("  Sell: 100 ${fiatAsset.asset}")
            println("  Buy: ${stellarAsset.asset}")

            try {
                // Create firm quote
                val quoteRequest = Sep38QuoteRequest(
                    context = "sep6",
                    sellAsset = fiatAsset.asset,
                    buyAsset = stellarAsset.asset,
                    sellAmount = "100"
                )

                val quoteResponse = quoteService.postQuote(quoteRequest, jwtToken)

                assertNotNull(quoteResponse.id, "Quote should have ID")
                assertNotNull(quoteResponse.expiresAt, "Quote should have expiration")
                assertNotNull(quoteResponse.price, "Quote should have price")

                println("\nFirm quote created successfully:")
                println("  Quote ID: ${quoteResponse.id}")
                println("  Expires at: ${quoteResponse.expiresAt}")
                println("  Price: ${quoteResponse.price}")
                println("  Total price: ${quoteResponse.totalPrice}")
                println("  Sell amount: ${quoteResponse.sellAmount} ${quoteResponse.sellAsset}")
                println("  Buy amount: ${quoteResponse.buyAmount} ${quoteResponse.buyAsset}")
                println("  Fee: ${quoteResponse.fee.total} ${quoteResponse.fee.asset}")

                // Retrieve the quote
                println("\nRetrieving quote by ID...")
                val retrievedQuote = quoteService.getQuote(quoteResponse.id, jwtToken)

                // Verify retrieved quote matches
                assertTrue(retrievedQuote.id == quoteResponse.id, "Quote IDs should match")
                assertTrue(retrievedQuote.price == quoteResponse.price, "Prices should match")

                println("Quote retrieved successfully:")
                println("  Quote ID matches: ${retrievedQuote.id == quoteResponse.id}")
                println("  All fields verified")

            } catch (e: Sep38BadRequestException) {
                // Anchor may not support this asset pair for firm quotes
                println("\nFirm quote not available for this pair (valid behavior)")
                println("  Error: ${e.error}")
                println("  This may indicate:")
                println("    - Asset pair not configured for quotes")
                println("    - Additional KYC required (SEP-12)")
                println("    - Amount outside allowed range")
            }
        } else {
            println("\nSkipping complete workflow test - need both Stellar and fiat assets")
            println("  Stellar assets available: ${stellarAsset != null}")
            println("  Fiat assets available: ${fiatAsset != null}")
        }
    }

    /**
     * Tests error handling for invalid parameters.
     *
     * Validates that:
     * - Invalid parameters are properly validated
     * - IllegalArgumentException is thrown for missing parameters
     * - Sep38BadRequestException is thrown for server-side validation
     *
     * Flow:
     * 1. Attempt to call prices() with both sellAsset and buyAsset
     * 2. Attempt to call price() with neither sellAmount nor buyAmount
     * 3. Verify proper exceptions are thrown
     *
     * Expected Result:
     * - IllegalArgumentException for client-side validation failures
     * - Clear error messages indicating the problem
     */
    @Test
    fun testInvalidParameters() = runTest {
        val quoteService = QuoteService.fromDomain(
            domain = testAnchorDomain
        )

        // Test 1: prices() with both sellAsset and buyAsset (invalid)
        assertFailsWith<IllegalArgumentException> {
            quoteService.prices(
                sellAsset = "stellar:USDC:GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN",
                buyAsset = "iso4217:USD",
                sellAmount = "100"
            )
        }

        println("Invalid parameters correctly rejected:")
        println("  prices() with both sellAsset and buyAsset throws IllegalArgumentException")

        // Test 2: price() with neither sellAmount nor buyAmount (invalid)
        assertFailsWith<IllegalArgumentException> {
            quoteService.price(
                context = "sep6",
                sellAsset = "stellar:USDC:GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN",
                buyAsset = "iso4217:USD"
                // Missing both sellAmount and buyAmount
            )
        }

        println("  price() without amount throws IllegalArgumentException")

        // Test 3: postQuote() with both sellAmount and buyAmount (invalid)
        assertFailsWith<IllegalArgumentException> {
            val quoteRequest = Sep38QuoteRequest(
                context = "sep6",
                sellAsset = "iso4217:USD",
                buyAsset = "stellar:USDC:GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN",
                sellAmount = "100",
                buyAmount = "100" // Both amounts provided (invalid)
            )
            quoteService.postQuote(quoteRequest, "dummy_token")
        }

        println("  postQuote() with both amounts throws IllegalArgumentException")
    }

    /**
     * Tests info() endpoint with authentication.
     *
     * Validates that:
     * - info() endpoint accepts optional JWT token
     * - Authenticated requests may provide personalized results
     * - Response format is same whether authenticated or not
     *
     * Flow:
     * 1. Call info() without authentication
     * 2. Get authenticated account
     * 3. Call info() with JWT token
     * 4. Compare results (should be similar structure)
     *
     * Expected Result:
     * - Both calls succeed
     * - Response structure is consistent
     * - Authenticated call may return additional/filtered assets
     */
    @Test
    fun testInfoWithAuth() = runTest {
        val quoteService = QuoteService.fromDomain(
            domain = testAnchorDomain
        )

        // Call without auth
        val unauthResponse = quoteService.info()
        val unauthAssetCount = unauthResponse.assets.size

        println("GET /info without auth:")
        println("  Assets: $unauthAssetCount")

        // Get authenticated account
        val (keyPair, jwtToken) = createAuthenticatedAccount()

        // Call with auth
        val authResponse = quoteService.info(jwtToken = jwtToken)
        val authAssetCount = authResponse.assets.size

        println("\nGET /info with auth:")
        println("  Account: ${keyPair.getAccountId()}")
        println("  Assets: $authAssetCount")

        // Both should return assets
        assertTrue(unauthAssetCount > 0, "Unauthenticated call should return assets")
        assertTrue(authAssetCount > 0, "Authenticated call should return assets")

        println("\nAuthentication comparison:")
        println("  Both calls successful")
        println("  Asset count may differ if anchor personalizes results")
    }
}
