package com.soneso.stellar.sdk.integrationTests

import com.soneso.stellar.sdk.*
import com.soneso.stellar.sdk.contract.ContractClient
import com.soneso.stellar.sdk.scval.Scv
import com.soneso.stellar.sdk.util.TestResourceUtil
import com.soneso.stellar.sdk.xdr.SCValXdr
import com.soneso.stellar.sdk.xdr.SorobanAuthorizationEntryXdr
import com.soneso.stellar.sdk.xdr.SorobanCredentialsXdr
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

/**
 * Comprehensive integration tests for Soroban smart contract operations using the new high-level ContractClient API.
 *
 * These tests are ported from the Flutter Stellar SDK's soroban_client_test.dart and demonstrate:
 * - Simple contract invocation (hello contract)
 * - Authorization handling (auth contract)
 * - Complex multi-party contracts (atomic swap)
 * - Contract deployment with constructors
 * - Read vs write call auto-detection
 * - Custom result parsing
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
     * Test hello contract with high-level invoke API.
     *
     * This test demonstrates:
     * 1. One-step contract deployment with deploy()
     * 2. Simple contract invocation with native types (String → SCValXdr.Str)
     * 3. Custom result parsing from XDR
     * 4. Automatic read-only call detection and execution
     *
     * The hello contract has a "hello" function that takes a string parameter
     * and returns a vector with two string: ["Hello", <parameter>].
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
        delay(5000) // Wait for account creation

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

        // Step 4: Invoke contract with high-level API
        val result = client.invoke(
            functionName = "hello",
            arguments = mapOf("to" to "John"),  // String → SCValXdr.Str (automatic conversion)
            source = sourceAccountId,
            signer = null,  // Read-only call
            parseResultXdrFn = { xdr ->
                // Parse Vec<String> result
                val vec = (xdr as SCValXdr.Vec).value?.value
                    ?: throw IllegalStateException("Expected Vec result")
                vec.map { element ->
                    (element as SCValXdr.Str).value.value
                }
            }
        )

        // Step 5: Verify result
        assertEquals(2, result.size, "Result should have 2 elements")
        assertEquals("Hello", result[0], "First element should be 'Hello'")
        assertEquals("John", result[1], "Second element should be 'John'")
        println("✓ Hello contract result: ${result.joinToString(", ")}")
    }

    /**
     * Test auth contract with high-level invoke API and authorization.
     *
     * This test demonstrates:
     * 1. Contract deployment
     * 2. Same-invoker scenario (no auth required)
     * 3. Different-invoker scenario (auth required)
     * 4. Manual auth entry signing with invokeWithXdr
     * 5. Custom result parsing (u32)
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
        delay(5000)

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

        // Step 4: Test same-invoker scenario (no auth required)
        val result1 = client.invoke(
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
        println("✓ Same-invoker result: $result1")

        // Step 5: Test different-invoker scenario (auth required)
        val invokerKeyPair = KeyPair.random()
        val invokerAccountId = invokerKeyPair.getAccountId()

        if (testOn == "testnet") {
            FriendBot.fundTestnetAccount(invokerAccountId)
        } else {
            FriendBot.fundFuturenetAccount(invokerAccountId)
        }
        delay(5000)

        // Step 6: Attempt without auth should fail
        var thrown = false
        try {
            client.invoke(
                functionName = "increment",
                arguments = mapOf(
                    "user" to invokerAccountId,  // Different user
                    "value" to 4
                ),
                source = sourceAccountId,  // Different from user
                signer = sourceKeyPair,
                parseResultXdrFn = { xdr ->
                    (xdr as SCValXdr.U32).value.value
                }
            )
        } catch (e: Exception) {
            thrown = true
            println("Expected error without auth: ${e.message}")
        }
        assertTrue(thrown, "Should fail without proper authorization")

        // Step 7: Use invokeWithXdr for manual auth handling
        delay(5000)
        val args = client.funcArgsToXdrSCValues(
            "increment",
            mapOf(
                "user" to invokerAccountId,
                "value" to 4
            )
        )

        val assembled = client.invokeWithXdr(
            functionName = "increment",
            parameters = args,
            source = sourceAccountId,
            signer = sourceKeyPair,
            parseResultXdrFn = { xdr ->
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
     * Test atomic swap with high-level API and multi-party authorization.
     *
     * This test demonstrates:
     * 1. Multiple contract deployments (swap + 2 tokens)
     * 2. Token contract initialization with constructors
     * 3. Token minting with auth
     * 4. Balance queries
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
            delay(3000)
            FriendBot.fundTestnetAccount(adminId)
            delay(3000)
            FriendBot.fundTestnetAccount(aliceId)
            delay(3000)
            FriendBot.fundTestnetAccount(bobId)
        } else {
            FriendBot.fundFuturenetAccount(sourceAccountId)
            delay(3000)
            FriendBot.fundFuturenetAccount(adminId)
            delay(3000)
            FriendBot.fundFuturenetAccount(aliceId)
            delay(3000)
            FriendBot.fundFuturenetAccount(bobId)
        }
        delay(5000)

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
        delay(5000)
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
        delay(5000)
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
        delay(5000)
        tokenAClient.invoke<Unit>(
            functionName = "mint",
            arguments = mapOf(
                "to" to aliceId,
                "amount" to 10000000000000L
            ),
            source = adminId,
            signer = adminKeyPair,
            parseResultXdrFn = null
        )
        println("✓ Minted TokenA to Alice")

        delay(5000)
        tokenBClient.invoke<Unit>(
            functionName = "mint",
            arguments = mapOf(
                "to" to bobId,
                "amount" to 10000000000000L
            ),
            source = adminId,
            signer = adminKeyPair,
            parseResultXdrFn = null
        )
        println("✓ Minted TokenB to Bob")

        // Step 6: Verify balances
        delay(5000)
        val aliceBalance = tokenAClient.invoke(
            functionName = "balance",
            arguments = mapOf("id" to aliceId),
            source = adminId,
            signer = null,  // Read-only
            parseResultXdrFn = { xdr ->
                // i128 to Long - access lo field directly for small values
                (xdr as SCValXdr.I128).value.lo.value.toLong()
            }
        )
        assertEquals(10000000000000L, aliceBalance, "Alice should have 10000000000000 TokenA")
        println("✓ Alice balance: $aliceBalance TokenA")

        val bobBalance = tokenBClient.invoke(
            functionName = "balance",
            arguments = mapOf("id" to bobId),
            source = adminId,
            signer = null,
            parseResultXdrFn = { xdr ->
                // i128 to Long - access lo field directly for small values
                (xdr as SCValXdr.I128).value.lo.value.toLong()
            }
        )
        assertEquals(10000000000000L, bobBalance, "Bob should have 10000000000000 TokenB")
        println("✓ Bob balance: $bobBalance TokenB")

        // Step 7: Execute atomic swap
        delay(10000)

        // Use invokeWithXdr for manual control over auth
        val swapArgs = swapClient.funcArgsToXdrSCValues(
            "swap",
            mapOf(
                "a" to aliceId,
                "b" to bobId,
                "token_a" to tokenAClient.contractId,
                "token_b" to tokenBClient.contractId,
                "amount_a" to 1000,
                "min_b_for_a" to 4500,
                "amount_b" to 5000,
                "min_a_for_b" to 950
            )
        )

        val swapTx = swapClient.invokeWithXdr<SCValXdr>(
            functionName = "swap",
            parameters = swapArgs,
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
            delay(3000)
            FriendBot.fundTestnetAccount(invokerAccountId)
        } else {
            FriendBot.fundFuturenetAccount(sourceAccountId)
            delay(3000)
            FriendBot.fundFuturenetAccount(invokerAccountId)
        }
        delay(5000)

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
        delay(5000)
        val args = client.funcArgsToXdrSCValues(
            "increment",
            mapOf(
                "user" to invokerAccountId,
                "value" to 5
            )
        )

        val assembled = client.invokeWithXdr(
            functionName = "increment",
            parameters = args,
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
        delay(5000)

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
        delay(5000)
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
        delay(5000)
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

        // Verify Token1 name
        delay(5000)
        val token1Name = token1.invoke(
            functionName = "name",
            arguments = emptyMap(),
            source = sourceAccountId,
            signer = null,
            parseResultXdrFn = { xdr ->
                Scv.fromString(xdr)
            }
        )
        assertEquals("Token1", token1Name, "Token1 name should be 'Token1'")
        println("✓ Token1 name: $token1Name")

        // Verify Token2 name
        delay(5000)
        val token2Name = token2.invoke(
            functionName = "name",
            arguments = emptyMap(),
            source = sourceAccountId,
            signer = null,
            parseResultXdrFn = { xdr ->
                Scv.fromString(xdr)
            }
        )
        assertEquals("Token2", token2Name, "Token2 name should be 'Token2'")
        println("✓ Token2 name: $token2Name")

        println("✓ Two-step deployment successful - WASM reused for 2 contracts")
    }
}
