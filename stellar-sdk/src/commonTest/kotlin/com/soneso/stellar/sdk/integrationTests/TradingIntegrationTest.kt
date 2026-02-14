package com.soneso.stellar.sdk.integrationTests

import com.soneso.stellar.sdk.*
import com.soneso.stellar.sdk.horizon.HorizonServer
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds
import kotlin.math.round

/**
 * Comprehensive integration tests for Trading-related operations.
 *
 * These tests verify the SDK's trading operations against a live Stellar testnet.
 * They cover:
 * - ManageBuyOffer operation (create, update, delete)
 * - ManageSellOffer operation (create, update, delete)
 * - CreatePassiveSellOffer operation
 * - Order book queries
 * - Offers queries (by account, by buying/selling asset)
 * - Trades queries (for specific offers)
 * - Price calculations and offer matching
 *
 * ## Trading Operations
 *
 * - **ManageBuyOffer**: Creates/updates/deletes an offer to buy a specific amount of an asset
 * - **ManageSellOffer**: Creates/updates/deletes an offer to sell a specific amount of an asset
 * - **CreatePassiveSellOffer**: Creates a passive sell offer that won't immediately match existing offers
 *
 * ## Test Network
 *
 * All tests use Stellar testnet. To switch to futurenet, change the `testOn` variable.
 *
 * ## Reference
 *
 * Ported from Flutter SDK's `trading_test.dart`
 *
 * @see <a href="https://developers.stellar.org/docs/learn/fundamentals/transactions/list-of-operations#manage-buy-offer">Manage Buy Offer</a>
 * @see <a href="https://developers.stellar.org/docs/learn/fundamentals/transactions/list-of-operations#manage-sell-offer">Manage Sell Offer</a>
 * @see <a href="https://developers.stellar.org/docs/learn/fundamentals/transactions/list-of-operations#create-passive-sell-offer">Create Passive Sell Offer</a>
 */
class TradingIntegrationTest {

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
     * Test manage buy offer operations.
     *
     * This test:
     * 1. Creates issuer and buyer accounts
     * 2. Creates a custom asset (ASTRO) issued by the issuer account
     * 3. Establishes trustline from buyer to ASTRO
     * 4. Creates a buy offer for ASTRO (buying 100 ASTRO at price 0.5 XLM)
     * 5. Verifies offer appears in account's offers
     * 6. Verifies offer appears in buying asset query
     * 7. Verifies order book shows the correct offer details
     * 8. Updates the buy offer (changing amount and price)
     * 9. Verifies updated offer has correct values
     * 10. Deletes the offer by setting amount to 0
     * 11. Verifies offer no longer exists
     * 12. Verifies operations and effects can be parsed
     *
     * ## Trading Terminology
     *
     * - **Buy Offer**: Specifies the amount you want to buy (buyAmount) and the price you're willing to pay
     * - **Price**: For a buy offer, price = selling/buying (e.g., 0.5 means pay 0.5 XLM per ASTRO)
     * - **Offer Amount**: In a buy offer, the amount is in units of the buying asset
     * - **Order Book**: The ask side shows sell offers, bid side shows buy offers
     *
     * ## Price Calculations
     *
     * When creating a buy offer:
     * - buyAmount = 100 (want to buy 100 ASTRO)
     * - price = 0.5 (willing to pay 0.5 XLM per ASTRO)
     * - Total cost = buyAmount * price = 50 XLM
     * - The offer in the order book will show: amount = sellingAmount = 50 XLM
     */
    @Test
    fun testManageBuyOffer() = runTest(timeout = 180.seconds) {
        // Create keypairs for issuer and buyer
        val issuerKeyPair = KeyPair.random()
        val buyerKeyPair = KeyPair.random()

        val issuerAccountId = issuerKeyPair.getAccountId()
        val buyerAccountId = buyerKeyPair.getAccountId()

        // Fund buyer account via FriendBot
        if (testOn == "testnet") {
            FriendBot.fundTestnetAccount(buyerAccountId)
        } else {
            FriendBot.fundFuturenetAccount(buyerAccountId)
        }

        realDelay(3000)

        // Create issuer account
        val buyerAccount = horizonServer.accounts().account(buyerAccountId)
        val createAccountOp = CreateAccountOperation(
            destination = issuerAccountId,
            startingBalance = "10"
        )

        var transaction = TransactionBuilder(
            sourceAccount = Account(buyerAccountId, buyerAccount.sequenceNumber),
            network = network
        )
            .addOperation(createAccountOp)
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        transaction.sign(buyerKeyPair)

        var response = horizonServer.submitTransaction(transaction.toEnvelopeXdrBase64())
        assertTrue(response.successful, "CreateAccount transaction should succeed")

        realDelay(3000)

        // Create custom asset ASTRO
        val assetCode = "ASTRO"
        val astroDollar = AssetTypeCreditAlphaNum12(assetCode, issuerAccountId)

        // Establish trustline from buyer to ASTRO
        val buyerAccountReloaded = horizonServer.accounts().account(buyerAccountId)
        val changeTrustOp = ChangeTrustOperation(
            asset = astroDollar,
            limit = "10000"
        )

        transaction = TransactionBuilder(
            sourceAccount = Account(buyerAccountId, buyerAccountReloaded.sequenceNumber),
            network = network
        )
            .addOperation(changeTrustOp)
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        transaction.sign(buyerKeyPair)

        response = horizonServer.submitTransaction(transaction.toEnvelopeXdrBase64())
        assertTrue(response.successful, "ChangeTrust transaction should succeed")

        realDelay(3000)

        // Create a buy offer: buying 100 ASTRO at price 0.5 XLM per ASTRO
        val amountBuying = "100"
        val price = "0.5"

        val buyerAccountReloaded2 = horizonServer.accounts().account(buyerAccountId)
        val manageBuyOfferOp = ManageBuyOfferOperation(
            selling = AssetTypeNative,
            buying = astroDollar,
            buyAmount = amountBuying,
            price = Price.fromString(price),
            offerId = 0  // 0 means create new offer
        )

        transaction = TransactionBuilder(
            sourceAccount = Account(buyerAccountId, buyerAccountReloaded2.sequenceNumber),
            network = network
        )
            .addOperation(manageBuyOfferOp)
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        transaction.sign(buyerKeyPair)

        response = horizonServer.submitTransaction(transaction.toEnvelopeXdrBase64())
        assertTrue(response.successful, "ManageBuyOffer create transaction should succeed")

        realDelay(3000)

        // Verify offer appears in account's offers
        var offersPage = horizonServer.offers().forAccount(buyerAccountId).execute()
        assertEquals(1, offersPage.records.size, "Should have 1 offer")

        var offer = offersPage.records.first()
        assertEquals(astroDollar, offer.buying, "Should be buying ASTRO")
        assertEquals(AssetTypeNative, offer.selling, "Should be selling native XLM")

        // Calculate and verify offer details
        // For a buy offer with buyAmount=100 and price=0.5:
        // - The offer amount (selling amount) = buyAmount * price = 50 XLM
        // - The offer price (in order book) = 1/price = 2.0 (how much ASTRO per XLM)
        val offerAmount = offer.amount.toDouble()
        val offerPrice = offer.price.toDouble()
        val buyingAmount = amountBuying.toDouble()

        // Verify that offerAmount * offerPrice rounds to buyingAmount
        assertTrue(round(offerAmount * offerPrice) == buyingAmount,
            "Offer amount * price should equal buying amount: ${round(offerAmount * offerPrice)} != $buyingAmount")

        assertEquals(buyerAccountId, offer.seller, "Seller should be buyer account")

        val offerId = offer.id

        // Verify offer appears in buying asset query
        offersPage = horizonServer.offers().forBuyingAsset(astroDollar).execute()
        assertTrue(offersPage.records.size >= 1, "Should have at least 1 offer buying ASTRO")

        var offerFound = false
        for (o in offersPage.records) {
            if (o.id == offerId) {
                offerFound = true
                break
            }
        }
        assertTrue(offerFound, "Offer should appear in buying asset query")

        // Verify order book
        var orderBook = horizonServer.orderBook()
            .buyingAsset(astroDollar)
            .sellingAsset(AssetTypeNative)
            .execute()

        assertTrue(orderBook.asks.isNotEmpty(), "Order book should have asks")
        val askAmount = orderBook.asks.first().amount.toDouble()
        val askPrice = orderBook.asks.first().price.toDouble()

        assertTrue(round(askAmount * askPrice) == buyingAmount,
            "Order book ask: amount * price should equal buying amount")

        // Verify base and counter assets in order book
        val base = orderBook.base
        val counter = orderBook.counter

        assertTrue(base is AssetTypeNative, "Base should be native")
        assertTrue(counter is AssetTypeCreditAlphaNum12, "Counter should be AlphaNum12")

        val counter12 = counter as AssetTypeCreditAlphaNum12
        assertEquals(assetCode, counter12.code, "Counter asset code should be ASTRO")
        assertEquals(issuerAccountId, counter12.issuer, "Counter asset issuer should match")

        // Check reverse order book (ASTRO/XLM instead of XLM/ASTRO)
        orderBook = horizonServer.orderBook()
            .buyingAsset(AssetTypeNative)
            .sellingAsset(astroDollar)
            .execute()

        assertTrue(orderBook.bids.isNotEmpty(), "Reverse order book should have bids")
        val bidAmount = orderBook.bids.first().amount.toDouble()
        val bidPrice = orderBook.bids.first().price.toDouble()

        // In reverse order book, the calculation should yield 25
        // (this is how the Stellar order book inversion works)
        assertTrue(round(bidAmount * bidPrice) == 25.0,
            "Reverse order book: amount * price should equal 25")

        // Update offer: change amount to 150 and price to 0.3
        val amountBuying2 = "150"
        val price2 = "0.3"

        val buyerAccountReloaded3 = horizonServer.accounts().account(buyerAccountId)
        val updateBuyOfferOp = ManageBuyOfferOperation(
            selling = AssetTypeNative,
            buying = astroDollar,
            buyAmount = amountBuying2,
            price = Price.fromString(price2),
            offerId = offerId.toLong()  // Existing offer ID to update
        )

        transaction = TransactionBuilder(
            sourceAccount = Account(buyerAccountId, buyerAccountReloaded3.sequenceNumber),
            network = network
        )
            .addOperation(updateBuyOfferOp)
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        transaction.sign(buyerKeyPair)

        response = horizonServer.submitTransaction(transaction.toEnvelopeXdrBase64())
        assertTrue(response.successful, "ManageBuyOffer update transaction should succeed")

        realDelay(3000)

        // Verify updated offer
        offersPage = horizonServer.offers().forAccount(buyerAccountId).execute()
        assertEquals(1, offersPage.records.size, "Should still have 1 offer")

        offer = offersPage.records.first()
        assertEquals(astroDollar, offer.buying, "Should still be buying ASTRO")
        assertEquals(AssetTypeNative, offer.selling, "Should still be selling XLM")

        val offerAmount2 = offer.amount.toDouble()
        val offerPrice2 = offer.price.toDouble()
        val buyingAmount2 = amountBuying2.toDouble()

        assertTrue(round(offerAmount2 * offerPrice2) == buyingAmount2,
            "Updated offer: amount * price should equal new buying amount")

        assertEquals(buyerAccountId, offer.seller, "Seller should still be buyer account")

        // Verify updated order book
        orderBook = horizonServer.orderBook()
            .buyingAsset(astroDollar)
            .sellingAsset(AssetTypeNative)
            .execute()

        assertTrue(orderBook.asks.isNotEmpty(), "Order book should still have asks")
        val askAmount2 = orderBook.asks.first().amount.toDouble()
        val askPrice2 = orderBook.asks.first().price.toDouble()

        assertTrue(round(askAmount2 * askPrice2) == buyingAmount2,
            "Updated order book: amount * price should equal new buying amount")

        val base2 = orderBook.base
        val counter2 = orderBook.counter

        assertTrue(base2 is AssetTypeNative, "Base should still be native")
        assertTrue(counter2 is AssetTypeCreditAlphaNum12, "Counter should still be AlphaNum12")

        val counter12_2 = counter2 as AssetTypeCreditAlphaNum12
        assertEquals(assetCode, counter12_2.code, "Counter asset code should still be ASTRO")
        assertEquals(issuerAccountId, counter12_2.issuer, "Counter asset issuer should still match")

        // Delete offer: set amount to 0
        val amountBuying3 = "0"

        val buyerAccountReloaded4 = horizonServer.accounts().account(buyerAccountId)
        val deleteBuyOfferOp = ManageBuyOfferOperation(
            selling = AssetTypeNative,
            buying = astroDollar,
            buyAmount = amountBuying3,
            price = Price.fromString(price2),
            offerId = offerId.toLong()
        )

        transaction = TransactionBuilder(
            sourceAccount = Account(buyerAccountId, buyerAccountReloaded4.sequenceNumber),
            network = network
        )
            .addOperation(deleteBuyOfferOp)
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        transaction.sign(buyerKeyPair)

        response = horizonServer.submitTransaction(transaction.toEnvelopeXdrBase64())
        assertTrue(response.successful, "ManageBuyOffer delete transaction should succeed")

        realDelay(3000)

        // Verify offer no longer exists
        offersPage = horizonServer.offers().forAccount(buyerAccountId).execute()
        assertTrue(offersPage.records.isEmpty(), "Should have no offers after deletion")

        // Verify order book is empty
        orderBook = horizonServer.orderBook()
            .buyingAsset(astroDollar)
            .sellingAsset(AssetTypeNative)
            .execute()

        assertEquals(0, orderBook.asks.size, "Order book asks should be empty")
        assertEquals(0, orderBook.bids.size, "Order book bids should be empty")

        // Verify operations and effects can be parsed
        val operationsPage = horizonServer.operations().forAccount(buyerAccountId).execute()
        assertTrue(operationsPage.records.isNotEmpty(), "Should have operations")

        val effectsPage = horizonServer.effects().forAccount(buyerAccountId).execute()
        assertTrue(effectsPage.records.isNotEmpty(), "Should have effects")
    }

    /**
     * Test manage sell offer operations.
     *
     * This test:
     * 1. Creates issuer and seller accounts
     * 2. Creates a custom asset (MOON) issued by the issuer account
     * 3. Establishes trustline from seller to MOON
     * 4. Sends MOON tokens from issuer to seller (so seller has tokens to sell)
     * 5. Creates a sell offer for MOON (selling 100 MOON at price 0.5 XLM per MOON)
     * 6. Verifies offer appears in account's offers with correct details
     * 7. Verifies offer appears in selling asset query
     * 8. Verifies order book shows the correct offer details
     * 9. Updates the sell offer (changing amount and price)
     * 10. Verifies updated offer has correct values
     * 11. Deletes the offer by setting amount to 0
     * 12. Verifies offer no longer exists
     *
     * ## Trading Terminology
     *
     * - **Sell Offer**: Specifies the amount you want to sell and the price you want to receive
     * - **Price**: For a sell offer, price = buying/selling (e.g., 0.5 means receive 0.5 XLM per MOON)
     * - **Offer Amount**: In a sell offer, the amount is in units of the selling asset
     * - **Order Book**: The ask side shows sell offers, bid side shows buy offers
     */
    @Test
    fun testManageSellOffer() = runTest(timeout = 180.seconds) {
        // Create keypairs for issuer and seller
        val issuerKeyPair = KeyPair.random()
        val sellerKeyPair = KeyPair.random()

        val issuerAccountId = issuerKeyPair.getAccountId()
        val sellerAccountId = sellerKeyPair.getAccountId()

        // Fund seller account via FriendBot
        if (testOn == "testnet") {
            FriendBot.fundTestnetAccount(sellerAccountId)
        } else {
            FriendBot.fundFuturenetAccount(sellerAccountId)
        }

        realDelay(3000)

        // Create issuer account
        val sellerAccount = horizonServer.accounts().account(sellerAccountId)
        val createAccountOp = CreateAccountOperation(
            destination = issuerAccountId,
            startingBalance = "10"
        )

        var transaction = TransactionBuilder(
            sourceAccount = Account(sellerAccountId, sellerAccount.sequenceNumber),
            network = network
        )
            .addOperation(createAccountOp)
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        transaction.sign(sellerKeyPair)

        var response = horizonServer.submitTransaction(transaction.toEnvelopeXdrBase64())
        assertTrue(response.successful, "CreateAccount transaction should succeed")

        realDelay(3000)

        // Get issuer account
        val issuerAccount = horizonServer.accounts().account(issuerAccountId)

        // Create custom asset MOON
        val assetCode = "MOON"
        val moonDollar = AssetTypeCreditAlphaNum4(assetCode, issuerAccountId)

        // Establish trustline from seller to MOON
        val sellerAccountReloaded = horizonServer.accounts().account(sellerAccountId)
        val changeTrustOp = ChangeTrustOperation(
            asset = moonDollar,
            limit = "10000"
        )

        transaction = TransactionBuilder(
            sourceAccount = Account(sellerAccountId, sellerAccountReloaded.sequenceNumber),
            network = network
        )
            .addOperation(changeTrustOp)
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        transaction.sign(sellerKeyPair)

        response = horizonServer.submitTransaction(transaction.toEnvelopeXdrBase64())
        assertTrue(response.successful, "ChangeTrust transaction should succeed")

        realDelay(3000)

        // Send MOON tokens from issuer to seller
        val issuerAccountReloaded = horizonServer.accounts().account(issuerAccountId)
        val paymentOp = PaymentOperation(
            destination = sellerAccountId,
            asset = moonDollar,
            amount = "2000"
        )

        transaction = TransactionBuilder(
            sourceAccount = Account(issuerAccountId, issuerAccountReloaded.sequenceNumber),
            network = network
        )
            .addOperation(paymentOp)
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        transaction.sign(issuerKeyPair)

        response = horizonServer.submitTransaction(transaction.toEnvelopeXdrBase64())
        assertTrue(response.successful, "Payment transaction should succeed")

        realDelay(3000)

        // Create a sell offer: selling 100 MOON at price 0.5 XLM per MOON
        val amountSelling = "100"
        val price = "0.5"

        val sellerAccountReloaded2 = horizonServer.accounts().account(sellerAccountId)
        val manageSellOfferOp = ManageSellOfferOperation(
            selling = moonDollar,
            buying = AssetTypeNative,
            amount = amountSelling,
            price = Price.fromString(price),
            offerId = 0  // 0 means create new offer
        )

        transaction = TransactionBuilder(
            sourceAccount = Account(sellerAccountId, sellerAccountReloaded2.sequenceNumber),
            network = network
        )
            .addOperation(manageSellOfferOp)
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        transaction.sign(sellerKeyPair)

        response = horizonServer.submitTransaction(transaction.toEnvelopeXdrBase64())
        assertTrue(response.successful, "ManageSellOffer create transaction should succeed")

        realDelay(3000)

        // Verify offer appears in account's offers
        var offersPage = horizonServer.offers().forAccount(sellerAccountId).execute()
        assertEquals(1, offersPage.records.size, "Should have 1 offer")

        var offer = offersPage.records.first()
        assertEquals(AssetTypeNative, offer.buying, "Should be buying native XLM")
        assertEquals(moonDollar, offer.selling, "Should be selling MOON")

        // For a sell offer, the offer amount equals the selling amount
        val offerAmount = offer.amount.toDouble()
        val sellingAmount = amountSelling.toDouble()
        assertEquals(offerAmount, sellingAmount, "Offer amount should equal selling amount")

        // For a sell offer, the offer price equals the input price
        val offerPrice = offer.price.toDouble()
        val sellingPrice = price.toDouble()
        assertEquals(offerPrice, sellingPrice, "Offer price should equal input price")

        assertEquals(sellerAccountId, offer.seller, "Seller should be seller account")

        val offerId = offer.id

        // Verify offer appears in selling asset query
        offersPage = horizonServer.offers().forSellingAsset(moonDollar).execute()
        assertTrue(offersPage.records.size >= 1, "Should have at least 1 offer selling MOON")

        var offerFound = false
        for (o in offersPage.records) {
            if (o.id == offerId) {
                offerFound = true
                break
            }
        }
        assertTrue(offerFound, "Offer should appear in selling asset query")

        // Verify order book
        var orderBook = horizonServer.orderBook()
            .buyingAsset(AssetTypeNative)
            .sellingAsset(moonDollar)
            .execute()

        assertTrue(orderBook.asks.isNotEmpty(), "Order book should have asks")
        val askAmount = orderBook.asks.first().amount.toDouble()
        assertEquals(askAmount, sellingAmount, "Order book ask amount should equal selling amount")

        val askPrice = orderBook.asks.first().price.toDouble()
        assertEquals(askPrice, sellingPrice, "Order book ask price should equal selling price")

        // Verify base and counter assets in order book
        val base = orderBook.base
        val counter = orderBook.counter

        assertTrue(counter is AssetTypeNative, "Counter should be native")
        assertTrue(base is AssetTypeCreditAlphaNum4, "Base should be AlphaNum4")

        val base4 = base as AssetTypeCreditAlphaNum4
        assertEquals(assetCode, base4.code, "Base asset code should be MOON")
        assertEquals(issuerAccountId, base4.issuer, "Base asset issuer should match")

        // Check reverse order book (XLM/MOON instead of MOON/XLM)
        orderBook = horizonServer.orderBook()
            .buyingAsset(moonDollar)
            .sellingAsset(AssetTypeNative)
            .execute()

        assertTrue(orderBook.bids.isNotEmpty(), "Reverse order book should have bids")
        val bidAmount = orderBook.bids.first().amount.toDouble()
        val bidPrice = orderBook.bids.first().price.toDouble()

        // In reverse order book, the calculation should yield 200
        assertTrue(round(bidAmount * bidPrice) == 200.0,
            "Reverse order book: amount * price should equal 200")

        // Update offer: change amount to 150 and price to 0.3
        val amountSelling2 = "150"
        val price2 = "0.3"

        val sellerAccountReloaded3 = horizonServer.accounts().account(sellerAccountId)
        val updateSellOfferOp = ManageSellOfferOperation(
            selling = moonDollar,
            buying = AssetTypeNative,
            amount = amountSelling2,
            price = Price.fromString(price2),
            offerId = offerId.toLong()
        )

        transaction = TransactionBuilder(
            sourceAccount = Account(sellerAccountId, sellerAccountReloaded3.sequenceNumber),
            network = network
        )
            .addOperation(updateSellOfferOp)
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        transaction.sign(sellerKeyPair)

        response = horizonServer.submitTransaction(transaction.toEnvelopeXdrBase64())
        assertTrue(response.successful, "ManageSellOffer update transaction should succeed")

        realDelay(3000)

        // Verify updated offer
        offersPage = horizonServer.offers().forAccount(sellerAccountId).execute()
        assertEquals(1, offersPage.records.size, "Should still have 1 offer")

        offer = offersPage.records.first()
        assertEquals(AssetTypeNative, offer.buying, "Should still be buying XLM")
        assertEquals(moonDollar, offer.selling, "Should still be selling MOON")

        val offerAmount2 = offer.amount.toDouble()
        val sellingAmount2 = amountSelling2.toDouble()
        assertEquals(offerAmount2, sellingAmount2, "Updated offer amount should equal new selling amount")

        val offerPrice2 = offer.price.toDouble()
        val sellingPrice2 = price2.toDouble()
        assertEquals(offerPrice2, sellingPrice2, "Updated offer price should equal new price")

        assertEquals(sellerAccountId, offer.seller, "Seller should still be seller account")

        // Delete offer: set amount to 0
        val amountSelling3 = "0"

        val sellerAccountReloaded4 = horizonServer.accounts().account(sellerAccountId)
        val deleteSellOfferOp = ManageSellOfferOperation(
            selling = moonDollar,
            buying = AssetTypeNative,
            amount = amountSelling3,
            price = Price.fromString(price2),
            offerId = offerId.toLong()
        )

        transaction = TransactionBuilder(
            sourceAccount = Account(sellerAccountId, sellerAccountReloaded4.sequenceNumber),
            network = network
        )
            .addOperation(deleteSellOfferOp)
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        transaction.sign(sellerKeyPair)

        response = horizonServer.submitTransaction(transaction.toEnvelopeXdrBase64())
        assertTrue(response.successful, "ManageSellOffer delete transaction should succeed")

        realDelay(3000)

        // Verify offer no longer exists
        offersPage = horizonServer.offers().forAccount(sellerAccountId).execute()
        assertEquals(0, offersPage.records.size, "Should have no offers after deletion")
    }

    /**
     * Test create passive sell offer operation.
     *
     * This test:
     * 1. Creates issuer and seller accounts
     * 2. Creates a custom asset (MARS) issued by the issuer account
     * 3. Establishes trustline from seller to MARS
     * 4. Sends MARS tokens from issuer to seller
     * 5. Creates a passive sell offer for MARS
     * 6. Verifies offer appears in account's offers with correct details
     * 7. Updates the offer using ManageSellOffer (passive offers can be updated as regular offers)
     * 8. Verifies updated offer has correct values
     * 9. Deletes the offer using ManageSellOffer with amount = 0
     * 10. Verifies offer no longer exists
     *
     * ## Passive Sell Offers
     *
     * A passive sell offer is an offer that won't immediately match existing offers at the same price.
     * It waits passively in the order book until another offer matches it. This prevents creating
     * circular trading paths.
     *
     * Key differences from regular sell offers:
     * - CreatePassiveSellOffer: Creates passive offers only (no offer ID parameter)
     * - Once created, passive offers can be updated/deleted using ManageSellOffer
     * - Passive offers have the same structure as regular offers in queries
     */
    @Test
    fun testCreatePassiveSellOffer() = runTest(timeout = 180.seconds) {
        // Create keypairs for issuer and seller
        val issuerKeyPair = KeyPair.random()
        val sellerKeyPair = KeyPair.random()

        val issuerAccountId = issuerKeyPair.getAccountId()
        val sellerAccountId = sellerKeyPair.getAccountId()

        // Fund seller account via FriendBot
        if (testOn == "testnet") {
            FriendBot.fundTestnetAccount(sellerAccountId)
        } else {
            FriendBot.fundFuturenetAccount(sellerAccountId)
        }

        realDelay(3000)

        // Create issuer account
        val sellerAccount = horizonServer.accounts().account(sellerAccountId)
        val createAccountOp = CreateAccountOperation(
            destination = issuerAccountId,
            startingBalance = "10"
        )

        var transaction = TransactionBuilder(
            sourceAccount = Account(sellerAccountId, sellerAccount.sequenceNumber),
            network = network
        )
            .addOperation(createAccountOp)
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        transaction.sign(sellerKeyPair)

        var response = horizonServer.submitTransaction(transaction.toEnvelopeXdrBase64())
        assertTrue(response.successful, "CreateAccount transaction should succeed")

        realDelay(3000)

        // Get issuer account
        val issuerAccount = horizonServer.accounts().account(issuerAccountId)

        // Create custom asset MARS
        val marsDollar = AssetTypeCreditAlphaNum4("MARS", issuerAccountId)

        // Establish trustline from seller to MARS
        val sellerAccountReloaded = horizonServer.accounts().account(sellerAccountId)
        val changeTrustOp = ChangeTrustOperation(
            asset = marsDollar,
            limit = "10000"
        )

        transaction = TransactionBuilder(
            sourceAccount = Account(sellerAccountId, sellerAccountReloaded.sequenceNumber),
            network = network
        )
            .addOperation(changeTrustOp)
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        transaction.sign(sellerKeyPair)

        response = horizonServer.submitTransaction(transaction.toEnvelopeXdrBase64())
        assertTrue(response.successful, "ChangeTrust transaction should succeed")

        realDelay(3000)

        // Send MARS tokens from issuer to seller
        val issuerAccountReloaded = horizonServer.accounts().account(issuerAccountId)
        val paymentOp = PaymentOperation(
            destination = sellerAccountId,
            asset = marsDollar,
            amount = "2000"
        )

        transaction = TransactionBuilder(
            sourceAccount = Account(issuerAccountId, issuerAccountReloaded.sequenceNumber),
            network = network
        )
            .addOperation(paymentOp)
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        transaction.sign(issuerKeyPair)

        response = horizonServer.submitTransaction(transaction.toEnvelopeXdrBase64())
        assertTrue(response.successful, "Payment transaction should succeed")

        realDelay(3000)

        // Create a passive sell offer: selling 100 MARS at price 0.5 XLM per MARS
        val amountSelling = "100"
        val price = "0.5"

        val sellerAccountReloaded2 = horizonServer.accounts().account(sellerAccountId)
        val createPassiveSellOfferOp = CreatePassiveSellOfferOperation(
            selling = marsDollar,
            buying = AssetTypeNative,
            amount = amountSelling,
            price = Price.fromString(price)
        )

        transaction = TransactionBuilder(
            sourceAccount = Account(sellerAccountId, sellerAccountReloaded2.sequenceNumber),
            network = network
        )
            .addOperation(createPassiveSellOfferOp)
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        transaction.sign(sellerKeyPair)

        response = horizonServer.submitTransaction(transaction.toEnvelopeXdrBase64())
        assertTrue(response.successful, "CreatePassiveSellOffer transaction should succeed")

        realDelay(3000)

        // Verify offer appears in account's offers
        var offersPage = horizonServer.offers().forAccount(sellerAccountId).execute()
        assertEquals(1, offersPage.records.size, "Should have 1 offer")

        var offer = offersPage.records.first()
        assertEquals(AssetTypeNative, offer.buying, "Should be buying native XLM")
        assertEquals(marsDollar, offer.selling, "Should be selling MARS")

        // For a passive sell offer, the details are the same as regular sell offer
        val offerAmount = offer.amount.toDouble()
        val sellingAmount = amountSelling.toDouble()
        assertEquals(offerAmount, sellingAmount, "Offer amount should equal selling amount")

        val offerPrice = offer.price.toDouble()
        val sellingPrice = price.toDouble()
        assertEquals(offerPrice, sellingPrice, "Offer price should equal input price")

        assertEquals(sellerAccountId, offer.seller, "Seller should be seller account")

        val offerId = offer.id

        // Update offer using ManageSellOffer: change amount to 150 and price to 0.3
        val amountSelling2 = "150"
        val price2 = "0.3"

        val sellerAccountReloaded3 = horizonServer.accounts().account(sellerAccountId)
        val manageSellOfferOp = ManageSellOfferOperation(
            selling = marsDollar,
            buying = AssetTypeNative,
            amount = amountSelling2,
            price = Price.fromString(price2),
            offerId = offerId.toLong()
        )

        transaction = TransactionBuilder(
            sourceAccount = Account(sellerAccountId, sellerAccountReloaded3.sequenceNumber),
            network = network
        )
            .addOperation(manageSellOfferOp)
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        transaction.sign(sellerKeyPair)

        response = horizonServer.submitTransaction(transaction.toEnvelopeXdrBase64())
        assertTrue(response.successful, "ManageSellOffer update transaction should succeed")

        realDelay(3000)

        // Verify updated offer
        offersPage = horizonServer.offers().forAccount(sellerAccountId).execute()
        assertEquals(1, offersPage.records.size, "Should still have 1 offer")

        offer = offersPage.records.first()
        assertEquals(AssetTypeNative, offer.buying, "Should still be buying XLM")
        assertEquals(marsDollar, offer.selling, "Should still be selling MARS")

        val offerAmount2 = offer.amount.toDouble()
        val sellingAmount2 = amountSelling2.toDouble()
        assertEquals(offerAmount2, sellingAmount2, "Updated offer amount should equal new selling amount")

        val offerPrice2 = offer.price.toDouble()
        val sellingPrice2 = price2.toDouble()
        assertEquals(offerPrice2, sellingPrice2, "Updated offer price should equal new price")

        assertEquals(sellerAccountId, offer.seller, "Seller should still be seller account")

        // Delete offer using ManageSellOffer: set amount to 0
        val amountSelling3 = "0"

        val sellerAccountReloaded4 = horizonServer.accounts().account(sellerAccountId)
        val deleteSellOfferOp = ManageSellOfferOperation(
            selling = marsDollar,
            buying = AssetTypeNative,
            amount = amountSelling3,
            price = Price.fromString(price2),
            offerId = offerId.toLong()
        )

        transaction = TransactionBuilder(
            sourceAccount = Account(sellerAccountId, sellerAccountReloaded4.sequenceNumber),
            network = network
        )
            .addOperation(deleteSellOfferOp)
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        transaction.sign(sellerKeyPair)

        response = horizonServer.submitTransaction(transaction.toEnvelopeXdrBase64())
        assertTrue(response.successful, "ManageSellOffer delete transaction should succeed")

        realDelay(3000)

        // Verify offer no longer exists
        offersPage = horizonServer.offers().forAccount(sellerAccountId).execute()
        assertEquals(0, offersPage.records.size, "Should have no offers after deletion")
    }

    /**
     * Test offer trades endpoint.
     *
     * This test verifies that the `/offers/{offer_id}/trades` endpoint is properly implemented
     * and can fetch trades for a specific offer.
     *
     * This test:
     * 1. Creates issuer, seller, and buyer accounts
     * 2. Creates a custom asset (TRD) issued by the issuer account
     * 3. Establishes trustlines from seller and buyer to TRD
     * 4. Sends TRD tokens from issuer to seller
     * 5. Seller creates a sell offer for TRD
     * 6. Gets the offer ID
     * 7. Buyer creates a buy offer that matches the sell offer (creating a trade)
     * 8. Queries the trades endpoint for the specific offer ID
     * 9. Verifies the response structure and that trades can be retrieved
     *
     * ## Trades Endpoint
     *
     * The `/offers/{offer_id}/trades` endpoint returns all trades that involved a specific offer.
     * This is useful for tracking the execution history of an offer.
     *
     * Note: Trades only occur when offers match. The test creates matching offers to ensure
     * at least one trade is executed.
     */
    @Test
    fun testOfferTradesEndpoint() = runTest(timeout = 180.seconds) {
        // Create keypairs for issuer, seller, and buyer
        val issuerKeyPair = KeyPair.random()
        val sellerKeyPair = KeyPair.random()
        val buyerKeyPair = KeyPair.random()

        val issuerAccountId = issuerKeyPair.getAccountId()
        val sellerAccountId = sellerKeyPair.getAccountId()
        val buyerAccountId = buyerKeyPair.getAccountId()

        // Fund seller and buyer accounts via FriendBot
        if (testOn == "testnet") {
            FriendBot.fundTestnetAccount(sellerAccountId)
            FriendBot.fundTestnetAccount(buyerAccountId)
        } else {
            FriendBot.fundFuturenetAccount(sellerAccountId)
            FriendBot.fundFuturenetAccount(buyerAccountId)
        }

        realDelay(3000)

        // Create issuer account
        val sellerAccount = horizonServer.accounts().account(sellerAccountId)
        val createAccountOp = CreateAccountOperation(
            destination = issuerAccountId,
            startingBalance = "10"
        )

        var transaction = TransactionBuilder(
            sourceAccount = Account(sellerAccountId, sellerAccount.sequenceNumber),
            network = network
        )
            .addOperation(createAccountOp)
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        transaction.sign(sellerKeyPair)

        var response = horizonServer.submitTransaction(transaction.toEnvelopeXdrBase64())
        assertTrue(response.successful, "CreateAccount transaction should succeed")

        realDelay(3000)

        val issuerAccount = horizonServer.accounts().account(issuerAccountId)

        // Create custom asset TRD
        val assetCode = "TRD"
        val tradeAsset = AssetTypeCreditAlphaNum4(assetCode, issuerAccountId)

        // Seller establishes trustline
        val sellerAccountReloaded = horizonServer.accounts().account(sellerAccountId)
        val changeTrustOp = ChangeTrustOperation(
            asset = tradeAsset,
            limit = "10000"
        )

        transaction = TransactionBuilder(
            sourceAccount = Account(sellerAccountId, sellerAccountReloaded.sequenceNumber),
            network = network
        )
            .addOperation(changeTrustOp)
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        transaction.sign(sellerKeyPair)

        response = horizonServer.submitTransaction(transaction.toEnvelopeXdrBase64())
        assertTrue(response.successful, "Seller ChangeTrust transaction should succeed")

        realDelay(3000)

        // Buyer establishes trustline
        val buyerAccount = horizonServer.accounts().account(buyerAccountId)
        val changeTrustOp2 = ChangeTrustOperation(
            asset = tradeAsset,
            limit = "10000"
        )

        transaction = TransactionBuilder(
            sourceAccount = Account(buyerAccountId, buyerAccount.sequenceNumber),
            network = network
        )
            .addOperation(changeTrustOp2)
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        transaction.sign(buyerKeyPair)

        response = horizonServer.submitTransaction(transaction.toEnvelopeXdrBase64())
        assertTrue(response.successful, "Buyer ChangeTrust transaction should succeed")

        realDelay(3000)

        // Issuer sends asset to seller
        val issuerAccountReloaded = horizonServer.accounts().account(issuerAccountId)
        val paymentOp = PaymentOperation(
            destination = sellerAccountId,
            asset = tradeAsset,
            amount = "2000"
        )

        transaction = TransactionBuilder(
            sourceAccount = Account(issuerAccountId, issuerAccountReloaded.sequenceNumber),
            network = network
        )
            .addOperation(paymentOp)
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        transaction.sign(issuerKeyPair)

        response = horizonServer.submitTransaction(transaction.toEnvelopeXdrBase64())
        assertTrue(response.successful, "Payment transaction should succeed")

        realDelay(3000)

        // Seller creates sell offer
        val amountSelling = "100"
        val price = "0.5"

        val sellerAccountReloaded2 = horizonServer.accounts().account(sellerAccountId)
        val manageSellOfferOp = ManageSellOfferOperation(
            selling = tradeAsset,
            buying = AssetTypeNative,
            amount = amountSelling,
            price = Price.fromString(price),
            offerId = 0
        )

        transaction = TransactionBuilder(
            sourceAccount = Account(sellerAccountId, sellerAccountReloaded2.sequenceNumber),
            network = network
        )
            .addOperation(manageSellOfferOp)
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        transaction.sign(sellerKeyPair)

        response = horizonServer.submitTransaction(transaction.toEnvelopeXdrBase64())
        assertTrue(response.successful, "ManageSellOffer create transaction should succeed")

        realDelay(3000)

        // Get the offer ID
        val offersPage = horizonServer.offers().forAccount(sellerAccountId).execute()
        assertTrue(offersPage.records.isNotEmpty(), "Seller should have at least one offer")

        val offer = offersPage.records.first()
        val offerId = offer.id

        // Buyer creates a buy offer to match the sell offer (this will create a trade)
        val amountBuying = "50"

        val buyerAccountReloaded = horizonServer.accounts().account(buyerAccountId)
        val manageBuyOfferOp = ManageBuyOfferOperation(
            selling = AssetTypeNative,
            buying = tradeAsset,
            buyAmount = amountBuying,
            price = Price.fromString(price),
            offerId = 0
        )

        transaction = TransactionBuilder(
            sourceAccount = Account(buyerAccountId, buyerAccountReloaded.sequenceNumber),
            network = network
        )
            .addOperation(manageBuyOfferOp)
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        transaction.sign(buyerKeyPair)

        response = horizonServer.submitTransaction(transaction.toEnvelopeXdrBase64())
        assertTrue(response.successful, "ManageBuyOffer transaction should succeed")

        realDelay(3000)

        // Now test the /offers/{offer_id}/trades endpoint
        val tradesPage = horizonServer.trades()
            .forOfferId(offerId.toLong())
            .limit(10)
            .execute()

        // Verify that we can fetch trades for this offer
        // The trades list might be empty if the offers didn't match, or contain trades if they did
        assertNotNull(tradesPage, "Trades page should not be null")
        assertNotNull(tradesPage.records, "Trades records should not be null")

        println("Trades for offer $offerId: ${tradesPage.records.size}")

        // If there are trades, verify their structure
        if (tradesPage.records.isNotEmpty()) {
            val trade = tradesPage.records.first()
            assertNotNull(trade.id, "Trade ID should not be null")
            assertTrue(
                trade.baseAccount != null || trade.counterAccount != null,
                "Trade should have at least one account"
            )
            println("Trade found: ${trade.id}")
        }
    }
}
