//
//  RelayerClientTest.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.unitTests.smartaccount

import com.soneso.stellar.sdk.smartaccount.core.*
import com.soneso.stellar.sdk.smartaccount.oz.*
import com.soneso.stellar.sdk.xdr.*
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
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
        private fun createMockClient(
            responseBody: String,
            statusCode: HttpStatusCode = HttpStatusCode.OK,
            contentType: String = "application/json"
        ): HttpClient {
            return HttpClient(MockEngine) {
                install(ContentNegotiation) {
                    json(Json { ignoreUnknownKeys = true; isLenient = true })
                }
                engine {
                    addHandler {
                        respond(
                            content = responseBody,
                            status = statusCode,
                            headers = headersOf(HttpHeaders.ContentType, contentType)
                        )
                    }
                }
            }
        }

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

        private fun createThrowingMockClient(exception: Throwable): HttpClient {
            return HttpClient(MockEngine) {
                install(ContentNegotiation) {
                    json(Json { ignoreUnknownKeys = true; isLenient = true })
                }
                engine {
                    addHandler {
                        throw exception
                    }
                }
            }
        }
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
    fun testConstructor_trailingSlashNormalization() {
        val client = OZRelayerClient(relayerUrl = "https://relayer.example.com///")
        assertNotNull(client)
        client.close()
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
}
