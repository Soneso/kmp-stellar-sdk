package com.soneso.stellar.sdk.unitTests.contract

import com.soneso.stellar.sdk.*
import com.soneso.stellar.sdk.contract.AssembledTransaction
import com.soneso.stellar.sdk.contract.exception.NeedsMoreSignaturesException
import com.soneso.stellar.sdk.unitTests.AuthTest
import com.soneso.stellar.sdk.unitTests.assertSingleSignatureVerifies
import com.soneso.stellar.sdk.unitTests.placeholderEntry
import com.soneso.stellar.sdk.rpc.SorobanServer
import com.soneso.stellar.sdk.scval.Scv
import com.soneso.stellar.sdk.xdr.*
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.*

class UnsignedPlaceholderAssembledTransactionTest {
    companion object {
        private val NETWORK = Network.TESTNET
        private const val CONTRACT_ID = AuthTest.CONTRACT_ID
    }

    /**
     * Builds a MockEngine routing on the JSON-RPC method name. The simulate response
     * carries [authEntries]; [latestLedgerSeq] answers each getLatestLedger call.
     */
    private fun mockServer(
        authEntries: List<SorobanAuthorizationEntryXdr>,
        latestLedgerSeq: () -> Long = { 20000L }
    ): SorobanServer {
        val engine = MockEngine { request ->
            val body = request.body.toByteArray().decodeToString()
            val resultJson = when {
                "\"getLedgerEntries\"" in body -> ledgerEntriesResultJson()
                "\"simulateTransaction\"" in body -> {
                    simulateResultJson(Scv.toVoid(), authEntries)
                }
                "\"getLatestLedger\"" in body ->
                    """{ "id": "abc", "protocolVersion": 22, "sequence": ${latestLedgerSeq()}, "closeTime": 1700000000, "headerXdr": "AA==", "metadataXdr": "AA==" }"""
                else -> "{}"
            }
            respond(
                content = ByteReadChannel(
                    """{ "jsonrpc": "2.0", "id": "1", "result": $resultJson }"""
                ),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = false })
            }
        }
        return SorobanServer(MOCK_RPC_SERVER_URL, client)
    }

    private fun invokeBuilder(): TransactionBuilder =
        TransactionBuilder(
            sourceAccount = Account(MOCK_RPC_SOURCE_ACCOUNT, MOCK_RPC_SOURCE_SEQ),
            network = NETWORK
        )
            .addOperation(
                InvokeHostFunctionOperation.invokeContractFunction(
                    contractAddress = CONTRACT_ID,
                    functionName = "hello",
                    parameters = emptyList()
                )
            )
            .setTimeout(300L)
            .setBaseFee(100L)

    private fun assembled(
        server: SorobanServer,
        signer: KeyPair?,
        useUpgradedAuth: Boolean = true
    ): AssembledTransaction<SCValXdr> {
        return AssembledTransaction(
            server = server,
            submitTimeout = 30,
            transactionSigner = signer,
            parseResultXdrFn = null,
            transactionBuilder = invokeBuilder(),
            useUpgradedAuth = useUpgradedAuth
        )
    }

    private fun firstEntry(tx: AssembledTransaction<SCValXdr>): SorobanAuthorizationEntryXdr {
        val op = tx.builtTransaction!!.operations.first() as InvokeHostFunctionOperation
        return op.auth.first()
    }

    private suspend fun assertSigning(authV2: Boolean, explicitExpiration: Boolean) {
        val signer = KeyPair.fromSecretSeed(AuthTest.SECRET_SEED)
        var ledgerCalls = 0
        mockServer(listOf(placeholderEntry(signer.getAccountId(), authV2)), latestLedgerSeq = {
            ledgerCalls++
            20000L
        }).use { server ->
            val tx = assembled(server, signer).simulate(restore = false)
            ledgerCalls = 0
            if (explicitExpiration) {
                tx.signAuthEntries(signer, validUntilLedgerSequence = 500)
            } else {
                tx.signAuthEntries(signer)
            }
            val entry = firstEntry(tx)
            val expectedArm = if (authV2) SorobanCredentialsTypeXdr.SOROBAN_CREDENTIALS_ADDRESS_V2
                else SorobanCredentialsTypeXdr.SOROBAN_CREDENTIALS_ADDRESS
            assertEquals(expectedArm, entry.credentials.discriminant)
            assertEquals(
                if (explicitExpiration) 500u else 20100u,
                entry.credentials.requireAddressCredentials().signatureExpirationLedger.value
            )
            assertEquals(if (explicitExpiration) 0 else 1, ledgerCalls)
            assertSingleSignatureVerifies(entry, signer)
            assertFalse(signer.getAccountId() in tx.needsNonInvokerSigningBy())
            tx.sign()
            assertNotNull(tx.signed)
        }
    }

    @Test
    fun testAddressDefaultExpiration() = runTest { assertSigning(false, false) }

    @Test
    fun testAddressV2DefaultExpiration() = runTest { assertSigning(true, false) }

    @Test
    fun testAddressExplicitExpiration() = runTest { assertSigning(false, true) }

    @Test
    fun testAddressV2ExplicitExpiration() = runTest { assertSigning(true, true) }

    private suspend fun assertSignerNeeded(authV2: Boolean) {
        val signer = KeyPair.fromSecretSeed(AuthTest.SECRET_SEED)
        mockServer(listOf(placeholderEntry(signer.getAccountId(), authV2))).use { server ->
            val tx = assembled(server, signer).simulate(restore = false)
            assertEquals(setOf(signer.getAccountId()), tx.needsNonInvokerSigningBy())
        }
    }

    @Test
    fun testAddressNeedsSigner() = runTest { assertSignerNeeded(false) }

    @Test
    fun testAddressV2NeedsSigner() = runTest { assertSignerNeeded(true) }

    private suspend fun assertUnsignedBlocksSign(authV2: Boolean) {
        val signer = KeyPair.fromSecretSeed(AuthTest.SECRET_SEED)
        mockServer(listOf(placeholderEntry(signer.getAccountId(), authV2))).use { server ->
            val tx = assembled(server, signer).simulate(restore = false)
            assertFailsWith<NeedsMoreSignaturesException> { tx.sign() }
        }
    }

    @Test
    fun testAddressBlocksSign() = runTest { assertUnsignedBlocksSign(false) }

    @Test
    fun testAddressV2BlocksSign() = runTest { assertUnsignedBlocksSign(true) }

    @Test
    fun testEmptyVectorDelegateNeedsSigner() = runTest {
        val signer = KeyPair.fromSecretSeed(AuthTest.SECRET_SEED)
        val entry = Auth.attachDelegates(
            placeholderEntry(signer.getAccountId(), signature = Scv.toVoid()),
            0,
            listOf(DelegateDescriptor(AuthTest.CREDENTIAL_ADDRESS, Scv.toVec(emptyList())))
        )
        mockServer(listOf(entry)).use { server ->
            val tx = assembled(server, signer).simulate(restore = false)
            assertEquals(setOf(signer.getAccountId(), AuthTest.CREDENTIAL_ADDRESS), tx.needsNonInvokerSigningBy())
        }
    }

    private suspend fun assertPayloadDoesNotNeedSigner(signature: SCValXdr) {
        val signer = KeyPair.fromSecretSeed(AuthTest.SECRET_SEED)
        for (authV2 in listOf(false, true)) {
            mockServer(listOf(placeholderEntry(signer.getAccountId(), authV2, signature))).use { server ->
                val tx = assembled(server, signer).simulate(restore = false)
                assertTrue(tx.needsNonInvokerSigningBy().isEmpty())
            }
        }
    }

    @Test
    fun testNonemptyVectorDoesNotNeedSigner() = runTest {
        assertPayloadDoesNotNeedSigner(Scv.toVec(listOf(Scv.toVoid())))
    }

    @Test
    fun testBytesDoNotNeedSigner() = runTest {
        assertPayloadDoesNotNeedSigner(Scv.toBytes(byteArrayOf(1)))
    }

    @Test
    fun testContractInstanceKeyDoesNotNeedSigner() = runTest {
        assertPayloadDoesNotNeedSigner(SCValXdr.Void(SCValTypeXdr.SCV_LEDGER_KEY_CONTRACT_INSTANCE))
    }
}
