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
 * Tests for RequestBuilder execute() methods using Ktor MockEngine.
 * 
 * These tests focus on testing the HTTP execute() calls that happen in RequestBuilder
 * implementations, ensuring proper request execution and response parsing.
 */
class RequestBuilderExecuteTest {

    companion object {
        private const val TEST_SERVER_URL = "https://horizon-testnet.stellar.org"
        private const val ACCOUNT_ID = "GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7"
        private const val POOL_ID = "dd7b1ab831c273310ddbec6f97870aa83c2fbd78ce22aded37ecbf4f3380fac7"
        private const val CB_ID = "00000000da0d57da7d4850e7fc10d2a9d0ebc731f7afb40574c03395b17d49149b91f5be"
        private const val TX_HASH = "abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890"

        // Mock JSON responses
        private const val ACCOUNTS_PAGE_RESPONSE = """{
            "_embedded": {
                "records": [{
                    "id": "$ACCOUNT_ID",
                    "account_id": "$ACCOUNT_ID",
                    "sequence": "123456789",
                    "subentry_count": 0,
                    "last_modified_ledger": 12345,
                    "last_modified_time": "2023-01-01T00:00:00Z",
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
                    "balances": [{
                        "asset_type": "native",
                        "balance": "9999.0000000"
                    }],
                    "signers": [{
                        "key": "$ACCOUNT_ID",
                        "type": "ed25519_public_key",
                        "weight": 1
                    }],
                    "paging_token": "123456789",
                    "_links": {
                        "self": {"href": "$TEST_SERVER_URL/accounts/$ACCOUNT_ID"},
                        "transactions": {"href": "$TEST_SERVER_URL/accounts/$ACCOUNT_ID/transactions"},
                        "operations": {"href": "$TEST_SERVER_URL/accounts/$ACCOUNT_ID/operations"},
                        "payments": {"href": "$TEST_SERVER_URL/accounts/$ACCOUNT_ID/payments"},
                        "effects": {"href": "$TEST_SERVER_URL/accounts/$ACCOUNT_ID/effects"},
                        "offers": {"href": "$TEST_SERVER_URL/accounts/$ACCOUNT_ID/offers"},
                        "trades": {"href": "$TEST_SERVER_URL/accounts/$ACCOUNT_ID/trades"},
                        "data": {"href": "$TEST_SERVER_URL/accounts/$ACCOUNT_ID/data/{key}", "templated": true}
                    }
                }]
            },
            "_links": {
                "self": {"href": "$TEST_SERVER_URL/accounts"}
            }
        }"""

        private const val TRANSACTIONS_PAGE_RESPONSE = """{
            "_embedded": {
                "records": [{
                    "id": "$TX_HASH",
                    "paging_token": "12345-1",
                    "hash": "$TX_HASH",
                    "ledger": 12345,
                    "created_at": "2023-01-01T00:00:00Z",
                    "source_account": "$ACCOUNT_ID",
                    "source_account_sequence": 123456789,
                    "fee_account": "$ACCOUNT_ID",
                    "fee_charged": 100,
                    "max_fee": 100,
                    "operation_count": 1,
                    "envelope_xdr": "AAAAAgAAAAA=",
                    "result_xdr": "AAAAAAAAAGT/////AAAAAQAAAAAAAAAB////+wAAAAA=",
                    "result_meta_xdr": "AAAAAgAAAAIAAAAD",
                    "fee_meta_xdr": "AAAAAgAAAAA=",
                    "memo_type": "none",
                    "successful": true,
                    "signatures": ["AAAAabc123def456"],
                    "_links": {
                        "self": {"href": "$TEST_SERVER_URL/transactions/$TX_HASH"},
                        "account": {"href": "$TEST_SERVER_URL/accounts/$ACCOUNT_ID"},
                        "ledger": {"href": "$TEST_SERVER_URL/ledgers/12345"},
                        "operations": {"href": "$TEST_SERVER_URL/transactions/$TX_HASH/operations"},
                        "effects": {"href": "$TEST_SERVER_URL/transactions/$TX_HASH/effects"},
                        "precedes": {"href": "$TEST_SERVER_URL/transactions?cursor=12345-1&order=asc"},
                        "succeeds": {"href": "$TEST_SERVER_URL/transactions?cursor=12345-1&order=desc"}
                    }
                }]
            },
            "_links": {
                "self": {"href": "$TEST_SERVER_URL/transactions"}
            }
        }"""

        private const val OPERATIONS_PAGE_RESPONSE = """{
            "_embedded": {
                "records": [{
                    "id": "12345-1",
                    "paging_token": "12345-1",
                    "transaction_successful": true,
                    "source_account": "$ACCOUNT_ID",
                    "type": "payment",
                    "type_i": 1,
                    "created_at": "2023-01-01T00:00:00Z",
                    "transaction_hash": "$TX_HASH",
                    "from": "$ACCOUNT_ID",
                    "to": "$ACCOUNT_ID",
                    "asset_type": "native",
                    "amount": "10.0000000",
                    "_links": {
                        "self": {"href": "$TEST_SERVER_URL/operations/12345-1"},
                        "transaction": {"href": "$TEST_SERVER_URL/transactions/$TX_HASH"},
                        "effects": {"href": "$TEST_SERVER_URL/operations/12345-1/effects"},
                        "succeeds": {"href": "$TEST_SERVER_URL/operations?cursor=12345-1&order=asc"},
                        "precedes": {"href": "$TEST_SERVER_URL/operations?cursor=12345-1&order=desc"}
                    }
                }]
            },
            "_links": {
                "self": {"href": "$TEST_SERVER_URL/operations"}
            }
        }"""

        private const val EFFECTS_PAGE_RESPONSE = """{
            "_embedded": {
                "records": [{
                    "id": "0012345-0000000001-0000000001",
                    "paging_token": "12345-1-1",
                    "account": "$ACCOUNT_ID",
                    "type": "account_credited",
                    "type_i": 2,
                    "created_at": "2023-01-01T00:00:00Z",
                    "asset_type": "native",
                    "amount": "10.0000000",
                    "_links": {
                        "operation": {"href": "$TEST_SERVER_URL/operations/12345-1"},
                        "succeeds": {"href": "$TEST_SERVER_URL/effects?cursor=12345-1-1&order=asc"},
                        "precedes": {"href": "$TEST_SERVER_URL/effects?cursor=12345-1-1&order=desc"}
                    }
                }]
            },
            "_links": {
                "self": {"href": "$TEST_SERVER_URL/effects"}
            }
        }"""

        private const val LEDGERS_PAGE_RESPONSE = """{
            "_embedded": {
                "records": [{
                    "id": "d17d52ca7b33eed1c31334fb1ba8e3d6c37b1d99f5cd6e9ea548a40abd65bc0b",
                    "paging_token": "12345",
                    "hash": "d17d52ca7b33eed1c31334fb1ba8e3d6c37b1d99f5cd6e9ea548a40abd65bc0b",
                    "prev_hash": "b8ab3bd6e9ac60b6e7a49cfef8bf5b3d3c3e0bb7e6eee9e1b8e8f6c7d1a0b5c2",
                    "sequence": 12345,
                    "successful_transaction_count": 5,
                    "failed_transaction_count": 0,
                    "operation_count": 10,
                    "tx_set_operation_count": 10,
                    "closed_at": "2023-01-01T00:00:00Z",
                    "total_coins": "100000000000.0000000",
                    "fee_pool": "1000.0000000",
                    "base_fee_in_stroops": 100,
                    "base_reserve_in_stroops": 5000000,
                    "max_tx_set_size": 1000,
                    "protocol_version": 20,
                    "header_xdr": "AAAADLjLdMiK26JLFNg0/S8VN7Zw==",
                    "_links": {
                        "self": {"href": "$TEST_SERVER_URL/ledgers/12345"},
                        "transactions": {"href": "$TEST_SERVER_URL/ledgers/12345/transactions"},
                        "operations": {"href": "$TEST_SERVER_URL/ledgers/12345/operations"},
                        "payments": {"href": "$TEST_SERVER_URL/ledgers/12345/payments"},
                        "effects": {"href": "$TEST_SERVER_URL/ledgers/12345/effects"}
                    }
                }]
            },
            "_links": {
                "self": {"href": "$TEST_SERVER_URL/ledgers"}
            }
        }"""

        private const val OFFERS_PAGE_RESPONSE = """{
            "_embedded": {
                "records": [{
                    "id": "165561423",
                    "paging_token": "165561423",
                    "seller": "$ACCOUNT_ID",
                    "selling": {
                        "asset_type": "credit_alphanum4",
                        "asset_code": "USD",
                        "asset_issuer": "$ACCOUNT_ID"
                    },
                    "buying": {
                        "asset_type": "native"
                    },
                    "amount": "100.0000000",
                    "price_r": {
                        "n": 1,
                        "d": 1
                    },
                    "price": "1.0000000",
                    "last_modified_ledger": 12345,
                    "last_modified_time": "2023-01-01T00:00:00Z",
                    "_links": {
                        "self": {"href": "$TEST_SERVER_URL/offers/165561423"},
                        "offer_maker": {"href": "$TEST_SERVER_URL/accounts/$ACCOUNT_ID"}
                    }
                }]
            },
            "_links": {
                "self": {"href": "$TEST_SERVER_URL/offers"}
            }
        }"""

        private const val TRADES_PAGE_RESPONSE = """{
            "_embedded": {
                "records": [{
                    "id": "12345-1-1",
                    "paging_token": "12345-1-1",
                    "ledger_close_time": "2023-01-01T00:00:00Z",
                    "offer_id": "165561423",
                    "base_offer_id": "165561423",
                    "counter_offer_id": "165561424",
                    "base_account": "$ACCOUNT_ID",
                    "base_amount": "10.0000000",
                    "base_asset_type": "native",
                    "counter_account": "$ACCOUNT_ID",
                    "counter_amount": "10.0000000",
                    "counter_asset_type": "credit_alphanum4",
                    "counter_asset_code": "USD",
                    "counter_asset_issuer": "$ACCOUNT_ID",
                    "base_is_seller": true,
                    "price": {
                        "n": 1,
                        "d": 1
                    },
                    "trade_type": "orderbook",
                    "_links": {
                        "self": {"href": "$TEST_SERVER_URL/trades/12345-1-1"},
                        "base": {"href": "$TEST_SERVER_URL/accounts/$ACCOUNT_ID"},
                        "counter": {"href": "$TEST_SERVER_URL/accounts/$ACCOUNT_ID"},
                        "operation": {"href": "$TEST_SERVER_URL/operations/12345-1"}
                    }
                }]
            },
            "_links": {
                "self": {"href": "$TEST_SERVER_URL/trades"}
            }
        }"""

        private const val ASSETS_PAGE_RESPONSE = """{
            "_embedded": {
                "records": [{
                    "asset_type": "credit_alphanum4",
                    "asset_code": "USD",
                    "asset_issuer": "GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7",
                    "paging_token": "USD_GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7_credit_alphanum4",
                    "accounts": {
                        "authorized": 10,
                        "authorized_to_maintain_liabilities": 0,
                        "unauthorized": 0
                    },
                    "balances": {
                        "authorized": "1000.0000000",
                        "authorized_to_maintain_liabilities": "0.0000000",
                        "unauthorized": "0.0000000"
                    },
                    "flags": {
                        "auth_required": false,
                        "auth_revocable": false,
                        "auth_immutable": false,
                        "auth_clawback_enabled": false
                    },
                    "_links": {
                        "toml": {"href": "https://example.com/.well-known/stellar.toml"}
                    }
                }]
            },
            "_links": {
                "self": {"href": "$TEST_SERVER_URL/assets"}
            }
        }"""

        private const val CLAIMABLE_BALANCES_PAGE_RESPONSE = """{
            "_embedded": {
                "records": [{
                    "id": "$CB_ID",
                    "asset": "native",
                    "amount": "10.0000000",
                    "sponsor": "$ACCOUNT_ID",
                    "last_modified_ledger": 12345,
                    "last_modified_time": "2023-01-01T00:00:00Z",
                    "claimants": [{
                        "destination": "$ACCOUNT_ID",
                        "predicate": {
                            "unconditional": true
                        }
                    }],
                    "flags": {
                        "clawback_enabled": false
                    },
                    "paging_token": "$CB_ID",
                    "_links": {
                        "self": {"href": "$TEST_SERVER_URL/claimable_balances/$CB_ID"},
                        "transactions": {"href": "$TEST_SERVER_URL/claimable_balances/$CB_ID/transactions"},
                        "operations": {"href": "$TEST_SERVER_URL/claimable_balances/$CB_ID/operations"}
                    }
                }]
            },
            "_links": {
                "self": {"href": "$TEST_SERVER_URL/claimable_balances"}
            }
        }"""

        private const val LIQUIDITY_POOLS_PAGE_RESPONSE = """{
            "_embedded": {
                "records": [{
                    "id": "$POOL_ID",
                    "paging_token": "$POOL_ID",
                    "fee_bp": 30,
                    "type": "constant_product",
                    "total_trustlines": "10",
                    "total_shares": "1000.0000000",
                    "reserves": [{
                        "asset": "native",
                        "amount": "500.0000000"
                    }, {
                        "asset": "USD:$ACCOUNT_ID",
                        "amount": "500.0000000"
                    }],
                    "last_modified_ledger": 12345,
                    "last_modified_time": "2023-01-01T00:00:00Z",
                    "_links": {
                        "self": {"href": "$TEST_SERVER_URL/liquidity_pools/$POOL_ID"},
                        "transactions": {"href": "$TEST_SERVER_URL/liquidity_pools/$POOL_ID/transactions"},
                        "operations": {"href": "$TEST_SERVER_URL/liquidity_pools/$POOL_ID/operations"}
                    }
                }]
            },
            "_links": {
                "self": {"href": "$TEST_SERVER_URL/liquidity_pools"}
            }
        }"""

        private const val ORDERBOOK_RESPONSE = """{
            "bids": [{
                "price_r": {
                    "n": 99,
                    "d": 100
                },
                "price": "0.9900000",
                "amount": "100.0000000"
            }],
            "asks": [{
                "price_r": {
                    "n": 101,
                    "d": 100
                },
                "price": "1.0100000",
                "amount": "100.0000000"
            }],
            "base": {
                "asset_type": "native"
            },
            "counter": {
                "asset_type": "credit_alphanum4",
                "asset_code": "USD",
                "asset_issuer": "$ACCOUNT_ID"
            },
            "_links": {
                "self": {"href": "$TEST_SERVER_URL/order_book?selling_asset_type=native&buying_asset_type=credit_alphanum4&buying_asset_code=USD&buying_asset_issuer=$ACCOUNT_ID"}
            }
        }"""

        private const val PATHS_PAGE_RESPONSE = """{
            "_embedded": {
                "records": [{
                    "source_asset_type": "native",
                    "source_amount": "10.0000000",
                    "destination_asset_type": "credit_alphanum4",
                    "destination_asset_code": "USD",
                    "destination_asset_issuer": "$ACCOUNT_ID",
                    "destination_amount": "9.9000000",
                    "path": []
                }]
            },
            "_links": {
                "self": {"href": "$TEST_SERVER_URL/paths/strict-send"}
            }
        }"""

        private const val FEE_STATS_RESPONSE = """{
            "last_ledger": 12345,
            "last_ledger_base_fee": 100,
            "ledger_capacity_usage": "0.97",
            "fee_charged": {
                "max": "100",
                "min": "100",
                "mode": "100",
                "p10": "100",
                "p20": "100",
                "p30": "100",
                "p40": "100",
                "p50": "100",
                "p60": "100",
                "p70": "100",
                "p80": "100",
                "p90": "100",
                "p95": "100",
                "p99": "100"
            },
            "max_fee": {
                "max": "100",
                "min": "100",
                "mode": "100",
                "p10": "100",
                "p20": "100",
                "p30": "100",
                "p40": "100",
                "p50": "100",
                "p60": "100",
                "p70": "100",
                "p80": "100",
                "p90": "100",
                "p95": "100",
                "p99": "100"
            },
            "_links": {
                "self": {"href": "$TEST_SERVER_URL/fee_stats"}
            }
        }"""

        private const val HEALTH_RESPONSE = """{
            "database_connected": true,
            "core_up": true,
            "core_synced": true
        }"""

        private const val ROOT_RESPONSE = """{
            "_links": {
                "self": {"href": "$TEST_SERVER_URL/"},
                "account": {"href": "$TEST_SERVER_URL/accounts/{account_id}", "templated": true},
                "accounts": {"href": "$TEST_SERVER_URL/accounts{?signer,sponsor,asset,cursor,limit,order}", "templated": true},
                "claimable_balances": {"href": "$TEST_SERVER_URL/claimable_balances{?asset,sponsor,claimant,cursor,limit,order}", "templated": true},
                "assets": {"href": "$TEST_SERVER_URL/assets{?asset_code,asset_issuer,cursor,limit,order}", "templated": true},
                "effects": {"href": "$TEST_SERVER_URL/effects{?cursor,limit,order}", "templated": true},
                "fee_stats": {"href": "$TEST_SERVER_URL/fee_stats"},
                "ledgers": {"href": "$TEST_SERVER_URL/ledgers{?cursor,limit,order}", "templated": true},
                "liquidity_pools": {"href": "$TEST_SERVER_URL/liquidity_pools{?reserves,cursor,limit,order}", "templated": true},
                "offers": {"href": "$TEST_SERVER_URL/offers{?seller,buying,selling,cursor,limit,order}", "templated": true},
                "operations": {"href": "$TEST_SERVER_URL/operations{?cursor,limit,order,include_failed,join}", "templated": true},
                "orderbook": {"href": "$TEST_SERVER_URL/order_book{?selling_asset_type,selling_asset_code,selling_asset_issuer,buying_asset_type,buying_asset_code,buying_asset_issuer}", "templated": true},
                "payments": {"href": "$TEST_SERVER_URL/payments{?cursor,limit,order,include_failed,join}", "templated": true},
                "trade_aggregations": {"href": "$TEST_SERVER_URL/trade_aggregations{?start_time,end_time,resolution,offset,base_asset_type,base_asset_code,base_asset_issuer,counter_asset_type,counter_asset_code,counter_asset_issuer,order,limit}", "templated": true},
                "trades": {"href": "$TEST_SERVER_URL/trades{?base_asset_type,base_asset_code,base_asset_issuer,counter_asset_type,counter_asset_code,counter_asset_issuer,offer_id,cursor,limit,order}", "templated": true},
                "transactions": {"href": "$TEST_SERVER_URL/transactions{?cursor,limit,order,include_failed}", "templated": true}
            },
            "horizon_version": "2.0.0",
            "core_version": "v19.10.1",
            "ingest_latest_ledger": 12345,
            "history_latest_ledger": 12345,
            "history_latest_ledger_closed_at": "2023-01-01T00:00:00Z",
            "history_elder_ledger": 1,
            "core_latest_ledger": 12345,
            "network_passphrase": "Test SDF Network ; September 2015",
            "current_protocol_version": 20,
            "core_supported_protocol_version": 20,
            "supported_protocol_version": 20
        }"""
    }

    private fun createMockClient(responseJson: String): HttpClient {
        val mockEngine = MockEngine { request ->
            respond(
                content = responseJson,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
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

    // ===== AccountsRequestBuilder Tests =====

    @Test
    fun testAccountsExecute() = runTest {
        val mockClient = createMockClient(ACCOUNTS_PAGE_RESPONSE)
        val server = HorizonServer(TEST_SERVER_URL, httpClient = mockClient)

        val page = server.accounts().execute()

        assertNotNull(page)
        assertTrue(page.records.isNotEmpty())
        assertEquals(1, page.records.size)
        assertEquals(ACCOUNT_ID, page.records.first().accountId)

        server.close()
    }

    @Test
    fun testAccountsForSignerExecute() = runTest {
        val mockClient = createMockClient(ACCOUNTS_PAGE_RESPONSE)
        val server = HorizonServer(TEST_SERVER_URL, httpClient = mockClient)

        val page = server.accounts()
            .forSigner(ACCOUNT_ID)
            .execute()

        assertNotNull(page)
        assertTrue(page.records.isNotEmpty())

        server.close()
    }

    @Test
    fun testAccountsForAssetExecute() = runTest {
        val mockClient = createMockClient(ACCOUNTS_PAGE_RESPONSE)
        val server = HorizonServer(TEST_SERVER_URL, httpClient = mockClient)

        val page = server.accounts()
            .forAsset("USD", ACCOUNT_ID)
            .execute()

        assertNotNull(page)
        assertTrue(page.records.isNotEmpty())

        server.close()
    }

    @Test
    fun testAccountsForSponsorExecute() = runTest {
        val mockClient = createMockClient(ACCOUNTS_PAGE_RESPONSE)
        val server = HorizonServer(TEST_SERVER_URL, httpClient = mockClient)

        val page = server.accounts()
            .forSponsor(ACCOUNT_ID)
            .execute()

        assertNotNull(page)
        assertTrue(page.records.isNotEmpty())

        server.close()
    }

    @Test
    fun testAccountsForLiquidityPoolExecute() = runTest {
        val mockClient = createMockClient(ACCOUNTS_PAGE_RESPONSE)
        val server = HorizonServer(TEST_SERVER_URL, httpClient = mockClient)

        val page = server.accounts()
            .forLiquidityPool(POOL_ID)
            .execute()

        assertNotNull(page)
        assertTrue(page.records.isNotEmpty())

        server.close()
    }

    // ===== TransactionsRequestBuilder Tests =====

    @Test
    fun testTransactionsExecute() = runTest {
        val mockClient = createMockClient(TRANSACTIONS_PAGE_RESPONSE)
        val server = HorizonServer(TEST_SERVER_URL, httpClient = mockClient)

        val page = server.transactions().execute()

        assertNotNull(page)
        assertTrue(page.records.isNotEmpty())
        assertEquals(1, page.records.size)
        assertEquals(TX_HASH, page.records.first().hash)

        server.close()
    }

    @Test
    fun testTransactionsForAccountExecute() = runTest {
        val mockClient = createMockClient(TRANSACTIONS_PAGE_RESPONSE)
        val server = HorizonServer(TEST_SERVER_URL, httpClient = mockClient)

        val page = server.transactions()
            .forAccount(ACCOUNT_ID)
            .execute()

        assertNotNull(page)
        assertTrue(page.records.isNotEmpty())

        server.close()
    }

    @Test
    fun testTransactionsForLedgerExecute() = runTest {
        val mockClient = createMockClient(TRANSACTIONS_PAGE_RESPONSE)
        val server = HorizonServer(TEST_SERVER_URL, httpClient = mockClient)

        val page = server.transactions()
            .forLedger(12345L)
            .execute()

        assertNotNull(page)
        assertTrue(page.records.isNotEmpty())

        server.close()
    }

    @Test
    fun testTransactionsForClaimableBalanceExecute() = runTest {
        val mockClient = createMockClient(TRANSACTIONS_PAGE_RESPONSE)
        val server = HorizonServer(TEST_SERVER_URL, httpClient = mockClient)

        val page = server.transactions()
            .forClaimableBalance(CB_ID)
            .execute()

        assertNotNull(page)
        assertTrue(page.records.isNotEmpty())

        server.close()
    }

    @Test
    fun testTransactionsForLiquidityPoolExecute() = runTest {
        val mockClient = createMockClient(TRANSACTIONS_PAGE_RESPONSE)
        val server = HorizonServer(TEST_SERVER_URL, httpClient = mockClient)

        val page = server.transactions()
            .forLiquidityPool(POOL_ID)
            .execute()

        assertNotNull(page)
        assertTrue(page.records.isNotEmpty())

        server.close()
    }

    @Test
    fun testTransactionsIncludeFailedExecute() = runTest {
        val mockClient = createMockClient(TRANSACTIONS_PAGE_RESPONSE)
        val server = HorizonServer(TEST_SERVER_URL, httpClient = mockClient)

        val page = server.transactions()
            .includeFailed(true)
            .execute()

        assertNotNull(page)
        assertTrue(page.records.isNotEmpty())

        server.close()
    }

    // ===== OperationsRequestBuilder Tests =====

    @Test
    fun testOperationsExecute() = runTest {
        val mockClient = createMockClient(OPERATIONS_PAGE_RESPONSE)
        val server = HorizonServer(TEST_SERVER_URL, httpClient = mockClient)

        val page = server.operations().execute()

        assertNotNull(page)
        assertTrue(page.records.isNotEmpty())
        assertEquals(1, page.records.size)

        server.close()
    }

    @Test
    fun testOperationsForAccountExecute() = runTest {
        val mockClient = createMockClient(OPERATIONS_PAGE_RESPONSE)
        val server = HorizonServer(TEST_SERVER_URL, httpClient = mockClient)

        val page = server.operations()
            .forAccount(ACCOUNT_ID)
            .execute()

        assertNotNull(page)
        assertTrue(page.records.isNotEmpty())

        server.close()
    }

    @Test
    fun testOperationsForLedgerExecute() = runTest {
        val mockClient = createMockClient(OPERATIONS_PAGE_RESPONSE)
        val server = HorizonServer(TEST_SERVER_URL, httpClient = mockClient)

        val page = server.operations()
            .forLedger(12345L)
            .execute()

        assertNotNull(page)
        assertTrue(page.records.isNotEmpty())

        server.close()
    }

    @Test
    fun testOperationsForTransactionExecute() = runTest {
        val mockClient = createMockClient(OPERATIONS_PAGE_RESPONSE)
        val server = HorizonServer(TEST_SERVER_URL, httpClient = mockClient)

        val page = server.operations()
            .forTransaction(TX_HASH)
            .execute()

        assertNotNull(page)
        assertTrue(page.records.isNotEmpty())

        server.close()
    }

    @Test
    fun testOperationsIncludeFailedExecute() = runTest {
        val mockClient = createMockClient(OPERATIONS_PAGE_RESPONSE)
        val server = HorizonServer(TEST_SERVER_URL, httpClient = mockClient)

        val page = server.operations()
            .includeFailed(true)
            .execute()

        assertNotNull(page)
        assertTrue(page.records.isNotEmpty())

        server.close()
    }

    @Test
    fun testOperationsIncludeTransactionsExecute() = runTest {
        val mockClient = createMockClient(OPERATIONS_PAGE_RESPONSE)
        val server = HorizonServer(TEST_SERVER_URL, httpClient = mockClient)

        val page = server.operations()
            .includeTransactions(true)
            .execute()

        assertNotNull(page)
        assertTrue(page.records.isNotEmpty())

        server.close()
    }

    // ===== PaymentsRequestBuilder Tests =====

    @Test
    fun testPaymentsExecute() = runTest {
        val mockClient = createMockClient(OPERATIONS_PAGE_RESPONSE) // Payments use operation response format
        val server = HorizonServer(TEST_SERVER_URL, httpClient = mockClient)

        val page = server.payments().execute()

        assertNotNull(page)
        assertTrue(page.records.isNotEmpty())

        server.close()
    }

    @Test
    fun testPaymentsForAccountExecute() = runTest {
        val mockClient = createMockClient(OPERATIONS_PAGE_RESPONSE)
        val server = HorizonServer(TEST_SERVER_URL, httpClient = mockClient)

        val page = server.payments()
            .forAccount(ACCOUNT_ID)
            .execute()

        assertNotNull(page)
        assertTrue(page.records.isNotEmpty())

        server.close()
    }

    @Test
    fun testPaymentsForLedgerExecute() = runTest {
        val mockClient = createMockClient(OPERATIONS_PAGE_RESPONSE)
        val server = HorizonServer(TEST_SERVER_URL, httpClient = mockClient)

        val page = server.payments()
            .forLedger(12345L)
            .execute()

        assertNotNull(page)
        assertTrue(page.records.isNotEmpty())

        server.close()
    }

    @Test
    fun testPaymentsForTransactionExecute() = runTest {
        val mockClient = createMockClient(OPERATIONS_PAGE_RESPONSE)
        val server = HorizonServer(TEST_SERVER_URL, httpClient = mockClient)

        val page = server.payments()
            .forTransaction(TX_HASH)
            .execute()

        assertNotNull(page)
        assertTrue(page.records.isNotEmpty())

        server.close()
    }

    @Test
    fun testPaymentsIncludeFailedExecute() = runTest {
        val mockClient = createMockClient(OPERATIONS_PAGE_RESPONSE)
        val server = HorizonServer(TEST_SERVER_URL, httpClient = mockClient)

        val page = server.payments()
            .includeFailed(true)
            .execute()

        assertNotNull(page)
        assertTrue(page.records.isNotEmpty())

        server.close()
    }

    @Test
    fun testPaymentsIncludeTransactionsExecute() = runTest {
        val mockClient = createMockClient(OPERATIONS_PAGE_RESPONSE)
        val server = HorizonServer(TEST_SERVER_URL, httpClient = mockClient)

        val page = server.payments()
            .includeTransactions(true)
            .execute()

        assertNotNull(page)
        assertTrue(page.records.isNotEmpty())

        server.close()
    }

    // ===== EffectsRequestBuilder Tests =====

    @Test
    fun testEffectsExecute() = runTest {
        val mockClient = createMockClient(EFFECTS_PAGE_RESPONSE)
        val server = HorizonServer(TEST_SERVER_URL, httpClient = mockClient)

        val page = server.effects().execute()

        assertNotNull(page)
        assertTrue(page.records.isNotEmpty())
        assertEquals(1, page.records.size)

        server.close()
    }

    @Test
    fun testEffectsForAccountExecute() = runTest {
        val mockClient = createMockClient(EFFECTS_PAGE_RESPONSE)
        val server = HorizonServer(TEST_SERVER_URL, httpClient = mockClient)

        val page = server.effects()
            .forAccount(ACCOUNT_ID)
            .execute()

        assertNotNull(page)
        assertTrue(page.records.isNotEmpty())

        server.close()
    }

    @Test
    fun testEffectsForLedgerExecute() = runTest {
        val mockClient = createMockClient(EFFECTS_PAGE_RESPONSE)
        val server = HorizonServer(TEST_SERVER_URL, httpClient = mockClient)

        val page = server.effects()
            .forLedger(12345L)
            .execute()

        assertNotNull(page)
        assertTrue(page.records.isNotEmpty())

        server.close()
    }

    @Test
    fun testEffectsForTransactionExecute() = runTest {
        val mockClient = createMockClient(EFFECTS_PAGE_RESPONSE)
        val server = HorizonServer(TEST_SERVER_URL, httpClient = mockClient)

        val page = server.effects()
            .forTransaction(TX_HASH)
            .execute()

        assertNotNull(page)
        assertTrue(page.records.isNotEmpty())

        server.close()
    }

    @Test
    fun testEffectsForOperationExecute() = runTest {
        val mockClient = createMockClient(EFFECTS_PAGE_RESPONSE)
        val server = HorizonServer(TEST_SERVER_URL, httpClient = mockClient)

        val page = server.effects()
            .forOperation(12345L)
            .execute()

        assertNotNull(page)
        assertTrue(page.records.isNotEmpty())

        server.close()
    }

    @Test
    fun testEffectsForLiquidityPoolExecute() = runTest {
        val mockClient = createMockClient(EFFECTS_PAGE_RESPONSE)
        val server = HorizonServer(TEST_SERVER_URL, httpClient = mockClient)

        val page = server.effects()
            .forLiquidityPool(POOL_ID)
            .execute()

        assertNotNull(page)
        assertTrue(page.records.isNotEmpty())

        server.close()
    }

    // ===== LedgersRequestBuilder Tests =====

    @Test
    fun testLedgersExecute() = runTest {
        val mockClient = createMockClient(LEDGERS_PAGE_RESPONSE)
        val server = HorizonServer(TEST_SERVER_URL, httpClient = mockClient)

        val page = server.ledgers().execute()

        assertNotNull(page)
        assertTrue(page.records.isNotEmpty())
        assertEquals(1, page.records.size)
        assertEquals(12345, page.records.first().sequence)

        server.close()
    }

    // ===== OffersRequestBuilder Tests =====

    @Test
    fun testOffersExecute() = runTest {
        val mockClient = createMockClient(OFFERS_PAGE_RESPONSE)
        val server = HorizonServer(TEST_SERVER_URL, httpClient = mockClient)

        val page = server.offers().execute()

        assertNotNull(page)
        assertTrue(page.records.isNotEmpty())
        assertEquals(1, page.records.size)

        server.close()
    }

    @Test
    fun testOffersForSellerExecute() = runTest {
        val mockClient = createMockClient(OFFERS_PAGE_RESPONSE)
        val server = HorizonServer(TEST_SERVER_URL, httpClient = mockClient)

        val page = server.offers()
            .forSeller(ACCOUNT_ID)
            .execute()

        assertNotNull(page)
        assertTrue(page.records.isNotEmpty())

        server.close()
    }

    @Test
    fun testOffersForBuyingAssetExecute() = runTest {
        val mockClient = createMockClient(OFFERS_PAGE_RESPONSE)
        val server = HorizonServer(TEST_SERVER_URL, httpClient = mockClient)

        val page = server.offers()
            .forBuyingAsset("native")
            .execute()

        assertNotNull(page)
        assertTrue(page.records.isNotEmpty())

        server.close()
    }

    @Test
    fun testOffersForSellingAssetExecute() = runTest {
        val mockClient = createMockClient(OFFERS_PAGE_RESPONSE)
        val server = HorizonServer(TEST_SERVER_URL, httpClient = mockClient)

        val page = server.offers()
            .forSellingAsset("credit_alphanum4", "USD", ACCOUNT_ID)
            .execute()

        assertNotNull(page)
        assertTrue(page.records.isNotEmpty())

        server.close()
    }

    @Test
    fun testOffersForSponsorExecute() = runTest {
        val mockClient = createMockClient(OFFERS_PAGE_RESPONSE)
        val server = HorizonServer(TEST_SERVER_URL, httpClient = mockClient)

        val page = server.offers()
            .forSponsor(ACCOUNT_ID)
            .execute()

        assertNotNull(page)
        assertTrue(page.records.isNotEmpty())

        server.close()
    }

    // ===== TradesRequestBuilder Tests =====

    @Test
    fun testTradesExecute() = runTest {
        val mockClient = createMockClient(TRADES_PAGE_RESPONSE)
        val server = HorizonServer(TEST_SERVER_URL, httpClient = mockClient)

        val page = server.trades().execute()

        assertNotNull(page)
        assertTrue(page.records.isNotEmpty())
        assertEquals(1, page.records.size)

        server.close()
    }

    @Test
    fun testTradesForAccountExecute() = runTest {
        val mockClient = createMockClient(TRADES_PAGE_RESPONSE)
        val server = HorizonServer(TEST_SERVER_URL, httpClient = mockClient)

        val page = server.trades()
            .forAccount(ACCOUNT_ID)
            .execute()

        assertNotNull(page)
        assertTrue(page.records.isNotEmpty())

        server.close()
    }

    @Test
    fun testTradesForLiquidityPoolExecute() = runTest {
        val mockClient = createMockClient(TRADES_PAGE_RESPONSE)
        val server = HorizonServer(TEST_SERVER_URL, httpClient = mockClient)

        val page = server.trades()
            .forLiquidityPool(POOL_ID)
            .execute()

        assertNotNull(page)
        assertTrue(page.records.isNotEmpty())

        server.close()
    }

    @Test
    fun testTradesForOfferIdExecute() = runTest {
        val mockClient = createMockClient(TRADES_PAGE_RESPONSE)
        val server = HorizonServer(TEST_SERVER_URL, httpClient = mockClient)

        val page = server.trades()
            .forOfferId(165561423L)
            .execute()

        assertNotNull(page)
        assertTrue(page.records.isNotEmpty())

        server.close()
    }

    @Test
    fun testTradesForBaseAssetExecute() = runTest {
        val mockClient = createMockClient(TRADES_PAGE_RESPONSE)
        val server = HorizonServer(TEST_SERVER_URL, httpClient = mockClient)

        val page = server.trades()
            .forBaseAsset("native")
            .execute()

        assertNotNull(page)
        assertTrue(page.records.isNotEmpty())

        server.close()
    }

    @Test
    fun testTradesForCounterAssetExecute() = runTest {
        val mockClient = createMockClient(TRADES_PAGE_RESPONSE)
        val server = HorizonServer(TEST_SERVER_URL, httpClient = mockClient)

        val page = server.trades()
            .forCounterAsset("credit_alphanum4", "USD", ACCOUNT_ID)
            .execute()

        assertNotNull(page)
        assertTrue(page.records.isNotEmpty())

        server.close()
    }

    @Test
    fun testTradesForTradeTypeExecute() = runTest {
        val mockClient = createMockClient(TRADES_PAGE_RESPONSE)
        val server = HorizonServer(TEST_SERVER_URL, httpClient = mockClient)

        val page = server.trades()
            .forTradeType("orderbook")
            .execute()

        assertNotNull(page)
        assertTrue(page.records.isNotEmpty())

        server.close()
    }

    // ===== AssetsRequestBuilder Tests =====

    @Test
    fun testAssetsExecute() = runTest {
        val mockClient = createMockClient(ASSETS_PAGE_RESPONSE)
        val server = HorizonServer(TEST_SERVER_URL, httpClient = mockClient)

        val page = server.assets().execute()

        assertNotNull(page)
        assertTrue(page.records.isNotEmpty())
        assertEquals(1, page.records.size)

        server.close()
    }

    @Test
    fun testAssetsForAssetCodeExecute() = runTest {
        val mockClient = createMockClient(ASSETS_PAGE_RESPONSE)
        val server = HorizonServer(TEST_SERVER_URL, httpClient = mockClient)

        val page = server.assets()
            .forAssetCode("USD")
            .execute()

        assertNotNull(page)
        assertTrue(page.records.isNotEmpty())

        server.close()
    }

    @Test
    fun testAssetsForAssetIssuerExecute() = runTest {
        val mockClient = createMockClient(ASSETS_PAGE_RESPONSE)
        val server = HorizonServer(TEST_SERVER_URL, httpClient = mockClient)

        val page = server.assets()
            .forAssetIssuer(ACCOUNT_ID)
            .execute()

        assertNotNull(page)
        assertTrue(page.records.isNotEmpty())

        server.close()
    }

    // ===== ClaimableBalancesRequestBuilder Tests =====

    @Test
    fun testClaimableBalancesExecute() = runTest {
        val mockClient = createMockClient(CLAIMABLE_BALANCES_PAGE_RESPONSE)
        val server = HorizonServer(TEST_SERVER_URL, httpClient = mockClient)

        val page = server.claimableBalances().execute()

        assertNotNull(page)
        assertTrue(page.records.isNotEmpty())
        assertEquals(1, page.records.size)

        server.close()
    }

    @Test
    fun testClaimableBalancesForSponsorExecute() = runTest {
        val mockClient = createMockClient(CLAIMABLE_BALANCES_PAGE_RESPONSE)
        val server = HorizonServer(TEST_SERVER_URL, httpClient = mockClient)

        val page = server.claimableBalances()
            .forSponsor(ACCOUNT_ID)
            .execute()

        assertNotNull(page)
        assertTrue(page.records.isNotEmpty())

        server.close()
    }

    @Test
    fun testClaimableBalancesForAssetExecute() = runTest {
        val mockClient = createMockClient(CLAIMABLE_BALANCES_PAGE_RESPONSE)
        val server = HorizonServer(TEST_SERVER_URL, httpClient = mockClient)

        val page = server.claimableBalances()
            .forAsset("native")
            .execute()

        assertNotNull(page)
        assertTrue(page.records.isNotEmpty())

        server.close()
    }

    @Test
    fun testClaimableBalancesForClaimantExecute() = runTest {
        val mockClient = createMockClient(CLAIMABLE_BALANCES_PAGE_RESPONSE)
        val server = HorizonServer(TEST_SERVER_URL, httpClient = mockClient)

        val page = server.claimableBalances()
            .forClaimant(ACCOUNT_ID)
            .execute()

        assertNotNull(page)
        assertTrue(page.records.isNotEmpty())

        server.close()
    }

    // ===== LiquidityPoolsRequestBuilder Tests =====

    @Test
    fun testLiquidityPoolsExecute() = runTest {
        val mockClient = createMockClient(LIQUIDITY_POOLS_PAGE_RESPONSE)
        val server = HorizonServer(TEST_SERVER_URL, httpClient = mockClient)

        val page = server.liquidityPools().execute()

        assertNotNull(page)
        assertTrue(page.records.isNotEmpty())
        assertEquals(1, page.records.size)

        server.close()
    }

    @Test
    fun testLiquidityPoolsForReservesExecute() = runTest {
        val mockClient = createMockClient(LIQUIDITY_POOLS_PAGE_RESPONSE)
        val server = HorizonServer(TEST_SERVER_URL, httpClient = mockClient)

        val page = server.liquidityPools()
            .forReserves("native", "USD:$ACCOUNT_ID")
            .execute()

        assertNotNull(page)
        assertTrue(page.records.isNotEmpty())

        server.close()
    }

    @Test
    fun testLiquidityPoolsForAccountExecute() = runTest {
        val mockClient = createMockClient(LIQUIDITY_POOLS_PAGE_RESPONSE)
        val server = HorizonServer(TEST_SERVER_URL, httpClient = mockClient)

        val page = server.liquidityPools()
            .forAccount(ACCOUNT_ID)
            .execute()

        assertNotNull(page)
        assertTrue(page.records.isNotEmpty())

        server.close()
    }

    // ===== OrderBookRequestBuilder Tests =====

    @Test
    fun testOrderBookExecute() = runTest {
        val mockClient = createMockClient(ORDERBOOK_RESPONSE)
        val server = HorizonServer(TEST_SERVER_URL, httpClient = mockClient)

        val orderBook = server.orderBook()
            .buyingAsset("credit_alphanum4", "USD", ACCOUNT_ID)
            .sellingAsset("native")
            .execute()

        assertNotNull(orderBook)
        assertTrue(orderBook.bids.isNotEmpty())
        assertTrue(orderBook.asks.isNotEmpty())

        server.close()
    }

    // ===== StrictSendPathsRequestBuilder Tests =====

    @Test
    fun testStrictSendPathsExecute() = runTest {
        val mockClient = createMockClient(PATHS_PAGE_RESPONSE)
        val server = HorizonServer(TEST_SERVER_URL, httpClient = mockClient)

        val page = server.strictSendPaths()
            .sourceAsset("native")
            .sourceAmount("10.0")
            .destinationAccount(ACCOUNT_ID)
            .execute()

        assertNotNull(page)
        assertTrue(page.records.isNotEmpty())

        server.close()
    }

    // ===== StrictReceivePathsRequestBuilder Tests =====

    @Test
    fun testStrictReceivePathsExecute() = runTest {
        val mockClient = createMockClient(PATHS_PAGE_RESPONSE)
        val server = HorizonServer(TEST_SERVER_URL, httpClient = mockClient)

        val page = server.strictReceivePaths()
            .sourceAccount(ACCOUNT_ID)
            .destinationAsset("credit_alphanum4", "USD", ACCOUNT_ID)
            .destinationAmount("10.0")
            .execute()

        assertNotNull(page)
        assertTrue(page.records.isNotEmpty())

        server.close()
    }

    // ===== FeeStatsRequestBuilder Tests =====

    @Test
    fun testFeeStatsExecute() = runTest {
        val mockClient = createMockClient(FEE_STATS_RESPONSE)
        val server = HorizonServer(TEST_SERVER_URL, httpClient = mockClient)

        val feeStats = server.feeStats().execute()

        assertNotNull(feeStats)
        assertEquals(12345L, feeStats.lastLedger)
        assertEquals(100L, feeStats.lastLedgerBaseFee)

        server.close()
    }

    // ===== HealthRequestBuilder Tests =====

    @Test
    fun testHealthExecute() = runTest {
        val mockClient = createMockClient(HEALTH_RESPONSE)
        val server = HorizonServer(TEST_SERVER_URL, httpClient = mockClient)

        val health = server.health().execute()

        assertNotNull(health)
        assertTrue(health.isHealthy)
        assertTrue(health.databaseConnected)
        assertTrue(health.coreUp)
        assertTrue(health.coreSynced)

        server.close()
    }

    // ===== RootRequestBuilder Tests =====

    @Test
    fun testRootExecute() = runTest {
        val mockClient = createMockClient(ROOT_RESPONSE)
        val server = HorizonServer(TEST_SERVER_URL, httpClient = mockClient)

        val root = server.root().execute()

        assertNotNull(root)
        assertEquals("2.0.0", root.horizonVersion)
        assertEquals("v19.10.1", root.stellarCoreVersion)

        server.close()
    }
}