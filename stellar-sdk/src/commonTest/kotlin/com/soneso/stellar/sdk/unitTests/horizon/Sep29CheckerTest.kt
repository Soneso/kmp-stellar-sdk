package com.soneso.stellar.sdk.unitTests.horizon

import com.soneso.stellar.sdk.*
import com.soneso.stellar.sdk.horizon.HorizonServer
import com.soneso.stellar.sdk.horizon.Sep29Checker
import com.soneso.stellar.sdk.horizon.exceptions.AccountRequiresMemoException
import com.soneso.stellar.sdk.horizon.exceptions.BadRequestException
import com.soneso.stellar.sdk.xdr.EnvelopeTypeXdr
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.*

/**
 * Unit tests for Sep29Checker (SEP-29 memo required checking).
 *
 * Every envelope under test is serialized by the SDK itself, so the assertions hold against
 * the byte layout Horizon actually receives. Covered:
 * - Which operations contribute a destination, and the operation index reported for it
 * - Memo types that make the check unnecessary
 * - Muxed destinations, which are exempt
 * - V0, V1 and fee bump envelopes
 * - Horizon outcomes per destination: memo required, not required, missing, other error
 * - Envelope strings with line breaks, which are checked and submitted as passed
 * - Envelopes that cannot be decoded
 */
class Sep29CheckerTest {

    private val serverUri = Url("https://horizon.stellar.org")

    private val txSourceAccountId = "GADBBY4WFXKKFJ7CMTG3J5YAUXMQDBILRQ6W3U5IWN5TQFZU4MWZ5T4K"
    private val destinationAccountId = "GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7"
    private val otherDestinationAccountId = "GA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJVSGZ"

    private fun accountJson(accountId: String, memoRequired: Boolean): String {
        val data = if (memoRequired) """"config.memo_required": "MQ=="""" else """"unrelated.key": "AA==""""
        return """
        {
            "id": "$accountId",
            "account_id": "$accountId",
            "sequence": "3298702387052545",
            "subentry_count": 0,
            "last_modified_ledger": 7654321,
            "last_modified_time": "2021-01-01T00:00:00Z",
            "num_sponsoring": 0,
            "num_sponsored": 0,
            "paging_token": "$accountId",
            "thresholds": {"low_threshold": 0, "med_threshold": 0, "high_threshold": 0},
            "flags": {
                "auth_required": false,
                "auth_revocable": false,
                "auth_immutable": false,
                "auth_clawback_enabled": false
            },
            "balances": [{"asset_type": "native", "balance": "10.0000000"}],
            "signers": [{"weight": 1, "key": "$accountId", "type": "ed25519_public_key"}],
            "data": {$data},
            "_links": {
                "self": {"href": "https://horizon.stellar.org/accounts/$accountId"},
                "transactions": {"href": "https://horizon.stellar.org/accounts/$accountId/transactions"},
                "operations": {"href": "https://horizon.stellar.org/accounts/$accountId/operations"},
                "payments": {"href": "https://horizon.stellar.org/accounts/$accountId/payments"},
                "effects": {"href": "https://horizon.stellar.org/accounts/$accountId/effects"},
                "offers": {"href": "https://horizon.stellar.org/accounts/$accountId/offers"},
                "trades": {"href": "https://horizon.stellar.org/accounts/$accountId/trades"},
                "data": {"href": "https://horizon.stellar.org/accounts/$accountId/data/{key}", "templated": true}
            }
        }
        """
    }

    /**
     * Mock Horizon that records every account looked up by the checker and answers
     * according to the supplied account classification.
     */
    private fun createRecordingMockClient(
        lookedUpAccountIds: MutableList<String>,
        memoRequiredAccountIds: Set<String> = emptySet(),
        missingAccountIds: Set<String> = emptySet(),
        forbiddenAccountIds: Set<String> = emptySet()
    ): HttpClient {
        val mockEngine = MockEngine { request ->
            val accountId = request.url.encodedPath.substringAfterLast('/')
            lookedUpAccountIds.add(accountId)
            val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
            when (accountId) {
                in missingAccountIds -> respond(
                    content = """{"title": "Resource Missing", "status": 404}""",
                    status = HttpStatusCode.NotFound,
                    headers = jsonHeaders
                )
                in forbiddenAccountIds -> respond(
                    content = """{"title": "Forbidden", "status": 403}""",
                    status = HttpStatusCode.Forbidden,
                    headers = jsonHeaders
                )
                else -> respond(
                    content = accountJson(accountId, accountId in memoRequiredAccountIds),
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders
                )
            }
        }

        return HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
        }
    }

    private fun buildTransaction(operations: List<Operation>, memo: Memo = MemoNone): Transaction {
        val builder = TransactionBuilder(Account(txSourceAccountId, 1234567890L), Network.TESTNET)
            .setBaseFee(100)
            .addPreconditions(TransactionPreconditions(timeBounds = TimeBounds(0, 0)))
        operations.forEach { builder.addOperation(it) }
        if (memo !== MemoNone) {
            builder.addMemo(memo)
        }
        return builder.build()
    }

    private fun envelopeXdr(operations: List<Operation>, memo: Memo = MemoNone): String =
        buildTransaction(operations, memo).toEnvelopeXdrBase64()

    @Test
    fun testCheckMemoRequiredThrowsForPaymentDestinationWithMemoRequiredEntry() = runTest {
        val lookedUp = mutableListOf<String>()
        val checker = Sep29Checker(
            createRecordingMockClient(lookedUp, memoRequiredAccountIds = setOf(destinationAccountId)),
            serverUri
        )

        val envelope = envelopeXdr(
            listOf(PaymentOperation(destinationAccountId, AssetTypeNative, "10.0000000"))
        )

        val exception = assertFailsWith<AccountRequiresMemoException> {
            checker.checkMemoRequired(envelope)
        }
        assertEquals(destinationAccountId, exception.accountId)
        assertEquals(0, exception.operationIndex)
        assertEquals(listOf(destinationAccountId), lookedUp)
    }

    @Test
    fun testCheckMemoRequiredAllowsPaymentDestinationWithoutMemoRequiredEntry() = runTest {
        val lookedUp = mutableListOf<String>()
        val checker = Sep29Checker(createRecordingMockClient(lookedUp), serverUri)

        val envelope = envelopeXdr(
            listOf(PaymentOperation(destinationAccountId, AssetTypeNative, "10.0000000"))
        )

        checker.checkMemoRequired(envelope)

        assertEquals(listOf(destinationAccountId), lookedUp)
    }

    @Test
    fun testCheckMemoRequiredThrowsForPathPaymentStrictReceiveDestination() = runTest {
        val lookedUp = mutableListOf<String>()
        val checker = Sep29Checker(
            createRecordingMockClient(lookedUp, memoRequiredAccountIds = setOf(destinationAccountId)),
            serverUri
        )

        val envelope = envelopeXdr(
            listOf(
                PathPaymentStrictReceiveOperation(
                    sendAsset = AssetTypeCreditAlphaNum4("USD", txSourceAccountId),
                    sendMax = "100.0000000",
                    destination = destinationAccountId,
                    destAsset = AssetTypeNative,
                    destAmount = "10.0000000"
                )
            )
        )

        val exception = assertFailsWith<AccountRequiresMemoException> {
            checker.checkMemoRequired(envelope)
        }
        assertEquals(destinationAccountId, exception.accountId)
        assertEquals(0, exception.operationIndex)
    }

    @Test
    fun testCheckMemoRequiredThrowsForPathPaymentStrictSendDestination() = runTest {
        val lookedUp = mutableListOf<String>()
        val checker = Sep29Checker(
            createRecordingMockClient(lookedUp, memoRequiredAccountIds = setOf(destinationAccountId)),
            serverUri
        )

        val envelope = envelopeXdr(
            listOf(
                PathPaymentStrictSendOperation(
                    sendAsset = AssetTypeNative,
                    sendAmount = "10.0000000",
                    destination = destinationAccountId,
                    destAsset = AssetTypeCreditAlphaNum12("LONGASSET", txSourceAccountId),
                    destMin = "1.0000000"
                )
            )
        )

        val exception = assertFailsWith<AccountRequiresMemoException> {
            checker.checkMemoRequired(envelope)
        }
        assertEquals(destinationAccountId, exception.accountId)
        assertEquals(0, exception.operationIndex)
    }

    @Test
    fun testCheckMemoRequiredThrowsForAccountMergeDestination() = runTest {
        val lookedUp = mutableListOf<String>()
        val checker = Sep29Checker(
            createRecordingMockClient(lookedUp, memoRequiredAccountIds = setOf(destinationAccountId)),
            serverUri
        )

        val envelope = envelopeXdr(listOf(AccountMergeOperation(destinationAccountId)))

        val exception = assertFailsWith<AccountRequiresMemoException> {
            checker.checkMemoRequired(envelope)
        }
        assertEquals(destinationAccountId, exception.accountId)
        assertEquals(0, exception.operationIndex)
    }

    @Test
    fun testCheckMemoRequiredReportsIndexOfOperationHoldingMemoRequiredDestination() = runTest {
        val lookedUp = mutableListOf<String>()
        val checker = Sep29Checker(
            createRecordingMockClient(lookedUp, memoRequiredAccountIds = setOf(otherDestinationAccountId)),
            serverUri
        )

        // Operation 1 carries no destination and must not shift the reported index
        val envelope = envelopeXdr(
            listOf(
                PaymentOperation(destinationAccountId, AssetTypeNative, "1.0000000"),
                BumpSequenceOperation(1234567900L),
                PaymentOperation(otherDestinationAccountId, AssetTypeNative, "2.0000000")
            )
        )

        val exception = assertFailsWith<AccountRequiresMemoException> {
            checker.checkMemoRequired(envelope)
        }
        assertEquals(otherDestinationAccountId, exception.accountId)
        assertEquals(2, exception.operationIndex)
    }

    @Test
    fun testCheckMemoRequiredSkipsAccountLookupWhenTransactionCarriesMemo() = runTest {
        val lookedUp = mutableListOf<String>()
        val checker = Sep29Checker(
            createRecordingMockClient(lookedUp, memoRequiredAccountIds = setOf(destinationAccountId)),
            serverUri
        )

        val envelope = envelopeXdr(
            listOf(PaymentOperation(destinationAccountId, AssetTypeNative, "10.0000000")),
            memo = MemoText("invoice-42")
        )

        checker.checkMemoRequired(envelope)

        assertTrue(lookedUp.isEmpty(), "A transaction carrying a memo must not trigger account lookups")
    }

    @Test
    fun testCheckMemoRequiredSkipsAccountLookupForIdMemo() = runTest {
        val lookedUp = mutableListOf<String>()
        val checker = Sep29Checker(
            createRecordingMockClient(lookedUp, memoRequiredAccountIds = setOf(destinationAccountId)),
            serverUri
        )

        val envelope = envelopeXdr(
            listOf(PaymentOperation(destinationAccountId, AssetTypeNative, "10.0000000")),
            memo = MemoId(9876543210UL)
        )

        checker.checkMemoRequired(envelope)

        assertTrue(lookedUp.isEmpty(), "A transaction carrying an id memo must not trigger account lookups")
    }

    @Test
    fun testCheckMemoRequiredSkipsMuxedDestination() = runTest {
        val lookedUp = mutableListOf<String>()
        val checker = Sep29Checker(
            createRecordingMockClient(lookedUp, memoRequiredAccountIds = setOf(destinationAccountId)),
            serverUri
        )

        val muxedDestination = MuxedAccount(destinationAccountId, 1234UL).address
        val envelope = envelopeXdr(
            listOf(PaymentOperation(muxedDestination, AssetTypeNative, "10.0000000"))
        )

        checker.checkMemoRequired(envelope)

        assertTrue(lookedUp.isEmpty(), "Muxed destinations already carry the virtual account id")
    }

    @Test
    fun testCheckMemoRequiredInspectsInnerOperationsOfFeeBumpTransaction() = runTest {
        val lookedUp = mutableListOf<String>()
        val checker = Sep29Checker(
            createRecordingMockClient(lookedUp, memoRequiredAccountIds = setOf(destinationAccountId)),
            serverUri
        )

        val inner = buildTransaction(
            listOf(PaymentOperation(destinationAccountId, AssetTypeNative, "10.0000000"))
        )
        val feeBump = FeeBumpTransactionBuilder(inner)
            .setFeeSource(txSourceAccountId)
            .setBaseFee(200)
            .build()

        val exception = assertFailsWith<AccountRequiresMemoException> {
            checker.checkMemoRequired(feeBump.toEnvelopeXdrBase64())
        }
        assertEquals(destinationAccountId, exception.accountId)
    }

    @Test
    fun testCheckMemoRequiredQueriesRepeatedDestinationOnlyOnce() = runTest {
        val lookedUp = mutableListOf<String>()
        val checker = Sep29Checker(createRecordingMockClient(lookedUp), serverUri)

        val envelope = envelopeXdr(
            listOf(
                PaymentOperation(destinationAccountId, AssetTypeNative, "1.0000000"),
                PaymentOperation(destinationAccountId, AssetTypeNative, "2.0000000")
            )
        )

        checker.checkMemoRequired(envelope)

        assertEquals(listOf(destinationAccountId), lookedUp)
    }

    @Test
    fun testCheckMemoRequiredTreatsMissingDestinationAccountAsNotRequiringMemo() = runTest {
        val lookedUp = mutableListOf<String>()
        val checker = Sep29Checker(
            createRecordingMockClient(lookedUp, missingAccountIds = setOf(destinationAccountId)),
            serverUri
        )

        val envelope = envelopeXdr(
            listOf(PaymentOperation(destinationAccountId, AssetTypeNative, "10.0000000"))
        )

        checker.checkMemoRequired(envelope)

        assertEquals(listOf(destinationAccountId), lookedUp)
    }

    @Test
    fun testCheckMemoRequiredPropagatesHorizonErrorsOtherThanNotFound() = runTest {
        val lookedUp = mutableListOf<String>()
        val checker = Sep29Checker(
            createRecordingMockClient(lookedUp, forbiddenAccountIds = setOf(destinationAccountId)),
            serverUri
        )

        val envelope = envelopeXdr(
            listOf(PaymentOperation(destinationAccountId, AssetTypeNative, "10.0000000"))
        )

        val exception = assertFailsWith<BadRequestException> {
            checker.checkMemoRequired(envelope)
        }
        assertEquals(403, exception.code)
    }

    @Test
    fun testCheckMemoRequiredSkipsCheckForTruncatedEnvelope() = runTest {
        val lookedUp = mutableListOf<String>()
        val checker = Sep29Checker(
            createRecordingMockClient(lookedUp, memoRequiredAccountIds = setOf(destinationAccountId)),
            serverUri
        )

        @OptIn(ExperimentalEncodingApi::class)
        val truncated = Base64.encode(
            Base64.decode(
                envelopeXdr(listOf(PaymentOperation(destinationAccountId, AssetTypeNative, "10.0000000")))
            ).copyOfRange(0, 24)
        )

        checker.checkMemoRequired(truncated)

        assertTrue(lookedUp.isEmpty(), "An unparseable envelope must not trigger account lookups")
    }

    @Test
    fun testCheckMemoRequiredSkipsCheckForInvalidBase64() = runTest {
        val lookedUp = mutableListOf<String>()
        val checker = Sep29Checker(
            createRecordingMockClient(lookedUp, memoRequiredAccountIds = setOf(destinationAccountId)),
            serverUri
        )

        checker.checkMemoRequired("invalid-base64-string!!!")

        assertTrue(lookedUp.isEmpty(), "Input that is not base64 must not trigger account lookups")
    }

    @Test
    fun testCheckMemoRequiredSkipsCheckForBytesThatAreNotAnEnvelope() = runTest {
        val lookedUp = mutableListOf<String>()
        val checker = Sep29Checker(
            createRecordingMockClient(lookedUp, memoRequiredAccountIds = setOf(destinationAccountId)),
            serverUri
        )

        @OptIn(ExperimentalEncodingApi::class)
        val notAnEnvelope = Base64.encode("this is not xdr at all".encodeToByteArray())

        checker.checkMemoRequired(notAnEnvelope)

        assertTrue(lookedUp.isEmpty(), "Base64 that decodes to non-envelope bytes must not trigger lookups")
    }

    @Test
    fun testCheckMemoRequiredSkipsAccountLookupForHashMemo() = runTest {
        val lookedUp = mutableListOf<String>()
        val checker = Sep29Checker(
            createRecordingMockClient(lookedUp, memoRequiredAccountIds = setOf(destinationAccountId)),
            serverUri
        )

        val envelope = envelopeXdr(
            listOf(PaymentOperation(destinationAccountId, AssetTypeNative, "10.0000000")),
            memo = MemoHash(ByteArray(32) { 7 })
        )

        checker.checkMemoRequired(envelope)

        assertTrue(lookedUp.isEmpty(), "A transaction carrying a hash memo must not trigger account lookups")
    }

    @Test
    fun testCheckMemoRequiredSkipsAccountLookupForReturnHashMemo() = runTest {
        val lookedUp = mutableListOf<String>()
        val checker = Sep29Checker(
            createRecordingMockClient(lookedUp, memoRequiredAccountIds = setOf(destinationAccountId)),
            serverUri
        )

        val envelope = envelopeXdr(
            listOf(PaymentOperation(destinationAccountId, AssetTypeNative, "10.0000000")),
            memo = MemoReturn(ByteArray(32) { 9 })
        )

        checker.checkMemoRequired(envelope)

        assertTrue(lookedUp.isEmpty(), "A transaction carrying a return memo must not trigger account lookups")
    }

    @Test
    fun testCheckMemoRequiredThrowsForPaymentDestinationInV0Envelope() = runTest {
        val lookedUp = mutableListOf<String>()
        val checker = Sep29Checker(
            createRecordingMockClient(lookedUp, memoRequiredAccountIds = setOf(destinationAccountId)),
            serverUri
        )

        val transaction = buildTransaction(
            listOf(PaymentOperation(destinationAccountId, AssetTypeNative, "10.0000000"))
        )
        transaction.envelopeType = EnvelopeTypeXdr.ENVELOPE_TYPE_TX_V0

        val exception = assertFailsWith<AccountRequiresMemoException> {
            checker.checkMemoRequired(transaction.toEnvelopeXdrBase64())
        }
        assertEquals(destinationAccountId, exception.accountId)
        assertEquals(0, exception.operationIndex)
        assertEquals(listOf(destinationAccountId), lookedUp)
    }

    // ===== The check as HorizonServer.submitTransaction applies it =====

    private val submittedTransactionJson = """
    {
        "id": "b9d0b2292c4e09e8eb22d036171491e87b8d2086bf8b265874c8d182cb9c9020",
        "paging_token": "12884905984",
        "hash": "b9d0b2292c4e09e8eb22d036171491e87b8d2086bf8b265874c8d182cb9c9020",
        "ledger": 3,
        "created_at": "2021-01-01T00:00:00Z",
        "source_account": "$txSourceAccountId",
        "source_account_sequence": "1234567891",
        "fee_account": "$txSourceAccountId",
        "fee_charged": 100,
        "max_fee": 100,
        "operation_count": 1,
        "successful": true,
        "signatures": [],
        "memo_type": "none",
        "_links": {
            "self": {"href": "https://horizon.stellar.org/transactions/b9d0b229"},
            "account": {"href": "https://horizon.stellar.org/accounts/$txSourceAccountId"},
            "ledger": {"href": "https://horizon.stellar.org/ledgers/3"},
            "operations": {"href": "https://horizon.stellar.org/transactions/b9d0b229/operations"},
            "effects": {"href": "https://horizon.stellar.org/transactions/b9d0b229/effects"},
            "precedes": {"href": "https://horizon.stellar.org/transactions?cursor=12884905984&order=asc"},
            "succeeds": {"href": "https://horizon.stellar.org/transactions?cursor=12884905984&order=desc"}
        }
    }
    """

    private fun createSubmitMockClient(): HttpClient {
        val mockEngine = MockEngine {
            respond(
                content = submittedTransactionJson,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        return HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
        }
    }

    @Test
    fun testSubmitTransactionRunsTheMemoRequiredCheck() = runTest {
        val lookedUp = mutableListOf<String>()
        val server = HorizonServer(
            serverUri.toString(),
            httpClient = createRecordingMockClient(lookedUp, memoRequiredAccountIds = setOf(destinationAccountId)),
            submitHttpClient = createSubmitMockClient()
        )

        val envelope = envelopeXdr(
            listOf(PaymentOperation(destinationAccountId, AssetTypeNative, "10.0000000"))
        )

        val exception = assertFailsWith<AccountRequiresMemoException> {
            server.submitTransaction(envelope)
        }
        assertEquals(destinationAccountId, exception.accountId)
        assertEquals(listOf(destinationAccountId), lookedUp)

        server.close()
    }

    @Test
    fun testSubmitTransactionSkipsTheMemoRequiredCheckWhenAsked() = runTest {
        val lookedUp = mutableListOf<String>()
        val server = HorizonServer(
            serverUri.toString(),
            httpClient = createRecordingMockClient(lookedUp, memoRequiredAccountIds = setOf(destinationAccountId)),
            submitHttpClient = createSubmitMockClient()
        )

        val envelope = envelopeXdr(
            listOf(PaymentOperation(destinationAccountId, AssetTypeNative, "10.0000000"))
        )

        val response = server.submitTransaction(envelope, skipMemoRequiredCheck = true)

        assertTrue(response.successful, "The submission response is returned unchanged")
        assertTrue(lookedUp.isEmpty(), "skipMemoRequiredCheck must suppress the destination lookup")

        server.close()
    }

    @Test
    fun testSubmitTransactionAsyncRunsTheMemoRequiredCheck() = runTest {
        val lookedUp = mutableListOf<String>()
        val server = HorizonServer(
            serverUri.toString(),
            httpClient = createRecordingMockClient(lookedUp, memoRequiredAccountIds = setOf(destinationAccountId)),
            submitHttpClient = createSubmitMockClient()
        )

        val envelope = envelopeXdr(
            listOf(PaymentOperation(destinationAccountId, AssetTypeNative, "10.0000000"))
        )

        val exception = assertFailsWith<AccountRequiresMemoException> {
            server.submitTransactionAsync(envelope)
        }
        assertEquals(destinationAccountId, exception.accountId)

        server.close()
    }

    // ===== Envelope strings through both submit methods =====

    /**
     * A server whose account lookups and submissions go to one mock engine, which records every
     * request as "METHOD path" and the form-encoded body of every submission as sent. With
     * [answerMalformed] a submission gets Horizon's answer to an envelope it cannot decode.
     */
    private fun recordingServer(
        requests: MutableList<String>,
        postedBodies: MutableList<String>,
        memoRequired: Boolean,
        answerMalformed: Boolean = false
    ): HorizonServer {
        val engine = MockEngine { request ->
            val path = request.url.encodedPath
            requests.add("${request.method.value} $path")
            val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
            when (path) {
                "/transactions", "/transactions_async" -> {
                    val body = request.body.toByteArray().decodeToString()
                    postedBodies.add(body)
                    when {
                        answerMalformed -> respond(
                            transactionMalformedJson(path, parseQueryString(body)["tx"].orEmpty()),
                            HttpStatusCode.BadRequest,
                            headersOf(HttpHeaders.ContentType, "application/problem+json; charset=utf-8")
                        )
                        path == "/transactions" -> respond(submittedTransactionJson, HttpStatusCode.OK, jsonHeaders)
                        else -> respond("""{"hash": "abc", "tx_status": "PENDING"}""", HttpStatusCode.Created, jsonHeaders)
                    }
                }
                else -> respond(accountJson(path.substringAfterLast('/'), memoRequired), HttpStatusCode.OK, jsonHeaders)
            }
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; isLenient = true }) }
        }
        return HorizonServer(serverUri.toString(), httpClient = client, submitHttpClient = client)
    }

    /**
     * Horizon's HTTP 400 body for an envelope it cannot decode, echoing the envelope. The async
     * endpoint adds the decoder error, an error value that Go encodes as `{}`.
     */
    private fun transactionMalformedJson(path: String, envelope: String): String = buildJsonObject {
        put("type", "https://stellar.org/horizon-errors/transaction_malformed")
        put("title", "Transaction Malformed")
        put("status", 400)
        put(
            "detail",
            "Horizon could not decode the transaction envelope in this request. A transaction should be an XDR " +
                "TransactionEnvelope struct encoded using base64.  The envelope read from this request is echoed " +
                "in the `extras.envelope_xdr` field of this response for your convenience."
        )
        putJsonObject("extras") {
            put("envelope_xdr", envelope)
            if (path == "/transactions_async") putJsonObject("error") {}
        }
    }.toString()

    private val submitPaths = listOf("/transactions", "/transactions_async")

    /**
     * Calls the submit method that posts to [path] and returns the hash of its response; without
     * [skip] it calls the one-argument overload.
     */
    private suspend fun submit(server: HorizonServer, path: String, envelope: String, skip: Boolean): String = when {
        path == "/transactions" && skip -> server.submitTransaction(envelope, skipMemoRequiredCheck = true).hash
        path == "/transactions" -> server.submitTransaction(envelope).hash
        skip -> server.submitTransactionAsync(envelope, skipMemoRequiredCheck = true).hash
        else -> server.submitTransactionAsync(envelope).hash
    }

    private fun paymentToDestination(): Transaction =
        buildTransaction(listOf(PaymentOperation(destinationAccountId, AssetTypeNative, "10.0000000")))

    private fun v0EnvelopeXdr(): String {
        val transaction = paymentToDestination()
        transaction.envelopeType = EnvelopeTypeXdr.ENVELOPE_TYPE_TX_V0
        return transaction.toEnvelopeXdrBase64()
    }

    private val envelopeKinds: Map<String, () -> String> = mapOf(
        "V0" to { v0EnvelopeXdr() },
        "V1" to { paymentToDestination().toEnvelopeXdrBase64() },
        "fee bump" to {
            FeeBumpTransactionBuilder(paymentToDestination())
                .setFeeSource(txSourceAccountId)
                .setBaseFee(200)
                .build()
                .toEnvelopeXdrBase64()
        }
    )

    private val lineBreaks: Map<String, (String) -> String> = mapOf(
        "LF lines" to { envelope: String -> envelope.chunked(76).joinToString("\n") },
        "CRLF lines" to { envelope: String -> envelope.chunked(76).joinToString("\r\n") },
        "a trailing LF" to { envelope: String -> envelope + "\n" },
        "a trailing CRLF" to { envelope: String -> envelope + "\r\n" }
    )

    /** A V0 payment envelope of 140 bytes ends in one `=`; its last digit carries two unused bits, set here. */
    private fun nonzeroPaddingBitsXdr(): String {
        val envelope = v0EnvelopeXdr()
        assertEquals(188, envelope.length)
        assertTrue(envelope.endsWith("=") && !envelope.endsWith("=="), envelope)
        val digits = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
        return envelope.substring(0, 186) + digits[digits.indexOf(envelope[186]) or 3] + "="
    }

    /** Strings that neither the check nor Horizon can decode. */
    @OptIn(ExperimentalEncodingApi::class)
    private val undecodableEnvelopes: Map<String, () -> String> = mapOf(
        "invalid base64" to { "invalid-base64-string!!!" },
        "bytes that are not an envelope" to { Base64.encode("this is not xdr at all".encodeToByteArray()) },
        "a truncated envelope" to {
            Base64.encode(Base64.decode(paymentToDestination().toEnvelopeXdrBase64()).copyOfRange(0, 24))
        },
        "a space" to { paymentToDestination().toEnvelopeXdrBase64().let { it.substring(0, 76) + " " + it.substring(76) } },
        "a tab" to { paymentToDestination().toEnvelopeXdrBase64().let { it.substring(0, 76) + "\t" + it.substring(76) } },
        "missing padding" to { v0EnvelopeXdr().trimEnd('=') }
    )

    @Test
    fun testSubmitMethodsCheckEnvelopesWithLineBreaks() = runTest {
        for (path in submitPaths) for ((kind, build) in envelopeKinds) for ((name, insert) in lineBreaks) {
            val case = "$path, $kind envelope with $name"
            val requests = mutableListOf<String>()
            val server = recordingServer(requests, mutableListOf(), memoRequired = true)

            val exception = assertFailsWith<AccountRequiresMemoException>(case) { submit(server, path, insert(build()), false) }

            assertEquals(destinationAccountId, exception.accountId, case)
            assertEquals(0, exception.operationIndex, case)
            assertEquals(listOf("GET /accounts/$destinationAccountId"), requests, case)
            server.close()
        }
    }

    @Test
    fun testSubmitMethodsPostCheckedEnvelopesWithLineBreaksAsPassed() = runTest {
        for (path in submitPaths) for ((kind, build) in envelopeKinds) for ((name, insert) in lineBreaks) {
            val case = "$path, $kind envelope with $name"
            val requests = mutableListOf<String>()
            val postedBodies = mutableListOf<String>()
            val server = recordingServer(requests, postedBodies, memoRequired = false)
            val input = insert(build())

            submit(server, path, input, false)

            assertEquals(listOf("GET /accounts/$destinationAccountId", "POST $path"), requests, case)
            val form = parseQueryString(postedBodies.single())
            assertEquals(setOf("tx"), form.names(), case)
            assertEquals(listOf(input), form.getAll("tx"), case)
            server.close()
        }
    }

    @Test
    fun testSubmitMethodsPostUndecodableEnvelopesUncheckedAndReturnHorizonsAnswer() = runTest {
        for (path in submitPaths) for ((name, envelope) in undecodableEnvelopes) {
            val case = "$path, envelope with $name"
            val requests = mutableListOf<String>()
            val postedBodies = mutableListOf<String>()
            val server = recordingServer(requests, postedBodies, memoRequired = true, answerMalformed = true)
            val input = envelope()

            val exception = assertFailsWith<BadRequestException>(case) { submit(server, path, input, false) }

            assertEquals(400, exception.code, case)
            assertEquals(transactionMalformedJson(path, input), exception.body, case)
            assertEquals(listOf("POST $path"), requests, case)
            assertEquals(listOf(input), parseQueryString(postedBodies.single()).getAll("tx"), case)
            server.close()
        }
    }

    @Test
    fun testSubmitMethodsPostEnvelopeWithNonzeroPaddingBitsUnchecked() = runTest {
        val expectedHashes = mapOf(
            "/transactions" to "b9d0b2292c4e09e8eb22d036171491e87b8d2086bf8b265874c8d182cb9c9020",
            "/transactions_async" to "abc"
        )
        for (path in submitPaths) {
            val requests = mutableListOf<String>()
            val postedBodies = mutableListOf<String>()
            val server = recordingServer(requests, postedBodies, memoRequired = true)
            val input = nonzeroPaddingBitsXdr()

            assertEquals(expectedHashes.getValue(path), submit(server, path, input, false), path)

            assertEquals(listOf("POST $path"), requests, path)
            assertEquals(listOf(input), parseQueryString(postedBodies.single()).getAll("tx"), path)
            server.close()
        }
    }

    @Test
    fun testSubmitMethodsPostEnvelopeWithLineBreaksUncheckedWhenTheCheckIsSkipped() = runTest {
        for (path in submitPaths) {
            val requests = mutableListOf<String>()
            val postedBodies = mutableListOf<String>()
            val server = recordingServer(requests, postedBodies, memoRequired = true)
            val input = paymentToDestination().toEnvelopeXdrBase64().chunked(76).joinToString("\r\n")

            submit(server, path, input, true)

            assertEquals(listOf("POST $path"), requests, path)
            assertEquals(listOf(input), parseQueryString(postedBodies.single()).getAll("tx"), path)
            server.close()
        }
    }
}
