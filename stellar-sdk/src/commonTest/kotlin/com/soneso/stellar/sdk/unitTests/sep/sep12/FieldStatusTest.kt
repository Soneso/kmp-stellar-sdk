// Copyright 2025 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.unitTests.sep.sep12

import com.soneso.stellar.sdk.sep.sep12.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FieldStatusTest {

    @Test
    fun testAcceptedStatus() {
        val status = FieldStatus.ACCEPTED
        assertEquals("ACCEPTED", status.name)
    }

    @Test
    fun testProcessingStatus() {
        val status = FieldStatus.PROCESSING
        assertEquals("PROCESSING", status.name)
    }

    @Test
    fun testRejectedStatus() {
        val status = FieldStatus.REJECTED
        assertEquals("REJECTED", status.name)
    }

    @Test
    fun testVerificationRequiredStatus() {
        val status = FieldStatus.VERIFICATION_REQUIRED
        assertEquals("VERIFICATION_REQUIRED", status.name)
    }

    @Test
    fun testFromStringAccepted() {
        val status = FieldStatus.fromString("ACCEPTED")
        assertEquals(FieldStatus.ACCEPTED, status)
    }

    @Test
    fun testFromStringProcessing() {
        val status = FieldStatus.fromString("PROCESSING")
        assertEquals(FieldStatus.PROCESSING, status)
    }

    @Test
    fun testFromStringRejected() {
        val status = FieldStatus.fromString("REJECTED")
        assertEquals(FieldStatus.REJECTED, status)
    }

    @Test
    fun testFromStringVerificationRequired() {
        val status = FieldStatus.fromString("VERIFICATION_REQUIRED")
        assertEquals(FieldStatus.VERIFICATION_REQUIRED, status)
    }

    @Test
    fun testFromStringCaseInsensitiveAccepted() {
        val status = FieldStatus.fromString("accepted")
        assertEquals(FieldStatus.ACCEPTED, status)
    }

    @Test
    fun testFromStringCaseInsensitiveProcessing() {
        val status = FieldStatus.fromString("Processing")
        assertEquals(FieldStatus.PROCESSING, status)
    }

    @Test
    fun testFromStringCaseInsensitiveRejected() {
        val status = FieldStatus.fromString("ReJeCtEd")
        assertEquals(FieldStatus.REJECTED, status)
    }

    @Test
    fun testFromStringCaseInsensitiveVerificationRequired() {
        val status = FieldStatus.fromString("verification_required")
        assertEquals(FieldStatus.VERIFICATION_REQUIRED, status)
    }

    @Test
    fun testFromStringInvalidThrowsException() {
        val exception = assertFailsWith<IllegalArgumentException> {
            FieldStatus.fromString("INVALID_STATUS")
        }
        assertEquals("Unknown field status: INVALID_STATUS", exception.message)
    }

    @Test
    fun testFromStringEmptyThrowsException() {
        val exception = assertFailsWith<IllegalArgumentException> {
            FieldStatus.fromString("")
        }
        assertEquals("Unknown field status: ", exception.message)
    }

    @Test
    fun testFromStringPendingThrowsException() {
        val exception = assertFailsWith<IllegalArgumentException> {
            FieldStatus.fromString("PENDING")
        }
        assertEquals("Unknown field status: PENDING", exception.message)
    }
}
