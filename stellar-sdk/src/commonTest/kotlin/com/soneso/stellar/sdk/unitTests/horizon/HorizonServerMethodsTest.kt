package com.soneso.stellar.sdk.unitTests.horizon

import com.soneso.stellar.sdk.horizon.HorizonServer
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.*

/**
 * Unit tests for HorizonServer HTTP methods using Ktor MockEngine.
 * 
 * Tests all suspend functions that make HTTP calls to verify:
 * - Correct URL construction 
 * - Proper JSON deserialization
 * - Response object creation
 * - Error handling
 */
class HorizonServerMethodsTest {

    private fun createMockClient(
        responseContent: String,
        statusCode: HttpStatusCode = HttpStatusCode.OK,
        contentType: String = "application/json",
        expectedPath: String = ""
    ): HttpClient {
        val mockEngine = MockEngine { request ->
            if (expectedPath.isNotEmpty() && !request.url.encodedPath.contains(expectedPath)) {
                respond(
                    content = """{"error": "Not found"}""",
                    status = HttpStatusCode.NotFound,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            } else {
                respond(
                    content = responseContent,
                    status = statusCode,
                    headers = headersOf(HttpHeaders.ContentType, contentType)
                )
            }
        }

        return HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
        }
    }

    // ========== Individual Resource Methods ==========

    @Test
    fun testAccountMethod() = runTest {
        val accountJson = """
        {
            "id": "GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7",
            "account_id": "GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7",
            "sequence": "3298702387052545",
            "sequence_ledger": 7654321,
            "sequence_time": 1640995200,
            "subentry_count": 0,
            "last_modified_ledger": 7654321,
            "last_modified_time": "2021-01-01T00:00:00Z",
            "num_sponsoring": 0,
            "num_sponsored": 0,
            "paging_token": "GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7",
            "thresholds": {
                "low_threshold": 0,
                "med_threshold": 0,
                "high_threshold": 0
            },
            "flags": {
                "auth_required": false,
                "auth_revocable": false,
                "auth_immutable": false,
                "auth_clawback_enabled": false
            },
            "balances": [
                {
                    "asset_type": "native",
                    "balance": "10.0000000",
                    "buying_liabilities": "0.0000000",
                    "selling_liabilities": "0.0000000"
                }
            ],
            "signers": [
                {
                    "weight": 1,
                    "key": "GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7",
                    "type": "ed25519_public_key"
                }
            ],
            "data": {},
            "_links": {
                "self": {"href": "https://horizon-testnet.stellar.org/accounts/GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7"},
                "transactions": {"href": "https://horizon-testnet.stellar.org/accounts/GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7/transactions"},
                "operations": {"href": "https://horizon-testnet.stellar.org/accounts/GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7/operations"},
                "payments": {"href": "https://horizon-testnet.stellar.org/accounts/GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7/payments"},
                "effects": {"href": "https://horizon-testnet.stellar.org/accounts/GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7/effects"},
                "offers": {"href": "https://horizon-testnet.stellar.org/accounts/GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7/offers"},
                "trades": {"href": "https://horizon-testnet.stellar.org/accounts/GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7/trades"},
                "data": {"href": "https://horizon-testnet.stellar.org/accounts/GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7/data/{key}", "templated": true}
            }
        }
        """.trimIndent()

        val mockClient = createMockClient(accountJson, expectedPath = "/accounts/")
        val server = HorizonServer("https://horizon-testnet.stellar.org", httpClient = mockClient)

        val accountId = "GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7"
        val account = server.accounts().account(accountId)

        assertEquals(accountId, account.accountId)
        assertEquals(3298702387052545L, account.sequenceNumber)
        assertEquals(0, account.subentryCount)
        assertEquals(1, account.balances.size)
        assertEquals("native", account.balances[0].assetType)

        server.close()
    }

    @Test
    fun testTransactionMethod() = runTest {
        val transactionJson = """
        {
            "id": "abc123def456",
            "paging_token": "abc123def456",
            "successful": true,
            "hash": "deadbeef",
            "ledger": 7654321,
            "created_at": "2021-01-01T00:00:00Z",
            "source_account": "GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7",
            "source_account_sequence": "3298702387052545",
            "fee_account": "GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7",
            "fee_charged": "100",
            "max_fee": "200",
            "operation_count": 1,
            "envelope_xdr": "AAAAAGL8HQvQkbK2HA3WVjRrKmjX00fG8sLI7m0ERwJW/AX3AAAAZAAiII0AAAABAAAAAAAAAAAAAAABAAAAAAAAAAAAAAAArqN6LeOagjxMaUP96Bzfs9e0corNZXzBWJkFoK7kvkwAAAAAO5rKAAAAAAAAAAABVvwF9wAAAECUSFLCrnOz",
            "result_xdr": "AAAAAAAAAGQAAAAAAAAAAQAAAAAAAAAAAAAAAAAAAAA=",
            "result_meta_xdr": "AAAAAQAAAAIAAAADAAcRcQAAAAAAAAAAYvwdC9CRsrYcDdZWNGsqaNfTR8bywsjubQRHAlb8BfcAAAAXSHbn/wAiII0AAAAAAAAAAAAAAAAAAAAAAAAAAAABAAAAAAAAAAAAAAAAAAAAAAAAAQAAAAU=",
            "fee_meta_xdr": "AAAAAgAAAAMAAcRcQAAAAAYvwdC9CRsrYcDdZWNGsqaNfTR8bywsjubQRHAlb8BfcAAAAXSHboAAAiII0AAAAAAAAAAAAAAAAAAAAAAAABAAAAAAAAAAAAAAAAAAAAAAAAAAAAAQAAAAAU=",
            "signatures": ["d2FiYWJh"],
            "memo_type": "none",
            "memo": null,
            "_links": {
                "self": {"href": "https://horizon-testnet.stellar.org/transactions/abc123def456"},
                "account": {"href": "https://horizon-testnet.stellar.org/accounts/GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7"},
                "ledger": {"href": "https://horizon-testnet.stellar.org/ledgers/7654321"},
                "operations": {"href": "https://horizon-testnet.stellar.org/transactions/abc123def456/operations"},
                "effects": {"href": "https://horizon-testnet.stellar.org/transactions/abc123def456/effects"},
                "precedes": {"href": "https://horizon-testnet.stellar.org/transactions?order=asc&cursor=abc123def456"},
                "succeeds": {"href": "https://horizon-testnet.stellar.org/transactions?order=desc&cursor=abc123def456"}
            }
        }
        """.trimIndent()

        val mockClient = createMockClient(transactionJson, expectedPath = "/transactions/")
        val server = HorizonServer("https://horizon-testnet.stellar.org", httpClient = mockClient)

        val transactionHash = "abc123def456"
        val transaction = server.transactions().transaction(transactionHash)

        assertEquals(transactionHash, transaction.id)
        assertEquals(true, transaction.successful)
        assertEquals("deadbeef", transaction.hash)
        assertEquals(7654321L, transaction.ledger)
        assertEquals(100L, transaction.feeCharged)
        assertEquals(1, transaction.operationCount)

        server.close()
    }

    @Test
    fun testOperationMethod() = runTest {
        val operationJson = """
        {
            "id": "123456789",
            "paging_token": "123456789",
            "transaction_successful": true,
            "source_account": "GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7",
            "type": "create_account",
            "created_at": "2021-01-01T00:00:00Z",
            "transaction_hash": "abc123def456",
            "starting_balance": "10.0000000",
            "funder": "GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7",
            "account": "GCXKG6RN4ONIEPCMNFB732A436Z5PNDSRLGWK7GBLCMQLIFO4S7EYWVU",
            "_links": {
                "self": {"href": "https://horizon-testnet.stellar.org/operations/123456789"},
                "transaction": {"href": "https://horizon-testnet.stellar.org/transactions/abc123def456"},
                "effects": {"href": "https://horizon-testnet.stellar.org/operations/123456789/effects"},
                "succeeds": {"href": "https://horizon-testnet.stellar.org/operations?order=desc&cursor=123456789"},
                "precedes": {"href": "https://horizon-testnet.stellar.org/operations?order=asc&cursor=123456789"}
            }
        }
        """.trimIndent()

        val mockClient = createMockClient(operationJson, expectedPath = "/operations/")
        val server = HorizonServer("https://horizon-testnet.stellar.org", httpClient = mockClient)

        val operationId = 123456789L
        val operation = server.operations().operation(operationId)

        assertEquals("123456789", operation.id)
        assertEquals(true, operation.transactionSuccessful)
        assertEquals("GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7", operation.sourceAccount)
        assertEquals("create_account", operation.type)

        server.close()
    }

    @Test
    fun testLedgerMethod() = runTest {
        val ledgerJson = """
        {
            "id": "abc123def456",
            "paging_token": "abc123def456",
            "hash": "deadbeef",
            "prev_hash": "prevhash123",
            "sequence": 7654321,
            "successful_transaction_count": 5,
            "failed_transaction_count": 1,
            "operation_count": 10,
            "tx_set_operation_count": 10,
            "closed_at": "2021-01-01T00:00:00Z",
            "total_coins": "105443902087.3472865",
            "fee_pool": "1807682.4683943",
            "base_fee_in_stroops": "100",
            "base_reserve_in_stroops": "5000000",
            "max_tx_set_size": 1000,
            "protocol_version": 20,
            "header_xdr": "AAAAFvwdC9CRsrYcDdZWNGsqaNfTR8bywsjubQRHAlb8BfcAAAAA",
            "_links": {
                "self": {"href": "https://horizon-testnet.stellar.org/ledgers/7654321"},
                "transactions": {"href": "https://horizon-testnet.stellar.org/ledgers/7654321/transactions"},
                "operations": {"href": "https://horizon-testnet.stellar.org/ledgers/7654321/operations"},
                "payments": {"href": "https://horizon-testnet.stellar.org/ledgers/7654321/payments"},
                "effects": {"href": "https://horizon-testnet.stellar.org/ledgers/7654321/effects"}
            }
        }
        """.trimIndent()

        val mockClient = createMockClient(ledgerJson, expectedPath = "/ledgers/")
        val server = HorizonServer("https://horizon-testnet.stellar.org", httpClient = mockClient)

        val ledgerSequence = 7654321L
        val ledger = server.ledgers().ledger(ledgerSequence)

        assertEquals("abc123def456", ledger.id)
        assertEquals(7654321L, ledger.sequence)
        assertEquals("deadbeef", ledger.hash)
        assertEquals(5, ledger.successfulTransactionCount)
        assertEquals(10, ledger.operationCount)
        assertEquals("100", ledger.baseFeeInStroops)

        server.close()
    }

    @Test
    fun testOfferMethod() = runTest {
        val offerJson = """
        {
            "id": "456789",
            "paging_token": "456789",
            "seller": "GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7",
            "selling": {
                "asset_type": "native"
            },
            "buying": {
                "asset_type": "credit_alphanum4",
                "asset_code": "USD",
                "asset_issuer": "GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7"
            },
            "amount": "10.0000000",
            "price_r": {
                "n": 1,
                "d": 2
            },
            "price": "0.5",
            "last_modified_ledger": 7654321,
            "last_modified_time": "2021-01-01T00:00:00Z",
            "_links": {
                "self": {"href": "https://horizon-testnet.stellar.org/offers/456789"},
                "offer_maker": {"href": "https://horizon-testnet.stellar.org/accounts/GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7"}
            }
        }
        """.trimIndent()

        val mockClient = createMockClient(offerJson, expectedPath = "/offers/")
        val server = HorizonServer("https://horizon-testnet.stellar.org", httpClient = mockClient)

        val offerId = 456789L
        val offer = server.offers().offer(offerId)

        assertEquals("456789", offer.id)
        assertEquals("GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7", offer.seller)
        assertEquals("native", offer.sellingAsset.assetType)
        assertEquals("credit_alphanum4", offer.buyingAsset.assetType)
        assertEquals("USD", offer.buyingAsset.assetCode)
        assertEquals("10.0000000", offer.amount)
        assertEquals("0.5", offer.price)

        server.close()
    }

    @Test
    fun testClaimableBalanceMethod() = runTest {
        val claimableBalanceJson = """
        {
            "id": "00000000929b20b72e5890ab51c24f1cc46fa01c4f318d8d33367d24dd614cfdf5491072",
            "asset": "native",
            "amount": "10.0000000",
            "sponsor": "GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7",
            "last_modified_ledger": 7654321,
            "last_modified_time": "2021-01-01T00:00:00Z",
            "paging_token": "00000000929b20b72e5890ab51c24f1cc46fa01c4f318d8d33367d24dd614cfdf5491072",
            "claimants": [
                {
                    "destination": "GCXKG6RN4ONIEPCMNFB732A436Z5PNDSRLGWK7GBLCMQLIFO4S7EYWVU",
                    "predicate": {
                        "unconditional": true
                    }
                }
            ],
            "flags": {
                "clawback_enabled": false
            },
            "_links": {
                "self": {"href": "https://horizon-testnet.stellar.org/claimable_balances/00000000929b20b72e5890ab51c24f1cc46fa01c4f318d8d33367d24dd614cfdf5491072"},
                "transactions": {"href": "https://horizon-testnet.stellar.org/claimable_balances/00000000929b20b72e5890ab51c24f1cc46fa01c4f318d8d33367d24dd614cfdf5491072/transactions"},
                "operations": {"href": "https://horizon-testnet.stellar.org/claimable_balances/00000000929b20b72e5890ab51c24f1cc46fa01c4f318d8d33367d24dd614cfdf5491072/operations"}
            }
        }
        """.trimIndent()

        val mockClient = createMockClient(claimableBalanceJson, expectedPath = "/claimable_balances/")
        val server = HorizonServer("https://horizon-testnet.stellar.org", httpClient = mockClient)

        val balanceId = "00000000929b20b72e5890ab51c24f1cc46fa01c4f318d8d33367d24dd614cfdf5491072"
        val claimableBalance = server.claimableBalances().claimableBalance(balanceId)

        assertEquals(balanceId, claimableBalance.id)
        assertEquals("native", claimableBalance.assetString)
        assertEquals("10.0000000", claimableBalance.amount)
        assertEquals("GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7", claimableBalance.sponsor)
        assertEquals(1, claimableBalance.claimants.size)

        server.close()
    }

    @Test
    fun testLiquidityPoolMethod() = runTest {
        val liquidityPoolJson = """
        {
            "id": "abcdef",
            "paging_token": "abcdef",
            "fee_bp": 30,
            "type": "constant_product",
            "total_trustlines": 300,
            "total_shares": "5000.0000000",
            "reserves": [
                {
                    "asset": "native",
                    "amount": "1000.0000000"
                },
                {
                    "asset": "EURT:GAP5LETOV6YIE62YAM56STDANPRDO7ZFDBGSNHJQIYGGKSMOZAHOOS2S",
                    "amount": "2000.0000000"
                }
            ],
            "_links": {
                "self": {"href": "https://horizon-testnet.stellar.org/liquidity_pools/abcdef"},
                "operations": {"href": "https://horizon-testnet.stellar.org/liquidity_pools/abcdef/operations"},
                "transactions": {"href": "https://horizon-testnet.stellar.org/liquidity_pools/abcdef/transactions"}
            }
        }
        """.trimIndent()

        val mockClient = createMockClient(liquidityPoolJson, expectedPath = "/liquidity_pools/")
        val server = HorizonServer("https://horizon-testnet.stellar.org", httpClient = mockClient)

        val poolId = "abcdef"
        val liquidityPool = server.liquidityPools().liquidityPool(poolId)

        assertEquals(poolId, liquidityPool.id)
        assertEquals(30, liquidityPool.feeBp)
        assertEquals("constant_product", liquidityPool.type)
        assertEquals(300L, liquidityPool.totalTrustlines)
        assertEquals("5000.0000000", liquidityPool.totalShares)
        assertEquals(2, liquidityPool.reserves.size)

        server.close()
    }

    // ========== Submit Transaction Methods ==========

    @Test
    fun testSubmitTransactionMethod() = runTest {
        val transactionJson = """
        {
            "id": "abc123def456",
            "paging_token": "abc123def456",
            "successful": true,
            "hash": "deadbeef",
            "ledger": 7654321,
            "created_at": "2021-01-01T00:00:00Z",
            "source_account": "GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7",
            "source_account_sequence": "3298702387052545",
            "fee_account": "GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7",
            "fee_charged": "100",
            "max_fee": "200",
            "operation_count": 1,
            "envelope_xdr": "AAAAAGL8HQvQkbK2HA3WVjRrKmjX00fG8sLI7m0ERwJW/AX3AAAAZAAiII0AAAABAAAAAAAAAAAAAAABAAAAAAAAAAAAAAAArqN6LeOagjxMaUP96Bzfs9e0corNZXzBWJkFoK7kvkwAAAAAO5rKAAAAAAAAAAABVvwF9wAAAECUSFLCrnOz",
            "result_xdr": "AAAAAAAAAGQAAAAAAAAAAQAAAAAAAAAAAAAAAAAAAAA=",
            "result_meta_xdr": "AAAAAQAAAAIAAAADAAcRcQAAAAAAAAAAYvwdC9CRsrYcDdZWNGsqaNfTR8bywsjubQRHAlb8BfcAAAAXSHbn/wAiII0AAAAAAAAAAAAAAAAAAAAAAAAAAAABAAAAAAAAAAAAAAAAAAAAAAAAAQAAAAU=",
            "fee_meta_xdr": "AAAAAgAAAAMAAcRcQAAAAAYvwdC9CRsrYcDdZWNGsqaNfTR8bywsjubQRHAlb8BfcAAAAXSHboAAAiII0AAAAAAAAAAAAAAAAAAAAAAAABAAAAAAAAAAAAAAAAAAAAAAAAAAAAAQAAAAAU=",
            "signatures": ["d2FiYWJh"],
            "memo_type": "none",
            "_links": {
                "self": {"href": "https://horizon-testnet.stellar.org/transactions/abc123def456"},
                "account": {"href": "https://horizon-testnet.stellar.org/accounts/GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7"},
                "ledger": {"href": "https://horizon-testnet.stellar.org/ledgers/7654321"},
                "operations": {"href": "https://horizon-testnet.stellar.org/transactions/abc123def456/operations"},
                "effects": {"href": "https://horizon-testnet.stellar.org/transactions/abc123def456/effects"},
                "precedes": {"href": "https://horizon-testnet.stellar.org/transactions?cursor=abc123def456&order=asc"},
                "succeeds": {"href": "https://horizon-testnet.stellar.org/transactions?cursor=abc123def456&order=desc"}
            }
        }
        """.trimIndent()

        val mockClient = createMockClient(transactionJson, expectedPath = "/transactions")
        val server = HorizonServer("https://horizon-testnet.stellar.org", 
            httpClient = mockClient, 
            submitHttpClient = mockClient)

        val envelopeXdr = "AAAAAGL8HQvQkbK2HA3WVjRrKmjX00fG8sLI7m0ERwJW/AX3AAAAZAAiII0AAAABAAAAAAAAAAAAAAABAAAAAAAAAAAAAAAArqN6LeOagjxMaUP96Bzfs9e0corNZXzBWJkFoK7kvkwAAAAAO5rKAAAAAAAAAAABVvwF9wAAAECUSFLCrnOz"
        val response = server.submitTransaction(envelopeXdr, skipMemoRequiredCheck = true)

        assertEquals("abc123def456", response.id)
        assertEquals(true, response.successful)
        assertEquals("deadbeef", response.hash)

        server.close()
    }

    @Test
    fun testSubmitTransactionAsyncMethod() = runTest {
        val asyncResponseJson = """
        {
            "hash": "abc123def456",
            "tx_status": "PENDING"
        }
        """.trimIndent()

        val mockClient = createMockClient(asyncResponseJson, 
            statusCode = HttpStatusCode.Created, 
            expectedPath = "/transactions_async")
        val server = HorizonServer("https://horizon-testnet.stellar.org", 
            httpClient = mockClient, 
            submitHttpClient = mockClient)

        val envelopeXdr = "AAAAAGL8HQvQkbK2HA3WVjRrKmjX00fG8sLI7m0ERwJW/AX3AAAAZAAiII0AAAABAAAAAAAAAAAAAAABAAAAAAAAAAAAAAAArqN6LeOagjxMaUP96Bzfs9e0corNZXzBWJkFoK7kvkwAAAAAO5rKAAAAAAAAAAABVvwF9wAAAECUSFLCrnOz"
        val response = server.submitTransactionAsync(envelopeXdr, skipMemoRequiredCheck = true)

        assertEquals("abc123def456", response.hash)
        assertEquals(com.soneso.stellar.sdk.horizon.responses.SubmitTransactionAsyncResponse.TransactionStatus.PENDING, response.txStatus)
        assertNull(response.errorResultXdr)

        server.close()
    }

    @Test
    fun testSubmitTransactionAsyncDuplicateStatus() = runTest {
        val asyncResponseJson = """
        {
            "hash": "abc123def456",
            "tx_status": "DUPLICATE"
        }
        """.trimIndent()

        val mockClient = createMockClient(asyncResponseJson, 
            statusCode = HttpStatusCode.Conflict, 
            expectedPath = "/transactions_async")
        val server = HorizonServer("https://horizon-testnet.stellar.org", 
            httpClient = mockClient, 
            submitHttpClient = mockClient)

        val envelopeXdr = "AAAAAGL8HQvQkbK2HA3WVjRrKmjX00fG8sLI7m0ERwJW/AX3AAAAZAAiII0AAAABAAAAAAAAAAAAAAABAAAAAAAAAAAAAAAArqN6LeOagjxMaUP96Bzfs9e0corNZXzBWJkFoK7kvkwAAAAAO5rKAAAAAAAAAAABVvwF9wAAAECUSFLCrnOz"
        val response = server.submitTransactionAsync(envelopeXdr, skipMemoRequiredCheck = true)

        assertEquals("abc123def456", response.hash)
        assertEquals(com.soneso.stellar.sdk.horizon.responses.SubmitTransactionAsyncResponse.TransactionStatus.DUPLICATE, response.txStatus)

        server.close()
    }

    @Test  
    fun testSubmitTransactionAsyncErrorStatus() = runTest {
        val asyncResponseJson = """
        {
            "hash": "abc123def456",
            "tx_status": "ERROR",
            "error_result_xdr": "AAAAAAAAAGT/////AAAAAQAAAAAAAAAB////+QAAAAA="
        }
        """.trimIndent()

        val mockClient = createMockClient(asyncResponseJson, 
            statusCode = HttpStatusCode.BadRequest, 
            expectedPath = "/transactions_async")
        val server = HorizonServer("https://horizon-testnet.stellar.org", 
            httpClient = mockClient, 
            submitHttpClient = mockClient)

        val envelopeXdr = "AAAAAGL8HQvQkbK2HA3WVjRrKmjX00fG8sLI7m0ERwJW/AX3AAAAZAAiII0AAAABAAAAAAAAAAAAAAABAAAAAAAAAAAAAAAArqN6LeOagjxMaUP96Bzfs9e0corNZXzBWJkFoK7kvkwAAAAAO5rKAAAAAAAAAAABVvwF9wAAAECUSFLCrnOz"
        val response = server.submitTransactionAsync(envelopeXdr, skipMemoRequiredCheck = true)

        assertEquals("abc123def456", response.hash)
        assertEquals(com.soneso.stellar.sdk.horizon.responses.SubmitTransactionAsyncResponse.TransactionStatus.ERROR, response.txStatus)
        assertEquals("AAAAAAAAAGT/////AAAAAQAAAAAAAAAB////+QAAAAA=", response.errorResultXdr)

        server.close()
    }

    // ========== Close Method ==========

    @Test
    fun testCloseMethod() {
        val mockClient = createMockClient("")
        val server = HorizonServer("https://horizon-testnet.stellar.org", httpClient = mockClient)

        // Should not throw
        server.close()

        // Multiple closes should not throw
        server.close()
    }

    // ========== Error Handling ==========

    @Test
    fun testNotFoundError() = runTest {
        val errorJson = """{"error": "Resource not found"}"""
        val mockClient = createMockClient(errorJson, HttpStatusCode.NotFound)
        val server = HorizonServer("https://horizon-testnet.stellar.org", httpClient = mockClient)

        assertFailsWith<com.soneso.stellar.sdk.horizon.exceptions.BadRequestException> {
            server.accounts().account("INVALID_ACCOUNT_ID")
        }

        server.close()
    }

    @Test
    fun testServerError() = runTest {
        val errorJson = """{"error": "Internal server error"}"""
        val mockClient = createMockClient(errorJson, HttpStatusCode.InternalServerError)
        val server = HorizonServer("https://horizon-testnet.stellar.org", httpClient = mockClient)

        assertFailsWith<com.soneso.stellar.sdk.horizon.exceptions.BadResponseException> {
            server.transactions().transaction("INVALID_HASH")
        }

        server.close()
    }
}