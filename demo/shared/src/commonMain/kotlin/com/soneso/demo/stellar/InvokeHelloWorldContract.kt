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
 * - Parse XDR results using custom result parsing functions
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
 * 4. **Result Parsing**: The result is parsed from XDR using a custom parsing function.
 *    The hello contract returns `Vec<String>` (a vector of strings), so we need to:
 *    - Cast the XDR to `SCValXdr.Vec`
 *    - Extract the vector elements
 *    - Map each element to a String
 *    - Join them to form the greeting
 *
 * ## XDR Type Handling
 *
 * The hello contract returns a `Vec<String>` in XDR format. The SDK does not automatically
 * convert this to a Kotlin String, so we must provide a custom parser:
 *
 * ```kotlin
 * parseResultXdrFn = { xdr ->
 *     val vec = (xdr as SCValXdr.Vec).value?.value
 *         ?: throw IllegalStateException("Expected Vec result")
 *     vec.map { element ->
 *         (element as SCValXdr.Str).value.value
 *     }.joinToString(" ")
 * }
 * ```
 *
 * This parser:
 * 1. Casts the XDR to `SCValXdr.Vec` (vector type)
 * 2. Extracts the inner list of elements
 * 3. Maps each element (expected to be `SCValXdr.Str`) to a Kotlin String
 * 4. Joins the strings with a space to form "Hello <name>"
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
 * ## Return Type Parsing
 *
 * For output, the contract returns XDR types that need custom parsing:
 * - `SCValXdr.Str` → Extract `.value.value` to get String
 * - `SCValXdr.U32` → Extract `.value.value` to get UInt
 * - `SCValXdr.Vec` → Extract `.value?.value` to get List<SCValXdr>
 * - `SCValXdr.Bool` → Extract `.value.value` to get Boolean
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
        // which enables automatic type conversion in the invoke() method
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

        // Step 5: Invoke the "hello" function using the beginner API with custom XDR parsing
        // The SDK will:
        // 1. Convert the Map arguments to XDR types based on the contract spec
        // 2. Build and simulate the transaction
        // 3. Sign the transaction with the provided signer
        // 4. Submit the transaction to the network
        // 5. Poll for the transaction result
        // 6. Parse the result XDR using our custom parsing function
        //
        // The hello contract returns Vec<String> (["Hello", <name>]) in XDR format,
        // so we need a custom parser to extract and join the strings
        val greeting = try {
            client.invoke(
                functionName = "hello",
                arguments = mapOf("to" to to),  // Map-based arguments - SDK handles conversion
                source = submitterAccountId,
                signer = signerKeyPair,
                parseResultXdrFn = { xdr ->
                    // Parse Vec<String> result from XDR
                    // The contract returns a vector with two elements: ["Hello", <name>]
                    val vec = (xdr as SCValXdr.Vec).value?.value
                        ?: throw IllegalStateException("Expected Vec result")

                    // Map each XDR string element to a Kotlin String
                    val strings = vec.map { element ->
                        (element as SCValXdr.Str).value.value
                    }

                    // Join the strings to form "Hello <name>"
                    strings.joinToString(" ")
                }
            )
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
