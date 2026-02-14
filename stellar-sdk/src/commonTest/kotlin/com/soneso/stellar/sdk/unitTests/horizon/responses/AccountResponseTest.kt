package com.soneso.stellar.sdk.unitTests.horizon.responses

import com.soneso.stellar.sdk.horizon.responses.AccountResponse
import com.soneso.stellar.sdk.horizon.responses.Link
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AccountResponseTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val accountJson = """
    {
        "id": "GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7",
        "account_id": "GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7",
        "sequence": 3298702387052545,
        "sequence_ledger": 7654321,
        "sequence_time": 1609459200,
        "subentry_count": 1,
        "inflation_destination": "GBVFTZL5HIPT4PFQVTZVIWR77V7LWYCXU4CLYWWHHOEXB64XPG5LDMTU",
        "home_domain": "example.com",
        "last_modified_ledger": 7654321,
        "last_modified_time": "2021-01-01T00:00:00Z",
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
                "asset_type": "credit_alphanum4",
                "asset_code": "USD",
                "asset_issuer": "GCDNJUBQSX7AJWLJACMJ7I4BC3Z47BQUTMHEICZLE6MU4KQBRYG5JY6B",
                "balance": "100.0000000",
                "buying_liabilities": "0.0000000",
                "selling_liabilities": "0.0000000",
                "limit": "922337203685.4775807",
                "is_authorized": true,
                "is_authorized_to_maintain_liabilities": true,
                "is_clawback_enabled": false,
                "last_modified_ledger": 7654320
            },
            {
                "asset_type": "native",
                "balance": "999.9999900",
                "buying_liabilities": "0.0000000",
                "selling_liabilities": "0.0000000"
            }
        ],
        "signers": [
            {
                "key": "GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7",
                "type": "ed25519_public_key",
                "weight": 1
            }
        ],
        "data": {
            "config": "dGVzdA=="
        },
        "num_sponsoring": 0,
        "num_sponsored": 0,
        "paging_token": "GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7",
        "_links": {
            "self": {"href": "https://horizon.stellar.org/accounts/GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7"},
            "transactions": {"href": "https://horizon.stellar.org/accounts/GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7/transactions{?cursor,limit,order}", "templated": true},
            "operations": {"href": "https://horizon.stellar.org/accounts/GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7/operations{?cursor,limit,order}", "templated": true},
            "payments": {"href": "https://horizon.stellar.org/accounts/GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7/payments{?cursor,limit,order}", "templated": true},
            "effects": {"href": "https://horizon.stellar.org/accounts/GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7/effects{?cursor,limit,order}", "templated": true},
            "offers": {"href": "https://horizon.stellar.org/accounts/GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7/offers{?cursor,limit,order}", "templated": true},
            "trades": {"href": "https://horizon.stellar.org/accounts/GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7/trades{?cursor,limit,order}", "templated": true},
            "data": {"href": "https://horizon.stellar.org/accounts/GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7/data/{key}", "templated": true}
        }
    }
    """.trimIndent()

    @Test
    fun testDeserialization() {
        val account = json.decodeFromString<AccountResponse>(accountJson)
        assertEquals("GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7", account.id)
        assertEquals("GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7", account.accountId)
        assertEquals(3298702387052545L, account.sequenceNumber)
        assertEquals(7654321L, account.sequenceLedger)
        assertEquals(1609459200L, account.sequenceTime)
        assertEquals(1, account.subentryCount)
        assertEquals("GBVFTZL5HIPT4PFQVTZVIWR77V7LWYCXU4CLYWWHHOEXB64XPG5LDMTU", account.inflationDestination)
        assertEquals("example.com", account.homeDomain)
        assertEquals(7654321, account.lastModifiedLedger)
        assertEquals("2021-01-01T00:00:00Z", account.lastModifiedTime)
        assertEquals(0, account.numSponsoring)
        assertEquals(0, account.numSponsored)
        assertNull(account.sponsor)
    }

    @Test
    fun testThresholds() {
        val account = json.decodeFromString<AccountResponse>(accountJson)
        assertEquals(0, account.thresholds.lowThreshold)
        assertEquals(0, account.thresholds.medThreshold)
        assertEquals(0, account.thresholds.highThreshold)
    }

    @Test
    fun testFlags() {
        val account = json.decodeFromString<AccountResponse>(accountJson)
        assertFalse(account.flags.authRequired)
        assertFalse(account.flags.authRevocable)
        assertFalse(account.flags.authImmutable)
        assertFalse(account.flags.authClawbackEnabled)
    }

    @Test
    fun testBalances() {
        val account = json.decodeFromString<AccountResponse>(accountJson)
        assertEquals(2, account.balances.size)

        val creditBalance = account.balances[0]
        assertEquals("credit_alphanum4", creditBalance.assetType)
        assertEquals("USD", creditBalance.assetCode)
        assertEquals("GCDNJUBQSX7AJWLJACMJ7I4BC3Z47BQUTMHEICZLE6MU4KQBRYG5JY6B", creditBalance.assetIssuer)
        assertEquals("100.0000000", creditBalance.balance)
        assertEquals("0.0000000", creditBalance.buyingLiabilities)
        assertEquals("0.0000000", creditBalance.sellingLiabilities)
        assertEquals(true, creditBalance.isAuthorized)
        assertEquals(true, creditBalance.isAuthorizedToMaintainLiabilities)
        assertEquals(false, creditBalance.isClawbackEnabled)
        assertEquals(7654320, creditBalance.lastModifiedLedger)

        val nativeBalance = account.balances[1]
        assertEquals("native", nativeBalance.assetType)
        assertNull(nativeBalance.assetCode)
        assertNull(nativeBalance.assetIssuer)
        assertEquals("999.9999900", nativeBalance.balance)
    }

    @Test
    fun testSigners() {
        val account = json.decodeFromString<AccountResponse>(accountJson)
        assertEquals(1, account.signers.size)
        assertEquals("GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7", account.signers[0].key)
        assertEquals("ed25519_public_key", account.signers[0].type)
        assertEquals(1, account.signers[0].weight)
        assertNull(account.signers[0].sponsor)
    }

    @Test
    fun testData() {
        val account = json.decodeFromString<AccountResponse>(accountJson)
        assertEquals(1, account.data.size)
        assertEquals("dGVzdA==", account.data["config"])
    }

    @Test
    fun testPagingToken() {
        val account = json.decodeFromString<AccountResponse>(accountJson)
        assertEquals("GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7", account.pagingToken)
    }

    @Test
    fun testLinks() {
        val account = json.decodeFromString<AccountResponse>(accountJson)
        assertTrue(account.links.self.href.contains("accounts/GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7"))
        assertTrue(account.links.transactions.templated == true)
    }

    @Test
    fun testGetIncrementedSequenceNumber() {
        val account = json.decodeFromString<AccountResponse>(accountJson)
        assertEquals(3298702387052546L, account.getIncrementedSequenceNumber())
    }

    @Test
    fun testMinimalAccountDeserialization() {
        val minimalJson = """
        {
            "id": "GTEST",
            "account_id": "GTEST",
            "sequence": 100,
            "subentry_count": 0,
            "last_modified_ledger": 1,
            "last_modified_time": "2021-01-01T00:00:00Z",
            "thresholds": {"low_threshold": 1, "med_threshold": 2, "high_threshold": 3},
            "flags": {"auth_required": true, "auth_revocable": true, "auth_immutable": false, "auth_clawback_enabled": true},
            "balances": [],
            "signers": [],
            "paging_token": "GTEST",
            "_links": {
                "self": {"href": "https://example.com/self"},
                "transactions": {"href": "https://example.com/tx"},
                "operations": {"href": "https://example.com/ops"},
                "payments": {"href": "https://example.com/pay"},
                "effects": {"href": "https://example.com/fx"},
                "offers": {"href": "https://example.com/offers"},
                "trades": {"href": "https://example.com/trades"},
                "data": {"href": "https://example.com/data"}
            }
        }
        """.trimIndent()
        val account = json.decodeFromString<AccountResponse>(minimalJson)
        assertEquals("GTEST", account.id)
        assertEquals(100L, account.sequenceNumber)
        assertNull(account.sequenceLedger)
        assertNull(account.sequenceTime)
        assertNull(account.inflationDestination)
        assertNull(account.homeDomain)
        assertTrue(account.balances.isEmpty())
        assertTrue(account.signers.isEmpty())
        assertTrue(account.data.isEmpty())
        assertTrue(account.flags.authRequired)
        assertTrue(account.flags.authRevocable)
        assertTrue(account.flags.authClawbackEnabled)
        assertEquals(1, account.thresholds.lowThreshold)
        assertEquals(2, account.thresholds.medThreshold)
        assertEquals(3, account.thresholds.highThreshold)
    }

    @Test
    fun testAccountResponseAllInnerClassesFullCoverage() {
        val accountResponseJson = """
        {
            "id": "GAIRISXKPLOWZBMFRPU5XJJMTOYEOAOHXGPXFC3BNR3TGIEYG3Q6VCDK",
            "account_id": "GAIRISXKPLOWZBMFRPU5XJJMTOYEOAOHXGPXFC3BNR3TGIEYG3Q6VCDK",
            "sequence": "12884901891",
            "sequence_ledger": 43466843,
            "sequence_time": 1684157425,
            "subentry_count": 5,
            "inflation_destination": "GDCHDRSDOBRMSUDKRE2C4U4KDLNEATJPIHHR2ORFL5BSD56G4DQXL4VW",
            "home_domain": "stellar.org", 
            "last_modified_ledger": 43466843,
            "last_modified_time": "2023-05-15T14:30:25Z",
            "thresholds": {
                "low_threshold": 1,
                "med_threshold": 2,
                "high_threshold": 3
            },
            "flags": {
                "auth_required": true,
                "auth_revocable": false,
                "auth_immutable": true,
                "auth_clawback_enabled": false
            },
            "balances": [
                {
                    "asset_type": "native",
                    "balance": "9999.9999000",
                    "buying_liabilities": "100.0000000",
                    "selling_liabilities": "200.0000000"
                },
                {
                    "asset_type": "credit_alphanum4",
                    "asset_code": "USD",
                    "asset_issuer": "GACKTN5DAZGWXRWB2WLM6OPBDHAMT6SJNGLJZPQMEZBUR4JUGBX2UK7V",
                    "limit": "10000.0000000",
                    "balance": "5000.0000000",
                    "buying_liabilities": "50.0000000",
                    "selling_liabilities": "75.0000000",
                    "is_authorized": true,
                    "is_authorized_to_maintain_liabilities": true,
                    "is_clawback_enabled": false,
                    "last_modified_ledger": 43466840,
                    "sponsor": "GSPONSORRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRR"
                },
                {
                    "asset_type": "credit_alphanum12",
                    "asset_code": "LONGASSET",
                    "asset_issuer": "GCKFBEIYTKP5RDHGA7BLEQGESGZ6Q6FE2MLJM5Q5P4LGWGDHTPBZQKSV",
                    "limit": "1000000.0000000",
                    "balance": "250000.0000000",
                    "buying_liabilities": "1000.0000000", 
                    "selling_liabilities": "2000.0000000",
                    "is_authorized": false,
                    "is_authorized_to_maintain_liabilities": false,
                    "is_clawback_enabled": true,
                    "last_modified_ledger": 43466841
                },
                {
                    "asset_type": "liquidity_pool_shares",
                    "liquidity_pool_id": "abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890",
                    "limit": "1000.0000000",
                    "balance": "500.0000000",
                    "last_modified_ledger": 43466842,
                    "sponsor": "GLIQUIDITYPOOL123456789012345678901234567890"
                }
            ],
            "signers": [
                {
                    "key": "GAIRISXKPLOWZBMFRPU5XJJMTOYEOAOHXGPXFC3BNR3TGIEYG3Q6VCDK",
                    "type": "ed25519_public_key",
                    "weight": 1,
                    "sponsor": "GSIGNERONEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEE"
                },
                {
                    "key": "XRPKEY1234567890ABCDEFGHIJKLMNOPQRSTUVWXYZ",
                    "type": "sha256_hash",
                    "weight": 2
                },
                {
                    "key": "TXKEY9876543210ZYXWVUTSRQPONMLKJIHGFEDCBA", 
                    "type": "preauth_tx",
                    "weight": 3,
                    "sponsor": "GSIGNERTWO1111111111111111111111111111111"
                }
            ],
            "data": {
                "config": "dGVzdCBjb25maWc=",
                "settings": "c2V0dGluZ3M="
            },
            "num_sponsoring": 10,
            "num_sponsored": 5,
            "sponsor": "GMASTERSPONSORRRRRRRRRRRRRRRRRRRRRRRRRRRRR",
            "paging_token": "GAIRISXKPLOWZBMFRPU5XJJMTOYEOAOHXGPXFC3BNR3TGIEYG3Q6VCDK",
            "_links": {
                "self": {"href": "https://horizon.stellar.org/accounts/GAIRISXKPLOWZBMFRPU5XJJMTOYEOAOHXGPXFC3BNR3TGIEYG3Q6VCDK"},
                "transactions": {"href": "https://horizon.stellar.org/accounts/GAIRISXKPLOWZBMFRPU5XJJMTOYEOAOHXGPXFC3BNR3TGIEYG3Q6VCDK/transactions"},
                "operations": {"href": "https://horizon.stellar.org/accounts/GAIRISXKPLOWZBMFRPU5XJJMTOYEOAOHXGPXFC3BNR3TGIEYG3Q6VCDK/operations"},
                "payments": {"href": "https://horizon.stellar.org/accounts/GAIRISXKPLOWZBMFRPU5XJJMTOYEOAOHXGPXFC3BNR3TGIEYG3Q6VCDK/payments"},
                "effects": {"href": "https://horizon.stellar.org/accounts/GAIRISXKPLOWZBMFRPU5XJJMTOYEOAOHXGPXFC3BNR3TGIEYG3Q6VCDK/effects"},
                "offers": {"href": "https://horizon.stellar.org/accounts/GAIRISXKPLOWZBMFRPU5XJJMTOYEOAOHXGPXFC3BNR3TGIEYG3Q6VCDK/offers"},
                "trades": {"href": "https://horizon.stellar.org/accounts/GAIRISXKPLOWZBMFRPU5XJJMTOYEOAOHXGPXFC3BNR3TGIEYG3Q6VCDK/trades"},
                "data": {"href": "https://horizon.stellar.org/accounts/GAIRISXKPLOWZBMFRPU5XJJMTOYEOAOHXGPXFC3BNR3TGIEYG3Q6VCDK/data/{key}"}
            }
        }
        """.trimIndent()

        val account = json.decodeFromString<AccountResponse>(accountResponseJson)

        assertEquals("GAIRISXKPLOWZBMFRPU5XJJMTOYEOAOHXGPXFC3BNR3TGIEYG3Q6VCDK", account.id)
        assertEquals("GAIRISXKPLOWZBMFRPU5XJJMTOYEOAOHXGPXFC3BNR3TGIEYG3Q6VCDK", account.accountId)
        assertEquals(12884901891L, account.sequenceNumber)
        assertEquals(43466843L, account.sequenceLedger)
        assertEquals(1684157425L, account.sequenceTime)
        assertEquals(5, account.subentryCount)
        assertEquals("GDCHDRSDOBRMSUDKRE2C4U4KDLNEATJPIHHR2ORFL5BSD56G4DQXL4VW", account.inflationDestination)
        assertEquals("stellar.org", account.homeDomain)
        assertEquals(43466843, account.lastModifiedLedger)
        assertEquals("2023-05-15T14:30:25Z", account.lastModifiedTime)
        assertEquals(10, account.numSponsoring)
        assertEquals(5, account.numSponsored)
        assertEquals("GMASTERSPONSORRRRRRRRRRRRRRRRRRRRRRRRRRRRR", account.sponsor)
        assertEquals("GAIRISXKPLOWZBMFRPU5XJJMTOYEOAOHXGPXFC3BNR3TGIEYG3Q6VCDK", account.pagingToken)
        assertEquals(2, account.data.size)
        assertEquals("dGVzdCBjb25maWc=", account.data["config"])
        assertEquals("c2V0dGluZ3M=", account.data["settings"])

        assertEquals(12884901892L, account.getIncrementedSequenceNumber())

        val thresholds = account.thresholds
        assertNotNull(thresholds)
        assertEquals(1, thresholds.lowThreshold)
        assertEquals(2, thresholds.medThreshold)
        assertEquals(3, thresholds.highThreshold)

        val flags = account.flags
        assertNotNull(flags)
        assertTrue(flags.authRequired)
        assertFalse(flags.authRevocable)
        assertTrue(flags.authImmutable)
        assertFalse(flags.authClawbackEnabled)

        val balances = account.balances
        assertEquals(4, balances.size)

        val nativeBalance = balances[0]
        assertEquals("native", nativeBalance.assetType)
        assertNull(nativeBalance.assetCode)
        assertNull(nativeBalance.assetIssuer)
        assertNull(nativeBalance.liquidityPoolId)
        assertNull(nativeBalance.limit)
        assertEquals("9999.9999000", nativeBalance.balance)
        assertEquals("100.0000000", nativeBalance.buyingLiabilities)
        assertEquals("200.0000000", nativeBalance.sellingLiabilities)
        assertNull(nativeBalance.isAuthorized)
        assertNull(nativeBalance.isAuthorizedToMaintainLiabilities)
        assertNull(nativeBalance.isClawbackEnabled)
        assertNull(nativeBalance.lastModifiedLedger)
        assertNull(nativeBalance.sponsor)

        val usdBalance = balances[1]
        assertEquals("credit_alphanum4", usdBalance.assetType)
        assertEquals("USD", usdBalance.assetCode)
        assertEquals("GACKTN5DAZGWXRWB2WLM6OPBDHAMT6SJNGLJZPQMEZBUR4JUGBX2UK7V", usdBalance.assetIssuer)
        assertNull(usdBalance.liquidityPoolId)
        assertEquals("10000.0000000", usdBalance.limit)
        assertEquals("5000.0000000", usdBalance.balance)
        assertEquals("50.0000000", usdBalance.buyingLiabilities)
        assertEquals("75.0000000", usdBalance.sellingLiabilities)
        assertEquals(true, usdBalance.isAuthorized)
        assertEquals(true, usdBalance.isAuthorizedToMaintainLiabilities)
        assertEquals(false, usdBalance.isClawbackEnabled)
        assertEquals(43466840, usdBalance.lastModifiedLedger)
        assertEquals("GSPONSORRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRR", usdBalance.sponsor)

        val longAssetBalance = balances[2]
        assertEquals("credit_alphanum12", longAssetBalance.assetType)
        assertEquals("LONGASSET", longAssetBalance.assetCode)
        assertEquals("GCKFBEIYTKP5RDHGA7BLEQGESGZ6Q6FE2MLJM5Q5P4LGWGDHTPBZQKSV", longAssetBalance.assetIssuer)
        assertNull(longAssetBalance.liquidityPoolId)
        assertEquals("1000000.0000000", longAssetBalance.limit)
        assertEquals("250000.0000000", longAssetBalance.balance)
        assertEquals("1000.0000000", longAssetBalance.buyingLiabilities)
        assertEquals("2000.0000000", longAssetBalance.sellingLiabilities)
        assertEquals(false, longAssetBalance.isAuthorized)
        assertEquals(false, longAssetBalance.isAuthorizedToMaintainLiabilities)
        assertEquals(true, longAssetBalance.isClawbackEnabled)
        assertEquals(43466841, longAssetBalance.lastModifiedLedger)
        assertNull(longAssetBalance.sponsor)

        val poolBalance = balances[3]
        assertEquals("liquidity_pool_shares", poolBalance.assetType)
        assertNull(poolBalance.assetCode)
        assertNull(poolBalance.assetIssuer)
        assertEquals("abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890", poolBalance.liquidityPoolId)
        assertEquals("1000.0000000", poolBalance.limit)
        assertEquals("500.0000000", poolBalance.balance)
        assertNull(poolBalance.buyingLiabilities)
        assertNull(poolBalance.sellingLiabilities)
        assertNull(poolBalance.isAuthorized)
        assertNull(poolBalance.isAuthorizedToMaintainLiabilities)
        assertNull(poolBalance.isClawbackEnabled)
        assertEquals(43466842, poolBalance.lastModifiedLedger)
        assertEquals("GLIQUIDITYPOOL123456789012345678901234567890", poolBalance.sponsor)

        val signers = account.signers
        assertEquals(3, signers.size)

        val signer1 = signers[0]
        assertEquals("GAIRISXKPLOWZBMFRPU5XJJMTOYEOAOHXGPXFC3BNR3TGIEYG3Q6VCDK", signer1.key)
        assertEquals("ed25519_public_key", signer1.type)
        assertEquals(1, signer1.weight)
        assertEquals("GSIGNERONEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEE", signer1.sponsor)

        val signer2 = signers[1]
        assertEquals("XRPKEY1234567890ABCDEFGHIJKLMNOPQRSTUVWXYZ", signer2.key)
        assertEquals("sha256_hash", signer2.type)
        assertEquals(2, signer2.weight)
        assertNull(signer2.sponsor)

        val signer3 = signers[2]
        assertEquals("TXKEY9876543210ZYXWVUTSRQPONMLKJIHGFEDCBA", signer3.key)
        assertEquals("preauth_tx", signer3.type)
        assertEquals(3, signer3.weight)
        assertEquals("GSIGNERTWO1111111111111111111111111111111", signer3.sponsor)

        val links = account.links
        assertNotNull(links)
        assertEquals("https://horizon.stellar.org/accounts/GAIRISXKPLOWZBMFRPU5XJJMTOYEOAOHXGPXFC3BNR3TGIEYG3Q6VCDK", links.self.href)
        assertEquals("https://horizon.stellar.org/accounts/GAIRISXKPLOWZBMFRPU5XJJMTOYEOAOHXGPXFC3BNR3TGIEYG3Q6VCDK/transactions", links.transactions.href)
        assertEquals("https://horizon.stellar.org/accounts/GAIRISXKPLOWZBMFRPU5XJJMTOYEOAOHXGPXFC3BNR3TGIEYG3Q6VCDK/operations", links.operations.href)
        assertEquals("https://horizon.stellar.org/accounts/GAIRISXKPLOWZBMFRPU5XJJMTOYEOAOHXGPXFC3BNR3TGIEYG3Q6VCDK/payments", links.payments.href)
        assertEquals("https://horizon.stellar.org/accounts/GAIRISXKPLOWZBMFRPU5XJJMTOYEOAOHXGPXFC3BNR3TGIEYG3Q6VCDK/effects", links.effects.href)
        assertEquals("https://horizon.stellar.org/accounts/GAIRISXKPLOWZBMFRPU5XJJMTOYEOAOHXGPXFC3BNR3TGIEYG3Q6VCDK/offers", links.offers.href)
        assertEquals("https://horizon.stellar.org/accounts/GAIRISXKPLOWZBMFRPU5XJJMTOYEOAOHXGPXFC3BNR3TGIEYG3Q6VCDK/trades", links.trades.href)
        assertEquals("https://horizon.stellar.org/accounts/GAIRISXKPLOWZBMFRPU5XJJMTOYEOAOHXGPXFC3BNR3TGIEYG3Q6VCDK/data/{key}", links.data.href)
    }

    @Test
    fun testAccountResponseWithNullableFields() {
        val minimalAccountJson = """
        {
            "id": "GMINIMAL123456789012345678901234567890123456",
            "account_id": "GMINIMAL123456789012345678901234567890123456",
            "sequence": "100",
            "subentry_count": 0,
            "last_modified_ledger": 1000,
            "last_modified_time": "2022-01-01T00:00:00Z",
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
                    "balance": "1000.0000000"
                }
            ],
            "signers": [
                {
                    "key": "GMINIMAL123456789012345678901234567890123456",
                    "type": "ed25519_public_key",
                    "weight": 1
                }
            ],
            "data": {},
            "paging_token": "minimal",
            "_links": {
                "self": {"href": "self"},
                "transactions": {"href": "tx"},
                "operations": {"href": "ops"},
                "payments": {"href": "payments"},
                "effects": {"href": "effects"},
                "offers": {"href": "offers"},
                "trades": {"href": "trades"},
                "data": {"href": "data"}
            }
        }
        """.trimIndent()

        val account = json.decodeFromString<AccountResponse>(minimalAccountJson)

        assertEquals("GMINIMAL123456789012345678901234567890123456", account.id)
        assertEquals(100L, account.sequenceNumber)
        assertEquals(101L, account.getIncrementedSequenceNumber())
        assertEquals(0, account.subentryCount)

        assertNull(account.sequenceLedger)
        assertNull(account.sequenceTime)
        assertNull(account.inflationDestination)
        assertNull(account.homeDomain)
        assertNull(account.numSponsoring)
        assertNull(account.numSponsored)
        assertNull(account.sponsor)

        val balance = account.balances[0]
        assertEquals("native", balance.assetType)
        assertEquals("1000.0000000", balance.balance)
        assertNull(balance.assetCode)
        assertNull(balance.assetIssuer)
        assertNull(balance.limit)
        assertNull(balance.buyingLiabilities)
        assertNull(balance.sellingLiabilities)
        assertNull(balance.isAuthorized)

        val signer = account.signers[0]
        assertEquals("GMINIMAL123456789012345678901234567890123456", signer.key)
        assertEquals("ed25519_public_key", signer.type)
        assertEquals(1, signer.weight)
        assertNull(signer.sponsor)

        assertTrue(account.data.isEmpty())
    }

    @Test
    fun testDirectInnerClassesConstruction() {
        val thresholds = AccountResponse.Thresholds(
            lowThreshold = 5,
            medThreshold = 10,
            highThreshold = 15
        )
        assertEquals(5, thresholds.lowThreshold)
        assertEquals(10, thresholds.medThreshold)
        assertEquals(15, thresholds.highThreshold)

        val flags = AccountResponse.Flags(
            authRequired = true,
            authRevocable = false,
            authImmutable = true,
            authClawbackEnabled = false
        )
        assertTrue(flags.authRequired)
        assertFalse(flags.authRevocable)
        assertTrue(flags.authImmutable)
        assertFalse(flags.authClawbackEnabled)

        val balance = AccountResponse.Balance(
            assetType = "credit_alphanum4",
            assetCode = "EUR",
            assetIssuer = "GTEST123",
            liquidityPoolId = null,
            limit = "50000.0000000",
            balance = "25000.0000000",
            buyingLiabilities = "100.0000000",
            sellingLiabilities = "200.0000000",
            isAuthorized = true,
            isAuthorizedToMaintainLiabilities = false,
            isClawbackEnabled = true,
            lastModifiedLedger = 12345,
            sponsor = "GSPONSOR123"
        )
        assertEquals("credit_alphanum4", balance.assetType)
        assertEquals("EUR", balance.assetCode)
        assertEquals("GTEST123", balance.assetIssuer)
        assertEquals("50000.0000000", balance.limit)
        assertEquals("25000.0000000", balance.balance)
        assertEquals("100.0000000", balance.buyingLiabilities)
        assertEquals("200.0000000", balance.sellingLiabilities)
        assertTrue(balance.isAuthorized!!)
        assertFalse(balance.isAuthorizedToMaintainLiabilities!!)
        assertTrue(balance.isClawbackEnabled!!)
        assertEquals(12345, balance.lastModifiedLedger)
        assertEquals("GSPONSOR123", balance.sponsor)

        val signer = AccountResponse.Signer(
            key = "GSIGNER123456789012345678901234567890123456",
            type = "ed25519_public_key",
            weight = 5,
            sponsor = "GSPONSOR456"
        )
        assertEquals("GSIGNER123456789012345678901234567890123456", signer.key)
        assertEquals("ed25519_public_key", signer.type)
        assertEquals(5, signer.weight)
        assertEquals("GSPONSOR456", signer.sponsor)

        val links = AccountResponse.Links(
            self = Link("https://self.com"),
            transactions = Link("https://transactions.com"),
            operations = Link("https://operations.com"),
            payments = Link("https://payments.com"),
            effects = Link("https://effects.com"),
            offers = Link("https://offers.com"),
            trades = Link("https://trades.com"),
            data = Link("https://data.com")
        )
        assertEquals("https://self.com", links.self.href)
        assertEquals("https://transactions.com", links.transactions.href)
        assertEquals("https://operations.com", links.operations.href)
        assertEquals("https://payments.com", links.payments.href)
        assertEquals("https://effects.com", links.effects.href)
        assertEquals("https://offers.com", links.offers.href)
        assertEquals("https://trades.com", links.trades.href)
        assertEquals("https://data.com", links.data.href)
    }

    @Test
    fun testGetIncrementedSequenceNumberFunction() {
        val account1Json = """
        {
            "id": "GTEST1",
            "account_id": "GTEST1",
            "sequence": "0",
            "subentry_count": 0,
            "last_modified_ledger": 1,
            "last_modified_time": "2022-01-01T00:00:00Z",
            "thresholds": {"low_threshold": 0, "med_threshold": 0, "high_threshold": 0},
            "flags": {"auth_required": false, "auth_revocable": false, "auth_immutable": false, "auth_clawback_enabled": false},
            "balances": [{"asset_type": "native", "balance": "0.0000000"}],
            "signers": [{"key": "GTEST1", "type": "ed25519_public_key", "weight": 1}],
            "data": {},
            "paging_token": "test1",
            "_links": {
                "self": {"href": "self"}, "transactions": {"href": "tx"}, "operations": {"href": "ops"},
                "payments": {"href": "payments"}, "effects": {"href": "effects"}, "offers": {"href": "offers"},
                "trades": {"href": "trades"}, "data": {"href": "data"}
            }
        }
        """.trimIndent()

        val account1 = json.decodeFromString<AccountResponse>(account1Json)
        assertEquals(0L, account1.sequenceNumber)
        assertEquals(1L, account1.getIncrementedSequenceNumber())

        val account2Json = account1Json.replace("\"sequence\": \"0\"", "\"sequence\": \"9223372036854775806\"")
        val account2 = json.decodeFromString<AccountResponse>(account2Json)
        assertEquals(9223372036854775806L, account2.sequenceNumber)
        assertEquals(9223372036854775807L, account2.getIncrementedSequenceNumber())

        val account3Json = account1Json.replace("\"sequence\": \"0\"", "\"sequence\": \"999999999\"")
        val account3 = json.decodeFromString<AccountResponse>(account3Json)
        assertEquals(999999999L, account3.sequenceNumber)
        assertEquals(1000000000L, account3.getIncrementedSequenceNumber())
    }

    @Test
    fun testComplexBalanceTypes() {
        val complexBalancesJson = """
        {
            "id": "GCOMPLEX",
            "account_id": "GCOMPLEX",
            "sequence": "100",
            "subentry_count": 10,
            "last_modified_ledger": 1000,
            "last_modified_time": "2022-01-01T00:00:00Z",
            "thresholds": {"low_threshold": 1, "med_threshold": 1, "high_threshold": 1},
            "flags": {"auth_required": false, "auth_revocable": false, "auth_immutable": false, "auth_clawback_enabled": false},
            "balances": [
                {
                    "asset_type": "native",
                    "balance": "0.0000000",
                    "buying_liabilities": "0.0000000",
                    "selling_liabilities": "0.0000000"
                },
                {
                    "asset_type": "credit_alphanum4",
                    "asset_code": "TEST", 
                    "asset_issuer": "GTESTISSUER",
                    "balance": "0.0000000",
                    "limit": "0.0000000",
                    "buying_liabilities": "0.0000000",
                    "selling_liabilities": "0.0000000",
                    "is_authorized": null,
                    "is_authorized_to_maintain_liabilities": null,
                    "is_clawback_enabled": null
                },
                {
                    "asset_type": "liquidity_pool_shares",
                    "liquidity_pool_id": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                    "balance": "0.0000000",
                    "limit": "922337203685.4775807"
                }
            ],
            "signers": [{"key": "GCOMPLEX", "type": "ed25519_public_key", "weight": 1}],
            "data": {},
            "paging_token": "complex",
            "_links": {
                "self": {"href": "self"}, "transactions": {"href": "tx"}, "operations": {"href": "ops"},
                "payments": {"href": "payments"}, "effects": {"href": "effects"}, "offers": {"href": "offers"},
                "trades": {"href": "trades"}, "data": {"href": "data"}
            }
        }
        """.trimIndent()

        val account = json.decodeFromString<AccountResponse>(complexBalancesJson)

        val nativeBalance = account.balances[0]
        assertEquals("0.0000000", nativeBalance.balance)
        assertEquals("0.0000000", nativeBalance.buyingLiabilities)
        assertEquals("0.0000000", nativeBalance.sellingLiabilities)

        val creditBalance = account.balances[1]
        assertEquals("TEST", creditBalance.assetCode)
        assertEquals("GTESTISSUER", creditBalance.assetIssuer)
        assertEquals("0.0000000", creditBalance.limit)
        assertNull(creditBalance.isAuthorized)
        assertNull(creditBalance.isAuthorizedToMaintainLiabilities)
        assertNull(creditBalance.isClawbackEnabled)

        val poolBalance = account.balances[2]
        assertEquals("liquidity_pool_shares", poolBalance.assetType)
        assertEquals("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef", poolBalance.liquidityPoolId)
        assertEquals("922337203685.4775807", poolBalance.limit)
        assertNull(poolBalance.assetCode)
        assertNull(poolBalance.assetIssuer)
    }

    @Test
    fun testMultipleSignerTypes() {
        val multiSignerJson = """
        {
            "id": "GMULTISIG",
            "account_id": "GMULTISIG",
            "sequence": "500",
            "subentry_count": 3,
            "last_modified_ledger": 2000,
            "last_modified_time": "2023-01-01T00:00:00Z",
            "thresholds": {"low_threshold": 2, "med_threshold": 4, "high_threshold": 6},
            "flags": {"auth_required": true, "auth_revocable": true, "auth_immutable": false, "auth_clawback_enabled": true},
            "balances": [{"asset_type": "native", "balance": "5000.0000000"}],
            "signers": [
                {
                    "key": "GMULTISIG",
                    "type": "ed25519_public_key", 
                    "weight": 2
                },
                {
                    "key": "TBA4ZUBKYSNGWQGIFQNQQLMJ4WMY3COXKJ73RTC7GJD2YMV3HNJ4EGGW",
                    "type": "sha256_hash",
                    "weight": 1
                },
                {
                    "key": "TBBW5FIKPZ2EBR7LGQVZLXKX3DXAMQTDQHHGIFX3T32KFI2GKY6QWPB2",
                    "type": "preauth_tx",
                    "weight": 3
                }
            ],
            "data": {
                "name": "TXVsdGktc2lnIEFjY291bnQ="
            },
            "paging_token": "multisig",
            "_links": {
                "self": {"href": "self"}, "transactions": {"href": "tx"}, "operations": {"href": "ops"},
                "payments": {"href": "payments"}, "effects": {"href": "effects"}, "offers": {"href": "offers"},
                "trades": {"href": "trades"}, "data": {"href": "data"}
            }
        }
        """.trimIndent()

        val account = json.decodeFromString<AccountResponse>(multiSignerJson)
        assertEquals(501L, account.getIncrementedSequenceNumber())

        assertEquals(2, account.thresholds.lowThreshold)
        assertEquals(4, account.thresholds.medThreshold)
        assertEquals(6, account.thresholds.highThreshold)

        assertTrue(account.flags.authRequired)
        assertTrue(account.flags.authRevocable)
        assertFalse(account.flags.authImmutable)
        assertTrue(account.flags.authClawbackEnabled)

        assertEquals(3, account.signers.size)
        assertEquals("ed25519_public_key", account.signers[0].type)
        assertEquals("sha256_hash", account.signers[1].type)
        assertEquals("preauth_tx", account.signers[2].type)
        assertEquals(2, account.signers[0].weight)
        assertEquals(1, account.signers[1].weight)
        assertEquals(3, account.signers[2].weight)

        assertEquals(1, account.data.size)
        assertEquals("TXVsdGktc2lnIEFjY291bnQ=", account.data["name"])
    }
}
