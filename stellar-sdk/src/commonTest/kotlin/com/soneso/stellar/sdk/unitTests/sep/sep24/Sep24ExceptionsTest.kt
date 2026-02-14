package com.soneso.stellar.sdk.unitTests.sep.sep24

import com.soneso.stellar.sdk.sep.sep24.exceptions.*
import kotlin.test.*

/**
 * Unit tests for SEP-24 exception classes.
 *
 * Tests cover all exception types:
 * - Sep24Exception (base)
 * - Sep24AuthenticationRequiredException
 * - Sep24InvalidRequestException
 * - Sep24TransactionNotFoundException
 * - Sep24ServerErrorException
 */
class Sep24ExceptionsTest {

    // ========== Sep24Exception (Base) ==========

    @Test
    fun testSep24Exception_message() {
        val exception = Sep24Exception("Test error")
        assertEquals("Test error", exception.message)
    }

    @Test
    fun testSep24Exception_withCause() {
        val cause = RuntimeException("Underlying cause")
        val exception = Sep24Exception("Wrapper", cause)
        assertEquals("Wrapper", exception.message)
        assertSame(cause, exception.cause)
    }

    @Test
    fun testSep24Exception_toString() {
        val exception = Sep24Exception("Test error")
        assertTrue(exception.toString().contains("SEP-24 error"))
        assertTrue(exception.toString().contains("Test error"))
    }

    @Test
    fun testSep24Exception_isException() {
        val exception = Sep24Exception("Test")
        assertTrue(exception is Exception)
        assertTrue(exception is Throwable)
    }

    // ========== Sep24AuthenticationRequiredException ==========

    @Test
    fun testSep24AuthenticationRequiredException_message() {
        val exception = Sep24AuthenticationRequiredException()
        assertEquals("Authentication required", exception.message)
    }

    @Test
    fun testSep24AuthenticationRequiredException_toString() {
        val exception = Sep24AuthenticationRequiredException()
        assertTrue(exception.toString().contains("SEP-24 authentication required"))
    }

    @Test
    fun testSep24AuthenticationRequiredException_inheritance() {
        val exception = Sep24AuthenticationRequiredException()
        assertTrue(exception is Sep24Exception)
        assertTrue(exception is Exception)
    }

    @Test
    fun testSep24AuthenticationRequiredException_canBeCaughtAsSep24Exception() {
        val caught = assertFailsWith<Sep24Exception> {
            throw Sep24AuthenticationRequiredException()
        }
        assertTrue(caught is Sep24AuthenticationRequiredException)
    }

    // ========== Sep24InvalidRequestException ==========

    @Test
    fun testSep24InvalidRequestException_message() {
        val exception = Sep24InvalidRequestException("Asset not supported")
        assertEquals("Asset not supported", exception.message)
    }

    @Test
    fun testSep24InvalidRequestException_toString() {
        val exception = Sep24InvalidRequestException("Invalid amount")
        assertTrue(exception.toString().contains("SEP-24 invalid request"))
        assertTrue(exception.toString().contains("Invalid amount"))
    }

    @Test
    fun testSep24InvalidRequestException_inheritance() {
        val exception = Sep24InvalidRequestException("Bad")
        assertTrue(exception is Sep24Exception)
        assertTrue(exception is Exception)
    }

    @Test
    fun testSep24InvalidRequestException_canBeCaughtAsSep24Exception() {
        val caught = assertFailsWith<Sep24Exception> {
            throw Sep24InvalidRequestException("Invalid")
        }
        assertTrue(caught is Sep24InvalidRequestException)
    }

    // ========== Sep24TransactionNotFoundException ==========

    @Test
    fun testSep24TransactionNotFoundException_withId() {
        val exception = Sep24TransactionNotFoundException("tx-abc-123")
        assertEquals("tx-abc-123", exception.transactionId)
        assertTrue(exception.message!!.contains("tx-abc-123"))
        assertTrue(exception.message!!.contains("not found"))
    }

    @Test
    fun testSep24TransactionNotFoundException_withoutId() {
        val exception = Sep24TransactionNotFoundException()
        assertNull(exception.transactionId)
        assertTrue(exception.message!!.contains("Transaction not found"))
    }

    @Test
    fun testSep24TransactionNotFoundException_nullId() {
        val exception = Sep24TransactionNotFoundException(null)
        assertNull(exception.transactionId)
    }

    @Test
    fun testSep24TransactionNotFoundException_toString() {
        val exception = Sep24TransactionNotFoundException("tx-99")
        assertTrue(exception.toString().contains("SEP-24 transaction not found"))
        assertTrue(exception.toString().contains("tx-99"))
    }

    @Test
    fun testSep24TransactionNotFoundException_toStringWithoutId() {
        val exception = Sep24TransactionNotFoundException()
        assertTrue(exception.toString().contains("SEP-24 transaction not found"))
    }

    @Test
    fun testSep24TransactionNotFoundException_inheritance() {
        val exception = Sep24TransactionNotFoundException()
        assertTrue(exception is Sep24Exception)
        assertTrue(exception is Exception)
    }

    // ========== Sep24ServerErrorException ==========

    @Test
    fun testSep24ServerErrorException_message() {
        val exception = Sep24ServerErrorException("Internal error", 500)
        assertEquals(500, exception.statusCode)
        assertTrue(exception.message!!.contains("500"))
        assertTrue(exception.message!!.contains("Internal error"))
    }

    @Test
    fun testSep24ServerErrorException_503() {
        val exception = Sep24ServerErrorException("Service unavailable", 503)
        assertEquals(503, exception.statusCode)
        assertTrue(exception.message!!.contains("503"))
    }

    @Test
    fun testSep24ServerErrorException_toString() {
        val exception = Sep24ServerErrorException("Bad Gateway", 502)
        assertTrue(exception.toString().contains("SEP-24 server error"))
        assertTrue(exception.toString().contains("502"))
        assertTrue(exception.toString().contains("Bad Gateway"))
    }

    @Test
    fun testSep24ServerErrorException_inheritance() {
        val exception = Sep24ServerErrorException("Error", 500)
        assertTrue(exception is Sep24Exception)
        assertTrue(exception is Exception)
    }

    @Test
    fun testSep24ServerErrorException_canBeCaughtAsSep24Exception() {
        val caught = assertFailsWith<Sep24Exception> {
            throw Sep24ServerErrorException("Down", 503)
        }
        assertTrue(caught is Sep24ServerErrorException)
        assertEquals(503, (caught as Sep24ServerErrorException).statusCode)
    }

    // ========== Exception Hierarchy ==========

    @Test
    fun testExceptionHierarchy() {
        val exceptions: List<Sep24Exception> = listOf(
            Sep24AuthenticationRequiredException(),
            Sep24InvalidRequestException("bad request"),
            Sep24TransactionNotFoundException("tx-1"),
            Sep24ServerErrorException("error", 500)
        )

        exceptions.forEach { exception ->
            assertTrue(exception is Sep24Exception, "${exception::class.simpleName} should extend Sep24Exception")
            assertTrue(exception is Exception, "${exception::class.simpleName} should extend Exception")
            assertNotNull(exception.message, "${exception::class.simpleName} should have a message")
        }
    }

    @Test
    fun testCatchingAsSep24Exception() {
        var caught = false
        try {
            throw Sep24ServerErrorException("down", 503)
        } catch (e: Sep24Exception) {
            caught = true
            assertTrue(e is Sep24ServerErrorException)
        }
        assertTrue(caught)
    }

    @Test
    fun testExceptionTypeDistinction() {
        val ex1 = Sep24AuthenticationRequiredException()
        val ex2 = Sep24InvalidRequestException("bad")
        val ex3 = Sep24TransactionNotFoundException()
        val ex4 = Sep24ServerErrorException("err", 500)

        assertTrue(ex1 is Sep24AuthenticationRequiredException)
        assertFalse(ex1 is Sep24InvalidRequestException)
        assertTrue(ex2 is Sep24InvalidRequestException)
        assertFalse(ex2 is Sep24AuthenticationRequiredException)
        assertTrue(ex3 is Sep24TransactionNotFoundException)
        assertFalse(ex3 is Sep24ServerErrorException)
        assertTrue(ex4 is Sep24ServerErrorException)
        assertFalse(ex4 is Sep24TransactionNotFoundException)
    }

    @Test
    fun testExceptionCanBeUsedInWhenExpression() {
        val exception: Sep24Exception = Sep24ServerErrorException("error", 503)

        val result = when (exception) {
            is Sep24AuthenticationRequiredException -> "auth_required"
            is Sep24InvalidRequestException -> "invalid_request"
            is Sep24TransactionNotFoundException -> "not_found"
            is Sep24ServerErrorException -> "server_error"
            else -> "unknown"
        }

        assertEquals("server_error", result)
    }
}
