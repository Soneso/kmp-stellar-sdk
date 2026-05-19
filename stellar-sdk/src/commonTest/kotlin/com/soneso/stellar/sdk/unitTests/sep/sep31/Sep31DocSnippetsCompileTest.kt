// Copyright 2026 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.unitTests.sep.sep31

import com.soneso.stellar.sdk.KeyPair
import com.soneso.stellar.sdk.MemoHash
import com.soneso.stellar.sdk.MemoId
import com.soneso.stellar.sdk.MemoText
import com.soneso.stellar.sdk.sep.sep12.CustomerStatus
import com.soneso.stellar.sdk.sep.sep12.GetCustomerInfoRequest
import com.soneso.stellar.sdk.sep.sep12.KYCService
import com.soneso.stellar.sdk.sep.sep09.NaturalPersonKYCFields
import com.soneso.stellar.sdk.sep.sep12.PutCustomerInfoRequest
import com.soneso.stellar.sdk.sep.sep09.StandardKYCFields
import com.soneso.stellar.sdk.sep.sep31.Sep31FeeDetails
import com.soneso.stellar.sdk.sep.sep31.Sep31FeeDetailsDetails
import com.soneso.stellar.sdk.sep.sep31.Sep31InfoResponse
import com.soneso.stellar.sdk.sep.sep31.Sep31PostTransactionsRequest
import com.soneso.stellar.sdk.sep.sep31.Sep31PostTransactionsResponse
import com.soneso.stellar.sdk.sep.sep31.Sep31ReceiveAssetInfo
import com.soneso.stellar.sdk.sep.sep31.Sep31RefundPayment
import com.soneso.stellar.sdk.sep.sep31.Sep31Refunds
import com.soneso.stellar.sdk.sep.sep31.Sep31Sep12TypesInfo
import com.soneso.stellar.sdk.sep.sep31.Sep31Service
import com.soneso.stellar.sdk.sep.sep31.Sep31TransactionResponse
import com.soneso.stellar.sdk.sep.sep31.Sep31TransactionStatus
import com.soneso.stellar.sdk.sep.sep31.exceptions.Sep31BadRequestException
import com.soneso.stellar.sdk.sep.sep31.exceptions.Sep31ConfigurationException
import com.soneso.stellar.sdk.sep.sep31.exceptions.Sep31CustomerInfoNeededException
import com.soneso.stellar.sdk.sep.sep31.exceptions.Sep31Exception
import com.soneso.stellar.sdk.sep.sep31.exceptions.Sep31ForbiddenException
import com.soneso.stellar.sdk.sep.sep31.exceptions.Sep31InvalidResponseException
import com.soneso.stellar.sdk.sep.sep31.exceptions.Sep31TransactionCallbackNotSupportedException
import com.soneso.stellar.sdk.sep.sep31.exceptions.Sep31TransactionInfoNeededException
import com.soneso.stellar.sdk.sep.sep31.exceptions.Sep31TransactionNotFoundException
import com.soneso.stellar.sdk.sep.sep31.exceptions.Sep31UnauthorizedException
import com.soneso.stellar.sdk.sep.sep31.exceptions.Sep31UnknownResponseException
import com.soneso.stellar.sdk.sep.sep38.QuoteService
import com.soneso.stellar.sdk.sep.sep38.Sep38QuoteRequest
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.delay
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json

/**
 * Compile-and-runtime verification for every Kotlin snippet in `docs/sep/sep-31.md`.
 * Every fenced kotlin block is mirrored here as a self-contained `@Test` so that
 * documentation drift (stale class names, wrong overload signatures, removed
 * parameters) breaks the build instead of silently misleading users.
 */
class Sep31DocSnippetsCompileTest {

    // ==================== Shared fixtures ====================

    private val anchorUrl = "https://anchor.example.org"
    private val jwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.sig"
    private val txId = "11111111-1111-1111-1111-111111111111"
    private val callbackUrl = "https://wallet.example.org/sep31-callback"
    private val jsonParser = Json { ignoreUnknownKeys = true; isLenient = true }

    private val infoJson = """
        {
          "receive": {
            "USDC": {
              "quotes_supported": true,
              "quotes_required": false,
              "fee_fixed": 5,
              "fee_percent": 1,
              "min_amount": 0.1,
              "max_amount": 1000,
              "funding_methods": ["SEPA", "SWIFT"],
              "sep12": {
                "sender": { "types": { "sep31-sender": { "description": "Senders" } } },
                "receiver": { "types": { "sep31-receiver": { "description": "Receivers" } } }
              }
            }
          }
        }
    """.trimIndent()

    private val postTxJson = """
        {
          "id": "11111111-1111-1111-1111-111111111111",
          "stellar_account_id": "GBRPYHIL2CI3FNQ4BXLFMNDLFJUNPU2HY3ZMFSHONUCEOASW7QC7OX2H",
          "stellar_memo_type": "id",
          "stellar_memo": "987654321"
        }
    """.trimIndent()

    // Completed transaction with self-consistent amount identities:
    //   amount_in (100) - amount_fee (10) - refunds.amount_refunded (0) - refunds.amount_fee (0) == amount_out (90)
    private val completedTxJson = """
        {
          "transaction": {
            "id": "11111111-1111-1111-1111-111111111111",
            "status": "completed",
            "amount_in": "100.00",
            "amount_out": "90.00",
            "amount_fee": "10.00",
            "fee_details": {
              "total": "10.00",
              "asset": "stellar:USDC:GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN",
              "details": [
                { "name": "Service fee", "amount": "8.00", "description": "Anchor service fee" },
                { "name": "BRL deposit fee", "amount": "2.00" }
              ]
            },
            "started_at": "2026-05-16T10:00:00Z",
            "completed_at": "2026-05-16T10:05:00Z",
            "stellar_account_id": "GBRPYHIL2CI3FNQ4BXLFMNDLFJUNPU2HY3ZMFSHONUCEOASW7QC7OX2H",
            "stellar_memo": "123456789",
            "stellar_memo_type": "id"
          }
        }
    """.trimIndent()

    private val partiallyRefundedTxJson = """
        {
          "transaction": {
            "id": "11111111-1111-1111-1111-111111111111",
            "status": "refunded",
            "amount_in": "100.00",
            "amount_out": "78.00",
            "amount_fee": "10.00",
            "fee_details": {
              "total": "10.00",
              "asset": "stellar:USDC:GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN",
              "details": [
                { "name": "Service fee", "amount": "10.00" }
              ]
            },
            "refunds": {
              "amount_refunded": "10.00",
              "amount_fee": "2.00",
              "payments": [
                { "id": "abc123", "amount": "10.00", "fee": "2.00" }
              ]
            }
          }
        }
    """.trimIndent()

    // ==================== Helpers ====================

    private fun mockClientFor(
        responseJson: String,
        status: HttpStatusCode = HttpStatusCode.OK,
        contentType: String = "application/json",
    ): HttpClient {
        val engine = MockEngine {
            respond(
                content = responseJson,
                status = status,
                headers = headersOf(HttpHeaders.ContentType, contentType),
            )
        }
        return HttpClient(engine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true })
            }
        }
    }

    /** Mock client that dispatches on request URL — used for snippets that touch /info plus another endpoint. */
    private fun mockMultiClient(
        handlers: Map<String, Pair<String, HttpStatusCode>>,
    ): HttpClient {
        val engine = MockEngine { request ->
            val path = request.url.encodedPath
            val match = handlers.entries.firstOrNull { path.endsWith(it.key) }
                ?: error("No mock handler for path: $path")
            respond(
                content = match.value.first,
                status = match.value.second,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        return HttpClient(engine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true })
            }
        }
    }

    // ==================== Snippet 1: Quick Example ====================

    @Test
    fun quickExample_compiles() = runTest {
        val client = mockMultiClient(
            mapOf(
                "/info" to (infoJson to HttpStatusCode.OK),
                "/transactions" to (postTxJson to HttpStatusCode.OK),
                "/transactions/$txId" to (completedTxJson to HttpStatusCode.OK),
            ),
        )

        suspend fun sendingAnchorQuickExample() {
            // Replaced fromDomain(...) with constructor + mock client to keep the test offline.
            val sep31 = Sep31Service(anchorUrl, client)
            val jwt = "eyJ..."

            val info = sep31.info(jwt = jwt)
            val usdc = info.receiveAssets["USDC"]
                ?: error("Receiving Anchor does not accept USDC")
            assertEquals(0.1, usdc.minAmount)

            val request = Sep31PostTransactionsRequest(
                amount = 100.0,
                assetCode = "USDC",
                fundingMethod = "SWIFT",
                senderId = "11111111-1111-1111-1111-111111111111",
                receiverId = "22222222-2222-2222-2222-222222222222",
            )
            val post = sep31.postTransactions(request, jwt)
            assertEquals(txId, post.id)

            val tx = sep31.getTransaction(post.id, jwt)
            assertEquals("completed", tx.status)
        }

        sendingAnchorQuickExample()
    }

    // ==================== Snippet 2: Creating the service (fromDomain) ====================

    @Test
    fun creatingService_constructorAndFromDomainSignatures_compile() = runTest {
        // Direct URL constructor with mock client.
        val client = mockClientFor(infoJson)
        val sep31: Sep31Service = Sep31Service("https://anchor.example.org/sep31", client)
        // Touch the public method so the call site stays in the test (information for compiler).
        sep31.info(jwt = "eyJ.touch.token")

        // fromDomain is a suspend factory — verify its signature compiles by referencing it.
        val factoryRef: suspend (String, HttpClient?, Map<String, String>?) -> Sep31Service =
            { d, c, h -> Sep31Service.fromDomain(d, c, h) }
        // Do not invoke — we are only validating the symbol exists with the expected shape.
        check(factoryRef !== null)
    }

    // ==================== Snippet 2a: HTTPS enforcement and loopback carve-out ====================

    @Test
    fun httpsCarveOut_productionAndLoopbackBothConstruct() = runTest {
        // Mirrors the doc snippet showing the three valid constructions: production
        // fromDomain, loopback fromDomain (TOML over http://), and direct loopback.
        // Production fromDomain is exercised by integration tests; the loopback
        // fromDomain path is exercised in Sep31ServiceTest with a MockEngine TOML.
        // This test only verifies the direct-construction signatures compile and
        // both URL shapes pass the constructor's HTTPS check.
        val anchor = Sep31Service("https://anchor.example.org/sep31")
        assertEquals("https://anchor.example.org/sep31", anchor.serviceUrl)

        val localDirect = Sep31Service("http://localhost:8080/sep31")
        assertEquals("http://localhost:8080/sep31", localDirect.serviceUrl)
    }

    // ==================== Snippet 2b: HTTP client defaults (override) ====================

    @Test
    fun httpClientDefaults_customClientOverride_compiles() = runTest {
        // Mirror of the doc snippet that builds a custom HttpClient and passes it to Sep31Service.
        // The function is defined inline to exercise type inference; the engine is left at its
        // default because we never invoke a network call in this test.
        fun createServiceWithCustomClient(): Sep31Service {
            val customClient = HttpClient {
                install(io.ktor.client.plugins.HttpTimeout) {
                    connectTimeoutMillis = 30_000
                    requestTimeoutMillis = 120_000
                }
                install(ContentNegotiation) {
                    json(Json { ignoreUnknownKeys = true; isLenient = true })
                }
                followRedirects = false
            }
            return Sep31Service("https://anchor.example.org", httpClient = customClient)
        }
        val sep31 = createServiceWithCustomClient()
        assertNotNull(sep31)
    }

    // ==================== Snippet 3: Getting anchor information (info) ====================

    @Test
    fun fetchInfo_compiles() = runTest {
        val client = mockClientFor(infoJson)
        val sep31 = Sep31Service(anchorUrl, client)
        val jwt = "eyJ..."

        val info = sep31.info(jwt = jwt, lang = "en")
        for ((code, asset) in info.receiveAssets) {
            // Touch every property referenced in the doc snippet.
            assertNotNull(code)
            val minAmount: Double? = asset.minAmount
            val maxAmount: Double? = asset.maxAmount
            val feeFixed: Double? = asset.feeFixed
            val feePercent: Double? = asset.feePercent
            val senderTypes: Map<String, String> = asset.sep12Info.senderTypes
            val receiverTypes: Map<String, String> = asset.sep12Info.receiverTypes
            assertNotNull(senderTypes)
            assertNotNull(receiverTypes)
            // No-op references to keep the compiler honest.
            check(minAmount != null && maxAmount != null && feeFixed != null && feePercent != null)
        }
    }

    // ==================== Snippet 4: Inspect funding methods and quote requirements ====================

    @Test
    fun inspectAssetCapabilities_compiles() = runTest {
        val client = mockClientFor(infoJson)
        val sep31 = Sep31Service(anchorUrl, client)
        val info = sep31.info(jwt = "eyJ...")
        val usdc = info.receiveAssets["USDC"] ?: return@runTest

        usdc.fundingMethods?.forEach { method: String ->
            assertNotNull(method)
        }

        when {
            usdc.quotesRequired == true -> {}
            usdc.quotesSupported == true -> {}
            else -> {}
        }
        assertEquals(true, usdc.quotesSupported)
    }

    // ==================== Snippet 5: Full payment flow ====================
    //
    // The doc snippet includes Stellar transaction submission (Horizon, KeyPair, TransactionBuilder).
    // The runtime test exercises only the SEP-31 portions; the Horizon path is exercised by the
    // separate Horizon test suites. This still validates every SEP-31 symbol the snippet uses.

    @OptIn(ExperimentalEncodingApi::class)
    @Test
    fun fullPaymentFlow_sep31SymbolsCompile() = runTest {
        val client = mockMultiClient(
            mapOf(
                "/info" to (infoJson to HttpStatusCode.OK),
                "/transactions" to (postTxJson to HttpStatusCode.OK),
                "/transactions/$txId" to (completedTxJson to HttpStatusCode.OK),
            ),
        )
        val sep31 = Sep31Service(anchorUrl, client)
        val jwt = "eyJ..."

        val info = sep31.info(jwt = jwt)
        val usdc = info.receiveAssets["USDC"]
            ?: error("Receiving Anchor does not accept USDC")

        val senderId = "11111111-1111-1111-1111-111111111111"
        val receiverId = "22222222-2222-2222-2222-222222222222"

        val quoteId: String? =
            if (usdc.quotesRequired == true) "33333333-3333-3333-3333-333333333333" else null

        val request = Sep31PostTransactionsRequest(
            amount = 100.0,
            assetCode = "USDC",
            senderId = senderId,
            receiverId = receiverId,
            quoteId = quoteId,
            fundingMethod = "SWIFT",
            refundMemo = "REFUND-INV-42",
            refundMemoType = "text",
        )
        val post = sep31.postTransactions(request, jwt)

        // Verify the memo-type dispatch logic from the doc snippet compiles for every branch.
        // The mock returns memo_type="id", so that arm is what executes against `post`.
        val destination = post.stellarAccountId
            ?: error("Anchor has not issued payment instructions yet; poll until pending_sender")
        assertNotNull(destination)
        val memo = when (post.stellarMemoType) {
            "id" -> MemoId(post.stellarMemo!!.toULong())
            "text" -> MemoText(post.stellarMemo!!)
            // Mirrors the doc snippet verbatim: stellar_memo for memo_type="hash" is base64-encoded
            // (32 raw bytes after decoding). MemoHash(String) parses hex, so the payload must be
            // base64-decoded into ByteArray first.
            "hash" -> MemoHash(Base64.decode(post.stellarMemo!!))
            else -> error("Unsupported memo type: ${post.stellarMemoType}")
        }
        assertNotNull(memo)

        // Exercise the "hash" branch at runtime against a synthetic response that mirrors the
        // exact `stellar_memo` shape the spec defines (32-byte base64). This catches future
        // regressions where the doc-pattern would throw against a real anchor.
        val hashPost = Sep31PostTransactionsResponse(
            id = txId,
            stellarAccountId = "GBRPYHIL2CI3FNQ4BXLFMNDLFJUNPU2HY3ZMFSHONUCEOASW7QC7OX2H",
            stellarMemoType = "hash",
            stellarMemo = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        )
        val hashMemo = when (hashPost.stellarMemoType) {
            "id" -> MemoId(hashPost.stellarMemo!!.toULong())
            "text" -> MemoText(hashPost.stellarMemo!!)
            "hash" -> MemoHash(Base64.decode(hashPost.stellarMemo!!))
            else -> error("Unsupported memo type: ${hashPost.stellarMemoType}")
        }
        assertNotNull(hashMemo)
        assertEquals(32, (hashMemo as MemoHash).bytes.size)

        // KeyPair reference from the snippet — verify the factory exists.
        val kp: KeyPair = KeyPair.fromSecretSeed(
            "SCH27VUZZ6UAKB67BDNF6FA42YMBMQCBKXWGMFD5TZ6S5ZZCZFLRXKHS",
        )
        assertNotNull(kp.getAccountId())

        val current = sep31.getTransaction(post.id, jwt)
        assertEquals("completed", current.status)
    }

    // ==================== Snippet 6: Tracking transaction status (handleStatus) ====================

    @Test
    fun handleStatus_allEnumBranchesCompile() = runTest {
        val client = mockClientFor(completedTxJson)
        val sep31 = Sep31Service(anchorUrl, client)

        suspend fun handleStatus(
            sep31: Sep31Service,
            txId: String,
            jwt: String,
        ): Sep31TransactionResponse {
            val response = sep31.getTransaction(txId, jwt)
            // Exhaustive when over all 10 enum values plus null — verified at compile time.
            when (Sep31TransactionStatus.fromString(response.status)) {
                Sep31TransactionStatus.PENDING_SENDER -> {}
                Sep31TransactionStatus.PENDING_STELLAR -> {}
                Sep31TransactionStatus.PENDING_CUSTOMER_INFO_UPDATE -> {}
                Sep31TransactionStatus.PENDING_TRANSACTION_INFO_UPDATE -> {}
                Sep31TransactionStatus.PENDING_RECEIVER -> {}
                Sep31TransactionStatus.PENDING_EXTERNAL -> {}
                Sep31TransactionStatus.COMPLETED -> {}
                Sep31TransactionStatus.REFUNDED -> {}
                Sep31TransactionStatus.EXPIRED -> {}
                Sep31TransactionStatus.ERROR -> {}
                null -> {}
            }
            return response
        }

        val response = handleStatus(sep31, txId, jwt)
        assertEquals(Sep31TransactionStatus.COMPLETED, Sep31TransactionStatus.fromString(response.status))
    }

    // ==================== Snippet 7: Amount-formula identities ====================

    @Test
    fun amountIdentities_completedTransaction_pass() = runTest {
        val parsedJson = jsonParser.decodeFromString(
            kotlinx.serialization.json.JsonObject.serializer(),
            completedTxJson,
        )
        val tx = Sep31TransactionResponse.fromJson(parsedJson)

        // Mirror the doc snippet's identity-checking function.
        val tolerance = 1e-9
        val amountIn = tx.amountIn!!.toDouble()
        val amountOut = tx.amountOut!!.toDouble()
        val totalFee = tx.feeDetails?.total?.toDouble()
            ?: error("fee_details required to recompute amount_out")
        val refundedAmount = tx.refunds?.amountRefunded?.toDouble() ?: 0.0
        val refundFee = tx.refunds?.amountFee?.toDouble() ?: 0.0
        val recomputedOut = amountIn - totalFee - refundedAmount - refundFee
        assertTrue(abs(recomputedOut - amountOut) < tolerance, "amount_out identity must hold")
    }

    @Test
    fun amountIdentities_partiallyRefundedTransaction_pass() = runTest {
        val parsedJson = jsonParser.decodeFromString(
            kotlinx.serialization.json.JsonObject.serializer(),
            partiallyRefundedTxJson,
        )
        val tx = Sep31TransactionResponse.fromJson(parsedJson)

        val tolerance = 1e-9
        val amountIn = tx.amountIn!!.toDouble()
        val amountOut = tx.amountOut!!.toDouble()
        val totalFee = tx.feeDetails!!.total.toDouble()
        val refundedAmount = tx.refunds!!.amountRefunded.toDouble()
        val refundFee = tx.refunds!!.amountFee.toDouble()
        val recomputedOut = amountIn - totalFee - refundedAmount - refundFee
        assertTrue(abs(recomputedOut - amountOut) < tolerance, "amount_out identity must hold")

        // refunds.amount_refunded = sum(payments[].amount)
        val sumAmounts = tx.refunds!!.payments.sumOf { it.amount.toDouble() }
        assertTrue(abs(sumAmounts - tx.refunds!!.amountRefunded.toDouble()) < tolerance)

        // refunds.amount_fee = sum(payments[].fee)
        val sumFees = tx.refunds!!.payments.sumOf { it.fee.toDouble() }
        assertTrue(abs(sumFees - tx.refunds!!.amountFee.toDouble()) < tolerance)
    }

    // ==================== Snippet 8: Fee breakdown ====================

    @Test
    fun printFeeBreakdown_compiles() = runTest {
        val parsedJson = jsonParser.decodeFromString(
            kotlinx.serialization.json.JsonObject.serializer(),
            completedTxJson,
        )
        val tx = Sep31TransactionResponse.fromJson(parsedJson)

        val fees: Sep31FeeDetails = tx.feeDetails!!
        assertNotNull(fees.total)
        assertNotNull(fees.asset)
        for (line: Sep31FeeDetailsDetails in fees.details.orEmpty()) {
            val description = line.description ?: "no description"
            // Touch every property the snippet references.
            assertNotNull(line.name)
            assertNotNull(line.amount)
            assertNotNull(description)
        }
        assertEquals(2, fees.details!!.size)
    }

    // ==================== Snippet 9: Refund handling ====================

    @Test
    fun printRefunds_compiles() = runTest {
        val parsedJson = jsonParser.decodeFromString(
            kotlinx.serialization.json.JsonObject.serializer(),
            partiallyRefundedTxJson,
        )
        val tx = Sep31TransactionResponse.fromJson(parsedJson)

        val refunds: Sep31Refunds = tx.refunds!!
        assertNotNull(refunds.amountRefunded)
        assertNotNull(refunds.amountFee)
        for (payment: Sep31RefundPayment in refunds.payments) {
            assertNotNull(payment.id)
            assertNotNull(payment.amount)
            assertNotNull(payment.fee)
        }
    }

    // ==================== Snippet 10: Registering a callback ====================

    @Test
    fun registerCallback_compiles() = runTest {
        // Mock returns 204 No Content for the PUT callback success path.
        val engine = MockEngine {
            respond(content = "", status = HttpStatusCode.NoContent, headers = headersOf())
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; isLenient = true }) }
        }
        val sep31 = Sep31Service(anchorUrl, client)

        try {
            sep31.putTransactionCallback(id = txId, callbackUrl = callbackUrl, jwt = jwt)
        } catch (e: Sep31TransactionCallbackNotSupportedException) {
            // The 404 arm — unreachable here but must compile.
            fail("Unexpected: ${e.message}")
        }
    }

    @Test
    fun registerCallback_handlesCallbackNotSupported() = runTest {
        // Mock returns 404 to exercise the catch arm.
        val engine = MockEngine {
            respond(
                content = """{"error":"Callback not supported"}""",
                status = HttpStatusCode.NotFound,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; isLenient = true }) }
        }
        val sep31 = Sep31Service(anchorUrl, client)

        assertFailsWith<Sep31TransactionCallbackNotSupportedException> {
            sep31.putTransactionCallback(id = txId, callbackUrl = callbackUrl, jwt = jwt)
        }
    }

    // ==================== Snippet 11: Verifying callback signatures ====================

    @OptIn(ExperimentalEncodingApi::class, ExperimentalTime::class)
    @Test
    fun verifyCallback_freshSignature_returnsTrue() = runTest {
        // Generate a keypair that signs a payload matching the verifier's canonical form.
        val anchorKey: KeyPair = KeyPair.random()
        val signingAccountId = anchorKey.getAccountId()
        val body = """{"transaction":{"id":"$txId","status":"completed"}}"""
        val registeredCallbackUrl = "https://wallet.example.org/sep31-callback"
        val host = Url(registeredCallbackUrl).host
        val timestamp: Long = Clock.System.now().epochSeconds
        val payload = "$timestamp.$host.$body".encodeToByteArray()
        val signature = anchorKey.sign(payload)
        val header = "t=$timestamp, s=${Base64.encode(signature)}"

        // Define the verifier inline — mirrors the doc snippet body verbatim.
        suspend fun verifyCallback(
            signingKey: String,
            signatureHeader: String?,
            xStellarSignatureHeader: String?,
            body: String,
        ): Boolean {
            val verifier = com.soneso.stellar.sdk.sep.common.CallbackSignatureVerifier(
                signingKey = signingKey,
                registeredCallbackUrl = "https://wallet.example.org/sep31-callback",
            )

            return when (val result = verifier.verify(signatureHeader, xStellarSignatureHeader, body)) {
                com.soneso.stellar.sdk.sep.common.CallbackSignatureVerifier.Result.Valid -> true
                is com.soneso.stellar.sdk.sep.common.CallbackSignatureVerifier.Result.Stale -> {
                    println("Callback stale by ${result.ageSeconds}s")
                    false
                }
                com.soneso.stellar.sdk.sep.common.CallbackSignatureVerifier.Result.SignatureMismatch,
                com.soneso.stellar.sdk.sep.common.CallbackSignatureVerifier.Result.MalformedHeader,
                com.soneso.stellar.sdk.sep.common.CallbackSignatureVerifier.Result.MissingHeader -> false
            }
        }

        val result = verifyCallback(
            signingKey = signingAccountId,
            signatureHeader = header,
            xStellarSignatureHeader = null,
            body = body,
        )
        assertTrue(result, "valid fresh signature must verify")
    }

    @OptIn(ExperimentalEncodingApi::class, ExperimentalTime::class)
    @Test
    fun verifyCallback_staleSignature_returnsFalse() = runTest {
        val anchorKey = KeyPair.random()
        val body = """{"transaction":{"id":"$txId","status":"completed"}}"""
        val registeredCallbackUrl = "https://wallet.example.org/sep31-callback"
        val host = Url(registeredCallbackUrl).host
        val staleTimestamp = Clock.System.now().epochSeconds - 300 // 5 minutes ago
        val payload = "$staleTimestamp.$host.$body".encodeToByteArray()
        val signature = anchorKey.sign(payload)
        val header = "t=$staleTimestamp, s=${Base64.encode(signature)}"

        suspend fun verifyCallback(
            signingKey: String,
            signatureHeader: String?,
            xStellarSignatureHeader: String?,
            body: String,
        ): Boolean {
            val verifier = com.soneso.stellar.sdk.sep.common.CallbackSignatureVerifier(
                signingKey = signingKey,
                registeredCallbackUrl = "https://wallet.example.org/sep31-callback",
            )

            return when (val result = verifier.verify(signatureHeader, xStellarSignatureHeader, body)) {
                com.soneso.stellar.sdk.sep.common.CallbackSignatureVerifier.Result.Valid -> true
                is com.soneso.stellar.sdk.sep.common.CallbackSignatureVerifier.Result.Stale -> {
                    println("Callback stale by ${result.ageSeconds}s")
                    false
                }
                com.soneso.stellar.sdk.sep.common.CallbackSignatureVerifier.Result.SignatureMismatch,
                com.soneso.stellar.sdk.sep.common.CallbackSignatureVerifier.Result.MalformedHeader,
                com.soneso.stellar.sdk.sep.common.CallbackSignatureVerifier.Result.MissingHeader -> false
            }
        }

        val result = verifyCallback(
            signingKey = anchorKey.getAccountId(),
            signatureHeader = header,
            xStellarSignatureHeader = null,
            body = body,
        )
        assertEquals(false, result, "stale signature must be rejected")
    }

    // ==================== Snippet 12: Error matrix (every exception type referenced) ====================

    @Test
    fun errorMatrix_referencesAllSep31Exceptions_compiles() = runTest {
        // The doc snippet's try/catch references every Sep31* exception type. Verify each
        // is in scope and assignment-compatible with Sep31Exception. We do not need to
        // exercise every path at runtime — that is covered by Sep31ServiceTest. This block
        // exists to break the build if any exception class is renamed or removed.
        val exceptions: List<Sep31Exception> = listOf(
            Sep31ConfigurationException("config"),
            Sep31BadRequestException("bad"),
            Sep31CustomerInfoNeededException(type = "sep31-sender"),
            @Suppress("DEPRECATION")
            Sep31TransactionInfoNeededException(fields = mapOf("k" to "v")),
            Sep31UnauthorizedException("unauth"),
            Sep31ForbiddenException("forbidden"),
            Sep31TransactionCallbackNotSupportedException("callback"),
            Sep31TransactionNotFoundException("not found"),
            Sep31InvalidResponseException("invalid"),
            Sep31UnknownResponseException("unknown", statusCode = 500, responseBody = ""),
        )
        assertEquals(10, exceptions.size)
    }

    // ==================== Snippet 12b: rawResponseBody escape hatch ====================

    @Test
    fun rawResponseBodyEscapeHatch_compiles() = runTest {
        // Mirrors the doc snippet that demonstrates how to read rawResponseBody for
        // local debugging when an anchor echoes a token. The mock returns a 400 body
        // containing a JWT-shaped substring so the rawResponseBody field is non-null
        // and the assertion verifies the field type and null-safety pattern compile.
        val realJwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.sig"
        val errorBody = """{"error":"invalid token $realJwt"}"""
        val engine = MockEngine {
            respond(
                content = errorBody,
                status = HttpStatusCode.BadRequest,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true })
            }
        }
        val sep31 = Sep31Service(anchorUrl, client)
        val localDebugMode = true

        var captured: String? = null
        try {
            sep31.postTransactions(Sep31PostTransactionsRequest(100.0, "USDC", "SWIFT"), "eyJ...")
        } catch (e: Sep31BadRequestException) {
            // Safe in production.
            assertNotNull(e.message)
            // Debugging only.
            if (localDebugMode) {
                captured = e.rawResponseBody
            }
        }
        assertNotNull(captured)
        assertTrue(captured!!.contains(realJwt), "rawResponseBody must preserve JWT for debugging")
    }

    // ==================== Snippet 13: Deprecated patchTransaction ====================

    @Test
    fun legacyPatch_compiles() = runTest {
        val client = mockClientFor(completedTxJson)
        val sep31 = Sep31Service(anchorUrl, client)

        @Suppress("DEPRECATION")
        val updated = sep31.patchTransaction(
            id = txId,
            fields = mapOf(
                "transaction" to mapOf("receiver_account_number" to "0987654321"),
            ),
            jwt = jwt,
        )
        assertEquals("completed", updated.status)
    }

    // ==================== Snippets 14-15: SDK class table sanity (types are constructible/parseable) ====================

    @Test
    fun sdkClassesTable_typesExistAndConstruct() = runTest {
        // Sep31InfoResponse / Sep31ReceiveAssetInfo / Sep31Sep12TypesInfo
        val infoObject = jsonParser.decodeFromString(
            kotlinx.serialization.json.JsonObject.serializer(),
            infoJson,
        )
        val info: Sep31InfoResponse = Sep31InfoResponse.fromJson(infoObject)
        val usdc: Sep31ReceiveAssetInfo = info.receiveAssets.getValue("USDC")
        val typesInfo: Sep31Sep12TypesInfo = usdc.sep12Info
        assertNotNull(typesInfo.senderTypes)
        assertNotNull(typesInfo.receiverTypes)

        // Sep31PostTransactionsResponse parses minimal body
        val postObject = jsonParser.decodeFromString(
            kotlinx.serialization.json.JsonObject.serializer(),
            postTxJson,
        )
        val post: Sep31PostTransactionsResponse = Sep31PostTransactionsResponse.fromJson(postObject)
        assertEquals(txId, post.id)

        // Sep31FeeDetails / Sep31FeeDetailsDetails / Sep31Refunds / Sep31RefundPayment
        val txObject = jsonParser.decodeFromString(
            kotlinx.serialization.json.JsonObject.serializer(),
            partiallyRefundedTxJson,
        )
        val tx: Sep31TransactionResponse = Sep31TransactionResponse.fromJson(txObject)
        val fees: Sep31FeeDetails = tx.feeDetails!!
        val feeLine: Sep31FeeDetailsDetails = fees.details!!.first()
        val refunds: Sep31Refunds = tx.refunds!!
        val refundPayment: Sep31RefundPayment = refunds.payments.first()
        assertNotNull(feeLine.name)
        assertNotNull(refundPayment.id)
    }

    // ==================== Snippet 16: SEP-12 sender/receiver registration ====================

    private val sep12PutCustomerJson = """{ "id": "11111111-1111-1111-1111-111111111111" }"""

    @Test
    fun registerCustomers_compiles() = runTest {
        // The PUT /customer endpoint is shared; the SDK distinguishes sender vs receiver
        // by the request body's `type` field, not the URL.
        val engine = MockEngine {
            respond(
                content = sep12PutCustomerJson,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true })
            }
        }
        val kyc = KYCService("https://anchor.example.org", client)

        suspend fun registerCustomers(jwt: String): Pair<String, String> {
            val sender = kyc.putCustomerInfo(
                PutCustomerInfoRequest(
                    jwt = jwt,
                    type = "sep31-sender",
                    kycFields = StandardKYCFields(
                        naturalPersonKYCFields = NaturalPersonKYCFields(
                            firstName = "Alice",
                            lastName = "Doe",
                            emailAddress = "alice@example.com",
                        ),
                    ),
                ),
            )
            val receiver = kyc.putCustomerInfo(
                PutCustomerInfoRequest(
                    jwt = jwt,
                    type = "sep31-receiver",
                    kycFields = StandardKYCFields(
                        naturalPersonKYCFields = NaturalPersonKYCFields(
                            firstName = "Bob",
                            lastName = "Smith",
                            emailAddress = "bob@example.com",
                        ),
                    ),
                ),
            )
            return sender.id to receiver.id
        }

        val (senderId, receiverId) = registerCustomers("eyJ...")
        assertEquals("11111111-1111-1111-1111-111111111111", senderId)
        assertEquals("11111111-1111-1111-1111-111111111111", receiverId)
    }

    // ==================== Snippet 17: SEP-38 firm quote acquisition ====================

    private val sep38QuoteJson = """
        {
          "id": "33333333-3333-3333-3333-333333333333",
          "expires_at": "2099-12-31T23:59:59Z",
          "total_price": "1.00",
          "price": "1.00",
          "sell_amount": "100.00",
          "buy_amount": "100.00",
          "sell_asset": "iso4217:USD",
          "buy_asset": "stellar:USDC:GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN",
          "fee": {
            "total": "0.00",
            "asset": "iso4217:USD"
          }
        }
    """.trimIndent()

    @Test
    fun acquireQuote_compiles() = runTest {
        val engine = MockEngine {
            respond(
                content = sep38QuoteJson,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true })
            }
        }
        val quotes = QuoteService("https://anchor.example.org", client)

        suspend fun acquireQuote(jwt: String): String {
            val quote = quotes.postQuote(
                Sep38QuoteRequest(
                    context = "sep31",
                    sellAsset = "iso4217:USD",
                    buyAsset = "stellar:USDC:GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN",
                    sellAmount = "100.00",
                ),
                jwt,
            )
            return quote.id
        }

        val quoteId = acquireQuote("eyJ...")
        assertEquals("33333333-3333-3333-3333-333333333333", quoteId)
    }

    // ==================== Snippet 18: Polling with statusEta-based backoff ====================

    @Test
    fun pollUntilTerminal_returnsImmediatelyOnCompleted() = runTest {
        // The mock returns the terminal "completed" body on every call, so the loop
        // exits on the first iteration; runTest's virtual clock keeps the delay()
        // calls instant when the loop does not short-circuit.
        val client = mockClientFor(completedTxJson)
        val sep31 = Sep31Service(anchorUrl, client)

        val terminalStatuses = setOf(
            Sep31TransactionStatus.COMPLETED,
            Sep31TransactionStatus.REFUNDED,
            Sep31TransactionStatus.EXPIRED,
            Sep31TransactionStatus.ERROR,
        )

        suspend fun pollUntilTerminal(
            sep31: Sep31Service,
            txId: String,
            jwt: String,
        ): Sep31TransactionResponse {
            var backoffMs = 2_000L
            val ceilingMs = 60_000L
            while (true) {
                val tx = sep31.getTransaction(txId, jwt)
                if (Sep31TransactionStatus.fromString(tx.status) in terminalStatuses) {
                    return tx
                }
                val waitMs = tx.statusEta?.let { (it * 1000L).coerceAtLeast(1_000L) } ?: backoffMs
                delay(waitMs)
                backoffMs = (backoffMs * 2).coerceAtMost(ceilingMs)
            }
            // Kotlin cannot infer that the while(true) is unreachable past return,
            // but the compiler is happy with the explicit return above.
        }

        val terminal = pollUntilTerminal(sep31, txId, jwt)
        assertEquals("completed", terminal.status)
    }

    // ==================== Snippet 19: KYC update round-trip ====================

    private val sep12NeedsInfoJson = """
        {
          "id": "22222222-2222-2222-2222-222222222222",
          "status": "NEEDS_INFO",
          "fields": {
            "address": {
              "type": "string",
              "description": "Sender postal address required by Receiving Anchor"
            }
          }
        }
    """.trimIndent()

    @Test
    fun resolveCustomerInfoUpdate_putsFieldsWhenNeedsInfo() = runTest {
        // Single MockEngine routes GET /customer to the NEEDS_INFO body and PUT /customer
        // to the canonical { "id": ... } success body.
        val engine = MockEngine { request: HttpRequestData ->
            val body = when (request.method) {
                HttpMethod.Get -> sep12NeedsInfoJson
                HttpMethod.Put -> sep12PutCustomerJson
                else -> error("Unexpected HTTP method: ${request.method}")
            }
            respond(
                content = body,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true })
            }
        }
        val kyc = KYCService("https://anchor.example.org", client)

        suspend fun resolveCustomerInfoUpdate(
            kyc: KYCService,
            customerId: String,
            transactionId: String,
            jwt: String,
        ) {
            val needs = kyc.getCustomerInfo(
                GetCustomerInfoRequest(jwt = jwt, id = customerId, transactionId = transactionId),
            )
            if (needs.status != CustomerStatus.NEEDS_INFO) return

            val collected = NaturalPersonKYCFields(
                address = "123 Main Street",
                city = "San Francisco",
                addressCountryCode = "USA",
            )
            kyc.putCustomerInfo(
                PutCustomerInfoRequest(
                    jwt = jwt,
                    id = customerId,
                    transactionId = transactionId,
                    kycFields = StandardKYCFields(naturalPersonKYCFields = collected),
                ),
            )
        }

        resolveCustomerInfoUpdate(
            kyc,
            customerId = "22222222-2222-2222-2222-222222222222",
            transactionId = txId,
            jwt = "eyJ...",
        )
    }
}
