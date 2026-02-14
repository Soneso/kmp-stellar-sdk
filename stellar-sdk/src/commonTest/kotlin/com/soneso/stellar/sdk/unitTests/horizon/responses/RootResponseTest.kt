package com.soneso.stellar.sdk.unitTests.horizon.responses

import com.soneso.stellar.sdk.horizon.responses.Link
import com.soneso.stellar.sdk.horizon.responses.RootResponse
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RootResponseTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val rootJson = """
    {
        "horizon_version": "2.26.0",
        "core_version": "stellar-core 19.10.0 (236556c6768e6a4e1e7dd2a78d5f0e3b87a09f9e)",
        "ingest_latest_ledger": 7505182,
        "history_latest_ledger": 7505182,
        "history_latest_ledger_closed_at": "2021-01-01T00:00:00Z",
        "history_elder_ledger": 1,
        "core_latest_ledger": 7505183,
        "network_passphrase": "Test SDF Network ; September 2015",
        "current_protocol_version": 18,
        "supported_protocol_version": 18,
        "core_supported_protocol_version": 18,
        "_links": {
            "account": {"href": "https://horizon.stellar.org/accounts/{account_id}", "templated": true},
            "accounts": {"href": "https://horizon.stellar.org/accounts{?signer,sponsor,asset,liquidity_pool,cursor,limit,order}", "templated": true},
            "account_transactions": {"href": "https://horizon.stellar.org/accounts/{account_id}/transactions{?cursor,limit,order}", "templated": true},
            "assets": {"href": "https://horizon.stellar.org/assets{?asset_code,asset_issuer,cursor,limit,order}", "templated": true},
            "effects": {"href": "https://horizon.stellar.org/effects{?cursor,limit,order}", "templated": true},
            "fee_stats": {"href": "https://horizon.stellar.org/fee_stats"},
            "friendbot": {"href": "https://friendbot.stellar.org/?addr={account}", "templated": true},
            "ledger": {"href": "https://horizon.stellar.org/ledgers/{sequence}", "templated": true},
            "ledgers": {"href": "https://horizon.stellar.org/ledgers{?cursor,limit,order}", "templated": true},
            "self": {"href": "https://horizon.stellar.org/"},
            "transactions": {"href": "https://horizon.stellar.org/transactions{?cursor,limit,order}", "templated": true}
        }
    }
    """.trimIndent()

    @Test
    fun testDeserialization() {
        val root = json.decodeFromString<RootResponse>(rootJson)
        assertEquals("2.26.0", root.horizonVersion)
        assertTrue(root.stellarCoreVersion.contains("19.10.0"))
        assertEquals(7505182L, root.ingestLatestLedger)
        assertEquals(7505182L, root.historyLatestLedger)
        assertEquals("2021-01-01T00:00:00Z", root.historyLatestLedgerClosedAt)
        assertEquals(1L, root.historyElderLedger)
        assertEquals(7505183L, root.coreLatestLedger)
        assertEquals("Test SDF Network ; September 2015", root.networkPassphrase)
        assertEquals(18, root.currentProtocolVersion)
        assertEquals(18, root.supportedProtocolVersion)
        assertEquals(18, root.coreSupportedProtocolVersion)
    }

    @Test
    fun testLinks() {
        val root = json.decodeFromString<RootResponse>(rootJson)
        assertTrue(root.links.account?.templated == true)
        assertTrue(root.links.accounts?.href?.contains("accounts") == true)
        assertTrue(root.links.assets?.href?.contains("assets") == true)
        assertTrue(root.links.effects?.href?.contains("effects") == true)
        assertEquals("https://horizon.stellar.org/fee_stats", root.links.feeStats?.href)
        assertTrue(root.links.friendbot?.href?.contains("friendbot") == true)
        assertTrue(root.links.ledger?.href?.contains("ledgers") == true)
        assertTrue(root.links.ledgers?.href?.contains("ledgers") == true)
        assertEquals("https://horizon.stellar.org/", root.links.self?.href)
    }

    @Test
    fun testMinimalLinks() {
        val minimalJson = """
        {
            "horizon_version": "1.0",
            "core_version": "core",
            "history_latest_ledger": 1,
            "history_latest_ledger_closed_at": "2021-01-01T00:00:00Z",
            "history_elder_ledger": 1,
            "core_latest_ledger": 1,
            "network_passphrase": "test",
            "current_protocol_version": 1,
            "supported_protocol_version": 1,
            "core_supported_protocol_version": 1,
            "_links": {}
        }
        """.trimIndent()
        val root = json.decodeFromString<RootResponse>(minimalJson)
        assertNull(root.ingestLatestLedger)
        assertNull(root.links.account)
        assertNull(root.links.accounts)
        assertNull(root.links.claimableBalances)
        assertNull(root.links.liquidityPools)
        assertNull(root.links.offers)
        assertNull(root.links.operations)
        assertNull(root.links.orderBook)
        assertNull(root.links.payments)
        assertNull(root.links.strictReceivePaths)
        assertNull(root.links.strictSendPaths)
        assertNull(root.links.tradeAggregations)
        assertNull(root.links.trades)
        assertNull(root.links.transaction)
        assertNull(root.links.transactions)
    }

    @Test
    fun testRootResponseLinksInnerClassFullCoverage() {
        val fullLinksJson = """
        {
            "horizon_version": "2.18.0",
            "core_version": "19.6.0",
            "ingest_latest_ledger": 42345678,
            "history_latest_ledger": 42345677,
            "history_latest_ledger_closed_at": "2023-01-01T12:00:00Z",
            "history_elder_ledger": 1,
            "core_latest_ledger": 42345678,
            "network_passphrase": "Public Global Stellar Network ; September 2015",
            "current_protocol_version": 19,
            "supported_protocol_version": 19,
            "core_supported_protocol_version": 19,
            "_links": {
                "account": {"href": "https://horizon.stellar.org/accounts/{account_id}"},
                "accounts": {"href": "https://horizon.stellar.org/accounts"},
                "account_transactions": {"href": "https://horizon.stellar.org/accounts/{account_id}/transactions"},
                "claimable_balances": {"href": "https://horizon.stellar.org/claimable_balances"},
                "assets": {"href": "https://horizon.stellar.org/assets"},
                "effects": {"href": "https://horizon.stellar.org/effects"},
                "fee_stats": {"href": "https://horizon.stellar.org/fee_stats"},
                "friendbot": {"href": "https://friendbot.stellar.org/"},
                "ledger": {"href": "https://horizon.stellar.org/ledgers/{sequence}"},
                "ledgers": {"href": "https://horizon.stellar.org/ledgers"},
                "liquidity_pools": {"href": "https://horizon.stellar.org/liquidity_pools"},
                "offer": {"href": "https://horizon.stellar.org/offers/{offer_id}"},
                "offers": {"href": "https://horizon.stellar.org/offers"},
                "operation": {"href": "https://horizon.stellar.org/operations/{id}"},
                "operations": {"href": "https://horizon.stellar.org/operations"},
                "order_book": {"href": "https://horizon.stellar.org/order_book"},
                "payments": {"href": "https://horizon.stellar.org/payments"},
                "self": {"href": "https://horizon.stellar.org/"},
                "strict_receive_paths": {"href": "https://horizon.stellar.org/paths/strict-receive"},
                "strict_send_paths": {"href": "https://horizon.stellar.org/paths/strict-send"},
                "trade_aggregations": {"href": "https://horizon.stellar.org/trade_aggregations"},
                "trades": {"href": "https://horizon.stellar.org/trades"},
                "transaction": {"href": "https://horizon.stellar.org/transactions/{hash}"},
                "transactions": {"href": "https://horizon.stellar.org/transactions"}
            }
        }
        """.trimIndent()

        val rootResponse = json.decodeFromString<RootResponse>(fullLinksJson)

        assertEquals("2.18.0", rootResponse.horizonVersion)
        assertEquals("19.6.0", rootResponse.stellarCoreVersion)
        assertEquals(42345678L, rootResponse.ingestLatestLedger)
        assertEquals(42345677L, rootResponse.historyLatestLedger)
        assertEquals("2023-01-01T12:00:00Z", rootResponse.historyLatestLedgerClosedAt)
        assertEquals(1L, rootResponse.historyElderLedger)
        assertEquals(42345678L, rootResponse.coreLatestLedger)
        assertEquals("Public Global Stellar Network ; September 2015", rootResponse.networkPassphrase)
        assertEquals(19, rootResponse.currentProtocolVersion)

        val links = rootResponse.links
        assertNotNull(links)
        assertEquals("https://horizon.stellar.org/accounts/{account_id}", links.account?.href)
        assertEquals("https://horizon.stellar.org/accounts", links.accounts?.href)
        assertEquals("https://horizon.stellar.org/accounts/{account_id}/transactions", links.accountTransactions?.href)
        assertEquals("https://horizon.stellar.org/claimable_balances", links.claimableBalances?.href)
        assertEquals("https://horizon.stellar.org/assets", links.assets?.href)
        assertEquals("https://horizon.stellar.org/effects", links.effects?.href)
        assertEquals("https://horizon.stellar.org/fee_stats", links.feeStats?.href)
        assertEquals("https://friendbot.stellar.org/", links.friendbot?.href)
        assertEquals("https://horizon.stellar.org/ledgers/{sequence}", links.ledger?.href)
        assertEquals("https://horizon.stellar.org/ledgers", links.ledgers?.href)
        assertEquals("https://horizon.stellar.org/liquidity_pools", links.liquidityPools?.href)
        assertEquals("https://horizon.stellar.org/offers/{offer_id}", links.offer?.href)
        assertEquals("https://horizon.stellar.org/offers", links.offers?.href)
        assertEquals("https://horizon.stellar.org/operations/{id}", links.operation?.href)
        assertEquals("https://horizon.stellar.org/operations", links.operations?.href)
        assertEquals("https://horizon.stellar.org/order_book", links.orderBook?.href)
        assertEquals("https://horizon.stellar.org/payments", links.payments?.href)
        assertEquals("https://horizon.stellar.org/", links.self?.href)
        assertEquals("https://horizon.stellar.org/paths/strict-receive", links.strictReceivePaths?.href)
        assertEquals("https://horizon.stellar.org/paths/strict-send", links.strictSendPaths?.href)
        assertEquals("https://horizon.stellar.org/trade_aggregations", links.tradeAggregations?.href)
        assertEquals("https://horizon.stellar.org/trades", links.trades?.href)
        assertEquals("https://horizon.stellar.org/transactions/{hash}", links.transaction?.href)
        assertEquals("https://horizon.stellar.org/transactions", links.transactions?.href)
    }

    @Test
    fun testRootResponseLinksWithNullValues() {
        val emptyLinksJson = """
        {
            "horizon_version": "1.0.0",
            "core_version": "18.0.0",
            "history_latest_ledger": 100,
            "history_latest_ledger_closed_at": "2022-01-01T00:00:00Z",
            "history_elder_ledger": 1,
            "core_latest_ledger": 100,
            "network_passphrase": "Test SDF Network ; September 2015",
            "current_protocol_version": 18,
            "supported_protocol_version": 18,
            "core_supported_protocol_version": 18,
            "_links": {}
        }
        """.trimIndent()

        val rootResponse = json.decodeFromString<RootResponse>(emptyLinksJson)

        assertNull(rootResponse.ingestLatestLedger)

        val links = rootResponse.links
        assertNotNull(links)
        assertNull(links.account)
        assertNull(links.accounts)
        assertNull(links.accountTransactions)
        assertNull(links.claimableBalances)
        assertNull(links.assets)
        assertNull(links.effects)
        assertNull(links.feeStats)
        assertNull(links.friendbot)
        assertNull(links.ledger)
        assertNull(links.ledgers)
        assertNull(links.liquidityPools)
        assertNull(links.offer)
        assertNull(links.offers)
        assertNull(links.operation)
        assertNull(links.operations)
        assertNull(links.orderBook)
        assertNull(links.payments)
        assertNull(links.self)
        assertNull(links.strictReceivePaths)
        assertNull(links.strictSendPaths)
        assertNull(links.tradeAggregations)
        assertNull(links.trades)
        assertNull(links.transaction)
        assertNull(links.transactions)
    }

    @Test
    fun testDirectLinksConstruction() {
        val links = RootResponse.Links(
            account = Link("https://account.com"),
            accounts = Link("https://accounts.com"),
            accountTransactions = Link("https://account-tx.com"),
            claimableBalances = Link("https://claimable.com"),
            assets = Link("https://assets.com"),
            effects = Link("https://effects.com"),
            feeStats = Link("https://fees.com"),
            friendbot = Link("https://friendbot.com"),
            ledger = Link("https://ledger.com"),
            ledgers = Link("https://ledgers.com"),
            liquidityPools = Link("https://pools.com"),
            offer = Link("https://offer.com"),
            offers = Link("https://offers.com"),
            operation = Link("https://operation.com"),
            operations = Link("https://operations.com"),
            orderBook = Link("https://orderbook.com"),
            payments = Link("https://payments.com"),
            self = Link("https://self.com"),
            strictReceivePaths = Link("https://strict-receive.com"),
            strictSendPaths = Link("https://strict-send.com"),
            tradeAggregations = Link("https://aggregations.com"),
            trades = Link("https://trades.com"),
            transaction = Link("https://transaction.com"),
            transactions = Link("https://transactions.com")
        )

        assertEquals("https://account.com", links.account?.href)
        assertEquals("https://accounts.com", links.accounts?.href)
        assertEquals("https://account-tx.com", links.accountTransactions?.href)
        assertEquals("https://claimable.com", links.claimableBalances?.href)
        assertEquals("https://assets.com", links.assets?.href)
        assertEquals("https://effects.com", links.effects?.href)
        assertEquals("https://fees.com", links.feeStats?.href)
        assertEquals("https://friendbot.com", links.friendbot?.href)
        assertEquals("https://ledger.com", links.ledger?.href)
        assertEquals("https://ledgers.com", links.ledgers?.href)
        assertEquals("https://pools.com", links.liquidityPools?.href)
        assertEquals("https://offer.com", links.offer?.href)
        assertEquals("https://offers.com", links.offers?.href)
        assertEquals("https://operation.com", links.operation?.href)
        assertEquals("https://operations.com", links.operations?.href)
        assertEquals("https://orderbook.com", links.orderBook?.href)
        assertEquals("https://payments.com", links.payments?.href)
        assertEquals("https://self.com", links.self?.href)
        assertEquals("https://strict-receive.com", links.strictReceivePaths?.href)
        assertEquals("https://strict-send.com", links.strictSendPaths?.href)
        assertEquals("https://aggregations.com", links.tradeAggregations?.href)
        assertEquals("https://trades.com", links.trades?.href)
        assertEquals("https://transaction.com", links.transaction?.href)
        assertEquals("https://transactions.com", links.transactions?.href)
    }

    @Test
    fun testPartialLinksConstruction() {
        val partialLinks = RootResponse.Links(
            self = Link("https://horizon.stellar.org/"),
            accounts = Link("https://horizon.stellar.org/accounts"),
            transactions = Link("https://horizon.stellar.org/transactions"),
        )

        assertEquals("https://horizon.stellar.org/", partialLinks.self?.href)
        assertEquals("https://horizon.stellar.org/accounts", partialLinks.accounts?.href)
        assertEquals("https://horizon.stellar.org/transactions", partialLinks.transactions?.href)
        
        assertNull(partialLinks.account)
        assertNull(partialLinks.accountTransactions)
        assertNull(partialLinks.claimableBalances)
        assertNull(partialLinks.assets)
        assertNull(partialLinks.effects)
        assertNull(partialLinks.feeStats)
        assertNull(partialLinks.friendbot)
        assertNull(partialLinks.ledger)
        assertNull(partialLinks.ledgers)
        assertNull(partialLinks.liquidityPools)
        assertNull(partialLinks.offer)
        assertNull(partialLinks.offers)
        assertNull(partialLinks.operation)
        assertNull(partialLinks.operations)
        assertNull(partialLinks.orderBook)
        assertNull(partialLinks.payments)
        assertNull(partialLinks.strictReceivePaths)
        assertNull(partialLinks.strictSendPaths)
        assertNull(partialLinks.tradeAggregations)
        assertNull(partialLinks.trades)
        assertNull(partialLinks.transaction)
    }

    @Test
    fun testTestnetRootResponse() {
        val testnetRootJson = """
        {
            "horizon_version": "2.17.1",
            "core_version": "19.5.1",
            "ingest_latest_ledger": 1500000,
            "history_latest_ledger": 1499999,
            "history_latest_ledger_closed_at": "2023-06-01T15:30:45Z",
            "history_elder_ledger": 1,
            "core_latest_ledger": 1500000,
            "network_passphrase": "Test SDF Network ; September 2015",
            "current_protocol_version": 19,
            "supported_protocol_version": 19,
            "core_supported_protocol_version": 19,
            "_links": {
                "self": {"href": "https://horizon-testnet.stellar.org/"},
                "friendbot": {"href": "https://friendbot.stellar.org/"},
                "accounts": {"href": "https://horizon-testnet.stellar.org/accounts"},
                "transactions": {"href": "https://horizon-testnet.stellar.org/transactions"}
            }
        }
        """.trimIndent()

        val rootResponse = json.decodeFromString<RootResponse>(testnetRootJson)

        assertEquals("Test SDF Network ; September 2015", rootResponse.networkPassphrase)
        assertEquals("2.17.1", rootResponse.horizonVersion)
        assertEquals(1500000L, rootResponse.ingestLatestLedger)
        assertEquals(1499999L, rootResponse.historyLatestLedger)

        val links = rootResponse.links
        assertEquals("https://horizon-testnet.stellar.org/", links.self?.href)
        assertEquals("https://friendbot.stellar.org/", links.friendbot?.href)
        assertEquals("https://horizon-testnet.stellar.org/accounts", links.accounts?.href)
        assertEquals("https://horizon-testnet.stellar.org/transactions", links.transactions?.href)

        assertNull(links.account)
        assertNull(links.assets)
        assertNull(links.effects)
        assertNull(links.ledgers)
    }
}
