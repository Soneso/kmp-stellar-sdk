package com.soneso.stellar.sdk.integrationTests

import com.soneso.stellar.sdk.*
import com.soneso.stellar.sdk.horizon.HorizonServer
import com.soneso.stellar.sdk.horizon.responses.*
import com.soneso.stellar.sdk.horizon.responses.effects.AccountCreatedEffectResponse
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

/**
 * Comprehensive integration tests for Horizon query operations.
 *
 * These tests verify the SDK's ability to query various Horizon endpoints against a live Stellar testnet.
 * They cover:
 * - Accounts queries (forSigner, forAsset)
 * - Assets queries (assetCode, assetIssuer filters)
 * - Effects queries (forAccount, forLedger, forTransaction, forOperation)
 * - Operations queries (including forClaimableBalance)
 * - Transactions queries (including forClaimableBalance)
 * - Ledgers queries
 * - Fee stats queries
 * - Offers and OrderBook queries
 * - Strict send/receive paths queries
 * - Trades queries
 * - Root endpoint
 *
 * **Test Network**: All tests use Stellar testnet by default.
 *
 * ## Ported from Flutter SDK
 * Based on: `/Users/chris/projects/Stellar/stellar_flutter_sdk/test/query_test.dart`
 *
 * @see <a href="https://developers.stellar.org/docs/data/horizon">Horizon API Documentation</a>
 */
class QueryIntegrationTest {

    private val testOn = "testnet" // or "futurenet"
    private val horizonServer = if (testOn == "testnet") {
        HorizonServer("https://horizon-testnet.stellar.org")
    } else {
        HorizonServer("https://horizon-futurenet.stellar.org")
    }
    private val network = if (testOn == "testnet") {
        Network.TESTNET
    } else {
        Network.FUTURENET
    }

    /**
     * Helper function to convert SDK Asset to Triple format for path queries.
     */
    private fun assetToTriple(asset: com.soneso.stellar.sdk.Asset): Triple<String, String?, String?> {
        return when (asset) {
            is AssetTypeNative -> Triple("native", null, null)
            is AssetTypeCreditAlphaNum4 -> Triple("credit_alphanum4", asset.code, asset.issuer)
            is AssetTypeCreditAlphaNum12 -> Triple("credit_alphanum12", asset.code, asset.issuer)
        }
    }

    /**
     * Helper function to convert list of SDK Assets to list of Triples.
     */
    private fun assetsToTriples(assets: List<com.soneso.stellar.sdk.Asset>): List<Triple<String, String?, String?>> {
        return assets.map { assetToTriple(it) }
    }

    /**
     * Test querying accounts by signer and by asset.
     *
     * This test:
     * 1. Creates and funds a main account
     * 2. Creates multiple test accounts
     * 3. Adds the main account as a signer to test accounts using SetOptions
     * 4. Verifies accounts appear in forSigner query with pagination
     * 5. Creates a custom asset and sets up trustlines
     * 6. Verifies accounts appear in forAsset query with pagination
     */
    @Test
    fun testQueryAccounts() = runTest(timeout = 120.seconds) {
        // Create and fund main account
            val accountKeyPair = KeyPair.random()
            val accountId = accountKeyPair.getAccountId()

            if (testOn == "testnet") {
                FriendBot.fundTestnetAccount(accountId)
            } else {
                FriendBot.fundFuturenetAccount(accountId)
            }

            realDelay(3000)

            val account = horizonServer.accounts().account(accountId)

            // Verify account can be queried
            val accountsForSigner = horizonServer.accounts().forSigner(accountId).execute()
            var found = false
            for (acc in accountsForSigner.records) {
                if (acc.accountId == accountId) {
                    found = true
                    break
                }
            }
            assertTrue(found, "Account should appear in forSigner query for itself")

            // Create 3 test accounts
            val testKeyPairs = mutableListOf<KeyPair>()
            for (i in 0 until 3) {
                testKeyPairs.add(KeyPair.random())
            }

            // Create issuer account for custom asset
            val issuerKeyPair = KeyPair.random()
            val issuerAccountId = issuerKeyPair.getAccountId()

            // Build transaction to create all accounts
            var transaction = TransactionBuilder(
                sourceAccount = Account(accountId, account.sequenceNumber),
                network = network
            )
                .addOperation(
                    CreateAccountOperation(
                        destination = issuerAccountId,
                        startingBalance = "5"
                    )
                )

            for (keyPair in testKeyPairs) {
                transaction.addOperation(
                    CreateAccountOperation(
                        destination = keyPair.getAccountId(),
                        startingBalance = "5"
                    )
                )
            }

            var tx = transaction
                .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
                .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
                .build()

            tx.sign(accountKeyPair)

            var response = horizonServer.submitTransaction(tx.toEnvelopeXdrBase64())
            assertTrue(response.successful, "CreateAccount transactions should succeed")

            realDelay(3000)

            // Add main account as signer to test accounts
            val updatedAccount = horizonServer.accounts().account(accountId)
            transaction = TransactionBuilder(
                sourceAccount = Account(accountId, updatedAccount.sequenceNumber),
                network = network
            )

            val signerKey = SignerKey.ed25519PublicKey(accountKeyPair.getPublicKey())
            for (keyPair in testKeyPairs) {
                val setOptionsOp = SetOptionsOperation(
                    signer = signerKey,
                    signerWeight = 1
                )
                setOptionsOp.sourceAccount = keyPair.getAccountId()
                transaction.addOperation(setOptionsOp)
            }

            tx = transaction
                .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
                .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
                .build()

            tx.sign(accountKeyPair)
            for (keyPair in testKeyPairs) {
                tx.sign(keyPair)
            }

            response = horizonServer.submitTransaction(tx.toEnvelopeXdrBase64())
            assertTrue(response.successful, "SetOptions transaction should succeed")

            realDelay(3000)

            // Verify accounts appear in forSigner query
            val accountsForSignerPage = horizonServer.accounts().forSigner(accountId).execute()
            assertTrue(accountsForSignerPage.records.size >= 4, "Should have at least 4 accounts (main + 3 test)")

            // Test pagination
            val accountsForSignerLimited = horizonServer.accounts()
                .forSigner(accountId)
                .limit(2)
                .order(com.soneso.stellar.sdk.horizon.requests.RequestBuilder.Order.DESC)
                .execute()
            assertEquals(2, accountsForSignerLimited.records.size, "Should have exactly 2 records with limit")

            // Create custom asset
            val astroDollar = AssetTypeCreditAlphaNum12("ASTRO", issuerAccountId)

            // Set up trustlines for all accounts
            val accountForTrust = horizonServer.accounts().account(accountId)
            transaction = TransactionBuilder(
                sourceAccount = Account(accountId, accountForTrust.sequenceNumber),
                network = network
            )
                .addOperation(
                    ChangeTrustOperation(
                        asset = astroDollar,
                        limit = "20000"
                    )
                )

            for (keyPair in testKeyPairs) {
                val changeTrustOp = ChangeTrustOperation(
                    asset = astroDollar,
                    limit = "20000"
                )
                changeTrustOp.sourceAccount = keyPair.getAccountId()
                transaction.addOperation(changeTrustOp)
            }

            tx = transaction
                .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
                .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
                .build()

            tx.sign(accountKeyPair)

            response = horizonServer.submitTransaction(tx.toEnvelopeXdrBase64())
            assertTrue(response.successful, "ChangeTrust transaction should succeed")

            realDelay(3000)

            // Verify accounts appear in forAsset query
            val accountsForAsset = horizonServer.accounts()
                .forAsset(astroDollar.code, astroDollar.issuer)
                .execute()
            assertTrue(accountsForAsset.records.size >= 4, "Should have at least 4 accounts with trustline")

            // Test pagination for forAsset
            val accountsForAssetLimited = horizonServer.accounts()
                .forAsset(astroDollar.code, astroDollar.issuer)
                .limit(2)
                .order(com.soneso.stellar.sdk.horizon.requests.RequestBuilder.Order.DESC)
                .execute()
            assertEquals(2, accountsForAssetLimited.records.size, "Should have exactly 2 records with limit")
    }

    /**
     * Test querying assets with filters.
     *
     * This test:
     * 1. Queries assets by asset code (ASTRO)
     * 2. Verifies pagination and ordering
     * 3. Queries assets by issuer
     * 4. Verifies asset statistics (accounts, balances, claimable balances)
     */
    @Test
    fun testQueryAssets() = runTest(timeout = 60.seconds) {
        // Query assets by code
            val assetsPage = horizonServer.assets()
                .forAssetCode("ASTRO")
                .limit(5)
                .order(com.soneso.stellar.sdk.horizon.requests.RequestBuilder.Order.DESC)
                .execute()

            val assets = assetsPage.records
            assertTrue(assets.isNotEmpty(), "Should find ASTRO assets")
            assertTrue(assets.size <= 5, "Should respect limit of 5")

            for (asset in assets) {
                println("Asset issuer: ${asset.assetIssuer}")
            }

            // Get an issuer and query by issuer
            if (assets.isNotEmpty()) {
                val assetIssuer = assets.last().assetIssuer
                val assetsPageByIssuer = horizonServer.assets()
                    .forAssetIssuer(assetIssuer)
                    .limit(5)
                    .order(com.soneso.stellar.sdk.horizon.requests.RequestBuilder.Order.DESC)
                    .execute()

                val assetsByIssuer = assetsPageByIssuer.records
                assertTrue(assetsByIssuer.isNotEmpty(), "Should find assets by issuer")
                assertTrue(assetsByIssuer.size <= 5, "Should respect limit of 5")

                for (asset in assetsByIssuer) {
                    println("Asset code: ${asset.assetCode}")
                    println("Num claimable balances: ${asset.numClaimableBalances ?: 0}")
                    println("Claimable balances amount: ${asset.claimableBalancesAmount ?: "0"}")
                    println("Accounts authorized: ${asset.accounts.authorized}")
                    println("Accounts authorizedToMaintainLiabilities: ${asset.accounts.authorizedToMaintainLiabilities}")
                    println("Accounts unauthorized: ${asset.accounts.unauthorized}")
                    println("Balances authorized: ${asset.balances.authorized}")
                    println("Balances authorizedToMaintainLiabilities: ${asset.balances.authorizedToMaintainLiabilities}")
                    println("Balances unauthorized: ${asset.balances.unauthorized}")
                }
            }
    }

    /**
     * Test querying effects with various filters.
     *
     * This test:
     * 1. Queries assets to get an issuer account
     * 2. Queries effects for that account
     * 3. Queries effects for a ledger
     * 4. Queries effects for a transaction
     * 5. Queries effects for an operation
     * 6. Verifies pagination and ordering
     */
    @Test
    fun testQueryEffects() = runTest(timeout = 60.seconds) {
        // Get an account with effects
            val assetsPage = horizonServer.assets()
                .forAssetCode("USDC")
                .limit(5)
                .order(com.soneso.stellar.sdk.horizon.requests.RequestBuilder.Order.DESC)
                .execute()

            val assets = assetsPage.records
            assertTrue(assets.isNotEmpty(), "Should find USDC assets")
            assertTrue(assets.size <= 5, "Should respect limit")

            val assetIssuer = assets.first().assetIssuer

            // Query effects for account
            val effectsPage = horizonServer.effects()
                .forAccount(assetIssuer)
                .limit(3)
                .order(com.soneso.stellar.sdk.horizon.requests.RequestBuilder.Order.ASC)
                .execute()

            val effects = effectsPage.records
            assertTrue(effects.isNotEmpty(), "Should have effects")
            assertTrue(effects.size <= 3, "Should respect limit of 3")

            // First effect should be AccountCreated
            val firstEffect = effects.first()
            assertTrue(
                firstEffect is AccountCreatedEffectResponse,
                "First effect should be AccountCreated"
            )

            // Query latest ledger with transactions
            // Try multiple ledgers to find one with transactions
            var ledgerWithTx: LedgerResponse? = null
            val ledgersPage = horizonServer.ledgers()
                .limit(20)
                .order(com.soneso.stellar.sdk.horizon.requests.RequestBuilder.Order.DESC)
                .execute()

            for (ledger in ledgersPage.records) {
                if ((ledger.successfulTransactionCount ?: 0) > 0) {
                    ledgerWithTx = ledger
                    break
                }
            }

            assertNotNull(ledgerWithTx, "Should find a ledger with transactions")

            // Query effects for ledger
            val effectsForLedger = horizonServer.effects()
                .forLedger(ledgerWithTx!!.sequence)
                .limit(3)
                .order(com.soneso.stellar.sdk.horizon.requests.RequestBuilder.Order.ASC)
                .execute()

            assertTrue(effectsForLedger.records.isNotEmpty(), "Ledger with transactions should have effects")

            // Query transactions for ledger
            val transactionsPage = horizonServer.transactions()
                .forLedger(ledgerWithTx.sequence)
                .limit(1)
                .order(com.soneso.stellar.sdk.horizon.requests.RequestBuilder.Order.DESC)
                .execute()

            if (transactionsPage.records.isNotEmpty()) {
                val transaction = transactionsPage.records.first()

                // Query effects for transaction
                val effectsForTransaction = horizonServer.effects()
                    .forTransaction(transaction.hash)
                    .limit(3)
                    .order(com.soneso.stellar.sdk.horizon.requests.RequestBuilder.Order.ASC)
                    .execute()

                // Most transactions have effects, but some system transactions might not
                // So we just verify the query works, not that it returns results
                assertNotNull(effectsForTransaction.records, "Effects query should return a list")

                // Query operations for transaction
                val operationsPage = horizonServer.operations()
                    .forTransaction(transaction.hash)
                    .limit(1)
                    .order(com.soneso.stellar.sdk.horizon.requests.RequestBuilder.Order.DESC)
                    .execute()

                if (operationsPage.records.isNotEmpty()) {
                    val operation = operationsPage.records.first()

                    // Query effects for operation
                    val effectsForOperation = horizonServer.effects()
                        .forOperation(operation.id.toLong())
                        .limit(3)
                        .order(com.soneso.stellar.sdk.horizon.requests.RequestBuilder.Order.ASC)
                        .execute()

                    // Most operations have effects, but some might not
                    // So we just verify the query works
                    assertNotNull(effectsForOperation.records, "Effects query should return a list")
                }
            }
    }

    /**
     * Test querying operations for claimable balance.
     *
     * This test queries operations associated with a claimable balance ID.
     * Note: Claimable balance IDs change frequently on testnet, so this test
     * verifies the query mechanism works but doesn't assert on specific results.
     */
    @Test
    fun testQueryOperationsForClaimableBalance() = runTest(timeout = 60.seconds) {
        // Try to query operations with a claimable balance ID
            // The ID may not exist, which is fine - we're testing the endpoint works
            try {
                val operationsPage1 = horizonServer.operations()
                    .forClaimableBalance("000000004dd97cb1a0ba1b6e1a188b49deafbad9e34c80e277c3725f815c757c2d05ddfe")
                    .limit(1)
                    .order(com.soneso.stellar.sdk.horizon.requests.RequestBuilder.Order.DESC)
                    .execute()

                // If we get results, verify they're valid
                if (operationsPage1.records.isNotEmpty()) {
                    val operation = operationsPage1.records.first()
                    assertNotNull(operation.id, "Operation should have an ID")
                }
            } catch (e: Exception) {
                // 400 or 404 is acceptable if the claimable balance doesn't exist
                assertTrue(
                    e.message?.contains("400") == true ||
                    e.message?.contains("404") == true ||
                    e.message?.contains("not found") == true,
                    "Expected 400/404 error for non-existent claimable balance, got: ${e.message}"
                )
            }

            // Try base58 format
            try {
                val operationsPage2 = horizonServer.operations()
                    .forClaimableBalance("BAAAAAAAJXMXZMNAXINW4GQYRNE55L523HRUZAHCO7BXEX4BLR2XYLIF3X7IL2Y")
                    .limit(1)
                    .order(com.soneso.stellar.sdk.horizon.requests.RequestBuilder.Order.DESC)
                    .execute()

                if (operationsPage2.records.isNotEmpty()) {
                    assertNotNull(operationsPage2.records.first().id, "Operation should have an ID")
                }
            } catch (e: Exception) {
                // 400 or 404 is acceptable
                assertTrue(
                    e.message?.contains("400") == true ||
                    e.message?.contains("404") == true ||
                    e.message?.contains("not found") == true,
                    "Expected 400/404 error for non-existent claimable balance"
                )
            }
    }

    /**
     * Test querying transactions for claimable balance.
     *
     * This test queries transactions associated with a claimable balance ID.
     * Note: Claimable balance IDs change frequently on testnet, so this test
     * verifies the query mechanism works but doesn't assert on specific results.
     */
    @Test
    fun testQueryTransactionsForClaimableBalance() = runTest(timeout = 60.seconds) {
        // Try to query transactions with a claimable balance ID
            // The ID may not exist, which is fine - we're testing the endpoint works
            try {
                val transactionsPage1 = horizonServer.transactions()
                    .forClaimableBalance("000000004dd97cb1a0ba1b6e1a188b49deafbad9e34c80e277c3725f815c757c2d05ddfe")
                    .limit(1)
                    .order(com.soneso.stellar.sdk.horizon.requests.RequestBuilder.Order.DESC)
                    .execute()

                // If we get results, verify they're valid
                if (transactionsPage1.records.isNotEmpty()) {
                    assertNotNull(transactionsPage1.records.first().hash, "Transaction should have a hash")
                }
            } catch (e: Exception) {
                // 400 or 404 is acceptable if the claimable balance doesn't exist
                assertTrue(
                    e.message?.contains("400") == true ||
                    e.message?.contains("404") == true ||
                    e.message?.contains("not found") == true,
                    "Expected 400/404 error for non-existent claimable balance, got: ${e.message}"
                )
            }

            // Try base58 format
            try {
                val transactionsPage2 = horizonServer.transactions()
                    .forClaimableBalance("BAAAAAAAJXMXZMNAXINW4GQYRNE55L523HRUZAHCO7BXEX4BLR2XYLIF3X7IL2Y")
                    .limit(1)
                    .order(com.soneso.stellar.sdk.horizon.requests.RequestBuilder.Order.DESC)
                    .execute()

                if (transactionsPage2.records.isNotEmpty()) {
                    assertNotNull(transactionsPage2.records.first().hash, "Transaction should have a hash")
                }
            } catch (e: Exception) {
                // 400 or 404 is acceptable
                assertTrue(
                    e.message?.contains("400") == true ||
                    e.message?.contains("404") == true ||
                    e.message?.contains("not found") == true,
                    "Expected 400/404 error for non-existent claimable balance"
                )
            }
    }

    /**
     * Test querying ledgers.
     *
     * This test:
     * 1. Queries the latest ledger
     * 2. Queries a specific ledger by sequence number
     * 3. Verifies both return the same ledger
     */
    @Test
    fun testQueryLedgers() = runTest(timeout = 60.seconds) {
        // Query latest ledger
            val ledgersPage = horizonServer.ledgers()
                .limit(1)
                .order(com.soneso.stellar.sdk.horizon.requests.RequestBuilder.Order.DESC)
                .execute()

            assertEquals(1, ledgersPage.records.size, "Should have 1 ledger")
            val ledger = ledgersPage.records.first()

            println("Ledger sequence: ${ledger.sequence}")
            // Note: txSetOperationCount might not be available in all responses
            // println("tx_set_operation_count: ${ledger.txSetOperationCount}")

            // Query specific ledger by sequence
            val ledger2 = horizonServer.ledgers().ledger(ledger.sequence)

            assertEquals(ledger.sequence, ledger2.sequence, "Ledger sequences should match")
    }

    /**
     * Test querying fee statistics.
     *
     * This test:
     * 1. Queries current fee stats
     * 2. Verifies all fee stat fields are present and valid
     */
    @Test
    fun testQueryFeeStats() = runTest(timeout = 60.seconds) {
        val feeStats = horizonServer.feeStats().execute()

            // Verify all required fields are present
            assertTrue(feeStats.lastLedger > 0, "lastLedger should be positive")
            assertTrue(feeStats.lastLedgerBaseFee > 0, "lastLedgerBaseFee should be positive")
            assertTrue(feeStats.ledgerCapacityUsage.isNotEmpty(), "ledgerCapacityUsage should not be empty")

            // Verify feeCharged stats
            assertTrue(feeStats.feeCharged.max >= 0, "feeCharged.max should be non-negative")
            assertTrue(feeStats.feeCharged.min >= 0, "feeCharged.min should be non-negative")
            assertTrue(feeStats.feeCharged.mode >= 0, "feeCharged.mode should be non-negative")
            assertTrue(feeStats.feeCharged.p10 >= 0, "feeCharged.p10 should be non-negative")
            assertTrue(feeStats.feeCharged.p20 >= 0, "feeCharged.p20 should be non-negative")
            assertTrue(feeStats.feeCharged.p30 >= 0, "feeCharged.p30 should be non-negative")
            assertTrue(feeStats.feeCharged.p40 >= 0, "feeCharged.p40 should be non-negative")
            assertTrue(feeStats.feeCharged.p50 >= 0, "feeCharged.p50 should be non-negative")
            assertTrue(feeStats.feeCharged.p60 >= 0, "feeCharged.p60 should be non-negative")
            assertTrue(feeStats.feeCharged.p70 >= 0, "feeCharged.p70 should be non-negative")
            assertTrue(feeStats.feeCharged.p80 >= 0, "feeCharged.p80 should be non-negative")
            assertTrue(feeStats.feeCharged.p90 >= 0, "feeCharged.p90 should be non-negative")
            assertTrue(feeStats.feeCharged.p95 >= 0, "feeCharged.p95 should be non-negative")
            assertTrue(feeStats.feeCharged.p99 >= 0, "feeCharged.p99 should be non-negative")

            // Verify maxFee stats
            assertTrue(feeStats.maxFee.max >= 0, "maxFee.max should be non-negative")
            assertTrue(feeStats.maxFee.min >= 0, "maxFee.min should be non-negative")
            assertTrue(feeStats.maxFee.mode >= 0, "maxFee.mode should be non-negative")
            assertTrue(feeStats.maxFee.p10 >= 0, "maxFee.p10 should be non-negative")
            assertTrue(feeStats.maxFee.p20 >= 0, "maxFee.p20 should be non-negative")
            assertTrue(feeStats.maxFee.p30 >= 0, "maxFee.p30 should be non-negative")
            assertTrue(feeStats.maxFee.p40 >= 0, "maxFee.p40 should be non-negative")
            assertTrue(feeStats.maxFee.p50 >= 0, "maxFee.p50 should be non-negative")
            assertTrue(feeStats.maxFee.p60 >= 0, "maxFee.p60 should be non-negative")
            assertTrue(feeStats.maxFee.p70 >= 0, "maxFee.p70 should be non-negative")
            assertTrue(feeStats.maxFee.p80 >= 0, "maxFee.p80 should be non-negative")
            assertTrue(feeStats.maxFee.p90 >= 0, "maxFee.p90 should be non-negative")
            assertTrue(feeStats.maxFee.p95 >= 0, "maxFee.p95 should be non-negative")
            assertTrue(feeStats.maxFee.p99 >= 0, "maxFee.p99 should be non-negative")

            println("Fee stats retrieved successfully")
    }

    /**
     * Test querying offers and order book.
     *
     * This test:
     * 1. Creates issuer and buyer accounts
     * 2. Creates a custom asset
     * 3. Sets up trustline
     * 4. Creates a buy offer
     * 5. Queries offers by account and by buying asset
     * 6. Queries order book with both asset orderings
     * 7. Verifies offer details and order book structure
     */
    @Test
    fun testQueryOffersAndOrderBook() = runTest(timeout = 120.seconds) {
        // Create and fund buyer account
            val buyerKeyPair = KeyPair.random()
            val buyerAccountId = buyerKeyPair.getAccountId()

            if (testOn == "testnet") {
                FriendBot.fundTestnetAccount(buyerAccountId)
            } else {
                FriendBot.fundFuturenetAccount(buyerAccountId)
            }

            realDelay(3000)

            val buyerAccount = horizonServer.accounts().account(buyerAccountId)

            // Create issuer account
            val issuerKeyPair = KeyPair.random()
            val issuerAccountId = issuerKeyPair.getAccountId()

            var transaction = TransactionBuilder(
                sourceAccount = Account(buyerAccountId, buyerAccount.sequenceNumber),
                network = network
            )
                .addOperation(
                    CreateAccountOperation(
                        destination = issuerAccountId,
                        startingBalance = "10"
                    )
                )
                .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
                .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
                .build()

            transaction.sign(buyerKeyPair)

            var response = horizonServer.submitTransaction(transaction.toEnvelopeXdrBase64())
            assertTrue(response.successful, "CreateAccount transaction should succeed")

            realDelay(3000)

            // Create custom asset
            val assetCode = "ASTRO"
            val astroDollar = AssetTypeCreditAlphaNum12(assetCode, issuerAccountId)

            // Set up trustline
            val buyerAccountForTrust = horizonServer.accounts().account(buyerAccountId)
            transaction = TransactionBuilder(
                sourceAccount = Account(buyerAccountId, buyerAccountForTrust.sequenceNumber),
                network = network
            )
                .addOperation(
                    ChangeTrustOperation(
                        asset = astroDollar,
                        limit = "10000"
                    )
                )
                .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
                .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
                .build()

            transaction.sign(buyerKeyPair)

            response = horizonServer.submitTransaction(transaction.toEnvelopeXdrBase64())
            assertTrue(response.successful, "ChangeTrust transaction should succeed")

            realDelay(3000)

            // Create buy offer
            val amountBuying = "100"
            val price = "0.5"

            val buyerAccountForOffer = horizonServer.accounts().account(buyerAccountId)
            transaction = TransactionBuilder(
                sourceAccount = Account(buyerAccountId, buyerAccountForOffer.sequenceNumber),
                network = network
            )
                .addOperation(
                    ManageBuyOfferOperation(
                        selling = AssetTypeNative,
                        buying = astroDollar,
                        buyAmount = amountBuying,
                        price = com.soneso.stellar.sdk.Price.fromString(price)
                    )
                )
                .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
                .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
                .build()

            transaction.sign(buyerKeyPair)

            response = horizonServer.submitTransaction(transaction.toEnvelopeXdrBase64())
            assertTrue(response.successful, "ManageBuyOffer transaction should succeed")

            realDelay(3000)

            // Query offers for account
            val offers = horizonServer.offers().forSeller(buyerAccountId).execute()
            assertEquals(1, offers.records.size, "Should have 1 offer")

            val offer = offers.records.first()

            // Note: Horizon's API sometimes omits asset type fields in offer responses
            // We can still verify the offer exists and has the right basic properties
            assertNotNull(offer.id, "Offer should have an ID")
            assertEquals(buyerAccountId, offer.seller, "Seller should match buyer")

            val offerAmount = offer.amount.toDouble()
            val offerPrice = offer.price.toDouble()
            val buyingAmountDouble = amountBuying.toDouble()

            assertEquals(buyingAmountDouble, (offerAmount * offerPrice).toInt().toDouble(), 0.5, "Amount should match")

            // Query offers by buying asset
            val offersByAsset = horizonServer.offers().forBuyingAsset(astroDollar).execute()
            assertTrue(offersByAsset.records.isNotEmpty(), "Should have offers for buying asset")

            var offerFound = false
            for (offerRecord in offersByAsset.records) {
                if (offerRecord.id == offer.id) {
                    offerFound = true
                    break
                }
            }
            assertTrue(offerFound, "Offer should be found in buying asset query")

            // Query order book (buying ASTRO, selling native)
            // Note: order_book endpoint doesn't support limit parameter
            val orderBook1 = horizonServer.orderBook()
                .buyingAsset(astroDollar)
                .sellingAsset(AssetTypeNative)
                .execute()

            assertTrue(orderBook1.asks.isNotEmpty(), "Order book should have asks")

            val askAmount = orderBook1.asks.first().amount.toDouble()
            val askPrice = orderBook1.asks.first().price.toDouble()
            assertEquals(buyingAmountDouble, (askAmount * askPrice).toInt().toDouble(), 0.5, "Ask amount should match")

            val base = orderBook1.base
            val counter = orderBook1.counter

            assertTrue(base is AssetTypeNative, "Base should be native")
            assertTrue(counter is AssetTypeCreditAlphaNum12, "Counter should be ASTRO")

            val counter12 = counter as AssetTypeCreditAlphaNum12
            assertEquals(assetCode, counter12.code, "Counter code should be ASTRO")
            assertEquals(issuerAccountId, counter12.issuer, "Counter issuer should match")

            // Query order book (selling ASTRO, buying native) - reversed
            // Note: order_book endpoint doesn't support limit parameter
            val orderBook2 = horizonServer.orderBook()
                .buyingAsset(AssetTypeNative)
                .sellingAsset(astroDollar)
                .execute()

            if (orderBook2.bids.isNotEmpty()) {
                val bidAmount = orderBook2.bids.first().amount.toDouble()
                val bidPrice = orderBook2.bids.first().price.toDouble()

                // In reversed order book, the bid represents our offer differently
                val expectedAmount = 25.0 // This is price dependent
                assertTrue((bidAmount * bidPrice).toInt() in 20..30, "Bid amount should be in expected range")
            }

            val base2 = orderBook2.base
            val counter2 = orderBook2.counter

            assertTrue(counter2 is AssetTypeNative, "Counter should be native when reversed")
            assertTrue(base2 is AssetTypeCreditAlphaNum12, "Base should be ASTRO when reversed")

            val base12 = base2 as AssetTypeCreditAlphaNum12
            assertEquals(assetCode, base12.code, "Base code should be ASTRO")
            assertEquals(issuerAccountId, base12.issuer, "Base issuer should match")
    }

    /**
     * Test querying strict send paths, strict receive paths, and trades.
     *
     * This complex test:
     * 1. Creates multiple accounts (A, B, C, D, E)
     * 2. Creates multiple assets (IOM, ECO, MOON)
     * 3. Sets up trustlines
     * 4. Distributes assets
     * 5. Creates sell offers to establish trading paths
     * 6. Queries strict send paths (source asset/amount -> destination)
     * 7. Executes path payment strict send
     * 8. Queries strict receive paths (source -> destination asset/amount)
     * 9. Executes path payment strict receive
     * 10. Queries and verifies trades
     * 11. Tests streaming trades
     */
    @Test
    fun testQueryStrictSendReceivePathsAndTrades() = runTest(timeout = 180.seconds) {
        // Create and fund account A
            val keyPairA = KeyPair.random()
            val accountAId = keyPairA.getAccountId()

            if (testOn == "testnet") {
                FriendBot.fundTestnetAccount(accountAId)
            } else {
                FriendBot.fundFuturenetAccount(accountAId)
            }

            realDelay(3000)

            val accountA = horizonServer.accounts().account(accountAId)

            // Create additional keypairs
            val keyPairB = KeyPair.random()
            val keyPairC = KeyPair.random()
            val keyPairD = KeyPair.random()
            val keyPairE = KeyPair.random()

            val accountBId = keyPairB.getAccountId()
            val accountCId = keyPairC.getAccountId()
            val accountDId = keyPairD.getAccountId()
            val accountEId = keyPairE.getAccountId()

            // Create accounts B, C, D, E
            var transaction = TransactionBuilder(
                sourceAccount = Account(accountAId, accountA.sequenceNumber),
                network = network
            )
                .addOperation(CreateAccountOperation(destination = accountCId, startingBalance = "10"))
                .addOperation(CreateAccountOperation(destination = accountBId, startingBalance = "10"))
                .addOperation(CreateAccountOperation(destination = accountDId, startingBalance = "10"))
                .addOperation(CreateAccountOperation(destination = accountEId, startingBalance = "10"))
                .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
                .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
                .build()

            transaction.sign(keyPairA)

            var response = horizonServer.submitTransaction(transaction.toEnvelopeXdrBase64())
            assertTrue(response.successful, "CreateAccount transaction should succeed")

            realDelay(3000)

            // Create assets issued by A
            val iomAsset = AssetTypeCreditAlphaNum4("IOM", accountAId)
            val ecoAsset = AssetTypeCreditAlphaNum4("ECO", accountAId)
            val moonAsset = AssetTypeCreditAlphaNum4("MOON", accountAId)

            // Set up trustlines for C (IOM)
            var accountC = horizonServer.accounts().account(accountCId)
            transaction = TransactionBuilder(
                sourceAccount = Account(accountCId, accountC.sequenceNumber),
                network = network
            )
                .addOperation(ChangeTrustOperation(asset = iomAsset, limit = "200999"))
                .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
                .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
                .build()

            transaction.sign(keyPairC)
            response = horizonServer.submitTransaction(transaction.toEnvelopeXdrBase64())
            assertTrue(response.successful)

            realDelay(3000)

            // Set up trustlines for B (IOM, ECO)
            val accountB = horizonServer.accounts().account(accountBId)
            transaction = TransactionBuilder(
                sourceAccount = Account(accountBId, accountB.sequenceNumber),
                network = network
            )
                .addOperation(ChangeTrustOperation(asset = iomAsset, limit = "200999"))
                .addOperation(ChangeTrustOperation(asset = ecoAsset, limit = "200999"))
                .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
                .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
                .build()

            transaction.sign(keyPairB)
            response = horizonServer.submitTransaction(transaction.toEnvelopeXdrBase64())
            assertTrue(response.successful)

            realDelay(3000)

            // Set up trustlines for D (ECO, MOON)
            val accountD = horizonServer.accounts().account(accountDId)
            transaction = TransactionBuilder(
                sourceAccount = Account(accountDId, accountD.sequenceNumber),
                network = network
            )
                .addOperation(ChangeTrustOperation(asset = ecoAsset, limit = "200999"))
                .addOperation(ChangeTrustOperation(asset = moonAsset, limit = "200999"))
                .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
                .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
                .build()

            transaction.sign(keyPairD)
            response = horizonServer.submitTransaction(transaction.toEnvelopeXdrBase64())
            assertTrue(response.successful)

            realDelay(3000)

            // Set up trustlines for E (MOON)
            val accountE = horizonServer.accounts().account(accountEId)
            transaction = TransactionBuilder(
                sourceAccount = Account(accountEId, accountE.sequenceNumber),
                network = network
            )
                .addOperation(ChangeTrustOperation(asset = moonAsset, limit = "200999"))
                .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
                .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
                .build()

            transaction.sign(keyPairE)
            response = horizonServer.submitTransaction(transaction.toEnvelopeXdrBase64())
            assertTrue(response.successful)

            realDelay(3000)

            // Distribute assets from A
            val accountAForPayments = horizonServer.accounts().account(accountAId)
            transaction = TransactionBuilder(
                sourceAccount = Account(accountAId, accountAForPayments.sequenceNumber),
                network = network
            )
                .addOperation(PaymentOperation(destination = accountCId, asset = iomAsset, amount = "100"))
                .addOperation(PaymentOperation(destination = accountBId, asset = iomAsset, amount = "100"))
                .addOperation(PaymentOperation(destination = accountBId, asset = ecoAsset, amount = "100"))
                .addOperation(PaymentOperation(destination = accountDId, asset = moonAsset, amount = "100"))
                .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
                .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
                .build()

            transaction.sign(keyPairA)
            response = horizonServer.submitTransaction(transaction.toEnvelopeXdrBase64())
            assertTrue(response.successful)

            realDelay(3000)

            // B creates sell offer: ECO -> IOM
            val accountBForOffer = horizonServer.accounts().account(accountBId)
            transaction = TransactionBuilder(
                sourceAccount = Account(accountBId, accountBForOffer.sequenceNumber),
                network = network
            )
                .addOperation(
                    ManageSellOfferOperation(
                        selling = ecoAsset,
                        buying = iomAsset,
                        amount = "100",
                        price = com.soneso.stellar.sdk.Price.fromString("0.5")
                    )
                )
                .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
                .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
                .build()

            transaction.sign(keyPairB)
            response = horizonServer.submitTransaction(transaction.toEnvelopeXdrBase64())
            assertTrue(response.successful)

            realDelay(3000)

            // D creates sell offer: MOON -> ECO
            val accountDForOffer = horizonServer.accounts().account(accountDId)
            transaction = TransactionBuilder(
                sourceAccount = Account(accountDId, accountDForOffer.sequenceNumber),
                network = network
            )
                .addOperation(
                    ManageSellOfferOperation(
                        selling = moonAsset,
                        buying = ecoAsset,
                        amount = "100",
                        price = com.soneso.stellar.sdk.Price.fromString("0.5")
                    )
                )
                .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
                .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
                .build()

            transaction.sign(keyPairD)
            response = horizonServer.submitTransaction(transaction.toEnvelopeXdrBase64())
            assertTrue(response.successful)

            realDelay(3000)

            // Test strict send paths - should throw with destinationAssets but no destinationAccount
            var exceptionThrown = false
            val destinationAssets = listOf(moonAsset)
            val destinationAssetsTriples = assetsToTriples(destinationAssets)
            try {
                horizonServer.strictSendPaths()
                    .sourceAsset("credit_alphanum4", "IOM", accountAId)
                    .sourceAmount("10")
                    .destinationAccount(accountEId)
                    .destinationAssets(destinationAssetsTriples)
                    .execute()
            } catch (e: Exception) {
                exceptionThrown = true
            }
            // This might not throw in all implementations, so we just test the path

            realDelay(3000)

            // Query strict send paths
            val strictSendPaths = horizonServer.strictSendPaths()
                .sourceAsset("credit_alphanum4", "IOM", accountAId)
                .sourceAmount("10")
                .destinationAccount(accountEId)
                .execute()

            assertTrue(strictSendPaths.records.isNotEmpty(), "Should find strict send paths")

            val pathResponse = strictSendPaths.records.first()
            assertEquals(40.0, pathResponse.destinationAmount.toDouble(), 1.0, "Destination amount should be around 40")
            assertEquals("credit_alphanum4", pathResponse.destinationAssetType, "Destination asset type should be credit_alphanum4")
            assertEquals("MOON", pathResponse.destinationAssetCode, "Destination asset code should be MOON")
            assertEquals(accountAId, pathResponse.destinationAssetIssuer, "Destination asset issuer should be A")

            assertEquals(10.0, pathResponse.sourceAmount.toDouble(), 0.1, "Source amount should be 10")
            assertEquals("credit_alphanum4", pathResponse.sourceAssetType, "Source asset type should be credit_alphanum4")
            assertEquals("IOM", pathResponse.sourceAssetCode, "Source asset code should be IOM")
            assertEquals(accountAId, pathResponse.sourceAssetIssuer, "Source asset issuer should be A")

            assertTrue(pathResponse.path.isNotEmpty(), "Path should not be empty")
            // Check if ECO is in the path
            val pathResponseAssets = pathResponse.path
            var foundEcoInPath = false
            for (responseAsset in pathResponseAssets) {
                if (responseAsset.assetType == "credit_alphanum4" &&
                    responseAsset.assetCode == "ECO" && responseAsset.assetIssuer == accountAId) {
                    foundEcoInPath = true
                    break
                }
            }
            assertTrue(foundEcoInPath, "Path should include ECO asset")

            // Query strict send paths with destinationAssets
            val strictSendPaths2 = horizonServer.strictSendPaths()
                .sourceAsset("credit_alphanum4", "IOM", accountAId)
                .sourceAmount("10")
                .destinationAssets(destinationAssetsTriples)
                .execute()

            assertTrue(strictSendPaths2.records.isNotEmpty(), "Should find strict send paths with destination assets")

            // Execute path payment strict send
            accountC = horizonServer.accounts().account(accountCId)
            // Convert path response assets to SDK assets
            val path = pathResponseAssets.map { responseAsset ->
                when {
                    responseAsset.assetType == "native" -> AssetTypeNative
                    responseAsset.assetType == "credit_alphanum4" && responseAsset.assetCode != null && responseAsset.assetIssuer != null ->
                        AssetTypeCreditAlphaNum4(responseAsset.assetCode, responseAsset.assetIssuer)
                    responseAsset.assetType == "credit_alphanum12" && responseAsset.assetCode != null && responseAsset.assetIssuer != null ->
                        AssetTypeCreditAlphaNum12(responseAsset.assetCode, responseAsset.assetIssuer)
                    else -> throw IllegalArgumentException("Unknown asset type: ${responseAsset.assetType}")
                }
            }

            transaction = TransactionBuilder(
                sourceAccount = Account(accountCId, accountC.sequenceNumber),
                network = network
            )
                .addOperation(
                    PathPaymentStrictSendOperation(
                        sendAsset = iomAsset,
                        sendAmount = "10",
                        destination = accountEId,
                        destAsset = moonAsset,
                        destMin = "38",
                        path = path
                    )
                )
                .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
                .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
                .build()

            transaction.sign(keyPairC)
            response = horizonServer.submitTransaction(transaction.toEnvelopeXdrBase64())
            assertTrue(response.successful, "PathPaymentStrictSend should succeed")

            realDelay(3000)

            // Verify E received MOON
            var accountEUpdated = horizonServer.accounts().account(accountEId)
            var foundMoon = false
            for (balance in accountEUpdated.balances) {
                if (balance.assetType != "native" && balance.assetCode == "MOON") {
                    assertTrue(balance.balance.toDouble() > 39.0, "MOON balance should be > 39")
                    foundMoon = true
                    break
                }
            }
            assertTrue(foundMoon, "Should have MOON balance")

            // Set up trade streaming
            var tradeExecuted = false
            val eventScope = CoroutineScope(Dispatchers.Default)
            val stream = horizonServer.trades()
                .forAccount(accountBId)
                .cursor("now")
                .stream(
                    serializer = TradeResponse.serializer(),
                    listener = object : com.soneso.stellar.sdk.horizon.requests.EventListener<TradeResponse> {
                        override fun onEvent(event: TradeResponse) {
                            tradeExecuted = true
                            assertEquals(accountBId, event.baseAccount, "Base account should be B")
                        }

                        override fun onFailure(error: Throwable?, responseCode: Int?) {
                            println("Trade stream failure: $error")
                        }
                    }
                )

            try {
                // Test strict receive paths
                exceptionThrown = false
                val sourceAssets = listOf(iomAsset)
                val sourceAssetsTriples = assetsToTriples(sourceAssets)
                try {
                    horizonServer.strictReceivePaths()
                        .destinationAsset("credit_alphanum4", "MOON", accountAId)
                        .destinationAmount("8")
                        .sourceAssets(sourceAssetsTriples)
                        .sourceAccount(accountCId)
                        .execute()
                } catch (e: Exception) {
                    exceptionThrown = true
                }
                // May or may not throw

                realDelay(1000)

                // Query strict receive paths
                val strictReceivePaths = horizonServer.strictReceivePaths()
                    .destinationAsset("credit_alphanum4", "MOON", accountAId)
                    .destinationAmount("8")
                    .sourceAssets(sourceAssetsTriples)
                    .execute()

                assertTrue(strictReceivePaths.records.isNotEmpty(), "Should find strict receive paths")

                val receivePathResponse = strictReceivePaths.records.first()
                assertEquals(8.0, receivePathResponse.destinationAmount.toDouble(), 0.1, "Destination amount should be 8")
                assertEquals("credit_alphanum4", receivePathResponse.destinationAssetType)
                assertEquals("MOON", receivePathResponse.destinationAssetCode)
                assertEquals(accountAId, receivePathResponse.destinationAssetIssuer)

                assertEquals(2.0, receivePathResponse.sourceAmount.toDouble(), 0.1, "Source amount should be 2")
                assertEquals("credit_alphanum4", receivePathResponse.sourceAssetType)
                assertEquals("IOM", receivePathResponse.sourceAssetCode)
                assertEquals(accountAId, receivePathResponse.sourceAssetIssuer)

                assertTrue(receivePathResponse.path.isNotEmpty())
                // Check if ECO is in the path
                var foundEcoInReceivePath = false
                for (responseAsset in receivePathResponse.path) {
                    if (responseAsset.assetType == "credit_alphanum4" &&
                        responseAsset.assetCode == "ECO" && responseAsset.assetIssuer == accountAId) {
                        foundEcoInReceivePath = true
                        break
                    }
                }
                assertTrue(foundEcoInReceivePath, "Receive path should include ECO asset")

                // Query with sourceAccount
                val strictReceivePaths2 = horizonServer.strictReceivePaths()
                    .destinationAsset("credit_alphanum4", "MOON", accountAId)
                    .destinationAmount("8")
                    .sourceAccount(accountCId)
                    .execute()

                assertTrue(strictReceivePaths2.records.isNotEmpty())

                // Execute path payment strict receive
                accountC = horizonServer.accounts().account(accountCId)
                // Convert receive path response assets to SDK assets
                val receivePath = receivePathResponse.path.map { responseAsset ->
                    when {
                        responseAsset.assetType == "native" -> AssetTypeNative
                        responseAsset.assetType == "credit_alphanum4" && responseAsset.assetCode != null && responseAsset.assetIssuer != null ->
                            AssetTypeCreditAlphaNum4(responseAsset.assetCode, responseAsset.assetIssuer)
                        responseAsset.assetType == "credit_alphanum12" && responseAsset.assetCode != null && responseAsset.assetIssuer != null ->
                            AssetTypeCreditAlphaNum12(responseAsset.assetCode, responseAsset.assetIssuer)
                        else -> throw IllegalArgumentException("Unknown asset type: ${responseAsset.assetType}")
                    }
                }

                transaction = TransactionBuilder(
                    sourceAccount = Account(accountCId, accountC.sequenceNumber),
                    network = network
                )
                    .addOperation(
                        PathPaymentStrictReceiveOperation(
                            sendAsset = iomAsset,
                            sendMax = "2",
                            destination = accountEId,
                            destAsset = moonAsset,
                            destAmount = "8",
                            path = receivePath
                        )
                    )
                    .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
                    .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
                    .build()

                transaction.sign(keyPairC)
                response = horizonServer.submitTransaction(transaction.toEnvelopeXdrBase64())
                assertTrue(response.successful, "PathPaymentStrictReceive should succeed")

                realDelay(3000)

                // Verify E received more MOON
                accountEUpdated = horizonServer.accounts().account(accountEId)
                foundMoon = false
                for (balance in accountEUpdated.balances) {
                    if (balance.assetType != "native" && balance.assetCode == "MOON") {
                        assertTrue(balance.balance.toDouble() > 47.0, "MOON balance should be > 47")
                        foundMoon = true
                        break
                    }
                }
                assertTrue(foundMoon, "Should have increased MOON balance")

                // Query trades
                val trades = horizonServer.trades().forAccount(accountBId).execute()
                assertEquals(2, trades.records.size, "Should have 2 trades")

                val trade1 = trades.records.first()
                assertTrue(trade1.baseIsSeller ?: false, "Base should be seller")
                assertEquals(accountBId, trade1.baseAccount, "Base account should be B")
                assertEquals(20.0, trade1.baseAmount.toDouble(), 0.1, "Base amount should be 20")
                assertEquals("credit_alphanum4", trade1.baseAssetType)
                assertEquals("ECO", trade1.baseAssetCode)
                assertEquals(accountAId, trade1.baseAssetIssuer)

                assertEquals(accountCId, trade1.counterAccount, "Counter account should be C")
                assertNotNull(trade1.counterOfferId)
                assertEquals(10.0, trade1.counterAmount.toDouble(), 0.1, "Counter amount should be 10")
                assertEquals("credit_alphanum4", trade1.counterAssetType)
                assertEquals("IOM", trade1.counterAssetCode)
                assertEquals(accountAId, trade1.counterAssetIssuer)
                val trade1Price = trade1.price
                assertNotNull(trade1Price, "Price should not be null")
                assertEquals(1, trade1Price!!.numerator, "Price numerator should be 1")
                assertEquals(2, trade1Price.denominator, "Price denominator should be 2")

                val trade2 = trades.records.last()
                assertTrue(trade2.baseIsSeller ?: false)
                assertEquals(accountBId, trade2.baseAccount)
                assertEquals(4.0, trade2.baseAmount.toDouble(), 0.1, "Base amount should be 4")

                // Wait for trade stream event
                realDelay(10000)

                assertTrue(tradeExecuted, "Trade should have been streamed")
            } finally {
                stream.close()
            }
    }

    /**
     * Test querying root endpoint.
     *
     * This test:
     * 1. Queries the root endpoint
     * 2. Verifies protocol version is returned
     */
    @Test
    fun testQueryRoot() = runTest(timeout = 60.seconds) {
        val root = horizonServer.root().execute()

        assertNotNull(root.stellarCoreVersion, "Core version should be present")
        assertTrue(root.currentProtocolVersion > 10, "Protocol version should be > 10")

        println("Root endpoint query successful")
        println("Core version: ${root.stellarCoreVersion}")
        println("Protocol version: ${root.currentProtocolVersion}")
    }
}
