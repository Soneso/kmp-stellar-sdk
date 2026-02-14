package com.soneso.stellar.sdk.unitTests.horizon.responses

import com.soneso.stellar.sdk.horizon.responses.Link
import com.soneso.stellar.sdk.horizon.responses.TransactionResponse
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TransactionResponseTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val transactionJson = """
    {
        "id": "abc123",
        "paging_token": "abc123",
        "successful": true,
        "hash": "deadbeef",
        "ledger": 7654321,
        "created_at": "2021-01-01T00:00:00Z",
        "source_account": "GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7",
        "account_muxed": "MAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCAAAAAAAAAAAAGDFE",
        "account_muxed_id": "12345",
        "source_account_sequence": 3298702387052545,
        "fee_account": "GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7",
        "fee_charged": 100,
        "max_fee": 200,
        "operation_count": 1,
        "envelope_xdr": "AAAA",
        "result_xdr": "BBBB",
        "result_meta_xdr": "CCCC",
        "fee_meta_xdr": "DDDD",
        "signatures": ["sig1", "sig2"],
        "preconditions": {
            "timebounds": {
                "min_time": "0",
                "max_time": "1609459200"
            },
            "ledgerbounds": {
                "min_ledger": 100,
                "max_ledger": 200
            },
            "min_account_sequence": 50,
            "min_account_sequence_age": 10,
            "min_account_sequence_ledger_gap": 5,
            "extra_signers": ["signer1"]
        },
        "memo_type": "text",
        "memo": "hello",
        "_links": {
            "self": {"href": "https://horizon.stellar.org/transactions/abc123"},
            "account": {"href": "https://horizon.stellar.org/accounts/GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7"},
            "ledger": {"href": "https://horizon.stellar.org/ledgers/7654321"},
            "operations": {"href": "https://horizon.stellar.org/transactions/abc123/operations"},
            "effects": {"href": "https://horizon.stellar.org/transactions/abc123/effects"},
            "precedes": {"href": "https://horizon.stellar.org/transactions?order=asc&cursor=abc123"},
            "succeeds": {"href": "https://horizon.stellar.org/transactions?order=desc&cursor=abc123"}
        }
    }
    """.trimIndent()

    @Test
    fun testDeserialization() {
        val tx = json.decodeFromString<TransactionResponse>(transactionJson)
        assertEquals("abc123", tx.id)
        assertEquals("abc123", tx.pagingToken)
        assertTrue(tx.successful)
        assertEquals("deadbeef", tx.hash)
        assertEquals(7654321L, tx.ledger)
        assertEquals("2021-01-01T00:00:00Z", tx.createdAt)
        assertEquals("GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7", tx.sourceAccount)
        assertEquals(3298702387052545L, tx.sourceAccountSequence)
        assertEquals("GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7", tx.feeAccount)
        assertEquals(100L, tx.feeCharged)
        assertEquals(200L, tx.maxFee)
        assertEquals(1, tx.operationCount)
    }

    @Test
    fun testMuxedAccount() {
        val tx = json.decodeFromString<TransactionResponse>(transactionJson)
        assertTrue(tx.accountMuxed?.contains("MAAZI") == true)
        assertEquals("12345", tx.accountMuxedId)
    }

    @Test
    fun testXdrFields() {
        val tx = json.decodeFromString<TransactionResponse>(transactionJson)
        assertEquals("AAAA", tx.envelopeXdr)
        assertEquals("BBBB", tx.resultXdr)
        assertEquals("CCCC", tx.resultMetaXdr)
        assertEquals("DDDD", tx.feeMetaXdr)
    }

    @Test
    fun testSignatures() {
        val tx = json.decodeFromString<TransactionResponse>(transactionJson)
        assertEquals(2, tx.signatures.size)
        assertEquals("sig1", tx.signatures[0])
        assertEquals("sig2", tx.signatures[1])
    }

    @Test
    fun testPreconditions() {
        val tx = json.decodeFromString<TransactionResponse>(transactionJson)
        val pre = tx.preconditions!!
        assertEquals("0", pre.timeBounds?.minTime)
        assertEquals("1609459200", pre.timeBounds?.maxTime)
        assertEquals(100L, pre.ledgerBounds?.minLedger)
        assertEquals(200L, pre.ledgerBounds?.maxLedger)
        assertEquals(50L, pre.minAccountSequence)
        assertEquals(10L, pre.minAccountSequenceAge)
        assertEquals(5L, pre.minAccountSequenceLedgerGap)
        assertEquals(1, pre.extraSigners?.size)
        assertEquals("signer1", pre.extraSigners?.get(0))
    }

    @Test
    fun testMemo() {
        val tx = json.decodeFromString<TransactionResponse>(transactionJson)
        assertEquals("text", tx.memoType)
        assertEquals("hello", tx.memoValue)
        assertNull(tx.memoBytes)
    }

    @Test
    fun testLinks() {
        val tx = json.decodeFromString<TransactionResponse>(transactionJson)
        assertTrue(tx.links.self.href.contains("transactions/abc123"))
        assertTrue(tx.links.account.href.contains("accounts/"))
        assertTrue(tx.links.ledger.href.contains("ledgers/"))
        assertTrue(tx.links.operations.href.contains("operations"))
        assertTrue(tx.links.effects.href.contains("effects"))
    }

    @Test
    fun testMinimalTransactionDeserialization() {
        val minimalJson = """
        {
            "id": "tx1",
            "paging_token": "tx1",
            "successful": false,
            "hash": "aaa",
            "ledger": 1,
            "created_at": "2021-01-01T00:00:00Z",
            "source_account": "GTEST",
            "source_account_sequence": 1,
            "fee_account": "GTEST",
            "fee_charged": 100,
            "max_fee": 100,
            "operation_count": 0,
            "signatures": [],
            "memo_type": "none",
            "_links": {
                "self": {"href": "https://example.com/self"},
                "account": {"href": "https://example.com/account"},
                "ledger": {"href": "https://example.com/ledger"},
                "operations": {"href": "https://example.com/ops"},
                "effects": {"href": "https://example.com/fx"},
                "precedes": {"href": "https://example.com/pre"},
                "succeeds": {"href": "https://example.com/suc"}
            }
        }
        """.trimIndent()
        val tx = json.decodeFromString<TransactionResponse>(minimalJson)
        assertEquals("tx1", tx.id)
        assertEquals(false, tx.successful)
        assertNull(tx.accountMuxed)
        assertNull(tx.accountMuxedId)
        assertNull(tx.feeAccountMuxed)
        assertNull(tx.feeAccountMuxedId)
        assertNull(tx.envelopeXdr)
        assertNull(tx.resultXdr)
        assertNull(tx.resultMetaXdr)
        assertNull(tx.feeMetaXdr)
        assertNull(tx.preconditions)
        assertNull(tx.feeBumpTransaction)
        assertNull(tx.innerTransaction)
        assertNull(tx.memoValue)
        assertNull(tx.memoBytes)
    }

    @Test
    fun testFeeBumpTransactionDeserialization() {
        val feeBumpJson = """
        {
            "id": "tx1",
            "paging_token": "tx1",
            "successful": true,
            "hash": "outer_hash",
            "ledger": 1,
            "created_at": "2021-01-01T00:00:00Z",
            "source_account": "GTEST",
            "source_account_sequence": 1,
            "fee_account": "GFEE",
            "fee_charged": 200,
            "max_fee": 300,
            "operation_count": 1,
            "signatures": ["outer_sig"],
            "fee_bump_transaction": {
                "hash": "bump_hash",
                "signatures": ["bump_sig1", "bump_sig2"]
            },
            "inner_transaction": {
                "hash": "inner_hash",
                "signatures": ["inner_sig"],
                "max_fee": 100
            },
            "memo_type": "none",
            "_links": {
                "self": {"href": "self"},
                "account": {"href": "account"},
                "ledger": {"href": "ledger"},
                "operations": {"href": "ops"},
                "effects": {"href": "fx"},
                "precedes": {"href": "pre"},
                "succeeds": {"href": "suc"}
            }
        }
        """.trimIndent()
        val tx = json.decodeFromString<TransactionResponse>(feeBumpJson)
        assertEquals("bump_hash", tx.feeBumpTransaction?.hash)
        assertEquals(2, tx.feeBumpTransaction?.signatures?.size)
        assertEquals("inner_hash", tx.innerTransaction?.hash)
        assertEquals(1, tx.innerTransaction?.signatures?.size)
        assertEquals(100L, tx.innerTransaction?.maxFee)
    }

    @Test
    fun testFeeAccountMuxedAndMemoBytes() {
        val txJson = """
        {
            "id": "abc123",
            "paging_token": "abc123",
            "successful": true,
            "hash": "deadbeef",
            "ledger": 7654321,
            "created_at": "2021-01-01T00:00:00Z",
            "source_account": "GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7",
            "source_account_sequence": 3298702387052545,
            "fee_account": "GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7",
            "fee_account_muxed": "MFEE4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCAAAAAAAAAAAAGDFE",
            "fee_account_muxed_id": "67890",
            "fee_charged": 100,
            "max_fee": 200,
            "operation_count": 1,
            "signatures": [],
            "memo_type": "text",
            "memo": "hello world",
            "memo_bytes": "aGVsbG8gd29ybGQ=",
            "_links": {
                "self": {"href": "https://horizon.stellar.org/transactions/abc123"},
                "account": {"href": "https://horizon.stellar.org/accounts/GAAZI"},
                "ledger": {"href": "https://horizon.stellar.org/ledgers/7654321"},
                "operations": {"href": "https://horizon.stellar.org/transactions/abc123/operations"},
                "effects": {"href": "https://horizon.stellar.org/transactions/abc123/effects"},
                "precedes": {"href": "https://horizon.stellar.org/transactions?order=asc&cursor=abc123"},
                "succeeds": {"href": "https://horizon.stellar.org/transactions?order=desc&cursor=abc123"}
            }
        }
        """.trimIndent()

        val tx = json.decodeFromString<TransactionResponse>(txJson)
        assertEquals("MFEE4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCAAAAAAAAAAAAGDFE", tx.feeAccountMuxed)
        assertEquals("67890", tx.feeAccountMuxedId)
        assertEquals("aGVsbG8gd29ybGQ=", tx.memoBytes)
        assertEquals("https://horizon.stellar.org/transactions?order=asc&cursor=abc123", tx.links.precedes.href)
        assertEquals("https://horizon.stellar.org/transactions?order=desc&cursor=abc123", tx.links.succeeds.href)
    }

    @Test
    fun testDirectInnerClassConstruction() {
        val links = TransactionResponse.Links(
            self = Link("https://self.com"),
            account = Link("https://account.com"),
            ledger = Link("https://ledger.com"),
            operations = Link("https://operations.com"),
            effects = Link("https://effects.com"),
            precedes = Link("https://precedes.com"),
            succeeds = Link("https://succeeds.com")
        )
        assertEquals("https://self.com", links.self.href)
        assertEquals("https://account.com", links.account.href)
        assertEquals("https://ledger.com", links.ledger.href)
        assertEquals("https://operations.com", links.operations.href)
        assertEquals("https://effects.com", links.effects.href)
        assertEquals("https://precedes.com", links.precedes.href)
        assertEquals("https://succeeds.com", links.succeeds.href)

        val timeBounds = TransactionResponse.Preconditions.TimeBounds(
            minTime = "1000",
            maxTime = "2000"
        )
        assertEquals("1000", timeBounds.minTime)
        assertEquals("2000", timeBounds.maxTime)

        val ledgerBounds = TransactionResponse.Preconditions.LedgerBounds(
            minLedger = 100L,
            maxLedger = 200L
        )
        assertEquals(100L, ledgerBounds.minLedger)
        assertEquals(200L, ledgerBounds.maxLedger)

        val preconditions = TransactionResponse.Preconditions(
            timeBounds = timeBounds,
            ledgerBounds = ledgerBounds,
            minAccountSequence = 500L,
            minAccountSequenceAge = 600L,
            minAccountSequenceLedgerGap = 10L,
            extraSigners = listOf("signer1", "signer2")
        )
        assertEquals(timeBounds, preconditions.timeBounds)
        assertEquals(ledgerBounds, preconditions.ledgerBounds)
        assertEquals(500L, preconditions.minAccountSequence)
        assertEquals(600L, preconditions.minAccountSequenceAge)
        assertEquals(10L, preconditions.minAccountSequenceLedgerGap)
        assertEquals(2, preconditions.extraSigners?.size)
        assertEquals("signer1", preconditions.extraSigners?.get(0))

        val feeBumpTx = TransactionResponse.FeeBumpTransaction(
            hash = "bumphashabc",
            signatures = listOf("bumpsig1", "bumpsig2")
        )
        assertEquals("bumphashabc", feeBumpTx.hash)
        assertEquals(2, feeBumpTx.signatures.size)
        assertEquals("bumpsig1", feeBumpTx.signatures[0])
        assertEquals("bumpsig2", feeBumpTx.signatures[1])

        val innerTx = TransactionResponse.InnerTransaction(
            hash = "innerhashabc",
            signatures = listOf("innersig1"),
            maxFee = 1000L
        )
        assertEquals("innerhashabc", innerTx.hash)
        assertEquals(1, innerTx.signatures.size)
        assertEquals("innersig1", innerTx.signatures[0])
        assertEquals(1000L, innerTx.maxFee)
    }

    @Test
    fun testPreconditionsWithNullFields() {
        val minimalPreconditionsJson = """
        {
            "id": "minimal123",
            "paging_token": "minimal123",
            "successful": false,
            "hash": "minimalhash",
            "ledger": 100,
            "created_at": "2021-01-01T00:00:00Z",
            "source_account": "GMINIMAL",
            "source_account_sequence": 1,
            "fee_account": "GMINIMAL",
            "fee_charged": 100,
            "max_fee": 100,
            "operation_count": 0,
            "signatures": [],
            "memo_type": "none",
            "preconditions": {},
            "_links": {
                "self": {"href": "self"},
                "account": {"href": "account"},
                "ledger": {"href": "ledger"},
                "operations": {"href": "ops"},
                "effects": {"href": "fx"},
                "precedes": {"href": "pre"},
                "succeeds": {"href": "suc"}
            }
        }
        """.trimIndent()

        val tx = json.decodeFromString<TransactionResponse>(minimalPreconditionsJson)
        val preconditions = tx.preconditions
        assertNotNull(preconditions)
        assertNull(preconditions.timeBounds)
        assertNull(preconditions.ledgerBounds)
        assertNull(preconditions.minAccountSequence)
        assertNull(preconditions.minAccountSequenceAge)
        assertNull(preconditions.minAccountSequenceLedgerGap)
        assertNull(preconditions.extraSigners)
    }

    @Test
    fun testTimeBoundsWithNullFields() {
        val timeBoundsNullJson = """
        {
            "id": "time123",
            "paging_token": "time123",
            "successful": true,
            "hash": "timehash",
            "ledger": 200,
            "created_at": "2021-01-01T00:00:00Z",
            "source_account": "GTIME",
            "source_account_sequence": 2,
            "fee_account": "GTIME",
            "fee_charged": 100,
            "max_fee": 100,
            "operation_count": 0,
            "signatures": [],
            "memo_type": "none",
            "preconditions": {
                "timebounds": {}
            },
            "_links": {
                "self": {"href": "self"},
                "account": {"href": "account"},
                "ledger": {"href": "ledger"},
                "operations": {"href": "ops"},
                "effects": {"href": "fx"},
                "precedes": {"href": "pre"},
                "succeeds": {"href": "suc"}
            }
        }
        """.trimIndent()

        val tx = json.decodeFromString<TransactionResponse>(timeBoundsNullJson)
        val timeBounds = tx.preconditions?.timeBounds
        assertNotNull(timeBounds)
        assertNull(timeBounds.minTime)
        assertNull(timeBounds.maxTime)
    }
}
