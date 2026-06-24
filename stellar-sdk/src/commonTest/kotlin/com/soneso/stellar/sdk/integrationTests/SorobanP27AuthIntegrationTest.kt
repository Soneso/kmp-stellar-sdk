package com.soneso.stellar.sdk.integrationTests

import com.soneso.stellar.sdk.*
import com.soneso.stellar.sdk.contract.ContractClient
import com.soneso.stellar.sdk.rpc.SorobanServer
import com.soneso.stellar.sdk.rpc.responses.GetTransactionStatus
import com.soneso.stellar.sdk.rpc.responses.SendTransactionStatus
import com.soneso.stellar.sdk.scval.Scv
import com.soneso.stellar.sdk.util.TestResourceUtil
import com.soneso.stellar.sdk.xdr.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

/**
 * Integration test for the Protocol 27 (CAP-71) ADDRESS_V2 credential arm against a live testnet.
 *
 * Uploads and deploys the auth contract, then invokes its `increment` function with the authorizing
 * account (the invoker) different from the transaction source. Simulation with `useUpgradedAuth`
 * returns an ADDRESS_V2 authorization entry for the invoker — a hard assertion proves the flag was
 * sent on the wire and honored by the server — and the entry is signed with [Auth.authorizeEntry],
 * which preserves the arm and signs over the address-bound (WITH_ADDRESS) preimage. The transaction
 * is submitted and the returned value is verified.
 *
 * ADDRESS_V2 is only valid on Protocol 27 or later, so this test requires a testnet running
 * Protocol 27 with a stellar-rpc that honors `useUpgradedAuth` (v27.1.0+), and network access to
 * the Soroban testnet RPC and Friendbot:
 * ```bash
 * ./gradlew :stellar-sdk:jvmTest --tests "SorobanP27AuthIntegrationTest"
 * ```
 *
 * @see Auth
 */
class SorobanP27AuthIntegrationTest {

    private val rpcUrl = "https://soroban-testnet.stellar.org"
    private val sorobanServer = SorobanServer(rpcUrl)
    private val network = Network.TESTNET

    /**
     * Invokes the auth contract with ADDRESS_V2 credentials end to end and verifies success.
     */
    @Test
    fun testInvokeWithAddressV2Credentials() = runTest(timeout = 300.seconds) {
        // Fund a submitter (transaction source) and a distinct invoker (the authorizing account).
        val submitter = KeyPair.random()
        val invoker = KeyPair.random()
        FriendBot.fundTestnetAccount(submitter.getAccountId())
        FriendBot.fundTestnetAccount(invoker.getAccountId())
        realDelay(5000)

        // Deploy the auth contract.
        val contractId = ContractClient.deploy(
            wasmBytes = TestResourceUtil.readWasmFile("soroban_auth_contract.wasm"),
            source = submitter.getAccountId(),
            signer = submitter,
            network = network,
            rpcUrl = rpcUrl
        ).contractId

        // Build the increment invocation; the invoker differs from the source, so simulation
        // returns an authorization entry for the invoker (with the ADDRESS_V2 arm, since
        // useUpgradedAuth is set below).
        realDelay(5000)
        val account = sorobanServer.getAccount(submitter.getAccountId())
        assertNotNull(account, "Submitter account should be loaded")

        val operation = InvokeHostFunctionOperation.invokeContractFunction(
            contractAddress = contractId,
            functionName = "increment",
            parameters = listOf(Address(invoker.getAccountId()).toSCVal(), Scv.toUint32(5u))
        )

        val transaction = TransactionBuilder(sourceAccount = account, network = network)
            .addOperation(operation)
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        // Request ADDRESS_V2 entries from the RPC. stellar-rpc v27.1.0+ honors the flag in
        // recording mode and records ADDRESS_V2 entries; servers without support silently
        // ignore it and return legacy ADDRESS entries.
        val simulateResponse = sorobanServer.simulateTransaction(transaction, useUpgradedAuth = true)
        assertNull(simulateResponse.error, "Simulation should not have error")
        assertNotNull(simulateResponse.results, "Simulation results should not be null")
        assertTrue(simulateResponse.results.isNotEmpty(), "Simulation should return a result")
        assertNotNull(simulateResponse.transactionData, "Transaction data should not be null")
        assertNotNull(simulateResponse.minResourceFee, "Min resource fee should not be null")

        val authBase64List = simulateResponse.results[0].auth
        assertNotNull(authBase64List, "Simulation should return authorization entries")
        assertTrue(authBase64List.isNotEmpty(), "There should be at least one authorization entry")
        val simulatedEntries = authBase64List.map { SorobanAuthorizationEntryXdr.fromXdrBase64(it) }

        // Testnet runs stellar-rpc v27.1.0+, so the invoker's entry must come back from
        // simulation with the ADDRESS_V2 arm already set — this proves the flag was sent on
        // the wire and honored by the server.
        assertTrue(
            simulatedEntries.any { it.credentials is SorobanCredentialsXdr.AddressV2 },
            "Simulation must return an ADDRESS_V2 auth entry when useUpgradedAuth is set " +
                "(requires stellar-rpc v27.1.0+)"
        )

        // Set the signature expiration from the latest ledger.
        val signatureExpirationLedger = sorobanServer.getLatestLedger().sequence + 100

        // Sign the server-returned ADDRESS_V2 entries with the invoker; authorizeEntry
        // preserves the arm and signs over the address-bound (WITH_ADDRESS) preimage.
        val signedAuthEntries = simulatedEntries.map { entry ->
            Auth.authorizeEntry(
                entry = entry,
                signer = invoker,
                validUntilLedgerSeq = signatureExpirationLedger,
                network = network
            )
        }

        // Each entry must carry the ADDRESS_V2 arm and use the address-bound (WITH_ADDRESS) preimage.
        val networkId = network.networkId()
        signedAuthEntries.forEach { entry ->
            assertTrue(
                entry.credentials is SorobanCredentialsXdr.AddressV2,
                "Each authorization entry must carry the ADDRESS_V2 arm"
            )
            val preimage = Auth.buildHashIDPreimage(entry.credentials, networkId, entry.rootInvocation)
            assertTrue(
                preimage is HashIDPreimageXdr.SorobanAuthorizationWithAddress,
                "ADDRESS_V2 must use the address-bound (WITH_ADDRESS) preimage"
            )
        }

        // Rebuild the operation with the signed ADDRESS_V2 entries.
        val signedOperation = InvokeHostFunctionOperation(
            hostFunction = operation.hostFunction,
            auth = signedAuthEntries
        )

        // TransactionBuilder.build() increments the sequence number; reset it so the rebuilt
        // transaction reuses the simulated sequence.
        account.setSequenceNumber(account.sequenceNumber - 1)
        val signedTransaction = TransactionBuilder(sourceAccount = account, network = network)
            .addOperation(signedOperation)
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        val preparedTransaction = sorobanServer.prepareTransaction(signedTransaction, simulateResponse)
        preparedTransaction.sign(submitter)

        val sendResponse = sorobanServer.sendTransaction(preparedTransaction)
        assertEquals(
            SendTransactionStatus.PENDING,
            sendResponse.status,
            "Submission must be accepted (PENDING); status=${sendResponse.status}, errorResultXdr=${sendResponse.errorResultXdr}"
        )
        assertNotNull(sendResponse.hash, "Transaction hash should not be null")

        // Poll generously: a valid submission can take longer to confirm under network load.
        val rpcTransactionResponse = sorobanServer.pollTransaction(
            hash = sendResponse.hash,
            maxAttempts = 50,
            sleepStrategy = { 3000L }
        )
        assertEquals(
            GetTransactionStatus.SUCCESS,
            rpcTransactionResponse.status,
            "Transaction signed with ADDRESS_V2 credentials should succeed on Protocol 27"
        )

        val resultValue = rpcTransactionResponse.getResultValue()
        assertNotNull(resultValue, "Result value should not be null")
        assertTrue(resultValue is SCValXdr.U32, "increment should return a u32")
        // increment returns the invoker's accumulated counter; a fresh invoker starts at 0, so a
        // single increment by 5 returns 5.
        assertEquals(5u, resultValue.value.value, "increment should return the accumulated counter value")
    }
}
