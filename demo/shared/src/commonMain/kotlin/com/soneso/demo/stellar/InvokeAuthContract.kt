package com.soneso.demo.stellar

import com.soneso.stellar.sdk.KeyPair
import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.contract.ContractClient
import com.soneso.stellar.sdk.xdr.SCValXdr

/**
 * Result type for authorization contract invocation operations.
 *
 * This sealed class represents the outcome of invoking the auth contract with dynamic
 * authorization detection, providing counter value, detected scenario, and who needed to sign.
 */
sealed class InvokeAuthContractResult {
    /**
     * Successful contract invocation with authorization details.
     *
     * @property counterValue The new counter value after increment
     * @property scenario The detected authorization scenario (same-invoker or different-invoker)
     * @property contractId The contract ID that was invoked
     * @property whoNeedsToSign Set of account IDs that needed to sign (empty for same-invoker)
     * @property transactionHash The hash of the invocation transaction
     */
    data class Success(
        val counterValue: UInt,
        val scenario: String,
        val contractId: String,
        val whoNeedsToSign: Set<String>,
        val transactionHash: String
    ) : InvokeAuthContractResult()

    /**
     * Failed contract invocation with error details.
     *
     * @property message Human-readable error message
     * @property exception The underlying exception, if any
     */
    data class Failure(
        val message: String,
        val exception: Throwable? = null
    ) : InvokeAuthContractResult()
}

/**
 * Invokes the "increment" function on a deployed auth contract with dynamic authorization handling.
 *
 * This function demonstrates a **unified production-ready pattern** for Soroban contract authorization
 * that automatically handles both same-invoker and different-invoker scenarios. It showcases how to:
 * - Use `ContractClient.fromNetwork()` to load contract specification
 * - Use `invokeWithXdr()` to build an AssembledTransaction (power API)
 * - Use `needsNonInvokerSigningBy()` to dynamically detect authorization requirements
 * - Conditionally call `signAuthEntries()` only when needed (different-invoker scenario)
 * - Use `funcArgsToXdrSCValues()` to convert Map arguments to XDR
 * - Use `funcResToNative()` to parse results automatically
 * - Extract transaction hash from sendTransactionResponse
 *
 * ## Soroban Authorization Patterns
 *
 * Smart contracts can require authorization when an operation is performed on behalf of a user.
 * There are two main scenarios:
 *
 * ### 1. Same-Invoker (Automatic Authorization)
 * When the **user account** (the account being incremented) is the **same** as the
 * **source account** (the account submitting the transaction):
 * - Authorization is **automatic**
 * - `needsNonInvokerSigningBy()` returns an **empty set**
 * - No `signAuthEntries()` call needed
 * - The SDK handles auth entries automatically
 *
 * Example: Alice increments her own counter using her account to submit.
 *
 * ### 2. Different-Invoker (Manual Authorization Required)
 * When the **user account** is **different** from the **source account**:
 * - Authorization is **required** from the user account
 * - `needsNonInvokerSigningBy()` returns a **set containing the user account ID**
 * - Must call `signAuthEntries(userKeyPair)` to sign the authorization
 * - The user explicitly authorizes the operation
 *
 * Example: Bob submits a transaction to increment Alice's counter. Alice must authorize.
 *
 * ## How needsNonInvokerSigningBy() Works
 *
 * After calling `invokeWithXdr()` to build the transaction, the SDK simulates it and
 * detects which Soroban authorization entries need to be signed. It returns a set of
 * account IDs that need to provide signatures:
 *
 * ```kotlin
 * val whoNeedsToSign = assembled.needsNonInvokerSigningBy()
 *
 * if (whoNeedsToSign.isEmpty()) {
 *     // Same-invoker scenario - automatic authorization
 *     println("Authorization is automatic")
 * } else {
 *     // Different-invoker scenario - manual authorization required
 *     println("Need signatures from: $whoNeedsToSign")
 *     assembled.signAuthEntries(userKeyPair)
 * }
 * ```
 *
 * ## Production Pattern: Unified Authorization Handling
 *
 * This function demonstrates the **recommended production pattern** for handling both scenarios
 * with a single code path:
 *
 * 1. Build the transaction with `invokeWithXdr()`
 * 2. Check `needsNonInvokerSigningBy()` to detect authorization requirements
 * 3. Conditionally call `signAuthEntries()` only if needed
 * 4. Submit with `signAndSubmit()`
 *
 * This pattern:
 * - Works for both same-invoker and different-invoker scenarios
 * - Doesn't require hardcoded logic to determine the scenario
 * - Is resilient to contract changes
 * - Follows best practices from the Stellar SDK
 *
 * ## Usage Example
 *
 * ### Same-Invoker Scenario
 * ```kotlin
 * val result = invokeAuthContract(
 *     contractId = "CA3D...",
 *     userAccountId = "GALICE...",     // Alice's account
 *     userKeyPair = aliceKeyPair,      // Alice's keypair
 *     sourceAccountId = "GALICE...",   // Same as user (Alice submits)
 *     sourceKeyPair = aliceKeyPair,    // Same keypair
 *     value = 5
 * )
 * // Result: Success(scenario = "Same-Invoker (Automatic Authorization)", whoNeedsToSign = emptySet())
 * ```
 *
 * ### Different-Invoker Scenario
 * ```kotlin
 * val result = invokeAuthContract(
 *     contractId = "CA3D...",
 *     userAccountId = "GALICE...",     // Alice's account
 *     userKeyPair = aliceKeyPair,      // Alice's keypair
 *     sourceAccountId = "GBOB...",     // Different (Bob submits)
 *     sourceKeyPair = bobKeyPair,      // Different keypair
 *     value = 5
 * )
 * // Result: Success(scenario = "Different-Invoker (Manual Authorization)", whoNeedsToSign = setOf("GALICE..."))
 * ```
 *
 * @param contractId The deployed auth contract ID (C... format, 56 characters)
 * @param userAccountId The user account to increment (G... format) - the account that owns the counter
 * @param userKeyPair The user's keypair for signing authorization entries (if needed)
 * @param sourceAccountId The account that will submit the transaction (G... format)
 * @param sourceKeyPair The source account's keypair for signing the transaction
 * @param value The amount to increment the counter
 * @return InvokeAuthContractResult.Success with authorization details or InvokeAuthContractResult.Failure with error
 *
 * @see ContractClient.fromNetwork
 * @see ContractClient.invokeWithXdr
 * @see ContractClient.funcArgsToXdrSCValues
 * @see ContractClient.funcResToNative
 * @see com.soneso.stellar.sdk.contract.AssembledTransaction.needsNonInvokerSigningBy
 * @see com.soneso.stellar.sdk.contract.AssembledTransaction.signAuthEntries
 * @see <a href="https://developers.stellar.org/docs/smart-contracts/guides/authorization">Soroban Authorization Guide</a>
 */
suspend fun invokeAuthContract(
    contractId: String,
    userAccountId: String,
    userKeyPair: KeyPair,
    sourceAccountId: String,
    sourceKeyPair: KeyPair,
    value: Int
): InvokeAuthContractResult {
    return try {
        // Step 1: Validate inputs
        if (contractId.isBlank()) {
            return InvokeAuthContractResult.Failure(
                message = "Contract ID cannot be empty"
            )
        }

        if (!contractId.startsWith('C')) {
            return InvokeAuthContractResult.Failure(
                message = "Contract ID must start with 'C' (got: ${contractId.take(1)})"
            )
        }

        if (contractId.length != 56) {
            return InvokeAuthContractResult.Failure(
                message = "Contract ID must be exactly 56 characters long (got: ${contractId.length})"
            )
        }

        if (userAccountId.isBlank()) {
            return InvokeAuthContractResult.Failure(
                message = "User account ID cannot be empty"
            )
        }

        if (!userAccountId.startsWith('G')) {
            return InvokeAuthContractResult.Failure(
                message = "User account ID must start with 'G' (got: ${userAccountId.take(1)})"
            )
        }

        if (userAccountId.length != 56) {
            return InvokeAuthContractResult.Failure(
                message = "User account ID must be exactly 56 characters long (got: ${userAccountId.length})"
            )
        }

        if (sourceAccountId.isBlank()) {
            return InvokeAuthContractResult.Failure(
                message = "Source account ID cannot be empty"
            )
        }

        if (!sourceAccountId.startsWith('G')) {
            return InvokeAuthContractResult.Failure(
                message = "Source account ID must start with 'G' (got: ${sourceAccountId.take(1)})"
            )
        }

        if (sourceAccountId.length != 56) {
            return InvokeAuthContractResult.Failure(
                message = "Source account ID must be exactly 56 characters long (got: ${sourceAccountId.length})"
            )
        }

        if (value < 0) {
            return InvokeAuthContractResult.Failure(
                message = "Value must be non-negative (got: $value)"
            )
        }

        // Step 2: Determine network and RPC URL
        val network = Network.TESTNET
        val rpcUrl = "https://soroban-testnet.stellar.org:443"

        // Step 3: Initialize ContractClient from the network
        // This loads the contract specification (WASM metadata) from the network,
        // which enables automatic type conversion in funcArgsToXdrSCValues() and funcResToNative()
        val client = try {
            ContractClient.fromNetwork(
                contractId = contractId,
                rpcUrl = rpcUrl,
                network = network
            )
        } catch (e: Exception) {
            return InvokeAuthContractResult.Failure(
                message = "Failed to load contract specification: ${e.message}",
                exception = e
            )
        }

        // Step 4: Convert arguments to XDR using the contract specification
        // The auth contract's increment function signature is:
        // fn increment(user: Address, value: u32) -> u32
        //
        // We use funcArgsToXdrSCValues() to convert the Map arguments to XDR types:
        // - "user" (String account ID) → SCValXdr.Address
        // - "value" (Int) → SCValXdr.U32
        val args = try {
            client.funcArgsToXdrSCValues(
                functionName = "increment",
                arguments = mapOf(
                    "user" to userAccountId,  // User account to increment
                    "value" to value          // Increment amount
                )
            )
        } catch (e: Exception) {
            return InvokeAuthContractResult.Failure(
                message = "Failed to convert arguments to XDR: ${e.message}",
                exception = e
            )
        }

        // Step 5: Build the AssembledTransaction using invokeWithXdr (power API)
        // This gives us control over the transaction before submission and allows us to:
        // 1. Detect authorization requirements with needsNonInvokerSigningBy()
        // 2. Conditionally sign auth entries if needed
        // 3. Submit the transaction when ready
        //
        // We provide a parseResultXdrFn to extract the UInt counter value from the result
        val assembled = try {
            client.invokeWithXdr(
                functionName = "increment",
                parameters = args,
                source = sourceAccountId,  // Account submitting the transaction
                signer = sourceKeyPair,    // Keypair for signing the transaction
                parseResultXdrFn = { xdr ->
                    // The increment function returns u32, which maps to SCValXdr.U32
                    (xdr as SCValXdr.U32).value.value
                }
            )
        } catch (e: Exception) {
            return InvokeAuthContractResult.Failure(
                message = "Failed to build transaction: ${e.message}",
                exception = e
            )
        }

        // Step 6: Detect authorization requirements dynamically
        // needsNonInvokerSigningBy() analyzes the simulated transaction and returns
        // a set of account IDs that need to sign authorization entries.
        //
        // Two scenarios:
        // - Empty set: Same-invoker (user == source) - authorization is automatic
        // - Non-empty set: Different-invoker (user != source) - manual authorization needed
        val whoNeedsToSign = assembled.needsNonInvokerSigningBy()

        // Step 7: Conditionally sign authorization entries if needed
        // This is the key pattern - we only call signAuthEntries() when required.
        // For same-invoker scenarios, the SDK handles auth automatically.
        if (whoNeedsToSign.isNotEmpty()) {
            // Different-invoker scenario: User account needs to authorize the operation
            if (whoNeedsToSign.contains(userAccountId)) {
                try {
                    assembled.signAuthEntries(userKeyPair)
                } catch (e: Exception) {
                    return InvokeAuthContractResult.Failure(
                        message = "Failed to sign authorization entries: ${e.message}",
                        exception = e
                    )
                }
            } else {
                // Unexpected: needsNonInvokerSigningBy() returned accounts we don't have keypairs for
                return InvokeAuthContractResult.Failure(
                    message = "Contract requires signatures from unknown accounts: $whoNeedsToSign"
                )
            }
        }

        // Step 8: Submit the transaction and get the result
        // signAndSubmit() will:
        // 1. Sign the transaction with the source keypair (if not already signed)
        // 2. Submit to the network
        // 3. Poll for the result
        // 4. Parse the result using our parseResultXdrFn (returns UInt)
        val counterValue = try {
            assembled.signAndSubmit(sourceKeyPair, force = false)
        } catch (e: Exception) {
            return InvokeAuthContractResult.Failure(
                message = "Failed to submit transaction: ${e.message}",
                exception = e
            )
        }

        // Step 9: Extract transaction hash from the sendTransactionResponse
        val transactionHash = assembled.sendTransactionResponse?.hash
            ?: return InvokeAuthContractResult.Failure(
                message = "Transaction hash not available"
            )

        // Step 10: Determine the scenario based on who needed to sign
        val scenario = if (whoNeedsToSign.isEmpty()) {
            "Same-Invoker (Automatic Authorization)"
        } else {
            "Different-Invoker (Manual Authorization)"
        }

        // Step 11: Return success with all details
        InvokeAuthContractResult.Success(
            counterValue = counterValue,
            scenario = scenario,
            contractId = contractId,
            whoNeedsToSign = whoNeedsToSign,
            transactionHash = transactionHash
        )

    } catch (e: com.soneso.stellar.sdk.contract.exception.ContractException) {
        // Soroban contract-specific errors
        InvokeAuthContractResult.Failure(
            message = "Contract error: ${e.message ?: "Unknown contract error"}",
            exception = e
        )
    } catch (e: com.soneso.stellar.sdk.rpc.exception.SorobanRpcException) {
        // RPC communication errors
        InvokeAuthContractResult.Failure(
            message = "RPC error: ${e.message ?: "Failed to communicate with Soroban RPC"}",
            exception = e
        )
    } catch (e: IllegalArgumentException) {
        // Validation errors (already wrapped above)
        InvokeAuthContractResult.Failure(
            message = e.message ?: "Invalid input",
            exception = e
        )
    } catch (e: Exception) {
        // Unexpected errors
        InvokeAuthContractResult.Failure(
            message = "Unexpected error: ${e.message ?: "Unknown error occurred"}",
            exception = e
        )
    }
}
