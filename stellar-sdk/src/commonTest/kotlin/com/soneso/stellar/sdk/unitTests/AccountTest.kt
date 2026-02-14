package com.soneso.stellar.sdk.unitTests

import com.soneso.stellar.sdk.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class AccountTest {

    private val accountId = "GBRPYHIL2CI3FNQ4BXLFMNDLFJUNPU2HY3ZMFSHONUCEOASW7QC7OX2H"
    private val sequenceNumber = 2908908335136768L

    @Test
    fun testCreateAccountFromAccountId() {
        val account = Account(accountId, sequenceNumber)

        assertEquals(accountId, account.accountId)
        assertEquals(sequenceNumber, account.sequenceNumber)
        assertEquals(accountId, account.keypair.getAccountId())
        assertFalse(account.keypair.canSign(), "Account created from ID should not be able to sign")
    }

    @Test
    fun testCreateAccountFromKeypair() = runTest {
        val seed = "SCZANGBA5YHTNYVVV4C3U252E2B6P6F5T3U6MM63WBSBZATAQI3EBTQ4"
        val keypair = KeyPair.fromSecretSeed(seed)
        val account = Account(keypair, sequenceNumber)

        assertEquals(keypair.getAccountId(), account.accountId)
        assertEquals(sequenceNumber, account.sequenceNumber)
        assertEquals(keypair, account.keypair)
        assertTrue(account.keypair.canSign(), "Account created from keypair should be able to sign")
    }

    @Test
    fun testInvalidAccountId() {
        val invalidAccountId = "INVALID"
        assertFailsWith<IllegalArgumentException> {
            Account(invalidAccountId, sequenceNumber)
        }
    }

    @Test
    fun testIncrementSequenceNumber() {
        val account = Account(accountId, 100L)

        assertEquals(100L, account.sequenceNumber)
        assertEquals(101L, account.getIncrementedSequenceNumber())
        assertEquals(100L, account.sequenceNumber, "getIncrementedSequenceNumber should not modify sequence")

        account.incrementSequenceNumber()
        assertEquals(101L, account.sequenceNumber)

        account.incrementSequenceNumber()
        assertEquals(102L, account.sequenceNumber)
    }

    @Test
    fun testSetSequenceNumber() {
        val account = Account(accountId, 100L)

        assertEquals(100L, account.sequenceNumber)

        account.setSequenceNumber(200L)
        assertEquals(200L, account.sequenceNumber)

        account.setSequenceNumber(0L)
        assertEquals(0L, account.sequenceNumber)
    }

    @Test
    fun testSequenceNumberOperations() {
        val account = Account(accountId, 0L)

        // Test incrementedSequenceNumber
        assertEquals(1L, account.getIncrementedSequenceNumber())
        assertEquals(0L, account.sequenceNumber)

        // Multiple calls should return same value
        assertEquals(1L, account.getIncrementedSequenceNumber())
        assertEquals(1L, account.getIncrementedSequenceNumber())
        assertEquals(0L, account.sequenceNumber)

        // Increment and verify
        account.incrementSequenceNumber()
        assertEquals(1L, account.sequenceNumber)
        assertEquals(2L, account.getIncrementedSequenceNumber())
    }

    @Test
    fun testEquality() {
        val account1 = Account(accountId, sequenceNumber)
        val account2 = Account(accountId, sequenceNumber)
        val account3 = Account(accountId, sequenceNumber + 1)

        // Different account ID
        val differentAccountId = "GCEZWKCA5VLDNRLN3RPRJMRZOX3Z6G5CHCGSNFHEYVXM3XOJMDS674JZ"
        val account4 = Account(differentAccountId, sequenceNumber)

        assertEquals(account1, account2)
        assertNotEquals(account1, account3, "Different sequence numbers should not be equal")
        assertNotEquals(account1, account4, "Different account IDs should not be equal")

        assertEquals(account1.hashCode(), account2.hashCode())
    }

    @Test
    fun testToString() {
        val account = Account(accountId, sequenceNumber)
        val str = account.toString()

        assertTrue(str.contains(accountId))
        assertTrue(str.contains(sequenceNumber.toString()))
    }

    @Test
    fun testTransactionBuilderAccountInterface() {
        val account: TransactionBuilderAccount = Account(accountId, sequenceNumber)

        assertEquals(accountId, account.accountId)
        assertEquals(sequenceNumber, account.sequenceNumber)
        assertEquals(sequenceNumber + 1, account.getIncrementedSequenceNumber())

        account.incrementSequenceNumber()
        assertEquals(sequenceNumber + 1, account.sequenceNumber)

        account.setSequenceNumber(1000L)
        assertEquals(1000L, account.sequenceNumber)
    }

    @Test
    fun testAccountWithKeypairInterface() = runTest {
        val seed = "SCZANGBA5YHTNYVVV4C3U252E2B6P6F5T3U6MM63WBSBZATAQI3EBTQ4"
        val keypair = KeyPair.fromSecretSeed(seed)
        val account = Account(keypair, 0L)

        // Should be able to sign
        val data = "test data".encodeToByteArray()
        val signature = account.keypair.sign(data)
        assertTrue(account.keypair.verify(data, signature))
    }

    @Test
    fun testLargeSequenceNumbers() {
        val maxSequence = Long.MAX_VALUE - 1
        val account = Account(accountId, maxSequence)

        assertEquals(maxSequence, account.sequenceNumber)
        assertEquals(Long.MAX_VALUE, account.getIncrementedSequenceNumber())

        account.incrementSequenceNumber()
        assertEquals(Long.MAX_VALUE, account.sequenceNumber)
    }

    @Test
    fun testNegativeSequenceNumber() {
        // Negative sequence numbers are technically allowed (though unusual)
        val account = Account(accountId, -1L)
        assertEquals(-1L, account.sequenceNumber)
        assertEquals(0L, account.getIncrementedSequenceNumber())
    }

    @Test
    fun testZeroSequenceNumber() {
        // New accounts start with sequence 0
        val account = Account(accountId, 0L)
        assertEquals(0L, account.sequenceNumber)
        assertEquals(1L, account.getIncrementedSequenceNumber())
    }
}
