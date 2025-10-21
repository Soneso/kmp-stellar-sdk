package com.soneso.demo.stellar

import com.soneso.stellar.sdk.KeyPair
import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.contract.ContractClient
import com.soneso.stellar.sdk.xdr.SCValXdr

/**
 * Result type for hello world contract invocation operations.
 *
 * This sealed class represents the outcome of invoking the hello world contract,
 * providing either a successful greeting message or detailed error information.
 */
sealed class InvokeHelloWorldResult {
    /**
     * Successful contract invocation with the greeting message.
     *
     * @property greeting The greeting message returned by the contract
     */
    data class Success(
        val greeting: String
    ) : InvokeHelloWorldResult()

    /**
     * Failed contract invocation with error details.
     *
     * @property message Human-readable error message
     * @property exception The underlying exception, if any
     */
    data class Error(
        val message: String,
        val exception: Throwable? = null
    ) : InvokeHelloWorldResult()
}

/**
 * Invokes the "hello" function on a deployed hello world contract using the SDK's ContractClient.
 *
 * This function demonstrates the high-level contract invocation API from the Stellar SDK.
 * It showcases how to:
 * - Initialize ContractClient from the network using a contract ID
 * - Use the beginner-friendly invoke() API with Map-based arguments
 * - Handle automatic type conversion from Kotlin types to Soroban XDR types
 * - Parse results using the contract spec with funcResToNative()
 *
 * ## ContractClient API - Beginner Mode
 *
 * The SDK provides two modes for contract interaction:
 * 1. **Beginner API** (used here): `invoke()` with Map<String, Any?> arguments
 * 2. **Power API**: `invokeWithXdr()` with manual XDR control
 *
 * This demo uses the beginner API for simplicity and educational value.
 *
 * ## How It Works
 *
 * 1. **Load Contract Spec**: ContractClient.fromNetwork() loads the contract specification
 *    from the network, which enables automatic type conversion
 *
 * 2. **Invoke Function**: The invoke() method accepts a Map where:
 *    - Keys are parameter names (must match the contract function signature)
 *    - Values are native Kotlin types (String, Int, Boolean, etc.)
 *    - The SDK automatically converts these to Soroban XDR types
 *
 * 3. **Auto-Execution**:
 *    - Read-only calls (signer = null): SDK simulates and returns result immediately
 *    - Write calls (signer provided): SDK simulates, signs, submits, and polls for result
 *
 * 4. **Result Parsing with funcResToNative()**: The SDK provides automatic result parsing
 *    using the contract specification. Instead of manually casting XDR types, we use
 *    `funcResToNative()` which:
 *    - Uses the contract spec to determine the expected return type
 *    - Automatically converts XDR to native Kotlin types
 *    - Handles complex types (vectors, maps, structs) automatically
 *    - Provides cleaner, more maintainable code
 *
 * ## Automatic Result Parsing (New Approach)
 *
 * The hello contract returns a `Vec<String>` in XDR format. Instead of manually parsing:
 *
 * ```kotlin
 * // Old approach - manual parsing (verbose and error-prone)
 * parseResultXdrFn = { xdr ->
 *     val vec = (xdr as SCValXdr.Vec).value?.value
 *         ?: throw IllegalStateException("Expected Vec result")
 *     vec.map { element ->
 *         (element as SCValXdr.Str).value.value
 *     }.joinToString(" ")
 * }
 * ```
 *
 * We now use `funcResToNative()` for automatic conversion:
 *
 * ```kotlin
 * // New approach - spec-based parsing (clean and type-safe)
 * val resultXdr = client.invoke<SCValXdr>(...)  // Get raw XDR
 * val result = client.funcResToNative("hello", resultXdr) as? List<*>  // Auto-convert
 * val greetings = result?.map { it.toString() } ?: emptyList()
 * ```
 *
 * **Benefits of funcResToNative()**:
 * - Uses contract specification for accurate type mapping
 * - Reduces boilerplate parsing code
 * - Less error-prone (no manual XDR casting)
 * - Easier to maintain and understand
 * - Consistent with SDK best practices
 *
 * ## Type Conversion Examples
 *
 * The SDK automatically handles type conversion for input arguments:
 * - `String` → `SCValXdr.Str` or `SCValXdr.Symbol` (based on contract spec)
 * - `Int` → `SCValXdr.U32`, `SCValXdr.I32`, etc. (based on contract spec)
 * - `Boolean` → `SCValXdr.Bool`
 * - `String` (G... format) → `SCAddressXdr` (Stellar address)
 * - `ByteArray` → `SCValXdr.Bytes`
 * - `List<T>` → `SCValXdr.Vec`
 * - `Map<K, V>` → `SCValXdr.Map`
 *
 * And for output with `funcResToNative()`:
 * - `SCValXdr.Str` → `String`
 * - `SCValXdr.U32` → `UInt` or `Long`
 * - `SCValXdr.Vec` → `List<*>`
 * - `SCValXdr.Bool` → `Boolean`
 * - `SCValXdr.Map` → `Map<*, *>`
 *
 * ## Usage Example
 *
 * ```kotlin
 * val result = invokeHelloWorldContract(
 *     contractId = "CA3D5KRYM6CB7OWQ6TWYRR3Z4T7GNZLKERYNZGGA5SOAOPIFY6YQGAXE",
 *     to = "Alice",
 *     submitterAccountId = "GXYZ...",
 *     secretKey = "SXYZ...",
 *     useTestnet = true
 * )
 *
 * when (result) {
 *     is InvokeHelloWorldResult.Success -> {
 *         println("Contract says: ${result.greeting}")
 *         // Output: "Hello Alice"
 *     }
 *     is InvokeHelloWorldResult.Error -> {
 *         println("Error: ${result.message}")
 *     }
 * }
 * ```
 *
 * @param contractId The deployed contract ID (C... format, 56 characters)
 * @param to The name to greet (parameter for the hello function)
 * @param submitterAccountId The account ID that will submit the transaction (G... format)
 * @param secretKey The submitter's secret key for signing (S... format)
 * @param useTestnet If true, connects to testnet; otherwise connects to mainnet (default: true)
 * @return InvokeHelloWorldResult.Success with greeting if invocation succeeded, InvokeHelloWorldResult.Error if it failed
 *
 * @see ContractClient.fromNetwork
 * @see ContractClient.invoke
 * @see ContractClient.funcResToNative
 * @see <a href="https://developers.stellar.org/docs/smart-contracts/guides/dapps/initialization">Contract Invocation Guide</a>
 */
suspend fun invokeHelloWorldContract(
    contractId: String,
    to: String,
    submitterAccountId: String,
    secretKey: String,
    useTestnet: Boolean = true
): InvokeHelloWorldResult {
    return try {
        // Step 1: Validate inputs
        if (contractId.isBlank()) {
            return InvokeHelloWorldResult.Error(
                message = "Contract ID cannot be empty"
            )
        }

        if (!contractId.startsWith('C')) {
            return InvokeHelloWorldResult.Error(
                message = "Contract ID must start with 'C' (got: ${contractId.take(1)})"
            )
        }

        if (contractId.length != 56) {
            return InvokeHelloWorldResult.Error(
                message = "Contract ID must be exactly 56 characters long (got: ${contractId.length})"
            )
        }

        if (to.isBlank()) {
            return InvokeHelloWorldResult.Error(
                message = "Name parameter 'to' cannot be empty"
            )
        }

        if (submitterAccountId.isBlank()) {
            return InvokeHelloWorldResult.Error(
                message = "Submitter account ID cannot be empty"
            )
        }

        if (!submitterAccountId.startsWith('G')) {
            return InvokeHelloWorldResult.Error(
                message = "Submitter account ID must start with 'G' (got: ${submitterAccountId.take(1)})"
            )
        }

        if (submitterAccountId.length != 56) {
            return InvokeHelloWorldResult.Error(
                message = "Submitter account ID must be exactly 56 characters long (got: ${submitterAccountId.length})"
            )
        }

        if (secretKey.isBlank()) {
            return InvokeHelloWorldResult.Error(
                message = "Secret key cannot be empty"
            )
        }

        if (!secretKey.startsWith('S')) {
            return InvokeHelloWorldResult.Error(
                message = "Secret key must start with 'S' (got: ${secretKey.take(1)})"
            )
        }

        // Step 2: Create KeyPair from secret seed
        val signerKeyPair = try {
            KeyPair.fromSecretSeed(secretKey)
        } catch (e: Exception) {
            return InvokeHelloWorldResult.Error(
                message = "Invalid secret key: ${e.message}",
                exception = e
            )
        }

        // Step 3: Determine network and RPC URL
        val network = if (useTestnet) Network.TESTNET else Network.PUBLIC
        val rpcUrl = if (useTestnet) {
            "https://soroban-testnet.stellar.org:443"
        } else {
            "https://soroban-mainnet.stellar.org:443"
        }

        // Step 4: Initialize ContractClient from the network
        // This loads the contract specification (WASM metadata) from the network,
        // which enables automatic type conversion in both invoke() and funcResToNative()
        val client = try {
            ContractClient.fromNetwork(
                contractId = contractId,
                rpcUrl = rpcUrl,
                network = network
            )
        } catch (e: Exception) {
            return InvokeHelloWorldResult.Error(
                message = "Failed to load contract specification: ${e.message}",
                exception = e
            )
        }

        // Step 5: Invoke the "hello" function using the beginner API with automatic result parsing
        // The SDK will:
        // 1. Convert the Map arguments to XDR types based on the contract spec
        // 2. Build and simulate the transaction
        // 3. Sign the transaction with the provided signer
        // 4. Submit the transaction to the network
        // 5. Poll for the transaction result
        // 6. Return the raw XDR result
        //
        // We then use funcResToNative() to parse the result automatically using the contract spec.
        // This is cleaner than manually parsing XDR types.
        val greeting = try {
            // Get the raw XDR result from contract invocation
            val resultXdr = client.invoke<SCValXdr>(
                functionName = "hello",
                arguments = mapOf("to" to to),  // Map-based arguments - SDK handles conversion
                source = submitterAccountId,
                signer = signerKeyPair
            )

            // Use funcResToNative() for automatic result parsing based on contract spec
            // The hello contract returns Vec<String> (["Hello", <name>])
            // funcResToNative() automatically converts this to a Kotlin List<String>
            val result = client.funcResToNative("hello", resultXdr) as? List<*>
                ?: throw IllegalStateException("Expected List result from contract")

            // Convert each element to String and join to form "Hello <name>"
            result.map { it.toString() }.joinToString(" ")
        } catch (e: Exception) {
            return InvokeHelloWorldResult.Error(
                message = "Failed to invoke contract function: ${e.message}",
                exception = e
            )
        }

        // Step 6: Return success with the greeting
        InvokeHelloWorldResult.Success(
            greeting = greeting
        )

    } catch (e: com.soneso.stellar.sdk.contract.exception.ContractException) {
        // Soroban contract-specific errors
        InvokeHelloWorldResult.Error(
            message = "Contract error: ${e.message ?: "Unknown contract error"}",
            exception = e
        )
    } catch (e: com.soneso.stellar.sdk.rpc.exception.SorobanRpcException) {
        // RPC communication errors
        InvokeHelloWorldResult.Error(
            message = "RPC error: ${e.message ?: "Failed to communicate with Soroban RPC"}",
            exception = e
        )
    } catch (e: IllegalArgumentException) {
        // Validation errors
        InvokeHelloWorldResult.Error(
            message = "Invalid input: ${e.message}",
            exception = e
        )
    } catch (e: Exception) {
        // Unexpected errors
        InvokeHelloWorldResult.Error(
            message = "Unexpected error: ${e.message ?: "Unknown error occurred"}",
            exception = e
        )
    }
}
