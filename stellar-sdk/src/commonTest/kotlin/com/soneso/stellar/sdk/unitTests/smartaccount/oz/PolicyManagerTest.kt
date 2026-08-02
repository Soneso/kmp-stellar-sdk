//
//  PolicyManagerTest.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.unitTests.smartaccount.oz

import com.soneso.stellar.sdk.unitTests.smartaccount.assertKeysAreInHostOrder
import com.soneso.stellar.sdk.unitTests.smartaccount.buildAccountEntryXdr
import com.soneso.stellar.sdk.unitTests.smartaccount.buildMinimalSorobanData
import com.soneso.stellar.sdk.unitTests.smartaccount.encodeToXdrHex
import com.soneso.stellar.sdk.unitTests.smartaccount.extractSymbolName
import com.soneso.stellar.sdk.unitTests.smartaccount.getTransactionSuccessResponseJson
import com.soneso.stellar.sdk.unitTests.smartaccount.latestLedgerResponseJson
import com.soneso.stellar.sdk.unitTests.smartaccount.ledgerEntriesResponseJson
import com.soneso.stellar.sdk.unitTests.smartaccount.sendTransactionPendingResponseJson
import com.soneso.stellar.sdk.unitTests.smartaccount.simulateCountResponseJson
import com.soneso.stellar.sdk.unitTests.smartaccount.simulateWithAuthResponseJson
import com.soneso.stellar.sdk.Address
import com.soneso.stellar.sdk.InvokeHostFunctionOperation
import com.soneso.stellar.sdk.KeyPair
import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.StrKey
import com.soneso.stellar.sdk.Transaction
import com.soneso.stellar.sdk.Util
import com.soneso.stellar.sdk.rpc.SorobanServer
import com.soneso.stellar.sdk.scval.Scv
import com.soneso.stellar.sdk.smartaccount.core.DelegatedSigner
import com.soneso.stellar.sdk.smartaccount.core.ExternalSigner
import com.soneso.stellar.sdk.smartaccount.core.SmartAccountSigner
import com.soneso.stellar.sdk.smartaccount.core.ValidationException
import com.soneso.stellar.sdk.smartaccount.oz.OZSmartAccountConfig
import com.soneso.stellar.sdk.smartaccount.oz.OZSmartAccountKit
import com.soneso.stellar.sdk.smartaccount.oz.PolicyInstallParams
import com.soneso.stellar.sdk.smartaccount.oz.OZPolicyManager
import com.soneso.stellar.sdk.smartaccount.oz.SelectedSigner
import com.ionspin.kotlin.bignum.integer.BigInteger
import com.soneso.stellar.sdk.xdr.HostFunctionXdr
import com.soneso.stellar.sdk.xdr.Int64Xdr
import com.soneso.stellar.sdk.xdr.InvokeContractArgsXdr
import com.soneso.stellar.sdk.xdr.SCMapEntryXdr
import com.soneso.stellar.sdk.xdr.SCMapXdr
import com.soneso.stellar.sdk.xdr.SCSymbolXdr
import com.soneso.stellar.sdk.xdr.SCValTypeXdr
import com.soneso.stellar.sdk.xdr.SCValXdr
import com.soneso.stellar.sdk.xdr.SorobanAddressCredentialsXdr
import com.soneso.stellar.sdk.xdr.SorobanAuthorizationEntryXdr
import com.soneso.stellar.sdk.xdr.SorobanAuthorizedFunctionXdr
import com.soneso.stellar.sdk.xdr.SorobanAuthorizedInvocationXdr
import com.soneso.stellar.sdk.xdr.SorobanCredentialsXdr
import com.soneso.stellar.sdk.xdr.Uint32Xdr
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
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// ---------------------------------------------------------------------------
// Scripted RPC mock server
// ---------------------------------------------------------------------------

/**
 * Creates a [SorobanServer] that answers requests strictly in the order of [responses] and
 * fails once the script is exhausted, so a flow that issues more RPC calls than the test
 * expects fails loudly. Every request body is handed to [onRequest] with its zero-based index.
 */
private fun policyMockServer(
    responses: List<String>,
    onRequest: ((index: Int, body: String) -> Unit)? = null
): SorobanServer {
    var requestIndex = 0
    val mockEngine = MockEngine { request ->
        val index = requestIndex++
        if (onRequest != null) {
            onRequest(index, request.body.toByteArray().decodeToString())
        }
        val body = responses.getOrNull(index)
            ?: error("Unexpected RPC request at index $index; script holds ${responses.size} responses")
        respond(
            content = ByteReadChannel(body),
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, "application/json")
        )
    }
    val httpClient = HttpClient(mockEngine) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
                encodeDefaults = false
            })
        }
    }
    return SorobanServer("https://soroban-testnet.stellar.org", httpClient)
}

/**
 * Extracts the contract invocation carried by a `simulateTransaction` or `sendTransaction`
 * JSON-RPC request body.
 */
private fun policyInvocation(requestBody: String): InvokeContractArgsXdr {
    val root = Json.parseToJsonElement(requestBody).jsonObject
    val envelopeBase64 = root["params"]!!.jsonObject["transaction"]!!.jsonPrimitive.content
    val transaction = Transaction.fromEnvelopeXdr(envelopeBase64, Network.TESTNET)
    val operation = transaction.operations
        .first { it is InvokeHostFunctionOperation } as InvokeHostFunctionOperation
    return (operation.hostFunction as HostFunctionXdr.InvokeContract).value
}

/** Extracts the authorization entries carried by a `sendTransaction` request body. */
private fun policySubmittedAuth(requestBody: String): List<SorobanAuthorizationEntryXdr> {
    val root = Json.parseToJsonElement(requestBody).jsonObject
    val envelopeBase64 = root["params"]!!.jsonObject["transaction"]!!.jsonPrimitive.content
    val transaction = Transaction.fromEnvelopeXdr(envelopeBase64, Network.TESTNET)
    val operation = transaction.operations
        .first { it is InvokeHostFunctionOperation } as InvokeHostFunctionOperation
    return operation.auth
}

/**
 * Unit tests for [PolicyInstallParams] ScVal encoding and for the [OZPolicyManager] flows
 * that install and uninstall policies on a context rule.
 *
 * The encoding tests verify that the three policy types (SimpleThreshold, WeightedThreshold,
 * SpendingLimit) produce ScVal output compatible with the Soroban smart account contracts,
 * including their input validation and error cases.
 *
 * The manager tests drive `add_policy` / `remove_policy` through a scripted Soroban RPC
 * mock and pin the exact contract invocation that reaches the network, the resolution of a
 * policy address to its on-chain policy ID, and the routing between the single-signer and
 * the multi-signer submission pipelines.
 */
class PolicyManagerTest {

    // MARK: - SimpleThreshold Tests

    @Test
    fun testSimpleThreshold_createsMapWithThresholdKey() {
        val params = PolicyInstallParams.SimpleThreshold(threshold = 2u)
        val scVal = params.toScVal()

        // Verify it produces a Map
        assertIs<SCValXdr.Map>(scVal)

        val entries = extractMapEntries(scVal)
        assertEquals(1, entries.size, "SimpleThreshold map must have exactly 1 entry")

        // Verify the key is "threshold"
        val key = entries[0].key
        assertIs<SCValXdr.Sym>(key)
        assertEquals("threshold", (key as SCValXdr.Sym).value.value)

        // Verify the value is U32(2)
        val value = entries[0].`val`
        assertIs<SCValXdr.U32>(value)
        assertEquals(2u, (value as SCValXdr.U32).value.value)
    }

    @Test
    fun testSimpleThreshold_thresholdOfOne() {
        val params = PolicyInstallParams.SimpleThreshold(threshold = 1u)
        val scVal = params.toScVal()

        val entries = extractMapEntries(scVal)
        val value = entries[0].`val`
        assertIs<SCValXdr.U32>(value)
        assertEquals(1u, (value as SCValXdr.U32).value.value)
    }

    @Test
    fun testSimpleThreshold_largeThresholdValue() {
        val params = PolicyInstallParams.SimpleThreshold(threshold = UInt.MAX_VALUE)
        val scVal = params.toScVal()

        val entries = extractMapEntries(scVal)
        val value = entries[0].`val`
        assertIs<SCValXdr.U32>(value)
        assertEquals(UInt.MAX_VALUE, (value as SCValXdr.U32).value.value)
    }

    @Test
    fun testSimpleThreshold_deterministicXdrEncoding() {
        val params1 = PolicyInstallParams.SimpleThreshold(threshold = 5u)
        val params2 = PolicyInstallParams.SimpleThreshold(threshold = 5u)

        val xdr1 = encodeToXdrHex(params1.toScVal())
        val xdr2 = encodeToXdrHex(params2.toScVal())

        assertEquals(xdr1, xdr2, "Identical SimpleThreshold params must produce identical XDR")
    }

    @Test
    fun testSimpleThreshold_differentThresholdsDifferentXdr() {
        val params1 = PolicyInstallParams.SimpleThreshold(threshold = 2u)
        val params2 = PolicyInstallParams.SimpleThreshold(threshold = 3u)

        val xdr1 = encodeToXdrHex(params1.toScVal())
        val xdr2 = encodeToXdrHex(params2.toScVal())

        assertTrue(xdr1 != xdr2, "Different threshold values must produce different XDR")
    }

    // MARK: - WeightedThreshold Tests

    @Test
    fun testWeightedThreshold_createsMapWithCorrectKeys() {
        val signer = DelegatedSigner(
            address = "GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7"
        )
        val params = PolicyInstallParams.WeightedThreshold(
            signerWeights = mapOf(signer to 50u),
            threshold = 100u
        )
        val scVal = params.toScVal()

        assertIs<SCValXdr.Map>(scVal)

        val entries = extractMapEntries(scVal)
        assertEquals(2, entries.size, "WeightedThreshold map must have exactly 2 entries")

        // First key must be "signer_weights" (alphabetically before "threshold")
        assertEquals("signer_weights", extractSymbolName(entries[0].key))

        // Second key must be "threshold"
        assertEquals("threshold", extractSymbolName(entries[1].key))
    }

    @Test
    fun testWeightedThreshold_thresholdValueIsCorrect() {
        val signer = DelegatedSigner(
            address = "GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7"
        )
        val params = PolicyInstallParams.WeightedThreshold(
            signerWeights = mapOf(signer to 50u),
            threshold = 100u
        )
        val scVal = params.toScVal()

        val entries = extractMapEntries(scVal)
        val thresholdEntry = entries.first { extractSymbolName(it.key) == "threshold" }
        val thresholdValue = thresholdEntry.`val`
        assertIs<SCValXdr.U32>(thresholdValue)
        assertEquals(100u, (thresholdValue as SCValXdr.U32).value.value)
    }

    @Test
    fun testWeightedThreshold_signerWeightsInnerMapContainsCorrectEntries() {
        val signer1 = DelegatedSigner(
            address = "GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7"
        )
        val signer2 = DelegatedSigner(
            address = "GBGWONUYEPTSADFMLRQSPRAPTWMGX5PMQXXHGSBVRF2KLUNVZT57SLVW"
        )
        val params = PolicyInstallParams.WeightedThreshold(
            signerWeights = mapOf(
                signer1 to 60u,
                signer2 to 40u
            ),
            threshold = 100u
        )
        val scVal = params.toScVal()

        val entries = extractMapEntries(scVal)
        val signerWeightsEntry = entries.first { extractSymbolName(it.key) == "signer_weights" }
        val innerMap = signerWeightsEntry.`val`
        assertIs<SCValXdr.Map>(innerMap)

        val innerEntries = extractMapEntries(innerMap)
        assertEquals(2, innerEntries.size, "Inner signer weights map must have 2 entries")

        // Each value must be U32
        for (entry in innerEntries) {
            assertIs<SCValXdr.U32>(entry.`val`)
        }

        // Verify the weight values are present (order depends on XDR key sorting)
        val weights = innerEntries.map { (it.`val` as SCValXdr.U32).value.value }.toSet()
        assertTrue(60u in weights, "Weight 60 must be present")
        assertTrue(40u in weights, "Weight 40 must be present")
    }

    @Test
    fun testWeightedThreshold_signerWeightsAreSortedInHostOrder() {
        val signer1 = DelegatedSigner(
            address = "GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7"
        )
        val signer2 = DelegatedSigner(
            address = "GBGWONUYEPTSADFMLRQSPRAPTWMGX5PMQXXHGSBVRF2KLUNVZT57SLVW"
        )
        val signer3 = DelegatedSigner(
            address = "GB33CUURS5XLLECMLSE2EMMDJBMZSVF27BW6PLS53OFTJMP46CZH3CVG"
        )

        // Insert in reverse order to verify sorting is applied
        val params = PolicyInstallParams.WeightedThreshold(
            signerWeights = mapOf(
                signer3 to 20u,
                signer1 to 50u,
                signer2 to 30u
            ),
            threshold = 100u
        )
        val scVal = params.toScVal()

        val entries = extractMapEntries(scVal)
        val signerWeightsEntry = entries.first { extractSymbolName(it.key) == "signer_weights" }
        val innerEntries = extractMapEntries(signerWeightsEntry.`val`)

        assertEquals(3, innerEntries.size)

        // Verify keys are in the host's ScMap key order
        assertKeysAreInHostOrder(innerEntries)
    }

    @Test
    fun testWeightedThreshold_withExternalSigners() {
        val signer = ExternalSigner(
            verifierAddress = "CDLZFC3SYJYDZT7K67VZ75HPJVIEUVNIXF47ZG2FB2RMQQVU2HHGCYSC",
            keyData = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        )
        val params = PolicyInstallParams.WeightedThreshold(
            signerWeights = mapOf(signer to 75u),
            threshold = 75u
        )
        val scVal = params.toScVal()

        val entries = extractMapEntries(scVal)
        assertEquals(2, entries.size)
        assertEquals("signer_weights", extractSymbolName(entries[0].key))
        assertEquals("threshold", extractSymbolName(entries[1].key))

        val innerEntries = extractMapEntries(entries[0].`val`)
        assertEquals(1, innerEntries.size)

        // The key should be the signer's ScVal representation (a Vec for ExternalSigner)
        val signerKey = innerEntries[0].key
        assertIs<SCValXdr.Vec>(signerKey)

        // The value is the weight
        val weightVal = innerEntries[0].`val`
        assertIs<SCValXdr.U32>(weightVal)
        assertEquals(75u, (weightVal as SCValXdr.U32).value.value)
    }

    @Test
    fun testWeightedThreshold_mixedDelegatedAndExternalSigners() {
        val delegatedSigner = DelegatedSigner(
            address = "GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7"
        )
        val externalSigner = ExternalSigner(
            verifierAddress = "CDLZFC3SYJYDZT7K67VZ75HPJVIEUVNIXF47ZG2FB2RMQQVU2HHGCYSC",
            keyData = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        )
        val params = PolicyInstallParams.WeightedThreshold(
            signerWeights = mapOf(
                delegatedSigner to 60u,
                externalSigner to 40u
            ),
            threshold = 100u
        )
        val scVal = params.toScVal()

        val entries = extractMapEntries(scVal)
        val innerEntries = extractMapEntries(entries[0].`val`)
        assertEquals(2, innerEntries.size)

        // Both signers should be Vec-typed keys (both DelegatedSigner and ExternalSigner produce Vecs)
        for (entry in innerEntries) {
            assertIs<SCValXdr.Vec>(entry.key)
        }

        // Verify keys are in the host's ScMap key order
        assertKeysAreInHostOrder(innerEntries)
    }

    @Test
    fun testWeightedThreshold_emptySignerWeightsThrows() {
        val params = PolicyInstallParams.WeightedThreshold(
            signerWeights = emptyMap(),
            threshold = 100u
        )

        val exception = assertFailsWith<ValidationException.InvalidInput> {
            params.toScVal()
        }
        assertTrue(
            exception.message!!.contains("at least one signer"),
            "Error message should mention at least one signer requirement"
        )
    }

    @Test
    fun testWeightedThreshold_deterministicRegardlessOfInsertionOrder() {
        val signer1 = DelegatedSigner(
            address = "GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7"
        )
        val signer2 = DelegatedSigner(
            address = "GBGWONUYEPTSADFMLRQSPRAPTWMGX5PMQXXHGSBVRF2KLUNVZT57SLVW"
        )

        val paramsA = PolicyInstallParams.WeightedThreshold(
            signerWeights = mapOf(signer1 to 50u, signer2 to 30u),
            threshold = 80u
        )
        val paramsB = PolicyInstallParams.WeightedThreshold(
            signerWeights = mapOf(signer2 to 30u, signer1 to 50u),
            threshold = 80u
        )

        val xdrA = encodeToXdrHex(paramsA.toScVal())
        val xdrB = encodeToXdrHex(paramsB.toScVal())

        assertEquals(
            xdrA, xdrB,
            "Same signers in different order must produce identical XDR"
        )
    }

    // MARK: - SpendingLimit Tests

    @Test
    fun testSpendingLimit_createsMapWithCorrectKeys() {
        val params = PolicyInstallParams.SpendingLimit(
            spendingLimit = BigInteger.fromLong(10_000_000L), // 1 XLM
            periodLedgers = 17280u        // ~1 day
        )
        val scVal = params.toScVal()

        assertIs<SCValXdr.Map>(scVal)

        val entries = extractMapEntries(scVal)
        assertEquals(2, entries.size, "SpendingLimit map must have exactly 2 entries")

        // Alphabetical order: "period_ledgers" before "spending_limit"
        assertEquals("period_ledgers", extractSymbolName(entries[0].key))
        assertEquals("spending_limit", extractSymbolName(entries[1].key))
    }

    @Test
    fun testSpendingLimit_periodLedgersIsU32() {
        val params = PolicyInstallParams.SpendingLimit(
            spendingLimit = BigInteger.fromLong(10_000_000L),
            periodLedgers = 17280u
        )
        val scVal = params.toScVal()

        val entries = extractMapEntries(scVal)
        val periodEntry = entries.first { extractSymbolName(it.key) == "period_ledgers" }

        val periodValue = periodEntry.`val`
        assertIs<SCValXdr.U32>(periodValue)
        assertEquals(17280u, (periodValue as SCValXdr.U32).value.value)
    }

    @Test
    fun testSpendingLimit_spendingLimitIsI128() {
        val params = PolicyInstallParams.SpendingLimit(
            spendingLimit = BigInteger.fromLong(10_000_000L), // 1 XLM in stroops
            periodLedgers = 17280u
        )
        val scVal = params.toScVal()

        val entries = extractMapEntries(scVal)
        val limitEntry = entries.first { extractSymbolName(it.key) == "spending_limit" }

        val limitValue = limitEntry.`val`
        assertIs<SCValXdr.I128>(limitValue)

        val parts = (limitValue as SCValXdr.I128).value
        assertEquals(0L, parts.hi.value, "High part must be 0 for positive values within Long range")
        assertEquals(10_000_000uL, parts.lo.value, "Low part must match the stroops value")
    }

    @Test
    fun testSpendingLimit_largeI128Value() {
        // Test with a large stroops value (e.g. 1 billion XLM = 10^16 stroops)
        val largeStroops = BigInteger.fromLong(10_000_000_000_000_000L) // 1 billion XLM
        val params = PolicyInstallParams.SpendingLimit(
            spendingLimit = largeStroops,
            periodLedgers = Util.LEDGERS_PER_DAY.toUInt()
        )
        val scVal = params.toScVal()

        val entries = extractMapEntries(scVal)
        val limitEntry = entries.first { extractSymbolName(it.key) == "spending_limit" }
        val limitValue = limitEntry.`val`
        assertIs<SCValXdr.I128>(limitValue)

        val parts = (limitValue as SCValXdr.I128).value
        assertEquals(0L, parts.hi.value)
        assertEquals(largeStroops.ulongValue(), parts.lo.value)
    }

    @Test
    fun testSpendingLimit_zeroSpendingLimitThrows() {
        val params = PolicyInstallParams.SpendingLimit(
            spendingLimit = BigInteger.ZERO,
            periodLedgers = 17280u
        )

        val exception = assertFailsWith<ValidationException.InvalidInput> {
            params.toScVal()
        }
        assertTrue(
            exception.message!!.contains("greater than zero"),
            "Error message should mention spending limit must be greater than zero"
        )
    }

    @Test
    fun testSpendingLimit_negativeSpendingLimitThrows() {
        val params = PolicyInstallParams.SpendingLimit(
            spendingLimit = BigInteger.fromLong(-100L),
            periodLedgers = 17280u
        )

        val exception = assertFailsWith<ValidationException.InvalidInput> {
            params.toScVal()
        }
        assertTrue(
            exception.message!!.contains("greater than zero"),
            "Error message should mention spending limit must be greater than zero"
        )
    }

    @Test
    fun testSpendingLimit_zeroPeriodLedgersThrows() {
        val params = PolicyInstallParams.SpendingLimit(
            spendingLimit = BigInteger.fromLong(10_000_000L),
            periodLedgers = 0u
        )

        val exception = assertFailsWith<ValidationException.InvalidInput> {
            params.toScVal()
        }
        assertTrue(
            exception.message!!.contains("greater than zero"),
            "Error message should mention period ledgers must be greater than zero"
        )
    }

    @Test
    fun testSpendingLimit_deterministicXdrEncoding() {
        val params1 = PolicyInstallParams.SpendingLimit(
            spendingLimit = BigInteger.fromLong(50_000_000L),
            periodLedgers = 34560u
        )
        val params2 = PolicyInstallParams.SpendingLimit(
            spendingLimit = BigInteger.fromLong(50_000_000L),
            periodLedgers = 34560u
        )

        val xdr1 = encodeToXdrHex(params1.toScVal())
        val xdr2 = encodeToXdrHex(params2.toScVal())

        assertEquals(xdr1, xdr2, "Identical SpendingLimit params must produce identical XDR")
    }

    @Test
    fun testSpendingLimit_oneLedgerPeriod() {
        val params = PolicyInstallParams.SpendingLimit(
            spendingLimit = BigInteger.ONE,
            periodLedgers = 1u
        )
        val scVal = params.toScVal()

        val entries = extractMapEntries(scVal)

        val periodEntry = entries.first { extractSymbolName(it.key) == "period_ledgers" }
        assertEquals(1u, (periodEntry.`val` as SCValXdr.U32).value.value)

        val limitEntry = entries.first { extractSymbolName(it.key) == "spending_limit" }
        val parts = (limitEntry.`val` as SCValXdr.I128).value
        assertEquals(0L, parts.hi.value)
        assertEquals(1uL, parts.lo.value)
    }

    @Test
    fun testSpendingLimit_maxUIntPeriodLedgers() {
        val params = PolicyInstallParams.SpendingLimit(
            spendingLimit = BigInteger.fromLong(1_000_000L),
            periodLedgers = UInt.MAX_VALUE
        )
        val scVal = params.toScVal()

        val entries = extractMapEntries(scVal)
        val periodEntry = entries.first { extractSymbolName(it.key) == "period_ledgers" }
        assertEquals(UInt.MAX_VALUE, (periodEntry.`val` as SCValXdr.U32).value.value)
    }

    // MARK: - Cross-Policy Type Verification

    @Test
    fun testAllPolicyTypes_produceMapScVal() {
        val simple = PolicyInstallParams.SimpleThreshold(threshold = 2u).toScVal()
        assertIs<SCValXdr.Map>(simple)

        val signer = DelegatedSigner(
            address = "GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7"
        )
        val weighted = PolicyInstallParams.WeightedThreshold(
            signerWeights = mapOf(signer to 50u),
            threshold = 50u
        ).toScVal()
        assertIs<SCValXdr.Map>(weighted)

        val spending = PolicyInstallParams.SpendingLimit(
            spendingLimit = BigInteger.fromLong(10_000_000L),
            periodLedgers = 17280u
        ).toScVal()
        assertIs<SCValXdr.Map>(spending)
    }

    @Test
    fun testAllPolicyTypes_produceDifferentXdr() {
        val signer = DelegatedSigner(
            address = "GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7"
        )

        val simpleXdr = encodeToXdrHex(
            PolicyInstallParams.SimpleThreshold(threshold = 2u).toScVal()
        )
        val weightedXdr = encodeToXdrHex(
            PolicyInstallParams.WeightedThreshold(
                signerWeights = mapOf(signer to 50u),
                threshold = 50u
            ).toScVal()
        )
        val spendingXdr = encodeToXdrHex(
            PolicyInstallParams.SpendingLimit(
                spendingLimit = BigInteger.fromLong(10_000_000L),
                periodLedgers = 17280u
            ).toScVal()
        )

        assertTrue(simpleXdr != weightedXdr, "SimpleThreshold and WeightedThreshold must differ")
        assertTrue(simpleXdr != spendingXdr, "SimpleThreshold and SpendingLimit must differ")
        assertTrue(weightedXdr != spendingXdr, "WeightedThreshold and SpendingLimit must differ")
    }

    // MARK: - amountToStroops Tests

    @Test
    fun testAmountToStroops_oneXlm() {
        val stroops = Util.amountToStroops("1")
        assertEquals(BigInteger.fromLong(10_000_000L), stroops)
    }

    @Test
    fun testAmountToStroops_fractionalAmount() {
        val stroops = Util.amountToStroops("0.5")
        assertEquals(BigInteger.fromLong(5_000_000L), stroops)
    }

    @Test
    fun testAmountToStroops_largeAmount() {
        val stroops = Util.amountToStroops("1000")
        assertEquals(BigInteger.fromLong(10_000_000_000L), stroops)
    }

    @Test
    fun testAmountToStroops_emptyString() {
        assertFailsWith<IllegalArgumentException> {
            Util.amountToStroops("")
        }
    }

    @Test
    fun testAmountToStroops_whitespace() {
        assertFailsWith<IllegalArgumentException> {
            Util.amountToStroops("   ")
        }
    }

    @Test
    fun testAmountToStroops_nonNumeric() {
        assertFailsWith<IllegalArgumentException> {
            Util.amountToStroops("abc")
        }
    }

    @Test
    fun testAmountToStroops_scientificNotation() {
        assertFailsWith<IllegalArgumentException> {
            Util.amountToStroops("1e7")
        }
    }

    @Test
    fun testAmountToStroops_subStroopAmount() {
        // 0.00000001 is less than 1 stroop (0.0000001), rounds to 0
        assertFailsWith<IllegalArgumentException> {
            Util.amountToStroops("0.00000001")
        }
    }

    @Test
    fun testAmountToStroops_decimalPrecision() {
        val stroops = Util.amountToStroops("10.5")
        assertEquals(BigInteger.fromLong(105_000_000L), stroops)
    }

    @Test
    fun testAmountToStroops_maxPrecision() {
        // 0.0000001 = 1 stroop (minimum)
        val stroops = Util.amountToStroops("0.0000001")
        assertEquals(BigInteger.fromLong(1L), stroops)
    }

    // MARK: - stroopsToI128ScVal Tests

    @Test
    fun testStroopsToI128ScVal_basicValue() {
        val scVal = Util.stroopsToI128ScVal(BigInteger.fromLong(10_000_000L))

        assertIs<SCValXdr.I128>(scVal)
        val parts = (scVal as SCValXdr.I128).value
        assertEquals(0L, parts.hi.value)
        assertEquals(10_000_000uL, parts.lo.value)
    }

    @Test
    fun testStroopsToI128ScVal_maxLongValue() {
        val scVal = Util.stroopsToI128ScVal(BigInteger.fromLong(Long.MAX_VALUE))

        assertIs<SCValXdr.I128>(scVal)
        val parts = (scVal as SCValXdr.I128).value
        assertEquals(0L, parts.hi.value)
        assertEquals(Long.MAX_VALUE.toULong(), parts.lo.value)
    }

    // MARK: - Policy Manager Fixtures

    /** The connected smart account contract. */
    private val smartAccount = StrKey.encodeContract(ByteArray(32) { (it + 41).toByte() })

    private val policyA = StrKey.encodeContract(ByteArray(32) { (it + 42).toByte() })
    private val policyB = StrKey.encodeContract(ByteArray(32) { (it + 43).toByte() })

    private val ruleSignerA = DelegatedSigner(StrKey.encodeContract(ByteArray(32) { (it + 51).toByte() }))
    private val ruleSignerB = DelegatedSigner(StrKey.encodeContract(ByteArray(32) { (it + 52).toByte() }))

    private val policyTxHash = "b2831f3b72fab7c4d7d3f6d1e5d1b604f3e2d1c0b9a8f7e6d5c4b3a2f1e0d9c8"

    private fun buildKitConfig(deployer: KeyPair): OZSmartAccountConfig = OZSmartAccountConfig(
        rpcUrl = "https://soroban-testnet.stellar.org",
        networkPassphrase = Network.TESTNET.networkPassphrase,
        accountWasmHash = "a" + "0".repeat(63),
        webauthnVerifierAddress = VERIFIER_A,
        deployerKeypair = deployer
    )

    /**
     * Builds an auth entry whose credentials name [address] and whose root invocation targets
     * [targetContract]. When [address] differs from the connected smart account, the
     * single-signer submit pipeline passes the entry through untouched, so the round-trip
     * needs no passkey.
     */
    private fun authEntryFor(
        address: String,
        targetContract: String = VERIFIER_A
    ): SorobanAuthorizationEntryXdr = SorobanAuthorizationEntryXdr(
        credentials = SorobanCredentialsXdr.Address(
            SorobanAddressCredentialsXdr(
                address = Address(address).toSCAddress(),
                nonce = Int64Xdr(7788L),
                signatureExpirationLedger = Uint32Xdr(0u),
                signature = SCValXdr.Void(SCValTypeXdr.SCV_VOID)
            )
        ),
        rootInvocation = SorobanAuthorizedInvocationXdr(
            function = SorobanAuthorizedFunctionXdr.ContractFn(
                InvokeContractArgsXdr(
                    contractAddress = Address(targetContract).toSCAddress(),
                    functionName = SCSymbolXdr("noop"),
                    args = emptyList()
                )
            ),
            subInvocations = emptyList()
        )
    )

    /**
     * The nine scripted responses of a single-signer submit round-trip: deployer lookup,
     * simulation, latest ledger, the two-call `get_context_rules_count` query (zero rules),
     * deployer re-fetch, re-simulation, submission, and one confirmation poll.
     */
    private fun submitResponses(
        accountXdr: String,
        authEntryXdr: String,
        sorobanDataXdr: String
    ): List<String> = listOf(
        ledgerEntriesResponseJson(accountXdr),
        simulateWithAuthResponseJson(authEntryXdr, sorobanDataXdr),
        latestLedgerResponseJson(),
        ledgerEntriesResponseJson(accountXdr),
        simulateCountResponseJson(SCValXdr.U32(Uint32Xdr(0u)).toXdrBase64(), sorobanDataXdr),
        ledgerEntriesResponseJson(accountXdr),
        simulateWithAuthResponseJson(authEntryXdr, sorobanDataXdr),
        sendTransactionPendingResponseJson(policyTxHash),
        getTransactionSuccessResponseJson(policyTxHash)
    )

    /** Builds a context rule Map ScVal in the on-chain field layout. */
    private fun contextRuleScVal(
        id: UInt,
        name: String,
        policies: List<String>,
        policyIds: List<UInt>
    ): SCValXdr {
        val entries = listOf(
            "context_type" to Scv.toVec(listOf(Scv.toSymbol("Default"))),
            "id" to Scv.toUint32(id),
            "name" to Scv.toString(name),
            "policies" to Scv.toVec(policies.map { Scv.toAddress(Address(it).toSCAddress()) }),
            "policy_ids" to Scv.toVec(policyIds.map { Scv.toUint32(it) }),
            "signer_ids" to Scv.toVec(emptyList()),
            "signers" to Scv.toVec(emptyList()),
            "valid_until" to Scv.toVoid()
        ).map { (key, value) -> SCMapEntryXdr(key = Scv.toSymbol(key), `val` = value) }
        return SCValXdr.Map(SCMapXdr(entries))
    }

    // MARK: - addPolicy

    @Test
    fun testAddPolicy_customInstallParams_submitsAddPolicyInvocation() = runTest {
        val deployer = KeyPair.random()
        val accountXdr = buildAccountEntryXdr(deployer).toXdrBase64()
        val sorobanDataXdr = buildMinimalSorobanData().toXdrBase64()
        val authEntryXdr = authEntryFor(VERIFIER_A).toXdrBase64()

        var simulateBody: String? = null
        var requestCount = 0
        val server = policyMockServer(
            submitResponses(accountXdr, authEntryXdr, sorobanDataXdr)
        ) { index, body ->
            requestCount = index + 1
            if (index == 1) simulateBody = body
        }

        val kit = OZSmartAccountKit.createWithServer(buildKitConfig(deployer), server)
        kit.setConnectedState("test-credential-id", smartAccount)

        val installParams = Scv.toMap(
            linkedMapOf(
                Scv.toSymbol("max_operations") to Scv.toUint32(5u)
            )
        )

        val result = kit.policyManager.addPolicy(
            contextRuleId = 3u,
            policyAddress = policyA,
            installParams = installParams
        )

        assertTrue(result.success, "Round-trip must succeed; error=${result.error}")
        assertEquals(policyTxHash, result.hash)
        assertEquals(9, requestCount, "The round-trip must issue exactly nine RPC calls")

        val invocation = policyInvocation(assertNotNull(simulateBody))
        assertEquals("add_policy", invocation.functionName.value)
        assertEquals(
            smartAccount,
            Address.fromSCAddress(invocation.contractAddress).toString(),
            "The invocation must target the connected smart account"
        )
        assertEquals(3, invocation.args.size, "add_policy takes three arguments")
        assertEquals(3u, Scv.fromUint32(invocation.args[0]))
        assertEquals(policyA, Address.fromSCVal(invocation.args[1]).toString())
        assertEquals(
            installParams.toXdrBase64(),
            invocation.args[2].toXdrBase64(),
            "Custom install parameters must reach the contract unchanged"
        )
    }

    @Test
    fun testAddPolicy_selectedSigners_routesToMultiSignerSubmission() = runTest {
        val deployer = KeyPair.random()
        val walletKeypair = KeyPair.random()
        val accountXdr = buildAccountEntryXdr(deployer).toXdrBase64()
        val sorobanDataXdr = buildMinimalSorobanData().toXdrBase64()

        val walletAddress = walletKeypair.getAccountId()
        val authEntryXdr = authEntryFor(walletAddress, targetContract = smartAccount).toXdrBase64()

        var simulateBody: String? = null
        var sendBody: String? = null
        val server = policyMockServer(
            submitResponses(accountXdr, authEntryXdr, sorobanDataXdr)
        ) { index, body ->
            if (index == 1) simulateBody = body
            if (index == 7) sendBody = body
        }

        val kit = OZSmartAccountKit.createWithServer(buildKitConfig(deployer), server)
        kit.externalSigners.addFromSecret(walletKeypair.getSecretSeed()!!.concatToString())
        kit.setConnectedState("test-credential-id", smartAccount)

        val result = kit.policyManager.addPolicy(
            contextRuleId = 0u,
            policyAddress = policyA,
            installParams = Scv.toVoid(),
            selectedSigners = listOf(SelectedSigner.Wallet(walletAddress))
        )

        assertTrue(result.success, "Multi-signer round-trip must succeed; error=${result.error}")
        assertEquals("add_policy", policyInvocation(assertNotNull(simulateBody)).functionName.value)

        // The multi-signer pipeline stamps an expiration and writes the wallet signature onto
        // the entry; the single-signer pipeline would have passed the entry through untouched.
        val submitted = policySubmittedAuth(assertNotNull(sendBody))
        assertEquals(1, submitted.size)
        val credentials = (submitted[0].credentials as SorobanCredentialsXdr.Address).value
        assertTrue(
            credentials.signatureExpirationLedger.value > 0u,
            "The submitted entry must carry a stamped expiration"
        )
        assertTrue(
            credentials.signature !is SCValXdr.Void,
            "The submitted entry must carry the wallet signature"
        )
    }

    // MARK: - addSimpleThreshold

    @Test
    fun testAddSimpleThreshold_submitsAddPolicyWithThresholdMap() = runTest {
        val deployer = KeyPair.random()
        val accountXdr = buildAccountEntryXdr(deployer).toXdrBase64()
        val sorobanDataXdr = buildMinimalSorobanData().toXdrBase64()
        val authEntryXdr = authEntryFor(VERIFIER_A).toXdrBase64()

        var simulateBody: String? = null
        val server = policyMockServer(
            submitResponses(accountXdr, authEntryXdr, sorobanDataXdr)
        ) { index, body -> if (index == 1) simulateBody = body }

        val kit = OZSmartAccountKit.createWithServer(buildKitConfig(deployer), server)
        kit.setConnectedState("test-credential-id", smartAccount)

        val result = kit.policyManager.addSimpleThreshold(
            contextRuleId = 1u,
            policyAddress = policyB,
            threshold = 2u
        )

        assertTrue(result.success, "Round-trip must succeed; error=${result.error}")

        val invocation = policyInvocation(assertNotNull(simulateBody))
        assertEquals("add_policy", invocation.functionName.value)
        assertEquals(1u, Scv.fromUint32(invocation.args[0]))
        assertEquals(policyB, Address.fromSCVal(invocation.args[1]).toString())

        val installParams = extractMapEntries(invocation.args[2])
        assertEquals(1, installParams.size, "A simple threshold installs a single parameter")
        assertEquals("threshold", extractSymbolName(installParams[0].key))
        assertEquals(2u, (installParams[0].`val` as SCValXdr.U32).value.value)
    }

    @Test
    fun testAddSimpleThreshold_zeroThreshold_throwsBeforeAnyRpcCall() = runTest {
        val deployer = KeyPair.random()
        val server = policyMockServer(emptyList())

        val kit = OZSmartAccountKit.createWithServer(buildKitConfig(deployer), server)
        kit.setConnectedState("test-credential-id", smartAccount)

        val exception = assertFailsWith<ValidationException.InvalidInput> {
            kit.policyManager.addSimpleThreshold(
                contextRuleId = 0u,
                policyAddress = policyA,
                threshold = 0u
            )
        }
        assertTrue(
            exception.message.contains("greater than zero"),
            "The encoder must reject a zero threshold; got: ${exception.message}"
        )
    }

    // MARK: - addWeightedThreshold

    @Test
    fun testAddWeightedThreshold_submitsAddPolicyWithSignerWeightsMap() = runTest {
        val deployer = KeyPair.random()
        val accountXdr = buildAccountEntryXdr(deployer).toXdrBase64()
        val sorobanDataXdr = buildMinimalSorobanData().toXdrBase64()
        val authEntryXdr = authEntryFor(VERIFIER_A).toXdrBase64()

        var simulateBody: String? = null
        val server = policyMockServer(
            submitResponses(accountXdr, authEntryXdr, sorobanDataXdr)
        ) { index, body -> if (index == 1) simulateBody = body }

        val kit = OZSmartAccountKit.createWithServer(buildKitConfig(deployer), server)
        kit.setConnectedState("test-credential-id", smartAccount)

        val weights: Map<SmartAccountSigner, UInt> = mapOf(ruleSignerA to 60u, ruleSignerB to 40u)

        val result = kit.policyManager.addWeightedThreshold(
            contextRuleId = 2u,
            policyAddress = policyA,
            signerWeights = weights,
            threshold = 100u
        )

        assertTrue(result.success, "Round-trip must succeed; error=${result.error}")

        val invocation = policyInvocation(assertNotNull(simulateBody))
        assertEquals("add_policy", invocation.functionName.value)
        assertEquals(2u, Scv.fromUint32(invocation.args[0]))

        val installParams = extractMapEntries(invocation.args[2])
        assertEquals(2, installParams.size)
        assertEquals("signer_weights", extractSymbolName(installParams[0].key))
        assertEquals("threshold", extractSymbolName(installParams[1].key))
        assertEquals(100u, (installParams[1].`val` as SCValXdr.U32).value.value)

        val encodedWeights = extractMapEntries(installParams[0].`val`)
            .associate { it.key.toXdrBase64() to (it.`val` as SCValXdr.U32).value.value }
        assertEquals(
            mapOf(
                ruleSignerA.toScVal().toXdrBase64() to 60u,
                ruleSignerB.toScVal().toXdrBase64() to 40u
            ),
            encodedWeights,
            "Each signer must be installed with the weight it was given"
        )
    }

    @Test
    fun testAddWeightedThreshold_emptySignerWeights_throwsBeforeAnyRpcCall() = runTest {
        val deployer = KeyPair.random()
        val server = policyMockServer(emptyList())

        val kit = OZSmartAccountKit.createWithServer(buildKitConfig(deployer), server)
        kit.setConnectedState("test-credential-id", smartAccount)

        val exception = assertFailsWith<ValidationException.InvalidInput> {
            kit.policyManager.addWeightedThreshold(
                contextRuleId = 0u,
                policyAddress = policyA,
                signerWeights = emptyMap(),
                threshold = 10u
            )
        }
        assertTrue(
            exception.message.contains("at least one signer"),
            "The encoder must reject an empty weights map; got: ${exception.message}"
        )
    }

    // MARK: - removePolicy by ID

    @Test
    fun testRemovePolicyById_submitsRemovePolicyInvocation() = runTest {
        val deployer = KeyPair.random()
        val accountXdr = buildAccountEntryXdr(deployer).toXdrBase64()
        val sorobanDataXdr = buildMinimalSorobanData().toXdrBase64()
        val authEntryXdr = authEntryFor(VERIFIER_A).toXdrBase64()

        var simulateBody: String? = null
        var requestCount = 0
        val server = policyMockServer(
            submitResponses(accountXdr, authEntryXdr, sorobanDataXdr)
        ) { index, body ->
            requestCount = index + 1
            if (index == 1) simulateBody = body
        }

        val kit = OZSmartAccountKit.createWithServer(buildKitConfig(deployer), server)
        kit.setConnectedState("test-credential-id", smartAccount)

        val result = kit.policyManager.removePolicy(contextRuleId = 4u, policyId = 6u)

        assertTrue(result.success, "Round-trip must succeed; error=${result.error}")
        assertEquals(policyTxHash, result.hash)
        assertEquals(1001u, result.ledger, "Ledger must come from the confirmation poll")
        assertEquals(9, requestCount, "The round-trip must issue exactly nine RPC calls")

        val invocation = policyInvocation(assertNotNull(simulateBody))
        assertEquals("remove_policy", invocation.functionName.value)
        assertEquals(
            smartAccount,
            Address.fromSCAddress(invocation.contractAddress).toString(),
            "The invocation must target the connected smart account"
        )
        assertEquals(2, invocation.args.size, "remove_policy takes two arguments")
        assertEquals(4u, Scv.fromUint32(invocation.args[0]))
        assertEquals(6u, Scv.fromUint32(invocation.args[1]))
    }

    @Test
    fun testRemovePolicyById_selectedSigners_routesToMultiSignerSubmission() = runTest {
        val deployer = KeyPair.random()
        val walletKeypair = KeyPair.random()
        val accountXdr = buildAccountEntryXdr(deployer).toXdrBase64()
        val sorobanDataXdr = buildMinimalSorobanData().toXdrBase64()

        val walletAddress = walletKeypair.getAccountId()
        val authEntryXdr = authEntryFor(walletAddress, targetContract = smartAccount).toXdrBase64()

        var simulateBody: String? = null
        var sendBody: String? = null
        val server = policyMockServer(
            submitResponses(accountXdr, authEntryXdr, sorobanDataXdr)
        ) { index, body ->
            if (index == 1) simulateBody = body
            if (index == 7) sendBody = body
        }

        val kit = OZSmartAccountKit.createWithServer(buildKitConfig(deployer), server)
        kit.externalSigners.addFromSecret(walletKeypair.getSecretSeed()!!.concatToString())
        kit.setConnectedState("test-credential-id", smartAccount)

        val result = kit.policyManager.removePolicy(
            contextRuleId = 0u,
            policyId = 1u,
            selectedSigners = listOf(SelectedSigner.Wallet(walletAddress))
        )

        assertTrue(result.success, "Multi-signer round-trip must succeed; error=${result.error}")
        assertEquals("remove_policy", policyInvocation(assertNotNull(simulateBody)).functionName.value)

        val submitted = policySubmittedAuth(assertNotNull(sendBody))
        assertEquals(1, submitted.size)
        val credentials = (submitted[0].credentials as SorobanCredentialsXdr.Address).value
        assertTrue(
            credentials.signatureExpirationLedger.value > 0u,
            "The submitted entry must carry a stamped expiration"
        )
        assertTrue(
            credentials.signature !is SCValXdr.Void,
            "The submitted entry must carry the wallet signature"
        )
    }

    // MARK: - removePolicy by address

    @Test
    fun testRemovePolicyByAddress_resolvesPolicyIdFromContextRule() = runTest {
        val deployer = KeyPair.random()
        val accountXdr = buildAccountEntryXdr(deployer).toXdrBase64()
        val sorobanDataXdr = buildMinimalSorobanData().toXdrBase64()
        val authEntryXdr = authEntryFor(VERIFIER_A).toXdrBase64()
        val rule = contextRuleScVal(
            id = 2u,
            name = "TokenOps",
            policies = listOf(policyA, policyB),
            policyIds = listOf(4u, 9u)
        )

        var ruleQueryBody: String? = null
        var simulateBody: String? = null
        val server = policyMockServer(
            listOf(
                ledgerEntriesResponseJson(accountXdr),
                simulateCountResponseJson(rule.toXdrBase64(), sorobanDataXdr)
            ) + submitResponses(accountXdr, authEntryXdr, sorobanDataXdr)
        ) { index, body ->
            if (index == 1) ruleQueryBody = body
            if (index == 3) simulateBody = body
        }

        val kit = OZSmartAccountKit.createWithServer(buildKitConfig(deployer), server)
        kit.setConnectedState("test-credential-id", smartAccount)

        val result = kit.policyManager.removePolicy(contextRuleId = 2u, policyAddress = policyB)

        assertTrue(result.success, "Round-trip must succeed; error=${result.error}")

        val ruleQuery = policyInvocation(assertNotNull(ruleQueryBody))
        assertEquals("get_context_rule", ruleQuery.functionName.value)
        assertEquals(2u, Scv.fromUint32(ruleQuery.args[0]), "The rule holding the policy must be fetched")

        val invocation = policyInvocation(assertNotNull(simulateBody))
        assertEquals("remove_policy", invocation.functionName.value)
        assertEquals(2u, Scv.fromUint32(invocation.args[0]))
        assertEquals(
            9u,
            Scv.fromUint32(invocation.args[1]),
            "The policy ID must be the one aligned with the requested policy address"
        )
    }

    @Test
    fun testRemovePolicyByAddress_addressNotOnContextRule_throwsValidationException() = runTest {
        val deployer = KeyPair.random()
        val accountXdr = buildAccountEntryXdr(deployer).toXdrBase64()
        val sorobanDataXdr = buildMinimalSorobanData().toXdrBase64()
        val rule = contextRuleScVal(
            id = 1u,
            name = "TokenOps",
            policies = listOf(policyA),
            policyIds = listOf(4u)
        )

        val server = policyMockServer(
            listOf(
                ledgerEntriesResponseJson(accountXdr),
                simulateCountResponseJson(rule.toXdrBase64(), sorobanDataXdr)
            )
        )

        val kit = OZSmartAccountKit.createWithServer(buildKitConfig(deployer), server)
        kit.setConnectedState("test-credential-id", smartAccount)

        val exception = assertFailsWith<ValidationException.InvalidInput> {
            kit.policyManager.removePolicy(contextRuleId = 1u, policyAddress = policyB)
        }
        assertTrue(
            exception.message.contains(policyB),
            "The exception must name the policy that is not installed; got: ${exception.message}"
        )
        assertTrue(
            exception.message.contains("not found on context rule 1"),
            "The exception must name the context rule that was searched; got: ${exception.message}"
        )
    }

    @Test
    fun testRemovePolicyByAddress_policyIdsShorterThanPolicies_throwsValidationException() = runTest {
        val deployer = KeyPair.random()
        val accountXdr = buildAccountEntryXdr(deployer).toXdrBase64()
        val sorobanDataXdr = buildMinimalSorobanData().toXdrBase64()
        // A rule whose policy_ids vector does not cover every installed policy: the address
        // resolves to index 1, for which no ID exists.
        val rule = contextRuleScVal(
            id = 3u,
            name = "TokenOps",
            policies = listOf(policyA, policyB),
            policyIds = listOf(4u)
        )

        val server = policyMockServer(
            listOf(
                ledgerEntriesResponseJson(accountXdr),
                simulateCountResponseJson(rule.toXdrBase64(), sorobanDataXdr)
            )
        )

        val kit = OZSmartAccountKit.createWithServer(buildKitConfig(deployer), server)
        kit.setConnectedState("test-credential-id", smartAccount)

        val exception = assertFailsWith<ValidationException.InvalidInput> {
            kit.policyManager.removePolicy(contextRuleId = 3u, policyAddress = policyB)
        }
        assertTrue(
            exception.message.contains("index 1") && exception.message.contains("only 1 entries"),
            "The exception must report the misaligned index and ID count; got: ${exception.message}"
        )
    }

    @Test
    fun testRemovePolicyByAddress_invalidPolicyAddress_throwsBeforeAnyRpcCall() = runTest {
        val deployer = KeyPair.random()
        val server = policyMockServer(emptyList())

        val kit = OZSmartAccountKit.createWithServer(buildKitConfig(deployer), server)
        kit.setConnectedState("test-credential-id", smartAccount)

        val exception = assertFailsWith<ValidationException.InvalidAddress> {
            kit.policyManager.removePolicy(contextRuleId = 0u, policyAddress = "NOT-A-CONTRACT")
        }
        assertTrue(
            exception.message.contains("policyAddress"),
            "The exception must name the offending field; got: ${exception.message}"
        )
    }

    // MARK: - Helper Functions

    /**
     * Extracts map entries from an SCValXdr.Map.
     */
    private fun extractMapEntries(scVal: SCValXdr): List<SCMapEntryXdr> {
        assertIs<SCValXdr.Map>(scVal)
        return (scVal as SCValXdr.Map).value?.value ?: emptyList()
    }
}
