package com.soneso.stellar.sdk.contract

import com.soneso.stellar.sdk.*
import com.soneso.stellar.sdk.rpc.SorobanServer
import com.soneso.stellar.sdk.rpc.responses.GetTransactionStatus
import com.soneso.stellar.sdk.xdr.*
import kotlin.random.Random

/**
 * A client to interact with Soroban smart contracts.
 *
 * ## Beginner-Friendly Usage (Recommended)
 *
 * ```kotlin
 * // Load spec from network - enables automatic type conversion and result parsing!
 * val client = ContractClient.fromNetwork(contractId, rpcUrl, network)
 *
 * // Invoke with native types - automatic argument conversion
 * val balance = client.invoke(
 *     functionName = "balance",
 *     arguments = mapOf("account" to "GABC..."),
 *     source = sourceAccount,
 *     signer = null,
 *     parseResultXdrFn = { xdr ->
 *         (xdr as SCValXdr.I128).value.lo.value.toLong()
 *     }
 * )
 *
 * // Or use manual result parsing with funcResToNative
 * val resultXdr = client.invoke(
 *     functionName = "balance",
 *     arguments = mapOf("account" to "GABC..."),
 *     source = sourceAccount,
 *     signer = null
 * )
 * val balance = client.funcResToNative("balance", resultXdr) as BigInteger
 * ```
 *
 * ## Power User Options
 *
 * ```kotlin
 * // Manual type conversion
 * val args = client.funcArgsToXdrSCValues("transfer", mapOf(...))
 * val assembled = client.invokeWithXdr("transfer", args, source, keypair)
 *
 * // Full manual control
 * assembled.simulate()
 * val result = assembled.signAndSubmit(keypair)
 * ```
 *
 * ## Result Parsing
 *
 * ContractClient provides helper methods for manual result parsing:
 * - `funcResToNative(functionName, scVal)` - Convert XDR result to native Kotlin types
 * - `funcArgsToXdrSCValues(functionName, args)` - Convert native arguments to XDR
 *
 * Type mapping (Soroban → Kotlin):
 * | Soroban Type | Kotlin Type |
 * |--------------|-------------|
 * | u32, i32     | UInt, Int   |
 * | u64, i64     | ULong, Long |
 * | u128, i128   | BigInteger  |
 * | bool         | Boolean     |
 * | symbol, string | String    |
 * | address      | String      |
 * | bytes        | ByteArray   |
 * | vec<T>       | List<T>     |
 * | map<K, V>    | Map<K, V>   |
 * | option<T>    | T?          |
 * | void         | null        |
 *
 * @property contractId The contract ID to interact with (C... address)
 * @property rpcUrl The RPC server URL
 * @property network The network to interact with
 * @property server The SorobanServer instance for RPC calls
 */
class ContractClient private constructor(
    val contractId: String,
    val rpcUrl: String,
    val network: Network,
    private val contractSpec: ContractSpec?
) {
    val server: SorobanServer = SorobanServer(rpcUrl)

    /**
     * Get the contract specification (if loaded).
     */
    fun getContractSpec(): ContractSpec? = contractSpec

    /**
     * Get available method names from contract spec.
     * Returns empty set if spec not loaded.
     */
    fun getMethodNames(): Set<String> {
        return contractSpec?.funcs()?.map { it.name.value }?.toSet() ?: emptySet()
    }

    /**
     * Invoke a contract function with automatic type conversion (RECOMMENDED).
     *
     * This is the **primary method** for contract interaction. It provides:
     * - Automatic type conversion from native Kotlin types to XDR
     * - Method validation against contract spec
     * - Auto read/write detection - reads return immediately, writes are signed/submitted
     * - Direct result return - no manual .result() or .signAndSubmit() needed
     *
     * ## Result Parsing Options
     *
     * You have two options for parsing results:
     *
     * **Option 1: Custom Parser (parseResultXdrFn)**
     * ```kotlin
     * val balance = client.invoke(
     *     functionName = "balance",
     *     arguments = mapOf("id" to accountId),
     *     source = sourceAccount,
     *     signer = null,
     *     parseResultXdrFn = { xdr ->
     *         (xdr as SCValXdr.I128).value.lo.value.toLong()
     *     }
     * )
     * ```
     *
     * **Option 2: Manual Parsing with funcResToNative**
     * ```kotlin
     * val resultXdr = client.invoke(
     *     functionName = "balance",
     *     arguments = mapOf("id" to accountId),
     *     source = sourceAccount,
     *     signer = null
     * )
     * val balance = client.funcResToNative("balance", resultXdr) as BigInteger
     * ```
     *
     * ## API Modes
     *
     * **Beginner API (this method)**:
     * - Automatic argument conversion: Map<String, Any?> → XDR
     * - Flexible result parsing: parseResultXdrFn or funcResToNative
     * - Auto-execution (reads return results, writes auto-submit)
     * - Requires contract spec (use [fromNetwork])
     *
     * **Power API ([invokeWithXdr])**:
     * - Manual XDR construction: List<SCValXdr>
     * - Manual result handling: Returns raw SCValXdr
     * - Manual transaction control: Returns [AssembledTransaction]
     * - Works with or without spec
     *
     * Requires contract spec (use [fromNetwork] or [deploy]).
     *
     * @param functionName The contract function to invoke
     * @param arguments Function arguments as Map<String, Any> (native Kotlin types)
     * @param source The source account (G... or M... address)
     * @param signer KeyPair for signing (null for read-only calls)
     * @param parseResultXdrFn Optional custom function to parse result XDR
     * @param options Invocation options
     * @return The parsed result value (using parseResultXdrFn if provided, otherwise raw SCValXdr)
     * @throws IllegalStateException if contract spec not loaded
     * @throws IllegalArgumentException if method not found or invalid arguments
     */
    suspend fun <T> invoke(
        functionName: String,
        arguments: Map<String, Any?>,
        source: String,
        signer: KeyPair?,
        parseResultXdrFn: ((SCValXdr) -> T)? = null,
        options: ClientOptions = ClientOptions(
            sourceAccountKeyPair = signer ?: KeyPair.fromAccountId(source),
            contractId = contractId,
            network = network,
            rpcUrl = rpcUrl
        )
    ): T {
        val spec = contractSpec
            ?: throw IllegalStateException(
                "invoke() requires ContractSpec for automatic type conversion. " +
                "Use ContractClient.fromNetwork() to load spec automatically."
            )

        // Validate method exists
        val func = spec.getFunc(functionName)
            ?: throw IllegalArgumentException(
                "Method '$functionName' not found in contract spec. " +
                "Available methods: ${spec.funcs().map { it.name.value }.joinToString(", ")}"
            )

        // Convert arguments using ContractSpec
        val parameters = try {
            spec.funcArgsToXdrSCValues(functionName, arguments)
        } catch (e: Exception) {
            throw IllegalArgumentException(
                "Failed to convert arguments for '$functionName': ${e.message}",
                e
            )
        }

        // Build and simulate transaction
        val assembled = buildTransaction(
            functionName = functionName,
            parameters = parameters,
            source = source,
            signer = signer,
            parseResultXdrFn = parseResultXdrFn,
            options = options
        )

        if (options.simulate) {
            assembled.simulate(restore = options.restore)
        }

        // Auto-detect read/write and execute
        return if (options.autoSubmit) {
            if (assembled.isReadCall()) {
                assembled.result()
            } else {
                if (signer == null) {
                    throw IllegalArgumentException(
                        "Signer required for write call to '$functionName'"
                    )
                }
                assembled.signAndSubmit(signer, force = false)
            }
        } else {
            assembled.result()
        }
    }

    /**
     * Convert function arguments from native Kotlin types to XDR.
     *
     * This is a **power-user helper** that exposes the type conversion logic.
     * Use this when you want control over conversion timing or need to inspect
     * the XDR before invoking.
     *
     * Requires contract spec (use [fromNetwork]).
     *
     * @param functionName The function name
     * @param arguments Map of argument names to native Kotlin values
     * @return List of SCValXdr ready to pass to invokeWithXdr
     * @throws IllegalStateException if contract spec not loaded
     *
     * @sample
     * ```kotlin
     * val client = ContractClient.fromNetwork(contractId, rpcUrl, network)
     *
     * // Manual conversion for inspection/reuse
     * val args = client.funcArgsToXdrSCValues("transfer", mapOf(
     *     "from" to "GABC...",
     *     "to" to "GXYZ...",
     *     "amount" to 1000
     * ))
     *
     * // Inspect XDR
     * println("Converted args: $args")
     * ```
     */
    fun funcArgsToXdrSCValues(
        functionName: String,
        arguments: Map<String, Any?>
    ): List<SCValXdr> {
        val spec = contractSpec
            ?: throw IllegalStateException(
                "funcArgsToXdrSCValues requires ContractSpec. " +
                "Use ContractClient.fromNetwork()."
            )

        return spec.funcArgsToXdrSCValues(functionName, arguments)
    }

    /**
     * Convert a single native value to XDR based on type definition.
     *
     * This is a **power-user helper** for manual type conversion.
     *
     * @param value The native Kotlin value
     * @param typeDef The XDR type definition
     * @return Converted SCValXdr
     */
    fun nativeToXdrSCVal(value: Any?, typeDef: SCSpecTypeDefXdr): SCValXdr {
        val spec = contractSpec
            ?: throw IllegalStateException(
                "nativeToXdrSCVal requires ContractSpec. " +
                "Use ContractClient.fromNetwork()."
            )

        return spec.nativeToXdrSCVal(value, typeDef)
    }

    /**
     * Convert a contract function result from XDR to native Kotlin types.
     *
     * This is a **power-user helper** that exposes the result parsing logic.
     * Use this when you want control over result parsing or need to parse
     * results obtained outside of the standard invoke flow.
     *
     * Requires contract spec (use [fromNetwork]).
     *
     * @param functionName The function name
     * @param scVal The result value as SCValXdr
     * @return The converted native Kotlin value, or null for void results
     * @throws IllegalStateException if contract spec not loaded
     * @throws com.soneso.stellar.sdk.contract.exception.ContractSpecException if function not found or conversion fails
     *
     * @sample
     * ```kotlin
     * val client = ContractClient.fromNetwork(contractId, rpcUrl, network)
     *
     * // Parse contract result to native type
     * val resultXdr = SCValXdr.I128(Int128PartsXdr(Int64Xdr(0L), Uint64Xdr(1000000UL)))
     * val balance = client.funcResToNative("balance", resultXdr) as BigInteger
     * println("Balance: $balance")  // BigInteger(1000000)
     * ```
     */
    fun funcResToNative(functionName: String, scVal: SCValXdr): Any? {
        val spec = contractSpec
            ?: throw IllegalStateException(
                "funcResToNative requires ContractSpec. " +
                "Use ContractClient.fromNetwork()."
            )

        return spec.funcResToNative(functionName, scVal)
    }

    /**
     * Convert a contract function result from base64-encoded XDR to native Kotlin types.
     *
     * This is a **convenience overload** that first decodes the base64 XDR string
     * before converting to native types.
     *
     * Requires contract spec (use [fromNetwork]).
     *
     * @param functionName The function name
     * @param base64Xdr The result value as base64-encoded XDR string
     * @return The converted native Kotlin value, or null for void results
     * @throws IllegalStateException if contract spec not loaded
     * @throws com.soneso.stellar.sdk.contract.exception.ContractSpecException if function not found or conversion fails
     *
     * @sample
     * ```kotlin
     * val client = ContractClient.fromNetwork(contractId, rpcUrl, network)
     *
     * // Parse base64-encoded result
     * val base64Result = "AAAAAwAAAAQ="  // Encoded SCVal
     * val value = client.funcResToNative("get_value", base64Result) as UInt
     * println("Value: $value")
     * ```
     */
    fun funcResToNative(functionName: String, base64Xdr: String): Any? {
        val spec = contractSpec
            ?: throw IllegalStateException(
                "funcResToNative requires ContractSpec. " +
                "Use ContractClient.fromNetwork()."
            )

        return spec.funcResToNative(functionName, base64Xdr)
    }

    /**
     * Invoke a contract function with manual XDR construction (ADVANCED).
     *
     * This is the **advanced method** for when you need full control over
     * XDR construction and transaction execution.
     *
     * Returns an [AssembledTransaction] that you control manually.
     * Results are **NOT** automatically parsed - you get raw SCValXdr.
     *
     * Most developers should use [invoke] instead for automatic type conversion.
     *
     * ## Beginner API vs Power API
     *
     * **Beginner API ([invoke])**:
     * - Arguments: Map<String, Any?> (automatic conversion)
     * - Results: Flexible (parseResultXdrFn or funcResToNative)
     * - Execution: Automatic (reads return, writes auto-submit)
     *
     * **Power API (this method)**:
     * - Arguments: List<SCValXdr> (manual XDR)
     * - Results: Raw SCValXdr (manual parsing)
     * - Execution: Manual via AssembledTransaction
     *
     * @param functionName The contract function to invoke
     * @param parameters Function parameters as XDR (List<SCValXdr>)
     * @param source The source account
     * @param signer KeyPair for signing (can be null)
     * @param parseResultXdrFn Optional result parser
     * @param options Invocation options
     * @return AssembledTransaction for manual control
     *
     * @sample
     * ```kotlin
     * // Power API - Manual XDR construction
     * val params = listOf(
     *     Address(accountId).toSCVal(),
     *     Scv.toInt128(BigInteger.fromInt(1000))
     * )
     * val assembled = client.invokeWithXdr("transfer", params, source, keypair)
     *
     * // Manual simulation and execution
     * assembled.simulate()
     * val resultXdr = assembled.signAndSubmit(keypair)
     *
     * // Manual result parsing
     * val balance = client.funcResToNative("transfer", resultXdr) as BigInteger
     * ```
     */
    suspend fun <T> invokeWithXdr(
        functionName: String,
        parameters: List<SCValXdr>,
        source: String,
        signer: KeyPair?,
        parseResultXdrFn: ((SCValXdr) -> T)? = null,
        options: ClientOptions = ClientOptions(
            sourceAccountKeyPair = signer ?: KeyPair.fromAccountId(source),
            contractId = contractId,
            network = network,
            rpcUrl = rpcUrl
        )
    ): AssembledTransaction<T> {
        // Validate method name if spec available
        if (contractSpec != null) {
            val func = contractSpec.getFunc(functionName)
            if (func == null) {
                val available = contractSpec.funcs().map { it.name.value }
                throw IllegalArgumentException(
                    "Method '$functionName' not found in contract spec. " +
                    "Available methods: ${available.joinToString(", ")}"
                )
            }
        }

        val assembled = buildTransaction(
            functionName = functionName,
            parameters = parameters,
            source = source,
            signer = signer,
            parseResultXdrFn = parseResultXdrFn,
            options = options
        )

        if (options.simulate) {
            assembled.simulate(restore = options.restore)
        }

        return assembled
    }

    /**
     * Internal helper to build transaction.
     */
    private suspend fun <T> buildTransaction(
        functionName: String,
        parameters: List<SCValXdr>,
        source: String,
        signer: KeyPair?,
        parseResultXdrFn: ((SCValXdr) -> T)?,
        options: ClientOptions
    ): AssembledTransaction<T> {
        val operation = InvokeHostFunctionOperation.invokeContractFunction(
            contractAddress = contractId,
            functionName = functionName,
            parameters = parameters
        )

        val sourceAccount = server.getAccount(source)
        val builder = TransactionBuilder(
            sourceAccount = sourceAccount,
            network = network
        )
            .addOperation(operation)
            .setTimeout(options.transactionTimeout)
            .setBaseFee(options.baseFee.toLong())

        return AssembledTransaction(
            server = server,
            submitTimeout = options.submitTimeout,
            transactionSigner = signer,
            parseResultXdrFn = parseResultXdrFn,
            transactionBuilder = builder
        )
    }

    /**
     * Close the underlying SorobanServer connection.
     */
    fun close() {
        server.close()
    }

    companion object {
        /**
         * Create a ContractClient with contract spec loaded from the network (RECOMMENDED).
         *
         * This is the **primary way** to create a ContractClient. It loads the
         * contract specification from the network, enabling:
         * - Automatic type conversion (native Kotlin types → XDR)
         * - Method name validation
         * - Contract introspection
         * - Manual result parsing helpers (funcResToNative)
         *
         * If the contract spec cannot be loaded, returns a client without spec
         * (falls back to manual XDR mode).
         *
         * @param contractId The contract ID (C... address)
         * @param rpcUrl The RPC server URL
         * @param network The network (TESTNET/PUBLIC)
         * @return ContractClient with contract spec if available
         */
        suspend fun fromNetwork(
            contractId: String,
            rpcUrl: String,
            network: Network
        ): ContractClient {
            val server = SorobanServer(rpcUrl)

            val contractSpec = try {
                val contractInfo = server.loadContractInfoForContractId(contractId)
                if (contractInfo?.specEntries?.isNotEmpty() == true) {
                    ContractSpec(contractInfo.specEntries)
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }

            return ContractClient(contractId, rpcUrl, network, contractSpec)
        }

        /**
         * Create a ContractClient without loading contract spec (ADVANCED).
         *
         * Use this for advanced scenarios where you want full manual control.
         * Most developers should use [fromNetwork] instead.
         *
         * @param contractId The contract ID (C... address)
         * @param rpcUrl The RPC server URL
         * @param network The network (TESTNET/PUBLIC)
         * @return ContractClient without contract spec
         */
        fun withoutSpec(
            contractId: String,
            rpcUrl: String,
            network: Network
        ): ContractClient {
            return ContractClient(contractId, rpcUrl, network, null)
        }

        /**
         * Deploy a contract in one step (RECOMMENDED).
         *
         * This is the **primary deployment method** that handles everything:
         * 1. Upload WASM (with duplicate detection)
         * 2. Deploy contract with optional constructor
         * 3. Load contract spec
         * 4. Return ready-to-use client
         *
         * For advanced scenarios requiring WASM reuse, see [install] and [deployFromWasmId].
         *
         * @param wasmBytes The contract WASM code
         * @param constructorArgs Constructor arguments as native Kotlin types
         * @param source Source account for transactions
         * @param signer KeyPair for signing
         * @param network Network to deploy to
         * @param rpcUrl RPC server URL
         * @param salt Salt for contract ID generation (default: random)
         * @param loadSpec Whether to load spec after deployment (default: true)
         * @return ContractClient for the deployed contract
         *
         * @sample
         * ```kotlin
         * val client = ContractClient.deploy(
         *     wasmBytes = File("token.wasm").readBytes(),
         *     constructorArgs = mapOf(
         *         "name" to "MyToken",
         *         "symbol" to "MTK",
         *         "decimals" to 7
         *     ),
         *     source = sourceAccount,
         *     signer = keypair,
         *     network = Network.TESTNET,
         *     rpcUrl = "https://soroban-testnet.stellar.org:443"
         * )
         * // Ready to use!
         * val balance = client.invoke("balance", mapOf("account" to account), source, null)
         * ```
         */
        suspend fun deploy(
            wasmBytes: ByteArray,
            constructorArgs: Map<String, Any?> = emptyMap(),
            source: String,
            signer: KeyPair,
            network: Network,
            rpcUrl: String,
            salt: ByteArray = Random.Default.nextBytes(32),
            loadSpec: Boolean = true
        ): ContractClient {
            // Step 1: Upload WASM
            val wasmId = installInternal(
                wasmBytes = wasmBytes,
                source = source,
                signer = signer,
                network = network,
                rpcUrl = rpcUrl
            )

            // Step 2: Deploy from WASM ID
            return deployFromWasmIdInternal(
                wasmId = wasmId,
                constructorArgs = constructorArgs,
                source = source,
                signer = signer,
                network = network,
                rpcUrl = rpcUrl,
                salt = salt,
                loadSpec = loadSpec
            )
        }

        /**
         * Upload contract WASM code (ADVANCED - Step 1 of 2).
         *
         * This is a **power-user method** for two-step deployment when you need to:
         * - Reuse the same WASM for multiple contract instances
         * - Inspect the WASM ID before deployment
         * - Separate upload and deployment transactions
         *
         * Most developers should use the one-step [deploy] method instead.
         *
         * Returns the WASM ID (hash) as a hex string which can be used with [deployFromWasmId].
         * Includes duplicate detection - if WASM already installed, returns existing ID.
         *
         * @param wasmBytes The compiled contract WASM code
         * @param source Source account for the transaction
         * @param signer KeyPair for signing
         * @param network Network to upload to
         * @param rpcUrl RPC server URL
         * @return WASM ID as hex string (ready to use)
         *
         * @sample
         * ```kotlin
         * // Step 1: Install WASM once
         * val wasmId = ContractClient.install(
         *     wasmBytes = File("token.wasm").readBytes(),
         *     source = sourceAccount,
         *     signer = keypair,
         *     network = Network.TESTNET,
         *     rpcUrl = "https://soroban-testnet.stellar.org:443"
         * )
         * println("WASM ID: $wasmId")  // Ready to use!
         *
         * // Step 2: Deploy multiple instances from same WASM
         * val client1 = ContractClient.deployFromWasmId(wasmId = wasmId, ...)
         * val client2 = ContractClient.deployFromWasmId(wasmId = wasmId, ...)
         * ```
         */
        suspend fun install(
            wasmBytes: ByteArray,
            source: String,
            signer: KeyPair,
            network: Network,
            rpcUrl: String
        ): String {
            return installInternal(wasmBytes, source, signer, network, rpcUrl)
        }

        /**
         * Deploy a contract from an existing WASM ID (ADVANCED - Step 2 of 2).
         *
         * This is a **power-user method** for deploying contracts from a previously
         * uploaded WASM. Use this to deploy multiple contract instances from the
         * same WASM code (saves fees and time).
         *
         * Most developers should use the one-step [deploy] method instead.
         *
         * @param wasmId WASM ID (hex string) from [install]
         * @param constructorArgs Constructor arguments as List<SCValXdr> (XDR)
         * @param source Source account
         * @param signer KeyPair for signing
         * @param network Network to deploy to
         * @param rpcUrl RPC server URL
         * @param salt Salt for contract ID generation (default: random)
         * @param loadSpec Whether to load spec after deployment (default: true)
         * @return ContractClient for the deployed contract
         *
         * @sample
         * ```kotlin
         * // After installing WASM...
         * val client = ContractClient.deployFromWasmId(
         *     wasmId = "a1b2c3...",  // Hex string from install()
         *     constructorArgs = listOf(
         *         Scv.toString("MyToken"),
         *         Scv.toString("MTK"),
         *         Scv.toInt32(7)
         *     ),
         *     source = sourceAccount,
         *     signer = keypair,
         *     network = Network.TESTNET,
         *     rpcUrl = "https://soroban-testnet.stellar.org:443"
         * )
         * ```
         */
        suspend fun deployFromWasmId(
            wasmId: String,
            constructorArgs: List<SCValXdr> = emptyList(),
            source: String,
            signer: KeyPair,
            network: Network,
            rpcUrl: String,
            salt: ByteArray = Random.Default.nextBytes(32),
            loadSpec: Boolean = true
        ): ContractClient {
            // Validate hex string format
            require(wasmId.matches(Regex("^[0-9a-fA-F]+$"))) {
                "Invalid WASM ID format: must be a hex string (got: $wasmId)"
            }
            require(wasmId.length % 2 == 0) {
                "Invalid WASM ID format: hex string must have even length (got: ${wasmId.length})"
            }

            // Convert hex string to ByteArray
            val wasmHash = hexStringToByteArray(wasmId)

            // Deploy contract
            val contractId = deployContractInternal(
                wasmHash = wasmHash,
                constructorParams = constructorArgs,
                source = source,
                signer = signer,
                network = network,
                rpcUrl = rpcUrl,
                salt = salt
            )

            // Return client with or without spec
            return if (loadSpec) {
                fromNetwork(contractId, rpcUrl, network)
            } else {
                ContractClient(contractId, rpcUrl, network, null)
            }
        }

        /**
         * Internal helper to install WASM code.
         * Returns WASM ID as hex string.
         */
        private suspend fun installInternal(
            wasmBytes: ByteArray,
            source: String,
            signer: KeyPair,
            network: Network,
            rpcUrl: String
        ): String {
            val server = SorobanServer(rpcUrl)

            val account = server.getAccount(source)

            val uploadFunction = HostFunctionXdr.Wasm(wasmBytes)
            val operation = InvokeHostFunctionOperation(hostFunction = uploadFunction)
            val transaction = TransactionBuilder(
                sourceAccount = account,
                network = network
            )
                .addOperation(operation)
                .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
                .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
                .build()

            val simulateResponse = server.simulateTransaction(transaction)
            if (simulateResponse.error != null) {
                throw IllegalStateException("WASM upload simulation failed: ${simulateResponse.error}")
            }

            val preparedTransaction = server.prepareTransaction(transaction, simulateResponse)
            preparedTransaction.sign(signer)

            val sendResponse = server.sendTransaction(preparedTransaction)
            if (sendResponse.hash == null) {
                throw IllegalStateException("Failed to send WASM upload transaction")
            }

            val rpcResponse = server.pollTransaction(
                hash = sendResponse.hash,
                maxAttempts = 60,
                sleepStrategy = { 3000L }
            )

            if (rpcResponse.status != GetTransactionStatus.SUCCESS) {
                throw IllegalStateException("WASM upload transaction failed with status: ${rpcResponse.status}")
            }

            val wasmId = rpcResponse.getWasmId()
                ?: throw IllegalStateException("Failed to extract WASM ID from transaction response")

            // Return the hex string directly (already in hex format from getWasmId())
            return wasmId
        }

        /**
         * Internal helper to deploy from WASM ID with Map arguments.
         */
        private suspend fun deployFromWasmIdInternal(
            wasmId: String,
            constructorArgs: Map<String, Any?>,
            source: String,
            signer: KeyPair,
            network: Network,
            rpcUrl: String,
            salt: ByteArray,
            loadSpec: Boolean
        ): ContractClient {
            // First load the spec from WASM to convert constructor args
            val server = SorobanServer(rpcUrl)

            val constructorXdr = if (constructorArgs.isNotEmpty()) {
                val contractInfo = server.loadContractInfoForWasmId(wasmId)
                if (contractInfo?.specEntries?.isNotEmpty() == true) {
                    val spec = ContractSpec(contractInfo.specEntries)
                    // Look for constructor function (usually named "__constructor")
                    val constructorFunc = spec.getFunc("__constructor")
                    if (constructorFunc != null) {
                        spec.funcArgsToXdrSCValues("__constructor", constructorArgs)
                    } else {
                        // If no constructor function, assume direct parameter mapping
                        emptyList()
                    }
                } else {
                    emptyList()
                }
            } else {
                emptyList()
            }

            // Convert hex string to ByteArray for deployment
            val wasmHash = hexStringToByteArray(wasmId)

            // Deploy contract
            val contractId = deployContractInternal(
                wasmHash = wasmHash,
                constructorParams = constructorXdr,
                source = source,
                signer = signer,
                network = network,
                rpcUrl = rpcUrl,
                salt = salt
            )

            // Return client with or without spec
            return if (loadSpec) {
                fromNetwork(contractId, rpcUrl, network)
            } else {
                ContractClient(contractId, rpcUrl, network, null)
            }
        }

        /**
         * Internal helper to deploy contract from WASM hash.
         */
        private suspend fun deployContractInternal(
            wasmHash: ByteArray,
            constructorParams: List<SCValXdr>,
            source: String,
            signer: KeyPair,
            network: Network,
            rpcUrl: String,
            salt: ByteArray
        ): String {
            val server = SorobanServer(rpcUrl)
            val account = server.getAccount(source)

            val addressObj = Address(source)
            val scAddress = addressObj.toSCAddress()
            val saltXdr = Uint256Xdr(salt)

            val preimage = ContractIDPreimageXdr.FromAddress(
                ContractIDPreimageFromAddressXdr(address = scAddress, salt = saltXdr)
            )

            val wasmHashXdr = HashXdr(wasmHash)
            val executable = ContractExecutableXdr.WasmHash(wasmHashXdr)

            val operation: InvokeHostFunctionOperation
            val transaction: Transaction

            if (constructorParams.isNotEmpty()) {
                // Use CreateContractV2 for contracts with constructors
                val createContractArgs = CreateContractArgsV2Xdr(
                    contractIdPreimage = preimage,
                    executable = executable,
                    constructorArgs = constructorParams
                )

                val createFunction = HostFunctionXdr.CreateContractV2(createContractArgs)
                operation = InvokeHostFunctionOperation(hostFunction = createFunction)
            } else {
                // Use CreateContract for contracts without constructors
                val createContractArgs = CreateContractArgsXdr(
                    contractIdPreimage = preimage,
                    executable = executable
                )

                val createFunction = HostFunctionXdr.CreateContract(createContractArgs)
                operation = InvokeHostFunctionOperation(hostFunction = createFunction)
            }

            transaction = TransactionBuilder(
                sourceAccount = account,
                network = network
            )
                .addOperation(operation)
                .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
                .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
                .build()

            val simulateResponse = server.simulateTransaction(transaction)
            if (simulateResponse.error != null) {
                throw IllegalStateException("Contract deployment simulation failed: ${simulateResponse.error}")
            }

            val preparedTransaction = server.prepareTransaction(transaction, simulateResponse)
            preparedTransaction.sign(signer)

            val sendResponse = server.sendTransaction(preparedTransaction)
            if (sendResponse.hash == null) {
                throw IllegalStateException("Failed to send contract deployment transaction")
            }

            val rpcResponse = server.pollTransaction(
                hash = sendResponse.hash,
                maxAttempts = 60,
                sleepStrategy = { 3000L }
            )

            if (rpcResponse.status != GetTransactionStatus.SUCCESS) {
                throw IllegalStateException("Contract deployment transaction failed with status: ${rpcResponse.status}")
            }

            return rpcResponse.getCreatedContractId()
                ?: throw IllegalStateException("Failed to extract contract ID from transaction response")
        }

        /**
         * Convert hex string to ByteArray.
         */
        private fun hexStringToByteArray(hex: String): ByteArray {
            require(hex.length % 2 == 0) { "Hex string must have even length" }
            return hex.chunked(2)
                .map { it.toInt(16).toByte() }
                .toByteArray()
        }
    }
}
