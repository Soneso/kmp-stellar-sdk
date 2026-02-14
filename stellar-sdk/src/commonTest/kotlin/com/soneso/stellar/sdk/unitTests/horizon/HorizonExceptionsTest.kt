package com.soneso.stellar.sdk.unitTests.horizon

import com.soneso.stellar.sdk.horizon.exceptions.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HorizonExceptionsTest {

    // ===== NetworkException =====

    @Test
    fun testNetworkExceptionWithMessageAndCause() {
        val cause = RuntimeException("underlying error")
        val exception = NetworkException("network failed", cause, 500, "error body")
        assertEquals("network failed", exception.message)
        assertEquals(cause, exception.cause)
        assertEquals(500, exception.code)
        assertEquals("error body", exception.body)
    }

    @Test
    fun testNetworkExceptionWithCodeAndBody() {
        val exception = NetworkException(code = 404, body = "not found")
        assertEquals("Network error occurred (code: 404)", exception.message)
        assertEquals(404, exception.code)
        assertEquals("not found", exception.body)
        assertNull(exception.cause)
    }

    @Test
    fun testNetworkExceptionWithCauseCodeBody() {
        val cause = RuntimeException("error")
        val exception = NetworkException(cause = cause, code = 502, body = "bad gateway")
        assertEquals("error", exception.message)
        assertEquals(cause, exception.cause)
        assertEquals(502, exception.code)
        assertEquals("bad gateway", exception.body)
    }

    @Test
    fun testNetworkExceptionIsException() {
        val exception = NetworkException("test")
        assertIs<Exception>(exception)
    }

    // ===== BadRequestException =====

    @Test
    fun testBadRequestExceptionWithCodeAndBody() {
        val exception = BadRequestException(code = 400, body = "bad request")
        assertEquals("Bad request (code: 400)", exception.message)
        assertEquals(400, exception.code)
        assertEquals("bad request", exception.body)
    }

    @Test
    fun testBadRequestExceptionWithAll() {
        val cause = RuntimeException("cause")
        val exception = BadRequestException("custom msg", cause, 422, "unprocessable")
        assertEquals("custom msg", exception.message)
        assertEquals(cause, exception.cause)
        assertEquals(422, exception.code)
        assertEquals("unprocessable", exception.body)
    }

    @Test
    fun testBadRequestExceptionIsNetworkException() {
        val exception = BadRequestException(code = 400, body = null)
        assertIs<NetworkException>(exception)
    }

    // ===== BadResponseException =====

    @Test
    fun testBadResponseExceptionWithCodeAndBody() {
        val exception = BadResponseException(code = 500, body = "internal error")
        assertEquals("Bad response from server (code: 500)", exception.message)
        assertEquals(500, exception.code)
        assertEquals("internal error", exception.body)
    }

    @Test
    fun testBadResponseExceptionWithAll() {
        val exception = BadResponseException("server error", null, 503, "service unavailable")
        assertEquals("server error", exception.message)
        assertEquals(503, exception.code)
    }

    @Test
    fun testBadResponseExceptionIsNetworkException() {
        val exception = BadResponseException(code = 500, body = "error")
        assertIs<NetworkException>(exception)
    }

    // ===== TooManyRequestsException =====

    @Test
    fun testTooManyRequestsExceptionWithCodeAndBody() {
        val exception = TooManyRequestsException(code = 429, body = "rate limited")
        assertEquals("Too many requests (code: 429)", exception.message)
        assertEquals(429, exception.code)
        assertEquals("rate limited", exception.body)
    }

    @Test
    fun testTooManyRequestsExceptionWithAll() {
        val exception = TooManyRequestsException("custom", null, 429, "body")
        assertEquals("custom", exception.message)
    }

    @Test
    fun testTooManyRequestsExceptionIsNetworkException() {
        val exception = TooManyRequestsException(code = 429, body = null)
        assertIs<NetworkException>(exception)
    }

    // ===== RequestTimeoutException =====

    @Test
    fun testRequestTimeoutExceptionWithCause() {
        val cause = RuntimeException("timed out")
        val exception = RequestTimeoutException(cause)
        assertEquals("Request timeout: timed out", exception.message)
        assertEquals(cause, exception.cause)
        assertNull(exception.code)
        assertNull(exception.body)
    }

    @Test
    fun testRequestTimeoutExceptionWithCodeAndBody() {
        val exception = RequestTimeoutException(code = 408, body = "timeout")
        assertEquals("Request timeout (code: 408)", exception.message)
        assertEquals(408, exception.code)
        assertEquals("timeout", exception.body)
    }

    @Test
    fun testRequestTimeoutExceptionWithAll() {
        val exception = RequestTimeoutException("custom timeout", null, 504, "gateway timeout")
        assertEquals("custom timeout", exception.message)
        assertEquals(504, exception.code)
    }

    @Test
    fun testRequestTimeoutExceptionIsNetworkException() {
        val cause = RuntimeException("timeout")
        val exception = RequestTimeoutException(cause)
        assertIs<NetworkException>(exception)
    }

    // ===== UnknownResponseException =====

    @Test
    fun testUnknownResponseExceptionWithCodeAndBody() {
        val exception = UnknownResponseException(code = 600, body = "unknown")
        assertEquals("Unknown response from server (code: 600)", exception.message)
        assertEquals(600, exception.code)
        assertEquals("unknown", exception.body)
    }

    @Test
    fun testUnknownResponseExceptionWithAll() {
        val exception = UnknownResponseException("custom unknown", null, 999, "body")
        assertEquals("custom unknown", exception.message)
    }

    @Test
    fun testUnknownResponseExceptionIsNetworkException() {
        val exception = UnknownResponseException(code = 600, body = null)
        assertIs<NetworkException>(exception)
    }

    // ===== ConnectionErrorException =====

    @Test
    fun testConnectionErrorExceptionWithCause() {
        val cause = RuntimeException("DNS resolution failed")
        val exception = ConnectionErrorException(cause)
        assertEquals("Connection error: DNS resolution failed", exception.message)
        assertEquals(cause, exception.cause)
        assertNull(exception.code)
        assertNull(exception.body)
    }

    @Test
    fun testConnectionErrorExceptionWithAll() {
        val exception = ConnectionErrorException("custom msg", null, null, null)
        assertEquals("custom msg", exception.message)
    }

    @Test
    fun testConnectionErrorExceptionIsNetworkException() {
        val cause = RuntimeException("network error")
        val exception = ConnectionErrorException(cause)
        assertIs<NetworkException>(exception)
    }

    // ===== SdkException =====

    @Test
    fun testSdkExceptionWithMessage() {
        val exception = SdkException("sdk error")
        assertEquals("sdk error", exception.message)
        assertNull(exception.cause)
    }

    @Test
    fun testSdkExceptionWithMessageAndCause() {
        val cause = RuntimeException("root cause")
        val exception = SdkException("sdk error", cause)
        assertEquals("sdk error", exception.message)
        assertEquals(cause, exception.cause)
    }

    @Test
    fun testSdkExceptionIsException() {
        val exception = SdkException("test")
        assertIs<Exception>(exception)
    }

    // ===== AccountRequiresMemoException =====

    @Test
    fun testAccountRequiresMemoException() {
        val exception = AccountRequiresMemoException(
            "Account requires memo",
            "GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7",
            0
        )
        assertEquals("Account requires memo", exception.message)
        assertEquals("GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7", exception.accountId)
        assertEquals(0, exception.operationIndex)
    }

    @Test
    fun testAccountRequiresMemoExceptionToString() {
        val exception = AccountRequiresMemoException(
            "Account requires memo",
            "GTEST",
            2
        )
        val str = exception.toString()
        assertTrue(str.contains("GTEST"))
        assertTrue(str.contains("2"))
        assertTrue(str.contains("Account requires memo"))
    }

    @Test
    fun testAccountRequiresMemoExceptionIsSdkException() {
        val exception = AccountRequiresMemoException("msg", "GTEST", 0)
        assertIs<SdkException>(exception)
    }

    @Test
    fun testAccountRequiresMemoExceptionIsException() {
        val exception = AccountRequiresMemoException("msg", "GTEST", 0)
        assertIs<Exception>(exception)
    }

    // ===== Inheritance chain =====

    @Test
    fun testExceptionHierarchy() {
        // All Network-derived exceptions are NetworkException and Exception
        val badReq = BadRequestException(code = 400, body = "")
        val badResp = BadResponseException(code = 500, body = "")
        val tooMany = TooManyRequestsException(code = 429, body = "")
        val timeout = RequestTimeoutException(code = 408, body = "")
        val unknown = UnknownResponseException(code = 600, body = "")
        val connErr = ConnectionErrorException(RuntimeException())

        assertIs<NetworkException>(badReq)
        assertIs<NetworkException>(badResp)
        assertIs<NetworkException>(tooMany)
        assertIs<NetworkException>(timeout)
        assertIs<NetworkException>(unknown)
        assertIs<NetworkException>(connErr)

        assertIs<Exception>(badReq)
        assertIs<Exception>(badResp)
        assertIs<Exception>(tooMany)
        assertIs<Exception>(timeout)
        assertIs<Exception>(unknown)
        assertIs<Exception>(connErr)

        // SdkException and AccountRequiresMemoException
        val sdk = SdkException("test")
        val memo = AccountRequiresMemoException("msg", "G", 0)
        assertIs<Exception>(sdk)
        assertIs<SdkException>(memo)
    }

    @Test
    fun testNullCodeAndBody() {
        val exception = NetworkException(message = "test", cause = null, code = null, body = null)
        assertNull(exception.code)
        assertNull(exception.body)
    }
}
