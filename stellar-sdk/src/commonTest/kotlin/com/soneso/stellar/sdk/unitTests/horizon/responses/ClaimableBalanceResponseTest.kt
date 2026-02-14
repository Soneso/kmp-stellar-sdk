package com.soneso.stellar.sdk.unitTests.horizon.responses

import com.soneso.stellar.sdk.horizon.responses.ClaimableBalanceResponse
import com.soneso.stellar.sdk.horizon.responses.Claimant
import com.soneso.stellar.sdk.horizon.responses.Predicate
import kotlinx.serialization.json.Json
import com.soneso.stellar.sdk.horizon.responses.Link
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ClaimableBalanceResponseTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val claimableBalanceJson = """
    {
        "id": "00000000da0d57da7d4850e7fc10d2a9d0ebc731f7afb40574c03395b17d49149b91f5be",
        "asset": "native",
        "amount": "100.0000000",
        "sponsor": "GCEZWKCA5VLDNRLN3RPRJMRZOX3Z6G5CHCGSNFHEBD9AFZQ7TM4JRS9",
        "last_modified_ledger": 28411995,
        "last_modified_time": "2020-02-26T19:29:16Z",
        "claimants": [
            {
                "destination": "GBVFTZL5HIPT4PFQVTZVIWR77V7LWYCXU4CLYWWHHOEXB64XPG5LDMTU",
                "predicate": {
                    "unconditional": true
                }
            },
            {
                "destination": "GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7",
                "predicate": {
                    "and": [
                        {"not": {"abs_before": "2020-06-01T00:00:00Z"}},
                        {"abs_before": "2020-12-31T00:00:00Z"}
                    ]
                }
            }
        ],
        "flags": {
            "clawback_enabled": false
        },
        "paging_token": "28411995-00000000da0d57da7d4850e7fc10d2a9d0ebc731f7afb40574c03395b17d49149b91f5be",
        "_links": {
            "self": {"href": "https://horizon.stellar.org/claimable_balances/00000000da0d57da"},
            "transactions": {"href": "https://horizon.stellar.org/claimable_balances/00000000da0d57da/transactions"},
            "operations": {"href": "https://horizon.stellar.org/claimable_balances/00000000da0d57da/operations"}
        }
    }
    """.trimIndent()

    @Test
    fun testDeserialization() {
        val balance = json.decodeFromString<ClaimableBalanceResponse>(claimableBalanceJson)
        assertEquals("00000000da0d57da7d4850e7fc10d2a9d0ebc731f7afb40574c03395b17d49149b91f5be", balance.id)
        assertEquals("native", balance.assetString)
        assertEquals("100.0000000", balance.amount)
        assertEquals("GCEZWKCA5VLDNRLN3RPRJMRZOX3Z6G5CHCGSNFHEBD9AFZQ7TM4JRS9", balance.sponsor)
        assertEquals(28411995L, balance.lastModifiedLedger)
        assertEquals("2020-02-26T19:29:16Z", balance.lastModifiedTime)
    }

    @Test
    fun testClaimants() {
        val balance = json.decodeFromString<ClaimableBalanceResponse>(claimableBalanceJson)
        assertEquals(2, balance.claimants.size)

        val claimant1 = balance.claimants[0]
        assertEquals("GBVFTZL5HIPT4PFQVTZVIWR77V7LWYCXU4CLYWWHHOEXB64XPG5LDMTU", claimant1.destination)
        assertEquals(true, claimant1.predicate.unconditional)

        val claimant2 = balance.claimants[1]
        assertEquals("GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7", claimant2.destination)
        assertEquals(2, claimant2.predicate.and?.size)
    }

    @Test
    fun testFlags() {
        val balance = json.decodeFromString<ClaimableBalanceResponse>(claimableBalanceJson)
        assertEquals(false, balance.flags?.clawbackEnabled)
    }

    @Test
    fun testLinks() {
        val balance = json.decodeFromString<ClaimableBalanceResponse>(claimableBalanceJson)
        assertTrue(balance.links.self.href.contains("claimable_balances"))
        assertTrue(balance.links.transactions?.href?.contains("transactions") == true)
        assertTrue(balance.links.operations?.href?.contains("operations") == true)
    }

    @Test
    fun testPagingToken() {
        val balance = json.decodeFromString<ClaimableBalanceResponse>(claimableBalanceJson)
        assertTrue(balance.pagingToken.contains("28411995"))
    }

    @Test
    fun testMinimalDeserialization() {
        val minimalJson = """
        {
            "id": "test-id",
            "asset": "USD:GTEST",
            "amount": "50.0",
            "paging_token": "token",
            "_links": {
                "self": {"href": "https://example.com/self"}
            }
        }
        """.trimIndent()
        val balance = json.decodeFromString<ClaimableBalanceResponse>(minimalJson)
        assertEquals("test-id", balance.id)
        assertEquals("USD:GTEST", balance.assetString)
        assertNull(balance.sponsor)
        assertNull(balance.lastModifiedLedger)
        assertNull(balance.lastModifiedTime)
        assertTrue(balance.claimants.isEmpty())
        assertNull(balance.flags)
        assertNull(balance.links.transactions)
        assertNull(balance.links.operations)
    }

    @Test
    fun testComprehensiveClaimableBalanceResponseDeserialization() {
        val comprehensiveJson = """
        {
            "id": "00000000929b20b72e5890ab51c24f1cc46fa01c4f318d8d33367d24dd614cfdf5491072",
            "asset": "USD:GCDNJUBQSX7AJWLJACMJ7I4BC3Z47BQUTMHEICZLE6MU4KQBRYG5JY6B",
            "amount": "1000.0000000",
            "sponsor": "GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7",
            "last_modified_ledger": 32069474,
            "last_modified_time": "2021-01-01T12:00:00Z",
            "claimants": [
                {
                    "destination": "GBVFTZL5HIPT4PFQVTZVIWR77V7LWYCXU4CLYWWHHOEXB64XPG5LDMTU",
                    "predicate": {
                        "unconditional": true
                    }
                },
                {
                    "destination": "GCDNJUBQSX7AJWLJACMJ7I4BC3Z47BQUTMHEICZLE6MU4KQBRYG5JY6B",
                    "predicate": {
                        "abs_before": "2025-01-01T00:00:00Z",
                        "rel_before": "3600"
                    }
                }
            ],
            "flags": {
                "clawback_enabled": true
            },
            "paging_token": "00000000929b20b72e5890ab51c24f1cc46fa01c4f318d8d33367d24dd614cfdf5491072",
            "_links": {
                "self": {
                    "href": "https://horizon.stellar.org/claimable_balances/00000000929b20b72e5890ab51c24f1cc46fa01c4f318d8d33367d24dd614cfdf5491072"
                },
                "transactions": {
                    "href": "https://horizon.stellar.org/claimable_balances/00000000929b20b72e5890ab51c24f1cc46fa01c4f318d8d33367d24dd614cfdf5491072/transactions",
                    "templated": true
                },
                "operations": {
                    "href": "https://horizon.stellar.org/claimable_balances/00000000929b20b72e5890ab51c24f1cc46fa01c4f318d8d33367d24dd614cfdf5491072/operations",
                    "templated": false
                }
            }
        }
        """.trimIndent()

        val claimableBalance = json.decodeFromString<ClaimableBalanceResponse>(comprehensiveJson)

        assertEquals("00000000929b20b72e5890ab51c24f1cc46fa01c4f318d8d33367d24dd614cfdf5491072", claimableBalance.id)
        assertEquals("USD:GCDNJUBQSX7AJWLJACMJ7I4BC3Z47BQUTMHEICZLE6MU4KQBRYG5JY6B", claimableBalance.assetString)
        assertEquals("1000.0000000", claimableBalance.amount)
        assertEquals("GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7", claimableBalance.sponsor)
        assertEquals(32069474L, claimableBalance.lastModifiedLedger)
        assertEquals("2021-01-01T12:00:00Z", claimableBalance.lastModifiedTime)
        assertEquals("00000000929b20b72e5890ab51c24f1cc46fa01c4f318d8d33367d24dd614cfdf5491072", claimableBalance.pagingToken)

        assertEquals(2, claimableBalance.claimants.size)
        
        val firstClaimant = claimableBalance.claimants[0]
        assertEquals("GBVFTZL5HIPT4PFQVTZVIWR77V7LWYCXU4CLYWWHHOEXB64XPG5LDMTU", firstClaimant.destination)
        assertEquals(true, firstClaimant.predicate.unconditional)
        assertNull(firstClaimant.predicate.absBefore)
        assertNull(firstClaimant.predicate.relBefore)
        assertNull(firstClaimant.predicate.and)
        assertNull(firstClaimant.predicate.or)
        assertNull(firstClaimant.predicate.not)
        
        val secondClaimant = claimableBalance.claimants[1]
        assertEquals("GCDNJUBQSX7AJWLJACMJ7I4BC3Z47BQUTMHEICZLE6MU4KQBRYG5JY6B", secondClaimant.destination)
        assertNull(secondClaimant.predicate.unconditional)
        assertEquals("2025-01-01T00:00:00Z", secondClaimant.predicate.absBefore)
        assertEquals("3600", secondClaimant.predicate.relBefore)
        assertNull(secondClaimant.predicate.and)
        assertNull(secondClaimant.predicate.or)
        assertNull(secondClaimant.predicate.not)

        assertEquals(true, claimableBalance.flags?.clawbackEnabled)

        assertEquals("https://horizon.stellar.org/claimable_balances/00000000929b20b72e5890ab51c24f1cc46fa01c4f318d8d33367d24dd614cfdf5491072", claimableBalance.links.self.href)
        assertEquals("https://horizon.stellar.org/claimable_balances/00000000929b20b72e5890ab51c24f1cc46fa01c4f318d8d33367d24dd614cfdf5491072/transactions", claimableBalance.links.transactions?.href)
        assertEquals("https://horizon.stellar.org/claimable_balances/00000000929b20b72e5890ab51c24f1cc46fa01c4f318d8d33367d24dd614cfdf5491072/operations", claimableBalance.links.operations?.href)
        assertNull(claimableBalance.links.self.templated)
        assertEquals(true, claimableBalance.links.transactions?.templated)
        assertEquals(false, claimableBalance.links.operations?.templated)
    }

    @Test
    fun testClaimableBalanceResponseWithNullableFields() {
        val nullableJson = """
        {
            "id": "minimal-claimable-balance-id",
            "asset": "native",
            "amount": "50.0000000",
            "sponsor": null,
            "last_modified_ledger": null,
            "last_modified_time": null,
            "claimants": [],
            "flags": null,
            "paging_token": "minimal-paging-token",
            "_links": {
                "self": {
                    "href": "https://horizon.stellar.org/claimable_balances/minimal"
                },
                "transactions": null,
                "operations": null
            }
        }
        """.trimIndent()

        val claimableBalance = json.decodeFromString<ClaimableBalanceResponse>(nullableJson)

        assertEquals("minimal-claimable-balance-id", claimableBalance.id)
        assertEquals("native", claimableBalance.assetString)
        assertEquals("50.0000000", claimableBalance.amount)
        assertNull(claimableBalance.sponsor)
        assertNull(claimableBalance.lastModifiedLedger)
        assertNull(claimableBalance.lastModifiedTime)
        assertTrue(claimableBalance.claimants.isEmpty())
        assertNull(claimableBalance.flags)
        assertEquals("minimal-paging-token", claimableBalance.pagingToken)

        assertEquals("https://horizon.stellar.org/claimable_balances/minimal", claimableBalance.links.self.href)
        assertNull(claimableBalance.links.transactions)
        assertNull(claimableBalance.links.operations)
    }

    @Test
    fun testClaimableBalanceResponseWithComplexPredicates() {
        val complexPredicateJson = """
        {
            "id": "complex-predicate-id",
            "asset": "LONGASSETCODE:GBVFTZL5HIPT4PFQVTZVIWR77V7LWYCXU4CLYWWHHOEXB64XPG5LDMTU",
            "amount": "2500.0000000",
            "sponsor": "GBVFTZL5HIPT4PFQVTZVIWR77V7LWYCXU4CLYWWHHOEXB64XPG5LDMTU",
            "last_modified_ledger": 45000000,
            "last_modified_time": "2022-06-15T10:30:00Z",
            "claimants": [
                {
                    "destination": "GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7",
                    "predicate": {
                        "and": [
                            {
                                "abs_before": "2030-12-31T23:59:59Z"
                            },
                            {
                                "not": {
                                    "rel_before": "86400"
                                }
                            }
                        ]
                    }
                },
                {
                    "destination": "GCDNJUBQSX7AJWLJACMJ7I4BC3Z47BQUTMHEICZLE6MU4KQBRYG5JY6B",
                    "predicate": {
                        "or": [
                            {
                                "unconditional": true
                            },
                            {
                                "rel_before": "7200"
                            }
                        ]
                    }
                }
            ],
            "flags": {
                "clawback_enabled": false
            },
            "paging_token": "complex-predicate-paging",
            "_links": {
                "self": {
                    "href": "https://horizon.stellar.org/claimable_balances/complex",
                    "templated": false
                },
                "transactions": {
                    "href": "https://horizon.stellar.org/claimable_balances/complex/transactions"
                },
                "operations": {
                    "href": "https://horizon.stellar.org/claimable_balances/complex/operations",
                    "templated": true
                }
            }
        }
        """.trimIndent()

        val claimableBalance = json.decodeFromString<ClaimableBalanceResponse>(complexPredicateJson)

        assertEquals("complex-predicate-id", claimableBalance.id)
        assertEquals("LONGASSETCODE:GBVFTZL5HIPT4PFQVTZVIWR77V7LWYCXU4CLYWWHHOEXB64XPG5LDMTU", claimableBalance.assetString)
        assertEquals("2500.0000000", claimableBalance.amount)
        assertEquals("GBVFTZL5HIPT4PFQVTZVIWR77V7LWYCXU4CLYWWHHOEXB64XPG5LDMTU", claimableBalance.sponsor)
        assertEquals(45000000L, claimableBalance.lastModifiedLedger)
        assertEquals("2022-06-15T10:30:00Z", claimableBalance.lastModifiedTime)
        assertEquals("complex-predicate-paging", claimableBalance.pagingToken)

        assertEquals(2, claimableBalance.claimants.size)
        
        val firstClaimant = claimableBalance.claimants[0]
        assertEquals("GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7", firstClaimant.destination)
        assertNull(firstClaimant.predicate.unconditional)
        assertEquals(2, firstClaimant.predicate.and?.size)
        assertEquals("2030-12-31T23:59:59Z", firstClaimant.predicate.and?.get(0)?.absBefore)
        assertEquals("86400", firstClaimant.predicate.and?.get(1)?.not?.relBefore)

        val secondClaimant = claimableBalance.claimants[1]
        assertEquals("GCDNJUBQSX7AJWLJACMJ7I4BC3Z47BQUTMHEICZLE6MU4KQBRYG5JY6B", secondClaimant.destination)
        assertEquals(2, secondClaimant.predicate.or?.size)
        assertEquals(true, secondClaimant.predicate.or?.get(0)?.unconditional)
        assertEquals("7200", secondClaimant.predicate.or?.get(1)?.relBefore)

        assertFalse(claimableBalance.flags?.clawbackEnabled!!)

        assertEquals("https://horizon.stellar.org/claimable_balances/complex", claimableBalance.links.self.href)
        assertEquals(false, claimableBalance.links.self.templated)
        assertEquals("https://horizon.stellar.org/claimable_balances/complex/transactions", claimableBalance.links.transactions?.href)
        assertNull(claimableBalance.links.transactions?.templated)
        assertEquals("https://horizon.stellar.org/claimable_balances/complex/operations", claimableBalance.links.operations?.href)
        assertEquals(true, claimableBalance.links.operations?.templated)
    }

    @Test
    fun testClaimableBalanceResponseInnerClassesDirectConstruction() {
        val flags1 = ClaimableBalanceResponse.Flags(clawbackEnabled = true)
        assertEquals(true, flags1.clawbackEnabled)
        
        val flags2 = ClaimableBalanceResponse.Flags(clawbackEnabled = null)
        assertNull(flags2.clawbackEnabled)

        val links = ClaimableBalanceResponse.Links(
            self = Link("https://test.self", true),
            transactions = Link("https://test.transactions", false),
            operations = Link("https://test.operations", null)
        )
        assertEquals("https://test.self", links.self.href)
        assertEquals(true, links.self.templated)
        assertEquals("https://test.transactions", links.transactions?.href)
        assertEquals(false, links.transactions?.templated)
        assertEquals("https://test.operations", links.operations?.href)
        assertNull(links.operations?.templated)
        
        val linksWithNulls = ClaimableBalanceResponse.Links(
            self = Link("https://test.only.self", false),
            transactions = null,
            operations = null
        )
        assertEquals("https://test.only.self", linksWithNulls.self.href)
        assertEquals(false, linksWithNulls.self.templated)
        assertNull(linksWithNulls.transactions)
        assertNull(linksWithNulls.operations)
    }
}
