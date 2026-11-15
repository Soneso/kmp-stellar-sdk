//
//  AuthDigestSchemeSelectionTest.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.unitTests.smartaccount.oz

import com.soneso.stellar.sdk.Address
import com.soneso.stellar.sdk.KeyPair
import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.Util
import com.soneso.stellar.sdk.rpc.SorobanServer
import com.soneso.stellar.sdk.rpc.exception.SorobanRpcException
import com.soneso.stellar.sdk.scval.Scv
import com.soneso.stellar.sdk.smartaccount.core.AuthDigestScheme
import com.soneso.stellar.sdk.smartaccount.core.ConfigurationException
import com.soneso.stellar.sdk.smartaccount.core.TransactionException
import com.soneso.stellar.sdk.smartaccount.oz.OZConstants
import com.soneso.stellar.sdk.smartaccount.oz.OZSmartAccountConfig
import com.soneso.stellar.sdk.smartaccount.oz.OZSmartAccountKit
import com.soneso.stellar.sdk.smartaccount.oz.SigningOperationState
import com.soneso.stellar.sdk.unitTests.smartaccount.buildAccountEntryXdr
import com.soneso.stellar.sdk.unitTests.smartaccount.buildAuthEntry
import com.soneso.stellar.sdk.unitTests.smartaccount.contractInstanceEntriesResponseJson
import com.soneso.stellar.sdk.unitTests.smartaccount.emptyLedgerEntriesResponseJson
import com.soneso.stellar.sdk.unitTests.smartaccount.latestLedgerResponseJson
import com.soneso.stellar.sdk.unitTests.smartaccount.ledgerEntriesResponseJson
import com.soneso.stellar.sdk.unitTests.smartaccount.rpcRequestMethod
import com.soneso.stellar.sdk.xdr.ContractDataDurabilityXdr
import com.soneso.stellar.sdk.xdr.ContractDataEntryXdr
import com.soneso.stellar.sdk.xdr.ContractExecutableExternalRefXdr
import com.soneso.stellar.sdk.xdr.ContractExecutableXdr
import com.soneso.stellar.sdk.xdr.ExtensionPointXdr
import com.soneso.stellar.sdk.xdr.HashXdr
import com.soneso.stellar.sdk.xdr.LedgerEntryDataXdr
import com.soneso.stellar.sdk.xdr.LedgerKeyXdr
import com.soneso.stellar.sdk.xdr.SorobanAuthorizationEntryXdr
import com.soneso.stellar.sdk.xdr.SorobanCredentialsXdr
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Selection of the auth digest scheme: the configured and built-in tables, the instance read
 * of the wallet's code, and the once-per-operation preparation both signing pipelines share.
 */
class AuthDigestSchemeSelectionTest {

    private val wallet = "CDLZFC3SYJYDZT7K67VZ75HPJVIEUVNIXF47ZG2FB2RMQQVU2HHGCYSC"
    private val otherContract = "CAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAD2KM"
    private val verifier = "CB26VN37RCVNTHJZDEPK6IRO2MMTS3Z2IEO5JD5BINY2OOJ5KKJG7NKY"

    private val v08Hash = "c514d35ea67eaea9bfc1fb3327854c0c06111bc1ddebd4fe217c504c1004b9e1"
    private val ownHash = "1f".repeat(32)
    private val unmappedHash = "0".repeat(64)

    private fun config(authDigestSchemes: Map<String, AuthDigestScheme> = emptyMap()) = OZSmartAccountConfig(
        rpcUrl = "https://soroban-testnet.stellar.org",
        networkPassphrase = Network.TESTNET.networkPassphrase,
        accountWasmHash = "a" + "0".repeat(63),
        webauthnVerifierAddress = verifier,
        authDigestSchemes = authDigestSchemes
    )

    private fun wasm(hex: String): ContractExecutableXdr = ContractExecutableXdr.WasmHash(HashXdr(Util.hexToBytes(hex)))

    /** A scripted RPC that records every method it is asked for. */
    private class RecordingRpc(private val responses: List<String>) {
        val methods = mutableListOf<String>()
        val bodies = mutableListOf<String>()

        val server: SorobanServer = SorobanServer(
            "https://soroban-testnet.stellar.org",
            HttpClient(MockEngine { request ->
                val body = request.body.toByteArray().decodeToString()
                bodies.add(body)
                methods.add(rpcRequestMethod(body))
                val response = responses.getOrNull(methods.size - 1)
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
    }

    private fun kit(rpc: RecordingRpc, authDigestSchemes: Map<String, AuthDigestScheme> = emptyMap()) =
        OZSmartAccountKit.createWithServer(config(authDigestSchemes), rpc.server)

    private val rpcErrorJson = """{"jsonrpc":"2.0","id":"test-id","error":{"code":-32603,"message":"instance read failed"}}"""

    private suspend fun foreignEntry(): SorobanAuthorizationEntryXdr =
        buildAuthEntry(Address(KeyPair.random().getAccountId()).toString())

    // MARK: - Tables

    @Test
    fun authDigestSchemeFor_builtInV08Build_isPreimage() {
        assertEquals(AuthDigestScheme.PREIMAGE_V1, config().authDigestSchemeFor(v08Hash))
        assertEquals(AuthDigestScheme.PREIMAGE_V1, config().authDigestSchemeFor(v08Hash.uppercase()))
    }

    @Test
    fun authDigestSchemeFor_unmappedHash_isLegacy() {
        assertEquals(AuthDigestScheme.LEGACY_CONCAT, config().authDigestSchemeFor(unmappedHash))
        assertEquals(AuthDigestScheme.LEGACY_CONCAT, config().authDigestSchemeFor(ownHash))
    }

    @Test
    fun authDigestSchemeFor_configuredOwnBuild_isPreimageInAnyCase() {
        val config = config(mapOf(ownHash.uppercase() to AuthDigestScheme.PREIMAGE_V1))
        assertEquals(AuthDigestScheme.PREIMAGE_V1, config.authDigestSchemeFor(ownHash))
        assertEquals(AuthDigestScheme.PREIMAGE_V1, config.authDigestSchemeFor(ownHash.uppercase()))
        assertEquals(AuthDigestScheme.PREIMAGE_V1, config.authDigestSchemeFor(v08Hash), "The built-in entry stays")
    }

    @Test
    fun authDigestSchemeFor_configuredOverrideOfBuiltIn_isLegacy() {
        val config = config(mapOf(v08Hash to AuthDigestScheme.LEGACY_CONCAT))
        assertEquals(AuthDigestScheme.LEGACY_CONCAT, config.authDigestSchemeFor(v08Hash))
    }

    @Test
    fun config_invalidAuthDigestSchemesKey_throwsAtConstruction() {
        for (key in listOf("", "abc", "0".repeat(63), "0".repeat(65), "g".repeat(64), "0x" + "0".repeat(62))) {
            val ex = assertFailsWith<ConfigurationException.InvalidConfig> {
                config(mapOf(key to AuthDigestScheme.PREIMAGE_V1))
            }
            assertTrue(ex.message.contains("authDigestSchemes key"), "Message names the option: ${ex.message}")
            assertTrue(ex.message.endsWith("got: $key"), "Message names the key: ${ex.message}")
        }
    }

    @Test
    fun config_keysDifferingOnlyInCase_mustAgree() {
        val ex = assertFailsWith<ConfigurationException.InvalidConfig> {
            config(mapOf(ownHash to AuthDigestScheme.PREIMAGE_V1, ownHash.uppercase() to AuthDigestScheme.LEGACY_CONCAT))
        }
        assertTrue(ex.message.contains(ownHash), "Message names the hash: ${ex.message}")

        val agreeing = config(mapOf(ownHash to AuthDigestScheme.PREIMAGE_V1, ownHash.uppercase() to AuthDigestScheme.PREIMAGE_V1))
        assertEquals(AuthDigestScheme.PREIMAGE_V1, agreeing.authDigestSchemeFor(ownHash))
    }

    @Test
    fun builder_authDigestSchemes_reachesTheConfig() {
        val schemes = mapOf(ownHash to AuthDigestScheme.PREIMAGE_V1)
        val config = OZSmartAccountConfig.Builder(
            rpcUrl = "https://soroban-testnet.stellar.org",
            networkPassphrase = Network.TESTNET.networkPassphrase,
            accountWasmHash = "a" + "0".repeat(63),
            webauthnVerifierAddress = verifier
        ).authDigestSchemes(schemes).build()

        assertEquals(schemes, config.authDigestSchemes)
        assertEquals(AuthDigestScheme.PREIMAGE_V1, config.authDigestSchemeFor(ownHash))
    }

    // MARK: - Instance read

    @Test
    fun resolveAuthDigestScheme_readsThePersistentInstanceOfTheWallet() = runTest {
        val rpc = RecordingRpc(listOf(contractInstanceEntriesResponseJson(wallet, wasm(v08Hash), latestLedger = 4242)))

        val read = kit(rpc).resolveAuthDigestScheme(wallet)

        assertEquals(AuthDigestScheme.PREIMAGE_V1, read.scheme)
        assertEquals(4242L, read.latestLedger)
        assertEquals(listOf("getLedgerEntries"), rpc.methods)
        val keys = Json.parseToJsonElement(rpc.bodies.single()).jsonObject["params"]!!.jsonObject["keys"]!!.jsonArray
        assertEquals(1, keys.size)
        val key = LedgerKeyXdr.fromXdrBase64(keys[0].jsonPrimitive.content) as LedgerKeyXdr.ContractData
        assertEquals(wallet, Address.fromSCAddress(key.value.contract).toString())
        assertEquals(Scv.toLedgerKeyContractInstance(), key.value.key)
        assertEquals(ContractDataDurabilityXdr.PERSISTENT, key.value.durability)
    }

    @Test
    fun resolveAuthDigestScheme_configuredHash_followsTheConfig() = runTest {
        val preimage = RecordingRpc(listOf(contractInstanceEntriesResponseJson(wallet, wasm(ownHash))))
        assertEquals(
            AuthDigestScheme.PREIMAGE_V1,
            kit(preimage, mapOf(ownHash to AuthDigestScheme.PREIMAGE_V1)).resolveAuthDigestScheme(wallet).scheme
        )

        val legacy = RecordingRpc(listOf(contractInstanceEntriesResponseJson(wallet, wasm(v08Hash))))
        assertEquals(
            AuthDigestScheme.LEGACY_CONCAT,
            kit(legacy, mapOf(v08Hash to AuthDigestScheme.LEGACY_CONCAT)).resolveAuthDigestScheme(wallet).scheme
        )
    }

    @Test
    fun resolveAuthDigestScheme_unmappedHash_isLegacy() = runTest {
        val rpc = RecordingRpc(listOf(contractInstanceEntriesResponseJson(wallet, wasm(unmappedHash))))
        assertEquals(AuthDigestScheme.LEGACY_CONCAT, kit(rpc).resolveAuthDigestScheme(wallet).scheme)
    }

    @Test
    fun resolveAuthDigestScheme_externalReferenceExecutable_isLegacy() = runTest {
        val externalRef = ContractExecutableXdr.ExternalRef(
            ContractExecutableExternalRefXdr(Address(otherContract).toSCAddress(), "account-v1".encodeToByteArray())
        )
        val rpc = RecordingRpc(listOf(contractInstanceEntriesResponseJson(wallet, externalRef)))
        assertEquals(AuthDigestScheme.LEGACY_CONCAT, kit(rpc, mapOf(v08Hash to AuthDigestScheme.PREIMAGE_V1)).resolveAuthDigestScheme(wallet).scheme)
    }

    @Test
    fun resolveAuthDigestScheme_stellarAssetExecutable_isLegacy() = runTest {
        val rpc = RecordingRpc(listOf(contractInstanceEntriesResponseJson(wallet, ContractExecutableXdr.Void)))
        assertEquals(AuthDigestScheme.LEGACY_CONCAT, kit(rpc).resolveAuthDigestScheme(wallet).scheme)
    }

    @Test
    fun resolveAuthDigestScheme_missingInstanceEntry_isLegacyAtTheReportedLedger() = runTest {
        val rpc = RecordingRpc(listOf(emptyLedgerEntriesResponseJson()))
        val read = kit(rpc).resolveAuthDigestScheme(wallet)
        assertEquals(AuthDigestScheme.LEGACY_CONCAT, read.scheme)
        assertEquals(100L, read.latestLedger)
    }

    @Test
    fun resolveAuthDigestScheme_responseWithoutEntries_isLegacyAtTheReportedLedger() = runTest {
        val rpc = RecordingRpc(listOf("""{"jsonrpc":"2.0","id":"test-id","result":{"latestLedger":321}}"""))
        val read = kit(rpc).resolveAuthDigestScheme(wallet)
        assertEquals(AuthDigestScheme.LEGACY_CONCAT, read.scheme)
        assertEquals(321L, read.latestLedger)
    }

    @Test
    fun resolveAuthDigestScheme_contractDataThatIsNoInstance_throwsSigningFailed() = runTest {
        val dataEntry = LedgerEntryDataXdr.ContractData(
            ContractDataEntryXdr(
                ext = ExtensionPointXdr.Void,
                contract = Address(wallet).toSCAddress(),
                key = Scv.toLedgerKeyContractInstance(),
                durability = ContractDataDurabilityXdr.PERSISTENT,
                `val` = Scv.toUint32(7u)
            )
        )
        val rpc = RecordingRpc(listOf(ledgerEntriesResponseJson(dataEntry.toXdrBase64())))
        val ex = assertFailsWith<TransactionException.SigningFailed> {
            kit(rpc).resolveAuthDigestScheme(wallet)
        }
        assertTrue(ex.message.contains(wallet), "Message names the wallet: ${ex.message}")
        assertTrue(ex.message.contains("not a contract instance (CONTRACT_DATA)"), ex.message)
    }

    @Test
    fun resolveAuthDigestScheme_entryThatIsNoContractInstance_throwsSigningFailed() = runTest {
        val accountEntry = buildAccountEntryXdr(KeyPair.random()).toXdrBase64()
        val rpc = RecordingRpc(listOf(ledgerEntriesResponseJson(accountEntry)))
        val ex = assertFailsWith<TransactionException.SigningFailed> {
            kit(rpc).resolveAuthDigestScheme(wallet)
        }
        assertTrue(ex.message.contains(wallet), "Message names the wallet: ${ex.message}")
        assertTrue(ex.message.contains("not a contract instance"), ex.message)
    }

    @Test
    fun resolveAuthDigestScheme_undecodableEntry_throwsSigningFailed() = runTest {
        val rpc = RecordingRpc(listOf(ledgerEntriesResponseJson("AAAA")))
        val ex = assertFailsWith<TransactionException.SigningFailed> {
            kit(rpc).resolveAuthDigestScheme(wallet)
        }
        assertTrue(ex.message.contains(wallet), "Message names the wallet: ${ex.message}")
    }

    @Test
    fun resolveAuthDigestScheme_rpcError_propagates() = runTest {
        val rpc = RecordingRpc(listOf(rpcErrorJson))
        assertFailsWith<SorobanRpcException> { kit(rpc).resolveAuthDigestScheme(wallet) }
    }

    @Test
    fun resolveAuthDigestScheme_upgradeBetweenOperations_changesTheScheme() = runTest {
        val rpc = RecordingRpc(
            listOf(
                contractInstanceEntriesResponseJson(wallet, wasm(unmappedHash)),
                contractInstanceEntriesResponseJson(wallet, wasm(v08Hash))
            )
        )
        val kit = kit(rpc)
        assertEquals(AuthDigestScheme.LEGACY_CONCAT, kit.resolveAuthDigestScheme(wallet).scheme)
        assertEquals(AuthDigestScheme.PREIMAGE_V1, kit.resolveAuthDigestScheme(wallet).scheme)
    }

    // MARK: - Once-per-operation preparation

    @Test
    fun prepareSigningOperation_walletEntries_readTheInstanceOnceAndExpireFromItsLedger() = runTest {
        val rpc = RecordingRpc(listOf(contractInstanceEntriesResponseJson(wallet, wasm(v08Hash), latestLedger = 5000)))
        val kit = kit(rpc)

        val state = kit.prepareSigningOperation(
            wallet,
            listOf(foreignEntry(), buildAuthEntry(wallet), buildAuthEntry(wallet))
        )

        assertEquals(AuthDigestScheme.PREIMAGE_V1, state.authDigestScheme)
        assertEquals((5000 + kit.config.signatureExpirationLedgers).toUInt(), state.expirationLedger)
        assertEquals(listOf("getLedgerEntries"), rpc.methods, "One instance read and no getLatestLedger")
    }

    @Test
    fun prepareSigningOperation_noWalletEntry_readsTheLatestLedgerOnly() = runTest {
        val rpc = RecordingRpc(listOf(latestLedgerResponseJson(7000)))
        val kit = kit(rpc)
        val sourceAccountEntry = buildAuthEntry(wallet).copy(credentials = SorobanCredentialsXdr.Void)

        val state = kit.prepareSigningOperation(wallet, listOf(foreignEntry(), sourceAccountEntry, buildAuthEntry(otherContract)))

        assertNull(state.authDigestScheme)
        assertEquals((7000 + kit.config.signatureExpirationLedgers).toUInt(), state.expirationLedger)
        assertEquals(listOf("getLatestLedger"), rpc.methods, "Zero instance reads without a smart account entry")
    }

    @Test
    fun signingOperationState_schemeForWalletEntry_returnsTheReadScheme() {
        val state = SigningOperationState(AuthDigestScheme.PREIMAGE_V1, 10u)
        assertEquals(AuthDigestScheme.PREIMAGE_V1, state.schemeForWalletEntry(wallet))
    }

    @Test
    fun signingOperationState_withoutScheme_refusesAWalletEntryNamingTheWallet() {
        val state = SigningOperationState(authDigestScheme = null, expirationLedger = 10u)
        val ex = assertFailsWith<IllegalStateException> { state.schemeForWalletEntry(wallet) }
        assertEquals("No digest scheme was resolved for the auth entry of $wallet", ex.message)
    }

    @Test
    fun prepareSigningOperation_instanceReadFails_propagatesTheRpcError() = runTest {
        val rpc = RecordingRpc(listOf(rpcErrorJson))
        assertFailsWith<SorobanRpcException> {
            kit(rpc).prepareSigningOperation(wallet, listOf(buildAuthEntry(wallet)))
        }
        assertEquals(listOf("getLedgerEntries"), rpc.methods)
    }

    @Test
    fun prepareSigningOperation_configuredExpiration_isAddedToTheReadLedger() = runTest {
        val rpc = RecordingRpc(listOf(contractInstanceEntriesResponseJson(wallet, latestLedger = 900)))
        val kit = OZSmartAccountKit.createWithServer(config().copy(signatureExpirationLedgers = 17), rpc.server)

        val state = kit.prepareSigningOperation(wallet, listOf(buildAuthEntry(wallet)))

        assertEquals(917u, state.expirationLedger)
        assertEquals(AuthDigestScheme.LEGACY_CONCAT, state.authDigestScheme)
    }

    @Test
    fun knownAuthDigestSchemes_mapsOnlyTheV08Build() {
        assertEquals(mapOf(v08Hash to AuthDigestScheme.PREIMAGE_V1), OZConstants.KNOWN_AUTH_DIGEST_SCHEMES)
    }
}
