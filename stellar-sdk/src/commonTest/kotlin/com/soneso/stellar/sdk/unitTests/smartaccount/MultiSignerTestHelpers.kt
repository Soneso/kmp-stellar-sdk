//
//  MultiSignerTestHelpers.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.unitTests.smartaccount

import com.soneso.stellar.sdk.Address
import com.soneso.stellar.sdk.KeyPair
import com.soneso.stellar.sdk.rpc.SorobanServer
import com.soneso.stellar.sdk.xdr.AccountEntryExtXdr
import com.soneso.stellar.sdk.xdr.AccountEntryXdr
import com.soneso.stellar.sdk.xdr.AccountIDXdr
import com.soneso.stellar.sdk.xdr.HostFunctionXdr
import com.soneso.stellar.sdk.xdr.Int64Xdr
import com.soneso.stellar.sdk.xdr.InvokeContractArgsXdr
import com.soneso.stellar.sdk.xdr.LedgerEntryDataXdr
import com.soneso.stellar.sdk.xdr.LedgerFootprintXdr
import com.soneso.stellar.sdk.xdr.PublicKeyXdr
import com.soneso.stellar.sdk.xdr.SCSymbolXdr
import com.soneso.stellar.sdk.xdr.SCValTypeXdr
import com.soneso.stellar.sdk.xdr.SCValXdr
import com.soneso.stellar.sdk.xdr.SequenceNumberXdr
import com.soneso.stellar.sdk.xdr.SorobanAddressCredentialsXdr
import com.soneso.stellar.sdk.xdr.SorobanAuthorizationEntryXdr
import com.soneso.stellar.sdk.xdr.SorobanAuthorizedFunctionXdr
import com.soneso.stellar.sdk.xdr.SorobanAuthorizedInvocationXdr
import com.soneso.stellar.sdk.xdr.SorobanCredentialsXdr
import com.soneso.stellar.sdk.xdr.SorobanResourcesXdr
import com.soneso.stellar.sdk.xdr.SorobanTransactionDataExtXdr
import com.soneso.stellar.sdk.xdr.SorobanTransactionDataXdr
import com.soneso.stellar.sdk.xdr.String32Xdr
import com.soneso.stellar.sdk.xdr.ThresholdsXdr
import com.soneso.stellar.sdk.xdr.Uint256Xdr
import com.soneso.stellar.sdk.xdr.Uint32Xdr
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import kotlinx.serialization.json.Json

// ---------------------------------------------------------------------------
// RPC mock response builders
// ---------------------------------------------------------------------------

internal fun ledgerEntriesResponseJson(accountXdrBase64: String): String = """
{
  "jsonrpc": "2.0",
  "id": "test-id",
  "result": {
    "entries": [
      {
        "key": "AAAAAAAAAAA=",
        "xdr": "$accountXdrBase64",
        "lastModifiedLedgerSeq": 100
      }
    ],
    "latestLedger": 100
  }
}
""".trimIndent()

internal fun simulateWithAuthResponseJson(authEntryBase64: String, sorobanDataBase64: String): String = """
{
  "jsonrpc": "2.0",
  "id": "test-id",
  "result": {
    "transactionData": "$sorobanDataBase64",
    "minResourceFee": 100,
    "results": [
      {
        "auth": ["$authEntryBase64"],
        "xdr": null
      }
    ],
    "latestLedger": 100
  }
}
""".trimIndent()

internal fun latestLedgerResponseJson(sequence: Int = 1000): String = """
{
  "jsonrpc": "2.0",
  "id": "test-id",
  "result": {
    "id": "abc123",
    "protocolVersion": 20,
    "sequence": $sequence
  }
}
""".trimIndent()

internal fun simulateCountResponseJson(countXdrBase64: String, sorobanDataBase64: String): String = """
{
  "jsonrpc": "2.0",
  "id": "test-id",
  "result": {
    "transactionData": "$sorobanDataBase64",
    "minResourceFee": 100,
    "results": [
      {
        "auth": [],
        "xdr": "$countXdrBase64"
      }
    ],
    "latestLedger": 100
  }
}
""".trimIndent()

internal fun sendTransactionPendingResponseJson(hash: String): String = """
{
  "jsonrpc": "2.0",
  "id": "test-id",
  "result": {
    "status": "PENDING",
    "hash": "$hash",
    "latestLedger": 1001,
    "latestLedgerCloseTime": 1700000000
  }
}
""".trimIndent()

internal fun getTransactionSuccessResponseJson(hash: String): String = """
{
  "jsonrpc": "2.0",
  "id": "test-id",
  "result": {
    "status": "SUCCESS",
    "latestLedger": 1002,
    "latestLedgerCloseTime": 1700000010,
    "oldestLedger": 900,
    "oldestLedgerCloseTime": 1699990000,
    "ledger": 1001,
    "createdAt": 1700000000,
    "envelopeXdr": null,
    "resultXdr": null,
    "resultMetaXdr": null
  }
}
""".trimIndent()

// ---------------------------------------------------------------------------
// XDR builders
// ---------------------------------------------------------------------------

internal fun buildAccountEntryXdr(deployer: KeyPair): LedgerEntryDataXdr {
    val accountEntry = AccountEntryXdr(
        accountId = AccountIDXdr(PublicKeyXdr.Ed25519(Uint256Xdr(deployer.getPublicKey()))),
        balance = Int64Xdr(100_000_000L),
        seqNum = SequenceNumberXdr(Int64Xdr(1L)),
        numSubEntries = Uint32Xdr(0u),
        inflationDest = null,
        flags = Uint32Xdr(0u),
        homeDomain = String32Xdr(""),
        thresholds = ThresholdsXdr(byteArrayOf(1, 0, 0, 0)),
        signers = emptyList(),
        ext = AccountEntryExtXdr.Void
    )
    return LedgerEntryDataXdr.Account(accountEntry)
}

internal fun buildAuthEntry(contractId: String): SorobanAuthorizationEntryXdr {
    val scAddress = Address(contractId).toSCAddress()
    return SorobanAuthorizationEntryXdr(
        credentials = SorobanCredentialsXdr.Address(
            SorobanAddressCredentialsXdr(
                address = scAddress,
                nonce = Int64Xdr(12345L),
                signatureExpirationLedger = Uint32Xdr(0u),
                signature = SCValXdr.Void(SCValTypeXdr.SCV_VOID)
            )
        ),
        rootInvocation = SorobanAuthorizedInvocationXdr(
            function = SorobanAuthorizedFunctionXdr.ContractFn(
                InvokeContractArgsXdr(
                    contractAddress = scAddress,
                    functionName = SCSymbolXdr("noop"),
                    args = emptyList()
                )
            ),
            subInvocations = emptyList()
        )
    )
}

internal fun buildMinimalSorobanData(): SorobanTransactionDataXdr = SorobanTransactionDataXdr(
    ext = SorobanTransactionDataExtXdr.Void,
    resources = SorobanResourcesXdr(
        footprint = LedgerFootprintXdr(emptyList(), emptyList()),
        instructions = Uint32Xdr(0u),
        diskReadBytes = Uint32Xdr(0u),
        writeBytes = Uint32Xdr(0u)
    ),
    resourceFee = Int64Xdr(0L)
)

internal fun stubHostFunction(contractId: String): HostFunctionXdr =
    HostFunctionXdr.InvokeContract(
        InvokeContractArgsXdr(
            contractAddress = Address(contractId).toSCAddress(),
            functionName = SCSymbolXdr("noop"),
            args = emptyList()
        )
    )

// ---------------------------------------------------------------------------
// Mock server builders
// ---------------------------------------------------------------------------

private fun buildMockHttpClient(engine: MockEngine): HttpClient = HttpClient(engine) {
    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = false
        })
    }
}

/**
 * Creates a [SorobanServer] backed by a [MockEngine] that handles the 5-response
 * sequence used when [OZMultiSignerManager.submitWithMultipleSigners] simulates
 * but does NOT reach the submission step (e.g., signing throws before re-simulation).
 *
 * Response sequence:
 * 1. getLedgerEntries — deployer account lookup (first simulation)
 * 2. simulateTransaction — main invocation simulation
 * 3. getLatestLedger — current ledger sequence
 * 4. getLedgerEntries — deployer account lookup (context-rule-count simulation)
 * 5. simulateTransaction — get_context_rules_count query (returns U32(0))
 */
internal fun buildSequentialMockServer(
    accountXdrBase64: String,
    authEntryBase64: String,
    sorobanDataBase64: String,
    countXdrBase64: String
): SorobanServer {
    var requestIndex = 0
    val mockEngine = MockEngine { _ ->
        val responseBody = when (requestIndex++) {
            0 -> ledgerEntriesResponseJson(accountXdrBase64)
            1 -> simulateWithAuthResponseJson(authEntryBase64, sorobanDataBase64)
            2 -> latestLedgerResponseJson()
            3 -> ledgerEntriesResponseJson(accountXdrBase64)
            4 -> simulateCountResponseJson(countXdrBase64, sorobanDataBase64)
            else -> error("Unexpected RPC request at index ${requestIndex - 1}")
        }
        respond(
            content = ByteReadChannel(responseBody),
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, "application/json")
        )
    }
    return SorobanServer("https://soroban-testnet.stellar.org", buildMockHttpClient(mockEngine))
}

/**
 * Creates a [SorobanServer] backed by a [MockEngine] that handles the full 9-response
 * sequence for a successful [OZMultiSignerManager.submitWithMultipleSigners] round-trip:
 *
 * 1. getLedgerEntries — deployer account lookup (first simulation)
 * 2. simulateTransaction — main invocation simulation
 * 3. getLatestLedger — current ledger sequence
 * 4. getLedgerEntries — deployer account lookup (context-rule-count simulation)
 * 5. simulateTransaction — get_context_rules_count query (returns U32(0))
 * 6. getLedgerEntries — deployer account lookup (re-fetch before re-simulation)
 * 7. simulateTransaction — re-simulation with signed auth entries
 * 8. sendTransaction — submission, returns PENDING with [txHash]
 * 9+ getTransaction — polling calls, all return SUCCESS
 */
internal fun buildSequentialMockServerWithSubmission(
    accountXdrBase64: String,
    authEntryBase64: String,
    sorobanDataBase64: String,
    countXdrBase64: String,
    txHash: String
): SorobanServer {
    var requestIndex = 0
    val mockEngine = MockEngine { _ ->
        val responseBody = when (requestIndex++) {
            0 -> ledgerEntriesResponseJson(accountXdrBase64)
            1 -> simulateWithAuthResponseJson(authEntryBase64, sorobanDataBase64)
            2 -> latestLedgerResponseJson()
            3 -> ledgerEntriesResponseJson(accountXdrBase64)
            4 -> simulateCountResponseJson(countXdrBase64, sorobanDataBase64)
            5 -> ledgerEntriesResponseJson(accountXdrBase64)
            6 -> simulateWithAuthResponseJson(authEntryBase64, sorobanDataBase64)
            7 -> sendTransactionPendingResponseJson(txHash)
            else -> getTransactionSuccessResponseJson(txHash)
        }
        respond(
            content = ByteReadChannel(responseBody),
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, "application/json")
        )
    }
    return SorobanServer("https://soroban-testnet.stellar.org", buildMockHttpClient(mockEngine))
}
