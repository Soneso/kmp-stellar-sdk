package com.soneso.stellar.sdk.unitTests.horizon.responses

import com.soneso.stellar.sdk.horizon.responses.Claimant
import com.soneso.stellar.sdk.horizon.responses.Predicate
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ClaimantTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun testClaimantDeserialization() {
        val claimantJson = """
        {
            "destination": "GCEZWKCA5VLDNRLN3RPRJMRZOX3Z6G5CHCGSNFHEYVXM3XOJMDS674JZ",
            "predicate": {
                "unconditional": true
            }
        }
        """.trimIndent()

        val claimant = json.decodeFromString<Claimant>(claimantJson)
        assertEquals("GCEZWKCA5VLDNRLN3RPRJMRZOX3Z6G5CHCGSNFHEYVXM3XOJMDS674JZ", claimant.destination)
        assertNotNull(claimant.predicate)
        assertEquals(true, claimant.predicate.unconditional)
    }

    @Test
    fun testClaimantWithAbsBeforePredicate() {
        val claimantJson = """
        {
            "destination": "GABC123",
            "predicate": {
                "abs_before": "1609459200"
            }
        }
        """.trimIndent()

        val claimant = json.decodeFromString<Claimant>(claimantJson)
        assertEquals("GABC123", claimant.destination)
        assertEquals("1609459200", claimant.predicate.absBefore)
    }

    @Test
    fun testClaimantWithRelBeforePredicate() {
        val claimantJson = """
        {
            "destination": "GDEF456",
            "predicate": {
                "rel_before": "3600"
            }
        }
        """.trimIndent()

        val claimant = json.decodeFromString<Claimant>(claimantJson)
        assertEquals("GDEF456", claimant.destination)
        assertEquals("3600", claimant.predicate.relBefore)
    }

    @Test
    fun testClaimantWithComplexPredicate() {
        val claimantJson = """
        {
            "destination": "GHIJ789",
            "predicate": {
                "and": [
                    {
                        "abs_before": "1609459200"
                    },
                    {
                        "or": [
                            {
                                "unconditional": true
                            },
                            {
                                "rel_before": "7200"
                            }
                        ]
                    }
                ]
            }
        }
        """.trimIndent()

        val claimant = json.decodeFromString<Claimant>(claimantJson)
        assertEquals("GHIJ789", claimant.destination)
        assertNotNull(claimant.predicate.and)
        assertEquals(2, claimant.predicate.and!!.size)
        assertEquals("1609459200", claimant.predicate.and!![0].absBefore)
        assertNotNull(claimant.predicate.and!![1].or)
        assertEquals(2, claimant.predicate.and!![1].or!!.size)
        assertEquals(true, claimant.predicate.and!![1].or!![0].unconditional)
        assertEquals("7200", claimant.predicate.and!![1].or!![1].relBefore)
    }

    @Test
    fun testClaimantWithNotPredicate() {
        val claimantJson = """
        {
            "destination": "GKLM000",
            "predicate": {
                "not": {
                    "abs_before": "1609459200"
                }
            }
        }
        """.trimIndent()

        val claimant = json.decodeFromString<Claimant>(claimantJson)
        assertEquals("GKLM000", claimant.destination)
        assertNotNull(claimant.predicate.not)
        assertEquals("1609459200", claimant.predicate.not!!.absBefore)
    }

    @Test
    fun testClaimantEquality() {
        val claimant1 = Claimant(
            destination = "GABC123",
            predicate = Predicate(unconditional = true)
        )
        val claimant2 = Claimant(
            destination = "GABC123",
            predicate = Predicate(unconditional = true)
        )
        val claimant3 = Claimant(
            destination = "GDEF456",
            predicate = Predicate(unconditional = true)
        )

        assertEquals(claimant1, claimant2)
        assertEquals(claimant1.hashCode(), claimant2.hashCode())
        assertNotNull(claimant1.toString())
    }

    @Test
    fun testMultipleClaimantsSerialization() {
        val claimantsJson = """
        [
            {
                "destination": "GABC123",
                "predicate": {
                    "unconditional": true
                }
            },
            {
                "destination": "GDEF456",
                "predicate": {
                    "abs_before": "1609459200"
                }
            }
        ]
        """.trimIndent()

        val claimants = json.decodeFromString<List<Claimant>>(claimantsJson)
        assertEquals(2, claimants.size)
        assertEquals("GABC123", claimants[0].destination)
        assertEquals(true, claimants[0].predicate.unconditional)
        assertEquals("GDEF456", claimants[1].destination)
        assertEquals("1609459200", claimants[1].predicate.absBefore)
    }
}