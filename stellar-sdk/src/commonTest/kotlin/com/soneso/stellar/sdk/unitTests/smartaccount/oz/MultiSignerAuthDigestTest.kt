//
//  MultiSignerAuthDigestTest.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.unitTests.smartaccount.oz

import com.soneso.stellar.sdk.Address
import com.soneso.stellar.sdk.KeyPair
import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.StrKey
import com.soneso.stellar.sdk.Util
import com.soneso.stellar.sdk.rpc.SorobanServer
import com.soneso.stellar.sdk.rpc.exception.SorobanRpcException
import com.soneso.stellar.sdk.scval.Scv
import com.soneso.stellar.sdk.smartaccount.core.SmartAccountAuth
import com.soneso.stellar.sdk.smartaccount.core.ValidationException
import com.soneso.stellar.sdk.smartaccount.oz.ConnectedWallet
import com.soneso.stellar.sdk.smartaccount.oz.ContextRuleType
import com.soneso.stellar.sdk.smartaccount.oz.ExternalWalletAdapter
import com.soneso.stellar.sdk.smartaccount.oz.OZExternalEd25519SignerAdapter
import com.soneso.stellar.sdk.smartaccount.oz.OZSmartAccountConfig
import com.soneso.stellar.sdk.smartaccount.oz.OZSmartAccountKit
import com.soneso.stellar.sdk.smartaccount.oz.SelectedSigner
import com.soneso.stellar.sdk.smartaccount.oz.SignAuthEntryOptions
import com.soneso.stellar.sdk.smartaccount.oz.SignAuthEntryResult
import com.soneso.stellar.sdk.unitTests.smartaccount.IndependentAuthDigest
import com.soneso.stellar.sdk.unitTests.smartaccount.buildAccountEntryXdr
import com.soneso.stellar.sdk.unitTests.smartaccount.buildAuthEntry
import com.soneso.stellar.sdk.unitTests.smartaccount.buildMinimalSorobanData
import com.soneso.stellar.sdk.unitTests.smartaccount.contractInstanceEntriesResponseJson
import com.soneso.stellar.sdk.unitTests.smartaccount.getTransactionSuccessResponseJson
import com.soneso.stellar.sdk.unitTests.smartaccount.latestLedgerResponseJson
import com.soneso.stellar.sdk.unitTests.smartaccount.ledgerEntriesResponseJson
import com.soneso.stellar.sdk.unitTests.smartaccount.rpcRequestMethod
import com.soneso.stellar.sdk.unitTests.smartaccount.sendTransactionPendingResponseJson
import com.soneso.stellar.sdk.unitTests.smartaccount.simulateCountResponseJson
import com.soneso.stellar.sdk.unitTests.smartaccount.stubHostFunction
import com.soneso.stellar.sdk.xdr.ContractExecutableXdr
import com.soneso.stellar.sdk.xdr.ContractIDXdr
import com.soneso.stellar.sdk.xdr.HashIDPreimageXdr
import com.soneso.stellar.sdk.xdr.HashXdr
import com.soneso.stellar.sdk.xdr.InvokeContractArgsXdr
import com.soneso.stellar.sdk.xdr.LedgerKeyXdr
import com.soneso.stellar.sdk.xdr.MuxedContractXdr
import com.soneso.stellar.sdk.xdr.SCAddressXdr
import com.soneso.stellar.sdk.xdr.SCMapXdr
import com.soneso.stellar.sdk.xdr.SCSymbolXdr
import com.soneso.stellar.sdk.xdr.SCValXdr
import com.soneso.stellar.sdk.xdr.SorobanAuthorizationEntryXdr
import com.soneso.stellar.sdk.xdr.SorobanAuthorizedFunctionXdr
import com.soneso.stellar.sdk.xdr.SorobanAuthorizedInvocationXdr
import com.soneso.stellar.sdk.xdr.Uint32Xdr
import com.soneso.stellar.sdk.xdr.Uint64Xdr
import com.soneso.stellar.sdk.xdr.XdrReader
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
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The digest scheme through [com.soneso.stellar.sdk.smartaccount.oz.OZMultiSignerManager.submitWithMultipleSigners]:
 * the Ed25519 digest and the delegated `__check_auth` argument for both schemes, and the order
 * of the scheme read and the signer guard relative to every signer, foreign wallet entries
 * included.
 */
@OptIn(ExperimentalEncodingApi::class)
class MultiSignerAuthDigestTest {

    private val wallet = "CDLZFC3SYJYDZT7K67VZ75HPJVIEUVNIXF47ZG2FB2RMQQVU2HHGCYSC"
    private val webauthnVerifier = "CB26VN37RCVNTHJZDEPK6IRO2MMTS3Z2IEO5JD5BINY2OOJ5KKJG7NKY"
    private val ed25519Verifier = StrKey.encodeContract(ByteArray(32) { (it + 40).toByte() })
    private val txHash = "d4721e2a61e9a6b3c6c2e5c0d4c0a5f3e2d1c0b9a8f7e6d5c4b3a2f1e0d9c8b7"

    private val v08Executable = ContractExecutableXdr.WasmHash(
        HashXdr(Util.hexToBytes("c514d35ea67eaea9bfc1fb3327854c0c06111bc1ddebd4fe217c504c1004b9e1"))
    )
    private val legacyExecutable = ContractExecutableXdr.WasmHash(HashXdr(ByteArray(32)))

    /** Latest ledger the scripted signing read reports; the expiration adds the default 720. */
    private val signingLedger = 1000L
    private val expiration = (signingLedger + 720).toUInt()

    private val rpcErrorJson = """{"jsonrpc":"2.0","id":"test-id","error":{"code":-32603,"message":"instance read failed"}}"""

    /** A scripted RPC that records each method and body; requests past the script get [trailing]. */
    private class RecordingRpc(private val responses: List<String>, private val trailing: String? = null) {
        val methods = mutableListOf<String>()
        val bodies = mutableListOf<String>()

        val server = SorobanServer(
            "https://soroban-testnet.stellar.org",
            HttpClient(MockEngine { request ->
                val body = request.body.toByteArray().decodeToString()
                bodies.add(body)
                methods.add(rpcRequestMethod(body))
                val response = responses.getOrNull(methods.size - 1) ?: trailing
                    ?: error("Unexpected RPC request ${methods.size - 1}: ${methods.last()}")
                respond(
                    content = ByteReadChannel(response),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }) {
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; isLenient = true }) }
            }
        )

        /** Number of getLedgerEntries requests for a contract data key, the instance reads. */
        fun instanceReads(): Int = bodies.count { body ->
            rpcRequestMethod(body) == "getLedgerEntries" &&
                Json.parseToJsonElement(body).jsonObject["params"]!!.jsonObject["keys"]!!.jsonArray.any {
                    LedgerKeyXdr.fromXdrBase64(it.jsonPrimitive.content) is LedgerKeyXdr.ContractData
                }
        }
    }

    /** An Ed25519 adapter over a run-time key that records the digests it signs. */
    private class RecordingEd25519Adapter(private val keypair: KeyPair) : OZExternalEd25519SignerAdapter {
        val digests = mutableListOf<ByteArray>()

        override fun canSignFor(verifierAddress: String, publicKey: ByteArray): Boolean =
            publicKey.contentEquals(keypair.getPublicKey())

        override suspend fun signAuthDigest(authDigest: ByteArray, publicKey: ByteArray): ByteArray {
            digests.add(authDigest)
            return keypair.sign(authDigest)
        }
    }

    /** A wallet adapter over a run-time key that records the preimages it signs. */
    private class RecordingWalletAdapter(private val keypair: KeyPair) : ExternalWalletAdapter {
        val preimages = mutableListOf<HashIDPreimageXdr>()
        private val connected = ConnectedWallet(address = keypair.getAccountId(), walletId = "test", walletName = "Test")

        override suspend fun connect(): ConnectedWallet = connected
        override suspend fun disconnect() {}
        override fun canSignFor(address: String): Boolean = address == keypair.getAccountId()
        override fun getConnectedWallets(): List<ConnectedWallet> = listOf(connected)
        override fun getWalletForAddress(address: String): ConnectedWallet? = if (canSignFor(address)) connected else null

        override suspend fun signAuthEntry(preimageXdr: String, options: SignAuthEntryOptions?): SignAuthEntryResult {
            preimages.add(HashIDPreimageXdr.decode(XdrReader(Base64.decode(preimageXdr))))
            val signature = keypair.sign(Util.hash(Base64.decode(preimageXdr)))
            return SignAuthEntryResult(signedAuthEntry = Base64.encode(signature), signerAddress = keypair.getAccountId())
        }
    }

    private fun kit(
        rpc: RecordingRpc,
        deployer: KeyPair,
        ed25519Adapter: OZExternalEd25519SignerAdapter? = null,
        walletAdapter: ExternalWalletAdapter? = null
    ): OZSmartAccountKit {
        val kit = OZSmartAccountKit.createWithServer(
            OZSmartAccountConfig(
                rpcUrl = "https://soroban-testnet.stellar.org",
                networkPassphrase = Network.TESTNET.networkPassphrase,
                accountWasmHash = "a" + "0".repeat(63),
                webauthnVerifierAddress = webauthnVerifier,
                deployerKeypair = deployer,
                externalEd25519Adapter = ed25519Adapter,
                externalWallet = walletAdapter
            ),
            rpc.server
        )
        return kit
    }

    private fun simulateJson(entries: List<SorobanAuthorizationEntryXdr>): String {
        val auth = entries.joinToString(",") { "\"${it.toXdrBase64()}\"" }
        return """
        {"jsonrpc":"2.0","id":"test-id","result":{"transactionData":"${buildMinimalSorobanData().toXdrBase64()}",
        "minResourceFee":100,"results":[{"auth":[$auth],"xdr":null}],"latestLedger":100}}
        """.trimIndent()
    }

    /** The full submission script of one multi-signer operation, with [signingRead] at index 2. */
    private fun submissionScript(deployer: KeyPair, entries: List<SorobanAuthorizationEntryXdr>, signingRead: String): List<String> {
        val account = ledgerEntriesResponseJson(buildAccountEntryXdr(deployer).toXdrBase64())
        val simulation = simulateJson(entries)
        val sorobanData = buildMinimalSorobanData().toXdrBase64()
        return listOf(
            account,
            simulation,
            signingRead,
            account,
            simulateCountResponseJson(SCValXdr.U32(Uint32Xdr(0u)).toXdrBase64(), sorobanData),
            account,
            simulation,
            sendTransactionPendingResponseJson(txHash)
        )
    }

    private fun instanceRead(executable: ContractExecutableXdr) =
        contractInstanceEntriesResponseJson(wallet, executable, signingLedger)

    /** An auth entry of the G-address [address] that the wallet adapter signs directly. */
    private fun foreignEntry(address: String) = buildAuthEntry(address)

    private fun rawWalletCall(functionName: String, args: List<SCValXdr>): SorobanAuthorizationEntryXdr =
        buildAuthEntry(wallet).copy(
            rootInvocation = SorobanAuthorizedInvocationXdr(
                function = SorobanAuthorizedFunctionXdr.ContractFn(
                    InvokeContractArgsXdr(Address(wallet).toSCAddress(), SCSymbolXdr(functionName), args)
                ),
                subInvocations = emptyList()
            )
        )

    private fun checkAuthArgs(preimage: HashIDPreimageXdr): List<SCValXdr> {
        val invocation = when (preimage) {
            is HashIDPreimageXdr.SorobanAuthorizationWithAddress -> preimage.value.invocation
            is HashIDPreimageXdr.SorobanAuthorization -> preimage.value.invocation
            else -> error("Unexpected preimage $preimage")
        }
        val call = (invocation.function as SorobanAuthorizedFunctionXdr.ContractFn).value
        assertEquals("__check_auth", call.functionName.value)
        assertEquals(wallet, Address.fromSCAddress(call.contractAddress).toString())
        return call.args
    }

    /** The signature payload of [entry] at the operation's expiration ledger. */
    private suspend fun payload(entry: SorobanAuthorizationEntryXdr): ByteArray =
        SmartAccountAuth.buildAuthPayloadHash(entry, expiration, Network.TESTNET.networkPassphrase)

    // MARK: - Ed25519 digest

    private suspend fun ed25519Digest(
        executable: ContractExecutableXdr,
        entry: SorobanAuthorizationEntryXdr = buildAuthEntry(wallet)
    ): Pair<ByteArray, SorobanAuthorizationEntryXdr> {
        val deployer = KeyPair.random()
        val rawSeed = Random.nextBytes(32)
        val adapter = RecordingEd25519Adapter(KeyPair.fromSecretSeed(rawSeed))
        val rpc = RecordingRpc(submissionScript(deployer, listOf(entry), instanceRead(executable)), getTransactionSuccessResponseJson(txHash))
        val kit = kit(rpc, deployer, ed25519Adapter = adapter)
        val publicKey = kit.externalSigners.addEd25519FromRawKey(rawSeed, ed25519Verifier)
        kit.setConnectedState(null, wallet)

        val result = kit.multiSignerManager.submitWithMultipleSigners(
            hostFunction = stubHostFunction(wallet),
            selectedSigners = listOf(SelectedSigner.Ed25519(verifierAddress = ed25519Verifier, publicKey = publicKey)),
            resolveContextRuleIds = { _, _ -> listOf(0u) }
        )
        assertTrue(result.success, "error=${result.error}")
        assertEquals(1, rpc.instanceReads(), "One instance read per operation")
        assertTrue("getLatestLedger" !in rpc.methods, "The instance read sets the expiration")
        return adapter.digests.single() to entry
    }

    @Test
    fun ed25519Signer_v08Wallet_signsThePreimageDigest() = runTest {
        val (digest, entry) = ed25519Digest(v08Executable)
        assertContentEquals(IndependentAuthDigest.preimageDigest(wallet, payload(entry), listOf(0u)), digest)
    }

    @Test
    fun ed25519Signer_legacyWallet_signsTheLegacyDigest() = runTest {
        val (digest, entry) = ed25519Digest(legacyExecutable)
        assertContentEquals(IndependentAuthDigest.legacyDigest(payload(entry), listOf(0u)), digest)
    }

    // MARK: - Raw signer additions the contract accepts

    private fun externalSignerValue(verifierAddress: String, keyData: ByteArray): SCValXdr = Scv.toVec(
        listOf(Scv.toSymbol("External"), Scv.toAddress(Address(verifierAddress).toSCAddress()), Scv.toBytes(keyData))
    )

    /** Submits [entry] on a 0.8.0 wallet with one Ed25519 signer, which signs its preimage digest. */
    private suspend fun assertSignsWithEd25519(entry: SorobanAuthorizationEntryXdr) {
        val (digest, signed) = ed25519Digest(v08Executable, entry)
        assertContentEquals(IndependentAuthDigest.preimageDigest(wallet, payload(signed), listOf(0u)), digest)
    }

    @Test
    fun rawAddSigner_externalWithEmptyKeyData_signs() = runTest {
        assertSignsWithEd25519(rawWalletCall("add_signer", listOf(Scv.toUint32(0u), externalSignerValue(ed25519Verifier, ByteArray(0)))))
    }

    @Test
    fun rawBatchAddSigner_externalWithEmptyKeyData_signs() = runTest {
        assertSignsWithEd25519(
            rawWalletCall("batch_add_signer", listOf(Scv.toUint32(0u), Scv.toVec(listOf(externalSignerValue(ed25519Verifier, ByteArray(0))))))
        )
    }

    @Test
    fun rawAddContextRule_externalWithEmptyKeyData_signs() = runTest {
        assertSignsWithEd25519(
            rawWalletCall(
                "add_context_rule",
                listOf(
                    ContextRuleType.Default.toScVal(),
                    Scv.toString("rule"),
                    Scv.toVoid(),
                    Scv.toVec(listOf(externalSignerValue(ed25519Verifier, ByteArray(0)))),
                    SCValXdr.Map(SCMapXdr(emptyList()))
                )
            )
        )
    }

    @Test
    fun rawAddSigner_externalWithAnAccountVerifier_signs() = runTest {
        val accountVerifier = KeyPair.random().getAccountId()
        assertSignsWithEd25519(rawWalletCall("add_signer", listOf(Scv.toUint32(0u), externalSignerValue(accountVerifier, ByteArray(65) { 4 }))))
    }

    private val muxedContract: SCAddressXdr = SCAddressXdr.MuxedContract(
        MuxedContractXdr(Uint64Xdr(42uL), ContractIDXdr(HashXdr(ByteArray(32) { 7 })))
    )

    /** Submits [entry] with an Ed25519 signer and expects the refusal naming [field], before any signer or instance read. */
    private suspend fun assertRefusedBeforeAnySigner(entry: SorobanAuthorizationEntryXdr, field: String) {
        val deployer = KeyPair.random()
        val rawSeed = Random.nextBytes(32)
        val adapter = RecordingEd25519Adapter(KeyPair.fromSecretSeed(rawSeed))
        val rpc = RecordingRpc(submissionScript(deployer, listOf(entry), instanceRead(v08Executable)))
        val kit = kit(rpc, deployer, ed25519Adapter = adapter)
        val publicKey = kit.externalSigners.addEd25519FromRawKey(rawSeed, ed25519Verifier)
        kit.setConnectedState(null, wallet)

        val ex = assertFailsWith<ValidationException.InvalidInput> {
            kit.multiSignerManager.submitWithMultipleSigners(
                hostFunction = stubHostFunction(wallet),
                selectedSigners = listOf(SelectedSigner.Ed25519(verifierAddress = ed25519Verifier, publicKey = publicKey)),
                resolveContextRuleIds = { _, _ -> listOf(0u) }
            )
        }
        assertTrue(
            ex.message.contains("$field has an unsupported address type: SC_ADDRESS_TYPE_MUXED_CONTRACT, got: ${Address.fromSCAddress(muxedContract)}"),
            ex.message
        )
        assertEquals(0, adapter.digests.size, "No signer is asked")
        assertEquals(listOf("getLedgerEntries", "simulateTransaction"), rpc.methods, "No instance read after the refusal")
    }

    @Test
    fun rawAddSigner_delegatedMuxedContract_isRefusedBeforeAnySigner() = runTest {
        assertRefusedBeforeAnySigner(
            rawWalletCall("add_signer", listOf(Scv.toUint32(0u), Scv.toVec(listOf(Scv.toSymbol("Delegated"), Scv.toAddress(muxedContract))))),
            "Delegated signer address"
        )
    }

    @Test
    fun rawAddSigner_externalMuxedContractVerifier_isRefusedBeforeAnySigner() = runTest {
        assertRefusedBeforeAnySigner(
            rawWalletCall(
                "add_signer",
                listOf(Scv.toUint32(0u), Scv.toVec(listOf(Scv.toSymbol("External"), Scv.toAddress(muxedContract), Scv.toBytes(ByteArray(65) { 4 }))))
            ),
            "External signer verifier address"
        )
    }

    @Test
    fun rawAddSigner_delegatedAccount_signs() = runTest {
        val delegate = KeyPair.random().getAccountId()
        assertSignsWithEd25519(
            rawWalletCall("add_signer", listOf(Scv.toUint32(0u), Scv.toVec(listOf(Scv.toSymbol("Delegated"), Scv.toAddress(Address(delegate).toSCAddress())))))
        )
    }

    // MARK: - Delegated argument

    private suspend fun delegatedArgs(executable: ContractExecutableXdr): Pair<List<SCValXdr>, SorobanAuthorizationEntryXdr> {
        val deployer = KeyPair.random()
        val walletSigner = KeyPair.random()
        val adapter = RecordingWalletAdapter(walletSigner)
        val entry = buildAuthEntry(wallet)
        val rpc = RecordingRpc(submissionScript(deployer, listOf(entry), instanceRead(executable)), getTransactionSuccessResponseJson(txHash))
        val kit = kit(rpc, deployer, walletAdapter = adapter)
        kit.setConnectedState(null, wallet)

        val result = kit.multiSignerManager.submitWithMultipleSigners(
            hostFunction = stubHostFunction(wallet),
            selectedSigners = listOf(SelectedSigner.Wallet(address = walletSigner.getAccountId())),
            resolveContextRuleIds = { _, _ -> listOf(0u) }
        )
        assertTrue(result.success, "error=${result.error}")
        return checkAuthArgs(adapter.preimages.single()) to entry
    }

    @Test
    fun delegatedSigner_v08Wallet_authorizesThePreimage() = runTest {
        val (args, entry) = delegatedArgs(v08Executable)
        val expected = IndependentAuthDigest.preimage(wallet, payload(entry), listOf(0u))
        assertEquals(listOf(expected.toXdrBase64()), args.map { it.toXdrBase64() }, "The 0.8 argument is the AuthDigestPreimage map")
    }

    @Test
    fun delegatedSigner_legacyWallet_authorizesTheDigestBytes() = runTest {
        val (args, entry) = delegatedArgs(legacyExecutable)
        val expected = Scv.toBytes(IndependentAuthDigest.legacyDigest(payload(entry), listOf(0u)))
        assertEquals(listOf(expected.toXdrBase64()), args.map { it.toXdrBase64() })
    }

    // MARK: - Order: nothing is signed before the scheme read and the guard

    @Test
    fun foreignEntryFirst_instanceReadFails_noSignerIsAsked() = runTest {
        val deployer = KeyPair.random()
        val walletSigner = KeyPair.random()
        val walletAdapter = RecordingWalletAdapter(walletSigner)
        val rawSeed = Random.nextBytes(32)
        val ed25519Adapter = RecordingEd25519Adapter(KeyPair.fromSecretSeed(rawSeed))
        val entries = listOf(foreignEntry(walletSigner.getAccountId()), buildAuthEntry(wallet))
        val rpc = RecordingRpc(submissionScript(deployer, entries, rpcErrorJson))
        val kit = kit(rpc, deployer, ed25519Adapter = ed25519Adapter, walletAdapter = walletAdapter)
        val publicKey = kit.externalSigners.addEd25519FromRawKey(rawSeed, ed25519Verifier)
        kit.setConnectedState(null, wallet)

        assertFailsWith<SorobanRpcException> {
            kit.multiSignerManager.submitWithMultipleSigners(
                hostFunction = stubHostFunction(wallet),
                selectedSigners = listOf(
                    SelectedSigner.Wallet(address = walletSigner.getAccountId()),
                    SelectedSigner.Ed25519(verifierAddress = ed25519Verifier, publicKey = publicKey)
                ),
                resolveContextRuleIds = { _, _ -> listOf(0u) }
            )
        }
        assertEquals(0, walletAdapter.preimages.size, "The foreign entry's wallet signer must not be asked")
        assertEquals(0, ed25519Adapter.digests.size)
        assertEquals(listOf("getLedgerEntries", "simulateTransaction", "getLedgerEntries"), rpc.methods)
    }

    @Test
    fun foreignEntryFirst_rawBatchAddOfDefaultDeployer_refusedBeforeAnySignerOrRead() = runTest {
        val deployer = KeyPair.random()
        val walletSigner = KeyPair.random()
        val walletAdapter = RecordingWalletAdapter(walletSigner)
        val defaultDeployer = OZSmartAccountConfig.createDefaultDeployer().getAccountId()
        val batchAdd = rawWalletCall(
            "batch_add_signer",
            listOf(
                Scv.toUint32(0u),
                Scv.toVec(listOf(Scv.toVec(listOf(Scv.toSymbol("Delegated"), Scv.toAddress(Address(defaultDeployer).toSCAddress())))))
            )
        )
        val entries = listOf(foreignEntry(walletSigner.getAccountId()), batchAdd)
        val rpc = RecordingRpc(submissionScript(deployer, entries, instanceRead(v08Executable)))
        val kit = kit(rpc, deployer, walletAdapter = walletAdapter)
        kit.setConnectedState(null, wallet)

        val ex = assertFailsWith<ValidationException.InvalidInput> {
            kit.multiSignerManager.submitWithMultipleSigners(
                hostFunction = stubHostFunction(wallet),
                selectedSigners = listOf(SelectedSigner.Wallet(address = walletSigner.getAccountId())),
                resolveContextRuleIds = { _, _ -> listOf(0u) }
            )
        }
        assertTrue(ex.message.contains(defaultDeployer), "The refusal names the default deployer: ${ex.message}")
        assertEquals(0, walletAdapter.preimages.size, "The foreign entry's wallet signer must not be asked")
        assertEquals(listOf("getLedgerEntries", "simulateTransaction"), rpc.methods)
    }

    @Test
    fun noSmartAccountEntry_readsNoInstance() = runTest {
        val deployer = KeyPair.random()
        val walletSigner = KeyPair.random()
        val walletAdapter = RecordingWalletAdapter(walletSigner)
        val entries = listOf(foreignEntry(walletSigner.getAccountId()))
        val rpc = RecordingRpc(
            submissionScript(deployer, entries, latestLedgerResponseJson(signingLedger.toInt())),
            getTransactionSuccessResponseJson(txHash)
        )
        val kit = kit(rpc, deployer, walletAdapter = walletAdapter)
        kit.setConnectedState(null, wallet)

        val result = kit.multiSignerManager.submitWithMultipleSigners(
            hostFunction = stubHostFunction(wallet),
            selectedSigners = listOf(SelectedSigner.Wallet(address = walletSigner.getAccountId())),
            resolveContextRuleIds = { _, _ -> listOf(0u) }
        )

        assertTrue(result.success, "error=${result.error}")
        assertEquals(0, rpc.instanceReads(), "Zero instance reads without a smart account entry")
        assertEquals("getLatestLedger", rpc.methods[2])
        val signed = assertNotNull(walletAdapter.preimages.singleOrNull(), "The foreign entry is signed once")
        assertTrue(signed is HashIDPreimageXdr.SorobanAuthorization || signed is HashIDPreimageXdr.SorobanAuthorizationWithAddress)
    }
}
