//
//  SACClientTest.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.unitTests.contract

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.soneso.stellar.sdk.Address
import com.soneso.stellar.sdk.AssetTypeCreditAlphaNum12
import com.soneso.stellar.sdk.AssetTypeNative
import com.soneso.stellar.sdk.KeyPair
import com.soneso.stellar.sdk.MuxedAccount
import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.TransactionBuilder
import com.soneso.stellar.sdk.contract.ClientOptions
import com.soneso.stellar.sdk.contract.SACClient
import com.soneso.stellar.sdk.contract.exception.ExpiredStateException
import com.soneso.stellar.sdk.contract.exception.NotYetSimulatedException
import com.soneso.stellar.sdk.contract.exception.SendTransactionFailedException
import com.soneso.stellar.sdk.contract.exception.SimulationFailedException
import com.soneso.stellar.sdk.contract.exception.TransactionFailedException
import com.soneso.stellar.sdk.contract.exception.TransactionStillPendingException
import com.soneso.stellar.sdk.rpc.SorobanServer
import com.soneso.stellar.sdk.rpc.responses.GetTransactionStatus
import com.soneso.stellar.sdk.scval.Scv
import com.soneso.stellar.sdk.unitTests.rpc.responses.RpcReply
import com.soneso.stellar.sdk.xdr.ExtensionPointXdr
import com.soneso.stellar.sdk.xdr.HostFunctionXdr
import com.soneso.stellar.sdk.xdr.Int64Xdr
import com.soneso.stellar.sdk.xdr.InvokeContractArgsXdr
import com.soneso.stellar.sdk.xdr.LedgerEntryChangesXdr
import com.soneso.stellar.sdk.xdr.OperationBodyXdr
import com.soneso.stellar.sdk.xdr.PreconditionsXdr
import com.soneso.stellar.sdk.xdr.SCAddressXdr
import com.soneso.stellar.sdk.xdr.SCValTypeXdr
import com.soneso.stellar.sdk.xdr.SCValXdr
import com.soneso.stellar.sdk.xdr.SorobanAddressCredentialsXdr
import com.soneso.stellar.sdk.xdr.SorobanAuthorizationEntryXdr
import com.soneso.stellar.sdk.xdr.SorobanCredentialsXdr
import com.soneso.stellar.sdk.xdr.TransactionEnvelopeXdr
import com.soneso.stellar.sdk.xdr.TransactionMetaV3Xdr
import com.soneso.stellar.sdk.xdr.TransactionMetaXdr
import com.soneso.stellar.sdk.xdr.TransactionV1EnvelopeXdr
import com.soneso.stellar.sdk.xdr.TransactionXdr
import com.soneso.stellar.sdk.xdr.Uint32Xdr
import com.soneso.stellar.sdk.xdr.fromXdrBase64
import com.soneso.stellar.sdk.xdr.toXdrBase64
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Tests for [SACClient]: the factories, the embedded spec, the typed reads and writes, their
 * options, and the amount helper. Every client carries [REFUSING_RPC_URL]. A client from a
 * public factory gets a server with a real engine for that URL, so any request from it fails
 * offline; every other client gets a [SacRpc] server, a MockEngine that records every request.
 * Each simulateTransaction request of a SAC call is decoded: it must invoke the SAC with the
 * function and the complete argument list, and a write's request must carry the fee, the time
 * bound, and the auth flag of its options. The restore transaction that a keyed transfer runs
 * before it simulates again is checked for its restore operation and its source. Tests whose
 * polling continues past the first getTransaction request run on [Dispatchers.Default]: the
 * polling waits with delay() and measures its timeout in wall-clock time, and runTest skips
 * delays in virtual time.
 */
class SACClientTest {

    private companion object {
        /** The Stellar Asset Contract of `TESTASSET:GDUK...YLEX` on testnet. */
        const val SAC_ID = "CBEFNXB7GMEO2PKBCOSKRUQBDWAH4PVA2RIREKPV335KM7MBK2HRLRGB"
        const val XLM_TESTNET_ID = "CDLZFC3SYJYDZT7K67VZ75HPJVIEUVNIXF47ZG2FB2RMQQVU2HHGCYSC"
        const val XLM_PUBLIC_ID = "CAS3J7GYLGXMF6TDJBBYYSE3HQ6BBSMLNUQ34T6TZMYMW2EVH34XOWMA"
        const val ISSUER = "GDUKMGUGDZQK6YHYA5Z6AY2G4XDSZPSZ3SW5UN3ARVMO6QSRDWP5YLEX"
        const val MUXED_ACCOUNT_BASE = "GA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJVSGZ"
        const val MUXED_CONTRACT_BASE = "CA3D5KRYM6CB7OWQ6TWYRR3Z4T7GNZLKERYNZGGA5SOAOPIFY6YQGAXE"
        const val CLAIMABLE_BALANCE = "BAAD6DBUX6J22DMZOHIEZTEQ64CVCHEDRKWZONFEUL5Q26QD7R76RGR4TU"
        const val LIQUIDITY_POOL = "LA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJUPJN"
        const val OTHER_RPC_URL = "https://rpc.example.org"

        val I128_MAX: BigInteger = BigInteger.parseString("170141183460469231731687303715884105727", 10)
        val TWO_POW_127: BigInteger = BigInteger.parseString("170141183460469231731687303715884105728", 10)
        val TWO_POW_100: BigInteger = BigInteger.parseString("1267650600228229401496703205376", 10)

        const val OPTIONS_MESSAGE =
            "The options must keep this SACClient's sourceAccountKeyPair, contractId, network, and rpcUrl; got a different "

        const val READ_ONLY_TRANSFER_AND_SUBMIT =
            "transferAndSubmit needs the sending account's keypair, and this SACClient is read-only; " +
                "open the client with SACClient.forAsset() or SACClient.forContractId()"

        const val EXPIRED_STATE =
            "You need to restore contract state before you can invoke this method. You can set `restore` to true " +
                "in order to automatically restore the contract state when needed."

        fun archived(function: String) =
            "The Stellar Asset Contract $SAC_ID cannot run $function: ledger entries it needs are archived; " +
                "restore them from a client opened with a funded account's keypair"

        fun amountGrammar(preview: String) =
            "Invalid amount $preview: expected decimal digits with at most 7 fractional digits, such as 12.5"

        fun amountOutOfRange(amount: String) =
            "The transfer amount must be from 0 to 170141183460469231731687303715884105727 base units, got $amount"

        fun simulation(value: SCValXdr, auth: List<SorobanAuthorizationEntryXdr> = emptyList()) =
            rpcResult(simulateResultJson(value, auth))

        fun simulationFailure(error: String) = rpcResult("""{"error":"$error","latestLedger":14245}""")

        fun restoreNeeded(): RpcReply = rpcResult(restorePreambleResultJson(listOf(writeAuthEntry(SAC_ID))))

        val restoreTransactionSimulation =
            rpcResult("""{"transactionData":"${minimalSorobanData().toXdrBase64()}","minResourceFee":"100","latestLedger":14245}""")

        val writeSimulation get() = simulation(Scv.toVoid(), listOf(writeAuthEntry(SAC_ID)))

        val sendError = rpcResult("""{"status":"ERROR","hash":"${SacRpc.TX_HASH}","latestLedger":14245}""")
        val transactionFailed = rpcResult("""{"status":"FAILED","txHash":"${SacRpc.TX_HASH}","latestLedger":14246}""")
        val transactionNotFound = rpcResult("""{"status":"NOT_FOUND","latestLedger":14246}""")
        val statusUnavailable = RpcReply("upstream unavailable", HttpStatusCode.ServiceUnavailable)

        fun nowSeconds(): Long = TransactionBuilder.currentTimeMillis() / 1000L
    }

    private suspend fun keyedClient(rpc: SacRpc, keyPair: KeyPair): SACClient =
        SACClient.forServer(SAC_ID, keyPair, Network.TESTNET, REFUSING_RPC_URL, rpc.server)

    private suspend fun readOnlyClient(rpc: SacRpc): SACClient =
        SACClient.forServer(SAC_ID, null, Network.TESTNET, REFUSING_RPC_URL, rpc.server)

    private fun options(keyPair: KeyPair) = ClientOptions(keyPair, SAC_ID, Network.TESTNET, REFUSING_RPC_URL)

    /**
     * The expected options of a write's simulateTransaction request: the fee, the auth flag, and
     * a time bound whose maxTime lies [timeout] seconds after a clock reading between [before] and
     * [after], the readings in seconds taken around the call. The defaults are those of
     * [ClientOptions].
     */
    private class WriteOptions(
        val before: Long,
        val after: Long,
        val fee: UInt = 100u,
        val timeout: Long = 300L,
        val useUpgradedAuth: Boolean = true
    )

    /**
     * Asserts that [simulation] invokes [function] of the SAC with exactly [arguments], in order,
     * and, for a write, that it carries the fee, the time bound, and the auth flag of [write].
     */
    private fun assertSimulates(
        simulation: SacSimulation,
        function: String,
        arguments: List<SCValXdr>,
        write: WriteOptions? = null
    ) {
        val invocation = simulation.invocation
        assertEquals(SAC_ID, Address.fromSCAddress(invocation.contractAddress).toString())
        assertEquals(function, invocation.functionName.value)
        assertEquals(arguments.map { it.toXdrBase64() }, invocation.args.map { it.toXdrBase64() })
        if (write != null) {
            assertEquals(write.fee, simulation.transaction.fee.value)
            val window = (write.before + write.timeout)..(write.after + write.timeout)
            assertTrue(simulation.maxTime in window, "maxTime ${simulation.maxTime} outside $window")
            assertEquals(write.useUpgradedAuth, simulation.useUpgradedAuth)
        }
    }

    /** The arguments of a transfer of [amount] from [from] to [to], in the order of the spec. */
    private fun transferArguments(from: Address, to: Address, amount: BigInteger): List<SCValXdr> =
        listOf(from.toSCVal(), to.toSCVal(), Scv.toInt128(amount))

    /** The arguments of the read [function]: the holder for balance, none for the others. */
    private fun readArguments(function: String, holder: Address): List<SCValXdr> =
        if (function == "balance") listOf(holder.toSCVal()) else emptyList()

    /**
     * Asserts that [client] holds a keypair: its transferAndSubmit reaches the amount check, which
     * a read-only client never reaches, since it refuses first. No request precedes either check.
     */
    private suspend fun assertKeyed(client: SACClient) {
        val failure = assertFailsWith<IllegalArgumentException> {
            client.transferAndSubmit(Address(MOCK_RPC_CONTRACT_ID), BigInteger.fromInt(-1))
        }
        assertEquals(amountOutOfRange("-1"), failure.message)
    }

    /** Asserts that [client] is read-only: its transferAndSubmit throws the refusal. */
    private suspend fun assertReadOnly(client: SACClient) {
        val failure = assertFailsWith<IllegalStateException> {
            client.transferAndSubmit(Address(MOCK_RPC_CONTRACT_ID), BigInteger.ONE)
        }
        assertEquals(READ_ONLY_TRANSFER_AND_SUBMIT, failure.message)
    }

    // ==================== Factories and spec ====================

    @Test
    fun testForServerSendsNoRequestAndCarriesTheSacSpec() = runTest {
        val rpc = SacRpc(KeyPair.random().getAccountId())

        val keyed = keyedClient(rpc, KeyPair.random())
        val readOnly = readOnlyClient(rpc)

        for (client in listOf(keyed, readOnly)) {
            assertEquals(SAC_ID, client.contractId)
            assertEquals(SAC_ID, client.getContractClient().contractId)
            assertEquals(SAC_FUNCTION_NAMES.toSet(), client.getContractClient().getMethodNames())
        }
        assertEquals(emptyList(), rpc.methods)
        // The read-only source is the all-zero ed25519 public key.
        assertEquals("GAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAWHF", SACClient.READ_ONLY_SOURCE_ACCOUNT_ID)
        assertTrue(KeyPair.fromAccountId(SACClient.READ_ONLY_SOURCE_ACCOUNT_ID).getPublicKey().contentEquals(ByteArray(32)))
    }

    @Test
    fun testTheAssetFactoriesOpenTheContractOfTheAssetOnTheNetwork() = runTest {
        val keyed = SACClient.forAsset(
            AssetTypeCreditAlphaNum12("TESTASSET", ISSUER), KeyPair.random(), Network.TESTNET, REFUSING_RPC_URL
        )
        val readOnly = SACClient.readOnlyForAsset(AssetTypeNative, Network.TESTNET, REFUSING_RPC_URL)
        val keyedPublic = SACClient.forAsset(AssetTypeNative, KeyPair.random(), Network.PUBLIC, REFUSING_RPC_URL)
        val readOnlyPublic = SACClient.readOnlyForAsset(AssetTypeNative, Network.PUBLIC, REFUSING_RPC_URL)

        assertEquals(SAC_ID, keyed.contractId)
        assertEquals(XLM_TESTNET_ID, readOnly.contractId)
        assertEquals(XLM_PUBLIC_ID, keyedPublic.contractId)
        assertEquals(XLM_PUBLIC_ID, readOnlyPublic.contractId)
        assertEquals(Network.TESTNET, keyed.getContractClient().network)
        assertEquals(Network.TESTNET, readOnly.getContractClient().network)
        assertEquals(Network.PUBLIC, keyedPublic.getContractClient().network)
        assertEquals(Network.PUBLIC, readOnlyPublic.getContractClient().network)
        for (client in listOf(keyed, readOnly, keyedPublic, readOnlyPublic)) {
            assertEquals(REFUSING_RPC_URL, client.getContractClient().rpcUrl)
            assertEquals(SAC_FUNCTION_NAMES.toSet(), client.getContractClient().getMethodNames())
        }
        assertKeyed(keyed)
        assertKeyed(keyedPublic)
        assertReadOnly(readOnly)
        assertReadOnly(readOnlyPublic)
        listOf(keyed, readOnly, keyedPublic, readOnlyPublic).forEach { it.close() }
    }

    @Test
    fun testTheContractIdFactoriesOpenTheId() = runTest {
        val keyed = SACClient.forContractId(SAC_ID, KeyPair.random(), Network.TESTNET, REFUSING_RPC_URL)
        val readOnly = SACClient.readOnlyForContractId(XLM_TESTNET_ID, Network.TESTNET, REFUSING_RPC_URL)

        assertEquals(SAC_ID, keyed.contractId)
        assertEquals(XLM_TESTNET_ID, readOnly.contractId)
        for (client in listOf(keyed, readOnly)) {
            assertEquals(REFUSING_RPC_URL, client.getContractClient().rpcUrl)
            assertEquals(Network.TESTNET, client.getContractClient().network)
            assertEquals(SAC_FUNCTION_NAMES.toSet(), client.getContractClient().getMethodNames())
        }
        assertKeyed(keyed)
        assertReadOnly(readOnly)
        keyed.close()
        readOnly.close()
    }

    @Test
    fun testSpecEntriesReturnsAnEqualNewListOnEveryCall() = runTest {
        val first = SACClient.specEntries()
        val second = SACClient.specEntries()

        assertEquals(first, second)
        assertNotSame(first, second)
    }

    @Test
    fun testAMuxedContractIdIsRejectedBeforeAnyRequest() = runTest {
        val expected = "The contract to invoke must be a contract (C...) address, got a muxed contract (W...) address: " +
            "$MOCK_RPC_MUXED_CONTRACT_ID; use its base contract (C...) address"
        val rpc = SacRpc(KeyPair.random().getAccountId())

        val failure = assertFailsWith<IllegalArgumentException> {
            SACClient.forServer(MOCK_RPC_MUXED_CONTRACT_ID, KeyPair.random(), Network.TESTNET, REFUSING_RPC_URL, rpc.server)
        }

        assertEquals(expected, failure.message)
        assertEquals(emptyList(), rpc.methods)
        assertFalse(rpc.httpClient.isActive, "the server of a client that cannot open is closed")
        assertEquals(
            expected,
            assertFailsWith<IllegalArgumentException> {
                SACClient.forContractId(MOCK_RPC_MUXED_CONTRACT_ID, KeyPair.random(), Network.TESTNET, REFUSING_RPC_URL)
            }.message
        )
        assertEquals(
            expected,
            assertFailsWith<IllegalArgumentException> {
                SACClient.readOnlyForContractId(MOCK_RPC_MUXED_CONTRACT_ID, Network.TESTNET, REFUSING_RPC_URL)
            }.message
        )
    }

    @Test
    fun testASecretSeedAsContractIdIsNamedByItsKindOnly() = runTest {
        val seed = KeyPair.random().getSecretSeed()!!.concatToString()
        val rpc = SacRpc(KeyPair.random().getAccountId())

        val failure = assertFailsWith<IllegalArgumentException> {
            SACClient.forServer(seed, null, Network.TESTNET, REFUSING_RPC_URL, rpc.server)
        }
        val publicFailure = assertFailsWith<IllegalArgumentException> {
            SACClient.forContractId(seed, KeyPair.random(), Network.TESTNET, REFUSING_RPC_URL)
        }

        for (message in listOf(failure.message!!, publicFailure.message!!)) {
            assertEquals(
                "The contract to invoke must be a contract (C...) address, got a secret seed (S...) instead of an address",
                message
            )
            assertFalse(seed in message)
        }
        assertEquals(emptyList(), rpc.methods)
    }

    @Test
    fun testCloseClosesTheWrappedClient() = runTest {
        val rpc = SacRpc(KeyPair.random().getAccountId())
        val client = readOnlyClient(rpc)
        assertTrue(rpc.httpClient.isActive)

        client.close()

        assertFalse(rpc.httpClient.isActive)
    }

    // ==================== Reads ====================

    @Test
    fun testDecimalsReturnsTheU32() = runTest {
        val rpc = SacRpc(SACClient.READ_ONLY_SOURCE_ACCOUNT_ID, listOf(simulation(Scv.toUint32(7u))))

        assertEquals(7u, readOnlyClient(rpc).decimals())

        assertEquals(listOf("simulateTransaction"), rpc.methods)
        val simulation = rpc.simulations().single()
        assertSimulates(simulation, "decimals", emptyList())
        assertEquals(SACClient.READ_ONLY_SOURCE_ACCOUNT_ID, simulation.sourceAccount)
        assertEquals(1L, simulation.transaction.seqNum.value.value)
    }

    @Test
    fun testNameAndSymbolReturnTheStrings() = runTest {
        val rpc = SacRpc(
            SACClient.READ_ONLY_SOURCE_ACCOUNT_ID,
            listOf(simulation(Scv.toString("TESTASSET:$ISSUER")), simulation(Scv.toString("TESTASSET")))
        )
        val client = readOnlyClient(rpc)

        assertEquals("TESTASSET:$ISSUER", client.name())
        assertEquals("TESTASSET", client.symbol())

        assertEquals(listOf("simulateTransaction", "simulateTransaction"), rpc.methods)
        val (name, symbol) = rpc.simulations()
        assertSimulates(name, "name", emptyList())
        assertSimulates(symbol, "symbol", emptyList())
    }

    @Test
    fun testBalanceOfAnAccountAndAContractHolder() = runTest {
        val rpc = SacRpc(SACClient.READ_ONLY_SOURCE_ACCOUNT_ID, listOf(simulation(Scv.toInt128(TWO_POW_100))))
        val client = readOnlyClient(rpc)
        val holders = listOf(Address(KeyPair.random().getAccountId()), Address(MOCK_RPC_CONTRACT_ID))

        for (holder in holders) {
            assertEquals(TWO_POW_100, client.balance(holder))
        }

        assertEquals(listOf("simulateTransaction", "simulateTransaction"), rpc.methods)
        rpc.simulations().zip(holders).forEach { (simulation, holder) ->
            assertSimulates(simulation, "balance", listOf(holder.toSCVal()))
        }
    }

    @Test
    fun testABalanceOnAKeyedClientLoadsTheSourceAccountFirst() = runTest {
        val keyPair = KeyPair.random()
        val rpc = SacRpc(keyPair.getAccountId(), listOf(simulation(Scv.toInt128(TWO_POW_100))))
        val holder = Address(KeyPair.random().getAccountId())

        assertEquals(TWO_POW_100, keyedClient(rpc, keyPair).balance(holder))

        assertEquals(listOf("getLedgerEntries", "getLedgerEntries", "simulateTransaction"), rpc.methods)
        val simulation = rpc.simulations().single()
        assertSimulates(simulation, "balance", listOf(holder.toSCVal()))
        assertEquals(keyPair.getAccountId(), simulation.sourceAccount)
        assertEquals(MOCK_RPC_SOURCE_SEQ + 1, simulation.transaction.seqNum.value.value)
    }

    @Test
    fun testABalanceOfAMissingAccountOrTrustlineThrowsTheContractError() = runTest {
        for (hostError in listOf("HostError: Error(Contract, #6)", "HostError: Error(Contract, #13)")) {
            val rpc = SacRpc(SACClient.READ_ONLY_SOURCE_ACCOUNT_ID, listOf(simulationFailure(hostError)))
            val holder = Address(KeyPair.random().getAccountId())

            val failure = assertFailsWith<SimulationFailedException>(hostError) { readOnlyClient(rpc).balance(holder) }

            assertTrue(hostError in failure.message!!, failure.message)
            assertEquals(listOf("simulateTransaction"), rpc.methods)
            assertSimulates(rpc.simulations().single(), "balance", listOf(holder.toSCVal()))
        }
    }

    @Test
    fun testABalanceOfAMuxedHolderNamesItsBaseBeforeAnyRequest() = runTest {
        val rpc = SacRpc(SACClient.READ_ONLY_SOURCE_ACCOUNT_ID)
        val client = readOnlyClient(rpc)

        val muxedAccount = assertFailsWith<IllegalArgumentException> { client.balance(Address(MOCK_RPC_MUXED_ACCOUNT_ID)) }
        val muxedContract = assertFailsWith<IllegalArgumentException> { client.balance(Address(MOCK_RPC_MUXED_CONTRACT_ID)) }

        assertEquals(
            "The balance holder must be an account (G...) or contract (C...) address, got a muxed account (M...) " +
                "address: $MOCK_RPC_MUXED_ACCOUNT_ID; query its base account $MUXED_ACCOUNT_BASE",
            muxedAccount.message
        )
        assertEquals(
            "The balance holder must be an account (G...) or contract (C...) address, got a muxed contract (W...) " +
                "address: $MOCK_RPC_MUXED_CONTRACT_ID; query its base contract $MUXED_CONTRACT_BASE",
            muxedContract.message
        )
        assertEquals(emptyList(), rpc.methods)
    }

    @Test
    fun testABalanceOfAHostProducedAddressIsRejectedBeforeAnyRequest() = runTest {
        val rpc = SacRpc(SACClient.READ_ONLY_SOURCE_ACCOUNT_ID)
        val client = readOnlyClient(rpc)

        for ((holder, kind) in listOf(CLAIMABLE_BALANCE to "claimable balance", LIQUIDITY_POOL to "liquidity pool")) {
            val failure = assertFailsWith<IllegalArgumentException>(kind) { client.balance(Address(holder)) }
            assertEquals(
                "Failed to convert arguments for 'balance': Invalid type: Address takes an account (G...) or contract " +
                    "(C...) address, got the $kind address $holder, which is produced by the host and is not a contract input",
                failure.message
            )
        }
        assertEquals(emptyList(), rpc.methods)
    }

    @Test
    fun testEachReadRejectsAnotherResultArm() = runTest {
        val holder = Address(KeyPair.random().getAccountId())
        val cases: List<Triple<String, SCValXdr, suspend (SACClient) -> Unit>> = listOf(
            Triple("balance", Scv.toUint32(7u)) { it.balance(holder) },
            Triple("decimals", Scv.toInt128(BigInteger.fromInt(7))) { it.decimals() },
            Triple("name", Scv.toSymbol("TESTASSET")) { it.name() },
            Triple("symbol", Scv.toUint32(7u)) { it.symbol() }
        )
        val expected = mapOf(
            "balance" to "returned SCV_U32 from balance, expected SCV_I128",
            "decimals" to "returned SCV_I128 from decimals, expected SCV_U32",
            "name" to "returned SCV_SYMBOL from name, expected SCV_STRING",
            "symbol" to "returned SCV_U32 from symbol, expected SCV_STRING"
        )

        for ((function, value, read) in cases) {
            val rpc = SacRpc(SACClient.READ_ONLY_SOURCE_ACCOUNT_ID, listOf(simulation(value)))
            val failure = assertFailsWith<IllegalStateException>(function) { read(readOnlyClient(rpc)) }
            assertEquals("The Stellar Asset Contract $SAC_ID ${expected.getValue(function)}", failure.message)
            assertEquals(listOf("simulateTransaction"), rpc.methods)
            assertSimulates(rpc.simulations().single(), function, readArguments(function, holder))
        }
    }

    @Test
    fun testEachReadThrowsWhenItsEntriesAreArchivedAndNeverRestores() = runTest {
        val holder = Address(KeyPair.random().getAccountId())
        val reads: List<Pair<String, suspend (SACClient) -> Unit>> = listOf(
            "balance" to { it.balance(holder) },
            "decimals" to { it.decimals() },
            "name" to { it.name() },
            "symbol" to { it.symbol() }
        )

        for ((function, read) in reads) {
            // A keyed client would restore a write with this preamble; a read never does.
            val keyPair = KeyPair.random()
            val keyed = SacRpc(keyPair.getAccountId(), listOf(restoreNeeded()))
            val keyedFailure = assertFailsWith<IllegalStateException>(function) { read(keyedClient(keyed, keyPair)) }
            assertEquals(archived(function), keyedFailure.message)
            assertEquals(listOf("getLedgerEntries", "getLedgerEntries", "simulateTransaction"), keyed.methods)
            assertSimulates(keyed.simulations().single(), function, readArguments(function, holder))

            val readOnly = SacRpc(SACClient.READ_ONLY_SOURCE_ACCOUNT_ID, listOf(restoreNeeded()))
            val readOnlyFailure = assertFailsWith<IllegalStateException>(function) { read(readOnlyClient(readOnly)) }
            assertEquals(archived(function), readOnlyFailure.message)
            assertEquals(listOf("simulateTransaction"), readOnly.methods)
            assertSimulates(readOnly.simulations().single(), function, readArguments(function, holder))
        }
    }

    // ==================== transfer ====================

    @Test
    fun testATransferPassesTheCompleteArgumentsForEachRecipientKind() = runTest {
        val keyPair = KeyPair.random()
        val from = Address(keyPair.getAccountId())
        val recipients = listOf(
            Address(MOCK_RPC_MUXED_ACCOUNT_ID) to SCAddressXdr.MuxedAccount::class,
            Address(MOCK_RPC_MUXED_CONTRACT_ID) to SCAddressXdr.MuxedContract::class,
            Address(MOCK_RPC_CONTRACT_ID) to SCAddressXdr.ContractId::class,
            Address(KeyPair.random().getAccountId()) to SCAddressXdr.AccountId::class
        )
        val amounts = listOf(BigInteger.ZERO, I128_MAX, BigInteger.fromLong(125_000_000L), BigInteger.ONE)

        recipients.zip(amounts).forEach { (recipient, amount) ->
            val (to, arm) = recipient
            val rpc = SacRpc(keyPair.getAccountId(), listOf(writeSimulation))

            val before = nowSeconds()
            val transaction = keyedClient(rpc, keyPair).transfer(from, to, amount)
            val after = nowSeconds()

            val simulation = rpc.simulations().single()
            assertSimulates(simulation, "transfer", transferArguments(from, to, amount), WriteOptions(before, after))
            val arguments = simulation.invocation.args
            assertIs<SCAddressXdr.AccountId>(assertIs<SCValXdr.Address>(arguments[0]).value)
            assertTrue(arm.isInstance(assertIs<SCValXdr.Address>(arguments[1]).value), "$to")
            assertNotNull(transaction.builtTransaction)
            assertNull(transaction.signed)
            assertNull(transaction.sendTransactionResponse)
            assertEquals(listOf("getLedgerEntries", "getLedgerEntries", "simulateTransaction"), rpc.methods)
        }
    }

    @Test
    fun testATransferFromAContractPassesTheContractArmAndNeedsItsAuthorization() = runTest {
        val keyPair = KeyPair.random()
        val from = Address(MOCK_RPC_CONTRACT_ID)
        val to = Address(KeyPair.random().getAccountId())
        val rpc = SacRpc(
            keyPair.getAccountId(),
            listOf(simulation(Scv.toVoid(), listOf(unsignedAddressAuthEntry(SAC_ID, MOCK_RPC_CONTRACT_ID))))
        )

        val before = nowSeconds()
        val transaction = keyedClient(rpc, keyPair).transfer(from, to, BigInteger.TEN)
        val after = nowSeconds()

        val simulation = rpc.simulations().single()
        assertSimulates(simulation, "transfer", transferArguments(from, to, BigInteger.TEN), WriteOptions(before, after))
        assertIs<SCAddressXdr.ContractId>(assertIs<SCValXdr.Address>(simulation.invocation.args[0]).value)
        assertEquals(setOf(MOCK_RPC_CONTRACT_ID), transaction.needsNonInvokerSigningBy())
    }

    @Test
    fun testATransferFromAnotherAccountNeedsItsAuthorization() = runTest {
        val keyPair = KeyPair.random()
        val sender = KeyPair.random()
        val from = Address(sender.getAccountId())
        val to = Address(KeyPair.random().getAccountId())
        val rpc = SacRpc(
            keyPair.getAccountId(),
            listOf(simulation(Scv.toVoid(), listOf(unsignedAddressAuthEntry(SAC_ID, sender.getAccountId()))))
        )

        val before = nowSeconds()
        val transaction = keyedClient(rpc, keyPair).transfer(from, to, BigInteger.TEN)
        val after = nowSeconds()

        assertEquals(setOf(sender.getAccountId()), transaction.needsNonInvokerSigningBy())
        transaction.signAuthEntries(sender, validUntilLedgerSequence = 5000L)
        assertEquals(emptySet(), transaction.needsNonInvokerSigningBy())
        assertEquals(listOf("getLedgerEntries", "getLedgerEntries", "simulateTransaction"), rpc.methods)
        val simulation = rpc.simulations().single()
        assertSimulates(simulation, "transfer", transferArguments(from, to, BigInteger.TEN), WriteOptions(before, after))
        assertEquals(keyPair.getAccountId(), simulation.sourceAccount)
    }

    @Test
    fun testATransferAmountBelowZeroOrAboveTheI128MaximumIsRejectedBeforeAnyRequest() = runTest {
        val keyPair = KeyPair.random()
        val rpc = SacRpc(keyPair.getAccountId())
        val client = keyedClient(rpc, keyPair)
        val to = Address(KeyPair.random().getAccountId())

        for ((amount, text) in listOf(BigInteger.fromInt(-1) to "-1", TWO_POW_127 to "170141183460469231731687303715884105728")) {
            val failure = assertFailsWith<IllegalArgumentException>(text) {
                client.transfer(Address(keyPair.getAccountId()), to, amount)
            }
            // The amount check runs before the options check.
            val beforeOptions = assertFailsWith<IllegalArgumentException>(text) {
                client.transfer(Address(keyPair.getAccountId()), to, amount, options(keyPair).copy(network = Network.PUBLIC))
            }
            assertEquals(amountOutOfRange(text), failure.message)
            assertEquals(amountOutOfRange(text), beforeOptions.message)
        }
        assertEquals(emptyList(), rpc.methods)
    }

    @Test
    fun testATransferRejectsAMuxedSenderAndHostProducedRecipientsBeforeAnyRequest() = runTest {
        val keyPair = KeyPair.random()
        val rpc = SacRpc(keyPair.getAccountId())
        val client = keyedClient(rpc, keyPair)
        val from = Address(keyPair.getAccountId())
        val takes = "MuxedAddress takes an account (G...), muxed account (M...), contract (C...) or muxed contract (W...) address"

        val muxedSender = assertFailsWith<IllegalArgumentException> {
            client.transfer(Address(MOCK_RPC_MUXED_ACCOUNT_ID), Address(MOCK_RPC_CONTRACT_ID), BigInteger.ONE)
        }
        val claimableBalance = assertFailsWith<IllegalArgumentException> {
            client.transfer(from, Address(CLAIMABLE_BALANCE), BigInteger.ONE)
        }
        val liquidityPool = assertFailsWith<IllegalArgumentException> {
            client.transfer(from, Address(LIQUIDITY_POOL), BigInteger.ONE)
        }

        assertEquals(
            "Failed to convert arguments for 'transfer': Invalid type: Address takes an account (G...) or contract (C...) " +
                "address, got the muxed address $MOCK_RPC_MUXED_ACCOUNT_ID, which needs a MuxedAddress parameter",
            muxedSender.message
        )
        assertEquals(
            "Failed to convert arguments for 'transfer': Invalid type: $takes, got the claimable balance address " +
                "$CLAIMABLE_BALANCE, which is produced by the host and is not a contract input",
            claimableBalance.message
        )
        assertEquals(
            "Failed to convert arguments for 'transfer': Invalid type: $takes, got the liquidity pool address " +
                "$LIQUIDITY_POOL, which is produced by the host and is not a contract input",
            liquidityPool.message
        )
        assertEquals(emptyList(), rpc.methods)
    }

    @Test
    fun testAKeyedTransferRestoresArchivedEntriesFirst() = runTest {
        val keyPair = KeyPair.random()
        val from = Address(keyPair.getAccountId())
        val to = Address(MOCK_RPC_CONTRACT_ID)
        val rpc = SacRpc(keyPair.getAccountId(), listOf(restoreNeeded(), restoreTransactionSimulation, writeSimulation))

        val before = nowSeconds()
        val transaction = keyedClient(rpc, keyPair).transfer(from, to, BigInteger.ONE)
        val after = nowSeconds()

        assertEquals(
            listOf(
                "getLedgerEntries", "getLedgerEntries", "simulateTransaction",
                "getLedgerEntries", "simulateTransaction", "sendTransaction", "getTransaction",
                "getLedgerEntries", "simulateTransaction"
            ),
            rpc.methods
        )
        assertNull(transaction.simulation!!.restorePreamble)
        assertNull(transaction.signed)
        val (archived, restore, resimulated) = rpc.simulations()
        for (simulation in listOf(archived, resimulated)) {
            assertSimulates(simulation, "transfer", transferArguments(from, to, BigInteger.ONE), WriteOptions(before, after))
        }
        // Between the two, the restore transaction simulates its own operation.
        assertIs<OperationBodyXdr.RestoreFootprintOp>(restore.operationBody)
        assertEquals(keyPair.getAccountId(), restore.sourceAccount)
    }

    @Test
    fun testAReadOnlyTransferRefusesToRestoreSignAndSubmit() = runTest {
        val source = SACClient.READ_ONLY_SOURCE_ACCOUNT_ID
        val to = Address(KeyPair.random().getAccountId())
        val from = Address(KeyPair.random().getAccountId())

        val archived = SacRpc(source, listOf(restoreNeeded()))
        val archivedBefore = nowSeconds()
        val restoreFailure = assertFailsWith<IllegalStateException> {
            readOnlyClient(archived).transfer(from, to, BigInteger.ONE)
        }
        val archivedAfter = nowSeconds()
        assertEquals(readOnlyRefusal(source), restoreFailure.message)
        assertEquals(listOf("simulateTransaction"), archived.methods)
        assertSimulates(
            archived.simulations().single(), "transfer", transferArguments(from, to, BigInteger.ONE),
            WriteOptions(archivedBefore, archivedAfter)
        )

        val live = SacRpc(source, listOf(writeSimulation))
        val before = nowSeconds()
        val transaction = readOnlyClient(live).transfer(from, to, BigInteger.ONE)
        val after = nowSeconds()
        assertSimulates(
            live.simulations().single(), "transfer", transferArguments(from, to, BigInteger.ONE), WriteOptions(before, after)
        )
        val submitFailure = assertFailsWith<IllegalStateException> { transaction.signAndSubmit() }
        assertEquals(readOnlyRefusal(source), submitFailure.message)
        assertNull(transaction.signed)
        assertEquals(listOf("simulateTransaction"), live.methods)
    }

    // ==================== transferAndSubmit ====================

    @Test
    fun testTransferAndSubmitSendsFromTheSourceAndReturnsTheResponse() = runTest {
        val keyPair = KeyPair.random()
        val to = Address(KeyPair.random().getAccountId())
        val amount = BigInteger.fromLong(1_000_000_000L)
        val rpc = SacRpc(keyPair.getAccountId(), listOf(writeSimulation))

        val before = nowSeconds()
        val response = keyedClient(rpc, keyPair).transferAndSubmit(to, amount)
        val after = nowSeconds()

        assertEquals(GetTransactionStatus.SUCCESS, response.status)
        assertEquals(SacRpc.TX_HASH, response.txHash)
        assertEquals(
            listOf("getLedgerEntries", "getLedgerEntries", "simulateTransaction", "sendTransaction", "getTransaction"),
            rpc.methods
        )
        val simulation = rpc.simulations().single()
        assertSimulates(
            simulation, "transfer", transferArguments(Address(keyPair.getAccountId()), to, amount), WriteOptions(before, after)
        )
        assertEquals(keyPair.getAccountId(), simulation.sourceAccount)
        assertEquals(listOf(1), rpc.sentSignatureCounts())
    }

    @Test
    fun testTransferAndSubmitThrowsForAFailedTransaction() = runTest {
        val keyPair = KeyPair.random()
        val rpc = SacRpc(keyPair.getAccountId(), listOf(writeSimulation), getTransaction = listOf(transactionFailed))
        val to = Address(MOCK_RPC_CONTRACT_ID)

        val before = nowSeconds()
        val failure = assertFailsWith<TransactionFailedException> {
            keyedClient(rpc, keyPair).transferAndSubmit(to, BigInteger.ONE)
        }
        val after = nowSeconds()

        assertEquals("Transaction failed.", failure.message)
        assertEquals(
            listOf("getLedgerEntries", "getLedgerEntries", "simulateTransaction", "sendTransaction", "getTransaction"),
            rpc.methods
        )
        assertSimulates(
            rpc.simulations().single(), "transfer", transferArguments(Address(keyPair.getAccountId()), to, BigInteger.ONE),
            WriteOptions(before, after)
        )
    }

    @Test
    fun testTransferAndSubmitThrowsForARejectedSend() = runTest {
        val keyPair = KeyPair.random()
        val rpc = SacRpc(keyPair.getAccountId(), listOf(writeSimulation), send = listOf(sendError))
        val to = Address(MOCK_RPC_CONTRACT_ID)

        val before = nowSeconds()
        val failure = assertFailsWith<SendTransactionFailedException> {
            keyedClient(rpc, keyPair).transferAndSubmit(to, BigInteger.ONE)
        }
        val after = nowSeconds()

        assertEquals("Sending the transaction to the network failed! Status: ERROR", failure.message)
        assertEquals(listOf("getLedgerEntries", "getLedgerEntries", "simulateTransaction", "sendTransaction"), rpc.methods)
        assertSimulates(
            rpc.simulations().single(), "transfer", transferArguments(Address(keyPair.getAccountId()), to, BigInteger.ONE),
            WriteOptions(before, after)
        )
    }

    @Test
    fun testTransferAndSubmitThrowsWithoutAReturnValueInTheMeta() = runTest {
        val keyPair = KeyPair.random()
        val meta = TransactionMetaXdr.V3(
            TransactionMetaV3Xdr(
                ext = ExtensionPointXdr.Void,
                txChangesBefore = LedgerEntryChangesXdr(emptyList()),
                operations = emptyList(),
                txChangesAfter = LedgerEntryChangesXdr(emptyList()),
                sorobanMeta = null
            )
        ).toXdrBase64()
        val success = rpcResult("""{"status":"SUCCESS","txHash":"${SacRpc.TX_HASH}","latestLedger":14246,"resultMetaXdr":"$meta"}""")
        val rpc = SacRpc(keyPair.getAccountId(), listOf(writeSimulation), getTransaction = listOf(success))
        val to = Address(MOCK_RPC_CONTRACT_ID)

        val before = nowSeconds()
        val failure = assertFailsWith<IllegalStateException> {
            keyedClient(rpc, keyPair).transferAndSubmit(to, BigInteger.ONE)
        }
        val after = nowSeconds()

        assertEquals("No return value in transaction meta", failure.message)
        assertEquals(
            listOf("getLedgerEntries", "getLedgerEntries", "simulateTransaction", "sendTransaction", "getTransaction"),
            rpc.methods
        )
        assertSimulates(
            rpc.simulations().single(), "transfer", transferArguments(Address(keyPair.getAccountId()), to, BigInteger.ONE),
            WriteOptions(before, after)
        )
    }

    @Test
    fun testTransferAndSubmitPollsPastAFailedStatusRequest() = runTest(timeout = 90.seconds) {
        withContext(Dispatchers.Default) {
            val keyPair = KeyPair.random()
            val rpc = SacRpc(
                keyPair.getAccountId(),
                listOf(writeSimulation),
                getTransaction = listOf(statusUnavailable, SacRpc.TRANSACTION_SUCCESS)
            )
            val to = Address(MOCK_RPC_CONTRACT_ID)

            val before = nowSeconds()
            val response = keyedClient(rpc, keyPair).transferAndSubmit(to, BigInteger.ONE)
            val after = nowSeconds()

            assertEquals(GetTransactionStatus.SUCCESS, response.status)
            assertEquals(SacRpc.TX_HASH, response.txHash)
            assertEquals(
                listOf(
                    "getLedgerEntries", "getLedgerEntries", "simulateTransaction", "sendTransaction",
                    "getTransaction", "getTransaction"
                ),
                rpc.methods
            )
            assertSimulates(
                rpc.simulations().single(), "transfer", transferArguments(Address(keyPair.getAccountId()), to, BigInteger.ONE),
                WriteOptions(before, after)
            )
        }
    }

    @Test
    fun testTransferAndSubmitThrowsTheLastStatusFailureWhenTheSubmitTimeoutEnds() = runTest(timeout = 90.seconds) {
        withContext(Dispatchers.Default) {
            val keyPair = KeyPair.random()
            val rpc = SacRpc(keyPair.getAccountId(), listOf(writeSimulation), getTransaction = listOf(statusUnavailable))
            val client = keyedClient(rpc, keyPair)
            val to = Address(MOCK_RPC_CONTRACT_ID)
            val start = TransactionBuilder.currentTimeMillis()
            val before = nowSeconds()

            val failure = assertFailsWith<IllegalArgumentException> {
                client.transferAndSubmit(to, BigInteger.ONE)
            }
            val after = nowSeconds()

            assertTrue(
                failure.message!!.startsWith("Failed to parse response for method getTransaction:"),
                failure.message
            )
            assertTrue(TransactionBuilder.currentTimeMillis() - start >= 30_000L)
            assertTrue(rpc.methods.count { it == "getTransaction" } > 1)
            assertFalse("sendTransaction" in rpc.methods.dropWhile { it != "getTransaction" })
            assertSimulates(
                rpc.simulations().single(), "transfer", transferArguments(Address(keyPair.getAccountId()), to, BigInteger.ONE),
                WriteOptions(before, after)
            )
        }
    }

    @Test
    fun testAKeyedTransferAndSubmitRunsTheChecksOfTransferBeforeAnyRequest() = runTest {
        val keyPair = KeyPair.random()
        val rpc = SacRpc(keyPair.getAccountId(), listOf(writeSimulation))
        val client = keyedClient(rpc, keyPair)
        val to = Address(MOCK_RPC_CONTRACT_ID)
        val unsimulated = options(keyPair).copy(simulate = false)

        // With simulate = false, the amount and options checks still run before the account request
        // and before the NotYetSimulatedException of sign().
        val negative = assertFailsWith<IllegalArgumentException> {
            client.transferAndSubmit(to, BigInteger.fromInt(-1), unsimulated)
        }
        val tooLarge = assertFailsWith<IllegalArgumentException> { client.transferAndSubmit(to, TWO_POW_127) }
        val otherNetwork = assertFailsWith<IllegalArgumentException> {
            client.transferAndSubmit(to, BigInteger.ONE, unsimulated.copy(network = Network.PUBLIC))
        }

        assertEquals(
            "The transfer amount must be from 0 to 170141183460469231731687303715884105727 base units, got -1",
            negative.message
        )
        assertEquals(
            "The transfer amount must be from 0 to 170141183460469231731687303715884105727 base units, " +
                "got 170141183460469231731687303715884105728",
            tooLarge.message
        )
        assertEquals(
            "The options must keep this SACClient's sourceAccountKeyPair, contractId, network, and rpcUrl; " +
                "got a different network",
            otherNetwork.message
        )
        assertEquals(emptyList(), rpc.methods)
    }

    @Test
    fun testAReadOnlyTransferAndSubmitIsRefusedBeforeAnyRequestOrCheck() = runTest {
        val rpc = SacRpc(SACClient.READ_ONLY_SOURCE_ACCOUNT_ID, listOf(writeSimulation))
        val client = readOnlyClient(rpc)
        val to = Address(MOCK_RPC_CONTRACT_ID)
        val placeholder = KeyPair.fromAccountId(SACClient.READ_ONLY_SOURCE_ACCOUNT_ID)
        val attempts: List<Pair<String, suspend () -> Unit>> = listOf(
            "default options" to { client.transferAndSubmit(to, BigInteger.ONE) },
            "amount -1" to { client.transferAndSubmit(to, BigInteger.fromInt(-1)) },
            "other options" to {
                client.transferAndSubmit(to, BigInteger.ONE, options(placeholder).copy(network = Network.PUBLIC))
            },
            "simulate = false" to {
                client.transferAndSubmit(to, BigInteger.ONE, options(placeholder).copy(simulate = false))
            }
        )

        for ((name, attempt) in attempts) {
            val failure = assertFailsWith<IllegalStateException>(name) { attempt() }
            assertEquals(READ_ONLY_TRANSFER_AND_SUBMIT, failure.message, name)
        }
        assertEquals(emptyList(), rpc.methods)
    }

    // ==================== Options ====================

    @Test
    fun testTheDefaultOptionsOfBothWrites() = runTest {
        val keyPair = KeyPair.random()
        val to = Address(MOCK_RPC_CONTRACT_ID)
        val rpc = SacRpc(keyPair.getAccountId(), listOf(writeSimulation))
        val client = keyedClient(rpc, keyPair)

        val from = Address(keyPair.getAccountId())

        val before = nowSeconds()
        client.transfer(from, to, BigInteger.ONE)
        client.transferAndSubmit(to, BigInteger.ONE)
        val after = nowSeconds()

        val simulations = rpc.simulations()
        assertEquals(2, simulations.size)
        simulations.forEach {
            assertSimulates(
                it, "transfer", transferArguments(from, to, BigInteger.ONE),
                WriteOptions(before, after, fee = 100u, timeout = 300L, useUpgradedAuth = true)
            )
        }
    }

    @Test
    fun testTheFeeTimeoutAndAuthOptionsOfBothWrites() = runTest {
        val keyPair = KeyPair.random()
        val to = Address(MOCK_RPC_CONTRACT_ID)
        val rpc = SacRpc(keyPair.getAccountId(), listOf(writeSimulation))
        val client = keyedClient(rpc, keyPair)
        val options = options(keyPair).copy(baseFee = 1234, transactionTimeout = 60L, useUpgradedAuth = false)

        val from = Address(keyPair.getAccountId())

        val before = nowSeconds()
        client.transfer(from, to, BigInteger.ONE, options)
        client.transferAndSubmit(to, BigInteger.ONE, options)
        val after = nowSeconds()

        val simulations = rpc.simulations()
        assertEquals(2, simulations.size)
        simulations.forEach {
            assertSimulates(
                it, "transfer", transferArguments(from, to, BigInteger.ONE),
                WriteOptions(before, after, fee = 1234u, timeout = 60L, useUpgradedAuth = false)
            )
        }
    }

    @Test
    fun testOptionsThatChangeAnIdentityFieldAreRejectedBeforeAnyRequest() = runTest {
        val keyPair = KeyPair.random()
        val rpc = SacRpc(keyPair.getAccountId(), listOf(writeSimulation))
        val client = keyedClient(rpc, keyPair)
        val to = Address(MOCK_RPC_CONTRACT_ID)
        val changed = listOf(
            "sourceAccountKeyPair" to options(keyPair).copy(sourceAccountKeyPair = KeyPair.random()),
            "contractId" to options(keyPair).copy(contractId = XLM_TESTNET_ID),
            "network" to options(keyPair).copy(network = Network.PUBLIC),
            "rpcUrl" to options(keyPair).copy(rpcUrl = OTHER_RPC_URL)
        )

        for ((field, options) in changed) {
            val transferFailure = assertFailsWith<IllegalArgumentException>(field) {
                client.transfer(Address(keyPair.getAccountId()), to, BigInteger.ONE, options)
            }
            val submitFailure = assertFailsWith<IllegalArgumentException>(field) {
                client.transferAndSubmit(to, BigInteger.ONE, options)
            }
            assertEquals(OPTIONS_MESSAGE + field, transferFailure.message)
            assertEquals(OPTIONS_MESSAGE + field, submitFailure.message)
            assertFalse(OTHER_RPC_URL in transferFailure.message!!)
        }
        assertEquals(emptyList(), rpc.methods)
    }

    @Test
    fun testTheFirstDifferentIdentityFieldInComparisonOrderIsNamed() = runTest {
        val keyPair = KeyPair.random()
        val rpc = SacRpc(keyPair.getAccountId(), listOf(writeSimulation))
        val client = keyedClient(rpc, keyPair)
        val to = Address(MOCK_RPC_CONTRACT_ID)
        // The comparisons run in the order sourceAccountKeyPair, contractId, network, rpcUrl.
        val changed = listOf(
            "The options must keep this SACClient's sourceAccountKeyPair, contractId, network, and rpcUrl; " +
                "got a different sourceAccountKeyPair" to options(keyPair).copy(
                    sourceAccountKeyPair = KeyPair.random(),
                    contractId = XLM_TESTNET_ID,
                    network = Network.PUBLIC,
                    rpcUrl = OTHER_RPC_URL
                ),
            "The options must keep this SACClient's sourceAccountKeyPair, contractId, network, and rpcUrl; " +
                "got a different contractId" to options(keyPair).copy(
                    contractId = XLM_TESTNET_ID,
                    network = Network.PUBLIC,
                    rpcUrl = OTHER_RPC_URL
                ),
            "The options must keep this SACClient's sourceAccountKeyPair, contractId, network, and rpcUrl; " +
                "got a different network" to options(keyPair).copy(network = Network.PUBLIC, rpcUrl = OTHER_RPC_URL)
        )

        for ((expected, options) in changed) {
            val transferFailure = assertFailsWith<IllegalArgumentException>(expected) {
                client.transfer(Address(keyPair.getAccountId()), to, BigInteger.ONE, options)
            }
            val submitFailure = assertFailsWith<IllegalArgumentException>(expected) {
                client.transferAndSubmit(to, BigInteger.ONE, options)
            }
            assertEquals(expected, transferFailure.message)
            assertEquals(expected, submitFailure.message)
        }
        assertEquals(emptyList(), rpc.methods)
    }

    @Test
    fun testOptionsWithThePublicKeyOfTheSourcePass() = runTest {
        val keyPair = KeyPair.random()
        val to = Address(MOCK_RPC_CONTRACT_ID)
        val from = Address(keyPair.getAccountId())
        val keyedRpc = SacRpc(keyPair.getAccountId(), listOf(writeSimulation))
        val publicOnly = options(KeyPair.fromAccountId(keyPair.getAccountId()))

        val keyedBefore = nowSeconds()
        keyedClient(keyedRpc, keyPair).transfer(from, to, BigInteger.ONE, publicOnly)
        val response = keyedClient(keyedRpc, keyPair).transferAndSubmit(to, BigInteger.ONE, publicOnly)
        val keyedAfter = nowSeconds()

        assertEquals(GetTransactionStatus.SUCCESS, response.status)
        assertEquals(listOf(1), keyedRpc.sentSignatureCounts())
        val keyedSimulations = keyedRpc.simulations()
        assertEquals(2, keyedSimulations.size)
        keyedSimulations.forEach {
            assertSimulates(it, "transfer", transferArguments(from, to, BigInteger.ONE), WriteOptions(keyedBefore, keyedAfter))
            assertEquals(keyPair.getAccountId(), it.sourceAccount)
        }

        val readOnlyRpc = SacRpc(SACClient.READ_ONLY_SOURCE_ACCOUNT_ID, listOf(writeSimulation))
        val placeholder = options(KeyPair.fromAccountId(SACClient.READ_ONLY_SOURCE_ACCOUNT_ID))
        val before = nowSeconds()
        readOnlyClient(readOnlyRpc).transfer(from, to, BigInteger.ONE, placeholder)
        val after = nowSeconds()
        assertEquals(listOf("simulateTransaction"), readOnlyRpc.methods)
        val readOnlySimulation = readOnlyRpc.simulations().single()
        assertSimulates(readOnlySimulation, "transfer", transferArguments(from, to, BigInteger.ONE), WriteOptions(before, after))
        assertEquals(SACClient.READ_ONLY_SOURCE_ACCOUNT_ID, readOnlySimulation.sourceAccount)
    }

    @Test
    fun testWithoutSimulationATransferIsReturnedUnsimulated() = runTest {
        val keyPair = KeyPair.random()
        val to = Address(MOCK_RPC_CONTRACT_ID)
        val from = Address(keyPair.getAccountId())

        val keyedRpc = SacRpc(keyPair.getAccountId(), listOf(writeSimulation))
        val keyed = keyedClient(keyedRpc, keyPair).transfer(from, to, BigInteger.ONE, options(keyPair).copy(simulate = false))
        assertNull(keyed.simulation)
        assertNull(keyed.builtTransaction)
        assertEquals(listOf("getLedgerEntries"), keyedRpc.methods)

        val readOnlyRpc = SacRpc(SACClient.READ_ONLY_SOURCE_ACCOUNT_ID, listOf(writeSimulation))
        val placeholder = KeyPair.fromAccountId(SACClient.READ_ONLY_SOURCE_ACCOUNT_ID)
        val readOnly = readOnlyClient(readOnlyRpc).transfer(from, to, BigInteger.ONE, options(placeholder).copy(simulate = false))
        assertNull(readOnly.simulation)
        assertNull(readOnly.builtTransaction)
        assertEquals(emptyList(), readOnlyRpc.methods)
    }

    @Test
    fun testWithoutSimulationTransferAndSubmitThrowsAfterItsChecks() = runTest {
        val keyPair = KeyPair.random()
        val rpc = SacRpc(keyPair.getAccountId(), listOf(writeSimulation))

        val failure = assertFailsWith<NotYetSimulatedException> {
            keyedClient(rpc, keyPair).transferAndSubmit(
                Address(MOCK_RPC_CONTRACT_ID), BigInteger.ONE, options(keyPair).copy(simulate = false)
            )
        }

        assertEquals("Transaction has not yet been simulated.", failure.message)
        assertEquals(listOf("getLedgerEntries"), rpc.methods)
    }

    @Test
    fun testSignOfAnUnsimulatedReadOnlyTransferThrowsNotYetSimulatedBeforeTheRefusal() = runTest {
        val source = SACClient.READ_ONLY_SOURCE_ACCOUNT_ID
        val rpc = SacRpc(source, listOf(writeSimulation))
        val unsimulated = options(KeyPair.fromAccountId(source)).copy(simulate = false)
        val transaction = readOnlyClient(rpc).transfer(
            Address(KeyPair.random().getAccountId()), Address(MOCK_RPC_CONTRACT_ID), BigInteger.ONE, unsimulated
        )
        val attempts: List<Pair<String, suspend () -> Unit>> = listOf(
            "sign()" to { transaction.sign() },
            "sign(force = true)" to { transaction.sign(force = true) },
            "sign(KeyPair.random())" to { transaction.sign(KeyPair.random()) },
            "signAndSubmit()" to { transaction.signAndSubmit() }
        )

        for ((name, attempt) in attempts) {
            val failure = assertFailsWith<NotYetSimulatedException>(name) { attempt() }
            assertEquals("Transaction has not yet been simulated.", failure.message, name)
        }
        assertNull(transaction.signed)
        assertEquals(emptyList(), rpc.methods)
    }

    @Test
    fun testWithoutRestoreAKeyedWriteKeepsThePreambleAndRefusesToSign() = runTest {
        val keyPair = KeyPair.random()
        val to = Address(MOCK_RPC_CONTRACT_ID)
        val noRestore = options(keyPair).copy(restore = false)
        val from = Address(keyPair.getAccountId())
        val rpc = SacRpc(keyPair.getAccountId(), listOf(restoreNeeded()))
        val client = keyedClient(rpc, keyPair)

        val before = nowSeconds()
        val transaction = client.transfer(from, to, BigInteger.ONE, noRestore)
        assertNotNull(transaction.simulation!!.restorePreamble)
        val signFailure = assertFailsWith<ExpiredStateException> { transaction.signAndSubmit() }
        val submitFailure = assertFailsWith<ExpiredStateException> { client.transferAndSubmit(to, BigInteger.ONE, noRestore) }
        val after = nowSeconds()

        assertEquals(EXPIRED_STATE, signFailure.message)
        assertEquals(EXPIRED_STATE, submitFailure.message)
        // Without restore, no restore transaction is simulated or sent.
        assertEquals(
            listOf(
                "getLedgerEntries", "getLedgerEntries", "simulateTransaction",
                "getLedgerEntries", "getLedgerEntries", "simulateTransaction"
            ),
            rpc.methods
        )
        val simulations = rpc.simulations()
        assertEquals(2, simulations.size)
        simulations.forEach {
            assertSimulates(it, "transfer", transferArguments(from, to, BigInteger.ONE), WriteOptions(before, after))
        }
    }

    @Test
    fun testWithoutRestoreAReadOnlyWriteRefusesToSignFirst() = runTest {
        val source = SACClient.READ_ONLY_SOURCE_ACCOUNT_ID
        val rpc = SacRpc(source, listOf(restoreNeeded()))
        val noRestore = options(KeyPair.fromAccountId(source)).copy(restore = false)
        val from = Address(KeyPair.random().getAccountId())
        val to = Address(MOCK_RPC_CONTRACT_ID)

        val before = nowSeconds()
        val transaction = readOnlyClient(rpc).transfer(from, to, BigInteger.ONE, noRestore)
        val after = nowSeconds()

        assertNotNull(transaction.simulation!!.restorePreamble)
        val failure = assertFailsWith<IllegalStateException> { transaction.sign() }
        assertEquals(readOnlyRefusal(source), failure.message)
        assertEquals(listOf("simulateTransaction"), rpc.methods)
        assertSimulates(
            rpc.simulations().single(), "transfer", transferArguments(from, to, BigInteger.ONE), WriteOptions(before, after)
        )
    }

    @Test
    fun testAutoSubmitChangesNeitherWrite() = runTest {
        val keyPair = KeyPair.random()
        val from = Address(keyPair.getAccountId())
        val to = Address(MOCK_RPC_CONTRACT_ID)
        val noAutoSubmit = options(keyPair).copy(autoSubmit = false)

        val transferRpc = SacRpc(keyPair.getAccountId(), listOf(writeSimulation))
        val transferBefore = nowSeconds()
        val transaction = keyedClient(transferRpc, keyPair).transfer(from, to, BigInteger.ONE, noAutoSubmit)
        val transferAfter = nowSeconds()

        assertNotNull(transaction.builtTransaction)
        assertNull(transaction.signed)
        assertEquals(listOf("getLedgerEntries", "getLedgerEntries", "simulateTransaction"), transferRpc.methods)
        assertSimulates(
            transferRpc.simulations().single(), "transfer", transferArguments(from, to, BigInteger.ONE),
            WriteOptions(transferBefore, transferAfter)
        )

        val submitRpc = SacRpc(keyPair.getAccountId(), listOf(writeSimulation))
        val submitBefore = nowSeconds()
        val response = keyedClient(submitRpc, keyPair).transferAndSubmit(to, BigInteger.ONE, noAutoSubmit)
        val submitAfter = nowSeconds()

        assertEquals(GetTransactionStatus.SUCCESS, response.status)
        assertEquals(SacRpc.TX_HASH, response.txHash)
        assertEquals(
            listOf("getLedgerEntries", "getLedgerEntries", "simulateTransaction", "sendTransaction", "getTransaction"),
            submitRpc.methods
        )
        assertSimulates(
            submitRpc.simulations().single(), "transfer", transferArguments(from, to, BigInteger.ONE),
            WriteOptions(submitBefore, submitAfter)
        )
        assertEquals(listOf(1), submitRpc.sentSignatureCounts())
    }

    @Test
    fun testTheSubmitTimeoutBoundsTheWaitOfBothWrites() = runTest(timeout = 90.seconds) {
        withContext(Dispatchers.Default) {
            val keyPair = KeyPair.random()
            val from = Address(keyPair.getAccountId())
            val to = Address(MOCK_RPC_CONTRACT_ID)
            val shortWait = options(keyPair).copy(submitTimeout = 3)
            val expected = "Waited 3 seconds for transaction to complete, but it did not. Returning anyway. You can " +
                "call result() to await the result later or check the status of the transaction manually."

            val submitRpc = SacRpc(keyPair.getAccountId(), listOf(writeSimulation), getTransaction = listOf(transactionNotFound))
            val submitStart = TransactionBuilder.currentTimeMillis()
            val submitBefore = nowSeconds()
            val submitFailure = assertFailsWith<TransactionStillPendingException> {
                keyedClient(submitRpc, keyPair).transferAndSubmit(to, BigInteger.ONE, shortWait)
            }
            val submitAfter = nowSeconds()
            val submitWait = TransactionBuilder.currentTimeMillis() - submitStart
            assertEquals(expected, submitFailure.message)

            val transferRpc = SacRpc(keyPair.getAccountId(), listOf(writeSimulation), getTransaction = listOf(transactionNotFound))
            val transferBefore = nowSeconds()
            val transaction = keyedClient(transferRpc, keyPair).transfer(from, to, BigInteger.ONE, shortWait)
            val transferAfter = nowSeconds()
            val transferStart = TransactionBuilder.currentTimeMillis()
            val transferFailure = assertFailsWith<TransactionStillPendingException> { transaction.signAndSubmit() }
            val transferWait = TransactionBuilder.currentTimeMillis() - transferStart
            assertEquals(expected, transferFailure.message)
            assertSame(transaction, transferFailure.assembledTransaction)

            // The wait ends after the 3 seconds of the options, well before the default 30.
            for (wait in listOf(submitWait, transferWait)) {
                assertTrue(wait in 3_000L until 30_000L, "waited $wait ms")
            }
            for (rpc in listOf(submitRpc, transferRpc)) {
                assertTrue(rpc.methods.count { it == "getTransaction" } > 1, "${rpc.methods}")
            }
            assertSimulates(
                submitRpc.simulations().single(), "transfer", transferArguments(from, to, BigInteger.ONE),
                WriteOptions(submitBefore, submitAfter)
            )
            assertSimulates(
                transferRpc.simulations().single(), "transfer", transferArguments(from, to, BigInteger.ONE),
                WriteOptions(transferBefore, transferAfter)
            )
        }
    }

    // ==================== toBaseUnits ====================

    @Test
    fun testToBaseUnitsConvertsDecimalAmounts() {
        val vectors = listOf(
            "0" to "0",
            "1" to "10000000",
            "12.5" to "125000000",
            "0.0000001" to "1",
            "1.2345678" to "12345678",
            "007" to "70000000",
            "922337203685.4775807" to "9223372036854775807",
            "17014118346046923173168730371588.4105727" to "170141183460469231731687303715884105727"
        )

        for ((amount, baseUnits) in vectors) {
            assertEquals(BigInteger.parseString(baseUnits, 10), SACClient.toBaseUnits(amount), amount)
        }
    }

    @Test
    fun testToBaseUnitsRejectsMalformedAmounts() {
        val rejected = listOf(
            "1.12345678" to "\"1.12345678\"",
            "-1" to "\"-1\"",
            "+1" to "\"+1\"",
            " 1" to "\" 1\"",
            "1 " to "\"1 \"",
            "1\n" to "\"1\\n\"",
            "" to "\"\"",
            "1." to "\"1.\"",
            ".5" to "\".5\"",
            "1e7" to "\"1e7\"",
            "1,5" to "\"1,5\"",
            "١" to "\"١\"",
            "１" to "\"１\""
        )

        for ((amount, preview) in rejected) {
            val failure = assertFailsWith<IllegalArgumentException>(amount) { SACClient.toBaseUnits(amount) }
            assertEquals(amountGrammar(preview), failure.message, amount)
        }
    }

    @Test
    fun testToBaseUnitsPreviewEscapesAndTruncatesTheAmount() {
        // JSON leaves DEL raw, and XdrJson.preview writes it as \x with two lowercase hex digits.
        val cases = listOf(
            "1\n" to "\"1\\n\"",
            "1\u007F" to "\"1\\x7f\"",
            "a".repeat(100) to "\"" + "a".repeat(79) + "..."
        )

        for ((amount, preview) in cases) {
            val failure = assertFailsWith<IllegalArgumentException> { SACClient.toBaseUnits(amount) }
            assertEquals(amountGrammar(preview), failure.message)
        }
    }

    @Test
    fun testToBaseUnitsRejectsAmountsAboveTheLargestI128() {
        val failure = assertFailsWith<IllegalArgumentException> {
            SACClient.toBaseUnits("17014118346046923173168730371588.4105728")
        }

        assertEquals(
            "Amount 17014118346046923173168730371588.4105728 exceeds the largest i128 amount, " +
                "17014118346046923173168730371588.4105727",
            failure.message
        )
    }

    @Test
    fun testToBaseUnitsNamesASecretSeedByItsKindOnly() = runTest {
        val seed = KeyPair.random().getSecretSeed()!!.concatToString()

        val failure = assertFailsWith<IllegalArgumentException> { SACClient.toBaseUnits(seed) }

        assertEquals("Invalid amount: got a secret seed (S...) instead of a decimal amount", failure.message)
        assertFalse(seed in failure.message!!)
    }
}

// ==================== Shared by the SAC unit tests ====================

/**
 * An RPC URL that refuses connections on loopback. A client whose server has a real engine
 * fails offline on any request to it, so a test that opens a client through a public factory
 * fails if the factory sends a request.
 */
internal const val REFUSING_RPC_URL = "http://127.0.0.1:1"

/**
 * An HttpClient over a MockEngine for JSON-RPC. Each request body is parsed and passed to [answer],
 * whose reply is sent with the status and body it names as application/json. The mock never
 * reaches a network.
 */
internal fun jsonRpcClient(answer: (body: JsonObject) -> RpcReply): HttpClient = HttpClient(
    MockEngine { request ->
        val reply = answer(Json.parseToJsonElement(request.body.toByteArray().decodeToString()).jsonObject)
        respond(
            content = ByteReadChannel(reply.body),
            status = reply.status,
            headers = headersOf(HttpHeaders.ContentType, "application/json")
        )
    }
) {
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = false })
    }
}

/** A JSON-RPC answer whose result is [json]. */
internal fun rpcResult(json: String): RpcReply = RpcReply("""{"jsonrpc":"2.0","id":"1","result":$json}""")

/** One decoded simulateTransaction request. */
internal class SacSimulation(val transaction: TransactionXdr, val useUpgradedAuth: Boolean) {
    val operationBody: OperationBodyXdr get() = transaction.operations.single().body
    val invocation: InvokeContractArgsXdr
        get() {
            val body = assertIs<OperationBodyXdr.InvokeHostFunctionOp>(operationBody)
            return assertIs<HostFunctionXdr.InvokeContract>(body.value.hostFunction).value
        }
    val sourceAccount: String get() = MuxedAccount.fromXdr(transaction.sourceAccount).accountId
    val maxTime: Long
        get() = assertIs<PreconditionsXdr.TimeBounds>(transaction.cond).value.maxTime.value.value.toLong()
}

/**
 * A Soroban RPC over a MockEngine. getLedgerEntries answers with the account entry of
 * [sourceAccount]; simulateTransaction, sendTransaction and getTransaction answer with the next
 * reply of their script and repeat its last reply. A request of any other method, or of a method
 * whose script is empty, gets a JSON-RPC error. Every request is recorded.
 */
internal class SacRpc(
    private val sourceAccount: String,
    simulate: List<RpcReply> = emptyList(),
    send: List<RpcReply> = listOf(SEND_PENDING),
    getTransaction: List<RpcReply> = listOf(TRANSACTION_SUCCESS)
) {
    private val requests = mutableListOf<JsonObject>()
    private val scripts = mapOf(
        "simulateTransaction" to ArrayDeque(simulate),
        "sendTransaction" to ArrayDeque(send),
        "getTransaction" to ArrayDeque(getTransaction)
    )

    val httpClient = jsonRpcClient { body ->
        requests.add(body)
        reply(body["method"]!!.jsonPrimitive.content)
    }

    val server = SorobanServer(MOCK_RPC_SERVER_URL, httpClient)

    val methods: List<String> get() = requests.map { it["method"]!!.jsonPrimitive.content }

    private fun reply(method: String): RpcReply {
        if (method == "getLedgerEntries") return rpcResult(ledgerEntriesResultJson(sourceAccount))
        val script = scripts[method]?.takeIf { it.isNotEmpty() }
            ?: return RpcReply("""{"jsonrpc":"2.0","id":"1","error":{"code":-32600,"message":"unexpected $method"}}""")
        return if (script.size > 1) script.removeFirst() else script.first()
    }

    fun simulations(): List<SacSimulation> = requests
        .filter { it["method"]!!.jsonPrimitive.content == "simulateTransaction" }
        .map {
            val params = it["params"]!!.jsonObject
            SacSimulation(v1EnvelopeOf(params).tx, params["useUpgradedAuth"]!!.jsonPrimitive.boolean)
        }

    /** The signature count of each sent transaction. */
    fun sentSignatureCounts(): List<Int> = requests
        .filter { it["method"]!!.jsonPrimitive.content == "sendTransaction" }
        .map { v1EnvelopeOf(it["params"]!!.jsonObject).signatures.size }

    companion object {
        /** The hash of the transaction that the default replies report. */
        const val TX_HASH = "3389e9f0f1a7c19f0e9b8a1e9d2b3c4d5e6f70819293a4b5c6d7e8f901020304"

        /** The default sendTransaction reply: the network accepted the transaction. */
        val SEND_PENDING = rpcResult("""{"status":"PENDING","hash":"$TX_HASH","latestLedger":14245}""")

        /** The default getTransaction reply: the transaction succeeded and returned void. */
        val TRANSACTION_SUCCESS = rpcResult(
            """{"status":"SUCCESS","txHash":"$TX_HASH","latestLedger":14246,""" +
                """"resultMetaXdr":"${successResultMetaBase64(Scv.toVoid())}"}"""
        )
    }
}

/** The refusal of a transaction that a read-only client built for [sourceAccount]. */
internal fun readOnlyRefusal(sourceAccount: String): String =
    "This transaction was built by a read-only client for source account $sourceAccount, which has no " +
        "keypair to sign with; build it from a client opened with the submitting account's keypair"

/**
 * A simulateTransaction result with a void return value, the auth entries [auth], and a restore
 * preamble, as for a call whose ledger entries are archived.
 */
internal fun restorePreambleResultJson(auth: List<SorobanAuthorizationEntryXdr>): String {
    val txData = minimalSorobanData().toXdrBase64()
    val authArray = auth.joinToString(",") { "\"${it.toXdrBase64()}\"" }
    return """{"transactionData":"$txData","minResourceFee":"100",""" +
        """"results":[{"auth":[$authArray],"xdr":"${Scv.toVoid().toXdrBase64()}"}],""" +
        """"restorePreamble":{"transactionData":"$txData","minResourceFee":50},"latestLedger":14245}"""
}

/** An auth entry for a call of [contractId] with unsigned address credentials for [address]. */
internal fun unsignedAddressAuthEntry(contractId: String, address: String): SorobanAuthorizationEntryXdr =
    writeAuthEntry(contractId).copy(
        credentials = SorobanCredentialsXdr.Address(
            SorobanAddressCredentialsXdr(
                address = Address(address).toSCAddress(),
                nonce = Int64Xdr(7L),
                signatureExpirationLedger = Uint32Xdr(0u),
                signature = SCValXdr.Void(SCValTypeXdr.SCV_VOID)
            )
        )
    )

/** The V1 envelope of the transaction in the [params] of a JSON-RPC request. */
internal fun v1EnvelopeOf(params: JsonObject): TransactionV1EnvelopeXdr {
    val envelope = TransactionEnvelopeXdr.fromXdrBase64(params["transaction"]!!.jsonPrimitive.content)
    return assertIs<TransactionEnvelopeXdr.V1>(envelope).value
}
