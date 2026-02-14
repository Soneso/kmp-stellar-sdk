// Copyright 2025 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.unitTests.sep.sep12

import com.soneso.stellar.sdk.sep.sep12.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CustomerStatusTest {

    @Test
    fun testAcceptedStatus() {
        val status = CustomerStatus.ACCEPTED
        assertEquals("ACCEPTED", status.name)
    }

    @Test
    fun testProcessingStatus() {
        val status = CustomerStatus.PROCESSING
        assertEquals("PROCESSING", status.name)
    }

    @Test
    fun testNeedsInfoStatus() {
        val status = CustomerStatus.NEEDS_INFO
        assertEquals("NEEDS_INFO", status.name)
    }

    @Test
    fun testRejectedStatus() {
        val status = CustomerStatus.REJECTED
        assertEquals("REJECTED", status.name)
    }

    @Test
    fun testFromStringAccepted() {
        val status = CustomerStatus.fromString("ACCEPTED")
        assertEquals(CustomerStatus.ACCEPTED, status)
    }

    @Test
    fun testFromStringProcessing() {
        val status = CustomerStatus.fromString("PROCESSING")
        assertEquals(CustomerStatus.PROCESSING, status)
    }

    @Test
    fun testFromStringNeedsInfo() {
        val status = CustomerStatus.fromString("NEEDS_INFO")
        assertEquals(CustomerStatus.NEEDS_INFO, status)
    }

    @Test
    fun testFromStringRejected() {
        val status = CustomerStatus.fromString("REJECTED")
        assertEquals(CustomerStatus.REJECTED, status)
    }

    @Test
    fun testFromStringCaseInsensitiveAccepted() {
        val status = CustomerStatus.fromString("accepted")
        assertEquals(CustomerStatus.ACCEPTED, status)
    }

    @Test
    fun testFromStringCaseInsensitiveProcessing() {
        val status = CustomerStatus.fromString("Processing")
        assertEquals(CustomerStatus.PROCESSING, status)
    }

    @Test
    fun testFromStringCaseInsensitiveNeedsInfo() {
        val status = CustomerStatus.fromString("needs_info")
        assertEquals(CustomerStatus.NEEDS_INFO, status)
    }

    @Test
    fun testFromStringCaseInsensitiveRejected() {
        val status = CustomerStatus.fromString("ReJeCtEd")
        assertEquals(CustomerStatus.REJECTED, status)
    }

    @Test
    fun testFromStringInvalidThrowsException() {
        val exception = assertFailsWith<IllegalArgumentException> {
            CustomerStatus.fromString("INVALID_STATUS")
        }
        assertEquals("Unknown customer status: INVALID_STATUS", exception.message)
    }

    @Test
    fun testFromStringEmptyThrowsException() {
        val exception = assertFailsWith<IllegalArgumentException> {
            CustomerStatus.fromString("")
        }
        assertEquals("Unknown customer status: ", exception.message)
    }

    @Test
    fun testFromStringPendingThrowsException() {
        val exception = assertFailsWith<IllegalArgumentException> {
            CustomerStatus.fromString("PENDING")
        }
        assertEquals("Unknown customer status: PENDING", exception.message)
    }
}
