package com.soneso.stellar.sdk.contract

import com.soneso.stellar.sdk.*
import com.soneso.stellar.sdk.rpc.SorobanServer
import com.soneso.stellar.sdk.xdr.SCValXdr

/**
 * A client to interact with Soroban smart contracts.
 *
 * This client wraps [SorobanServer] and [TransactionBuilder] to make it easier
 * to interact with Soroban smart contracts. For more fine-grained control, use
 * them directly.
 *
 * ## Basic Usage
 *
 * ```kotlin
 * val client = ContractClient(
 *     contractId = "CABC...",
 *     rpcUrl = "https://soroban-testnet.stellar.org:443",
 *     network = Network.TESTNET
 * )
 *
 * // Read-only call (no signing needed)
 * val balance = client.invoke<Long>(
 *     functionName = "balance",
 *     parameters = listOf(Scv.toAddress(accountId)),
 *     source = accountId,
 *     signer = null,
 *     parseResultXdrFn = { Scv.fromInt128(it).toLong() }
 * ).result()
 *
 * // Write call (requires signing)
 * val result = client.invoke<Unit>(
 *     functionName = "transfer",
 *     parameters = listOf(
 *         Scv.toAddress(from),
 *         Scv.toAddress(to),
 *         Scv.toInt128(amount)
 *     ),
 *     source = accountId,
 *     signer = keypair,
 *     parseResultXdrFn = null // Void return
 * ).signAndSubmit(keypair)
 *
 * // Close when done
 * client.close()
 * ```
 *
 * @property contractId The contract ID to interact with (C... address)
 * @property network The network to interact with
 * @property server The SorobanServer instance for RPC calls
 */
class ContractClient(
    val contractId: String,
    rpcUrl: String,
    val network: Network
) {
    val server: SorobanServer = SorobanServer(rpcUrl)

    /**
     * Build an [AssembledTransaction] to invoke a function on the contract.
     *
     * This is a convenience method with default timeout values.
     *
     * @param functionName The name of the function to invoke
     * @param parameters The parameters to pass to the function (as SCValXdr)
     * @param source The source account for the transaction (G... or M... address)
     * @param signer The KeyPair to sign with (or null for read-only calls)
     * @param parseResultXdrFn Function to parse the result XDR (or null for raw SCValXdr)
     * @param baseFee The base fee for the transaction (default 100)
     * @return An AssembledTransaction ready for signing/submitting
     */
    suspend fun <T> invoke(
        functionName: String,
        parameters: List<SCValXdr>,
        source: String,
        signer: KeyPair?,
        parseResultXdrFn: ((SCValXdr) -> T)? = null,
        baseFee: Int = 100
    ): AssembledTransaction<T> {
        return invoke(
            functionName = functionName,
            parameters = parameters,
            source = source,
            signer = signer,
            parseResultXdrFn = parseResultXdrFn,
            baseFee = baseFee,
            transactionTimeout = 300,
            submitTimeout = 30,
            simulate = true,
            restore = true
        )
    }

    /**
     * Build an [AssembledTransaction] to invoke a function on the contract.
     *
     * Full control version with all configuration options.
     *
     * @param functionName The name of the function to invoke
     * @param parameters The parameters to pass to the function (as SCValXdr)
     * @param source The source account for the transaction (G... or M... address)
     * @param signer The KeyPair to sign with (or null for read-only calls)
     * @param parseResultXdrFn Function to parse the result XDR (or null for raw SCValXdr)
     * @param baseFee The base fee for the transaction (default 100)
     * @param transactionTimeout Transaction validity timeout in seconds (default 300)
     * @param submitTimeout Polling timeout when submitting in seconds (default 30)
     * @param simulate Whether to simulate the transaction (default true)
     * @param restore Whether to auto-restore contract state if needed (default true)
     * @return An AssembledTransaction ready for signing/submitting
     */
    suspend fun <T> invoke(
        functionName: String,
        parameters: List<SCValXdr>,
        source: String,
        signer: KeyPair?,
        parseResultXdrFn: ((SCValXdr) -> T)?,
        baseFee: Int = 100,
        transactionTimeout: Long = 300,
        submitTimeout: Int = 30,
        simulate: Boolean = true,
        restore: Boolean = true
    ): AssembledTransaction<T> {
        // Build InvokeHostFunctionOperation
        val operation = InvokeHostFunctionOperation.invokeContractFunction(
            contractAddress = contractId,
            functionName = functionName,
            parameters = parameters
        )

        // Create transaction builder with placeholder account (sequence will be updated in simulate)
        val builder = TransactionBuilder(
            sourceAccount = Account(source, 0L),
            network = network
        )
            .addOperation(operation)
            .setTimeout(transactionTimeout)
            .setBaseFee(baseFee.toLong())

        // Create assembled transaction
        val assembled = AssembledTransaction(
            server = server,
            submitTimeout = submitTimeout,
            transactionSigner = signer,
            parseResultXdrFn = parseResultXdrFn,
            transactionBuilder = builder
        )

        // Simulate if requested
        if (simulate) {
            assembled.simulate(restore)
        }

        return assembled
    }

    /**
     * Close the underlying SorobanServer connection.
     */
    fun close() {
        server.close()
    }
}
