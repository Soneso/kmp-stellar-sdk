// Copyright 2025 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.unitTests.sep.sep38

import com.soneso.stellar.sdk.sep.sep38.*
import com.soneso.stellar.sdk.sep.sep38.exceptions.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Sep38ExceptionsTest {

    @Test
    fun testSep38Exception() {
        val exception = Sep38Exception("Test error")
        assertEquals("Test error", exception.message)
        assertTrue(exception.toString().contains("SEP-38 error"))
    }

    @Test
    fun testSep38BadRequestException() {
        val error = "Invalid asset format"
        val exception = Sep38BadRequestException(error)

        assertEquals(error, exception.error)
        assertTrue(exception.message?.contains("Bad request (400)") == true)
        assertTrue(exception.message?.contains(error) == true)
        assertTrue(exception.toString().contains("bad request"))
        assertTrue(exception.toString().contains(error))
    }

    @Test
    fun testSep38PermissionDeniedException() {
        val error = "Token expired"
        val exception = Sep38PermissionDeniedException(error)

        assertEquals(error, exception.error)
        assertTrue(exception.message?.contains("Permission denied (403)") == true)
        assertTrue(exception.message?.contains(error) == true)
        assertTrue(exception.toString().contains("permission denied"))
        assertTrue(exception.toString().contains(error))
    }

    @Test
    fun testSep38NotFoundException() {
        val error = "Quote ID does not exist"
        val exception = Sep38NotFoundException(error)

        assertEquals(error, exception.error)
        assertTrue(exception.message?.contains("Quote not found (404)") == true)
        assertTrue(exception.message?.contains(error) == true)
        assertTrue(exception.toString().contains("not found"))
        assertTrue(exception.toString().contains(error))
    }

    @Test
    fun testSep38UnknownResponseException() {
        val statusCode = 500
        val body = "Internal server error"
        val exception = Sep38UnknownResponseException(statusCode, body)

        assertEquals(statusCode, exception.statusCode)
        assertEquals(body, exception.responseBody)
        assertTrue(exception.message?.contains("Unknown response") == true)
        assertTrue(exception.message?.contains(statusCode.toString()) == true)
        assertTrue(exception.toString().contains("unknown response"))
        assertTrue(exception.toString().contains("500"))
        assertTrue(exception.toString().contains(body))
    }

    @Test
    fun testExceptionHierarchy() {
        val badRequest = Sep38BadRequestException("Invalid parameter")
        assertTrue(badRequest is Sep38Exception)
        assertTrue(badRequest is Exception)

        val permissionDenied = Sep38PermissionDeniedException("No access")
        assertTrue(permissionDenied is Sep38Exception)
        assertTrue(permissionDenied is Exception)

        val notFound = Sep38NotFoundException("Not found")
        assertTrue(notFound is Sep38Exception)
        assertTrue(notFound is Exception)

        val unknown = Sep38UnknownResponseException(500, "Server error")
        assertTrue(unknown is Sep38Exception)
        assertTrue(unknown is Exception)
    }

    @Test
    fun testCatchingAsSep38Exception() {
        var caught = false

        try {
            throw Sep38BadRequestException("Test")
        } catch (e: Sep38Exception) {
            caught = true
            assertTrue(e.message?.contains("Bad request") == true)
        }

        assertTrue(caught)
    }

    @Test
    fun testUnknownResponseExceptionNotExtendingSep38Exception() {
        // Sep38UnknownResponseException extends Sep38Exception directly
        val exception = Sep38UnknownResponseException(503, "Service unavailable")
        assertTrue(exception is Sep38Exception)
    }
}
