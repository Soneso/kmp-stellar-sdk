// Copyright 2026 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.unitTests.sep.sep06

import com.soneso.stellar.sdk.sep.sep06.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/**
 * Tests for Sep06TransactionKind enum.
 *
 * Verifies all 4 kind values, helper methods (isDeposit, isWithdrawal, isExchange),
 * and fromValue parsing functionality.
 */
class Sep06TransactionKindTest {

    // ========== All Kind Values ==========

    @Test
    fun testAllKindValuesExist() = runTest {
        // Verify all 4 SEP-6 kind values are defined
        val expectedKinds = listOf(
            "deposit",
            "withdrawal",
            "deposit-exchange",
            "withdrawal-exchange"
        )

        expectedKinds.forEach { kindValue ->
            val kind = Sep06TransactionKind.fromValue(kindValue)
            assertNotNull(kind, "Kind '$kindValue' should be defined")
            assertEquals(kindValue, kind.value, "Kind value should match")
        }
    }

    @Test
    fun testKindEnumValues() = runTest {
        assertEquals("deposit", Sep06TransactionKind.DEPOSIT.value)
        assertEquals("withdrawal", Sep06TransactionKind.WITHDRAWAL.value)
        assertEquals("deposit-exchange", Sep06TransactionKind.DEPOSIT_EXCHANGE.value)
        assertEquals("withdrawal-exchange", Sep06TransactionKind.WITHDRAWAL_EXCHANGE.value)
    }

    @Test
    fun testKindEnumCount() = runTest {
        // Verify exactly 4 kinds exist
        assertEquals(4, Sep06TransactionKind.entries.size)
    }

    // ========== fromValue Tests ==========

    @Test
    fun testFromValueForAllKinds() = runTest {
        assertEquals(Sep06TransactionKind.DEPOSIT, Sep06TransactionKind.fromValue("deposit"))
        assertEquals(Sep06TransactionKind.WITHDRAWAL, Sep06TransactionKind.fromValue("withdrawal"))
        assertEquals(Sep06TransactionKind.DEPOSIT_EXCHANGE, Sep06TransactionKind.fromValue("deposit-exchange"))
        assertEquals(Sep06TransactionKind.WITHDRAWAL_EXCHANGE, Sep06TransactionKind.fromValue("withdrawal-exchange"))
    }

    @Test
    fun testFromValueUnknown() = runTest {
        assertNull(Sep06TransactionKind.fromValue("unknown_kind"))
        assertNull(Sep06TransactionKind.fromValue(""))
        assertNull(Sep06TransactionKind.fromValue("DEPOSIT")) // Case-sensitive
        assertNull(Sep06TransactionKind.fromValue("Withdrawal")) // Case-sensitive
        assertNull(Sep06TransactionKind.fromValue("deposit_exchange")) // Underscore vs hyphen
        assertNull(Sep06TransactionKind.fromValue("withdraw")) // Wrong value
    }

    // ========== isDeposit Tests ==========

    @Test
    fun testIsDeposit() = runTest {
        assertTrue(Sep06TransactionKind.DEPOSIT.isDeposit())
        assertTrue(Sep06TransactionKind.DEPOSIT_EXCHANGE.isDeposit())
        assertFalse(Sep06TransactionKind.WITHDRAWAL.isDeposit())
        assertFalse(Sep06TransactionKind.WITHDRAWAL_EXCHANGE.isDeposit())
    }

    @Test
    fun testDepositKindsCount() = runTest {
        val depositKinds = Sep06TransactionKind.entries.filter { it.isDeposit() }
        assertEquals(2, depositKinds.size)
        assertTrue(depositKinds.contains(Sep06TransactionKind.DEPOSIT))
        assertTrue(depositKinds.contains(Sep06TransactionKind.DEPOSIT_EXCHANGE))
    }

    // ========== isWithdrawal Tests ==========

    @Test
    fun testIsWithdrawal() = runTest {
        assertTrue(Sep06TransactionKind.WITHDRAWAL.isWithdrawal())
        assertTrue(Sep06TransactionKind.WITHDRAWAL_EXCHANGE.isWithdrawal())
        assertFalse(Sep06TransactionKind.DEPOSIT.isWithdrawal())
        assertFalse(Sep06TransactionKind.DEPOSIT_EXCHANGE.isWithdrawal())
    }

    @Test
    fun testWithdrawalKindsCount() = runTest {
        val withdrawalKinds = Sep06TransactionKind.entries.filter { it.isWithdrawal() }
        assertEquals(2, withdrawalKinds.size)
        assertTrue(withdrawalKinds.contains(Sep06TransactionKind.WITHDRAWAL))
        assertTrue(withdrawalKinds.contains(Sep06TransactionKind.WITHDRAWAL_EXCHANGE))
    }

    // ========== isExchange Tests ==========

    @Test
    fun testIsExchange() = runTest {
        assertTrue(Sep06TransactionKind.DEPOSIT_EXCHANGE.isExchange())
        assertTrue(Sep06TransactionKind.WITHDRAWAL_EXCHANGE.isExchange())
        assertFalse(Sep06TransactionKind.DEPOSIT.isExchange())
        assertFalse(Sep06TransactionKind.WITHDRAWAL.isExchange())
    }

    @Test
    fun testExchangeKindsCount() = runTest {
        val exchangeKinds = Sep06TransactionKind.entries.filter { it.isExchange() }
        assertEquals(2, exchangeKinds.size)
        assertTrue(exchangeKinds.contains(Sep06TransactionKind.DEPOSIT_EXCHANGE))
        assertTrue(exchangeKinds.contains(Sep06TransactionKind.WITHDRAWAL_EXCHANGE))
    }

    // ========== Standard (Non-Exchange) Tests ==========

    @Test
    fun testStandardKinds() = runTest {
        val standardKinds = Sep06TransactionKind.entries.filter { !it.isExchange() }
        assertEquals(2, standardKinds.size)
        assertTrue(standardKinds.contains(Sep06TransactionKind.DEPOSIT))
        assertTrue(standardKinds.contains(Sep06TransactionKind.WITHDRAWAL))
    }

    // ========== Kind Categories Are Mutually Exclusive ==========

    @Test
    fun testDepositAndWithdrawalMutuallyExclusive() = runTest {
        for (kind in Sep06TransactionKind.entries) {
            // A kind is either deposit or withdrawal, never both
            assertTrue(
                kind.isDeposit() xor kind.isWithdrawal(),
                "${kind.value} should be either deposit or withdrawal, not both"
            )
        }
    }

    @Test
    fun testExchangeOrthogonalToDirection() = runTest {
        // Exchange is orthogonal to deposit/withdrawal
        // Each combination exists:
        // - deposit (standard)
        // - withdrawal (standard)
        // - deposit-exchange
        // - withdrawal-exchange

        val depositStandard = Sep06TransactionKind.entries.filter { it.isDeposit() && !it.isExchange() }
        val depositExchange = Sep06TransactionKind.entries.filter { it.isDeposit() && it.isExchange() }
        val withdrawalStandard = Sep06TransactionKind.entries.filter { it.isWithdrawal() && !it.isExchange() }
        val withdrawalExchange = Sep06TransactionKind.entries.filter { it.isWithdrawal() && it.isExchange() }

        assertEquals(1, depositStandard.size)
        assertEquals(1, depositExchange.size)
        assertEquals(1, withdrawalStandard.size)
        assertEquals(1, withdrawalExchange.size)

        assertEquals(Sep06TransactionKind.DEPOSIT, depositStandard.first())
        assertEquals(Sep06TransactionKind.DEPOSIT_EXCHANGE, depositExchange.first())
        assertEquals(Sep06TransactionKind.WITHDRAWAL, withdrawalStandard.first())
        assertEquals(Sep06TransactionKind.WITHDRAWAL_EXCHANGE, withdrawalExchange.first())
    }

    // ========== Transaction Helper Methods ==========

    @Test
    fun testTransactionKindHelper() = runTest {
        // Test Sep06Transaction.getKindEnum() helper
        val depositTx = Sep06Transaction(
            id = "tx-1",
            kind = "deposit",
            status = "completed"
        )
        assertEquals(Sep06TransactionKind.DEPOSIT, depositTx.getKindEnum())
        assertTrue(depositTx.getKindEnum()!!.isDeposit())
        assertFalse(depositTx.getKindEnum()!!.isExchange())

        val withdrawalExchangeTx = Sep06Transaction(
            id = "tx-2",
            kind = "withdrawal-exchange",
            status = "pending_anchor"
        )
        assertEquals(Sep06TransactionKind.WITHDRAWAL_EXCHANGE, withdrawalExchangeTx.getKindEnum())
        assertTrue(withdrawalExchangeTx.getKindEnum()!!.isWithdrawal())
        assertTrue(withdrawalExchangeTx.getKindEnum()!!.isExchange())

        val unknownKindTx = Sep06Transaction(
            id = "tx-3",
            kind = "unknown",
            status = "completed"
        )
        assertNull(unknownKindTx.getKindEnum())
    }

    // ========== Kind Semantics ==========

    @Test
    fun testDepositSemantics() = runTest {
        // Deposit: User sends off-chain asset, receives on-chain asset
        val deposit = Sep06TransactionKind.DEPOSIT
        assertTrue(deposit.isDeposit())
        assertFalse(deposit.isWithdrawal())
        assertFalse(deposit.isExchange())

        // Deposit-exchange: User sends off-chain asset A, receives on-chain asset B (different)
        val depositExchange = Sep06TransactionKind.DEPOSIT_EXCHANGE
        assertTrue(depositExchange.isDeposit())
        assertFalse(depositExchange.isWithdrawal())
        assertTrue(depositExchange.isExchange())
    }

    @Test
    fun testWithdrawalSemantics() = runTest {
        // Withdrawal: User sends on-chain asset, receives off-chain asset
        val withdrawal = Sep06TransactionKind.WITHDRAWAL
        assertFalse(withdrawal.isDeposit())
        assertTrue(withdrawal.isWithdrawal())
        assertFalse(withdrawal.isExchange())

        // Withdrawal-exchange: User sends on-chain asset A, receives off-chain asset B (different)
        val withdrawalExchange = Sep06TransactionKind.WITHDRAWAL_EXCHANGE
        assertFalse(withdrawalExchange.isDeposit())
        assertTrue(withdrawalExchange.isWithdrawal())
        assertTrue(withdrawalExchange.isExchange())
    }

    @Test
    fun testExchangeRequiresSep38() = runTest {
        // Exchange transactions require SEP-38 quotes for rate determination
        val exchangeKinds = listOf(
            Sep06TransactionKind.DEPOSIT_EXCHANGE,
            Sep06TransactionKind.WITHDRAWAL_EXCHANGE
        )

        exchangeKinds.forEach { kind ->
            assertTrue(kind.isExchange(), "${kind.value} should be an exchange kind")
        }
    }

    // ========== Combined Scenarios ==========

    @Test
    fun testAllKindCombinations() = runTest {
        // Verify the 2x2 matrix of deposit/withdrawal x standard/exchange

        // Standard deposit
        val stdDeposit = Sep06TransactionKind.DEPOSIT
        assertTrue(stdDeposit.isDeposit() && !stdDeposit.isExchange())

        // Standard withdrawal
        val stdWithdrawal = Sep06TransactionKind.WITHDRAWAL
        assertTrue(stdWithdrawal.isWithdrawal() && !stdWithdrawal.isExchange())

        // Exchange deposit
        val exchDeposit = Sep06TransactionKind.DEPOSIT_EXCHANGE
        assertTrue(exchDeposit.isDeposit() && exchDeposit.isExchange())

        // Exchange withdrawal
        val exchWithdrawal = Sep06TransactionKind.WITHDRAWAL_EXCHANGE
        assertTrue(exchWithdrawal.isWithdrawal() && exchWithdrawal.isExchange())
    }

    @Test
    fun testKindWithTransactionParsing() = runTest {
        // Test that kinds are correctly parsed from transaction responses
        val transactionKinds = listOf(
            "deposit" to Sep06TransactionKind.DEPOSIT,
            "withdrawal" to Sep06TransactionKind.WITHDRAWAL,
            "deposit-exchange" to Sep06TransactionKind.DEPOSIT_EXCHANGE,
            "withdrawal-exchange" to Sep06TransactionKind.WITHDRAWAL_EXCHANGE
        )

        transactionKinds.forEach { (kindStr, expectedKind) ->
            val tx = Sep06Transaction(
                id = "test-tx",
                kind = kindStr,
                status = "completed"
            )
            assertEquals(expectedKind, tx.getKindEnum())
        }
    }
}
