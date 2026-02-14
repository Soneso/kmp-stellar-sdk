package com.soneso.stellar.sdk.unitTests.sep.sep12

import com.soneso.stellar.sdk.sep.sep12.exceptions.*
import kotlin.test.*

/**
 * Unit tests for SEP-12 KYC exception classes.
 *
 * Tests cover all exception types:
 * - KYCException (base)
 * - CustomerNotFoundException
 * - UnauthorizedException
 * - FileTooLargeException
 * - CustomerAlreadyExistsException
 * - InvalidFieldException
 */
class Sep12ExceptionsTest {

    // ========== KYCException (Base) ==========

    @Test
    fun testKYCException_message() {
        val exception = KYCException("KYC error occurred")
        assertEquals("KYC error occurred", exception.message)
    }

    @Test
    fun testKYCException_withCause() {
        val cause = RuntimeException("Underlying cause")
        val exception = KYCException("Wrapper", cause)
        assertEquals("Wrapper", exception.message)
        assertSame(cause, exception.cause)
    }

    @Test
    fun testKYCException_isException() {
        val exception = KYCException("Test")
        assertTrue(exception is Exception)
        assertTrue(exception is Throwable)
    }

    // ========== CustomerNotFoundException ==========

    @Test
    fun testCustomerNotFoundException_message() {
        val exception = CustomerNotFoundException("GABC123")
        assertTrue(exception.message!!.contains("GABC123"))
        assertTrue(exception.message!!.contains("not found"))
    }

    @Test
    fun testCustomerNotFoundException_accountIdProperty() {
        val exception = CustomerNotFoundException("GDEF456")
        assertEquals("GDEF456", exception.accountId)
    }

    @Test
    fun testCustomerNotFoundException_inheritance() {
        val exception = CustomerNotFoundException("GTEST")
        assertTrue(exception is KYCException)
        assertTrue(exception is Exception)
    }

    @Test
    fun testCustomerNotFoundException_canBeCaughtAsKYCException() {
        val caught = assertFailsWith<KYCException> {
            throw CustomerNotFoundException("GABC")
        }
        assertTrue(caught is CustomerNotFoundException)
    }

    // ========== UnauthorizedException ==========

    @Test
    fun testUnauthorizedException_message() {
        val exception = UnauthorizedException()
        assertTrue(exception.message!!.contains("401"))
        assertTrue(exception.message!!.contains("Unauthorized"))
    }

    @Test
    fun testUnauthorizedException_inheritance() {
        val exception = UnauthorizedException()
        assertTrue(exception is KYCException)
        assertTrue(exception is Exception)
    }

    @Test
    fun testUnauthorizedException_canBeCaughtAsKYCException() {
        val caught = assertFailsWith<KYCException> {
            throw UnauthorizedException()
        }
        assertTrue(caught is UnauthorizedException)
    }

    // ========== FileTooLargeException ==========

    @Test
    fun testFileTooLargeException_withFileSize() {
        val exception = FileTooLargeException(10_000_000L)
        assertEquals(10_000_000L, exception.fileSize)
        assertTrue(exception.message!!.contains("413"))
        assertTrue(exception.message!!.contains("Too Large"))
    }

    @Test
    fun testFileTooLargeException_withoutFileSize() {
        val exception = FileTooLargeException()
        assertNull(exception.fileSize)
        assertTrue(exception.message!!.contains("413"))
    }

    @Test
    fun testFileTooLargeException_nullFileSize() {
        val exception = FileTooLargeException(null)
        assertNull(exception.fileSize)
    }

    @Test
    fun testFileTooLargeException_inheritance() {
        val exception = FileTooLargeException(1000L)
        assertTrue(exception is KYCException)
        assertTrue(exception is Exception)
    }

    @Test
    fun testFileTooLargeException_fileSizeInMessage() {
        val exception = FileTooLargeException(5_242_880L)
        // 5242880 bytes = ~5 MB
        assertTrue(exception.message!!.contains("MB"))
    }

    // ========== CustomerAlreadyExistsException ==========

    @Test
    fun testCustomerAlreadyExistsException_withExistingId() {
        val exception = CustomerAlreadyExistsException("cust-12345")
        assertEquals("cust-12345", exception.existingCustomerId)
        assertTrue(exception.message!!.contains("409"))
        assertTrue(exception.message!!.contains("cust-12345"))
    }

    @Test
    fun testCustomerAlreadyExistsException_withoutExistingId() {
        val exception = CustomerAlreadyExistsException()
        assertNull(exception.existingCustomerId)
        assertTrue(exception.message!!.contains("409"))
    }

    @Test
    fun testCustomerAlreadyExistsException_nullExistingId() {
        val exception = CustomerAlreadyExistsException(null)
        assertNull(exception.existingCustomerId)
    }

    @Test
    fun testCustomerAlreadyExistsException_inheritance() {
        val exception = CustomerAlreadyExistsException()
        assertTrue(exception is KYCException)
        assertTrue(exception is Exception)
    }

    // ========== InvalidFieldException ==========

    @Test
    fun testInvalidFieldException_withBothProperties() {
        val exception = InvalidFieldException(
            fieldName = "email_address",
            fieldError = "Invalid email format"
        )

        assertEquals("email_address", exception.fieldName)
        assertEquals("Invalid email format", exception.fieldError)
        assertTrue(exception.message!!.contains("email_address"))
        assertTrue(exception.message!!.contains("Invalid email format"))
    }

    @Test
    fun testInvalidFieldException_fieldNameOnly() {
        val exception = InvalidFieldException(fieldName = "mobile_number")
        assertEquals("mobile_number", exception.fieldName)
        assertNull(exception.fieldError)
        assertTrue(exception.message!!.contains("mobile_number"))
    }

    @Test
    fun testInvalidFieldException_fieldErrorOnly() {
        val exception = InvalidFieldException(fieldError = "Value too long")
        assertNull(exception.fieldName)
        assertEquals("Value too long", exception.fieldError)
        assertTrue(exception.message!!.contains("Value too long"))
    }

    @Test
    fun testInvalidFieldException_noProperties() {
        val exception = InvalidFieldException()
        assertNull(exception.fieldName)
        assertNull(exception.fieldError)
        assertTrue(exception.message!!.contains("400"))
    }

    @Test
    fun testInvalidFieldException_inheritance() {
        val exception = InvalidFieldException()
        assertTrue(exception is KYCException)
        assertTrue(exception is Exception)
    }

    // ========== Exception Hierarchy ==========

    @Test
    fun testExceptionHierarchy() {
        val exceptions: List<KYCException> = listOf(
            CustomerNotFoundException("GTEST"),
            UnauthorizedException(),
            FileTooLargeException(1000L),
            CustomerAlreadyExistsException("id"),
            InvalidFieldException("field", "error")
        )

        exceptions.forEach { exception ->
            assertTrue(exception is KYCException, "${exception::class.simpleName} should extend KYCException")
            assertTrue(exception is Exception, "${exception::class.simpleName} should extend Exception")
            assertNotNull(exception.message, "${exception::class.simpleName} should have a message")
        }
    }

    @Test
    fun testCatchingAsKYCException() {
        var caught = false
        try {
            throw CustomerNotFoundException("GABC")
        } catch (e: KYCException) {
            caught = true
            assertTrue(e is CustomerNotFoundException)
        }
        assertTrue(caught)
    }

    @Test
    fun testExceptionTypeDistinction() {
        val ex1 = CustomerNotFoundException("GTEST")
        val ex2 = UnauthorizedException()
        val ex3 = FileTooLargeException()
        val ex4 = CustomerAlreadyExistsException()
        val ex5 = InvalidFieldException()

        assertTrue(ex1 is CustomerNotFoundException)
        assertFalse(ex1 is UnauthorizedException)
        assertTrue(ex2 is UnauthorizedException)
        assertFalse(ex2 is CustomerNotFoundException)
        assertTrue(ex3 is FileTooLargeException)
        assertFalse(ex3 is CustomerAlreadyExistsException)
        assertTrue(ex4 is CustomerAlreadyExistsException)
        assertFalse(ex4 is FileTooLargeException)
        assertTrue(ex5 is InvalidFieldException)
        assertFalse(ex5 is CustomerNotFoundException)
    }

    @Test
    fun testExceptionCanBeUsedInWhenExpression() {
        val exception: KYCException = InvalidFieldException("email", "bad format")

        val result = when (exception) {
            is CustomerNotFoundException -> "not_found"
            is UnauthorizedException -> "unauthorized"
            is FileTooLargeException -> "too_large"
            is CustomerAlreadyExistsException -> "already_exists"
            is InvalidFieldException -> "invalid_field"
            else -> "unknown"
        }

        assertEquals("invalid_field", result)
    }
}
