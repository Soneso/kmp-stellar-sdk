package com.soneso.stellar.sdk.unitTests.sep.sep06

import com.soneso.stellar.sdk.sep.sep06.exceptions.*
import kotlin.test.*

/**
 * Unit tests for SEP-06 exception classes.
 *
 * Tests cover all exception types:
 * - Sep06Exception (base)
 * - Sep06AuthenticationRequiredException
 * - Sep06CustomerInformationNeededException
 * - Sep06CustomerInformationStatusException
 * - Sep06InvalidRequestException
 * - Sep06TransactionNotFoundException
 * - Sep06ServerErrorException
 */
class Sep06ExceptionsTest {

    // ========== Sep06Exception (Base) ==========

    @Test
    fun testSep06Exception_message() {
        val exception = Sep06Exception("Test error")
        assertEquals("Test error", exception.message)
    }

    @Test
    fun testSep06Exception_withCause() {
        val cause = RuntimeException("Underlying cause")
        val exception = Sep06Exception("Wrapper", cause)
        assertEquals("Wrapper", exception.message)
        assertNotNull(exception.cause)
        assertEquals("Underlying cause", exception.cause?.message)
    }

    @Test
    fun testSep06Exception_toString() {
        val exception = Sep06Exception("Test error")
        assertTrue(exception.toString().contains("SEP-06 error"))
        assertTrue(exception.toString().contains("Test error"))
    }

    @Test
    fun testSep06Exception_isException() {
        val exception = Sep06Exception("Test")
        assertTrue(exception is Exception)
        assertTrue(exception is Throwable)
    }

    // ========== Sep06AuthenticationRequiredException ==========

    @Test
    fun testSep06AuthenticationRequiredException_message() {
        val exception = Sep06AuthenticationRequiredException()
        assertEquals("Authentication required", exception.message)
    }

    @Test
    fun testSep06AuthenticationRequiredException_toString() {
        val exception = Sep06AuthenticationRequiredException()
        assertTrue(exception.toString().contains("SEP-06 authentication required"))
    }

    @Test
    fun testSep06AuthenticationRequiredException_inheritance() {
        val exception = Sep06AuthenticationRequiredException()
        assertTrue(exception is Sep06Exception)
        assertTrue(exception is Exception)
    }

    @Test
    fun testSep06AuthenticationRequiredException_canBeCaughtAsSep06Exception() {
        val caught = assertFailsWith<Sep06Exception> {
            throw Sep06AuthenticationRequiredException()
        }
        assertTrue(caught is Sep06AuthenticationRequiredException)
    }

    // ========== Sep06CustomerInformationNeededException ==========

    @Test
    fun testSep06CustomerInformationNeededException_fields() {
        val fields = listOf("first_name", "last_name", "email_address")
        val exception = Sep06CustomerInformationNeededException(fields)

        assertEquals(fields, exception.fields)
        assertTrue(exception.message!!.contains("first_name"))
        assertTrue(exception.message!!.contains("last_name"))
        assertTrue(exception.message!!.contains("email_address"))
    }

    @Test
    fun testSep06CustomerInformationNeededException_emptyFields() {
        val exception = Sep06CustomerInformationNeededException(emptyList())
        assertEquals(emptyList(), exception.fields)
    }

    @Test
    fun testSep06CustomerInformationNeededException_toString() {
        val exception = Sep06CustomerInformationNeededException(listOf("email_address"))
        assertTrue(exception.toString().contains("SEP-06 customer information needed"))
        assertTrue(exception.toString().contains("email_address"))
    }

    @Test
    fun testSep06CustomerInformationNeededException_inheritance() {
        val exception = Sep06CustomerInformationNeededException(listOf("name"))
        assertTrue(exception is Sep06Exception)
    }

    // ========== Sep06CustomerInformationStatusException ==========

    @Test
    fun testSep06CustomerInformationStatusException_pending() {
        val exception = Sep06CustomerInformationStatusException(
            status = "pending",
            moreInfoUrl = "https://example.com/kyc-status",
            eta = 300
        )

        assertEquals("pending", exception.status)
        assertEquals("https://example.com/kyc-status", exception.moreInfoUrl)
        assertEquals(300L, exception.eta)
    }

    @Test
    fun testSep06CustomerInformationStatusException_denied() {
        val exception = Sep06CustomerInformationStatusException(
            status = "denied",
            moreInfoUrl = "https://example.com/denied-info"
        )

        assertEquals("denied", exception.status)
        assertNull(exception.eta)
    }

    @Test
    fun testSep06CustomerInformationStatusException_noMoreInfoUrl() {
        val exception = Sep06CustomerInformationStatusException(status = "pending")
        assertNull(exception.moreInfoUrl)
        assertNull(exception.eta)
    }

    @Test
    fun testSep06CustomerInformationStatusException_messageContainsStatus() {
        val exception = Sep06CustomerInformationStatusException(
            status = "pending",
            moreInfoUrl = "https://example.com"
        )
        assertTrue(exception.message!!.contains("pending"))
        assertTrue(exception.message!!.contains("https://example.com"))
    }

    @Test
    fun testSep06CustomerInformationStatusException_toString() {
        val exception = Sep06CustomerInformationStatusException(
            status = "denied",
            eta = 60
        )
        assertTrue(exception.toString().contains("SEP-06 customer information status"))
        assertTrue(exception.toString().contains("denied"))
        assertTrue(exception.toString().contains("60"))
    }

    @Test
    fun testSep06CustomerInformationStatusException_inheritance() {
        val exception = Sep06CustomerInformationStatusException(status = "pending")
        assertTrue(exception is Sep06Exception)
    }

    // ========== Sep06InvalidRequestException ==========

    @Test
    fun testSep06InvalidRequestException_message() {
        val exception = Sep06InvalidRequestException("Asset code not supported")
        assertEquals("Asset code not supported", exception.errorMessage)
        assertEquals("Asset code not supported", exception.message)
    }

    @Test
    fun testSep06InvalidRequestException_toString() {
        val exception = Sep06InvalidRequestException("Invalid amount")
        assertTrue(exception.toString().contains("SEP-06 invalid request"))
        assertTrue(exception.toString().contains("Invalid amount"))
    }

    @Test
    fun testSep06InvalidRequestException_inheritance() {
        val exception = Sep06InvalidRequestException("Bad request")
        assertTrue(exception is Sep06Exception)
    }

    // ========== Sep06TransactionNotFoundException ==========

    @Test
    fun testSep06TransactionNotFoundException_withId() {
        val exception = Sep06TransactionNotFoundException("tx-12345")
        assertEquals("tx-12345", exception.transactionId)
        assertTrue(exception.message!!.contains("tx-12345"))
    }

    @Test
    fun testSep06TransactionNotFoundException_withoutId() {
        val exception = Sep06TransactionNotFoundException()
        assertNull(exception.transactionId)
        assertTrue(exception.message!!.contains("Transaction not found"))
    }

    @Test
    fun testSep06TransactionNotFoundException_nullId() {
        val exception = Sep06TransactionNotFoundException(null)
        assertNull(exception.transactionId)
    }

    @Test
    fun testSep06TransactionNotFoundException_toString() {
        val exception = Sep06TransactionNotFoundException("tx-99")
        assertTrue(exception.toString().contains("SEP-06 transaction not found"))
        assertTrue(exception.toString().contains("tx-99"))
    }

    @Test
    fun testSep06TransactionNotFoundException_inheritance() {
        val exception = Sep06TransactionNotFoundException()
        assertTrue(exception is Sep06Exception)
    }

    // ========== Sep06ServerErrorException ==========

    @Test
    fun testSep06ServerErrorException_withMessage() {
        val exception = Sep06ServerErrorException(500, "Internal Server Error")
        assertEquals(500, exception.statusCode)
        assertEquals("Internal Server Error", exception.errorMessage)
        assertTrue(exception.message!!.contains("500"))
        assertTrue(exception.message!!.contains("Internal Server Error"))
    }

    @Test
    fun testSep06ServerErrorException_withoutMessage() {
        val exception = Sep06ServerErrorException(503)
        assertEquals(503, exception.statusCode)
        assertNull(exception.errorMessage)
        assertTrue(exception.message!!.contains("503"))
    }

    @Test
    fun testSep06ServerErrorException_toString() {
        val exception = Sep06ServerErrorException(502, "Bad Gateway")
        assertTrue(exception.toString().contains("SEP-06 server error"))
        assertTrue(exception.toString().contains("502"))
        assertTrue(exception.toString().contains("Bad Gateway"))
    }

    @Test
    fun testSep06ServerErrorException_inheritance() {
        val exception = Sep06ServerErrorException(500)
        assertTrue(exception is Sep06Exception)
    }

    // ========== Exception Hierarchy ==========

    @Test
    fun testExceptionHierarchy() {
        val exceptions: List<Sep06Exception> = listOf(
            Sep06AuthenticationRequiredException(),
            Sep06CustomerInformationNeededException(listOf("name")),
            Sep06CustomerInformationStatusException("pending"),
            Sep06InvalidRequestException("bad"),
            Sep06TransactionNotFoundException("tx-1"),
            Sep06ServerErrorException(500)
        )

        exceptions.forEach { exception ->
            assertTrue(exception is Sep06Exception, "${exception::class.simpleName} should extend Sep06Exception")
            assertTrue(exception is Exception, "${exception::class.simpleName} should extend Exception")
            assertNotNull(exception.message, "${exception::class.simpleName} should have a message")
        }
    }

    @Test
    fun testCatchingAsSep06Exception() {
        var caught = false
        try {
            throw Sep06InvalidRequestException("Invalid")
        } catch (e: Sep06Exception) {
            caught = true
            assertTrue(e is Sep06InvalidRequestException)
        }
        assertTrue(caught)
    }
}
