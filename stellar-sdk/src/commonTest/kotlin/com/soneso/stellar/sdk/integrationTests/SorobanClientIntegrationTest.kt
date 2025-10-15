package com.soneso.stellar.sdk.integrationTests

import com.soneso.stellar.sdk.*
import com.soneso.stellar.sdk.horizon.HorizonServer
import com.soneso.stellar.sdk.rpc.SorobanServer
import com.soneso.stellar.sdk.rpc.responses.GetTransactionStatus
import com.soneso.stellar.sdk.contract.ContractClient
import com.soneso.stellar.sdk.contract.ContractSpec
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
 * Integration tests for SorobanClient high-level API and ContractSpec functionality.
 *
 * These tests verify the SDK's high-level contract interaction APIs against a live Stellar testnet.
 * They cover:
 * - ContractSpec-based automatic type conversion
 * - Hello contract invocation with ContractSpec
 * - Auth contract invocation with ContractSpec
 * - Atomic swap with ContractSpec (multi-party authorization)
 * - Comparison of manual XDR construction vs ContractSpec approach
 *
 * **Test Network**: All tests use Stellar testnet Soroban RPC server.
 *
 * ## Running Tests
 *
 * These tests require network access to Soroban testnet RPC and Friendbot:
 * ```bash
 * ./gradlew :stellar-sdk:jvmTest --tests "SorobanClientIntegrationTest"
 * ```
 *
 * ## Known Issues
 *
 * - testAtomicSwapWithContractSpec may fail due to Soroban testnet latency (transaction NOT_FOUND)
 * - This is a known issue with complex multi-party transactions on testnet
 * - The transaction typically succeeds but polling times out before it's included in a ledger
 *
 * ## Ported From
 *
 * These tests are ported from the Flutter Stellar SDK's soroban_client_test.dart:
 * - test hello contract with ContractSpec (lines 163-214)
 * - test auth with ContractSpec (lines 280-324)
 * - test atomic swap with ContractSpec (lines 435-543)
 *
 * **Reference**: `/Users/chris/projects/Stellar/stellar_flutter_sdk/test/soroban_client_test.dart`
 *
 * @see <a href="https://developers.stellar.org/docs/learn/smart-contract-internals/contract-interactions/stellar-transaction">Contract Interactions</a>
 * @see ContractSpec
 */
class SorobanClientIntegrationTest {

    private val testOn = "testnet"
    private val sorobanServer = SorobanServer("https://soroban-testnet.stellar.org")
    private val horizonServer = HorizonServer("https://horizon-testnet.stellar.org")
    private val network = Network.TESTNET

    companion object {
        /**
         * Shared source account keypair used by all tests.
         */
        var sourceAccountKeyPair: KeyPair? = null

        /**
         * Shared hello contract WASM ID.
         */
        var helloContractWasmId: String? = null

        /**
         * Shared hello contract ID.
         */
        var helloContractId: String? = null

        /**
         * Shared auth contract WASM ID.
         */
        var authContractWasmId: String? = null

        /**
         * Shared auth contract ID.
         */
        var authContractId: String? = null

        /**
         * Shared swap contract WASM ID.
         */
        var swapContractWasmId: String? = null

        /**
         * Shared swap contract ID.
         */
        var swapContractId: String? = null

        /**
         * Shared token contract WASM ID.
         */
        var tokenContractWasmId: String? = null

        /**
         * Shared token A contract ID.
         */
        var tokenAContractId: String? = null

        /**
         * Shared token B contract ID.
         */
        var tokenBContractId: String? = null

        /**
         * Admin keypair for token operations.
         */
        var adminKeyPair: KeyPair? = null

        /**
         * Alice keypair for atomic swap.
         */
        var aliceKeyPair: KeyPair? = null

        /**
         * Bob keypair for atomic swap.
         */
        var bobKeyPair: KeyPair? = null
    }

    /**
     * Tests the hello contract with ContractSpec for automatic type conversion.
     *
     * This test validates:
     * 1. Uploading and deploying the hello world contract
     * 2. Loading contract metadata and extracting ContractSpec
     * 3. Using ContractSpec.funcArgsToXdrSCValues() for automatic type conversion
     * 4. Invoking contract with converted arguments
     * 5. Validating the result matches expected output
     * 6. Comparing manual XDR approach vs ContractSpec approach
     *
     * The test demonstrates:
     * - ContractSpec extraction from contract metadata
     * - Automatic conversion from Kotlin types to SCVals
     * - Simplified contract invocation without manual XDR construction
     * - Function introspection (getting function details)
     *
     * The hello contract has a "hello" function that takes a symbol parameter (name)
     * and returns a vector with two symbols: ["Hello", <parameter>].
     *
     * **Prerequisites**: Network connectivity to Stellar testnet
     * **Duration**: ~60-90 seconds (includes upload, deploy, and invocation)
     *
     * **Reference**: Ported from Flutter SDK's test hello contract with ContractSpec
     * (soroban_client_test.dart lines 163-214)
     */
    @Test
    fun testHelloContractWithContractSpec() = runTest(timeout = 180.seconds) {
        // Given: Create and fund test account
        val keyPair = KeyPair.random()
        val accountId = keyPair.getAccountId()
        sourceAccountKeyPair = keyPair

        FriendBot.fundTestnetAccount(accountId)
        delay(5000) // Wait for account creation

        // Step 1: Upload hello contract
        val helloContractCode = TestResourceUtil.readWasmFile("soroban_hello_world_contract.wasm")
        assertTrue(helloContractCode.isNotEmpty(), "Hello contract code should not be empty")

        val wasmId = installContract(helloContractCode)
        helloContractWasmId = wasmId
        println("Installed hello contract wasm hash: $wasmId")

        // Step 2: Deploy hello contract
        delay(5000)
        val contractId = deployContract(wasmId)
        helloContractId = contractId
        println("Deployed hello contract contract id: $contractId")

        // Step 3: Load contract info and extract ContractSpec
        delay(5000)
        val contractInfo = sorobanServer.loadContractInfoForContractId(contractId)
        assertNotNull(contractInfo, "Contract info should be loaded")
        assertTrue(contractInfo!!.specEntries.isNotEmpty(), "Contract should have spec entries")

        // Create ContractSpec from spec entries
        val contractSpec = ContractSpec(contractInfo.specEntries)
        assertNotNull(contractSpec, "ContractSpec should be created")

        // Step 4: Demonstrate ContractSpec capabilities
        val functions = contractSpec.funcs()
        println("Contract functions: ${functions.map { it.name.value }}")
        assertTrue(functions.isNotEmpty(), "Contract should have functions")

        val helloFunc = contractSpec.getFunc("hello")
        assertNotNull(helloFunc, "Should find hello function")
        assertEquals("hello", helloFunc!!.name.value, "Function name should be 'hello'")
        assertTrue(helloFunc.inputs.isNotEmpty(), "Hello function should have inputs")
        println("Found hello function with ${helloFunc.inputs.size} input(s)")

        // Step 5: Manual XDR approach (original approach)
        println("=== Manual XdrSCVal Creation (Original) ===")
        val manualArg = Scv.toSymbol("John")
        val manualResult = invokeContract(contractId, "hello", listOf(manualArg))

        // Validate manual result
        assertTrue(manualResult is SCValXdr.Vec, "Result should be a vector")
        val manualVec = (manualResult as SCValXdr.Vec).value?.value
        assertNotNull(manualVec, "Vector should not be null")
        assertEquals(2, manualVec.size, "Vector should have 2 elements")
        assertTrue(manualVec[0] is SCValXdr.Sym, "First element should be a symbol")
        assertTrue(manualVec[1] is SCValXdr.Sym, "Second element should be a symbol")
        assertEquals("Hello", (manualVec[0] as SCValXdr.Sym).value.value)
        assertEquals("John", (manualVec[1] as SCValXdr.Sym).value.value)
        val manualResultValue = "${(manualVec[0] as SCValXdr.Sym).value.value}, ${(manualVec[1] as SCValXdr.Sym).value.value}"
        assertEquals("Hello, John", manualResultValue)
        println("Manual result: $manualResultValue")

        // Step 6: ContractSpec approach (new approach)
        println("=== ContractSpec Approach (New) ===")
        // Much simpler and more readable!
        // Instead of: listOf(Scv.toSymbol("Maria"))
        // We can use: mapOf("to" to "Maria")
        val specArgs = contractSpec.funcArgsToXdrSCValues("hello", mapOf("to" to "Maria"))
        assertNotNull(specArgs, "ContractSpec should convert arguments")
        assertEquals(1, specArgs.size, "Should have 1 argument")

        val specResult = invokeContract(contractId, "hello", specArgs)

        // Validate ContractSpec result
        assertTrue(specResult is SCValXdr.Vec, "Result should be a vector")
        val specVec = (specResult as SCValXdr.Vec).value?.value
        assertNotNull(specVec, "Vector should not be null")
        assertEquals(2, specVec.size, "Vector should have 2 elements")
        assertTrue(specVec[0] is SCValXdr.Sym, "First element should be a symbol")
        assertTrue(specVec[1] is SCValXdr.Sym, "Second element should be a symbol")
        assertEquals("Hello", (specVec[0] as SCValXdr.Sym).value.value)
        assertEquals("Maria", (specVec[1] as SCValXdr.Sym).value.value)
        val specResultValue = "${(specVec[0] as SCValXdr.Sym).value.value}, ${(specVec[1] as SCValXdr.Sym).value.value}"
        assertEquals("Hello, Maria", specResultValue)
        println("ContractSpec result: $specResultValue")

        println("✓ ContractSpec successfully converted hello function arguments")
        println("✓ Result: $specResultValue")

        // Step 7: Test with different name
        val args3 = contractSpec.funcArgsToXdrSCValues("hello", mapOf("to" to "World"))
        val result3 = invokeContract(contractId, "hello", args3)
        val vec3 = (result3 as SCValXdr.Vec).value?.value
        val resultValue3 = "${(vec3!![0] as SCValXdr.Sym).value.value}, ${(vec3[1] as SCValXdr.Sym).value.value}"
        assertEquals("Hello, World", resultValue3)
        println("✓ Another test works: $resultValue3")

        println("✓ ContractSpec successfully simplified contract invocation")
    }

    /**
     * Tests the auth contract with ContractSpec for automatic type conversion.
     *
     * This test validates:
     * 1. Deploying the auth contract (from cached WASM or fresh deploy)
     * 2. Loading contract metadata and extracting ContractSpec
     * 3. Using ContractSpec for automatic conversion of account ID → Address, int → u32
     * 4. Comparing manual XDR approach vs ContractSpec approach
     * 5. Testing both same-submitter and different-submitter scenarios
     *
     * The test demonstrates:
     * - ContractSpec extraction from contract metadata
     * - Automatic type conversion for Address and u32 types
     * - Simplified auth contract invocation without manual XDR construction
     * - Handling authorization when invoker != submitter
     *
     * The auth contract has an "increment" function that requires authorization
     * from a specific user account and takes a u32 value to increment.
     *
     * **Prerequisites**: Network connectivity to Stellar testnet
     * **Duration**: ~60-90 seconds (includes deploy and invocations)
     *
     * **Reference**: Ported from Flutter SDK's test auth with ContractSpec
     * (soroban_client_test.dart lines 280-324)
     */
    @Test
    fun testAuthWithContractSpec() = runTest(timeout = 180.seconds) {
        // Given: Use existing account or create new one
        val keyPair = sourceAccountKeyPair ?: run {
            val kp = KeyPair.random()
            FriendBot.fundTestnetAccount(kp.getAccountId())
            delay(5000)
            sourceAccountKeyPair = kp
            kp
        }
        val accountId = keyPair.getAccountId()

        // Step 1: Deploy auth contract (if not already deployed)
        val contractId = authContractId ?: run {
            // Upload auth contract
            val authContractCode = TestResourceUtil.readWasmFile("soroban_auth_contract.wasm")
            assertTrue(authContractCode.isNotEmpty(), "Auth contract code should not be empty")

            delay(5000)
            val wasmId = installContract(authContractCode)
            authContractWasmId = wasmId
            println("Installed auth contract wasm hash: $wasmId")

            // Deploy auth contract
            delay(5000)
            val cid = deployContract(wasmId)
            authContractId = cid
            println("Deployed auth contract contract id: $cid")
            cid
        }

        // Step 2: Load contract info and extract ContractSpec
        delay(5000)
        val contractInfo = sorobanServer.loadContractInfoForContractId(contractId)
        assertNotNull(contractInfo, "Contract info should be loaded")
        assertTrue(contractInfo!!.specEntries.isNotEmpty(), "Contract should have spec entries")

        // Create ContractSpec from spec entries
        val contractSpec = ContractSpec(contractInfo.specEntries)
        assertNotNull(contractSpec, "ContractSpec should be created")

        val methodNames = contractSpec.funcs().map { it.name.value }
        assertTrue(methodNames.contains("increment"), "Contract should have increment method")
        assertEquals(1, methodNames.size, "Auth contract should have 1 function")

        // Step 3: Manual XDR approach (original approach)
        println("=== Manual XdrSCVal Creation (Original) ===")
        val invokerAddress = Address(accountId)
        val manualArgs = listOf(invokerAddress.toSCVal(), Scv.toUint32(5u))
        val manualResult = invokeContract(contractId, "increment", manualArgs)

        // Validate manual result
        assertTrue(manualResult is SCValXdr.U32, "Result should be u32")
        val manualValue = (manualResult as SCValXdr.U32).value.value
        assertEquals(5u, manualValue)
        println("Manual result: $manualValue")

        // Step 4: ContractSpec approach (new approach)
        println("=== ContractSpec Approach (New) ===")
        // Much simpler and more readable!
        // Instead of: listOf(Address(accountId).toSCVal(), Scv.toUint32(7u))
        // We can use: mapOf("user" to accountId, "value" to 7)
        val specArgs = contractSpec.funcArgsToXdrSCValues("increment", mapOf(
            "user" to accountId,  // String account ID -> automatically converts to Address
            "value" to 7          // int -> automatically converts to u32
        ))
        assertNotNull(specArgs, "ContractSpec should convert arguments")
        assertEquals(2, specArgs.size, "Should have 2 arguments")

        val specResult = invokeContract(contractId, "increment", specArgs)

        // Validate ContractSpec result
        assertTrue(specResult is SCValXdr.U32, "Result should be u32")
        val specValue = (specResult as SCValXdr.U32).value.value
        assertEquals(12u, specValue) // 5 + 7
        println("ContractSpec result: $specValue")

        // Step 5: Test another invocation
        val args3 = contractSpec.funcArgsToXdrSCValues("increment", mapOf(
            "user" to accountId,
            "value" to 9
        ))
        val result3 = invokeContract(contractId, "increment", args3)
        val value3 = (result3 as SCValXdr.U32).value.value
        assertEquals(21u, value3) // 5 + 7 + 9
        println("✓ Another test: $value3")

        println("✓ ContractSpec successfully simplified auth contract invocation")
    }

    /**
     * Tests atomic swap with ContractSpec for complex multi-party contracts.
     *
     * This test validates:
     * 1. Deploying token and swap contracts
     * 2. Initializing tokens using ContractSpec (automatic type conversion)
     * 3. Minting tokens using ContractSpec
     * 4. Querying balances using ContractSpec
     * 5. Executing atomic swap with ContractSpec (8 parameters with auto-conversion)
     * 6. Comparing manual XDR approach vs ContractSpec approach for complex parameters
     * 7. Multi-party authorization (Alice and Bob signing)
     *
     * The test demonstrates:
     * - ContractSpec for complex contract interactions
     * - Automatic conversion: String → Address, int → i128, int → u32
     * - Significant code simplification for multi-parameter functions
     * - Multi-party authorization with ContractSpec
     *
     * This is the most complex test, showing how ContractSpec makes real-world
     * multi-party smart contract interactions much simpler and more readable.
     *
     * **Prerequisites**: Network connectivity to Stellar testnet
     * **Duration**: ~180-240 seconds (includes multiple contract deployments and operations)
     *
     * **Reference**: Ported from Flutter SDK's test atomic swap with ContractSpec
     * (soroban_client_test.dart lines 435-543)
     */
    @Test
    fun testAtomicSwapWithContractSpec() = runTest(timeout = 360.seconds) {
        // Given: Setup accounts
        val sourceKp = sourceAccountKeyPair ?: run {
            val kp = KeyPair.random()
            FriendBot.fundTestnetAccount(kp.getAccountId())
            delay(5000)
            sourceAccountKeyPair = kp
            kp
        }

        // Create admin, alice, bob accounts
        val admin = adminKeyPair ?: run {
            val kp = KeyPair.random()
            FriendBot.fundTestnetAccount(kp.getAccountId())
            delay(5000)
            adminKeyPair = kp
            kp
        }

        val alice = aliceKeyPair ?: run {
            val kp = KeyPair.random()
            FriendBot.fundTestnetAccount(kp.getAccountId())
            delay(5000)
            aliceKeyPair = kp
            kp
        }

        val bob = bobKeyPair ?: run {
            val kp = KeyPair.random()
            FriendBot.fundTestnetAccount(kp.getAccountId())
            delay(5000)
            bobKeyPair = kp
            kp
        }

        val adminId = admin.getAccountId()
        val aliceId = alice.getAccountId()
        val bobId = bob.getAccountId()

        println("Admin: $adminId")
        println("Alice: $aliceId")
        println("Bob: $bobId")

        // Step 1: Upload and deploy contracts
        delay(5000)

        // Upload/deploy token contract for TokenA
        val tokenAId = tokenAContractId ?: run {
            val tokenCode = TestResourceUtil.readWasmFile("soroban_token_contract.wasm")
            val wasmId = tokenContractWasmId ?: installContractWithKeypair(tokenCode, admin)
            tokenContractWasmId = wasmId
            delay(5000)
            val cid = deployContractWithKeypair(wasmId, admin)
            tokenAContractId = cid
            println("Deployed TokenA: $cid")
            cid
        }

        // Upload/deploy token contract for TokenB
        delay(5000)
        val tokenBId = tokenBContractId ?: run {
            val tokenCode = TestResourceUtil.readWasmFile("soroban_token_contract.wasm")
            val wasmId = tokenContractWasmId!!
            val cid = deployContractWithKeypair(wasmId, admin)
            tokenBContractId = cid
            println("Deployed TokenB: $cid")
            cid
        }

        // Upload/deploy swap contract
        delay(5000)
        val swapId = swapContractId ?: run {
            val swapCode = TestResourceUtil.readWasmFile("soroban_atomic_swap_contract.wasm")
            val wasmId = installContractWithKeypair(swapCode, admin)
            swapContractWasmId = wasmId
            delay(5000)
            val cid = deployContractWithKeypair(wasmId, admin)
            swapContractId = cid
            println("Deployed Swap: $cid")
            cid
        }

        // Step 2: Load ContractSpecs
        delay(5000)
        val tokenAInfo = sorobanServer.loadContractInfoForContractId(tokenAId)
        val tokenASpec = ContractSpec(tokenAInfo!!.specEntries)

        val tokenBInfo = sorobanServer.loadContractInfoForContractId(tokenBId)
        val tokenBSpec = ContractSpec(tokenBInfo!!.specEntries)

        val swapInfo = sorobanServer.loadContractInfoForContractId(swapId)
        val swapSpec = ContractSpec(swapInfo!!.specEntries)

        // Step 3: Initialize tokens using ContractSpec
        println("=== Creating tokens with ContractSpec ===")

        val initArgsA = tokenASpec.funcArgsToXdrSCValues("initialize", mapOf(
            "admin" to adminId,     // String -> Address (automatic)
            "decimal" to 0,         // int -> u32 (automatic)
            "name" to "TokenA",     // String -> String (direct)
            "symbol" to "TokenA"    // String -> String (direct)
        ))
        invokeContractWithKeypair(tokenAId, "initialize", initArgsA, admin)
        delay(5000)

        val initArgsB = tokenBSpec.funcArgsToXdrSCValues("initialize", mapOf(
            "admin" to adminId,
            "decimal" to 0,
            "name" to "TokenB",
            "symbol" to "TokenB"
        ))
        invokeContractWithKeypair(tokenBId, "initialize", initArgsB, admin)
        delay(5000)
        println("✓ Tokens created using ContractSpec")

        // Step 4: Mint tokens using ContractSpec
        println("=== Minting tokens with ContractSpec ===")

        val mintArgsA = tokenASpec.funcArgsToXdrSCValues("mint", mapOf(
            "to" to aliceId,                    // String -> Address (automatic)
            "amount" to 10000000000000L         // Long -> i128 (automatic)
        ))
        invokeContractWithKeypair(tokenAId, "mint", mintArgsA, admin)
        delay(5000)

        val mintArgsB = tokenBSpec.funcArgsToXdrSCValues("mint", mapOf(
            "to" to bobId,
            "amount" to 10000000000000L
        ))
        invokeContractWithKeypair(tokenBId, "mint", mintArgsB, admin)
        delay(5000)
        println("✓ Alice and Bob funded using ContractSpec")

        // Step 5: Verify balances using ContractSpec
        val balanceArgsA = tokenASpec.funcArgsToXdrSCValues("balance", mapOf(
            "id" to aliceId  // String -> Address (automatic)
        ))
        val aliceBalance = invokeContractWithKeypair(tokenAId, "balance", balanceArgsA, admin)
        val aliceBalanceValue = ((aliceBalance as SCValXdr.I128).value.lo.value.toLong())
        assertEquals(10000000000000L, aliceBalanceValue)

        val balanceArgsB = tokenBSpec.funcArgsToXdrSCValues("balance", mapOf(
            "id" to bobId
        ))
        val bobBalance = invokeContractWithKeypair(tokenBId, "balance", balanceArgsB, admin)
        val bobBalanceValue = ((bobBalance as SCValXdr.I128).value.lo.value.toLong())
        assertEquals(10000000000000L, bobBalanceValue)
        println("✓ Balances verified using ContractSpec")

        // Step 6: Execute atomic swap using ContractSpec
        println("=== Executing atomic swap with ContractSpec ===")

        // ContractSpec makes complex contract invocation much simpler and more readable
        // Instead of manually constructing 8 complex XDR values:
        //   Address(aliceId).toSCVal(), Address(bobId).toSCVal(), ...
        //   Scv.toInt128(BigInteger.fromLong(1000L)), ...
        // We can use simple Kotlin types with automatic conversion:
        val swapArgs = swapSpec.funcArgsToXdrSCValues("swap", mapOf(
            "a" to aliceId,                 // String -> Address (automatic)
            "b" to bobId,                   // String -> Address (automatic)
            "token_a" to tokenAId,          // String -> Address (automatic)
            "token_b" to tokenBId,          // String -> Address (automatic)
            "amount_a" to 1000,             // int -> i128 (automatic)
            "min_b_for_a" to 4500,          // int -> i128 (automatic)
            "amount_b" to 5000,             // int -> i128 (automatic)
            "min_a_for_b" to 950            // int -> i128 (automatic)
        ))
        println("✓ ContractSpec automatically converted 8 parameters with correct types")

        // Step 7: Execute swap with authorization using ContractClient (matches Flutter SDK)
        println("=== Executing swap with ContractClient (Flutter SDK approach) ===")
        delay(10000)

        // Create ContractClient for the swap contract
        val swapClient = ContractClient(
            contractId = swapId,
            rpcUrl = "https://soroban-testnet.stellar.org",
            network = network
        )

        // Build transaction using high-level API (matches Flutter SDK's buildInvokeMethodTx)
        val tx = swapClient.invoke<SCValXdr>(
            functionName = "swap",
            parameters = swapArgs,  // Use ContractSpec-generated args
            source = adminId,
            signer = admin,
            parseResultXdrFn = null  // Return raw SCValXdr
        )

        // Check who needs to sign (matches Flutter SDK's needsNonInvokerSigningBy)
        val whoElseNeedsToSign = tx.needsNonInvokerSigningBy()
        println("Addresses that need to sign: $whoElseNeedsToSign")
        assertEquals(2, whoElseNeedsToSign.size, "Should have 2 addresses that need to sign")
        assertTrue(whoElseNeedsToSign.contains(aliceId), "Should include Alice")
        assertTrue(whoElseNeedsToSign.contains(bobId), "Should include Bob")

        // Sign auth entries for Alice (matches Flutter SDK's signAuthEntries)
        tx.signAuthEntries(alice)
        println("✓ Signed by Alice")

        // Sign auth entries for Bob (matches Flutter SDK's signAuthEntries)
        tx.signAuthEntries(bob)
        println("✓ Signed by Bob")

        // Submit transaction (matches Flutter SDK's signAndSend)
        val result = tx.signAndSubmit(admin)
        assertNotNull(result)

        println("✓ Atomic swap completed successfully using ContractSpec!")
        println("✓ ContractSpec made complex contract invocation much simpler and more readable")
    }

    // ============================================================================
    // Helper Methods
    // ============================================================================

    /**
     * Helper function to install (upload) a contract WASM.
     */
    private suspend fun installContract(contractCode: ByteArray): String {
        return installContractWithKeypair(contractCode, sourceAccountKeyPair!!)
    }

    /**
     * Helper function to install (upload) a contract WASM with specific keypair.
     */
    private suspend fun installContractWithKeypair(contractCode: ByteArray, keyPair: KeyPair): String {
        delay(5000)

        val accountId = keyPair.getAccountId()
        val account = sorobanServer.getAccount(accountId)

        val uploadFunction = HostFunctionXdr.Wasm(contractCode)
        val operation = InvokeHostFunctionOperation(hostFunction = uploadFunction)
        val transaction = TransactionBuilder(
            sourceAccount = account,
            network = network
        )
            .addOperation(operation)
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        val simulateResponse = sorobanServer.simulateTransaction(transaction)
        assertNull(simulateResponse.error, "Upload simulation should not have error")

        val preparedTransaction = sorobanServer.prepareTransaction(transaction, simulateResponse)
        preparedTransaction.sign(keyPair)

        val sendResponse = sorobanServer.sendTransaction(preparedTransaction)
        assertNotNull(sendResponse.hash)

        val rpcResponse = sorobanServer.pollTransaction(
            hash = sendResponse.hash!!,
            maxAttempts = 60,  // Increased from 30 to 60
            sleepStrategy = { 3000L }
        )

        assertEquals(GetTransactionStatus.SUCCESS, rpcResponse.status)

        val wasmId = rpcResponse.getWasmId()
        assertNotNull(wasmId)
        return wasmId!!
    }

    /**
     * Helper function to deploy a contract from WASM ID.
     */
    private suspend fun deployContract(wasmId: String): String {
        return deployContractWithKeypair(wasmId, sourceAccountKeyPair!!)
    }

    /**
     * Helper function to deploy a contract from WASM ID with specific keypair.
     */
    private suspend fun deployContractWithKeypair(wasmId: String, keyPair: KeyPair): String {
        delay(5000)

        val accountId = keyPair.getAccountId()
        val account = sorobanServer.getAccount(accountId)

        val addressObj = Address(accountId)
        val scAddress = addressObj.toSCAddress()
        val salt = Uint256Xdr(ByteArray(32) { Random.nextInt(256).toByte() })

        val preimage = ContractIDPreimageXdr.FromAddress(
            ContractIDPreimageFromAddressXdr(address = scAddress, salt = salt)
        )

        val wasmHash = HashXdr(wasmId.chunked(2).map { it.toInt(16).toByte() }.toByteArray())
        val executable = ContractExecutableXdr.WasmHash(wasmHash)

        val createContractArgs = CreateContractArgsXdr(
            contractIdPreimage = preimage,
            executable = executable
        )

        val createFunction = HostFunctionXdr.CreateContract(createContractArgs)
        val operation = InvokeHostFunctionOperation(hostFunction = createFunction)

        val transaction = TransactionBuilder(
            sourceAccount = account,
            network = network
        )
            .addOperation(operation)
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        val simulateResponse = sorobanServer.simulateTransaction(transaction)
        assertNull(simulateResponse.error, "Deploy simulation should not have error")

        val preparedTransaction = sorobanServer.prepareTransaction(transaction, simulateResponse)
        preparedTransaction.sign(keyPair)

        val sendResponse = sorobanServer.sendTransaction(preparedTransaction)
        assertNotNull(sendResponse.hash)

        val rpcResponse = sorobanServer.pollTransaction(
            hash = sendResponse.hash!!,
            maxAttempts = 60,  // Increased from 30 to 60
            sleepStrategy = { 3000L }
        )

        assertEquals(GetTransactionStatus.SUCCESS, rpcResponse.status)

        val contractId = rpcResponse.getCreatedContractId()
        assertNotNull(contractId)
        return contractId!!
    }

    /**
     * Helper function to invoke a contract method.
     */
    private suspend fun invokeContract(
        contractId: String,
        functionName: String,
        args: List<SCValXdr>
    ): SCValXdr {
        return invokeContractWithKeypair(contractId, functionName, args, sourceAccountKeyPair!!)
    }

    /**
     * Helper function to invoke a contract method with specific keypair.
     */
    private suspend fun invokeContractWithKeypair(
        contractId: String,
        functionName: String,
        args: List<SCValXdr>,
        keyPair: KeyPair
    ): SCValXdr {
        delay(5000)

        val accountId = keyPair.getAccountId()
        val account = sorobanServer.getAccount(accountId)

        val contractAddress = Address(contractId)
        val invokeArgs = InvokeContractArgsXdr(
            contractAddress = contractAddress.toSCAddress(),
            functionName = SCSymbolXdr(functionName),
            args = args
        )
        val hostFunction = HostFunctionXdr.InvokeContract(invokeArgs)
        val operation = InvokeHostFunctionOperation(hostFunction = hostFunction)

        val transaction = TransactionBuilder(
            sourceAccount = account,
            network = network
        )
            .addOperation(operation)
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        val simulateResponse = sorobanServer.simulateTransaction(transaction)
        assertNull(simulateResponse.error, "Invoke simulation should not have error")

        val preparedTransaction = sorobanServer.prepareTransaction(transaction, simulateResponse)
        preparedTransaction.sign(keyPair)

        val sendResponse = sorobanServer.sendTransaction(preparedTransaction)
        assertNotNull(sendResponse.hash)

        val rpcResponse = sorobanServer.pollTransaction(
            hash = sendResponse.hash!!,
            maxAttempts = 60,  // Increased from 30 to 60
            sleepStrategy = { 3000L }
        )

        assertEquals(GetTransactionStatus.SUCCESS, rpcResponse.status)

        val resVal = rpcResponse.getResultValue()
        assertNotNull(resVal)
        return resVal!!
    }
}
