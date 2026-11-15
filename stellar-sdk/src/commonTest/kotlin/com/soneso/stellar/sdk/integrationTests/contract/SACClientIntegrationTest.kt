package com.soneso.stellar.sdk.integrationTests.contract

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.soneso.stellar.sdk.AbstractTransaction
import com.soneso.stellar.sdk.Address
import com.soneso.stellar.sdk.Asset
import com.soneso.stellar.sdk.AssetTypeCreditAlphaNum4
import com.soneso.stellar.sdk.AssetTypeNative
import com.soneso.stellar.sdk.ChangeTrustOperation
import com.soneso.stellar.sdk.InvokeHostFunctionOperation
import com.soneso.stellar.sdk.KeyPair
import com.soneso.stellar.sdk.MuxedAccount
import com.soneso.stellar.sdk.MuxedContract
import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.PaymentOperation
import com.soneso.stellar.sdk.TransactionBuilder
import com.soneso.stellar.sdk.TransactionPreconditions
import com.soneso.stellar.sdk.contract.ContractClient
import com.soneso.stellar.sdk.contract.SACClient
import com.soneso.stellar.sdk.horizon.HorizonServer
import com.soneso.stellar.sdk.integrationTests.fundTestAccountAndAwaitVisibility
import com.soneso.stellar.sdk.integrationTests.networkProtocolAtLeast
import com.soneso.stellar.sdk.integrationTests.realDelay
import com.soneso.stellar.sdk.rpc.SorobanServer
import com.soneso.stellar.sdk.rpc.responses.GetTransactionResponse
import com.soneso.stellar.sdk.rpc.responses.GetTransactionStatus
import com.soneso.stellar.sdk.rpc.responses.SendTransactionStatus
import com.soneso.stellar.sdk.scval.Scv
import com.soneso.stellar.sdk.unitTests.contract.SAC_FUNCTION_NAMES
import com.soneso.stellar.sdk.unitTests.contract.jsonRpcClient
import com.soneso.stellar.sdk.unitTests.rpc.responses.RpcReply
import com.soneso.stellar.sdk.xdr.ContractEventBodyXdr
import com.soneso.stellar.sdk.xdr.ContractEventV0Xdr
import com.soneso.stellar.sdk.xdr.SCValXdr
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Verifies [SACClient], the Stellar Asset Contract detection of [ContractClient.forContract],
 * and [InvokeHostFunctionOperation.createStellarAssetContract] against testnet.
 *
 * Each network test funds its own issuer and two holders, A and B, through Friendbot, gives A
 * and B a trustline to the issuer's `TEST` asset, deploys the asset's Stellar Asset Contract,
 * and pays A 1000 TEST. [testSacClient] then opens the contract through
 * [ContractClient.forContract], reads it with a read-only [SACClient], transfers from A to B,
 * to a muxed account (M...) of B, and from A with the issuer as the source, and reads the
 * Stellar Asset Contract of XLM. [testTransferToMuxedContract] transfers to a muxed contract
 * (W...) address and is gated on protocol 30 through [networkProtocolAtLeast]; below that it
 * prints why it skipped and returns. [testProtocolGate] checks that gate offline.
 */
class SACClientIntegrationTest {

    /**
     * The accounts and the asset a network test runs on: the issuer of [asset], the holders [a]
     * and [b] with a trustline to it, and the id of its deployed Stellar Asset Contract.
     */
    private class SacContext(
        val asset: Asset,
        val issuer: KeyPair,
        val a: KeyPair,
        val b: KeyPair,
        val contractId: String
    )

    @Test
    fun testSacClient() = runTest(timeout = 600.seconds) {
        val sorobanServer = SorobanServer(RPC_URL)
        val horizonServer = HorizonServer(HORIZON_URL)
        val opened = mutableListOf<SACClient>()
        try {
            val context = deployTestAsset(sorobanServer, horizonServer)
            val sacId = context.contractId
            val aId = context.a.getAccountId()
            val bId = context.b.getAccountId()
            val issuerId = context.issuer.getAccountId()

            // Detection: forContract opens the contract with the embedded spec.
            val detected = ContractClient.forContract(sacId, RPC_URL, Network.TESTNET)
            try {
                assertEquals(SAC_FUNCTION_NAMES.toSet(), detected.getMethodNames())
            } finally {
                detected.close()
            }

            // Read-only reads.
            val reader = SACClient.readOnlyForAsset(context.asset, Network.TESTNET, RPC_URL).also { opened += it }
            assertEquals(BigInteger.fromLong(10_000_000_000L), reader.balance(Address(aId)))
            assertEquals(7u, reader.decimals())
            assertEquals("TEST:$issuerId", reader.name())
            assertEquals("TEST", reader.symbol())

            // A to B, with A as the source.
            val senderA = SACClient.forAsset(context.asset, context.a, Network.TESTNET, RPC_URL).also { opened += it }
            val toB = senderA.transferAndSubmit(Address(bId), SACClient.toBaseUnits("100"))
            assertEquals(GetTransactionStatus.SUCCESS, toB.status)
            awaitReplicas()
            val bAfterFirstTransfer = BigInteger.fromLong(1_000_000_000L)
            assertEquals(bAfterFirstTransfer, reader.balance(Address(bId)))
            assertEquals(BigInteger.fromLong(9_000_000_000L), reader.balance(Address(aId)))

            // A to a muxed account (M...) of B.
            val muxedAmount = BigInteger.fromLong(250_000_000L)
            val toMuxedB = senderA.transfer(
                Address(aId),
                Address(MuxedAccount(bId, 4242uL).address),
                muxedAmount
            )
            toMuxedB.signAndSubmit()
            val muxedResponse = assertNotNull(toMuxedB.getTransactionResponse)
            assertEquals(GetTransactionStatus.SUCCESS, muxedResponse.status)
            val muxedEvent = transferEvent(muxedResponse)
            assertEquals(bId, Address.fromSCVal(muxedEvent.topics[2]).toString())
            val muxedData = Scv.fromMap(muxedEvent.data)
            assertEquals(muxedAmount, Scv.fromInt128(assertNotNull(muxedData[Scv.toSymbol("amount")])))
            assertEquals(4242uL, Scv.fromUint64(assertIs<SCValXdr.U64>(muxedData[Scv.toSymbol("to_muxed_id")])))
            awaitReplicas()
            val bAfterMuxedTransfer = bAfterFirstTransfer + muxedAmount
            assertEquals(bAfterMuxedTransfer, reader.balance(Address(bId)))

            // The issuer as the source; A authorizes its transfer.
            val senderIssuer = SACClient.forAsset(context.asset, context.issuer, Network.TESTNET, RPC_URL)
                .also { opened += it }
            val authorizedAmount = SACClient.toBaseUnits("10.5")
            assertEquals(BigInteger.fromLong(105_000_000L), authorizedAmount)
            val authorized = senderIssuer.transfer(Address(aId), Address(bId), authorizedAmount)
            assertEquals(setOf(aId), authorized.needsNonInvokerSigningBy())
            authorized.signAuthEntries(context.a)
            authorized.signAndSubmit()
            assertEquals(GetTransactionStatus.SUCCESS, assertNotNull(authorized.getTransactionResponse).status)
            awaitReplicas()
            assertEquals(bAfterMuxedTransfer + BigInteger.fromLong(105_000_000L), reader.balance(Address(bId)))

            // The Stellar Asset Contract of XLM.
            val xlmReader = SACClient.readOnlyForAsset(AssetTypeNative, Network.TESTNET, RPC_URL).also { opened += it }
            assertEquals(TESTNET_XLM_CONTRACT_ID, xlmReader.contractId)
            assertTrue(xlmReader.balance(Address(aId)) > BigInteger.ZERO, "A should hold XLM")
            assertEquals(7u, xlmReader.decimals())
            assertEquals("native", xlmReader.name())
            assertEquals("native", xlmReader.symbol())
        } finally {
            opened.forEach { it.close() }
            sorobanServer.close()
            horizonServer.close()
        }
    }

    @Test
    fun testTransferToMuxedContract() = runTest(timeout = 600.seconds) {
        val sorobanServer = SorobanServer(RPC_URL)
        val horizonServer = HorizonServer(HORIZON_URL)
        val opened = mutableListOf<SACClient>()
        try {
            if (!networkProtocolAtLeast(sorobanServer, 30)) {
                println(
                    "Skipping the muxed contract transfer test: muxed contract (W...) destinations need " +
                        "protocol 30, and the network at $RPC_URL runs a protocol below 30"
                )
                return@runTest
            }

            val context = deployTestAsset(sorobanServer, horizonServer)
            val sacId = context.contractId
            val sender = SACClient.forAsset(context.asset, context.a, Network.TESTNET, RPC_URL).also { opened += it }
            val reader = SACClient.readOnlyForAsset(context.asset, Network.TESTNET, RPC_URL).also { opened += it }

            val amount = BigInteger.fromLong(50_000_000L)
            val toMuxedContract = sender.transfer(
                Address(context.a.getAccountId()),
                Address(MuxedContract(sacId, 7uL).address),
                amount
            )
            toMuxedContract.signAndSubmit()
            val response = assertNotNull(toMuxedContract.getTransactionResponse)
            assertEquals(GetTransactionStatus.SUCCESS, response.status)
            val data = Scv.fromMap(transferEvent(response).data)
            assertEquals(7uL, Scv.fromUint64(assertIs<SCValXdr.U64>(data[Scv.toSymbol("to_muxed_id")])))
            awaitReplicas()
            assertEquals(amount, reader.balance(Address(sacId)))
        } finally {
            opened.forEach { it.close() }
            sorobanServer.close()
            horizonServer.close()
        }
    }

    @Test
    fun testProtocolGate() = runTest {
        latestLedgerServer("""{ "id": "$LEDGER_HASH", "protocolVersion": 30, "sequence": 1000 }""").use { server ->
            assertTrue(networkProtocolAtLeast(server, 30))
        }
        latestLedgerServer("""{ "id": "$LEDGER_HASH", "protocolVersion": 29, "sequence": 1000 }""").use { server ->
            assertFalse(networkProtocolAtLeast(server, 30))
        }
        latestLedgerServer("""{ "id": "$LEDGER_HASH", "sequence": 1000 }""").use { server ->
            val failure = assertFailsWith<IllegalArgumentException> { networkProtocolAtLeast(server, 30) }
            assertTrue(
                failure.message.orEmpty().startsWith("Failed to parse response for method getLatestLedger:"),
                "Unexpected message: ${failure.message}"
            )
        }
    }

    /**
     * Funds an issuer and two holders, A and B, gives A and B a trustline to the issuer's `TEST`
     * asset, deploys the asset's Stellar Asset Contract, and pays A 1000 TEST.
     */
    private suspend fun deployTestAsset(sorobanServer: SorobanServer, horizonServer: HorizonServer): SacContext {
        val issuer = KeyPair.random()
        val a = KeyPair.random()
        val b = KeyPair.random()
        for (keyPair in listOf(issuer, a, b)) {
            fundTestAccountAndAwaitVisibility(keyPair.getAccountId(), rpc = sorobanServer, horizon = horizonServer)
        }
        val issuerId = issuer.getAccountId()
        val asset = AssetTypeCreditAlphaNum4("TEST", issuerId)

        // Building a transaction increments the sequence number of this account object, so it
        // serves the three issuer transactions without a reload.
        val issuerAccount = sorobanServer.getAccount(issuerId)

        // Trustlines: one issuer transaction with a trustline operation for each holder.
        val trustA = ChangeTrustOperation(asset = asset).apply { sourceAccount = a.getAccountId() }
        val trustB = ChangeTrustOperation(asset = asset).apply { sourceAccount = b.getAccountId() }
        val trustlines = TransactionBuilder(sourceAccount = issuerAccount, network = Network.TESTNET)
            .addOperation(trustA)
            .addOperation(trustB)
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()
        trustlines.sign(issuer)
        trustlines.sign(a)
        trustlines.sign(b)
        val trustlineResponse = horizonServer.submitTransaction(trustlines.toEnvelopeXdrBase64())
        assertTrue(trustlineResponse.successful, "The trustline transaction should succeed")
        realDelay(5000)

        // Deploy the Stellar Asset Contract of the asset.
        val deploy = TransactionBuilder(sourceAccount = issuerAccount, network = Network.TESTNET)
            .addOperation(InvokeHostFunctionOperation.createStellarAssetContract(asset))
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()
        val simulateResponse = sorobanServer.simulateTransaction(deploy)
        assertNull(simulateResponse.error, "The deploy simulation should succeed")
        val preparedDeploy = sorobanServer.prepareTransaction(deploy, simulateResponse)
        preparedDeploy.sign(issuer)
        val sendResponse = sorobanServer.sendTransaction(preparedDeploy)
        assertEquals(
            SendTransactionStatus.PENDING,
            sendResponse.status,
            "The network should accept the deploy: ${sendResponse.errorResultXdr}"
        )
        val deployResponse = sorobanServer.pollTransaction(
            hash = assertNotNull(sendResponse.hash, "The deploy should have a hash"),
            maxAttempts = 30,
            sleepStrategy = { 3000L }
        )
        assertEquals(GetTransactionStatus.SUCCESS, deployResponse.status, "The deploy should succeed")
        val contractId = asset.getContractId(Network.TESTNET)
        assertEquals(contractId, deployResponse.getCreatedContractId())
        println("Deployed the Stellar Asset Contract $contractId of TEST:$issuerId")

        // The issuer pays A 1000 TEST.
        val payment = TransactionBuilder(sourceAccount = issuerAccount, network = Network.TESTNET)
            .addOperation(PaymentOperation(destination = a.getAccountId(), asset = asset, amount = "1000"))
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()
        payment.sign(issuer)
        val paymentResponse = horizonServer.submitTransaction(payment.toEnvelopeXdrBase64())
        assertTrue(paymentResponse.successful, "The payment to A should succeed")
        realDelay(5000)

        return SacContext(asset, issuer, a, b, contractId)
    }

    /**
     * Returns the one event of the first operation of [response] whose first topic is the symbol
     * `transfer`, after asserting that the response carries its contract events.
     */
    private fun transferEvent(response: GetTransactionResponse): ContractEventV0Xdr {
        val events = assertNotNull(response.events, "The transaction response should carry its events")
        assertNotNull(events.contractEventsXdr, "The events should carry the contract events")
        val operationEvents = events.parseContractEventsXdr()!![0].map { event ->
            when (val body = event.body) {
                is ContractEventBodyXdr.V0 -> body.value
            }
        }
        val transfers = operationEvents.filter { it.topics.firstOrNull() == Scv.toSymbol("transfer") }
        assertEquals(1, transfers.size, "The first operation should emit one transfer event")
        return transfers.single()
    }

    /**
     * Waits three seconds in wall-clock time after a submission. The RPC answers from
     * replicas that can trail the one that reported the result, and the following read needs
     * the new balances and the following write the current sequence number.
     */
    private suspend fun awaitReplicas() {
        realDelay(3000)
    }

    /**
     * Returns a [SorobanServer] on a Ktor MockEngine that answers `getLatestLedger` with
     * [resultJson] as its result and fails any other method.
     */
    private fun latestLedgerServer(resultJson: String): SorobanServer {
        val client = jsonRpcClient { body ->
            check(body["method"]!!.jsonPrimitive.content == "getLatestLedger") { "Unexpected JSON-RPC request: $body" }
            RpcReply("""{ "jsonrpc": "2.0", "id": "1", "result": $resultJson }""")
        }
        return SorobanServer(RPC_URL, client)
    }

    private companion object {
        const val RPC_URL = "https://soroban-testnet.stellar.org"
        const val HORIZON_URL = "https://horizon-testnet.stellar.org"

        /** The contract id of the Stellar Asset Contract of XLM on testnet. */
        const val TESTNET_XLM_CONTRACT_ID = "CDLZFC3SYJYDZT7K67VZ75HPJVIEUVNIXF47ZG2FB2RMQQVU2HHGCYSC"

        /** A ledger hash for the mocked `getLatestLedger` answers. */
        const val LEDGER_HASH = "0f4c1b3a5d7e9f80a2b4c6d8e0f1a3b5c7d9e1f2a4b6c8d0e2f4a6b8c0d2e4f6"
    }
}
