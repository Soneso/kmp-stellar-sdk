package com.soneso.stellar.sdk.unitTests.horizon.responses

import com.soneso.stellar.sdk.horizon.responses.Predicate
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PredicateTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun testUnconditionalPredicate() {
        val predicateJson = """
        {
            "unconditional": true
        }
        """.trimIndent()

        val predicate = json.decodeFromString<Predicate>(predicateJson)
        assertEquals(true, predicate.unconditional)
        assertNull(predicate.absBefore)
        assertNull(predicate.relBefore)
        assertNull(predicate.and)
        assertNull(predicate.or)
        assertNull(predicate.not)
    }

    @Test
    fun testAbsBeforePredicate() {
        val predicateJson = """
        {
            "abs_before": "1609459200"
        }
        """.trimIndent()

        val predicate = json.decodeFromString<Predicate>(predicateJson)
        assertNull(predicate.unconditional)
        assertEquals("1609459200", predicate.absBefore)
        assertNull(predicate.relBefore)
        assertNull(predicate.and)
        assertNull(predicate.or)
        assertNull(predicate.not)
    }

    @Test
    fun testRelBeforePredicate() {
        val predicateJson = """
        {
            "rel_before": "3600"
        }
        """.trimIndent()

        val predicate = json.decodeFromString<Predicate>(predicateJson)
        assertNull(predicate.unconditional)
        assertNull(predicate.absBefore)
        assertEquals("3600", predicate.relBefore)
        assertNull(predicate.and)
        assertNull(predicate.or)
        assertNull(predicate.not)
    }

    @Test
    fun testAndPredicate() {
        val predicateJson = """
        {
            "and": [
                {
                    "abs_before": "1609459200"
                },
                {
                    "rel_before": "3600"
                }
            ]
        }
        """.trimIndent()

        val predicate = json.decodeFromString<Predicate>(predicateJson)
        assertNull(predicate.unconditional)
        assertNull(predicate.absBefore)
        assertNull(predicate.relBefore)
        assertNotNull(predicate.and)
        assertEquals(2, predicate.and!!.size)
        assertEquals("1609459200", predicate.and!![0].absBefore)
        assertEquals("3600", predicate.and!![1].relBefore)
        assertNull(predicate.or)
        assertNull(predicate.not)
    }

    @Test
    fun testOrPredicate() {
        val predicateJson = """
        {
            "or": [
                {
                    "unconditional": true
                },
                {
                    "abs_before": "1609459200"
                }
            ]
        }
        """.trimIndent()

        val predicate = json.decodeFromString<Predicate>(predicateJson)
        assertNull(predicate.unconditional)
        assertNull(predicate.absBefore)
        assertNull(predicate.relBefore)
        assertNull(predicate.and)
        assertNotNull(predicate.or)
        assertEquals(2, predicate.or!!.size)
        assertEquals(true, predicate.or!![0].unconditional)
        assertEquals("1609459200", predicate.or!![1].absBefore)
        assertNull(predicate.not)
    }

    @Test
    fun testNotPredicate() {
        val predicateJson = """
        {
            "not": {
                "abs_before": "1609459200"
            }
        }
        """.trimIndent()

        val predicate = json.decodeFromString<Predicate>(predicateJson)
        assertNull(predicate.unconditional)
        assertNull(predicate.absBefore)
        assertNull(predicate.relBefore)
        assertNull(predicate.and)
        assertNull(predicate.or)
        assertNotNull(predicate.not)
        assertEquals("1609459200", predicate.not!!.absBefore)
    }

    @Test
    fun testComplexNestedPredicate() {
        val predicateJson = """
        {
            "and": [
                {
                    "or": [
                        {
                            "unconditional": true
                        },
                        {
                            "rel_before": "7200"
                        }
                    ]
                },
                {
                    "not": {
                        "abs_before": "1609459200"
                    }
                }
            ]
        }
        """.trimIndent()

        val predicate = json.decodeFromString<Predicate>(predicateJson)
        assertNotNull(predicate.and)
        assertEquals(2, predicate.and!!.size)
        
        // First element in AND is an OR predicate
        val orPredicate = predicate.and!![0]
        assertNotNull(orPredicate.or)
        assertEquals(2, orPredicate.or!!.size)
        assertEquals(true, orPredicate.or!![0].unconditional)
        assertEquals("7200", orPredicate.or!![1].relBefore)
        
        // Second element in AND is a NOT predicate
        val notPredicate = predicate.and!![1]
        assertNotNull(notPredicate.not)
        assertEquals("1609459200", notPredicate.not!!.absBefore)
    }

    @Test
    fun testEmptyPredicate() {
        val predicateJson = "{}"

        val predicate = json.decodeFromString<Predicate>(predicateJson)
        assertNull(predicate.unconditional)
        assertNull(predicate.absBefore)
        assertNull(predicate.relBefore)
        assertNull(predicate.and)
        assertNull(predicate.or)
        assertNull(predicate.not)
    }

    @Test
    fun testPredicateWithMultipleFields() {
        // Edge case: predicate with multiple mutually exclusive fields
        val predicateJson = """
        {
            "unconditional": true,
            "abs_before": "1609459200",
            "rel_before": "3600"
        }
        """.trimIndent()

        val predicate = json.decodeFromString<Predicate>(predicateJson)
        assertEquals(true, predicate.unconditional)
        assertEquals("1609459200", predicate.absBefore)
        assertEquals("3600", predicate.relBefore)
    }

    @Test
    fun testPredicateEquality() {
        val predicate1 = Predicate(unconditional = true)
        val predicate2 = Predicate(unconditional = true)
        val predicate3 = Predicate(absBefore = "1609459200")

        assertEquals(predicate1, predicate2)
        assertTrue(predicate1 != predicate3)
    }
}