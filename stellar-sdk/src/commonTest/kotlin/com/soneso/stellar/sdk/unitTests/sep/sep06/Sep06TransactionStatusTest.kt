// Copyright 2026 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.unitTests.sep.sep06

import com.soneso.stellar.sdk.sep.sep06.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/**
 * Tests for Sep06TransactionStatus enum.
 *
 * Verifies all 17 status values, helper methods (isTerminal, isError, isPending),
 * and fromValue parsing functionality.
 */
class Sep06TransactionStatusTest {

    // ========== All Status Values ==========

    @Test
    fun testAllStatusValuesExist() = runTest {
        // Verify all 17 SEP-6 status values are defined
        val expectedStatuses = listOf(
            "incomplete",
            "pending_user_transfer_start",
            "pending_user_transfer_complete",
            "pending_external",
            "pending_anchor",
            "pending_stellar",
            "pending_trust",
            "pending_user",
            "pending_customer_info_update",
            "pending_transaction_info_update",
            "completed",
            "refunded",
            "expired",
            "error",
            "no_market",
            "too_small",
            "too_large"
        )

        expectedStatuses.forEach { statusValue ->
            val status = Sep06TransactionStatus.fromValue(statusValue)
            assertNotNull(status, "Status '$statusValue' should be defined")
            assertEquals(statusValue, status.value, "Status value should match")
        }
    }

    @Test
    fun testStatusEnumValues() = runTest {
        assertEquals("incomplete", Sep06TransactionStatus.INCOMPLETE.value)
        assertEquals("pending_user_transfer_start", Sep06TransactionStatus.PENDING_USER_TRANSFER_START.value)
        assertEquals("pending_user_transfer_complete", Sep06TransactionStatus.PENDING_USER_TRANSFER_COMPLETE.value)
        assertEquals("pending_external", Sep06TransactionStatus.PENDING_EXTERNAL.value)
        assertEquals("pending_anchor", Sep06TransactionStatus.PENDING_ANCHOR.value)
        assertEquals("pending_stellar", Sep06TransactionStatus.PENDING_STELLAR.value)
        assertEquals("pending_trust", Sep06TransactionStatus.PENDING_TRUST.value)
        assertEquals("pending_user", Sep06TransactionStatus.PENDING_USER.value)
        assertEquals("pending_customer_info_update", Sep06TransactionStatus.PENDING_CUSTOMER_INFO_UPDATE.value)
        assertEquals("pending_transaction_info_update", Sep06TransactionStatus.PENDING_TRANSACTION_INFO_UPDATE.value)
        assertEquals("completed", Sep06TransactionStatus.COMPLETED.value)
        assertEquals("refunded", Sep06TransactionStatus.REFUNDED.value)
        assertEquals("expired", Sep06TransactionStatus.EXPIRED.value)
        assertEquals("error", Sep06TransactionStatus.ERROR.value)
        assertEquals("no_market", Sep06TransactionStatus.NO_MARKET.value)
        assertEquals("too_small", Sep06TransactionStatus.TOO_SMALL.value)
        assertEquals("too_large", Sep06TransactionStatus.TOO_LARGE.value)
    }

    // ========== fromValue Tests ==========

    @Test
    fun testFromValueForAllStatuses() = runTest {
        assertEquals(Sep06TransactionStatus.INCOMPLETE, Sep06TransactionStatus.fromValue("incomplete"))
        assertEquals(Sep06TransactionStatus.PENDING_USER_TRANSFER_START, Sep06TransactionStatus.fromValue("pending_user_transfer_start"))
        assertEquals(Sep06TransactionStatus.PENDING_USER_TRANSFER_COMPLETE, Sep06TransactionStatus.fromValue("pending_user_transfer_complete"))
        assertEquals(Sep06TransactionStatus.PENDING_EXTERNAL, Sep06TransactionStatus.fromValue("pending_external"))
        assertEquals(Sep06TransactionStatus.PENDING_ANCHOR, Sep06TransactionStatus.fromValue("pending_anchor"))
        assertEquals(Sep06TransactionStatus.PENDING_STELLAR, Sep06TransactionStatus.fromValue("pending_stellar"))
        assertEquals(Sep06TransactionStatus.PENDING_TRUST, Sep06TransactionStatus.fromValue("pending_trust"))
        assertEquals(Sep06TransactionStatus.PENDING_USER, Sep06TransactionStatus.fromValue("pending_user"))
        assertEquals(Sep06TransactionStatus.PENDING_CUSTOMER_INFO_UPDATE, Sep06TransactionStatus.fromValue("pending_customer_info_update"))
        assertEquals(Sep06TransactionStatus.PENDING_TRANSACTION_INFO_UPDATE, Sep06TransactionStatus.fromValue("pending_transaction_info_update"))
        assertEquals(Sep06TransactionStatus.COMPLETED, Sep06TransactionStatus.fromValue("completed"))
        assertEquals(Sep06TransactionStatus.REFUNDED, Sep06TransactionStatus.fromValue("refunded"))
        assertEquals(Sep06TransactionStatus.EXPIRED, Sep06TransactionStatus.fromValue("expired"))
        assertEquals(Sep06TransactionStatus.ERROR, Sep06TransactionStatus.fromValue("error"))
        assertEquals(Sep06TransactionStatus.NO_MARKET, Sep06TransactionStatus.fromValue("no_market"))
        assertEquals(Sep06TransactionStatus.TOO_SMALL, Sep06TransactionStatus.fromValue("too_small"))
        assertEquals(Sep06TransactionStatus.TOO_LARGE, Sep06TransactionStatus.fromValue("too_large"))
    }

    @Test
    fun testFromValueUnknown() = runTest {
        assertNull(Sep06TransactionStatus.fromValue("unknown_status"))
        assertNull(Sep06TransactionStatus.fromValue(""))
        assertNull(Sep06TransactionStatus.fromValue("COMPLETED")) // Case-sensitive
        assertNull(Sep06TransactionStatus.fromValue("Pending_Anchor")) // Case-sensitive
    }

    // ========== isTerminal Tests ==========

    @Test
    fun testTerminalStatuses() = runTest {
        // All terminal statuses
        assertTrue(Sep06TransactionStatus.COMPLETED.isTerminal())
        assertTrue(Sep06TransactionStatus.REFUNDED.isTerminal())
        assertTrue(Sep06TransactionStatus.EXPIRED.isTerminal())
        assertTrue(Sep06TransactionStatus.ERROR.isTerminal())
        assertTrue(Sep06TransactionStatus.NO_MARKET.isTerminal())
        assertTrue(Sep06TransactionStatus.TOO_SMALL.isTerminal())
        assertTrue(Sep06TransactionStatus.TOO_LARGE.isTerminal())
    }

    @Test
    fun testNonTerminalStatuses() = runTest {
        // All non-terminal statuses
        assertFalse(Sep06TransactionStatus.INCOMPLETE.isTerminal())
        assertFalse(Sep06TransactionStatus.PENDING_USER_TRANSFER_START.isTerminal())
        assertFalse(Sep06TransactionStatus.PENDING_USER_TRANSFER_COMPLETE.isTerminal())
        assertFalse(Sep06TransactionStatus.PENDING_EXTERNAL.isTerminal())
        assertFalse(Sep06TransactionStatus.PENDING_ANCHOR.isTerminal())
        assertFalse(Sep06TransactionStatus.PENDING_STELLAR.isTerminal())
        assertFalse(Sep06TransactionStatus.PENDING_TRUST.isTerminal())
        assertFalse(Sep06TransactionStatus.PENDING_USER.isTerminal())
        assertFalse(Sep06TransactionStatus.PENDING_CUSTOMER_INFO_UPDATE.isTerminal())
        assertFalse(Sep06TransactionStatus.PENDING_TRANSACTION_INFO_UPDATE.isTerminal())
    }

    @Test
    fun testIsTerminalStaticMethod() = runTest {
        // Test the static isTerminal method with string values
        assertTrue(Sep06TransactionStatus.isTerminal("completed"))
        assertTrue(Sep06TransactionStatus.isTerminal("refunded"))
        assertTrue(Sep06TransactionStatus.isTerminal("expired"))
        assertTrue(Sep06TransactionStatus.isTerminal("error"))
        assertTrue(Sep06TransactionStatus.isTerminal("no_market"))
        assertTrue(Sep06TransactionStatus.isTerminal("too_small"))
        assertTrue(Sep06TransactionStatus.isTerminal("too_large"))

        assertFalse(Sep06TransactionStatus.isTerminal("incomplete"))
        assertFalse(Sep06TransactionStatus.isTerminal("pending_user_transfer_start"))
        assertFalse(Sep06TransactionStatus.isTerminal("pending_anchor"))
        assertFalse(Sep06TransactionStatus.isTerminal("unknown_status"))
    }

    @Test
    fun testTerminalStatusesSet() = runTest {
        val terminalStatuses = Sep06TransactionStatus.terminalStatuses

        assertEquals(7, terminalStatuses.size)
        assertTrue(terminalStatuses.contains(Sep06TransactionStatus.COMPLETED))
        assertTrue(terminalStatuses.contains(Sep06TransactionStatus.REFUNDED))
        assertTrue(terminalStatuses.contains(Sep06TransactionStatus.EXPIRED))
        assertTrue(terminalStatuses.contains(Sep06TransactionStatus.ERROR))
        assertTrue(terminalStatuses.contains(Sep06TransactionStatus.NO_MARKET))
        assertTrue(terminalStatuses.contains(Sep06TransactionStatus.TOO_SMALL))
        assertTrue(terminalStatuses.contains(Sep06TransactionStatus.TOO_LARGE))
    }

    // ========== isError Tests ==========

    @Test
    fun testErrorStatuses() = runTest {
        // Error statuses (a subset of terminal statuses)
        assertTrue(Sep06TransactionStatus.ERROR.isError())
        assertTrue(Sep06TransactionStatus.NO_MARKET.isError())
        assertTrue(Sep06TransactionStatus.TOO_SMALL.isError())
        assertTrue(Sep06TransactionStatus.TOO_LARGE.isError())
    }

    @Test
    fun testNonErrorStatuses() = runTest {
        // Non-error statuses (including some terminal ones like completed/refunded)
        assertFalse(Sep06TransactionStatus.COMPLETED.isError())
        assertFalse(Sep06TransactionStatus.REFUNDED.isError())
        assertFalse(Sep06TransactionStatus.EXPIRED.isError())
        assertFalse(Sep06TransactionStatus.INCOMPLETE.isError())
        assertFalse(Sep06TransactionStatus.PENDING_USER_TRANSFER_START.isError())
        assertFalse(Sep06TransactionStatus.PENDING_USER_TRANSFER_COMPLETE.isError())
        assertFalse(Sep06TransactionStatus.PENDING_EXTERNAL.isError())
        assertFalse(Sep06TransactionStatus.PENDING_ANCHOR.isError())
        assertFalse(Sep06TransactionStatus.PENDING_STELLAR.isError())
        assertFalse(Sep06TransactionStatus.PENDING_TRUST.isError())
        assertFalse(Sep06TransactionStatus.PENDING_USER.isError())
        assertFalse(Sep06TransactionStatus.PENDING_CUSTOMER_INFO_UPDATE.isError())
        assertFalse(Sep06TransactionStatus.PENDING_TRANSACTION_INFO_UPDATE.isError())
    }

    @Test
    fun testErrorStatusesSet() = runTest {
        val errorStatuses = Sep06TransactionStatus.errorStatuses

        assertEquals(4, errorStatuses.size)
        assertTrue(errorStatuses.contains(Sep06TransactionStatus.ERROR))
        assertTrue(errorStatuses.contains(Sep06TransactionStatus.NO_MARKET))
        assertTrue(errorStatuses.contains(Sep06TransactionStatus.TOO_SMALL))
        assertTrue(errorStatuses.contains(Sep06TransactionStatus.TOO_LARGE))
    }

    // ========== isPending Tests ==========

    @Test
    fun testPendingStatuses() = runTest {
        assertTrue(Sep06TransactionStatus.PENDING_USER_TRANSFER_START.isPending())
        assertTrue(Sep06TransactionStatus.PENDING_USER_TRANSFER_COMPLETE.isPending())
        assertTrue(Sep06TransactionStatus.PENDING_EXTERNAL.isPending())
        assertTrue(Sep06TransactionStatus.PENDING_ANCHOR.isPending())
        assertTrue(Sep06TransactionStatus.PENDING_STELLAR.isPending())
        assertTrue(Sep06TransactionStatus.PENDING_TRUST.isPending())
        assertTrue(Sep06TransactionStatus.PENDING_USER.isPending())
        assertTrue(Sep06TransactionStatus.PENDING_CUSTOMER_INFO_UPDATE.isPending())
        assertTrue(Sep06TransactionStatus.PENDING_TRANSACTION_INFO_UPDATE.isPending())
    }

    @Test
    fun testNonPendingStatuses() = runTest {
        // Non-pending statuses
        assertFalse(Sep06TransactionStatus.INCOMPLETE.isPending())
        assertFalse(Sep06TransactionStatus.COMPLETED.isPending())
        assertFalse(Sep06TransactionStatus.REFUNDED.isPending())
        assertFalse(Sep06TransactionStatus.EXPIRED.isPending())
        assertFalse(Sep06TransactionStatus.ERROR.isPending())
        assertFalse(Sep06TransactionStatus.NO_MARKET.isPending())
        assertFalse(Sep06TransactionStatus.TOO_SMALL.isPending())
        assertFalse(Sep06TransactionStatus.TOO_LARGE.isPending())
    }

    @Test
    fun testPendingStatusesSet() = runTest {
        val pendingStatuses = Sep06TransactionStatus.pendingStatuses

        assertEquals(9, pendingStatuses.size)
        assertTrue(pendingStatuses.contains(Sep06TransactionStatus.PENDING_USER_TRANSFER_START))
        assertTrue(pendingStatuses.contains(Sep06TransactionStatus.PENDING_USER_TRANSFER_COMPLETE))
        assertTrue(pendingStatuses.contains(Sep06TransactionStatus.PENDING_EXTERNAL))
        assertTrue(pendingStatuses.contains(Sep06TransactionStatus.PENDING_ANCHOR))
        assertTrue(pendingStatuses.contains(Sep06TransactionStatus.PENDING_STELLAR))
        assertTrue(pendingStatuses.contains(Sep06TransactionStatus.PENDING_TRUST))
        assertTrue(pendingStatuses.contains(Sep06TransactionStatus.PENDING_USER))
        assertTrue(pendingStatuses.contains(Sep06TransactionStatus.PENDING_CUSTOMER_INFO_UPDATE))
        assertTrue(pendingStatuses.contains(Sep06TransactionStatus.PENDING_TRANSACTION_INFO_UPDATE))
    }

    // ========== Status Categories Mutual Exclusivity ==========

    @Test
    fun testStatusCategoriesAreMutuallyExclusive() = runTest {
        // Verify that no status is in multiple exclusive categories
        for (status in Sep06TransactionStatus.entries) {
            val isPending = status.isPending()
            val isTerminal = status.isTerminal()
            val isIncomplete = status == Sep06TransactionStatus.INCOMPLETE

            // A status should be in exactly one of: pending, terminal, or incomplete
            val categoryCount = listOf(isPending, isTerminal, isIncomplete).count { it }
            assertEquals(
                1,
                categoryCount,
                "Status ${status.value} should be in exactly one category (pending, terminal, incomplete)"
            )
        }
    }

    @Test
    fun testErrorStatusesAreSubsetOfTerminal() = runTest {
        // All error statuses should also be terminal
        for (status in Sep06TransactionStatus.errorStatuses) {
            assertTrue(
                status.isTerminal(),
                "Error status ${status.value} should also be terminal"
            )
        }
    }

    // ========== Transaction Helper Methods ==========

    @Test
    fun testTransactionStatusHelper() = runTest {
        // Test Sep06Transaction.getStatusEnum() helper
        val completedTx = Sep06Transaction(
            id = "tx-1",
            kind = "deposit",
            status = "completed"
        )
        assertEquals(Sep06TransactionStatus.COMPLETED, completedTx.getStatusEnum())
        assertTrue(completedTx.isTerminal())

        val pendingTx = Sep06Transaction(
            id = "tx-2",
            kind = "withdrawal",
            status = "pending_anchor"
        )
        assertEquals(Sep06TransactionStatus.PENDING_ANCHOR, pendingTx.getStatusEnum())
        assertFalse(pendingTx.isTerminal())

        val unknownStatusTx = Sep06Transaction(
            id = "tx-3",
            kind = "deposit",
            status = "unknown_status"
        )
        assertNull(unknownStatusTx.getStatusEnum())
        assertFalse(unknownStatusTx.isTerminal())
    }

    // ========== Status Descriptions ==========

    @Test
    fun testStatusSemanticsForDeposit() = runTest {
        // Typical deposit flow statuses
        val depositFlow = listOf(
            Sep06TransactionStatus.INCOMPLETE,                    // Initial state, missing info
            Sep06TransactionStatus.PENDING_USER_TRANSFER_START,   // User instructed to send funds
            Sep06TransactionStatus.PENDING_USER_TRANSFER_COMPLETE,// Funds received by anchor
            Sep06TransactionStatus.PENDING_ANCHOR,                // Anchor processing
            Sep06TransactionStatus.PENDING_STELLAR,               // Waiting for Stellar tx
            Sep06TransactionStatus.COMPLETED                      // Deposit done
        )

        // All except COMPLETED should be non-terminal (can progress)
        depositFlow.dropLast(1).forEach { status ->
            assertFalse(status.isTerminal(), "${status.value} should not be terminal")
        }
        assertTrue(Sep06TransactionStatus.COMPLETED.isTerminal())
    }

    @Test
    fun testStatusSemanticsForWithdrawal() = runTest {
        // Typical withdrawal flow statuses
        val withdrawalFlow = listOf(
            Sep06TransactionStatus.INCOMPLETE,
            Sep06TransactionStatus.PENDING_USER_TRANSFER_START,   // Anchor waiting for Stellar payment
            Sep06TransactionStatus.PENDING_STELLAR,               // Stellar tx submitted
            Sep06TransactionStatus.PENDING_ANCHOR,                // Anchor processing
            Sep06TransactionStatus.PENDING_EXTERNAL,              // External system processing
            Sep06TransactionStatus.COMPLETED
        )

        // All except COMPLETED should be non-terminal
        withdrawalFlow.dropLast(1).forEach { status ->
            assertFalse(status.isTerminal(), "${status.value} should not be terminal")
        }
    }

    @Test
    fun testKycRelatedStatuses() = runTest {
        // KYC-related pending statuses
        val kycStatuses = listOf(
            Sep06TransactionStatus.PENDING_CUSTOMER_INFO_UPDATE,
            Sep06TransactionStatus.PENDING_TRANSACTION_INFO_UPDATE
        )

        kycStatuses.forEach { status ->
            assertTrue(status.isPending(), "${status.value} should be pending")
            assertFalse(status.isTerminal(), "${status.value} should not be terminal")
            assertFalse(status.isError(), "${status.value} should not be error")
        }
    }
}
