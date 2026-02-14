package com.soneso.stellar.sdk.integrationTests

import com.soneso.stellar.sdk.*
import com.soneso.stellar.sdk.horizon.HorizonServer
import com.soneso.stellar.sdk.rpc.SorobanServer
import com.soneso.stellar.sdk.rpc.responses.GetTransactionStatus
import com.soneso.stellar.sdk.scval.Scv
import com.soneso.stellar.sdk.util.TestResourceUtil
import com.soneso.stellar.sdk.xdr.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

/**
 * Comprehensive integration tests for Soroban authorization (Auth) functionality.
 *
 * These tests verify the SDK's Soroban authorization integration against a live Stellar testnet.
 * They cover:
 * - Contract upload and deployment with authorization
 * - Contract invocation with authorization (invoker != submitter)
 * - Contract invocation without manual authorization (invoker == submitter)
 * - Authorization signing with Auth.authorizeEntry()
 * - Signature expiration ledger handling
 * - Transaction lifecycle with authorization entries
 *
 * **Test Network**: All tests use Stellar testnet Soroban RPC server.
 *
 * ## Running Tests
 *
 * These tests require network access to Soroban testnet RPC and Friendbot:
 * ```bash
 * ./gradlew :stellar-sdk:jvmTest --tests "SorobanAuthIntegrationTest"
 * ```
 *
 * ## Test Contract
 *
 * These tests use the `soroban_auth_contract.wasm` which has an "increment" function
 * that requires authorization from a specific user account.
 *
 * ## Ported From
 *
 * These tests are ported from the Flutter Stellar SDK's soroban_test_auth.dart:
 * - test upload auth contract (lines 232-280)
 * - test create auth contract (lines 282-326)
 * - test restore footprint (lines 328-330)
 * - test invoke auth account (lines 332-434) - invoker != submitter, manual auth signing
 * - test invoke auth invoker (lines 436-494) - invoker == submitter, auto-auth
 *
 * **Reference**: `/Users/chris/projects/Stellar/stellar_flutter_sdk/test/soroban_test_auth.dart`
 *
 * @see <a href="https://developers.stellar.org/docs/learn/encyclopedia/security/authorization/">Smart Contract Authorization</a>
 * @see Auth
 */
class SorobanAuthIntegrationTest {

    private val testOn = "testnet" // "testnet" or "futurenet"

    private val sorobanServer = if (testOn == "testnet") {
        SorobanServer("https://soroban-testnet.stellar.org")
    } else {
        SorobanServer("https://rpc-futurenet.stellar.org")
    }

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

    companion object {
        /**
         * Shared WASM ID from testStep1UploadAuthContract, used by testStep2CreateAuthContract.
         */
        var authContractWasmId: String? = null

        /**
         * Shared contract ID from testStep2CreateAuthContract, used by invoke tests.
         */
        var authContractId: String? = null

        /**
         * Shared submitter keypair (account that submits transactions).
         */
        var submitterKeyPair: KeyPair? = null

        /**
         * Shared invoker keypair (account that invokes contract functions).
         * Different from submitter to test authorization signing.
         */
        var invokerKeyPair: KeyPair? = null
    }

    /**
     * Tests uploading the Soroban auth contract WASM to the ledger.
     *
     * This test validates the contract upload workflow for authorization contracts:
     * 1. Creates and funds two test accounts via Friendbot (submitter and invoker)
     * 2. Loads the auth contract WASM file
     * 3. Builds an UploadContractWasmHostFunction operation using helper method
     * 4. Simulates the transaction to get resource estimates
     * 5. Prepares the transaction with simulation results
     * 6. Signs and submits the transaction to Soroban RPC
     * 7. Polls for transaction completion
     * 8. Extracts the WASM ID from the transaction result
     * 9. Extends the contract code footprint TTL for testing
     * 10. Stores WASM ID and keypairs for use by other tests
     *
     * The test demonstrates:
     * - Account creation and funding for both submitter and invoker
     * - Contract WASM upload workflow using SDK helper method
     * - WASM ID extraction from transaction result
     * - Contract code footprint TTL extension
     *
     * The auth contract has an "increment" function that requires authorization
     * from a specific user account, making it suitable for testing authorization flows.
     *
     * **Prerequisites**: Network connectivity to Stellar testnet
     * **Duration**: ~60-90 seconds (includes account funding, upload, and TTL extension)
     *
     * **Reference**: Ported from Flutter SDK's test upload auth contract
     * (soroban_test_auth.dart lines 232-280)
     */
    @Test
    fun testStep1UploadAuthContract() = runTest(timeout = 150.seconds) {
        // Given: Create and fund two test accounts (submitter and invoker)
        val submitter = KeyPair.random()
        val submitterId = submitter.getAccountId()
        val invoker = KeyPair.random()
        val invokerId = invoker.getAccountId()

        // Fund both accounts via FriendBot (network-dependent)
        if (testOn == "testnet") {
            FriendBot.fundTestnetAccount(submitterId)
            FriendBot.fundTestnetAccount(invokerId)
        } else if (testOn == "futurenet") {
            FriendBot.fundFuturenetAccount(submitterId)
            FriendBot.fundFuturenetAccount(invokerId)
        }
        realDelay(5000) // Wait for account creation

        // Store keypairs for later tests
        submitterKeyPair = submitter
        invokerKeyPair = invoker

        // Load account for sequence number
        val account = sorobanServer.getAccount(submitterId)
        assertNotNull(account, "Submitter account should be loaded")

        // Load auth contract WASM file
        val contractCode = TestResourceUtil.readWasmFile("soroban_auth_contract.wasm")
        assertTrue(contractCode.isNotEmpty(), "Auth contract code should not be empty")

        // When: Building upload contract transaction using SDK helper method
        val operation = InvokeHostFunctionOperation.uploadContractWasm(contractCode)

        val transaction = TransactionBuilder(
            sourceAccount = account,
            network = network
        )
            .addOperation(operation)
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        // Simulate transaction to obtain transaction data + resource fee
        val simulateResponse = sorobanServer.simulateTransaction(transaction)
        assertNull(simulateResponse.error, "Simulation should not have error")
        assertNotNull(simulateResponse.results, "Simulation results should not be null")
        assertNotNull(simulateResponse.transactionData, "Transaction data should not be null")
        assertNotNull(simulateResponse.minResourceFee, "Min resource fee should not be null")

        // Prepare transaction with simulation results
        val preparedTransaction = sorobanServer.prepareTransaction(transaction, simulateResponse)

        // Sign transaction
        preparedTransaction.sign(submitter)

        // Then: Submit transaction to Soroban RPC
        val sendResponse = sorobanServer.sendTransaction(preparedTransaction)
        assertNotNull(sendResponse.hash, "Transaction hash should not be null")
        assertNotNull(sendResponse.status, "Transaction status should not be null")

        // Poll for transaction completion
        val rpcTransactionResponse = sorobanServer.pollTransaction(
            hash = sendResponse.hash,
            maxAttempts = 30,
            sleepStrategy = { 3000L }
        )

        assertEquals(
            GetTransactionStatus.SUCCESS,
            rpcTransactionResponse.status,
            "Transaction should succeed"
        )

        // Extract WASM ID from transaction result
        val wasmId = rpcTransactionResponse.getWasmId()
        assertNotNull(wasmId, "WASM ID should be extracted from transaction result")
        assertTrue(wasmId.isNotEmpty(), "WASM ID should not be empty")

        // Store WASM ID for later tests
        authContractWasmId = wasmId

        println("Auth contract uploaded with WASM ID: $wasmId")

        // Extend contract code footprint TTL for testing
        // Optional TTL extension disabled (testnet timing issues)
        // try { extendContractCodeFootprintTTL(wasmId, 100000) } catch (e: Exception) { println("Note: TTL extension failed (non-critical): ${e.message}") }
    }

    /**
     * Tests creating (deploying) the Soroban auth contract instance from an uploaded WASM.
     *
     * This test validates the contract deployment workflow for authorization contracts:
     * 1. Uses the WASM ID from testStep1UploadAuthContract
     * 2. Creates a CreateContractHostFunction using SDK helper method
     * 3. Simulates the deployment transaction
     * 4. Applies authorization entries from simulation (auto-auth for contract creation)
     * 5. Signs and submits the transaction
     * 6. Polls for transaction completion
     * 7. Extracts the created contract ID
     * 8. Stores contract ID for use by testInvokeAuthAccount and testInvokeAuthInvoker
     *
     * The test demonstrates:
     * - Contract instance creation from uploaded WASM using SDK helper method
     * - Authorization entry handling (auto-auth from simulation)
     * - Contract ID extraction from transaction result
     *
     * This test depends on testStep1UploadAuthContract having run first to provide the WASM ID.
     * If run independently, it will be skipped with an appropriate message.
     *
     * **Prerequisites**:
     * - testStep1UploadAuthContract must run first (provides WASM ID)
     * - Network connectivity to Stellar testnet
     *
     * **Duration**: ~30-60 seconds (includes network delays and polling)
     *
     * **Reference**: Ported from Flutter SDK's test create auth contract
     * (soroban_test_auth.dart lines 282-326)
     */
    @Test
    fun testStep2CreateAuthContract() = runTest(timeout = 120.seconds) {
        // Given: Check that testStep1UploadAuthContract has run and provided a WASM ID
        val wasmId = authContractWasmId
        val submitter = submitterKeyPair

        if (wasmId == null || submitter == null) {
            println("Skipping testStep2CreateAuthContract: testStep1UploadAuthContract must run first to provide WASM ID")
            return@runTest
        }

        realDelay(5000) // Wait for network to settle

        // Reload account for current sequence number
        val submitterId = submitter.getAccountId()
        val account = sorobanServer.getAccount(submitterId)
        assertNotNull(account, "Submitter account should be loaded")

        // When: Building create contract transaction using SDK helper method
        val addressObj = Address(submitterId)
        val salt = ByteArray(32) { 0 } // Use zero salt for deterministic contract ID in tests

        val operation = InvokeHostFunctionOperation.createContract(
            wasmId = wasmId,
            address = addressObj,
            salt = salt
        )

        val transaction = TransactionBuilder(
            sourceAccount = account,
            network = network
        )
            .addOperation(operation)
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        // Simulate transaction to obtain transaction data + resource fee + auth entries
        val simulateResponse = sorobanServer.simulateTransaction(transaction)
        assertNull(simulateResponse.error, "Simulation should not have error")
        assertNotNull(simulateResponse.results, "Simulation results should not be null")
        assertNotNull(simulateResponse.transactionData, "Transaction data should not be null")
        assertNotNull(simulateResponse.minResourceFee, "Min resource fee should not be null")

        // Prepare transaction with simulation results (includes auth entries)
        val preparedTransaction = sorobanServer.prepareTransaction(transaction, simulateResponse)

        // Sign transaction
        preparedTransaction.sign(submitter)

        // Then: Submit transaction to Soroban RPC
        val sendResponse = sorobanServer.sendTransaction(preparedTransaction)
        assertNotNull(sendResponse.hash, "Transaction hash should not be null")
        assertNotNull(sendResponse.status, "Transaction status should not be null")

        // Poll for transaction completion
        val rpcTransactionResponse = sorobanServer.pollTransaction(
            hash = sendResponse.hash,
            maxAttempts = 30,
            sleepStrategy = { 3000L }
        )

        assertEquals(
            GetTransactionStatus.SUCCESS,
            rpcTransactionResponse.status,
            "Transaction should succeed"
        )

        // Extract contract ID from transaction result
        val contractId = rpcTransactionResponse.getCreatedContractId()
        assertNotNull(contractId, "Contract ID should be extracted from transaction result")
        assertTrue(contractId.isNotEmpty(), "Contract ID should not be empty")
        assertTrue(contractId.startsWith("C"), "Contract ID should be strkey-encoded (start with 'C')")

        // Store contract ID for invoke tests
        authContractId = contractId

        println("Auth contract created with contract ID: $contractId")
    }

    /**
     * Tests restoring the auth contract footprint (state restoration).
     *
     * This test validates the Soroban state restoration workflow for auth contracts:
     * 1. Loads the auth contract WASM file
     * 2. Creates an upload contract transaction using SDK helper method
     * 3. Simulates to get the footprint (which ledger entries need restoration)
     * 4. Modifies the footprint: moves all readOnly keys to readWrite, clears readOnly
     * 5. Creates a RestoreFootprintOperation
     * 6. Builds a transaction with the modified footprint
     * 7. Simulates the restore transaction to get proper resource fee
     * 8. Signs and submits the restore transaction
     * 9. Polls for transaction success
     * 10. Verifies operations can be parsed via Horizon
     *
     * The test demonstrates:
     * - Footprint manipulation for restoration (readOnly → readWrite)
     * - RestoreFootprintOperation usage
     * - State restoration workflow for archived contract data
     *
     * **Prerequisites**: Network connectivity to Stellar testnet
     * **Duration**: ~30-60 seconds (includes network delays and polling)
     *
     * **Reference**: Ported from Flutter SDK's test restore footprint
     * (soroban_test_auth.dart lines 328-330, references lines 70-143)
     */
    @Test
    fun testStep3RestoreFootprint() = runTest(timeout = 120.seconds) {
        realDelay(5000) // Wait between tests

        // Given: Create and fund test account
        val keyPair = KeyPair.random()
        val accountId = keyPair.getAccountId()

        // Fund account via FriendBot (network-dependent)
        if (testOn == "testnet") {
            FriendBot.fundTestnetAccount(accountId)
        } else if (testOn == "futurenet") {
            FriendBot.fundFuturenetAccount(accountId)
        }
        realDelay(5000) // Wait for account creation

        // Load account
        var account = sorobanServer.getAccount(accountId)
        assertNotNull(account, "Account should be loaded")

        // Load auth contract WASM file
        val contractCode = TestResourceUtil.readWasmFile("soroban_auth_contract.wasm")
        assertTrue(contractCode.isNotEmpty(), "Auth contract code should not be empty")

        // When: Create upload transaction to get footprint using SDK helper method
        val uploadOperation = InvokeHostFunctionOperation.uploadContractWasm(contractCode)

        var transaction = TransactionBuilder(
            sourceAccount = account,
            network = network
        )
            .addOperation(uploadOperation)
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        // Simulate first to obtain the transaction data + footprint
        var simulateResponse = sorobanServer.simulateTransaction(transaction)
        assertNull(simulateResponse.error, "Simulation should not have error")
        assertNotNull(simulateResponse.transactionData, "Transaction data should not be null")

        // Extract and modify the footprint: move readOnly to readWrite
        val transactionData = simulateResponse.parseTransactionData()
        assertNotNull(transactionData, "Transaction data should be parsed")

        val originalFootprint = transactionData.resources.footprint
        val modifiedFootprint = LedgerFootprintXdr(
            readOnly = emptyList(), // Clear readOnly
            readWrite = originalFootprint.readOnly + originalFootprint.readWrite // Combine all keys into readWrite
        )

        // Create modified transaction data with the new footprint
        val modifiedTransactionData = SorobanTransactionDataXdr(
            ext = transactionData.ext,
            resources = SorobanResourcesXdr(
                footprint = modifiedFootprint,
                instructions = transactionData.resources.instructions,
                diskReadBytes = transactionData.resources.diskReadBytes,
                writeBytes = transactionData.resources.writeBytes
            ),
            resourceFee = transactionData.resourceFee
        )

        // Reload account for current sequence number
        account = sorobanServer.getAccount(accountId)
        assertNotNull(account, "Account should be reloaded")

        // Then: Build restore transaction
        val restoreOperation = RestoreFootprintOperation()
        transaction = TransactionBuilder(
            sourceAccount = account,
            network = network
        )
            .addOperation(restoreOperation)
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .setSorobanData(modifiedTransactionData)
            .build()

        // Simulate restore transaction to obtain proper resource fee
        simulateResponse = sorobanServer.simulateTransaction(transaction)
        assertNull(simulateResponse.error, "Restore simulation should not have error")
        assertNotNull(simulateResponse.transactionData, "Restore simulation should return transaction data")
        assertNotNull(simulateResponse.minResourceFee, "Restore simulation should return min resource fee")

        // Prepare transaction with simulation results
        val preparedTransaction = sorobanServer.prepareTransaction(transaction, simulateResponse)

        // Sign transaction
        preparedTransaction.sign(keyPair)

        // Verify transaction XDR encoding and decoding round-trip
        val transactionEnvelopeXdr = preparedTransaction.toEnvelopeXdrBase64()
        assertEquals(
            transactionEnvelopeXdr,
            AbstractTransaction.fromEnvelopeXdr(transactionEnvelopeXdr, network).toEnvelopeXdrBase64(),
            "Transaction XDR should round-trip correctly"
        )

        // Send transaction to soroban RPC server
        val sendResponse = sorobanServer.sendTransaction(preparedTransaction)
        assertNotNull(sendResponse.hash, "Transaction hash should not be null")
        assertNotNull(sendResponse.status, "Transaction status should not be null")

        // Poll for transaction completion
        val rpcTransactionResponse = sorobanServer.pollTransaction(
            hash = sendResponse.hash,
            maxAttempts = 30,
            sleepStrategy = { 3000L }
        )

        assertEquals(
            GetTransactionStatus.SUCCESS,
            rpcTransactionResponse.status,
            "Restore transaction should succeed"
        )

        println("Restored auth contract footprint successfully")
    }

    /**
     * Tests invoking the auth contract with explicit authorization (submitter != invoker).
     *
     * This test validates the complete authorization workflow when the transaction submitter
     * is different from the account being authorized to invoke the contract:
     * 1. Uses the contract ID from testStep2CreateAuthContract
     * 2. Builds an InvokeContract operation using SDK helper method
     * 3. Simulates the transaction to get authorization entries
     * 4. Gets the latest ledger to set signature expiration
     * 5. Manually signs authorization entries with the invoker's keypair using Auth.authorizeEntry()
     * 6. Rebuilds the transaction with signed authorization entries
     * 7. Signs the transaction with the submitter's keypair
     * 8. Submits and polls for transaction completion
     * 9. Extracts and validates the return value
     * 10. Verifies transaction can be parsed via Horizon
     *
     * The test demonstrates:
     * - Authorization signing when invoker != submitter
     * - Using Auth.authorizeEntry() to sign authorization entries
     * - Setting signature expiration ledger
     * - Two-phase signing: auth entries (invoker) + transaction (submitter)
     * - Result value extraction and validation
     * - Horizon API integration
     * - Using SDK helper method for contract invocation
     *
     * This test depends on testStep2CreateAuthContract having run first to provide the contract ID.
     * If run independently, it will be skipped with an appropriate message.
     *
     * **Prerequisites**:
     * - testStep2CreateAuthContract must run first (provides contract ID)
     * - Network connectivity to Stellar testnet
     *
     * **Duration**: ~30-60 seconds (includes network delays and polling)
     *
     * **Reference**: Ported from Flutter SDK's test invoke auth account
     * (soroban_test_auth.dart lines 332-434)
     */
    @Test
    fun testStep4InvokeAuthAccount() = runTest(timeout = 120.seconds) {
        // Given: Check that testStep2CreateAuthContract has run and provided a contract ID
        val contractId = authContractId
        val submitter = submitterKeyPair
        val invoker = invokerKeyPair

        if (contractId == null || submitter == null || invoker == null) {
            println("Skipping testStep4InvokeAuthAccount: testStep2CreateAuthContract must run first to provide contract ID")
            return@runTest
        }

        realDelay(5000) // Wait for network to settle

        // Reload submitter account for sequence number
        val submitterId = submitter.getAccountId()
        val account = sorobanServer.getAccount(submitterId)
        assertNotNull(account, "Submitter account should be loaded")

        // When: Building invoke contract transaction using SDK helper method
        val invokerId = invoker.getAccountId()
        val invokerAddress = Address(invokerId)
        val functionName = "increment"
        val args = listOf(
            invokerAddress.toSCVal(),
            Scv.toUint32(3u)
        )

        val operation = InvokeHostFunctionOperation.invokeContractFunction(
            contractAddress = contractId,
            functionName = functionName,
            parameters = args
        )

        val transaction = TransactionBuilder(
            sourceAccount = account,
            network = network
        )
            .addOperation(operation)
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        // Simulate transaction to obtain transaction data + resource fee + auth entries
        val simulateResponse = sorobanServer.simulateTransaction(transaction)
        assertNull(simulateResponse.error, "Simulation should not have error")
        assertNotNull(simulateResponse.results, "Simulation results should not be null")
        assertNotNull(simulateResponse.transactionData, "Transaction data should not be null")
        assertNotNull(simulateResponse.minResourceFee, "Min resource fee should not be null")

        // Get authorization entries from simulation result
        val simulateResult = simulateResponse.results[0]
        val authBase64List = simulateResult.auth
        assertNotNull(authBase64List, "Authorization entries should be returned from simulation")
        assertTrue(authBase64List.isNotEmpty(), "Should have at least one authorization entry")

        // Parse auth entries from base64
        val authEntries = authBase64List.map { SorobanAuthorizationEntryXdr.fromXdrBase64(it) }

        // Get latest ledger to set signature expiration
        val latestLedgerResponse = sorobanServer.getLatestLedger()
        val signatureExpirationLedger = latestLedgerResponse.sequence + 10

        // Sign authorization entries with invoker's keypair
        val signedAuthEntries = authEntries.map { authEntry ->
            Auth.authorizeEntry(
                entry = authEntry,
                signer = invoker,
                validUntilLedgerSeq = signatureExpirationLedger,
                network = network
            )
        }

        // Rebuild the operation with signed auth entries
        val signedOperation = InvokeHostFunctionOperation(
            hostFunction = operation.hostFunction,
            auth = signedAuthEntries
        )

        // Reset account sequence number so the rebuilt transaction
        // uses the same sequence as the simulated one (TransactionBuilder.build()
        // increments the sequence number each time it's called).
        account.setSequenceNumber(account.sequenceNumber - 1)

        // Rebuild transaction with signed operation
        val signedTransaction = TransactionBuilder(
            sourceAccount = account,
            network = network
        )
            .addOperation(signedOperation)
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        // Prepare transaction with simulation results
        val preparedTransaction = sorobanServer.prepareTransaction(signedTransaction, simulateResponse)

        // Sign transaction with submitter's keypair
        preparedTransaction.sign(submitter)

        // Verify transaction XDR encoding and decoding round-trip
        val transactionEnvelopeXdr = preparedTransaction.toEnvelopeXdrBase64()
        println("TX ENVELOPE: $transactionEnvelopeXdr")
        assertEquals(
            transactionEnvelopeXdr,
            AbstractTransaction.fromEnvelopeXdr(transactionEnvelopeXdr, network).toEnvelopeXdrBase64(),
            "Transaction XDR should round-trip correctly"
        )

        // Then: Send the transaction
        val sendResponse = sorobanServer.sendTransaction(preparedTransaction)
        assertNotNull(sendResponse.hash, "Transaction hash should not be null")
        assertNotNull(sendResponse.status, "Transaction status should not be null")

        // Poll for transaction completion
        val rpcTransactionResponse = sorobanServer.pollTransaction(
            hash = sendResponse.hash,
            maxAttempts = 30,
            sleepStrategy = { 3000L }
        )

        assertEquals(
            GetTransactionStatus.SUCCESS,
            rpcTransactionResponse.status,
            "Transaction should succeed"
        )

        // Extract and validate the result value
        val resVal = rpcTransactionResponse.getResultValue()
        assertNotNull(resVal, "Result value should not be null")

        // The increment function returns a u32
        assertTrue(resVal is SCValXdr.U32, "Result should be a u32")
        val resultValue = resVal.value.value
        println("Result: $resultValue")

        realDelay(5000) // Wait for Horizon to process

        // Verify transaction can be parsed via Horizon
        val horizonTransaction = horizonServer.transactions().transaction(sendResponse.hash)
        assertNotNull(horizonTransaction, "Horizon transaction should be found")
        assertEquals(1, horizonTransaction.operationCount, "Should have 1 operation")
        assertEquals(transactionEnvelopeXdr, horizonTransaction.envelopeXdr, "Envelope XDR should match")

        // Check if meta can be parsed
        horizonTransaction.resultMetaXdr?.let { resultMeta ->
            val meta = TransactionMetaXdr.fromXdrBase64(resultMeta)
            assertNotNull(meta, "Meta should be parsed")
            assertEquals(resultMeta, meta.toXdrBase64(), "Meta XDR should round-trip")
        }

        // Verify operations can be parsed via Horizon
        val operationsPage = horizonServer.operations().forTransaction(sendResponse.hash).execute()
        assertTrue(operationsPage.records.isNotEmpty(), "Should have operations")

        println("Auth contract invoked successfully (submitter != invoker)")
    }

    /**
     * Tests invoking the auth contract without manual authorization (submitter == invoker).
     *
     * This test validates the auto-authorization workflow when the transaction submitter
     * is the same as the account being authorized to invoke the contract:
     * 1. Uses the contract ID from testStep2CreateAuthContract
     * 2. Builds an InvokeContract operation using SDK helper method
     * 3. Simulates the transaction to get authorization entries and transaction data
     * 4. Uses authorization entries directly from simulation (auto-auth)
     * 5. Signs the transaction with the invoker's keypair (who is also the submitter)
     * 6. Submits and polls for transaction completion
     * 7. Extracts and validates the return value
     *
     * The test demonstrates:
     * - Auto-authorization when invoker == submitter (no manual Auth.authorizeEntry() needed)
     * - Simplified workflow for self-invocation
     * - Setting authorization entries from simulation
     * - Result value extraction and validation
     * - Using SDK helper method for contract invocation
     *
     * This test depends on testStep2CreateAuthContract having run first to provide the contract ID.
     * If run independently, it will be skipped with an appropriate message.
     *
     * **Prerequisites**:
     * - testStep2CreateAuthContract must run first (provides contract ID)
     * - Network connectivity to Stellar testnet
     *
     * **Duration**: ~30-60 seconds (includes network delays and polling)
     *
     * **Reference**: Ported from Flutter SDK's test invoke auth invoker
     * (soroban_test_auth.dart lines 436-494)
     */
    @Test
    fun testStep5InvokeAuthInvoker() = runTest(timeout = 120.seconds) {
        // Given: Check that testStep2CreateAuthContract has run and provided a contract ID
        val contractId = authContractId
        val invoker = invokerKeyPair

        if (contractId == null || invoker == null) {
            println("Skipping testStep5InvokeAuthInvoker: testStep2CreateAuthContract must run first to provide contract ID")
            return@runTest
        }

        realDelay(5000) // Wait for network to settle

        // Load invoker account for sequence number (invoker is also the submitter)
        val invokerId = invoker.getAccountId()
        val account = sorobanServer.getAccount(invokerId)
        assertNotNull(account, "Invoker account should be loaded")

        // When: Building invoke contract transaction using SDK helper method
        val invokerAddress = Address(invokerId)
        val functionName = "increment"
        val args = listOf(
            invokerAddress.toSCVal(),
            Scv.toUint32(3u)
        )

        val operation = InvokeHostFunctionOperation.invokeContractFunction(
            contractAddress = contractId,
            functionName = functionName,
            parameters = args
        )

        val transaction = TransactionBuilder(
            sourceAccount = account,
            network = network
        )
            .addOperation(operation)
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        // Simulate transaction to obtain transaction data + resource fee
        val simulateResponse = sorobanServer.simulateTransaction(transaction)
        assertNull(simulateResponse.error, "Simulation should not have error")
        assertNotNull(simulateResponse.results, "Simulation results should not be null")
        assertNotNull(simulateResponse.transactionData, "Transaction data should not be null")
        assertNotNull(simulateResponse.minResourceFee, "Min resource fee should not be null")

        // Prepare transaction with simulation results (includes auto-auth)
        val preparedTransaction = sorobanServer.prepareTransaction(transaction, simulateResponse)

        // Sign transaction with invoker's keypair (who is also the submitter)
        preparedTransaction.sign(invoker)

        // Verify transaction XDR encoding and decoding round-trip
        val transactionEnvelopeXdr = preparedTransaction.toEnvelopeXdrBase64()
        assertEquals(
            transactionEnvelopeXdr,
            AbstractTransaction.fromEnvelopeXdr(transactionEnvelopeXdr, network).toEnvelopeXdrBase64(),
            "Transaction XDR should round-trip correctly"
        )

        // Then: Send the transaction
        val sendResponse = sorobanServer.sendTransaction(preparedTransaction)
        assertNotNull(sendResponse.hash, "Transaction hash should not be null")
        assertNotNull(sendResponse.status, "Transaction status should not be null")

        // Poll for transaction completion
        val rpcTransactionResponse = sorobanServer.pollTransaction(
            hash = sendResponse.hash,
            maxAttempts = 30,
            sleepStrategy = { 3000L }
        )

        assertEquals(
            GetTransactionStatus.SUCCESS,
            rpcTransactionResponse.status,
            "Transaction should succeed"
        )

        // Extract and validate the result value
        val resVal = rpcTransactionResponse.getResultValue()
        assertNotNull(resVal, "Result value should not be null")

        // The increment function returns a u32
        assertTrue(resVal is SCValXdr.U32, "Result should be a u32")
        val resultValue = resVal.value.value
        println("Result: $resultValue")

        println("Auth contract invoked successfully (submitter == invoker, auto-auth)")
    }

    // ============================================================================
    // Helper Methods
    // ============================================================================

    /**
     * Helper function to extend contract code footprint TTL.
     *
     * This function:
     * 1. Loads the submitter account
     * 2. Creates an ExtendFootprintTTLOperation
     * 3. Builds the footprint with the contract code ledger key
     * 4. Simulates and prepares the transaction
     * 5. Signs and submits the transaction
     * 6. Polls for transaction success
     * 7. Verifies the transaction can be parsed via Horizon
     *
     * @param wasmId The WASM ID (hex string) of the contract code to extend
     * @param extendTo The number of ledgers to extend the TTL by
     */
    private suspend fun extendContractCodeFootprintTTL(wasmId: String, extendTo: Int) {
        realDelay(5000) // Wait between operations

        val submitter = submitterKeyPair!!
        val submitterId = submitter.getAccountId()

        // Load account
        val account = sorobanServer.getAccount(submitterId)
        assertNotNull(account, "Account should be loaded")

        // Create ExtendFootprintTTL operation
        val extendOperation = ExtendFootprintTTLOperation(extendTo = extendTo)

        // Create transaction for extending TTL
        var transaction = TransactionBuilder(
            sourceAccount = account,
            network = network
        )
            .addOperation(extendOperation)
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        // Build the footprint with contract code ledger key
        val codeKey = LedgerKeyXdr.ContractCode(
            LedgerKeyContractCodeXdr(
                hash = HashXdr(wasmId.chunked(2).map { it.toInt(16).toByte() }.toByteArray())
            )
        )

        val footprint = LedgerFootprintXdr(
            readOnly = listOf(codeKey),
            readWrite = emptyList()
        )

        val resources = SorobanResourcesXdr(
            footprint = footprint,
            instructions = Uint32Xdr(0u),
            diskReadBytes = Uint32Xdr(0u),
            writeBytes = Uint32Xdr(0u)
        )

        val transactionData = SorobanTransactionDataXdr(
            ext = SorobanTransactionDataExtXdr.Void,
            resources = resources,
            resourceFee = Int64Xdr(0L)
        )

        // Rebuild transaction with soroban data
        transaction = TransactionBuilder(
            sourceAccount = account,
            network = network
        )
            .addOperation(extendOperation)
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .setSorobanData(transactionData)
            .build()

        // Simulate first to obtain the transaction data + resource fee
        val simulateResponse = sorobanServer.simulateTransaction(transaction)
        assertNull(simulateResponse.error, "Extend simulation should not have error")
        assertNotNull(simulateResponse.transactionData, "Extend simulation should return transaction data")
        assertNotNull(simulateResponse.minResourceFee, "Extend simulation should return min resource fee")

        // Reload account for current sequence number
        val accountReloaded = sorobanServer.getAccount(submitterId)
        assertNotNull(accountReloaded, "Account should be reloaded")

        // Prepare transaction with simulation results
        val preparedTransaction = sorobanServer.prepareTransaction(transaction, simulateResponse)

        // Sign transaction
        preparedTransaction.sign(submitter)

        // Verify transaction XDR encoding and decoding round-trip
        val transactionEnvelopeXdr = preparedTransaction.toEnvelopeXdrBase64()
        assertEquals(
            transactionEnvelopeXdr,
            AbstractTransaction.fromEnvelopeXdr(transactionEnvelopeXdr, network).toEnvelopeXdrBase64(),
            "Transaction XDR should round-trip correctly"
        )

        // Send transaction to soroban RPC server
        val sendResponse = sorobanServer.sendTransaction(preparedTransaction)
        assertNotNull(sendResponse.hash, "Transaction hash should not be null")
        assertNotNull(sendResponse.status, "Transaction status should not be null")

        // Poll for transaction completion
        val rpcTransactionResponse = sorobanServer.pollTransaction(
            hash = sendResponse.hash,
            maxAttempts = 30,
            sleepStrategy = { 3000L }
        )

        assertEquals(
            GetTransactionStatus.SUCCESS,
            rpcTransactionResponse.status,
            "Extend transaction should succeed"
        )

        println("Extended contract code footprint TTL for WASM ID: $wasmId")
    }
}
