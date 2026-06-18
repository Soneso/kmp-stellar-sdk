package com.soneso.stellar.sdk.integrationTests

import com.soneso.stellar.sdk.*
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
 * Integration test for the Protocol 27 (CAP-71) ADDRESS_WITH_DELEGATES credential arm against a
 * live testnet.
 *
 * Deploys a modular custom account whose `__check_auth` carries no signature of its own and instead
 * forwards authorization to its registered delegate signers via the CAP-71 delegation host functions
 * (`get_delegated_signers` / `delegate_auth`). A protected business call (the auth contract's
 * `increment`) is invoked with the modular account as the authorizing address, so the host calls the
 * modular account's `__check_auth`, which delegates to a registered G-account signer.
 *
 * Flow:
 *  1. Deploy the modular account, registering a G-account as an allowed delegate signer.
 *  2. Invoke `increment(user = modular account, value = 5)`; the modular account must authorize it.
 *  3. Simulate (recording mode) to obtain the legacy ADDRESS authorization entry and its nonce.
 *  4. Convert the entry to ADDRESS_WITH_DELEGATES, preserving the simulated nonce, attaching the
 *     G-account as a delegate, and signing only the delegate node (the top-level signature stays
 *     Void because the modular account verifies no signature of its own).
 *  5. Re-simulate with the signed entry attached (enforcing mode) so the modular account's
 *     `__check_auth` runs and its footprint reads are captured, then submit and verify success.
 *
 * ADDRESS_WITH_DELEGATES is only valid on Protocol 27 or later, so this test requires a testnet
 * running Protocol 27 and network access to the Soroban testnet RPC and Friendbot:
 * ```bash
 * ./gradlew :stellar-sdk:jvmTest --tests "SorobanP27WithDelegatesIntegrationTest"
 * ```
 *
 * @see Auth
 */
class SorobanP27WithDelegatesIntegrationTest {

    private val sorobanServer = SorobanServer("https://soroban-testnet.stellar.org")
    private val network = Network.TESTNET

    /**
     * Authorizes a contract call via ADDRESS_WITH_DELEGATES credentials end to end and verifies success.
     */
    @Test
    fun testInvokeWithAddressWithDelegatesCredentials() = runTest(timeout = 300.seconds) {
        // Fund a submitter (transaction source) and a distinct delegate (a G-account that authorizes
        // on behalf of the modular account).
        val submitter = KeyPair.random()
        val delegate = KeyPair.random()
        FriendBot.fundTestnetAccount(submitter.getAccountId())
        FriendBot.fundTestnetAccount(delegate.getAccountId())
        realDelay(5000)

        // Deploy the modular custom account, registering the delegate G-account as an allowed signer.
        val modularWasmId = uploadContract(submitter, "soroban_modular_account_contract.wasm")
        val modularAccountId = createContract(
            submitter = submitter,
            wasmId = modularWasmId,
            salt = ByteArray(32) { 1 },
            constructorArgs = listOf(Scv.toVec(listOf(Address(delegate.getAccountId()).toSCVal())))
        )

        // Deploy the auth (increment) business contract.
        val authWasmId = uploadContract(submitter, "soroban_auth_contract.wasm")
        val authContractId = createContract(
            submitter = submitter,
            wasmId = authWasmId,
            salt = ByteArray(32) { 2 }
        )

        realDelay(5000)
        val account = sorobanServer.getAccount(submitter.getAccountId())
        assertNotNull(account, "Submitter account should be loaded")

        // increment(user = modular account, value = 5) requires the modular account's authorization,
        // so the host invokes its __check_auth, which delegates to the registered G-account.
        val operation = InvokeHostFunctionOperation.invokeContractFunction(
            contractAddress = authContractId,
            functionName = "increment",
            parameters = listOf(Address(modularAccountId).toSCVal(), Scv.toUint32(5u))
        )

        val transaction = TransactionBuilder(sourceAccount = account, network = network)
            .addOperation(operation)
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        // Recording-mode simulation: returns the legacy ADDRESS authorization entry for the modular
        // account (with the RPC-assigned nonce). __check_auth is not executed in this pass.
        val simulateResponse = sorobanServer.simulateTransaction(transaction)
        assertNull(simulateResponse.error, "Simulation should not have error")
        assertNotNull(simulateResponse.results, "Simulation results should not be null")
        assertTrue(simulateResponse.results.isNotEmpty(), "Simulation should return a result")

        val authBase64List = simulateResponse.results[0].auth
        assertNotNull(authBase64List, "Simulation should return authorization entries")
        assertTrue(authBase64List.isNotEmpty(), "There should be at least one authorization entry")
        val simulatedEntries = authBase64List.map { SorobanAuthorizationEntryXdr.fromXdrBase64(it) }

        // Set the signature expiration from the latest ledger.
        val signatureExpirationLedger = sorobanServer.getLatestLedger().sequence + 100

        // Convert each ADDRESS entry to the ADDRESS_WITH_DELEGATES arm, preserving the simulated nonce
        // so the recorded footprint still matches, attaching the delegate, and signing only the
        // delegate node. The top-level signature stays Void: the modular account verifies no signature
        // of its own and authorizes purely through its delegate.
        val signedAuthEntries = simulatedEntries.map { entry ->
            val withDelegates = Auth.attachDelegates(
                entry = entry,
                validUntilLedgerSeq = signatureExpirationLedger,
                delegates = listOf(DelegateDescriptor(address = delegate.getAccountId()))
            )
            Auth.authorizeEntry(
                entry = withDelegates,
                signer = delegate,
                validUntilLedgerSeq = signatureExpirationLedger,
                network = network,
                options = Auth.AuthOptions(forAddress = delegate.getAccountId())
            )
        }

        // Each entry must carry the ADDRESS_WITH_DELEGATES arm with a Void top-level signature and a
        // signed delegate node for the G-account.
        signedAuthEntries.forEach { entry ->
            val credentials = entry.credentials
            assertTrue(
                credentials is SorobanCredentialsXdr.AddressWithDelegates,
                "Each authorization entry must carry the ADDRESS_WITH_DELEGATES arm"
            )
            assertEquals(
                Scv.toVoid(),
                credentials.value.addressCredentials.signature,
                "The top-level signature must remain Void (the modular account signs nothing itself)"
            )
            assertEquals(
                1,
                credentials.value.delegates.size,
                "Exactly one delegate node should be attached"
            )
            val node = credentials.value.delegates.firstOrNull {
                Address.fromSCAddress(it.address).toString() == delegate.getAccountId()
            }
            assertNotNull(node, "A delegate node for the G-account signer must be present")
            assertTrue(
                node.signature is SCValXdr.Vec,
                "The delegate node must carry a signature (a Vec of account signatures) after signing"
            )
        }

        // Rebuild the operation with the signed ADDRESS_WITH_DELEGATES entries.
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

        // Enforcing-mode re-simulation: the signed auth is present, so the modular account's
        // __check_auth runs and its storage reads plus the delegate's account entry are captured in
        // the footprint. The recording-mode simulation above could not have captured them.
        val reSimulation = sorobanServer.simulateTransaction(signedTransaction)
        assertNull(reSimulation.error, "Re-simulation with signed auth should not have error")

        // prepareTransaction merges the complete footprint and resource fees from the enforcing
        // re-simulation while preserving the already-signed auth entries.
        val preparedTransaction = sorobanServer.prepareTransaction(signedTransaction, reSimulation)
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
            "Transaction authorized via ADDRESS_WITH_DELEGATES credentials should succeed on Protocol 27"
        )

        val resultValue = rpcTransactionResponse.getResultValue()
        assertNotNull(resultValue, "Result value should not be null")
        assertTrue(resultValue is SCValXdr.U32, "increment should return a u32")
        // increment returns the modular account's accumulated counter; a fresh account starts at 0,
        // so a single increment by 5 returns 5, proving the delegated authorization succeeded.
        assertEquals(5u, resultValue.value.value, "increment should return the accumulated counter value")
    }

    // ============================================================================
    // Helper Methods
    // ============================================================================

    /** Uploads a contract WASM from the test resources and returns its WASM id. */
    private suspend fun uploadContract(submitter: KeyPair, wasmFileName: String): String {
        val account = sorobanServer.getAccount(submitter.getAccountId())
        assertNotNull(account, "Submitter account should be loaded")

        val contractCode = TestResourceUtil.readWasmFile(wasmFileName)
        assertTrue(contractCode.isNotEmpty(), "Contract code should not be empty")

        val transaction = TransactionBuilder(sourceAccount = account, network = network)
            .addOperation(InvokeHostFunctionOperation.uploadContractWasm(contractCode))
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        val simulateResponse = sorobanServer.simulateTransaction(transaction)
        assertNull(simulateResponse.error, "Upload simulation should not have error")
        val preparedTransaction = sorobanServer.prepareTransaction(transaction, simulateResponse)
        preparedTransaction.sign(submitter)

        val sendResponse = sorobanServer.sendTransaction(preparedTransaction)
        assertNotNull(sendResponse.hash, "Upload transaction hash should not be null")
        val rpcTransactionResponse = sorobanServer.pollTransaction(
            hash = sendResponse.hash, maxAttempts = 30, sleepStrategy = { 3000L }
        )
        assertEquals(GetTransactionStatus.SUCCESS, rpcTransactionResponse.status, "Upload should succeed")

        val wasmId = rpcTransactionResponse.getWasmId()
        assertNotNull(wasmId, "WASM id should be returned")
        assertTrue(wasmId.isNotEmpty(), "WASM id should not be empty")
        return wasmId
    }

    /**
     * Deploys a contract instance from the uploaded WASM and returns its contract id. Passes
     * [constructorArgs] (selecting CreateContractV2) when the contract declares a `__constructor`.
     */
    private suspend fun createContract(
        submitter: KeyPair,
        wasmId: String,
        salt: ByteArray,
        constructorArgs: List<SCValXdr>? = null
    ): String {
        realDelay(5000)
        val account = sorobanServer.getAccount(submitter.getAccountId())
        assertNotNull(account, "Submitter account should be loaded")

        val transaction = TransactionBuilder(sourceAccount = account, network = network)
            .addOperation(
                InvokeHostFunctionOperation.createContract(
                    wasmId = wasmId,
                    address = Address(submitter.getAccountId()),
                    constructorArgs = constructorArgs,
                    salt = salt
                )
            )
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        val simulateResponse = sorobanServer.simulateTransaction(transaction)
        assertNull(simulateResponse.error, "Create simulation should not have error")
        val preparedTransaction = sorobanServer.prepareTransaction(transaction, simulateResponse)
        preparedTransaction.sign(submitter)

        val sendResponse = sorobanServer.sendTransaction(preparedTransaction)
        assertNotNull(sendResponse.hash, "Create transaction hash should not be null")
        val rpcTransactionResponse = sorobanServer.pollTransaction(
            hash = sendResponse.hash, maxAttempts = 30, sleepStrategy = { 3000L }
        )
        assertEquals(GetTransactionStatus.SUCCESS, rpcTransactionResponse.status, "Create should succeed")

        val contractId = rpcTransactionResponse.getCreatedContractId()
        assertNotNull(contractId, "Contract id should be returned")
        assertTrue(contractId.isNotEmpty(), "Contract id should not be empty")
        return contractId
    }
}
