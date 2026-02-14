package com.soneso.stellar.sdk.integrationTests

import com.soneso.stellar.sdk.*
import com.soneso.stellar.sdk.horizon.HorizonServer
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlin.time.Duration.Companion.seconds
import kotlin.test.*

/**
 * Comprehensive integration tests for FeeBumpTransaction operations.
 *
 * These tests verify the SDK's fee bump transaction operations against a live Stellar testnet.
 * They cover:
 * - Creating and submitting fee bump transactions
 * - Fee bump transactions with regular accounts
 * - Fee bump transactions with muxed accounts (M... addresses)
 * - Verifying fee bump transaction structure in responses
 * - Verifying inner transaction preservation
 * - Operations and effects parsing for fee bump transactions
 *
 * ## Fee Bump Transaction Overview
 *
 * Fee bump transactions allow increasing the fee of a previously submitted transaction
 * that may be stuck due to insufficient fees. The fee bump transaction:
 * - Wraps an existing inner transaction
 * - Has its own fee source account that pays the additional fee
 * - Requires signatures from both the inner transaction source and fee source
 * - Preserves the inner transaction signatures
 *
 * ## Test Execution
 *
 * These tests use real network operations and require:
 * 1. Network access to Stellar testnet
 * 2. FriendBot availability for funding
 * 3. Network latency tolerance (delays after operations)
 *
 * Run: `./gradlew :stellar-sdk:jvmTest --tests "FeeBumpTransactionIntegrationTest"`
 *
 * @see <a href="https://developers.stellar.org/docs/encyclopedia/fee-bump-transactions">Fee-bump Transactions</a>
 * @see <a href="https://github.com/stellar/stellar-protocol/blob/master/core/cap-0015.md">CAP-15: Fee-Bump Transactions</a>
 */
class FeeBumpTransactionIntegrationTest {

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
     * Test submitting a fee bump transaction with regular accounts.
     *
     * This test:
     * 1. Creates and funds three accounts:
     *    - Source account (pays for CreateAccount operation)
     *    - Destination account (receives funds)
     *    - Payer account (pays the fee bump)
     * 2. Creates an inner transaction with CreateAccount operation from source to destination
     * 3. Signs the inner transaction with the source account
     * 4. Creates a fee bump transaction wrapping the inner transaction
     * 5. Sets the fee source to the payer account
     * 6. Signs the fee bump transaction with the payer account
     * 7. Submits the fee bump transaction to the network
     * 8. Verifies the transaction succeeded
     * 9. Verifies the destination account was created with correct balance
     * 10. Retrieves the transaction and verifies fee bump structure:
     *     - feeBumpTransaction field is populated with signatures
     *     - innerTransaction field is populated with correct maxFee
     * 11. Retrieves the inner transaction by its hash
     * 12. Verifies the inner transaction source account is correct
     * 13. Verifies operations and effects can be parsed for both accounts
     */
    @Test
    fun testSubmitFeeBumpTransaction() = runTest(timeout = 120.seconds) {
        // 1. Create and fund accounts
        val sourceKeyPair = KeyPair.random()
        val sourceId = sourceKeyPair.getAccountId()
        val destinationKeyPair = KeyPair.random()
        val destinationId = destinationKeyPair.getAccountId()
        val payerKeyPair = KeyPair.random()
        val payerId = payerKeyPair.getAccountId()

        println("Source account ID: $sourceId")
        println("Destination account ID: $destinationId")
        println("Payer account ID: $payerId")

        if (testOn == "testnet") {
            FriendBot.fundTestnetAccount(sourceId)
            FriendBot.fundTestnetAccount(payerId)
        } else {
            FriendBot.fundFuturenetAccount(sourceId)
            FriendBot.fundFuturenetAccount(payerId)
        }

        realDelay(3000)

        // 2. Get source account details
        val sourceAccount = horizonServer.accounts().account(sourceId)

        // 3. Create inner transaction with CreateAccount operation
        val innerTx = TransactionBuilder(
            sourceAccount = Account(sourceId, sourceAccount.sequenceNumber),
            network = network
        )
            .addOperation(
                CreateAccountOperation(
                    destination = destinationId,
                    startingBalance = "10"
                )
            )
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        // 4. Sign inner transaction with source account
        innerTx.sign(sourceKeyPair)

        println("Inner transaction signed")

        // 5. Create fee bump transaction
        val feeBump = FeeBumpTransactionBuilder(innerTx)
            .setBaseFee(200)
            .setFeeSource(payerId)
            .build()

        // 6. Sign fee bump transaction with payer account
        feeBump.sign(payerKeyPair)

        println("Fee bump transaction signed")

        // 7. Submit fee bump transaction
        val response = horizonServer.submitTransaction(feeBump.toEnvelopeXdrBase64())

        // 8. Verify transaction succeeded
        assertTrue(response.successful, "Fee bump transaction should succeed")
        assertNotNull(response.hash, "Response should have transaction hash")

        println("Fee bump transaction submitted: ${response.hash}")

        realDelay(3000)

        // 9. Verify destination account was created with correct balance
        val destination = horizonServer.accounts().account(destinationId)
        var found = false
        for (balance in destination.balances) {
            if (balance.assetType == "native") {
                val balanceAmount = balance.balance.toDouble()
                assertTrue(balanceAmount > 9, "Balance should be > 9, got $balanceAmount")
                found = true
                break
            }
        }
        assertTrue(found, "Native balance should be found")

        println("Destination account created with balance")

        // 10. Retrieve transaction and verify fee bump structure
        var transaction = horizonServer.transactions().transaction(response.hash)

        assertNotNull(transaction.feeBumpTransaction, "Transaction should have feeBumpTransaction field")
        assertTrue(
            transaction.feeBumpTransaction.signatures.isNotEmpty(),
            "Fee bump transaction should have signatures"
        )
        assertEquals(
            100,
            transaction.innerTransaction?.maxFee,
            "Inner transaction maxFee should be 100"
        )

        println("Fee bump transaction structure verified")

        // 11. Retrieve inner transaction by its hash
        val innerTxHash = transaction.innerTransaction!!.hash
        transaction = horizonServer.transactions().transaction(innerTxHash)

        // 12. Verify inner transaction source account
        assertEquals(
            sourceId,
            transaction.sourceAccount,
            "Inner transaction source account should match"
        )

        println("Inner transaction verified")

        // 13. Verify operations and effects can be parsed for both accounts
        var operationsPage = horizonServer.operations().forAccount(sourceId).execute()
        assertTrue(operationsPage.records.isNotEmpty(), "Source account should have operations")

        operationsPage = horizonServer.operations().forAccount(payerId).execute()
        assertTrue(operationsPage.records.isNotEmpty(), "Payer account should have operations")

        var effectsPage = horizonServer.effects().forAccount(sourceId).execute()
        assertTrue(effectsPage.records.isNotEmpty(), "Source account should have effects")

        effectsPage = horizonServer.effects().forAccount(payerId).execute()
        assertTrue(effectsPage.records.isNotEmpty(), "Payer account should have effects")

        println("Operations and effects verified")
    }

    /**
     * Test submitting a fee bump transaction with muxed accounts.
     *
     * This test verifies that fee bump transactions work correctly with muxed accounts (M... addresses).
     * Muxed accounts allow multiple virtual accounts to share the same underlying Stellar account.
     *
     * This test:
     * 1. Creates and funds three accounts:
     *    - Source account (pays for CreateAccount operation)
     *    - Destination account (receives funds)
     *    - Payer account (pays the fee bump)
     * 2. Creates muxed account IDs for source and payer accounts with unique muxed IDs
     * 3. Creates an inner transaction with CreateAccount operation
     *    - Sets muxed source account on the operation
     * 4. Signs the inner transaction with the source account keypair
     * 5. Creates a fee bump transaction with muxed payer account
     * 6. Signs the fee bump transaction with the payer account keypair
     * 7. Submits the fee bump transaction to the network
     * 8. Verifies the transaction succeeded
     * 9. Verifies the destination account was created with correct balance
     * 10. Retrieves the transaction and verifies fee bump structure:
     *     - feeBumpTransaction field is populated with signatures
     *     - innerTransaction field is populated with correct maxFee
     * 11. Retrieves the inner transaction by its hash
     * 12. Verifies the inner transaction source account matches the base account (not muxed)
     * 13. Verifies operations and effects can be parsed for both base accounts
     */
    @Test
    fun testSubmitFeeBumpTransactionWithMuxedAccounts() = runTest(timeout = 120.seconds) {
        // 1. Create and fund accounts
        val sourceKeyPair = KeyPair.random()
        val sourceId = sourceKeyPair.getAccountId()
        val destinationKeyPair = KeyPair.random()
        val destinationId = destinationKeyPair.getAccountId()
        val payerKeyPair = KeyPair.random()
        val payerId = payerKeyPair.getAccountId()

        println("Source account ID: $sourceId")
        println("Destination account ID: $destinationId")
        println("Payer account ID: $payerId")

        if (testOn == "testnet") {
            FriendBot.fundTestnetAccount(sourceId)
            FriendBot.fundTestnetAccount(payerId)
        } else {
            FriendBot.fundFuturenetAccount(sourceId)
            FriendBot.fundFuturenetAccount(payerId)
        }

        realDelay(3000)

        // 2. Create muxed accounts
        val muxedSourceAccount = MuxedAccount(sourceId, 97839283928292UL)
        val muxedPayerAccount = MuxedAccount(payerId, 24242423737333UL)

        println("Muxed source account: ${muxedSourceAccount.address}")
        println("Muxed payer account: ${muxedPayerAccount.address}")

        // 3. Get source account details
        val sourceAccount = horizonServer.accounts().account(sourceId)

        // 4. Create inner transaction with CreateAccount operation and muxed source
        val createAccountOp = CreateAccountOperation(
            destination = destinationId,
            startingBalance = "10"
        )
        createAccountOp.sourceAccount = muxedSourceAccount.address

        val innerTx = TransactionBuilder(
            sourceAccount = Account(sourceId, sourceAccount.sequenceNumber),
            network = network
        )
            .addOperation(createAccountOp)
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        // 5. Sign inner transaction with source account
        innerTx.sign(sourceKeyPair)

        println("Inner transaction signed")

        // 6. Create fee bump transaction with muxed payer account
        val feeBump = FeeBumpTransactionBuilder(innerTx)
            .setBaseFee(200)
            .setFeeSource(muxedPayerAccount.address)
            .build()

        // 7. Sign fee bump transaction with payer account
        feeBump.sign(payerKeyPair)

        println("Fee bump transaction signed")

        // 8. Submit fee bump transaction
        val response = horizonServer.submitTransaction(feeBump.toEnvelopeXdrBase64())

        // 9. Verify transaction succeeded
        assertTrue(response.successful, "Fee bump transaction should succeed")
        assertNotNull(response.hash, "Response should have transaction hash")

        println("Fee bump transaction submitted: ${response.hash}")

        realDelay(3000)

        // 10. Verify destination account was created with correct balance
        val destination = horizonServer.accounts().account(destinationId)
        var found = false
        for (balance in destination.balances) {
            if (balance.assetType == "native") {
                val balanceAmount = balance.balance.toDouble()
                assertTrue(balanceAmount > 9, "Balance should be > 9, got $balanceAmount")
                found = true
                break
            }
        }
        assertTrue(found, "Native balance should be found")

        println("Destination account created with balance")

        // 11. Retrieve transaction and verify fee bump structure
        var transaction = horizonServer.transactions().transaction(response.hash)

        assertNotNull(transaction.feeBumpTransaction, "Transaction should have feeBumpTransaction field")
        assertTrue(
            transaction.feeBumpTransaction.signatures.isNotEmpty(),
            "Fee bump transaction should have signatures"
        )
        assertEquals(
            100,
            transaction.innerTransaction?.maxFee,
            "Inner transaction maxFee should be 100"
        )

        println("Fee bump transaction structure verified")

        // 12. Retrieve inner transaction by its hash
        val innerTxHash = transaction.innerTransaction!!.hash
        transaction = horizonServer.transactions().transaction(innerTxHash)

        // 13. Verify inner transaction source account (should be base account, not muxed)
        assertEquals(
            sourceId,
            transaction.sourceAccount,
            "Inner transaction source account should match base account"
        )

        println("Inner transaction verified")

        // 14. Verify operations and effects can be parsed for both base accounts
        var operationsPage = horizonServer.operations().forAccount(sourceId).execute()
        assertTrue(operationsPage.records.isNotEmpty(), "Source account should have operations")

        operationsPage = horizonServer.operations().forAccount(payerId).execute()
        assertTrue(operationsPage.records.isNotEmpty(), "Payer account should have operations")

        var effectsPage = horizonServer.effects().forAccount(sourceId).execute()
        assertTrue(effectsPage.records.isNotEmpty(), "Source account should have effects")

        effectsPage = horizonServer.effects().forAccount(payerId).execute()
        assertTrue(effectsPage.records.isNotEmpty(), "Payer account should have effects")

        println("Operations and effects verified")
    }
}
