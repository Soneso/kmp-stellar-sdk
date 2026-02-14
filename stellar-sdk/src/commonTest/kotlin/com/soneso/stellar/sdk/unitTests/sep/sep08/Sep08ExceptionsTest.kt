// Copyright 2026 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.unitTests.sep.sep08

import com.soneso.stellar.sdk.sep.sep08.exceptions.*
import kotlin.test.*

/**
 * Unit tests for SEP-08 exception classes.
 *
 * Tests cover all exception types:
 * - Sep08Exception (base)
 * - Sep08IncompleteInitDataException
 * - Sep08InvalidTransactionResponseException
 * - Sep08InvalidActionResponseException
 */
class Sep08ExceptionsTest {

    // ========== Sep08Exception (Base) ==========

    @Test
    fun testSep08ExceptionMessage() {
        val exception = Sep08Exception("Test error")
        assertEquals("Test error", exception.message)
    }

    @Test
    fun testSep08ExceptionToString() {
        val exception = Sep08Exception("Test error")
        assertTrue(exception.toString().contains("SEP-08 error"))
        assertTrue(exception.toString().contains("Test error"))
    }

    @Test
    fun testSep08ExceptionWithCause() {
        val cause = RuntimeException("Underlying cause")
        val exception = Sep08Exception("Wrapper", cause)
        assertEquals("Wrapper", exception.message)
        assertNotNull(exception.cause)
        assertEquals("Underlying cause", exception.cause?.message)
    }

    @Test
    fun testSep08ExceptionIsException() {
        val exception = Sep08Exception("Test")
        assertTrue(exception is Exception)
        assertTrue(exception is Throwable)
    }

    // ========== Sep08IncompleteInitDataException ==========

    @Test
    fun testIncompleteInitDataExceptionMessage() {
        val exception = Sep08IncompleteInitDataException(
            "Network passphrase not found in stellar.toml"
        )
        assertEquals("Network passphrase not found in stellar.toml", exception.message)
    }

    @Test
    fun testIncompleteInitDataExceptionToString() {
        val exception = Sep08IncompleteInitDataException(
            "Horizon URL could not be determined"
        )
        assertTrue(exception.toString().contains("SEP-08 incomplete init data"))
        assertTrue(exception.toString().contains("Horizon URL could not be determined"))
    }

    @Test
    fun testIncompleteInitDataExceptionIsBase() {
        val exception = Sep08IncompleteInitDataException("missing data")
        assertTrue(exception is Sep08Exception)
        assertTrue(exception is Exception)
    }

    @Test
    fun testIncompleteInitDataExceptionCanBeCaughtAsSep08Exception() {
        val caught = assertFailsWith<Sep08Exception> {
            throw Sep08IncompleteInitDataException("missing network")
        }
        assertTrue(caught is Sep08IncompleteInitDataException)
    }

    // ========== Sep08InvalidTransactionResponseException ==========

    @Test
    fun testInvalidTransactionResponseExceptionMessage() {
        val exception = Sep08InvalidTransactionResponseException(
            "Missing 'status' field in approval server response"
        )
        assertEquals(
            "Missing 'status' field in approval server response",
            exception.message
        )
    }

    @Test
    fun testInvalidTransactionResponseExceptionToString() {
        val exception = Sep08InvalidTransactionResponseException(
            "Unknown status 'invalid' in response"
        )
        assertTrue(exception.toString().contains("SEP-08 invalid transaction response"))
        assertTrue(exception.toString().contains("Unknown status 'invalid' in response"))
    }

    @Test
    fun testInvalidTransactionResponseExceptionIsBase() {
        val exception = Sep08InvalidTransactionResponseException("bad response")
        assertTrue(exception is Sep08Exception)
        assertTrue(exception is Exception)
    }

    @Test
    fun testInvalidTransactionResponseExceptionCanBeCaughtAsSep08Exception() {
        val caught = assertFailsWith<Sep08Exception> {
            throw Sep08InvalidTransactionResponseException("malformed")
        }
        assertTrue(caught is Sep08InvalidTransactionResponseException)
    }

    // ========== Sep08InvalidActionResponseException ==========

    @Test
    fun testInvalidActionResponseExceptionMessage() {
        val exception = Sep08InvalidActionResponseException(
            "Missing 'result' field in action URL response"
        )
        assertEquals(
            "Missing 'result' field in action URL response",
            exception.message
        )
    }

    @Test
    fun testInvalidActionResponseExceptionToString() {
        val exception = Sep08InvalidActionResponseException(
            "Unknown result 'bad_value' in action URL response"
        )
        assertTrue(exception.toString().contains("SEP-08 invalid action response"))
        assertTrue(exception.toString().contains("Unknown result 'bad_value' in action URL response"))
    }

    @Test
    fun testInvalidActionResponseExceptionIsBase() {
        val exception = Sep08InvalidActionResponseException("bad action response")
        assertTrue(exception is Sep08Exception)
        assertTrue(exception is Exception)
    }

    @Test
    fun testInvalidActionResponseExceptionCanBeCaughtAsSep08Exception() {
        val caught = assertFailsWith<Sep08Exception> {
            throw Sep08InvalidActionResponseException("malformed action")
        }
        assertTrue(caught is Sep08InvalidActionResponseException)
    }

    // ========== Exception Hierarchy ==========

    @Test
    fun testExceptionHierarchy() {
        val exceptions: List<Sep08Exception> = listOf(
            Sep08IncompleteInitDataException("missing"),
            Sep08InvalidTransactionResponseException("bad tx response"),
            Sep08InvalidActionResponseException("bad action response")
        )

        exceptions.forEach { exception ->
            assertTrue(
                exception is Sep08Exception,
                "${exception::class.simpleName} should extend Sep08Exception"
            )
            assertTrue(
                exception is Exception,
                "${exception::class.simpleName} should extend Exception"
            )
            assertNotNull(
                exception.message,
                "${exception::class.simpleName} should have a message"
            )
        }
    }

    @Test
    fun testCatchingAsSep08Exception() {
        var caught = false
        try {
            throw Sep08InvalidTransactionResponseException("Invalid")
        } catch (e: Sep08Exception) {
            caught = true
            assertTrue(e is Sep08InvalidTransactionResponseException)
        }
        assertTrue(caught)
    }

    @Test
    fun testAllExceptionsHaveDistinctToStringFormats() {
        val base = Sep08Exception("test")
        val init = Sep08IncompleteInitDataException("test")
        val tx = Sep08InvalidTransactionResponseException("test")
        val action = Sep08InvalidActionResponseException("test")

        assertTrue(base.toString().contains("SEP-08 error"))
        assertTrue(init.toString().contains("SEP-08 incomplete init data"))
        assertTrue(tx.toString().contains("SEP-08 invalid transaction response"))
        assertTrue(action.toString().contains("SEP-08 invalid action response"))

        // Each toString is distinct
        assertNotEquals(base.toString(), init.toString())
        assertNotEquals(init.toString(), tx.toString())
        assertNotEquals(tx.toString(), action.toString())
    }
}
