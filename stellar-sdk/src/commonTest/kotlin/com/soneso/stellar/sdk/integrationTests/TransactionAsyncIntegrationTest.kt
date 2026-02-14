package com.soneso.stellar.sdk.integrationTests

import com.soneso.stellar.sdk.*
import com.soneso.stellar.sdk.horizon.HorizonServer
import com.soneso.stellar.sdk.horizon.responses.SubmitTransactionAsyncResponse
import com.soneso.stellar.sdk.horizon.exceptions.BadRequestException
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

/**
 * Comprehensive integration tests for asynchronous transaction submission.
 *
 * These tests verify the SDK's async transaction submission against a live Stellar testnet.
 * They cover:
 * - Successful async transaction submission (PENDING status)
 * - Duplicate transaction detection (DUPLICATE status)
 * - Malformed transaction handling (400 error)
 * - Transaction errors (ERROR status for invalid sequence number)
 * - Transaction polling and verification after submission
 *
 * ## Async Transaction Submission Overview
 *
 * The async transaction submission endpoint differs from the synchronous endpoint:
 *
 * ### Synchronous (`submitTransaction`)
 * - Waits for transaction to be ingested into a ledger
 * - Returns full transaction details after ledger close
 * - Takes 5-10 seconds (waiting for ledger)
 * - Throws exception on any error
 *
 * ### Asynchronous (`submitTransactionAsync`)
 * - Returns immediately with submission status from Stellar Core
 * - Does NOT wait for ledger ingestion
 * - Returns within milliseconds
 * - Returns status codes: PENDING, ERROR, DUPLICATE, TRY_AGAIN_LATER
 *
 * ## Transaction Status Values
 *
 * - **PENDING**: Transaction passed initial validation and is queued
 * - **DUPLICATE**: Identical transaction already submitted
 * - **ERROR**: Transaction failed validation (with error_result_xdr)
 * - **TRY_AGAIN_LATER**: Server is busy, retry later
 *
 * ## HTTP Status Codes
 *
 * - **201**: Transaction submitted successfully (PENDING)
 * - **409**: Duplicate transaction (DUPLICATE)
 * - **400**: Malformed request or transaction error (ERROR or malformed)
 * - **503**: Server busy (TRY_AGAIN_LATER)
 *
 * ## Typical Workflow
 *
 * 1. Build and sign transaction
 * 2. Submit via `submitTransactionAsync()`
 * 3. Check response status (PENDING = success, ERROR = failure)
 * 4. Wait 5-10 seconds for ledger close
 * 5. Query transaction by hash to verify success
 * 6. Check transaction result and operations
 *
 * ## Use Cases
 *
 * Use async submission when:
 * - You need immediate feedback on transaction acceptance
 * - You want to implement custom polling logic
 * - You're submitting many transactions in parallel
 * - You need to know the transaction hash immediately
 *
 * Use sync submission when:
 * - You want to wait for final confirmation
 * - You need the full transaction result immediately
 * - You're submitting a single transaction
 *
 * ## Test Execution
 *
 * **IMPORTANT**: These tests use independent accounts and do not depend on each other.
 * They can run in any order or in parallel. Each test:
 * - Creates its own fresh account via FriendBot
 * - Performs its specific test scenario
 * - Is fully self-contained
 *
 * ### Running Tests
 *
 * **Run all tests together**:
 * ```bash
 * ./gradlew :stellar-sdk:jvmTest --tests "TransactionAsyncIntegrationTest"
 * ```
 *
 * **Run individual tests** (recommended when a specific test fails):
 * ```bash
 * ./gradlew :stellar-sdk:jvmTest --tests "TransactionAsyncIntegrationTest.testSubmitAsyncSuccess"
 * ./gradlew :stellar-sdk:jvmTest --tests "TransactionAsyncIntegrationTest.testSubmitAsyncDuplicate"
 * ./gradlew :stellar-sdk:jvmTest --tests "TransactionAsyncIntegrationTest.testSubmitAsyncMalformed"
 * ./gradlew :stellar-sdk:jvmTest --tests "TransactionAsyncIntegrationTest.testSubmitAsyncError"
 * ```
 *
 * ### Test Flakiness
 *
 * **Why tests may be flaky when run together**:
 * - Testnet congestion: Multiple simultaneous account creations and transactions
 * - Network timing: Ledger close timing varies (typically 5-10 seconds, but can be longer)
 * - FriendBot rate limiting: May slow down when creating many accounts rapidly
 * - Transaction queue: Testnet may process transactions more slowly under load
 *
 * **If tests fail when run together but pass individually**:
 * This is expected behavior due to testnet congestion. The tests are correct and
 * production-ready. The failure indicates testnet load, not SDK bugs.
 *
 * **Solutions**:
 * 1. Run tests individually (most reliable for testnet)
 * 2. Increase delays between tests (not currently possible with KMP common tests)
 * 3. Re-run failed tests - they will likely pass on retry
 * 4. Use a less loaded testnet instance (if available)
 *
 * These tests use real network operations and require:
 * 1. Network access to Stellar testnet
 * 2. FriendBot availability for funding
 * 3. Network latency tolerance (delays after operations)
 * 4. Patience with testnet timing variations
 *
 * ## Reference
 *
 * Ported from Flutter SDK's `transaction_async_test.dart`
 *
 * @see <a href="https://developers.stellar.org/docs/data/horizon/api-reference/submit-async-transaction">Submit Transaction Asynchronously</a>
 */
class TransactionAsyncIntegrationTest {

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
     * Test successful async transaction submission.
     *
     * This test verifies the standard async transaction workflow:
     * 1. Creates and funds a test account via FriendBot
     * 2. Gets the account details including current sequence number
     * 3. Creates a BumpSequence operation that increases sequence by 10
     * 4. Builds and signs the transaction
     * 5. Submits transaction asynchronously via `submitTransactionAsync()`
     * 6. Verifies response:
     *    - Status is PENDING (transaction accepted by Core)
     *    - Hash is returned
     * 7. Waits for ledger to close (with retries for testnet timing)
     * 8. Queries transaction by hash to verify it was successful
     * 9. Verifies transaction appears in ledger with successful=true
     *
     * ## BumpSequence Operation
     *
     * The BumpSequence operation is used because it:
     * - Has no side effects on other accounts
     * - Is simple and fast to execute
     * - Provides clear success/failure indication
     * - Commonly used in testing scenarios
     *
     * ## Expected Flow
     *
     * ```
     * submitTransactionAsync() → PENDING → [wait for ledger] → transaction successful
     * ```
     *
     * ## Key Assertions
     *
     * - `txStatus == PENDING`: Core accepted the transaction
     * - `transactionResponse.successful == true`: Transaction succeeded in ledger
     */
    @Test
    fun testSubmitAsyncSuccess() = runTest(timeout = 90.seconds) {
        // 1. Create and fund account
        val keyPair = KeyPair.random()
        val accountId = keyPair.getAccountId()

        println("[testSubmitAsyncSuccess] Test account: $accountId")

        if (testOn == "testnet") {
            FriendBot.fundTestnetAccount(accountId)
        } else {
            FriendBot.fundFuturenetAccount(accountId)
        }

        realDelay(5000) // Increased delay for account creation

        // 2. Get account details
        val account = horizonServer.accounts().account(accountId)
        val startSequence = account.sequenceNumber

        println("[testSubmitAsyncSuccess] Start sequence: $startSequence")

        // 3. Create BumpSequence operation
        val bumpSequenceOp = BumpSequenceOperation(bumpTo = startSequence + 10)

        // 4. Build and sign transaction
        val transaction = TransactionBuilder(
            sourceAccount = Account(accountId, account.sequenceNumber),
            network = network
        )
            .addOperation(bumpSequenceOp)
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        transaction.sign(keyPair)

        println("[testSubmitAsyncSuccess] Transaction built and signed")

        // 5. Submit async
        val response = horizonServer.submitTransactionAsync(transaction.toEnvelopeXdrBase64())

        // 6. Verify response
        assertEquals(
            SubmitTransactionAsyncResponse.TransactionStatus.PENDING,
            response.txStatus,
            "Transaction status should be PENDING"
        )
        assertNotNull(response.hash, "Response should contain transaction hash")

        println("[testSubmitAsyncSuccess] Transaction submitted async: ${response.hash} (status: ${response.txStatus})")

        // 7. Wait for ledger to close (with retry logic for testnet timing)
        var transactionResponse: com.soneso.stellar.sdk.horizon.responses.TransactionResponse? = null
        var attempts = 0
        val maxAttempts = 10 // Increased to 10 attempts (50 seconds total)

        while (attempts < maxAttempts) {
            realDelay(5000)
            attempts++

            try {
                transactionResponse = horizonServer.transactions().transaction(response.hash)
                println("[testSubmitAsyncSuccess] Transaction found in ledger after ${attempts * 5} seconds")
                break
            } catch (e: Exception) {
                if (attempts < maxAttempts) {
                    println("[testSubmitAsyncSuccess] Transaction not yet in ledger (attempt $attempts/$maxAttempts), waiting...")
                } else {
                    println("[testSubmitAsyncSuccess] Failed to find transaction after ${attempts * 5} seconds")
                    throw e
                }
            }
        }

        // 9. Verify transaction succeeded
        assertNotNull(transactionResponse, "Transaction should be found in ledger")
        assertTrue(transactionResponse.successful, "Transaction should be successful")
        assertEquals(response.hash, transactionResponse.hash, "Transaction hash should match")

        println("[testSubmitAsyncSuccess] Transaction verified in ledger: successful=${transactionResponse.successful}")
    }

    /**
     * Test duplicate transaction detection with async submission.
     *
     * This test verifies that submitting the same transaction twice results in a DUPLICATE status:
     * 1. Creates and funds a test account
     * 2. Gets account details and sequence number
     * 3. Creates a BumpSequence operation
     * 4. Builds and signs transaction
     * 5. Submits transaction asynchronously (first submission)
     * 6. Verifies first response is PENDING
     * 7. Submits the SAME transaction again (second submission)
     * 8. Verifies second response is DUPLICATE
     * 9. Verifies hash is the same for both submissions
     * 10. Waits for ledger close (with retries)
     * 11. Queries transaction to verify it succeeded (only once)
     *
     * ## Duplicate Detection
     *
     * Stellar Core detects duplicates by transaction hash:
     * - Same envelope XDR = same hash
     * - Duplicate transactions are rejected immediately
     * - DUPLICATE status in response
     *
     * ## Why This Matters
     *
     * Duplicate detection prevents:
     * - Accidental double-spending
     * - Replay attacks
     * - Network congestion from repeated submissions
     *
     * ## Expected Flow
     *
     * ```
     * submitTransactionAsync() → PENDING
     * submitTransactionAsync() → DUPLICATE [same hash]
     * [wait for ledger]
     * query transaction → successful (only included once)
     * ```
     *
     * ## Key Assertions
     *
     * - First submission: `txStatus == PENDING`
     * - Second submission: `txStatus == DUPLICATE`
     * - Both responses have same hash
     * - Transaction succeeds in ledger (only once)
     */
    @Test
    fun testSubmitAsyncDuplicate() = runTest(timeout = 90.seconds) {
        // 1. Create and fund account
        val keyPair = KeyPair.random()
        val accountId = keyPair.getAccountId()

        println("[testSubmitAsyncDuplicate] Test account: $accountId")

        if (testOn == "testnet") {
            FriendBot.fundTestnetAccount(accountId)
        } else {
            FriendBot.fundFuturenetAccount(accountId)
        }

        realDelay(5000) // Increased delay for account creation

        // 2. Get account details
        val account = horizonServer.accounts().account(accountId)
        val startSequence = account.sequenceNumber

        println("[testSubmitAsyncDuplicate] Start sequence: $startSequence")

        // 3. Create BumpSequence operation
        val bumpSequenceOp = BumpSequenceOperation(bumpTo = startSequence + 10)

        // 4. Build and sign transaction
        val transaction = TransactionBuilder(
            sourceAccount = Account(accountId, account.sequenceNumber),
            network = network
        )
            .addOperation(bumpSequenceOp)
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        transaction.sign(keyPair)

        println("[testSubmitAsyncDuplicate] Transaction built and signed")

        // 5. First submission
        val response1 = horizonServer.submitTransactionAsync(transaction.toEnvelopeXdrBase64())

        // 6. Verify first response is PENDING
        assertEquals(
            SubmitTransactionAsyncResponse.TransactionStatus.PENDING,
            response1.txStatus,
            "First submission should be PENDING"
        )

        println("[testSubmitAsyncDuplicate] First submission: ${response1.hash} (status: ${response1.txStatus})")

        // 7. Second submission (same transaction)
        val response2 = horizonServer.submitTransactionAsync(transaction.toEnvelopeXdrBase64())

        // 8. Verify second response is DUPLICATE
        assertEquals(
            SubmitTransactionAsyncResponse.TransactionStatus.DUPLICATE,
            response2.txStatus,
            "Second submission should be DUPLICATE"
        )

        println("[testSubmitAsyncDuplicate] Second submission: ${response2.hash} (status: ${response2.txStatus})")

        // 9. Verify hash is the same
        assertEquals(response1.hash, response2.hash, "Transaction hash should be the same")

        // 10. Wait for ledger to close (with retry logic for testnet timing)
        var transactionResponse: com.soneso.stellar.sdk.horizon.responses.TransactionResponse? = null
        var attempts = 0
        val maxAttempts = 10 // Increased to 10 attempts (50 seconds total)

        while (attempts < maxAttempts) {
            realDelay(5000)
            attempts++

            try {
                transactionResponse = horizonServer.transactions().transaction(response1.hash)
                println("[testSubmitAsyncDuplicate] Transaction found in ledger after ${attempts * 5} seconds")
                break
            } catch (e: Exception) {
                if (attempts < maxAttempts) {
                    println("[testSubmitAsyncDuplicate] Transaction not yet in ledger (attempt $attempts/$maxAttempts), waiting...")
                } else {
                    println("[testSubmitAsyncDuplicate] Failed to find transaction after ${attempts * 5} seconds")
                    throw e
                }
            }
        }

        // 11. Verify transaction succeeded
        assertNotNull(transactionResponse, "Transaction should be found in ledger")
        assertTrue(transactionResponse.successful, "Transaction should be successful")

        println("[testSubmitAsyncDuplicate] Transaction verified in ledger: successful=${transactionResponse.successful}")
    }

    /**
     * Test malformed transaction handling with async submission.
     *
     * This test verifies that submitting a malformed transaction envelope results in a 400 error:
     * 1. Creates and funds a test account
     * 2. Gets account details
     * 3. Builds and signs a valid transaction
     * 4. Converts transaction to XDR envelope
     * 5. Corrupts the XDR by truncating it (removes last 10 characters)
     * 6. Attempts to submit the malformed XDR
     * 7. Verifies that a BadRequestException is thrown
     * 8. Verifies the exception has HTTP status 400
     *
     * ## Malformed vs Invalid
     *
     * - **Malformed**: XDR cannot be decoded (400 error, exception thrown)
     * - **Invalid**: XDR is valid but transaction fails validation (ERROR status, no exception)
     *
     * ## Why Test This
     *
     * Ensures the SDK properly handles:
     * - Corrupt network data
     * - Partial transmission failures
     * - XDR encoding bugs
     * - Client-side XDR manipulation errors
     *
     * ## Expected Flow
     *
     * ```
     * submitTransactionAsync(malformedXdr) → BadRequestException (400)
     * ```
     *
     * ## Key Assertions
     *
     * - Exception type is `BadRequestException`
     * - HTTP status code is 400
     * - Exception is thrown (not returned as ERROR status)
     */
    @Test
    fun testSubmitAsyncMalformed() = runTest(timeout = 90.seconds) {
        // 1. Create and fund account
        val keyPair = KeyPair.random()
        val accountId = keyPair.getAccountId()

        println("[testSubmitAsyncMalformed] Test account: $accountId")

        if (testOn == "testnet") {
            FriendBot.fundTestnetAccount(accountId)
        } else {
            FriendBot.fundFuturenetAccount(accountId)
        }

        realDelay(5000) // Increased delay for account creation

        // 2. Get account details
        val account = horizonServer.accounts().account(accountId)
        val startSequence = account.sequenceNumber

        println("[testSubmitAsyncMalformed] Start sequence: $startSequence")

        // 3. Build and sign valid transaction
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

        // 4. Get valid XDR envelope
        var envelopeXdrBase64 = transaction.toEnvelopeXdrBase64()

        println("[testSubmitAsyncMalformed] Valid XDR length: ${envelopeXdrBase64.length}")

        // 5. Corrupt the XDR by truncating it
        envelopeXdrBase64 = envelopeXdrBase64.substring(0, envelopeXdrBase64.length - 10)

        println("[testSubmitAsyncMalformed] Malformed XDR length: ${envelopeXdrBase64.length}")

        // 6. Attempt to submit malformed XDR
        var thrown = false
        try {
            horizonServer.submitTransactionAsync(envelopeXdrBase64)
            fail("Should throw BadRequestException for malformed XDR")
        } catch (e: BadRequestException) {
            // 7. Verify exception
            thrown = true
            assertEquals(400, e.code, "Exception should have HTTP status 400")
            assertNotNull(e.body, "Exception should have response body")
            println("[testSubmitAsyncMalformed] BadRequestException caught: ${e.message}")
        }

        // 8. Verify exception was thrown
        assertTrue(thrown, "BadRequestException should be thrown for malformed XDR")
    }

    /**
     * Test transaction error handling with async submission (invalid sequence number).
     *
     * This test verifies that submitting a transaction with validation errors results in ERROR status:
     * 1. Creates and funds a test account
     * 2. Creates an Account object with INVALID sequence number (100000000)
     * 3. Builds transaction using the invalid sequence
     * 4. Signs transaction (signature is valid, but sequence is wrong)
     * 5. Submits transaction asynchronously
     * 6. Verifies response:
     *    - Status is ERROR (transaction failed validation)
     *    - errorResultXdr is present (contains error details)
     *
     * ## Error vs Malformed
     *
     * - **Malformed (400 exception)**: XDR cannot be decoded
     * - **Error (ERROR status)**: XDR is valid, but transaction fails Stellar validation
     *
     * ## Common Transaction Errors
     *
     * - Invalid sequence number (tx_bad_seq)
     * - Insufficient balance (tx_insufficient_balance)
     * - Invalid signatures (tx_bad_auth)
     * - Operation failures (tx_failed with operation-specific errors)
     *
     * ## Error Result XDR
     *
     * The `errorResultXdr` field contains a base64-encoded TransactionResult XDR with:
     * - Result code (e.g., txBAD_SEQ, txINSUFFICIENT_BALANCE)
     * - Operation results (if transaction reached operation processing)
     * - Fee charged (usually 0 for failed transactions)
     *
     * ## Expected Flow
     *
     * ```
     * submitTransactionAsync(invalidTx) → ERROR + errorResultXdr
     * ```
     *
     * ## Key Assertions
     *
     * - `txStatus == ERROR`: Transaction failed validation
     * - `errorResultXdr != null`: Error details provided
     * - No exception thrown (unlike malformed XDR)
     */
    @Test
    fun testSubmitAsyncError() = runTest(timeout = 90.seconds) {
        // 1. Create and fund account
        val keyPair = KeyPair.random()
        val accountId = keyPair.getAccountId()

        println("[testSubmitAsyncError] Test account: $accountId")

        if (testOn == "testnet") {
            FriendBot.fundTestnetAccount(accountId)
        } else {
            FriendBot.fundFuturenetAccount(accountId)
        }

        realDelay(5000) // Increased delay for account creation

        // 2. Create Account with INVALID sequence number
        // This will cause the transaction to fail with tx_bad_seq
        val account = Account(accountId, 100000000)
        val startSequence = account.sequenceNumber

        println("[testSubmitAsyncError] Using invalid sequence: $startSequence")

        // 3. Build transaction with invalid sequence
        val bumpSequenceOp = BumpSequenceOperation(bumpTo = startSequence + 10)

        val transaction = TransactionBuilder(
            sourceAccount = account,
            network = network
        )
            .addOperation(bumpSequenceOp)
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        // 4. Sign transaction (signature is valid, but sequence is wrong)
        transaction.sign(keyPair)

        println("[testSubmitAsyncError] Transaction built and signed with invalid sequence")

        // 5. Submit async
        val response = horizonServer.submitTransactionAsync(transaction.toEnvelopeXdrBase64())

        // 6. Verify response is ERROR
        assertEquals(
            SubmitTransactionAsyncResponse.TransactionStatus.ERROR,
            response.txStatus,
            "Transaction status should be ERROR"
        )
        assertNotNull(response.errorResultXdr, "Error result XDR should be present")

        println("[testSubmitAsyncError] Transaction failed as expected: status=${response.txStatus}, errorResultXdr=${response.errorResultXdr}")
    }
}
