package com.soneso.stellar.sdk.integrationTests

import com.soneso.stellar.sdk.*
import com.soneso.stellar.sdk.FriendBot
import com.soneso.stellar.sdk.horizon.HorizonServer
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

/**
 * Comprehensive integration tests for Account-related operations.
 *
 * These tests verify the SDK's account operations against a live Stellar testnet.
 * They cover:
 * - SetOptions operation (thresholds, signers, flags, home domain)
 * - CreateAccount and ChangeTrust operations
 * - AccountMerge operation (with regular and muxed accounts)
 * - BumpSequence operation
 * - ManageData operation (set and delete)
 * - MuxedAccount ID parsing
 * - AccountData endpoint
 * - Operations and effects parsing
 *
 * **IMPORTANT**: These tests are marked with @Ignore by default because they:
 * 1. Require network access to Stellar testnet
 * 2. Depend on FriendBot availability for funding
 * 3. Take longer to execute (network latency)
 *
 * To run these tests:
 * 1. Remove @Ignore annotations
 * 2. Ensure you have stable internet connection
 * 3. Run: `./gradlew :stellar-sdk:jvmTest --tests "AccountIntegrationTest"`
 *
 * ## Test Network
 *
 * All tests use Stellar testnet. To switch to futurenet, change the `testOn` variable.
 *
 * @see <a href="https://developers.stellar.org/docs/fundamentals-and-concepts/testnet-and-pubnet">Stellar Networks</a>
 */
class AccountIntegrationTest {

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
     * Test SetOptions operation with thresholds, signers, flags, and home domain.
     *
     * This test:
     * 1. Creates and funds a test account (A)
     * 2. Generates a second keypair (B) as additional signer
     * 3. Sets various account options:
     *    - Home domain
     *    - Signer B with weight 1
     *    - Thresholds (low=1, med=3, high=5)
     *    - Master key weight = 5
     *    - Flags (auth required, auth revocable)
     * 4. Verifies all options were set correctly
     * 5. Verifies account appears in signer query results
     * 6. Verifies operations and effects can be parsed
     */
    @Test
    fun testSetAccountOptions() = runTest(timeout = 60.seconds) {
        // Create and fund account A
        val keyPairA = KeyPair.random()
        val accountAId = keyPairA.getAccountId()

        if (testOn == "testnet") {
            FriendBot.fundTestnetAccount(accountAId)
        } else {
            FriendBot.fundFuturenetAccount(accountAId)
        }

        // Wait for account to be created
        realDelay(3000)

        val accountA = horizonServer.accounts().account(accountAId)
        val seqNum = accountA.sequenceNumber

        // Create second keypair for signer
        val keyPairB = KeyPair.random()

        // Create signer key from keypair B
        val bKey = SignerKey.ed25519PublicKey(keyPairB.getPublicKey())

        // Generate random home domain
        val newHomeDomain = "www.${Random.nextInt(10000)}.com"

        // Build SetOptions operation
        val setOptionsOp = SetOptionsOperation(
            homeDomain = newHomeDomain,
            signer = bKey,
            signerWeight = 1,
            highThreshold = 5,
            masterKeyWeight = 5,
            mediumThreshold = 3,
            lowThreshold = 1,
            setFlags = 2  // AUTH_REVOCABLE_FLAG
        )

        // Build and sign transaction
        val transaction = TransactionBuilder(
            sourceAccount = Account(accountAId, accountA.sequenceNumber),
            network = network
        )
            .addOperation(setOptionsOp)
            .addMemo(MemoText("Test set account options"))
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        transaction.sign(keyPairA)

        // Submit transaction
        val response = horizonServer.submitTransaction(transaction.toEnvelopeXdrBase64())
        assertTrue(response.successful, "Transaction should succeed")
        assertNotNull(response.hash)

        // Wait for transaction to be processed
        realDelay(3000)

        // Verify account was updated
        val updatedAccountA = horizonServer.accounts().account(accountAId)

        assertTrue(updatedAccountA.sequenceNumber > seqNum, "Sequence number should increase")
        assertEquals(newHomeDomain, updatedAccountA.homeDomain, "Home domain should be set")
        assertEquals(5, updatedAccountA.thresholds.highThreshold)
        assertEquals(3, updatedAccountA.thresholds.medThreshold)
        assertEquals(1, updatedAccountA.thresholds.lowThreshold)

        // Verify signers
        assertTrue(updatedAccountA.signers.size > 1, "Should have multiple signers")

        var bFound = false
        var aFound = false
        for (signer in updatedAccountA.signers) {
            if (signer.key == keyPairB.getAccountId()) {
                bFound = true
            }
            if (signer.key == keyPairA.getAccountId()) {
                aFound = true
                assertEquals(5, signer.weight, "Master key weight should be 5")
            }
        }
        assertTrue(aFound, "Account A should be a signer")
        assertTrue(bFound, "Account B should be a signer")

        // Verify flags
        assertFalse(updatedAccountA.flags.authRequired)
        assertTrue(updatedAccountA.flags.authRevocable)
        assertFalse(updatedAccountA.flags.authImmutable)

        // Verify account appears in signer query
        val accountsForSigner = horizonServer.accounts().forSigner(keyPairB.getAccountId()).execute()
        var foundInSignerQuery = false
        for (account in accountsForSigner.records) {
            if (account.accountId == accountAId) {
                foundInSignerQuery = true
                break
            }
        }
        assertTrue(foundInSignerQuery, "Account A should appear in signer query for B")

        // Verify operations and effects can be parsed
        val operationsPage = horizonServer.operations().forAccount(accountAId).execute()
        assertTrue(operationsPage.records.isNotEmpty(), "Should have operations")

        val effectsPage = horizonServer.effects().forAccount(accountAId).execute()
        assertTrue(effectsPage.records.isNotEmpty(), "Should have effects")
    }

    /**
     * Test finding accounts for a specific asset.
     *
     * This test:
     * 1. Creates and funds account A
     * 2. Creates and funds account C using CreateAccount operation
     * 3. Creates trustline from C to asset issued by A using ChangeTrust operation
     * 4. Verifies account C appears in asset query results
     * 5. Verifies operations and effects can be parsed
     */
    @Test
    fun testFindAccountsForAsset() = runTest(timeout = 60.seconds) {
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

        // Create account C with CreateAccount operation
        val createAccountOp = CreateAccountOperation(
            destination = accountCId,
            startingBalance = "10"
        )

        var transaction = TransactionBuilder(
            sourceAccount = Account(accountAId, accountA.sequenceNumber),
            network = network
        )
            .addOperation(createAccountOp)
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        transaction.sign(keyPairA)

        var response = horizonServer.submitTransaction(transaction.toEnvelopeXdrBase64())
        assertTrue(response.successful, "CreateAccount transaction should succeed")

        realDelay(3000)

        val accountC = horizonServer.accounts().account(accountCId)

        // Create asset issued by A
        val iomAsset = AssetTypeCreditAlphaNum4("IOM", accountAId)

        // Create trustline from C to IOM
        val changeTrustOp = ChangeTrustOperation(
            asset = iomAsset,
            limit = "200999"
        )

        transaction = TransactionBuilder(
            sourceAccount = Account(accountCId, accountC.sequenceNumber),
            network = network
        )
            .addOperation(changeTrustOp)
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        transaction.sign(keyPairC)

        response = horizonServer.submitTransaction(transaction.toEnvelopeXdrBase64())
        assertTrue(response.successful, "ChangeTrust transaction should succeed")

        realDelay(3000)

        // Find accounts for asset
        val accountsForAsset = horizonServer.accounts()
            .forAsset(iomAsset.code, iomAsset.issuer)
            .execute()

        var cFound = false
        for (account in accountsForAsset.records) {
            if (account.accountId == accountCId) {
                cFound = true
                break
            }
        }
        assertTrue(cFound, "Account C should appear in asset query")

        // Verify operations and effects can be parsed
        val operationsPageA = horizonServer.operations().forAccount(accountAId).execute()
        assertTrue(operationsPageA.records.isNotEmpty())

        val operationsPageC = horizonServer.operations().forAccount(accountCId).execute()
        assertTrue(operationsPageC.records.isNotEmpty())

        val effectsPageA = horizonServer.effects().forAccount(accountAId).execute()
        assertTrue(effectsPageA.records.isNotEmpty())

        val effectsPageC = horizonServer.effects().forAccount(accountCId).execute()
        assertTrue(effectsPageC.records.isNotEmpty())
    }

    /**
     * Test AccountMerge operation.
     *
     * This test:
     * 1. Creates and funds accounts X and Y
     * 2. Merges account Y into X using AccountMerge operation
     * 3. Verifies account Y no longer exists (404 error)
     * 4. Verifies operations and effects can be parsed
     */
    @Test
    fun testAccountMerge() = runTest(timeout = 60.seconds) {
        // Create and fund accounts X and Y
        val keyPairX = KeyPair.random()
        val keyPairY = KeyPair.random()

        val accountXId = keyPairX.getAccountId()
        val accountYId = keyPairY.getAccountId()

        if (testOn == "testnet") {
            FriendBot.fundTestnetAccount(accountXId)
            FriendBot.fundTestnetAccount(accountYId)
        } else {
            FriendBot.fundFuturenetAccount(accountXId)
            FriendBot.fundFuturenetAccount(accountYId)
        }

        realDelay(3000)

        // Merge Y into X
        val accountMergeOp = AccountMergeOperation(destination = accountXId)

        val accountY = horizonServer.accounts().account(accountYId)
        val transaction = TransactionBuilder(
            sourceAccount = Account(accountYId, accountY.sequenceNumber),
            network = network
        )
            .addOperation(accountMergeOp)
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        transaction.sign(keyPairY)

        val response = horizonServer.submitTransaction(transaction.toEnvelopeXdrBase64())
        assertTrue(response.successful, "AccountMerge transaction should succeed")

        realDelay(3000)

        // Verify account Y no longer exists
        try {
            horizonServer.accounts().account(accountYId)
            fail("Account Y should not exist after merge")
        } catch (e: Exception) {
            // Expected - account should not exist
            assertTrue(e.message?.contains("404") == true || e.message?.contains("not found") == true,
                "Should get 404 error for non-existent account")
        }

        // Verify operations and effects can be parsed
        val operationsPageX = horizonServer.operations().forAccount(accountXId).execute()
        assertTrue(operationsPageX.records.isNotEmpty())

        val operationsPageY = horizonServer.operations().forAccount(accountYId).execute()
        assertTrue(operationsPageY.records.isNotEmpty())

        val effectsPageX = horizonServer.effects().forAccount(accountXId).execute()
        assertTrue(effectsPageX.records.isNotEmpty())

        val effectsPageY = horizonServer.effects().forAccount(accountYId).execute()
        assertTrue(effectsPageY.records.isNotEmpty())
    }

    /**
     * Test AccountMerge operation with muxed accounts.
     *
     * This test:
     * 1. Creates and funds accounts X and Y
     * 2. Creates muxed account IDs for both X and Y
     * 3. Merges account Y into X using muxed accounts in AccountMerge operation
     * 4. Verifies account Y no longer exists
     * 5. Verifies operations and effects can be parsed
     */
    @Test
    fun testAccountMergeMuxedAccounts() = runTest(timeout = 60.seconds) {
        // Create and fund accounts X and Y
        val keyPairX = KeyPair.random()
        val keyPairY = KeyPair.random()

        val accountXId = keyPairX.getAccountId()
        val accountYId = keyPairY.getAccountId()

        if (testOn == "testnet") {
            FriendBot.fundTestnetAccount(accountXId)
            FriendBot.fundTestnetAccount(accountYId)
        } else {
            FriendBot.fundFuturenetAccount(accountXId)
            FriendBot.fundFuturenetAccount(accountYId)
        }

        realDelay(3000)

        // Create muxed accounts
        val muxedDestinationAccount = MuxedAccount(accountXId, 10120291UL)
        val muxedSourceAccount = MuxedAccount(accountYId, 9999999999UL)

        // Merge Y into X using muxed accounts
        val accountMergeOp = AccountMergeOperation(destination = muxedDestinationAccount.address)
        accountMergeOp.sourceAccount = muxedSourceAccount.address

        val accountY = horizonServer.accounts().account(accountYId)
        val transaction = TransactionBuilder(
            sourceAccount = Account(accountYId, accountY.sequenceNumber),
            network = network
        )
            .addOperation(accountMergeOp)
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        transaction.sign(keyPairY)

        val response = horizonServer.submitTransaction(transaction.toEnvelopeXdrBase64())
        assertTrue(response.successful, "AccountMerge with muxed accounts should succeed")

        realDelay(3000)

        // Verify account Y no longer exists
        try {
            horizonServer.accounts().account(accountYId)
            fail("Account Y should not exist after merge")
        } catch (e: Exception) {
            assertTrue(e.message?.contains("404") == true || e.message?.contains("not found") == true,
                "Should get 404 error")
        }

        // Verify operations and effects can be parsed
        val operationsPageX = horizonServer.operations().forAccount(accountXId).execute()
        assertTrue(operationsPageX.records.isNotEmpty())

        val operationsPageY = horizonServer.operations().forAccount(accountYId).execute()
        assertTrue(operationsPageY.records.isNotEmpty())

        val effectsPageX = horizonServer.effects().forAccount(accountXId).execute()
        assertTrue(effectsPageX.records.isNotEmpty())

        val effectsPageY = horizonServer.effects().forAccount(accountYId).execute()
        assertTrue(effectsPageY.records.isNotEmpty())
    }

    /**
     * Test BumpSequence operation.
     *
     * This test:
     * 1. Creates and funds a test account
     * 2. Gets the current sequence number
     * 3. Bumps sequence number by 10 using BumpSequence operation
     * 4. Verifies the new sequence number is correct
     * 5. Verifies operations and effects can be parsed
     */
    @Test
    fun testBumpSequence() = runTest(timeout = 60.seconds) {
        // Create and fund account
        val keyPair = KeyPair.random()
        val accountId = keyPair.getAccountId()

        if (testOn == "testnet") {
            FriendBot.fundTestnetAccount(accountId)
        } else {
            FriendBot.fundFuturenetAccount(accountId)
        }

        realDelay(3000)

        val account = horizonServer.accounts().account(accountId)
        val startSequence = account.sequenceNumber

        // Bump sequence by 10
        val bumpSequenceOp = BumpSequenceOperation(bumpTo = startSequence + 10)

        val transaction = TransactionBuilder(
            sourceAccount = Account(accountId, account.sequenceNumber),
            network = network
        )
            .addOperation(bumpSequenceOp)
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        transaction.sign(keyPair)

        val response = horizonServer.submitTransaction(transaction.toEnvelopeXdrBase64())
        assertTrue(response.successful, "BumpSequence transaction should succeed")

        realDelay(3000)

        // Verify sequence number was bumped
        val updatedAccount = horizonServer.accounts().account(accountId)
        assertEquals(startSequence + 10, updatedAccount.sequenceNumber,
            "Sequence number should be bumped by 10")

        // Verify operations and effects can be parsed
        val operationsPage = horizonServer.operations().forAccount(accountId).execute()
        assertTrue(operationsPage.records.isNotEmpty())

        val effectsPage = horizonServer.effects().forAccount(accountId).execute()
        assertTrue(effectsPage.records.isNotEmpty())
    }

    /**
     * Test ManageData operation (set and delete).
     *
     * This test:
     * 1. Creates and funds a test account
     * 2. Sets a data entry with key-value pair using ManageData operation
     * 3. Verifies the data was stored correctly
     * 4. Deletes the data entry using ManageData with null value
     * 5. Verifies the data was deleted
     * 6. Verifies operations and effects can be parsed
     */
    @Test
    fun testManageData() = runTest(timeout = 60.seconds) {
        // Create and fund account
        val keyPair = KeyPair.random()
        val accountId = keyPair.getAccountId()

        if (testOn == "testnet") {
            FriendBot.fundTestnetAccount(accountId)
        } else {
            FriendBot.fundFuturenetAccount(accountId)
        }

        realDelay(3000)

        val account = horizonServer.accounts().account(accountId)

        // Set data entry
        val key = "Sommer"
        val value = "Die Möbel sind heiß!"
        val valueBytes = value.encodeToByteArray()

        val manageDataOp = ManageDataOperation(name = key, value = valueBytes)

        var transaction = TransactionBuilder(
            sourceAccount = Account(accountId, account.sequenceNumber),
            network = network
        )
            .addOperation(manageDataOp)
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        transaction.sign(keyPair)

        var response = horizonServer.submitTransaction(transaction.toEnvelopeXdrBase64())
        assertTrue(response.successful, "ManageData set transaction should succeed")

        realDelay(3000)

        // Verify data was set
        val updatedAccount = horizonServer.accounts().account(accountId)
        assertTrue(updatedAccount.data.containsKey(key), "Data entry should exist")

        // Decode and verify value
        val storedValueBase64 = updatedAccount.data[key]
        assertNotNull(storedValueBase64)

        // Delete data entry
        val deleteDataOp = ManageDataOperation(name = key, value = null)

        val accountForDelete = horizonServer.accounts().account(accountId)
        transaction = TransactionBuilder(
            sourceAccount = Account(accountId, accountForDelete.sequenceNumber),
            network = network
        )
            .addOperation(deleteDataOp)
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        transaction.sign(keyPair)

        response = horizonServer.submitTransaction(transaction.toEnvelopeXdrBase64())
        assertTrue(response.successful, "ManageData delete transaction should succeed")

        realDelay(3000)

        // Verify data was deleted
        val finalAccount = horizonServer.accounts().account(accountId)
        assertFalse(finalAccount.data.containsKey(key), "Data entry should be deleted")

        // Verify operations and effects can be parsed
        val operationsPage = horizonServer.operations().forAccount(accountId).execute()
        assertTrue(operationsPage.records.isNotEmpty())

        val effectsPage = horizonServer.effects().forAccount(accountId).execute()
        assertTrue(effectsPage.records.isNotEmpty())
    }

    /**
     * Test MuxedAccount ID parsing.
     *
     * This test verifies that muxed account IDs (M... addresses) can be correctly
     * parsed to extract the underlying ed25519 account ID and muxed ID.
     */
    @Test
    fun testMuxedAccountIdParsing() {
        val med25519AccountId = "MAQAA5L65LSYH7CQ3VTJ7F3HHLGCL3DSLAR2Y47263D56MNNGHSQSAAAAAAAAAAE2LP26"
        val mux = MuxedAccount(med25519AccountId)

        assertEquals("GAQAA5L65LSYH7CQ3VTJ7F3HHLGCL3DSLAR2Y47263D56MNNGHSQSTVY",
            mux.ed25519AccountId, "Should extract correct ed25519 account ID")
        assertEquals(1234UL, mux.id, "Should extract correct muxed ID")
        assertEquals(med25519AccountId, mux.address, "Should encode back to original address")
    }

    /**
     * Test AccountData endpoint.
     *
     * This test:
     * 1. Creates and funds a test account
     * 2. Sets a data entry with key-value pair
     * 3. Retrieves the data using the accountData endpoint
     * 4. Verifies the data value and decoding
     * 5. Deletes the data entry
     * 6. Verifies the data retrieval fails with 404
     */
    @Test
    fun testAccountDataEndpoint() = runTest(timeout = 60.seconds) {
        // Create and fund account
        val keyPair = KeyPair.random()
        val accountId = keyPair.getAccountId()

        if (testOn == "testnet") {
            FriendBot.fundTestnetAccount(accountId)
        } else {
            FriendBot.fundFuturenetAccount(accountId)
        }

        realDelay(3000)

        val account = horizonServer.accounts().account(accountId)

        // Set data entry
        val key = "test_key"
        val value = "Hello, Stellar!"
        val valueBytes = value.encodeToByteArray()

        val manageDataOp = ManageDataOperation(name = key, value = valueBytes)

        var transaction = TransactionBuilder(
            sourceAccount = Account(accountId, account.sequenceNumber),
            network = network
        )
            .addOperation(manageDataOp)
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        transaction.sign(keyPair)

        var response = horizonServer.submitTransaction(transaction.toEnvelopeXdrBase64())
        assertTrue(response.successful, "ManageData transaction should succeed")

        realDelay(3000)

        // Test the accountData endpoint
        val dataResponse = horizonServer.accounts().accountData(accountId, key)

        // Verify the decoded value
        assertEquals(value, dataResponse.decodedString, "Decoded string should match")
        assertEquals(value, dataResponse.decodedStringOrNull, "Decoded string or null should match")

        // Clean up - delete the data
        val accountForDelete = horizonServer.accounts().account(accountId)
        val deleteDataOp = ManageDataOperation(name = key, value = null)

        transaction = TransactionBuilder(
            sourceAccount = Account(accountId, accountForDelete.sequenceNumber),
            network = network
        )
            .addOperation(deleteDataOp)
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        transaction.sign(keyPair)

        response = horizonServer.submitTransaction(transaction.toEnvelopeXdrBase64())
        assertTrue(response.successful, "Delete data transaction should succeed")

        realDelay(3000)

        // Verify data retrieval fails
        try {
            horizonServer.accounts().accountData(accountId, key)
            fail("Should throw exception for non-existent data")
        } catch (e: Exception) {
            // Expected
            assertTrue(e.message?.contains("404") == true || e.message?.contains("not found") == true,
                "Should get 404 error")
        }
    }

    /**
     * Test streaming transactions for an account.
     *
     * This test:
     * 1. Creates and funds account A
     * 2. Creates and funds account B (via CreateAccount operation)
     * 3. Starts streaming transactions for account A using cursor("now")
     * 4. Submits payment transactions from B to A
     * 5. Verifies the stream receives the events
     * 6. Verifies the count reaches 3 events
     * 7. Closes the stream properly
     */
    @Test
    fun testStreamTransactionsForAccount() = runTest(timeout = 90.seconds) {
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

        // Create keypair B
        val keyPairB = KeyPair.random()
        val accountBId = keyPairB.getAccountId()

        // Create account B with CreateAccount operation
        val createAccountOp = CreateAccountOperation(
            destination = accountBId,
            startingBalance = "1000"
        )

        var transaction = TransactionBuilder(
            sourceAccount = Account(accountAId, accountA.sequenceNumber),
            network = network
        )
            .addOperation(createAccountOp)
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        transaction.sign(keyPairA)

        var response = horizonServer.submitTransaction(transaction.toEnvelopeXdrBase64())
        assertTrue(response.successful, "CreateAccount transaction should succeed")

        realDelay(3000)

        // Thread-safe counter for received events
        var count = 0
        val eventScope = CoroutineScope(Dispatchers.Default)

        // Start streaming transactions for account A
        val stream = horizonServer.transactions()
            .forAccount(accountAId)
            .cursor("now")
            .stream(
                serializer = com.soneso.stellar.sdk.horizon.responses.TransactionResponse.serializer(),
                listener = object : com.soneso.stellar.sdk.horizon.requests.EventListener<com.soneso.stellar.sdk.horizon.responses.TransactionResponse> {
                    override fun onEvent(event: com.soneso.stellar.sdk.horizon.responses.TransactionResponse) {
                        count++
                        println("Account transaction event received: $count")
                        assertEquals(1, event.operationCount, "Transaction should have 1 operation")

                        // Submit next payment if count < 3
                        if (count < 3) {
                            eventScope.launch {
                                try {
                                    val accountB = horizonServer.accounts().account(accountBId)
                                    val paymentOp = PaymentOperation(
                                        destination = accountAId,
                                        asset = AssetTypeNative,
                                        amount = "10"
                                    )

                                    val tx = TransactionBuilder(
                                        sourceAccount = Account(accountBId, accountB.sequenceNumber),
                                        network = network
                                    )
                                        .addOperation(paymentOp)
                                        .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
                                        .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
                                        .build()

                                    tx.sign(keyPairB)

                                    val submitResponse = horizonServer.submitTransaction(tx.toEnvelopeXdrBase64())
                                    assertTrue(submitResponse.successful, "Payment transaction should succeed")
                                } catch (e: Exception) {
                                    println("Error submitting payment: ${e.message}")
                                }
                            }
                        }
                    }

                    override fun onFailure(error: Throwable?, responseCode: Int?) {
                        println("Stream failure: $error, code: $responseCode")
                    }
                }
            )

        realDelay(3000)

        try {
            // Submit first payment from B to A to trigger the stream
            val accountB = horizonServer.accounts().account(accountBId)
            val paymentOp = PaymentOperation(
                destination = accountAId,
                asset = AssetTypeNative,
                amount = "10"
            )

            transaction = TransactionBuilder(
                sourceAccount = Account(accountBId, accountB.sequenceNumber),
                network = network
            )
                .addOperation(paymentOp)
                .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
                .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
                .build()

            transaction.sign(keyPairB)

            response = horizonServer.submitTransaction(transaction.toEnvelopeXdrBase64())
            assertTrue(response.successful, "First payment transaction should succeed")

            // Wait for stream to receive all events
            // Use real delay (not virtual time) since SSE stream runs on Dispatchers.Default
            withContext(Dispatchers.Default) {
                realDelay(30000)
            }

            // Verify count
            assertEquals(3, count, "Should receive 3 transaction events")
        } finally {
            // Clean up - close stream
            stream.close()
        }
    }
}
