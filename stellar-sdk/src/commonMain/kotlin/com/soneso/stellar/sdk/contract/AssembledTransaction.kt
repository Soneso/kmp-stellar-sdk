package com.soneso.stellar.sdk.contract

import com.soneso.stellar.sdk.*
import com.soneso.stellar.sdk.contract.exception.*
import com.soneso.stellar.sdk.rpc.SorobanServer
import com.soneso.stellar.sdk.rpc.SorobanDataBuilder
import com.soneso.stellar.sdk.rpc.responses.GetTransactionResponse
import com.soneso.stellar.sdk.rpc.responses.SendTransactionResponse
import com.soneso.stellar.sdk.rpc.responses.SimulateTransactionResponse
import com.soneso.stellar.sdk.rpc.responses.SendTransactionStatus
import com.soneso.stellar.sdk.rpc.responses.GetTransactionStatus
import com.soneso.stellar.sdk.xdr.*
import kotlinx.coroutines.delay

/**
 * Represents a prepared transaction ready for signing and submission.
 *
 * AssembledTransaction manages the complete lifecycle:
 * 1. **Simulate** - Get resource estimates from the network
 * 2. **Sign** - Add transaction and/or authorization signatures
 * 3. **Submit** - Send to network and poll for completion
 * 4. **Parse** - Convert result to desired type
 *
 * ## Usage Patterns
 *
 * ### Read-Only Call (No Signing)
 * ```kotlin
 * val assembled = client.invoke<Long>(
 *     functionName = "balance",
 *     parameters = listOf(Scv.toAddress(account)),
 *     source = account,
 *     signer = null,
 *     parseResultXdrFn = { Scv.fromInt128(it).toLong() }
 * )
 * val balance = assembled.result() // Returns simulated result
 * ```
 *
 * ### Write Call (Standard Flow)
 * ```kotlin
 * val assembled = client.invoke<Unit>(
 *     functionName = "transfer",
 *     parameters = listOf(from, to, amount),
 *     source = account,
 *     signer = keypair,
 *     parseResultXdrFn = null
 * )
 * val result = assembled.signAndSubmit(keypair) // One-step
 * ```
 *
 * ### Advanced Flow (Separate Steps)
 * ```kotlin
 * val assembled = client.invoke<TransferResult>(...)
 * assembled.sign(keypair)            // Sign transaction
 * assembled.signAuthEntries(keypair) // Sign auth entries if needed
 * val result = assembled.submit()    // Submit and wait
 * ```
 *
 * ### Multi-Auth Workflow (Atomic Swaps, etc.)
 * ```kotlin
 * // Build transaction (e.g., atomic swap between Alice and Bob)
 * val tx = atomicSwapClient.invoke<Unit>(
 *     functionName = "swap",
 *     parameters = listOf(alice, bob, tokenA, tokenB, amounts...),
 *     source = invokerAccount,
 *     signer = invokerKeyPair
 * )
 *
 * // Check who needs to sign auth entries
 * val whoElseNeedsToSign = tx.needsNonInvokerSigningBy()
 * // Returns: ["GALICE...", "GBOB..."]
 *
 * // Sign auth entries for Alice
 * tx.signAuthEntries(aliceKeyPair)
 *
 * // Sign auth entries for Bob (using delegate for remote signing)
 * tx.signAuthEntries(
 *     authEntriesSigner = KeyPair.fromAccountId(bobId),
 *     authorizeEntryDelegate = { entry, network ->
 *         // Send to remote server for signing
 *         val entryXdr = entry.toXdrBase64()
 *         val signedXdr = remoteSignService.sign(entryXdr)
 *         SorobanAuthorizationEntryXdr.fromXdrBase64(signedXdr)
 *     }
 * )
 *
 * // Submit transaction
 * val result = tx.signAndSubmit(invokerKeyPair)
 * ```
 *
 * @param T The type of the parsed result
 * @property server The SorobanServer for RPC calls
 * @property submitTimeout Polling timeout in seconds
 * @property transactionSigner Optional KeyPair for signing
 * @property parseResultXdrFn Optional function to parse result
 * @property transactionBuilder The transaction builder
 */
class AssembledTransaction<T>(
    private val server: SorobanServer,
    private val submitTimeout: Int,
    private val transactionSigner: KeyPair?,
    private val parseResultXdrFn: ((SCValXdr) -> T)?,
    private var transactionBuilder: TransactionBuilder
) {
    /**
     * The TransactionBuilder before simulation.
     *
     * You can modify this before calling simulate() if you set simulate=false
     * in the ContractClient.invoke() call.
     *
     * Example:
     * ```kotlin
     * val tx = client.invoke<T>(
     *     functionName = "method",
     *     parameters = params,
     *     source = account,
     *     signer = keypair,
     *     parseResultXdrFn = parser,
     *     simulate = false // Don't auto-simulate
     * )
     * tx.raw?.addMemo(Memo.text("Hello!"))
     * tx.simulate()
     * ```
     */
    var raw: TransactionBuilder? = transactionBuilder
        private set

    /**
     * The built transaction after simulation (null before simulation).
     */
    var builtTransaction: Transaction? = null
        private set

    /**
     * The signed transaction (separate from builtTransaction).
     *
     * Set after calling sign(). Null if not yet signed.
     */
    var signed: Transaction? = null
        private set

    /**
     * The simulation response (null before simulation).
     */
    var simulation: SimulateTransactionResponse? = null
        private set

    /**
     * The send transaction response (null before submission).
     */
    var sendTransactionResponse: SendTransactionResponse? = null
        private set

    /**
     * The get transaction response (null before completion).
     */
    var getTransactionResponse: GetTransactionResponse? = null
        private set

    // Cached simulation data
    private var simulationResult: SimulateTransactionResponse.SimulateHostFunctionResult? = null
    private var simulationTransactionData: SorobanTransactionDataXdr? = null

    /**
     * Simulates the transaction on the network.
     *
     * Must be called before signing or submitting. Will automatically restore
     * required contract state if [restore] is true and this is not a read call.
     *
     * @param restore Whether to automatically restore contract state if needed
     * @return This AssembledTransaction for chaining
     * @throws SimulationFailedException if the simulation failed
     * @throws RestorationFailureException if contract state could not be restored
     */
    suspend fun simulate(restore: Boolean = true): AssembledTransaction<T> {
        // Clear cached data
        simulationResult = null
        simulationTransactionData = null

        // Update source account sequence number
        val sourceAccount = server.getAccount(transactionBuilder.sourceAccount.accountId)
        transactionBuilder.sourceAccount.setSequenceNumber(sourceAccount.sequenceNumber)

        // Build and simulate
        val builtTx = transactionBuilder.build()
        simulation = server.simulateTransaction(builtTx)

        // Handle restoration if needed
        if (restore && simulation!!.restorePreamble != null && !isReadCall()) {
            try {
                restoreFootprint()
            } catch (e: ContractException) {
                throw RestorationFailureException("Failed to restore contract data.", this)
            } catch (e: Exception) {
                throw RestorationFailureException("Failed to restore contract data: ${e.message}", this)
            }
            // Re-simulate after restoration
            return simulate(restore = false)
        }

        // Check for simulation errors
        if (simulation!!.error != null) {
            throw SimulationFailedException(
                "Transaction simulation failed: ${simulation!!.error}",
                this
            )
        }

        // Assemble transaction with simulation results
        builtTransaction = server.prepareTransaction(builtTx, simulation!!)

        return this
    }

    /**
     * Signs the transaction.
     *
     * @param transactionSigner The KeyPair to sign with (or null to use constructor signer)
     * @param force Whether to sign even if this is a read call (default false)
     * @return This AssembledTransaction for chaining
     * @throws NotYetSimulatedException if not yet simulated
     * @throws NoSignatureNeededException if read call and force=false
     * @throws ExpiredStateException if contract state needs restoration
     * @throws NeedsMoreSignaturesException if more auth signatures required
     */
    suspend fun sign(transactionSigner: KeyPair? = null, force: Boolean = false): AssembledTransaction<T> {
        if (builtTransaction == null) {
            throw NotYetSimulatedException("Transaction has not yet been simulated.", this)
        }

        if (!force && isReadCall()) {
            throw NoSignatureNeededException(
                "This is a read call. It requires no signature or submitting. Set force=true to sign and submit anyway.",
                this
            )
        }

        if (simulation != null && simulation!!.restorePreamble != null) {
            throw ExpiredStateException(
                "You need to restore contract state before you can invoke this method. " +
                "You can set `restore` to true in order to automatically restore the contract state when needed.",
                this
            )
        }

        val signer = transactionSigner ?: this.transactionSigner
            ?: throw IllegalArgumentException(
                "You must provide a transactionSigner to sign the transaction, either here or in the constructor."
            )

        // Check for non-invoker signatures needed
        val sigsNeeded = needsNonInvokerSigningBy(includeAlreadySigned = false)
            .filter { !it.startsWith("C") } // Filter out contract addresses

        if (sigsNeeded.isNotEmpty()) {
            throw NeedsMoreSignaturesException(
                "Transaction requires signatures from $sigsNeeded. See `needsNonInvokerSigningBy` for details.",
                this
            )
        }

        builtTransaction!!.sign(signer)
        signed = builtTransaction  // Set signed property
        return this
    }

    /**
     * Signs the transaction and submits it in one step.
     *
     * Convenience method combining [sign] and [submit].
     *
     * @param transactionSigner The KeyPair to sign with (or null to use constructor signer)
     * @param force Whether to sign even if this is a read call (default false)
     * @return The parsed result (or raw SCValXdr if parseResultXdrFn is null)
     * @throws NotYetSimulatedException if not yet simulated
     * @throws NoSignatureNeededException if read call and force=false
     * @throws NeedsMoreSignaturesException if more auth signatures required
     * @throws SendTransactionFailedException if submission failed
     * @throws TransactionStillPendingException if still pending after timeout
     * @throws ExpiredStateException if contract state needs restoration
     * @throws TransactionFailedException if transaction failed
     */
    suspend fun signAndSubmit(transactionSigner: KeyPair? = null, force: Boolean = false): T {
        sign(transactionSigner, force)
        return submit()
    }

    /**
     * Signs authorization entries in the transaction.
     *
     * This method is used for multi-authorization workflows where the contract invocation
     * requires authorization from specific accounts (other than the transaction invoker).
     *
     * ## Use Cases
     *
     * 1. **Atomic Swaps**: Alice and Bob both need to authorize their token transfers
     * 2. **Multi-Signature Operations**: Multiple parties must approve a contract action
     * 3. **Remote Signing**: One party signs locally, another signs on a remote server
     *
     * ## Parameters
     *
     * - **authEntriesSigner**: The KeyPair to sign with. Can be public-key-only if using delegate.
     * - **validUntilLedgerSequence**: Optional expiration ledger. Defaults to current + 100 (~8.3 minutes).
     * - **authorizeEntryDelegate**: Optional function for custom/remote signing logic.
     *
     * ## Basic Usage (Local Signing)
     *
     * ```kotlin
     * val tx = swapClient.invoke<Unit>(
     *     functionName = "swap",
     *     parameters = swapParams,
     *     source = invokerAccount,
     *     signer = invokerKeyPair
     * )
     *
     * // Sign auth entries for Alice (has private key)
     * tx.signAuthEntries(aliceKeyPair)
     *
     * // Sign auth entries for Bob (has private key)
     * tx.signAuthEntries(bobKeyPair)
     *
     * // Submit the fully-signed transaction
     * val result = tx.signAndSubmit(invokerKeyPair)
     * ```
     *
     * ## Remote Signing (Delegate Pattern)
     *
     * ```kotlin
     * // Bob's signing happens on a remote server
     * val bobPublicOnly = KeyPair.fromAccountId(bobId)
     * tx.signAuthEntries(
     *     authEntriesSigner = bobPublicOnly,
     *     authorizeEntryDelegate = { entry, network ->
     *         // Send to remote server
     *         val entryXdr = entry.toXdrBase64()
     *         val signedXdr = httpClient.post("/sign", entryXdr)
     *
     *         // Return signed entry
     *         SorobanAuthorizationEntryXdr.fromXdrBase64(signedXdr)
     *     }
     * )
     * ```
     *
     * ## Custom Expiration
     *
     * ```kotlin
     * // Get current ledger
     * val latestLedger = server.getLatestLedger()
     *
     * // Set expiration to 1 hour from now (~720 ledgers)
     * tx.signAuthEntries(
     *     authEntriesSigner = aliceKeyPair,
     *     validUntilLedgerSequence = latestLedger.sequence + 720
     * )
     * ```
     *
     * ## How It Works
     *
     * 1. The method finds all auth entries that match the signer's address
     * 2. For each matching entry:
     *    - Sets the expiration ledger
     *    - Signs the entry using Auth.authorizeEntry() or the delegate
     *    - Updates the signature in the entry
     * 3. Rebuilds the transaction with the updated auth entries
     *
     * @param authEntriesSigner The KeyPair to sign auth entries with (must match address in entry)
     * @param validUntilLedgerSequence Ledger sequence until which signatures are valid (null = current + 100)
     * @param authorizeEntryDelegate Optional function for custom signing logic (enables remote signing)
     * @return This AssembledTransaction for chaining
     * @throws NotYetSimulatedException if not yet simulated
     * @throws IllegalStateException if no entries need signing or wrong signer
     * @throws IllegalArgumentException if signer missing private key (when delegate not provided)
     */
    suspend fun signAuthEntries(
        authEntriesSigner: KeyPair,
        validUntilLedgerSequence: Long? = null,
        authorizeEntryDelegate: (suspend (SorobanAuthorizationEntryXdr, Network) -> SorobanAuthorizationEntryXdr)? = null
    ): AssembledTransaction<T> {
        if (builtTransaction == null) {
            throw NotYetSimulatedException("Transaction has not yet been simulated.", this)
        }

        val signerAddress = authEntriesSigner.getAccountId()

        // Validate signer has entries to sign (unless using delegate)
        if (authorizeEntryDelegate == null) {
            val neededSigning = needsNonInvokerSigningBy(includeAlreadySigned = false)
            if (neededSigning.isEmpty()) {
                throw IllegalStateException(
                    "No unsigned non-invoker auth entries; maybe you already signed?"
                )
            }
            if (!neededSigning.contains(signerAddress)) {
                throw IllegalStateException(
                    "No auth entries for public key $signerAddress. " +
                    "Addresses that need signing: $neededSigning"
                )
            }
            if (!authEntriesSigner.canSign()) {
                throw IllegalArgumentException(
                    "You must provide a signer keypair containing the private key."
                )
            }
        }

        // Get or calculate expiration ledger (default: current + 100)
        val expirationLedger = validUntilLedgerSequence ?: run {
            val latestLedger = server.getLatestLedger()
            latestLedger.sequence + 100
        }

        // Get the operation and its auth entries
        val operation = builtTransaction!!.operations.first()
        if (operation !is InvokeHostFunctionOperation) {
            throw IllegalStateException("Expected InvokeHostFunctionOperation, got ${operation::class.simpleName}")
        }

        // Create mutable list of auth entries
        val updatedAuthEntries = mutableListOf<SorobanAuthorizationEntryXdr>()

        // Iterate through auth entries and sign matching ones
        for (entry in operation.auth) {
            // Check if this entry uses address credentials
            if (entry.credentials.discriminant != SorobanCredentialsTypeXdr.SOROBAN_CREDENTIALS_ADDRESS) {
                updatedAuthEntries.add(entry)
                continue
            }

            val addressCreds = entry.credentials as? SorobanCredentialsXdr.Address
            if (addressCreds == null) {
                updatedAuthEntries.add(entry)
                continue
            }

            // Get the address from the credentials
            val entryAddress = Address.fromSCAddress(addressCreds.value.address).toString()

            // Skip if this entry is for a different signer
            if (entryAddress != signerAddress) {
                updatedAuthEntries.add(entry)
                continue
            }

            // Create updated address credentials with new expiration
            val updatedAddressCreds = addressCreds.value.copy(
                signatureExpirationLedger = Uint32Xdr(expirationLedger.toUInt())
            )

            // Create updated entry with new expiration
            val entryToSign = entry.copy(
                credentials = SorobanCredentialsXdr.Address(updatedAddressCreds)
            )

            // Sign the entry
            val signedEntry = if (authorizeEntryDelegate != null) {
                authorizeEntryDelegate(entryToSign, transactionBuilder.network)
            } else {
                Auth.authorizeEntry(entryToSign, authEntriesSigner, expirationLedger, transactionBuilder.network)
            }

            updatedAuthEntries.add(signedEntry)
        }

        // Rebuild the operation with updated auth entries
        val updatedOperation = InvokeHostFunctionOperation(
            hostFunction = operation.hostFunction,
            auth = updatedAuthEntries
        )
        updatedOperation.sourceAccount = operation.sourceAccount

        // Rebuild the transaction with the updated operation
        val currentTx = builtTransaction!!

        // Create new transaction builder with same source account from original builder
        val newBuilder = TransactionBuilder(
            sourceAccount = transactionBuilder.sourceAccount,
            network = transactionBuilder.network
        )
            .addOperation(updatedOperation)
            .setBaseFee(currentTx.fee.toLong())

        // Copy preconditions if they exist
        if (currentTx.preconditions != null) {
            newBuilder.addPreconditions(currentTx.preconditions!!)
        }

        // Copy soroban data if it exists
        if (currentTx.sorobanData != null) {
            newBuilder.setSorobanData(currentTx.sorobanData!!)
        }

        // Build the new transaction
        builtTransaction = newBuilder.build()

        // Important: Clear signed transaction since we modified builtTransaction
        // The transaction will need to be signed again (with sign() or signAndSubmit())
        signed = null

        return this
    }

    /**
     * Get the addresses that need to sign authorization entries.
     *
     * Returns account or contract addresses (G.., C..) that need to sign auth entries.
     * Useful for multi-sig scenarios where you need to collect signatures.
     *
     * @param includeAlreadySigned Whether to include already-signed entries (default false)
     * @return Set of addresses that need to sign
     * @throws NotYetSimulatedException if not yet simulated
     */
    fun needsNonInvokerSigningBy(includeAlreadySigned: Boolean = false): Set<String> {
        if (builtTransaction == null) {
            throw NotYetSimulatedException("Transaction has not yet been simulated.", this)
        }

        val operation = builtTransaction!!.operations.firstOrNull()
        if (operation !is InvokeHostFunctionOperation) {
            return emptySet()
        }

        return operation.auth
            .filter { it.credentials.discriminant == SorobanCredentialsTypeXdr.SOROBAN_CREDENTIALS_ADDRESS }
            .filter { entry ->
                val addressCreds = entry.credentials as? SorobanCredentialsXdr.Address
                if (addressCreds == null) {
                    false
                } else {
                    includeAlreadySigned ||
                    addressCreds.value.signature.discriminant == SCValTypeXdr.SCV_VOID
                }
            }
            .mapNotNull { entry ->
                val addressCreds = entry.credentials as? SorobanCredentialsXdr.Address
                addressCreds?.let {
                    Address.fromSCAddress(it.value.address).toString()
                }
            }
            .toSet()
    }

    /**
     * Get the result from simulation.
     *
     * For read-only calls, this returns the simulated result without submitting.
     * For write calls, use [submit] or [signAndSubmit] instead.
     *
     * @return The parsed result (or raw SCValXdr if parseResultXdrFn is null)
     * @throws NotYetSimulatedException if not yet simulated
     */
    fun result(): T {
        val simData = getSimulationData()
        val rawResult = simData.returnedValue

        return if (parseResultXdrFn != null) {
            parseResultXdrFn.invoke(rawResult)
        } else {
            @Suppress("UNCHECKED_CAST")
            rawResult as T
        }
    }

    /**
     * Check if this is a read-only call.
     *
     * A call is read-only if:
     * - It has no authorization entries
     * - It has no read-write footprint entries
     *
     * Read-only calls don't need signing or submitting - just call [result].
     *
     * @return true if read-only, false otherwise
     * @throws NotYetSimulatedException if not yet simulated
     */
    fun isReadCall(): Boolean {
        val simData = getSimulationData()
        val auths = simData.auth ?: emptyList()
        val writes = simData.transactionData.resources.footprint.readWrite
        return auths.isEmpty() && writes.isEmpty()
    }

    /**
     * Submits the transaction to the network.
     *
     * Sends the transaction and polls for completion. Parses the result using
     * parseResultXdrFn if provided.
     *
     * @return The parsed result (or raw SCValXdr if parseResultXdrFn is null)
     * @throws NotYetSimulatedException if not yet simulated
     * @throws SendTransactionFailedException if submission failed
     * @throws TransactionStillPendingException if still pending after timeout
     * @throws TransactionFailedException if transaction failed
     */
    suspend fun submit(): T {
        val response = submitInternal()

        // Parse result from transaction meta
        val transactionMeta = TransactionMetaXdr.fromXdrBase64(response.resultMetaXdr!!)
        val resultVal = when (transactionMeta) {
            is TransactionMetaXdr.V3 -> transactionMeta.value.sorobanMeta?.returnValue
            is TransactionMetaXdr.V4 -> transactionMeta.value.sorobanMeta?.returnValue
            else -> null
        } ?: throw IllegalStateException("No return value in transaction meta")

        return if (parseResultXdrFn != null) {
            parseResultXdrFn.invoke(resultVal)
        } else {
            @Suppress("UNCHECKED_CAST")
            resultVal as T
        }
    }

    /**
     * Get structured simulation result data.
     *
     * Returns the auth entries, transaction data, and return value from simulation.
     * This is useful when you need to inspect or manipulate simulation results.
     *
     * @return Structured simulation data
     * @throws NotYetSimulatedException if not yet simulated
     */
    fun getSimulationData(): SimulateHostFunctionResult {
        if (simulationResult != null && simulationTransactionData != null) {
            return SimulateHostFunctionResult(
                auth = simulationResult!!.parseAuth(),
                transactionData = simulationTransactionData!!,
                returnedValue = SCValXdr.fromXdrBase64(simulationResult!!.xdr!!)
            )
        }

        if (simulation == null) {
            throw NotYetSimulatedException("Transaction has not yet been simulated.", this)
        }

        simulationResult = simulation!!.results!!.first()
        simulationTransactionData = SorobanTransactionDataXdr.fromXdrBase64(simulation!!.transactionData!!)

        return SimulateHostFunctionResult(
            auth = simulationResult!!.parseAuth(),
            transactionData = simulationTransactionData!!,
            returnedValue = SCValXdr.fromXdrBase64(simulationResult!!.xdr!!)
        )
    }

    /**
     * Get the transaction envelope as base64-encoded XDR.
     *
     * @return The transaction envelope XDR string
     */
    fun toEnvelopeXdrBase64(): String {
        return builtTransaction?.toEnvelopeXdrBase64()
            ?: throw NotYetSimulatedException("Transaction has not yet been built.", this)
    }

    /**
     * Restores the contract footprint.
     *
     * Called automatically by [simulate] when restore=true and footprint needs restoration.
     * Can also be called manually if you want to restore before simulation.
     *
     * @throws TransactionFailedException if restoration transaction failed
     * @throws TransactionStillPendingException if restoration still pending
     * @throws SendTransactionFailedException if sending restoration failed
     */
    suspend fun restoreFootprint() {
        if (transactionSigner == null) {
            throw IllegalArgumentException(
                "For automatic restore to work you must provide a transactionSigner when initializing AssembledTransaction."
            )
        }

        val restoreTxBuilder = TransactionBuilder(
            sourceAccount = transactionBuilder.sourceAccount,
            network = transactionBuilder.network
        )
            .setBaseFee(100L)
            .addOperation(RestoreFootprintOperation())
            .setSorobanData(
                SorobanDataBuilder(simulation!!.restorePreamble!!.transactionData!!).build()
            )
            .addPreconditions(
                TransactionPreconditions(
                    timeBounds = TimeBounds(minTime = 0, maxTime = 0)
                )
            )

        val restoreAssembled = AssembledTransaction<SCValXdr>(
            server = server,
            submitTimeout = submitTimeout,
            transactionSigner = transactionSigner,
            parseResultXdrFn = null,
            transactionBuilder = restoreTxBuilder
        )

        restoreAssembled.simulate(restore = false)
        restoreAssembled.sign(transactionSigner, force = true)
        restoreAssembled.submitInternal()
    }

    private suspend fun submitInternal(): GetTransactionResponse {
        if (builtTransaction == null) {
            throw NotYetSimulatedException("Transaction has not yet been simulated.", this)
        }

        // Send transaction
        if (sendTransactionResponse == null) {
            sendTransactionResponse = server.sendTransaction(builtTransaction!!)
            if (sendTransactionResponse!!.status != SendTransactionStatus.PENDING) {
                throw SendTransactionFailedException(
                    "Sending the transaction to the network failed!",
                    this
                )
            }
        }

        // Poll for result with exponential backoff
        val txHash = sendTransactionResponse!!.hash!!
        val attempts = withExponentialBackoff(
            timeout = submitTimeout,
            fn = { server.getTransaction(txHash) },
            keepWaitingIf = { it.status == GetTransactionStatus.NOT_FOUND }
        )

        getTransactionResponse = attempts.last()

        return when (getTransactionResponse!!.status) {
            GetTransactionStatus.SUCCESS -> getTransactionResponse!!
            GetTransactionStatus.NOT_FOUND -> throw TransactionStillPendingException(
                "Waited $submitTimeout seconds for transaction to complete, but it did not. " +
                "Returning anyway. You can call result() to await the result later " +
                "or check the status of the transaction manually.",
                this
            )
            GetTransactionStatus.FAILED -> throw TransactionFailedException(
                "Transaction failed.",
                this
            )
        }
    }
}

/**
 * Result data from transaction simulation.
 *
 * Contains all the data returned by the Soroban RPC simulation, including:
 * - Authorization entries that need signing
 * - Transaction resource data (footprint, resource limits)
 * - The return value from the contract function
 *
 * This is useful for:
 * - Inspecting which accounts need to sign auth entries
 * - Understanding resource consumption
 * - Examining the simulated return value before submitting
 *
 * Example:
 * ```kotlin
 * val simData = tx.getSimulationData()
 * println("Auth entries: ${simData.auth?.size}")
 * println("Read footprint: ${simData.transactionData.resources.footprint.readOnly.size}")
 * println("Return value: ${simData.returnedValue}")
 * ```
 */
data class SimulateHostFunctionResult(
    /**
     * Authorization entries returned from simulation.
     * Null if the contract function doesn't require authorization.
     */
    val auth: List<SorobanAuthorizationEntryXdr>?,

    /**
     * Transaction resource data including footprint and resource limits.
     * This must be included in the transaction for execution.
     */
    val transactionData: SorobanTransactionDataXdr,

    /**
     * The return value from the simulated contract function call.
     * This is the result you would get if the transaction were executed.
     */
    val returnedValue: SCValXdr
)

/**
 * Exponential backoff helper for polling operations.
 *
 * @param timeout Total timeout in seconds
 * @param fn The function to call repeatedly
 * @param keepWaitingIf Predicate to determine if we should continue waiting
 * @return List of all attempts
 */
internal suspend fun <T> withExponentialBackoff(
    timeout: Int,
    fn: suspend () -> T,
    keepWaitingIf: (T) -> Boolean
): List<T> {
    val attempts = mutableListOf<T>()
    attempts.add(fn())

    if (!keepWaitingIf(attempts.first())) {
        return attempts
    }

    val startTime = currentTimeMillis()
    val waitUntil = startTime + (timeout * 1000L)
    var waitTime = 1000L
    val maxWaitTime = 60000L

    while (currentTimeMillis() < waitUntil && keepWaitingIf(attempts.last())) {
        delay(waitTime)
        attempts.add(fn())

        waitTime = (waitTime * 2).coerceAtMost(maxWaitTime)
        val remaining = waitUntil - currentTimeMillis()
        if (remaining < waitTime) {
            waitTime = remaining
        }
    }

    return attempts
}
