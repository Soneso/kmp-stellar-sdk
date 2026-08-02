//
//  RelayerClientTest.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.unitTests.smartaccount.oz

import com.soneso.stellar.sdk.unitTests.smartaccount.createMockClient
import com.soneso.stellar.sdk.unitTests.smartaccount.createThrowingMockClient
import com.soneso.stellar.sdk.smartaccount.core.*
import com.soneso.stellar.sdk.smartaccount.oz.*
import com.soneso.stellar.sdk.xdr.*
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RelayerClientTest {

    // Reusable test XDR fixtures

    private fun createTestHostFunction(): HostFunctionXdr {
        val contractAddress = SCAddressXdr.ContractId(
            ContractIDXdr(HashXdr(ByteArray(32)))
        )
        return HostFunctionXdr.InvokeContract(
            InvokeContractArgsXdr(
                contractAddress = contractAddress,
                functionName = SCSymbolXdr("hello"),
                args = listOf(SCValXdr.Sym(SCSymbolXdr("world")))
            )
        )
    }

    private fun createTestAuthEntry(): SorobanAuthorizationEntryXdr {
        val contractAddress = SCAddressXdr.ContractId(
            ContractIDXdr(HashXdr(ByteArray(32)))
        )
        return SorobanAuthorizationEntryXdr(
            credentials = SorobanCredentialsXdr.Void,
            rootInvocation = SorobanAuthorizedInvocationXdr(
                function = SorobanAuthorizedFunctionXdr.ContractFn(
                    InvokeContractArgsXdr(
                        contractAddress = contractAddress,
                        functionName = SCSymbolXdr("hello"),
                        args = emptyList()
                    )
                ),
                subInvocations = emptyList()
            )
        )
    }

    private fun createTestTransactionEnvelope(): TransactionEnvelopeXdr {
        val sourceAccount = MuxedAccountXdr.Ed25519(Uint256Xdr(ByteArray(32)))
        val tx = TransactionXdr(
            sourceAccount = sourceAccount,
            fee = Uint32Xdr(100u),
            seqNum = SequenceNumberXdr(Int64Xdr(1L)),
            cond = PreconditionsXdr.Void,
            memo = MemoXdr.Void,
            operations = listOf(
                OperationXdr(
                    sourceAccount = null,
                    body = OperationBodyXdr.BumpSequenceOp(
                        BumpSequenceOpXdr(
                            bumpTo = SequenceNumberXdr(Int64Xdr(1L))
                        )
                    )
                )
            ),
            ext = TransactionExtXdr.Void
        )
        return TransactionEnvelopeXdr.V1(
            TransactionV1EnvelopeXdr(
                tx = tx,
                signatures = emptyList()
            )
        )
    }

    companion object {
        private fun createCapturingMockClient(
            responseBody: String,
            statusCode: HttpStatusCode = HttpStatusCode.OK,
            onRequest: (String) -> Unit
        ): HttpClient {
            return HttpClient(MockEngine) {
                install(ContentNegotiation) {
                    json(Json { ignoreUnknownKeys = true; isLenient = true })
                }
                engine {
                    addHandler { request ->
                        val body = request.body.toByteArray().decodeToString()
                        onRequest(body)
                        respond(
                            content = responseBody,
                            status = statusCode,
                            headers = headersOf(HttpHeaders.ContentType, "application/json")
                        )
                    }
                }
            }
        }

        /**
         * Responds with a body shorter than the advertised Content-Length, which is how a
         * connection dropped mid-response looks to the client: the status line and headers
         * arrive, but reading the body fails.
         */
        private fun createTruncatedBodyMockClient(): HttpClient {
            return HttpClient(MockEngine) {
                install(ContentNegotiation) {
                    json(Json { ignoreUnknownKeys = true; isLenient = true })
                }
                engine {
                    addHandler {
                        respond(
                            content = ByteReadChannel("""{"success": true"""),
                            status = HttpStatusCode.OK,
                            headers = headersOf(
                                HttpHeaders.ContentType to listOf("application/json"),
                                HttpHeaders.ContentLength to listOf("4096")
                            )
                        )
                    }
                }
            }
        }
    }

    // Malformed XDR fixtures: a 4-byte value where the XDR schema demands a 32-byte
    // fixed opaque. Encoding throws, so these exercise the client's encode-failure
    // paths without any network round-trip.

    private fun createUnencodableHostFunction(): HostFunctionXdr {
        return HostFunctionXdr.InvokeContract(
            InvokeContractArgsXdr(
                contractAddress = SCAddressXdr.ContractId(ContractIDXdr(HashXdr(ByteArray(4)))),
                functionName = SCSymbolXdr("hello"),
                args = emptyList()
            )
        )
    }

    private fun createUnencodableAuthEntry(): SorobanAuthorizationEntryXdr {
        return SorobanAuthorizationEntryXdr(
            credentials = SorobanCredentialsXdr.Void,
            rootInvocation = SorobanAuthorizedInvocationXdr(
                function = SorobanAuthorizedFunctionXdr.ContractFn(
                    InvokeContractArgsXdr(
                        contractAddress = SCAddressXdr.ContractId(ContractIDXdr(HashXdr(ByteArray(4)))),
                        functionName = SCSymbolXdr("hello"),
                        args = emptyList()
                    )
                ),
                subInvocations = emptyList()
            )
        )
    }

    private fun createUnencodableTransactionEnvelope(): TransactionEnvelopeXdr {
        val tx = TransactionXdr(
            sourceAccount = MuxedAccountXdr.Ed25519(Uint256Xdr(ByteArray(4))),
            fee = Uint32Xdr(100u),
            seqNum = SequenceNumberXdr(Int64Xdr(1L)),
            cond = PreconditionsXdr.Void,
            memo = MemoXdr.Void,
            operations = emptyList(),
            ext = TransactionExtXdr.Void
        )
        return TransactionEnvelopeXdr.V1(
            TransactionV1EnvelopeXdr(tx = tx, signatures = emptyList())
        )
    }

    // MARK: - Constructor validation

    @Test
    fun testConstructor_blankUrl_throwsConfigurationException() {
        assertFailsWith<ConfigurationException.InvalidConfig> {
            OZRelayerClient(relayerUrl = "")
        }
    }

    @Test
    fun testConstructor_whitespaceUrl_throwsConfigurationException() {
        assertFailsWith<ConfigurationException.InvalidConfig> {
            OZRelayerClient(relayerUrl = "   ")
        }
    }

    @Test
    fun testConstructor_httpUrl_throwsConfigurationException() {
        assertFailsWith<ConfigurationException.InvalidConfig> {
            OZRelayerClient(relayerUrl = "http://relayer.example.com")
        }
    }

    @Test
    fun testConstructor_httpsUrlSucceeds() {
        val client = OZRelayerClient(relayerUrl = "https://relayer.example.com")
        assertNotNull(client)
        client.close()
    }

    @Test
    fun testConstructor_localhostHttpUrlSucceeds() {
        val client = OZRelayerClient(relayerUrl = "http://localhost:3000")
        assertNotNull(client)
        client.close()
    }

    @Test
    fun testConstructor_localhostWithoutPortSucceeds() {
        val client = OZRelayerClient(relayerUrl = "http://localhost")
        assertNotNull(client)
        client.close()
    }

    @Test
    fun testConstructor_trailingSlashNormalization() = runTest {
        var capturedUrl: String? = null
        val mockClient = HttpClient(MockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true })
            }
            engine {
                addHandler { request ->
                    capturedUrl = request.url.toString()
                    respond(
                        content = """{"success": true, "data": {"hash": "abc123"}}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
            }
        }
        try {
            val relayer = OZRelayerClient(
                relayerUrl = "https://relayer.example.com///",
                injectedClient = mockClient
            )

            relayer.send(createTestHostFunction(), listOf(createTestAuthEntry()))

            // Constructing the client is not enough: the trailing slashes must be gone from
            // the URL actually put on the wire.
            val url = assertNotNull(capturedUrl, "The relayer request must reach the engine")
            assertTrue(
                url.startsWith("https://relayer.example.com"),
                "The host must be preserved; got: $url"
            )
            assertFalse(
                url.removePrefix("https://").contains("//"),
                "Trailing slashes must be stripped before the request URL is built; got: $url"
            )

            relayer.close()
        } finally {
            mockClient.close()
        }
    }

    @Test
    fun testConstructor_ftpSchemeThrows() {
        assertFailsWith<ConfigurationException.InvalidConfig> {
            OZRelayerClient(relayerUrl = "ftp://relayer.example.com")
        }
    }

    @Test
    fun testConstructor_noSchemeThrows() {
        assertFailsWith<ConfigurationException.InvalidConfig> {
            OZRelayerClient(relayerUrl = "relayer.example.com")
        }
    }

    @Test
    fun testConstructor_customTimeoutIsAccepted() {
        val client = OZRelayerClient(
            relayerUrl = "https://relayer.example.com",
            timeoutMs = 10_000L
        )
        assertNotNull(client)
        client.close()
    }

    // MARK: - send: success

    @Test
    fun testSend_success_returnsHash() = runTest {
        val mockClient = createMockClient(
            """{"success": true, "data": {"hash": "abc123", "transactionId": "tx-001", "status": "PENDING"}}"""
        )
        try {
            val relayer = OZRelayerClient(
                relayerUrl = "https://relayer.example.com",
                injectedClient = mockClient
            )

            val response = relayer.send(createTestHostFunction(), listOf(createTestAuthEntry()))

            assertTrue(response.success)
            assertEquals("abc123", response.hash)
            assertEquals("tx-001", response.transactionId)
            assertEquals("PENDING", response.status)

            relayer.close()
        } finally {
            mockClient.close()
        }
    }

    @Test
    fun testSend_buildsCorrectJsonPayload() = runTest {
        var capturedBody: String? = null

        val mockClient = createCapturingMockClient(
            """{"success": true, "data": {"hash": "abc123"}}"""
        ) { capturedBody = it }

        try {
            val relayer = OZRelayerClient(
                relayerUrl = "https://relayer.example.com",
                injectedClient = mockClient
            )

            val response = relayer.send(createTestHostFunction(), listOf(createTestAuthEntry()))

            assertTrue(response.success)
            assertEquals("abc123", response.hash)

            assertNotNull(capturedBody)
            val json = Json.parseToJsonElement(capturedBody!!).jsonObject

            assertTrue(json.containsKey("func"))
            val funcValue = json["func"]!!.jsonPrimitive.content
            assertTrue(funcValue.isNotEmpty())

            assertTrue(json.containsKey("auth"))
            val authArray = json["auth"]!!.jsonArray
            assertEquals(1, authArray.size)
            val authEntryBase64 = authArray[0].jsonPrimitive.content
            assertTrue(authEntryBase64.isNotEmpty())

            relayer.close()
        } finally {
            mockClient.close()
        }
    }

    @Test
    fun testSend_errorResponse_returnsErrorWithCode() = runTest {
        val mockClient = createMockClient(
            responseBody = """{"success": false, "error": "simulation failed", "code": "SIMULATION_FAILED"}""",
            statusCode = HttpStatusCode.BadRequest
        )
        try {
            val relayer = OZRelayerClient(
                relayerUrl = "https://relayer.example.com",
                injectedClient = mockClient
            )

            val response = relayer.send(createTestHostFunction(), listOf(createTestAuthEntry()))

            assertFalse(response.success)
            assertEquals("simulation failed", response.error)
            assertEquals("SIMULATION_FAILED", response.errorCode)

            relayer.close()
        } finally {
            mockClient.close()
        }
    }

    @Test
    fun testSend_errorWithNestedDataCode_returnsCorrectErrorCode() = runTest {
        val mockClient = createMockClient(
            responseBody = """{"success": false, "error": "tx failed", "data": {"code": "ONCHAIN_FAILED", "details": "..."}}""",
            statusCode = HttpStatusCode.BadRequest
        )
        try {
            val relayer = OZRelayerClient(
                relayerUrl = "https://relayer.example.com",
                injectedClient = mockClient
            )

            val response = relayer.send(createTestHostFunction(), listOf(createTestAuthEntry()))

            assertFalse(response.success)
            assertEquals("tx failed", response.error)
            assertEquals("ONCHAIN_FAILED", response.errorCode)

            relayer.close()
        } finally {
            mockClient.close()
        }
    }

    @Test
    fun testSend_nonJsonResponse_returnsError() = runTest {
        val mockClient = createMockClient(
            responseBody = "<html>Bad Gateway</html>",
            contentType = "text/html"
        )
        try {
            val relayer = OZRelayerClient(
                relayerUrl = "https://relayer.example.com",
                injectedClient = mockClient
            )

            val response = relayer.send(createTestHostFunction(), listOf(createTestAuthEntry()))

            assertFalse(response.success)
            assertNotNull(response.error)
            assertTrue(response.error!!.contains("Failed to parse relayer response"))

            relayer.close()
        } finally {
            mockClient.close()
        }
    }

    @Test
    fun testSend_networkError_returnsError() = runTest {
        val mockClient = createThrowingMockClient(Exception("Connection refused"))
        try {
            val relayer = OZRelayerClient(
                relayerUrl = "https://relayer.example.com",
                injectedClient = mockClient
            )

            val response = relayer.send(createTestHostFunction(), listOf(createTestAuthEntry()))

            assertFalse(response.success)
            assertNotNull(response.error)

            relayer.close()
        } finally {
            mockClient.close()
        }
    }

    @Test
    fun testSend_engineThrowable_returnsError() = runTest {
        // Non-Exception Throwable simulates the Kotlin/JS HTTP engine reporting
        // a connectivity failure as kotlin.Error; the no-throw contract must
        // capture it in the response
        val mockClient = createThrowingMockClient(Error("Fail to fetch"))
        try {
            val relayer = OZRelayerClient(
                relayerUrl = "https://relayer.example.com",
                injectedClient = mockClient
            )

            val response = relayer.send(createTestHostFunction(), listOf(createTestAuthEntry()))

            assertFalse(response.success)
            assertEquals("Fail to fetch", response.error)

            relayer.close()
        } finally {
            mockClient.close()
        }
    }

    @Test
    fun testSend_cancellation_propagates() = runTest {
        val mockClient = createThrowingMockClient(CancellationException("cancelled"))
        try {
            val relayer = OZRelayerClient(
                relayerUrl = "https://relayer.example.com",
                injectedClient = mockClient
            )

            assertFailsWith<CancellationException> {
                relayer.send(createTestHostFunction(), listOf(createTestAuthEntry()))
            }

            relayer.close()
        } finally {
            mockClient.close()
        }
    }

    // MARK: - send: timeout

    @Test
    fun testSend_timeout_returnsTimeoutErrorCode() = runTest {
        val mockClient = createThrowingMockClient(
            HttpRequestTimeoutException("https://relayer.example.com", 360_000L)
        )
        try {
            val relayer = OZRelayerClient(
                relayerUrl = "https://relayer.example.com",
                injectedClient = mockClient
            )

            val response = relayer.send(createTestHostFunction(), listOf(createTestAuthEntry()))

            assertFalse(response.success)
            assertEquals(RelayerErrorCodes.TIMEOUT, response.errorCode)
            assertTrue(response.error!!.contains("timed out"))

            relayer.close()
        } finally {
            mockClient.close()
        }
    }

    // MARK: - send: perRequestTimeoutMs

    @Test
    fun testSend_withPerRequestTimeout_sendsRequest() = runTest {
        val mockClient = createMockClient(
            """{"success": true, "data": {"hash": "timeout-test-hash"}}"""
        )
        try {
            val relayer = OZRelayerClient(
                relayerUrl = "https://relayer.example.com",
                injectedClient = mockClient
            )

            val response = relayer.send(
                createTestHostFunction(),
                listOf(createTestAuthEntry()),
                perRequestTimeoutMs = 5_000L
            )

            assertTrue(response.success)
            assertEquals("timeout-test-hash", response.hash)

            relayer.close()
        } finally {
            mockClient.close()
        }
    }

    // MARK: - sendXdr: success

    @Test
    fun testSendXdr_success_returnsHash() = runTest {
        val mockClient = createMockClient(
            """{"success": true, "data": {"hash": "def456", "transactionId": "tx-002", "status": "SUCCESS"}}"""
        )
        try {
            val relayer = OZRelayerClient(
                relayerUrl = "https://relayer.example.com",
                injectedClient = mockClient
            )

            val response = relayer.sendXdr(createTestTransactionEnvelope())

            assertTrue(response.success)
            assertEquals("def456", response.hash)
            assertEquals("tx-002", response.transactionId)
            assertEquals("SUCCESS", response.status)

            relayer.close()
        } finally {
            mockClient.close()
        }
    }

    @Test
    fun testSendXdr_buildsCorrectJsonPayload() = runTest {
        var capturedBody: String? = null

        val mockClient = createCapturingMockClient(
            """{"success": true, "data": {"hash": "def456"}}"""
        ) { capturedBody = it }

        try {
            val relayer = OZRelayerClient(
                relayerUrl = "https://relayer.example.com",
                injectedClient = mockClient
            )

            val response = relayer.sendXdr(createTestTransactionEnvelope())

            assertTrue(response.success)
            assertEquals("def456", response.hash)

            assertNotNull(capturedBody)
            val json = Json.parseToJsonElement(capturedBody!!).jsonObject

            assertTrue(json.containsKey("xdr"))
            val xdrValue = json["xdr"]!!.jsonPrimitive.content
            assertTrue(xdrValue.isNotEmpty())

            // Verify the XDR round-trips
            val decoded = TransactionEnvelopeXdr.fromXdrBase64(xdrValue)
            assertTrue(decoded is TransactionEnvelopeXdr.V1)

            relayer.close()
        } finally {
            mockClient.close()
        }
    }

    @Test
    fun testSendXdr_errorResponse_returnsError() = runTest {
        val mockClient = createMockClient(
            responseBody = """{"success": false, "error": "invalid xdr", "code": "INVALID_XDR"}""",
            statusCode = HttpStatusCode.BadRequest
        )
        try {
            val relayer = OZRelayerClient(
                relayerUrl = "https://relayer.example.com",
                injectedClient = mockClient
            )

            val response = relayer.sendXdr(createTestTransactionEnvelope())

            assertFalse(response.success)
            assertEquals("invalid xdr", response.error)
            assertEquals("INVALID_XDR", response.errorCode)

            relayer.close()
        } finally {
            mockClient.close()
        }
    }

    @Test
    fun testSendXdr_timeout_returnsTimeoutErrorCode() = runTest {
        val mockClient = createThrowingMockClient(
            HttpRequestTimeoutException("https://relayer.example.com", 360_000L)
        )
        try {
            val relayer = OZRelayerClient(
                relayerUrl = "https://relayer.example.com",
                injectedClient = mockClient
            )

            val response = relayer.sendXdr(createTestTransactionEnvelope())

            assertFalse(response.success)
            assertEquals(RelayerErrorCodes.TIMEOUT, response.errorCode)
            assertTrue(response.error!!.contains("timed out"))

            relayer.close()
        } finally {
            mockClient.close()
        }
    }

    // MARK: - Response parsing

    @Test
    fun testResponseParsing_withDataWrapper_extractsNestedFields() = runTest {
        val mockClient = createMockClient(
            """{"success": true, "data": {"transactionId": "tx-100", "hash": "hash-100", "status": "PENDING"}}"""
        )
        try {
            val relayer = OZRelayerClient(
                relayerUrl = "https://relayer.example.com",
                injectedClient = mockClient
            )

            val response = relayer.send(createTestHostFunction(), listOf(createTestAuthEntry()))

            assertTrue(response.success)
            assertEquals("tx-100", response.transactionId)
            assertEquals("hash-100", response.hash)
            assertEquals("PENDING", response.status)

            relayer.close()
        } finally {
            mockClient.close()
        }
    }

    @Test
    fun testResponseParsing_withoutDataWrapper_usesTopLevelFields() = runTest {
        val mockClient = createMockClient(
            """{"success": true, "transactionId": "tx-200", "hash": "hash-200", "status": "SUCCESS"}"""
        )
        try {
            val relayer = OZRelayerClient(
                relayerUrl = "https://relayer.example.com",
                injectedClient = mockClient
            )

            val response = relayer.send(createTestHostFunction(), listOf(createTestAuthEntry()))

            assertTrue(response.success)
            assertEquals("tx-200", response.transactionId)
            assertEquals("hash-200", response.hash)
            assertEquals("SUCCESS", response.status)

            relayer.close()
        } finally {
            mockClient.close()
        }
    }

    @Test
    fun testResponseParsing_errorFromErrorField() = runTest {
        val mockClient = createMockClient(
            responseBody = """{"success": false, "error": "specific error message"}""",
            statusCode = HttpStatusCode.BadRequest
        )
        try {
            val relayer = OZRelayerClient(
                relayerUrl = "https://relayer.example.com",
                injectedClient = mockClient
            )

            val response = relayer.send(createTestHostFunction(), listOf(createTestAuthEntry()))

            assertFalse(response.success)
            assertEquals("specific error message", response.error)

            relayer.close()
        } finally {
            mockClient.close()
        }
    }

    @Test
    fun testResponseParsing_errorFallbackToMessageField() = runTest {
        val mockClient = createMockClient(
            responseBody = """{"success": false, "message": "fallback error message"}""",
            statusCode = HttpStatusCode.BadRequest
        )
        try {
            val relayer = OZRelayerClient(
                relayerUrl = "https://relayer.example.com",
                injectedClient = mockClient
            )

            val response = relayer.send(createTestHostFunction(), listOf(createTestAuthEntry()))

            assertFalse(response.success)
            assertEquals("fallback error message", response.error)

            relayer.close()
        } finally {
            mockClient.close()
        }
    }

    // MARK: - extractErrorCode

    @Test
    fun testExtractErrorCode_topLevelCode() = runTest {
        val mockClient = createMockClient(
            responseBody = """{"success": false, "error": "failed", "code": "SIMULATION_FAILED"}""",
            statusCode = HttpStatusCode.BadRequest
        )
        try {
            val relayer = OZRelayerClient(
                relayerUrl = "https://relayer.example.com",
                injectedClient = mockClient
            )

            val response = relayer.send(createTestHostFunction(), listOf(createTestAuthEntry()))

            assertFalse(response.success)
            assertEquals("SIMULATION_FAILED", response.errorCode)

            relayer.close()
        } finally {
            mockClient.close()
        }
    }

    @Test
    fun testExtractErrorCode_errorCodeField() = runTest {
        val mockClient = createMockClient(
            responseBody = """{"success": false, "error": "unauthorized", "errorCode": "UNAUTHORIZED"}""",
            statusCode = HttpStatusCode.Forbidden
        )
        try {
            val relayer = OZRelayerClient(
                relayerUrl = "https://relayer.example.com",
                injectedClient = mockClient
            )

            val response = relayer.send(createTestHostFunction(), listOf(createTestAuthEntry()))

            assertFalse(response.success)
            assertEquals("UNAUTHORIZED", response.errorCode)

            relayer.close()
        } finally {
            mockClient.close()
        }
    }

    @Test
    fun testExtractErrorCode_nestedDataCode() = runTest {
        val mockClient = createMockClient(
            responseBody = """{"success": false, "error": "failed", "data": {"code": "ONCHAIN_FAILED"}}""",
            statusCode = HttpStatusCode.BadRequest
        )
        try {
            val relayer = OZRelayerClient(
                relayerUrl = "https://relayer.example.com",
                injectedClient = mockClient
            )

            val response = relayer.send(createTestHostFunction(), listOf(createTestAuthEntry()))

            assertFalse(response.success)
            assertEquals("ONCHAIN_FAILED", response.errorCode)

            relayer.close()
        } finally {
            mockClient.close()
        }
    }

    @Test
    fun testExtractErrorCode_noCodeFieldReturnsNull() = runTest {
        val mockClient = createMockClient(
            responseBody = """{"success": false, "error": "something went wrong"}""",
            statusCode = HttpStatusCode.BadRequest
        )
        try {
            val relayer = OZRelayerClient(
                relayerUrl = "https://relayer.example.com",
                injectedClient = mockClient
            )

            val response = relayer.send(createTestHostFunction(), listOf(createTestAuthEntry()))

            assertFalse(response.success)
            assertEquals("something went wrong", response.error)
            assertNull(response.errorCode)

            relayer.close()
        } finally {
            mockClient.close()
        }
    }

    // MARK: - Status code fallback message

    @Test
    fun testErrorResponse_statusCodeFallback_whenNoErrorOrMessageField() = runTest {
        val mockClient = createMockClient(
            responseBody = """{"success": false}""",
            statusCode = HttpStatusCode.BadGateway
        )
        try {
            val relayer = OZRelayerClient(
                relayerUrl = "https://relayer.example.com",
                injectedClient = mockClient
            )

            val response = relayer.send(createTestHostFunction(), listOf(createTestAuthEntry()))

            assertFalse(response.success)
            assertNotNull(response.error)
            assertTrue(
                response.error!!.contains("502"),
                "Error message must contain the HTTP status code when no error/message field is present"
            )

            relayer.close()
        } finally {
            mockClient.close()
        }
    }

    // MARK: - close

    @Test
    fun testClose_clientIsAutoCloseable() {
        val relayer = OZRelayerClient(
            relayerUrl = "https://relayer.example.com"
        )
        // AutoCloseable contract: close() should not throw
        relayer.close()
    }

    @Test
    fun testClose_doubleCloseDoesNotThrow() {
        val relayer = OZRelayerClient(
            relayerUrl = "https://relayer.example.com"
        )
        relayer.close()
        // Second close should be safe
        relayer.close()
    }

    // MARK: - RelayerErrorCodes constants

    @Test
    fun testRelayerErrorCodes_allCodesAreNonBlank() {
        val codes = listOf(
            RelayerErrorCodes.INVALID_PARAMS,
            RelayerErrorCodes.INVALID_XDR,
            RelayerErrorCodes.POOL_CAPACITY,
            RelayerErrorCodes.SIMULATION_FAILED,
            RelayerErrorCodes.ONCHAIN_FAILED,
            RelayerErrorCodes.INVALID_TIME_BOUNDS,
            RelayerErrorCodes.FEE_LIMIT_EXCEEDED,
            RelayerErrorCodes.UNAUTHORIZED,
            RelayerErrorCodes.TIMEOUT
        )
        codes.forEach { code ->
            assertTrue(code.isNotBlank(), "Error code must not be blank: '$code'")
        }
    }

    // MARK: - XDR encoding failures

    @Test
    fun testSend_hostFunctionCannotBeEncoded_returnsErrorWithoutRequest() = runTest {
        var requestMade = false
        val mockClient = createCapturingMockClient("""{"success": true}""") { requestMade = true }
        try {
            val relayer = OZRelayerClient(
                relayerUrl = "https://relayer.example.com",
                injectedClient = mockClient
            )

            val response = relayer.send(
                createUnencodableHostFunction(),
                listOf(createTestAuthEntry())
            )

            assertFalse(response.success)
            assertTrue(
                response.error!!.contains("Failed to encode host function to XDR"),
                "Error must identify the host function as the unencodable component; got: ${response.error}"
            )
            assertFalse(requestMade, "No request may be sent when the payload cannot be built")

            relayer.close()
        } finally {
            mockClient.close()
        }
    }

    @Test
    fun testSend_authEntryCannotBeEncoded_returnsErrorWithoutRequest() = runTest {
        var requestMade = false
        val mockClient = createCapturingMockClient("""{"success": true}""") { requestMade = true }
        try {
            val relayer = OZRelayerClient(
                relayerUrl = "https://relayer.example.com",
                injectedClient = mockClient
            )

            val response = relayer.send(
                createTestHostFunction(),
                listOf(createTestAuthEntry(), createUnencodableAuthEntry())
            )

            assertFalse(response.success)
            assertTrue(
                response.error!!.contains("Failed to encode auth entry to XDR"),
                "Error must identify the auth entry as the unencodable component; got: ${response.error}"
            )
            assertFalse(requestMade, "No request may be sent when the payload cannot be built")

            relayer.close()
        } finally {
            mockClient.close()
        }
    }

    @Test
    fun testSendXdr_envelopeCannotBeEncoded_returnsErrorWithoutRequest() = runTest {
        var requestMade = false
        val mockClient = createCapturingMockClient("""{"success": true}""") { requestMade = true }
        try {
            val relayer = OZRelayerClient(
                relayerUrl = "https://relayer.example.com",
                injectedClient = mockClient
            )

            val response = relayer.sendXdr(createUnencodableTransactionEnvelope())

            assertFalse(response.success)
            assertTrue(
                response.error!!.contains("Failed to encode transaction envelope to XDR"),
                "Error must identify the envelope as the unencodable component; got: ${response.error}"
            )
            assertFalse(requestMade, "No request may be sent when the payload cannot be built")

            relayer.close()
        } finally {
            mockClient.close()
        }
    }

    // MARK: - Response body failures

    @Test
    fun testSend_responseBodyTruncated_returnsErrorWithoutThrowing() = runTest {
        val mockClient = createTruncatedBodyMockClient()
        try {
            val relayer = OZRelayerClient(
                relayerUrl = "https://relayer.example.com",
                injectedClient = mockClient
            )

            val response = relayer.send(createTestHostFunction(), listOf(createTestAuthEntry()))

            assertFalse(
                response.success,
                "A connection dropped mid-body must not be reported as a successful submission"
            )
            assertTrue(
                response.error!!.isNotBlank(),
                "The transport failure must be described in the response"
            )
            assertNull(response.hash, "No transaction hash may be reported for an unread response")

            relayer.close()
        } finally {
            mockClient.close()
        }
    }

    @Test
    fun testSend_networkErrorWithoutMessage_returnsDefaultError() = runTest {
        // Some engines report connectivity failures with a null message; the client
        // must still describe the failure rather than return an empty error.
        val mockClient = createThrowingMockClient(RuntimeException())
        try {
            val relayer = OZRelayerClient(
                relayerUrl = "https://relayer.example.com",
                injectedClient = mockClient
            )

            val response = relayer.send(createTestHostFunction(), listOf(createTestAuthEntry()))

            assertFalse(response.success)
            assertEquals("Relayer request failed", response.error)

            relayer.close()
        } finally {
            mockClient.close()
        }
    }

    @Test
    fun testSend_longNonJsonBody_isTruncatedInError() = runTest {
        val body = "<html>" + "x".repeat(400) + "</html>"
        val mockClient = createMockClient(responseBody = body, contentType = "text/html")
        try {
            val relayer = OZRelayerClient(
                relayerUrl = "https://relayer.example.com",
                injectedClient = mockClient
            )

            val response = relayer.send(createTestHostFunction(), listOf(createTestAuthEntry()))

            assertFalse(response.success)
            val error = response.error!!
            assertTrue(
                error.startsWith("Failed to parse relayer response as JSON:"),
                "Error must name the parse failure; got: $error"
            )
            assertTrue(error.endsWith("..."), "Long bodies must be truncated with an ellipsis")
            assertFalse(
                error.contains("x".repeat(201)),
                "At most 200 characters of the body may be echoed back"
            )

            relayer.close()
        } finally {
            mockClient.close()
        }
    }

    // MARK: - Success flag handling

    @Test
    fun testSend_httpSuccessWithBodySuccessFalse_returnsError() = runTest {
        val mockClient = createMockClient(
            responseBody = """{"success": false, "error": "pool at capacity", "code": "POOL_CAPACITY"}""",
            statusCode = HttpStatusCode.OK
        )
        try {
            val relayer = OZRelayerClient(
                relayerUrl = "https://relayer.example.com",
                injectedClient = mockClient
            )

            val response = relayer.send(createTestHostFunction(), listOf(createTestAuthEntry()))

            assertFalse(
                response.success,
                "HTTP 200 alone must not be treated as success when the body says otherwise"
            )
            assertEquals("pool at capacity", response.error)
            assertEquals(RelayerErrorCodes.POOL_CAPACITY, response.errorCode)

            relayer.close()
        } finally {
            mockClient.close()
        }
    }

    @Test
    fun testSend_httpErrorWithBodySuccessTrue_returnsError() = runTest {
        val mockClient = createMockClient(
            responseBody = """{"success": true, "hash": "should-be-ignored"}""",
            statusCode = HttpStatusCode.InternalServerError
        )
        try {
            val relayer = OZRelayerClient(
                relayerUrl = "https://relayer.example.com",
                injectedClient = mockClient
            )

            val response = relayer.send(createTestHostFunction(), listOf(createTestAuthEntry()))

            assertFalse(
                response.success,
                "A success flag in the body must not override a non-2xx status"
            )
            assertNull(response.hash, "No hash may be reported for a failed submission")
            assertTrue(response.error!!.contains("500"))

            relayer.close()
        } finally {
            mockClient.close()
        }
    }

    @Test
    fun testSend_successFlagMissing_returnsError() = runTest {
        val mockClient = createMockClient(
            responseBody = """{"hash": "abc123"}""",
            statusCode = HttpStatusCode.OK
        )
        try {
            val relayer = OZRelayerClient(
                relayerUrl = "https://relayer.example.com",
                injectedClient = mockClient
            )

            val response = relayer.send(createTestHostFunction(), listOf(createTestAuthEntry()))

            assertFalse(
                response.success,
                "An absent success flag must be treated as failure, not success"
            )
            assertNull(response.hash)

            relayer.close()
        } finally {
            mockClient.close()
        }
    }

    @Test
    fun testSend_successFlagNotBoolean_returnsError() = runTest {
        val mockClient = createMockClient(
            responseBody = """{"success": "yes", "hash": "abc123"}""",
            statusCode = HttpStatusCode.OK
        )
        try {
            val relayer = OZRelayerClient(
                relayerUrl = "https://relayer.example.com",
                injectedClient = mockClient
            )

            val response = relayer.send(createTestHostFunction(), listOf(createTestAuthEntry()))

            assertFalse(
                response.success,
                "A non-boolean success flag must not be interpreted as success"
            )
            assertNull(response.hash)

            relayer.close()
        } finally {
            mockClient.close()
        }
    }

    @Test
    fun testSend_successWithoutOptionalFields_returnsNullTransactionDetails() = runTest {
        val mockClient = createMockClient("""{"success": true, "data": {}}""")
        try {
            val relayer = OZRelayerClient(
                relayerUrl = "https://relayer.example.com",
                injectedClient = mockClient
            )

            val response = relayer.send(createTestHostFunction(), listOf(createTestAuthEntry()))

            assertTrue(response.success)
            assertNull(response.hash, "An absent hash must surface as null, not an empty string")
            assertNull(response.transactionId)
            assertNull(response.status)

            relayer.close()
        } finally {
            mockClient.close()
        }
    }

    @Test
    fun testSend_successWithNullDataFields_returnsNullTransactionDetails() = runTest {
        val mockClient = createMockClient(
            """{"success": true, "data": {"hash": null, "transactionId": null, "status": null}}"""
        )
        try {
            val relayer = OZRelayerClient(
                relayerUrl = "https://relayer.example.com",
                injectedClient = mockClient
            )

            val response = relayer.send(createTestHostFunction(), listOf(createTestAuthEntry()))

            assertTrue(response.success)
            assertNull(response.hash, "JSON null must decode to a Kotlin null, not the string \"null\"")
            assertNull(response.transactionId)
            assertNull(response.status)

            relayer.close()
        } finally {
            mockClient.close()
        }
    }

    // MARK: - Error message and code extraction edge cases

    @Test
    fun testResponseParsing_nullErrorField_fallsBackToMessageField() = runTest {
        val mockClient = createMockClient(
            responseBody = """{"success": false, "error": null, "message": "fallback error message"}""",
            statusCode = HttpStatusCode.BadRequest
        )
        try {
            val relayer = OZRelayerClient(
                relayerUrl = "https://relayer.example.com",
                injectedClient = mockClient
            )

            val response = relayer.send(createTestHostFunction(), listOf(createTestAuthEntry()))

            assertFalse(response.success)
            assertEquals("fallback error message", response.error)

            relayer.close()
        } finally {
            mockClient.close()
        }
    }

    @Test
    fun testResponseParsing_nullErrorAndMessageFields_fallsBackToStatusCode() = runTest {
        val mockClient = createMockClient(
            responseBody = """{"success": false, "error": null, "message": null}""",
            statusCode = HttpStatusCode.BadGateway
        )
        try {
            val relayer = OZRelayerClient(
                relayerUrl = "https://relayer.example.com",
                injectedClient = mockClient
            )

            val response = relayer.send(createTestHostFunction(), listOf(createTestAuthEntry()))

            assertFalse(response.success)
            assertTrue(
                response.error!!.contains("502"),
                "Explicit JSON nulls must fall through to the status-code message; got: ${response.error}"
            )

            relayer.close()
        } finally {
            mockClient.close()
        }
    }

    @Test
    fun testExtractErrorCode_structuredCodeField_fallsBackToErrorCodeField() = runTest {
        // Some relayer deployments return a structured `code` object; the scalar
        // `errorCode` field must still be honoured.
        val mockClient = createMockClient(
            responseBody = """{"success": false, "error": "failed", """ +
                """"code": {"value": "SIMULATION_FAILED"}, "errorCode": "FEE_LIMIT_EXCEEDED"}""",
            statusCode = HttpStatusCode.BadRequest
        )
        try {
            val relayer = OZRelayerClient(
                relayerUrl = "https://relayer.example.com",
                injectedClient = mockClient
            )

            val response = relayer.send(createTestHostFunction(), listOf(createTestAuthEntry()))

            assertFalse(response.success)
            assertEquals(RelayerErrorCodes.FEE_LIMIT_EXCEEDED, response.errorCode)

            relayer.close()
        } finally {
            mockClient.close()
        }
    }

    @Test
    fun testExtractErrorCode_structuredErrorCodeField_fallsBackToNestedDataCode() = runTest {
        val mockClient = createMockClient(
            responseBody = """{"success": false, "error": "failed", """ +
                """"errorCode": ["UNAUTHORIZED"], "data": {"code": "ONCHAIN_FAILED"}}""",
            statusCode = HttpStatusCode.BadRequest
        )
        try {
            val relayer = OZRelayerClient(
                relayerUrl = "https://relayer.example.com",
                injectedClient = mockClient
            )

            val response = relayer.send(createTestHostFunction(), listOf(createTestAuthEntry()))

            assertFalse(response.success)
            assertEquals(RelayerErrorCodes.ONCHAIN_FAILED, response.errorCode)

            relayer.close()
        } finally {
            mockClient.close()
        }
    }

    @Test
    fun testExtractErrorCode_nestedDataWithoutCode_returnsNullAndKeepsDetails() = runTest {
        val mockClient = createMockClient(
            responseBody = """{"success": false, "error": "failed", "data": {"reason": "unknown"}}""",
            statusCode = HttpStatusCode.BadRequest
        )
        try {
            val relayer = OZRelayerClient(
                relayerUrl = "https://relayer.example.com",
                injectedClient = mockClient
            )

            val response = relayer.send(createTestHostFunction(), listOf(createTestAuthEntry()))

            assertFalse(response.success)
            assertNull(response.errorCode)
            val details = assertNotNull(response.details, "The data object must be surfaced as details")
            assertEquals("unknown", details.jsonObject["reason"]!!.jsonPrimitive.content)

            relayer.close()
        } finally {
            mockClient.close()
        }
    }

    @Test
    fun testExtractErrorCode_nonObjectData_returnsNull() = runTest {
        val mockClient = createMockClient(
            responseBody = """{"success": false, "error": "failed", "data": "not-an-object"}""",
            statusCode = HttpStatusCode.BadRequest
        )
        try {
            val relayer = OZRelayerClient(
                relayerUrl = "https://relayer.example.com",
                injectedClient = mockClient
            )

            val response = relayer.send(createTestHostFunction(), listOf(createTestAuthEntry()))

            assertFalse(response.success)
            assertNull(response.errorCode)
            assertEquals(
                "not-an-object",
                assertNotNull(response.details).jsonPrimitive.content
            )

            relayer.close()
        } finally {
            mockClient.close()
        }
    }

    @Test
    fun testRelayerErrorCodes_specificValues() {
        assertEquals("TIMEOUT", RelayerErrorCodes.TIMEOUT)
        assertEquals("INVALID_PARAMS", RelayerErrorCodes.INVALID_PARAMS)
        assertEquals("INVALID_XDR", RelayerErrorCodes.INVALID_XDR)
        assertEquals("POOL_CAPACITY", RelayerErrorCodes.POOL_CAPACITY)
        assertEquals("SIMULATION_FAILED", RelayerErrorCodes.SIMULATION_FAILED)
        assertEquals("ONCHAIN_FAILED", RelayerErrorCodes.ONCHAIN_FAILED)
        assertEquals("INVALID_TIME_BOUNDS", RelayerErrorCodes.INVALID_TIME_BOUNDS)
        assertEquals("FEE_LIMIT_EXCEEDED", RelayerErrorCodes.FEE_LIMIT_EXCEEDED)
        assertEquals("UNAUTHORIZED", RelayerErrorCodes.UNAUTHORIZED)
    }

    // ===== Responses whose fields carry an unexpected JSON shape =====

    @Test
    fun testSend_successFieldIsObject_returnsErrorWithoutThrowing() = runTest {
        val mockClient = createMockClient("""{"success": {"nested": true}, "error": "relayer rejected"}""")
        try {
            val relayer = OZRelayerClient(
                relayerUrl = "https://relayer.example.com",
                injectedClient = mockClient
            )

            val response = relayer.send(createTestHostFunction(), listOf(createTestAuthEntry()))

            assertFalse(response.success, "A non-boolean success field cannot mean success")
            assertEquals("relayer rejected", response.error)

            relayer.close()
        } finally {
            mockClient.close()
        }
    }

    @Test
    fun testSend_errorFieldIsObject_fallsBackToStatusMessage() = runTest {
        val mockClient = createMockClient(
            """{"success": false, "error": {"code": 42, "detail": "nope"}}""",
            statusCode = HttpStatusCode.BadRequest
        )
        try {
            val relayer = OZRelayerClient(
                relayerUrl = "https://relayer.example.com",
                injectedClient = mockClient
            )

            val response = relayer.send(createTestHostFunction(), listOf(createTestAuthEntry()))

            assertFalse(response.success)
            assertEquals(
                "Relayer request failed with status 400",
                response.error,
                "A non-primitive error field falls back to the status-derived message"
            )

            relayer.close()
        } finally {
            mockClient.close()
        }
    }

    @Test
    fun testSend_hashFieldIsArray_reportsSuccessWithoutHash() = runTest {
        val mockClient = createMockClient(
            """{"success": true, "data": {"transactionId": "tx-1", "hash": ["a", "b"], "status": "PENDING"}}"""
        )
        try {
            val relayer = OZRelayerClient(
                relayerUrl = "https://relayer.example.com",
                injectedClient = mockClient
            )

            val response = relayer.send(createTestHostFunction(), listOf(createTestAuthEntry()))

            assertTrue(response.success)
            assertEquals("tx-1", response.transactionId)
            assertNull(response.hash, "A non-primitive hash is reported as absent rather than raising")
            assertEquals("PENDING", response.status)

            relayer.close()
        } finally {
            mockClient.close()
        }
    }

    @Test
    fun testSend_errorCodeFieldIsObject_reportsErrorWithoutCode() = runTest {
        val mockClient = createMockClient(
            """{"success": false, "error": "bad request", "code": {"nested": 1}}""",
            statusCode = HttpStatusCode.BadRequest
        )
        try {
            val relayer = OZRelayerClient(
                relayerUrl = "https://relayer.example.com",
                injectedClient = mockClient
            )

            val response = relayer.send(createTestHostFunction(), listOf(createTestAuthEntry()))

            assertFalse(response.success)
            assertEquals("bad request", response.error)
            assertNull(response.errorCode, "A non-primitive code is reported as absent")

            relayer.close()
        } finally {
            mockClient.close()
        }
    }
}
