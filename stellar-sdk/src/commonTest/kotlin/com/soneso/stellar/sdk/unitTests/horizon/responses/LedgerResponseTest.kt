package com.soneso.stellar.sdk.unitTests.horizon.responses

import com.soneso.stellar.sdk.horizon.responses.LedgerResponse
import com.soneso.stellar.sdk.horizon.responses.Link
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LedgerResponseTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val ledgerJson = """
    {
        "id": "abc123",
        "paging_token": "7505182",
        "hash": "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2",
        "prev_hash": "f6e5d4c3b2a1f6e5d4c3b2a1f6e5d4c3b2a1f6e5d4c3b2a1f6e5d4c3b2a1f6e5",
        "sequence": 7505182,
        "successful_transaction_count": 10,
        "failed_transaction_count": 2,
        "operation_count": 25,
        "tx_set_operation_count": 30,
        "closed_at": "2021-01-01T00:00:00Z",
        "total_coins": "105443902087.3472865",
        "fee_pool": "1807982.8413787",
        "base_fee_in_stroops": "100",
        "base_reserve_in_stroops": "5000000",
        "max_tx_set_size": 100,
        "protocol_version": 18,
        "header_xdr": "AAAA",
        "_links": {
            "self": {"href": "https://horizon.stellar.org/ledgers/7505182"},
            "transactions": {"href": "https://horizon.stellar.org/ledgers/7505182/transactions{?cursor,limit,order}", "templated": true},
            "operations": {"href": "https://horizon.stellar.org/ledgers/7505182/operations{?cursor,limit,order}", "templated": true},
            "payments": {"href": "https://horizon.stellar.org/ledgers/7505182/payments{?cursor,limit,order}", "templated": true},
            "effects": {"href": "https://horizon.stellar.org/ledgers/7505182/effects{?cursor,limit,order}", "templated": true}
        }
    }
    """.trimIndent()

    @Test
    fun testDeserialization() {
        val ledger = json.decodeFromString<LedgerResponse>(ledgerJson)
        assertEquals("abc123", ledger.id)
        assertEquals("7505182", ledger.pagingToken)
        assertEquals("a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2", ledger.hash)
        assertEquals("f6e5d4c3b2a1f6e5d4c3b2a1f6e5d4c3b2a1f6e5d4c3b2a1f6e5d4c3b2a1f6e5", ledger.prevHash)
        assertEquals(7505182L, ledger.sequence)
        assertEquals(10, ledger.successfulTransactionCount)
        assertEquals(2, ledger.failedTransactionCount)
        assertEquals(25, ledger.operationCount)
        assertEquals(30, ledger.txSetOperationCount)
        assertEquals("2021-01-01T00:00:00Z", ledger.closedAt)
        assertEquals("105443902087.3472865", ledger.totalCoins)
        assertEquals("1807982.8413787", ledger.feePool)
        assertEquals("100", ledger.baseFeeInStroops)
        assertEquals("5000000", ledger.baseReserveInStroops)
        assertEquals(100, ledger.maxTxSetSize)
        assertEquals(18, ledger.protocolVersion)
        assertEquals("AAAA", ledger.headerXdr)
    }

    @Test
    fun testLinks() {
        val ledger = json.decodeFromString<LedgerResponse>(ledgerJson)
        assertTrue(ledger.links.self.href.contains("ledgers/7505182"))
        assertTrue(ledger.links.transactions.templated == true)
        assertTrue(ledger.links.operations.templated == true)
        assertTrue(ledger.links.payments.templated == true)
        assertTrue(ledger.links.effects.templated == true)
    }

    @Test
    fun testMinimalDeserialization() {
        val minimalJson = """
        {
            "id": "min123",
            "paging_token": "1",
            "hash": "aaa",
            "sequence": 1,
            "closed_at": "2021-01-01T00:00:00Z",
            "total_coins": "100.0",
            "fee_pool": "0.0",
            "base_fee_in_stroops": "100",
            "base_reserve_in_stroops": "5000000",
            "_links": {
                "self": {"href": "https://example.com/self"},
                "transactions": {"href": "https://example.com/tx"},
                "operations": {"href": "https://example.com/ops"},
                "payments": {"href": "https://example.com/pay"},
                "effects": {"href": "https://example.com/fx"}
            }
        }
        """.trimIndent()
        val ledger = json.decodeFromString<LedgerResponse>(minimalJson)
        assertEquals("min123", ledger.id)
        assertNull(ledger.prevHash)
        assertNull(ledger.successfulTransactionCount)
        assertNull(ledger.failedTransactionCount)
        assertNull(ledger.operationCount)
        assertNull(ledger.txSetOperationCount)
        assertNull(ledger.maxTxSetSize)
        assertNull(ledger.protocolVersion)
        assertNull(ledger.headerXdr)
    }

    @Test
    fun testLedgerResponseLinksInnerClassFullCoverage() {
        val ledgerResponseJson = """
        {
            "id": "e73064774d9ad4de4d0e4e28b5b16f8dcc5169b3b24bc5b5d2c5b52e2b65a144",
            "paging_token": "186640427761664",
            "hash": "e73064774d9ad4de4d0e4e28b5b16f8dcc5169b3b24bc5b5d2c5b52e2b65a144",
            "prev_hash": "6651bd81e8e4e3f9f8c8a4b1c9b1f5b7e5b8c1d4c5b1f5b7e5b8c1d4c5b1f5b7",
            "sequence": 43466843,
            "successful_transaction_count": 127,
            "failed_transaction_count": 3,
            "operation_count": 318,
            "tx_set_operation_count": 321,
            "closed_at": "2023-05-15T14:30:25Z",
            "total_coins": "105443902087.4975000",
            "fee_pool": "1895194.6850400",
            "base_fee_in_stroops": "100",
            "base_reserve_in_stroops": "5000000", 
            "max_tx_set_size": 500,
            "protocol_version": 19,
            "header_xdr": "AAAAFGZTvYHo5OP5+MikscmwAaABEh6RbYCQkCDDRAAAAAAABhX+AAAAAGRk8sUAAAAZAAAAAQAAAAsABhX4AAABkAAAAAGnZvAAAFdFAAAFYnE8",
            "_links": {
                "self": {"href": "https://horizon.stellar.org/ledgers/43466843"},
                "transactions": {"href": "https://horizon.stellar.org/ledgers/43466843/transactions"},
                "operations": {"href": "https://horizon.stellar.org/ledgers/43466843/operations"},
                "payments": {"href": "https://horizon.stellar.org/ledgers/43466843/payments"},
                "effects": {"href": "https://horizon.stellar.org/ledgers/43466843/effects"}
            }
        }
        """.trimIndent()

        val ledger = json.decodeFromString<LedgerResponse>(ledgerResponseJson)

        assertEquals("e73064774d9ad4de4d0e4e28b5b16f8dcc5169b3b24bc5b5d2c5b52e2b65a144", ledger.id)
        assertEquals("186640427761664", ledger.pagingToken)
        assertEquals("e73064774d9ad4de4d0e4e28b5b16f8dcc5169b3b24bc5b5d2c5b52e2b65a144", ledger.hash)
        assertEquals("6651bd81e8e4e3f9f8c8a4b1c9b1f5b7e5b8c1d4c5b1f5b7e5b8c1d4c5b1f5b7", ledger.prevHash)
        assertEquals(43466843L, ledger.sequence)
        assertEquals(127, ledger.successfulTransactionCount)
        assertEquals(3, ledger.failedTransactionCount)
        assertEquals(318, ledger.operationCount)
        assertEquals(321, ledger.txSetOperationCount)
        assertEquals("2023-05-15T14:30:25Z", ledger.closedAt)
        assertEquals("105443902087.4975000", ledger.totalCoins)
        assertEquals("1895194.6850400", ledger.feePool)
        assertEquals("100", ledger.baseFeeInStroops)
        assertEquals("5000000", ledger.baseReserveInStroops)
        assertEquals(500, ledger.maxTxSetSize)
        assertEquals(19, ledger.protocolVersion)
        assertEquals("AAAAFGZTvYHo5OP5+MikscmwAaABEh6RbYCQkCDDRAAAAAAABhX+AAAAAGRk8sUAAAAZAAAAAQAAAAsABhX4AAABkAAAAAGnZvAAAFdFAAAFYnE8", ledger.headerXdr)

        val links = ledger.links
        assertNotNull(links)
        assertEquals("https://horizon.stellar.org/ledgers/43466843", links.self.href)
        assertEquals("https://horizon.stellar.org/ledgers/43466843/transactions", links.transactions.href)
        assertEquals("https://horizon.stellar.org/ledgers/43466843/operations", links.operations.href)
        assertEquals("https://horizon.stellar.org/ledgers/43466843/payments", links.payments.href)
        assertEquals("https://horizon.stellar.org/ledgers/43466843/effects", links.effects.href)
    }

    @Test
    fun testLedgerResponseWithNullableFields() {
        val minimalLedgerJson = """
        {
            "id": "minimal123",
            "paging_token": "100",
            "hash": "minimalhash123",
            "sequence": 1,
            "closed_at": "2022-01-01T00:00:00Z",
            "total_coins": "100000000000.0000000",
            "fee_pool": "0.0000000",
            "base_fee_in_stroops": "100",
            "base_reserve_in_stroops": "5000000",
            "_links": {
                "self": {"href": "https://minimal.com/ledgers/1"},
                "transactions": {"href": "https://minimal.com/ledgers/1/tx"},
                "operations": {"href": "https://minimal.com/ledgers/1/ops"},
                "payments": {"href": "https://minimal.com/ledgers/1/payments"},
                "effects": {"href": "https://minimal.com/ledgers/1/effects"}
            }
        }
        """.trimIndent()

        val ledger = json.decodeFromString<LedgerResponse>(minimalLedgerJson)

        assertEquals("minimal123", ledger.id)
        assertEquals("100", ledger.pagingToken)
        assertEquals("minimalhash123", ledger.hash)
        assertEquals(1L, ledger.sequence)
        assertEquals("2022-01-01T00:00:00Z", ledger.closedAt)
        assertEquals("100000000000.0000000", ledger.totalCoins)
        assertEquals("0.0000000", ledger.feePool)
        assertEquals("100", ledger.baseFeeInStroops)
        assertEquals("5000000", ledger.baseReserveInStroops)

        assertNull(ledger.prevHash)
        assertNull(ledger.successfulTransactionCount)
        assertNull(ledger.failedTransactionCount)
        assertNull(ledger.operationCount)
        assertNull(ledger.txSetOperationCount)
        assertNull(ledger.maxTxSetSize)
        assertNull(ledger.protocolVersion)
        assertNull(ledger.headerXdr)

        val links = ledger.links
        assertEquals("https://minimal.com/ledgers/1", links.self.href)
        assertEquals("https://minimal.com/ledgers/1/tx", links.transactions.href)
        assertEquals("https://minimal.com/ledgers/1/ops", links.operations.href)
        assertEquals("https://minimal.com/ledgers/1/payments", links.payments.href)
        assertEquals("https://minimal.com/ledgers/1/effects", links.effects.href)
    }

    @Test
    fun testDirectLinksConstruction() {
        val links = LedgerResponse.Links(
            self = Link("https://self-ledger.com"),
            transactions = Link("https://transactions.com"),
            operations = Link("https://operations.com"),
            payments = Link("https://payments.com"),
            effects = Link("https://effects.com")
        )

        assertEquals("https://self-ledger.com", links.self.href)
        assertEquals("https://transactions.com", links.transactions.href)
        assertEquals("https://operations.com", links.operations.href)
        assertEquals("https://payments.com", links.payments.href)
        assertEquals("https://effects.com", links.effects.href)
    }

    @Test
    fun testGenesisLedgerResponse() {
        val genesisLedgerJson = """
        {
            "id": "genesis000000000000000000000000000000000000000000000000000000000000",
            "paging_token": "4294967296",
            "hash": "63d98f536ee68d1b27b5b89f23af5311b7569a24faf1403ad0b52b633b07be99",
            "sequence": 1,
            "successful_transaction_count": 0,
            "failed_transaction_count": 0,
            "operation_count": 0,
            "tx_set_operation_count": 0,
            "closed_at": "1970-01-01T00:00:00Z",
            "total_coins": "100000000000.0000000",
            "fee_pool": "0.0000000",
            "base_fee_in_stroops": "100",
            "base_reserve_in_stroops": "5000000",
            "max_tx_set_size": 100,
            "protocol_version": 1,
            "header_xdr": "AAAAAAAAAAA=",
            "_links": {
                "self": {"href": "https://horizon.stellar.org/ledgers/1"},
                "transactions": {"href": "https://horizon.stellar.org/ledgers/1/transactions"},
                "operations": {"href": "https://horizon.stellar.org/ledgers/1/operations"},
                "payments": {"href": "https://horizon.stellar.org/ledgers/1/payments"},
                "effects": {"href": "https://horizon.stellar.org/ledgers/1/effects"}
            }
        }
        """.trimIndent()

        val ledger = json.decodeFromString<LedgerResponse>(genesisLedgerJson)

        assertEquals("genesis000000000000000000000000000000000000000000000000000000000000", ledger.id)
        assertEquals(1L, ledger.sequence)
        assertEquals(0, ledger.successfulTransactionCount)
        assertEquals(0, ledger.failedTransactionCount)
        assertEquals(0, ledger.operationCount)
        assertEquals(0, ledger.txSetOperationCount)
        assertEquals("1970-01-01T00:00:00Z", ledger.closedAt)
        assertEquals("100000000000.0000000", ledger.totalCoins)
        assertEquals("0.0000000", ledger.feePool)
        assertEquals(1, ledger.protocolVersion)
        assertEquals("AAAAAAAAAAA=", ledger.headerXdr)
        assertNull(ledger.prevHash)
    }

    @Test
    fun testLedgerResponseEquality() {
        val equalityJson = """
        {
            "id": "test123",
            "paging_token": "100",
            "hash": "testhash",
            "sequence": 100,
            "closed_at": "2023-01-01T00:00:00Z",
            "total_coins": "1000.0000000",
            "fee_pool": "10.0000000",
            "base_fee_in_stroops": "100",
            "base_reserve_in_stroops": "5000000",
            "_links": {
                "self": {"href": "self"},
                "transactions": {"href": "tx"},
                "operations": {"href": "ops"},
                "payments": {"href": "payments"},
                "effects": {"href": "effects"}
            }
        }
        """.trimIndent()

        val ledger1 = json.decodeFromString<LedgerResponse>(equalityJson)
        val ledger2 = json.decodeFromString<LedgerResponse>(equalityJson)

        assertEquals(ledger1.id, ledger2.id)
        assertEquals(ledger1.pagingToken, ledger2.pagingToken)
        assertEquals(ledger1.hash, ledger2.hash)
        assertEquals(ledger1.sequence, ledger2.sequence)
        assertEquals(ledger1.closedAt, ledger2.closedAt)
        assertEquals(ledger1.totalCoins, ledger2.totalCoins)
        assertEquals(ledger1.feePool, ledger2.feePool)
        assertEquals(ledger1.baseFeeInStroops, ledger2.baseFeeInStroops)
        assertEquals(ledger1.baseReserveInStroops, ledger2.baseReserveInStroops)

        assertEquals(ledger1.links.self.href, ledger2.links.self.href)
        assertEquals(ledger1.links.transactions.href, ledger2.links.transactions.href)
        assertEquals(ledger1.links.operations.href, ledger2.links.operations.href)
        assertEquals(ledger1.links.payments.href, ledger2.links.payments.href)
        assertEquals(ledger1.links.effects.href, ledger2.links.effects.href)
    }

    @Test
    fun testHighSequenceNumberLedger() {
        val highSequenceLedgerJson = """
        {
            "id": "highseq123456789012345678901234567890123456789012345678901234567890",
            "paging_token": "999999999999999999",
            "hash": "highseqhash123456789012345678901234567890123456789012345678901234567890",
            "prev_hash": "prevhash123456789012345678901234567890123456789012345678901234567890",
            "sequence": 999999999,
            "successful_transaction_count": 2147483647,
            "failed_transaction_count": 1000000,
            "operation_count": 2147483647,
            "tx_set_operation_count": 2147483647,
            "closed_at": "2099-12-31T23:59:59Z",
            "total_coins": "999999999999.9999999",
            "fee_pool": "999999999.9999999",
            "base_fee_in_stroops": "1000",
            "base_reserve_in_stroops": "10000000",
            "max_tx_set_size": 5000,
            "protocol_version": 100,
            "header_xdr": "VGhpcyBpcyBhIHZlcnkgbG9uZyBoZWFkZXIgWERSIGZvciBhIGhpZ2ggc2VxdWVuY2UgbnVtYmVyIGxlZGdlciBmb3IgdGVzdGluZw==",
            "_links": {
                "self": {"href": "https://future-horizon.stellar.org/ledgers/999999999"},
                "transactions": {"href": "https://future-horizon.stellar.org/ledgers/999999999/transactions"},
                "operations": {"href": "https://future-horizon.stellar.org/ledgers/999999999/operations"},
                "payments": {"href": "https://future-horizon.stellar.org/ledgers/999999999/payments"},
                "effects": {"href": "https://future-horizon.stellar.org/ledgers/999999999/effects"}
            }
        }
        """.trimIndent()

        val ledger = json.decodeFromString<LedgerResponse>(highSequenceLedgerJson)

        assertEquals(999999999L, ledger.sequence)
        assertEquals(2147483647, ledger.successfulTransactionCount)
        assertEquals(1000000, ledger.failedTransactionCount)
        assertEquals(2147483647, ledger.operationCount)
        assertEquals(2147483647, ledger.txSetOperationCount)
        assertEquals("2099-12-31T23:59:59Z", ledger.closedAt)
        assertEquals("999999999999.9999999", ledger.totalCoins)
        assertEquals("999999999.9999999", ledger.feePool)
        assertEquals("1000", ledger.baseFeeInStroops)
        assertEquals("10000000", ledger.baseReserveInStroops)
        assertEquals(5000, ledger.maxTxSetSize)
        assertEquals(100, ledger.protocolVersion)
        assertTrue(ledger.headerXdr?.length!! > 50)

        val links = ledger.links
        assertTrue(links.self.href.contains("future-horizon"))
        assertTrue(links.transactions.href.contains("999999999"))
    }

    @Test
    fun testZeroValueLedger() {
        val zeroLedgerJson = """
        {
            "id": "zero00000000000000000000000000000000000000000000000000000000000000",
            "paging_token": "0",
            "hash": "zerohash000000000000000000000000000000000000000000000000000000000000",
            "sequence": 0,
            "successful_transaction_count": 0,
            "failed_transaction_count": 0,
            "operation_count": 0,
            "tx_set_operation_count": 0,
            "closed_at": "2000-01-01T00:00:00Z",
            "total_coins": "0.0000000",
            "fee_pool": "0.0000000",
            "base_fee_in_stroops": "0",
            "base_reserve_in_stroops": "0",
            "max_tx_set_size": 0,
            "protocol_version": 0,
            "header_xdr": "",
            "_links": {
                "self": {"href": "https://test.com/ledgers/0"},
                "transactions": {"href": "https://test.com/ledgers/0/transactions"},
                "operations": {"href": "https://test.com/ledgers/0/operations"},
                "payments": {"href": "https://test.com/ledgers/0/payments"},
                "effects": {"href": "https://test.com/ledgers/0/effects"}
            }
        }
        """.trimIndent()

        val ledger = json.decodeFromString<LedgerResponse>(zeroLedgerJson)

        assertEquals(0L, ledger.sequence)
        assertEquals(0, ledger.successfulTransactionCount)
        assertEquals(0, ledger.failedTransactionCount)
        assertEquals(0, ledger.operationCount)
        assertEquals(0, ledger.txSetOperationCount)
        assertEquals("0.0000000", ledger.totalCoins)
        assertEquals("0.0000000", ledger.feePool)
        assertEquals("0", ledger.baseFeeInStroops)
        assertEquals("0", ledger.baseReserveInStroops)
        assertEquals(0, ledger.maxTxSetSize)
        assertEquals(0, ledger.protocolVersion)
        assertEquals("", ledger.headerXdr)
    }
}
