package com.soneso.stellar.sdk.unitTests.horizon.responses

import com.soneso.stellar.sdk.horizon.responses.Link
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LinkTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun testSimpleLinkDeserialization() {
        val linkJson = """{"href": "https://horizon.stellar.org/accounts/GTEST"}"""
        val link = json.decodeFromString<Link>(linkJson)
        assertEquals("https://horizon.stellar.org/accounts/GTEST", link.href)
        assertNull(link.templated)
    }

    @Test
    fun testTemplatedLinkDeserialization() {
        val linkJson = """{"href": "https://horizon.stellar.org/accounts/{account_id}", "templated": true}"""
        val link = json.decodeFromString<Link>(linkJson)
        assertTrue(link.href.contains("{account_id}"))
        assertEquals(true, link.templated)
    }

    @Test
    fun testNonTemplatedLinkDeserialization() {
        val linkJson = """{"href": "https://horizon.stellar.org/ledgers/123", "templated": false}"""
        val link = json.decodeFromString<Link>(linkJson)
        assertEquals(false, link.templated)
    }

    @Test
    fun testDataClassEquality() {
        val l1 = Link("https://example.com", true)
        val l2 = Link("https://example.com", true)
        assertEquals(l1, l2)
        assertEquals(l1.hashCode(), l2.hashCode())
    }

    @Test
    fun testCopy() {
        val link = Link("https://example.com", true)
        val copy = link.copy(templated = false)
        assertEquals("https://example.com", copy.href)
        assertEquals(false, copy.templated)
    }
}
