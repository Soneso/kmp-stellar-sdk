// Copyright 2026 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.unitTests.sep.sep30

import com.soneso.stellar.sdk.sep.sep30.exceptions.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/**
 * Unit tests for SEP-30 exception classes.
 *
 * Tests cover all exception types:
 * - Sep30Exception (base)
 * - Sep30BadRequestException (HTTP 400)
 * - Sep30UnauthorizedException (HTTP 401)
 * - Sep30NotFoundException (HTTP 404)
 * - Sep30ConflictException (HTTP 409)
 * - Sep30UnknownResponseException (unexpected HTTP status)
 * - Sep30InvalidResponseException (malformed HTTP 200 body)
 */
class Sep30ExceptionsTest {

    // ========== Sep30Exception (Base) ==========

    @Test
    fun testSep30ExceptionMessage() = runTest {
        val exception = Sep30Exception("test error")
        assertEquals("test error", exception.message)
    }

    @Test
    fun testSep30ExceptionToString() = runTest {
        val exception = Sep30Exception("recovery server unreachable")
        assertTrue(exception.toString().contains("SEP-30 error"))
        assertTrue(exception.toString().contains("recovery server unreachable"))
    }

    @Test
    fun testSep30ExceptionWithCause() = runTest {
        val cause = RuntimeException("connection refused")
        val exception = Sep30Exception("recovery server unreachable", cause)
        assertEquals("recovery server unreachable", exception.message)
        assertNotNull(exception.cause)
        assertEquals("connection refused", exception.cause?.message)
    }

    @Test
    fun testSep30ExceptionIsException() = runTest {
        val exception = Sep30Exception("test")
        assertTrue(exception is Exception)
        assertTrue(exception is Throwable)
    }

    // ========== Sep30BadRequestException ==========

    @Test
    fun testSep30BadRequestExceptionMessage() = runTest {
        val exception = Sep30BadRequestException("missing 'identities' field in request body")
        assertEquals("missing 'identities' field in request body", exception.message)
    }

    @Test
    fun testSep30BadRequestExceptionToString() = runTest {
        val exception = Sep30BadRequestException("invalid Stellar address format")
        assertTrue(exception.toString().contains("SEP-30 bad request"))
        assertTrue(exception.toString().contains("invalid Stellar address format"))
    }

    @Test
    fun testSep30BadRequestExceptionIsBaseException() = runTest {
        val exception = Sep30BadRequestException("bad request")
        assertTrue(exception is Sep30Exception)
        assertTrue(exception is Exception)
    }

    @Test
    fun testSep30BadRequestExceptionCanBeCaughtAsSep30Exception() = runTest {
        val caught = assertFailsWith<Sep30Exception> {
            throw Sep30BadRequestException("missing required field")
        }
        assertTrue(caught is Sep30BadRequestException)
    }

    // ========== Sep30UnauthorizedException ==========

    @Test
    fun testSep30UnauthorizedExceptionMessage() = runTest {
        val exception = Sep30UnauthorizedException("SEP-10 token expired")
        assertEquals("SEP-10 token expired", exception.message)
    }

    @Test
    fun testSep30UnauthorizedExceptionToString() = runTest {
        val exception = Sep30UnauthorizedException("missing authentication token")
        assertTrue(exception.toString().contains("SEP-30 unauthorized"))
        assertTrue(exception.toString().contains("missing authentication token"))
    }

    @Test
    fun testSep30UnauthorizedExceptionCanBeCaughtAsSep30Exception() = runTest {
        val caught = assertFailsWith<Sep30Exception> {
            throw Sep30UnauthorizedException("invalid credentials")
        }
        assertTrue(caught is Sep30UnauthorizedException)
    }

    // ========== Sep30NotFoundException ==========

    @Test
    fun testSep30NotFoundExceptionMessage() = runTest {
        val exception = Sep30NotFoundException("account not registered")
        assertEquals("account not registered", exception.message)
    }

    @Test
    fun testSep30NotFoundExceptionToString() = runTest {
        val exception = Sep30NotFoundException("account GABC... not found")
        assertTrue(exception.toString().contains("SEP-30 not found"))
        assertTrue(exception.toString().contains("account GABC... not found"))
    }

    @Test
    fun testSep30NotFoundExceptionCanBeCaughtAsSep30Exception() = runTest {
        val caught = assertFailsWith<Sep30Exception> {
            throw Sep30NotFoundException("no such account")
        }
        assertTrue(caught is Sep30NotFoundException)
    }

    // ========== Sep30ConflictException ==========

    @Test
    fun testSep30ConflictExceptionMessage() = runTest {
        val exception = Sep30ConflictException("account already registered")
        assertEquals("account already registered", exception.message)
    }

    @Test
    fun testSep30ConflictExceptionToString() = runTest {
        val exception = Sep30ConflictException("registration already exists")
        assertTrue(exception.toString().contains("SEP-30 conflict"))
        assertTrue(exception.toString().contains("registration already exists"))
    }

    @Test
    fun testSep30ConflictExceptionCanBeCaughtAsSep30Exception() = runTest {
        val caught = assertFailsWith<Sep30Exception> {
            throw Sep30ConflictException("duplicate registration")
        }
        assertTrue(caught is Sep30ConflictException)
    }

    // ========== Sep30UnknownResponseException ==========

    @Test
    fun testSep30UnknownResponseExceptionProperties() = runTest {
        val exception = Sep30UnknownResponseException(
            statusCode = 503,
            responseBody = """{"error": "service unavailable"}""",
            message = "unexpected HTTP status"
        )
        assertEquals(503, exception.statusCode)
        assertEquals("""{"error": "service unavailable"}""", exception.responseBody)
        assertEquals("unexpected HTTP status", exception.message)
    }

    @Test
    fun testSep30UnknownResponseExceptionToString() = runTest {
        val exception = Sep30UnknownResponseException(
            statusCode = 500,
            responseBody = "Internal Server Error",
            message = "recovery server error"
        )
        assertTrue(exception.toString().contains("SEP-30 unknown response"))
        assertTrue(exception.toString().contains("HTTP 500"))
        assertTrue(exception.toString().contains("recovery server error"))
    }

    @Test
    fun testSep30UnknownResponseExceptionCanBeCaughtAsSep30Exception() = runTest {
        val caught = assertFailsWith<Sep30Exception> {
            throw Sep30UnknownResponseException(
                statusCode = 502,
                responseBody = "Bad Gateway",
                message = "gateway error"
            )
        }
        assertTrue(caught is Sep30UnknownResponseException)
    }

    @Test
    fun testSep30UnknownResponseExceptionDifferentStatusCodes() = runTest {
        val error500 = Sep30UnknownResponseException(500, "Internal Error", "server error")
        val error502 = Sep30UnknownResponseException(502, "Bad Gateway", "gateway error")
        val error503 = Sep30UnknownResponseException(503, "Unavailable", "service down")

        assertEquals(500, error500.statusCode)
        assertEquals(502, error502.statusCode)
        assertEquals(503, error503.statusCode)

        assertTrue(error500.toString().contains("HTTP 500"))
        assertTrue(error502.toString().contains("HTTP 502"))
        assertTrue(error503.toString().contains("HTTP 503"))
    }

    // ========== Sep30InvalidResponseException ==========

    @Test
    fun testSep30InvalidResponseExceptionMessage() = runTest {
        val exception = Sep30InvalidResponseException(
            "missing 'address' field in response"
        )
        assertEquals("missing 'address' field in response", exception.message)
    }

    @Test
    fun testSep30InvalidResponseExceptionToString() = runTest {
        val exception = Sep30InvalidResponseException("malformed JSON in response body")
        assertTrue(exception.toString().contains("SEP-30 invalid response"))
        assertTrue(exception.toString().contains("malformed JSON in response body"))
    }

    @Test
    fun testSep30InvalidResponseExceptionCanBeCaughtAsSep30Exception() = runTest {
        val caught = assertFailsWith<Sep30Exception> {
            throw Sep30InvalidResponseException("unparseable response")
        }
        assertTrue(caught is Sep30InvalidResponseException)
    }

    // ========== Exception Hierarchy ==========

    @Test
    fun testSep30ExceptionHierarchy() = runTest {
        val badRequest = Sep30BadRequestException("bad")
        val unauthorized = Sep30UnauthorizedException("unauth")
        val notFound = Sep30NotFoundException("missing")
        val conflict = Sep30ConflictException("exists")
        val unknown = Sep30UnknownResponseException(500, "body", "error")
        val invalid = Sep30InvalidResponseException("malformed")

        assertIs<Sep30Exception>(badRequest)
        assertIs<Sep30Exception>(unauthorized)
        assertIs<Sep30Exception>(notFound)
        assertIs<Sep30Exception>(conflict)
        assertIs<Sep30Exception>(unknown)
        assertIs<Sep30Exception>(invalid)
    }

    @Test
    fun testSep30ExceptionsPreserveMessage() = runTest {
        val message = "detailed error description"

        val exceptions: List<Sep30Exception> = listOf(
            Sep30Exception(message),
            Sep30BadRequestException(message),
            Sep30UnauthorizedException(message),
            Sep30NotFoundException(message),
            Sep30ConflictException(message),
            Sep30UnknownResponseException(500, "body", message),
            Sep30InvalidResponseException(message)
        )

        exceptions.forEach { exception ->
            assertEquals(
                message,
                exception.message,
                "${exception::class.simpleName} should preserve message"
            )
        }
    }

    @Test
    fun testAllExceptionsHaveDistinctToStringFormats() = runTest {
        val base = Sep30Exception("test")
        val badRequest = Sep30BadRequestException("test")
        val unauthorized = Sep30UnauthorizedException("test")
        val notFound = Sep30NotFoundException("test")
        val conflict = Sep30ConflictException("test")
        val unknown = Sep30UnknownResponseException(500, "body", "test")
        val invalid = Sep30InvalidResponseException("test")

        assertTrue(base.toString().contains("SEP-30 error"))
        assertTrue(badRequest.toString().contains("SEP-30 bad request"))
        assertTrue(unauthorized.toString().contains("SEP-30 unauthorized"))
        assertTrue(notFound.toString().contains("SEP-30 not found"))
        assertTrue(conflict.toString().contains("SEP-30 conflict"))
        assertTrue(unknown.toString().contains("SEP-30 unknown response"))
        assertTrue(invalid.toString().contains("SEP-30 invalid response"))

        // Each toString is distinct
        val toStrings = listOf(
            base.toString(),
            badRequest.toString(),
            unauthorized.toString(),
            notFound.toString(),
            conflict.toString(),
            unknown.toString(),
            invalid.toString()
        )
        assertEquals(toStrings.size, toStrings.toSet().size, "All toString formats should be distinct")
    }

    @Test
    fun testCatchingAsSep30Exception() = runTest {
        var caught = false
        try {
            throw Sep30ConflictException("account already registered")
        } catch (e: Sep30Exception) {
            caught = true
            assertTrue(e is Sep30ConflictException)
        }
        assertTrue(caught)
    }
}
