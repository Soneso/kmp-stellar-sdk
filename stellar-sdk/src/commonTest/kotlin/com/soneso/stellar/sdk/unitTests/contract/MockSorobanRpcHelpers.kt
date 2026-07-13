//
//  MockSorobanRpcHelpers.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.unitTests.contract

import com.soneso.stellar.sdk.Address
import com.soneso.stellar.sdk.KeyPair
import com.soneso.stellar.sdk.rpc.SorobanServer
import com.soneso.stellar.sdk.xdr.AccountEntryExtXdr
import com.soneso.stellar.sdk.xdr.AccountEntryXdr
import com.soneso.stellar.sdk.xdr.ExtensionPointXdr
import com.soneso.stellar.sdk.xdr.Int64Xdr
import com.soneso.stellar.sdk.xdr.InvokeContractArgsXdr
import com.soneso.stellar.sdk.xdr.LedgerEntryChangesXdr
import com.soneso.stellar.sdk.xdr.LedgerEntryDataXdr
import com.soneso.stellar.sdk.xdr.LedgerFootprintXdr
import com.soneso.stellar.sdk.xdr.SCSymbolXdr
import com.soneso.stellar.sdk.xdr.SCValXdr
import com.soneso.stellar.sdk.xdr.SequenceNumberXdr
import com.soneso.stellar.sdk.xdr.SorobanAuthorizationEntryXdr
import com.soneso.stellar.sdk.xdr.SorobanAuthorizedFunctionXdr
import com.soneso.stellar.sdk.xdr.SorobanAuthorizedInvocationXdr
import com.soneso.stellar.sdk.xdr.SorobanCredentialsXdr
import com.soneso.stellar.sdk.xdr.SorobanResourcesXdr
import com.soneso.stellar.sdk.xdr.SorobanTransactionDataExtXdr
import com.soneso.stellar.sdk.xdr.SorobanTransactionDataXdr
import com.soneso.stellar.sdk.xdr.SorobanTransactionMetaExtXdr
import com.soneso.stellar.sdk.xdr.SorobanTransactionMetaXdr
import com.soneso.stellar.sdk.xdr.String32Xdr
import com.soneso.stellar.sdk.xdr.ThresholdsXdr
import com.soneso.stellar.sdk.xdr.TransactionMetaV3Xdr
import com.soneso.stellar.sdk.xdr.TransactionMetaXdr
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
import kotlinx.serialization.json.Json

/**
 * Shared mocked Soroban RPC scaffolding for ContractClient-level unit tests.
 *
 * [mockRpcServer] builds a [SorobanServer] over a Ktor MockEngine that routes on the
 * JSON-RPC method name and serves well-formed responses for the build/simulate path
 * and, optionally, the sign-and-submit path — so tests drive the real client logic
 * with no live server contacted.
 */

internal const val MOCK_RPC_SERVER_URL = "https://soroban-testnet.stellar.org:443"
internal const val MOCK_RPC_CONTRACT_ID = "CA3D5KRYM6CB7OWQ6TWYRR3Z4T7GNZLKERYNZGGA5SOAOPIFY6YQGAXE"
internal const val MOCK_RPC_SOURCE_ACCOUNT = "GBRPYHIL2CI3FNQ4BXLFMNDLFJUNPU2HY3ZMFSHONUCEOASW7QC7OX2H"
internal const val MOCK_RPC_SOURCE_SEQ = 100L

/** Minimal but well-formed Soroban transaction data; empty read-write footprint. */
internal fun minimalSorobanData(): SorobanTransactionDataXdr =
    SorobanTransactionDataXdr(
        ext = SorobanTransactionDataExtXdr.Void,
        resources = SorobanResourcesXdr(
            footprint = LedgerFootprintXdr(readOnly = emptyList(), readWrite = emptyList()),
            instructions = Uint32Xdr(0u),
            diskReadBytes = Uint32Xdr(0u),
            writeBytes = Uint32Xdr(0u)
        ),
        resourceFee = Int64Xdr(0L)
    )

/** A source-account (void-credentials) auth entry; its presence marks a write call. */
internal fun writeAuthEntry(contractId: String = MOCK_RPC_CONTRACT_ID): SorobanAuthorizationEntryXdr =
    SorobanAuthorizationEntryXdr(
        credentials = SorobanCredentialsXdr.Void,
        rootInvocation = SorobanAuthorizedInvocationXdr(
            function = SorobanAuthorizedFunctionXdr.ContractFn(
                InvokeContractArgsXdr(
                    contractAddress = Address(contractId).toSCAddress(),
                    functionName = SCSymbolXdr("hello"),
                    args = emptyList()
                )
            ),
            subInvocations = emptyList()
        )
    )

/** Base64 account ledger entry so getAccount() resolves a sequence number. */
internal fun accountEntryBase64(sourceAccount: String = MOCK_RPC_SOURCE_ACCOUNT): String {
    val accountId = KeyPair.fromAccountId(sourceAccount).getXdrAccountId()
    val entry = LedgerEntryDataXdr.Account(
        AccountEntryXdr(
            accountId = accountId,
            balance = Int64Xdr(10_000_000L),
            seqNum = SequenceNumberXdr(Int64Xdr(MOCK_RPC_SOURCE_SEQ)),
            numSubEntries = Uint32Xdr(0u),
            inflationDest = null,
            flags = Uint32Xdr(0u),
            homeDomain = String32Xdr(""),
            thresholds = ThresholdsXdr(ByteArray(4)),
            signers = emptyList(),
            ext = AccountEntryExtXdr.Void
        )
    )
    return entry.toXdrBase64()
}

internal fun ledgerEntriesResultJson(sourceAccount: String = MOCK_RPC_SOURCE_ACCOUNT): String {
    val entryXdr = accountEntryBase64(sourceAccount)
    return """
        {
          "entries": [ { "key": "AAAAAA==", "xdr": "$entryXdr", "lastModifiedLedgerSeq": 1 } ],
          "latestLedger": 14245
        }
    """.trimIndent()
}

internal fun simulateResultJson(
    returnValue: SCValXdr,
    authEntries: List<SorobanAuthorizationEntryXdr> = emptyList()
): String {
    val authArray = authEntries.joinToString(",") { "\"${it.toXdrBase64()}\"" }
    val txData = minimalSorobanData().toXdrBase64()
    val returnXdr = returnValue.toXdrBase64()
    return """
        {
          "transactionData": "$txData",
          "minResourceFee": "100",
          "results": [ { "auth": [ $authArray ], "xdr": "$returnXdr" } ],
          "latestLedger": 14245
        }
    """.trimIndent()
}

/**
 * Base64 TransactionMeta (V3) carrying [returnValue] as the Soroban return value, as the
 * network reports it in a successful getTransaction response's resultMetaXdr.
 */
internal fun successResultMetaBase64(returnValue: SCValXdr): String {
    val meta = TransactionMetaXdr.V3(
        TransactionMetaV3Xdr(
            ext = ExtensionPointXdr.Void,
            txChangesBefore = LedgerEntryChangesXdr(emptyList()),
            operations = emptyList(),
            txChangesAfter = LedgerEntryChangesXdr(emptyList()),
            sorobanMeta = SorobanTransactionMetaXdr(
                ext = SorobanTransactionMetaExtXdr.Void,
                events = emptyList(),
                returnValue = returnValue,
                diagnosticEvents = emptyList()
            )
        )
    )
    return meta.toXdrBase64()
}

/**
 * Builds a [SorobanServer] over a MockEngine routing on the JSON-RPC method name.
 *
 * getLedgerEntries resolves [sourceAccount]'s sequence number; simulateTransaction
 * carries [simulateReturnValue] and [authEntries] (a non-empty auth list makes the
 * call a write call). When [submittedReturnValue] is non-null, sendTransaction
 * (PENDING with [txHash]) and getTransaction (SUCCESS with a result meta carrying
 * [submittedReturnValue]) are also routed, driving the sign-and-submit branch end to
 * end; keeping the simulated and submitted values distinct lets a test pin a parsed
 * result to the submitted transaction rather than the simulation.
 */
internal fun mockRpcServer(
    simulateReturnValue: SCValXdr,
    authEntries: List<SorobanAuthorizationEntryXdr> = emptyList(),
    sourceAccount: String = MOCK_RPC_SOURCE_ACCOUNT,
    submittedReturnValue: SCValXdr? = null,
    txHash: String = "3389e9f0f1a7c19f0e9b8a1e9d2b3c4d5e6f70819293a4b5c6d7e8f901020304"
): SorobanServer {
    val engine = MockEngine { request ->
        val body = request.body.toByteArray().decodeToString()
        val resultJson = when {
            "\"getLedgerEntries\"" in body -> ledgerEntriesResultJson(sourceAccount)
            "\"simulateTransaction\"" in body -> simulateResultJson(simulateReturnValue, authEntries)
            submittedReturnValue != null && "\"sendTransaction\"" in body ->
                """{ "status": "PENDING", "hash": "$txHash", "latestLedger": 14245 }"""
            submittedReturnValue != null && "\"getTransaction\"" in body -> """
                {
                  "status": "SUCCESS",
                  "txHash": "$txHash",
                  "latestLedger": 14246,
                  "resultMetaXdr": "${successResultMetaBase64(submittedReturnValue)}"
                }
            """.trimIndent()
            else -> error("unrouted JSON-RPC request in mockRpcServer: $body")
        }
        respond(
            content = ByteReadChannel("""{ "jsonrpc": "2.0", "id": "1", "result": $resultJson }"""),
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
