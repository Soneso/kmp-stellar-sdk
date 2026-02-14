package com.soneso.stellar.sdk.unitTests.horizon.responses

import com.soneso.stellar.sdk.horizon.responses.AssetResponse
import com.soneso.stellar.sdk.horizon.responses.Link
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AssetResponseTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val assetJson = """
    {
        "asset_type": "credit_alphanum4",
        "asset_code": "USD",
        "asset_issuer": "GCDNJUBQSX7AJWLJACMJ7I4BC3Z47BQUTMHEICZLE6MU4KQBRYG5JY6B",
        "paging_token": "USD_GCDNJUBQSX7AJWLJACMJ7I4BC3Z47BQUTMHEICZLE6MU4KQBRYG5JY6B_credit_alphanum4",
        "contract_id": "a1b2c3d4e5f6",
        "num_claimable_balances": 5,
        "num_liquidity_pools": 3,
        "num_contracts": 2,
        "accounts": {
            "authorized": 1000,
            "authorized_to_maintain_liabilities": 50,
            "unauthorized": 10
        },
        "claimable_balances_amount": "500.0000000",
        "liquidity_pools_amount": "1000.0000000",
        "contracts_amount": "250.0000000",
        "balances": {
            "authorized": "5000000.0000000",
            "authorized_to_maintain_liabilities": "100000.0000000",
            "unauthorized": "5000.0000000"
        },
        "flags": {
            "auth_required": true,
            "auth_revocable": true,
            "auth_immutable": false,
            "auth_clawback_enabled": false
        },
        "_links": {
            "toml": {"href": "https://example.com/.well-known/stellar.toml"}
        }
    }
    """.trimIndent()

    @Test
    fun testDeserialization() {
        val asset = json.decodeFromString<AssetResponse>(assetJson)
        assertEquals("credit_alphanum4", asset.assetType)
        assertEquals("USD", asset.assetCode)
        assertEquals("GCDNJUBQSX7AJWLJACMJ7I4BC3Z47BQUTMHEICZLE6MU4KQBRYG5JY6B", asset.assetIssuer)
        assertEquals("USD_GCDNJUBQSX7AJWLJACMJ7I4BC3Z47BQUTMHEICZLE6MU4KQBRYG5JY6B_credit_alphanum4", asset.pagingToken)
        assertEquals("a1b2c3d4e5f6", asset.contractId)
        assertEquals(5, asset.numClaimableBalances)
        assertEquals(3, asset.numLiquidityPools)
        assertEquals(2, asset.numContracts)
        assertEquals("500.0000000", asset.claimableBalancesAmount)
        assertEquals("1000.0000000", asset.liquidityPoolsAmount)
        assertEquals("250.0000000", asset.contractsAmount)
    }

    @Test
    fun testAccounts() {
        val asset = json.decodeFromString<AssetResponse>(assetJson)
        assertEquals(1000, asset.accounts.authorized)
        assertEquals(50, asset.accounts.authorizedToMaintainLiabilities)
        assertEquals(10, asset.accounts.unauthorized)
    }

    @Test
    fun testBalances() {
        val asset = json.decodeFromString<AssetResponse>(assetJson)
        assertEquals("5000000.0000000", asset.balances.authorized)
        assertEquals("100000.0000000", asset.balances.authorizedToMaintainLiabilities)
        assertEquals("5000.0000000", asset.balances.unauthorized)
    }

    @Test
    fun testFlags() {
        val asset = json.decodeFromString<AssetResponse>(assetJson)
        assertTrue(asset.flags.authRequired)
        assertTrue(asset.flags.authRevocable)
        assertFalse(asset.flags.authImmutable)
        assertFalse(asset.flags.authClawbackEnabled)
    }

    @Test
    fun testLinks() {
        val asset = json.decodeFromString<AssetResponse>(assetJson)
        assertEquals("https://example.com/.well-known/stellar.toml", asset.links.toml.href)
    }

    @Test
    fun testMinimalDeserialization() {
        val minimalJson = """
        {
            "asset_type": "credit_alphanum12",
            "asset_code": "LONGASSET",
            "asset_issuer": "GTEST",
            "paging_token": "token123",
            "accounts": {"authorized": 0, "authorized_to_maintain_liabilities": 0, "unauthorized": 0},
            "balances": {"authorized": "0", "authorized_to_maintain_liabilities": "0", "unauthorized": "0"},
            "flags": {"auth_required": false, "auth_revocable": false, "auth_immutable": false, "auth_clawback_enabled": false},
            "_links": {"toml": {"href": "https://example.com/toml"}}
        }
        """.trimIndent()
        val asset = json.decodeFromString<AssetResponse>(minimalJson)
        assertEquals("credit_alphanum12", asset.assetType)
        assertEquals("LONGASSET", asset.assetCode)
        assertNull(asset.contractId)
        assertNull(asset.numClaimableBalances)
        assertNull(asset.numLiquidityPools)
        assertNull(asset.numContracts)
        assertNull(asset.claimableBalancesAmount)
    }

    @Test
    fun testComprehensiveAssetResponseDeserialization() {
        val comprehensiveAssetJson = """
        {
            "asset_type": "credit_alphanum4",
            "asset_code": "USD",
            "asset_issuer": "GCDNJUBQSX7AJWLJACMJ7I4BC3Z47BQUTMHEICZLE6MU4KQBRYG5JY6B",
            "paging_token": "USD_GCDNJUBQSX7AJWLJACMJ7I4BC3Z47BQUTMHEICZLE6MU4KQBRYG5JY6B_credit_alphanum4",
            "contract_id": "CACCL47PRYOGWNHPGJ5ZDJGVD6QWQHQR7MDDDG4HYCW2LRZZCKK7FPWZ",
            "num_claimable_balances": 42,
            "num_liquidity_pools": 15,
            "num_contracts": 8,
            "accounts": {
                "authorized": 100,
                "authorized_to_maintain_liabilities": 25,
                "unauthorized": 5
            },
            "claimable_balances_amount": "12500.0000000",
            "liquidity_pools_amount": "875000.0000000",
            "contracts_amount": "45000.0000000",
            "balances": {
                "authorized": "999999.9999999",
                "authorized_to_maintain_liabilities": "50000.0000000",
                "unauthorized": "100.0000000"
            },
            "flags": {
                "auth_required": true,
                "auth_revocable": false,
                "auth_immutable": true,
                "auth_clawback_enabled": false
            },
            "_links": {
                "toml": {
                    "href": "https://example.com/.well-known/stellar.toml",
                    "templated": false
                }
            }
        }
        """.trimIndent()

        val asset = json.decodeFromString<AssetResponse>(comprehensiveAssetJson)

        assertEquals("credit_alphanum4", asset.assetType)
        assertEquals("USD", asset.assetCode)
        assertEquals("GCDNJUBQSX7AJWLJACMJ7I4BC3Z47BQUTMHEICZLE6MU4KQBRYG5JY6B", asset.assetIssuer)
        assertEquals("USD_GCDNJUBQSX7AJWLJACMJ7I4BC3Z47BQUTMHEICZLE6MU4KQBRYG5JY6B_credit_alphanum4", asset.pagingToken)
        assertEquals("CACCL47PRYOGWNHPGJ5ZDJGVD6QWQHQR7MDDDG4HYCW2LRZZCKK7FPWZ", asset.contractId)
        assertEquals(42, asset.numClaimableBalances)
        assertEquals(15, asset.numLiquidityPools)
        assertEquals(8, asset.numContracts)
        assertEquals("12500.0000000", asset.claimableBalancesAmount)
        assertEquals("875000.0000000", asset.liquidityPoolsAmount)
        assertEquals("45000.0000000", asset.contractsAmount)

        assertEquals(100, asset.accounts.authorized)
        assertEquals(25, asset.accounts.authorizedToMaintainLiabilities)
        assertEquals(5, asset.accounts.unauthorized)

        assertEquals("999999.9999999", asset.balances.authorized)
        assertEquals("50000.0000000", asset.balances.authorizedToMaintainLiabilities)
        assertEquals("100.0000000", asset.balances.unauthorized)

        assertTrue(asset.flags.authRequired)
        assertFalse(asset.flags.authRevocable)
        assertTrue(asset.flags.authImmutable)
        assertFalse(asset.flags.authClawbackEnabled)

        assertEquals("https://example.com/.well-known/stellar.toml", asset.links.toml.href)
        assertEquals(false, asset.links.toml.templated)
    }

    @Test
    fun testAssetResponseWithNullableFields() {
        val assetJsonWithNulls = """
        {
            "asset_type": "credit_alphanum12",
            "asset_code": "LONGASSETCODE",
            "asset_issuer": "GBVFTZL5HIPT4PFQVTZVIWR77V7LWYCXU4CLYWWHHOEXB64XPG5LDMTU",
            "paging_token": "LONGASSETCODE_GBVFTZL5HIPT4PFQVTZVIWR77V7LWYCXU4CLYWWHHOEXB64XPG5LDMTU_credit_alphanum12",
            "contract_id": null,
            "num_claimable_balances": null,
            "num_liquidity_pools": null,
            "num_contracts": null,
            "accounts": {
                "authorized": 0,
                "authorized_to_maintain_liabilities": 0,
                "unauthorized": 0
            },
            "claimable_balances_amount": null,
            "liquidity_pools_amount": null,
            "contracts_amount": null,
            "balances": {
                "authorized": "0.0000000",
                "authorized_to_maintain_liabilities": "0.0000000",
                "unauthorized": "0.0000000"
            },
            "flags": {
                "auth_required": false,
                "auth_revocable": true,
                "auth_immutable": false,
                "auth_clawback_enabled": true
            },
            "_links": {
                "toml": {
                    "href": "https://test.org/stellar.toml"
                }
            }
        }
        """.trimIndent()

        val asset = json.decodeFromString<AssetResponse>(assetJsonWithNulls)

        assertEquals("credit_alphanum12", asset.assetType)
        assertEquals("LONGASSETCODE", asset.assetCode)
        assertEquals("GBVFTZL5HIPT4PFQVTZVIWR77V7LWYCXU4CLYWWHHOEXB64XPG5LDMTU", asset.assetIssuer)
        assertEquals("LONGASSETCODE_GBVFTZL5HIPT4PFQVTZVIWR77V7LWYCXU4CLYWWHHOEXB64XPG5LDMTU_credit_alphanum12", asset.pagingToken)
        assertNull(asset.contractId)
        assertNull(asset.numClaimableBalances)
        assertNull(asset.numLiquidityPools)
        assertNull(asset.numContracts)
        assertNull(asset.claimableBalancesAmount)
        assertNull(asset.liquidityPoolsAmount)
        assertNull(asset.contractsAmount)

        assertEquals(0, asset.accounts.authorized)
        assertEquals(0, asset.accounts.authorizedToMaintainLiabilities)
        assertEquals(0, asset.accounts.unauthorized)

        assertEquals("0.0000000", asset.balances.authorized)
        assertEquals("0.0000000", asset.balances.authorizedToMaintainLiabilities)
        assertEquals("0.0000000", asset.balances.unauthorized)

        assertFalse(asset.flags.authRequired)
        assertTrue(asset.flags.authRevocable)
        assertFalse(asset.flags.authImmutable)
        assertTrue(asset.flags.authClawbackEnabled)

        assertEquals("https://test.org/stellar.toml", asset.links.toml.href)
        assertNull(asset.links.toml.templated)
    }

    @Test
    fun testAssetResponseInnerClassesDirectConstruction() {
        val accounts = AssetResponse.Accounts(
            authorized = 50,
            authorizedToMaintainLiabilities = 30,
            unauthorized = 10
        )
        assertEquals(50, accounts.authorized)
        assertEquals(30, accounts.authorizedToMaintainLiabilities)
        assertEquals(10, accounts.unauthorized)

        val balances = AssetResponse.Balances(
            authorized = "1000000.0000000",
            authorizedToMaintainLiabilities = "500000.0000000",
            unauthorized = "50000.0000000"
        )
        assertEquals("1000000.0000000", balances.authorized)
        assertEquals("500000.0000000", balances.authorizedToMaintainLiabilities)
        assertEquals("50000.0000000", balances.unauthorized)

        val flags = AssetResponse.Flags(
            authRequired = true,
            authRevocable = true,
            authImmutable = false,
            authClawbackEnabled = true
        )
        assertTrue(flags.authRequired)
        assertTrue(flags.authRevocable)
        assertFalse(flags.authImmutable)
        assertTrue(flags.authClawbackEnabled)

        val links = AssetResponse.Links(
            toml = Link("https://direct.test/toml", true)
        )
        assertEquals("https://direct.test/toml", links.toml.href)
        assertEquals(true, links.toml.templated)
    }
}
