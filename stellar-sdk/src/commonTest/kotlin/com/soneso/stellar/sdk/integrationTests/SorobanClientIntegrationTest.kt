package com.soneso.stellar.sdk.integrationTests

import com.soneso.stellar.sdk.*
import com.soneso.stellar.sdk.contract.ContractClient
import com.soneso.stellar.sdk.scval.Scv
import com.soneso.stellar.sdk.util.TestResourceUtil
import com.soneso.stellar.sdk.xdr.SCValXdr
import com.soneso.stellar.sdk.xdr.SorobanAuthorizationEntryXdr
import com.soneso.stellar.sdk.xdr.SorobanCredentialsXdr
import com.ionspin.kotlin.bignum.integer.BigInteger
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

/**
 * Comprehensive integration tests for Soroban smart contract operations using the high-level ContractClient API.
 *
 * These tests are ported from the Flutter Stellar SDK's soroban_client_test.dart and demonstrate:
 * - Simple contract invocation (hello contract)
 * - Authorization handling (auth contract)
 * - Complex multi-party contracts (atomic swap)
 * - Contract deployment with constructors
 * - Read vs write call auto-detection
 * - Manual result parsing with parseResultXdrFn or funcResToNative
 *
 * **Ported From**: `/Users/chris/projects/Stellar/stellar_flutter_sdk/test/soroban_client_test.dart`
 *
 * **Test Network**: Tests run against Stellar testnet with automatic account funding via FriendBot.
 *
 * ## Running Tests
 *
 * ```bash
 * # Run on JVM
 * ./gradlew :stellar-sdk:jvmTest --tests "SorobanClientIntegrationTest"
 *
 * # Run on macOS Native
 * ./gradlew :stellar-sdk:macosArm64Test --tests "SorobanClientIntegrationTest"
 *
 * # Run on JavaScript Node.js
 * ./gradlew :stellar-sdk:jsNodeTest --tests "SorobanClientIntegrationTest"
 * ```
 *
 * **Platform Support**: All platforms (JVM, macOS Native, JavaScript Node)
 *
 * @see ContractClient
 * @see com.soneso.stellar.sdk.contract.AssembledTransaction
 */
class SorobanClientIntegrationTest {

    private val testOn = "testnet" // or "futurenet"
    private val rpcUrl = if (testOn == "testnet") {
        "https://soroban-testnet.stellar.org"
    } else {
        "https://rpc-futurenet.stellar.org"
    }
    private val network = if (testOn == "testnet") {
        Network.TESTNET
    } else {
        Network.FUTURENET
    }

    /**
     * Test hello contract with high-level invoke API and manual result parsing.
     *
     * This test demonstrates:
     * 1. One-step contract deployment with deploy()
     * 2. Simple contract invocation with native types (String → SCValXdr.Str)
     * 3. Manual result parsing with funcResToNative
     * 4. Automatic read-only call detection and execution
     *
     * The hello contract has a "hello" function that takes a string parameter
     * and returns a vector with two strings: ["Hello", <parameter>].
     *
     * **Ported From**: Flutter SDK's `test hello contract`
     *
     * **Prerequisites**: Testnet connectivity
     * **Duration**: ~60-90 seconds (includes upload, deploy, and invocation)
     */
    @Test
    fun testHelloContract() = runTest(timeout = 180.seconds) {
        // Step 1: Create and fund test account
        val sourceKeyPair = KeyPair.random()
        val sourceAccountId = sourceKeyPair.getAccountId()

        if (testOn == "testnet") {
            FriendBot.fundTestnetAccount(sourceAccountId)
        } else {
            FriendBot.fundFuturenetAccount(sourceAccountId)
        }
        realDelay(5000) // Wait for account creation

        // Step 2: Deploy hello contract
        val helloContractWasm = TestResourceUtil.readWasmFile("soroban_hello_world_contract.wasm")
        assertTrue(helloContractWasm.isNotEmpty(), "Hello contract WASM should not be empty")

        val client = ContractClient.deploy(
            wasmBytes = helloContractWasm,
            constructorArgs = emptyMap(), // No constructor
            source = sourceAccountId,
            signer = sourceKeyPair,
            network = network,
            rpcUrl = rpcUrl
        )
        println("Deployed hello contract: ${client.contractId}")

        // Step 3: Verify method names
        val methodNames = client.getMethodNames()
        assertEquals(1, methodNames.size, "Should have 1 method")
        assertTrue(methodNames.contains("hello"), "Should have hello method")

        // Step 4: Invoke contract and use funcResToNative for manual parsing
        val resultXdr = client.invoke<SCValXdr>(
            functionName = "hello",
            arguments = mapOf("to" to "John"),  // String → SCValXdr.Str (automatic conversion)
            source = sourceAccountId,
            signer = null  // Read-only call
        )

        // Step 5: Parse result manually using funcResToNative
        val result = client.funcResToNative("hello", resultXdr) as List<*>

        // Step 6: Verify result
        assertEquals(2, result.size, "Result should have 2 elements")
        assertEquals("Hello", result[0], "First element should be 'Hello'")
        assertEquals("John", result[1], "Second element should be 'John'")
        println("✓ Hello contract result (manual parse): ${result.joinToString(", ")}")
    }

    /**
     * Test auth contract with high-level invoke API, manual result parsing, and authorization.
     *
     * This test demonstrates:
     * 1. Contract deployment
     * 2. Same-invoker scenario (no auth required)
     * 3. Manual result parsing with parseResultXdrFn
     * 4. Different-invoker scenario (auth required)
     * 5. Manual auth entry signing with buildInvoke
     *
     * The auth contract has an "increment" function that requires authorization
     * from a specific user account and takes a u32 value to increment.
     *
     * **Ported From**: Flutter SDK's `test auth` (lines 183-245)
     *
     * **Prerequisites**: Testnet connectivity
     * **Duration**: ~90-120 seconds
     */
    @Test
    fun testAuthContract() = runTest(timeout = 240.seconds) {
        // Step 1: Create and fund test account
        val sourceKeyPair = KeyPair.random()
        val sourceAccountId = sourceKeyPair.getAccountId()

        if (testOn == "testnet") {
            FriendBot.fundTestnetAccount(sourceAccountId)
        } else {
            FriendBot.fundFuturenetAccount(sourceAccountId)
        }
        realDelay(5000)

        // Step 2: Deploy auth contract
        val authContractWasm = TestResourceUtil.readWasmFile("soroban_auth_contract.wasm")
        assertTrue(authContractWasm.isNotEmpty(), "Auth contract WASM should not be empty")

        val client = ContractClient.deploy(
            wasmBytes = authContractWasm,
            constructorArgs = emptyMap(),
            source = sourceAccountId,
            signer = sourceKeyPair,
            network = network,
            rpcUrl = rpcUrl
        )
        println("Deployed auth contract: ${client.contractId}")

        // Step 3: Verify method names
        val methodNames = client.getMethodNames()
        assertEquals(1, methodNames.size, "Should have 1 method")
        assertTrue(methodNames.contains("increment"), "Should have increment method")

        // Step 4: Test same-invoker scenario with parseResultXdrFn
        val result1 = client.invoke<UInt>(
            functionName = "increment",
            arguments = mapOf(
                "user" to sourceAccountId,  // String → Address (automatic)
                "value" to 3                // Int → u32 (automatic)
            ),
            source = sourceAccountId,
            signer = sourceKeyPair,  // Same as user, so no extra auth needed
            parseResultXdrFn = { xdr ->
                (xdr as SCValXdr.U32).value.value
            }
        )

        assertEquals(3u, result1, "Result should be 3")
        println("✓ Same-invoker result (parseResultXdrFn): $result1")

        // Step 5: Test different-invoker scenario (auth required)
        val invokerKeyPair = KeyPair.random()
        val invokerAccountId = invokerKeyPair.getAccountId()

        if (testOn == "testnet") {
            FriendBot.fundTestnetAccount(invokerAccountId)
        } else {
            FriendBot.fundFuturenetAccount(invokerAccountId)
        }
        realDelay(5000)

        // Step 6: Attempt without auth should fail
        var thrown = false
        try {
            client.invoke<SCValXdr>(
                functionName = "increment",
                arguments = mapOf(
                    "user" to invokerAccountId,  // Different user
                    "value" to 4
                ),
                source = sourceAccountId,  // Different from user
                signer = sourceKeyPair
            )
        } catch (e: Exception) {
            thrown = true
            println("Expected error without auth: ${e.message}")
        }
        assertTrue(thrown, "Should fail without proper authorization")

        // Step 7: Use buildInvoke for manual auth handling (advanced API)
        realDelay(5000)
        val assembled = client.buildInvoke(
            functionName = "increment",
            arguments = mapOf(
                "user" to invokerAccountId,
                "value" to 4
            ),
            source = sourceAccountId,
            signer = sourceKeyPair,
            parseResultXdrFn = { xdr ->
                // Custom parsing for advanced API
                (xdr as SCValXdr.U32).value.value
            }
        )

        // Sign auth entries
        assembled.signAuthEntries(invokerKeyPair)
        val result2 = assembled.signAndSubmit(sourceKeyPair, force = false)
        assertEquals(4u, result2, "Result should be 4")
        println("✓ Different-invoker with auth result: $result2")
    }

    /**
     * Test atomic swap with high-level API, manual result parsing, and multi-party authorization.
     *
     * This test demonstrates:
     * 1. Multiple contract deployments (swap + 2 tokens)
     * 2. Token contract initialization with constructors
     * 3. Token minting with auth
     * 4. Manual result parsing for balance queries
     * 5. Complex multi-parameter contract invocation (8 parameters)
     * 6. Multi-party authorization (Alice and Bob signing)
     * 7. Transaction execution with needsNonInvokerSigningBy()
     *
     * This is the most comprehensive test showing real-world multi-party
     * smart contract interactions.
     *
     * **Ported From**: Flutter SDK's `test atomic swap` (lines 297-410)
     *
     * **Prerequisites**: Testnet connectivity
     * **Duration**: ~240-300 seconds (multiple contract deployments)
     */
    @Test
    fun testAtomicSwap() = runTest(timeout = 480.seconds) {
        // Step 1: Create and fund test accounts
        val sourceKeyPair = KeyPair.random()
        val sourceAccountId = sourceKeyPair.getAccountId()

        val adminKeyPair = KeyPair.random()
        val adminId = adminKeyPair.getAccountId()

        val aliceKeyPair = KeyPair.random()
        val aliceId = aliceKeyPair.getAccountId()

        val bobKeyPair = KeyPair.random()
        val bobId = bobKeyPair.getAccountId()

        if (testOn == "testnet") {
            FriendBot.fundTestnetAccount(sourceAccountId)
            realDelay(3000)
            FriendBot.fundTestnetAccount(adminId)
            realDelay(3000)
            FriendBot.fundTestnetAccount(aliceId)
            realDelay(3000)
            FriendBot.fundTestnetAccount(bobId)
        } else {
            FriendBot.fundFuturenetAccount(sourceAccountId)
            realDelay(3000)
            FriendBot.fundFuturenetAccount(adminId)
            realDelay(3000)
            FriendBot.fundFuturenetAccount(aliceId)
            realDelay(3000)
            FriendBot.fundFuturenetAccount(bobId)
        }
        realDelay(5000)

        println("Accounts funded:")
        println("  Source: $sourceAccountId")
        println("  Admin: $adminId")
        println("  Alice: $aliceId")
        println("  Bob: $bobId")

        // Step 2: Deploy atomic swap contract
        val swapContractWasm = TestResourceUtil.readWasmFile("soroban_atomic_swap_contract.wasm")
        val swapClient = ContractClient.deploy(
            wasmBytes = swapContractWasm,
            constructorArgs = emptyMap(),
            source = sourceAccountId,
            signer = sourceKeyPair,
            network = network,
            rpcUrl = rpcUrl
        )
        println("Deployed swap contract: ${swapClient.contractId}")

        // Step 3: Deploy token A with constructor
        realDelay(5000)
        val tokenContractWasm = TestResourceUtil.readWasmFile("soroban_token_contract.wasm")
        val tokenAClient = ContractClient.deploy(
            wasmBytes = tokenContractWasm,
            constructorArgs = mapOf(
                "admin" to adminId,
                "decimal" to 8,
                "name" to "TokenA",
                "symbol" to "TokenA"
            ),
            source = adminId,
            signer = adminKeyPair,
            network = network,
            rpcUrl = rpcUrl
        )
        println("Deployed TokenA: ${tokenAClient.contractId}")

        // Step 4: Deploy token B with constructor
        realDelay(5000)
        val tokenBClient = ContractClient.deploy(
            wasmBytes = tokenContractWasm,
            constructorArgs = mapOf(
                "admin" to adminId,
                "decimal" to 8,
                "name" to "TokenB",
                "symbol" to "TokenB"
            ),
            source = adminId,
            signer = adminKeyPair,
            network = network,
            rpcUrl = rpcUrl
        )
        println("Deployed TokenB: ${tokenBClient.contractId}")

        // Step 5: Mint tokens to Alice and Bob
        realDelay(5000)
        tokenAClient.invoke<Unit>(
            functionName = "mint",
            arguments = mapOf(
                "to" to aliceId,
                "amount" to 10000000000000L
            ),
            source = adminId,
            signer = adminKeyPair
        )
        println("✓ Minted TokenA to Alice")

        realDelay(5000)
        tokenBClient.invoke<Unit>(
            functionName = "mint",
            arguments = mapOf(
                "to" to bobId,
                "amount" to 10000000000000L
            ),
            source = adminId,
            signer = adminKeyPair
        )
        println("✓ Minted TokenB to Bob")

        // Step 6: Verify balances with manual parsing using funcResToNative
        realDelay(5000)
        val aliceBalanceXdr = tokenAClient.invoke<SCValXdr>(
            functionName = "balance",
            arguments = mapOf("id" to aliceId),
            source = adminId,
            signer = null  // Read-only
        )
        val aliceBalance = tokenAClient.funcResToNative("balance", aliceBalanceXdr) as BigInteger

        assertEquals(BigInteger.fromLong(10000000000000L), aliceBalance, "Alice should have 10000000000000 TokenA")
        println("✓ Alice balance (funcResToNative): $aliceBalance TokenA")

        val bobBalanceXdr = tokenBClient.invoke<SCValXdr>(
            functionName = "balance",
            arguments = mapOf("id" to bobId),
            source = adminId,
            signer = null
        )
        val bobBalance = tokenBClient.funcResToNative("balance", bobBalanceXdr) as BigInteger

        assertEquals(BigInteger.fromLong(10000000000000L), bobBalance, "Bob should have 10000000000000 TokenB")
        println("✓ Bob balance (funcResToNative): $bobBalance TokenB")

        // Step 7: Execute atomic swap
        realDelay(10000)

        // Use buildInvoke for manual control over auth
        val swapTx = swapClient.buildInvoke<SCValXdr>(
            functionName = "swap",
            arguments = mapOf(
                "a" to aliceId,
                "b" to bobId,
                "token_a" to tokenAClient.contractId,
                "token_b" to tokenBClient.contractId,
                "amount_a" to 1000,
                "min_b_for_a" to 4500,
                "amount_b" to 5000,
                "min_a_for_b" to 950
            ),
            source = sourceAccountId,
            signer = sourceKeyPair,
            parseResultXdrFn = null  // Return raw XDR
        )

        // Step 8: Check who needs to sign
        val whoElseNeedsToSign = swapTx.needsNonInvokerSigningBy()
        println("Addresses that need to sign: $whoElseNeedsToSign")
        assertEquals(2, whoElseNeedsToSign.size, "Should need 2 signatures")
        assertTrue(whoElseNeedsToSign.contains(aliceId), "Should include Alice")
        assertTrue(whoElseNeedsToSign.contains(bobId), "Should include Bob")

        // Step 9: Sign auth entries
        swapTx.signAuthEntries(aliceKeyPair)
        println("✓ Signed by Alice")

        swapTx.signAuthEntries(bobKeyPair)
        println("✓ Signed by Bob")

        // Step 10: Submit transaction
        val result = swapTx.signAndSubmit(sourceKeyPair, force = false)
        assertNotNull(result)
        println("✓ Atomic swap completed successfully!")
    }

    /**
     * Test auth contract with delegate signing pattern.
     *
     * This test demonstrates:
     * 1. Authorization with a separate signing delegate
     * 2. Manual auth entry construction and signing
     * 3. Integration with external signing services
     *
     * This pattern is useful when the signer is on a different device
     * or signing server (e.g., hardware wallet, HSM, remote API).
     *
     * **Ported From**: Flutter SDK's `test auth` with delegate (lines 382-402)
     *
     * **Prerequisites**: Testnet connectivity
     * **Duration**: ~120-150 seconds
     */
    @Test
    fun testAuthWithDelegate() = runTest(timeout = 240.seconds) {
        // Step 1: Create and fund test accounts
        val sourceKeyPair = KeyPair.random()
        val sourceAccountId = sourceKeyPair.getAccountId()

        val invokerKeyPair = KeyPair.random()
        val invokerAccountId = invokerKeyPair.getAccountId()

        if (testOn == "testnet") {
            FriendBot.fundTestnetAccount(sourceAccountId)
            realDelay(3000)
            FriendBot.fundTestnetAccount(invokerAccountId)
        } else {
            FriendBot.fundFuturenetAccount(sourceAccountId)
            realDelay(3000)
            FriendBot.fundFuturenetAccount(invokerAccountId)
        }
        realDelay(5000)

        // Step 2: Deploy auth contract
        val authContractWasm = TestResourceUtil.readWasmFile("soroban_auth_contract.wasm")
        val client = ContractClient.deploy(
            wasmBytes = authContractWasm,
            constructorArgs = emptyMap(),
            source = sourceAccountId,
            signer = sourceKeyPair,
            network = network,
            rpcUrl = rpcUrl
        )
        println("Deployed auth contract: ${client.contractId}")

        // Step 3: Build transaction with invoker as different user
        realDelay(5000)
        val assembled = client.buildInvoke(
            functionName = "increment",
            arguments = mapOf(
                "user" to invokerAccountId,
                "value" to 5
            ),
            source = sourceAccountId,
            signer = sourceKeyPair,
            parseResultXdrFn = { xdr ->
                (xdr as SCValXdr.U32).value.value
            }
        )

        // Step 4: Sign with delegate pattern
        // Create public-only KeyPair (simulates remote signer scenario)
        val invokerPublicKeyPair = KeyPair.fromAccountId(invokerAccountId)

        // Use the delegate function to simulate remote signing
        assembled.signAuthEntries(
            authEntriesSigner = invokerPublicKeyPair,
            authorizeEntryDelegate = { entry: SorobanAuthorizationEntryXdr, net: Network ->
                // This delegate simulates sending the entry to a remote server for signing
                println("Delegate called for signing entry")

                // In a real scenario, you would:
                // 1. Encode the entry as XDR: val entryXdr = entry.toXdrBase64()
                // 2. Send to remote server: val signedXdr = httpClient.post("/sign", entryXdr)
                // 3. Decode and return: SorobanAuthorizationEntryXdr.fromXdrBase64(signedXdr)

                // For testing, we simulate the remote server signing it:
                // Extract the expiration ledger from the entry
                val addressCreds = (entry.credentials as? SorobanCredentialsXdr.Address)?.value
                val expirationLedger = addressCreds?.signatureExpirationLedger?.value?.toLong()
                    ?: throw IllegalStateException("No expiration ledger in entry")

                // Sign the entry with the actual private key (simulating remote server)
                Auth.authorizeEntry(entry, invokerKeyPair, expirationLedger, net)
            }
        )
        println("✓ Signed by invoker via delegate")

        // Step 5: Submit transaction
        val result = assembled.signAndSubmit(sourceKeyPair, force = false)
        assertEquals(5u, result, "Result should be 5")
        println("✓ Auth with delegate result: $result")
    }

    /**
     * Test two-step deployment (install + deploy) for WASM reuse.
     *
     * This test demonstrates:
     * 1. WASM upload with install()
     * 2. Multiple contract deployments from same WASM with deployFromWasmId()
     * 3. Fee and time savings from WASM reuse
     * 4. Manual XDR constructor arguments
     * 5. Manual result parsing for token name queries
     *
     * This pattern is useful for deploying multiple instances of the same
     * contract (e.g., token factory, multi-tenant applications).
     *
     * **Ported From**: Flutter SDK's deployment pattern (lines 31-52)
     *
     * **Prerequisites**: Testnet connectivity
     * **Duration**: ~120-150 seconds
     */
    @Test
    fun testTwoStepDeployment() = runTest(timeout = 240.seconds) {
        // Step 1: Create and fund test account
        val sourceKeyPair = KeyPair.random()
        val sourceAccountId = sourceKeyPair.getAccountId()

        if (testOn == "testnet") {
            FriendBot.fundTestnetAccount(sourceAccountId)
        } else {
            FriendBot.fundFuturenetAccount(sourceAccountId)
        }
        realDelay(5000)

        // Step 2: Install WASM once
        val tokenContractWasm = TestResourceUtil.readWasmFile("soroban_token_contract.wasm")
        val wasmId = ContractClient.install(
            wasmBytes = tokenContractWasm,
            source = sourceAccountId,
            signer = sourceKeyPair,
            network = network,
            rpcUrl = rpcUrl
        )
        println("Installed WASM ID: $wasmId")

        // Step 3: Deploy first instance with constructor
        realDelay(5000)
        val token1 = ContractClient.deployFromWasmId(
            wasmId = wasmId,
            constructorArgs = listOf(
                Scv.toAddress(Address(sourceAccountId).toSCAddress()),  // admin (SCAddressXdr)
                Scv.toUint32(7u),                                       // decimal
                Scv.toString("Token1"),                                 // name
                Scv.toString("TK1")                                     // symbol
            ),
            source = sourceAccountId,
            signer = sourceKeyPair,
            network = network,
            rpcUrl = rpcUrl
        )
        println("Deployed Token1: ${token1.contractId}")

        // Step 4: Deploy second instance with different constructor args
        realDelay(5000)
        val token2 = ContractClient.deployFromWasmId(
            wasmId = wasmId,
            constructorArgs = listOf(
                Scv.toAddress(Address(sourceAccountId).toSCAddress()),
                Scv.toUint32(7u),
                Scv.toString("Token2"),
                Scv.toString("TK2")
            ),
            source = sourceAccountId,
            signer = sourceKeyPair,
            network = network,
            rpcUrl = rpcUrl
        )
        println("Deployed Token2: ${token2.contractId}")

        // Step 5: Verify both contracts are different and functional
        assertNotEquals(token1.contractId, token2.contractId, "Contract IDs should be different")

        // Verify Token1 name with parseResultXdrFn
        realDelay(5000)
        val token1Name = token1.invoke<String>(
            functionName = "name",
            arguments = emptyMap(),
            source = sourceAccountId,
            signer = null,
            parseResultXdrFn = { xdr ->
                (xdr as SCValXdr.Str).value.value
            }
        )

        assertEquals("Token1", token1Name, "Token1 name should be 'Token1'")
        println("✓ Token1 name (parseResultXdrFn): $token1Name")

        // Verify Token2 name with funcResToNative
        realDelay(5000)
        val token2NameXdr = token2.invoke<SCValXdr>(
            functionName = "name",
            arguments = emptyMap(),
            source = sourceAccountId,
            signer = null
        )
        val token2Name = token2.funcResToNative("name", token2NameXdr) as String

        assertEquals("Token2", token2Name, "Token2 name should be 'Token2'")
        println("✓ Token2 name (funcResToNative): $token2Name")

        println("✓ Two-step deployment successful - WASM reused for 2 contracts")
    }

    /**
     * Test buildInvoke with memo.
     *
     * This test demonstrates:
     * 1. Using buildInvoke() to get transaction control
     * 2. Adding a memo before signing
     * 3. Signing and submitting the customized transaction
     */
    @Test
    fun testBuildInvokeWithMemo() = runTest(timeout = 180.seconds) {
        // Step 1: Create and fund test account
        val sourceKeyPair = KeyPair.random()
        val sourceAccountId = sourceKeyPair.getAccountId()

        if (testOn == "testnet") {
            FriendBot.fundTestnetAccount(sourceAccountId)
        } else {
            FriendBot.fundFuturenetAccount(sourceAccountId)
        }
        realDelay(5000)

        // Step 2: Deploy hello contract
        val helloContractWasm = TestResourceUtil.readWasmFile("soroban_hello_world_contract.wasm")
        val client = ContractClient.deploy(
            wasmBytes = helloContractWasm,
            constructorArgs = emptyMap(),
            source = sourceAccountId,
            signer = sourceKeyPair,
            network = network,
            rpcUrl = rpcUrl
        )
        println("Deployed hello contract: ${client.contractId}")

        // Step 3: Build transaction using buildInvoke
        realDelay(5000)
        val tx = client.buildInvoke<List<String>>(
            functionName = "hello",
            arguments = mapOf("to" to "World"),
            source = sourceAccountId,
            signer = sourceKeyPair,
            parseResultXdrFn = { result ->
                when (result) {
                    is SCValXdr.Vec -> {
                        result.value?.value?.map { scVal ->
                            when (scVal) {
                                is SCValXdr.Sym -> scVal.value.value
                                else -> ""
                            }
                        }?.joinToString("") ?: ""
                    }
                    else -> ""
                }.let { listOf(it) }
            }
        )

        // Step 4: Add memo before signing
        tx.raw?.addMemo(MemoText("Test buildInvoke"))

        // Step 5: Get result (read-only call doesn't need signing)
        val result = tx.result()
        assertNotNull(result)
        assertTrue(result.isNotEmpty())
        println("✓ buildInvoke with memo completed")
    }

    /**
     * Test buildInvoke simulation inspection.
     *
     * This test demonstrates:
     * 1. Using buildInvoke() without signing
     * 2. Inspecting simulation data
     * 3. Getting result without submitting transaction
     */
    @Test
    fun testBuildInvokeSimulationInspection() = runTest(timeout = 180.seconds) {
        // Step 1: Create and fund test account
        val sourceKeyPair = KeyPair.random()
        val sourceAccountId = sourceKeyPair.getAccountId()

        if (testOn == "testnet") {
            FriendBot.fundTestnetAccount(sourceAccountId)
        } else {
            FriendBot.fundFuturenetAccount(sourceAccountId)
        }
        realDelay(5000)

        // Step 2: Deploy hello contract
        val helloContractWasm = TestResourceUtil.readWasmFile("soroban_hello_world_contract.wasm")
        val client = ContractClient.deploy(
            wasmBytes = helloContractWasm,
            constructorArgs = emptyMap(),
            source = sourceAccountId,
            signer = sourceKeyPair,
            network = network,
            rpcUrl = rpcUrl
        )
        println("Deployed hello contract: ${client.contractId}")

        // Step 3: Build transaction for inspection
        realDelay(5000)
        val tx = client.buildInvoke<List<String>>(
            functionName = "hello",
            arguments = mapOf("to" to "World"),
            source = sourceAccountId,
            signer = null,
            parseResultXdrFn = { result ->
                when (result) {
                    is SCValXdr.Vec -> {
                        result.value?.value?.map { scVal ->
                            when (scVal) {
                                is SCValXdr.Sym -> scVal.value.value
                                else -> ""
                            }
                        }?.joinToString("") ?: ""
                    }
                    else -> ""
                }.let { listOf(it) }
            }
        )

        // Step 4: Transaction should be simulated (default behavior)
        assertNotNull(tx.getSimulationData())

        // Step 5: Should be a read call (no auth entries)
        assertTrue(tx.isReadCall())

        // Step 6: Get result without submitting
        val result = tx.result()
        assertNotNull(result)
        println("✓ Simulation inspection completed")
    }

    /**
     * Test buildInvoke transaction control.
     *
     * This test demonstrates:
     * 1. Full transaction control workflow
     * 2. Accessing transaction data
     * 3. Customizing transaction before submission
     */
    @Test
    fun testBuildInvokeTransactionControl() = runTest(timeout = 180.seconds) {
        // Step 1: Create and fund test account
        val sourceKeyPair = KeyPair.random()
        val sourceAccountId = sourceKeyPair.getAccountId()

        if (testOn == "testnet") {
            FriendBot.fundTestnetAccount(sourceAccountId)
        } else {
            FriendBot.fundFuturenetAccount(sourceAccountId)
        }
        realDelay(5000)

        // Step 2: Deploy hello contract
        val helloContractWasm = TestResourceUtil.readWasmFile("soroban_hello_world_contract.wasm")
        val client = ContractClient.deploy(
            wasmBytes = helloContractWasm,
            constructorArgs = emptyMap(),
            source = sourceAccountId,
            signer = sourceKeyPair,
            network = network,
            rpcUrl = rpcUrl
        )
        println("Deployed hello contract: ${client.contractId}")

        // Step 3: Build transaction
        realDelay(5000)
        val tx = client.buildInvoke<List<String>>(
            functionName = "hello",
            arguments = mapOf("to" to "BuildInvoke"),
            source = sourceAccountId,
            signer = sourceKeyPair,
            parseResultXdrFn = { result ->
                when (result) {
                    is SCValXdr.Vec -> {
                        result.value?.value?.map { scVal ->
                            when (scVal) {
                                is SCValXdr.Sym -> scVal.value.value
                                else -> ""
                            }
                        }?.joinToString("") ?: ""
                    }
                    else -> ""
                }.let { listOf(it) }
            }
        )

        // Step 4: Verify we can access transaction data
        assertNotNull(tx.raw)
        assertNotNull(tx.getSimulationData())

        // Step 5: Customize transaction
        tx.raw?.addMemo(MemoText("Control test"))

        // Step 6: Get result (read-only call doesn't need signing)
        val result = tx.result()
        assertNotNull(result)
        assertTrue(result.isNotEmpty())
        println("✓ Transaction control completed")
    }
}
