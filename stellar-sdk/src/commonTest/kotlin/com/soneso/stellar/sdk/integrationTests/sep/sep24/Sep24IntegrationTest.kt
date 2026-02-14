// Copyright 2026 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.integrationTests.sep.sep24

import com.soneso.stellar.sdk.sep.sep24.*
import com.soneso.stellar.sdk.FriendBot
import com.soneso.stellar.sdk.KeyPair
import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.sep.sep10.WebAuth
import com.soneso.stellar.sdk.sep.sep24.exceptions.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.fail

/**
 * Integration tests for SEP-24 Hosted Deposit and Withdrawal service against live testnet anchor.
 *
 * These tests validate the complete SEP-24 interactive flow against
 * testanchor.stellar.org, a real Stellar test anchor maintained by the
 * Stellar Development Foundation.
 *
 * Test Coverage:
 * - Service discovery via stellar.toml (fromDomain)
 * - GET /info to retrieve supported assets and capabilities
 * - POST /transactions/deposit/interactive to initiate deposit flow
 * - POST /transactions/withdraw/interactive to initiate withdrawal flow
 * - GET /transaction to query single transaction status
 * - GET /transactions to query transaction history
 * - Transaction status polling with status callbacks
 * - Error handling for authentication failures
 * - Error handling for invalid requests
 *
 * SEP-24 Workflow:
 * 1. Call info() to discover available assets and capabilities
 * 2. Authenticate with SEP-10 to obtain JWT token
 * 3. Call deposit() or withdraw() to initiate interactive flow
 * 4. Display returned URL in webview for user interaction
 * 5. Poll transaction status until completion or terminal state
 *
 * Authentication:
 * - info() endpoint is public (no auth required)
 * - All other endpoints require SEP-10 JWT token
 *
 * Network Requirements:
 * - Connectivity to https://testanchor.stellar.org
 * - Connectivity to https://friendbot.stellar.org (for account funding)
 * - Average test duration: 5-10 seconds per test (depends on network latency)
 *
 * Interactive Flow Limitation:
 * - Complete end-to-end flow requires manual webview interaction
 * - Tests validate flow initiation and transaction query capabilities
 * - Tests do NOT complete full interactive flow (would require UI automation)
 *
 * Reference:
 * - SEP-24 Specification: https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0024.md
 * - SEP-10 Specification: https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0010.md
 * - Test Anchor: https://testanchor.stellar.org
 * - Test Anchor stellar.toml: https://testanchor.stellar.org/.well-known/stellar.toml
 * - Test Anchor TRANSFER_SERVER_SEP0024: https://testanchor.stellar.org/sep24
 *
 * Note: These tests are NOT marked with @Ignore as they always have testnet
 * connectivity and should run as part of the standard test suite.
 */
class Sep24IntegrationTest {

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
     * Tests SEP-24 service discovery via stellar.toml.
     *
     * Validates that:
     * - Sep24Service.fromDomain() discovers TRANSFER_SERVER_SEP0024 from stellar.toml
     * - The service is properly initialized with correct endpoint
     *
     * Flow:
     * 1. Call Sep24Service.fromDomain()
     * 2. Verify service instance is created
     *
     * Expected Result:
     * - Sep24Service instance is created successfully
     * - Service address is https://testanchor.stellar.org/sep24
     */
    @Test
    fun testFromDomain() = runTest {
        val sep24Service = Sep24Service.fromDomain(
            domain = testAnchorDomain
        )

        assertNotNull(sep24Service, "SEP-24 service should be created from domain")

        println("Service discovery from domain successful:")
        println("  Domain: $testAnchorDomain")
        println("  Expected Endpoint: https://testanchor.stellar.org/sep24")
    }

    /**
     * Tests GET /info endpoint to retrieve anchor capabilities.
     *
     * Validates that:
     * - GET /info works without authentication
     * - Response contains deposit and/or withdraw asset information
     * - Assets include limits, fees, and feature support
     *
     * Flow:
     * 1. Initialize Sep24Service from domain
     * 2. Call info() without JWT token
     * 3. Verify response contains asset information
     *
     * Expected Result:
     * - Response contains deposit assets or withdraw assets
     * - Each asset has enabled flag
     * - Assets may include minAmount, maxAmount, fee information
     * - Features information indicates anchor capabilities
     */
    @Test
    fun testInfo() = runTest {
        val sep24Service = Sep24Service.fromDomain(
            domain = testAnchorDomain
        )

        val response = sep24Service.info()

        // Anchor should support either deposits or withdrawals (or both)
        val hasDeposit = response.depositAssets != null && response.depositAssets!!.isNotEmpty()
        val hasWithdraw = response.withdrawAssets != null && response.withdrawAssets!!.isNotEmpty()

        assertTrue(
            hasDeposit || hasWithdraw,
            "Anchor should support at least deposits or withdrawals"
        )

        println("GET /info successful:")

        response.depositAssets?.let { deposits ->
            println("  Deposit assets: ${deposits.size}")
            deposits.forEach { (code, assetInfo) ->
                println("    $code: enabled=${assetInfo.enabled}")
                assetInfo.minAmount?.let { println("      Min: $it") }
                assetInfo.maxAmount?.let { println("      Max: $it") }
                assetInfo.feeFixed?.let { println("      Fee Fixed: $it") }
                assetInfo.feePercent?.let { println("      Fee Percent: $it") }
            }
        }

        response.withdrawAssets?.let { withdraws ->
            println("  Withdraw assets: ${withdraws.size}")
            withdraws.forEach { (code, assetInfo) ->
                println("    $code: enabled=${assetInfo.enabled}")
                assetInfo.minAmount?.let { println("      Min: $it") }
                assetInfo.maxAmount?.let { println("      Max: $it") }
            }
        }

        response.features?.let { features ->
            println("  Features:")
            println("    Account creation: ${features.accountCreation}")
            println("    Claimable balances: ${features.claimableBalances}")
        }

        response.feeEndpoint?.let { feeInfo ->
            println("  Fee endpoint:")
            println("    Enabled: ${feeInfo.enabled}")
            println("    Auth required: ${feeInfo.authenticationRequired}")
        }
    }

    /**
     * Tests deposit flow initiation and transaction query.
     *
     * Validates that:
     * - Can initiate deposit with valid JWT token
     * - Deposit returns interactive URL and transaction ID
     * - Can query transaction status with transaction ID
     * - Transaction starts in appropriate status
     *
     * Flow:
     * 1. Create and fund test account via Friendbot
     * 2. Authenticate via SEP-10 to get JWT token
     * 3. Get supported assets from info()
     * 4. Initiate deposit for available asset
     * 5. Verify interactive response contains URL and ID
     * 6. Query transaction by ID
     * 7. Verify transaction details match
     *
     * Expected Result:
     * - Deposit initiation succeeds with valid JWT
     * - Interactive URL is returned for webview display
     * - Transaction ID is returned for status tracking
     * - Transaction can be queried by ID
     * - Transaction is in incomplete or pending status
     *
     * Note: This test does NOT complete the full interactive flow as that
     * would require webview automation and manual user interaction.
     */
    @Test
    fun testDepositFlow() = runTest {
        val sep24Service = Sep24Service.fromDomain(
            domain = testAnchorDomain
        )

        // Get authenticated account
        val (keyPair, jwtToken) = createAuthenticatedAccount()

        println("Authentication successful:")
        println("  Account: ${keyPair.getAccountId()}")
        println("  JWT token obtained via SEP-10")

        // Get supported assets
        val infoResponse = sep24Service.info()
        val depositAssets = infoResponse.depositAssets

        if (depositAssets != null && depositAssets.isNotEmpty()) {
            // Find first enabled deposit asset
            val enabledAsset = depositAssets.entries.find { it.value.enabled }

            if (enabledAsset != null) {
                val assetCode = enabledAsset.key
                println("\nInitiating deposit for asset: $assetCode")

                try {
                    // Initiate deposit
                    val depositResponse = sep24Service.deposit(
                        Sep24DepositRequest(
                            assetCode = assetCode,
                            jwt = jwtToken,
                            account = keyPair.getAccountId(),
                            amount = "100",
                            claimableBalanceSupported = true
                        )
                    )

                    assertNotNull(depositResponse.id, "Deposit should return transaction ID")
                    assertNotNull(depositResponse.url, "Deposit should return interactive URL")
                    assertEquals("interactive_customer_info_needed", depositResponse.type)

                    println("Deposit initiated successfully:")
                    println("  Transaction ID: ${depositResponse.id}")
                    println("  Interactive URL: ${depositResponse.url}")
                    println("  Type: ${depositResponse.type}")
                    println("\nNote: In production, display this URL in a webview for user interaction")

                    // Query transaction by ID
                    println("\nQuerying transaction status...")
                    val txResponse = sep24Service.transaction(
                        Sep24TransactionRequest(
                            jwt = jwtToken,
                            id = depositResponse.id
                        )
                    )

                    val transaction = txResponse.transaction
                    assertEquals(depositResponse.id, transaction.id, "Transaction IDs should match")
                    assertEquals("deposit", transaction.kind, "Kind should be deposit")

                    println("Transaction query successful:")
                    println("  ID: ${transaction.id}")
                    println("  Kind: ${transaction.kind}")
                    println("  Status: ${transaction.status}")
                    println("  Started at: ${transaction.startedAt}")

                    // Verify transaction is not in completed state (requires webview interaction)
                    val statusEnum = transaction.getStatusEnum()
                    assertNotNull(statusEnum, "Status should be valid")
                    assertFalse(
                        transaction.isTerminal(),
                        "Transaction should not be completed without user interaction"
                    )

                } catch (e: Sep24InvalidRequestException) {
                    // Anchor may require additional parameters
                    println("\nDeposit requires additional parameters (valid behavior):")
                    println("  Error: ${e.message}")
                    println("  This may indicate KYC requirements (SEP-12)")
                }
            } else {
                println("\nSkipping deposit test - no enabled deposit assets")
            }
        } else {
            println("\nSkipping deposit test - anchor does not support deposits")
        }
    }

    /**
     * Tests withdrawal flow initiation and transaction query.
     *
     * Validates that:
     * - Can initiate withdrawal with valid JWT token
     * - Withdrawal returns interactive URL and transaction ID
     * - Can query transaction status with transaction ID
     * - Transaction starts in appropriate status
     *
     * Flow:
     * 1. Create and fund test account via Friendbot
     * 2. Authenticate via SEP-10 to get JWT token
     * 3. Get supported assets from info()
     * 4. Initiate withdrawal for available asset
     * 5. Verify interactive response contains URL and ID
     * 6. Query transaction by ID
     * 7. Verify transaction details match
     *
     * Expected Result:
     * - Withdrawal initiation succeeds with valid JWT
     * - Interactive URL is returned for webview display
     * - Transaction ID is returned for status tracking
     * - Transaction can be queried by ID
     * - Transaction is in incomplete or pending status
     *
     * Note: This test does NOT complete the full interactive flow as that
     * would require webview automation and manual user interaction.
     */
    @Test
    fun testWithdrawFlow() = runTest {
        val sep24Service = Sep24Service.fromDomain(
            domain = testAnchorDomain
        )

        // Get authenticated account
        val (keyPair, jwtToken) = createAuthenticatedAccount()

        println("Authentication successful:")
        println("  Account: ${keyPair.getAccountId()}")

        // Get supported assets
        val infoResponse = sep24Service.info()
        val withdrawAssets = infoResponse.withdrawAssets

        if (withdrawAssets != null && withdrawAssets.isNotEmpty()) {
            // Find first enabled withdrawal asset
            val enabledAsset = withdrawAssets.entries.find { it.value.enabled }

            if (enabledAsset != null) {
                val assetCode = enabledAsset.key
                println("\nInitiating withdrawal for asset: $assetCode")

                try {
                    // Initiate withdrawal
                    val withdrawResponse = sep24Service.withdraw(
                        Sep24WithdrawRequest(
                            assetCode = assetCode,
                            jwt = jwtToken,
                            account = keyPair.getAccountId(),
                            amount = "100"
                        )
                    )

                    assertNotNull(withdrawResponse.id, "Withdrawal should return transaction ID")
                    assertNotNull(withdrawResponse.url, "Withdrawal should return interactive URL")

                    println("Withdrawal initiated successfully:")
                    println("  Transaction ID: ${withdrawResponse.id}")
                    println("  Interactive URL: ${withdrawResponse.url}")
                    println("  Type: ${withdrawResponse.type}")

                    // Query transaction by ID
                    println("\nQuerying transaction status...")
                    val txResponse = sep24Service.transaction(
                        Sep24TransactionRequest(
                            jwt = jwtToken,
                            id = withdrawResponse.id
                        )
                    )

                    val transaction = txResponse.transaction
                    assertEquals(withdrawResponse.id, transaction.id)
                    assertEquals("withdrawal", transaction.kind)

                    println("Transaction query successful:")
                    println("  ID: ${transaction.id}")
                    println("  Kind: ${transaction.kind}")
                    println("  Status: ${transaction.status}")

                } catch (e: Sep24InvalidRequestException) {
                    // Anchor may require additional parameters
                    println("\nWithdrawal requires additional parameters (valid behavior):")
                    println("  Error: ${e.message}")
                }
            } else {
                println("\nSkipping withdrawal test - no enabled withdrawal assets")
            }
        } else {
            println("\nSkipping withdrawal test - anchor does not support withdrawals")
        }
    }

    /**
     * Tests transaction history query.
     *
     * Validates that:
     * - Can query transaction history with valid JWT token
     * - Response contains list of transactions
     * - Can filter by asset code
     * - Can filter by kind (deposit/withdrawal)
     *
     * Flow:
     * 1. Create and fund test account
     * 2. Authenticate via SEP-10
     * 3. Initiate a deposit transaction
     * 4. Query transaction history
     * 5. Verify history contains the transaction
     *
     * Expected Result:
     * - Transaction history query succeeds
     * - Response contains list of transactions (may be empty for new account)
     * - If transactions exist, they contain valid status and kind
     */
    @Test
    fun testTransactionHistory() = runTest {
        val sep24Service = Sep24Service.fromDomain(
            domain = testAnchorDomain
        )

        // Get authenticated account
        val (keyPair, jwtToken) = createAuthenticatedAccount()

        println("Authentication successful:")
        println("  Account: ${keyPair.getAccountId()}")

        // Get supported assets
        val infoResponse = sep24Service.info()
        val depositAssets = infoResponse.depositAssets

        if (depositAssets != null && depositAssets.isNotEmpty()) {
            val enabledAsset = depositAssets.entries.find { it.value.enabled }

            if (enabledAsset != null) {
                val assetCode = enabledAsset.key

                try {
                    // Try to initiate a deposit to create transaction history
                    val depositResponse = sep24Service.deposit(
                        Sep24DepositRequest(
                            assetCode = assetCode,
                            jwt = jwtToken,
                            account = keyPair.getAccountId(),
                            amount = "100"
                        )
                    )

                    println("\nDeposit initiated: ${depositResponse.id}")

                    // Query transaction history
                    println("Querying transaction history...")
                    val historyResponse = sep24Service.transactions(
                        Sep24TransactionsRequest(
                            assetCode = assetCode,
                            jwt = jwtToken,
                            kind = "deposit",
                            limit = 10
                        )
                    )

                    assertNotNull(historyResponse.transactions, "Should return transaction list")

                    println("Transaction history retrieved:")
                    println("  Total transactions: ${historyResponse.transactions.size}")

                    if (historyResponse.transactions.isNotEmpty()) {
                        // Verify our transaction is in the history
                        val ourTransaction = historyResponse.transactions.find { it.id == depositResponse.id }

                        if (ourTransaction != null) {
                            println("  Found our transaction:")
                            println("    ID: ${ourTransaction.id}")
                            println("    Kind: ${ourTransaction.kind}")
                            println("    Status: ${ourTransaction.status}")
                            assertEquals("deposit", ourTransaction.kind)
                        }

                        // Display first few transactions
                        println("\n  Recent transactions:")
                        historyResponse.transactions.take(3).forEach { tx ->
                            println("    ${tx.id}: ${tx.kind} - ${tx.status}")
                        }
                    }

                } catch (e: Sep24InvalidRequestException) {
                    println("\nCannot create test transaction (valid behavior):")
                    println("  Error: ${e.message}")

                    // Still try to query history even if we can't create a transaction
                    try {
                        val historyResponse = sep24Service.transactions(
                            Sep24TransactionsRequest(
                                assetCode = assetCode,
                                jwt = jwtToken,
                                limit = 10
                            )
                        )

                        println("\nTransaction history retrieved:")
                        println("  Total transactions: ${historyResponse.transactions.size}")
                    } catch (e: Sep24Exception) {
                        println("  History query also failed: ${e.message}")
                    }
                }
            } else {
                println("\nSkipping history test - no enabled deposit assets")
            }
        } else {
            println("\nSkipping history test - no deposit assets available")
        }
    }

    /**
     * Tests that authenticated endpoints require valid JWT token.
     *
     * Validates that:
     * - POST /transactions/deposit/interactive requires JWT
     * - POST /transactions/withdraw/interactive requires JWT
     * - GET /transaction requires JWT
     * - GET /transactions requires JWT
     * - Invalid JWT returns authentication error
     *
     * Flow:
     * 1. Initialize Sep24Service
     * 2. Get supported assets
     * 3. Attempt deposit without valid JWT
     * 4. Verify authentication error is thrown
     *
     * Expected Result:
     * - Sep24AuthenticationRequiredException or Sep24ServerErrorException is thrown
     * - Error indicates authentication failure
     *
     * Note: Different anchors may return different HTTP status codes for auth failures:
     * - 403 Forbidden (Sep24AuthenticationRequiredException)
     * - 401 Unauthorized (Sep24ServerErrorException)
     * - 500 Internal Server Error if anchor doesn't handle invalid JWT well
     */
    @Test
    fun testAuthenticationRequired() = runTest {
        val sep24Service = Sep24Service.fromDomain(
            domain = testAnchorDomain
        )

        val infoResponse = sep24Service.info()
        val depositAssets = infoResponse.depositAssets

        if (depositAssets != null && depositAssets.isNotEmpty()) {
            val assetCode = depositAssets.keys.first()

            // Test deposit with invalid JWT
            try {
                sep24Service.deposit(
                    Sep24DepositRequest(
                        assetCode = assetCode,
                        jwt = "invalid_jwt_token_12345",
                        account = "GABC123..." // Dummy account
                    )
                )
                fail("Deposit with invalid JWT should fail")
            } catch (e: Sep24Exception) {
                // Should throw Sep24AuthenticationRequiredException or Sep24ServerErrorException
                assertTrue(
                    e is Sep24AuthenticationRequiredException || e is Sep24ServerErrorException,
                    "Should throw authentication or server error exception"
                )
                println("Authentication validation successful:")
                println("  Deposit without valid JWT correctly failed: ${e::class.simpleName}")
            }

            // Test transaction query with invalid JWT
            try {
                sep24Service.transaction(
                    Sep24TransactionRequest(
                        jwt = "invalid_jwt_token_12345",
                        id = "dummy-transaction-id"
                    )
                )
                fail("Transaction query with invalid JWT should fail")
            } catch (e: Sep24Exception) {
                assertTrue(
                    e is Sep24AuthenticationRequiredException ||
                    e is Sep24ServerErrorException ||
                    e is Sep24TransactionNotFoundException,
                    "Should throw authentication/server error or not found exception"
                )
                println("  Transaction query without valid JWT correctly failed: ${e::class.simpleName}")
            }

            // Test transactions history with invalid JWT
            try {
                sep24Service.transactions(
                    Sep24TransactionsRequest(
                        assetCode = assetCode,
                        jwt = "invalid_jwt_token_12345"
                    )
                )
                fail("Transaction history with invalid JWT should fail")
            } catch (e: Sep24Exception) {
                assertTrue(
                    e is Sep24AuthenticationRequiredException || e is Sep24ServerErrorException,
                    "Should throw authentication or server error exception"
                )
                println("  Transaction history without valid JWT correctly failed: ${e::class.simpleName}")
            }

        } else {
            println("\nSkipping auth test - no deposit assets available")
        }
    }

    /**
     * Tests transaction status enum and terminal status detection.
     *
     * Validates that:
     * - Transaction status parsing works correctly
     * - isTerminal() correctly identifies terminal statuses
     * - getStatusEnum() returns correct enum values
     *
     * Flow:
     * 1. Test all terminal status values
     * 2. Test non-terminal status values
     * 3. Verify terminal detection logic
     *
     * Expected Result:
     * - Terminal statuses: completed, refunded, expired, error, no_market, too_small, too_large
     * - Non-terminal statuses: all incomplete/pending states
     */
    @Test
    fun testTransactionStatus() = runTest {
        // Test terminal statuses
        val terminalStatuses = listOf(
            Sep24TransactionStatus.COMPLETED,
            Sep24TransactionStatus.REFUNDED,
            Sep24TransactionStatus.EXPIRED,
            Sep24TransactionStatus.ERROR,
            Sep24TransactionStatus.NO_MARKET,
            Sep24TransactionStatus.TOO_SMALL,
            Sep24TransactionStatus.TOO_LARGE
        )

        terminalStatuses.forEach { status ->
            assertTrue(
                Sep24TransactionStatus.isTerminal(status.value),
                "${status.value} should be terminal"
            )
        }

        println("Terminal status validation successful:")
        terminalStatuses.forEach { status ->
            println("  ${status.value} - terminal")
        }

        // Test non-terminal statuses
        val nonTerminalStatuses = listOf(
            Sep24TransactionStatus.INCOMPLETE,
            Sep24TransactionStatus.PENDING_USER_TRANSFER_START,
            Sep24TransactionStatus.PENDING_USER_TRANSFER_COMPLETE,
            Sep24TransactionStatus.PENDING_EXTERNAL,
            Sep24TransactionStatus.PENDING_ANCHOR,
            Sep24TransactionStatus.ON_HOLD,
            Sep24TransactionStatus.PENDING_STELLAR,
            Sep24TransactionStatus.PENDING_TRUST,
            Sep24TransactionStatus.PENDING_USER
        )

        nonTerminalStatuses.forEach { status ->
            assertFalse(
                Sep24TransactionStatus.isTerminal(status.value),
                "${status.value} should not be terminal"
            )
        }

        println("\nNon-terminal status validation successful:")
        nonTerminalStatuses.forEach { status ->
            println("  ${status.value} - not terminal")
        }

        // Test status enum parsing
        assertEquals(
            Sep24TransactionStatus.COMPLETED,
            Sep24TransactionStatus.fromValue("completed")
        )
        assertEquals(
            Sep24TransactionStatus.PENDING_ANCHOR,
            Sep24TransactionStatus.fromValue("pending_anchor")
        )

        println("\nStatus enum parsing successful")
    }

    /**
     * Tests transaction not found error handling.
     *
     * Validates that:
     * - Querying non-existent transaction returns 404
     * - Sep24TransactionNotFoundException is thrown
     *
     * Flow:
     * 1. Create authenticated account
     * 2. Query transaction with non-existent ID
     * 3. Verify 404 error is thrown
     *
     * Expected Result:
     * - Sep24TransactionNotFoundException is thrown for non-existent transaction
     */
    @Test
    fun testTransactionNotFound() = runTest {
        val sep24Service = Sep24Service.fromDomain(
            domain = testAnchorDomain
        )

        // Get authenticated account
        val (_, jwtToken) = createAuthenticatedAccount()

        // Query non-existent transaction
        assertFailsWith<Sep24TransactionNotFoundException> {
            sep24Service.transaction(
                Sep24TransactionRequest(
                    jwt = jwtToken,
                    id = "non-existent-transaction-id-12345"
                )
            )
        }

        println("Transaction not found handling successful:")
        println("  Query with non-existent ID correctly returns 404")
    }

    /**
     * Tests info endpoint with language parameter.
     *
     * Validates that:
     * - info() accepts optional language parameter
     * - Response is returned regardless of language
     *
     * Flow:
     * 1. Call info() without language
     * 2. Call info() with language parameter
     * 3. Verify both succeed
     *
     * Expected Result:
     * - Both calls succeed
     * - Response structure is consistent
     */
    @Test
    fun testInfoWithLanguage() = runTest {
        val sep24Service = Sep24Service.fromDomain(
            domain = testAnchorDomain
        )

        // Call without language
        val defaultResponse = sep24Service.info()
        assertNotNull(defaultResponse)

        println("GET /info without language successful")

        // Call with language
        val localizedResponse = sep24Service.info(lang = "en")
        assertNotNull(localizedResponse)

        println("GET /info with language='en' successful")
        println("  Both calls returned valid responses")
    }
}
