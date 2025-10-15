package com.soneso.stellar.sdk.integrationTests

import com.soneso.stellar.sdk.*
import com.soneso.stellar.sdk.horizon.HorizonServer
import com.soneso.stellar.sdk.rpc.SorobanServer
import com.soneso.stellar.sdk.rpc.responses.GetTransactionStatus
import com.soneso.stellar.sdk.scval.Scv
import com.soneso.stellar.sdk.util.TestResourceUtil
import com.soneso.stellar.sdk.xdr.*
import com.ionspin.kotlin.bignum.integer.BigInteger
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

/**
 * Comprehensive integration tests for Soroban atomic swap contract functionality.
 *
 * These tests verify the SDK's Soroban atomic swap integration against a live Stellar testnet.
 * They cover:
 * - Token contract upload and deployment
 * - Token initialization (name, symbol, decimals)
 * - Minting tokens to test accounts
 * - Token balance queries
 * - Atomic swap contract deployment
 * - Multi-party atomic swap execution (Alice and Bob)
 * - Authorization handling for both parties
 * - Contract footprint restoration
 * - Contract code footprint TTL extension
 *
 * **Test Network**: All tests use Stellar testnet Soroban RPC server.
 *
 * ## Running Tests
 *
 * These tests require network access to Soroban testnet RPC and Friendbot:
 * ```bash
 * ./gradlew :stellar-sdk:jvmTest --tests "SorobanAtomicSwapIntegrationTest"
 * ```
 *
 * ## Atomic Swap Workflow
 *
 * The atomic swap contract demonstrates a complete multi-party contract interaction:
 *
 * 1. **Setup Phase**:
 *    - Deploy two token contracts (TokenA and TokenB)
 *    - Initialize tokens with names and symbols
 *    - Mint tokens to Alice (TokenA) and Bob (TokenB)
 *
 * 2. **Swap Phase**:
 *    - Alice wants to swap 1000 units of TokenA for at least 4500 units of TokenB
 *    - Bob wants to swap 5000 units of TokenB for at least 950 units of TokenA
 *    - Admin account submits the swap transaction
 *    - Both Alice and Bob sign authorization entries
 *    - Swap executes atomically (both succeed or both fail)
 *
 * ## Ported From
 *
 * These tests are ported from the Flutter Stellar SDK's soroban_test_atomic_swap.dart:
 * - test install contracts (lines 520-538)
 * - test create contracts (lines 540-558)
 * - test restore footprint (lines 560-563)
 * - test create tokens (lines 565-570)
 * - test mint tokens (lines 572-583)
 * - test atomic swap (lines 585-697)
 *
 * **Reference**: `/Users/chris/projects/Stellar/stellar_flutter_sdk/test/soroban_test_atomic_swap.dart`
 *
 * @see <a href="https://soroban.stellar.org/docs/how-to-guides/atomic-swap/">Soroban Atomic Swap Guide</a>
 * @see <a href="https://soroban.stellar.org/docs/learn/authorization/">Soroban Authorization</a>
 * @see Auth
 */
class SorobanAtomicSwapIntegrationTest {

    private val testOn = "testnet"
    private val sorobanServer = SorobanServer("https://soroban-testnet.stellar.org")
    private val horizonServer = HorizonServer("https://horizon-testnet.stellar.org")
    private val network = Network.TESTNET

    companion object {
        /**
         * Admin account that submits transactions (contract upload, deployment, etc.).
         */
        var adminKeyPair: KeyPair? = null

        /**
         * Alice account that owns TokenA and wants to swap it for TokenB.
         */
        var aliceKeyPair: KeyPair? = null

        /**
         * Bob account that owns TokenB and wants to swap it for TokenA.
         */
        var bobKeyPair: KeyPair? = null

        /**
         * WASM ID for TokenA contract.
         */
        var tokenAContractWasmId: String? = null

        /**
         * Contract ID for deployed TokenA instance.
         */
        var tokenAContractId: String? = null

        /**
         * WASM ID for TokenB contract.
         */
        var tokenBContractWasmId: String? = null

        /**
         * Contract ID for deployed TokenB instance.
         */
        var tokenBContractId: String? = null

        /**
         * WASM ID for atomic swap contract.
         */
        var swapContractWasmId: String? = null

        /**
         * Contract ID for deployed atomic swap instance.
         */
        var swapContractId: String? = null
    }

    /**
     * Tests installing (uploading) token and atomic swap contracts to the ledger.
     *
     * This test validates the complete contract upload workflow:
     * 1. Creates and funds three test accounts: admin, alice, bob
     * 2. Uploads TokenA contract WASM
     * 3. Extends TokenA contract code footprint TTL
     * 4. Loads and validates TokenA contract metadata
     * 5. Uploads TokenB contract WASM
     * 6. Extends TokenB contract code footprint TTL
     * 7. Loads and validates TokenB contract metadata
     * 8. Uploads atomic swap contract WASM
     * 9. Extends swap contract code footprint TTL
     * 10. Loads and validates swap contract metadata
     * 11. Stores WASM IDs and keypairs for subsequent tests
     *
     * The test demonstrates:
     * - Account creation and funding for multiple parties
     * - Uploading multiple contract WASMs
     * - Extracting WASM IDs from transaction results
     * - Extending contract code TTL for testing
     * - Loading and validating contract metadata
     *
     * **Prerequisites**: Network connectivity to Stellar testnet
     * **Duration**: ~120-180 seconds (includes multiple uploads and TTL extensions)
     *
     * **Reference**: Ported from Flutter SDK's test install contracts
     * (soroban_test_atomic_swap.dart lines 520-538)
     */
    @Test
    fun testInstallContracts() = runTest(timeout = 300.seconds) {
        // Given: Create and fund three test accounts
        val admin = KeyPair.random()
        val adminId = admin.getAccountId()
        val alice = KeyPair.random()
        val aliceId = alice.getAccountId()
        val bob = KeyPair.random()
        val bobId = bob.getAccountId()

        // Fund all accounts via FriendBot
        FriendBot.fundTestnetAccount(adminId)
        FriendBot.fundTestnetAccount(aliceId)
        FriendBot.fundTestnetAccount(bobId)
        delay(5000) // Wait for account creation

        // Store keypairs for later tests
        adminKeyPair = admin
        aliceKeyPair = alice
        bobKeyPair = bob

        println("Admin: $adminId")
        println("Alice: $aliceId")
        println("Bob: $bobId")

        // When: Upload TokenA contract
        delay(5000)
        val tokenAWasmId = installContract("soroban_token_contract.wasm")
        tokenAContractWasmId = tokenAWasmId
        println("TokenA WASM ID: $tokenAWasmId")

        // Extend TokenA contract code footprint TTL
        delay(5000)
        extendContractCodeFootprintTTL(tokenAWasmId, 100000)

        // Verify TokenB contract info can be loaded
        val tokenAInfo = sorobanServer.loadContractInfoForWasmId(tokenAWasmId)
        assertNotNull(tokenAInfo, "TokenA contract info should be loaded")
        assertTrue(tokenAInfo!!.specEntries.isNotEmpty(), "TokenA should have spec entries")
        assertTrue(tokenAInfo.metaEntries.isNotEmpty(), "TokenA should have meta entries")

        // When: Upload TokenB contract
        delay(5000)
        val tokenBWasmId = installContract("soroban_token_contract.wasm")
        tokenBContractWasmId = tokenBWasmId
        println("TokenB WASM ID: $tokenBWasmId")

        // Extend TokenB contract code footprint TTL
        delay(5000)
        extendContractCodeFootprintTTL(tokenBWasmId, 100000)

        // Verify TokenB contract info can be loaded
        val tokenBInfo = sorobanServer.loadContractInfoForWasmId(tokenBWasmId)
        assertNotNull(tokenBInfo, "TokenB contract info should be loaded")
        assertTrue(tokenBInfo!!.specEntries.isNotEmpty(), "TokenB should have spec entries")
        assertTrue(tokenBInfo.metaEntries.isNotEmpty(), "TokenB should have meta entries")

        // When: Upload atomic swap contract
        delay(5000)
        val swapWasmId = installContract("soroban_atomic_swap_contract.wasm")
        swapContractWasmId = swapWasmId
        println("Swap WASM ID: $swapWasmId")

        // Extend swap contract code footprint TTL
        delay(5000)
        extendContractCodeFootprintTTL(swapWasmId, 100000)

        // Verify swap contract info can be loaded
        val swapInfo = sorobanServer.loadContractInfoForWasmId(swapWasmId)
        assertNotNull(swapInfo, "Swap contract info should be loaded")
        assertTrue(swapInfo!!.specEntries.isNotEmpty(), "Swap should have spec entries")
        assertTrue(swapInfo.metaEntries.isNotEmpty(), "Swap should have meta entries")

        delay(5000) // Final wait for ledger to settle
        println("All contracts installed successfully")
    }

    /**
     * Tests creating (deploying) contract instances from uploaded WASMs.
     *
     * This test validates the contract deployment workflow:
     * 1. Uses WASM IDs from testInstallContracts
     * 2. Deploys TokenA contract instance
     * 3. Verifies TokenA contract info can be loaded
     * 4. Deploys TokenB contract instance
     * 5. Verifies TokenB contract info can be loaded
     * 6. Deploys atomic swap contract instance
     * 7. Verifies swap contract info can be loaded
     * 8. Stores contract IDs for subsequent tests
     *
     * The test demonstrates:
     * - Contract instance creation from uploaded WASMs
     * - Authorization entry handling (auto-auth from simulation)
     * - Contract ID extraction from transaction results
     * - Contract info retrieval by contract ID
     *
     * This test depends on testInstallContracts having run first.
     * If run independently, it will be skipped with an appropriate message.
     *
     * **Prerequisites**:
     * - testInstallContracts must run first (provides WASM IDs)
     * - Network connectivity to Stellar testnet
     *
     * **Duration**: ~60-90 seconds (includes three contract deployments)
     *
     * **Reference**: Ported from Flutter SDK's test create contracts
     * (soroban_test_atomic_swap.dart lines 540-558)
     */
    @Test
    fun testCreateContracts() = runTest(timeout = 180.seconds) {
        // Given: Check that testInstallContracts has run
        val tokenAWasmId = tokenAContractWasmId
        val tokenBWasmId = tokenBContractWasmId
        val swapWasmId = swapContractWasmId
        val admin = adminKeyPair

        if (tokenAWasmId == null || tokenBWasmId == null || swapWasmId == null || admin == null) {
            println("Skipping testCreateContracts: testInstallContracts must run first")
            return@runTest
        }

        // When: Deploy TokenA contract
        delay(5000)
        val tokenAId = createContract(tokenAWasmId)
        tokenAContractId = tokenAId
        println("TokenA Contract ID: $tokenAId")

        // Verify TokenA contract info can be loaded
        delay(5000)
        val tokenAInfo = sorobanServer.loadContractInfoForContractId(tokenAId)
        assertNotNull(tokenAInfo, "TokenA contract info should be loaded")
        assertTrue(tokenAInfo!!.specEntries.isNotEmpty(), "TokenA should have spec entries")
        assertTrue(tokenAInfo.metaEntries.isNotEmpty(), "TokenA should have meta entries")

        // When: Deploy TokenB contract
        delay(5000)
        val tokenBId = createContract(tokenBWasmId)
        tokenBContractId = tokenBId
        println("TokenB Contract ID: $tokenBId")

        // Verify TokenB contract info can be loaded
        delay(5000)
        val tokenBInfo = sorobanServer.loadContractInfoForContractId(tokenBId)
        assertNotNull(tokenBInfo, "TokenB contract info should be loaded")
        assertTrue(tokenBInfo!!.specEntries.isNotEmpty(), "TokenB should have spec entries")
        assertTrue(tokenBInfo.metaEntries.isNotEmpty(), "TokenB should have meta entries")

        // When: Deploy atomic swap contract
        delay(5000)
        val swapId = createContract(swapWasmId)
        swapContractId = swapId
        println("Swap Contract ID: $swapId")

        // Verify swap contract info can be loaded
        delay(5000)
        val swapInfo = sorobanServer.loadContractInfoForContractId(swapId)
        assertNotNull(swapInfo, "Swap contract info should be loaded")
        assertTrue(swapInfo!!.specEntries.isNotEmpty(), "Swap should have spec entries")
        assertTrue(swapInfo.metaEntries.isNotEmpty(), "Swap should have meta entries")

        delay(5000) // Final wait for ledger to settle
        println("All contracts created successfully")
    }

    /**
     * Tests restoring contract footprints (state restoration).
     *
     * This test validates the Soroban state restoration workflow:
     * 1. Creates and funds a test account for restoration operations
     * 2. Restores token contract footprint
     * 3. Restores atomic swap contract footprint
     *
     * The test demonstrates:
     * - Footprint manipulation for restoration (readOnly → readWrite)
     * - RestoreFootprintOperation usage
     * - State restoration workflow for archived contract data
     *
     * **Prerequisites**: Network connectivity to Stellar testnet
     * **Duration**: ~60-90 seconds (includes two restore operations)
     *
     * **Reference**: Ported from Flutter SDK's test restore footprint
     * (soroban_test_atomic_swap.dart lines 560-563)
     */
    @Test
    fun testRestoreFootprint() = runTest(timeout = 180.seconds) {
        // Create and fund test account for restore operations
        val restoreAdmin = KeyPair.random()
        val restoreAdminId = restoreAdmin.getAccountId()
        FriendBot.fundTestnetAccount(restoreAdminId)
        delay(5000) // Wait for account creation

        // Temporarily set adminKeyPair for restore operations
        val originalAdminKeyPair = adminKeyPair
        adminKeyPair = restoreAdmin

        try {
            // Restore token contract footprint
            restoreContractFootprint("soroban_token_contract.wasm")

            // Restore atomic swap contract footprint
            restoreContractFootprint("soroban_atomic_swap_contract.wasm")

            println("Footprints restored successfully")
        } finally {
            // Restore original adminKeyPair
            adminKeyPair = originalAdminKeyPair
        }
    }

    /**
     * Tests initializing token contracts.
     *
     * This test validates the token initialization workflow:
     * 1. Uses contract IDs from testCreateContracts
     * 2. Initializes TokenA with name "TokenA", symbol "TokenA", decimals 8
     * 3. Initializes TokenB with name "TokenB", symbol "TokenB", decimals 8
     *
     * The test demonstrates:
     * - Invoking token contract initialize function
     * - Passing admin address, decimals, name, and symbol parameters
     * - Transaction simulation and submission
     *
     * This test depends on testCreateContracts having run first.
     * If run independently, it will be skipped with an appropriate message.
     *
     * **Prerequisites**:
     * - testCreateContracts must run first (provides contract IDs)
     * - Network connectivity to Stellar testnet
     *
     * **Duration**: ~30-60 seconds (includes two initializations)
     *
     * **Reference**: Ported from Flutter SDK's test create tokens
     * (soroban_test_atomic_swap.dart lines 565-570)
     */
    @Test
    fun testCreateTokens() = runTest(timeout = 120.seconds) {
        // Given: Check that testCreateContracts has run
        val tokenAId = tokenAContractId
        val tokenBId = tokenBContractId
        val admin = adminKeyPair

        if (tokenAId == null || tokenBId == null || admin == null) {
            println("Skipping testCreateTokens: testCreateContracts must run first")
            return@runTest
        }

        // When: Initialize TokenA
        createToken(tokenAId, "TokenA", "TokenA")
        delay(5000)

        // When: Initialize TokenB
        createToken(tokenBId, "TokenB", "TokenB")
        delay(5000)

        println("Tokens initialized successfully")
    }

    /**
     * Tests minting tokens to test accounts.
     *
     * This test validates the token minting workflow:
     * 1. Uses contract IDs from testCreateContracts
     * 2. Mints 10,000,000,000,000 units of TokenA to Alice
     * 3. Queries Alice's TokenA balance
     * 4. Mints 10,000,000,000,000 units of TokenB to Bob
     * 5. Queries Bob's TokenB balance
     * 6. Verifies balances match expected amounts
     *
     * The test demonstrates:
     * - Invoking token contract mint function
     * - Querying token balances
     * - Validating i128 return values
     *
     * This test depends on testCreateTokens having run first.
     * If run independently, it will be skipped with an appropriate message.
     *
     * **Prerequisites**:
     * - testCreateTokens must run first (provides initialized tokens)
     * - Network connectivity to Stellar testnet
     *
     * **Duration**: ~60-90 seconds (includes minting and balance queries)
     *
     * **Reference**: Ported from Flutter SDK's test mint tokens
     * (soroban_test_atomic_swap.dart lines 572-583)
     */
    @Test
    fun testMintTokens() = runTest(timeout = 150.seconds) {
        // Given: Check that testCreateTokens has run
        val tokenAId = tokenAContractId
        val tokenBId = tokenBContractId
        val alice = aliceKeyPair
        val bob = bobKeyPair

        if (tokenAId == null || tokenBId == null || alice == null || bob == null) {
            println("Skipping testMintTokens: testCreateTokens must run first")
            return@runTest
        }

        val aliceId = alice.getAccountId()
        val bobId = bob.getAccountId()

        // When: Mint TokenA to Alice
        mint(tokenAId, aliceId, 10000000000000L)
        delay(5000)

        // When: Mint TokenB to Bob
        mint(tokenBId, bobId, 10000000000000L)
        delay(5000)

        // Then: Verify Alice's TokenA balance
        val aliceTokenABalance = balance(tokenAId, aliceId)
        assertEquals(10000000000000L, aliceTokenABalance, "Alice should have 10T TokenA")
        delay(5000)

        // Then: Verify Bob's TokenB balance
        val bobTokenBBalance = balance(tokenBId, bobId)
        assertEquals(10000000000000L, bobTokenBBalance, "Bob should have 10T TokenB")
        delay(5000)

        println("Tokens minted successfully - Alice: 10T TokenA, Bob: 10T TokenB")
    }

    /**
     * Tests atomic swap execution between two parties.
     *
     * This test validates the complete atomic swap workflow:
     * 1. Uses contract IDs from testCreateContracts
     * 2. Alice wants to swap 1000 TokenA for at least 4500 TokenB
     * 3. Bob wants to swap 5000 TokenB for at least 950 TokenA
     * 4. Admin submits the swap transaction
     * 5. Gets latest ledger for signature expiration
     * 6. Signs authorization entries for Alice
     * 7. Signs authorization entries for Bob
     * 8. Rebuilds transaction with signed auth entries
     * 9. Signs transaction with admin keypair
     * 10. Submits and polls for transaction completion
     * 11. Validates swap execution succeeded
     *
     * The test demonstrates:
     * - Multi-party contract invocation
     * - Building InvokeContract with multiple parameters
     * - Authorization signing for both parties using Auth.authorizeEntry()
     * - Signature expiration ledger handling
     * - Transaction reconstruction with signed auth entries
     * - Two-phase signing: auth entries (Alice & Bob) + transaction (admin)
     * - Result value extraction
     *
     * This is the most complex test, demonstrating real-world multi-party
     * smart contract interaction with proper authorization.
     *
     * This test depends on testMintTokens having run first.
     * If run independently, it will be skipped with an appropriate message.
     *
     * **Prerequisites**:
     * - testMintTokens must run first (provides funded accounts)
     * - Network connectivity to Stellar testnet
     *
     * **Duration**: ~30-60 seconds (includes simulation, signing, submission)
     *
     * **Reference**: Ported from Flutter SDK's test atomic swap
     * (soroban_test_atomic_swap.dart lines 585-697)
     */
    @Test
    fun testAtomicSwap() = runTest(timeout = 120.seconds) {
        // Given: Check that all previous tests have run
        val atomicSwapContractId = swapContractId
        val tokenACId = tokenAContractId
        val tokenBCId = tokenBContractId
        val admin = adminKeyPair
        val aliceKp = aliceKeyPair
        val bobKp = bobKeyPair

        if (atomicSwapContractId == null || tokenACId == null || tokenBCId == null ||
            admin == null || aliceKp == null || bobKp == null) {
            println("Skipping testAtomicSwap: previous tests must run first")
            return@runTest
        }

        delay(10000) // Extra delay before swap

        val swapSubmitterAccountId = admin.getAccountId()
        val aliceAccountId = aliceKp.getAccountId()
        val bobAccountId = bobKp.getAccountId()

        // Prepare swap parameters
        val addressAlice = Address(aliceAccountId)
        val addressBob = Address(bobAccountId)

        // Alice wants to swap 1000 TokenA for at least 4500 TokenB
        val amountA = Scv.toInt128(BigInteger.fromLong(1000L))
        val minBForA = Scv.toInt128(BigInteger.fromLong(4500L))

        // Bob wants to swap 5000 TokenB for at least 950 TokenA
        val amountB = Scv.toInt128(BigInteger.fromLong(5000L))
        val minAForB = Scv.toInt128(BigInteger.fromLong(950L))

        val swapFunctionName = "swap"

        val invokeArgs = listOf(
            addressAlice.toSCVal(),
            addressBob.toSCVal(),
            Address(tokenACId).toSCVal(),
            Address(tokenBCId).toSCVal(),
            amountA,
            minBForA,
            amountB,
            minAForB
        )

        // Load submitter account for sequence number
        val account = sorobanServer.getAccount(swapSubmitterAccountId)
        assertNotNull(account, "Admin account should be loaded")

        // When: Building atomic swap transaction
        val contractAddress = Address(atomicSwapContractId)
        val invokeContractArgs = InvokeContractArgsXdr(
            contractAddress = contractAddress.toSCAddress(),
            functionName = SCSymbolXdr(swapFunctionName),
            args = invokeArgs
        )
        val hostFunction = HostFunctionXdr.InvokeContract(invokeContractArgs)
        val operation = InvokeHostFunctionOperation(
            hostFunction = hostFunction
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
        assertNotNull(simulateResponse.transactionData, "Transaction data should not be null")
        assertNotNull(simulateResponse.minResourceFee, "Min resource fee should not be null")

        // Get authorization entries from simulation result
        val simulateResult = simulateResponse.results!![0]
        val authBase64List = simulateResult.auth
        assertNotNull(authBase64List, "Authorization entries should be returned from simulation")
        assertTrue(authBase64List.isNotEmpty(), "Should have authorization entries")

        // Parse auth entries from base64
        val authEntries = authBase64List.map { SorobanAuthorizationEntryXdr.fromXdrBase64(it) }

        // Get latest ledger to set signature expiration
        val latestLedgerResponse = sorobanServer.getLatestLedger()
        val signatureExpirationLedger = latestLedgerResponse.sequence + 10

        // Sign authorization entries for both Alice and Bob
        val signedAuthEntries = authEntries.map { authEntry ->
            val credentials = authEntry.credentials
            if (credentials is SorobanCredentialsXdr.Address) {
                val address = credentials.value.address
                val addressAccountId = when (address) {
                    is SCAddressXdr.AccountId -> {
                        val accountId = address.value.value
                        KeyPair.fromPublicKey((accountId as PublicKeyXdr.Ed25519).value.value).getAccountId()
                    }
                    else -> null
                }

                when (addressAccountId) {
                    aliceAccountId -> {
                        Auth.authorizeEntry(
                            entry = authEntry,
                            signer = aliceKp,
                            validUntilLedgerSeq = signatureExpirationLedger.toLong(),
                            network = network
                        )
                    }
                    bobAccountId -> {
                        Auth.authorizeEntry(
                            entry = authEntry,
                            signer = bobKp,
                            validUntilLedgerSeq = signatureExpirationLedger.toLong(),
                            network = network
                        )
                    }
                    else -> authEntry // Shouldn't happen, but return original if address doesn't match
                }
            } else {
                authEntry // Return original if not Address credentials
            }
        }

        // Rebuild the operation with signed auth entries
        val signedOperation = InvokeHostFunctionOperation(
            hostFunction = hostFunction,
            auth = signedAuthEntries
        )

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

        // Sign transaction with admin keypair
        preparedTransaction.sign(admin)

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
        val statusResponse = sorobanServer.pollTransaction(
            hash = sendResponse.hash!!,
            maxAttempts = 30,
            sleepStrategy = { 3000L }
        )

        assertEquals(
            GetTransactionStatus.SUCCESS,
            statusResponse.status,
            "Atomic swap transaction should succeed"
        )

        // Extract and log the result value
        val resVal = statusResponse.getResultValue()
        assertNotNull(resVal, "Result value should not be null")
        println("Swap result: ${resVal.toXdrBase64()}")

        println("Atomic swap executed successfully!")
    }

    // ============================================================================
    // Helper Methods
    // ============================================================================

    /**
     * Helper function to install (upload) a contract WASM.
     *
     * @param contractCodePath The path to the WASM file in test resources
     * @return The WASM ID (hex string) of the uploaded contract
     */
    private suspend fun installContract(contractCodePath: String): String {
        delay(5000)

        val admin = adminKeyPair!!
        val adminId = admin.getAccountId()

        // Load account
        val account = sorobanServer.getAccount(adminId)
        assertNotNull(account, "Admin account should be loaded")

        // Load contract WASM file
        val contractCode = TestResourceUtil.readWasmFile(contractCodePath)
        assertTrue(contractCode.isNotEmpty(), "Contract code should not be empty")

        // Upload contract
        val uploadFunction = HostFunctionXdr.Wasm(contractCode)
        val operation = InvokeHostFunctionOperation(
            hostFunction = uploadFunction
        )
        val transaction = TransactionBuilder(
            sourceAccount = account,
            network = network
        )
            .addOperation(operation)
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        // Simulate first to obtain the transaction data + resource fee
        val simulateResponse = sorobanServer.simulateTransaction(transaction)
        assertNull(simulateResponse.error, "Simulation should not have error")
        assertNotNull(simulateResponse.transactionData, "Transaction data should not be null")

        // Prepare transaction with simulation results
        val preparedTransaction = sorobanServer.prepareTransaction(transaction, simulateResponse)

        // Sign transaction
        preparedTransaction.sign(admin)

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
            hash = sendResponse.hash!!,
            maxAttempts = 30,
            sleepStrategy = { 3000L }
        )

        assertEquals(
            GetTransactionStatus.SUCCESS,
            rpcTransactionResponse.status,
            "Upload transaction should succeed"
        )

        // Extract WASM ID from transaction result
        val wasmId = rpcTransactionResponse.getWasmId()
        assertNotNull(wasmId, "WASM ID should be extracted")
        assertTrue(wasmId!!.isNotEmpty(), "WASM ID should not be empty")

        return wasmId
    }

    /**
     * Helper function to create (deploy) a contract instance from a WASM.
     *
     * @param wasmId The WASM ID (hex string) of the uploaded contract
     * @return The contract ID (strkey C... format) of the deployed contract
     */
    private suspend fun createContract(wasmId: String): String {
        delay(5000)

        val admin = adminKeyPair!!
        val adminId = admin.getAccountId()

        // Reload account for current sequence number
        val account = sorobanServer.getAccount(adminId)
        assertNotNull(account, "Admin account should be loaded")

        // Build the operation for creating the contract
        val addressObj = Address(adminId)
        val scAddress = addressObj.toSCAddress()

        // Generate salt (random for each contract)
        val salt = Uint256Xdr(ByteArray(32) { Random.nextInt(256).toByte() })

        val preimage = ContractIDPreimageXdr.FromAddress(
            ContractIDPreimageFromAddressXdr(
                address = scAddress,
                salt = salt
            )
        )

        val wasmHash = HashXdr(wasmId.chunked(2).map { it.toInt(16).toByte() }.toByteArray())
        val executable = ContractExecutableXdr.WasmHash(wasmHash)

        val createContractArgs = CreateContractArgsXdr(
            contractIdPreimage = preimage,
            executable = executable
        )

        val createFunction = HostFunctionXdr.CreateContract(createContractArgs)
        val operation = InvokeHostFunctionOperation(
            hostFunction = createFunction
        )

        val transaction = TransactionBuilder(
            sourceAccount = account,
            network = network
        )
            .addOperation(operation)
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        // Simulate first to obtain the transaction data + resource fee
        val simulateResponse = sorobanServer.simulateTransaction(transaction)
        assertNull(simulateResponse.error, "Simulation should not have error")
        assertNotNull(simulateResponse.transactionData, "Transaction data should not be null")

        // Prepare transaction with simulation results
        val preparedTransaction = sorobanServer.prepareTransaction(transaction, simulateResponse)

        // Sign transaction
        preparedTransaction.sign(admin)

        // Send transaction to soroban RPC server
        val sendResponse = sorobanServer.sendTransaction(preparedTransaction)
        assertNotNull(sendResponse.hash, "Transaction hash should not be null")
        assertNotNull(sendResponse.status, "Transaction status should not be null")

        // Poll for transaction completion
        val rpcTransactionResponse = sorobanServer.pollTransaction(
            hash = sendResponse.hash!!,
            maxAttempts = 30,
            sleepStrategy = { 3000L }
        )

        assertEquals(
            GetTransactionStatus.SUCCESS,
            rpcTransactionResponse.status,
            "Create contract transaction should succeed"
        )

        // Extract contract ID from transaction result
        val contractId = rpcTransactionResponse.getCreatedContractId()
        assertNotNull(contractId, "Contract ID should be extracted")
        assertTrue(contractId!!.isNotEmpty(), "Contract ID should not be empty")
        assertTrue(contractId.startsWith("C"), "Contract ID should be strkey-encoded")

        return contractId
    }

    /**
     * Helper function to initialize a token contract.
     *
     * @param contractId The contract ID of the token
     * @param name The token name
     * @param symbol The token symbol
     */
    private suspend fun createToken(contractId: String, name: String, symbol: String) {
        delay(5000)

        val admin = adminKeyPair!!
        val adminId = admin.getAccountId()

        // Reload account for sequence number
        val account = sorobanServer.getAccount(adminId)
        assertNotNull(account, "Admin account should be loaded")

        val adminAddress = Address(adminId)
        val functionName = "initialize"
        val tokenName = Scv.toString(name)
        val tokenSymbol = Scv.toString(symbol)

        val args = listOf(
            adminAddress.toSCVal(),
            Scv.toUint32(8u), // decimals
            tokenName,
            tokenSymbol
        )

        val contractAddress = Address(contractId)
        val invokeArgs = InvokeContractArgsXdr(
            contractAddress = contractAddress.toSCAddress(),
            functionName = SCSymbolXdr(functionName),
            args = args
        )
        val hostFunction = HostFunctionXdr.InvokeContract(invokeArgs)
        val operation = InvokeHostFunctionOperation(
            hostFunction = hostFunction
        )

        val transaction = TransactionBuilder(
            sourceAccount = account,
            network = network
        )
            .addOperation(operation)
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        // Simulate first to obtain the transaction data + resource fee
        val simulateResponse = sorobanServer.simulateTransaction(transaction)
        assertNull(simulateResponse.error, "Simulation should not have error")
        assertNotNull(simulateResponse.transactionData, "Transaction data should not be null")

        // Prepare transaction with simulation results
        val preparedTransaction = sorobanServer.prepareTransaction(transaction, simulateResponse)

        // Sign transaction
        preparedTransaction.sign(admin)

        // Verify transaction XDR encoding and decoding round-trip
        val transactionEnvelopeXdr = preparedTransaction.toEnvelopeXdrBase64()
        assertEquals(
            transactionEnvelopeXdr,
            AbstractTransaction.fromEnvelopeXdr(transactionEnvelopeXdr, network).toEnvelopeXdrBase64(),
            "Transaction XDR should round-trip correctly"
        )

        // Send the transaction
        val sendResponse = sorobanServer.sendTransaction(preparedTransaction)
        assertNotNull(sendResponse.hash, "Transaction hash should not be null")
        assertNotNull(sendResponse.status, "Transaction status should not be null")

        // Poll for transaction completion
        val statusResponse = sorobanServer.pollTransaction(
            hash = sendResponse.hash!!,
            maxAttempts = 30,
            sleepStrategy = { 3000L }
        )

        assertEquals(
            GetTransactionStatus.SUCCESS,
            statusResponse.status,
            "Initialize transaction should succeed"
        )

        println("Token initialized: $name ($symbol)")
    }

    /**
     * Helper function to mint tokens to an account.
     *
     * @param contractId The contract ID of the token
     * @param toAccountId The account ID to mint tokens to
     * @param amount The amount of tokens to mint
     */
    private suspend fun mint(contractId: String, toAccountId: String, amount: Long) {
        delay(5000)

        val admin = adminKeyPair!!
        val adminId = admin.getAccountId()

        // Reload account for sequence number
        val account = sorobanServer.getAccount(adminId)
        assertNotNull(account, "Admin account should be loaded")

        val toAddress = Address(toAccountId)
        val amountVal = Scv.toInt128(BigInteger.fromLong(amount))
        val functionName = "mint"

        val args = listOf(toAddress.toSCVal(), amountVal)

        val contractAddress = Address(contractId)
        val invokeArgs = InvokeContractArgsXdr(
            contractAddress = contractAddress.toSCAddress(),
            functionName = SCSymbolXdr(functionName),
            args = args
        )
        val hostFunction = HostFunctionXdr.InvokeContract(invokeArgs)
        val operation = InvokeHostFunctionOperation(
            hostFunction = hostFunction
        )

        val transaction = TransactionBuilder(
            sourceAccount = account,
            network = network
        )
            .addOperation(operation)
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        // Simulate first to obtain the transaction data + resource fee
        val simulateResponse = sorobanServer.simulateTransaction(transaction)
        assertNull(simulateResponse.error, "Simulation should not have error")
        assertNotNull(simulateResponse.transactionData, "Transaction data should not be null")

        // Prepare transaction with simulation results
        val preparedTransaction = sorobanServer.prepareTransaction(transaction, simulateResponse)

        // Sign transaction
        preparedTransaction.sign(admin)

        // Verify transaction XDR encoding and decoding round-trip
        val transactionEnvelopeXdr = preparedTransaction.toEnvelopeXdrBase64()
        assertEquals(
            transactionEnvelopeXdr,
            AbstractTransaction.fromEnvelopeXdr(transactionEnvelopeXdr, network).toEnvelopeXdrBase64(),
            "Transaction XDR should round-trip correctly"
        )

        // Send the transaction
        val sendResponse = sorobanServer.sendTransaction(preparedTransaction)
        assertNotNull(sendResponse.hash, "Transaction hash should not be null")
        assertNotNull(sendResponse.status, "Transaction status should not be null")

        // Poll for transaction completion
        val statusResponse = sorobanServer.pollTransaction(
            hash = sendResponse.hash!!,
            maxAttempts = 30,
            sleepStrategy = { 3000L }
        )

        assertEquals(
            GetTransactionStatus.SUCCESS,
            statusResponse.status,
            "Mint transaction should succeed"
        )

        println("Minted $amount tokens to $toAccountId")
    }

    /**
     * Helper function to query token balance.
     *
     * @param contractId The contract ID of the token
     * @param accountId The account ID to query balance for
     * @return The token balance
     */
    private suspend fun balance(contractId: String, accountId: String): Long {
        delay(5000)

        val admin = adminKeyPair!!
        val adminId = admin.getAccountId()

        // Reload account for sequence number
        val account = sorobanServer.getAccount(adminId)
        assertNotNull(account, "Admin account should be loaded")

        val address = Address(accountId)
        val functionName = "balance"

        val args = listOf(address.toSCVal())

        val contractAddress = Address(contractId)
        val invokeArgs = InvokeContractArgsXdr(
            contractAddress = contractAddress.toSCAddress(),
            functionName = SCSymbolXdr(functionName),
            args = args
        )
        val hostFunction = HostFunctionXdr.InvokeContract(invokeArgs)
        val operation = InvokeHostFunctionOperation(
            hostFunction = hostFunction
        )

        val transaction = TransactionBuilder(
            sourceAccount = account,
            network = network
        )
            .addOperation(operation)
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        // Simulate first to obtain the transaction data + resource fee
        val simulateResponse = sorobanServer.simulateTransaction(transaction)
        assertNull(simulateResponse.error, "Simulation should not have error")
        assertNotNull(simulateResponse.transactionData, "Transaction data should not be null")

        // Prepare transaction with simulation results
        val preparedTransaction = sorobanServer.prepareTransaction(transaction, simulateResponse)

        // Sign transaction
        preparedTransaction.sign(admin)

        // Verify transaction XDR encoding and decoding round-trip
        val transactionEnvelopeXdr = preparedTransaction.toEnvelopeXdrBase64()
        assertEquals(
            transactionEnvelopeXdr,
            AbstractTransaction.fromEnvelopeXdr(transactionEnvelopeXdr, network).toEnvelopeXdrBase64(),
            "Transaction XDR should round-trip correctly"
        )

        // Send the transaction
        val sendResponse = sorobanServer.sendTransaction(preparedTransaction)
        assertNotNull(sendResponse.hash, "Transaction hash should not be null")
        assertNotNull(sendResponse.status, "Transaction status should not be null")

        // Poll for transaction completion
        val statusResponse = sorobanServer.pollTransaction(
            hash = sendResponse.hash!!,
            maxAttempts = 30,
            sleepStrategy = { 3000L }
        )

        assertEquals(
            GetTransactionStatus.SUCCESS,
            statusResponse.status,
            "Balance query transaction should succeed"
        )

        // Extract result value
        val resVal = statusResponse.getResultValue()
        assertNotNull(resVal, "Result value should not be null")

        // The balance function returns an i128
        assertTrue(resVal is SCValXdr.I128, "Result should be i128")
        val parts = (resVal as SCValXdr.I128).value
        return parts.lo.value.toLong()
    }

    /**
     * Helper function to restore contract footprint.
     *
     * @param contractCodePath The path to the WASM file in test resources
     */
    private suspend fun restoreContractFootprint(contractCodePath: String) {
        delay(5000)

        val admin = adminKeyPair!!
        val adminId = admin.getAccountId()

        // Load account
        var account = sorobanServer.getAccount(adminId)
        assertNotNull(account, "Admin account should be loaded")

        // Load contract WASM file
        val contractCode = TestResourceUtil.readWasmFile(contractCodePath)
        assertTrue(contractCode.isNotEmpty(), "Contract code should not be empty")

        // Create upload transaction to get footprint
        val uploadFunction = HostFunctionXdr.Wasm(contractCode)
        val uploadOperation = InvokeHostFunctionOperation(hostFunction = uploadFunction)

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

        val originalFootprint = transactionData!!.resources.footprint
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
        account = sorobanServer.getAccount(adminId)
        assertNotNull(account, "Account should be reloaded")

        // Build restore transaction
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
        preparedTransaction.sign(admin)

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
            hash = sendResponse.hash!!,
            maxAttempts = 30,
            sleepStrategy = { 3000L }
        )

        assertEquals(
            GetTransactionStatus.SUCCESS,
            rpcTransactionResponse.status,
            "Restore transaction should succeed"
        )

        println("Restored footprint for $contractCodePath")
    }

    /**
     * Helper function to extend contract code footprint TTL.
     *
     * @param wasmId The WASM ID (hex string) of the contract code to extend
     * @param extendTo The number of ledgers to extend the TTL by
     */
    private suspend fun extendContractCodeFootprintTTL(wasmId: String, extendTo: Int) {
        delay(5000)

        val admin = adminKeyPair!!
        val adminId = admin.getAccountId()

        // Load account
        var account = sorobanServer.getAccount(adminId)
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

        // Reload account for current sequence number before rebuilding transaction
        delay(3000)
        account = sorobanServer.getAccount(adminId)
        assertNotNull(account, "Account should be reloaded")

        // Rebuild transaction with soroban data and RELOADED account
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

        // Prepare transaction with simulation results
        val preparedTransaction = sorobanServer.prepareTransaction(transaction, simulateResponse)

        // Sign transaction
        preparedTransaction.sign(admin)

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
            hash = sendResponse.hash!!,
            maxAttempts = 30,
            sleepStrategy = { 3000L }
        )

        assertEquals(
            GetTransactionStatus.SUCCESS,
            rpcTransactionResponse.status,
            "Extend transaction should succeed"
        )

        // Wait for Horizon to process
        delay(5000)

        // Check Horizon responses decoding
        val transactionResponse = horizonServer.transactions().transaction(sendResponse.hash!!)
        assertNotNull(transactionResponse, "Horizon transaction should be found")
        assertEquals(1, transactionResponse.operationCount, "Should have 1 operation")
        assertEquals(transactionEnvelopeXdr, transactionResponse.envelopeXdr, "Envelope XDR should match")

        // Check operation response from horizon
        val operations = horizonServer.operations().forTransaction(sendResponse.hash!!).execute()
        assertTrue(operations.records.isNotEmpty(), "Should have operations")

        println("Extended contract code footprint TTL for WASM ID: $wasmId")
    }
}
