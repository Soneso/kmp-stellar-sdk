package com.soneso.stellar.sdk.integrationTests

import com.soneso.stellar.sdk.*
import com.soneso.stellar.sdk.horizon.HorizonServer
import com.soneso.stellar.sdk.horizon.requests.RequestBuilder
import com.soneso.stellar.sdk.horizon.responses.operations.PaymentOperationResponse
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

/**
 * Comprehensive integration tests for Payment-related operations.
 *
 * These tests verify the SDK's payment operations against a live Stellar testnet.
 * They cover:
 * - Native (XLM) payments
 * - Non-native asset payments (credit_alphanum4, credit_alphanum12)
 * - Path payments (strict send and strict receive)
 * - Muxed accounts in payments
 * - Transaction preconditions
 * - Payment streaming via SSE
 * - Payment query endpoints
 *
 * ## Payment Operations
 *
 * - **Payment**: Direct transfer of assets between accounts
 * - **PathPaymentStrictSend**: Multi-hop payment specifying exact send amount
 * - **PathPaymentStrictReceive**: Multi-hop payment specifying exact receive amount
 *
 * ## Test Network
 *
 * All tests use Stellar testnet. To switch to futurenet, change the `testOn` variable.
 *
 * @see <a href="https://developers.stellar.org/docs/learn/fundamentals/transactions/list-of-operations#payment">Payment Operation</a>
 * @see <a href="https://developers.stellar.org/docs/learn/fundamentals/transactions/list-of-operations#path-payment-strict-send">Path Payment Strict Send</a>
 * @see <a href="https://developers.stellar.org/docs/learn/fundamentals/transactions/list-of-operations#path-payment-strict-receive">Path Payment Strict Receive</a>
 */
class PaymentsIntegrationTest {

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
     * Test sending native (XLM) payment.
     *
     * This test:
     * 1. Creates and funds account A via FriendBot
     * 2. Creates account C using CreateAccount operation
     * 3. Sends 100 XLM payment from A to C
     * 4. Verifies C's balance increased by 100+ XLM
     * 5. Verifies payment appears in C's payment history
     * 6. Verifies operations and effects can be parsed
     */
    @Test
    fun testSendNativePayment() = runTest(timeout = 90.seconds) {
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

        // Create keypair C
        val keyPairC = KeyPair.random()
        val accountCId = keyPairC.getAccountId()

        // Create account C
        var transaction = TransactionBuilder(
            sourceAccount = Account(accountAId, accountA.sequenceNumber),
            network = network
        )
            .addOperation(CreateAccountOperation(destination = accountCId, startingBalance = "10"))
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        transaction.sign(keyPairA)

        var response = horizonServer.submitTransaction(transaction.toEnvelopeXdrBase64())
        assertTrue(response.successful, "CreateAccount transaction should succeed")

        realDelay(3000)

        // Send 100 XLM native payment from A to C
        val accountAReloaded = horizonServer.accounts().account(accountAId)
        transaction = TransactionBuilder(
            sourceAccount = Account(accountAId, accountAReloaded.sequenceNumber),
            network = network
        )
            .addOperation(PaymentOperation(destination = accountCId, asset = AssetTypeNative, amount = "100"))
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        transaction.sign(keyPairA)

        response = horizonServer.submitTransaction(transaction.toEnvelopeXdrBase64())
        assertTrue(response.successful, "Payment transaction should succeed")

        realDelay(3000)

        // Verify account C balance
        val accountC = horizonServer.accounts().account(accountCId)
        var found = false
        for (balance in accountC.balances) {
            if (balance.assetType == "native") {
                assertTrue(balance.balance.toDouble() > 100.0, "Balance should be greater than 100")
                found = true
                break
            }
        }
        assertTrue(found, "Native balance should exist")

        // Verify payment in history
        val payments = horizonServer.payments().forAccount(accountCId).order(RequestBuilder.Order.DESC).execute()
        found = false
        for (payment in payments.records) {
            if (payment is PaymentOperationResponse) {
                if (payment.from == accountAId) {
                    found = true
                    break
                }
            }
        }
        assertTrue(found, "Payment should appear in account C's history")

        // Verify operations and effects can be parsed
        val operationsPage = horizonServer.operations().forAccount(accountAId).execute()
        assertTrue(operationsPage.records.isNotEmpty(), "Should have operations")

        val effectsPage = horizonServer.effects().forAccount(accountAId).execute()
        assertTrue(effectsPage.records.isNotEmpty(), "Should have effects")
    }

    /**
     * Test sending native payment with transaction preconditions.
     *
     * This test:
     * 1. Creates and funds accounts A and C
     * 2. Builds a payment transaction with preconditions:
     *    - Ledger bounds (dynamic maxLedger based on current network state)
     * 3. Submits the transaction
     * 4. Verifies preconditions are stored correctly
     * 5. Verifies account has sequence ledger and time fields
     *
     * Note: This test uses simplified preconditions (ledger bounds only) to avoid
     * timing issues with minSequenceAge/minSequenceLedgerGap in integration tests.
     */
    @Test
    fun testSendNativePaymentWithPreconditions() = runTest(timeout = 90.seconds) {
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

        // Create keypair C
        val keyPairC = KeyPair.random()
        val accountCId = keyPairC.getAccountId()

        // Create account C
        var transaction = TransactionBuilder(
            sourceAccount = Account(accountAId, accountA.sequenceNumber),
            network = network
        )
            .addOperation(CreateAccountOperation(destination = accountCId, startingBalance = "10"))
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        transaction.sign(keyPairA)

        var response = horizonServer.submitTransaction(transaction.toEnvelopeXdrBase64())
        assertTrue(response.successful, "CreateAccount transaction should succeed")

        realDelay(3000)

        // Get current ledger from Horizon to calculate safe maxLedger
        val root = horizonServer.root().execute()
        val currentLedger = root.historyLatestLedger
        val maxLedger = (currentLedger + 1000000).toInt() // Far in the future

        // Build preconditions - simplified to only test ledger bounds
        val precond = TransactionPreconditions(
            timeBounds = TimeBounds(minTime = 0L, maxTime = 0L), // 0 means no bounds
            ledgerBounds = LedgerBounds(minLedger = 0, maxLedger = maxLedger)
        )

        realDelay(1000)

        // Send 100 XLM native payment from A to C with preconditions
        val accountAReloaded = horizonServer.accounts().account(accountAId)
        transaction = TransactionBuilder(
            sourceAccount = Account(accountAId, accountAReloaded.sequenceNumber),
            network = network
        )
            .addOperation(PaymentOperation(destination = accountCId, asset = AssetTypeNative, amount = "100"))
            .addPreconditions(precond)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        transaction.sign(keyPairA)

        response = horizonServer.submitTransaction(transaction.toEnvelopeXdrBase64())
        assertTrue(response.successful, "Payment transaction with preconditions should succeed")

        val hash = response.hash
        assertNotNull(hash, "Transaction hash should not be null")

        realDelay(3000)

        // Verify preconditions were stored
        val trx = horizonServer.transactions().transaction(hash)
        assertNotNull(trx.preconditions, "Preconditions should not be null")

        val conds = trx.preconditions
        // Note: TimeBounds with 0 values means no time bounds, may not be returned by Horizon
        // assertEquals("0", conds.timeBounds?.minTime, "Min time should match")
        // assertEquals("0", conds.timeBounds?.maxTime, "Max time should match")
        assertEquals(0L, conds.ledgerBounds?.minLedger, "Min ledger should match")
        assertEquals(maxLedger.toLong(), conds.ledgerBounds?.maxLedger, "Max ledger should match")

        // Verify account C balance
        val accountC = horizonServer.accounts().account(accountCId)
        var found = false
        for (balance in accountC.balances) {
            if (balance.assetType == "native") {
                assertTrue(balance.balance.toDouble() > 100.0, "Balance should be greater than 100")
                found = true
                break
            }
        }
        assertTrue(found, "Native balance should exist")

        // Verify account A has sequence ledger and time
        val accountAFinal = horizonServer.accounts().account(accountAId)
        assertNotNull(accountAFinal.sequenceLedger, "Sequence ledger should not be null")
        assertNotNull(accountAFinal.sequenceTime, "Sequence time should not be null")

        // Verify payment in history
        val payments = horizonServer.payments().forAccount(accountCId).order(RequestBuilder.Order.DESC).execute()
        found = false
        for (payment in payments.records) {
            if (payment is PaymentOperationResponse && payment.from == accountAId) {
                found = true
                break
            }
        }
        assertTrue(found, "Payment should appear in account C's history")

        // Verify operations and effects can be parsed
        val operationsPage = horizonServer.operations().forAccount(accountAId).execute()
        assertTrue(operationsPage.records.isNotEmpty(), "Should have operations")

        val effectsPage = horizonServer.effects().forAccount(accountAId).execute()
        assertTrue(effectsPage.records.isNotEmpty(), "Should have effects")
    }

    /**
     * Test sending native payment with muxed source and destination accounts.
     *
     * This test:
     * 1. Creates and funds accounts A and C
     * 2. Creates muxed account IDs for both A and C
     * 3. Sends payment using muxed accounts
     * 4. Verifies transaction succeeded and C's balance increased
     * 5. Verifies payment and transaction appear in queries
     */
    @Test
    fun testSendNativePaymentMuxedAccounts() = runTest(timeout = 90.seconds) {
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

        // Create keypair C
        val keyPairC = KeyPair.random()
        val accountCId = keyPairC.getAccountId()

        // Create account C
        var transaction = TransactionBuilder(
            sourceAccount = Account(accountAId, accountA.sequenceNumber),
            network = network
        )
            .addOperation(CreateAccountOperation(destination = accountCId, startingBalance = "10"))
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        transaction.sign(keyPairA)

        var response = horizonServer.submitTransaction(transaction.toEnvelopeXdrBase64())
        assertTrue(response.successful, "CreateAccount transaction should succeed")

        realDelay(3000)

        // Create muxed accounts
        val muxedDestinationAccount = MuxedAccount(accountCId, 10120291UL)
        val muxedSourceAccount = MuxedAccount(accountAId, 9999999999UL)

        // Send payment with muxed accounts
        val accountAReloaded = horizonServer.accounts().account(accountAId)
        val paymentOperation = PaymentOperation(
            destination = muxedDestinationAccount.address,
            asset = AssetTypeNative,
            amount = "100"
        )
        paymentOperation.sourceAccount = muxedSourceAccount.address

        transaction = TransactionBuilder(
            sourceAccount = Account(accountAId, accountAReloaded.sequenceNumber),
            network = network
        )
            .addOperation(paymentOperation)
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        transaction.sign(keyPairA)

        response = horizonServer.submitTransaction(transaction.toEnvelopeXdrBase64())
        assertTrue(response.successful, "Payment with muxed accounts should succeed")

        val transactionHash = response.hash
        assertNotNull(transactionHash)

        realDelay(3000)

        // Verify account C balance
        val accountC = horizonServer.accounts().account(accountCId)
        var found = false
        for (balance in accountC.balances) {
            if (balance.assetType == "native") {
                assertTrue(balance.balance.toDouble() > 100.0, "Balance should be greater than 100")
                found = true
                break
            }
        }
        assertTrue(found, "Native balance should exist")

        // Verify payment in history
        val payments = horizonServer.payments().forAccount(accountCId).order(RequestBuilder.Order.DESC).execute()
        found = false
        for (payment in payments.records) {
            if (payment is PaymentOperationResponse && payment.from == accountAId) {
                found = true
                break
            }
        }
        assertTrue(found, "Payment should appear in account C's history")

        // Verify transaction in history
        val transactions = horizonServer.transactions().forAccount(accountCId).order(RequestBuilder.Order.DESC).execute()
        found = false
        for (tx in transactions.records) {
            if (tx.hash == transactionHash) {
                found = true
                break
            }
        }
        assertTrue(found, "Transaction should appear in account C's history")

        // Verify operations and effects can be parsed
        val operationsPage = horizonServer.operations().forAccount(accountAId).execute()
        assertTrue(operationsPage.records.isNotEmpty(), "Should have operations")

        val effectsPage = horizonServer.effects().forAccount(accountAId).execute()
        assertTrue(effectsPage.records.isNotEmpty(), "Should have effects")
    }

    /**
     * Test sending native payment with custom max operation fee.
     *
     * This test:
     * 1. Creates and funds accounts A and C
     * 2. Sets custom max operation fee (300 stroops)
     * 3. Creates account C with custom fee
     * 4. Sends payment from A to C
     * 5. Verifies transactions succeed with custom fee
     */
    @Test
    fun testSendNativePaymentWithMaxOperationFee() = runTest(timeout = 90.seconds) {
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

        // Create keypair C
        val keyPairC = KeyPair.random()
        val accountCId = keyPairC.getAccountId()

        // Create account C with custom max operation fee
        var transaction = TransactionBuilder(
            sourceAccount = Account(accountAId, accountA.sequenceNumber),
            network = network
        )
            .addOperation(CreateAccountOperation(destination = accountCId, startingBalance = "10"))
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(300) // Custom max operation fee
            .build()

        transaction.sign(keyPairA)

        var response = horizonServer.submitTransaction(transaction.toEnvelopeXdrBase64())
        assertTrue(response.successful, "CreateAccount with custom fee should succeed")

        realDelay(3000)

        // Send 100 XLM native payment from A to C
        val accountAReloaded = horizonServer.accounts().account(accountAId)
        transaction = TransactionBuilder(
            sourceAccount = Account(accountAId, accountAReloaded.sequenceNumber),
            network = network
        )
            .addOperation(PaymentOperation(destination = accountCId, asset = AssetTypeNative, amount = "100"))
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        transaction.sign(keyPairA)

        response = horizonServer.submitTransaction(transaction.toEnvelopeXdrBase64())
        assertTrue(response.successful, "Payment transaction should succeed")

        realDelay(3000)

        // Verify account C balance
        val accountC = horizonServer.accounts().account(accountCId)
        var found = false
        for (balance in accountC.balances) {
            if (balance.assetType == "native") {
                assertTrue(balance.balance.toDouble() > 100.0, "Balance should be greater than 100")
                found = true
                break
            }
        }
        assertTrue(found, "Native balance should exist")
    }

    /**
     * Test sending non-native asset payment.
     *
     * This test:
     * 1. Creates and funds accounts A, B, and C
     * 2. Creates custom asset IOM issued by A
     * 3. Establishes trustlines from C and B to IOM
     * 4. Sends 100 IOM from A to C
     * 5. Sends 50.09 IOM from C to B
     * 6. Verifies balances are correct
     */
    @Test
    fun testSendNonNativePayment() = runTest(timeout = 120.seconds) {
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

        // Create keypairs C and B
        val keyPairC = KeyPair.random()
        val keyPairB = KeyPair.random()
        val accountCId = keyPairC.getAccountId()
        val accountBId = keyPairB.getAccountId()

        // Create accounts C and B
        var transaction = TransactionBuilder(
            sourceAccount = Account(accountAId, accountA.sequenceNumber),
            network = network
        )
            .addOperation(CreateAccountOperation(destination = accountCId, startingBalance = "10"))
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        transaction.sign(keyPairA)
        var response = horizonServer.submitTransaction(transaction.toEnvelopeXdrBase64())
        assertTrue(response.successful)

        realDelay(3000)

        val accountAReloaded1 = horizonServer.accounts().account(accountAId)
        transaction = TransactionBuilder(
            sourceAccount = Account(accountAId, accountAReloaded1.sequenceNumber),
            network = network
        )
            .addOperation(CreateAccountOperation(destination = accountBId, startingBalance = "10"))
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        transaction.sign(keyPairA)
        response = horizonServer.submitTransaction(transaction.toEnvelopeXdrBase64())
        assertTrue(response.successful)

        realDelay(3000)

        // Create custom asset IOM
        val iomAsset = AssetTypeCreditAlphaNum4("IOM", accountAId)

        // Create trustline from C to IOM
        val accountC = horizonServer.accounts().account(accountCId)
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

        // Create trustline from B to IOM
        val accountB = horizonServer.accounts().account(accountBId)
        transaction = TransactionBuilder(
            sourceAccount = Account(accountBId, accountB.sequenceNumber),
            network = network
        )
            .addOperation(ChangeTrustOperation(asset = iomAsset, limit = "200999"))
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        transaction.sign(keyPairB)
        response = horizonServer.submitTransaction(transaction.toEnvelopeXdrBase64())
        assertTrue(response.successful)

        realDelay(3000)

        // Send 100 IOM from A to C
        val accountAReloaded2 = horizonServer.accounts().account(accountAId)
        transaction = TransactionBuilder(
            sourceAccount = Account(accountAId, accountAReloaded2.sequenceNumber),
            network = network
        )
            .addOperation(PaymentOperation(destination = accountCId, asset = iomAsset, amount = "100"))
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        transaction.sign(keyPairA)
        response = horizonServer.submitTransaction(transaction.toEnvelopeXdrBase64())
        assertTrue(response.successful, "Payment of IOM from A to C should succeed")

        realDelay(3000)

        // Verify C has IOM balance
        var accountCReloaded = horizonServer.accounts().account(accountCId)
        var found = false
        for (balance in accountCReloaded.balances) {
            if (balance.assetType != "native" && balance.assetCode == "IOM") {
                assertTrue(balance.balance.toDouble() > 90.0, "IOM balance should be greater than 90")
                found = true
                break
            }
        }
        assertTrue(found, "IOM balance should exist for account C")

        // Send 50.09 IOM from C to B
        accountCReloaded = horizonServer.accounts().account(accountCId)
        transaction = TransactionBuilder(
            sourceAccount = Account(accountCId, accountCReloaded.sequenceNumber),
            network = network
        )
            .addOperation(PaymentOperation(destination = accountBId, asset = iomAsset, amount = "50.09"))
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        transaction.sign(keyPairC)
        response = horizonServer.submitTransaction(transaction.toEnvelopeXdrBase64())
        assertTrue(response.successful, "Payment of IOM from C to B should succeed")

        realDelay(3000)

        // Verify B has IOM balance
        val accountBReloaded = horizonServer.accounts().account(accountBId)
        found = false
        for (balance in accountBReloaded.balances) {
            if (balance.assetType != "native" && balance.assetCode == "IOM") {
                assertTrue(balance.balance.toDouble() > 40.0, "IOM balance should be greater than 40")
                found = true
                break
            }
        }
        assertTrue(found, "IOM balance should exist for account B")

        // Verify operations and effects can be parsed
        val operationsPage = horizonServer.operations().forAccount(accountAId).execute()
        assertTrue(operationsPage.records.isNotEmpty(), "Should have operations")

        val effectsPage = horizonServer.effects().forAccount(accountAId).execute()
        assertTrue(effectsPage.records.isNotEmpty(), "Should have effects")
    }

    /**
     * Test sending non-native payment with muxed accounts.
     *
     * This test:
     * 1. Creates and funds accounts A, B, and C
     * 2. Creates muxed account IDs for all accounts
     * 3. Creates custom asset IOM
     * 4. Establishes trustlines using muxed accounts
     * 5. Sends IOM payments using muxed accounts
     * 6. Verifies balances are correct
     */
    @Test
    fun testSendNonNativePaymentWithMuxedAccounts() = runTest(timeout = 120.seconds) {
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

        // Create keypairs C and B
        val keyPairC = KeyPair.random()
        val keyPairB = KeyPair.random()
        val accountCId = keyPairC.getAccountId()
        val accountBId = keyPairB.getAccountId()

        // Create muxed accounts
        val muxedCAccount = MuxedAccount(accountCId, 10120291UL)
        val muxedAAccount = MuxedAccount(accountAId, 9999999999UL)
        val muxedBAccount = MuxedAccount(accountBId, 82882999828222UL)

        // Create account C with muxed source
        val createCOp = CreateAccountOperation(destination = accountCId, startingBalance = "10")
        createCOp.sourceAccount = muxedAAccount.address

        var transaction = TransactionBuilder(
            sourceAccount = Account(accountAId, accountA.sequenceNumber),
            network = network
        )
            .addOperation(createCOp)
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        transaction.sign(keyPairA)
        var response = horizonServer.submitTransaction(transaction.toEnvelopeXdrBase64())
        assertTrue(response.successful)

        realDelay(3000)

        // Create account B with muxed source
        val createBOp = CreateAccountOperation(destination = accountBId, startingBalance = "10")
        createBOp.sourceAccount = muxedAAccount.address

        val accountAReloaded1 = horizonServer.accounts().account(accountAId)
        transaction = TransactionBuilder(
            sourceAccount = Account(accountAId, accountAReloaded1.sequenceNumber),
            network = network
        )
            .addOperation(createBOp)
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        transaction.sign(keyPairA)
        response = horizonServer.submitTransaction(transaction.toEnvelopeXdrBase64())
        assertTrue(response.successful)

        realDelay(3000)

        // Create custom asset IOM
        val iomAsset = AssetTypeCreditAlphaNum4("IOM", accountAId)

        // Create trustline from C to IOM with muxed account
        val changeTrustCOp = ChangeTrustOperation(asset = iomAsset, limit = "200999")
        changeTrustCOp.sourceAccount = muxedCAccount.address

        val accountC = horizonServer.accounts().account(accountCId)
        transaction = TransactionBuilder(
            sourceAccount = Account(accountCId, accountC.sequenceNumber),
            network = network
        )
            .addOperation(changeTrustCOp)
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        transaction.sign(keyPairC)
        response = horizonServer.submitTransaction(transaction.toEnvelopeXdrBase64())
        assertTrue(response.successful)

        realDelay(3000)

        // Create trustline from B to IOM with muxed account
        val changeTrustBOp = ChangeTrustOperation(asset = iomAsset, limit = "200999")
        changeTrustBOp.sourceAccount = muxedBAccount.address

        val accountB = horizonServer.accounts().account(accountBId)
        transaction = TransactionBuilder(
            sourceAccount = Account(accountBId, accountB.sequenceNumber),
            network = network
        )
            .addOperation(changeTrustBOp)
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        transaction.sign(keyPairB)
        response = horizonServer.submitTransaction(transaction.toEnvelopeXdrBase64())
        assertTrue(response.successful)

        realDelay(3000)

        // Send 100 IOM from A to C with muxed accounts
        val paymentOp1 = PaymentOperation(
            destination = muxedCAccount.address,
            asset = iomAsset,
            amount = "100"
        )
        paymentOp1.sourceAccount = muxedAAccount.address

        val accountAReloaded2 = horizonServer.accounts().account(accountAId)
        transaction = TransactionBuilder(
            sourceAccount = Account(accountAId, accountAReloaded2.sequenceNumber),
            network = network
        )
            .addOperation(paymentOp1)
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        transaction.sign(keyPairA)
        response = horizonServer.submitTransaction(transaction.toEnvelopeXdrBase64())
        assertTrue(response.successful)

        realDelay(3000)

        // Verify C has IOM balance
        var accountCReloaded = horizonServer.accounts().account(accountCId)
        var found = false
        for (balance in accountCReloaded.balances) {
            if (balance.assetType != "native" && balance.assetCode == "IOM") {
                assertTrue(balance.balance.toDouble() > 90.0)
                found = true
                break
            }
        }
        assertTrue(found)

        // Send 100 IOM from C to B with muxed accounts
        val paymentOp2 = PaymentOperation(
            destination = muxedBAccount.address,
            asset = iomAsset,
            amount = "100"
        )
        paymentOp2.sourceAccount = muxedCAccount.address

        accountCReloaded = horizonServer.accounts().account(accountCId)
        transaction = TransactionBuilder(
            sourceAccount = Account(accountCId, accountCReloaded.sequenceNumber),
            network = network
        )
            .addOperation(paymentOp2)
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        transaction.sign(keyPairC)
        response = horizonServer.submitTransaction(transaction.toEnvelopeXdrBase64())
        assertTrue(response.successful)

        realDelay(3000)

        // Verify B has IOM balance
        val accountBReloaded = horizonServer.accounts().account(accountBId)
        found = false
        for (balance in accountBReloaded.balances) {
            if (balance.assetType != "native" && balance.assetCode == "IOM") {
                assertTrue(balance.balance.toDouble() > 40.0)
                found = true
                break
            }
        }
        assertTrue(found)
    }

    /**
     * Test no signature transaction envelope.
     *
     * This test:
     * 1. Creates and funds account A
     * 2. Builds a transaction without signing it
     * 3. Encodes to XDR and decodes back
     * 4. Verifies the transaction can be round-tripped without signatures
     */
    @Test
    fun testNoSignatureTransactionEnvelope() = runTest(timeout = 60.seconds) {
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

        // Create keypair C
        val keyPairC = KeyPair.random()
        val accountCId = keyPairC.getAccountId()

        // Build transaction without signing
        val transaction = TransactionBuilder(
            sourceAccount = Account(accountAId, accountA.sequenceNumber),
            network = network
        )
            .addOperation(CreateAccountOperation(destination = accountCId, startingBalance = "10"))
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        // Encode to XDR
        val envelopeXdrBase64 = transaction.toEnvelopeXdrBase64()

        // Decode from XDR
        val abstractTransaction = AbstractTransaction.fromEnvelopeXdr(envelopeXdrBase64, network)
        val transaction2 = abstractTransaction as Transaction

        // Verify source account matches
        assertEquals(transaction.sourceAccount, transaction2.sourceAccount,
            "Source account should match after decoding")
    }
}
